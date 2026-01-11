package ai.pipecat.gemini_multimodal_websocket_demo.ui

import ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.ProductCategory
import ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.ShoppingItem
import ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes.ShoppingListManager
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.Colors
import ai.pipecat.gemini_multimodal_websocket_demo.ui.theme.TextStyles
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Shopping List Screen - displays shopping items grouped by category with checkboxes.
 * 
 * Requirements: 7.3, 7.5, 7.8
 */
@Composable
fun ShoppingListScreen(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val shoppingListManager = remember { ShoppingListManager(context) }
    val coroutineScope = rememberCoroutineScope()
    
    var items by remember { mutableStateOf<List<ShoppingItem>>(emptyList()) }
    var showClearDialog by remember { mutableStateOf(false) }
    
    // Load items
    LaunchedEffect(Unit) {
        items = shoppingListManager.getItems()
    }
    
    // Refresh function
    val refreshItems = {
        coroutineScope.launch {
            items = shoppingListManager.getItems()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.activityBackground)
            .padding(6.dp)
    ) {
        // Header with back button and title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Colors.buttonNormal)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "←",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W600,
                    color = Color.White,
                    style = TextStyles.base
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cart icon for visual distinction (Requirement 7.8)
                Text(
                    text = "🛒",
                    fontSize = 28.sp
                )
                Text(
                    text = "Lista zakupów",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.Black,
                    style = TextStyles.base
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        // "Wyczyść kupione" button (Requirement 7.5)
        if (items.any { it.isPurchased }) {
            Button(
                onClick = { showClearDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Colors.buttonWarning
                )
            ) {
                Text(
                    text = "Wyczyść kupione",
                    style = TextStyles.base,
                    fontWeight = FontWeight.W600
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (items.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🛒",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Lista zakupów jest pusta",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.Gray,
                        style = TextStyles.base
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Powiedz: 'Dodaj do listy zakupów: ...'",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        style = TextStyles.base
                    )
                }
            }
        } else {
            // Items list grouped by category (Requirement 7.3)
            val itemsByCategory = items.groupBy { it.category }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Iterate through categories in order
                ProductCategory.values()
                    .sortedBy { it.order }
                    .forEach { category ->
                        val categoryItems = itemsByCategory[category]
                        if (!categoryItems.isNullOrEmpty()) {
                            // Category header
                            item {
                                Text(
                                    text = category.displayName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.W700,
                                    color = Colors.buttonAccent,
                                    style = TextStyles.base,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            
                            // Category items
                            items(categoryItems) { item ->
                                ShoppingItemRow(
                                    item = item,
                                    onCheckedChange = { checked ->
                                        coroutineScope.launch {
                                            val updated = item.copy(isPurchased = checked)
                                            shoppingListManager.updateItem(updated)
                                            refreshItems()
                                        }
                                    }
                                )
                            }
                        }
                    }
            }
        }
    }
    
    // Clear purchased confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "Wyczyść kupione produkty?",
                    style = TextStyles.base,
                    fontWeight = FontWeight.W600
                )
            },
            text = {
                Text(
                    text = "Czy na pewno chcesz usunąć wszystkie kupione produkty z listy?",
                    style = TextStyles.base
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            shoppingListManager.clearPurchased()
                            refreshItems()
                            Toast.makeText(
                                context,
                                "Usunięto kupione produkty",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Colors.buttonWarning
                    )
                ) {
                    Text("Usuń", style = TextStyles.base)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showClearDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Gray
                    )
                ) {
                    Text("Anuluj", style = TextStyles.base)
                }
            }
        )
    }
}

/**
 * Individual shopping item row with checkbox.
 * 
 * Requirements: 7.3, 7.4
 */
@Composable
fun ShoppingItemRow(
    item: ShoppingItem,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable { onCheckedChange(!item.isPurchased) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Checkbox - larger size
        Checkbox(
            checked = item.isPurchased,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(32.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = Colors.buttonAccent
            )
        )
        
        // Item name with quantity
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = if (item.quantity != null) {
                    "${item.name} (${item.quantity})"
                } else {
                    item.name
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                color = if (item.isPurchased) Color.Gray else Color.Black,
                style = TextStyles.base,
                textDecoration = if (item.isPurchased) TextDecoration.LineThrough else TextDecoration.None
            )
        }
    }
}
