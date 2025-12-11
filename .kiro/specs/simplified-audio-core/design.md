# Design Document: Simplified Audio Core

## Overview

Radykalne uproszczenie architektury audio z ~5000 linii do ~500 linii, wzorując się na oficjalnym demo Gemini. Eliminujemy:
- VoiceSessionStateMachine (800 linii) - duplikuje logikę Gemini
- ConversationMonitor (300 linii) - własna detekcja ciszy zamiast turnComplete
- SideEffectExecutor (300 linii) - niepotrzebna warstwa abstrakcji
- Custom batching - AudioTrack ma wbudowany bufor
- Zombie Audio Protection - wystarczy flush()

Zostaje prosta architektura: **GeminiClient → AudioEngine → AudioTrack/AudioRecord**

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    VoiceClientManager                        │
│  (Uproszczony - tylko koordynacja, ~300 linii)              │
│                                                              │
│  ┌─────────────────┐         ┌─────────────────┐            │
│  │  GeminiClient   │◄───────►│   AudioEngine   │            │
│  │  (~150 linii)   │         │   (~200 linii)  │            │
│  └────────┬────────┘         └────────┬────────┘            │
│           │                           │                      │
│           │ Events:                   │ Direct I/O:          │
│           │ - audio                   │ - AudioTrack.write() │
│           │ - interrupted             │ - AudioRecord.read() │
│           │ - turnComplete            │ - flush()            │
│           │ - transcription           │                      │
└───────────┼───────────────────────────┼──────────────────────┘
            │                           │
            ▼                           ▼
    ┌───────────────┐           ┌───────────────┐
    │ Gemini Live   │           │ Android Audio │
    │ WebSocket API │           │ AudioTrack    │
    └───────────────┘           │ AudioRecord   │
                                └───────────────┘
