# Design Document

## Overview

This design describes the migration from a custom Kotlin-based Markdown parser to a WebView-based renderer using industry-standard JavaScript libraries (marked.js + KaTeX). The new architecture will provide professional-grade rendering matching ChatGPT/Claude quality while maintaining performance and enabling future features like charts and diagrams.

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     NotesScreen.kt                          │
│                  (Compose UI Layer)                         │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              MarkdownWebView.kt                             │
│           (Compose AndroidView Wrapper)                     │
│  • WebViewAssetLoader configuration                         │
│  • JavaScript interface                                     │
│  • Theme synchronization                                    │
│  • Navigation interception                                  │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                  Android WebView                            │
│              (Native Browser Component)                     │
│  • Loads via https://appassets.androidplatform.net/         │
│  • File access disabled for security                        │
│  • Handles own scrolling                                    │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│          assets/markdown-renderer/                          │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  index.html (Template)                               │  │
│  │  • marked.min.js (Markdown parser with GFM)          │  │
│  │  • katex.min.js + katex.min.css (LaTeX)             │  │
│  │  • highlight.min.js (Syntax highlighting)           │  │
│  │  • styles.css (Custom styling)                       │  │
│  │  • renderer.js (Main rendering logic)               │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Component Interaction Flow

```
User opens report
       │
       ▼
NotesScreen loads markdown content
       │
       ▼
MarkdownWebView created with content
       │
       ▼
WebViewAssetLoader configured (appassets.androidplatform.net)
       │
       ▼
WebView loads index.html via HTTPS URL
       │
       ▼
JavaScript libraries initialize
       │
       ▼
renderMarkdown(content) called via evaluateJavascript
       │
       ▼
marked.js parses Markdown (GFM mode) → HTML
       │
       ▼
HTML sanitized/escaped if needed
       │
       ▼
KaTeX renders LaTeX expressions
       │
       ▼
highlight.js applies syntax highlighting
       │
       ▼
Content displayed to user (WebView handles scrolling)
       │
       ▼
User clicks link → WebViewClient intercepts
       │
       ▼
Link opened in system browser (not in WebView)
```

## Components and Interfaces

### 1. MarkdownWebView (Kotlin)

**Role:** Compose wrapper for Android WebView that manages rendering lifecycle

**Main Fields:**
- `markdown: String` - The Markdown content to render
- `modifier: Modifier` - Compose modifier for layout (use fillMaxHeight() or weight(1f))
- `onLinkClick: (String) -> Unit` - Callback for link clicks
- `assetLoader: WebViewAssetLoader` - Secure asset loader for HTTPS URLs

**Main Methods:**

#### `MarkdownWebView(markdown: String, modifier: Modifier, onLinkClick: (String) -> Unit)`
**Role:** Composable function that creates and manages WebView
**Preconditions:** Valid Markdown string
**Parameters:**
- `markdown`: Markdown content to render
- `modifier`: Layout modifier
- `onLinkClick`: Callback when user clicks a link
**Returns:** Unit (Composable)
**Postconditions:** WebView rendered with content
**Side-effects:** Creates WebView, loads HTML, evaluates JavaScript
**Code Reference:** `MarkdownWebView.kt:15-80`

#### `escapeForJavaScript(text: String): String`
**Role:** Escape special characters for safe JavaScript string interpolation
**Preconditions:** Non-null string
**Parameters:**
- `text`: String to escape
**Returns:** Escaped string safe for JavaScript
**Postconditions:** All special characters properly escaped
**Code Reference:** `MarkdownWebView.kt:85-95`

### 2. WebViewInterface (Kotlin)

**Role:** JavaScript interface for bidirectional communication (minimal API for security)

**Main Methods:**

#### `@JavascriptInterface onError(message: String)`
**Role:** Called from JavaScript when rendering error occurs
**Preconditions:** Non-null error message
**Parameters:**
- `message`: Error description
**Returns:** Unit
**Postconditions:** Error logged
**Side-effects:** Logs to Android logcat
**Code Reference:** `MarkdownWebView.kt:117-120`

**Note:** Link handling is done entirely in WebViewClient.shouldOverrideUrlLoading for single source of truth. JavaScript bridge is kept minimal for security.

### 3. renderer.js (JavaScript)

**Role:** Main rendering logic coordinating marked.js and KaTeX

**Main Functions:**

