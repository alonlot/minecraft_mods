"""Great Hall sound effects (ElevenLabs) -- generate, inspect, master.

Reads the key from C:\\Users\\alonl\\eleven_key.txt (never printed, never committed).
Raw MP3 takes go to a scratch directory outside the repo.

  python great_hall_sfx.py gen [names...]     fetch raw takes (skips existing; names force a refetch)
  python great_hall_sfx.py env [names...]     print the 100 ms RMS envelope of each raw take
  python great_hall_sfx.py master [names...]  trim, level-match, limit, write the .ogg
  python great_hall_sfx.py check              every sounds.json entry resolves to an .ogg

Mastering follows the ElevenLabs note: explicit (start, end) window read off the envelope,
RMS level-match, alimiter, and ffmpeg INPUT seeking (-ss before -i) in the measuring and the
encoding pass alike.
"""

import json
import os
import subprocess
import sys
import urllib.request

import numpy as np

KEY_FILE = r"C:\Users\alonl\eleven_key.txt"
API = "https://api.elevenlabs.io/v1/sound-generation"

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic")
OUT_DIR = os.path.join(ASSETS, "sounds", "pensieve", "sfx")
RAW_DIR = (r"C:\Users\alonl\AppData\Local\Temp\claude\C--Users-alonl-OneDrive-Desktop-harry-potter-mod"
           r"\4f579a71-d4a1-442d-8a31-491678844c18\scratchpad\hall_audio")

TARGET_RMS = -18.0   # dBFS; the repo's sounds sit around -11 to -26
LIMIT = 0.65

# name -> (prompt, seconds)
SOUNDS = {
    "door_groan": (
        "Huge ancient oak doors creaking open slowly, a deep wooden groan with iron hinges "
        "screeching, close-miked and loud, continuous from the first instant. No music, no voices.", 4.0),
    "door_boom": (
        "Massive wooden doors thudding fully open against stone walls, one deep loud boom with "
        "stone-hall reverb, immediate impact at the very start. No music, no voices.", 3.0),
    "murmur_a": (
        "A large stone hall full of hundreds of chattering schoolchildren, dense continuous excited "
        "murmur, cutlery clinks, reverberant, loud and steady throughout with no silence. No music.", 22.0),
    "murmur_b": (
        "Hundreds of students chatting and laughing at long dining tables in a vast stone hall, dense "
        "continuous babble, plates and cutlery clinking, reverberant, loud and steady throughout with "
        "no silence and no distinct voices. No music.", 22.0),
    "hush": (
        "A large crowd falling quickly silent, loud shushing and hissing of hundreds of people "
        "starting immediately, in a stone hall. No music.", 3.0),
    "cheer_big": (
        "A house table of a hundred students erupting in loud cheers, clapping and whoops in a stone "
        "hall, huge roar starting immediately and dense throughout. No music.", 6.0),
    "cheer_small": (
        "Loud applause and cheers from a crowd of students in a stone hall, whoops and clapping, "
        "dense and continuous from the first instant. No music.", 5.0),
    "steps_group": (
        "A group of children's footsteps on stone flagstones walking together, close-miked, loud, "
        "continuous steady pace throughout. No music, no voices.", 8.0),
    "steps_one": (
        "A single child's footsteps on stone flagstones, nervous hesitant pace, close-miked and loud, "
        "starting immediately. No music, no voices.", 4.0),
    "stool": (
        "A wooden stool scraping loudly on a stone floor, then creaking as someone sits down on it, "
        "close-miked, immediate. No music, no voices.", 1.5),
    "ceiling_shimmer": (
        "A magical shimmering chime swell, loud glittering bells and glassy sparkle rising "
        "continuously, like stars appearing, dense throughout with no silence. No voices.", 4.0),
    "hat_brim": (
        "A soft heavy fabric whoosh and loud rustle of an old leather hat being lowered over a head, "
        "close-miked, immediate. No music, no voices.", 2.0),
    "whispers": (
        "A big hall of students whispering loudly in surprise, a dense continuous hiss of hundreds of "
        "whispers, close-miked, no silence throughout. No music.", 5.0),
}

# name -> (start, end, fade_in, fade_out) in seconds of the raw take, read off the envelope.
# None until read: master uses the whole take.
WINDOWS = {
    "door_groan": (0.0, 2.5, 0.0, 0.1),
    "door_boom": (0.0, 2.0, 0.0, 0.3),
    "murmur_a": (0.1, 20.1, 0.5, 0.5),      # beds: baked fades so two can crossfade
    "murmur_b": (0.6, 20.6, 0.5, 0.5),
    "hush": (0.0, 2.6, 0.0, 0.3),
    "cheer_big": (0.2, 4.9, 0.0, 0.4),
    "cheer_small": (0.45, 4.4, 0.03, 0.3),
    "steps_group": (0.0, 7.9, 0.0, 0.1),
    "steps_one": (0.35, 3.55, 0.0, 0.1),
    "stool": (0.08, 1.0, 0.0, 0.1),
    "ceiling_shimmer": (0.0, 3.0, 0.0, 0.5),
    "hat_brim": (0.3, 1.3, 0.0, 0.2),
    "whispers": (0.0, 4.7, 0.05, 0.3),
}

