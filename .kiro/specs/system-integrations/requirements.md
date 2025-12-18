# Requirements Document

## Introduction

Ten dokument definiuje wymagania dla kompleksowych integracji systemowych w aplikacji Kumpel Chat. Integracje umożliwią asystentowi głosowemu Gemini Live interakcję z systemowymi funkcjami Androida: kontaktami, SMS, alarmami, kalendarzem, transportem publicznym, Google Maps oraz dedykowaną listą zakupów. Wszystkie integracje będą konfigurowalne w UI Integracji z możliwością włączania/wyłączania poszczególnych funkcji (domyślnie wszystkie włączone).

**Target SDK:** 35 (Compile SDK: 34)
**Min SDK:** 26 (Android 8.0)
**Publikacja:** Planowana na Google Play (wymaga zgodności z politykami Play Store)

## Glossary

- **System**: Aplikacja Kumpel Chat z asystentem głosowym Gemini Live
- **Użytkownik**: Osoba korzystająca z aplikacji
- **Gemini Live**: Asystent głosowy AI komunikujący się przez WebSocket
- **Tool**: Funkcja wywoływana przez Gemini Live do wykonania akcji systemowej
- **IntegrationsTab**: Ekran UI do konfiguracji integracji
- **ContentProvider**: Systemowy mechanizm Androida do dostępu do danych (kontakty, kalendarz)
- **AlarmManager**: Systemowy mechanizm Androida do planowania alarmów
- **SmsManager**: Systemowy mechanizm Androida do wysyłania SMS
- **Lista zakupów**: Specjalna notatka z produktami, checkboxami i kategoryzacją
- **Transport publiczny**: Autobusy, tramwaje, pociągi dostępne przez API rozkładów jazdy

## Requirements

### Requirement 1: Integracja z kontaktami i SMS

**User Story:** Jako użytkownik, chcę przeglądać kontakty i wysyłać SMS przez asystenta głosowego, aby szybko komunikować się z osobami z mojej listy kontaktów.

#### Acceptance Criteria

1. WHEN użytkownik prosi o wyszukanie kontaktu THEN System SHALL przeszukać kontakty urządzenia i zwrócić pasujące wyniki z imieniem i numerem telefonu
2. WHEN użytkownik prosi o wysłanie SMS do osoby z kontaktów THEN System SHALL zidentyfikować kontakt, pobrać numer i otworzyć domyślną aplikację SMS z wypełnionym odbiorcą i treścią (tryb Intent - domyślny dla Google Play)
3. WHEN użytkownik prosi o wysłanie SMS do nieznanego numeru THEN System SHALL otworzyć domyślną aplikację SMS z wypełnionym numerem i treścią
4. IF brak uprawnień READ_CONTACTS THEN System SHALL poprosić użytkownika o przyznanie uprawnień przed wykonaniem operacji
5. WHEN System przygotowuje SMS THEN System SHALL odczytać treść wiadomości użytkownikowi i poprosić o potwierdzenie przed otwarciem aplikacji SMS
6. WHEN aplikacja SMS zostanie otwarta z wypełnionymi danymi THEN System SHALL poinformować użytkownika że wiadomość jest gotowa do wysłania
7. WHERE tryb enterprise/sideload jest włączony THEN System SHALL umożliwić wysyłkę SMS bez UI przez SmsManager (wymaga SEND_SMS)
8. IF wysłanie SMS w trybie enterprise nie powiedzie się THEN System SHALL poinformować użytkownika o błędzie z opisem przyczyny

### Requirement 2: Integracja z alarmami i przypomnieniami

**User Story:** Jako użytkownik, chcę ustawiać alarmy i przypomnienia głosowo na określone dni i godziny, aby nie musieć ręcznie konfigurować budzika.

**Uwaga:** ACTION_SET_ALARM nie obsługuje ustawiania alarmu na konkretną datę (tylko godzinę i dni tygodnia). Dla przypomnień na konkretny dzień i godzinę System używa własnego mechanizmu z AlarmManager + BroadcastReceiver + notyfikacją.

#### Acceptance Criteria

