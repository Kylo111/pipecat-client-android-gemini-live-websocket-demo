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
fun DuplexModeButton(
    isFullDuplex: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .size(48.dp)
            .background(
                color = if (isFullDuplex) Colors.buttonAccent else Colors.buttonNormal,
                shape = CircleShape
            )
    ) {
        Icon(
            painter = painterResource(
                id = if (isFullDuplex) R.drawable.ic_duplex_full else R.drawable.ic_duplex_half
            ),
            contentDescription = if (isFullDuplex) "Full-duplex mode: can interrupt bot" else "Half-duplex mode: wait for bot to finish",
            tint = Color.White
        )
    }
}

@Composable
@Preview
fun PreviewDuplexModeButtonFull() {
    DuplexModeButton(
        isFullDuplex = true,
        onToggle = {}
    )
}

@Composable
@Preview
fun PreviewDuplexModeButtonHalf() {
    DuplexModeButton(
        isFullDuplex = false,
        onToggle = {}
    )
}
