"""Generate the Whomping Willow's sounds through the ElevenLabs SFX API.

Reads the API key from the local key file (never committed), writes MP3s to a scratch directory,
then converts each to mono OGG Vorbis with ffmpeg and drops it into the resource pack. Sound events
are registered in ModSounds and mapped in assets/hogwartsmagic/sounds.json. Pass sound names as
arguments to regenerate only those.

Mono is not optional -- Minecraft will not attenuate a stereo sound with distance, so a stereo clip
plays at full volume from anywhere in the world.

Every prompt here is weather and timber. The willow has no voice and must never sound like it has
one: the moment a clip reads as a growl it stops being a tree and starts being a monster wearing a
tree costume.
"""

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

KEY_FILE = r"C:\Users\alonl\eleven_key.txt"
API = "https://api.elevenlabs.io/v1/sound-generation"

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                       "sounds", "whomping_willow")
TMP_DIR = os.path.join(os.environ.get("TEMP", "."), "whomping_willow_sfx")

# name -> (prompt, seconds, prompt_influence)
SOUNDS = {
    "ambient": (
        "A very large old tree in a steady gale: thick timber creaking and groaning under load, a "
        "broad hiss of leaves, small twigs ticking against each other. Recorded outdoors, close. "
        "No words, no music.",
        4.0, 0.5),
    "alert": (
        "A huge tree suddenly taking strain: one sharp deep creak of timber twisting, a heavy groan "
        "rising quickly, leaves shaken hard all at once. No words, no music.",
        1.6, 0.6),
    "whip": (
        "A long heavy tree branch swung hard through the air, a deep whoosh with leaves tearing "
        "past, ending in a dry woody crack. No words, no music.",
        1.2, 0.6),
    "impact": (
        "A heavy wooden club slamming into packed earth, a dull thud with a splintering crack and a "
        "spray of dirt. Close and short. No words, no music.",
        1.0, 0.6),
    "groan": (
        "A massive tree trunk leaning under its own weight, long low fibrous groaning of wet timber "
        "under stress, deep and slow. No words, no music.",
        3.0, 0.55),
    "roots": (
        "Thick tree roots wrenching and grinding in wet soil, earth tearing, stones grating, wood "
        "straining underground. No words, no music.",
        2.0, 0.55),
    "hurt": (
        "An axe biting deep into living wood, a wet chopping impact followed by a sharp splitting "
        "crack and a short shudder of leaves. No words, no music.",
        1.2, 0.6),
    "death": (
        "A huge tree being felled: a long tearing split up the trunk, timber cracking apart, then "
        "the whole mass toppling and crashing down through branches into the ground, settling into "
        "silence. No words, no music.",
        5.0, 0.55),
}


def read_key():
    with open(KEY_FILE, encoding="utf-8") as fh:
        return fh.read().strip()


def generate(key, name, prompt, seconds, influence, attempts=4):
    """POST one prompt, retrying on 429 and on dropped connections."""
    payload = {"text": prompt, "duration_seconds": seconds, "prompt_influence": influence}
    last = None
    for attempt in range(attempts):
        req = urllib.request.Request(
            API,
            data=json.dumps(payload).encode("utf-8"),
            headers={"xi-api-key": key, "Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=240) as resp:
                data = resp.read()
            mp3 = os.path.join(TMP_DIR, name + ".mp3")
            with open(mp3, "wb") as fh:
                fh.write(data)
            return mp3
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", "replace")[:400]
            last = "HTTP %s %s" % (exc.code, body)
            if exc.code not in (429, 500, 502, 503, 504):
                raise RuntimeError(last) from exc
        except Exception as exc:
            last = repr(exc)
        wait = 15 * (attempt + 1)
        print("  retrying in %ds (%s)" % (wait, last))
        time.sleep(wait)
    raise RuntimeError("gave up on %s: %s" % (name, last))


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
