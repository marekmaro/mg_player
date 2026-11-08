# Audyt Equinox 0.8

Stan na wersję 0.8.0. Każdą zmianę w `main` automatycznie sprawdzają testy jednostkowe, Android Lint, budowanie wersji release (R8) oraz testy na emulatorach (opis niżej).

Legenda: ✅ działa / sprawdzone · 🔧 poprawione w tym audycie · ⚠️ ograniczenie lub do zrobienia

## 1. Zgodność z urządzeniami

| Obszar | Stan | Szczegóły |
|---|---|---|
| Najstarszy Android | ✅ | Android 8.0 (API 26), czyli ok. 97% aktywnych telefonów. Test na emulatorze Androida 8.0 przy każdej zmianie. |
| Najnowszy Android | ✅ | targetSdk 35 (Android 15): wymuszony tryb edge-to-edge obsłużony, uprawnienia do odtwarzania w tle (Android 14) zadeklarowane. |
| Android 10 | 🔧 | Skanowanie telefonu potrzebowało `requestLegacyExternalStorage` (ograniczony dostęp do plików tylko na Androidzie 10). Dodane. |
| Android 8–12 a 13+ | ✅ | Uprawnienia: `READ_EXTERNAL_STORAGE` (do 12) i `READ_MEDIA_AUDIO` + powiadomienia (13+). Test na emulatorach 8, 11 i 14. |
| Mały ekran (4", 320 dp) | 🔧 | Wcześniejsze audyty: pierścień zostawia margines do przewijania, powitanie chowa się przy aktywnej karcie, lista mieści się obok pokrętła. |
| Duża czcionka (ułatwienia dostępu) | 🔧 | Test na emulatorze z tekstem 130% znalazł dwa problemy, oba poprawione: przycisk „Rozumiem” w samouczku był poza ekranem (samouczek się teraz przewija), a w poziomie na listę zostawało półtora wiersza (na niskim ekranie nagłówek przewija się razem z listą). |
| Telefon w poziomie | 🔧 | Przy wysokości ekranu poniżej 480 dp nagłówek i karta „Słuchaj dalej” są częścią przewijanej listy. |
| Telefony składane | ✅ | Układ dwupanelowy po rozłożeniu, tryb „namiotu” (pierścień nad zgięciem); obrót i złożenie nie gubią ekranu. Rozłożony składak sprawdzany w teście (symulacja rozmiaru). |
| Tablety, poziomo | ✅ | Pokrętło obok listy, „Teraz gra” w dwóch kolumnach. |
| Chromebook, DeX, mysz | 🔧 | Dotyk nie jest już wymagany do instalacji; kółko myszy kręci pokrętłem. |
| Bez usług Google | ✅ | Aplikacja nie korzysta z Google Play Services, więc działa też na Huawei i telefonach bez Google. Do instalacji z Google Play potrzebny jest oczywiście sklep. |
| Telefony bez wibracji | ✅ | Wibracje są opcjonalne, ich brak niczego nie psuje. |
| Wyłączone animacje | ✅ | Powitanie się nie pokazuje. |
| Agresywne oszczędzanie baterii (Xiaomi, Huawei, Oppo, Samsung…) | 🔧 | Gdy system może usypiać aplikację, w Sesjach pojawia się podpowiedź z przyciskiem do ustawień baterii i poradami dla producenta telefonu (dontkillmyapp.com). Można ją ukryć. |

## 2. Stabilność

| Obszar | Stan | Szczegóły |
|---|---|---|
| Awarie | ✅ | Test na emulatorach przeszukuje logi pod kątem awarii (`FATAL EXCEPTION`) po przejściu przez wszystkie ekrany. |
| Uszkodzony / usunięty plik | ✅ | Pomijany automatycznie (maks. 5 z rzędu). |
| Zerwane radio | ✅ | 3 próby ponownego połączenia; radio działa przy zgaszonym ekranie (blokada usypiania sieci). |
| Brak internetu | ✅ | Radio i ciekawostki pokazują komunikat; ciekawostki ponawiają próbę po 30 s. |
| Odmowa uprawnień | ✅ | Przycisk „Zezwól na dostęp do audio”, a po powrocie z ustawień biblioteka wczytuje się sama. |
| Obrót, składanie, zamknięcie przez system | ✅ | Stos ekranów i stan zapisywane; sesje (sen, stoper, wyzwanie) działają w usłudze odtwarzania. |
| Wersja release (R8) | ✅ | Test na emulatorze sprawdza wersję zoptymalizowaną, a nie tylko debug. Reguły zachowują nazwy zapisywane w ustawieniach. |

## 3. Wydajność i bateria

- ✅ Ekran odświeża się tylko, gdy aplikacja jest widoczna. Usługa budzi się co 2 s zamiast co 0,5 s, gdy nic nie gra.
- ✅ Zapisy na dysk co ok. 5 s i przy pauzie (a nie dwa razy na sekundę).
- ✅ Widżety odświeżane co ok. 5 s tylko podczas odtwarzania i tylko przy widocznej zmianie.
- ✅ Pokrętło: zbiorcze kroki, jedno przewijanie naraz, wibracja najwyżej co 30 ms.
- ✅ R8 (zmniejszanie kodu i zasobów) w wersji do sklepu.
- 🔧 Duże biblioteki: wyszukiwanie utworów po id liczone raz, wiersze listy zapamiętane. Testy wydajności: dobór utworów do stopera i reguły folderów dla 20 000 plików mieszczą się z zapasem w limitach.

## 4. Prywatność i bezpieczeństwo

- ✅ Brak kont, reklam, analityki i śledzenia. Pliki zostają w telefonie.
- ✅ Połączenia z internetem: tylko **radio-browser.info** (lista stacji), **Wikipedia** (ciekawostki, tylko nazwa wykonawcy lub autora; można wyłączyć) i same strumienie radiowe.
- ✅ Klucz podpisu tylko w sekretach GitHuba i u właściciela, nigdy w repozytorium.
- ✅ Komponenty eksportowane tylko tam, gdzie wymaga ich system (usługa odtwarzania dla słuchawek i Android Auto, kafelek z uprawnieniem systemowym). Przyciski widżetów są wewnętrzne.
- ⚠️ `usesCleartextTraffic=true`: część stacji radiowych nadaje tylko przez http, więc to celowe. Dotyczy wyłącznie strumieni i nie przesyła danych użytkownika.
- 🔧 **Polityka prywatności** napisana (`docs/polityka-prywatnosci.md`), a w aplikacji jest ekran „Prywatność” (Biblioteka → Prywatność). ⚠️ Google Play wymaga **publicznego adresu**, a repozytorium jest prywatne, więc trzeba ją opublikować (np. GitHub Pages, Gist, Google Sites).

## 5. Dostępność

- ✅ Opisy dla czytników ekranu na przyciskach ikon, pokrętle, tarczy, folderach i klockach.
- ✅ Kontrast tekstu co najmniej 4,5:1 w obu motywach.
- ✅ Cele dotyku co najmniej 44 dp.
- ✅ Duża czcionka sprawdzana w teście.
- ⚠️ Pokrętło jest gestem. Zawsze jest jednak alternatywa: dotknięcie wiersza, przyciski na tarczy i „Duże przyciski” w „Ułóż ekran”.

## 6. Znane ograniczenia (do kolejnych wersji)

- 🔧 Rozdziały wewnątrz plików M4B/M4A (znacznik „chpl”, zapisywany m.in. przez ffmpeg): numer i tytuł, przeskakiwanie, „sen po rozdziale”. ⚠️ Rozdziały zapisane jako osobna ścieżka tekstowa (format Apple) nie są jeszcze odczytywane.
- 🔧 Widżety są jasne lub ciemne zgodnie z motywem telefonu.
- 🔧 Zależności zaktualizowane do najnowszych stabilnych wersji: AGP 9.5.1, Gradle 9.9, Kotlin 2.4.21, Compose BOM 2026.09, Media3 1.11.1; compileSdk 37, targetSdk 36 (wymóg Google Play dla nowych aplikacji). Lint: 40 ostrzeżeń stylu, 0 błędów.
- ⚠️ Testy na prawdziwych telefonach: jak dotąd jeden (Samsung, uwagi o płynności pokrętła wprowadzone w 0.6). Przed publikacją warto przetestować w grupie testerów, czego i tak wymaga Google Play (12 osób przez 14 dni).

## 7. Wyniki testów na emulatorach (wersja 0.8 na nowych zależnościach)

| Urządzenie testowe | Wynik | Uwagi |
|---|---|---|
| Android 8.0 (API 26), ekran 4" | ✅ | Wszystkie ekrany, playlisty, biblioteka, skanowanie, widżety. Kafelek pominięty, bo emulator Androida 8 nie pozwala go dodać poleceniem (sam kafelek działa od Androida 7). |
| Android 11 (API 30), czcionka 130% | ✅ | Po dwóch poprawkach układu (wyżej). |
| Android 14 (API 34) | ✅ | Pełny zestaw z kafelkiem i symulacją rozłożonego składaka. |

Każdy test przechodzi: ekran główny, motywy (Telefon / Dzień / Noc), pokrętło, muzykę, samouczek, klocki, audiobook, sesje, radio, obrót ekranu, rozłożony składak, playlisty, bibliotekę i skanowanie, widżety, kafelek. Na koniec sprawdza log pod kątem awarii.

## 8. Jak powtórzyć sprawdzenie

- **GitHub → Actions → Build APK:** testy jednostkowe, lint, release i 3 emulatory.
- **Lokalnie:** `./gradlew testDebugUnitTest lintDebug assembleRelease`
