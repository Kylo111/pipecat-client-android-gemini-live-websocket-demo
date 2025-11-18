# Analiza: Dlaczego Pause/Resume Rozwiązuje Problem?

## Co Robi Pause/Resume?

### Pause():
```kotlin
fun pause() {
    // 1. Anuluje reconnection attempts
    reconnectionManager.cancelReconnection()
    
    // 2. Zamyka WebSocket
    webSocket?.close(1000, "Paused by user")
    
    // 3. Czyści zasoby ALE zachowuje session handle
    handleDisconnect(preserveSessionHandle = true)
    
    // 4. Ustawia isPaused = true
    isPaused.value = true
}
```

### Resume():
```kotlin
fun resume() {
    // 1. Czyści flagę paused
    isPaused.value = false
    
    // 2. Wywołuje start() z session resumption
    start(currentThreadSettings)
    // To tworzy NOWY WebSocket od zera!
}
```

## Dlaczego To Działa?

### Problem z Reconnection:
1. **Stary WebSocket jest "zepsuty"** - może być w dziwnym stanie
2. **Reconnection próbuje naprawić stary WebSocket** - czasami to nie działa
3. **Może być race condition** - wiele prób reconnection jednocześnie

### Rozwiązanie Pause/Resume:
1. **Pause zamyka stary WebSocket** - czyste zamknięcie
2. **Resume tworzy NOWY WebSocket** - świeży start
3. **Anuluje wszystkie reconnection attempts** - czyści stan
4. **Zachowuje session handle** - kontynuacja rozmowy

## Kluczowa Różnica

### Reconnection (Nie Działa Zawsze):
```
CONNECTED → problem → RECONNECTING → próba naprawy → czasami utknięcie
```

### Pause/Resume (Działa Zawsze):
```
CONNECTED → problem → PAUSE (zamknij wszystko) → RESUME (nowy WebSocket) → CONNECTED
```

## Rozwiązanie: Auto-Recovery

Zamiast czekać 120 sekund, możemy:

### Opcja 1: Szybki Restart Po Timeout
```kotlin
private suspend fun attemptReconnect() {
    // ... próba reconnection
    
    // Jeśli timeout (10s) i nie udało się:
    if (waited >= maxWait && state.value != ConnectionState.CONNECTED) {
        Log.w(TAG, "⚠️ Reconnection timeout - trying quick restart")
        
        // Zrób to co pause/resume:
        quickRestart()
    }
}

private fun quickRestart() {
    Log.i(TAG, "🔄 Quick restart - closing old WebSocket and creating new one")
    
    // 1. Zamknij stary WebSocket
    webSocket?.close(1000, "Quick restart")
    webSocket = null
    
    // 2. Anuluj wszystkie reconnection attempts
    reconnectionManager.cancelReconnection()
    
    // 3. Krótkie opóźnienie (500ms)
    delay(500)
    
    // 4. Nowy WebSocket
    start(currentThreadSettings)
}
```

### Opcja 2: Zmniejsz Timeout i Dodaj Quick Restart
```kotlin
private inner class ReconnectionManager {
    private val maxAttempts = 3 // Zmniejsz z 5 do 3
    private val baseDelay = 500L // Zmniejsz z 1000ms do 500ms
    private val TOTAL_RECONNECTION_TIMEOUT = 15000L // Zmniejsz z 30s do 15s
    
    suspend fun startReconnection() {
        // ... próby reconnection
        
        // Po 3 próbach (max 15s):
        if (attemptCount >= maxAttempts) {
            Log.w(TAG, "Max attempts reached - trying quick restart")
            quickRestart()
        }
    }
}
```

