# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Task completed - historical record
**Current Documentation:** See /docs/implementation/ or /docs/operations/ for current documentation

---

# Instrukcje Debugowania "Ponowne łączenie..."

## Jak Przechwycić Problem

### 1. Logi zostały wyczyszczone
```bash
adb -s EM95IBKZEYIFSO69 logcat -c
```

### 2. Teraz wywołaj problem
- Rozpocznij rozmowę
- Poczekaj aż model zacznie mówić
- **Problem pojawia się podczas wypowiadania modelu**
- Słyszysz sygnał dźwiękowy (jak Picovoice)
- Pojawia się "Ponowne łączenie..."

### 3. Gdy pojawi się "Ponowne łączenie..." - NATYCHMIAST uruchom:

```bash
# Zapisz logi do pliku
adb -s EM95IBKZEYIFSO69 logcat -d > reconnecting_logs.txt

# LUB pokaż w konsoli
adb -s EM95IBKZEYIFSO69 logcat -d | grep -E "VoiceClientManager|Picovoice|Porcupine|WebSocket|Reconnection|🔄|🔍|🚨|⚠️|RECONNECTING"
```

## Czego Szukamy

### 1. Czy startReconnection() jest wywoływany?
```
🔄 Starting reconnection process (max 3 attempts, 10s timeout)
```

### 2. Czy auto-restart job startuje?
```
🔍 DEBUG: Launching auto-restart monitor job
🔍 DEBUG: Auto-restart job started, waiting 5s...
```

### 3. Czy jest konflikt z Picovoice?
```
Picovoice
Porcupine
Wake word detected
```

### 4. Czy WebSocket się zamyka?
```
WebSocket closed
WebSocket failure
onClosed
onFailure
```

### 5. Czy jest auto-restart po 5 sekundach?
```
🔍 DEBUG: 5s passed, checking state...
⚠️ Still reconnecting after 5s - triggering automatic restart
🚨🚨🚨 AUTOMATIC RESTART TRIGGERED! 🚨🚨🚨
```

## Możliwe Przyczyny

### Hipoteza 1: Picovoice Wywołuje Problem
- Sygnał dźwiękowy = Picovoice wykrywa wake word
- Picovoice próbuje pause session
- Ale zamiast pause jest RECONNECTING

**Sprawdź:**
```bash
adb -s EM95IBKZEYIFSO69 logcat -d | grep -E "Picovoice|Porcupine|Wake word"
```

### Hipoteza 2: WebSocket Zamyka Się Podczas Bot Audio
- Bot mówi → AudioRecord jest stopped
- WebSocket dostaje jakiś błąd
- Przechodzi w RECONNECTING

**Sprawdź:**
```bash
adb -s EM95IBKZEYIFSO69 logcat -d | grep -E "Bot.*speaking|AudioRecord.*stop|WebSocket.*close"
```

### Hipoteza 3: startReconnection() Nie Jest Wywoływany
- Stan RECONNECTING jest ustawiony
- Ale startReconnection() nie jest wywoływany
- Brak auto-restart job

**Sprawdź:**
```bash
adb -s EM95IBKZEYIFSO69 logcat -d | grep -E "Starting reconnection|auto-restart"
```

### Hipoteza 4: Scope Jest Null
- `scope?.launch {}` nie działa bo scope = null
- startReconnection() nie jest wykonywany

**Sprawdź:**
```bash
adb -s EM95IBKZEYIFSO69 logcat -d | grep -E "scope|Scope"
```

## Co Zrobić Dalej

### Jeśli Brak Logów "🔄 Starting reconnection":
→ startReconnection() NIE jest wywoływany
→ Trzeba znaleźć gdzie ustawiany jest stan RECONNECTING bez wywołania startReconnection()

### Jeśli Jest "Starting reconnection" Ale Brak "🔍 DEBUG":
→ auto-restart job nie startuje
→ Problem z `launch {}` wewnątrz startReconnection()

### Jeśli Jest "🔍 DEBUG" Ale Brak "🚨 AUTOMATIC RESTART":
→ auto-restart job działa ale warunek nie jest spełniony
→ Stan zmienia się przed 5 sekundami

### Jeśli Jest Picovoice w Logach:
→ Picovoice wywołuje problem
→ Trzeba naprawić interakcję Picovoice ↔ VoiceClientManager

## Komenda Do Uruchomienia

```bash
# Wyczyść logi
adb -s EM95IBKZEYIFSO69 logcat -c

# Wywołaj problem (model mówi → "Ponowne łączenie...")

# Natychmiast zapisz logi
adb -s EM95IBKZEYIFSO69 logcat -d > reconnecting_problem_$(date +%Y%m%d_%H%M%S).txt

# Pokaż kluczowe linie
adb -s EM95IBKZEYIFSO69 logcat -d | grep -E "VoiceClientManager|Picovoice|WebSocket|Reconnection|🔄|🔍|🚨"
```

## Prześlij Mi Logi

Gdy problem się pojawi, prześlij mi:
1. Pełne logi z momentu problemu
2. Opis co się stało (model mówił, sygnał dźwiękowy, etc.)
3. Czy był jakiś tekst w UI przed "Ponowne łączenie..."
