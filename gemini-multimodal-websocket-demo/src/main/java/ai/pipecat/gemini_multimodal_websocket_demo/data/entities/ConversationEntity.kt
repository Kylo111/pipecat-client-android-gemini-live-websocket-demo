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
    
    val metadata: String? = null // JSON
)
