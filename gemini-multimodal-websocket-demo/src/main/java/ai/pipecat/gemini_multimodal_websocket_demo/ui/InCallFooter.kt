package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.R
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun FooterIconButton(
    modifier: Modifier,
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    contentDescription: String,
    foreground: Color,
    background: Color,
    border: Color,
) {
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier
            .border(1.dp, border, shape)
            .clip(shape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            modifier = Modifier.size(28.dp),
            painter = painterResource(icon),
            tint = foreground,
            contentDescription = contentDescription
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.InCallFooter(
    onClickEnd: () -> Unit,
    onCameraClick: () -> Unit = {},
    onGalleryClick: () -> Unit = {},
    onSpeakerClick: () -> Unit = {},
    isSpeakerphoneOn: Boolean = false,
) {
    var showImageOptions by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Row(
        Modifier
            .fillMaxWidth()
            .align(Alignment.CenterHorizontally)
            .padding(15.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FooterIconButton(
            modifier = Modifier,
            onClick = { showImageOptions = true },
            icon = R.drawable.image_gallery,
            contentDescription = "Send image",
            foreground = Color.White,
            background = Colors.buttonNormal,
            border = Colors.buttonNormal
        )
        
        FooterIconButton(
            modifier = Modifier,
            onClick = onSpeakerClick,
            icon = R.drawable.sound,
            contentDescription = "Toggle speaker",
            foreground = Color.White,
            background = if (isSpeakerphoneOn) Colors.buttonAccent else Colors.buttonNormal,
            border = if (isSpeakerphoneOn) Colors.buttonAccent else Colors.buttonNormal
        )
        
        FooterIconButton(
            modifier = Modifier,
            onClick = onClickEnd,
            icon = R.drawable.circle,
            contentDescription = "End call",
            foreground = Color.White,
            background = Colors.endButton,
            border = Colors.endButton
        )
    }

    if (showImageOptions) {
        ModalBottomSheet(
            onDismissRequest = { showImageOptions = false },
            sheetState = sheetState,
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Send Image",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                ImageOptionItem(
                    icon = R.drawable.video,
                    text = "Camera",
                    onClick = {
                        showImageOptions = false
                        onCameraClick()
                    }
                )
                
                ImageOptionItem(
                    icon = R.drawable.image,
                    text = "Gallery",
                    onClick = {
                        showImageOptions = false
                        onGalleryClick()
                    }
                )
                
                Spacer(modifier = Modifier.padding(bottom = 16.dp))
            }
        }
    }
}

@Composable
private fun ImageOptionItem(
    @DrawableRes icon: Int,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(icon),
            tint = Color.Black,
            contentDescription = null
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.W500,
            color = Color.Black
        )
    }
}
