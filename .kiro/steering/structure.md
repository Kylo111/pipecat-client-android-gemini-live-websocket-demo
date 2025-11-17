## Project Structure

### Root Level
- Single module Android application
- Module name: `gemini-multimodal-websocket-demo`
- Package: `ai.pipecat.gemini_multimodal_websocket_demo`

### Source Organization

```
gemini-multimodal-websocket-demo/src/main/
├── AndroidManifest.xml
├── java/ai/pipecat/gemini_multimodal_websocket_demo/
│   ├── MainActivity.kt              # Main entry point, lifecycle management
│   ├── RTVIApplication.kt           # Application class
│   ├── VoiceClientManager.kt        # Core client management and state
│   ├── VoiceService.kt              # Foreground service for background operation
│   ├── SessionManager.kt            # Session and transcript management
│   ├── LibreChatService.kt          # LibreChat API integration
│   ├── AuthManager.kt               # Authentication management
│   ├── PorcupineService.kt          # Wake word detection service
│   ├── PicovoiceManager.kt          # Picovoice configuration
│   ├── Preferences.kt               # App preferences (API key storage)
│   ├── OfflineConversationManager.kt # Offline conversation storage
│   ├── OfflineSummaryQueue.kt       # Queue for offline summaries
│   ├── ThreadSettingsManager.kt     # Per-thread settings
│   ├── ReconnectionManager.kt       # WebSocket reconnection logic
│   ├── ui/                          # Compose UI components
│   │   ├── InCallLayout.kt          # Main in-call screen
│   │   ├── InCallHeader.kt          # Header with timer
│   │   ├── InCallFooter.kt          # Footer with controls
│   │   ├── LoginScreen.kt           # LibreChat login
│   │   ├── ConversationListScreen.kt # Thread selection
│   │   ├── SettingsScreen.kt        # App settings
│   │   ├── PermissionScreen.kt      # Permission request UI
│   │   ├── AudioIndicator.kt        # Audio level visualization
│   │   ├── BotIndicator.kt          # Bot status indicator
│   │   ├── UserMicButton.kt         # Mic toggle button
│   │   ├── Timer.kt                 # Call duration timer
│   │   └── theme/                   # Theme configuration
│   │       ├── Color.kt
│   │       ├── Theme.kt
│   │       └── Type.kt
│   ├── utils/                       # Utility classes
│   │   ├── RealTimeClock.kt
│   │   ├── TimeUtils.kt
│   │   ├── NetworkMonitor.kt        # Network connectivity monitoring
│   │   ├── BatteryProfiler.kt       # Battery usage profiling
│   │   ├── PerformanceLogger.kt     # Performance logging
│   │   └── ImageProcessor.kt        # Image processing for vision
│   ├── tools/                       # Function calling tools
│   │   ├── ToolDefinitions.kt       # Tool schemas
│   │   └── ToolExecutor.kt          # Tool execution
│   └── models/                      # Data models
│       ├── ConversationItem.kt
│       ├── ThreadSettings.kt
│       └── CustomWakeWord.kt
└── res/                             # Android resources
    ├── drawable/                    # Vector icons
    ├── font/                        # Inter font
    ├── mipmap-*/                    # App icons
    └── values/                      # Strings, themes
```

### Architecture Patterns

**State Management**: Compose mutable state with reactive updates
- `VoiceClientManager` holds all client state as Compose state objects
- UI components observe state changes automatically

**Background Operation**:
- `VoiceService` - Foreground service maintains session in background
- `PorcupineService` - Independent wake word detection service
- Wake locks keep CPU active for audio processing
- Services continue when app is in background or screen is off

**Client Architecture**: 
- `VoiceClientManager` manages WebSocket connection and audio
- `SessionManager` handles LibreChat integration and transcripts
- `ReconnectionManager` handles automatic reconnection
- Event callbacks pattern for real-time updates

**Lifecycle Management**:
- `MainActivity` uses lifecycle observers (DefaultLifecycleObserver)
- `handlePause()` - app goes to background (session continues)
- `handleResume()` - app returns to foreground (session already running)
- `handleStop()` - app no longer visible (session continues)
- `onDestroy()` - cleanup only when activity is finishing
- `onLowMemory()` / `onTrimMemory()` - emergency cleanup on memory pressure

**UI Pattern**: Jetpack Compose with single-activity architecture
- `MainActivity` is the sole activity
- Composable functions for all UI components
- Material3 design system

**Permissions**: Runtime permission handling via Accompanist library
- Microphone and camera permissions requested at runtime
- `PermissionScreen` composable manages permission flow

### Naming Conventions
- Kotlin file names match class names (PascalCase)
- Composable functions use PascalCase
- State variables use camelCase
- Resource files use snake_case
