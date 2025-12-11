@echo off
echo ========================================
echo AUDIO DEBUG LOG COLLECTION
echo ========================================
echo.
echo This script will collect detailed logs for audio issues.
echo Please follow these steps:
echo.
echo 1. Start the app and begin a conversation
echo 2. Wait for bot to speak (listen for glitches/pops/overlaps)
echo 3. Press Ctrl+C when you hear the audio problem
echo.
echo Starting log collection in 5 seconds...
timeout /t 5 /nobreak > nul
echo.
echo Collecting logs... (Press Ctrl+C when audio problem occurs)
echo.

adb -s EM95IBKZEYIFSO69 logcat -v threadtime | findstr /i "AudioEngine AudioTrack startPlayback stopPlayback clearAudioQueue interruptPlayback playbackJob _isPlaying audioTrackMutex playbackStateMutex generation ERROR WARN"
