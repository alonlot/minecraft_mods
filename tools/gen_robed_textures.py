"""
Re-shade the robed humanoids: the Death Eater (texture.png, WizardModel) and the four house
students (student_*.png, StudentModel), plus the Gringotts goblins' suits (goblin*.png).

The originals painted every robe, sleeve, hat and trouser face one flat black, so in game they read
as cut-out silhouettes. This keeps everything the originals drew in colour (faces, hair, house
trim, crests, shirts and ties, hands) and repaints only the flat cloth, texel by texel from where
it sits on the model (tools/texture_audit/paint.py):

  wool robes   vertical folds that deepen and flare toward the hem, lit shoulders, a dark hem
  sleeves      the same folds with a turned-back cuff
  hats         felt with a crease up the crown and a lit brim edge; the brim's underside is filled
  trousers     charcoal, with leather shoes
  goblins      the banker's tailored suit shaded without the robe flare, leather shoes on both
  Death Eater  a silver mask with engraved scrollwork, black eye holes and a mouth grille in place
               of the grey face, a hood around it, and the gold trim turned to tarnished silver

Run (after bash tools/texture_audit/run.sh has dumped the models):
    python tools/gen_robed_textures.py [--preview DIR]
"""
import io
import math
import os
import random
import subprocess
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, 'texture_audit'))
from paint import texels, preview  # noqa: E402

# the hand-drawn originals this re-shades, read from git so re-running is idempotent
SOURCE_COMMIT = '3d22654d'
TEXDIR = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'hogwartsmagic',
                      'textures', 'entity')

WOOL = [(9, 9, 11), (15, 15, 18), (21, 21, 25), (28, 28, 33), (37, 37, 43), (49, 49, 56)]
FELT = [(10, 10, 12), (16, 15, 18), (23, 22, 26), (31, 30, 35), (42, 41, 47), (56, 55, 62)]
TROUSER = [(22, 23, 26), (30, 31, 35), (39, 40, 45), (50, 51, 57)]
SHOE = [(16, 12, 10), (26, 20, 16), (40, 31, 25), (62, 50, 40)]
SILVER = [(52, 54, 60), (84, 86, 94), (120, 122, 131), (158, 160, 168), (196, 198, 205), (228, 229, 234)]
SKIN_PALE = [(150, 138, 128), (178, 166, 155), (200, 189, 178)]


def is_flat_black(c):
    return c[3] > 0 and max(c[:3]) < 40


def is_gold(c):
    r, g, b = c[:3]
    return c[3] > 0 and r > 90 and r > b + 40 and g > b + 15


def clamp(i, n):
    return max(0, min(n - 1, int(round(i))))


def cloth(t, ramp, rng, fold_px=3.2, flare=True, base=2.7):
    """Wool or felt at texel t: vertical folds, deeper toward the bottom, lit from above."""
    nx, ny, nz = t.n
    if t.face == 'top':
        return ramp[clamp(base + 0.8 + rng.uniform(-0.5, 0.5), len(ramp))]
    if t.face == 'bottom':
        return ramp[clamp(base - 1.6 + rng.uniform(-0.4, 0.4), len(ramp))]
    across = t.local[0] if t.face in ('front', 'back') else t.local[2]
    phase = {'front': 0.0, 'back': 1.7, 'left': 0.9, 'right': 2.6}[t.face]
    amp = (0.8 + 1.1 * ny) if flare else 0.85
    fold = math.sin(2 * math.pi * across / fold_px + phase + 0.6 * ny)
    level = base + amp * fold + rng.uniform(-0.35, 0.35)
    if ny < 0.06:
        level += 0.8                       # light catches the top edge
    if t.face == 'back':
        level -= 0.3
    if ny > 0.965:
        level = base - 1.4                 # hem in shadow
    return ramp[clamp(level, len(ramp))]


def hat(t, rng):
    nx, ny, nz = t.n
    if t.face == 'bottom':
        return FELT[1] if rng.random() < 0.7 else FELT[0]
    if t.face == 'top':
        edge = min(nx, 1 - nx, nz, 1 - nz)
        return FELT[4] if edge < 0.09 else FELT[clamp(3 + rng.uniform(-0.6, 0.4), len(FELT))]
    across = t.local[0] if t.face in ('front', 'back') else t.local[2]
    crease = abs(across) < 0.6 and t.face == 'front'
    level = 2.6 - 0.9 * ny + rng.uniform(-0.4, 0.4)
    if crease:
        level -= 1.2
    if ny < 0.2:
        level += 0.6
    return FELT[clamp(level, len(FELT))]


