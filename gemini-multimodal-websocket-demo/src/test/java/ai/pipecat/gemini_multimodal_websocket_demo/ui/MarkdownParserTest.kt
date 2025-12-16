package ai.pipecat.gemini_multimodal_websocket_demo.ui

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Unit tests for MarkdownParser functionality.
 * 
 * Tests specific examples of markdown parsing to ensure correct behavior
 * for headers, bold, italic, code, lists, and links.
 * 
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5
 */
class MarkdownParserTest : FunSpec({
    
    test("parse simple header") {
        // Given markdown with header
        val markdown = "# Header 1"
        
        // When parsed
        val result = MarkdownParser.parseMarkdown(markdown)
        
        // Then result contains the header text
        val textBlock = result.blocks.firstOrNull() as? MarkdownParser.Block.TextBlock
        textBlock?.annotatedString?.text shouldContain "Header 1"
    }
    
    test("parse bold text") {
        // Given markdown with bold text
        val markdown = "This is **bold** text"
        
        // When parsed
        val result = MarkdownParser.parseMarkdown(markdown)
        
        // Then result contains the text
        val textBlock = result.blocks.firstOrNull() as? MarkdownParser.Block.TextBlock
        textBlock?.annotatedString?.text shouldBe "This is bold text"
    }
    
    test("parse table") {
        // Given markdown with table
        val markdown = "| Col1 | Col2 |\n|------|------|\n| A | B |"
        
        // When parsed
        val result = MarkdownParser.parseMarkdown(markdown)
        
        // Then result contains table block
        val tableBlock = result.blocks.firstOrNull() as? MarkdownParser.Block.TableBlock
        tableBlock?.rows?.size shouldBe 2 // Header + 1 data row
    }
})