# Android Gemini Multimodal Live WebSocket Demo

Demo app for using the Pipecat Android client to connect to Gemini Multimodal Live over a WebSocket connection.

<img alt="screenshot" src="files/screenshot.png" width="600px" />

---

## 📚 Documentation

**For comprehensive technical documentation, see [DOCS_INDEX.md](DOCS_INDEX.md)**

The complete documentation is organized into:
- **Architecture & Design** - System architecture, requirements, and decisions
- **Domain & Models** - Core domain objects and state machines
- **Implementation Details** - Component documentation and interaction sequences
- **Guides** - Quick start and setup instructions

---

## Features

- **Real-time Voice Conversation** - Bidirectional audio streaming with Gemini Live API
- **Background Operation** - Continue conversations when app is minimized or screen is off
- **Wake Word Detection** - Hands-free activation via Picovoice Porcupine
- **Automatic Reconnection** - Intelligent reconnection with exponential backoff
- **Image Sharing** - Send images with automatic compression and validation
- **LibreChat Integration** - Sync conversations and transcripts
- **Session Management** - Pause, resume, and manage conversation sessions
- **Polish UI** - Material3 design with connection status indicators

---

## Quick Start

### Prerequisites

- Android Studio (latest stable version)
- Android device or emulator (API 26+)
- Gemini API key
- (Optional) Picovoice API key for wake word detection
- (Optional) LibreChat instance for conversation sync

### Installation

1. **Clone the repository:**
   ```bash
   git clone <repository-url>
   cd <repository-directory>
   ```

2. **Configure API keys:**
   - Create `.env` file in project root (see `.env.example`)
   - Add your Gemini API key
   - (Optional) Add Picovoice API key for wake word detection

3. **Build and install:**
   ```bash
   ./gradlew clean build
   ./gradlew installDebug
   ```

4. **Run the app:**
   - Launch app on device
   - Enter API key in settings (if not configured in `.env`)
   - Grant microphone permission
   - Start a conversation!

### First Conversation

1. Tap the microphone button to start
2. Speak to the AI assistant
3. The bot will respond with voice
4. Tap the microphone button again to pause
5. Use the hang-up button to end the conversation

**For detailed setup instructions, see [Quick Start Guide](docs/guides/quick-start.md)**

---

## Architecture Overview

### Core Components

- **VoiceClientManager** - WebSocket connection and audio streaming management
- **VoiceService** - Foreground service for background operation
- **SessionManager** - Session lifecycle and transcript management
- **PorcupineService** - Wake word detection service
- **ReconnectionManager** - Automatic reconnection with exponential backoff

### Key Features

**Background Operation:**
- Foreground service keeps conversation active when app is in background
- Wake lock maintains CPU active for audio processing
- Persistent notification shows conversation status

**Connection Stability:**
- Automatic reconnection with exponential backoff (1s, 2s, 4s, 8s, 16s)
- Error classification (recoverable vs fatal)
- User dialog after 5 failed attempts

**Wake Word Detection:**
- Hands-free activation via Picovoice Porcupine
- Built-in wake words: "Jarvis", "Computer", "Alexa", etc.
- Custom wake word support via Picovoice Console

**For detailed architecture documentation, see [Architecture Overview](docs/project/architecture.md)**

---

## Development

### Build Commands

```bash
# Full clean build
./gradlew clean build

# Install debug APK
./gradlew installDebug

# Run on connected device
./gradlew :gemini-multimodal-websocket-demo:installDebug

# Run tests
./gradlew test
```

### Project Structure

```
gemini-multimodal-websocket-demo/src/main/java/
├── MainActivity.kt              # Main entry point
├── VoiceClientManager.kt        # Core client management
├── VoiceService.kt              # Background service
├── SessionManager.kt            # Session management
├── PorcupineService.kt          # Wake word detection
├── ui/                          # Compose UI components
├── utils/                       # Utility classes
├── tools/                       # Function calling tools
└── models/                      # Data models
```

### Technology Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose with Material3
- **Build System:** Gradle with Kotlin DSL
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35

**For detailed development information, see [Quick Start Guide](docs/guides/quick-start.md)**

---

## Troubleshooting

### Common Issues

**Connection problems:**
- Verify API key is valid
- Check network connectivity
- Review logs: `adb logcat | grep "VoiceClientManager"`

**Background operation not working:**
- Check WAKE_LOCK permission granted
- Verify VoiceService is running
- Check battery optimization settings

**Wake word not detecting:**
- Verify Picovoice API key is valid
- Check microphone permission granted
- Review logs: `adb logcat | grep "PorcupineService"`

**For detailed troubleshooting, see [Picovoice Setup Guide](docs/guides/picovoice-setup.md)**

---

## Documentation

### Quick Links

- **[Documentation Index](DOCS_INDEX.md)** - Complete documentation navigation
- **[Quick Start Guide](docs/guides/quick-start.md)** - Installation and setup
- **[Picovoice Setup](docs/guides/picovoice-setup.md)** - Wake word configuration
- **[Architecture](docs/project/architecture.md)** - System architecture
- **[Components](docs/implementation/components.md)** - Component documentation
- **[Requirements](docs/project/requirements.md)** - Functional requirements

### Documentation Structure

```
docs/
├── project/          # Requirements, architecture, decisions
├── domain/           # Domain models and state machines
├── implementation/   # Component details and interactions
├── operations/       # Security, errors, troubleshooting
├── testing/          # Test strategy and results
└── guides/           # User guides and tutorials
```

---

## Performance

### Target Metrics

- **Connection Stability:** Reconnection success rate > 95%
- **Reconnection Speed:** Average < 5 seconds
- **Battery Usage:** < 5% per hour during conversation
- **Image Processing:** < 2 seconds for typical images

### Monitoring

Performance metrics are logged automatically. Check logs with:
```bash
adb logcat | grep "PerformanceLogger\|BatteryProfiler"
```

---

## License

[Add your license information here]

---

## Contributing

[Add contribution guidelines here]

---

## Support

For questions or issues:
1. Check [DOCS_INDEX.md](DOCS_INDEX.md) for comprehensive documentation
2. Review [Troubleshooting Guide](docs/guides/picovoice-setup.md#troubleshooting)
3. Check archived documentation in `/archive/` for historical context
4. Contact the development team

---

**Last Updated:** 2025-12-01
