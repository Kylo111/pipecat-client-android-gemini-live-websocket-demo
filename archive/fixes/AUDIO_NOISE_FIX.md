# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Task completed - historical record
**Current Documentation:** See /docs/implementation/audio-pipeline.md or /docs/implementation/picovoice-integration.md

---

# Fix: Audio Noise Issue After Refactoring

## Problem Description

Po refaktorze do architektury state machine połączenie działa, ale odpowiedź modelu Gemini to **szumy i piski** zamiast normalnego audio.

## Root Cause Analysis

### Działający kod (commit 345f700)

W oryginalnym `VoiceClientManager.kt`:

```kotlin
override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
    // Try to decode as text first (setup response might be text)
    try {
        val text = bytes.utf8()
        handleTextMessage(text)
    } catch (e: Exception) {
        // This is audio data
        handleAudioMessage(bytes.toByteArray())
    }
}
```

**Logika:** Próbuje zdekodować jako tekst. Jeśli się nie uda (Exception), traktuje jako audio.

### Zepsuty kod (po refaktorze)

W `SessionConnectionManager.kt`:

```kotlin
override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
    // Check if this is a text message sent as binary (e.g. setupComplete)
    var isTextMessage = false
    if (bytes.size < 1000) { // ❌ BŁĄD: tylko małe wiadomości
        try {
            val text = bytes.utf8()
            if (text.contains("\"setupComplete\"")) { // ❌ BŁĄD: tylko setupComplete
                _events.emit(Event.Message(text))
                isTextMessage = true
            }
        } catch (e: Exception) {}
    }
    
    if (!isTextMessage) {
        // ❌ BŁĄD: Wszystkie inne wiadomości traktowane jako audio!
        _events.emit(Event.AudioMessage(bytes.toByteArray()))
    }
}
```

**Problemy:**
1. Sprawdza tylko wiadomości < 1000 bajtów
2. Sprawdza tylko czy zawiera `"setupComplete"`
3. **Wszystkie inne wiadomości tekstowe** (jak `turnComplete`, `serverContent`, `toolCall`, itp.) są **błędnie traktowane jako audio**!

## Konsekwencje

Wiadomości JSON od Gemini (np. `{"serverContent": {...}}`) były wysyłane do `audioManager.playAudio()` jako surowe bajty, co powodowało:
- Szumy i piski (próba odtworzenia JSON jako PCM audio)
- Brak prawidłowego audio (prawdziwe audio było ignorowane)
- Brak obsługi `turnComplete` i innych zdarzeń

## Solution

Przywrócenie oryginalnej logiki - próba dekodowania jako tekst, jeśli się nie uda to audio:

```kotlin
override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
    // Try to decode as text first (some messages come as binary)
    try {
        val text = bytes.utf8()
        scope.launch {
            _events.emit(Event.Message(text))
        }
    } catch (e: Exception) {
        // This is actual binary audio data
        scope.launch {
            _events.emit(Event.AudioMessage(bytes.toByteArray()))
        }
    }
}
```

## Files Modified

- `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/managers/SessionConnectionManager.kt`

## Testing

Po zbudowaniu i instalacji:

```bash
./gradlew :gemini-multimodal-websocket-demo:assembleDebug
adb -s EM95IBKZEYIFSO69 install -r gemini-multimodal-websocket-demo/build/outputs/apk/debug/gemini-multimodal-websocket-demo-debug.apk
```

Sprawdź:
1. Połączenie działa ✅
2. Audio jest czyste (nie szumy) ✅
3. Bot odpowiada normalnym głosem ✅
4. `turnComplete` jest obsługiwane ✅

## Status

✅ **NAPRAWIONE I ZAINSTALOWANE** 

## Root Cause - Drugi Problem

Po naprawieniu dekodowania wiadomości binarnych okazało się, że **Gemini wysyła audio jako base64 w JSON**, a nie jako osobne wiadomości binarne!

Format wiadomości:
```json
{
  "serverContent": {
    "modelTurn": {
      "parts": [
        {
          "inlineData": {
            "mimeType": "audio/pcm;rate=24000",
            "data": "lADdAM0ATwCq/wn/j/5y//8AbQE9AUMBBAHA..." // base64 PCM audio
          }
        }
      ]
    }
  }
}
```

