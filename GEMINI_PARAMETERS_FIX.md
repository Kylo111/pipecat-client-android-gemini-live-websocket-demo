# Naprawa Parametrów Gemini - Usunięcie Nieobsługiwanych

**Data:** 2025-12-16  
**Problem:** Gemini nie odpowiadał - Setup nie był zakończony  
**Przyczyna:** Gemini Live API nie wspiera niektórych parametrów

---

## Problem

Po dodaniu zaawansowanych parametrów, Gemini:
- ✅ Łączył się (WebSocket OK)
- ❌ NIE kończył Setup
- ❌ NIE odpowiadał na pytania

**Diagnoza pokazała:**
```
❌ Setup NIE został zakończony - to jest problem!
❌ Brak odpowiedzi od Gemini
```

---

## Przyczyna

Według dokumentacji Gemini Live API, **te parametry NIE są wspierane**:
- ❌ `presence_penalty` - powoduje odrzucenie requestu
- ❌ `frequency_penalty` - powoduje odrzucenie requestu  
- ❌ `stop_sequences` - powoduje odrzucenie requestu

Gemini API **milcząco odrzuca** cały request jeśli zawiera nieobsługiwane parametry.

---

## Rozwiązanie

### Usunięto z API:
- ❌ `presence_penalty`
- ❌ `frequency_penalty`
- ❌ `stop_sequences`

### Pozostawiono (WSPIERANE):
- ✅ `temperature` (0.0-2.0)
- ✅ `top_p` (0.0-1.0)
- ✅ `top_k` (1-128)
- ✅ `max_output_tokens` (1-8000)

---

## Zmiany w Kodzie

### 1. GenerationConfig (protocol/GeminiProtocol.kt)
```kotlin
data class GenerationConfig(
    val response_modalities: List<String>,
    val speech_config: SpeechConfig?,
    val temperature: Float?,
    val top_p: Float?,
    val top_k: Int?,
    val max_output_tokens: Int?
    // NOTE: presence_penalty, frequency_penalty, stop_sequences 
    // are NOT supported by Gemini Live API
)
```

### 2. buildSetupMessage() - Usunięto parametry
```kotlin
fun buildSetupMessage(
    model: String,
    voiceName: String,
    systemPrompt: String,
    temperature: Float,
    sessionHandle: String?,
    canResumeSession: Boolean,
    toolDeclarations: List<JsonElement>,
    topP: Float? = null,
    topK: Int? = null,
    maxOutputTokens: Int? = null
    // Removed: presencePenalty, frequencyPenalty, stopSequences
)
```

### 3. GeminiClient.connect() - Usunięto parametry
```kotlin
suspend fun connect(
    voiceName: String = "Puck",
    systemPrompt: String = "",
    temperature: Float = 0.8f,
    toolDeclarations: List<JsonElement> = emptyList(),
    topP: Float? = null,
    topK: Int? = null,
    maxOutputTokens: Int? = null
    // Removed: presencePenalty, frequencyPenalty, stopSequences
)
```

### 4. VoiceClientManager.connect() - Usunięto parametry

### 5. ModelSettingsDialog - Zaktualizowano UI
- Usunięto slidery dla nieobsługiwanych parametrów
- Dodano info box z ostrzeżeniem
- Pozostawiono tylko wspierane parametry

---

## Co Dalej Działa

### ✅ Wspierane Parametry (Działają)

#### 1. **Temperature** (0.0-2.0)
- **0.0-0.3:** Bardzo precyzyjne, powtarzalne odpowiedzi
- **0.7-1.0:** Zbalansowane (domyślne: 0.8)
- **1.5-2.0:** Bardzo kreatywne, losowe

#### 2. **Top P** (0.0-1.0)
- **0.7-0.85:** Skupione odpowiedzi (domyślne: 0.85)
- **0.9-1.0:** Większa różnorodność

#### 3. **Top K** (1-128)
- **10-30:** Precyzyjne odpowiedzi (domyślne: 30)
- **40-100:** Większa różnorodność

#### 4. **Max Output Tokens** (256-4096)
- **256-512:** Krótkie odpowiedzi
- **1024:** Średnie (domyślne)
- **2048-4096:** Długie odpowiedzi

---

## Jak Przetestować

### 1. Uruchom aplikację
```powershell
# Wyczyść logi
adb -s EM95IBKZEYIFSO69 logcat -c

# Monitoruj połączenie
adb -s EM95IBKZEYIFSO69 logcat | Select-String "DIAGNOSTIC|Setup complete|Connected"
```

### 2. Rozpocznij rozmowę

Powinieneś zobaczyć:
```
✅ Connected to Gemini
✅ Setup complete
✅ Odpowiedzi od AI
```

### 3. Przetestuj parametry

**Test A: Zwięzły (Temperature 0.3, Top P 0.7, Top K 15, Max Tokens 256)**
> "Opowiedz mi o AI"

**Oczekiwane:** Krótka, precyzyjna odpowiedź

**Test B: Kreatywny (Temperature 1.5, Top P 0.95, Top K 80, Max Tokens 2048)**
> "Opowiedz mi o AI"

**Oczekiwane:** Długa, kreatywna, różnorodna odpowiedź

---

## Wnioski

### ❌ Nie Udało Się
- Presence Penalty (eliminacja powtórzeń)
- Frequency Penalty (redukcja gadatliwości)
- Stop Sequences (zatrzymywanie na frazach)

### ✅ Udało Się
- Temperature (kontrola losowości)
- Top P (nucleus sampling)
- Top K (wybór tokenów)
- Max Output Tokens (długość odpowiedzi)

### 📊 Efekt
Nadal możemy kontrolować:
- **Długość odpowiedzi** (max_output_tokens)
- **Precyzję vs Kreatywność** (temperature, top_p, top_k)
- **Styl odpowiedzi** (kombinacja parametrów)

Nie możemy bezpośrednio kontrolować:
- Powtórzeń (brak presence_penalty)
- Gadatliwości (brak frequency_penalty)
- Zatrzymywania na frazach (brak stop_sequences)

---

## Status

✅ **Naprawione**  
✅ **Zainstalowane**  
⏳ **Czeka na test użytkownika**

---

## Następne Kroki

1. Przetestuj czy Gemini teraz odpowiada
2. Przetestuj różne wartości parametrów
3. Sprawdź czy różnice są widoczne
4. Jeśli działa - możemy dodać predefiniowane profile

**Uruchom:** `.\diagnose_gemini_connection.ps1` aby sprawdzić czy Setup się kończy pomyślnie.
