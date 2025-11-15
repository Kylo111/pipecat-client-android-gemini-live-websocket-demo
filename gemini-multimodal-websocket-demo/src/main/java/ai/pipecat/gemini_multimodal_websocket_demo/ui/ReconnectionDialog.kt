package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import ai.pipecat.gemini_multimodal_websocket_demo.R

/**
 * Dialog shown when reconnection attempts reach the maximum limit
 * Allows user to continue trying or end the conversation
 * 
 * @param onContinue Callback invoked when user chooses to continue reconnection attempts
 * @param onEndConversation Callback invoked when user chooses to end the conversation
 */
@Composable
fun ReconnectionDialog(
    onContinue: () -> Unit,
    onEndConversation: () -> Unit
) {
    Dialog(onDismissRequest = { /* Dialog cannot be dismissed by clicking outside */ }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = stringResource(R.string.reconnection_dialog_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Message
                Text(
                    text = stringResource(R.string.reconnection_dialog_message),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Black,
                    style = TextStyles.base,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // End conversation button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .border(
                                width = 1.dp,
                                color = Colors.buttonNormal,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .clickable { onEndConversation() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.reconnection_dialog_end),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W600,
                            color = Colors.buttonNormal,
                            style = TextStyles.base
                        )
                    }
                    
                    // Continue button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Colors.buttonNormal)
                            .clickable { onContinue() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.reconnection_dialog_continue),
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
