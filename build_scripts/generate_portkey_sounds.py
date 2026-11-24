"""Generate and master the Portkey's eight sounds through the ElevenLabs SFX API.

    python build_scripts/generate_portkey_sounds.py gen [--takes N] [--first K] [names...]  # fetch takes K..K+N-1
    python build_scripts/generate_portkey_sounds.py env [names...]               # envelope + body of every take
    python build_scripts/generate_portkey_sounds.py master [names...]            # cut, level, limit -> OGG

The set is one piece: the charge hum rises into the hook's yank, the hook throws you into the wind of the tunnel,
the wind lets go into the landing thump. Bystanders hear the two pops.

The API key is read from C:\\Users\\alonl\\eleven_key.txt at run time (never committed, never printed). Raw takes are
not shippable: `env` prints each take's 100 ms RMS envelope, its peak and its low-band body (how much of it is below
250 Hz: a thin take reads -20 dB or lower), with the pick rule's verdict; the chosen (take, start, end) goes into
WINDOWS with the numbers that decided it. `master` cuts each window with input seeking (-ss before -i, in both the
measure and the encode pass), fades it in over 15 ms, matches it to its target RMS, caps the peaks with a limiter
and writes mono 44.1 kHz Vorbis q5. The transit wind is crossfaded onto itself so it loops without a seam.
"""

import json
import math
import os
import statistics
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.request

KEY_FILE = r"C:\Users\alonl\eleven_key.txt"
API = "https://api.elevenlabs.io/v1/sound-generation"

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "sounds", "portkey")
TMP_DIR = os.environ.get("PORTKEY_SFX_TMP") or os.path.join(os.environ.get("TEMP", "."), "portkey_sfx")

TAIL = ", close-miked, loud, no silence, no music, no voice"

# name -> (prompt, seconds, takes)
PROMPTS = {
    "charge": ("A magical charge building up: a low resonant hum rising steadily in pitch and intensity, glassy "
               "shimmering overtones, air being drawn hard into one point, swelling to a tense peak" + TAIL, 4.0, 3),
    "hook": ("A violent magical yank: a sharp sucking whoosh snapping into a deep punchy thump and a tight cloth-snap "
             "crack, like being jerked off your feet by a hook" + TAIL, 1.5, 3),
    # Two more hook takes with "intense" (takes 4-5): the hook is the loudest beat of the set and gets the most choice.
    "hook_intense": ("A violent magical yank, intense: a sharp sucking whoosh snapping into a deep punchy thump and a "
                     "tight cloth-snap crack, like being jerked off your feet by a hook" + TAIL, 1.5, 2),
    "transit": ("Roaring wind tunnel: a howling vortex of rushing air spinning around the listener, deep rumble, "
                "whipping fluttering gusts, high whistling swirl, continuous and intense" + TAIL, 6.0, 2),
    "land": ("A body slamming onto the ground: a heavy thump on earth, a short skid scraping through grass and "
             "gravel, cloth rustle" + TAIL, 1.5, 3),
    "pop_depart": ("A magical implosion: air sucked inward into a point with a short rising whirl, ending in a sharp "
                   "hollow pop" + TAIL, 1.2, 2),
    # Takes 1-3 of the plain prompt were a single 10 ms click over a thin hiss: re-prompted for body (takes 4-6).
    "pop_arrive": ("A magical arrival: a sharp heavy crack-pop bursting outward with a big punchy gust of displaced "
                   "air whooshing past and a short whirl dying away, intense, full-bodied" + TAIL, 1.2, 3),
    "crumble": ("Old dry leather crumbling into dust: crackling, flaking, a papery crunch and dust sifting down"
                + TAIL, 1.5, 2),
    "bind": ("A short magical seal: a bright shimmer with a low resonant bell tone settling into place" + TAIL,
             1.0, 2),
}