1. WHEN użytkownik prosi o ustawienie powtarzającego się alarmu (np. "codziennie o 7:00", "w dni robocze o 6:30") THEN System SHALL otworzyć systemową aplikację Zegar przez ACTION_SET_ALARM z EXTRA_HOUR, EXTRA_MINUTES i EXTRA_DAYS
2. WHEN użytkownik prosi o przypomnienie na konkretny dzień i godzinę (np. "przypomnij mi jutro o 15:00", "alarm na 25 grudnia o 8:00") THEN System SHALL utworzyć własne przypomnienie przez AlarmManager
3. WHEN własne przypomnienie jest tworzone THEN System SHALL zapisać je w lokalnej bazie danych z tytułem, datą, godziną i statusem
4. WHEN nadejdzie czas przypomnienia THEN System SHALL wyświetlić notyfikację z dźwiękiem nawet gdy aplikacja nie jest uruchomiona (przez BroadcastReceiver)
5. WHEN użytkownik prosi o listę przypomnień THEN System SHALL zwrócić listę aktywnych przypomnień z lokalnej bazy
6. WHEN użytkownik prosi o usunięcie przypomnienia THEN System SHALL anulować alarm w AlarmManager i usunąć z bazy
7. WHEN urządzenie zostanie zrestartowane THEN System SHALL ponownie zarejestrować wszystkie aktywne przypomnienia przez BootReceiver
8. IF System działa na Android 12+ THEN System SHALL sprawdzić canScheduleExactAlarms() przed utworzeniem przypomnienia
9. IF canScheduleExactAlarms() zwraca false THEN System SHALL przekierować użytkownika do ACTION_REQUEST_SCHEDULE_EXACT_ALARM
10. IF użytkownik odmówi SCHEDULE_EXACT_ALARM THEN System SHALL użyć setAndAllowWhileIdle() z informacją o możliwym opóźnieniu (zależnym od Doze/App Standby)
11. WHEN przypomnienie zostanie utworzone THEN System SHALL potwierdzić użytkownikowi szczegóły (data, godzina, tytuł)

### Requirement 3: Integracja z kalendarzem - wydarzenia

**User Story:** Jako użytkownik, chcę zarządzać wydarzeniami w kalendarzu głosowo, aby szybko planować i sprawdzać swój harmonogram.

#### Acceptance Criteria

1. WHEN użytkownik prosi o sprawdzenie wydarzeń na dany dzień THEN System SHALL odczytać wydarzenia z kalendarza dla wskazanej daty przez CalendarContract
2. WHEN użytkownik prosi o dodanie wydarzenia i ma uprawnienia WRITE_CALENDAR THEN System SHALL utworzyć wydarzenie w kalendarzu przez CalendarContract z tytułem, datą początkową i końcową
3. WHEN użytkownik prosi o dodanie wydarzenia bez uprawnień WRITE_CALENDAR THEN System SHALL otworzyć systemowy kalendarz przez ACTION_INSERT intent z wypełnionymi danymi
4. WHEN użytkownik prosi o dodanie wydarzenia z opisem THEN System SHALL utworzyć wydarzenie zawierające tytuł, opis, datę i godzinę
5. WHEN użytkownik prosi o usunięcie wydarzenia THEN System SHALL usunąć wskazane wydarzenie z kalendarza (wymaga WRITE_CALENDAR)
6. WHEN użytkownik prosi o modyfikację wydarzenia THEN System SHALL zaktualizować wskazane pola wydarzenia (tytuł, data, godzina, opis)
7. IF brak uprawnień READ_CALENDAR THEN System SHALL poprosić użytkownika o przyznanie uprawnień przed odczytem
8. IF brak uprawnień WRITE_CALENDAR i użytkownik chce dodać wydarzenie THEN System SHALL użyć ACTION_INSERT intent jako fallback
9. WHEN operacja na kalendarzu zostanie wykonana THEN System SHALL potwierdzić wykonanie operacji użytkownikowi

### Requirement 4: Lista zadań TODO (specjalna notatka)

**User Story:** Jako użytkownik, chcę zarządzać listą zadań TODO głosowo i ręcznie, aby śledzić rzeczy do zrobienia.

**Implementacja:** Specjalna notatka "Rzeczy do zrobienia" analogiczna do Listy zakupów - własna baza danych w aplikacji Kumpel Chat.

#### Acceptance Criteria

