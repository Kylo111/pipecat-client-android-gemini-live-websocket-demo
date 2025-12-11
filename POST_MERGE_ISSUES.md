# Post-Merge Issues Analysis

## Data: 2024-12-11

Po merge nowego Core Audio ze starą logiką trzeba naprawić błędy integracji.

## Problemy do Sprawdzenia

### 1. Bot dostaje "bzdury" na input w prompcie

**Status**: ✅ ROZWIĄZANY

**Opis**: Bot dostaje nieprawidłowy kontekst w prompcie systemowym.

**Przepływ kontekstu** (ZWERYFIKOWANY):
1. `ConversationLauncher.launchOfflineConversation()` wywołuje `sessionManager.startOfflineSession()`
2. `SessionManager.startOfflineSession()` wywołuje `offlineContextBuilder.buildContext(conversationId)`
3. `OfflineContextBuilder.buildContext()` buduje kontekst z:
   - Global User Card (persistent facts)
   - Local Conversation Card (conversation-specific state)
   - Meta-Summary (narrative history)
   - Last Session Transcript (most recent conversation)
   - Conversation Persona (system prompt)
4. Kontekst jest zwracany do `ConversationLauncher`
5. `ConversationLauncher.buildOfflineSystemPrompt()` łączy:
   - `basePrompt` (systemPrompt z offline conversation)
   - `conversationContext` (z OfflineContextBuilder)
6. Pełny prompt jest zapisywany w `Preferences.systemPrompt.value`
7. `VoiceClientManagerSimple.start()` pobiera `Preferences.systemPrompt.value`
8. `VoiceClientManagerSimple` przekazuje do `SimpleVoiceClientManager.connect(systemPrompt = ...)`
9. `SimpleVoiceClientManager` przekazuje do `GeminiClient.connect(systemPrompt = ...)`

**Co trzeba sprawdzić**:
- [ ] Czy `OfflineContextBuilder.buildContext()` zwraca prawidłowy kontekst?
- [ ] Czy kontekst zawiera wszystkie komponenty (Global Card, Local Card, Meta-Summary, Transcript)?
- [ ] Czy `buildOfflineSystemPrompt()` prawidłowo łączy basePrompt z kontekstem?
- [ ] Czy `Preferences.systemPrompt.value` zawiera pełny prompt przed startem?
- [ ] Czy `GeminiClient` otrzymuje pełny prompt?

**Możliwe przyczyny**:
1. `OfflineContextBuilder` zwraca pusty kontekst (brak danych w bazie)
2. `buildOfflineSystemPrompt()` nieprawidłowo formatuje kontekst
3. `Preferences.systemPrompt.value` jest nadpisywany po ustawieniu
4. `GeminiClient` nie przekazuje pełnego prompta do Gemini API

**Wyniki testów (2024-12-11 23:15):**

✅ **OfflineContextBuilder** zwraca prawidłowy kontekst (1325 znaków):
- Global User Card: `userName: "Алексей", knownLanguages: ["Polish"]`
- Local Conversation Card: `currentTopic: "Introduction to Quantum Physics"`
- Meta-Summary: "New conversation started. User introduced himself as Krzysiek..."
- Last Session Transcript: 320 chars

✅ **ConversationLauncher** łączy kontekst z basePrompt (1956 znaków)

✅ **GeminiClient** otrzymuje pełny prompt i wysyła do API (setup message: 8259 chars)

✅ **Bot odpowiada z kontekstem** - rozmawiał o fizyce kwantowej

**Wniosek:** Kontekst jest przekazywany prawidłowo. Problem nie leży w kontekście.

---

### 2. Brak aktualizacji kart użytkownika po zakończeniu sesji

**Status**: DO ZBADANIA

**Opis**: Po zakończeniu konwersacji nie widać "zapamiętuje wspomnienia" - czyli nie są aktualizowane karty użytkownika i podsumowanie.

