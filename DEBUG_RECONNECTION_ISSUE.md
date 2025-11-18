# Debug: Problem z Auto-Restart

## Problem
Auto-restart po 5 sekundach nie działa - "Ponowne łączenie..." wisi ponad 20 sekund.

## Potrzebne Informacje

### 1. Sprawdź czy kod został zainstalowany
```bash
# Sprawdź datę instalacji APK
adb -s EM95IBKZEYIFSO69 shell dumpsys package ai.pipecat.gemini_multimodal_websocket_demo | grep -E "firstInstallTime|lastUpdateTime"
```

### 2. Wyczyść logi i wywołaj problem
```bash
# Wyczyść logi
adb -s EM95IBKZEYIFSO69 logcat -c

# Teraz wywołaj problem (wyłącz WiFi lub spowoduj utratę połączenia)
# Poczekaj 30 sekund

# Pokaż logi reconnection
adb -s EM95IBKZEYIFSO69 logcat -d | grep -E "VoiceClientManager|Reconnection|RECONNECTING|AUTO|RESTART"
```

### 3. Sprawdź czy metoda doAutomaticRestart() istnieje
```bash
# Sprawdź czy metoda jest w APK
adb -s EM95IBKZEYIFSO69 shell pm dump ai.pipecat.gemini_multimodal_websocket_demo | grep -i "restart"
```

## Możliwe Przyczyny

### 1. Kod Nie Został Zainstalowany
- IDE autoformat mógł zmienić kod
- Trzeba przebudować i zainstalować ponownie

### 2. Job Nie Startuje
- `autoRestartJob` może nie być uruchamiany
- Trzeba dodać więcej logów

### 3. Warunek Nie Jest Spełniony
- `state.value == ConnectionState.RECONNECTING` może być false
- Stan może się zmienić przed 5 sekundami

### 4. Scope Jest Null
- `scope?.launch` może nie działać jeśli scope jest null
- Trzeba sprawdzić czy scope istnieje

## Debugging

### Dodaj Więcej Logów

W `startReconnection()` dodaj:
```kotlin
Log.i(TAG, "🔍 DEBUG: Starting auto-restart monitor")
Log.i(TAG, "   Current state: ${state.value}")
Log.i(TAG, "   Scope: ${if (scope != null) "exists" else "NULL"}")

val autoRestartJob = launch {
    Log.i(TAG, "🔍 DEBUG: Auto-restart job started")
    delay(AUTO_RESTART_TIMEOUT)
    Log.i(TAG, "🔍 DEBUG: 5 seconds passed, checking state...")
    Log.i(TAG, "   Current state: ${state.value}")
    
    if (state.value == ConnectionState.RECONNECTING) {
        Log.w(TAG, "⚠️ Still reconnecting after 5s - triggering automatic restart")
        doAutomaticRestart()
    } else {
        Log.i(TAG, "✅ State changed to ${state.value}, no restart needed")
    }
}
```

### Sprawdź Czy Job Działa

Dodaj na początku `doAutomaticRestart()`:
```kotlin
private suspend fun doAutomaticRestart() {
    Log.e(TAG, "🚨 AUTOMATIC RESTART TRIGGERED!")
    Log.e(TAG, "   This should be visible in logs!")
    // ... reszta kodu
}
```

## Szybki Fix

Jeśli auto-restart nie działa, możemy:

### Opcja 1: Uproszczona Wersja (Bez Parallel Job)
```kotlin
suspend fun startReconnection() {
    val startTime = System.currentTimeMillis()
    
    reconnectJob = scope?.launch {
        while (isActive && attemptCount < maxAttempts) {
            val elapsed = System.currentTimeMillis() - startTime
            
            // Sprawdź co 500ms czy minęło 5 sekund
            if (elapsed > 5000L && state.value == ConnectionState.RECONNECTING) {
                Log.w(TAG, "⚠️ 5 seconds passed, doing automatic restart")
                doAutomaticRestart()
                return@launch
            }
            
            attemptCount++
            // ... reszta kodu
        }
    }
}
```

### Opcja 2: Timeout w attemptReconnect()
```kotlin
private suspend fun attemptReconnect() {
    // ... setup
    
    // Czekaj tylko 2 sekundy zamiast 5
    val maxWait = 2000L
    
    while (waited < maxWait) {
        delay(500)
        waited += 500
        
        if (state.value == ConnectionState.CONNECTED && botReady.value) {
            return
        }
    }
    
    // Po 2 sekundach bez sukcesu → restart
    if (attemptCount >= 2) {
        Log.w(TAG, "⚠️ 2 attempts failed, doing automatic restart")
        doAutomaticRestart()
    }
}
```

## Następne Kroki

1. **Wyczyść logi**: `adb -s EM95IBKZEYIFSO69 logcat -c`
2. **Wywołaj problem**: Wyłącz WiFi podczas rozmowy
3. **Poczekaj 30 sekund**
4. **Pokaż logi**: `adb -s EM95IBKZEYIFSO69 logcat -d | grep VoiceClientManager`
5. **Szukaj**: "AUTO", "RESTART", "🔍", "🚨"

Jeśli nie ma tych logów → kod nie został zainstalowany lub job nie startuje.
