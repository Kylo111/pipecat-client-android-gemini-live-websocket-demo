@echo off
setlocal enabledelayedexpansion

if not exist audio_test_log.txt (
    echo Error: audio_test_log.txt not found!
    echo Please run quick_audio_test.bat first.
    pause
    exit /b 1
)

echo ========================================
echo AUDIO LOG ANALYSIS
echo ========================================
echo.

echo Analyzing audio_test_log.txt...
echo.

echo ========================================
echo 1. AudioTrack Lifecycle Events
echo ========================================
findstr /i "AudioTrack.*init AudioTrack.*release AudioTrack.*play AudioTrack.*stop" audio_test_log.txt
echo.

echo ========================================
echo 2. Playback State Changes
echo ========================================
findstr /i "startPlayback stopPlayback _isPlaying" audio_test_log.txt
echo.

echo ========================================
echo 3. Queue Operations
echo ========================================
findstr /i "queueAudio clearAudioQueue audioQueue" audio_test_log.txt
echo.

echo ========================================
echo 4. Generation ID Changes
echo ========================================
findstr /i "generation" audio_test_log.txt
echo.

echo ========================================
echo 5. Errors and Warnings
echo ========================================
findstr /i "ERROR WARN FATAL" audio_test_log.txt | findstr /i "audio"
echo.

echo ========================================
echo 6. Mutex Operations
echo ========================================
findstr /i "Mutex withLock audioTrackMutex playbackStateMutex" audio_test_log.txt
echo.

echo ========================================
echo 7. Playback Job Activity
echo ========================================
findstr /i "playbackJob" audio_test_log.txt
echo.

echo ========================================
echo Analysis complete!
echo ========================================
echo.
echo Key things to look for:
echo - Multiple AudioTrack instances at same time
echo - startPlayback called while _isPlaying is true
echo - stopPlayback not completing before next startPlayback
echo - Queue not being cleared properly
echo - Generation ID not incrementing
echo - Any ERROR or WARN messages
echo.
pause
