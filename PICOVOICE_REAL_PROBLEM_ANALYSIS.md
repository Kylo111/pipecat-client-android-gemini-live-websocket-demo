# Prawdziwa analiza problemu Picovoice przy wyłączonym ekranie

## Błędne założenie
❌ **Rozwiązanie 1 było błędne** - Picovoice MUSI działać podczas sesji, bo służy do zatrzymywania modelu Gemini wake wordem (np. "Alexa" = pause/resume).

## Prawdziwy problem

### Dwa AudioRecord działające jednocześnie

#### PorcupineService (Picovoice)
```kotlin
// Picovoice wewnętrznie tworzy AudioRecord
porcupineManager = builder.build(this, callback)
porcupineManager?.start()

// Picovoice używa domyślnie:
// - AudioSource: MediaRecorder.AudioSource.MIC (7)
// - Sample rate: 16000 Hz
// - Channel: MONO
```

#### VoiceClientManager
```kotlin
audioRecord = AudioRecord(
    MediaRecorder.AudioSource.VOICE_COMMUNICATION,  // = 7 (to samo!)
    SAMPLE_RATE = 16000,  // to samo!
    CHANNEL_CONFIG = MONO,  // to samo!
    AUDIO_FORMAT,
    bufferSize
)
```

### Problem: Android pozwala na dwa AudioRecord, ALE...

Android **MOŻE** pozwolić na dwa AudioRecord jednocześnie, jeśli:
1. Używają różnych AudioSource
2. System ma wystarczająco zasobów
3. Nie ma konfliktu na poziomie HAL (Hardware Abstraction Layer)

**ALE** przy wyłączonym ekranie:
- System wchodzi w Doze Mode
- Agresywniejsze zarządzanie zasobami
- Jeden z AudioRecord może być zabity przez system
- To powoduje błąd w WebSocket → kończy sesję

## Dlaczego problem występuje tylko z Picovoice?

### Bez Picovoice:
- ✅ Tylko VoiceClientManager używa mikrofonu
- ✅ VoiceService + PARTIAL_WAKE_LOCK chroni przed Doze
- ✅ Stabilne działanie

### Z Picovoice:
- ❌ Dwa serwisy konkurują o mikrofon
- ❌ PorcupineService ma własny foreground service
- ❌ Dwa wake locks mogą się konfliktować
- ❌ System może zabić jeden z AudioRecord przy wyłączonym ekranie
- ❌ To kończy sesję zamiast próbować reconnect

## Prawdziwe rozwiązanie

### Opcja A: Użyj różnych AudioSource (ZALECANE)

Zmień VoiceClientManager na inny AudioSource:

```kotlin
// Zamiast VOICE_COMMUNICATION (7), użyj:
audioRecord = AudioRecord(
    MediaRecorder.AudioSource.MIC,  // = 1 (domyślny mikrofon)
    // LUB
    MediaRecorder.AudioSource.VOICE_RECOGNITION,  // = 6 (optymalizowany dla rozpoznawania mowy)
    SAMPLE_RATE,
    CHANNEL_CONFIG,
    AUDIO_FORMAT,
    bufferSize
)
```

**Dlaczego to pomoże**:
- Różne AudioSource mogą współistnieć
- System może routować audio z różnych źródeł
- Mniejsze ryzyko konfliktu

### Opcja B: Zwiększ priorytet VoiceService

```kotlin
// W VoiceService.onStartCommand()
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // ... existing code ...
    
    // Zwiększ priorytet procesu
    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
    
    // Request audio focus
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val focusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
    } else {
        null
    }
    
    if (focusRequest != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        audioManager.requestAudioFocus(focusRequest)
    }
    
    return START_STICKY
}
```

### Opcja C: Lepsze zarządzanie błędami AudioRecord

Gdy AudioRecord nie może się uruchomić, nie kończyć sesji, tylko:
1. Poczekać
2. Spróbować ponownie
3. Jeśli nadal nie działa, pokazać użytkownikowi dialog

```kotlin
private fun startAudioRecording() {
    var retryCount = 0
    val maxRetries = 3
    
    while (retryCount < maxRetries) {
        try {
            // ... create AudioRecord ...
            
            audioRecord?.startRecording()
            
            // Verify state
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                Log.i(TAG, "✅ AudioRecord started successfully")
                return
            } else {
                Log.w(TAG, "⚠️ AudioRecord not recording, retry $retryCount/$maxRetries")
                audioRecord?.release()
                audioRecord = null
                retryCount++
                Thread.sleep(500) // Wait before retry
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ AudioRecord error: ${e.message}, retry $retryCount/$maxRetries")
            retryCount++
            Thread.sleep(500)
        }
    }
    
    // After max retries, show error but DON'T end session
    errors.add(Error("Nie można uruchomić mikrofonu. Sprawdź czy inna aplikacja nie używa mikrofonu."))
}
```

### Opcja D: Użyj AudioManager.MODE_IN_COMMUNICATION

```kotlin
private fun setupAudioManager() {
    audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    // Set audio mode to IN_COMMUNICATION for better priority
    audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
    
    // This gives higher priority than MODE_NORMAL
    // and tells system this is a voice call
    Log.i(TAG, "AudioManager mode set to MODE_IN_COMMUNICATION")
}
```

## Dlaczego sesja się kończy zamiast reconnect?

Sprawdźmy `onFailure()` w WebSocket:

```kotlin
override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
    // Classify error
    val errorType = WebSocketErrorClassifier.classifyError(t)
    
    when (errorType) {
        FATAL -> {
            // ❌ To kończy sesję!
            handleDisconnect()
        }
        RECOVERABLE -> {
            // ✅ To próbuje reconnect
            state.value = ConnectionState.RECONNECTING
            reconnectionManager.startReconnection()
        }
    }
}
```

**Problem**: Jeśli AudioRecord rzuci wyjątek, może być sklasyfikowany jako FATAL zamiast RECOVERABLE.

**Rozwiązanie**: Dodaj lepszą klasyfikację błędów AudioRecord:

```kotlin
// W WebSocketErrorClassifier
fun classifyError(t: Throwable): ErrorType {
    return when {
        // AudioRecord errors should be RECOVERABLE, not FATAL
        t.message?.contains("AudioRecord") == true -> ErrorType.RECOVERABLE
        t.message?.contains("audio") == true -> ErrorType.RECOVERABLE
        t.message?.contains("recording") == true -> ErrorType.RECOVERABLE
        
        // ... existing classification ...
    }
}
```

## Rekomendacja finalna

**Implementuj kombinację**:

1. **Opcja A**: Zmień AudioSource w VoiceClientManager na `VOICE_RECOGNITION`
2. **Opcja C**: Dodaj retry logic dla AudioRecord
3. **Opcja D**: Użyj `MODE_IN_COMMUNICATION` dla wyższego priorytetu
4. **Lepsze logowanie**: Dodaj szczegółowe logi do debugowania

To powinno rozwiązać problem bez wyłączania Picovoice podczas sesji.

## Testy

Po implementacji przetestuj:
1. Sesja z Picovoice + wyłączony ekran → sesja aktywna
2. Wake word podczas sesji → pause/resume działa
3. Długa sesja (30 min) z wyłączonym ekranem → stabilna
4. Memory pressure → sesja przetrwa