**Przepływ aktualizacji pamięci** (ZWERYFIKOWANY):
1. `SessionManager.endSession()` jest wywoływany po zakończeniu sesji
2. Sprawdza czy sesja spełnia minimalne wymagania (czas, długość transkrypcji)
3. Dla konwersacji `gemini_live` lub `offline`:
   - Pobiera `conversationSystemPrompt` (persona) przez `getConversationSystemPrompt()`
   - Blokuje konwersację przez `conversationLockManager.lockConversation()`
   - Wywołuje `memoryUpdateService.updateMemoryAfterSession(conversationId, transcript, conversationSystemPrompt)`
   - Wywołuje `memoryUpdateService.persistMemoryUpdate()` żeby zapisać zmiany
   - Odblokowuje konwersację przez `conversationLockManager.unlockConversation()`

**Co trzeba sprawdzić**:
- [ ] Czy `endSession()` jest wywoływany po zakończeniu sesji?
- [ ] Czy sesja spełnia minimalne wymagania (MIN_SESSION_DURATION_SECONDS, MIN_TRANSCRIPT_LENGTH)?
- [ ] Czy `memoryUpdateService.updateMemoryAfterSession()` jest wywoływany?
- [ ] Czy `memoryUpdateService.persistMemoryUpdate()` jest wywoływany?
- [ ] Czy karty użytkownika są faktycznie aktualizowane w bazie danych?
- [ ] Czy są logi "🧠 Using MemoryUpdateService for memory evolution"?
- [ ] Czy są logi "✅ Memory updated successfully"?
- [ ] Czy są logi "✅ Memory persisted to storage"?

**Możliwe przyczyny**:
1. `endSession()` nie jest wywoływany (problem w lifecycle)
2. Sesja nie spełnia minimalnych wymagań (za krótka, za mało transkrypcji)
3. `memoryUpdateService.updateMemoryAfterSession()` zwraca błąd
4. `memoryUpdateService.persistMemoryUpdate()` zwraca błąd
5. Baza danych nie zapisuje zmian
6. Logi są, ale użytkownik ich nie widzi (problem z UI)

**Akcje**:
1. Dodać logi na początku `endSession()` żeby potwierdzić wywołanie
2. Dodać logi sprawdzające minimalne wymagania
3. Sprawdzić logi z `memoryUpdateService` w logcat
4. Sprawdzić czy są błędy w `persistMemoryUpdate()`
5. Sprawdzić bazę danych po zakończeniu sesji

---

### 3. Bot nie działa w nowej konwersacji

**Status**: DO ZBADANIA

**Opis**: Po zrobieniu nowej konwersacji bot w tej konwersacji nie działa.

**Co trzeba sprawdzić**:
- [ ] Czy nowa konwersacja jest tworzona w bazie danych?
- [ ] Czy nowa konwersacja ma prawidłowy systemPrompt?
- [ ] Czy `startOfflineSession()` działa dla nowej konwersacji?
- [ ] Czy `OfflineContextBuilder.buildContext()` zwraca pusty kontekst dla nowej konwersacji (to jest OK)?
- [ ] Czy `VoiceClientManager` startuje prawidłowo dla nowej konwersacji?
- [ ] Czy są jakieś błędy w logach?

**Możliwe przyczyny**:
1. Nowa konwersacja nie jest tworzona w bazie danych
2. Nowa konwersacja ma nieprawidłowy systemPrompt (pusty lub null)
3. `startOfflineSession()` zwraca błąd dla nowej konwersacji
4. `VoiceClientManager` nie startuje dla nowej konwersacji
5. Problem z audio (mikrofon, speaker)

**Akcje**:
1. Sprawdzić logi podczas tworzenia nowej konwersacji
2. Sprawdzić bazę danych po utworzeniu nowej konwersacji
3. Sprawdzić logi podczas startu sesji dla nowej konwersacji
4. Sprawdzić logi z `VoiceClientManager` podczas startu

---

## Plan Działania

### Faza 1: Diagnostyka (CURRENT)

