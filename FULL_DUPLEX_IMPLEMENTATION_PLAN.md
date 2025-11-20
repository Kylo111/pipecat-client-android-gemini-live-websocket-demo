# Plan Implementacji Przełącznika Full-Duplex / Half-Duplex

## Podsumowanie

**Trudność:** ŚREDNIA ⚙️  
**Czas implementacji:** 1-2 godziny  
**Ryzyko:** ŚREDNIE (może powodować problemy z echo/feedback w full-duplex)

## Obecna sytuacja

Aplikacja działa w trybie **half-duplex**:
- Gdy bot mówi → AudioRecord jest zatrzymany (`stopAudioRecording()`)
- Gdy bot kończy → AudioRecord jest wznawiany (`resumeAudioRecording()`)
- Użytkownik NIE może przerywać bota

To rozwiązanie zostało wprowadzone aby naprawić **krytyczny bug Gemini Live API** z acoustic echo i VAD false positives, który powodował przerywanie odpowiedzi bota w połowie zdania.

## Cel

Dodać przełącznik w Settings pozwalający użytkownikowi wybrać:
- **Half-Duplex** (domyślnie) - bezpieczny, bot kończy wypowiedzi
- **Full-Duplex** - użytkownik może przerywać bota, ale ryzyko echo/feedback

## Zmiany do wprowadzenia

### 1. Preferences.kt - Dodanie nowej preferencji

```kotlin
// Dodaj w sekcji z innymi preferencjami (około linii 50)
val fullDuplexMode = BooleanPref("full_duplex_mode", false) // Domyślnie half-duplex (bezpieczniejsze)
```

### 2. Preferences.kt - Inicjalizacja

```kotlin
// W funkcji initAppStart(), dodaj do listy inicjalizowanych preferencji (około linii 70)
fun initAppStart(context: Context) {
    prefs = context.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    migratePreferences()

    listOf(
        apiKey, systemPrompt, selectedVoice, modelName,
        geminiApiKey, googleCloudApiKey, sessionTimeoutMinutes, autoPauseTimeoutSeconds, 
        botResponseTimeoutMinutes, activityDetectionThreshold, keepScreenAwake,
        selectedSkin, userPin, defaultServerUrl, isDarkTheme, appTheme, toolsInstruction, 
        useSummaryMode, summaryPrompt, summaryModel, parentalLockEnabled,
        fullDuplexMode  // ← DODAJ TO
    ).forEach { it.init() }
}
```

### 3. VoiceClientManager.kt - Modyfikacja logiki audio

**Zmiana w handleTextMessage() - gdy bot zaczyna mówić:**

```kotlin
// Około linii 1033-1037
if (!botIsTalking.value) {
    Log.i(TAG, "Bot started speaking")
    botIsTalking.value = true
    
    // NOWA LOGIKA: Zatrzymaj AudioRecord tylko w half-duplex mode
    if (!Preferences.fullDuplexMode.value) {
        stopAudioRecording()      // Stop AudioRecord to free mic
        Log.i(TAG, "🎤 Half-duplex: AudioRecord stopped (bot speaking)")
    } else {
        Log.i(TAG, "🎤 Full-duplex: AudioRecord continues (user can interrupt)")
    }
    
    updatePicovoiceState()    // Resume Picovoice (can use mic now)
}
updateBotResponseTime() // Bot responded with audio
```

**Zmiana w handleTextMessage() - gdy bot kończy (turnComplete w serverContent):**

```kotlin
// Około linii 1050-1054
if (serverContent?.containsKey("turnComplete") == true) {
    Log.i(TAG, "🔇 Bot stopped speaking (turnComplete in serverContent)")
    botIsTalking.value = false
    
    // NOWA LOGIKA: Wznów AudioRecord tylko jeśli był zatrzymany (half-duplex)
    if (!Preferences.fullDuplexMode.value) {
        resumeAudioRecording()    // Resume AudioRecord
        Log.i(TAG, "🎤 Half-duplex: AudioRecord resumed (bot finished)")
    } else {
        Log.i(TAG, "🎤 Full-duplex: AudioRecord was never stopped")
    }
    
    updatePicovoiceState()    // Pause Picovoice (VoiceClientManager needs mic)
}
```

**Zmiana w handleTextMessage() - gdy bot kończy (turnComplete at root):**

```kotlin
// Około linii 1059-1063
if (jsonObject.containsKey("turnComplete")) {
    Log.i(TAG, "🔇 Bot stopped speaking (turnComplete at root)")
    botIsTalking.value = false
    
    // NOWA LOGIKA: Wznów AudioRecord tylko jeśli był zatrzymany (half-duplex)
    if (!Preferences.fullDuplexMode.value) {
        resumeAudioRecording()    // Resume AudioRecord
        Log.i(TAG, "🎤 Half-duplex: AudioRecord resumed (bot finished)")
    } else {
        Log.i(TAG, "🎤 Full-duplex: AudioRecord was never stopped")
    }
    
    updatePicovoiceState()    // Pause Picovoice (VoiceClientManager needs mic)
}
```

**OPCJONALNIE - Modyfikacja startAudioRecording() aby nie wysyłać audio gdy bot mówi w half-duplex:**

```kotlin
// W pętli nagrywania (około linii 1650-1660)
// Ta logika już istnieje, ale można dodać log dla full-duplex:

// CRITICAL FIX: Don't send audio while bot is talking
// This prevents echo/feedback and bot interruption
if (botIsTalking.value) {
    if (Preferences.fullDuplexMode.value) {
        // W full-duplex wysyłamy audio nawet gdy bot mówi
        Log.d(TAG, "🎤 Full-duplex: Sending audio while bot talks (user can interrupt)")
        // Kontynuuj normalnie - nie rób continue
    } else {
        // W half-duplex nie wysyłamy audio gdy bot mówi
        if (DEBUG_LOGGING) {
            Log.d(TAG, "⏸️ Half-duplex: Skipping audio send - bot is talking")
        }
        continue // Skip sending this audio chunk
    }
}
```

