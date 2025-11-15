# Android Gemini Multimodal Live Websocket Demo

Demo app for using the Pipecat Android client to connect to Gemini
Multimodal Live over a Websocket connection.

<img alt="screenshot" src="files/screenshot.png" width="600px" />

## Features

- **Real-time Voice Conversation**: Bidirectional audio streaming with Gemini Live API
- **Automatic Reconnection**: Intelligent reconnection with exponential backoff strategy
- **Image Sharing**: Send images with automatic compression and validation
- **Background Operation**: Continue conversations when app is minimized or screen is off
- **Connection Stability**: Enhanced error handling and timeout configuration
- **Polish UI**: Material3 design with connection status indicators

## Architecture

### Core Components

#### VoiceClientManager
The central component managing WebSocket connections, audio streaming, and client state.

**Key Features:**
- Connection state management (DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, DISCONNECTING)
- Automatic reconnection with exponential backoff (1s, 2s, 4s, 8s, 16s)
- Image processing and compression before sending
- Thread-safe AudioTrack synchronization
- Error classification (recoverable vs fatal)

**Configuration:**
- WebSocket read timeout: 60 seconds
- Ping interval: 15 seconds (for faster connection problem detection)
- Max reconnection attempts: 5 (then shows user dialog)

#### VoiceService
Foreground service enabling background operation during active conversations.

**Features:**
- Persistent notification showing conversation status
- Wake lock management for screen-off operation
- Automatic lifecycle management
- Notification actions for ending conversation

**Wake Lock Configuration:**
- Type: PARTIAL_WAKE_LOCK (keeps CPU running, allows screen off)
- Timeout: 2 hours (safety measure)
- Automatically released on session end

#### ReconnectionManager
Handles automatic reconnection logic with user interaction.

**Reconnection Strategy:**
1. Attempt 1: Wait 1 second
2. Attempt 2: Wait 2 seconds
3. Attempt 3: Wait 4 seconds
4. Attempt 4: Wait 8 seconds
5. Attempt 5: Wait 16 seconds
6. After 5 attempts: Show dialog asking user to continue or end session

**User Options:**
- "Kontynuuj" - Resets counter and continues reconnection
- "Zakończ rozmowę" - Ends session and navigates to thread list

#### ImageProcessor
Validates, compresses, and resizes images before transmission.

**Image Processing Parameters:**
- Max raw size: 5MB (before processing)
- Compression quality: 85% JPEG
- Max dimension: 2300px (longest side, maintains aspect ratio)
- Max final size: ~7MB (after Base64 encoding)

**Processing Steps:**
1. Load image with efficient memory usage (inSampleSize)
2. Resize if longest dimension > 2300px
3. Compress to 85% JPEG quality
4. Validate final size
5. Queue for retry if connection lost during send

#### WebSocketErrorClassifier
Classifies errors to determine appropriate response strategy.

**Error Categories:**

**RECOVERABLE** (triggers automatic reconnection):
- SocketTimeoutException - Network timeout
- UnknownHostException - DNS failure
- IOException - General I/O error
- ConnectException - Connection refused
- EOFException - Connection closed unexpectedly
- Ping-pong timeout errors

**FATAL** (shows error, no retry):
- SSLException - Certificate error
- ProtocolException - Protocol mismatch
- IllegalStateException - Programming error
- SecurityException - Permission denied

**UNKNOWN** (logged and treated as recoverable)

#### TranscriptSyncManager
Ensures reliable transcript synchronization with LibreChat.

**Features:**
- Infinite retry with exponential backoff
- Progress indicator showing attempt count
- Blocks new conversations until sync completes
- User can cancel with warning

### UI Components

#### ConnectionStatusIndicator
Shows current connection state in the conversation screen.

**States:**
- "Połączono" (green) - CONNECTED
- "Ponowne łączenie... próba X z 5" (yellow) - RECONNECTING
- "Rozłączono" (red) - DISCONNECTED

#### ReconnectionDialog
Appears after 5 failed reconnection attempts.

**Options:**
- "Kontynuuj" - Resets attempt counter and continues reconnection
- "Zakończ rozmowę" - Ends session and returns to thread list

