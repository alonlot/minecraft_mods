"""Generate the Boggart shadow texture.

A hidden Boggart has no shape of its own. BoggartRenderer draws a vex through a
buffer that forces every render type to entityTranslucent(boggart_shadow.png),
so this texture is not a model skin. It is a 64x64 tile of dark violet smoke
that is sampled wherever the vex UVs land. Everything about it is soft: a
smooth value-noise base, a few darker swirls, and alpha between 150 and 230 so
the shape stays see-through but still reads as a solid dark blob.
"""

import math
import os
import random

from PIL import Image

random.seed(1993)  # Prisoner of Azkaban

OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "hogwartsmagic",
    "textures", "entity", "boggart_shadow.png",
)

SIZE = 64

DARK = (0x1B, 0x14, 0x26)
LIGHT = (0x3A, 0x2A, 0x4C)
ALPHA_MIN = 150
ALPHA_MAX = 230


def clamp(v):
    return max(0, min(255, int(round(v))))


def lerp(a, b, t):
    return a + (b - a) * t


def smoothstep(t):
    return t * t * (3.0 - 2.0 * t)


def value_noise_grid(cells):
    """A tileable grid of random lattice values."""
    return [[random.random() for _ in range(cells)] for _ in range(cells)]


def sample(grid, x, y, cells):
    """Bilinear sample of a tileable lattice at texture coordinates 0..SIZE."""
    fx = x / SIZE * cells
    fy = y / SIZE * cells
    x0 = int(math.floor(fx)) % cells
    y0 = int(math.floor(fy)) % cells
    x1 = (x0 + 1) % cells
    y1 = (y0 + 1) % cells
    tx = smoothstep(fx - math.floor(fx))
    ty = smoothstep(fy - math.floor(fy))
    top = lerp(grid[y0][x0], grid[y0][x1], tx)
    bottom = lerp(grid[y1][x0], grid[y1][x1], tx)
    return lerp(top, bottom, ty)


def smoke(x, y, octaves):
    """Fractal value noise in 0..1, tileable in both directions."""
    total = 0.0
    weight = 0.0
    amp = 1.0
    for grid, cells in octaves:
        total += sample(grid, x, y, cells) * amp
        weight += amp
        amp *= 0.5
    return total / weight


def main():
    octaves = [(value_noise_grid(c), c) for c in (4, 8, 16)]

    # A few darker swirls: short spiral arcs stamped with a soft falloff.
    swirls = []
    for _ in range(5):
        cx = random.uniform(0, SIZE)
        cy = random.uniform(0, SIZE)
        radius = random.uniform(6.0, 12.0)
        turns = random.uniform(0.6, 1.2)
        phase = random.uniform(0, math.tau)
        swirls.append((cx, cy, radius, turns, phase))

    dark_mask = [[0.0] * SIZE for _ in range(SIZE)]
    for cx, cy, radius, turns, phase in swirls:
        steps = 120
        for i in range(steps):
            t = i / steps
            angle = phase + t * turns * math.tau
            r = radius * (0.25 + 0.75 * t)
            px = cx + math.cos(angle) * r
            py = cy + math.sin(angle) * r
            for dy in range(-2, 3):
                for dx in range(-2, 3):
                    d = math.hypot(dx, dy)
                    if d > 2.2:
                        continue
                    ix = int(px + dx) % SIZE
                    iy = int(py + dy) % SIZE
                    strength = (1.0 - d / 2.2) * 0.7
                    dark_mask[iy][ix] = max(dark_mask[iy][ix], strength)

    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            n = smoke(x, y, octaves)
            # Pull the noise toward the middle so the tile is mostly mid-dark.
            n = 0.5 + (n - 0.5) * 1.4
            n = max(0.0, min(1.0, n))
            n *= 1.0 - dark_mask[y][x]
            grain = random.gauss(0.0, 0.025)
            n = max(0.0, min(1.0, n + grain))

            r = lerp(DARK[0], LIGHT[0], n)
            g = lerp(DARK[1], LIGHT[1], n)
            b = lerp(DARK[2], LIGHT[2], n)
            # Denser where the smoke is darker.
            a = lerp(ALPHA_MAX, ALPHA_MIN, n * 0.8) + random.gauss(0.0, 6.0)
            a = max(ALPHA_MIN, min(ALPHA_MAX, a))
            px[x, y] = (clamp(r), clamp(g), clamp(b), clamp(a))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
