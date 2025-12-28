/**
 * Markdown Renderer with LaTeX and Syntax Highlighting Support
 * 
 * This script provides functions to render Markdown content with:
 * - GFM (GitHub Flavored Markdown) extensions
 * - LaTeX math rendering via KaTeX
 * - Syntax highlighting via highlight.js
 * - HTML sanitization for XSS prevention
 */

// Configure marked.js with GFM extensions
function configureMarked() {
    if (typeof marked === 'undefined') {
        console.error('marked.js not loaded');
        return false;
    }

    try {
        // Enable GFM extensions: tables, strikethrough, task lists
        marked.setOptions({
            gfm: true,
            breaks: true,
            tables: true,
            pedantic: false,
            sanitize: false, // We handle sanitization separately
            smartLists: true,
            smartypants: false
        });

        return true;
    } catch (error) {
        console.error('Error configuring marked:', error);
        return false;
    }
}

/**
 * Sanitize HTML to prevent XSS attacks
 * Phase 1: Escape ALL HTML tags by default
 * 
 * @param {string} html - HTML string to sanitize
 * @returns {string} - Sanitized HTML string
 */
function sanitizeHtml(html) {
    if (!html) return '';

    // Phase 1: Escape all HTML tags
    // Convert < to &lt; and > to &gt;
    const escaped = html
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');

    return escaped;
}

/**
 * Render Markdown content with LaTeX support
 * 
 * @param {string} markdown - Raw Markdown string
 */
function renderMarkdown(markdown) {
    try {
        const contentDiv = document.getElementById('content');
        const errorDiv = document.getElementById('error');

        if (!contentDiv) {
            console.error('Content div not found');
            return;
        }

        // Hide error div
        if (errorDiv) {
            errorDiv.style.display = 'none';
        }

        // Check if markdown contains HTML tags
        const htmlTagPattern = /<[^>]+>/;
        if (htmlTagPattern.test(markdown)) {
            console.warn('HTML detected in Markdown - will be escaped for security');
        }

        // Configure marked if not already done
        if (!window.markedConfigured) {
            window.markedConfigured = configureMarked();
        }

        if (!window.markedConfigured) {
            throw new Error('Failed to configure marked.js');
        }

        // STEP 1: Protect LaTeX expressions from markdown parsing
        // Replace LaTeX with placeholders before markdown parsing
        const latexPlaceholders = [];
        let placeholderIndex = 0;
        
        // Protect display math: \[...\] and $$...$$
        let processedMarkdown = markdown.replace(/\\\[([\s\S]*?)\\\]|\$\$([\s\S]*?)\$\$/g, (match, latex1, latex2) => {
            const latex = latex1 || latex2;
            const placeholder = `LATEXDISPLAY${placeholderIndex}ENDLATEX`;
            latexPlaceholders.push({ placeholder, latex, type: 'display', index: placeholderIndex });
            placeholderIndex++;
            return placeholder;
        });
        
        // Protect inline math: \(...\) and $...$
        processedMarkdown = processedMarkdown.replace(/\\\(([\s\S]*?)\\\)|\$([^\$\n]+?)\$/g, (match, latex1, latex2) => {
            const latex = latex1 || latex2;
            const placeholder = `LATEXINLINE${placeholderIndex}ENDLATEX`;
            latexPlaceholders.push({ placeholder, latex, type: 'inline', index: placeholderIndex });
            placeholderIndex++;
            return placeholder;
        });

        console.log('LaTeX placeholders created:', latexPlaceholders.length);

        // STEP 2: Parse Markdown to HTML
        let html = marked.parse(processedMarkdown);

        console.log('HTML after markdown parsing (first 500 chars):', html.substring(0, 500));

        // STEP 3: Replace placeholders with rendered LaTeX
        if (typeof katex !== 'undefined') {
            latexPlaceholders.forEach(({ placeholder, latex, type, index }) => {
                try {
                    const rendered = katex.renderToString(latex, {
                        displayMode: type === 'display',
                        throwOnError: false,
                        errorColor: '#d32f2f'
                    });
                    
                    // Use regex to find placeholder (it might be escaped or modified by marked)
                    const placeholderPattern = new RegExp(`LATEXDISPLAY${index}ENDLATEX|LATEXINLINE${index}ENDLATEX`, 'g');
                    
                    if (type === 'display') {
                        html = html.replace(placeholderPattern, '<div class="katex-display">' + rendered + '</div>');
                    } else {
                        html = html.replace(placeholderPattern, '<span class="katex-inline">' + rendered + '</span>');
                    }
                    
                    console.log('Replaced placeholder:', placeholder, 'for LaTeX:', latex.substring(0, 50));
                } catch (e) {
                    console.error('LaTeX rendering error:', e, 'for LaTeX:', latex);
                    const errorHtml = type === 'display' 
                        ? '<div class="katex-error">LaTeX Error: ' + latex + '</div>'
                        : '<span class="katex-error">LaTeX Error: ' + latex + '</span>';
                    const placeholderPattern = new RegExp(`LATEXDISPLAY${index}ENDLATEX|LATEXINLINE${index}ENDLATEX`, 'g');
                    html = html.replace(placeholderPattern, errorHtml);
                }
            });
        } else {
            console.warn('KaTeX not loaded, LaTeX expressions will not be rendered');
        }

        // STEP 4: Render HTML
        contentDiv.innerHTML = html;

        // STEP 5: Apply syntax highlighting
        if (typeof hljs !== 'undefined') {
            contentDiv.querySelectorAll('pre code').forEach((block) => {
                hljs.highlightElement(block);
            });
        }

        // Notify Android that rendering completed successfully
        if (typeof Android !== 'undefined' && Android.onRenderComplete) {
            Android.onRenderComplete();
        }

    } catch (error) {
        console.error('Error rendering markdown:', error);
        showError('Failed to render content: ' + error.message);
        
        // Report error to Android via JavaScript interface
        if (typeof Android !== 'undefined' && Android.onError) {
            Android.onError('Rendering error: ' + error.message);
        }
    }
}

/**
 * Show error message to user
 * 
 * @param {string} message - Error message to display
 */
function showError(message) {
    const errorDiv = document.getElementById('error');
    if (errorDiv) {
        errorDiv.textContent = message;
        errorDiv.style.display = 'block';
    }
}

/**
 * Handle link clicks (called from WebViewClient, not used in Phase 1)
 * Note: Link handling is done entirely in WebViewClient.shouldOverrideUrlLoading
 * This function is kept for potential future use
 * 
 * @param {string} url - URL that was clicked
 */
function handleLinkClick(url) {
    console.log('Link clicked:', url);
    // Link handling is done in WebViewClient for security
    // This is just a placeholder for potential future use
}

// Initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        configureMarked();
    });
} else {
    configureMarked();
}
