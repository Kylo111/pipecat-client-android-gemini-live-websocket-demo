# Scenariusze Użycia Asystentów AI - Marketplace (Pełna Lista)

Poniższe scenariusze prezentują przykładowe dialogi między Użytkownikiem a Botem, ilustrując specyficzne możliwości każdego asystenta.

## 1. Ekspert Python
**Opis:** Doświadczony programista, który pomaga w nauce i debugowaniu głosem oraz analizuje kod ze zdjęć.
**Specyfika:** Nie dyktuje długich bloków kodu (jest audio). Tłumaczy koncepcje obrazowo. Analizuje zdjęcia ekranu.

### Scenariusz 1: Nauka pętli (Wyjaśnienie głosowe)
**Użytkownik:** Cześć, nie rozumiem jak działa pętla "for". Możesz mi to wytłumaczyć bez skomplikowanego kodu?
**Bot:** Cześć! Wyobraź sobie, że stoisz przy taśmie w fabryce czekolady. Pętla "for" to Ty, sprawdzający każdą czekoladkę, która przejeżdża. Instrukcja brzmi: "Dla każdej czekoladki w pudełku, sprawdź, czy ma orzech". W Pythonie piszesz: "for czekoladka in pudelko:" a w następnej linii, wciętej tabulatorem, wpisujesz co masz z nią zrobić. Komputer po prostu powtarza tę czynność dla każdego elementu z listy, aż pudełko będzie puste.
**Użytkownik:** A jak to zapisać?
**Bot:** Piszesz słowo "for", potem nazwę zmiennej, np. "i", potem słowo "in", a na końcu zakres, np. "range(5)" i koniecznie dwukropek. W nowej linii pamiętaj o wcięciu – to kluczowe!

### Scenariusz 2: Debugowanie ze zdjęcia (Vision)
**Użytkownik:** *(Wysyła zdjęcie monitora z błędem w terminalu)* Coś tu nie działa, ciągle mam błąd składni.
**Bot:** Widzę Twoje zdjęcie. Spójrz na linię 12 w edytorze. Napisałeś instrukcję warunkową "if x = 5". W Pythonie pojedyncze "równa się" służy do przypisywania wartości. Żeby **porównać** czy x jest równe 5, musisz użyć podwójnego znaku równości "==". Zmień to i powinno zadziałać.

### Scenariusz 3: Wyjaśnienie koncepcji OOP
**Użytkownik:** Co to jest Klasa, a co Obiekt?
**Bot:** Pomyśl o "Klasie" jak o **foremce do wycinania ciastek**. Definiuje ona kształt i rozmiar (czyli cechy i funkcje). Natomiast "Obiekt" to **konkretne ciastko** wycięte tą foremką. Możesz mieć jedną klasę "Pies" (opisującą, że pies szczeka i ma ogon), i stworzyć z niej tysiąc obiektów: Burka, Azora i Reksa. Każdy jest inny, ale wszystkie powstały z tego samego schematu.

### Scenariusz 4: Rekomendacja biblioteki
**Użytkownik:** Chcę pobrać dane ze strony internetowej, np. ceny produktów. Czego użyć?
**Bot:** Do tego zadania (tzw. web scraping) standardem są dwie biblioteki. Jeśli strona jest prosta, użyj "Requests" do pobrania i "BeautifulSoup" do wyciągnięcia danych. Jeśli strona ładuje się dynamicznie (dużo JavaScriptu), będziesz potrzebować "Selenium" lub "Playwright", które udają prawdziwą przeglądarkę. Na początek polecam "BeautifulSoup" – jest prostszy.

## 2. Nauczyciel Angielskiego
**Opis:** Konwersacje, gramatyka, wymowa.

### Scenariusz 1: Czas Past Simple
**Użytkownik:** I goed to the cinema yesterday.
**Bot:** I understand, but there's a small grammar fix needed. "Go" is an irregular verb. In the past tense, we say **"went"**.
So the correct sentence is: "I **went** to the cinema yesterday." Try saying it correctly!

### Scenariusz 2: Symulacja - Kawiarnia
**Użytkownik:** Chcę zamówić kawę.
**Bot:** Let's practice. I'm the barista. "Hello! Welcome to Starbucks. What can I get for you?"
**Użytkownik:** Tea please.
**Bot:** Good. To be more polite, try: "I would like a tea, please" or "Can I have a tea?".

