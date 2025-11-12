# Design Document - LibreChat Session Integration

## Overview

Integracja aplikacji Android z platformą LibreChat w celu stworzenia systemu nauki z kontekstem sesji. System umożliwia użytkownikom wybór tematów nauki (wątków konwersacji), pobieranie przygotowanego kontekstu od agenta LibreChat z wykorzystaniem RAG, prowadzenie sesji głosowych z Gemini Live API, oraz automatyczne generowanie i synchronizację podsumowań sesji.

Kluczowe założenia projektowe:
- Minimalna ingerencja w istniejącą architekturę Pipecat/Gemini
- Lokalne przechowywanie kontekstu sesji (bez Redis/zewnętrznych baz)
- Asynchroniczne operacje sieciowe bez wpływu na audio
- Proste API LibreChat jako źródło kontekstu i cel podsumowań

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Android Application                     │
│                                                               │
│  ┌────────────────┐  ┌──────────────────┐  ┌──────────────┐│
│  │  UI Layer      │  │  Session Layer   │  │  Network     ││
│  │                │  │                  │  │  Layer       ││
│  │ - LoginScreen  │  │ - SessionManager │  │ - LibreChat  ││
│  │ - ThreadList   │  │ - Transcript     │  │   Service    ││
│  │ - InCallLayout │  │   Capture        │  │ - Auth       ││
│  │                │  │ - Summary Gen    │  │   Manager    ││
│  └────────┬───────┘  └────────┬─────────┘  └──────┬───────┘│
│           │                   │                     │        │
│           └───────────────────┴─────────────────────┘        │
│                              │                               │
└──────────────────────────────┼───────────────────────────────┘
                               │
                ┌──────────────┴──────────────┐
                │                             │
        ┌───────▼────────┐          ┌────────▼────────┐
        │  Gemini Live   │          │   LibreChat     │
        │  WebSocket API │          │   REST API      │
        │                │          │                 │
        │ - Audio Stream │          │ - Auth          │
        │ - Multimodal   │          │ - Threads       │
        │ - Transcripts  │          │ - Context       │
        └────────────────┘          │ - Summaries     │
                                    │                 │
                                    │  ┌───────────┐  │
                                    │  │ Learning  │  │
                                    │  │ Agent     │  │
                                    │  │ + RAG     │  │
                                    │  └───────────┘  │
                                    └─────────────────┘
```

### Component Interaction Flow

```
User Journey:

1. Login Flow:
   User → LoginScreen → AuthManager → LibreChat API
                                     ↓
                              Store Token (EncryptedSharedPreferences)

2. Thread Selection:
   User → ThreadListScreen → LibreChatService.getThreads()
                                     ↓
                              Display Threads (MATEMATYKA, BIOLOGIA)

3. Session Start:
   User selects thread → LibreChatService.getContext(conversationId)
                                     ↓
                         Learning Agent (RAG + History Analysis)
                                     ↓
                         Return: {systemPrompt, initialMessage, metadata}
                                     ↓
                         SessionManager.initSession(context)
                                     ↓
                         VoiceClientManager.start(with systemPrompt)

4. During Session:
   User speaks → Gemini Live → Bot responds
        ↓              ↓
   TranscriptCapture  TranscriptCapture
        ↓              ↓
   SessionContext (in-memory)

5. Session End:
   User disconnects → SessionManager.endSession()
                                     ↓
                         SummaryGenerator.generate()
                                     ↓
                         {lessonSummary, parentReport}
                                     ↓
                         LibreChatService.sendSummary()
                                     ↓
                         Learning Agent → Update Memory
                                       → Send Telegram to Parent
```

## Components and Interfaces

### 1. Authentication Layer

#### AuthManager
Zarządza uwierzytelnieniem użytkownika i tokenami.

```kotlin
class AuthManager(private val context: Context) {
    
    private val encryptedPrefs: SharedPreferences
    
    data class AuthCredentials(
        val serverUrl: String,
        val email: String,
        val password: String
    )
    
    data class AuthToken(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAt: Long
    )
    
