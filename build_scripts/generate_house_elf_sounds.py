"""Generate the House Elf sound set via the ElevenLabs sound-effects API.

The voice being aimed for is Dobby's: tiny, high-pitched, squeaky and nervous,
with a raspy edge. Nothing here should sound like an adult.

The API key lives OUTSIDE the repo (default C:\\Users\\alonl\\eleven_key.txt) and is
never printed or committed. Output is converted to game-ready mono OGG with
ffmpeg and dropped into the mod's assets.

Run from the repo root:  python build_scripts/generate_house_elf_sounds.py
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
    "src", "main", "resources", "assets", "hogwartsmagic", "sounds", "house_elf",
)

# name -> (prompt, duration_seconds)
SOUNDS = {
    "ambient1": (
        "A tiny timid creature muttering nervously to itself, very high-pitched "
        "squeaky raspy voice, small and anxious, quiet, short",
        2.0,
    ),
    "ambient2": (
        "A small frightened creature sniffling and whimpering, high-pitched thin "
        "squeak, tremulous and meek",
        1.6,
    ),
    "ambient3": (
        "Small bare feet padding softly on forest floor with a faint anxious "
        "high-pitched squeak and a rustle of cloth",
        2.0,
    ),
    "crack": (
        "A sudden violent whip-crack of air snapping shut, sharp explosive bang "
        "with a brief magical shimmer tail, teleportation",
        1.2,
    ),
    "hurt": (
        "A tiny creature's sharp yelp of pain, very high-pitched thin shriek, "
        "startled and brief",
        0.9,
    ),
    "death": (
        "A small frail creature's last breath, a thin high gasp fading into "
        "silence, weak and sorrowful",
        1.8,
    ),
    "bind": (
        "A small meek creature squeaking in submission, a soft grateful whimper "
        "with a rustle of rough cloth as it bows",
        1.6,
    ),
    "free": (
        "An ecstatic high-pitched squeal of pure joy from a tiny creature, "
        "delighted shriek and laughter, jubilant",
        2.0,
    ),
    "punish": (
        "A dull heavy thud of a head striking wood followed by a small pained "
        "high-pitched squeak",
        1.2,
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
