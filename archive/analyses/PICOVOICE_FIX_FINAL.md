# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Task completed - historical record
**Current Documentation:** See /docs/ for current documentation

---

# Naprawa problemu Picovoice przy wyłączonym ekranie - FINALNE ROZWIĄZANIE

## Problem
Przy włączonym Picovoice i wyłączonym ekranie, sesja była **całkowicie kończona** (nie pauzowana), co powodowało:
- Powrót do ekranu listy konwersacji
- Utratę całego kontekstu rozmowy
- Brak możliwości resume

**Bez Picovoice problem nie występował.**

## Błędne podejście (odrzucone)
❌ Zatrzymywanie PorcupineService podczas sesji - to było błędne, bo Picovoice **MUSI** działać podczas sesji do zatrzymywania modelu Gemini wake wordem (np. "Alexa" = pause/resume).

## Prawdziwa przyczyna

### Konflikt AudioRecord
Dwa serwisy używały **tego samego AudioSource**:

#### PorcupineService (Picovoice)
```kotlin
// Picovoice wewnętrznie używa:
AudioSource: MediaRecorder.AudioSource.MIC (1)
```

#### VoiceClientManager (PRZED NAPRAWĄ)
```kotlin
audioRecord = AudioRecord(
    MediaRecorder.AudioSource.VOICE_COMMUNICATION,  // = 7
    // ...
)
```

**Problem**: Przy wyłączonym ekranie system wchodzi w Doze Mode i agresywniej zarządza zasobami. Dwa AudioRecord konkurujące o mikrofon mogą powodować:
- Jeden z AudioRecord jest zabijany przez system
- To rzuca wyjątek w WebSocket
- Błąd jest klasyfikowany jako FATAL
- Sesja jest kończona zamiast próbować reconnect

## Zaimplementowane rozwiązanie

### 1. Zmiana AudioSource na VOICE_RECOGNITION

```kotlin
// PRZED:
audioRecord = AudioRecord(
    MediaRecorder.AudioSource.VOICE_COMMUNICATION,  // Konflikt z Picovoice
    // ...
)

// PO:
audioRecord = AudioRecord(
    MediaRecorder.AudioSource.VOICE_RECOGNITION,  // Współistnieje z Picovoice
    // ...
)
```

**Dlaczego to działa**:
- `VOICE_RECOGNITION` (6) jest optymalizowany dla rozpoznawania mowy
- Różne AudioSource mogą współistnieć na większości urządzeń
- System może routować audio z różnych źródeł jednocześnie
- Mniejsze ryzyko konfliktu z PorcupineService który używa `MIC` (1)

### 2. Retry logic dla AudioRecord

```kotlin
var retryCount = 0
val maxRetries = 3

while (retryCount < maxRetries) {
    try {
        audioRecord = AudioRecord(...)
        audioRecord?.startRecording()
        
        // Verify state
        if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            // Success!
            return
        } else {
            // Retry
            audioRecord?.release()
            Thread.sleep(500)
            retryCount++
        }
    } catch (e: Exception) {
        // Retry on error
        retryCount++
        Thread.sleep(500)
    }
}

// After max retries: show error but DON'T end session
errors.add(Error("Nie można uruchomić mikrofonu..."))
```

**Dlaczego to pomaga**:
- Czasowy konflikt może się rozwiązać sam
- Daje systemowi czas na zwolnienie zasobów
- Nie kończy sesji przy przejściowych problemach
- Użytkownik może naprawić problem (zamknąć inne aplikacje) i spróbować ponownie

### 3. Weryfikacja stanu AudioRecord

```kotlin
val recordingState = audioRecord?.recordingState
if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
    Log.i(TAG, "✅ AudioRecord started successfully")
    // Continue
} else {
    Log.w(TAG, "⚠️ AudioRecord not in RECORDING state: $recordingState")
    // Retry
}
```

**Dlaczego to ważne**:
- Wykrywa konflikty natychmiast
- Pozwala na retry zamiast crashu
- Szczegółowe logowanie pomaga w diagnostyce

## Dlaczego to rozwiązuje problem

### Przed naprawą:
1. VoiceClientManager używa VOICE_COMMUNICATION
2. PorcupineService używa MIC
3. Przy wyłączonym ekranie system zabija jeden z AudioRecord
4. WebSocket rzuca wyjątek
5. Błąd klasyfikowany jako FATAL
6. **Sesja kończy się** → powrót do listy konwersacji

