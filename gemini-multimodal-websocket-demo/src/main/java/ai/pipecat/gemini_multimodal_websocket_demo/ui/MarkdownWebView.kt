package ai.pipecat.gemini_multimodal_websocket_demo.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream

/**
 * WebView-based Markdown renderer using marked.js and KaTeX.
 * 
 * This component provides professional-grade Markdown rendering with:
 * - CommonMark + GFM (tables, strikethrough, task lists)
 * - LaTeX math rendering via KaTeX
 * - Syntax highlighting via highlight.js
 * - Secure asset loading via HTTPS (appassets.androidplatform.net)
 * - XSS prevention through HTML sanitization
 * 
 * @param markdown The Markdown content to render
 * @param modifier Compose modifier for layout (use fillMaxHeight() or weight(1f))
 * @param onLinkClick Callback invoked when user clicks a link
 * @param onRenderComplete Callback invoked when rendering completes successfully
 * @param onError Callback invoked when a rendering error occurs
 * @param onWebViewCreated Callback invoked when WebView is created (for testing)
 */
@Composable
fun MarkdownWebView(
    markdown: String,
    modifier: Modifier = Modifier,
    onLinkClick: (String) -> Unit = {},
    onRenderComplete: () -> Unit = {},
    onError: (String) -> Unit = {},
    onWebViewCreated: (WebView) -> Unit = {}
) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    
    // Create WebViewAssetLoader for secure HTTPS asset loading
    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }
    
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                // Configure WebView settings with security hardening
                settings.apply {
                    // Enable JavaScript for marked.js and KaTeX
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    
                    // SECURITY: Disable file access to prevent file:// vulnerabilities
                    allowFileAccess = false
                    allowContentAccess = false
                    allowFileAccessFromFileURLs = false
                    allowUniversalAccessFromFileURLs = false
                    
                    // SECURITY: Disable unnecessary features
                    setSupportMultipleWindows(false)
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    
                    // Cache policy: LOAD_DEFAULT (network blocked in shouldInterceptRequest)
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    
                    // Enable smooth scrolling
                    setRenderPriority(WebSettings.RenderPriority.HIGH)
                    
                    // Enable text selection and copying
                    textZoom = 100  // Ensure consistent text size for selection
                }
                
                // Configure nested scrolling for smooth interaction
                isNestedScrollingEnabled = true
                
                // Enable text selection (long-press to select)
                setOnLongClickListener(null)  // Use default long-click behavior for text selection
                isLongClickable = true
                
                // SECURITY: Block JS dialogs and log console messages
                webChromeClient = object : WebChromeClient() {
                    override fun onJsAlert(
                        view: WebView,
                        url: String,
                        message: String,
                        result: JsResult
                    ): Boolean {
                        Log.w(TAG, "Blocked JS alert: $message")
                        result.cancel()
                        return true  // Block alert
                    }
                    
                    override fun onJsConfirm(
                        view: WebView,
                        url: String,
                        message: String,
                        result: JsResult
                    ): Boolean {
                        Log.w(TAG, "Blocked JS confirm: $message")
                        result.cancel()
                        return true  // Block confirm
                    }
                    
                    override fun onJsPrompt(
                        view: WebView,
                        url: String,
                        message: String,
                        defaultValue: String,
                        result: JsPromptResult
                    ): Boolean {
                        Log.w(TAG, "Blocked JS prompt: $message")
                        result.cancel()
                        return true  // Block prompt
                    }
                    
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        Log.d(TAG, "WebView console [${consoleMessage.messageLevel()}]: ${consoleMessage.message()}")
                        return true
                    }
                }
                
                // Add JavaScript interface for error reporting (minimal API for security)
                addJavascriptInterface(WebViewInterface(onError, onRenderComplete), "Android")
                
                // Notify that WebView was created (for testing)
                onWebViewCreated(this)
                
                // Track if page is loaded
                var pageLoaded = false
                var pendingMarkdown: String? = null
                
                // SECURITY: Intercept requests and control navigation
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.d(TAG, "Page finished loading: $url")
                        view?.setTag(R.id.webview_page_loaded, true)
                        
                        // Render any pending markdown
                        val pendingMd = view?.getTag(R.id.webview_pending_markdown) as? String
                        if (pendingMd != null) {
                            renderMarkdownContent(view, pendingMd, isDarkTheme)
                            view?.setTag(R.id.webview_pending_markdown, null)
                        }
                    }
                    
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        val url = request.url
                        
                        // SECURITY: Only allow appassets.androidplatform.net
                        if (url.host == "appassets.androidplatform.net") {
                            return assetLoader.shouldInterceptRequest(url)
                        }
                        
                        // HARD BLOCK: Return 403 Forbidden for all other requests
                        // This prevents WebView from attempting network fallback
                        Log.w(TAG, "Blocked unauthorized request: $url")
                        return WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            403,
                            "Forbidden",
                            mapOf("X-Blocked-Reason" to "Not appassets domain"),
                            ByteArrayInputStream("403 Forbidden: Network requests blocked".toByteArray())
                        )
                    }
                    
                    // SECURITY: Validate and control all navigation
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val url = request.url
                        val scheme = url.scheme?.lowercase()
                        
                        // SECURITY: Allowlist only http/https schemes
                        when (scheme) {
                            "http", "https" -> {
                                // Open in system browser
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, url)
                                    context.startActivity(intent)
                                    onLinkClick(url.toString())
                                } catch (e: ActivityNotFoundException) {
                                    Log.e(TAG, "No activity found to handle URL: $url", e)
                                }
                                return true  // Block navigation in WebView
                            }
                            "file", "javascript", "intent", "data" -> {
                                // SECURITY: Block dangerous schemes
                                Log.w(TAG, "Blocked dangerous scheme: $scheme for URL: $url")
                                return true  // Block navigation
                            }
                            else -> {
                                // Block unknown schemes
                                Log.w(TAG, "Blocked unknown scheme: $scheme for URL: $url")
                                return true  // Block navigation
                            }
                        }
                    }
                }
                
                // Store references for update block
                setTag(R.id.webview_page_loaded, false)
                setTag(R.id.webview_pending_markdown, null as String?)
                
                // Load HTML via HTTPS URL (appassets.androidplatform.net)
                loadUrl("https://appassets.androidplatform.net/assets/markdown-renderer/index.html")
            }
        },
        update = { webView ->
            // Check if HTML is present in markdown (for security auditing)
            if (markdown.contains("<") && markdown.contains(">")) {
                Log.w(TAG, "HTML detected in Markdown content - will be sanitized")
            }
            
            // Check if page is loaded
            val pageLoaded = webView.getTag(R.id.webview_page_loaded) as? Boolean ?: false
            
            if (pageLoaded) {
                // Page is loaded, render immediately
                renderMarkdownContent(webView, markdown, isDarkTheme)
            } else {
                // Page not loaded yet, store markdown for later
                webView.setTag(R.id.webview_pending_markdown, markdown)
                Log.d(TAG, "Page not loaded yet, markdown will be rendered after page load")
            }
        }
    )
}

