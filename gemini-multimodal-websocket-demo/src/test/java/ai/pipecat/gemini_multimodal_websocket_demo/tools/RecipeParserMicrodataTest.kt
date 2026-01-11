package ai.pipecat.gemini_multimodal_websocket_demo.tools

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.json.JSONObject

class RecipeParserMicrodataTest {

    @Test
    fun `test recipe parsing with microdata`() {
        val sampleHtml = """
            <!doctype html>
            <html>
            <body>
            <div class="post-con">
                <article itemscope="itemscope" itemtype="https://schema.org/Recipe">
                    <h1 itemprop="name">Sałatka jarzynowa</h1>
                    <meta itemprop="description" content="Pyszna sałatka.">
                    <meta itemprop="prepTime" content="PT20M">
                    <meta itemprop="cookTime" content="PT30M">
                    <meta itemprop="totalTime" content="PT50M">
                    <meta itemprop="image" content="https://example.com/salad.jpg">
                    
                    <div id="recipeIngredients">
                        <ul>
                            <li><span itemprop="recipeIngredient">3 ziemniaki</span></li>
                            <li><span itemprop="recipeIngredient">4 marchewki</span></li>
                        </ul>
                    </div>
                    
                    <div class="steps">
                        <div class="step" itemprop="recipeInstructions" itemscope itemtype="https://schema.org/HowToStep">
                            <div class="step-text" itemprop="text">
                                <p>Ugotuj warzywa.</p>
                            </div>
                        </div>
                        <div class="step" itemprop="recipeInstructions" itemscope itemtype="https://schema.org/HowToStep">
                            <div class="step-text" itemprop="text">
                                <p>Pokrój w kostkę.</p>
                            </div>
                        </div>
                         <!-- Sometimes instructions are just text inside the div without HowToStep -->
                        <div itemprop="recipeInstructions">
                            Wymieszaj z majonezem.
                        </div>
                    </div>
                </article>
            </div>
            </body>
            </html>
        """.trimIndent()
        
        val url = "https://aniagotuje.pl/przepis/salatka-jarzynowa"
        val result = RecipeParser.parseHtml(sampleHtml, url)
        
        println("Parser Result: $result")
        
        assertFalse(result.startsWith("Error"), "Result should not be an error")
        
        val json = JSONObject(result)
        assertEquals("Sałatka jarzynowa", json.getString("name"))
        assertEquals("Pyszna sałatka.", json.getString("description"))
        assertEquals("PT20M", json.getString("prepTime"))
        assertEquals("https://example.com/salad.jpg", json.getString("image"))
        
        val ingredients = json.getJSONArray("ingredients")
        assertEquals(2, ingredients.length())
        assertEquals("3 ziemniaki", ingredients.getString(0))
        
        val instructions = json.getJSONArray("instructions")
        assertEquals(3, instructions.length())
        assertEquals("Ugotuj warzywa.", instructions.getString(0))
        assertEquals("Wymieszaj z majonezem.", instructions.getString(2))
    }
}
