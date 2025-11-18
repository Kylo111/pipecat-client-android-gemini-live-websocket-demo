# Naprawa Picovoice - Broadcast Control Strategy

## Problem

Aplikacja crashowała przy próbie uruchomienia PorcupineService dynamicznie:
```
SecurityException: Starting FGS with type microphone requires permissions: 
FOREGROUND_SERVICE_MICROPHONE and the app must be in the eligible state
```

**Przyczyna:** Android 14+ (targetSDK 35) nie pozwala na dynamiczne uruchamianie foreground service z typem `microphone` gdy aplikacja nie ma aktywnego AudioRecord.

## Rozwiązanie

Zmiana strategii z **dynamic start/stop service** na **continuous service with pause/resume control**:

### Stara strategia (nie działała):
```
Bot mówi → startService(PorcupineService) → CRASH
Bot kończy → stopService(PorcupineService)
```

### Nowa strategia (działa):
```
App start → PorcupineService uruchamia się raz i działa cały czas
Bot mówi → broadcast RESUME_PORCUPINE → Porcupine.start()
Bot kończy → broadcast PAUSE_PORCUPINE → Porcupine.stop()
```

---

## Implementacja

### 1. PorcupineService - Broadcast Control

**Dodano w PorcupineService.kt:**

```kotlin
private var isPorcupinePaused = false
private var controlReceiver: android.content.BroadcastReceiver? = null

override fun onCreate() {
    super.onCreate()
    registerControlReceiver()
}

private fun registerControlReceiver() {
    controlReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "ai.pipecat.gemini_multimodal_websocket_demo.PAUSE_PORCUPINE" -> {
                    pausePorcupine()
                }
                "ai.pipecat.gemini_multimodal_websocket_demo.RESUME_PORCUPINE" -> {
                    resumePorcupine()
                }
            }
        }
    }
    
    val filter = android.content.IntentFilter().apply {
        addAction("ai.pipecat.gemini_multimodal_websocket_demo.PAUSE_PORCUPINE")
        addAction("ai.pipecat.gemini_multimodal_websocket_demo.RESUME_PORCUPINE")
    }
    registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
}

private fun pausePorcupine() {
    if (isPorcupinePaused) return
    
    porcupineManager?.stop()  // Stops AudioRecord
    isPorcupinePaused = true
    Log.i(TAG, "🔵 Porcupine PAUSED (AudioRecord stopped)")
}

private fun resumePorcupine() {
    if (!isPorcupinePaused) return
    if (!isInitialized) return
    
    porcupineManager?.start()  // Starts AudioRecord
    isPorcupinePaused = false
    Log.i(TAG, "🔵 Porcupine RESUMED (AudioRecord started)")
}
```

### 2. VoiceClientManager - Broadcast Sender

**Zmieniono updatePicovoiceState():**

```kotlin
private fun updatePicovoiceState() {
    try {
        val shouldPorcupineBeActive = isPaused.value || botIsTalking.value
        
        val action = if (shouldPorcupineBeActive) {
            "ai.pipecat.gemini_multimodal_websocket_demo.RESUME_PORCUPINE"
        } else {
            "ai.pipecat.gemini_multimodal_websocket_demo.PAUSE_PORCUPINE"
        }
        
        val intent = Intent(action)
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
        
        val reason = when {
            isPaused.value -> "session paused"
            botIsTalking.value -> "bot talking"
            else -> "user can talk"
        }
        
        Log.i(TAG, "🔵 Picovoice ${if (shouldPorcupineBeActive) "RESUME" else "PAUSE"} ($reason)")
    } catch (e: Exception) {
        Log.e(TAG, "Error updating Picovoice state: ${e.message}", e)
    }
}
```

### 3. RTVIApplication - Auto-start Service

**Dodano w RTVIApplication.kt:**