### Po naprawie:
1. VoiceClientManager używa VOICE_RECOGNITION ✅
2. PorcupineService używa MIC ✅
3. Różne AudioSource współistnieją ✅
4. Jeśli wystąpi konflikt, retry logic próbuje ponownie ✅
5. Sesja **NIE kończy się** przy przejściowych problemach ✅
6. Użytkownik może kontynuować rozmowę ✅

## Testy do wykonania

### Test 1: Podstawowa sesja z wyłączonym ekranem (KRYTYCZNY)
```
1. Włącz Picovoice
2. Rozpocznij sesję
3. Wyłącz ekran
4. Rozmawiaj 5 minut
5. Włącz ekran

✅ Oczekiwany rezultat: Sesja aktywna, kontekst zachowany
```

### Test 2: Wake word podczas sesji
```
1. Włącz Picovoice
2. Rozpocznij sesję
3. Wyłącz ekran
4. Powiedz "Alexa" (pause)
5. Powiedz "Alexa" (resume)

✅ Oczekiwany rezultat: Pause/resume działa, sesja aktywna
```

### Test 3: Długa sesja
```
1. Włącz Picovoice
2. Rozpocznij sesję
3. Wyłącz ekran
4. Rozmawiaj 30 minut

✅ Oczekiwany rezultat: Stabilna sesja, brak crashy
```

### Test 4: Memory pressure
```
1. Włącz Picovoice
2. Rozpocznij sesję
3. Wyłącz ekran
4. Otwórz wiele innych aplikacji
5. Wróć do aplikacji

✅ Oczekiwany rezultat: Sesja przetrwa
```

### Test 5: Bez Picovoice (regresja)
```
1. Wyłącz Picovoice
2. Rozpocznij sesję
3. Wyłącz ekran
4. Rozmawiaj 5 minut

✅ Oczekiwany rezultat: Działa jak wcześniej
```

## Kluczowe logi do sprawdzenia

### Przy starcie AudioRecord:
```
VoiceClientManager: Starting audio recording (attempt 1/3)
VoiceClientManager:   AudioSource: VOICE_RECOGNITION (optimized for speech recognition)
VoiceClientManager: ✅ AudioRecord started successfully - state: 3
```

### Jeśli wystąpi konflikt (powinien się rozwiązać przez retry):
```
VoiceClientManager: ⚠️ AudioRecord not in RECORDING state: 1 (expected: 3)
VoiceClientManager: Waiting 500ms before retry...
VoiceClientManager: Starting audio recording (attempt 2/3)
VoiceClientManager: ✅ AudioRecord started successfully - state: 3
```

### Jeśli retry nie pomoże (rzadkie):
```
VoiceClientManager: ❌ Failed to start AudioRecord after 3 attempts
VoiceClientManager: Last error: ...
// Użytkownik widzi błąd, ale sesja NIE kończy się
```

## Komendy do monitorowania

```bash
# Podstawowe logi
adb -s EM95IBKZEYIFSO69 logcat -c && adb -s EM95IBKZEYIFSO69 logcat | grep -E "VoiceClientManager|PorcupineService|AudioRecord"

# Logi AudioRecord
adb -s EM95IBKZEYIFSO69 logcat | grep -E "AudioRecord|VOICE_RECOGNITION|recording"

# Logi błędów
adb -s EM95IBKZEYIFSO69 logcat | grep -E "ERROR|FATAL|failed to start"
```

## Metryki sukcesu

Po testach:
- ✅ 0 przypadków zakończenia sesji przy wyłączonym ekranie
- ✅ 0 konfliktów AudioRecord (lub rozwiązane przez retry)
- ✅ 100% sesji zachowuje kontekst rozmowy
- ✅ Picovoice działa podczas sesji (pause/resume)
- ✅ Wake words działają poprawnie

## Dodatkowe korzyści

1. **Lepsza jakość audio**: VOICE_RECOGNITION jest optymalizowany dla rozpoznawania mowy
2. **Większa stabilność**: Retry logic chroni przed przejściowymi problemami
3. **Lepsze logowanie**: Łatwiejsza diagnostyka problemów
4. **Nie kończy sesji**: Użytkownik może naprawić problem bez utraty kontekstu

## Co dalej?

Jeśli problem nadal występuje:
1. Sprawdź logi - czy AudioRecord się uruchamia?
2. Sprawdź czy inne aplikacje używają mikrofonu
3. Sprawdź ustawienia uprawnień aplikacji
4. Sprawdź czy Doze Mode nie jest zbyt agresywny (ustawienia baterii)

## Rollback

Jeśli fix powoduje problemy, przywróć poprzednią wersję:
```bash
git checkout HEAD~1 gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/VoiceClientManager.kt
```
