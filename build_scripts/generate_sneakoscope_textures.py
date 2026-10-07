"""Paint the Sneakoscope block textures.

    python3 build_scripts/generate_sneakoscope_textures.py [--preview]

Writes three sheets to assets/hogwartsmagic/textures/block/:

  sneakoscope_brass.png        base plate, pivot and spindle (opaque)
  sneakoscope_glass.png        the glass top at rest (translucent)
  sneakoscope_glass_spin.png   the glass top spinning and glowing, 6 animated frames (16x96, with .mcmeta)

With --preview it also writes /tmp/sneakoscope_preview.png: the brass and glass sheets and the six spin frames at
8x over a checkerboard (so the glass alpha shows), for a look before the game.

WHAT IT DEPICTS
---------------
A Sneakoscope: a small glass spinning top on a brass stand, 6 x 8 x 6 pixels in the block (models/block/
sneakoscope.json). At rest the glass is pale aqua and clear. When it detects something it spins, lights up and
whistles: the spinning sheet is the same glass, brighter, and the model draws it at full brightness (forge_data light 15).

WHY THE SPIN IS A COLUMN THAT STEPS
-----------------------------------
The models leave the UVs out, so Minecraft maps each face from its position in the block. Every side face of the
glass body (x or z from 5 to 11, y from 2 to 6) reads texture columns 5..10 and rows 10..13. A bright facet edge
that steps one column per frame across columns 5..10 (period 6, the face width) sweeps across all four sides at
once, which reads as the top turning. Two ticks a frame and no interpolation: a crisp, quick spin.

STYLE
-----
As generate_wizard_cauldron_textures.py: tileable value noise (the lattice wraps at 16), then every colour is
snapped onto a short ramp through a 4x4 Bayer matrix. The glass ramps carry alpha, so the dither also dithers the
transparency. Brass gets a few single-pixel polish glints.
"""

import math
import os
import random
import sys

from PIL import Image

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                       "assets", "hogwartsmagic", "textures", "block")
PREVIEW = "/tmp/sneakoscope_preview.png"

SEED = 1993

BAYER = [
    [0, 8, 2, 10],
    [12, 4, 14, 6],
    [3, 11, 1, 9],
    [15, 7, 13, 5],
]

# Polished brass, dark in the scratches, bright where it is rubbed.
BRASS = [
    (70, 48, 20, 255),
    (100, 72, 30, 255),
    (134, 100, 42, 255),
    (170, 132, 56, 255),
    (204, 164, 78, 255),
    (234, 200, 116, 255),
]
# Clear glass at rest: pale aqua, more opaque where it is brighter.
GLASS = [
    (120, 168, 176, 110),
    (150, 196, 202, 130),
    (184, 222, 224, 150),
    (220, 242, 240, 175),
    (246, 252, 250, 210),
]
# Spinning glass: the same blue-grey glass, lifted about 35% toward white (same alpha), so it reads as lit glass.
def _lift(c, k=0.35):
    return (round(c[0] + (255 - c[0]) * k), round(c[1] + (255 - c[1]) * k), round(c[2] + (255 - c[2]) * k), c[3])


GLOW = [_lift(c) for c in GLASS]
EDGE_HI = (235, 248, 250, 210)
EDGE_MID = (214, 234, 238, 190)

FRAMES = 6
FACE_LEFT = 5    # first texture column of every glass side face
FACE_WIDTH = 6   # columns 5..10


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


def paint_brass(rng):
    img = Image.new("RGBA", (16, 16))
    coarse = value_noise(rng, cells=4)
    detail = value_noise(rng, cells=8)
    for y in range(16):
        for x in range(16):
            v = 0.52 + (coarse[y][x] - 0.5) * 0.30 + (detail[y][x] - 0.5) * 0.12
            if y == 15:
                v -= 0.12  # the base plate's sides read row 15: its lower edge sits in shadow
            img.putpixel((x, y), ramp_pick(BRASS, v, x, y))
    for _ in range(10):
        img.putpixel((rng.randrange(16), rng.randrange(15)), BRASS[5])
    return img


def glass_value(field, x, y):
    v = 0.45 + (field[y][x] - 0.5) * 0.35
    if y in (11, 12):
        v += 0.15  # the bright band round the widest part of the top
    if (x - FACE_LEFT) % FACE_WIDTH in (0, FACE_WIDTH - 1):
        v -= 0.12  # face edges a little darker, so the top reads as faceted
    return v


def paint_glass(field):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), ramp_pick(GLASS, glass_value(field, x, y), x, y))
    return img


def paint_spin(field):
    sheet = Image.new("RGBA", (16, 16 * FRAMES))
    for f in range(FRAMES):
        for y in range(16):
            for x in range(16):
                v = glass_value(field, x, y) + 0.10 + 0.08 * math.sin((y + f) * math.pi / 3)
                colour = ramp_pick(GLOW, v, x, y)
                phase = (x - FACE_LEFT - f) % FACE_WIDTH
                if phase == 0:
                    colour = EDGE_HI
                elif phase == 1:
                    colour = EDGE_MID
                elif phase == 3:
                    colour = ramp_pick(GLOW, v + 0.25, x, y)  # the opposite facet, fainter
                sheet.putpixel((x, y + 16 * f), colour)
    return sheet


def write_preview(tiles, scale=8, gap=8):
    side = 16 * scale
    img = Image.new("RGBA", (gap + len(tiles) * (side + gap), side + 2 * gap), (24, 24, 28, 255))
    for i, tile in enumerate(tiles):
        ox, oy = gap + i * (side + gap), gap
        for ty in range(16):
            for tx in range(16):
                shade = 70 if (tx + ty) % 2 == 0 else 50
                img.paste((shade, shade, shade, 255),
                          (ox + tx * scale, oy + ty * scale, ox + (tx + 1) * scale, oy + (ty + 1) * scale))
        img.alpha_composite(tile.resize((side, side), Image.Resampling.NEAREST), (ox, oy))
    img.save(PREVIEW)


def main():
    rng = random.Random(SEED)
    out = os.path.abspath(OUT_DIR)
    os.makedirs(out, exist_ok=True)
    brass = paint_brass(rng)
    field = value_noise(rng, cells=4)
    glass = paint_glass(field)
    spin = paint_spin(field)
    brass.save(os.path.join(out, "sneakoscope_brass.png"))
    glass.save(os.path.join(out, "sneakoscope_glass.png"))
    spin.save(os.path.join(out, "sneakoscope_glass_spin.png"))
    with open(os.path.join(out, "sneakoscope_glass_spin.png.mcmeta"), "w", encoding="utf-8") as fh:
        fh.write('{\n  "animation": {\n    "frametime": 2\n  }\n}\n')
    print("wrote 3 textures to", out)
    if "--preview" in sys.argv[1:]:
        frames = [spin.crop((0, 16 * f, 16, 16 * f + 16)) for f in range(FRAMES)]
        write_preview([brass, glass] + frames)
        print("wrote", PREVIEW)


if __name__ == "__main__":
    main()
