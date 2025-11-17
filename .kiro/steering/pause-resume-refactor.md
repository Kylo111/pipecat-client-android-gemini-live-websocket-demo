---
inclusion: manual
---

# Refaktor Pause/Resume - Plan na przyszłość

## Problem

Obecna implementacja pause/resume w `VoiceClientManager` ma problemy architektoniczne, które mogą prowadzić do race conditions i niespójności stanu.

## Zidentyfikowane problemy

### 1. Podwójne wywołanie cleanup (NAPRAWIONE)

**Problem**: Podczas pauzowania `handleDisconnect()` było wywoływane dwa razy:
- Raz przez `pause()` z `preserveSessionHandle=true` ✅
- Drugi raz przez WebSocket callback `onClosed()` bez parametru (domyślnie `false`) ❌

**Skutek**: Głośnik był wyłączany podczas pauzy, mimo że powinien być zachowany.

**Rozwiązanie**: W `onClosed()` sprawdzamy `isPaused.value` i nie wywołujemy `handleDisconnect()` jeśli to pause.

```kotlin
override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
    if (state.value == ConnectionState.DISCONNECTING) {
        val isUserPause = isPaused.value
        if (isUserPause) {
            // Nie wywołuj handleDisconnect() - już było wywołane przez pause()
        } else {
            handleDisconnect(preserveSessionHandle = false)
        }
        return
    }
}
```

### 2. Race conditions w zarządzaniu stanem

**Problem**: Stan może być niespójny między:
- `state: ConnectionState` (DISCONNECTED, CONNECTING, CONNECTED, etc.)
- `isPaused: Boolean`
- `mic.value: Boolean`
- `isSpeakerphoneOn.value: Boolean`

**Przykład**: Użytkownik klika pause, a UI pokazuje "Łączę" zamiast "Wstrzymano".

**Przyczyna**: 
1. `pause()` ustawia `state = DISCONNECTING`
2. `handleDisconnect()` ustawia `state = DISCONNECTED`
3. `resume()` jest wywoływane przed zakończeniem `onClosed()`
4. `resume()` ustawia `state = CONNECTING`
5. UI pokazuje nieprawidłowy stan

### 3. Brak synchronizacji między wątkami

**Problem**: WebSocket callbacki (`onClosed`, `onFailure`) są wywoływane w background thread, podczas gdy UI operacje są w main thread.

**Skutek**: Możliwe race conditions przy dostępie do współdzielonych zmiennych.

### 4. Brak walidacji przejść między stanami

**Problem**: Kod pozwala na dowolne przejścia między stanami bez walidacji.

**Przykład**: Można wywołać `resume()` gdy `state = CONNECTED`, co prowadzi do niespójności.

## Proponowany refaktor

### Faza 1: Sealed Class dla stanu sesji

```kotlin
sealed class SessionState {
    object Idle : SessionState()
    object Connecting : SessionState()
    data class Connected(
        val sessionHandle: String?,
        val audioSettings: AudioSettings
    ) : SessionState()
    data class Paused(
        val sessionHandle: String,
        val audioSettings: AudioSettings,
        val pausedAt: Long
    ) : SessionState()
    object Disconnecting : SessionState()
    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int
    ) : SessionState()
    data class Error(val message: String) : SessionState()
}

data class AudioSettings(
    val isSpeakerphoneOn: Boolean,
    val isMicEnabled: Boolean,
    val audioMode: Int
)
```

### Faza 2: State Machine z walidacją

```kotlin
private val stateMutex = Mutex()
private var _sessionState: SessionState = SessionState.Idle
val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

private suspend fun transitionTo(newState: SessionState) = stateMutex.withLock {
    val oldState = _sessionState
    
    // Walidacja przejścia
    if (!isValidTransition(oldState, newState)) {
        Log.w(TAG, "Invalid state transition: $oldState -> $newState")
        return@withLock
    }
    
    Log.i(TAG, "State transition: $oldState -> $newState")
    _sessionState = newState
    
    // Callback dla UI
    onStateChanged(oldState, newState)
}

private fun isValidTransition(from: SessionState, to: SessionState): Boolean {
    return when (from) {
        is SessionState.Idle -> to is SessionState.Connecting
        is SessionState.Connecting -> to is SessionState.Connected || to is SessionState.Error
        is SessionState.Connected -> to is SessionState.Paused || to is SessionState.Disconnecting
        is SessionState.Paused -> to is SessionState.Connecting || to is SessionState.Disconnecting
        is SessionState.Disconnecting -> to is SessionState.Idle
        is SessionState.Reconnecting -> to is SessionState.Connected || to is SessionState.Error
        is SessionState.Error -> to is SessionState.Idle || to is SessionState.Connecting
    }
}
```

### Faza 3: Uproszczone metody pause/resume