1. **Dodać logi diagnostyczne** w kluczowych miejscach:
   - `OfflineContextBuilder.buildContext()` - co zwraca
   - `ConversationLauncher.buildOfflineSystemPrompt()` - pełny prompt
   - `VoiceClientManagerSimple.start()` - co jest w Preferences
   - `GeminiClient.connect()` - co jest wysyłane do API
   - `SessionManager.endSession()` - czy jest wywoływany
   - `MemoryUpdateService.updateMemoryAfterSession()` - czy jest wywoływany
   - `MemoryUpdateService.persistMemoryUpdate()` - czy jest wywoływany

2. **Uruchomić testy**:
   - Utworzyć nową konwersację
   - Przeprowadzić krótką rozmowę (>30s, >50 znaków)
   - Zakończyć sesję
   - Sprawdzić logi
   - Sprawdzić bazę danych
   - Rozpocząć nową sesję w tej samej konwersacji
   - Sprawdzić czy kontekst jest przekazywany

### Faza 2: Naprawa

Po zidentyfikowaniu problemów:
1. Naprawić problem z kontekstem (jeśli istnieje)
2. Naprawić problem z aktualizacją pamięci (jeśli istnieje)
3. Naprawić problem z nową konwersacją (jeśli istnieje)

### Faza 3: Weryfikacja

1. Przetestować pełny przepływ:
   - Utworzenie nowej konwersacji
   - Pierwsza sesja (brak kontekstu)
   - Zakończenie sesji (aktualizacja pamięci)
   - Druga sesja (z kontekstem)
   - Weryfikacja że bot pamięta poprzednią rozmowę

---

## Notatki

- Nowy Core Audio (`audio/simple/`) jest używany przez `VoiceClientManagerSimple`
- Stary `VoiceClientManager` jest deprecated ale nadal w kodzie
- `SessionManager` obsługuje oba managery (stary i nowy)
- `OfflineContextBuilder` jest używany tylko dla offline conversations
- `MemoryUpdateService` jest używany dla `gemini_live` i `offline` conversations
- LibreChat conversations używają legacy summary generator

---

## Status: TESTY ZAKOŃCZONE - ZNALEZIONO PROBLEMY

### Dodane logi diagnostyczne:

1. **OfflineContextBuilder.buildContext()**:
   - Logi na początku funkcji z ID konwersacji
   - Logi dla każdego komponentu (Global Card, Local Card, Meta-Summary, Transcript)
   - Logi z podglądem zawartości każdego komponentu
   - Logi z finalnym kontekstem (długość + preview)

2. **ConversationLauncher.buildOfflineSystemPrompt()**:
   - Logi z długością basePrompt i conversationContext
   - Logi z finalnym promptem (długość + preview)
   - Logi z Preferences.systemPrompt.value po ustawieniu

3. **VoiceClientManagerSimple.start()**:
   - Logi z systemPrompt z Preferences (długość + preview)
   - Logi z liczbą narzędzi

4. **GeminiClient.connect()**:
   - Logi z parametrami połączenia (model, voice, temperature)
   - Logi z systemPrompt (długość + preview)
   - Logi z setup message (długość + preview)

5. **SessionManager.endSession()**:
   - Logi na początku funkcji
   - Logi sprawdzające minimalne wymagania
   - Logi z transkrypcją (długość + preview)
   - Logi z wynikami aktualizacji pamięci

### Aplikacja zainstalowana:

✅ Kompilacja: `./gradlew assembleDebug -x test` - SUCCESS
✅ Instalacja: `./gradlew installDebug` - SUCCESS
✅ Urządzenie: 2409FPCC4G (Android 15)

### Instrukcje testowania:

**KROK 1: Uruchomić aplikację na urządzeniu**

**KROK 2: Uruchomić monitorowanie logów**
```bash
adb -s 2409FPCC4G logcat -c
adb -s 2409FPCC4G logcat | grep -E "DIAGNOSTIC|OfflineContextBuilder|ConversationLauncher|VoiceClientManagerSimple|GeminiClient|SessionManager|MemoryUpdateService"
```

**KROK 3: Utworzyć nową konwersację offline**
- Kliknąć "+" w aplikacji
- Wprowadzić nazwę i system prompt
- Zapisać

