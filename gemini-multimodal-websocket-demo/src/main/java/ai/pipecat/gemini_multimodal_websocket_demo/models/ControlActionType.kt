package ai.pipecat.gemini_multimodal_websocket_demo.models

import kotlinx.serialization.Serializable

/**
 * Enum representing the types of actions that the Control Agent can decide to take.
 */
@Serializable
enum class ControlActionType {
    NO_ACTION,          // Default - let Gemini Live handle it
    MUTE,               // Pause microphone, interrupt Gemini
    HANGUP,             // End session, interrupt Gemini
    SWITCH_CONVERSATION, // Switch to different conversation
    TOOL_USE,           // Execute a tool
    REASONING_TASK      // Delegate to Reasoning Agent (async)
}