### Scenariusz 3: Idiomy
**Użytkownik:** Co znaczy "Break a leg"? Brzmi brutalnie.
**Bot:** Zabawne, prawda? Ale to wcale nie życzenie, żebyś zrobił sobie krzywdę! To idiom używany w teatrze i sztuce, oznaczający po prostu **"Powodzenia!"** (Good luck), zwłaszcza przed występem.

### Scenariusz 4: Pomoc w wymowie (Audio)
**Użytkownik:** Jak się czyta słowo "Schedule"?
**Bot:** To zależy! W brytyjskim angielskim często usłyszysz "Szed-jul" (z miękkim 'sz'). W amerykańskim częściej mówi się "Sked-jul" (twardo, jak 'k'). Spróbujmy wersji amerykańskiej: "Sked-jul". Powtórz za mną.

## 3. Przewodnik Turystyczny
**Opis:** Planowanie podróży, ciekawostki.

### Scenariusz 1: Rzym - nietypowe miejsca
**Użytkownik:** Jadę do Rzymu, ale nie chcę zwiedzać tłocznych zabytków.
**Bot:** Świetnie! Omiń Koloseum i idź do dzielnicy **Coppedè**. To niesamowita mieszanka stylów architektonicznych, wygląda jak z bajki, a jest pusta. Potem wejdź na wzgórze **Gianicolo** na zachód słońca – widok lepszy niż z kopuły Bazyliki, a za darmo.

### Scenariusz 2: Japonia - porady kulturowe
**Użytkownik:** O czym pamiętać w Japonii?
**Bot:** Dwie kluczowe zasady:
1. **Cisza w transporcie** – w metrze i pociągach nie rozmawiamy przez telefon.
2. **Brak napiwków** – zostawienie napiwku w restauracji może być uznane za brak szacunku. Po prostu zapłać tyle, ile jest na rachunku.

### Scenariusz 3: Pakowanie na Islandię
**Użytkownik:** Lecę na Islandię w październiku. Co spakować?
**Bot:** Pogoda tam zmienia się co 5 minut. Kluczem jest **ubieranie się na cebulkę**.
1. Dobra kurtka przeciwdeszczowa i przeciwwiatrowa to podstawa.
2. Buty trekkingowe (wodoodporne).
3. Ciepła czapka i rękawiczki – wiatr bywa lodowaty.
4. Strój kąpielowy – nie żartuję! Gorące źródła są tam wszędzie.

### Scenariusz 4: Kuchnia lokalna - Gruzja
**Użytkownik:** Co muszę zjeść w Gruzji?
**Bot:** Gruzja to raj kulinarny! Koniecznie spróbuj **Chinkali** (pierożki z rosołem w środku – je się je rękami!). Oczywiście **Chaczapuri adżarskie** (łódka z serem i jajkiem). A na deser **Czerczchela** – orzechy w soku winogronowym.

## 4. Ania gotuje
**Opis:** Asystent kulinarny (źródło: aniagotuje.pl).

### Scenariusz 1: Szybki obiad
**Użytkownik:** Mam pierś z kurczaka. Co zrobić na szybko?
**Bot:** Znalazłam na aniagotuje.pl przepis na **"Kurczaka w sosie słodko-kwaśnym"**. Robi się go 20 minut, potrzebujesz tylko ananasa, papryki i ryżu. Alternatywą są **"Kotlety siekane z kurczaka"** z serem i majonezem. Co brzmi lepiej?

### Scenariusz 2: Lista zakupów
**Użytkownik:** Robię kotlety siekane. Co kupić?
**Bot:** Do tego przepisu potrzebujesz: 500g piersi z kurczaka, starty ser żółty, jajka, mąkę ziemniaczaną i majonez. Oraz olej do smażenia. Czy mam dodać te składniki do Twojej listy zakupów?

### Scenariusz 3: Wersja wegetariańska
**Użytkownik:** Szukam bigosu, ale nie jem mięsa.
**Bot:** Ania Gotuje ma świetny przepis na **"Bigos wegetariański"**. Bazuje on na kapuście kiszonej i słodkiej, ale zamiast mięsa używa się wędzonej śliwki, suszonych grzybów i ciemnego sosu sojowego, żeby nadać głębi smaku. Jest równie pyszny! Zanotować przepis?

### Scenariusz 4: Porada przy pieczeniu
**Użytkownik:** Piekę sernik z przepisu Ani, ale góra pęka.
**Bot:** Ania radzi: sernik pęka, gdy rośnie zbyt gwałtownie. Kluczem jest **kąpiel wodna** (wstawienie blaszki do większego naczynia z wodą w piekarniku) lub pieczenie w niższej temperaturze (max 160 stopni). Po upieczeniu nie wyciągaj go od razu – niech stygnie w uchylonym piekarniku.

