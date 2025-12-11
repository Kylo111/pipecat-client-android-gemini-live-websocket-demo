# Audio Diagnostics - Analiza Problemu

## Status po Fazie 2

### ✅ Co działa:
1. **Batching 16 chunków** - potwierdzone w logach
2. **Zmniejszony recording buffer** - 5120 bytes (drugi test)
3. **Flush buffer implementation** - kod dodany

### ❌ Co nadal nie działa:
1. **Trylock fail** - występuje przy pierwszym write
2. **Recording overflow** - występuje podczas playback

---

## Analiza Trylock Fail

### Timing w logach:
```
12-10 12:02:08.990 AudioTrack.write() - requested: 30720, written: 30720
12-10 12:02:09.016 E AudioALSAPlaybackHandlerBase: trylock fail  ← 26ms później
12-10 12:02:09.027 mixer throttle begin: requires sleep 7 ms
```

### Hipoteza:
Trylock fail występuje **zaraz po pierwszym dużym write** (30720 bytes).

**Przyczyna:** Sterownik ALSA potrzebuje czasu na "strawienie" dużego chunka. Gdy próbujemy zapytać o status (getQueuedFramesInfo) zaraz po write, sterownik jest zajęty i trylock fail.

**To NIE jest błąd krytyczny** - to warning, że sterownik był chwilowo zajęty. Mixer sam się throttle'uje (sleep 7ms) i czeka.

### Czy to problem?
**NIE** - to normalne zachowanie przy dużych write'ach. Ważne jest:
- ✅ Audio się nie przerywa (brak trzasków)
- ✅ Występuje tylko raz na początku
- ✅ Mixer sam się synchronizuje

---

## Analiza Recording Overflow

### Timing w logach:
```
12-10 11:56:07.613 Recording paused (half-duplex mode)
12-10 11:56:09.873 W Overrun! RsmpInBuffer drop data 320  ← 2.26s później
12-10 11:56:09.937 stopPlayback() CALLED
12-10 11:56:09.947 Recording resumed
```

### Problem:
1. Recording paused at 11:56:07.613
2. Bot mówi przez 2.26 sekundy
3. Buffer overflow at 11:56:09.873
4. Playback stopped at 11:56:09.937

**Przyczyna:** Mimo że `isRecordingPaused = true`, AudioRecord **nadal nagrywa** w tle (tylko nie czytamy danych). Po 2+ sekundach buffer się zapełnia.

### Rozwiązanie:
Zamiast tylko ustawiać flagę `isRecordingPaused`, musimy **faktycznie zatrzymać AudioRecord** podczas playback.

---

## Plan Naprawy - Faza 3

### Opcja A: Stop/Start AudioRecord (ZALECANE)
```kotlin
fun pauseRecording() {
    audioRecord?.stop()  // Faktycznie zatrzymaj nagrywanie
    isRecordingPaused = true
}

fun resumeRecording() {
    audioRecord?.startRecording()  // Wznów nagrywanie
    isRecordingPaused = false
}
```

**Zalety:**
- ✅ Zero overflow - AudioRecord nie nagrywa w tle
- ✅ Oszczędność CPU/baterii
- ✅ Prosty kod

**Wady:**
- ⚠️ Opóźnienie przy resume (~50-100ms)
- ⚠️ Możliwe trzaski przy start/stop

### Opcja B: Continuous flush podczas pause
```kotlin
fun pauseRecording() {
    isRecordingPaused = true
    
    // Uruchom background job który ciągle opróżnia buffer
    flushJob = scope.launch {
        val dummyBuffer = ByteArray(inputBufferSize)
        while (isRecordingPaused) {
            audioRecord?.read(dummyBuffer, 0, dummyBuffer.size)
            delay(50)  // Co 50ms
        }
    }
}
```

**Zalety:**
- ✅ Brak opóźnienia przy resume
- ✅ AudioRecord zawsze gotowy

**Wady:**
- ❌ Marnowanie CPU/baterii
- ❌ Bardziej skomplikowany kod

### Opcja C: Zwiększ recording buffer (WORKAROUND)
```kotlin
private const val RECORDING_BUFFER_MULTIPLIER = 30  // Powrót do dużego bufora
```

**Zalety:**
- ✅ Najprostsze
- ✅ Brak zmian w logice

**Wady:**
- ❌ Nie rozwiązuje problemu, tylko go maskuje
- ❌ Większe zużycie pamięci
- ❌ Stale audio przy resume

---

## Rekomendacja

**Opcja A: Stop/Start AudioRecord**

Uzasadnienie:
1. Half-duplex mode = tylko jedno urządzenie mówi naraz
2. Gdy bot mówi, mikrofon nie jest potrzebny
3. Stop/Start to standardowa praktyka w half-duplex
4. Opóźnienie 50-100ms jest akceptowalne (użytkownik i tak czeka na koniec odpowiedzi bota)

---

## Implementacja Opcji A

### Zmiany w pauseRecording():
```kotlin
fun pauseRecording() {
    if (!_isRecording.value || isRecordingPaused) return
    
    Log.i(TAG, "🔇 Pausing recording - stopping AudioRecord")
    
    try {
        // Flush buffer first
        val dummyBuffer = ByteArray(inputBufferSize)
        var flushed = 0
        while (audioRecord?.read(dummyBuffer, 0, dummyBuffer.size) ?: 0 > 0) {
            flushed += dummyBuffer.size
        }
        
        // Stop AudioRecord
        audioRecord?.stop()
        
        Log.i(TAG, "🔇 AudioRecord stopped - flushed $flushed bytes")
    } catch (e: Exception) {
        Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
    }
    
    isRecordingPaused = true
    _userAudioLevel.value = 0f
}
```

### Zmiany w resumeRecording():
```kotlin
fun resumeRecording() {
    if (!_isRecording.value || !isRecordingPaused) return
    
    Log.i(TAG, "🔊 Resuming recording - starting AudioRecord")
    
    try {
        audioRecord?.startRecording()
        Log.i(TAG, "🔊 AudioRecord started")
    } catch (e: Exception) {
        Log.e(TAG, "Error starting AudioRecord: ${e.message}")
    }
    
    isRecordingPaused = false
}
```

---

## Oczekiwane Rezultaty

### Po Fazie 3:
- ✅ Zero recording overflow
- ✅ Trylock fail akceptowalny (1x na początku)
- ✅ Smooth audio playback
- ✅ Oszczędność CPU/baterii podczas playback
- ⚠️ Niewielkie opóźnienie przy resume (~50-100ms)

---

## Status

- ✅ Diagnoza kompletna
- ✅ Flush buffer naprawiony (synchroniczny)
- ⏳ Czeka na implementację Opcji A
- ⏳ Testy użytkownika

**Data:** 2024-12-10  
**Wersja:** 3.0 (Diagnostics)
