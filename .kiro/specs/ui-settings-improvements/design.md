# Design Document

## Overview

This design document outlines the architecture and implementation approach for enhancing the Android application's user interface and settings functionality. The improvements focus on persistent authentication with automatic re-login, redesigned conversation list interface, PIN-protected settings screen, per-conversation voice/audio configuration, and comprehensive Gemini API settings.

## Architecture

### Component Structure

```
MainActivity
├── LoginScreen (conditional - only if no stored credentials)
├── ThreadListScreen (with theme toggle, settings icon)
│   ├── ThreadCard (with long-press for per-thread settings)
│   └── ThreadConfigDialog (voice, speed, volume, temperature)
├── SettingsScreen (PIN-protected)
│   ├── PINEntryDialog
│   └── SettingsContent
└── InCallLayout (existing, unchanged)
```

### Data Flow

1. **Authentication Flow**:
   - App launch → Check stored credentials → Auto-login or show LoginScreen
   - Token expiration → Auto re-authenticate using stored credentials
   - Explicit logout → Clear credentials → Show LoginScreen

2. **Thread Configuration Flow**:
   - Long-press thread → Show ThreadConfigDialog
   - Configure voice/speed/volume/temperature → Save to ThreadSettings
   - Start session → Load ThreadSettings → Apply to VoiceClientManager

3. **Settings Access Flow**:
   - Tap gear icon → Show PIN entry → Validate PIN → Show SettingsScreen
   - Modify settings → Save to Preferences → Apply immediately
   - Tap X or back → Save all changes → Return to ThreadListScreen


## Components and Interfaces

### 1. Enhanced AuthManager

**Purpose**: Extend existing AuthManager to support credential persistence and automatic re-authentication

**New Methods**:
```kotlin
// Store login credentials securely for auto-login
fun storeCredentials(email: String, password: String)

// Retrieve stored credentials
fun getStoredCredentials(): AuthCredentials?

// Check if credentials are stored
fun hasStoredCredentials(): Boolean

// Auto-login using stored credentials
suspend fun autoLogin(): Result<AuthToken>

// Clear stored credentials (on explicit logout)
fun clearStoredCredentials()
```

**Storage**: Use EncryptedSharedPreferences (already in use) to store email and password securely

**Integration Points**:
- MainActivity: Check `hasStoredCredentials()` on launch
- LoginScreen: Call `storeCredentials()` after successful login
- Token refresh: Call `autoLogin()` when token expires

### 2. ThreadSettings Data Model

**Purpose**: Store per-conversation voice and audio configuration

**Data Structure**:
```kotlin
@Serializable
data class ThreadSettings(
    val conversationId: String,
    val voiceName: String = "Puck",
    val speechSpeed: Float = 1.0f,
    val volumeBoost: Float = 1.0f,
    val temperature: Float = 1.0f
)
```

**Storage**: SharedPreferences with JSON serialization (similar to existing Preferences pattern)

**Manager Class**:
```kotlin
object ThreadSettingsManager {
    fun getSettings(conversationId: String): ThreadSettings
    fun saveSettings(settings: ThreadSettings)
    fun getDefaultSettings(): ThreadSettings
}
```


### 3. Enhanced Preferences

**Purpose**: Add new preference fields for Gemini API key, summary prompt, session timeout, wake lock, and skin selection

**New Preferences**:
```kotlin
object Preferences {
    // Existing
    val apiKey: StringPref
    val systemPrompt: StringPref
    val selectedVoice: StringPref
    val modelName: StringPref
    
    // New additions
    val geminiApiKey: StringPref  // Separate from LibreChat auth
    val summaryPrompt: StringPref
    val sessionTimeoutMinutes: IntPref
    val keepScreenAwake: BooleanPref
    val selectedSkin: StringPref
    val userPin: StringPref  // Encrypted PIN storage
    val defaultServerUrl: StringPref  // Default: "www.kumpel-chat.fun"
    val isDarkTheme: BooleanPref
}
```

**New Preference Types**:
```kotlin
class IntPref(key: String, defaultValue: Int)
class BooleanPref(key: String, defaultValue: Boolean)
```

### 4. PINManager

**Purpose**: Handle PIN validation and management

**Implementation**:
```kotlin
object PINManager {
    private const val DEFAULT_PIN = "2222"
    
    fun validatePIN(pin: String): Boolean
    fun changePIN(currentPin: String, newPin: String): Result<Unit>
    fun resetToDefault()
}
```

