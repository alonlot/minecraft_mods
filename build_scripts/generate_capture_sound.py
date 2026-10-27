"""Generate the Pensieve capture sound (a whisper drawn out like a thread) through the ElevenLabs SFX API.

Reads the API key from C:\\Users\\alonl\\eleven_key.txt (never committed). The raw take is decoded, a 100 ms RMS
envelope picks the window that opens on the sound and ends when it dies away, the level is matched to a target RMS,
and the clip is written as mono OGG Vorbis to sounds/pensieve/capture.ogg. Input seeking (-ss before -i) is used in
both the measuring and the encoding pass so the measured gain is the written one.
"""

import array
import json
import math
import os
import subprocess
import sys
import urllib.request

KEY_FILE = r"C:\Users\alonl\eleven_key.txt"
API = "https://api.elevenlabs.io/v1/sound-generation"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "sounds", "pensieve", "capture.ogg")
TMP = os.path.join(os.environ.get("TEMP", "."), "capture_sfx")
PROMPT = ("A soft breathy whisper drawn out like a thread, with a faint silver shimmer. "
          "Close-miked, dense and continuous throughout, no silence, no words, no music.")
TARGET_RMS_DB = -22.0
RATE = 44100


def decode(mp3, ss=0.0, t=None):
    cmd = ["ffmpeg", "-v", "error", "-ss", str(ss), "-i", mp3]
    if t is not None:
        cmd += ["-t", str(t)]
    cmd += ["-ac", "1", "-ar", str(RATE), "-f", "s16le", "-"]
    raw = subprocess.run(cmd, check=True, capture_output=True).stdout
    a = array.array("h")
    a.frombytes(raw)
    return a


def rms_db(samples):
    if not samples:
        return -120.0
    s = sum(x * x for x in samples) / len(samples)
    return 10 * math.log10(max(s, 1.0) / (32768.0 ** 2))


def main():
    os.makedirs(TMP, exist_ok=True)
    mp3 = os.path.join(TMP, "capture.mp3")
    if "--reuse" not in sys.argv:
        key = open(KEY_FILE, encoding="utf-8").read().strip()
        req = urllib.request.Request(
            API, data=json.dumps({"text": PROMPT, "duration_seconds": 2.5, "prompt_influence": 0.5}).encode(),
            headers={"xi-api-key": key, "Content-Type": "application/json"}, method="POST")
        with urllib.request.urlopen(req, timeout=180) as resp:
            open(mp3, "wb").write(resp.read())

    a = decode(mp3)
    win = RATE // 10
    env = [rms_db(a[i:i + win]) for i in range(0, len(a) - win + 1, win)]
    print("envelope dB per 100 ms:", " ".join("%.0f" % e for e in env))
    peak = max(env)
    live = [i for i, e in enumerate(env) if e > peak - 24]
    start = max(0, live[0] - 0) * 0.1
    end = (live[-1] + 1) * 0.1
    print("window %.1f s .. %.1f s (peak %.1f dB)" % (start, end, peak))

    window = decode(mp3, start, end - start)
    gain = TARGET_RMS_DB - rms_db(window)
    print("gain %.1f dB" % gain)
    fade = min(0.12, (end - start) / 4)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", str(start), "-t", str(end - start), "-i", mp3, "-ac", "1",
                    "-ar", str(RATE), "-af",
                    "volume=%.2fdB,alimiter=level=disabled:limit=0.5,afade=t=in:d=0.02,afade=t=out:st=%.2f:d=%.2f"
                    % (gain, end - start - fade, fade),
                    "-c:a", "libvorbis", "-q:a", "5", OUT], check=True)
    check = decode(OUT)
    print("wrote %s: %.2f s, rms %.1f dB, peak %.1f dB" % (
        OUT, len(check) / RATE, rms_db(check), 20 * math.log10(max(abs(x) for x in check) / 32768.0)))


if __name__ == "__main__":
    main()
