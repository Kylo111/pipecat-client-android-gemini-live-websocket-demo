# Design Document: Vertex AI RAG Engine + Gemini Live API Integration

## Overview

This document describes the technical design for integrating Vertex AI RAG Engine with Gemini Live API in the Android voice assistant application. The integration enables the AI model to autonomously retrieve information from user documents and marketplace template knowledge bases during real-time voice conversations.

### Key Design Decisions

1. **Backend-First Architecture**: All Vertex AI RAG operations go through a Backend RAG Service (no service account keys on mobile)
2. **Two Independent Channels**: 
   - Android ↔ Gemini Live API (WebSocket) - direct connection for voice/retrieval
   - Android ↔ Backend RAG Service (HTTPS) - for corpus/file management
   - Backend does NOT proxy Live API traffic
3. **Lazy Corpus Creation**: Private user corpus created on first document upload, not on app start
4. **Single Corpus Per User**: All user documents in one private corpus; per-conversation scope via DB associations only (no retrieval filtering in v1)
5. **GCS Signed URL Upload**: Large file uploads via signed URLs for scalability
6. **Tool-Use Round-Trip**: Proper BidiGenerateContentToolCall → BidiGenerateContentToolResponse protocol with correct function_responses[] structure

### Security: API Keys and Tokens

**Gemini Live API Authentication:**
- Android connects directly to Gemini Live API using an API key
- Current approach: API key stored in EncryptedSharedPreferences (user-provided)
- Risk mitigation: API key has limited scope (Gemini API only, no Vertex AI admin)

**Future consideration:** If tighter security is needed:
- Backend could issue short-lived session tokens for Live API
- Or: Backend proxies Live API (adds latency, increases complexity)
- Decision: v1 uses direct connection with user-provided API key

**Backend RAG Service Authentication:**
- Android authenticates with Backend using user OAuth tokens or API keys
- Backend uses service account for Vertex AI operations
- No service account credentials on mobile devices

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Android App                                  │
├─────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐ │
│  │ RAGCorpusManager│  │DocumentUpload   │  │ RAGConfiguration    │ │
│  │                 │  │Service          │  │ Repository          │ │
│  │ - corpus cache  │  │ - file picker   │  │ - local cache       │ │
│  │ - associations  │  │ - validation    │  │ - sync with backend │ │
│  └────────┬────────┘  │ - upload status │  └──────────┬──────────┘ │
│           │           └────────┬────────┘             │            │
│           │                    │                      │            │
│  ┌────────▼────────────────────▼──────────────────────▼──────────┐ │
│  │                    BackendRAGClient                            │ │
│  │  - HTTP client for Backend RAG Service                        │ │
│  │  - Auth token management                                       │ │
│  │  - Signed URL requests                                         │ │
│  └────────────────────────────┬──────────────────────────────────┘ │
│                               │                                     │
│  ┌────────────────────────────▼──────────────────────────────────┐ │
│  │                    GeminiClient (Enhanced)                     │ │
│  │  - WebSocket to Gemini Live API                               │ │
│  │  - retrieval tool in setup message                            │ │
│  │  - ToolCall/ToolResponse handling                             │ │
│  │  - Grounding metadata parsing                                  │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │                    UI Components                               │ │
│  │  - RAGSearchIndicator (animation)                             │ │
│  │  - DocumentListView                                            │ │
│  │  - CitationDisplay                                             │ │
│  └───────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                               │
                               │ HTTPS
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Backend RAG Service                               │
├─────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐ │
│  │ CorpusManager   │  │ FileManager     │  │ AuthService         │ │
│  │ - create corpus │  │ - signed URLs   │  │ - user validation   │ │
│  │ - delete corpus │  │ - RAG import    │  │ - permission check  │ │
│  │ - list corpora  │  │ - file delete   │  │ - audit logging     │ │
│  └────────┬────────┘  └────────┬────────┘  └─────────────────────┘ │
│           │                    │                                    │
│  ┌────────▼────────────────────▼────────────────────────────────┐  │
│  │                 Vertex AI RAG Engine Client                   │  │
│  │  - Service Account credentials                                │  │
│  │  - Corpus CRUD operations                                     │  │
│  │  - File import from GCS                                       │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                               │                                     │
│  ┌────────────────────────────▼──────────────────────────────────┐ │
│  │                    Backend Database                            │ │
│  │  - user_corpora (user_id, corpus_resource_name)               │ │
│  │  - template_corpora (template_id, corpus_resource_name)       │ │
│  │  - rag_files (file_id, corpus_id, display_name, status)       │ │
│  └───────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                               │
                               │ Google APIs (HTTP/gRPC)
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Google Cloud Platform                             │
├─────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐ │
│  │ Vertex AI RAG   │  │ Cloud Storage   │  │ Gemini Live API     │ │
│  │ Engine          │  │ (GCS)           │  │                     │ │
│  │ - RAG Corpora   │  │ - Upload bucket │  │ - WebSocket         │ │
│  │ - RAG Files     │  │ - Signed URLs   │  │ - retrieval tool    │ │
│  │ - Retrieval     │  │                 │  │ - grounding         │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. RAGCorpusManager (Android)

