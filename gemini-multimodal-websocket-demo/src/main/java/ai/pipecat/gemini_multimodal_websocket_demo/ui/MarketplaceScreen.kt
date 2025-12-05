package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.R
import ai.pipecat.gemini_multimodal_websocket_demo.data.repository.ConfigurationRepository
import ai.pipecat.gemini_multimodal_websocket_demo.models.ConversationTemplate
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import ai.pipecat.gemini_multimodal_websocket_demo.usecases.ImportAssistantUseCase
import ai.pipecat.gemini_multimodal_websocket_demo.utils.IconMapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Marketplace screen displaying available assistant templates.
 * 
 * This screen shows a catalog of pre-configured conversation templates that users
 * can browse and import into their personal workspace. Each template is displayed
 * as a card with icon, title, description, and configuration details.
 * 
 * Requirements validated:
 * - 1.1: Displays all available conversation templates
 * - 1.2: Shows template metadata (title, description, voice, temperature)
 * - 1.3: Displays empty state message when no templates available
 * - 1.4: Organizes templates in scrollable list format
 * - 1.5: Shows description with maximum of 3 lines
 * - 8.1: Distinct visual styling from personal conversations
 * - 8.4: Clear "Import" action buttons
 * - 8.5: Elevated card styling with padding
 */
@Composable
fun MarketplaceScreen(
    configRepository: ConfigurationRepository,
    importUseCase: ImportAssistantUseCase,
    onBack: () -> Unit,
    onImportSuccess: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val templates = remember { configRepository.getMarketplaceTemplates() }
    
    var importingTemplateId by remember { mutableStateOf<String?>(null) }
    var showSuccessMessage by remember { mutableStateOf(false) }
    var showErrorMessage by remember { mutableStateOf<String?>(null) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.mainSurfaceBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Header with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(id = R.drawable.chevron_down),
                        contentDescription = "Back",
                        tint = Colors.buttonNormal,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Text(
                    text = "Marketplace",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.W700,
                    color = Colors.textPrimary,
                    style = TextStyles.base
                )
                
                // Spacer to balance the layout
                Spacer(modifier = Modifier.size(48.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Content area
            when {
                templates.isEmpty() -> {
                    // Empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No templates available.\nCheck back later for new assistants.",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W400,
                            color = Color.Gray,
                            style = TextStyles.base,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp)
                        )
                    }
                }
                
                else -> {
                    // Template list
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(templates) { template ->
                            MarketplaceTemplateCard(
                                template = template,
                                isImporting = importingTemplateId == template.id,
                                onImportClick = {
                                    importingTemplateId = template.id
                                    coroutineScope.launch {
                                        val result = importUseCase.execute(template.id)
                                        importingTemplateId = null
                                        
                                        if (result.isSuccess) {
                                            showSuccessMessage = true
                                            // Delay navigation to allow user to see success message
                                            kotlinx.coroutines.delay(2000)
                                            onImportSuccess()
                                        } else {
                                            showErrorMessage = result.exceptionOrNull()?.message 
                                                ?: "Failed to import template"
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // Success snackbar
        if (showSuccessMessage) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2000)
                showSuccessMessage = false
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF4CAF50),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = "✓ Template imported successfully!",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.White,
                        style = TextStyles.base,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
        
        // Error snackbar
        showErrorMessage?.let { errorMsg ->
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(3000)
                showErrorMessage = null
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF44336),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = "✗ $errorMsg",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.White,
                        style = TextStyles.base,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Card component displaying a single marketplace template.
 * 
 * Shows the template's icon, title, description (max 3 lines), voice option,
 * temperature setting, and an import button. Uses elevated card styling to
 * distinguish from flat conversation list items.
 * 
 * Requirements validated:
 * - 1.2: Shows title, description, voiceId, and temperature
 * - 1.5: Description limited to 3 lines
 * - 8.3: Displays icon based on iconIdentifier
 * - 8.4: Provides clear "Import" button
 * - 8.5: Elevated card styling with padding
 */
@Composable
private fun MarketplaceTemplateCard(
    template: ConversationTemplate,
    isImporting: Boolean,
    onImportClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Colors.cardBackground
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Icon and Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Icon(
                    painter = painterResource(id = IconMapper.getIconResource(template.iconIdentifier)),
                    contentDescription = null,
                    tint = Colors.buttonNormal,
                    modifier = Modifier.size(40.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Title
                Text(
                    text = template.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.W700,
                    color = Colors.textPrimary,
                    style = TextStyles.base,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Description (max 3 lines)
            Text(
                text = template.description,
                fontSize = 14.sp,
                fontWeight = FontWeight.W400,
                color = Colors.textSecondary,
                style = TextStyles.base,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Configuration details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Voice ID
                ConfigDetailChip(
                    label = "Voice",
                    value = template.voiceId ?: "Puck"
                )
                
                // Temperature
                ConfigDetailChip(
                    label = "Temperature",
                    value = String.format("%.1f", template.temperature)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Import button
            Button(
                onClick = onImportClick,
                enabled = !isImporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Colors.buttonNormal,
                    disabledContainerColor = Color.Gray
                )
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Importing...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.White,
                        style = TextStyles.base
                    )
                } else {
                    Text(
                        text = "Import",
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

/**
 * Small chip component displaying a configuration detail (label + value).
 */
@Composable
private fun ConfigDetailChip(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.W500,
            color = Color.Gray,
            style = TextStyles.base
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Colors.buttonSection)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.W600,
                color = Colors.textPrimary,
                style = TextStyles.base
            )
        }
    }
}
