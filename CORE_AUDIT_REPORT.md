# KOMPLEKSOWY AUDYT CORE - Sesja, Gemini i Maszyna Stanów

## Data audytu: 2025-12-03

---

## 1. PODSUMOWANIE WYKONAWCZE

### 1.1 Główne problemy zidentyfikowane

| Problem | Priorytet | Wpływ |
|---------|-----------|-------|
| Brak synchronizacji audioGenerationId między komponentami | KRYTYCZNY | Duplikacja audio, piki |
| Thinking state nigdy nie jest używany | WYSOKI | Niepotrzebna złożoność |
| Brak obsługi SilenceDetected w maszynie stanów | WYSOKI | Bot nie kończy mówienia |
| Podwójne wywołanie processEvent dla audio | KRYTYCZNY | Duplikacja audio |
| Brak anti-echo w trybie full-duplex | WYSOKI | Zakłócenia audio |
| Niespójność między WebSocketClient.connectionState a VoiceUiState | ŚREDNI | Błędne stany UI |

---

## 2. ARCHITEKTURA CORE

### 2.1 Główne komponenty

```
┌─────────────────────────────────────────────────────────────────────┐
│                        VoiceClientManager                            │
│  (Koordynator - 1190 linii)                                         │
│                                                                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐     │
│  │ VoiceSession    │  │ SideEffect      │  │ VoiceUiState    │     │
│  │ StateMachine    │  │ Executor        │  │ Mapper          │     │
│  │ (751 linii)     │  │ (250 linii)     │  │ (150 linii)     │     │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘     │
│           │                    │                    │               │
│           └────────────────────┼────────────────────┘               │
│                                │                                     │
└────────────────────────────────┼─────────────────────────────────────┘
                                 │
        ┌────────────────────────┼────────────────────────┐
        │                        │                        │
        ▼                        ▼                        ▼
┌───────────────┐      ┌───────────────┐      ┌───────────────┐
│  AudioEngine  │      │ WebSocketClient│      │ GeminiProtocol│
│  (1063 linii) │      │  (350 linii)  │      │  (350 linii)  │
└───────────────┘      └───────────────┘      └───────────────┘
```


### 2.2 Przepływ eventów

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           PRZEPŁYW EVENTÓW                                │
└──────────────────────────────────────────────────────────────────────────┘

1. WEJŚCIE AUDIO (użytkownik mówi):
   AudioEngine.onAudioRecorded() 
   → VoiceClientManagerListeners.wireAudioEngine()
   → processEvent(VoiceEvent.AudioInput)
   → StateMachine.reduce()
   → SideEffect.SendAudio
   → SideEffectExecutor.execute()
   → WebSocketClient.send()

2. WYJŚCIE AUDIO (bot mówi):
   WebSocketClient.onMessage(bytes)
   → VoiceClientManagerListeners.wireWebSocketClient()
   → handleAudioMessage()
   → processEvent(VoiceEvent.BotAudioReceived)
   → StateMachine.reduce()
   → SideEffect.QueueAudio
   → SideEffectExecutor.execute()
   → AudioEngine.queueAudio()

3. WIADOMOŚCI TEKSTOWE (transkrypcje, tool calls):
   WebSocketClient.onMessage(text)
   → handleTextMessage()
   → GeminiProtocol.parseMessage()
   → processEvent(VoiceEvent.*)
   → StateMachine.reduce()
   → SideEffects
   → SideEffectExecutor.execute()
```

---

## 3. MASZYNA STANÓW - ANALIZA

### 3.1 Stany sesji (VoiceSessionState)

| Stan | Opis | Przejścia wejściowe | Przejścia wyjściowe |
|------|------|---------------------|---------------------|
| **Idle** | Brak sesji | StopRequested z dowolnego | StartRequested → Connecting |
| **Connecting** | Łączenie WebSocket | StartRequested z Idle/Paused/Error | SetupComplete → Listening |
| **Listening** | Gotowy, użytkownik może mówić | SetupComplete, TurnComplete, Interrupted | BotAudioReceived → Speaking |
| **Thinking** | Czeka na odpowiedź bota | ⚠️ NIGDY NIE UŻYWANY | BotAudioReceived → Speaking |
| **Speaking** | Bot mówi | BotAudioReceived z Listening | TurnComplete/Interrupted → Listening |
| **Paused** | Spauzowany | PauseRequested, AutoPauseTriggered | StartRequested → Connecting |
| **Error** | Błąd | WebSocketError | StartRequested → Connecting |

### 3.2 ⚠️ PROBLEM: Stan Thinking nigdy nie jest używany

**Obserwacja:** W `reduceListening()` przy `BotAudioReceived` następuje bezpośrednie przejście do `Speaking`:

```kotlin
// VoiceSessionStateMachine.kt:230
is VoiceEvent.BotAudioReceived -> {
    // CRITICAL FIX: First audio chunk should transition directly to Speaking
    Log.d(TAG, "First bot audio chunk received in Listening state - transitioning to Speaking")
    ReduceResult(
        newState = VoiceSessionState.Speaking(...),
        ...
    )
}
```

**Skutek:** Stan `Thinking` jest martwy kod. Nigdy nie ma przejścia `Listening → Thinking`.

**Rekomendacja:** Usunąć stan `Thinking` lub zmienić logikę tak, aby był używany.


### 3.3 ⚠️ PROBLEM: SilenceDetected nie jest obsługiwany

**Obserwacja:** `ConversationMonitor` emituje `VoiceEvent.SilenceDetected`, ale maszyna stanów go ignoruje:

```kotlin
// VoiceSessionStateMachine.kt - brak obsługi SilenceDetected w żadnym stanie!
// ConversationMonitor.kt:230
listener?.onSilenceDetected()
```

**Skutek:** Gdy bot przestaje mówić bez wysłania `turnComplete`, sesja zostaje w stanie `Speaking` na zawsze.

**Rekomendacja:** Dodać obsługę `SilenceDetected` w stanie `Speaking`:
```kotlin
is VoiceEvent.SilenceDetected -> {
    ReduceResult(
        newState = VoiceSessionState.Listening(...),
        sideEffects = listOf(
            SideEffect.StopPlayback,
            SideEffect.StopSilenceDetection,
            SideEffect.StartAutoPauseTimer
        )
    )
}
```

---

## 4. PRZEPŁYWY AUDIO - ANALIZA KRYTYCZNA

### 4.1 Przepływ audio od bota

```
WebSocket.onMessage(bytes)
    │
    ▼
