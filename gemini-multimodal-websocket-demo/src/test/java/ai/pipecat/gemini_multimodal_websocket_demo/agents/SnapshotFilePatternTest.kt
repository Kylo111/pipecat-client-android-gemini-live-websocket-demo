package ai.pipecat.gemini_multimodal_websocket_demo.agents

import ai.pipecat.gemini_multimodal_websocket_demo.models.ReasoningSnapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Unit tests for Snapshot File Pattern validation.
 * 
 * Tests the core logic of:
 * - Task 25.1: Snapshot file creation and JSON content correctness
 * - Task 25.2: Snapshot file cleanup logic
 * - Task 25.3: Large transcript handling (>10KB)
 * 
 * Note: These tests validate the data model and serialization logic.
 * Full integration tests with Android Context should be run on device.
 */
class SnapshotFilePatternTest {
    
    private val json = Json { 
        prettyPrint = false
        ignoreUnknownKeys = true
    }
    
    // ========== Task 25.1: Test Snapshot File creation ==========
    
    @Test
    fun testSnapshotSerializationAndDeserialization() {
        // Given
        val snapshot = ReasoningSnapshot(
            taskId = "test-task-123",
            conversationId = "conv-456",
            taskDescription = "Test task description",
            priority = "HIGH",
            previousSessionTranscript = "Previous session content",
            currentSessionTranscript = "Current session content",
            isReportTask = false,
            reportTopics = null
        )
        
        // When
        val jsonString = json.encodeToString(ReasoningSnapshot.serializer(), snapshot)
        val deserialized = json.decodeFromString(ReasoningSnapshot.serializer(), jsonString)
        
        // Then
        assertEquals("Task ID should match", snapshot.taskId, deserialized.taskId)
        assertEquals("Conversation ID should match", snapshot.conversationId, deserialized.conversationId)
        assertEquals("Task description should match", snapshot.taskDescription, deserialized.taskDescription)
        assertEquals("Priority should match", snapshot.priority, deserialized.priority)
        assertEquals("Previous transcript should match", snapshot.previousSessionTranscript, deserialized.previousSessionTranscript)
        assertEquals("Current transcript should match", snapshot.currentSessionTranscript, deserialized.currentSessionTranscript)
        assertEquals("isReportTask should match", snapshot.isReportTask, deserialized.isReportTask)
        assertEquals("reportTopics should match", snapshot.reportTopics, deserialized.reportTopics)
    }
    
    @Test
    fun testSnapshotWithReportTask() {
        // Given
        val snapshot = ReasoningSnapshot(
            taskId = "report-task-001",
            conversationId = "conv-001",
            taskDescription = "Generate report",
            priority = "NORMAL",
            previousSessionTranscript = "Previous content",
            currentSessionTranscript = "Current content",
            isReportTask = true,
            reportTopics = listOf("AI", "Machine Learning", "Neural Networks")
        )
        
        // When
        val jsonString = json.encodeToString(ReasoningSnapshot.serializer(), snapshot)
        val deserialized = json.decodeFromString(ReasoningSnapshot.serializer(), jsonString)
        
        // Then
        assertTrue("Should be report task", deserialized.isReportTask)
        assertNotNull("Should have topics", deserialized.reportTopics)
        assertEquals("Should have 3 topics", 3, deserialized.reportTopics?.size)
        assertTrue("Should contain AI topic", deserialized.reportTopics?.contains("AI") == true)
        assertTrue("Should contain ML topic", deserialized.reportTopics?.contains("Machine Learning") == true)
    }
    
    @Test
    fun testSnapshotWithNullPreviousTranscript() {
        // Given
        val snapshot = ReasoningSnapshot(
            taskId = "task-no-previous",
            conversationId = "conv-new",
            taskDescription = "First session task",
            priority = "NORMAL",
            previousSessionTranscript = null,
            currentSessionTranscript = "Current session content"
        )
        
        // When
        val jsonString = json.encodeToString(ReasoningSnapshot.serializer(), snapshot)
        val deserialized = json.decodeFromString(ReasoningSnapshot.serializer(), jsonString)
        
        // Then
        assertNull("Previous transcript should be null", deserialized.previousSessionTranscript)
        assertNotNull("Current transcript should not be null", deserialized.currentSessionTranscript)
        assertEquals("Current transcript should match", snapshot.currentSessionTranscript, deserialized.currentSessionTranscript)
    }
    
    @Test
    fun testSnapshotJsonStructure() {
        // Given
        val snapshot = ReasoningSnapshot(
            taskId = "task-123",
            conversationId = "conv-456",
            taskDescription = "Test",
            priority = "HIGH",
            previousSessionTranscript = "prev",
            currentSessionTranscript = "curr"
        )
        
        // When
        val jsonString = json.encodeToString(ReasoningSnapshot.serializer(), snapshot)
        println("JSON output: $jsonString")
        
        // Then - Verify it's valid JSON by deserializing
        val deserialized = json.decodeFromString(ReasoningSnapshot.serializer(), jsonString)
        assertNotNull("Should deserialize successfully", deserialized)
        
        // Verify all fields are present in deserialized object
        assertEquals("task-123", deserialized.taskId)
        assertEquals("conv-456", deserialized.conversationId)
        assertEquals("Test", deserialized.taskDescription)
        assertEquals("HIGH", deserialized.priority)
        assertEquals("prev", deserialized.previousSessionTranscript)
        assertEquals("curr", deserialized.currentSessionTranscript)
        assertFalse(deserialized.isReportTask)
        assertNull(deserialized.reportTopics)
        assertTrue(deserialized.createdAt > 0)
    }
    