Manages corpus associations and caching on the Android side.

**Important:** All methods return cached data. Call `syncWithBackend()` on app start and before critical operations to ensure cache is fresh. Backend DB is the source of truth.

```kotlin
class RAGCorpusManager(
    private val backendClient: BackendRAGClient,
    private val database: AppDatabase
) {
    // Get user's private corpus resource name (CACHE-ONLY; call syncWithBackend first)
    suspend fun getPrivateCorpusResourceName(userId: String): String?
    
    // Get template's global corpus resource name (CACHE-ONLY; call syncWithBackend first)
    suspend fun getTemplateCorpusResourceName(templateId: String): String?
    
    // Associate template corpus with conversation (updates local cache)
    suspend fun associateTemplateCorpus(conversationId: String, templateId: String)
    
    // Sync corpus mappings with backend (MUST be called on app start)
    suspend fun syncWithBackend()
    
    // Check if user has a private corpus (CACHE-ONLY)
    suspend fun hasPrivateCorpus(userId: String): Boolean
}
```

### 2. DocumentUploadService (Android)

Handles document selection, validation, and upload flow.

```kotlin
class DocumentUploadService(
    private val backendClient: BackendRAGClient,
    private val database: AppDatabase,
    private val corpusManager: RAGCorpusManager
) {
    // Supported file types
    val supportedMimeTypes = listOf(
        "application/pdf",
        "text/plain",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/markdown"
    )
    
    // Max file size: 10MB
    val maxFileSizeBytes = 10 * 1024 * 1024L
    
    // Validate file before upload
    fun validateFile(uri: Uri): ValidationResult
    
    // Upload document (returns flow for progress tracking)
    fun uploadDocument(
        uri: Uri,
        conversationId: String
    ): Flow<UploadState>
    
    // Delete document
    suspend fun deleteDocument(documentId: Long): Result<Unit>
    
    // Get documents for conversation
    suspend fun getDocumentsForConversation(conversationId: String): List<DocumentEntity>
}

sealed class UploadState {
    object Pending : UploadState()
    data class Uploading(val progress: Float) : UploadState()
    data class Uploaded(val ragFileId: String) : UploadState()
    data class Failed(val error: String) : UploadState()
}
```

### 3. BackendRAGClient (Android)

HTTP client for communicating with Backend RAG Service.

