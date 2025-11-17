package ai.pipecat.gemini_multimodal_websocket_demo.data.dao

import androidx.room.*
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity): Long
    
    @Update
    suspend fun update(document: DocumentEntity)
    
    @Delete
    suspend fun delete(document: DocumentEntity)
    
    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: Long): DocumentEntity?
    
    @Query("SELECT * FROM documents ORDER BY created_at DESC")
    fun getAllFlow(): Flow<List<DocumentEntity>>
    
    @Query("SELECT * FROM documents ORDER BY created_at DESC")
    suspend fun getAll(): List<DocumentEntity>
    
    @Query("SELECT * FROM documents WHERE uploaded_to_vertex = 0")
    suspend fun getPendingUploads(): List<DocumentEntity>
    
    @Query("SELECT * FROM documents WHERE upload_status = 'failed'")
    suspend fun getFailedUploads(): List<DocumentEntity>
    
    @Query("UPDATE documents SET uploaded_to_vertex = 1, vertex_rag_file_id = :vertexId, upload_status = 'uploaded', last_synced_at = :timestamp WHERE id = :id")
    suspend fun markAsUploaded(id: Long, vertexId: String, timestamp: Long)
    
    @Query("UPDATE documents SET upload_status = :status, error_message = :errorMessage WHERE id = :id")
    suspend fun updateUploadStatus(id: Long, status: String, errorMessage: String? = null)
    
    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getCount(): Int
    
    @Query("SELECT SUM(file_size) FROM documents")
    suspend fun getTotalSize(): Long?
}
