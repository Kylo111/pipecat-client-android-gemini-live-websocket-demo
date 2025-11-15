# Raport Stabilności Połączenia - Gemini Multimodal WebSocket Demo

**Data:** 14 listopada 2025  
**Wersja aplikacji:** 1.0  
**Autor analizy:** Kiro AI Assistant

---

## 1. PODSUMOWANIE WYKONAWCZE

### Status ogólny: ⚠️ WYMAGA UWAGI

Aplikacja wykorzystuje bezpośrednie połączenie WebSocket z Gemini Live API (v1beta) i działa poprawnie w podstawowych scenariuszach. Jednak analiza kodu ujawniła **kilka krytycznych problemów** związanych ze stabilnością połączenia, obsługą błędów i zarządzaniem zasobami.

### Kluczowe zagrożenia:
1. 🔴 **KRYTYCZNE:** Brak automatycznego reconnect przy utracie połączenia
2. 🔴 **KRYTYCZNE:** Potencjalne wycieki pamięci przy nieprawidłowym zamknięciu
3. 🟡 **WYSOKIE:** Niewystarczająca obsługa timeoutów WebSocket
4. 🟡 **WYSOKIE:** Problemy z wysyłaniem obrazów przy słabym połączeniu
5. 🟡 **ŚREDNIE:** Brak walidacji rozmiaru obrazów przed wysłaniem

---

## 2. ANALIZA POŁĄCZENIA WEBSOCKET

### 2.1 Konfiguracja klienta OkHttp

**Lokalizacja:** `VoiceClientManager.kt:159-165`

```kotlin
private val client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.SECONDS)  // ⚠️ PROBLEM
    .writeTimeout(10, TimeUnit.SECONDS)
    .pingInterval(20, TimeUnit.SECONDS)
    .build()
```

#### Problemy zidentyfikowane:

**🔴 KRYTYCZNY: readTimeout = 0**
- **Opis:** Timeout odczytu ustawiony na 0 oznacza brak limitu czasowego
- **Ryzyko:** Połączenie może zawiesić się na nieskończoność przy braku odpowiedzi od serwera
- **Scenariusz:** Utrata połączenia sieciowego bez zamknięcia socketu → aplikacja czeka w nieskończoność
- **Rekomendacja:** Ustawić `readTimeout(60, TimeUnit.SECONDS)` lub więcej

**🟡 ŚREDNI: pingInterval = 20s**
- **Opis:** Ping co 20 sekund może być za rzadki dla niestabilnych połączeń mobilnych
- **Ryzyko:** Opóźnione wykrycie utraty połączenia (do 20s)
- **Rekomendacja:** Rozważyć zmniejszenie do 10-15 sekund dla lepszej responsywności

**🟡 ŚREDNI: Brak retry policy dla połączenia**
- **Opis:** Brak automatycznego ponownego łączenia przy błędzie
- **Ryzyko:** Użytkownik musi ręcznie reconnectować po każdym błędzie sieci
- **Rekomendacja:** Implementacja exponential backoff retry

### 2.2 Obsługa błędów WebSocket

**Lokalizacja:** `VoiceClientManager.kt:398-413`

```kotlin
override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
    Log.e(TAG, "WebSocket failure: ${t.message}", t)
    
    // Ignore AudioTrack errors
    val isAudioTrackError = t.message?.contains("AudioTrack") == true
    if (!isAudioTrackError) {
        errors.add(Error("Connection failed: ${t.message}"))
    }
    
    handleDisconnect()
}
```

#### Problemy:

**🔴 KRYTYCZNY: Brak rozróżnienia typów błędów**
- Wszystkie błędy traktowane jednakowo
- Brak retry dla błędów przejściowych (network timeout, DNS failure)
- Brak różnicowania błędów recoverable vs non-recoverable

**Rekomendowane typy błędów do obsługi:**
```kotlin
when (t) {
    is SocketTimeoutException -> // Retry z backoff
    is UnknownHostException -> // Sprawdź połączenie, retry
    is SSLException -> // Błąd certyfikatu, nie retry
    is IOException -> // Błąd I/O, retry
    else -> // Nieznany błąd, loguj i informuj użytkownika
}
```

