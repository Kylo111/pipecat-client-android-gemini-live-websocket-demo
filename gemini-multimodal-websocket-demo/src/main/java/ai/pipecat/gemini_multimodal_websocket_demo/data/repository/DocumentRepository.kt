package ai.pipecat.gemini_multimodal_websocket_demo.data.repository

import ai.pipecat.gemini_multimodal_websocket_demo.data.dao.DocumentDao
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.DocumentEntity
import kotlinx.coroutines.flow.Flow

class DocumentRepository(
    private val documentDao: DocumentDao
) {
    
    // Add document
    suspend fun addDocument(
        fileName: String,
        fileContent: ByteArray,
        mimeType: String
    ): Long {
        val document = DocumentEntity(
            fileName = fileName,
            fileContent = fileContent,
            mimeType = mimeType,
            fileSize = fileContent.size.toLong(),
            createdAt = System.currentTimeMillis()
        )
        
        return documentDao.insert(document)
    }
    
    // Get all documents
    fun getAllDocumentsFlow(): Flow<List<DocumentEntity>> {
        return documentDao.getAllFlow()
    }
    
    suspend fun getAllDocuments(): List<DocumentEntity> {
        return documentDao.getAll()
    }
    
    // Get document by ID
    suspend fun getDocument(id: Long): DocumentEntity? {
        return documentDao.getById(id)
    }
    
    // Delete document
    suspend fun deleteDocument(id: Long) {
        documentDao.getById(id)?.let { document ->
            documentDao.delete(document)
        }
    }
    
    // Get pending uploads
    suspend fun getPendingUploads(): List<DocumentEntity> {
        return documentDao.getPendingUploads()
    }
    
    // Get failed uploads
    suspend fun getFailedUploads(): List<DocumentEntity> {
        return documentDao.getFailedUploads()
    }
    
    // Mark as uploaded
    suspend fun markAsUploaded(id: Long, vertexId: String) {
        val now = System.currentTimeMillis()
        documentDao.markAsUploaded(id, vertexId, now)
    }
    
    // Update upload status
    suspend fun updateUploadStatus(id: Long, status: String, errorMessage: String? = null) {
        documentDao.updateUploadStatus(id, status, errorMessage)
    }
    
    // Get total storage used
    suspend fun getTotalStorageUsed(): Long {
        return documentDao.getTotalSize() ?: 0L
    }
    
    // Get document count
    suspend fun getDocumentCount(): Int {
        return documentDao.getCount()
    }
    
    // Validate file size (max 50MB)
    fun validateFileSize(fileSize: Long): Boolean {
        val maxSize = 50 * 1024 * 1024L // 50MB
        return fileSize <= maxSize
    }
    
    // Validate MIME type
    fun validateMimeType(mimeType: String): Boolean {
        val supportedTypes = listOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain",
            "text/markdown",
            "audio/mpeg",
            "audio/mp3",
            "image/png",
            "image/jpeg"
        )
        return supportedTypes.any { mimeType.startsWith(it) }
    }
}
