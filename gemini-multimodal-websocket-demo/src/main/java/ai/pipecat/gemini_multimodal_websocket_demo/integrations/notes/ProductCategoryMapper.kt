package ai.pipecat.gemini_multimodal_websocket_demo.integrations.notes

/**
 * Product category for shopping list items.
 * 
 * Categories are displayed in the order specified by the `order` field.
 */
enum class ProductCategory(val displayName: String, val order: Int) {
    FRUIT_VEG("Owoce i Warzywa", 1),
    BREAD("Pieczywo", 2),
    DAIRY("Nabiał", 3),
    MEAT("Mięso i Wędliny", 4),
    FISH("Ryby i Owoce Morza", 5),
    DRY_GOODS("Produkty Sypkie", 6),
    PRESERVES("Przetwory i Sosy", 7),
    NIGHTSHADE("Bakalie i Przyprawy", 8), // Replaced generic stuff
    DRINKS("Napoje", 9),
    SWEETS("Słodycze i Przekąski", 10),
    FROZEN("Mrożonki", 11),
    HOUSEHOLD("Chemia i Kosmetyki", 12),
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
        "jajka" to ProductCategory.DAIRY,
        "jajko" to ProductCategory.DAIRY,
        "margaryna" to ProductCategory.DAIRY,
        "serek" to ProductCategory.DAIRY,
        "śmietanka" to ProductCategory.DAIRY,
        "jogurtowy" to ProductCategory.DAIRY,
        "mleczny" to ProductCategory.DAIRY,
        "serowy" to ProductCategory.DAIRY,
        "homogenizowany" to ProductCategory.DAIRY,
        
        // Bread
        "chleb" to ProductCategory.BREAD,
        "bułka" to ProductCategory.BREAD,
        "bułki" to ProductCategory.BREAD,
        "bagietka" to ProductCategory.BREAD,
        "pączek" to ProductCategory.BREAD,
        "pączki" to ProductCategory.BREAD,
        "drożdżówka" to ProductCategory.BREAD,
        "croissant" to ProductCategory.BREAD,
        "chałka" to ProductCategory.BREAD,
        "tortilla" to ProductCategory.BREAD,
        "precel" to ProductCategory.BREAD,
        "pieczywo" to ProductCategory.BREAD,
        "żytnie" to ProductCategory.BREAD,
        "żytni" to ProductCategory.BREAD,
        "razowe" to ProductCategory.BREAD,
        "razowy" to ProductCategory.BREAD,
        "orkiszowe" to ProductCategory.BREAD,
        "bułeczki" to ProductCategory.BREAD,
        