### 2.3 Lifecycle połączenia

**Problem: Brak automatycznego reconnect**

Obecna implementacja:
- ✅ Poprawne zamykanie przy `stop()`
- ✅ Cleanup zasobów w `handleDisconnect()`
- ❌ Brak automatycznego reconnect
- ❌ Brak queue dla wiadomości podczas reconnect

**Scenariusze problematyczne:**

1. **Utrata WiFi podczas rozmowy:**
   - Połączenie się zrywa
   - Użytkownik musi ręcznie kliknąć "Connect" ponownie
   - Transkrypcje z okresu rozłączenia są tracone

2. **Przełączenie WiFi ↔ Mobile Data:**
   - Android zmienia interfejs sieciowy
   - WebSocket nie wykrywa zmiany natychmiast
   - Może trwać do 20s (ping interval) zanim błąd zostanie wykryty

3. **Słabe połączenie z przerwami:**
   - Częste reconnect → frustracja użytkownika
   - Brak informacji o jakości połączenia

---

## 3. ZARZĄDZANIE AUDIO

### 3.1 AudioRecord - Nagrywanie

**Lokalizacja:** `VoiceClientManager.kt:617-680`

#### Problemy:

**🟡 ŚREDNI: Brak obsługi błędów AudioRecord**
```kotlin
val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0

if (read > 0) {
    // Przetwarzanie...
}
```

**Brakujące sprawdzenia:**
- `AudioRecord.ERROR_INVALID_OPERATION` (-3)
- `AudioRecord.ERROR_BAD_VALUE` (-2)
- `AudioRecord.ERROR_DEAD_OBJECT` (-6)
- `AudioRecord.ERROR` (-1)

**Rekomendacja:**
```kotlin
when {
    read > 0 -> // Przetwarzanie
    read == AudioRecord.ERROR_INVALID_OPERATION -> {
        Log.e(TAG, "AudioRecord invalid operation")
        // Restart AudioRecord
    }
    read == AudioRecord.ERROR_DEAD_OBJECT -> {
        Log.e(TAG, "AudioRecord dead object")
        // Recreate AudioRecord
    }
    else -> Log.w(TAG, "AudioRecord error: $read")
}
```

**🟡 ŚREDNI: Brak throttling przy wysyłaniu audio**
```kotlin
delay(adjustedDelay) // Adjusted delay based on speech speed
```

- Delay bazuje tylko na `speechSpeed`
- Brak adaptacji do przepustowości sieci
- Może powodować przeciążenie przy wolnym połączeniu

### 3.2 AudioTrack - Odtwarzanie

**Lokalizacja:** `VoiceClientManager.kt:684-705`

**🟡 NISKI: Używanie deprecated API**
```kotlin
audioTrack = AudioTrack(
    AudioManager.STREAM_VOICE_CALL,  // Deprecated
    OUTPUT_SAMPLE_RATE,
    OUTPUT_CHANNEL_CONFIG,
    AUDIO_FORMAT,
    bufferSize,
    AudioTrack.MODE_STREAM
)
```

**Rekomendacja:** Migracja do `AudioTrack.Builder`:
```kotlin
audioTrack = AudioTrack.Builder()
    .setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    )
    .setAudioFormat(
        AudioFormat.Builder()
            .setSampleRate(OUTPUT_SAMPLE_RATE)
            .setChannelMask(OUTPUT_CHANNEL_CONFIG)
            .setEncoding(AUDIO_FORMAT)
            .build()
    )
    .setBufferSizeInBytes(bufferSize)
    .setTransferMode(AudioTrack.MODE_STREAM)
    .build()
```

**🔴 KRYTYCZNY: Brak synchronizacji przy zapisie do AudioTrack**
```kotlin
audioTrack?.write(boostedAudio, 0, boostedAudio.size)
```

- Metoda `write()` może być wywoływana z wielu wątków
- Brak synchronizacji może prowadzić do race conditions
- Potencjalne zniekształcenia audio lub crashes

---

## 4. WYSYŁANIE OBRAZÓW

### 4.1 Implementacja sendImage()

