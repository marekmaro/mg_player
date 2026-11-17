"""
Przejście po ekranach Equinox na emulatorze (przez adb + uiautomator).
Robi zrzuty ekranu, kręci pokrętłem i sprawdza logcat pod kątem awarii.
Kończy się błędem, jeśli aplikacja się wywróci albo nie znajdzie kluczowych napisów.
"""
import math
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG, OUT = sys.argv[1], sys.argv[2]
problems = []
log = open(f"{OUT}/kroki.txt", "w", encoding="utf-8")


def note(msg):
    print(msg)
    log.write(msg + "\n")
    log.flush()


def adb(*args, check=False):
    return subprocess.run(["adb", *args], capture_output=True, text=True, check=check).stdout


def shot(name):
    with open(f"{OUT}/{name}.png", "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
    note(f"zrzut: {name}")


def nodes():
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        xml = adb("exec-out", "cat", "/sdcard/ui.xml")
        try:
            root = ET.fromstring(xml)
            break
        except ET.ParseError:
            time.sleep(1)
    else:
        return []
    out = []
    for n in root.iter("node"):
        b = re.findall(r"\d+", n.get("bounds", ""))
        if len(b) == 4:
            x1, y1, x2, y2 = map(int, b)
            out.append((n.get("text", ""), n.get("content-desc", ""), (x1 + x2) // 2, (y1 + y2) // 2, x2 - x1))
    return out


def find(text, exact=False, desc=False):
    """Szuka napisu; „A|B” = wariant polski albo angielski (aplikacja jest dwujęzyczna)."""
    if "|" in text:
        for alt in text.split("|"):
            r = find(alt, exact, desc)
            if r:
                return r
        return None
    for t, d, x, y, w in nodes():
        v = d if desc else t
        if (v == text) if exact else (text in v):
            return x, y, w
    return None


def screen_size():
    size = re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))
    return (int(size[-1][0]), int(size[-1][1])) if size else (1080, 1920)


W, H = screen_size()
note(f"ekran: {W}x{H}  gęstość: {adb('shell', 'wm', 'density').strip()}")


LOW = (0.92, 0.55)  # przewijanie z dołu ekranu (pod pierścieniem na ekranie odtwarzania)


def find_scroll(text, exact=False, desc=False, zone=(0.55, 0.25)):
    """Szuka napisu; jeśli go nie widać, przewija listę w górę (maks. 3 razy)."""
    pos = find(text, exact, desc)
    tries = 0
    while not pos and tries < 3:
        # przewijanie przy lewej krawędzi, żeby nie kręcić pokrętłem
        x = str(int(W * 0.12))  # na liście, ale z dala od pokrętła
        adb("shell", "input", "swipe", x, str(int(H * zone[0])), x, str(int(H * zone[1])), "400")
        time.sleep(1)
        pos = find(text, exact, desc)
        tries += 1
    return pos


def tap(text, exact=False, desc=False, required=True, zone=(0.55, 0.25)):
    pos = find_scroll(text, exact, desc, zone)
    if not pos:
        if required:
            problems.append(f"nie znaleziono: {text!r}")
        note(f"BRAK: {text!r}")
        seen = [t or d for t, d, *_ in nodes() if (t or d)]
        note("  na ekranie: " + " | ".join(seen)[:400])
        return False
    adb("shell", "input", "tap", str(pos[0]), str(pos[1]))
    note(f"tap: {text!r}")
    time.sleep(1.5)
    return True


def expect(text, step, zone=(0.55, 0.25)):
    if find_scroll(text, zone=zone):
        note(f"OK [{step}]: widać {text!r}")
    else:
        problems.append(f"[{step}] brak napisu {text!r}")
        note(f"PROBLEM [{step}]: brak {text!r}")


def rotate(cx, cy, r, degrees, steps=24):
    """Obrót palcem po okręgu (zgodnie z ruchem wskazówek dla degrees > 0)."""
    start = -90.0
    pts = [(cx + r * math.cos(math.radians(start + degrees * i / steps)),
            cy + r * math.sin(math.radians(start + degrees * i / steps))) for i in range(steps + 1)]
    adb("shell", "input", "motionevent", "DOWN", str(int(pts[0][0])), str(int(pts[0][1])))
    for x, y in pts[1:]:
        adb("shell", "input", "motionevent", "MOVE", str(int(x)), str(int(y)))
    adb("shell", "input", "motionevent", "UP", str(int(pts[-1][0])), str(int(pts[-1][1])))
    time.sleep(1)


def back():
    adb("shell", "input", "keyevent", "4")
    time.sleep(1.2)


def crashed():
    logs = adb("logcat", "-d", "-v", "brief")
    # tylko awarie Equinox (na starszych Androidach log zawiera też inne aplikacje)
    hits = [l for l in logs.splitlines() if f"Process: {PKG}" in l]
    return hits, logs


# 1. Start
adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
time.sleep(6)
shot("01-start")
expect("EQUINOX", "start")
expect("Losuj wszystko|Shuffle all", "start")

# 1b. Motyw: domyślnie „TELEFON” (jak ustawienia telefonu); przełącz na Dzień i Noc, potem wróć
EN_THEME = {"TELEFON": "PHONE", "DZIEŃ": "DAY", "NOC": "NIGHT", "PORA DNIA": "TIME OF DAY"}


def set_theme(label):
    for _ in range(5):
        if find(f"Motyw: {label}|Theme: {EN_THEME.get(label, label)}", desc=True):
            return True
        if not tap("Motyw:|Theme:", desc=True, required=False):
            return False
        time.sleep(0.8)
    return bool(find(f"Motyw: {label}|Theme: {EN_THEME.get(label, label)}", desc=True))

if find("Motyw: TELEFON|Theme: PHONE", desc=True):
    note("OK [motyw]: domyślnie wg ustawień telefonu")
else:
    problems.append("[motyw] domyślny motyw nie jest TELEFON")
for label, name in [("DZIEŃ", "01b-motyw-dzien"), ("NOC", "01c-motyw-noc")]:
    if set_theme(label):
        shot(name)
set_theme("TELEFON")

# 2. Pokrętło: obrót o 90° powinien przesunąć zaznaczenie o 3 pozycje (co 30°)
ok = find("Wybierz|Select", desc=True)
if ok:
    cx, cy, w = ok
    ring_r = int(w / 0.36 / 2 * 0.78)
    rotate(cx, cy, ring_r, 95)
    shot("02-pokretlo-po-obrocie")
else:
    problems.append("nie znaleziono pokrętła (OK)")

# 3. Muzyka -> pierwszy utwór -> Teraz gra
tap("Muzyka|Music", exact=True)
shot("03-muzyka")
expect("Nocny przystanek", "lista muzyki")
tap("Cisza przed burzą")
time.sleep(3)
shot("04a-samouczek")
expect("Jak sterować kółkiem|How to use the dial", "samouczek przy pierwszym otwarciu")
tap("Rozumiem|Got it", exact=True, required=False)
shot("04-teraz-gra")
expect("TERAZ GRA|NOW PLAYING", "teraz gra")
tap("Utwory|Songs", exact=True, required=False)
# znaczniki: ✦ → ★ Perełki
if tap("Znaczniki|Marks", desc=True, required=False, zone=LOW):
    expect("Perełki|Gems", "okno znaczników")
    tap("Perełki|Gems", exact=True, required=False)
    tap("Gotowe|Done", exact=True, required=False)
shot("05-tryb-utwory")
# Klocki: włącz „Następne” i „Zegar” w edytorze układu
if tap("Ułóż ekran|Arrange screen", required=False, zone=LOW):
    shot("05b-edytor-klockow")
    tap("Włącz Następne|Turn on Up next", desc=True, required=False)
    tap("Włącz Zegar|Turn on Clock", desc=True, required=False)
    shot("05c-edytor-po-zmianie")
    back()
    time.sleep(1)
    shot("05d-teraz-gra-z-klockami")
    expect("NASTĘPNE|UP NEXT", "klocek Następne na ekranie", zone=LOW)

back()
back()

# 4. Audiobooki
tap("Audiobooki|Audiobooks", exact=True)
shot("06-audiobooki")
expect("Lalka", "audiobooki")
tap("Lalka")
time.sleep(3)
shot("07-audiobook-gra")
expect("AUDIOBOOK", "audiobook")
tap("1.5×", exact=True, required=False, zone=LOW)
time.sleep(1)
shot("08-audiobook-predkosc")
back()
back()

# 5. Sesje + menu sygnału
tap("Sesje|Sessions", exact=True)
shot("09-sesje")
expect("Stoper muzyczny|Music timer", "sesje")
tap("♪ sygnał|♪ signal")
shot("10-menu-sygnalu")
tap("Dzwonek|Bell", required=False)
tap("30", exact=True, required=False)
time.sleep(1)
shot("11-sesje-timer")
back()

# 6. Radio (wymaga internetu na emulatorze; brak stacji nie jest błędem aplikacji)
tap("Radio", exact=True)
time.sleep(8)
shot("12-radio")
back()

# 7. Przytrzymanie OK = pauza
ok = find("Wybierz|Select", desc=True)
if ok:
    adb("shell", "input", "swipe", str(ok[0]), str(ok[1]), str(ok[0]), str(ok[1]), "900")
    time.sleep(1)
    shot("13-po-przytrzymaniu-ok")

# 8. Obrót ekranu (test odtworzenia aktywności)
adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
adb("shell", "settings", "put", "system", "user_rotation", "1")
time.sleep(3)
shot("14-poziomo")
adb("shell", "settings", "put", "system", "user_rotation", "0")
time.sleep(2)
expect("EQUINOX", "po obrocie ekranu")

# 9. Symulacja rozłożonego składaka (ok. 700x840 dp): układ dwupanelowy
note("  przed rozłożeniem: " + " | ".join(t or d for t, d, *_ in nodes() if (t or d))[:300])
adb("shell", "wm", "size", "1840x2208")
adb("shell", "wm", "density", "420")
time.sleep(4)
W, H = screen_size()  # przewijanie musi znać nowy rozmiar ekranu
note("  po rozłożeniu: " + " | ".join(t or d for t, d, *_ in nodes() if (t or d))[:300])
shot("15-skladak-menu")
tap("Muzyka|Music", exact=True)
tap("Nocny przystanek")
time.sleep(2)
shot("16-skladak-teraz-gra")
expect("TERAZ GRA|NOW PLAYING", "składak: teraz gra")
# obrót = zmiana konfiguracji jak przy rozkładaniu: ekran ma zostać ten sam
adb("shell", "settings", "put", "system", "user_rotation", "1")
time.sleep(3)
shot("17-skladak-poziomo")
expect("TERAZ GRA|NOW PLAYING", "składak: ekran zachowany po zmianie konfiguracji")
adb("shell", "settings", "put", "system", "user_rotation", "0")
adb("shell", "wm", "size", "reset")
adb("shell", "wm", "density", "reset")
time.sleep(2)
W, H = screen_size()

# 9b. Playlisty: utwórz, dodaj utwór, sprawdź
for _ in range(4):  # wróć do menu głównego
    if find("Losuj wszystko|Shuffle all"):
        break
    back()
tap("Playlisty|Playlists", exact=True)
tap("+ Nowa playlista|+ New playlist")
tap("Nazwa|Name", exact=True, required=False)  # pole tekstowe w oknie
adb("shell", "input", "text", "Wieczorna")
time.sleep(1)
tap("Utwórz|Create", exact=True)
time.sleep(1)
shot("20-playlista-pusta")
expect("Wieczorna", "playlista utworzona")
expect("Perełki|Gems", "playlista automatyczna ze znacznika")
tap("+ Dodaj|+ Add")
shot("21-dodawanie-utworow")
tap("+", exact=True)          # pierwszy utwór na liście
tap("+", exact=True)          # drugi
back()
shot("22-playlista-z-utworami")
expect("✕", "utwory dodane do playlisty")
back()
back()

# 9c. Biblioteka: foldery i skanowanie telefonu
tap("Biblioteka|Library", exact=True)
shot("23-biblioteka")
expect("Testowe", "folder z muzyką widoczny")
tap("Skanuj teraz|Scan now")
time.sleep(12)
shot("24-po-skanowaniu")
expect("Znaleziono|Found", "skanowanie telefonu")
back()

# 9d. Wykonawcy i wyszukiwarka
def go_home():
    """Wstecz aż do menu głównego; gdyby aplikacja się zamknęła – uruchom ją ponownie."""
    for _ in range(5):
        if find("Losuj wszystko|Shuffle all"):
            return
        back()
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(4)

go_home()
tap("Muzyka|Music", exact=True)
tap("Wykonawcy|Artists", exact=True)
shot("25-wykonawcy")
expect("Nieznany wykonawca|Unknown artist", "lista wykonawców")
go_home()
tap("Szukaj|Search", exact=True)
time.sleep(1)
if not find("Nocny"):
    tap("Tytuł, wykonawca|Title, artist", required=False)  # gdyby kursor nie trafił sam do pola
adb("shell", "input", "text", "Nocny")
time.sleep(1.5)
shot("26-szukaj")
expect("Nocny przystanek", "wyszukiwarka")
go_home()
# bez polskich znaków: „burza” ma znaleźć „Cisza przed burzą”
tap("Szukaj|Search", exact=True)
time.sleep(1)
adb("shell", "input", "text", "burza")
time.sleep(1.5)
expect("Cisza przed burzą", "wyszukiwanie bez polskich znaków")
go_home()

# 9f. Statystyki i ustawienia („wschód słońca”)
tap("Statystyki|Stats", exact=True)
shot("28-statystyki")
expect("Odkrycia|Discoveries", "ekran statystyk")
go_home()
tap("Ustawienia|Settings", desc=True)
time.sleep(1.5)
shot("29-ustawienia")
expect("BRZMIENIE|SOUND", "panel ustawień")
tap("Zmień|Change", exact=True, required=False)
time.sleep(1)
shot("30-brzmienie")
expect("Ciepły wieczór|Warm evening", "karty brzmienia")
tap("Ciepły wieczór|Warm evening", required=False)
tap("Gotowe|Done", exact=True, required=False)
back()  # zamyka ustawienia (zachód słońca)
time.sleep(1)

# 9e. Wznawianie: zamknij aplikację całkowicie i uruchom ponownie – ma pamiętać, co grało
adb("shell", "am", "force-stop", PKG)
time.sleep(2)
adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
time.sleep(7)
shot("27-po-ponownym-uruchomieniu")
expect("SŁUCHAJ DALEJ|KEEP LISTENING", "wznowienie ostatniej sesji po zamknięciu aplikacji")

# 10. Widżety: system musi widzieć oba widżety Equinox
dump = adb("shell", "dumpsys", "appwidget")
for w in ["PlayerWidget", "RingWidget"]:
    if f"widget.{w}" in dump and PKG in dump:
        note(f"OK [widżety]: system widzi {w}")
    else:
        problems.append(f"[widżety] system nie widzi {w}")

# 11. Kafelek w szybkich ustawieniach: dotknięcie ma uruchomić stoper muzyczny
#     (polecenie „cmd statusbar add-tile” jest dostępne od Androida 10)
SDK = int(os.environ.get("SDK", "34"))
if SDK < 29:
    note("pomijam test kafelka: Android starszy niż 10 nie ma polecenia add-tile")
else:
    adb("shell", "input", "keyevent", "3")  # ekran główny telefonu
    time.sleep(2)
    tile = f"{PKG}/.StopwatchTileService"
    note(adb("shell", "cmd", "statusbar", "add-tile", tile).strip() or "kafelek dodany")
    time.sleep(2)
    adb("shell", "cmd", "statusbar", "click-tile", tile)
    time.sleep(10)
    adb("shell", "cmd", "statusbar", "collapse")
    shot("18-kafelek-stoper")
    expect("TERAZ GRA|NOW PLAYING", "kafelek: stoper wystartował i otworzył odtwarzacz")
    back()
    tap("Sesje|Sessions", exact=True, required=False)
    shot("19-sesje-stoper-dziala")
    expect("Zatrzymaj stoper|Stop the timer", "kafelek: stoper aktywny w Sesjach")

hits, logs = crashed()
open(f"{OUT}/logcat.txt", "w", encoding="utf-8").write(logs)
if hits:
    problems.append("AWARIA aplikacji: " + " | ".join(hits[:5]))

# zrzuty do logu (base64), żeby dało się je obejrzeć bez pobierania artefaktów
import base64, glob
# tylko kluczowe ekrany (log GitHuba ma limit rozmiaru)
KEY = ("04-teraz", "26-", "28-", "29-", "30-")
for f in sorted(glob.glob(f"{OUT}/*.png")):
    if not f.split("/")[-1].startswith(KEY):
        continue
    # pomniejszamy (ImageMagick jest na maszynach GitHuba), żeby linia zmieściła się w logu
    small = f + ".jpg"
    try:
        if subprocess.run(["convert", f, "-resize", "360x", "-quality", "70", small]).returncode != 0:
            small = f
    except OSError:
        small = f
    print("SHOT " + f.split("/")[-1] + " " + base64.b64encode(open(small, "rb").read()).decode())

note("\n=== WYNIK ===")
if problems:
    for p in problems:
        note(" - " + p)
    sys.exit(1)
note("Wszystko OK")
