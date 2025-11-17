# Implementation Plan

- [x] 1. Set up Picovoice infrastructure and dependencies
  - Add Picovoice Porcupine dependency to build.gradle.kts
  - Add required permissions to AndroidManifest.xml (RECEIVE_BOOT_COMPLETED)
  - Create data models: CustomWakeWord, WakeWordThreadAssociation, PicovoiceConfig
  - Create PicovoicePreferences for storing configuration using SharedPreferences pattern
  - _Requirements: 2.5, 2.9, 5.8_

- [x] 2. Implement core Picovoice infrastructure and system wake words
  - Create PicovoiceManager object with service control methods (enable/disable)
  - Implement custom wake word CRUD operations (add, delete, get)
  - Implement thread-wake word association methods (assign, unassign, get)
  - Implement settings methods (access key, sensitivity, activation sound)
  - Add file validation for .ppn imports
  - Create Picovoice account at console.picovoice.ai
  - Create "start", "stop", and "koniec" wake words in Polish language
  - Download .ppn files for Android and add to assets/picovoice/system/ directory
  - Create or obtain system_activation.mp3 and custom_activation.mp3 sound files
  - Add sound files to res/raw/ directory
  - _Requirements: 1.1, 1.2, 2.1, 2.5, 2.6, 2.7, 2.9, 2.10, 2.11, 3.1, 3.7, 5.7, 5.8, 5.9, 6.1, 6.2, 6.3, 6.4_

- [x] 3. Implement PorcupineService with wake word detection and handling
  - Create PorcupineService class extending Service
  - Implement Foreground Service with notification showing active wake word count
  - Add notification action to open settings
  - Implement PorcupineManager initialization with access key validation
  - Implement wake word loading from storage (system + custom)
  - Handle PorcupineManager lifecycle (start/stop/destroy)
  - Create WakeWordHandler class for processing wake word detections
  - Implement system command handling (start/stop/koniec)
  - Implement custom command handling (launch MainActivity with thread ID)
  - Implement broadcast sending for in-app commands (toggle microphone)
  - Implement application termination logic
  - Add activation sound playback with enable/disable toggle
  - Create BootReceiver class with BOOT_COMPLETED intent filter
  - Check if Picovoice is enabled before starting service on boot
  - Add error handling for initialization failures
  - _Requirements: 1.1, 1.2, 1.3, 4.1, 4.2, 4.3, 4.4, 4.6, 4.7, 4.8, 4.9, 4.10, 4.12, 6.1, 6.2, 6.5, 7.5, 7.7, 7.8_

- [x] 4. Implement MainActivity integration and broadcast handling
  - Add onNewIntent handling for wake word launches
  - Register broadcast receivers for toggle microphone and terminate app
  - Implement auto-launch thread logic when receiving wake word intent
  - Add cleanup in onDestroy for broadcast receivers
  - _Requirements: 4.4, 4.5, 4.10_

- [x] 5. Create Picovoice settings UI with wake word management
  - Create PicovoiceSettingsPanel composable in SettingsScreen
  - Add enable/disable toggle with service control
  - Add access key input field with validation
  - Add sensitivity slider (0.0-1.0)
  - Add system wake words section (read-only display)
  - Add custom wake words list with status indicators (green/gray)
  - Add "Add Wake Word" button
  - Add delete wake word functionality
  - Add import .ppn file button for each wake word
  - Create WakeWordInstructionsDialog composable
  - Add step-by-step instructions in Polish
  - Add clickable link to https://console.picovoice.ai
  - Add tips section for choosing good wake words
  - Add "Import .ppn file" button that launches file picker
  - Implement file picker launcher in PicovoiceSettingsPanel
  - Implement file copy to internal storage (filesDir/picovoice/custom/)
  - Add .ppn file validation
  - Update wake word metadata with file path
  - Update UI to show green status after successful import
  - Add error dialogs for initialization failures, invalid access key, permission denial, and invalid .ppn files
  - Add loading indicators during operations
  - Add success messages for wake word operations
  - _Requirements: 2.5, 2.6, 2.7, 2.9, 2.11, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9, 7.6, 7.7, 7.8, 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_

- [x] 6. Implement wake word assignment in ThreadConfigDialog
  - Create WakeWordAssignmentSection composable
  - Add wake word dropdown showing available wake words (green status only)
  - Display currently assigned wake word if exists
  - Add unassign functionality
  - Filter out wake words already assigned to other threads
  - Show message when no wake words available
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.8, 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

- [ ]* 15. Testing and validation
  - [ ]* 15.1 Test system wake word detection (start/stop/koniec)
    - Test in foreground and background
    - Test microphone toggle functionality
    - Test application termination
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_
  
  - [ ]* 15.2 Test custom wake word flow
    - Test adding custom wake word
    - Test instructions dialog
    - Test .ppn file import
    - Test status indicator changes
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_
  
  - [ ]* 15.3 Test thread assignment and launch
    - Test assigning wake word to thread
    - Test wake word filtering in dropdown
    - Test launching app from background
    - Test auto-starting conversation
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 4.3, 4.4, 4.5_
  
  - [ ]* 15.4 Test service lifecycle
    - Test service start/stop
    - Test service restart after crash
    - Test boot receiver
    - Test battery optimization scenarios
    - _Requirements: 4.9, 4.10, 4.11, 4.12_
  
  - [ ]* 15.5 Test error scenarios
    - Test invalid access key
    - Test invalid .ppn file
    - Test permission denial
    - Test service initialization failure
    - _Requirements: 7.5, 7.6, 7.7, 7.8_

- [ ]* 16. Documentation and polish
  - Add code comments and documentation
  - Create user guide for wake word creation
  - Add troubleshooting section in settings
  - Optimize battery usage
  - Performance testing
