# Requirements Document

## Introduction

System integracji aplikacji Android z platformą LibreChat, umożliwiający zarządzanie sesjami nauki z kontekstem. Aplikacja będzie łączyć się z kontem użytkownika w LibreChat, pobierać listę wątków konwersacji (np. MATEMATYKA, BIOLOGIA), przygotowywać kontekst nauki z wykorzystaniem agenta LibreChat i RAG, oraz synchronizować podsumowania sesji z powrotem do LibreChat. Rozwiązanie wykorzystuje lokalne przechowywanie kontekstu sesji bez potrzeby Redis czy dodatkowego backendu.

## Glossary

- **Android_App**: Aplikacja mobilna na Androida oparta na Pipecat z integracją Gemini Live API
- **LibreChat**: Platforma do zarządzania konwersacjami AI z agentami i długoterminową pamięcią
- **Session_Context**: Lokalny kontekst sesji nauki przechowywany w pamięci aplikacji Android podczas trwania sesji
- **Conversation_Thread**: Wątek konwersacji w LibreChat reprezentujący temat nauki (np. MATEMATYKA)
- **Learning_Agent**: Agent w LibreChat odpowiedzialny za przygotowanie kontekstu nauki z wykorzystaniem RAG
- **Transcript_Entry**: Pojedynczy wpis transkrypcji zawierający timestamp, mówcę (user/bot) i tekst
- **Lesson_Summary**: Podsumowanie sesji nauki generowane na podstawie transkrypcji
- **RAG**: Retrieval-Augmented Generation - system pobierania rozdziałów z książek do kontekstu

## Requirements

### Requirement 1

**User Story:** Jako użytkownik aplikacji, chcę zalogować się do mojego konta LibreChat, aby uzyskać dostęp do moich wątków nauki

#### Acceptance Criteria

1. WHEN użytkownik uruchamia aplikację po raz pierwszy, THE Android_App SHALL wyświetlić ekran logowania z polami dla URL LibreChat, adresu email i hasła (tymi samymi danymi co konto LibreChat)
2. WHEN użytkownik wprowadza poprawne dane logowania (email i hasło z konta LibreChat) i klika przycisk logowania, THE Android_App SHALL wysłać żądanie uwierzytelnienia do LibreChat API
3. IF uwierzytelnienie zakończy się sukcesem, THEN THE Android_App SHALL zapisać token autoryzacyjny w bezpiecznym magazynie aplikacji
4. IF uwierzytelnienie zakończy się niepowodzeniem, THEN THE Android_App SHALL wyświetlić komunikat o błędzie z informacją o przyczynie
5. WHILE token autoryzacyjny jest ważny, THE Android_App SHALL automatycznie logować użytkownika przy kolejnych uruchomieniach

### Requirement 2

**User Story:** Jako użytkownik, chcę zobaczyć listę moich wątków nauki z LibreChat, aby wybrać temat do nauki

#### Acceptance Criteria

1. WHEN użytkownik jest zalogowany i otwiera ekran główny, THE Android_App SHALL pobrać listę wątków konwersacji z LibreChat API
2. THE Android_App SHALL wyświetlić każdy wątek jako przycisk z nazwą tematu (np. MATEMATYKA, BIOLOGIA)
3. WHEN lista wątków jest pusta, THE Android_App SHALL wyświetlić komunikat informujący o braku dostępnych wątków
4. WHEN pobieranie listy wątków trwa dłużej niż 2 sekundy, THE Android_App SHALL wyświetlić wskaźnik ładowania
5. IF pobieranie listy wątków zakończy się błędem, THEN THE Android_App SHALL wyświetlić komunikat o błędzie z opcją ponowienia próby

### Requirement 3

**User Story:** Jako użytkownik, chcę rozpocząć sesję nauki dla wybranego tematu, aby otrzymać przygotowany kontekst od agenta LibreChat

#### Acceptance Criteria

