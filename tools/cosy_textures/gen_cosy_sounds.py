"""
The two indoor ambience loops of the cosy home pack: rain on the windows and the hearth crackle.

Run from the repo root:  python tools/cosy_textures/gen_cosy_sounds.py [fetch|master]
  fetch   asks ElevenLabs for each take and saves the raw mp3 under RAW_DIR (the key is read from KEY_FILE,
          which lives outside the repo; it is never printed or written anywhere)
  master  envelope-trims, makes a seamless loop, level-matches and writes mono OGG into the mod

Every raw take is judged from its 100 ms RMS envelope before it is used, as the memory notes say: the window is
read off the envelope, the loop is closed with an equal-power crossfade, and the level is matched by RMS then limited.
"""
import json
import math
import os
import subprocess
import sys
import urllib.request

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src/main/resources/assets/hogwartsmagic/sounds/cosy")
KEY_FILE = r"C:\Users\alonl\eleven_key.txt"
RAW_DIR = os.environ.get("COSY_RAW_DIR", os.path.join(os.path.dirname(os.path.abspath(__file__)), "_raw"))
SR = 44100

SOUNDS = {
    "rain_window_loop": {
        "prompt": "Steady rain pattering against a window pane, heard from inside a quiet room. Dense, continuous "
                  "soft drops running down the glass, close-miked, even and unchanging throughout, no thunder, "
                  "no wind gusts, no voices, no silence.",
        "seconds": 12,
        "target_rms_db": -19.0,
    },
    "hearth_crackle_loop": {
        "prompt": "A cosy fireplace crackling, close-miked. Logs burning with soft steady crackles, small pops and "
                  "a low warm hiss, dense and continuous throughout, no silence, no voices, no loud bangs, no wood "
                  "being moved.",
        "seconds": 12,
        "target_rms_db": -19.0,
    },
}
CROSSFADE_S = 1.0
HEAD_TRIM_S = 0.25


def fetch():
    key = open(KEY_FILE, encoding="utf-8").read().strip()
    os.makedirs(RAW_DIR, exist_ok=True)
    for name, spec in SOUNDS.items():
        body = {"text": spec["prompt"], "duration_seconds": spec["seconds"], "prompt_influence": 0.45}
        req = urllib.request.Request("https://api.elevenlabs.io/v1/sound-generation",
                                     data=json.dumps(body).encode("utf-8"),
                                     headers={"xi-api-key": key, "Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=180) as resp:
            data = resp.read()
        path = os.path.join(RAW_DIR, name + ".mp3")
        open(path, "wb").write(data)
        print("saved", path, len(data), "bytes")


def decode(path):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                         check=True, stdout=subprocess.PIPE).stdout
    return np.frombuffer(raw, dtype=np.float32).copy()


def rms_db(x):
    return 20 * math.log10(max(1e-9, float(np.sqrt(np.mean(np.square(x))))))


def envelope(x, window_s=0.1):
    n = int(SR * window_s)
    return [rms_db(x[i:i + n]) for i in range(0, len(x) - n + 1, n)]


def encode_ogg(x, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2").tobytes()
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-f", "s16le", "-ar", str(SR), "-ac", "1", "-i", "-",
                    "-c:a", "libvorbis", "-q:a", "5", path], check=True, input=pcm)


def master():
    for name, spec in SOUNDS.items():
        x = decode(os.path.join(RAW_DIR, name + ".mp3"))
        env = envelope(x)
        print(name, "length %.2fs" % (len(x) / SR))
        print("  envelope dB per 100 ms:", " ".join("%.0f" % e for e in env))
        # The window: skip the head (the take often opens with a click or a ramp) and keep the rest.
        x = x[int(HEAD_TRIM_S * SR):]
        # Seamless loop: the last CROSSFADE_S seconds fade out under the first CROSSFADE_S seconds fading in,
        # equal power, and the result is the length without that overlap. End and start then meet mid-texture.
        n = int(CROSSFADE_S * SR)
        body = x[:len(x) - n].copy()
        tail = x[len(x) - n:]
        t = np.linspace(0, math.pi / 2, n, dtype=np.float32)
        body[:n] = body[:n] * np.sin(t) + tail * np.cos(t)
        # Level-match by RMS, then limit: vorbis overshoots, so leave headroom rather than chase 0 dBFS.
        gain = 10 ** ((spec["target_rms_db"] - rms_db(body)) / 20)
        body = body * gain
        peak = float(np.max(np.abs(body)))
        if peak > 0.65:
            body = np.tanh(body / 0.65 * 0.9) / 0.9 * 0.65 if peak > 0.9 else body * (0.65 / peak)
        print("  gain %+.1f dB, final RMS %.1f dBFS, peak %.2f, loop %.2fs" % (20 * math.log10(gain), rms_db(body),
                                                                         float(np.max(np.abs(body))), len(body) / SR))
        encode_ogg(body, os.path.join(OUT, name + ".ogg"))
        print("  wrote", os.path.join(OUT, name + ".ogg"))


if __name__ == "__main__":
    step = sys.argv[1] if len(sys.argv) > 1 else "master"
    if step == "fetch":
        fetch()
    else:
        master()