def trousers(t, rng):
    nx, ny, nz = t.n
    if ny > 0.8 or t.face == 'bottom':     # shoes
        if t.face == 'top':
            return SHOE[2]
        lvl = 1.2 + (0.9 if t.face == 'front' and ny < 0.9 else 0) + rng.uniform(-0.4, 0.4)
        if ny > 0.95 or t.face == 'bottom':
            lvl = 0
        return SHOE[clamp(lvl, len(SHOE))]
    across = t.local[0] if t.face in ('front', 'back') else t.local[2]
    level = 1.5 + 0.6 * math.sin(across * 2.1 + ny * 3) + rng.uniform(-0.4, 0.4)
    if ny > 0.74:
        level -= 0.8                       # trouser cuff shadow over the shoe
    return TROUSER[clamp(level, len(TROUSER))]


def sleeve(t, rng, cuff_from=0.84):
    nx, ny, nz = t.n
    if ny > cuff_from and t.face != 'bottom':
        return WOOL[3] if ny < cuff_from + 0.06 else WOOL[2]     # turned-back cuff
    return cloth(t, WOOL, rng, fold_px=2.6, flare=False, base=2.2)


def silver(level, rng=None):
    if rng is not None:
        level += rng.uniform(-0.3, 0.3)
    return SILVER[clamp(level, len(SILVER))]


# Death Eater mask on the 8x10 face: columns run the model's right -> left, rows top -> bottom.
# H hood, h hood edge, S silver, L lit silver, s engraved line, E eye hole, d shadow under the
# eye, g grille slot
MASK = [
    "HHHHHHHH",
    "HhSLLShH",
    "hSsLLsSh",
    "SsLSSLsS",
    "SEESSEES",
    "SdSLLSdS",
    "SsSSSSsS",
    "SSsLLsSS",
    "hSgSgSgh",
    "hhSSSShh",
]


def death_eater_head(t, old, rng):
    nx, ny, nz = t.n
    if t.face == 'front':
        ch = MASK[min(9, int(ny * 10))][min(7, int(nx * 8))]
        if ch == 'H':
            return cloth(t, WOOL, rng, base=1.6, flare=False)
        if ch == 'h':
            return WOOL[3]
        if ch == 'E':
            return (6, 5, 6)
        if ch == 'd':
            return SILVER[1]
        if ch == 'g':
            return SILVER[0]
        if ch == 's':
            return silver(1.6, rng)
        if ch == 'L':
            return silver(4.6, rng)
        return silver(3.2 - 0.9 * ny, rng)
    if t.face in ('left', 'right') and nz < 0.13 and 0.12 < ny < 0.92:
        return silver(2.0, rng)            # the mask wraps a texel round each cheek
    if t.face == 'top':
        return WOOL[2]
    return cloth(t, WOOL, rng, base=1.8, flare=False)   # hood


def death_eater_nose(t, rng):
    if t.face == 'front':
        return silver(4.2 if t.n[0] < 0.5 else 3.4, rng)
    if t.face == 'bottom':
        return SILVER[1]
    return silver(2.6, rng)


def death_eater(old):
    img = old.copy()
    px = img.load()
    rng = random.Random(1997)
    for t in texels('wizard'):
        c = old.getpixel((t.u, t.v))
        p = t.part
        if p == '/head':
            px[t.u, t.v] = death_eater_head(t, c, rng) + (255,)
        elif p == '/nose':
            px[t.u, t.v] = death_eater_nose(t, rng) + (255,)
        elif p == '/head/bone':
            if c[3] > 0 or t.face == 'bottom':
                px[t.u, t.v] = hat(t, rng) + (255,)
        elif p == '/body':
            if c[3] == 0 and t.face != 'front':
                continue
            if c[3] == 0:
                continue
            if is_gold(c):
                lum = (c[0] + c[1] + c[2]) / 3
                px[t.u, t.v] = silver(0.4 + (lum - 90) / 90.0) + (255,)   # pewter, not bright
            elif t.cube == 1:
                px[t.u, t.v] = cloth(t, WOOL, rng) + (255,)
            else:
                px[t.u, t.v] = cloth(t, WOOL, rng, base=1.6, flare=False) + (255,)
        elif p.endswith('arm'):
            if t.n[1] > 0.88 or t.face == 'bottom':
                px[t.u, t.v] = SKIN_PALE[clamp(1.4 - (0.8 if t.face == 'bottom' else 0) + rng.uniform(-0.5, 0.5), 3)] + (255,)
            else:
                px[t.u, t.v] = sleeve(t, rng, cuff_from=0.76) + (255,)
        elif p.endswith('leg'):
            px[t.u, t.v] = trousers(t, rng) + (255,)
    return img


