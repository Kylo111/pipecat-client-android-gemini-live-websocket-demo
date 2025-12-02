# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Source material - planned for /docs/testing/ (directory exists but is empty, planned for future phases)
**Current Documentation:** See /docs/testing/ for future documentation

---

# SCENARIUSZE TESTOWE - LIFECYCLE & RESOURCE MANAGEMENT

## OVERVIEW

Ten dokument zawiera szczegółowe scenariusze testowe do weryfikacji poprawności zarządzania cyklem życia i zasobami po refaktoryzacji.

---

## KATEGORIA 1: BACKGROUND SCENARIOS

### Test 1.1: Aplikacja w tle przez 24 godziny
**Cel:** Weryfikacja że aplikacja nie wyciekana baterii w tle

**Warunki wstępne:**
- Aplikacja zainstalowana
- Pełna bateria (100%)
- WiFi włączone

**Kroki:**
1. Uruchom aplikację
2. Zaloguj się do LibreChat
3. Rozpocznij rozmowę głosową
4. Minimalizuj aplikację (Home button)
5. Pozostaw urządzenie na 24h
6. Sprawdź poziom baterii
7. Otwórz aplikację

**Oczekiwany rezultat:**
- ✅ VoiceService automatycznie zatrzymany po 2h
- ✅ Wake lock zwolniony po 2h
- ✅ Bateria zużyta max 5%
- ✅ Aplikacja działa normalnie po otwarciu
- ✅ Brak zombie processes w logach
- ✅ Notification pokazuje "Sesja zakończona automatycznie"

**Weryfikacja:**
```bash
# Sprawdź logi
adb logcat | grep "VoiceService\|WakeLock\|timeout"

# Sprawdź wake locks
adb shell dumpsys power | grep -A 5 "Wake Locks"

# Sprawdź running services
adb shell dumpsys activity services | grep "VoiceService\|PorcupineService"
```

---

### Test 1.2: Przełączanie między aplikacjami
**Cel:** Weryfikacja że audio recording jest pauzowane w tle

**Kroki:**
1. Rozpocznij rozmowę
2. Przełącz się do innej aplikacji (np. Chrome)
3. Poczekaj 30 sekund
4. Wróć do aplikacji

**Oczekiwany rezultat:**
- ✅ Audio recording zatrzymane w kroku 2
- ✅ WebSocket connection aktywny
- ✅ Audio recording wznowione w kroku 4
- ✅ Rozmowa kontynuowana bez przerwy

**Weryfikacja:**
```bash
# Sprawdź audio recording status
adb logcat | grep "Audio recording\|pauseAudioRecording\|resumeAudioRecording"
```

---

### Test 1.3: Ekran wyłączony przez 1 godzinę
**Cel:** Weryfikacja auto-pause functionality

**Kroki:**
1. Rozpocznij rozmowę
2. Wyłącz ekran (Power button)
3. Poczekaj 1 godzinę
4. Włącz ekran

**Oczekiwany rezultat:**
- ✅ Auto-pause triggered po 5 min inactivity
- ✅ Session paused (nie disconnected)
- ✅ Wake lock zwolniony
- ✅ Możliwość resume po włączeniu ekranu
- ✅ Bateria zużyta < 2%

---

## KATEGORIA 2: CRASH & RECOVERY SCENARIOS

### Test 2.1: Force stop aplikacji
**Cel:** Weryfikacja cleanup przy force stop

**Kroki:**
1. Rozpocznij rozmowę
2. Otwórz Settings → Apps → Gemini Demo
3. Kliknij "Force Stop"
4. Sprawdź system resources
5. Uruchom aplikację ponownie

**Oczekiwany rezultat:**
- ✅ Wszystkie zasoby zwolnione
- ✅ Wake lock released
- ✅ Audio recording stopped
- ✅ WebSocket closed
- ✅ VoiceService stopped
- ✅ Brak zombie processes
- ✅ Aplikacja startuje normalnie

**Weryfikacja:**
```bash
# Przed force stop
adb shell dumpsys power | grep "Wake Locks" > before.txt
adb shell dumpsys audio | grep "AudioRecord" >> before.txt

# Force stop
adb shell am force-stop ai.pipecat.gemini_multimodal_websocket_demo

# Po force stop
adb shell dumpsys power | grep "Wake Locks" > after.txt
adb shell dumpsys audio | grep "AudioRecord" >> after.txt

# Porównaj
diff before.txt after.txt
```

---

### Test 2.2: OutOfMemoryError podczas sesji
**Cel:** Weryfikacja graceful handling przy OOM

**Kroki:**
1. Rozpocznij rozmowę
2. Symuluj OOM (wypełnij pamięć)
3. Poczekaj na crash
4. Sprawdź logi
5. Uruchom aplikację

