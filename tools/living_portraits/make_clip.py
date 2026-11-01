"""Install one lip-synced Flow video moment for a portrait.

  python tools/living_portraits/make_clip.py fat_lady <clip> <line_key> <flow.mp4>

1. faster-whisper word timings for the clip's own (Flow) voice and for each recorded Voice A take of the
   line (tools/living_portraits/takes/<name>/<key>_take*.wav, made by make_voice.py).
2. Picks the take whose words match the clip's and need the least stretching, then time-stretches it piece by
   piece so every word starts where her lips say it (atempo, so the pitch stays).
3. Writes that as sounds/portrait/<name>/<key>.ogg, starting at the clip's first frame.
4. Cuts the video to a 1:2 canvas and saves 192x384 frames at 12 fps to
   textures/entity/portrait/<name>/clips/<clip>/NNN.png.
5. Records frames/fps in portraits/<name>.json and the line's length + mouth envelope.
"""
import glob, json, os, re, shutil, subprocess, sys
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic")
SR, FPS, FRAME = 16000, 12, (192, 384)
sys.path.insert(0, HERE)
from make_voice import write_meta  # noqa: E402


def load(path):
    pcm = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", path, "-f", "s16le", "-ac", "1", "-ar", str(SR), "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(pcm, np.int16).astype(np.float32) / 32768


def onset(x, db=-30):
    hop = SR // 50
    rms = np.array([np.sqrt(np.mean(x[i:i + hop] ** 2)) for i in range(0, len(x) - hop, hop)])
    loud = np.nonzero(20 * np.log10(rms / rms.max() + 1e-9) > db)[0]
    return loud[0] * hop / SR if len(loud) else 0.0


def stretch(seg, factor):
    """factor = input length / output length."""
    chain = []
    while factor > 2:
        chain.append("atempo=2"); factor /= 2
    while factor < 0.5:
        chain.append("atempo=0.5"); factor /= 0.5
    chain.append(f"atempo={factor:.5f}")
    out = subprocess.run(["ffmpeg", "-loglevel", "error", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "-",
                          "-af", ",".join(chain), "-f", "f32le", "-"], input=seg.tobytes(), capture_output=True,
                         check=True)
    return np.frombuffer(out.stdout, np.float32)


def words(model, path, text):
    segs, _ = model.transcribe(path, word_timestamps=True, language="en", initial_prompt=text)
    norm = lambda w: re.sub(r"[^a-z]", "", w.lower())
    return [(norm(w.word), w.start, w.end) for s in segs for w in s.words if norm(w.word)]


def main(name, clip, key, mp4):
    spec = json.load(open(os.path.join(HERE, f"{name}_lines.json")))
    text = next(l["text"] for l in spec["lines"] if l["key"] == key)
    from faster_whisper import WhisperModel
    model = WhisperModel("small.en", device="cpu", compute_type="int8")

    work = os.path.join(HERE, "takes", name)
    clip_wav = os.path.join(work, f"{clip}_clip.wav")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", mp4, "-vn", "-ac", "1", "-ar", str(SR), clip_wav],
                   check=True)
    cx = load(clip_wav)
    cw = words(model, clip_wav, text)
    print("clip :", " ".join(w for w, *_ in cw))

    best = None
    for take in sorted(glob.glob(os.path.join(work, f"{key}_take*.wav"))):
        tw = words(model, take, text)
        if [w for w, *_ in tw] != [w for w, *_ in cw]:
            print("skip", os.path.basename(take), "words differ:", " ".join(w for w, *_ in tw))
            continue
        cost = sum(abs(np.log(max(0.05, b[1] - a[1]) / max(0.05, d[1] - c[1])))
                   for a, b, c, d in zip(tw, tw[1:], cw, cw[1:]))
        if best is None or cost < best[0]:
            best = (cost, take, tw)
    if best is None:
        sys.exit("no take matched the clip's words; record more takes or fix the line")
    _, take, tw = best
    print("using", os.path.basename(take))

    # anchors: every word start, plus the last word's end; whisper pins the first word early, so trust loudness
    x = load(take)
    anchors = [(t[1], c[1]) for t, c in zip(tw, cw)] + [(tw[-1][2], cw[-1][2])]
    anchors[0] = (max(anchors[0][0], onset(x)), max(anchors[0][1], onset(cx)))
    clean = [anchors[0]]
    for a in anchors[1:]:  # keep anchors strictly increasing on both sides
        if a[0] > clean[-1][0] + 0.04 and a[1] > clean[-1][1] + 0.04:
            clean.append(a)
    y = np.zeros(len(cx) + SR, np.float32)
    for (a, c), (b, d) in zip(clean, clean[1:]):
        s = stretch(x[int(a * SR):int(b * SR)], (b - a) / (d - c))
        i = int(c * SR)
        y[i:i + len(s)] += s[:len(y) - i]
    tail = x[int(clean[-1][0] * SR):]
    i = int(clean[-1][1] * SR)
    y[i:i + len(tail)] += tail[:len(y) - i]
    y = y[:len(cx)]

    ogg = os.path.join(ASSETS, "sounds", "portrait", name, key + ".ogg")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "-",
                    "-af", "loudnorm=I=-16:TP=-1.5", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", ogg],
                   input=y.tobytes(), check=True)

    frames = os.path.join(ASSETS, "textures", "entity", "portrait", name, "clips", clip)
    shutil.rmtree(frames, ignore_errors=True)
    os.makedirs(frames)
    # one 256-colour palette for the whole clip: ~5x smaller than truecolour and no palette flicker
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", mp4, "-filter_complex",
                    f"fps={FPS},crop=ih/2:ih,scale={FRAME[0]}:{FRAME[1]}:flags=area,split[a][b];"
                    "[a]palettegen=max_colors=256:stats_mode=full[p];[b][p]paletteuse=dither=bayer:bayer_scale=4",
                    os.path.join(frames, "%03d.png")], check=True)
    # ffmpeg numbers from 001; shift to 000-based
    pngs = sorted(glob.glob(os.path.join(frames, "*.png")))
    for i, p in enumerate(pngs):
        os.rename(p, os.path.join(frames, f"{i:03d}.png"))

    meta_path = os.path.join(ASSETS, "portraits", name + ".json")
    meta = json.load(open(meta_path))
    meta.setdefault("clips", {})[clip] = {"frames": len(pngs), "fps": FPS}
    json.dump(meta, open(meta_path, "w"), separators=(",", ":"))
    write_meta(name, spec)
    print(f"installed {clip}: {len(pngs)} frames, voice {key}.ogg")


if __name__ == "__main__":
    main(*sys.argv[1:5])
