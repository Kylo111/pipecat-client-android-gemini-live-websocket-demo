# Implementation Tasks

## Phase 1: Core Stability (Priority 1) 🔴

### Task 1.1: WebSocket Configuration Enhancement
**File:** `VoiceClientManager.kt`
**Estimated time:** 15 minutes

- [x] Change `readTimeout` from `0` to `60 seconds`




-

- [x] Change `pingInterval` from `20` to `15 seconds`



-

- [x] Test connection stability with new timeouts



- [x] Verify ping-pong timeout detection is faster












- [ ] Verify ping-pong timeout detection is faster


**Acceptance Criteria:**
- WebSocket client has 60s read timeout
- Ping interval is 15 seconds
- Connection failures are detected within 15-20 seconds

---

### Task 1.2: WebSocket Error Classifier
**File:** `utils/WebSocketErrorClassifier.kt` (new)
**Estimated time:** 30 minutes


- [x] Create `WebSocketErrorClassifier` object




- [x] Implement `ErrorType` enum (RECOVERABLE, FATAL, UNKNOWN)





- [x] Implement `classifyError(throwable: Throwable)` method





- [x] Add classification logic for:




  - SocketTimeoutException → RECOVERABLE
  - UnknownHostException → RECOVERABLE
  - IOException → RECOVERABLE
  - ConnectException → RECOVERABLE
  - EOFException → RECOVERABLE
  - SSLException → FATAL
  - ProtocolException → FATAL
  - IllegalStateException → FATAL
  - SecurityException → FATAL
  - Others → UNKNOWN
- [x] Add unit tests for all error types





**Acceptance Criteria:**
- All error types are correctly classified
- Unit tests pass with 100% coverage
- Classifier is used in WebSocket failure handler

---

### Task 1.3: Connection State Enhancement
**File:** `VoiceClientManager.kt`
**Estimated time:** 20 minutes

- [x] Add `RECONNECTING` state to `ConnectionState` enum




-

- [x] Update state machine to support RECONNECTING state





- [x] Update UI to display reconnecting status




- [x] Add state transition logging





**Acceptance Criteria:**
- RECONNECTING state exists in enum
- UI shows "Ponowne łączenie..." when in RECONNECTING state
- State transitions are logged

---

### Task 1.4: ReconnectionManager Implementation
**File:** `VoiceClientManager.kt` (inner class)
**Estimated time:** 1 hour
-

- [x] Create `ReconnectionManager` inner class




- [x] Implement exponential backoff calculation (1s, 2s, 4s, 8s, 16s)





- [x] Implement `startReconnection()` method





- [x] Implement attempt counter (max 5 attempts)






- [x] Implement `cancelReconnection()` method







- [x] Implement `reset()` method




-

- [x] Add callback for showing user dialog after max attempts






- [x] Integrate with VoiceClientManager







**Acceptance Criteria:**
- Reconnection attempts use exponential backoff
- Max 5 attempts before showing dialog
- Reconnection can be cancelled
- Counter resets on successful connection

---

### Task 1.5: Enhanced WebSocket Failure Handler
**File:** `VoiceClientManager.kt`
**Estimated time:** 45 minutes

- [x] Modify `onFailure()` to use `WebSocketErrorClassifier`



- [x] For RECOVERABLE errors: trigger reconnection, stay in conversation





- [x] For FATAL errors: show error, disconnect normally





- [x] For UNKNOWN errors: log and treat as recoverable





- [x] Remove automatic navigation to thread list on error





- [x] Add detailed error logging








**Acceptance Criteria:**
- Recoverable errors trigger automatic reconnection
- Fatal errors show appropriate error message
- App stays in conversation screen during reconnection
- No automatic navigation away from conversation

---

### Task 1.6: AudioTrack Synchronization
**File:** `VoiceClientManager.kt`
**Estimated time:** 20 minutes
- [x] Add `private val audioTrackMutex = Mutex()`




- [ ] Add `private val audioTrackMutex = Mutex()`


- [x] Wrap AudioTrack write operations with `audioTrackMutex.withLock {}`




- [x] Test concurrent audio playback




- [x] Verify no race conditions





**Acceptance Criteria:**
- AudioTrack writes are synchronized
- No audio corruption during concurrent writes
- No performance degradation

---

## Phase 2: User Experience (Priority 2) 🟡

### Task 2.1: Reconnection Dialog UI
**File:** `ui/ReconnectionDialog.kt` (new)
**Estimated time:** 45 minutes

- [x] Create `ReconnectionDialog` composable

