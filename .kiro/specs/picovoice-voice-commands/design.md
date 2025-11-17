# Design Document

## Overview

This document outlines the technical design for integrating Picovoice Porcupine wake word detection into the Android application. The system will enable hands-free voice control through two types of wake words:

1. **System Wake Words**: Built-in commands (start/stop/koniec) for controlling the active voice session
2. **Custom Wake Words**: User-created wake words for launching specific conversation threads from background

The implementation uses Picovoice Porcupine SDK running in a Foreground Service that continuously listens for wake words, even when the app is in the background.

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        MainActivity                          │
│  ┌────────────────┐  ┌──────────────┐  ┌─────────────────┐ │
│  │ SettingsScreen │  │ ThreadConfig │  │ InCallLayout    │ │
│  │ (Picovoice UI) │  │ (Wake Word   │  │ (Voice Session) │ │
│  │                │  │  Assignment) │  │                 │ │
│  └────────────────┘  └──────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ Intent (launch/control)
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     PorcupineService                         │
│                   (Foreground Service)                       │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              PorcupineManager                         │  │
│  │  - Listens for wake words continuously               │  │
│  │  - Loads system + custom .ppn files                  │  │
│  │  - Triggers callbacks on detection                   │  │
│  └──────────────────────────────────────────────────────┘  │
│                              │                               │
│                              │ Wake word detected            │
│                              ▼                               │
│  ┌──────────────────────────────────────────────────────┐  │
│  │         WakeWordHandler                               │  │
│  │  - System commands: start/stop/koniec                │  │
│  │  - Custom commands: launch thread                    │  │
│  │  - Play activation sound                             │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ Persistent storage
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  PicovoicePreferences                        │
│  - Access key                                                │
│  - Sensitivity settings                                      │
│  - Custom wake words metadata                                │
│  - Thread-wake word associations                             │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ File storage
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Internal Storage (/files/picovoice)             │
│  - Custom .ppn files                                         │
│  - System .ppn files (built-in)                              │
└─────────────────────────────────────────────────────────────┘
```


### Component Interaction Flow

#### Scenario 1: User enables Picovoice in Settings
1. User toggles Picovoice ON in SettingsScreen
2. SettingsScreen calls `PicovoiceManager.enablePicovoice()`
3. PicovoiceManager starts PorcupineService as Foreground Service
4. PorcupineService initializes PorcupineManager with system wake words
5. Service displays persistent notification "Nasłuchiwanie komend głosowych..."

#### Scenario 2: User adds custom wake word
1. User clicks "Add Wake Word" in Picovoice settings panel
2. Dialog shows with name input field
3. User enters wake word name (e.g., "asystent")
4. App displays instruction dialog with:
   - Step-by-step guide
   - Link to https://console.picovoice.ai
   - "Import .ppn file" button
5. User creates wake word in Picovoice Console
6. User downloads .ppn file
7. User clicks "Import .ppn file" → file picker opens
8. User selects downloaded .ppn file
9. App validates and copies file to internal storage
10. Wake word status changes to green (ready)

#### Scenario 3: User assigns wake word to thread
1. User opens ThreadConfigDialog for a conversation
2. Dialog shows "Wake Word" dropdown with available custom wake words
3. User selects wake word from list
4. App saves association: `threadId → wakeWordId`
5. PorcupineService reloads wake words to include this custom .ppn
6. Wake word disappears from available list for other threads

#### Scenario 4: Background wake word detection (custom)
1. PorcupineService continuously listens via PorcupineManager
2. User speaks custom wake word (e.g., "asystent")
3. PorcupineManager callback triggered with keywordIndex
4. WakeWordHandler identifies associated threadId
5. Service plays activation sound
6. Service creates Intent with threadId extra
7. Service launches MainActivity with FLAG_ACTIVITY_NEW_TASK
8. MainActivity receives Intent, extracts threadId
9. MainActivity auto-starts conversation with that thread
10. VoiceClientManager connects to Gemini Live

#### Scenario 5: In-app wake word detection (system)
1. User is in active voice session (InCallLayout)
2. PorcupineService detects "stop" wake word
3. WakeWordHandler identifies system command
4. Service sends broadcast to MainActivity
5. MainActivity receives broadcast
6. VoiceClientManager toggles microphone (pause session)
7. Service plays activation sound


## Components and Interfaces

### 1. PorcupineService (Foreground Service)

**Purpose**: Continuously listen for wake words in background using Picovoice Porcupine SDK.

**Key Responsibilities**:
- Initialize and manage PorcupineManager lifecycle
- Load system and custom .ppn files
- Handle wake word detection callbacks
- Manage Foreground Service notification
- Send Intents/Broadcasts on wake word detection
- Play activation sounds

**Interface**:
```kotlin
class PorcupineService : Service() {
    private var porcupineManager: PorcupineManager? = null
    private val wakeWordHandler = WakeWordHandler()
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
    override fun onDestroy()
    
