# Implementation Plan: WebView Markdown Renderer

## Overview

This plan outlines the implementation of a WebView-based Markdown renderer to replace the custom parser. The implementation will be done in phases to allow testing and rollback if needed.

## Tasks

- [x] 1. Prepare Assets and HTML Template
  - Download and bundle JavaScript libraries (marked.js, KaTeX, highlight.js)
  - Create HTML template with proper structure
  - Create custom CSS for styling
  - Set up asset directory structure
  - _Requirements: 6.1, 6.2, 6.3_

- [x] 1.1 Download and prepare JavaScript libraries
  - Download marked.js v11.0.0 minified
  - Download KaTeX v0.16.9 (JS, CSS, fonts)
  - Download highlight.js v11.9.0 with common languages
  - Verify file integrity and licenses
  - _Requirements: 6.1, 6.2_

- [x] 1.2 Create assets directory structure
  - Create `assets/markdown-renderer/` directory
  - Create subdirectories: `js/`, `css/`, `fonts/`
  - Copy libraries to appropriate directories
  - _Requirements: 6.1_

- [x] 1.3 Create HTML template (index.html)
  - Create basic HTML5 structure
  - Link CSS and JavaScript files
  - Add content div and error div
  - Add viewport meta tag for responsive rendering
  - _Requirements: 1.1, 6.1_

- [x] 1.4 Create custom CSS (styles.css)
  - Define CSS variables for theming (light/dark)
  - Style typography (fonts, sizes, line heights)
  - Style Markdown elements (headers, lists, blockquotes)
  - Style tables with borders and alternating rows
  - Style code blocks with background
  - Style LaTeX display and inline math
  - Style links with hover effects
  - _Requirements: 1.3, 3.1, 3.2, 4.4, 5.3_

- [x] 1.5 Create renderer JavaScript (renderer.js)
  - Implement `configureMarked()` function with GFM extensions enabled
  - Implement `renderMarkdown(markdown)` function
  - Implement `renderLatex()` function with KaTeX
  - Implement `sanitizeHtml(html)` function for XSS prevention
  - Implement `handleLinkClick(url)` function
  - Add error handling with try-catch
  - _Requirements: 1.2, 2.1, 2.2, 5.1, 11.1, 13.4, 14.1_

- [x] 2. Implement MarkdownWebView Component
  - Create Kotlin Composable wrapper for WebView
  - Implement WebViewAssetLoader for secure HTTPS asset loading
  - Implement JavaScript interface for callbacks
  - Implement navigation interception for security
  - Add theme synchronization
  - _Requirements: 1.1, 6.2, 6.3, 13.1, 13.2, 13.3_

- [x] 2.1 Create MarkdownWebView.kt file
  - Create Composable function signature
  - Add parameters: markdown, modifier, onLinkClick
  - Set up WebViewAssetLoader for HTTPS asset serving
  - _Requirements: 1.1, 6.2, 6.3_

- [x] 2.2 Implement WebView factory with security configuration
  - Create AndroidView with WebView factory
  - Configure WebView settings (JavaScript enabled, file access DISABLED)
  - Disable unnecessary features (multiple windows, zoom)
  - Set cache mode to LOAD_DEFAULT (network blocked separately)
  - Set WebChromeClient to block JS dialogs (alert, confirm, prompt)
  - Set WebChromeClient for console message logging
  - Set WebViewClient with shouldInterceptRequest (hard block non-appassets with 403)
  - Set WebViewClient with shouldOverrideUrlLoading (scheme allowlist validation)
  - Load HTML via HTTPS URL (appassets.androidplatform.net)
  - _Requirements: 1.1, 6.1, 6.2, 6.3, 11.1, 13.1, 13.2, 13.3, 13.6_

- [x] 2.3 Implement JavaScript interface (WebViewInterface)
  - Create inner class with @JavascriptInterface methods
  - Implement ONLY `onError(message: String)` method (minimal API)
  - Add interface to WebView with `addJavascriptInterface`
  - Note: Link handling done in WebViewClient, not JS bridge
  - _Requirements: 11.1, 13.3_

- [x] 2.4 Implement content rendering with sanitization
  - Create `escapeForJavaScript(text: String)` utility function
  - In update block, escape markdown content
  - Call `evaluateJavascript` with `renderMarkdown()`
  - Handle rendering errors gracefully
  - Log warning if HTML detected in Markdown
  - _Requirements: 1.1, 1.2, 11.2, 14.3_

