# Implementacja Stop/Start AudioRecord - Fix Konfliktu Picovoice

## ✅ Zaimplementowano

### Problem
- VoiceClientManager i Picovoice używały dwóch AudioRecord jednocześnie
- Android nie pozwala na równoczesne użycie mikrofonu przez dwie instancje AudioRecord
- System mógł killować aplikację przy zgaszonym ekranie z powodu konfliktu zasobów

### Rozwiązanie
Implementacja dynamicznego stop/start AudioRecord w VoiceClientManager:
- **Gdy bot mówi** → AudioRecord STOP → zwalnia mikrofon dla Picovoice
- **Gdy bot kończy** → AudioRecord START → przejmuje mikrofon z powrotem
- **Gdy sesja pauzowana** → Picovoice START → może nasłuchiwać wake word
- **Gdy sesja aktywna** → Picovoice STOP → VoiceClientManager ma mikrofon

---

## Zmiany w Kodzie

### 1. Dodano 3 funkcje pomocnicze w VoiceClientManager

```kotlin
/**
 * Stop AudioRecord to free microphone for Picovoice
 * Called when bot starts speaking
 */
private fun stopAudioRecording() {
    try {
        audioRecord?.stop()
        Log.i(TAG, "🎤 AudioRecord stopped (bot speaking, freeing mic for Picovoice)")
    } catch (e: Exception) {
        Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
    }
}

/**
 * Resume AudioRecord after bot stops speaking
 * Called when bot finishes speaking
 */
private fun resumeAudioRecording() {
    try {
        audioRecord?.startRecording()
        Log.i(TAG, "🎤 AudioRecord resumed (bot finished, reclaiming mic)")
    } catch (e: Exception) {
        Log.w(TAG, "Error resuming AudioRecord: ${e.message}")
    }
}

/**
 * Update Picovoice service state based on session state
 * Start Picovoice when bot is talking or session is paused
 * Stop Picovoice when user is talking (to avoid AudioRecord conflict)
 */
private fun updatePicovoiceState() {
    try {
        if (isPaused.value || botIsTalking.value) {
            // Start Picovoice when bot talks or session paused
            val intent = Intent(context, PorcupineService::class.java)
            context.startService(intent)
            Log.i(TAG, "🔵 Picovoice started (${if (isPaused.value) "session paused" else "bot talking"})")
        } else {
            // Stop Picovoice when user talks
            val intent = Intent(context, PorcupineService::class.java)
            context.stopService(intent)
            Log.i(TAG, "🔵 Picovoice stopped (user can talk)")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error updating Picovoice state: ${e.message}")
    }
}
```

### 2. Wywołania w 4 miejscach

#### A) Gdy bot ZACZYNA mówić
**Lokalizacja:** `handleTextMessage()` → serverContent → modelTurn → audio parts

```kotlin
if (!botIsTalking.value) {
    Log.i(TAG, "Bot started speaking")
    botIsTalking.value = true
    stopAudioRecording()      // ← DODANO
    updatePicovoiceState()    // ← DODANO
}
```

#### B) Gdy bot KOŃCZY mówić (2 miejsca)
**Lokalizacja 1:** `handleTextMessage()` → serverContent → turnComplete

```kotlin
if (serverContent?.containsKey("turnComplete") == true) {
    Log.i(TAG, "🔇 Bot stopped speaking (turnComplete in serverContent)")
    botIsTalking.value = false
    resumeAudioRecording()    // ← DODANO
    updatePicovoiceState()    // ← DODANO
}
```

**Lokalizacja 2:** `handleTextMessage()` → root level → turnComplete

```kotlin
if (jsonObject.containsKey("turnComplete")) {
    Log.i(TAG, "🔇 Bot stopped speaking (turnComplete at root)")
    botIsTalking.value = false
    resumeAudioRecording()    // ← DODANO
    updatePicovoiceState()    // ← DODANO
}
```

#### C) Gdy sesja PAUZOWANA
**Lokalizacja:** `pause()`

```kotlin
// Mark as paused and disable mic
isPaused.value = true
mic.value = false

// Stop AudioRecord if still running
audioRecord?.stop()          // ← DODANO

// Update Picovoice state (start it since session is paused)
updatePicovoiceState()       // ← DODANO
```

#### D) Gdy sesja WZNAWIANA
**Lokalizacja:** `resume()`

```kotlin
// Clear paused flag
isPaused.value = false

// Update Picovoice state (stop it since session is resuming)
updatePicovoiceState()       // ← DODANO

// Start auto-pause monitoring
startAutoPauseMonitoring()
```

### 3. Fix recording loop
**Lokalizacja:** `startAudioRecording()` → recording loop

```kotlin
while (isActive && (state.value == CONNECTED || state.value == RECONNECTING)) {
    // Skip reading if bot is talking (AudioRecord is stopped)
    if (botIsTalking.value) {
        delay(100)  // Wait while bot talks
        continue
    }
    
    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
    // ... rest
}
```

---

## Oczekiwane Zachowanie

