## Technology Stack

### Build System
- Gradle with Kotlin DSL (`.gradle.kts`)
- Android Gradle Plugin 8.5.2
- Kotlin 2.0.20

### Core Technologies
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material3
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35
- **Compile SDK**: 34
- **JVM Target**: 1.8

### Key Libraries
- `ai.pipecat:gemini-live-websocket-transport` (0.3.4) - Core Pipecat client for Gemini integration
- Jetpack Compose BOM (2024.09.03)
- AndroidX Core KTX, Lifecycle Runtime KTX, Activity Compose
- Accompanist Permissions (0.34.0) - Runtime permission handling
- ConstraintLayout Compose (1.0.1)
- Kotlinx Serialization JSON (1.7.1)

### Common Commands

Build the project:
```bash
./gradlew build
```

Clean build:
```bash
./gradlew clean build
```

Install debug APK to connected device:
```bash
./gradlew installDebug
```

Run the app:
```bash
./gradlew :gemini-multimodal-websocket-demo:installDebug
```

Check dependencies:
```bash
./gradlew dependencies
```

### Build Configuration
- Version catalog managed in `gradle/libs.versions.toml`
- ProGuard rules defined but minification disabled in debug/release builds
- Compose compiler plugin enabled
- BuildConfig feature enabled for runtime configuration
