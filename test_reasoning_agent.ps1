# Test Reasoning Agent - Direct Tool Call via Logcat
# This script simulates a tool call to test if Reasoning Agent works

Write-Host "=== REASONING AGENT TEST ===" -ForegroundColor Cyan
Write-Host ""

# Step 1: Clear logs
Write-Host "1. Clearing logs..." -ForegroundColor Yellow
adb -s EM95IBKZEYIFSO69 logcat -c

# Step 2: Start monitoring logs in background
Write-Host "2. Starting log monitor..." -ForegroundColor Yellow
$logJob = Start-Job -ScriptBlock {
    adb -s EM95IBKZEYIFSO69 logcat | Select-String -Pattern "ToolExecutor|ReasoningWorker|ReasoningAgentManager|SnapshotFile|🧠|✅|❌"
}

# Wait a bit for log monitor to start
Start-Sleep -Seconds 2

Write-Host ""
Write-Host "3. Test Instructions:" -ForegroundColor Green
Write-Host "   a) Make sure app is running and conversation is active" -ForegroundColor White
Write-Host "   b) Say: 'Zapisz w notatkach: test reasoning agent'" -ForegroundColor White
Write-Host "   c) Watch the logs below for tool execution" -ForegroundColor White
Write-Host ""
Write-Host "Expected log entries:" -ForegroundColor Cyan
Write-Host "   🧠 Starting reasoning task: ..." -ForegroundColor Gray
Write-Host "   ✅ Reasoning task started successfully: ..." -ForegroundColor Gray
Write-Host "   ReasoningAgentManager: Creating snapshot file..." -ForegroundColor Gray
Write-Host "   ReasoningWorker: Starting task..." -ForegroundColor Gray
Write-Host ""
Write-Host "Press Ctrl+C to stop monitoring" -ForegroundColor Yellow
Write-Host "===========================================" -ForegroundColor Cyan
Write-Host ""

# Monitor logs
try {
    while ($true) {
        $logs = Receive-Job -Job $logJob
        if ($logs) {
            $logs | ForEach-Object {
                if ($_ -match "🧠|Starting reasoning") {
                    Write-Host $_ -ForegroundColor Cyan
                } elseif ($_ -match "✅|successfully") {
                    Write-Host $_ -ForegroundColor Green
                } elseif ($_ -match "❌|Error|Failed") {
                    Write-Host $_ -ForegroundColor Red
                } elseif ($_ -match "⚠️|Warning") {
                    Write-Host $_ -ForegroundColor Yellow
                } else {
                    Write-Host $_ -ForegroundColor White
                }
            }
        }
        Start-Sleep -Milliseconds 500
    }
} finally {
    Stop-Job -Job $logJob
    Remove-Job -Job $logJob
}