- [x] 2.5 Configure WebView layout for flexible height
  - Use Modifier.fillMaxHeight() or weight(1f) instead of fixed height
  - Enable smooth scrolling within WebView
  - Configure nested scrolling if needed
  - Test scrolling performance with long documents
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 2.6 Implement theme synchronization
  - Detect current theme (light/dark) from MaterialTheme
  - Pass theme to JavaScript via CSS variables
  - Update theme when MaterialTheme changes
  - _Requirements: 1.5_

- [x] 3. Integrate with NotesScreen
  - Replace MarkdownText with MarkdownWebView
  - Test with existing notes
  - Verify all functionality preserved
  - _Requirements: 8.1, 8.2, 8.4_

- [x] 3.1 Update NotesScreen imports
  - Import MarkdownWebView
  - Remove MarkdownText import (keep commented for rollback)
  - _Requirements: 8.1_

- [x] 3.2 Replace MarkdownText usage in NoteDetailView
  - Replace MarkdownText composable with MarkdownWebView
  - Pass markdown content
  - Pass appropriate modifier
  - Implement onLinkClick callback
  - _Requirements: 1.1, 5.1, 8.1_

- [x] 3.3 Test with sample notes
  - Open notes with various Markdown elements
  - Verify headers, lists, code blocks render correctly
  - Verify tables render correctly
  - Verify links are clickable
  - _Requirements: 1.2, 3.1, 5.1, 8.1_

- [x] 4. Add LaTeX Support Testing
  - Test block LaTeX rendering
  - Test inline LaTeX rendering
  - Test error handling for invalid LaTeX
  - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [x] 4.1 Test block LaTeX with sample expressions
  - Test `\[...\]` delimiter
  - Test `$$...$$` delimiter
  - Test complex expressions (fractions, integrals, matrices)
  - Verify proper spacing and alignment
  - _Requirements: 2.1, 2.4_

- [x] 4.2 Test inline LaTeX with sample expressions
  - Test `\(...\)` delimiter
  - Test `$...$` delimiter
  - Test inline math in paragraphs
  - Verify baseline alignment
  - _Requirements: 2.2, 2.4_

- [x] 4.3 Test LaTeX error handling
  - Test invalid LaTeX syntax
  - Verify error message displayed
  - Verify app doesn't crash
  - Verify raw LaTeX shown on error
  - _Requirements: 2.3, 11.3_

- [ ] 5. Add Syntax Highlighting Testing
  - Test code blocks with language identifiers
  - Test various programming languages
  - Verify highlighting accuracy
  - _Requirements: 4.1, 4.2, 4.5_

- [x] 5.1 Test syntax highlighting for common languages
  - Test Python code block
  - Test JavaScript code block
  - Test Kotlin code block
  - Test Java code block
  - Test SQL code block
  - _Requirements: 4.1, 4.5_

- [x] 5.2 Test code block styling
  - Verify monospace font used
  - Verify background color applied
  - Verify indentation preserved
  - Verify line breaks preserved
  - _Requirements: 4.2, 4.3, 4.4_

- [x] 6. Performance Testing and Optimization
  - Measure rendering time for various document sizes
  - Optimize asset loading
  - Implement WebView instance reuse
  - Test memory usage
  - _Requirements: 9.1, 9.2, 9.3, 9.4_

- [x] 6.1 Measure baseline performance
  - Test rendering time for small documents (<10KB)
  - Test rendering time for medium documents (10-50KB)
  - Test rendering time for large documents (50-100KB)
  - Record memory usage before and after rendering
  - _Requirements: 9.1_

- [x] 6.2 Optimize asset loading
  - Verify assets load from bundle, not network
  - Measure asset loading time
  - Consider lazy loading highlight.js
  - _Requirements: 6.2, 9.1_

- [x] 6.3 Implement WebView instance reuse
  - Create WebView pool or singleton pattern
  - Reuse WebView when navigating between notes
  - Clear content between reuses
  - _Requirements: 9.3_

- [x] 6.4 Test memory management
  - Test with multiple notes opened in sequence
  - Monitor memory usage over time
  - Test onTrimMemory handling
  - Verify no memory leaks
  - _Requirements: 9.4_