1. WHEN użytkownik wybiera wątek konwersacji, THE Android_App SHALL wysłać żądanie GET do LibreChat API endpoint `/api/learning/context/{conversationId}`
2. WHEN Learning_Agent otrzyma żądanie kontekstu, THE Learning_Agent SHALL przeanalizować dotychczasowy przebieg nauki z podsumowań w wątku
3. WHEN Learning_Agent przygotowuje kontekst, THE Learning_Agent SHALL wykorzystać RAG do pobrania odpowiedniego rozdziału z książki
4. THE Learning_Agent SHALL zwrócić do Android_App gotowy kontekst w formacie JSON zawierający readyToUseContext z polami systemPrompt, initialMessage, voiceParameters oraz metadata z informacjami o temacie i materiałach
5. WHEN Android_App otrzyma kontekst, THE Android_App SHALL zainicjalizować Session_Context z otrzymanym systemPrompt i rozpocząć sesję z Gemini Live API używając otrzymanego kontekstu

### Requirement 4

**User Story:** Jako użytkownik, chcę aby aplikacja zapisywała transkrypcje mojej rozmowy z botem, aby można było wygenerować podsumowanie sesji

#### Acceptance Criteria

1. WHEN użytkownik mówi podczas sesji, THE Android_App SHALL przechwycić transkrypcję wypowiedzi użytkownika
2. WHEN bot odpowiada podczas sesji, THE Android_App SHALL przechwycić transkrypcję odpowiedzi bota
3. THE Android_App SHALL zapisać każdy Transcript_Entry z timestamp, identyfikatorem mówcy (user lub bot) i tekstem w lokalnej pamięci
4. WHILE sesja trwa, THE Android_App SHALL przechowywać wszystkie Transcript_Entry w Session_Context
5. THE Android_App SHALL zapewnić że transkrypcje są zapisywane w kolejności chronologicznej

### Requirement 5

**User Story:** Jako użytkownik, chcę aby aplikacja dynamicznie aktualizowała kontekst podczas sesji, aby bot miał dostęp do dodatkowych informacji w razie potrzeby

#### Acceptance Criteria

1. WHERE aplikacja potrzebuje dodać kontekst podczas sesji, THE Android_App SHALL wysłać wiadomość tekstową z dodatkowym kontekstem do Gemini Live API
2. THE Android_App SHALL formatować dodatkowy kontekst jako czytelną wiadomość tekstową
3. WHEN kontekst jest aktualizowany, THE Android_App SHALL zapisać informację o aktualizacji w Session_Context
4. THE Android_App SHALL zapewnić że aktualizacje kontekstu nie przerywają bieżącej konwersacji głosowej
5. THE Android_App SHALL ograniczyć częstotliwość aktualizacji kontekstu do maksymalnie jednej na 30 sekund

### Requirement 6

**User Story:** Jako użytkownik, chcę zakończyć sesję nauki i automatycznie wysłać podsumowania do LibreChat, aby mój postęp został zapisany i rodzice otrzymali informację

#### Acceptance Criteria

1. WHEN użytkownik kończy sesję nauki, THE Android_App SHALL wygenerować dwa rodzaje podsumowań na podstawie zebranych Transcript_Entry
2. THE Android_App SHALL wygenerować Lesson_Summary zawierające kluczowe tematy omówione podczas sesji, trudności ucznia, ocenę postępu i sugestie następnych kroków dla kontekstu przyszłych lekcji
3. THE Android_App SHALL wygenerować Parent_Report zawierające informacje o przebiegu lekcji, czasie trwania, tematach omawianych i zidentyfikowanych trudnościach w formacie zrozumiałym dla rodzica
4. WHEN podsumowania są wygenerowane, THE Android_App SHALL wysłać żądanie POST do LibreChat API endpoint `/api/learning/summary` z obiema podsumowaniami
5. WHEN Learning_Agent otrzyma podsumowania, THE Learning_Agent SHALL zaktualizować pamięć ucznia i postęp w nauce oraz wysłać Parent_Report do rodzica przez Telegram
6. IF wysłanie podsumowań zakończy się sukcesem, THEN THE Android_App SHALL wyświetlić potwierdzenie i wyczyścić Session_Context

