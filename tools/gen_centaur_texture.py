"""
Generate minator.png (128x64) for MinatorModel, the mod's Centaur.

The old sheet was two unrelated skins pasted side by side: a grey, stone-coloured illager for the
human half (on an opaque white background) and a horse sheet still carrying saddle and chest
pieces the model never samples. This paints a bay centaur from where every texel sits on the
model (tools/texture_audit/paint.py):

  head    long dark hair falling down the back, brows, eyes, a short beard; the nose cube shaded
  torso   tanned and bare, chest and stomach modelled in shade, a leather baldric from the right
          shoulder to the left hip with a buckle, a quiver of fletched arrows across the back,
          a belt where the man meets the horse
  arms    tanned, leather bracers on the forearms
  horse   chestnut coat darker along the spine, faint dapples, a paler belly; black stockings
          and grey hooves on the legs; a long black tail

Run (after bash tools/texture_audit/run.sh has dumped the models):
    python tools/gen_centaur_texture.py [--preview out.png]
"""
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, 'texture_audit'))
from paint import texels, preview  # noqa: E402

OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'hogwartsmagic',
                   'textures', 'entity', 'minator.png')

SKIN = [(104, 66, 46), (128, 84, 60), (150, 102, 75), (172, 122, 92), (192, 144, 111), (210, 166, 132)]
HAIR = [(18, 12, 9), (28, 20, 14), (40, 28, 20), (54, 39, 27), (70, 52, 36)]
LEATHER = [(48, 29, 16), (68, 43, 24), (90, 59, 34), (114, 78, 47), (138, 100, 64)]
BRASS = (168, 140, 86)
COAT = [(62, 31, 17), (82, 42, 23), (102, 55, 30), (122, 68, 38), (142, 83, 48), (160, 99, 60)]
POINTS = [(16, 12, 10), (25, 19, 15), (36, 28, 22), (50, 40, 32)]
HOOF = [(44, 42, 40), (64, 60, 56), (86, 82, 76)]
EYE_WHITE = (222, 214, 200)
IRIS = (58, 38, 22)
MOUTH = (92, 52, 40)
FLETCH = [(214, 206, 188), (150, 40, 34)]

rng = random.Random(4417)


def pick(ramp, level, jitter=0.35):
    i = int(round(level + rng.uniform(-jitter, jitter)))
    return ramp[max(0, min(len(ramp) - 1, i))]


# 8x10 face, columns run the centaur's right -> left, rows top -> bottom.
# H hair, h lit strand, S skin, s shade, b brow, w eye white, e iris, B beard, m mouth
FACE = [
    "HHHHHHHH",
    "HhHHHHhH",
    "HSSSSSSH",
    "HbbSSbbH",
    "HweSSewH",
    "HSSSSSSH",
    "HSsSSsSH",
    "HBSmmSBH",
    "HBBBBBBH",
    "hBBBBBBh",
]


def head(t):
    nx, ny, nz = t.n
    f = t.face
    if f == 'front':
        ch = FACE[min(9, int(ny * 10))][min(7, int(nx * 8))]
        if ch == 'H':
            return pick(HAIR, 1.6)
        if ch == 'h':
            return HAIR[3]
        if ch == 'b':
            return HAIR[1]
        if ch == 'w':
            return EYE_WHITE
        if ch == 'e':
            return IRIS
        if ch == 'B':
            return pick(HAIR, 1.8 - 0.6 * ny, 0.6)
        if ch == 'm':
            return MOUTH
        if ch == 's':
            return SKIN[2]
        return pick(SKIN, 3.6 - 0.8 * ny, 0.25)
    if f == 'top':
        return pick(HAIR, 2.2, 0.7)
    if f == 'bottom':
        return pick(HAIR, 1.2) if nz > 0.4 else pick(SKIN, 1.6)
    if f == 'back':
        strand = math.sin(t.local[0] * 2.4)
        return pick(HAIR, 1.8 + 0.9 * strand - 0.5 * ny, 0.4)
    # sides: hair everywhere but a cheek, an ear and the jaw near the face
    if nz < 0.42 and 0.18 < ny < 0.72:
        if 0.24 < nz < 0.40 and 0.36 < ny < 0.58:
            return SKIN[2] if ny < 0.5 else SKIN[1]      # ear
        return pick(SKIN, 3.0 - 0.8 * ny, 0.25)
    if nz < 0.42 and ny >= 0.72:
        return pick(HAIR, 1.6, 0.5)                       # beard along the jaw
    strand = math.sin(t.local[2] * 2.4 + 1.0)
    return pick(HAIR, 1.8 + 0.8 * strand - 0.4 * ny, 0.4)


def nose(t):
    if t.face == 'bottom':
        return SKIN[0]
    if t.face == 'front':
        return SKIN[4] if t.n[1] < 0.5 else SKIN[3]
    return SKIN[2] if t.face in ('left', 'right') else SKIN[3]


def baldric(nx, ny):
    """The strap's centre line runs from the right shoulder (nx 0, ny 0) to the left hip."""
    return abs(ny - (0.06 + 0.86 * nx)) < 0.085


