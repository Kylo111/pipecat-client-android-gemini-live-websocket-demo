package ai.pipecat.gemini_multimodal_websocket_demo.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String, // UUID
    
    val title: String?,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "last_session_at")
    val lastSessionAt: Long,
    
    @ColumnInfo(name = "session_count")
    val sessionCount: Int = 0,
    
    @ColumnInfo(name = "total_duration_seconds")
    val totalDurationSeconds: Int = 0,
    
    @ColumnInfo(name = "document_count")
    val documentCount: Int = 0,
    
    @ColumnInfo(name = "meta_summary")
    val metaSummary: String? = null,
    
    val source: String = "gemini_live", // 'gemini_live' | 'librechat'
    
    val metadata: String? = null, // JSON
    
    // OLD: These will be removed in task 13.2
    @ColumnInfo(name = "custom_summary_prompt")
    val customSummaryPrompt: String? = null,
    
    @ColumnInfo(name = "copy_summary_to_clipboard")
    val copySummaryToClipboard: Boolean = false,
    
    // NEW: Local conversation card (JSON serialized LocalConversationCard)
    @ColumnInfo(name = "local_card_json")
    val localCardJson: String? = null,
    
    // NEW: Last updated timestamp for memory
    @ColumnInfo(name = "last_updated_at")
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    
    // NEW: Memory update in progress flag
    @ColumnInfo(name = "memory_update_pending")
    val memoryUpdatePending: Boolean = false,
    
    // Template tracking fields for marketplace integration
    @ColumnInfo(name = "origin_template_id")
    val originTemplateId: String? = null,      // Links back to marketplace template
    
    @ColumnInfo(name = "origin_template_version")
    val originTemplateVersion: Int? = null     // Version at time of import
)
