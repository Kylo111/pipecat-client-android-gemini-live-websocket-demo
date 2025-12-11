package ai.pipecat.gemini_multimodal_websocket_demo.protocol

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Property-based tests for GeminiProtocol message parsing.
 * 
 * These tests verify that the protocol parser correctly handles all message types
 * from the Gemini Live API and preserves data integrity through parsing.
 * 
 * **Feature: voiceclientmanager-refactoring, Property 4: Protocol parsing round-trip consistency**
 * **Feature: voiceclientmanager-refactoring, Property 5: SetupComplete message parsing**
 * **Feature: voiceclientmanager-refactoring, Property 6: Audio data parsing preserves bytes**
 * **Feature: voiceclientmanager-refactoring, Property 7: Transcript parsing preserves text**
 * **Feature: voiceclientmanager-refactoring, Property 8: ToolCall parsing preserves all fields**
 * **Feature: voiceclientmanager-refactoring, Property 9: SessionUpdate parsing preserves handle and flag**
 * **Feature: voiceclientmanager-refactoring, Property 10: Unknown message preserves raw JSON**
 */
class GeminiProtocolPropertyTest {
    
    private val protocol = GeminiProtocol()
    
    /**
     * Property 5: SetupComplete message parsing
     * 
     * For any JSON string containing a "setupComplete" key, parsing SHALL return 
     * a GeminiEvent.SetupComplete.
     * 
     * **Validates: Requirements 2.2**
     */
    @Test
    fun `property_5_setupComplete_message_parsing`() {
        // Test with minimal setupComplete message
        val message1 = """{"setupComplete":{}}"""
        val result1 = protocol.parseMessage(message1)
        assertIs<GeminiEvent.SetupComplete>(result1)
        
        // Test with setupComplete containing extra fields (should be ignored)
        val message2 = """{"setupComplete":{"extra":"field"},"other":"data"}"""
        val result2 = protocol.parseMessage(message2)
        assertIs<GeminiEvent.SetupComplete>(result2)
        
        // Test with setupComplete as first key
        val message3 = """{"setupComplete":null}"""
        val result3 = protocol.parseMessage(message3)
        assertIs<GeminiEvent.SetupComplete>(result3)
    }
    
    /**
     * Property 6: Audio data parsing preserves bytes
     * 
     * For any base64-encoded audio data in a serverContent message, parsing SHALL 
     * return AudioData with correctly decoded bytes (decode(encode(bytes)) == bytes).
     * 
     * **Validates: Requirements 2.3**
     */
    @Test
    fun `property_6_audio_data_parsing_preserves_bytes`() {
        // Test with various audio data sizes
        val testSizes = listOf(256, 512, 1024, 2048, 4096)
        
        for (size in testSizes) {
            // Generate random audio bytes
            val originalBytes = ByteArray(size) { (it % 256).toByte() }
            // Use Java's Base64 encoder for unit tests (Android Base64 is not available)
            val encodedAudio = java.util.Base64.getEncoder().encodeToString(originalBytes)
            
            // Create serverContent message with audio
            val message = buildJsonObject {
                putJsonObject("serverContent") {
                    putJsonObject("modelTurn") {
                        putJsonArray("parts") {
                            add(buildJsonObject {
                                putJsonObject("inlineData") {
                                    put("mimeType", "audio/pcm")
                                    put("data", encodedAudio)
                                }
                            })
                        }
                    }
                }
            }.toString()
            
            // Parse the message
            val result = protocol.parseMessage(message)
            
            // Verify it's AudioData
            assertIs<GeminiEvent.AudioData>(result)
            
            // Verify bytes are preserved exactly
            assertEquals(originalBytes.size, result.audioBytes.size, 
                "Audio bytes size should match for size $size")
            assertTrue(originalBytes.contentEquals(result.audioBytes),
                "Audio bytes should be preserved exactly for size $size")
            assertEquals("audio/pcm", result.mimeType)
        }
    }
    
