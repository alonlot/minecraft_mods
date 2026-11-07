"""Paints the unicorn textures (adult, foal, horn glow) from the box-UV layout in layout.json.

Usage: python tools/unicorn/paint_unicorn.py [out_dir]
Writes unicorn.png, unicorn_foal.png and unicorn_glow.png (128x64).

Face rects relative to a cube's (u, v) for a w x h x d box:
  up (u+d, v, w, d)   down (u+d+w, v, w, d)
  side_a (u, v+d, d, h)   front (u+d, v+d, w, h)   side_b (u+d+w, v+d, d, h)   back (u+2d+w, v+d, w, h)
side_a's right edge and side_b's left edge touch the front face.
"""
import json
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
W, H = 128, 64


def hx(s):
    s = s.lstrip('#')
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


ADULT = {
    'coat': [hx('FDFDFF'), hx('EEF0F7'), hx('CDD1E0'), hx('A3A9C2')],
    'hair': [hx('FFFFFF'), hx('E1E6F3'), hx('BFC8DF'), hx('98A3C2')],
    'tints': [hx('E9E2FF'), hx('DDF2FA'), hx('FFF0F6')],
    'hoof': [hx('F6D97F'), hx('DDA941'), hx('A97A22'), hx('7E5714')],
    'pink': hx('D9C7D0'),
    'nostril': hx('8C7C88'),
    'eye_dark': hx('1A1630'),
    'eye_mid': hx('2C2747'),
    'eye_glint': hx('8FA6F0'),
    'lash': hx('7F8197'),
    'sparkle': hx('F4F0FF'),
}
FOAL = {
    'coat': [hx('FCE9A8'), hx('EDC867'), hx('C99B3A'), hx('9A6F22')],
    'hair': [hx('FFF6D2'), hx('F5E0A0'), hx('E4C677'), hx('C9A452')],
    'tints': [hx('FFF9E2'), hx('F9E6B8'), hx('FFEFD0')],
    'hoof': [hx('E9C46A'), hx('C99A3C'), hx('9A6E20'), hx('6E4C12')],
    'pink': hx('E2B98D'),
    'nostril': hx('8E6A3C'),
    'eye_dark': hx('22160C'),
    'eye_mid': hx('3A2814'),
    'eye_glint': hx('E8D9A8'),
    'lash': hx('8E6A30'),
    'sparkle': hx('FFF4CF'),
}

HORN_LIGHT = hx('FBF4E4')
HORN_HI = hx('FFFFFF')
HORN_STRIPE = hx('D9CBAE')
HORN_DEEP = hx('BFAE8C')

COAT = {'body_main': (0.0, 0.42), 'chest': (0.05, 0.45), 'rump': (0.02, 0.42),
        'neck_main': (0.0, 0.28), 'skull': (0.0, 0.2), 'muzzle': (0.05, 0.3), 'jaw_main': (0.35, 0.45),
        'ear_left_c': (0.0, 0.15), 'ear_right_c': (0.0, 0.15)}
for side in ('front_left', 'front_right'):
    COAT['upper_' + side] = (0.32, 0.5)
    COAT['cannon_' + side] = (0.45, 0.62)
for side in ('back_left', 'back_right'):
    COAT['thigh_' + side] = (0.18, 0.5)
    COAT['cannon_' + side] = (0.45, 0.62)
HAIR = {'mane_crest', 'mane_fall', 'forelock', 'tail_base', 'tail_hair'}


def ramp(stops, t):
    t = max(0.0, min(1.0, t))
    seg = t * (len(stops) - 1)
    i = min(int(seg), len(stops) - 2)
    return lerp(stops[i], stops[i + 1], seg - i)


def faces(c):
    u, v, w, h, d = c['u'], c['v'], c['w'], c['h'], c['d']
    return {
        'up': (u + d, v, w, d),
        'down': (u + d + w, v, w, d),
        'side_a': (u, v + d, d, h),
        'front': (u + d, v + d, w, h),
        'side_b': (u + d + w, v + d, d, h),
        'back': (u + 2 * d + w, v + d, w, h),
    }


def from_front(face, col, fw):
    """Columns counted from the edge that touches the front face (side faces only)."""
    return fw - 1 - col if face == 'side_a' else col


