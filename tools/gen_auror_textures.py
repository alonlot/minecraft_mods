"""
Paint the three Auror textures (auror_0..2.png) for AurorRenderer, which draws the students' robed
model (StudentModel) bareheaded. Starts from the shaded Gryffindor student texture and repaints it
texel by texel from where each texel sits on the model (tools/texture_audit/paint.py):

  greatcoat   crimson wool, the student's folds and hem shadow, black lapels down the open front,
              a dark leather belt round the waist
  sleeves     crimson with turned-back black cuffs; the hands stay skin
  waistcoat   charcoal with a column of silver buttons where the house shirt and tie were
  trousers    charcoal, with boots
  hair        one colour and cut per variant: dark brown, black going grey, auburn
  eyes        brown, grey, hazel instead of the students' green

Run (after bash tools/texture_audit/run.sh has dumped the models, or with its quads.json copied in):
    python tools/gen_auror_textures.py [--preview DIR]
"""
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, 'texture_audit'))
import paint  # noqa: E402
from paint import texels, preview  # noqa: E402
from gen_robed_textures import cloth, trousers, clamp  # noqa: E402

TEXDIR = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'hogwartsmagic', 'textures', 'entity')

CRIMSON = [(40, 7, 11), (58, 11, 16), (78, 16, 21), (98, 21, 27), (120, 29, 34), (146, 41, 43)]
LAPEL = [(14, 13, 16), (22, 21, 25), (31, 30, 35)]
BELT = [(30, 21, 15), (44, 31, 22), (60, 44, 31)]
VEST = [(26, 26, 30), (34, 34, 39), (44, 44, 50)]
BUTTON = (176, 180, 190)
COLLAR = (214, 210, 200)

VARIANTS = [
    # hair ramp, eye colour, long hair (falls to the shoulders at the back)
    ([(40, 26, 18), (56, 37, 26), (74, 50, 35), (92, 64, 45)], (74, 48, 30), False),
    ([(24, 24, 26), (44, 43, 45), (88, 87, 90), (130, 129, 132)], (92, 98, 106), False),
    ([(84, 30, 14), (112, 44, 20), (140, 62, 30), (168, 84, 44)], (96, 84, 40), True),
]


def is_green_eye(c):
    r, g, b = c[:3]
    return c[3] > 0 and g > 120 and g > r + 50 and g > b + 50


def hair(t, ramp, rng, long_hair):
    nx, ny, nz = t.n
    f = t.face
    if f == 'front':
        on = ny < 0.12
    elif f in ('left', 'right'):
        on = ny < 0.32 or (nz > 0.45 and ny < (0.95 if long_hair else 0.75))
    elif f == 'back':
        on = ny < (1.01 if long_hair else 0.8)
    else:
        on = f == 'top'
    if not on:
        return None
    across = t.local[0] if f in ('front', 'back') else t.local[2]
    level = 1.6 + 0.9 * math.sin(across * 2.3 + (0 if f == 'back' else 1.3)) + rng.uniform(-0.5, 0.5)
    if f != 'top' and ny > 0.6:
        level -= 0.7
    return ramp[clamp(level, len(ramp))]


def coat(t, old_c, rng):
    """The student's open robe as a crimson greatcoat; its house-coloured edge becomes a black lapel."""
    nx, ny, nz = t.n
    if old_c[3] == 0:
        return None                                  # keep the open front open
    r, g, b = old_c[:3]
    trim = (r > 120 and g < 90) or (r > 150 and g > 110 and b < 80)
    if trim and t.face == 'front':
        return LAPEL[clamp(1 + rng.uniform(-0.6, 0.6), len(LAPEL))]
    if 0.33 <= ny <= 0.39 and t.face != 'bottom':
        return BELT[clamp(1 + (0.8 if ny < 0.35 else 0) + rng.uniform(-0.4, 0.4), len(BELT))]
    return cloth(t, CRIMSON, rng)


def sleeve(t, old_c, rng):
    nx, ny, nz = t.n
    if tuple(t.size) == (8, 4, 4):                   # the folded forearms: hands meet in the middle
        if abs(t.local[0]) < 1.6:
            return None
        return cloth(t, CRIMSON, rng, fold_px=2.6, flare=False, base=2.4) if abs(t.local[0]) > 2.6 else LAPEL[1]
    if old_c[3] > 0 and max(old_c[:3]) >= 70:
        return None                                  # hands
    if ny > 0.82 and t.face != 'bottom':
        return LAPEL[2] if ny < 0.88 else LAPEL[1]
    return cloth(t, CRIMSON, rng, fold_px=2.6, flare=False, base=2.4)


def waistcoat(t, old_c, rng):
    nx, ny, nz = t.n
    if t.face == 'front':
        if ny < 0.08:
            return COLLAR
        if abs(t.local[0]) < 0.6 and 0.12 < ny < 0.8 and int(ny * 12) % 2 == 0:
            return BUTTON
    return VEST[clamp(1 + rng.uniform(-0.7, 0.7), len(VEST))]


def auror(old, seed, variant):
    ramp, eye, long_hair = VARIANTS[variant]
    img = old.copy()
    px = img.load()
    rng = random.Random(seed)
    for t in texels('student'):
        c = old.getpixel((t.u, t.v))
        p = t.part
        new = None
        if p == '/head':
            new = hair(t, ramp, rng, long_hair)
            if new is None and is_green_eye(c):
                new = eye
        elif p == '/bodywear':
            new = coat(t, c, rng)
        elif p in ('/arms', '/arms/mirrored'):
            new = sleeve(t, c, rng)
        elif p == '/body':
            new = waistcoat(t, c, rng)
        elif p.endswith('leg'):
            new = trousers(t, rng)
        if new is not None:
            px[t.u, t.v] = tuple(new) + (255,)
    return img


def main():
    out_dir = sys.argv[sys.argv.index('--preview') + 1] if '--preview' in sys.argv else None
    base = Image.open(os.path.join(TEXDIR, 'student_gryffindor.png')).convert('RGBA')
    if out_dir:
        m = paint._model('student')
        paint._cache['auror'] = dict(m, quads=[q for q in m['quads'] if q['p'] != '/bone'])
    sheets = []
    for v in range(len(VARIANTS)):
        img = auror(base, 500 + v, v)
        img.save(os.path.join(TEXDIR, 'auror_%d.png' % v))
        print('wrote auror_%d.png' % v)
        if out_dir:
            os.makedirs(out_dir, exist_ok=True)
            sheets.append(preview('auror', img, os.path.join(out_dir, 'auror_%d.png' % v)))
    if sheets:
        both = Image.new('RGB', (sheets[0].width, sheets[0].height * len(sheets)))
        for i, s in enumerate(sheets):
            both.paste(s, (0, i * s.height))
        both.save(os.path.join(out_dir, 'auror_sheet.png'))


if __name__ == '__main__':
    main()
