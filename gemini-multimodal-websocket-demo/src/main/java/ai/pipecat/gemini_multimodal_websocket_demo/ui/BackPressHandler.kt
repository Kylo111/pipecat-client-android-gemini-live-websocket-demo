package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.state.ConnectionState
import ai.pipecat.gemini_multimodal_websocket_demo.Screen
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Handles back button press behavior based on current screen and connection state.
 * 
 * Behavior:
 * - IN_CALL with active connection: Shows confirmation dialog
 * - IN_CALL when disconnected: Navigates directly without dialog
 * - THREAD_LIST: Exits app (default system behavior)
 * - Other screens: Navigates back
 */
@Composable
fun BackPressHandler(
    currentScreen: Screen,
    connectionState: ConnectionState,
    onEndSession: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var showExitDialog by remember { mutableStateOf(false) }
    
    // Determine if connection is active
    val isConnectionActive = connectionState == ConnectionState.CONNECTED ||
                            connectionState == ConnectionState.CONNECTING ||
                            connectionState == ConnectionState.RECONNECTING
    
    BackHandler(enabled = currentScreen == Screen.IN_CALL) {
        if (isConnectionActive) {
            // Show confirmation dialog when connection is active
            showExitDialog = true
        } else {
            // Navigate directly when disconnected
            onNavigateBack()
        }
    }
    
    // Exit confirmation dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = {
                Text(
                    text = "Zakończ rozmowę",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Black,
                    style = TextStyles.base
                )
            },
            text = {
                Text(
                    text = "Czy chcesz zakończyć rozmowę?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Black,
                    style = TextStyles.base
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        onEndSession()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Colors.buttonNormal
                    )
                ) {
                    Text(
                        text = "Tak",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W700,
                        color = Color.White,
                        style = TextStyles.base
                    )
                }
            },
            dismissButton = {
                Button(
                    onClick = { showExitDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "Nie",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W700,
                        color = Color.Black,
                        style = TextStyles.base
                    )
                }
            },
            containerColor = Color.White
        )
    }
}
