---
title: Vertex AI RAG + Conversation Memory Integration
status: draft
created: 2025-11-17
---

# Vertex AI RAG + Conversation Memory Integration

## Cel

Integracja dwóch systemów z Google Vertex AI:
1. **Conversation Memory** - dwuwarstwowe przechowywanie historii konwersacji (SQLite + Vertex AI Vector Search)
2. **RAG System** - zarządzanie dokumentami per-konwersacja z możliwością odpytywania przez Gemini Live

## Wymagania Funkcjonalne

### 1. Conversation Memory System

#### 1.1 Warstwa 1: SQLite (Local Storage)
- **Session-based storage** - każda sesja Gemini Live to jeden rekord
- Pełna transkrypcja sesji (wszystkie wymiany user↔assistant)
- Auto-generated summary po zakończeniu każdej sesji (Gemini Flash)
- Ostatnie 3-5 sesji z aktywnej konwersacji
- Instant access (<5ms)
- Offline fallback - działa bez internetu
- Metadata: session_id, conversation_id, started_at, ended_at, duration, message_count

#### 1.2 Warstwa 2: Vertex AI Vector Search
- Semantic search po całej historii konwersacji
- Automatyczny background sync z SQLite
- Vertex AI Embeddings (Gecko) do wektoryzacji
- Wyszukiwanie podobnych wiadomości z przeszłości
- Metadata filtering (conversationId, dateRange, role)

#### 1.3 Funkcjonalności
- Automatyczne zapisywanie transkrypcji z Gemini Live
- Generowanie podsumowań starszych wątków (>50 wiadomości)
- Pobieranie kontekstu: recent (SQLite) + semantic (Vertex)
- Background sync przez WorkManager
- Retry logic dla failed syncs
- Offline mode - tylko SQLite


### 2. RAG Document System

#### 2.1 Obsługiwane Formaty
- PDF
- MP3 (audio)
- DOC, DOCX
- XLS, XLSX
- TXT, MD
- Obrazy (PNG, JPG) - opcjonalnie

#### 2.2 Przechowywanie Dokumentów
- SQLite: metadata + file content (BLOB, max 50MB)
- Vertex AI RAG: indeksowane dokumenty w shared corpus
- **Shared documents** - wszystkie konwersacje mają dostęp do wszystkich dokumentów
- Filtrowanie dokumentów per-conversation przez metadata/tags (opcjonalne)

#### 2.3 Funkcjonalności
- File picker - wybór plików z urządzenia
- Upload do SQLite (natychmiast)
- Background upload do Vertex AI RAG
- Status tracking: pending/uploading/uploaded/failed
- Retry logic dla failed uploads
- Usuwanie dokumentów (lokalnie + Vertex)
- Lista dokumentów per-conversation
- Preview dokumentów (nazwa, rozmiar, typ, status)

#### 2.4 Integracja z Gemini Live
- Automatyczne dodawanie kontekstu z RAG do system instructions
- Query RAG przed rozpoczęciem konwersacji
- Możliwość re-query podczas konwersacji (opcjonalne)
- Informowanie użytkownika o dostępnych dokumentach


### 3. Integracja z LibreChat

#### 3.1 Wymagania
- Współdzielenie dokumentów między Gemini Live a LibreChat
- Synchronizacja historii konwersacji
- Unified conversation ID
- Export/import konwersacji

#### 3.2 Funkcjonalności
- Dodawanie dokumentów w LibreChat → dostępne w Gemini Live
- Historia z LibreChat → dostępna w Gemini Live (przez Vertex Vector Search)
- Przełączanie między trybami: Gemini Live ↔ LibreChat
- Zachowanie kontekstu przy przełączaniu

## Architektura Techniczna

### Database Schema (SQLite)

