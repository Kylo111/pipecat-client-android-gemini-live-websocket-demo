package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dialog displaying step-by-step instructions for creating wake words in Picovoice Console.
 * Includes tips for choosing good wake words and a link to the console.
 * 
 * @param onDismiss Callback invoked when dialog is dismissed
 * @param onImportClick Callback invoked when user clicks "Import .ppn file" button
 */
@Composable
fun WakeWordInstructionsDialog(
    onDismiss: () -> Unit,
    onImportClick: () -> Unit
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Jak utworzyć wake word",
                fontSize = 20.sp,
                fontWeight = FontWeight.W700,
                color = Color.Black,
                style = TextStyles.base
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Step 1: Link to Picovoice Console
                InstructionStep(
                    number = 1,
                    text = "Przejdź do Picovoice Console"
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Colors.buttonNormal)
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.picovoice.ai"))
                            context.startActivity(intent)
                        }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Otwórz Console →",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.White,
                        style = TextStyles.base
                    )
                }
                
                // Step 2: Create account
                InstructionStep(
                    number = 2,
                    text = "Utwórz konto lub zaloguj się (darmowe)"
                )
                
                // Step 3: Navigate to Porcupine
                InstructionStep(
                    number = 3,
                    text = "Przejdź do sekcji 'Porcupine' → 'Wake Words'"
                )
                
                // Step 4: Create wake word
                InstructionStep(
                    number = 4,
                    text = "Kliknij 'Create Wake Word'"
                )
                
                // Step 5: Enter name
                InstructionStep(
                    number = 5,
                    text = "Wpisz nazwę wake word (np. 'asystent')"
                )
                
                // Step 6: Select language
                InstructionStep(
                    number = 6,
                    text = "Wybierz język: Polski (pl)"
                )
                
                // Step 7: Train
                InstructionStep(
                    number = 7,
                    text = "Kliknij 'Train' - proces trwa ~10 sekund"
                )
                
                // Step 8: Download
                InstructionStep(
                    number = 8,
                    text = "Pobierz plik .ppn dla platformy Android"
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Default wake word info
                TipCard(
                    title = "Domyślne słowo aktywacyjne",
                    tips = listOf(
                        "Aplikacja używa wbudowanego słowa 'ALEXA' do pauzowania/wznawiania sesji",
                        "Działa od razu bez potrzeby tworzenia plików .ppn",
                        "Możesz utworzyć własne wake words dla lepszego dopasowania"
                    )
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Tips section
                TipCard(
                    title = "Wskazówki",
                    tips = listOf(
                        "Wybieraj wake words z wieloma sylabami (np. 'asystent' lepsze niż 'ok')",
                        "Unikaj popularnych słów używanych w codziennych rozmowach",
                        "Testuj wake word w Console przed importem",
                        "Używaj unikalnej wymowy dla lepszego rozpoznawania"
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onImportClick()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Colors.buttonNormal
                )
            ) {
                Text(
                    text = "Importuj plik .ppn",
                    style = TextStyles.base,
                    fontWeight = FontWeight.W600
                )
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.LightGray
                )
            ) {
                Text(
                    text = "Zamknij",
                    style = TextStyles.base,
                    fontWeight = FontWeight.W600,
                    color = Color.Black
                )
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Individual instruction step with number and text.
 */
@Composable
private fun InstructionStep(
    number: Int,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Step number circle
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Colors.buttonNormal),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                fontSize = 14.sp,
                fontWeight = FontWeight.W700,
                color = Color.White,
                style = TextStyles.base
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Step text
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.W400,
            color = Color.Black,
            style = TextStyles.base,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Card displaying tips for creating wake words.
 */
@Composable
private fun TipCard(
    title: String,
    tips: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = Color(0xFFFFA500),
                shape = RoundedCornerShape(8.dp)
            )
            .background(Color(0xFFFFF8E1), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "💡 $title",
            fontSize = 14.sp,
            fontWeight = FontWeight.W700,
            color = Color(0xFFFF8C00),
            style = TextStyles.base
        )
        
        tips.forEach { tip ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W700,
                    color = Color(0xFFFF8C00),
                    style = TextStyles.base,
                    modifier = Modifier.padding(end = 8.dp)
                )
                
                Text(
                    text = tip,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W400,
                    color = Color.Black,
                    style = TextStyles.base,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
