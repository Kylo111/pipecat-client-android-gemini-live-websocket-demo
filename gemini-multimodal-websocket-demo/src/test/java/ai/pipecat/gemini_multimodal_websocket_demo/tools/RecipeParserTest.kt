package ai.pipecat.gemini_multimodal_websocket_demo.tools

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.json.JSONObject

class RecipeParserTest {

    @Test
    fun `test recipe parsing with sample data`() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@type": "Recipe",
                  "name": "Test Salad",
                  "description": "A test recipe",
                  "recipeIngredient": [
                    "1 carrot",
                    "2 potatoes"
                  ],
                  "recipeInstructions": [
                    {
                      "@type": "HowToStep",
                      "text": "Peel vegetables"
                    },
                    {
                      "@type": "HowToStep",
                      "text": "Cook vegetables"
                    }
                  ],
                  "image": [
                    "https://example.com/image.jpg"
                  ]
                }
                </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()
        
        val url = "https://example.com/recipe"
        val result = RecipeParser.parseHtml(sampleHtml, url)
        
        assertFalse(result.startsWith("Error"), "Result should not be an error")
        
        val json = JSONObject(result)
        assertEquals("Test Salad", json.getString("name"))
        val ingredients = json.getJSONArray("ingredients")
        assertEquals(2, ingredients.length())
        assertEquals("1 carrot", ingredients.getString(0))
        
        val instructions = json.getJSONArray("instructions")
        assertEquals(2, instructions.length())
        assertEquals("Peel vegetables", instructions.getString(0))
        
        assertEquals("https://example.com/image.jpg", json.getString("image"))
    }
}
