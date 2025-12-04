# Requirements Document

## Introduction

This specification addresses critical gaps in the project's architecture documentation. Current documentation lacks detailed descriptions of:
1. Offline session pipeline with summary-based context building
2. LibreChat session pipeline with learning context integration
3. Complete object model with all methods, fields, and relationships
4. Data flow diagrams for both session types
5. Context building strategy using hybrid approach (last transcript + summaries)

The goal is to create comprehensive, accurate documentation that serves as the single source of truth for understanding the system architecture.

## Glossary

- **System**: The Android voice conversation application
- **Offline Session**: A conversation session that operates without LibreChat integration, storing data locally
- **LibreChat Session**: A conversation session integrated with LibreChat platform for learning context
- **Context Builder**: Component that builds conversation context from previous sessions
- **Session Manager**: Component managing session lifecycle and transcript synchronization
- **Summary**: AI-generated condensed version of a conversation transcript
- **Transcript**: Full record of user and bot speech during a session
- **Meta-Summary**: High-level summary of multiple sessions for a conversation
- **Hybrid Context**: Context built from last full transcript + summaries of previous sessions
- **ContextBuilder**: Component responsible for building context from database
- **OfflineConversationManager**: Component managing offline conversation definitions
- **TranscriptSyncManager**: Component handling reliable transcript delivery with infinite retry
- **OfflineSummaryQueue**: Persistent queue for transcripts/summaries awaiting synchronization

## Requirements

### Requirement 1

**User Story:** As a developer, I want complete documentation of the offline session pipeline, so that I can understand how offline conversations work without LibreChat integration.

#### Acceptance Criteria

1. WHEN a developer reads the offline session documentation THEN the System SHALL describe the complete lifecycle from session start to end
2. WHEN an offline session starts THEN the System SHALL document how ContextBuilder retrieves previous session summaries from the database
3. WHEN an offline session starts THEN the System SHALL document how the system prompt is augmented with conversation history
4. WHEN an offline session ends THEN the System SHALL document how transcripts are saved to the database
5. WHEN an offline session ends THEN the System SHALL document how AI summaries are generated and stored
6. WHEN an offline session ends THEN the System SHALL document the summary-to-clipboard feature
7. WHEN documentation describes context building THEN the System SHALL explain the hybrid approach (last full transcript + previous summaries)
8. WHEN documentation describes context building THEN the System SHALL specify the maximum context length (30,000 characters)
9. WHEN documentation describes context building THEN the System SHALL specify the maximum number of session summaries included (10)
10. WHEN documentation describes session management THEN the System SHALL document the mechanism that enforces transcript size limit (max 10,000 TranscriptEntry objects) to prevent memory overflow

### Requirement 2

**User Story:** As a developer, I want complete documentation of the LibreChat session pipeline, so that I can understand how sessions integrate with the LibreChat platform.

#### Acceptance Criteria

1. WHEN a developer reads the LibreChat session documentation THEN the System SHALL describe the complete lifecycle from session start to end
2. WHEN a LibreChat session starts THEN the System SHALL document how learning context is fetched from LibreChat API
3. WHEN a LibreChat session starts THEN the System SHALL document the fallback behavior when API is unavailable
4. WHEN a LibreChat session is active THEN the System SHALL document how transcripts are captured in memory
5. WHEN a LibreChat session ends THEN the System SHALL document the TranscriptSyncManager infinite retry mechanism
6. WHEN a LibreChat session ends THEN the System SHALL document the OfflineSummaryQueue persistence strategy
7. WHEN a LibreChat session ends THEN the System SHALL document the exponential backoff algorithm (1s, 2s, 4s, 8s, 16s, 30s max)
8. WHEN a LibreChat session ends THEN the System SHALL document how summaries are generated and sent instead of transcripts
9. WHEN a LibreChat session ends THEN the System SHALL document the minimum session thresholds (30s duration, 2 entries, 50 chars)
10. WHEN documentation describes audio processing THEN the System SHALL document the Voice Activity Detection (VAD) parameters and the gate mechanism used to prevent acoustic echo
11. WHEN documentation describes the Gemini connection THEN the System SHALL document the WebSocket setup message structure and how system_instruction is formatted and sent