**Security**: Store PIN hash (not plaintext) in EncryptedSharedPreferences


### 5. Enhanced LoginScreen

**Changes**:
- Pre-fill server URL with "www.kumpel-chat.fun" (editable)
- Store credentials on successful login
- Load stored server URL if available

**UI Updates**:
- Default server URL field value
- Maintain existing validation and error handling

### 6. Redesigned ThreadListScreen

**UI Changes**:
1. Replace "Wybierz temat nauki" with "Co dzis robimy?" in styled frame
2. Remove "agents" header from thread cards
3. Convert thread cards to elongated buttons (full-width, scrollable list)
4. Replace red "Wyloguj" button with small gear icon (top-right)
5. Add theme toggle (light/dark) on left side

**New Components**:
```kotlin
@Composable
fun ThreadListHeader(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onSettingsClick: () -> Unit
)

@Composable
fun ThreadButton(
    thread: ConversationThread,
    onClick: () -> Unit,
    onLongPress: () -> Unit
)

@Composable
fun ThreadConfigDialog(
    thread: ConversationThread,
    currentSettings: ThreadSettings,
    onSave: (ThreadSettings) -> Unit,
    onDismiss: () -> Unit
)
```

**Long-Press Handling**:
- Use `Modifier.pointerInput` with `detectTapGestures` for long-press detection
- Show ThreadConfigDialog with voice dropdown, speed/volume/temperature sliders


### 7. New SettingsScreen

**Access Control**:
- Triggered by gear icon in ThreadListScreen
- Show PIN entry dialog first
- Only show settings content after PIN validation

**Settings Sections**:

1. **Gemini API Configuration**:
   - API Key (masked text field)
   - Model Name (text field with default)
   
2. **Session Management**:
   - Keep Screen Awake (toggle)
   - Session Timeout (number input in minutes)
   
3. **Visual Preferences**:
   - Skin Selection (dropdown with 3 options - placeholder feature)
   
4. **Summary Configuration**:
   - Summary Prompt (multi-line text field with default)
   
5. **Security**:
   - Change PIN button
   - Logout button

**UI Components**:
```kotlin
@Composable
fun PINEntryDialog(
    onPINValidated: () -> Unit,
    onDismiss: () -> Unit
)

@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onLogout: () -> Unit
)

@Composable
fun ChangePINDialog(
    onPINChanged: () -> Unit,
    onDismiss: () -> Unit
)
```

**Layout**:
- X button in top-right corner
- Scrollable content area
- Save changes automatically on close


### 8. Enhanced VoiceClientManager

**Purpose**: Apply per-thread settings when starting voice session

**New Method**:
```kotlin
fun start(threadSettings: ThreadSettings? = null)
```

**Changes**:
- Accept optional ThreadSettings parameter
- Apply voice, speed, volume, temperature from ThreadSettings
- Fall back to default Preferences if no ThreadSettings provided
- Pass voiceName to Gemini API setup
- Apply speed/volume/temperature to audio configuration

**Integration**:
- MainActivity calls `voiceClientManager.start(threadSettings)` when starting session
- ThreadSettings loaded from ThreadSettingsManager based on conversationId

### 9. Theme Management

**Implementation**:
```kotlin
object ThemeManager {
    val isDarkTheme = mutableStateOf(false)
    
    fun toggleTheme() {
        isDarkTheme.value = !isDarkTheme.value
        Preferences.isDarkTheme.value = isDarkTheme.value
    }
    
    fun loadTheme() {
        isDarkTheme.value = Preferences.isDarkTheme.value ?: false
    }
}
```

**Application**:
- RTVIClientTheme composable checks ThemeManager.isDarkTheme
- Apply dark/light color scheme accordingly
- Persist theme preference across app restarts


## Data Models

### Voice Options

Complete list of available Gemini voices with descriptions:

