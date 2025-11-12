# Design Document

## Overview

This design document outlines the architecture and implementation approach for enhancing the Gemini multimodal voice demo application with advanced configuration capabilities, image sharing, and improved user experience. The enhancements include upgrading to the latest Gemini model (gemini-2.5-flash-native-audio-preview-09-2025), implementing a comprehensive settings screen, adding image capture/upload functionality during live sessions, preventing screen timeout, and improving audio volume.

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        MainActivity                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Connect      │  │ Settings     │  │ InCall Layout    │  │
│  │ Screen       │  │ Screen       │  │ + Image Button   │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   VoiceClientManager                         │
│  • Model Configuration (gemini-2.5-flash-native-audio...)   │
│  • Voice Configuration (speech_config)                       │
│  • System Prompt Configuration                               │
│  • Image Sending (sendRealtimeInput)                        │
│  • Wake Lock Management                                      │
│  • Audio Volume Control                                      │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      Preferences                             │
│  • API Key (existing)                                        │
│  • System Prompt (new StringPref)                           │
│  • Selected Voice (new StringPref)                           │
│  • Model Name (new StringPref)                               │
└─────────────────────────────────────────────────────────────┘
```

### Component Interaction Flow

1. **App Launch**: Load preferences (API key, system prompt, voice, model)
2. **Settings Screen**: User configures settings → Save to SharedPreferences
3. **Connection**: VoiceClientManager uses saved preferences to configure Gemini client
4. **Live Session**: User can send images without interrupting audio stream
5. **Screen Management**: Wake lock acquired on connection, released on disconnect

## Components and Interfaces

### 1. Enhanced Preferences System

**New Preference Fields**:
```kotlin
object Preferences {
    val apiKey = StringPref(PREF_API_KEY)                    // existing
    val systemPrompt = StringPref(PREF_SYSTEM_PROMPT)        // new
    val selectedVoice = StringPref(PREF_SELECTED_VOICE)      // new
    val modelName = StringPref(PREF_MODEL_NAME)              // new
}
```

**Default Values**:
- System Prompt: "You are a helpful assistant"
- Selected Voice: "Puck"
- Model Name: "gemini-2.5-flash-native-audio-preview-09-2025"

### 2. Settings Screen UI

**New Composable**: `SettingsScreen.kt`

**UI Structure**:
```
┌─────────────────────────────────────────┐
│  ← Settings                             │
├─────────────────────────────────────────┤
│  Model                                  │
│  ┌───────────────────────────────────┐ │
│  │ gemini-2.5-flash-native-audio...  │ │
│  └───────────────────────────────────┘ │
│                                         │
│  API Key                                │
│  ┌───────────────────────────────────┐ │
│  │ ••••••••••••••••••••••••••••••••  │ │
│  └───────────────────────────────────┘ │
│                                         │
│  System Prompt                          │
│  ┌───────────────────────────────────┐ │
│  │ You are a helpful assistant       │ │
│  │                                   │ │
│  │                                   │ │
│  └───────────────────────────────────┘ │
│                                         │
│  Voice                                  │
│  ┌───────────────────────────────────┐ │
│  │ Puck ▼                            │ │
│  └───────────────────────────────────┘ │
│                                         │
│              [Save Settings]            │
└─────────────────────────────────────────┘
```

**Voice Options** (30 voices):
- Zephyr, Puck, Charon, Kore, Fenrir, Leda, Orus, Aoede
- Callirrhoe, Autonoe, Enceladus, Iapetus, Umbriel, Algieba
- Despina, Erinome, Algenib, Rasalgethi, Laomedeia, Achernar
- Alnilam, Schedar, Gacrux, Pulcherrima, Achird, Zubenelgenubi
- Vindemiatrix, Sadachbia, Sadaltager, Sulafat

**Navigation**:
- Settings button in InCallHeader or ConnectSettings screen
- Back button returns to previous screen
- Settings persist automatically on save

### 3. Enhanced VoiceClientManager

**New Configuration Method**:
```kotlin
fun start() {
    val apiKey = Preferences.apiKey.value ?: return
    val systemPrompt = Preferences.systemPrompt.value ?: "You are a helpful assistant"
    val voiceName = Preferences.selectedVoice.value ?: "Puck"
    val model = Preferences.modelName.value ?: "gemini-2.5-flash-native-audio-preview-09-2025"
    
    val config = GeminiLiveWebsocketTransport.buildConfig(
        apiKey = apiKey,
        model = model,
        systemInstruction = Value.Object(
            "parts" to Value.Object(
                "text" to Value.Str(systemPrompt)
            )
        ),
        speechConfig = Value.Object(
            "voiceConfig" to Value.Object(
                "prebuiltVoiceConfig" to Value.Object(
                    "voiceName" to Value.Str(voiceName)
                )
            )
        )
    )
    
    // Acquire wake lock
    acquireWakeLock()
    
    // ... rest of connection logic
}
```

**Image Sending Method**:
```kotlin
fun sendImage(imageUri: Uri) {
    val imageBytes = context.contentResolver.openInputStream(imageUri)?.readBytes()
    imageBytes?.let { bytes ->
        client.value?.sendRealtimeInput(
            image = Value.Object(
                "data" to Value.Str(Base64.encodeToString(bytes, Base64.NO_WRAP)),
                "mimeType" to Value.Str(getMimeType(imageUri))
            )
        )?.displayErrors()
    }
}
```

### 4. Image Capture/Selection Component

**New Composable**: `ImageButton.kt` in InCallFooter

**Image Selection Flow**:
1. User taps image button
2. Bottom sheet shows options: "Camera" or "Gallery"
3. Camera: Launch camera intent → capture photo → send to Gemini
4. Gallery: Launch gallery picker → select image → send to Gemini

**Android Permissions Required**:
- `android.permission.CAMERA` (for camera capture)
- `android.permission.READ_MEDIA_IMAGES` (Android 13+)
- `android.permission.READ_EXTERNAL_STORAGE` (Android 12 and below)

**Image Processing**:
- Support PNG and JPG formats
- Convert to Base64 for transmission
- Compress if image exceeds size limits (e.g., 4MB)
- Maintain aspect ratio during compression

### 5. Wake Lock Management

**Implementation in VoiceClientManager**:
```kotlin
private var wakeLock: PowerManager.WakeLock? = null

