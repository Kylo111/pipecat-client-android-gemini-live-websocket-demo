# Audio Fix - Faza 2: Buffer Optimization

## Wyniki Fazy 1 (Batching)

### ✅ Sukces:
- Batching działa poprawnie (8 chunków → 15360 bajtów)
- Trylock fail zredukowany z ~10+ do 1 wystąpienia
- Redukcja wywołań JNI o ~88%

### ❌ Pozostałe problemy:
1. **Trylock fail** - nadal występuje (rzadko, ale jest)
2. **Recording buffer overflow** - NOWY problem!

---

## Analiza Problemu 2: Recording Buffer Overflow

### Logi:
```
12-10 11:56:09.873 W AudioFlinger_Threads: Overrun! RsmpInBuffer drop data 320
12-10 11:56:09.873 W AudioFlinger_Threads: RecordThread: buffer overflow
```

### Przyczyna:
1. **Gigantyczny bufor nagrywania:** 38400 bajtów (2.4 sekundy)
2. **pauseRecording() nie opróżnia bufora** - stare audio "wycieka" przy resume
3. **Half-duplex timing** - bot mówi, bufor się zapełnia, overflow

### Matematyka:
```
inputBufferSize = minBufferSize * 30
minBufferSize ≈ 1280 bytes (80ms @ 16kHz)
inputBufferSize = 1280 * 30 = 38400 bytes = 2.4 sekundy

Problem: Gdy bot mówi 2+ sekundy, bufor się zapełnia i overflow
```

---

## Rozwiązanie Fazy 2

### 1. Zmniejsz bufor nagrywania (4x zamiast 30x)
```kotlin
private const val RECORDING_BUFFER_MULTIPLIER = 4   // 256ms buffer
private const val PLAYBACK_BUFFER_MULTIPLIER = 30   // 1.2s buffer
```

**Uzasadnienie:**
- Recording nie potrzebuje dużego bufora (nie ma network jitter)
- Mały bufor = szybsze opróżnianie = brak overflow
- Playback nadal ma duży bufor dla stabilności

### 2. Flush recording buffer przy pauseRecording()
```kotlin
fun pauseRecording() {
    isRecordingPaused = true
    
    // Flush buffer - read and discard all pending data
    val dummyBuffer = ByteArray(inputBufferSize)
    while (audioRecord?.read(dummyBuffer, 0, dummyBuffer.size) > 0) {
        // Discard
    }
}
```

**Uzasadnienie:**
- Stare audio z przed pauzy nie powinno być wysyłane po resume
- Zapobiega echo/feedback issues
- Zapobiega overflow podczas długich odpowiedzi bota

### 3. Zwiększ BATCH_SIZE (16 zamiast 8)
```kotlin
val BATCH_SIZE = 16  // 30720 bytes = 320ms
```

**Uzasadnienie:**
- Jeszcze mniej wywołań JNI (~15 writes/sec zamiast ~30)
- Jeszcze mniej stresu dla sterownika ALSA
- Powinno wyeliminować ostatnie trylock fail

---

## Oczekiwane Rezultaty

### Przed Fazą 2:
- ✅ Batching działa (8 chunków)
- ⚠️ Trylock fail: 1 wystąpienie
- ❌ Recording overflow: Częste
- ⚠️ Write frequency: ~30/sec

### Po Fazie 2:
- ✅ Batching ulepszone (16 chunków)
- ✅ Trylock fail: 0 wystąpień (cel)
- ✅ Recording overflow: 0 wystąpień
- ✅ Write frequency: ~15/sec

---

## Plan Testowania

### Test 1: Długa odpowiedź bota (overflow test)
1. Zadaj pytanie wymagające długiej odpowiedzi (30+ sekund)
2. Pozwól botowi mówić bez przerywania
3. Sprawdź logi:
   - ❌ Brak "Overrun! RsmpInBuffer drop data"
   - ❌ Brak "RecordThread: buffer overflow"

### Test 2: Przerwanie bota (flush test)
1. Zadaj pytanie
2. Przerwij bota w połowie odpowiedzi
3. Od razu zacznij mówić
4. Sprawdź:
   - ✅ Twoje audio jest wysyłane natychmiast
   - ✅ Brak echo/feedback
   - ✅ Brak "stale audio" z przed przerwania

### Test 3: Trylock fail elimination
1. Uruchom długą rozmowę (5+ minut)
2. Monitoruj logi:
   ```bash
   test_audio_batching.bat
   ```
3. Sprawdź:
   - ❌ Brak "trylock fail" errors
   - ✅ "Batched 16 chunks → 30720 bytes"
   - ✅ Smooth audio playback

---

## Metryki

### Recording Buffer:
- **Przed:** 38400 bytes (2.4s) → **Po:** 5120 bytes (320ms)
- **Redukcja:** 87% mniejszy bufor

### Playback Batching:
- **Przed:** 8 chunks (15360B) → **Po:** 16 chunks (30720B)
- **Write frequency:** 30/sec → 15/sec
- **Redukcja:** 50% mniej wywołań

### Oczekiwana jakość:
- ✅ Zero trylock fail
- ✅ Zero recording overflow
- ✅ Smooth audio (brak trzasków)
- ✅ Fast interruption (<200ms)

---

## Backup Plan (jeśli nadal są problemy)

### Plan C: Adaptive batching
```kotlin
val BATCH_SIZE = when {
    audioQueue.size > 200 -> 32  // Agresywny
    audioQueue.size > 100 -> 16  // Normalny
    else -> 8                     // Konserwatywny
}
```

### Plan D: Queue size limit
```kotlin
if (audioQueue.size > 300) {
    // Drop oldest chunks to prevent bloat
    audioQueue.removeAt(0)
    Log.w(TAG, "Queue bloat - dropping old chunk")
}
```

---

## Status

- ✅ Kod zaimplementowany (Faza 2)
- ✅ Build successful
- ⏳ Czeka na testy użytkownika
- ⏳ Weryfikacja metryk

**Data:** 2024-12-10  
**Wersja:** 2.0  
**Poprzednia wersja:** AUDIO_BATCHING_FIX.md
