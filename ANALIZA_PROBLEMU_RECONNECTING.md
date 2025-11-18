# Analiza Problemu z "Ponowne łączenie..."

## Problem
Czasami aplikacja pokazuje "Ponowne łączenie..." i pomaga tylko spauzowanie i wznowienie sesji.

## Zidentyfikowane Przyczyny

### 1. **WebSocket Health Monitoring - Zbyt Agresywny Timeout**

```kotlin
private val WEBSOCKET_HEALTH_CHECK_INTERVAL_MS = 5000L // Check every 5 seconds
private val WEBSOCKET_TIMEOUT_MS = 60000L // 60 seconds without any message = connection issue
```

**Problem:**
- Sprawdza co 5 sekund czy otrzymano wiadomość
- Jeśli przez 60 sekund brak wiadomości → przechodzi w RECONNECTING
- Ale podczas normalnej rozmowy mogą być długie przerwy (użytkownik myśli, bot czeka)
- To może powodować fałszywe alarmy

**Logi pokazują:**
```kotlin
if (timeSinceLastMessage > WEBSOCKET_TIMEOUT_MS) {
    Log.e(TAG, "⚠️ WebSocket connection appears stalled!")
    state.value = ConnectionState.RECONNECTING
    reconnectionManager.startReconnection()
}
```

### 2. **Reconnection Loop - Może Utknąć w RECONNECTING**

W `attemptReconnect()`:
```kotlin
// Wait longer for connection to establish (up to 5 seconds)
var waited = 0L
val maxWait = 5000L
while (waited < maxWait && state.value == ConnectionState.RECONNECTING) {
    delay(500)
    waited += 500
}

if (state.value == ConnectionState.CONNECTED) {
    Log.i(TAG, "Reconnection successful!")
} else {
    Log.w(TAG, "Reconnection attempt did not result in CONNECTED state after ${waited}ms (current: ${state.value})")
}
```

**Problem:**
- Czeka max 5 sekund na połączenie
- Jeśli WebSocket się łączy ale `setupComplete` nie przychodzi szybko → uznaje za failure
- Próbuje ponownie z exponential backoff (1s, 2s, 4s, 8s, 16s)
- Może utknąć w pętli reconnection

### 3. **Duplikacja Reconnection Attempts**

Wiele miejsc wywołuje `reconnectionManager.startReconnection()`:
- `onClosed()` - unexpected closure
- `onFailure()` - recoverable errors
- `onFailure()` - unknown errors  
- `WebSocket health monitoring` - timeout

**Problem:**
- Może być wiele równoczesnych prób reconnection
- Choć jest check `if (state.value == ConnectionState.RECONNECTING)` to może być race condition

### 4. **WebSocket Health Monitoring Nie Jest Zatrzymywany Podczas Reconnection**

```kotlin
// Only check if connected
if (state.value == ConnectionState.CONNECTED) {
    val timeSinceLastMessage = System.currentTimeMillis() - lastWebSocketMessageTime
    // ...
}
```

**Problem:**
- Monitoring działa tylko gdy CONNECTED
- Ale podczas RECONNECTING też powinien być zatrzymany
- Może powodować konflikty

### 5. **Brak Timeout dla Całego Reconnection Process**

`ReconnectionManager` ma:
- Max 5 prób
- Exponential backoff między próbami
- Ale brak globalnego timeoutu

**Problem:**
- Jeśli każda próba trwa 5 sekund + backoff
- Całość może trwać: 5s + 1s + 5s + 2s + 5s + 4s + 5s + 8s + 5s + 16s = ~56 sekund
- Użytkownik widzi "Ponowne łączenie..." przez minutę
- To frustrujące

## Rozwiązania

### Rozwiązanie 1: Zwiększ WebSocket Timeout (Najprostsze)
```kotlin
private val WEBSOCKET_TIMEOUT_MS = 120000L // 2 minuty zamiast 1 minuty
```

**Zalety:**
- Prosty fix
- Zmniejsza fałszywe alarmy
- Daje więcej czasu na normalną rozmowę

**Wady:**
- Nie rozwiązuje problemu utknięcia w RECONNECTING