handleAudioMessage(audioData)
    │
    ├── audioChunksReceived++
    ├── conversationMonitor?.updateBotAudioTime()
    ├── applyVolumeBoost() [opcjonalnie]
    │
    ▼
processEvent(VoiceEvent.BotAudioReceived(boostedAudio))
    │
    ▼
StateMachine.reduce()
    │
    ├── Listening → Speaking + [StartPlayback, QueueAudio]
    └── Speaking → Speaking + [QueueAudio]
    │
    ▼
SideEffectExecutor.execute()
    │
    ├── StartPlayback → audioEngine.startPlayback()
    └── QueueAudio → audioEngine.queueAudio(data, generationId)
```

### 4.2 ⚠️ KRYTYCZNY PROBLEM: Dwa różne audioGenerationId

**Obserwacja:** Istnieją DWA różne generation ID:

1. **VoiceClientManager.audioGenerationId** (AtomicInteger)
   - Używany w `SideEffectExecutor.QueueAudio`
   - Przekazywany do `audioEngine.queueAudio(data, generationId)`

2. **AudioEngine.currentGenerationId** (AtomicInteger)
   - Używany wewnętrznie w AudioEngine
   - Inkrementowany w `interruptPlayback()`

**Problem w kodzie:**

```kotlin
// SideEffectExecutor.kt:115
is SideEffect.QueueAudio -> {
    val currentGenId = audioGenerationId.get()  // ← VoiceClientManager.audioGenerationId
    audioEngine.queueAudio(sideEffect.data, currentGenId)
}

// AudioEngine.kt:870
fun queueAudio(data: ByteArray, generationId: Int) {
    // Only queue if generationId matches current
    if (generationId == currentGenerationId.get()) {  // ← AudioEngine.currentGenerationId
        // ...
    }
}
```

**Skutek:** Te dwa ID NIGDY nie są zsynchronizowane! 
- `VoiceClientManager.audioGenerationId` zaczyna od 0
- `AudioEngine.currentGenerationId` zaczyna od 0
- Ale `interruptPlayback()` inkrementuje tylko `AudioEngine.currentGenerationId`
- `VoiceClientManager.audioGenerationId` nigdy nie jest inkrementowany!

**To powoduje:** Wszystkie pakiety audio są odrzucane po pierwszym przerwaniu, bo generation ID się nie zgadzają.


### 4.3 ⚠️ PROBLEM: Brak synchronizacji ConversationMonitor.isBotTalking

**Obserwacja:** `ConversationMonitor.setBotTalking()` nigdy nie jest wywoływany!

```kotlin
// ConversationMonitor.kt:85
fun setBotTalking(talking: Boolean) {
    isBotTalking = talking
    // ...
}
```

**Szukanie wywołań:** Brak wywołań `setBotTalking()` w całym kodzie!

**Skutek:** 
- `isBotTalking` zawsze jest `false`
- Auto-pause timer nie jest resetowany gdy bot mówi
- Silence detection nie działa poprawnie

---

## 5. OBSŁUGA PAUZY - ANALIZA

### 5.1 Wymagania (z opisu użytkownika)

| Przypadek | Oczekiwane zachowanie | Status |
|-----------|----------------------|--------|
| Użytkownik pauzuje (przycisk) | WebSocket zamyka się, session handle zachowany | ✅ OK |
| Brak aktywności użytkownika | Auto-pause po timeout | ✅ OK |
| Brak aktywności bota | Pause po timeout | ⚠️ CZĘŚCIOWO |
| Komenda Picovoice ALEXA | Pause | ✅ OK |
| Użytkownik odpauzuje (przycisk) | Reconnect z tym samym tokenem | ✅ OK |
| Picovoice odpauzuje | Reconnect z tym samym tokenem | ✅ OK |

### 5.2 Przepływ pauzy

```
pause() [VoiceClientManager.kt:920]
    │
    ▼
