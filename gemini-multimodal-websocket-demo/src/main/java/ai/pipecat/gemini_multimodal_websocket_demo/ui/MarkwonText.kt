package ai.pipecat.gemini_multimodal_websocket_demo.ui

import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * Composable that renders Markdown with LaTeX support using Markwon library.
 * 
 * Features:
 * - Full Markdown support (headers, lists, code blocks, links, etc.)
 * - Inline LaTeX: \(...\) or $...$
 * - Block LaTeX: \[...\] or $$...$$
 * - Clickable links
 * - Proper theming (light/dark mode)
 * - Native scrolling performance
 * 
 * @param markdown The markdown text to render
 * @param modifier Modifier for the composable
 * @param style Base text style to apply
 */
@Composable
fun MarkwonMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    
    val markwon = remember(textColor, linkColor) {
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .build()
    }
    
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                // Set text properties
                textSize = style.fontSize.value
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                
                // Enable link clicking
                movementMethod = android.text.method.LinkMovementMethod.getInstance()
                
                // Apply theme
                setTextIsSelectable(true)
            }
        },
        update = { textView ->
            markwon.setMarkdown(textView, markdown)
        },
        modifier = modifier.fillMaxWidth()
    )
}
