"""Fit Flow's painted card frames, card back and frog box onto the sizes CardArt draws.

  python tools/living_cards/make_frames.py frame_common <flow.jpeg>
  python tools/living_cards/make_frames.py back <flow.jpeg>
  python tools/living_cards/make_frames.py box <flow.jpeg>

Frames: the painted black window is found and the picture is stretched piecewise (separately left of, across and right
of the window, and likewise top to bottom), so the window lands exactly on CardArt's portrait window (x 32..288,
y 22..406 of a 320x448 card) and is then punched transparent. The back is resized to 320x448. The box is cut out of its
white background onto a 192x192 square whose top 77 rows hold the lid (CardRevealScreen lifts those rows).
"""
import os
import sys

import cv2
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "textures", "card")
W, H = 320, 448
WIN = (32, 22, 288, 406)  # x0, y0, x1, y1, as CardArt.WIN_X/WIN_Y with the 256x384 window


def find_window(img):
    """Bounding box of the large dark region in the middle: the painted window."""
    h, w = img.shape[:2]
    grey = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    dark = (grey < 40).astype(np.uint8)
    dark = cv2.morphologyEx(dark, cv2.MORPH_OPEN, np.ones((9, 9), np.uint8))
    n, lab, stats, _ = cv2.connectedComponentsWithStats(dark)
    best = max(range(1, n), key=lambda i: stats[i, cv2.CC_STAT_AREA])
    x, y, bw, bh = stats[best, :4]
    if bw < w * 0.5 or bh < h * 0.5:
        raise RuntimeError(f"window too small: {bw}x{bh} in {w}x{h}")
    return x, y, x + bw, y + bh


def piecewise(src_edges, dst_edges, n):
    """For each output coordinate 0..n-1, the source coordinate (piecewise linear through the edge pairs)."""
    return np.interp(np.arange(n) + 0.5, dst_edges, src_edges).astype(np.float32) - 0.5


def frame(name, path):
    img = cv2.imread(path)
    h, w = img.shape[:2]
    x0, y0, x1, y1 = find_window(img)
    # pull the window edges in by a few pixels so no black rim survives the punch
    inset = int(round(w * 0.004))
    mx = piecewise([0, x0 + inset, x1 - inset, w], [0, WIN[0], WIN[2], W], W)
    my = piecewise([0, y0 + inset, y1 - inset, h], [0, WIN[1], WIN[3], H], H)
    mapx, mapy = np.meshgrid(mx, my)
    out = cv2.remap(img, mapx, mapy, cv2.INTER_AREA if w > W else cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)
    # the remap above is a big downscale; INTER_AREA is not supported by remap, so pre-blur to avoid aliasing
    if w > 2 * W:
        small = cv2.resize(img, None, fx=W / w * 2, fy=H / h * 2, interpolation=cv2.INTER_AREA)
        sx, sy = small.shape[1] / w, small.shape[0] / h
        out = cv2.remap(small, mapx * sx, mapy * sy, cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
    alpha = np.full((H, W), 255, np.uint8)
    alpha[WIN[1]:WIN[3], WIN[0]:WIN[2]] = 0
    cv2.imwrite(os.path.join(TEX, name + ".png"), np.dstack([out, alpha]))
    print(f"{name}: window {x0},{y0}-{x1},{y1} of {w}x{h} -> {WIN}")


def back(path):
    img = cv2.imread(path)
    cv2.imwrite(os.path.join(TEX, "back.png"), cv2.resize(img, (W, H), interpolation=cv2.INTER_AREA))
    print("back written")


def box(path):
    img = cv2.imread(path).astype(np.float32) / 255
    # white background -> alpha: distance from white, softened
    d = np.clip((1.0 - img.min(axis=2)) * 6.0, 0, 1)
    mask = (d > 0.15).astype(np.uint8)
    n, lab, stats, _ = cv2.connectedComponentsWithStats(mask)
    big = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
    keep = (lab == big).astype(np.uint8)
    flood = keep.copy()
    cv2.floodFill(flood, np.zeros((keep.shape[0] + 2, keep.shape[1] + 2), np.uint8), (0, 0), 1)
    keep = keep | (1 - flood)
    alpha = cv2.GaussianBlur(keep.astype(np.float32), (0, 0), 1.0)
    x, y, bw, bh = stats[big, :4]
    side = int(max(bw, bh) * 1.06)
    cx, cy = x + bw // 2, y + bh // 2
    rgba = np.dstack([img, alpha])
    pad = side
    rgba = cv2.copyMakeBorder(rgba, pad, pad, pad, pad, cv2.BORDER_CONSTANT, value=(1, 1, 1, 0))
    crop = rgba[cy + pad - side // 2: cy + pad + side // 2, cx + pad - side // 2: cx + pad + side // 2]
    out = cv2.resize(crop, (192, 192), interpolation=cv2.INTER_AREA)
    cv2.imwrite(os.path.join(TEX, "box.png"), (np.clip(out, 0, 1) * 255).astype(np.uint8))
    print("box written; check the lid sits in the top 77 rows")


if __name__ == "__main__":
    kind, src = sys.argv[1], sys.argv[2]
    if kind.startswith("frame_"):
        frame(kind, src)
    elif kind == "back":
        back(src)
    else:
        box(src)
