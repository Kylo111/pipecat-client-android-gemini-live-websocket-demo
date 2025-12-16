# Requirements Document: Reasoning Agent

## Introduction

Reasoning Agent to asynchroniczny agent rozumujący działający w tle, przeznaczony do:
1. **Głębokich analiz w trakcie rozmowy** - wyzwalany przez Gemini Live gdy potrzebna jest zewnętrzna wiedza lub zapis
2. **Generowania raportów po sesji** - wyzwalany przez model podsumowujący gdy wykryje potrzebę głębszej analizy
3. **Tryb "Whisperer"** - automatyczne uruchamianie gdy Gemini Live wykryje brak wiedzy lub frustrację użytkownika

**Kluczowa zasada:** Reasoning Agent przejmuje od Gemini Live wszystkie "ciężkie" operacje:
- Zapisywanie do notatnika
- Zapisywanie do schowka
- Wyszukiwanie w Perplexity (głębokie)
- Wysyłanie na Telegram/dysk

**Gemini Live zachowuje:**
- Google Grounding (szybkie wyszukiwania real-time)
- Narzędzia wymagające natychmiastowej odpowiedzi (pogoda, czas, lokalizacja, nawigacja)
- Media control (Spotify, etc.)

**Uproszczony interfejs:** Reasoning Agent sam rozpoznaje intencję na podstawie pełnego kontekstu. Gemini Live przekazuje tylko:
- `task_description` - naturalne polecenie opisujące zadanie
- `priority` - priorytet zadania (LOW/NORMAL/HIGH)

**Fire-and-Forget:** Gemini Live NIE czeka na wynik. Odpala Reasoner i kontynuuje rozmowę. Gdy wynik jest gotowy, zostaje wstrzyknięty jako context.

## Glossary

- **Reasoning_Agent**: Asynchroniczny agent rozumujący używający modeli wysokiej inteligencji (DeepSeek, Claude) przez OpenRouter
- **Full_Context**: Pełny kontekst przekazywany do Reasoning Agent (szczegóły poniżej)
- **Hidden_Prompt**: Cichy kontekst wstrzykiwany do Gemini Live bez widoczności dla użytkownika
- **Context_Injection**: Mechanizm wstrzykiwania wiedzy z Reasoning Agent do Gemini Live
- **Perplexity_Search**: Głębokie wyszukiwanie z cytowaniami przez Perplexity Sonar API
- **Google_Grounding**: Szybkie wyszukiwanie przez Google Search API (pozostaje w Gemini Live)
- **Summary_Model**: Gemini 2.5 Flash używany do podsumowań sesji i aktualizacji kart pamięci
- **Report_Trigger**: Mechanizm wykrywania potrzeby raportu przez model podsumowujący
- **Global_User_Card**: Trwałe fakty o użytkowniku (preferencje, języki, tło zawodowe)
- **Local_Conversation_Card**: Stan specyficzny dla konwersacji (temat, cele, ustalenia, pendingInsight)
- **Meta_Summary**: Narracyjna historia konwersacji (do 700 słów) - ŹRÓDŁO PRAWDY o kontekście roli
- **Previous_Session_Transcript**: Transkrypt poprzedniej (zakończonej) sesji
- **Current_Session_Transcript**: Transkrypt bieżącej sesji (in-memory lub właśnie zakończonej)
- **Snapshot_File**: Plik JSON w cache zawierający transkrypty (obejście limitu 10KB WorkManager)
- **Pending_Insight**: Wiedza z Reasoner zapisana gdy sesja była zamknięta, do wykorzystania w następnej sesji
- **Whisperer_Mode**: Tryb automatycznego uruchamiania Reasoner gdy Gemini Live wykryje brak wiedzy
- **Orphan_Result**: Wynik Reasoner gdy sesja już nie istnieje

## Context Structure (CRITICAL)

### Co Reasoning Agent DOSTAJE:

| Komponent | Źródło | Opis |
|-----------|--------|------|
| **Reasoning Agent System Prompt** | SystemPrompts.reasoningAgentSystemPrompt | Instrukcje dla Reasoning Agent |
| **Global User Card** | GlobalMemoryDataStore | Trwałe fakty o użytkowniku |
| **Local Conversation Card** | ConversationRepository | Stan tej konwersacji (w tym pendingInsight) |
| **Meta-Summary** | ConversationRepository | Historia narracyjna - ŹRÓDŁO PRAWDY o roli/kontekście |
| **Previous Session Transcript** | Snapshot File | Transkrypt POPRZEDNIEJ sesji (przekazany explicite) |
| **Current Session Transcript** | Snapshot File | Transkrypt BIEŻĄCEJ/zakończonej sesji (przekazany explicite) |

