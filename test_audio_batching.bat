@echo off
echo ========================================
echo Audio Batching Test - Monitor Logs
echo ========================================
echo.
echo Clearing logcat...
adb -s EM95IBKZEYIFSO69 logcat -c
echo.
echo Monitoring audio logs (Ctrl+C to stop)...
echo Look for:
echo   - "BATCHING ENABLED" message
echo   - Batched X chunks messages
echo   - Reduced write frequency
echo   - No "trylock fail" errors
echo.
adb -s EM95IBKZEYIFSO69 logcat | findstr /I "AudioEngine AudioFlinger trylock BATCHING Batched"
