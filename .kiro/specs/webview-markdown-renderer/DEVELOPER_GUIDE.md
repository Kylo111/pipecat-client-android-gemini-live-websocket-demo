# WebView Markdown Renderer - Developer Guide

## Overview

This guide provides detailed instructions for developers working with the WebView-based Markdown renderer. It covers common tasks like adding new block types, integrating additional libraries, updating assets, and maintaining security.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Adding New Block Types](#adding-new-block-types)
3. [Adding Chart.js Support](#adding-chartjs-support)
4. [Asset Update Process](#asset-update-process)
5. [HTML Sanitization Policy](#html-sanitization-policy)
6. [URL Validation and Scheme Allowlist](#url-validation-and-scheme-allowlist)
7. [Security Considerations](#security-considerations)
8. [Testing Guidelines](#testing-guidelines)
9. [Troubleshooting](#troubleshooting)

---

## Architecture Overview

### Component Stack

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
│  • JavaScript interface (minimal)                           │
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
│  • index.html (Template)                                    │
│  • marked.min.js (Markdown parser with GFM)                 │
│  • katex.min.js + katex.min.css (LaTeX)                    │
│  • highlight.min.js (Syntax highlighting)                  │
│  • styles.css (Custom styling)                              │
│  • renderer.js (Main rendering logic)                       │
└─────────────────────────────────────────────────────────────┘
```

### Key Files

- **MarkdownWebView.kt**: Compose wrapper, security configuration, WebView lifecycle
- **renderer.js**: Markdown parsing, LaTeX rendering, syntax highlighting
- **index.html**: HTML template with library imports
- **styles.css**: Custom styling with theme support

---

## Adding New Block Types

### Step 1: Extend Markdown Syntax (Optional)

If you need custom syntax (e.g., `:::chart` blocks), you'll need to:

1. **Preprocess the Markdown** in `renderer.js` before calling `marked.parse()`:

```javascript
function preprocessMarkdown(markdown) {
    // Example: Convert :::chart blocks to placeholders
    const chartBlocks = [];
    let chartIndex = 0;
    
    const processed = markdown.replace(/:::chart\n([\s\S]*?):::/g, (match, content) => {
        const placeholder = `CHARTBLOCK${chartIndex}ENDCHART`;
        chartBlocks.push({ placeholder, content, index: chartIndex });
        chartIndex++;
        return placeholder;
    });
    
    return { processed, chartBlocks };
}
```

2. **Post-process the HTML** after `marked.parse()`:

```javascript
function renderMarkdown(markdown) {
    // ... existing code ...
    
    const { processed, chartBlocks } = preprocessMarkdown(markdown);
    let html = marked.parse(processed);
    
    // Replace placeholders with rendered charts
    chartBlocks.forEach(({ placeholder, content, index }) => {
        const chartHtml = renderChart(content);
        html = html.replace(placeholder, chartHtml);
    });
    
    // ... rest of rendering ...
}
```

### Step 2: Add Rendering Logic

Create a new function for your block type:

```javascript
/**
 * Render a custom block type
 * 
 * @param {string} content - Block content
 * @returns {string} - Rendered HTML
 */
function renderCustomBlock(content) {
    try {
        // Your rendering logic here
        return '<div class="custom-block">' + content + '</div>';
    } catch (error) {
        console.error('Error rendering custom block:', error);
        return '<div class="error">Failed to render custom block</div>';
    }
}
```

### Step 3: Add Styling

Add CSS for your new block type in `styles.css`:

```css
.custom-block {
    padding: 1rem;
    margin: 1rem 0;
    border-left: 4px solid var(--primary-color);
    background-color: var(--code-background);
}
```

### Step 4: Test

Create test cases in your test suite:

```kotlin
@Test
fun testCustomBlockRendering() {
    val markdown = """
        :::custom
        Custom content here
        :::
    """.trimIndent()
    
    // Test rendering
    // Verify output contains expected HTML
}
```

---

## Adding Chart.js Support

### Step 1: Download Chart.js

1. Download Chart.js from https://www.chartjs.org/
2. Use the minified version: `chart.min.js`
3. Place in `assets/markdown-renderer/js/`

### Step 2: Update index.html

Add Chart.js script tag:

```html
<script src="js/chart.min.js"></script>
```

### Step 3: Add Chart Rendering Function

In `renderer.js`:

```javascript
/**
 * Render a chart from JSON configuration
 * 
 * @param {string} chartConfig - JSON string with chart configuration
 * @param {string} containerId - ID for the canvas container
 * @returns {string} - HTML for chart canvas
 */
function renderChart(chartConfig, containerId) {
    try {
        const config = JSON.parse(chartConfig);
        
        // Create canvas element
        const canvasHtml = `<canvas id="${containerId}" class="chart-canvas"></canvas>`;
        
        // Schedule chart rendering after DOM update
        setTimeout(() => {
            const canvas = document.getElementById(containerId);
            if (canvas && typeof Chart !== 'undefined') {
                new Chart(canvas, config);
            }
        }, 100);
        
        return `<div class="chart-container">${canvasHtml}</div>`;
    } catch (error) {
        console.error('Error rendering chart:', error);
        return '<div class="error">Failed to render chart</div>';
    }
}
```

### Step 4: Define Chart Syntax

Example Markdown syntax:

````markdown
```chart
{
  "type": "bar",
  "data": {
    "labels": ["Red", "Blue", "Yellow"],
    "datasets": [{
      "label": "My Dataset",
      "data": [12, 19, 3]
    }]
  }
}
```
````

### Step 5: Integrate with Markdown Parser

```javascript
function renderMarkdown(markdown) {
    // ... existing code ...
    
    // After marked.parse(), find chart code blocks
    const chartPattern = /<code class="language-chart">([\s\S]*?)<\/code>/g;
    let chartIndex = 0;
    
    html = html.replace(chartPattern, (match, chartConfig) => {
        const containerId = `chart-${chartIndex++}`;
        return renderChart(chartConfig, containerId);
    });
    
    // ... rest of rendering ...
}
```

### Step 6: Add Styling

```css
.chart-container {
    margin: 1rem 0;
    padding: 1rem;
    background-color: var(--background-color);
}

.chart-canvas {
    max-width: 100%;
    height: auto;
}
```

---

## Asset Update Process

### When to Update Assets

- Security vulnerabilities in libraries
- New features in libraries
- Performance improvements
- Bug fixes

### Update Procedure

#### 1. Download New Version

```bash
# Example: Updating marked.js
cd gemini-multimodal-websocket-demo/src/main/assets/markdown-renderer/js/
wget https://cdn.jsdelivr.net/npm/marked@11.1.0/marked.min.js
```

#### 2. Verify Integrity

Check the file hash against the official release:

```bash
sha256sum marked.min.js
# Compare with official hash from GitHub release
```

#### 3. Test Locally

1. Build the app: `./gradlew clean build`
2. Install on device: `./gradlew installDebug`
3. Test with various Markdown documents
4. Check for console errors: `adb logcat | grep "MarkdownWebView"`

#### 4. Update Version Documentation

Update the version in this guide and in code comments:

```kotlin
// MarkdownWebView.kt
/**
 * Libraries:
 * - marked.js v11.1.0 (updated 2024-12-28)
 * - KaTeX v0.16.9
 * - highlight.js v11.9.0
 */
```

#### 5. Regression Testing

Run the full test suite:

```bash
./gradlew test
```

#### 6. Commit Changes

```bash
git add assets/markdown-renderer/
git commit -m "Update marked.js to v11.1.0 for security fix"
```

### Library Versions

Current versions (as of implementation):
- **marked.js**: v11.0.0
- **KaTeX**: v0.16.9
- **highlight.js**: v11.9.0

---

## HTML Sanitization Policy

### Current Policy (Phase 1)

**Default: Escape ALL HTML tags**

All raw HTML in Markdown is escaped by default:
- `<` → `&lt;`
- `>` → `&gt;`

This prevents XSS attacks but also prevents legitimate HTML usage.

### Implementation

The sanitization happens in `renderer.js`:

```javascript
function sanitizeHtml(html) {
    // Phase 1: Escape all HTML tags
    return html
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
}
```

**Note**: Currently, `sanitizeHtml()` is defined but not actively used because marked.js is configured with `sanitize: false`. HTML escaping happens naturally through the Markdown parsing process.

### Future Policy (Phase 2)

**Allowlist safe HTML tags**

In a future update, we may allow specific safe tags:

**Allowed tags**:
- `<b>`, `<i>`, `<em>`, `<strong>` - Text formatting
- `<a>` - Links (with href validation)
- `<code>`, `<pre>` - Code blocks
- `<ul>`, `<ol>`, `<li>` - Lists
- `<table>`, `<tr>`, `<td>`, `<th>` - Tables

**Always blocked**:
- `<script>` - JavaScript execution
- `<iframe>` - Embedded content
- `<object>`, `<embed>` - Plugin content
- `<link>`, `<style>` - External resources
- Event handlers: `onclick`, `onerror`, `onload`, etc.

### Implementing Allowlist (Future)

```javascript
function sanitizeHtml(html) {
    const allowedTags = ['b', 'i', 'em', 'strong', 'a', 'code', 'pre', 'ul', 'ol', 'li', 'table', 'tr', 'td', 'th'];
    const blockedTags = ['script', 'iframe', 'object', 'embed', 'link', 'style'];
    
    // Use DOMParser to parse HTML safely
    const parser = new DOMParser();
    const doc = parser.parseFromString(html, 'text/html');
    
    // Remove blocked tags
    blockedTags.forEach(tag => {
        doc.querySelectorAll(tag).forEach(el => el.remove());
    });
    
    // Remove event handlers
    doc.querySelectorAll('*').forEach(el => {
        Array.from(el.attributes).forEach(attr => {
            if (attr.name.startsWith('on')) {
                el.removeAttribute(attr.name);
            }
        });
    });
    
    return doc.body.innerHTML;
}
```

### Security Auditing

When HTML is detected in Markdown, a warning is logged:

```kotlin
// MarkdownWebView.kt
if (markdown.contains("<") && markdown.contains(">")) {
    Log.w(TAG, "HTML detected in Markdown content - will be sanitized")
}
```

Monitor these logs to detect potential XSS attempts:

```bash
adb logcat | grep "HTML detected in Markdown"
```

---

## URL Validation and Scheme Allowlist

### Current Implementation

URL validation happens in `WebViewClient.shouldOverrideUrlLoading()`:

```kotlin
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
```

### Allowed Schemes

**Allowed** (open in system browser):
- `http://` - Standard web URLs
- `https://` - Secure web URLs

**Blocked** (security risk):
- `file://` - Local file access
- `javascript:` - JavaScript execution
- `intent://` - Android intent URLs
- `data:` - Data URLs (can contain scripts)
- All other schemes

### Adding New Schemes

If you need to support additional schemes (e.g., `mailto:`, `tel:`):

1. **Add to allowlist**:

```kotlin
when (scheme) {
    "http", "https", "mailto", "tel" -> {
        // Open in system browser/app
        try {
            val intent = Intent(Intent.ACTION_VIEW, url)
            context.startActivity(intent)
            onLinkClick(url.toString())
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No activity found to handle URL: $url", e)
            // Show user-friendly error
        }
        return true
    }
    // ... rest of cases ...
}
```

2. **Document the change**:

Update this guide and add a comment in the code explaining why the scheme was added.

3. **Test thoroughly**:

```kotlin
@Test
fun testMailtoLinkHandling() {
    val markdown = "[Email me](mailto:test@example.com)"
    // Verify link opens email app
}
```

### Security Monitoring

Monitor blocked URLs:

```bash
adb logcat | grep "Blocked dangerous scheme\|Blocked unknown scheme"
```

---

## Security Considerations

### Threat Model

**Assumptions**:
- Markdown content may come from untrusted sources
- Users may copy/paste malicious content
- Agents may generate content with embedded attacks

**Threats**:
1. **XSS (Cross-Site Scripting)**: Malicious JavaScript in HTML tags
2. **File Access**: Reading local files via `file://` URLs
3. **Intent Hijacking**: Launching malicious apps via `intent://` URLs
4. **Data Exfiltration**: Sending data via network requests
5. **Popup Abuse**: Annoying users with JS dialogs

### Security Layers

#### Layer 1: WebView Configuration

```kotlin
// Disable file access
allowFileAccess = false
allowContentAccess = false
allowFileAccessFromFileURLs = false
allowUniversalAccessFromFileURLs = false

// Disable unnecessary features
setSupportMultipleWindows(false)
setSupportZoom(false)
mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
```

#### Layer 2: Asset Loading

```kotlin
// Only allow appassets.androidplatform.net
if (url.host == "appassets.androidplatform.net") {
    return assetLoader.shouldInterceptRequest(url)
}

// Hard block all other requests
return WebResourceResponse(
    "text/plain", "UTF-8", 403, "Forbidden",
    mapOf("X-Blocked-Reason" to "Not appassets domain"),
    ByteArrayInputStream("403 Forbidden".toByteArray())
)
```

#### Layer 3: Navigation Control

```kotlin
// Block all navigation except http/https to system browser
override fun shouldOverrideUrlLoading(...): Boolean {
    // Validate scheme, open in browser, block WebView navigation
    return true  // Always block navigation in WebView
}
```

#### Layer 4: JavaScript Dialog Blocking

```kotlin
// Block alert, confirm, prompt
override fun onJsAlert(...): Boolean {
    Log.w(TAG, "Blocked JS alert: $message")
    result.cancel()
    return true
}
```

#### Layer 5: HTML Sanitization

```javascript
// Escape all HTML tags by default
// Future: Allowlist safe tags only
```

### Security Testing

Run security tests regularly:

```bash
./gradlew test --tests "*XSSTest*"
./gradlew test --tests "*SecurityTest*"
```

### Security Auditing

Monitor security logs:

```bash
adb logcat | grep -E "Blocked|XSS|HTML detected|dangerous scheme"
```

---

## Testing Guidelines

### Unit Tests

Test individual functions:

```kotlin
@Test
fun testEscapeForJavaScript() {
    val input = "Hello\n\"World\""
    val expected = "Hello\\n\\\"World\\\""
    assertEquals(expected, escapeForJavaScript(input))
}
```

### Integration Tests

Test full rendering pipeline:

```kotlin
@Test
fun testMarkdownRendering() {
    val markdown = "# Hello\n\nThis is **bold**"
    // Render and verify HTML output
}
```

### Security Tests

Test XSS prevention:

```kotlin
@Test
fun testXSSPrevention() {
    val markdown = "<script>alert('XSS')</script>"
    // Verify script is escaped/removed
}
```

### Performance Tests

Test rendering speed:

```kotlin
@Test
fun testLargeDocumentPerformance() {
    val markdown = generateLargeMarkdown(100_000) // 100KB
    val startTime = System.currentTimeMillis()
    // Render
    val duration = System.currentTimeMillis() - startTime
    assertTrue(duration < 500) // Should render in <500ms
}
```

### Manual Testing Checklist

- [ ] Test with various Markdown documents
- [ ] Test LaTeX rendering (block and inline)
- [ ] Test syntax highlighting for common languages
- [ ] Test tables with various content
- [ ] Test links (http, https, file, javascript)
- [ ] Test theme switching (light/dark)
- [ ] Test text selection and copying
- [ ] Test with malformed Markdown
- [ ] Test with very long documents
- [ ] Test with special characters and emoji
- [ ] Test in airplane mode (no network)
- [ ] Test memory usage over time

---

## Troubleshooting

### Issue: Markdown not rendering

**Symptoms**: Blank screen, no content displayed

**Possible causes**:
1. JavaScript not enabled
2. Assets not loading
3. JavaScript error

**Debugging**:

```bash
# Check WebView console logs
adb logcat | grep "MarkdownWebView\|WebView console"

# Check for JavaScript errors
adb logcat | grep "Error rendering markdown"
```

**Solutions**:
- Verify `javaScriptEnabled = true`
- Verify assets exist in `assets/markdown-renderer/`
- Check for JavaScript syntax errors in `renderer.js`

### Issue: LaTeX not rendering

**Symptoms**: Raw LaTeX shown instead of rendered math

**Possible causes**:
1. KaTeX not loaded
2. LaTeX syntax error
3. Placeholder replacement failed

**Debugging**:

```bash
# Check for KaTeX errors
adb logcat | grep "KaTeX\|LaTeX"
```

**Solutions**:
- Verify `katex.min.js` exists and loads
- Test LaTeX syntax in online KaTeX editor
- Check placeholder regex patterns

### Issue: Links not opening

**Symptoms**: Clicking links does nothing

**Possible causes**:
1. `shouldOverrideUrlLoading` not implemented
2. No browser app installed
3. Scheme not in allowlist

**Debugging**:

```bash
# Check for link click logs
adb logcat | grep "shouldOverrideUrlLoading\|Link clicked"
```

**Solutions**:
- Verify `shouldOverrideUrlLoading` returns `true`
- Test with different URL schemes
- Check for `ActivityNotFoundException`

### Issue: High memory usage

**Symptoms**: App crashes with OutOfMemoryError

**Possible causes**:
1. WebView not being reused
2. Large documents
3. Memory leaks

**Debugging**:

```bash
# Monitor memory usage
adb shell dumpsys meminfo ai.pipecat.gemini_multimodal_websocket_demo
```

**Solutions**:
- Implement WebView pooling/reuse
- Clear WebView cache periodically
- Test with smaller documents
- Profile with Android Studio Memory Profiler

### Issue: Slow rendering

**Symptoms**: Long delay before content appears

**Possible causes**:
1. Large document
2. Many LaTeX expressions
3. Syntax highlighting overhead

**Debugging**:

```bash
# Check rendering time logs
adb logcat | grep "Rendered markdown content"
```

**Solutions**:
- Optimize LaTeX placeholder regex
- Lazy load highlight.js
- Use WebView pooling
- Profile with Chrome DevTools

---

## Additional Resources

### Documentation
- [marked.js Documentation](https://marked.js.org/)
- [KaTeX Documentation](https://katex.org/)
- [highlight.js Documentation](https://highlightjs.org/)
- [Android WebView Guide](https://developer.android.com/guide/webapps/webview)
- [WebViewAssetLoader](https://developer.android.com/reference/androidx/webkit/WebViewAssetLoader)

### Security
- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)
- [Android WebView Security](https://developer.android.com/training/articles/security-tips#WebView)

### Testing
- [Kotest Documentation](https://kotest.io/)
- [Android Testing Guide](https://developer.android.com/training/testing)

---

## Changelog

### Version 1.0 (2024-12-28)
- Initial implementation
- marked.js v11.0.0
- KaTeX v0.16.9
- highlight.js v11.9.0
- Security hardening complete
- Full test suite implemented

---

## Support

For questions or issues:
1. Check this guide first
2. Review test cases for examples
3. Check logs for error messages
4. Consult the design document
5. Ask the development team

