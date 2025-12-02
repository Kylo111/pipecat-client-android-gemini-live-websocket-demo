# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Planning document - superseded by MIGRATION_LOG.md and actual implementation
**Current Documentation:** See MIGRATION_LOG.md for complete operation history and /docs/ for current documentation

---

# Plan Restrukturyzacji Dokumentacji

**Data utworzenia:** 2025-12-01  
**Status:** DRAFT - Oczekuje na zatwierdzenie  
**Cel:** Uporządkowanie dokumentacji markdown i utworzenie profesjonalnej dokumentacji technicznej dla Superwizora (RAG)

---

## 1. ANALIZA OBECNEGO STANU

### 1.1 Statystyki
- **Łączna liczba plików .md w katalogu głównym:** 82 pliki
- **Łączna liczba linii:** ~20,329 linii
- **Największe pliki:**
  - SECURITY_AUDIT_REPORT.md (1,214 linii)
  - REFACTORING_PLAN.md (1,077 linii)
  - RAPORT_STABILNOSCI_POLACZENIA.md (794 linie)
  - VULNERABILITY_ANALYSIS.md (600 linii)

### 1.2 Kategorie zidentyfikowanych plików

#### A. PLIKI ŹRÓDŁOWE (do zachowania i reorganizacji)
**Architektura i wymagania:**
- README.md - główny opis projektu
- REFACTORING_PLAN.md - plan refaktoryzacji lifecycle
- SECURITY_AUDIT_REPORT.md - audyt bezpieczeństwa
- VULNERABILITY_ANALYSIS.md - analiza podatności
- AUDYT_GEMINI_LIVE_FULL_DUPLEX.md - audyt full duplex

**Analizy problemów:**
- RAPORT_STABILNOSCI_POLACZENIA.md - raport stabilności
- CONNECTION_STABILITY_TEST_RESULTS.md - wyniki testów
- TESTING_SCENARIOS.md - scenariusze testowe
- IMPLEMENTATION_ISSUES.md - problemy implementacyjne

**Dokumentacja funkcjonalności:**
- FULL_DUPLEX_IMPLEMENTATION_PLAN.md - plan full duplex
- FULL_DUPLEX_IMPLEMENTATION_SUMMARY.md - podsumowanie full duplex
- SYSTEM_TRANSCRIPTION_IMPLEMENTATION.md - implementacja transkrypcji
- PERPLEXITY_GOOGLE_SEARCH_IMPLEMENTATION.md - implementacja wyszukiwania

**Dokumentacja Picovoice:**
- PICOVOICE_WAKE_WORD_SETUP_GUIDE.md - przewodnik setup
- PICOVOICE_QUICK_START.md - quick start
- PICOVOICE_BUILTIN_KEYWORDS.md - wbudowane słowa kluczowe
- PICOVOICE_TROUBLESHOOTING.md - rozwiązywanie problemów
- PICOVOICE_SCREEN_OFF_ANALYSIS.md - analiza screen off
- PICOVOICE_REAL_PROBLEM_ANALYSIS.md - analiza prawdziwych problemów
- PICOVOICE_UI_IMPROVEMENTS_SUMMARY.md - podsumowanie ulepszeń UI
- PICOVOICE_FIX_FINAL.md - finalne poprawki
- PICOVOICE_FIX_SUMMARY.md - podsumowanie poprawek
- PICOVOICE_FIX_TESTING.md - testowanie poprawek

**Analizy bugów:**
- CRITICAL_BUGS_ANALYSIS.md - analiza krytycznych bugów
- KRYTYCZNY_BUG_PICOVOICE_RECONNECTING.md - bug reconnecting
- KRYTYCZNY_BUG_STARTRECONNECTION.md - bug startReconnection
- DEBUG_RECONNECTION_ISSUE.md - debug reconnection
- FIX_RECONNECTION_STUCK.md - fix stuck reconnection
- AUDIO_BUFFER_UNDERRUN_FIX.md - fix buffer underrun
- AUDIO_NOISE_FIX.md - fix audio noise
- AUDIO_PIPELINE_AND_TIMEOUT_FIX.md - fix pipeline i timeout

