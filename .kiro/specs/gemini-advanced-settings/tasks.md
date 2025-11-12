# Implementation Plan

- [x] 1. Extend Preferences system and update model configuration
  - Add new StringPref fields to Preferences.kt: systemPrompt, selectedVoice, modelName
  - Set default values: systemPrompt="You are a helpful assistant", selectedVoice="Puck", modelName="gemini-2.5-flash-native-audio-preview-09-2025"
  - Initialize new preferences in initAppStart() method
  - Update VoiceClientManager.start() to read all preferences and configure Gemini client with model, systemPrompt, and voice settings
  - Add speech_config with voice_config and prebuilt_voice_config to buildConfig call
  - _Requirements: 1.1, 1.2, 3.1, 3.5, 3.6_

- [x] 2. Create Settings screen UI with navigation
  - Create new SettingsScreen.kt composable with scrollable layout
  - Add TextField for model name (read-only or editable)
  - Add TextField for API key (password type, pre-populated from Preferences)
  - Add multi-line TextField for system prompt (pre-populated from Preferences)
  - Create Voice data class and VoiceList object with all 30 Gemini voices
  - Add DropdownMenu/ExposedDropdownMenuBox for voice selection with all 30 voices
  - Add Save button that writes all values to Preferences
  - Add navigation state management (enum Screen or boolean flags) to MainActivity
  - Add Settings button to InCallHeader or ConnectSettings that navigates to SettingsScreen
  - Add back button in SettingsScreen that returns to previous screen
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 3.2, 3.3, 3.4_

- [x] 3. Implement image capture and sending functionality
- [x] 3.1 Add image permissions to AndroidManifest.xml
  - Add READ_MEDIA_IMAGES permission (Android 13+)
  - Add READ_EXTERNAL_STORAGE permission (Android 12 and below)
  - Note: CAMERA permission already exists in manifest
  - _Requirements: 4.1, 4.2_

- [x] 3.2 Create image selection UI in InCallFooter
  - Add ImageButton composable with camera/image icon
  - Implement bottom sheet or dialog with "Camera" and "Gallery" options
  - Position button alongside the End button in InCallFooter
  - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [x] 3.3 Implement camera and gallery launchers in MainActivity
  - Add ActivityResultContracts.TakePicture for camera capture
  - Add ActivityResultContracts.PickVisualMedia for gallery selection
  - Create temporary file URI for camera captures
  - Pass image selection callback to InCallLayout and InCallFooter
  - _Requirements: 4.3, 4.4, 4.5_

- [x] 3.4 Implement image processing and sending in VoiceClientManager
  - Add sendImage(uri: Uri) method to VoiceClientManager
  - Read image from Uri using ContentResolver
  - Convert image to ByteArray and encode to Base64
  - Determine MIME type (PNG or JPG)
  - Send image via client.sendRealtimeInput with proper format
  - Add error handling for file read errors, unsupported formats, and network errors
  - _Requirements: 4.5, 4.6, 4.7, 4.8, 4.9_

- [x] 4. Implement wake lock to prevent screen timeout
- [x] 4.1 Add wake lock permission and field
  - Add WAKE_LOCK permission to AndroidManifest.xml
  - Add PowerManager.WakeLock field to VoiceClientManager
  - _Requirements: 5.1_

- [x] 4.2 Implement wake lock lifecycle management
  - Create acquireWakeLock() method that gets PowerManager and acquires SCREEN_BRIGHT_WAKE_LOCK
  - Call acquireWakeLock() in onConnected() callback
  - Create releaseWakeLock() method that releases and nullifies wake lock
  - Call releaseWakeLock() in onDisconnected() callback
  - Add null checks to prevent double acquisition or release
  - _Requirements: 5.2, 5.3, 5.4, 5.5_

- [x] 5. Implement audio volume enhancement
  - Add AudioManager field to VoiceClientManager
  - Create increaseAudioVolume() method that sets STREAM_VOICE_CALL or STREAM_MUSIC to 90% of max volume
  - Call increaseAudioVolume() in onConnected() callback after wake lock acquisition
  - Add error handling for AudioManager unavailable
  - Test audio output and adjust volume percentage or stream type if needed
  - _Requirements: 6.1, 6.2, 6.3, 6.4_
