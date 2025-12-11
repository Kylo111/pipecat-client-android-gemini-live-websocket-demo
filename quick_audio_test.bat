@echo off
echo ========================================
echo QUICK AUDIO ISSUE TEST
echo ========================================
echo.
echo Instructions:
echo 1. Make sure app is running
echo 2. Start a conversation
echo 3. Listen for audio issues (glitches, pops, overlapping)
echo 4. This script will collect logs for 30 seconds
echo.
echo Press any key to start log collection...
pause > nul

echo.
echo Clearing old logs...
adb -s EM95IBKZEYIFSO69 logcat -c

echo.
echo Collecting logs for 30 seconds...
echo (Interact with the app now - have a conversation)
echo.

timeout /t 30 /nobreak

echo.
echo Saving logs to audio_test_log.txt...
adb -s EM95IBKZEYIFSO69 logcat -d -v threadtime > audio_test_log.txt

echo.
echo ========================================
echo Log collection complete!
echo ========================================
echo.
echo Filtering for audio-related entries...
findstr /i "AudioEngine AudioTrack startPlayback stopPlayback _isPlaying playbackJob generation clearAudioQueue ERROR WARN" audio_test_log.txt > audio_test_filtered.txt

echo.
echo Files created:
echo - audio_test_log.txt (full log)
echo - audio_test_filtered.txt (filtered for audio)
echo.
echo Please check audio_test_filtered.txt for issues.
echo.
pause
