package ai.pipecat.gemini_multimodal_websocket_demo.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import ai.pipecat.gemini_multimodal_websocket_demo.models.ApiKeysConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

object ApiKeysExporter {
    private const val TAG = "ApiKeysExporter"
    
    private val json = Json {
        prettyPrint = true
        encodeDefaults = false
    }
    
    /**
     * Converts ApiKeysConfig to formatted JSON string
     */
    fun toJson(config: ApiKeysConfig): Result<String> {
        return try {
            val jsonString = json.encodeToString(config)
            Log.d(TAG, "Successfully converted API keys to JSON")
            Result.success(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert to JSON: ${e.message}", e)
            Result.failure(Exception("Błąd podczas tworzenia JSON: ${e.message}"))
        }
    }
    
    /**
     * Exports ApiKeysConfig to a file URI
     */
    fun exportToUri(context: Context, uri: Uri, config: ApiKeysConfig): Result<Unit> {
        return try {
            val jsonResult = toJson(config)
            if (jsonResult.isFailure) {
                return Result.failure(jsonResult.exceptionOrNull()!!)
            }
            
            val jsonString = jsonResult.getOrNull()!!
            
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.bufferedWriter().use { writer ->
                    writer.write(jsonString)
                }
            } ?: return Result.failure(Exception("Nie można zapisać pliku"))
            
            Log.d(TAG, "Successfully exported API keys to URI: $uri")
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write file to URI: ${e.message}", e)
            Result.failure(Exception("Błąd zapisu pliku: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error writing to URI: ${e.message}", e)
            Result.failure(Exception("Nieoczekiwany błąd: ${e.message}"))
        }
    }
}
