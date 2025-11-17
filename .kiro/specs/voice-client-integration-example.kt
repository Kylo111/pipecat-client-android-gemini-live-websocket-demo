// Przykład integracji z VoiceClientManager

class VoiceClientManager(
    private val contextBuilder: ContextBuilder,
    private val sessionDao: SessionDao,
    // ... inne dependencies
) {
    
    private var currentSessionId: String? = null
    private val currentTranscript = StringBuilder()
    
    suspend fun startConversationWithContext(
        conversationId: String,
        currentTopic: String? = null
    ) {
        // 1. Utwórz nową sesję
        currentSessionId = UUID.randomUUID().toString()
        currentTranscript.clear()
        
        val session = SessionEntity(
            id = currentSessionId!!,
            conversationId = conversationId,
            startedAt = System.currentTimeMillis(),
            transcript = "",
            messageCount = 0
        )
        sessionDao.insert(session)
        
        // 2. Zbuduj kontekst (hybrid approach)
        val context = contextBuilder.buildContextWithBudget(
            conversationId = conversationId,
            currentTopic = currentTopic,
            maxTokens = 30000
        )
        
        // 3. Formatuj dla Gemini
        val systemInstruction = buildSystemInstruction(context)
        
        // 4. Konfiguracja Gemini Live
        val config = RTVIClientOptions(
            params = RTVIClientParams(
                baseUrl = baseUrl,
                config = listOf(
                    RTVIServiceConfig(
                        service = "llm",
                        options = listOf(
                            RTVIOption("model", "gemini-2.0-flash-exp"),
                            RTVIOption("system_instruction", systemInstruction)
                        )
                    )
                )
            )
        )
        
        // 5. Połącz
        voiceClient.connect(config)
        
        // 6. Nasłuchuj transkrypcji
        setupTranscriptListeners()
    }
    
    private fun buildSystemInstruction(context: ConversationContext): String {
        val contextText = contextBuilder.formatForGemini(context)
        
        return """
            You are a helpful AI assistant with access to conversation history and documents.
            
            $contextText
            
            === INSTRUCTIONS ===
            - Use the context above to provide relevant, informed responses
            - The LAST SESSION contains the most recent conversation - user may refer to it
            - Reference specific details from documents when relevant
            - If user asks "what did we discuss?", refer to recent sessions
            - Maintain continuity with previous conversations
            - Be natural and conversational
        """.trimIndent()
    }
    
    private fun setupTranscriptListeners() {
        // Nasłuchuj user transcript
        voiceClient.on("user-transcript") { event ->
            val text = event.data["text"] as? String ?: return@on
            appendToTranscript("user", text)
        }
        
        // Nasłuchuj bot transcript
        voiceClient.on("bot-transcript") { event ->
            val text = event.data["text"] as? String ?: return@on
            appendToTranscript("assistant", text)
        }
    }
    
    private fun appendToTranscript(role: String, text: String) {
        currentSessionId?.let { sessionId ->
            // Append do current transcript
            currentTranscript.append("$role: $text\n")
            
            // Update w bazie (async)
            scope.launch {
                sessionDao.updateTranscript(
                    sessionId = sessionId,
                    transcript = currentTranscript.toString()
                )
                sessionDao.incrementMessageCount(sessionId)
            }
        }
    }
    
    suspend fun endSession() {
        currentSessionId?.let { sessionId ->
            val session = sessionDao.getById(sessionId) ?: return
            
            // 1. Zaktualizuj ended_at i duration
            val endedAt = System.currentTimeMillis()
            val duration = ((endedAt - session.startedAt) / 1000).toInt()
            
            sessionDao.update(session.copy(
                endedAt = endedAt,
                durationSeconds = duration
            ))
            
            // 2. Generuj summary (jeśli sesja była dłuższa niż 2 min)
            if (duration > 120 && session.transcript.isNotBlank()) {
                generateSessionSummary(sessionId)
            }
            
            // 3. Schedule background sync do Vertex
            scheduleVertexSync(sessionId)
            
            // 4. Sprawdź czy czas na meta-summary
            checkAndGenerateMetaSummary(session.conversationId)
            
            currentSessionId = null
            currentTranscript.clear()
        }
        
        voiceClient.disconnect()
    }
    
    private suspend fun generateSessionSummary(sessionId: String) {
        val session = sessionDao.getById(sessionId) ?: return
        
        try {
            val summary = geminiClient.generateContent(
                model = "gemini-1.5-flash",
                prompt = """
                    Podsumuj poniższą konwersację w 3-5 zdaniach.
                    Skup się na głównych tematach i kluczowych ustaleniach.
                    
                    Konwersacja:
                    ${session.transcript}
                    
                    Podsumowanie:
                """.trimIndent()
            ).text
            
            sessionDao.updateSummary(sessionId, summary)
            
        } catch (e: Exception) {
            Log.e("VoiceClientManager", "Failed to generate summary", e)
            // Retry później przez WorkManager
        }
    }
    
    private suspend fun checkAndGenerateMetaSummary(conversationId: String) {
        val sessionCount = sessionDao.getSessionCount(conversationId)
        
        // Co 10 sesji generuj meta-summary
        if (sessionCount % 10 == 0) {
            scope.launch {
                generateMetaSummary(conversationId)
            }
        }
    }
    
    private suspend fun generateMetaSummary(conversationId: String) {
        val sessions = sessionDao.getAllSessions(conversationId)
        val summaries = sessions.mapNotNull { it.summary }
        
        if (summaries.isEmpty()) return
        
        try {
            val metaSummary = geminiClient.generateContent(
                model = "gemini-1.5-flash",
                prompt = """
                    Stwórz zwięzłe podsumowanie całej konwersacji na podstawie poniższych podsumowań sesji.
                    Wyodrębnij główne tematy, kluczowe decyzje i wnioski.
                    
                    Podsumowania sesji:
                    ${summaries.joinToString("\n\n")}
                    
                    Podsumowanie całej konwersacji:
                """.trimIndent()
            ).text
            
            conversationDao.updateMetaSummary(conversationId, metaSummary)
            
        } catch (e: Exception) {
            Log.e("VoiceClientManager", "Failed to generate meta-summary", e)
        }
    }
}