```kotlin
data class VoiceOption(
    val name: String,
    val description: String
)

val AVAILABLE_VOICES = listOf(
    VoiceOption("Zephyr", "jasny, żywy"),
    VoiceOption("Puck", "pozytywny, przyjazny, pewny siebie"),
    VoiceOption("Charon", "głęboki, autorytatywny, informacyjny"),
    VoiceOption("Kore", "neutralny, profesjonalny, stanowczy"),
    VoiceOption("Fenrir", "ciepły, przyjazny, podekscytowany"),
    VoiceOption("Leda", "młodzieńczy"),
    VoiceOption("Orus", "stanowczy, dojrzały"),
    VoiceOption("Aoede", "swobodny, zwiewny"),
    VoiceOption("Callirrhoe", "kobiecy"),
    VoiceOption("Autonoe", ""),
    VoiceOption("Enceladus", "męski"),
    VoiceOption("Iapetus", "casualowy, przystępny"),
    VoiceOption("Umbriel", ""),
    VoiceOption("Algieba", ""),
    VoiceOption("Despina", "kobiecy"),
    VoiceOption("Erinome", "kobiecy"),
    VoiceOption("Algenib", "ciepły, pewny, kobiecy"),
    VoiceOption("Rasalgethi", "konwersacyjny, męski"),
    VoiceOption("Laomedeia", ""),
    VoiceOption("Achernar", ""),
    VoiceOption("Alnilam", ""),
    VoiceOption("Schedar", ""),
    VoiceOption("Gacrux", "gładki, autorytatywny, kobiecy"),
    VoiceOption("Pulcherrima", "entuzjastyczny, młodzieńczy, kobiecy"),
    VoiceOption("Achird", "młodzieńczy, przyjazny, kobiecy"),
    VoiceOption("Zubenelgenubi", ""),
    VoiceOption("Vindemiatrix", ""),
    VoiceOption("Sadachbia", ""),
    VoiceOption("Sadaltager", ""),
    VoiceOption("Sulafat", "")
)
```

### Skin System Framework

```kotlin
data class SkinTheme(
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val onPrimary: Color,
    val onSecondary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val buttonNormal: Color,
    val buttonWarning: Color,
    val textFieldBorder: Color,
    // ... other color properties
)

enum class AppSkin(val displayName: String) {
    DEFAULT("Domyślny"),
    DARK_BLUE("Ciemny Niebieski"),
    WARM_ORANGE("Ciepły Pomarańczowy")
}

object SkinManager {
    private val skins = mapOf(
        AppSkin.DEFAULT to SkinTheme(/* current app colors */),
        AppSkin.DARK_BLUE to SkinTheme(/* placeholder - to be designed */),
        AppSkin.WARM_ORANGE to SkinTheme(/* placeholder - to be designed */)
    )
    
    var currentSkin: AppSkin = AppSkin.DEFAULT
        set(value) {
            field = value
            Preferences.selectedSkin.value = value.name
        }
    
    val currentTheme: SkinTheme
        get() = skins[currentSkin] ?: skins[AppSkin.DEFAULT]!!
    
    fun loadSkin() {
        val savedSkin = Preferences.selectedSkin.value
        currentSkin = AppSkin.values().find { it.name == savedSkin } ?: AppSkin.DEFAULT
    }
}
```

**Implementation Strategy**:
- Create framework with proper structure for multiple skins
- Implement DEFAULT skin with current app colors
- Add placeholder definitions for DARK_BLUE and WARM_ORANGE
- Update Colors object to delegate to SkinManager.currentTheme
- Show "Coming soon" badge for non-DEFAULT skins in UI
- Future work: Design and implement DARK_BLUE and WARM_ORANGE color schemes


## Error Handling

### Authentication Errors

1. **Token Expiration**:
   - Detect expired token in AuthManager
   - Attempt auto-login with stored credentials
   - If auto-login fails, clear credentials and show LoginScreen
   - Display error message explaining re-authentication failure

2. **Network Errors During Auto-Login**:
   - Retry auto-login with exponential backoff
   - After 3 failed attempts, show LoginScreen with error
   - Preserve stored credentials for manual retry

3. **Invalid Stored Credentials**:
   - Clear stored credentials
   - Show LoginScreen with "Session expired, please log in again" message

### PIN Validation Errors

1. **Incorrect PIN**:
   - Show error message "Nieprawidłowy PIN"
   - Clear PIN input field
   - Allow unlimited retry attempts
   - No lockout mechanism (simple security model)

2. **PIN Change Errors**:
   - Validate current PIN before allowing change
   - Require new PIN confirmation
   - Show error if new PIN is less than 4 digits
   - Show error if confirmation doesn't match

### Thread Settings Errors

1. **Invalid Settings Values**:
   - Validate speed range (0.5x - 2.0x)
   - Validate volume range (0.5x - 2.0x)
   - Validate temperature range (0.0 - 2.0)
   - Show error dialog if values out of range
   - Prevent saving invalid settings

