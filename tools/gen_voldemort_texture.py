"""
Paint voldemort.png and voldemort_eyes.png (128x128) for VoldemortModel.

Voldemort used to wear the Death Eater's illager rig, pointed hat and all, in one flat black noise with a
grey face. This paints his own body from where every texel sits on the new model
(tools/texture_audit/paint.py):

  head    chalk-pale bald skull with faint blue veins at the temples and crown, sunken sockets and red
          slit eyes, no nose but two slit nostrils on a flat face, a thin lipless mouth, hollow cheeks
  neck    pale, tendons in shade, under a standing collar
  robes   near-black with a cool cast: soft vertical folds that follow the cloth, a wrapped front with a
          seam from his left shoulder to his right hip,
          bell sleeves dark inside, a deep green-black lining to the cape, the skirt hem and the cape's
          foot left ragged (cut-out texels: the model renders with alpha cutout)
  hands   long pale fingers with grey nails, knuckles in shade
  feet    bare, pale, under the robe's edge

The eyes layer is additive (RenderType.eyes): the slits burn red with a dim halo in the sockets.

Run (after bash tools/texture_audit/run.sh has dumped the models):
    python tools/gen_voldemort_texture.py [--preview out.png]
"""
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, 'texture_audit'))
from paint import texels, preview  # noqa: E402

TEX = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'hogwartsmagic', 'textures', 'entity')

SKIN = [(132, 140, 143), (156, 164, 166), (178, 186, 186), (198, 205, 203), (215, 221, 218), (230, 234, 230)]
VEIN = (150, 162, 180)
SOCKET = [(70, 72, 80), (96, 100, 108)]
IRIS = (196, 30, 26)
NOSTRIL = (92, 96, 104)
LIP = (120, 112, 118)
MOUTH = (90, 84, 92)
NAIL = (150, 152, 150)
ROBE = [(8, 8, 11), (13, 13, 17), (19, 19, 24), (26, 26, 32), (35, 35, 42), (47, 47, 56), (60, 60, 70)]
LINING = [(10, 16, 14), (15, 23, 20), (21, 31, 27)]
VOID = (4, 4, 6)

rng = random.Random(1926)


def pick(ramp, level, jitter=0.3):
    i = int(round(level + rng.uniform(-jitter, jitter)))
    return ramp[max(0, min(len(ramp) - 1, i))]


def fold(t, period=3.2, phase=0.0):
    """A soft vertical fold, from where the texel is across the cloth (world x and z, so the folds wrap
    round a panel instead of restarting on every face)."""
    w = t.world
    s = w[0] * 16 + w[2] * 16 * 0.7 + phase
    return math.sin(s * 2 * math.pi / period) + 0.45 * math.sin(s * 2 * math.pi / (period * 2.3) + 1.7)


# 6x7 face, columns run Voldemort's right -> left, rows top -> bottom.
# S skin, h highlight, b brow shade, o socket, E eye, n bridge, N nostril, c hollow cheek, m mouth, j jaw
FACE = [
    "hhhhhh",
    "SShhSS",
    "ooSSoo",
    "cEnnEc",
    "SSNNSS",
    "cmmmmc",
    "jSSSSj",
]
EYES = {(1, 3), (4, 3)}


def head(t):
    nx, ny, nz = t.n
    f = t.face
    if t.cube == 1:                                   # crown cap
        if f == 'top':
            return pick(SKIN, 4.3, 0.3)
        return pick(SKIN, 3.6, 0.3)
    if f == 'front':
        col, row = min(5, int(nx * 6)), min(6, int(ny * 7))
        ch = FACE[row][col]
        if ch == 'h':
            return pick(SKIN, 4.4, 0.3)
        if ch == 'o':
            return SOCKET[1]
        if ch == 'E':
            return IRIS
        if ch == 'n':
            return SKIN[4]
        if ch == 'N':
            return NOSTRIL
        if ch == 'c':
            return SKIN[2]
        if ch == 'm':
            return LIP if col in (1, 4) else MOUTH
        if ch == 'j':
            return SKIN[2]
        return pick(SKIN, 3.6 - 0.6 * ny, 0.25)
    if f == 'top':
        if abs(math.sin(t.local[0] * 1.3 + t.local[2] * 0.9)) < 0.12 and rng.random() < 0.6:
            return VEIN
        return pick(SKIN, 3.9, 0.3)
    if f == 'bottom':
        return SKIN[1]
    if f == 'back':
        level = 3.4 - 1.0 * ny
        if 0.1 < ny < 0.6 and abs(math.sin(t.local[0] * 1.1 + ny * 5.0)) < 0.1:
            return VEIN
        return pick(SKIN, level, 0.3)
    # sides: temple veins, a small flat ear, the jaw falling into shade
    if 0.38 < ny < 0.62 and 0.45 < nz < 0.66:
        return SKIN[2] if ny < 0.5 else SKIN[1]
    if 0.1 < ny < 0.42 and nz < 0.45 and abs(math.sin(nz * 9.0 + ny * 6.0)) < 0.16:
        return VEIN
    level = 3.6 - 1.2 * ny - (0.6 if nz < 0.2 and ny > 0.6 else 0)
    return pick(SKIN, level, 0.3)