#### `renderMarkdown(markdown: String): void`
**Role:** Parse and render Markdown with LaTeX support and HTML sanitization
**Preconditions:** marked.js and KaTeX loaded
**Parameters:**
- `markdown`: Raw Markdown string
**Returns:** void
**Postconditions:** Content rendered in DOM with HTML sanitized
**Side-effects:** Updates #content div, sanitizes HTML if present
**HTML Policy:** By default, escape ALL raw HTML tags in Markdown to prevent XSS. Future: configurable allowlist for safe tags (e.g., <b>, <i>, <em>, <strong>).
**Code Reference:** `assets/markdown-renderer/renderer.js:15-45`

#### `configureMarked(): void`
**Role:** Configure marked.js with GFM extensions and custom renderer
**Preconditions:** marked.js loaded
**Returns:** void
**Postconditions:** marked.js configured with GFM (tables, strikethrough, task lists)
**Code Reference:** `assets/markdown-renderer/renderer.js:50-75`

#### `renderLatex(): void`
**Role:** Find and render all LaTeX expressions using KaTeX
**Preconditions:** KaTeX loaded, content in DOM
**Returns:** void
**Postconditions:** All LaTeX rendered
**Side-effects:** Modifies DOM, may throw on invalid LaTeX
**Code Reference:** `assets/markdown-renderer/renderer.js:80-100`

#### `sanitizeHtml(html: String): String`
**Role:** Escape or remove potentially dangerous HTML tags
**Preconditions:** HTML string
**Parameters:**
- `html`: HTML string to sanitize
**Returns:** Sanitized HTML string
**Postconditions:** XSS-vulnerable tags removed/escaped
**Policy:** 
- **Default (Phase 1):** Escape ALL raw HTML tags (convert < to &lt;, > to &gt;)
- **Future (Phase 2):** Allowlist safe tags: <b>, <i>, <em>, <strong>, <a>, <code>, <pre>
- **Always block:** <script>, <iframe>, <object>, <embed>, <link>, <style>, event handlers (onclick, onerror, etc.)
**Code Reference:** `assets/markdown-renderer/renderer.js:105-120`

### 4. styles.css

**Role:** Custom styling for rendered content

**Key Styles:**
- Typography: Font families, sizes, line heights
- Colors: Light/dark mode support via CSS variables
- Layout: Margins, padding, max-width
- Components: Tables, code blocks, blockquotes
- LaTeX: Display math centering, inline math baseline
- Links: Color, hover states, underline

**Code Reference:** `assets/markdown-renderer/styles.css`

## Data Models

### MarkdownContent
```kotlin
data class MarkdownContent(
    val raw: String,              // Original Markdown
    val metadata: ContentMetadata? = null
)
```

### ContentMetadata
```kotlin
data class ContentMetadata(
    val title: String,
    val created: Long,
    val modified: Long,
    val tags: List<String>
)
```

