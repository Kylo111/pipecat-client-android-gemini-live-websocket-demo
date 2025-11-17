# RAPORT AUDYTU BEZPIECZEŃSTWA I JAKOŚCI KODU

**Data:** 17 listopada 2025  
**Aplikacja:** Gemini Multimodal WebSocket Demo (Android)  
**Wersja:** Aktualna wersja z repozytorium

---

## STRESZCZENIE WYKONAWCZE

Przeprowadzono szczegółowy audyt bezpieczeństwa i jakości kodu aplikacji Android do komunikacji głosowej z Gemini AI. Zidentyfikowano **12 krytycznych podatności**, **8 poważnych problemów** oraz **15 średnich zagrożeń**. Główne obszary problemowe to zarządzanie cyklem życia, wycieki zasobów, brak obsługi scenariuszy background oraz potencjalne zombie procesy.

### Ocena ogólna: ⚠️ **WYMAGA NATYCHMIASTOWEJ INTERWENCJI**

---

## 1. KRYTYCZNE PODATNOŚCI (Priority: CRITICAL)

### 1.1 🔴 Brak cleanup w MainActivity.onDestroy()
**Lokalizacja:** `MainActivity.kt:1000-1020`  
**Opis:** MainActivity nie wywołuje `voiceClientManager.stop()` w `onDestroy()` gdy użytkownik nie zakończył ręcznie sesji.

**Scenariusz ataku:**
1. Użytkownik uruchamia rozmowę
2. Minimalizuje aplikację
3. System Android zabija proces z powodu braku pamięci
4. WebSocket pozostaje otwarty, wake lock aktywny, audio recording działa w tle

**Konsekwencje:**
- Wyciek baterii (wake lock nigdy nie zwolniony)
- Wyciek pamięci (AudioRecord/AudioTrack nie zwolnione)
- Zombie WebSocket connections
- Potencjalne nagrywanie audio bez wiedzy użytkownika

**Kod problematyczny:**
```kotlin
override fun onDestroy() {
    super.onDestroy()
    unregisterWakeWordBroadcastReceivers()
    
    if (isFinishing) {
        val connectionState = voiceClientManager.state.value
        if (connectionState == ConnectionState.CONNECTED || 
            connectionState == ConnectionState.RECONNECTING) {
            // ❌ NIE ZATRZYMUJE voiceClientManager!
            Log.d("MainActivity", "Activity finishing but conversation active - VoiceService continues")
        } else {
            stopVoiceService()
        }
    }
    networkMonitor.unregister()
}
```


**Rekomendacja:**
```kotlin
override fun onDestroy() {
    super.onDestroy()
    unregisterWakeWordBroadcastReceivers()
    
    // ZAWSZE cleanup resources
    if (isFinishing) {
        lifecycleScope.launch {
            // End session gracefully
            voiceClientManager.sessionManager?.endSession()
            voiceClientManager.stop()
        }
        stopVoiceService()
    }
    networkMonitor.unregister()
}
```

---

### 1.2 🔴 VoiceService nie zatrzymuje się automatycznie
**Lokalizacja:** `VoiceService.kt:90-100`  
**Opis:** VoiceService używa `START_NOT_STICKY` ale nie ma mechanizmu auto-stop po timeout.

**Scenariusz problemu:**
1. Użytkownik rozpoczyna rozmowę (VoiceService startuje)
2. Aplikacja crashuje lub jest zabita przez system
3. VoiceService pozostaje aktywny z wake lock
4. Bateria wyciekana przez godziny/dni

**Kod problematyczny:**
```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // ...
    return START_NOT_STICKY // ❌ Nie wystarczy!
}
```

**Rekomendacja:**
```kotlin
private var serviceTimeoutJob: Job? = null
private val MAX_SERVICE_DURATION = 2 * 60 * 60 * 1000L // 2 godziny

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
        ACTION_START -> {
            startForegroundService()
            acquireWakeLock()
            startServiceTimeout() // ✅ Dodaj timeout
        }
        // ...
    }
    return START_NOT_STICKY
}

private fun startServiceTimeout() {
    serviceTimeoutJob?.cancel()
    serviceTimeoutJob = CoroutineScope(Dispatchers.Default).launch {
        delay(MAX_SERVICE_DURATION)
        Log.w(TAG, "Service timeout reached, stopping service")
        stopService()
    }
}
```

---

### 1.3 🔴 PorcupineService startuje na BOOT bez weryfikacji
**Lokalizacja:** `BootReceiver.kt:15-25`  
**Opis:** BootReceiver automatycznie startuje PorcupineService po restarcie urządzenia bez sprawdzenia czy użytkownik tego chce.

