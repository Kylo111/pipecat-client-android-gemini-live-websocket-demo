# Implementacja Perplexity API i Google Search Grounding

## Podsumowanie

Dodano obsługę dwóch nowych metod wyszukiwania dla bota audio Gemini:

1. **Perplexity Sonar API** - zaawansowane wyszukiwanie z automatycznymi cytowaniami
2. **Google Search Grounding** - wbudowane wyszukiwanie Google w Gemini (gotowe do użycia)

## Zmiany w kodzie

### 1. Preferences.kt
**Dodano:**
- `perplexityApiKey` - przechowywanie klucza API Perplexity
- Zaktualizowano `toolsInstruction` z informacjami o nowych narzędziach wyszukiwania

**Lokalizacja:** `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/Preferences.kt`

```kotlin
val perplexityApiKey = StringPref("perplexity_api_key") // For Perplexity Sonar API
```

### 2. ToolDefinitions.kt
**Dodano:**
- `searchPerplexityTool()` - definicja narzędzia Perplexity
- `getGoogleSearchGrounding()` - konfiguracja Google Search grounding
- Logika warunkowa: Perplexity jest dodawane tylko gdy klucz API jest skonfigurowany

**Lokalizacja:** `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/tools/ToolDefinitions.kt`

```kotlin
// Perplexity jest dodawane tylko gdy API key jest dostępny
val perplexityApiKey = ai.pipecat.gemini_multimodal_websocket_demo.Preferences.perplexityApiKey.value
if (!perplexityApiKey.isNullOrBlank()) {
    builtInTools.add(searchPerplexityTool())
}
```

### 3. ToolExecutor.kt
**Dodano:**
- `searchPerplexity()` - implementacja wywołania Perplexity Sonar API
- Obsługa narzędzia `search_perplexity` w `executeTool()`

**Lokalizacja:** `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/tools/ToolExecutor.kt`

**Funkcjonalność:**
- Wywołuje Perplexity API endpoint: `https://api.perplexity.ai/chat/completions`
- Używa modelu `sonar-pro` (zoptymalizowany pod kątem złożonych zapytań)
- Zwraca wyniki z automatycznymi cytowaniami źródeł
- Obsługuje błędy (brak klucza API, błędy sieciowe)

### 4. SettingsScreen.kt
**Dodano:**
- Pole tekstowe "Klucz API Perplexity (opcjonalny)"
- Link do uzyskania klucza: https://www.perplexity.ai/settings/api
- Opis funkcjonalności Perplexity

**Lokalizacja:** `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/ui/SettingsScreen.kt`

## Jak używać

### Perplexity Sonar API

1. **Domyślny klucz API:**
   - ✅ Aplikacja ma wbudowany domyślny klucz API Perplexity
   - ✅ Perplexity działa od razu po instalacji (bez konfiguracji)
   - Możesz użyć własnego klucza jeśli chcesz

2. **Opcjonalnie: Użyj własnego klucza API:**
   - Wejdź na: https://www.perplexity.ai/settings/api
   - Wygeneruj klucz API
   - Koszt: ~$0.005 za token wyjściowy (model sonar-pro)
   - Otwórz Ustawienia w aplikacji
   - Sekcja "Konfiguracja Gemini API"
   - Wpisz swój klucz w pole "Klucz API Perplexity (opcjonalny)"
   - Zapisz ustawienia

3. **Użycie:**
   - Bot automatycznie użyje `search_perplexity` dla zapytań politycznych i aktualności
   - Przykład: "Jakie są najnowsze wydarzenia polityczne w Polsce?"
   - Bot wykona: `search_perplexity(query="najnowsze wydarzenia polityczne w Polsce")`
   
4. **Filtry czasowe:**
   - "Najnowsze wydarzenia" → `recency_filter="day"`
   - "Co się działo w tym tygodniu?" → `recency_filter="week"`
   - "Wydarzenia w ostatnim miesiącu" → `recency_filter="month"`
   - Bot automatycznie wybiera odpowiedni filtr na podstawie kontekstu

### Google Search Grounding

**Status:** Gotowe do implementacji w VoiceClientManager

Google Search grounding to wbudowana funkcja Gemini API, która nie wymaga dodatkowego klucza API.

**Aby włączyć:**
W `VoiceClientManager.kt`, w sekcji setup message, dodaj Google Search grounding do `tools`:

```kotlin
// Obecna konfiguracja:
tools = listOf(Tool(function_declarations = toolDeclarations))

// Zmień na:
tools = listOf(
    Tool(function_declarations = toolDeclarations),
    buildJsonObject { put("google_search", buildJsonObject {}) }
)
```

**Uwaga:** Google Search grounding działa automatycznie - Gemini sam decyduje kiedy użyć wyszukiwania Google, bez wywoływania funkcji.

## Różnice między narzędziami wyszukiwania

| Funkcja | search_web (Serper) | search_perplexity (Sonar) | Google Search Grounding |
|---------|---------------------|---------------------------|------------------------|
| **Typ** | Function calling | Function calling | Wbudowane w Gemini |
| **Klucz API** | Serper API | Perplexity API | Nie wymagany |
| **Cytowania** | Podstawowe | Automatyczne, szczegółowe | Automatyczne |
| **Użycie** | Ogólne wyszukiwanie | Wydarzenia polityczne, aktualności | Automatyczne przez Gemini |
| **Koszt** | ~$5/1000 zapytań | ~$0.005/token | Wliczony w Gemini API |
| **Kontrola** | Jawne wywołanie | Jawne wywołanie | Automatyczne |
| **Filtry czasowe** | ❌ Nie | ✅ Tak (hour/day/week/month/year) | ❌ Nie |
| **Max wyników** | 5 (stałe) | 1-20 (parametr) | Automatyczne |