- [x] Add dialog text: "Nie udało się połączyć po 5 próbach. Kontynuować próby?"



- [ ] Add dialog text: "Nie udało się połączyć po 5 próbach. Kontynuować próby?"
-

- [x] Add "Kontynuuj" button (resets counter, continues reconnection)


- [x] Add "Zakończ rozmowę" button (ends session)



- [ ] Add "Zakończ rozmowę" button (ends session)
- [x] Integrate with ReconnectionManager




- [ ] Integrate with ReconnectionManager
-

- [x] Add Polish translations





**Acceptance Criteria:**
- Dialog appears after 5 failed reconnection attempts
- "Kontynuuj" resets counter and continues reconnection
- "Zakończ rozmowę" ends session and navigates to thread list
- Dialog is in Polish

---

### Task 2.2: Reconnection Status UI
**File:** `ui/InCallHeader.kt` or `ui/ConnectionStatusIndicator.kt` (new)
**Estimated time:** 30 minutes

- [x] Add connection status indicator to in-call UI



- [x] Show "Połączono" when CONNECTED (green)





- [x] Show "Ponowne łączenie... próba X z 5" when RECONNECTING (yellow)




- [x] Show "Rozłączono" when DISCONNECTED (red)




- [x] Add animated indicator for reconnecting state




- [x] Polish translations







**Acceptance Criteria:**
- Status indicator is visible in conversation screen
- Shows current connection state
- Shows attempt number during reconnection
- Animated during reconnection

---

### Task 2.3: ImageProcessor Implementation
**File:** `utils/ImageProcessor.kt` (new)
**Estimated time:** 1.5 hours

- [ ] Create `ImageProcessor` class
- [ ] Implement `ProcessedImage` data class
- [ ] Implement `processImage(uri: Uri)` method
- [ ] Implement image loading with `BitmapFactory.Options.inSampleSize`
- [ ] Implement resize logic (max 2300px on longest dimension)
- [ ] Implement compression (85% JPEG quality)
- [ ] Add size validation (max 5MB raw)
- [ ] Add OutOfMemoryError handling
- [ ] Return `Result<ProcessedImage>`
- [ ] Add unit tests

**Acceptance Criteria:**
- Images are resized to max 2300px on longest dimension
- Images are compressed to 85% JPEG quality
- Aspect ratio is maintained
- OutOfMemoryError is handled gracefully
- Processing runs on IO dispatcher
- Unit tests pass

---

### Task 2.4: Enhanced sendImage with Processing
**File:** `VoiceClientManager.kt`
**Estimated time:** 1 hour

- [ ] Integrate `ImageProcessor` into `sendImage()` method
- [ ] Add progress indicator during processing
- [ ] Add image queue for retry (`pendingImage: Uri?`)
- [ ] Queue image if not connected
- [ ] Show message: "Obraz zostanie wysłany po ponownym połączeniu"
- [ ] Implement `retryPendingImage()` method
- [ ] Call `retryPendingImage()` after successful reconnection
- [ ] Add error handling for processing failures
- [ ] Add timeout (30 seconds)

**Acceptance Criteria:**
- Images are processed before sending
- Progress indicator is shown during processing
- Images are queued when not connected
- Queued images are sent after reconnection
- Processing failures show appropriate error messages

---

### Task 2.5: BackPressHandler Implementation
**File:** `MainActivity.kt`
**Estimated time:** 45 minutes

- [ ] Create `BackPressHandler` composable
- [ ] Use Compose `BackHandler` API
- [ ] Check current screen and connection state
- [ ] Show confirmation dialog when in IN_CALL with active connection
- [ ] Dialog text: "Czy chcesz zakończyć rozmowę?"
- [ ] Buttons: "Tak" (ends session), "Nie" (stays)
- [ ] No dialog when disconnected - navigate directly
- [ ] No dialog on thread list - exit app
- [ ] Integrate with MainActivity

**Acceptance Criteria:**
- Back button shows confirmation dialog during active conversation
- Dialog is in Polish
- "Tak" ends session and navigates to thread list
- "Nie" stays in conversation
- No dialog when already disconnected
- Back on thread list exits app

---

### Task 2.6: Stay in Conversation Screen
**File:** `VoiceClientManager.kt`, `MainActivity.kt`
**Estimated time:** 30 minutes

- [ ] Remove all automatic navigation to thread list on errors
- [ ] Ensure RECONNECTING state keeps user in conversation screen
- [ ] Only navigate when user explicitly ends session
- [ ] Update error handling to not trigger navigation
- [ ] Test all error scenarios

