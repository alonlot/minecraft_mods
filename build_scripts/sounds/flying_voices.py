"""Flying memory voice lines (Fish Audio TTS) -> sounds/pensieve/voice/flying_*.ogg.

Same pipeline as letter_voices.py: key from C:\\Users\\alonl\\fish_key.txt (never printed, never committed), raw mp3
takes in a scratch directory outside the repo, trim silence, loudnorm -16/-1.5, mono 44.1k vorbis. Pass line ids as
arguments to redo only those. Measured lengths merge into flying_clip_lengths.json beside the sfx lengths.
No line contains a name for the boy. Music is not made here (Task 10).

Voices:
  Hooch       9f646202b5694df98acc78d756674599  (user pick hooch_C)
  Neville     ebe169afb0934b5bbcc5c1ed62a024b5  (user pick neville_A)
  Malfoy      b817c2a28b90454ea1c54f1342fdabd2  (draco_A)
  Viewer      67ccf061bf3b4f9c8b635addc77eba6f  (young Harry, age 13)
  McGonagall  d625beac29e2417faba8ace4c3d6ccdd  (same as the other memories)
  Wood        de272fd1bff547c5af60a11049d6bca5  Fish "Oliver wood", author unlisted, one take
  Commentator 7c90feecce0e4bbb8d5d1a20a98e6a11  Fish "Commentator (good)" by LeroyJr, one take
flying_class_up is a ragged chorus: Neville, Malfoy and the viewer voice, three takes mixed with small offsets.
"""

import json
import os
import subprocess
import sys
import urllib.request

KEY_FILE = r"C:\Users\alonl\fish_key.txt"
API = "https://api.fish.audio/v1/tts"

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                       "sounds", "pensieve", "voice")
LENGTHS = os.path.join(HERE, "flying_clip_lengths.json")
RAW_DIR = (r"C:\Users\alonl\AppData\Local\Temp\claude\C--Users-alonl-OneDrive-Desktop-harry-potter-mod"
           r"\eca4c2f1-9bf3-4c65-b86d-13bbb709117a\scratchpad\flying_audio")

HOOCH = "9f646202b5694df98acc78d756674599"
NEVILLE = "ebe169afb0934b5bbcc5c1ed62a024b5"
MALFOY = "b817c2a28b90454ea1c54f1342fdabd2"
VIEWER = "67ccf061bf3b4f9c8b635addc77eba6f"
MCGONAGALL = "d625beac29e2417faba8ace4c3d6ccdd"
WOOD = "de272fd1bff547c5af60a11049d6bca5"
COMMENTATOR = "7c90feecce0e4bbb8d5d1a20a98e6a11"

# id -> (voice, speed, text)
LINES = {
    "flying_hooch_welcome": (HOOCH, 1.0, "Good afternoon, class. Don't stand there gawping, find a broom."),
    "flying_hooch_left": (HOOCH, 1.0, "Stand on the left of it. Right hand out, flat, over the handle."),
    "flying_hooch_up": (HOOCH, 1.0, "Now, firmly, and mean it: say 'Up!'"),
    "flying_viewer_up": (VIEWER, 1.0, "Up!"),
    "flying_neville_whoa": (NEVILLE, 1.0, "It's going on its own! I can't stop it, help!"),
    "flying_hooch_nobody": (HOOCH, 1.0, "Nobody moves! Feet on the grass until I'm back, or you'll answer for it."),
    "flying_malfoy_look": (MALFOY, 1.0, "Look what the clumsy idiot dropped."),
    "flying_malfoy_come": (MALFOY, 1.0, "Want it? Come up and take it, then."),
    "flying_malfoy_catch": (MALFOY, 1.0, "Go on, then. Fetch!"),
    "flying_mcg_follow": (MCGONAGALL, 1.0, "You. With me. Now."),
    "flying_hooch_clean": (HOOCH, 1.0, "I want a clean game, from all of you."),
    "flying_captain": (WOOD, 1.0, "Just catch the Snitch. That's all you've got to do."),
    "flying_comm_off": (COMMENTATOR, 1.0, "And they're off!"),
    "flying_comm_goal": (COMMENTATOR, 1.0, "Goal! Goal!"),
    "flying_comm_seeker": (COMMENTATOR, 1.0, "The Seeker has seen something!"),
}

# the chorus: (voice, speed, delay seconds, gain) takes of "Up!", mixed
CHORUS = {
    "flying_class_up": [(NEVILLE, 1.0, 0.00, 1.0), (MALFOY, 1.1, 0.07, 0.9), (VIEWER, 0.9, 0.15, 0.8)],
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


def chorus(key, name, takes, out, force):
    inputs, parts = [], []
    for i, (voice, speed, delay, gain) in enumerate(takes):
        raw = os.path.join(RAW_DIR, "%s_%d.mp3" % (name, i))
        if not os.path.exists(raw) or force:
            tts(key, voice, speed, "Up!", raw)
        inputs += ["-i", raw]
        ms = int(delay * 1000)
        parts.append("[%d]adelay=%d|%d,volume=%.2f[a%d]" % (i, ms, ms, gain, i))
    mix = "".join("[a%d]" % i for i in range(len(takes)))
    flt = ";".join(parts) + ";" + mix + "amix=inputs=%d:normalize=0[m]" % len(takes)
    mixed = os.path.join(RAW_DIR, name + ".wav")
    subprocess.run(["ffmpeg", "-v", "error", "-y"] + inputs + ["-filter_complex", flt, "-map", "[m]", mixed],
                   check=True)
    convert(mixed, out)


def save_lengths(update):
    data = {}
    if os.path.exists(LENGTHS):
        with open(LENGTHS, encoding="utf-8") as f:
            data = json.load(f)
    data.update(update)
    with open(LENGTHS, "w", encoding="utf-8") as f:
        json.dump(dict(sorted(data.items())), f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    args = sys.argv[1:]
    with open(KEY_FILE) as f:
        key = f.read().strip()
    os.makedirs(RAW_DIR, exist_ok=True)
    os.makedirs(OUT_DIR, exist_ok=True)
    names = args or (list(LINES) + list(CHORUS))
    lengths = {}
    for name in names:
        out = os.path.join(OUT_DIR, name + ".ogg")
        if name in CHORUS:
            chorus(key, name, CHORUS[name], out, bool(args))
            lengths[name] = round(duration(out), 2)
            print("%-22s %.2fs  chorus" % (name, lengths[name]))
            continue
        voice, speed, text = LINES[name]
        raw = os.path.join(RAW_DIR, name + ".mp3")
        if not os.path.exists(raw) or args:
            tts(key, voice, speed, text, raw)
        convert(raw, out)
        lengths[name] = round(duration(out), 2)
        print("%-22s %.2fs  (raw %.2fs)  %s" % (name, lengths[name], duration(raw), text))
    save_lengths(lengths)