### Requirement 7

**User Story:** Jako użytkownik, chcę aby aplikacja działała płynnie bez wpływu na jakość rozmowy głosowej, aby doświadczenie nauki było naturalne

#### Acceptance Criteria

1. THE Android_App SHALL przechowywać Session_Context w pamięci lokalnej bez wykorzystania zewnętrznych baz danych
2. THE Android_App SHALL wykonywać operacje sieciowe z LibreChat asynchronicznie bez blokowania wątku UI
3. WHEN aplikacja komunikuje się z LibreChat API, THE Android_App SHALL zapewnić że operacje nie wpływają na opóźnienie audio w Gemini Live
4. THE Android_App SHALL ograniczyć rozmiar Session_Context do maksymalnie 10000 Transcript_Entry
5. IF Session_Context przekroczy limit, THEN THE Android_App SHALL usunąć najstarsze wpisy zachowując najnowsze

### Requirement 8

**User Story:** Jako użytkownik, chcę wysyłać zdjęcia zadań domowych podczas sesji, aby bot mógł je analizować i pomagać w rozwiązaniu

#### Acceptance Criteria

1. WHEN użytkownik robi zdjęcie zadania podczas sesji, THE Android_App SHALL wysłać obraz do Gemini Live API
2. THE Gemini Live API SHALL przeanalizować obraz i udzielić głosowej odpowiedzi na podstawie zawartości obrazu
3. WHEN obraz jest wysyłany, THE Android_App SHALL zapisać informację o wysłaniu obrazu wraz z timestamp w Session_Context
4. IF przesyłanie obrazu zakończy się błędem, THEN THE Android_App SHALL wyświetlić komunikat o błędzie i umożliwić ponowną próbę
5. THE Android_App SHALL ograniczyć rozmiar przesyłanych obrazów do maksymalnie 4MB z automatyczną kompresją jeśli to konieczne

### Requirement 9

**User Story:** Jako użytkownik, chcę aby aplikacja obsługiwała błędy połączenia z LibreChat, aby móc kontynuować naukę nawet przy problemach sieciowych

#### Acceptance Criteria

1. IF połączenie z LibreChat API nie powiedzie się podczas pobierania kontekstu, THEN THE Android_App SHALL umożliwić rozpoczęcie sesji z domyślnym kontekstem
2. IF wysłanie podsumowania nie powiedzie się, THEN THE Android_App SHALL zapisać podsumowanie lokalnie i ponowić próbę przy następnym połączeniu
3. WHEN aplikacja wykryje brak połączenia sieciowego, THE Android_App SHALL wyświetlić ostrzeżenie użytkownikowi
4. THE Android_App SHALL implementować mechanizm retry z wykładniczym wycofywaniem dla nieudanych żądań API
5. THE Android_App SHALL przechowywać kolejkę nieudanych podsumowań z maksymalnie 10 wpisami

### Requirement 10

**User Story:** Jako rodzic, chcę otrzymywać powiadomienia o zakończonych sesjach nauki przez agenta LibreChat, aby śledzić postępy dziecka

#### Acceptance Criteria

1. WHEN Learning_Agent otrzyma Parent_Report z Android_App, THE Learning_Agent SHALL wysłać powiadomienie na Telegram rodzica
2. THE powiadomienie SHALL zawierać informacje z Parent_Report: nazwę tematu, czas trwania sesji, kluczowe tematy omówione i zidentyfikowane trudności
3. WHERE rodzic włączył powiadomienia w konfiguracji LibreChat, THE Learning_Agent SHALL wysyłać powiadomienia po każdej sesji
4. WHERE rodzic wyłączył powiadomienia w konfiguracji LibreChat, THE Learning_Agent SHALL nie wysyłać powiadomień ale zapisać Parent_Report w historii
5. THE Learning_Agent SHALL formatować powiadomienie w sposób czytelny i przyjazny dla rodzica