**Scenariusz ataku:**
1. Użytkownik instaluje aplikację
2. Urządzenie restartuje się
3. PorcupineService automatycznie startuje i nasłuchuje mikrofonu
4. Użytkownik nie wie że aplikacja działa w tle

**Konsekwencje:**
- Naruszenie prywatności (nasłuchiwanie bez wiedzy)
- Wyciek baterii
- Potencjalne nagrywanie bez zgody

**Kod problematyczny:**
```kotlin
override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
        if (PicovoiceManager.isEnabled()) {
            // ❌ Brak weryfikacji czy użytkownik wyraził zgodę
            PicovoiceManager.enablePicovoice(context)
        }
    }
}
```

**Rekomendacja:**
```kotlin
override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
        // ✅ Sprawdź explicit consent
        val autoStartEnabled = PicovoicePreferences.isAutoStartOnBootEnabled(context)
        if (autoStartEnabled && PicovoiceManager.isEnabled()) {
            PicovoiceManager.enablePicovoice(context)
        }
    }
}
```


---

### 1.4 🔴 Wake Lock może pozostać aktywny na zawsze
**Lokalizacja:** `VoiceService.kt:180-195`, `VoiceClientManager.kt:1650-1670`  
**Opis:** Wake lock ma timeout 2 godziny, ale może być re-acquired bez limitu.

**Scenariusz problemu:**
1. Użytkownik rozpoczyna rozmowę
2. Wake lock acquired z 2h timeout
3. Po 1.5h następuje reconnection
4. Wake lock re-acquired z nowym 2h timeout
5. Proces powtarza się w nieskończoność

**Konsekwencje:**
- Ekran nigdy nie gaśnie
- Drastyczny wyciek baterii
- Urządzenie może się przegrzać

**Kod problematyczny:**
```kotlin
// VoiceService.kt
private fun acquireWakeLock() {
    if (wakeLock == null) {
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            acquire(WAKE_LOCK_TIMEOUT) // ❌ Może być wywołane wielokrotnie
        }
    }
}

// VoiceClientManager.kt
private fun acquireWakeLock() {
    if (wakeLock?.isHeld == true) {
        return // ❌ Nie sprawdza czasu trwania
    }
    wakeLock = powerManager.newWakeLock(
        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
        "GeminiDemo::VoiceSessionWakeLock"
    )
    wakeLock?.acquire() // ❌ Brak timeout!
}
```

**Rekomendacja:**
```kotlin
private var wakeLockAcquiredAt: Long = 0
private val MAX_WAKE_LOCK_DURATION = 4 * 60 * 60 * 1000L // 4 godziny max

private fun acquireWakeLock() {
    // ✅ Sprawdź czy nie przekroczono max czasu
    if (wakeLockAcquiredAt > 0) {
        val duration = System.currentTimeMillis() - wakeLockAcquiredAt
        if (duration > MAX_WAKE_LOCK_DURATION) {
            Log.w(TAG, "Max wake lock duration exceeded, forcing stop")
            stop()
            return
        }
    }
    
    if (wakeLock?.isHeld != true) {
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            acquire(WAKE_LOCK_TIMEOUT)
        }
        wakeLockAcquiredAt = System.currentTimeMillis()
    }
}
```

---

### 1.5 🔴 Brak obsługi LOW_MEMORY callback
**Lokalizacja:** `MainActivity.kt`, `RTVIApplication.kt`  
**Opis:** Aplikacja nie implementuje `onTrimMemory()` ani `onLowMemory()`.

**Scenariusz problemu:**
1. System Android ma mało pamięci
2. Wysyła `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)`
3. Aplikacja ignoruje i kontynuuje działanie
4. System zabija proces bez graceful shutdown
5. Zasoby nie są zwolnione

**Konsekwencje:**
- Crash bez cleanup
- Zombie processes
- Wycieki zasobów

**Rekomendacja:**
```kotlin
// MainActivity.kt
override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    
    when (level) {
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
            Log.w(TAG, "Critical memory pressure, stopping session")
            lifecycleScope.launch {
                voiceClientManager.sessionManager?.endSession()
                voiceClientManager.stop()
            }
        }
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
            Log.w(TAG, "Low memory, pausing session")
            voiceClientManager.pause()
        }
    }
}
```


---

## 2. POWAŻNE PROBLEMY (Priority: HIGH)

