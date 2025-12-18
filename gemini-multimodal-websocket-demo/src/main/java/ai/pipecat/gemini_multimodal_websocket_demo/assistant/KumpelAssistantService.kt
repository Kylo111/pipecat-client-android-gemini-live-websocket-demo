package ai.pipecat.gemini_multimodal_websocket_demo.assistant

import android.service.voice.VoiceInteractionService

/**
 * Voice Interaction Service required for the app to be a system assistant.
 * This service is registered in AndroidManifest.xml and allows the system
 * to recognize this app as a valid assistant option.
 */
class KumpelAssistantService : VoiceInteractionService() {
    
    override fun onReady() {
        super.onReady()
        // Service is ready - no additional setup needed
    }
}
