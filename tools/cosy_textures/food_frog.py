"""Chocolate frog entity texture (64x32), painted per box face to match ChocolateFrogModel's UV layout.

    python tools/cosy_textures/food_frog.py

Every cuboid at texOffs (u, v) with size (w, h, d) unfolds as: top (u+d, v, w, d), bottom (u+d+w, v, w, d),
right (u, v+d, d, h), front (u+d, v+d, w, h), left (u+d+w, v+d, d, h), back (u+2d+w, v+d, w, h).
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "textures", "entity",
                   "chocolate_frog.png")

CHOC = (107, 58, 26, 255)
CHOC_LIGHT = (138, 82, 38, 255)
CHOC_MID = (122, 70, 34, 255)
CHOC_DARK = (87, 48, 26, 255)
BELLY = (200, 146, 88, 255)
GOLD = (242, 211, 107, 255)
PUPIL = (37, 18, 10, 255)
FOIL = (233, 184, 58, 255)


def fill(px, x, y, w, h, c):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            px[xx, yy] = c


def cuboid(px, u, v, w, h, d, top, bottom, side, front, back):
    fill(px, u + d, v, w, d, top)
    fill(px, u + d + w, v, w, d, bottom)
    fill(px, u, v + d, d, h, side)
    fill(px, u + d, v + d, w, h, front)
    fill(px, u + d + w, v + d, d, h, side)
    fill(px, u + 2 * d + w, v + d, w, h, back)


def main():
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    # Body 5x3x6 at (0,0): chocolate, a gold-foil stripe down the back, a pale belly.
    cuboid(px, 0, 0, 5, 3, 6, CHOC_LIGHT, BELLY, CHOC_MID, CHOC, CHOC_DARK)
    fill(px, 6 + 2, 0, 1, 6, FOIL)  # stripe on the top face
    for (x, y) in ((6, 2), (9, 4), (7, 1)):  # a few darker flecks, like moulded chocolate
        px[x, y] = CHOC_DARK
    # Head 5x2x3 at (0,9): a mouth line across the front.
    cuboid(px, 0, 9, 5, 2, 3, CHOC_LIGHT, BELLY, CHOC_MID, CHOC, CHOC_DARK)
    fill(px, 3, 12, 5, 1, CHOC_DARK)  # the mouth
    px[4, 11] = CHOC_DARK  # nostrils
    px[6, 11] = CHOC_DARK
    # Eyes 1x1x1 at (24,0): gold, with the dark pupil on the front face.
    cuboid(px, 24, 0, 1, 1, 1, GOLD, GOLD, GOLD, PUPIL, GOLD)
    # Hind legs 2x1x4 at (0,16), forelegs 1x2x1 at (14,16).
    cuboid(px, 0, 16, 2, 1, 4, CHOC_LIGHT, BELLY, CHOC_MID, CHOC, CHOC_DARK)
    cuboid(px, 14, 16, 1, 2, 1, CHOC_LIGHT, BELLY, CHOC_MID, CHOC, CHOC_DARK)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
