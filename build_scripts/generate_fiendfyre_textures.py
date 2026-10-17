"""Textures for the Fiendfyre serpent, drawn procedurally so they can be re-tuned.

Outputs (assets/hogwartsmagic/textures/entity/):
  fiendfyre_body.png  256x512 - churning fire, tiles in both directions. u runs round the body, v along it.
                      RGB is premultiplied fire for additive blending; A is char density, so the same
                      texture drawn with straight alpha and a dark vertex colour gives the smoky molten
                      crust the bright flames sit on.
  fiendfyre_fx.png    512x512 - sprites, RGB premultiplied for additive blending, A the sprite's coverage so
                      the same sprite drawn with straight alpha and a black vertex colour darkens instead:
        (0,0)     128x256  flame tongue, base at the bottom, tip at the top
        (128,0)   128x256  second flame tongue, forked
        (256,0)   128x128  soft glow
        (384,0)   128x128  eye: white-hot slit in a yellow iris
        (256,128) 64x64    solid white, for fangs
        (320,128) 64x64    socket: black, alpha a disc with a soft rim, to burn eye sockets out of the fire

The film serpent is fire with a shape, not a shape on fire: a bright yellow-white core, orange flames
torn backwards off it, deep red at the edges, and dark smoky seams between the tongues that keep it
from reading as a glowing blob.

Run:  python build_scripts/generate_fiendfyre_textures.py
"""
import os

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets",
                   "hogwartsmagic", "textures", "entity")
RNG = np.random.default_rng(666)