# name -> dict(take, start, end seconds in the raw take; rms target dBFS; optional fade-out, loop crossfade)
WINDOWS = {
    # charge_2: the only take with no 100 ms drop over 2 dB (charge_1: 2, charge_3: 12, it peaks at 1.7 s and dies).
    # It rises -54 -> -15 dB; the first 0.5 s is noise floor (-54..-40), so the window opens at -33 dB and runs 3.4 s
    # to the -17 dB crest at 3.9 s. No real fade-out: the hook cuts it.
    "charge": dict(take=2, start=0.5, end=3.9, rms=-16, fade=0.05),
    # hook_intense_1 over hook_1-3: a true suck-in (-37 -> -4 dB over 0.18 s, 20 ms bins) slamming into a 0.18 s
    # punch, all of it below 150 Hz; hooks 1-3 were single hits (hook_2 two). Opens at 0.08 s so the punch lands
    # ~0.1 s after the flash; ends 0.62 s, 30 dB under the peak.
    "hook": dict(src="hook_intense", take=1, start=0.08, end=0.62, rms=-13, fade=0.08),
    # transit_1: the plan's 3.0 s steadiest span would take a fading tail on every take (each one decays from 3 s).
    # transit_1 holds -10..-16 dB from 0.8 to 3.05 s (deep, body -4 dB) and is looped there with a 0.25 s crossfade.
    "transit": dict(take=1, start=0.8, end=3.05, rms=-15, loop=0.25),
    # land_3: the only take with a single sharp attack (10 ms bins: -27 -> -2 dB inside 60 ms at 0.13 s); land_1 is
    # thin (peak -13.5) and land_2 peaks late. Opens 10 ms before the impact; 30 dB under the peak by 0.88 s.
    "land": dict(take=3, start=0.12, end=0.88, rms=-13, fade=0.15),
    # pop_depart_1: every take ends on its pop with a tail after it; take 1 has the longest build (-26 -> -9 dB over
    # 0.4 s) into the pop at 0.43 s. Window to pop + 0.29 s.
    # Pinned gain/trim: Vorbis overshoots this pop's peak erratically (+5.8 dB in reads -2.9, -3.0, -4.5 dBFS for trims
    # 0, -1, -2), so the iteration could not hold both the ceiling and the RMS. +4.0/-1.0 gives -4.2 dBFS peak at
    # -16.3 dBFS RMS: 1.3 dB under target, which the in-game volume makes up.
    "pop_depart": dict(take=1, start=0.0, end=0.72, rms=-15, fade=0.12, gain=4.0, trim=-1.0),
    # pop_arrive_6 (re-prompted): crack at 0 then a gust that decays -14 -> -35 dB over 0.45 s; takes 1-3 were a
    # 10 ms click over hiss (take 1 near silence), takes 4-5 a crack with no gust.
    "pop_arrive": dict(src="pop_arrive", take=6, start=0.0, end=0.55, rms=-15, fade=0.12),
    # crumble_1: the most even crackle (spread 4.1 dB over 1.2 s; takes 2-3: 8.7 and 6.0).
    "crumble": dict(take=1, start=0.0, end=1.2, rms=-20, fade=0.15),
    # bind_3: the only clean attack (-19 dB from the first bin, a held bell to 0.6 s); takes 1-2 swell in.
    "bind": dict(take=3, start=0.0, end=0.8, rms=-20, fade=0.2),
}
LIMIT = 0.65
FADE_IN = 0.015
# Encoded peaks must stay at or under this (the limiter's ceiling, -3.7 dBFS).
PEAK_CEILING = -3.7
# Up to this much more gain into the limiter than the plain RMS match, to reach the target RMS on a peaky take.
MAX_EXTRA_LIMITING = 7.0
MASTER_PASSES = 8


def read_key():
    with open(KEY_FILE, encoding="utf-8") as fh:
        return fh.read().strip()


def mp3_path(name, take):
    return os.path.join(TMP_DIR, "%s_%d.mp3" % (name, take))


