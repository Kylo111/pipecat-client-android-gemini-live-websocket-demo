# Plan: Rollback Refaktoru i Właściwe Podejście

## Decyzja: ROLLBACK do commit 345f700

### Powody
1. ❌ Refaktor wprowadził 4+ krytyczne błędy
2. ❌ Audio ma zakłócenia (urywa słowa)
3. ❌ Pauza/wyjście nie zatrzymuje audio
4. ❌ Więcej czasu na naprawy niż korzyści
5. ✅ Commit 345f700 działał stabilnie

## Krok 1: Rollback

```bash
# Sprawdź różnice
git diff 345f700 HEAD

# Przywróć działający kod
git checkout 345f700 -- gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/VoiceClientManager.kt

# Usuń nowe pliki managera (jeśli nie są potrzebne)
git rm gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/managers/SessionAudioManager.kt
git rm gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/managers/SessionConnectionManager.kt
git rm gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/managers/SessionMonitoringManager.kt
git rm gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/state/SessionStateMachine.kt
git rm gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/state/SessionState.kt
git rm gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/state/SessionEvent.kt

# Commit rollback
git commit -m "Rollback: Przywrócenie stabilnego kodu z commit 345f700"
```

## Krok 2: Jeśli refaktor jest NAPRAWDĘ potrzebny

### Właściwe podejście do refaktoru:

#### A. Najpierw testy
```kotlin
// Dodaj testy jednostkowe PRZED refaktorem
@Test
fun `audio playback stops immediately on pause`() {
    // Given: playing audio
    // When: pause()
    // Then: audio stops within 100ms
}

@Test
fun `no audio glitches during playback`() {
    // Given: receiving audio chunks
    // When: playing continuously
    // Then: no gaps or distortions
}
```

#### B. Refaktor inkrementalny (nie wszystko naraz)

**Faza 1: Ekstrakcja audio (1 tydzień)**
- Wydziel tylko audio do `AudioManager`
- Testy po każdej zmianie
- Commit po każdym działającym kroku

**Faza 2: Ekstrakcja połączenia (1 tydzień)**
- Wydziel WebSocket do `ConnectionManager`
- Testy
- Commit

**Faza 3: State machine (2 tygodnie)**
- Dodaj state machine
- Testy
- Commit

#### C. Feature flags
```kotlin
object FeatureFlags {
    val USE_NEW_ARCHITECTURE = false  // Łatwe przełączanie
}

if (FeatureFlags.USE_NEW_ARCHITECTURE) {
    // Nowy kod
} else {
    // Stary, działający kod
}
```

## Krok 3: Alternatywa - Minimalne poprawki

Jeśli rollback nie jest opcją, napraw tylko krytyczne problemy:

### Problem 1: Audio gra po pauzie
```kotlin
// W VoiceClientManager - dodaj natychmiastowe zatrzymanie
fun pause() {
    scope.launch {
        audioTrack?.stop()      // STOP nie pause
        audioTrack?.flush()     // Wyczyść bufor
        audioTrack?.release()   // Zwolnij zasób
        audioTrack = null       // Wymuś reinicjalizację
    }
}
```

### Problem 2: Zakłócenia audio
Przywróć oryginalną logikę z commit 345f700:
- Mutex z `withLock`
- Sprawdzanie stanu AudioTrack przed każdym write
- Obliczanie poziomu audio

## Rekomendacja

**PRZYWRÓĆ commit 345f700 i zostaw tak.**

Jeśli absolutnie musisz refaktor:
1. Zrób to w osobnym branchu
2. Dodaj testy NAJPIERW
3. Refaktoruj małymi krokami
4. Testuj po każdym kroku
5. Merge tylko gdy wszystko działa TAK SAMO jak przed refaktorem

## Pytanie do Ciebie

Czy chcesz:
- **A) Rollback do 345f700** (zalecane) - 5 minut
- **B) Spróbować naprawić obecny kod** - kilka godzin, bez gwarancji
- **C) Zrobić właściwy refaktor z testami** - 2-4 tygodnie

Powiedz co wybierasz.
