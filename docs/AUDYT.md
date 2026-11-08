# Audyt Equinox 0.7

Stan na wersję 0.7.0. Każdą zmianę w `main` automatycznie sprawdzają testy jednostkowe, Android Lint, budowanie wersji release (R8) oraz testy na emulatorach (opis niżej).

Legenda: ✅ działa / sprawdzone · 🔧 poprawione w tym audycie · ⚠️ ograniczenie lub do zrobienia

## 1. Zgodność z urządzeniami

| Obszar | Stan | Szczegóły |
|---|---|---|
| Najstarszy Android | ✅ | Android 8.0 (API 26), czyli ok. 97% aktywnych telefonów. Test na emulatorze Androida 8.0 przy każdej zmianie. |
| Najnowszy Android | ✅ | targetSdk 35 (Android 15): wymuszony tryb edge-to-edge obsłużony, uprawnienia do odtwarzania w tle (Android 14) zadeklarowane. |
| Android 10 | 🔧 | Skanowanie telefonu potrzebowało `requestLegacyExternalStorage` (ograniczony dostęp do plików tylko na Androidzie 10). Dodane. |
| Android 8–12 a 13+ | ✅ | Uprawnienia: `READ_EXTERNAL_STORAGE` (do 12) i `READ_MEDIA_AUDIO` + powiadomienia (13+). Test na emulatorach 8, 11 i 14. |
| Mały ekran (4", 320 dp) | 🔧 | Wcześniejsze audyty: pierścień zostawia margines do przewijania, powitanie chowa się przy aktywnej karcie, lista mieści się obok pokrętła. |
| Duża czcionka (ułatwienia dostępu) | ✅ | Test na emulatorze z tekstem 130%. Listy i ekrany przewijają się, nic nie jest ucięte na stałe. |
| Telefony składane | ✅ | Układ dwupanelowy po rozłożeniu, tryb „namiotu” (pierścień nad zgięciem); obrót i złożenie nie gubią ekranu. Rozłożony składak sprawdzany w teście (symulacja rozmiaru). |
| Tablety, poziomo | ✅ | Pokrętło obok listy, „Teraz gra” w dwóch kolumnach. |
| Chromebook, DeX, mysz | 🔧 | Dotyk nie jest już wymagany do instalacji; kółko myszy kręci pokrętłem. |
| Bez usług Google | ✅ | Aplikacja nie korzysta z Google Play Services, więc działa też na Huawei i telefonach bez Google. Do instalacji z Google Play potrzebny jest oczywiście sklep. |
| Telefony bez wibracji | ✅ | Wibracje są opcjonalne, ich brak niczego nie psuje. |
| Wyłączone animacje | ✅ | Powitanie się nie pokazuje. |
| Agresywne oszczędzanie baterii (Xiaomi, Huawei, Oppo) | ⚠️ | Odtwarzanie działa jako usługa pierwszoplanowa z powiadomieniem, co zwykle wystarcza. Na części telefonów system może jednak zatrzymać timer snu po wyłączeniu ekranu. Pomoc dla użytkownika: dontkillmyapp.com. Do rozważenia: podpowiedź w aplikacji. |

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
- ⚠️ Bardzo duże biblioteki (10 000+ plików) nie były testowane. Wczytywanie jest w tle, ale lista playlist i stoper przetwarzają całą bibliotekę naraz.

## 4. Prywatność i bezpieczeństwo

- ✅ Brak kont, reklam, analityki i śledzenia. Pliki zostają w telefonie.
- ✅ Połączenia z internetem: tylko **radio-browser.info** (lista stacji), **Wikipedia** (ciekawostki, tylko nazwa wykonawcy lub autora; można wyłączyć) i same strumienie radiowe.
- ✅ Klucz podpisu tylko w sekretach GitHuba i u właściciela, nigdy w repozytorium.
- ✅ Komponenty eksportowane tylko tam, gdzie wymaga ich system (usługa odtwarzania dla słuchawek i Android Auto, kafelek z uprawnieniem systemowym). Przyciski widżetów są wewnętrzne.
- ⚠️ `usesCleartextTraffic=true`: część stacji radiowych nadaje tylko przez http, więc to celowe. Dotyczy wyłącznie strumieni i nie przesyła danych użytkownika.
- ⚠️ **Polityka prywatności jest wymagana przez Google Play** (aplikacja łączy się z internetem i czyta pliki audio). Do napisania przed publikacją.

## 5. Dostępność

- ✅ Opisy dla czytników ekranu na przyciskach ikon, pokrętle, tarczy, folderach i klockach.
- ✅ Kontrast tekstu co najmniej 4,5:1 w obu motywach.
- ✅ Cele dotyku co najmniej 44 dp.
- ✅ Duża czcionka sprawdzana w teście.
- ⚠️ Pokrętło jest gestem. Zawsze jest jednak alternatywa: dotknięcie wiersza, przyciski na tarczy i „Duże przyciski” w „Ułóż ekran”.

## 6. Znane ograniczenia (do kolejnych wersji)

- ⚠️ Rozdziały zapisane wewnątrz jednego pliku M4B nie są widoczne osobno.
- ⚠️ Widżety mają zawsze ciemny wygląd (nie zmieniają się z motywem).
- ⚠️ Zależności (Media3, Compose, AGP) są o kilka wersji starsze od najnowszych. Działają stabilnie, a aktualizację warto zrobić osobno i przetestować.
- ⚠️ Testy na prawdziwych telefonach: jak dotąd jeden (Samsung, uwagi o płynności pokrętła wprowadzone w 0.6). Przed publikacją warto przetestować w grupie testerów, czego i tak wymaga Google Play (12 osób przez 14 dni).

## 7. Jak powtórzyć sprawdzenie

- **GitHub → Actions → Build APK:** testy jednostkowe, lint, release i 3 emulatory.
- **Lokalnie:** `./gradlew testDebugUnitTest lintDebug assembleRelease`