### Rozwiązanie 2: Zatrzymaj Health Monitoring Podczas Reconnection
```kotlin
private fun startWebSocketHealthMonitoring() {
    webSocketHealthJob = scope?.launch {
        while (isActive) {
            delay(WEBSOCKET_HEALTH_CHECK_INTERVAL_MS)
            
            // ZMIANA: Sprawdzaj tylko gdy CONNECTED
            if (state.value == ConnectionState.CONNECTED) {
                val timeSinceLastMessage = System.currentTimeMillis() - lastWebSocketMessageTime
                
                if (timeSinceLastMessage > WEBSOCKET_TIMEOUT_MS) {
                    Log.e(TAG, "⚠️ WebSocket connection appears stalled!")
                    state.value = ConnectionState.RECONNECTING
                    updateServiceNotification()
                    reconnectionManager.startReconnection()
                }
            } else if (state.value == ConnectionState.RECONNECTING) {
                // NOWE: Podczas reconnection nie sprawdzaj - ReconnectionManager się tym zajmuje
                Log.d(TAG, "Skipping health check - reconnection in progress")
            }
        }
    }
}
```

### Rozwiązanie 3: Dodaj Timeout dla Reconnection Process
```kotlin
private inner class ReconnectionManager {
    private val maxAttempts = 5
    private val baseDelay = 1000L
    private val TOTAL_RECONNECTION_TIMEOUT = 30000L // 30 sekund max
    
    suspend fun startReconnection() {
        reconnectJob?.cancel()
        
        val startTime = System.currentTimeMillis()
        
        reconnectJob = scope?.launch {
            while (isActive && attemptCount < maxAttempts) {
                // NOWE: Sprawdź czy nie przekroczono globalnego timeoutu
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > TOTAL_RECONNECTION_TIMEOUT) {
                    Log.w(TAG, "Reconnection timeout after ${elapsed}ms")
                    showMaxAttemptsDialog()
                    return@launch
                }
                
                attemptCount++
                // ... reszta kodu
            }
        }
    }
}
```

### Rozwiązanie 4: Lepsze Wykrywanie Sukcesu Reconnection
```kotlin
private suspend fun attemptReconnect() {
    try {
        // Clean up old WebSocket
        webSocket?.close(1000, "Reconnecting")
        webSocket = null
        
        // Start new connection
        start(currentThreadSettings)
        
        // ZMIANA: Czekaj na setupComplete zamiast tylko na zmianę stanu
        var waited = 0L
        val maxWait = 10000L // Zwiększ do 10 sekund
        
        while (waited < maxWait) {
            delay(500)
            waited += 500
            
            // Sukces jeśli otrzymaliśmy setupComplete (botReady = true)
            if (state.value == ConnectionState.CONNECTED && botReady.value) {
                Log.i(TAG, "✅ Reconnection successful - setupComplete received")
                return
            }
            
            // Failure jeśli wróciliśmy do DISCONNECTED
            if (state.value == ConnectionState.DISCONNECTED) {
                Log.w(TAG, "❌ Reconnection failed - disconnected")
                return
            }
        }
        
        Log.w(TAG, "⚠️ Reconnection timeout after ${waited}ms (state: ${state.value}, botReady: ${botReady.value})")
        
    } catch (e: Exception) {
        Log.e(TAG, "Reconnection attempt failed: ${e.message}", e)
    }
}
```

### Rozwiązanie 5: Dodaj Manual Recovery Button
W UI podczas RECONNECTING:
```kotlin
if (connectionState == ConnectionState.RECONNECTING) {
    Button(onClick = {
        // Force stop reconnection and allow manual retry
        voiceClientManager.stopReconnection()
        voiceClientManager.pause()
    }) {
        Text("Anuluj i spróbuj ponownie")
    }
}
```

## Rekomendowane Rozwiązanie

**Kombinacja rozwiązań 1, 2, 3 i 4:**

1. ✅ Zwiększ WebSocket timeout do 2 minut (mniej fałszywych alarmów)
2. ✅ Zatrzymaj health monitoring podczas reconnection (unikaj konfliktów)
3. ✅ Dodaj globalny timeout 30s dla reconnection (nie utknij na minutę)
4. ✅ Lepsze wykrywanie sukcesu - czekaj na botReady (pewność że działa)

To powinno rozwiązać problem "utknięcia" w stanie RECONNECTING.

## Dodatkowe Usprawnienia

### Debug Logging
Dodaj więcej logów aby zrozumieć co się dzieje:
```kotlin
Log.i(TAG, "🔍 Reconnection state:")
Log.i(TAG, "  - Attempt: $attemptCount/$maxAttempts")
Log.i(TAG, "  - State: ${state.value}")
Log.i(TAG, "  - BotReady: ${botReady.value}")
Log.i(TAG, "  - WebSocket: ${if (webSocket != null) "exists" else "null"}")
Log.i(TAG, "  - Elapsed: ${System.currentTimeMillis() - startTime}ms")
```

### Metrics
Zbieraj metryki:
- Ile razy reconnection się udaje
- Ile razy failuje
- Średni czas reconnection
- Przyczyny reconnection (timeout, error, closure)
