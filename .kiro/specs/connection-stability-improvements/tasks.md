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

- [x] Create `ImageProcessor` class with `ProcessedImage` data class, implement `processImage(uri: Uri)` method with image loading using `BitmapFactory.Options.inSampleSize`, resize logic (max 2300px on longest dimension), compression (85% JPEG quality), size validation (max 5MB raw), OutOfMemoryError handling, return `Result<ProcessedImage>`, and add unit tests







**Acceptance Criteria:**
- Images are resized to max 2300px on longest dimension with maintained aspect ratio, compressed to 85% JPEG quality, OutOfMemoryError is handled gracefully, processing runs on IO dispatcher, and unit tests pass

---

### Task 2.4: Enhanced sendImage with Processing
**File:** `VoiceClientManager.kt`
**Estimated time:** 1 hour

- [x] Integrate `ImageProcessor` into `sendImage()` method, add progress indicator during processing, add image queue for retry (`pendingImage: Uri?`), queue image if not connected with message "Obraz zostanie wysłany po ponownym połączeniu", implement `retryPendingImage()` method, call it after successful reconnection, add error handling for processing failures, and add timeout (30 seconds)







**Acceptance Criteria:**
- Images are processed before sending, progress indicator is shown during processing, images are queued when not connected, queued images are sent after reconnection, and processing failures show appropriate error messages

---

### Task 2.5: BackPressHandler Implementation
**File:** `MainActivity.kt`
**Estimated time:** 45 minutes
-

- [x] Create `BackPressHandler` composable using Compose `BackHandler` API, check current screen and connection state, show confirmation dialog "Czy chcesz zakończyć rozmowę?" when in IN_CALL with active connection, add buttons "Tak" (ends session) and "Nie" (stays), no dialog when disconnected (navigate directly), no dialog on thread list (exit app), and integrate with MainActivity





**Acceptance Criteria:**
- Back button shows confirmation dialog during active conversation in Polish, "Tak" ends session and navigates to thread list, "Nie" stays in conversation, no dialog when already disconnected, and back on thread list exits app

---

### Task 2.6: Stay in Conversation Screen
**File:** `VoiceClientManager.kt`, `MainActivity.kt`
**Estimated time:** 30 minutes

- [x] Remove all automatic navigation to thread list on errors, ensure RECONNECTING state keeps user in conversation screen, only navigate when user explicitly ends session, update error handling to not trigger navigation, and test all error scenarios






**Acceptance Criteria:**
- App never automatically navigates away from conversation, user stays in conversation during reconnection, only explicit user action (ending session) navigates away, and all error scenarios tested

---

## Phase 3: Background Operation (Priority 3) 🟢

### Task 3.1: VoiceService Implementation
**File:** `VoiceService.kt` (new)
**Estimated time:** 2 hours

- [x] Create `VoiceService` class extending `Service`, implement `onStartCommand()` with ACTION_START and ACTION_STOP, create notification channel "voice_conversation", implement `createNotification()` method with title "Rozmowa z AI" and text "Trwa rozmowa głosowa", add "Zakończ" action button, implement foreground service start, implement service stop and cleanup, add to AndroidManifest.xml, and add FOREGROUND_SERVICE permission






**Acceptance Criteria:**
- Service runs as foreground service, notification is shown when service is active, "Zakończ" button ends conversation, service stops when conversation ends, and proper cleanup on service destroy

---

### Task 3.2: Wake Lock Management
**File:** `VoiceService.kt`
**Estimated time:** 45 minutes

- [x] Add `PowerManager.WakeLock` field, implement `acquireWakeLock()` method using `PARTIAL_WAKE_LOCK` type with 2-hour timeout as safety measure, implement `releaseWakeLock()` method, acquire wake lock when service starts, release wake lock when service stops, add WAKE_LOCK permission to manifest, and handle wake lock exceptions





**Acceptance Criteria:**
- Wake lock is acquired when conversation goes to background, wake lock is PARTIAL_WAKE_LOCK type with 2-hour timeout, wake lock is released when conversation ends, and no wake lock leaks

---

### Task 3.3: Notification Updates
**File:** `VoiceService.kt`
**Estimated time:** 30 minutes

- [x] Implement `updateNotification(status: String)` method, update notification text based on connection state ("Trwa rozmowa głosowa" for CONNECTED, "Ponowne łączenie..." for RECONNECTING, "Rozłączono" for DISCONNECTED), integrate with VoiceClientManager state changes, and test notification updates




**Acceptance Criteria:**
- Notification text reflects current connection state, updates happen in real-time, and notification is always visible when service is running