private fun acquireWakeLock() {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = powerManager.newWakeLock(
        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
        "GeminiDemo::VoiceSessionWakeLock"
    )
    wakeLock?.acquire()
}

private fun releaseWakeLock() {
    wakeLock?.release()
    wakeLock = null
}
```

**Lifecycle**:
- Acquire: When `onConnected()` callback fires
- Release: When `onDisconnected()` callback fires
- Auto-release: Android system handles if app crashes

**Manifest Permission**:
```xml
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

### 6. Audio Volume Enhancement

**Approach**: Modify audio stream volume programmatically

**Implementation Options**:

**Option A - System Volume Control**:
```kotlin
private fun increaseAudioVolume() {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
    audioManager.setStreamVolume(
        AudioManager.STREAM_VOICE_CALL,
        (maxVolume * 0.9).toInt(), // 90% of max
        0
    )
}
```

**Option B - Audio Track Amplification** (if direct access to audio stream):
```kotlin
private fun amplifyAudioData(audioData: ByteArray, factor: Float = 2.0f): ByteArray {
    val shortArray = ShortArray(audioData.size / 2)
    ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortArray)
    
    for (i in shortArray.indices) {
        val amplified = (shortArray[i] * factor).toInt()
        shortArray[i] = amplified.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
    
    val result = ByteArray(audioData.size)
    ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shortArray)
    return result
}
```

**Recommendation**: Start with Option A (system volume) as it's simpler and doesn't require audio stream manipulation. If insufficient, implement Option B.

## Data Models

### Voice Model
```kotlin
data class Voice(
    val name: String,
    val displayName: String,
    val description: String = ""
)

object VoiceList {
    val voices = listOf(
        Voice("Zephyr", "Zephyr", "Bright"),
        Voice("Puck", "Puck", "Upbeat, Clear"),
        Voice("Charon", "Charon", "Informative"),
        Voice("Kore", "Kore", "Firm, Confident"),
        // ... all 30 voices
    )
}
```

### Settings State
```kotlin
data class SettingsState(
    val apiKey: String = "",
    val systemPrompt: String = "You are a helpful assistant",
    val selectedVoice: String = "Puck",
    val modelName: String = "gemini-2.5-flash-native-audio-preview-09-2025"
)
```

## Error Handling

### Configuration Errors
- **Missing API Key**: Show error dialog before attempting connection
- **Invalid Voice Name**: Fall back to default "Puck"
- **Empty System Prompt**: Use default prompt

