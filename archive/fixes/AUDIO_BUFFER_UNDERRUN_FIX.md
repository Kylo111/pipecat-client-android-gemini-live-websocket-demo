# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Task completed - historical record
**Current Documentation:** See /docs/implementation/audio-pipeline.md or /docs/implementation/picovoice-integration.md

---

# Fix: Audio Buffer Underrun (Piki/Trzaski podczas odtwarzania)

## Problem

Podczas odtwarzania audio od Gemini Live słychać było piki i trzaski. Problem wynikał z **buffer underrun** w AudioTrack.

### Przyczyna

Gdy Gemini wysyła audio przez internet, pakiety przychodzą w nieregularnych odstępach czasu (network jitter):
- Raz pakiet przychodzi po 10ms
- Raz po 15ms  
- Czasem jest opóźnienie 20-30ms

Aplikacja odtwarzała audio natychmiast po otrzymaniu. Jeśli kolejny pakiet spóźnił się choćby o kilka milisekund:
1. Bufor AudioTrack się opróżniał (underrun)
2. Głośnik "milknął" na ułamek sekundy
3. Gdy przyszedł kolejny pakiet, głośnik nagle dostawał dźwięk
4. To nagłe włączenie/wyłączenie prądu w głośniku słychać jako "pik" lub trzask

## Rozwiązanie

### 1. Zwiększenie bufora AudioTrack (4x → 8x)

**Przed:**
```kotlin
val bufferSize = minBufferSize * 4
```

**Po:**
```kotlin
val bufferSize = minBufferSize * 8
```

**Efekt:** Większy bufor daje więcej czasu na przybycie kolejnych pakietów przed underrun.

### 2. Użycie WRITE_BLOCKING mode (API 21+)

**Przed:**
```kotlin
val written = audioTrackInstance.write(boostedAudio, 0, boostedAudio.size)
```

**Po:**
```kotlin
val written = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    // Blocking write - czeka aż bufor ma miejsce
    audioTrackInstance.write(boostedAudio, 0, boostedAudio.size, AudioTrack.WRITE_BLOCKING)
} else {
    // Fallback dla starszych API
    audioTrackInstance.write(boostedAudio, 0, boostedAudio.size)
}
```

**Efekt:** `WRITE_BLOCKING` zapewnia, że dane są w pełni zapisane do bufora przed powrotem. Zapobiega to sytuacji, gdzie część danych jest odrzucana.

### 3. Monitoring underrun (API 24+)

Dodano monitoring underrun dla debugowania:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    val underrunCount = audioTrackInstance.underrunCount
    if (underrunCount > 0 && DEBUG_LOGGING) {
        Log.w(TAG, "⚠️ AudioTrack underrun detected (count: $underrunCount)")
    }
}
```

### 4. Lepsze error handling

Dodano obsługę błędu `ERROR_DEAD_OBJECT` z automatycznym recovery:

```kotlin
if (written == AudioTrack.ERROR_DEAD_OBJECT) {
    Log.e(TAG, "❌ AudioTrack dead - attempting recovery")
    try {
        audioTrackInstance.stop()
        audioTrackInstance.release()
        audioTrack = null
        // Will be recreated on next connection
    } catch (e: Exception) {
        Log.e(TAG, "Error during AudioTrack recovery: ${e.message}")
    }
}
```

## Zmienione pliki

- `VoiceClientManager.kt`:
  - `startAudioPlayback()` - zwiększony bufor do 8x
  - `handleAudioMessage()` - dodano WRITE_BLOCKING, monitoring underrun, lepsze error handling

## Testowanie

1. Uruchom aplikację i rozpocznij rozmowę z Gemini
2. Pozwól botowi mówić przez dłuższy czas (30+ sekund)
3. Sprawdź czy słychać piki/trzaski podczas odtwarzania
4. Przetestuj w różnych warunkach sieci (WiFi, LTE, słaby sygnał)

### Oczekiwany rezultat

- ✅ Brak pików/trzasków podczas odtwarzania audio
- ✅ Płynne odtwarzanie nawet przy network jitter
- ✅ Stabilne działanie przez długie sesje

## Techniczne szczegóły

### Buffer size calculation

Dla sample rate 24000 Hz, mono, 16-bit PCM:
- Min buffer size: ~3840 bytes (typowo)
- Używany buffer: 3840 * 8 = 30720 bytes
- Czas bufora: ~640ms

To daje wystarczający margines na network jitter (typowo 10-50ms).

### WRITE_BLOCKING vs WRITE_NON_BLOCKING

- **WRITE_BLOCKING**: Czeka aż bufor ma miejsce, gwarantuje pełny zapis
- **WRITE_NON_BLOCKING**: Zwraca natychmiast, może zapisać tylko część danych

Dla streamingu audio WRITE_BLOCKING jest lepszy, bo zapewnia ciągłość danych w buforze.

## Status

✅ Zaimplementowane
✅ Zbudowane
✅ Zainstalowane na urządzeniu
⏳ Oczekuje na testy użytkownika
