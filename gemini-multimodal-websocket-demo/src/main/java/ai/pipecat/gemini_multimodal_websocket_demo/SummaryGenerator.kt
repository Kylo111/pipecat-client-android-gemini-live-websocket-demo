package ai.pipecat.gemini_multimodal_websocket_demo

import ai.pipecat.gemini_multimodal_websocket_demo.models.network.LessonSummaryData
import ai.pipecat.gemini_multimodal_websocket_demo.models.network.ParentReportData
import android.content.Context

/**
 * Generates session summaries for learning sessions.
 * Analyzes transcripts to create lesson summaries and parent reports.
 */
class SummaryGenerator(private val context: Context) {
    
    /**
     * Generates a lesson summary based on session transcripts.
     * 
     * @param transcripts List of transcript entries from the session
     * @param duration Session duration in milliseconds
     * @return LessonSummaryData containing key topics, difficulties, progress, and next steps
     */
    fun generateLessonSummary(
        transcripts: List<SessionManager.TranscriptEntry>,
        duration: Long
    ): LessonSummaryData {
        val keyTopics = extractKeyTopics(transcripts)
        val difficulties = identifyDifficulties(transcripts)
        val progress = assessProgress(transcripts, duration)
        val nextSteps = suggestNextSteps(transcripts)
        
        return LessonSummaryData(
            keyTopics = keyTopics,
            studentDifficulties = difficulties,
            progressAssessment = progress,
            nextSteps = nextSteps
        )
    }
    
    /**
     * Generates a parent-friendly report based on the lesson summary.
     * 
     * @param lessonSummary The lesson summary data
     * @param subject The subject being studied
     * @param duration Session duration in milliseconds
     * @return ParentReportData formatted for parent consumption
     */
    fun generateParentReport(
        lessonSummary: LessonSummaryData,
        subject: String,
        duration: Long
    ): ParentReportData {
        return ParentReportData(
            subject = subject,
            duration = duration,
            topicsCovered = lessonSummary.keyTopics,
            identifiedDifficulties = lessonSummary.studentDifficulties,
            overallPerformance = formatPerformanceForParent(lessonSummary)
        )
    }
    
