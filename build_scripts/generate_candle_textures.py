"""Textures for the floating_candles memory effect (client/pensieve/FloatingCandlesRenderer).

Outputs (assets/hogwartsmagic/textures/entity/):
  candle_wax.png    16x64, opaque. A tall cream-white wax candle seen side on (the renderer turns it to face the
                    camera round its own axis): rounded shading across, the top glowing where the flame lights the
                    melt pool, a few wax drips running down, a short dark wick stub at the very top.
  candle_flame.png  32x64, premultiplied for additive blending: a teardrop flame, white-hot core, gold body,
                    orange-red tip and a faint blue base.
The halo round each flame reuses ceiling_dot.png.

Run:  python build_scripts/generate_candle_textures.py [preview.png]
"""
import os
import sys

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets",
                   "hogwartsmagic", "textures", "entity")
RNG = np.random.default_rng(1492)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def wax():
    w, h = 16, 64
    u = (np.arange(w) + 0.5) / w
    v = (np.arange(h) + 0.5) / h                       # 0 top .. 1 bottom
    U, V = np.meshgrid(u, v)
    across = np.cos((U - 0.5) * np.pi * 0.95)          # round: bright middle, darker sides
    shade = 0.62 + 0.38 * across
    top_glow = np.exp(-V / 0.10) * 0.35                # the flame lights the melt pool and the top of the wax
    base = 0.92 - 0.10 * V                             # slightly warmer and dimmer down the candle
    lum = np.clip(shade * base + top_glow, 0, 1.15)
    # Drips: a few raised runs from the rim, each catching a little light on one side.
    drips = np.zeros_like(U)
    for _ in range(3):
        x0 = RNG.uniform(0.15, 0.85)
        length = RNG.uniform(0.15, 0.45)
        width = RNG.uniform(0.05, 0.09)
        run = np.exp(-((U - x0) / width) ** 2) * smoothstep(length, length - 0.08, V) * smoothstep(0.02, 0.06, V)
        drips = np.maximum(drips, run)
    lum = lum + 0.10 * drips
    r = np.clip(lum * 1.00, 0, 1)
    g = np.clip(lum * 0.94, 0, 1)
    b = np.clip(lum * 0.80, 0, 1)
    rgba = np.stack([r, g, b, np.ones_like(r)], axis=-1)
    # The top rim: a slightly uneven melted lip, then the dark wick stub in the middle of the top two rows.
    rgba[0, :, :3] *= 1.05
    rgba[0:2, 7:9, :3] = [0.18, 0.13, 0.10]
    save("candle_wax.png", rgba)


def flame():
    w, h = 32, 64
    x = ((np.arange(w) + 0.5) / w - 0.5) * 2           # -1..1
    y = (np.arange(h) + 0.5) / h                       # 0 top .. 1 bottom
    X, Y = np.meshgrid(x, y)
    # A teardrop: widest a third up from the bottom, pinched to a point at the top.
    t = 1 - Y
    width = np.where(t < 0.3, np.sqrt(np.clip(t / 0.3, 0, 1)) * 0.75 + 0.05,
                     0.8 * np.clip(1 - (t - 0.3) / 0.7, 0, 1) ** 0.9)
    r = np.abs(X) / np.maximum(width, 1e-3)
    body = smoothstep(1.0, 0.55, r) * smoothstep(0.0, 0.08, t) * smoothstep(1.0, 0.85, t)
    core = smoothstep(0.55, 0.0, r) * np.exp(-((t - 0.28) / 0.18) ** 2)
    hot = np.clip(core, 0, 1)
    tip = smoothstep(0.45, 0.9, t)
    red = np.clip(body * (1.0) + hot, 0, 1)
    green = np.clip(body * (0.78 - 0.38 * tip) + hot * 0.95, 0, 1)
    blue = np.clip(body * (0.30 - 0.25 * tip) + hot * 0.75, 0, 1)
    base_blue = np.exp(-((t - 0.06) / 0.05) ** 2) * smoothstep(1.0, 0.3, r) * 0.35
    blue = np.clip(blue + base_blue, 0, 1)
    a = np.clip(np.maximum(body, hot), 0, 1)
    save("candle_flame.png", np.stack([red * a, green * a, blue * a, a], axis=-1))


def save(name, rgba):
    img = Image.fromarray((np.clip(rgba, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")
    img.save(os.path.join(OUT, name))
    print("wrote", name, img.size)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    wax()
    flame()
    if len(sys.argv) > 1:
        bg = Image.new("RGBA", (16 * 8 + 32 * 4 + 20, 64 * 4), (20, 16, 12, 255))
        w_img = Image.open(os.path.join(OUT, "candle_wax.png")).resize((16 * 8 // 2, 64 * 4), Image.NEAREST)
        f_img = Image.open(os.path.join(OUT, "candle_flame.png")).resize((32 * 4, 64 * 4), Image.BILINEAR)
        bg.paste(w_img, (0, 0))
        arr = np.asarray(bg).astype(np.float32)
        f = np.asarray(f_img).astype(np.float32)
        x0 = 16 * 8 // 2 + 20
        arr[:, x0:x0 + 128, :3] = np.clip(arr[:, x0:x0 + 128, :3] + f[..., :3], 0, 255)
        Image.fromarray(arr.astype(np.uint8)).save(sys.argv[1])
        print("preview", sys.argv[1])
