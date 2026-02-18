# Implementation Plan: Modern UI Voice Visualizer

## Overview

This implementation plan breaks down the modernization of the Android voice assistant UI into discrete, incremental coding tasks. The plan follows a phased approach: Theme System → Glass Components → Visualizer → Transcript Improvements → Dual Screen Modes → Integration & Polish.

Each task builds on previous work and includes property-based tests to validate correctness. The implementation maintains all existing functionality while introducing the new visual experience.

## Tasks

- [ ] 1. Implement Theme System Foundation
  - [x] 1.1 Create ThemeMode enum and theme resolution logic
    - Create `ThemeMode` enum with AUTO, LIGHT, DARK values in `ui/theme/ThemeManager.kt`
    - Implement `getCurrentTheme(themeMode: ThemeMode, isSystemDark: Boolean): ThemeColors` function
    - Define `ThemeColors` data class with all color properties including gradients
    - _Requirements: 5.1, 5.2, 5.3, 5.4_
  
  - [ ]* 1.2 Write property tests for theme resolution
    - **Property 11: AUTO Theme Mode Follows System Setting**
    - **Property 12: LIGHT Theme Mode Ignores System Setting**
    - **Property 13: DARK Theme Mode Ignores System Setting**
    - **Validates: Requirements 5.2, 5.3, 5.4**
  
  - [x] 1.3 Implement theme preference migration
    - Add `getThemeMode()` and `setThemeMode(mode: ThemeMode)` to `Preferences.kt`
    - Implement `migrateThemePreference()` function to convert boolean to enum
    - Call migration on app startup in `RTVIApplication.kt`
    - _Requirements: 5.7, 5.8_
  
  - [ ]* 1.4 Write property tests for theme migration
    - **Property 16: Theme Migration Correctness**
    - **Property 15: Theme Mode Persistence Round-Trip**
    - **Validates: Requirements 5.7, 5.8**
  
  - [x] 1.5 Define color palettes and gradients for both themes
    - Update `ui/theme/Color.kt` with light and dark gradient colors
    - Define gradient start/end colors for backgrounds
    - Define glass token colors (background, border) for both themes
    - Ensure all colors meet WCAG AA contrast requirements
    - _Requirements: 5.9, 2.8, 8.1_
  
  - [ ]* 1.6 Write property tests for color distinctness and contrast
    - **Property 17: Light and Dark Themes Have Distinct Gradients**
    - **Property 6: WCAG AA Contrast Ratios for Text on Glass**
    - **Validates: Requirements 5.9, 2.8, 8.1**

- [x] 2. Checkpoint - Verify theme system works
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 3. Implement Glass Components
  - [x] 3.1 Create GlassStyle data class and GlassContainer component
    - Create `ui/GlassContainer.kt` with `GlassStyle` data class
    - Implement `GlassContainer` composable with blur effect for API 31+
    - Implement fallback styling for API 26-30 (no blur)
    - Add try-catch for blur effect failures
    - _Requirements: 2.1, 2.2, 2.4_
  
  - [ ]* 3.2 Write unit tests for glass component
    - Test blur applied on API 31+
    - Test fallback on API 26-30
    - Test glass style properties (alpha, border width)
    - **Property 5: Glass Components Have Correct Transparency and Borders**
    - **Validates: Requirements 2.1, 2.2, 2.4**
  
  - [x] 3.3 Create ColorUtils for contrast ratio calculations
    - Create `ui/utils/ColorUtils.kt`
    - Implement `calculateContrastRatio(foreground: Color, background: Color): Float`
    - Handle alpha channel correctly for transparent backgrounds
    - Add clamping for edge cases
    - _Requirements: 2.8, 8.1_
  
  - [ ]* 3.4 Write property tests for contrast calculations
    - Test contrast ratio calculation for various color combinations
    - Test handling of transparent colors
    - Verify WCAG AA compliance (>= 4.5:1)
    - **Validates: Requirements 2.8, 8.1**

- [ ] 4. Implement Visualizer Core Components
  - [x] 4.1 Create PerlinNoiseGenerator
    - Create `ui/utils/PerlinNoiseGenerator.kt`
    - Implement classic Perlin noise algorithm with permutation table
    - Add `noise(x: Float, y: Float, z: Float): Float` function
    - Add error handling for initialization failures
    - _Requirements: 1.4_
  
  - [ ]* 4.2 Write unit tests for Perlin noise
    - Test noise output is in range [-1.0, 1.0]
    - Test noise is continuous (no sudden jumps)
    - Test same inputs produce same outputs (deterministic)
  
  - [x] 4.3 Create AudioUtils with envelope follower
    - Create `ui/utils/AudioUtils.kt`
    - Implement `applyEnvelopeFollower(current: Float, target: Float, alpha: Float): Float`
    - Implement `clampAudioLevel(level: Float): Float` to ensure [0.0, 1.0] range
    - _Requirements: 1.5_
  
  - [ ]* 4.4 Write property tests for envelope follower
    - **Property 3: Envelope Follower Smoothing Reduces Volatility**
    - Test smoothing reduces variance for random audio sequences
    - Test clamping keeps values in valid range
    - **Validates: Requirements 1.5**
  
  - [x] 4.5 Create VisualizerState enum and state determination logic
    - Create `VisualizerState` enum (IDLE, LISTENING, SPEAKING) in `ui/GeminiVisualizer.kt`
    - Implement `determineVisualizerState(isUserSpeaking: Boolean, isBotSpeaking: Boolean): VisualizerState`
    - _Requirements: 1.1, 1.2, 1.3_
  
  - [ ]* 4.6 Write property tests for state transitions
    - **Property 1: Visualizer State Transitions for User Speech**
    - **Property 2: Visualizer State Transitions for Bot Speech**
    - **Validates: Requirements 1.2, 1.3**

