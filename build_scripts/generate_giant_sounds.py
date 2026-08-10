"""Generate the Giant's sound effects through the ElevenLabs SFX API.

Reads the API key from C:\\Users\\alonl\\eleven_key.txt (never committed), writes
MP3s to a scratch directory, then converts each to mono OGG Vorbis with ffmpeg
and drops it into the resource pack. Sound events are registered in ModSounds
and mapped in assets/hogwartsmagic/sounds.json.

Pass sound names as arguments to regenerate only those.
"""

import json
import os
import subprocess
import sys
import urllib.request

KEY_FILE = r"C:\Users\alonl\eleven_key.txt"
API = "https://api.elevenlabs.io/v1/sound-generation"

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                       "sounds", "giant")
TMP_DIR = os.path.join(os.environ.get("TEMP", "."), "giant_sfx")

# name -> (prompt, seconds, prompt_influence)
SOUNDS = {
    "ambient1": (
        "An enormous dim-witted giant mumbling to itself, low guttural nonsense "
        "syllables in a huge chesty voice, slow heavy breathing underneath. "
        "Dopey rather than menacing. No words, no music.",
        3.0,
        0.5,
    ),
    "ambient2": (
        "A huge giant snuffling and grunting, deep bass throat noise, curious "
        "animal-like mumble, a wet sniff. Enormous lungs. No words, no music.",
        3.0,
        0.5,
    ),
    "angry": (
        "A deep guttural giant snarl rising into a warning growl, monstrous and "
        "heavy, enormous chest resonance. Threatening. No words, no music.",
        2.5,
        0.55,
    ),
    "roar": (
        "A colossal giant roaring with fury, enormous deep bellow, chest-rattling "
        "low frequencies, echoing away through a dense forest. No words, no music.",
        4.0,
        0.6,
    ),
    "step": (
        "One enormous heavy footstep thudding onto forest ground, deep bass "
        "impact, soil and small debris shifting. Nothing else. No music.",
        1.5,
        0.6,
    ),
    "swipe": (
        "An enormous arm swinging fast through the air, heavy low whoosh with "
        "weight behind it, ending in a dull air thump. No music.",
        1.5,
        0.6,
    ),
    "kick_smash": (
        "A giant kicking through wood and earth, heavy splintering timber, "
        "bursting dirt and stone chips, one deep percussive impact and debris "
        "scattering afterwards. No music.",
        3.0,
        0.6,
    ),
    "grab": (
        "A huge fist snatching and closing around something, quick brutal grab, "
        "cloth and body scuffle, a short effortful giant grunt. No music.",
        2.0,
        0.55,
    ),
    "throw": (
        "A giant hurling something heavy through the air, an effortful roaring "
        "grunt with a huge arm whoosh and a release. No words, no music.",
        2.5,
        0.55,
    ),
    "hurt": (
        "An enormous giant bellowing in pain, deep guttural roar of hurt, "
        "monstrous and short. No words, no music.",
        2.0,
        0.55,
    ),
    "death": (
        "An enormous giant dying, a long collapsing groan falling in pitch, one "
        "final deep bellow, then a massive body crashing to the ground. "
        "No words, no music.",
        5.0,
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
        data=json.dumps(payload).encode("utf-8"),
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
        print("generating", name, "...")
        mp3 = generate(key, name, prompt, seconds, influence)
        ogg = to_ogg(mp3, name)
        print("  wrote %s (%d bytes)" % (ogg, os.path.getsize(ogg)))


if __name__ == "__main__":
    main()
