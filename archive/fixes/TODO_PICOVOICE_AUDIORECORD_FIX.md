# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Task completed - historical record
**Current Documentation:** See /docs/implementation/audio-pipeline.md or /docs/implementation/picovoice-integration.md

---

# TODO: Fix Picovoice AudioRecord Conflict

## Problem
- VoiceClientManager i Picovoice używają dwóch AudioRecord jednocześnie
- Android nie pozwala → konflikt → system może killować przy zgaszonym ekranie

## Rozwiązanie
Stop/Start AudioRecord w VoiceClientManager gdy bot mówi → zwalnia mikrofon dla Picovoice

---

## Do Zrobienia (30 min)

### 1. Dodać 3 funkcje pomocnicze w VoiceClientManager (~10 linii)

```kotlin
private fun stopAudioRecording() {
    audioRecord?.stop()
    Log.i(TAG, "🎤 AudioRecord stopped")
}

private fun resumeAudioRecording() {
    audioRecord?.startRecording()
    Log.i(TAG, "🎤 AudioRecord resumed")
}

private fun updatePicovoiceState() {
    if (isPaused.value || botIsTalking.value) {
        // Start Picovoice when bot talks or session paused
        val intent = Intent(context, PorcupineService::class.java)
        context.startService(intent)
        Log.i(TAG, "🔵 Picovoice started")
    } else {
        // Stop Picovoice when user talks
        val intent = Intent(context, PorcupineService::class.java)
        context.stopService(intent)
        Log.i(TAG, "🔵 Picovoice stopped")
    }
}
```

### 2. Wywołać w 4 miejscach

**A) Gdy bot ZACZYNA mówić** (szukaj: `botIsTalking.value = true`)
```kotlin
botIsTalking.value = true
stopAudioRecording()      // ← DODAĆ
updatePicovoiceState()    // ← DODAĆ
```

**B) Gdy bot KOŃCZY mówić** (szukaj: `botIsTalking.value = false`)
```kotlin
botIsTalking.value = false
resumeAudioRecording()    // ← DODAĆ
updatePicovoiceState()    // ← DODAĆ
```

**C) Gdy sesja PAUZOWANA** (w funkcji `pause()`)
```kotlin
isPaused.value = true
audioRecord?.stop()       // ← DODAĆ (jeśli nie ma)
updatePicovoiceState()    // ← DODAĆ
```

**D) Gdy sesja WZNAWIANA** (w funkcji `resume()`)
```kotlin
isPaused.value = false
updatePicovoiceState()    // ← DODAĆ
// AudioRecord startuje automatycznie po połączeniu
```

### 3. Opcjonalnie: Fix recording loop

W recording loop dodać check (jeśli AudioRecord.read() zwraca błąd gdy stopped):
```kotlin
while (isActive && state.value == CONNECTED) {
    if (botIsTalking.value) {
        delay(100)  // Wait while bot talks
        continue
    }
    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
    // ... rest
}
```

---

## Testowanie (15 min)

1. **Build & Install**
   ```bash
   ./gradlew clean build
   ./gradlew installDebug
   ```

2. **Test 1: Podstawowy flow**
   - Powiedz coś → bot odpowiada → sprawdź logi
   - Szukaj: `AudioRecord stopped`, `Picovoice started`

3. **Test 2: Wake word podczas bot mówi**
   - Bot mówi → powiedz "Alexa" → bot się pauzuje

4. **Test 3: Zgaszony ekran**
   - Zgaś ekran → bot mówi → "Alexa" → sprawdź czy działa

---

## Oczekiwane Logi

```
Bot started speaking
🎤 AudioRecord stopped
🔵 Picovoice started

Bot stopped speaking  
🎤 AudioRecord resumed
🔵 Picovoice stopped
```

---

## Pliki do Edycji

- `VoiceClientManager.kt` - dodać 3 funkcje + 4 wywołania

**To wszystko!**