```kotlin
class BackendRAGClient(
    private val httpClient: OkHttpClient,
    private val authManager: AuthManager
) {
    // Get signed URL for file upload
    suspend fun getSignedUploadUrl(
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long
    ): SignedUrlResponse
    
    // Trigger RAG import after GCS upload
    // Note: conversationId is optional metadata for future per-conversation filtering
    // In v1, it does NOT affect retrieval scope (all user docs in one corpus)
    suspend fun importFileToRAG(
        gcsUri: String,
        displayName: String,
        conversationId: String? = null  // Optional metadata, not used for filtering in v1
    ): ImportResponse
    
    // Delete file from RAG
    suspend fun deleteFile(ragFileId: String): Result<Unit>
    
    // Get user's corpus info
    suspend fun getUserCorpusInfo(): CorpusInfo?
    
    // Get template corpus info
    suspend fun getTemplateCorpusInfo(templateId: String): CorpusInfo?
    
    // Sync all corpus mappings
    suspend fun syncCorpusMappings(): SyncResponse
    
    // Get allowed corpora for session
    suspend fun getAllowedCorpora(
        userId: String,
        templateId: String?
    ): List<String>
}

data class SignedUrlResponse(
    val uploadUrl: String,
    val gcsUri: String,
    val expiresAt: Long
)

data class ImportResponse(
    val ragFileId: String,
    val corpusResourceName: String,
    val status: String
)

data class CorpusInfo(
    val corpusResourceName: String,
    val displayName: String,
    val fileCount: Int
)
```

### 4. GeminiClient (Enhanced)

Extended to support RAG retrieval tool configuration.

**Important:** Android connects directly to Gemini Live API via WebSocket. The Backend RAG Service is a separate channel for corpus/file management only - it does NOT proxy Live API traffic.

```kotlin
class GeminiClient(
    private val apiKey: String,
    private val model: String,
    private val scope: CoroutineScope
) {
    // Existing callbacks...
    var onToolCall: ((String, String, JsonElement) -> Unit)? = null
    
    // New: RAG-specific callbacks
    var onRetrievalStarted: (() -> Unit)? = null
    var onRetrievalCompleted: ((GroundingMetadata?) -> Unit)? = null
    var onRetrievalCancelled: (() -> Unit)? = null
    
    // Enhanced connect with RAG configuration
    suspend fun connect(
        voiceName: String = "Puck",
        systemPrompt: String = "",
        temperature: Float = 0.8f,
        toolDeclarations: List<JsonElement> = emptyList(),
        ragConfig: RAGConfig? = null,  // NEW
        // ... other params
    )
    
    // Send tool response following BidiGenerateContentToolResponse format
    // For managed retrieval tools, the response payload follows the documented
    // tool-response structure for vertex_rag_store (may be empty {} or status-based)
    fun sendToolResponse(functionResponses: List<FunctionResponse>)
}

// Function response matching BidiGenerateContentToolResponse.function_responses[]
data class FunctionResponse(
    val id: String,           // Must match ToolCall id
    val name: String,         // Tool name
    val response: JsonElement // Response payload (structure depends on tool type)
)

// RAG configuration - supports 0..N corpora for future extensibility
data class RAGConfig(
    val corpora: List<CorpusEntry>  // 0..2 in v1, extensible for future
) {
    // Convenience constructor for v1 (private + global)
    constructor(
        privateCorpusResourceName: String?,
        globalCorpusResourceName: String?
    ) : this(
        listOfNotNull(
            privateCorpusResourceName?.let { CorpusEntry(it, CorpusType.PRIVATE) },
            globalCorpusResourceName?.let { CorpusEntry(it, CorpusType.GLOBAL) }
        )
    )
    
    // Select corpus for single-corpus API (fallback strategy)
    fun selectSingleCorpus(): CorpusEntry? {
        // Priority: GLOBAL (template) > PRIVATE (user)
        // Rationale: Template corpus is domain-specific and more relevant
        return corpora.firstOrNull { it.type == CorpusType.GLOBAL }
            ?: corpora.firstOrNull { it.type == CorpusType.PRIVATE }
    }
}

data class CorpusEntry(
    val resourceName: String,
    val type: CorpusType
)

enum class CorpusType {
    PRIVATE,  // User's personal corpus
    GLOBAL    // Template's shared corpus
}

/**
 * Single-Corpus Limitation (v1):
 * 
 * If the Live API supports only one corpus in rag_resources, we use this priority:
 * 1. Template's global corpus (if template has RAG enabled)
 * 2. User's private corpus (fallback)
 * 
 * Edge case: User has personal documents AND uses RAG-enabled template
 * - In v1, template corpus takes priority (user docs not searched)
 * - Future: If API supports multiple corpora, both will be included
 * - Alternative (not in v1): Merge corpora server-side (increases cost/complexity)
 * 
 * This limitation is documented in Out of Scope section of requirements.
 */

data class GroundingMetadata(
    val groundingChunks: List<GroundingChunk>,
    val groundingSupports: List<GroundingSupport>
)

data class GroundingChunk(
    val retrievedContext: RetrievedContext
)

data class RetrievedContext(
    val uri: String,
    val title: String,
    val text: String
)
```