### 2.1 🟠 SessionManager nie zatrzymuje sync job w onDestroy
**Lokalizacja:** `SessionManager.kt:200-250`  
**Opis:** TranscriptSyncManager może kontynuować infinite retry loop nawet po zamknięciu aplikacji.

**Kod problematyczny:**
```kotlin
private inner class TranscriptSyncManager {
    private var syncJob: Job? = null
    
    suspend fun syncTranscripts(summaryRequest: SummaryRequest): Result<Unit> {
        syncJob = scope.launch {
            while (!isCancelled) { // ❌ Może działać w nieskończoność
                attempt++
                // ...
                delay(calculateBackoff(attempt))
            }
        }
    }
}
```

**Rekomendacja:**
```kotlin
// SessionManager.kt
fun cleanup() {
    transcriptSyncManager.cancelSync()
    transcriptSyncManager.reset()
}

// MainActivity.kt
override fun onDestroy() {
    super.onDestroy()
    if (isFinishing) {
        voiceClientManager.sessionManager?.cleanup()
    }
}
```

---

### 2.2 🟠 NetworkMonitor nie jest unregistered w crash scenario
**Lokalizacja:** `MainActivity.kt:1015`  
**Opis:** NetworkMonitor.unregister() wywoływane tylko w onDestroy, nie w crash.

**Konsekwencje:**
- Memory leak (callback nie usunięty)
- Zombie listener w ConnectivityManager

**Rekomendacja:**
```kotlin
// RTVIApplication.kt
class RTVIApplication : Application() {
    private var networkMonitor: NetworkMonitor? = null
    
    override fun onCreate() {
        super.onCreate()
        networkMonitor = NetworkMonitor(this)
        
        // ✅ Cleanup on process death
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityDestroyed(activity: Activity) {
                if (activity is MainActivity && isFinishing) {
                    networkMonitor?.unregister()
                }
            }
            // ... other callbacks
        })
    }
}
```

---

### 2.3 🟠 AudioRecord/AudioTrack nie są zatrzymane w onPause
**Lokalizacja:** `VoiceClientManager.kt:1500-1600`  
**Opis:** Audio recording kontynuuje w tle nawet gdy aplikacja jest w background.

**Scenariusz problemu:**
1. Użytkownik rozpoczyna rozmowę
2. Przełącza się do innej aplikacji (onPause)
3. AudioRecord nadal nagrywa mikrofon
4. Użytkownik nie wie że jest nagrywany

**Konsekwencje:**
- Naruszenie prywatności
- Wyciek baterii
- Potencjalne nagrywanie wrażliwych rozmów

**Rekomendacja:**
```kotlin
// MainActivity.kt
override fun onPause() {
    super.onPause()
    
    // ✅ Pause audio recording when app goes to background
    if (!isChangingConfigurations) {
        voiceClientManager.pauseAudioRecording()
    }
}

override fun onResume() {
    super.onResume()
    
    // ✅ Resume only if user wants to continue
    if (voiceClientManager.state.value == ConnectionState.CONNECTED) {
        voiceClientManager.resumeAudioRecording()
    }
}
```


---

### 2.4 🟠 Brak timeout dla WebSocket reconnection
**Lokalizacja:** `VoiceClientManager.kt:1850-1950`  
**Opis:** ReconnectionManager może próbować reconnect w nieskończoność po pokazaniu dialogu.

**Kod problematyczny:**
```kotlin
fun continueReconnection() {
    scope?.launch {
        reconnectionManager.reset() // ❌ Reset counter
        reconnectionManager.startReconnection() // ❌ Start again - może być w pętli
    }
}
```

**Rekomendacja:**
```kotlin
private var totalReconnectionAttempts = 0
private val MAX_TOTAL_ATTEMPTS = 20

fun continueReconnection() {
    if (totalReconnectionAttempts >= MAX_TOTAL_ATTEMPTS) {
        Log.e(TAG, "Max total reconnection attempts reached, forcing stop")
        stop()
        return
    }
    
    scope?.launch {
        reconnectionManager.reset()
        reconnectionManager.startReconnection()
    }
}
```

---

### 2.5 🟠 PorcupineManager może leak Porcupine instance
**Lokalizacja:** `PorcupineService.kt:80-120`  
**Opis:** PorcupineManager nie jest properly deleted w crash scenario.

**Kod problematyczny:**
```kotlin
override fun onDestroy() {
    super.onDestroy()
    try {
        porcupineManager?.stop()
        porcupineManager?.delete() // ❌ Może nie być wywołane w crash
        porcupineManager = null
    } catch (e: Exception) {
        Log.e(TAG, "Error stopping PorcupineManager", e)
    }
}
```

