package ai.pipecat.gemini_multimodal_websocket_demo.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("conversation_id"),
        Index("started_at")
    ]
)
data class SessionEntity(
    @PrimaryKey
    val id: String, // UUID
    
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    
    @ColumnInfo(name = "ended_at")
    val endedAt: Long? = null,
    
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int? = null,
    
    val transcript: String,
    
    val summary: String? = null,
    
    @ColumnInfo(name = "message_count")
    val messageCount: Int = 0,
    
    @ColumnInfo(name = "synced_to_vertex")
    val syncedToVertex: Boolean = false,
    
    @ColumnInfo(name = "vertex_vector_id")
    val vertexVectorId: String? = null,
    
    val metadata: String? = null // JSON
)
