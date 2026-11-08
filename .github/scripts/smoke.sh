#!/usr/bin/env bash
# Test dymny na emulatorze: pliki testowe -> instalacja -> przejście po ekranach.
# Działa na Androidzie 8.0 (API 26) i nowszych; FONT_SCALE=1.3 sprawdza dużą czcionkę.
set -euo pipefail

PKG=io.github.marekmaro.equinox
APK=$(ls apk/*.apk | head -1)
OUT=smoke
mkdir -p "$OUT" audio
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
export SDK
echo "Android API $SDK, czcionka ${FONT_SCALE:-1.0}"

python3 .github/scripts/make_audio.py audio

# poczekaj, aż pamięć telefonu będzie gotowa (inaczej kopiowanie bywa odrzucane)
for i in $(seq 1 30); do
  if adb shell "mkdir -p /sdcard/Music/Testowe /sdcard/Audiobooks/Lalka && touch /sdcard/Music/Testowe/.ok" 2>/dev/null; then break; fi
  sleep 3
done
push() { for i in 1 2 3 4 5; do adb push "$1" "$2" >/dev/null && return 0; sleep 3; done; return 1; }
for f in audio/music/*.wav; do push "$f" /sdcard/Music/Testowe/; done
for f in audio/book/*.wav; do push "$f" /sdcard/Audiobooks/Lalka/; done

# indeksowanie plików: nowsze Androidy jednym poleceniem, starsze plik po pliku
if [ "$SDK" -ge 29 ]; then
  adb shell content call --uri content://media --method scan_volume --arg external_primary || true
else
  for f in audio/music/*.wav; do
    adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "\"file:///sdcard/Music/Testowe/$(basename "$f")\"" >/dev/null || true
  done
  for f in audio/book/*.wav; do
    adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "\"file:///sdcard/Audiobooks/Lalka/$(basename "$f")\"" >/dev/null || true
  done
fi
sleep 8

if [ -n "${FONT_SCALE:-}" ] && [ "$FONT_SCALE" != "1.0" ]; then
  adb shell settings put system font_scale "$FONT_SCALE"
fi

adb install -r "$APK"
if [ "$SDK" -ge 33 ]; then
  adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO
  adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
else
  adb shell pm grant $PKG android.permission.READ_EXTERNAL_STORAGE
fi
adb logcat -c

python3 .github/scripts/smoke.py $PKG "$OUT"