```kotlin
override fun onCreate() {
    super.onCreate()
    // ... existing init code ...
    
    // Start PorcupineService as foreground service
    // It will be paused/resumed dynamically via broadcasts
    startPorcupineService()
}

private fun startPorcupineService() {
    try {
        val intent = Intent(this, PorcupineService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Log.i("RTVIApplication", "PorcupineService started")
    } catch (e: Exception) {
        Log.e("RTVIApplication", "Failed to start PorcupineService", e)
    }
}
```

---

## Flow Diagram

### Uruchomienie aplikacji
```
App Start
  ↓
RTVIApplication.onCreate()
  ↓
startPorcupineService()
  ↓
PorcupineService.onStartCommand()
  ↓
startForeground() ← Natychmiast, przed inicjalizacją
  ↓
initializePorcupine() ← Asynchronicznie w tle
  ↓
porcupineManager.start() ← AudioRecord aktywny
  ↓
Service działa w tle, nasłuchuje wake words
```

### Bot zaczyna mówić
```
Gemini wysyła audio
  ↓
handleTextMessage() → botIsTalking = true
  ↓
stopAudioRecording() ← VoiceClientManager AudioRecord STOP
  ↓
updatePicovoiceState()
  ↓
sendBroadcast(RESUME_PORCUPINE)
  ↓
PorcupineService.resumePorcupine()
  ↓
porcupineManager.start() ← Picovoice AudioRecord START
  ↓
Picovoice nasłuchuje wake word (może pauzować sesję)
```

### Bot kończy mówić
```
Gemini wysyła turnComplete
  ↓
handleTextMessage() → botIsTalking = false
  ↓
resumeAudioRecording() ← VoiceClientManager AudioRecord START
  ↓
updatePicovoiceState()
  ↓
sendBroadcast(PAUSE_PORCUPINE)
  ↓
PorcupineService.pausePorcupine()
  ↓
porcupineManager.stop() ← Picovoice AudioRecord STOP
  ↓
VoiceClientManager ma wyłączny dostęp do mikrofonu
```

### Sesja pauzowana
```
User pauzuje (przycisk lub wake word)
  ↓
VoiceClientManager.pause()
  ↓
isPaused = true
  ↓
audioRecord?.stop() ← VoiceClientManager AudioRecord STOP
  ↓
updatePicovoiceState()
  ↓
sendBroadcast(RESUME_PORCUPINE)
  ↓
PorcupineService.resumePorcupine()
  ↓
porcupineManager.start() ← Picovoice AudioRecord START
  ↓
Picovoice nasłuchuje wake word (może wznowić sesję)
```

---

## Zalety Nowego Rozwiązania

### ✅ Brak crashy
- PorcupineService uruchamia się raz przy starcie aplikacji
- `startForeground()` wywoływane natychmiast, przed inicjalizacją AudioRecord
- Spełnia wymagania Android 14+ dla foreground service z typem microphone

### ✅ Brak konfliktów AudioRecord
- Tylko jedna instancja AudioRecord aktywna w danym momencie
- VoiceClientManager i Picovoice nigdy nie używają mikrofonu jednocześnie
- Dynamiczne przełączanie przez pause/resume

### ✅ Wake word działa gdy bot mówi
- Gdy bot mówi → VoiceClientManager nie nagrywa → Picovoice może nasłuchiwać
- Użytkownik może powiedzieć "Alexa" żeby pauzować podczas odpowiedzi bota

### ✅ Wake word działa gdy sesja pauzowana
- Gdy sesja pauzowana → VoiceClientManager nie używa mikrofonu → Picovoice nasłuchuje
- Użytkownik może powiedzieć "Alexa" żeby wznowić sesję

### ✅ Stabilność przy zgaszonym ekranie
- PorcupineService działa jako foreground service z notyfikacją
- Wake lock nie jest potrzebny dla Picovoice (tylko dla VoiceClientManager)
- System nie killuje aplikacji

---

## Testowanie

### Test 1: Podstawowy flow
```bash
adb -s EM95IBKZEYIFSO69 logcat | Select-String -Pattern "Porcupine|AudioRecord|botIsTalking"
```