### 5. GeminiProtocol (Enhanced)

Extended to build RAG tool configuration in setup message.

```kotlin
class GeminiProtocol {
    // Build setup message with RAG retrieval tool
    fun buildSetupMessage(
        model: String,
        voiceName: String,
        systemPrompt: String,
        temperature: Float,
        sessionHandle: String?,
        canResumeSession: Boolean,
        toolDeclarations: List<JsonElement>,
        ragConfig: RAGConfig?,  // NEW
        // ... other params
    ): SetupMessage
    
    // Parse grounding metadata from response
    fun parseGroundingMetadata(jsonObject: JsonObject): GroundingMetadata?
    
    // Build retrieval tool configuration for vertex_rag_store
    private fun buildRetrievalTool(ragConfig: RAGConfig): JsonElement
    
    // Build tool response for managed retrieval
    // For vertex_rag_store, retrieval is executed server-side by Vertex AI
    // The response payload format must follow the documented structure
    // (implementation must verify current API requirements)
    fun buildRetrievalToolResponse(callId: String, toolName: String): FunctionResponse
    
    // Serialize BidiGenerateContentToolResponse
    fun serializeToolResponse(functionResponses: List<FunctionResponse>): String
}

/**
 * Tool Response Format Note:
 * 
 * For managed retrieval tools (vertex_rag_store), the retrieval is executed
 * server-side by Vertex AI RAG Engine. The client still needs to send a
 * BidiGenerateContentToolResponse to complete the tool-use round-trip.
 * 
 * CRITICAL: Tool response MUST be sent as a dedicated `toolResponse` message type,
 * NOT as regular text via `clientContent`. The WebSocket message structure is:
 * {
 *   "toolResponse": {
 *     "functionResponses": [
 *       {"id": "call_id", "name": "tool_name", "response": {...}}
 *     ]
 *   }
 * }
 * 
 * The exact response payload structure must be verified against current
 * Live API documentation. It may be:
 * - Empty response: {"id": "...", "name": "...", "response": {}}
 * - Status-based: {"id": "...", "name": "...", "response": {"status": "ok"}}
 * 
 * Implementation MUST include integration test to validate response format.
 */
```

### 6. RAGConfigurationRepository (Android)

Manages RAG configuration persistence and sync.

```kotlin
class RAGConfigurationRepository(
    private val database: AppDatabase,
    private val backendClient: BackendRAGClient
) {
    // Get cached corpus associations
    suspend fun getCachedCorpusAssociations(): List<CorpusAssociation>
    
    // Sync with backend (called on app start)
    suspend fun syncWithBackend(): SyncResult
    
    // Mark association as invalid
    suspend fun markAssociationInvalid(corpusResourceName: String)
    
    // Get RAG config for conversation
    suspend fun getRAGConfigForConversation(
        conversationId: String,
        userId: String
    ): RAGConfig?
}
```

## Data Models

### Database Entities (Android - Room)