### RenderConfig
```kotlin
data class RenderConfig(
    val enableLatex: Boolean = true,
    val enableSyntaxHighlight: Boolean = true,
    val enableTables: Boolean = true,
    val enableGfmExtensions: Boolean = true,  // Tables, strikethrough, task lists
    val htmlSanitizationMode: HtmlSanitizationMode = HtmlSanitizationMode.ESCAPE_ALL
)

enum class HtmlSanitizationMode {
    ESCAPE_ALL,           // Escape all HTML tags (safest, default)
    ALLOWLIST_SAFE_TAGS,  // Allow safe formatting tags only (future)
    DISABLED              // No sanitization (dangerous, for testing only)
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Markdown Parsing Completeness
*For any* valid CommonMark + GFM Markdown document, parsing and rendering should produce HTML that represents all elements without loss of information.
**Validates: Requirements 1.2**

### Property 2: LaTeX Rendering Correctness
*For any* valid LaTeX expression supported by KaTeX, rendering should produce mathematically correct output matching KaTeX's reference implementation.
**Validates: Requirements 2.1, 2.2, 2.4**

### Property 3: Link Navigation Security
*For any* clickable link in rendered content, clicking it should open in the system browser and never navigate within the WebView.
**Validates: Requirements 5.2, 13.2, 13.3**

### Property 4: Asset Loading Security
*For any* app installation, all assets should load via WebViewAssetLoader using HTTPS URLs (appassets.androidplatform.net) with file access disabled.
**Validates: Requirements 6.2, 6.3, 13.1**

### Property 5: HTML Sanitization
*For any* Markdown containing HTML tags, the HTML should be escaped or sanitized to prevent XSS attacks.
**Validates: Requirements 13.4, 14.1, 14.5**

### Property 6: Error Isolation
*For any* rendering error (invalid LaTeX, malformed Markdown), the error should be contained without crashing the WebView or app.
**Validates: Requirements 11.1, 11.2, 11.3**

### Property 7: Theme Consistency
*For any* theme change (light/dark mode), the WebView content should update to match the new theme within 100ms.
**Validates: Requirements 1.5**

### Property 8: Performance Bounds
*For any* document under 100KB, initial rendering should complete within 500ms on devices with 4GB+ RAM.
**Validates: Requirements 9.1**

### Property 9: Backward Compatibility
*For any* note created with the old parser, rendering with the new WebView should produce visually equivalent output.
**Validates: Requirements 8.1, 8.2**

### Property 10: Text Selection Preservation
*For any* text selection in the WebView, the selected text should match the underlying Markdown content character-for-character (as plain text).
**Validates: Requirements 12.1, 12.2, 12.3**

## Error Handling

### JavaScript Errors
- **Detection:** WebView console messages captured via WebChromeClient
- **Handling:** Log to Android logcat with ERROR level
- **Recovery:** Display error message in WebView, allow retry
- **User Impact:** Minimal - error shown inline, app continues

### LaTeX Rendering Errors
- **Detection:** KaTeX throws exception on invalid syntax
- **Handling:** Catch in try-catch, render raw LaTeX with error styling
- **Recovery:** User can see what failed, can edit source
- **User Impact:** Low - invalid LaTeX shown as text

### Asset Loading Failures
- **Detection:** WebView onReceivedError callback
- **Handling:** Log error, attempt reload once
- **Recovery:** Fall back to plain text display
- **User Impact:** Medium - no formatting but content readable

### Height Calculation Failures
- **Detection:** Height = 0 or unreasonably large (>10000px)
- **Handling:** Use default height (800dp)
- **Recovery:** User can scroll, content not clipped
- **User Impact:** Low - slight layout issue

### Memory Pressure
- **Detection:** onTrimMemory callback
- **Handling:** Clear WebView cache, release unused instances
- **Recovery:** Reload content when memory available
- **User Impact:** Low - brief loading delay

## Testing Strategy

### Unit Tests
- **Markdown Escaping:** Test escapeForJavaScript with special characters
- **Height Calculation:** Test height bounds (min/max)
- **Link Parsing:** Test URL extraction from click events
- **Error Messages:** Test error formatting

### Property-Based Tests (Kotest)
- **Property 1:** Generate random valid Markdown, verify no parsing errors
- **Property 2:** Generate random LaTeX expressions, verify KaTeX accepts them
- **Property 3:** Generate documents of varying sizes, verify height accuracy
- **Property 6:** Generate invalid Markdown/LaTeX, verify graceful degradation
- **Property 9:** Load old notes, verify visual equivalence

### Integration Tests
- **Full Rendering:** Load sample reports, verify all elements render
- **Link Clicking:** Simulate clicks, verify callbacks invoked
- **Theme Switching:** Toggle theme, verify CSS updates
- **Performance:** Measure rendering time for various document sizes

### Manual Testing
- **Visual Inspection:** Compare rendering to ChatGPT/Claude
- **Scrolling:** Test smooth scrolling on various devices
- **Copy/Paste:** Verify text selection and clipboard
- **Edge Cases:** Very long documents, complex LaTeX, nested tables

### Test Configuration
- Minimum 100 iterations per property test
- Test on devices: 4GB RAM, 8GB RAM, 12GB RAM
- Test on Android versions: 8.0, 10.0, 13.0, 14.0
- Performance baseline: Pixel 6 (mid-range 2021 device)

## Implementation Notes

### Asset Preparation
1. Download marked.js v11.0.0 (minified)
2. Download KaTeX v0.16.9 (minified JS + CSS + fonts)
3. Download highlight.js v11.9.0 (minified, common languages)
4. Create custom styles.css with Material3 colors
5. Bundle all in `assets/markdown-renderer/`

### WebView Configuration
```kotlin
// Create WebViewAssetLoader for secure HTTPS asset loading
val assetLoader = WebViewAssetLoader.Builder()
    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
    .build()

