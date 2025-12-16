# Requirements Document

## Introduction

Refaktoryzacja ekranu ustawień (SettingsScreen) z jednego długiego scrollowalnego widoku na system zakładek (tabs). Zmiana organizacji ustawień na logiczne sekcje oraz zmiana logiki logowania - użytkownik może korzystać z aplikacji bez logowania do Kumpel-chat (tylko konwersacje offline).

## Glossary

- **SettingsScreen**: Główny ekran ustawień aplikacji
- **Tab**: Zakładka w interfejsie użytkownika pozwalająca na przełączanie między sekcjami
- **Kumpel-chat**: Nowa nazwa dla integracji z LibreChat
- **Konwersacje offline**: Lokalne konwersacje przechowywane na urządzeniu, działające bez połączenia z serwerem
- **Klucze API**: Tokeny autoryzacyjne do zewnętrznych usług (Gemini, Perplexity, OpenRouter, Picovoice, Telegram)
- **Agent sterowania głosowego**: Moduł rozpoznający komendy głosowe do sterowania aplikacją
- **Reasoning Agent**: Agent rozumujący wykonujący głębokie analizy w tle
- **Picovoice**: Usługa wykrywania komend głosowych (wake words)

## Requirements

### Requirement 1

**User Story:** Jako użytkownik, chcę mieć ustawienia podzielone na zakładki, żeby łatwiej nawigować między różnymi kategoriami ustawień.

#### Acceptance Criteria

1. WHEN użytkownik otwiera ekran ustawień THEN system SHALL wyświetlić pasek zakładek na górze ekranu z możliwością przewijania poziomego
2. WHEN użytkownik klika na zakładkę THEN system SHALL wyświetlić zawartość wybranej zakładki i oznaczyć ją jako aktywną
3. WHEN użytkownik przewija zawartość zakładki THEN system SHALL zachować pozycję przewijania dla każdej zakładki osobno
4. THE system SHALL zawierać następujące zakładki w kolejności: "Klucze i konta", "Sesja i wygląd", "Agenci", "Integracje"

### Requirement 2

**User Story:** Jako użytkownik, chcę mieć wszystkie klucze API i logowanie do Kumpel-chat w jednej zakładce "Klucze i konta", żeby łatwo zarządzać dostępem do usług.

#### Acceptance Criteria

1. WHEN użytkownik otwiera zakładkę "Klucze i konta" THEN system SHALL wyświetlić sekcję "Klucze API" z polami: klucz Gemini API, nazwa modelu Gemini, klucz Perplexity API, klucz OpenRouter API, klucz Picovoice, token Telegram, ID czatu Telegram
2. WHEN użytkownik wprowadza klucz API THEN system SHALL walidować format klucza przed zapisaniem
3. WHEN użytkownik zapisuje ustawienia z pustym kluczem Gemini THEN system SHALL wyświetlić ostrzeżenie że klucz jest wymagany do pełnej funkcjonalności
4. WHEN użytkownik zapisuje ustawienia z nieprawidłowym kluczem THEN system SHALL wyświetlić komunikat o błędzie walidacji
5. WHEN użytkownik klika przycisk "Importuj z JSON" THEN system SHALL otworzyć selektor plików do wyboru pliku JSON z kluczami
6. WHEN użytkownik wybiera prawidłowy plik JSON z kluczami THEN system SHALL zaimportować wszystkie klucze i wypełnić odpowiednie pola
7. WHEN plik JSON zawiera nieprawidłowy format THEN system SHALL wyświetlić komunikat o błędzie z opisem problemu
8. THE format pliku JSON SHALL zawierać pola: geminiApiKey, modelName, perplexityApiKey, openRouterApiKey, picovoiceAccessKey, telegramBotToken, telegramChatId

### Requirement 3

**User Story:** Jako użytkownik, chcę mieć logowanie do Kumpel-chat w zakładce "Klucze i konta", żeby zarządzać synchronizacją konwersacji.

#### Acceptance Criteria

1. WHEN użytkownik otwiera zakładkę "Klucze i konta" THEN system SHALL wyświetlić sekcję "Kumpel-chat" z formularzem logowania (email/hasło) jeśli użytkownik nie jest zalogowany
2. WHEN użytkownik jest zalogowany do Kumpel-chat THEN system SHALL wyświetlić status połączenia, przycisk wylogowania oraz ustawienia trybu podsumowania
3. WHEN użytkownik nie wprowadzi danych logowania THEN system SHALL pozwolić na korzystanie z aplikacji tylko z konwersacjami offline
4. WHEN użytkownik loguje się do Kumpel-chat THEN system SHALL zapisać dane uwierzytelniające i włączyć synchronizację konwersacji
5. WHEN użytkownik wylogowuje się z Kumpel-chat THEN system SHALL zachować lokalne konwersacje offline i wyłączyć synchronizację

