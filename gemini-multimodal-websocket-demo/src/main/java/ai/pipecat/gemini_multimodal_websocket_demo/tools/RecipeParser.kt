package ai.pipecat.gemini_multimodal_websocket_demo.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Parses recipes from aniagotuje.pl using Schema.org/Recipe JSON-LD or Microdata.
 * Logic extracted from ToolExecutor for testability.
 */
object RecipeParser {

    /**
     * Parse recipe from a live URL.
     */
    suspend fun parse(url: String): String = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(15000)
                .get()

            parseHtml(doc.outerHtml(), url)
        } catch (e: Exception) {
            "Error fetching recipe: ${e.message}"
        }
    }

    /**
     * Parse recipe from HTML content (for testing).
     */
    fun parseHtml(html: String, urlSource: String): String {
        try {
            val doc = Jsoup.parse(html)

            // Try JSON-LD first
            var recipeJson = extractJsonLd(doc)

            // If JSON-LD failed or wasn't found, try Microdata
            if (recipeJson == null) {
                recipeJson = extractMicrodata(doc)
            }

            if (recipeJson == null) {
                return "Error: No recipe data (JSON-LD or Microdata) found on this page."
            }

            // Normalize Data
            val recipe = normalizeRecipe(recipeJson, urlSource)
            return recipe.toString()

        } catch (e: Exception) {
            return "Error parsing recipe: ${e.message}"
        }
    }

    private fun extractJsonLd(doc: Document): JSONObject? {
        val scriptElements = doc.select("script[type=application/ld+json]")
        for (element in scriptElements) {
            try {
                val jsonContent = element.data()
                val jsonTokener = org.json.JSONTokener(jsonContent)
                val root = jsonTokener.nextValue()

                if (root is JSONObject) {
                    if (isRecipeType(root.optString("@type"))) return root
                    if (root.has("@graph")) {
                        val graph = root.getJSONArray("@graph")
                        for (i in 0 until graph.length()) {
                            val item = graph.getJSONObject(i)
                            if (isRecipeType(item.optString("@type"))) return item
                        }
                    }
                } else if (root is JSONArray) {
                    for (i in 0 until root.length()) {
                        val item = root.getJSONObject(i)
                        if (isRecipeType(item.optString("@type"))) return item
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
        return null
    }

    private fun isRecipeType(type: String): Boolean {
        // Handle "Recipe" or "schema.org/Recipe" or array types
        return type.contains("Recipe", ignoreCase = true)
    }

    private fun extractMicrodata(doc: Document): JSONObject? {
        // Find the element with itemtype="http://schema.org/Recipe" or https
        val recipeElement = doc.select("[itemtype~=https?://schema.org/Recipe]").first() ?: return null

        val json = JSONObject()

        // Name
        json.put("name", getMetaOrText(recipeElement, "name"))

        // Description
        json.put("description", getMetaOrText(recipeElement, "description"))

        // Times
        json.put("prepTime", getMetaContent(recipeElement, "prepTime"))
        json.put("cookTime", getMetaContent(recipeElement, "cookTime"))
        json.put("totalTime", getMetaContent(recipeElement, "totalTime"))

        // Ingredients
        val ingredients = JSONArray()
        recipeElement.select("[itemprop=recipeIngredient]").forEach {
            ingredients.put(it.text().trim())
        }
        json.put("recipeIngredient", ingredients)

        // Instructions
        val instructions = JSONArray()
        val instructionElements = recipeElement.select("[itemprop=recipeInstructions]")
        for (el in instructionElements) {
            // Check if it's a structural element (HowToStep) or just text
            val textEl = el.select("[itemprop=text]")
            if (textEl.isNotEmpty()) {
                instructions.put(textEl.text().trim())
            } else {
                 // Sometimes the element itself contains the text
                 instructions.put(el.text().trim())
            }
        }
        json.put("recipeInstructions", instructions)

        // Image
        var imageUrl = getMetaContent(recipeElement, "image")
        if (imageUrl.isEmpty()) {
             // Try img src
             val img = recipeElement.selectFirst("img[itemprop=image]")
             if (img != null) {
                 imageUrl = img.absUrl("src") // Use absUrl to get absolute URL directly from Jsoup
             } else {
                 // Check if the recipeElement itself is an image (unlikely but possible)
                 if (recipeElement.tagName() == "img" && recipeElement.hasAttr("src")) {
                     imageUrl = recipeElement.absUrl("src")
                 }
             }
        }
        if (imageUrl.isNotEmpty()) {
            json.put("image", imageUrl)
        }

        return json
    }

    private fun getMetaOrText(parent: Element, itemprop: String): String {
        val el = parent.selectFirst("[itemprop=$itemprop]") ?: return ""
        return if (el.tagName() == "meta") {
            el.attr("content")
        } else {
            el.text().trim()
        }
    }

    private fun getMetaContent(parent: Element, itemprop: String): String {
        val el = parent.selectFirst("[itemprop=$itemprop]") ?: return ""
        return el.attr("content")
    }

    private fun normalizeRecipe(recipeJson: JSONObject, urlSource: String): JSONObject {
        val result = JSONObject()
        result.put("name", recipeJson.optString("name", "Unknown Recipe").trim())
        result.put("description", recipeJson.optString("description", ""))
        result.put("prepTime", recipeJson.optString("prepTime", ""))
        result.put("cookTime", recipeJson.optString("cookTime", ""))
        result.put("totalTime", recipeJson.optString("totalTime", ""))
        result.put("url", urlSource)

        // Ingredients
        val ingredients = JSONArray()
        val ingVal = recipeJson.opt("recipeIngredient")
        if (ingVal is JSONArray) {
            for (i in 0 until ingVal.length()) ingredients.put(ingVal.getString(i).trim())
        } else if (ingVal is String) {
            ingredients.put(ingVal.trim())
        }
        result.put("ingredients", ingredients)

        // Instructions
        val instructions = JSONArray()
        val instVal = recipeJson.opt("recipeInstructions")
        processInstructions(instVal, instructions)
        result.put("instructions", instructions)

        // Rating
        val ratingObj = recipeJson.optJSONObject("aggregateRating")
        if (ratingObj != null) {
            result.put("ratingValue", ratingObj.optString("ratingValue", ""))
            result.put("ratingCount", ratingObj.optString("ratingCount", ""))
        }

        // Image
        var imageUrl = ""
        val imgVal = recipeJson.opt("image")
        if (imgVal is String) imageUrl = imgVal
        else if (imgVal is JSONArray && imgVal.length() > 0) {
            val first = imgVal.get(0)
            if (first is String) imageUrl = first
            else if (first is JSONObject) imageUrl = first.optString("url", "")
        } else if (imgVal is JSONObject) {
            imageUrl = imgVal.optString("url", "")
        }

        // Ensure absolute URL if parsing from Microdata resulted in relative path (fallback)
        if (imageUrl.isNotEmpty() && !imageUrl.startsWith("http")) {
             // Simple naive join. Jsoup.absUrl should handle this but this is a safety net
             val baseUrl = if (urlSource.endsWith("/")) urlSource.dropLast(1) else urlSource
             // If urlSource is "https://aniagotuje.pl/przepis/x", base domain is "https://aniagotuje.pl"
             // But usually relative paths start with /.
             if (imageUrl.startsWith("/")) {
                 val uri = java.net.URI(urlSource)
                 val domain = uri.scheme + "://" + uri.host
                 imageUrl = domain + imageUrl
             } else {
                 // Relative to current path? uncommon for root assets usually
                 imageUrl = "$baseUrl/$imageUrl"
             }
        }
        
        result.put("image", imageUrl)

        return result
    }

    private fun processInstructions(instVal: Any?, output: JSONArray) {
        if (instVal == null) return

        if (instVal is JSONArray) {
            for (i in 0 until instVal.length()) {
                val item = instVal.get(i)
                processInstructionItem(item, output)
            }
        } else {
            processInstructionItem(instVal, output)
        }
    }

    private fun processInstructionItem(item: Any, output: JSONArray) {
        when (item) {
            is String -> {
                if (item.trim().isNotEmpty()) output.put(item.trim())
            }
            is JSONObject -> {
                val type = item.optString("@type", "")
                if (type.contains("HowToSection", ignoreCase = true)) {
                    val name = item.optString("name", "")
                    if (name.isNotEmpty()) output.put("### $name") // Optional heading for section
                    val elements = item.opt("itemListElement")
                    processInstructions(elements, output)
                } else if (type.contains("HowToStep", ignoreCase = true) || item.has("text")) {
                    val text = item.optString("text", "")
                    if (text.isNotEmpty()) output.put(text.trim())
                }
            }
            is JSONArray -> {
                for (i in 0 until item.length()) {
                    processInstructionItem(item.get(i), output)
                }
            }
        }
    }
}