processEvent(VoiceEvent.PauseRequested)
    │
    ▼
StateMachine.reduce() [Listening/Speaking/Thinking]
    │
    ▼
ReduceResult(
    newState = VoiceSessionState.Paused(canResume = true),
    sideEffects = [
        StopRecording,
        StopAutoPauseTimer,
        Disconnect(code=1000, reason="User paused"),
        UpdateServiceNotification,
        UpdatePicovoiceState
    ]
)
    │
    ▼
SideEffectExecutor.execute()
    │
    ├── audioEngine.stopRecording()
    ├── conversationMonitor.stopAutoPauseTimer()
    ├── webSocketClient.disconnect(1000, "User paused")
    └── ...
    │
    ▼
handleDisconnect(preserveSessionHandle = true)
    │
    ├── reconnectionManager.cancelReconnection()
    ├── webSocketClient.stopHealthMonitoring()
    └── [NIE zwalnia: scope, wakeLock, bluetooth]
```

### 5.3 ✅ Poprawna obsługa: Nie reconnectuje po user pause

```kotlin
// VoiceClientManagerListeners.kt:140
override fun onDisconnected(code: Int, reason: String) {
    // CRITICAL FIX: Check reason string FIRST
    if (reason == "User paused" || reason == "Auto-pause timeout") {
        Log.i(TAG, "✅ User-initiated pause detected, NOT reconnecting")
        return
    }
    // ...
}
```


---

## 6. OBSŁUGA PRZERWANIA (INTERRUPTED) - ANALIZA

### 6.1 Wymaganie

W trybie full-duplex, gdy użytkownik mówi podczas gdy bot mówi, Gemini wysyła `interrupted: true`. Aplikacja powinna:
1. NATYCHMIAST zatrzymać audio playback
2. Wyczyścić bufor audio
3. Przejść do stanu Listening

### 6.2 Przepływ przerwania

```
GeminiProtocol.parseServerContent()
    │
    ├── Sprawdza interrupted PIERWSZY (najwyższy priorytet)
    │   if (serverContent.containsKey("interrupted")) {
    │       val interrupted = serverContent["interrupted"]?.jsonPrimitive?.content?.toBoolean()
    │       if (interrupted) return GeminiEvent.Interrupted
    │   }
    │
    ▼
handleTextMessage() → processEvent(VoiceEvent.Interrupted)
    │
    ▼
StateMachine.reduceSpeaking()
    │
    ▼
is VoiceEvent.Interrupted -> {
    ReduceResult(
        newState = VoiceSessionState.Listening(...),
        sideEffects = [
            ClearAudioQueue,  // ← PIERWSZY
            StopPlayback,
            StopSilenceDetection,
            StartAutoPauseTimer,
            UpdatePicovoiceState
        ]
    )
}
    │
    ▼
SideEffectExecutor.execute()
    │
    ├── ClearAudioQueue → audioEngine.interruptPlayback()
    │       │
    │       ├── currentGenerationId.incrementAndGet()  // Invaliduje pakiety
    │       ├── audioTrack.pause()
    │       ├── audioTrack.flush()  // SYNCHRONICZNIE!
    │       └── audioTrack.play()
    │
    └── StopPlayback → audioEngine.stopPlayback()
```

### 6.3 ✅ Poprawna kolejność: ClearAudioQueue przed StopPlayback

```kotlin
// VoiceSessionStateMachine.kt:450
is VoiceEvent.Interrupted -> {
    sideEffects = buildList {
        add(SideEffect.ClearAudioQueue) // ← PIERWSZY - flush AudioTrack
        add(SideEffect.StopPlayback)    // ← DRUGI
        // ...
    }
}
```

### 6.4 ⚠️ PROBLEM: interruptPlayback() używa wewnętrznego generationId

```kotlin
// AudioEngine.kt:880
fun interruptPlayback() {
    val newId = currentGenerationId.incrementAndGet()  // ← AudioEngine.currentGenerationId
    // ...
}
```

Ale `SideEffectExecutor.QueueAudio` używa `VoiceClientManager.audioGenerationId`:

```kotlin
// SideEffectExecutor.kt:115
is SideEffect.QueueAudio -> {
    val currentGenId = audioGenerationId.get()  // ← VoiceClientManager.audioGenerationId
    audioEngine.queueAudio(sideEffect.data, currentGenId)
}
```

**Te dwa ID nie są zsynchronizowane!**


---

## 7. ANTI-ECHO W TRYBIE FULL-DUPLEX

### 7.1 Wymaganie

W trybie głośnomówiącym i full-duplex powinno być używane systemowe anti-echo (AEC).

### 7.2 Obecna implementacja

```kotlin
// AudioEngine.kt:260
audioRecord = AudioRecord(
    MediaRecorder.AudioSource.VOICE_COMMUNICATION,  // ← Włącza AEC
    INPUT_SAMPLE_RATE,
    CHANNEL_CONFIG_IN,
    AUDIO_FORMAT,
    inputBufferSize
)
```

**`VOICE_COMMUNICATION`** automatycznie włącza:
- Acoustic Echo Cancellation (AEC)
- Noise Suppression (NS)
- Automatic Gain Control (AGC)

### 7.3 ⚠️ PROBLEM: Brak jawnej konfiguracji AEC

Android nie gwarantuje, że AEC jest włączone dla `VOICE_COMMUNICATION`. Należy jawnie sprawdzić i włączyć:

```kotlin
// Brakujący kod:
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor

