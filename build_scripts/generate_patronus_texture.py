"""Draws textures/entity/patronus.png, the stag Patronus skin (64x64), to fit PatronusModel's UV layout.

A soft silver-blue gradient per box (pale at the top of each net, bluer toward the bottom), a little
brighter on the chest and the antlers. Run from the repo root; needs Pillow.
"""
from PIL import Image

OUT = "src/main/resources/assets/hogwartsmagic/textures/entity/patronus.png"

# (u, v, width, height, brightness boost) for each UV net in PatronusModel.
REGIONS = [
    (0, 0, 28, 15, 0.55),   # chest
    (28, 0, 22, 11, 0.0),   # waist
    (0, 15, 26, 13, 0.1),   # haunch
    (26, 15, 16, 13, 0.3),  # neck
    (42, 15, 18, 9, 0.35),  # skull
    (0, 28, 14, 7, 0.25),   # muzzle
    (14, 28, 8, 3, 0.3),    # ear
    (22, 28, 10, 5, 0.4),   # tail
    (32, 28, 12, 9, 0.0),   # front upper leg
    (44, 28, 8, 6, 0.0),    # front shin
    (52, 28, 8, 3, 0.2),    # hoof
    (0, 40, 12, 9, 0.0),    # hind upper leg
    (12, 40, 8, 7, 0.0),    # hind shin
    (56, 0, 4, 6, 0.9),     # antler tines
]

TOP = (226, 238, 255)
BOTTOM = (150, 190, 236)
WHITE = (250, 252, 255)


def lerp(a, b, t):
    return tuple(int(round(x + (y - x) * t)) for x, y in zip(a, b))


def main():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for u, v, w, h, boost in REGIONS:
        for y in range(h):
            t = y / max(1, h - 1)
            for x in range(w):
                # A faint sheen across the net so faces are not flat.
                sheen = 0.08 * (1.0 - abs((x / max(1, w - 1)) - 0.5) * 2.0)
                c = lerp(TOP, BOTTOM, t)
                c = lerp(c, WHITE, min(1.0, boost * (1.0 - 0.5 * t) + sheen))
                alpha = 240 if boost >= 0.9 else 214
                img.putpixel((u + x, v + y), c + (alpha,))
    img.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