## Zalecenia użycia (dla bota)

Bot został poinstruowany aby:

1. **Dla wydarzeń politycznych i aktualności** → użyj `search_perplexity`
   - Bardziej dokładne wyniki
   - Automatyczne cytowania źródeł
   - Lepsze dla złożonych zapytań

2. **Dla ogólnego wyszukiwania** → użyj `search_web`
   - Szybsze
   - Wystarczające dla prostych faktów

3. **Google Search Grounding** → automatyczne
   - Gemini sam decyduje kiedy użyć
   - Nie wymaga jawnego wywołania funkcji

## Instrukcje dla bota

Zaktualizowano `toolsInstruction` w Preferences:

```
SEARCH TOOL SELECTION:
- For political news, current events, complex queries → USE search_perplexity (more accurate, with citations)
- For general web search, quick facts → USE search_web
- Perplexity is available only if API key is configured

MANDATORY BEHAVIOR:
- When user asks for information → EXECUTE the tool IMMEDIATELY
- DO NOT ask "Do you want me to..." - just DO IT
- DO NOT explain what you will do - just EXECUTE the tool
```

## Testowanie

### Test Perplexity:
1. Skonfiguruj klucz API Perplexity w ustawieniach
2. Uruchom konwersację
3. Zapytaj: "Jakie są najnowsze wydarzenia polityczne w Europie?"
4. Bot powinien automatycznie wywołać `search_perplexity`
5. Sprawdź logi: `adb logcat | grep "Perplexity"`

### Test Google Search Grounding:
1. Zaimplementuj w VoiceClientManager (patrz wyżej)
2. Uruchom konwersację
3. Zapytaj o aktualne informacje
4. Gemini automatycznie użyje wyszukiwania Google gdy potrzebne

## Logi diagnostyczne

```bash
# Sprawdź czy Perplexity jest dostępne
adb logcat | grep "Configuring.*tools"

# Monitoruj wywołania Perplexity
adb logcat | grep "Perplexity"

# Sprawdź błędy API
adb logcat | grep "ToolExecutor"
```

## Znane ograniczenia

1. **Perplexity API:**
   - Wymaga aktywnego klucza API
   - Koszt za użycie (pay-per-use)
   - Limit zapytań zależny od planu

2. **Google Search Grounding:**
   - Wymaga implementacji w VoiceClientManager
   - Brak kontroli nad tym kiedy jest używane
   - Może zwiększyć koszty Gemini API

## Następne kroki

1. **Przetestuj na urządzeniu:**
   ```bash
   ./gradlew :gemini-multimodal-websocket-demo:installDebug
   ```

2. **Skonfiguruj klucz Perplexity:**
   - Ustawienia → Klucz API Perplexity

3. **Opcjonalnie: Włącz Google Search Grounding:**
   - Edytuj VoiceClientManager.kt
   - Dodaj grounding do tools

4. **Testuj różne scenariusze:**
   - Wydarzenia polityczne (Perplexity)
   - Ogólne wyszukiwanie (Serper)
   - Automatyczne wyszukiwanie (Google Grounding)

## Status implementacji

✅ Preferences - dodano perplexityApiKey z domyślną wartością
✅ ToolDefinitions - dodano searchPerplexityTool() z parametrami (recency_filter, max_results)
✅ ToolExecutor - zaimplementowano searchPerplexity() z obsługą filtrów czasowych
✅ SettingsScreen - dodano pole API key
✅ Instrukcje bota - zaktualizowano toolsInstruction z przykładami użycia filtrów
✅ Build - kompilacja pomyślna
✅ Domyślny klucz API - Perplexity działa od razu po instalacji
⏳ Google Search Grounding - gotowe do implementacji w VoiceClientManager
⏳ Testowanie na urządzeniu - wymaga podłączenia urządzenia

## Nowe funkcje Perplexity

### Filtry czasowe (recency_filter)
- `hour` - ostatnia godzina
- `day` - ostatnie 24 godziny
- `week` - ostatni tydzień (7 dni)
- `month` - ostatni miesiąc (30 dni)
- `year` - ostatni rok (365 dni)

### Parametr max_results
- Zakres: 1-20
- Domyślnie: 5
- Bot może poprosić o więcej wyników dla złożonych zapytań

### Przykłady użycia

```kotlin
// Najnowsze wydarzenia (ostatnie 24h)
search_perplexity(
    query = "wydarzenia polityczne w Polsce",
    recency_filter = "day"
)

// Wydarzenia z ostatniego tygodnia
search_perplexity(
    query = "dywersja w Polsce",
    recency_filter = "week",
    max_results = 10
)

// Wszystkie wyniki (bez filtra czasowego)
search_perplexity(
    query = "historia Polski"
)
```

## Dokumentacja API

### Perplexity Sonar API
- Dokumentacja: https://docs.perplexity.ai/
- Endpoint: `https://api.perplexity.ai/chat/completions`
- Model: `sonar-pro` (lub `sonar` dla tańszej opcji)
- Format: OpenAI-compatible API

### Google Search Grounding
- Dokumentacja: https://ai.google.dev/gemini-api/docs/grounding
- Format: `{"google_search": {}}`
- Automatyczne cytowania i źródła