**Oczekiwany rezultat:**
- ✅ onTrimMemory() wywołane przed crash
- ✅ Emergency cleanup wykonany
- ✅ Session saved to offline queue
- ✅ Transcript nie stracony
- ✅ Aplikacja startuje normalnie

**Symulacja OOM:**
```kotlin
// Dodaj do MainActivity dla testu
fun simulateOOM() {
    val list = mutableListOf<ByteArray>()
    try {
        while (true) {
            list.add(ByteArray(1024 * 1024)) // 1MB
        }
    } catch (e: OutOfMemoryError) {
        Log.e(TAG, "OOM triggered", e)
    }
}
```

---

### Test 2.3: System zabija proces (low memory)
**Cel:** Weryfikacja że system może cleanly zabić proces

**Kroki:**
1. Rozpocznij rozmowę
2. Otwórz wiele innych aplikacji (Chrome, YouTube, etc.)
3. Poczekaj aż system zabije proces
4. Sprawdź logi
5. Uruchom aplikację

**Oczekiwany rezultat:**
- ✅ onTrimMemory(CRITICAL) wywołane
- ✅ Emergency shutdown wykonany
- ✅ Session saved
- ✅ Brak zombie resources
- ✅ Aplikacja startuje normalnie

**Weryfikacja:**
```bash
# Monitoruj memory pressure
adb shell dumpsys meminfo ai.pipecat.gemini_multimodal_websocket_demo

# Sprawdź logi
adb logcat | grep "onTrimMemory\|Emergency\|CRITICAL"
```

---

## KATEGORIA 3: NETWORK SCENARIOS

### Test 3.1: Utrata połączenia podczas rozmowy
**Cel:** Weryfikacja reconnection logic

**Kroki:**
1. Rozpocznij rozmowę
2. Wyłącz WiFi i mobile data
3. Poczekaj 2 minuty
4. Włącz WiFi
5. Poczekaj na reconnection

**Oczekiwany rezultat:**
- ✅ Reconnection attempts started
- ✅ Max 5 attempts z exponential backoff
- ✅ Dialog pokazany po 5 attempts
- ✅ Możliwość continue lub end
- ✅ Session resumed po reconnection
- ✅ Transcript nie stracony

---

### Test 3.2: Słabe połączenie (packet loss)
**Cel:** Weryfikacja stabilności przy złym połączeniu

**Kroki:**
1. Rozpocznij rozmowę
2. Symuluj packet loss (50%)
3. Kontynuuj rozmowę przez 5 minut
4. Przywróć normalne połączenie

**Oczekiwany rezultat:**
- ✅ WebSocket reconnects automatycznie
- ✅ Audio quality degraded ale działa
- ✅ Brak crash
- ✅ Transcript complete

**Symulacja packet loss:**
```bash
# Na rooted device
adb shell
tc qdisc add dev wlan0 root netem loss 50%

# Przywróć
tc qdisc del dev wlan0 root
```

---

## KATEGORIA 4: WAKE WORD SCENARIOS

### Test 4.1: Wake word spam attack
**Cel:** Weryfikacja rate limiting

**Kroki:**
1. Włącz Picovoice
2. Odtwórz nagranie z wake word w pętli (10x w 10 sekund)
3. Sprawdź logi

**Oczekiwany rezultat:**
- ✅ Tylko pierwsze detection processed
- ✅ Kolejne ignored przez 5 sekund
- ✅ Brak multiple sessions
- ✅ Brak battery drain

**Weryfikacja:**
```bash
adb logcat | grep "Wake word\|rate limit\|ignored"
```

---

### Test 4.2: PorcupineService przez 24h
**Cel:** Weryfikacja że service nie działa w nieskończoność

**Kroki:**
1. Włącz Picovoice
2. Restart urządzenia (auto-start)
3. Poczekaj 24h
4. Sprawdź status

**Oczekiwany rezultat:**
- ✅ Service stopped po 8h
- ✅ Notification pokazuje timeout
- ✅ Bateria zużyta < 10%
- ✅ Możliwość manual restart

---

## KATEGORIA 5: PERMISSION SCENARIOS

### Test 5.1: Odwołanie permission podczas sesji
**Cel:** Weryfikacja graceful handling przy utracie permissions

**Kroki:**
1. Rozpocznij rozmowę
2. Otwórz Settings → Apps → Permissions
3. Odwołaj RECORD_AUDIO permission
4. Wróć do aplikacji

**Oczekiwany rezultat:**
- ✅ Session paused automatycznie
- ✅ Error message pokazany
- ✅ Możliwość grant permission i resume
- ✅ Brak crash

---