    /**
     * Extracts key topics from transcripts using keyword frequency analysis.
     */
    private fun extractKeyTopics(transcripts: List<SessionManager.TranscriptEntry>): List<String> {
        if (transcripts.isEmpty()) return emptyList()
        
        // Common stop words to filter out
        val stopWords = setOf(
            "i", "me", "my", "we", "you", "your", "he", "she", "it", "they", "them",
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "is", "are", "was", "were", "be", "been",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "can", "may", "might", "should", "this", "that", "these", "those",
            "what", "which", "who", "when", "where", "why", "how", "yes", "no",
            "ok", "okay", "um", "uh", "so", "well", "now", "then"
        )
        
        // Extract words from bot transcripts (they contain teaching content)
        val wordFrequency = mutableMapOf<String, Int>()
        
        transcripts
            .filter { it.speaker == SessionManager.Speaker.BOT }
            .forEach { entry ->
                val words = entry.text
                    .lowercase()
                    .replace(Regex("[^a-ząćęłńóśźż\\s]"), " ")
                    .split(Regex("\\s+"))
                    .filter { it.length > 3 && it !in stopWords }
                
                words.forEach { word ->
                    wordFrequency[word] = wordFrequency.getOrDefault(word, 0) + 1
                }
            }
        
        // Return top 5 most frequent words as key topics
        return wordFrequency
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key.replaceFirstChar { c -> c.uppercase() } }
    }
    
    /**
     * Identifies student difficulties by detecting questions and confusion markers.
     */
    private fun identifyDifficulties(transcripts: List<SessionManager.TranscriptEntry>): List<String> {
        val difficulties = mutableListOf<String>()
        
        // Confusion markers in Polish and English
        val confusionMarkers = listOf(
            "nie rozumiem", "nie wiem", "co to znaczy", "jak to", "dlaczego",
            "don't understand", "don't know", "what does", "how do", "why",
            "confused", "trudne", "difficult", "hard", "nie mogę", "can't"
        )
        
        // Question markers
        val questionMarkers = listOf("?", "jak", "co", "dlaczego", "kiedy", "gdzie")
        
        // Track repeated topics (indicating difficulty)
        val topicRepetitions = mutableMapOf<String, Int>()
        
        transcripts
            .filter { it.speaker == SessionManager.Speaker.USER }
            .forEach { entry ->
                val text = entry.text.lowercase()
                
                // Check for confusion markers
                confusionMarkers.forEach { marker ->
                    if (text.contains(marker)) {
                        // Extract context around the marker
                        val context = extractContext(entry.text, marker)
                        if (context.isNotEmpty() && !difficulties.contains(context)) {
                            difficulties.add(context)
                        }
                    }
                }
                
                // Check for questions
                if (questionMarkers.any { text.contains(it) } || text.contains("?")) {
                    val words = text.split(Regex("\\s+"))
                    words.forEach { word ->
                        if (word.length > 4) {
                            topicRepetitions[word] = topicRepetitions.getOrDefault(word, 0) + 1
                        }
                    }
                }
            }
        
        // Add topics that were asked about multiple times
        topicRepetitions
            .filter { it.value >= 2 }
            .keys
            .take(3)
            .forEach { topic ->
                difficulties.add("Powtarzające się pytania o: $topic")
            }
        
        return difficulties.take(5)
    }
    
    /**
     * Extracts context around a confusion marker.
     */
    private fun extractContext(text: String, marker: String): String {
        val index = text.lowercase().indexOf(marker.lowercase())
        if (index == -1) return ""
        
        // Get a few words after the marker
        val afterMarker = text.substring(index + marker.length).trim()
        val words = afterMarker.split(Regex("\\s+")).take(5)
        
        return if (words.isNotEmpty()) {
            "Trudności z: ${words.joinToString(" ")}"
        } else {
            ""
        }
    }
    
    /**
     * Assesses student progress based on session duration and topic coverage.
     */
    private fun assessProgress(
        transcripts: List<SessionManager.TranscriptEntry>,
        duration: Long
    ): String {
        if (transcripts.isEmpty()) {
            return "Sesja była zbyt krótka aby ocenić postęp"
        }
        
        val durationMinutes = duration / 60000
        val userEntries = transcripts.count { it.speaker == SessionManager.Speaker.USER }
        val botEntries = transcripts.count { it.speaker == SessionManager.Speaker.BOT }
        val totalEntries = transcripts.size
        
        // Calculate engagement score
        val engagementRatio = if (totalEntries > 0) userEntries.toFloat() / totalEntries else 0f
        
        // Calculate average response length (indicator of understanding)
        val avgUserResponseLength = transcripts
            .filter { it.speaker == SessionManager.Speaker.USER }
            .map { it.text.length }
            .average()
            .takeIf { !it.isNaN() } ?: 0.0
        
        return when {
            durationMinutes < 5 -> "Sesja była krótka (${durationMinutes}min). Uczeń był aktywny ale potrzebuje więcej czasu na naukę."
            
            engagementRatio > 0.4 && avgUserResponseLength > 50 -> 
                "Bardzo dobry postęp! Uczeń aktywnie uczestniczył w sesji (${durationMinutes}min), zadawał pytania i udzielał rozbudowanych odpowiedzi."
            
            engagementRatio > 0.3 -> 
                "Dobry postęp. Uczeń był zaangażowany podczas ${durationMinutes}-minutowej sesji i uczestniczył w rozmowie."
            
            engagementRatio > 0.2 -> 
                "Umiarkowany postęp. Uczeń słuchał i odpowiadał, ale mógłby być bardziej aktywny w zadawaniu pytań."
            
            else -> 
                "Sesja trwała ${durationMinutes}min. Uczeń głównie słuchał wyjaśnień. Zachęcamy do większej aktywności w następnej sesji."
        }
    }
    
    /**
     * Suggests next steps based on identified difficulties.
     */
    private fun suggestNextSteps(transcripts: List<SessionManager.TranscriptEntry>): List<String> {
        val nextSteps = mutableListOf<String>()
        
        val difficulties = identifyDifficulties(transcripts)
        val keyTopics = extractKeyTopics(transcripts)
        
        // Suggest review if there were difficulties
        if (difficulties.isNotEmpty()) {
            nextSteps.add("Powtórzyć materiał związany z trudnościami: ${difficulties.take(2).joinToString(", ")}")
        }
        
        // Suggest practice exercises
        if (keyTopics.isNotEmpty()) {
            nextSteps.add("Przećwiczyć zadania z tematów: ${keyTopics.take(3).joinToString(", ")}")
        }
        
        // Suggest continuation based on session length
        val duration = transcripts.lastOrNull()?.timestamp?.minus(transcripts.firstOrNull()?.timestamp ?: 0L) ?: 0L
        val durationMinutes = duration / 60000
        
        if (durationMinutes < 10) {
            nextSteps.add("Zaplanować dłuższą sesję (15-20 minut) dla lepszego przyswojenia materiału")
        } else {
            nextSteps.add("Kontynuować naukę w podobnym tempie")
        }
        
        // Always suggest asking questions
        if (difficulties.isEmpty()) {
            nextSteps.add("Zachęcać do zadawania pytań podczas nauki")
        }
        
        return nextSteps.take(4)
    }
    
    /**
     * Formats performance assessment in simple, positive language for parents.
     */
    private fun formatPerformanceForParent(summary: LessonSummaryData): String {
        val hasTopics = summary.keyTopics.isNotEmpty()
        val hasDifficulties = summary.studentDifficulties.isNotEmpty()
        
        return when {
            // Excellent performance - topics covered, no major difficulties
            hasTopics && !hasDifficulties -> {
                "Świetna sesja! Dziecko aktywnie uczestniczyło w lekcji i dobrze przyswajało materiał. " +
                "Omówiono tematy: ${summary.keyTopics.take(3).joinToString(", ")}. " +
                "Nie zaobserwowano większych trudności."
            }
            
            // Good performance - topics covered, some difficulties
            hasTopics && hasDifficulties -> {
                "Dobra sesja nauki. Dziecko pracowało nad tematami: ${summary.keyTopics.take(2).joinToString(", ")}. " +
                "Pojawiły się pewne wyzwania (${summary.studentDifficulties.size} obszarów wymaga dodatkowej uwagi), " +
                "ale to normalna część procesu uczenia się. Warto powtórzyć materiał."
            }
            
            // Moderate performance - few topics, some difficulties
            !hasTopics && hasDifficulties -> {
                "Sesja nauki odbyła się. Dziecko potrzebuje więcej czasu na przyswojenie materiału. " +
                "Zidentyfikowano ${summary.studentDifficulties.size} obszarów wymagających dodatkowej pracy. " +
                "Zachęcamy do regularnych, krótszych sesji."
            }
            
            // Short or incomplete session
            else -> {
                "Sesja nauki została przeprowadzona. " +
                "Dziecko uczestniczyło w zajęciach. " +
                "Dla lepszych rezultatów zalecamy dłuższe i bardziej regularne sesje."
            }
        }
    }
    

}