- [x] 5. Checkpoint - Verify core utilities work
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 6. Implement GeminiVisualizer Component
  - [x] 6.1 Create GeminiVisualizer composable with Canvas rendering
    - Create `ui/GeminiVisualizer.kt`
    - Implement `GeminiVisualizer(audioLevel: Float, state: VisualizerState, modifier: Modifier)` composable
    - Set up Canvas with LaunchedEffect for 60fps animation loop
    - Implement time tracking for animation
    - _Requirements: 1.1, 1.6, 1.9_
  
  - [x] 6.2 Implement blob point calculation with sine waves and Perlin noise
    - Implement `calculateBlobPoints(time: Float, audioLevel: Float, config: VisualizerConfig): List<Offset>`
    - Use sine waves for base circular shape
    - Apply Perlin noise for organic deformation
    - Scale radius based on audio level
    - _Requirements: 1.4, 1.2, 1.3_
  
  - [x] 6.3 Implement gradient color selection based on state and theme
    - Implement `getGradientColors(state: VisualizerState, theme: ThemeMode): List<Color>`
    - Define color gradients for IDLE (blue/purple), LISTENING (blue/cyan), SPEAKING (orange/pink)
    - Ensure colors adapt to light/dark theme
    - _Requirements: 1.7, 5.10_
  
  - [ ]* 6.4 Write property tests for visualizer colors
    - **Property 4: Gradient Colors Change with State**
    - **Property 18: Visualizer Colors Update with Theme**
    - **Validates: Requirements 1.7, 5.10**
  
  - [x] 6.5 Integrate visualizer with VoiceClientManager
    - Observe `VoiceClientManager.audioLevel` for user speech
    - Observe `VoiceClientManager.botAudioLevel` for bot speech
    - Observe `VoiceClientManager.isUserSpeaking` and `isBotSpeaking` for state
    - Apply envelope follower smoothing to audio levels
    - Add error handling for invalid audio levels
    - _Requirements: 1.2, 1.3, 1.5_
  
  - [ ]* 6.6 Write integration tests for visualizer
    - Test visualizer reacts to audio level changes
    - Test state transitions based on VoiceClientManager state
    - Test error handling for invalid values

- [x] 7. Implement Enhanced Transcript Bubbles
  - [x] 7.1 Create EnhancedTranscriptBubble component
    - Create `ui/EnhancedTranscriptBubble.kt`
    - Implement `EnhancedTranscriptBubble(message: TranscriptMessage, modifier: Modifier)` composable
    - Apply GlassContainer with 16dp rounded corners
    - Add fade-in and slide-in animations for entry
    - Add fade-out and slide-out animations for exit
    - _Requirements: 3.1, 3.2, 3.5, 3.6_
  
  - [x] 7.2 Implement bubble color schemes for user and bot messages
    - Implement `getBubbleColors(isUser: Boolean, theme: ThemeMode): BubbleColors`
    - Define distinct colors for user messages (blue-tinted)
    - Define distinct colors for bot messages (purple-tinted)
    - Ensure colors adapt to theme mode
    - _Requirements: 3.3, 3.8_
  
  - [ ]* 7.3 Write property tests for bubble colors
    - **Property 7: User and Bot Message Colors Are Distinct**
    - **Property 8: Transcript Bubbles Maintain Contrast in All Themes**
    - **Property 9: Transcript Bubble Colors Adapt to Theme**
    - **Validates: Requirements 3.3, 3.7, 3.8**
  
  - [ ]* 7.4 Write accessibility tests for bubbles
    - Test contrast ratios meet WCAG AA in all themes
    - Test content descriptions are present
    - **Validates: Requirements 3.7, 8.1**

