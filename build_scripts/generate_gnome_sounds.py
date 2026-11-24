"""Generate the garden gnome sound set via the ElevenLabs sound-effects API, then master it.

Three stages, run from the repo root:

    python build_scripts/generate_gnome_sounds.py fetch  RAW_DIR      # raw MP3 takes, kept OUT of the repo
    python build_scripts/generate_gnome_sounds.py env    RAW_DIR      # print each take's 100 ms RMS envelope
    python build_scripts/generate_gnome_sounds.py master RAW_DIR      # cut, level-match, limit, encode OGGs

The API key lives OUTSIDE the repo (C:\\Users\\alonl\\eleven_key.txt); it is read at run time and never printed.

Mastering (every take): the raw clip is cut to an explicit (start, end) window chosen from its envelope so it
opens on the event, level-matched by RMS into the repo's -11..-26 dBFS range, run through
alimiter=level=disabled:limit=0.65, and written as mono 44.1 kHz vorbis q5. Input seeking (-ss before -i) is used
in both the measuring and the encoding pass. Prompts are loud and close-miked with "no silence"; quietness is the
playSound volume's job, not the sample's.
"""
import array
import json
import math
import os
import subprocess
import sys
import urllib.error
import urllib.request

KEY_PATH = r"C:\Users\alonl\eleven_key.txt"
API_URL = "https://api.elevenlabs.io/v1/sound-generation"

OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "hogwartsmagic", "sounds", "gnome",
)

TARGET_RMS_DB = -17.0           # where each cut is level-matched to (the repo's sounds sit in -11..-26)
RANGE_DB = (-26.0, -11.0)
LIMIT = 0.65

# name -> (prompt, duration_seconds)
TAKES = {
    "giggle1": ("a tiny goblin-like creature giggling mischievously, high nasal cackle, close-miked, dry, no silence", 1.2),
    "giggle2": ("a tiny goblin-like creature giggling mischievously, high nasal cackle, close-miked, dry, no silence", 1.2),
    "giggle3": ("a tiny goblin-like creature giggling mischievously, high nasal cackle, close-miked, dry, no silence", 1.2),
    "squeak1": ("a small creature's sharp indignant squeak, a high yelp, close-miked, no silence", 0.6),
    "squeak2": ("a small creature's sharp indignant squeak, a high yelp, close-miked, no silence", 0.6),
    "bite": ("small sharp teeth snapping shut on skin, a quick chomp with a tiny growl, close-miked, no silence", 0.5),
    "wail": ("a tiny creature wailing as it is flung through the air, a high 'waaaah' that swoops away, "
             "close-miked, no silence", 1.6),
}

# name -> (start_seconds, end_seconds) of the mastered cut inside the raw take; chosen from the `env` printout.
WINDOWS = {}
_windows_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "gnome_sound_windows.json")
if os.path.exists(_windows_path):
    with open(_windows_path, "r", encoding="utf-8") as fh:
        WINDOWS = json.load(fh)


def read_key():
    with open(KEY_PATH, "r", encoding="utf-8-sig") as fh:
        return fh.read().strip()


def generate(key, prompt, seconds):
    body = json.dumps({"text": prompt, "duration_seconds": seconds, "prompt_influence": 0.45}).encode("utf-8")
    req = urllib.request.Request(API_URL, data=body,
                                 headers={"xi-api-key": key, "Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=180) as resp:
        return resp.read()


def mono_filter(path):
    """Averages stereo to mono by hand: ffmpeg's default downmix sums correlated channels at +3 dB, which
    pushed peaks through the limiter. Empty string for an already-mono file."""
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "a:0", "-show_entries", "stream=channels",
                          "-of", "csv=p=0", path], check=True, capture_output=True, text=True).stdout.strip()
    return "pan=mono|c0=0.5*c0+0.5*c1," if out == "2" else ""


