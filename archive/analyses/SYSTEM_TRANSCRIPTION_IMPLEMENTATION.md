# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Task completed - historical record
**Current Documentation:** See /docs/testing/ or /docs/implementation/ for current documentation

---

# Implementacja Systemowej Transkrypcji Android

## Podsumowanie

Zamieniono transkrypcję z Gemini API na systemową transkrypcję Android (SpeechRecognizer) w celu poprawy jakości transkrypcji mowy użytkownika.

## Problem

- Transkrypcja z Gemini API (`inputTranscription`) miała słabą jakość dla mowy użytkownika
- Transkrypcja bota (`outputTranscription`) była dobra, ale dla spójności również została zmieniona
- Systemowa transkrypcja Android działa znacznie lepiej dla języka polskiego

## Zmiany w Kodzie

### 1. VoiceClientManager.kt

**Dodane importy:**
```kotlin
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
```

**Nowe zmienne:**
```kotlin
// System transcription for user audio
private var userSpeechRecognizer: SpeechRecognizer? = null
private var userRecognitionIntent: Intent? = null
private var isUserRecognitionActive = false

// System transcription for bot audio
private var botSpeechRecognizer: SpeechRecognizer? = null
private var botRecognitionIntent: Intent? = null
private var isBotRecognitionActive = false
private val botAudioBuffer = mutableListOf<ByteArray>()
```

**Wyłączenie transkrypcji Gemini:**
```kotlin
// W setup message:
output_audio_transcription = null,  // Było: OutputAudioTranscription()
input_audio_transcription = null,   // Było: InputAudioTranscription()
```

**Usunięcie obsługi transkrypcji z Gemini:**
- Usunięto kod obsługujący `inputTranscription` i `outputTranscription` z `serverContent`
- Transkrypcja jest teraz obsługiwana przez Android SpeechRecognizer

### 2. Nowe Metody

**`startUserSpeechRecognition()`**
- Uruchamia Android SpeechRecognizer dla ciągłej transkrypcji mowy użytkownika
- Konfiguracja:
  - Język: `pl-PL` (polski)
  - Model: `LANGUAGE_MODEL_FREE_FORM` (swobodna mowa)
  - Partial results: włączone (wyniki częściowe)
- Automatyczne restartowanie po zakończeniu rozpoznawania (ciągła transkrypcja)
- Obsługa błędów z automatycznym restartem

**`stopUserSpeechRecognition()`**
- Zatrzymuje i zwalnia zasoby SpeechRecognizer dla użytkownika

**`startBotSpeechRecognition(audioData: ByteArray)`**
- Placeholder dla transkrypcji audio bota
- Uwaga: Android SpeechRecognizer działa tylko z mikrofonem, nie z raw audio
- Dla pełnej implementacji transkrypcji bota potrzebne byłoby:
  - Google Cloud Speech-to-Text API
  - ML Kit Speech Recognition
  - Lub zapis audio do pliku i odtworzenie przez MediaPlayer

**`stopBotSpeechRecognition()`**
- Zatrzymuje transkrypcję bota

### 3. Integracja z Lifecycle

**W `setupComplete`:**
```kotlin
// Start system transcription for user audio
startUserSpeechRecognition()
```

**W `handleDisconnect()`:**
```kotlin
// Stop speech recognition
stopUserSpeechRecognition()
stopBotSpeechRecognition()
```

## Jak Działa

### Transkrypcja Użytkownika

1. Po nawiązaniu połączenia WebSocket (`setupComplete`) uruchamia się `startUserSpeechRecognition()`
2. SpeechRecognizer nasłuchuje ciągle mowy użytkownika
3. Gdy wykryje mowę, zwraca transkrypcję przez callback `onResults()`
4. Transkrypcja jest przekazywana do:
   - `sessionManager?.captureUserTranscript(transcript)` - zapis do sesji
   - `onUserTranscript?.invoke(transcript)` - callback dla UI
   - `updateActivity()` - aktualizacja czasu ostatniej aktywności
5. Po zakończeniu rozpoznawania, SpeechRecognizer automatycznie się restartuje dla ciągłej transkrypcji

### Obsługa Błędów

- Błędy `NO_MATCH` i `SPEECH_TIMEOUT` są ignorowane (normalne w ciągłej transkrypcji)
- Inne błędy są logowane
- Automatyczny restart po błędzie (jeśli połączenie jest aktywne)

### Transkrypcja Bota

- Obecnie nie jest w pełni zaimplementowana
- Android SpeechRecognizer wymaga mikrofonu jako źródła audio
- Dla pełnej implementacji potrzebne jest zewnętrzne API lub zapis do pliku

## Konfiguracja

### Język

Domyślnie ustawiony na polski (`pl-PL`). Można zmienić w metodzie `startUserSpeechRecognition()`:

```kotlin
putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL") // Zmień na inny język
```

### Uprawnienia

Używa istniejącego uprawnienia `RECORD_AUDIO` z AndroidManifest.xml - nie wymaga dodatkowych uprawnień.

## Testowanie

### Przed Testem

1. Zainstaluj aplikację: `./gradlew installDebug`
2. Wyczyść logi: `adb -s EM95IBKZEYIFSO69 logcat -c`
3. Uruchom aplikację na urządzeniu

### Podczas Testu

1. Rozpocznij rozmowę głosową
2. Mów po polsku
3. Obserwuj logi:

```bash
adb -s EM95IBKZEYIFSO69 logcat | grep -E "VoiceClientManager|SpeechRecognizer"
```

### Czego Szukać w Logach

**Uruchomienie transkrypcji:**
```
I/VoiceClientManager: Starting user speech recognition
I/VoiceClientManager: User speech recognition started
D/VoiceClientManager: User speech recognizer ready
```

**Transkrypcja mowy:**
```
D/VoiceClientManager: User transcript (system): [tekst wypowiedzi]
```

**Błędy (jeśli występują):**
```
W/VoiceClientManager: User speech recognition error: [opis błędu]
```

## Zalety Systemowej Transkrypcji

✅ **Lepsza jakość** - Android SpeechRecognizer jest zoptymalizowany dla języka polskiego
✅ **Offline support** - może działać offline (jeśli urządzenie ma zainstalowane modele)
✅ **Niższe opóźnienie** - lokalne przetwarzanie
✅ **Mniejsze zużycie danych** - nie wysyła audio do Gemini dla transkrypcji
✅ **Ciągła transkrypcja** - automatyczne restartowanie dla nieprzerwanych wyników

## Ograniczenia

⚠️ **Transkrypcja bota** - nie jest w pełni zaimplementowana (wymaga dodatkowego API)
⚠️ **Wymaga Google Services** - SpeechRecognizer wymaga Google Play Services
⚠️ **Język** - obecnie ustawiony na polski, wymaga zmiany kodu dla innych języków

## Następne Kroki

Jeśli potrzebna jest również transkrypcja audio bota, możliwe opcje:

1. **Google Cloud Speech-to-Text API** - płatne, ale wysokiej jakości
2. **ML Kit Speech Recognition** - darmowe, on-device
3. **Zapis do pliku** - zapisz audio bota do pliku tymczasowego i użyj SpeechRecognizer
4. **Pozostaw Gemini** - dla bota można zostawić `outputTranscription` z Gemini

## Status

✅ Kod zaimplementowany
✅ Kompilacja udana
✅ Aplikacja zainstalowana na urządzeniu
⏳ Oczekiwanie na testy użytkownika

**Proszę przetestować aplikację i potwierdzić, że transkrypcja działa lepiej!**
