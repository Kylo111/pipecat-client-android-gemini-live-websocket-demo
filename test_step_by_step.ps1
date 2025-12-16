# Step-by-step Reasoning Agent Test with detailed instructions

Write-Host "╔════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   REASONING AGENT - STEP BY STEP TEST                     ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# Clear logs
Write-Host "Clearing logs..." -ForegroundColor Yellow
adb -s EM95IBKZEYIFSO69 logcat -c
Write-Host "✓ Logs cleared" -ForegroundColor Green
Write-Host ""

# Step 1: Launch app
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray
Write-Host "STEP 1: Launch Application" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray
Write-Host ""
Write-Host "Action: Open the app on your phone" -ForegroundColor White
Write-Host "Press ENTER when app is open..." -ForegroundColor Yellow
$null = Read-Host

Write-Host "Checking if app is running..." -ForegroundColor Yellow
Start-Sleep -Seconds 2

$appRunning = adb -s EM95IBKZEYIFSO69 shell "ps -A | grep pipecat"
if ($appRunning) {
    Write-Host "✓ App is running" -ForegroundColor Green
} else {
    Write-Host "✗ App not detected. Make sure it's open!" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Step 2: Start voice session
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray
Write-Host "STEP 2: Start Voice Session" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray
Write-Host ""
Write-Host "Action: Press the microphone button to start a voice session" -ForegroundColor White
Write-Host "Press ENTER when session is started..." -ForegroundColor Yellow
$null = Read-Host

Write-Host "Checking for component initialization..." -ForegroundColor Yellow
Start-Sleep -Seconds 3

$logs = adb -s EM95IBKZEYIFSO69 logcat -d

# Check ReasoningAgentManager
$reasoningInit = $logs | Select-String "ReasoningAgentManager.*initialized|ReasoningAgentManager reference set"
if ($reasoningInit) {
    Write-Host "✓ ReasoningAgentManager initialized" -ForegroundColor Green
} else {
    Write-Host "✗ ReasoningAgentManager NOT initialized" -ForegroundColor Red
    Write-Host "  This is critical - Reasoning Agent won't work!" -ForegroundColor Red
}

# Check tool registration
$toolReg = $logs | Select-String "start_reasoning_task" | Select-Object -Last 1
if ($toolReg) {
    Write-Host "✓ start_reasoning_task tool found in logs" -ForegroundColor Green
} else {
    Write-Host "✗ start_reasoning_task tool NOT found" -ForegroundColor Red
}

Write-Host ""

# Step 3: Test voice command
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray
Write-Host "STEP 3: Test Voice Command" -ForegroundColor Yellow
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Gray
Write-Host ""
Write-Host "Now we'll monitor logs in real-time." -ForegroundColor White
Write-Host ""
Write-Host "Action: Say this command:" -ForegroundColor White
Write-Host "  'Zapisz w notatkach: test reasoning agent'" -ForegroundColor Cyan
Write-Host ""
Write-Host "Starting log monitor... (Press Ctrl+C to stop)" -ForegroundColor Yellow
Write-Host ""

# Monitor logs
$patterns = @(
    "GeminiClient.*Tool call.*start_reasoning_task",
    "ToolExecutor.*start_reasoning_task",
    "ReasoningAgentManager.*Starting",
    "ReasoningWorker.*Processing",
    "OpenRouterClient.*Calling|OpenRouterClient.*response",
    "ContextInjector.*Injecting",
    "GeminiClient.*text:.*Zapisuję|GeminiClient.*text:.*Przygotowałem"
)

$pattern = ($patterns -join "|")

try {
    adb -s EM95IBKZEYIFSO69 logcat | Select-String -Pattern $pattern | ForEach-Object {
        $line = $_.Line
        $timestamp = $line.Substring(0, 18)
        $content = $line.Substring(18)
        
        # Highlight key events
        if ($content -match "Tool call.*start_reasoning_task") {
            Write-Host ""
            Write-Host "╔══════════════════════════════════════════════════════════╗" -ForegroundColor Green
            Write-Host "║  ✓ TOOL CALL DETECTED: start_reasoning_task             ║" -ForegroundColor Green
            Write-Host "╚══════════════════════════════════════════════════════════╝" -ForegroundColor Green
            Write-Host "$timestamp$content" -ForegroundColor Green
            Write-Host ""
        } elseif ($content -match "ReasoningAgentManager.*Starting") {
            Write-Host ""
            Write-Host ">>> ReasoningAgentManager: Task started <<<" -ForegroundColor Yellow
            Write-Host "$timestamp$content" -ForegroundColor Yellow
            Write-Host ""
        } elseif ($content -match "ReasoningWorker.*Processing") {
            Write-Host ""
            Write-Host ">>> ReasoningWorker: Processing task <<<" -ForegroundColor Yellow
            Write-Host "$timestamp$content" -ForegroundColor Yellow
            Write-Host ""
        } elseif ($content -match "OpenRouterClient.*Calling") {
            Write-Host ""
            Write-Host ">>> OpenRouter API: Calling <<<" -ForegroundColor Magenta
            Write-Host "$timestamp$content" -ForegroundColor Magenta
            Write-Host ""
        } elseif ($content -match "OpenRouterClient.*response") {
            Write-Host ""
            Write-Host "╔══════════════════════════════════════════════════════════╗" -ForegroundColor Green
            Write-Host "║  ✓ OPENROUTER RESPONSE RECEIVED                         ║" -ForegroundColor Green
            Write-Host "╚══════════════════════════════════════════════════════════╝" -ForegroundColor Green
            Write-Host "$timestamp$content" -ForegroundColor Green
            Write-Host ""
        } elseif ($content -match "ContextInjector.*Injecting") {
            Write-Host ""
            Write-Host "╔══════════════════════════════════════════════════════════╗" -ForegroundColor Green
            Write-Host "║  ✓ RESULT INJECTED BACK TO CONVERSATION                 ║" -ForegroundColor Green
            Write-Host "╚══════════════════════════════════════════════════════════╝" -ForegroundColor Green
            Write-Host "$timestamp$content" -ForegroundColor Green
            Write-Host ""
        } elseif ($content -match "text:.*Przygotowałem") {
            Write-Host ""
            Write-Host "╔══════════════════════════════════════════════════════════╗" -ForegroundColor Red
            Write-Host "║  ✗ HALLUCINATION DETECTED!                               ║" -ForegroundColor Red
            Write-Host "╚══════════════════════════════════════════════════════════╝" -ForegroundColor Red
            Write-Host "$timestamp$content" -ForegroundColor Red
            Write-Host "Gemini said it created a note but didn't call the tool!" -ForegroundColor Red
            Write-Host ""
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
