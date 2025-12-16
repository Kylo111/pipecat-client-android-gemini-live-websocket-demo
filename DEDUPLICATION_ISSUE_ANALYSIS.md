# Analiza problemu: Duplikacja raportów (In-Session vs Post-Session)

## Obserwacje z logów

### 1. Utworzono dwa raporty

**Raport 1: In-Session (podczas rozmowy)**
- Źródło: Gemini Live wywołał `start_reasoning_task`
- Task ID: `94149073-9ebd-4819-b89d-00d11cdf1eaf`
- Czas: `16:53:08`
- Task description: "Perform an in-depth analysis of Google Cloud Speech-to-Text and Microsoft Azure Cognitive Services pricing..."
- `Is report task: false` ← To jest zwykły task, NIE post-session report
- Source: **WHISPERER** (Gemini Live w trybie Whisperer)

**Raport 2: Post-Session (po zakończeniu sesji)**
- Źródło: Summary Model wykrył potrzebę raportu
- Czas: Prawdopodobnie po `16:53:08` (po zakończeniu sesji)
- Tytuł: "Post-Session Report"
- Source: **SUMMARY**

### 2. Dlaczego Post-Session jest lepszy?

**Post-Session raport:**
- Ma pełny kontekst całej rozmowy (previous + current transcript)
- Summary Model już przeanalizował całą sesję
- Wie dokładnie jakie tematy były omawiane
- Ma dostęp do Meta-Summary (zaktualizowanego po sesji)

**In-Session raport:**
- Tworzony w trakcie rozmowy
- Może nie mieć pełnego kontekstu (rozmowa jeszcze trwa)
- Bazuje na częściowym transkrypcie

## Problem: Deduplication nie zadziałał

### Dlaczego deduplication nie zapobiegł duplikacji?

#### Scenariusz 1: Timing Issue

```
Timeline:
T1: 16:53:08 - Gemini Live wywołuje start_reasoning_task
T2: 16:53:08 - TaskRegistry.createTask (source=WHISPERER, status=PENDING)
T3: 16:53:08 - ReasoningWorker starts
T4: [rozmowa trwa dalej...]
T5: [sesja kończy się]
T6: Summary Model analizuje transkrypt
T7: Summary wykrywa needs_report=true
T8: MemoryUpdateService.checkReportDeduplication()
T9: ??? - Czy sprawdził TaskRegistry?
```

**Możliwe przyczyny:**

1. **Task jeszcze PENDING**
   - Gdy Summary sprawdza deduplication, task z Live jest jeszcze PENDING
   - Ale według kodu, PENDING tasks **powinny** blokować duplikaty
   - Sprawdzenie: `TaskRegistry.checkDeduplication()` powinno znaleźć PENDING task

2. **Różne topics**
   - Live task: topics extracted from task description
   - Summary task: topics from `report_topics` field
   - Jeśli topics są różnie sformułowane, overlap może być < 70%

3. **MemoryUpdateService nie wywołał checkReportDeduplication**
   - Kod pokazuje że powinien: linia 151
   - Ale może wystąpił błąd lub warunek nie został spełniony

4. **TaskRegistry nie znalazł podobnych tasków**
   - `findSimilarTasks()` może nie znaleźć overlap > 70%
   - TopicMatcher może nie rozpoznać że to te same tematy

### Kod odpowiedzialny

**MemoryUpdateService.kt:150-153**
```kotlin
// Check deduplication if report is needed
if (memoryUpdateResult.needsReport && memoryUpdateResult.reportTopics.isNotEmpty()) {
    memoryUpdateResult = checkReportDeduplication(conversationId, memoryUpdateResult)
}
```

**TaskRegistry.kt:checkDeduplication()**
```kotlin
suspend fun checkDeduplication(
    conversationId: String,
    requestedTopics: List<String>
): DeduplicationResult {
    // Get recent tasks (within 24h window)
    val recentTasks = taskRecordDao.getRecentTasks(
        conversationId = conversationId,
        since = System.currentTimeMillis() - (DEDUPLICATION_WINDOW_HOURS * 3600 * 1000)
    )
    
    // Check overlap with each task
    for (task in recentTasks) {
        val taskTopics = parseTopics(task.topics)
        val overlap = topicMatcher.computeOverlap(requestedTopics, taskTopics)
        
        if (overlap >= SIMILARITY_THRESHOLD) {
            // Found similar task!
            return DeduplicationResult(
                shouldSkip = true,
                coveredTopics = requestedTopics,
                uncoveredTopics = emptyList(),
                coveringTasks = listOf(task),
                reason = "Task ${task.taskId} already covers topics (${task.status})"
            )
        }
    }
    
    // No overlap found
    return DeduplicationResult(shouldSkip = false, ...)
}
```

## Diagnostyka

### Co sprawdzić w logach?

1. **Czy MemoryUpdateService wywołał checkReportDeduplication?**
   ```
   Szukaj: "Checking report deduplication for topics"
   ```

2. **Jakie topics zostały porównane?**
   ```
   Live task topics: [extracted from task description]
   Summary report_topics: [from Summary Model response]
   ```

