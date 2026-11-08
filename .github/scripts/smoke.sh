#!/usr/bin/env bash
# Test dymny na emulatorze: pliki testowe -> instalacja -> przejście po ekranach.
set -euo pipefail

PKG=io.github.marekmaro.equinox
APK=$(ls apk/*.apk | head -1)
OUT=smoke
mkdir -p "$OUT" audio

python3 .github/scripts/make_audio.py audio

# poczekaj, aż pamięć telefonu będzie gotowa (inaczej kopiowanie bywa odrzucane)
for i in $(seq 1 30); do
  if adb shell "mkdir -p /sdcard/Music/Testowe /sdcard/Audiobooks/Lalka && touch /sdcard/Music/Testowe/.ok" 2>/dev/null; then break; fi
  sleep 3
done
push() { for i in 1 2 3 4 5; do adb push "$1" "$2" >/dev/null && return 0; sleep 3; done; return 1; }
for f in audio/music/*.wav; do push "$f" /sdcard/Music/Testowe/; done
for f in audio/book/*.wav; do push "$f" /sdcard/Audiobooks/Lalka/; done
adb shell content call --uri content://media --method scan_volume --arg external_primary || true
sleep 8

adb install -r "$APK"
adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c

python3 .github/scripts/smoke.py $PKG "$OUT"