### Requirement 4

**User Story:** Jako użytkownik, chcę mieć ustawienia sesji, trybu audio i preferencji wizualnych w zakładce "Sesja i wygląd", żeby konfigurować zachowanie aplikacji.

#### Acceptance Criteria

1. WHEN użytkownik otwiera zakładkę "Sesja i wygląd" THEN system SHALL wyświetlić sekcje: Zarządzanie sesją, Tryb audio, Preferencje wizualne, Bezpieczeństwo
2. THE sekcja Zarządzanie sesją SHALL zawierać: przełącznik utrzymywania ekranu, slider auto-pause, slider timeout bota, slider czułości wykrywania
3. THE sekcja Tryb audio SHALL zawierać przełącznik Full-Duplex z opisem różnic między trybami
4. THE sekcja Preferencje wizualne SHALL zawierać wybór motywu i skórki
5. THE sekcja Bezpieczeństwo SHALL zawierać przełącznik blokady przed dziećmi i przycisk zmiany PIN

### Requirement 5

**User Story:** Jako użytkownik, chcę mieć ustawienia agentów w zakładce "Agenci", żeby konfigurować agenta sterowania i agenta rozumującego.

#### Acceptance Criteria

1. WHEN użytkownik otwiera zakładkę "Agenci" THEN system SHALL wyświetlić sekcję "Agent sterowania głosowego" z przełącznikiem włączenia i opisem funkcji
2. WHEN agent sterowania jest włączony THEN system SHALL wyświetlić status aktywności agenta
3. THE sekcja "Agent sterowania głosowego" SHALL zawierać informacje o obsługiwanych komendach głosowych
4. WHEN użytkownik otwiera zakładkę "Agenci" THEN system SHALL wyświetlić sekcję "Agent rozumujący" z przełącznikiem włączenia i opisem funkcji
5. WHEN agent rozumujący jest włączony THEN system SHALL wyświetlić wybór modelu i przełącznik trybu Whisperer
6. THE sekcja "Agent rozumujący" SHALL zawierać informacje o wymaganych kluczach API (OpenRouter, Perplexity)

### Requirement 6

**User Story:** Jako użytkownik, chcę mieć ustawienia integracji w zakładce "Integracje", żeby zarządzać Picovoice i Telegram.

#### Acceptance Criteria

1. WHEN użytkownik otwiera zakładkę "Integracje" THEN system SHALL wyświetlić sekcję "Picovoice" z przełącznikiem włączenia i sliderem czułości
2. THE sekcja "Picovoice" SHALL zawierać listę systemowych komend głosowych
3. THE sekcja "Picovoice" SHALL zawierać możliwość dodawania własnych komend głosowych z importem plików .ppn
4. WHEN użytkownik włącza Picovoice bez klucza dostępu THEN system SHALL wyświetlić komunikat o konieczności wprowadzenia klucza w zakładce "Klucze i konta"
5. WHEN użytkownik otwiera zakładkę "Integracje" THEN system SHALL wyświetlić sekcję "Telegram" z polami: token bota i ID czatu
6. THE sekcja "Telegram" SHALL zawierać przycisk testowania połączenia
7. THE sekcja "Telegram" SHALL zawierać instrukcje konfiguracji bota Telegram
8. WHEN użytkownik otwiera zakładkę "Integracje" THEN system SHALL wyświetlić sekcję "Custom Tools" z możliwością importu i zarządzania własnymi narzędziami

### Requirement 7

**User Story:** Jako użytkownik, chcę móc korzystać z aplikacji bez logowania do Kumpel-chat, żeby używać konwersacji offline.

#### Acceptance Criteria

1. WHEN aplikacja uruchamia się bez zapisanych danych logowania THEN system SHALL wyświetlić ekran główny z możliwością tworzenia konwersacji offline
2. WHEN użytkownik nie jest zalogowany do Kumpel-chat THEN system SHALL ukryć opcje synchronizacji i wyświetlić tylko konwersacje offline
3. WHEN użytkownik tworzy konwersację bez logowania THEN system SHALL zapisać ją lokalnie jako konwersację offline
4. THE system SHALL wyświetlić informację o możliwości zalogowania się do Kumpel-chat w zakładce "Klucze i konta"

### Requirement 8 (REMOVED)

**Status:** REMOVED - Niepotrzebny globalny przycisk wylogowania

**Rationale:** Logowanie i wylogowanie z Kumpel-chat jest zarządzane w zakładce "Klucze i konta". Nie ma potrzeby globalnego przycisku wylogowania z aplikacji, ponieważ:
- Użytkownik może korzystać z aplikacji bez logowania (tryb offline)
- Wylogowanie z Kumpel-chat jest dostępne w odpowiedniej sekcji
- Globalny przycisk "Wyloguj z aplikacji" jest mylący i niepotrzebny