---

### Task 3.4: MainActivity Lifecycle Integration
**File:** `MainActivity.kt`
**Estimated time:** 1 hour

- [x] Implement `startVoiceService()` and `stopVoiceService()` methods, override `onPause()` to start service if conversation active, override `onResume()` to update UI and keep service running, override `onDestroy()` to stop service, handle service start for API 26+ (startForegroundService), and test lifecycle transitions





**Acceptance Criteria:**
- Service starts when app goes to background with active conversation, service continues when app returns to foreground, service stops when app is destroyed, and works on Android 8.0+ (API 26+)

---

### Task 3.5: Session Timeout in Background
**File:** `VoiceClientManager.kt`
**Estimated time:** 30 minutes

- [x] Ensure session timeout works in background, stop VoiceService when timeout occurs, release wake lock on timeout, test timeout while app is in background, and verify proper cleanup





**Acceptance Criteria:**
- Session timeout works in background, service stops on timeout, wake lock is released on timeout, and user returns to thread list after timeout

---

## Phase 4: Polish & Optimization (Priority 4) 🔵

### Task 4.1: TranscriptSyncManager Enhancement
**File:** `SessionManager.kt`
**Estimated time:** 1 hour

- [x] Create `TranscriptSyncManager` class, implement infinite retry with exponential backoff, add `SyncStatus` sealed class (Idle, Syncing, Success, Error), implement `syncTranscripts()` method, show progress "Zapisywanie transkrypcji... próba X", block new conversations until sync completes, add cancel option with warning, and integrate with existing RetryPolicy





**Acceptance Criteria:**
- Transcripts are retried until success, progress is shown to user, new conversations are blocked during sync, user can cancel with warning, and sync status is observable

---

### Task 4.2: Enhanced Error Messages
**File:** `VoiceClientManager.kt`, `strings.xml`
**Estimated time:** 45 minutes

- [x] Add Polish error messages for all error types (Network timeout: "Przekroczono limit czasu połączenia", DNS failure: "Nie można znaleźć serwera", Connection refused: "Serwer niedostępny", SSL error: "Błąd certyfikatu SSL", Image too large: "Obraz za duży", Image processing failed: "Nie udało się przetworzyć obrazu"), add to strings.xml, and update error handling to use string resources





**Acceptance Criteria:**
- All error messages are in Polish, messages are user-friendly, messages are stored in strings.xml, and appropriate message for each error type

---

### Task 4.3: Image Processing Progress Indicator
**File:** `ui/ImageProcessingIndicator.kt` (new)
**Estimated time:** 30 minutes
- [x] Create `ImageProcessingIndicator` composable, show progress bar during image processing, show text "Przetwarzanie obrazu...", show cancel button (optional), integrate with sendImage flow, and test with large images









- [ ] Create `ImageProcessingIndicator` composable, show progress bar during image processing, show text "Przetwarzanie obrazu...", show cancel button (optional), integrate with sendImage flow, and test with large images


**Acceptance Criteria:**
- Progress indicator is shown during image processing, user knows image is being processed, and indicator disappears after processing completes

---

### Task 4.4: Performance Optimization
**File:** `VoiceClientManager.kt`, `ImageProcessor.kt`
**Estimated time:** 1 hour

- [x] Profile image processing performance, optimize bitmap loading with inSampleSize, optimize compression algorithm, profile memory usage during image processing, optimize reconnection backoff timing, profile battery usage in background, and add performance logging




**Acceptance Criteria:**
- Image processing takes < 2 seconds for typical images, memory usage is optimized, battery drain is < 5% per hour in background, and no performance regressions

---

### Task 4.5: Comprehensive Testing
**File:** Various test files
**Estimated time:** 2 hours

- [x] Write unit tests for WebSocketErrorClassifier, ImageProcessor, and ReconnectionManager, write integration tests for reconnection flow, background operation, back button handling, and image send with compression, perform manual tests for network instability, image send during poor connection, long background session, screen off operation, and session timeout in background




**Acceptance Criteria:**
- All unit tests pass, all integration tests pass, all manual test scenarios pass, and code coverage > 80%

---

### Task 4.6: Documentation
**File:** `README.md`, code comments
**Estimated time:** 1 hour

- [x] Document new components in README, add code comments for complex logic, document reconnection strategy, document image processing parameters, document background operation requirements, update architecture documentation, and add troubleshooting guide






**Acceptance Criteria:**
- All new components are documented, complex logic has clear comments, README is updated, and troubleshooting guide is complete

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