2. **Settings Load Failure**:
   - Fall back to default settings
   - Log error for debugging
   - Continue with default values


## Testing Strategy

### Unit Tests

1. **AuthManager Tests**:
   - Test credential storage and retrieval
   - Test auto-login with valid credentials
   - Test auto-login with invalid credentials
   - Test credential clearing on logout
   - Test token expiration detection

2. **ThreadSettingsManager Tests**:
   - Test settings save and load
   - Test default settings retrieval
   - Test settings persistence across app restarts
   - Test multiple thread settings management

3. **PINManager Tests**:
   - Test PIN validation with correct PIN
   - Test PIN validation with incorrect PIN
   - Test PIN change with valid current PIN
   - Test PIN change with invalid current PIN
   - Test PIN hash storage (not plaintext)

4. **Preferences Tests**:
   - Test new preference types (IntPref, BooleanPref)
   - Test preference persistence
   - Test default values

### Integration Tests

1. **Authentication Flow**:
   - Test app launch with stored credentials → auto-login → ThreadListScreen
   - Test app launch without credentials → LoginScreen
   - Test successful login → credential storage → ThreadListScreen
   - Test token expiration → auto-login → continue session
   - Test logout → credential clearing → LoginScreen

2. **Thread Configuration Flow**:
   - Test long-press on thread → show config dialog
   - Test save thread settings → persist settings
   - Test start session with thread settings → apply to VoiceClientManager
   - Test start session without thread settings → use defaults

3. **Settings Access Flow**:
   - Test gear icon click → show PIN dialog
   - Test correct PIN → show SettingsScreen
   - Test incorrect PIN → show error, stay on PIN dialog
   - Test settings modification → save on close
   - Test logout from settings → clear credentials → LoginScreen

4. **Theme Toggle**:
   - Test theme toggle → apply immediately
   - Test theme persistence across app restarts


### UI Tests

1. **LoginScreen**:
   - Verify default server URL is pre-filled
   - Verify server URL is editable
   - Verify login button is disabled with empty fields
   - Verify login button is enabled with all fields filled
   - Verify error display on failed login
   - Verify navigation to ThreadListScreen on successful login

2. **ThreadListScreen**:
   - Verify "Co dzis robimy?" text is displayed in styled frame
   - Verify threads are displayed as elongated buttons
   - Verify no "agents" header is shown
   - Verify gear icon is displayed (not "Wyloguj" button)
   - Verify theme toggle is displayed on left side
   - Verify long-press on thread shows config dialog
   - Verify thread selection navigates to InCallLayout

3. **ThreadConfigDialog**:
   - Verify voice dropdown shows all available voices
   - Verify speed slider range (0.5x - 2.0x)
   - Verify volume slider range (0.5x - 2.0x)
   - Verify temperature slider range (0.0 - 2.0)
   - Verify save button persists settings
   - Verify cancel button dismisses without saving

4. **PINEntryDialog**:
   - Verify numeric keypad is displayed
   - Verify only digits 0-9 are accepted
   - Verify 4-digit PIN entry
   - Verify error message on incorrect PIN
   - Verify navigation to SettingsScreen on correct PIN

5. **SettingsScreen**:
   - Verify X button in top-right corner
   - Verify all settings sections are displayed
   - Verify API key field is masked
   - Verify model name field has default value
   - Verify summary prompt field has default value
   - Verify keep screen awake toggle
   - Verify session timeout input
   - Verify skin dropdown (placeholder)
   - Verify change PIN button
   - Verify logout button
   - Verify settings are saved on close

### Manual Testing Checklist

- [ ] App launches with stored credentials and auto-logs in
- [ ] App launches without credentials and shows LoginScreen
- [ ] Token expiration triggers auto-login
- [ ] Failed auto-login shows LoginScreen with error
- [ ] Explicit logout clears credentials
- [ ] Theme toggle works and persists
- [ ] Long-press on thread shows config dialog
- [ ] Thread settings are applied to voice session
- [ ] Gear icon shows PIN entry
- [ ] Correct PIN shows SettingsScreen
- [ ] Incorrect PIN shows error
- [ ] Settings changes are saved on close
- [ ] Logout from settings works correctly
- [ ] Screen wake lock works during session
- [ ] Session timeout ends session automatically