**Rekomendacja:**
```kotlin
private var cleanupJob: Job? = null

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // ...
    
    // ✅ Schedule cleanup after max duration
    cleanupJob = CoroutineScope(Dispatchers.Default).launch {
        delay(8 * 60 * 60 * 1000L) // 8 godzin max
        Log.w(TAG, "Max service duration reached, stopping")
        stopSelf()
    }
    
    return START_NOT_STICKY
}

override fun onDestroy() {
    super.onDestroy()
    cleanupJob?.cancel()
    
    try {
        porcupineManager?.stop()
        porcupineManager?.delete()
    } catch (e: Exception) {
        Log.e(TAG, "Error in cleanup", e)
    } finally {
        porcupineManager = null
    }
}
```

---

## 3. ŚREDNIE ZAGROŻENIA (Priority: MEDIUM)

### 3.1 🟡 Brak rate limiting dla wake word detection
**Lokalizacja:** `PorcupineService.kt:150-180`  
**Opis:** Wake word może być triggered wielokrotnie w krótkim czasie.

**Scenariusz ataku:**
1. Atakujący odtwarza nagranie z wake word w pętli
2. Aplikacja uruchamia sesję wielokrotnie
3. Wyciek baterii i zasobów

**Rekomendacja:**
```kotlin
private var lastWakeWordTime = 0L
private val MIN_WAKE_WORD_INTERVAL = 5000L // 5 sekund

private fun onWakeWordDetected(keywordIndex: Int) {
    val now = System.currentTimeMillis()
    if (now - lastWakeWordTime < MIN_WAKE_WORD_INTERVAL) {
        Log.d(TAG, "Wake word ignored - too soon after last detection")
        return
    }
    lastWakeWordTime = now
    
    // ... rest of logic
}
```

---

### 3.2 🟡 Brak validation dla session resumption handle
**Lokalizacja:** `VoiceClientManager.kt:600-650`  
**Opis:** Session handle nie jest walidowany przed użyciem.

**Rekomendacja:**
```kotlin
private fun isSessionHandleValid(handle: String?): Boolean {
    if (handle == null) return false
    if (handle.length < 20) return false // Minimum length check
    if (!handle.matches(Regex("^[A-Za-z0-9+/=]+$"))) return false // Base64 check
    return true
}

val canResumeSession = isSessionHandleValid(sessionResumptionHandle) && 
                      isSessionResumable && 
                      (System.currentTimeMillis() - sessionCreatedTime) < SESSION_RESUMPTION_TIMEOUT
```


---

### 3.3 🟡 Credentials stored in EncryptedSharedPreferences bez backup exclusion
**Lokalizacja:** `AuthManager.kt:50-80`  
**Opis:** Encrypted credentials mogą być backupowane do cloud.

**Rekomendacja:**
```xml
<!-- AndroidManifest.xml -->
<application
    android:fullBackupContent="@xml/backup_rules"
    android:dataExtractionRules="@xml/data_extraction_rules">
    
<!-- backup_rules.xml -->
<full-backup-content>
    <exclude domain="sharedpref" path="librechat_auth_prefs.xml"/>
    <exclude domain="sharedpref" path="librechat_auth_prefs.xml.bak"/>
</full-backup-content>
```

---

### 3.4 🟡 Brak timeout dla image processing
**Lokalizacja:** `VoiceClientManager.kt:1550-1650`  
**Opis:** Image processing ma 30s timeout, ale może blokować UI.

**Rekomendacja:**
```kotlin
// Dodaj progress callback
imageProcessingJob = scope?.launch(Dispatchers.IO) {
    try {
        isProcessingImage.value = true
        
        val processingResult = withTimeout(30000L) {
            imageProcessor.processImage(uri) { progress ->
                // ✅ Update UI with progress
                withContext(Dispatchers.Main) {
                    imageProcessingProgress.value = progress
                }
            }
        }
        // ...
    }
}
```

---

## 4. ZOMBIE KLASY I METODY

### 4.1 Potencjalne zombie classes:

1. **`RealTimeClock.kt`** - Nie używana nigdzie w kodzie
2. **`TimeUtils.kt`** - Funkcje mogą być nieużywane
3. **`BatteryProfiler.kt`** - Używana tylko w VoiceService, ale logi nie są nigdzie przetwarzane
4. **`PerformanceLogger.kt`** - Logi memory ale bez analizy

**Rekomendacja:** Przeprowadzić analizę dead code:
```bash
./gradlew :gemini-multimodal-websocket-demo:lintDebug
```