#### Sessions Table
```sql
CREATE TABLE sessions (
    id TEXT PRIMARY KEY, -- UUID
    conversation_id TEXT NOT NULL,
    started_at INTEGER NOT NULL,
    ended_at INTEGER,
    duration_seconds INTEGER,
    transcript TEXT NOT NULL, -- Pełna transkrypcja sesji
    summary TEXT, -- Auto-generated po zakończeniu
    message_count INTEGER DEFAULT 0, -- Liczba wymian w sesji
    synced_to_vertex BOOLEAN DEFAULT 0,
    vertex_vector_id TEXT,
    metadata TEXT, -- JSON
    FOREIGN KEY (conversation_id) REFERENCES conversations(id)
);

CREATE INDEX idx_sessions_conversation ON sessions(conversation_id);
CREATE INDEX idx_sessions_started_at ON sessions(started_at);
```

#### Session Messages Table (Opcjonalne - dla szczegółów)
```sql
CREATE TABLE session_messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    role TEXT NOT NULL, -- 'user' | 'assistant'
    content TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions(id)
);

CREATE INDEX idx_session_messages_session ON session_messages(session_id);
```

#### Documents Table
```sql
CREATE TABLE documents (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id TEXT NOT NULL,
    file_name TEXT NOT NULL,
    file_content BLOB NOT NULL,
    mime_type TEXT NOT NULL,
    file_size INTEGER NOT NULL,
    uploaded_to_vertex BOOLEAN DEFAULT 0,
    vertex_rag_file_id TEXT,
    upload_status TEXT DEFAULT 'pending', -- 'pending' | 'uploading' | 'uploaded' | 'failed'
    created_at INTEGER NOT NULL,
    last_synced_at INTEGER,
    error_message TEXT,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id)
);
```


#### Conversations Table
```sql
CREATE TABLE conversations (
    id TEXT PRIMARY KEY, -- UUID
    title TEXT,
    created_at INTEGER NOT NULL,
    last_session_at INTEGER NOT NULL,
    session_count INTEGER DEFAULT 0,
    total_duration_seconds INTEGER DEFAULT 0,
    document_count INTEGER DEFAULT 0,
    meta_summary TEXT, -- Summary of summaries (co 10 sesji)
    source TEXT DEFAULT 'gemini_live', -- 'gemini_live' | 'librechat'
    metadata TEXT -- JSON
);
```

#### Document Tags (opcjonalne filtrowanie)
```sql
CREATE TABLE document_tags (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    document_id INTEGER NOT NULL,
    tag TEXT NOT NULL,
    FOREIGN KEY (document_id) REFERENCES documents(id)
);

CREATE INDEX idx_document_tags ON document_tags(tag);
```

### Vertex AI Components

#### 1. Vertex AI Vector Search
- **Index**: conversation-memory-index
- **Endpoint**: conversation-memory-endpoint
- **Embeddings Model**: textembedding-gecko@003
- **Dimensions**: 768
- **Distance Measure**: DOT_PRODUCT_DISTANCE

#### 2. Vertex AI RAG
- **Single Shared Corpus**: rag-corpus-{userId} (wszystkie dokumenty użytkownika)
- **Metadata Filtering**: conversationId, tags dla opcjonalnego filtrowania
- **Supported File Types**: PDF, DOCX, TXT, MP3, XLSX, PNG, JPG


### Android Architecture

#### Layers
```
┌─────────────────────────────────────────────────────┐
│                  UI Layer (Compose)                 │
│  - InCallLayout (z document/history indicators)    │
│  - DocumentManagerScreen (nowy)                     │
│  - ConversationHistoryScreen (nowy)                 │
└──────────────────┬──────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────┐
│              ViewModel Layer                        │
│  - VoiceClientManager (rozszerzony)                 │
│  - DocumentViewModel (nowy)                         │
│  - ConversationViewModel (nowy)                     │
└──────────────────┬──────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────┐
│             Repository Layer                        │
│  - ConversationRepository (nowy)                    │
│  - DocumentRepository (nowy)                        │
└──────────────────┬──────────────────────────────────┘
                   │
         ┌─────────┴─────────┐
         │                   │
┌────────▼────────┐  ┌───────▼──────────┐
│  Local Storage  │  │  Vertex AI Layer │
│  - Room DB      │  │  - Vector Search │
│  - SQLite       │  │  - RAG API       │
│                 │  │  - Embeddings    │
└─────────────────┘  └──────────────────┘
```