### Co Reasoning Agent NIE DOSTAJE:

| Komponent | Powód wykluczenia |
|-----------|-------------------|
| **Global Prompt dla Gemini Live** | Zabrudziłby kontekst, agent mógłby pomyśleć że instrukcje są do niego |
| **Tools Instruction dla Gemini Live** | Nie dotyczy Reasoning Agent |
| **Conversation Persona (System Prompt)** | **USUNIĘTA** - Ryzyko prompt injection; Meta-Summary zawiera wystarczający kontekst |

### Dlaczego usunęliśmy Personę (CRITICAL):

1. **Ryzyko prompt injection** - złośliwa Persona mogłaby manipulować Reasoning Agent
2. **Meta-Summary** jest generowane przez zaufany model (Summary Agent)
3. Meta-Summary zawiera esencję narracji i kontekst roli
4. Reasoner (Claude/DeepSeek) wywnioskuje kontekst z transkryptów i meta-summary
5. Eliminuje wektor ataku przez user-generated content

### Snapshot File Pattern (obejście limitu 10KB WorkManager):

**Problem:** WorkManager ma twardy limit 10KB na `Data`. Dwa transkrypty z 20-minutowej rozmowy przekroczą ten limit.

**Rozwiązanie:**
1. `ReasoningAgentManager` zapisuje transkrypty do pliku JSON w `cacheDir/reasoning-snapshots/`
2. Przekazuje do WorkManager tylko `snapshot_file_path`
3. `ReasoningWorker` odczytuje plik, przetwarza, usuwa po zakończeniu

```
cacheDir/
└── reasoning-snapshots/
    └── task_{UUID}.json
```

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        PRZYPADEK 1: W TRAKCIE ROZMOWY               │
│                        (Fire-and-Forget + Whisperer Mode)           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  User Audio → Gemini Live → [Wykrywa potrzebę LUB brak wiedzy]     │
│                    │                                                │
│                    ▼                                                │
│         start_reasoning_task(task_description, priority)           │
│         [Fire-and-Forget - NIE czeka na wynik!]                    │
│                    │                                                │
│                    ▼                                                │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │         ReasoningAgentManager                                │   │
│  │                                                              │   │
│  │  1. Pobiera previousTranscript z getRecentSessions(2)[1]    │   │
│  │  2. Pobiera currentTranscript z SessionManager (in-memory)  │   │
│  │  3. Zapisuje do Snapshot File (JSON w cacheDir)             │   │
│  │  4. Przekazuje snapshot_file_path do WorkManager            │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                    │                                                │
│                    ▼                                                │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │              REASONING WORKER (WorkManager Expedited)        │   │
│  │                                                              │   │
│  │  1. Odczytuje Snapshot File                                  │   │
│  │  2. Buduje Full Context (karty, meta-summary, transkrypty)  │   │
│  │  3. NIE dostaje: Gemini Live prompts, Persona               │   │
│  │  4. Wywołuje OpenRouter + Perplexity                        │   │
│  │  5. Po zakończeniu: ContextInjector                         │   │
│  │  6. Usuwa Snapshot File (cleanup)                           │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                    │                                                │
│                    ▼                                                │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │              CONTEXT INJECTOR                                │   │
│  │                                                              │   │
│  │  if (sessionActive):                                         │   │
│  │    → Wstrzyknij do Gemini Live (hidden prompt)              │   │
│  │  else (Orphan Result):                                       │   │
│  │    → Zapisz jako pendingInsight w LocalConversationCard     │   │
│  │    → Przy następnej sesji asystent zobaczy tę wiedzę        │   │
│  │                                                              │   │
│  │  if (Worker failed after retries):                          │   │
│  │    → Wstrzyknij info o błędzie (Negative Feedback)          │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                        PRZYPADEK 2: PO SESJI                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Sesja kończy się → Summary Model analizuje transkrypcję           │
│                    │                                                │
│                    ▼                                                │
│  WAŻNE: Summary PRZED wywołaniem Reasoning Agent musi:             │
│  1. Pobrać previousSessionTranscript z DB                          │
│  2. Zachować currentSessionTranscript (właśnie zakończony)         │
│  3. Zapisać do Snapshot File                                       │
│  4. Przekazać snapshot_file_path do ReasoningWorker                │
│  5. Dopiero potem zapisać bieżący jako "last session"              │
│                    │                                                │
│         [Jeśli needs_report = true]                                │
│                    │                                                │
│                    ▼                                                │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │              REASONING WORKER (WorkManager)                  │   │
│  │                                                              │   │
│  │  - Odczytuje Snapshot File (nie z DB!)                      │   │
│  │  - Generuje raport                                          │   │
│  │  - Zapisuje do: Notatnik, Telegram, Local storage           │   │
│  │  - Usuwa Snapshot File                                      │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## Tool Distribution

