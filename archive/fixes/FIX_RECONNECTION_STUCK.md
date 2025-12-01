# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Task completed - historical record
**Current Documentation:** See /docs/implementation/audio-pipeline.md or /docs/implementation/picovoice-integration.md

---

# Fix: Problem z Utknięciem w Stanie "Ponowne łączenie..."

## Problem
Aplikacja czasami pokazuje "Ponowne łączenie..." i nie może się połączyć. Pomaga tylko spauzowanie i wznowienie sesji.

## Przyczyny

### 1. Zbyt Krótki WebSocket Timeout
- Timeout 60 sekund był zbyt krótki
- Podczas normalnej rozmowy mogą być długie przerwy (użytkownik myśli)
- Powodowało to fałszywe alarmy i niepotrzebne reconnection

### 2. Health Monitoring Podczas Reconnection
- WebSocket health monitoring działał również podczas reconnection
- Mógł powodować konflikty z ReconnectionManager

### 3. Zbyt Krótki Timeout dla Pojedynczej Próby
- Czekał tylko 5 sekund na połączenie
- Czasami setupComplete przychodzi później
- Uznawał to za failure i próbował ponownie

### 4. Brak Globalnego Timeoutu
- Reconnection mógł trwać bardzo długo (5 prób × ~10s = ~50s)
- Użytkownik widział "Ponowne łączenie..." przez minutę
- Brak możliwości przerwania

### 5. Słabe Wykrywanie Sukcesu
- Sprawdzał tylko `state.value == CONNECTED`
- Nie sprawdzał czy `botReady == true` (setupComplete otrzymane)
- Mógł uznać połączenie za udane zanim było gotowe

## Rozwiązanie

### Zmiana 1: Zwiększenie WebSocket Timeout
```kotlin
// PRZED:
private val WEBSOCKET_TIMEOUT_MS = 60000L // 60 seconds

// PO:
private val WEBSOCKET_TIMEOUT_MS = 120000L // 2 minutes (increased to reduce false positives)
```

**Efekt:**
- Mniej fałszywych alarmów podczas normalnej rozmowy
- Daje więcej czasu na myślenie/pauzę bez triggera reconnection

### Zmiana 2: Zatrzymanie Health Monitoring Podczas Reconnection
```kotlin
private fun startWebSocketHealthMonitoring() {
    webSocketHealthJob = scope?.launch {
        while (isActive) {
            delay(WEBSOCKET_HEALTH_CHECK_INTERVAL_MS)
            
            // Only check if connected (not during reconnection)
            if (state.value == ConnectionState.CONNECTED) {
                // ... sprawdzanie health
            } else if (state.value == ConnectionState.RECONNECTING) {
                // During reconnection, don't check health - ReconnectionManager handles it
                if (DEBUG_LOGGING) {
                    Log.d(TAG, "⏸️ Skipping health check - reconnection in progress")
                }
            }
        }
    }
}
```

**Efekt:**
- Unika konfliktów między health monitoring a ReconnectionManager
- Tylko ReconnectionManager zarządza reconnection

### Zmiana 3: Globalny Timeout dla Reconnection
```kotlin
private inner class ReconnectionManager {
    private val TOTAL_RECONNECTION_TIMEOUT = 30000L // 30 seconds max
    
    suspend fun startReconnection() {
        val startTime = System.currentTimeMillis()
        
        reconnectJob = scope?.launch {
            while (isActive && attemptCount < maxAttempts) {
                // Check global timeout
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > TOTAL_RECONNECTION_TIMEOUT) {
                    Log.w(TAG, "⏱️ Reconnection timeout after ${elapsed / 1000}s")
                    showMaxAttemptsDialog()
                    return@launch
                }
                
                // ... próba reconnection
            }
        }
    }
}
```

**Efekt:**
- Maksymalnie 30 sekund reconnection (zamiast potencjalnie 50+)
- Po 30 sekundach pokazuje dialog z opcjami
- Użytkownik nie czeka w nieskończoność

### Zmiana 4: Lepsze Wykrywanie Sukcesu
```kotlin
private suspend fun attemptReconnect() {
    // ... setup connection
    
    var waited = 0L
    val maxWait = 10000L // Increased from 5s to 10s
    
    while (waited < maxWait) {
        delay(500)
        waited += 500
        
        // Success: Connected AND received setupComplete
        if (state.value == ConnectionState.CONNECTED && botReady.value) {
            Log.i(TAG, "✅ Reconnection successful after ${waited}ms")
            return
        }
        
        // Failure: Disconnected
        if (state.value == ConnectionState.DISCONNECTED) {
            Log.w(TAG, "❌ Reconnection failed - disconnected after ${waited}ms")
            return
        }
    }
    
    Log.w(TAG, "⏱️ Reconnection timeout after ${waited}ms")
}
```

**Efekt:**
- Czeka na pełne połączenie (setupComplete)
- Dłuższy timeout (10s zamiast 5s) daje więcej czasu
- Lepsze logowanie dla debugowania

