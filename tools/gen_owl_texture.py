"""
Generate owl.png - the 64x64 atlas for OwlModel (an eagle owl: mottled brown
back, streaked buff breast, pale brows, big orange eyes, dark ear tufts).

Box-UV layout for a w x h x d cube at texOffs (tx, ty), as each face is seen
from outside the model:
  Top    (tx + d,        ty)       w x d   bottom row borders the front
  Bottom (tx + d + w,    ty)       w x d
  Right  (tx,            ty + d)   d x h   right column borders the front
  Front  (tx + d,        ty + d)   w x h
  Left   (tx + d + w,    ty + d)   d x h   left column borders the front
  Back   (tx + 2d + w,   ty + d)   w x h

Cubes (OwlModel.createBodyLayer):
  head  8x8x8 at (0, 0)
  body  8x8x6 at (0, 32)
  wings 1x8x6 at (32, 32)  - the right wing is mirrored, so both wings show
                             the Left region (39, 38) on their outer side
  legs  2x4x2 at (32, 48) and (40, 48)

Run:  python tools/gen_owl_texture.py [out.png]
"""

import os
import random
import sys

from PIL import Image

DARK    = ( 52,  34,  22, 255)
BROWN_D = ( 84,  56,  34, 255)
BROWN   = (116,  80,  48, 255)
BROWN_L = (146, 104,  62, 255)
TAWNY   = (176, 128,  76, 255)
BUFF    = (204, 162, 106, 255)
BUFF_L  = (224, 192, 140, 255)
CREAM   = (238, 220, 184, 255)
EYE     = (242, 138,  26, 255)
EYE_D   = (196,  96,  18, 255)
PUPIL   = ( 14,  10,   8, 255)
GLINT   = (255, 246, 220, 255)
BEAK    = ( 54,  50,  48, 255)
BEAK_L  = ( 92,  86,  80, 255)
TOE     = ( 96,  90,  84, 255)
TALON   = ( 28,  26,  26, 255)

KEY = {
    'D': DARK, 'R': BROWN_D, 'b': BROWN, 'l': BROWN_L, 't': TAWNY,
    'u': BUFF, 'v': BUFF_L, 'c': CREAM, 'o': EYE, 'O': EYE_D, 'p': PUPIL,
    'g': GLINT, 'k': BEAK, 'K': BEAK_L,
}

img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
px = img.load()
rng = random.Random(1997)


def put(x, y, c):
    px[x, y] = c


def paint_map(x0, y0, rows):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            put(x0 + i, y0 + j, KEY[ch])