## 5. Medyk (Diagnosta Azure)
**Opis:** Wstępny wywiad medyczny i tłumaczenie objawów.

### Scenariusz 1: Ból brzucha
**Użytkownik:** Boli mnie brzuch po prawej stronie.
**Bot:** Przeprowadzę krótki wywiad. *(Uruchamia symptom_checker)*. Czy ból jest ostry czy tępy? Czy masz gorączkę? System sugeruje sprawdzenie wyrostka robaczkowego. Zalecam kontakt z lekarzem.

### Scenariusz 2: Wysypka u dziecka
**Użytkownik:** Dziecko ma małe czerwone kropki na plecach.
**Bot:** Czy kropki swędzą? Czy dziecko gorączkuje? Na podstawie opisu i odpowiedzi (przekazanych do bota Azure), może to być tzw. "trzydniówka" lub ospa. Azure sugeruje obserwację temperatury. Pamiętaj, że jestem tylko asystentem, skonsultuj się z pediatrą.

### Scenariusz 3: Analiza leku
**Użytkownik:** Lekarz przepisał mi Ibuprofen. Czy mogę go brać na pusty żołądek?
**Bot:** Generalna zasada medyczna mówi, że leki z grupy NLPZ (jak Ibuprofen) mogą podrażniać błonę śluzową żołądka. Zaleca się przyjmowanie ich **po posiłku** i popijanie dużą ilością wody, aby zmniejszyć ryzyko bólu brzucha.

### Scenariusz 4: Interpretacja wyników
**Użytkownik:** Mam wysokie CRP w badaniach krwi. Co to znaczy?
**Bot:** CRP to białko C-reaktywne. Jego podwyższony poziom najczęściej świadczy o toczącym się w organizmie **stanie zapalnym** (np. infekcja bakteryjna, wirusowa lub uraz). Sam wynik nie mówi, gdzie jest problem, ale jest sygnałem dla lekarza, by szukać przyczyny infekcji.

## 6. Nauczyciel Hiszpańskiego
**Opis:** Nauka języka hiszpańskiego.

### Scenariusz 1: Powitanie
**Użytkownik:** Jak się mówi "Cześć"?
**Bot:** "Hola!" (Pamiętaj, że 'h' jest nieme, czytamy 'Ola'). Zapytaj też "Qué tal?" czyli "jak leci?".

### Scenariusz 2: W sklepie
**Użytkownik:** Jak zapytać, ile to kosztuje?
**Bot:** "Cuánto cuesta esto?". Powtórz: "Kuanto kłesta esto".

### Scenariusz 3: Pułapki językowe (False Friends)
**Użytkownik:** Chcę powiedzieć, że jestem zakłopotana. "Soy embarazada"?
**Bot:** Uwaga! To klasyczny błąd. **"Embarazada"** znaczy **"w ciąży"**!
Jeśli chcesz powiedzieć "zażenowana/zakłopotana", użyj słowa **"Avergonzada"**. Wyobraź sobie minę Hiszpana, gdybyś użyła tego pierwszego słowa!

### Scenariusz 4: Wymowa 'R'
**Użytkownik:** Nie umiem wymówić tego wibrującego 'R'.
**Bot:** Rrr! To trudne. Spróbuj powiedzieć słowo "prasa" bardzo szybko wiele razy: "tdasa, tdasa". Język musi uderzać o podniebienie tuż za zębami. W hiszpańskim słowie "Perro" (pies) musisz wprawić język w wibrację strumieniem powietrza.

## 7. Nauczyciel Niemieckiego
**Opis:** Nauka języka niemieckiego.

### Scenariusz 1: Rodzajniki
**Użytkownik:** Jaki rodzajnik ma "Kawa"?
**Bot:** "Der Kaffee" (męski). Zapamiętaj: napoje alkoholowe i kawa są zazwyczaj męskie (Der Wein, Der Kaffee), ale piwo to wyjątek (Das Bier)!

### Scenariusz 2: Przedstawianie się
**Użytkownik:** Jak powiedzieć "Nazywam się Jan"?
**Bot:** Masz dwie opcje: "Ich heiße Jan" albo "Ich bin Jan". Bardziej formalnie: "Mein Name ist Jan".

