"""The brick of the wall behind the Leaky Cauldron.

    python build_scripts/generate_archway_texture.py

Vanilla bricks are too clean and far too orange for this wall.  The film's is a sooty London
stock brick -- brown-grey, unevenly fired so no two bricks are the same shade, with a pale lime
mortar that has weathered darker at the joints.  It also has to tile seamlessly in both axes, since
the wall is sixteen wide and seven high and any repeat that reads as a repeat will read as a repeat
across the whole panel.

Two textures come out of this: the wall itself, and a `_lit` variant with the mortar glowing, which
the archway animation swaps in as the seam of light runs across the wall before it opens.
"""

import os

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                   'src', 'main', 'resources', 'assets', 'hogwartsmagic', 'textures', 'block')

N = 16
COURSE = 4          # brick height in pixels
LENGTH = 8          # brick length in pixels
MORTAR = (104, 98, 88)
MORTAR_DARK = (76, 71, 64)

# Fired-brick shades.  A tight spread on purpose: brick varies a lot in hue and very little in
# value, and getting that the wrong way round is what makes procedural brick look like confetti.
SHADES = [
    (124, 82, 66), (112, 74, 60), (134, 92, 74), (106, 70, 58),
    (128, 88, 72), (118, 78, 62), (140, 98, 78), (100, 68, 58),
]


def _hash(a, b, salt=0):
    h = (a * 73856093) ^ (b * 19349663) ^ (salt * 83492791)
    h &= 0x7FFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0x7FFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def brick_at(px, py):
    """Which brick a pixel belongs to, with every other course offset by half a brick."""
    row = py // COURSE
    shift = (row % 2) * (LENGTH // 2)
    col = ((px + shift) % N) // LENGTH
    return row, col, (px + shift) % LENGTH, py % COURSE


def build(lit=False):
    im = Image.new('RGBA', (N, N))
    px = im.load()
    for y in range(N):
        for x in range(N):
            row, col, ix, iy = brick_at(x, y)
            joint = (iy == 0) or (ix == 0)
            if joint:
                # mortar, darker where it has run and caught soot
                base = MORTAR if _hash(x, y, 3) > 0.4 else MORTAR_DARK
                if lit:
                    # the seam of light: mortar goes warm and bright, brick stays where it is
                    base = (255, min(255, base[1] + 120), 120)
                px[x, y] = base + (255,)
                continue
            shade = SHADES[int(_hash(row, col, 1) * len(SHADES)) % len(SHADES)]
            # per-pixel grain, then soot that pools toward the bottom of each brick
            g = int((_hash(x, y, 2) - 0.5) * 22)
            soot = int(6 * iy)
            px[x, y] = (max(0, min(255, shade[0] + g - soot)),
                        max(0, min(255, shade[1] + g - soot)),
                        max(0, min(255, shade[2] + g - soot)), 255)
    return im


ENTITY_OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                          'src', 'main', 'resources', 'assets', 'hogwartsmagic', 'textures', 'entity')


def entity_sheet(tile):
    """The same brick as a 64x32 cube sheet, for the renderer.

    While the wall is opening the real blocks are invisible and every moving brick is drawn as a
    free cube by DiagonArchwayRenderer. A ModelPart cube does not sample a 16x16 block texture -- it
    samples a sheet with the six faces laid out where the mesh expects them -- so the block texture
    is stamped into all six slots of a 16x16x16 box at texOffs(0,0):

        top (16,0)  bottom (32,0)          right (0,16)  front (16,16)  left (32,16)  back (48,16)
    """
    sheet = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    for x, y in ((16, 0), (32, 0), (0, 16), (16, 16), (32, 16), (48, 16)):
        sheet.paste(tile, (x, y))
    return sheet


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(ENTITY_OUT, exist_ok=True)
    for name, lit in (('diagon_archway_brick', False), ('diagon_archway_brick_lit', True)):
        tile = build(lit)
        path = os.path.join(OUT, name + '.png')
        tile.save(path)
        print('wrote %s' % os.path.normpath(path))
        epath = os.path.join(ENTITY_OUT, name + '.png')
        entity_sheet(tile).save(epath)
        print('wrote %s' % os.path.normpath(epath))