#### B. PLIKI ROBOCZE/TYMCZASOWE (do archiwizacji)
**Taski pojedyncze (TASK_*.md):**
- TASK_1.1_COMPLETION_SUMMARY.md
- TASK_1.1.4_COMPLETION_SUMMARY.md
- TASK_1.1.4_PING_PONG_DETECTION_VERIFICATION.md
- TASK_1.3_UI_UPDATE_VERIFICATION.md
- TASK_1.4.2_EXPONENTIAL_BACKOFF_VERIFICATION.md
- TASK_1.4.3_ATTEMPT_COUNTER_VERIFICATION.md
- TASK_1.4.6_CALLBACK_IMPLEMENTATION.md
- TASK_1.4_RECONNECTION_MANAGER_IMPLEMENTATION.md
- TASK_1.5.2_RECOVERABLE_ERROR_HANDLING_VERIFICATION.md
- TASK_1.5.3_FATAL_ERROR_HANDLING_VERIFICATION.md
- TASK_1.5.4_UNKNOWN_ERROR_HANDLING_VERIFICATION.md
- TASK_1.5.6_DETAILED_ERROR_LOGGING_IMPLEMENTATION.md
- TASK_1.5_ENHANCED_WEBSOCKET_FAILURE_HANDLER_VERIFICATION.md
- TASK_1.6_CONCURRENT_AUDIO_PLAYBACK_TEST.md
- TASK_1.6_RACE_CONDITION_VERIFICATION.md
- TASK_2.1_END_CONVERSATION_BUTTON_VERIFICATION.md
- TASK_2.1_RECONNECTION_DIALOG_IMPLEMENTATION.md
- TASK_2.1_RECONNECTION_DIALOG_INTEGRATION_VERIFICATION.md
- TASK_2.2_RECONNECTION_STATUS_WITH_ATTEMPT_COUNT.md
- TASK_2.4_IMAGE_PROCESSING_INTEGRATION.md
- TASK_2.6_COMPLETION_SUMMARY.md
- TASK_2.6_STAY_IN_CONVERSATION_VERIFICATION.md
- TASK_2.6_TESTING_GUIDE.md
- TASK_2_IMPLEMENTATION_SUMMARY.md
- TASK_3.1_VOICESERVICE_IMPLEMENTATION.md
- TASK_3.3_NOTIFICATION_UPDATES_COMPLETION.md
- TASK_3.4_LIFECYCLE_INTEGRATION_VERIFICATION.md
- TASK_3.5_SESSION_TIMEOUT_BACKGROUND_VERIFICATION.md
- TASK_4.2_ENHANCED_ERROR_MESSAGES_COMPLETION.md
- TASK_4.3_IMAGE_PROCESSING_INDICATOR_IMPLEMENTATION.md
- TASK_4.4_PERFORMANCE_OPTIMIZATION_IMPLEMENTATION.md
- TASK_5.1_VOICESERVICE_INTEGRATION_COMPLETION.md
- TASK_BOT_RESPONSE_TIMEOUT_IMPLEMENTATION.md

**Analizy tymczasowe (polskie nazwy):**
- ANALIZA_DLACZEGO_PAUSE_RESUME_DZIALA.md
- ANALIZA_PROBLEMU_RECONNECTING.md
- ANALIZA_USUWANIA_KONWERSACJI.md
- FINALNE_ROZWIAZANIE_DELETE_RECREATE.md
- IMPLEMENTACJA_AUTO_RESTART_5S.md
- IMPLEMENTACJA_STOP_START_AUDIORECORD.md
- INSTRUKCJE_DEBUG_RECONNECTING.md
- NAPRAWA_PICOVOICE_BROADCAST.md
- NAPRAWA_USUWANIA_KONWERSACJI.md
- PODSUMOWANIE_FIX_RECONNECTION.md
- WYJASNIENIE_TIMING_DELAY.md
- FIX_PICOVOICE_START_PAUSED.md

**Inne tymczasowe:**
- DIAGNOSIS.md
- FINAL_SUMMARY.md
- PING_PONG_VERIFICATION.md
- VERIFICATION_COMPLETE.md
- VOICE_CLIENT_MANAGER_REFACTORING_ANALYSIS.md
- REFACTOR_AUDIT_REPORT.md
- REFACTOR_ROLLBACK_PLAN.md
- TEST_ALEXA.md
- TODO_PICOVOICE_AUDIORECORD_FIX.md
- test-ping-pong-detection.md

---

## 2. PROPONOWANA STRUKTURA KATALOGÓW