    suspend fun login(credentials: AuthCredentials): Result<AuthToken>
    suspend fun logout()
    suspend fun refreshToken(): Result<AuthToken>
    fun getStoredToken(): AuthToken?
    fun isTokenValid(): Boolean
}
```

**Implementacja:**
- Używa `EncryptedSharedPreferences` dla bezpiecznego przechowywania tokenów
- Automatyczne odświeżanie tokenu przed wygaśnięciem
- Obsługa błędów 401 (Unauthorized) z automatycznym retry po refresh

#### LoginScreen (Composable)
Ekran logowania do LibreChat.

```kotlin
@Composable
fun LoginScreen(
    authManager: AuthManager,
    onLoginSuccess: () -> Unit
) {
    var serverUrl by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // UI implementation with email and password fields matching LibreChat account
}
```

### 2. LibreChat Integration Layer

#### LibreChatService
Główny serwis komunikacji z LibreChat API.

```kotlin
class LibreChatService(
    private val authManager: AuthManager,
    private val httpClient: OkHttpClient
) {
    
    data class ConversationThread(
        val id: String,
        val title: String,
        val subject: String,
        val lastActivity: Long
    )
    
    data class LearningContext(
        val readyToUseContext: ReadyContext,
        val metadata: ContextMetadata
    )
    
    data class ReadyContext(
        val systemPrompt: String,
        val initialMessage: String,
        val voiceParameters: VoiceParameters
    )
    
    data class VoiceParameters(
        val tone: String,
        val pace: String,
        val style: String
    )
    
    data class ContextMetadata(
        val subject: String,
        val gradeLevel: String,
        val estimatedDuration: String,
        val materialsUsed: List<String>
    )
    
    data class SessionSummary(
        val conversationId: String,
        val lessonSummary: LessonSummary,
        val parentReport: ParentReport
    )
    
    data class LessonSummary(
        val keyTopics: List<String>,
        val studentDifficulties: List<String>,
        val progressAssessment: String,
        val nextSteps: List<String>
    )
    
    data class ParentReport(
        val subject: String,
        val duration: Long,
        val topicsCovered: List<String>,
        val identifiedDifficulties: List<String>,
        val overallPerformance: String
    )
    
    // API Methods
    suspend fun getConversationThreads(): Result<List<ConversationThread>>
    suspend fun getLearningContext(conversationId: String): Result<LearningContext>
    suspend fun sendSessionSummary(summary: SessionSummary): Result<Unit>
}
```

**API Endpoints:**
```
GET  /api/learning/threads
     Response: [{ id, title, subject, lastActivity }]

GET  /api/learning/context/{conversationId}
     Response: { readyToUseContext: {...}, metadata: {...} }

POST /api/learning/summary
     Body: { conversationId, lessonSummary, parentReport }
     Response: { success: true }
