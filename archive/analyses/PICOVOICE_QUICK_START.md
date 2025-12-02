# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Source material - consolidated into /docs/guides/picovoice-setup.md
**Current Documentation:** See /docs/guides/picovoice-setup.md for current documentation

---

# Picovoice - Szybki Start

## ✅ Picovoice działa!

Aplikacja została zaktualizowana i używa wbudowanego słowa kluczowego **"ALEXA"**.

## Jak używać

1. **Włącz Picovoice** w ustawieniach aplikacji
2. Zobaczysz notyfikację: "Picovoice aktywny - Nasłuchiwanie 1 komend głosowych"
3. Powiedz **"Alexa"** aby pauzować/wznawiać sesję (włączyć/wyłączyć mikrofon)

## Co zostało zmienione

- ❌ Usunięto komendę "koniec/terminator" (zamykanie aplikacji)
- ✅ Dodano jedną prostą komendę: **"ALEXA"** do pauzowania sesji
- ✅ Działa od razu bez potrzeby tworzenia plików `.ppn`
- ✅ Używa wbudowanego słowa kluczowego z biblioteki Picovoice

## Logi potwierdzające działanie

```
Using built-in wake word: ALEXA (toggle mic)
Loaded built-in system wake word: alexa
Porcupine initialized and started successfully
Wake word detected: alexa (SYSTEM)
Handling wake word: alexa (SYSTEM)
System command: alexa
```

## Opcjonalne: Własne słowa kluczowe

Jeśli chcesz używać polskich słów "start" i "stop" zamiast "Alexa":

1. Utwórz pliki `.ppn` w Picovoice Console (instrukcje w `PICOVOICE_WAKE_WORD_SETUP_GUIDE.md`)
2. Umieść je w: `gemini-multimodal-websocket-demo/src/main/assets/picovoice/system/`
3. Przebuduj aplikację

Aplikacja automatycznie wykryje pliki i użyje ich zamiast wbudowanego "Alexa".

## Testowanie

Sprawdź logi w czasie rzeczywistym:
```bash
adb -s EM95IBKZEYIFSO69 logcat | grep -i "picovoice\|porcupine\|alexa"
```

Powinieneś zobaczyć komunikaty o wykryciu słowa kluczowego gdy powiesz "Alexa".
