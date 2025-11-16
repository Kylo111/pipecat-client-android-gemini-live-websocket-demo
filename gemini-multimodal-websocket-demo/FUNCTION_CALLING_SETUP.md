# Function Calling Setup Guide

Aplikacja obsługuje 8 funkcji (tools), które Gemini Live może wywoływać podczas rozmowy:

## 🛠️ Dostępne funkcje

### 1. **search_web** - Wyszukiwanie w internecie
- Wyszukuje aktualne informacje w internecie
- Przykład: "Wyszukaj najnowsze wiadomości o AI"

### 2. **get_weather** - Pogoda
- Pobiera aktualną pogodę dla lokalizacji
- Przykład: "Jaka jest pogoda w Warszawie?"

### 3. **get_current_time** - Czas rzeczywisty
- Zwraca aktualną datę i czas
- Przykład: "Która jest godzina?"

### 4. **get_location** - Lokalizacja użytkownika
- Pobiera aktualną lokalizację GPS
- Przykład: "Gdzie jestem?"

### 5. **calculate** - Kalkulator
- Wykonuje obliczenia matematyczne
- Przykład: "Oblicz 15% z 250"

### 6. **create_note** - Tworzenie notatek
- Tworzy notatkę w aplikacji (Keep, Evernote, Notion)
- Przykład: "Stwórz notatkę: kupić mleko"

### 7. **control_media** - Sterowanie multimediami
- Kontroluje Spotify, YouTube Music
- Przykład: "Włącz muzykę", "Następny utwór"

### 8. **search_nearby** - Wyszukiwanie w pobliżu
- Znajduje miejsca w okolicy
- Przykład: "Znajdź najbliższą aptekę"

## 🔑 Konfiguracja API Keys

Aby funkcje działały, musisz skonfigurować klucze API w pliku:
`gemini-multimodal-websocket-demo/src/main/java/ai/pipecat/gemini_multimodal_websocket_demo/tools/ToolExecutor.kt`

### 1. Serper API (wyszukiwanie w internecie)

```kotlin
private const val SERPER_API_KEY = "YOUR_SERPER_API_KEY"
```

**Jak uzyskać:**
1. Przejdź na https://serper.dev
2. Zarejestruj się (darmowy plan: 2500 zapytań/miesiąc)
3. Skopiuj API key z dashboardu
4. Wklej do `SERPER_API_KEY`

### 2. OpenWeatherMap API (pogoda)

```kotlin
private const val OPENWEATHER_API_KEY = "YOUR_OPENWEATHER_API_KEY"
```

**Jak uzyskać:**
1. Przejdź na https://openweathermap.org/api
2. Zarejestruj się (darmowy plan: 1000 zapytań/dzień)
3. Wygeneruj API key w sekcji "API keys"
4. Wklej do `OPENWEATHER_API_KEY`

### 3. Google Places API (wyszukiwanie w pobliżu)

```kotlin
private const val GOOGLE_PLACES_API_KEY = "YOUR_GOOGLE_PLACES_API_KEY"
```

**Jak uzyskać:**
1. Przejdź na https://console.cloud.google.com
2. Utwórz nowy projekt lub wybierz istniejący
3. Włącz "Places API" w bibliotece API
4. Utwórz credentials (API key)
5. Wklej do `GOOGLE_PLACES_API_KEY`

**Uwaga:** Google Places API wymaga karty kredytowej, ale oferuje $200 darmowych kredytów miesięcznie.

## 📱 Uprawnienia

Aplikacja automatycznie prosi o następujące uprawnienia:

- **Lokalizacja** (ACCESS_FINE_LOCATION) - dla `get_location` i `search_nearby`
- **Mikrofon** (RECORD_AUDIO) - już skonfigurowane
- **Internet** (INTERNET) - już skonfigurowane

## 🚀 Funkcje działające bez API keys

Następujące funkcje działają od razu bez konfiguracji:

- ✅ **get_current_time** - czas systemowy
- ✅ **calculate** - obliczenia lokalne
- ✅ **create_note** - używa Android Intent
- ✅ **control_media** - używa Android Media Controls
- ✅ **get_location** - używa Google Play Services (już w projekcie)

## 🧪 Testowanie

Po skonfigurowaniu API keys, zbuduj i zainstaluj aplikację:

```bash
./gradlew clean build
./gradlew installDebug
```

Następnie podczas rozmowy z Gemini, wypróbuj:

1. **"Wyszukaj najnowsze wiadomości o SpaceX"** - test search_web
2. **"Jaka jest pogoda w Krakowie?"** - test get_weather
3. **"Która jest godzina?"** - test get_current_time
4. **"Gdzie jestem?"** - test get_location
5. **"Oblicz pierwiastek z 144"** - test calculate
6. **"Stwórz notatkę: spotkanie o 15:00"** - test create_note
7. **"Włącz muzykę"** - test control_media
8. **"Znajdź najbliższą kawiarnie"** - test search_nearby

## 🔍 Debugowanie

Włącz szczegółowe logi w `VoiceClientManager.kt`:

```kotlin
private const val DEBUG_LOGGING = true
```

Następnie sprawdź logi:

```bash
adb -s EM95IBKZEYIFSO69 logcat | grep -i "ToolExecutor\|VoiceClientManager"
```

## 📝 Notatki

- Funkcje są wywoływane automatycznie przez Gemini gdy uzna to za stosowne
- Nie musisz używać specjalnych komend - po prostu rozmawiaj naturalnie
- Gemini sam zdecyduje kiedy użyć funkcji na podstawie kontekstu rozmowy
- Wyniki funkcji są przekazywane z powrotem do Gemini, który je interpretuje i odpowiada głosowo

## 🔒 Bezpieczeństwo

**WAŻNE:** Nie commituj API keys do repozytorium!

Dla produkcji, użyj:
- Android BuildConfig
- Zmiennych środowiskowych
- Lub serwera backend do proxy API calls

Przykład z BuildConfig:

```kotlin
// build.gradle.kts
android {
    defaultConfig {
        buildConfigField("String", "SERPER_API_KEY", "\"${System.getenv("SERPER_API_KEY")}\"")
    }
}

// ToolExecutor.kt
private const val SERPER_API_KEY = BuildConfig.SERPER_API_KEY
```

## 🆘 Pomoc

Jeśli funkcja nie działa:

1. Sprawdź logi: `adb logcat | grep ToolExecutor`
2. Upewnij się że API key jest poprawny
3. Sprawdź czy masz uprawnienia (lokalizacja dla get_location/search_nearby)
4. Sprawdź limity API (czy nie przekroczyłeś darmowego planu)

## 🎯 Rozszerzanie

Aby dodać własną funkcję:

1. Dodaj definicję w `ToolDefinitions.kt`
2. Dodaj implementację w `ToolExecutor.kt`
3. Gemini automatycznie będzie mógł jej używać!