#### Key Classes

**Data Layer:**
- `MessageEntity` - wiadomości w SQLite
- `DocumentEntity` - dokumenty w SQLite
- `ConversationEntity` - konwersacje w SQLite
- `MessageDao`, `DocumentDao`, `ConversationDao` - Room DAOs

**Domain Layer:**
- `ConversationRepository` - zarządzanie wiadomościami
- `DocumentRepository` - zarządzanie dokumentami
- `VertexVectorSearchService` - Vector Search operations
- `VertexRAGService` - RAG operations
- `VertexEmbeddingsService` - embeddings generation

**Background Processing:**
- `MessageSyncWorker` - sync wiadomości do Vertex Vector Search
- `DocumentUploadWorker` - upload dokumentów do Vertex RAG
- `ConversationSummaryWorker` - generowanie podsumowań


## User Flows

### Flow 1: Rozpoczęcie Konwersacji z Kontekstem

1. Użytkownik otwiera aplikację
2. Wybiera istniejącą konwersację lub tworzy nową
3. System buduje kontekst:
   - **Pełna transkrypcja ostatniej sesji** (jeśli istnieje)
   - Summaries poprzednich 5-10 sesji
   - Conversation meta-summary
   - Query Vertex Vector Search dla semantic matches
   - Query Vertex RAG dla dokumentów
4. Gemini Live startuje z pełnym kontekstem w system instructions
5. Użytkownik rozmawia, transkrypcja zapisuje się real-time do SQLite
6. Po zakończeniu sesji:
   - Auto-generate summary (Gemini Flash, 2-3 sek)
   - Background sync summary do Vertex Vector Search

### Flow 2: Dodawanie Dokumentu do Konwersacji

1. Użytkownik w konwersacji klika "Add Document"
2. File picker - wybór pliku
3. Walidacja (typ, rozmiar <50MB)
4. Zapis do SQLite (natychmiast)
5. UI pokazuje dokument ze statusem "Uploading..."
6. Background upload do Vertex RAG
7. Status zmienia się na "Uploaded" lub "Failed"
8. Przy następnej konwersacji dokument jest dostępny dla Gemini

### Flow 3: Wyszukiwanie w Historii

1. Użytkownik klika "Search History"
2. Wpisuje zapytanie
3. System:
   - Generuje embedding zapytania (Vertex Embeddings)
   - Szuka w Vertex Vector Search
   - Pokazuje wyniki z kontekstem
4. Użytkownik może:
   - Przejść do tej konwersacji
   - Skopiować fragment
   - Dodać do current context

### Flow 4: Offline Mode

1. Brak internetu
2. System automatycznie:
   - Używa tylko SQLite (warstwa 1)
   - Wyłącza Vertex queries
   - Pokazuje indicator "Offline mode"
3. Wszystkie operacje zapisują się lokalnie
4. Po powrocie internetu - automatic background sync


## UI Components (Nowe/Zmodyfikowane)

### 1. InCallLayout (Rozszerzony)
- Indicator: liczba dostępnych dokumentów
- Indicator: liczba wiadomości w historii
- Button: "Documents" - otwiera DocumentManagerScreen
- Button: "History" - otwiera ConversationHistoryScreen

### 2. DocumentManagerScreen (Nowy)
```
┌─────────────────────────────────────┐
│  Documents (3)              [+ Add] │
├─────────────────────────────────────┤
│  📄 Raport_Q4.pdf                   │
│     2.3 MB • Uploaded ✓             │
├─────────────────────────────────────┤
│  📊 Dane_sprzedaz.xlsx              │
│     1.1 MB • Uploading... 45%       │
├─────────────────────────────────────┤
│  🎵 Nagranie_spotkania.mp3          │
│     15.2 MB • Failed ⚠️  [Retry]    │
└─────────────────────────────────────┘
```

