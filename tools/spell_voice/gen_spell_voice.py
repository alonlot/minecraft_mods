"""Spoken incantations for spell learning: one Fish Audio take per spell, trimmed, level matched, mono OGG.

Usage:  python tools/spell_voice/gen_spell_voice.py [spell_id ...]
  no ids = every spell in SPELLS. Raw mp3 takes go to the scratch dir given by SPELL_VOICE_RAW (default
  tools/spell_voice/raw, git-ignored). Output: src/main/resources/assets/hogwartsmagic/sounds/spells/voice/<id>.ogg

The key is read from C:/Users/alonl/fish_key.txt and never printed. SAY holds a respelling for a spell when the
plain spelling comes out wrong; check every take with check_spell_voice (whisper + IncantationMatcher).
"""
import json
import os
import subprocess
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
VOICE_ID = "8a2dc101722542f5a7300dd3d8478a8f"
KEY_FILE = r"C:\Users\alonl\fish_key.txt"
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "sounds", "spells", "voice")
RAW_DIR = os.environ.get("SPELL_VOICE_RAW", os.path.join(ROOT, "tools", "spell_voice", "raw"))

# Spell id -> the incantation as the matcher spells it (IncantationMatcher.INCANTATIONS).
SPELLS = json.load(open(os.path.join(os.path.dirname(__file__), "spells.json"), encoding="utf-8"))
# Spell id -> text sent to the TTS, when the plain spelling is mispronounced. Filled in after listening/checking.
SAY = json.load(open(os.path.join(os.path.dirname(__file__), "say.json"), encoding="utf-8"))


def tts(text, out_path):
    key = open(KEY_FILE, encoding="utf-8").read().strip()
    body = json.dumps({"text": text, "reference_id": VOICE_ID, "format": "mp3"}).encode("utf-8")
    req = urllib.request.Request("https://api.fish.audio/v1/tts", data=body, method="POST", headers={
        "Authorization": "Bearer " + key, "model": "speech-1.6", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = resp.read()
    with open(out_path, "wb") as f:
        f.write(data)


def to_ogg(mp3, ogg):
    # Envelope trim (both ends), then level match, then mono 44.1k vorbis.
    trim = ("silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.03,"
            "areverse,silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.08,areverse,"
            "afade=t=in:d=0.01,loudnorm=I=-16:TP=-1.5:LRA=11")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", mp3, "-af", trim, "-ac", "1", "-ar", "44100",
                    "-c:a", "libvorbis", "-q:a", "5", ogg], check=True)


def main(ids):
    os.makedirs(OUT_DIR, exist_ok=True)
    os.makedirs(RAW_DIR, exist_ok=True)
    for sid in ids or list(SPELLS):
        text = SAY.get(sid, SPELLS[sid] + "!")
        mp3 = os.path.join(RAW_DIR, sid + ".mp3")
        tts(text, mp3)
        to_ogg(mp3, os.path.join(OUT_DIR, sid + ".ogg"))
        print(sid, "<-", repr(text))


if __name__ == "__main__":
    main(sys.argv[1:])
