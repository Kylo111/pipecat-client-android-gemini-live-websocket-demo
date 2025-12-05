package ai.pipecat.gemini_multimodal_websocket_demo.usecases

import ai.pipecat.gemini_multimodal_websocket_demo.OfflineConversationManager
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.ConfigurationRepository
import ai.pipecat.gemini_multimodal_websocket_demo.models.OfflineConversation
import java.util.UUID

/**
 * Use case for importing a marketplace template into a user's personal conversation.
 * 
 * This use case handles the process of converting a read-only marketplace template
 * into a user-owned OfflineConversation that can be customized and used.
 * 
 * Requirements validated:
 * - 2.1: Creates new OfflineConversation with unique ID
 * - 2.2: Copies title, systemPrompt, voiceId, and temperature
 * - 2.3: Does NOT copy description field
 * - 2.4: Stores originTemplateId and originTemplateVersion
 * - 2.5: Sets appropriate timestamps
 */
class ImportAssistantUseCase(
    private val offlineConversationManager: OfflineConversationManager,
    private val configRepository: ConfigurationRepository
) {
    
    /**
     * Executes the import operation for a given template ID.
     * 
     * @param templateId The unique identifier of the template to import
     * @return Result containing the newly created OfflineConversation on success,
     *         or an exception on failure (e.g., template not found)
     */
    suspend fun execute(templateId: String): Result<OfflineConversation> {
        // Retrieve the template from the configuration repository
        val template = configRepository.getTemplateById(templateId)
            ?: return Result.failure(TemplateNotFoundException(templateId))
        
        // Create a new conversation with a unique ID
        val currentTime = System.currentTimeMillis()
        val newConversation = OfflineConversation(
            id = UUID.randomUUID().toString(),
            title = template.title,
            systemPrompt = template.systemPrompt,
            voiceName = template.voiceId ?: "Puck",
            temperature = template.temperature,
            createdAt = currentTime,
            updatedAt = currentTime,
            // Store template tracking information for future updates
            originTemplateId = template.id,
            originTemplateVersion = template.version
            // Note: description is NOT copied - it's marketplace-only
        )
        
        // Add the conversation to the manager
        offlineConversationManager.add(newConversation)
        
        return Result.success(newConversation)
    }
}

/**
 * Exception thrown when a template with the specified ID is not found.
 */
class TemplateNotFoundException(templateId: String) : 
    Exception("Template not found: $templateId")
