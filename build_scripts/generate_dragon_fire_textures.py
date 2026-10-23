"""Textures for the memory Horntail's fire (the `breathe` action, client/pensieve/DragonFireRenderer), drawn
procedurally and seeded so they can be re-tuned. A placeholder set until Task 11's generate_first_task_textures.py
takes them over; the renderer only needs the file names and the sprite cells below.

Outputs (assets/hogwartsmagic/textures/entity/):
  dragon_fire_sprites.png  512x512, 128 px cells:
        row 0 (y 0)    four billowing flame puffs: RGB premultiplied grey light (the vertex colour tints it), A the same,
                       drawn additive; bright churning centre, ragged soft rim, black at the cell edge
        row 1 (y 128)  four smoke puffs: RGB white, A the cloud (soft, lumpy); tinted and drawn with straight alpha
        row 2 (y 256)  (0) soft glow, (128) ember spark, (256) flame tongue (base at the bottom), (384) splash lick
  dragon_fire_body.png     128x512, tiles along v (the jet's length): streaming flame, bright down the middle (u 0.5)
                           and fading to black at both edges; premultiplied grey, additive
  dragon_fire_shimmer.png  128x256, tiles along v: thin wavering light and dark strands at low alpha, for the heat haze
  dragon_scorch.png        128x128, tiles both ways: soot, dark brown-black with a lumpy alpha and a few cracks
  dragon_embers.png        128x128, tiles both ways: glowing orange specks and crack lines on black, additive

Run:  python build_scripts/generate_dragon_fire_textures.py
"""
import os

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets",
                   "hogwartsmagic", "textures", "entity")
RNG = np.random.default_rng(2026_10_23)


