# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Task completed - historical record
**Current Documentation:** See /docs/implementation/audio-pipeline.md or /docs/implementation/picovoice-integration.md

---

# Fix: Picovoice Start w Stanie PAUSED

## Problem

Po poprzedniej implementacji:
- Picovoice startował automatycznie w `initializePorcupine()`
- VoiceClientManager też miał aktywny AudioRecord
- **Dwa AudioRecord działały jednocześnie** → błędy `-38` (ENOSYS)
- Picovoice nie reagował na "Alexa" bo nie mógł uzyskać dostępu do mikrofonu

## Rozwiązanie

**Picovoice musi startować w stanie PAUSED** i czekać na broadcast RESUME:

### 1. Zmiana w PorcupineService - Start w stanie PAUSED

**Przed:**
```kotlin
porcupineManager = builder.build(this, callback)

// Start listening
porcupineManager?.start()

Log.d(TAG, "Porcupine initialized and started successfully")
```

**Po:**
```kotlin
porcupineManager = builder.build(this, callback)

// CRITICAL: Do NOT start automatically!
// Porcupine will be started via RESUME_PORCUPINE broadcast when needed
// This prevents AudioRecord conflict with VoiceClientManager
isPorcupinePaused = true

Log.d(TAG, "Porcupine initialized (PAUSED, waiting for RESUME broadcast)")
```

### 2. Auto-resume w RTVIApplication

Dodano automatyczne wznowienie Picovoice 2 sekundy po starcie aplikacji (gdy nie ma aktywnej sesji):

```kotlin
private fun startPorcupineService() {
    try {
        val intent = Intent(this, PorcupineService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Log.i("RTVIApplication", "PorcupineService started")
        
        // Give service time to initialize, then resume Picovoice
        // (no active session at app start, so Picovoice should be listening)
        Handler(Looper.getMainLooper()).postDelayed({
            resumePicovoiceOnAppStart()
        }, 2000) // 2 seconds delay for initialization
        
    } catch (e: Exception) {
        Log.e("RTVIApplication", "Failed to start PorcupineService", e)
    }
}

private fun resumePicovoiceOnAppStart() {
    try {
        // Resume Picovoice since no session is active at app start
        val intent = Intent("ai.pipecat.gemini_multimodal_websocket_demo.RESUME_PORCUPINE")
        intent.setPackage(packageName)
        sendBroadcast(intent)
        Log.i("RTVIApplication", "Picovoice resumed on app start")
    } catch (e: Exception) {
        Log.e("RTVIApplication", "Failed to resume Picovoice", e)
    }
}
```

---

## Flow Diagram

### Uruchomienie aplikacji (POPRAWIONE)

```
App Start
  ↓
RTVIApplication.onCreate()
  ↓
startPorcupineService()
  ↓
PorcupineService.onStartCommand()
  ↓
startForeground() ← Natychmiast
  ↓
initializePorcupine() ← Asynchronicznie
  ↓
porcupineManager.build() ← Tworzy PorcupineManager
  ↓
isPorcupinePaused = true ← PAUSED, NIE startuje AudioRecord
  ↓
[2 sekundy delay]
  ↓
resumePicovoiceOnAppStart()
  ↓
sendBroadcast(RESUME_PORCUPINE)
  ↓
PorcupineService.resumePorcupine()
  ↓
porcupineManager.start() ← Teraz startuje AudioRecord
  ↓
Picovoice nasłuchuje "Alexa" (brak konfliktu, VoiceClientManager nie działa)
```

### Rozpoczęcie sesji

```
User klika "Start"
  ↓
VoiceClientManager.start()
  ↓
setupAudioManager()
  ↓
startAudioRecording() ← VoiceClientManager AudioRecord START
  ↓
updatePicovoiceState() ← isPaused=false, botIsTalking=false
  ↓
sendBroadcast(PAUSE_PORCUPINE)
  ↓
PorcupineService.pausePorcupine()
  ↓
porcupineManager.stop() ← Picovoice AudioRecord STOP
  ↓
VoiceClientManager ma wyłączny dostęp do mikrofonu
```

### Bot mówi

```
Gemini wysyła audio
  ↓
botIsTalking = true
  ↓
stopAudioRecording() ← VoiceClientManager AudioRecord STOP
  ↓
updatePicovoiceState() ← isPaused=false, botIsTalking=true
  ↓
sendBroadcast(RESUME_PORCUPINE)
  ↓
PorcupineService.resumePorcupine()
  ↓
porcupineManager.start() ← Picovoice AudioRecord START
  ↓
Picovoice nasłuchuje "Alexa" (może pauzować podczas odpowiedzi bota)
```

---

## Oczekiwane Logi

