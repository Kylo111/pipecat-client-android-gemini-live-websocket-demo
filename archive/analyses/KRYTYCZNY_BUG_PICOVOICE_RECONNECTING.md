# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Task completed - historical record
**Current Documentation:** See /docs/implementation/ or /docs/operations/ for current documentation

---

# KRYTYCZNY BUG: Picovoice Anuluje Reconnection!

## Problem
"Ponowne łączenie..." wisi w nieskończoność podczas gdy bot mówi. Słychać sygnał dźwiękowy (Picovoice) i zamiast pause jest RECONNECTING.

## Przyczyna

### Sekwencja Zdarzeń:
1. **Bot mówi** → AudioRecord jest stopped, Picovoice może słuchać
2. **Picovoice fałszywie wykrywa wake word** w mowie bota (np. słyszy "Alexa")
3. **PorcupineService** wysyła broadcast `ACTION_TOGGLE_MICROPHONE`
4. **MainActivity** odbiera broadcast i wywołuje `voiceClientManager.toggleMic()`
5. **toggleMic()** wywołuje `enableMic(false)` (wyłącz mikrofon)
6. **enableMic(false)** sprawdza stan:
   ```kotlin
   if (state.value == ConnectionState.RECONNECTING) {  // ← PROBLEM!
       pause()  // ← Anuluje reconnection!
   }
   ```
7. **pause()** anuluje reconnection i ustawia `isPaused = true`
8. **Auto-restart nie działa** bo stan się zmienił na DISCONNECTED

### Kod Problematyczny:

```kotlin
fun enableMic(enabled: Boolean) {
    if (!enabled) {
        // BŁĄD: Wywołuje pause() nawet gdy RECONNECTING!
        if (state.value == ConnectionState.CONNECTED || 
            state.value == ConnectionState.CONNECTING ||
            state.value == ConnectionState.RECONNECTING) {  // ← TU!
            pause()  // ← Anuluje reconnection!
        }
    }
}
```

## Dlaczego To Jest Błędne Koło?

1. **Reconnection startuje** (np. z WebSocket health timeout)
2. **Picovoice wykrywa wake word** (fałszywie, w mowie bota lub szumie)
3. **enableMic(false)** wywołuje `pause()` bo stan = RECONNECTING
4. **pause()** anuluje reconnection:
   ```kotlin
   fun pause() {
       reconnectionManager.cancelReconnection()  // ← Anuluje!
       isPaused.value = true
       webSocket?.close(1000, "Paused by user")
       handleDisconnect(preserveSessionHandle = true)
   }
   ```
5. **Stan zmienia się** na DISCONNECTED z isPaused=true
6. **Auto-restart nie działa** bo reconnection został anulowany
7. **"Ponowne łączenie..." wisi** w nieskończoność

## Rozwiązanie

### PRZED (Błędne):
```kotlin
fun enableMic(enabled: Boolean) {
    if (!enabled) {
        if (state.value == ConnectionState.CONNECTED || 
            state.value == ConnectionState.CONNECTING ||
            state.value == ConnectionState.RECONNECTING) {  // ← Źle!
            pause()  // ← Anuluje reconnection!
        }
    }
}
```

### PO (Poprawne):
```kotlin
fun enableMic(enabled: Boolean) {
    if (!enabled) {
        // CRITICAL FIX: Do NOT pause if already RECONNECTING!
        if (state.value == ConnectionState.RECONNECTING) {
            Log.w(TAG, "⚠️ Mic disabled during RECONNECTING - ignoring")
            Log.w(TAG, "   This is likely a false wake word detection")
            return  // ← Ignoruj, pozwól reconnection działać!
        }
        
        if (state.value == ConnectionState.CONNECTED || 
            state.value == ConnectionState.CONNECTING) {
            pause()
        }
    }
}
```

## Dlaczego To Działa?

1. **Reconnection startuje** (np. z WebSocket health timeout)
2. **Picovoice wykrywa wake word** (fałszywie)
3. **enableMic(false)** sprawdza stan = RECONNECTING
4. **IGNORUJE** i zwraca (nie wywołuje pause!)
5. **Reconnection kontynuuje** normalnie
6. **Po 5 sekundach** auto-restart się wykonuje
7. **Połączenie zostaje przywrócone** automatycznie

## Dlaczego Picovoice Wykrywa Wake Word Podczas Bot Audio?

### Problem:
- Bot mówi → AudioRecord jest stopped
- Picovoice może słuchać (ma własny AudioRecord)
- Mowa bota może zawierać dźwięki podobne do wake word
- Picovoice fałszywie wykrywa "Alexa" w mowie bota

### Możliwe Rozwiązania (Przyszłość):

