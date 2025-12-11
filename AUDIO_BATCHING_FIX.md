# Audio Batching Fix - Plan Naprawy

## Problem (Diagnoza z logów)

### 1. Trylock Fail - Sterownik ALSA przeciążony
```
E AudioALSAPlaybackHandlerBase: getQueuedFramesInfo(), pcm_get_htimestamp or trylock fail, ret -1
```
**Przyczyna:** Pętla playback wykonuje 10+ zapisów po 1920B w ciągu 3ms, przeciążając sterownik audio.

### 2. Buffer Bloat - 14 sekund opóźnienia
```
queueSize: 358 chunks × 1920B = 687KB ≈ 14.3 sekundy audio
```
**Przyczyna:** Sieć dostarcza dane szybciej niż głośnik je odtwarza.

### 3. Inefficient Writes - Nadmiar wywołań JNI
```
10 × AudioTrack.write(1920B) w 3ms = ~3333 wywołań/sekundę
```
**Przyczyna:** Każde wywołanie write() to kosztowne przejście Java → Native (JNI).

---

## Rozwiązanie: Audio Batching

### Koncepcja
Zamiast pisać małe chunki (1920B) pojedynczo, agregujemy je w większe batche (15360B) przed zapisem.

### Przed (Old):
```
Queue: [1920B] [1920B] [1920B] [1920B] [1920B] [1920B] [1920B] [1920B]
         ↓       ↓       ↓       ↓       ↓       ↓       ↓       ↓
      write   write   write   write   write   write   write   write
      (8 wywołań JNI w ~24ms)
```

### Po (New):
```
Queue: [1920B] [1920B] [1920B] [1920B] [1920B] [1920B] [1920B] [1920B]
         └──────────────────────────────────────────────────────┘
                              ↓
                    Aggregate to 15360B
                              ↓
                          write ONCE
                    (1 wywołanie JNI w ~24ms)
```

---

## Implementacja

### Zmiany w `AudioEngine.kt`

#### 1. Konfiguracja batchingu
```kotlin
val BATCH_SIZE = 8  // 8 chunków × 1920B = 15360B (~160ms audio)
```

#### 2. Agregacja chunków
```kotlin
val chunksToWrite = mutableListOf<AudioChunk>()
audioQueueMutex.withLock {
    var collected = 0
    while (collected < BATCH_SIZE && audioQueue.isNotEmpty()) {
        val chunk = audioQueue.removeAt(0)
        if (chunk.generationId == currentGen) {
            chunksToWrite.add(chunk)
            collected++
        }
    }
}
```

#### 3. Sklejanie bufora
```kotlin
val totalSize = chunksToWrite.sumOf { it.data.size }
val batchBuffer = ByteArray(totalSize)
var offset = 0

for (chunk in chunksToWrite) {
    System.arraycopy(chunk.data, 0, batchBuffer, offset, chunk.data.size)
    offset += chunk.data.size
}
```

#### 4. Pojedynczy zapis
```kotlin
audioTrackInstance.write(batchBuffer, 0, batchBuffer.size, AudioTrack.WRITE_BLOCKING)
```

---

## Oczekiwane Rezultaty

### Metryki przed naprawą:
- **Częstotliwość zapisów:** ~250 writes/sec (1920B co 4ms)
- **Wywołania JNI:** ~3333/sec
- **Błędy ALSA:** Częste "trylock fail"
- **Opóźnienie:** 14+ sekund (buffer bloat)

### Metryki po naprawie:
- **Częstotliwość zapisów:** ~30 writes/sec (15360B co 33ms)
- **Wywołania JNI:** ~400/sec (redukcja o 88%)
- **Błędy ALSA:** Brak "trylock fail"
- **Opóźnienie:** <2 sekundy (normalny buffer)

---

## Plan Testowania

### 1. Weryfikacja batchingu w logach
```bash
test_audio_batching.bat
```

Szukaj:
- ✅ `Playback loop started (BATCHING ENABLED: 8 chunks per write)`
- ✅ `📦 Batched 8 chunks → 15360 bytes`
- ✅ `✍️ AudioTrack.write() - requested: 15360, written: 15360`
- ❌ Brak `trylock fail` errors

### 2. Test jakości audio
1. Uruchom rozmowę z botem
2. Pozwól botowi mówić przez 10+ sekund
3. Przerwij botowi w połowie zdania
4. Sprawdź:
   - ✅ Audio gładkie, bez trzasków
   - ✅ Przerwanie natychmiastowe (<200ms)
   - ✅ Brak "cyfrowych czkawek"

### 3. Test obciążenia
1. Uruchom rozmowę
2. Przełącz się do innej aplikacji (background)
3. Wróć do aplikacji
4. Sprawdź:
   - ✅ Audio nadal płynne
   - ✅ Brak dropoutów
   - ✅ Synchronizacja UI (audio indicator)

---

## Potencjalne Problemy i Rozwiązania

### Problem 1: Zwiększone opóźnienie przerwania
**Symptom:** Bot mówi 160ms dłużej po przerwaniu  
**Przyczyna:** Batch 15360B = 160ms audio w buforze  
**Rozwiązanie:** `interruptPlayback()` już wywołuje `flush()` - powinno działać

### Problem 2: Nierówne chunki
**Symptom:** Ostatni batch <8 chunków  
**Status:** ✅ Kod obsługuje - pisze ile jest dostępne

### Problem 3: Generation mismatch w trakcie batcha
**Symptom:** Część chunków z nowej generacji w starym batchu  
**Status:** ✅ Kod sprawdza generationId dla każdego chunku

---

## Monitoring i Diagnostyka

### Logi do obserwacji:
```
🔄 playbackJob iteration #X - queueSize: Y
📦 Batched 8 chunks → 15360 bytes
✍️ AudioTrack.write() - requested: 15360, written: 15360
```

### Czerwone flagi:
```
❌ trylock fail (sterownik nadal przeciążony)
❌ written: 480 (partial write - buffer pełny)
❌ queueSize: 300+ (buffer bloat wraca)
```

---

## Następne Kroki (jeśli batching nie wystarczy)

### Plan B: Dynamiczny batch size
```kotlin
val BATCH_SIZE = when {
    audioQueue.size > 200 -> 16  // Agresywny batching
    audioQueue.size > 100 -> 8   // Normalny
    else -> 4                     // Konserwatywny
}
```

### Plan C: Queue throttling
```kotlin
if (audioQueue.size > MAX_QUEUE_SIZE) {
    // Drop oldest chunks
    audioQueue.removeAt(0)
}
```

### Plan D: Adaptive buffer size
```kotlin
val outputBufferSize = when (networkQuality) {
    GOOD -> minBufferSize * 8
    POOR -> minBufferSize * 30
}
```

---

## Status

- ✅ Kod zaimplementowany
- ✅ Build successful
- ⏳ Czeka na testy użytkownika
- ⏳ Weryfikacja metryk

**Data:** 2024-12-10  
**Wersja:** 1.0  
**Autor:** Kiro AI Assistant
