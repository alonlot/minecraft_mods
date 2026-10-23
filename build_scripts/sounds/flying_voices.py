"""Flying memory voice lines (Fish Audio TTS) -> sounds/pensieve/voice/flying_*.ogg.

Same pipeline as letter_voices.py: key from C:\\Users\\alonl\\fish_key.txt (never printed, never committed), raw mp3
takes in a scratch directory outside the repo, trim silence, loudnorm -16/-1.5, mono 44.1k vorbis. Pass line ids as
arguments to redo only those. Measured lengths merge into flying_clip_lengths.json beside the sfx lengths.
No line contains a name for the boy. Music is not made here (Task 10).
Mastering: linear gain to -16 LUFS integrated + alimiter -2 dBTP latency=1 (loudnorm misses on short clips).

Voices:
  Hooch       0887ed97dc294cfab9ffccc608d80605  (user pick hooch_B, 2026-10-23)
  Neville     ebe169afb0934b5bbcc5c1ed62a024b5  (user pick neville_A)
  Malfoy      0fe8aa6bccf64c48b67f034aa17c6058  (user pick draco_D, 2026-10-23)
  Viewer      67ccf061bf3b4f9c8b635addc77eba6f  (young Harry, age 13)
  McGonagall  d625beac29e2417faba8ace4c3d6ccdd  (same as the other memories)
  Wood        32d9e4819f9e4bd79845fd64536300a4  (user pick wood_B: Fish "Oliver Wood" by kat faw, 2026-10-23)
  Commentator 7c90feecce0e4bbb8d5d1a20a98e6a11  (user pick commentator_A: Fish "Commentator (good)" by LeroyJr)
flying_class_up is a ragged chorus: Neville, Hermione, Malfoy and Wood, four whisper-gated takes spread over 0.38 s (not the viewer, who has his own clip).
"""

import json
import os
import subprocess
import sys
import urllib.request

KEY_FILE = r"C:\Users\alonl\fish_key.txt"
API = "https://api.fish.audio/v1/tts"

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                       "sounds", "pensieve", "voice")
LENGTHS = os.path.join(HERE, "flying_clip_lengths.json")
RAW_DIR = (r"C:\Users\alonl\AppData\Local\Temp\claude\C--Users-alonl-OneDrive-Desktop-harry-potter-mod"
           r"\eca4c2f1-9bf3-4c65-b86d-13bbb709117a\scratchpad\flying_audio")

HOOCH = "0887ed97dc294cfab9ffccc608d80605"
NEVILLE = "ebe169afb0934b5bbcc5c1ed62a024b5"
MALFOY = "0fe8aa6bccf64c48b67f034aa17c6058"
VIEWER = "67ccf061bf3b4f9c8b635addc77eba6f"
MCGONAGALL = "d625beac29e2417faba8ace4c3d6ccdd"
WOOD = "32d9e4819f9e4bd79845fd64536300a4"
COMMENTATOR = "7c90feecce0e4bbb8d5d1a20a98e6a11"

# id -> (voice, speed, text)
LINES = {
    "flying_hooch_welcome": (HOOCH, 1.0, "Good afternoon, class. Don't stand there gawping, find a broom."),
    "flying_hooch_left": (HOOCH, 1.0, "Stand on the left of it. Right hand out, flat, over the handle."),
    "flying_hooch_up": (HOOCH, 1.0, "Now, firmly, and mean it: say 'Up!'"),
    "flying_viewer_up": (VIEWER, 1.0, "Up!"),
    "flying_neville_whoa": (NEVILLE, 1.0, "It's going on its own! I can't stop it, help!"),
    "flying_hooch_nobody": (HOOCH, 1.0, "Nobody moves! Feet on the grass until I'm back, or you'll answer for it."),
    "flying_malfoy_look": (MALFOY, 1.0, "Look what the clumsy idiot dropped."),
    "flying_malfoy_come": (MALFOY, 1.0, "Want it? Come up and take it, then."),
    "flying_malfoy_catch": (MALFOY, 1.0, "Go on, then. Fetch!"),
    "flying_mcg_follow": (MCGONAGALL, 1.0, "You. With me. Now."),
    "flying_hooch_clean": (HOOCH, 1.0, "I want a clean game, from all of you."),
    "flying_captain": (WOOD, 1.0, "Just catch the Snitch. That's all you've got to do."),
    "flying_comm_off": (COMMENTATOR, 1.0, "And they're off!"),
    "flying_comm_goal": (COMMENTATOR, 1.0, "And it's in!"),
    "flying_comm_seeker": (COMMENTATOR, 1.0, "The Seeker has seen something!"),
}

# the chorus: (voice, speed, delay seconds, gain) takes of "Up!", mixed
HERMIONE = "478907affa884ae7824ac8a06374aba3"
CHORUS = {
    "flying_class_up": [(NEVILLE, 1.0, 0.00, 1.0), (HERMIONE, 1.0, 0.12, 0.9), (MALFOY, 1.0, 0.25, 0.85),
                        (WOOD, 1.0, 0.38, 0.8)],
}