### Uruchomienie aplikacji
```
RTVIApplication: PorcupineService started
PorcupineService: PorcupineService onCreate
PorcupineService: Control receiver registered
PorcupineService: PorcupineService onStartCommand
PorcupineService: Started as foreground service
PorcupineService: Initializing Porcupine with 1 wake words
PorcupineService: Porcupine initialized (PAUSED, waiting for RESUME broadcast)
[2 seconds delay]
RTVIApplication: Picovoice resumed on app start
PorcupineService: 🔵 Porcupine RESUMED (AudioRecord started, listening for wake words)
```

### Rozpoczęcie sesji
```
VoiceClientManager: Starting connection...
VoiceClientManager: Setup complete - State transition: CONNECTING -> CONNECTED
VoiceClientManager: Starting audio recording
VoiceClientManager: 🔵 Picovoice PAUSE (user can talk)
PorcupineService: 🔵 Porcupine PAUSED (AudioRecord stopped, VoiceClientManager can use mic)
```

### Bot mówi
```
VoiceClientManager: Bot started speaking
VoiceClientManager: 🎤 AudioRecord stopped (bot speaking, freeing mic for Picovoice)
VoiceClientManager: 🔵 Picovoice RESUME (bot talking)
PorcupineService: 🔵 Porcupine RESUMED (AudioRecord started, listening for wake words)
```

### Wake word wykryty
```
PorcupineService: Wake word detected: alexa (index: 0)
PorcupineService: System wake word detected: alexa
PorcupineService: Sending toggle mic broadcast
MainActivity: Received toggle mic broadcast
VoiceClientManager: 🎤 Toggle microphone - Current state: ON
VoiceClientManager: Mic disabled - pausing session
```

---

## Testowanie

### Test 1: Uruchomienie aplikacji
```powershell
adb -s EM95IBKZEYIFSO69 logcat -c
# Uruchom aplikację
adb -s EM95IBKZEYIFSO69 logcat | Select-String -Pattern "Porcupine|RTVIApplication"
```

**Oczekiwane:**
1. "PorcupineService started"
2. "Porcupine initialized (PAUSED, waiting for RESUME broadcast)"
3. Po 2 sekundach: "Picovoice resumed on app start"
4. "🔵 Porcupine RESUMED"
5. **BRAK błędów AudioRecord `-38`**

### Test 2: Wake word bez sesji
**Kroki:**
1. Uruchom aplikację
2. Poczekaj 3 sekundy (Picovoice się inicjalizuje)
3. Powiedz "Alexa"
4. Sprawdź czy:
   - Picovoice wykrywa wake word
   - Logi: "Wake word detected: alexa"

### Test 3: Rozpoczęcie sesji
**Kroki:**
1. Kliknij "Start" w aplikacji
2. Sprawdź logi:
   - "🔵 Picovoice PAUSE (user can talk)"
   - "🔵 Porcupine PAUSED"
   - **BRAK błędów AudioRecord `-38`**

### Test 4: Bot mówi
**Kroki:**
1. Rozpocznij rozmowę
2. Bot odpowiada
3. Sprawdź logi:
   - "Bot started speaking"
   - "🎤 AudioRecord stopped"
   - "🔵 Picovoice RESUME (bot talking)"
   - "🔵 Porcupine RESUMED"
4. Powiedz "Alexa" podczas gdy bot mówi
5. Sprawdź czy:
   - Picovoice wykrywa wake word
   - Sesja się pauzuje

### Test 5: Zgaszony ekran
**Kroki:**
1. Rozpocznij rozmowę
2. Zgaś ekran
3. Bot mówi → powiedz "Alexa"
4. Sprawdź czy:
   - Wake word działa
   - Aplikacja nie crashuje
   - **BRAK błędów AudioRecord `-38`**

---

## Kluczowe Zmiany

### ✅ Picovoice startuje w stanie PAUSED
- Nie tworzy AudioRecord automatycznie
- Czeka na broadcast RESUME_PORCUPINE
- Zapobiega konfliktowi z VoiceClientManager

### ✅ Auto-resume po 2 sekundach
- Daje czas na inicjalizację Picovoice
- Wznawia Picovoice gdy nie ma aktywnej sesji
- Użytkownik może od razu używać wake word

### ✅ Brak konfliktów AudioRecord
- Tylko jedna instancja AudioRecord aktywna w danym momencie
- Dynamiczne przełączanie przez PAUSE/RESUME broadcasts
- **Brak błędów `-38`**

---

## Pliki Zmodyfikowane

1. **PorcupineService.kt**
   - Zmieniono `initializePorcupine()` - nie wywołuje `start()`
   - Ustawia `isPorcupinePaused = true` po inicjalizacji

2. **RTVIApplication.kt**
   - Dodano `resumePicovoiceOnAppStart()` z 2-sekundowym delay
   - Automatyczne wznowienie Picovoice po starcie aplikacji

---

## Status

✅ **ZAIMPLEMENTOWANO I ZBUDOWANO**
- Build successful
- APK zainstalowany na urządzeniu
- Gotowe do testowania

**Data implementacji:** 2025-01-18
**Czas implementacji:** ~15 minut
**Status:** ✅ Gotowe do testowania - powinno naprawić błędy AudioRecord -38