```kotlin
// Enhanced DocumentEntity
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "file_name")
    val fileName: String,
    
    @ColumnInfo(name = "display_name")
    val displayName: String,
    
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    
    @ColumnInfo(name = "file_size")
    val fileSize: Long,
    
    @ColumnInfo(name = "vertex_rag_file_id")
    val vertexRagFileId: String? = null,
    
    @ColumnInfo(name = "upload_status")
    val uploadStatus: String = "pending",
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
)

// New: Document-Conversation association
@Entity(
    tableName = "document_conversation_associations",
    primaryKeys = ["document_id", "conversation_id"]
)
data class DocumentConversationAssociation(
    @ColumnInfo(name = "document_id")
    val documentId: Long,
    
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

// New: Corpus cache
@Entity(tableName = "corpus_cache")
data class CorpusCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "corpus_resource_name")
    val corpusResourceName: String,
    
    @ColumnInfo(name = "corpus_type")
    val corpusType: String,  // "private" or "global"
    
    @ColumnInfo(name = "owner_id")
    val ownerId: String,  // user_id or template_id
    
    @ColumnInfo(name = "display_name")
    val displayName: String?,
    
    @ColumnInfo(name = "is_valid")
    val isValid: Boolean = true,
    
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long
)
```

### Enhanced ConversationTemplate

