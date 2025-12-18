package ai.pipecat.gemini_multimodal_websocket_demo.data.dao

import androidx.room.*
import ai.pipecat.gemini_multimodal_websocket_demo.data.entities.ProductCategoryEntity

/**
 * Data Access Object for Product Category operations.
 */
@Dao
interface ProductCategoryDao {
    
    @Query("SELECT * FROM product_categories WHERE productName = :productName")
    suspend fun getCategoryForProduct(productName: String): ProductCategoryEntity?
    
    @Query("SELECT * FROM product_categories WHERE isUserCorrection = 1")
    suspend fun getAllUserCorrections(): List<ProductCategoryEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCategory(category: ProductCategoryEntity)
    
    @Query("DELETE FROM product_categories WHERE productName = :productName")
    suspend fun deleteCategory(productName: String)
}
