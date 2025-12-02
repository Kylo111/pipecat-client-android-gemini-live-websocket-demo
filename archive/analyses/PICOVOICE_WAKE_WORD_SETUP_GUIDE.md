# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Source material - consolidated into /docs/guides/picovoice-setup.md
**Current Documentation:** See /docs/guides/picovoice-setup.md for current documentation

---

# Picovoice Wake Word Setup Guide

## Problem
Picovoice nie działa, ponieważ brakuje plików `.ppn` dla słów aktywacyjnych (start, stop, koniec).

## Logi pokazują błąd:
```
Failed to copy system wake word: start_pl.ppn
java.io.FileNotFoundException: picovoice/system/start_pl.ppn
```

## Rozwiązanie

### Krok 1: Utwórz konto Picovoice
1. Przejdź do: https://console.picovoice.ai
2. Zarejestruj się lub zaloguj
3. Skopiuj swój Access Key (będzie potrzebny później)

### Krok 2: Utwórz słowa aktywacyjne

Musisz utworzyć 3 słowa aktywacyjne w języku polskim:

#### A. Słowo "start"
1. W konsoli Picovoice przejdź do: **Porcupine** → **Wake Words**
2. Kliknij **"Create Wake Word"**
3. Wpisz: `start`
4. Wybierz język: **Polski (pl)**
5. Kliknij **"Train"** (trwa ~10 sekund)
6. Po zakończeniu treningu kliknij **"Download"**
7. Wybierz platformę: **Android**
8. Pobierz plik `.ppn`
9. Zmień nazwę pliku na: `start_pl.ppn`

#### B. Słowo "stop"
1. Powtórz kroki jak wyżej
2. Wpisz: `stop`
3. Wybierz język: **Polski (pl)**
4. Pobierz dla **Android**
5. Zmień nazwę na: `stop_pl.ppn`

#### C. Słowo "koniec"
1. Powtórz kroki jak wyżej
2. Wpisz: `koniec`
3. Wybierz język: **Polski (pl)**
4. Pobierz dla **Android**
5. Zmień nazwę na: `koniec_pl.ppn`

### Krok 3: Umieść pliki w projekcie

Skopiuj wszystkie 3 pliki `.ppn` do folderu:
```
gemini-multimodal-websocket-demo/src/main/assets/picovoice/system/
```

Struktura powinna wyglądać tak:
```
gemini-multimodal-websocket-demo/src/main/assets/picovoice/system/
├── README.md
├── start_pl.ppn
├── stop_pl.ppn
└── koniec_pl.ppn
```

### Krok 4: Zbuduj i zainstaluj aplikację

```bash
./gradlew clean build
./gradlew installDebug
```

### Krok 5: Włącz Picovoice w aplikacji

1. Otwórz aplikację
2. Przejdź do **Ustawień**
3. Znajdź sekcję **Picovoice**
4. Włącz **"Enable Picovoice"**
5. Jeśli masz własny Access Key, wprowadź go (opcjonalnie)

### Krok 6: Testowanie

Po włączeniu Picovoice powinieneś zobaczyć notyfikację:
```
Picovoice aktywny
Nasłuchiwanie 3 komend głosowych
```

Teraz możesz testować komendy:
- **"start"** lub **"stop"** - włącza/wyłącza mikrofon podczas rozmowy
- **"koniec"** - zamyka aplikację

## Sprawdzanie logów

Aby sprawdzić czy Picovoice działa:

```bash
adb -s EM95IBKZEYIFSO69 logcat -c
adb -s EM95IBKZEYIFSO69 logcat | grep -i "picovoice\|porcupine\|wakeword"
```

Powinieneś zobaczyć:
```
Loaded system wake word: start
Loaded system wake word: stop
Loaded system wake word: koniec
Porcupine initialized and started successfully
```

## Rozwiązywanie problemów

### Problem: "No wake words to load"
**Rozwiązanie**: Pliki `.ppn` nie są w folderze assets. Sprawdź czy skopiowałeś wszystkie 3 pliki.

### Problem: "PorcupineException"
**Rozwiązanie**: Sprawdź czy Access Key jest poprawny. Możesz go zmienić w ustawieniach aplikacji.

### Problem: Słowa nie są wykrywane
**Rozwiązanie**: 
1. Zwiększ czułość (sensitivity) w ustawieniach
2. Mów wyraźnie i głośno
3. Upewnij się, że mikrofon działa
4. Sprawdź czy aplikacja ma uprawnienia do mikrofonu

## Uwagi

- Pliki `.ppn` są specyficzne dla platformy (Android/iOS/Linux/etc.)
- Zawsze pobieraj wersję dla **Android**
- Słowa aktywacyjne są trenowane dla konkretnego języka
- Darmowe konto Picovoice ma limit użycia - sprawdź limity na konsoli
