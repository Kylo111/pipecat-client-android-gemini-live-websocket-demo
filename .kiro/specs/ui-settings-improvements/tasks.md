# Implementation Plan

- [x] 1. Set up core data structures and managers
  - Create ThreadSettings data model with conversationId, voiceName, speechSpeed, volumeBoost, temperature
  - Create ThreadSettingsManager for saving/loading per-conversation settings
  - Add new preference types (IntPref, BooleanPref) to Preferences.kt
  - Add new preferences: geminiApiKey, summaryPrompt, sessionTimeoutMinutes, keepScreenAwake, selectedSkin, userPin, defaultServerUrl, isDarkTheme
  - Create PINManager for PIN validation and management with encrypted storage
  - _Requirements: 7, 7.1, 8, 9, 10, 11, 14_

- [x] 2. Enhance AuthManager for credential persistence
  - Add storeCredentials() method to save email and password securely
  - Add getStoredCredentials() method to retrieve stored credentials
  - Add hasStoredCredentials() method to check if credentials exist
  - Add autoLogin() method for automatic re-authentication
  - Add clearStoredCredentials() method for explicit logout
  - Update login() to call storeCredentials() on success
  - _Requirements: 1_

- [x] 3. Update LoginScreen with default server URL
  - Set default server URL to "www.kumpel-chat.fun"
  - Make server URL field editable
  - Load stored server URL from Preferences if available
  - Store custom server URL when user modifies it
  - _Requirements: 2_

- [x] 4. Implement automatic login flow in MainActivity
  - Check hasStoredCredentials() on app launch
  - If credentials exist and token is invalid, call autoLogin()
  - Navigate to ThreadListScreen on successful auto-login
  - Navigate to LoginScreen if no credentials or auto-login fails
  - Handle token expiration with automatic re-authentication
  - _Requirements: 1_

- [x] 5. Create ThemeManager and implement theme toggle
  - Create ThemeManager with isDarkTheme state
  - Add toggleTheme() method
  - Add loadTheme() method to restore theme preference
  - Update RTVIClientTheme to use ThemeManager.isDarkTheme
  - Persist theme preference across app restarts
  - _Requirements: 6_


- [x] 6. Redesign ThreadListScreen UI
  - Replace "Wybierz temat nauki" with "Co dzis robimy?" in styled frame
  - Remove "agents" header from thread display
  - Convert thread cards to elongated full-width buttons
  - Make thread list vertically scrollable
  - Replace red "Wyloguj" button with small gear icon in top-right
  - Add theme toggle switch on left side of header
  - Wire theme toggle to ThemeManager.toggleTheme()
  - _Requirements: 3, 4, 5, 6_

- [x] 7. Implement long-press detection for thread configuration
  - Add Modifier.pointerInput with detectTapGestures to ThreadButton
  - Detect long-press gesture on thread buttons
  - Show ThreadConfigDialog on long-press
  - Pass current thread and settings to dialog
  - _Requirements: 8_

- [x] 8. Create ThreadConfigDialog component
  - Create dialog composable with voice dropdown, speed/volume/temperature sliders
  - Populate voice dropdown with all 30 available Gemini voices
  - Display voice descriptions (e.g., "Puck - pozytywny, przyjazny, pewny siebie")
  - Add speed slider (0.5x - 2.0x) with numeric display
  - Add volume slider (0.5x - 2.0x) with numeric display
  - Add temperature slider (0.0 - 2.0) with numeric display
  - Add Save and Cancel buttons
  - Validate settings ranges before saving
  - Save settings to ThreadSettingsManager on Save button click
  - _Requirements: 8_

- [x] 9. Create PINEntryDialog component
  - Create dialog with numeric keypad (0-9)
  - Accept only 4-digit PIN input
  - Validate PIN using PINManager.validatePIN()
  - Show error message on incorrect PIN
  - Clear PIN field after error
  - Call onPINValidated callback on correct PIN
  - _Requirements: 4_

- [x] 10. Create SettingsScreen component
  - Create scrollable settings screen with X button in top-right
  - Add Gemini API Key field (masked text input)
  - Add Model Name field with default value
  - Add Keep Screen Awake toggle
  - Add Session Timeout input (minutes)
  - Add Skin Selection dropdown (3 options, placeholder feature)
  - Add Summary Prompt multi-line text field with default
  - Add Change PIN button
  - Add Logout button
  - Save all settings to Preferences on close (X button or back)
  - _Requirements: 7, 7.1, 9, 10, 11, 12, 13_


