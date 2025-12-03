package ai.pipecat.gemini_multimodal_websocket_demo.utils

import ai.pipecat.gemini_multimodal_websocket_demo.R
import ai.pipecat.gemini_multimodal_websocket_demo.SessionManager
import ai.pipecat.gemini_multimodal_websocket_demo.network.WebSocketClient
import ai.pipecat.gemini_multimodal_websocket_demo.state.VoiceEvent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Processes images for transmission over WebSocket connection.
 * Handles validation, resizing, compression, encoding, and transmission.
 * Includes performance optimizations and detailed logging.
 * 
 * Requirements: 6.1 - Extract image processing logic from VoiceClientManager
 */
class ImageProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "ImageProcessor"
        private const val MAX_RAW_SIZE_BYTES = 5 * 1024 * 1024 // 5MB
        private const val MAX_DIMENSION_PX = 2300
        private const val COMPRESSION_QUALITY = 85
        private const val MAX_PROCESSED_SIZE_BYTES = 7 * 1024 * 1024 // 7MB after Base64
        private const val IMAGE_PROCESSING_TIMEOUT_MS = 30000L // 30 seconds
        
        // Performance thresholds
        private const val TARGET_PROCESSING_TIME_MS = 2000L
        private const val WARNING_MEMORY_USAGE_KB = 10 * 1024 // 10MB
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }
    
    /**
     * Data class representing a processed image ready for transmission
     */
    data class ProcessedImage(
        val data: ByteArray,
        val mimeType: String,
        val originalSize: Int,
        val processedSize: Int,
        val dimensions: Pair<Int, Int>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as ProcessedImage
            
            if (!data.contentEquals(other.data)) return false
            if (mimeType != other.mimeType) return false
            if (originalSize != other.originalSize) return false
            if (processedSize != other.processedSize) return false
            if (dimensions != other.dimensions) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + mimeType.hashCode()
            result = 31 * result + originalSize
            result = 31 * result + processedSize
            result = 31 * result + dimensions.hashCode()
            return result
        }
    }
    
    /**
     * Processes an image from the given URI.
     * Validates size, resizes if needed, and compresses to meet transmission requirements.
     * Includes performance profiling and optimization.
     * 
     * @param uri The URI of the image to process
     * @return Result containing ProcessedImage on success or error on failure
     */
    suspend fun processImage(uri: Uri): Result<ProcessedImage> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        PerformanceLogger.logMemory("ImageProcessor.start")
        
        try {
            Log.d(TAG, "Starting image processing for URI: $uri")
            
            // Load bitmap with efficient memory usage
            val (bitmap, loadMetrics) = PerformanceLogger.measureSuspend("ImageProcessor.loadBitmap") {
                loadBitmap(uri)
            }
            
            if (bitmap == null) {
                return@withContext Result.failure(
                    IllegalArgumentException("Failed to load image from URI")
                )
            }
            
            val originalWidth = bitmap.width
            val originalHeight = bitmap.height
            Log.d(TAG, "Original dimensions: ${originalWidth}x${originalHeight}")
            
            // Check if memory usage is concerning
            if (loadMetrics.memoryUsedKb > WARNING_MEMORY_USAGE_KB) {
                Log.w(TAG, "High memory usage during bitmap load: ${loadMetrics.memoryUsedKb}KB")
            }
            
            // Compress and resize if needed
            val (processedData, compressMetrics) = PerformanceLogger.measureSuspend("ImageProcessor.compressAndResize") {
                compressAndResize(bitmap, COMPRESSION_QUALITY, MAX_DIMENSION_PX)
            }
            
            // Validate final size
            if (processedData.size > MAX_PROCESSED_SIZE_BYTES) {
                bitmap.recycle()
                return@withContext Result.failure(
                    IllegalStateException("Processed image size (${processedData.size} bytes) exceeds maximum allowed size")
                )
            }
            
            // Calculate final dimensions
            val longestDimension = maxOf(originalWidth, originalHeight)
            val scale = if (longestDimension > MAX_DIMENSION_PX) {
                MAX_DIMENSION_PX.toFloat() / longestDimension
            } else {
                1.0f
            }
            val finalWidth = (originalWidth * scale).toInt()
            val finalHeight = (originalHeight * scale).toInt()
            
            bitmap.recycle()
            
            val processedImage = ProcessedImage(
                data = processedData,
                mimeType = "image/jpeg",
                originalSize = 0, // We don't have the original file size
                processedSize = processedData.size,
                dimensions = Pair(finalWidth, finalHeight)
            )
            
            val totalTime = System.currentTimeMillis() - startTime
            val compressionRatio = if (loadMetrics.memoryUsedKb > 0) {
                ((loadMetrics.memoryUsedKb - (processedData.size / 1024)).toFloat() / loadMetrics.memoryUsedKb * 100).toInt()
            } else 0
            
            Log.i(TAG, "Image processed successfully: ${processedData.size} bytes, ${finalWidth}x${finalHeight}, " +
                    "total time: ${totalTime}ms, compression: ${compressionRatio}%")
            
            // Warn if processing took too long
            if (totalTime > TARGET_PROCESSING_TIME_MS) {
                Log.w(TAG, "Image processing exceeded target time: ${totalTime}ms > ${TARGET_PROCESSING_TIME_MS}ms")
            }
            
            PerformanceLogger.logMemory("ImageProcessor.complete")
            Result.success(processedImage)
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError while processing image", e)
            PerformanceLogger.logMemory("ImageProcessor.OOM")
            Result.failure(OutOfMemoryError("Image too large to process. Please select a smaller image."))
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
            Result.failure(e)
        }
    }
    
    /**
     * Loads a bitmap from URI using efficient memory management
     */
    private suspend fun loadBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                // First decode with inJustDecodeBounds to get dimensions
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(stream, null, options)
                
                // Calculate inSampleSize
                val sampleSize = calculateInSampleSize(
                    options,
                    MAX_DIMENSION_PX,
                    MAX_DIMENSION_PX
                )
                
                Log.d(TAG, "Using inSampleSize: $sampleSize")
                
                // Reopen stream and decode with inSampleSize
                context.contentResolver.openInputStream(uri)?.use { newStream ->
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inJustDecodeBounds = false
                    }
                    BitmapFactory.decodeStream(newStream, null, decodeOptions)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap", e)
            null
        }
    }
    
    /**
     * Compresses and resizes bitmap if needed.
     * Optimized for performance with adaptive quality and efficient scaling.
     */
    private suspend fun compressAndResize(
        bitmap: Bitmap,
        quality: Int = COMPRESSION_QUALITY,
        maxDimension: Int = MAX_DIMENSION_PX
    ): ByteArray = withContext(Dispatchers.IO) {
        val longestDimension = maxOf(bitmap.width, bitmap.height)
        
        val finalBitmap = if (longestDimension > maxDimension) {
            // Calculate scale to maintain aspect ratio
            val scale = maxDimension.toFloat() / longestDimension
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            
            Log.d(TAG, "Resizing from ${bitmap.width}x${bitmap.height} to ${newWidth}x${newHeight}")
            
            // Use filtering for better quality at reasonable performance cost
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
        
        // Compress to JPEG with adaptive quality
        val outputStream = ByteArrayOutputStream()
        var currentQuality = quality
        var compressedData: ByteArray
        
        // First attempt with target quality
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, outputStream)
        compressedData = outputStream.toByteArray()
        
        // If still too large, reduce quality iteratively
        var attempts = 0
        while (compressedData.size > MAX_PROCESSED_SIZE_BYTES && currentQuality > 50 && attempts < 3) {
            attempts++
            currentQuality -= 10
            outputStream.reset()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, outputStream)
            compressedData = outputStream.toByteArray()
            Log.d(TAG, "Reduced quality to $currentQuality%, size: ${compressedData.size} bytes")
        }
        
        // Clean up if we created a new bitmap
        if (finalBitmap != bitmap) {
            finalBitmap.recycle()
        }
        
        compressedData
    }
    
    /**
     * Calculates the optimal inSampleSize for loading a bitmap
     * to reduce memory usage while maintaining quality.
     * Optimized to be more aggressive for very large images.
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            // Calculate the largest inSampleSize value that is a power of 2
            // and keeps both height and width larger than the requested height and width
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
            
            // For very large images (>4K), be more aggressive
            if (height > 4000 || width > 4000) {
                inSampleSize = maxOf(inSampleSize, 2)
            }
        }
        
        Log.d(TAG, "Calculated inSampleSize: $inSampleSize for ${width}x${height} -> ${reqWidth}x${reqHeight}")
        return inSampleSize
    }
    
    /**
     * Gets performance statistics for the last image processing operation
     */
    fun getPerformanceStats(): String {
        return "ImageProcessor performance stats available in logs"
    }
    
    /**
     * Result of image sending operation
     */
    sealed class SendImageResult {
        data class Success(val imageDescription: String) : SendImageResult()
        data class Queued(val uri: Uri) : SendImageResult()
        data class Failure(val errorMessage: String) : SendImageResult()
    }
    
    /**
     * Sends an image through the WebSocket connection.
     * Handles the entire flow: processing, encoding, transmission, and error handling.
     * 
     * This method:
     * 1. Validates connection state
     * 2. Processes the image (resize, compress)
     * 3. Encodes to Base64
     * 4. Builds and sends WebSocket message
     * 5. Records in session manager
     * 6. Handles errors and queuing for retry
     * 
     * Requirements: 6.1 - Extract image sending logic from VoiceClientManager
     * 
     * @param uri The URI of the image to send
     * @param isConnected Whether the WebSocket is currently connected
     * @param webSocketClient The WebSocket client to send through
     * @param sessionManager Optional session manager to record the event
     * @param onEvent Callback to emit VoiceEvents for state updates
     * @return SendImageResult indicating success, queued, or failure
     */
    suspend fun sendImage(
        uri: Uri,
        isConnected: Boolean,
        webSocketClient: WebSocketClient,
        sessionManager: SessionManager?,
        onEvent: (VoiceEvent) -> Unit
    ): SendImageResult = withContext(Dispatchers.IO) {
        // Check if not connected - queue the image for retry after reconnection
        if (!isConnected) {
            Log.w(TAG, "Cannot send image - not connected")
            onEvent(VoiceEvent.ImageProcessingFailed(context.getString(R.string.error_image_queued_for_retry)))
            return@withContext SendImageResult.Queued(uri)
        }

        Log.i(TAG, "Starting image send with processing - URI: $uri")
        val startTime = System.currentTimeMillis()
        
        try {
            // Emit ImageProcessingStarted event to update state
            onEvent(VoiceEvent.ImageProcessingStarted)
            
            // Process image with timeout
            val processingResult = withTimeout(IMAGE_PROCESSING_TIMEOUT_MS) {
                processImage(uri)
            }
            
            processingResult.onSuccess { processedImage ->
                Log.i(TAG, "Image processed successfully:")
                Log.i(TAG, "  Original size: ${processedImage.originalSize} bytes")
                Log.i(TAG, "  Processed size: ${processedImage.processedSize} bytes (${processedImage.processedSize / 1024} KB)")
                Log.i(TAG, "  Dimensions: ${processedImage.dimensions.first}x${processedImage.dimensions.second}")
                Log.i(TAG, "  MIME type: ${processedImage.mimeType}")
                
                // Encode to Base64
                val base64Image = Base64.encodeToString(processedImage.data, Base64.NO_WRAP)
                val base64Size = base64Image.length
                
                Log.i(TAG, "Image encoded to Base64 - Size: $base64Size chars (${base64Size / 1024} KB)")
                
                // Check if still connected before sending
                if (!isConnected) {
                    Log.w(TAG, "Connection lost during image processing, queuing for retry")
                    onEvent(VoiceEvent.ImageProcessingFailed(context.getString(R.string.error_image_queued_for_retry)))
                    onEvent(VoiceEvent.ImageProcessingCompleted)
                    return@withContext SendImageResult.Queued(uri)
                }
                
                // Build and send message
                val message = buildJsonObject {
                    putJsonObject("realtime_input") {
                        putJsonArray("media_chunks") {
                            add(buildJsonObject {
                                put("mime_type", processedImage.mimeType)
                                put("data", base64Image)
                            })
                        }
                    }
                }
                
                val messageJson = json.encodeToString(message)
                val messageSent = webSocketClient.send(messageJson)
                
                val elapsedTime = System.currentTimeMillis() - startTime
                
                if (messageSent) {
                    Log.i(TAG, "Image sent successfully in ${elapsedTime}ms")
                    
                    // Record image event in session
                    val imageDescription = "Image sent: ${uri.lastPathSegment ?: "unknown"} " +
                            "(${processedImage.processedSize} bytes, ${processedImage.dimensions.first}x${processedImage.dimensions.second})"
                    sessionManager?.recordImageSent(imageDescription)
                    
                    // Emit completion event
                    onEvent(VoiceEvent.ImageProcessingCompleted)
                    
                    return@withContext SendImageResult.Success(imageDescription)
                } else {
                    Log.e(TAG, "Failed to send image - WebSocket send returned false")
                    val errorMessage = context.getString(R.string.error_image_send_failed, context.getString(R.string.error_image_send_connection_problem))
                    onEvent(VoiceEvent.ImageProcessingFailed(errorMessage))
                    onEvent(VoiceEvent.ImageProcessingCompleted)
                    return@withContext SendImageResult.Failure(errorMessage)
                }
                
            }.onFailure { error ->
                Log.e(TAG, "Image processing failed: ${error.message}", error)
                
                val errorMessage = when (error) {
                    is OutOfMemoryError -> context.getString(R.string.error_image_too_large_memory)
                    is kotlinx.coroutines.TimeoutCancellationException -> context.getString(R.string.error_image_processing_timeout)
                    else -> context.getString(R.string.error_image_processing_failed_with_message, error.message ?: "")
                }
                
                // Emit failure events
                onEvent(VoiceEvent.ImageProcessingFailed(errorMessage))
                onEvent(VoiceEvent.ImageProcessingCompleted)
                
                return@withContext SendImageResult.Failure(errorMessage)
            }
            
            // Emit completion event
            onEvent(VoiceEvent.ImageProcessingCompleted)
            
            // Should not reach here, but return failure as fallback
            SendImageResult.Failure("Unknown error")
            
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "Image processing timeout after ${IMAGE_PROCESSING_TIMEOUT_MS}ms", e)
            val errorMessage = context.getString(R.string.error_image_processing_timeout)
            onEvent(VoiceEvent.ImageProcessingFailed(errorMessage))
            onEvent(VoiceEvent.ImageProcessingCompleted)
            SendImageResult.Failure(errorMessage)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory while processing image", e)
            val errorMessage = context.getString(R.string.error_image_too_large_memory)
            onEvent(VoiceEvent.ImageProcessingFailed(errorMessage))
            onEvent(VoiceEvent.ImageProcessingCompleted)
            SendImageResult.Failure(errorMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending image: ${e.message}", e)
            val errorMessage = context.getString(R.string.error_image_send_failed, e.message ?: "")
            onEvent(VoiceEvent.ImageProcessingFailed(errorMessage))
            onEvent(VoiceEvent.ImageProcessingCompleted)
            SendImageResult.Failure(errorMessage)
        }
    }
}
