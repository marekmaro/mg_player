# Equinox

<img src="docs/ikony/equinox-ikona-play-512.png" width="96" alt="Ikona Equinox: słońce nad horyzontem">

Odtwarzacz na Androida: **muzyka, audiobooki i radio internetowe**, obsługiwany jednym kciukiem.

*Equinox* to równonoc, czyli równowaga dnia i nocy. Za dnia aplikacja pomaga się skupić (stoper muzyczny, wyzwanie), a nocą wycisza (timer snu, audiobooki, radio). Ma jasny motyw **Dzień** i ciemny **Noc**. Domyślnie motyw działa **jak w telefonie** (tryb ciemny systemu). Można też wybrać **wg pory dnia** (7:00 i 19:00) albo jeden motyw na stałe.

## Co już działa (wersja 0.11)

- **Wznawianie**: aplikacja pamięta, co i gdzie grało (muzyka, audiobook, radio), także po zamknięciu aplikacji lub restarcie telefonu. Słuchawki Bluetooth i systemowe kontrolki multimediów mogą wznowić odtwarzanie.
- **Znaczniki**: ♥ Ulubione, ★ Perełki, ☼ Na dzień, ☾ Na noc – każdy z własną playlistą automatyczną; znaczki widać na listach.
- **Mądre losowanie**: bez tego samego wykonawcy pod rząd, niedawno słuchane na koniec, ♥ i ★ częściej; włączenie w trakcie nie przerywa utworu, wyłączenie przywraca kolejność (można przełączyć na zwykłe).
- **Wyszukiwanie**: bez polskich znaków („burza” znajdzie „burzą”), utwory, wykonawcy, albumy, audiobooki, playlisty i stacje, ostatnie wyszukiwania, przycisk ⌕ na listach.
- **Nowości**: wiersz w menu z utworami z ostatnich 30 dni i znacznik „nowe” na liście.
- **Jak w każdym dobrym odtwarzaczu**: powtarzanie (wszystko / jeden), losowo, wykonawcy, albumy, foldery, wyszukiwarka, „zagraj jako następny” i „do kolejki”, playlisty automatyczne (Ulubione, Ostatnio dodane, Najczęściej słuchane).
- **Brzmienie**: korektor z 10 ustawieniami o ludzkich nazwach (Ciepły wieczór, Bas w kościach, Klub o północy, Czysty głos…), schowany pod napisem ♪ na ekranie odtwarzania.
- **Okładki**: okładka zapisana w pliku w środku pierścienia (albo ciekawostka o wykonawcy).
- **Statystyki** (tylko na telefonie, do ukrycia): zegar słuchania w ciągu doby, wykonawca tygodnia, odkrycia z przyciskiem „Zagraj coś nieodkrytego”.
- **Ustawienia** wysuwane jak wschód słońca: język, wygląd, brzmienie, pokrętło, dodatki.
- **Dwa języki**: polski i angielski, domyślnie język telefonu.
- **Pokrętło**: każda lista przewija się obrotem kciuka po okręgu. Im szybciej kręcisz, tym dalej skacze. Przy długich listach pojawia się duża litera, a zaznaczenie zawsze nadąża. Środek „OK” wybiera, przytrzymanie „OK” to pauza.
- **Muzyka**: automatycznie wczytuje pliki z telefonu. Jest „Losuj wszystko” na samej górze menu.
- **Audiobooki**: jeden folder lub album to jedna książka. Aplikacja pamięta rozdział i pozycję każdej książki, a po wznowieniu cofa o 5 s. Prędkość 0.8–2×, przyciski ±30 s.
- **Playlisty**: tworzenie, zmiana nazwy, usuwanie. Utwór dodasz przyciskiem „+” na liście muzyki albo „＋ Do playlisty” na ekranie odtwarzania, a usuniesz go „✕”. Przy dodawaniu jest wyszukiwarka.
- **Biblioteka**: każdy folder oznaczasz jako Auto / Muzyka / Audiobook / Pomiń (dotyczy też podfolderów). Przycisk **Skanuj telefon** dodaje pliki, których system jeszcze nie zauważył.
- **Formaty**: MP3, AAC/M4A, M4B, FLAC, OGG, Opus, WAV, AMR. Zależnie od telefonu też ALAC i AC-3. Nieobsługiwane: WMA, APE, DSD.
- **Radio**: wyszukiwarka tysięcy stacji (radio-browser.info), na start popularne polskie stacje. Ulubione (★) i dodawanie własnego linku do strumienia (także HLS .m3u8 i listy .pls / .m3u).
- **Teraz gra**: pierścień działa jak pokrętło, w trzech trybach do wyboru: **Przewijanie** (±10 s), **Utwory** (następny/poprzedni) i **Głośność**. Środek pierścienia: dotknięcie = pauza/odtwarzanie, 2× = następny, przytrzymanie = poprzedni.
- Na każdej liście przytrzymanie „OK” wstrzymuje lub wznawia odtwarzanie, bez wychodzenia z listy.
- **Sesje**:
  - **Sen**: 15/30/60 min albo „po rozdziale”, z płynnym ściszaniem
  - **Wyzwanie**: X minut audiobooka dziennie, licznik dni z rzędu
  - **Stoper muzyczny**: ustawiasz czas, a aplikacja układa muzykę dokładnie na ten czas. Kiedy muzyka ucichnie, czas minął. Na koniec melodyjny sygnał do wyboru (domyślnie Fanfara 8-bit, w menu pod czasem także Klasyczny, Dzwonek i Delikatny; kanał alarmu, słychać go nawet przy ściszonej muzyce) i wibracja. Próbki są w `docs/`.