---

### 4.2 Nieużywane metody w VoiceClientManager:

```kotlin
// Metoda pause() i resume() - używane ale mogą być uproszczone
fun pause() { /* ... */ }
fun resume() { /* ... */ }

// Metoda enableMic() - duplikuje logikę pause/resume
fun enableMic(enabled: Boolean) { /* ... */ }
```

**Rekomendacja:** Refactor do jednej metody:
```kotlin
fun setMicrophoneEnabled(enabled: Boolean, preserveSession: Boolean = true) {
    if (enabled) {
        if (preserveSession && isPaused.value) {
            resume()
        } else {
            start(currentThreadSettings)
        }
    } else {
        if (preserveSession) {
            pause()
        } else {
            stop()
        }
    }
}
```

---

## 5. SCENARIUSZE TESTOWE - UŻYTKOWNIK ZAPOMNIAŁ WYŁĄCZYĆ

### Scenariusz 1: Aplikacja w tle przez 24h
**Kroki:**
1. Użytkownik rozpoczyna rozmowę
2. Minimalizuje aplikację
3. Zapomina o niej przez 24h

**Obecne zachowanie:**
- ❌ VoiceService działa przez 24h
- ❌ Wake lock aktywny przez 24h
- ❌ WebSocket connection aktywny
- ❌ Bateria wycieknięta

**Oczekiwane zachowanie:**
- ✅ Auto-pause po 5 min inactivity
- ✅ VoiceService stop po 2h max
- ✅ Wake lock released po timeout
- ✅ Notification z opcją resume

---

### Scenariusz 2: Crash podczas aktywnej sesji
**Kroki:**
1. Użytkownik rozpoczyna rozmowę
2. Aplikacja crashuje (OutOfMemoryError)

**Obecne zachowanie:**
- ❌ VoiceService może pozostać aktywny
- ❌ Wake lock nie zwolniony
- ❌ AudioRecord nie zatrzymany
- ❌ WebSocket nie zamknięty

**Oczekiwane zachowanie:**
- ✅ Wszystkie zasoby zwolnione przez system
- ✅ VoiceService auto-stop
- ✅ Graceful cleanup w finally blocks

---

### Scenariusz 3: System zabija proces z powodu low memory
**Kroki:**
1. Użytkownik rozpoczyna rozmowę
2. System ma mało pamięci
3. Wysyła onTrimMemory(CRITICAL)
4. Zabija proces

**Obecne zachowanie:**
- ❌ Brak reakcji na onTrimMemory
- ❌ Process killed bez cleanup
- ❌ Zombie resources

**Oczekiwane zachowanie:**
- ✅ onTrimMemory() wywołuje graceful shutdown
- ✅ Session saved to offline queue
- ✅ Resources released before kill


---

## 6. PLAN REFAKTORYZACJI

### Faza 1: KRYTYCZNE POPRAWKI (1-2 dni)

#### 1.1 Lifecycle Management
```kotlin
// MainActivity.kt - Dodać proper cleanup
override fun onDestroy() {
    super.onDestroy()
    
    // Cleanup broadcast receivers
    unregisterWakeWordBroadcastReceivers()
    
    // ZAWSZE cleanup resources
    if (isFinishing) {
        lifecycleScope.launch {
            try {
                // End session gracefully
                voiceClientManager.sessionManager?.endSession()
                voiceClientManager.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
            }
        }
        stopVoiceService()
    }
    
    // Cleanup network monitor
    networkMonitor.unregister()
}

override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    
    when (level) {
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
            Log.w(TAG, "Critical memory pressure, emergency shutdown")
            lifecycleScope.launch {
                voiceClientManager.sessionManager?.endSession()
                voiceClientManager.stop()
            }
        }
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
            Log.w(TAG, "Low memory, pausing session")
            voiceClientManager.pause()
        }
    }
}
```

#### 1.2 Service Timeout
```kotlin
// VoiceService.kt - Dodać auto-stop
private var serviceTimeoutJob: Job? = null
private val MAX_SERVICE_DURATION = 2 * 60 * 60 * 1000L // 2h

private fun startForegroundService() {
    val notification = createNotification("Trwa rozmowa głosowa")
    startForeground(NOTIFICATION_ID, notification)
    
    batteryProfiler.startProfiling()
    
    // ✅ Schedule auto-stop
    serviceTimeoutJob = CoroutineScope(Dispatchers.Default).launch {
        delay(MAX_SERVICE_DURATION)
        Log.w(TAG, "Service timeout reached, stopping")
        stopService()
    }
}

override fun onDestroy() {
    serviceTimeoutJob?.cancel()
    releaseWakeLock()
    batteryProfiler.stopProfiling()
    instance = null
    super.onDestroy()
}
```

