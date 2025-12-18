package ai.pipecat.gemini_multimodal_websocket_demo.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing user category corrections for products.
 * 
 * When a user manually changes a product's category, we store that
 * correction and use it for future additions of the same product.
 */
@Entity(tableName = "product_categories")
data class ProductCategoryEntity(
    @PrimaryKey 
    val productName: String,
    
    /** Category assigned to this product (stored as string enum value) */
    val category: String,
    
    /** Whether this is a user correction (true) or default mapping (false) */
    val isUserCorrection: Boolean = false
)