### Zmiana 5: Ulepszone Logowanie
```kotlin
Log.i(TAG, "🔄 Attempting reconnection (attempt $attemptCount of $maxAttempts)...")
Log.i(TAG, "   Thread settings: ${currentThreadSettings?.conversationId ?: "none"}")
Log.i(TAG, "   Current state: ${state.value}")
Log.i(TAG, "⏳ Waiting for connection (max ${maxWait / 1000}s)...")

// Co 2 sekundy:
Log.d(TAG, "   ${waited / 1000}s: state=${state.value}, botReady=${botReady.value}, webSocket=${if (webSocket != null) "exists" else "null"}")

// Sukces:
Log.i(TAG, "✅ Reconnection successful after ${waited}ms")
Log.i(TAG, "   State: CONNECTED, botReady: true")

// Timeout:
Log.w(TAG, "⏱️ Reconnection timeout after ${waited}ms")
Log.w(TAG, "   Final state: ${state.value}, botReady: ${botReady.value}")
```

**Efekt:**
- Łatwiejsze debugowanie problemów
- Widać dokładnie co się dzieje podczas reconnection
- Emoji pomagają szybko znaleźć ważne logi

## Testowanie

### Scenariusz 1: Normalna Rozmowa z Przerwami
1. Rozpocznij rozmowę
2. Mów przez 30 sekund
3. Zamilknij na 90 sekund (myślenie)
4. Mów ponownie

**Oczekiwany rezultat:**
- ✅ Nie powinno być reconnection (timeout 2 minuty)
- ✅ Rozmowa kontynuowana normalnie

### Scenariusz 2: Rzeczywista Utrata Połączenia
1. Rozpocznij rozmowę
2. Wyłącz WiFi/dane mobilne
3. Poczekaj 5 sekund
4. Włącz WiFi/dane mobilne

**Oczekiwany rezultat:**
- ✅ Przechodzi w stan RECONNECTING
- ✅ Próbuje połączyć się ponownie
- ✅ Po max 30 sekundach pokazuje dialog jeśli nie udało się
- ✅ Jeśli się uda - wraca do CONNECTED

### Scenariusz 3: Długie Reconnection
1. Rozpocznij rozmowę
2. Wyłącz WiFi/dane mobilne
3. NIE włączaj z powrotem
4. Obserwuj logi

**Oczekiwany rezultat:**
- ✅ Próbuje reconnection przez max 30 sekund
- ✅ Po 30 sekundach pokazuje dialog
- ✅ Użytkownik może wybrać: kontynuuj próby lub zakończ

### Scenariusz 4: Pause/Resume Podczas Reconnection
1. Rozpocznij rozmowę
2. Wyłącz WiFi (zaczyna reconnection)
3. Natychmiast kliknij pause
4. Włącz WiFi
5. Kliknij resume

**Oczekiwany rezultat:**
- ✅ Pause anuluje reconnection
- ✅ Resume rozpoczyna nowe połączenie
- ✅ Połączenie się udaje

## Komendy Testowe

### Build i Install
```bash
./gradlew clean build
./gradlew installDebug
```

### Monitorowanie Logów
```bash
# Wszystkie logi reconnection
adb -s EM95IBKZEYIFSO69 logcat | grep -E "Reconnection|RECONNECTING|WebSocket"

# Tylko ważne logi (emoji)
adb -s EM95IBKZEYIFSO69 logcat | grep -E "🔄|✅|❌|⏱️|⏸️"

# Health monitoring
adb -s EM95IBKZEYIFSO69 logcat | grep "WebSocket health"
```

## Metryki Sukcesu

Po wdrożeniu zmian:
- ✅ Mniej fałszywych reconnection podczas normalnej rozmowy
- ✅ Szybsze wykrywanie rzeczywistych problemów z połączeniem
- ✅ Maksymalnie 30 sekund reconnection (zamiast 50+)
- ✅ Lepsze logowanie dla debugowania
- ✅ Użytkownik ma kontrolę (dialog po timeout)

## Potencjalne Dalsze Usprawnienia

### 1. Przycisk "Anuluj Reconnection" w UI
```kotlin
if (connectionState == ConnectionState.RECONNECTING) {
    Button(onClick = {
        voiceClientManager.stopReconnection()
        voiceClientManager.pause()
    }) {
        Text("Anuluj i spróbuj ponownie")
    }
}
```

### 2. Metryki Reconnection
Zbieraj statystyki:
- Ile razy reconnection się udaje
- Ile razy failuje
- Średni czas reconnection
- Przyczyny reconnection

### 3. Adaptacyjny Timeout
Dostosuj timeout na podstawie historii:
- Jeśli użytkownik często robi długie przerwy → zwiększ timeout
- Jeśli sieć jest niestabilna → zmniejsz timeout dla szybszego wykrywania

### 4. Ping/Pong dla WebSocket
Dodaj aktywne ping/pong zamiast pasywnego monitoringu:
```kotlin
// Co 30 sekund wysyłaj ping
scope?.launch {
    while (isActive) {
        delay(30000)
        webSocket?.send("{\"ping\": true}")
    }
}
```