    private fun initializePorcupine()
    private fun loadWakeWords(): List<WakeWordConfig>
    private fun onWakeWordDetected(keywordIndex: Int)
    private fun playActivationSound(isSystemCommand: Boolean)
    private fun createNotification(): Notification
}

data class WakeWordConfig(
    val id: String,
    val name: String,
    val ppnPath: String,
    val type: WakeWordType, // SYSTEM or CUSTOM
    val threadId: String? = null, // For custom wake words
    val sensitivity: Float = 0.5f
)

enum class WakeWordType {
    SYSTEM,  // start/stop/koniec
    CUSTOM   // User-created for threads
}
```

**Lifecycle**:
- Started when user enables Picovoice in settings
- Runs as Foreground Service with persistent notification
- Stopped when user disables Picovoice or app is uninstalled
- Auto-restarts after device boot (via BootReceiver)


### 2. WakeWordHandler

**Purpose**: Process wake word detections and execute appropriate actions.

**Key Responsibilities**:
- Identify wake word type (system vs custom)
- Execute system commands (start/stop/koniec)
- Launch MainActivity for custom wake words
- Send broadcasts for in-app commands

**Interface**:
```kotlin
class WakeWordHandler(private val context: Context) {
    
    fun handleWakeWord(wakeWord: WakeWordConfig) {
        when (wakeWord.type) {
            WakeWordType.SYSTEM -> handleSystemCommand(wakeWord.name)
            WakeWordType.CUSTOM -> handleCustomCommand(wakeWord)
        }
    }
    
    private fun handleSystemCommand(command: String) {
        when (command) {
            "start", "stop" -> sendToggleMicrophoneBroadcast()
            "koniec" -> terminateApplication()
        }
    }
    