def fetch(key, path, prompt, seconds, attempts=4):
    payload = {"text": prompt, "duration_seconds": seconds, "prompt_influence": 0.45}
    last = None
    for attempt in range(attempts):
        req = urllib.request.Request(
            API, data=json.dumps(payload).encode("utf-8"),
            headers={"xi-api-key": key, "Content-Type": "application/json"}, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=240) as resp:
                data = resp.read()
            with open(path, "wb") as fh:
                fh.write(data)
            return
        except urllib.error.HTTPError as exc:
            last = "HTTP %s %s" % (exc.code, exc.read().decode("utf-8", "replace")[:300])
            if exc.code not in (429, 500, 502, 503, 504):
                raise RuntimeError(last) from exc
        except Exception as exc:  # network hiccup: retry
            last = repr(exc)
        time.sleep(15 * (attempt + 1))
    raise RuntimeError("gave up on %s: %s" % (os.path.basename(path), last))


def mono(path):
    """The downmix every pass uses, stated outright. Left to -ac 1, ffmpeg sums stereo at 0.5 per channel into s16
    but at 0.707 into the float input of libvorbis, so the encoded file came out ~3 dB hotter than measured (peaks
    past the limiter). An explicit pan makes the measure and the encode hear the same signal."""
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "a:0", "-show_entries", "stream=channels",
                          "-of", "csv=p=0", path], check=True, capture_output=True, text=True).stdout.strip()
    return "pan=mono|c0=0.5*c0+0.5*c1" if out.startswith("2") else "anull"