**Acceptance Criteria:**
- App never automatically navigates away from conversation
- User stays in conversation during reconnection
- Only explicit user action (ending session) navigates away
- All error scenarios tested

---

## Phase 3: Background Operation (Priority 3) 🟢

### Task 3.1: VoiceService Implementation
**File:** `VoiceService.kt` (new)
**Estimated time:** 2 hours

- [ ] Create `VoiceService` class extending `Service`
- [ ] Implement `onStartCommand()` with ACTION_START and ACTION_STOP
- [ ] Create notification channel "voice_conversation"
- [ ] Implement `createNotification()` method
- [ ] Notification title: "Rozmowa z AI"
- [ ] Notification text: "Trwa rozmowa głosowa"
- [ ] Add "Zakończ" action button
- [ ] Implement foreground service start
- [ ] Implement service stop and cleanup
- [ ] Add to AndroidManifest.xml
- [ ] Add FOREGROUND_SERVICE permission

**Acceptance Criteria:**
- Service runs as foreground service
- Notification is shown when service is active
- "Zakończ" button ends conversation
- Service stops when conversation ends
- Proper cleanup on service destroy

---

### Task 3.2: Wake Lock Management
**File:** `VoiceService.kt`
**Estimated time:** 45 minutes

- [ ] Add `PowerManager.WakeLock` field
- [ ] Implement `acquireWakeLock()` method
- [ ] Use `PARTIAL_WAKE_LOCK` type
- [ ] Set timeout to 2 hours as safety measure
- [ ] Implement `releaseWakeLock()` method
- [ ] Acquire wake lock when service starts
- [ ] Release wake lock when service stops
- [ ] Add WAKE_LOCK permission to manifest
- [ ] Handle wake lock exceptions

**Acceptance Criteria:**
- Wake lock is acquired when conversation goes to background
- Wake lock is PARTIAL_WAKE_LOCK type
- Wake lock has 2-hour timeout
- Wake lock is released when conversation ends
- No wake lock leaks

---

### Task 3.3: Notification Updates
**File:** `VoiceService.kt`
**Estimated time:** 30 minutes

- [ ] Implement `updateNotification(status: String)` method
- [ ] Update notification text based on connection state:
  - "Trwa rozmowa głosowa" (CONNECTED)
  - "Ponowne łączenie..." (RECONNECTING)
  - "Rozłączono" (DISCONNECTED)
- [ ] Integrate with VoiceClientManager state changes
- [ ] Test notification updates

**Acceptance Criteria:**
- Notification text reflects current connection state
- Updates happen in real-time
- Notification is always visible when service is running

---

### Task 3.4: MainActivity Lifecycle Integration
**File:** `MainActivity.kt`
**Estimated time:** 1 hour

- [ ] Implement `startVoiceService()` method
- [ ] Implement `stopVoiceService()` method
- [ ] Override `onPause()` - start service if conversation active
- [ ] Override `onResume()` - update UI, keep service running
- [ ] Override `onDestroy()` - stop service
- [ ] Handle service start for API 26+ (startForegroundService)
- [ ] Test lifecycle transitions

**Acceptance Criteria:**
- Service starts when app goes to background with active conversation
- Service continues when app returns to foreground
- Service stops when app is destroyed
- Works on Android 8.0+ (API 26+)

---

### Task 3.5: Session Timeout in Background
**File:** `VoiceClientManager.kt`
**Estimated time:** 30 minutes

- [ ] Ensure session timeout works in background
- [ ] Stop VoiceService when timeout occurs
- [ ] Release wake lock on timeout
- [ ] Test timeout while app is in background
- [ ] Verify proper cleanup

**Acceptance Criteria:**
- Session timeout works in background
- Service stops on timeout
- Wake lock is released on timeout
- User returns to thread list after timeout

---

## Phase 4: Polish & Optimization (Priority 4) 🔵

### Task 4.1: TranscriptSyncManager Enhancement
**File:** `SessionManager.kt`
**Estimated time:** 1 hour

- [ ] Create `TranscriptSyncManager` class
- [ ] Implement infinite retry with exponential backoff
- [ ] Add `SyncStatus` sealed class (Idle, Syncing, Success, Error)
- [ ] Implement `syncTranscripts()` method
- [ ] Show progress: "Zapisywanie transkrypcji... próba X"
- [ ] Block new conversations until sync completes
- [ ] Add cancel option with warning
- [ ] Integrate with existing RetryPolicy