def pcm(path, start=None, end=None):
    """Decoded mono 44.1 kHz samples in [-1, 1], optionally only [start, end) (input seeking)."""
    cmd = ["ffmpeg", "-loglevel", "error"]
    if start is not None:
        cmd += ["-ss", "%.3f" % start]
    if end is not None:
        cmd += ["-t", "%.3f" % (end - (start or 0.0))]
    af = mono_filter(path).rstrip(",")
    cmd += ["-i", path] + (["-af", af] if af else []) + ["-ac", "1", "-ar", "44100", "-f", "s16le", "-"]
    raw = subprocess.run(cmd, check=True, capture_output=True).stdout
    a = array.array("h")
    a.frombytes(raw[: len(raw) - len(raw) % 2])
    return [v / 32768.0 for v in a]


def rms_db(samples):
    if not samples:
        return -120.0
    m = sum(v * v for v in samples) / len(samples)
    return 10.0 * math.log10(m) if m > 1e-12 else -120.0


def fetch(raw_dir):
    key = read_key()
    os.makedirs(raw_dir, exist_ok=True)
    for name, (prompt, seconds) in TAKES.items():
        dest = os.path.join(raw_dir, name + ".mp3")
        if os.path.exists(dest):
            print("skip (exists):", name)
            continue
        try:
            with open(dest, "wb") as fh:
                fh.write(generate(key, prompt, seconds))
            print("wrote", name + ".mp3", os.path.getsize(dest), "bytes")
        except urllib.error.HTTPError as exc:
            print("FAILED", name, exc.code, exc.reason)
            if os.path.exists(dest):
                os.unlink(dest)


def envelope(raw_dir):
    for name in TAKES:
        path = os.path.join(raw_dir, name + ".mp3")
        if not os.path.exists(path):
            continue
        s = pcm(path)
        step = 4410
        bars = []
        for i in range(0, len(s), step):
            bars.append(rms_db(s[i:i + step]))
        print("%-8s %.2fs  whole RMS %.1f dBFS" % (name, len(s) / 44100.0, rms_db(s)))
        print("         " + " ".join("%5.0f" % b for b in bars))


def master(raw_dir):
    os.makedirs(OUT_DIR, exist_ok=True)
    for name in TAKES:
        path = os.path.join(raw_dir, name + ".mp3")
        if name not in WINDOWS or not os.path.exists(path):
            print("no window / take for", name)
            continue
        start, end = WINDOWS[name]
        dur = end - start
        cut = pcm(path, start, end)
        gain = TARGET_RMS_DB - rms_db(cut)
        dest = os.path.join(OUT_DIR, name + ".ogg")
        for attempt in range(3):
            fade_out = min(0.04, dur / 4.0)
            af = (mono_filter(path) + "volume=%.2fdB,alimiter=level=disabled:limit=%.2f,afade=t=in:d=0.004,afade=t=out:st=%.3f:d=%.3f"
                  % (gain, LIMIT, dur - fade_out, fade_out))
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-ss", "%.3f" % start, "-t", "%.3f" % dur,
                            "-i", path, "-af", af, "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", dest],
                           check=True)
            out = pcm(dest)
            level = rms_db(out)
            peak = max(abs(v) for v in out) if out else 0.0
            if RANGE_DB[0] <= level <= RANGE_DB[1]:
                break
            gain += TARGET_RMS_DB - level           # the limiter ate some: push and try again
        print("%-8s %.2fs  RMS %.1f dBFS  peak %.2f  (%s)" % (
            name, dur, level, peak, "ok" if RANGE_DB[0] <= level <= RANGE_DB[1] else "OUT OF RANGE"))


def main():
    if len(sys.argv) != 3 or sys.argv[1] not in ("fetch", "env", "master"):
        print(__doc__)
        return 1
    {"fetch": fetch, "env": envelope, "master": master}[sys.argv[1]](sys.argv[2])
    return 0


if __name__ == "__main__":
    sys.exit(main())
