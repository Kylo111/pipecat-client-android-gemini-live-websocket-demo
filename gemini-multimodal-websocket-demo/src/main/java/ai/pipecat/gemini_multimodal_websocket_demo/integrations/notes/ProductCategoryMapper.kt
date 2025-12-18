package ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes

/**
 * Product category for shopping list items.
 * 
 * Categories are displayed in the order specified by the `order` field.
 */
enum class ProductCategory(val displayName: String, val order: Int) {
    DAIRY("Nabiał", 1),
    BREAD("Pieczywo", 2),
    VEGETABLES("Warzywa", 3),
    FRUITS("Owoce", 4),
    MEAT("Mięso", 5),
    FISH("Ryby", 6),
    FROZEN("Mrożonki", 7),
    DRINKS("Napoje", 8),
    SWEETS("Słodycze", 9),
    HOUSEHOLD("Chemia", 10),
    OTHER("Inne", 99);
    
    companion object {
        fun fromString(value: String): ProductCategory {
            return values().find { it.name == value } ?: OTHER
        }
    }
}

/**
 * Maps product names to categories.
 * 
 * Provides a default dictionary of common products and their categories,
 * with fallback to OTHER for unknown products.
 */
object ProductCategoryMapper {
    
    /**
     * Default product-to-category mapping dictionary.
     * Keys are lowercase product names for case-insensitive matching.
     */
    private val defaultMapping = mapOf(
        // Dairy
        "mleko" to ProductCategory.DAIRY,
        "ser" to ProductCategory.DAIRY,
        "masło" to ProductCategory.DAIRY,
        "jogurt" to ProductCategory.DAIRY,
        "śmietana" to ProductCategory.DAIRY,
        "twaróg" to ProductCategory.DAIRY,
        "kefir" to ProductCategory.DAIRY,
        "maślanka" to ProductCategory.DAIRY,
        
        // Bread
        "chleb" to ProductCategory.BREAD,
        "bułka" to ProductCategory.BREAD,
        "bułki" to ProductCategory.BREAD,
        "bagietka" to ProductCategory.BREAD,
        "pączek" to ProductCategory.BREAD,
        "pączki" to ProductCategory.BREAD,
        "drożdżówka" to ProductCategory.BREAD,
        "croissant" to ProductCategory.BREAD,
        
        // Vegetables
        "pomidor" to ProductCategory.VEGETABLES,
        "pomidory" to ProductCategory.VEGETABLES,
        "ogórek" to ProductCategory.VEGETABLES,
        "ogórki" to ProductCategory.VEGETABLES,
        "sałata" to ProductCategory.VEGETABLES,
        "marchew" to ProductCategory.VEGETABLES,
        "marchewka" to ProductCategory.VEGETABLES,
        "ziemniak" to ProductCategory.VEGETABLES,
        "ziemniaki" to ProductCategory.VEGETABLES,
        "cebula" to ProductCategory.VEGETABLES,
        "czosnek" to ProductCategory.VEGETABLES,
        "papryka" to ProductCategory.VEGETABLES,
        "brokuł" to ProductCategory.VEGETABLES,
        "brokuły" to ProductCategory.VEGETABLES,
        "kalafior" to ProductCategory.VEGETABLES,
        "kapusta" to ProductCategory.VEGETABLES,
        "por" to ProductCategory.VEGETABLES,
        "seler" to ProductCategory.VEGETABLES,
        "pietruszka" to ProductCategory.VEGETABLES,
        "szpinak" to ProductCategory.VEGETABLES,
        "bakłażan" to ProductCategory.VEGETABLES,
        "cukinia" to ProductCategory.VEGETABLES,
        
        // Fruits
        "jabłko" to ProductCategory.FRUITS,
        "jabłka" to ProductCategory.FRUITS,
        "banan" to ProductCategory.FRUITS,
        "banany" to ProductCategory.FRUITS,
        "pomarańcza" to ProductCategory.FRUITS,
        "pomarańcze" to ProductCategory.FRUITS,
        "mandarynka" to ProductCategory.FRUITS,
        "mandarynki" to ProductCategory.FRUITS,
        "gruszka" to ProductCategory.FRUITS,
        "gruszki" to ProductCategory.FRUITS,
        "truskawka" to ProductCategory.FRUITS,
        "truskawki" to ProductCategory.FRUITS,
        "malina" to ProductCategory.FRUITS,
        "maliny" to ProductCategory.FRUITS,
        "borówka" to ProductCategory.FRUITS,
        "borówki" to ProductCategory.FRUITS,
        "winogrono" to ProductCategory.FRUITS,
        "winogrona" to ProductCategory.FRUITS,
        "arbuz" to ProductCategory.FRUITS,
        "melon" to ProductCategory.FRUITS,
        "kiwi" to ProductCategory.FRUITS,
        "ananas" to ProductCategory.FRUITS,
        
        // Meat
        "kurczak" to ProductCategory.MEAT,
        "pierś" to ProductCategory.MEAT,
        "piersi" to ProductCategory.MEAT,
        "udko" to ProductCategory.MEAT,
        "udka" to ProductCategory.MEAT,
        "wołowina" to ProductCategory.MEAT,
        "wieprzowina" to ProductCategory.MEAT,
        "schab" to ProductCategory.MEAT,
        "kiełbasa" to ProductCategory.MEAT,
        "parówka" to ProductCategory.MEAT,
        "parówki" to ProductCategory.MEAT,
        "szynka" to ProductCategory.MEAT,
        "boczek" to ProductCategory.MEAT,
        "mięso" to ProductCategory.MEAT,
        
        // Fish
        "ryba" to ProductCategory.FISH,
        "łosoś" to ProductCategory.FISH,
        "dorsz" to ProductCategory.FISH,
        "tuńczyk" to ProductCategory.FISH,
        "śledź" to ProductCategory.FISH,
        "pstrąg" to ProductCategory.FISH,
        "krewetka" to ProductCategory.FISH,
        "krewetki" to ProductCategory.FISH,
        
        // Frozen
        "lody" to ProductCategory.FROZEN,
        "pizza" to ProductCategory.FROZEN,
        "frytki" to ProductCategory.FROZEN,
        "mrożonki" to ProductCategory.FROZEN,
        
        // Drinks
        "woda" to ProductCategory.DRINKS,
        "sok" to ProductCategory.DRINKS,
        "kawa" to ProductCategory.DRINKS,
        "herbata" to ProductCategory.DRINKS,
        "piwo" to ProductCategory.DRINKS,
        "wino" to ProductCategory.DRINKS,
        "cola" to ProductCategory.DRINKS,
        "pepsi" to ProductCategory.DRINKS,
        "sprite" to ProductCategory.DRINKS,
        "napój" to ProductCategory.DRINKS,
        
        // Sweets
        "czekolada" to ProductCategory.SWEETS,
        "cukierek" to ProductCategory.SWEETS,
        "cukierki" to ProductCategory.SWEETS,
        "ciastko" to ProductCategory.SWEETS,
        "ciastka" to ProductCategory.SWEETS,
        "ciasto" to ProductCategory.SWEETS,
        "lody" to ProductCategory.SWEETS,
        "batonik" to ProductCategory.SWEETS,
        "wafel" to ProductCategory.SWEETS,
        
        // Household
        "mydło" to ProductCategory.HOUSEHOLD,
        "szampon" to ProductCategory.HOUSEHOLD,
        "pasta" to ProductCategory.HOUSEHOLD,
        "proszek" to ProductCategory.HOUSEHOLD,
        "płyn" to ProductCategory.HOUSEHOLD,
        "papier" to ProductCategory.HOUSEHOLD,
        "ręcznik" to ProductCategory.HOUSEHOLD,
        "chusteczka" to ProductCategory.HOUSEHOLD,
        "chusteczki" to ProductCategory.HOUSEHOLD,
        "detergent" to ProductCategory.HOUSEHOLD,
        "środek" to ProductCategory.HOUSEHOLD
    )
    
    /**
     * Gets the category for a product name.
     * 
     * Performs case-insensitive lookup in the default dictionary.
     * Returns OTHER if product is not found.
     * 
     * @param name Product name to categorize
     * @return ProductCategory for this product
     */
    fun getCategoryForProduct(name: String): ProductCategory {
        val normalizedName = name.trim().lowercase()
        return defaultMapping[normalizedName] ?: ProductCategory.OTHER
    }
}