    private fun handleCustomCommand(wakeWord: WakeWordConfig) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_THREAD_ID, wakeWord.threadId)
            putExtra(EXTRA_WAKE_WORD_TRIGGER, true)
        }
        context.startActivity(intent)
    }
    
    private fun sendToggleMicrophoneBroadcast() {
        val intent = Intent(ACTION_TOGGLE_MICROPHONE)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
    
    private fun terminateApplication() {
        // Send broadcast to MainActivity to gracefully shutdown
        val intent = Intent(ACTION_TERMINATE_APP)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
    
    companion object {
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_WAKE_WORD_TRIGGER = "wake_word_trigger"
        const val ACTION_TOGGLE_MICROPHONE = "ai.pipecat.TOGGLE_MICROPHONE"
        const val ACTION_TERMINATE_APP = "ai.pipecat.TERMINATE_APP"
    }
}
```


### 3. PicovoiceManager

**Purpose**: Centralized manager for Picovoice configuration and service control.

**Key Responsibilities**:
- Start/stop PorcupineService
- Manage custom wake words (add, delete, import .ppn)
- Manage thread-wake word associations
- Provide UI state for settings screen

**Interface**:
```kotlin
object PicovoiceManager {
    
    // Service control
    fun enablePicovoice(context: Context)
    fun disablePicovoice(context: Context)
    fun isEnabled(): Boolean
    
    // Custom wake words
    fun addCustomWakeWord(name: String): CustomWakeWord
    fun deleteCustomWakeWord(id: String)
    fun importPpnFile(wakeWordId: String, uri: Uri): Result<Unit>
    fun getCustomWakeWords(): List<CustomWakeWord>
    
    // Thread associations
    fun assignWakeWordToThread(wakeWordId: String, threadId: String)
    fun unassignWakeWordFromThread(threadId: String)
    fun getWakeWordForThread(threadId: String): CustomWakeWord?
    fun getAvailableWakeWords(): List<CustomWakeWord> // Not assigned to any thread
    
    // Settings
    fun setCustomAccessKey(key: String) // Set user's custom key
    fun clearCustomAccessKey() // Revert to default key
    fun getAccessKey(): String? // Returns custom key if set, otherwise null (use default)
    fun isUsingDefaultKey(): Boolean // Check if using default or custom key
    fun setSensitivity(sensitivity: Float)
    fun getSensitivity(): Float
    fun setActivationSoundEnabled(enabled: Boolean)
    fun isActivationSoundEnabled(): Boolean
}

data class CustomWakeWord(
    val id: String,
    val name: String,
    val ppnFilePath: String?,
    val isReady: Boolean, // true if .ppn file is imported
    val assignedThreadId: String?,
    val createdAt: Long
)
```


### 4. AccessKeyField (Composable)

**Purpose**: Allow users to optionally set custom Picovoice API key.

**Interface**:
```kotlin
@Composable
fun AccessKeyField(
    isUsingDefault: Boolean,
    onSetCustomKey: (String) -> Unit,
    onClearCustomKey: () -> Unit
) {
    var customKey by remember { mutableStateOf("") }
    var showKeyInput by remember { mutableStateOf(!isUsingDefault) }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Picovoice API Key", style = TextStyles.label)
            
            if (isUsingDefault) {
                Chip(
                    text = "Domyślny klucz",
                    backgroundColor = Color.Green.copy(alpha = 0.2f),
                    textColor = Color.Green
                )
            } else {
                Chip(
                    text = "Własny klucz",
                    backgroundColor = Color.Blue.copy(alpha = 0.2f),
                    textColor = Color.Blue
                )
            }
        }
        
        if (showKeyInput) {
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = customKey,
                onValueChange = { customKey = it },
                label = { Text("Własny API Key (opcjonalnie)") },
                placeholder = { Text("Wklej klucz z console.picovoice.ai") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { 
                        if (customKey.isNotBlank()) {
                            onSetCustomKey(customKey)
                            showKeyInput = false
                        }
                    },
                    enabled = customKey.isNotBlank()
                ) {
                    Text("Zapisz")
                }
                
                if (!isUsingDefault) {
                    OutlinedButton(onClick = { 
                        onClearCustomKey()
                        customKey = ""
                        showKeyInput = false
                    }) {
                        Text("Przywróć domyślny")
                    }
                }
                
                TextButton(onClick = { 
                    showKeyInput = false
                    customKey = ""
                }) {
                    Text("Anuluj")
                }
            }
        } else {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = { showKeyInput = true }) {
                Text(if (isUsingDefault) "Ustaw własny klucz" else "Zmień klucz")
            }
        }
        
        Text(
            text = "Aplikacja używa domyślnego klucza API. Możesz użyć własnego klucza z console.picovoice.ai dla większej kontroli.",
            style = TextStyles.caption,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
```

### 5. PicovoiceSettingsPanel (Composable)

**Purpose**: UI component in SettingsScreen for Picovoice configuration.

**Key Responsibilities**:
- Display enable/disable toggle
- Show list of custom wake words with status
- Provide UI for adding/deleting wake words
- Show instructions dialog
- Handle .ppn file import
- Display sensitivity slider
- Show access key input field

**Interface**:
```kotlin
@Composable
fun PicovoiceSettingsPanel(
    modifier: Modifier = Modifier
) {
    var isEnabled by remember { mutableStateOf(PicovoiceManager.isEnabled()) }
    var customWakeWords by remember { mutableStateOf(PicovoiceManager.getCustomWakeWords()) }
    var showInstructionsDialog by remember { mutableStateOf(false) }
    var selectedWakeWord by remember { mutableStateOf<CustomWakeWord?>(null) }
    
    Column(modifier = modifier) {
        // Enable/Disable toggle
        PicovoiceToggle(
            enabled = isEnabled,
            onToggle = { enabled ->
                if (enabled) PicovoiceManager.enablePicovoice(context)
                else PicovoiceManager.disablePicovoice(context)
                isEnabled = enabled
            }
        )
        
        // Access key input (optional - can use default)
        AccessKeyField(
            isUsingDefault = PicovoiceManager.isUsingDefaultKey(),
            onSetCustomKey = { key -> PicovoiceManager.setCustomAccessKey(key) },
            onClearCustomKey = { PicovoiceManager.clearCustomAccessKey() }
        )
        
        // Sensitivity slider
        SensitivitySlider()
        
        // System wake words (read-only)
        SystemWakeWordsSection()
        
        // Custom wake words list
        CustomWakeWordsSection(
            wakeWords = customWakeWords,
            onAddClick = { showInstructionsDialog = true },
            onDeleteClick = { wakeWord ->
                PicovoiceManager.deleteCustomWakeWord(wakeWord.id)
                customWakeWords = PicovoiceManager.getCustomWakeWords()
            },
            onImportClick = { wakeWord ->
                selectedWakeWord = wakeWord
                // Launch file picker
            }
        )
    }
    
    if (showInstructionsDialog) {
        WakeWordInstructionsDialog(
            onDismiss = { showInstructionsDialog = false },
            onImportClick = { /* Launch file picker */ }
        )
    }
}
```


### 5. WakeWordInstructionsDialog (Composable)

**Purpose**: Display step-by-step instructions for creating wake words in Picovoice Console.

**Interface**:
```kotlin
@Composable
fun WakeWordInstructionsDialog(
    onDismiss: () -> Unit,
    onImportClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jak utworzyć wake word") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                InstructionStep(
                    number = 1,
                    text = "Przejdź do Picovoice Console",
                    action = {
                        LinkButton(
                            text = "Otwórz Console",
                            url = "https://console.picovoice.ai"
                        )
                    }
                )
                InstructionStep(
                    number = 2,
                    text = "Utwórz konto lub zaloguj się (darmowe)"
                )
                InstructionStep(
                    number = 3,
                    text = "Przejdź do sekcji 'Porcupine' → 'Wake Words'"
                )
                InstructionStep(
                    number = 4,
                    text = "Kliknij 'Create Wake Word'"
                )
                InstructionStep(
                    number = 5,
                    text = "Wpisz nazwę wake word (np. 'asystent')"
                )
                InstructionStep(
                    number = 6,
                    text = "Wybierz język: Polski (pl)"
                )
                InstructionStep(
                    number = 7,
                    text = "Kliknij 'Train' - proces trwa ~10 sekund"
                )
                InstructionStep(
                    number = 8,
                    text = "Pobierz plik .ppn (Android)"
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TipCard(
                    title = "Wskazówki",
                    tips = listOf(
                        "Wybieraj wake words z wieloma sylabami (np. 'asystent' lepsze niż 'ok')",
                        "Unikaj popularnych słów używanych w codziennych rozmowach",
                        "Testuj wake word w Console przed importem"
                    )
                )
            }
        },
        confirmButton = {
            Button(onClick = onImportClick) {
                Text("Importuj plik .ppn")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Zamknij")
            }
        }
    )
}
```


### 6. ThreadConfigDialog Enhancement

**Purpose**: Add wake word assignment to existing thread configuration dialog.

**Interface**:
```kotlin
@Composable
fun ThreadConfigDialog(
    threadId: String,
    onDismiss: () -> Unit,
    onSave: (ThreadSettings) -> Unit
) {
    // ... existing thread settings UI ...
    
    // New: Wake word assignment section
    WakeWordAssignmentSection(
        threadId = threadId,
        currentWakeWord = PicovoiceManager.getWakeWordForThread(threadId),
        availableWakeWords = PicovoiceManager.getAvailableWakeWords(),
        onAssign = { wakeWord ->
            PicovoiceManager.assignWakeWordToThread(wakeWord.id, threadId)
        },
        onUnassign = {
            PicovoiceManager.unassignWakeWordFromThread(threadId)
        }
    )
}

