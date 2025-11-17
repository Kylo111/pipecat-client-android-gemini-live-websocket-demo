## Development Workflow Rules
MASZ ZABRONIONE I NIE WOLNO POD ZADNYM POZOREM ROBIC WORKAROUND JEZELI UZYTKOWNIK NIE POZWOLI

NIE KASUJ TEZ ZADNYCH FUNKCJI ANI FUNKCJONALNOSCI JEZELI UZYTKOWNIK NIE POZWOLIŁ

### Build and Deployment Process

**Local Compilation**: 
- Application is compiled locally on the development machine
- Always use Gradle commands for building and installing

**Connected Device**:
- Android device is connected in debug mode via ADB
- Device ID: `EM95IBKZEYIFSO69`
- Verify device connection before building: `adb devices`

### Mandatory Testing Workflow

**After Every Change**:
1. Compile the application: `./gradlew clean build`
2. Install on the connected device: `./gradlew installDebug`
3. Check logs immediately after installation: `adb -s EM95IBKZEYIFSO69 logcat`
4. Monitor for crashes, errors, or unexpected behavior

**Critical Rule**: 
- NEVER declare success or completion until the user has tested the new build
- Always wait for user confirmation that the feature works as expected
- Do not assume functionality works based on successful compilation alone

### Log Monitoring

**Standard Log Check**:
```bash
adb -s EM95IBKZEYIFSO69 logcat -c && adb -s EM95IBKZEYIFSO69 logcat | grep -i "pipecat\|gemini\|error\|exception"
```

**Clear logs before testing**:
```bash
adb -s EM95IBKZEYIFSO69 logcat -c
```

**Filter for app-specific logs**:
```bash
adb -s EM95IBKZEYIFSO69 logcat | grep "ai.pipecat.gemini_multimodal_websocket_demo"
```

**Filter for lifecycle events**:
```bash
adb -s EM95IBKZEYIFSO69 logcat | grep -E "MainActivity.*Lifecycle|MainActivity.*handle|VoiceService|PorcupineService"
```

### Build Commands

**Full clean build and install**:
```bash
./gradlew clean build && ./gradlew installDebug
```

**Quick install after code changes**:
```bash
./gradlew :gemini-multimodal-websocket-demo:installDebug
```

**Uninstall before fresh install** (if needed):
```bash
adb -s EM95IBKZEYIFSO69 uninstall ai.pipecat.gemini_multimodal_websocket_demo
```

### Testing Protocol

1. Make code changes
2. Build and install on device
3. Check logs for compilation/installation errors
4. Launch app on device
5. Monitor logs during app usage
6. Wait for user to test and provide feedback
7. Only proceed to next task after user confirmation

### Background Operation Testing

**Test scenarios for background operation**:
1. Start a conversation
2. Press home button (app goes to background)
3. Verify session continues (check logs)
4. Speak to the device
5. Verify audio is recorded and bot responds
6. Turn off screen
7. Verify session continues with screen off
8. Return to app
9. Verify session is still active

**Session pause testing**:
1. Start a conversation
2. Press pause button (or use wake word)
3. Verify session pauses (WebSocket closes, audio stops)
4. Press resume (or use wake word)
5. Verify session resumes with session resumption

**Memory pressure testing**:
1. Start a conversation
2. Open many other apps to create memory pressure
3. Verify app handles memory pressure gracefully
4. Check logs for onTrimMemory events
5. Verify forceStop() is called only in critical situations
