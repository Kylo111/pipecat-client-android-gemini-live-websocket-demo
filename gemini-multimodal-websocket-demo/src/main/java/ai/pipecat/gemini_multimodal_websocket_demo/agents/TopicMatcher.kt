package ai.pipecat.gemini_multimodal_websocket_demo.agents

/**
 * Semantic topic matching for deduplication.
 * 
 * Uses multiple strategies:
 * 1. Exact match (normalized)
 * 2. Synonym matching (hardcoded common synonyms)
 * 3. Jaccard similarity for overlap computation
 */
class TopicMatcher {
    
    companion object {
        // Common synonyms for deduplication
        // Maps normalized topic to list of synonyms
        val SYNONYMS = mapOf(
            "euro zone" to listOf("strefa euro", "eurozone", "euroland", "euro area"),
            "european union" to listOf("unia europejska", "eu", "ue"),
            "poland" to listOf("polska", "polish", "pl"),
            "united states" to listOf("usa", "us", "america", "stany zjednoczone"),
            "united kingdom" to listOf("uk", "britain", "wielka brytania", "great britain"),
            "artificial intelligence" to listOf("ai", "sztuczna inteligencja", "machine learning", "ml"),
            "cryptocurrency" to listOf("crypto", "kryptowaluta", "bitcoin", "blockchain"),
            "climate change" to listOf("global warming", "zmiana klimatu", "climate crisis"),
            "economy" to listOf("ekonomia", "gospodarka", "economic"),
            "politics" to listOf("polityka", "political", "government", "rząd"),
            "technology" to listOf("tech", "technologia", "it"),
            "health" to listOf("zdrowie", "healthcare", "medical", "medycyna"),
            "education" to listOf("edukacja", "szkolnictwo", "learning", "nauka"),
            "environment" to listOf("środowisko", "environmental", "ecology", "ekologia"),
            "energy" to listOf("energia", "power", "renewable", "odnawialna"),
            "finance" to listOf("finanse", "financial", "banking", "bankowość"),
            "security" to listOf("bezpieczeństwo", "safety", "cybersecurity"),
            "research" to listOf("badania", "study", "investigation", "analiza"),
            "report" to listOf("raport", "analysis", "summary", "podsumowanie"),
            "note" to listOf("notatka", "notes", "memo", "zapiski")
        )
        
        // Common stopwords to filter out when extracting topics
        val STOPWORDS = setOf(
            // English
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "about", "as", "into", "through", "during",
            "before", "after", "above", "below", "between", "under", "again", "further",
            "then", "once", "here", "there", "when", "where", "why", "how", "all",
            "both", "each", "few", "more", "most", "other", "some", "such", "no",
            "nor", "not", "only", "own", "same", "so", "than", "too", "very",
            "can", "will", "just", "should", "now", "is", "are", "was", "were",
            "be", "been", "being", "have", "has", "had", "do", "does", "did",
            "make", "made", "get", "got", "give", "gave", "take", "took",
            // Polish
            "i", "w", "z", "na", "do", "o", "po", "od", "dla", "przez",
            "przy", "ze", "za", "nad", "pod", "przed", "bez", "u", "co",
            "jak", "że", "się", "nie", "to", "jest", "są", "był", "była",
            "było", "były", "będzie", "będą", "może", "można", "trzeba",
            "już", "jeszcze", "tylko", "także", "również", "albo", "lub",
            "ani", "czy", "jeśli", "gdyby", "kiedy", "gdzie", "dlaczego",
            "który", "która", "które", "którzy", "jakie", "jaki", "jaka"
        )
    }
    
    /**
     * Normalize topic for comparison.
     * Lowercase, trim, remove punctuation.
     * 
     * @param topic Raw topic string
     * @return Normalized topic string
     */
    fun normalize(topic: String): String {
        return topic
            .lowercase()
            .trim()
            .replace(Regex("[^a-z0-9ąćęłńóśźżĄĆĘŁŃÓŚŹŻ\\s]"), "") // Remove punctuation, keep Polish chars
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()
    }
    
    /**
     * Check if two topics are semantically similar.
     * Uses exact match after normalization and synonym matching.
     * 
     * @param topic1 First topic
     * @param topic2 Second topic
     * @return True if topics are similar
     */
    fun areSimilar(topic1: String, topic2: String): Boolean {
        val normalized1 = normalize(topic1)
        val normalized2 = normalize(topic2)
        
        // Exact match after normalization
        if (normalized1 == normalized2) {
            return true
        }
        
        // Check if either topic is a synonym of the other
        return isSynonym(normalized1, normalized2)
    }
    
    /**
     * Check if two normalized topics are synonyms.
     * 
     * @param normalized1 First normalized topic
     * @param normalized2 Second normalized topic
     * @return True if topics are synonyms
     */
    private fun isSynonym(normalized1: String, normalized2: String): Boolean {
        // Check if normalized1 is in the synonym list of normalized2
        for ((key, synonyms) in SYNONYMS) {
            val allVariants = listOf(key) + synonyms
            if (allVariants.contains(normalized1) && allVariants.contains(normalized2)) {
                return true
            }
        }
        return false
    }
    
    /**
     * Compute topic overlap between two topic lists.
     * Uses Jaccard similarity with synonym expansion.
     * 
     * @param topics1 First list of topics
     * @param topics2 Second list of topics
     * @return Overlap percentage (0.0 to 1.0)
     */
    fun computeOverlap(topics1: List<String>, topics2: List<String>): Float {
        if (topics1.isEmpty() && topics2.isEmpty()) {
            return 1.0f // Both empty = perfect overlap
        }
        if (topics1.isEmpty() || topics2.isEmpty()) {
            return 0.0f // One empty = no overlap
        }
        
        // Normalize all topics
        val normalized1 = topics1.map { normalize(it) }.toSet()
        val normalized2 = topics2.map { normalize(it) }.toSet()
        
        // Expand with synonyms
        val expanded1 = expandWithSynonyms(normalized1)
        val expanded2 = expandWithSynonyms(normalized2)
        
        // Compute Jaccard similarity: |intersection| / |union|
        val intersection = expanded1.intersect(expanded2)
        val union = expanded1.union(expanded2)
        
        return if (union.isEmpty()) {
            0.0f
        } else {
            intersection.size.toFloat() / union.size.toFloat()
        }
    }
    
    /**
     * Expand a set of topics with their synonyms.
     * 
     * @param topics Set of normalized topics
     * @return Expanded set including synonyms
     */
    private fun expandWithSynonyms(topics: Set<String>): Set<String> {
        val expanded = mutableSetOf<String>()
        expanded.addAll(topics)
        
        for (topic in topics) {
            // Find all synonym groups that contain this topic
            for ((key, synonyms) in SYNONYMS) {
                val allVariants = listOf(key) + synonyms
                if (allVariants.contains(topic)) {
                    // Add all variants from this synonym group
                    expanded.addAll(allVariants)
                }
            }
        }
        
        return expanded
    }
    
    /**
     * Extract topics from task description.
     * Uses simple NLP: split by delimiters, filter stopwords, normalize.
     * 
     * @param taskDescription Task description text
     * @return List of normalized topics
     */
    fun extractTopics(taskDescription: String): List<String> {
        // Split by common delimiters
        val words = taskDescription
            .split(Regex("[,;.!?\\n\\r]+"))
            .flatMap { it.split(Regex("\\s+")) }
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
            .filter { it !in STOPWORDS }
            .filter { it.length >= 3 } // Filter out very short words
        
        // Remove duplicates while preserving order
        return words.distinct()
    }
}
