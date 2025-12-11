# Integracja Auto-Mute w Nowym Systemie

**Data:** 2025-12-11
**Status:** ✅ ZAIMPLEMENTOWANE

---

## Podsumowanie

Zaimplementowano automatyczne mute (zamiast pauzy) w nowym uproszczonym systemie audio. Kluczowa różnica: **mute utrzymuje połączenie WebSocket aktywne**, co eliminuje koszty reconnect i zapewnia natychmiastowe wznowienie.

---

## Nowy Komponent: AutoMuteMonitor

**Lokalizacja:** `audio/simple/AutoMuteMonitor.kt`

### Odpowiedzialności

1. **User Inactivity Timer** - Mute po braku aktywności użytkownika
2. **Bot Response Timeout** - Mute gdy bot nie odpowiada przez określony czas

### Kluczowe Różnice vs ConversationMonitor

| Aspekt | ConversationMonitor (stary) | AutoMuteMonitor (nowy) |
|--------|----------------------------|------------------------|
| **Akcja** | Pause (disconnect WebSocket) | Mute (keep connection) |
| **Koszt** | Reconnect = tokeny setup | Zero kosztów |
| **Latencja** | ~500-1000ms reconnect | ~0ms (natychmiastowe) |
| **Silence Detection** | Tak (fallback dla turnComplete) | Nie (Gemini turnComplete wystarczy) |
| **Linie kodu** | ~330 | ~220 |

---

## Przepływ Danych - Auto-Mute

### Trigger 1: User Inactivity

```
┌─────────────────┐
│ AudioEngine     │ ← Nagrywa audio
└────────┬────────┘
         │ onAudioRecorded
         ▼
┌─────────────────┐
│ VoiceClientMgr  │ ← Sprawdza audio level
│ audioLevel > 0.02? │
└────────┬────────┘
         │ YES: resetAutoMuteTimer(audioLevel)
         ▼
┌─────────────────┐
│ AutoMuteMonitor │ ← Reset timer do 60s
│ lastActivityTime = now │
└─────────────────┘

... 60 sekund bez aktywności ...

┌─────────────────┐
│ AutoMuteMonitor │ ← Timer wygasł
│ onAutoMuteTriggered() │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ VoiceClientMgr  │ ← setMuted(true)
│ _isMuted = true │
└─────────────────┘

Efekt:
- Audio NADAL jest nagrywane
- Audio NIE jest wysyłane do Gemini
- WebSocket POZOSTAJE połączony
- Koszt: $0.00
```

### Trigger 2: Bot Response Timeout

```
┌─────────────────┐
│ GeminiClient    │ ← Otrzymuje audio od bota
│ onAudio()       │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ VoiceClientMgr  │ ← onGeminiAudio()
│ updateBotResponseTime() │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ AutoMuteMonitor │ ← Reset timer do 5min
│ lastBotResponseTime = now │
└─────────────────┘

... 5 minut bez odpowiedzi bota ...

┌─────────────────┐
│ AutoMuteMonitor │ ← Timer wygasł
│ onBotResponseTimeout() │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ VoiceClientMgr  │ ← setMuted(true)
│ _isMuted = true │
└─────────────────┘

Efekt:
- Mikrofon jest muted
- WebSocket POZOSTAJE połączony
- Użytkownik może unmute i kontynuować
```

---

## Integracja z VoiceClientManager

### Parametry Konstruktora

```kotlin
class VoiceClientManager(
    private val context: Context,
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash-exp",
    private val autoMuteTimeoutSeconds: Int = 60,        // ← NOWE
    private val botResponseTimeoutMinutes: Int = 5,      // ← NOWE
    private val activityThreshold: Float = 0.02f         // ← NOWE
)
```

### Inicjalizacja

```kotlin
private val autoMuteMonitor = AutoMuteMonitor(
    scope = scope,
    autoMuteTimeoutSeconds = autoMuteTimeoutSeconds,
    botResponseTimeoutMinutes = botResponseTimeoutMinutes,
    activityThreshold = activityThreshold
)
```

### Exposed State

```kotlin
// Timer state (exposed for UI)
val secondsUntilAutoMute: StateFlow<Int> = autoMuteMonitor.secondsUntilAutoMute
val minutesUntilBotTimeout: StateFlow<Int> = autoMuteMonitor.minutesUntilBotTimeout
```

