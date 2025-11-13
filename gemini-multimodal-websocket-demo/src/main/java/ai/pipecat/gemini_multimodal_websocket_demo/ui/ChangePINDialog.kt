package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.PINManager
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
 * Dialog for changing PIN
 * Validates current PIN, new PIN (4 digits), and confirmation match
 * 
 * @param onPINChanged Callback invoked when PIN is successfully changed
 * @param onDismiss Callback invoked when dialog is dismissed
 */
@Composable
fun ChangePINDialog(
    onPINChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    var currentStep by remember { mutableStateOf(PINChangeStep.CURRENT_PIN) }
    var currentPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

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
                    text = when (currentStep) {
                        PINChangeStep.CURRENT_PIN -> "Wprowadź obecny PIN"
                        PINChangeStep.NEW_PIN -> "Wprowadź nowy PIN"
                        PINChangeStep.CONFIRM_PIN -> "Potwierdź nowy PIN"
                        PINChangeStep.SUCCESS -> "PIN zmieniony"
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
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
                    val currentInput = when (currentStep) {
                        PINChangeStep.CURRENT_PIN -> currentPinInput
                        PINChangeStep.NEW_PIN -> newPinInput
                        PINChangeStep.CONFIRM_PIN -> confirmPinInput
                        PINChangeStep.SUCCESS -> "****"
                    }
                    for (index in 0..3) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index < currentInput.length) Colors.buttonNormal
                                    else Colors.lightGrey
                                )
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Error message
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W400,
                        color = Colors.buttonWarning,
                        style = TextStyles.base
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Success message
                if (successMessage != null) {
                    Text(
                        text = successMessage!!,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W400,
                        color = Colors.buttonNormal,
                        style = TextStyles.base
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Show keypad only if not in success state
                if (currentStep != PINChangeStep.SUCCESS) {
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
                            for (digit in 1..3) {
                                NumericButton(digit.toString(), Modifier.weight(1f)) {
                                    handleDigitInput(
                                        digit.toString(),
                                        currentStep,
                                        currentPinInput,
                                        newPinInput,
                                        confirmPinInput,
                                        onCurrentPinChange = { currentPinInput = it },
                                        onNewPinChange = { newPinInput = it },
                                        onConfirmPinChange = { confirmPinInput = it },
                                        onStepChange = { currentStep = it },
                                        onError = { errorMessage = it },
                                        onSuccess = { 
                                            successMessage = it
                                            currentStep = PINChangeStep.SUCCESS
                                        }
                                    )
                                }
                            }
                        }
                        
                        // Row 2: 4, 5, 6
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (digit in 4..6) {
                                NumericButton(digit.toString(), Modifier.weight(1f)) {
                                    handleDigitInput(
                                        digit.toString(),
                                        currentStep,
                                        currentPinInput,
                                        newPinInput,
                                        confirmPinInput,
                                        onCurrentPinChange = { currentPinInput = it },
                                        onNewPinChange = { newPinInput = it },
                                        onConfirmPinChange = { confirmPinInput = it },
                                        onStepChange = { currentStep = it },
                                        onError = { errorMessage = it },
                                        onSuccess = { 
                                            successMessage = it
                                            currentStep = PINChangeStep.SUCCESS
                                        }
                                    )
                                }
                            }
                        }
                        
                        // Row 3: 7, 8, 9
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (digit in 7..9) {
                                NumericButton(digit.toString(), Modifier.weight(1f)) {
                                    handleDigitInput(
                                        digit.toString(),
                                        currentStep,
                                        currentPinInput,
                                        newPinInput,
                                        confirmPinInput,
                                        onCurrentPinChange = { currentPinInput = it },
                                        onNewPinChange = { newPinInput = it },
                                        onConfirmPinChange = { confirmPinInput = it },
                                        onStepChange = { currentStep = it },
                                        onError = { errorMessage = it },
                                        onSuccess = { 
                                            successMessage = it
                                            currentStep = PINChangeStep.SUCCESS
                                        }
                                    )
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
                                handleDigitInput(
                                    "0",
                                    currentStep,
                                    currentPinInput,
                                    newPinInput,
                                    confirmPinInput,
                                    onCurrentPinChange = { currentPinInput = it },
                                    onNewPinChange = { newPinInput = it },
                                    onConfirmPinChange = { confirmPinInput = it },
                                    onStepChange = { currentStep = it },
                                    onError = { errorMessage = it },
                                    onSuccess = { 
                                        successMessage = it
                                        currentStep = PINChangeStep.SUCCESS
                                    }
                                )
                            }
                            
                            // Backspace button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Colors.lightGrey)
                                    .clickable {
                                        when (currentStep) {
                                            PINChangeStep.CURRENT_PIN -> {
                                                if (currentPinInput.isNotEmpty()) {
                                                    currentPinInput = currentPinInput.dropLast(1)
                                                    errorMessage = null
                                                }
                                            }
                                            PINChangeStep.NEW_PIN -> {
                                                if (newPinInput.isNotEmpty()) {
                                                    newPinInput = newPinInput.dropLast(1)
                                                    errorMessage = null
                                                }
                                            }
                                            PINChangeStep.CONFIRM_PIN -> {
                                                if (confirmPinInput.isNotEmpty()) {
                                                    confirmPinInput = confirmPinInput.dropLast(1)
                                                    errorMessage = null
                                                }
                                            }
                                            PINChangeStep.SUCCESS -> {}
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
                }
                
                // Action buttons
                if (currentStep == PINChangeStep.SUCCESS) {
                    // OK button for success state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Colors.buttonNormal)
                            .clickable { 
                                onPINChanged()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "OK",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W600,
                            color = Color.White,
                            style = TextStyles.base
                        )
                    }
                } else {
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
                            text = "Anuluj",
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
 * Enum representing the steps in the PIN change process
 */
private enum class PINChangeStep {
    CURRENT_PIN,
    NEW_PIN,
    CONFIRM_PIN,
    SUCCESS
}

/**
 * Handle digit input for the current step
 */
private fun handleDigitInput(
    digit: String,
    currentStep: PINChangeStep,
    currentPinInput: String,
    newPinInput: String,
    confirmPinInput: String,
    onCurrentPinChange: (String) -> Unit,
    onNewPinChange: (String) -> Unit,
    onConfirmPinChange: (String) -> Unit,
    onStepChange: (PINChangeStep) -> Unit,
    onError: (String) -> Unit,
    onSuccess: (String) -> Unit
) {
    when (currentStep) {
        PINChangeStep.CURRENT_PIN -> {
            if (currentPinInput.length < 4) {
                val newInput = currentPinInput + digit
                onCurrentPinChange(newInput)
                
                if (newInput.length == 4) {
                    // Validate current PIN
                    if (PINManager.validatePIN(newInput)) {
                        onStepChange(PINChangeStep.NEW_PIN)
                        onError("")
                    } else {
                        onError("Nieprawidłowy obecny PIN")
                        onCurrentPinChange("")
                    }
                }
            }
        }
        PINChangeStep.NEW_PIN -> {
            if (newPinInput.length < 4) {
                val newInput = newPinInput + digit
                onNewPinChange(newInput)
                
                if (newInput.length == 4) {
                    // Validate new PIN is 4 digits
                    if (newInput.all { it.isDigit() }) {
                        onStepChange(PINChangeStep.CONFIRM_PIN)
                        onError("")
                    } else {
                        onError("PIN musi składać się z 4 cyfr")
                        onNewPinChange("")
                    }
                }
            }
        }
        PINChangeStep.CONFIRM_PIN -> {
            if (confirmPinInput.length < 4) {
                val newInput = confirmPinInput + digit
                onConfirmPinChange(newInput)
                
                if (newInput.length == 4) {
                    // Validate confirmation matches new PIN
                    if (newInput == newPinInput) {
                        // Change PIN
                        val result = PINManager.changePIN(currentPinInput, newPinInput)
                        if (result.isSuccess) {
                            onSuccess("PIN został pomyślnie zmieniony")
                        } else {
                            onError(result.exceptionOrNull()?.message ?: "Błąd zmiany PIN")
                            onConfirmPinChange("")
                        }
                    } else {
                        onError("PIN nie pasuje")
                        onConfirmPinChange("")
                    }
                }
            }
        }
        PINChangeStep.SUCCESS -> {
            // Do nothing in success state
        }
    }
}