if (AcousticEchoCanceler.isAvailable()) {
    val aec = AcousticEchoCanceler.create(audioRecord.audioSessionId)
    aec?.enabled = true
}

if (NoiseSuppressor.isAvailable()) {
    val ns = NoiseSuppressor.create(audioRecord.audioSessionId)
    ns?.enabled = true
}
```

---

## 8. LISTA WSZYSTKICH EVENTÓW

### 8.1 VoiceEvent (state/VoiceEvent.kt)

| Event | Źródło | Obsługiwany w stanach |
|-------|--------|----------------------|
| `StartRequested` | start(), resume() | Idle, Paused, Error |
| `StopRequested` | stop(), forceStop() | Wszystkie |
| `PauseRequested` | pause() | Listening, Thinking, Speaking |
| `ResumeRequested` | (nieużywany) | Paused |
| `WebSocketConnected` | WebSocketClient | (ignorowany) |
| `SetupComplete` | GeminiProtocol | Connecting |
| `WebSocketDisconnected` | WebSocketClient | (obsługiwany w listenerze) |
| `WebSocketError` | WebSocketClient | Connecting |
| `AudioInput` | AudioEngine | Listening |
| `BotAudioReceived` | handleAudioMessage() | Listening, Thinking, Speaking |
| `BotStartedSpeaking` | (nieużywany) | Listening, Thinking |
| `BotStoppedSpeaking` | (nieużywany) | Speaking |
| `TurnComplete` | GeminiProtocol | Speaking |
| `Interrupted` | GeminiProtocol | Speaking |
| `MicToggled` | (nieużywany) | Listening, Speaking |
| `SpeakerToggled` | (nieużywany) | - |
| `ImageSelected` | (nieużywany) | - |
| `ImageProcessingStarted` | sendImage() | Auxiliary |
| `ImageProcessingCompleted` | sendImage() | Auxiliary |
| `ImageProcessingFailed` | sendImage() | Auxiliary |
| `AutoPauseTriggered` | ConversationMonitor | Listening |
| `BotResponseTimeout` | ConversationMonitor | Thinking |
| `SilenceDetected` | ConversationMonitor | ⚠️ NIEOBSŁUGIWANY |
| `UserTranscript` | GeminiProtocol | Listening, Speaking |
| `BotTranscript` | GeminiProtocol | Listening, Speaking |
| `ToolCallReceived` | GeminiProtocol | Listening, Speaking, Auxiliary |
| `ToolExecutionComplete` | ToolExecutor | Auxiliary |
| `SessionHandleReceived` | GeminiProtocol | Connecting, Listening, Speaking |


---

## 9. LISTA WSZYSTKICH SIDE EFFECTS

### 9.1 SideEffect (state/SideEffect.kt)

| Side Effect | Executor | Opis |
|-------------|----------|------|
| `StartRecording` | audioEngine.startRecording() | Rozpoczyna nagrywanie |
| `StopRecording` | audioEngine.stopRecording() | Zatrzymuje nagrywanie |
| `PauseRecording` | audioEngine.pauseRecording() | Pauzuje nagrywanie (half-duplex) |
| `ResumeRecording` | audioEngine.resumeRecording() | Wznawia nagrywanie |
| `StartPlayback` | audioEngine.startPlayback() | Rozpoczyna odtwarzanie |
| `StopPlayback` | audioEngine.stopPlayback() | Zatrzymuje odtwarzanie |
| `ClearAudioQueue` | audioEngine.interruptPlayback() | Czyści kolejkę i flush AudioTrack |
| `QueueAudio(data)` | audioEngine.queueAudio() | Kolejkuje audio do odtworzenia |
| `Connect(url, setup)` | webSocketClient.connect() | Łączy WebSocket |
| `Disconnect(code, reason)` | webSocketClient.disconnect() | Rozłącza WebSocket |
| `SendAudio(data)` | webSocketClient.send() | Wysyła audio przez WebSocket |
| `SendToolResponse(id, result)` | webSocketClient.send() | Wysyła odpowiedź tool |
| `StartAutoPauseTimer` | conversationMonitor.startAutoPauseTimer() | Uruchamia timer auto-pause |
| `StopAutoPauseTimer` | conversationMonitor.stopAutoPauseTimer() | Zatrzymuje timer |
| `StartBotResponseTimer` | conversationMonitor.startBotResponseTimer() | Uruchamia timer odpowiedzi bota |
| `StopBotResponseTimer` | conversationMonitor.stopBotResponseTimer() | Zatrzymuje timer |
| `StartSilenceDetection` | conversationMonitor.startSilenceDetection() | Uruchamia detekcję ciszy |
| `StopSilenceDetection` | conversationMonitor.stopSilenceDetection() | Zatrzymuje detekcję |
| `SaveSessionHandle(handle)` | sessionStateManager.updateResumptionHandle() | Zapisuje handle sesji |
| `ClearSessionHandle` | sessionStateManager.endSession() | Czyści handle sesji |
| `UpdateServiceNotification` | callback | Aktualizuje notyfikację |
| `ShowError(message)` | errors.add() | Pokazuje błąd |
| `UpdatePicovoiceState` | callback | Aktualizuje stan Picovoice |
| `PerformPostSetupOperations` | callback | Operacje po połączeniu |
| `ExecuteTool(id, name, args)` | toolExecutor.executeTool() | Wykonuje tool |
| `EmitUserTranscript(text)` | callback | Emituje transkrypt użytkownika |
| `EmitBotTranscript(text)` | callback | Emituje transkrypt bota |

---

## 10. ZIDENTYFIKOWANE PODATNOŚCI I DUPLIKATY

### 10.1 ⚠️ Duplikat: Dwa audioGenerationId

**Lokalizacja:**
- `VoiceClientManager.audioGenerationId` (linia 97)
- `AudioEngine.currentGenerationId` (linia 175)

**Problem:** Nie są zsynchronizowane. `interruptPlayback()` inkrementuje tylko `AudioEngine.currentGenerationId`.

**Naprawa:** Usunąć `VoiceClientManager.audioGenerationId` i używać tylko `AudioEngine.currentGenerationId`, lub zsynchronizować je.

### 10.2 ⚠️ Duplikat: Dwa connectionState

**Lokalizacja:**
- `WebSocketClient._connectionState` (StateFlow)
- `VoiceUiState.connectionState` (mapowany z VoiceSessionState)

**Problem:** Mogą być niespójne. `WebSocketClient` aktualizuje swój stan niezależnie od maszyny stanów.

### 10.3 ⚠️ Nieużywane eventy

| Event | Status |
|-------|--------|
| `ResumeRequested` | Nieużywany - `start()` jest wywoływany zamiast tego |
| `BotStartedSpeaking` | Nieużywany - `BotAudioReceived` obsługuje to |
| `BotStoppedSpeaking` | Nieużywany - `TurnComplete` obsługuje to |
| `MicToggled` | Nieużywany - `enableMic()` używa `pause()`/`resume()` |
| `SpeakerToggled` | Nieużywany |
| `ImageSelected` | Nieużywany |

### 10.4 ⚠️ Nieobsługiwane eventy

| Event | Problem |
|-------|---------|
| `SilenceDetected` | Emitowany przez ConversationMonitor, ale ignorowany przez maszynę stanów |
| `WebSocketConnected` | Emitowany, ale ignorowany (tylko logowany) |


---

## 11. POTENCJALNE PROBLEMY Z AUDIO

### 11.1 ⚠️ KRYTYCZNY: Desynchronizacja generationId

**Scenariusz problemu:**

1. Bot zaczyna mówić (generationId = 0 w obu miejscach)
2. Użytkownik przerywa → `interruptPlayback()` → `AudioEngine.currentGenerationId = 1`
3. `VoiceClientManager.audioGenerationId` nadal = 0
4. Nowe audio przychodzi → `SideEffectExecutor` używa `audioGenerationId.get()` = 0
5. `AudioEngine.queueAudio(data, 0)` sprawdza `currentGenerationId.get()` = 1
6. **0 != 1 → Audio jest odrzucane!**

**Skutek:** Po pierwszym przerwaniu, ŻADNE audio nie jest odtwarzane.

### 11.2 ⚠️ Brak wywołania setBotTalking()

**Szukanie w kodzie:**
```bash
grep -r "setBotTalking" --include="*.kt"
# Wynik: Tylko definicja w ConversationMonitor.kt, brak wywołań!
```

**Skutek:**
- `ConversationMonitor.isBotTalking` zawsze = false
- Auto-pause timer nie jest resetowany gdy bot mówi
- Silence detection nie działa poprawnie

### 11.3 ⚠️ Brak aktualizacji botAudioTime w niektórych ścieżkach

**Obecna implementacja:**
```kotlin
// VoiceClientManager.kt:730
private fun handleAudioMessage(audioData: ByteArray) {
    // ...
    conversationMonitor?.updateBotAudioTime()  // ✅ OK
    // ...
}
```

**Problem:** `updateBotAudioTime()` jest wywoływany, ale `setBotTalking(true)` nigdy nie jest wywoływany.

---

## 12. DIAGRAM MASZYNY STANÓW

```
                    ┌─────────────────────────────────────────────────────────┐
                    │                                                         │
                    │                      StopRequested                      │
                    │                           │                             │
                    ▼                           │                             │
              ┌─────────┐                       │                             │
              │  IDLE   │◄──────────────────────┼─────────────────────────────┤
              └────┬────┘                       │                             │
                   │                            │                             │
                   │ StartRequested             │                             │
                   ▼                            │                             │
            ┌────────────┐                      │                             │
            │ CONNECTING │                      │                             │
            └─────┬──────┘                      │                             │
                  │                             │                             │
                  │ SetupComplete               │                             │
                  ▼                             │                             │
            ┌───────────┐                       │                             │
            │ LISTENING │◄──────────────────────┼──────────────┐              │
            └─────┬─────┘                       │              │              │
                  │                             │              │              │
                  │ BotAudioReceived            │              │              │
                  │ (BEZPOŚREDNIO!)             │              │              │
                  ▼                             │              │              │
            ┌──────────┐                        │              │              │
            │ SPEAKING │────────────────────────┤              │              │
            └─────┬────┘                        │              │              │
                  │                             │              │              │
                  │ TurnComplete/Interrupted    │              │              │
                  └─────────────────────────────┼──────────────┘              │
                                                │                             │
                  PauseRequested/AutoPause      │                             │
                  ─────────────────────────────►│                             │
                                                │                             │
                                          ┌─────┴─────┐                       │
                                          │  PAUSED   │───────────────────────┘
                                          └───────────┘
                                                │
                                                │ StartRequested
                                                │ (resume)
                                                ▼
                                          ┌────────────┐
                                          │ CONNECTING │
                                          └────────────┘