## KATEGORIA 6: EDGE CASES

### Test 6.1: Bardzo długa sesja (4h+)
**Cel:** Weryfikacja max duration limits

**Kroki:**
1. Rozpocznij rozmowę
2. Pozostaw aktywną przez 4h
3. Sprawdź co się dzieje

**Oczekiwany rezultat:**
- ✅ VoiceService stopped po 2h
- ✅ Wake lock released po 4h max
- ✅ Session może być resumed
- ✅ Transcript saved

---

### Test 6.2: Multiple rapid start/stop
**Cel:** Weryfikacja że nie ma race conditions

**Kroki:**
1. Start session
2. Natychmiast stop
3. Natychmiast start
4. Powtórz 10x

**Oczekiwany rezultat:**
- ✅ Brak crash
- ✅ Brak zombie resources
- ✅ Każdy start/stop clean
- ✅ Brak memory leaks

---

### Test 6.3: Configuration change podczas sesji
**Cel:** Weryfikacja że rotation nie przerywa sesji

**Kroki:**
1. Rozpocznij rozmowę
2. Obróć urządzenie (portrait → landscape)
3. Obróć z powrotem
4. Kontynuuj rozmowę

**Oczekiwany rezultat:**
- ✅ Session kontynuowana
- ✅ Audio nie przerwane
- ✅ UI odtworzone poprawnie
- ✅ Brak memory leaks

---

## AUTOMATED TEST SUITE

### Unit Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class ResourceManagementTest {
    
    @Test
    fun testWakeLockReleasedOnDestroy() {
        // Test implementation
    }
    
    @Test
    fun testAudioRecordingPausedOnBackground() {
        // Test implementation
    }
    
    @Test
    fun testServiceStopsAfterTimeout() {
        // Test implementation
    }
    
    @Test
    fun testEmergencyCleanupOnLowMemory() {
        // Test implementation
    }
    
    @Test
    fun testSessionSavedOnCrash() {
        // Test implementation
    }
}
```

### Integration Tests

```kotlin
@RunWith(AndroidJUnit4::class)
@LargeTest
class LifecycleIntegrationTest {
    
    @Test
    fun testFullSessionLifecycle() {
        // Start → Pause → Resume → Stop
    }
    
    @Test
    fun testBackgroundBehavior() {
        // Background → Foreground → Background
    }
    
    @Test
    fun testNetworkLossRecovery() {
        // Connected → Disconnected → Reconnected
    }
}
```

---

## PERFORMANCE BENCHMARKS

### Baseline Metrics (przed refaktoryzacją)

```
Battery drain (24h background): ~40%
Memory usage (active session): ~150MB
Wake lock duration: Unlimited
Crash rate: 2.5%
Zombie processes: 3 per session
```

### Target Metrics (po refaktoryzacji)

```
Battery drain (24h background): <5%
Memory usage (active session): <100MB
Wake lock duration: Max 4h
Crash rate: <0.5%
Zombie processes: 0
```

### Monitoring Commands

```bash
# Battery stats
adb shell dumpsys batterystats --reset
# ... run tests ...
adb shell dumpsys batterystats > battery_stats.txt

# Memory profiling
adb shell dumpsys meminfo ai.pipecat.gemini_multimodal_websocket_demo

# CPU usage
adb shell top -n 1 | grep gemini

# Wake locks
adb shell dumpsys power | grep "Wake Locks"

# Services
adb shell dumpsys activity services | grep "VoiceService\|PorcupineService"
```

---

## TEST EXECUTION CHECKLIST

### Pre-Release Testing

- [ ] Wszystkie unit tests pass
- [ ] Wszystkie integration tests pass
- [ ] Manual test scenarios completed
- [ ] Performance benchmarks met
- [ ] No memory leaks detected
- [ ] No zombie processes
- [ ] Battery drain < 5% (24h background)
- [ ] Crash rate < 0.5%
- [ ] Security audit passed

### Regression Testing

- [ ] Existing functionality works
- [ ] No new crashes introduced
- [ ] Performance not degraded
- [ ] UI responsive
- [ ] Audio quality maintained

---

## BUG REPORTING TEMPLATE

```markdown
## Bug Report

**Test Case:** [Test ID]
**Expected:** [Expected behavior]
**Actual:** [Actual behavior]
**Severity:** [Critical/High/Medium/Low]

**Steps to Reproduce:**
1. 
2. 
3. 

**Logs:**
```
[Paste relevant logs]
```

**Screenshots:**
[Attach screenshots]

**Device Info:**
- Model: 
- Android version: 
- App version: 
```

---

**Dokument przygotował:** Kiro AI Assistant  
**Data:** 17 listopada 2025  
**Wersja:** 1.0