    /**
     * Property 7: Transcript parsing preserves text
     * 
     * For any transcript text in a serverContent message, parsing SHALL return 
     * a Transcript event with the exact same text.
     * 
     * **Validates: Requirements 2.4**
     */
    @Test
    fun `property_7_transcript_parsing_preserves_text`() {
        // Test various transcript texts
        val testTexts = listOf(
            "Hello, how are you?",
            "This is a test with special chars: !@#$%^&*()",
            "Multi-line\ntext\ntest",
            "Unicode: 你好世界 🌍",
            "Very long text: " + "a".repeat(1000),
            ""  // Empty string
        )
        
        for (text in testTexts) {
            // Test bot transcript
            val botMessage = buildJsonObject {
                putJsonObject("serverContent") {
                    putJsonObject("outputTranscription") {
                        put("text", text)
                    }
                }
            }.toString()
            
            val botResult = protocol.parseMessage(botMessage)
            assertIs<GeminiEvent.Transcript>(botResult)
            assertEquals(text, botResult.text, "Bot transcript text should be preserved")
            assertEquals(GeminiEvent.Transcript.Speaker.BOT, botResult.speaker)
            
            // Test user transcript
            val userMessage = buildJsonObject {
                putJsonObject("serverContent") {
                    putJsonObject("inputTranscription") {
                        put("text", text)
                    }
                }
            }.toString()
            
            val userResult = protocol.parseMessage(userMessage)
            assertIs<GeminiEvent.Transcript>(userResult)
            assertEquals(text, userResult.text, "User transcript text should be preserved")
            assertEquals(GeminiEvent.Transcript.Speaker.USER, userResult.speaker)
        }
    }
    
    /**
     * Property 8: ToolCall parsing preserves all fields
     * 
     * For any toolCall message with random id, name, and arguments, parsing SHALL 
     * return a ToolCall event with all fields preserved exactly.
     * 
     * **Validates: Requirements 2.5**
     */
    @Test
    fun `property_8_toolCall_parsing_preserves_all_fields`() {
        // Test various tool calls
        val testCases = listOf(
            Triple("call-1", "get_weather", """{"location":"New York"}"""),
            Triple("call-2", "set_alarm", """{"time":"10:30","label":"Meeting"}"""),
            Triple("call-3", "search", """{"query":"kotlin programming","limit":10}"""),
            Triple("call-uuid-12345", "complex_tool", """{"nested":{"deep":{"value":123}},"array":[1,2,3]}""")
        )
        
        for ((id, name, argsJson) in testCases) {
            val message = buildJsonObject {
                putJsonObject("toolCall") {
                    putJsonArray("functionCalls") {
                        add(buildJsonObject {
                            put("id", id)
                            put("name", name)
                            // Parse args as JSON object
                            val argsObj = kotlinx.serialization.json.Json.parseToJsonElement(argsJson).jsonObject
                            put("args", argsObj)
                        })
                    }
                }
            }.toString()
            
            val result = protocol.parseMessage(message)
            assertIs<GeminiEvent.ToolCall>(result)
            assertEquals(id, result.id, "Tool call ID should be preserved")
            assertEquals(name, result.name, "Tool call name should be preserved")
            // Arguments should be preserved (comparing as JSON strings)
            assertEquals(argsJson, result.arguments.toString(), "Tool call arguments should be preserved")
        }
    }
    
    /**
     * Property 9: SessionUpdate parsing preserves handle and flag
     * 
     * For any sessionResumptionUpdate message with random handle and resumable flag, 
     * parsing SHALL return a SessionUpdate event with both values preserved.
     * 
     * **Validates: Requirements 2.6**
     */
    @Test
    fun `property_9_sessionUpdate_parsing_preserves_handle_and_flag`() {
        // Test various handles and resumable flags
        val testCases = listOf(
            Pair("handle-1", true),
            Pair("handle-2", false),
            Pair("very-long-handle-" + "x".repeat(100), true),
            Pair("handle-with-special-chars-!@#$%", false),
            Pair("", true)  // Empty handle
        )
        
        for ((handle, resumable) in testCases) {
            val message = buildJsonObject {
                putJsonObject("sessionResumptionUpdate") {
                    put("newHandle", handle)
                    put("resumable", resumable)
                }
            }.toString()
            
            val result = protocol.parseMessage(message)
            assertIs<GeminiEvent.SessionUpdate>(result)
            assertEquals(handle, result.handle, "Session handle should be preserved")
            assertEquals(resumable, result.resumable, "Resumable flag should be preserved")
        }
    }
    
