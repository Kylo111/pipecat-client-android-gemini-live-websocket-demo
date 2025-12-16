# Direct Test - Check if Reasoning Agent components exist
# This checks if all required components are initialized

Write-Host "=== REASONING AGENT COMPONENT CHECK ===" -ForegroundColor Cyan
Write-Host ""

Write-Host "Checking logs for initialization..." -ForegroundColor Yellow
adb -s EM95IBKZEYIFSO69 logcat -d | Select-String -Pattern "ReasoningAgentManager|SnapshotFileManager|ReasoningContextBuilder" | Select-Object -Last 20

Write-Host ""
Write-Host "Checking if start_reasoning_task tool is registered..." -ForegroundColor Yellow
adb -s EM95IBKZEYIFSO69 logcat -d | Select-String -Pattern "start_reasoning_task" | Select-Object -Last 10

Write-Host ""
Write-Host "Checking VoiceService initialization..." -ForegroundColor Yellow
adb -s EM95IBKZEYIFSO69 logcat -d | Select-String -Pattern "VoiceService.*Reasoning|setReasoningAgentManager" | Select-Object -Last 10

Write-Host ""
Write-Host "=== MANUAL TEST ===" -ForegroundColor Cyan
Write-Host "Now try saying: 'Zapisz w notatkach: test'" -ForegroundColor Green
Write-Host "Then run: .\test_reasoning_agent.ps1" -ForegroundColor Green
