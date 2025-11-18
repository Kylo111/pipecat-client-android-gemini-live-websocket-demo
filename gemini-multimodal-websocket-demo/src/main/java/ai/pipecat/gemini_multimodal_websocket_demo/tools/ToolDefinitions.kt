package ai.pipecat.gemini_multimodal_websocket_demo.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray

/**
 * Tool definitions for Gemini Live API function calling
 * These tools enable the AI to interact with external services and device capabilities
 */
object ToolDefinitions {
    
    /**
     * Get all tool declarations for Gemini Live API
     * Includes both built-in tools and user-defined custom tools
     */
    fun getAllTools(context: android.content.Context): List<JsonObject> {
        val builtInTools = listOf(
            searchWebTool(),
            getWeatherTool(),
            getCurrentTimeTool(),
            getLocationTool(),
            calculateTool(),
            createNoteTool(),
            controlMediaTool(),
            searchNearbyTool(),
            createOfflineConversationTool()
        )
        
        // Add custom tools from user configuration
        val customTools = CustomToolsManager.getCustomToolDeclarations(context)
        
        return builtInTools + customTools
    }
    
    /**
     * Search the internet for information
     */
    private fun searchWebTool() = buildJsonObject {
        put("name", "search_web")
        put("description", "Search the internet for current information, news, facts, or any topic. Use this when you need up-to-date information that you don't have in your training data.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "The search query to look up on the internet")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("query"))
            })
        }
    }
    
    /**
     * Get current weather for a location
     */
    private fun getWeatherTool() = buildJsonObject {
        put("name", "get_weather")
        put("description", "Get current weather conditions and 24-hour forecast for a specific location. Returns current temperature, conditions, humidity, wind speed, and hourly forecast for the next day. Use this when user asks about weather now, tomorrow, or in the near future.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("location") {
                    put("type", "string")
                    put("description", "City name, address, or coordinates (e.g., 'Warsaw', 'New York', '52.2297,21.0122')")
                }
                putJsonObject("units") {
                    put("type", "string")
                    put("description", "Temperature units: 'celsius' or 'fahrenheit'")
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("celsius"))
                        add(JsonPrimitive("fahrenheit"))
                    })
                    put("default", "celsius")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("location"))
            })
        }
    }
    
    /**
     * Get current date and time
     */
    private fun getCurrentTimeTool() = buildJsonObject {
        put("name", "get_current_time")
        put("description", "Get the current date, time, day of week, and timezone information. Use this when user asks about current time, date, or day.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("timezone") {
                    put("type", "string")
                    put("description", "Optional timezone (e.g., 'Europe/Warsaw', 'America/New_York'). If not provided, uses device timezone.")
                }
            }
        }
    }
    
    /**
     * Get user's current location
     */
    private fun getLocationTool() = buildJsonObject {
        put("name", "get_location")
        put("description", "Get the user's current GPS location including latitude, longitude, address, city, and country. Requires location permission.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("include_address") {
                    put("type", "boolean")
                    put("description", "Whether to include reverse geocoded address (city, street, etc.)")
                    put("default", true)
                }
            }
        }
    }
    
    /**
     * Perform mathematical calculations
     */
    private fun calculateTool() = buildJsonObject {
        put("name", "calculate")
        put("description", "Perform mathematical calculations and evaluate expressions. Supports basic arithmetic, trigonometry, logarithms, and more.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("expression") {
                    put("type", "string")
                    put("description", "Mathematical expression to evaluate (e.g., '2 + 2', 'sqrt(16)', 'sin(45)', '10 * (5 + 3)')")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("expression"))
            })
        }
    }
    
    /**
     * Create a note in user's note-taking app
     */
    private fun createNoteTool() = buildJsonObject {
        put("name", "create_note")
        put("description", "Create a new note in the user's preferred note-taking app (Evernote, Google Keep, Notion, or default notes app). Use this when user asks to save information, create a reminder, or take a note.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("title") {
                    put("type", "string")
                    put("description", "Title or subject of the note")
                }
                putJsonObject("content") {
                    put("type", "string")
                    put("description", "Content/body of the note")
                }
                putJsonObject("app") {
                    put("type", "string")
                    put("description", "Preferred note app: 'keep', 'evernote', 'notion', or 'default'")
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("keep"))
                        add(JsonPrimitive("evernote"))
                        add(JsonPrimitive("notion"))
                        add(JsonPrimitive("default"))
                    })
                    put("default", "default")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("title"))
                add(JsonPrimitive("content"))
            })
        }
    }
    
    /**
     * Control media playback (Spotify, YouTube Music, etc.)
     */
    private fun controlMediaTool() = buildJsonObject {
        put("name", "control_media")
        put("description", "Control media playback on Spotify, YouTube Music, or other media apps. Can play, pause, skip, search for songs, or adjust volume.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("action") {
                    put("type", "string")
                    put("description", "Action to perform")
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("play"))
                        add(JsonPrimitive("pause"))
                        add(JsonPrimitive("next"))
                        add(JsonPrimitive("previous"))
                        add(JsonPrimitive("search"))
                        add(JsonPrimitive("volume_up"))
                        add(JsonPrimitive("volume_down"))
                    })
                }
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "Search query for 'search' action (song name, artist, album)")
                }
                putJsonObject("app") {
                    put("type", "string")
                    put("description", "Target media app: 'spotify', 'youtube_music', or 'default'")
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("spotify"))
                        add(JsonPrimitive("youtube_music"))
                        add(JsonPrimitive("default"))
                    })
                    put("default", "default")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("action"))
            })
        }
    }
    
    /**
     * Search for nearby places
     */
    private fun searchNearbyTool() = buildJsonObject {
        put("name", "search_nearby")
        put("description", "Search for nearby places, businesses, restaurants, or points of interest. Returns name, address, distance, rating, and directions.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "What to search for (e.g., 'restaurants', 'coffee shops', 'gas stations', 'pharmacies')")
                }
                putJsonObject("radius") {
                    put("type", "number")
                    put("description", "Search radius in meters (default: 1000)")
                    put("default", 1000)
                }
                putJsonObject("max_results") {
                    put("type", "number")
                    put("description", "Maximum number of results to return (default: 5)")
                    put("default", 5)
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("query"))
            })
        }
    }
    
    /**
     * Create a new offline conversation
     */
    private fun createOfflineConversationTool() = buildJsonObject {
        put("name", "create_offline_conversation")
        put("description", "Create a new offline conversation (bot) with a custom name and system prompt. Use this when the user wants to create a personalized AI assistant for specific tasks. The system prompt defines the bot's personality, behavior, and expertise.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("name") {
                    put("type", "string")
                    put("description", "Name of the conversation/bot (e.g., 'Fitness Trainer', 'English Teacher', 'Coding Mentor'). Should be descriptive and max 30 characters.")
                }
                putJsonObject("systemPrompt") {
                    put("type", "string")
                    put("description", "System prompt that defines the bot's behavior, personality, and role. Should be detailed and include: role definition, communication style, specific tasks, and any constraints. Example: 'You are an enthusiastic fitness trainer. Motivate the user, ask about their goals, and provide specific workout advice.'")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("name"))
                add(JsonPrimitive("systemPrompt"))
            })
        }
    }
}