### Event Wiring

```kotlin
private fun wireAutoMuteMonitor() {
    autoMuteMonitor.listener = object : AutoMuteMonitorListener {
        override fun onAutoMuteTriggered() {
            Log.i(TAG, "⏱️ Auto-mute triggered - user inactivity")
            setMuted(true)
        }
        
        override fun onBotResponseTimeout() {
            Log.i(TAG, "⏱️ Bot response timeout - muting microphone")
            setMuted(true)
        }
    }
}
```

### Lifecycle Integration

**Connect:**
```kotlin
suspend fun connect(...) {
    // ... setup ...
    
    // Start auto-mute timers
    autoMuteMonitor.startAutoMuteTimer()
    autoMuteMonitor.startBotResponseTimer()
}
```

**Disconnect:**
```kotlin
fun disconnect() {
    // Stop auto-mute timers
    autoMuteMonitor.stopAutoMuteTimer()
    autoMuteMonitor.stopBotResponseTimer()
    
    // ... cleanup ...
}
```

**SetMuted:**
```kotlin
fun setMuted(muted: Boolean) {
    _isMuted.value = muted
    Log.i(TAG, "🎤 Muted: $muted")
    
    // When unmuting, restart timers
    if (!muted && _connectionState.value == ConnectionState.CONNECTED) {
        autoMuteMonitor.startAutoMuteTimer()
        autoMuteMonitor.startBotResponseTimer()
    }
}
```

**Release:**
```kotlin
fun release() {
    disconnect()
    autoMuteMonitor.release()
    audioEngine.release()
}
```

---

## Integracja z VoiceClientManagerSimple

### Przekazywanie Parametrów z Preferences

```kotlin
fun start(settings: ThreadSettings? = null) {
    // Get auto-mute settings from preferences
    val autoMuteTimeoutSeconds = Preferences.autoPauseTimeoutSeconds.value
    val botResponseTimeoutMinutes = Preferences.botResponseTimeoutMinutes.value
    val activityThreshold = Preferences.activityDetectionThreshold.value
    
    // Create new simplified manager
    simpleManager = SimpleVoiceClientManager(
        context = context,
        apiKey = apiKey,
        model = model,
        autoMuteTimeoutSeconds = autoMuteTimeoutSeconds,
        botResponseTimeoutMinutes = botResponseTimeoutMinutes,
        activityThreshold = activityThreshold
    )
    
    // Wire state updates
    wireStateUpdates()
    
    // ...
}
```

### Mapowanie Timer State

```kotlin
private fun wireStateUpdates() {
    val manager = simpleManager ?: return
    
    // Observe auto-mute timer
    scope.launch {
        manager.secondsUntilAutoMute.collect { seconds ->
            _uiState.value = _uiState.value.copy(secondsUntilAutoPause = seconds)
        }
    }
    
    // Observe bot response timeout timer
    scope.launch {
        manager.minutesUntilBotTimeout.collect { minutes ->
            _uiState.value = _uiState.value.copy(minutesUntilBotTimeout = minutes)
        }
    }
    
    // Observe muted state
    scope.launch {
        snapshotFlow { manager.isMuted.value }
            .collect { isMuted ->
                _uiState.value = _uiState.value.copy(
                    isPaused = isMuted,
                    isMicEnabled = !isMuted
                )
            }
    }
}
```

---

## Ustawienia w UI (SettingsScreen)

### Auto-Pause Timeout (teraz Auto-Mute)

```kotlin
// Slider: 10s - 120s
val autoPauseTimeout by remember { 
    mutableStateOf(Preferences.autoPauseTimeoutSeconds.value) 
}

Slider(
    value = autoPauseTimeout.toFloat(),
    onValueChange = { autoPauseTimeout = it.toInt() },
    valueRange = 10f..120f,
    steps = 21
)

// Opis:
"Czas bezczynności użytkownika po którym sesja jest pauzowana 
(bot mówiący nie liczy się jako aktywność)"
```

**UWAGA:** Opis w UI mówi "pauzowana", ale faktycznie jest to **mute** (nie disconnect).

### Bot Response Timeout

