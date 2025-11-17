# Picovoice - Rozwiązywanie Problemów

## Problem: "Alexa" nie działa

### Krok 1: Sprawdź czy Picovoice jest włączony

1. Otwórz aplikację
2. Przejdź do **Ustawień** (Settings)
3. Znajdź sekcję **"Picovoice Wake Word Detection"**
4. Upewnij się, że przełącznik **"Enable Picovoice"** jest włączony (ON)

### Krok 2: Sprawdź notyfikację

Po włączeniu Picovoice powinieneś zobaczyć notyfikację:
```
Picovoice aktywny
Nasłuchiwanie 1 komend głosowych
```

Jeśli nie widzisz tej notyfikacji, Picovoice nie działa.

### Krok 3: Sprawdź uprawnienia

Aplikacja potrzebuje uprawnienia do mikrofonu:
1. Przejdź do ustawień telefonu
2. Aplikacje → Gemini Multimodal Demo
3. Uprawnienia → Mikrofon
4. Ustaw na **"Zezwalaj zawsze"** lub **"Zezwalaj tylko podczas używania aplikacji"**

### Krok 4: Sprawdź logi (dla deweloperów)

```bash
# Wyczyść logi
adb -s EM95IBKZEYIFSO69 logcat -c

# Monitoruj logi Picovoice
adb -s EM95IBKZEYIFSO69 logcat | grep -i "picovoice\|porcupine\|alexa"
```

Powinieneś zobaczyć:
```
Using built-in wake word: ALEXA (toggle mic)
Loaded built-in system wake word: alexa
Porcupine initialized and started successfully
```

### Krok 5: Testuj wykrywanie

1. Upewnij się, że jesteś w aktywnej rozmowie (połączony z Gemini)
2. Powiedz wyraźnie: **"Alexa"** (po angielsku)
3. Sprawdź czy sesja się pauzuje/wznawia

### Typowe problemy

#### Problem: Brak notyfikacji Picovoice
**Rozwiązanie**: 
- Włącz Picovoice w ustawieniach aplikacji
- Zrestartuj aplikację

#### Problem: "Alexa" nie jest wykrywana
**Rozwiązanie**:
- Mów wyraźnie i głośno
- Wymów "Alexa" po angielsku (nie "Aleksa")
- Zwiększ czułość (sensitivity) w ustawieniach Picovoice
- Sprawdź czy mikrofon działa (nagraj coś w innej aplikacji)

#### Problem: Wielokrotne przełączanie
**Rozwiązanie**:
- To normalne - Picovoice wykrywa słowo kilka razy podczas wymowy
- Każde wykrycie przełącza stan: ON→OFF→ON→OFF
- Możesz dodać debouncing (opóźnienie między przełączeniami)

#### Problem: Picovoice nie startuje po restarcie telefonu
**Rozwiązanie**:
- Włącz opcję "Start on boot" w ustawieniach Picovoice
- Sprawdź czy aplikacja ma uprawnienie "Autostart" w ustawieniach telefonu

### Sprawdzenie czy Picovoice jest włączony (terminal)

```bash
# Sprawdź czy plik konfiguracyjny istnieje
adb -s EM95IBKZEYIFSO69 shell "run-as ai.pipecat.gemini_multimodal_websocket_demo ls shared_prefs/"

# Sprawdź zawartość (jeśli istnieje)
adb -s EM95IBKZEYIFSO69 shell "run-as ai.pipecat.gemini_multimodal_websocket_demo cat shared_prefs/picovoice_prefs.xml"
```

Powinieneś zobaczyć:
```xml
<boolean name="picovoice_enabled" value="true" />
```

### Resetowanie Picovoice

Jeśli nic nie działa:
1. Wyłącz Picovoice w ustawieniach
2. Zamknij aplikację całkowicie
3. Wyczyść cache aplikacji (opcjonalnie)
4. Otwórz aplikację ponownie
5. Włącz Picovoice w ustawieniach

## Kontakt

Jeśli problem nadal występuje, sprawdź logi i zgłoś błąd z pełnymi logami.
