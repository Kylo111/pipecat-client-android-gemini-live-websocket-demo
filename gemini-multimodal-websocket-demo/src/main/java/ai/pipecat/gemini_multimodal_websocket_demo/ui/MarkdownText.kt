package ai.pipecat.gemini_multimodal_websocket_demo.ui

import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

/**
 * Markdown parser that converts markdown text to AnnotatedString for Compose Text.
 * 
 * Supports:
 * - Headers (h1-h6) with different font sizes
 * - Bold (**text**) and italic (*text*)
 * - Inline code (`code`)
 * - Code blocks (```code```)
 * - Bullet lists (- item, * item)
 * - Numbered lists (1. item)
 * - Links ([text](url))
 * 
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5
 */
object MarkdownParser {
    
    /**
     * Parse markdown text into AnnotatedString with proper styling.
     */
    fun parseMarkdown(markdown: String): AnnotatedString {
        return buildAnnotatedString {
            val lines = markdown.lines()
            var i = 0
            
            while (i < lines.size) {
                val line = lines[i]
                
                when {
                    // Code blocks (```code```)
                    line.trim().startsWith("```") -> {
                        val codeBlockResult = parseCodeBlock(lines, i)
                        appendCodeBlock(codeBlockResult.first)
                        i = codeBlockResult.second
                    }
                    
                    // Headers (# ## ### #### ##### ######)
                    line.trim().startsWith("#") -> {
                        appendHeader(line)
                        if (i < lines.size - 1) append("\n")
                        i++
                    }
                    
                    // Bullet lists (- item, * item)
                    line.trim().startsWith("- ") || line.trim().startsWith("* ") -> {
                        appendBulletListItem(line)
                        if (i < lines.size - 1) append("\n")
                        i++
                    }
                    
                    // Numbered lists (1. item, 2. item, etc.)
                    line.trim().matches(Regex("^\\d+\\. .*")) -> {
                        appendNumberedListItem(line)
                        if (i < lines.size - 1) append("\n")
                        i++
                    }
                    
                    // Regular paragraph with inline formatting
                    line.isNotBlank() -> {
                        appendParagraphWithInlineFormatting(line)
                        if (i < lines.size - 1) append("\n")
                        i++
                    }
                    
                    // Empty line
                    else -> {
                        if (i < lines.size - 1) append("\n")
                        i++
                    }
                }
            }
        }
    }
    
    /**
     * Parse code block starting from current line index.
     * Returns the code content and the next line index to process.
     */
    private fun parseCodeBlock(lines: List<String>, startIndex: Int): Pair<String, Int> {
        val codeLines = mutableListOf<String>()
        var i = startIndex + 1 // Skip opening ```
        
        while (i < lines.size) {
            val line = lines[i]
            if (line.trim().startsWith("```")) {
                // Found closing ```
                return Pair(codeLines.joinToString("\n"), i + 1)
            }
            codeLines.add(line)
            i++
        }
        
        // No closing ``` found, treat as regular text
        return Pair(codeLines.joinToString("\n"), i)
    }
    
