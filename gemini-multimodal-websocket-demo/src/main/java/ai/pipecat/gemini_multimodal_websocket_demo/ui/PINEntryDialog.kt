package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.PINManager
import ai.pipecat.gemini_multimodal_websocket_demo.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Dialog for PIN entry with numeric keypad
 * Validates 4-digit PIN and calls callback on successful validation
 * 
 * @param onPINValidated Callback invoked when correct PIN is entered
 * @param onDismiss Callback invoked when dialog is dismissed
 */
@Composable
fun PINEntryDialog(
    onPINValidated: () -> Unit,
    onDismiss: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
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
                    text = stringResource(id = R.string.pin_enter_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(id = R.string.pin_default_info),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.Gray,
                    style = TextStyles.base
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // PIN dots display
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    for (index in 0..3) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index < pinInput.length) Colors.buttonNormal
                                    else Colors.lightGrey
                                )
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Error message
                if (errorMessage != null) {
                    val errorText = if (errorMessage == "ERROR_INVALID_PIN") {
                        stringResource(id = R.string.pin_invalid_error)
                    } else {
                        errorMessage!!
                    }
                    
                    Text(
                        text = errorText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W400,
                        color = Colors.buttonWarning,
                        style = TextStyles.base
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Numeric keypad (3x4 grid)
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Row 1: 1, 2, 3
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NumericButton("1", Modifier.weight(1f)) {
                            if (pinInput.length < 4) {
                                pinInput += "1"
                                checkPIN(pinInput, onPINValidated) { error ->
                                    errorMessage = error
                                    pinInput = ""
                                }
                            }
                        }
                        NumericButton("2", Modifier.weight(1f)) {
                            if (pinInput.length < 4) {
                                pinInput += "2"
                                checkPIN(pinInput, onPINValidated) { error ->
                                    errorMessage = error
                                    pinInput = ""
                                }
                            }
                        }
                        NumericButton("3", Modifier.weight(1f)) {
                            if (pinInput.length < 4) {
                                pinInput += "3"
                                checkPIN(pinInput, onPINValidated) { error ->
                                    errorMessage = error
                                    pinInput = ""
                                }
                            }
                        }
                    }
                    
                    // Row 2: 4, 5, 6
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NumericButton("4", Modifier.weight(1f)) {
                            if (pinInput.length < 4) {
                                pinInput += "4"
                                checkPIN(pinInput, onPINValidated) { error ->
                                    errorMessage = error
                                    pinInput = ""
                                }
                            }
                        }
                        NumericButton("5", Modifier.weight(1f)) {
                            if (pinInput.length < 4) {
                                pinInput += "5"
                                checkPIN(pinInput, onPINValidated) { error ->
                                    errorMessage = error
                                    pinInput = ""
                                }
                            }
                        }
                        NumericButton("6", Modifier.weight(1f)) {
                            if (pinInput.length < 4) {
                                pinInput += "6"
                                checkPIN(pinInput, onPINValidated) { error ->
                                    errorMessage = error
                                    pinInput = ""
                                }
                            }
                        }
                    }
                    
                    // Row 3: 7, 8, 9
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NumericButton("7", Modifier.weight(1f)) {
                            if (pinInput.length < 4) {
                                pinInput += "7"
                                checkPIN(pinInput, onPINValidated) { error ->
                                    errorMessage = error
                                    pinInput = ""
                                }
                            }
                        }
                        NumericButton("8", Modifier.weight(1f)) {
                            if (pinInput.length < 4) {
                                pinInput += "8"
                                checkPIN(pinInput, onPINValidated) { error ->
                                    errorMessage = error
                                    pinInput = ""
                                }
                            }
                        }
                        NumericButton("9", Modifier.weight(1f)) {
                            if (pinInput.length < 4) {
                                pinInput += "9"
                                checkPIN(pinInput, onPINValidated) { error ->
                                    errorMessage = error
                                    pinInput = ""
                                }
                            }
                        }
                    }
                    
                    // Row 4: empty, 0, backspace
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Empty space
                        Box(modifier = Modifier.weight(1f))
                        
                        // 0 button
                        NumericButton("0", Modifier.weight(1f)) {
                            if (pinInput.length < 4) {
                                pinInput += "0"
                                checkPIN(pinInput, onPINValidated) { error ->
                                    errorMessage = error
                                    pinInput = ""
                                }
                            }
                        }
                        
                        // Backspace button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Colors.lightGrey)
                                .clickable {
                                    if (pinInput.isNotEmpty()) {
                                        pinInput = pinInput.dropLast(1)
                                        errorMessage = null
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⌫",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.W600,
                                color = Colors.buttonNormal,
                                style = TextStyles.base
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Cancel button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(
                            width = 1.dp,
                            color = Colors.buttonNormal,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.common_cancel),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        color = Colors.buttonNormal,
                        style = TextStyles.base
                    )
                }
            }
        }
    }
}

/**
 * Numeric button component for the keypad
 */
@Composable
private fun NumericButton(
    digit: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Colors.buttonNormal)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            fontSize = 24.sp,
            fontWeight = FontWeight.W600,
            color = Color.White,
            style = TextStyles.base
        )
    }
}

/**
 * Check if PIN is complete and validate it
 */
private fun checkPIN(
    pin: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (pin.length == 4) {
        if (PINManager.validatePIN(pin)) {
            onSuccess()
        } else {
            // We can't access context here easily for string resource in logic function,
            // but the error is displayed in UI which is Composable.
            // Better to pass a resource ID or let the UI handle the error message.
            // For now, let's keep it simple and assume the UI will display a generic error
            // or we modify the signature.
            // To be safe and quick without changing signature too much:
            onError("ERROR_INVALID_PIN") // We will handle this in UI to show localized string
        }
    }
}
