"""Letter memory sound effects (ElevenLabs) -- generate, inspect, master.

Reads the key from C:\\Users\\alonl\\eleven_key.txt (never printed, never committed).
Raw MP3 takes go to a scratch directory outside the repo.

  python letter_sfx.py gen [names...]     fetch raw takes (skips existing; names force a refetch)
  python letter_sfx.py env [names...]     print the 100 ms RMS envelope of each raw take
  python letter_sfx.py master [names...]  trim, level-match, limit, write the .ogg
  python letter_sfx.py check              every sounds.json entry resolves to an .ogg

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
           r"\4f579a71-d4a1-442d-8a31-491678844c18\scratchpad\letter_audio")

TARGET_RMS = -18.0   # dBFS; the repo's sounds sit around -11 to -26
LIMIT = 0.65

# name -> (prompt, seconds). Reused existing sounds: none of OLLIVANDERS_* is copied; the scene may
# play OLLIVANDERS_DOOR / OLLIVANDERS_MEASURE / OLLIVANDERS_BOX directly where they fit.
SOUNDS = {
    "slot": (
        "A single letter dropping through a brass letterbox slot and landing on a doormat, a small "
        "metal flap clack then a soft paper thump, close-miked, immediate. No music, no voices.", 2.0),
    "hammer": (
        "Hammering nails into wood, five or six hard rapid hammer blows on planks, close-miked, "
        "loud, dry, starting immediately. No music, no voices.", 3.0),
    "owls": (
        "Many owls hooting outside at night, a dense chorus of overlapping hoots and wing flutters, "
        "loud and continuous throughout with no silence. No music, no voices.", 8.0),
    "chimney_rumble": (
        "A deep rumble and roar inside a brick chimney, soot falling, rushing wind and low rolling "
        "thunder-like vibration, loud and continuous from the first instant. No music, no voices.", 5.0),
    "whoosh": (
        "A dense rush of thousands of fluttering paper letters, loud rapid flapping and rustling "
        "paper whooshing past, continuous from the first instant, no silence. No music, no voices.", 4.0),
    "storm": (
        "A stormy sea at night, howling wind, heavy rain and big waves roaring, loud, dense and "
        "continuous throughout with no silence and no gaps, steady and loopable. No music, no voices.", 22.0),
    "waves": (
        "Ocean waves slapping and sloshing against the hull of a small wooden boat, creaking timber, "
        "water splashing, loud, close-miked, continuous throughout. No music, no voices.", 8.0),
    "thunder": (
        "A huge crack of thunder followed by a long deep rolling rumble, loud, immediate. "
        "No music, no voices.", 6.0),
    "pound": (
        "Huge fists pounding on a heavy wooden door, three enormous thunderous booming blows that "
        "shake the wood, loud, close-miked, immediate. No music, no voices.", 4.0),
    "door_crash": (
        "A heavy wooden door torn off its hinges with splintering wood and screeching nails, then "
        "slamming flat onto a stone floor with a huge crash, loud, immediate. No music, no voices.", 3.0),
    "fire": (
        "A fire crackling in a hearth, loud close-miked pops, snaps and hissing embers, dense and "
        "continuous throughout with no silence. No music, no voices.", 10.0),
}

# name -> (take, start, end, fade_in, fade_out) in seconds of the chosen raw take.
# Filled in after reading the envelopes; unlisted names use take 1 and the whole clip.
WINDOWS = {
    "slot": (2, 0.5, 1.4, 0.0, 0.25),
    "hammer": (1, 0.25, 2.9, 0.0, 0.2),
    "owls": (2, 0.8, 7.0, 0.1, 0.4),
    "chimney_rumble": (2, 0.3, 4.5, 0.1, 0.5),
    "whoosh": (1, 0.9, 2.8, 0.1, 0.4),
    "waves": (2, 0.5, 5.0, 0.2, 0.6),
    "thunder": (1, 0.0, 4.9, 0.02, 0.9),
    "pound": (1, 0.0, 2.4, 0.0, 0.3),
    "door_crash": (2, 0.0, 1.2, 0.0, 0.3),
    "fire": (1, 0.3, 9.2, 0.1, 0.4),
}

# Seamless loops: name -> (take, length, crossfade). The last `crossfade` seconds of source
# beyond `length` are blended (equal power) into the head, so the file's end runs into its start.
LOOPS = {"storm": (1, 20.0, 2.0)}

TARGETS = {}


def raw_path(name, take=1):
    return os.path.join(RAW_DIR, "%s_%d.mp3" % (name, take))


def gen(names):
    with open(KEY_FILE) as f:
        key = f.read().strip()
    os.makedirs(RAW_DIR, exist_ok=True)
    for name in names:
        prompt, secs = SOUNDS[name]
        for take in (1, 2):
            path = raw_path(name, take)
            if os.path.exists(path) and name not in FORCE:
                continue
            body = json.dumps({"text": prompt, "duration_seconds": secs, "prompt_influence": 0.45}).encode()
            req = urllib.request.Request(API, data=body, headers={"xi-api-key": key,
                                                                  "Content-Type": "application/json"})
            with urllib.request.urlopen(req, timeout=180) as r:
                data = r.read()
            with open(path, "wb") as f:
                f.write(data)
            print("fetched", name, take, len(data), "bytes")


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


def envelope(name, take):
    a = decode(raw_path(name, take), mono_mix=True)
    w = 4410
    vals = [rms_db(a[i:i + w]) for i in range(0, len(a) - w + 1, w)]
    print("== %s#%d  %.2fs  whole-take RMS %.1f dBFS" % (name, take, len(a) / 44100, rms_db(a)))
    line = ""
    for i, v in enumerate(vals):
        line += "%5.0f" % v
        if i % 10 == 9:
            print("  t=%4.1f: %s" % ((i - 9) / 10, line))
            line = ""
    if line:
        print("  t=%4.1f: %s" % ((len(vals) // 10 * 10) / 10, line))


def master_loop(name):
    take, length, xf = LOOPS[name]
    a = decode(raw_path(name, take), mono_mix=True)
    n, k = int(length * 44100), int(xf * 44100)
    out = a[:n].copy()
    ramp = np.linspace(0.0, np.pi / 2, k)
    out[:k] = a[:k] * np.sin(ramp) + a[n:n + k] * np.cos(ramp)
    gain = TARGETS.get(name, TARGET_RMS) - rms_db(out)
    pcm = (np.clip(out, -1, 1) * 32767).astype(np.int16).tobytes()
    dst = os.path.join(OUT_DIR, "letter_%s.ogg" % name)
    filt = "volume=%.2fdB,alimiter=level=disabled:limit=%.2f:latency=1" % (gain, LIMIT)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", "44100", "-ac", "1", "-i", "-",
                    "-af", filt, "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", dst],
                   input=pcm, check=True)
    res = decode(dst)
    _lens[name] = round(len(res) / 44100, 2)
    print("%-16s LOOP %.1fs xf %.1fs  gain %+.1f dB  out %.2fs  RMS %.1f dBFS  peak %.1f dBFS  seam jump %.1f dB"
          % (name, length, xf, gain, len(res) / 44100, rms_db(res), db(float(np.max(np.abs(res)))),
             rms_db(res[-4410:]) - rms_db(res[:4410])))


def master(name):
    if name in LOOPS:
        return master_loop(name)
    take0 = WINDOWS.get(name, (1,))[0]
    src = raw_path(name, take0)
    total = len(decode(src, mono_mix=True)) / 44100
    _, start, end, fi, fo = WINDOWS.get(name, (1, 0.0, total, 0.0, 0.0))
    dur = end - start
    seg = decode(src, start, dur, mono_mix=True)           # measuring pass: input seeking
    cur = rms_db(seg)
    gain = TARGETS.get(name, TARGET_RMS) - cur
    filt = "pan=mono|c0=0.5*c0+0.5*c1,volume=%.2fdB" % gain
    if fi > 0:
        filt += ",afade=t=in:st=0:d=%.3f" % fi
    if fo > 0:
        filt += ",afade=t=out:st=%.3f:d=%.3f" % (dur - fo, fo)
    filt += ",alimiter=level=disabled:limit=%.2f:latency=1" % LIMIT
    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, "letter_%s.ogg" % name)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", str(start), "-i", src, "-t", str(dur),
                    "-af", filt, "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", out],
                   check=True)
    res = decode(out)
    _lens[name] = round(len(res) / 44100, 2)
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
_lens = {}

if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    names = sys.argv[2:] or list(SOUNDS)
    if cmd == "gen":
        FORCE = set(sys.argv[2:])
        gen(names)
    elif cmd == "env":
        for n in names:
            for t in (1, 2):
                envelope(n, t)
    elif cmd == "master":
        for n in names:
            master(n)
        lp = os.path.join(os.path.dirname(os.path.abspath(__file__)), "letter_clip_lengths.json")
        cur = json.load(open(lp, encoding="utf-8")) if os.path.exists(lp) else {}
        cur.update({"letter_" + k: v for k, v in _lens.items()})
        with open(lp, "w", encoding="utf-8") as f:
            json.dump(dict(sorted(cur.items())), f, indent=2)
            f.write("\n")
    elif cmd == "check":
        sys.exit(1 if check() else 0)
    else:
        print(__doc__)
