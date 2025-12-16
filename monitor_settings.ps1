# Monitor Settings screen logs in real-time
Write-Host "=== MONITORING SETTINGS LOGS ===" -ForegroundColor Cyan
Write-Host "Waiting for Settings activity..." -ForegroundColor Yellow
Write-Host "Open Settings in the app now!" -ForegroundColor Green
Write-Host ""

# Clear logs first
adb -s EM95IBKZEYIFSO69 logcat -c

# Monitor logs in real-time
adb -s EM95IBKZEYIFSO69 logcat | Select-String -Pattern "SettingsScreen|OpenRouter|AgentConfig|Validation|pipecat" | ForEach-Object {
    $line = $_.Line
    
    # Color code based on content
    if ($line -match "ERROR|Failed|failed|Exception|✗") {
        Write-Host $line -ForegroundColor Red
    } elseif ($line -match "SUCCESS|success|✓|validated") {
        Write-Host $line -ForegroundColor Green
    } elseif ($line -match "Validating|Testing|Checking|init") {
        Write-Host $line -ForegroundColor Yellow
    } elseif ($line -match "model|Model") {
        Write-Host $line -ForegroundColor Cyan
    } else {
        Write-Host $line -ForegroundColor Gray
    }
}
