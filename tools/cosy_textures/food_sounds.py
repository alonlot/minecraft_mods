"""Cosy food sounds via ElevenLabs: the cooking pot's bubbling loop.

    python tools/cosy_textures/food_sounds.py <scratch_dir>

Reads the API key from C:/Users/alonl/eleven_key.txt (outside the repo; never printed, never committed). The raw take
goes to <scratch_dir>; the mastered file goes to assets/hogwartsmagic/sounds/cosy/pot_bubble_loop.ogg.

Mastering (see the project's ElevenLabs notes): measure a 100 ms RMS envelope, check there is no dead air, make the
take loop seamlessly by cross-fading its tail over its head (output = take length minus the fade), level-match by RMS
to about -22 dBFS, limit at 0.65 (Vorbis overshoots), and encode mono OGG. Input seeking (-ss before -i) everywhere.
"""
import json
import math
import os
import struct
import subprocess
import sys
import urllib.request

KEY_FILE = r"C:\Users\alonl\eleven_key.txt"
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "sounds", "cosy")

PROMPT = ("A thick stew bubbling slowly in a big cast iron cooking pot over a fire, close-miked, "
          "continuous soft gloopy bubbles popping and a gentle steady simmer, warm and cosy, "
          "dense and continuous throughout, no silence, no voices, no music, no metal clanking")
TAKE_SECONDS = 10.0
FADE = 1.5
TARGET_RMS_DB = -22.0


def generate(path):
    key = open(KEY_FILE, encoding="utf-8").read().strip()
    body = json.dumps({"text": PROMPT, "duration_seconds": TAKE_SECONDS, "prompt_influence": 0.5}).encode()
    req = urllib.request.Request("https://api.elevenlabs.io/v1/sound-generation", data=body,
                                 headers={"xi-api-key": key, "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as r:
        data = r.read()
    with open(path, "wb") as f:
        f.write(data)


def decode(path):
    out = subprocess.run(["ffmpeg", "-v", "error", "-ss", "0", "-i", path, "-f", "s16le", "-ac", "1", "-ar", "44100", "-"],
                         capture_output=True, check=True).stdout
    return struct.unpack("<%dh" % (len(out) // 2), out)


def rms_db(samples):
    if not samples:
        return -120.0
    e = sum(s * s for s in samples) / len(samples)
    return 10 * math.log10(e / (32768.0 ** 2) + 1e-12)


def envelope(samples, window=4410):
    return [round(rms_db(samples[i:i + window]), 1) for i in range(0, len(samples) - window + 1, window)]


def main():
    scratch = sys.argv[1]
    os.makedirs(scratch, exist_ok=True)
    raw = os.path.join(scratch, "pot_bubble_raw.mp3")
    if not os.path.exists(raw):
        generate(raw)
    samples = decode(raw)
    env = envelope(samples)
    print("take: %.2fs, overall RMS %.1f dBFS" % (len(samples) / 44100.0, rms_db(samples)))
    print("100 ms RMS envelope (dBFS):", env)
    quiet = [i for i, v in enumerate(env) if v < -55]
    print("windows under -55 dBFS:", len(quiet), "of", len(env))
    length = len(samples) / 44100.0
    keep = length - FADE
    # Gain by the RMS of what will actually be kept.
    gain_db = TARGET_RMS_DB - rms_db(samples)
    print("gain %.1f dB" % gain_db)
    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, "pot_bubble_loop.ogg")
    # [b] is the tail, [a] the body; the tail fades out as the head fades in, so the end of the output (end of the
    # body) flows straight into its start (the start of the tail) when it repeats.
    flt = ("[0:a]atrim=0:%.3f,asetpts=N/SR/TB[a];[0:a]atrim=%.3f:%.3f,asetpts=N/SR/TB[b];"
           "[b][a]acrossfade=d=%.3f:c1=tri:c2=tri,volume=%.2fdB,alimiter=level=disabled:limit=0.65[o]"
           % (keep, keep, length, FADE, gain_db))
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-ss", "0", "-i", raw, "-filter_complex", flt, "-map", "[o]",
                    "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", out], check=True)
    mastered = decode(out)
    print("mastered: %.2fs, RMS %.1f dBFS, peak %.1f dBFS" % (len(mastered) / 44100.0, rms_db(mastered),
                                                              20 * math.log10(max(abs(s) for s in mastered) / 32768.0)))
    print("envelope:", envelope(mastered))
    print("wrote", out)


if __name__ == "__main__":
    main()
