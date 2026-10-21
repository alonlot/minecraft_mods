"""Letter memory voice lines (Fish Audio TTS) -> sounds/pensieve/voice/letter_*.ogg.

Same pipeline as wand_voices.py: reads the key from C:\\Users\\alonl\\fish_key.txt (never printed, never
committed), raw mp3 takes go to a scratch directory outside the repo, then trim silence at both ends, loudnorm,
mono 44.1k vorbis. Pass line ids as arguments to redo only those. Measured lengths (seconds, 2 decimals) are merged
into letter_clip_lengths.json next to this script, beside the letter sound effect lengths.

  python letter_voices.py                  all lines
  python letter_voices.py letter_vernon_gun  just those

Voices (the user picked the A samples in run/voice_samples/stage2, matched back to Fish ids by MFCC fingerprint):
  Vernon   6758b288b72748a3bfe4f39a7674ebde  "Dursley"
  Petunia  32e344f53f114cfcbb7ed086f10f2403  "british female"
  Hagrid   f6fd51174e2743db91c4e91fe5de4ec1  (same as the Wand memory)
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
LENGTHS = os.path.join(HERE, "letter_clip_lengths.json")
RAW_DIR = (r"C:\Users\alonl\AppData\Local\Temp\claude\C--Users-alonl-OneDrive-Desktop-harry-potter-mod"
           r"\eca4c2f1-9bf3-4c65-b86d-13bbb709117a\scratchpad\letter_audio")

VERNON = "6758b288b72748a3bfe4f39a7674ebde"
PETUNIA = "32e344f53f114cfcbb7ed086f10f2403"
HAGRID = "f6fd51174e2743db91c4e91fe5de4ec1"

# id -> (voice, speed, text)
LINES = {
    "letter_petunia_up": (PETUNIA, 1.0, "Up! Get up! Now!"),
    "letter_vernon_mine": (VERNON, 1.0, "Who'd be writing to you? Give that here, boy!"),
    "letter_vernon_nomail": (VERNON, 1.0, "No more post! Not one more letter comes through that door!"),
    "letter_vernon_sunday": (VERNON, 1.0, "Fine day, Sunday. No post on Sundays!"),
    "letter_vernon_shout": (VERNON, 1.0, "Get away from them! Out! Out!"),
    "letter_vernon_gun": (VERNON, 1.0, "Who's there? I'm warning you, I'm armed!"),
    "letter_hagrid_sorry": (HAGRID, 1.0, "Sorry about that."),
    "letter_hagrid_found": (HAGRID, 1.0, "There you are. Last time I saw you, you were only a baby."),
    "letter_hagrid_wizard": (HAGRID, 1.0, "You're a wizard."),
    "letter_hagrid_letter": (HAGRID, 1.0, "This is for you. It's been trying to reach you for weeks."),
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
