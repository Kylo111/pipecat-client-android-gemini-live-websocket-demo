package ai.pipecat.gemini_multimodal_websocket_demo.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import ai.pipecat.gemini_multimodal_websocket_demo.models.ApiKeysConfig
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

object ApiKeysImporter {
    private const val TAG = "ApiKeysImporter"
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    fun parseJson(jsonString: String): Result<ApiKeysConfig> {
        return try {
            val config = json.decodeFromString<ApiKeysConfig>(jsonString)
            Log.d(TAG, "Successfully parsed API keys from JSON")
            Result.success(config)
        } catch (e: SerializationException) {
            Log.e(TAG, "Failed to parse JSON: ${e.message}", e)
            Result.failure(Exception("Nieprawidlowy format JSON: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error parsing JSON: ${e.message}", e)
            Result.failure(Exception("Blad podczas parsowania JSON: ${e.message}"))
        }
    }
    
    fun importFromUri(context: Context, uri: Uri): Result<ApiKeysConfig> {
        return try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: return Result.failure(Exception("Nie mozna odczytac pliku"))
            
            Log.d(TAG, "Successfully read file from URI: $uri")
            parseJson(jsonString)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read file from URI: ${e.message}", e)
            Result.failure(Exception("Blad odczytu pliku: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error reading from URI: ${e.message}", e)
            Result.failure(Exception("Nieoczekiwany blad: ${e.message}"))
        }
    }
}