```

**Implementacja:**
- Automatyczne dodawanie Authorization header z tokenem
- Retry logic z exponential backoff (3 próby)
- Timeout: 30s dla context, 10s dla innych
- Offline queue dla nieudanych summaries

#### ThreadListScreen (Composable)
Ekran wyboru wątku nauki.

```kotlin
@Composable
fun ThreadListScreen(
    libreChatService: LibreChatService,
    onThreadSelected: (String) -> Unit,
    onLogout: () -> Unit
) {
    var threads by remember { mutableStateOf<List<ConversationThread>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        // Load threads
    }
    
    // UI: Grid of thread buttons (MATEMATYKA, BIOLOGIA, etc.)
}
```

### 3. Session Management Layer

#### SessionManager
Zarządza kontekstem sesji nauki.

```kotlin
class SessionManager(
    private val context: Context,
    private val libreChatService: LibreChatService
) {
    
    data class SessionContext(
        val sessionId: String,
        val conversationId: String,
        val startTime: Long,
        val systemPrompt: String,
        val transcripts: MutableList<TranscriptEntry>,
        val imageEvents: MutableList<ImageEvent>,
        val contextUpdates: MutableList<ContextUpdate>
    )
    
    data class TranscriptEntry(
        val timestamp: Long,
        val speaker: Speaker,
        val text: String
    )
    
    enum class Speaker {
        USER, BOT
    }
    
    data class ImageEvent(
        val timestamp: Long,
        val description: String
    )
    
    data class ContextUpdate(
        val timestamp: Long,
        val additionalContext: String
    )
    
    private var currentSession: SessionContext? = null
    private val maxTranscripts = 10000
    
    suspend fun startSession(conversationId: String): Result<SessionContext>
    fun captureUserTranscript(text: String)
    fun captureBotTranscript(text: String)
    fun recordImageSent(description: String)
    fun updateContext(additionalContext: String)
    suspend fun endSession(): Result<Unit>
    fun getCurrentSession(): SessionContext?
}
```

**Implementacja:**
- `startSession()`: Pobiera kontekst z LibreChat, inicjalizuje SessionContext
- Transkrypcje przechowywane w pamięci (MutableList)
- Limit 10000 wpisów - usuwanie najstarszych przy przekroczeniu
- `endSession()`: Generuje podsumowania i wysyła do LibreChat

#### TranscriptCapture
Integracja z VoiceClientManager do przechwytywania transkrypcji.

**Modyfikacje w VoiceClientManager:**
```kotlin
class VoiceClientManager(
    private val context: Context,
    private val sessionManager: SessionManager? = null
) {
    
    // Existing code...
    
    // Add transcript callbacks
    var onUserTranscript: ((String) -> Unit)? = null
    var onBotTranscript: ((String) -> Unit)? = null
    
    private fun handleTextMessage(text: String) {
        // Existing parsing logic...
        
        // NEW: Capture bot transcripts
        if (jsonObject.containsKey("serverContent")) {
            val serverContent = jsonObject["serverContent"]?.jsonObject
            if (serverContent?.containsKey("modelTurn") == true) {
                // Extract text from model turn
                val text = extractTextFromModelTurn(serverContent)
                if (text.isNotEmpty()) {
                    onBotTranscript?.invoke(text)
                    sessionManager?.captureBotTranscript(text)
                }
            }
        }
    }
    
    // NEW: Capture user transcripts
    // Note: Gemini Live API may not return user transcripts directly
    // Alternative: Use Android SpeechRecognizer for local transcription
    private fun captureUserSpeech(audioData: ByteArray) {
        // Use SpeechRecognizer to transcribe user audio locally
        // Then call: onUserTranscript?.invoke(transcribedText)
    }
}
```

**Uwaga:** Gemini Live API może nie zwracać transkrypcji użytkownika. Rozwiązania:
1. Użyć Android `SpeechRecognizer` do lokalnej transkrypcji audio użytkownika
2. Wysłać żądanie do Gemini o zwracanie transkrypcji w odpowiedziach
3. Zapisywać tylko transkrypcje bota (mniej kompletne, ale prostsze)

**Rekomendacja:** Opcja 1 - lokalna transkrypcja z `SpeechRecognizer`

#### SummaryGenerator
Generuje podsumowania sesji.

```kotlin
class SummaryGenerator {
    
    fun generateLessonSummary(
        transcripts: List<TranscriptEntry>,
        duration: Long
    ): LessonSummary {
        // Analiza transkrypcji:
        // - Wyodrębnienie kluczowych tematów (keyword extraction)
        // - Identyfikacja trudności (pytania użytkownika, powtórzenia)
        // - Ocena postępu (długość odpowiedzi, pewność)
        // - Sugestie następnych kroków
        
        return LessonSummary(
            keyTopics = extractKeyTopics(transcripts),
            studentDifficulties = identifyDifficulties(transcripts),
            progressAssessment = assessProgress(transcripts, duration),
            nextSteps = suggestNextSteps(transcripts)
        )
    }
    
    fun generateParentReport(
        lessonSummary: LessonSummary,
        subject: String,
        duration: Long
    ): ParentReport {
        // Formatowanie dla rodzica:
        // - Prosty język
        // - Pozytywny ton
        // - Konkretne przykłady
        
        return ParentReport(
            subject = subject,
            duration = duration,
            topicsCovered = lessonSummary.keyTopics,
            identifiedDifficulties = lessonSummary.studentDifficulties,
            overallPerformance = formatPerformanceForParent(lessonSummary)
        )
    }
    
