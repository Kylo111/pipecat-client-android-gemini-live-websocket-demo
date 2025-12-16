# Complete Reasoning Agent Test
# Tests the full flow: Gemini Live -> start_reasoning_task -> ReasoningWorker -> OpenRouter -> Result

Write-Host "=== REASONING AGENT FULL TEST ===" -ForegroundColor Cyan
Write-Host ""

# Clear logs
Write-Host "Clearing logs..." -ForegroundColor Yellow
adb -s EM95IBKZEYIFSO69 logcat -c
Start-Sleep -Milliseconds 500

Write-Host ""
Write-Host "=== TEST INSTRUCTIONS ===" -ForegroundColor Green
Write-Host "1. Start a voice session in the app" -ForegroundColor White
Write-Host "2. Say: 'Zapisz w notatkach: test reasoning agent'" -ForegroundColor White
Write-Host "3. Wait for Gemini Live to respond" -ForegroundColor White
Write-Host "4. Press ENTER here when done" -ForegroundColor White
Write-Host ""
Write-Host "Monitoring logs in real-time..." -ForegroundColor Yellow
Write-Host "Press Ctrl+C to stop monitoring" -ForegroundColor Gray
Write-Host ""

# Monitor logs in real-time
$logPatterns = @(
    "ToolExecutor.*start_reasoning_task",
    "ReasoningAgentManager",
    "ReasoningWorker",
    "OpenRouterClient",
    "ContextInjector",
    "SnapshotFileManager",
    "ReasoningContextBuilder",
    "GeminiClient.*Tool call.*start_reasoning_task",
    "VoiceClientManager.*Tool.*start_reasoning_task"
)

$pattern = ($logPatterns -join "|")

try {
    adb -s EM95IBKZEYIFSO69 logcat | Select-String -Pattern $pattern | ForEach-Object {
        $line = $_.Line
        $timestamp = $line.Substring(0, 18)
        $content = $line.Substring(18)
        
        # Color coding
        if ($content -match "ERROR|Failed|failed|Exception|✗") {
            Write-Host "$timestamp" -NoNewline -ForegroundColor Gray
            Write-Host $content -ForegroundColor Red
        } elseif ($content -match "SUCCESS|success|✓|validated|completed|Injecting") {
            Write-Host "$timestamp" -NoNewline -ForegroundColor Gray
            Write-Host $content -ForegroundColor Green
        } elseif ($content -match "Starting|Calling|Executing|Processing") {
            Write-Host "$timestamp" -NoNewline -ForegroundColor Gray
            Write-Host $content -ForegroundColor Yellow
        } elseif ($content -match "Tool call|start_reasoning_task") {
            Write-Host "$timestamp" -NoNewline -ForegroundColor Gray
            Write-Host $content -ForegroundColor Cyan
        } elseif ($content -match "OpenRouter|API") {
            Write-Host "$timestamp" -NoNewline -ForegroundColor Gray
            Write-Host $content -ForegroundColor Magenta
        } else {
            Write-Host "$timestamp" -NoNewline -ForegroundColor Gray
            Write-Host $content -ForegroundColor White
        }
    }
} catch {
    Write-Host ""
    Write-Host "Monitoring stopped." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== TEST COMPLETE ===" -ForegroundColor Cyan
