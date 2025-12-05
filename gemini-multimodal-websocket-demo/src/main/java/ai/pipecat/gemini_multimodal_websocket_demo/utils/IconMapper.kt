package ai.pipecat.gemini_multimodal_websocket_demo.utils

import ai.pipecat.gemini_multimodal_websocket_demo.R

/**
 * Maps string icon identifiers to drawable resource IDs for marketplace templates.
 * 
 * This utility provides a centralized mapping between JSON-based icon identifiers
 * and Android drawable resources, enabling type-safe resource access while maintaining
 * flexibility in the configuration file.
 * 
 * **Validates: Requirements 8.3**
 */
object IconMapper {
    
    /**
     * Map of icon identifier strings to drawable resource IDs.
     * 
     * Supported identifiers:
     * - "robot" - AI/bot assistant icon
     * - "teacher" - Educational assistant icon
     * - "chef" - Cooking/recipe assistant icon
     * - "code" - Programming/development assistant icon
     * - "travel" - Travel guide assistant icon
     * - "help" - Help/support assistant icon
     */
    private val iconMap = mapOf(
        "robot" to R.drawable.console,        // Console represents AI/technical assistant
        "teacher" to R.drawable.help_circle,  // Help circle for educational content
        "chef" to R.drawable.image_gallery,   // Gallery for visual/creative content
        "code" to R.drawable.console,         // Console for coding assistants
        "travel" to R.drawable.image,         // Image for visual/travel content
        "help" to R.drawable.help_circle      // Help circle for support
    )
    
    /**
     * Retrieves the drawable resource ID for the given icon identifier.
     * 
     * @param identifier The icon identifier string from the template configuration,
     *                   or null if no identifier is specified
     * @return The drawable resource ID corresponding to the identifier,
     *         or the default icon if the identifier is null or not found
     * 
     * **Examples:**
     * ```kotlin
     * val robotIcon = IconMapper.getIconResource("robot")  // Returns R.drawable.console
     * val unknownIcon = IconMapper.getIconResource("unknown")  // Returns R.drawable.circle
     * val nullIcon = IconMapper.getIconResource(null)  // Returns R.drawable.circle
     * ```
     */
    fun getIconResource(identifier: String?): Int {
        return identifier?.let { iconMap[it] } ?: R.drawable.circle
    }
}
