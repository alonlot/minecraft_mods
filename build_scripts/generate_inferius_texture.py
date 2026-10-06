"""Generate the placeholder Inferius skin: 64x64 zombie layout.

Pale grey skin, dark ribs on the torso, black matted hair, ragged dark cloth
on the lower body. Placeholder until the Blockbench model (Task 3) lands.
"""

import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                   "textures", "entity", "inferius.png")

SKIN = (0xB9, 0xBD, 0xB4)
SKIN_DARK = (0x8F, 0x94, 0x8B)
HAIR = (0x1B, 0x1B, 0x1E)
CLOTH = (0x3A, 0x3F, 0x3A)
RIB = (0x4A, 0x4D, 0x48)
EYE = (0x10, 0x10, 0x12)

rng = random.Random(7)


def shade(c, amt):
    return tuple(max(0, min(255, v + amt)) for v in c)


def fill(img, x, y, w, h, base, jitter=8):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            img.putpixel((xx, yy), shade(base, rng.randint(-jitter, jitter)) + (255,))


def box_uv(img, u, v, w, h, d, base, jitter=8):
    """Fill the six faces of a zombie-layout box whose UV origin is (u, v)."""
    fill(img, u + d, v, w, d, base, jitter)                   # top
    fill(img, u + d + w, v, w, d, shade(base, -20), jitter)   # bottom
    fill(img, u, v + d, d, h, base, jitter)                   # right
    fill(img, u + d, v + d, w, h, base, jitter)               # front
    fill(img, u + d + w, v + d, d, h, base, jitter)           # left
    fill(img, u + 2 * d + w, v + d, w, h, shade(base, -10), jitter)  # back


def main():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    # head 8x8x8 at (0,0): skin, hair on top and back, sunken eyes
    box_uv(img, 0, 0, 8, 8, 8, SKIN)
    fill(img, 8, 0, 8, 8, HAIR, 5)    # top
    fill(img, 24, 8, 8, 4, HAIR, 5)   # back of head
    fill(img, 8, 8, 8, 2, HAIR, 5)    # front fringe
    for ex in (10, 13):
        for dx in (0, 1):
            for dy in (12, 13):
                img.putpixel((ex + dx, dy), EYE + (255,))
    fill(img, 10, 15, 4, 1, SKIN_DARK, 3)  # slack mouth

    # torso 8x12x4 at (16,16): skin with dark ribs, ragged cloth at the hem
    box_uv(img, 16, 16, 8, 12, 4, SKIN)
    for ry in range(22, 30, 2):
        fill(img, 21, ry, 6, 1, RIB, 3)
    fill(img, 20, 28, 8, 4, CLOTH, 6)  # front lower cloth
    fill(img, 32, 28, 8, 4, CLOTH, 6)  # back lower cloth

    # arms 4x12x4 at (40,16) right and (32,48) left: skin, darker hands
    for (u, v) in ((40, 16), (32, 48)):
        box_uv(img, u, v, 4, 12, 4, SKIN)
        fill(img, u, v + 4 + 8, 16, 4, SKIN_DARK, 4)

    # legs 4x12x4 at (0,16) right and (16,48) left: cloth rags, skin below the knee
    for (u, v) in ((0, 16), (16, 48)):
        box_uv(img, u, v, 4, 12, 4, CLOTH, 7)
        fill(img, u, v + 4 + 8, 16, 4, SKIN, 6)
        for _ in range(6):  # ragged hem notches
            img.putpixel((u + 4 + rng.randint(0, 3), v + 4 + 7), SKIN + (255,))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
