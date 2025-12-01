# Quick Start Guide

**Source Documents:**
- README.md (Development section)

**Last Updated:** 2025-12-01

---

## Prerequisites

- Android Studio (latest version)
- Android device or emulator (Android 8.0+, API 26+)
- Gemini API key
- LibreChat account (optional)

---

## Installation

### 1. Clone the Repository

```bash
git clone <repository-url>
cd pipecat-client-android-gemini-live-websocket-demo
```

### 2. Open in Android Studio

1. Launch Android Studio
2. Select "Open an Existing Project"
3. Navigate to the cloned repository
4. Click "OK"

### 3. Configure API Keys

Create a `.env` file in the project root (or configure in app):

```
GEMINI_API_KEY=your_gemini_api_key_here
```

---

## Building the App

### Clean Build

```bash
./gradlew clean build
```

### Install Debug APK

```bash
./gradlew installDebug
```

### Run on Connected Device

```bash
./gradlew :gemini-multimodal-websocket-demo:installDebug
```

---

## First Run

### 1. Grant Permissions

On first launch, the app will request:
- **Microphone** - Required for voice input
- **Camera** - Required for image sharing (optional)
- **Notifications** - Required for background operation (Android 13+)

### 2. Configure Settings

1. Open the app
2. Tap the settings icon
3. Enter your Gemini API key (if not in .env)
4. Configure LibreChat (optional):
   - Enter LibreChat URL
   - Login with credentials

### 3. Start a Conversation

1. Return to main screen
2. Tap the microphone button
3. Start speaking
4. Bot will respond with voice

---

## Basic Usage

### Starting a Conversation

1. Tap microphone button
2. Wait for "Connected" status
3. Speak naturally
4. Bot responds automatically

### Pausing a Conversation

1. Tap microphone button again
2. Or say "Alexa" (if Picovoice enabled)
3. Session paused, can resume later

### Ending a Conversation

1. Tap the end call button
2. Or navigate back
3. Session ends, transcript saved

### Sending Images

1. During conversation, tap image button
2. Select image from gallery or take photo
3. Image automatically compressed and sent
4. Bot responds with image analysis

---

## Development Commands

### Build Commands

```bash
# Full clean build
./gradlew clean build

# Quick install after code changes
./gradlew :gemini-multimodal-websocket-demo:installDebug

# Uninstall before fresh install
adb uninstall ai.pipecat.gemini_multimodal_websocket_demo
```

### Testing Commands

```bash
# Run unit tests
./gradlew test

# Run specific test
./gradlew test --tests WebSocketErrorClassifierTest

# Check test coverage
./gradlew testDebugUnitTestCoverage
```

### Debugging Commands

```bash
# Check device connection
adb devices

# View app logs
adb logcat | grep "ai.pipecat.gemini_multimodal_websocket_demo"

# Check service status
adb shell dumpsys activity services | grep VoiceService

# Check wake lock status
adb shell dumpsys power | grep "Wake Locks"

# Clear app data (reset)
adb shell pm clear ai.pipecat.gemini_multimodal_websocket_demo

# Monitor battery usage
adb shell dumpsys batterystats | grep "ai.pipecat.gemini_multimodal_websocket_demo"
```

---

## Configuration

### Gemini API Key

**Option 1: Environment Variable**
```bash
export GEMINI_API_KEY=your_key_here
```

**Option 2: In-App Settings**
1. Open app
2. Go to Settings
3. Enter API key
4. Save

### LibreChat Integration

1. Deploy LibreChat instance
2. Get LibreChat URL
3. In app settings:
   - Enter LibreChat URL
   - Login with credentials
4. Conversations automatically synced

### Picovoice Wake Word

1. Go to Settings
2. Enable "Picovoice Wake Word Detection"
3. Say "Alexa" to toggle microphone
4. (Optional) Configure custom wake words

---

## Troubleshooting

### Build Fails

**Issue:** Gradle build fails

**Solution:**
```bash
./gradlew clean
./gradlew build --refresh-dependencies
```

### App Crashes on Launch

**Issue:** App crashes immediately

**Solution:**
1. Check logcat for errors
2. Verify API key is configured
3. Check permissions are granted
4. Try clean install:
```bash
adb uninstall ai.pipecat.gemini_multimodal_websocket_demo
./gradlew installDebug
```

### No Audio Input/Output

**Issue:** Microphone or speaker not working

**Solution:**
1. Check microphone permission granted
2. Check device volume
3. Try enabling speakerphone in settings
4. Check logs for audio errors:
```bash
adb logcat | grep "AudioRecord\|AudioTrack"
```

### Connection Issues

**Issue:** Cannot connect to Gemini

**Solution:**
1. Verify API key is correct
2. Check internet connection
3. Check logs for connection errors:
```bash
adb logcat | grep "VoiceClientManager\|WebSocket"
```

### Background Operation Not Working

**Issue:** Conversation stops when app minimized

**Solution:**
1. Check notification permission granted (Android 13+)
2. Verify VoiceService is running:
```bash
adb shell dumpsys activity services | grep VoiceService
```
3. Check battery optimization settings
4. Ensure wake lock permission granted

---

## Next Steps

- Read [Architecture Documentation](../project/architecture.md)
- Review [Lifecycle Management](../implementation/lifecycle.md)
- Check [Troubleshooting Guide](../operations/troubleshooting.md)
- Explore [Picovoice Setup](picovoice-setup.md)

---

## Support

For issues and questions:
1. Check [Troubleshooting Guide](../operations/troubleshooting.md)
2. Review logs for error messages
3. Check GitHub issues
4. Contact development team

---

**Document Status:** ACTIVE  
**Review Cycle:** Quarterly  
**Next Review:** 2026-03-01