        // Vegetables & Fruits
        "pomidor" to ProductCategory.FRUIT_VEG,
        "pomidory" to ProductCategory.FRUIT_VEG,
        "ogórek" to ProductCategory.FRUIT_VEG,
        "ogórki" to ProductCategory.FRUIT_VEG,
        "sałata" to ProductCategory.FRUIT_VEG,
        "marchew" to ProductCategory.FRUIT_VEG,
        "marchewka" to ProductCategory.FRUIT_VEG,
        "ziemniak" to ProductCategory.FRUIT_VEG,
        "ziemniaki" to ProductCategory.FRUIT_VEG,
        "cebula" to ProductCategory.FRUIT_VEG,
        "czosnek" to ProductCategory.FRUIT_VEG,
        "papryka" to ProductCategory.FRUIT_VEG,
        "brokuł" to ProductCategory.FRUIT_VEG,
        "brokuły" to ProductCategory.FRUIT_VEG,
        "kalafior" to ProductCategory.FRUIT_VEG,
        "kapusta" to ProductCategory.FRUIT_VEG,
        "por" to ProductCategory.FRUIT_VEG,
        "seler" to ProductCategory.FRUIT_VEG,
        "pietruszka" to ProductCategory.FRUIT_VEG,
        "szpinak" to ProductCategory.FRUIT_VEG,
        "bakłażan" to ProductCategory.FRUIT_VEG,
        "cukinia" to ProductCategory.FRUIT_VEG,
        "rzodkiewka" to ProductCategory.FRUIT_VEG,
        "burak" to ProductCategory.FRUIT_VEG,
        "pieczarki" to ProductCategory.FRUIT_VEG,
        "dynia" to ProductCategory.FRUIT_VEG,
        "fasola" to ProductCategory.FRUIT_VEG,
        "kukurydza" to ProductCategory.FRUIT_VEG,
        "jabłko" to ProductCategory.FRUIT_VEG,
        "jabłka" to ProductCategory.FRUIT_VEG,
        "banan" to ProductCategory.FRUIT_VEG,
        "banany" to ProductCategory.FRUIT_VEG,
        "pomarańcza" to ProductCategory.FRUIT_VEG,
        "pomarańcze" to ProductCategory.FRUIT_VEG,
        "mandarynka" to ProductCategory.FRUIT_VEG,
        "mandarynki" to ProductCategory.FRUIT_VEG,
        "gruszka" to ProductCategory.FRUIT_VEG,
        "gruszki" to ProductCategory.FRUIT_VEG,
        "truskawka" to ProductCategory.FRUIT_VEG,
        "truskawki" to ProductCategory.FRUIT_VEG,
        "malina" to ProductCategory.FRUIT_VEG,
        "maliny" to ProductCategory.FRUIT_VEG,
        "borówka" to ProductCategory.FRUIT_VEG,
        "borówki" to ProductCategory.FRUIT_VEG,
        "winogrono" to ProductCategory.FRUIT_VEG,
        "winogrona" to ProductCategory.FRUIT_VEG,
        "arbuz" to ProductCategory.FRUIT_VEG,
        "melon" to ProductCategory.FRUIT_VEG,
        "kiwi" to ProductCategory.FRUIT_VEG,
        "ananas" to ProductCategory.FRUIT_VEG,
        "cytryna" to ProductCategory.FRUIT_VEG,
        "limonka" to ProductCategory.FRUIT_VEG,
        "awokado" to ProductCategory.FRUIT_VEG,
        "brzoskwinia" to ProductCategory.FRUIT_VEG,
        "śliwka" to ProductCategory.FRUIT_VEG,
        
        // Meat & Cold Cuts
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
        "kabanosy" to ProductCategory.MEAT,
        "mielone" to ProductCategory.MEAT,
        "indyk" to ProductCategory.MEAT,
        "wędlina" to ProductCategory.MEAT,
        "salami" to ProductCategory.MEAT,
        "pasztet" to ProductCategory.MEAT,
        
        // Fish
        "ryba" to ProductCategory.FISH,
        "łosoś" to ProductCategory.FISH,
        "dorsz" to ProductCategory.FISH,
        "tuńczyk" to ProductCategory.FISH,
        "śledź" to ProductCategory.FISH,
        "pstrąg" to ProductCategory.FISH,
        "krewetka" to ProductCategory.FISH,
        "krewetki" to ProductCategory.FISH,
        "paluszki rybne" to ProductCategory.FISH,
        "kalmary" to ProductCategory.FISH,
        
        // Dry Goods (Produkty sypkie)
        "mąka" to ProductCategory.DRY_GOODS,
        "ryż" to ProductCategory.DRY_GOODS,
        "kasza" to ProductCategory.DRY_GOODS,
        "makaron" to ProductCategory.DRY_GOODS,
        "płatki" to ProductCategory.DRY_GOODS,
        "musli" to ProductCategory.DRY_GOODS,
        "owsianka" to ProductCategory.DRY_GOODS,
        "cukier" to ProductCategory.DRY_GOODS,
        "sól" to ProductCategory.DRY_GOODS,
        
        // Preserves & Sauces (Przetwory i Sosy)
        "olej" to ProductCategory.PRESERVES,
        "oliwa" to ProductCategory.PRESERVES,
        "ocet" to ProductCategory.PRESERVES,
        "ketchup" to ProductCategory.PRESERVES,
        "musztarda" to ProductCategory.PRESERVES,
        "majonez" to ProductCategory.PRESERVES,
        "dżem" to ProductCategory.PRESERVES,
        "miód" to ProductCategory.PRESERVES,
        "konfitura" to ProductCategory.PRESERVES,
        "przecier" to ProductCategory.PRESERVES,
        "sos" to ProductCategory.PRESERVES,
        "pomidory w puszce" to ProductCategory.PRESERVES,
        "kukurydza w puszce" to ProductCategory.PRESERVES,
        "groszek" to ProductCategory.PRESERVES,
        "fasolka" to ProductCategory.PRESERVES,
        