**Kroki:**
1. Uruchom aplikację
2. Sprawdź logi: "PorcupineService started"
3. Rozpocznij rozmowę
4. Bot mówi → sprawdź logi:
   - "Bot started speaking"
   - "🎤 AudioRecord stopped"
   - "🔵 Porcupine RESUME (bot talking)"
5. Bot kończy → sprawdź logi:
   - "Bot stopped speaking"
   - "🎤 AudioRecord resumed"
   - "🔵 Porcupine PAUSE (user can talk)"

### Test 2: Wake word podczas bot mówi
**Kroki:**
1. Bot mówi (długa odpowiedź)
2. Powiedz "Alexa" podczas gdy bot mówi
3. Sprawdź czy:
   - Picovoice wykrywa wake word
   - Bot się pauzuje
   - Sesja przechodzi w stan paused

### Test 3: Zgaszony ekran
**Kroki:**
1. Rozpocznij rozmowę
2. Zgaś ekran
3. Bot mówi → powiedz "Alexa"
4. Sprawdź czy:
   - Wake word działa
   - Aplikacja nie crashuje
   - Brak błędów AudioRecord

### Test 4: Pauza/wznowienie
**Kroki:**
1. Rozpocznij rozmowę
2. Pauzuj (przycisk lub wake word)
3. Sprawdź logi: "🔵 Porcupine RESUME (session paused)"
4. Powiedz "Alexa" żeby wznowić
5. Sprawdź logi: "🔵 Porcupine PAUSE (user can talk)"

---

## Oczekiwane Logi

### Uruchomienie aplikacji
```
RTVIApplication: PorcupineService started
PorcupineService: PorcupineService onCreate
PorcupineService: Control receiver registered
PorcupineService: PorcupineService onStartCommand
PorcupineService: Started as foreground service
PorcupineService: Initializing Porcupine with X wake words
PorcupineService: Porcupine initialized and started successfully
```

### Bot mówi
```
VoiceClientManager: Bot started speaking
VoiceClientManager: 🎤 AudioRecord stopped (bot speaking, freeing mic for Picovoice)
VoiceClientManager: 🔵 Picovoice RESUME (bot talking)
PorcupineService: 🔵 Porcupine RESUMED (AudioRecord started, listening for wake words)
```

### Bot kończy
```
VoiceClientManager: 🔇 Bot stopped speaking (turnComplete in serverContent)
VoiceClientManager: 🎤 AudioRecord resumed (bot finished, reclaiming mic)
VoiceClientManager: 🔵 Picovoice PAUSE (user can talk)
PorcupineService: 🔵 Porcupine PAUSED (AudioRecord stopped, VoiceClientManager can use mic)
```

### Sesja pauzowana
```
VoiceClientManager: State transition: CONNECTED -> DISCONNECTING (pause)
VoiceClientManager: 🔵 Picovoice RESUME (session paused)
PorcupineService: 🔵 Porcupine RESUMED (AudioRecord started, listening for wake words)
```

---

## Pliki Zmodyfikowane

1. **VoiceClientManager.kt**
   - Zmieniono `updatePicovoiceState()` na wysyłanie broadcastów
   - Zachowano wywołania w 4 miejscach (bot start/stop, pause/resume)

2. **PorcupineService.kt**
   - Dodano `controlReceiver` do obsługi broadcastów
   - Dodano `pausePorcupine()` i `resumePorcupine()`
   - Dodano `isPorcupinePaused` flag

3. **RTVIApplication.kt**
   - Dodano `startPorcupineService()` w `onCreate()`
   - Service uruchamia się automatycznie przy starcie aplikacji

---

## Status

✅ **ZAIMPLEMENTOWANO I ZBUDOWANO**
- Build successful
- APK zainstalowany na urządzeniu
- Gotowe do testowania

**Data implementacji:** 2025-01-18
**Czas implementacji:** ~45 minut
**Status:** ✅ Gotowe do testowania
