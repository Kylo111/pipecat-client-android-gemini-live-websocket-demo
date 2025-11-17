# Instrukcje testowania naprawy Picovoice przy wyłączonym ekranie

## Zaimplementowane zmiany

### 1. Automatyczne zatrzymywanie PorcupineService podczas aktywnej sesji
- Gdy rozpoczyna się sesja głosowa (`VoiceClientManager.start()`), PorcupineService jest automatycznie zatrzymywany
- Eliminuje to konflikt AudioRecord między dwoma serwisami
- Zapobiega problemom z dostępem do mikrofonu przy wyłączonym ekranie

### 2. Automatyczne wznawianie PorcupineService po zakończeniu sesji
- Gdy sesja się kończy (`VoiceClientManager.stop()`), PorcupineService jest automatycznie wznawiany
- Przywraca funkcjonalność wake word detection po zakończeniu rozmowy
- Użytkownik może ponownie używać wake words do uruchamiania nowych sesji

### 3. Detekcja konfliktu AudioRecord
- Dodano weryfikację stanu AudioRecord po uruchomieniu
- Jeśli AudioRecord nie może się uruchomić (konflikt z innym serwisem), błąd jest wykrywany natychmiast
- Logowanie szczegółowe pomaga w diagnostyce problemów

## Scenariusze testowe

### Test 1: Podstawowa sesja z wyłączonym ekranem (KRYTYCZNY)

**Cel**: Sprawdzić czy sesja przetrwa przy wyłączonym ekranie z włączonym Picovoice

**Kroki**:
1. Włącz Picovoice w ustawieniach
2. Uruchom aplikację
3. Zaloguj się do LibreChat
4. Wybierz konwersację i rozpocznij sesję
5. **Wyłącz ekran telefonu**
6. Rozmawiaj z botem przez 5 minut
7. Włącz ekran

**Oczekiwany rezultat**:
- ✅ Sesja pozostaje aktywna przez cały czas
- ✅ Aplikacja pokazuje ekran rozmowy (IN_CALL)
- ✅ Kontekst rozmowy jest zachowany
- ✅ Można kontynuować rozmowę
- ✅ W logach: "🛑 Stopping PorcupineService to prevent AudioRecord conflict"
- ✅ W logach: "✅ AudioRecord started successfully"

**Poprzedni problem**:
- ❌ Sesja kończyła się
- ❌ Aplikacja wracała do listy konwersacji
- ❌ Kontekst rozmowy był tracony

### Test 2: Długa sesja z wyłączonym ekranem

**Cel**: Sprawdzić stabilność podczas długiej sesji

**Kroki**:
1. Włącz Picovoice
2. Rozpocznij sesję
3. Wyłącz ekran
4. Rozmawiaj przez 30 minut
5. Włącz ekran

**Oczekiwany rezultat**:
- ✅ Sesja aktywna przez cały czas
- ✅ Brak crashy
- ✅ Brak memory leaks
- ✅ Transkrypty są zapisywane

### Test 3: Wake word po zakończeniu sesji

**Cel**: Sprawdzić czy PorcupineService wraca po zakończeniu sesji

**Kroki**:
1. Włącz Picovoice
2. Rozpocznij sesję (PorcupineService powinien się zatrzymać)
3. Zakończ sesję
4. Poczekaj 5 sekund
5. Powiedz wake word (np. "Alexa")

**Oczekiwany rezultat**:
- ✅ Po zakończeniu sesji w logach: "🔄 Restarting PorcupineService after session ended"
- ✅ Wake word jest wykrywany
- ✅ Nowa sesja może być uruchomiona

### Test 4: Przełączanie między sesjami

**Cel**: Sprawdzić czy PorcupineService jest prawidłowo zarządzany

**Kroki**:
1. Włącz Picovoice
2. Rozpocznij sesję A
3. Zakończ sesję A
4. Poczekaj 2 sekundy
5. Rozpocznij sesję B
6. Zakończ sesję B

**Oczekiwany rezultat**:
- ✅ Każda sesja zatrzymuje PorcupineService
- ✅ Po każdym zakończeniu PorcupineService wraca
- ✅ Brak konfliktów AudioRecord

### Test 5: Memory pressure z Picovoice

