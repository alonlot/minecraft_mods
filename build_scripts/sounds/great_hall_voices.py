"""Great Hall voice lines (Fish Audio TTS) -> sounds/pensieve/voice/hall_*.ogg.

Reads the key from C:\\Users\\alonl\\fish_key.txt (never printed, never committed). Raw mp3 takes go
to a scratch directory outside the repo. Pipeline: trim silence at both ends, loudnorm, mono 44.1k
vorbis. Pass line ids as arguments to redo only those.
"""

import json
import os
import subprocess
import sys
import urllib.request

KEY_FILE = r"C:\Users\alonl\fish_key.txt"
API = "https://api.fish.audio/v1/tts"

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                       "sounds", "pensieve", "voice")
RAW_DIR = (r"C:\Users\alonl\AppData\Local\Temp\claude\C--Users-alonl-OneDrive-Desktop-harry-potter-mod"
           r"\4f579a71-d4a1-442d-8a31-491678844c18\scratchpad\hall_audio")

HAT = "c944589a55ad450e8109d39cd3ecc488"
MCG = "d625beac29e2417faba8ace4c3d6ccdd"
DUMBLEDORE = "90dfa283adee46eea0f69383dc5ab8f3"
HERMIONE = "484917f6b5d9499daf6c41742413ccb2"
RON = "5087254d8fe8466cb5e750a1b0dd44a4"

# id -> (voice, speed, text)
LINES = {
    "hall_hat_open": (HAT, 1.0, "Hmm... So. Let us begin where every sorting begins."),
    "hall_mcg_doors": (MCG, 0.95, "In a few moments you will pass through these doors and be sorted. "
                                  "Smarten yourselves up. Follow me."),
    "hall_mcg_call": (MCG, 0.95, "When I call your name, you will come forward. "
                                 "I shall place the Sorting Hat on your head."),
    "hall_mcg_granger": (MCG, 0.95, "Granger, Hermione."),
    "hall_mcg_weasley": (MCG, 0.95, "Weasley, Ronald."),
    "hall_dumbledore": (DUMBLEDORE, 0.92, "Welcome! Welcome to a new year at Hogwarts."),
    "hall_hermione_nervous": (HERMIONE, 1.08, "I've read all about the Sorting. It's only a hat... isn't it?"),
    "hall_ron_nervous": (RON, 1.05, "My brothers said we'd have to wrestle a troll."),
}


def tts(key, voice, speed, text, path):
    body = json.dumps({"text": text, "reference_id": voice, "format": "mp3",
                       "prosody": {"speed": speed}}).encode()
    req = urllib.request.Request(API, data=body, headers={
        "Authorization": "Bearer " + key, "model": "speech-1.6", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=180) as r:
        data = r.read()
    with open(path, "wb") as f:
        f.write(data)


def convert(src, out):
    sil = ("silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,"
           "areverse,silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.12,areverse,")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", src, "-af", sil + "loudnorm=I=-16:TP=-1.5",
                    "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", out], check=True)


def duration(path):
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0",
                        path], capture_output=True, text=True, check=True)
    return float(r.stdout.strip())


if __name__ == "__main__":
    names = sys.argv[1:] or list(LINES)
    with open(KEY_FILE) as f:
        key = f.read().strip()
    os.makedirs(RAW_DIR, exist_ok=True)
    os.makedirs(OUT_DIR, exist_ok=True)
    for name in names:
        voice, speed, text = LINES[name]
        raw = os.path.join(RAW_DIR, name + ".mp3")
        if not os.path.exists(raw) or sys.argv[1:]:
            tts(key, voice, speed, text, raw)
        out = os.path.join(OUT_DIR, name + ".ogg")
        convert(raw, out)
        print("%-22s %.2fs  (raw %.2fs)  %s" % (name, duration(out), duration(raw), text))