@Composable
fun WakeWordAssignmentSection(
    threadId: String,
    currentWakeWord: CustomWakeWord?,
    availableWakeWords: List<CustomWakeWord>,
    onAssign: (CustomWakeWord) -> Unit,
    onUnassign: () -> Unit
) {
    Column {
        Text("Wake Word", style = TextStyles.label)
        
        if (currentWakeWord != null) {
            // Show assigned wake word
            AssignedWakeWordCard(
                wakeWord = currentWakeWord,
                onUnassign = onUnassign
            )
        } else {
            // Show dropdown to select wake word
            WakeWordDropdown(
                availableWakeWords = availableWakeWords.filter { it.isReady },
                onSelect = onAssign
            )
        }
        
        if (availableWakeWords.isEmpty()) {
            Text(
                text = "Brak dostępnych wake words. Dodaj nowy w ustawieniach.",
                style = TextStyles.caption,
                color = Color.Gray
            )
        }
    }
}
```


### 7. BootReceiver

**Purpose**: Auto-start PorcupineService after device boot.

**Interface**:
```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (PicovoiceManager.isEnabled()) {
                PicovoiceManager.enablePicovoice(context)
            }
        }
    }
}
```

**AndroidManifest.xml**:
```xml
<receiver 
    android:name=".BootReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED"/>
    </intent-filter>
