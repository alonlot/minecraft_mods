"""Horntail and Ironbelly egg textures: block shell (16x16, auto-UV'd onto the egg model) and inventory icons.
Horntail: speckled grey stone shell, charcoal specks and a few rust flecks, clustered.
Ironbelly: pale metallic grey, cool mottling and one soft highlight band.
Run from the repo root."""
import sys
sys.path.insert(0, r".claude/skills/texturing-minecraft-items/scripts")
from PIL import Image
from pixkit import ramp, save, hexc, outline

OUT = "src/main/resources/assets/hogwartsmagic/textures/"

# --- shell ramps, darkest .. lightest ---
HORN = [hexc(c) for c in ramp("#8b8d88", 5)]
IRON = [hexc(c) for c in ramp("#a6acb3", 5, spread=0.8)]
SPECK = hexc("#3e403d")
RUST = hexc("#7a5a40")
COOL = hexc("#7a828c")
BAND = hexc("#e4e8ec")

# Speckle clusters (x, y) for the Horntail shell: small groups, not uniform noise.
HORN_SPECKS = [(2, 2), (3, 2), (3, 3), (9, 1), (10, 1), (13, 3), (12, 4), (13, 4),
               (5, 6), (6, 6), (6, 7), (1, 9), (2, 9), (10, 8), (11, 8), (11, 9),
               (4, 12), (5, 12), (5, 13), (13, 12), (14, 12), (8, 14), (9, 14), (9, 13)]
HORN_RUST = [(7, 3), (14, 8), (3, 10), (8, 11)]
# Mottling for the Ironbelly shell
IRON_MOTTLE = [(1, 1), (2, 1), (2, 2), (7, 3), (8, 3), (8, 4), (12, 2), (13, 2),
               (4, 7), (5, 7), (5, 8), (11, 7), (12, 7), (12, 8), (2, 12), (3, 12),
               (8, 13), (9, 13), (9, 14), (14, 11), (13, 12)]


def shell(base, specks, mid_mark, flecks=(), fleck_col=None, band_row=None):
    """A flat block-face texture: soft diagonal light from the top left with marks on top."""
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            # three soft diagonal bands of shade, broken by a checker nudge so it is not banded stripes
            t = (x + y) / 30.0
            i = 3 if t < 0.28 else 2 if t < 0.62 else 1
            if (x * 7 + y * 13) % 11 == 0:
                i = min(4, i + 1)
            px[x, y] = base[i]
    for p in specks:
        px[p] = mid_mark
    for p in flecks:
        px[p] = fleck_col
    if band_row is not None:
        for x in range(2, 9):
            px[x, band_row] = BAND
    return img


def egg_icon(base, marks, mark_col, flecks=(), fleck_col=None, band=False):
    """An egg about 10 wide and 13 tall, lit from the top left, outlined."""
    widths = [4, 6, 8, 8, 10, 10, 10, 10, 10, 10, 8, 6, 4]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    y0 = 2
    cx = 8.0
    for r, w in enumerate(widths):
        for k in range(w):
            x = int(cx - w / 2 + k)
            u = (k + 0.5) / w - 0.5          # -0.5 left .. +0.5 right
            v = (r + 0.5) / len(widths) - 0.5  # top .. bottom
            light = -0.6 * u - 0.5 * v        # brighter to the upper left
            i = 4 if light > 0.30 else 3 if light > 0.08 else 2 if light > -0.12 else 1 if light > -0.30 else 0
            px[x, y0 + r] = base[i]
    for (x, y) in marks:
        if px[x, y][3]:
            px[x, y] = mark_col
    for (x, y) in flecks:
        if px[x, y][3]:
            px[x, y] = fleck_col
    if band:
        for x in range(6, 10):
            if px[x, 6][3]:
                px[x, 6] = BAND
    return outline(img, darken=0.30)


save(shell(HORN, HORN_SPECKS, SPECK, HORN_RUST, RUST), OUT + "block/horntail_egg.png")
save(shell(IRON, IRON_MOTTLE, COOL, band_row=5), OUT + "block/ironbelly_egg.png")
save(egg_icon(HORN, [(6, 5), (7, 5), (7, 6), (10, 8), (11, 8), (6, 10), (7, 11), (9, 12)], SPECK,
              [(9, 5), (5, 9)], RUST), OUT + "item/horntail_egg.png")
save(egg_icon(IRON, [(10, 7), (10, 8), (6, 11), (7, 11), (9, 4)], COOL, band=True),
     OUT + "item/ironbelly_egg.png")