webView.settings.apply {
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
    
    // Cache policy: LOAD_DEFAULT is fine since we block network in shouldInterceptRequest
    // appassets are cached automatically by WebViewAssetLoader
    cacheMode = WebSettings.LOAD_DEFAULT
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
}

// SECURITY: Block JS dialogs and popups
webView.webChromeClient = object : WebChromeClient() {
    override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
        Log.w(TAG, "Blocked JS alert: $message")
        result.cancel()
        return true  // Block alert
    }
    
    override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
        Log.w(TAG, "Blocked JS confirm: $message")
        result.cancel()
        return true  // Block confirm
    }
    
    override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult): Boolean {
        Log.w(TAG, "Blocked JS prompt: $message")
        result.cancel()
        return true  // Block prompt
    }
    
    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        Log.d(TAG, "WebView console: ${consoleMessage.message()}")
        return true
    }
}

// Intercept asset requests and serve via HTTPS
webView.webViewClient = object : WebViewClient() {
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
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "No activity found to handle URL: $url", e)
                    // Show user-friendly error
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

// Load via HTTPS URL instead of file://
webView.loadUrl("https://appassets.androidplatform.net/assets/markdown-renderer/index.html")
```

### Security Considerations
- **File Access:** Disabled (allowFileAccess = false) to prevent file:// URL vulnerabilities
- **Asset Loading:** Use WebViewAssetLoader to serve assets via HTTPS (appassets.androidplatform.net)
  - Block all requests outside appassets.androidplatform.net domain
  - Return 403 Forbidden for unauthorized requests (hard block, no network fallback)
  - No network fetches by design
- **Navigation Control:** 
  - Block all navigation in WebView via shouldOverrideUrlLoading
  - Allowlist only http/https schemes for external browser
  - Block dangerous schemes: file://, javascript://, intent://, data://
  - Validate URLs before opening with try/catch for ActivityNotFoundException
- **HTML Sanitization:** 
  - Default: Escape ALL raw HTML tags in Markdown
  - Future: Configurable allowlist for safe tags
  - Always block: <script>, <iframe>, <object>, <embed>, event handlers
- **JavaScript Bridge:** 
  - Minimal interface with only onError() method
  - Link handling done entirely in WebViewClient (single source of truth)
  - No navigation or URL handling in JavaScript
- **JavaScript Dialogs:**
  - Block all JS dialogs: alert(), confirm(), prompt()
  - Prevent popup abuse and UX disruption
  - Log blocked attempts for security auditing
- **WebView Settings:**
  - Disable multiple windows, zoom controls
  - Cache policy: LOAD_DEFAULT (appassets cached automatically, network blocked in shouldInterceptRequest)
  - Block mixed content (HTTPS only)
  - Disable universal access to prevent cross-origin attacks
- **Threat Model:** Assume Markdown content may contain malicious HTML/JavaScript from external sources or compromised agents

### Performance Optimizations
- Reuse single WebView instance per screen
- Lazy load highlight.js (only if code blocks present)
- Use minified assets (marked, KaTeX, highlight.js)
- Cache HTML template in memory
- Let WebView handle its own scrolling (use fillMaxHeight() or weight(1f) in Compose)
- Cache policy: LOAD_DEFAULT with network blocked in shouldInterceptRequest (appassets cached automatically)

### Future Extensibility
- Chart.js integration: Add `renderChart(type, data)` function
- Mermaid diagrams: Add `renderDiagram(mermaid)` function
- Interactive elements: Add event listeners for custom blocks
- Export: Add `exportToPDF()` and `exportToImage()` functions

## Migration Strategy

### Phase 1: Parallel Implementation
- Keep old `MarkdownText.kt` intact
- Implement new `MarkdownWebView.kt` alongside
- Add feature flag to switch between renderers
- Test with subset of users

### Phase 2: Gradual Rollout
- Enable WebView for new notes only
- Monitor performance metrics
- Collect user feedback
- Fix issues before full migration

### Phase 3: Full Migration
- Switch all notes to WebView renderer
- Remove old parser code
- Update documentation
- Celebrate! 🎉

### Rollback Plan
- Keep old parser code for 2 releases
- Feature flag allows instant rollback
- Database unchanged (Markdown is Markdown)
- No data migration needed
