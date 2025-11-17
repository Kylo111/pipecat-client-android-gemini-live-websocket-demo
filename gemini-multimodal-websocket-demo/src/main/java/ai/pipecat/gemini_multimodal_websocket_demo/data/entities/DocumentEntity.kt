package ai.pipecat.gemini_multimodal_websocket_demo.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "file_name")
    val fileName: String,
    
    @ColumnInfo(name = "file_content", typeAffinity = ColumnInfo.BLOB)
    val fileContent: ByteArray,
    
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    
    @ColumnInfo(name = "file_size")
    val fileSize: Long,
    
    @ColumnInfo(name = "uploaded_to_vertex")
    val uploadedToVertex: Boolean = false,
    
    @ColumnInfo(name = "vertex_rag_file_id")
    val vertexRagFileId: String? = null,
    
    @ColumnInfo(name = "upload_status")
    val uploadStatus: String = "pending", // 'pending' | 'uploading' | 'uploaded' | 'failed'
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long? = null,
    
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DocumentEntity

        if (id != other.id) return false
        if (fileName != other.fileName) return false
        if (!fileContent.contentEquals(other.fileContent)) return false
        if (mimeType != other.mimeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + fileContent.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}
