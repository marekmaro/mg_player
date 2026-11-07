# MG Player

Odtwarzacz na Androida: **muzyka, audiobooki i radio internetowe**, obsługiwany jednym kciukiem.

## Co już działa (wersja 0.1)

- **Pokrętło**: każda lista w aplikacji przewija się obrotem kciuka po okręgu, z lekką wibracją przy każdym kroku. Środek „OK” wybiera pozycję.
- **Muzyka**: automatycznie wczytuje pliki z telefonu. Jest „Losuj wszystko” na samej górze menu.
- **Audiobooki**: jeden folder lub album to jedna książka. Aplikacja pamięta rozdział i pozycję każdej książki, a po wznowieniu cofa o 5 s. Prędkość 0.8–2×, przyciski ±30 s.
- **Radio**: wyszukiwarka tysięcy stacji (radio-browser.info), na start popularne polskie stacje. Ulubione (★) i dodawanie własnego linku do strumienia.
- **Teraz gra**: pierścień działa jak pokrętło, w trzech trybach do wyboru: **Przewijanie** (±10 s), **Utwory** (następny/poprzedni) i **Głośność**. Środek pierścienia: dotknięcie = pauza/odtwarzanie, 2× = następny, przytrzymanie = poprzedni.
- Na każdej liście przytrzymanie „OK” wstrzymuje lub wznawia odtwarzanie, bez wychodzenia z listy.
- **Sesje**:
  - **Sen**: 15/30/60 min albo „po rozdziale”, z płynnym ściszaniem
  - **Wyzwanie**: X minut audiobooka dziennie, licznik dni z rzędu
  - **Stoper muzyczny**: ustawiasz czas, a aplikacja układa muzykę dokładnie na ten czas. Kiedy muzyka ucichnie, czas minął. Na koniec 3 sygnały dźwiękowe (kanał alarmu, słychać je nawet przy ściszonej muzyce) i wibracja.
- **Ciekawostki zamiast okładki**: krótki opis wykonawcy (albo autora audiobooka) w środku pierścienia i karta z faktami z Wikipedii. Dotknięcie pokazuje następny fakt. Najpierw szukam w polskiej Wikipedii, potem w angielskiej. Wyniki są zapisywane w telefonie. Do Wikipedii wysyłana jest tylko nazwa wykonawcy. Można to wyłączyć przyciskiem „Ukryj”.
- Odtwarzanie w tle, powiadomienie z kontrolkami, ekran blokady, słuchawki Bluetooth (Media3).

Audiobooki są rozpoznawane po folderze, którego ścieżka zawiera np. `Audiobooks`, `Audiobooki`, `Książki`, albo po znaczniku audiobooka w pliku.

## Jak pobrać APK bez instalowania niczego

1. Wejdź w zakładkę **Actions** w tym repozytorium.
2. Otwórz najnowsze uruchomienie **Build APK** z zielonym ✓.
3. Na dole w sekcji **Artifacts** pobierz `mg-player-debug-apk` (plik ZIP).
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

## Technologia

Kotlin · Jetpack Compose · Media3 (ExoPlayer + MediaSession) · minSdk 26 (Android 8.0) · targetSdk 35

```
app/src/main/java/pl/mgplayer/
├── MainActivity.kt       nawigacja i uprawnienia
├── PlaybackService.kt    odtwarzanie w tle (Media3)
├── PlayerViewModel.kt    logika: kolejki, audiobooki, sesje, radio
├── data/                 biblioteka plików, API radia, zapis ustawień
└── ui/                   motyw, pokrętło, ekrany
```

## Plan dalszy

- [ ] Timer snu i stoper działające także po zamknięciu aplikacji (przeniesienie do serwisu)
- [ ] Korektor: „Bas ↔ Głos” + „Czysty głos” dla audiobooków
- [ ] Okładki albumów
- [ ] Premium (Google Play Billing) i reklama w miejscu okładki (AdMob, tylko przy otwartej aplikacji)
- [ ] Wersja release z podpisem i publikacja w Google Play
