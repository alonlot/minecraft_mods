"""First Task memory voice lines (Fish Audio TTS) -> sounds/pensieve/voice/first_task_*.ogg.

Same pipeline as flying_voices.py: key from the Fish key file (never printed, never committed), raw mp3
takes in a scratch directory outside the repo, whisper transcription gate, trim silence, linear gain to -16 LUFS
plus alimiter -2 dBTP latency=1, mono 44.1k vorbis. Measured lengths go to first_task_clip_lengths.json.
No line contains the player's name. Not registered in sounds.json (Task 12).

  python -I first_task_voices.py            make every line
  python -I first_task_voices.py <id> ...   redo only these (fresh take)
  python -I first_task_voices.py samples    teenage Accio alternatives -> run/voice_samples/first_task/ (+README)

Voices:
  Commentator 7c90feecce0e4bbb8d5d1a20a98e6a11  (user pick: Fish "Commentator (good)" by LeroyJr)
  Viewer      42cbd05f78814d6f8b47d90a3cb37f0b  (user pick accio_C2: Fish "Harry potter" by Akshay Srivastava; the shipped take is that sample)
  Alternatives for the 14-year-old champion (samples only): b841aacef7dc48f9b18b6e89bf573e80, 42cbd05f78814d6f8b47d90a3cb37f0b
first_task_comm_call_muffled is the same call take through a 900 Hz low-pass and a short room reverb (the tent plays it).
"""

import json
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from flying_voices import (tts, convert, duration, heard, measure_lufs, save_lengths, KEY_FILE)  # noqa: E402
import flying_voices  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT_DIR = flying_voices.OUT_DIR
flying_voices.LENGTHS = os.path.join(HERE, "first_task_clip_lengths.json")
RAW_DIR = flying_voices.RAW_DIR.replace("flying_audio", "first_task_audio")
SAMPLE_DIR = os.path.join(ROOT, "run", "voice_samples", "first_task")

COMMENTATOR = "7c90feecce0e4bbb8d5d1a20a98e6a11"
VIEWER = "42cbd05f78814d6f8b47d90a3cb37f0b"  # user pick accio_C2 (2026-10-23)
ALTS = [("B", "b841aacef7dc48f9b18b6e89bf573e80", "Young teenager (boy) with deep voice", "Carter Lively"),
        ("C", "42cbd05f78814d6f8b47d90a3cb37f0b", "Harry potter", "Akshay Srivastava")]

LINES = {
    "first_task_comm_call": (COMMENTATOR, 1.0, "And now, ladies and gentlemen, our final champion!"),
    "first_task_comm_horntail": (COMMENTATOR, 1.0, "And here she is, the Hungarian Horntail! Nerves of steel, now!"),
    "first_task_comm_accio": (COMMENTATOR, 1.0, "A Summoning Charm! Oh, that's clever, very clever!"),
    "first_task_comm_chain": (COMMENTATOR, 1.0, "Her chain's gone! She's loose, she's after them!"),
    "first_task_comm_egg": (COMMENTATOR, 1.0, "They've done it! The golden egg! What a flight!"),
    "first_task_viewer_accio": (VIEWER, 1.0, "Accio Firebolt!"),
}
MUFFLED = "first_task_comm_call_muffled"


def norm(s):
    return re.sub(r"[^a-z ]", "", s.lower().replace("-", " ")).split()


def gated_take(key, voice, speed, text, raw, tries=8):
    """Fresh takes until whisper hears the line (first/last word match is enough for 'Accio' misspellings)."""
    want = norm(text)
    for _ in range(tries):
        tts(key, voice, speed, text, raw)
        got = norm(heard(raw))
        hit = sum(1 for w in want if w in got) / len(want)
        if hit >= 0.8 or (len(want) <= 2 and hit >= 0.5 and got[:1] and got[0].startswith("ac")):
            return
    raise RuntimeError("no clean take of: " + text)


def muffle(src_ogg, out):
    tmp = out + ".m.wav"
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", src_ogg, "-af",
                    "lowpass=f=900,lowpass=f=900,aecho=0.8:0.6:45|90:0.35|0.2,apad=pad_dur=0.3",
                    "-ac", "1", "-ar", "44100", tmp], check=True)
    convert(tmp, out)
    os.remove(tmp)


def lead_silence(path):
    r = subprocess.run(["ffmpeg", "-v", "info", "-i", path, "-af", "silencedetect=n=-40dB:d=0.05", "-f", "null", "-"],
                       capture_output=True, text=True)
    m = re.search(r"silence_start: (-?[\d.]+)\s*\n.*silence_end: ([\d.]+)", r.stderr)
    return float(m.group(2)) if m and float(m.group(1)) <= 0.01 else 0.0


def samples(key):
    os.makedirs(SAMPLE_DIR, exist_ok=True)
    line = "Accio Firebolt!"
    rows = ["First Task memory voice samples: the viewer's \"%s\" (Fish Audio, speech-1.6)" % line,
            "Champion is 14; the default clone (A) sounds about 11. Pick one; the take must pass the whisper gate.", ""]
    cands = [("A", "67ccf061bf3b4f9c8b635addc77eba6f", "Harry Potter (young clone, current default)", "(Flying viewer voice)")] + ALTS
    for tag, vid, title, author in cands:
        for n in range(1, 4):
            name = "accio_%s%d.mp3" % (tag, n)
            try:
                gated_take(key, vid, 1.0, line, os.path.join(SAMPLE_DIR, name))
                status = "ok"
            except Exception as e:
                status = "FAILED " + str(e)[:60]
            rows.append("%s | id %s | title: %s | author: %s | line: %s | %s" % (name, vid, title, author, line, status))
    rows += ["", "Commentator (already picked, user): 7c90feecce0e4bbb8d5d1a20a98e6a11 Commentator (good) by LeroyJr"]
    with open(os.path.join(SAMPLE_DIR, "README.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(rows) + "\n")


if __name__ == "__main__":
    args = sys.argv[1:]
    key = open(KEY_FILE).read().strip()
    os.makedirs(RAW_DIR, exist_ok=True)
    os.makedirs(OUT_DIR, exist_ok=True)
    if args == ["samples"]:
        samples(key)
        sys.exit(0)
    names = args or list(LINES)
    lengths = {}
    for name in names:
        voice, speed, text = LINES[name]
        raw = os.path.join(RAW_DIR, name + ".mp3")
        if not os.path.exists(raw) or args:
            gated_take(key, voice, speed, text, raw)
        out = os.path.join(OUT_DIR, name + ".ogg")
        convert(raw, out)
        lengths[name] = round(duration(out), 2)
        print("%-26s %.2fs %.1f LUFS lead %.2fs  %s" % (name, lengths[name], measure_lufs(out), lead_silence(out), text))
        if name == "first_task_comm_call":
            mout = os.path.join(OUT_DIR, MUFFLED + ".ogg")
            muffle(out, mout)
            lengths[MUFFLED] = round(duration(mout), 2)
            print("%-26s %.2fs %.1f LUFS" % (MUFFLED, lengths[MUFFLED], measure_lufs(mout)))
    save_lengths(lengths)