def torso(t):
    nx, ny, nz = t.n
    f = t.face
    if ny > 0.86 and f != 'top':
        if f == 'front' and 0.4 < nx < 0.6 and ny < 0.95:
            return BRASS
        return pick(LEATHER, 2.0 if ny < 0.93 else 1.0, 0.2)     # belt
    if f == 'top':
        if baldric(nx, 0.0) or nx < 0.12:
            return LEATHER[2]
        return pick(SKIN, 4.0)
    if f == 'bottom':
        return SKIN[1]
    if f == 'front':
        if baldric(nx, ny):
            if abs(nx - 0.5) < 0.07 and abs(ny - 0.49) < 0.07:
                return BRASS
            edge = abs(ny - (0.06 + 0.86 * nx)) > 0.05
            return LEATHER[3] if edge else LEATHER[2]
        cx = abs(nx - 0.5)
        level = 3.4 - 0.6 * ny
        if 0.18 < ny < 0.38:
            level += 0.5 if cx > 0.1 else -0.4               # pectorals with a centre line
        if 0.38 < ny < 0.44 and cx > 0.08:
            level -= 1.0                                     # shadow under the chest
        if 0.5 < ny < 0.84:
            if cx < 0.06:
                level -= 0.8                                 # linea alba
            elif int(ny * 12) % 2 == 0:
                level -= 0.5                                 # stomach bands
        if cx > 0.42:
            level -= 0.6                                     # flanks turn away from the light
        return pick(SKIN, level, 0.1)
    if f == 'back':
        if 0.48 < nx < 0.86 and ny < 0.74:                   # quiver across the back
            if ny < 0.12:
                k = int(t.local[0] * 3) % 3
                return FLETCH[0] if k else FLETCH[1]
            if ny < 0.16:
                return LEATHER[4]
            edge = nx < 0.54 or nx > 0.8
            return LEATHER[1] if edge else pick(LEATHER, 2.4 - 0.8 * ny, 0.3)
        if baldric(1 - nx, ny):
            return LEATHER[2]
        level = 3.0 - 0.5 * ny - (0.8 if abs(nx - 0.5) < 0.06 else 0)   # spine groove
        return pick(SKIN, level, 0.1)
    # sides
    if ny < 0.12 and f == 'right' and nz < 0.5:
        return LEATHER[2]                                    # strap over the right shoulder
    return pick(SKIN, 2.8 - 0.6 * ny, 0.25)


def arm(t):
    nx, ny, nz = t.n
    if t.face == 'bottom' or ny > 0.92:
        return pick(SKIN, 2.4, 0.2)                          # hand
    if 0.56 < ny < 0.86:
        lip = ny < 0.61 or ny > 0.81
        return LEATHER[3] if lip else pick(LEATHER, 2.0, 0.4)
    level = 3.4 - 0.7 * ny
    if t.face == 'top':
        level = 3.8
    if t.face in ('back', 'right') and t.face != 'top':
        level -= 0.4
    return pick(SKIN, level, 0.25)


def coat(t):
    nx, ny, nz = t.n
    f = t.face
    dapple = 0.5 * math.sin(t.local[0] * 1.3 + t.local[2] * 0.7) * math.sin(t.local[2] * 1.1 - t.local[1] * 0.9)
    if f == 'top':
        level = 1.8 - (0.8 if abs(nx - 0.5) < 0.15 else 0)  # dark dorsal stripe
        return pick(COAT, level + dapple, 0.3)
    if f == 'bottom':
        return pick(COAT, 3.2, 0.4)                          # paler belly
    level = 2.6 - 0.8 * (1 - ny) + 0.9 * ny * (ny < 0.85) + dapple
    if ny > 0.9:
        level -= 0.8
    if f == 'back':
        level -= 0.4
    return pick(COAT, level, 0.3)


def leg(t):
    nx, ny, nz = t.n
    if t.face == 'bottom':
        return HOOF[0]
    if ny > 0.86:
        return pick(HOOF, 1.4 if t.face == 'front' else 1.0, 0.3)
    if ny > 0.44:
        return pick(POINTS, 1.6 - 0.8 * (ny - 0.44), 0.4)   # black stocking
    if ny > 0.36:
        return pick(COAT, 0.6, 0.4)                          # coat fading into the stocking
    if t.face == 'top':
        return COAT[1]
    return pick(COAT, 2.2 - 0.6 * ny, 0.3)


def tail(t):
    strand = math.sin(t.local[0] * 3.1 + t.local[2] * 2.3)
    return pick(POINTS, 1.4 + 0.9 * strand - 0.5 * t.n[1], 0.4)


def main():
    img = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
    px = img.load()
    for t in texels('minator'):
        p = t.part
        if p.endswith('/nose'):
            c = nose(t)
        elif p.endswith('/head2'):
            c = head(t)
        elif p.endswith('/body2'):
            c = torso(t)
        elif p.endswith('_arm'):
            c = arm(t)
        elif p.endswith('/body'):
            c = coat(t)
        elif p.endswith('_leg'):
            c = leg(t)
        elif p.endswith('/tail'):
            c = tail(t)
        else:
            continue
        px[t.u, t.v] = c + (255,)
    img.save(OUT)
    print('wrote', os.path.abspath(OUT))
    if '--preview' in sys.argv:
        preview('minator', img, sys.argv[sys.argv.index('--preview') + 1])


if __name__ == '__main__':
    main()