- [x] 8. Checkpoint - Verify components render correctly
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Implement Dual Screen Modes
  - [x] 9.1 Create SessionScreenMode enum and mode state management
    - Add `SessionScreenMode` enum (VISUALIZER, TRANSCRIPTION) to `ui/InCallLayout.kt`
    - Add `screenMode` state variable with default VISUALIZER
    - Implement mode toggle logic
    - _Requirements: 4.1, 4.4, 4.5_
  
  - [x] 9.2 Restructure InCallLayout for dual modes
    - Wrap content in `AnimatedContent` with mode-based switching
    - Add transition animations (fade + slide) between modes
    - Ensure transitions are smooth (300-500ms duration)
    - _Requirements: 4.6_
  
  - [x] 9.3 Implement VisualizerModeContent composable
    - Create `VisualizerModeContent` composable in `ui/InCallLayout.kt`
    - Center GeminiVisualizer with large size
    - Make visualizer tappable to toggle to transcription mode
    - Add optional session timer at top
    - _Requirements: 4.2, 4.4_
  
  - [x] 9.4 Implement TranscriptionModeContent composable
    - Create `TranscriptionModeContent` composable in `ui/InCallLayout.kt`
    - Display LazyColumn of EnhancedTranscriptBubble components
    - Auto-scroll to latest message
    - Make transcript area tappable to toggle back to visualizer mode
    - _Requirements: 4.3, 4.5_
  
  - [x] 9.5 Create GlassControlCapsule component
    - Create `GlassControlCapsule` composable in `ui/InCallLayout.kt`
    - Apply GlassContainer styling
    - Include mic toggle, pause/resume, and end session buttons
    - Ensure 48dp minimum touch targets
    - Position at bottom center, visible in both modes
    - _Requirements: 2.3, 8.2_
  
  - [ ]* 9.6 Write property tests for mode transitions
    - **Property 10: Session State Preserved During Mode Transitions**
    - Test session state unchanged after toggling modes
    - Test audio processing continues during transitions
    - **Validates: Requirements 4.7**
  
  - [ ]* 9.7 Write accessibility tests for controls
    - **Property 19: Touch Targets Meet Minimum Size**
    - Test all interactive elements >= 48dp
    - **Property 20: Visual Elements Have Content Descriptions**
    - Test content descriptions present
    - **Validates: Requirements 8.2, 8.4**

- [x] 10. Implement Theme Selection UI
  - [x] 10.1 Add theme mode selection to SettingsScreen
    - Modify `ui/SettingsScreen.kt` to add theme mode selector
    - Create radio button group for AUTO, LIGHT, DARK options
    - Wire selection to `Preferences.setThemeMode()`
    - Show current theme mode as selected
    - _Requirements: 5.5_
  
  - [ ]* 10.2 Write integration tests for theme selection
    - **Property 14: Theme Changes Apply Immediately**
    - Test theme updates without app restart
    - Test UI recomposes with new colors
    - **Validates: Requirements 5.6**

- [x] 11. Checkpoint - Verify all features work together
  - Ensure all tests pass, ask the user if questions arise.

- [x] 12. Integration and Polish
  - [x] 12.1 Add gradient backgrounds to InCallLayout
    - Implement `GradientBackground` composable
    - Apply gradient based on current theme
    - Ensure gradient updates when theme changes
    - _Requirements: 2.5_
  
  - [x] 12.2 Optimize visualizer performance
    - Use `remember` to cache Perlin noise generator
    - Reuse `Path` objects instead of creating new ones
    - Use `derivedStateOf` for computed values
    - Limit recomposition scope to canvas only
    - _Requirements: 1.6, 6.2_
  

    - Add try-catch in Canvas draw scope for visualizer
    - Add fallback static circle if rendering fails
    - Add error logging for debugging
    - Handle blur effect failures gracefully
    - _Requirements: Error Handling section_
  
  - [ ]* 12.4 Run full regression test suite
    - Run all existing tests for LibreChat, STT, TTS, wake word
    - Verify no functionality broken
    - Verify background operation still works
    - Verify session management unchanged
    - **Validates: Requirements 7.1-7.10**
  
  - [ ]* 12.5 Performance validation
    - Measure visualizer frame rate (target 60fps)
    - Measure battery consumption (max 10% increase)
    - Test on different screen sizes and orientations
    - Test memory usage under pressure
    - **Validates: Requirements 6.2, 6.6, 6.7, 6.8**
  
  - [ ]* 12.6 Accessibility validation
    - Run TalkBack and verify all components accessible
    - Verify dynamic font sizing works
    - Verify keyboard navigation where applicable
    - Run full contrast ratio validation
    - **Validates: Requirements 8.3, 8.6, 8.8**

- [x] 13. Final checkpoint - Complete testing and validation
  - Ensure all tests pass, ask the user if questions arise.
  - Verify all 20 correctness properties validated
  - Verify all requirements covered
  - Ready for user acceptance testing

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at key milestones
- Property tests validate universal correctness properties (100+ iterations each)
- Unit tests validate specific examples and edge cases
- Integration tests verify component interactions
- Regression tests ensure no existing functionality broken
- The implementation follows a phased approach to minimize risk
- All existing features (LibreChat, STT, TTS, wake word, background operation) must continue working
- Theme migration runs automatically on first launch after update
- Visualizer uses pure Compose Canvas without external libraries
- Glass effects gracefully degrade on older Android versions (API 26-30)
