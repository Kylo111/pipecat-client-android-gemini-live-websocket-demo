# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Task completed - historical record
**Current Documentation:** See /docs/ for current documentation

---

# Analiza problemu: Picovoice przy wyłączonym ekranie kończy sesję

## Problem
Przy włączonym Picovoice i wyłączonym ekranie, sesja jest **całkowicie kończona** (nie pauzowana), co powoduje:
- Powrót do ekranu listy konwersacji
- Utratę całego kontekstu rozmowy
- Brak możliwości resume

**Bez Picovoice problem nie występuje.**

## Potencjalne przyczyny

### 1. **KONFLIKT AUDIORECORD - Dwa serwisy używają mikrofonu jednocześnie**

#### PorcupineService
```kotlin
// Picovoice używa własnego AudioRecord wewnętrznie
porcupineManager = builder.build(this, callback)
porcupineManager?.start()  // ❌ Tworzy AudioRecord
```

#### VoiceClientManager
```kotlin
audioRecord = AudioRecord(
    MediaRecorder.AudioSource.VOICE_COMMUNICATION,  // ❌ Ten sam zasób
    SAMPLE_RATE,
    CHANNEL_CONFIG,
    AUDIO_FORMAT,
    bufferSize
)
audioRecord?.startRecording()
```

**Problem**: Android **NIE POZWALA** na dwa aktywne AudioRecord jednocześnie!
- Gdy PorcupineService tworzy AudioRecord, VoiceClientManager może stracić dostęp
- Gdy ekran jest wyłączony, system może agresywniej zarządzać zasobami audio
- Jeden z AudioRecord może być zabity przez system, co powoduje błąd

### 2. **DOZE MODE i App Standby**

Przy wyłączonym ekranie Android wchodzi w tryby oszczędzania energii:
- **Doze Mode**: Ogranicza dostęp do CPU, sieci, wake locks
- **App Standby**: Ogranicza działanie aplikacji w tle

**Foreground Service** chroni przed tym, ale:
- Jeśli AudioRecord rzuci wyjątek (konflikt z Picovoice), może to wywołać `onFailure()` w WebSocket
- WebSocket failure może być sklasyfikowany jako FATAL zamiast RECOVERABLE
- To kończy sesję zamiast próbować reconnect

### 3. **WAKE LOCK i AudioRecord**

```kotlin
// VoiceClientManager używa PARTIAL_WAKE_LOCK
wakeLock = powerManager.newWakeLock(
    PowerManager.PARTIAL_WAKE_LOCK,
    "VoiceClient::WakeLock"
)
```

**Problem**: PARTIAL_WAKE_LOCK utrzymuje CPU, ale:
- Nie gwarantuje dostępu do mikrofonu przy wyłączonym ekranie
- Jeśli Picovoice ma własny wake lock, mogą się konfliktować
- System może preferować jeden wake lock nad drugim

### 4. **AUDIO FOCUS CONFLICT**

Dwa serwisy mogą walczyć o audio focus:
- PorcupineService nasłuchuje wake words
- VoiceClientManager nagrywa rozmowę
- System może przyznać focus tylko jednemu

### 5. **MEMORY PRESSURE przy wyłączonym ekranie**

System jest bardziej agresywny z pamięcią przy wyłączonym ekranie:
- Dwa AudioRecord + bufory audio = duże zużycie pamięci
- System może wywołać `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)`
- To kończy sesję w MainActivity:

```kotlin
TRIM_MEMORY_RUNNING_CRITICAL -> {
    voiceClientManager.stop()  // ❌ Kończy sesję
    stopVoiceService()
}
```

## Dlaczego bez Picovoice działa?

Bez PorcupineService:
- ✅ Tylko jeden AudioRecord (VoiceClientManager)
- ✅ Brak konfliktu o zasób mikrofonu
- ✅ Mniejsze zużycie pamięci
- ✅ Brak konfliktu wake locks
- ✅ Brak konfliktu audio focus

## Rozwiązania

### Rozwiązanie 1: **Zatrzymaj Picovoice podczas aktywnej sesji** (ZALECANE)

```kotlin
// W VoiceClientManager.start()
fun start(threadSettings: ThreadSettings? = null) {
    // Stop PorcupineService during active session
    stopPorcupineService()
    
    // ... existing code ...
}

// W VoiceClientManager.stop()
fun stop() {
    // ... existing code ...
    
    // Restart PorcupineService after session ends
    if (PicovoiceManager.isPicovoiceEnabled()) {
        startPorcupineService()
    }
}

private fun stopPorcupineService() {
    try {
        val intent = Intent(context, PorcupineService::class.java)
        context.stopService(intent)
        Log.d(TAG, "PorcupineService stopped during active session")
    } catch (e: Exception) {
        Log.e(TAG, "Error stopping PorcupineService", e)
    }
}

private fun startPorcupineService() {
    try {
        val intent = Intent(context, PorcupineService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        Log.d(TAG, "PorcupineService restarted after session ended")
    } catch (e: Exception) {
        Log.e(TAG, "Error starting PorcupineService", e)
    }
}
```

