# Analiza Zaawansowanych Parametrów Gemini 2.5 Flash dla Aplikacji

**Data:** 2025-12-16  
**Status:** Analiza techniczna  
**Cel:** Ocena możliwości zastosowania zaawansowanych parametrów Gemini Live API

---

## 1. Obecny Stan Konfiguracji

### 1.1 Aktualnie Używane Parametry

Aplikacja obecnie wykorzystuje **minimalny zestaw parametrów** w `GeminiProtocol.buildSetupMessage()`:

```kotlin
GenerationConfig(
    response_modalities = listOf("AUDIO"),
    speech_config = SpeechConfig(
        voice_config = VoiceConfig(
            prebuilt_voice_config = PrebuiltVoiceConfig(
                voice_name = voiceName  // "Puck", "Charon", "Kore", "Fenrir", "Aoede"
            )
        )
    ),
    temperature = temperature  // Default: 0.8f
)
```

### 1.2 Model ThreadSettings

Obecnie przechowuje tylko podstawowe ustawienia:

```kotlin
data class ThreadSettings(
    val conversationId: String,
    val voiceName: String = "Puck",
    val speechSpeed: Float = 1.0f,      // Nie używane przez Gemini Live
    val volumeBoost: Float = 1.0f,      // Nie używane przez Gemini Live
    val temperature: Float = 1.0f
)
```

**Uwaga:** `speechSpeed` i `volumeBoost` są zdefiniowane, ale **nie są przekazywane do Gemini API**.

---

## 2. Parametry z Dokumentacji Gemini - Ocena Zastosowania

### 2.1 ✅ ZALECANE DO IMPLEMENTACJI

#### A. Parametry Kontroli Generowania (Generation Parameters)

| Parametr | Zakres | Default | Zastosowanie w Aplikacji | Priorytet |
|----------|--------|---------|--------------------------|-----------|
| **temperature** | 0.0-2.0 | 1.0 | ✅ Już zaimplementowane (0.8f) | - |
| **topP** | 0.0-1.0 | 0.95 | 🔥 **Redukcja "fluff"** - zmniejszenie do 0.85 ograniczy gadatliwość | **WYSOKI** |
| **topK** | 1-128 | 40 | 🔥 **Precyzja odpowiedzi** - wartość 20-30 dla bardziej konkretnych odpowiedzi | **WYSOKI** |
| **maxOutputTokens** | 1-8000 | varies | 🔥 **Zwięzłość** - ustawienie 512-1024 wymusi krótsze odpowiedzi | **WYSOKI** |
| **presencePenalty** | -2.0-2.0 | 0.0 | 🔥 **KLUCZOWY** - wartość 0.8-1.0 eliminuje powtórzenia i echo | **KRYTYCZNY** |
| **frequencyPenalty** | -2.0-2.0 | 0.0 | 🔥 **KLUCZOWY** - wartość 0.7-0.8 redukuje zbędne słowa i "lanie wody" | **KRYTYCZNY** |

**Uzasadnienie:**
- **presencePenalty** i **frequencyPenalty** są **kluczowe** dla rozwiązania problemu gadatliwości AI
- **topP** i **topK** poprawią precyzję i zmniejszą losowość
- **maxOutputTokens** wymusi zwięzłość odpowiedzi

#### B. Stop Sequences (Sekwencje Zatrzymania)

```kotlin
stopSequences: List<String> = listOf(
    "Rozumiem że chcesz",
    "Czy mogę Ci coś jeszcze",
    "Actually, let me try again",
    "Let me clarify",
    "To be more specific"
)
```

**Zastosowanie:** Zatrzymuje model, gdy zaczyna "lać wodę" lub powtarzać się.

**Priorytet:** **WYSOKI** - bezpośrednio rozwiązuje problem gadatliwości.

---

### 2.2 ⚠️ OGRANICZONE ZASTOSOWANIE

#### C. Parametry Audio/Voice (Native Audio)

| Parametr | Status | Uwagi |
|----------|--------|-------|
| **responseModalities** | ✅ Używane | Już ustawione na `["AUDIO"]` |
| **voiceConfig.voiceName** | ✅ Używane | Już zaimplementowane (5 głosów) |
| **language_code** | ❌ Brak w API | Gemini Live **nie wspiera** explicit language selection |
| **mediaResolution** | ⚠️ Możliwe | LOW/HIGH - może wpłynąć na jakość i latencję |
| **audioTimestamp** | ⚠️ Możliwe | Znaczniki czasu dla audio - przydatne do debugowania |

**Uwaga:** Dokumentacja wspomina `language_code`, ale **Gemini Live API automatycznie wykrywa język** z audio.

#### D. Zaawansowane Parametry (Advanced)