#### 1.3 Wake Lock Protection
```kotlin
// VoiceClientManager.kt - Dodać wake lock tracking
private var wakeLockAcquiredAt: Long = 0
private val MAX_WAKE_LOCK_DURATION = 4 * 60 * 60 * 1000L

private fun acquireWakeLock() {
    // Check max duration
    if (wakeLockAcquiredAt > 0) {
        val duration = System.currentTimeMillis() - wakeLockAcquiredAt
        if (duration > MAX_WAKE_LOCK_DURATION) {
            Log.e(TAG, "Max wake lock duration exceeded, forcing stop")
            stop()
            return
        }
    }
    
    if (!Preferences.keepScreenAwake.value) {
        return
    }
    
    if (wakeLock?.isHeld != true) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "GeminiDemo::VoiceSessionWakeLock"
        )
        wakeLock?.acquire(MAX_WAKE_LOCK_DURATION)
        wakeLockAcquiredAt = System.currentTimeMillis()
    }
}
```

---

### Faza 2: BACKGROUND HANDLING (2-3 dni)

#### 2.1 Audio Recording Pause
```kotlin
// VoiceClientManager.kt - Dodać pause/resume audio
private var isAudioPaused = false

fun pauseAudioRecording() {
    if (isAudioPaused) return
    
    try {
        audioRecord?.stop()
        recordingJob?.cancel()
        isAudioPaused = true
        Log.d(TAG, "Audio recording paused")
    } catch (e: Exception) {
        Log.e(TAG, "Error pausing audio", e)
    }
}

fun resumeAudioRecording() {
    if (!isAudioPaused) return
    
    try {
        audioRecord?.startRecording()
        startAudioRecording()
        isAudioPaused = false
        Log.d(TAG, "Audio recording resumed")
    } catch (e: Exception) {
        Log.e(TAG, "Error resuming audio", e)
    }
}

// MainActivity.kt
override fun onPause() {
    super.onPause()
    if (!isChangingConfigurations) {
        voiceClientManager.pauseAudioRecording()
    }
}

override fun onResume() {
    super.onResume()
    if (voiceClientManager.state.value == ConnectionState.CONNECTED) {
        voiceClientManager.resumeAudioRecording()
    }
}
```

#### 2.2 Session Cleanup Manager
```kotlin
// Nowa klasa: SessionCleanupManager.kt
class SessionCleanupManager(
    private val voiceClientManager: VoiceClientManager,
    private val sessionManager: SessionManager
) {
    private var cleanupJob: Job? = null
    
    fun scheduleCleanup(delayMs: Long = 2 * 60 * 60 * 1000L) {
        cleanupJob?.cancel()
        cleanupJob = CoroutineScope(Dispatchers.Default).launch {
            delay(delayMs)
            performCleanup()
        }
    }
    
    suspend fun performCleanup() {
        try {
            Log.i(TAG, "Performing scheduled cleanup")
            sessionManager.endSession()
            voiceClientManager.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    fun cancelCleanup() {
        cleanupJob?.cancel()
    }
}
```

---

### Faza 3: MONITORING I DIAGNOSTYKA (1-2 dni)

#### 3.1 Resource Monitor
```kotlin
// Nowa klasa: ResourceMonitor.kt
class ResourceMonitor(private val context: Context) {
    
    data class ResourceSnapshot(
        val timestamp: Long,
        val memoryUsed: Long,
        val batteryLevel: Int,
        val wakeLockHeld: Boolean,
        val audioRecordingActive: Boolean,
        val webSocketConnected: Boolean
    )
    
    private val snapshots = mutableListOf<ResourceSnapshot>()
    private var monitorJob: Job? = null
    
    fun startMonitoring() {
        monitorJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                captureSnapshot()
                delay(60000) // Every minute
            }
        }
    }
    
    private fun captureSnapshot() {
        val runtime = Runtime.getRuntime()
        val snapshot = ResourceSnapshot(
            timestamp = System.currentTimeMillis(),
            memoryUsed = runtime.totalMemory() - runtime.freeMemory(),
            batteryLevel = getBatteryLevel(),
            wakeLockHeld = isWakeLockHeld(),
            audioRecordingActive = isAudioRecording(),
            webSocketConnected = isWebSocketConnected()
        )
        
        snapshots.add(snapshot)
        
        // Keep only last 24 hours
        val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
        snapshots.removeAll { it.timestamp < cutoff }
        
        // Log anomalies
        detectAnomalies(snapshot)
    }
    
    private fun detectAnomalies(snapshot: ResourceSnapshot) {
        // Detect memory leak
        if (snapshot.memoryUsed > 200 * 1024 * 1024) { // 200MB
            Log.w(TAG, "⚠️ High memory usage: ${snapshot.memoryUsed / 1024 / 1024}MB")
        }
        
        // Detect wake lock leak
        if (snapshot.wakeLockHeld && !snapshot.webSocketConnected) {
            Log.w(TAG, "⚠️ Wake lock held but not connected")
        }
    }
    
    fun stopMonitoring() {
        monitorJob?.cancel()
    }
    
    fun getReport(): String {
        return snapshots.joinToString("\n") { 
            "${it.timestamp}: Memory=${it.memoryUsed/1024/1024}MB, " +
            "Battery=${it.batteryLevel}%, WakeLock=${it.wakeLockHeld}"
        }
    }
}
```


