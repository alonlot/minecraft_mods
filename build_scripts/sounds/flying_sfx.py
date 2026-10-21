"""Flying memory sound effects (ElevenLabs) -- generate, inspect, master.

Reads the key from C:\\Users\\alonl\\eleven_key.txt (never printed, never committed).
Raw MP3 takes go to RAW_DIR, a directory outside the repo that this script owns.

  python flying_sfx.py gen [names...]     fetch raw takes (skips existing; names force a refetch)
  python flying_sfx.py env [names...]     print the 100 ms RMS envelope of each raw take
  python flying_sfx.py master [names...]  trim, level-match, limit, write the .ogg + clip lengths
  python flying_sfx.py check              every sounds.json entry resolves; every subtitle key exists

Mastering follows the ElevenLabs note: explicit (start, end) window read off the envelope,
RMS level-match, alimiter, and ffmpeg INPUT seeking (-ss before -i) in the measuring and the
encoding pass alike. Loops are crossfaded (equal power) so the file's end runs into its start.

WINDOWS: measured cut points, read off the 100 ms envelopes (seconds of the chosen raw take)

  name           take  start   end    fade in/out   note
  broom_up         1   0.28    0.88   0 / 0.20      spike at 0.30; compressed
  broom_whoosh     1   0.55    1.75   0.10 / 0.30   rush 0.6-1.4
  broom_buck       1   0.20    2.20   0.02 / 0.25   continuous rattle until 2.2
  thud             1   0.00    0.80   0 / 0.20      thump in the first 0.1 s
  whistle          1   0.05    1.55   0.02 / 0.25   steady blast 0.1-1.1, then decay
  crate            1   0.28    1.78   0 / 0.30      lid bang at 0.3, chains after
  crowd_swell      2   0.00    4.00   0 / 0.80      rises from -20 to -9 by 0.7 s
  crowd_erupt      2   0.00    8.00   0 / 1.00      dense -4..-8 first 5 s; compressed flat to a sustained roar
  crowd_chant      2   0.00    8.00   0 / 0.80      steady rhythmic -8..-15
  crowd_gasp       2   0.10    2.10   0 / 0.50      gasp opens at 0.1
  thunder          1   0.10    5.10   0.02 / 1.50   crack at 0.4, roll to 5
  bludger_crack    1   0.00    0.60   0 / 0.15      crack at 0
  quaffle_catch    2   0.15    0.55   0 / 0.12      hit at 0.16 (pre-echo from 0.13); compressed

LOOPS (take, start, length, crossfade): lawn_air 1/0.9/18.5/1.9 (start chosen by scan so the head and tail levels match), crowd_bed 1/0/20/2,
rain 2/0.3/19/2, snitch_flutter 1/1.3/2/0.5. The limiter runs with latency=1: without it
alimiter delays its output by its attack time (~4.4 ms of silence at the head, the same cut
off the tail), which puts a dropout at every loop seam.
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
RAW_DIR = r"C:\Users\alonl\AppData\Local\Temp\hogwartsmagic_flying_sfx_raw"
LENGTHS_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "flying_clip_lengths.json")

TARGET_RMS = -18.0   # dBFS; the repo's sounds sit around -11 to -26
LIMIT = 0.65

NOVOICE = " No music, no voices."

# name -> (prompt, seconds to request). Requests carry a little slack over the brief's length.
SOUNDS = {
    "lawn_air": (
        "A breezy sunny afternoon on a school lawn, steady wind moving through long grass, leaves "
        "rustling, distant birds singing, loud and continuous throughout with no silence and no "
        "gaps, steady and loopable." + NOVOICE, 22.0),
    "broom_up": (
        "A wooden broom handle smacking up into an open palm, a sharp leathery slap with a short "
        "whoosh, close-miked, loud, immediate." + NOVOICE, 1.5),
    "broom_whoosh": (
        "A broom tearing past a listener at speed, a loud fast whoosh of rushing air with twigs "
        "rustling, close-miked, dense, immediate." + NOVOICE, 2.5),
    "broom_buck": (
        "A wooden broom rattling and bucking violently, shaking wood, creaking handle and "
        "rattling twigs, loud, dense and continuous from the first instant." + NOVOICE, 3.0),
    "thud": (
        "A boy landing hard on grass turf, a heavy body thump with a grunt-free dull impact and "
        "rustling grass, close-miked, loud, immediate. No voices, no music.", 1.5),
    "whistle": (
        "A referee's metal whistle, one long loud piercing blast, close-miked, immediate, "
        "sustained throughout." + NOVOICE, 2.5),
    "crate": (
        "A heavy wooden crate's lid thrown open with a bang, chains rattling and clanking loudly, "
        "wood creaking, close-miked, loud, immediate." + NOVOICE, 2.5),
    "crowd_bed": (
        "A packed stadium crowd roaring continuously under heavy rain, tens of thousands of "
        "people cheering and shouting as a dense wall of noise with rain hissing, loud, dense "
        "and continuous throughout with no silence and no gaps, steady and loopable. No music, "
        "no clear words.", 22.0),
    "crowd_swell": (
        "A packed stadium crowd rising from a murmur into a loud excited cheer, building "
        "quickly, loud and dense, starting immediately. No music, no clear words.", 5.0),
    "crowd_erupt": (
        "A packed stadium erupting into a huge victory roar, tens of thousands of people "
        "screaming and cheering wildly, a deafening wall of celebration, loud, dense and "
        "continuous throughout, immediate. No music, no clear words.", 10.0),
    "crowd_chant": (
        "A packed stadium crowd chanting rhythmically in unison with stomping and clapping, a "
        "driving beat, loud, dense and continuous throughout with no silence. "
        "No music, no clear words.", 10.0),
    "crowd_gasp": (
        "A packed stadium crowd gasping together in shock, a sudden loud collective intake of "
        "breath with a few cries, immediate. No music, no clear words.", 3.0),
    "rain": (
        "Heavy driving rain hammering down with strong gusting wind, loud, dense and continuous "
        "throughout with no silence and no gaps, steady and loopable." + NOVOICE, 22.0),
    "thunder": (
        "A close, enormous thunderclap cracking overhead then rolling away in a long deep "
        "rumble, loud, immediate." + NOVOICE, 7.0),
    "bludger_crack": (
        "A wooden bat cracking hard against a heavy iron ball, a loud sharp wooden crack with a "
        "metallic clang, close-miked, immediate." + NOVOICE, 1.5),
    "quaffle_catch": (
        "A leather ball slapped hard into gloved hands, a loud dull leathery smack, close-miked, "
        "immediate." + NOVOICE, 1.0),
    "snitch_flutter": (
        "Tiny metallic wings buzzing and fluttering rapidly, a bright insect-like whirring "
        "chatter of small metal wings, loud, dense and continuous throughout with no silence, "
        "steady and loopable." + NOVOICE, 4.0),
}

# name -> (take, start, end, fade_in, fade_out) in seconds of the chosen raw take.
# Filled in after reading the envelopes; unlisted names use take 1 and the whole clip.
WINDOWS = {
    "broom_up": (1, 0.28, 0.88, 0.0, 0.2),
    "broom_whoosh": (1, 0.55, 1.75, 0.1, 0.3),
    "broom_buck": (1, 0.2, 2.2, 0.02, 0.25),
    "thud": (1, 0.0, 0.8, 0.0, 0.2),
    "whistle": (1, 0.05, 1.55, 0.02, 0.25),
    "crate": (1, 0.28, 1.78, 0.0, 0.3),
    "crowd_swell": (2, 0.0, 4.0, 0.0, 0.8),
    "crowd_erupt": (2, 0.0, 8.0, 0.0, 1.0),
    "crowd_chant": (2, 0.0, 8.0, 0.0, 0.8),
    "crowd_gasp": (2, 0.1, 2.1, 0.0, 0.5),
    "thunder": (1, 0.1, 5.1, 0.02, 1.5),
    "bludger_crack": (1, 0.0, 0.6, 0.0, 0.15),
    "quaffle_catch": (2, 0.15, 0.55, 0.0, 0.12),
}

# Seamless loops: name -> (take, start, length, crossfade). The `crossfade` seconds of source
# beyond start+length are blended (equal power) into the head, so the file's end runs into its
# start. The source tops out at 22 s, so lawn_air (quiet first 1.5 s) and rain are a little
# under the brief's 20 s; the real lengths are in flying_clip_lengths.json.
LOOPS = {
    "lawn_air": (1, 0.9, 18.5, 1.9),
    "crowd_bed": (1, 0.0, 20.0, 2.0),
    "rain": (2, 0.3, 19.0, 2.0),
    "snitch_flutter": (1, 1.3, 2.0, 0.5),
}

# Per-sound RMS overrides (dBFS). Crowd layers and beds sit under the rain and wind; the
# scene mixes the rest with the playSound volume argument.
# Per-sound compression applied before the level match (turns a spike over a thin body into a
# solid hit, or a roar that decays into a sustained one).
COMPRESS = {
    "broom_up": "acompressor=threshold=0.05:ratio=4:attack=2:release=80:makeup=2",
    "quaffle_catch": "acompressor=threshold=0.02:ratio=12:attack=1:release=60:makeup=4",
    "crowd_erupt": "acompressor=threshold=0.03:ratio=20:attack=20:release=300:makeup=6",
}

TARGETS = {
    "crowd_erupt": -14.0,
    "crowd_swell": -16.0,
    "lawn_air": -24.0,
    "crowd_bed": -22.0,
    "rain": -22.0,
}


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


def decode(path, start=0.0, dur=None):
    """Decode to float mono as (L+R)/2 in numpy. Always asks ffmpeg for stereo (a mono source is
    upmixed by duplication, so (L+R)/2 is the source itself) -- the same formula the encode
    pass applies, since ffmpeg's own -ac 1 downmix differs from it by ~3 dB.
    Note: a MONO file decodes 3 dB low here (the upmix puts 0.707 in each channel), so the printed
    RMS of a finished .ogg is 3 dB below its true level; the match is consistent, TARGETS are in
    these units, and the true levels are the printed ones + 3 dB."""
    cmd = ["ffmpeg", "-v", "error", "-ss", str(start), "-i", path]
    if dur is not None:
        cmd += ["-t", str(dur)]
    cmd += ["-f", "s16le", "-ac", "2", "-ar", "44100", "-"]
    raw = subprocess.run(cmd, capture_output=True, check=True).stdout
    a = np.frombuffer(raw, dtype=np.int16).astype(np.float64) / 32768.0
    return a.reshape(-1, 2).mean(axis=1)


def db(x):
    return 20 * np.log10(max(x, 1e-9))


def rms_db(a):
    return db(float(np.sqrt(np.mean(a * a)))) if len(a) else -99.0


def envelope(name, take):
    if not os.path.exists(raw_path(name, take)):
        return
    a = decode(raw_path(name, take))
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
    take, start, length, xf = LOOPS[name]
    a = decode(raw_path(name, take))[int(start * 44100):]
    n, k = int(length * 44100), int(xf * 44100)
    out = a[:n].copy()
    ramp = np.linspace(0.0, np.pi / 2, k)
    out[:k] = a[:k] * np.sin(ramp) + a[n:n + k] * np.cos(ramp)
    gain = TARGETS.get(name, TARGET_RMS) - rms_db(out)
    pcm = (np.clip(out, -1, 1) * 32767).astype(np.int16).tobytes()
    os.makedirs(OUT_DIR, exist_ok=True)
    dst = os.path.join(OUT_DIR, "flying_%s.ogg" % name)
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
    total = len(decode(src)) / 44100
    _, start, end, fi, fo = WINDOWS.get(name, (1, 0.0, total, 0.0, 0.0))
    dur = end - start
    comp = COMPRESS.get(name)
    orig_start = start
    if comp:
        # Compress first (to a scratch wav), then measure and encode from that.
        os.makedirs(RAW_DIR, exist_ok=True)
        tmp = os.path.join(RAW_DIR, "%s_comp.wav" % name)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", str(start), "-i", src, "-t", str(dur), "-af",
                        "aformat=channel_layouts=stereo,pan=mono|c0=0.5*c0+0.5*c1," + comp,
                        "-ac", "1", "-ar", "44100", tmp], check=True)
        src, start = tmp, 0.0
        seg = decode(src, 0.0, dur)
    else:
        seg = decode(src, start, dur)                      # measuring pass: input seeking
    cur = rms_db(seg)
    gain = TARGETS.get(name, TARGET_RMS) - cur
    # aformat upmixes a mono source to stereo so the pan filter below always has c1.
    filt = "aformat=channel_layouts=stereo,pan=mono|c0=0.5*c0+0.5*c1,volume=%.2fdB" % gain
    if fi > 0:
        filt += ",afade=t=in:st=0:d=%.3f" % fi
    if fo > 0:
        filt += ",afade=t=out:st=%.3f:d=%.3f" % (dur - fo, fo)
    filt += ",alimiter=level=disabled:limit=%.2f:latency=1" % LIMIT
    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, "flying_%s.ogg" % name)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", str(start), "-i", src, "-t", str(dur),
                    "-af", filt, "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", out],
                   check=True)
    res = decode(out)
    _lens[name] = round(len(res) / 44100, 2)
    print("%-16s window %.2f-%.2f (%.2fs)  gain %+.1f dB  out %.2fs  RMS %.1f dBFS  peak %.1f dBFS"
          % (name, orig_start, end, dur, gain, len(res) / 44100, rms_db(res), db(float(np.max(np.abs(res))))))


def check():
    path = os.path.join(ASSETS, "sounds.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    with open(os.path.join(ASSETS, "lang", "en_us.json"), encoding="utf-8") as f:
        lang = json.load(f)
    bad = 0
    n = 0
    for event, body in data.items():
        sub = body.get("subtitle")
        if sub and sub not in lang:
            print("NO SUBTITLE KEY", event, "->", sub)
            bad += 1
        for s in body.get("sounds", []):
            ref = s["name"] if isinstance(s, dict) else s
            if isinstance(s, dict) and s.get("type") == "event":
                # An event entry points at another sound event, not a file.
                ns, _, rel = ref.partition(":")
                if ns == "hogwartsmagic" and rel not in data:
                    print("MISSING EVENT", event, "->", ref)
                    bad += 1
                n += 1
                continue
            ns, _, rel = ref.partition(":")
            if not rel:
                ns, rel = "minecraft", ref
            if ns != "hogwartsmagic":
                continue
            n += 1
            if not os.path.isfile(os.path.join(ASSETS, "sounds", rel + ".ogg")):
                print("MISSING", event, "->", ref)
                bad += 1
    print("%d entries checked, %d problems" % (n, bad))
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
        cur = json.load(open(LENGTHS_FILE, encoding="utf-8")) if os.path.exists(LENGTHS_FILE) else {}
        cur.update({"flying_" + k: v for k, v in _lens.items()})
        with open(LENGTHS_FILE, "w", encoding="utf-8") as f:
            json.dump(dict(sorted(cur.items())), f, indent=2)
            f.write("\n")
    elif cmd == "check":
        sys.exit(1 if check() else 0)
    else:
        print(__doc__)