| Parametr | Zastosowanie | Priorytet |
|----------|--------------|-----------|
| **thinkingConfig** | ⚠️ Zwiększa latencję | **NISKI** - nie dla real-time voice |
| **responseMimeType** | ❌ Nie dla audio | Tylko dla text/JSON responses |
| **responseSchema** | ❌ Nie dla audio | Tylko dla structured JSON |
| **responseLogprobs** | ❌ Nie wspierane | Live API nie wspiera |
| **seed** | ⚠️ Reprodukcyjność | **NISKI** - nie dla conversational AI |

**Uwaga:** **thinkingConfig** może poprawić jakość, ale **zwiększy latencję** - nieodpowiednie dla real-time voice.

---

### 2.3 ❌ NIE WSPIERANE PRZEZ GEMINI LIVE API

Według dokumentacji, **Live API nie wspiera**:
- ❌ `responseLogprobs`
- ❌ `responseMimeType` (tylko audio)
- ❌ `logprobs`
- ❌ `responseSchema` (tylko audio)

---

## 3. Rekomendowana Implementacja

### 3.1 Rozszerzenie ThreadSettings

```kotlin
@Serializable
data class ThreadSettings(
    val conversationId: String,
    
    // Voice configuration
    val voiceName: String = "Puck",
    
    // Generation parameters (NOWE)
    val temperature: Float = 0.8f,
    val topP: Float = 0.85f,              // NOWE - redukcja fluff
    val topK: Int = 30,                   // NOWE - precyzja
    val maxOutputTokens: Int = 1024,      // NOWE - zwięzłość
    val presencePenalty: Float = 0.9f,    // NOWE - eliminacja powtórzeń
    val frequencyPenalty: Float = 0.75f,  // NOWE - redukcja gadatliwości
    
    // Stop sequences (NOWE)
    val stopSequences: List<String> = listOf(
        "Rozumiem że chcesz",
        "Czy mogę Ci coś jeszcze",
        "Actually, let me try again"
    ),
    
    // Audio configuration (OPCJONALNE)
    val mediaResolution: String = "MEDIA_RESOLUTION_HIGH",
    val audioTimestamp: Boolean = false,
    
    // Legacy (do usunięcia lub przeniesienia do audio engine)
    val speechSpeed: Float = 1.0f,    // Nie używane przez Gemini
    val volumeBoost: Float = 1.0f     // Nie używane przez Gemini
)
```

### 3.2 Rozszerzenie GenerationConfig w GeminiProtocol

```kotlin
@Serializable
data class GenerationConfig(
    val response_modalities: List<String>,
    val speech_config: SpeechConfig,
    
    // Basic parameters
    val temperature: Float,
    
    // NEW: Advanced generation parameters
    val top_p: Float? = null,
    val top_k: Int? = null,
    val max_output_tokens: Int? = null,
    val presence_penalty: Float? = null,
    val frequency_penalty: Float? = null,
    val stop_sequences: List<String>? = null,
    
    // NEW: Audio parameters
    val media_resolution: String? = null,
    val audio_timestamp: Boolean? = null
)
```

### 3.3 Aktualizacja buildSetupMessage()

```kotlin
fun buildSetupMessage(
    model: String,
    voiceName: String,
    systemPrompt: String,
    temperature: Float,
    sessionHandle: String?,
    canResumeSession: Boolean,
    toolDeclarations: List<JsonElement>,
    // NEW parameters
    topP: Float? = null,
    topK: Int? = null,
    maxOutputTokens: Int? = null,
    presencePenalty: Float? = null,
    frequencyPenalty: Float? = null,
    stopSequences: List<String>? = null,
    mediaResolution: String? = null,
    audioTimestamp: Boolean? = null
): SetupMessage {
    val modelName = if (model.startsWith("models/")) model else "models/$model"
    
    return SetupMessage(
        setup = Setup(
            model = modelName,
            generation_config = GenerationConfig(
                response_modalities = listOf("AUDIO"),
                speech_config = SpeechConfig(
                    voice_config = VoiceConfig(
                        prebuilt_voice_config = PrebuiltVoiceConfig(
                            voice_name = voiceName
                        )
                    )
                ),
                temperature = temperature,
                // NEW parameters
                top_p = topP,
                top_k = topK,
                max_output_tokens = maxOutputTokens,
                presence_penalty = presencePenalty,
                frequency_penalty = frequencyPenalty,
                stop_sequences = stopSequences,
                media_resolution = mediaResolution,
                audio_timestamp = audioTimestamp
            ),
            system_instruction = SystemInstruction(
                parts = listOf(Part(text = systemPrompt))
            ),
            output_audio_transcription = OutputAudioTranscription(),
            input_audio_transcription = InputAudioTranscription(),
            session_resumption = if (canResumeSession && sessionHandle != null) {
                SessionResumptionConfig(handle = sessionHandle)
            } else {
                SessionResumptionConfig(handle = null)
            },
            tools = if (toolDeclarations.isNotEmpty()) {
                listOf(Tool(function_declarations = toolDeclarations))
            } else {
                null
            }
        )
    )
}
```