```

## Components and Interfaces

### 1. GeminiClient

Prosty klient WebSocket z event-based API (wzorowany na oficjalnym demo).

```kotlin
class GeminiClient(
    private val apiKey: String,
    private val model: String = "gemini-2.0-flash-exp"
) {
    // Events
    var onAudio: ((ByteArray) -> Unit)? = null
    var onInterrupted: (() -> Unit)? = null
    var onTurnComplete: (() -> Unit)? = null
    var onInputTranscription: ((String, Boolean) -> Unit)? = null
    var onOutputTranscription: ((String, Boolean) -> Unit)? = null
    var onError: ((Exception) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    
    // State
    val isConnected: Boolean
    
    // Methods
    suspend fun connect(config: LiveConnectConfig)
    fun disconnect()
    fun sendAudio(audioData: ByteArray)
    fun sendText(text: String)
}
```

### 2. AudioEngine

Uproszczony silnik audio - bezpośredni zapis do AudioTrack, bez batching.
Używa VOICE_COMMUNICATION dla AEC i Kotlin Channel dla non-blocking writes.

```kotlin
class AudioEngine(
    private val outputSampleRate: Int = 24000,
    private val inputSampleRate: Int = 16000,
    private val scope: CoroutineScope
) {
    // Callbacks
    var onAudioRecorded: ((ByteArray) -> Unit)? = null
    var onPlaybackComplete: (() -> Unit)? = null
    
    // State
    val isPlaying: Boolean
    val isRecording: Boolean
    
    // Internal - non-blocking audio channel
    private val audioChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private var writeJob: Job? = null
    
    // AEC
    private var echoCanceler: AcousticEchoCanceler? = null
    
    // Playback (non-blocking - puts to channel)
    fun startPlayback()
    fun queueAudio(pcm16Data: ByteArray)  // Non-blocking, puts to channel
    fun flush()                            // Clear channel and AudioTrack
    fun isPlaybackFinished(): Boolean      // Check if all audio played
    
    // Recording (with AEC)
    fun startRecording()  // Uses VOICE_COMMUNICATION source
    fun stopRecording()
    
    // Lifecycle
    fun release()
    
    // Internal - runs on IO dispatcher
    private suspend fun audioWriteLoop() {
        for (chunk in audioChannel) {
            audioTrack.write(chunk, 0, chunk.size)  // Blocking only this coroutine
        }
    }
}
```

**Threading Model:**
```
WebSocket Thread          Audio Coroutine (Dispatchers.IO)
      │                              │
      │ onMessage(audio)             │
      ▼                              │
 audioChannel.send()  ──────────►  audioChannel.receive()
      │ (non-blocking)               │
      │                              ▼
      │                        audioTrack.write()
      │                         (blocking OK here)
```

### 3. VoiceClientManager (Uproszczony)

Koordynator - łączy GeminiClient z AudioEngine, zarządza lifecycle.

```kotlin
class VoiceClientManager(
    private val context: Context
) {
    // UI State (Compose)
    val connectionState: State<ConnectionState>
    val isBotSpeaking: State<Boolean>
    val userTranscript: State<String>
    val botTranscript: State<String>
    
    // Methods
    suspend fun connect(apiKey: String, systemPrompt: String?)
    fun disconnect()
    fun setMuted(muted: Boolean)
    
    // Internal - event handling
    private fun onGeminiAudio(data: ByteArray)
    private fun onGeminiInterrupted()
    private fun onGeminiTurnComplete()
}
```

## Data Models

### ConnectionState
```kotlin
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
```

### Audio Configuration
```kotlin
object AudioConfig {
    const val OUTPUT_SAMPLE_RATE = 24000
    const val INPUT_SAMPLE_RATE = 16000
    const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
    const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val BUFFER_SIZE_MS = 40  // Minimum buffer for smooth playback
    
    // AEC Configuration
    const val AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_COMMUNICATION  // Enables system AEC
    const val AUDIO_USAGE = AudioAttributes.USAGE_VOICE_COMMUNICATION
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Direct write without batching
*For any* sequence of audio chunks received from Gemini, the AudioEngine SHALL write each chunk directly to AudioTrack without accumulating in intermediate buffers, and the internal queue size SHALL remain below 500ms equivalent at all times.
**Validates: Requirements 1.1, 1.3, 1.4**

### Property 2: Playback latency
*For any* first audio chunk arrival, the time between chunk receipt and AudioTrack.play() call SHALL be less than 100ms.
**Validates: Requirements 1.2**

### Property 3: Turn completion accuracy
*For any* turnComplete event from Gemini, the system SHALL signal end of bot turn only after AudioTrack.getPlaybackHeadPosition() equals total written samples.
**Validates: Requirements 2.1, 2.2**

### Property 4: Interrupt handling
*For any* interrupted event from Gemini, the AudioEngine SHALL call AudioTrack.flush(), clear all pending audio data, and be ready to play new audio within 50ms.
**Validates: Requirements 3.1, 3.2, 3.4**

### Property 5: Full-duplex audio
*For any* period when bot is speaking, the AudioEngine SHALL continue sending recorded audio to Gemini without interruption.
**Validates: Requirements 3.3**

### Property 6: Continuous playback
*For any* sequence of audio chunks, the AudioEngine SHALL write them to AudioTrack without introducing gaps (no silence between chunks).
**Validates: Requirements 4.4**

### Property 7: Transcription forwarding
*For any* transcription event (input or output) from Gemini, the VoiceClientManager SHALL emit it to UI state without blocking audio playback.
**Validates: Requirements 7.1, 7.2, 7.3**

### Property 8: Background operation
*For any* app lifecycle transition to background, the WebSocket connection SHALL remain active and audio recording/playback SHALL continue.
**Validates: Requirements 6.1, 6.2**

### Property 9: Echo cancellation
*For any* audio playback through speakers, the recorded microphone input SHALL NOT contain the played audio (AEC active).
**Validates: Requirements 9.1, 9.2, 9.3, 9.4**

### Property 10: Non-blocking WebSocket
*For any* audio chunk received on WebSocket thread, the queueAudio() call SHALL return within 1ms without blocking on AudioTrack.write().
**Validates: Requirements 10.1, 10.2, 10.3, 10.4**

### Property 11: Bluetooth device routing
*For any* Bluetooth headset connection event, the AudioDeviceHandler SHALL route audio to Bluetooth within 500ms.
**Validates: Requirements 11.1, 11.2, 11.3**

### Property 12: Permission safety
*For any* missing RECORD_AUDIO permission, the AudioEngine SHALL throw PermissionException instead of crashing.
**Validates: Requirements 12.1, 12.2, 12.3**

## Audio Device Routing (Bluetooth/Headset)

### AudioDeviceHandler

Mały helper (~60 linii) do zarządzania routingiem audio:

```kotlin
class AudioDeviceHandler(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    // Callback for hot-plugging
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>?) = updateAudioDevice()
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>?) = updateAudioDevice()
    }
    
    fun start() {
        // 1. Set mode to communication (Critical for VoIP/AEC/Bluetooth)
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        
        // 2. Register callback for hot-plugging
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
        
        // 3. Initial routing
        updateAudioDevice()
    }
    
    fun stop() {
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        audioManager.mode = AudioManager.MODE_NORMAL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
    }
    
    private fun updateAudioDevice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        
        val devices = audioManager.availableCommunicationDevices
        
        // Priority: Bluetooth > Wired Headset > Earpiece > Speaker
        val targetDevice = devices.firstOrNull { 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET 
        } ?: devices.firstOrNull { 
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || 
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES 
        } ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
          ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        
        targetDevice?.let { audioManager.setCommunicationDevice(it) }
    }
}
```

### Integration with VoiceClientManager

```kotlin
class VoiceClientManager(context: Context) {
    private val audioDeviceHandler = AudioDeviceHandler(context)
    
    suspend fun connect(...) {
        audioDeviceHandler.start()  // Before audio starts
        // ... rest of connection
    }
    
    fun disconnect() {
        audioDeviceHandler.stop()  // After audio stops
        // ... rest of cleanup
    }
}
```

## AEC (Acoustic Echo Cancellation) Strategy

### Problem
Na Androidzie (w przeciwieństwie do przeglądarki) mikrofon słyszy głośniki. Bez AEC bot słyszy sam siebie, pętli się i przerywa.

### Rozwiązanie (dwupoziomowe)

1. **System AEC via VOICE_COMMUNICATION**
```kotlin
audioRecord = AudioRecord(
    MediaRecorder.AudioSource.VOICE_COMMUNICATION,  // Włącza systemowe AEC
    inSampleRate,
    AudioFormat.CHANNEL_IN_MONO,
    AudioFormat.ENCODING_PCM_16BIT,
    bufferSize
)
```

2. **Hardware AEC jako backup**
```kotlin
if (AcousticEchoCanceler.isAvailable()) {
    echoCanceler = AcousticEchoCanceler.create(audioRecord.audioSessionId)
    echoCanceler?.enabled = true
}
```

### AudioTrack Configuration for AEC
```kotlin
audioTrack = AudioTrack.Builder()
    .setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build())
    // ...
```

## Error Handling

### WebSocket Errors
- Connection failure → emit ERROR state, allow retry
- Unexpected disconnect → emit DISCONNECTED, cleanup audio
- Message parse error → log and ignore, continue session

### Audio Errors
- AudioTrack creation failure → emit ERROR, prevent session start
- AudioRecord permission denied → emit ERROR with message
- Buffer underrun → continue playback, AudioTrack handles internally

### Recovery Strategy
```kotlin
// Simple error handling - no complex retry logic
fun onError(error: Exception) {
    Log.e(TAG, "Error: ${error.message}")
    disconnect()
    connectionState.value = ConnectionState.ERROR
}
```

## Testing Strategy

### Dual Testing Approach

Używamy zarówno unit testów jak i property-based testów:
- **Unit testy**: konkretne przykłady, edge cases, konfiguracja
- **Property-based testy**: uniwersalne właściwości dla wszystkich inputów

### Property-Based Testing Framework

Używamy **Kotest** z modułem property testing dla Kotlin.

```kotlin
// build.gradle.kts
testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
testImplementation("io.kotest:kotest-property:5.8.0")
```

Konfiguracja: minimum 100 iteracji na property test.

### Test Annotations

Każdy property test MUSI być oznaczony komentarzem:
```kotlin
// **Feature: simplified-audio-core, Property 1: Direct write without batching**
```

### Unit Tests

1. **AudioEngine Configuration**
   - Verify AudioTrack created with MODE_STREAM
   - Verify sample rate 24kHz for output
   - Verify PCM16 format

2. **GeminiClient Events**
   - Verify event callbacks are invoked
   - Verify connection state transitions

3. **Edge Cases**
   - Empty audio chunk handling
   - Rapid connect/disconnect
   - Multiple flush() calls

### Property-Based Tests

1. **Property 1**: Direct write - generate random chunk sequences, verify no batching
2. **Property 2**: Latency - measure time to first play across scenarios
3. **Property 3**: Turn completion - verify position matches written samples
4. **Property 4**: Interrupt - verify flush clears all state
5. **Property 5**: Full-duplex - verify recording continues during playback
6. **Property 6**: Continuous playback - verify no gaps in output
7. **Property 7**: Transcription - verify events forwarded without blocking
8. **Property 8**: Background - verify connection survives lifecycle changes
9. **Property 9**: Echo cancellation - verify AEC is enabled and configured
10. **Property 10**: Non-blocking WebSocket - verify queueAudio returns immediately
11. **Property 11**: Bluetooth routing - verify audio routes to BT when connected
12. **Property 12**: Permission safety - verify graceful handling of missing permissions

### Mocking Strategy

```kotlin
// Interface for AudioTrack to enable mocking
interface AudioOutput {
    fun write(data: ByteArray, offset: Int, size: Int): Int
    fun play()
    fun flush()
    fun stop()
    fun release()
    fun getPlaybackHeadPosition(): Int
}

// Real implementation
class AudioTrackOutput(private val audioTrack: AudioTrack) : AudioOutput {
    override fun write(data: ByteArray, offset: Int, size: Int) = 
        audioTrack.write(data, offset, size)
    // ...
}

// Mock for testing
class MockAudioOutput : AudioOutput {
    val writtenData = mutableListOf<ByteArray>()
    var playbackPosition = 0
    // ...
}
```
