"""Generate the Basilisk's sound effects through the ElevenLabs SFX API.

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
                       "sounds", "basilisk")
TMP_DIR = os.path.join(os.environ.get("TEMP", "."), "basilisk_sfx")

# name -> (prompt, seconds, prompt_influence)
SOUNDS = {
    "ambient": (
        "An enormous serpent breathing, a long low hiss with a wet rattle deep "
        "in the throat, huge lungs, slow and patient. Reptilian and menacing. "
        "No words, no music.",
        4.0,
        0.6,
    ),
    "ambient2": (
        "Enormous heavy snake scales dragging slowly over wet stone, a low "
        "rasping slither with the weight of something huge behind it, faint "
        "dripping water. No music.",
        4.0,
        0.6,
    ),
    "hiss_threat": (
        "A giant snake rearing and hissing in warning, a sharp explosive hiss "
        "rising into a sustained venomous rattle, jaws creaking open. "
        "Terrifying. No words, no music.",
        2.5,
        0.6,
    ),
    "strike": (
        "A colossal snake striking, a fast whipping lunge through air followed "
        "by an enormous wet snap of jaws slamming shut. Brutal and instant. "
        "No music.",
        1.5,
        0.6,
    ),
    "spit": (
        "A huge reptile spitting venom, a guttural hawking hiss then a wet "
        "pressurised spray launching forward. Nasty and fluid. No music.",
        1.5,
        0.6,
    ),
    "bite": (
        "Enormous fangs sinking into flesh, a wet crunching puncture with a "
        "low serpent growl behind it, short and vicious. No music.",
        1.2,
        0.6,
    ),
    "burrow": (
        "Something enormous driving down into earth and stone, heavy grinding "
        "rock, collapsing soil and gravel pouring, a deep rumble receding "
        "underground. No music.",
        3.0,
        0.6,
    ),
    "erupt": (
        "The ground bursting open as something colossal erupts from below, an "
        "explosion of earth and stone debris with a huge serpent roar-hiss "
        "tearing out of it. No music.",
        2.5,
        0.6,
    ),
    "gaze_heartbeat": (
        "A single slow heavy heartbeat under crushing dread, deep sub-bass "
        "thud with a dry stone crackling sound layered over it, as if flesh is "
        "turning to stone. No music, no melody.",
        1.6,
        0.55,
    ),
    "hurt": (
        "An enormous serpent shrieking in pain, a strangled high hiss cracking "
        "into a deep guttural roar, thrashing coils. Short. No words, no music.",
        1.8,
        0.55,
    ),
    "death": (
        "A colossal serpent dying, a long ragged hiss draining away, wet "
        "gurgling, then an enormous heavy body collapsing and settling onto "
        "stone. No words, no music.",
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
