package ai.pipecat.gemini_multimodal_websocket_demo.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Processes images for transmission over WebSocket connection.
 * Handles validation, resizing, and compression to ensure images meet size requirements.
 * Includes performance optimizations and detailed logging.
 */
class ImageProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "ImageProcessor"
        private const val MAX_RAW_SIZE_BYTES = 5 * 1024 * 1024 // 5MB
        private const val MAX_DIMENSION_PX = 2300
        private const val COMPRESSION_QUALITY = 85
        private const val MAX_PROCESSED_SIZE_BYTES = 7 * 1024 * 1024 // 7MB after Base64
        
        // Performance thresholds
        private const val TARGET_PROCESSING_TIME_MS = 2000L
        private const val WARNING_MEMORY_USAGE_KB = 10 * 1024 // 10MB
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
}
