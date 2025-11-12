package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.AuthManager
import ai.pipecat.gemini_multimodal_websocket_demo.models.LibreChatError
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.textFieldColors
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Login screen for LibreChat authentication
 * Allows users to enter their LibreChat server URL, email, and password
 * 
 * @param authManager AuthManager instance for handling authentication
 * @param onLoginSuccess Callback invoked when login is successful
 */
@Composable
fun LoginScreen(
    authManager: AuthManager,
    onLoginSuccess: () -> Unit
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    
    var serverUrl by remember { mutableStateOf(authManager.getServerUrl() ?: "") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<LibreChatError?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.mainSurfaceBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .imePadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // Title
            Text(
                text = "Zaloguj się do LibreChat",
                fontSize = 24.sp,
                fontWeight = FontWeight.W700,
                color = Color.Black,
                style = TextStyles.base
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Login Card
            Box(
                Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    // Server URL
                    Text(
                        text = "Adres serwera LibreChat",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        style = TextStyles.base,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Colors.textFieldBorder, RoundedCornerShape(12.dp)),
                        value = serverUrl,
                        onValueChange = { 
                            serverUrl = it
                            error = null
                        },
                        placeholder = { Text("https://librechat.example.com") },
                        colors = textFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Email
                    Text(
                        text = "Email",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        style = TextStyles.base,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Colors.textFieldBorder, RoundedCornerShape(12.dp)),
                        value = email,
                        onValueChange = { 
                            email = it
                            error = null
                        },
                        placeholder = { Text("twoj@email.com") },
                        colors = textFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Password
                    Text(
                        text = "Hasło",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        style = TextStyles.base,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Colors.textFieldBorder, RoundedCornerShape(12.dp)),
                        value = password,
                        onValueChange = { 
                            password = it
                            error = null
                        },
                        placeholder = { Text("Twoje hasło") },
                        colors = textFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                if (serverUrl.isNotBlank() && email.isNotBlank() && password.isNotBlank()) {
                                    coroutineScope.launch {
                                        isLoading = true
                                        error = null
                                        
                                        // Normalize server URL - add https:// if no scheme provided
                                        var normalizedUrl = serverUrl.trim()
                                        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
                                            normalizedUrl = "https://$normalizedUrl"
                                        }
                                        normalizedUrl = normalizedUrl.trimEnd('/')
                                        
                                        val credentials = AuthManager.AuthCredentials(
                                            serverUrl = normalizedUrl,
                                            email = email.trim(),
                                            password = password
                                        )
                                        
                                        val result = authManager.login(credentials)
                                        
                                        isLoading = false
                                        
                                        if (result.isSuccess) {
                                            onLoginSuccess()
                                        } else {
                                            error = result.exceptionOrNull() as? LibreChatError
                                                ?: LibreChatError.AuthenticationError("Nieznany błąd podczas logowania")
                                        }
                                    }
                                }
                            }
                        )
                    )

                    // Error Display
                    error?.let { err ->
                        Spacer(modifier = Modifier.height(16.dp))
                        ErrorDisplay(
                            error = err,
                            onRetry = {
                                coroutineScope.launch {
                                    isLoading = true
                                    error = null
                                    
                                    // Normalize server URL - add https:// if no scheme provided
                                    var normalizedUrl = serverUrl.trim()
                                    if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
                                        normalizedUrl = "https://$normalizedUrl"
                                    }
                                    normalizedUrl = normalizedUrl.trimEnd('/')
                                    
                                    val credentials = AuthManager.AuthCredentials(
                                        serverUrl = normalizedUrl,
                                        email = email.trim(),
                                        password = password
                                    )
                                    
                                    val result = authManager.login(credentials)
                                    
                                    isLoading = false
                                    
                                    if (result.isSuccess) {
                                        onLoginSuccess()
                                    } else {
                                        error = result.exceptionOrNull() as? LibreChatError
                                            ?: LibreChatError.AuthenticationError("Nieznany błąd podczas logowania")
                                    }
                                }
                            },
                            onDismiss = { error = null },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Login Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isLoading || serverUrl.isBlank() || email.isBlank() || password.isBlank()) 
                                    Colors.lightGrey 
                                else 
                                    Colors.buttonNormal
                            )
                            .clickable(
                                enabled = !isLoading && serverUrl.isNotBlank() && email.isNotBlank() && password.isNotBlank()
                            ) {
                                Log.d("LoginScreen", "Login button clicked")
                                coroutineScope.launch {
                                    Log.d("LoginScreen", "Starting login coroutine")
                                    isLoading = true
                                    error = null
                                    
                                    // Normalize server URL - add https:// if no scheme provided
                                    var normalizedUrl = serverUrl.trim()
                                    if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
                                        normalizedUrl = "https://$normalizedUrl"
                                    }
                                    // Remove trailing slash
                                    normalizedUrl = normalizedUrl.trimEnd('/')
                                    
                                    val credentials = AuthManager.AuthCredentials(
                                        serverUrl = normalizedUrl,
                                        email = email.trim(),
                                        password = password
                                    )
                                    
                                    Log.d("LoginScreen", "Calling authManager.login with server: ${credentials.serverUrl}")
                                    val result = authManager.login(credentials)
                                    
                                    isLoading = false
                                    
                                    if (result.isSuccess) {
                                        Log.d("LoginScreen", "Login successful!")
                                        onLoginSuccess()
                                    } else {
                                        Log.e("LoginScreen", "Login failed: ${result.exceptionOrNull()?.message}")
                                        error = result.exceptionOrNull() as? LibreChatError
                                            ?: LibreChatError.AuthenticationError("Nieznany błąd podczas logowania")
                                    }
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Zaloguj się",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.W600,
                                color = Color.White,
                                style = TextStyles.base
                            )
                        }
                    }
                }
            }
        }
    }
}
