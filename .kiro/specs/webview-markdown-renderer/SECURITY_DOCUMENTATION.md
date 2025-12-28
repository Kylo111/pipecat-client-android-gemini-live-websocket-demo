# WebView Markdown Renderer - Security Documentation

## Overview

This document provides comprehensive security documentation for the WebView-based Markdown renderer, including threat model, security controls, configuration details, and testing procedures.

## Table of Contents

1. [Threat Model](#threat-model)
2. [Security Architecture](#security-architecture)
3. [WebView Security Settings](#webview-security-settings)
4. [Request Blocking (Hard-Block)](#request-blocking-hard-block)
5. [Navigation Control](#navigation-control)
6. [JavaScript Dialog Blocking](#javascript-dialog-blocking)
7. [HTML Sanitization](#html-sanitization)
8. [URL Validation](#url-validation)
9. [Security Testing](#security-testing)
10. [Incident Response](#incident-response)

---

## Threat Model

### Assumptions

1. **Untrusted Content**: Markdown content may come from:
   - External sources (copied from web)
   - AI agents (potentially compromised)
   - User input (malicious or accidental)
   - Synchronized data (LibreChat, cloud storage)

2. **Attack Vectors**:
   - XSS (Cross-Site Scripting) via HTML injection
   - File access via `file://` URLs
   - Intent hijacking via `intent://` URLs
   - Data exfiltration via network requests
   - Popup abuse via JavaScript dialogs
   - Navigation hijacking via malicious links

3. **Assets to Protect**:
   - User data (notes, conversations, API keys)
   - Device files and storage
   - User privacy and security
   - App stability and performance

### Threat Actors

1. **Malicious Content Creator**: Intentionally crafts XSS payloads
2. **Compromised Agent**: AI agent generates malicious content
3. **Accidental User**: User copies malicious content unknowingly
4. **Network Attacker**: MITM attack on content delivery (mitigated by HTTPS)

### Attack Scenarios

#### Scenario 1: Script Injection
**Attack**: User copies Markdown with embedded `<script>` tags
**Impact**: JavaScript execution, data theft, UI manipulation
**Mitigation**: HTML escaping, script tag removal

#### Scenario 2: File Access
**Attack**: Malicious link with `file://` protocol
**Impact**: Access to local files, data exfiltration
**Mitigation**: File access disabled, scheme allowlist

#### Scenario 3: Intent Hijacking
**Attack**: Link with `intent://` protocol
**Impact**: Launch malicious apps, phishing
**Mitigation**: Scheme allowlist, intent blocking

#### Scenario 4: Popup Abuse
**Attack**: JavaScript `alert()` spam
**Impact**: User annoyance, denial of service
**Mitigation**: JavaScript dialog blocking

#### Scenario 5: Network Exfiltration
**Attack**: Embedded image/iframe to external server
**Impact**: Data exfiltration, tracking
**Mitigation**: Network request hard-block

---

## Security Architecture

### Defense in Depth

The renderer implements multiple layers of security:

```
┌─────────────────────────────────────────────────────────────┐
│ Layer 1: WebView Configuration                              │
│ • File access disabled                                      │
│ • Unnecessary features disabled                             │
│ • Mixed content blocked                                     │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ Layer 2: Asset Loading Control                              │
│ • WebViewAssetLoader (HTTPS only)                           │
│ • appassets.androidplatform.net domain                      │
│ • Hard-block non-appassets requests (403 Forbidden)         │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ Layer 3: Navigation Control                                 │
│ • Block all WebView navigation                              │
│ • Scheme allowlist (http, https only)                       │
│ • Open links in system browser                              │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ Layer 4: JavaScript Control                                 │
│ • Block JS dialogs (alert, confirm, prompt)                 │
│ • Minimal JS interface (error reporting only)               │
│ • Console logging for debugging                             │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ Layer 5: Content Sanitization                               │
│ • HTML escaping (default)                                   │
│ • Future: Tag allowlist                                     │
│ • XSS prevention                                            │
└─────────────────────────────────────────────────────────────┘
```

### Security Principles

1. **Least Privilege**: Only enable necessary features
2. **Defense in Depth**: Multiple security layers
3. **Fail Secure**: Block by default, allow explicitly
4. **Audit Trail**: Log all security events
5. **Simplicity**: Minimal attack surface

---

## WebView Security Settings

### Complete Settings Documentation

```kotlin
settings.apply {
    // ============================================================
    // JAVASCRIPT SETTINGS
    // ============================================================
    
    // Enable JavaScript (REQUIRED for marked.js and KaTeX)
    // Risk: JavaScript execution
    // Mitigation: Content sanitization, dialog blocking
    javaScriptEnabled = true
    
    // Enable DOM storage (REQUIRED for marked.js)
    // Risk: Data persistence
    // Mitigation: Isolated storage, no sensitive data
    domStorageEnabled = true
    
    // ============================================================
    // FILE ACCESS SETTINGS (CRITICAL SECURITY)
    // ============================================================
    
    // Disable file access (PREVENTS file:// URLs)
    // This is the PRIMARY defense against local file access
    allowFileAccess = false
    
    // Disable content access (PREVENTS content:// URLs)
    allowContentAccess = false
    
    // Disable file access from file URLs (PREVENTS CORS bypass)
    allowFileAccessFromFileURLs = false
    
    // Disable universal access from file URLs (PREVENTS CORS bypass)
    allowUniversalAccessFromFileURLs = false
    
    // ============================================================
    // FEATURE DISABLING (REDUCE ATTACK SURFACE)
    // ============================================================
    
    // Disable multiple windows (PREVENTS popup abuse)
    setSupportMultipleWindows(false)
    
    // Disable zoom (PREVENTS UI manipulation)
    setSupportZoom(false)
    builtInZoomControls = false
    displayZoomControls = false
    
    // ============================================================
    // NETWORK AND CACHING
    // ============================================================
    
    // Cache policy: LOAD_DEFAULT
    // Network requests are blocked in shouldInterceptRequest
    // appassets are cached automatically by WebViewAssetLoader
    cacheMode = WebSettings.LOAD_DEFAULT
    
    // Block mixed content (HTTPS only)
    // Prevents loading HTTP resources in HTTPS page
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    
    // ============================================================
    // RENDERING SETTINGS
    // ============================================================
    
    // High render priority for smooth scrolling
    setRenderPriority(WebSettings.RenderPriority.HIGH)
    
    // Consistent text size for selection
    textZoom = 100
}
```

### Settings Verification Checklist

- [x] `javaScriptEnabled = true` (required for rendering)
- [x] `domStorageEnabled = true` (required for marked.js)
- [x] `allowFileAccess = false` (CRITICAL)
- [x] `allowContentAccess = false` (CRITICAL)
- [x] `allowFileAccessFromFileURLs = false` (CRITICAL)
- [x] `allowUniversalAccessFromFileURLs = false` (CRITICAL)
- [x] `setSupportMultipleWindows(false)` (security)
- [x] `setSupportZoom(false)` (security)
- [x] `builtInZoomControls = false` (security)
- [x] `displayZoomControls = false` (security)
- [x] `cacheMode = LOAD_DEFAULT` (performance)
- [x] `mixedContentMode = MIXED_CONTENT_NEVER_ALLOW` (security)

---

## Request Blocking (Hard-Block)

### Implementation

```kotlin
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
```

### Why Hard-Block (403) Instead of Null?

**Returning `null`**:
- WebView may attempt network fallback
- May try alternative protocols
- Unpredictable behavior

**Returning 403 Forbidden**:
- Explicit rejection
- No fallback attempts
- Clear error message
- Auditable (logged)

### Allowed Requests

**ONLY** requests to `appassets.androidplatform.net` are allowed:
- `https://appassets.androidplatform.net/assets/markdown-renderer/index.html`
- `https://appassets.androidplatform.net/assets/markdown-renderer/js/marked.min.js`
- `https://appassets.androidplatform.net/assets/markdown-renderer/js/katex.min.js`
- `https://appassets.androidplatform.net/assets/markdown-renderer/js/highlight.min.js`
- `https://appassets.androidplatform.net/assets/markdown-renderer/js/renderer.js`
- `https://appassets.androidplatform.net/assets/markdown-renderer/css/styles.css`
- `https://appassets.androidplatform.net/assets/markdown-renderer/css/katex.min.css`
- `https://appassets.androidplatform.net/assets/markdown-renderer/fonts/*`

### Blocked Requests

**ALL** other requests are blocked with 403:
- External URLs (http://, https://)
- File URLs (file://)
- Data URLs (data:)
- Intent URLs (intent://)
- Any other protocol

### Testing Hard-Block

```kotlin
@Test
fun testNetworkRequestBlocking() {
    val markdown = """
        ![External Image](https://evil.com/image.png)
        <img src="http://evil.com/track.gif">
    """.trimIndent()
    
    // Render markdown
    // Verify requests are blocked
    // Check logs for "Blocked unauthorized request"
}
```

### Monitoring Blocked Requests

```bash
# Monitor blocked requests in real-time
adb logcat | grep "Blocked unauthorized request"

# Example output:
# W/MarkdownWebView: Blocked unauthorized request: https://evil.com/image.png
# W/MarkdownWebView: Blocked unauthorized request: http://evil.com/track.gif
```

---

## Navigation Control

### Implementation

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

### Navigation Rules

1. **ALWAYS return `true`**: Block ALL navigation in WebView
2. **Allowlist schemes**: Only http/https allowed
3. **Open externally**: Use system browser for allowed schemes
4. **Block dangerous**: file, javascript, intent, data
5. **Block unknown**: Any other scheme

### Scheme Allowlist

**Allowed** (open in system browser):
- `http://` - Standard web URLs
- `https://` - Secure web URLs

**Blocked** (security risk):
- `file://` - Local file access
- `javascript:` - JavaScript execution
- `intent://` - Android intent URLs
- `data:` - Data URLs (can contain scripts)
- `about:` - Browser internal pages
- `blob:` - Blob URLs
- `content:` - Content provider URLs
- All other schemes

### Testing Navigation

```kotlin
@Test
fun testNavigationBlocking() {
    val testCases = listOf(
        "http://example.com" to true,  // Should open in browser
        "https://example.com" to true, // Should open in browser
        "file:///etc/passwd" to false, // Should be blocked
        "javascript:alert('XSS')" to false, // Should be blocked
        "intent://scan" to false, // Should be blocked
        "data:text/html,<script>alert('XSS')</script>" to false // Should be blocked
    )
    
    testCases.forEach { (url, shouldOpen) ->
        // Test navigation
        // Verify behavior matches expected
    }
}
```

---

## JavaScript Dialog Blocking

### Implementation

```kotlin
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
```

### Why Block JavaScript Dialogs?

1. **Popup Abuse**: Malicious content can spam dialogs
2. **Denial of Service**: Infinite alert loops
3. **Phishing**: Fake login prompts
4. **User Annoyance**: Unwanted interruptions
5. **Security**: Prevent social engineering

### Blocked Dialogs

- `alert()` - Information dialogs
- `confirm()` - Confirmation dialogs
- `prompt()` - Input dialogs

### Allowed Dialogs

- None (all blocked for security)

### Console Logging

Console messages are logged for debugging:
- `console.log()` → Android Log.d()
- `console.warn()` → Android Log.d()
- `console.error()` → Android Log.d()

### Testing Dialog Blocking

```kotlin
@Test
fun testJavaScriptDialogBlocking() {
    val markdown = """
        <script>
        alert('This should be blocked');
        confirm('This should be blocked');
        prompt('This should be blocked');
        </script>
    """.trimIndent()
    
    // Render markdown
    // Verify no dialogs appear
    // Check logs for "Blocked JS alert/confirm/prompt"
}
```

### Monitoring Blocked Dialogs

```bash
# Monitor blocked dialogs
adb logcat | grep "Blocked JS"

# Example output:
# W/MarkdownWebView: Blocked JS alert: This should be blocked
# W/MarkdownWebView: Blocked JS confirm: This should be blocked
# W/MarkdownWebView: Blocked JS prompt: This should be blocked
```

---

## HTML Sanitization

### Current Policy (Phase 1)

**Default: Escape ALL HTML tags**

All raw HTML in Markdown is escaped:
- `<` → `&lt;`
- `>` → `&gt;`

This happens naturally through the Markdown parsing process.

### Detection and Logging

```kotlin
// Check if HTML is present in markdown (for security auditing)
if (markdown.contains("<") && markdown.contains(">")) {
    Log.w(TAG, "HTML detected in Markdown content - will be sanitized")
}
```

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

### Testing HTML Sanitization

See [XSS_TEST_CORPUS.md](XSS_TEST_CORPUS.md) for comprehensive test cases.

---

## URL Validation

### Validation Layer

URL validation happens in `shouldOverrideUrlLoading()`:

1. **Extract scheme**: `url.scheme?.lowercase()`
2. **Check allowlist**: `when (scheme) { ... }`
3. **Open or block**: Based on scheme
4. **Log decision**: All blocks logged

### Validation Rules

```kotlin
when (scheme) {
    "http", "https" -> {
        // ALLOWED: Open in system browser
        try {
            val intent = Intent(Intent.ACTION_VIEW, url)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Handle error gracefully
            Log.e(TAG, "No activity found to handle URL: $url", e)
        }
        return true
    }
    "file", "javascript", "intent", "data" -> {
        // BLOCKED: Dangerous schemes
        Log.w(TAG, "Blocked dangerous scheme: $scheme for URL: $url")
        return true
    }
    else -> {
        // BLOCKED: Unknown schemes
        Log.w(TAG, "Blocked unknown scheme: $scheme for URL: $url")
        return true
    }
}
```

### Error Handling

```kotlin
try {
    val intent = Intent(Intent.ACTION_VIEW, url)
    context.startActivity(intent)
} catch (e: ActivityNotFoundException) {
    // No app can handle this URL
    Log.e(TAG, "No activity found to handle URL: $url", e)
    // Could show user-friendly error message
}
```

### Testing URL Validation

```kotlin
@Test
fun testURLValidation() {
    val testCases = mapOf(
        "http://example.com" to URLValidationResult.ALLOWED,
        "https://example.com" to URLValidationResult.ALLOWED,
        "file:///etc/passwd" to URLValidationResult.BLOCKED_DANGEROUS,
        "javascript:alert('XSS')" to URLValidationResult.BLOCKED_DANGEROUS,
        "intent://scan" to URLValidationResult.BLOCKED_DANGEROUS,
        "data:text/html,<script>" to URLValidationResult.BLOCKED_DANGEROUS,
        "unknown://test" to URLValidationResult.BLOCKED_UNKNOWN
    )
    
    testCases.forEach { (url, expected) ->
        val result = validateURL(url)
        assertEquals(expected, result)
    }
}
```

---

## Security Testing

### Test Categories

1. **XSS Prevention**: See [XSS_TEST_CORPUS.md](XSS_TEST_CORPUS.md)
2. **Navigation Security**: URL scheme validation
3. **Request Blocking**: Network request hard-block
4. **Dialog Blocking**: JavaScript dialog prevention
5. **File Access**: File URL blocking

### Running Security Tests

```bash
# Run all security tests
./gradlew test --tests "*Security*"

# Run XSS tests
./gradlew test --tests "*XSSTest*"

# Run navigation tests
./gradlew test --tests "*NavigationTest*"

# Run with verbose output
./gradlew test --tests "*Security*" --info
```

### Manual Security Testing

1. **Test XSS vectors** from corpus
2. **Test file:// URLs** in links
3. **Test javascript: URLs** in links
4. **Test intent:// URLs** in links
5. **Test data: URLs** in images
6. **Test JS dialogs** (alert, confirm, prompt)
7. **Test network requests** (external images)
8. **Test in airplane mode** (should work)

### Security Audit Checklist

- [ ] All WebView settings verified
- [ ] Request blocking tested
- [ ] Navigation control tested
- [ ] Dialog blocking tested
- [ ] XSS corpus passed
- [ ] File access blocked
- [ ] Network requests blocked
- [ ] Logs reviewed for security events

---

## Incident Response

### Detection

Monitor logs for security events:

```bash
# Monitor all security events
adb logcat | grep -E "Blocked|HTML detected|XSS|dangerous scheme"

# Monitor specific events
adb logcat | grep "Blocked unauthorized request"
adb logcat | grep "Blocked JS"
adb logcat | grep "Blocked dangerous scheme"
```

### Response Procedure

1. **Detect**: Security event logged
2. **Analyze**: Determine attack vector
3. **Isolate**: Identify affected content
4. **Fix**: Implement mitigation
5. **Test**: Verify fix with corpus
6. **Document**: Add to corpus
7. **Deploy**: Release security fix
8. **Monitor**: Watch for similar attempts

### Escalation

**Low Severity** (blocked attempt):
- Log event
- Continue monitoring
- Review in weekly security meeting

**Medium Severity** (bypass attempt):
- Immediate investigation
- Implement fix within 24 hours
- Test thoroughly
- Deploy as hotfix

**High Severity** (successful bypass):
- Immediate investigation
- Implement fix within 4 hours
- Emergency deployment
- User notification if needed
- Post-mortem analysis

---

## Appendix: Security Checklist

### Pre-Release Security Checklist

- [ ] All WebView settings documented and verified
- [ ] Request blocking (hard-block) implemented and tested
- [ ] Navigation control implemented and tested
- [ ] JavaScript dialog blocking implemented and tested
- [ ] HTML sanitization implemented and tested
- [ ] URL validation implemented and tested
- [ ] XSS corpus tests passing (100%)
- [ ] Security tests passing (100%)
- [ ] Manual security testing completed
- [ ] Security documentation up to date
- [ ] Logs reviewed for security events
- [ ] No security warnings in code
- [ ] Security audit completed

### Runtime Security Monitoring

- [ ] Monitor logs for blocked requests
- [ ] Monitor logs for blocked dialogs
- [ ] Monitor logs for HTML detection
- [ ] Monitor logs for dangerous schemes
- [ ] Review security events weekly
- [ ] Update XSS corpus quarterly
- [ ] Run security tests before each release

---

## References

- [Android WebView Security](https://developer.android.com/training/articles/security-tips#WebView)
- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)
- [WebViewAssetLoader Documentation](https://developer.android.com/reference/androidx/webkit/WebViewAssetLoader)
- [XSS Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)

---

## Changelog

### Version 1.0 (2024-12-28)
- Initial security documentation
- Complete threat model
- All security controls documented
- Testing procedures defined
- Incident response procedures defined

