# Requirements Document

## Introduction

This document specifies the requirements for integrating Vertex AI RAG Engine with Gemini Live API in the Android voice assistant application. The integration enables the AI model to autonomously decide when to retrieve information from user-uploaded documents and marketplace template knowledge bases during real-time voice conversations.

The system supports two types of RAG corpora:
1. **Private User Corpora** - One corpus per user containing their personal documents (lazy-created on first use)
2. **Global Template Corpora** - Shared corpora associated with marketplace assistant templates (managed by admin)

**Architecture Decision:** All Vertex AI RAG operations are performed by a Backend RAG Service using service account credentials. The Android app never stores service account keys - it communicates with the backend via authenticated API calls. This ensures security, auditability, and proper IAM control.

The Gemini Live API will use the `retrieval` tool with `vertex_rag_store` configuration, allowing the model to automatically invoke RAG when relevant to the conversation.

## Glossary

- **RAG (Retrieval-Augmented Generation)**: A technique that enhances AI responses by retrieving relevant information from external knowledge bases
- **Vertex AI RAG Engine**: Google Cloud service for managing RAG corpora and performing semantic retrieval
- **RAG Corpus**: A collection of documents indexed for semantic search in Vertex AI
- **RAG File**: A single document uploaded to a RAG corpus
- **Corpus Resource Name**: Full GCP resource path (e.g., `projects/{project}/locations/{location}/ragCorpora/{corpus_id}`)
- **Retrieval Tool**: Gemini Live API tool configuration that enables automatic RAG invocation
- **Tool Call**: Event emitted by Gemini when it decides to use a tool (including retrieval)
- **Tool Response**: Message sent back to Gemini after tool execution (BidiGenerateContentToolResponse)
- **Grounding Metadata**: Response metadata containing source citations from RAG retrieval
- **Grounding Chunk**: A text fragment retrieved from RAG corpus, included in grounding metadata
- **Private Corpus**: User-specific RAG corpus containing personal documents
- **Global Corpus**: Shared RAG corpus associated with a marketplace template
- **Backend RAG Service**: Server-side service that performs Vertex AI operations using service account credentials
- **System of Record**: The authoritative source of data (Backend DB for corpus mappings)

## Data Architecture Decision

**Source of Truth:** The Backend RAG Service database is the system of record for all corpus mappings (`user_id -> private_corpus_resource_name`, `template_id -> global_corpus_resource_name`). The Android app maintains a local cache of these mappings for UI display and offline access, but always syncs with backend on startup and before RAG operations.

## Out of Scope

The following features are explicitly out of scope for this initial implementation:
- **Per-conversation retrieval filtering** - May be added later depending on Vertex AI RAG Engine tool support for metadata filters in Live API
- **Real-time document indexing status** - Documents are considered available after successful RAG import completes; indexing progress is not tracked. UI may show "processing" state until import callback is received (import may be async depending on API)
- **Multi-region corpus replication** - Single region deployment initially
- **Document versioning** - Re-upload creates new file, old version is deleted

## Requirements

### Requirement 1: RAG Corpus Management

**User Story:** As a system administrator, I want to manage RAG corpora programmatically, so that the application can automatically create and maintain knowledge bases for users and templates.

#### Acceptance Criteria

1. WHEN a user uploads their first document OR starts their first RAG-enabled conversation THEN the Backend_RAG_Service SHALL create a private RAG corpus for that user in Vertex AI (lazy creation)
2. WHEN a marketplace template with RAG files is imported THEN the RAG_Corpus_Manager SHALL associate the template's global corpus resource name with the user's conversation in the local database
3. WHEN a corpus is created THEN the Backend_RAG_Service SHALL return the corpus resource name and the Android app SHALL store it in the local database
4. WHEN querying corpus associations THEN the RAG_Corpus_Manager SHALL retrieve corpus resource names from the local database without calling Vertex AI API
5. IF corpus creation fails THEN the Backend_RAG_Service SHALL retry with exponential backoff and return an error to the Android app

### Requirement 2: Document Upload to RAG

**User Story:** As a user, I want to upload documents to my conversations, so that the AI can reference them during our voice interactions.

#### Acceptance Criteria