### 3. ConversationHistoryScreen (Nowy)
```
┌─────────────────────────────────────┐
│  Search History                     │
│  [Search box...]                    │
├─────────────────────────────────────┤
│  Recent Conversations               │
├─────────────────────────────────────┤
│  📅 Today                            │
│  "Omówienie projektu X"             │
│  3 messages • 2 documents           │
├─────────────────────────────────────┤
│  📅 Yesterday                        │
│  "Analiza danych sprzedażowych"     │
│  15 messages • 1 document           │
└─────────────────────────────────────┘
```

### 4. ConversationDetailScreen (Nowy)
- Pełna historia wiadomości
- Attached documents
- Export conversation
- Delete conversation


## Implementation Plan

### Phase 1: Foundation (3-4 dni)

#### Task 1.1: Database Setup
- [ ] Utworzyć Room database schema
- [ ] Zdefiniować Entities: Message, Document, Conversation
- [ ] Zaimplementować DAOs
- [ ] Migracje database
- [ ] Unit testy dla DAOs

#### Task 1.2: Vertex AI Setup (GCP)
- [ ] Utworzyć Vertex AI Vector Search index
- [ ] Skonfigurować index endpoint
- [ ] Utworzyć Vertex AI RAG corpus (shared)
- [ ] Skonfigurować authentication (service account)
- [ ] Przetestować API calls z Postman/curl

#### Task 1.3: Basic Repositories
- [ ] ConversationRepository - podstawowe operacje CRUD
- [ ] DocumentRepository - podstawowe operacje CRUD
- [ ] Testy jednostkowe

### Phase 2: Conversation Memory (4-5 dni)

#### Task 2.1: SQLite Session Storage
- [ ] SessionEntity + SessionDao (Room)
- [ ] Zapisywanie transkrypcji podczas sesji Gemini Live
- [ ] Real-time append do session.transcript
- [ ] Opcjonalnie: session_messages dla szczegółów
- [ ] Pobieranie ostatnich N sesji
- [ ] Filtrowanie po conversationId
- [ ] UI: lista konwersacji z session count

#### Task 2.2: Vertex Vector Search Integration
- [ ] VertexEmbeddingsService - generowanie embeddings
- [ ] VertexVectorSearchService - CRUD operations
- [ ] Upsert wiadomości do Vector Search
- [ ] Semantic search query
- [ ] Testy integracyjne

#### Task 2.3: Background Sync
- [ ] SessionSyncWorker - WorkManager
- [ ] Sync logic: session summaries → Vertex Vector Search
- [ ] Batch sync (multiple sessions)
- [ ] Retry mechanism
- [ ] Status tracking
- [ ] Network constraints

#### Task 2.4: Context Retrieval (Hybrid Approach)
- [ ] **Pełna transkrypcja ostatniej sesji** (szczegóły, świeża pamięć)
- [ ] Summaries poprzednich 5-10 sesji (kontekst historyczny)
- [ ] Conversation meta-summary (big picture overview)
- [ ] Semantic search w Vertex (2-3 relevantne stare sesje)
- [ ] Merge w odpowiedniej kolejności: meta → recent summaries → last full → semantic
- [ ] Formatowanie dla Gemini system instructions
- [ ] Token budget management (max ~30k tokens context)


### Phase 3: RAG Document System (4-5 dni)

#### Task 3.1: Document Storage (SQLite)
- [ ] File picker integration
- [ ] Walidacja plików (typ, rozmiar)
- [ ] Zapis do SQLite (BLOB)
- [ ] Metadata tracking
- [ ] UI: DocumentManagerScreen

#### Task 3.2: Vertex RAG Integration
- [ ] VertexRAGService - upload/delete/query
- [ ] Per-conversation corpus management
- [ ] Upload dokumentów do Vertex RAG
- [ ] Query RAG dla kontekstu
- [ ] Testy integracyjne

#### Task 3.3: Background Upload
- [ ] DocumentUploadWorker - WorkManager
- [ ] Upload logic: SQLite → Vertex RAG
- [ ] Progress tracking
- [ ] Retry mechanism
- [ ] Error handling