    private fun extractKeyTopics(transcripts: List<TranscriptEntry>): List<String>
    private fun identifyDifficulties(transcripts: List<TranscriptEntry>): List<String>
    private fun assessProgress(transcripts: List<TranscriptEntry>, duration: Long): String
    private fun suggestNextSteps(transcripts: List<TranscriptEntry>): List<String>
    private fun formatPerformanceForParent(summary: LessonSummary): String
}
```

**Implementacja:**
- Prosta analiza tekstowa (keyword frequency, sentiment)
- Bez ML - reguły heurystyczne
- Trudności: pytania użytkownika, słowa "nie rozumiem", powtórzenia
- Postęp: długość sesji, liczba tematów, pewność odpowiedzi

### 4. UI Layer Updates

#### Modyfikacje MainActivity

```kotlin
class MainActivity : ComponentActivity() {
    
    private lateinit var authManager: AuthManager
    private lateinit var libreChatService: LibreChatService
    private lateinit var sessionManager: SessionManager
    private lateinit var voiceClientManager: VoiceClientManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize services
        authManager = AuthManager(this)
        libreChatService = LibreChatService(authManager, createHttpClient())
        sessionManager = SessionManager(this, libreChatService)
        voiceClientManager = VoiceClientManager(this, sessionManager)
        
        setContent {
            var currentScreen by remember { mutableStateOf(
                if (authManager.isTokenValid()) Screen.THREAD_LIST else Screen.LOGIN
            )}
            
            RTVIClientTheme {
                when (currentScreen) {
                    Screen.LOGIN -> LoginScreen(
                        authManager = authManager,
                        onLoginSuccess = { currentScreen = Screen.THREAD_LIST }
                    )
                    Screen.THREAD_LIST -> ThreadListScreen(
                        libreChatService = libreChatService,
                        onThreadSelected = { conversationId ->
                            // Start session
                            lifecycleScope.launch {
                                sessionManager.startSession(conversationId)
                                currentScreen = Screen.IN_CALL
                            }
                        },
                        onLogout = {
                            lifecycleScope.launch {
                                authManager.logout()
                                currentScreen = Screen.LOGIN
                            }
                        }
                    )
                    Screen.IN_CALL -> InCallLayout(
                        voiceClientManager = voiceClientManager,
                        onDisconnect = {
                            lifecycleScope.launch {
                                sessionManager.endSession()
                                currentScreen = Screen.THREAD_LIST
                            }
                        }
                    )
                    // ... existing screens
                }
            }
        }
    }
}
```

#### Nowy Screen enum

```kotlin
enum class Screen {
    LOGIN,
    THREAD_LIST,
    CONNECT,      // Existing - for direct Gemini connection
    IN_CALL,
    SETTINGS
}
```

### 5. Offline Support & Error Handling

#### OfflineSummaryQueue
Kolejka nieudanych podsumowań.

```kotlin
class OfflineSummaryQueue(private val context: Context) {
    
    private val maxQueueSize = 10
    private val prefsKey = "offline_summaries"
    
    fun enqueue(summary: SessionSummary)
    fun dequeue(): SessionSummary?
    fun size(): Int
    fun clear()
    
