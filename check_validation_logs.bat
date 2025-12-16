@echo off
echo === CHECKING VALIDATION LOGS ===
echo.
adb -s EM95IBKZEYIFSO69 logcat -d | findstr /i "SettingsScreen OpenRouter Validation reasoningAgentModel AgentConfig"
echo.
echo === END OF LOGS ===
pause
