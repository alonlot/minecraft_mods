"""Paint the Wizard Cauldron block textures.

    python build_scripts/generate_wizard_cauldron_textures.py

Writes five sheets to assets/hogwartsmagic/textures/block/:

  wizard_cauldron_side.png    outside of the pot, the feet and the lugs
  wizard_cauldron_inner.png   inside walls and floor
  wizard_cauldron_top.png     top of the rim and of the shoulder ledge
  wizard_cauldron_bottom.png  underside
  wizard_cauldron_brew.png    the brew surface, 4 animated frames (16x64, with .mcmeta)

WHAT IT DEPICTS
---------------
A black cast-iron pot on four short feet, with a rolled rim, a narrower neck, a shoulder band of
rivets and a band of engraved runes round the belly. The old texture was one 16x16 icon (a green
square in a grey frame) stretched over a solid box. The new model (models/block/wizard_cauldron.json)
is hollow, so it needs real faces: outside, inside, rim and floor.

WHY THE SIDE SHEET IS PAINTED BY ROWS
-------------------------------------
The model leaves the UVs out, so Minecraft maps each face from its position in the block. A side
face at height y uses texture row 16 - y. So each row of the side sheet belongs to one part of the
pot, and the sheet is painted as horizontal bands:

  rows  4..4   rim lip (y 11..12): the brightest metal, worn by ladles
  rows  5..6   neck (y 9..11): set back, in shadow under the rim
  row   7      shoulder band (y 8..9): rivets
  rows  8..12  belly (y 3..8): the rune band at rows 9..10
  row  13      lower belly (y 2..3): soot starts
  rows 14..15  feet (y 0..2): the darkest, soot-black

STYLE
-----
As generate_statue_heart_texture.py: tileable value noise for the metal (the lattice wraps at 16,
so a row of cauldrons has no seam), then every colour is snapped onto a short ramp through a 4x4
Bayer matrix. The runes are grooves, not stickers: a dark cut with one dull bronze pixel of
catch-light on its lower edge.
"""

import os
import random

from PIL import Image

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                       "assets", "hogwartsmagic", "textures", "block")

SEED = 1907

BAYER = [
    [0, 8, 2, 10],
    [12, 4, 14, 6],
    [3, 11, 1, 9],
    [15, 7, 13, 5],
]

# Cast iron, blackened, with a faint green-blue cast so it reads as old and magical, not as
# vanilla's grey cauldron.
IRON = [
    (18, 18, 22),
    (28, 29, 34),
    (39, 41, 47),
    (52, 55, 62),
    (68, 72, 80),
    (90, 95, 104),
    (118, 124, 134),
]
SOOT = (14, 12, 12)
RIVET_HI = (132, 138, 148)
RIVET_LO = (24, 24, 28)
RUNE_CUT = (26, 27, 32)
RUNE_GLINT = (138, 108, 58)
RESIDUE = [(34, 58, 44), (46, 78, 56), (62, 102, 70)]

# The brew: a deep green potion, darker at the walls, with lighter swirl and bubbles.
BREW = [
    (14, 52, 30),
    (20, 74, 40),
    (30, 100, 52),
    (46, 132, 66),
    (82, 176, 96),
    (150, 226, 150),
]