def neck(t):
    nx, ny, nz = t.n
    if t.face == 'front' and abs(nx - 0.5) > 0.3:
        return SKIN[1]                                # tendons in shade
    return pick(SKIN, 2.6 - 0.8 * ny, 0.3)


def collar(t):
    nx, ny, nz = t.n
    if t.face == 'top':
        if 0.3 < nx < 0.7 and 0.3 < nz < 0.7:
            return SKIN[1]                            # the neck coming out of it
        return ROBE[5] if (nz < 0.2 or nz > 0.8 or nx < 0.1 or nx > 0.9) else ROBE[3]
    if t.face == 'bottom':
        return ROBE[0]
    return pick(ROBE, 4.2 - 1.6 * ny, 0.4)


def seam_line(nx, ny):
    """The wrap seam on the front, from his left shoulder (nx 1, ny 0) to his right hip."""
    return ny - (1.0 - nx) * 0.9


def torso(t):
    nx, ny, nz = t.n
    f = t.face
    if f == 'front':
        d = seam_line(nx, ny)
        if -0.06 < d < 0.03:
            return ROBE[0]
        if 0.03 <= d < 0.12:
            return ROBE[5]                            # the overlapping edge catching light
        level = 3.0 + 0.7 * fold(t, 3.0) - 0.8 * ny - (0.8 if d < 0 else 0)
        return pick(ROBE, level, 0.35)
    if f == 'top':
        return pick(ROBE, 4.6, 0.4)
    if f == 'bottom':
        return ROBE[0]
    level = 2.6 + 0.6 * fold(t, 3.0) - 0.8 * ny - (0.5 if f == 'back' else 0)
    return pick(ROBE, level, 0.35)


def ragged(t, rows_from_bottom, frac):
    """True where the cloth's foot is torn away: ragged tongues along the bottom rows."""
    h = t.size[1]
    from_bottom = (1.0 - t.n[1]) * h
    if from_bottom > rows_from_bottom:
        return False
    k = int(math.floor(t.world[0] * 16 + 37)) * 7 + int(math.floor(t.world[2] * 16 + 53)) * 13
    tongue = (math.sin(k * 1.37) * 0.5 + 0.5)
    return tongue < frac * (1.0 - from_bottom / (rows_from_bottom + 0.6))


def cape(t):
    nx, ny, nz = t.n
    f = t.face
    if f in ('front', 'back', 'left', 'right') and ragged(t, 2.2, 0.75):
        return None
    if f == 'front':                                  # the inside, toward his back: the lining
        return pick(LINING, 1.4 + 0.6 * fold(t, 3.6) - 0.6 * ny, 0.4)
    if f == 'top':
        return ROBE[4]
    if f == 'bottom':
        return ROBE[0]
    if f in ('left', 'right'):
        return pick(ROBE, 2.0 - 0.6 * ny, 0.3)
    level = 3.0 + 0.9 * fold(t, 3.6, 1.0) - 1.2 * ny + (0.6 if ny < 0.08 else 0)
    return pick(ROBE, level, 0.35)


def skirt(t):
    nx, ny, nz = t.n
    f = t.face
    if t.cube == 1:                                   # the flared hem
        if f == 'bottom':
            return ROBE[0]
        if f == 'top':
            return ROBE[3]
        if ragged(t, 1.6, 0.85):
            return None
        return pick(ROBE, 2.2 + 0.7 * fold(t, 3.0) - 0.8 * ny, 0.35)
    if f == 'top':
        return ROBE[3]
    if f == 'bottom':
        return ROBE[0]
    level = 2.8 + 0.8 * fold(t, 3.0) - 0.9 * ny - (0.4 if f == 'back' else 0)
    if f == 'front' and abs(t.world[0]) * 16 < 0.6:
        level -= 1.2                                  # where the two panels meet
    return pick(ROBE, level, 0.35)