    /**
     * Append code block with monospace font and background styling.
     */
    private fun AnnotatedString.Builder.appendCodeBlock(code: String) {
        pushStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                background = Color(0xFFF5F5F5),
                color = Color(0xFF333333)
            )
        )
        append(code)
        pop()
        append("\n")
    }
    
    /**
     * Append header with appropriate font size based on level.
     */
    private fun AnnotatedString.Builder.appendHeader(line: String) {
        val trimmed = line.trim()
        val level = trimmed.takeWhile { it == '#' }.length
        val text = trimmed.drop(level).trim()
        
        val fontSize = when (level) {
            1 -> 24.sp
            2 -> 20.sp
            3 -> 18.sp
            4 -> 16.sp
            5 -> 14.sp
            6 -> 12.sp
            else -> 14.sp
        }
        
        pushStyle(
            SpanStyle(
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        )
        appendInlineFormatting(text)
        pop()
    }
    
    /**
     * Append bullet list item with proper indentation.
     */
    private fun AnnotatedString.Builder.appendBulletListItem(line: String) {
        val trimmed = line.trim()
        val text = trimmed.drop(2) // Remove "- " or "* "
        
        append("• ")
        appendInlineFormatting(text)
    }
    
    /**
     * Append numbered list item.
     */
    private fun AnnotatedString.Builder.appendNumberedListItem(line: String) {
        val trimmed = line.trim()
        val numberPart = trimmed.takeWhile { it.isDigit() || it == '.' }
        val text = trimmed.drop(numberPart.length).trim()
        
        append("$numberPart ")
        appendInlineFormatting(text)
    }
    
    /**
     * Append paragraph with inline formatting applied.
     */
    private fun AnnotatedString.Builder.appendParagraphWithInlineFormatting(line: String) {
        appendInlineFormatting(line.trim())
    }
    
    /**
     * Apply inline formatting (bold, italic, code, links) to text.
     */
    private fun AnnotatedString.Builder.appendInlineFormatting(text: String) {
        var i = 0
        val chars = text.toCharArray()
        
        while (i < chars.size) {
            when {
                // Links [text](url)
                i < chars.size - 3 && chars[i] == '[' -> {
                    val linkResult = parseLinkAt(text, i)
                    if (linkResult != null) {
                        appendLink(linkResult.first, linkResult.second)
                        i = linkResult.third
                    } else {
                        append(chars[i])
                        i++
                    }
                }
                
                // Bold **text**
                i < chars.size - 3 && chars[i] == '*' && chars[i + 1] == '*' -> {
                    val boldResult = parseBoldAt(text, i)
                    if (boldResult != null) {
                        appendBold(boldResult.first)
                        i = boldResult.second
                    } else {
                        append(chars[i])
                        i++
                    }
                }
                
                // Italic *text*
                i < chars.size - 2 && chars[i] == '*' -> {
                    val italicResult = parseItalicAt(text, i)
                    if (italicResult != null) {
                        appendItalic(italicResult.first)
                        i = italicResult.second
                    } else {
                        append(chars[i])
                        i++
                    }
                }
                
                // Inline code `code`
                i < chars.size - 2 && chars[i] == '`' -> {
                    val codeResult = parseInlineCodeAt(text, i)
                    if (codeResult != null) {
                        appendInlineCode(codeResult.first)
                        i = codeResult.second
                    } else {
                        append(chars[i])
                        i++
                    }
                }
                
                // Regular character
                else -> {
                    append(chars[i])
                    i++
                }
            }
        }
    }
    
    /**
     * Parse link at given position. Returns (text, url, nextIndex) or null if not a valid link.
     */
    private fun parseLinkAt(text: String, startIndex: Int): Triple<String, String, Int>? {
        if (startIndex >= text.length || text[startIndex] != '[') return null
        
        // Find closing ]
        var i = startIndex + 1
        while (i < text.length && text[i] != ']') i++
        if (i >= text.length) return null
        
        val linkText = text.substring(startIndex + 1, i)
        i++ // Skip ]
        
        // Check for (url)
        if (i >= text.length || text[i] != '(') return null
        i++ // Skip (
        
        val urlStart = i
        while (i < text.length && text[i] != ')') i++
        if (i >= text.length) return null
        
        val url = text.substring(urlStart, i)
        i++ // Skip )
        
        return Triple(linkText, url, i)
    }
    
    /**
     * Parse bold text at given position. Returns (text, nextIndex) or null if not valid bold.
     */
    private fun parseBoldAt(text: String, startIndex: Int): Pair<String, Int>? {
        if (startIndex + 1 >= text.length || text[startIndex] != '*' || text[startIndex + 1] != '*') {
            return null
        }
        
        // Find closing **
        var i = startIndex + 2
        while (i < text.length - 1) {
            if (text[i] == '*' && text[i + 1] == '*') {
                val boldText = text.substring(startIndex + 2, i)
                return Pair(boldText, i + 2)
            }
            i++
        }
        
        return null
    }
    
    /**
     * Parse italic text at given position. Returns (text, nextIndex) or null if not valid italic.
     */
    private fun parseItalicAt(text: String, startIndex: Int): Pair<String, Int>? {
        if (startIndex >= text.length || text[startIndex] != '*') return null
        
        // Find closing *
        var i = startIndex + 1
        while (i < text.length) {
            if (text[i] == '*') {
                val italicText = text.substring(startIndex + 1, i)
                return Pair(italicText, i + 1)
            }
            i++
        }
        
        return null
    }
    
    /**
     * Parse inline code at given position. Returns (text, nextIndex) or null if not valid code.
     */
    private fun parseInlineCodeAt(text: String, startIndex: Int): Pair<String, Int>? {
        if (startIndex >= text.length || text[startIndex] != '`') return null
        
        // Find closing `
        var i = startIndex + 1
        while (i < text.length) {
            if (text[i] == '`') {
                val codeText = text.substring(startIndex + 1, i)
                return Pair(codeText, i + 1)
            }
            i++
        }
        
        return null
    }
    
    /**
     * Append link with styling and URL annotation.
     */
    private fun AnnotatedString.Builder.appendLink(text: String, url: String) {
        pushStringAnnotation(tag = "URL", annotation = url)
        pushStyle(
            SpanStyle(
                color = Color(0xFF1976D2),
                textDecoration = TextDecoration.Underline
            )
        )
        append(text)
        pop()
        pop()
    }
    
    /**
     * Append bold text.
     */
    private fun AnnotatedString.Builder.appendBold(text: String) {
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        appendInlineFormatting(text) // Allow nested formatting
        pop()
    }
    
    /**
     * Append italic text.
     */
    private fun AnnotatedString.Builder.appendItalic(text: String) {
        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
        appendInlineFormatting(text) // Allow nested formatting
        pop()
    }
    
    /**
     * Append inline code with monospace font.
     */
    private fun AnnotatedString.Builder.appendInlineCode(text: String) {
        pushStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = Color(0xFFF5F5F5),
                color = Color(0xFF333333)
            )
        )
        append(text)
        pop()
    }
}

/**
 * Composable that renders markdown text with proper formatting.
 * 
 * @param markdown The markdown text to render
 * @param modifier Modifier for the composable
 * @param style Base text style to apply
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    val uriHandler = LocalUriHandler.current
    val annotatedString = remember(markdown) {
        MarkdownParser.parseMarkdown(markdown)
    }
    
    SelectionContainer {
        ClickableText(
            text = annotatedString,
            modifier = modifier,
            style = style,
            onClick = { offset ->
                // Handle link clicks
                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        try {
                            uriHandler.openUri(annotation.item)
                        } catch (e: Exception) {
                            // Handle invalid URLs gracefully
                        }
                    }
            }
        )
    }
}