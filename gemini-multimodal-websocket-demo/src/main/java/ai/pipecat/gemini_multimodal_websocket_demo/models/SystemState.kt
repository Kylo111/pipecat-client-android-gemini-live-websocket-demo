package ai.pipecat.gemini_multimodal_websocket_demo.models

import kotlinx.serialization.Serializable

/**
 * Minimal system state context for Control Agent.
 */
@Serializable
data class SystemState(
    val isMediaPlaying: Boolean = false,
    val currentAudioState: AudioState = AudioState.IDLE,
    val availableTools: List<String> = emptyList()
)

@Serializable
enum class AudioState {
    IDLE,
    RECORDING,
    PLAYING_TTS,
    PLAYING_MEDIA
}