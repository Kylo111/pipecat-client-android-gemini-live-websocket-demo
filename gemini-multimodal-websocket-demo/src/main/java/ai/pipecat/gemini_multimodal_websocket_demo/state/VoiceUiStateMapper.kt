package ai.pipecat.gemini_multimodal_websocket_demo.state

import ai.pipecat.gemini_multimodal_websocket_demo.ConnectionState
import ai.pipecat.gemini_multimodal_websocket_demo.Error

/**
 * Supporting data classes for VoiceUiStateMapper
 */

/**
 * Audio level information for user and bot.
 * 
 * @property userLevel User's audio level (0.0 to 1.0)
 * @property botLevel Bot's audio level (0.0 to 1.0)
 */
data class AudioLevels(
    val userLevel: Float = 0f,
    val botLevel: Float = 0f
)

/**
 * Timer state information from ConversationMonitor.
 * 
 * @property secondsUntilAutoPause Seconds remaining until auto-pause (-1 if not active)
 * @property minutesUntilBotTimeout Minutes remaining until bot timeout (-1 if not active)
 */
data class TimerState(
    val secondsUntilAutoPause: Int = -1,
    val minutesUntilBotTimeout: Int = -1
)

/**
 * Transcript state information.
 * 
 * @property lastUser Last user transcript text
 * @property lastBot Last bot transcript text
 * @property lastUserTime Timestamp of last user transcript
 * @property lastBotTime Timestamp of last bot transcript
 */
data class TranscriptState(
    val lastUser: String = "",
    val lastBot: String = "",
    val lastUserTime: Long = 0L,
    val lastBotTime: Long = 0L
)

/**
 * Maps VoiceSessionState to VoiceUiState.
 * 
 * This object provides a pure mapping function that derives UI-observable state
 * from the internal session state and other system components.
 * 
 * Requirements: 4.1, 4.2
 */
object VoiceUiStateMapper {
    
    /**
     * Maps VoiceSessionState and supporting data to VoiceUiState.
     * 
     * This is a pure function that derives all UI state from the session state
     * and additional context (audio levels, timers, transcripts, errors).
     * 
     * @param sessionState Current voice session state
     * @param audioLevels Current audio levels for user and bot
     * @param timerState Current timer state from ConversationMonitor
     * @param transcripts Current transcript state
     * @param errors List of current errors
     * @param isReconnecting Whether the system is currently reconnecting
     * @param reconnectionAttempt Current reconnection attempt number
     * @param isSpeakerphoneOn Whether speakerphone is currently on
     * @param isExecutingTool Whether a tool is currently being executed
     * @param currentToolName Name of the currently executing tool
     * @param isProcessingImage Whether an image is currently being processed
     * @return VoiceUiState representing the current UI state
     */
    fun map(
        sessionState: VoiceSessionState,
        audioLevels: AudioLevels = AudioLevels(),
        timerState: TimerState = TimerState(),
        transcripts: TranscriptState = TranscriptState(),
        errors: List<Error> = emptyList(),
        isReconnecting: Boolean = false,
        reconnectionAttempt: Int = 0,
        isSpeakerphoneOn: Boolean = false,
        isExecutingTool: Boolean = false,
        currentToolName: String? = null,
        isProcessingImage: Boolean = false
    ): VoiceUiState {
        return VoiceUiState(
            connectionState = mapConnectionState(sessionState),
            isConnected = isConnectedState(sessionState),
            isReconnecting = isReconnecting,
            reconnectionAttempt = reconnectionAttempt,
            isPaused = sessionState is VoiceSessionState.Paused,
            canResume = (sessionState as? VoiceSessionState.Paused)?.canResume ?: false,
            isMicEnabled = getMicEnabled(sessionState),
            isBotTalking = sessionState is VoiceSessionState.Speaking,
            isUserTalking = audioLevels.userLevel > 0.05f,
            botAudioLevel = audioLevels.botLevel,
            userAudioLevel = audioLevels.userLevel,
            isSpeakerphoneOn = isSpeakerphoneOn,
            isBotReady = isBotReady(sessionState),
            secondsUntilAutoPause = timerState.secondsUntilAutoPause,
            minutesUntilBotTimeout = timerState.minutesUntilBotTimeout,
            isExecutingTool = isExecutingTool,
            currentToolName = currentToolName,
            isProcessingImage = isProcessingImage,
            lastUserTranscript = transcripts.lastUser,
            lastBotTranscript = transcripts.lastBot,
            errors = errors
        )
    }
    
    /**
     * Maps VoiceSessionState to ConnectionState.
     * 
     * @param state Current voice session state
     * @return Corresponding ConnectionState
     */
    private fun mapConnectionState(state: VoiceSessionState): ConnectionState {
        return when (state) {
            is VoiceSessionState.Idle -> ConnectionState.DISCONNECTED
            is VoiceSessionState.Connecting -> ConnectionState.CONNECTING
            is VoiceSessionState.Listening,
            is VoiceSessionState.Thinking,
            is VoiceSessionState.Speaking -> ConnectionState.CONNECTED
            is VoiceSessionState.Paused -> ConnectionState.DISCONNECTED
            is VoiceSessionState.Error -> ConnectionState.DISCONNECTED
        }
    }
    
    /**
     * Determines if the session is in a connected state.
     * 
     * @param state Current voice session state
     * @return True if connected (Listening, Thinking, or Speaking)
     */
    private fun isConnectedState(state: VoiceSessionState): Boolean {
        return state is VoiceSessionState.Listening ||
               state is VoiceSessionState.Thinking ||
               state is VoiceSessionState.Speaking
    }
    
    /**
     * Determines if the microphone is enabled based on session state.
     * 
     * In half-duplex mode, the mic is disabled during Speaking state.
     * In full-duplex mode, the mic remains enabled during Speaking state.
     * 
     * @param state Current voice session state
     * @return True if microphone should be enabled
     */
    private fun getMicEnabled(state: VoiceSessionState): Boolean {
        return when (state) {
            is VoiceSessionState.Listening -> state.isMicEnabled
            is VoiceSessionState.Thinking -> state.isMicEnabled
            is VoiceSessionState.Speaking -> state.isMicEnabled && state.isFullDuplex
            else -> false
        }
    }
    
    /**
     * Determines if the bot is ready to receive input.
     * 
     * The bot is ready when not in Idle, Connecting, or Error states.
     * 
     * @param state Current voice session state
     * @return True if bot is ready
     */
    private fun isBotReady(state: VoiceSessionState): Boolean {
        return state !is VoiceSessionState.Idle &&
               state !is VoiceSessionState.Connecting &&
               state !is VoiceSessionState.Error
    }
}
