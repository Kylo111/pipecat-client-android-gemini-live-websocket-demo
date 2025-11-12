# Podsumowanie problemów z implementacją Gemini Live API w Android

## Data: 12 listopada 2025

## Kontekst projektu
Aplikacja Android do komunikacji głosowej z Gemini Live API, oparta na bibliotece Pipecat Android SDK.

---

## Problemy napotkane podczas implementacji

### 1. ❌ Niekompatybilność wersji API z nowymi modelami

**Problem:**
```
Websocket onClosing(1008, gemini-2.5-flash-native-audio-preview-09-2025 is not found 
for API version v1alpha, or is not supported for bidiGenerateContent)
```

**Przyczyna:**
- Biblioteka Pipecat (wersje 0.3.4 - 0.3.7) używa API v1alpha
- Endpoint: `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent`
- Nowe modele Gemini 2.5 (w tym `gemini-2.5-flash-native-audio-preview-09-2025`) nie są dostępne w v1alpha
- Tylko starsze modele jak `models/gemini-2.0-flash-exp` działają z v1alpha

**Status:** NIEROZWIĄZANY
- Aktualizacja biblioteki Pipecat do 0.3.7 nie rozwiązała problemu
- Model działa w Gemini AI Studio i innych aplikacjach używających nowszej wersji API

---

### 2. ✅ Nieprawidłowy format systemInstruction

**Problem początkowy:**
```
Invalid value at 'setup.system_instruction' 
(type.googleapis.com/google.ai.generativelanguage.v1alpha.Content)
```

**Rozwiązanie:**
Zmiana z:
```kotlin
systemInstruction = Value.Str(systemPrompt)
```

Na:
```kotlin
systemInstruction = Value.Object(
    "parts" to Value.Object(
        "text" to Value.Str(systemPrompt)
    )
)
```

**Status:** ROZWIĄZANY

---

### 3. ✅ Nieprawidłowa lista głosów

**Problem:**
- Aplikacja miała 30 głosów, które nie pasowały do oficjalnej listy Gemini Live API
- Brak głosu "Zephyr" w oryginalnej liście

**Rozwiązanie:**
Zaktualizowano listę do 8 oficjalnych głosów:
- Puck, Charon, Kore, Fenrir, Aoede, Leda, Orus, Zephyr

**Status:** ROZWIĄZANY

---

### 4. ⚠️ Brak możliwości wysyłania obrazów

**Problem:**
```kotlin
// TODO: The Pipecat RTVIClient library doesn't currently expose a method to send
// realtime input (images) to the Gemini Live API.
```

**Przyczyna:**
- Biblioteka `pipecat-client-android` nie ma metody `sendRealtimeInput()`
- Brak dostępu do surowego WebSocket do wysłania wiadomości `realtimeInput` z `mediaChunks`

**Obejście:**
Funkcja `sendImage()` jest przygotowana, ale wymaga:
1. Aktualizacji biblioteki Pipecat z nową metodą
2. Lub bezpośredniego dostępu do WebSocket

**Status:** NIEROZWIĄZANY - wymaga aktualizacji biblioteki

---

### 5. ✅ Brak szczegółowych logów diagnostycznych

**Problem:**
Trudność w diagnozowaniu problemów z połączeniem

**Rozwiązanie:**
Dodano szczegółowe logi w VoiceClientManager:
- Model, voice, system prompt, API key length przy starcie
- Transport state changes
- Connection/disconnection events
- Wszystkie callback events

**Status:** ROZWIĄZANY

---

## Porównanie z działającymi implementacjami

### Audio-speaker (działa poprawnie)
- **Biblioteka:** Bezpośrednie połączenie WebSocket z OkHttp
- **Endpoint:** Ten sam v1alpha
- **Model:** `models/gemini-1.5-pro` (starszy model)
- **Kluczowa różnica:** Nie używa biblioteki pośredniczącej

### Gemini AI Studio (działa poprawnie)
- **Biblioteka:** `@google/genai` (JavaScript/TypeScript)
- **Wersja API:** Prawdopodobnie nowsza niż v1alpha
- **Model:** `gemini-2.5-flash-native-audio-preview-09-2025` działa bez problemów

---

## Główny problem: Ograniczenia biblioteki Pipecat

### Dlaczego Pipecat nie działa z nowymi modelami?