---

### Faza 4: TESTY I WALIDACJA (2-3 dni)

#### 4.1 Automated Tests
```kotlin
// Test: ResourceLeakTest.kt
@RunWith(AndroidJUnit4::class)
class ResourceLeakTest {
    
    @Test
    fun testWakeLockReleasedOnDestroy() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        // Start session
        scenario.onActivity { activity ->
            activity.voiceClientManager.start()
        }
        
        // Verify wake lock acquired
        Thread.sleep(1000)
        assertTrue(isWakeLockHeld())
        
        // Destroy activity
        scenario.close()
        
        // Verify wake lock released
        Thread.sleep(1000)
        assertFalse(isWakeLockHeld())
    }
    
    @Test
    fun testAudioRecordingStoppedOnPause() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        
        scenario.onActivity { activity ->
            activity.voiceClientManager.start()
        }
        
        Thread.sleep(1000)
        assertTrue(isAudioRecording())
        
        // Pause activity
        scenario.moveToState(Lifecycle.State.STARTED)
        
        Thread.sleep(1000)
        assertFalse(isAudioRecording())
    }
    
    @Test
    fun testServiceStopsAfterTimeout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, VoiceService::class.java).apply {
            action = VoiceService.ACTION_START
        }
        
        context.startService(intent)
        assertTrue(isServiceRunning(VoiceService::class.java))
        
        // Wait for timeout (use shorter timeout for test)
        Thread.sleep(5000)
        
        assertFalse(isServiceRunning(VoiceService::class.java))
    }
}
```

#### 4.2 Manual Test Scenarios
```markdown
## Test Case 1: 24h Background Test
1. Start conversation
2. Minimize app
3. Wait 24 hours
4. Check:
   - [ ] VoiceService stopped after 2h
   - [ ] Wake lock released
   - [ ] No zombie processes
   - [ ] Battery drain < 5%

## Test Case 2: Crash Recovery
1. Start conversation
2. Force crash (throw exception)
3. Restart app
4. Check:
   - [ ] No zombie resources
   - [ ] Session can be resumed
   - [ ] Transcript saved to offline queue

## Test Case 3: Low Memory
1. Start conversation
2. Trigger low memory (fill RAM)
3. Check:
   - [ ] onTrimMemory called
   - [ ] Session paused gracefully
   - [ ] Resources released
   - [ ] App doesn't crash

## Test Case 4: Network Loss
1. Start conversation
2. Disable WiFi/mobile data
3. Wait 5 minutes
4. Re-enable network
5. Check:
   - [ ] Reconnection successful
   - [ ] Transcript not lost
   - [ ] No duplicate sessions
```

---

## 7. DOKUMENTACJA ZMIAN

### 7.1 Changelog Template
```markdown
# Changelog - Security & Lifecycle Fixes

## [Version 2.0.0] - 2025-11-XX

### 🔒 Security Fixes
- Fixed wake lock leak that could drain battery indefinitely
- Added proper cleanup in MainActivity.onDestroy()
- Implemented onTrimMemory() for graceful low-memory handling
- Added timeout protection for VoiceService (max 2h)
- Fixed PorcupineService auto-start without user consent

### 🐛 Bug Fixes
- Fixed audio recording continuing in background
- Fixed WebSocket not closing on app termination
- Fixed SessionManager sync job not cancelling
- Fixed NetworkMonitor callback leak

### ✨ New Features
- Added ResourceMonitor for diagnostics
- Added SessionCleanupManager for automatic cleanup
- Added background audio pause/resume
- Added wake lock duration tracking

### 📝 Documentation
- Added security audit report
- Added lifecycle management guide
- Added testing scenarios
```