**Lokalizacja:** `VoiceClientManager.kt:857-906`

#### Problemy zidentyfikowane:

**🔴 WYSOKI: Brak walidacji rozmiaru obrazu**
```kotlin
val imageBytes = inputStream.use { it.readBytes() }
val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
```

**Ryzyka:**
- Brak limitu rozmiaru → możliwe OutOfMemoryError
- Duże obrazy (>10MB) mogą zablokować WebSocket
- Base64 encoding zwiększa rozmiar o ~33%
- Brak kompresji przed wysłaniem

**Rekomendowane limity:**
- Max rozmiar surowego obrazu: 5MB
- Max rozmiar po Base64: 7MB
- Automatyczna kompresja dla większych plików

**Przykładowa implementacja:**
```kotlin
fun sendImage(uri: Uri) {
    if (state.value != ConnectionState.CONNECTED) {
        errors.add(Error("Not connected"))
        return
    }

    scope?.launch(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open image")
            
            var imageBytes = inputStream.use { it.readBytes() }
            
            // Walidacja rozmiaru
            val maxSize = 5 * 1024 * 1024 // 5MB
            if (imageBytes.size > maxSize) {
                // Kompresja
                imageBytes = compressImage(imageBytes, maxSize)
            }
            
            val mimeType = getMimeType(uri)
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            
            // Sprawdź rozmiar po Base64
            if (base64Image.length > 7 * 1024 * 1024) {
                withContext(Dispatchers.Main) {
                    errors.add(Error("Image too large after encoding"))
                }
                return@launch
            }
            
            // Wysyłanie...
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory while processing image", e)
            withContext(Dispatchers.Main) {
                errors.add(Error("Image too large for device memory"))
            }
        }
    }
}
```

**🟡 ŚREDNI: Brak timeout dla wysyłania**
- Wysyłanie dużego obrazu może trwać długo
- Brak informacji o postępie dla użytkownika
- Brak możliwości anulowania

**🟡 ŚREDNI: Wysyłanie na głównym wątku**
```kotlin
webSocket?.send(messageJson)
```
- Operacja I/O na wątku wywołującym
- Powinno być w coroutine z Dispatchers.IO

### 4.2 Obsługa błędów przy wysyłaniu obrazów

**Brakujące scenariusze:**
- Timeout podczas wysyłania
- Utrata połączenia w trakcie wysyłania
- Błąd parsowania obrazu
- Nieobsługiwany format (HEIC, WebP)

---

## 5. TIMEOUTY I SESJE

### 5.1 Session Timeout

**Lokalizacja:** `VoiceClientManager.kt:195-227`

```kotlin
private fun startIdleMonitoring() {
    val timeoutMinutes = Preferences.sessionTimeoutMinutes.value
    if (timeoutMinutes <= 0) {
        Log.i(TAG, "Session timeout disabled")
        return
    }
    
    val timeoutMillis = timeoutMinutes * 60 * 1000L
    
    idleCheckJob = scope?.launch {
        while (isActive && state.value == ConnectionState.CONNECTED) {
            delay(10000) // Check every 10 seconds
            
            val idleTime = System.currentTimeMillis() - lastActivityTime
            if (idleTime >= timeoutMillis) {
                stop()
                onSessionTimeout?.invoke()
                break
            }
        }
    }
}
```

#### Ocena: ✅ Dobrze zaimplementowane

**Plusy:**
- Sprawdzanie co 10s jest rozsądne
- Callback dla UI
- Możliwość wyłączenia

**Drobne uwagi:**
- Brak ostrzeżenia przed timeout (np. "1 minuta do końca sesji")
- Brak możliwości przedłużenia sesji przez użytkownika

### 5.2 Network Timeouts

**Problem: Brak timeout dla operacji sieciowych LibreChat**

**Lokalizacja:** `LibreChatService.kt:48-60`

```kotlin
private val httpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS)
    // ...
```

**Ocena:** ✅ Poprawnie skonfigurowane

Ale:
- Endpoint `/api/learning/context` ma custom timeout 30s (dobry)
- Brak timeout dla retry policy

---