- [x] 7. Error Handling and Edge Cases
  - Test with malformed Markdown
  - Test with very long documents
  - Test with empty content
  - Test with special characters
  - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5_

- [x] 7.1 Test malformed Markdown handling
  - Test unclosed code blocks
  - Test malformed tables
  - Test invalid link syntax
  - Verify graceful degradation
  - _Requirements: 8.3, 11.2_

- [x] 7.2 Test edge cases
  - Test empty markdown string
  - Test very long single line
  - Test deeply nested lists
  - Test special characters (emoji, unicode)
  - _Requirements: 11.1, 11.2_

- [x] 7.3 Test error logging
  - Verify JavaScript errors logged to logcat
  - Verify error context included in logs
  - Verify errors don't crash app
  - _Requirements: 11.1, 11.5_

- [x] 7.4 Run XSS regression test suite
  - Run XSS test corpus against sanitization (see task 10.4 for corpus)
  - Test Markdown with embedded <script> tags
  - Test Markdown with embedded <iframe> tags
  - Test Markdown with onclick/onerror handlers
  - Test SVG-based XSS vectors
  - Test data: URLs in images
  - Verify HTML is escaped/sanitized
  - Verify XSS prevention works
  - Document any failures for corpus update
  - _Requirements: 13.4, 14.1, 14.5_

- [x] 7.5 Test navigation security and URL validation
  - Test clicking http/https links opens system browser
  - Test WebView never navigates to external URLs
  - Test file:// URLs are blocked
  - Test javascript: URLs are blocked
  - Test intent: URLs are blocked
  - Test data: URLs are blocked
  - Test ActivityNotFoundException handling
  - Verify scheme allowlist works correctly
  - _Requirements: 13.2, 13.3_

- [x] 7.6 Test asset loading security and network blocking
  - Verify assets load via HTTPS (appassets.androidplatform.net)
  - Verify file access is disabled
  - Test with network disabled (airplane mode) - should work
  - Test unauthorized request returns 403 Forbidden (not null)
  - Verify requests outside appassets domain are hard-blocked
  - Log and verify all blocked requests
  - Verify no network fallback attempts
  - _Requirements: 6.2, 6.3, 13.1, 13.6_

- [x] 7.7 Test JavaScript dialog blocking
  - Test that alert() is blocked and logged
  - Test that confirm() is blocked and logged
  - Test that prompt() is blocked and logged
  - Verify no popups appear to user
  - Verify blocked attempts are logged for auditing
  - _Requirements: 13.5_

- [x] 8. Text Selection and Copy
  - Enable text selection in WebView
  - Test copy functionality
  - Verify clipboard content
  - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5_

- [x] 8.1 Enable text selection
  - Configure WebView to allow text selection
  - Test long-press to select text
  - Verify selection handles appear
  - _Requirements: 12.1_

- [x] 8.2 Test copy functionality
  - Test copying plain text
  - Test copying LaTeX (as rendered text, not source)
  - Test copying across paragraphs
  - Verify clipboard contains plain text (not HTML)
  - _Requirements: 12.2, 12.3, 12.4, 12.5_

- [x] 9. Backward Compatibility Testing
  - Test with notes created before migration
  - Verify visual equivalence
  - Test all existing features (delete, rename, share)
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [x] 9.1 Test with existing notes
  - Open 10+ existing notes
  - Compare rendering to old parser (screenshots)
  - Verify no content loss
  - Verify no layout issues
  - _Requirements: 8.1, 8.2_

- [x] 9.2 Test existing functionality
  - Test note deletion
  - Test note renaming
  - Test note sharing
  - Test context menu
  - _Requirements: 8.5_

- [x] 10. Documentation and Cleanup
  - Document WebView architecture
  - Add code comments
  - Update README if needed
  - Remove old parser code (after verification)
  - _Requirements: 10.5_

- [x] 10.1 Add code documentation
  - Document MarkdownWebView.kt with KDoc
  - Document JavaScript functions with JSDoc
  - Add inline comments for complex logic
  - Document security decisions and threat model
  - _Requirements: 10.5_

- [x] 10.2 Create developer guide
  - Document how to add new block types
  - Document how to add Chart.js support
  - Document asset update process
  - Document HTML sanitization policy
  - Document URL validation and scheme allowlist
  - _Requirements: 10.1, 10.2, 10.5_