        // Spices & Nuts (Bakalie i Przyprawy)
        "przyprawa" to ProductCategory.NIGHTSHADE,
        "pieprz" to ProductCategory.NIGHTSHADE,
        "papryka słodka" to ProductCategory.NIGHTSHADE,
        "zioła" to ProductCategory.NIGHTSHADE,
        "orzechy" to ProductCategory.NIGHTSHADE,
        "rodzynki" to ProductCategory.NIGHTSHADE,
        "migdały" to ProductCategory.NIGHTSHADE,
        "ziarna" to ProductCategory.NIGHTSHADE,
        "pestki" to ProductCategory.NIGHTSHADE,
        "ziele angielskie" to ProductCategory.NIGHTSHADE,
        "liść laurowy" to ProductCategory.NIGHTSHADE,
        "liście laurowe" to ProductCategory.NIGHTSHADE,
        "majeranek" to ProductCategory.NIGHTSHADE,
        "tymianek" to ProductCategory.NIGHTSHADE,
        "bazylia" to ProductCategory.NIGHTSHADE,
        "oregano" to ProductCategory.NIGHTSHADE,
        "kurkuma" to ProductCategory.NIGHTSHADE,
        "cynamon" to ProductCategory.NIGHTSHADE,
        "imbir" to ProductCategory.NIGHTSHADE,
        "gałka muszkatołowa" to ProductCategory.NIGHTSHADE,
        "goździki" to ProductCategory.NIGHTSHADE,
        "kolendra" to ProductCategory.NIGHTSHADE,
        "kminek" to ProductCategory.NIGHTSHADE,
        "lubczyk" to ProductCategory.NIGHTSHADE,
        "czarnuszka" to ProductCategory.NIGHTSHADE,
        "sezam" to ProductCategory.NIGHTSHADE,
        "mak" to ProductCategory.NIGHTSHADE,
        
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
        "energetyk" to ProductCategory.DRINKS,
        "kakao" to ProductCategory.DRINKS,
        
        // Sweets & Snacks
        "czekolada" to ProductCategory.SWEETS,
        "cukierek" to ProductCategory.SWEETS,
        "cukierki" to ProductCategory.SWEETS,
        "ciastko" to ProductCategory.SWEETS,
        "ciastka" to ProductCategory.SWEETS,
        "ciasto" to ProductCategory.SWEETS,
        "batonik" to ProductCategory.SWEETS,
        "wafel" to ProductCategory.SWEETS,
        "chipsy" to ProductCategory.SWEETS,
        "paluszki" to ProductCategory.SWEETS,
        "żelki" to ProductCategory.SWEETS,
        "chrupki" to ProductCategory.SWEETS,
        "popcorn" to ProductCategory.SWEETS,
        
        // Frozen
        "lody" to ProductCategory.FROZEN,
        "pizza" to ProductCategory.FROZEN,
        "frytki" to ProductCategory.FROZEN,
        "mrożonki" to ProductCategory.FROZEN,
        "warzywa na patelnię" to ProductCategory.FROZEN,
        "pierogi" to ProductCategory.FROZEN,
        
        // Household & Beauty
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
        "środek" to ProductCategory.HOUSEHOLD,
        "gąbka" to ProductCategory.HOUSEHOLD,
        "worki" to ProductCategory.HOUSEHOLD,
        "szczoteczka" to ProductCategory.HOUSEHOLD,
        "żel pod prysznic" to ProductCategory.HOUSEHOLD,
        "dezodorant" to ProductCategory.HOUSEHOLD
    )
    
    /**
     * Gets the category for a product name.
     * 
     * Performs case-insensitive matching. Checks for exact matches first,
     * then falls back to checking if any known product name is contained 
     * within the given name.
     * 
     * @param name Product name to categorize
     * @return ProductCategory for this product
     */
    fun getCategoryForProduct(name: String): ProductCategory {
        val normalizedName = name.trim().lowercase()
        
        // 1. Try exact match
        defaultMapping[normalizedName]?.let { return it }
        
        // 2. Try partial match (e.g. "mleko owsiane" should match "mleko")
        // We sort by length descending to match the most specific product first
        val sortedProducts = defaultMapping.keys.sortedByDescending { it.length }
        for (product in sortedProducts) {
            if (normalizedName.contains(product)) {
                return defaultMapping[product]!!
            }
        }
        
        return ProductCategory.OTHER
    }
}
