"""Turn a legendary card's Veo exit clip into card frames.

  python tools/living_cards/make_clip.py merlin <clip.mp4>

The clip was made at 9:16 from a 2:3 painting padded top and bottom (make_clip frames in clip_frames/), so the 2:3
middle is cropped back out. Frames are taken at 12 fps and scaled to 256x384. The first few frames cross-fade from the
card's own base painting and the last few into its empty plate, so the card never jumps when the clip starts or ends.
Writes textures/card/<key>/clips/exit/NNNN.png, removes stale frames, and sets exit_clip in cards/<key>.json.
"""
import glob
import json
import os
import subprocess
import sys

import cv2
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic")
FPS = 12
BLEND = 4  # frames cross-faded at each end


def frames(mp4):
    tmp = os.path.join(ROOT, "tools", "living_cards", "clip_frames", "tmp")
    os.makedirs(tmp, exist_ok=True)
    for f in glob.glob(os.path.join(tmp, "*.png")):
        os.remove(f)
    subprocess.run(["ffmpeg", "-v", "error", "-i", mp4, "-vf", f"fps={FPS}", os.path.join(tmp, "%04d.png")], check=True)
    return [cv2.imread(f).astype(np.float32) / 255 for f in sorted(glob.glob(os.path.join(tmp, "*.png")))]


def crop_middle(img):
    h, w = img.shape[:2]
    ch = int(round(w * 3 / 2))
    y0 = (h - ch) // 2
    return cv2.resize(img[y0:y0 + ch], (256, 384), interpolation=cv2.INTER_AREA)


def main(key, mp4):
    tex = os.path.join(ASSETS, "textures", "card", key)
    base = cv2.imread(os.path.join(tex, "base.png")).astype(np.float32) / 255
    plate = cv2.imread(os.path.join(tex, "empty.png")).astype(np.float32) / 255
    clip = [crop_middle(f) for f in frames(mp4)]
    n = len(clip)
    for i in range(min(BLEND, n)):
        t = (i + 1) / (BLEND + 1)
        clip[i] = base * (1 - t) + clip[i] * t
        j = n - 1 - i
        clip[j] = plate * (1 - t) + clip[j] * t
    out = os.path.join(tex, "clips", "exit")
    os.makedirs(out, exist_ok=True)
    for f in glob.glob(os.path.join(out, "*.png")):
        os.remove(f)
    for i, f in enumerate(clip):
        cv2.imwrite(os.path.join(out, f"{i:04d}.png"), (np.clip(f, 0, 1) * 255 + 0.5).astype(np.uint8))
    jpath = os.path.join(ASSETS, "cards", key + ".json")
    data = json.load(open(jpath))
    data["exit_clip"] = {"frames": n, "fps": FPS}
    json.dump(data, open(jpath, "w"), indent=2)
    print(f"{key}: {n} frames at {FPS} fps")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
