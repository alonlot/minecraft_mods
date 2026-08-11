"""Generate the Grindylow's sound effects through the ElevenLabs SFX API.

Reads the API key from C:\\Users\\alonl\\eleven_key.txt (never committed), writes
MP3s to a scratch directory, then converts each to mono OGG Vorbis with ffmpeg
and drops it into the resource pack. Sound events are registered in ModSounds
and mapped in assets/hogwartsmagic/sounds.json.

The grindylow is small, wet and vicious, and everything it does should sound
like it: nothing here is deep or heavy. The shriek is the one that matters —
it is what a nest of them makes when the hoard is opened, and it needs to be the
sound of being in trouble.

Pass sound names as arguments to regenerate only those.
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
                       "sounds", "grindylow")
TMP_DIR = os.path.join(os.environ.get("TEMP", "."), "grindylow_sfx")

# name -> (prompt, seconds, prompt_influence)
SOUNDS = {
    "ambient": (
        "Heard underwater: a small amphibious creature clicking and burbling "
        "wetly to itself, tiny chittering throat noises, bubbles. Close and "
        "unpleasant. No words, no music.",
        3.0,
        0.6,
    ),
    "shriek": (
        "Heard underwater: several small vicious creatures shrieking at once as "
        "they attack, high thin screams layered over each other, wet and "
        "gurgling, sudden and alarming. No words, no music.",
        2.0,
        0.65,
    ),
    "grab": (
        "Heard underwater: long thin fingers seizing hold of a leg, a wet "
        "slapping grip followed by a small creature's straining hiss. Short. "
        "No music.",
        1.2,
        0.6,
    ),
    "snap": (
        "Thin brittle bones snapping underwater, a sharp dry crack muffled by "
        "water, followed by a small shrill squeal of pain. Very short. "
        "No music.",
        1.0,
        0.6,
    ),
    "hurt": (
        "A small amphibious creature shrieking in pain underwater, thin and "
        "high and wet, cut short. No words, no music.",
        1.0,
        0.55,
    ),
    "death": (
        "A small amphibious creature dying underwater, a thin wail collapsing "
        "into wet gurgling and bubbles, then nothing. No words, no music.",
        2.0,
        0.6,
    ),
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
