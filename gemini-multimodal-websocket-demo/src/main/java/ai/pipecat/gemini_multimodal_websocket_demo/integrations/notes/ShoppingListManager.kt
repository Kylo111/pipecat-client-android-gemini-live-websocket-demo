package ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes

import android.content.Context
import ai.pipecat.gemini_multimodal_websocket_demo.data.AppDatabase
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.ShoppingItemEntity
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.ProductCategoryEntity
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Shopping list item data model.
 */
data class ShoppingItem(
    val id: Long,
    val name: String,
    val quantity: Int?,
    val category: ProductCategory,
    val isPurchased: Boolean,
    val createdAt: LocalDateTime
)

/**
 * Manages the shopping list special note.
 * 
 * Provides CRUD operations for shopping items with automatic categorization
 * and user category corrections.
 * 
 * Requirements: 7.1, 7.2, 7.7
 */
class ShoppingListManager(private val context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val shoppingItemDao = database.shoppingItemDao()
    private val productCategoryDao = database.productCategoryDao()
    
    /**
     * Gets all shopping items, sorted by category order.
     * 
     * Items are returned in category order (as defined by ProductCategory.order),
     * then alphabetically by name within each category.
     * 
     * @return List of shopping items sorted by category
     */
    suspend fun getItems(): List<ShoppingItem> {
        val entities = shoppingItemDao.getAllItems()
        return entities
            .map { it.toShoppingItem() }
            .sortedWith(compareBy({ it.category.order }, { it.name }))
    }
    
    /**
     * Adds a new item to the shopping list with automatic categorization.
     * 
     * The category is determined by:
     * 1. User correction for this product (if exists)
     * 2. Default dictionary mapping
     * 3. Fallback to OTHER
     * 
     * @param name Product name
     * @param quantity Optional quantity
     * @return The created shopping item
     */
    suspend fun addItem(name: String, quantity: Int? = null): ShoppingItem {
        val category = getCategoryForProduct(name)
        
        val entity = ShoppingItemEntity(
            name = name.trim(),
            quantity = quantity,
            category = category.name,
            isPurchased = false,
            createdAt = System.currentTimeMillis()
        )
        
        val id = shoppingItemDao.insertItem(entity)
        return entity.copy(id = id).toShoppingItem()
    }
    
    /**
     * Gets the category for a product.
     * 
     * Checks user corrections first, then falls back to default mapping.
     * 
     * @param productName Product name to categorize
     * @return ProductCategory for this product
     */
    suspend fun getCategoryForProduct(productName: String): ProductCategory {
        // Check for user correction first
        val userCorrection = productCategoryDao.getCategoryForProduct(productName.trim().lowercase())
        if (userCorrection != null) {
            return ProductCategory.fromString(userCorrection.category)
        }
        
        // Fall back to default mapping
        return ProductCategoryMapper.getCategoryForProduct(productName)
    }
    
    /**
     * Updates an existing shopping item.
     * 
     * Used primarily for marking items as purchased/unpurchased.
     * 
     * @param item Shopping item to update
     */
    suspend fun updateItem(item: ShoppingItem) {
        val entity = ShoppingItemEntity(
            id = item.id,
            name = item.name,
            quantity = item.quantity,
            category = item.category.name,
            isPurchased = item.isPurchased,
            createdAt = item.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        shoppingItemDao.updateItem(entity)
    }
    
    /**
     * Deletes a shopping item by ID.
     * 
     * @param id Item ID to delete
     */
    suspend fun deleteItem(id: Long) {
        shoppingItemDao.deleteItem(id)
    }
    
    /**
     * Deletes a shopping item by name.
     * 
     * If multiple items have the same name, returns a list of matching items
     * for the user to choose from (they should use deleteItem(id) instead).
     * 
     * @param name Item name to delete
     * @return List of matching items if multiple found, empty list if deleted successfully
     */
    suspend fun deleteItemByName(name: String): List<ShoppingItem> {
        val allItems = shoppingItemDao.getAllItems()
        val matches = allItems.filter { it.name.equals(name, ignoreCase = true) }
        
        return when {
            matches.isEmpty() -> emptyList() // No matches found
            matches.size == 1 -> {
                // Single match - delete it
                shoppingItemDao.deleteItem(matches[0].id)
                emptyList()
            }
            else -> {
                // Multiple matches - return them for user to choose
                matches.map { it.toShoppingItem() }
            }
        }
    }
    
    /**
     * Marks an item as purchased by name.
     * 
     * If multiple items have the same name, returns a list of matching items
     * for the user to choose from (they should use markItemPurchasedById instead).
     * 
     * @param name Item name to mark as purchased
     * @return List of matching items if multiple found, empty list if marked successfully
     */
    suspend fun markItemPurchased(name: String): List<ShoppingItem> {
        val allItems = shoppingItemDao.getAllItems()
        val matches = allItems.filter { 
            it.name.equals(name, ignoreCase = true) && !it.isPurchased 
        }
        
        return when {
            matches.isEmpty() -> emptyList() // No matches found
            matches.size == 1 -> {
                // Single match - mark it
                val updated = matches[0].copy(isPurchased = true)
                shoppingItemDao.updateItem(updated)
                emptyList()
            }
            else -> {
                // Multiple matches - return them for user to choose
                matches.map { it.toShoppingItem() }
            }
        }
    }
    
    /**
     * Marks an item as purchased by ID.
     * 
     * @param id Item ID to mark as purchased
     */
    suspend fun markItemPurchasedById(id: Long) {
        val item = shoppingItemDao.getItemById(id)
        if (item != null) {
            val updated = item.copy(isPurchased = true)
            shoppingItemDao.updateItem(updated)
        }
    }
    
    /**
     * Clears all purchased items from the shopping list.
     */
    suspend fun clearPurchased() {
        shoppingItemDao.deletePurchasedItems()
    }
    
    /**
     * Clears all items from the shopping list.
     * 
     * This is a destructive operation and should be confirmed by the user.
     */
    suspend fun clearAll() {
        shoppingItemDao.deleteAllItems()
    }
    
    /**
     * Saves a user category correction for a product.
     * 
     * This correction will be used for all future additions of this product.
     * 
     * @param productName Product name
     * @param category Category to assign
     */
    suspend fun saveUserCategoryCorrection(productName: String, category: ProductCategory) {
        val entity = ProductCategoryEntity(
            productName = productName.trim().lowercase(),
            category = category.name,
            isUserCorrection = true
        )
        productCategoryDao.insertOrUpdateCategory(entity)
    }
    
    /**
     * Converts ShoppingItemEntity to ShoppingItem domain model.
     */
    private fun ShoppingItemEntity.toShoppingItem(): ShoppingItem {
        return ShoppingItem(
            id = id,
            name = name,
            quantity = quantity,
            category = ProductCategory.fromString(category),
            isPurchased = isPurchased,
            createdAt = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(createdAt),
                ZoneId.systemDefault()
            )
        )
    }
}