1. WHEN użytkownik prosi o listę zadań TODO THEN System SHALL odczytać zadania ze specjalnej notatki "Rzeczy do zrobienia" i przedstawić je użytkownikowi
2. WHEN użytkownik prosi o dodanie zadania TODO THEN System SHALL dodać zadanie do notatki z tytułem, opcjonalnym terminem i priorytetem
3. WHEN użytkownik prosi o oznaczenie zadania jako wykonane THEN System SHALL oznaczyć zadanie jako ukończone (checkbox zaznaczony, przekreślenie)
4. WHEN użytkownik prosi o usunięcie zadania TODO THEN System SHALL usunąć wskazane zadanie z notatki
5. WHEN użytkownik prosi o zadania na dany dzień THEN System SHALL zwrócić zadania z terminem na wskazaną datę
6. WHEN użytkownik otwiera listę TODO w UI THEN System SHALL wyświetlić zadania z checkboxami, terminem i możliwością edycji
7. WHEN zadanie ma termin THEN System SHALL opcjonalnie utworzyć przypomnienie przez mechanizm z R2
8. WHEN lista TODO jest wyświetlana na liście notatek THEN System SHALL wyróżnić ją wizualnie (ikona checklisty i wyróżniony kolor)
9. WHEN użytkownik klika przycisk "Wyczyść ukończone" THEN System SHALL usunąć wszystkie zaznaczone zadania

### Requirement 5: Integracja z Google Maps

**User Story:** Jako użytkownik, chcę korzystać z Google Maps przez asystenta głosowego, aby nawigować i wyszukiwać miejsca.

**Uwaga:** google.navigation: intent obsługuje tylko tryby: d (driving), b (bicycling), w (walking), l (two-wheeler). Transit nie ma turn-by-turn navigation - obsługiwany jest przez R6.

#### Acceptance Criteria

1. WHEN użytkownik prosi o nawigację samochodem do miejsca THEN System SHALL uruchomić Google Maps z nawigacją turn-by-turn przez google.navigation: intent z mode=d
2. WHEN użytkownik prosi o nawigację pieszo do miejsca THEN System SHALL uruchomić Google Maps z nawigacją turn-by-turn przez google.navigation: intent z mode=w
3. WHEN użytkownik prosi o nawigację rowerem do miejsca THEN System SHALL uruchomić Google Maps z nawigacją turn-by-turn przez google.navigation: intent z mode=b
4. WHEN użytkownik prosi o wyszukanie miejsca THEN System SHALL uruchomić Google Maps z wyszukiwaniem wskazanej frazy przez geo: URI
5. WHEN użytkownik prosi o pokazanie miejsca na mapie THEN System SHALL otworzyć Google Maps wycentrowane na wskazanej lokalizacji przez geo: URI
6. WHEN użytkownik prosi o trasę transportem publicznym THEN System SHALL przekierować do mechanizmu transportu publicznego (R6) który wyliczy trasę i opcjonalnie otworzy Mapy

### Requirement 6: Integracja z transportem publicznym

**User Story:** Jako użytkownik, chcę sprawdzać połączenia autobusów, tramwajów i pociągów, aby wiedzieć kiedy i skąd odjeżdża najbliższe połączenie do celu.

**Implementacja:** Google Maps Platform Directions API w trybie TRANSIT. API wyznacza trasy, nie jest tablicą odjazdów - wymaga podania celu.

#### Acceptance Criteria

1. WHEN użytkownik prosi o połączenie transportem publicznym do miejsca THEN System SHALL użyć GPS do lokalizacji użytkownika i wywołać Directions API z mode=transit
2. WHEN System znajdzie połączenie THEN System SHALL poinformować użytkownika o: nazwie przystanku startowego, numerze linii, godzinie odjazdu, czasie dojścia do przystanku pieszo i szacowanym czasie całej podróży
3. WHEN użytkownik prosi o połączenie z konkretnego przystanku do celu THEN System SHALL wywołać Directions API z origin ustawionym na przystanek i destination na cel
4. WHEN użytkownik prosi o połączenie z odjazdem o określonej godzinie THEN System SHALL użyć parametru departure_time w Directions API
5. WHEN użytkownik prosi o dotarcie na określoną godzinę THEN System SHALL użyć parametru arrival_time w Directions API
6. IF brak uprawnień lokalizacji THEN System SHALL poprosić użytkownika o przyznanie uprawnień GPS lub podanie adresu startowego
7. IF brak połączenia internetowego THEN System SHALL poinformować użytkownika o braku możliwości sprawdzenia połączeń
8. WHEN użytkownik prosi o alternatywne połączenia THEN System SHALL użyć parametru alternatives=true i przedstawić dostępne opcje dojazdu (jeśli API zwróci alternatywy)
9. WHEN użytkownik wybierze połączenie THEN System SHALL opcjonalnie otworzyć Google Maps wycentrowane na celu lub przystanku startowym (pomocniczo - trasa jest już wyliczona i opowiedziana głosowo)
10. WHEN trasa wymaga dojścia pieszo do przystanku THEN System SHALL opcjonalnie uruchomić nawigację pieszą (google.navigation: mode=w) do przystanku

### Requirement 7: Lista zakupów