```kotlin
// Slider: 1min - 15min
val botResponseTimeout by remember { 
    mutableStateOf(Preferences.botResponseTimeoutMinutes.value) 
}

Slider(
    value = botResponseTimeout.toFloat(),
    onValueChange = { botResponseTimeout = it.toInt() },
    valueRange = 1f..15f,
    steps = 13
)

// Opis:
"Czas po którym sesja jest pauzowana jeśli bot nie odpowiada"
```

### Activity Detection Threshold

```kotlin
// Slider: 0.0 - 0.1 (0% - 10%)
val activityThreshold by remember { 
    mutableStateOf(Preferences.activityDetectionThreshold.value) 
}

Slider(
    value = activityThreshold,
    onValueChange = { activityThreshold = it },
    valueRange = 0f..0.1f
)

// Opis:
"Próg poziomu audio dla detekcji aktywności użytkownika"
```

---

## Preferences Storage

**Lokalizacja:** `Preferences.kt`

```kotlin
val autoPauseTimeoutSeconds = IntPref(
    PREF_AUTO_PAUSE_TIMEOUT_SECONDS, 
    60  // Default: 60 seconds
)

val botResponseTimeoutMinutes = IntPref(
    "bot_response_timeout_minutes", 
    5   // Default: 5 minutes
)

val activityDetectionThreshold = FloatPref(
    PREF_ACTIVITY_DETECTION_THRESHOLD, 
    0.02f  // Default: 2% audio level
)
```

---

## Detekcja Aktywności Użytkownika

### Algorytm

```kotlin
// 1. Nagraj audio
audioEngine.onAudioRecorded = { audioData ->
    if (!_isMuted.value) {
        geminiClient.sendAudio(audioData)
        
        // 2. Oblicz poziom audio (RMS)
        val audioLevel = updateUserAudioLevel(audioData)
        
        // 3. Sprawdź czy przekracza próg
        // 4. Jeśli tak, zresetuj timer
        autoMuteMonitor.resetAutoMuteTimer(audioLevel)
    }
}
```

### Próg Aktywności

```kotlin
fun resetAutoMuteTimer(audioLevel: Float = 1.0f) {
    // Only reset if audio level exceeds threshold
    if (audioLevel >= activityThreshold && autoMuteJob?.isActive == true) {
        lastActivityTime = System.currentTimeMillis()
        _secondsUntilAutoMute.value = autoMuteTimeoutSeconds
    }
}
```

**Domyślny próg:** 0.02 (2% maksymalnego poziomu)
- Filtruje szum tła
- Wykrywa faktyczną mowę użytkownika
- Można dostosować w Settings

---

## Bot Talking State

### Wpływ na Auto-Mute Timer

Gdy bot mówi, timer bezczynności użytkownika jest **pauzowany**:

```kotlin
fun setBotTalking(talking: Boolean) {
    isBotTalking = talking
    if (talking && autoMuteJob?.isActive == true) {
        // Reset timer when bot starts talking
        lastActivityTime = System.currentTimeMillis()
        _secondsUntilAutoMute.value = autoMuteTimeoutSeconds
    }
}
```

**Integracja:**
```kotlin
private fun onGeminiAudio(audioData: ByteArray) {
    if (!_isBotSpeaking.value) {
        _isBotSpeaking.value = true
        autoMuteMonitor.setBotTalking(true)  // ← Pauzuje user inactivity timer
    }
    // ...
}

private fun onGeminiTurnComplete() {
    // Wait for playback to finish
    scope.launch {
        while (!audioEngine.isPlaybackFinished()) {
            delay(50)
        }
        
        _isBotSpeaking.value = false
        autoMuteMonitor.setBotTalking(false)  // ← Wznawia user inactivity timer
    }
}
```

---

## UI Display

### Timer Countdown

**Auto-Mute Timer:**
```kotlin
val secondsUntilAutoMute: StateFlow<Int>

// UI:
if (secondsUntilAutoMute > 0) {
    Text("Auto-mute in ${secondsUntilAutoMute}s")
}
```

**Bot Response Timer:**
```kotlin
val minutesUntilBotTimeout: StateFlow<Int>

// UI:
if (minutesUntilBotTimeout > 0) {
    Text("Bot timeout in ${minutesUntilBotTimeout}min")
}
```

**Disabled State:**
- Wartość `-1` oznacza timer wyłączony
- Nie wyświetlaj countdown gdy `-1`

---

## Porównanie: Pause vs Mute

### Stary System (Pause)