def paint(pal, layout, seed, foal):
    rnd = random.Random(seed)
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    px = img.load()

    def put(x, y, rgb, a=255):
        px[x, y] = (int(round(rgb[0])), int(round(rgb[1])), int(round(rgb[2])), a)

    for name, c in layout.items():
        fs = faces(c)
        if name in COAT:
            top, bot = COAT[name]
            for face, (fx, fy, fw, fh) in fs.items():
                for j in range(fh):
                    for i in range(fw):
                        if face == 'up':
                            t = max(0.0, top - 0.06)
                        elif face == 'down':
                            t = 0.72
                        else:
                            t = top + (bot - top) * (j / max(1, fh - 1)) ** 1.2
                        t += rnd.uniform(-0.035, 0.035)
                        col = ramp(pal['coat'], t)
                        if rnd.random() < 0.012 and face not in ('down',):
                            col = lerp(col, pal['sparkle'], 0.8)
                        put(fx + i, fy + j, col)
            if name == 'skull':
                for face in ('side_a', 'side_b'):
                    fx, fy, fw, fh = fs[face]

                    def at(k, row, rgb):
                        col_i = fw - 1 - k if face == 'side_a' else k
                        put(fx + col_i, fy + row, rgb)
                    at(1, 1, pal['lash'])
                    at(2, 1, pal['lash'])
                    at(1, 2, pal['eye_mid'])
                    at(2, 2, pal['eye_glint'])
                    at(1, 3, pal['eye_dark'])
                    at(2, 3, pal['eye_mid'])
            if name == 'muzzle':
                fx, fy, fw, fh = fs['front']
                for j in range(fh):
                    for i in range(fw):
                        if j >= 2:
                            px[fx + i, fy + j] = tuple(int(x) for x in lerp(px[fx + i, fy + j][:3], pal['pink'], 0.55)) + (255,)
                put(fx, fy + 1, pal['nostril'])
                put(fx + fw - 1, fy + 1, pal['nostril'])
                for face in ('side_a', 'side_b'):
                    fx, fy, fw, fh = fs[face]
                    for j in range(2, fh):
                        for i in range(fw):
                            k = from_front(face, i, fw)
                            if k <= 2:
                                base = px[fx + i, fy + j][:3]
                                put(fx + i, fy + j, lerp(base, pal['pink'], 0.5 - 0.15 * k))
                    k0 = 0
                    col_i = fw - 1 - k0 if face == 'side_a' else k0
                    put(fx + col_i, fy + 1, pal['nostril'])
            if name.startswith('ear'):
                fx, fy, fw, fh = fs['front']
                for j in range(1, fh):
                    for i in range(fw):
                        put(fx + i, fy + j, lerp(px[fx + i, fy + j][:3], pal['pink'], 0.6))
            if name.startswith('cannon'):
                for face in ('side_a', 'front', 'side_b', 'back'):
                    fx, fy, fw, fh = fs[face]
                    for i in range(fw):
                        put(fx + i, fy + fh - 1, lerp(px[fx + i, fy + fh - 1][:3], pal['hair'][0], 0.6))
        elif name in HAIR:
            for face, (fx, fy, fw, fh) in fs.items():
                strand_t = [rnd.uniform(0.0, 0.75) for _ in range(fw)]
                strand_tint = [rnd.choice(pal['tints']) if rnd.random() < 0.25 else None for _ in range(fw)]
                for j in range(fh):
                    for i in range(fw):
                        if face == 'up':
                            t = 0.05 + rnd.uniform(0, 0.15)
                        elif face == 'down':
                            t = 0.55 + rnd.uniform(0, 0.2)
                        else:
                            t = strand_t[i] + 0.25 * (j / max(1, fh - 1)) + rnd.uniform(-0.05, 0.05)
                        col = ramp(pal['hair'], t)
                        if strand_tint[i] is not None and face not in ('up', 'down'):
                            col = lerp(col, strand_tint[i], 0.45)
                        put(fx + i, fy + j, col)
        elif name.startswith('horn'):
            seg = int(name[-1])
            for face, (fx, fy, fw, fh) in fs.items():
                for j in range(fh):
                    for i in range(fw):
                        r = (i + j * 2 + seg) % 4
                        if r == 0:
                            col = HORN_STRIPE
                        elif r == 1:
                            col = HORN_DEEP if face in ('back', 'down') else lerp(HORN_STRIPE, HORN_LIGHT, 0.5)
                        elif r == 3 and face in ('front', 'side_a', 'up'):
                            col = HORN_HI
                        else:
                            col = HORN_LIGHT
                        put(fx + i, fy + j, col)
        elif name.startswith('hoof'):
            for face, (fx, fy, fw, fh) in fs.items():
                for j in range(fh):
                    for i in range(fw):
                        if face == 'up':
                            col = pal['hoof'][1]
                        elif face == 'down':
                            col = pal['hoof'][3]
                        else:
                            col = pal['hoof'][0] if j == 0 else pal['hoof'][2]
                            if j == 0 and rnd.random() < 0.3:
                                col = lerp(col, (255, 255, 255), 0.4)
                        put(fx + i, fy + j, col)
        else:
            raise SystemExit('unpainted cube ' + name)
    return img


def glow(layout):
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    px = img.load()
    for name, c in layout.items():
        if not name.startswith('horn'):
            continue
        seg = int(name[-1])
        a = 90 + seg * 25
        for face, (fx, fy, fw, fh) in faces(c).items():
            for j in range(fh):
                for i in range(fw):
                    r = (i + j * 2 + seg) % 4
                    col = (255, 250, 236) if r != 0 else (225, 214, 255)
                    px[fx + i, fy + j] = col + (min(255, a),)
    return img


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'hogwartsmagic', 'textures', 'entity')
    with open(os.path.join(HERE, 'layout.json')) as f:
        layout = json.load(f)
    os.makedirs(out, exist_ok=True)
    paint(ADULT, layout, 7, False).save(os.path.join(out, 'unicorn.png'))
    paint(FOAL, layout, 7, True).save(os.path.join(out, 'unicorn_foal.png'))
    glow(layout).save(os.path.join(out, 'unicorn_glow.png'))
    print('wrote', os.path.abspath(out))


if __name__ == '__main__':
    main()