#### BackPressHandler
Manages back button behavior based on connection state.

**Behavior:**
- Active conversation (CONNECTED/RECONNECTING): Shows confirmation dialog
- Disconnected: Navigates to thread list without dialog
- Thread list screen: Exits app

#### ImageProcessingIndicator
Shows progress during image processing.

**Display:**
- Progress bar
- Text: "Przetwarzanie obrazu..."
- Appears during compression and resize operations

## Background Operation

### Requirements

**Permissions:**
- `FOREGROUND_SERVICE` - Required for background audio streaming
- `WAKE_LOCK` - Keeps CPU running when screen is off
- `POST_NOTIFICATIONS` - Shows persistent notification (Android 13+)

**Manifest Configuration:**
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".VoiceService"
    android:foregroundServiceType="microphone"
    android:exported="false" />
```

### Lifecycle Behavior

**App Goes to Background (onPause):**
1. VoiceService starts as foreground service
2. Persistent notification appears
3. Wake lock acquired (PARTIAL_WAKE_LOCK)
4. Conversation continues

**App Returns to Foreground (onResume):**
1. UI updates with current state
2. Service continues running
3. Wake lock remains active

**Session Ends or Timeout:**
1. VoiceService stops
2. Wake lock released
3. Notification dismissed
4. Resources cleaned up

### Battery Optimization

**Estimated Battery Usage:**
- Active conversation: ~5% per hour
- Background operation: ~3-4% per hour (screen off)

**Optimization Strategies:**
- PARTIAL_WAKE_LOCK (not FULL_WAKE_LOCK)
- Efficient ping interval (15 seconds)
- Exponential backoff for reconnection
- Service stops immediately when session ends

## Error Handling

### User-Facing Error Messages

All error messages are in Polish and user-friendly:

| Error Type | Message |
|------------|---------|
| Network timeout | "Przekroczono limit czasu połączenia" |
| DNS failure | "Nie można znaleźć serwera" |
| Connection refused | "Serwer niedostępny" |
| SSL error | "Błąd certyfikatu SSL" |
| Image too large | "Obraz za duży" |
| Image processing failed | "Nie udało się przetworzyć obrazu" |

### Logging Strategy

**Production Logs:**
- Connection state changes
- Reconnection attempts with reason
- Image processing results (size before/after)
- Error classifications
- Service lifecycle events

**Debug Logs:**
- WebSocket message details
- Audio buffer statistics
- Detailed error stack traces
- Performance metrics

## Troubleshooting Guide

### Connection Issues

**Problem: Frequent disconnections**
- Check network stability
- Verify API key is valid
- Check device logs: `adb logcat | grep "VoiceClientManager"`
- Look for ping-pong timeout messages

**Problem: Reconnection fails repeatedly**
- Verify internet connection
- Check if Gemini API is accessible
- Review error classification in logs
- Try manual reconnection after network stabilizes

**Problem: "Ping-pong timeout" errors**
- Normal on unstable networks
- Automatic reconnection should handle this
- If persistent, check network quality
- Consider switching to more stable network

### Image Sending Issues

**Problem: Images fail to send**
- Check image size (should be < 5MB raw)
- Verify image format (JPEG, PNG supported)
- Check logs for OutOfMemoryError
- Try smaller image or better quality photo

**Problem: "Obraz za duży" error**
- Image exceeds processing limits
- Try taking photo at lower resolution
- Use image editing app to reduce size first
- Check available device memory

**Problem: Image queued but not sent after reconnection**
- Check connection status indicator
- Verify reconnection succeeded
- Check logs for retry attempts
- Try manual resend if automatic retry fails

### Background Operation Issues

**Problem: Conversation stops when screen turns off**
- Check WAKE_LOCK permission granted
- Verify VoiceService is running: `adb shell dumpsys activity services | grep VoiceService`
- Check battery optimization settings (should exclude app)
- Review logs for wake lock acquisition

**Problem: Notification not showing**
- Check POST_NOTIFICATIONS permission (Android 13+)
- Verify notification channel created
- Check notification settings for app
- Review logs for notification creation errors

**Problem: High battery drain**
- Normal: ~5% per hour during conversation
- Check wake lock is released after session ends
- Verify service stops when conversation ends
- Review logs for wake lock leaks

### UI Issues

**Problem: Back button doesn't show confirmation**
- Check connection state (should be CONNECTED or RECONNECTING)
- Verify BackPressHandler is active
- Check logs for dialog display
- Try disconnecting and reconnecting

**Problem: Connection status not updating**
- Check StateFlow observation in UI
- Verify VoiceClientManager state changes
- Review logs for state transitions
- Try force-closing and reopening app

**Problem: Stuck in RECONNECTING state**
- Check network connectivity
- Review reconnection attempt logs
- Try manual disconnect/reconnect
- Check if max attempts dialog should appear

### Performance Issues

**Problem: Image processing takes too long**
- Normal: < 2 seconds for typical images
- Large images (>10MB) may take longer
- Check device memory availability
- Try closing other apps to free memory

**Problem: Audio latency or stuttering**
- Check network quality (ping, bandwidth)
- Verify AudioTrack synchronization working
- Review logs for buffer underruns
- Try more stable network connection

### Debugging Commands

**Check device connection:**
```bash
adb devices
```

**View app logs:**
```bash
adb logcat | grep "ai.pipecat.gemini_multimodal_websocket_demo"
```

**Check service status:**
```bash
adb shell dumpsys activity services | grep VoiceService
```

**Check wake lock status:**
```bash
adb shell dumpsys power | grep "Wake Locks"
```

**Clear app data (reset):**
```bash
adb shell pm clear ai.pipecat.gemini_multimodal_websocket_demo
```

**Monitor battery usage:**
```bash
adb shell dumpsys batterystats | grep "ai.pipecat.gemini_multimodal_websocket_demo"
```

## Development

### Build Commands

**Full clean build:**
```bash
./gradlew clean build
```

**Install debug APK:**
```bash
./gradlew installDebug
```

**Run on connected device:**
```bash
./gradlew :gemini-multimodal-websocket-demo:installDebug
```

### Testing

**Run unit tests:**
```bash
./gradlew test
```

**Run specific test:**
```bash
./gradlew test --tests WebSocketErrorClassifierTest
```

**Check test coverage:**
```bash
./gradlew testDebugUnitTestCoverage
```

### Code Structure

```
gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/
├── MainActivity.kt                    # Main entry point, lifecycle management
├── VoiceClientManager.kt              # Core client management and state
├── VoiceService.kt                    # Background service for conversations
├── SessionManager.kt                  # Session and transcript management
├── Preferences.kt                     # App preferences (API key storage)
├── ui/
│   ├── InCallLayout.kt                # Main in-call screen
│   ├── ConnectionStatusIndicator.kt   # Connection state display
│   ├── ReconnectionDialog.kt          # Reconnection user dialog
│   ├── BackPressHandler.kt            # Back button handling
│   ├── ImageProcessingIndicator.kt    # Image processing progress
│   └── TranscriptSyncIndicator.kt     # Transcript sync progress
└── utils/
    ├── ImageProcessor.kt              # Image compression and validation
    ├── WebSocketErrorClassifier.kt    # Error classification
    ├── PerformanceLogger.kt           # Performance monitoring
    └── BatteryProfiler.kt             # Battery usage tracking
```

## Performance Metrics

### Target Metrics

- **Connection Stability**: Reconnection success rate > 95%
- **Reconnection Speed**: Average < 5 seconds
- **Image Processing**: < 2 seconds for typical images
- **Battery Usage**: < 5% per hour during conversation
- **Memory Usage**: Optimized for devices with 2GB+ RAM

### Monitoring

Performance metrics are logged automatically:
- Image processing time and size reduction
- Reconnection attempts and success rate
- Battery usage during background operation
- Memory usage during image processing

Check logs with:
```bash
adb logcat | grep "PerformanceLogger\|BatteryProfiler"
```

## License

[Add your license information here]

## Contributing

[Add contribution guidelines here]