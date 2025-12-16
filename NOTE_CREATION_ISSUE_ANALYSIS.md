# Analiza problemu: Notatki bez badania Perplexity

## Problem

Po długiej rozmowie, gdy użytkownik prosi o "głębokie wyszukiwanie i notatkę", notatka jest krótka i ogólna - zawiera tylko informacje z kontekstu rozmowy, bez wyników badania przez Perplexity.

## Możliwe przyczyny

### 1. Reasoning Agent pomija wywołanie Perplexity

**Symptom:** Model (DeepSeek/Claude) decyduje, że ma wystarczający kontekst z transkryptu i tworzy notatkę bez wcześniejszego badania.

**Dlaczego to się dzieje:**
- Model ma autonomię w wyborze akcji
- Jeśli kontekst wydaje się wystarczający, może pominąć `search_perplexity`
- Bezpośrednio wykonuje `create_note` z informacjami z transkryptu

**Kod odpowiedzialny:**
```kotlin
// ReasoningWorker.kt:516-520
"create_note" -> {
    val title = parameters["title"]?.jsonPrimitive?.content ?: ""
    val content = parameters["content"]?.jsonPrimitive?.content ?: ""
    ReasoningAction.SaveNote(title, content, false)
}
```

### 2. Gemini Live ma dostęp do narzędzia notatek (NIE POWINIEN)

**Symptom:** Gemini Live sam tworzy notatkę zamiast delegować do Reasoning Agent.

**Sprawdzenie:**
```kotlin
// SystemPrompts.kt:221-224
**YOU DO NOT HAVE THESE TOOLS:**
- ❌ `create_note` - DOES NOT EXIST
- ❌ `save_note` - DOES NOT EXIST
```

Gemini Live **nie powinien** mieć dostępu do `create_note`, ale warto sprawdzić czy faktycznie nie ma.

### 3. Reasoning Agent używa tylko kontekstu z injection

**Symptom:** Zamiast robić nowe badanie, Reasoning Agent używa wcześniej wstrzykniętych wyników badań.

**Dlaczego to się dzieje:**
- Jeśli wcześniej było badanie, jego wyniki są w `pendingInsight`
- Reasoning Agent widzi te wyniki w kontekście
- Może zdecydować, że nie potrzebuje nowego badania

## Diagnostyka

### Krok 1: Sprawdź logi

Uruchom skrypt diagnostyczny:
```powershell
.\diagnose_note_issue.ps1
```

Następnie odtwórz problem i sprawdź logi.

### Krok 2: Sprawdź czy Perplexity został wywołany

W pliku `note_issue_perplexity.txt` szukaj:
```
PerplexityClient: Search attempt
PerplexityClient: Search successful
```

Jeśli **NIE MA** tych logów → Reasoning Agent pominął badanie.

### Krok 3: Sprawdź JSON response od OpenRouter

W pliku `note_issue_response.txt` szukaj struktury:
```json
{
  "reasoning": "...",
  "actions": [
    {
      "type": "search_perplexity",  // ← Czy to jest?
      "parameters": {...}
    },
    {
      "type": "create_note",
      "parameters": {...}
    }
  ]
}
```

Jeśli `search_perplexity` **NIE MA** w actions → Model pominął badanie.

### Krok 4: Sprawdź treść notatki

Otwórz utworzoną notatkę i sprawdź:
- Czy zawiera sekcję "Research Findings"? (z NoteEnricher)
- Czy zawiera źródła/cytowania?
- Czy zawiera szczegółowe fakty czy tylko ogólniki?

## Rozwiązania

### Rozwiązanie 1: Wymuś wywołanie Perplexity w prompcie

**Modyfikacja:** `SystemPrompts.kt` - dodaj do `reasoningAgentSystemPrompt`:

```kotlin
## CRITICAL RULES FOR NOTE CREATION

When the task involves creating a note:
1. **ALWAYS** call search_perplexity FIRST to gather detailed information
2. Use multiple search queries if the topic is complex
3. **NEVER** create a note based only on transcript context
4. The note should contain:
   - Detailed research findings from Perplexity
   - Source citations
   - Key facts with evidence
5. Only after gathering research, call create_note with enriched content

**WRONG APPROACH:**
```json
{
  "actions": [
    {"type": "create_note", "parameters": {...}}  // ❌ No research!
  ]
}
```

**CORRECT APPROACH:**
```json
{
  "actions": [
    {"type": "search_perplexity", "parameters": {"query": "..."}},
    {"type": "search_perplexity", "parameters": {"query": "..."}},  // Multiple if needed
    {"type": "create_note", "parameters": {...}}  // ✅ After research
  ]
}
```
```

