"""Paint the Basin of Despair and Crystal Goblet textures (16x16).

    python build_scripts/generate_basin_textures.py

Writes textures/block/: despair_basin_stone (blackstone-ish walls), despair_basin_rim (polished top),
despair_basin_liquid (bright glowing green), despair_basin_floor (dry dark stone),
despair_basin_locket (gold) and crystal_goblet (pale translucent crystal).
"""

import os
import random

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "assets", "hogwartsmagic", "textures", "block")


def shade(base, delta):
    return tuple(max(0, min(255, c + delta)) for c in base)


def noisy(base, seed, spread, alpha=False):
    rng = random.Random(seed)
    image = Image.new("RGBA", (16, 16))
    px = image.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = shade(base, rng.randint(-spread, spread)) + (255,)
    return image


def stone():
    image = noisy((38, 36, 42), 21, 6)
    px = image.load()
    for i in range(16):
        px[i, 7] = shade(px[i, 7][:3], -14) + (255,)
        px[i, 15] = shade(px[i, 15][:3], -14) + (255,)
    for y, off in ((0, 4), (8, 11)):
        for dy in range(7):
            px[off, y + dy] = shade(px[off, y + dy][:3], -12) + (255,)
    return image


def rim():
    image = noisy((52, 50, 58), 22, 5)
    px = image.load()
    for i in range(16):
        for e in (0, 15):
            px[i, e] = shade(px[i, e][:3], -16) + (255,)
            px[e, i] = shade(px[e, i][:3], -16) + (255,)
    return image


def liquid():
    rng = random.Random(23)
    image = Image.new("RGBA", (16, 16))
    px = image.load()
    for y in range(16):
        for x in range(16):
            wave = ((x * 3 + y * 5) % 11) / 11.0
            g = int(205 + 40 * wave + rng.randint(-6, 6))
            px[x, y] = (40 + int(30 * wave), min(255, g), 90 + int(30 * wave), 255)
    return image


def floor():
    image = noisy((26, 25, 30), 24, 4)
    px = image.load()
    for (x, y) in ((3, 4), (9, 10), (12, 2), (5, 13)):
        px[x, y] = (18, 18, 22, 255)
    return image


def locket():
    image = Image.new("RGBA", (16, 16), (196, 154, 36, 255))
    px = image.load()
    rng = random.Random(25)
    for y in range(16):
        for x in range(16):
            px[x, y] = shade((196, 154, 36), rng.randint(-14, 14)) + (255,)
    for i in range(16):
        px[i, 0] = (236, 200, 90, 255)
        px[0, i] = (236, 200, 90, 255)
        px[i, 15] = (130, 98, 20, 255)
        px[15, i] = (130, 98, 20, 255)
    # Emerald "S" gleam in the middle.
    for (x, y) in ((6, 6), (7, 6), (8, 6), (6, 7), (7, 8), (8, 9), (6, 9), (7, 9)):
        px[x, y] = (40, 170, 90, 255)
    return image


def goblet():
    rng = random.Random(26)
    image = Image.new("RGBA", (16, 16))
    px = image.load()
    for y in range(16):
        for x in range(16):
            v = rng.randint(-8, 8)
            px[x, y] = (205 + v, 228 + v // 2, 240, 190)
    for i in range(16):
        px[i, 0] = (245, 252, 255, 230)
        px[0, i] = (245, 252, 255, 230)
    return image


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, image in (("despair_basin_stone", stone()), ("despair_basin_rim", rim()),
                        ("despair_basin_liquid", liquid()), ("despair_basin_floor", floor()),
                        ("despair_basin_locket", locket()), ("crystal_goblet", goblet())):
        image.save(os.path.join(OUT, name + ".png"))
        print("wrote", name)


if __name__ == "__main__":
    main()
