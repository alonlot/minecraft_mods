"""Preview the Portkey tunnel shader without the game: a numpy port of shaders/program/portkey.fsh, driven by the
same timeline numbers as portkey.PortkeyTimeline, rendered over any screenshot.

    python tools/portkey_shader_preview.py <screenshot.png> <out_dir> [width]

Writes one frame per beat (charge t 30 and 58, hook h 1 / 2.5 / 4, tunnel h 15, exit x 1 / 3 / 8) and a contact
sheet. Keep it in step with portkey.fsh and PortkeyTimeline when either is tuned; it is a look-check, not the truth.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

TAU = 2 * math.pi
EDGE = 0.72
CORE = np.array([0.84, 0.93, 1.00])
GLOW = np.array([0.75, 0.88, 1.00])
GOLD = np.array([1.00, 0.85, 0.54])
CYAN = np.array([0.56, 0.89, 1.00])
ROSE = np.array([1.00, 0.70, 0.78])
VIOLET = np.array([0.71, 0.61, 1.00])
WALL_DEEP = np.array([0.043, 0.039, 0.118])
WALL_OUT = np.array([0.122, 0.165, 0.333])
M32 = np.uint64(0xFFFFFFFF)


def pcg(v):
    v = v.astype(np.uint64) & M32
    state = (v * np.uint64(747796405) + np.uint64(2891336453)) & M32
    shift = (state >> np.uint64(28)) + np.uint64(4)
    word = (((state >> shift) ^ state) * np.uint64(277803737)) & M32
    return ((word >> np.uint64(22)) ^ word) & M32


def hash_u(v):
    return pcg(v).astype(np.float64) / 4294967295.0


def hash2(a, b):
    return hash_u((a.astype(np.uint64) ^ pcg(np.asarray(b, dtype=np.uint64) + np.uint64(0x9E3779B9))) & M32)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def vnoise(px, py, period, salt):
    ix, iy = np.floor(px), np.floor(py)
    fx, fy = px - ix, py - iy
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    x0 = np.mod(ix, period).astype(np.uint64) + np.uint64(salt)
    x1 = np.mod(ix + 1, period).astype(np.uint64) + np.uint64(salt)
    y0 = (iy.astype(np.int64) + 1048576).astype(np.uint64)
    y1 = y0 + np.uint64(1)
    a, b = hash2(x0, y0), hash2(x1, y0)
    c, d = hash2(x0, y1), hash2(x1, y1)
    return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy


def palette(h):
    out = np.empty(h.shape + (3,))
    out[:] = VIOLET
    for edge, col in ((0.97, ROSE), (0.94, CYAN), (0.85, GOLD), (0.70, CORE)):
        out[h < edge] = col
    return out


def sample(img, u, v):
    h, w, _ = img.shape
    x = np.clip((u * w).astype(int), 0, w - 1)
    y = np.clip(((1 - v) * h).astype(int), 0, h - 1)  # GL texCoord: v up
    return img[y, x]


def frame(img, f, time_s, seed=1234):
    h, w, _ = img.shape
    v, u = np.mgrid[0:h, 0:w]
    tu = (u + 0.5) / w
    tv = 1 - (v + 0.5) / h
    aspect = np.array([w / h, 1.0])
    cx, cy = f["centreX"], f["centreY"]
    dx, dy = (tu - cx) * aspect[0], (tv - cy)
    r = np.hypot(dx, dy)
    rn = r / EDGE
    a = np.arctan2(dy, dx)
    # world
    aw = a + f["twist"] * (1 - smoothstep(0, 1, rn))
    rw = r * (1 - f["pull"] * (1 - np.clip(rn, 0, 1)))
    dirx, diry = np.cos(aw), np.sin(aw)
    su, sv = cx + dirx * rw / aspect[0], cy + diry * rw
    if f["blur"] > 0.001:
        col = np.zeros(img.shape)
        tot = 0
        for i in range(8):
            k = i / 7
            wgt = 1 - 0.6 * k
            m = f["blur"] * 0.14 * k * np.clip(rn, 0, 1)
            col += sample(img, su + (cx - su) * m, sv + (cy - sv) * m) * wgt
            tot += wgt
        col /= tot
    else:
        col = sample(img, su, sv).astype(np.float64)
    if f["chroma"] > 0.05:
        k = (f["chroma"] / h) * np.clip(rn * 1.3, 0, 1)
        ox, oy = dirx / aspect[0] * k, diry * k
        amt = min(1.0, f["chroma"])
        col[..., 0] = col[..., 0] + (sample(img, su + ox, sv + oy)[..., 0] - col[..., 0]) * amt
        col[..., 2] = col[..., 2] + (sample(img, su - ox, sv - oy)[..., 2] - col[..., 2]) * amt
    col = col + f["glow"] * (rn * rn)[..., None] * GLOW * 0.6
    if f["mix"] > 0.002:
        z = 0.35 / np.maximum(r, 0.02)
        tu2 = np.mod(a / TAU + f["spin"] / TAU + 0.08 * z, 1.0)
        tv2 = z + time_s * 2.2
        n = vnoise(tu2 * 12, tv2 * 0.7, 12, seed) * 0.65 + vnoise(tu2 * 24, tv2 * 1.6, 24, seed + 77) * 0.35
        tun = (WALL_DEEP + (WALL_OUT - WALL_DEEP) * smoothstep(0, 1.1, rn)[..., None]) * (0.4 + 1.2 * n)[..., None]
        neb = vnoise(tu2 * 6, tv2 * 0.35, 6, seed + 5)
        tun += (np.array([0.10, 0.06, 0.22]) + (np.array([0.05, 0.14, 0.25]) - np.array([0.10, 0.06, 0.22]))
                * n[..., None]) * (smoothstep(0.4, 0.9, neb) * 1.8)[..., None]
        tun += GLOW * ((0.5 + 0.5 * np.cos(tv2 * TAU * 0.35)) ** 16 * 0.10 * smoothstep(0.15, 0.6, rn))[..., None]
        rim = smoothstep(0.8, 1.15, rn)
        for band in range(3):
            lanes = 24.0 * 2 ** band
            cell = np.floor(tu2 * lanes)
            ident = (cell.astype(np.uint64) + np.uint64(band * 4099) + np.uint64(seed * 131)) & M32
            lit = hash_u(ident) > 0.80
            across = np.abs(np.mod(tu2 * lanes, 1.0) - 0.5) * 2
            width = 0.55 - 0.12 * band
            line = 1 - smoothstep(0, width, across)
            speed = 0.7 + 1.3 * hash2(ident, 3)
            phase = hash2(ident, 7) * 10
            seg = np.mod((tv2 * speed + phase) * 0.45, 1.0)
            ln = (0.26 + 0.2 * np.minimum(z * 0.25, 1)) * (1 + 1.2 * rim)
            dash = smoothstep(0, 0.04, seg) * (1 - smoothstep(ln * 0.35, ln, seg))
            tint = palette(hash2(ident, 13))
            tint = tint + (1 - tint) * (rim * 0.6)[..., None]
            bright = (1.6 - 0.35 * band) * (0.6 + 0.4 * hash2(ident, 19))
            tun += tint * (line * dash * bright * lit)[..., None]
        tun += CORE * (np.exp(-6 * rn) * (0.9 + 0.6 * f["glow"]))[..., None]
        tun *= (1 - 0.55 * smoothstep(0.95, 1.5, rn))[..., None]
        col = col + (tun - col) * min(1.0, f["mix"])
    return np.clip(col, 0, 1)


# --- the timeline (a copy of PortkeyTimeline's curves) -------------------------------------------------------------

def ease(t):
    return min(max(t / 60.0, 0), 1) ** 1.6


def sstep(e0, e1, x):
    t = min(max((x - e0) / (e1 - e0), 0), 1)
    return t * t * (3 - 2 * t)


def lerp(a, b, f):
    return a + (b - a) * f


def spin(h):
    if h <= 0:
        return 0.0
    u = min(h / 10, 1)
    eased = 10 * (u ** 3 - 0.5 * u ** 4) + max(0, h - 10)
    return (0.9 * h + 1.6 * eased) / 20


def charge(t):
    e = ease(t)
    return dict(mix=0, twist=0.06 * e, pull=0.035 * e, blur=0, chroma=1.5 * sstep(40, 60, t), glow=0.25 * e,
                spin=0, centreX=0.5, centreY=0.5)


def tunnel(h):
    ramp = min(max(h / 4, 0), 1) ** 2
    mix = sstep(1, 3.5, h)
    return dict(mix=mix, twist=lerp(0.06, 1.8, ramp), pull=lerp(0.035, 0.32, ramp), blur=0.8 * ramp,
                chroma=lerp(1.5, 7, ramp), glow=lerp(0.25, 0.6, sstep(0, 3, h)), spin=spin(h),
                centreX=0.5 + mix * 0.06 * math.sin(0.21 * h), centreY=0.5 + mix * 0.045 * math.sin(0.33 * h + 1))


def exit_(h, x):
    f = tunnel(h)
    out = min(x / 5, 1)
    pull = lerp(f["pull"], -0.12, sstep(0, 1.5, x)) if x < 1.5 else lerp(-0.12, 0, sstep(1.5, 6, x))
    return dict(mix=f["mix"] * (1 - out) ** 2, twist=f["twist"] * (1 - sstep(0, 10, x)),
                pull=pull, blur=f["blur"] * (1 - sstep(0, 8, x)), chroma=f["chroma"] * (1 - sstep(0, 6, x)),
                glow=f["glow"] * (1 - sstep(0, 6, x)), spin=f["spin"],
                centreX=lerp(f["centreX"], 0.5, out), centreY=lerp(f["centreY"], 0.5, out))


def main():
    src, out_dir = sys.argv[1], sys.argv[2]
    width = int(sys.argv[3]) if len(sys.argv) > 3 else 480
    os.makedirs(out_dir, exist_ok=True)
    im = Image.open(src).convert("RGB")
    im = im.resize((width, int(width * im.height / im.width)))
    img = np.asarray(im).astype(np.float64) / 255.0
    beats = [("charge t30", charge(30), 1.5), ("charge t58", charge(58), 2.9), ("hook h1", tunnel(1), 3.05),
             ("hook h2.5", tunnel(2.5), 3.12), ("hook h4", tunnel(4), 3.2), ("tunnel h15", tunnel(15), 3.75),
             ("exit x1", exit_(25, 1), 4.25), ("exit x3", exit_(27, 3), 4.35), ("exit x8", exit_(32, 8), 4.6)]
    tiles = []
    for name, f, ts in beats:
        arr = (frame(img, f, ts) * 255).astype(np.uint8)
        tile = Image.fromarray(arr)
        tile.save(os.path.join(out_dir, name.replace(" ", "_").replace(".", "_") + ".png"))
        ImageDraw.Draw(tile).text((6, 6), name, fill=(255, 255, 0))
        tiles.append(tile)
    w, h = tiles[0].size
    sheet = Image.new("RGB", (w * 3, h * 3))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % 3) * w, (i // 3) * h))
    sheet.save(os.path.join(out_dir, "sheet.png"))
    print(os.path.join(out_dir, "sheet.png"))


if __name__ == "__main__":
    main()
