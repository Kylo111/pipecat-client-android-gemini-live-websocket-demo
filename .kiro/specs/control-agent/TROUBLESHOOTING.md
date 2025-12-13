# Control Agent - Troubleshooting Guide

## Problem: Control Agent nie reaguje na polecenia głosowe

### Diagnoza

Control Agent jest **domyślnie wyłączony** ze względów bezpieczeństwa. Aby działał, musisz go włączyć w ustawieniach aplikacji.

### Rozwiązanie

1. **Otwórz aplikację**
2. **Przejdź do Ustawień** (ikona koła zębatego)
3. **Znajdź sekcję "Agent sterowania głosowego"**
4. **Włącz przełącznik "Włącz agenta sterowania"**
5. **Sprawdź, czy widzisz zielony tekst**: "✅ WŁĄCZONY: Agent nasłuchuje komend głosowych..."

### Weryfikacja

Po włączeniu Control Agenta, sprawdź logi:

```bash
adb -s EM95IBKZEYIFSO69 logcat -c
adb -s EM95IBKZEYIFSO69 logcat | grep -E "ControlAgent|FlashLite|onUserTranscript"
```

Powinieneś zobaczyć logi typu:
- `ControlAgentManager: onUserTranscript called: 'wycisz'`
- `ControlAgentManager: 🎯 Processing transcript: 'wycisz'`
- `FlashLiteClient: Analyzing intent...`

### Testowanie

Po włączeniu, wypróbuj następujące komendy:

**Polski:**
- "wycisz" - powinno wyciszyć mikrofon
- "zakończ" - powinno zakończyć sesję
- "rozłącz" - powinno zakończyć sesję
- "przełącz na [nazwa konwersacji]" - powinno przełączyć konwersację

**Angielski:**
- "mute" - should mute microphone
- "end" - should end session
- "hangup" - should end session
- "switch to [conversation name]" - should switch conversation

### Dodatkowe sprawdzenia

1. **Sprawdź, czy masz klucz API Gemini**:
   - Ustawienia → Klucz API Gemini
   - Control Agent używa Gemini 2.5 Flash Lite

2. **Sprawdź logi błędów**:
   ```bash
   adb -s EM95IBKZEYIFSO69 logcat | grep -E "ERROR|Exception" | grep ControlAgent
   ```

3. **Sprawdź, czy transkrypcje są finalne**:
   - Control Agent przetwarza tylko finalne transkrypcje (isFinal=true)
   - Gemini Live musi najpierw zakończyć transkrypcję

### Znane ograniczenia

1. **Latencja**: Control Agent może zareagować z opóźnieniem 500-1000ms
2. **Finalne transkrypcje**: Tylko finalne transkrypcje są przetwarzane
3. **Fail-safe**: W razie wątpliwości, Control Agent zwraca NO_ACTION i pozwala Gemini Live obsłużyć wypowiedź

### Debug Mode

Aby włączyć szczegółowe logi, dodaj w `ControlAgentManager.kt`:

```kotlin
companion object {
    private const val TAG = "ControlAgentManager"
    private const val DEBUG = true  // Włącz szczegółowe logi
}
```

Następnie przebuduj aplikację:
```bash
./gradlew clean build && ./gradlew installDebug
```

## Kontakt

Jeśli problem nadal występuje, zbierz logi i skontaktuj się z zespołem deweloperskim.