### 4. SettingsScreen.kt - Dodanie UI toggle

**Dodaj nową sekcję w SettingsScreen (po Session Management, przed Visual Preferences):**

```kotlin
// Około linii 680, po sekcji "Zarządzanie sesją"

// Audio Mode Section
SettingsSection(title = "Tryb audio") {
    // Full-Duplex Mode Toggle
    var fullDuplexMode by remember { mutableStateOf(Preferences.fullDuplexMode.value) }
    
    SettingsToggle(
        label = "Full-Duplex (eksperymentalny)",
        checked = fullDuplexMode,
        onCheckedChange = { 
            fullDuplexMode = it
            Preferences.fullDuplexMode.value = it
        }
    )

    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = if (fullDuplexMode) {
            "⚠️ FULL-DUPLEX: Możesz przerywać bota, ale może wystąpić echo lub bot może przerywać swoje wypowiedzi. Mikrofon nagrywa cały czas."
        } else {
            "✅ HALF-DUPLEX (zalecane): Bot kończy swoje wypowiedzi bez przerywania. Mikrofon jest wyłączany gdy bot mówi."
        },
        fontSize = 12.sp,
        fontWeight = FontWeight.W400,
        color = if (fullDuplexMode) Color(0xFFFF9800) else Color(0xFF4CAF50),
        style = TextStyles.base,
        lineHeight = 16.sp
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    // Detailed explanation
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "ℹ️ Różnice między trybami:",
            fontSize = 12.sp,
            fontWeight = FontWeight.W600,
            color = Color.Black,
            style = TextStyles.base
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "• Half-Duplex: Mikrofon wyłącza się gdy bot mówi. Nie możesz przerywać bota, ale jego odpowiedzi są stabilne i bez echo.",
            fontSize = 11.sp,
            fontWeight = FontWeight.W400,
            color = Color.DarkGray,
            style = TextStyles.base,
            lineHeight = 14.sp
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "• Full-Duplex: Mikrofon działa cały czas. Możesz przerywać bota, ale może wystąpić acoustic echo lub bot może przerywać swoje wypowiedzi (znany bug Gemini API).",
            fontSize = 11.sp,
            fontWeight = FontWeight.W400,
            color = Color.DarkGray,
            style = TextStyles.base,
            lineHeight = 14.sp
        )
    }
}
```

## Testowanie

### Scenariusze testowe

**Half-Duplex (domyślny):**
1. ✅ Bot mówi → mikrofon wyłączony → nie można przerywać
2. ✅ Bot kończy → mikrofon włączony → można mówić
3. ✅ Brak echo/feedback
4. ✅ Bot kończy swoje wypowiedzi

**Full-Duplex (eksperymentalny):**
1. ⚠️ Bot mówi → mikrofon włączony → można przerywać
2. ⚠️ Sprawdź czy nie ma echo (może wystąpić)
3. ⚠️ Sprawdź czy bot nie przerywa swoich wypowiedzi (znany bug)
4. ⚠️ Sprawdź czy przerywanie bota działa poprawnie

### Komendy testowe

```bash
# Build i install
./gradlew clean build && ./gradlew installDebug

# Monitoruj logi
adb -s EM95IBKZEYIFSO69 logcat | grep -E "VoiceClientManager|Full-duplex|Half-duplex|AudioRecord"

# Sprawdź czy preferencja jest zapisana
adb -s EM95IBKZEYIFSO69 shell "run-as ai.pipecat.gemini_multimodal_websocket_demo cat /data/data/ai.pipecat.gemini_multimodal_websocket_demo/shared_prefs/prefs.xml | grep full_duplex"
```

## Znane problemy i ograniczenia

### Half-Duplex (obecny tryb)
✅ **Zalety:**
- Stabilne działanie
- Brak echo/feedback
- Bot kończy wypowiedzi
- Bezpieczny dla produkcji

❌ **Wady:**
- Nie można przerywać bota
- Mniej naturalna konwersacja

### Full-Duplex (eksperymentalny)
✅ **Zalety:**
- Można przerywać bota
- Bardziej naturalna konwersacja
- Prawdziwy "live" chat

❌ **Wady:**
- Ryzyko acoustic echo/feedback
- Bot może przerywać swoje wypowiedzi (znany bug Gemini API)
- Wymaga testów w różnych warunkach
- Może nie działać stabilnie

## Rekomendacje

1. **Domyślnie half-duplex** - bezpieczniejszy i stabilniejszy
2. **Full-duplex jako opcja eksperymentalna** - dla użytkowników którzy chcą testować
3. **Wyraźne ostrzeżenie w UI** - że full-duplex może powodować problemy
4. **Możliwość szybkiego powrotu** - do half-duplex jeśli full-duplex nie działa

## Podsumowanie zmian

**Pliki do modyfikacji:**
1. `Preferences.kt` - dodanie `fullDuplexMode` (2 linie)
2. `VoiceClientManager.kt` - warunkowe zatrzymywanie AudioRecord (6 miejsc)
3. `SettingsScreen.kt` - dodanie UI toggle (1 sekcja, ~60 linii)

**Łączna liczba zmian:** ~80 linii kodu

**Czas implementacji:** 1-2 godziny (włącznie z testowaniem)

**Ryzyko:** Średnie - full-duplex może nie działać stabilnie, ale half-duplex pozostaje domyślny i bezpieczny
