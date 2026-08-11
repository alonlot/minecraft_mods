"""Generate the merpeople's sound effects through the ElevenLabs SFX API.

Reads the API key from C:\\Users\\alonl\\eleven_key.txt (never committed), writes
MP3s to a scratch directory, then converts each to mono OGG Vorbis with ffmpeg
and drops it into the resource pack. Sound events are registered in ModSounds
and mapped in assets/hogwartsmagic/sounds.json.

The joke the books make about Mermish is that it is a real language with a real
song in it, and that both are unbearable in air - Harry only hears the egg's
chorus once he puts it underwater. So `call` and `song` are the submerged
versions, warm and musical, and `screech` is the same voice heard from above
the surface: shrill, grating and wrong.

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
                       "sounds", "merperson")
TMP_DIR = os.path.join(os.environ.get("TEMP", "."), "merperson_sfx")

# name -> (prompt, seconds, prompt_influence)
SOUNDS = {
    "ambient": (
        "Heard from underwater: a low murmuring throat-click from a "
        "humanoid sea creature, wet gurgling consonants, bubbles rising, "
        "distant and muffled by deep cold water. No words, no music.",
        4.0,
        0.6,
    ),
    "call": (
        "Heard from underwater: an eerie warning call from a humanoid sea "
        "creature, a rising warbling cry with a hard clicking rasp behind it, "
        "muffled and reverberant in deep water, threatening. No words in any "
        "human language, no music.",
        2.5,
        0.6,
    ),
    "screech": (
        "The same cry heard in open air: a horrible shrill screeching wail, "
        "grating and metallic, like a hundred fingernails and a seagull, "
        "unbearable and harsh, no reverb. No words, no music.",
        2.5,
        0.65,
    ),
    "song": (
        "Heard from underwater: an eerie wordless chorus of many voices "
        "singing in unison, cold and beautiful and inhuman, slow rising "
        "melody, heavy underwater reverb, bubbles. Haunting, not comforting.",
        9.0,
        0.5,
    ),
    "spear": (
        "A stone-tipped spear thrust underwater, a fast heavy whoosh through "
        "water then a wet solid impact. Short and brutal. No music.",
        1.2,
        0.6,
    ),
    "hurt": (
        "A humanoid sea creature crying out in pain underwater, a short "
        "strangled shriek breaking into wet gurgling, bubbles bursting. "
        "No words, no music.",
        1.5,
        0.55,
    ),
    "death": (
        "A humanoid sea creature dying underwater, a long wavering wail "
        "sinking away into gurgling bubbles and silence, distant and cold. "
        "No words, no music.",
        4.0,
        0.6,
    ),
}


def read_key():
    with open(KEY_FILE, encoding="utf-8") as fh:
        return fh.read().strip()


def generate(key, name, prompt, seconds, influence, attempts=4):
    """POST one prompt, retrying on 429 and on dropped connections.

    The SFX endpoint rate-limits per concurrent request rather than per minute,
    so a plain backoff clears it; a 429 that survives every attempt is a real
    quota problem and the body says so.
    """
    payload = {
        "text": prompt,
        "duration_seconds": seconds,
        "prompt_influence": influence,
    }
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
        except Exception as exc:                    # dropped connection
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