#### Task 3.4: Document-Conversation Association
- [ ] Many-to-many relationship
- [ ] Współdzielenie dokumentów między konwersacjami
- [ ] UI: wybór istniejących dokumentów
- [ ] UI: usuwanie powiązań

### Phase 4: Gemini Live Integration (2-3 dni)

#### Task 4.1: Context Building
- [ ] Pobieranie conversation context
- [ ] Pobieranie document context (RAG query)
- [ ] Merge contexts
- [ ] Formatowanie system instructions

#### Task 4.2: VoiceClientManager Extension
- [ ] Modyfikacja startConversation()
- [ ] Przekazywanie conversationId
- [ ] Automatyczne zapisywanie transkrypcji
- [ ] Real-time context updates (opcjonalne)

#### Task 4.3: UI Updates
- [ ] InCallLayout - document/history indicators
- [ ] Buttons: Documents, History
- [ ] Navigation do nowych screens
- [ ] Loading states


### Phase 5: Advanced Features (3-4 dni)

#### Task 5.1: Session Summaries (LLM-based)
- [ ] Auto-generate summary po zakończeniu każdej sesji
- [ ] Gemini Flash API integration (2-3 sekundy, ~$0.0005/sesja)
- [ ] Zapis summary w sessions.summary
- [ ] Meta-summaries: co 10 sesji → conversation.meta_summary
- [ ] Background processing dla meta-summaries
- [ ] UI: wyświetlanie session summaries w historii
- [ ] Manual trigger: user może regenerować summary
- [ ] Opcjonalne: cleanup pełnych transkrypcji po X dniach (zostaw tylko summaries)

#### Task 5.2: Search & History UI
- [ ] ConversationHistoryScreen
- [ ] Search functionality
- [ ] ConversationDetailScreen
- [ ] Export conversation

#### Task 5.3: Offline Mode
- [ ] Network detection
- [ ] Offline indicator UI
- [ ] Graceful degradation
- [ ] Auto-sync po powrocie online

#### Task 5.4: LibreChat Integration (opcjonalne)
- [ ] Shared conversation ID format
- [ ] Import konwersacji z LibreChat
- [ ] Export do LibreChat
- [ ] Sync documents

### Phase 6: Testing & Polish (2-3 dni)

#### Task 6.1: Integration Testing
- [ ] End-to-end testy
- [ ] Performance testing
- [ ] Memory leak testing
- [ ] Network failure scenarios

#### Task 6.2: Error Handling
- [ ] User-friendly error messages
- [ ] Retry dialogs
- [ ] Logging
- [ ] Crash reporting

#### Task 6.3: UI/UX Polish
- [ ] Animations
- [ ] Loading states
- [ ] Empty states
- [ ] Accessibility

#### Task 6.4: Documentation
- [ ] User guide
- [ ] Developer documentation
- [ ] API documentation
- [ ] Deployment guide


## Technical Considerations

### Performance

**SQLite Optimization:**
- Indeksy na: conversation_id, timestamp, synced_to_vertex
- VACUUM regularnie dla BLOB cleanup
- Pagination dla długich konwersacji
- Lazy loading dokumentów

**Vertex AI Rate Limits:**
- Embeddings API: 600 requests/minute
- Vector Search: 1000 queries/minute
- RAG API: 300 requests/minute
- Implementacja rate limiting w client

**Background Processing:**
- Batch operations (max 100 messages/batch)
- Exponential backoff dla retries
- Battery optimization - sync tylko na WiFi/charging (opcjonalnie)

### Security

**API Keys:**
- Service account credentials w encrypted SharedPreferences
- Nigdy w kodzie źródłowym
- Rotacja kluczy co 90 dni

**Data Privacy:**
- Szyfrowanie SQLite database (SQLCipher opcjonalnie)
- HTTPS dla wszystkich API calls
- User consent dla cloud sync
- Możliwość wyłączenia Vertex sync (tylko local)

**File Validation:**
- Whitelist MIME types
- Max file size: 50MB
- Virus scanning (opcjonalnie przez Cloud Storage)
- Sanitization file names


### Cost Estimation (miesięcznie dla 1 użytkownika)