### Rozwiązanie 2: Walidacja akcji przed wykonaniem

**Modyfikacja:** `ReasoningWorker.kt` - dodaj walidację:

```kotlin
private fun validateActions(
    actions: List<ReasoningAction>,
    taskDescription: String
): ValidationResult {
    // Check if task mentions "note" or "save"
    val isNoteTask = taskDescription.contains("note", ignoreCase = true) ||
                     taskDescription.contains("save", ignoreCase = true) ||
                     taskDescription.contains("zapisz", ignoreCase = true)
    
    if (isNoteTask) {
        val hasSearch = actions.any { it is ReasoningAction.SearchPerplexity }
        val hasNote = actions.any { it is ReasoningAction.SaveNote }
        
        if (hasNote && !hasSearch) {
            return ValidationResult.Invalid(
                "Note creation requires prior research. Add search_perplexity action first."
            )
        }
    }
    
    return ValidationResult.Valid
}
```

### Rozwiązanie 3: Dwuetapowy proces

**Modyfikacja:** Rozdziel zadanie na dwa etapy:

1. **Etap 1:** Reasoning Agent robi tylko badanie (search_perplexity)
2. **Etap 2:** Po otrzymaniu wyników, drugi task tworzy notatkę

```kotlin
// W ReasoningAgentManager.kt
suspend fun startResearchAndNote(
    conversationId: String,
    topic: String
) {
    // Etap 1: Badanie
    val researchTaskId = startReasoningTask(
        conversationId = conversationId,
        taskDescription = "Research topic: $topic. Use search_perplexity to gather detailed information.",
        priority = TaskPriority.HIGH
    )
    
    // Czekaj na wynik badania
    // ...
    
    // Etap 2: Notatka (z wynikami badania w kontekście)
    val noteTaskId = startReasoningTask(
        conversationId = conversationId,
        taskDescription = "Create note about: $topic. Use research results from previous task.",
        priority = TaskPriority.NORMAL
    )
}
```

### Rozwiązanie 4: Specjalizowane narzędzie "research_and_note"

**Modyfikacja:** Dodaj nowe narzędzie które wymusza sekwencję:

```kotlin
// W SystemPrompts.kt
5. **research_and_note(topic, queries)** - Research topic and create comprehensive note
   - Automatically performs multiple Perplexity searches
   - Synthesizes results into structured note
   - Guarantees research before note creation
   - Use for: "make a note about X", "research and save Y"
```

```kotlin
// W ReasoningWorker.kt
"research_and_note" -> {
    val topic = parameters["topic"]?.jsonPrimitive?.content ?: ""
    val queries = parameters["queries"]?.jsonArray?.map { 
        it.jsonPrimitive.content 
    } ?: listOf(topic)
    
    ReasoningAction.ResearchAndNote(topic, queries)
}
```

## Rekomendacja

**Najlepsze rozwiązanie:** Kombinacja **Rozwiązania 1** i **Rozwiązania 2**

1. **Rozwiązanie 1:** Wyraźnie instruuj model w prompcie
   - Łatwe do implementacji
   - Nie wymaga zmian w architekturze
   - Działa dla wszystkich modeli

2. **Rozwiązanie 2:** Walidacja jako safety net
   - Catch errors jeśli model zignoruję instrukcje
   - Może zwrócić błąd lub automatycznie dodać search_perplexity

## Następne kroki

1. ✅ Uruchom `diagnose_note_issue.ps1` i potwierdź diagnozę
2. ⏳ Zaimplementuj Rozwiązanie 1 (modyfikacja promptu)
3. ⏳ Zaimplementuj Rozwiązanie 2 (walidacja)
4. ⏳ Przetestuj z rzeczywistym przypadkiem
5. ⏳ Jeśli problem persystuje, rozważ Rozwiązanie 4

## Pytania do użytkownika

1. Czy chcesz najpierw uruchomić diagnostykę, żeby potwierdzić przyczynę?
2. Czy preferujesz rozwiązanie przez prompt (łatwiejsze) czy przez kod (bardziej niezawodne)?
3. Czy notatka powinna **zawsze** wymagać badania Perplexity, czy tylko gdy użytkownik wyraźnie prosi o "głębokie wyszukiwanie"?
