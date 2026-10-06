"""Paint the Locket cave block textures (16x16): Concealed Rock and Blood Seal.

    python build_scripts/generate_cave_block_textures.py

Writes textures/block/concealed_rock.png (grey deepslate-tile look, a touch cooler than vanilla so
it is a believable cave wall) and textures/block/blood_seal.png (near-black stone with faint red veins).
The hidden chain reuses the vanilla chain texture, so it has none here.
"""

import os
import random

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "assets", "hogwartsmagic", "textures", "block")


def shade(base, delta):
    return tuple(max(0, min(255, c + delta)) for c in base)


def concealed_rock():
    rng = random.Random(7)
    base = (74, 76, 82)
    image = Image.new("RGB", (16, 16), base)
    px = image.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = shade(base, rng.randint(-6, 6))
    # Four 8x8 tiles with a dark mortar line and a lighter top-left bevel.
    for i in range(16):
        for line in (0, 8):
            px[i, line] = shade(base, -26)
            px[line, i] = shade(base, -26)
    for ty in (0, 8):
        for tx in (0, 8):
            for i in range(1, 8):
                px[tx + i, ty + 1] = shade(px[tx + i, ty + 1], 9)
                px[tx + 1, ty + i] = shade(px[tx + 1, ty + i], 9)
    return image


def blood_seal():
    rng = random.Random(13)
    base = (30, 28, 32)
    image = Image.new("RGB", (16, 16), base)
    px = image.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = shade(base, rng.randint(-5, 5))
    # Faint wandering red veins.
    for start_x in (3, 11):
        x, y = start_x, 0
        while y < 16:
            px[x % 16, y] = (92, 18, 22)
            if rng.random() < 0.5:
                px[(x + 1) % 16, y] = (60, 16, 20)
            x += rng.choice((-1, 0, 0, 1))
            y += 1
    return image


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    concealed_rock().save(os.path.join(OUT, "concealed_rock.png"))
    blood_seal().save(os.path.join(OUT, "blood_seal.png"))
    print("wrote concealed_rock.png and blood_seal.png")