3. **Jaki był wynik computeOverlap?**
   ```
   Szukaj: "computeOverlap" lub "topic overlap"
   ```

4. **Czy TaskRegistry znalazł recent tasks?**
   ```
   Szukaj: "getRecentTasks" lub "Found X recent tasks"
   ```

### Komendy diagnostyczne

```powershell
# 1. Sprawdź czy checkReportDeduplication został wywołany
Get-Content note_issue_full.txt | Select-String -Pattern "Checking report deduplication|checkReportDeduplication"

# 2. Sprawdź topics z obu tasków
Get-Content note_issue_full.txt | Select-String -Pattern "Extracted topics|report_topics|reportTopics"

# 3. Sprawdź wynik deduplication
Get-Content note_issue_full.txt | Select-String -Pattern "shouldSkip|Deduplication check result|overlap"

# 4. Sprawdź TaskRegistry operations
Get-Content note_issue_full.txt | Select-String -Pattern "TaskRegistry|getRecentTasks|findSimilarTasks"
```

## Możliwe przyczyny (ranking)

### 1. Topics nie matchują (najbardziej prawdopodobne)

**Symptom:**
- Live task: topics extracted z task description przez `TopicMatcher.extractTopics()`
- Summary task: topics z `report_topics` field (określone przez Summary Model)
- Jeśli są różnie sformułowane, overlap < 70%

**Przykład:**
```
Live topics: ["perform", "indepth", "analysis", "google", "cloud", "speechtotext", ...]
Summary topics: ["Google Cloud pricing", "Azure pricing", "Polish TTS"]

Overlap: może być < 70% bo są różnie sformułowane
```

**Rozwiązanie:**
- Popraw `TopicMatcher.extractTopics()` - lepsze wyodrębnianie tematów
- Dodaj więcej synonimów do `TopicMatcher.SYNONYMS`
- Użyj semantic similarity (embeddings) zamiast prostego string matching

### 2. MemoryUpdateService nie wywołał checkReportDeduplication

**Symptom:**
- Brak logów "Checking report deduplication"
- Warunek `if (memoryUpdateResult.needsReport && memoryUpdateResult.reportTopics.isNotEmpty())` nie został spełniony

**Możliwe przyczyny:**
- `needsReport` było `false` (ale wtedy raport by nie powstał)
- `reportTopics` było puste (ale wtedy raport by nie powstał)
- Wystąpił exception przed wywołaniem

**Rozwiązanie:**
- Dodaj więcej logów w MemoryUpdateService
- Sprawdź czy warunek jest zawsze spełniony

### 3. TaskRegistry.getRecentTasks() nie zwrócił Live task

**Symptom:**
- Live task został utworzony, ale nie został znaleziony przez `getRecentTasks()`

**Możliwe przyczyny:**
- Database transaction nie został scommitowany na czas
- Query filtruje po złym conversationId
- Timing issue - task jeszcze nie w bazie gdy Summary sprawdza

**Rozwiązanie:**
- Dodaj `@Transaction` do `createTask()`
- Dodaj delay przed sprawdzeniem deduplication
- Użyj in-memory cache zamiast tylko database

### 4. SIMILARITY_THRESHOLD za wysoki (70%)

**Symptom:**
- Topics są podobne, ale overlap < 70%
- Deduplication nie zadziała nawet dla bardzo podobnych tasków

**Rozwiązanie:**
- Obniż threshold do 50-60%
- Lub użyj różnych thresholds dla różnych źródeł (LIVE vs SUMMARY)

## Rekomendowane rozwiązania

### Rozwiązanie 1: Popraw topic extraction i matching (PRIORYTET 1)

**Problem:** Topics z Live i Summary są różnie sformułowane.

**Implementacja:**

1. **Lepsze wyodrębnianie tematów:**
```kotlin
// TopicMatcher.kt
fun extractTopics(text: String): List<String> {
    // Zamiast prostego split, użyj:
    // 1. Named Entity Recognition (NER) - wykryj nazwy firm, produktów
    // 2. Noun phrase extraction - wyodrębnij frazy rzeczownikowe
    // 3. Keyword extraction - TF-IDF lub RAKE
    
    val entities = extractNamedEntities(text) // "Google Cloud", "Azure"
    val phrases = extractNounPhrases(text) // "speech-to-text", "pricing"
    val keywords = extractKeywords(text) // "analysis", "Polish"
    
    return (entities + phrases + keywords).distinct()
}
```

2. **Więcej synonimów:**
```kotlin
val SYNONYMS = mapOf(
    "google cloud" to listOf("gcp", "google cloud platform"),
    "azure" to listOf("microsoft azure", "azure cognitive services"),
    "speech-to-text" to listOf("stt", "speech recognition", "transcription"),
    "text-to-speech" to listOf("tts", "speech synthesis"),
    "pricing" to listOf("cost", "price", "billing", "fees")
)
```

3. **Semantic similarity:**
```kotlin
// Użyj embeddings dla lepszego matchingu
fun computeSemanticSimilarity(topic1: String, topic2: String): Float {
    // Opcja 1: Użyj lokalnego modelu (np. sentence-transformers)
    // Opcja 2: Użyj API (OpenAI embeddings, Cohere)
    // Opcja 3: Prosty cosine similarity na word vectors
}
```