HAIR = {   # the originals were bald under the hat; one hair colour per house texture
    'gryffindor': [(52, 32, 22), (70, 44, 30), (92, 60, 40), (112, 76, 52)],
    'hufflepuff': [(104, 80, 46), (132, 104, 62), (160, 130, 82), (186, 158, 104)],
    'ravenclaw':  [(16, 14, 16), (26, 23, 26), (38, 34, 38), (54, 49, 54)],
    'slytherin':  [(150, 144, 120), (184, 178, 150), (212, 207, 182), (234, 230, 210)],
}


def hair(t, ramp, rng):
    nx, ny, nz = t.n
    f = t.face
    if f == 'front':
        on = ny < 0.1
    elif f in ('left', 'right'):
        on = ny < 0.3 or (nz > 0.45 and ny < 0.78)
    elif f == 'back':
        on = ny < 0.82
    else:
        on = f == 'top'
    if not on:
        return None
    across = t.local[0] if f in ('front', 'back') else t.local[2]
    level = 1.6 + 0.9 * math.sin(across * 2.3 + (0 if f == 'back' else 1.3)) + rng.uniform(-0.5, 0.5)
    if f != 'top' and ny > 0.6:
        level -= 0.7                       # ends tuck into shadow at the nape
    return ramp[clamp(level, len(ramp))]


def student(old, seed, house):
    img = old.copy()
    px = img.load()
    rng = random.Random(seed)
    for t in texels('student'):
        c = old.getpixel((t.u, t.v))
        p = t.part
        if p == '/head':
            h = hair(t, HAIR[house], rng)
            if h is not None:
                px[t.u, t.v] = h + (255,)
        elif p == '/bone':
            if is_flat_black(c) or (c[3] == 0 and t.face == 'bottom'):
                px[t.u, t.v] = hat(t, rng) + (255,)
        elif p == '/bodywear':
            if is_flat_black(c):
                px[t.u, t.v] = cloth(t, WOOL, rng) + (255,)
        elif p == '/arms':
            if is_flat_black(c):
                px[t.u, t.v] = sleeve(t, rng) + (255,)
        elif p.endswith('leg'):
            if is_flat_black(c) or c[3] == 0:
                px[t.u, t.v] = trousers(t, rng) + (255,)
            elif max(c[:3]) < 70:
                px[t.u, t.v] = trousers(t, rng) + (255,)
    return img


def goblin(old, seed):
    """Gringotts goblins: the banker's tailored black suit gets cloth shading (no robe flare),
    and both goblins' flat black shoes become polished leather."""
    img = old.copy()
    px = img.load()
    rng = random.Random(seed)
    for t in texels('goblin'):
        c = old.getpixel((t.u, t.v))
        if not is_flat_black(c) or t.part.endswith('/head'):
            continue
        if t.part.endswith('_leg') and (t.n[1] > 0.8 or t.face == 'bottom'):
            lvl = 0 if t.face == 'bottom' else (2.4 if t.face == 'top' else 1.3 + (0.9 if t.face == 'front' else 0))
            px[t.u, t.v] = SHOE[clamp(lvl + rng.uniform(-0.3, 0.3), len(SHOE))] + (255,)
        else:
            px[t.u, t.v] = cloth(t, WOOL, rng, fold_px=4.5, flare=False, base=2.6) + (255,)
    return img


def main():
    out_dir = sys.argv[sys.argv.index('--preview') + 1] if '--preview' in sys.argv else None
    jobs = [('wizard', 'texture', lambda im: death_eater(im))]
    for i, house in enumerate(['gryffindor', 'hufflepuff', 'ravenclaw', 'slytherin']):
        jobs.append(('student', 'student_' + house, lambda im, s=i, h=house: student(im, 100 + s, h)))
    jobs.append(('goblin', 'goblin', lambda im: goblin(im, 300)))
    jobs.append(('goblin', 'goblin_guard', lambda im: goblin(im, 301)))
    for model, name, fn in jobs:
        path = os.path.join(TEXDIR, name + '.png')
        blob = subprocess.run(['git', 'show', '%s:src/main/resources/assets/hogwartsmagic/textures/entity/%s.png'
                               % (SOURCE_COMMIT, name)], cwd=os.path.join(HERE, '..'),
                              capture_output=True, check=True).stdout
        old = Image.open(io.BytesIO(blob)).convert('RGBA')
        new = fn(old)
        new.save(path)
        print('wrote', name + '.png')
        if out_dir:
            os.makedirs(out_dir, exist_ok=True)
            a = preview(model, old, os.path.join(out_dir, name + '_before.png'))
            b = preview(model, new, os.path.join(out_dir, name + '_after.png'))
            both = Image.new('RGB', (a.width, a.height * 2))
            both.paste(a, (0, 0)); both.paste(b, (0, a.height))
            both.save(os.path.join(out_dir, name + '_compare.png'))


if __name__ == '__main__':
    main()
