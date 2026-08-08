"""Generate the Gringotts goblin sound set via the ElevenLabs sound-effects API.

The API key lives OUTSIDE the repo (default C:\\Users\\alonl\\eleven_key.txt) and is
never printed or committed. Output is converted to game-ready mono OGG with
ffmpeg and dropped into the mod's assets.

Run from the repo root:  python build_scripts/generate_goblin_sounds.py
"""

import json
import os
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request

KEY_PATH = r"C:\Users\alonl\eleven_key.txt"
API_URL = "https://api.elevenlabs.io/v1/sound-generation"

OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "hogwartsmagic", "sounds", "goblin",
)

# name -> (prompt, duration_seconds)
SOUNDS = {
    "ambient1": (
        "A small elderly goblin creature muttering to itself under its breath, "
        "low gravelly nasal grumbling, dry and disapproving, short",
        2.0,
    ),
    "ambient2": (
        "An old goblin bank clerk sniffing and clearing its throat, a short "
        "raspy nasal huff, gravelly and impatient",
        1.6,
    ),
    "ambient3": (
        "Gold coins being counted and stacked on a wooden counter, small metallic "
        "clinks, with a faint low goblin grunt underneath",
        2.2,
    ),
    "hurt": (
        "A small goblin creature yelping in pain, sharp high-pitched raspy shriek, "
        "short and nasal",
        1.0,
    ),
    "death": (
        "A small goblin creature's dying wheeze, a rattling gurgling gasp trailing "
        "off into silence, raspy and low",
        1.8,
    ),
    "trade": (
        "A satisfied goblin grunt of approval followed by gold coins clinking into "
        "a metal scale pan",
        1.6,
    ),
    "no": (
        "A short sharp disapproving goblin snort and hiss, nasal and derisive",
        1.0,
    ),
    "angry": (
        "An enraged goblin guard snarling and shrieking, harsh raspy battle cry, "
        "high-pitched and vicious",
        1.6,
    ),
}


def read_key():
    with open(KEY_PATH, "r", encoding="utf-8-sig") as fh:
        return fh.read().strip()


def generate(key, prompt, seconds):
    body = json.dumps(
        {"text": prompt, "duration_seconds": seconds, "prompt_influence": 0.45}
    ).encode("utf-8")
    req = urllib.request.Request(
        API_URL,
        data=body,
        headers={"xi-api-key": key, "Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=180) as resp:
        return resp.read()


def to_ogg(mp3_bytes, dest):
    with tempfile.NamedTemporaryFile(suffix=".mp3", delete=False) as tmp:
        tmp.write(mp3_bytes)
        tmp_path = tmp.name
    try:
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", tmp_path,
             "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", dest],
            check=True,
        )
    finally:
        os.unlink(tmp_path)


def main():
    if not os.path.exists(KEY_PATH):
        print("ERROR: API key file not found at", KEY_PATH)
        return 1
    key = read_key()
    os.makedirs(OUT_DIR, exist_ok=True)

    failures = []
    for name, (prompt, seconds) in SOUNDS.items():
        dest = os.path.join(OUT_DIR, name + ".ogg")
        if os.path.exists(dest):
            print("skip (exists):", name)
            continue
        try:
            mp3 = generate(key, prompt, seconds)
            to_ogg(mp3, dest)
            print("wrote", name + ".ogg", os.path.getsize(dest), "bytes")
        except urllib.error.HTTPError as exc:
            failures.append((name, "HTTP %s %s" % (exc.code, exc.reason)))
            print("FAILED", name, exc.code, exc.reason)
        except Exception as exc:  # noqa: BLE001 - report and keep going
            failures.append((name, str(exc)))
            print("FAILED", name, exc)

    if failures:
        print("\n%d sound(s) failed:" % len(failures))
        for name, why in failures:
            print("  ", name, "-", why)
        return 1
    print("\nAll sounds generated into", OUT_DIR)
    return 0


if __name__ == "__main__":
    sys.exit(main())