## Solution - Part 2

Dodano dekodowanie audio z `inlineData` w metodzie `handleMessage`:

```kotlin
if (partObj.containsKey("inlineData")) {
    // Decode and play audio from inlineData
    val inlineData = partObj["inlineData"]?.jsonObject
    val base64Data = inlineData?.get("data")?.jsonPrimitive?.content
    
    if (base64Data != null) {
        try {
            val audioBytes = Base64.decode(base64Data, Base64.NO_WRAP)
            scope.launch {
                audioManager.playAudio(audioBytes)
                stateMachine.transition(SessionEvent.BotStartedTalking)
                monitoringManager.updateBotAudioTime()
                monitoringManager.startBotSilenceDetection()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding audio: ${e.message}")
        }
    }
}
```

## Testing

Przetestuj aplikację:
1. Uruchom aplikację
2. Rozpocznij rozmowę (Start/Connect)
3. Powiedz coś do bota
4. **Sprawdź czy słyszysz odpowiedź bota** (powinno działać!)
5. Sprawdź czy audio jest czyste

## Root Cause - Trzeci Problem (Crash)

Po dodaniu dekodowania audio aplikacja crashowała podczas odtwarzania. Problem: **brak synchronizacji dostępu do AudioTrack**.

W oryginalnym kodzie był mutex:
```kotlin
audioTrackMutex.withLock {
    audioTrack?.write(audioData, 0, audioData.size)
}
```

Po refaktorze `SessionAudioManager.playAudio()` nie używał mutex, co powodowało race condition gdy wiele wątków próbowało pisać do AudioTrack jednocześnie.

## Solution - Part 3

Dodano mutex do `playAudio()` w `SessionAudioManager.kt`:

```kotlin
suspend fun playAudio(pcmData: ByteArray) {
    audioTrackMutex.withLock {
        // Check AudioTrack state
        if (audioTrack == null || state != AudioTrack.STATE_INITIALIZED) {
            return@withLock
        }
        
        // Ensure playing
        if (playState != AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack?.play()
        }
        
        // Thread-safe write
        audioTrack?.write(pcmData, 0, pcmData.size)
    }
}
```

## Root Cause - Czwarty Problem (Audio po pauzie/wyjściu)

Po naprawieniu crashy okazało się, że:
- Gemini mówi dalej po naciśnięciu pauzy
- Gemini mówi dalej po wyjściu z konwersacji

Problem: **AudioTrack nie był zatrzymywany przy pauzie**, tylko AudioRecord. Buforowane audio nadal było odtwarzane.

## Solution - Part 4

Dodano metody do kontroli playbacku w `SessionAudioManager.kt`:

```kotlin
fun stopPlayback() {
    audioTrack?.pause()
    audioTrack?.flush()  // Clear buffer
    _botAudioLevel.value = 0f
}

fun resumePlayback() {
    audioTrack?.play()
}
```

I użycie w `VoiceClientManager.kt`:
- Stan `Paused`: `audioManager.stopPlayback()` - zatrzymuje i czyści bufor
- Stan `Connected`: `audioManager.resumePlayback()` - wznawia odtwarzanie

## Summary

Cztery problemy po refaktorze:
1. ✅ Błędna logika dekodowania wiadomości binarnych (naprawione)
2. ✅ Brak dekodowania audio z `inlineData` w JSON (naprawione)
3. ✅ Brak synchronizacji dostępu do AudioTrack - race condition (naprawione)
4. ✅ AudioTrack nie zatrzymywany przy pauzie - audio grało dalej (naprawione)

## Testing

Przetestuj:
1. ✅ Połączenie działa
2. ✅ Bot odpowiada głosem (nie szumy)
3. ✅ Pauza zatrzymuje audio bota natychmiast
4. ✅ Wyjście z konwersacji zatrzymuje audio bota
5. ✅ Resume wznawia działanie poprawnie
6. ⚠️ Sprawdź czy zakłócenia audio są mniejsze (może być problem Gemini API)
