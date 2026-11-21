"""Paints the Sphinx texture (128x64) from the box-UV layout in SphinxModel.

Usage: python tools/sphinx/paint_sphinx.py [out_path]

A lion's tawny coat, a woman's face in warm ochre with kohl-lined eyes, and the nemes headdress in
gold and lapis stripes with the cobra on the brow. BOXES below must match the texOffs and sizes in
SphinxModel.createBodyLayer: a box moved there and not here paints fur on her face.

Face rects relative to a box's (u, v) for a w x h x d box:
  top (u+d, v, w, d)   bottom (u+d+w, v, w, d)
  side_a (u, v+d, d, h)   front (u+d, v+d, w, h)   side_b (u+d+w, v+d, d, h)   back (u+2d+w, v+d, w, h)
"front" is the face toward -z, which is the way she looks.
"""
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'assets', 'hogwartsmagic', 'textures',
                   'entity', 'sphinx.png')
W, H = 128, 64

# name: (u, v, w, h, d, kind)
BOXES = {
    'body': (0, 0, 12, 10, 26, 'fur'),
    'head': (76, 0, 8, 8, 8, 'face'),
    'lappet_left': (108, 0, 3, 11, 2, 'stripes'),
    'lappet_right': (118, 0, 3, 11, 2, 'stripes'),
    'uraeus': (108, 13, 1, 2, 1, 'gold'),
    'nemes': (76, 16, 10, 4, 10, 'nemes'),
    'nemes_back': (116, 16, 4, 5, 2, 'stripes'),
    'leg_front_left': (0, 36, 5, 9, 5, 'leg'),
    'leg_front_right': (20, 36, 5, 9, 5, 'leg'),
    'leg_back_left': (40, 36, 5, 9, 5, 'leg'),
    'leg_back_right': (60, 36, 5, 9, 5, 'leg'),
    'tail': (80, 36, 2, 2, 14, 'fur'),
    'tuft': (112, 36, 3, 3, 3, 'tuft'),
    'neck': (0, 50, 6, 5, 6, 'fur'),
}

FUR = [(214, 168, 98), (200, 152, 84), (186, 138, 74), (226, 182, 112)]
FUR_DARK = (150, 106, 56)
BELLY = (234, 206, 150)
SKIN = [(184, 124, 76), (176, 118, 72), (190, 132, 82)]
SKIN_SHADE = (148, 96, 58)
KOHL = (24, 18, 22)
PUPIL = (58, 36, 22)
EYE_WHITE = (232, 222, 200)
LIPS = (150, 74, 62)
GOLD = (226, 180, 66)
GOLD_DARK = (178, 132, 40)
LAPIS = (40, 74, 158)
LAPIS_DARK = (28, 50, 112)
TUFT = (92, 62, 34)

rng = random.Random(1907)


def faces(u, v, w, h, d):
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'side_a': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'side_b': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
    }


def fill(im, rect, fn):
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            im.putpixel((x0 + x, y0 + y), fn(x, y, w, h))


def fur(x, y, w, h):
    c = rng.choice(FUR)
    return FUR_DARK if rng.random() < 0.06 else c


