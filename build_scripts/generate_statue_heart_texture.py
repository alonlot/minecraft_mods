"""Paint the 16x16 Statue Heart block texture.

    python build_scripts/generate_statue_heart_texture.py

Writes assets/hogwartsmagic/textures/block/statue_heart.png -- one sheet, used on all six faces
by the cube_all model, because the rune is carved into every side of the stone.

WHAT IT DEPICTS
---------------
The core a Living Statue is animated around: a lump of the same rock the statues are cut from,
with the binding rune still lit inside it. The stone here is deliberately the entity texture's
grey (living_statue.png averages around (100,100,95) -- neutral, a hair warm) pulled several
steps darker, so a heart on the floor next to a statue reads as the same quarry rather than as
an unrelated ore block.

The rune is drawn as a GROOVE, not as a filled glyph. A solid glowing heart at 16px looks like a
sticker painted onto the block; a one-pixel channel with light pooling in the recess behind it
looks like something was cut into the rock. The channel is derived rather than hand-plotted: the
filled heart mask is eroded by one pixel and subtracted from itself, which guarantees the groove
is exactly one pixel wide everywhere, including around the cleft between the lobes -- hand-listing
those pixels drifts by one somewhere and the cleft is always where it shows.

STYLE
-----
Three passes do the work, in the order vanilla stone textures imply:

  * The stone is tileable value noise, not per-pixel noise. Blocks sit next to each other, so the
    lattice wraps at 16 (4x4 cells, step 4) and a wall of hearts has no seam. Per-pixel noise is
    seamless too, but it looks like television snow rather than rock.
  * Everything is quantised onto a six-step ramp through a 4x4 Bayer matrix. The dither is the
    point: an unquantised gradient reads as a render, the banding-plus-stipple reads as painted.
  * The glow is composited last, from a blurred field, so light spills out of the channel onto the
    surrounding surface and pools on the recessed floor inside the heart. Without that spill the
    groove reads as a purple line drawn on grey; with it, it reads as light coming out of a hole.
"""

import os
import random

from PIL import Image

SIZE = 16
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "assets", "hogwartsmagic", "textures", "block", "statue_heart.png")

# --- palette ---------------------------------------------------------------------------------
# Ordered dark -> bright and indexed by a 0..1 intensity. R = G > B throughout the stone ramp,
# which is what keeps it in the same family as the statue's granite instead of drifting blue like
# deepslate does.

STONE = [(28, 28, 27), (36, 36, 34), (45, 45, 42), (55, 55, 51), (66, 66, 61), (78, 78, 72)]
GLOW = [(70, 40, 116), (110, 64, 176), (152, 102, 222), (200, 166, 246), (238, 224, 255)]

BAYER = [[0, 8, 2, 10],
         [12, 4, 14, 6],
         [3, 11, 1, 9],
         [15, 7, 13, 5]]

# --- the glyph -------------------------------------------------------------------------------
# Filled heart as inclusive column spans per row, symmetric about x = 7.5. Two clear pixels of
# margin on every side: the texture tiles, and two hearts butted together want a gap wide enough
# to read as two carvings rather than one long scar.

HEART_ROWS = {
    4: [(4, 5), (10, 11)],
    5: [(3, 6), (9, 12)],
    6: [(3, 12)],
    7: [(3, 12)],
    8: [(4, 11)],
    9: [(5, 10)],
    10: [(6, 9)],
    11: [(7, 8)],
}


def filled_mask():
    mask = [[False] * SIZE for _ in range(SIZE)]
    for row, spans in HEART_ROWS.items():
        for c0, c1 in spans:
            for col in range(c0, c1 + 1):
                mask[row][col] = True
    return mask


def eroded(mask):
    """Shrink by one pixel on a 4-neighbourhood -- the interior floor of the carving."""
    out = [[False] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if not mask[y][x]:
                continue
            out[y][x] = all(
                0 <= y + dy < SIZE and 0 <= x + dx < SIZE and mask[y + dy][x + dx]
                for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)))
    return out


