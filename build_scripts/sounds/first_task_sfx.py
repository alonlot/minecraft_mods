"""First Task memory sound effects (ElevenLabs) -- generate, inspect, master, check.

Reads the key from C:\\Users\\alonl\\eleven_key.txt (never printed, never committed).
Raw MP3 takes go to RAW_DIR, a directory outside the repo that this script owns.

  python -I first_task_sfx.py gen [names...]     fetch raw takes (skips existing; names force a refetch)
  python -I first_task_sfx.py env [names...]     print the 100 ms RMS envelope of each raw take
  python -I first_task_sfx.py master [names...]  trim, level-match, limit, write the .ogg + clip lengths
  python -I first_task_sfx.py check              length, RMS, peak and loop seam step of every .ogg

Same mastering rules as flying_sfx.py: an explicit (start, end) window read off the envelope so the
transient opens the clip, RMS level-match, alimiter with latency=1 (otherwise it delays its output by
~4.4 ms and leaves a dropout at every loop seam), ffmpeg INPUT seeking (-ss before -i) in the
measuring and the encoding pass alike. Loops are crossfaded (equal power) so the end runs into the start.

WINDOWS holds the measured cut points (seconds of the chosen raw take); its notes say what each
envelope showed. Files are mono 44.1 kHz Vorbis in sounds/pensieve/sfx/first_task_<name>.ogg.
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
RAW_DIR = r"C:\Users\alonl\AppData\Local\Temp\hogwartsmagic_first_task_sfx_raw"
LENGTHS_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "first_task_clip_lengths.json")
PREFIX = "first_task_"

TARGET_RMS = -18.0   # dBFS as decode() measures it; the repo's sounds sit around -11 to -26
LIMIT = 0.65

NOVOICE = " No music, no voices."

# name -> (prompt, seconds to request, takes, target length s). Requests carry slack over the length.
SOUNDS = {
    "tent_crowd": (
        "A huge stadium crowd roaring and stomping outside a big canvas tent, heard muffled through "
        "heavy canvas, a deep dull wall of distant cheering with thumping feet, loud, dense and "
        "continuous throughout with no silence and no gaps, steady and loopable. No music, no clear words.",
        22.0, 3, 20.0),
    "ground_thud": (
        "A colossal creature stamping its foot, a massive ground-shaking boom with a deep sub-bass "
        "rumble, gravel and rocks rattling and falling, cinematic, huge, immediate." + NOVOICE,
        3.0, 3, 2.0),
    "cannon": (
        "A single huge cannon boom fired once, a massive concussive blast with a long echo rolling "
        "off distant mountains, cinematic, immediate." + NOVOICE, 4.0, 3, 3.0),
    "crowd_bed": (
        "A packed stadium crowd in bright daylight, tens of thousands of people cheering excitedly "
        "with a roar of chatter and whistles, a dense wall of noise, dry weather, loud, dense and "
        "continuous throughout with no silence and no gaps, steady and loopable. No music, no clear words.",
        22.0, 3, 20.0),
    "crowd_hush": (
        "A roaring stadium crowd suddenly falling silent in shock, the cheering cuts away into a "
        "stunned hush with a few gasps and distant murmurs, immediate. No music, no clear words.",
        4.0, 3, 3.0),
    "horntail_roar": (
        "An enormous dragon's furious roar, a deep rasping bellowing scream with a thunderous "
        "growling rumble, huge, monstrous and cinematic, loud, immediate, sustained throughout, "
        "with a long echo." + NOVOICE, 4.5, 5, 3.5),
    "horntail_snap": (
        "A dragon's huge jaws snapping shut with a wet snarl, a heavy crunching bite and a "
        "guttural growl, loud, close, immediate." + NOVOICE, 2.0, 3, 1.2),
    "wingbeat": (
        "One single enormous leathery wingbeat of a giant dragon, a deep powerful whump of moving "
        "air with a leathery flap, loud, immediate." + NOVOICE, 1.5, 3, 1.0),
    "fire_roar": (
        "A dragon breathing a huge roaring jet of fire, a thunderous rushing blowtorch roar of "
        "flame with a deep growl, enormous, cinematic, loud, dense, immediate and sustained "
        "throughout." + NOVOICE, 3.5, 5, 2.5),
    "fire_scorch": (
        "A blast of flame hitting a stone wall, roaring fire washing over rock, hissing, popping "
        "and crackling loudly, dense and cinematic, immediate." + NOVOICE, 3.0, 3, 2.0),
    "chain_snap": (
        "A massive iron chain snapping under tension, a loud metallic crack and clang with heavy "
        "links whipping and clattering across stone, immediate." + NOVOICE, 2.5, 3, 1.5),
    "slate_smash": (
        "Many stone roof slates smashing and sliding off a roof, a loud clattering crash of "
        "breaking tiles and shards tumbling down, dense, immediate." + NOVOICE, 3.0, 3, 2.0),
    "bridge_crash": (
        "A giant stone bridge smashed apart, a thunderous collapse of masonry, huge blocks "
        "crashing and grinding, a deep boom followed by rubble tumbling and dust settling, "
        "enormous, cinematic, loud, immediate." + NOVOICE, 5.0, 5, 4.0),
    "accio": (
        "A magical summoning spell, a quick shimmering swish rising into a loud rushing whoosh "
        "with a bright sparkle, fantasy sound effect, immediate." + NOVOICE, 2.0, 3, 1.2),
    "wind_high": (
        "A fast strong wind rushing past at great height, a loud steady roar of rushing air with "
        "whistling gusts, loud, dense and continuous throughout with no silence and no gaps, "
        "steady and loopable." + NOVOICE, 22.0, 3, 20.0),
    "egg_grab": (
        "A hand snatching and closing around a heavy metal egg, a quick grip with a bright gold "
        "metallic clink, close-miked, immediate." + NOVOICE, 1.0, 3, 0.5),
}

# name -> (take, start, end, fade_in, fade_out) in seconds of the chosen raw take.
# Unlisted one-shots use take 1 and the whole clip.
WINDOWS = {
    # name           take  start  end    fade in/out   note
    "ground_thud":   (3, 0.00, 2.00, 0.0, 0.5),    # -10 dB hit at 0, rattle tail to -50 by 2.0
    "cannon":        (1, 0.00, 3.00, 0.0, 0.8),    # -3 dB boom at 0, echo -15 at 1.0, -35 at 2.0
    "crowd_hush":    (3, 0.20, 3.60, 0.02, 0.5),   # roar -9 at 0.2 falling to -46 by 3.6
    "horntail_roar": (5, 0.05, 3.55, 0.03, 0.7),   # 0.25 s attack, flat -6 to 2.5 s, then rasp tail
    "horntail_snap": (2, 0.05, 1.25, 0.0, 0.3),    # bite at 0.1 (-8), snarl to 0.4
    "wingbeat":      (3, 0.00, 1.00, 0.0, 0.3),    # one whump (takes 1 and 2 flap twice)
    "fire_roar":     (4, 0.00, 2.90, 0.0, 0.3),    # 0.3 s ramp, flat -4 to 2.5 s, natural decay
    "fire_scorch":   (2, 0.05, 2.05, 0.0, 0.5),    # blast at 0.1 (-3), hiss decays -36 by 2.0
    "chain_snap":    (3, 0.05, 1.55, 0.0, 0.25),   # crack 0.1-0.3, links 0.6-0.8 and 1.3
    "slate_smash":   (1, 0.00, 2.00, 0.0, 0.5),    # first crash at 0 (-11), second at 0.8
    "bridge_crash":  (5, 0.00, 4.00, 0.0, 1.0),    # boom at 0, rubble tumbling -37 at 3 s
    "accio":         (1, 0.00, 1.20, 0.0, 0.3),    # swish rising from -28 to -9 at 0.2 s
    "egg_grab":      (3, 0.07, 0.57, 0.0, 0.15),   # grip then clink at 0.1 (-15), ring-off
}

# Seamless loops: name -> (take, start, length, crossfade). The `crossfade` seconds of source
# beyond start+length are blended (equal power) into the head, so the file's end runs into its start.
LOOPS = {
    "tent_crowd": (2, 1.0, 17.8, 2.0),    # drifts -12 to -27; flatten() levels it
    "crowd_bed":  (2, 0.3, 19.0, 2.0),    # flat -10 all take
    "wind_high":  (2, 1.8, 18.0, 2.0),    # gusty -9 to -16
}

# Per-sound compression applied before the level match.
COMPRESS = {
    # sparse transients over a thin body: raise the body so the RMS match is not all peak
    "chain_snap":  "acompressor=threshold=0.03:ratio=8:attack=2:release=120:makeup=6",
    "slate_smash": "acompressor=threshold=0.03:ratio=8:attack=3:release=150:makeup=5",
    "egg_grab":    "acompressor=threshold=0.03:ratio=8:attack=1:release=80:makeup=4",
}

# Pitched-down copy mixed under the window: name -> (speed factor, mix dB re the dry RMS). Gives
# the roars, the fire and the crash a bigger body than the raw take has.
LAYERS = {
    "horntail_roar": (0.5, -1.0),
    "fire_roar":     (0.55, -3.0),
    "bridge_crash":  (0.5, -2.0),
    "ground_thud":   (0.65, -2.0),
    "cannon":        (0.70, -4.0),
}

# Per-sound RMS targets (dBFS, decode() units). Beds sit under the scene's own sounds.
TARGETS = {
    "horntail_roar": -14.0, "fire_roar": -14.0, "fire_scorch": -16.0, "bridge_crash": -15.0,
    "cannon": -15.0, "ground_thud": -16.0, "horntail_snap": -16.0,
}


def raw_path(name, take=1):
    return os.path.join(RAW_DIR, "%s_%d.mp3" % (name, take))


def gen(names):
    with open(KEY_FILE) as f:
        key = f.read().strip()
    os.makedirs(RAW_DIR, exist_ok=True)
    for name in names:
        prompt, secs, takes, _ = SOUNDS[name]
        for take in range(1, takes + 1):
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
    """Decode to float mono as (L+R)/2. Always asks ffmpeg for stereo (a mono source is upmixed by
    duplication) -- the same formula the encode pass applies. A mono file therefore decodes 3 dB low
    here; the match is consistent and TARGETS are in these units (true level = printed + 3 dB)."""
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
    print("== %s#%d  %.2fs  whole-take RMS %.1f dBFS  peak %.1f" % (name, take, len(a) / 44100, rms_db(a),
                                                                  db(float(np.max(np.abs(a))))))
    line = ""
    for i, v in enumerate(vals):
        line += "%5.0f" % v
        if i % 10 == 9:
            print("  t=%4.1f: %s" % ((i - 9) / 10, line))
            line = ""
    if line:
        print("  t=%4.1f: %s" % ((len(vals) // 10 * 10) / 10, line))


SEAM_SCAN = 0.25   # s of loop length the seam search may use either side of the nominal


def flatten(a, win=4.0, lo=-6.0, hi=10.0):
    """Remove slow level drift from a loop source: gain = (mean level - smoothed level), clipped.
    Fast gusts and crowd swells survive; the head and tail of the loop end up at the same level."""
    blk = 11025                                   # 0.25 s blocks
    nb = len(a) // blk
    env = np.array([rms_db(a[i * blk:(i + 1) * blk]) for i in range(nb)])
    k = max(1, int(win * 44100 / blk))
    pad = np.concatenate([np.full(k // 2, env[0]), env, np.full(k // 2, env[-1])])
    sm = np.convolve(pad, np.ones(k) / k, mode="valid")[:nb]
    g = np.clip(np.mean(env) - sm, lo, hi)
    t_blk = (np.arange(nb) + 0.5) * blk
    gain_db = np.interp(np.arange(len(a)), t_blk, g)
    return a * (10 ** (gain_db / 20))


def encode_pcm(pcm_f, dst, filt):
    pcm = (np.clip(pcm_f, -1, 1) * 32767).astype(np.int16).tobytes()
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", "44100", "-ac", "1", "-i", "-",
                    "-af", filt, "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", dst],
                   input=pcm, check=True)


def master_loop(name):
    take, start, length, xf = LOOPS[name]
    a = decode(raw_path(name, take))[int(start * 44100):]
    n, k = int(length * 44100), int(xf * 44100)
    a = flatten(a[:n + int(SEAM_SCAN * 44100) + k])
    # The loop's wrap is out[-1]=a[n-1] into out[0]=a[n], so the seam step is the source's own
    # sample step at n; pick the length (within SEAM_SCAN) where that step is smallest.
    lo_n = n - int(SEAM_SCAN * 44100)
    n = lo_n + int(np.argmin(np.abs(a[lo_n + 1:n + int(SEAM_SCAN * 44100)] - a[lo_n:n + int(SEAM_SCAN * 44100) - 1]))) + 1
    length = n / 44100
    out = a[:n].copy()
    ramp = np.linspace(0.0, np.pi / 2, k)
    out[:k] = a[:k] * np.sin(ramp) + a[n:n + k] * np.cos(ramp)
    gain = TARGETS.get(name, TARGET_RMS) - rms_db(out)
    os.makedirs(OUT_DIR, exist_ok=True)
    dst = os.path.join(OUT_DIR, PREFIX + "%s.ogg" % name)
    encode_pcm(out, dst, "volume=%.2fdB,alimiter=level=disabled:limit=%.2f:latency=1" % (gain, LIMIT))
    res = decode(dst)
    _lens[name] = round(len(res) / 44100, 2)
    print("%-16s LOOP %.1fs xf %.1fs  gain %+.1f dB  out %.2fs  RMS %.1f dBFS  peak %.1f dBFS  seam %.4f"
          % (name, length, xf, gain, len(res) / 44100, rms_db(res), db(float(np.max(np.abs(res)))),
             abs(float(res[0] - res[-1]))))


def master(name):
    if name in LOOPS:
        return master_loop(name)
    take0 = WINDOWS.get(name, (1,))[0]
    src = raw_path(name, take0)
    total = len(decode(src)) / 44100
    _, start, end, fi, fo = WINDOWS.get(name, (1, 0.0, total, 0.0, 0.0))
    dur = end - start
    comp = COMPRESS.get(name)
    layer = LAYERS.get(name)
    orig_start = start
    if comp or layer:
        # Layer a pitched-down copy (a bigger body), compress, then measure and encode from the result.
        os.makedirs(RAW_DIR, exist_ok=True)
        seg = decode(src, start, dur)
        if layer:
            f, mix_db = layer
            idx = np.arange(len(seg)) * f
            low = np.interp(idx, np.arange(len(seg)), seg)
            seg = seg + low * (10 ** (mix_db / 20)) * (np.sqrt(np.mean(seg ** 2)) / max(np.sqrt(np.mean(low ** 2)), 1e-9))
        tmp = os.path.join(RAW_DIR, "%s_comp.wav" % name)
        pcm = (np.clip(seg / max(1.0, float(np.max(np.abs(seg)))), -1, 1) * 32767).astype(np.int16).tobytes()
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", "44100", "-ac", "1", "-i", "-",
                        "-af", comp or "anull", "-ac", "1", "-ar", "44100", tmp], input=pcm, check=True)
        src, start = tmp, 0.0
        seg = decode(src, 0.0, dur)
    else:
        seg = decode(src, start, dur)                      # measuring pass: input seeking
    gain = TARGETS.get(name, TARGET_RMS) - rms_db(seg)
    filt = "aformat=channel_layouts=stereo,pan=mono|c0=0.5*c0+0.5*c1,volume=%.2fdB" % gain
    if fi > 0:
        filt += ",afade=t=in:st=0:d=%.3f" % fi
    if fo > 0:
        filt += ",afade=t=out:st=%.3f:d=%.3f" % (dur - fo, fo)
    filt += ",alimiter=level=disabled:limit=%.2f:latency=1" % LIMIT
    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, PREFIX + "%s.ogg" % name)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", str(start), "-i", src, "-t", str(dur),
                    "-af", filt, "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", out],
                   check=True)
    res = decode(out)
    _lens[name] = round(len(res) / 44100, 2)
    print("%-16s window %.2f-%.2f (%.2fs)  gain %+.1f dB  out %.2fs  RMS %.1f dBFS  peak %.1f dBFS  first>-20dB at %.3fs"
          % (name, orig_start, end, dur, gain, len(res) / 44100, rms_db(res), db(float(np.max(np.abs(res)))),
             float(np.argmax(np.abs(res) > 0.1 * np.max(np.abs(res))) / 44100)))


def check():
    """Every file: length against the brief (+-20%), RMS, peak (<= 0.65), loop seam step (<= 0.02)."""
    bad = 0
    print("%-16s %7s %7s %9s %8s %s" % ("name", "len s", "brief", "RMS dBFS", "peak", "seam step"))
    for name, (_, _, _, brief) in SOUNDS.items():
        path = os.path.join(OUT_DIR, PREFIX + "%s.ogg" % name)
        if not os.path.isfile(path):
            print("%-16s MISSING" % name)
            bad += 1
            continue
        a = decode(path)
        length = len(a) / 44100
        peak = float(np.max(np.abs(a)))
        seam = ""
        flags = []
        if abs(length - brief) > 0.2 * brief:
            flags.append("LENGTH")
        if peak > LIMIT + 0.005:
            flags.append("PEAK")
        if name in LOOPS:
            step = abs(float(a[0] - a[-1]))
            seam = "%.4f" % step
            if step > 0.02:
                flags.append("SEAM")
            # head/tail 0.5 s level step and the quietest 0.25 s block (a dropout shows here)
            ht = rms_db(a[:22050]) - rms_db(a[-22050:])
            blocks = [rms_db(a[i:i + 11025]) for i in range(0, len(a) - 11025, 11025)]
            seam += "  head-tail %+.1f dB  quietest block %.1f dB under mean" % (ht, rms_db(a) - min(blocks))
        if flags:
            bad += 1
        print("%-16s %7.2f %7.1f %9.1f %8.3f %s %s" % (name, length, brief, rms_db(a), peak, seam,
                                                         " ".join(flags)))
    print("%d files, %d problems" % (len(SOUNDS), bad))
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
            for t in range(1, SOUNDS[n][2] + 1):
                envelope(n, t)
    elif cmd == "master":
        for n in names:
            master(n)
        cur = json.load(open(LENGTHS_FILE, encoding="utf-8")) if os.path.exists(LENGTHS_FILE) else {}
        cur.update({PREFIX + k: v for k, v in _lens.items()})
        with open(LENGTHS_FILE, "w", encoding="utf-8") as f:
            json.dump(dict(sorted(cur.items())), f, indent=2)
            f.write("\n")
    elif cmd == "check":
        sys.exit(1 if check() else 0)
    else:
        print(__doc__)
