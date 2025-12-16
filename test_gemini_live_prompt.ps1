# Test if Gemini Live understands "Zapisz w notatkach" command
# This checks if the updated SystemPrompts.toolsInstruction is working

Write-Host "=== GEMINI LIVE PROMPT TEST ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "This test verifies that Gemini Live knows to call start_reasoning_task" -ForegroundColor Yellow
Write-Host "for 'Zapisz w notatkach' commands instead of hallucinating." -ForegroundColor Yellow
Write-Host ""

# Clear logs
adb -s EM95IBKZEYIFSO69 logcat -c

Write-Host "=== TEST STEPS ===" -ForegroundColor Green
Write-Host "1. Start a voice session in the app" -ForegroundColor White
Write-Host "2. Say: 'Zapisz w notatkach: testowa notatka'" -ForegroundColor White
Write-Host "3. Observe Gemini Live's response" -ForegroundColor White
Write-Host ""
Write-Host "Expected behavior:" -ForegroundColor Cyan
Write-Host "  ✓ Gemini should say: 'Zapisuję...' or 'Uruchamiam agenta...'" -ForegroundColor Green
Write-Host "  ✓ You should see: Tool call: start_reasoning_task" -ForegroundColor Green
Write-Host "  ✗ Gemini should NOT say: 'Przygotowałem notatkę' (hallucination)" -ForegroundColor Red
Write-Host ""
Write-Host "Monitoring logs... (Press Ctrl+C to stop)" -ForegroundColor Yellow
Write-Host ""

# Monitor for tool calls and responses
$patterns = @(
    "GeminiClient.*Tool call",
    "ToolExecutor.*Executing tool",
    "GeminiProtocol.*Parsed.*ToolCall",
    "VoiceClientManager.*Tool call received",
    "GeminiClient.*text:",
    "ReasoningAgentManager.*Starting"
)

$pattern = ($patterns -join "|")

try {
    adb -s EM95IBKZEYIFSO69 logcat | Select-String -Pattern $pattern | ForEach-Object {
        $line = $_.Line
        $timestamp = $line.Substring(0, 18)
        $content = $line.Substring(18)
        
        # Highlight important events
        if ($content -match "Tool call.*start_reasoning_task") {
            Write-Host ""
            Write-Host ">>> TOOL CALL DETECTED <<<" -ForegroundColor Green -BackgroundColor Black
            Write-Host "$timestamp$content" -ForegroundColor Green
            Write-Host ""
        } elseif ($content -match "text:.*Przygotowałem|text:.*Zapisałem") {
            Write-Host ""
            Write-Host ">>> HALLUCINATION DETECTED <<<" -ForegroundColor Red -BackgroundColor Black
            Write-Host "$timestamp$content" -ForegroundColor Red
            Write-Host "Gemini is hallucinating instead of calling start_reasoning_task!" -ForegroundColor Red
            Write-Host ""
        } elseif ($content -match "text:.*Zapisuję|text:.*Uruchamiam") {
            Write-Host ""
            Write-Host ">>> CORRECT RESPONSE <<<" -ForegroundColor Green -BackgroundColor Black
            Write-Host "$timestamp$content" -ForegroundColor Green
            Write-Host ""
        } elseif ($content -match "Tool call") {
            Write-Host "$timestamp" -NoNewline -ForegroundColor Gray
            Write-Host $content -ForegroundColor Cyan
        } elseif ($content -match "Executing tool") {
            Write-Host "$timestamp" -NoNewline -ForegroundColor Gray
            Write-Host $content -ForegroundColor Yellow
        } elseif ($content -match "text:") {
            Write-Host "$timestamp" -NoNewline -ForegroundColor Gray
            Write-Host $content -ForegroundColor White
        } else {
            Write-Host "$timestamp" -NoNewline -ForegroundColor Gray
            Write-Host $content -ForegroundColor Gray
        }
    }
} catch {
    Write-Host ""
    Write-Host "Monitoring stopped." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== TEST COMPLETE ===" -ForegroundColor Cyan
