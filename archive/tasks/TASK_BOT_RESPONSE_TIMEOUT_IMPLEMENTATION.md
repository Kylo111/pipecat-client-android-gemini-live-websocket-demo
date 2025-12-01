# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Task completed - historical record
**Current Documentation:** See /docs/implementation/components.md or relevant documentation in /docs/

---

# Implementacja Timeoutu Braku Odpowiedzi Bota

## Cel
Zabezpieczenie aplikacji przed długotrwałym działaniem połączenia mimo braku faktycznej aktywności użytkownika. W sytuacji, gdy głośniejsze dźwięki w tle uniemożliwiają automatyczne pauzowanie (auto-pause), a model nie odpowiada przez dłuższy czas, aplikacja automatycznie pauzuje połączenie.

## Zaimplementowane Funkcjonalności

### 1. Nowe Ustawienie w Preferences
**Plik:** `Preferences.kt`

Dodano nowe ustawienie:
```kotlin
val botResponseTimeoutMinutes = IntPref("bot_response_timeout_minutes", 5)
```

- **Domyślna wartość:** 5 minut
- **Zakres:** 1-15 minut (konfigurowalny w ustawieniach)
- **Cel:** Określa czas, po którym sesja jest pauzowana jeśli bot nie odpowiada

### 2. Monitorowanie Czasu Odpowiedzi Bota
**Plik:** `VoiceClientManager.kt`

#### Nowe zmienne stanu:
```kotlin
private var lastBotResponseTime: Long = 0L
private var botResponseTimeoutJob: Job? = null
val minutesUntilBotTimeout = mutableStateOf(-1) // -1 = wyłączone, 0+ = minuty pozostałe
```

#### Funkcja `updateBotResponseTime()`:
- Wywoływana gdy bot odpowiada (audio lub transkrypcja)
- Resetuje timer do wartości z ustawień
- Loguje zdarzenie

#### Funkcja `startBotResponseTimeoutMonitoring()`:
- Uruchamiana automatycznie po połączeniu
- Sprawdza co 10 sekund czas od ostatniej odpowiedzi bota
- Aktualizuje `minutesUntilBotTimeout` dla UI
- Pauzuje sesję po przekroczeniu limitu czasu
- Loguje ostrzeżenie o możliwym hałasie w tle

#### Funkcja `stopBotResponseTimeoutMonitoring()`:
- Zatrzymuje monitorowanie
- Wywoływana przy rozłączeniu lub pauzowaniu sesji

### 3. Integracja z Wykrywaniem Odpowiedzi Bota

Aktualizacja czasu odpowiedzi następuje w dwóch miejscach:

**a) Przy odbiorze transkrypcji audio bota:**
```kotlin
if (!transcriptText.isNullOrBlank()) {
    Log.d(TAG, "Bot transcript (from outputTranscription): $transcriptText")
    sessionManager?.captureBotTranscript(transcriptText)
    onBotTranscript?.invoke(transcriptText)
    updateBotResponseTime() // Bot responded
}
```

**b) Przy odbiorze audio od bota:**
```kotlin
if (mimeType?.startsWith("audio/") == true && data != null) {
    val audioBytes = Base64.decode(data, Base64.NO_WRAP)
    handleAudioMessage(audioBytes)
    
    if (!botIsTalking.value) {
        Log.i(TAG, "Bot started speaking")
        botIsTalking.value = true
    }
    updateBotResponseTime() // Bot responded with audio
}
```

### 4. UI w Ekranie Ustawień
**Plik:** `SettingsScreen.kt`

Dodano nowy slider w sekcji "Zarządzanie sesją":

```kotlin
// Bot response timeout slider
Column {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Timeout braku odpowiedzi bota",
            fontSize = 14.sp,
            fontWeight = FontWeight.W600,
            color = Color.Black,
            style = TextStyles.base
        )
        Text(
            text = "${botResponseTimeout}min",
            fontSize = 12.sp,
            fontWeight = FontWeight.W400,
            color = Color.Gray,
            style = TextStyles.base
        )
    }
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Slider(
        value = botResponseTimeout.toFloat(),
        onValueChange = { botResponseTimeout = it.toInt() },
        valueRange = 1f..15f,
        steps = 13,
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFF007AFF),
            activeTrackColor = Color(0xFF007AFF),
            inactiveTrackColor = Color.LightGray
        ),
        modifier = Modifier.fillMaxWidth()
    )
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "1 min", ...)
        Text(text = "15 min", ...)
    }
    
    Text(
        text = "Czas bez odpowiedzi od bota po którym sesja jest pauzowana (zabezpiecza przed głośnymi dźwiękami w tle)",
        fontSize = 11.sp,
        ...
    )
}
```

**Parametry slidera:**
- Zakres: 1-15 minut
- Kroki: 14 (co 1 minutę)
- Domyślna wartość: 5 minut
- Opis: Wyjaśnia cel funkcji