### Scenariusz 1: Bot mówi
```
1. Bot zaczyna mówić
   → botIsTalking = true
   → AudioRecord STOP
   → Picovoice START
   → Logi: "🎤 AudioRecord stopped (bot speaking, freeing mic for Picovoice)"
   → Logi: "🔵 Picovoice started (bot talking)"

2. Bot kończy mówić
   → botIsTalking = false
   → AudioRecord START
   → Picovoice STOP
   → Logi: "🎤 AudioRecord resumed (bot finished, reclaiming mic)"
   → Logi: "🔵 Picovoice stopped (user can talk)"
```

### Scenariusz 2: Sesja pauzowana
```
1. Użytkownik pauzuje (przycisk lub wake word)
   → isPaused = true
   → AudioRecord STOP
   → Picovoice START
   → Logi: "🔵 Picovoice started (session paused)"

2. Użytkownik wznawia (przycisk lub wake word)
   → isPaused = false
   → Picovoice STOP
   → AudioRecord START (automatycznie po połączeniu)
   → Logi: "🔵 Picovoice stopped (user can talk)"
```

### Scenariusz 3: Wake word podczas bot mówi
```
1. Bot mówi
   → Picovoice jest aktywny (AudioRecord zatrzymany)

2. Użytkownik mówi "Alexa"
   → Picovoice wykrywa wake word
   → Wysyła broadcast do MainActivity
   → MainActivity wywołuje voiceClientManager.toggleMic()
   → Sesja się pauzuje
```

---

## Testowanie

### Test 1: Podstawowy flow
```bash
./gradlew :gemini-multimodal-websocket-demo:installDebug
adb -s EM95IBKZEYIFSO69 logcat -c
adb -s EM95IBKZEYIFSO69 logcat | grep -E "AudioRecord|Picovoice|botIsTalking"
```

**Kroki:**
1. Uruchom aplikację
2. Rozpocznij rozmowę
3. Powiedz coś → bot odpowiada
4. Sprawdź logi:
   - "Bot started speaking"
   - "🎤 AudioRecord stopped"
   - "🔵 Picovoice started"
   - "Bot stopped speaking"
   - "🎤 AudioRecord resumed"
   - "🔵 Picovoice stopped"

### Test 2: Wake word podczas bot mówi
**Kroki:**
1. Bot mówi (długa odpowiedź)
2. Powiedz "Alexa" podczas gdy bot mówi
3. Sprawdź czy:
   - Bot się pauzuje
   - Picovoice wykrywa wake word
   - Sesja przechodzi w stan paused

### Test 3: Zgaszony ekran
**Kroki:**
1. Rozpocznij rozmowę
2. Zgaś ekran
3. Bot mówi → powiedz "Alexa"
4. Sprawdź czy:
   - Wake word działa
   - Aplikacja nie crashuje
   - Brak konfliktów AudioRecord

### Test 4: Długa sesja
**Kroki:**
1. Rozpocznij rozmowę
2. Prowadź rozmowę przez 5-10 minut
3. Zgaś i włącz ekran kilka razy
4. Używaj wake word kilka razy
5. Sprawdź czy:
   - Brak memory leaks
   - Brak crashy
   - Wake word działa stabilnie

---

## Logi do Monitorowania

### Sukces
```
Bot started speaking
🎤 AudioRecord stopped (bot speaking, freeing mic for Picovoice)
🔵 Picovoice started (bot talking)

Bot stopped speaking (turnComplete in serverContent)
🎤 AudioRecord resumed (bot finished, reclaiming mic)
🔵 Picovoice stopped (user can talk)
```

### Pauza
```
State transition: CONNECTED -> DISCONNECTING (pause - session handle preserved)
🔵 Picovoice started (session paused)
🔄 Pausing session - session handle preserved for resumption
```

### Wznowienie
```
🔵 Picovoice stopped (user can talk)
🔄 Resuming session with handle: ...
```

---

## Pliki Zmodyfikowane

- `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/VoiceClientManager.kt`
  - Dodano 3 funkcje pomocnicze
  - Dodano 6 wywołań w strategicznych miejscach
  - Dodano check w recording loop

---

## Status

✅ **ZAIMPLEMENTOWANO I ZBUDOWANO**
- Build successful
- APK zainstalowany na urządzeniu
- Gotowe do testowania

## Następne Kroki

1. **Testowanie podstawowe** (5 min)
   - Uruchom aplikację
   - Sprawdź logi podczas rozmowy
   - Zweryfikuj stop/start AudioRecord

2. **Testowanie wake word** (5 min)
   - Test wake word podczas bot mówi
   - Test wake word przy zgaszonym ekranie

3. **Testowanie stabilności** (10 min)
   - Długa sesja
   - Wielokrotne pause/resume
   - Zgaszony ekran

4. **Raport z testów**
   - Zapisz logi
   - Zgłoś problemy jeśli wystąpią
   - Potwierdź sukces

---

**Data implementacji:** 2025-01-18
**Czas implementacji:** ~30 minut
**Status:** ✅ Gotowe do testowania
