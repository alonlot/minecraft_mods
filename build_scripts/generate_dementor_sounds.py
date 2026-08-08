"""Generate the Dementor's new sound effects through the ElevenLabs SFX API.

Reads the API key from C:\\Users\\alonl\\eleven_key.txt (never committed), writes
MP3s to a scratch directory, then converts each to mono OGG Vorbis with ffmpeg
and drops it into the resource pack. Sound events are registered in ModSounds
and mapped in assets/hogwartsmagic/sounds.json.

Existing dementor/ambient, hurt and death are left alone; this only fills in the
three cues the new animation set needs.
"""

import os
import subprocess
import sys
import urllib.request

KEY_FILE = r"C:\Users\alonl\eleven_key.txt"
API = "https://api.elevenlabs.io/v1/sound-generation"

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                       "sounds", "dementor")
TMP_DIR = os.path.join(os.environ.get("TEMP", "."), "dementor_sfx")

# name -> (prompt, seconds, prompt_influence)
SOUNDS = {
    "suck": (
        "A slow dragging inhale of freezing air, like a death rattle played in "
        "reverse. Hollow, wet, rasping. Very low rumble underneath, no music, "
        "no voice, no words.",
        4.0,
        0.55,
    ),
    "kiss": (
        "A sudden rushing intake of air followed by a deep guttural roar that "
        "rises into a rattling shriek. Cavernous, wet, inhuman. Sounds like "
        "something enormous opening its mouth right next to you. No words.",
        5.0,
        0.6,
    ),
    "shriek": (
        "A piercing inhuman shriek of pain recoiling backwards, dry and rattling, "
        "cut short. Like wind screaming through a cracked pipe. No words, no music.",
        3.0,
        0.6,
    ),
}


def read_key():
    with open(KEY_FILE, encoding="utf-8") as fh:
        return fh.read().strip()


def generate(key, name, prompt, seconds, influence):
    payload = {
        "text": prompt,
        "duration_seconds": seconds,
        "prompt_influence": influence,
    }
    import json as _json

    req = urllib.request.Request(
        API,
        data=_json.dumps(payload).encode("utf-8"),
        headers={"xi-api-key": key, "Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=180) as resp:
        data = resp.read()
    mp3 = os.path.join(TMP_DIR, name + ".mp3")
    with open(mp3, "wb") as fh:
        fh.write(data)
    return mp3


def to_ogg(mp3, name):
    ogg = os.path.join(OUT_DIR, name + ".ogg")
    subprocess.run(
        ["ffmpeg", "-y", "-i", mp3, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", ogg],
        check=True,
        capture_output=True,
    )
    return ogg


def main():
    key = read_key()
    os.makedirs(TMP_DIR, exist_ok=True)
    os.makedirs(OUT_DIR, exist_ok=True)

    only = sys.argv[1:]
    for name, (prompt, seconds, influence) in SOUNDS.items():
        if only and name not in only:
            continue
        print("generating", name, "...")
        mp3 = generate(key, name, prompt, seconds, influence)
        ogg = to_ogg(mp3, name)
        print("  wrote %s (%d bytes)" % (ogg, os.path.getsize(ogg)))


if __name__ == "__main__":
    main()