1. **Zahardkodowana wersja API v1alpha**
   - Biblioteka łączy się tylko z v1alpha endpoint
   - Brak możliwości zmiany wersji API przez konfigurację

2. **Brak wsparcia dla nowych modeli**
   - Gemini 2.5 modele wymagają nowszej wersji API
   - v1alpha wspiera tylko starsze modele

3. **Ograniczona funkcjonalność**
   - Brak metody do wysyłania obrazów
   - Brak dostępu do surowego WebSocket

---

## Możliwe rozwiązania

### Opcja 1: Czekać na aktualizację Pipecat ⏳
**Plusy:**
- Minimalne zmiany w kodzie
- Zachowanie istniejącej architektury

**Minusy:**
- Nie wiadomo kiedy będzie dostępna
- Może nigdy nie wspierać nowszych wersji API

### Opcja 2: Bezpośrednia implementacja WebSocket ✅ ZALECANE
**Plusy:**
- Pełna kontrola nad wersją API
- Możliwość używania najnowszych modeli
- Możliwość wysyłania obrazów
- Wzorowane na działającej implementacji (Audio-speaker)

**Minusy:**
- Wymaga przepisania VoiceClientManager
- Więcej kodu do utrzymania

**Implementacja:**
```kotlin
// Bezpośrednie połączenie z Gemini Live API
val client = OkHttpClient.Builder()
    .connectTimeout(10_000, TimeUnit.MILLISECONDS)
    .pingInterval(15_000, TimeUnit.MILLISECONDS)
    .build()

val request = Request.Builder()
    .url("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey")
    .build()

val webSocket = client.newWebSocket(request, webSocketListener)
```

### Opcja 3: Użycie Firebase AI Logic SDK ⚠️
**Plusy:**
- Oficjalne wsparcie Google
- Regularne aktualizacje

**Minusy:**
- Nie wspiera Live API z WebSocket
- Tylko standardowe API (generateContent)
- Nie nadaje się do real-time voice

---

## Rekomendacja

**Zalecam implementację Opcji 2: Bezpośrednie WebSocket**

Powody:
1. Jedyne rozwiązanie pozwalające na użycie `gemini-2.5-flash-native-audio-preview-09-2025`
2. Wzorowane na działającej implementacji (Audio-speaker)
3. Pełna kontrola nad funkcjonalnością
4. Możliwość wysyłania obrazów
5. Lepsze wsparcie dla języka polskiego (nowy model)

---

## Co zostało zaimplementowane i działa

✅ Settings screen z konfiguracją:
- API Key
- Model Name
- System Prompt
- Voice selection (8 oficjalnych głosów)

✅ Podstawowa komunikacja głosowa (ze starym modelem)

✅ Wake lock (ekran nie gaśnie podczas rozmowy)

✅ Zwiększenie głośności audio

✅ Obsługa uprawnień (mikrofon, kamera, storage)

✅ UI do wyboru obrazów (kamera/galeria)

✅ Szczegółowe logi diagnostyczne

---

## Następne kroki

1. **Decyzja:** Czy przepisać na bezpośrednie WebSocket?
2. **Jeśli TAK:** Implementacja na podstawie Audio-speaker
3. **Jeśli NIE:** Czekać na aktualizację Pipecat lub używać starego modelu

---

## Linki i zasoby

- [Gemini Live API Documentation](https://ai.google.dev/gemini-api/docs/live)
- [Pipecat Android SDK](https://docs.pipecat.ai/client/android)
- [Audio-speaker (działająca implementacja)](https://github.com/Kylo111/Audio-speaker)
- [Issue: gemini-2.5-flash not found in v1alpha](https://github.com/google/adk-python/issues/2591)
- [Deprecated Android SDK](https://github.com/google-gemini/deprecated-generative-ai-android)

---

## Wnioski

Głównym problemem jest **niekompatybilność biblioteki Pipecat z nowymi modelami Gemini 2.5**. 
Biblioteka używa przestarzałej wersji API (v1alpha), która nie wspiera najnowszych modeli.

Dla produkcyjnej aplikacji wymagającej najlepszej jakości (szczególnie dla języka polskiego), 
**konieczna jest implementacja bezpośredniego połączenia WebSocket** bez biblioteki pośredniczącej.
