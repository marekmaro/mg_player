# Jak zbudować aplikację Sundial

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
   - `sundial-debug-apk`: wersja do testów (polecana na co dzień),
   - `sundial-release-test-apk`: wersja zoptymalizowana, taka jak w sklepie, ale podpisana kluczem testowym.
4. Rozpakuj ZIP i przenieś plik `.apk` na telefon (kabel, Dysk Google, e-mail do siebie).
5. Otwórz plik na telefonie. Android zapyta o zgodę na instalację z tego źródła: zezwól.

Wersji debug i release nie da się mieć jednocześnie, bo mają różne podpisy. Przy zmianie odinstaluj poprzednią.

**Widżety:** przytrzymaj palec na pustym miejscu ekranu głównego, wybierz **Widżety**, potem **Sundial**, i wybierz **Odtwarzacz** (4×2) albo **Pierścień** (2×2).
**Kafelek:** rozwiń szybkie ustawienia (dwa razy przesuń w dół od góry ekranu), dotknij ✏️ (edytuj) i przeciągnij **Stoper muzyczny** na listę.

---

## B. Wersja do Google Play (plik AAB)

Google Play przyjmuje plik **AAB** podpisany Twoim **kluczem przesyłania** (upload key). Klucz tworzysz raz i **przechowujesz bezpiecznie**: kopia na pendrivie i w menedżerze haseł.

> Google Play i tak podpisuje aplikację własnym kluczem (Play App Signing). Twój klucz służy tylko do przesyłania, a gdybyś go zgubił, można go zresetować przez pomoc Google. Mimo to dbaj o niego.

### 1. Utwórz klucz (jednorazowo)

Potrzebujesz Javy (`keytool`). Jest w Android Studio, a osobno do pobrania z https://adoptium.net. W terminalu:

```bash
keytool -genkeypair -v -keystore sundial-upload.jks -alias sundial -keyalg RSA -keysize 4096 -validity 10000
```

Program zapyta o hasło i dane (imię, kraj). **Zapisz hasło.**
Pliku `sundial-upload.jks` **nigdy nie wrzucaj do repozytorium.**

### 2. Dodaj klucz jako sekrety na GitHubie

Zamień plik klucza na tekst (base64):

- **Windows (PowerShell):**
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("sundial-upload.jks")) | Set-Clipboard
  ```
- **macOS:**
  ```bash
  base64 -i sundial-upload.jks | pbcopy
  ```
- **Linux:**
  ```bash
  base64 -w0 sundial-upload.jks
  ```

Na GitHubie: **Settings → Secrets and variables → Actions → New repository secret**. Dodaj 4 sekrety:

| Nazwa | Wartość |
|---|---|
| `SUNDIAL_KEYSTORE_BASE64` | tekst skopiowany powyżej |
| `SUNDIAL_KEYSTORE_PASSWORD` | hasło do klucza |
| `SUNDIAL_KEY_ALIAS` | `sundial` |
| `SUNDIAL_KEY_PASSWORD` | hasło do klucza (to samo, jeśli nie podałeś osobnego) |

### 3. Zbuduj wersję do sklepu

1. **Actions → Release (Google Play) → Run workflow**.
2. Po kilku minutach pobierz artefakt `sundial-release`. W środku jest:
   - `app-release.aab`, czyli plik do wysłania do Google Play,
   - `app-release.apk`, czyli ta sama wersja do zainstalowania na telefonie.

### 4. Przed każdą nową wersją w sklepie

W `app/build.gradle.kts` zwiększ `versionCode` (np. 3 → 4) i zmień `versionName` (np. "0.3.0" → "0.4.0"). Google Play odrzuci plik z tym samym `versionCode`.

### 5. Google Play Console (w skrócie)

1. Załóż konto na https://play.google.com/console. Opłata to 25 USD jednorazowo, do tego weryfikacja tożsamości.
2. **Utwórz aplikację:** nazwa Sundial, język polski, bezpłatna.
3. Uzupełnij **zawartość aplikacji**: politykę prywatności (link), kategorię wiekową, bezpieczeństwo danych, reklamy (nie ma).
4. Wypełnij **wpis w sklepie**: opis z `docs/opis-sklep.md`, ikonę 512×512, grafikę 1024×500 i co najmniej 2 zrzuty ekranu.
5. **Testy zamknięte:** nowe konta prywatne muszą mieć **co najmniej 12 testerów przez 14 dni**, zanim mogą opublikować aplikację dla wszystkich.
6. Potem: **Produkcja → Utwórz wersję → wgraj `app-release.aab`**.

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
| `./gradlew assembleDebug` | APK do testów → `app/build/outputs/apk/debug/` |
| `./gradlew testDebugUnitTest` | testy jednostkowe |
| `./gradlew lintDebug` | analiza kodu (raport w `app/build/reports/`) |
| `./gradlew bundleRelease` | AAB do sklepu (z kluczem, patrz niżej) |

Podpisany build lokalnie (macOS/Linux):

```bash
export SUNDIAL_KEYSTORE=/ścieżka/do/sundial-upload.jks
export SUNDIAL_KEYSTORE_PASSWORD=...
export SUNDIAL_KEY_ALIAS=sundial
export SUNDIAL_KEY_PASSWORD=...
./gradlew bundleRelease
```

Na Windowsie zamiast `export` użyj `set` (cmd) albo `$env:NAZWA="..."` (PowerShell), a zamiast `./gradlew` wpisz `gradlew.bat`.