def decode(path, start=0.0, length=None, filt=None):
    """Mono s16le samples at 44.1 kHz; input seeking so start is exact."""
    cmd = ["ffmpeg", "-v", "error", "-ss", str(start), "-i", path]
    if length is not None:
        cmd += ["-t", str(length)]
    cmd += ["-af", mono(path) + ("," + filt if filt else "")]
    cmd += ["-ac", "1", "-ar", "44100", "-f", "s16le", "-"]
    raw = subprocess.run(cmd, check=True, capture_output=True).stdout
    return struct.unpack("<%dh" % (len(raw) // 2), raw)


def rms_db(samples):
    if not samples:
        return -99.0
    ms = sum(s * s for s in samples) / len(samples)
    return 10 * math.log10(ms / (32768.0 ** 2)) if ms > 0 else -99.0


def peak_db(samples):
    p = max((abs(s) for s in samples), default=0)
    return 20 * math.log10(p / 32768.0) if p > 0 else -99.0


def envelope(samples):
    win = 4410
    return [rms_db(samples[i:i + win]) for i in range(0, len(samples) - win + 1, win)]


def onset(env, below_peak=30.0):
    """The first 100 ms bin within `below_peak` dB of the loudest one."""
    top = max(env)
    for i, v in enumerate(env):
        if v >= top - below_peak:
            return i
    return 0


# ---- pick rules (one per sound) ---------------------------------------------------------------------------------

def steadiest_rise(env, span_bins=34):
    """Charge: how many 100 ms drops of more than 2 dB inside the first `span_bins` bins after the onset."""
    o = onset(env)
    part = env[o:o + span_bins]
    return sum(1 for a, b in zip(part, part[1:]) if b < a - 2.0), o


def peak_within(env, seconds):
    """Hook/land/arrive: whether the loudest bin comes within `seconds` of the onset."""
    o = onset(env)
    p = env.index(max(env))
    return (p - o) * 0.1 <= seconds + 1e-6, o, p


def steadiest_span(env, span_bins=30):
    """Transit: the start bin and spread (dB std-dev) of the steadiest `span_bins` window."""
    best = None
    for i in range(0, max(1, len(env) - span_bins + 1)):
        part = env[i:i + span_bins]
        if len(part) < span_bins:
            break
        sd = statistics.pstdev(part)
        if best is None or sd < best[1]:
            best = (i, sd)
    return best


def tail_end(env, from_bin, below_peak=30.0, max_s=None):
    """Seconds where the take falls `below_peak` dB under its peak after `from_bin` (capped at max_s)."""
    top = max(env)
    p = env.index(top)
    end = len(env)
    for i in range(max(p, from_bin), len(env)):
        if env[i] < top - below_peak:
            end = i
            break
    s = end * 0.1
    return min(s, max_s) if max_s else s


def body(path):
    """Low-band body: RMS below 250 Hz minus full-band RMS (dB). Thin takes read about -20 or lower."""
    full = decode(path)
    low = decode(path, filt="lowpass=f=250,lowpass=f=250")
    return rms_db(low) - rms_db(full)


def verdict(name, env):
    if name == "charge":
        drops, o = steadiest_rise(env)
        return "drops>2dB=%d onset=%.1fs" % (drops, o * 0.1)
    if name in ("hook", "hook_intense"):
        ok, o, p = peak_within(env, 0.3)
        return "peak %.1fs after onset (%s) end=%.1fs" % ((p - o) * 0.1, "ok" if ok else "LATE",
                                                          tail_end(env, o, max_s=o * 0.1 + 1.1))
    if name == "land":
        ok, o, p = peak_within(env, 0.15)
        return "peak %.1fs after onset (%s) end=%.1fs" % ((p - o) * 0.1, "ok" if ok else "LATE",
                                                          tail_end(env, o, max_s=o * 0.1 + 1.2))
    if name == "pop_arrive":
        ok, o, p = peak_within(env, 0.2)
        return "peak %.1fs after onset (%s) end=%.1fs" % ((p - o) * 0.1, "ok" if ok else "LATE", tail_end(env, o))
    if name == "pop_depart":
        p = env.index(max(env))
        return "pop at %.1fs of %.1fs (%s)" % (p * 0.1, len(env) * 0.1, "ok" if p >= len(env) / 2 else "EARLY")
    if name == "transit":
        i, sd = steadiest_span(env)
        return "steadiest 3 s from %.1fs, sd %.2f dB" % (i * 0.1, sd)
    if name == "crumble":
        o = onset(env)
        part = env[o:o + 12]
        return "crackle spread %.2f dB onset=%.1fs" % (statistics.pstdev(part) if len(part) > 1 else 0, o * 0.1)
    o = onset(env)
    ok, _, p = peak_within(env, 0.15)
    return "attack %s onset=%.1fs end=%.1fs" % ("clean" if ok else "SOFT", o * 0.1, tail_end(env, o))


# ---- master -------------------------------------------------------------------------------------------------------

def master_one(name):
    w = WINDOWS[name]
    src = mp3_path(w.get("src", name), w["take"])  # "src" picks a take from a re-prompted variant
    length = w["end"] - w["start"]
    out = os.path.join(OUT_DIR, name + ".ogg")
    os.makedirs(OUT_DIR, exist_ok=True)
    if w.get("loop"):
        # Seamless ring: output = seg[x:L] + blend(seg tail fading out, seg head fading in). The blend is followed by
        # seg[x] in the loop, which is exactly what the head continues into, so the wrap has no seam.
        x = w["loop"]
        folded = os.path.join(TMP_DIR, name + "_loop.wav")
        subprocess.run(["ffmpeg", "-y", "-v", "error", "-ss", str(w["start"]), "-i", src, "-t", str(length + x),
                        "-filter_complex",
                        "[0:a]" + mono(src) + ",asplit=3[a][b][c];"
                        "[a]atrim=%f:%f,asetpts=PTS-STARTPTS[mid];"
                        "[b]atrim=%f:%f,asetpts=PTS-STARTPTS[tail];"
                        "[c]atrim=0:%f,asetpts=PTS-STARTPTS[head];"
                        "[tail][head]acrossfade=d=%f:c1=qsin:c2=qsin[blend];"
                        "[mid][blend]concat=n=2:v=0:a=1[o]" % (x, length, length, length + x, x, x),
                        "-map", "[o]", "-ac", "1", "-ar", "44100", folded], check=True)
        samples_src, ss = folded, 0.0
    else:
        samples_src, ss = src, w["start"]
    pre = decode(samples_src, ss, length)
    gain = w.get("gain", w["rms"] - rms_db(pre))
    trim = w.get("trim", 0.0)
    used = gain
    final = None
    for _ in range(1 if "gain" in w else MASTER_PASSES):
        encode(samples_src, ss, length, out, gain, trim, w.get("fade"), bool(w.get("loop")))
        used = gain
        final = decode(out)
        miss = w["rms"] - rms_db(final)
        over = peak_db(final) - PEAK_CEILING
        if (abs(miss) <= 0.5 and over <= 0.0) or "gain" in w:
            break
        if over > 0.0:
            trim -= over + 0.1  # Vorbis overshoots the limiter's ceiling on hard transients: trim after it
        else:
            gain = min(gain + miss, w["rms"] - rms_db(pre) + MAX_EXTRA_LIMITING)  # the limiter ate some RMS
    over = peak_db(final) - PEAK_CEILING
    gain = used
    if over > 0.0:  # the ceiling wins over the last half dB of RMS
        trim -= over + 0.1
        encode(samples_src, ss, length, out, gain, trim, w.get("fade"), bool(w.get("loop")))
        final = decode(out)
    line = "%-11s take %d  %.2f-%.2fs  gain %+.1f dB trim %+.1f  RMS %.1f dBFS (target %d)  peak %.1f dBFS" % (
        name, w["take"], w["start"], w["end"], gain, trim, rms_db(final), w["rms"], peak_db(final))
    if w.get("loop"):
        steps = [abs(a - b) for a, b in zip(final, final[1:])]
        seam = abs(final[-1] - final[0]) / 32768.0
        line += "  loop seam %.4f (p99 step %.4f)" % (seam, sorted(steps)[int(len(steps) * 0.99)] / 32768.0)
    print(line)


def encode(src, ss, length, out, gain, trim, fade, loop):
    chain = [mono(src)]
    if not loop:
        chain.append("afade=t=in:st=0:d=%f" % FADE_IN)
    # latency=1: the limiter's 5 ms look-ahead is compensated; left off it delays the clip and cuts its end, which
    # opened a seam in the loop.
    chain += ["volume=%fdB" % gain, "alimiter=level=disabled:limit=%s:latency=1" % LIMIT]
    if trim:
        chain.append("volume=%fdB" % trim)
    if fade:
        chain.append("afade=t=out:st=%f:d=%f" % (max(0.0, length - fade), fade))
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-ss", str(ss), "-i", src, "-t", str(length),
                    "-af", ",".join(chain), "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", out],
                   check=True)