1. WHEN a user selects a document to upload THEN the Document_Upload_Service SHALL validate the file type and size before processing
2. WHEN a valid document is selected THEN the Android app SHALL upload the file to Backend_RAG_Service, which SHALL then import the file to the user's private RAG corpus via GCS (signed URL upload → GCS → RAG import)
3. WHEN the upload completes successfully THEN the Backend_RAG_Service SHALL return the RAG file ID and the Android app SHALL cache it in the local database
4. WHEN displaying upload progress THEN the Document_Upload_Service SHALL show upload status (pending, uploading, uploaded, failed)
5. IF document upload fails THEN the Document_Upload_Service SHALL display an error message and allow retry
6. WHEN a document is deleted THEN the Android app SHALL request deletion from Backend_RAG_Service, which SHALL remove the file from Vertex AI RAG corpus and update backend DB

**Upload Strategy:** Files are uploaded via GCS signed URLs for better scalability and reliability with large files. The flow is: Android → Backend (get signed URL) → GCS (upload bytes) → Backend (trigger RAG import from GCS).

**File Constraints:**
- Supported types: PDF, TXT, DOCX, MD (text-based documents)
- Maximum file size: 10MB per file
- GCS storage: Shared bucket with user_id prefix (e.g., `gs://rag-uploads/{user_id}/{file_id}`)
- Retention: GCS object deleted after successful RAG import (RAG Engine stores indexed content)

### Requirement 3: RAG Tool Configuration in Live API

**User Story:** As a developer, I want to configure the retrieval tool in Gemini Live API setup, so that the model can automatically access RAG corpora during conversations.

#### Acceptance Criteria

1. WHEN starting a conversation with RAG-enabled template THEN the Gemini_Client SHALL include the retrieval tool with vertex_rag_store configuration in the setup message
2. WHEN configuring retrieval tool THEN the Session_Manager SHALL build rag_resources following the current Live API schema. Implementation MUST verify whether the API supports multiple corpus entries (list) or single corpus. IF the API supports only one corpus THEN the Session_Manager SHALL choose template global corpus (if available) OR user's private corpus (fallback)
3. WHEN the model invokes retrieval THEN the Gemini_Client SHALL receive a BidiGenerateContentToolCall event
4. WHEN a retrieval ToolCall is received THEN the Gemini_Client SHALL send a BidiGenerateContentToolResponse containing the tool output as specified by the tool schema, mapped to the call ID (this is a required round-trip, not just an acknowledgment)
5. WHEN retrieval completes THEN the Gemini_Client SHALL receive grounding metadata with source citations in the response
6. IF retrieval is cancelled by the model THEN the Gemini_Client SHALL receive a BidiGenerateContentToolCallCancellation event and stop waiting for response
7. WHEN both private and global corpora are configured AND the API supports multiple corpora THEN the retrieval SHALL search across both corpora and merge results (expected behavior: relevance-based ranking, subject to Vertex retrieval ranking)
8. WHEN sending tool response for retrieval THEN the Gemini_Client SHALL send the tool output exactly in the structure expected by the retrieval/vertex_rag_store tool. IF retrieval is fully managed server-side (no client execution required) THEN the client SHALL follow the documented tool-response payload format for managed retrieval tools
9. WHEN implementing rag_resources configuration THEN the implementation SHALL include an automated integration test that validates the selected rag_resources shape against the current Live API schema to catch schema changes early

**Note:** The retrieval tool in Gemini Live API with Vertex RAG Engine handles the actual retrieval server-side. The client configures which corpora to search via rag_resources in the setup message. Implementation must follow the current Live API schema for rag_resources format.

### Requirement 4: RAG Tool Call UI Feedback

**User Story:** As a user, I want visual feedback when the AI is searching my documents, so that I understand why there might be a brief pause in the conversation.

#### Acceptance Criteria

1. WHEN a retrieval tool call is received THEN the UI SHALL display a "searching documents" animation
2. WHEN the retrieval completes or is cancelled THEN the UI SHALL hide the searching animation
3. WHEN displaying the animation THEN the UI SHALL use a distinct visual style from the speaking indicator
4. WHEN retrieval takes longer than 3 seconds THEN the UI SHALL display a text hint "Searching your documents..."

### Requirement 5: Conversation-Document Association

**User Story:** As a user, I want to attach documents to specific conversations, so that each assistant has access to relevant knowledge.

#### Acceptance Criteria

1. WHEN viewing a conversation THEN the UI SHALL display a list of attached documents with their names and upload status
2. WHEN adding a document to a conversation THEN the Conversation_Document_Service SHALL create an association in the database linking document ID to conversation ID
3. WHEN starting a conversation with attached documents THEN the Session_Manager SHALL include the user's private corpus in the RAG configuration (all user documents are in one corpus; filtering is done by metadata if needed)
4. WHEN removing a document from a conversation THEN the Conversation_Document_Service SHALL remove the association but preserve the document in the user's private corpus
5. WHEN a conversation is deleted THEN the Conversation_Document_Service SHALL remove document associations but preserve documents in the user's private corpus