- **Ciekawostki zamiast okładki**: krótki opis wykonawcy (albo autora audiobooka) w środku pierścienia i karta z faktami z Wikipedii. Dotknięcie pokazuje następny fakt. Najpierw szukam w polskiej Wikipedii, potem w angielskiej. Wyniki są zapisywane w telefonie. Do Wikipedii wysyłana jest tylko nazwa wykonawcy. Można to wyłączyć przyciskiem „Ukryj”.
- **Powitanie**: krótka animacja przy starcie (podziałka, pierścień, obrót dnia i nocy, powitanie zależne od pory dnia). Dotknięcie ją pomija; przy wyłączonych animacjach w systemie jej nie ma.
- **Klocki ekranu odtwarzania**: ekran „Teraz gra” składa się z klocków (tytuł, tryby pierścienia, przyciski, audiobook, ciekawostki, następne, dziś, zegar, sesje). W „▦ Ułóż ekran” włączasz je, wyłączasz i ustawiasz kolejność.
- **Stabilność**: uszkodzone pliki są pomijane, zerwane radio łączy się ponownie, biblioteka sama się odświeża po dodaniu plików, a radio działa przy zgaszonym ekranie.
- **Widżety**: „Odtwarzacz” (4×2: pierścień, tytuł, ⏮ ⏯ ⏭) i „Pierścień” (2×2: środek wstrzymuje i wznawia).
- **Kafelek w szybkich ustawieniach „Stoper muzyczny”**: jedno dotknięcie uruchamia stoper, a kafelek pokazuje, ile zostało.
- **Telefony składane i tablety**: po rozłożeniu układ dwupanelowy. W trybie „namiotu” pierścień jest nad zgięciem, a sterowanie pod nim. Obrót ani złożenie telefonu nie gubi otwartego ekranu.
- Timer snu, stoper, wyzwanie i zapis pozycji audiobooka działają w serwisie odtwarzania, także po zamknięciu aplikacji.
- Odtwarzanie w tle, powiadomienie z kontrolkami, ekran blokady, słuchawki Bluetooth (Media3).

Audiobooki są rozpoznawane po folderze, którego ścieżka zawiera np. `Audiobooks`, `Audiobooki`, `Książki`, albo po znaczniku audiobooka w pliku.

## Jak zbudować aplikację

Pełna instrukcja, w tym wersja do Google Play i podpisywanie: **[docs/BUDOWANIE.md](docs/BUDOWANIE.md)**

## Jak pobrać APK bez instalowania niczego

1. Wejdź w zakładkę **Actions** w tym repozytorium.
2. Otwórz najnowsze uruchomienie **Build APK** z zielonym ✓.
3. Na dole w sekcji **Artifacts** pobierz `equinox-debug-apk` (plik ZIP).
4. Rozpakuj ZIP i przenieś `app-debug.apk` na telefon.
5. Otwórz plik na telefonie i zezwól na instalację z nieznanych źródeł.

Każda zmiana w gałęzi `main` automatycznie buduje nowy APK.

## Praca na własnym komputerze

1. Zainstaluj [Android Studio](https://developer.android.com/studio). Zawiera Javę, Android SDK i emulator.
2. Zainstaluj [Git](https://git-scm.com).
3. Sklonuj repozytorium:
   ```bash
   git clone https://github.com/marekmaro/mg_player.git
   ```
4. W Android Studio wybierz **File → Open** i wskaż folder `mg_player`.
5. Podłącz telefon z włączonym **debugowaniem USB** (Ustawienia → O telefonie → 7× „Numer kompilacji” → Opcje programisty) albo uruchom emulator.
6. Kliknij ▶ **Run**.

Budowanie z wiersza poleceń:
```bash
./gradlew assembleDebug
```

## Audyt

Raport zgodności, stabilności, prywatności i dostępności: **[docs/AUDYT.md](docs/AUDYT.md)**

## Testy

Każda zmiana w `main` uruchamia w GitHub Actions:
- **testy jednostkowe** (`./gradlew testDebugUnitTest`): formatowanie czasu, dobór utworów do stopera, rozpoznawanie wykonawców, ciekawostki, sygnały,
- **Android Lint**,
- **test na emulatorze** wersji release (R8) (`.github/scripts/smoke.py`): instalacja, pliki testowe, przejście po wszystkich ekranach, kręcenie pokrętłem, obrót ekranu, symulacja rozłożonego składaka, widżety, kliknięcie kafelka i sprawdzenie, czy aplikacja się nie wywraca. Zrzuty ekranu są w artefakcie `smoke-screenshots`.

## Technologia

Kotlin · Jetpack Compose · Media3 (ExoPlayer + MediaSession) · minSdk 26 (Android 8.0) · targetSdk 35

```
app/src/main/java/io/github/marekmaro/equinox/
├── MainActivity.kt       nawigacja i uprawnienia
├── PlaybackService.kt    odtwarzanie w tle (Media3) + sesje
├── Core.kt               wspólny stan sesji, dobór utworów do stopera
├── EndChime.kt           sygnały końca stopera (generowane w kodzie)
├── PlayerViewModel.kt    logika: kolejki, audiobooki, sesje, radio
├── data/                 biblioteka plików, API radia, zapis ustawień
└── ui/                   motyw, pokrętło, ekrany
```

## Plan dalszy

- [ ] Korektor: „Bas ↔ Głos” + „Czysty głos” dla audiobooków
- [ ] Okładki albumów
- [ ] Premium (Google Play Billing) i reklama w miejscu okładki (AdMob, tylko przy otwartej aplikacji)
- [ ] Opublikować politykę prywatności pod publicznym adresem (tekst: docs/polityka-prywatnosci.md)
- [ ] Publikacja w Google Play (wersja z podpisem jest gotowa, zobacz docs/BUDOWANIE.md)