</receiver>

<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```


## Data Models

### CustomWakeWord
```kotlin
@Serializable
data class CustomWakeWord(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ppnFilePath: String? = null,
    val assignedThreadId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val sensitivity: Float = 0.5f
) {
    val isReady: Boolean
        get() = ppnFilePath != null && File(ppnFilePath).exists()
}
```

### WakeWordThreadAssociation
```kotlin
@Serializable
data class WakeWordThreadAssociation(
    val threadId: String,
    val wakeWordId: String,
    val assignedAt: Long = System.currentTimeMillis()
)
```

### PicovoiceConfig
```kotlin
@Serializable
data class PicovoiceConfig(
    val accessKey: String,
    val isEnabled: Boolean = false,
    val sensitivity: Float = 0.5f,
    val activationSoundEnabled: Boolean = true,
    val customWakeWords: List<CustomWakeWord> = emptyList(),
    val threadAssociations: List<WakeWordThreadAssociation> = emptyList()
)
```


## Error Handling

### Picovoice Initialization Errors

**Scenario**: PorcupineManager fails to initialize
- **Causes**: Invalid access key, missing permissions, unsupported device
- **Handling**:
  1. Catch exception during PorcupineManager.Builder().build()
  2. Log error with details
  3. Show notification: "Picovoice initialization failed"
  4. Disable Picovoice in settings
  5. Display error message in settings UI with troubleshooting steps

### Wake Word File Errors

**Scenario**: .ppn file is invalid or corrupted
- **Causes**: Wrong file format, corrupted download, incompatible version
- **Handling**:
  1. Validate file extension (.ppn) before import
  2. Attempt to load file with PorcupineManager
  3. If load fails, show error dialog: "Invalid .ppn file"
  4. Keep wake word in "not ready" state
  5. Allow user to re-import

### Permission Errors

**Scenario**: Microphone permission denied
- **Causes**: User denied permission, permission revoked
- **Handling**:
  1. Check permission before starting PorcupineService
  2. If denied, show permission request dialog
  3. If permanently denied, show settings intent
  4. Disable Picovoice until permission granted
  5. Display clear message in settings UI

### Service Lifecycle Errors

**Scenario**: PorcupineService crashes or is killed by system
- **Causes**: Low memory, battery optimization, system constraints
- **Handling**:
  1. Return START_STICKY from onStartCommand
  2. Service will auto-restart
  3. Re-initialize PorcupineManager on restart
  4. Log restart event for debugging
  5. Show notification: "Wake word detection restarted"

### Thread Launch Errors

**Scenario**: Cannot launch MainActivity from background
- **Causes**: Background restrictions, battery optimization
- **Handling**:
  1. Check if app is in background restrictions
  2. Use FLAG_ACTIVITY_NEW_TASK
  3. If launch fails, show notification instead
  4. Notification action opens app to thread
  5. Log failure for debugging


## Testing Strategy

### Unit Tests

**PicovoiceManager Tests**:
- Test custom wake word CRUD operations
- Test thread-wake word association logic
- Test available wake words filtering
- Test configuration persistence

**WakeWordHandler Tests**:
- Test system command routing
- Test custom command Intent creation
- Test broadcast sending
- Test application termination logic

### Integration Tests

**PorcupineService Tests**:
- Test service lifecycle (start/stop)
- Test wake word loading from storage
- Test notification creation
- Test foreground service behavior

**UI Tests**:
- Test PicovoiceSettingsPanel interactions
- Test wake word addition flow
- Test .ppn file import flow
- Test thread assignment UI

### Manual Testing Scenarios

1. **Basic Wake Word Detection**:
   - Enable Picovoice
   - Speak system wake word "stop"
   - Verify microphone toggles

2. **Custom Wake Word Flow**:
   - Add custom wake word
   - Follow instructions to create in Console
   - Import .ppn file
   - Verify green status indicator

3. **Thread Launch**:
   - Assign wake word to thread
   - Close app
   - Speak wake word
   - Verify app launches with correct thread

4. **Background Behavior**:
   - Enable Picovoice
   - Lock screen
   - Speak wake word
   - Verify app launches

5. **Error Scenarios**:
   - Import invalid file
   - Deny microphone permission
   - Use invalid access key
   - Verify error messages

6. **Battery Optimization**:
   - Enable battery optimization
   - Test wake word detection
   - Verify service persistence


## Implementation Details

### Picovoice SDK Integration

**Gradle Dependencies**:
```kotlin
dependencies {
    implementation("ai.picovoice:porcupine-android:3.0.0")
}
```

**BuildConfig Setup** (build.gradle.kts):
```kotlin
android {
    defaultConfig {
        // Add default Picovoice API key
        buildConfigField("String", "DEFAULT_PICOVOICE_KEY", "\"YOUR_API_KEY_HERE\"")
    }
    
    buildFeatures {
        buildConfig = true
    }
}
```

**Note**: Replace `YOUR_API_KEY_HERE` with your actual Picovoice API key. This key will be used as default for all users, but they can override it in settings.

**Access Key Management**:
- Default API key hardcoded in BuildConfig (provided by developer)
- User can override with custom key in settings
- Store custom key in SharedPreferences (encrypted)
- Priority: Custom key > Default key
- Provide UI for user to enter custom key
- Show "Using default key" or "Using custom key" indicator
- Validate key format before use

**PorcupineManager Initialization**:
```kotlin
private fun initializePorcupine() {
    try {
        // Get access key (custom or default)
        val accessKey = PicovoiceManager.getAccessKey() ?: BuildConfig.DEFAULT_PICOVOICE_KEY
        if (accessKey.isBlank()) {
            throw IllegalStateException("Access key not configured")
        }
        
        val wakeWords = loadWakeWords()
        val keywordPaths = wakeWords.map { it.ppnPath }.toTypedArray()
        val sensitivities = wakeWords.map { it.sensitivity }.toFloatArray()
        
        porcupineManager = PorcupineManager.Builder()
            .setAccessKey(accessKey)
            .setKeywordPaths(*keywordPaths)
            .setSensitivities(*sensitivities)
            .build(this) { keywordIndex ->
                onWakeWordDetected(keywordIndex)
            }
        
        porcupineManager?.start()
        Log.d(TAG, "Porcupine initialized with ${wakeWords.size} wake words")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Porcupine", e)
        handleInitializationError(e)
    }
}
```

### System Wake Words

**Built-in .ppn Files**:
- Create "start", "stop", "koniec" wake words in Picovoice Console
- Download .ppn files for Polish language
- Include in app assets: `assets/picovoice/system/`
- Copy to internal storage on first launch

**File Structure**:
```
assets/
  picovoice/
    system/
      start_pl.ppn
      stop_pl.ppn
      koniec_pl.ppn
