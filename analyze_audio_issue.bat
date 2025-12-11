@echo off
setlocal enabledelayedexpansion

echo ========================================
echo AUDIO ISSUE DEEP ANALYSIS
echo ========================================
echo.

set OUTPUT_FILE=audio_analysis_%date:~-4,4%%date:~-10,2%%date:~-7,2%_%time:~0,2%%time:~3,2%%time:~6,2%.log
set OUTPUT_FILE=%OUTPUT_FILE: =0%

echo Clearing logcat buffer...
adb -s EM95IBKZEYIFSO69 logcat -c

echo.
echo ========================================
echo SCENARIO 1: AudioTrack Lifecycle
echo ========================================
echo Please start a conversation and let bot speak for 10 seconds
echo Press any key when ready...
pause > nul

echo Collecting AudioTrack lifecycle logs for 15 seconds...
timeout /t 15 /nobreak > nul
adb -s EM95IBKZEYIFSO69 logcat -d -v threadtime | findstr /i "AudioTrack" > %OUTPUT_FILE%

echo.
echo ========================================
echo SCENARIO 2: Playback State Changes
echo ========================================
echo Please interrupt the bot mid-sentence
echo Press any key when ready...
pause > nul

adb -s EM95IBKZEYIFSO69 logcat -c
echo Collecting playback state logs for 10 seconds...
timeout /t 10 /nobreak > nul
adb -s EM95IBKZEYIFSO69 logcat -d -v threadtime | findstr /i "startPlayback stopPlayback _isPlaying" >> %OUTPUT_FILE%

echo.
echo ========================================
echo SCENARIO 3: Queue Operations
echo ========================================
echo Please have a rapid back-and-forth conversation
echo Press any key when ready...
pause > nul

adb -s EM95IBKZEYIFSO69 logcat -c
echo Collecting queue operation logs for 20 seconds...
timeout /t 20 /nobreak > nul
adb -s EM95IBKZEYIFSO69 logcat -d -v threadtime | findstr /i "clearAudioQueue interruptPlayback generation queueAudio" >> %OUTPUT_FILE%

echo.
echo ========================================
echo SCENARIO 4: Mutex and Concurrency
echo ========================================
echo Please trigger rapid bot responses
echo Press any key when ready...
pause > nul

adb -s EM95IBKZEYIFSO69 logcat -c
echo Collecting concurrency logs for 15 seconds...
timeout /t 15 /nobreak > nul
adb -s EM95IBKZEYIFSO69 logcat -d -v threadtime | findstr /i "Mutex withLock audioTrackMutex playbackStateMutex" >> %OUTPUT_FILE%

echo.
echo ========================================
echo SCENARIO 5: Error and Warning Messages
echo ========================================
adb -s EM95IBKZEYIFSO69 logcat -d -v threadtime | findstr /i "ERROR WARN FATAL" | findstr /i "audio" >> %OUTPUT_FILE%

echo.
echo ========================================
echo Analysis complete!
echo ========================================
echo Logs saved to: %OUTPUT_FILE%
echo.
echo Please share this file for analysis.
pause
