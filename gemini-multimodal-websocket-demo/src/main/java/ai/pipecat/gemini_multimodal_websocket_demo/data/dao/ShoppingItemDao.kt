package ai.pipecat.gemini_multimodal_websocket_demo.data.dao

import androidx.room.*
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.ShoppingItemEntity

/**
 * Data Access Object for Shopping Item operations.
 */
@Dao
interface ShoppingItemDao {
    
    @Query("SELECT * FROM shopping_items WHERE isPurchased = 0 ORDER BY category ASC, name ASC")
    suspend fun getAllActiveItems(): List<ShoppingItemEntity>
    
    @Query("SELECT * FROM shopping_items ORDER BY category ASC, name ASC")
    suspend fun getAllItems(): List<ShoppingItemEntity>
    
    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun getItemById(id: Long): ShoppingItemEntity?
    
    @Insert
    suspend fun insertItem(item: ShoppingItemEntity): Long
    
    @Update
    suspend fun updateItem(item: ShoppingItemEntity)
    
    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteItem(id: Long)
    
    @Query("DELETE FROM shopping_items WHERE isPurchased = 1")
    suspend fun deletePurchasedItems()
    
    @Query("DELETE FROM shopping_items")
    suspend fun deleteAllItems()
}