**Acceptance Criteria:**
- Transcripts are retried until success
- Progress is shown to user
- New conversations are blocked during sync
- User can cancel with warning
- Sync status is observable

---

### Task 4.2: Enhanced Error Messages
**File:** `VoiceClientManager.kt`, `strings.xml`
**Estimated time:** 45 minutes

- [ ] Add Polish error messages for all error types
- [ ] Network timeout: "Przekroczono limit czasu połączenia"
- [ ] DNS failure: "Nie można znaleźć serwera"
- [ ] Connection refused: "Serwer niedostępny"
- [ ] SSL error: "Błąd certyfikatu SSL"
- [ ] Image too large: "Obraz za duży"
- [ ] Image processing failed: "Nie udało się przetworzyć obrazu"
- [ ] Add to strings.xml
- [ ] Update error handling to use string resources

**Acceptance Criteria:**
- All error messages are in Polish
- Messages are user-friendly
- Messages are stored in strings.xml
- Appropriate message for each error type

---

### Task 4.3: Image Processing Progress Indicator
**File:** `ui/ImageProcessingIndicator.kt` (new)
**Estimated time:** 30 minutes

- [ ] Create `ImageProcessingIndicator` composable
- [ ] Show progress bar during image processing
- [ ] Show text: "Przetwarzanie obrazu..."
- [ ] Show cancel button (optional)
- [ ] Integrate with sendImage flow
- [ ] Test with large images

**Acceptance Criteria:**
- Progress indicator is shown during image processing
- User knows image is being processed
- Indicator disappears after processing completes

---

### Task 4.4: Performance Optimization
**File:** `VoiceClientManager.kt`, `ImageProcessor.kt`
**Estimated time:** 1 hour

- [ ] Profile image processing performance
- [ ] Optimize bitmap loading with inSampleSize
- [ ] Optimize compression algorithm
- [ ] Profile memory usage during image processing
- [ ] Optimize reconnection backoff timing
- [ ] Profile battery usage in background
- [ ] Add performance logging

**Acceptance Criteria:**
- Image processing takes < 2 seconds for typical images
- Memory usage is optimized
- Battery drain is < 5% per hour in background
- No performance regressions

---

### Task 4.5: Comprehensive Testing
**File:** Various test files
**Estimated time:** 2 hours

- [ ] Write unit tests for WebSocketErrorClassifier
- [ ] Write unit tests for ImageProcessor
- [ ] Write unit tests for ReconnectionManager
- [ ] Write integration test for reconnection flow
- [ ] Write integration test for background operation
- [ ] Write integration test for back button handling
- [ ] Write integration test for image send with compression
- [ ] Manual test: network instability
- [ ] Manual test: image send during poor connection
- [ ] Manual test: long background session
- [ ] Manual test: screen off operation
- [ ] Manual test: session timeout in background

**Acceptance Criteria:**
- All unit tests pass
- All integration tests pass
- All manual test scenarios pass
- Code coverage > 80%

---

### Task 4.6: Documentation
**File:** `README.md`, code comments
**Estimated time:** 1 hour

- [ ] Document new components in README
- [ ] Add code comments for complex logic
- [ ] Document reconnection strategy
- [ ] Document image processing parameters
- [ ] Document background operation requirements
- [ ] Update architecture documentation
- [ ] Add troubleshooting guide

**Acceptance Criteria:**
- All new components are documented
- Complex logic has clear comments
- README is updated
- Troubleshooting guide is complete

---

## Summary

**Total Tasks:** 31
**Estimated Total Time:** ~20 hours

### By Phase:
- **Phase 1 (Core Stability):** 6 tasks, ~4 hours
- **Phase 2 (User Experience):** 6 tasks, ~5.5 hours
- **Phase 3 (Background Operation):** 5 tasks, ~5 hours
- **Phase 4 (Polish & Optimization):** 6 tasks, ~5.5 hours

### Priority Order:
1. Start with Phase 1 for immediate stability improvements
2. Move to Phase 2 for better user experience
3. Implement Phase 3 for background operation
4. Finish with Phase 4 for polish and optimization

### Critical Path:
Task 1.1 → 1.2 → 1.3 → 1.4 → 1.5 → 2.1 → 2.2 → 2.3 → 2.4 → 2.5 → 2.6

### Can be done in parallel:
- Task 1.6 (AudioTrack sync) - independent
- Task 2.3 (ImageProcessor) - independent until Task 2.4
- Task 3.1-3.5 (Background operation) - can start after Phase 1
- Task 4.1-4.6 (Polish) - can be done anytime