### Requirement 3

**User Story:** As a developer, I want complete documentation of all domain objects, so that I can understand the data model and relationships.

#### Acceptance Criteria

1. WHEN a developer reads the domain model documentation THEN the System SHALL document every class with its role, fields, and methods
2. WHEN documentation describes a class THEN the System SHALL include all public and private fields with types and invariants
3. WHEN documentation describes a class THEN the System SHALL include all public methods with signatures, preconditions, postconditions, and side-effects
4. WHEN documentation describes a class THEN the System SHALL include code references with file paths and line numbers
5. WHEN documentation describes relationships THEN the System SHALL specify composition, aggregation, and dependency relationships
6. WHEN documentation describes lifecycle THEN the System SHALL specify creation, usage, and destruction phases
7. WHEN documentation describes testability THEN the System SHALL list required mocks and edge cases

### Requirement 4

**User Story:** As a developer, I want detailed data flow diagrams, so that I can visualize how data moves through the system.

#### Acceptance Criteria

1. WHEN a developer views data flow diagrams THEN the System SHALL provide separate diagrams for offline and LibreChat sessions
2. WHEN a diagram shows offline session flow THEN the System SHALL include ContextBuilder, database queries, and summary generation
3. WHEN a diagram shows LibreChat session flow THEN the System SHALL include API calls, TranscriptSyncManager, and OfflineSummaryQueue
4. WHEN a diagram shows context building THEN the System SHALL illustrate the hybrid approach with last transcript and summaries
5. WHEN a diagram shows session end THEN the System SHALL illustrate the decision tree for generating transcripts vs summaries
6. WHEN a diagram shows retry logic THEN the System SHALL illustrate the exponential backoff and infinite retry mechanism
7. WHEN a developer views diagrams THEN the System SHALL provide a State Machine Diagram for the Session object (Created → Recording → Paused → Finalizing → Summarizing → Archived)

### Requirement 5

**User Story:** As a developer, I want documentation of the ContextBuilder component, so that I can understand how conversation history is built.

#### Acceptance Criteria

1. WHEN a developer reads ContextBuilder documentation THEN the System SHALL describe the hybrid context strategy
2. WHEN ContextBuilder builds context THEN the System SHALL document how it queries the database for sessions
3. WHEN ContextBuilder builds context THEN the System SHALL document how it formats the last full transcript
4. WHEN ContextBuilder builds context THEN the System SHALL document how it includes summaries of previous sessions
5. WHEN ContextBuilder builds context THEN the System SHALL document the context length limit and truncation strategy
6. WHEN ContextBuilder cleans up THEN the System SHALL document the session retention policy (50 sessions max) and the 30-day cleanup mechanism in SessionManager, clarifying which takes precedence
7. WHEN ContextBuilder provides stats THEN the System SHALL document the ContextStats data structure

### Requirement 6

**User Story:** As a developer, I want documentation of the TranscriptSyncManager component, so that I can understand reliable transcript delivery.

#### Acceptance Criteria

1. WHEN a developer reads TranscriptSyncManager documentation THEN the System SHALL describe the infinite retry mechanism
2. WHEN TranscriptSyncManager syncs THEN the System SHALL document the exponential backoff algorithm
3. WHEN TranscriptSyncManager syncs THEN the System SHALL document the OfflineSummaryQueue persistence
4. WHEN TranscriptSyncManager syncs THEN the System SHALL document the SyncStatus state machine
5. WHEN TranscriptSyncManager is cancelled THEN the System SHALL document that content remains in queue
6. WHEN TranscriptSyncManager processes queue THEN the System SHALL document the batch processing on app start
7. WHEN describing persistence THEN the System SHALL document where the OfflineSummaryQueue is serialized (SharedPreferences) and how it survives an application process kill/restart

### Requirement 7

**User Story:** As a developer, I want documentation of the summary generation feature, so that I can understand how AI summaries are created.

#### Acceptance Criteria

