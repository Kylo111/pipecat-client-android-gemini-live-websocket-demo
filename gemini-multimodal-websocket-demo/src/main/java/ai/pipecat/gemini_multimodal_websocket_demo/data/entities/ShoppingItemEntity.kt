package ai.pipecat.gemini_multimodal_websocket_demo.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for shopping list items.
 * 
 * Items are automatically categorized and can be marked as purchased.
 */
@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    
    /** Name of the product */
    val name: String,
    
    /** Optional quantity (e.g., 2, 5) */
    val quantity: Int?,
    
    /** Product category (stored as string enum value) */
    val category: String,
    
    /** Whether this item has been purchased */
    val isPurchased: Boolean = false,
    
    /** When this item was added (epoch milliseconds) */
    val createdAt: Long = System.currentTimeMillis()
)