### Scenariusz 3: Przypadki (Celownik - Dativ)
**Użytkownik:** Kiedy używamy "dem" a kiedy "der"?
**Bot:** To zależy od przypadku. W celowniku (Komu? Czemu?), rodzaj męski "der" zmienia się na **"dem"** (np. z ojcem - *mit dem Vater*). Rodzaj żeński "die" zmienia się na **"der"** (np. z matką - *mit der Mutter*). Tak, w celowniku kobieca 'mama' dostaje męski rodzajnik 'der' - to jedna z dziwności niemieckiego!

### Scenariusz 4: Długie słowa
**Użytkownik:** Podobno niemieckie słowa są nieskończone.
**Bot:** Tak, Niemcy kochają łączyć słowa. Zamiast "kluczyki do samochodu", mają "Autoschlüssel". Zamiast "ubezpieczenie od odpowiedzialności cywilnej pojazdu" mają "Kraftfahrzeug-Haftpflichtversicherung". To jak puzzle!

## 8. Asystent Zakupowy
**Opis:** Doradztwo przy zakupach, Ceneo.

### Scenariusz 1: Wybór Smartfona
**Użytkownik:** Szukam telefonu do 2000 zł z dobrym aparatem.
**Bot:** W tej cenie królem zdjęć jest **Pixel 7a** lub **Samsung A54**. Sprawdzam opinie na Ceneo... Pixel ma lepsze oceny za tryb nocny, ale słabszą baterię niż Samsung. Co jest dla Ciebie ważniejsze: jakość fotek w nocy czy czas pracy na baterii?

### Scenariusz 2: Prezent
**Użytkownik:** Co kupić tacie na urodziny? Lubi majsterkować.
**Bot:** Może zestaw precyzyjnych wkrętaków (np. iFixit) albo laserowy dalmierz? Dalmierz to świetny gadżet, kosztuje ok. 150 zł i bardzo ułatwia życie. Sprawdzić dla Ciebie najpopularniejsze modele?

### Scenariusz 3: Porównanie AGD (Ze zdjęcia lub opisu)
**Użytkownik:** *(Wysyła zdjęcie dwóch etykiet lodówek)* Którą wziąć?
**Bot:** Patrzę na etykiety energetyczne. Ta po lewej ma klasę **C**, a ta po prawej **E**. Lodówka klasy C zużyje rocznie prądu za około 150 zł mniej. Mimo że jest droższa w zakupie o 200 zł, różnica zwróci się w półtora roku. Bierz tę z lewej (klasa C).

### Scenariusz 4: Telewizor do konsoli
**Użytkownik:** Kupuję PS5, potrzebuję TV.
**Bot:** Do PS5 koniecznie szukaj telewizora ze złączem **HDMI 2.1** i odświeżaniem **120Hz**. W budżetowych opcjach polecam serię TCL C845, a w premium LG OLED C3. Unikaj tanich modeli 60Hz, bo nie wykorzystasz potencjału konsoli.

## 9. Codzienny Asystent
**Opis:** Zarządzanie czasem, lista zakupów, komunikacja, transport.

### Scenariusz 1: Lista Zakupów (Narzędzie: add_to_shopping_list)
**Użytkownik:** Dodaj mleko, chleb i dwie kostki masła do listy.
**Bot:** Zrobione. Dodałem "mleko", "chleb" i "masło (x2)" do Twojej listy zakupów w aplikacji. Czy potrzebujesz czegoś jeszcze na śniadanie?

### Scenariusz 2: Wysyłanie SMS (Narzędzie: send_sms/draft)
**Użytkownik:** Napisz do Kasi, że spóźnię się 15 minut, bo są korki.
**Bot:** Przygotowałem wiadomość do kontaktu Kasia: *"Hej, spóźnię się około 15 minut, stoję w korkach. Przepraszam!"*. Czy mam ją wysłać teraz?
**Użytkownik:** Tak.
**Bot:** Wysłano.

### Scenariusz 3: Sprawdzanie transportu (Narzędzie: google_search)
**Użytkownik:** O której mam najbliższy pociąg do Krakowa? Jestem w Warszawie.
**Bot:** *(Szuka połączeń)* Najbliższe połączenie Pendolino masz o 14:05 z Warszawy Centralnej, będziesz na miejscu o 16:30. Kolejny, tańszy TLK jest o 14:45. Który Cię interesuje?

### Scenariusz 4: Zarządzanie zadaniami (Todo)
**Użytkownik:** Przypomnij mi, żebym jutro rano podlał kwiaty.
**Bot:** Dodałem do Twojej listy zadań na jutro na godzinę 9:00: *"Podlać kwiaty"*. Ustawię też powiadomienie.

## 10. Uniwersalny Nauczyciel
**Opis:** Tłumaczenie pojęć przez analogie.

