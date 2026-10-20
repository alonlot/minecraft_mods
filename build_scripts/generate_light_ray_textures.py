"""Textures for the light_rays memory effect (client/pensieve/LightRaysRenderer): god-rays through a doorway.

Outputs (assets/hogwartsmagic/textures/entity/), both premultiplied, white (the renderer tints them warm gold):
  light_ray.png   64x256. One shaft: across u a soft profile (zero at both edges) carrying fine lengthwise
                  streaks; along v a gentle uneven breath, so a shaft is never a smooth bar. The renderer fades it
                  along its length with vertex colour.
  light_glow.png  64x64. A soft rounded rectangle: peak in the middle, zero at every edge. The bright doorway gap
                  and the pool of light on the floor (v 0.5 at the door, v 1 at the far edge).

Motes reuse ceiling_dot.png (generate_ceiling_textures.py).

Run:  python build_scripts/generate_light_ray_textures.py [preview.png]
"""
import os
import sys

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets",
                   "hogwartsmagic", "textures", "entity")
RNG = np.random.default_rng(2207)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def noise1d(n, cutoff):
    """Periodic smooth 1-D noise in 0..1, keeping frequencies up to cutoff."""
    spec = np.fft.rfft(RNG.standard_normal(n))
    f = np.arange(spec.size)
    spec *= np.exp(-(f / cutoff) ** 2)
    spec[0] = 0
    v = np.fft.irfft(spec, n)
    v -= v.min()
    return v / v.max()


def save(name, v):
    v = np.clip(v, 0, 1)
    rgba = np.stack([v, v, v, v], axis=-1)
    img = Image.fromarray((rgba * 255 + 0.5).astype(np.uint8), "RGBA")
    img.save(os.path.join(OUT, name))
    print("wrote", name, img.size)
    return img


def ray():
    w, h = 64, 256
    u = (np.arange(w) + 0.5) / w
    across = np.exp(-((u - 0.5) / 0.26) ** 2) * smoothstep(0.0, 0.18, u) * smoothstep(1.0, 0.82, u)
    streaks = 0.5 + 0.5 * noise1d(w, 9)
    along = 0.8 + 0.2 * noise1d(h, 5)
    v = across[None, :] * streaks[None, :] * along[:, None]
    return save("light_ray.png", v / v.max())


def glow():
    n = 64
    c = (np.arange(n) + 0.5) / n * 2 - 1
    x, y = np.meshgrid(c, c)
    r = np.maximum(np.abs(x) ** 3 + np.abs(y) ** 3, 0) ** (1 / 3)   # a rounded square
    v = smoothstep(1.0, 0.0, r) ** 1.6              # the p=3 norm is >= max(|x|, |y|): zero at every edge
    return save("light_glow.png", v / v.max())


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    a, b = ray(), glow()
    if len(sys.argv) > 1:
        gold = np.array([1.0, 0.85, 0.63])
        sheet = np.zeros((256, 64 + 16 + 256, 3))
        sheet[:, :64] = np.asarray(a)[..., :3] / 255.0 * gold
        sheet[:, 80:] = np.asarray(b.resize((256, 256), Image.BILINEAR))[..., :3] / 255.0 * gold
        Image.fromarray((sheet * 255).astype(np.uint8)).save(sys.argv[1])
        print("preview", sys.argv[1])