**Note:** Documents are stored in a single private corpus per user. Per-conversation document scope is achieved through database associations for UI organization purposes. In the initial implementation, retrieval searches the entire user's private corpus. Per-conversation filtering via metadata may be added in a future version if Vertex AI RAG Engine supports metadata filters in the Live API retrieval tool.

### Requirement 6: Marketplace Template RAG Integration

**User Story:** As a user, I want marketplace templates to include pre-configured knowledge bases, so that specialized assistants have domain expertise.

#### Acceptance Criteria

1. WHEN a marketplace template defines RAG files THEN the Template_Configuration SHALL include the global corpus resource name
2. WHEN importing a template with RAG THEN the Import_Service SHALL associate the global corpus with the new conversation
3. WHEN starting a conversation from RAG-enabled template THEN the Session_Manager SHALL include both user private corpus and template global corpus in retrieval configuration
4. WHEN multiple templates share a corpus THEN the RAG_Corpus_Manager SHALL reuse the existing corpus reference

### Requirement 7: Grounding Citation Display

**User Story:** As a user, I want to see which documents the AI referenced in its response, so that I can verify the information source.

#### Acceptance Criteria

1. WHEN a response includes grounding metadata THEN the Transcript_Display SHALL show source citation indicators
2. WHEN displaying citations THEN the UI SHALL show the document display name (cached locally) mapped from the RAG file ID returned in grounding metadata
3. WHEN a citation is tapped THEN the UI SHALL show the grounding chunk text exactly as returned in grounding metadata (not the full document)
4. IF no grounding metadata is present THEN the UI SHALL display the response without citation indicators
5. WHEN caching documents THEN the Document_Repository SHALL store document display name locally and map it to the RAG file ID for UI display

**Note:** Grounding metadata contains chunks (text fragments) retrieved from the corpus, not full documents. The UI displays these chunks as-is when citations are expanded.

### Requirement 8: RAG Configuration Persistence

**User Story:** As a user, I want my RAG settings to persist across app restarts, so that I don't need to reconfigure document access.

#### Acceptance Criteria

1. WHEN the app restarts THEN the RAG_Configuration_Repository SHALL restore corpus associations from the local cache for immediate UI display
2. WHEN the app starts with network connectivity THEN the RAG_Configuration_Repository SHALL sync with Backend_RAG_Service to refresh corpus mappings (backend DB is source of truth)
3. WHEN a corpus resource name changes on backend THEN the sync SHALL update the local cache
4. IF a corpus no longer exists on backend THEN the RAG_Configuration_Repository SHALL mark the association as invalid in local cache and notify the user

**Note:** Backend DB is the system of record. Android local database serves as a cache for offline access and fast UI rendering.

### Requirement 9: RAG Corpus Data Isolation

**User Story:** As a user, I want my private documents to be inaccessible to other users, so that my data remains confidential.

#### Acceptance Criteria

1. WHEN creating a private corpus THEN the Backend_RAG_Service SHALL use the user's unique identifier in the corpus name (e.g., `user_{user_id}_private`)
2. WHEN configuring retrieval THEN the Session_Manager SHALL request the allowed corpus list from Backend_RAG_Service (or verify via signed session config) before starting Live session (backend is source of truth for access control, not local cache)
3. WHEN a user account is deleted THEN the Backend_RAG_Service SHALL delete the user's private corpus and all contained files
4. WHEN accessing global template corpora THEN the Backend_RAG_Service SHALL enforce read-only access for non-admin users (users can query but not modify global corpora)

### Requirement 10: Backend RAG Service Authentication

**User Story:** As a developer, I want secure authentication architecture, so that RAG operations are authorized and auditable without exposing credentials on mobile devices.

#### Acceptance Criteria

1. WHEN making Vertex AI API calls THEN the Backend_RAG_Service SHALL use service account credentials (Android app SHALL NOT store service account keys)
2. WHEN the Android app needs RAG operations THEN the app SHALL authenticate with the Backend_RAG_Service using user OAuth tokens or API keys
3. WHEN the Backend_RAG_Service receives a request THEN the service SHALL validate the user's identity and permissions before executing Vertex AI operations
4. IF authentication with Backend_RAG_Service fails THEN the Android app SHALL disable RAG features gracefully and display an appropriate error message
5. WHEN auditing RAG operations THEN the Backend_RAG_Service SHALL log all corpus and file operations with user identity and timestamp