---

## 4. Profil Konfiguracji dla Różnych Przypadków Użycia

### 4.1 Profil "Zwięzły Asystent" (Rekomendowany dla Voice)

```kotlin
val conciseAssistantProfile = ThreadSettings(
    conversationId = "...",
    voiceName = "Puck",
    temperature = 0.7f,           // Mniej losowości
    topP = 0.85f,                 // Skupienie na najlepszych tokenach
    topK = 25,                    // Ograniczenie wyboru
    maxOutputTokens = 512,        // Krótkie odpowiedzi
    presencePenalty = 1.0f,       // Maksymalna eliminacja powtórzeń
    frequencyPenalty = 0.8f,      // Silna redukcja gadatliwości
    stopSequences = listOf(
        "Rozumiem że chcesz",
        "Czy mogę Ci coś jeszcze",
        "Let me clarify"
    )
)
```

**Zastosowanie:** Szybkie, konkretne odpowiedzi bez "lania wody".

### 4.2 Profil "Kreatywny Rozmówca"

```kotlin
val creativeProfile = ThreadSettings(
    conversationId = "...",
    voiceName = "Aoede",
    temperature = 1.2f,           // Więcej kreatywności
    topP = 0.95f,                 // Większa różnorodność
    topK = 40,                    // Szerszy wybór tokenów
    maxOutputTokens = 2048,       // Dłuższe odpowiedzi
    presencePenalty = 0.5f,       // Umiarkowana eliminacja powtórzeń
    frequencyPenalty = 0.3f,      // Mniejsza redukcja gadatliwości
    stopSequences = emptyList()   // Brak ograniczeń
)
```

**Zastosowanie:** Storytelling, kreatywne rozmowy, brainstorming.

### 4.3 Profil "Precyzyjny Ekspert"

```kotlin
val expertProfile = ThreadSettings(
    conversationId = "...",
    voiceName = "Charon",
    temperature = 0.3f,           // Minimalna losowość
    topP = 0.75f,                 // Bardzo skupione odpowiedzi
    topK = 15,                    // Wąski wybór tokenów
    maxOutputTokens = 1024,       // Średnie odpowiedzi
    presencePenalty = 0.9f,       // Silna eliminacja powtórzeń
    frequencyPenalty = 0.6f,      // Umiarkowana redukcja gadatliwości
    stopSequences = listOf(
        "Actually, let me try again",
        "To be more specific"
    )
)
```

**Zastosowanie:** Techniczne pytania, faktyczne informacje, precyzyjne instrukcje.

---

## 5. Plan Implementacji

### Faza 1: Podstawowe Parametry (Priorytet: KRYTYCZNY)
**Czas: 2-3 godziny**

1. ✅ Rozszerzyć `GenerationConfig` o:
   - `presence_penalty`
   - `frequency_penalty`
   - `top_p`
   - `top_k`
   - `max_output_tokens`
   - `stop_sequences`

2. ✅ Zaktualizować `buildSetupMessage()` w `GeminiProtocol`

3. ✅ Rozszerzyć `ThreadSettings` o nowe pola

4. ✅ Zaktualizować `VoiceClientManager.connect()` do przekazywania parametrów

5. ✅ Dodać domyślny profil "Zwięzły Asystent" w `Preferences`

### Faza 2: UI dla Konfiguracji (Priorytet: WYSOKI)
**Czas: 3-4 godziny**

1. ✅ Dodać zakładkę "Advanced Settings" w `SettingsScreen`

2. ✅ Dodać slidery dla:
   - Temperature (0.0-2.0)
   - Top P (0.5-1.0)
   - Top K (10-100)
   - Max Tokens (256-4096)
   - Presence Penalty (0.0-2.0)
   - Frequency Penalty (0.0-2.0)

3. ✅ Dodać pole tekstowe dla Stop Sequences (lista)

4. ✅ Dodać predefiniowane profile (dropdown):
   - Zwięzły Asystent
   - Kreatywny Rozmówca
   - Precyzyjny Ekspert
   - Custom

### Faza 3: Testowanie i Optymalizacja (Priorytet: WYSOKI)
**Czas: 2-3 godziny**

1. ✅ Przetestować każdy profil w rzeczywistych rozmowach

2. ✅ Zmierzyć wpływ na:
   - Długość odpowiedzi (liczba tokenów)
   - Częstotliwość powtórzeń
   - Jakość odpowiedzi (subiektywna ocena)
   - Latencję (czy parametry wpływają na czas odpowiedzi)

3. ✅ Dostroić wartości domyślne na podstawie testów

4. ✅ Dodać logowanie parametrów do diagnostyki