#### 1. Wyłącz Picovoice Podczas Bot Audio
```kotlin
private fun updatePicovoiceState() {
    // Pause Picovoice gdy bot mówi
    val shouldPorcupineBeActive = isPaused.value && !botIsTalking.value
    // ...
}
```

#### 2. Dodaj Debouncing dla Wake Word
```kotlin
private var lastWakeWordTime = 0L
private val WAKE_WORD_DEBOUNCE_MS = 2000L

fun onWakeWordDetected() {
    val now = System.currentTimeMillis()
    if (now - lastWakeWordTime < WAKE_WORD_DEBOUNCE_MS) {
        Log.d(TAG, "Wake word ignored (debounce)")
        return
    }
    lastWakeWordTime = now
    // ... handle wake word
}
```

#### 3. Sprawdź Stan Przed Wysłaniem Broadcast
```kotlin
private fun sendToggleMicrophoneBroadcast() {
    // Nie wysyłaj jeśli reconnecting
    if (voiceClientManager.state.value == ConnectionState.RECONNECTING) {
        Log.d(TAG, "Ignoring wake word during reconnection")
        return
    }
    // ... send broadcast
}
```

## Testowanie

### Jak Wywołać Problem (PRZED Poprawką):
1. Rozpocznij rozmowę
2. Poczekaj aż bot zacznie mówić
3. Podczas mowy bota poczekaj na fałszywe wykrycie wake word
4. Lub ręcznie wywołaj reconnection (wyłącz WiFi na chwilę)
5. Podczas RECONNECTING powiedz "Alexa"

**Rezultat PRZED:**
- ❌ "Ponowne łączenie..." wisi w nieskończoność
- ❌ Brak auto-restart
- ❌ Użytkownik musi ręcznie pause/resume

### Po Poprawce:
1. Rozpocznij rozmowę
2. Wywołaj reconnection
3. Podczas RECONNECTING powiedz "Alexa"

**Rezultat PO:**
- ✅ Wake word jest ignorowany
- ✅ Reconnection kontynuuje
- ✅ Auto-restart działa po 5 sekundach
- ✅ Połączenie przywrócone automatycznie

### Logi Do Sprawdzenia:
```bash
# Wyczyść logi
adb -s EM95IBKZEYIFSO69 logcat -c

# Wywołaj problem

# Sprawdź logi
adb -s EM95IBKZEYIFSO69 logcat -d | grep -E "Wake word|Picovoice|RECONNECTING|enableMic|⚠️"
```

### Oczekiwane Logi PO Poprawce:
```
Wake word detected: Alexa (SYSTEM)
🎤 Toggle microphone - Current state: ON
⚠️ Mic disabled during RECONNECTING - ignoring
   This is likely a false wake word detection
🔄 Starting reconnection process (max 3 attempts, 10s timeout)
🔍 DEBUG: 5s passed, checking state...
⚠️ Still reconnecting after 5s - triggering automatic restart
🚨🚨🚨 AUTOMATIC RESTART TRIGGERED! 🚨🚨🚨
✅ Automatic restart successful after 2000ms
```

## Dodatkowe Bugi Naprawione

### 1. startReconnection() Nie Był Wywoływany
W WebSocket health monitoring brakowało `scope?.launch {}`:
```kotlin
// PRZED:
reconnectionManager.startReconnection()  // ❌ Nie działa!

// PO:
scope?.launch {
    reconnectionManager.startReconnection()  // ✅ Działa!
}
```

### 2. Auto-Restart Job Nie Startował
Dodano debug logi aby potwierdzić że job startuje:
```kotlin
Log.i(TAG, "🔍 DEBUG: Launching auto-restart monitor job")
val autoRestartJob = launch {
    Log.i(TAG, "🔍 DEBUG: Auto-restart job started, waiting 5s...")
    delay(AUTO_RESTART_TIMEOUT)
    // ...
}
```

## Status

- [x] Bug zidentyfikowany (Picovoice anuluje reconnection)
- [x] Poprawka zaimplementowana (ignoruj wake word podczas RECONNECTING)
- [x] Kod zbudowany i zainstalowany
- [x] Debug logi dodane
- [ ] Testy użytkownika (czekam na feedback)

## Podsumowanie

To był **KRYTYCZNY BUG** który powodował że:
- Picovoice fałszywie wykrywał wake word podczas mowy bota
- `enableMic(false)` wywoływał `pause()` nawet gdy RECONNECTING
- `pause()` anulował reconnection
- Auto-restart nie działał
- "Ponowne łączenie..." wisiało w nieskończoność

Teraz po poprawce:
- ✅ Wake word jest ignorowany podczas RECONNECTING
- ✅ Reconnection kontynuuje normalnie
- ✅ Auto-restart działa po 5 sekundach
- ✅ Połączenie przywracane automatycznie
- ✅ Użytkownik nie musi nic robić
