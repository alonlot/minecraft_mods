"""Generate the living cards' five sounds with ElevenLabs and master them to mono OGG.

  python tools/living_cards/make_sounds.py gen      # fetch raw takes into tools/living_cards/sfx_raw/ (gitignored scratch)
  python tools/living_cards/make_sounds.py env      # print a 100 ms RMS envelope of each take
  python tools/living_cards/make_sounds.py master   # trim with WINDOWS, level-match, write the OGGs

The key is read from C:/Users/alonl/eleven_key.txt at run time and never written anywhere.
"""
import json
import os
import struct
import subprocess
import sys
import urllib.request

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RAW = os.path.join(ROOT, "tools", "living_cards", "sfx_raw")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "sounds", "card")
KEY = r"C:\Users\alonl\eleven_key.txt"

SOUNDS = {
    "lid_pop": ("A single loud close-miked pop of a small cardboard candy box lid flipping open, crisp paper thump with a "
                "tiny sparkle, immediate, no silence, no metal", 1.0),
    "shimmer": ("A bright loud magical shimmer sweep, glittering crystal chimes rising quickly across the stereo field, "
                "dense and continuous throughout, immediate, no silence", 1.5),
    "legendary_chime": ("A loud radiant magical golden chime, a warm choir-like bell chord swelling with sparkling harp "
                        "glissando, triumphant, immediate, dense and continuous, no silence", 2.5),
    "walk_out": ("Soft but close-miked footsteps of a person in robes walking away on a wooden floor, three steps with "
                 "fabric rustle, immediate, continuous, no silence", 1.5),
    "puff": ("A loud close-miked magical puff of smoke, a quick soft whoosh and pop of air with a faint sparkle, "
             "immediate, no silence, no explosion", 0.8),
}
# (start, end) seconds per take, read off the envelope; None keeps the whole clip
WINDOWS = {"lid_pop": (0.0, 0.5), "shimmer": (0.0, 1.2), "legendary_chime": (0.0, 2.0), "walk_out": (0.35, 1.3), "puff": (0.0, 0.5)}
TARGET_RMS_DB = {"lid_pop": -16, "shimmer": -20, "legendary_chime": -18, "walk_out": -22, "puff": -18}


def gen():
    os.makedirs(RAW, exist_ok=True)
    key = open(KEY).read().strip()
    for name, (prompt, dur) in SOUNDS.items():
        req = urllib.request.Request("https://api.elevenlabs.io/v1/sound-generation",
                                     data=json.dumps({"text": prompt, "duration_seconds": dur, "prompt_influence": 0.45}).encode(),
                                     headers={"xi-api-key": key, "Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=120) as r:
            open(os.path.join(RAW, name + ".mp3"), "wb").write(r.read())
        print("got", name)


def pcm(path, start=None, end=None):
    cmd = ["ffmpeg", "-v", "error"]
    if start is not None:
        cmd += ["-ss", str(start)]
    cmd += ["-i", path]
    if end is not None:
        cmd += ["-t", str(end - (start or 0))]
    cmd += ["-ac", "1", "-ar", "44100", "-f", "s16le", "-"]
    raw = subprocess.run(cmd, capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0


def env():
    for name in SOUNDS:
        x = pcm(os.path.join(RAW, name + ".mp3"))
        w = 4410
        db = [20 * np.log10(max(1e-6, float(np.sqrt((x[i:i + w] ** 2).mean())))) for i in range(0, len(x), w)]
        print(f"{name:16s} {len(x) / 44100:.2f}s ", " ".join(f"{d:.0f}" for d in db))


def master():
    os.makedirs(OUT, exist_ok=True)
    for name in SOUNDS:
        src = os.path.join(RAW, name + ".mp3")
        win = WINDOWS[name]
        start, end = (win if win else (None, None))
        x = pcm(src, start, end)
        rms = 20 * np.log10(max(1e-6, float(np.sqrt((x ** 2).mean()))))
        gain = TARGET_RMS_DB[name] - rms
        cmd = ["ffmpeg", "-y", "-v", "error"]
        if start is not None:
            cmd += ["-ss", str(start)]
        cmd += ["-i", src]
        if end is not None:
            cmd += ["-t", str(end - (start or 0))]
        cmd += ["-af", f"afade=t=out:st={max(0.0, len(x) / 44100 - 0.08):.3f}:d=0.08,volume={gain:.2f}dB,"
                       "alimiter=level=disabled:limit=0.65",
                "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", os.path.join(OUT, name + ".ogg")]
        subprocess.run(cmd, check=True)
        print(f"{name}: rms {rms:.1f} dB, gain {gain:+.1f} dB, {len(x) / 44100:.2f}s")


if __name__ == "__main__":
    {"gen": gen, "env": env, "master": master}[sys.argv[1]]()