## 6. ZARZĄDZANIE PAMIĘCIĄ I ZASOBAMI

### 6.1 Potencjalne wycieki pamięci

**🔴 KRYTYCZNY: Coroutine scope nie zawsze jest czyszczony**

**Lokalizacja:** `VoiceClientManager.kt:813-826`

```kotlin
private fun handleDisconnect() {
    // ...
    scope?.cancel()
    scope = null
    // ...
}
```

**Problem:**
- Jeśli `handleDisconnect()` nie zostanie wywołany (crash, force kill), scope pozostaje aktywny
- `recordingJob` może nadal działać w tle
- AudioRecord/AudioTrack mogą nie zostać zwolnione

**Rekomendacja:**
- Użycie `CoroutineScope` z `SupervisorJob` i lifecycle-aware scope
- Implementacja `onCleared()` lub podobnego mechanizmu cleanup

**🟡 ŚREDNI: Wake lock może nie zostać zwolniony**

```kotlin
private fun releaseWakeLock() {
    try {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    } catch (e: Exception) {
        Log.e(TAG, "Failed to release wake lock", e)
    }
}
```

**Scenariusz problematyczny:**
- Crash aplikacji podczas aktywnego połączenia
- Wake lock pozostaje aktywny
- Bateria się wyczerpuje

**Rekomendacja:**
- Użycie `WakeLock.acquire(timeout)` z automatycznym timeout
- Monitoring w `onDestroy()` Activity

### 6.2 Transkrypcje - limit pamięci

**Lokalizacja:** `SessionManager.kt:169-179`

```kotlin
private fun enforceTranscriptLimit(session: SessionContext) {
    if (session.transcripts.size > MAX_TRANSCRIPTS) {
        val toRemove = session.transcripts.size - MAX_TRANSCRIPTS
        repeat(toRemove) {
            session.transcripts.removeAt(0)
        }
    }
}
```

**Ocena:** ✅ Dobrze zaimplementowane

- Limit 10,000 transkrypcji jest rozsądny
- FIFO usuwanie najstarszych

**Potencjalny problem:**
- Długie transkrypcje mogą zająć dużo pamięci
- Brak limitu na długość pojedynczej transkrypcji

---

## 7. OBSŁUGA OFFLINE I RETRY

### 7.1 Offline Summary Queue

**Lokalizacja:** `OfflineSummaryQueue.kt`

**Ocena:** ✅ Bardzo dobrze zaimplementowane

**Plusy:**
- FIFO queue z limitem 10 elementów
- Persystencja w SharedPreferences
- Automatyczne przetwarzanie przy reconnect
- Obsługa błędów parsowania

**Drobne uwagi:**
- SharedPreferences może nie być najlepsze dla dużych danych
- Rozważyć Room Database dla większej niezawodności

### 7.2 Retry Policy

**Lokalizacja:** `RetryPolicy.kt`

```kotlin
suspend fun <T> withRetry(
    maxAttempts: Int = 3,
    initialDelay: Long = 1000,
    maxDelay: Long = 10000,
    factor: Double = 2.0,
    block: suspend () -> T
): Result<T>
```

**Ocena:** ✅ Dobrze zaimplementowane

**Exponential backoff:**
- 1s → 2s → 4s (max 10s)
- 3 próby domyślnie

**Brakujące:**
- Jitter (losowe opóźnienie) dla uniknięcia thundering herd
- Możliwość anulowania retry

---

## 8. NETWORK MONITORING

**Lokalizacja:** `NetworkMonitor.kt`

**Ocena:** ✅ Bardzo dobrze zaimplementowane

```kotlin
private val networkCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
        val wasDisconnected = !_isConnected.value
        _isConnected.value = true
        
        if (wasDisconnected) {
            _onNetworkReconnected.value = System.currentTimeMillis()
        }
    }
    
    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        // ...
    }
}
```

**Plusy:**
- Wykrywanie zmian połączenia
- Trigger dla offline queue processing
- StateFlow dla reaktywności

**Potencjalne ulepszenia:**
- Wykrywanie typu połączenia (WiFi vs Mobile)
- Monitoring jakości połączenia (bandwidth, latency)
- Ostrzeżenie użytkownika o słabym połączeniu

