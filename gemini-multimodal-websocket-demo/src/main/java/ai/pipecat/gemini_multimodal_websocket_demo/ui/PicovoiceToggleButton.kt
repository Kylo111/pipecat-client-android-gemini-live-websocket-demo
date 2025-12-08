package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.R
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun PicovoiceToggleButton(
    isEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .size(48.dp)
            .background(
                color = if (isEnabled) Colors.buttonAccent else Colors.buttonWarning,
                shape = CircleShape
            )
    ) {
        Icon(
            painter = painterResource(
                id = if (isEnabled) R.drawable.ic_wake_word_on else R.drawable.ic_wake_word_off
            ),
            contentDescription = if (isEnabled) "Wake word detection enabled" else "Wake word detection disabled",
            tint = Color.White
        )
    }
}

@Composable
@Preview
fun PreviewPicovoiceToggleButtonEnabled() {
    PicovoiceToggleButton(
        isEnabled = true,
        onToggle = {}
    )
}

@Composable
@Preview
fun PreviewPicovoiceToggleButtonDisabled() {
    PicovoiceToggleButton(
        isEnabled = false,
        onToggle = {}
    )
}