### Gemini Live (Real-time, low latency)
| Tool | Opis | Powód |
|------|------|-------|
| `search_web` | Google Grounding | Szybkie fakty, real-time |
| `get_weather` | Pogoda | Natychmiastowa odpowiedź |
| `get_current_time` | Czas | Natychmiastowa odpowiedź |
| `get_location` | Lokalizacja GPS | Natychmiastowa odpowiedź |
| `calculate` | Kalkulacje | Natychmiastowa odpowiedź |
| `control_media` | Spotify/media | Natychmiastowa akcja |
| `search_nearby` | Miejsca w pobliżu | Natychmiastowa odpowiedź |
| `start_navigation` | Nawigacja | Natychmiastowa akcja |

### Reasoning Agent (Async, high quality)
| Tool | Opis | Powód |
|------|------|-------|
| `search_perplexity` | Głębokie wyszukiwanie | Wymaga syntezy, cytowania |
| `create_note` | Notatnik | Wymaga formatowania, kontekstu |
| `copy_to_clipboard` | Schowek | Wymaga formatowania |
| `send_telegram` | Telegram | Wymaga formatowania, kontekstu |
| `save_to_drive` | Google Drive | Wymaga formatowania |
| `generate_report` | Raporty | Wymaga głębokiej analizy |

## Requirements

### Requirement 1: Full Context Access (CRITICAL)

**User Story:** As a Reasoning Agent, I need access to full conversation context with proper separation.

#### Acceptance Criteria

1. WHEN Reasoning Agent starts a task THEN it SHALL receive:
   - Reasoning Agent System Prompt (its own instructions)
   - Global User Card (from GlobalMemoryDataStore)
   - Local Conversation Card (from ConversationRepository)
   - Meta-Summary (from ConversationRepository) - SOURCE OF TRUTH for role context
   - Previous Session Transcript (from Snapshot File)
   - Current Session Transcript (from Snapshot File)

2. WHEN building context THEN the system SHALL NOT include:
   - Global Prompt for Gemini Live
   - Tools Instruction for Gemini Live
   - Conversation Persona (System Prompt) - risk of prompt injection

3. WHEN transcript contains errors THEN Reasoning Agent SHALL use bot responses and Meta-Summary to infer user intent

### Requirement 2: Snapshot File Pattern (WorkManager Limit)

**User Story:** As a developer, I need to pass large transcripts to WorkManager without hitting the 10KB limit.

#### Acceptance Criteria

1. WHEN ReasoningAgentManager prepares task THEN it SHALL:
   - Create JSON object with both transcripts and metadata
   - Save to `cacheDir/reasoning-snapshots/task_{UUID}.json`
   - Pass only `snapshot_file_path` in WorkManager Data

2. WHEN ReasoningWorker starts THEN it SHALL:
   - Read snapshot file from path
   - Parse JSON to get transcripts
   - Delete file after processing (cleanup)

3. IF snapshot file is missing THEN Worker SHALL return Result.failure()

### Requirement 3: Race Condition Prevention

**User Story:** As a developer, I want to prevent race conditions between Summary and Reasoning Agent.

#### Acceptance Criteria

1. WHEN Summary Model triggers Reasoning Agent THEN it SHALL:
   - Get previousTranscript BEFORE any DB changes
   - Keep currentTranscript in memory
   - Save to Snapshot File
   - Pass snapshot_file_path to Worker
   - THEN proceed with normal DB operations

2. WHEN ReasoningWorker reads transcripts THEN it SHALL read from Snapshot File, NOT from database

3. WHEN getRecentSessions is called THEN it SHALL use `ORDER BY started_at DESC` for deterministic results

### Requirement 4: Fire-and-Forget Pattern

**User Story:** As Gemini Live, I want to delegate tasks without waiting for results.

#### Acceptance Criteria

1. WHEN Gemini Live calls start_reasoning_task THEN it SHALL NOT wait for result
2. WHEN task is scheduled THEN Gemini Live SHALL continue conversation immediately
3. WHEN Reasoning Agent completes THEN it SHALL inject result via ContextInjector
4. WHEN result is injected THEN Gemini Live SHALL incorporate knowledge naturally in next response