```
/
├── README.md                          # Główny README (uproszczony, high-level)
├── DOCS_INDEX.md                      # Indeks całej dokumentacji
│
├── /docs/                             # Główny katalog dokumentacji
│   │
│   ├── /project/                      # Dokumentacja projektowa
│   │   ├── requirements.md            # Wymagania funkcjonalne i niefunkcjonalne
│   │   ├── architecture.md            # Architektura systemu
│   │   ├── decisions.md               # Decyzje architektoniczne (ADR)
│   │   └── roadmap.md                 # Plan rozwoju
│   │
│   ├── /domain/                       # Model domenowy
│   │   ├── model.md                   # Obiekty domenowe, agregaty
│   │   ├── state-machine.md           # Maszyny stanów (ConnectionState, etc.)
│   │   ├── events.md                  # Zdarzenia domenowe
│   │   └── business-rules.md          # Reguły biznesowe
│   │
│   ├── /implementation/               # Szczegóły implementacji
│   │   ├── components.md              # Opis komponentów i klas
│   │   ├── interactions.md            # Sekwencje i przepływy
│   │   ├── lifecycle.md               # Zarządzanie cyklem życia
│   │   ├── audio-pipeline.md          # Pipeline audio
│   │   ├── websocket-management.md    # Zarządzanie WebSocket
│   │   ├── picovoice-integration.md   # Integracja Picovoice
│   │   └── librechat-integration.md   # Integracja LibreChat
│   │
│   ├── /operations/                   # Operacje i monitoring
│   │   ├── errors-and-recovery.md     # Obsługa błędów i recovery
│   │   ├── monitoring.md              # Monitoring i metryki
│   │   ├── security.md                # Bezpieczeństwo
│   │   ├── performance.md             # Wydajność i optymalizacja
│   │   └── troubleshooting.md         # Rozwiązywanie problemów
│   │
│   ├── /testing/                      # Dokumentacja testów
│   │   ├── test-strategy.md           # Strategia testowania
│   │   ├── test-scenarios.md          # Scenariusze testowe
│   │   └── test-results.md            # Wyniki testów
│   │
│   └── /guides/                       # Przewodniki użytkownika
│       ├── picovoice-setup.md         # Setup Picovoice
│       ├── quick-start.md             # Szybki start
│       └── development.md             # Przewodnik dla deweloperów
│
└── /archive/                          # Archiwum przestarzałych dokumentów
    ├── /tasks/                        # Stare TASK_*.md
    ├── /analyses/                     # Stare analizy
    └── /fixes/                        # Stare dokumenty fix
```

---

## 3. MAPOWANIE PLIKÓW

### 3.1 Pliki do /docs/project/

**requirements.md** (nowy, skonsolidowany):
- Źródła: README.md (sekcja Features), AUDYT_GEMINI_LIVE_FULL_DUPLEX.md
- Zawartość: Wymagania funkcjonalne, niefunkcjonalne, ograniczenia

**architecture.md** (nowy, skonsolidowany):
- Źródła: README.md (sekcja Architecture), REFACTORING_PLAN.md (sekcja Architecture)
- Zawartość: Komponenty główne, bounded contexts, moduły

**decisions.md** (nowy):
- Źródła: REFACTORING_PLAN.md, SECURITY_AUDIT_REPORT.md (rekomendacje)
- Zawartość: ADR (Architecture Decision Records)

### 3.2 Pliki do /docs/domain/

**model.md** (nowy):
- Źródła: Analiza kodu + README.md
- Zawartość: VoiceClientManager, SessionManager, ConnectionState, etc.

**state-machine.md** (nowy):
- Źródła: Analiza kodu VoiceClientManager.kt
- Zawartość: ConnectionState transitions, lifecycle states

### 3.3 Pliki do /docs/implementation/

**components.md** (nowy, skonsolidowany):
- Źródła: README.md (Core Components), analiza kodu
- Zawartość: Szczegółowy opis każdej klasy

**lifecycle.md** (przenieś i rozszerz):
- Źródło: .kiro/steering/lifecycle.md
- Dodaj: REFACTORING_PLAN.md (Faza 2)

**audio-pipeline.md** (nowy):
- Źródła: AUDIO_PIPELINE_AND_TIMEOUT_FIX.md, AUDIO_BUFFER_UNDERRUN_FIX.md
- Zawartość: AudioRecord, AudioTrack, buffer management