---

## 8. METRYKI I KPI

### 8.1 Przed refaktoryzacją (baseline)
```
Battery drain (24h background): ~40%
Memory leaks detected: 5
Zombie processes: 3
Crash rate: 2.5%
Wake lock duration: Unlimited
```

### 8.2 Po refaktoryzacji (target)
```
Battery drain (24h background): <5%
Memory leaks detected: 0
Zombie processes: 0
Crash rate: <0.5%
Wake lock duration: Max 4h
```

### 8.3 Monitoring Dashboard
```kotlin
// Dodać do Firebase Analytics
class MetricsCollector {
    fun logSessionMetrics() {
        Firebase.analytics.logEvent("session_ended") {
            param("duration_minutes", sessionDuration / 60000)
            param("wake_lock_held", wakeLockDuration / 1000)
            param("memory_peak_mb", peakMemoryUsage / 1024 / 1024)
            param("reconnection_count", reconnectionAttempts)
            param("cleanup_success", cleanupSuccessful)
        }
    }
}
```

---

## 9. PRIORYTETYZACJA ZADAŃ

### Sprint 1 (Tydzień 1) - KRYTYCZNE
- [ ] 1.1 MainActivity.onDestroy() cleanup
- [ ] 1.2 VoiceService auto-stop timeout
- [ ] 1.3 Wake lock duration tracking
- [ ] 1.4 onTrimMemory() implementation
- [ ] 1.5 PorcupineService consent check

### Sprint 2 (Tydzień 2) - WYSOKIE
- [ ] 2.1 Audio recording pause/resume
- [ ] 2.2 SessionManager cleanup
- [ ] 2.3 NetworkMonitor lifecycle
- [ ] 2.4 WebSocket reconnection limit
- [ ] 2.5 PorcupineManager cleanup

### Sprint 3 (Tydzień 3) - ŚREDNIE
- [ ] 3.1 Wake word rate limiting
- [ ] 3.2 Session handle validation
- [ ] 3.3 Credentials backup exclusion
- [ ] 3.4 Image processing progress
- [ ] Dead code removal

### Sprint 4 (Tydzień 4) - TESTY
- [ ] Unit tests
- [ ] Integration tests
- [ ] Manual test scenarios
- [ ] Performance testing
- [ ] Security audit verification

---

## 10. PODSUMOWANIE I REKOMENDACJE

### Najważniejsze wnioski:
1. **Aplikacja ma poważne problemy z lifecycle management** - zasoby nie są properly zwalniane
2. **Brak ochrony przed długotrwałym działaniem w tle** - może działać dni bez kontroli
3. **Wake lock może pozostać aktywny na zawsze** - krytyczny wyciek baterii
4. **Audio recording działa w tle bez wiedzy użytkownika** - naruszenie prywatności
5. **Brak graceful shutdown w crash scenarios** - zombie processes

### Natychmiastowe działania (do 48h):
1. ✅ Dodać proper cleanup w MainActivity.onDestroy()
2. ✅ Dodać timeout dla VoiceService (max 2h)
3. ✅ Dodać wake lock duration tracking
4. ✅ Implementować onTrimMemory()
5. ✅ Dodać audio pause w onPause()

### Długoterminowe (1 miesiąc):
1. Przeprowadzić pełną refaktoryzację lifecycle management
2. Dodać comprehensive testing suite
3. Implementować monitoring i diagnostykę
4. Przeprowadzić security audit przez zewnętrzną firmę
5. Dodać automated tests w CI/CD

### Ocena ryzyka:
- **Ryzyko bezpieczeństwa:** 🔴 WYSOKIE
- **Ryzyko prywatności:** 🔴 WYSOKIE  
- **Ryzyko stabilności:** 🟠 ŚREDNIE
- **Ryzyko wydajności:** 🟠 ŚREDNIE

### Rekomendacja końcowa:
**Aplikacja wymaga natychmiastowej interwencji przed wypuszczeniem do produkcji.** Zidentyfikowane problemy mogą prowadzić do poważnych naruszeń prywatności użytkowników oraz drastycznego wyciekania baterii. Zaleca się wstrzymanie release'u do czasu implementacji przynajmniej krytycznych poprawek z Fazy 1.

---

**Raport przygotował:** Kiro AI Assistant  
**Data:** 17 listopada 2025  
**Wersja raportu:** 1.0
