package ai.pipecat.gemini_multimodal_websocket_demo.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.LocalContentColor

/**
 * Markdown parser that converts markdown text to blocks for rendering.
 * 
 * Supports:
 * - Headers (h1-h6) with different font sizes
 * - Bold (**text**) and italic (*text*)
 * - Inline code (`code`)
 * - Code blocks (```code```)
 * - Bullet lists (- item, * item)
 * - Numbered lists (1. item)
 * - Links ([text](url)) - clickable
 * - Tables (| col1 | col2 |) with horizontal scrolling
 * 
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5
 */
object MarkdownParser {
    
    sealed class Block {
        data class TextBlock(val annotatedString: AnnotatedString) : Block()
        data class TableBlock(val rows: List<List<String>>) : Block()
        data class LatexBlock(val latex: String) : Block()
    }
    
    data class ParsedContent(
        val blocks: List<Block>
    )
    
    /**
     * Parse markdown text into blocks (text or table).
     */
    fun parseMarkdown(markdown: String): ParsedContent {
        val blocks = mutableListOf<Block>()
        val lines = markdown.lines()
        var i = 0
        
        // Accumulator for text lines between tables
        val textLines = mutableListOf<String>()
        
        fun flushTextBlock() {
            if (textLines.isNotEmpty()) {
                val textContent = textLines.joinToString("\n")
                val annotatedString = parseTextBlock(textContent)
                blocks.add(Block.TextBlock(annotatedString))
                textLines.clear()
            }
        }
        
        while (i < lines.size) {
            val line = lines[i]
            
            when {
                // Latex Blocks ($$ or \[)
                line.trim().startsWith("$$") || line.trim().startsWith("\\[") -> {
                    val latexResult = parseLatexBlock(lines, i)
                    flushTextBlock()
                    blocks.add(Block.LatexBlock(latexResult.first))
                    i = latexResult.second
                }

                // Tables (| col1 | col2 |)
                line.trim().startsWith("|") && line.trim().endsWith("|") -> {
                    val tableResult = parseTable(lines, i)
                    if (tableResult != null) {
                        // Flush accumulated text before table
                        flushTextBlock()
                        // Add table block
                        blocks.add(Block.TableBlock(tableResult.first))
                        i = tableResult.second
                    } else {
                        textLines.add(line)
                        i++
                    }
                }
                
                // All other lines - accumulate as text
                else -> {
                    textLines.add(line)
                    i++
                }
            }
        }
        
        // Flush remaining text
        flushTextBlock()
        
        return ParsedContent(blocks)
    }
    