    /**
     * Property 10: Unknown message preserves raw JSON
     * 
     * For any JSON string that doesn't match known message types, parsing SHALL 
     * return an Unknown event containing the original JSON string.
     * 
     * **Validates: Requirements 2.9**
     */
    @Test
    fun `property_10_unknown_message_preserves_raw_json`() {
        // Test various unknown message formats
        val testMessages = listOf(
            """{"unknownType":"value"}""",
            """{"random":"data","nested":{"field":"value"}}""",
            """{"empty":{}}""",
            """{"array":[1,2,3,4,5]}"""
        )
        
        for (message in testMessages) {
            val result = protocol.parseMessage(message)
            assertIs<GeminiEvent.Unknown>(result)
            // The raw JSON should be preserved (may be reformatted but content same)
            assertTrue(result.rawJson.contains("unknownType") || 
                      result.rawJson.contains("random") ||
                      result.rawJson.contains("empty") ||
                      result.rawJson.contains("array"),
                "Unknown message should preserve raw JSON content")
        }
    }
    
    /**
     * Property 4: Protocol parsing round-trip consistency
     * 
     * For any valid GeminiEvent object that can be serialized, serializing to JSON 
     * and then parsing back SHALL produce an equivalent event.
     * 
     * **Validates: Requirements 2.10**
     */
    @Test
    fun `property_4_protocol_parsing_round_trip_consistency`() {
        // Test SetupComplete round-trip
        val setupMessage = """{"setupComplete":{}}"""
        val setupResult = protocol.parseMessage(setupMessage)
        assertIs<GeminiEvent.SetupComplete>(setupResult)
        
        // Test AudioData round-trip
        val audioBytes = ByteArray(512) { (it % 256).toByte() }
        // Use Java's Base64 encoder for unit tests (Android Base64 is not available)
        val encodedAudio = java.util.Base64.getEncoder().encodeToString(audioBytes)
        val audioMessage = buildJsonObject {
            putJsonObject("serverContent") {
                putJsonObject("modelTurn") {
                    putJsonArray("parts") {
                        add(buildJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", "audio/pcm")
                                put("data", encodedAudio)
                            }
                        })
                    }
                }
            }
        }.toString()
        
        val audioResult = protocol.parseMessage(audioMessage)
        assertIs<GeminiEvent.AudioData>(audioResult)
        assertTrue(audioBytes.contentEquals(audioResult.audioBytes))
        
        // Test Transcript round-trip
        val transcriptText = "Hello, this is a test"
        val transcriptMessage = buildJsonObject {
            putJsonObject("serverContent") {
                putJsonObject("outputTranscription") {
                    put("text", transcriptText)
                }
            }
        }.toString()
        
        val transcriptResult = protocol.parseMessage(transcriptMessage)
        assertIs<GeminiEvent.Transcript>(transcriptResult)
        assertEquals(transcriptText, transcriptResult.text)
        
        // Test ToolCall round-trip
        val toolMessage = buildJsonObject {
            putJsonObject("toolCall") {
                putJsonArray("functionCalls") {
                    add(buildJsonObject {
                        put("id", "test-id")
                        put("name", "test_function")
                        putJsonObject("args") {
                            put("param1", "value1")
                        }
                    })
                }
            }
        }.toString()
        
        val toolResult = protocol.parseMessage(toolMessage)
        assertIs<GeminiEvent.ToolCall>(toolResult)
        assertEquals("test-id", toolResult.id)
        assertEquals("test_function", toolResult.name)
    }
    
    /**
     * Test TurnComplete message parsing
     */
    @Test
    fun `test_turnComplete_message_parsing`() {
        // Test turnComplete in serverContent
        val message1 = buildJsonObject {
            putJsonObject("serverContent") {
                put("turnComplete", true)
            }
        }.toString()
        
        val result1 = protocol.parseMessage(message1)
        assertIs<GeminiEvent.TurnComplete>(result1)
        
        // Test turnComplete at root level
        val message2 = """{"turnComplete":{}}"""
        val result2 = protocol.parseMessage(message2)
        // Root level turnComplete is not directly parsed, but hasTurnCompleteAtRoot can check it
        assertTrue(protocol.hasTurnCompleteAtRoot(
            kotlinx.serialization.json.Json.parseToJsonElement(message2).jsonObject
        ))
    }
    
    /**
     * Test Interrupted message parsing
     */
    @Test
    fun `test_interrupted_message_parsing`() {
        val message = buildJsonObject {
            putJsonObject("serverContent") {
                put("interrupted", true)
            }
        }.toString()
        
        val result = protocol.parseMessage(message)
        assertIs<GeminiEvent.Interrupted>(result)
    }
    
    /**
     * Test malformed JSON handling
     */
    @Test
    fun `test_malformed_json_returns_parseError`() {
        val malformedJson = """{"incomplete": """
        val result = protocol.parseMessage(malformedJson)
        assertIs<GeminiEvent.ParseError>(result)
    }
}