```kotlin
suspend fun pause() {
    val currentState = _sessionState
    if (currentState !is SessionState.Connected) {
        Log.w(TAG, "Cannot pause - not connected (state: $currentState)")
        return
    }
    
    // Zapisz ustawienia audio
    val audioSettings = AudioSettings(
        isSpeakerphoneOn = isSpeakerphoneOn.value,
        isMicEnabled = mic.value,
        audioMode = audioManager?.mode ?: AudioManager.MODE_NORMAL
    )
    
    // Przejdź do stanu Paused
    transitionTo(SessionState.Paused(
        sessionHandle = currentState.sessionHandle ?: "",
        audioSettings = audioSettings,
        pausedAt = System.currentTimeMillis()
    ))
    
    // Zamknij WebSocket
    webSocket?.close(1000, "Paused by user")
    
    // Cleanup (ale zachowaj AudioManager)
    cleanupResources(preserveAudio = true)
}

suspend fun resume() {
    val currentState = _sessionState
    if (currentState !is SessionState.Paused) {
        Log.w(TAG, "Cannot resume - not paused (state: $currentState)")
        return
    }
    
    // Przejdź do stanu Connecting
    transitionTo(SessionState.Connecting)
    
    // Przywróć ustawienia audio
    restoreAudioSettings(currentState.audioSettings)
    
    // Połącz ponownie
    start(currentThreadSettings, sessionHandle = currentState.sessionHandle)
}
```

### Faza 4: Obsługa WebSocket callbacków

```kotlin
override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
    scope?.launch {
        val currentState = _sessionState
        
        when (currentState) {
            is SessionState.Paused -> {
                // Oczekiwane zamknięcie podczas pauzy - nic nie rób
                Log.i(TAG, "WebSocket closed during pause - expected")
            }
            is SessionState.Disconnecting -> {
                // Oczekiwane zamknięcie podczas stop - przejdź do Idle
                transitionTo(SessionState.Idle)
                cleanupResources(preserveAudio = false)
            }
            is SessionState.Connected -> {
                // Nieoczekiwane zamknięcie - spróbuj reconnect
                Log.w(TAG, "Unexpected WebSocket closure")
                transitionTo(SessionState.Reconnecting(attempt = 1, maxAttempts = 5))
                reconnectionManager.startReconnection()
            }
            else -> {
                Log.w(TAG, "WebSocket closed in unexpected state: $currentState")
            }
        }
    }
}
```

## Korzyści z refaktoru

1. **Jednoznaczny stan** - jeden sealed class zamiast wielu boolean flag
2. **Walidacja przejść** - niemożliwe nieprawidłowe przejścia
3. **Thread-safe** - Mutex chroni przed race conditions
4. **Łatwiejsze debugowanie** - jasne logi przejść między stanami
5. **Zachowanie kontekstu** - AudioSettings są częścią stanu Paused
6. **Testowalne** - łatwo przetestować wszystkie przejścia

## Plan implementacji

### Priorytet: ŚREDNI
Obecne rozwiązanie działa, ale nie jest idealne.

### Etapy:

1. **Przygotowanie** (1-2h)
   - Stwórz sealed class `SessionState`
   - Dodaj `AudioSettings` data class
   - Napisz testy jednostkowe dla `isValidTransition()`

2. **Migracja stopniowa** (3-4h)
   - Dodaj nowy `_sessionState` obok starego `state`
   - Synchronizuj oba stany podczas przejściowego okresu
   - Stopniowo przenoś logikę do nowego systemu

3. **Refaktor callbacków** (2-3h)
   - Przepisz `onClosed()`, `onFailure()`, `onOpen()`
   - Dodaj Mutex dla thread-safety
   - Usuń stary kod

4. **Testy** (2-3h)
   - Przetestuj wszystkie scenariusze pause/resume
   - Przetestuj reconnection
   - Przetestuj zachowanie AudioSettings

5. **Cleanup** (1h)
   - Usuń stary `state: ConnectionState`
   - Usuń niepotrzebne boolean flagi
   - Aktualizuj dokumentację

**Całkowity czas**: ~10-15h

## Uwagi

- **NIE RUSZAJ** tego kodu bez pełnego zrozumienia obecnej implementacji
- **PRZETESTUJ** dokładnie wszystkie scenariusze przed wdrożeniem
- **ZACHOWAJ** backward compatibility z UI (StateFlow)
- **DOKUMENTUJ** każdą zmianę w logach

## Status

- [x] Problem zidentyfikowany (2024-11-17)
- [x] Tymczasowe rozwiązanie wdrożone (fix w `onClosed()`)
- [ ] Refaktor zaplanowany
- [ ] Testy napisane
- [ ] Implementacja
- [ ] Code review
- [ ] Wdrożenie

---

**Ostatnia aktualizacja**: 2024-11-17
**Autor**: Kiro AI
**Priorytet**: ŚREDNI (działa, ale można lepiej)
