package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.ConnectionState
import ai.pipecat.gemini_multimodal_websocket_demo.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConnectionStatusIndicator(
    connectionState: ConnectionState,
    reconnectionAttempt: Int = 0,
    maxReconnectionAttempts: Int = 5,
    isPaused: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Show indicator for all states except DISCONNECTING
    AnimatedVisibility(
        visible = connectionState != ConnectionState.DISCONNECTING,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        // Determine if we should show animated indicator
        val shouldAnimate = connectionState == ConnectionState.RECONNECTING || 
                           connectionState == ConnectionState.CONNECTING
        
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = if (shouldAnimate) 0.3f else 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )

        // Determine color based on state
        val backgroundColor = when {
            isPaused && connectionState == ConnectionState.DISCONNECTED -> Color(0xFF9E9E9E) // Gray for paused
            connectionState == ConnectionState.CONNECTED -> Color(0xFF4CAF50) // Green
            connectionState == ConnectionState.RECONNECTING -> Color(0xFFFFA726) // Orange
            connectionState == ConnectionState.CONNECTING -> Color(0xFF42A5F5) // Blue
            connectionState == ConnectionState.DISCONNECTED -> Color(0xFFF44336) // Red
            else -> Color.Transparent
        }
        
        // Determine text based on state
        val statusText = when {
            isPaused && connectionState == ConnectionState.DISCONNECTED -> "Wstrzymano (kliknij mikrofon aby wznowić)"
            connectionState == ConnectionState.CONNECTED -> stringResource(R.string.connection_status_connected)
            connectionState == ConnectionState.RECONNECTING -> {
                if (reconnectionAttempt > 0) {
                    stringResource(
                        R.string.connection_status_reconnecting_with_attempt,
                        reconnectionAttempt,
                        maxReconnectionAttempts
                    )
                } else {
                    stringResource(R.string.connection_status_reconnecting)
                }
            }
            connectionState == ConnectionState.CONNECTING -> stringResource(R.string.connection_status_connecting)
            connectionState == ConnectionState.DISCONNECTED -> stringResource(R.string.connection_status_disconnected)
            else -> ""
        }

        Box(
            modifier = Modifier
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(if (shouldAnimate) alpha else 1f)
                        .background(Color.White, CircleShape)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