**Cel**: Sprawdzić czy fix działa przy niskiej pamięci

**Kroki**:
1. Włącz Picovoice
2. Rozpocznij sesję
3. Wyłącz ekran
4. Otwórz wiele innych aplikacji (Chrome, YouTube, etc.)
5. Wróć do aplikacji

**Oczekiwany rezultat**:
- ✅ Sesja przetrwa
- ✅ Jeśli system zabije PorcupineService, sesja nadal działa
- ✅ W logach brak błędów AudioRecord conflict

### Test 6: Bez Picovoice (regresja)

**Cel**: Sprawdzić czy zmiany nie zepsuły działania bez Picovoice

**Kroki**:
1. Wyłącz Picovoice w ustawieniach
2. Rozpocznij sesję
3. Wyłącz ekran
4. Rozmawiaj przez 5 minut

**Oczekiwany rezultat**:
- ✅ Sesja działa normalnie
- ✅ W logach: "Picovoice is disabled, no need to stop PorcupineService"
- ✅ Brak prób zatrzymania/wznawiania PorcupineService

### Test 7: Tool call z wyłączonym ekranem

**Cel**: Sprawdzić czy tool calls działają przy wyłączonym ekranie

**Kroki**:
1. Włącz Picovoice
2. Rozpocznij sesję
3. Wyłącz ekran
4. Poproś bota o wykonanie tool call (np. "search web for weather")
5. Poczekaj na wynik
6. Włącz ekran

**Oczekiwany rezultat**:
- ✅ Tool call wykonuje się poprawnie
- ✅ Sesja pozostaje aktywna
- ✅ Wynik tool call jest zwrócony do bota
- ✅ Transkrypty są zapisywane

## Komendy do monitorowania logów

### Podstawowe logi
```bash
adb -s EM95IBKZEYIFSO69 logcat -c && adb -s EM95IBKZEYIFSO69 logcat | grep -E "VoiceClientManager|PorcupineService|AudioRecord"
```

### Logi Picovoice
```bash
adb -s EM95IBKZEYIFSO69 logcat | grep -E "Stopping PorcupineService|Restarting PorcupineService|AudioRecord"
```

### Logi lifecycle
```bash
adb -s EM95IBKZEYIFSO69 logcat | grep -E "MainActivity.*Lifecycle|VoiceService|handlePause|handleResume"
```

### Logi błędów
```bash
adb -s EM95IBKZEYIFSO69 logcat | grep -E "ERROR|FATAL|AudioRecord conflict|failed to start"
```

## Kluczowe logi do sprawdzenia

### Przy starcie sesji:
```
VoiceClientManager: 🛑 Stopping PorcupineService to prevent AudioRecord conflict during active session
VoiceClientManager: ✅ PorcupineService stopped successfully
VoiceClientManager: ✅ AudioRecord started successfully - state: 3
```

### Przy zakończeniu sesji:
```
VoiceClientManager: 🔄 Restarting PorcupineService after session ended
VoiceClientManager: ✅ PorcupineService restart requested
```

### Jeśli wystąpi konflikt (NIE POWINNO SIĘ ZDARZYĆ):
```
VoiceClientManager: ❌ AudioRecord failed to start - state: X (expected: 3)
VoiceClientManager: AudioRecord failed to start - may be in use by another service (PorcupineService?)
```

## Metryki sukcesu

Po wszystkich testach:
- ✅ 0 przypadków zakończenia sesji przy wyłączonym ekranie
- ✅ 0 konfliktów AudioRecord
- ✅ 100% sesji zachowuje kontekst rozmowy
- ✅ PorcupineService prawidłowo zatrzymywany/wznawiany
- ✅ Wake words działają po zakończeniu sesji

## Rollback plan

Jeśli fix powoduje problemy:

1. Przywróć poprzednią wersję VoiceClientManager.kt:
```bash
git checkout HEAD~1 gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/VoiceClientManager.kt
```

2. Lub wyłącz Picovoice w ustawieniach aplikacji

## Dodatkowe uwagi

- Fix nie wpływa na działanie bez Picovoice
- Fix nie zmienia logiki lifecycle MainActivity
- Fix nie zmienia logiki VoiceService
- Fix jest minimalny i skupiony tylko na konflikcie AudioRecord
