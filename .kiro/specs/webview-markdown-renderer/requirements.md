# Requirements Document

## Introduction

This specification defines the migration from the custom Markdown parser to a WebView-based renderer for displaying reports and notes in the application. The WebView renderer will provide professional-grade rendering of Markdown, LaTeX, tables, and future support for charts and diagrams, matching the quality of applications like ChatGPT and Claude.

## Glossary

- **WebView**: Android's web browser component that can render HTML/CSS/JavaScript
- **WebViewAssetLoader**: Android component for securely serving local assets via HTTPS URLs
- **KaTeX**: Fast math typesetting library for the web
- **marked.js**: Markdown parser and compiler for JavaScript
- **GFM**: GitHub Flavored Markdown - CommonMark extension with tables, strikethrough, and task lists
- **Renderer**: The component responsible for converting Markdown to visual output
- **Asset Bundle**: JavaScript and CSS files packaged within the APK
- **JavaScriptInterface**: Android mechanism for bidirectional communication between Kotlin and JavaScript
- **Report**: Generated document from Reasoning Agent containing research findings
- **Note**: User-created or agent-created text document stored locally

## Requirements

### Requirement 1: WebView-based Markdown Rendering

**User Story:** As a user, I want reports and notes to render with professional typography and formatting, so that complex content is easy to read and visually appealing.

#### Acceptance Criteria

1. WHEN a user opens a report or note, THE System SHALL render the Markdown content using a WebView component
2. WHEN rendering Markdown, THE System SHALL support CommonMark syntax plus GFM extensions (tables, strikethrough, task lists) including headers, lists, code blocks, blockquotes, and emphasis
3. WHEN displaying rendered content, THE System SHALL apply consistent styling that matches the app's design language
4. WHEN content is rendered, THE System SHALL maintain smooth scrolling performance within the WebView itself
5. WHEN the user switches between light and dark mode, THE System SHALL update the WebView styling accordingly

### Requirement 2: Full LaTeX Support

**User Story:** As a user viewing scientific or mathematical content, I want LaTeX expressions to render beautifully, so that equations and formulas are clear and professional.

#### Acceptance Criteria

1. WHEN Markdown contains block LaTeX delimited by `\[...\]` or `$$...$$`, THE System SHALL render it as display math using KaTeX
2. WHEN Markdown contains inline LaTeX delimited by `\(...\)` or `$...$`, THE System SHALL render it as inline math using KaTeX
3. WHEN LaTeX rendering fails, THE System SHALL display an error message without crashing
4. WHEN LaTeX is rendered, THE System SHALL use proper mathematical typography with correct spacing and alignment
5. WHEN viewing LaTeX content, THE System SHALL support all standard LaTeX commands and symbols supported by KaTeX

### Requirement 3: Enhanced Table Rendering

**User Story:** As a user viewing tabular data, I want tables to be clearly formatted and easy to read, so that I can quickly understand structured information.

#### Acceptance Criteria

1. WHEN Markdown contains a table, THE System SHALL render it with proper borders and cell padding
2. WHEN displaying tables, THE System SHALL apply alternating row colors for better readability
3. WHEN a table is wider than the screen, THE System SHALL enable horizontal scrolling
4. WHEN rendering table headers, THE System SHALL style them distinctly from data rows
5. WHEN tables contain formatted text, THE System SHALL preserve inline formatting within cells

### Requirement 4: Code Syntax Highlighting

**User Story:** As a user viewing code snippets, I want syntax highlighting, so that code is easier to read and understand.

#### Acceptance Criteria

1. WHEN Markdown contains a fenced code block with a language identifier, THE System SHALL apply syntax highlighting
2. WHEN displaying code, THE System SHALL use a monospace font with appropriate line height
3. WHEN rendering code blocks, THE System SHALL preserve indentation and whitespace
4. WHEN code blocks are displayed, THE System SHALL provide a subtle background color to distinguish them from text
5. WHEN syntax highlighting is applied, THE System SHALL support common languages including Python, JavaScript, Kotlin, Java, and SQL

### Requirement 5: Clickable Links

