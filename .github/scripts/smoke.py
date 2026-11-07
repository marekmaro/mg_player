"""
Przejście po ekranach Sundial na emulatorze (przez adb + uiautomator).
Robi zrzuty ekranu, kręci pokrętłem i sprawdza logcat pod kątem awarii.
Kończy się błędem, jeśli aplikacja się wywróci albo nie znajdzie kluczowych napisów.
"""
import math
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
    for t, d, x, y, w in nodes():
        v = d if desc else t
        if (v == text) if exact else (text in v):
            return x, y, w
    return None


SIZE = re.findall(r"(\d+)x(\d+)", adb("shell", "wm", "size"))
W, H = (int(SIZE[-1][0]), int(SIZE[-1][1])) if SIZE else (1080, 1920)
note(f"ekran: {W}x{H}  gęstość: {adb('shell', 'wm', 'density').strip()}")


def find_scroll(text, exact=False, desc=False):
    """Szuka napisu; jeśli go nie widać, przewija listę w górę (maks. 3 razy)."""
    pos = find(text, exact, desc)
    tries = 0
    while not pos and tries < 3:
        adb("shell", "input", "swipe", str(W // 2), str(int(H * 0.50)), str(W // 2), str(int(H * 0.22)), "400")
        time.sleep(1)
        pos = find(text, exact, desc)
        tries += 1
    return pos


def tap(text, exact=False, desc=False, required=True):
    pos = find_scroll(text, exact, desc)
    if not pos:
        if required:
            problems.append(f"nie znaleziono: {text!r}")
        note(f"BRAK: {text!r}")
        return False
    adb("shell", "input", "tap", str(pos[0]), str(pos[1]))
    note(f"tap: {text!r}")
    time.sleep(1.5)
    return True


def expect(text, step):
    if find_scroll(text):
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
    hits = [l for l in logs.splitlines() if "FATAL EXCEPTION" in l or (f"Process: {PKG}" in l)]
    return hits, logs


# 1. Start
adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
time.sleep(6)
shot("01-start")
expect("SUNDIAL", "start")
expect("Losuj wszystko", "start")

# 2. Pokrętło: obrót o 90° powinien przesunąć zaznaczenie o 3 pozycje (co 30°)
ok = find("Wybierz", desc=True)
if ok:
    cx, cy, w = ok
    ring_r = int(w / 0.36 / 2 * 0.78)
    rotate(cx, cy, ring_r, 95)
    shot("02-pokretlo-po-obrocie")
else:
    problems.append("nie znaleziono pokrętła (OK)")

# 3. Muzyka -> pierwszy utwór -> Teraz gra
tap("Muzyka", exact=True)
shot("03-muzyka")
expect("Nocny przystanek", "lista muzyki")
tap("Cisza przed burzą")
time.sleep(3)
shot("04-teraz-gra")
expect("TERAZ GRA", "teraz gra")
tap("Utwory", exact=True, required=False)
shot("05-tryb-utwory")
back()
back()

# 4. Audiobooki
tap("Audiobooki", exact=True)
shot("06-audiobooki")
expect("Lalka", "audiobooki")
tap("Lalka")
time.sleep(3)
shot("07-audiobook-gra")
expect("AUDIOBOOK", "audiobook")
tap("1.5×", exact=True, required=False)
time.sleep(1)
shot("08-audiobook-predkosc")
back()
back()

# 5. Sesje + menu sygnału
tap("Sesje", exact=True)
shot("09-sesje")
expect("Stoper muzyczny", "sesje")
tap("♪ sygnał")
shot("10-menu-sygnalu")
tap("Dzwonek", required=False)
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
ok = find("Wybierz", desc=True)
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

hits, logs = crashed()
open(f"{OUT}/logcat.txt", "w", encoding="utf-8").write(logs)
if hits:
    problems.append("AWARIA aplikacji: " + " | ".join(hits[:5]))

# zrzuty do logu (base64), żeby dało się je obejrzeć bez pobierania artefaktów
import base64, glob
for f in sorted(glob.glob(f"{OUT}/*.png")):
    print("SHOT " + f.split("/")[-1] + " " + base64.b64encode(open(f, "rb").read()).decode())

note("\n=== WYNIK ===")
if problems:
    for p in problems:
        note(" - " + p)
    sys.exit(1)
note("Wszystko OK")
