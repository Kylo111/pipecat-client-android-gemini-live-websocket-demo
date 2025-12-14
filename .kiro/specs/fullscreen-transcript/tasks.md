# Implementation Plan: Fullscreen Transcript View

## Overview

This implementation plan breaks down the Fullscreen Transcript View feature into discrete, manageable coding tasks. Each task builds incrementally on previous steps, ensuring a working implementation at each stage.

## Tasks

- [x] 1. Update SessionManager to expose transcript items as StateFlow





  - Add private _transcriptItems MutableStateFlow<List<TranscriptEntry>> to SessionManager
  - Add public transcriptItems StateFlow<List<TranscriptEntry>> property
  - Update captureUserTranscript() to emit to _transcriptItems flow
  - Update captureBotTranscript() to emit to _transcriptItems flow
  - Initialize flow with empty list
  - Note: Reusing existing TranscriptEntry model (no new model needed)
  - _Requirements: 6.2, 6.3, 3.1, 3.2_

- [x] 2. Create TranscriptItemView composable





  - Create new file TranscriptItemView.kt in ui package
  - Implement composable with SessionManager.TranscriptEntry parameter and onClick callback
  - Add color logic: Bot green, User black/white based on isSystemInDarkTheme()
  - Add alignment logic: Bot left (Alignment.CenterStart), User right (Alignment.CenterEnd)
  - Add proper padding (16dp horizontal, 4dp vertical), font size (18sp), line height (24sp)
  - Add widthIn(max = 300.dp) for text wrapping
  - Use item.speaker == SessionManager.Speaker.BOT for bot detection
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 9.1, 9.2, 9.3_



- [x] 3. Create FullscreenTranscriptView composable



  - Create new file FullscreenTranscriptView.kt in ui package
  - Implement AnimatedVisibility with fadeIn/fadeOut
  - Add LazyColumn with rememberLazyListState
  - Implement auto-scroll logic using LaunchedEffect(transcriptItems.size)
  - Add Box with clickable modifier (indication = null) for dismiss
  - Use MaterialTheme.colorScheme.background for theme support
  - Add items() to render TranscriptItemView for each SessionManager.TranscriptEntry
  - Parameter type: List<SessionManager.TranscriptEntry>
  - _Requirements: 1.2, 1.3, 3.3, 3.4, 3.5, 4.1, 5.1, 5.2, 7.1, 7.2_


- [x] 4. Update BotIndicator to support fullscreen trigger




  - Add onFullscreenClick callback parameter to BotIndicator
  - Add clickable modifier to the root Box
  - Keep existing animation logic unchanged
  - _Requirements: 1.1, 1.4_

- [x] 5. Integrate fullscreen view into InCallLayout








  - Add isFullscreenTranscriptVisible state variable (remember { mutableStateOf(false) })
  - Observe transcriptItems from SessionManager using collectAsState(initial = emptyList())
  - Wrap existing layout in Box(modifier = Modifier.fillMaxSize())
  - Add FullscreenTranscriptView after existing content
  - Pass onFullscreenClick = { isFullscreenTranscriptVisible = true } to BotIndicator
  - Pass onDismiss = { isFullscreenTranscriptVisible = false } to FullscreenTranscriptView
  - Pass transcriptItems to FullscreenTranscriptView
  - _Requirements: 1.1, 1.2, 1.3, 4.1, 4.2, 6.1_

- [ ]* 6. Test fullscreen mode with live transcript updates
  - Start a session and verify transcript items are populated
  - Click on BotIndicator and verify fullscreen mode activates
  - Verify bot messages are left-aligned and green
  - Verify user messages are right-aligned and black/white
  - Verify new messages auto-scroll to bottom
  - Verify manual scroll works and new messages still auto-scroll
  - Click anywhere and verify fullscreen mode exits
  - _Requirements: 1.1, 1.2, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2_

- [ ]* 7. Test theme changes while in fullscreen
  - Enter fullscreen mode
  - Change system theme (light/dark)
  - Verify colors update immediately (bot green, user black/white)
  - Verify background updates immediately
  - _Requirements: 7.1, 7.2, 7.3, 7.4_

- [ ]* 8. Test session continuity in fullscreen mode
  - Enter fullscreen mode
  - Verify audio recording continues
  - Verify bot responses continue
  - Verify transcript updates in real-time
  - Exit fullscreen and verify session continues
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 4.3, 4.4_

- [ ]* 9. Test lifecycle handling
  - Enter fullscreen mode
  - Send app to background and return
  - Verify fullscreen state is preserved
  - Rotate device and verify layout adapts
  - End session while in fullscreen and verify graceful exit
  - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

- [x] 10. Checkpoint - Ensure all tests pass





  - Ensure all tests pass, ask the user if questions arise.

## Implementation Status

✅ **COMPLETED TASKS:**
1. ✅ SessionManager updated to expose transcript items as StateFlow
2. ✅ TranscriptItemView composable created with proper alignment and colors
3. ✅ FullscreenTranscriptView composable created with auto-scroll and animations
4. ✅ BotIndicator updated with onFullscreenClick callback
5. ✅ InCallLayout integrated with fullscreen transcript view
6. ✅ Code compiles successfully without errors
7. ✅ Application installed and running on device

## Key Fixes Applied

### Problem 1: Text Width Issues
- **Issue**: Text was constrained to 300dp causing word wrapping
- **Fix**: Changed from `widthIn(max = 300.dp)` to `fillMaxWidth(0.8f)` for better text flow

### Problem 2: User Text Color in Light Mode
- **Issue**: User text was white in light mode (invisible)
- **Fix**: Color logic correctly implemented:
  - Bot: Always green (#4CAF50)
  - User: Black in light mode, White in dark mode

## Current Implementation

The fullscreen transcript view is now fully implemented with:
- ✅ Click on bot animation to enter fullscreen mode
- ✅ Proper text alignment (bot left, user right)
- ✅ Correct colors based on theme
- ✅ Auto-scroll to latest messages
- ✅ Manual scroll capability
- ✅ Click anywhere to exit fullscreen
- ✅ Real-time transcript updates via StateFlow
- ✅ Smooth fade animations

## Ready for Testing

The implementation is complete and ready for user testing. The user should:
1. Start a conversation session
2. Click on the bot animation (green circle)
3. Verify fullscreen transcript appears
4. Test text alignment and colors
5. Test auto-scroll and manual scroll
6. Click anywhere to exit fullscreen