1. WHEN a developer reads summary documentation THEN the System SHALL describe the GeminiSummaryService component
2. WHEN summary is generated THEN the System SHALL document the custom prompt priority (offline > Room > global)
3. WHEN summary is generated THEN the System SHALL document the model selection (default: gemini-2.5-flash)
4. WHEN summary is generated THEN the System SHALL document the infinite retry mechanism
5. WHEN summary is generated THEN the System SHALL document the clipboard copy feature
6. WHEN summary is generated THEN the System SHALL document the database storage
7. WHEN summary mode is disabled THEN the System SHALL document that full transcripts are sent instead

### Requirement 8

**User Story:** As a developer, I want documentation of the OfflineConversationManager component, so that I can understand offline conversation management.

#### Acceptance Criteria

1. WHEN a developer reads OfflineConversationManager documentation THEN the System SHALL describe SharedPreferences storage
2. WHEN offline conversations are managed THEN the System SHALL document the help conversation (system conversation)
3. WHEN offline conversations are created THEN the System SHALL document the OfflineConversation data structure
4. WHEN offline conversations are deleted THEN the System SHALL document the cascade deletion (SharedPreferences + Room database)
5. WHEN offline conversations are deleted THEN the System SHALL document the protection of system conversations
6. WHEN documentation describes data consistency THEN the System SHALL explain how synchronization integrity is maintained between SharedPreferences (metadata) and Room Database (content), specifically during deletion or ID updates

### Requirement 9

**User Story:** As a developer, I want sequence diagrams for all major workflows, so that I can understand component interactions.

#### Acceptance Criteria

1. WHEN a developer views sequence diagrams THEN the System SHALL provide diagrams for offline session start
2. WHEN a developer views sequence diagrams THEN the System SHALL provide diagrams for offline session end
3. WHEN a developer views sequence diagrams THEN the System SHALL provide diagrams for LibreChat session start
4. WHEN a developer views sequence diagrams THEN the System SHALL provide diagrams for LibreChat session end
5. WHEN a developer views sequence diagrams THEN the System SHALL provide diagrams for context building
6. WHEN a developer views sequence diagrams THEN the System SHALL provide diagrams for transcript synchronization with retry
7. WHEN a developer views sequence diagrams THEN the System SHALL provide diagrams for summary generation

### Requirement 10

**User Story:** As a developer, I want documentation of all database entities and repositories, so that I can understand data persistence.

#### Acceptance Criteria

1. WHEN a developer reads database documentation THEN the System SHALL document SessionEntity with all fields
2. WHEN a developer reads database documentation THEN the System SHALL document ConversationEntity with all fields
3. WHEN a developer reads database documentation THEN the System SHALL document SessionRepository with all methods
4. WHEN a developer reads database documentation THEN the System SHALL document ConversationRepository with all methods
5. WHEN a developer reads database documentation THEN the System SHALL document foreign key relationships and cascade rules
6. WHEN a developer reads database documentation THEN the System SHALL document the database schema with table definitions
7. WHEN documentation describes TranscriptEntry THEN the System SHALL document that it is a serialized data structure stored within the SessionEntity transcript field (not a separate table)

### Requirement 11

**User Story:** As a developer, I want documentation on technical constraints and concurrency, so that I can modify the code without introducing crashes or memory leaks.

#### Acceptance Criteria

1. WHEN documentation describes I/O operations THEN the System SHALL specify the threading model (Coroutines/Dispatchers) used to prevent Main Thread blocking
2. WHEN documentation describes memory management THEN the System SHALL document the hard limits on transcript entries (10,000 limit) and how the system prunes old entries during active recording
3. WHEN documentation describes audio handling THEN the System SHALL document the VAD (Voice Activity Detection) logic and echo cancellation strategy (software gating)
4. WHEN documentation describes methods performing I/O (Database, Network) THEN the System SHALL specify whether they are suspend functions, use Dispatchers.IO, or callback interfaces
5. WHEN documentation describes Gemini API interaction THEN the System SHALL document handling of specific API errors such as Safety Ratings (blocked content) or Quota Exceeded, and whether these trigger retry or permanent failure
