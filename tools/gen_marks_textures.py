"""Generates the Glacius frost textures for client/fx/marks (tileable 128x128):
marks_frost.png  pale blue-white rime, crystalline ridges and needles, straight alpha
marks_glint.png  black with a few four-point star glints, for the additive pass
Run: python tools/gen_marks_textures.py
"""
import os
import numpy as np
from PIL import Image

N = 128
rng = np.random.default_rng(7)
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "hogwartsmagic",
                   "textures", "fx")
os.makedirs(OUT, exist_ok=True)


def voronoi_ridges(cells):
    pts = rng.random((cells, 2)) * N
    ys, xs = np.mgrid[0:N, 0:N]
    d = []
    for px, py in pts:
        dx = np.abs(xs - px)
        dy = np.abs(ys - py)
        dx = np.minimum(dx, N - dx)
        dy = np.minimum(dy, N - dy)
        d.append(np.sqrt(dx * dx + dy * dy))
    d = np.sort(np.stack(d), axis=0)
    return d[1] - d[0]  # small near the cell borders


def blur_wrap(a, k):
    for _ in range(k):
        a = (a + np.roll(a, 1, 0) + np.roll(a, -1, 0) + np.roll(a, 1, 1) + np.roll(a, -1, 1)) / 5.0
    return a


ridge = voronoi_ridges(46)
ridge = np.clip(1 - ridge / 5.0, 0, 1)
ridge2 = np.clip(1 - voronoi_ridges(150) / 3.0, 0, 1)
noise = blur_wrap(rng.random((N, N)), 3)
noise = (noise - noise.min()) / (noise.max() - noise.min())

alpha = 0.22 + 0.4 * noise + 0.55 * ridge + 0.25 * ridge2
alpha = np.clip(alpha, 0, 1)
rgb = np.zeros((N, N, 3))
rgb[..., 0] = 0.72 + 0.22 * ridge + 0.06 * noise
rgb[..., 1] = 0.88 + 0.12 * ridge
rgb[..., 2] = 1.0
img = np.dstack([rgb, alpha])
Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, "marks_frost.png"))

glint = np.zeros((N, N, 3))
for _ in range(7):
    cx, cy = rng.integers(8, N - 8, 2)
    arm = int(rng.integers(4, 8))
    for i in range(-arm, arm + 1):
        f = 1 - abs(i) / (arm + 1)
        for (x, y) in ((cx + i, cy), (cx, cy + i)):
            glint[y, x] = np.maximum(glint[y, x], [0.75 * f, 0.92 * f, 1.0 * f])
    glint[cy, cx] = [1, 1, 1]
    for (x, y) in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
        glint[y, x] = np.maximum(glint[y, x], [0.8, 0.95, 1.0])
a = np.ones((N, N, 1))
Image.fromarray((np.clip(np.concatenate([glint, a], axis=2), 0, 1) * 255).astype(np.uint8), "RGBA").save(
    os.path.join(OUT, "marks_glint.png"))
print("ok")