UWAGA: Stan THINKING nigdy nie jest osiągany!
       Przejście Listening → Thinking nie istnieje.
```


---

## 13. REKOMENDACJE NAPRAW

### 13.1 KRYTYCZNE (do natychmiastowej naprawy)

#### 13.1.1 Naprawić synchronizację audioGenerationId

**Opcja A: Usunąć VoiceClientManager.audioGenerationId**

```kotlin
// SideEffectExecutor.kt - zmienić:
is SideEffect.QueueAudio -> {
    // Nie używać zewnętrznego generationId
    // AudioEngine sam zarządza swoim generationId
    audioEngine.queueAudioDirect(sideEffect.data)
}

// AudioEngine.kt - dodać:
fun queueAudioDirect(data: ByteArray) {
    val genId = currentGenerationId.get()
    scope.launch {
        audioQueueMutex.withLock {
            audioQueue.add(AudioChunk(genId, data))
        }
    }
}
```

**Opcja B: Zsynchronizować oba ID**

```kotlin
// SideEffectExecutor.kt - zmienić ClearAudioQueue:
is SideEffect.ClearAudioQueue -> {
    // Pobierz nowy generationId z AudioEngine po interruptPlayback
    audioEngine.interruptPlayback()
    // Zsynchronizuj VoiceClientManager.audioGenerationId
    val newGenId = audioEngine.getCurrentGenerationId()
    audioGenerationId.set(newGenId)
}

