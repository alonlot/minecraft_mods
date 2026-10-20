"""Textures for the Great Hall's enchanted ceiling (the enchanted_ceiling memory effect, client/pensieve/EnchantedCeiling).

Outputs (assets/hogwartsmagic/textures/entity/):
  ceiling_sky.png       256x256, tiling, opaque. Near-white mottling (0.78..1.0) with a faint violet/teal drift;
                        multiplied by the renderer's night gradient (#0B1030 edges -> #1A2350 centre), so the sky
                        is never a flat fill.
  ceiling_clouds_a.png  128x128, tiling, straight alpha <= 0.35. Long soft wisps, grey-blue, the far layer.
  ceiling_clouds_b.png  128x128, tiling, straight alpha <= 0.35. Broken puffier wisps, the near layer.
  ceiling_star.png      64x64, premultiplied for additive blending: a white-hot core, a soft halo and four faint
                        diffraction spikes. Small stars use it shrunk (the halo is then sub-pixel); bright ones also
                        get a big faint copy as their glow.
  ceiling_dot.png       16x16, premultiplied for additive blending: a plain soft round dot. The faint stars use it;
                        a 64 px sprite shrunk to 3 px with no mipmaps would sparkle as the camera moves.

Every tiling texture is filtered noise made in the frequency domain, so it wraps without a seam by construction.

Run:  python build_scripts/generate_ceiling_textures.py
"""
import os

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets",
                   "hogwartsmagic", "textures", "entity")
RNG = np.random.default_rng(1991)


def periodic_noise(size, slope, stretch=1.0, low_cut=1.0, soft=None):
    """Tileable fractal noise, 0..1: white noise shaped by a 1/f^slope spectrum. stretch > 1 draws features out
    along x (wisps); soft, in cycles per tile, rolls off the fine grain so edges stay feathery."""
    white = RNG.standard_normal((size, size))
    fy = np.fft.fftfreq(size)[:, None] * size
    fx = np.fft.fftfreq(size)[None, :] * size
    f = np.sqrt((fx * stretch) ** 2 + (fy / stretch) ** 2)
    f[0, 0] = 1.0
    amp = 1.0 / np.power(np.maximum(f, low_cut), slope)
    if soft:
        amp *= np.exp(-(f / soft) ** 2)
    amp[0, 0] = 0.0
    n = np.real(np.fft.ifft2(np.fft.fft2(white) * amp))
    n -= n.min()
    return n / n.max()


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def save(name, rgba):
    img = Image.fromarray(np.clip(rgba * 255.0 + 0.5, 0, 255).astype(np.uint8), "RGBA")
    path = os.path.join(OUT, name)
    img.save(path)
    print("wrote", os.path.normpath(path), img.size)


def sky():
    size = 256
    base = periodic_noise(size, 1.6, soft=40)
    fine = periodic_noise(size, 1.1, soft=90)
    hue = periodic_noise(size, 2.2)
    v = 0.70 + 0.30 * (0.7 * base + 0.3 * fine)
    # A faint drift between violet and teal, so the dark has depth rather than one flat navy.
    r = v * (0.94 + 0.10 * hue)
    g = v * (0.97 + 0.03 * (1 - hue))
    b = v * (1.0 - 0.04 * hue)
    m = max(r.max(), g.max(), b.max())
    rgba = np.stack([r / m, g / m, b / m, np.ones_like(v)], axis=-1)
    save("ceiling_sky.png", rgba)


def clouds(name, stretch, lo, hi, slope, tint_shift):
    size = 128
    shape = periodic_noise(size, slope, stretch=stretch, low_cut=2.0, soft=22)
    detail = periodic_noise(size, 1.0, stretch=stretch, soft=40)
    n = 0.82 * shape + 0.18 * detail
    a = smoothstep(lo, hi, n)
    a = a * (0.75 + 0.25 * detail)                 # feathered inner texture, never a solid block
    a = 0.35 * a / a.max()
    light = smoothstep(lo, hi + 0.15, n)
    # Grey-blue, a touch lighter where the wisp is thickest (moonlit tops).
    r = 0.55 + 0.25 * light + tint_shift
    g = 0.60 + 0.25 * light + tint_shift
    b = 0.75 + 0.22 * light
    rgba = np.stack([np.clip(r, 0, 1), np.clip(g, 0, 1), np.clip(b, 0, 1), a], axis=-1)
    save(name, rgba)