    /**
     * Parse a text block (non-table content) into AnnotatedString.
     */
    private fun parseTextBlock(text: String): AnnotatedString {
        return buildAnnotatedString {
            val lines = text.lines()
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
     * Parse table starting from current line index.
     * Returns the table rows and the next line index to process.
     */
    private fun parseTable(lines: List<String>, startIndex: Int): Pair<List<List<String>>, Int>? {
        val tableRows = mutableListOf<List<String>>()
        var i = startIndex
        
        // Parse header row
        if (i >= lines.size || !lines[i].trim().startsWith("|")) return null
        val headerCells = parseTableRow(lines[i])
        if (headerCells.isEmpty()) return null
        tableRows.add(headerCells)
        i++
        
        // Check for separator row (|---|---|)
        if (i >= lines.size) return Pair(tableRows, i)
        val separatorLine = lines[i].trim()
        if (separatorLine.startsWith("|") && separatorLine.contains("-")) {
            i++ // Skip separator
        }
        
        // Parse data rows
        while (i < lines.size) {
            val line = lines[i].trim()
            if (!line.startsWith("|") || !line.endsWith("|")) break
            
            val cells = parseTableRow(line)
            if (cells.isEmpty()) break
            tableRows.add(cells)
            i++
        }
        
        return if (tableRows.size > 1) Pair(tableRows, i) else null
    }
    
    /**
     * Parse a single table row into cells.
     * Cleans HTML tags and markdown formatting for better table display.
     */
    private fun parseTableRow(line: String): List<String> {
        val trimmed = line.trim().removeSurrounding("|")
        return trimmed.split("|").map { cell ->
            cell.trim()
                .replace(Regex("<br>|<br/>|<br />"), " ") // Remove HTML line breaks
                .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1") // Remove bold **text**
                .replace(Regex("\\*([^*]+)\\*"), "$1") // Remove italic *text*
                .replace(Regex("`([^`]+)`"), "$1") // Remove inline code `text`
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
     * Parse LaTeX block starting from current line index.
     * Supports both single line and multi-line blocks for $$ and \[
     */
    private fun parseLatexBlock(lines: List<String>, startIndex: Int): Pair<String, Int> {
        val i = startIndex
        val startLine = lines[i].trim()
        
        // Determine delimiters
        val isBracket = startLine.startsWith("\\[")
        val endMarker = if (isBracket) "\\]" else "$$"
        val startMarker = if (isBracket) "\\[" else "$$"
        
        // Check for single line case: $$ equation $$ or \[ equation \]
        if (startLine.length > startMarker.length && startLine.endsWith(endMarker) && startLine != startMarker) {
            val content = startLine.substring(startMarker.length, startLine.length - endMarker.length).trim()
            return Pair(content, i + 1)
        }
        
        // Multi-line case
        val latexLines = mutableListOf<String>()
        var currentIndex = i + 1
        
        while (currentIndex < lines.size) {
            val line = lines[currentIndex]
            if (line.trim().startsWith(endMarker)) {
                return Pair(latexLines.joinToString("\n"), currentIndex + 1)
            }
            latexLines.add(line)
            currentIndex++
        }
        
        return Pair(latexLines.joinToString("\n"), currentIndex)
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
     * Also auto-detects plain URLs and makes them clickable.
     */
    private fun AnnotatedString.Builder.appendInlineFormatting(text: String) {
        var i = 0
        val chars = text.toCharArray()
        
        while (i < chars.size) {
            when {
                // Markdown links [text](url)
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
                
                // Auto-detect plain URLs (http:// or https://)
                i < chars.size - 7 && (text.substring(i).startsWith("http://") || text.substring(i).startsWith("https://")) -> {
                    val urlResult = parseUrlAt(text, i)
                    if (urlResult != null) {
                        appendLink(urlResult.first, urlResult.first) // Use URL as both text and link
                        i = urlResult.second
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
     * Parse plain URL at given position. Returns (url, nextIndex) or null if not a valid URL.
     * Detects URLs starting with http:// or https://
     */
    private fun parseUrlAt(text: String, startIndex: Int): Pair<String, Int>? {
        if (startIndex >= text.length) return null
        
        // Check if starts with http:// or https://
        val isHttp = text.substring(startIndex).startsWith("http://")
        val isHttps = text.substring(startIndex).startsWith("https://")
        if (!isHttp && !isHttps) return null
        
        // Find end of URL (space, newline, or end of string)
        var i = startIndex
        while (i < text.length && !text[i].isWhitespace() && text[i] != ')' && text[i] != ']') {
            i++
        }
        
        val url = text.substring(startIndex, i)
        return Pair(url, i)
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
     * Append link with styling and URL annotation using new LinkAnnotation API.
     */
    private fun AnnotatedString.Builder.appendLink(text: String, url: String) {
        withLink(
            LinkAnnotation.Url(
                url = url,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = Color(0xFF1976D2),
                        textDecoration = TextDecoration.Underline
                    )
                )
            )
        ) {
            append(text)
        }
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
 * Links are clickable and open in browser using new LinkAnnotation API.
 * Text wraps normally - tables have horizontal scroll.
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
    val parsedContent = remember(markdown) {
        MarkdownParser.parseMarkdown(markdown)
    }
    
    // Render blocks - text blocks wrap, table blocks scroll horizontally
    Column(modifier = modifier) {
        parsedContent.blocks.forEach { block ->
            when (block) {
                is MarkdownParser.Block.TextBlock -> {
                    // Regular text - wraps normally
                    SelectionContainer {
                        Text(
                            text = block.annotatedString,
                            modifier = Modifier.fillMaxWidth(),
                            style = style
                        )
                    }
                }
                is MarkdownParser.Block.TableBlock -> {
                    // Table - horizontal scroll
                    TableView(
                        rows = block.rows,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is MarkdownParser.Block.LatexBlock -> {
                    // LaTeX - rendered via WebView
                    LatexView(
                        latex = block.latex,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Renders LaTeX using a WebView and KaTeX from CDN.
 * Includes fallback to raw text if KaTeX fails to load (e.g. offline).
 * Uses auto-render extension to support mixed text and math.
 */
@Composable
fun LatexView(
    latex: String,
    modifier: Modifier = Modifier
) {
    // Get current text color from theme
    val contentColor = LocalContentColor.current
    val textColorHex = String.format("#%06X", (0xFFFFFF and contentColor.toArgb()))
    
    // Generate unique ID for this equation
    val htmlContent = remember(latex, textColorHex) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
            <script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
            <script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js"></script>
            <style>
                body { 
                    margin: 0; 
                    padding: 4px; 
                    background-color: transparent;
                    color: $textColorHex; /* Adapt to theme */
                    font-family: Roboto, sans-serif;
                    line-height: 1.5;
                    font-size: 16px; /* Match typical body text size */
                }
                .katex-display { margin: 0.5em 0; }
                #error { color: #FF6B6B; font-size: 12px; display: none; }
                #raw { font-family: monospace; white-space: pre-wrap; display: none; }
                #content { word-wrap: break-word; }
            </style>
        </head>
        <body>
            <div id="content">${latex.replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")}</div>
            <div id="raw" style="display:none">$$ ${latex.replace("<", "&lt;").replace(">", "&gt;")} $$</div>
            
            <script>
                document.addEventListener("DOMContentLoaded", function() {
                    try {
                        if (typeof renderMathInElement === 'undefined') {
                            throw new Error("KaTeX auto-render not loaded");
                        }
                        
                        renderMathInElement(document.body, {
                            delimiters: [
                                {left: '$$', right: '$$', display: true},
                                {left: '\\[', right: '\\]', display: true},
                                {left: '\\(', right: '\\)', display: false},
                                {left: '$', right: '$', display: false}
                            ],
                            throwOnError: false
                        });
                    } catch (e) {
                         // Fallback to raw text if error occurs
                        // document.getElementById('content').style.display = 'none'; // Keep content visible as text
                        // document.getElementById('raw').style.display = 'block';
                        console.error("Latex render error:", e);
                    }
                });
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.domStorageEnabled = true
                setBackgroundColor(0x00000000) // Transparent background
                
                // Disable scrolling to act more like a static view
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("https://katex.org", htmlContent, "text/html", "UTF-8", null)
        },
        // Wrap content height to avoid infinite expansion, but don't limit hard
        modifier = modifier.heightIn(min = 40.dp)
    )
}

/**
 * Renders a table with horizontal scrolling.
 * Uses monospace font for proper column alignment.
 */
@Composable
fun TableView(
    rows: List<List<String>>,
    modifier: Modifier = Modifier
) {
    if (rows.isEmpty()) return
    
    val columnCount = rows.maxOfOrNull { it.size } ?: 0
    val columnWidths = IntArray(columnCount) { col ->
        rows.maxOfOrNull { row -> row.getOrNull(col)?.length ?: 0 } ?: 0
    }
    
    // Build table as AnnotatedString
    val tableText = buildAnnotatedString {
        pushStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                background = Color(0xFFF8F8F8)
            )
        )
        
        rows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { colIndex, cell ->
                val paddedCell = cell.padEnd(columnWidths[colIndex] + 1)
                
                if (rowIndex == 0) {
                    // Header row - bold
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(paddedCell)
                    pop()
                } else {
                    append(paddedCell)
                }
                
                if (colIndex < row.size - 1) {
                    append(" | ")
                }
            }
            
            if (rowIndex < rows.size - 1) {
                append("\n")
                
                // Add separator after header
                if (rowIndex == 0) {
                    columnWidths.forEachIndexed { index, width ->
                        append("-".repeat(width + 1))
                        if (index < columnWidths.size - 1) {
                            append("-+-")
                        }
                    }
                    append("\n")
                }
            }
        }
        
        pop()
    }
    
    // Horizontal scroll for table
    SelectionContainer {
        Text(
            text = tableText,
            modifier = modifier.horizontalScroll(rememberScrollState()),
            style = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        )
    }
}