def tile_noise(w, h, cells_x, cells_y, octaves=5, falloff=0.55):
    """fBm value noise that tiles in both axes (each octave a periodic lattice, smoothly interpolated), 0..1."""
    out = np.zeros((h, w), np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        cx, cy = cells_x * 2 ** o, cells_y * 2 ** o
        lat = RNG.random((cy, cx)).astype(np.float32)
        ys = np.arange(h) * cy / h
        xs = np.arange(w) * cx / w
        y0, x0 = np.floor(ys).astype(int), np.floor(xs).astype(int)
        ty, tx = ys - y0, xs - x0
        ty, tx = ty * ty * (3 - 2 * ty), tx * tx * (3 - 2 * tx)
        y1, x1 = (y0 + 1) % cy, (x0 + 1) % cx
        a, b = lat[np.ix_(y0 % cy, x0 % cx)], lat[np.ix_(y0 % cy, x1)]
        c, d = lat[np.ix_(y1, x0 % cx)], lat[np.ix_(y1, x1)]
        tx2, ty2 = tx[None, :], ty[:, None]
        out += amp * ((a * (1 - tx2) + b * tx2) * (1 - ty2) + (c * (1 - tx2) + d * tx2) * ty2)
        total += amp
        amp *= falloff
    return out / total


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def grid(n):
    """Cell coordinates -1..1 (x right, y down)."""
    c = (np.arange(n) + 0.5) / n * 2 - 1
    return np.meshgrid(c, c)


def to_img(rgb, a):
    rgba = np.dstack([np.clip(rgb, 0, 1)] + [np.clip(a, 0, 1)]) if rgb.ndim == 3 else \
        np.dstack([np.clip(rgb, 0, 1)] * 3 + [np.clip(a, 0, 1)])
    return Image.fromarray((rgba * 255 + 0.5).astype(np.uint8), "RGBA")


def flame_puff(n=128):
    """A billowing ball of burning gas: a lumpy disc, hotter inside, its rim torn by noise."""
    x, y = grid(n)
    r = np.sqrt(x * x + y * y)
    ang = np.arctan2(y, x)
    big = tile_noise(n, n, 3, 3, 4)
    fine = tile_noise(n, n, 8, 8, 4)
    # A rim that wobbles with the angle, so the puff is not a disc.
    rim = 0.86 + 0.05 * np.sin(ang * 3 + RNG.random() * 6) + 0.04 * np.sin(ang * 5 + RNG.random() * 6) + 0.2 * (big - 0.5)
    body = 1 - smoothstep(rim * 0.45, rim, r)
    churn = 0.62 + 0.6 * (fine - 0.5) + 0.5 * (big - 0.5)
    lum = body * np.clip(churn, 0, 1.2) * (0.7 + 0.3 * (1 - smoothstep(0, 0.7, r)))
    lum *= 1 - smoothstep(0.86, 0.99, r)            # always black at the cell edge
    lum = np.clip(lum * 1.3, 0, 1)
    return lum, lum


def smoke_puff(n=128):
    x, y = grid(n)
    r = np.sqrt(x * x + y * y)
    big = tile_noise(n, n, 3, 3, 4)
    fine = tile_noise(n, n, 7, 7, 4)
    rim = 0.7 + 0.25 * (big - 0.5)
    a = (1 - smoothstep(rim * 0.2, rim, r)) * (0.65 + 0.7 * (fine - 0.5))
    a *= 1 - smoothstep(0.85, 0.98, r)
    return np.ones_like(a), np.clip(a * 0.95, 0, 1)


def glow(n=128):
    x, y = grid(n)
    r = np.sqrt(x * x + y * y)
    g = np.exp(-(r / 0.38) ** 2) * (1 - smoothstep(0.85, 1.0, r))
    return g, g


def spark(n=128):
    x, y = grid(n)
    r = np.sqrt(x * x + y * y)
    g = np.exp(-(r / 0.10) ** 2) + 0.35 * np.exp(-(r / 0.35) ** 2)
    g *= 1 - smoothstep(0.8, 1.0, r)
    g = np.clip(g, 0, 1)
    return g, g


def tongue(n=128, fork=False):
    """A flame tongue: wide at the base (bottom), licking up to a wavering tip."""
    x, y = grid(n)
    up = (1 - y) / 2                                 # 0 at the bottom, 1 at the top
    noise = tile_noise(n, n, 4, 4, 4)
    sway = 0.12 * np.sin(up * 5.5 + 1.3) * up
    half = 0.55 * (1 - up) ** 0.7 + 0.02
    if fork:
        half *= 1.2
    d = np.abs(x - sway) / np.maximum(half, 1e-3)
    body = (1 - smoothstep(0.5, 1.0, d + 0.35 * (noise - 0.5))) * (1 - smoothstep(0.75, 1.0, up))
    body *= smoothstep(0.0, 0.3, up)
    lum = np.clip(body * (0.6 + 0.6 * (1 - up)) * (0.75 + 0.5 * (noise - 0.5)), 0, 1)
    return lum, lum


def sprites():
    img = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    for i in range(4):
        img.paste(to_img(*flame_puff()), (i * 128, 0))
        img.paste(to_img(*smoke_puff()), (i * 128, 128))
    img.paste(to_img(*glow()), (0, 256))
    img.paste(to_img(*spark()), (128, 256))
    img.paste(to_img(*tongue()), (256, 256))
    img.paste(to_img(*tongue(fork=True)), (384, 256))
    return img


def body():
    """Streaming flame along v: noise stretched along the jet, a bright core down the middle, black edges."""
    w, h = 128, 512
    n1 = tile_noise(w, h, 4, 6, 5)
    n2 = tile_noise(w, h, 8, 20, 4)
    u = (np.arange(w) + 0.5) / w * 2 - 1
    across = np.exp(-(u / 0.5) ** 2)[None, :]
    edge = (1 - smoothstep(0.7, 0.98, np.abs(u)))[None, :]
    streak = np.clip(0.45 + 0.9 * (n1 - 0.5) + 0.6 * (n2 - 0.5), 0, 1.3)
    lum = np.clip(across * streak * edge * 1.25, 0, 1)
    return to_img(lum, lum)


def shimmer():
    """Heat haze: thin wavering strands, some lighter, some darker, faint."""
    w, h = 128, 256
    n = tile_noise(w, h, 4, 4, 4)
    u = (np.arange(w) + 0.5) / w
    v = (np.arange(h) + 0.5) / h
    uu, vv = np.meshgrid(u, v)
    n2 = tile_noise(w, h, 3, 6, 3)
    warp = 0.05 * np.sin(vv * np.pi * 2 * 2 + uu * 5) + 0.35 * (n2 - 0.5)
    strands = 0.5 + 0.5 * np.sin((uu + warp) * np.pi * 2 * 6)
    strands = strands ** 4 * smoothstep(0.3, 0.7, n)
    edge = np.sin(uu * np.pi) ** 1.5
    a = np.clip(strands * edge * (0.6 + 0.8 * (n - 0.5)), 0, 1) * 0.55
    light = 0.82 + 0.18 * (n > 0.5)
    return to_img(np.ones_like(a) * light, a)


def scorch():
    n = 128
    big = tile_noise(n, n, 3, 3, 5)
    fine = tile_noise(n, n, 12, 12, 3)
    crack = np.abs(tile_noise(n, n, 5, 5, 3, 0.45) - 0.5)
    cracks = 1 - smoothstep(0.0, 0.035, crack)
    tone = 0.035 + 0.07 * big + 0.03 * fine
    rgb = np.dstack([tone * 1.15, tone * 0.95, tone * 0.8]) * (1 - 0.3 * cracks[..., None])
    a = np.clip(0.6 + 0.6 * (big - 0.5) + 0.35 * (fine - 0.5) + 0.1 * cracks, 0.3, 1)
    return to_img(rgb, a)


def embers():
    n = 128
    big = tile_noise(n, n, 4, 4, 4)
    fine = tile_noise(n, n, 16, 16, 3)
    crack = np.abs(tile_noise(n, n, 5, 5, 3, 0.45) - 0.5)
    lines = (1 - smoothstep(0.0, 0.04, crack)) * smoothstep(0.3, 0.6, big)
    specks = smoothstep(0.6, 0.8, fine) * smoothstep(0.25, 0.55, big)
    heat = np.clip(lines * 0.9 + specks, 0, 1)
    rgb = np.dstack([heat, heat ** 1.6 * 0.55, heat ** 3 * 0.18])
    return to_img(rgb, heat)


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, img in (("dragon_fire_sprites.png", sprites()), ("dragon_fire_body.png", body()),
                      ("dragon_fire_shimmer.png", shimmer()), ("dragon_scorch.png", scorch()),
                      ("dragon_embers.png", embers())):
        img.save(os.path.join(OUT, name))
        print("wrote", name, img.size)


if __name__ == "__main__":
    main()
