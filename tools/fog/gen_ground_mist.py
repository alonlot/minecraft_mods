"""Generate the ground-mist particle sprites (textures/particle/ground_mist_0..3.png).

White RGB, all shape in alpha: a wide soft ellipse (mist lies flatter than it is tall) broken up by
fractal noise, so overlapping wisps read as one uneven body of mist rather than a stack of discs.
64x64 so a 3-4 block quad stays soft instead of showing pixels. Deterministic (fixed seeds).

    python tools/fog/gen_ground_mist.py
"""
import math
import os
import random

from PIL import Image

SIZE = 64
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources",
                   "assets", "hogwartsmagic", "textures", "particle")


def value_noise(seed, cells):
    rnd = random.Random(seed)
    grid = [[rnd.random() for _ in range(cells + 1)] for _ in range(cells + 1)]
    for i in range(cells + 1):          # tile the edges so octaves do not seam
        grid[i][cells] = grid[i][0]
        grid[cells][i] = grid[0][i]

    def sample(u, v):
        x, y = u * cells, v * cells
        x0, y0 = int(x) % cells, int(y) % cells
        fx, fy = x - int(x), y - int(y)
        sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = grid[y0][x0] + (grid[y0][x0 + 1] - grid[y0][x0]) * sx
        b = grid[y0 + 1][x0] + (grid[y0 + 1][x0 + 1] - grid[y0 + 1][x0]) * sx
        return a + (b - a) * sy

    return sample


def sprite(seed):
    octaves = [(value_noise(seed * 7 + k, 3 * 2 ** k), 0.5 ** k) for k in range(4)]
    norm = sum(w for _, w in octaves)
    rnd = random.Random(seed)
    stretch = 1.35 + rnd.random() * 0.3            # wider than tall
    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    for j in range(SIZE):
        for i in range(SIZE):
            u, v = (i + 0.5) / SIZE, (j + 0.5) / SIZE
            dx, dy = (u - 0.5) * 2.0, (v - 0.5) * 2.0 * stretch
            r = math.sqrt(dx * dx + dy * dy)
            falloff = max(0.0, 1.0 - r) ** 1.6     # soft all the way to the rim
            n = sum(f(u, v) * w for f, w in octaves) / norm
            density = max(0.0, (n - 0.28) / 0.72) ** 1.3
            a = falloff * density * 1.9
            px[i, j] = (255, 255, 255, int(max(0.0, min(1.0, a)) * 255))
    return img


if __name__ == "__main__":
    for k in range(4):
        path = os.path.normpath(os.path.join(OUT, f"ground_mist_{k}.png"))
        sprite(11 + k).save(path)
        print(path)
