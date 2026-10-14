"""Paint the Hogwarts Assignment scroll icons (16x16), one per seal.

    python build_scripts/generate_assignment_textures.py

Writes textures/item/assignment.png (the Common scroll, also the fallback) and
assignment_uncommon/rare/epic/dark.png. The seal colours match the Tomes of Mastery tiers:
white, yellow, aqua, light purple; Dark Orders are black parchment with a green seal.
"""

import os

from PIL import Image

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                       "assets", "hogwartsmagic", "textures", "item")

OUTLINE = (74, 46, 23, 255)
VARIANTS = {
    "assignment": {"paper": (234, 215, 168), "shade": (201, 174, 120), "ink": (122, 90, 55), "seal": (232, 232, 232), "seal_dark": (168, 168, 168)},
    "assignment_uncommon": {"paper": (234, 215, 168), "shade": (201, 174, 120), "ink": (122, 90, 55), "seal": (232, 197, 71), "seal_dark": (168, 132, 30)},
    "assignment_rare": {"paper": (234, 215, 168), "shade": (201, 174, 120), "ink": (122, 90, 55), "seal": (85, 214, 224), "seal_dark": (36, 140, 156)},
    "assignment_epic": {"paper": (234, 215, 168), "shade": (201, 174, 120), "ink": (122, 90, 55), "seal": (199, 125, 255), "seal_dark": (128, 64, 186)},
    "assignment_dark": {"paper": (52, 49, 58), "shade": (34, 32, 38), "ink": (98, 196, 116), "seal": (47, 168, 79), "seal_dark": (22, 98, 44)},
}


def paint(colors):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    paper = colors["paper"] + (255,)
    shade = colors["shade"] + (255,)
    ink = colors["ink"] + (255,)

    # The sheet between the two rolls.
    for y in range(4, 12):
        for x in range(3, 13):
            px[x, y] = paper
        px[2, y] = OUTLINE
        px[13, y] = OUTLINE
        px[12, y] = shade
    # Lines of writing.
    for y, (a, b) in ((5, (4, 11)), (7, (4, 10)), (9, (4, 8))):
        for x in range(a, b + 1):
            if (x + y) % 3 != 0:
                px[x, y] = ink
    # Top and bottom rolls: outline, highlight, body, shadow.
    for top in (2, 11):
        for x in range(1, 15):
            px[x, top] = OUTLINE
            px[x, top + 3] = OUTLINE
        for x in range(2, 14):
            px[x, top + 1] = tuple(min(255, c + 18) for c in colors["paper"]) + (255,)
            px[x, top + 2] = shade
        for y in range(top, top + 4):
            px[1, y] = OUTLINE
            px[14, y] = OUTLINE
        px[1, top] = (0, 0, 0, 0)
        px[14, top] = (0, 0, 0, 0)
        px[1, top + 3] = (0, 0, 0, 0)
        px[14, top + 3] = (0, 0, 0, 0)
    # Wax seal over the bottom right, with a ribbon tail.
    seal = colors["seal"] + (255,)
    seal_dark = colors["seal_dark"] + (255,)
    for (x, y) in ((10, 8), (11, 8), (9, 9), (10, 9), (11, 9), (12, 9), (9, 10), (10, 10), (11, 10), (12, 10), (10, 11), (11, 11)):
        px[x, y] = seal
    for (x, y) in ((12, 10), (11, 11), (10, 10)):
        px[x, y] = seal_dark
    px[10, 12] = seal_dark
    px[11, 13] = seal_dark
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for name, colors in VARIANTS.items():
        path = os.path.join(OUT_DIR, name + ".png")
        paint(colors).save(path)
        print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    main()