```

### Custom Wake Words Storage

**File Storage**:
- Location: `context.filesDir/picovoice/custom/`
- Naming: `{wakeWordId}.ppn`
- Metadata: Stored in SharedPreferences as JSON

**Import Process**:
```kotlin
fun importPpnFile(wakeWordId: String, uri: Uri): Result<Unit> {
    return try {
        val customDir = File(context.filesDir, "picovoice/custom")
        customDir.mkdirs()
        
        val destFile = File(customDir, "$wakeWordId.ppn")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        // Validate by attempting to load
        validatePpnFile(destFile.absolutePath)
        
        // Update metadata
        updateWakeWordPath(wakeWordId, destFile.absolutePath)
        
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to import .ppn file", e)
        Result.failure(e)
    }
}
```


### Activation Sounds

**Sound Files**:
- System command sound: `res/raw/system_activation.mp3` (short beep)
- Custom command sound: `res/raw/custom_activation.mp3` (longer tone)

**Playback**:
```kotlin
private fun playActivationSound(isSystemCommand: Boolean) {
    if (!PicovoiceManager.isActivationSoundEnabled()) return
    
    try {
        val soundRes = if (isSystemCommand) {
            R.raw.system_activation
        } else {
            R.raw.custom_activation
        }
        
        val mediaPlayer = MediaPlayer.create(this, soundRes)
        mediaPlayer.setOnCompletionListener { it.release() }
        mediaPlayer.start()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to play activation sound", e)
    }
}
```

### Foreground Service Notification

**Notification Channel**:
```kotlin
private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Picovoice Wake Word Detection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Continuous wake word detection service"
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}
```

**Notification**:
```kotlin
private fun createNotification(): Notification {
    val pendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )
    
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Nasłuchiwanie komend głosowych")
        .setContentText("Picovoice aktywny - ${activeWakeWords.size} wake words")
        .setSmallIcon(R.drawable.ic_mic)
        .setContentIntent(pendingIntent)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()
}
```

### MainActivity Intent Handling

**Handle Wake Word Launch**:
```kotlin
override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    intent?.let { handleIntent(it) }
}