def value_noise(rng, cells=4, size=16):
    """Tileable value noise in 0..1 on a size x size grid, lattice wraps every `cells`."""
    step = size // cells
    lattice = [[rng.random() for _ in range(cells)] for _ in range(cells)]
    out = [[0.0] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            gx, gy = x / step, y / step
            x0, y0 = int(gx) % cells, int(gy) % cells
            x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
            fx, fy = gx - int(gx), gy - int(gy)
            sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
            top = lattice[y0][x0] * (1 - sx) + lattice[y0][x1] * sx
            bot = lattice[y1][x0] * (1 - sx) + lattice[y1][x1] * sx
            out[y][x] = top * (1 - sy) + bot * sy
    return out


def ramp_pick(ramp, value, x, y):
    """Snap value (0..1) onto the ramp with an ordered dither."""
    value = max(0.0, min(1.0, value))
    scaled = value * (len(ramp) - 1)
    lo = int(scaled)
    frac = scaled - lo
    threshold = (BAYER[y % 4][x % 4] + 0.5) / 16.0
    idx = lo + 1 if frac > threshold and lo + 1 < len(ramp) else lo
    return ramp[idx]


def metal_field(rng, base, spread, fine=0.06):
    coarse = value_noise(rng, cells=4)
    detail = value_noise(rng, cells=8)
    return [[base + (coarse[y][x] - 0.5) * spread + (detail[y][x] - 0.5) * fine
             for x in range(16)] for y in range(16)]


def paint_side(rng):
    img = Image.new("RGBA", (16, 16))
    # Low spread on purpose: at 0.18 the darkest ramp step clumps into black blotches that read
    # as coal ore, not as cast iron.
    field = metal_field(rng, 0.0, 0.07, fine=0.05)
    # Base brightness per row: the band each row belongs to (see the docstring).
    row_base = {
        0: 0.42, 1: 0.42, 2: 0.42, 3: 0.42,
        4: 0.74,                      # rim lip
        5: 0.30, 6: 0.36,             # neck, shadowed under the rim
        7: 0.50,                      # shoulder band
        8: 0.56, 9: 0.54, 10: 0.52, 11: 0.48, 12: 0.40,
        13: 0.24,                     # lower belly, soot starts
        14: 0.14, 15: 0.10,           # feet
    }
    for y in range(16):
        for x in range(16):
            v = row_base[y] + field[y][x]
            img.putpixel((x, y), ramp_pick(IRON, v, x, y) + (255,))

    # Worn highlight along the top of the rim lip, broken where the ladle never touches it.
    for x in range(16):
        if rng.random() < 0.7:
            img.putpixel((x, 4), IRON[6] + (255,))

    # Shoulder rivets: every fourth pixel, a bright cap over a dark shadow pixel.
    for x in range(1, 16, 4):
        img.putpixel((x, 7), RIVET_HI + (255,))
        img.putpixel((x, 8), RIVET_LO + (255,))

    # The rune band, rows 9..10: short glyphs cut into the iron, with a bronze catch-light under
    # each cut. Glyphs are 2..3 px wide with a 2 px gap, so they read as writing, not as a stripe.
    glyphs = [
        [(0, 0), (1, 1), (0, 1)],
        [(0, 0), (1, 0), (1, 1)],
        [(0, 1), (1, 0), (2, 1)],
        [(0, 0), (0, 1), (1, 0)],
        [(1, 0), (0, 1), (1, 1)],
    ]
    x = 0
    gi = 0
    while x < 16:
        glyph = glyphs[gi % len(glyphs)]
        width = max(dx for dx, _ in glyph) + 1
        for dx, dy in glyph:
            px = (x + dx) % 16
            img.putpixel((px, 9 + dy), RUNE_CUT + (255,))
        # Catch-light only under the bottom of each cut, so the bronze follows the glyph.
        for dx, dy in glyph:
            if dy == 1:
                img.putpixel(((x + dx) % 16, 11), RUNE_GLINT + (255,))
        x += width + 2
        gi += 1

    # Soot creeping up from the feet: a few dark drips into the lower belly.
    for x in range(16):
        if rng.random() < 0.2:
            img.putpixel((x, 12), SOOT + (255,))
    return img


def paint_inner(rng):
    img = Image.new("RGBA", (16, 16))
    field = metal_field(rng, 0.0, 0.08)
    for y in range(16):
        for x in range(16):
            # Darker the deeper you look into the pot.
            v = 0.34 - (y / 15.0) * 0.22 + field[y][x]
            img.putpixel((x, y), ramp_pick(IRON[:5], v, x, y) + (255,))
    # A crust of dried brew at the old fill line (inner walls run y 3..11, rows 5..13; the
    # brew surface sits at y 9.5, so the line is about row 6..7).
    for x in range(16):
        top = 6 + (1 if rng.random() < 0.3 else 0)
        img.putpixel((x, top), RESIDUE[rng.randrange(3)] + (255,))
        if rng.random() < 0.45:
            img.putpixel((x, top + 1), RESIDUE[rng.randrange(2)] + (255,))
    return img


def paint_top(rng):
    img = Image.new("RGBA", (16, 16))
    field = metal_field(rng, 0.0, 0.14)
    for y in range(16):
        for x in range(16):
            # The rim top spans 2..14; its outer edge (2 and 13) is rolled and catches light,
            # its inner edge (4 and 11, the lip of the opening) is the darkest.
            v = 0.50 + field[y][x]
            if x in (2, 13) or y in (2, 13):
                v += 0.22
            if (x in (4, 11) and 4 <= y <= 11) or (y in (4, 11) and 4 <= x <= 11):
                v -= 0.20
            img.putpixel((x, y), ramp_pick(IRON, v, x, y) + (255,))
    return img


def paint_bottom(rng):
    img = Image.new("RGBA", (16, 16))
    field = metal_field(rng, 0.0, 0.10)
    for y in range(16):
        for x in range(16):
            v = 0.08 + field[y][x]
            img.putpixel((x, y), ramp_pick([SOOT] + IRON[:3], v, x, y) + (255,))
    return img


def paint_brew(rng, frames=4):
    """A slow swirl: the same noise field rotated round the centre, one step per frame."""
    import math

    sheet = Image.new("RGBA", (16, 16 * frames))
    base = value_noise(rng, cells=4)
    bubbles = [(rng.randrange(4, 12), rng.randrange(4, 12)) for _ in range(5)]
    for f in range(frames):
        angle = f * (math.pi / 2) / frames
        ca, sa = math.cos(angle), math.sin(angle)
        for y in range(16):
            for x in range(16):
                cx, cy = x - 7.5, y - 7.5
                rx, ry = cx * ca - cy * sa + 7.5, cx * sa + cy * ca + 7.5
                n = base[int(ry) % 16][int(rx) % 16]
                r = math.hypot(cx, cy)
                # Spiral arm: brighter along a curve whose phase turns with the frame.
                arm = 0.5 + 0.5 * math.sin(math.atan2(cy, cx) * 2 + r * 0.8 - f * (math.pi / 2))
                v = 0.30 + n * 0.30 + arm * 0.22 - (r / 11.0) * 0.25
                sheet.putpixel((x, y + 16 * f), ramp_pick(BREW[:5], v, x, y) + (255,))
        # Bubbles rise and pop: each bubble shows on two of the four frames.
        for i, (bx, by) in enumerate(bubbles):
            if (f + i) % 4 in (0, 1):
                sheet.putpixel((bx, by + 16 * f), BREW[5] + (255,))
                if (f + i) % 4 == 1:
                    sheet.putpixel(((bx + 1) % 16, by + 16 * f), BREW[4] + (255,))
    return sheet


def main():
    rng = random.Random(SEED)
    out = os.path.abspath(OUT_DIR)
    os.makedirs(out, exist_ok=True)
    paint_side(rng).save(os.path.join(out, "wizard_cauldron_side.png"))
    paint_inner(rng).save(os.path.join(out, "wizard_cauldron_inner.png"))
    paint_top(rng).save(os.path.join(out, "wizard_cauldron_top.png"))
    paint_bottom(rng).save(os.path.join(out, "wizard_cauldron_bottom.png"))
    paint_brew(rng).save(os.path.join(out, "wizard_cauldron_brew.png"))
    with open(os.path.join(out, "wizard_cauldron_brew.png.mcmeta"), "w", encoding="utf-8") as fh:
        fh.write('{\n  "animation": {\n    "frametime": 8,\n    "interpolate": true\n  }\n}\n')
    print("wrote 5 textures to", out)


if __name__ == "__main__":
    main()