# --- stone -----------------------------------------------------------------------------------

def wrapped_noise(cells, seed):
    """Smoothed value noise on a lattice that wraps at SIZE, so the block tiles with itself."""
    rng = random.Random(seed)
    lattice = [[rng.random() for _ in range(cells)] for _ in range(cells)]
    step = SIZE / cells
    out = [[0.0] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        gy = y / step
        y0, ty = int(gy) % cells, gy - int(gy)
        y1 = (y0 + 1) % cells
        ty = ty * ty * (3 - 2 * ty)
        for x in range(SIZE):
            gx = x / step
            x0, tx = int(gx) % cells, gx - int(gx)
            x1 = (x0 + 1) % cells
            tx = tx * tx * (3 - 2 * tx)
            top = lattice[y0][x0] * (1 - tx) + lattice[y0][x1] * tx
            bot = lattice[y1][x0] * (1 - tx) + lattice[y1][x1] * tx
            out[y][x] = top * (1 - ty) + bot * ty
    return out


def pick(ramp, t, x, y):
    """Quantise onto a ramp, breaking the band edge with the Bayer threshold at (x, y)."""
    t = max(0.0, min(1.0, t))
    v = t * (len(ramp) - 1)
    i = int(v)
    if v - i > (BAYER[y % 4][x % 4] + 0.5) / 16.0:
        i += 1
    return ramp[min(i, len(ramp) - 1)]


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def blur(field, passes):
    """Box blur with edge clamping. This is the light spill, so it wants to be soft and wide."""
    for _ in range(passes):
        nxt = [[0.0] * SIZE for _ in range(SIZE)]
        for y in range(SIZE):
            for x in range(SIZE):
                total = 0.0
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        sy = min(SIZE - 1, max(0, y + dy))
                        sx = min(SIZE - 1, max(0, x + dx))
                        total += field[sy][sx]
                nxt[y][x] = total / 9.0
        field = nxt
    return field


def build():
    heart = filled_mask()
    floor = eroded(heart)                       # recessed stone inside the carving
    groove = [[heart[y][x] and not floor[y][x] for x in range(SIZE)] for y in range(SIZE)]

    coarse = wrapped_noise(4, 90210)            # blotching, the size of a chisel face
    fine = wrapped_noise(8, 90211)              # grit on top of it

    light = [[1.0 if groove[y][x] else 0.0 for x in range(SIZE)] for y in range(SIZE)]
    light = blur(light, 2)
    peak = max(max(row) for row in light)

    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    speck = random.Random(90212)

    for y in range(SIZE):
        for x in range(SIZE):
            if groove[y][x]:
                # The channel itself. Held near the top of the ramp but not pinned to it -- a
                # perfectly even line looks printed, a slightly uneven one looks lit.
                col = pick(GLOW, 0.72 + 0.28 * fine[y][x], x, y)
                px[x, y] = col + (255,)
                continue

            t = 0.30 + 0.44 * coarse[y][x] + 0.26 * fine[y][x]
            if floor[y][x]:
                # Sunk floor: darker, and darkest along its top edge where the lip overhangs it.
                t -= 0.42 if (y > 0 and groove[y - 1][x]) else 0.30
            elif speck.random() < 0.07:
                t -= 0.22                        # pits and old chips in the surface
            col = pick(STONE, t, x, y)

            spill = light[y][x] / peak
            if spill > 0.02:
                # Light out of the channel: warmer and stronger where it pools on the sunk floor,
                # a thin wash where it falls on the flat surface outside the heart. Kept low on
                # purpose -- at full strength the spill closes over the stone and the whole face
                # turns into one violet blob, which loses the carving the texture is about.
                strength = min(0.58, spill * (1.05 if floor[y][x] else 0.55))
                col = lerp(col, GLOW[3] if floor[y][x] else GLOW[2], strength)
            px[x, y] = col + (255,)

    return img


if __name__ == "__main__":
    path = os.path.normpath(OUT)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    build().save(path)
    print("wrote", path)