**websocket-management.md** (nowy):
- Źródła: CONNECTION_STABILITY_TEST_RESULTS.md, RAPORT_STABILNOSCI_POLACZENIA.md
- Zawartość: WebSocket lifecycle, reconnection, ping-pong

**picovoice-integration.md** (skonsoliduj):
- Źródła: Wszystkie PICOVOICE_*.md
- Zawartość: Setup, troubleshooting, known issues

### 3.4 Pliki do /docs/operations/

**errors-and-recovery.md** (skonsoliduj):
- Źródła: README.md (Error Handling), IMPLEMENTATION_ISSUES.md
- Zawartość: Typy błędów, strategie recovery

**security.md** (skonsoliduj i aktualizuj):
- Źródło główne: SECURITY_AUDIT_REPORT.md (jako baza)
- Status dokumentu: AKTYWNY (living document)
- Sekcja źródłowa: Dodać "Źródło historyczne: SECURITY_AUDIT_REPORT.md (archived 2025-11-17)"
- Zawartość: Aktualne zagrożenia, mitigacje, best practices
- UWAGA: Sam dokument security.md jest AKTYWNY, tylko źródłowy raport jest archived

**performance.md** (nowy):
- Źródła: README.md (Performance Metrics), REFACTORING_PLAN.md (Metryki)

**troubleshooting.md** (skonsoliduj):
- Źródła: README.md (Troubleshooting), PICOVOICE_TROUBLESHOOTING.md

### 3.5 Pliki do /docs/testing/

**test-strategy.md** (nowy):
- Źródła: TESTING_SCENARIOS.md, REFACTORING_PLAN.md (Faza 4)

**test-scenarios.md** (przenieś):
- Źródło: TESTING_SCENARIOS.md

**test-results.md** (skonsoliduj):
- Źródła: CONNECTION_STABILITY_TEST_RESULTS.md, wszystkie VERIFICATION_*.md

### 3.6 Pliki do /docs/guides/

**picovoice-setup.md** (skonsoliduj):
- Źródła: PICOVOICE_WAKE_WORD_SETUP_GUIDE.md, PICOVOICE_QUICK_START.md

**quick-start.md** (nowy):
- Źródła: README.md (Development section)

### 3.7 Pliki do /archive/

