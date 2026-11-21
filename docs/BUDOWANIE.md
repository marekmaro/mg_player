# Jak zbudować aplikację Equinox

Są trzy drogi, od najprostszej:

| Co chcesz zrobić | Droga | Co trzeba zainstalować |
|---|---|---|
| Zainstalować aplikację na swoim telefonie | **A. GitHub (APK)** | nic |
| Wysłać aplikację do Google Play | **B. GitHub (AAB do sklepu)** | Javę, tylko raz do stworzenia klucza |
| Zmieniać kod i testować na bieżąco | **C. Android Studio** | Android Studio |

---

## A. APK na telefon, bez instalowania czegokolwiek

1. Wejdź na https://github.com/marekmaro/mg_player/actions
2. Kliknij najnowsze uruchomienie **Build APK** z zielonym ✓.
3. Na dole w **Artifacts** pobierz:
   - `equinox-debug-apk`: wersja do testów (polecana na co dzień),
   - `equinox-release-test-apk`: wersja zoptymalizowana, taka jak w sklepie, ale podpisana kluczem testowym.
4. Rozpakuj ZIP i przenieś plik `.apk` na telefon (kabel, Dysk Google, e-mail do siebie).
5. Otwórz plik na telefonie. Android zapyta o zgodę na instalację z tego źródła: zezwól.

Wersji debug i release nie da się mieć jednocześnie, bo mają różne podpisy. Przy zmianie odinstaluj poprzednią.

**Widżety:** przytrzymaj palec na pustym miejscu ekranu głównego, wybierz **Widżety**, potem **Equinox**, i wybierz **Odtwarzacz** (4×2) albo **Pierścień** (2×2).
**Kafelek:** rozwiń szybkie ustawienia (dwa razy przesuń w dół od góry ekranu), dotknij ✏️ (edytuj) i przeciągnij **Stoper muzyczny** na listę.

---

## B. Wersja do Google Play (plik AAB)

Google Play przyjmuje plik **AAB** podpisany Twoim **kluczem przesyłania** (upload key). Klucz tworzysz raz i **przechowujesz bezpiecznie**: kopia na pendrivie i w menedżerze haseł.

> Google Play i tak podpisuje aplikację własnym kluczem (Play App Signing). Twój klucz służy tylko do przesyłania, a gdybyś go zgubił, można go zresetować przez pomoc Google. Mimo to dbaj o niego.

### 1. Utwórz klucz (jednorazowo)

Potrzebujesz Javy (`keytool`). Jest w Android Studio, a osobno do pobrania z https://adoptium.net. W terminalu:

```bash
keytool -genkeypair -v -keystore equinox-upload.jks -alias equinox -keyalg RSA -keysize 4096 -validity 10000
```

Program zapyta o hasło i dane (imię, kraj). **Zapisz hasło.**
Pliku `equinox-upload.jks` **nigdy nie wrzucaj do repozytorium.**

### 2. Dodaj klucz jako sekrety na GitHubie

Zamień plik klucza na tekst (base64):