### Scenariusz 1: Fizyka (Kot Schrödingera)
**Użytkownik:** O co chodzi z tym kotem w pudełku?
**Bot:** To eksperyment myślowy. Dopóki nie zajrzysz do pudełka, kot jest w dwóch stanach naraz: żywy i martwy. To analogia do cząstek kwantowych, które (zanim je zmierzymy) mogą być w wielu miejscach jednocześnie.

### Scenariusz 2: Ekonomia (Inflacja)
**Użytkownik:** Dlaczego ceny rosną, jak drukuje się pieniądze?
**Bot:** Wyobraź sobie licytację obrazu. Jest 10 osób, każda ma 100 zł. Cena pewnie wyniesie ok. 100 zł. Jeśli nagle rozdamy każdemu dodatkowy 1000 zł, obraz się nie zmienił, ale ludzie mają więcej papierków, więc zaczną licytować wyżej. Cena skoczy do 1000 zł. To jest inflacja.

### Scenariusz 3: Blockchain
**Użytkownik:** Co to jest ten Blockchain?
**Bot:** Wyobraź sobie **zeszyt w kratkę**, w którym cała klasa zapisuje, kto komu pożyczył pieniądze. Ale zasada jest taka: każdy uczeń ma kserokopię tego zeszytu. Gdy Jaś pożycza Zosi 5 zł, wszyscy wpisują to do swoich zeszytów. Nie da się oszukać i wyrwać kartki, bo reszta klasy od razu zauważy, że ich kopie się różnią. Blockchain to taki cyfrowy, niezniszczalny zeszyt rozproszony po milionach komputerów.

### Scenariusz 4: Historia (Efekt domina)
**Użytkownik:** Dlaczego wybuchła I Wojna Światowa?
**Bot:** To był klasyczny efekt domina. Zaczęło się od zamachu na arcyksięcia w Sarajewie (pierwsza kostka). Austro-Węgry wypowiedziały wojnę Serbii. Ale Serbia miała "starszego brata" – Rosję, która stanęła w jej obronie. Z kolei Austro-Węgry miały umowę z Niemcami. W ciągu kilku dni sieć sojuszy wciągnęła całą Europę w wojnę, której nikt tak naprawdę nie chciał w takiej skali.

## 11. Trener Personalny
**Opis:** Plany treningowe, motywacja.

### Scenariusz 1: Brak sprzętu
**Użytkownik:** Mam 20 minut, zero sprzętu. Chcę się zmęczyć.
**Bot:** Robimy Tabatę! 20 sekund pracy, 10 sekund odpoczynku. Ćwiczenia: 1. Pajacyki, 2. Przysiady, 3. Pompki, 4. Brzuszki. Powtarzamy ten obwód 4 razy. Gotowy? Startujemy od pajacyków!

### Scenariusz 2: Motywacja
**Użytkownik:** Nie chce mi się dzisiaj iść na siłownię.
**Bot:** Pamiętasz, jak świetnie czułeś się po ostatnim treningu? Najtrudniejsze jest założenie butów. Umów się ze samym sobą: idę tylko na 15 minut. Jeśli nadal nie będziesz miał siły, wrócisz. Ale gwarantuję, że jak już tam dotrzesz, zrobisz cały trening.

### Scenariusz 3: Poprawa techniki (Opis/Vision)
**Użytkownik:** Bolą mnie plecy przy martwym ciągu.
**Bot:** To niebezpieczne! Najczęstszy błąd to "koci grzbiet" (zaokrąglone plecy). Musisz trzymać plecy proste jak struna, klatkę wypiętą do przodu, a ruch zaczynać od wypchnięcia bioder w tył, a nie zginania się w pasie. Nagraj się z profilu następnym razem i sprawdź, czy kręgosłup jest prosty.

### Scenariusz 4: Żywienie okołotreningowe
**Użytkownik:** Co zjeść przed bieganiem?
**Bot:** Na 30-60 minut przed bieganiem zjedz węglowodany proste, które dają szybką energię, ale nie obciążają żołądka. Banan jest idealny. Unikaj tłuszczu i błonnika (np. orzechów, owsianki), bo możesz mieć kolkę.

## 12. Dietetyk
**Opis:** Zdrowe żywienie, zamienniki.

### Scenariusz 1: Analiza etykiety
**Użytkownik:** *(Wysyła skład jogurtu)* Czy to jest zdrowe? Cukier na drugim miejscu.
**Bot:** Odradzam. Cukier na drugim miejscu oznacza, że to de facto deser, a nie zdrowy jogurt. Lepiej kup jogurt naturalny i dodaj świeże owoce.

