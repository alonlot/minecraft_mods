"""Paint the Boggart's own-shape texture (textures/entity/boggart.png).

    python build_scripts/generate_boggart_texture.py

The model is client/model/BoggartModel.java: hand-placed box UV on a 64x64 sheet. Every box footprint
below must match that file (same texOffs, same sizes) or the parts come out striped with transparency.

Materials: one. The Boggart has no surface, so everything is the same dark violet smoke, noisier
towards the edges and thinner (lower alpha) on the tendrils so they fade into the floor. The head's
front face gets two pale lights where eyes would be; they are the one thing on it that is bright.
"""

import os
import random

from PIL import Image

random.seed(1993)

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "main", "resources", "assets", "hogwartsmagic", "textures", "entity", "boggart.png")

SIZE = 64
DARK = (0x1B, 0x14, 0x26)
LIGHT = (0x3A, 0x2A, 0x4C)
EYE = (0xC9, 0xB3, 0xF0)

# (u, v, width, height, depth, alpha_top, alpha_bottom)
BOXES = [
    (0, 0, 10, 14, 8, 235, 225),      # mass
    (36, 0, 8, 10, 6, 235, 230),      # upper
    (36, 16, 7, 7, 7, 240, 235),      # head
    (0, 30, 3, 3, 3, 220, 210),       # left wisp
    (12, 30, 3, 3, 3, 220, 210),      # right wisp
] + [(i * 6, 22, 2, 6, 1, 215, 90) for i in range(6)]  # tendrils fade out downwards


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def paint_box(px, u, v, w, h, d, a_top, a_bottom):
    """Box UV footprint: top/bottom caps in the first d rows, then the four sides below them."""
    width = 2 * (d + w)
    height = d + h
    for y in range(height):
        for x in range(width):
            # Caps occupy only the middle of the first d rows.
            if y < d and not (d <= x < d + 2 * w):
                continue
            n = random.random()
            shade = lerp(DARK, LIGHT, n * n)
            # Rows below the cap are the sides; fade alpha from top to bottom of the side.
            side_t = 0.0 if y < d else (y - d) / max(1, h - 1)
            alpha = int(round(a_top + (a_bottom - a_top) * side_t))
            alpha = max(0, min(255, alpha + random.randint(-12, 12)))
            px[u + x, v + y] = (*shade, alpha)


def main():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    for box in BOXES:
        paint_box(px, *box)
    # Head front face in box UV sits at (u + d, v + d), size w x h.
    hu, hv, hw, hh, hd = 36, 16, 7, 7, 7
    fx, fy = hu + hd, hv + hd
    for ex in (1, 4):
        for dx in range(2):
            for dy in range(2):
                px[fx + ex + dx, fy + 2 + dy] = (*EYE, 255)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("wrote", OUT, img.size)


if __name__ == "__main__":
    main()
