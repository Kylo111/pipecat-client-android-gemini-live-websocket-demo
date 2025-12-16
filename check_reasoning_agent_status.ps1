# Check Reasoning Agent Status
# Verifies all components are initialized and ready

Write-Host "=== REASONING AGENT STATUS CHECK ===" -ForegroundColor Cyan
Write-Host ""

# Get recent logs
$logs = adb -s EM95IBKZEYIFSO69 logcat -d

Write-Host "Checking component initialization..." -ForegroundColor Yellow
Write-Host ""

# Check 1: ReasoningAgentManager initialization
$reasoningInit = $logs | Select-String "ReasoningAgentManager.*initialized|ReasoningAgentManager reference set"
if ($reasoningInit) {
    Write-Host "✓ ReasoningAgentManager: INITIALIZED" -ForegroundColor Green
    $reasoningInit | Select-Object -Last 1 | ForEach-Object { 
        Write-Host "  $($_.Line.Substring(0, 18)) $($_.Line.Substring(18))" -ForegroundColor Gray 
    }
} else {
    Write-Host "✗ ReasoningAgentManager: NOT FOUND" -ForegroundColor Red
}
Write-Host ""

# Check 2: SessionManager availability
$sessionManager = $logs | Select-String "SessionManager.*initialized|setSessionManager"
if ($sessionManager) {
    Write-Host "✓ SessionManager: AVAILABLE" -ForegroundColor Green
    $sessionManager | Select-Object -Last 1 | ForEach-Object { 
        Write-Host "  $($_.Line.Substring(0, 18)) $($_.Line.Substring(18))" -ForegroundColor Gray 
    }
} else {
    Write-Host "✗ SessionManager: NOT FOUND" -ForegroundColor Red
}
Write-Host ""

# Check 3: OpenRouter API key
$openRouterKey = $logs | Select-String "OpenRouter.*key.*present|OpenRouterClient.*Validating"
if ($openRouterKey) {
    Write-Host "✓ OpenRouter API Key: CONFIGURED" -ForegroundColor Green
    $openRouterKey | Select-Object -Last 1 | ForEach-Object { 
        Write-Host "  $($_.Line.Substring(0, 18)) $($_.Line.Substring(18))" -ForegroundColor Gray 
    }
} else {
    Write-Host "⚠ OpenRouter API Key: UNKNOWN STATUS" -ForegroundColor Yellow
}
Write-Host ""

# Check 4: start_reasoning_task tool registration
$toolRegistration = $logs | Select-String "start_reasoning_task.*registered|availableTools.*start_reasoning_task"
if ($toolRegistration) {
    Write-Host "✓ start_reasoning_task Tool: REGISTERED" -ForegroundColor Green
    $toolRegistration | Select-Object -Last 1 | ForEach-Object { 
        Write-Host "  $($_.Line.Substring(0, 18)) $($_.Line.Substring(18))" -ForegroundColor Gray 
    }
} else {
    Write-Host "✗ start_reasoning_task Tool: NOT REGISTERED" -ForegroundColor Red
}
Write-Host ""

# Check 5: Recent tool calls
$recentToolCalls = $logs | Select-String "Tool call.*start_reasoning_task" | Select-Object -Last 3
if ($recentToolCalls) {
    Write-Host "Recent start_reasoning_task calls:" -ForegroundColor Cyan
    $recentToolCalls | ForEach-Object { 
        Write-Host "  $($_.Line.Substring(0, 18)) $($_.Line.Substring(18))" -ForegroundColor Gray 
    }
} else {
    Write-Host "No recent start_reasoning_task calls found" -ForegroundColor Yellow
}
Write-Host ""

# Check 6: Recent ReasoningWorker activity
$workerActivity = $logs | Select-String "ReasoningWorker.*Processing|ReasoningWorker.*doWork" | Select-Object -Last 3
if ($workerActivity) {
    Write-Host "Recent ReasoningWorker activity:" -ForegroundColor Cyan
    $workerActivity | ForEach-Object { 
        Write-Host "  $($_.Line.Substring(0, 18)) $($_.Line.Substring(18))" -ForegroundColor Gray 
    }
} else {
    Write-Host "No recent ReasoningWorker activity" -ForegroundColor Yellow
}
Write-Host ""

# Check 7: Recent OpenRouter API calls
$openRouterCalls = $logs | Select-String "OpenRouterClient.*Calling|OpenRouter API response" | Select-Object -Last 3
if ($openRouterCalls) {
    Write-Host "Recent OpenRouter API calls:" -ForegroundColor Cyan
    $openRouterCalls | ForEach-Object { 
        Write-Host "  $($_.Line.Substring(0, 18)) $($_.Line.Substring(18))" -ForegroundColor Gray 
    }
} else {
    Write-Host "No recent OpenRouter API calls" -ForegroundColor Yellow
}
Write-Host ""

# Check 8: Recent errors
$errors = $logs | Select-String "ReasoningAgent.*ERROR|ReasoningWorker.*ERROR|OpenRouterClient.*ERROR" | Select-Object -Last 5
if ($errors) {
    Write-Host "⚠ Recent errors found:" -ForegroundColor Red
    $errors | ForEach-Object { 
        Write-Host "  $($_.Line.Substring(0, 18)) $($_.Line.Substring(18))" -ForegroundColor Red 
    }
} else {
    Write-Host "✓ No recent errors" -ForegroundColor Green
}
Write-Host ""

Write-Host "=== STATUS CHECK COMPLETE ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "To test Reasoning Agent:" -ForegroundColor Yellow
Write-Host "  1. Run: .\test_reasoning_agent_full.ps1" -ForegroundColor White
Write-Host "  2. Start voice session in app" -ForegroundColor White
Write-Host "  3. Say: 'Zapisz w notatkach: test'" -ForegroundColor White