**KROK 4: Rozpocząć pierwszą sesję**
- Kliknąć na nową konwersację
- Sprawdzić logi - powinny pokazać:
  - `[DIAGNOSTIC] Building context for conversation: [ID]`
  - `[DIAGNOSTIC] No conversation context, using base prompt only` (pierwsza sesja)
  - `[DIAGNOSTIC] System prompt from Preferences: X chars`
  - `[DIAGNOSTIC] Connecting to Gemini Live API...`

**KROK 5: Przeprowadzić krótką rozmowę**
- Rozmawiać z botem przez >30 sekund
- Powiedzieć >50 znaków tekstu
- Sprawdzić czy bot odpowiada

**KROK 6: Zakończyć sesję**
- Kliknąć przycisk zakończenia
- Sprawdzić logi - powinny pokazać:
  - `[DIAGNOSTIC] endSession() called`
  - `[DIAGNOSTIC] Session qualifies for memory update: ...`
  - `[DIAGNOSTIC] Using MemoryUpdateService for memory evolution`
  - `[DIAGNOSTIC] Memory updated successfully`
  - `[DIAGNOSTIC] Memory persisted to storage`

**KROK 7: Rozpocząć drugą sesję w tej samej konwersacji**
- Kliknąć ponownie na tę samą konwersację
- Sprawdzić logi - powinny pokazać:
  - `[DIAGNOSTIC] Building context for conversation: [ID]`
  - `[DIAGNOSTIC] Global User Card loaded: ...`
  - `[DIAGNOSTIC] Local Conversation Card loaded: ...`
  - `[DIAGNOSTIC] Meta-Summary: ...`
  - `[DIAGNOSTIC] Last Session Transcript: X chars`
  - `[DIAGNOSTIC] Built context: X characters, Y sections`
  - `[DIAGNOSTIC] Full prompt length: X chars`

**KROK 8: Sprawdzić czy bot pamięta poprzednią rozmowę**
- Zapytać bota o coś z poprzedniej rozmowy
- Sprawdzić czy bot odpowiada z kontekstem

### Oczekiwane logi:

**Podczas startu sesji**:
- `[DIAGNOSTIC] Building context for conversation: [ID]`
- `[DIAGNOSTIC] Global User Card loaded: ...`
- `[DIAGNOSTIC] Local Conversation Card: ...`
- `[DIAGNOSTIC] Meta-Summary: ...`
- `[DIAGNOSTIC] Last Session Transcript: ...`
- `[DIAGNOSTIC] Built context: X characters, Y sections`
- `[DIAGNOSTIC] Building offline system prompt: ...`
- `[DIAGNOSTIC] Full prompt length: X chars`
- `[DIAGNOSTIC] System prompt from Preferences: X chars`
- `[DIAGNOSTIC] Connecting to Gemini Live API...`
- `[DIAGNOSTIC] System prompt preview: ...`

**Podczas zakończenia sesji**:
- `[DIAGNOSTIC] endSession() called`
- `[DIAGNOSTIC] Session qualifies for memory update: ...`
- `[DIAGNOSTIC] Using MemoryUpdateService for memory evolution`
- `[DIAGNOSTIC] Conversation persona: ...`
- `[DIAGNOSTIC] Memory updated successfully`
- `[DIAGNOSTIC] Memory persisted to storage`


---

### 4. Bot używa złego modelu (gemini-2.0 zamiast gemini-2.5)

**Status**: ❌ ZNALEZIONY - DO NAPRAWY

**Opis**: Bot jest głupi i słabo mówi po polsku, ponieważ używa `gemini-2.0-flash-exp` zamiast `gemini-2.5-flash-exp`.

**Dowód z logów**:
```
GeminiClient: Connection parameters:
  - model: gemini-2.0-flash-exp  ❌ ZŁY MODEL
```

**Stary kod używał**:
```kotlin
// VoiceClientManager.kt (stary)
val model = Preferences.modelName.value ?: "gemini-2.5-flash-native-audio-preview-09-2025"
```