**User Story:** As a user viewing content with hyperlinks, I want to click links to open them, so that I can access referenced resources.

#### Acceptance Criteria

1. WHEN Markdown contains a link, THE System SHALL render it as clickable with distinct styling
2. WHEN a user clicks a link, THE System SHALL open it in the device's default browser
3. WHEN hovering over a link (on devices with pointer input), THE System SHALL show a visual indication
4. WHEN links are displayed, THE System SHALL use the app's primary color for consistency
5. WHEN a link fails to open, THE System SHALL show an appropriate error message

### Requirement 6: Secure Asset Management

**User Story:** As a developer, I want rendering libraries bundled securely in the app, so that the renderer works offline, loads quickly, and prevents security vulnerabilities.

#### Acceptance Criteria

1. WHEN the app is built, THE System SHALL bundle marked.js, KaTeX, and related assets in the APK
2. WHEN the WebView loads, THE System SHALL serve assets via WebViewAssetLoader using HTTPS URLs (appassets.androidplatform.net)
3. WHEN configuring WebView, THE System SHALL disable file access (allowFileAccess = false) to prevent file:// URL vulnerabilities
4. WHEN assets are loaded, THE System SHALL use minified versions to minimize size
5. WHEN the app starts, THE System SHALL cache the HTML template for fast subsequent loads
6. WHEN assets are updated, THE System SHALL version them to prevent cache issues

### Requirement 7: WebView Height and Scrolling

**User Story:** As a user scrolling through content, I want the WebView to handle scrolling smoothly, so that I can read long documents without layout issues.

#### Acceptance Criteria

1. WHEN content is rendered, THE System SHALL allow the WebView to handle its own scrolling
2. WHEN content is very long, THE System SHALL set a reasonable fixed height for the WebView container
3. WHEN the WebView is displayed, THE System SHALL enable smooth scrolling within the WebView
4. WHEN content is very short, THE System SHALL set a minimum height to prevent layout issues
5. WHEN the WebView is embedded in a scrolling parent, THE System SHALL configure nested scrolling properly to avoid conflicts

### Requirement 8: Backward Compatibility

**User Story:** As a user with existing notes and reports, I want them to continue displaying correctly, so that I don't lose access to my content.

#### Acceptance Criteria

1. WHEN opening notes created before the migration, THE System SHALL render them correctly with the new renderer
2. WHEN displaying old reports, THE System SHALL handle any legacy formatting gracefully
3. WHEN encountering unsupported syntax, THE System SHALL render it as plain text rather than failing
4. WHEN migrating, THE System SHALL not require modification of existing note files
5. WHEN the new renderer is active, THE System SHALL maintain all existing functionality including copy, share, and delete

### Requirement 9: Performance Optimization

**User Story:** As a user opening reports, I want them to load quickly, so that I can access information without delay.

#### Acceptance Criteria

1. WHEN a report is opened, THE System SHALL display content within 500ms on modern devices
2. WHEN rendering large documents, THE System SHALL maintain smooth scrolling at 60fps
3. WHEN multiple reports are opened in sequence, THE System SHALL reuse the WebView instance
4. WHEN the app is low on memory, THE System SHALL release WebView resources appropriately
5. WHEN content is updated, THE System SHALL re-render efficiently without full page reload

### Requirement 10: Future Extensibility

**User Story:** As a developer planning future features, I want the renderer to support charts and diagrams, so that we can add rich visualizations later.

#### Acceptance Criteria

1. WHEN the renderer is implemented, THE System SHALL use an architecture that allows adding new block types
2. WHEN designing the HTML template, THE System SHALL include placeholder support for Chart.js
3. WHEN implementing JavaScript interfaces, THE System SHALL allow for future bidirectional communication
4. WHEN structuring assets, THE System SHALL organize them to easily add new libraries
5. WHEN documenting the implementation, THE System SHALL include guidance for adding chart support

### Requirement 11: Error Handling

**User Story:** As a user, I want the app to handle rendering errors gracefully, so that one problematic document doesn't crash the app.

#### Acceptance Criteria

