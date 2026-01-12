package ai.pipecat.gemini_multimodal_websocket_demo.integrations

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log

/**
 * Monitors MediaStore for new screenshots and notifies callback.
 * 
 * Uses ContentObserver to detect new images in MediaStore, filters for screenshots
 * by checking RELATIVE_PATH and DISPLAY_NAME, and deduplicates by _ID.
 * 
 * Compatible with scoped storage (Android 10+) and all Android versions.
 */
class ScreenshotMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "ScreenshotMonitor"
    }
    
    private var contentObserver: ContentObserver? = null
    private var lastProcessedId: Long = -1
    private var callback: ((Uri) -> Unit)? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    
    /**
     * Start monitoring MediaStore for new screenshots.
     * 
     * @param onNewScreenshot Callback invoked when a new screenshot is detected.
     *                        Receives the Uri of the screenshot.
     */
    fun startMonitoring(onNewScreenshot: (Uri) -> Unit) {
        Log.i(TAG, "========== STARTING SCREENSHOT MONITORING ==========")
        Log.d(TAG, "Callback provided: ${onNewScreenshot != null}")
        
        callback = onNewScreenshot
        
        // Create background thread for ContentObserver
        handlerThread = HandlerThread("ScreenshotMonitorThread").apply {
            start()
        }
        backgroundHandler = Handler(handlerThread!!.looper)
        
        Log.d(TAG, "Background thread created and started")
        
        // Create ContentObserver on background thread
        contentObserver = object : ContentObserver(backgroundHandler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                Log.i(TAG, "📸 MediaStore onChange detected! selfChange=$selfChange, uri=$uri")
                checkForNewScreenshot(uri)
            }
        }
        
        // Register observer on MediaStore
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver!!
        )
        
        Log.i(TAG, "✅ Screenshot monitoring ACTIVE - waiting for screenshots...")
        Log.i(TAG, "Monitoring URI: ${MediaStore.Images.Media.EXTERNAL_CONTENT_URI}")
    }
    
    /**
     * Stop monitoring and unregister ContentObserver.
     */
    fun stopMonitoring() {
        Log.d(TAG, "Stopping screenshot monitoring")
        
        contentObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            contentObserver = null
        }
        
        // Stop background thread
        handlerThread?.quitSafely()
        handlerThread = null
        backgroundHandler = null
        
        callback = null
        lastProcessedId = -1
        
        Log.i(TAG, "Screenshot monitoring stopped")
    }
    
    /**
     * Query MediaStore for the most recent image and check if it's a screenshot.
     * 
     * @param targetUri Optional URI of the specific item that changed.
     */
    private fun checkForNewScreenshot(targetUri: Uri? = null) {
        val currentCallback = callback ?: return
        
        Log.d(TAG, "🔍 Checking for new screenshot... (targetUri: $targetUri)")
        
        // Safety check for permissions
        val hasPermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermission) {
            Log.e(TAG, "❌ Cannot check for screenshot: Permission DENIED")
            return
        }
        
        try {
            // Define projection
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATE_ADDED
            )
            
            // If targetUri is provided and looks like a single item (ends with ID), query just that.
            // Otherwise, query for the latest image.
            val queryUri = if (targetUri != null && targetUri.lastPathSegment?.run { toLongOrNull() } != null) {
                targetUri
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            // Use applicationContext for resolver to be safer in background
            val resolver = context.applicationContext.contentResolver
            
            val cursor = resolver.query(
                queryUri,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            
            if (cursor == null) {
                Log.w(TAG, "⚠️ Cursor is null for URI: $queryUri")
                return
            }
            
            cursor.use {
                if (!it.moveToFirst()) {
                    Log.d(TAG, "No images found in query result")
                    return
                }
                
                val idIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val displayNameIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val relativePathIndex = it.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                
                val id = it.getLong(idIndex)
                val displayName = it.getString(displayNameIndex) ?: ""
                val relativePath = if (relativePathIndex >= 0) {
                    it.getString(relativePathIndex) ?: ""
                } else {
                    ""
                }
                
                Log.d(TAG, "Checking image ID: $id, Name: $displayName, Path: $relativePath")
                
                // Deduplicate
                if (id == lastProcessedId) {
                    Log.d(TAG, "⏭️ Already processed image ID: $id")
                    return
                }
                
                if (!isScreenshot(relativePath, displayName)) {
                    Log.v(TAG, "❌ Not a screenshot: $displayName")
                    return
                }
                
                // Found a screenshot!
                Log.i(TAG, "✅ NEW SCREENSHOT DETECTED: $displayName (ID: $id)")
                lastProcessedId = id
                
                // Build Uri for this image
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                
                // Small delay to ensure the file is ready to be read
                Thread.sleep(500)
                
                currentCallback(uri)
                Log.i(TAG, "✅ Callback triggered for: $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL ERROR in checkForNewScreenshot: ${e::class.java.simpleName}: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Check if an image is a screenshot based on its path and filename.
     * 
     * @param relativePath The RELATIVE_PATH from MediaStore (e.g., "Pictures/Screenshots/")
     * @param displayName The DISPLAY_NAME from MediaStore (e.g., "Screenshot_20260112.png")
     * @return true if this appears to be a screenshot
     */
    private fun isScreenshot(relativePath: String?, displayName: String?): Boolean {
        // Keywords to look for in path or name
        val keywords = listOf("screenshot", "sreenshot", "zrzut", "screen")
        
        // Check RELATIVE_PATH
        val pathMatch = relativePath?.let { path ->
            keywords.any { keyword -> path.contains(keyword, ignoreCase = true) }
        } ?: false
        
        // Check DISPLAY_NAME
        val nameMatch = displayName?.let { name ->
            keywords.any { keyword -> name.contains(keyword, ignoreCase = true) }
        } ?: false
        
        val isScreenshot = pathMatch || nameMatch
        
        if (isScreenshot) {
            Log.d(TAG, "Screenshot filter matched: path=$relativePath, name=$displayName")
        } else {
            Log.v(TAG, "Filter rejected image: path=$relativePath, name=$displayName")
        }
        
        return isScreenshot
    }
    
    /**
     * Check if monitoring is currently active.
     */
    fun isMonitoring(): Boolean {
        return contentObserver != null
    }
}