    suspend fun processQueue(libreChatService: LibreChatService) {
        while (size() > 0) {
            val summary = dequeue() ?: break
            val result = libreChatService.sendSessionSummary(summary)
            if (result.isFailure) {
                // Re-enqueue and stop processing
                enqueue(summary)
                break
            }
        }
    }
}
```

**Implementacja:**
- Przechowywanie w SharedPreferences jako JSON
- FIFO queue
- Automatyczne przetwarzanie przy następnym połączeniu

#### RetryPolicy

```kotlin
class RetryPolicy {
    companion object {
        suspend fun <T> withRetry(
            maxAttempts: Int = 3,
            initialDelay: Long = 1000,
            maxDelay: Long = 10000,
            factor: Double = 2.0,
            block: suspend () -> T
        ): Result<T> {
            var currentDelay = initialDelay
            repeat(maxAttempts - 1) { attempt ->
                try {
                    return Result.success(block())
                } catch (e: Exception) {
                    Log.w("RetryPolicy", "Attempt ${attempt + 1} failed: ${e.message}")
                }
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
            // Last attempt
            return try {
                Result.success(block())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
```

## Data Models

### Persistence Models

```kotlin
// Stored in EncryptedSharedPreferences
data class StoredAuthToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,
    val serverUrl: String
)

// Stored in SharedPreferences (JSON)
data class StoredSummary(
    val timestamp: Long,
    val conversationId: String,
    val lessonSummaryJson: String,
    val parentReportJson: String
)
```

### Network Models

```kotlin
// Request/Response for LibreChat API

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresIn: Long
)

@Serializable
data class ThreadsResponse(
    val threads: List<ThreadItem>
)

@Serializable
data class ThreadItem(
    val id: String,
    val title: String,
    val subject: String,
    val lastActivity: Long
)

@Serializable
data class ContextResponse(
    val readyToUseContext: ReadyContextData,
    val metadata: MetadataData
)

@Serializable
data class ReadyContextData(
    val systemPrompt: String,
    val initialMessage: String,
    val voiceParameters: VoiceParametersData
)

@Serializable
data class VoiceParametersData(
    val tone: String,
    val pace: String,
    val style: String
)

@Serializable
data class MetadataData(
    val subject: String,
    val gradeLevel: String,
    val estimatedDuration: String,
    val materialsUsed: List<String>
)

@Serializable
data class SummaryRequest(
    val conversationId: String,
    val lessonSummary: LessonSummaryData,
    val parentReport: ParentReportData
)

@Serializable
data class LessonSummaryData(
    val keyTopics: List<String>,
    val studentDifficulties: List<String>,
    val progressAssessment: String,
    val nextSteps: List<String>
)

@Serializable
data class ParentReportData(
    val subject: String,
    val duration: Long,
    val topicsCovered: List<String>,
    val identifiedDifficulties: List<String>,
    val overallPerformance: String
)

@Serializable
data class SummaryResponse(
    val success: Boolean,
    val message: String?
)
```

## Error Handling

### Error Types

```kotlin
sealed class LibreChatError : Exception() {
    data class NetworkError(override val message: String) : LibreChatError()
    data class AuthenticationError(override val message: String) : LibreChatError()
    data class ServerError(val code: Int, override val message: String) : LibreChatError()
    data class ParseError(override val message: String) : LibreChatError()
    object TokenExpired : LibreChatError()
}
```

### Error Handling Strategy

1. **Network Errors**: Retry z exponential backoff
2. **401 Unauthorized**: Automatyczny refresh token, potem retry
3. **403 Forbidden**: Wylogowanie użytkownika
4. **500 Server Error**: Retry (max 3), potem offline queue
5. **Timeout**: Retry z dłuższym timeout
6. **Parse Error**: Log error, pokazać użytkownikowi

### User Feedback

```kotlin
@Composable
fun ErrorDisplay(error: LibreChatError) {
    when (error) {
        is LibreChatError.NetworkError -> 
            "Brak połączenia z internetem. Sprawdź połączenie."
        is LibreChatError.AuthenticationError -> 
            "Błąd logowania. Sprawdź dane dostępu."
        is LibreChatError.ServerError -> 
            "Problem z serwerem LibreChat. Spróbuj później."
        is LibreChatError.TokenExpired -> 
            "Sesja wygasła. Zaloguj się ponownie."
        is LibreChatError.ParseError -> 
            "Błąd przetwarzania danych. Skontaktuj się z pomocą techniczną."
    }
}
```

## Testing Strategy

### Unit Tests

1. **AuthManager Tests**
   - Login success/failure
   - Token storage/retrieval
   - Token refresh logic
   - Token expiration detection

2. **LibreChatService Tests**
   - API request formatting
   - Response parsing
   - Error handling
   - Retry logic

3. **SessionManager Tests**
   - Session initialization
   - Transcript capture
   - Context updates
   - Session cleanup

4. **SummaryGenerator Tests**
   - Key topic extraction
   - Difficulty identification
   - Progress assessment
   - Parent report formatting

### Integration Tests

1. **End-to-End Session Flow**
   - Login → Thread selection → Session start → Transcript capture → Session end → Summary sent

2. **Offline Mode**
   - Summary queuing when offline
   - Queue processing when online

3. **Error Recovery**
   - Token refresh on 401
   - Retry on network errors
   - Graceful degradation

### Manual Testing Checklist

- [ ] Login with valid credentials
- [ ] Login with invalid credentials
- [ ] Thread list loading
- [ ] Thread selection and context loading
- [ ] Session start with custom system prompt
- [ ] Transcript capture during conversation
- [ ] Image sending during session
- [ ] Session end and summary generation
- [ ] Summary sent to LibreChat
- [ ] Offline summary queuing
- [ ] Token expiration and refresh
- [ ] Network error handling
- [ ] App restart with valid token

## Performance Considerations

### Memory Management

1. **Transcript Limit**: Max 10000 entries (~1MB assuming 100 bytes/entry)
2. **Image Compression**: Max 4MB, automatic compression
3. **Session Cleanup**: Clear SessionContext after successful summary send

### Network Optimization

1. **Connection Pooling**: Reuse OkHttpClient instance
2. **Request Batching**: Send summary only once at session end
3. **Compression**: Enable gzip for API requests
4. **Timeout Configuration**:
   - Connect: 10s
   - Read: 30s (context endpoint), 10s (others)
   - Write: 10s

### UI Responsiveness

1. **Async Operations**: All network calls on IO dispatcher
2. **Loading States**: Show progress indicators for >500ms operations
3. **Debouncing**: Prevent rapid API calls (e.g., thread list refresh)

## Security Considerations

### Data Protection

1. **Token Storage**: EncryptedSharedPreferences with AES-256
2. **HTTPS Only**: Enforce TLS 1.2+ for all API calls
3. **Certificate Pinning**: Optional for production
4. **No Logging**: Never log tokens or sensitive data

### API Security

1. **Authorization Header**: `Authorization: Bearer <token>`
2. **Token Refresh**: Automatic before expiration
3. **Logout**: Clear all stored credentials
4. **Session Timeout**: Respect server-side session limits

## Migration Path

### Phase 1: Core Integration (MVP)
- AuthManager + LoginScreen
- LibreChatService (basic endpoints)
- ThreadListScreen
- SessionManager (basic transcript capture)

### Phase 2: Enhanced Features
- SummaryGenerator with heuristics
- OfflineSummaryQueue
- User transcript capture (SpeechRecognizer)
- Error handling improvements

### Phase 3: Polish
- UI/UX improvements
- Performance optimization
- Comprehensive testing
- Documentation

## Dependencies

### New Dependencies Required

```kotlin
// build.gradle.kts

dependencies {
    // Existing dependencies...
    
    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Network (already have OkHttp)
    // implementation("com.squareup.okhttp3:okhttp:4.12.0") // Already included
    
    // JSON (already have kotlinx.serialization)
    // implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1") // Already included
    
    // Speech Recognition (Android built-in)
    // No additional dependency needed
    
    // Coroutines (already have)
    // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") // Already included
}
```

### Permissions Required

```xml
<!-- AndroidManifest.xml -->

<!-- Existing permissions -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAMERA" />

<!-- No new permissions needed -->
```

## Conclusion

Ten projekt integruje aplikację Android z LibreChat w sposób minimalnie inwazyjny, zachowując istniejącą funkcjonalność Gemini Live. Kluczowe decyzje projektowe:

1. **Lokalne przechowywanie**: Brak Redis/baz danych - wszystko w pamięci
2. **Asynchroniczność**: Operacje sieciowe nie blokują audio
3. **Prostota**: Minimalna liczba nowych komponentów
4. **Niezawodność**: Offline queue, retry logic, error handling
5. **Bezpieczeństwo**: Encrypted storage, HTTPS, token management

Implementacja będzie przyrostowa (3 fazy), co pozwoli na testowanie i iterację.