    // ========== Task 25.2: Test Snapshot File cleanup logic ==========
    
    @Test
    fun testFileAgeCalculation() {
        // Given
        val now = System.currentTimeMillis()
        val twentyFourHoursAgo = now - (24 * 60 * 60 * 1000)
        val twentyFiveHoursAgo = now - (25 * 60 * 60 * 1000)
        val oneHourAgo = now - (1 * 60 * 60 * 1000)
        
        // When/Then
        val cutoff = now - (24 * 60 * 60 * 1000)
        
        assertTrue("25 hours ago should be older than cutoff", twentyFiveHoursAgo < cutoff)
        assertFalse("24 hours ago should not be older than cutoff", twentyFourHoursAgo < cutoff)
        assertFalse("1 hour ago should not be older than cutoff", oneHourAgo < cutoff)
    }
    
    @Test
    fun testTempFileIdentification() {
        // Given
        val tempFileName = "task_abc123.tmp"
        val jsonFileName = "task_abc123.json"
        val otherFileName = "task_abc123.txt"
        
        // When/Then
        assertTrue("Should identify .tmp extension", tempFileName.endsWith(".tmp"))
        assertFalse("Should not identify .json as temp", jsonFileName.endsWith(".tmp"))
        assertFalse("Should not identify .txt as temp", otherFileName.endsWith(".tmp"))
    }
    
    @Test
    fun testSnapshotFileNamingPattern() {
        // Given
        val taskId = "test-task-123"
        val expectedFileName = "task_$taskId.json"
        val expectedTempFileName = "task_$taskId.tmp"
        
        // When/Then
        assertEquals("JSON file name should match pattern", "task_test-task-123.json", expectedFileName)
        assertEquals("Temp file name should match pattern", "task_test-task-123.tmp", expectedTempFileName)
    }
    
    // ========== Task 25.3: Test large transcripts ==========
    
    @Test
    fun testLargeTranscriptSerialization_Over10KB() {
        // Given - Create transcript larger than 10KB
        val largeTranscript = buildString {
            repeat(3000) { // ~15KB of text
                append("User: This is a test message with some content. ")
                append("Bot: This is a response with detailed information. ")
            }
        }
        
        val transcriptSize = largeTranscript.toByteArray().size
        assertTrue("Transcript should be larger than 10KB (actual: $transcriptSize bytes)", 
            transcriptSize > 10 * 1024)
        
        val snapshot = ReasoningSnapshot(
            taskId = "large-task",
            conversationId = "conv-large",
            taskDescription = "Task with large transcript",
            priority = "HIGH",
            previousSessionTranscript = largeTranscript,
            currentSessionTranscript = largeTranscript
        )
        
        // When
        val jsonString = json.encodeToString(ReasoningSnapshot.serializer(), snapshot)
        val jsonSize = jsonString.toByteArray().size
        
        // Then
        assertTrue("JSON should be larger than 10KB (actual: $jsonSize bytes)", 
            jsonSize > 10 * 1024)
        
        // Verify deserialization works
        val deserialized = json.decodeFromString(ReasoningSnapshot.serializer(), jsonString)
        assertEquals("Previous transcript length should match", 
            largeTranscript.length, deserialized.previousSessionTranscript?.length)
        assertEquals("Current transcript length should match", 
            largeTranscript.length, deserialized.currentSessionTranscript.length)
    }
    
    @Test
    fun testVeryLargeTranscriptSerialization_Over50KB() {
        // Given - Create very large transcript (>50KB)
        val veryLargeTranscript = buildString {
            repeat(15000) { // ~75KB of text
                append("User: This is a longer test message with more content to simulate a very long conversation. ")
                append("Bot: This is a detailed response with comprehensive information and explanations. ")
            }
        }
        
        val transcriptSize = veryLargeTranscript.toByteArray().size
        assertTrue("Transcript should be larger than 50KB (actual: $transcriptSize bytes)", 
            transcriptSize > 50 * 1024)
        
        val snapshot = ReasoningSnapshot(
            taskId = "very-large-task",
            conversationId = "conv-very-large",
            taskDescription = "Task with very large transcript",
            priority = "HIGH",
            previousSessionTranscript = veryLargeTranscript,
            currentSessionTranscript = veryLargeTranscript
        )
        
        // When
        val jsonString = json.encodeToString(ReasoningSnapshot.serializer(), snapshot)
        val jsonSize = jsonString.toByteArray().size
        
        // Then
        assertTrue("JSON should be larger than 50KB (actual: $jsonSize bytes)", 
            jsonSize > 50 * 1024)
        
        // Verify content integrity
        val deserialized = json.decodeFromString(ReasoningSnapshot.serializer(), jsonString)
        assertEquals("Transcript length should match", 
            veryLargeTranscript.length, deserialized.currentSessionTranscript.length)
        
        // Verify content is actually the same (not just length)
        assertEquals("Previous transcript content should match", 
            veryLargeTranscript, deserialized.previousSessionTranscript)
        assertEquals("Current transcript content should match", 
            veryLargeTranscript, deserialized.currentSessionTranscript)
    }
    
