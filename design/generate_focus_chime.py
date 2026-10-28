"""
Makes app/src/main/res/raw/focus_chime.wav: HabitGame's "focus finished" sound, a short bright
"level complete" chime (a rising C major arpeggio of bell tones, then a ringing chord), about 1.6 s.

    py -I design/generate_focus_chime.py
"""
import math
import pathlib
import struct
import wave

RATE = 44_100
LENGTH = 1.6  # seconds
OUT = pathlib.Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "raw" / "focus_chime.wav"


def bell(freq: float, t: float) -> float:
    """A soft bell: the note plus a few quieter overtones, each fading at its own pace."""
    if t < 0:
        return 0.0
    attack = min(1.0, t / 0.006)  # no click at the start
    partials = [(1.0, 1.0, 3.2), (2.0, 0.45, 5.0), (3.0, 0.18, 7.5), (4.2, 0.08, 10.0)]
    return attack * sum(a * math.exp(-d * t) * math.sin(2 * math.pi * freq * m * t) for m, a, d in partials)


# (start time in seconds, note frequency in Hz): C6, E6, G6, then C7 with the chord ringing under it
NOTES = [(0.00, 1046.50), (0.09, 1318.51), (0.18, 1567.98), (0.30, 2093.00), (0.30, 1046.50), (0.30, 1567.98)]

samples = []
for i in range(int(RATE * LENGTH)):
    t = i / RATE
    value = sum(bell(f, t - start) * (0.8 if start < 0.3 else 0.55) for start, f in NOTES)
    fade = min(1.0, (LENGTH - t) / 0.25)  # ends softly
    samples.append(value * fade)

peak = max(abs(s) for s in samples)
scale = 0.85 * 32767 / peak
OUT.parent.mkdir(parents=True, exist_ok=True)
with wave.open(str(OUT), "wb") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(RATE)
    w.writeframes(b"".join(struct.pack("<h", int(s * scale)) for s in samples))
print(f"Wrote {OUT} ({OUT.stat().st_size // 1024} KB)")