### Opcja 3: Inteligentny Restart (Najlepsze)
```kotlin
private suspend fun attemptReconnect() {
    try {
        // Clean up old WebSocket COMPLETELY
        webSocket?.close(1000, "Reconnecting")
        webSocket = null
        
        // KRÓTKIE OPÓŹNIENIE - pozwól staremu WebSocket się zamknąć
        delay(500)
        
        // Ensure we're in RECONNECTING state
        if (state.value != ConnectionState.RECONNECTING) {
            state.value = ConnectionState.RECONNECTING
        }
        
        // Call start() to initiate NEW connection
        start(currentThreadSettings)
        
        // Wait for connection (5 seconds is enough for fresh connection)
        var waited = 0L
        val maxWait = 5000L // Zmniejsz z 10s do 5s
        
        while (waited < maxWait) {
            delay(500)
            waited += 500
            
            // Success
            if (state.value == ConnectionState.CONNECTED && botReady.value) {
                Log.i(TAG, "✅ Reconnection successful after ${waited}ms")
                return
            }
            
            // Failure
            if (state.value == ConnectionState.DISCONNECTED) {
                Log.w(TAG, "❌ Reconnection failed - disconnected after ${waited}ms")
                return
            }
        }
        
        Log.w(TAG, "⏱️ Reconnection timeout after ${waited}ms")
        
    } catch (e: Exception) {
        Log.e(TAG, "❌ Reconnection attempt failed: ${e.message}", e)
    }
}
```

## Rekomendacja: Agresywniejszy Restart

### Zmień Parametry:
```kotlin
// WebSocket health monitoring
private val WEBSOCKET_TIMEOUT_MS = 30000L // 30 sekund zamiast 120s

// ReconnectionManager
private val maxAttempts = 3 // 3 próby zamiast 5
private val baseDelay = 500L // 500ms zamiast 1000ms
private val TOTAL_RECONNECTION_TIMEOUT = 10000L // 10 sekund zamiast 30s
```

### Dodaj Delay Przed Nowym WebSocket:
```kotlin
private suspend fun attemptReconnect() {
    // Clean up old WebSocket
    webSocket?.close(1000, "Reconnecting")
    webSocket = null
    
    // KLUCZOWE: Poczekaj 500ms aby stary WebSocket się zamknął
    delay(500)
    
    // Teraz utwórz nowy
    start(currentThreadSettings)
    
    // Czekaj tylko 5 sekund (świeże połączenie jest szybkie)
    var waited = 0L
    val maxWait = 5000L
    // ...
}
```

## Dlaczego To Zadziała?

1. **Szybszy timeout** (30s zamiast 120s) - szybciej wykrywa problem
2. **Mniej prób** (3 zamiast 5) - szybciej przechodzi do restart
3. **Krótsze opóźnienia** (500ms zamiast 1s) - szybsze próby
4. **Globalny timeout 10s** - maksymalnie 10 sekund reconnection
5. **Delay przed nowym WebSocket** - czyste zamknięcie starego
6. **Krótszy timeout dla pojedynczej próby** (5s zamiast 10s) - świeże połączenie jest szybkie

## Implementacja

Zmień te wartości w `VoiceClientManager.kt`:

```kotlin
// Linia ~270
private val WEBSOCKET_TIMEOUT_MS = 30000L // 30 seconds (było 120s)

// Linia ~2330
private inner class ReconnectionManager {
    private val maxAttempts = 3 // było 5
    private val baseDelay = 500L // było 1000L
    private val TOTAL_RECONNECTION_TIMEOUT = 10000L // było 30000L
}

// Linia ~2390
private suspend fun attemptReconnect() {
    // Dodaj delay przed start():
    delay(500) // NOWE!
    
    // Zmień maxWait:
    val maxWait = 5000L // było 10000L
}
```

## Rezultat

- ✅ Wykrywa problem po 30 sekundach (zamiast 120s)
- ✅ Próbuje 3 razy po 500ms = max 10 sekund
- ✅ Czyści stary WebSocket przed nowym
- ✅ Użytkownik czeka max 10 sekund (zamiast 30s)
- ✅ Jeśli nie działa - pokazuje dialog

To jest dokładnie to co robi pause/resume, ale automatycznie!
