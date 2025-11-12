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
│   ├── MainActivity.kt              # Main entry point, connection UI
│   ├── RTVIApplication.kt           # Application class
│   ├── VoiceClientManager.kt        # Core client management and state
│   ├── Preferences.kt               # App preferences (API key storage)
│   ├── ui/                          # Compose UI components
│   │   ├── InCallLayout.kt          # Main in-call screen
│   │   ├── InCallHeader.kt          # Header with timer
│   │   ├── InCallFooter.kt          # Footer with controls
│   │   ├── PermissionScreen.kt      # Permission request UI
│   │   ├── AudioIndicator.kt        # Audio level visualization
│   │   ├── BotIndicator.kt          # Bot status indicator
│   │   ├── UserMicButton.kt         # Mic toggle button
│   │   ├── Timer.kt                 # Call duration timer
│   │   └── theme/                   # Theme configuration
│   │       ├── Color.kt
│   │       ├── Theme.kt
│   │       └── Type.kt
│   └── utils/                       # Utility classes
│       ├── RealTimeClock.kt
│       └── TimeUtils.kt
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

**Client Architecture**: 
- `RTVIClient` from Pipecat library handles WebSocket communication
- `GeminiLiveWebsocketTransport` provides Gemini-specific transport layer
- Event callbacks pattern for real-time updates (speaking, transcripts, metrics)

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
