"""Generate the Hippogriff's sound set through the ElevenLabs SFX API.

Buckbeak in Prisoner of Azkaban is voiced as an eagle in front and a horse behind:
sharp raptor screeches and beak clicks over equine snorts, nickers and hoof scrapes.
This regenerates the three placeholder cues (ambient/hurt/death) with that character
and adds the five new ones the rebuilt animation set triggers.

Reads the API key from C:\\Users\\alonl\\eleven_key.txt (never committed), writes MP3s
to a scratch dir, converts each to mono OGG Vorbis with ffmpeg and drops it into the
resource pack.

Run:  python build_scripts/generate_hippogriff_sounds.py [name ...]
"""

import json as _json
import os
import subprocess
import sys
import urllib.request

KEY_FILE = r"C:\Users\alonl\eleven_key.txt"
API = "https://api.elevenlabs.io/v1/sound-generation"

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                       "sounds", "hippogriff")
TMP_DIR = os.path.join(os.environ.get("TEMP", "."), "hippogriff_sfx")

# name -> (prompt, seconds, prompt_influence)
SOUNDS = {
    "ambient": (
        "A large eagle chirruping softly, a short warbling trill followed by a quiet "
        "horse-like snort through nostrils. Calm, curious, close-up. A couple of dry "
        "beak clicks at the end. No music, no human voice.",
        3.0,
        0.5,
    ),
    "screech": (
        "A huge eagle screaming at full volume, a piercing raptor screech that cracks "
        "and rasps at the top, ending in an angry rattling shriek. Enormous bird, "
        "close-up, outdoors. No music, no human voice.",
        3.0,
        0.6,
    ),
    "hurt": (
        "A sharp startled squawk from a giant eagle, cut short and pained, with a "
        "rough gasping horse grunt underneath. Sudden, one hit. No music, no words.",
        1.5,
        0.6,
    ),
    "death": (
        "A long descending eagle wail losing strength, collapsing into a heavy shuddering "
        "horse groan and a final slow exhale through nostrils. Mournful, close-up. "
        "No music, no human voice.",
        4.5,
        0.55,
    ),
    "bow": (
        "A low soft chirrup from a very large bird, a gentle rolling coo answered by a "
        "quiet horse nicker. Warm, accepting, unhurried. Close-up. No music, no words.",
        2.5,
        0.5,
    ),
    "wings": (
        "One enormous pair of feathered wings beating down hard, a deep heavy whump of "
        "displaced air with feathers rustling and creaking. Close-up, outdoors. "
        "No music, no voice.",
        1.5,
        0.6,
    ),
    "snort": (
        "A horse snorting and blowing air through its nostrils, then a hoof scraping "
        "and pawing at dry ground. Impatient, close-up. No music, no voice.",
        2.5,
        0.55,
    ),
    "takeoff": (
        "A giant winged animal launching from the ground: talons scraping earth, then "
        "two enormous wing beats hammering air, with a short sharp eagle screech over "
        "the top. Powerful, close-up. No music, no voice.",
        3.5,
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
        ["ffmpeg", "-y", "-i", mp3, "-ac", "1", "-ar", "44100",
         "-c:a", "libvorbis", "-q:a", "5", ogg],
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
        print("generating", name, "...", flush=True)
        mp3 = generate(key, name, prompt, seconds, influence)
        ogg = to_ogg(mp3, name)
        print("  wrote %s (%d bytes)" % (ogg, os.path.getsize(ogg)), flush=True)


if __name__ == "__main__":
    main()
