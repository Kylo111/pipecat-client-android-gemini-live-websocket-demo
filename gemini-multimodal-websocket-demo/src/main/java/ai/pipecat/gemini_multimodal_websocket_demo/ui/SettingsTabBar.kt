package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Shapes
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Horizontal tab bar for the Settings screen
 * 
 * Displays 4 tabs that fit on screen without scrolling:
 * - API Keys and Accounts (Klucze i konta)
 * - Session and Appearance (Sesja i wygląd)
 * - Agents (Agenci)
 * - Integrations (Integracje)
 * 
 * @param selectedTab The currently selected tab
 * @param onTabSelected Callback invoked when a tab is clicked
 * @param modifier Optional modifier for the tab bar
 */
@Composable
fun SettingsTabBar(
    selectedTab: SettingsTab,
    onTabSelected: (SettingsTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Colors.mainSurfaceBackground)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsTab.entries.forEach { tab ->
            TabItem(
                tab = tab,
                isSelected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Individual tab item in the tab bar
 * 
 * @param tab The tab to display
 * @param isSelected Whether this tab is currently selected
 * @param onClick Callback invoked when the tab is clicked
 * @param modifier Optional modifier for the tab item
 */
@Composable
private fun TabItem(
    tab: SettingsTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        Colors.buttonAccent
    } else {
        Colors.cardBackground
    }
    
    val textColor = if (isSelected) {
        Colors.textOnButton
    } else {
        Colors.textSecondary
    }
    
    val borderColor = if (isSelected) {
        Colors.buttonAccent
    } else {
        Colors.textFieldBorder
    }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Shapes.buttonCornerRadius))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(Shapes.buttonCornerRadius)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = tab.icon,
                fontSize = 16.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(
                text = tab.title,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                lineHeight = 12.sp
            )
        }
    }
}