### Requirement 5: Whisperer Mode

**User Story:** As a user, I want Gemini Live to automatically get help when it lacks knowledge.

#### Acceptance Criteria

1. WHEN Gemini Live detects lack of knowledge causing user frustration THEN it SHALL trigger start_reasoning_task silently
2. WHEN topic is too complex for immediate answer THEN Gemini Live MAY trigger Whisperer Mode
3. WHEN triggering Whisperer Mode THEN Gemini Live SHALL NOT announce it to user
4. WHEN Whisperer Mode is active THEN Gemini Live SHALL continue conversation naturally ("buying time")

### Requirement 6: Orphan Result Handling

**User Story:** As a developer, I want to handle results when session is already closed.

#### Acceptance Criteria

1. WHEN ContextInjector receives result THEN it SHALL check if session is active
2. IF session is active THEN inject result as hidden prompt
3. IF session is closed (Orphan Result) THEN:
   - Save result as `pendingInsight` in LocalConversationCard
   - At next session start, assistant SHALL see pending insight
4. WHEN pendingInsight is consumed THEN it SHALL be cleared from LocalConversationCard

### Requirement 7: Error Feedback (Negative Feedback Loop)

**User Story:** As a developer, I want to inform Gemini Live when Reasoning Agent fails.

#### Acceptance Criteria

1. WHEN ReasoningWorker fails after all retries THEN it SHALL:
   - Inject error message to session (if active): "System message: Reasoning task failed due to internal error."
   - Save error info to pendingInsight (if session closed)
2. WHEN Gemini Live receives error message THEN it MAY inform user or continue without knowledge

### Requirement 8: Deep Dive Trigger (Przypadek 1)

**User Story:** As a user, I want Gemini Live to delegate complex research tasks.

#### Acceptance Criteria

1. WHEN Gemini Live detects need for deep research THEN it SHALL call `start_reasoning_task`
2. WHEN `start_reasoning_task` is called THEN system SHALL create Snapshot File with transcripts
3. WHEN Reasoning Agent completes THEN it SHALL inject result or save as pendingInsight
4. WHEN Reasoning Agent is processing THEN Gemini Live SHALL inform user briefly (optional)

### Requirement 9: Report Trigger (Przypadek 2)

**User Story:** As a user, I want automatic report generation after important conversations.

#### Acceptance Criteria

1. WHEN session ends THEN Summary Model SHALL analyze transcript
2. WHEN Summary Model detects report need THEN it SHALL:
   - Create Snapshot File with both transcripts BEFORE DB changes
   - Schedule ReasoningWorker with snapshot_file_path
3. WHEN generating report THEN Reasoning Agent SHALL use Perplexity to enrich topics
4. WHEN report is complete THEN save to: notatnik, Telegram (if configured), local storage

### Requirement 10: Perplexity Integration

**User Story:** As a user, I want Reasoning Agent to use Perplexity for deep research.

#### Acceptance Criteria

1. WHEN Reasoning Agent needs external knowledge THEN it SHALL call Perplexity Sonar API
2. WHEN calling Perplexity THEN use appropriate recency_filter
3. WHEN Perplexity returns results THEN synthesize with context and preserve citations
4. IF Perplexity API fails THEN retry with exponential backoff (3 attempts)

### Requirement 11: Note Creation

**User Story:** As a user, I want Reasoning Agent to create well-formatted notes.

#### Acceptance Criteria

1. WHEN Reasoning Agent determines note creation is needed THEN format content appropriately
2. WHEN creating note THEN add metadata (date, source conversation)
3. WHEN note is saved THEN inject confirmation (or save as pendingInsight)

### Requirement 12: Clipboard Operations

**User Story:** As a user, I want Reasoning Agent to copy formatted content to clipboard.

#### Acceptance Criteria

1. WHEN Reasoning Agent determines clipboard copy is needed THEN format content appropriately
2. WHEN content is copied THEN update Android clipboard
3. WHEN clipboard is updated THEN inject confirmation (or save as pendingInsight)

### Requirement 13: Telegram Integration

**User Story:** As a user, I want Reasoning Agent to send reports and notes to Telegram.

#### Acceptance Criteria

1. WHEN Reasoning Agent determines Telegram send is needed THEN format message appropriately
2. WHEN sending to Telegram THEN use configured bot token and chat ID
3. IF Telegram send fails THEN save locally and notify via pendingInsight

### Requirement 14: Context Injection

**User Story:** As a developer, I want Reasoning Agent to inject context into Gemini Live seamlessly.

#### Acceptance Criteria

