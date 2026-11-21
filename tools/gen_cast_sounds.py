#!/usr/bin/env python3
"""Generates the wand cast sounds with the ElevenLabs sound-effects API.

Writes mono Ogg Vorbis files to assets/hogwartsmagic/sounds/cast/ (several takes of each sound, so repeated
casts do not sound the same) and prints the sounds.json entries to add. Needs ELEVENLABS_API_KEY, and
ffmpeg with libvorbis. Generation is not deterministic: running it again gives different takes.

Run from the repository root: python3 tools/gen_cast_sounds.py [name ...]
"""
import json
import os
import subprocess
import sys
import tempfile
import urllib.request

OUT = "src/main/resources/assets/hogwartsmagic/sounds/cast"
API = "https://api.elevenlabs.io/v1/sound-generation"

# name: (takes, seconds, prompt)
SOUNDS = {
    "swish_light": (3, 0.5, "a single very quick light swish of a thin wooden wand flicked through the air, "
                            "close, dry, no reverb, no music"),
    "swish_heavy": (3, 0.7, "a single fast heavy whoosh of a stick slashed hard through the air, close, punchy, "
                            "dry, no reverb, no music"),
    "swish_rise": (2, 0.9, "a soft airy rising whoosh as a wand is lifted up high, with a faint magical shimmer, "
                           "dry, no music"),
    "release": (3, 0.8, "a sharp magical spell being cast from a wand: a bright electric crackle and snap with a "
                        "short zap, fantasy game sound effect, no music, no voice"),
    "release_dark": (2, 1.0, "a dark sinister magic curse being cast: a deep electric crackle with a low hiss, "
                             "fantasy game sound effect, no music, no voice"),
    "impact": (3, 0.9, "a magic spell bolt hitting a target: a bright sparkling burst with a soft thump, fantasy "
                       "game sound effect, no music, no voice"),
    "impact_heavy": (2, 1.4, "a powerful magic blast hitting a stone wall: a deep boom with crackling sparks and "
                             "falling debris, fantasy game sound effect, no music"),
    "whoosh_snap": (3, 0.5, "an extremely fast sharp whoosh of a thin wand whipped through the air ending in a "
                            "crisp snap, close, dry, punchy, no reverb, no music"),
    "release_punch": (3, 0.6, "a punchy deep magical bass thump with a sharp crack at the start, a powerful spell "
                              "blasting out of a wand, satisfying fantasy game sound effect, no music, no voice"),
    "impact_crunch": (3, 0.8, "a hard punchy magic spell hitting a body: a sharp crack, a deep heavy thud and a "
                              "sizzling burst of sparks, satisfying fantasy game hit sound, no music, no voice"),
    "switch": (2, 0.5, "a tiny delicate magical sparkle chime, short and soft, fantasy game interface sound, "
                       "no music"),
}


def generate(prompt, seconds):
    body = json.dumps({"text": prompt, "duration_seconds": seconds, "prompt_influence": 0.55}).encode()
    request = urllib.request.Request(API, data=body, method="POST", headers={
        "Content-Type": "application/json",
        "xi-api-key": os.environ.get("ELEVENLABS_API_KEY", "set-by-proxy"),
    })
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def to_ogg(mp3, path):
    """Mono (so the sound is placed in the world), the leading silence cut, levels evened out, short fade out."""
    with tempfile.NamedTemporaryFile(suffix=".mp3") as tmp:
        tmp.write(mp3)
        tmp.flush()
        subprocess.run([
            "ffmpeg", "-loglevel", "error", "-y", "-i", tmp.name, "-ac", "1", "-ar", "44100",
            "-af", "silenceremove=start_periods=1:start_threshold=-45dB,loudnorm=I=-16:TP=-1.5,"
                   "areverse,afade=t=in:d=0.08,areverse",
            "-c:a", "libvorbis", "-q:a", "5", path,
        ], check=True)


def main(names):
    os.makedirs(OUT, exist_ok=True)
    entries = {}
    for name, (takes, seconds, prompt) in SOUNDS.items():
        if names and name not in names:
            continue
        files = []
        for i in range(1, takes + 1):
            path = f"{OUT}/{name}{i}.ogg"
            to_ogg(generate(prompt, seconds), path)
            files.append({"name": f"hogwartsmagic:cast/{name}{i}"})
            print("wrote", path)
        entries[f"cast.{name}"] = {"sounds": files}
    print(json.dumps(entries, indent=2))


if __name__ == "__main__":
    os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    main(sys.argv[1:])
