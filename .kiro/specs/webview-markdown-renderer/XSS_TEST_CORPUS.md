# XSS Test Corpus for WebView Markdown Renderer

## Overview

This document contains a comprehensive corpus of XSS (Cross-Site Scripting) attack vectors for testing the WebView Markdown Renderer's security. These test cases should be run regularly to ensure the renderer remains secure against known and emerging XSS attacks.

## Purpose

- **Regression Testing**: Verify that security fixes remain effective
- **New Vector Discovery**: Document new attack vectors as they're discovered
- **Security Auditing**: Provide evidence of security testing
- **Developer Education**: Help developers understand XSS risks

## Test Categories

1. [Script Tag Injection](#1-script-tag-injection)
2. [Event Handler Injection](#2-event-handler-injection)
3. [Iframe Injection](#3-iframe-injection)
4. [SVG-Based XSS](#4-svg-based-xss)
5. [Data URL Injection](#5-data-url-injection)
6. [JavaScript Protocol](#6-javascript-protocol)
7. [HTML Entity Encoding](#7-html-entity-encoding)
8. [CSS-Based XSS](#8-css-based-xss)
9. [Markdown-Specific Vectors](#9-markdown-specific-vectors)
10. [Polyglot Payloads](#10-polyglot-payloads)

---

## 1. Script Tag Injection

### 1.1 Basic Script Tag

**Payload:**
```markdown
<script>alert('XSS')</script>
```

**Expected Behavior:** Script tag should be escaped or removed. No JavaScript execution.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 1.2 Script Tag with Attributes

**Payload:**
```markdown
<script type="text/javascript">alert('XSS')</script>
```

**Expected Behavior:** Script tag should be escaped or removed.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 1.3 Script Tag with Newlines

**Payload:**
```markdown
<script>
alert('XSS')
</script>
```

**Expected Behavior:** Script tag should be escaped or removed.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 1.4 Script Tag with Mixed Case

**Payload:**
```markdown
<ScRiPt>alert('XSS')</sCrIpT>
```

**Expected Behavior:** Script tag should be escaped or removed (case-insensitive).

**Test Result:** ✅ PASS / ❌ FAIL

---

### 1.5 Script Tag with Null Bytes

**Payload:**
```markdown
<script\x00>alert('XSS')</script>
```

**Expected Behavior:** Script tag should be escaped or removed.

**Test Result:** ✅ PASS / ❌ FAIL

---

## 2. Event Handler Injection

### 2.1 Onerror Event

**Payload:**
```markdown
<img src=x onerror=alert('XSS')>
```

**Expected Behavior:** Event handler should be removed or escaped.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 2.2 Onload Event

**Payload:**
```markdown
<body onload=alert('XSS')>
```

**Expected Behavior:** Event handler should be removed or escaped.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 2.3 Onclick Event

**Payload:**
```markdown
<div onclick=alert('XSS')>Click me</div>
```

**Expected Behavior:** Event handler should be removed or escaped.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 2.4 Onmouseover Event

**Payload:**
```markdown
<span onmouseover=alert('XSS')>Hover me</span>
```

**Expected Behavior:** Event handler should be removed or escaped.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 2.5 Multiple Event Handlers

**Payload:**
```markdown
<img src=x onerror=alert('XSS1') onload=alert('XSS2')>
```

**Expected Behavior:** All event handlers should be removed or escaped.

**Test Result:** ✅ PASS / ❌ FAIL

---

## 3. Iframe Injection

### 3.1 Basic Iframe

**Payload:**
```markdown
<iframe src="javascript:alert('XSS')"></iframe>
```

**Expected Behavior:** Iframe should be removed or escaped.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 3.2 Iframe with Data URL

**Payload:**
```markdown
<iframe src="data:text/html,<script>alert('XSS')</script>"></iframe>
```

**Expected Behavior:** Iframe should be removed or escaped.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 3.3 Iframe with External URL

**Payload:**
```markdown
<iframe src="https://evil.com/xss.html"></iframe>
```

**Expected Behavior:** Iframe should be removed or escaped.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 3.4 Iframe with Srcdoc

**Payload:**
```markdown
<iframe srcdoc="<script>alert('XSS')</script>"></iframe>
```

**Expected Behavior:** Iframe should be removed or escaped.

**Test Result:** ✅ PASS / ❌ FAIL

---

## 4. SVG-Based XSS

### 4.1 SVG with Script

**Payload:**
```markdown
<svg><script>alert('XSS')</script></svg>
```

**Expected Behavior:** SVG with script should be removed or escaped.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 4.2 SVG with Onload

**Payload:**
```markdown
<svg onload=alert('XSS')></svg>
```

**Expected Behavior:** Event handler should be removed or escaped.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 4.3 SVG with Animate

**Payload:**
```markdown
<svg><animate onbegin=alert('XSS') attributeName=x dur=1s>
```

**Expected Behavior:** Event handler should be removed or escaped.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 4.4 SVG with ForeignObject

**Payload:**
```markdown
<svg><foreignObject><script>alert('XSS')</script></foreignObject></svg>
```

**Expected Behavior:** Script should be removed or escaped.

**Test Result:** ✅ PASS / ❌ FAIL

---

## 5. Data URL Injection

### 5.1 Data URL in Image

**Payload:**
```markdown
<img src="data:text/html,<script>alert('XSS')</script>">
```

**Expected Behavior:** Data URL should be blocked or sanitized.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 5.2 Data URL in Link

**Payload:**
```markdown
[Click me](data:text/html,<script>alert('XSS')</script>)
```

**Expected Behavior:** Data URL should be blocked in navigation.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 5.3 Base64 Encoded Data URL

**Payload:**
```markdown
<img src="data:text/html;base64,PHNjcmlwdD5hbGVydCgnWFNTJyk8L3NjcmlwdD4=">
```

**Expected Behavior:** Data URL should be blocked or sanitized.

**Test Result:** ✅ PASS / ❌ FAIL

---

## 6. JavaScript Protocol

### 6.1 JavaScript Protocol in Link

**Payload:**
```markdown
[Click me](javascript:alert('XSS'))
```

**Expected Behavior:** JavaScript protocol should be blocked in navigation.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 6.2 JavaScript Protocol in Image

**Payload:**
```markdown
<img src="javascript:alert('XSS')">
```

**Expected Behavior:** JavaScript protocol should be blocked.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 6.3 JavaScript Protocol with Encoding

**Payload:**
```markdown
<a href="java&#x09;script:alert('XSS')">Click</a>
```

**Expected Behavior:** JavaScript protocol should be blocked (even with encoding).

**Test Result:** ✅ PASS / ❌ FAIL

---

## 7. HTML Entity Encoding

### 7.1 HTML Entities in Script

**Payload:**
```markdown
&lt;script&gt;alert('XSS')&lt;/script&gt;
```

**Expected Behavior:** Should render as text, not execute.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 7.2 Decimal Entities

**Payload:**
```markdown
&#60;script&#62;alert('XSS')&#60;/script&#62;
```

**Expected Behavior:** Should render as text, not execute.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 7.3 Hex Entities

**Payload:**
```markdown
&#x3C;script&#x3E;alert('XSS')&#x3C;/script&#x3E;
```

**Expected Behavior:** Should render as text, not execute.

**Test Result:** ✅ PASS / ❌ FAIL

---

## 8. CSS-Based XSS

### 8.1 Style Tag with Expression

**Payload:**
```markdown
<style>body{background:url("javascript:alert('XSS')")}</style>
```

**Expected Behavior:** Style tag should be removed or escaped.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 8.2 Inline Style with Expression

**Payload:**
```markdown
<div style="background:url('javascript:alert(XSS)')">XSS</div>
```

**Expected Behavior:** JavaScript in style should be blocked.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 8.3 Link Tag with Stylesheet

**Payload:**
```markdown
<link rel="stylesheet" href="javascript:alert('XSS')">
```

**Expected Behavior:** Link tag should be removed or escaped.

**Test Result:** ✅ PASS / ❌ FAIL

---

## 9. Markdown-Specific Vectors

### 9.1 Markdown Link with JavaScript

**Payload:**
```markdown
[Click me](javascript:alert('XSS'))
```

**Expected Behavior:** JavaScript protocol should be blocked in navigation.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 9.2 Markdown Image with Onerror

**Payload:**
```markdown
![alt](x onerror=alert('XSS'))
```

**Expected Behavior:** Event handler should be removed or escaped.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 9.3 HTML in Markdown Code Block

**Payload:**
````markdown
```html
<script>alert('XSS')</script>
```
````

**Expected Behavior:** Should render as code, not execute.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 9.4 HTML in Inline Code

**Payload:**
```markdown
This is `<script>alert('XSS')</script>` inline code
```

**Expected Behavior:** Should render as code, not execute.

**Test Result:** ✅ PASS / ❌ FAIL

---

## 10. Polyglot Payloads

### 10.1 Polyglot 1

**Payload:**
```markdown
jaVasCript:/*-/*`/*\`/*'/*"/**/(/* */oNcliCk=alert() )//%0D%0A%0d%0a//</stYle/</titLe/</teXtarEa/</scRipt/--!>\x3csVg/<sVg/oNloAd=alert()//>\x3e
```

**Expected Behavior:** Should be escaped or sanitized, no execution.

**Test Result:** ✅ PASS / ❌ FAIL

---

### 10.2 Polyglot 2

**Payload:**
```markdown
'">><marquee><img src=x onerror=confirm(1)></marquee>"></plaintext\></|\><plaintext/onmouseover=prompt(1)><script>prompt(1)</script>@gmail.com<isindex formaction=javascript:alert(/XSS/) type=submit>'-->"></script><script>alert(document.cookie)</script>"><img/id="confirm&lpar;1)"/alt="/"src="/"onerror=eval(id)>'"><img src="http://i.imgur.com/P8mL8.jpg">
```

**Expected Behavior:** Should be escaped or sanitized, no execution.

**Test Result:** ✅ PASS / ❌ FAIL

---

## Automated Test Suite

### Test Implementation

Create automated tests for all corpus entries:

```kotlin
@Test
fun testXSSCorpus() {
    val corpus = loadXSSCorpus()
    
    corpus.forEach { testCase ->
        val markdown = testCase.payload
        
        // Render markdown
        val result = renderMarkdown(markdown)
        
        // Verify no script execution
        assertFalse(
            result.contains("<script>"),
            "Test case ${testCase.id} failed: Script tag not escaped"
        )
        
        // Verify no event handlers
        assertFalse(
            result.matches(Regex("on\\w+=")),
            "Test case ${testCase.id} failed: Event handler not removed"
        )
        
        // Verify no javascript: protocol
        assertFalse(
            result.contains("javascript:"),
            "Test case ${testCase.id} failed: JavaScript protocol not blocked"
        )
        
        // Verify no data: URLs
        assertFalse(
            result.contains("data:text/html"),
            "Test case ${testCase.id} failed: Data URL not blocked"
        )
    }
}
```

### Corpus Data Structure

```kotlin
data class XSSTestCase(
    val id: String,
    val category: String,
    val name: String,
    val payload: String,
    val expectedBehavior: String,
    val severity: Severity
)

enum class Severity {
    CRITICAL,  // Direct script execution
    HIGH,      // Event handler injection
    MEDIUM,    // Indirect execution vectors
    LOW        // Edge cases
}
```

---

## Corpus Maintenance

### Adding New Vectors

When a new XSS vector is discovered:

1. **Document the vector** in this corpus
2. **Categorize it** appropriately
3. **Add expected behavior**
4. **Assign severity level**
5. **Create automated test**
6. **Verify fix**
7. **Update test results**

### Review Schedule

- **Weekly**: Run full corpus against latest code
- **Before Release**: Full security audit with corpus
- **After Security Fix**: Verify fix with relevant corpus entries
- **Quarterly**: Review and update corpus with new vectors

### Reporting

Generate corpus test report:

```bash
./gradlew test --tests "*XSSTest*" > xss_test_report.txt
```

Report should include:
- Total test cases
- Pass/fail count
- Failed test details
- Severity breakdown
- Recommendations

---

## Security Monitoring

### Log Monitoring

Monitor for XSS attempts in production:

```bash
adb logcat | grep -E "HTML detected|Blocked dangerous|XSS"
```

### Metrics to Track

- Number of XSS attempts detected
- Types of vectors attempted
- Frequency of attempts
- Source of malicious content

### Incident Response

If XSS is detected in production:

1. **Isolate**: Identify affected content
2. **Analyze**: Determine attack vector
3. **Fix**: Implement sanitization
4. **Test**: Verify fix with corpus
5. **Document**: Add to corpus
6. **Deploy**: Release security fix
7. **Monitor**: Watch for similar attempts

---

## References

### XSS Resources

- [OWASP XSS Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)
- [PortSwigger XSS Cheat Sheet](https://portswigger.net/web-security/cross-site-scripting/cheat-sheet)
- [HTML5 Security Cheatsheet](https://html5sec.org/)
- [XSS Filter Evasion Cheat Sheet](https://owasp.org/www-community/xss-filter-evasion-cheatsheet)

### Testing Tools

- [XSStrike](https://github.com/s0md3v/XSStrike) - XSS detection suite
- [DOMPurify](https://github.com/cure53/DOMPurify) - HTML sanitizer
- [OWASP ZAP](https://www.zaproxy.org/) - Security testing tool

---

## Changelog

### Version 1.0 (2024-12-28)
- Initial corpus creation
- 40+ test vectors across 10 categories
- Automated test suite structure
- Maintenance procedures

### Future Additions

- [ ] Mutation XSS vectors
- [ ] DOM-based XSS vectors
- [ ] Context-specific vectors (JSON, XML)
- [ ] Browser-specific vectors
- [ ] Mobile-specific vectors

---

## Appendix: Quick Reference

### Critical Vectors to Always Test

1. `<script>alert('XSS')</script>`
2. `<img src=x onerror=alert('XSS')>`
3. `<iframe src="javascript:alert('XSS')"></iframe>`
4. `[Click](javascript:alert('XSS'))`
5. `<svg onload=alert('XSS')></svg>`

### Sanitization Checklist

- [ ] Script tags removed/escaped
- [ ] Event handlers removed
- [ ] Iframe tags removed/escaped
- [ ] JavaScript protocol blocked
- [ ] Data URLs blocked
- [ ] SVG scripts removed
- [ ] Style tags removed/escaped
- [ ] Link tags removed/escaped

### Test Command

```bash
# Run XSS tests
./gradlew test --tests "*XSSTest*"

# Run with verbose output
./gradlew test --tests "*XSSTest*" --info

# Run specific category
./gradlew test --tests "*XSSTest.testScriptInjection*"
```

