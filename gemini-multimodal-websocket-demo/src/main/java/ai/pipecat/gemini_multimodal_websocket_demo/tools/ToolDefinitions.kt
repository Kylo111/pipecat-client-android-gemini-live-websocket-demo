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
    
    // Data class for tool metadata
    data class ToolInfo(
        val name: String,
        val friendlyName: String,
        val category: String,
        val description: String
    )

    // Helper map of all available tool definitions for UI
    val ALL_TOOLS_INFO = listOf(
        // System / General
        ToolInfo("google_search", "Google Search Grounding (Natywne)", "System", "Natywne wyszukiwanie Google (zastępuje search_web)"),
        ToolInfo("start_reasoning_task", "Reasoning Agent (Myślenie)", "System", "Głęboka analiza, notatki, Telegram, Perplexity"),
        ToolInfo("create_offline_conversation", "Tworzenie Asystentów", "System", "Tworzenie nowych botów (do rozmowy 'Pomoc')"),
        ToolInfo("search_perplexity", "Perplexity (Online)", "System", "Wyszukiwanie informacji online z cytowaniami"),
        ToolInfo("create_done_item", "Zapisywanie Postępów", "System", "Automatyczne logowanie postępów sesji"),
        
        // Information / Search
        ToolInfo("get_weather", "Pogoda", "Informacje", "Sprawdzanie pogody i prognoz"),
        ToolInfo("get_current_time", "Czas i Data", "Informacje", "Aktualny czas, strefy czasowe"),
        ToolInfo("get_location", "Lokalizacja", "Informacje", "Pobieranie GPS użytkownika"),
        ToolInfo("calculate", "Kalkulator", "Informacje", "Obliczenia matematyczne"),
        ToolInfo("search_nearby", "Miejsca w pobliżu", "Informacje", "Wyszukiwanie restauracji, sklepów itp."),
        ToolInfo("search_on_map", "Szukaj na mapie", "Informacje", "Wyszukiwanie adresu/miejsca w Google Maps"),
        
        // Navigation / Transport
        ToolInfo("start_navigation", "Nawigacja", "Transport", "Nawigacja Google Maps (start)"),
        ToolInfo("find_transit_route", "Komunikacja miejska", "Transport", "Wyszukiwanie połączeń autobusowych/kolejowych"),
        ToolInfo("show_on_map", "Pokaż na mapie", "Transport", "Pokazanie współrzędnych na mapie"),
        
        // Utilities / Actions
        ToolInfo("control_media", "Sterowanie Mediami", "Narzędzia", "Spotify, YouTube Music (play/pause)"),
        ToolInfo("search_contacts", "Kontakty", "Komunikacja", "Szukanie numerów telefonu"),
        ToolInfo("send_sms", "Wysyłanie SMS", "Komunikacja", "Wysyłanie wiadomości tekstowych"),
        ToolInfo("symptom_checker", "Sprawdzanie Objawów", "Zdrowie", "Azure Health Bot (triage medyczny)"),
        
        // Calendar / Reminders / Tasks
        ToolInfo("set_alarm", "Budzik", "Organizator", "Ustawianie budzików w systemie"),
        ToolInfo("create_reminder", "Przypomnienia", "Organizator", "Tworzenie przypomnień"),
        ToolInfo("list_reminders", "Lista Przypomnień", "Organizator", "Przeglądanie przypomnień"),
        ToolInfo("delete_reminder", "Usuń Przypomnienie", "Organizator", "Usuwanie przypomnień"),
        ToolInfo("get_calendar_events", "Kalendarz", "Organizator", "Odczyt wydarzeń z kalendarza"),
        ToolInfo("create_calendar_event", "Dodaj do Kalendarza", "Organizator", "Tworzenie wydarzeń"),
        ToolInfo("delete_calendar_event", "Usuń z Kalendarza", "Organizator", "Usuwanie wydarzeń"),
        ToolInfo("get_todo_tasks", "Lista Zadań (TODO)", "Organizator", "Przeglądanie listy zadań"),
        ToolInfo("add_todo_task", "Dodaj Zadanie", "Organizator", "Dodawanie zadań do listy"),
        ToolInfo("complete_todo_task", "Ukończ Zadanie", "Organizator", "Oznaczanie zadań jako zrobione"),
        ToolInfo("delete_todo_task", "Usuń Zadanie", "Organizator", "Usuwanie zadań"),
        
        // Shopping
        ToolInfo("get_shopping_list", "Lista Zakupów", "Zakupy", "Przeglądanie listy zakupów"),
        ToolInfo("add_to_shopping_list", "Dodaj do Zakupów", "Zakupy", "Dodawanie produktów"),
        ToolInfo("remove_from_shopping_list", "Usuń z Zakupów", "Zakupy", "Usuwanie produktów"),
        ToolInfo("mark_item_purchased", "Oznacz Kupione", "Zakupy", "Oznaczanie jako kupione"),
        ToolInfo("clear_purchased_items", "Wyczyść Kupione", "Zakupy", "Usuwanie kupionych produktów"),
        ToolInfo("cook_process_recipe", "Kitchen: Cook", "Culinary", "Automated recipe fetching, formatting, and shopping list (AniaGotuje & Allrecipes)"),
        ToolInfo("ania_process_recipe", "Kuchnia: Ania", "Kulinaria", "Alias dla narzędzia kucharskiego (AniaGotuje)"),
        ToolInfo("encyclopedia_lookup", "Encyklopedia", "Wiedza", "Szczegółowe opracowania z Wikipedii i bogate notatki")
    )

    // Data class for tool groups in UI
    data class ToolGroup(
        val groupName: String, // Unique ID for the group
        val titleResId: Int, // Display name resource ID
        val descResId: Int, // Description resource ID
        val tools: List<String> // List of tool names in this group
    )

    // Defined groups for UI selection
    val TOOL_GROUPS = listOf(
        // System
        ToolGroup("google_search", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_search_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_search_desc, listOf("google_search")),
        ToolGroup("reasoning", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_reasoning_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_reasoning_desc, listOf("start_reasoning_task")),
        ToolGroup("search_perplexity", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_perplexity_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_perplexity_desc, listOf("search_perplexity")),
        ToolGroup("progress", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_progress_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_progress_desc, listOf("create_done_item")),
        
        // Groups
        ToolGroup("shopping_list", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_shopping_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_shopping_desc, 
            listOf("get_shopping_list", "add_to_shopping_list", "remove_from_shopping_list", "mark_item_purchased", "clear_purchased_items")),
            
        ToolGroup("todo_list", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_todo_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_todo_desc,
            listOf("get_todo_tasks", "add_todo_task", "complete_todo_task", "delete_todo_task")),
            
        ToolGroup("encyclopedia", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_encyclopedia_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_encyclopedia_desc,
            listOf("encyclopedia_lookup")),
            
        ToolGroup("calendar", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_calendar_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_calendar_desc,
            listOf("get_calendar_events", "create_calendar_event", "delete_calendar_event")),
            
        ToolGroup("reminders", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_reminders_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_reminders_desc,
            listOf("create_reminder", "list_reminders", "delete_reminder")),
            
        // Single Tools
        ToolGroup("alarms", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_alarms_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_alarms_desc, listOf("set_alarm")),
        ToolGroup("weather", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_weather_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_weather_desc, listOf("get_weather")),
        ToolGroup("time", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_time_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_time_desc, listOf("get_current_time")),
        ToolGroup("location", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_location_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_location_desc, listOf("get_location")),
        ToolGroup("calculate", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_calculate_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_calculate_desc, listOf("calculate")),
        ToolGroup("places", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_places_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_places_desc, listOf("search_nearby")),
        ToolGroup("map_search", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_map_search_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_map_search_desc, listOf("search_on_map")),
        ToolGroup("navigation", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_navigation_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_navigation_desc, listOf("start_navigation")),
        ToolGroup("transit", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_transit_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_transit_desc, listOf("find_transit_route")),
        ToolGroup("show_map", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_show_map_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_show_map_desc, listOf("show_on_map")),
        ToolGroup("media", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_media_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_media_desc, listOf("control_media")),
        ToolGroup("contacts", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_contacts_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_contacts_desc, listOf("search_contacts")),
        ToolGroup("sms", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_sms_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_sms_desc, listOf("send_sms")),
        ToolGroup("health", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_health_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_health_desc, listOf("symptom_checker")),
        ToolGroup("culinary", ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_culinary_title, ai.pipecat.gemini_multimodal_websocket_demo.R.string.tool_group_culinary_desc, listOf("cook_process_recipe", "ania_process_recipe"))
    )


    /**
     * Get tool declarations filtered by allowed list
     */
    fun getToolsForConversation(context: android.content.Context, allowedTools: List<String>?): List<JsonObject> {
        val allTools = getAllTools(context) // Gets all available tools including integration tools
        
        // If list is null, return all tools (default behavior)
        if (allowedTools == null) return allTools
        
        // Filter tools
        return allTools.filter { toolJson ->
            val toolName = toolJson["name"]?.toString()?.replace("\"", "")
            
            // Special handling for google_search grounding
            if (toolJson.containsKey("google_search")) {
                return@filter allowedTools.contains("google_search")
            }
            
            // Keep if name matches allowed list
            // Also always include critical system tools if needed? No, let user control everything.
            allowedTools.contains(toolName)
        }
    }

    /**
     * Get all tool declarations for Gemini Live API
     * Includes both built-in tools and user-defined custom tools
     * 
     * Tools from system integrations are filtered based on IntegrationManager enabled state.
     * 
     * NOTE: create_offline_conversation is NOT included here - it's only available
     * in the "Help" conversation to prevent accidental creation of duplicate conversations
     */
    fun getAllTools(context: android.content.Context): List<JsonObject> {
        val builtInTools = mutableListOf(
            // searchWebTool(), // REMOVED - Using native googleSearchGrounding instead
            getGoogleSearchGrounding(),
            getWeatherTool(),
            getCurrentTimeTool(),
            getLocationTool(),
            calculateTool(),
            // createNoteTool(), // REMOVED - handled by Reasoning Agent via start_reasoning_task
            controlMediaTool(),
            searchNearbyTool(),
            // createOfflineConversationTool(), // REMOVED - only for "Help" conversation
            startNavigationTool(),
            // copyToClipboardTool(), // REMOVED - handled by Reasoning Agent via start_reasoning_task
            startReasoningTaskTool(),

            searchPerplexityTool(),
            symptomCheckerTool(),
            getCreateDoneItemTool(),
            cookProcessRecipeTool(),
            aniaProcessRecipeTool(),
            encyclopediaLookupTool()
        )
        
        // Add system integration tools based on IntegrationManager enabled state
        val integrationManager = ai.pipecat.gemini_multimodal_websocket_demo.integrations.IntegrationManager(context)
        val integrationTools = integrationManager.getEnabledTools()
        builtInTools.addAll(integrationTools)
        
        // NOTE: These tools are NOT included in Gemini Live - they're handled by Reasoning Agent:
        // - search_perplexity: Deep search with citations (use start_reasoning_task instead)
        // - create_note: Note creation (use start_reasoning_task instead)
        // - copy_to_clipboard: Clipboard operations (use start_reasoning_task instead)
        // - send_telegram: Telegram messaging (use start_reasoning_task instead)
        //
        // Gemini Live uses native Google Search Grounding for quick searches.
        // Complex research, note-taking, and clipboard operations are delegated to Reasoning Agent.
        
        // Add custom tools from user configuration
        val customTools = CustomToolsManager.getCustomToolDeclarations(context)
        
        return builtInTools + customTools
    }
    
    /**
     * Get tools for "Help" conversation - includes create_offline_conversation
     * This special conversation can create new offline conversations for the user
     */
    fun getHelpConversationTools(context: android.content.Context): List<JsonObject> {
        val tools = getAllTools(context).toMutableList()
        tools.add(createOfflineConversationTool())
        return tools
    }
    
    /**
     * Get Google Search grounding configuration
     * This is a special tool that uses Gemini's built-in grounding feature
     * Note: This should be added separately to the setup message, not as a function_declaration
     */
    fun getGoogleSearchGrounding(): JsonObject = buildJsonObject {
        put("google_search", buildJsonObject {})
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
    
    /**
     * Start Google Maps navigation to a destination
     */
    private fun startNavigationTool() = buildJsonObject {
        put("name", "start_navigation")
        put("description", "Start turn-by-turn navigation in Google Maps to a specific destination. Ask the user for the destination address if not provided. Use this when user wants directions, navigation, or to go somewhere.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("destination") {
                    put("type", "string")
                    put("description", "Destination address, place name, or coordinates (e.g., 'Plac Zamkowy, Warsaw', 'Eiffel Tower', '52.2297,21.0122')")
                }
                putJsonObject("mode") {
                    put("type", "string")
                    put("description", "Navigation mode: 'driving' (car), 'walking', 'bicycling', or 'transit' (public transport)")
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("driving"))
                        add(JsonPrimitive("walking"))
                        add(JsonPrimitive("bicycling"))
                        add(JsonPrimitive("transit"))
                    })
                    put("default", "driving")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("destination"))
            })
        }
    }
    
    /**
     * Search using Perplexity Sonar API
     * Provides real-time search with automatic citations
     */
    private fun searchPerplexityTool() = buildJsonObject {
        put("name", "search_perplexity")
        put("description", "Search for current information about political events, news, and real-time data using Perplexity Sonar API. This provides more accurate and up-to-date information than regular web search, with automatic citations. Use this for political news, current events, and complex queries requiring authoritative sources. Supports time-based filtering (hour, day, week, month, year).")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "Search query (e.g., 'latest political events in Poland', 'current news about climate change')")
                }
                putJsonObject("recency_filter") {
                    put("type", "string")
                    put("description", "Time filter for search results: 'hour' (last hour), 'day' (last 24h), 'week' (last 7 days), 'month' (last 30 days), 'year' (last 365 days). Default: no filter (all time)")
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("hour"))
                        add(JsonPrimitive("day"))
                        add(JsonPrimitive("week"))
                        add(JsonPrimitive("month"))
                        add(JsonPrimitive("year"))
                    })
                }
                putJsonObject("max_results") {
                    put("type", "number")
                    put("description", "Maximum number of search results to return (1-20). Default: 5")
                    put("default", 5)
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("query"))
            })
        }
    }
    
    /**
     * Copy text to clipboard
     * Allows the AI to copy text to the system clipboard on user request
     */
    private fun copyToClipboardTool() = buildJsonObject {
        put("name", "copy_to_clipboard")
        put("description", "Copy text to the system clipboard. Use this when the user asks to copy, save to clipboard, or remember text, code snippets, summaries, or any information they want to paste later.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("text") {
                    put("type", "string")
                    put("description", "The text content to copy to the clipboard")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("text"))
            })
        }
    }
    
    /**
     * Start a reasoning task in the background
     * Delegates complex research, note-taking, and analysis to the Reasoning Agent
     */
    private fun startReasoningTaskTool() = buildJsonObject {
        put("name", "start_reasoning_task")
        put("description", """
            Start a background reasoning task for complex research, deep analysis, note-taking, or information gathering that requires external tools (Perplexity search, note creation, clipboard operations, Telegram messages).
            
            Use this when:
            - User asks for deep research or detailed information about a topic
            - User wants to save notes, summaries, or information
            - User requests information you don't have or need to verify
            - Task requires multiple steps or external searches
            - You detect lack of knowledge or need authoritative sources
            
            This is a fire-and-forget operation - you will receive results later when ready.
            Continue the conversation naturally while the task processes in the background.
            
            Examples:
            - "Research the latest developments in quantum computing"
            - "Save a summary of our conversation to my notes"
            - "Find detailed information about the 2024 elections in Poland"
            - "Create a note with the recipe we just discussed"
        """.trimIndent())
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("task_description") {
                    put("type", "string")
                    put("description", "Natural language description of the task. Be specific about what needs to be done. Include context from the conversation if relevant.")
                }
                putJsonObject("priority") {
                    put("type", "string")
                    put("description", "Task priority level")
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("LOW"))
                        add(JsonPrimitive("NORMAL"))
                        add(JsonPrimitive("HIGH"))
                    })
                    put("default", "NORMAL")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("task_description"))
            })
        }
    }
    
    /**
     * Specialized tool for medical symptom checking via Azure Health Bot.
     * Use this when the user describes health issues or symptoms.
     */
    private fun symptomCheckerTool() = buildJsonObject {
        put("name", "symptom_checker")
        put("description", """
            Consult a medical symptom checker (Azure Health Bot). Use this when the user describes symptoms or health issues. 
            Gemini MUST translate non-English input to English before calling this tool.
            
            CRITICAL WORKFLOW:
            1. Translate user symptoms to English -> call symptom_checker(userTextEn=...)
            2. If bot asks questions -> relay them to user -> loop
            3. WHEN EXAM COMPLETED (tool returns status="DONE" and triage info):
               - Summarize the diagnosis to the user
               - IMMEDIATELY trigger a Reasoning Task to generate a detailed medical report:
                 call start_reasoning_task(
                   task_description="GENERATE MEDICAL REPORT: Analyze symptoms [LIST SYMPTOMS] and triage result [TRIAGE]. Search for likely conditions and suggested medications using Perplexity. Create a detailed report with potential diagnoses and treatment options.",
                   priority="HIGH"
                 )
               - Tell the user: "Diagnoza zakończona. Przygotowuję teraz szczegółowy raport z analizą i listą leków..."
        """.trimIndent())
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("userTextEn") {
                    put("type", "string")
                    put("description", "The user's symptoms described in English. Gemini MUST translate non-English input to English.")
                }
                putJsonObject("conversationId") {
                    put("type", "string")
                    put("description", "Existing conversation ID for continuation, if available.")
                }
                putJsonObject("watermark") {
                    put("type", "string")
                    put("description", "The watermark from previous response to fetch only new activities.")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("userTextEn"))
            })
        }
    }
    
    /**
     * Search contacts by name or phone number
     */
    internal fun searchContactsTool() = buildJsonObject {
        put("name", "search_contacts")
        put("description", "Search the user's contacts by name or phone number. Returns matching contacts with their phone numbers. Use this when the user asks to find a contact, look up a phone number, or wants to send a message to someone.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "Search query - can be a name (e.g., 'John Smith') or phone number (e.g., '555-1234')")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("query"))
            })
        }
    }
    
    /**
     * Send SMS message
     */
    internal fun sendSmsTool() = buildJsonObject {
        put("name", "send_sms")
        put("description", "Open the SMS app with a pre-filled message ready to send. The user will review and send the message. Use this when the user asks to send a text message, SMS, or message someone. Either contact_name or phone_number must be provided (phone_number takes precedence if both are given).")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("contact_name") {
                    put("type", "string")
                    put("description", "Name of the contact to send SMS to (optional if phone_number is provided)")
                }
                putJsonObject("phone_number") {
                    put("type", "string")
                    put("description", "Phone number to send SMS to (optional if contact_name is provided). Takes precedence over contact_name if both are provided.")
                }
                putJsonObject("message") {
                    put("type", "string")
                    put("description", "The message text to send")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("message"))
            })
        }
    }
    
    fun getCreateDoneItemTool(): JsonObject {
        return buildJsonObject {
            put("name", "create_done_item")
            put("description", "Log a completed learning objective or topic covered in the session. Use this when the session ends to summarize what was achieved.")
            put("parameters", buildJsonObject {
                put("type", "OBJECT")
                put("properties", buildJsonObject {
                    put("description", buildJsonObject {
                        put("type", "STRING")
                        put("description", "A concise summary of what was done/learned (max 1 sentence).")
                    })
                    put("topic", buildJsonObject {
                        put("type", "STRING")
                        put("description", "A short tag or topic name (e.g. 'Past Tense', 'Thermodynamics'). Max 3 words.")
                    })
                })
                put("required", buildJsonArray {
                    add(JsonPrimitive("description"))
                    add(JsonPrimitive("topic"))
                })
            })
        }
    }
    
    /**
     * Set a recurring system alarm
     */
    internal fun setAlarmTool() = buildJsonObject {
        put("name", "set_alarm")
        put("description", "Set a recurring alarm that will ring at the specified time on selected days (e.g., 'every day at 7:00', 'weekdays at 6:30'). This creates a real alarm in the system Clock app. Use this for alarms that repeat on specific days of the week. For one-time reminders on specific dates, use create_reminder instead. IMPORTANT: You CAN and SHOULD use this tool when the user asks to set an alarm.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("hour") {
                    put("type", "integer")
                    put("description", "Hour of day (0-23)")
                }
                putJsonObject("minutes") {
                    put("type", "integer")
                    put("description", "Minutes (0-59)")
                }
                putJsonObject("days") {
                    put("type", "array")
                    put("description", "Days of week when alarm should repeat. Use Calendar constants: 1=Sunday, 2=Monday, 3=Tuesday, 4=Wednesday, 5=Thursday, 6=Friday, 7=Saturday. Omit for one-time alarm.")
                    putJsonObject("items") {
                        put("type", "integer")
                    }
                }
                putJsonObject("label") {
                    put("type", "string")
                    put("description", "Optional label/message for the alarm")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("hour"))
                add(JsonPrimitive("minutes"))
            })
        }
    }
    
    /**
     * Create a reminder for a specific date and time
     */
    internal fun createReminderTool() = buildJsonObject {
        put("name", "create_reminder")
        put("description", "Create a reminder for a specific date and time (e.g., 'remind me tomorrow at 3pm', 'reminder on December 25 at 8:00'). The reminder will show a notification at the specified time. Use this for one-time reminders on specific dates. For recurring alarms, use set_alarm instead.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("title") {
                    put("type", "string")
                    put("description", "Title/description of the reminder")
                }
                putJsonObject("date") {
                    put("type", "string")
                    put("description", "Date in ISO format (YYYY-MM-DD)")
                }
                putJsonObject("time") {
                    put("type", "string")
                    put("description", "Time in 24-hour format (HH:MM)")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("title"))
                add(JsonPrimitive("date"))
                add(JsonPrimitive("time"))
            })
        }
    }
    
    /**
     * List all active reminders
     */
    internal fun listRemindersTool() = buildJsonObject {
        put("name", "list_reminders")
        put("description", "Get a list of all active reminders. Returns reminders sorted by date/time. Use this when the user asks to see their reminders, check what reminders they have, or wants to know about upcoming reminders.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                // No parameters needed
            }
        }
    }
    
    /**
     * Delete a reminder
     */
    internal fun deleteReminderTool() = buildJsonObject {
        put("name", "delete_reminder")
        put("description", "Delete a specific reminder by its ID. Use this when the user asks to cancel, remove, or delete a reminder. You must first call list_reminders to get the reminder ID.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("reminder_id") {
                    put("type", "integer")
                    put("description", "ID of the reminder to delete (obtained from list_reminders)")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("reminder_id"))
            })
        }
    }
    
    /**
     * Get calendar events for a specific date
     */
    internal fun getCalendarEventsTool() = buildJsonObject {
        put("name", "get_calendar_events")
        put("description", "Get calendar events for a specific date. Returns all events scheduled for that day with their titles, times, and descriptions. Use this when the user asks about their schedule, what they have planned, or events on a specific day.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("date") {
                    put("type", "string")
                    put("description", "Date in ISO format (YYYY-MM-DD). Use 'today' for current date, 'tomorrow' for next day.")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("date"))
            })
        }
    }
    
    /**
     * Create a new calendar event
     */
    internal fun createCalendarEventTool() = buildJsonObject {
        put("name", "create_calendar_event")
        put("description", "Create a new calendar event. If WRITE_CALENDAR permission is granted, creates the event directly. Otherwise, opens the calendar app with pre-filled event data for user to confirm. Use this when the user wants to schedule an appointment, meeting, or event.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("title") {
                    put("type", "string")
                    put("description", "Event title/name")
                }
                putJsonObject("start_date") {
                    put("type", "string")
                    put("description", "Start date in ISO format (YYYY-MM-DD)")
                }
                putJsonObject("start_time") {
                    put("type", "string")
                    put("description", "Start time in 24-hour format (HH:MM)")
                }
                putJsonObject("end_date") {
                    put("type", "string")
                    put("description", "End date in ISO format (YYYY-MM-DD). If not provided, uses start_date.")
                }
                putJsonObject("end_time") {
                    put("type", "string")
                    put("description", "End time in 24-hour format (HH:MM)")
                }
                putJsonObject("description") {
                    put("type", "string")
                    put("description", "Optional event description/notes")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("title"))
                add(JsonPrimitive("start_date"))
                add(JsonPrimitive("start_time"))
                add(JsonPrimitive("end_time"))
            })
        }
    }
    
    /**
     * Delete a calendar event
     */
    internal fun deleteCalendarEventTool() = buildJsonObject {
        put("name", "delete_calendar_event")
        put("description", "Delete a calendar event by its ID. Use this when the user asks to cancel or remove an event. You must first call get_calendar_events to get the event ID.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("event_id") {
                    put("type", "integer")
                    put("description", "ID of the calendar event to delete (obtained from get_calendar_events)")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("event_id"))
            })
        }
    }
    
    /**
     * Get TODO tasks
     */
    internal fun getTodoTasksTool() = buildJsonObject {
        put("name", "get_todo_tasks")
        put("description", "Get the user's TODO tasks. Can optionally filter by a specific date to show only tasks due on that date. Use this when the user asks about their tasks, to-do list, or what they need to do.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("date") {
                    put("type", "string")
                    put("description", "Optional date to filter tasks (format: YYYY-MM-DD). If provided, only returns tasks due on this date. If omitted, returns all tasks.")
                }
            }
        }
    }
    
    /**
     * Add a TODO task
     */
    internal fun addTodoTaskTool() = buildJsonObject {
        put("name", "add_todo_task")
        put("description", "Add a new task to the TODO list. Can optionally set a due date and priority. Use this when the user asks to add a task, remember to do something, or create a to-do item.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("title") {
                    put("type", "string")
                    put("description", "Title/description of the task")
                }
                putJsonObject("due_date") {
                    put("type", "string")
                    put("description", "Optional due date and time for the task (format: YYYY-MM-DDTHH:MM:SS). If provided, a reminder will be created.")
                }
                putJsonObject("priority") {
                    put("type", "string")
                    put("description", "Priority level of the task")
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("LOW"))
                        add(JsonPrimitive("NORMAL"))
                        add(JsonPrimitive("HIGH"))
                    })
                    put("default", "NORMAL")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("title"))
            })
        }
    }
    
    /**
     * Mark a TODO task as complete
     */
    internal fun completeTodoTaskTool() = buildJsonObject {
        put("name", "complete_todo_task")
        put("description", "Mark a TODO task as completed. Use this when the user says they finished a task or want to check it off. You must first call get_todo_tasks to get the task ID.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("task_id") {
                    put("type", "integer")
                    put("description", "ID of the task to mark as complete (obtained from get_todo_tasks)")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("task_id"))
            })
        }
    }
    
    /**
     * Delete a TODO task
     */
    internal fun deleteTodoTaskTool() = buildJsonObject {
        put("name", "delete_todo_task")
        put("description", "Delete a TODO task. Use this when the user asks to remove or delete a task. You must first call get_todo_tasks to get the task ID.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("task_id") {
                    put("type", "integer")
                    put("description", "ID of the task to delete (obtained from get_todo_tasks)")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("task_id"))
            })
        }
    }
    
    /**
     * Navigate to a destination using Google Maps
     */
    internal fun navigateToTool() = buildJsonObject {
        put("name", "navigate_to")
        put("description", "Start turn-by-turn navigation to a destination using Google Maps. Use this when the user wants directions, navigation, or to go somewhere. Supports driving, walking, bicycling, and two-wheeler modes.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("destination") {
                    put("type", "string")
                    put("description", "Destination address, place name, or coordinates (e.g., 'Plac Zamkowy, Warsaw', 'Eiffel Tower', '52.2297,21.0122')")
                }
                putJsonObject("mode") {
                    put("type", "string")
                    put("description", "Navigation mode: 'driving' (car), 'walking' (pedestrian), 'bicycling' (bike), or 'two_wheeler' (motorcycle/scooter)")
                    put("enum", buildJsonArray {
                        add(JsonPrimitive("driving"))
                        add(JsonPrimitive("walking"))
                        add(JsonPrimitive("bicycling"))
                        add(JsonPrimitive("two_wheeler"))
                    })
                    put("default", "driving")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("destination"))
            })
        }
    }
    
    /**
     * Search for a place on Google Maps
     */
    internal fun searchOnMapTool() = buildJsonObject {
        put("name", "search_on_map")
        put("description", "Search for a place, business, or address on Google Maps. Opens Google Maps with search results. Use this when the user wants to find a location, look up a place, or see where something is on the map.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "Search query - can be a place name, business name, address, or type of place (e.g., 'restaurants near me', 'Central Park', '123 Main St')")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("query"))
            })
        }
    }
    
    /**
     * Show a specific location on Google Maps
     */
    internal fun showOnMapTool() = buildJsonObject {
        put("name", "show_on_map")
        put("description", "Show a specific location on Google Maps using coordinates. Opens Google Maps centered on the specified latitude and longitude with an optional label. Use this when you have exact coordinates to display.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("latitude") {
                    put("type", "number")
                    put("description", "Latitude coordinate (e.g., 52.2297)")
                }
                putJsonObject("longitude") {
                    put("type", "number")
                    put("description", "Longitude coordinate (e.g., 21.0122)")
                }
                putJsonObject("label") {
                    put("type", "string")
                    put("description", "Optional label/name for the location")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("latitude"))
                add(JsonPrimitive("longitude"))
            })
        }
    }
    
    /**
     * Find public transit route
     */
    internal fun findTransitRouteTool() = buildJsonObject {
        put("name", "find_transit_route")
        put("description", "Find REAL public transit routes (buses, trams, trains, subways) with ACTUAL departure times and schedules from Google Directions API. Returns detailed route information including exact departure times, line numbers, stops, and walking directions. IMPORTANT: You MUST use this tool when the user asks about buses, trams, trains, or public transportation - DO NOT make up or hallucinate schedules! This provides real-time transit data. Note: For turn-by-turn navigation by car/bike/walking, use navigate_to instead.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("destination") {
                    put("type", "string")
                    put("description", "Destination address or place name WITH CITY (e.g., 'Plac Zamkowy, Warsaw' NOT just 'Plac Zamkowy', 'Central Station, Krakow' NOT just 'Central Station'). IMPORTANT: Always include city name!")
                }
                putJsonObject("origin") {
                    put("type", "string")
                    put("description", "Origin address or place name WITH CITY (e.g., 'Plac Zamkowy, Warsaw' not just 'Plac Zamkowy'). If not provided, will use current GPS location (requires location permission). Can also be 'current' or 'my location' to explicitly use GPS. IMPORTANT: Always include city name in addresses!")
                }
                putJsonObject("departure_time") {
                    put("type", "string")
                    put("description", "Desired departure time in ISO format (YYYY-MM-DDTHH:MM). Use 'now' for immediate departure. Mutually exclusive with arrival_time.")
                }
                putJsonObject("arrival_time") {
                    put("type", "string")
                    put("description", "Desired arrival time in ISO format (YYYY-MM-DDTHH:MM). Use this when user wants to arrive by a specific time. Mutually exclusive with departure_time.")
                }
                putJsonObject("alternatives") {
                    put("type", "boolean")
                    put("description", "Request alternative routes if available. Note: API may still return only one route.")
                    put("default", false)
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("destination"))
            })
        }
    }
    
    /**
     * Get shopping list
     */
    internal fun getShoppingListTool() = buildJsonObject {
        put("name", "get_shopping_list")
        put("description", "Get the user's shopping list with all items grouped by category (dairy, bread, vegetables, fruits, meat, fish, frozen, drinks, sweets, household, other). Shows which items are purchased and which are still needed. Use this when the user asks about their shopping list or what they need to buy.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                // No parameters needed
            }
        }
    }
    
    /**
     * Add items to shopping list
     */
    internal fun addToShoppingListTool() = buildJsonObject {
        put("name", "add_to_shopping_list")
        put("description", "Add one or more items to the shopping list. VERY IMPORTANT: You SHOULD categorize each item yourself to ensure they are grouped correctly. Available categories: Owoce i Warzywa, Pieczywo, Nabiał, Mięso i Wędliny, Ryby i Owoce Morza, Produkty Sypkie, Przetwory i Sosy, Bakalie i Przyprawy, Napoje, Słodycze i Przekąski, Mrożonki, Chemia i Kosmetyki. Format for each item: 'name | category' (e.g., 'mleko | Nabiał', '2 kg mąki | Produkty Sypkie'). If you don't provide a category, the system will try to guess it.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("items") {
                    put("type", "array")
                    put("description", "List of items to add. Each item can be just a name (e.g., 'milk') or include quantity and category (e.g., 'milk | Nabiał', '3 apples | Owoce')")
                    putJsonObject("items") {
                        put("type", "string")
                    }
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("items"))
            })
        }
    }
    
    /**
     * Remove item from shopping list
     */
    internal fun removeFromShoppingListTool() = buildJsonObject {
        put("name", "remove_from_shopping_list")
        put("description", "Remove an item from the shopping list by name. If multiple items have the same name, returns a list of matches for the user to choose from (they should then specify the item_id). Use this when the user wants to delete or remove an item from their shopping list.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("item_name") {
                    put("type", "string")
                    put("description", "Name of the item to remove (e.g., 'milk', 'bread'). If multiple items match, will return list of matches.")
                }
                putJsonObject("item_id") {
                    put("type", "integer")
                    put("description", "Optional item ID for removing a specific item when multiple items have the same name. Get this from get_shopping_list.")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("item_name"))
            })
        }
    }
    
    /**
     * Mark item as purchased
     */
    internal fun markItemPurchasedTool() = buildJsonObject {
        put("name", "mark_item_purchased")
        put("description", "Mark an item as purchased/bought on the shopping list. If multiple items have the same name, returns a list of matches for the user to choose from (they should then specify the item_id). Use this when the user says they bought something or wants to check off an item.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("item_name") {
                    put("type", "string")
                    put("description", "Name of the item to mark as purchased (e.g., 'milk', 'bread'). If multiple items match, will return list of matches.")
                }
                putJsonObject("item_id") {
                    put("type", "integer")
                    put("description", "Optional item ID for marking a specific item when multiple items have the same name. Get this from get_shopping_list.")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("item_name"))
            })
        }
    }
    
    /**
     * Clear purchased items from shopping list
     */
    internal fun clearPurchasedItemsTool() = buildJsonObject {
        put("name", "clear_purchased_items")
        put("description", "Remove all purchased/bought items from the shopping list. Use this when the user wants to clean up their shopping list after shopping or remove all checked items.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                // No parameters needed
            }
        }
    }

    const val TOOL_COOK_PROCESS_RECIPE = "cook_process_recipe"
    internal fun cookProcessRecipeTool() = buildJsonObject {
        put("name", TOOL_COOK_PROCESS_RECIPE)
        put("description", "Fetches and formats recipes from aniagotuje.pl and allrecipes.com, and optionally adds ingredients to shopping list.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "Name of the dish (e.g., 'pancakes') or search query.")
                }
                putJsonObject("url") {
                    put("type", "string")
                    put("description", "Optional: Direct URL to the recipe on aniagotuje.pl or allrecipes.com")
                }
                putJsonObject("should_add_shopping_list") {
                    put("type", "boolean")
                    put("description", "Whether to automatically add ingredients to the shopping list. Default is true.")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("query"))
            })
        }
    }

    /**
     * Alias for backward compatibility and user preference.
     */
    internal fun aniaProcessRecipeTool() = buildJsonObject {
        put("name", "ania_process_recipe")
        put("description", "Pobierz, sformatuj i zapisz przepis z aniagotuje.pl oraz zaktualizuj listę zakupów.")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "Nazwa potrawy lub zapytanie wyszukiwania.")
                }
                putJsonObject("should_add_shopping_list") {
                    put("type", "boolean")
                    put("description", "Czy automatycznie dodać składniki do listy zakupów. Domyślnie true.")
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("query"))
            })
        }
    }

    fun encyclopediaLookupTool() = buildJsonObject {
        put("name", "encyclopedia_lookup")
        put("description", """
            Lookup detailed information from Wikipedia (English) and generate a comprehensive, rich Markdown note in the user's language.
            Use this when the user confirms they want a detailed research note, article, or encyclopedia entry on a specific topic.
            This tool fetches images, tables, and structured data, translates it, and saves it as a new note.
        """.trimIndent())
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "The specific topic or entity to look up on Wikipedia (e.g., 'Marie Curie', 'Quantum Mechanics', 'History of Poland').")
                }
                putJsonObject("exhaustive_note") {
                    put("type", "boolean")
                    put("description", "Whether to generate a full, rich note with images and tables. Default is true.")
                    put("default", true)
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("query"))
            })
        }
    }
}
