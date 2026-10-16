"""Generate and master the Pensieve's seven sounds through the ElevenLabs SFX API.

    python build_scripts/generate_pensieve_sounds.py gen [names...]     # fetch MP3s to a scratch dir
    python build_scripts/generate_pensieve_sounds.py env [names...]     # print a 100 ms RMS envelope of each MP3
    python build_scripts/generate_pensieve_sounds.py master [names...]  # trim, level-match, limit -> OGG

The API key is read from C:\\Users\\alonl\\eleven_key.txt at run time (never committed, never printed). Raw takes are
not shippable: `master` cuts each to the WINDOWS below so the clip opens on its event, matches it to a target RMS and
caps the peaks with a limiter. Prompts ask for the event itself, loud and close; the game makes it quiet through the
playSound volume. void_hum is crossfaded onto itself so it loops without a click.
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
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "sounds", "pensieve")
TMP_DIR = os.environ.get("PENSIEVE_SFX_TMP") or os.path.join(os.environ.get("TEMP", "."), "pensieve_sfx")

# name -> (prompt, seconds)
SOUNDS = {
    "swallow": ("A deep liquid gulp into a silver basin, airy rush, loud, close-miked, immediate, no silence", 3.0),
    "fall": ("A long falling whoosh through a dark void with soft shimmering, loud, close-miked, immediate, "
             "no silence", 6.0),
    "void_hum": ("A low seamless looping drone, a slow breathing hum, steady and continuous, loud, close-miked, "
                 "no silence", 8.0),
    "rise": ("A reverse whoosh rising upward, ending in a soft surface break of liquid, loud, close-miked, "
             "immediate, no silence", 3.0),
    "gasp": ("A sharp startled gasp for air with a liquid splash, loud, close-miked, immediate, no silence", 2.0),
    "distant_thud": ("A loud, close, heavy thud heard through water, muffled underwater, deep and immediate, "
                     "no silence", 1.5),
    "pour": ("A thin stream of silver liquid pouring into stone, loud, close-miked, immediate, no silence", 3.0),
}

# name -> dict(start, end seconds in the raw take; rms target dBFS; optional compress, loop crossfade seconds)
WINDOWS = {
    "swallow": dict(start=0.1, end=1.3, rms=-18, fade=0.2),
    "fall": dict(start=1.0, end=3.8, rms=-22, loop=0.7),
    "void_hum": dict(start=2.0, end=7.5, rms=-24, loop=0.8),
    "rise": dict(start=0.2, end=1.9, rms=-18),
    "gasp": dict(start=0.0, end=0.8, rms=-16, fade=0.15),
    "distant_thud": dict(start=0.0, end=0.9, rms=-16, compress=True, fade=0.2),
    "pour": dict(start=0.0, end=1.8, rms=-22, fade=0.25),
}
LIMIT = 0.65


def read_key():
    with open(KEY_FILE, encoding="utf-8") as fh:
        return fh.read().strip()


def mp3_path(name):
    return os.path.join(TMP_DIR, name + ".mp3")


def fetch(key, name, prompt, seconds, attempts=4):
    payload = {"text": prompt, "duration_seconds": seconds, "prompt_influence": 0.45}
    last = None
    for attempt in range(attempts):
        req = urllib.request.Request(
            API, data=json.dumps(payload).encode("utf-8"),
            headers={"xi-api-key": key, "Content-Type": "application/json"}, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=240) as resp:
                data = resp.read()
            with open(mp3_path(name), "wb") as fh:
                fh.write(data)
            return
        except urllib.error.HTTPError as exc:
            last = "HTTP %s %s" % (exc.code, exc.read().decode("utf-8", "replace")[:300])
            if exc.code not in (429, 500, 502, 503, 504):
                raise RuntimeError(last) from exc
        except Exception as exc:
            last = repr(exc)
        time.sleep(15 * (attempt + 1))
    raise RuntimeError("gave up on %s: %s" % (name, last))


def decode(path, start=0.0, length=None, filt=None):
    """Mono s16le samples at 44.1 kHz; input seeking so start is exact."""
    cmd = ["ffmpeg", "-v", "error", "-ss", str(start), "-i", path]
    if length is not None:
        cmd += ["-t", str(length)]
    if filt:
        cmd += ["-af", filt]
    cmd += ["-ac", "1", "-ar", "44100", "-f", "s16le", "-"]
    raw = subprocess.run(cmd, check=True, capture_output=True).stdout
    return struct.unpack("<%dh" % (len(raw) // 2), raw)


def rms_db(samples):
    if not samples:
        return -99.0
    ms = sum(s * s for s in samples) / len(samples)
    return 10 * math.log10(ms / (32768.0 ** 2)) if ms > 0 else -99.0


def envelope(path):
    s = decode(path)
    win = 4410
    return [rms_db(s[i:i + win]) for i in range(0, len(s) - win + 1, win)]


def names_from(args):
    return [a for a in args if a in SOUNDS] or list(SOUNDS)


def master_one(name):
    w = WINDOWS[name]
    src = mp3_path(name)
    length = w["end"] - w["start"]
    out = os.path.join(OUT_DIR, name + ".ogg")
    os.makedirs(OUT_DIR, exist_ok=True)
    chain = []
    if w.get("compress"):
        chain.append("acompressor=threshold=0.08:ratio=4:attack=5:release=80:makeup=1")
    if w.get("loop"):
        # Seamless ring: output = seg[x:L] + blend(seg tail fading out, seg head fading in). The blend is followed
        # by seg[x] in the loop, which is exactly what the head continues into, so the wrap has no seam.
        x = w["loop"]
        folded = os.path.join(TMP_DIR, name + "_loop.wav")
        subprocess.run(["ffmpeg", "-y", "-v", "error", "-ss", str(w["start"]), "-i", src, "-t", str(length + x),
                        "-filter_complex",
                        "[0:a]asplit=3[a][b][c];"
                        "[a]atrim=%f:%f,asetpts=PTS-STARTPTS[mid];"
                        "[b]atrim=%f:%f,asetpts=PTS-STARTPTS[tail];"
                        "[c]atrim=0:%f,asetpts=PTS-STARTPTS[head];"
                        "[tail][head]acrossfade=d=%f:c1=qsin:c2=qsin[blend];"
                        "[mid][blend]concat=n=2:v=0:a=1[o]" % (x, length, length, length + x, x, x),
                        "-map", "[o]", "-ac", "1", "-ar", "44100", folded], check=True)
        samples_src = folded
        cut = length
    else:
        samples_src = src
        cut = length
    pre = decode(samples_src, 0.0 if w.get("loop") else w["start"], cut, ",".join(chain) or None)
    gain = w["rms"] - rms_db(pre)
    fade = w.get("fade", 0.1)
    tail = [] if w.get("loop") else ["afade=t=out:st=%f:d=%f" % (max(0.0, cut - fade), fade)]
    flt = ",".join(chain + ["volume=%fdB" % gain, "alimiter=level=disabled:limit=%s" % LIMIT] + tail)
    ss = "0" if w.get("loop") else str(w["start"])
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-ss", ss, "-i", samples_src, "-t", str(cut), "-af", flt,
                    "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", out], check=True)
    final = rms_db(decode(out))
    print("%-13s window %.2f-%.2fs  gain %+.1f dB  final RMS %.1f dBFS" % (name, w["start"], w["end"], gain, final))


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    cmd, names = sys.argv[1], names_from(sys.argv[2:])
    os.makedirs(TMP_DIR, exist_ok=True)
    if cmd == "gen":
        key = read_key()
        for n in names:
            prompt, secs = SOUNDS[n]
            fetch(key, n, prompt, secs)
            print("fetched", n)
    elif cmd == "env":
        for n in names:
            print(n, " ".join("%.0f" % v for v in envelope(mp3_path(n))))
    elif cmd == "master":
        for n in names:
            master_one(n)


if __name__ == "__main__":
    main()