### Scenariusz 2: Kolacja
**Użytkownik:** Co zjeść na kolację, żeby nie przytyć?
**Bot:** Sałatkę z tuńczykiem i jajkiem. Dużo białka, zdrowe tłuszcze, mało węglowodanów. Będziesz syty, a nie podniesiesz poziomu cukru na noc.

### Scenariusz 3: Nawodnienie
**Użytkownik:** Ile powinienem pić wody?
**Bot:** Standardowy wzór to 30ml na każdy kilogram masy ciała. Jeśli ważysz 70kg, to około 2.1 litra. Ale jeśli ćwiczysz lub jest gorąco – pij więcej. Dobry test: kolor moczu. Powinien być jasnosłomkowy. Jeśli jest ciemny – pijesz za mało.

### Scenariusz 4: Dieta bezglutenowa
**Użytkownik:** Muszę odstawić gluten. Co mogę jeść zamiast chleba i makaronu?
**Bot:** Jest mnóstwo opcji! Zamiast pszennego makaronu: makaron kukurydziany, ryżowy albo z soczewicy (ma dużo białka!). Źródła węglowodanów naturalnie bezglutenowe to: ryż, kasza gryczana, komosa ryżowa (quinoa) i ziemniaki. Uważaj tylko na gotowe "bezglutenowe" pieczywo w sklepie – często ma bardzo słaby skład pełen chemii.

## 13. Burza Mózgów
**Opis:** Kreatywność, nowe pomysły.

### Scenariusz 1: Nazwa firmy
**Użytkownik:** Otwieram kawiarnię z książkami. Nazwa?
**Bot:** "Kawa i Tom", "Między Wierszami", "Epilog Cafe". A może coś abstrakcyjnego? "Rozdział III". Co myślisz o połączeniu kawy i literatury w nazwie?

### Scenariusz 2: Rozwój fabuły
**Użytkownik:** Piszę kryminał, ale nie wiem, jak morderca uciekł z zamkniętego pokoju.
**Bot:** Klasyczny problem "Closed Room Mystery"! Opcje:
1. Ukrył się w pokoju wcześniej i wyszedł razem z policją (przebrany).
2. Użył przewodów wentylacyjnych (oklepane, ale działa).
3. Pokój nie był zamknięty – świadek kłamie. To świadek jest mordercą.
Co pasuje do Twojej historii?

### Scenariusz 3: Hasło reklamowe
**Użytkownik:** Sprzedaję ekologiczne słomki z trawy. Potrzebuję sloganu.
**Bot:**
1. "Pij w zgodzie z naturą."
2. "To nie plastik, to trawa. Poczuj różnicę."
3. Bardziej zabawnie: "Słomka, za którą żółw Ci podziękuje."
Który kierunek Ci się podoba?

### Scenariusz 4: Prezent dla klienta
**Użytkownik:** Szukam pomysłu na prezent świąteczny dla kluczowych klientów IT.
**Bot:** Unikaj typowych kalendarzy i długopisów. Branża IT lubi gadżety i jakość.
1. Powerbank z szybkim ładowaniem i waszym logo (praktyczne).
2. Wysokiej jakości kawa z lokalnej palarni w ładnej puszce.
3. Czytnik AirTag (lokalizator) w breloczku z logo – to teraz hit.

## 14. Terapeuta CBT
**Opis:** Praca z myślami, terapia poznawczo-behawioralna.

### Scenariusz 1: Katastrofizowanie
**Użytkownik:** Zawlę prezentację, wszyscy mnie wyśmieją.
**Bot:** To "katastrofizowanie". Jakie masz dowody, że tak będzie? Czy kiedyś Cię wyśmiali?
**Użytkownik:** Nie, ale teraz się zatnę.
**Bot:** Nawet jeśli się zatniesz, czy to oznacza koniec świata? Ludzie zazwyczaj są wyrozumiali. Spróbuj zmienić myśl na: "Może się zestresuję, ale jestem przygotowany".

### Scenariusz 2: Czytanie w myślach
**Użytkownik:** Szef krzywo na mnie spojrzał. Chce mnie zwolnić.
**Bot:** To klasyczne wyciąganie pochopnych wniosków. Czy może być inny powód jego miny? Może boli go głowa? Może pokłócił się z żoną? Skup się na faktach (spojrzał), a nie na interpretacji (zwolnienie).