**archive/tasks/** (przenieś wszystkie TASK_*.md):
- Dodaj header: "STATUS: ARCHIVED - Zobacz /docs/implementation/"

**archive/analyses/** (przenieś analizy polskie):
- ANALIZA_*.md, NAPRAWA_*.md, PODSUMOWANIE_*.md, etc.
- Dodaj header: "STATUS: ARCHIVED"

**archive/fixes/** (przenieś dokumenty fix):
- FIX_*.md, AUDIO_*_FIX.md, etc.
- Dodaj header: "STATUS: ARCHIVED"

---

## 4. NOWE DOKUMENTY DO UTWORZENIA

### 4.1 /docs/project/requirements.md
**Zawartość:**
- Wymagania funkcjonalne (real-time voice, background operation, wake word, etc.)
- Wymagania niefunkcjonalne (performance, security, battery)
- Ograniczenia (Android API level, hardware requirements)
- Powiązania z taskami (które wymaganie realizuje który task)

### 4.2 /docs/domain/model.md
**Zawartość:**
- **VoiceClientManager**: rola, pola, metody, stany, zależności
- **SessionManager**: rola, pola, metody, lifecycle
- **ConnectionState**: enum, transitions, triggers
- **ReconnectionManager**: strategia, backoff, limity
- **AudioPipeline**: AudioRecord, AudioTrack, buffers
- Diagramy relacji między obiektami

### 4.3 /docs/implementation/components.md
**Format dla każdego komponentu:**
```markdown
## [ComponentName]

### Rola
[Opis odpowiedzialności]

### Główne metody
- `methodName(params)`: [opis, prewarunki, postwarunki, side-effects]

### Stany
- [Lista stanów i transitions]

### Zależności
- [Lista zależności z kierunkiem i typem relacji]

### Wyjątki i błędy
- [Możliwe błędy, jak są obsługiwane]

### Testowalność
- [Jak testować, mocki, edge cases]
```

### 4.4 /docs/implementation/interactions.md
**Zawartość:**
- Sekwencje krok po kroku dla kluczowych scenariuszy:
  - Start conversation
  - Reconnection flow
  - Background operation
  - Wake word detection
  - Error recovery
- Format: krok po kroku, które obiekty/metody uczestniczą

### 4.5 /docs/operations/errors-and-recovery.md
**Zawartość:**
- Katalog typów błędów (RECOVERABLE, FATAL, UNKNOWN)
- Strategie recovery dla każdego typu
- Retry policies
- Timeout configurations
- Metryki i alerty
- Przykłady logów

---

## 5. ZASADY DOKUMENTACJI

### 5.1 Wymagania jakościowe

**Dla każdej metody:**
- Rola i odpowiedzialność
- Parametry wejściowe (typy, walidacja)
- Wartość zwracana
- Prewarunki (co musi być spełnione przed wywołaniem)
- Postwarunki (co jest gwarantowane po wykonaniu)
- Side-effects (zmiany stanu, I/O, etc.)
- Możliwe wyjątki i błędy
- Przykłady użycia

**Dla każdego obiektu:**
- Rola w systemie
- Główne pola (typy, znaczenie, invarianty)
- Główne metody (jak wyżej)
- Relacje z innymi obiektami (kompozycja, agregacja, obserwacja)
- Lifecycle (tworzenie, użycie, niszczenie)
- Testowalność (jak mockować, edge cases)

**Dla każdej relacji:**
- Kierunek zależności (A → B)
- Typ relacji (kompozycja, agregacja, asocjacja, obserwacja)
- Kardynalność (1:1, 1:N, N:M)
- Wpływ na testowalność
- Możliwe problemy (circular dependencies, memory leaks)

### 5.2 Oznaczanie niepewności

Jeśli czegoś nie można wywnioskować z kodu:
```markdown
**UNKNOWN / TO CLARIFY**: [opis czego nie wiadomo i dlaczego]
```

### 5.3 Linki i referencje

Każdy dokument powinien zawierać:
- Linki do powiązanych dokumentów
- Linki do kodu źródłowego (pliki, linie)
- Linki do tasków/issues
- Data ostatniej aktualizacji

---

## 6. GUARD RAILS - ZASADY BEZPIECZEŃSTWA

**KRYTYCZNE ZASADY przed rozpoczęciem migracji:**

### 6.0 Pre-Migration Checklist
**Przed rozpoczęciem jakiejkolwiek operacji:**
- [ ] Plan zatwierdzony przez użytkownika
- [ ] Utworzony backup (git commit z opisem "Pre-documentation-restructuring")
- [ ] Utworzony MIGRATION_LOG.md
- [ ] Utworzony .kiro/steering/documentation-rules.md
- [ ] Użytkownik potwierdził gotowość do startu

### 6.1 Zakaz usuwania bez śladu
- ❌ **NIGDY nie usuwaj plików** bez przeniesienia do /archive/
- ✅ Każdy plik musi mieć ślad: albo w /archive/, albo skonsolidowany w /docs/
- ✅ Każdy przeniesiony plik do /archive/ musi mieć header z linkiem do aktualnego dokumentu

### 6.2 Rozwiązywanie konfliktów treści
**Gdy znajdziesz sprzeczne informacje w różnych plikach:**
1. **Priorytet 1:** Aktualny kod źródłowy (gemini-multimodal-websocket-demo/src/)
2. **Priorytet 2:** Najnowsze pliki (według daty modyfikacji i treści)
3. **Priorytet 3:** Pliki z "FINAL" lub "SUMMARY" w nazwie
4. **Priorytet 4:** Starsze analizy i taski

**Proces:**
- Porównaj z kodem źródłowym
- Jeśli kod się różni od dokumentacji → kod ma rację
- Oznacz sprzeczności jako "OUTDATED" w starym dokumencie
- W nowym dokumencie dodaj: "Aktualizacja: [data] - poprzednia wersja była nieprawidłowa"

### 6.3 Weryfikacja przed zapisem
**Przed zapisaniem każdego nowego dokumentu w /docs/:**
- [ ] Sprawdź czy informacje są zgodne z kodem
- [ ] Dodaj referencje do plików źródłowych (ścieżki, linie)
- [ ] Dodaj datę ostatniej weryfikacji
- [ ] Jeśli czegoś nie możesz zweryfikować → oznacz "UNKNOWN / TO CLARIFY"

### 6.4 Logowanie zmian
**Każda operacja musi być zalogowana:**
- Plik przeniesiony: skąd → dokąd
- Plik skonsolidowany: które źródła → jaki wynik
- Konflikt rozwiązany: jaka była sprzeczność, jak rozwiązano

**Format logu:** (utworzyć MIGRATION_LOG.md)
```markdown
## [Data] [Operacja]
- Źródło: [ścieżka]
- Cel: [ścieżka]
- Powód: [dlaczego]
- Konflikty: [jeśli były]
```

### 6.5 Checkpoint po każdej fazie
**Po zakończeniu każdej fazy:**
1. Zatrzymaj się
2. Pokaż użytkownikowi podsumowanie
3. Czekaj na zatwierdzenie przed kolejną fazą

---

## 7. PROCES MIGRACJI

### Faza 1: Przygotowanie (1 dzień)
1. ✅ Utworzenie tego planu
2. ✅ Dodanie Guard Rails
3. ⏳ Review i zatwierdzenie przez użytkownika
4. ⏳ Utworzenie MIGRATION_LOG.md
5. ⏳ Utworzenie struktury katalogów
6. ⏳ Utworzenie steering rules dla przyszłych agentów

### Faza 2: Archiwizacja (0.5 dnia)
1. Utworzenie /archive/ z podkatalogami
2. Przeniesienie TASK_*.md do /archive/tasks/
3. Przeniesienie analiz polskich do /archive/analyses/
4. Przeniesienie fix documents do /archive/fixes/
5. Dodanie ARCHIVED headers do wszystkich

### Faza 3: Konsolidacja (2 dni)
1. Utworzenie /docs/ z podkatalogami
2. Skonsolidowanie dokumentów według mapowania
3. Utworzenie nowych dokumentów (requirements.md, model.md, etc.)
4. Dodanie linków krzyżowych

### Faza 4: Szczegółowa dokumentacja (3-4 dni)
1. Analiza kodu źródłowego
2. Wypełnienie components.md (wszystkie klasy)
3. Wypełnienie interactions.md (wszystkie sekwencje)
4. Wypełnienie model.md (wszystkie obiekty domenowe)
5. Dodanie diagramów (Mermaid)

### Faza 5: Weryfikacja (1 dzień)
1. Review kompletności
2. Sprawdzenie linków
3. Weryfikacja z kodem
4. Utworzenie DOCS_INDEX.md

---

## 8. STEERING RULES DLA AGENTÓW

**Utworzyć: .kiro/steering/documentation-rules.md**

Zawartość dla przyszłych agentów:

```markdown
---
inclusion: always
---

# Documentation Maintenance Rules

## CRITICAL: File Deletion Policy

**NEVER delete any .md file without:**
1. Moving it to /archive/ with proper categorization
2. Adding ARCHIVED header with link to current document
3. Logging the operation in MIGRATION_LOG.md

**If you find outdated information:**
- Move file to /archive/
- Update current document in /docs/
- Add reference to archived version

## Conflict Resolution

**When documentation conflicts with code:**
1. Code is ALWAYS the source of truth
2. Update documentation to match code
3. Mark old documentation as OUTDATED
4. Add note: "Updated [date] - verified against [file:line]"

**When multiple documents conflict:**
1. Check file dates and content
2. Verify against source code
3. Prioritize: code > recent docs > old docs
4. Consolidate into single source of truth

## New Documentation

**Where to place new documents:**
- Task completion notes → /archive/tasks/
- Technical analysis → /docs/implementation/ or /docs/operations/
- User guides → /docs/guides/
- Architecture decisions → /docs/project/decisions.md

**Required in every new document:**
- Date of creation
- Source references (code files, lines)
- Links to related documents
- "Last verified: [date]" footer

## Documentation Quality

**Every method description must include:**
- Role and responsibility
- Parameters (types, validation)
- Return value
- Preconditions
- Postconditions
- Side-effects
- Possible errors
- Example usage

**Every object description must include:**
- Role in system
- Main fields (types, invariants)
- Main methods (as above)
- Relations with other objects
- Lifecycle
- Testability notes

**If uncertain:**
- Mark as "UNKNOWN / TO CLARIFY: [reason]"
- Do NOT guess or assume
- Ask user for clarification

## RAG Indexing Priority

**Always index (Priority 1):**
- /docs/project/architecture.md
- /docs/domain/model.md
- /docs/implementation/components.md
- /docs/implementation/lifecycle.md

**Context-dependent (Priority 2):**
- /docs/implementation/interactions.md
- /docs/operations/errors-and-recovery.md

**Never index:**
- /archive/* (historical only)
```

---

## 9. DOCS_MAINTENANCE_RULES.md

Po zakończeniu migracji utworzyć dokument z zasadami operacyjnymi:

**Gdzie trafiają nowe dokumenty:**
- Robocze TASK_*.md → /workspace/tmp/ (nie commitować)
- Po zakończeniu tasku → /archive/tasks/ z ARCHIVED header
- Nowe analizy → /docs/implementation/ lub /docs/operations/
- Nowe przewodniki → /docs/guides/

**Hook "doc-sync":**
- Trigger: Po zakończeniu tasku
- Akcja: Przenieś TASK_*.md do /archive/, zaktualizuj odpowiedni dokument w /docs/

**Zasady aktualizacji:**
- Każda zmiana w kodzie → aktualizacja odpowiedniego dokumentu w /docs/
- Każda nowa klasa → dodanie do components.md
- Każdy nowy flow → dodanie do interactions.md

---

## 8. KLUCZOWE PLIKI DLA RAG SUPERWIZORA

Po zakończeniu migracji, system RAG powinien indeksować:

**Priorytet 1 (zawsze w kontekście):**
- /docs/project/architecture.md
- /docs/domain/model.md
- /docs/implementation/components.md
- /docs/implementation/lifecycle.md

**Priorytet 2 (w zależności od zadania):**
- /docs/implementation/interactions.md
- /docs/operations/errors-and-recovery.md
- /docs/implementation/websocket-management.md
- /docs/implementation/audio-pipeline.md

**Priorytet 3 (referencje i kontekst):**
- /docs/operations/troubleshooting.md
- /docs/testing/test-scenarios.md
- /docs/guides/*
- README.md (opcjonalnie - high-level overview, przydatny dla raportów dla ludzi)

**Nie indeksować:**
- /archive/* (przestarzałe, tylko dla historii)

---

## 10. NASTĘPNE KROKI

**Oczekuję na zatwierdzenie:**
1. Czy struktura katalogów jest odpowiednia?
2. Czy mapowanie plików jest poprawne?
3. Czy są jakieś dodatkowe kategorie dokumentów?
4. Czy nazwy katalogów są intuicyjne?

**Po zatwierdzeniu:**
1. Utworzę strukturę katalogów
2. Zacznę migrację według planu
3. Będę raportować postęp po każdej fazie

---

---

## CHANGELOG PLANU

**2025-12-01 - v1.1 - Dodano Guard Rails:**
- ✅ Doprecyzowano security.md jako AKTYWNY dokument (nie archived)
- ✅ Przeniesiono README.md do Priorytetu 3 (opcjonalny dla RAG)
- ✅ Dodano sekcję 6: Guard Rails - zasady bezpieczeństwa
- ✅ Dodano Pre-Migration Checklist
- ✅ Dodano zasady rozwiązywania konfliktów (kod > dokumentacja)
- ✅ Dodano zakaz usuwania bez śladu
- ✅ Dodano wymaganie logowania wszystkich operacji (MIGRATION_LOG.md)
- ✅ Dodano sekcję 8: Steering Rules dla przyszłych agentów
- ✅ Dodano checkpoint po każdej fazie

**2025-12-01 - v1.0 - Wersja początkowa:**
- Analiza 82 plików markdown
- Proponowana struktura katalogów
- Mapowanie plików
- Plan migracji w 5 fazach

---

**UWAGA:** Ten plan jest DRAFT v1.1. Nie wykonuję żadnych masowych operacji dopóki użytkownik nie zatwierdzi.

**Zmiany wprowadzone na podstawie sugestii użytkownika:**
1. Security.md jest dokumentem AKTYWNYM (tylko źródło archived)
2. README.md w Priorytecie 3 dla RAG (opcjonalny, przydatny dla raportów)
3. Dodano Guard Rails - zakaz usuwania, rozwiązywanie konfliktów, weryfikacja
4. Dodano steering rules dla przyszłych agentów
5. Dodano Pre-Migration Checklist
