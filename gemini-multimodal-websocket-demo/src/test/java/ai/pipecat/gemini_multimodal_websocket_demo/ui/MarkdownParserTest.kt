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
        result.text shouldContain "Header 1"
    }
    
    test("parse bold text") {
        // Given markdown with bold text
        val markdown = "This is **bold** text"
        
        // When parsed
        val result = MarkdownParser.parseMarkdown(markdown)
        
        // Then result contains the text
        result.text shouldBe "This is bold text"
    }
    
    test("parse italic text") {
        // Given markdown with italic text
        val markdown = "This is *italic* text"
        
        // When parsed
        val result = MarkdownParser.parseMarkdown(markdown)
        
        // Then result contains the text
        result.text shouldBe "This is italic text"
    }
    
    test("parse inline code") {
        // Given markdown with inline code
        val markdown = "Use `code` here"
        
        // When parsed
        val result = MarkdownParser.parseMarkdown(markdown)
        
        // Then result contains the text
        result.text shouldBe "Use code here"
    }
    
    test("parse bullet list") {
        // Given markdown with bullet list
        val markdown = "- Item 1\n- Item 2"
        
        // When parsed
        val result = MarkdownParser.parseMarkdown(markdown)
        
        // Then result contains bullet points
        result.text shouldContain "• Item 1"
        result.text shouldContain "• Item 2"
    }
    
    test("parse numbered list") {
        // Given markdown with numbered list
        val markdown = "1. First item\n2. Second item"
        
        // When parsed
        val result = MarkdownParser.parseMarkdown(markdown)
        
        // Then result contains numbered items
        result.text shouldContain "1. First item"
        result.text shouldContain "2. Second item"
    }
    
    test("parse link") {
        // Given markdown with link
        val markdown = "Visit [Google](https://google.com)"
        
        // When parsed
        val result = MarkdownParser.parseMarkdown(markdown)
        
        // Then result contains link text
        result.text shouldContain "Visit Google"
    }
    
    test("parse code block") {
        // Given markdown with code block
        val markdown = "```\ncode line 1\ncode line 2\n```"
        
        // When parsed
        val result = MarkdownParser.parseMarkdown(markdown)
        
        // Then result contains code content
        result.text shouldContain "code line 1"
        result.text shouldContain "code line 2"
    }
    
    test("parse mixed formatting") {
        // Given markdown with multiple formatting types
        val markdown = "# Header\n\nThis is **bold** and *italic* text with `code`.\n\n- List item"
        
        // When parsed
        val result = MarkdownParser.parseMarkdown(markdown)
        
        // Then result contains all elements
        result.text shouldContain "Header"
        result.text shouldContain "bold"
        result.text shouldContain "italic"
        result.text shouldContain "code"
        result.text shouldContain "• List item"
    }
    
    test("handle empty string") {
        // Given empty markdown
        val markdown = ""
        
        // When parsed
        val result = MarkdownParser.parseMarkdown(markdown)
        
        // Then result is empty
        result.text shouldBe ""
    }
    
    test("handle plain text without markdown") {
        // Given plain text
        val markdown = "Just plain text without any formatting"
        
        // When parsed
        val result = MarkdownParser.parseMarkdown(markdown)
        
        // Then result is unchanged
        result.text shouldBe "Just plain text without any formatting"
    }
})