**User Story:** Jako użytkownik, chcę mieć dedykowaną listę zakupów, którą mogę edytować głosowo i ręcznie, z produktami posortowanymi według kategorii.

#### Acceptance Criteria

1. WHEN użytkownik prosi o dodanie produktu do listy zakupów THEN System SHALL dodać produkt do listy z przypisaniem do odpowiedniej kategorii
2. WHEN użytkownik dodaje wiele produktów THEN System SHALL posortować produkty według kategorii (np. nabiał, pieczywo, warzywa, owoce, mięso, ryby, mrożonki, napoje, słodycze, chemia, inne)
3. WHEN użytkownik otwiera listę zakupów w UI THEN System SHALL wyświetlić listę z checkboxami przy każdym produkcie, pogrupowaną według kategorii
4. WHEN użytkownik zaznacza checkbox produktu THEN System SHALL oznaczyć produkt jako kupiony (wizualne przekreślenie)
5. WHEN użytkownik klika przycisk "Wyczyść kupione" THEN System SHALL usunąć wszystkie zaznaczone (kupione) produkty z listy
6. WHEN użytkownik prosi o usunięcie produktu głosowo THEN System SHALL usunąć wskazany produkt z listy
7. WHEN użytkownik prosi o wyświetlenie listy zakupów THEN System SHALL odczytać produkty z listy pogrupowane według kategorii
8. WHEN lista zakupów jest wyświetlana na liście notatek THEN System SHALL wyróżnić ją wizualnie (ikona koszyka i wyróżniony kolor)
9. WHEN użytkownik edytuje listę ręcznie w UI THEN System SHALL zapisać zmiany natychmiast do lokalnej bazy
10. WHEN Gemini Live dodaje produkty THEN System SHALL automatycznie przypisać kategorię używając słownika produktów z fallback do kategorii "inne"
11. WHEN użytkownik ręcznie zmienia kategorię produktu THEN System SHALL zapisać korektę i użyć jej w przyszłości dla tego produktu
12. WHEN użytkownik prosi o wyczyszczenie całej listy THEN System SHALL poprosić o potwierdzenie przed usunięciem wszystkich produktów

### Requirement 8: Konfiguracja integracji w UI

**User Story:** Jako użytkownik, chcę włączać i wyłączać poszczególne integracje w ustawieniach, aby kontrolować jakie funkcje są dostępne dla asystenta.

#### Acceptance Criteria

1. WHEN użytkownik otwiera zakładkę Integracje THEN System SHALL wyświetlić sekcję "Integracje systemowe" z przełącznikami dla każdej integracji
2. WHEN aplikacja jest instalowana po raz pierwszy THEN System SHALL ustawić wszystkie przełączniki integracji jako włączone domyślnie (bez automatycznego proszenia o uprawnienia)
3. WHEN użytkownik wyłącza integrację THEN System SHALL ukryć odpowiednie narzędzia przed Gemini Live
4. WHEN użytkownik włącza integrację THEN System SHALL udostępnić odpowiednie narzędzia dla Gemini Live
5. WHEN integracja wymaga uprawnień THEN System SHALL wyświetlić informację o wymaganych uprawnieniach przy przełączniku (np. "Wymaga: Kontakty")
6. WHEN użytkownik po raz pierwszy używa funkcji wymagającej uprawnień THEN System SHALL poprosić o przyznanie uprawnień w kontekście tej funkcji
7. WHEN uprawnienia są przyznane THEN System SHALL wyświetlić zielony znacznik przy integracji
8. WHEN uprawnienia są odmówione THEN System SHALL wyświetlić ostrzeżenie przy integracji z linkiem do ustawień systemu

### Requirement 9: Obsługa uprawnień

**User Story:** Jako użytkownik, chcę być informowany o wymaganych uprawnieniach i móc je przyznawać w kontekście używanej funkcji.

#### Acceptance Criteria

1. WHEN integracja wymaga uprawnień THEN System SHALL wyświetlić jasny opis dlaczego uprawnienie jest potrzebne
2. WHEN użytkownik odmówi uprawnień THEN System SHALL poinformować o ograniczonej funkcjonalności bez blokowania innych funkcji
3. WHEN użytkownik wybierze "Nie pytaj ponownie" THEN System SHALL wyświetlić instrukcję jak włączyć uprawnienia w ustawieniach systemu
4. WHEN uprawnienia są już przyznane THEN System SHALL nie pytać ponownie o te same uprawnienia
5. WHEN użytkownik próbuje użyć funkcji bez uprawnień THEN System SHALL poprosić o uprawnienia w kontekście tej funkcji