- **Windows (PowerShell):**
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path "equinox-upload.jks"))) | Set-Clipboard
  ```
  Uruchom to w folderze, w którym leży plik klucza (`Resolve-Path` zamienia nazwę na pełną ścieżkę; bez tego Windows szuka pliku w `C:\Windows`). Nie wiesz, gdzie jest plik? `Get-ChildItem -Path $HOME -Recurse -Filter equinox-upload.jks -ErrorAction SilentlyContinue`
- **macOS:**
  ```bash
  base64 -i equinox-upload.jks | pbcopy
  ```
- **Linux:**
  ```bash
  base64 -w0 equinox-upload.jks
  ```

Na GitHubie: **Settings → Secrets and variables → Actions → New repository secret**. Dodaj 4 sekrety:

| Nazwa | Wartość |
|---|---|
| `EQUINOX_KEYSTORE_BASE64` | tekst skopiowany powyżej |
| `EQUINOX_KEYSTORE_PASSWORD` | hasło do klucza |
| `EQUINOX_KEY_ALIAS` | `equinox` |
| `EQUINOX_KEY_PASSWORD` | hasło do klucza (to samo, jeśli nie podałeś osobnego) |

### 3. Zbuduj wersję do sklepu

Aplikacja ma dwie wersje:
- **github** – z wbudowaną aktualizacją z GitHub Releases (instalacja spoza sklepu),
- **play** – dla Google Play, bez samodzielnej aktualizacji (zabrania jej regulamin sklepu).

1. **Actions → Release → Run workflow** – tylko buduje, niczego nie publikuje.
2. Po kilku minutach pobierz artefakt `equinox-release`. W środku jest:
   - `equinox-X.Y.Z-play.aab`, czyli plik do wysłania do Google Play,
   - `equinox-X.Y.Z.apk`, czyli wersja „github” do zainstalowania na telefonie.

### 3a. Nowa wersja z aktualizacją dla zainstalowanych aplikacji (GitHub Releases)

Wersja „github” raz na dobę sprawdza najnowsze wydanie w repozytorium i proponuje aktualizację
(Ustawienia → Aktualizacje, kropka przy ☼). Żeby wydać nową wersję:

1. W `app/build.gradle.kts` zwiększ `versionCode` i `versionName` (np. 0.15.0), zatwierdź i wypchnij.
2. Utwórz tag z listą zmian – jej treść zobaczą użytkownicy w aplikacji:
   ```bash
   git tag -a v0.15.0 -m "- Nowość pierwsza
   - Poprawka druga"
   git push origin v0.15.0
   ```
3. Workflow **Release** sprawdzi, że tag zgadza się z `versionName`, zbuduje podpisany APK i utworzy wydanie.

⚠️ Repozytorium jest publiczne, więc plik z wydania może pobrać każdy, kto zna adres – to jest publikacja.
Aktualizacja zainstaluje się tylko wtedy, gdy plik jest podpisany tym samym kluczem co zainstalowana aplikacja.

### 4. Przed każdą nową wersją w sklepie

W `app/build.gradle.kts` zwiększ `versionCode` (np. 3 → 4) i zmień `versionName` (np. "0.3.0" → "0.4.0"). Google Play odrzuci plik z tym samym `versionCode`.

### 5. Google Play Console (w skrócie)

1. Załóż konto na https://play.google.com/console. Opłata to 25 USD jednorazowo, do tego weryfikacja tożsamości.
2. **Utwórz aplikację:** nazwa Equinox, język polski, bezpłatna.
3. Uzupełnij **zawartość aplikacji**: politykę prywatności (link), kategorię wiekową, bezpieczeństwo danych, reklamy (nie ma).
4. Wypełnij **wpis w sklepie**: opis z `docs/opis-sklep.md`, ikonę 512×512, grafikę 1024×500 i co najmniej 2 zrzuty ekranu.
5. **Testy zamknięte:** nowe konta prywatne muszą mieć **co najmniej 12 testerów przez 14 dni**, zanim mogą opublikować aplikację dla wszystkich.
6. Potem: **Produkcja → Utwórz wersję → wgraj `equinox-X.Y.Z-play.aab`**.

---

## C. Android Studio (do pracy nad kodem)

1. Zainstaluj **Android Studio**: https://developer.android.com/studio (zawiera Javę, Android SDK i emulator).
2. Zainstaluj **Git**: https://git-scm.com
3. Pobierz projekt:
   ```bash
   git clone https://github.com/marekmaro/mg_player.git
   ```
4. Android Studio → **File → Open** → folder `mg_player`. Poczekaj, aż Gradle pobierze wszystko (za pierwszym razem kilka minut).
5. Podłącz telefon z **debugowaniem USB** (Ustawienia → O telefonie → 7× dotknij „Numer kompilacji” → Opcje programisty → Debugowanie USB) albo utwórz emulator (Device Manager).
6. Kliknij ▶ **Run**.

Przydatne polecenia (w terminalu, w folderze projektu):

| Polecenie | Co robi |
|---|---|
| `./gradlew assembleGithubDebug` | APK do testów → `app/build/outputs/apk/github/debug/` |
| `./gradlew testGithubDebugUnitTest` | testy jednostkowe |
| `./gradlew lintGithubDebug` | analiza kodu (raport w `app/build/reports/`) |
| `./gradlew bundlePlayRelease` | AAB do Google Play (z kluczem, patrz niżej) |
| `./gradlew assembleGithubRelease` | podpisany APK z aktualizacją z GitHuba |

Podpisany build lokalnie (macOS/Linux):

```bash
export EQUINOX_KEYSTORE=/ścieżka/do/equinox-upload.jks
export EQUINOX_KEYSTORE_PASSWORD=...
export EQUINOX_KEY_ALIAS=equinox
export EQUINOX_KEY_PASSWORD=...
./gradlew bundlePlayRelease assembleGithubRelease
```

Na Windowsie zamiast `export` użyj `set` (cmd) albo `$env:NAZWA="..."` (PowerShell), a zamiast `./gradlew` wpisz `gradlew.bat`.
