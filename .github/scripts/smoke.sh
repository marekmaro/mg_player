#!/usr/bin/env bash
# Test dymny na emulatorze: pliki testowe -> instalacja -> przejście po ekranach.
set -euo pipefail

PKG=io.github.marekmaro.sundial
APK=$(ls apk/*.apk | head -1)
OUT=smoke
mkdir -p "$OUT" audio

python3 .github/scripts/make_audio.py audio

adb shell mkdir -p /sdcard/Music/Testowe /sdcard/Audiobooks/Lalka
for f in audio/music/*.wav; do adb push "$f" /sdcard/Music/Testowe/ >/dev/null; done
for f in audio/book/*.wav; do adb push "$f" /sdcard/Audiobooks/Lalka/ >/dev/null; done
adb shell content call --uri content://media --method scan_volume --arg external_primary || true
sleep 8

adb install -r "$APK"
adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c

python3 .github/scripts/smoke.py $PKG "$OUT"
