"""Record a portrait's voice lines with Fish Audio and install them as game sounds.

  python tools/living_portraits/make_voice.py fat_lady

Reads tools/living_portraits/<name>_lines.json. For every line without an installed .ogg it asks Fish Audio
for a take (lines with a "clip" get 3 takes, kept raw in tools/living_portraits/takes/<name>/ for
lipsync_dub.py), trims the silence at both ends, levels it to -16 LUFS and writes mono 44.1 kHz Vorbis to
assets/hogwartsmagic/sounds/portrait/<name>/<key>.ogg. Video lines are installed by lipsync_dub.py instead.
Then it rewrites assets/hogwartsmagic/portraits/<name>.json with each line's length and a 50 ms loudness
envelope (0..1) that drives the puppet's mouth. The Fish key is read from C:\\Users\\alonl\\fish_key.txt.
"""
import json, os, subprocess, sys, urllib.request
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic")
HERE = os.path.dirname(os.path.abspath(__file__))
FX = "silenceremove=start_periods=1:start_threshold=-45dB,areverse," \
     "silenceremove=start_periods=1:start_threshold=-45dB,areverse,loudnorm=I=-16:TP=-1.5"


def tts(text, voice, key):
    req = urllib.request.Request("https://api.fish.audio/v1/tts",
                                 data=json.dumps({"text": text, "reference_id": voice, "format": "mp3"}).encode(),
                                 headers={"Authorization": "Bearer " + key, "model": "speech-1.6",
                                          "Content-Type": "application/json"})
    return urllib.request.urlopen(req).read()


def envelope(path):
    pcm = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", path, "-f", "s16le", "-ac", "1", "-ar", "8000", "-"],
                         capture_output=True, check=True).stdout
    x = np.frombuffer(pcm, np.int16).astype(np.float32) / 32768
    rms = [float(np.sqrt(np.mean(x[i:i + 400] ** 2))) for i in range(0, len(x) - 399, 400)]
    peak = max(rms) if rms else 1
    return len(x) / 8000, [round(r / peak, 2) for r in rms]


def main(name):
    spec = json.load(open(os.path.join(HERE, f"{name}_lines.json")))
    key = open(r"C:\Users\alonl\fish_key.txt").read().strip()
    snd = os.path.join(ASSETS, "sounds", "portrait", name)
    takes = os.path.join(HERE, "takes", name)
    os.makedirs(snd, exist_ok=True)
    os.makedirs(takes, exist_ok=True)
    for line in spec["lines"]:
        ogg = os.path.join(snd, line["key"] + ".ogg")
        if line.get("clip"):
            for i in range(3):
                raw = os.path.join(takes, f"{line['key']}_take{i}.wav")
                if not os.path.exists(raw):
                    mp3 = raw[:-4] + ".mp3"
                    open(mp3, "wb").write(tts(line["text"], spec["voice"], key))
                    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", mp3, "-af", FX, "-ac", "1",
                                    "-ar", "44100", raw], check=True)
                    os.remove(mp3)
            print("takes", line["key"])
            continue
        if os.path.exists(ogg):
            continue
        mp3 = os.path.join(takes, line["key"] + ".mp3")
        open(mp3, "wb").write(tts(line["text"], spec["voice"], key))
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", mp3, "-af", FX, "-ac", "1", "-ar", "44100",
                        "-c:a", "libvorbis", "-q:a", "5", ogg], check=True)
        os.remove(mp3)
        print("recorded", line["key"])
    write_meta(name, spec)


def write_meta(name, spec):
    """Line lengths + mouth envelopes for every installed line, plus clip frame counts."""
    snd = os.path.join(ASSETS, "sounds", "portrait", name)
    meta_path = os.path.join(ASSETS, "portraits", name + ".json")
    meta = json.load(open(meta_path)) if os.path.exists(meta_path) else {}
    meta.setdefault("clips", {})
    meta["lines"] = {}
    for line in spec["lines"]:
        ogg = os.path.join(snd, line["key"] + ".ogg")
        if os.path.exists(ogg):
            secs, env = envelope(ogg)
            meta["lines"][line["key"]] = {"seconds": round(secs, 2), "envelope": env}
    os.makedirs(os.path.dirname(meta_path), exist_ok=True)
    json.dump(meta, open(meta_path, "w"), separators=(",", ":"))
    print("wrote", meta_path, len(meta["lines"]), "lines")
    register_sounds(name, spec)


def register_sounds(name, spec):
    """Add portrait.<name>.<key> to sounds.json for every line (keeps the file's existing order and style)."""
    path = os.path.join(ASSETS, "sounds.json")
    sounds = json.load(open(path, encoding="utf-8"))
    added = 0
    for line in spec["lines"]:
        event = f"portrait.{name}.{line['key']}"
        if event not in sounds:
            sounds[event] = {"sounds": [{"name": f"hogwartsmagic:portrait/{name}/{line['key']}"}]}
            added += 1
    if added:
        json.dump(sounds, open(path, "w", encoding="utf-8", newline="\n"), indent=2, ensure_ascii=False)
        open(path, "a", encoding="utf-8", newline="\n").write("\n")
    print("sounds.json:", added, "added")


if __name__ == "__main__":
    main(sys.argv[1])