### Image Sending Errors
- **Permission Denied**: Show permission rationale dialog
- **Image Too Large**: Compress and retry, show warning if compression fails
- **Network Error**: Display error message, don't disconnect session
- **Unsupported Format**: Convert to JPG or show error

### Wake Lock Errors
- **Permission Denied**: Log warning, continue without wake lock
- **Already Acquired**: Check if wake lock exists before acquiring

### Audio Volume Errors
- **Permission Denied**: Continue with default volume
- **Audio Manager Unavailable**: Log error, skip volume adjustment

## Testing Strategy

### Unit Tests (Optional)
- Preferences read/write operations
- Voice list validation
- Image compression logic
- Base64 encoding/decoding

### Integration Tests
1. **Settings Persistence**: Save settings → restart app → verify settings loaded
2. **Model Configuration**: Connect with custom model → verify in logs
3. **Voice Configuration**: Select voice → connect → verify voice in response
4. **System Prompt**: Set custom prompt → verify bot behavior matches
5. **Image Sending**: Send image during session → verify session continues
6. **Wake Lock**: Start session → verify screen stays on → disconnect → verify wake lock released

### Manual Testing Protocol
1. Install app on device: `./gradlew installDebug`
2. Open settings → configure all fields → save
3. Connect to Gemini → verify connection with new settings
4. Monitor logs: `adb -s EM95IBKZEYIFSO69 logcat | grep -i "pipecat\|gemini"`
5. Test image sending: capture photo → send → verify in logs
6. Test wake lock: start session → wait 2 minutes → verify screen stays on
7. Test audio volume: listen to bot response → verify audible
8. Disconnect → verify wake lock released
9. Restart app → verify settings persisted

### Device Testing
- **Target Device**: Connected Android device (ID: EM95IBKZEYIFSO69)
- **Build Command**: `./gradlew clean build && ./gradlew installDebug`
- **Log Monitoring**: `adb -s EM95IBKZEYIFSO69 logcat -c && adb -s EM95IBKZEYIFSO69 logcat | grep -i "pipecat\|gemini\|error\|exception"`

## Implementation Notes

### Gemini Live API Configuration

Based on Gemini Live API documentation, the configuration structure should be:

```kotlin
val config = mapOf(
    "response_modalities" to listOf("AUDIO"),
    "speech_config" to mapOf(
        "voice_config" to mapOf(
            "prebuilt_voice_config" to mapOf(
                "voice_name" to voiceName
            )
        )
    ),
    "system_instruction" to mapOf(
        "parts" to listOf(
            mapOf("text" to systemPrompt)
        )
    )
)
```

### Model Parameter

The model name should be passed to the transport factory or connection method, not in the config object. Check `GeminiLiveWebsocketTransport` API for exact parameter location.

### Image Sending API

Images are sent using `sendRealtimeInput` or similar method with:
```kotlin
mapOf(
    "mime_type" to "image/jpeg",
    "data" to base64EncodedImageString
)
```

### Navigation State Management

Use Compose navigation or simple state management:
```kotlin
enum class Screen {
    CONNECT,
    SETTINGS,
    IN_CALL
}

val currentScreen = mutableStateOf(Screen.CONNECT)
```

## Design Decisions and Rationales

### 1. Settings Screen as Separate Composable
**Decision**: Create dedicated `SettingsScreen.kt` instead of expanding `ConnectSettings`
**Rationale**: Separation of concerns, easier to maintain, can be accessed during or before connection

### 2. Wake Lock Scope
**Decision**: Acquire wake lock only during active session
**Rationale**: Battery efficiency, follows Android best practices, automatic cleanup on disconnect

### 3. Voice Selection UI
**Decision**: Dropdown/Spinner instead of radio buttons
**Rationale**: 30 voices would make radio button list too long, dropdown is more compact

### 4. Image Sending Without Interruption
**Decision**: Use existing WebSocket connection for image data
**Rationale**: Gemini Live API supports multimodal input on same connection, no need for separate channel

### 5. Audio Volume Approach
**Decision**: Start with system volume control
**Rationale**: Simpler implementation, less risk of audio distortion, can upgrade to stream amplification if needed

### 6. Model Name as Configurable
**Decision**: Allow model name to be changed in settings
**Rationale**: Future-proofing for new model versions, easier testing with different models

### 7. Preferences Storage
**Decision**: Continue using SharedPreferences with existing Preferences pattern
**Rationale**: Consistent with existing codebase, simple and reliable for key-value storage
