# STATUS: ARCHIVED

**Archived Date:** 2025-12-01
**Reason:** Task completed - historical record
**Current Documentation:** See /docs/testing/ or /docs/implementation/ for current documentation

---

# Test "Alexa" Wake Word

## Status
✅ Picovoice jest włączony i działa
✅ Wbudowane słowo "ALEXA" jest załadowane
✅ Settings Screen zaktualizowany - pokazuje tylko "ALEXA"
✅ Metoda `toggleMic()` przywrócona - pauzuje/wznawia sesję

## Jak przetestować

1. **Uruchom aplikację i połącz się z Gemini**
2. **Sprawdź czy Picovoice działa:**
   ```bash
   adb -s EM95IBKZEYIFSO69 logcat | grep -i "picovoice\|porcupine\|alexa\|toggle"
   ```

3. **Powiedz "Alexa" podczas rozmowy**
   - Mów wyraźnie po angielsku: "Alexa" (nie "Aleksa")
   - Sesja powinna się pauzować (websocket zamyka się)
   - Powiedz ponownie "Alexa" - sesja powinna się wznowić

## Oczekiwane logi

Gdy powiesz "Alexa":
```
Wake word detected: alexa (SYSTEM)
Handling wake word: alexa (SYSTEM)
System command: alexa
Sending toggle microphone broadcast
Toggle microphone broadcast received
🎤 Toggle microphone - Current state: ON
Mic enabled - resuming session  (lub "Mic disabled - pausing session")
```

## Co zostało naprawione

1. ✅ Przywrócono oryginalną metodę `toggleMic()` która wywołuje `enableMic()`
2. ✅ `enableMic()` pauzuje/wznawia całą sesję (websocket), nie tylko mikrofon
3. ✅ Settings Screen pokazuje tylko "ALEXA" zamiast "start/stop/koniec"
4. ✅ Picovoice działa w tle i nasłuchuje "Alexa"

## Jeśli nie działa

1. Sprawdź czy Picovoice jest włączony w Settings
2. Sprawdź czy widzisz notyfikację "Picovoice aktywny"
3. Sprawdź logi - czy Picovoice wykrywa słowo
4. Zwiększ czułość (sensitivity) w Settings jeśli słowo nie jest wykrywane