def tts(key, voice, speed, text, path):
    body = json.dumps({"text": text, "reference_id": voice, "format": "mp3",
                       "prosody": {"speed": speed}}).encode()
    req = urllib.request.Request(API, data=body, headers={
        "Authorization": "Bearer " + key, "model": "speech-1.6", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=180) as r:
        data = r.read()
    with open(path, "wb") as f:
        f.write(data)


TARGET_LUFS = -16.0
LIMIT = 0.794  # -2.0 dBTP before Vorbis, which overshoots a little


def measure_lufs(path):
    r = subprocess.run(["ffmpeg", "-v", "info", "-nostats", "-i", path, "-af", "ebur128=peak=true", "-f", "null", "-"],
                       capture_output=True, text=True)
    tail = r.stderr[r.stderr.rindex("Summary:"):]
    for line in tail.splitlines():
        if line.strip().startswith("I:"):
            return float(line.split()[1])
    raise RuntimeError("no loudness for " + path)


def convert(src, out):
    """Trim silence, linear gain to -16 LUFS integrated (not loudnorm's dynamic mode, which misses on short
    clips), then alimiter at -2 dBTP with latency=1 (no head delay), mono 44.1k vorbis."""
    sil = ("silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,"
           "areverse,silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.12,areverse")
    tmp = out + ".trim.wav"
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", src, "-af", sil, "-ac", "1", "-ar", "44100", tmp],
                   check=True)
    gain = TARGET_LUFS - measure_lufs(tmp)
    for _ in range(5):  # the limiter eats loudness on peaky clips: re-measure the result and top up the gain
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", tmp, "-af",
                        "volume=%.2fdB,alimiter=level=disabled:limit=%.3f:latency=1" % (gain, LIMIT),
                        "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5", out], check=True)
        miss = TARGET_LUFS - measure_lufs(out)
        if abs(miss) < 0.25:
            break
        gain += miss
    os.remove(tmp)


def duration(path):
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0",
                        path], capture_output=True, text=True, check=True)
    return float(r.stdout.strip())


_WHISPER = []


def heard(path):
    if not _WHISPER:
        import whisper
        _WHISPER.append(whisper.load_model("small.en"))
    return _WHISPER[0].transcribe(path, fp16=False)["text"]


def chorus(key, name, takes, out, force):
    inputs, parts = [], []
    for i, (voice, speed, delay, gain) in enumerate(takes):
        raw = os.path.join(RAW_DIR, "%s_%d.mp3" % (name, i))
        if not os.path.exists(raw) or force:
            for _ in range(8):  # gate: every take must be heard as "up" on its own
                tts(key, voice, speed, "Up!", raw)
                if heard(raw).strip(" .!?,").lower() == "up":
                    break
            else:
                raise RuntimeError("no clean Up! from " + voice)
        inputs += ["-i", raw]
        ms = int(delay * 1000)
        parts.append("[%d]adelay=%d|%d,volume=%.2f[a%d]" % (i, ms, ms, gain, i))
    mix = "".join("[a%d]" % i for i in range(len(takes)))
    flt = ";".join(parts) + ";" + mix + "amix=inputs=%d:normalize=0[m]" % len(takes)
    mixed = os.path.join(RAW_DIR, name + ".wav")
    subprocess.run(["ffmpeg", "-v", "error", "-y"] + inputs + ["-filter_complex", flt, "-map", "[m]", mixed],
                   check=True)
    convert(mixed, out)


def save_lengths(update):
    data = {}
    if os.path.exists(LENGTHS):
        with open(LENGTHS, encoding="utf-8") as f:
            data = json.load(f)
    data.update(update)
    with open(LENGTHS, "w", encoding="utf-8") as f:
        json.dump(dict(sorted(data.items())), f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    args = sys.argv[1:]
    with open(KEY_FILE) as f:
        key = f.read().strip()
    os.makedirs(RAW_DIR, exist_ok=True)
    os.makedirs(OUT_DIR, exist_ok=True)
    names = args or (list(LINES) + list(CHORUS))
    lengths = {}
    for name in names:
        out = os.path.join(OUT_DIR, name + ".ogg")
        if name in CHORUS:
            chorus(key, name, CHORUS[name], out, bool(args))
            lengths[name] = round(duration(out), 2)
            print("%-22s %.2fs  chorus" % (name, lengths[name]))
            continue
        voice, speed, text = LINES[name]
        raw = os.path.join(RAW_DIR, name + ".mp3")
        if not os.path.exists(raw) or args:
            tts(key, voice, speed, text, raw)
        convert(raw, out)
        lengths[name] = round(duration(out), 2)
        print("%-22s %.2fs  (raw %.2fs)  %s" % (name, lengths[name], duration(raw), text))
    save_lengths(lengths)