---

## 9. PROBLEMY SPECYFICZNE DLA JĘZYKA POLSKIEGO

### 9.1 Transkrypcja

**Obecna implementacja:**
- Gemini Live API obsługuje język polski natywnie
- Brak lokalnego rozpoznawania mowy (Android SpeechRecognizer został usunięty)

**Potencjalne problemy:**
- Opóźnienie w transkrypcji przy słabym połączeniu
- Brak offline fallback
- Błędy w transkrypcji polskich znaków diakrytycznych

### 9.2 System Prompt

**Lokalizacja:** `LibreChatService.kt:234-250`

```kotlin
val fullSystemPrompt = contextResponse.systemPrompt
    .replace("{memory}", memoryContext)
    .plus(recentContext)
```

**Uwagi:**
- System prompt może zawierać polskie znaki
- Brak walidacji encoding (UTF-8)
- Potencjalne problemy z emoji w promptach

---

## 10. REKOMENDACJE PRIORYTETOWE

### 🔴 KRYTYCZNE (do naprawy natychmiast)

1. **Dodać readTimeout dla WebSocket**
   ```kotlin
   .readTimeout(60, TimeUnit.SECONDS)
   ```

2. **Implementować automatyczny reconnect**
   ```kotlin
   private fun attemptReconnect() {
       if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
           val delay = calculateBackoff(reconnectAttempts)
           scope?.launch {
               delay(delay)
               start()
           }
       }
   }
   ```

3. **Dodać walidację rozmiaru obrazów**
   ```kotlin
   if (imageBytes.size > MAX_IMAGE_SIZE) {
       imageBytes = compressImage(imageBytes)
   }
   ```

4. **Synchronizować dostęp do AudioTrack**
   ```kotlin
   private val audioTrackLock = Any()
   
   synchronized(audioTrackLock) {
       audioTrack?.write(boostedAudio, 0, boostedAudio.size)
   }
   ```

### 🟡 WYSOKIE (do naprawy w najbliższym czasie)

5. **Rozróżniać typy błędów WebSocket**
   - Implementować retry dla błędów przejściowych
   - Informować użytkownika o błędach permanentnych

6. **Dodać obsługę błędów AudioRecord**
   - Restart przy ERROR_DEAD_OBJECT
   - Recreate przy ERROR_INVALID_OPERATION

7. **Migrować AudioTrack do nowego API**
   - Użyć AudioTrack.Builder
   - Lepsze zarządzanie audio focus

8. **Dodać timeout dla wysyłania obrazów**
   - Progress indicator dla użytkownika
   - Możliwość anulowania

### 🟢 ŚREDNIE (nice to have)

9. **Dodać monitoring jakości połączenia**
   - Wykrywanie bandwidth
   - Adaptacja quality audio do połączenia

10. **Implementować ostrzeżenie przed session timeout**
    - Notification 1 minutę przed końcem
    - Możliwość przedłużenia sesji

11. **Dodać jitter do retry policy**
    ```kotlin
    val jitter = Random.nextLong(0, 1000)
    delay(currentDelay + jitter)
    ```

12. **Migrować offline queue do Room Database**
    - Lepsza wydajność
    - Większa niezawodność

---

## 11. SCENARIUSZE TESTOWE

### Test 1: Utrata połączenia podczas rozmowy
**Kroki:**
1. Rozpocznij rozmowę głosową
2. Wyłącz WiFi
3. Obserwuj zachowanie aplikacji

**Oczekiwane:**
- Wykrycie utraty połączenia w <20s
- Automatyczny reconnect po przywróceniu WiFi
- Informacja dla użytkownika o statusie

**Obecne zachowanie:**
- ❌ Brak automatycznego reconnect
- ❌ Użytkownik musi ręcznie reconnectować

### Test 2: Wysyłanie dużego obrazu (>10MB)
**Kroki:**
1. Połącz się z Gemini
2. Wyślij obraz >10MB

**Oczekiwane:**
- Kompresja obrazu przed wysłaniem
- Progress indicator
- Timeout po 30s