# Sparse footstep takes are all transients; matching their RMS to the set would clip the steps.
TARGETS = {"steps_group": -22.0, "steps_one": -22.0, "stool": -20.0, "hat_brim": -20.0}


def raw_path(name):
    return os.path.join(RAW_DIR, name + ".mp3")


def gen(names):
    with open(KEY_FILE) as f:
        key = f.read().strip()
    os.makedirs(RAW_DIR, exist_ok=True)
    for name in names:
        prompt, secs = SOUNDS[name]
        path = raw_path(name)
        if os.path.exists(path) and name not in FORCE:
            continue
        body = json.dumps({"text": prompt, "duration_seconds": secs, "prompt_influence": 0.45}).encode()
        req = urllib.request.Request(API, data=body, headers={"xi-api-key": key,
                                                              "Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=180) as r:
            data = r.read()
        with open(path, "wb") as f:
            f.write(data)
        print("fetched", name, len(data), "bytes")


def decode(path, start=0.0, dur=None, mono_mix=False):
    """Decode to float mono. mono_mix=True downmixes a stereo take as (L+R)/2 in numpy -- the
    same formula the pan filter uses when encoding -- since ffmpeg's own -ac 1 downmix of
    stereo and its handling of an already-mono file differ by ~3 dB."""
    if mono_mix:
        cmd = ["ffmpeg", "-v", "error", "-ss", str(start), "-i", path]
        if dur is not None:
            cmd += ["-t", str(dur)]
        cmd += ["-f", "s16le", "-ac", "2", "-ar", "44100", "-"]
        raw = subprocess.run(cmd, capture_output=True, check=True).stdout
        a = np.frombuffer(raw, dtype=np.int16).astype(np.float64) / 32768.0
        return a.reshape(-1, 2).mean(axis=1)
    cmd = ["ffmpeg", "-v", "error", "-ss", str(start), "-i", path]
    if dur is not None:
        cmd += ["-t", str(dur)]
    cmd += ["-f", "s16le", "-ac", "1", "-ar", "44100", "-"]
    raw = subprocess.run(cmd, capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.int16).astype(np.float64) / 32768.0


def db(x):
    return 20 * np.log10(max(x, 1e-9))


def rms_db(a):
    return db(float(np.sqrt(np.mean(a * a)))) if len(a) else -99.0


def envelope(name):
    a = decode(raw_path(name), mono_mix=True)
    w = 4410
    vals = [rms_db(a[i:i + w]) for i in range(0, len(a) - w + 1, w)]
    print("== %s  %.2fs  whole-take RMS %.1f dBFS" % (name, len(a) / 44100, rms_db(a)))
    line = ""
    for i, v in enumerate(vals):
        line += "%5.0f" % v
        if i % 10 == 9:
            print("  t=%4.1f: %s" % ((i - 9) / 10, line))
            line = ""
    if line:
        print("  t=%4.1f: %s" % ((len(vals) // 10 * 10) / 10, line))


def master(name):
    src = raw_path(name)
    total = len(decode(src, mono_mix=True)) / 44100
    start, end, fi, fo = WINDOWS.get(name, (0.0, total, 0.0, 0.0))
    dur = end - start
    seg = decode(src, start, dur, mono_mix=True)           # measuring pass: input seeking
    cur = rms_db(seg)
    gain = TARGETS.get(name, TARGET_RMS) - cur
    filt = "pan=mono|c0=0.5*c0+0.5*c1,volume=%.2fdB" % gain
    if fi > 0:
        filt += ",afade=t=in:st=0:d=%.3f" % fi
    if fo > 0:
        filt += ",afade=t=out:st=%.3f:d=%.3f" % (dur - fo, fo)
    filt += ",alimiter=level=disabled:limit=%.2f" % LIMIT
    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, "hall_%s.ogg" % name)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", str(start), "-i", src, "-t", str(dur),
                    "-af", filt, "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", out],
                   check=True)
    res = decode(out)
    print("%-16s window %.2f-%.2f (%.2fs)  gain %+.1f dB  out %.2fs  RMS %.1f dBFS  peak %.1f dBFS"
          % (name, start, end, dur, gain, len(res) / 44100, rms_db(res), db(float(np.max(np.abs(res))))))


def check():
    path = os.path.join(ASSETS, "sounds.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    bad = 0
    n = 0
    for event, body in data.items():
        for s in body.get("sounds", []):
            ref = s["name"] if isinstance(s, dict) else s
            ns, _, rel = ref.partition(":")
            if not rel:
                ns, rel = "minecraft", ref
            if ns != "hogwartsmagic":
                continue
            n += 1
            if not os.path.isfile(os.path.join(ASSETS, "sounds", rel + ".ogg")):
                print("MISSING", event, "->", ref)
                bad += 1
    print("%d entries checked, %d missing" % (n, bad))
    return bad


FORCE = set()

if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    names = sys.argv[2:] or list(SOUNDS)
    if cmd == "gen":
        FORCE = set(sys.argv[2:])
        gen(names)
    elif cmd == "env":
        for n in names:
            envelope(n)
    elif cmd == "master":
        for n in names:
            master(n)
    elif cmd == "check":
        sys.exit(1 if check() else 0)
    else:
        print(__doc__)
