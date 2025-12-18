# Picovoice Lifecycle Fix

## Problem
PorcupineService was starting independently when Picovoice was enabled in settings, running continuously even when:
- App was on the main UI (not in conversation)
- App was completely killed
- No conversation was active

This caused:
- Unnecessary battery drain
- Wake word detection sounds playing when not needed
- Microphone access when no conversation was active

## Solution
Changed PorcupineService lifecycle to be tied to conversation sessions:

### Changes Made

#### 1. VoiceService.kt
- **Added**: `startPorcupineServiceIfEnabled()` - Starts PorcupineService when conversation begins
- **Added**: `stopPorcupineServiceIfRunning()` - Stops PorcupineService when conversation ends
- **Modified**: `onStartCommand()` - Now starts PorcupineService when VoiceService starts
- **Modified**: `stopService()` - Now stops PorcupineService when VoiceService stops

#### 2. PicovoiceManager.kt
- **Modified**: `enablePicovoice()` - No longer starts service immediately, just sets enabled flag
- **Modified**: `restartService()` - Only restarts if VoiceService is running (active conversation)
- **Added**: Check for VoiceService.getInstance() to determine if conversation is active

#### 3. RTVIApplication.kt
- **Deprecated**: `startPorcupineService()` - No longer starts service on app launch
- **Removed**: `resumePicovoiceOnAppStart()` - No longer needed

#### 4. BootReceiver.kt
- **Modified**: No longer starts PorcupineService on device boot
- **Added**: Documentation explaining new behavior

## New Behavior

### When Picovoice is Enabled in Settings:
1. **App Launch**: PorcupineService does NOT start
2. **Conversation Starts**: PorcupineService starts automatically with VoiceService
3. **During Conversation**: Wake word detection is active
4. **Conversation Ends**: PorcupineService stops automatically with VoiceService
5. **App in Background**: If conversation is active, PorcupineService continues running
6. **App Killed**: PorcupineService stops (no conversation active)

### When Picovoice is Disabled in Settings:
- PorcupineService never starts, regardless of conversation state

## Benefits

1. **Battery Efficiency**: Wake word detection only runs during active conversations
2. **Privacy**: Microphone access only when needed
3. **User Experience**: No unexpected wake word sounds when not in conversation
4. **Resource Management**: Service lifecycle properly managed

## Testing Instructions

1. Enable Picovoice in Settings
2. Launch app - verify NO PorcupineService notification appears
3. Start a conversation - verify PorcupineService notification appears
4. Test wake word (e.g., "Alexa") - should work during conversation
5. End conversation - verify PorcupineService notification disappears
6. Kill app completely - verify no PorcupineService running

## Logs to Monitor

```bash
# Clear logs
adb -s EM95IBKZEYIFSO69 logcat -c

# Monitor Picovoice lifecycle
adb -s EM95IBKZEYIFSO69 logcat | grep -E "PorcupineService|VoiceService.*Picovoice|PicovoiceManager"
```

Expected log sequence:
1. App launch: "Picovoice is enabled - will start with next conversation"
2. Conversation start: "PorcupineService started for conversation session"
3. Conversation end: "PorcupineService stopped after conversation ended"

## Date
2024-12-17