1. WHEN JavaScript errors occur in the WebView, THE System SHALL log them without crashing
2. WHEN content fails to render, THE System SHALL display a user-friendly error message
3. WHEN LaTeX syntax is invalid, THE System SHALL show the raw LaTeX with an error indicator
4. WHEN the WebView fails to load, THE System SHALL fall back to displaying raw Markdown
5. WHEN errors are logged, THE System SHALL include sufficient context for debugging

### Requirement 12: Text Selection and Copy

**User Story:** As a user, I want to select and copy text from reports, so that I can use the information elsewhere.

#### Acceptance Criteria

1. WHEN a user long-presses on text, THE System SHALL enable text selection mode
2. WHEN text is selected, THE System SHALL show selection handles and a copy button
3. WHEN the user copies text, THE System SHALL copy it to the clipboard as plain text
4. WHEN copying LaTeX content, THE System SHALL copy the rendered text representation (not LaTeX source)
5. WHEN selection mode is active, THE System SHALL allow selecting across multiple paragraphs

### Requirement 13: WebView Security and Navigation Control

**User Story:** As a developer, I want the WebView to be secure and prevent unauthorized navigation, so that the app is protected from XSS and other web-based attacks.

#### Acceptance Criteria

1. WHEN the WebView is configured, THE System SHALL disable file access (allowFileAccess = false) to prevent file:// URL vulnerabilities
2. WHEN the WebView loads content, THE System SHALL only allow navigation to the initial HTML template (no external URLs)
3. WHEN a user clicks a link in rendered content, THE System SHALL validate the URL scheme against an allowlist (http, https only) and open in the system browser instead of navigating within the WebView
4. WHEN Markdown contains HTML tags, THE System SHALL sanitize or escape them to prevent XSS attacks
5. WHEN JavaScript attempts to show a dialog (alert, confirm, prompt), THE System SHALL block it and log the attempt for security auditing
6. WHEN the WebView intercepts requests, THE System SHALL block all requests outside the appassets.androidplatform.net domain by returning 403 Forbidden (hard block, no network fallback)
7. WHEN a URL with a dangerous scheme is encountered (file://, javascript://, intent://, data://), THE System SHALL block it and log a warning
8. WHEN the WebView is initialized, THE System SHALL disable universal file access, mixed content, and unnecessary features (multiple windows, zoom) to enforce security policies

### Requirement 14: HTML-in-Markdown Policy

**User Story:** As a developer, I want a clear policy for HTML embedded in Markdown, so that security risks are minimized while maintaining functionality.

#### Acceptance Criteria

1. WHEN Markdown contains raw HTML tags, THE System SHALL escape them by default to prevent XSS (Phase 1: escape ALL HTML)
2. WHEN rendering Markdown, THE System SHALL document the HTML sanitization mode (ESCAPE_ALL, ALLOWLIST_SAFE_TAGS, or DISABLED)
3. WHEN HTML is detected in Markdown, THE System SHALL log a warning for security auditing
4. WHEN the security policy is defined, THE System SHALL be configurable to allow safe HTML tags if needed in the future (Phase 2: allowlist <b>, <i>, <em>, <strong>, <a>, <code>, <pre> only)
5. WHEN HTML sanitization is applied, THE System SHALL preserve the text content while removing potentially dangerous tags (always block: <script>, <iframe>, <object>, <embed>, <link>, <style>, event handlers)

## Notes

- This migration will replace the custom `MarkdownParser` object in `MarkdownText.kt`
- The WebView implementation will be cross-platform compatible for future iOS development
- Performance testing should be conducted on devices with 4GB RAM or less
- The implementation MUST follow Android WebView security best practices:
  - Use WebViewAssetLoader for serving local assets via HTTPS
  - Disable file access (allowFileAccess = false)
  - Block navigation to external URLs (intercept in WebViewClient)
  - Sanitize or escape HTML-in-Markdown to prevent XSS
- For very long documents, let WebView handle its own scrolling rather than dynamic height adjustment
- Consider using a single WebView instance pool to reduce memory overhead
- GFM (GitHub Flavored Markdown) extensions should be explicitly enabled in marked.js configuration
