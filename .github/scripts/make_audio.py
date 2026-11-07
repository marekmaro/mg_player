"""Tworzy małe pliki WAV do testów: kilka „utworów” i 3 rozdziały audiobooka."""
import math
import os
import struct
import sys
import wave

RATE = 8000


def tone(path, seconds, freq):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(1)  # 8-bit, żeby pliki były małe
        w.setframerate(RATE)
        frames = bytearray()
        for i in range(int(seconds * RATE)):
            v = 0.25 * math.sin(2 * math.pi * freq * i / RATE)
            frames.append(int(128 + v * 127))
        w.writeframes(bytes(frames))


out = sys.argv[1]
songs = [("Poranek nad jeziorem", 95), ("Nocny przystanek", 130), ("Szum miasta", 75),
         ("Lato w kadrze", 110), ("Ostatni tramwaj", 150), ("Cisza przed burzą", 85)]
for i, (name, sec) in enumerate(songs):
    tone(f"{out}/music/{name}.wav", sec, 220 + i * 40)
for ch in range(1, 4):
    tone(f"{out}/book/Rozdział {ch:02d}.wav", 60, 180)
print("audio ok")
