@echo off
echo ========================================
echo Control Agent Diagnostic Tool
echo ========================================
echo.

echo [1/3] Checking if Control Agent is enabled in preferences...
adb -s EM95IBKZEYIFSO69 shell "run-as ai.pipecat.gemini_multimodal_websocket_demo cat /data/data/ai.pipecat.gemini_multimodal_websocket_demo/shared_prefs/prefs.xml | grep control_agent_enabled"
echo.

echo [2/3] Checking recent Control Agent logs...
adb -s EM95IBKZEYIFSO69 logcat -d | findstr /C:"ControlAgent" | findstr /C:"enabled disabled"
echo.

echo [3/3] Checking for recent transcript processing...
adb -s EM95IBKZEYIFSO69 logcat -d | findstr /C:"onUserTranscript" | findstr /V /C:"already initialized"
echo.

echo ========================================
echo Diagnostic complete!
echo.
echo If you see "control_agent_enabled" value="false", 
echo then Control Agent is DISABLED.
echo.
echo To enable it:
echo 1. Open the app
echo 2. Go to Settings (gear icon)
echo 3. Find "Agent sterowania głosowego" section
echo 4. Toggle "Włącz agenta sterowania" to ON
echo ========================================
pause