- [x] 11. Create ChangePINDialog component
  - Create dialog for PIN change
  - Add current PIN input field
  - Add new PIN input field
  - Add confirm new PIN input field
  - Validate current PIN using PINManager
  - Validate new PIN is 4 digits
  - Validate new PIN confirmation matches
  - Call PINManager.changePIN() on successful validation
  - Show success message and close dialog
  - _Requirements: 14_

- [x] 12. Wire settings access flow in MainActivity
  - Add gear icon click handler in ThreadListScreen
  - Show PINEntryDialog on gear icon click
  - Navigate to SettingsScreen after PIN validation
  - Handle settings screen close (X button)
  - Return to ThreadListScreen after settings close
  - _Requirements: 4, 15_

- [x] 13. Implement logout functionality in SettingsScreen
  - Add logout button click handler
  - Call AuthManager.clearStoredCredentials()
  - Navigate to LoginScreen
  - Clear all session data
  - _Requirements: 13_

- [x] 14. Enhance VoiceClientManager to accept ThreadSettings
  - Modify start() method to accept optional ThreadSettings parameter
  - Load ThreadSettings from ThreadSettingsManager if conversationId provided
  - Apply voiceName from ThreadSettings to Gemini API setup
  - Apply speechSpeed to audio configuration
  - Apply volumeBoost to audio output
  - Apply temperature to Gemini API generation config
  - Fall back to Preferences defaults if no ThreadSettings provided
  - _Requirements: 8_

- [x] 15. Update MainActivity session start flow
  - Load ThreadSettings for selected conversationId
  - Pass ThreadSettings to voiceClientManager.start()
  - Ensure thread-specific settings are applied to voice session
  - _Requirements: 8_

- [x] 16. Implement screen wake lock functionality
  - Check Preferences.keepScreenAwake when starting voice session
  - Acquire wake lock if enabled (already implemented in VoiceClientManager)
  - Release wake lock when session ends
  - _Requirements: 9_


- [ ]* 17. Implement session timeout functionality
  - Add idle time tracking in VoiceClientManager
  - Check Preferences.sessionTimeoutMinutes
  - Monitor user activity (speaking, interactions)
  - End session when idle time exceeds timeout
  - Navigate to ThreadListScreen on timeout
  - Turn off screen on timeout (release wake lock)
  - _Requirements: 10_

- [x] 18. Update SummaryGenerator to use custom prompt
  - Load Preferences.summaryPrompt when generating summary
  - Use custom prompt if available, otherwise use default
  - Ensure summary generation works with custom prompts
  - _Requirements: 12_

- [x] 19. Add default summary prompt to Preferences
  - Set default summary prompt value (current prompt used in app)
  - Make it editable in SettingsScreen
  - Persist custom prompt across app restarts
  - _Requirements: 12_

- [x] 20. Create voice options data model
  - Define VoiceOption data class with name and description
  - Create AVAILABLE_VOICES list with all 30 Gemini voices
  - Include Polish descriptions for each voice
  - Use in ThreadConfigDialog dropdown
  - _Requirements: 8_

- [ ]* 21. Implement skin system framework
  - Create SkinTheme data class with color schemes (primary, secondary, background, surface, etc.)
  - Create AppSkin enum with 3 options (DEFAULT, DARK_BLUE, WARM_ORANGE)
  - Create SkinManager object to manage current skin and provide theme colors
  - Implement DEFAULT skin with current app colors
  - Add placeholder definitions for DARK_BLUE and WARM_ORANGE (to be designed later)
  - Add skin dropdown to SettingsScreen
  - Save selected skin to Preferences
  - Apply DEFAULT skin colors through SkinManager
  - Update Colors object to use SkinManager.currentTheme
  - Display "Coming soon" badge for non-DEFAULT skins
  - _Requirements: 11_

- [ ]* 22. Polish UI styling and animations
  - Style "Co dzis robimy?" frame with border and padding
  - Add smooth theme transition animation
  - Style gear icon with appropriate size and color
  - Add ripple effect to thread buttons
  - Style PIN entry dialog with numeric keypad layout
  - Add slider value labels and formatting
  - Ensure consistent spacing and alignment
  - _Requirements: 3, 4, 5, 6_

