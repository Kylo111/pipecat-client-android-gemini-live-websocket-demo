# Analiza Błędów Krytycznych - VoiceClientManager

## Data: 2025-11-30

## Błąd Krytyczny #1: Brak Implementacji Auto-Pause Monitoring

### Problem
Kod wywołuje metody które **NIE ISTNIEJĄ**:
- `startAutoPauseMonitoring()` - wywoływana w 3 miejscach
- `stopAutoPauseMonitoring()` - wywoływana w 1 miejscu
- `startBotResponseTimeoutMonitoring()` - wywoływana w 1 miejscu

### Lokalizacje wywołań:
1. Linia ~1000: `startAutoPauseMonitoring()` po setupComplete
2. Linia ~1800: `startAutoPauseMonitoring()` w startAudioRecording
3. Linia ~2100: `startAutoPauseMonitoring()` w resume()
4. Linia ~1000: `startBotResponseTimeoutMonitoring()` po setupComplete
5. Linia ~2300: `stopAutoPauseMonitoring()` w handleDisconnect

### Konsekwencje:
- **Auto-pause NIE DZIAŁA** - aplikacja nie pauzuje się po okresie bezczynności użytkownika
- **Bot response timeout NIE DZIAŁA** - aplikacja nie pauzuje się gdy bot nie odpowiada
- Zmienne `secondsUntilAutoPause` i `minutesUntilBotTimeout` są aktualizowane ale nigdy nie sprawdzane
- Jobs `autoPauseJob` i `botResponseTimeoutJob` są deklarowane ale nigdy nie uruchamiane

## Błąd Krytyczny #2: Audio Pipeline - Piki i Zakłócenia

### Problem
Mimo zwiększenia bufora do 8x i użycia WRITE_BLOCKING, nadal występują:
- Piki dźwięku
- Początek słowa nachodzi na koniec poprzedniego
- Zakłócenia podczas dłuższych wypowiedzi

### Możliwe przyczyny:
1. **Brak synchronizacji między pakietami** - każdy pakiet audio jest odtwarzany natychmiast
2. **Interruption handling** - `interruptPlayback()` może nie działać poprawnie
3. **Generation ID** - może być race condition przy sprawdzaniu
4. **AudioTrack state** - może być problem z restartowaniem po flush

### Lokalizacja:
- `handleAudioMessage()` - linia ~1400
- `interruptPlayback()` - linia ~1200
- AudioTrack write - linia ~1450

## Rozwiązanie

### 1. Implementacja Auto-Pause Monitoring
Dodać metody:
```kotlin
private fun startAutoPauseMonitoring() {
    autoPauseJob?.cancel()
    
    val timeout = Preferences.autoPauseTimeoutSeconds.value
    if (timeout <= 0) {
        secondsUntilAutoPause.value = -1
        return
    }
    
    lastActivityTime = System.currentTimeMillis()
    secondsUntilAutoPause.value = timeout
    
    autoPauseJob = scope?.launch {
        while (isActive) {
            delay(1000) // Check every second
            
            if (isPaused.value || state.value != ConnectionState.CONNECTED) {
                break
            }
            
            val elapsed = (System.currentTimeMillis() - lastActivityTime) / 1000
            val remaining = timeout - elapsed.toInt()
            
            secondsUntilAutoPause.value = remaining.coerceAtLeast(0)
            
            if (remaining <= 0) {
                Log.i(TAG, "⏸️ Auto-pause triggered - ${timeout}s of inactivity")
                pause()
                break
            }
        }
    }
}

private fun stopAutoPauseMonitoring() {
    autoPauseJob?.cancel()
    autoPauseJob = null
    secondsUntilAutoPause.value = -1
}
```

### 2. Implementacja Bot Response Timeout Monitoring
Dodać metodę:
```kotlin
private fun startBotResponseTimeoutMonitoring() {
    botResponseTimeoutJob?.cancel()
    
    val timeout = Preferences.botResponseTimeoutMinutes.value
    if (timeout <= 0) {
        minutesUntilBotTimeout.value = -1
        return
    }
    
    lastBotResponseTime = System.currentTimeMillis()
    minutesUntilBotTimeout.value = timeout
    
    botResponseTimeoutJob = scope?.launch {
        while (isActive) {
            delay(60000) // Check every minute
            
            if (isPaused.value || state.value != ConnectionState.CONNECTED) {
                break
            }
            
            val elapsed = (System.currentTimeMillis() - lastBotResponseTime) / 60000
            val remaining = timeout - elapsed.toInt()
            
            minutesUntilBotTimeout.value = remaining.coerceAtLeast(0)
            
            if (remaining <= 0) {
                Log.w(TAG, "⏸️ Bot response timeout - no response for ${timeout} minutes")
                pause()
                break
            }
        }
    }
}
```

### 3. Naprawa Audio Pipeline
Dodać kolejkę audio z synchronizacją:
```kotlin
private val audioQueue = mutableListOf<ByteArray>()
private val audioQueueMutex = Mutex()
private var audioPlaybackJob: Job? = null

private fun handleAudioMessage(audioData: ByteArray) {
    // Add to queue instead of playing immediately
    scope?.launch {
        audioQueueMutex.withLock {
            audioQueue.add(audioData)
        }
    }
    
    // Start playback job if not running
    if (audioPlaybackJob == null || !audioPlaybackJob!!.isActive) {
        startAudioPlaybackJob()
    }
}

private fun startAudioPlaybackJob() {
    audioPlaybackJob = scope?.launch {
        while (isActive) {
            val chunk = audioQueueMutex.withLock {
                if (audioQueue.isEmpty()) null else audioQueue.removeAt(0)
            }
            
            if (chunk == null) {
                delay(10)
                continue
            }
            
            // Play chunk with proper synchronization
            playAudioChunk(chunk)
        }
    }
}
```

## Status
- [ ] Implementacja auto-pause monitoring
- [ ] Implementacja bot response timeout monitoring
- [ ] Naprawa audio pipeline
- [ ] Testy na urządzeniu