def paint_fur(im, u, v, w, h, d):
    f = faces(u, v, w, h, d)
    for name, rect in f.items():
        fill(im, rect, fur)
    # Belly is pale, the spine a shade darker down the middle.
    fill(im, f['bottom'], lambda x, y, w, h: BELLY if rng.random() > 0.15 else FUR[3])
    tx, ty, tw, th = f['top']
    for y in range(th):
        im.putpixel((tx + tw // 2, ty + y), FUR_DARK if rng.random() < 0.6 else FUR[2])
    # Flanks shade toward the belly.
    for side in ('side_a', 'side_b', 'front', 'back'):
        sx, sy, sw, sh = f[side]
        for x in range(sw):
            im.putpixel((sx + x, sy + sh - 1), FUR[2])


def paint_leg(im, u, v, w, h, d):
    paint_fur(im, u, v, w, h, d)
    f = faces(u, v, w, h, d)
    for side in ('side_a', 'front', 'side_b', 'back'):
        sx, sy, sw, sh = f[side]
        for x in range(sw):
            for y in range(sh - 3, sh):
                im.putpixel((sx + x, sy + y), BELLY if y < sh - 1 else FUR[1])
        if side == 'front':
            # Claws between the toes.
            for x in range(1, sw, 2):
                im.putpixel((sx + x, sy + sh - 1), FUR_DARK)
    # Pads: one tone, with the toe lines between them.
    fill(im, f['bottom'], lambda x, y, w, h: FUR_DARK if x in (1, 3) else FUR[2])


def paint_face(im, u, v, w, h, d):
    f = faces(u, v, w, h, d)
    for rect in f.values():
        fill(im, rect, lambda x, y, w, h: rng.choice(SKIN))
    fx, fy, fw, fh = f['front']
    # Rows 0-2 sit under the nemes band; the face shows from row 3.
    px = lambda x, y, c: im.putpixel((fx + x, fy + y), c)
    for x in (1, 2, 5, 6):
        px(x, 3, SKIN_SHADE)                          # brows
    for x, c in ((0, KOHL), (1, EYE_WHITE), (2, PUPIL), (5, PUPIL), (6, EYE_WHITE), (7, KOHL)):
        px(x, 4, c)                                   # long kohl-lined eyes
    px(1, 5, KOHL)
    px(6, 5, KOHL)
    px(3, 5, SKIN_SHADE)                              # the shade beside the nose
    px(4, 5, SKIN[2])
    px(3, 6, LIPS)
    px(4, 6, LIPS)
    for x in range(fw):
        px(x, 7, SKIN_SHADE if x in (0, 7) else SKIN[0])
    for side in ('side_a', 'side_b'):
        sx, sy, sw, sh = f[side]
        for y in range(sh):
            im.putpixel((sx + sw // 2, sy + y), SKIN_SHADE)


def stripes(x, y, w, h):
    return (GOLD if y % 2 == 0 else LAPIS) if rng.random() > 0.04 else (GOLD_DARK if y % 2 == 0 else LAPIS_DARK)


def paint_stripes(im, u, v, w, h, d):
    for rect in faces(u, v, w, h, d).values():
        fill(im, rect, stripes)


def paint_nemes(im, u, v, w, h, d):
    f = faces(u, v, w, h, d)
    for name in ('side_a', 'side_b', 'back'):
        fill(im, f[name], stripes)
    # The brow band: plain gold with a dark edge, the cobra rises from its middle.
    fill(im, f['front'], lambda x, y, w, h: GOLD_DARK if y == h - 1 else GOLD)
    # The crown: stripes running front to back.
    fill(im, f['top'], lambda x, y, w, h: GOLD if x % 2 == 0 else LAPIS)
    fill(im, f['bottom'], lambda x, y, w, h: LAPIS_DARK)


def paint_gold(im, u, v, w, h, d):
    for rect in faces(u, v, w, h, d).values():
        fill(im, rect, lambda x, y, w, h: GOLD if y > 0 else GOLD_DARK)
    fx, fy, _, _ = faces(u, v, w, h, d)['front']
    im.putpixel((fx, fy), (40, 160, 120))           # the cobra's eye


def paint_tuft(im, u, v, w, h, d):
    for rect in faces(u, v, w, h, d).values():
        fill(im, rect, lambda x, y, w, h: TUFT if rng.random() > 0.2 else FUR_DARK)


PAINTERS = {'fur': paint_fur, 'leg': paint_leg, 'face': paint_face, 'stripes': paint_stripes,
            'nemes': paint_nemes, 'gold': paint_gold, 'tuft': paint_tuft}


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else OUT
    im = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    for name, (u, v, w, h, d, kind) in BOXES.items():
        assert u + 2 * (w + d) <= W and v + d + h <= H, name + ' runs off the texture'
        PAINTERS[kind](im, u, v, w, h, d)
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    im.save(out)
    print('wrote', out)


if __name__ == '__main__':
    main()
