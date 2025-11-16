package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.ThemeManager
import ai.pipecat.gemini_multimodal_websocket_demo.models.AppTheme
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Shapes
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.ThemePresets
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionScreen(
    onBack: () -> Unit
) {
    val currentTheme = ThemeManager.currentTheme.value
    val isDark = ThemeManager.isDarkTheme.value
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wybierz motyw") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Colors.mainSurfaceBackground,
                    titleContentColor = Colors.textPrimary
                )
            )
        },
        containerColor = Colors.activityBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Dark mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(Shapes.cardCornerRadius))
                    .background(Colors.cardBackground)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Tryb ciemny",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Colors.textPrimary
                    )
                    Text(
                        text = "Włącz ciemny wariant motywu",
                        fontSize = 12.sp,
                        color = Colors.textSecondary
                    )
                }
                Switch(
                    checked = isDark,
                    onCheckedChange = { ThemeManager.toggleTheme() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Colors.buttonAccent,
                        checkedTrackColor = Colors.buttonAccent.copy(alpha = 0.5f)
                    )
                )
            }
            
            // Theme list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(AppTheme.entries) { theme ->
                    ThemeCard(
                        theme = theme,
                        isSelected = theme == currentTheme,
                        isDark = isDark,
                        onClick = { ThemeManager.setTheme(theme) }
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeCard(
    theme: AppTheme,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val style = ThemePresets.getThemeStyle(theme, isDark)
    
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) style.colors.buttonAccent else Color.Transparent,
        animationSpec = tween(300), label = ""
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 2.dp,
        animationSpec = tween(300), label = ""
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation, RoundedCornerShape(style.shapes.cardCornerRadius))
            .clip(RoundedCornerShape(style.shapes.cardCornerRadius))
            .background(style.colors.cardBackground)
            .border(
                width = if (isSelected) 3.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(style.shapes.cardCornerRadius)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header with icon and name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = theme.icon,
                        fontSize = 32.sp
                    )
                    Column {
                        Text(
                            text = theme.displayName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = style.colors.textPrimary
                        )
                        Text(
                            text = theme.description,
                            fontSize = 12.sp,
                            color = style.colors.textSecondary
                        )
                    }
                }
                
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Wybrany",
                        tint = style.colors.buttonAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Preview
            ThemePreview(style)
        }
    }
}

@Composable
fun ThemePreview(style: ai.pipecat.gemini_multimodal_websocket_demo.models.ThemeStyle) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Color palette
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ColorSwatch(style.colors.buttonNormal, Modifier.weight(1f))
            ColorSwatch(style.colors.buttonAccent, Modifier.weight(1f))
            ColorSwatch(style.colors.buttonWarning, Modifier.weight(1f))
            ColorSwatch(style.colors.audioIndicatorActive, Modifier.weight(1f))
        }
        
        // Button preview
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(style.shapes.buttonCornerRadius))
                    .background(
                        if (style.effects.useGradients) {
                            Brush.horizontalGradient(
                                listOf(style.colors.gradientStart, style.colors.gradientEnd)
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(style.colors.buttonNormal, style.colors.buttonNormal)
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Przycisk",
                    color = style.colors.textOnButton,
                    fontSize = 12.sp
                )
            }
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(style.shapes.micButtonCornerRadius))
                    .background(style.colors.unmutedMicBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎤",
                    fontSize = 16.sp
                )
            }
        }
        
        // Shape indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Kształty:",
                fontSize = 10.sp,
                color = style.colors.textSecondary
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(style.shapes.buttonCornerRadius))
                    .background(style.colors.lightGrey)
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(style.shapes.indicatorCornerRadius))
                    .background(style.colors.lightGrey)
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Effects indicators
            if (style.effects.useGradients) {
                Text(text = "🌈", fontSize = 12.sp)
            }
            if (style.effects.useGlow) {
                Text(text = "✨", fontSize = 12.sp)
            }
            if (style.effects.useShadows) {
                Text(text = "🌑", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun ColorSwatch(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
    )
}