// AudioEngine.kt - dodać:
fun getCurrentGenerationId(): Int = currentGenerationId.get()
```

#### 13.1.2 Dodać wywołania setBotTalking()

```kotlin
// VoiceSessionStateMachine.kt - w reduceSpeaking przy wejściu:
// Dodać nowy SideEffect:
sealed class SideEffect {
    // ...
    object NotifyBotStartedTalking : SideEffect()
    object NotifyBotStoppedTalking : SideEffect()
}

// SideEffectExecutor.kt:
is SideEffect.NotifyBotStartedTalking -> {
    conversationMonitor?.setBotTalking(true)
}
is SideEffect.NotifyBotStoppedTalking -> {
    conversationMonitor?.setBotTalking(false)
}
```

#### 13.1.3 Obsłużyć SilenceDetected

```kotlin
// VoiceSessionStateMachine.kt - w reduceSpeaking:
is VoiceEvent.SilenceDetected -> {
    Log.i(TAG, "🔇 Silence detected - bot stopped speaking")
    ReduceResult(
        newState = VoiceSessionState.Listening(
            isMicEnabled = state.isMicEnabled,
            isFullDuplex = state.isFullDuplex
        ),
        sideEffects = buildList {
            add(SideEffect.NotifyBotStoppedTalking)
            add(SideEffect.StopPlayback)
            add(SideEffect.StopSilenceDetection)
            add(SideEffect.StartAutoPauseTimer)
            if (!state.isFullDuplex && state.isMicEnabled) {
                add(SideEffect.ResumeRecording)
            }
        }
    )
}
```

### 13.2 WYSOKIE (do naprawy w następnej iteracji)

#### 13.2.1 Usunąć martwy stan Thinking

Stan `Thinking` nigdy nie jest osiągany. Opcje:
1. Usunąć go całkowicie
2. Lub zmienić logikę tak, aby `Listening + UserTranscript → Thinking`

#### 13.2.2 Dodać jawne AEC

```kotlin
// AudioEngine.kt - w startRecording():
private var aec: AcousticEchoCanceler? = null
private var ns: NoiseSuppressor? = null