def names_from(args, pool=None):
    pool = PROMPTS if pool is None else pool
    return [a for a in args if a in pool] or list(pool)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    cmd, rest = sys.argv[1], sys.argv[2:]
    takes = None
    first = 1
    if "--takes" in rest:
        i = rest.index("--takes")
        takes = int(rest[i + 1])
        rest = rest[:i] + rest[i + 2:]
    if "--first" in rest:
        i = rest.index("--first")
        first = int(rest[i + 1])
        rest = rest[:i] + rest[i + 2:]
    names = names_from(rest)
    os.makedirs(TMP_DIR, exist_ok=True)
    if cmd == "gen":
        key = read_key()
        for n in names:
            prompt, secs, default_takes = PROMPTS[n]
            for k in range(first, first + (takes or default_takes)):
                fetch(key, mp3_path(n, k), prompt, secs)
                print("fetched", n, k)
    elif cmd == "env":
        for n in names:
            k = 1
            while os.path.exists(mp3_path(n, k)):
                s = decode(mp3_path(n, k))
                env = envelope(s)
                print("%s_%d  %.1fs  peak %.1f  rms %.1f  body %.1f  | %s" % (
                    n, k, len(s) / 44100.0, peak_db(s), rms_db(s), body(mp3_path(n, k)), verdict(n, env)))
                print("   ", " ".join("%.0f" % v for v in env))
                k += 1
    elif cmd == "master":
        for n in names_from(rest, WINDOWS):
            master_one(n)


if __name__ == "__main__":
    main()