**Zalety**:
- ✅ Eliminuje konflikt AudioRecord
- ✅ Zmniejsza zużycie pamięci
- ✅ Proste do implementacji
- ✅ Picovoice działa gdy nie ma aktywnej sesji

**Wady**:
- ❌ Brak wake word detection podczas rozmowy
- ❌ Trzeba ręcznie zatrzymać/wznowić PorcupineService

### Rozwiązanie 2: **Użyj Picovoice tylko do startu, nie podczas sesji**

Zmień logikę tak, żeby:
- Picovoice uruchamia sesję (wake word → start conversation)
- Po starcie sesji, Picovoice jest automatycznie wyłączany
- Po zakończeniu sesji, Picovoice wraca

### Rozwiązanie 3: **Zwiększ priorytet VoiceService i dodaj ochronę**

```kotlin
// W VoiceService
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // ... existing code ...
    
    // Request audio focus to prevent conflicts
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val result = audioManager.requestAudioFocus(
        null,
        AudioManager.STREAM_VOICE_CALL,
        AudioManager.AUDIOFOCUS_GAIN
    )
    
    if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        Log.w(TAG, "Audio focus not granted - may conflict with other audio apps")
    }
    
    return START_STICKY
}
```

### Rozwiązanie 4: **Lepsze zarządzanie błędami AudioRecord**

```kotlin
// W VoiceClientManager.startAudioRecording()
private fun startAudioRecording() {
    try {
        // ... existing code ...
        
        audioRecord?.startRecording()
        
        // Verify recording state
        if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            throw IllegalStateException("AudioRecord failed to start - may be in use by another app")
        }
        
    } catch (e: IllegalStateException) {
        Log.e(TAG, "AudioRecord conflict detected: ${e.message}")
        
        // Check if PorcupineService is running
        if (isPorcupineServiceRunning()) {
            Log.w(TAG, "PorcupineService is running - stopping it to free AudioRecord")
            stopPorcupineService()
            
            // Retry after short delay
            scope?.launch {
                delay(500)
                startAudioRecording()
            }
        } else {
            throw e
        }
    }
}
```

### Rozwiązanie 5: **Zmniejsz agresywność TRIM_MEMORY**

```kotlin
// W MainActivity.onTrimMemory()
TRIM_MEMORY_RUNNING_CRITICAL -> {
    // Don't stop session immediately - try to free memory first
    Log.w(TAG, "Critical memory - attempting to free resources without ending session")
    
    // Stop PorcupineService first (frees AudioRecord)
    stopPorcupineService()
    
    // Clear image caches
    voiceClientManager.clearImageCache()
    
    // Only stop session if still critical after cleanup
    scope.launch {
        delay(1000)
        if (isMemoryStillCritical()) {
            voiceClientManager.stop()
            stopVoiceService()
        }
    }
}
```

## Rekomendacja

**Implementuj Rozwiązanie 1 + Rozwiązanie 4**:

1. Automatycznie zatrzymuj PorcupineService podczas aktywnej sesji
2. Dodaj detekcję konfliktu AudioRecord i automatyczne rozwiązywanie
3. Wznów PorcupineService po zakończeniu sesji

To zapewni:
- ✅ Stabilną sesję bez konfliktów
- ✅ Działanie przy wyłączonym ekranie
- ✅ Zachowanie kontekstu rozmowy
- ✅ Picovoice działa gdy nie ma aktywnej sesji

## Testy do wykonania

Po implementacji przetestuj:

1. **Test podstawowy**:
   - Włącz Picovoice
   - Uruchom sesję
   - Wyłącz ekran
   - Rozmawiaj przez 5 minut
   - Włącz ekran
   - ✅ Sesja powinna być aktywna

2. **Test wake word**:
   - Włącz Picovoice
   - Powiedz wake word
   - Sesja się uruchamia
   - ✅ PorcupineService powinien się zatrzymać
   - Zakończ sesję
   - ✅ PorcupineService powinien się wznowić

3. **Test memory pressure**:
   - Uruchom sesję z Picovoice
   - Otwórz wiele innych aplikacji
   - Wyłącz ekran
   - ✅ Sesja powinna przetrwać

4. **Test długiej sesji**:
   - Uruchom sesję
   - Wyłącz ekran
   - Rozmawiaj przez 30 minut
   - ✅ Brak crashy, sesja aktywna
