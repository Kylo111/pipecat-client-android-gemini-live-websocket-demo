# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Source material - consolidated into /docs/guides/picovoice-setup.md
**Current Documentation:** See /docs/guides/picovoice-setup.md for current documentation

---

# Picovoice - Wbudowane Słowa Kluczowe

## Lista wszystkich wbudowanych słów kluczowych (Built-in Keywords)

Picovoice Porcupine ma 14 wbudowanych słów kluczowych, które działają od razu bez potrzeby tworzenia plików `.ppn`:

1. **ALEXA** - "Alexa"
2. **AMERICANO** - "Americano"
3. **BLUEBERRY** - "Blueberry"
4. **BUMBLEBEE** - "Bumblebee"
5. **COMPUTER** - "Computer"
6. **GRAPEFRUIT** - "Grapefruit"
7. **GRASSHOPPER** - "Grasshopper"
8. **HEY_GOOGLE** - "Hey Google"
9. **HEY_SIRI** - "Hey Siri"
10. **JARVIS** - "Jarvis"
11. **OK_GOOGLE** - "OK Google"
12. **PICOVOICE** - "Picovoice"
13. **PORCUPINE** - "Porcupine"
14. **TERMINATOR** - "Terminator"

## Aktualnie używane słowa w aplikacji

Aplikacja została uproszczona do jednej komendy głosowej:

### Domyślne mapowanie (działa od razu):
- **"ALEXA"** → Pauzuje/wznawia sesję (toggle mikrofonu)

### Opcjonalne mapowanie (gdy dodasz pliki .ppn):
- **"start"** → Włącza mikrofon
- **"stop"** → Wyłącza mikrofon

## Jak to działa teraz

### Scenariusz 1: Bez plików .ppn (działa od razu) ✅
Aplikacja automatycznie użyje wbudowanego słowa:
- Powiedz **"Alexa"** aby pauzować/wznowić sesję (toggle mikrofonu)

### Scenariusz 2: Z plikami .ppn (opcjonalnie)
Jeśli dodasz pliki `start_pl.ppn` i `stop_pl.ppn` do folderu assets, aplikacja użyje ich zamiast wbudowanego słowa.

## Testowanie wbudowanych słów

1. Zbuduj i zainstaluj aplikację:
```bash
./gradlew clean build && ./gradlew installDebug
```

2. Włącz Picovoice w ustawieniach aplikacji

3. Sprawdź logi:
```bash
adb -s EM95IBKZEYIFSO69 logcat | grep -i "picovoice\|porcupine"
```

4. Powinieneś zobaczyć:
```
Using built-in wake word: ALEXA (toggle mic)
Loaded built-in system wake word: alexa
Porcupine initialized and started successfully
Nasłuchiwanie 1 komend głosowych
```

5. Testuj komendę:
   - Powiedz **"Alexa"** (wyraźnie, po angielsku)
   - Mikrofon powinien się włączyć/wyłączyć

## Dlaczego "ALEXA"?

Picovoice nie ma wbudowanych słów "start" i "stop" (za mało sylab). "Alexa" to:
- ✅ Wbudowane słowo - działa od razu bez plików .ppn
- ✅ Łatwe do wymówienia
- ✅ Dobrze rozpoznawane przez Picovoice
- ✅ Wystarczająco długie (3 sylaby)

### Opcja A: Użyj "Alexa" (działa od razu) ✅ ZALECANE
- Brak konieczności tworzenia plików
- Działa natychmiast po włączeniu Picovoice
- Wystarczy powiedzieć "Alexa" aby pauzować/wznawiać sesję

### Opcja B: Utwórz własne słowa w Picovoice Console (opcjonalnie)
- Możesz utworzyć dowolne słowa, w tym polskie
- Słowa będą lepiej dopasowane do Twojego języka
- Instrukcje w pliku: `PICOVOICE_WAKE_WORD_SETUP_GUIDE.md`

## Uwagi techniczne

- Wbudowane słowa są trenowane tylko dla języka angielskiego
- Działają najlepiej z wyraźną wymową angielską
- Nie wymagają plików `.ppn` - są wbudowane w bibliotekę Picovoice
- Aplikacja automatycznie wykrywa czy są dostępne pliki `.ppn` i wybiera odpowiednie słowa