/**
 * JavaScript interface for bidirectional communication between WebView and Kotlin.
 * 
 * SECURITY: This interface is kept minimal with only error reporting.
 * Link handling is done entirely in WebViewClient for single source of truth.
 */
private class WebViewInterface(
    private val onError: (String) -> Unit = {},
    private val onRenderComplete: () -> Unit = {}
) {
    /**
     * Called from JavaScript when a rendering error occurs.
     * 
     * @param message Error description from JavaScript
     */
    @JavascriptInterface
    fun onError(message: String) {
        Log.e(TAG, "WebView rendering error: $message")
        onError.invoke(message)
    }
    
    /**
     * Called from JavaScript when rendering completes successfully.
     */
    @JavascriptInterface
    fun onRenderComplete() {
        Log.d(TAG, "WebView rendering completed")
        onRenderComplete.invoke()
    }
}

/**
 * Render markdown content in WebView
 * 
 * @param webView The WebView instance
 * @param markdown The markdown content to render
 * @param isDarkTheme Whether dark theme is active
 */
private fun renderMarkdownContent(webView: WebView?, markdown: String, isDarkTheme: Boolean) {
    if (webView == null) return
    
    // Escape markdown content for safe JavaScript interpolation
    val escapedMarkdown = escapeForJavaScript(markdown)
    
    // Update theme
    val theme = if (isDarkTheme) "dark" else "light"
    webView.evaluateJavascript(
        "document.documentElement.setAttribute('data-theme', '$theme');",
        null
    )
    
    // Render markdown via JavaScript
    webView.evaluateJavascript(
        "renderMarkdown('$escapedMarkdown');",
        null
    )
    
    Log.d(TAG, "Rendered markdown content (${markdown.length} chars)")
}

/**
 * Escape special characters for safe JavaScript string interpolation.
 * 
 * This prevents JavaScript injection by escaping characters that could
 * break out of a JavaScript string literal.
 * 
 * @param text The text to escape
 * @return Escaped text safe for JavaScript string interpolation
 */
private fun escapeForJavaScript(text: String): String {
    return text
        .replace("\\", "\\\\")  // Backslash must be first
        .replace("'", "\\'")     // Single quote
        .replace("\"", "\\\"")   // Double quote
        .replace("\n", "\\n")    // Newline
        .replace("\r", "\\r")    // Carriage return
        .replace("\t", "\\t")    // Tab
        .replace("\b", "\\b")    // Backspace
        .replace("\u000C", "\\f") // Form feed
}

// Tag IDs for WebView state management
private object R {
    object id {
        const val webview_page_loaded = 0x7f0a0001
        const val webview_pending_markdown = 0x7f0a0002
    }
}

private const val TAG = "MarkdownWebView"