### Rozwiązanie 2: In-memory task cache (PRIORYTET 2)

**Problem:** Database query może nie zwrócić świeżo utworzonego taska.

**Implementacja:**
```kotlin
// TaskRegistry.kt
class TaskRegistry(
    private val taskDao: TaskRecordDao,
    private val topicMatcher: TopicMatcher
) {
    // In-memory cache for recent tasks
    private val recentTasksCache = mutableMapOf<String, MutableList<TaskRecord>>()
    private val cacheLock = Mutex()
    
    suspend fun createTask(...): TaskRecord {
        val task = TaskRecord(...)
        
        // Save to database
        taskDao.insert(task)
        
        // Also add to cache
        cacheLock.withLock {
            val conversationTasks = recentTasksCache.getOrPut(conversationId) { mutableListOf() }
            conversationTasks.add(task)
        }
        
        return task
    }
    
    suspend fun checkDeduplication(...): DeduplicationResult {
        // Check cache first, then database
        val cachedTasks = cacheLock.withLock {
            recentTasksCache[conversationId]?.toList() ?: emptyList()
        }
        
        val dbTasks = taskDao.getRecentTasks(conversationId, since)
        
        // Merge and deduplicate
        val allTasks = (cachedTasks + dbTasks).distinctBy { it.taskId }
        
        // Check overlap...
    }
}
```

### Rozwiązanie 3: Obniż threshold lub użyj adaptive threshold (PRIORYTET 3)

**Problem:** 70% może być za wysoko dla różnie sformułowanych topics.

**Implementacja:**
```kotlin
// TaskRegistry.kt
companion object {
    const val SIMILARITY_THRESHOLD_STRICT = 0.7f // For exact matches
    const val SIMILARITY_THRESHOLD_RELAXED = 0.5f // For cross-source matches
}

suspend fun checkDeduplication(...): DeduplicationResult {
    for (task in recentTasks) {
        val taskTopics = parseTopics(task.topics)
        val overlap = topicMatcher.computeOverlap(requestedTopics, taskTopics)
        
        // Use different thresholds based on source
        val threshold = if (task.source == TaskSource.WHISPERER) {
            // More relaxed for Live tasks (they might be ongoing)
            SIMILARITY_THRESHOLD_RELAXED
        } else {
            SIMILARITY_THRESHOLD_STRICT
        }
        
        if (overlap >= threshold) {
            // Found similar task!
            return DeduplicationResult(shouldSkip = true, ...)
        }
    }
}
```

### Rozwiązanie 4: Explicit coordination flag (PRIORYTET 4)

**Problem:** Może być trudno wykryć overlap przez topics.

**Implementacja:**
```kotlin
// Gdy Live tworzy task, ustaw flagę "blocks_post_session_report"
// Summary sprawdza tę flagę zamiast (lub oprócz) topic overlap

// TaskRecord.kt
data class TaskRecord(
    ...
    val blocksPostSessionReport: Boolean = false // NEW
)

// ReasoningAgentManager.kt
suspend fun startReasoningTask(...) {
    val blocksPostSession = taskDescription.contains("report", ignoreCase = true) ||
                           taskDescription.contains("analysis", ignoreCase = true)
    
    taskRegistry.createTask(
        ...
        blocksPostSessionReport = blocksPostSession
    )
}

// TaskRegistry.kt
suspend fun checkDeduplication(...): DeduplicationResult {
    // Check if any recent task blocks post-session reports
    val blockingTask = recentTasks.find { it.blocksPostSessionReport }
    
    if (blockingTask != null) {
        return DeduplicationResult(
            shouldSkip = true,
            reason = "Task ${blockingTask.taskId} blocks post-session reports"
        )
    }
    
    // Continue with topic-based deduplication...
}
```

## Następne kroki (w kolejności)

1. ✅ **Zbadaj logi** - potwierdź która przyczyna jest faktyczna
   ```powershell
   # Uruchom komendy diagnostyczne z sekcji "Diagnostyka"
   ```

2. ⏳ **Zaimplementuj Rozwiązanie 1** - popraw topic matching
   - Najpierw: dodaj więcej logów do `TopicMatcher.computeOverlap()`
   - Następnie: popraw `extractTopics()` i dodaj synonimy

3. ⏳ **Zaimplementuj Rozwiązanie 2** - in-memory cache
   - Zapewni że świeżo utworzone taski są widoczne

4. ⏳ **Przetestuj** - odtwórz scenariusz i sprawdź czy deduplication działa

5. ⏳ **Jeśli problem persystuje** - rozważ Rozwiązanie 3 lub 4

## Pytania do użytkownika

1. Czy mam uruchomić komendy diagnostyczne na istniejących logach?
2. Czy chcesz najpierw zobaczyć dokładnie jakie topics były porównywane?
3. Czy preferujesz rozwiązanie przez lepszy matching (Rozw. 1) czy przez cache (Rozw. 2)?
4. Czy Post-Session raport powinien **zawsze** być blokowany jeśli Live już zrobił raport, niezależnie od topics?
