"""Generate the Gringotts undercroft sound set via the ElevenLabs sound-effects API.

Same shape as the goblin, basilisk and giant generators: the key lives OUTSIDE the repo,
is never printed or committed, and the results are converted to game-ready mono OGG with
ffmpeg.

Three groups, and they are doing different jobs:

  vault_door.*  The door is most of the reason this feature exists, and a door is 60% sound.
                Four beats that have to read as one mechanism: a finger on cold iron, the
                metal going liquid, the bolts, and the whole thing parting.
  cart.*        A rumble that scales with speed and a flange screech that only happens in
                the corners are what make a spiral SOUND like a spiral. The slam is the
                bottom of a drop.
  ironbelly.*   Old, sick and half-starved rather than a boss. The cower is the important
                one -- it has to sound pitiable, or cowing it with Clankers feels like
                winning instead of like bullying something that has been down there too long.

Run from the repo root:  python build_scripts/generate_gringotts_sounds.py
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

ASSETS = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "hogwartsmagic", "sounds",
)

# folder -> {name: (prompt, seconds)}
SOUNDS = {
    "vault_door": {
        "touch": (
            "A fingertip dragging slowly down a huge sheet of cold iron in a stone chamber, "
            "faint metallic ringing rising under it, one soft tick as something inside the "
            "metal wakes. Close and dry. No words, no music.",
            1.4,
        ),
        "ripple": (
            "A vast sheet of metal turning liquid: a deep bending groan of steel, a low "
            "watery surge moving across it, faint high harmonic shimmer riding on top. "
            "Heavy and slow, not an explosion. No words, no music.",
            2.6,
        ),
        "cogs": (
            "Enormous iron gears turning inside a stone wall: heavy interlocking teeth "
            "meshing, deep ratcheting clanks, a slow hydraulic groan, steam venting in short "
            "hisses between the clanks. Heard from outside a thick wall. No words, no music.",
            2.4,
        ),
        "bolts": (
            "A long sequence of heavy iron bolts withdrawing one after another around a "
            "circular frame, each a hard metallic clack with a screwing scrape before it, "
            "rhythm accelerating slightly. Cold and dry, echoing off stone. No words, no music.",
            2.6,
        ),
        "open": (
            "A colossal iron vault door parting: stone grinding on stone, a long "
            "pressure-release hiss of vapour, a deep resonant boom rolling away into a huge "
            "cavern. Ends open, not cut. No words, no music.",
            3.4,
        ),
        "seal": (
            "A colossal iron vault door slamming shut and locking: one enormous impact, iron "
            "bolts driving home hard in quick succession, a final deep resonant thud dying "
            "into stone. No words, no music.",
            2.2,
        ),
    },
    "cart": {
        "rumble": (
            "A heavy iron mine cart running fast on old rails inside a rock tunnel, "
            "continuous low rumble and rattle, wheels drumming over rail joints, seamless "
            "and loopable with no start or end. No words, no music.",
            3.0,
        ),
        "screech": (
            "Steel wheel flanges squealing hard against rail in a tight curve, a sustained "
            "high metallic screech with a rough edge, echoing in a stone tunnel, seamless and "
            "loopable. No words, no music.",
            3.0,
        ),
        "launch": (
            "An iron mine cart lurching into motion: a heavy metallic clank of a brake "
            "releasing, chain rattle, then wheels beginning to roll and gather speed. "
            "No words, no music.",
            1.8,
        ),
        "brake": (
            "A hand brake dragging hard on an iron mine cart: rough grinding metal on metal, "
            "rising screech, wheels slowing. No words, no music.",
            1.8,
        ),
        "slam": (
            "A heavy iron mine cart hitting the bottom of a steep drop and bottoming out on "
            "its springs: one huge metallic crash, followed by rattling and a low echo in a "
            "vast cavern. No words, no music.",
            2.0,
        ),
    },
    "ironbelly": {
        "ambient": (
            "An enormous old sick dragon breathing slowly in a cave: low wet wheezing rumble, "
            "a faint rattle at the end of each breath, scales shifting on stone. Tired and "
            "ancient, not threatening. No words, no music.",
            3.0,
        ),
        "roar": (
            "An enormous dragon roaring in a deep stone cavern, a cracked ragged bellow with "
            "a broken edge to it rather than a clean one, long echo. No words, no music.",
            2.8,
        ),
        "cower": (
            "An enormous animal in distress backing away: a high whimpering keen from "
            "something very large, breath catching, scales dragging on stone as it retreats. "
            "Pitiable, frightened, not aggressive. No words, no music.",
            2.4,
        ),
        "chain": (
            "Enormous heavy iron chains dragging and clanking across a stone floor, taking up "
            "slack with a hard jolt at the end, deep and echoing. No words, no music.",
            2.2,
        ),
    },
    "clankers": {
        "ring": (
            "A cluster of small iron noisemakers shaken hard: sharp overlapping metallic "
            "rattles and clanks, bright and jangling, echoing off stone walls. Harsh and "
            "unpleasant. No words, no music.",
            1.6,
        ),
    },
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

    failures = []
    made = 0
    for folder, sounds in SOUNDS.items():
        out_dir = os.path.join(ASSETS, folder)
        os.makedirs(out_dir, exist_ok=True)
        for name, (prompt, seconds) in sounds.items():
            dest = os.path.join(out_dir, name + ".ogg")
            if os.path.exists(dest):
                print("skip (exists):", folder + "/" + name)
                continue
            try:
                to_ogg(generate(key, prompt, seconds), dest)
                made += 1
                print("wrote %s/%s.ogg  %d bytes" % (folder, name, os.path.getsize(dest)))
            except urllib.error.HTTPError as exc:
                failures.append((folder + "/" + name, "HTTP %s %s" % (exc.code, exc.reason)))
                print("FAILED", folder + "/" + name, exc.code, exc.reason)
            except Exception as exc:  # noqa: BLE001 - report and keep going
                failures.append((folder + "/" + name, str(exc)))
                print("FAILED", folder + "/" + name, exc)

    print("\n%d generated, %d failed" % (made, len(failures)))
    for name, why in failures:
        print("  ", name, "-", why)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