**Vertex AI Embeddings:**
- 1000 wiadomości/miesiąc × 200 znaków = 200k znaków
- $0.025 / 1k znaków = $5

**Vertex AI Vector Search:**
- Index storage: 1M wektorów × $0.10 = $0.10
- Queries: 3000/miesiąc × $0.10/1M = $0.0003

**Vertex AI RAG:**
- Storage: 10 dokumentów × 5MB = 50MB × $0.02/GB = $0.001
- Queries: 100/miesiąc × $0.50/1M tokens = $0.05

**Total: ~$5-6/miesiąc/użytkownik**

### Scalability

**SQLite Limits:**
- Max database size: 281 TB (praktycznie unlimited)
- Max BLOB size: 2GB (ale używamy 50MB limit)
- Concurrent readers: unlimited
- Concurrent writers: 1 (ale wystarczy dla mobile)

**Vertex AI Limits:**
- Vector Search: 10M wektorów/index (wystarczy na lata)
- RAG: 10k dokumentów/corpus
- Embeddings: 20k tokens/request

**Recommendations:**
- Archiwizacja starych konwersacji (>1 rok)
- Cleanup nieużywanych dokumentów
- Monitoring storage usage


## Dependencies (Nowe)

### Gradle Dependencies

```kotlin
// Room Database
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// WorkManager
implementation("androidx.work:work-runtime-ktx:2.9.0")

// Vertex AI SDK
implementation("com.google.cloud:google-cloud-aiplatform:3.35.0")
implementation("com.google.cloud:google-cloud-vertexai:0.5.0")

// Networking
implementation("com.squareup.retrofit2:retrofit:2.9.0")
implementation("com.squareup.retrofit2:converter-gson:2.9.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// File handling
implementation("androidx.documentfile:documentfile:1.0.1")

// Optional: SQLCipher for encryption
implementation("net.zetetic:android-database-sqlcipher:4.5.4")
```

### GCP Setup Requirements

1. **Google Cloud Project**
   - Enabled APIs:
     - Vertex AI API
     - Cloud Storage API (dla RAG)
   
2. **Service Account**
   - Roles:
     - Vertex AI User
     - Storage Object Admin (dla RAG)
   - JSON key file

3. **Vertex AI Resources**
   - Vector Search Index
   - Vector Search Endpoint
   - RAG Corpus (może być tworzony dynamicznie)


## Testing Strategy

### Unit Tests
- DAOs (Room)
- Repositories
- ViewModels
- Utility functions
- Coverage target: >80%

### Integration Tests
- SQLite ↔ Vertex sync
- Document upload flow
- Context retrieval
- Background workers

### UI Tests (Compose)
- DocumentManagerScreen
- ConversationHistoryScreen
- File picker flow
- Search functionality

### Manual Testing Scenarios
1. **Happy Path**: Dodaj dokument → rozpocznij konwersację → sprawdź kontekst
2. **Offline Mode**: Wyłącz internet → dodaj wiadomości → włącz → sprawdź sync
3. **Large Files**: Upload 50MB pliku → sprawdź progress → verify w Vertex
4. **Error Recovery**: Symuluj network failure → sprawdź retry logic
5. **Multiple Conversations**: Przełączaj między konwersacjami → verify context isolation

### Performance Tests
- 1000 wiadomości w SQLite - query time
- 100 dokumentów - list rendering
- Background sync - battery impact
- Memory usage podczas upload 50MB


## Risk Assessment

### High Risk
1. **Vertex AI API Changes** - Google może zmienić API
   - Mitigation: Version pinning, monitoring deprecation notices
   
2. **Cost Overrun** - Użytkownicy mogą generować wysokie koszty
   - Mitigation: Rate limiting, usage monitoring, user quotas

3. **Data Loss** - Błąd w sync może stracić dane
   - Mitigation: Backup strategy, transaction safety, retry logic

### Medium Risk
1. **Performance Issues** - Duże pliki mogą spowalniać app
   - Mitigation: Background processing, pagination, lazy loading

