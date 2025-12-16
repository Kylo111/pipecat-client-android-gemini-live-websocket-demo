# Get validation logs from Android device
Write-Host "=== VALIDATION LOGS ===" -ForegroundColor Cyan
Write-Host ""

$logs = adb -s EM95IBKZEYIFSO69 logcat -d | Select-String -Pattern "SettingsScreen|OpenRouterClient|AgentConfig|Validation|reasoningAgentModel"

if ($logs) {
    $logs | ForEach-Object {
        $line = $_.Line
        if ($line -match "ERROR|Failed|failed|✗") {
            Write-Host $line -ForegroundColor Red
        } elseif ($line -match "SUCCESS|success|✓") {
            Write-Host $line -ForegroundColor Green
        } elseif ($line -match "Validating|Testing|Checking") {
            Write-Host $line -ForegroundColor Yellow
        } else {
            Write-Host $line -ForegroundColor Gray
        }
    }
} else {
    Write-Host "No validation logs found!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Showing last 50 lines of logcat:" -ForegroundColor Yellow
    adb -s EM95IBKZEYIFSO69 logcat -d | Select-Object -Last 50
}

Write-Host ""
Write-Host "=== END OF LOGS ===" -ForegroundColor Cyan