```
User Inactivity (60s)
    ↓
AutoPauseTriggered event
    ↓
State Machine: Listening → Paused
    ↓
Side Effects:
- StopRecording
- StopAutoPauseTimer
- Disconnect(code=1000, reason="Auto-pause timeout")
- UpdateServiceNotification
    ↓
WebSocket CLOSED
    ↓
Resume:
- Reconnect WebSocket (~500-1000ms)
- Setup session (tokeny!)
- StartRecording
```

**Koszt:** Reconnect + setup = tokeny

### Nowy System (Mute)

```
User Inactivity (60s)
    ↓
onAutoMuteTriggered()
    ↓
setMuted(true)
    ↓
_isMuted = true
    ↓
Audio recording CONTINUES
Audio sending STOPS
WebSocket REMAINS CONNECTED
    ↓
Unmute:
- setMuted(false)
- Audio sending RESUMES immediately
```

**Koszt:** $0.00

---

## Korzyści Nowego Podejścia

### 1. Zero Kosztów Idle Time

- Mute nie wysyła audio → zero tokenów
- WebSocket idle → zero kosztów
- Unmute natychmiastowe → zero reconnect

### 2. Lepsza UX

- Unmute jest **natychmiastowe** (0ms vs 500-1000ms)
- Brak "connecting..." po unmute
- Płynniejsze przejścia

### 3. Prostszy Kod

- Brak state machine transitions
- Brak disconnect/reconnect logic
- Mniej side effects

### 4. Mniej Błędów

- Brak reconnection failures
- Brak race conditions podczas reconnect
- Brak zombie audio po reconnect

---

## Testy

### Test 1: User Inactivity Auto-Mute

```bash
# 1. Uruchom aplikację
./gradlew installDebug

# 2. Połącz się z Gemini
# 3. Nie mów przez 60 sekund
# 4. Sprawdź logi

adb -s EM95IBKZEYIFSO69 logcat | grep -E "Auto-mute|setMuted"
```

**Oczekiwany wynik:**
```
AutoMuteMonitor: Auto-mute monitoring started (timeout: 60s)
... 60 sekund ...
AutoMuteMonitor: ⏱️ Auto-mute triggered - no user activity for 60s
VoiceClientManager: ⏱️ Auto-mute triggered - user inactivity
VoiceClientManager: 🎤 Muted: true
```

**Weryfikacja:**
- Mikrofon jest muted (UI pokazuje czerwony przycisk)
- WebSocket POZOSTAJE połączony (brak "Disconnected" w logach)
- Timer countdown znika (secondsUntilAutoMute = -1)

### Test 2: Bot Response Timeout

```bash
# 1. Uruchom aplikację
# 2. Połącz się z Gemini
# 3. Powiedz coś do bota
# 4. Czekaj 5 minut bez odpowiedzi bota
# 5. Sprawdź logi

adb -s EM95IBKZEYIFSO69 logcat | grep -E "Bot response timeout|setMuted"
```

**Oczekiwany wynik:**
```
AutoMuteMonitor: Bot response timeout monitoring started (timeout: 5min)
... 5 minut ...
AutoMuteMonitor: ⏱️ Bot response timeout triggered - no response for 5min
VoiceClientManager: ⏱️ Bot response timeout - muting microphone
VoiceClientManager: 🎤 Muted: true
```

### Test 3: Activity Detection

```bash
# 1. Uruchom aplikację
# 2. Połącz się z Gemini
# 3. Mów cicho (poniżej progu)
# 4. Sprawdź czy timer się resetuje

adb -s EM95IBKZEYIFSO69 logcat | grep -E "Auto-mute timer reset|audio level"
```

**Oczekiwany wynik:**
- Mowa powyżej progu (>0.02): timer resetuje się
- Szum poniżej progu (<0.02): timer NIE resetuje się

### Test 4: Bot Talking Pauses Timer

```bash
# 1. Uruchom aplikację
# 2. Połącz się z Gemini
# 3. Zadaj pytanie
# 4. Obserwuj timer podczas odpowiedzi bota

adb -s EM95IBKZEYIFSO69 logcat | grep -E "Bot started speaking|setBotTalking|Auto-mute"
```

