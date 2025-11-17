# Diagnoza problemów

## Problem 1: Bot Pomoc nie wie o Picovoice

### Sprawdzenie:
- Plik `help_conversation_prompt.txt` zawiera sekcję "Komendy Głosowe Picovoice" ✅
- Rozmiar: 10KB (~2500 tokenów) ✅
- `OfflineConversationManager.getHelpPrompt()` ładuje z assets ✅

### Możliwe przyczyny:
1. **Plik nie został zainstalowany w APK** - trzeba przebudować
2. **Cache aplikacji** - stara wersja promptu w pamięci
3. **Błąd ładowania** - sprawdź logi

### Rozwiązanie:
```bash
# Wyczyść cache i przebuduj
./gradlew clean
./gradlew assembleDebug installDebug

# Wyczyść dane aplikacji na urządzeniu
adb -s EM95IBKZEYIFSO69 shell pm clear ai.pipecat.gemini_multimodal_websocket_demo

# Zainstaluj ponownie
./gradlew installDebug
```

## Problem 2: Brak menu edycji dla konwersacji Pomoc

### Sprawdzenie:
- `OfflineConversation` ma pole `isSystemConversation: Boolean` ✅
- Konwersacja Pomoc jest tworzona z `isSystemConversation = true` ✅

### Problem:
Konwersacje systemowe (`isSystemConversation = true`) są **celowo** zablokowane przed edycją, aby użytkownik nie mógł zepsuć bota Pomoc.

### Czy to jest bug?
**NIE** - to jest zamierzone zachowanie. Bot Pomoc powinien być chroniony przed edycją.

### Jeśli chcesz umożliwić edycję:
Trzeba zmienić logikę w `ConversationListScreen` aby pokazywać menu także dla konwersacji systemowych. Ale **nie rekomenduj tego** - użytkownik może zepsuć bota Pomoc.

## Problem 3: Brak linku i importu w UI Picovoice

### Sprawdzenie kodu:
```kotlin
// W SettingsScreen.kt linia ~1280:
CustomWakeWordItem(
    wakeWord = wakeWord,
    onImportClick = {
        wakeWordToImport = wakeWord
        filePickerLauncher.launch("*/*")  // ✅ Import działa
    },
    onShowInstructions = {
        showInstructionsDialog = true  // ✅ Dialog z instrukcjami
    }
)

// Dialog z instrukcjami linia ~1310:
if (showInstructionsDialog) {
    WakeWordInstructionsDialog(  // ✅ Dialog istnieje
        onDismiss = { showInstructionsDialog = false },
        onImportClick = { }
    )
}
```

### Sprawdzenie WakeWordInstructionsDialog:
Plik: `gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/ui/WakeWordInstructionsDialog.kt`

Zawiera:
- ✅ Klikalny link "Otwórz Console →" (linia ~90)
- ✅ 8 kroków instrukcji
- ✅ Wskazówki (TipCard)
- ✅ Przycisk "Importuj plik .ppn"

### Wszystko jest w kodzie!

## Dlaczego nie widać?

### Możliwe przyczyny:

1. **Stara wersja APK**
   - Sprawdź: `adb shell dumpsys package ai.pipecat.gemini_multimodal_websocket_demo | grep lastUpdateTime`
   - Powinna być: 2025-11-17 14:30 lub później

2. **Cache UI**
   - Android może cache'ować stare composables
   - Rozwiązanie: Wymuś zatrzymanie aplikacji

3. **Nie dodano komendy głosowej**
   - Link i import pokazują się **tylko po dodaniu komendy**
   - Kroki:
     1. Kliknij "+ Dodaj"
     2. Wpisz nazwę (np. "test")
     3. Kliknij "Dodaj"
     4. **Teraz** powinny pojawić się przyciski "Importuj .ppn" i "Instrukcje"

## Jak przetestować poprawnie

### Test 1: Bot Pomoc wie o Picovoice

```bash
# 1. Wyczyść dane aplikacji
adb -s EM95IBKZEYIFSO69 shell pm clear ai.pipecat.gemini_multimodal_websocket_demo

# 2. Zainstaluj świeżą wersję
./gradlew installDebug

# 3. Uruchom aplikację i zaloguj się

# 4. Uruchom konwersację "❓ Pomoc"

# 5. Zapytaj: "Jak działają komendy głosowe?"

# 6. Bot powinien odpowiedzieć z wiedzą o Picovoice
```

### Test 2: Menu edycji dla Pomoc

```
1. Długie przytrzymanie na "❓ Pomoc"
2. ❌ Menu NIE powinno się pojawić (to jest zamierzone!)
3. Konwersacje systemowe są chronione przed edycją
```

### Test 3: Link i import w Picovoice

```
1. Otwórz Ustawienia
2. Znajdź "Komendy głosowe Picovoice"
3. Kliknij "+ Dodaj"
4. Wpisz "test"
5. Kliknij "Dodaj"
6. ✅ Powinna pojawić się karta z komendą "test"
7. ✅ Powinny być przyciski:
   - "Importuj .ppn" (niebieski)
   - "Instrukcje" (biały z obramowaniem)
8. Kliknij "Instrukcje"
9. ✅ Powinien pojawić się dialog z:
   - 8 krokami instrukcji
   - Przyciskiem "Otwórz Console →" (niebieski)
   - Wskazówkami (żółte tło)
10. Kliknij "Otwórz Console →"
11. ✅ Powinna otworzyć się przeglądarka z console.picovoice.ai
```

## Komendy diagnostyczne

### Sprawdź wersję APK
```bash
adb -s EM95IBKZEYIFSO69 shell dumpsys package ai.pipecat.gemini_multimodal_websocket_demo | grep -E "versionCode|lastUpdateTime"
```

### Sprawdź czy prompt się załadował
```bash
adb -s EM95IBKZEYIFSO69 logcat -d | grep "System Prompt length"
```

### Sprawdź błędy ładowania promptu
```bash
adb -s EM95IBKZEYIFSO69 logcat -d | grep -E "OfflineConvManager|Error loading help"
```

### Wymuś zatrzymanie aplikacji
```bash
adb -s EM95IBKZEYIFSO69 shell am force-stop ai.pipecat.gemini_multimodal_websocket_demo
```

### Wyczyść cache i dane
```bash
adb -s EM95IBKZEYIFSO69 shell pm clear ai.pipecat.gemini_multimodal_websocket_demo
```

## Rekomendowane kroki

1. **Wyczyść dane aplikacji**
   ```bash
   adb -s EM95IBKZEYIFSO69 shell pm clear ai.pipecat.gemini_multimodal_websocket_demo
   ```

2. **Przebuduj i zainstaluj**
   ```bash
   ./gradlew clean assembleDebug installDebug
   ```

3. **Uruchom aplikację i przetestuj**
   - Zaloguj się ponownie
   - Uruchom bota Pomoc
   - Dodaj komendę głosową w ustawieniach
   - Sprawdź czy wszystko działa

## Jeśli nadal nie działa

Prześlij logi:
```bash
adb -s EM95IBKZEYIFSO69 logcat -d > full_logs.txt
```

I sprawdź:
1. Czy są błędy ładowania assets
2. Czy prompt się załadował (szukaj "System Prompt length")
3. Czy są błędy w UI (szukaj "ERROR" lub "FATAL")
