"""Hieroglyph sandstone and the revealed cursed glyph, 16x16, in vanilla sandstone's own palette.

    python build_scripts/generate_tomb_textures.py

The plain tile and the cursed tile must be the same picture: a cursed glyph is meant to be found
with Revelio, not by eye. Only the revealed state differs, with the cut lines glowing red.

The glyphs are cut into the stone, not painted on it, so each carved pixel is dark and the pixel
under it catches the light: the lower lip of a cut faces the sky. The frame is vanilla chiseled
sandstone's own, so the tile sits in a wall of cut and chiseled sandstone without a seam.
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'hogwartsmagic', 'textures', 'block')

DARK = (198, 174, 113)
MID = (209, 186, 138)
LIGHT = (237, 235, 203)
GRAIN = [(227, 219, 176), (231, 228, 187), (227, 219, 176), (227, 219, 176), (223, 214, 170)]
RED = (176, 32, 18)
EMBER = (250, 116, 52)

# Ankh, left: loop, cross-bar, stem.
ANKH = [(3, 2), (4, 2), (5, 2), (2, 3), (6, 3), (2, 4), (6, 4), (3, 5), (5, 5),
        (1, 6), (2, 6), (3, 6), (4, 6), (5, 6), (6, 6), (7, 6)] + [(4, y) for y in range(7, 13)]
# Eye of Horus, right: brow, lids, pupil, the tear line and the curl.
EYE = [(x, 3) for x in range(9, 15)] + [(x, 5) for x in range(10, 14)] + [(9, 6), (14, 6)] \
    + [(x, 7) for x in range(10, 14)] + [(11, 6), (12, 6)] \
    + [(10, 8), (10, 9), (9, 10)] + [(13, 8), (14, 9), (14, 10), (13, 10)]
# Water, under the eye.
WATER = [(9, 13), (10, 12), (11, 13), (12, 12), (13, 13), (14, 12)]
CUT = set(ANKH + EYE + WATER)


def grain(x, y):
    h = (x * 73856093) ^ (y * 19349663) ^ 0x5bd1e995
    return GRAIN[((h ^ (h >> 13)) & 0x7FFFFFFF) % len(GRAIN)]


def tile(revealed):
    im = Image.new('RGB', (16, 16))
    for y in range(16):
        for x in range(16):
            if y == 0:
                c = LIGHT
            elif y == 1 or y == 14:
                c = MID
            elif y == 15:
                c = DARK
            elif x == 0:
                c = (231, 228, 187)
            elif x == 15:
                c = (213, 196, 150)
            else:
                c = grain(x, y)
            im.putpixel((x, y), c)
    # The lower lip of every cut catches the light.
    for (x, y) in CUT:
        if (x, y + 1) not in CUT and y + 1 < 14:
            im.putpixel((x, y + 1), LIGHT)
    for (x, y) in CUT:
        im.putpixel((x, y), (EMBER if (x * 7 + y * 3) % 4 == 0 else RED) if revealed else DARK)
    return im


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    tile(False).save(os.path.join(OUT, 'hieroglyph_sandstone.png'))
    tile(True).save(os.path.join(OUT, 'cursed_glyph_revealed.png'))
    print('wrote hieroglyph_sandstone.png, cursed_glyph_revealed.png')