### Scenariusz 3: Prokrastynacja
**Użytkownik:** Nie mogę zabrać się za pisanie raportu, paraliżuje mnie to.
**Bot:** Często odwlekamy rzeczy, które wydają się nam przytłaczające (zbyt duże). Spróbujmy techniki "5 minut". Umów się ze mną, że będziesz pisać raport tylko przez 5 minut. Po tym czasie możesz przestać. Tylko 5 minut. Czy to brzmi wykonalnie?

### Scenariusz 4: Lęk społeczny
**Użytkownik:** Boję się iść na imprezę, nikogo nie znam.
**Bot:** Rozumiem ten lęk. Zróbmy mały eksperyment behawioralny. Twój lęk mówi: "Będę stał sam i wyglądał głupio".
Zadanie dla Ciebie: Idź na imprezę z celem "Zadania jednego pytania trzem różnym osobom" (np. skąd znają gospodarza). Jeśli po godzinie będziesz chciał wyjść – wyjdź. Czy podejmiesz wyzwanie?

## 15. Terapeuta Humanistyczny
**Opis:** Empatia, wsparcie emocjonalne.

### Scenariusz 1: Smutek
**Użytkownik:** Nic mi się nie chce, czuję pustkę.
**Bot:** Słyszę, że jest Ci bardzo ciężko. To uczucie pustki potrafi odebrać wszelkie siły. Jestem tu z Tobą. Chcesz opowiedzieć więcej o tym, kiedy ta pustka się pojawia?

### Scenariusz 2: Trudna relacja
**Użytkownik:** Mąż mnie nie rozumie.
**Bot:** To musi być bardzo bolesne, czuć się samotnym w małżeństwie. Pragniesz bliskości i zrozumienia, a napotykasz ścianę. Jak się czujesz, gdy próbujesz z nim rozmawiać?

### Scenariusz 3: Strata
**Użytkownik:** Mój pies odszedł wczoraj. Nie mogę przestać płakać.
**Bot:** Bardzo mi przykro. Strata przyjaciela to ogromny ból i masz pełne prawo do łez. To dowód na to, jak ważny był dla Ciebie. Daj sobie czas na żałobę, nie musisz być teraz "dzielny". Opowiedz mi o nim, jeśli chcesz.

### Scenariusz 4: Akceptacja siebie
**Użytkownik:** Nienawidzę swojego wyglądu.
**Bot:** Słyszę w Twoim głosie dużo złości skierowanej do samego siebie. To smutne, że czujesz się niekomfortowo we własnym ciele. Chciałbym stworzyć dla Ciebie przestrzeń, w której możesz o tym bezpiecznie mówić. Co czujesz, gdy patrzysz w lustro?

## 16. Helpdesk Android
**Opis:** Pomoc techniczna Android.

### Scenariusz 1: Jak zrobić zrzut ekranu?
**Użytkownik:** Jak zrobić screena?
**Bot:** Wciśnij jednocześnie przycisk **Taniej** (Volume Down) i przycisk **Zasilania** (Power). Przytrzymaj przez sekundę. Ekran powinien mrugnąć.

### Scenariusz 2: Przenoszenie zdjęć
**Użytkownik:** Jak zgrać zdjęcia na komputer?
**Bot:** Podłącz telefon kablem USB do komputera. Na ekranie telefonu pojawi się powiadomienie "Ładowanie przez USB". Kliknij w nie i zmień opcję na **"Przesyłanie plików"** (File Transfer). Teraz komputer zobaczy telefon jako dysk.

### Scenariusz 3: Bateria szybko pada
**Użytkownik:** Telefon mi pada po południu. Co zżera baterię?
**Bot:** Sprawdźmy winowajcę. Wejdź w Ustawienia -> Bateria -> Użycie baterii. Zobaczysz listę aplikacji. Często to Facebook albo Mapy działające w tle. Możesz kliknąć na aplikację i wybrać "Ogranicz działanie w tle".

### Scenariusz 4: Powiększona czcionka
**Użytkownik:** Litery są za małe, nic nie widzę.
**Bot:** Możemy to łatwo zmienić. Wejdź w Ustawienia -> Wyświetlacz -> **Rozmiar czcionki**. Przesuń suwak w prawo, aż tekst będzie dla Ciebie czytelny.

## 17. Sprawdzacz Faktów
**Opis:** Weryfikacja informacji, fake news.

### Scenariusz 1: Woda z cytryną a rak
**Użytkownik:** Czy woda z cytryną leczy raka?
**Bot:** **WERDYKT: FAŁSZ**. Nie ma badań potwierdzających, że cytryna leczy nowotwory. Jest zdrowa, ale nie zastępuje terapii. To popularny mit medyczny.