def tile_noise(w, h, cells_x, cells_y, octaves=5):
    """fBm value noise that tiles in both axes: each octave is a periodic lattice upsampled bicubically."""
    out = np.zeros((h, w), np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        cx, cy = cells_x * 2 ** o, cells_y * 2 ** o
        lat = RNG.random((cy, cx)).astype(np.float32)
        # periodic smooth interpolation
        ys = np.arange(h) * cy / h
        xs = np.arange(w) * cx / w
        y0, x0 = np.floor(ys).astype(int), np.floor(xs).astype(int)
        ty, tx = ys - y0, xs - x0
        ty, tx = ty * ty * (3 - 2 * ty), tx * tx * (3 - 2 * tx)
        y1, x1 = (y0 + 1) % cy, (x0 + 1) % cx
        a = lat[np.ix_(y0, x0)]
        b = lat[np.ix_(y0, x1)]
        c = lat[np.ix_(y1, x0)]
        d = lat[np.ix_(y1, x1)]
        tx2, ty2 = tx[None, :], ty[:, None]
        out += amp * ((a * (1 - tx2) + b * tx2) * (1 - ty2) + (c * (1 - tx2) + d * tx2) * ty2)
        total += amp
        amp *= 0.55
    return out / total


def fire_ramp(t):
    """0..1 heat -> premultiplied RGB: black, blood red, orange, yellow, white."""
    stops = np.array([
        [0.00, 0.00, 0.00, 0.00],
        [0.22, 0.30, 0.02, 0.00],
        [0.45, 0.85, 0.20, 0.02],
        [0.65, 1.00, 0.52, 0.08],
        [0.82, 1.00, 0.82, 0.30],
        [1.00, 1.00, 0.98, 0.80],
    ], np.float32)
    t = np.clip(t, 0, 1)
    rgb = np.zeros(t.shape + (3,), np.float32)
    for i in range(3):
        rgb[..., i] = np.interp(t, stops[:, 0], stops[:, i + 1])
    return rgb


def to_png(rgb, alpha=None):
    if alpha is None:
        alpha = np.ones(rgb.shape[:2], np.float32)
    arr = np.concatenate([np.clip(rgb, 0, 1), np.clip(alpha, 0, 1)[..., None]], axis=-1)
    return Image.fromarray((arr * 255 + 0.5).astype(np.uint8), "RGBA")


def body():
    w, h = 256, 512
    # Stretched along v so the flames read as streaks torn backwards along the body.
    base = tile_noise(w, h, 4, 3, octaves=6)
    # Domain warp: shift u by another noise field so the streaks curl.
    warp = tile_noise(w, h, 3, 2, octaves=3)
    shift = ((warp - 0.5) * 60).astype(int)
    rows = np.arange(h)[:, None]
    cols = (np.arange(w)[None, :] + shift) % w
    streak = tile_noise(w, h, 8, 2, octaves=4)[rows, cols]
    heat = 0.55 * base + 0.45 * streak
    heat = (heat - heat.min()) / (heat.max() - heat.min())
    # Ridged: bright tongues with sharp dark seams between them.
    ridged = 1.0 - np.abs(heat * 2 - 1)
    heat = np.clip(ridged ** 2.6 * 0.95, 0, 1)
    rgb = fire_ramp(heat)
    char = np.clip(1.0 - heat * 1.4, 0, 1) * 0.85 + 0.15
    to_png(rgb, char).save(os.path.join(OUT, "fiendfyre_body.png"))


def flame_tongue(w, h, forked, seed_shift):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    v = 1.0 - y / (h - 1)            # 0 at base, 1 at tip
    u = (x / (w - 1)) * 2 - 1        # -1..1
    n = tile_noise(w, h, 3, 4, octaves=4)
    n2 = tile_noise(w, h, 6, 8, octaves=3)
    # Teardrop half-width, wobbling sideways with height.
    sway = (n2 - 0.5) * 0.5 * v + np.sin(v * 5 + seed_shift) * 0.12 * v
    width = 0.85 * np.sqrt(np.clip(v * 3, 0, 1)) * (1 - v) ** 0.8 + 0.02
    d = np.abs(u - sway) / width
    if forked:
        fork = np.clip((v - 0.45) * 2.5, 0, 1)
        d = np.minimum(np.abs(u - sway - 0.35 * fork) / (width * (1 - 0.45 * fork)),
                       np.abs(u - sway + 0.35 * fork) / (width * (1 - 0.45 * fork)))
    shape = np.clip(1 - d, 0, 1) ** 0.7
    heat = shape * (0.55 + 0.6 * n) * (1.15 - 0.75 * v)
    heat = gaussian_filter(heat, 1.2)
    rgb = fire_ramp(np.clip(heat * 1.3, 0, 1))
    return rgb


def glow(size):
    y, x = np.mgrid[0:size, 0:size].astype(np.float32)
    r = np.hypot(x - (size - 1) / 2, y - (size - 1) / 2) / (size / 2)
    a = np.clip(1 - r, 0, 1) ** 2.2
    return np.repeat(a[..., None], 3, axis=-1)


def eye(size):
    y, x = np.mgrid[0:size, 0:size].astype(np.float32)
    cx = (x - (size - 1) / 2) / (size / 2)
    cy = (y - (size - 1) / 2) / (size / 2)
    r = np.hypot(cx, cy)
    iris = np.clip(1 - r, 0, 1) ** 1.5
    slit = np.clip(1 - np.abs(cx) / (0.12 * np.clip(1 - np.abs(cy), 0, 1) + 0.02), 0, 1) * (r < 0.9)
    rgb = fire_ramp(np.clip(iris * 0.9, 0, 1))
    rgb = rgb * (1 - slit[..., None] * 0.0) + slit[..., None] * np.array([1.0, 1.0, 0.9])
    return np.clip(rgb, 0, 1)


def fx():
    atlas = np.zeros((512, 512, 3), np.float32)
    atlas[0:256, 0:128] = flame_tongue(128, 256, False, 0.0)
    atlas[0:256, 128:256] = flame_tongue(128, 256, True, 2.0)
    atlas[0:128, 256:384] = glow(128)
    atlas[0:128, 384:512] = eye(128)
    atlas[128:192, 256:320] = 1.0
    alpha = atlas.max(axis=-1)
    y, x = np.mgrid[0:64, 0:64].astype(np.float32)
    r = np.hypot(x - 31.5, y - 31.5) / 32
    alpha[128:192, 320:384] = np.clip((1 - r) / 0.45, 0, 1) ** 1.5
    to_png(atlas, alpha).save(os.path.join(OUT, "fiendfyre_fx.png"))


if __name__ == "__main__":
    body()
    fx()
    print("wrote fiendfyre_body.png, fiendfyre_fx.png")