fun startRecording() {
    // ... po utworzeniu audioRecord ...
    
    // Włącz AEC jeśli dostępne
    if (AcousticEchoCanceler.isAvailable()) {
        aec = AcousticEchoCanceler.create(audioRecord!!.audioSessionId)
        aec?.enabled = true
        Log.i(TAG, "AEC enabled: ${aec?.enabled}")
    }
    
    // Włącz NS jeśli dostępne
    if (NoiseSuppressor.isAvailable()) {
        ns = NoiseSuppressor.create(audioRecord!!.audioSessionId)
        ns?.enabled = true
        Log.i(TAG, "NS enabled: ${ns?.enabled}")
    }
}

fun stopRecording() {
    // ... przed release audioRecord ...
    aec?.release()
    aec = null
    ns?.release()
    ns = null
}
```

#### 13.2.3 Usunąć nieużywane eventy

Usunąć lub oznaczyć jako deprecated:
- `ResumeRequested`
- `BotStartedSpeaking`
- `BotStoppedSpeaking`
- `MicToggled`
- `SpeakerToggled`
- `ImageSelected`

### 13.3 ŚREDNIE (do rozważenia)

#### 13.3.1 Ujednolicić connectionState

Rozważyć usunięcie `WebSocketClient._connectionState` i poleganie tylko na `VoiceSessionState`.

#### 13.3.2 Dodać testy property-based dla maszyny stanów

Testy powinny weryfikować:
- Każdy stan ma zdefiniowane przejścia
- Nie ma nieosiągalnych stanów
- Każdy event jest obsługiwany w co najmniej jednym stanie


---

## 14. WERYFIKACJA WYMAGAŃ UŻYTKOWNIKA

### 14.1 Pauza - 4 przypadki

| Przypadek | Implementacja | Status |
|-----------|---------------|--------|
| Użytkownik pauzuje | `pause()` → `PauseRequested` → `Paused` | ✅ OK |
| Brak aktywności użytkownika | `ConversationMonitor.onAutoPauseTriggered()` → `AutoPauseTriggered` → `Paused` | ✅ OK |
| Brak aktywności bota | `ConversationMonitor.onBotResponseTimeout()` → `BotResponseTimeout` → `Paused` | ⚠️ Tylko w Thinking (który nigdy nie jest osiągany!) |
| Komenda Picovoice ALEXA | `enableMic(false)` → `pause()` | ✅ OK |

**⚠️ PROBLEM:** `BotResponseTimeout` jest obsługiwany tylko w stanie `Thinking`, który nigdy nie jest osiągany!

**Naprawa:** Dodać obsługę `BotResponseTimeout` w stanie `Listening`:

```kotlin
// VoiceSessionStateMachine.kt - w reduceListening:
is VoiceEvent.BotResponseTimeout -> {
    ReduceResult(
        newState = VoiceSessionState.Paused(canResume = true),
        sideEffects = listOf(
            SideEffect.StopRecording,
            SideEffect.StopBotResponseTimer,
            SideEffect.Disconnect(code = 1000, reason = "Bot response timeout"),
            SideEffect.ShowError("No response from bot"),
            SideEffect.UpdateServiceNotification,
            SideEffect.UpdatePicovoiceState
        )
    )
}
```

### 14.2 Odpauzowanie - 2 przypadki

| Przypadek | Implementacja | Status |
|-----------|---------------|--------|
| Użytkownik odpauzuje | `resume()` → `start()` → `StartRequested` → `Connecting` | ✅ OK |
| Picovoice odpauzuje | `enableMic(true)` → `resume()` | ✅ OK |

### 14.3 Full-duplex przerwanie

| Wymaganie | Implementacja | Status |
|-----------|---------------|--------|
| Gemini wysyła `interrupted: true` | `GeminiProtocol.parseServerContent()` sprawdza PIERWSZY | ✅ OK |
| NATYCHMIAST stop audio | `interruptPlayback()` jest SYNCHRONICZNE | ✅ OK |
| Czyszczenie bufora | `audioTrack.flush()` w `interruptPlayback()` | ✅ OK |

### 14.4 Anti-echo w full-duplex

| Wymaganie | Implementacja | Status |
|-----------|---------------|--------|
| Systemowe anti-echo | `VOICE_COMMUNICATION` audio source | ⚠️ Niejawne |
| Jawne AEC | Brak | ❌ BRAK |

---

## 15. PODSUMOWANIE

### 15.1 Główne problemy do naprawy

1. **KRYTYCZNY:** Desynchronizacja `audioGenerationId` między `VoiceClientManager` i `AudioEngine`
2. **KRYTYCZNY:** Brak wywołań `setBotTalking()` w `ConversationMonitor`
3. **WYSOKI:** `SilenceDetected` nie jest obsługiwany w maszynie stanów
4. **WYSOKI:** Stan `Thinking` jest martwy (nigdy nie osiągany)
5. **WYSOKI:** `BotResponseTimeout` obsługiwany tylko w `Thinking` (który jest martwy)
6. **ŚREDNI:** Brak jawnego AEC w `AudioEngine`
7. **NISKI:** Nieużywane eventy zaśmiecają kod

### 15.2 Kolejność napraw

1. **Faza 1:** Naprawić synchronizację `audioGenerationId` (rozwiąże problem duplikacji audio)
2. **Faza 2:** Dodać wywołania `setBotTalking()` i obsługę `SilenceDetected`
3. **Faza 3:** Naprawić obsługę `BotResponseTimeout` (dodać do `Listening`)
4. **Faza 4:** Usunąć martwy stan `Thinking` lub zmienić logikę
5. **Faza 5:** Dodać jawne AEC
6. **Faza 6:** Wyczyścić nieużywane eventy

### 15.3 Szacowany nakład pracy

| Faza | Nakład | Ryzyko |
|------|--------|--------|
| Faza 1 | 2-4h | Wysokie (core audio) |
| Faza 2 | 1-2h | Średnie |
| Faza 3 | 0.5h | Niskie |
| Faza 4 | 1-2h | Średnie |
| Faza 5 | 1h | Niskie |
| Faza 6 | 1h | Niskie |

---

## 16. ZAŁĄCZNIKI

### 16.1 Pliki przeanalizowane

- `VoiceClientManager.kt` (1190 linii)
- `VoiceSessionStateMachine.kt` (751 linii)
- `VoiceEvent.kt` (230 linii)
- `SideEffect.kt` (200 linii)
- `SideEffectExecutor.kt` (250 linii)
- `VoiceUiState.kt` (50 linii)
- `VoiceUiStateMapper.kt` (150 linii)
- `VoiceSessionState.kt` (120 linii)
- `AudioEngine.kt` (1063 linii)
- `WebSocketClient.kt` (350 linii)
- `GeminiProtocol.kt` (350 linii)
- `ConversationMonitor.kt` (280 linii)
- `VoiceClientManagerListeners.kt` (280 linii)

**Łącznie:** ~5274 linii kodu Core

---

*Audyt wykonany: 2025-12-03*
*Autor: Kiro AI Assistant*


---

## 17. WERYFIKACJA PROBLEMÓW (grep search)

### 17.1 Potwierdzenie: audioGenerationId używany tylko w 2 miejscach

```
VoiceClientManager.kt:100 - definicja: private val audioGenerationId = AtomicInteger(0)
VoiceClientManager.kt:229 - przekazanie do SideEffectExecutor
SideEffectExecutor.kt:41 - parametr konstruktora
SideEffectExecutor.kt:126 - użycie: val currentGenId = audioGenerationId.get()
```

**NIGDZIE nie jest inkrementowany!** Tylko `AudioEngine.currentGenerationId` jest inkrementowany w `interruptPlayback()`.

### 17.2 Potwierdzenie: setBotTalking() nigdy nie wywoływany

```
ConversationMonitor.kt:161 - definicja: fun setBotTalking(talking: Boolean)
ConversationMonitorTest.kt:159 - użycie w teście: monitor.setBotTalking(true)
ConversationMonitorTest.kt:165 - użycie w teście: monitor.setBotTalking(false)
```

**Brak wywołań w kodzie produkcyjnym!** Tylko w testach.

---

## 18. KONKRETNE PLIKI DO NAPRAWY

### Faza 1: Synchronizacja audioGenerationId

| Plik | Linia | Zmiana |
|------|-------|--------|
| `VoiceClientManager.kt` | 100 | Usunąć `audioGenerationId` |
| `VoiceClientManager.kt` | 229 | Usunąć przekazanie do SideEffectExecutor |
| `SideEffectExecutor.kt` | 41 | Usunąć parametr `audioGenerationId` |
| `SideEffectExecutor.kt` | 126 | Zmienić na `audioEngine.queueAudioDirect()` |
| `AudioEngine.kt` | - | Dodać `queueAudioDirect()` i `getCurrentGenerationId()` |

### Faza 2: setBotTalking i SilenceDetected

| Plik | Linia | Zmiana |
|------|-------|--------|
| `SideEffect.kt` | - | Dodać `NotifyBotStartedTalking`, `NotifyBotStoppedTalking` |
| `SideEffectExecutor.kt` | - | Dodać obsługę nowych side effects |
| `VoiceSessionStateMachine.kt` | ~230 | Dodać `NotifyBotStartedTalking` przy przejściu do Speaking |
| `VoiceSessionStateMachine.kt` | ~450 | Dodać obsługę `SilenceDetected` w Speaking |
| `VoiceSessionStateMachine.kt` | ~380 | Dodać `NotifyBotStoppedTalking` przy TurnComplete/Interrupted |

### Faza 3: BotResponseTimeout w Listening

| Plik | Linia | Zmiana |
|------|-------|--------|
| `VoiceSessionStateMachine.kt` | ~280 | Dodać obsługę `BotResponseTimeout` w `reduceListening()` |

---

*Koniec raportu audytu*
