"""Wand memory voice lines (Fish Audio TTS) -> sounds/pensieve/voice/wand_*.ogg.

Reads the key from C:\\Users\\alonl\\fish_key.txt (never printed, never committed). Raw mp3 takes go
to a scratch directory outside the repo. Pipeline: trim silence at both ends, loudnorm, mono 44.1k
vorbis. Pass line ids as arguments to redo only those. Also writes wand_clip_lengths.json (seconds,
2 decimals) next to this script, merged with the other wand clips.

  python wand_voices.py                 all lines
  python wand_voices.py wand_olli_try   just those
  python wand_voices.py candidates      wand_olli_curious with each Ollivander candidate (scratch only)
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
LENGTHS = os.path.join(HERE, "wand_clip_lengths.json")
RAW_DIR = (r"C:\Users\alonl\AppData\Local\Temp\claude\C--Users-alonl-OneDrive-Desktop-harry-potter-mod"
           r"\4f579a71-d4a1-442d-8a31-491678844c18\scratchpad\wand_audio")

HAGRID = "f6fd51174e2743db91c4e91fe5de4ec1"
# Ollivander: no John Hurt model exists on Fish; the only one named for him is "Olivander" (en).
# Candidates tried for wand_olli_curious (see the `candidates` mode):
#   2862713ba35a42ae95eb744cdbc0065c  "Olivander"           <- chosen (explicitly named, en)
#   fd7658ce781f47bea452d7afcd96d231  "Lawrence Oleander"   (Hogwarts Legacy wandmaker, runner-up)
#   739b695fd30747d1bbcc64342c163c9e  "Professor Garlick HOGWARTS LEGACY" (runner-up)
OLLIVANDER = "2862713ba35a42ae95eb744cdbc0065c"
CANDIDATES = {
    "olivander": "2862713ba35a42ae95eb744cdbc0065c",
    "oleander": "fd7658ce781f47bea452d7afcd96d231",
    "garlick": "739b695fd30747d1bbcc64342c163c9e",
}

# id -> (voice, speed, text)
LINES = {
    "wand_hagrid_bricks": (HAGRID, 1.0, "Three up... two across... Right. Stand back."),
    "wand_hagrid_welcome": (HAGRID, 0.95, "Welcome to Diagon Alley."),
    "wand_hagrid_wand": (HAGRID, 1.0, "Go on, then. Ollivanders. Only place for wands."),
    "wand_olli_wondered": (OLLIVANDER, 0.92, "Ah. I wondered when I'd be seeing you."),
    "wand_olli_wandarm": (OLLIVANDER, 0.95, "Which is your wand arm? Hold out your hand."),
    "wand_olli_try": (OLLIVANDER, 0.95, "Give it a wave."),
    "wand_olli_no": (OLLIVANDER, 0.95, "No, no, apparently not."),
    "wand_olli_no2": (OLLIVANDER, 0.95, "No. Definitely not."),
    "wand_olli_tricky": (OLLIVANDER, 0.9, "A tricky customer. I wonder..."),
    "wand_olli_curious": (OLLIVANDER, 0.9, "Curious... very curious. The wand chooses the wizard. "
                                           "It is not always clear why."),
    "wand_olli_great": (OLLIVANDER, 0.92, "I think we must expect great things from you."),
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


def loudness(path):
    r = subprocess.run(["ffmpeg", "-hide_banner", "-i", path, "-af", "volumedetect", "-f", "null", "-"],
                       capture_output=True, text=True)
    out = {}
    for ln in r.stderr.splitlines():
        for k in ("mean_volume", "max_volume"):
            if k + ":" in ln:
                out[k] = ln.split(k + ":")[1].strip()
    return out


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
    if args == ["candidates"]:
        _, speed, text = LINES["wand_olli_curious"]
        for cname, vid in CANDIDATES.items():
            raw = os.path.join(RAW_DIR, "cand_%s.mp3" % cname)
            tts(key, vid, speed, text, raw)
            out = raw.replace(".mp3", ".ogg")
            convert(raw, out)
            print("%-10s %.2fs  %s" % (cname, duration(out), loudness(raw)))
        sys.exit(0)
    os.makedirs(OUT_DIR, exist_ok=True)
    names = args or list(LINES)
    lengths = {}
    for name in names:
        voice, speed, text = LINES[name]
        raw = os.path.join(RAW_DIR, name + ".mp3")
        if not os.path.exists(raw) or args:
            tts(key, voice, speed, text, raw)
        out = os.path.join(OUT_DIR, name + ".ogg")
        convert(raw, out)
        lengths[name] = round(duration(out), 2)
        print("%-22s %.2fs  (raw %.2fs)  %s" % (name, lengths[name], duration(raw), text))
    save_lengths(lengths)