- [x] 10.3 Clean up old code
  - Mark MarkdownText.kt as deprecated
  - Remove after 2 releases
  - Update imports across codebase
  - _Requirements: N/A_

- [x] 10.4 Create XSS test corpus
  - Document known XSS payloads for regression testing
  - Include script, iframe, onerror, svg, data: URL vectors
  - Create automated test suite for corpus
  - Update corpus when new vectors discovered
  - _Requirements: 13.4, 14.1_

- [x] 10.5 Harden WebView settings and implement hard-block for requests
  - Review and document all WebView settings
  - Verify setSupportMultipleWindows(false)
  - Verify zoom controls disabled
  - Document cache policy (LOAD_DEFAULT with network blocked)
  - Verify all unnecessary features disabled
  - Implement hard-block in shouldInterceptRequest (return 403, not null)
  - Test that WebView never attempts network fallback
  - _Requirements: 6.3, 13.1, 13.6_

- [x] 10.6 Implement JavaScript dialog blocking
  - Override onJsAlert in WebChromeClient to block and log
  - Override onJsConfirm in WebChromeClient to block and log
  - Override onJsPrompt in WebChromeClient to block and log
  - Add telemetry/logging for blocked dialog attempts
  - Test with malicious content attempting popups
  - _Requirements: 13.5_

- [x] 10.7 Implement URL validation layer
  - Create URLValidator utility class
  - Implement scheme allowlist (http, https only)
  - Add try/catch for ActivityNotFoundException
  - Add telemetry/logging for blocked URLs
  - Test with edge cases (malformed URLs, unknown schemes)
  - _Requirements: 5.2, 13.2, 13.3, 13.7_

- [x] 11. Final Testing and Release
  - Perform full regression testing
  - Test on multiple devices
  - Test on different Android versions
  - Prepare release notes
  - _Requirements: All_

- [x] 11.1 Regression testing
  - Test all note operations
  - Test all report operations
  - Test theme switching
  - Test app lifecycle (background/foreground)
  - _Requirements: All_

- [x] 11.2 Device testing
  - Test on low-end device (4GB RAM)
  - Test on mid-range device (6-8GB RAM)
  - Test on high-end device (12GB+ RAM)
  - Test on tablet
  - _Requirements: 9.1, 9.2_

- [x] 11.3 Android version testing
  - Test on Android 8.0 (API 26)
  - Test on Android 10.0 (API 29)
  - Test on Android 13.0 (API 33)
  - Test on Android 14.0 (API 34)
  - _Requirements: All_

## Notes

- Tasks can be executed in parallel where dependencies allow
- Each task should be tested before moving to the next
- Keep old MarkdownText.kt for 2 releases as rollback option
- Monitor performance metrics after each phase
- Collect user feedback during gradual rollout

### CRITICAL SECURITY NOTES:
- **WebViewAssetLoader:** Always use HTTPS URLs (appassets.androidplatform.net), never allowFileAccess = true
- **Scheme Allowlist:** Only http/https allowed for external browser; block file://, javascript://, intent://, data://
- **Request Hard-Block:** Return 403 Forbidden (not null) for non-appassets requests to prevent network fallback
- **JavaScript Bridge:** Keep minimal (only onError); link handling in WebViewClient for single source of truth
- **JavaScript Dialogs:** Block all alert/confirm/prompt to prevent popup abuse
- **HTML Sanitization:** Default = escape ALL HTML tags; document any future allowlist clearly
- **Cache Policy:** LOAD_DEFAULT is fine; network blocked in shouldInterceptRequest (appassets cached automatically)

### TECHNICAL NOTES:
- **GFM Support:** Enable GFM extensions in marked.js configuration (tables, strikethrough, task lists)
- **Height Strategy:** Use fillMaxHeight() or weight(1f) in Compose, let WebView handle scrolling
- **HTML Policy:** Phase 1 = escape all HTML; Phase 2 (future) = allowlist safe tags with clear documentation
- **Testing:** 
  - XSS test corpus created in task 10.4, used in task 7.4
  - Test with network disabled (airplane mode) to verify no network dependency
  - Log all blocked requests (URLs, dialogs, schemes) for security auditing
  - Hard-block returns 403 Forbidden to prevent WebView fallback behavior