1. WHEN Reasoning Agent completes task THEN prepare synthesized context
2. WHEN injecting context THEN use SessionManager.updateContext() mechanism
3. WHEN session is closed THEN save as pendingInsight in LocalConversationCard
4. WHEN injecting THEN include: source, confidence level, key facts

### Requirement 15: Gemini Live Tool Modification

**User Story:** As a developer, I want to modify Gemini Live's tool set.

#### Acceptance Criteria

1. WHEN configuring Gemini Live tools THEN REMOVE: `create_note`, `search_perplexity`
2. WHEN configuring Gemini Live tools THEN ADD: `start_reasoning_task(task_description, priority)`
3. WHEN Gemini Live system prompt is constructed THEN include:
   - Instructions about when to use `start_reasoning_task`
   - Whisperer Mode instructions (silent trigger on lack of knowledge)

### Requirement 16: Summary Model Enhancement

**User Story:** As a developer, I want the summary model to detect report needs.

#### Acceptance Criteria

1. WHEN Summary Model analyzes transcript THEN include report detection
2. WHEN returning structured output THEN include: `needs_report`, `report_topics`, `report_priority`
3. WHEN `needs_report` is true THEN create Snapshot File and schedule ReasoningWorker

### Requirement 17: Reasoning Agent System Prompt

**User Story:** As a developer, I want Reasoning Agent to have a comprehensive system prompt.

#### Acceptance Criteria

1. WHEN Reasoning Agent processes task THEN it SHALL use system prompt that explains:
   - Available actions (search, save, copy, send)
   - How to interpret user intent from context
   - How to handle transcription errors
   - That Meta-Summary is the source of truth for conversation context
   - Output format for context injection
2. WHEN system prompt is constructed THEN it SHALL be separate from Gemini Live prompts

## Configuration Schema

```json
{
  "reasoning_agent": {
    "enabled": true,
    "provider": "openrouter",
    "model_id": "deepseek/deepseek-r1",
    "temperature": 0.4,
    "system_prompt": "...",
    "snapshot_dir": "reasoning-snapshots",
    "tools": {
      "perplexity": { "enabled": true, "model": "sonar-pro" },
      "notes": { "enabled": true, "default_app": "google_keep" },
      "telegram": { "enabled": true },
      "clipboard": { "enabled": true }
    }
  },
  "gemini_live": {
    "tools_to_remove": ["create_note", "search_perplexity"],
    "tools_to_add": ["start_reasoning_task"],
    "whisperer_mode_enabled": true
  },
  "summary_model": {
    "report_detection_enabled": true
  }
}
```

## Data Flow Examples

### Example 1: Whisperer Mode (automatic trigger)

```
User: "Dlaczego Polska nie weszła do strefy euro?"

Gemini Live: [Detects complex topic, may lack detailed knowledge]
  → Silently calls: start_reasoning_task("szczegóły o Polsce i strefie euro", "HIGH")
  → Continues naturally: "To złożona kwestia polityczna i ekonomiczna. 
     Główne argumenty dotyczą suwerenności monetarnej..."
  
[Meanwhile, Reasoning Agent searches Perplexity]

[30 seconds later, result is injected]

Gemini Live: [Receives context update]
  → "...właśnie mi się przypomniało, że kluczowym momentem było referendum 
     w 2003 roku, gdzie Polacy zagłosowali za przystąpieniem do UE, 
     ale bez zobowiązania do przyjęcia euro..."
```

### Example 2: Orphan Result (session closed)

```
User ends session while Reasoning Agent is still working

ReasoningWorker completes:
  → ContextInjector.injectResult()
  → sessionManager.isSessionActive() = false
  → Saves to LocalConversationCard.pendingInsight:
    "Znaleziono informacje o strefie euro: [summary]"

Next session starts:
  → OfflineContextBuilder reads pendingInsight
  → Gemini Live sees: "Masz zaległą wiedzę z poprzedniej analizy: [treść]"
  → Clears pendingInsight after consumption
```

### Example 3: Post-session report (race-condition safe)

```
Session ends:

Summary Model (BEFORE modifying DB):
  1. previousTranscript = getRecentSessions(2)[1]?.transcript
  2. currentTranscript = sess.transcript (in memory)
  3. Creates Snapshot File: task_abc123.json
  4. Analyzes → needs_report = true
  5. Schedules ReasoningWorker with snapshot_file_path
  6. THEN proceeds with normal DB operations

ReasoningWorker:
  - Reads task_abc123.json (not from DB!)
  - Generates report
  - Saves to Notes, Telegram
  - Deletes task_abc123.json
```
