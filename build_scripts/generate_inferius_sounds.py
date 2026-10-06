"""Generate the Inferius's sound effects through the ElevenLabs SFX API.

Reads the API key from C:\\Users\\alonl\\eleven_key.txt (never committed), writes raw
MP3s to a scratch directory, then masters each take (input-seek to an explicit
window so it opens on the event, RMS level-match, limiter) into a mono OGG Vorbis
in the resource pack. Sound events are registered in ModSounds and mapped in
assets/hogwartsmagic/sounds.json.

Usage:
  python generate_inferius_sounds.py gen [names...]    fetch raw takes only
  python generate_inferius_sounds.py env [names...]    print 100 ms RMS envelopes
  python generate_inferius_sounds.py master [names...] window + level-match -> OGG
"""

import json
import math
import os
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.request

KEY_FILE = r"C:\Users\alonl\eleven_key.txt"
API = "https://api.elevenlabs.io/v1/sound-generation"

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                       "sounds", "inferius")
TMP_DIR = os.path.join(os.environ.get("TEMP", "."), "inferius_sfx")

# name -> (prompt, seconds, prompt_influence)
SOUNDS = {
    "ambient": (
        "Close-miked, loud and continuous throughout: the slow rasping wet breath "
        "of a drowned corpse, water rattling in a dead throat, long laboured "
        "inhale and exhale. No voice, no words, no music, no silence.",
        3.0,
        0.5,
    ),
    "hurt": (
        "Close-miked and immediate: a heavy wet thud of a blow landing on a "
        "waterlogged corpse with a choked gurgle of water. No words, no music.",
        1.0,
        0.5,
    ),
    "death": (
        "Close-miked and loud: a waterlogged body collapsing, bones clattering and "
        "wet cloth slapping the ground, ending in a final bubbling rattle. "
        "No words, no music.",
        2.0,
        0.5,
    ),
    "grab": (
        "Close-miked and immediate: a sharp hiss and a cold wet grip clamping "
        "onto a limb, slick fingers squeezing. No words, no music.",
        1.0,
        0.5,
    ),
    "rise": (
        "Close-miked, loud and dense throughout: the wet scraping and dragging of "
        "a heavy body hauling itself out of black water, streaming water and "
        "dripping. No voice, no music, no silence.",
        2.5,
        0.5,
    ),
}

# name -> (start_s, end_s, target_rms_dbfs); filled in from the printed envelopes.
MASTER = {
    "ambient": (0.25, 1.8, -22.0),
    "hurt": (0.0, 0.55, -17.0),
    "death": (0.1, 1.45, -17.0),
    "grab": (0.05, 0.65, -17.0),
    "rise": (0.0, 1.65, -19.0),
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


def decode(mp3, start=0.0, dur=None):
    """Decode to mono s16le samples; -ss BEFORE -i so measuring matches encoding."""
    cmd = ["ffmpeg", "-v", "error", "-ss", str(start), "-i", mp3]
    if dur is not None:
        cmd += ["-t", str(dur)]
    cmd += ["-ac", "1", "-ar", "44100", "-f", "s16le", "-"]
    raw = subprocess.run(cmd, check=True, capture_output=True).stdout
    return struct.unpack("<%dh" % (len(raw) // 2), raw)


def rms_db(samples):
    if not samples:
        return -120.0
    ms = sum(s * s for s in samples) / len(samples)
    return 10 * math.log10(ms / (32768.0 ** 2)) if ms > 0 else -120.0


def envelope(name):
    samples = decode(os.path.join(TMP_DIR, name + ".mp3"))
    win = 4410
    print(name, "total %.2fs" % (len(samples) / 44100.0))
    row = []
    for i in range(0, len(samples), win):
        row.append("%.1f:%5.1f" % (i / 44100.0, rms_db(samples[i:i + win])))
    for i in range(0, len(row), 8):
        print("  " + "  ".join(row[i:i + 8]))


def master(name):
    start, end, target = MASTER[name]
    mp3 = os.path.join(TMP_DIR, name + ".mp3")
    dur = end - start
    measured = rms_db(decode(mp3, start, dur))
    gain = target - measured
    fade = min(0.08, dur / 4)
    ogg = os.path.join(OUT_DIR, name + ".ogg")
    af = "volume=%.2fdB,alimiter=level=disabled:limit=0.65,afade=t=out:st=%.3f:d=%.3f" % (
        gain, dur - fade, fade)
    subprocess.run(
        ["ffmpeg", "-y", "-v", "error", "-ss", str(start), "-i", mp3, "-t", str(dur),
         "-af", af, "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", ogg],
        check=True, capture_output=True)
    out = rms_db(decode(ogg))
    print("%s: window %.2f-%.2f, measured %.1f dB, gain %+.1f dB, result %.1f dB RMS, %d bytes"
          % (name, start, end, measured, gain, out, os.path.getsize(ogg)))


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "gen"
    only = sys.argv[2:]
    os.makedirs(TMP_DIR, exist_ok=True)
    os.makedirs(OUT_DIR, exist_ok=True)
    key = read_key() if mode == "gen" else None
    for name, (prompt, seconds, influence) in SOUNDS.items():
        if only and name not in only:
            continue
        if mode == "gen":
            print("generating", name, "...")
            print("  wrote", generate(key, name, prompt, seconds, influence))
        elif mode == "env":
            envelope(name)
        elif mode == "master":
            master(name)


if __name__ == "__main__":
    main()