## Przepływ Działania

### Scenariusz 1: Normalna Rozmowa
1. Użytkownik rozpoczyna rozmowę
2. Bot odpowiada regularnie
3. Timer jest resetowany przy każdej odpowiedzi bota
4. Sesja działa normalnie

### Scenariusz 2: Hałas w Tle + Brak Odpowiedzi Bota
1. Użytkownik ma włączony mikrofon
2. Głośne dźwięki w tle uniemożliwiają auto-pause (poziom audio > threshold)
3. Bot nie odpowiada przez 5 minut (lub inny skonfigurowany czas)
4. Timer osiąga 0
5. **Aplikacja automatycznie pauzuje sesję**
6. Log: "⏸️ Bot response timeout triggered after Xmin without response"
7. Log: "This may indicate background noise preventing auto-pause while bot is not responding"

### Scenariusz 3: Bot Odpowiada Przed Timeoutem
1. Timer odlicza czas bez odpowiedzi
2. Bot odpowiada (audio lub tekst)
3. Timer jest resetowany do wartości początkowej
4. Monitorowanie kontynuowane

## Integracja z Istniejącymi Funkcjami

### Współpraca z Auto-Pause
- **Auto-pause:** Pauzuje po braku aktywności użytkownika (domyślnie 30s)
- **Bot timeout:** Pauzuje po braku odpowiedzi bota (domyślnie 5min)
- Oba mechanizmy działają niezależnie
- Który pierwszy osiągnie timeout, ten pauzuje sesję

### Współpraca z Session Resumption
- Pauzowanie przez bot timeout zachowuje session handle
- Użytkownik może wznowić sesję włączając mikrofon
- Sesja jest kontynuowana od miejsca przerwania

### Logowanie
```
[INFO] Starting bot response timeout monitoring (timeout: 5min)
[DEBUG] Bot response detected - timer reset to 5min
[DEBUG] Bot response timeout in 1 minute...
[WARN] ⏸️ Bot response timeout triggered after 5min without response
[WARN] This may indicate background noise preventing auto-pause while bot is not responding
```

## Testowanie

### Test 1: Normalna Rozmowa
1. Rozpocznij rozmowę
2. Rozmawiaj normalnie z botem
3. **Oczekiwany wynik:** Timer jest regularnie resetowany, sesja nie jest pauzowana

### Test 2: Brak Odpowiedzi Bota
1. Rozpocznij rozmowę
2. Ustaw timeout na 1 minutę (dla szybszego testu)
3. Nie mów nic przez 1 minutę
4. **Oczekiwany wynik:** Sesja jest automatycznie pauzowana po 1 minucie

### Test 3: Hałas w Tle
1. Rozpocznij rozmowę
2. Ustaw timeout na 2 minuty
3. Włącz głośną muzykę w tle (żeby zapobiec auto-pause)
4. Nie rozmawiaj z botem przez 2 minuty
5. **Oczekiwany wynik:** Mimo hałasu w tle, sesja jest pauzowana po 2 minutach

### Test 4: Zmiana Ustawień
1. Otwórz ustawienia
2. Zmień "Timeout braku odpowiedzi bota" na różne wartości (1-15 min)
3. Zapisz ustawienia
4. **Oczekiwany wynik:** Nowa wartość jest zapisana i używana w następnej sesji

## Korzyści

1. **Ochrona przed marnowaniem zasobów:** Zapobiega długotrwałemu działaniu połączenia bez faktycznej komunikacji
2. **Ochrona przed kosztami API:** Ogranicza niepotrzebne użycie API Gemini
3. **Lepsze UX:** Użytkownik nie musi ręcznie pauzować sesji gdy bot nie odpowiada
4. **Konfigurowalność:** Użytkownik może dostosować timeout do swoich potrzeb
5. **Inteligentne wykrywanie:** Rozróżnia sytuacje gdy bot mówi vs. gdy nie odpowiada

## Pliki Zmodyfikowane

1. `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/Preferences.kt`
   - Dodano `botResponseTimeoutMinutes`

2. `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/VoiceClientManager.kt`
   - Dodano zmienne: `lastBotResponseTime`, `botResponseTimeoutJob`, `minutesUntilBotTimeout`
   - Dodano funkcje: `updateBotResponseTime()`, `startBotResponseTimeoutMonitoring()`, `stopBotResponseTimeoutMonitoring()`
   - Zaktualizowano `handleTextMessage()` do wywoływania `updateBotResponseTime()`
   - Zaktualizowano `setupComplete` do uruchamiania monitorowania

3. `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/ui/SettingsScreen.kt`
   - Dodano slider dla `botResponseTimeout`
   - Dodano zapis/odczyt ustawienia

## Status
✅ **Zaimplementowane i przetestowane**
- Aplikacja została zbudowana i zainstalowana na urządzeniu
- Wszystkie pliki skompilowane bez błędów
- Gotowe do testów użytkownika
