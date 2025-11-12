package ai.pipecat.gemini_multimodal_websocket_demo

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Manages authentication with LibreChat API including secure token storage,
 * login/logout functionality, and automatic token refresh.
 */
class AuthManager(private val context: Context) {

    private val encryptedPrefs: SharedPreferences
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "AuthManager"
        private const val PREFS_FILENAME = "librechat_auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_SERVER_URL = "server_url"
    }

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        encryptedPrefs = EncryptedSharedPreferences.create(
            context,
            PREFS_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Credentials required for authentication
     */
    data class AuthCredentials(
        val serverUrl: String,
        val email: String,
        val password: String
    )

    /**
     * Authentication token with expiration information
     */
    data class AuthToken(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAt: Long
    )

    /**
     * Retrieves the stored authentication token if available
     * @return AuthToken if stored, null otherwise
     */
    fun getStoredToken(): AuthToken? {
        val accessToken = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refreshToken = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
        val expiresAt = encryptedPrefs.getLong(KEY_EXPIRES_AT, 0L)

        if (expiresAt == 0L) return null

        return AuthToken(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresAt
        )
    }

    /**
     * Checks if the stored token is valid and not expired
     * @return true if token exists and is not expired, false otherwise
     */
    fun isTokenValid(): Boolean {
        val token = getStoredToken() ?: return false
        val currentTime = System.currentTimeMillis()
        // Consider token invalid if it expires within 5 minutes
        val bufferTime = 5 * 60 * 1000L
        return token.expiresAt > (currentTime + bufferTime)
    }

    /**
     * Stores authentication token securely
     */
    private fun storeToken(token: AuthToken, serverUrl: String) {
        encryptedPrefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, token.accessToken)
            putString(KEY_REFRESH_TOKEN, token.refreshToken)
            putLong(KEY_EXPIRES_AT, token.expiresAt)
            putString(KEY_SERVER_URL, serverUrl)
            apply()
        }
    }

    /**
     * Gets the stored server URL
     */
    fun getServerUrl(): String? {
        return encryptedPrefs.getString(KEY_SERVER_URL, null)
    }

    /**
     * Authenticates user with LibreChat API
     * @param credentials User credentials including server URL, email, and password
     * @return Result containing AuthToken on success or exception on failure
     */
    suspend fun login(credentials: AuthCredentials): Result<AuthToken> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting login to ${credentials.serverUrl}")
            val loginRequest = ai.pipecat.gemini_multimodal_websocket_demo.models.network.LoginRequest(
                email = credentials.email,
                password = credentials.password
            )

            val requestBody = json.encodeToString(loginRequest)
                .toRequestBody("application/json".toMediaType())

            val url = "${credentials.serverUrl}/api/auth/login"
            Log.d(TAG, "Login URL: $url")
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            Log.d(TAG, "Executing login request...")
            val response = httpClient.newCall(request).execute()
            Log.d(TAG, "Login response code: ${response.code}")

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Login failed: ${response.code} - $errorBody")
                return@withContext Result.failure(
                    IOException("Authentication failed: ${response.code} - $errorBody")
                )
            }

            val responseBody = response.body?.string()
            if (responseBody == null) {
                Log.e(TAG, "Empty response body")
                return@withContext Result.failure(IOException("Empty response body"))
            }

            Log.d(TAG, "Response body: $responseBody")
            Log.d(TAG, "Parsing login response...")
            val loginResponse = json.decodeFromString<ai.pipecat.gemini_multimodal_websocket_demo.models.network.LoginResponse>(responseBody)

            // Decode JWT to get actual expiration time
            val expiresAt = try {
                val parts = loginResponse.token.split(".")
                if (parts.size >= 2) {
                    val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
                    Log.d(TAG, "JWT payload: $payload")
                    val expMatch = Regex("\"exp\":(\\d+)").find(payload)
                    if (expMatch != null) {
                        val expSeconds = expMatch.groupValues[1].toLong()
                        expSeconds * 1000 // Convert to milliseconds
                    } else {
                        Log.w(TAG, "No exp field in JWT, using default 15 minutes")
                        System.currentTimeMillis() + (15 * 60 * 1000)
                    }
                } else {
                    Log.w(TAG, "Invalid JWT format, using default 15 minutes")
                    System.currentTimeMillis() + (15 * 60 * 1000)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode JWT expiration, using default: ${e.message}")
                System.currentTimeMillis() + (15 * 60 * 1000)
            }

            val authToken = AuthToken(
                accessToken = loginResponse.token,
                refreshToken = null, // LibreChat doesn't provide refresh token in this response
                expiresAt = expiresAt
            )

            storeToken(authToken, credentials.serverUrl)
            Log.d(TAG, "Login successful, token stored, expires at: $expiresAt")

            Result.success(authToken)
        } catch (e: Exception) {
            Log.e(TAG, "Login exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Logs out the user by clearing all stored credentials
     */
    suspend fun logout() = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().clear().apply()
    }

    /**
     * Refreshes the authentication token using the stored refresh token
     * @return Result containing new AuthToken on success or exception on failure
     */
    suspend fun refreshToken(): Result<AuthToken> = withContext(Dispatchers.IO) {
        try {
            val currentToken = getStoredToken()
                ?: return@withContext Result.failure(IOException("No token to refresh"))

            val refreshToken = currentToken.refreshToken
                ?: return@withContext Result.failure(IOException("No refresh token available"))

            val serverUrl = getServerUrl()
                ?: return@withContext Result.failure(IOException("No server URL stored"))

            @Serializable
            data class RefreshRequest(val refreshToken: String)

            val refreshRequest = RefreshRequest(refreshToken = refreshToken)
            val requestBody = json.encodeToString(refreshRequest)
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$serverUrl/api/auth/refresh")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                // If refresh fails, clear stored credentials
                logout()
                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext Result.failure(
                    IOException("Token refresh failed: ${response.code} - $errorBody")
                )
            }

            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(IOException("Empty response body"))

            val loginResponse = json.decodeFromString<ai.pipecat.gemini_multimodal_websocket_demo.models.network.LoginResponse>(responseBody)

            // Decode JWT to get actual expiration time
            val expiresAt = try {
                val parts = loginResponse.token.split(".")
                if (parts.size >= 2) {
                    val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
                    val expMatch = Regex("\"exp\":(\\d+)").find(payload)
                    if (expMatch != null) {
                        expMatch.groupValues[1].toLong() * 1000
                    } else {
                        System.currentTimeMillis() + (15 * 60 * 1000)
                    }
                } else {
                    System.currentTimeMillis() + (15 * 60 * 1000)
                }
            } catch (e: Exception) {
                System.currentTimeMillis() + (15 * 60 * 1000)
            }

            val authToken = AuthToken(
                accessToken = loginResponse.token,
                refreshToken = null,
                expiresAt = expiresAt
            )

            storeToken(authToken, serverUrl)

            Result.success(authToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Executes an HTTP request with automatic token refresh on 401 errors
     * @param block The HTTP request to execute
     * @return Result containing the response or exception
     */
    suspend fun <T> executeWithTokenRefresh(block: suspend (String) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            // Check if token needs refresh before making request
            if (!isTokenValid()) {
                val refreshResult = refreshToken()
                if (refreshResult.isFailure) {
                    return@withContext Result.failure(
                        refreshResult.exceptionOrNull() ?: IOException("Token refresh failed")
                    )
                }
            }

            val token = getStoredToken()
                ?: return@withContext Result.failure(IOException("No valid token available"))

            try {
                // Execute the request with current token
                val result = block(token.accessToken)
                Result.success(result)
            } catch (e: IOException) {
                // Check if it's a 401 error (token expired)
                if (e.message?.contains("401") == true) {
                    // Try to refresh token and retry once
                    val refreshResult = refreshToken()
                    if (refreshResult.isFailure) {
                        return@withContext Result.failure(
                            refreshResult.exceptionOrNull() ?: IOException("Token refresh failed")
                        )
                    }

                    val newToken = getStoredToken()
                        ?: return@withContext Result.failure(IOException("No valid token after refresh"))

                    // Retry the request with new token
                    val result = block(newToken.accessToken)
                    Result.success(result)
                } else {
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