def arm(t):
    nx, ny, nz = t.n
    f = t.face
    if t.cube == 0:                                   # upper arm in robe
        if f == 'top':
            return pick(ROBE, 4.4, 0.4)
        if f == 'bottom':
            return ROBE[0]
        return pick(ROBE, 3.2 + 0.5 * math.sin(ny * 9.0 + nx * 2.0) - 0.8 * ny - (0.5 if f == 'back' else 0), 0.35)
    if t.cube == 1:                                   # hand
        if f == 'top':
            return SKIN[2]
        return pick(SKIN, 3.2 if f == 'front' else 2.4, 0.3)
    # fingers: long, a knuckle crease, grey nails at the tips
    if f == 'bottom':
        return NAIL
    if ny > 0.7:
        return NAIL if f == 'front' else SKIN[2]
    if 0.3 < ny < 0.42:
        return SKIN[1]
    if f in ('left', 'right'):
        return SKIN[2]
    return pick(SKIN, 3.4 - 0.6 * ny, 0.25)


def sleeve(t):
    nx, ny, nz = t.n
    f = t.face
    if f == 'bottom':
        return VOID if 0.2 < nx < 0.8 and 0.2 < nz < 0.8 else ROBE[1]
    if f == 'top':
        return ROBE[3]
    if ny > 0.75:
        return ROBE[5] if f == 'front' else ROBE[4]  # the cuff's turned edge
    return pick(ROBE, 3.0 + 0.6 * math.sin(nx * 7.0 + ny * 3.0) - 0.6 * ny - (0.5 if f == 'back' else 0), 0.35)


def leg(t):
    nx, ny, nz = t.n
    f = t.face
    if t.cube == 1:                                   # toes
        if f == 'front':
            return NAIL if int(t.local[0] * 2) % 2 == 0 else SKIN[3]
        if f == 'bottom':
            return SKIN[1]
        return SKIN[3] if f == 'top' else SKIN[2]
    if f == 'bottom':
        return SKIN[1]
    if ny > 0.9:                                      # bare feet
        if f == 'top':
            return SKIN[3]
        return pick(SKIN, 2.8, 0.3)
    if ny > 0.8:
        return ROBE[1]                                # shadow of the hem on the ankle
    return pick(ROBE, 2.0 - 0.6 * ny, 0.3)


def eyes_layer(t):
    if t.part.endswith('/head') and t.cube == 0 and t.face == 'front':
        col, row = min(5, int(t.n[0] * 6)), min(6, int(t.n[1] * 7))
        if (col, row) in EYES:
            return (255, 54, 36)
        if row in (2, 3, 4) and col in (0, 1, 2, 3, 4, 5) and any(abs(col - c) + abs(row - r) == 1 for c, r in EYES):
            return (70, 6, 4)
    return None


def paint():
    img = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    eyes = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    px, ex = img.load(), eyes.load()
    for t in texels('voldemort'):
        p = t.part
        if p.endswith('/head'):
            c = head(t)
        elif p.endswith('/cape'):
            c = cape(t)
        elif p.endswith('_sleeve'):
            c = sleeve(t)
        elif p.endswith('_arm'):
            c = arm(t)
        elif p.endswith('_skirt'):
            c = skirt(t)
        elif p.endswith('_leg'):
            c = leg(t)
        elif p.endswith('/body'):
            c = (neck, torso, collar)[t.cube](t)
        else:
            continue
        if c is not None:
            px[t.u, t.v] = c + (255,)
        g = eyes_layer(t)
        if g is not None:
            ex[t.u, t.v] = g + (255,)
    return img, eyes


def main():
    img, eyes = paint()
    img.save(os.path.join(TEX, 'voldemort.png'))
    eyes.save(os.path.join(TEX, 'voldemort_eyes.png'))
    print('wrote voldemort.png and voldemort_eyes.png')
    if '--preview' in sys.argv:
        preview('voldemort', img, sys.argv[sys.argv.index('--preview') + 1])


if __name__ == '__main__':
    main()