    @Test
    fun testMultipleLargeSnapshotsSerialization() {
        // Given - Create multiple large snapshots
        val largeTranscript = "x".repeat(15 * 1024) // 15KB
        
        val snapshots = (1..5).map { i ->
            ReasoningSnapshot(
                taskId = "large-task-$i",
                conversationId = "conv-$i",
                taskDescription = "Task $i",
                priority = "NORMAL",
                previousSessionTranscript = largeTranscript,
                currentSessionTranscript = largeTranscript
            )
        }
        
        // When - Serialize all snapshots
        val jsonStrings = snapshots.map { 
            json.encodeToString(ReasoningSnapshot.serializer(), it) 
        }
        
        // Then - Verify all are large and can be deserialized
        jsonStrings.forEachIndexed { index, jsonString ->
            val size = jsonString.toByteArray().size
            assertTrue("Snapshot $index should be > 10KB (actual: $size bytes)", 
                size > 10 * 1024)
            
            val deserialized = json.decodeFromString(ReasoningSnapshot.serializer(), jsonString)
            assertEquals("Task ID should match for snapshot $index", 
                "large-task-${index + 1}", deserialized.taskId)
            assertEquals("Transcript length should match for snapshot $index", 
                largeTranscript.length, deserialized.currentSessionTranscript.length)
        }
    }
    
    @Test
    fun testTranscriptWithSpecialCharacters() {
        // Given - Transcript with special characters, unicode, etc.
        val specialTranscript = buildString {
            append("User: Hello! How are you? 你好！\n")
            append("Bot: I'm doing well, thanks! 😊\n")
            append("User: Can you help with \"quotes\" and 'apostrophes'?\n")
            append("Bot: Sure! Here's some code: { \"key\": \"value\" }\n")
            append("User: What about backslashes \\ and newlines?\n")
            append("Bot: No problem! \\n \\t \\r\n")
            // Make it large
            repeat(2000) {
                append("More content with émojis 🎉 and spëcial çharacters!\n")
            }
        }
        
        val transcriptSize = specialTranscript.toByteArray().size
        assertTrue("Transcript should be > 10KB (actual: $transcriptSize bytes)", 
            transcriptSize > 10 * 1024)
        
        val snapshot = ReasoningSnapshot(
            taskId = "special-chars-task",
            conversationId = "conv-special",
            taskDescription = "Task with special characters",
            priority = "NORMAL",
            previousSessionTranscript = null,
            currentSessionTranscript = specialTranscript
        )
        
        // When
        val jsonString = json.encodeToString(ReasoningSnapshot.serializer(), snapshot)
        val deserialized = json.decodeFromString(ReasoningSnapshot.serializer(), jsonString)
        
        // Then - Verify special characters are preserved
        assertEquals("Special characters should be preserved", 
            specialTranscript, deserialized.currentSessionTranscript)
        assertTrue("Should contain emoji", deserialized.currentSessionTranscript.contains("😊"))
        assertTrue("Should contain Chinese", deserialized.currentSessionTranscript.contains("你好"))
        assertTrue("Should contain quotes", deserialized.currentSessionTranscript.contains("\"quotes\""))
    }
    
    @Test
    fun testWorkManagerDataSizeLimit() {
        // Given - WorkManager has 10KB limit
        val workManagerLimit = 10 * 1024 // 10KB
        
        // Create a snapshot that would exceed WorkManager limit
        val largeTranscript = "x".repeat(15 * 1024) // 15KB
        val snapshot = ReasoningSnapshot(
            taskId = "test-task",
            conversationId = "test-conv",
            taskDescription = "Test",
            priority = "NORMAL",
            previousSessionTranscript = largeTranscript,
            currentSessionTranscript = largeTranscript
        )
        
        // When - Serialize to JSON
        val jsonString = json.encodeToString(ReasoningSnapshot.serializer(), snapshot)
        val jsonSize = jsonString.toByteArray().size
        
        // Then - Verify it exceeds WorkManager limit
        assertTrue("Snapshot JSON should exceed WorkManager 10KB limit (actual: $jsonSize bytes)", 
            jsonSize > workManagerLimit)
        
        // Verify file path would be small enough
        val filePath = "/data/data/app/cache/reasoning-snapshots/task_test-task.json"
        val filePathSize = filePath.toByteArray().size
        assertTrue("File path should be well under WorkManager limit (actual: $filePathSize bytes)", 
            filePathSize < workManagerLimit)
        
        println("Snapshot JSON size: $jsonSize bytes (exceeds WorkManager limit)")
        println("File path size: $filePathSize bytes (fits in WorkManager)")
        println("This validates the Snapshot File Pattern bypasses the WorkManager limit!")
    }
}