### Scenariusz 2: Opłaty za Facebooka
**Użytkownik:** Czy Facebook będzie płatny od poniedziałku?
**Bot:** **WERDYKT: FAŁSZ**. To tzw. "łańcuszek internetowy", który krąży od lat. Meta nie ogłosiła wprowadzenia opłat za podstawowe konto.

### Scenariusz 3: Cytat Einsteina
**Użytkownik:** Czy Einstein powiedział, że "Gdy wyginą pszczoły, ludziom zostaną 4 lata życia"?
**Bot:** **WERDYKT: NIEPOTWIERDZONE/WĄTPLIWE**. Instytut Einsteina w Jerozolimie nie ma tego cytatu w swoich archiwach. Prawdopodobnie przypisano mu te słowa, aby nadać im większą wagę, ale nie ma dowodów, że kiedykolwiek to powiedział.

### Scenariusz 4: Oszustwo SMS (Dopłata do paczki)
**Użytkownik:** Dostałem SMS: "Twoja paczka jest wstrzymana z powodu niedopłaty 1.50 PLN. Kliknij w link".
**Bot:** **OSTRZEŻENIE: TO OSZUSTWO (SCAM)!** Nie klikaj w link! Firmy kurierskie nigdy nie proszą o dopłaty przez SMS z dziwnymi linkami. To próba kradzieży pieniędzy z konta bankowego. Zablokuj numer i usuń wiadomość.

## 18. Co obejrzeć? (Movie Guide)
**Opis:** Rekomendacje filmowe.

### Scenariusz 1: Thriller kryminalny
**Użytkownik:** Chcę coś w stylu "Siedem", mroczny kryminał.
**Bot:** Obejrzyj **"Zodiak"** tego samego reżysera (David Fincher) – gęsty klimat i oparte na faktach. Albo **"Labirynt" (Prisoners)** Denisa Villeneuve – trzyma w napięciu do ostatniej sekundy.

### Scenariusz 2: Bajka dla dorosłych
**Użytkownik:** Jakaś animacja, ale mądra.
**Bot:** **"Co w duszy gra" (Soul)** od Pixara. Piękna wizualnie opowieść o sensie życia i jazzie. Wzruszy każdego dorosłego.

### Scenariusz 3: Filmografia aktora
**Użytkownik:** Co jeszcze dobrego zagrał Cillian Murphy (ten z Oppenheimera)?
**Bot:** Koniecznie zobacz serial **"Peaky Blinders"** – to jego życiowa rola. Z filmów: **"Wiatr buszujący w jęczmieniu"** (dramat wojenny) lub **"Incepcja"**, gdzie zagrał świetną rolę drugoplanową.

### Scenariusz 4: Identyfikacja po opisie
**Użytkownik:** Taki film o facecie, który utknął na lotnisku i tam zamieszkał.
**Bot:** To **"Terminal"** ze Stevenem Spielbergiem. W roli głównej Tom Hanks. Ciepły, wzruszający film.

## 19. Co nowego? (News Agent)
**Opis:** Skrót wiadomości.

### Scenariusz 1: Technologia
**Użytkownik:** Co nowego w AI w tym tygodniu?
**Bot:** Google pokazało Gemini 1.5, które analizuje godzinne filmy wideo. OpenAI zapowiedziało model Sora do generowania wideo z tekstu. Dzieje się! Chcesz szczegóły o którymś z nich?

### Scenariusz 2: Kosmos
**Użytkownik:** Co słychać na Marsie?
**Bot:** Łazik Perseverance przesłał nowe zdjęcia delty rzeki, gdzie szuka śladów życia. NASA potwierdziła też, że helikopter Ingenuity zakończył misję po uszkodzeniu śmigła – latał 3 lata dłużej niż planowano!

### Scenariusz 3: Sport (Wyniki)
**Użytkownik:** Jak poszło Idze Świątek wczoraj?
**Bot:** Iga wygrała w dwóch setach 6:2, 6:1 i awansowała do ćwierćfinału turnieju w Madrycie. Mecz trwał tylko godzinę. Jej kolejną rywalką będzie Sabalenka.

### Scenariusz 4: Wybory/Świat
**Użytkownik:** Jakie są wyniki wyborów w [Kraj]?
**Bot:** Wg wstępnych wyników (exit poll) wygrała partia X z wynikiem 35%. Oficjalne wyniki PKW poda jutro rano. Szykuje się zmiana koalicji rządzącej.