```kotlin
@Serializable
data class ConversationTemplate(
    val id: String,
    val version: Int,
    val title: String,
    val description: String,
    val systemPrompt: String,
    val voiceId: String? = "Puck",
    val temperature: Float = 1.0f,
    val iconIdentifier: String? = null,
    // NEW: RAG configuration
    val ragCorpusResourceName: String? = null,  // Global corpus for this template
    val ragEnabled: Boolean = false
)
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Lazy corpus creation
*For any* user without a private corpus, when they upload their first document OR start their first RAG-enabled conversation, a private corpus SHALL be created and the corpus resource name SHALL be stored.
**Validates: Requirements 1.1, 1.3**

### Property 2: RAG resource ID round-trip
*For any* RAG resource (corpus or file), when created and stored locally, retrieving it by ID SHALL return the same resource name that was originally stored.
**Validates: Requirements 1.3, 2.3**

### Property 3: Document validation
*For any* file selected for upload, the validation SHALL accept files with supported MIME types (PDF, TXT, DOCX, MD) AND size ≤ 10MB, and SHALL reject all others.
**Validates: Requirements 2.1**

### Property 4: Upload status state machine
*For any* document upload, the status SHALL transition only through valid states: pending → uploading → (uploaded | failed), and failed state SHALL allow retry (back to pending).
**Validates: Requirements 2.4, 2.5**

### Property 5: ToolCall-ToolResponse round-trip
*For any* BidiGenerateContentToolCall with a given ID, the client SHALL eventually send exactly one BidiGenerateContentToolResponse with matching call ID (unless a ToolCallCancellation for that ID is received first). If ToolCallCancellation is received, the client SHALL NOT send a ToolResponse for that ID.
**Validates: Requirements 3.4, 3.8**

### Property 6: RAG search animation state machine
*For any* retrieval tool call, the UI animation state SHALL be: hidden → visible (on ToolCall) → hidden (on ToolResponse or Cancellation), with no other transitions.
**Validates: Requirements 4.1, 4.2**

### Property 7: Document-conversation association CRUD
*For any* document-conversation association created, it SHALL be retrievable by conversation ID, and deletion SHALL remove only the association (not the document).
**Validates: Requirements 5.2, 5.4**

### Property 8: Document preservation on association removal
*For any* document, when its conversation association is removed OR when the conversation is deleted, the document SHALL still exist in the user's private corpus.
**Validates: Requirements 5.4, 5.5**

### Property 9: Template corpus association on import
*For any* marketplace template with RAG enabled, when imported, the new conversation SHALL have an association to the template's global corpus resource name.
**Validates: Requirements 6.2**

### Property 10: Citation display based on grounding metadata
*For any* response, citation indicators SHALL be displayed if and only if grounding metadata is present in the response.
**Validates: Requirements 7.1, 7.4**

### Property 11: RAG file ID to display name mapping
*For any* document stored locally, there SHALL exist a mapping from its RAG file ID to its display name, and this mapping SHALL be retrievable for UI display.
**Validates: Requirements 7.2, 7.5**

### Property 12: Configuration persistence across restart
*For any* corpus association stored before app termination, it SHALL be retrievable from local cache after app restart without network access.
**Validates: Requirements 8.1**

### Property 13: Backend sync updates local cache
*For any* corpus resource name change on backend, after sync completes, the local cache SHALL reflect the updated value.
**Validates: Requirements 8.3**

### Property 14: User ID in corpus name
*For any* private corpus created, the corpus resource name SHALL contain the user's unique identifier.
**Validates: Requirements 9.1**

### Property 15: Auth failure disables RAG features
*For any* authentication failure with Backend RAG Service, RAG features (upload, retrieval configuration) SHALL be disabled and an error message SHALL be displayed.
**Validates: Requirements 10.4**

## Error Handling

### Upload Errors

| Error Type | Handling Strategy | User Message |
|------------|-------------------|--------------|
| File too large | Reject before upload | "File exceeds 10MB limit" |
| Unsupported type | Reject before upload | "Unsupported file type" |
| Network error | Retry with backoff | "Upload failed. Tap to retry" |
| GCS upload failed | Retry with backoff | "Upload failed. Tap to retry" |
| RAG import failed | Mark as failed, allow retry | "Processing failed. Tap to retry" |
| Auth expired | Refresh token, retry | (Silent retry) |

### Retrieval Errors

| Error Type | Handling Strategy | User Message |
|------------|-------------------|--------------|
| Corpus not found | Mark invalid, notify user | "Knowledge base unavailable" |
| Retrieval timeout | Hide animation, continue | (Silent, conversation continues) |
| Tool response error | Log, continue conversation | (Silent, conversation continues) |

### Sync Errors

| Error Type | Handling Strategy | User Message |
|------------|-------------------|--------------|
| Network unavailable | Use cached data | (Silent, use cache) |
| Backend error | Retry with backoff | (Silent retry) |
| Corpus deleted | Mark invalid, notify | "Some documents unavailable" |

## Testing Strategy

### Dual Testing Approach

This implementation uses both unit tests and property-based tests:
- **Unit tests**: Verify specific examples, edge cases, and integration points
- **Property-based tests**: Verify universal properties that should hold across all inputs

### Property-Based Testing Library

**Library**: [Kotest Property Testing](https://kotest.io/docs/proptest/property-based-testing.html)
- Kotlin-native property testing
- Integrates with JUnit
- Minimum 100 iterations per property test

### Test Categories

#### 1. Unit Tests
- Document validation (specific file types, sizes)
- Upload state machine transitions
- Grounding metadata parsing
- RAG config building

#### 2. Property-Based Tests
- RAG resource ID round-trip (Property 2)
- Document validation (Property 3)
- Upload status state machine (Property 4)
- ToolCall-ToolResponse round-trip (Property 5)
- Document-conversation association CRUD (Property 7)
- Citation display logic (Property 10)
- File ID to display name mapping (Property 11)
- Configuration persistence (Property 12)

#### 3. Integration Tests
- Full upload flow (Android → Backend → GCS → RAG)
- Retrieval tool configuration in Live API
- Backend sync flow
- rag_resources schema validation (Requirement 3.9)

### Test Annotations

Each property-based test MUST be tagged with:
```kotlin
/**
 * **Feature: vertex-rag-live-integration, Property 2: RAG resource ID round-trip**
 * **Validates: Requirements 1.3, 2.3**
 */
@Test
fun `property - RAG resource ID round-trip`() = runTest {
    // Property test implementation
}
```