2. **Network Reliability** - Słabe połączenie może powodować failures
   - Mitigation: Robust retry logic, offline mode, queue system

3. **Storage Limits** - Urządzenie może zabraknie miejsca
   - Mitigation: Storage monitoring, cleanup old data, user warnings

### Low Risk
1. **UI/UX Issues** - Użytkownicy mogą nie rozumieć funkcji
   - Mitigation: Onboarding, tooltips, documentation

2. **Compatibility** - Różne wersje Android
   - Mitigation: Min SDK 26, testing na różnych devices

## Success Metrics

### Technical Metrics
- SQLite query time: <5ms dla recent messages
- Vertex sync success rate: >95%
- Document upload success rate: >90%
- App crash rate: <0.1%
- Background sync battery impact: <5%

### User Metrics
- Average documents per conversation: 2-3
- Context retrieval accuracy: >85% (user feedback)
- Offline mode usage: 10-20% sessions
- Feature adoption rate: >50% active users


## Timeline Summary

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| Phase 1: Foundation | 3-4 dni | GCP setup |
| Phase 2: Conversation Memory | 4-5 dni | Phase 1 |
| Phase 3: RAG Document System | 4-5 dni | Phase 1 |
| Phase 4: Gemini Live Integration | 2-3 dni | Phase 2, 3 |
| Phase 5: Advanced Features | 3-4 dni | Phase 4 |
| Phase 6: Testing & Polish | 2-3 dni | Phase 5 |
| **Total** | **18-24 dni** | |

**Note:** Phase 2 i Phase 3 mogą być realizowane równolegle przez różnych developerów.

## Open Questions

1. **LibreChat Integration Details**
   - Jaki format danych używa LibreChat?
   - Czy LibreChat ma API do sync?
   - Czy potrzebny jest custom backend dla sync?

2. **Session Summaries & Context** ✅ RESOLVED
   - **Kiedy:** Automatycznie po zakończeniu każdej sesji Gemini Live (jeśli >2 min)
   - **Model:** Gemini 1.5 Flash (koszt ~$0.0005/sesja, 2-3 sekundy)
   - **Strategia:** 
     - Per-session summary (natychmiast po sesji)
     - Meta-summary co 10 sesji (conversation-level overview)
   - **Context dla nowej sesji (HYBRID):**
     - **Pełna transkrypcja ostatniej sesji** (szczegóły, świeża pamięć)
     - Summaries poprzednich 5-10 sesji (kontekst historyczny)
     - Conversation meta-summary (big picture)
     - Semantic matches z Vertex (2-3 relevantne stare sesje)
   - **Token budget:** Max 30k tokens, automatic trimming jeśli przekracza
   - **Storage:** Session-based, pełne transkrypcje + summaries
   - **User control:** Może regenerować summary, może wyłączyć auto-summary
   - **Cleanup:** Opcjonalnie usuwanie pełnych transkrypcji po 30 dniach (zostają summaries)

3. **Document Sharing** ✅ RESOLVED
   - **Wszystkie dokumenty są shared** między konwersacjami tego samego użytkownika
   - **Jeden shared Vertex RAG corpus** dla wszystkich dokumentów
   - **Opcjonalne:** Tagging system do organizacji (np. #work, #personal)
   - **Między użytkownikami:** Nie w MVP, możliwe w przyszłości

4. **Data Retention**
   - Jak długo przechowywać dane w Vertex?
   - Automatyczne archiwizowanie starych konwersacji?
   - User control nad data retention?

5. **Authentication**
   - Czy każdy user ma własny GCP project?
   - Czy shared project z multi-tenancy?
   - Jak zarządzać service account keys?

## Next Steps

1. **Review tego spec** z zespołem
2. **Odpowiedź na Open Questions**
3. **GCP Setup** - utworzenie resources
4. **Proof of Concept** - Phase 1 + basic Phase 2
5. **User Testing** - feedback na PoC
6. **Full Implementation** - Phase 2-6

---

**Status:** Draft - czeka na review
**Last Updated:** 2025-11-17
**Author:** Kiro AI Assistant
