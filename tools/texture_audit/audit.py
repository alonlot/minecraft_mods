"""Score entity textures against the UV regions their models really sample, and render previews.

    python audit.py quads.json TEXTURE_DIR OUT_DIR

quads.json comes from AtlasDump (run.sh). For every (model, texture) pair this prints:
  size     the png size vs the size the model declares (ratio must match)
  holes    % of sampled texels that are fully transparent (fine for cutouts, bad on solid parts)
  stray    % of the png's opaque texels that no face samples (paint that never shows: a layout
           drawn for some other UV map)
  flat     % of faces painted one single colour (placeholder / programmer art)
and writes OUT_DIR/<texture>.png: front, three-quarter, side, back, top views plus the atlas with
the sampled regions outlined.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

PAIRS = [
    ('acromantula', 'acromantula'), ('basilisk', 'basilisk'), ('boggart', 'boggart'),
    ('dementor', 'dementor'), ('giant', 'giant'), ('goblin', 'goblin'), ('goblin', 'goblin_guard'),
    ('grindylow', 'grindylow'), ('hippogriff', 'hippogriff'), ('house_elf', 'house_elf'),
    ('house_elf', 'house_elf_free'), ('horntail', 'hungarian_horntail'),
    ('horntail', 'ukrainian_ironbelly'), ('inferius', 'inferius'), ('merperson', 'merperson'),
    ('merperson', 'merchieftainess'), ('minator', 'minator'), ('patronus', 'patronus_form'),
    ('snake', 'snake'), ('troll', 'troll'), ('willow', 'whomping_willow'),
    ('lycan', 'lycan_werewolf'), ('cart', 'gringotts_cart'), ('owl', 'owl'),
    ('wizard', 'texture'), ('wizard', 'voldemort'), ('student', 'student_gryffindor'),
    ('student', 'student_hufflepuff'), ('student', 'student_ravenclaw'),
    ('student', 'student_slytherin'), ('statue', 'living_statue'), ('snitch', 'golden_snitch'),
    ('broom_training', 'broom_shooting_star_v2'), ('broom_nimbus', 'broom_nimbus_2000_v2'),
    ('broom_firebolt', 'broom_firebolt_v2'),
]
TILE = 300


def quads_of(model):
    out = []
    for q in model['quads']:
        v = np.array(q['v'], dtype=float).reshape(4, 5)
        out.append((q['p'], v[:, :3], v[:, 3:]))
    return out


def uv_rects(quads, w, h):
    rects = []
    for _, _, uv in quads:
        u0, u1 = uv[:, 0].min() * w, uv[:, 0].max() * w
        v0, v1 = uv[:, 1].min() * h, uv[:, 1].max() * h
        rects.append((int(math.floor(u0 + 1e-4)), int(math.floor(v0 + 1e-4)),
                      int(math.ceil(u1 - 1e-4)), int(math.ceil(v1 - 1e-4))))
    return rects


def render(quads, T, az, el, size=TILE):
    th, tw = T.shape[:2]
    P = np.concatenate([q[1] for q in quads])
    lo, hi = P.min(0), P.max(0)
    center = (lo + hi) / 2
    radius = max(1e-3, np.linalg.norm(hi - lo) / 2)
    a, e = math.radians(az), math.radians(el)
    fwd = -np.array([math.cos(e) * math.sin(a), math.sin(e), math.cos(e) * math.cos(a)])
    right = np.cross(fwd, [0, 1, 0]); right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    scale = size * 0.48 / radius
    img = np.zeros((size, size, 3)); img[:] = (0.56, 0.64, 0.58)
    zb = np.full((size, size), np.inf)
    light = np.array([0.3, 0.9, 0.5]); light /= np.linalg.norm(light)
    for _, W, UV in quads:
        W = W - center
        n = np.cross(W[1] - W[0], W[2] - W[0])
        if np.linalg.norm(n) < 1e-9:
            continue
        n /= np.linalg.norm(n)
        if n @ fwd > 0:
            n = -n
        bright = 0.5 + 0.5 * max(0.0, n @ light)
        S = np.stack([size / 2 + (W @ right) * scale, size / 2 - (W @ up) * scale, W @ fwd], axis=1)
        for tri in ((0, 1, 2), (0, 2, 3)):
            p = S[list(tri)]; q = UV[list(tri)]
            xmin, xmax = int(max(0, p[:, 0].min())), int(min(size - 1, p[:, 0].max() + 1))
            ymin, ymax = int(max(0, p[:, 1].min())), int(min(size - 1, p[:, 1].max() + 1))
            if xmin > xmax or ymin > ymax:
                continue
            d = (p[1, 1] - p[2, 1]) * (p[0, 0] - p[2, 0]) + (p[2, 0] - p[1, 0]) * (p[0, 1] - p[2, 1])
            if abs(d) < 1e-9:
                continue
            xs, ys = np.meshgrid(np.arange(xmin, xmax + 1) + 0.5, np.arange(ymin, ymax + 1) + 0.5)
            w0 = ((p[1, 1] - p[2, 1]) * (xs - p[2, 0]) + (p[2, 0] - p[1, 0]) * (ys - p[2, 1])) / d
            w1 = ((p[2, 1] - p[0, 1]) * (xs - p[2, 0]) + (p[0, 0] - p[2, 0]) * (ys - p[2, 1])) / d
            w2 = 1 - w0 - w1
            m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            z = w0 * p[0, 2] + w1 * p[1, 2] + w2 * p[2, 2]
            uu = (w0 * q[0, 0] + w1 * q[1, 0] + w2 * q[2, 0]) * tw
            vv = (w0 * q[0, 1] + w1 * q[1, 1] + w2 * q[2, 1]) * th
            ui = np.clip(uu.astype(int), 0, tw - 1); vi = np.clip(vv.astype(int), 0, th - 1)
            texel = T[vi, ui]
            m &= texel[..., 3] > 0.1
            yy, xx = np.nonzero(m)
            Y, X = yy + ymin, xx + xmin
            c = z[yy, xx] < zb[Y, X]
            yy, xx, Y, X = yy[c], xx[c], Y[c], X[c]
            zb[Y, X] = z[yy, xx]
            img[Y, X] = texel[yy, xx, :3] * bright
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype('uint8'))


def main():
    data = json.load(open(sys.argv[1]))
    texdir, outdir = sys.argv[2], sys.argv[3]
    os.makedirs(outdir, exist_ok=True)
    print('%-26s %-10s %-9s %6s %6s %6s' % ('texture', 'png', 'model', 'holes', 'stray', 'flat'))
    for mname, tname in PAIRS:
        if mname not in data:
            continue
        model = data[mname]
        path = os.path.join(texdir, tname + '.png')
        if not os.path.exists(path):
            print('%-26s MISSING' % tname)
            continue
        im = Image.open(path).convert('RGBA')
        w, h = im.size
        T = np.asarray(im, dtype=float) / 255
        quads = quads_of(model)
        rects = uv_rects(quads, w, h)
        used = np.zeros((h, w), bool)
        flat = 0
        faces = 0
        for (u0, v0, u1, v1) in rects:
            u0, v0, u1, v1 = max(0, u0), max(0, v0), min(w, u1), min(h, v1)
            if u1 <= u0 or v1 <= v0:
                continue
            used[v0:v1, u0:u1] = True
            reg = T[v0:v1, u0:u1].reshape(-1, 4)
            reg = reg[reg[:, 3] > 0.1]
            if (u1 - u0) * (v1 - v0) >= 4:
                faces += 1
                if len(reg) and len(np.unique((reg[:, :3] * 255).astype(int), axis=0)) == 1:
                    flat += 1
        alpha = T[..., 3] > 0.1
        holes = 100.0 * (used & ~alpha).sum() / max(1, used.sum())
        stray = 100.0 * (alpha & ~used).sum() / max(1, alpha.sum())
        flatp = 100.0 * flat / max(1, faces)
        ratio_ok = abs(w / h - model['tw'] / model['th']) < 1e-6
        print('%-26s %-10s %-9s %5.1f%% %5.1f%% %5.1f%%%s' % (
            tname, '%dx%d' % (w, h), '%dx%d' % (model['tw'], model['th']), holes, stray, flatp,
            '' if ratio_ok else '  RATIO MISMATCH'))
        views = [(0, 10), (40, 25), (90, 5), (180, 15), (0, 80)]
        tiles = [render(quads, T, az, el) for az, el in views]
        atlas_w = TILE * 2
        sc = atlas_w / max(w, h)
        bg = Image.new('RGBA', im.size, (255, 0, 255, 255)); bg.alpha_composite(im)
        at = bg.convert('RGB').resize((int(w * sc), int(h * sc)), Image.NEAREST)
        dr = ImageDraw.Draw(at)
        for (u0, v0, u1, v1) in rects:
            if u1 <= u0 or v1 <= v0:
                continue
            dr.rectangle([u0 * sc, v0 * sc, max(u0 * sc, u1 * sc - 1), max(v0 * sc, v1 * sc - 1)], outline=(0, 255, 255))
        sheet = Image.new('RGB', (TILE * 5, TILE + at.size[1]), (20, 20, 20))
        for i, t in enumerate(tiles):
            sheet.paste(t, (i * TILE, 0))
        sheet.paste(at, (0, TILE))
        sheet.save(os.path.join(outdir, tname + '.png'))


if __name__ == '__main__':
    main()