def star():
    size = 64
    c = (size - 1) / 2.0
    y, x = np.mgrid[0:size, 0:size]
    dx, dy = (x - c) / c, (y - c) / c
    r = np.sqrt(dx * dx + dy * dy)
    core = np.exp(-(r / 0.07) ** 2)
    halo = np.exp(-(r / 0.30) ** 2) * 0.45
    outer = np.exp(-(r / 0.65) ** 2) * 0.12
    spikes = (np.exp(-(dx / 0.025) ** 2) + np.exp(-(dy / 0.025) ** 2)) * np.exp(-(r / 0.55) ** 2) * 0.35
    v = core + halo + outer + spikes
    v *= smoothstep(1.0, 0.85, r)                  # exactly black at the quad edge
    v = np.clip(v / v.max(), 0, 1)
    # Premultiplied: colour carries the intensity, a faint cold tint away from the white core.
    rr = v * (0.88 + 0.12 * core)
    gg = v * (0.93 + 0.07 * core)
    bb = v
    rgba = np.stack([rr, gg, bb, v], axis=-1)
    save("ceiling_star.png", rgba)


def dot():
    size = 16
    c = (size - 1) / 2.0
    y, x = np.mgrid[0:size, 0:size]
    r = np.sqrt((x - c) ** 2 + (y - c) ** 2) / c
    v = np.exp(-(r / 0.45) ** 2) * smoothstep(1.0, 0.8, r)
    v = np.clip(v / v.max(), 0, 1)
    save("ceiling_dot.png", np.stack([v, v, v, v], axis=-1))


def contact_sheet():
    """A preview for a human: each texture tiled 2x2 over the night gradient, side by side."""
    names = ["ceiling_sky.png", "ceiling_clouds_a.png", "ceiling_clouds_b.png", "ceiling_star.png"]
    tiles = []
    for n in names:
        im = Image.open(os.path.join(OUT, n)).convert("RGBA")
        w, h = im.size
        sheet = Image.new("RGBA", (w * 2, h * 2), (19, 27, 64, 255))
        for i in range(2):
            for j in range(2):
                sheet.alpha_composite(im, (i * w, j * h)) if n != "ceiling_star.png" else None
        if n == "ceiling_star.png":
            arr = np.asarray(im).astype(np.float32) / 255.0
            big = np.asarray(sheet).astype(np.float32) / 255.0
            arr = np.kron(arr, np.ones((2, 2, 1)))
            big[..., :3] = np.clip(big[..., :3] + arr[..., :3], 0, 1)
            sheet = Image.fromarray((big * 255).astype(np.uint8), "RGBA")
        if n == "ceiling_sky.png":
            arr = np.asarray(sheet).astype(np.float32) / 255.0
            arr[..., 0] *= 0x1A / 255.0
            arr[..., 1] *= 0x23 / 255.0
            arr[..., 2] *= 0x50 / 255.0
            sheet = Image.fromarray((arr * 255).astype(np.uint8), "RGBA")
        tiles.append(sheet.resize((256, 256), Image.LANCZOS))
    out = Image.new("RGBA", (256 * len(tiles), 256))
    for i, t in enumerate(tiles):
        out.paste(t, (i * 256, 0))
    return out


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    sky()
    clouds("ceiling_clouds_a.png", stretch=2.4, lo=0.42, hi=0.82, slope=1.6, tint_shift=0.0)
    clouds("ceiling_clouds_b.png", stretch=1.4, lo=0.50, hi=0.85, slope=1.5, tint_shift=0.03)
    star()
    dot()
    import sys
    if len(sys.argv) > 1:
        contact_sheet().save(sys.argv[1])
        print("preview", sys.argv[1])
