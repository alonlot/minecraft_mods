"""Paint the Memory of the Cave item texture (16x16): a silver-blue vial of swirling memory.

    python build_scripts/generate_cave_memory_texture.py

Writes textures/item/cave_memory.png. Flat shading, one-pixel dark outline, one highlight.
"""

import math
import os

from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "assets", "hogwartsmagic", "textures", "item", "cave_memory.png")

GLASS = (190, 205, 215, 255)
CORK = (120, 86, 56, 255)
SWIRL_DARK = (74, 112, 160, 255)
SWIRL_MID = (130, 170, 210, 255)
SWIRL_LIGHT = (214, 232, 248, 255)
OUTLINE = (30, 40, 58, 255)


def main():
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    # Neck and cork.
    d.rectangle((6, 1, 9, 2), fill=CORK)
    d.rectangle((6, 3, 9, 5), fill=GLASS)
    # Round belly.
    d.ellipse((2, 5, 13, 14), fill=GLASS)
    px = image.load()
    # Silver-blue memory filling the belly, with a diagonal swirl band.
    for y in range(6, 14):
        for x in range(3, 13):
            if px[x, y][3] == 0 or px[x, y] != GLASS:
                continue
            wave = math.sin((x + y) * 0.9)
            px[x, y] = SWIRL_LIGHT if wave > 0.6 else SWIRL_MID if wave > -0.3 else SWIRL_DARK
    # Fluid in the neck.
    for y in (4, 5):
        for x in (7, 8):
            px[x, y] = SWIRL_MID
    px[4, 8] = (255, 255, 255, 255)  # glint
    px[7, 3] = (255, 255, 255, 255)
    # Outline.
    src = image.copy().load()
    for y in range(16):
        for x in range(16):
            if src[x, y][3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and src[nx, ny][3] != 0:
                    px[x, y] = OUTLINE
                    break
    image.save(OUT)
    print("wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
