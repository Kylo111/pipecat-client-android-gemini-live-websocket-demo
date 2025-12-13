package ai.pipecat.gemini_multimodal_websocket_demo.models

/**
 * Result of executing an action through ActionExecutor.
 */
sealed class ActionResult {
    object Success : ActionResult()
    data class Error(val message: String) : ActionResult()
    object Skipped : ActionResult()  // NO_ACTION or disabled
}