**Nowy kod ma hardcoded**:
```kotlin
// GeminiClient.kt (nowy)
class GeminiClient(
    private val apiKey: String,
    private val model: String = "gemini-2.0-flash-exp",  ❌ HARDCODED ZŁY MODEL
    private val scope: CoroutineScope
)
```

**Naprawa**:
1. Zmienić default w `GeminiClient` na `gemini-2.5-flash-exp`
2. Przekazywać model z `Preferences.modelName.value` w `VoiceClientManagerSimple`
3. Dodać parametr `model` do konstruktora `VoiceClientManager` (simple)

**Pliki do zmiany**:
- `audio/simple/GeminiClient.kt` - zmienić default model
- `VoiceClientManagerSimple.kt` - pobrać model z Preferences i przekazać
- `audio/simple/VoiceClientManager.kt` - dodać parametr model

---

## Wyniki Testów

### Test 1: Kontekst (2024-12-11 23:15)

**Konwersacja**: "Nauczyciel" (fizyka kwantowa)
**Czas trwania**: 15 sekund
**Transkrypcja**: 363 znaki

**Wyniki**:
- ✅ Kontekst budowany prawidłowo (1325 chars)
- ✅ Prompt łączony prawidłowo (1956 chars)
- ✅ Setup message wysłany do API (8259 chars)
- ✅ Bot odpowiada z kontekstem
- ❌ Sesja za krótka dla aktualizacji pamięci (min 30s)
- ❌ Bot używa złego modelu (gemini-2.0 zamiast 2.5)

**Logi kluczowe**:
```
OfflineContextBuilder: ✅ [DIAGNOSTIC] Built context: 1325 characters, 5 sections
ConversationLauncher: ✅ [DIAGNOSTIC] Full prompt length: 1956 chars
GeminiClient: ❌ model: gemini-2.0-flash-exp
SessionManager: ⏭️ Session too short for memory update (15s, 363 chars) - skipping
```

---

## Plan Naprawy

### Faza 1: Naprawa modelu (CURRENT)

1. ✅ Zidentyfikowano problem - hardcoded `gemini-2.0-flash-exp`
2. 🔄 Zmienić default na `gemini-2.5-flash-exp`
3. 🔄 Przekazywać model z Preferences
4. 🔄 Skompilować i zainstalować
5. 🔄 Przetestować czy bot jest mądrzejszy

### Faza 2: Test aktualizacji pamięci

1. Przeprowadzić rozmowę >30 sekund
2. Sprawdzić logi:
   - `[DIAGNOSTIC] Using MemoryUpdateService for memory evolution`
   - `[DIAGNOSTIC] Memory updated successfully`
   - `[DIAGNOSTIC] Memory persisted to storage`
3. Rozpocząć nową sesję i sprawdzić czy kontekst zawiera nowe informacje

---

## Status: NAPRAWA ZAINSTALOWANA - GOTOWA DO TESTÓW

### Zmiany wprowadzone:

1. ✅ `GeminiClient.kt` - zmieniony default model z `gemini-2.0-flash-exp` na `gemini-2.5-flash-exp`
2. ✅ `VoiceClientManager.kt` (simple) - dodany parametr `model` do konstruktora
3. ✅ `VoiceClientManagerSimple.kt` - pobiera model z `Preferences.modelName.value` i przekazuje do `SimpleVoiceClientManager`
4. ✅ Aplikacja skompilowana i zainstalowana

### Następny test:

1. Uruchomić aplikację
2. Rozpocząć konwersację
3. Sprawdzić logi czy używany jest prawidłowy model:
   ```
   VoiceClientManagerSimple: 🔍 [DIAGNOSTIC] Using model: gemini-2.5-flash-exp
   GeminiClient: - model: gemini-2.5-flash-exp
   ```
4. Sprawdzić czy bot jest mądrzejszy i lepiej mówi po polsku
5. Przeprowadzić rozmowę >30 sekund żeby przetestować aktualizację pamięci