def mottled(x0, y0, w, h, dark_top=0.0, spots=0.10, streaks=0.18):
    """Brown plumage: per-feather tone, dark shaft streaks, buff tips.
    dark_top > 0 darkens toward the top rows (crown, mantle)."""
    for j in range(h):
        for i in range(w):
            r = rng.random()
            tone = BROWN if r < 0.55 else (BROWN_L if r < 0.82 else BROWN_D)
            # feathers sit in staggered rows: a dark shaft every other column,
            # shifted by one on alternate rows
            if (i + j // 2) % 3 == 0 and rng.random() < streaks * 3:
                tone = DARK if rng.random() < 0.45 else BROWN_D
            elif rng.random() < spots:
                tone = TAWNY if rng.random() < 0.6 else BUFF
            if dark_top and j < h * dark_top and tone in (BROWN_L, TAWNY):
                tone = BROWN
            put(x0 + i, y0 + j, tone)


def breast(x0, y0, w, h):
    """Buff breast: staggered dark drop-streaks above, broken fine bars
    below, browner flanks. The top two rows sit behind the head."""
    for j in range(h):
        for i in range(w):
            edge = i == 0 or i == w - 1
            if edge:
                c = TAWNY if rng.random() < 0.6 else BROWN_L
            else:
                c = BUFF_L if rng.random() < 0.55 else BUFF
            if j < 5:
                if (i + j // 2) % 3 == 1 and rng.random() < 0.7:
                    c = BROWN_D if rng.random() < 0.75 else DARK
            elif j % 2 == 1 and (i + j // 2) % 2 == 0 and rng.random() < 0.6:
                c = TAWNY if not edge else BROWN
            put(x0 + i, y0 + j, c)


# ---------------------------------------------------------------- head 8x8x8
HX, HY, D = 0, 0, 8
# top: mottled crown, dark ear tufts on the two front corners
mottled(HX + D, HY, 8, 8, dark_top=1.0, spots=0.06)
for (x, y) in [(0, 6), (1, 6), (0, 7), (1, 7), (6, 6), (7, 6), (6, 7), (7, 7), (0, 5), (7, 5)]:
    put(HX + D + x, HY + y, DARK)
put(HX + D + 1, HY + 5, BROWN_D)
put(HX + D + 6, HY + 5, BROWN_D)

# bottom: pale chin / throat ruff
for j in range(8):
    for i in range(8):
        put(HX + D + 8 + i, HY + j, BUFF_L if (i + j) % 3 else BUFF)

# front: the face. 3x3 orange eyes with a centred pupil, pale brows,
# dark beak between the lower halves of the eyes, a buff ruff below.
paint_map(HX + D, HY + D, [
    "DRtbbtRD",
    "RvccccvR",
    "OoOccOoO",
    "opoccopo",
    "OoOKkOoO",
    "tuuKkuut",
    "RtuuuutR",
    "RRtuutRR",
])


def head_side(x0, y0, front_col):
    """Mottled side of the head with the facial-disc rim along the front edge."""
    mottled(x0, y0, 8, 8, spots=0.08)
    back_col = 7 - front_col
    for j in range(8):
        put(x0 + front_col, y0 + j, DARK if 1 < j < 6 else BROWN_D)
        inner = front_col + (1 if front_col == 0 else -1)
        put(x0 + inner, y0 + j, TAWNY if j > 1 else BROWN)
    for j in range(5, 8):
        put(x0 + back_col, y0 + j, BROWN_D)


head_side(HX, HY + D, front_col=7)            # right side
head_side(HX + D + 8, HY + D, front_col=0)    # left side
mottled(HX + 2 * D + 8, HY + D, 8, 8, dark_top=0.5, spots=0.10)   # back of head

# ---------------------------------------------------------------- body 8x8x6
BX, BY, BD = 0, 32, 6
mottled(BX + BD, BY, 8, 6, dark_top=1.0)                 # top (under the head)
for j in range(6):                                       # bottom: pale vent
    for i in range(8):
        put(BX + BD + 8 + i, BY + j, BUFF if (i * 3 + j) % 4 else TAWNY)
breast(BX + BD, BY + BD, 8, 8)                           # front
mottled(BX, BY + BD, 6, 8)                               # right side (under wing)
mottled(BX + BD + 8, BY + BD, 6, 8)                      # left side (under wing)
mottled(BX + 2 * BD + 8, BY + BD, 8, 8, dark_top=0.4, spots=0.14)   # back
for i in range(8):                                       # tail bars along the bottom
    put(BX + 2 * BD + 8 + i, BY + BD + 6, TAWNY if i % 2 else BUFF)
    put(BX + 2 * BD + 8 + i, BY + BD + 7, DARK if i % 2 == 0 else BROWN_D)

# ---------------------------------------------------------------- wings 1x8x6
WX, WY, WD = 32, 32, 6
for j in range(6):                                       # top / bottom edges
    put(WX + WD, WY + j, BROWN_D)
    put(WX + WD + 1, WY + j, BROWN)
# inner (Right region): paler underwing, barred
for j in range(8):
    for i in range(6):
        c = BUFF_L if rng.random() < 0.55 else BUFF
        if j >= 4 and j % 2 == 0 and rng.random() < 0.7:
            c = BROWN_L
        put(WX + i, WY + WD + j, c)
# front and back edges
for j in range(8):
    put(WX + WD, WY + WD + j, TAWNY if j < 3 else BROWN_D)
    put(WX + WD + 1 + WD, WY + WD + j, DARK if j % 2 else BROWN_D)
# outer (Left region): column 0 is the leading edge
OX, OY = WX + WD + 1, WY + WD
OUTER = [
    "tlbbRb",   # shoulder / lesser coverts
    "ulbtbR",
    "luRbtb",
    "vtbRtR",   # greater coverts with buff tips
    "RRDRDR",
    "ttbtbt",   # barred flight feathers
    "DRDRDD",
    "tbtbDD",
]
paint_map(OX, OY, OUTER)
# a little noise on the coverts so the two wings are not flat
for j in range(0, 4):
    for i in range(1, 6):
        if rng.random() < 0.18:
            put(OX + i, OY + j, DARK)

# ---------------------------------------------------------------- legs 2x4x2
def leg(tx, ty):
    for (u, v) in [(tx + 2, ty), (tx + 3, ty)]:
        put(u, v, BUFF); put(u, v + 1, BUFF)
    for (u, v) in [(tx + 4, ty), (tx + 5, ty)]:
        put(u, v, TOE); put(u, v + 1, TOE)
    for face in range(4):
        fx = tx + face * 2
        for j in range(4):
            for i in range(2):
                if j < 3:
                    c = BUFF_L if (i + j) % 2 else BUFF
                else:
                    c = TALON if face == 1 else TOE
                put(fx + i, ty + 2 + j, c)


leg(32, 48)
leg(40, 48)

out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets',
    'hogwartsmagic', 'textures', 'entity', 'owl.png')
img.save(out)
print('wrote', os.path.abspath(out))