**Obecne zachowanie:**
- ⚠️ Możliwy OutOfMemoryError
- ⚠️ Brak progress indicator
- ⚠️ Brak timeout

### Test 3: Długa sesja (>1h)
**Kroki:**
1. Rozpocznij rozmowę
2. Pozostaw aktywną przez >1h
3. Obserwuj zużycie pamięci i baterii

**Oczekiwane:**
- Stabilne zużycie pamięci
- Wake lock aktywny
- Brak memory leaks

**Obecne zachowanie:**
- ✅ Session timeout działa
- ⚠️ Potencjalne memory leaks przy crash

### Test 4: Przełączanie WiFi ↔ Mobile Data
**Kroki:**
1. Rozpocznij rozmowę na WiFi
2. Przełącz na Mobile Data
3. Obserwuj zachowanie

**Oczekiwane:**
- Seamless transition
- Brak przerwy w rozmowie

**Obecne zachowanie:**
- ❌ Połączenie się zrywa
- ❌ Wymaga ręcznego reconnect

---

## 12. METRYKI DO MONITOROWANIA

### Połączenie
- ✅ Connection state (DISCONNECTED/CONNECTING/CONNECTED)
- ❌ Liczba reconnect attempts
- ❌ Średni czas połączenia
- ❌ Liczba błędów WebSocket

### Audio
- ✅ User audio level
- ✅ Bot audio level
- ❌ Audio latency
- ❌ Packet loss rate

### Sesja
- ✅ Session duration
- ✅ Transcript count
- ❌ Image send success rate
- ❌ Average response time

### Zasoby
- ❌ Memory usage
- ❌ Battery drain
- ❌ Network bandwidth usage

---

## 13. WNIOSKI KOŃCOWE

### Mocne strony aplikacji:
1. ✅ Solidna implementacja WebSocket z OkHttp
2. ✅ Dobra obsługa offline queue
3. ✅ Network monitoring z reaktywnym UI
4. ✅ Session management z timeout
5. ✅ Retry policy dla API calls

### Główne zagrożenia:
1. 🔴 Brak automatycznego reconnect - **największy problem**
2. 🔴 Niewystarczająca obsługa błędów WebSocket
3. 🔴 Potencjalne wycieki pamięci przy crash
4. 🟡 Brak walidacji rozmiaru obrazów
5. 🟡 Deprecated AudioTrack API

### Ogólna ocena stabilności:

**Połączenie WebSocket:** 6/10
- Działa dobrze w idealnych warunkach
- Słaba odporność na problemy sieciowe

**Zarządzanie zasobami:** 7/10
- Dobre cleanup w normalnych warunkach
- Ryzyko leaks przy crash

**Obsługa błędów:** 5/10
- Podstawowa obsługa
- Brak retry dla większości operacji

**Wysyłanie obrazów:** 4/10
- Funkcjonalność działa
- Brak zabezpieczeń przed dużymi plikami

**Ogólna stabilność:** 6/10
- Aplikacja działa, ale wymaga ulepszeń dla produkcji

---

## 14. PLAN DZIAŁANIA

### Faza 1: Krytyczne poprawki (1-2 dni)
- [ ] Dodać readTimeout dla WebSocket
- [ ] Implementować podstawowy reconnect
- [ ] Dodać walidację rozmiaru obrazów
- [ ] Synchronizować AudioTrack

### Faza 2: Ulepszenia stabilności (3-5 dni)
- [ ] Rozbudować obsługę błędów WebSocket
- [ ] Dodać obsługę błędów AudioRecord
- [ ] Implementować timeout dla obrazów
- [ ] Dodać monitoring połączenia

### Faza 3: Optymalizacja (5-7 dni)
- [ ] Migrować do nowego AudioTrack API
- [ ] Dodać metryki i monitoring
- [ ] Implementować ostrzeżenia przed timeout
- [ ] Optymalizować zużycie baterii

### Faza 4: Testy (2-3 dni)
- [ ] Testy stabilności połączenia
- [ ] Testy memory leaks
- [ ] Testy długich sesji
- [ ] Testy w różnych warunkach sieciowych

---

**Koniec raportu**
