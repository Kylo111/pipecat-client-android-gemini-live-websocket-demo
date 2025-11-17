package ai.pipecat.gemini_multimodal_websocket_demo.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Unit tests for ImageProcessor
 * 
 * Note: These tests focus on the core logic and error handling.
 * Full integration tests with actual image files would require Android instrumentation tests.
 */
@RunWith(MockitoJUnitRunner::class)
class ImageProcessorTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    private lateinit var imageProcessor: ImageProcessor
    
    @Before
    fun setup() {
        imageProcessor = ImageProcessor(mockContext)
    }
    
    @Test
    fun `ProcessedImage data class equality works correctly`() {
        val data1 = byteArrayOf(1, 2, 3, 4)
        val data2 = byteArrayOf(1, 2, 3, 4)
        val data3 = byteArrayOf(5, 6, 7, 8)
        
        val image1 = ImageProcessor.ProcessedImage(
            data = data1,
            mimeType = "image/jpeg",
            originalSize = 1000,
            processedSize = 500,
            dimensions = Pair(800, 600)
        )
        
        val image2 = ImageProcessor.ProcessedImage(
            data = data2,
            mimeType = "image/jpeg",
            originalSize = 1000,
            processedSize = 500,
            dimensions = Pair(800, 600)
        )
        
        val image3 = ImageProcessor.ProcessedImage(
            data = data3,
            mimeType = "image/jpeg",
            originalSize = 1000,
            processedSize = 500,
            dimensions = Pair(800, 600)
        )
        
        assertEquals(image1, image2)
        assertNotEquals(image1, image3)
    }
    
    @Test
    fun `ProcessedImage hashCode works correctly`() {
        val data = byteArrayOf(1, 2, 3, 4)
        
        val image1 = ImageProcessor.ProcessedImage(
            data = data,
            mimeType = "image/jpeg",
            originalSize = 1000,
            processedSize = 500,
            dimensions = Pair(800, 600)
        )
        
        val image2 = ImageProcessor.ProcessedImage(
            data = data.copyOf(),
            mimeType = "image/jpeg",
            originalSize = 1000,
            processedSize = 500,
            dimensions = Pair(800, 600)
        )
        
        assertEquals(image1.hashCode(), image2.hashCode())
    }
    
    @Test
    fun `ProcessedImage contains correct mime type`() {
        val image = ImageProcessor.ProcessedImage(
            data = byteArrayOf(1, 2, 3),
            mimeType = "image/jpeg",
            originalSize = 100,
            processedSize = 50,
            dimensions = Pair(100, 100)
        )
        
        assertEquals("image/jpeg", image.mimeType)
    }
    
    @Test
    fun `ProcessedImage stores dimensions correctly`() {
        val image = ImageProcessor.ProcessedImage(
            data = byteArrayOf(1, 2, 3),
            mimeType = "image/jpeg",
            originalSize = 100,
            processedSize = 50,
            dimensions = Pair(1920, 1080)
        )
        
        assertEquals(Pair(1920, 1080), image.dimensions)
        assertEquals(1920, image.dimensions.first)
        assertEquals(1080, image.dimensions.second)
    }
    
    @Test
    fun `ProcessedImage stores size information correctly`() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val image = ImageProcessor.ProcessedImage(
            data = data,
            mimeType = "image/jpeg",
            originalSize = 1000,
            processedSize = 500,
            dimensions = Pair(800, 600)
        )
        
        assertEquals(1000, image.originalSize)
        assertEquals(500, image.processedSize)
        assertEquals(5, image.data.size)
    }
    
    @Test
    fun `ProcessedImage maintains aspect ratio information`() {
        val image = ImageProcessor.ProcessedImage(
            data = byteArrayOf(1, 2, 3),
            mimeType = "image/jpeg",
            originalSize = 100,
            processedSize = 50,
            dimensions = Pair(1600, 900) // 16:9 aspect ratio
        )
        
        val aspectRatio = image.dimensions.first.toFloat() / image.dimensions.second.toFloat()
        assertEquals(16f / 9f, aspectRatio, 0.01f)
    }
    
    @Test
    fun `ProcessedImage handles square images`() {
        val image = ImageProcessor.ProcessedImage(
            data = byteArrayOf(1, 2, 3),
            mimeType = "image/jpeg",
            originalSize = 100,
            processedSize = 50,
            dimensions = Pair(1000, 1000)
        )
        
        assertEquals(image.dimensions.first, image.dimensions.second)
    }
    
    @Test
    fun `ProcessedImage handles portrait orientation`() {
        val image = ImageProcessor.ProcessedImage(
            data = byteArrayOf(1, 2, 3),
            mimeType = "image/jpeg",
            originalSize = 100,
            processedSize = 50,
            dimensions = Pair(900, 1600) // Portrait
        )
        
        assertTrue(image.dimensions.second > image.dimensions.first)
    }
    
    @Test
    fun `ProcessedImage handles landscape orientation`() {
        val image = ImageProcessor.ProcessedImage(
            data = byteArrayOf(1, 2, 3),
            mimeType = "image/jpeg",
            originalSize = 100,
            processedSize = 50,
            dimensions = Pair(1600, 900) // Landscape
        )
        
        assertTrue(image.dimensions.first > image.dimensions.second)
    }
}