**Oczekiwany wynik:**
```
VoiceClientManager: 🤖 Bot started speaking
AutoMuteMonitor: setBotTalking(true)
... bot mówi przez 30 sekund ...
VoiceClientManager: 🎵 Playback finished
AutoMuteMonitor: setBotTalking(false)
AutoMuteMonitor: Auto-mute timer reset to 60s
```

### Test 5: Unmute Restarts Timers

```bash
# 1. Uruchom aplikację
# 2. Połącz się z Gemini
# 3. Kliknij MUTE
# 4. Czekaj 10 sekund
# 5. Kliknij UNMUTE
# 6. Sprawdź logi

adb -s EM95IBKZEYIFSO69 logcat | grep -E "setMuted|startAutoMuteTimer|startBotResponseTimer"
```

**Oczekiwany wynik:**
```
VoiceClientManager: 🎤 Muted: true
... 10 sekund ...
VoiceClientManager: 🎤 Muted: false
AutoMuteMonitor: Auto-mute monitoring started (timeout: 60s)
AutoMuteMonitor: Bot response timeout monitoring started (timeout: 5min)
```

---

## Migracja ze Starego Systemu

### Zmiany w Nazewnictwie

| Stary System | Nowy System |
|--------------|-------------|
| `autoPauseTimeoutSeconds` | `autoMuteTimeoutSeconds` |
| `AutoPauseTriggered` | `onAutoMuteTriggered()` |
| `StartAutoPauseTimer` | `startAutoMuteTimer()` |
| `StopAutoPauseTimer` | `stopAutoMuteTimer()` |
| `secondsUntilAutoPause` | `secondsUntilAutoMute` |

### Zachowana Kompatybilność

**Preferences:**
- Klucz `autoPauseTimeoutSeconds` pozostaje bez zmian
- UI nadal pokazuje "Automatyczne pauzowanie"
- Wartości domyślne bez zmian

**UI State:**
- `secondsUntilAutoPause` mapowane z `secondsUntilAutoMute`
- `isPaused` mapowane z `isMuted`
- `isMicEnabled` mapowane z `!isMuted`

---

## Znane Ograniczenia

### 1. Brak Silence Detection

Nowy system **nie ma** bot silence detection (fallback dla turnComplete).

**Uzasadnienie:**
- Gemini Live API zawsze wysyła `turnComplete`
- Silence detection była workaround dla starych problemów
- Uproszczenie kodu

**Jeśli potrzebne:**
- Można dodać w przyszłości
- Wzorować się na `ConversationMonitor.startSilenceDetection()`

### 2. Timer Granularity

Timery sprawdzają co 1 sekundę (nie co 100ms).

**Wpływ:**
- Countdown może być opóźniony o max 1s
- Nie wpływa na funkcjonalność
- Oszczędza CPU

---

## Przyszłe Ulepszenia

### 1. Configurable Timer Granularity

```kotlin
class AutoMuteMonitor(
    // ...
    private val timerGranularityMs: Long = 1000L  // Default: 1s
)
```

### 2. Adaptive Threshold

```kotlin
// Automatycznie dostosuj próg na podstawie poziomu szumu tła
fun calibrateThreshold() {
    // Measure background noise for 5 seconds
    // Set threshold = noise_level * 1.5
}
```

### 3. Visual Feedback

```kotlin
// Pokaż countdown w UI
@Composable
fun AutoMuteCountdown(seconds: Int) {
    if (seconds in 1..10) {
        Text(
            text = "Auto-mute in ${seconds}s",
            color = Color.Orange
        )
    }
}
```

### 4. Notification

```kotlin
// Powiadom użytkownika przed auto-mute
if (remaining == 10) {
    showNotification("Microphone will be muted in 10 seconds")
}
```

---

## Podsumowanie

✅ **Zaimplementowano:**
- AutoMuteMonitor (220 linii)
- Integracja z VoiceClientManager
- Integracja z VoiceClientManagerSimple
- Mapowanie timer state do UI
- Detekcja aktywności użytkownika
- Bot talking state management

✅ **Korzyści:**
- Zero kosztów podczas idle time
- Natychmiastowe unmute (0ms latencja)
- Prostszy kod (brak state machine)
- Lepsza UX

✅ **Kompatybilność:**
- Preferences bez zmian
- UI bez zmian (tylko semantyka)
- Wszystkie ustawienia działają

**Ostatnia aktualizacja:** 2025-12-11