private fun handleIntent(intent: Intent) {
    if (intent.getBooleanExtra(WakeWordHandler.EXTRA_WAKE_WORD_TRIGGER, false)) {
        val threadId = intent.getStringExtra(WakeWordHandler.EXTRA_THREAD_ID)
        if (threadId != null) {
            // Auto-launch thread
            lifecycleScope.launch {
                launchThread(threadId)
            }
        }
    }
}
```

### Broadcast Receivers in MainActivity

**Register Receivers**:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Register broadcast receivers
    LocalBroadcastManager.getInstance(this).apply {
        registerReceiver(
            toggleMicrophoneReceiver,
            IntentFilter(WakeWordHandler.ACTION_TOGGLE_MICROPHONE)
        )
        registerReceiver(
            terminateAppReceiver,
            IntentFilter(WakeWordHandler.ACTION_TERMINATE_APP)
        )
    }
}

private val toggleMicrophoneReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        voiceClientManager.toggleMicrophone()
    }
}

private val terminateAppReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        finishAffinity() // Close all activities
        exitProcess(0)
    }
}
```


## Security Considerations

### Access Key Protection
- Store access key in encrypted SharedPreferences
- Never log access key
- Clear access key on app uninstall
- Validate key format before use

### File Security
- Store .ppn files in internal storage (not accessible to other apps)
- Validate file extensions and content before import
- Clean up orphaned files on wake word deletion

### Permission Management
- Request RECORD_AUDIO permission explicitly
- Check permission before starting service
- Handle permission revocation gracefully
- Explain permission usage to user

### Background Restrictions
- Respect battery optimization settings
- Handle Doze mode appropriately
- Use Foreground Service to maintain reliability
- Provide clear notification about background activity

## Performance Considerations

### Battery Usage
- Porcupine is optimized for low power consumption
- Typical usage: ~1-2% battery per hour
- Use appropriate sensitivity to reduce false positives
- Consider disabling when not needed

### Memory Usage
- Each .ppn file: ~50-100 KB
- PorcupineManager: ~10-20 MB RAM
- Limit to reasonable number of wake words (10-15 max)
- Clean up resources on service stop

### CPU Usage
- Porcupine uses minimal CPU (~1-2%)
- Runs on separate thread
- No impact on UI performance
- Efficient wake word detection algorithm

### Storage
- System wake words: ~300 KB
- Custom wake words: ~100 KB each
- Metadata: ~10 KB
- Total: < 2 MB for typical usage


## Dependencies

### Required Libraries
```kotlin
// Picovoice Porcupine SDK
implementation("ai.picovoice:porcupine-android:3.0.0")

// Existing dependencies (already in project)
implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
```

### Permissions (AndroidManifest.xml)
```xml
<!-- Already exists -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />

<!-- New -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

### Service Declaration (AndroidManifest.xml)
```xml
<service
    android:name=".PorcupineService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="microphone" />

<receiver 
    android:name=".BootReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED"/>
    </intent-filter>
</receiver>
```

## Migration Strategy

### Phase 1: Core Infrastructure
1. Add Picovoice dependency
2. Create PorcupineService skeleton
3. Implement PicovoiceManager
4. Add data models and storage

### Phase 2: System Wake Words
1. Create system wake words in Picovoice Console
2. Add .ppn files to assets
3. Implement system command handling
4. Test basic wake word detection

### Phase 3: Custom Wake Words
1. Implement custom wake word CRUD
2. Add .ppn file import functionality
3. Create instructions dialog
4. Test custom wake word flow

### Phase 4: Thread Integration
1. Add wake word assignment to ThreadConfigDialog
2. Implement thread launch from background
3. Test end-to-end flow
4. Add activation sounds

### Phase 5: UI Polish
1. Create PicovoiceSettingsPanel
2. Add status indicators
3. Improve error messages
4. Add sensitivity controls

### Phase 6: Testing & Optimization
1. Comprehensive testing
2. Performance optimization
3. Battery usage testing
4. Documentation

