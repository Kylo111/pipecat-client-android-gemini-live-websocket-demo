package ai.pipecat.gemini_multimodal_websocket_demo.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView

/**
 * Markdown parser that converts markdown text to blocks for rendering.
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
                // Latex Blocks
                line.trim().startsWith("$$") || line.trim().startsWith("\\[") -> {
                    val latexResult = parseLatexBlock(lines, i)
                    flushTextBlock()
                    blocks.add(Block.LatexBlock(latexResult.first))
                    i = latexResult.second
                }

                // Tables
                line.trim().startsWith("|") && line.trim().endsWith("|") -> {
                    val tableResult = parseTable(lines, i)
                    if (tableResult != null) {
                        flushTextBlock()
                        blocks.add(Block.TableBlock(tableResult.first))
                        i = tableResult.second
                    } else {
                        textLines.add(line)
                        i++
                    }
                }
                
                // All other lines
                else -> {
                    textLines.add(line)
                    i++
                }
            }
        }
        
        flushTextBlock()
        return ParsedContent(blocks)
    }
    
    private fun parseTextBlock(text: String): AnnotatedString {
        return buildAnnotatedString {
            val lines = text.lines()
            var i = 0
            
            while (i < lines.size) {
                val line = lines[i]
                
                when {
                    // Code blocks
                    line.trim().startsWith("```") -> {
                        val codeBlockResult = parseCodeBlock(lines, i)
                        appendCodeBlock(codeBlockResult.first)
                        i = codeBlockResult.second
                    }
                    
                    // Headers
                    line.trim().startsWith("#") -> {
                        appendHeader(line)
                        if (i < lines.size - 1) append("\n")
                        i++
                    }
                    
                    // Bullet lists
                    line.trim().startsWith("- ") || line.trim().startsWith("* ") -> {
                        appendBulletListItem(line)
                        if (i < lines.size - 1) append("\n")
                        i++
                    }
                    
                    // Numbered lists
                    line.trim().matches(Regex("^\\d+\\. .*")) -> {
                        appendNumberedListItem(line)
                        if (i < lines.size - 1) append("\n")
                        i++
                    }
                    
                    // Regular paragraph
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
    
    // ... helper parsing methods ...
    private fun parseTable(lines: List<String>, startIndex: Int): Pair<List<List<String>>, Int>? {
        val tableRows = mutableListOf<List<String>>()
        var i = startIndex
        if (i >= lines.size || !lines[i].trim().startsWith("|")) return null
        val headerCells = parseTableRow(lines[i])
        if (headerCells.isEmpty()) return null
        tableRows.add(headerCells)
        i++
        if (i >= lines.size) return Pair(tableRows, i)
        val separatorLine = lines[i].trim()
        if (separatorLine.startsWith("|") && separatorLine.contains("-")) i++
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
    
    private fun parseTableRow(line: String): List<String> {
        val trimmed = line.trim().removeSurrounding("|")
        return trimmed.split("|").map { cell ->
            cell.trim()
                .replace(Regex("<br>|<br/>|<br />"), " ")
                .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
                .replace(Regex("\\*([^*]+)\\*"), "$1")
                .replace(Regex("`([^`]+)`"), "$1")
        }
    }
    
    private fun parseCodeBlock(lines: List<String>, startIndex: Int): Pair<String, Int> {
        val codeLines = mutableListOf<String>()
        var i = startIndex + 1 
        while (i < lines.size) {
            val line = lines[i]
            if (line.trim().startsWith("```")) return Pair(codeLines.joinToString("\n"), i + 1)
            codeLines.add(line)
            i++
        }
        return Pair(codeLines.joinToString("\n"), i)
    }
    
    private fun parseLatexBlock(lines: List<String>, startIndex: Int): Pair<String, Int> {
        val i = startIndex
        val startLine = lines[i].trim()
        val isBracket = startLine.startsWith("\\[")
        val endMarker = if (isBracket) "\\]" else "$$"
        val startMarker = if (isBracket) "\\[" else "$$"
        if (startLine.length > startMarker.length && startLine.endsWith(endMarker) && startLine != startMarker) {
            val content = startLine.substring(startMarker.length, startLine.length - endMarker.length).trim()
            return Pair(content, i + 1)
        }
        val latexLines = mutableListOf<String>()
        var currentIndex = i + 1
        while (currentIndex < lines.size) {
            val line = lines[currentIndex]
            if (line.trim().startsWith(endMarker)) return Pair(latexLines.joinToString("\n"), currentIndex + 1)
            latexLines.add(line)
            currentIndex++
        }
        return Pair(latexLines.joinToString("\n"), currentIndex)
    }
    
    private fun AnnotatedString.Builder.appendCodeBlock(code: String) {
        pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, background = Color(0xFFF5F5F5), color = Color(0xFF333333)))
        append(code)
        pop()
        append("\n")
    }
    
    private fun AnnotatedString.Builder.appendHeader(line: String) {
        val trimmed = line.trim()
        val level = trimmed.takeWhile { it == '#' }.length
        val text = trimmed.drop(level).trim()
        val fontSize = when (level) { 1 -> 24.sp; 2 -> 20.sp; 3 -> 18.sp; 4 -> 16.sp; 5 -> 14.sp; else -> 14.sp }
        pushStyle(SpanStyle(fontSize = fontSize, fontWeight = FontWeight.Bold, color = Color.Black))
        appendInlineFormatting(text)
        pop()
    }
    
    private fun AnnotatedString.Builder.appendBulletListItem(line: String) {
        val trimmed = line.trim()
        val text = trimmed.drop(2)
        append("• ")
        appendInlineFormatting(text)
    }
    
    private fun AnnotatedString.Builder.appendNumberedListItem(line: String) {
        val trimmed = line.trim()
        val numberPart = trimmed.takeWhile { it.isDigit() || it == '.' }
        val text = trimmed.drop(numberPart.length).trim()
        append("$numberPart ")
        appendInlineFormatting(text)
    }
    
    private fun AnnotatedString.Builder.appendParagraphWithInlineFormatting(line: String) {
        appendInlineFormatting(line.trim())
    }
    
    private fun AnnotatedString.Builder.appendInlineFormatting(text: String) {
        var i = 0
        val chars = text.toCharArray()
        
        while (i < chars.size) {
            when {
                // Inline LaTeX \(...\) - render as plain text (simplified)
                i < chars.size - 3 && chars[i] == '\\' && chars[i + 1] == '(' -> {
                    val latexResult = parseInlineLatexAt(text, i)
                    if (latexResult != null) {
                        appendInlineLatex(latexResult.first)
                        i = latexResult.second
                    } else {
                        append(chars[i])
                        i++
                    }
                }
                
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
                
                // Bold/Italic/Code
                i < chars.size - 3 && chars[i] == '*' && chars[i + 1] == '*' -> {
                    val boldResult = parseBoldAt(text, i)
                    if (boldResult != null) { appendBold(boldResult.first); i = boldResult.second } else { append(chars[i]); i++ }
                }
                i < chars.size - 2 && chars[i] == '*' -> {
                    val italicResult = parseItalicAt(text, i)
                    if (italicResult != null) { appendItalic(italicResult.first); i = italicResult.second } else { append(chars[i]); i++ }
                }
                i < chars.size - 2 && chars[i] == '`' -> {
                    val codeResult = parseInlineCodeAt(text, i)
                    if (codeResult != null) { appendInlineCode(codeResult.first); i = codeResult.second } else { append(chars[i]); i++ }
                }
                
                // URLs - regex check last
                else -> {
                    val urlMatch = checkUrlAt(text, i)
                    if (urlMatch != null) {
                        appendLink(urlMatch.first, urlMatch.first)
                        i = urlMatch.second
                    } else {
                        append(chars[i])
                        i++
                    }
                }
            }
        }
    }
    
    // Parse inline LaTeX \(...\) and return content and end position
    private fun parseInlineLatexAt(text: String, startIndex: Int): Pair<String, Int>? {
        if (startIndex + 3 >= text.length) return null
        if (text[startIndex] != '\\' || text[startIndex + 1] != '(') return null
        
        var i = startIndex + 2
        while (i < text.length - 1) {
            if (text[i] == '\\' && text[i + 1] == ')') {
                val content = text.substring(startIndex + 2, i)
                return Pair(content, i + 2)
            }
            i++
        }
        return null
    }
    
    // Render inline LaTeX as styled text (simplified - strips LaTeX commands)
    private fun AnnotatedString.Builder.appendInlineLatex(latex: String) {
        // Simplify common LaTeX commands to readable text
        val simplified = latex
            .replace(Regex("\\\\mathbf\\{([^}]+)\\}"), "$1")  // \mathbf{F} -> F
            .replace(Regex("\\\\mathrm\\{([^}]+)\\}"), "$1")  // \mathrm{d} -> d
            .replace(Regex("\\\\text\\{([^}]+)\\}"), "$1")    // \text{abc} -> abc
            .replace(Regex("\\\\frac\\{([^}]+)\\}\\{([^}]+)\\}"), "$1/$2") // \frac{a}{b} -> a/b
            .replace(Regex("\\\\sqrt\\{([^}]+)\\}"), "√$1")   // \sqrt{x} -> √x
            .replace(Regex("\\\\([a-zA-Z]+)"), "")            // Remove other commands
            .replace("_", "")                                  // Remove subscript marker
            .replace("^", "")                                  // Remove superscript marker
            .replace("{", "").replace("}", "")                 // Remove braces
            .trim()
        
        // Style as italic to indicate it's a math expression
        pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Color(0xFF1565C0)))
        append(simplified)
        pop()
    }

    private fun checkUrlAt(text: String, startIndex: Int): Pair<String, Int>? {
        val char = text[startIndex]
        if (char != 'h' && char != 'H' && char != 'w' && char != 'W') return null
        
        val remainder = text.substring(startIndex)
        if (!remainder.startsWith("http", ignoreCase = true) && !remainder.startsWith("www.", ignoreCase = true)) return null
        
        // Robust Regex
        val urlRegex = Regex("^(?:https?://|www\\.)[a-zA-Z0-9][-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b(?:[-a-zA-Z0-9()@:%_+.~#?&/=]*)")
        val match = urlRegex.find(remainder) ?: return null
        
        var url = match.value
        while (url.endsWith(".") || url.endsWith(",") || url.endsWith(")") || url.endsWith("]")) {
            url = url.dropLast(1)
        }
        val fullUrl = if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url
        return Pair(fullUrl, startIndex + url.length)
    }
    
    private fun parseLinkAt(text: String, startIndex: Int): Triple<String, String, Int>? {
        if (startIndex >= text.length || text[startIndex] != '[') return null
        var i = startIndex + 1
        while (i < text.length && text[i] != ']') i++
        if (i >= text.length) return null
        val linkText = text.substring(startIndex + 1, i)
        i++ 
        if (i >= text.length || text[i] != '(') return null
        i++ 
        val urlStart = i
        while (i < text.length && text[i] != ')') i++
        if (i >= text.length) return null
        val url = text.substring(urlStart, i)
        i++ 
        return Triple(linkText, url, i)
    }
    
    private fun parseBoldAt(text: String, startIndex: Int): Pair<String, Int>? {
        if (startIndex + 1 >= text.length || text[startIndex] != '*' || text[startIndex + 1] != '*') return null
        var i = startIndex + 2
        while (i < text.length - 1) {
            if (text[i] == '*' && text[i + 1] == '*') return Pair(text.substring(startIndex + 2, i), i + 2)
            i++
        }
        return null
    }
    
    private fun parseItalicAt(text: String, startIndex: Int): Pair<String, Int>? {
        if (startIndex >= text.length || text[startIndex] != '*') return null
        var i = startIndex + 1
        while (i < text.length) {
            if (text[i] == '*') return Pair(text.substring(startIndex + 1, i), i + 1)
            i++
        }
        return null
    }
    
    private fun parseInlineCodeAt(text: String, startIndex: Int): Pair<String, Int>? {
        if (startIndex >= text.length || text[startIndex] != '`') return null
        var i = startIndex + 1
        while (i < text.length) {
            if (text[i] == '`') return Pair(text.substring(startIndex + 1, i), i + 1)
            i++
        }
        return null
    }
    
    private fun AnnotatedString.Builder.appendLink(text: String, url: String) {
        // Force style explicitly
        pushStyle(SpanStyle(color = Color(0xFF1976D2), textDecoration = TextDecoration.Underline))
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
        pop()
    }
    
    private fun AnnotatedString.Builder.appendBold(text: String) {
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        appendInlineFormatting(text)
        pop()
    }
    
    private fun AnnotatedString.Builder.appendItalic(text: String) {
        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
        appendInlineFormatting(text)
        pop()
    }
    
    private fun AnnotatedString.Builder.appendInlineCode(text: String) {
        pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFFF5F5F5), color = Color(0xFF333333)))
        append(text)
        pop()
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    val parsedContent = remember(markdown) {
        MarkdownParser.parseMarkdown(markdown)
    }
    
    Column(modifier = modifier) {
        parsedContent.blocks.forEach { block ->
            when (block) {
                is MarkdownParser.Block.TextBlock -> {
                    Text(
                        text = block.annotatedString,
                        modifier = Modifier.fillMaxWidth(),
                        style = style
                    )
                }
                is MarkdownParser.Block.TableBlock -> {
                    TableView(rows = block.rows, modifier = Modifier.fillMaxWidth())
                }
                is MarkdownParser.Block.LatexBlock -> {
                    LatexView(latex = block.latex, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun LatexView(latex: String, modifier: Modifier = Modifier) {
    val contentColor = LocalContentColor.current
    val textColorHex = String.format("#%06X", (0xFFFFFF and contentColor.toArgb()))
    var webViewHeight by remember { mutableStateOf(100.dp) }
    
    // Wrap latex content with delimiters for KaTeX to recognize
    val latexWithDelimiters = "$$${latex}$$"
    
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
                * { box-sizing: border-box; }
                html, body { margin: 0; padding: 0; width: 100%; overflow-x: hidden; }
                body { padding: 8px 12px; background-color: transparent; color: $textColorHex; font-family: Roboto, -apple-system, sans-serif; font-size: 16px; line-height: 1.6; }
                #content { word-wrap: break-word; overflow-wrap: break-word; }
                .katex-display { margin: 0.8em 0; overflow-x: auto; overflow-y: hidden; }
                .katex { color: $textColorHex; }
                #error { color: #FF6B6B; font-size: 12px; display: none; }
            </style>
        </head>
        <body>
            <div id="content">${latexWithDelimiters.replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")}</div>
            <div id="error"></div>
            <script>
                function updateHeight() { const height = document.body.scrollHeight; console.log('HEIGHT:' + height); }
                document.addEventListener("DOMContentLoaded", function() {
                    try {
                        if (typeof renderMathInElement === 'undefined') throw new Error("KaTeX not loaded");
                        renderMathInElement(document.body, { delimiters: [{left: '\$\$', right: '\$\$', display: true}, {left: '\\\\[', right: '\\\\]', display: true}, {left: '\\\\(', right: '\\\\)', display: false}, {left: '\$', right: '\$', display: false}], throwOnError: false });
                        setTimeout(updateHeight, 100); setTimeout(updateHeight, 500);
                    } catch (e) { document.getElementById('error').innerText = 'Err: ' + e.message; document.getElementById('error').style.display = 'block'; updateHeight(); }
                });
                window.addEventListener('resize', updateHeight);
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.apply { javaScriptEnabled = true; loadWithOverviewMode = true; useWideViewPort = false; domStorageEnabled = true; setSupportZoom(false) }
                setBackgroundColor(0x00000000)
                isVerticalScrollBarEnabled = true
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                        consoleMessage?.message()?.let { msg ->
                            if (msg.startsWith("HEIGHT:")) {
                                val height = msg.substringAfter("HEIGHT:").toIntOrNull()
                                if (height != null && height > 0) webViewHeight = (height + 20).dp
                            }
                        }
                        return true
                    }
                }
            }
        },
        update = { webView -> webView.loadDataWithBaseURL("https://katex.org", htmlContent, "text/html", "UTF-8", null) },
        modifier = modifier.heightIn(min = 60.dp, max = 2000.dp).height(webViewHeight)
    )
}

@Composable
fun TableView(rows: List<List<String>>, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    val columnCount = rows.maxOfOrNull { it.size } ?: 0
    val columnWidths = IntArray(columnCount) { col -> rows.maxOfOrNull { row -> row.getOrNull(col)?.length ?: 0 } ?: 0 }
    val tableText = buildAnnotatedString {
        pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, background = Color(0xFFF8F8F8)))
        rows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { colIndex, cell ->
                val paddedCell = cell.padEnd(columnWidths[colIndex] + 1)
                if (rowIndex == 0) { pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(paddedCell); pop() } else append(paddedCell)
                if (colIndex < row.size - 1) append(" | ")
            }
            if (rowIndex < rows.size - 1) {
                append("\n")
                if (rowIndex == 0) {
                    columnWidths.forEachIndexed { index, width ->
                        append("-".repeat(width + 1))
                        if (index < columnWidths.size - 1) append("-+-")
                    }
                    append("\n")
                }
            }
        }
        pop()
    }
    // SelectionContainer removed from TableView to be consistent with main Text? 
    // Actually, keeping SelectionContainer here is fine as tables usually aren't links.
    // But original code has it. I'll keep it.
    SelectionContainer {
        Text(text = tableText, modifier = modifier.horizontalScroll(rememberScrollState()), style = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp))
    }
}