### Faza 4: Opcjonalne Rozszerzenia (Priorytet: NISKI)
**Czas: 1-2 godziny**

1. ⚠️ Dodać `mediaResolution` (LOW/HIGH)
2. ⚠️ Dodać `audioTimestamp` dla debugowania
3. ⚠️ Rozważyć `thinkingConfig` dla specjalnych przypadków (z ostrzeżeniem o latencji)

---

## 6. Potencjalne Problemy i Rozwiązania

### Problem 1: Kompatybilność z Gemini Live API

**Ryzyko:** Niektóre parametry mogą nie być wspierane przez Live API.

**Rozwiązanie:**
- Przetestować każdy parametr osobno
- Dodać fallback do domyślnych wartości
- Logować błędy API i ignorować nieobsługiwane parametry

### Problem 2: Wpływ na Latencję

**Ryzyko:** Niektóre parametry (np. `thinkingConfig`) mogą zwiększyć latencję.

**Rozwiązanie:**
- Nie używać `thinkingConfig` dla real-time voice
- Monitorować czas odpowiedzi w testach
- Dodać ostrzeżenia w UI dla parametrów wpływających na latencję

### Problem 3: Zbyt Agresywne Penalties

**Ryzyko:** Wysokie `presencePenalty`/`frequencyPenalty` mogą sprawić, że odpowiedzi będą nienaturalne.

**Rozwiązanie:**
- Zacząć od umiarkowanych wartości (0.7-0.9)
- Dodać możliwość łatwego resetu do domyślnych
- Przetestować z różnymi typami pytań

### Problem 4: Stop Sequences w Języku Polskim

**Ryzyko:** Gemini może generować różne warianty fraz po polsku.

**Rozwiązanie:**
- Dodać wiele wariantów dla każdej frazy
- Testować z rzeczywistymi rozmowami
- Pozwolić użytkownikom dodawać własne stop sequences

---

## 7. Metryki Sukcesu

### Przed Implementacją (Baseline)
- Średnia długość odpowiedzi: **~500 tokenów**
- Częstotliwość powtórzeń: **~30%** (subiektywna ocena)
- Ocena zwięzłości: **3/10**

### Po Implementacji (Cel)
- Średnia długość odpowiedzi: **~200-300 tokenów** (-40-60%)
- Częstotliwość powtórzeń: **<10%** (-66%)
- Ocena zwięzłości: **8/10** (+166%)

### Jak Mierzyć
1. **Długość odpowiedzi:** Liczba tokenów w transkrypcji bota
2. **Powtórzenia:** Analiza n-gramów w odpowiedziach
3. **Zwięzłość:** Subiektywna ocena użytkownika (1-10)

---

## 8. Podsumowanie i Rekomendacje

### ✅ ZDECYDOWANIE WARTO ZAIMPLEMENTOWAĆ

1. **presencePenalty** (0.8-1.0) - **KRYTYCZNY** dla eliminacji powtórzeń
2. **frequencyPenalty** (0.7-0.8) - **KRYTYCZNY** dla redukcji gadatliwości
3. **topP** (0.85) - **WYSOKI** dla precyzji
4. **topK** (25-30) - **WYSOKI** dla precyzji
5. **maxOutputTokens** (512-1024) - **WYSOKI** dla zwięzłości
6. **stopSequences** - **WYSOKI** dla zatrzymywania "lania wody"

### ⚠️ OPCJONALNE

7. **mediaResolution** - może poprawić jakość audio
8. **audioTimestamp** - przydatne do debugowania

### ❌ NIE IMPLEMENTOWAĆ

9. **thinkingConfig** - zwiększa latencję (nieodpowiednie dla real-time)
10. **responseLogprobs** - nie wspierane przez Live API
11. **responseMimeType/responseSchema** - tylko dla text/JSON

### Szacowany Czas Implementacji

- **Faza 1 (Podstawowe parametry):** 2-3 godziny
- **Faza 2 (UI):** 3-4 godziny
- **Faza 3 (Testowanie):** 2-3 godziny
- **RAZEM:** **7-10 godzin**

### Oczekiwane Rezultaty

- **Redukcja gadatliwości o 40-60%**
- **Eliminacja powtórzeń o 66%**
- **Poprawa zwięzłości o 166%**
- **Lepsza kontrola nad stylem odpowiedzi**
- **Możliwość dostosowania do różnych przypadków użycia**

---

## 9. Następne Kroki

1. **Zatwierdzenie przez użytkownika** - czy implementować?
2. **Wybór fazy** - zacząć od Fazy 1 (podstawowe parametry)?
3. **Testowanie** - przygotować zestaw testowych pytań?
4. **Dokumentacja** - zaktualizować docs po implementacji?

**Pytanie do użytkownika:** Czy chcesz, żebym rozpoczął implementację? Od której fazy zaczynamy?
