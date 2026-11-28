"""
Paint the Auror and Death Eater robe textures from where each texel lands on the model.

    bash tools/wizard_robes/dump.sh          # after compileJava: bakes the models into out/quads.json
    python tools/wizard_robes/paint_robes.py [--preview out/preview.png]

Writes textures/models/armor/{auror,death_eater}_robes_{hood,coat,trousers,boots}.png. Every texture is
drawn at twice its model's box-UV density (a 64x32 layout gets a 128x64 PNG).

  Auror        black cowl lined in crimson; crimson greatcoat with a black shoulder mantle piped in silver,
               a stand collar, black lapels over a charcoal waistcoat, a leather belt and a gold badge;
               charcoal trousers; tall brown boots with silver buckles
  Death Eater  black cowl round a silver mask engraved with swirls; black robe closed with silver clasps
               and a chain across the chest, silver thread down the closure, a tattered hem and a cloak
               lined in green-black; black trousers; black boots with silver toe caps
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
sys.path.insert(0, os.path.join(ROOT, 'tools', 'texture_audit'))
import paint  # noqa: E402

OUT_DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'hogwartsmagic', 'textures', 'models', 'armor')
QUADS = os.path.join(HERE, 'out', 'quads.json')

CRIMSON = [(40, 7, 11), (58, 11, 16), (78, 16, 21), (98, 21, 27), (120, 29, 34), (146, 41, 43)]
WOOL = [(11, 10, 13), (17, 16, 20), (25, 24, 29), (34, 33, 39), (45, 44, 51)]
CHARCOAL = [(22, 22, 26), (30, 30, 35), (40, 40, 46), (52, 52, 59)]
LEATHER = [(26, 18, 13), (40, 28, 20), (56, 40, 28), (74, 54, 37), (94, 70, 48)]
SILVER = [(78, 82, 92), (118, 123, 134), (160, 165, 176), (200, 204, 213), (234, 236, 241)]
GOLD = [(110, 76, 18), (170, 128, 36), (222, 186, 78), (250, 226, 140)]
NIGHT = [(7, 7, 10), (12, 12, 17), (18, 18, 25), (26, 26, 35), (36, 36, 48), (50, 50, 66)]
LINING = [(8, 15, 11), (12, 22, 16), (17, 30, 22), (23, 40, 29)]

MASK = [
    "11222333322211",
    "12334443444321",
    "23431344313432",
    "23413211231432",
    "12322133122321",
    "12100022000121",
    "12110033001121",
    "23332234322333",
    "23411334311432",
    "13413344314431",
    "12332133123321",
    "11231000013211",
    "01223333332210",
    "00122222222100",
]
NOSE = ["3443", "3443", "2342", "2342", "1331", "0110"]
MASK_RAMP = [(28, 30, 36)] + SILVER[:4]


def pick(ramp, level):
    return ramp[max(0, min(len(ramp) - 1, int(math.floor(level))))]


def h(*key):
    """Stable 0..1 hash of a key (no RNG state, so a texel always gets the same value)."""
    x = 2166136261
    for k in key:
        for ch in str(k):
            x = ((x ^ ord(ch)) * 16777619) & 0xFFFFFFFF
    return (x % 10007) / 10007.0


def folds(a, seed=0.0, depth=1.0):
    """Cloth folds across a face, -1..1: two sines that drift so no two panels repeat."""
    return depth * (0.65 * math.sin(a * 1.9 + seed) + 0.35 * math.sin(a * 3.7 + seed * 2.3 + 1.1))


def load():
    data = json.load(open(QUADS))
    # Only the parts the armour layer draws for each slot: the meshes still carry vanilla's head, hat and
    # limbs, whose box UVs overlap the robe's extra parts and would claim their texels first.
    drawn = {
        'hood': lambda p: p.startswith('/head'),
        'robe': lambda p: p == '/body' or p.startswith('/body/') or p in ('/right_arm', '/left_arm'),
        'inner': lambda p: p in ('/body',) + LEGS,
        'outer': lambda p: p in LEGS,
    }
    for name, m in data.items():
        if name in drawn:
            m['quads'] = [q for q in m['quads'] if drawn[name](q['p'])]
        m['tw'] *= 2
        m['th'] *= 2
    paint._cache.clear()
    paint._cache.update(data)
    return data


def texture_for(model, fn):
    m = paint._cache[model]
    img = Image.new('RGBA', (m['tw'], m['th']), (0, 0, 0, 0))
    px = img.load()
    for t in paint.texels(model):
        c = fn(t)
        if c is not None:
            px[t.u, t.v] = tuple(c[:3]) + (255,)
    return img


# --------------------------------------------------------------------------------------------- coat

def across(t):
    """Position across the face in model pixels: x on front/back/top/bottom, z on the sides."""
    return t.local[2] if t.face in ('left', 'right') else t.local[0]


def coat_auror(t):
    p, f = t.part, t.face
    x, y, z = t.local
    if p == '/body':
        belt = 9.4 < y < 10.6
        if f == 'front':
            if belt:
                if abs(x) < 0.95:
                    edge = abs(x) > 0.6 or abs(y - 10.0) > 0.35
                    return SILVER[3] if edge and y < 10.0 else (SILVER[1] if edge else LEATHER[0])
                return pick(LEATHER, 2.5 - (y - 9.4) * 1.5)
            if abs(x) < 1.25:
                if abs(x) < 0.3 and (y % 2.4) < 0.5 and y < 9.0:
                    return SILVER[3] if (y % 2.4) < 0.25 else SILVER[1]
                return pick(CHARCOAL, 2.2 + folds(y, 0.4, 0.4) - (0.8 if abs(x) > 0.95 else 0))
            lapel = 2.7 - max(0.0, min(1.0, (y - 3.0) / 3.5)) * 0.85
            if abs(x) < lapel:
                lit = 3.2 if abs(x) > lapel - 0.6 else 2.0
                return pick(WOOL, lit - (0.9 if abs(x) < 1.55 else 0))
            if 2.7 < x < 3.8 and 3.5 < y < 4.9:
                dx, dy = x - 3.25, y - 4.2
                if abs(dx) + abs(dy) < 0.65:
                    return GOLD[3] if dx + dy < -0.15 else (GOLD[2] if abs(dx) + abs(dy) < 0.4 else GOLD[1])
            return pick(CRIMSON, 3.3 + folds(x, 0.0, 0.9) - (y > 11.4) * 1.2)
        if belt:
            return pick(LEATHER, 2.5 - (y - 9.4) * 1.5)
        if f == 'back':
            seam = abs(x) < 0.3 and y > 6
            return pick(CRIMSON, 3.0 + folds(x, 1.7, 0.9) - seam * 1.4 - (y > 11.4) * 1.2)
        if f in ('left', 'right'):
            return pick(CRIMSON, 3.1 + folds(z, 2.4, 0.7) - (y > 11.4) * 1.2)
        return pick(CRIMSON, 3.0 if f == 'top' else 1.0)
    if p in ('/right_arm', '/left_arm'):
        if t.cube == 0:
            return pick(CRIMSON, 3.2 + folds(y * 0.9 + across(t) * 0.4, 0.8, 0.8))
        if f == 'bottom':
            if 0.18 < t.n[0] < 0.82 and 0.18 < t.n[2] < 0.82:
                return None
            return CRIMSON[1]
        if f == 'top':
            return CRIMSON[2]
        if t.n[1] < 0.13:
            return SILVER[2]
        if t.n[1] < 0.45:
            return pick(WOOL, 3.0 - t.n[1] * 2.0)
        return pick(CRIMSON, 3.4 - t.n[1] * 1.4 + folds(across(t), 3.0, 0.4))
    if p.startswith('/body/skirt'):
        if f == 'bottom':
            return CRIMSON[0]
        inner = (p.endswith('front_right') and t.n[0] > 0.78) or (p.endswith('front_left') and t.n[0] < 0.22)
        if inner and f in ('front', 'top'):
            return pick(WOOL, 3.0 if (t.n[0] > 0.9 or t.n[0] < 0.1) else 2.0)
        if inner or (p.endswith('front_right') and f == 'left') or (p.endswith('front_left') and f == 'right'):
            return WOOL[2]
        world_x = t.world[0] * 16.0
        lvl = 3.3 + folds(world_x if f in ('front', 'back') else z, 0.5 + len(p), 0.9)
        if p.endswith('back') and f == 'back' and abs(x) < 0.3 and y > 5:
            lvl -= 1.6
        if y > 9.3:
            lvl -= 1.3
        if y > 9.7:
            lvl -= 0.8
        return pick(CRIMSON, lvl)
    if p == '/body/mantle':
        if f == 'bottom':
            return pick(CRIMSON, 2.4 + folds(x, 0.2, 0.5))
        if f == 'top':
            return pick(WOOL, 3.0 + folds(x, 1.0, 0.6))
        if f == 'front' and abs(x) < 0.75 and 0.15 < t.n[1] < 0.6:
            ring = abs(x) > 0.4 or t.n[1] < 0.27 or t.n[1] > 0.48
            return SILVER[3] if ring and x < 0 else (SILVER[2] if ring else WOOL[0])
        if 0.72 < t.n[1] < 0.88:
            return SILVER[2] if f in ('front', 'left') else SILVER[1]
        if t.n[1] >= 0.88:
            return WOOL[0]
        return pick(WOOL, 2.8 + folds(across(t), 0.6, 0.8) - t.n[1] * 0.8)
    if p == '/body/collar':
        if f == 'front' and abs(x) < 3.2:
            return None
        if f == 'top':
            edge = min(5.5 - abs(x), 5.0 - abs(z))
            return CRIMSON[2] if edge < 0.9 else WOOL[0]
        if f == 'bottom':
            return WOOL[0]
        if t.n[1] < 0.22:
            return CRIMSON[3]
        return pick(WOOL, 2.6 - t.n[1] * 1.1)
    return None


def tatter(t, rows, key):
    """True for the texels a ragged hem has lost: each column frays by its own 0..rows texels."""
    return (1.0 - t.n[1]) * t.size[1] * 2.0 < math.floor(h(key) * (rows + 1))


def coat_death_eater(t):
    p, f = t.part, t.face
    x, y, z = t.local
    if p == '/body':
        if f == 'front':
            ax = abs(x)
            for cy in (1.8, 4.4, 7.0):
                if abs(y - cy) < 0.3 and ax < 1.3:
                    return SILVER[4] if y < cy and x < 0.4 else SILVER[2]
            sag = 0.9 + 0.8 * (1.0 - min(1.0, ax / 3.4)) ** 0.7
            if 1.3 < ax < 3.6 and abs(y - sag) < 0.28 and int((ax - 1.3) / 0.575) % 2 == 0:
                return SILVER[3]
            if 9.6 < y < 10.4:
                if ax < 0.7:
                    return SILVER[3] if y < 10.0 else SILVER[1]
                return NIGHT[0] if (int(x / 0.575) % 4) else SILVER[1]
            if ax < 0.3:
                return NIGHT[0]
            if 0.6 < ax < 0.9 and int(y / 0.6) % 5 == 2:
                return SILVER[1]
            if curl_at(ax, y):
                return SILVER[1]
            return pick(NIGHT, 3.1 + folds(x, 0.3, 1.0) - (y > 11.4) * 1.2)
        if 9.6 < y < 10.4 and f in ('left', 'right'):
            return NIGHT[0]
        if f == 'back':
            return pick(NIGHT, 2.8 + folds(x, 1.1, 0.8))
        if f in ('left', 'right'):
            return pick(NIGHT, 3.0 + folds(z, 2.2, 0.7) - (y > 11.4) * 1.2)
        return pick(NIGHT, 3.0 if f == 'top' else 1.0)
    if p in ('/right_arm', '/left_arm'):
        if t.cube == 0:
            return pick(NIGHT, 3.0 + folds(y * 0.9 + across(t) * 0.4, 1.4, 0.9))
        if f == 'bottom':
            return None
        if f == 'top':
            return NIGHT[1]
        if tatter(t, 2, (p[:5], f, t.u)):
            return None
        if 0.08 < t.n[1] < 0.18:
            return SILVER[1]
        return pick(NIGHT, 3.2 - t.n[1] * 1.6 + folds(across(t), 3.0, 0.5))
    if p.startswith('/body/skirt'):
        if f == 'bottom':
            return None
        if f != 'top' and tatter(t, 4, (p, f, t.u)):
            return None
        inner = (p.endswith('front_right') and 0.72 < t.n[0] < 0.86) or (p.endswith('front_left') and 0.14 < t.n[0] < 0.28)
        if inner and f == 'front' and int(y / 0.6) % 3 != 1:
            return SILVER[1] if int(y / 0.6) % 6 else SILVER[2]
        world_x = t.world[0] * 16.0
        lvl = 3.2 + folds(world_x if f in ('front', 'back') else z, 0.9 + len(p), 1.0)
        if y > 8.0:
            lvl -= (y - 8.0) * 0.6
        return pick(NIGHT, lvl)
    if p == '/body/cloak':
        if f == 'bottom':
            return None
        if f != 'top' and tatter(t, 6, ('cloak', round(x / 0.5))):
            return None
        if f == 'front':
            return pick(LINING, 2.0 + folds(x, 0.4, 0.8) - y * 0.04)
        if f == 'top':
            return NIGHT[2]
        sheen = 0.6 * max(0.0, math.sin(x * 1.3 + 0.4) - 0.5) * 2.0
        lvl = 3.2 + folds(x, 0.7, 1.3) + sheen - max(0.0, y - 14.0) * 0.18
        if y < 0.9:
            lvl = 2.0
        return pick(NIGHT, lvl)
    return None


def curl_at(ax, y):
    """A sparse silver-thread curl beside the closure, every few pixels down the robe."""
    k = int(y / 2.2)
    cy = k * 2.2 + 1.1
    cx = 1.7
    d = math.hypot(ax - cx, (y - cy) * 1.1)
    return 0.45 < d < 0.75 and (ax - cx) + (y - cy) > -0.2 and k % 2 == 1 and y < 9.2


# --------------------------------------------------------------------------------------------- hood

def hood(t, death_eater):
    p, f = t.part, t.face
    x, y, z = t.local
    cloth = NIGHT if death_eater else WOOL
    if p == '/head/mask' or p == '/head/nose':
        if not death_eater:
            return None
        grid = MASK if p == '/head/mask' else NOSE
        if f == 'front':
            cols, rows = len(grid[0]), len(grid)
            c = min(cols - 1, int(t.n[0] * cols))
            r = min(rows - 1, int(t.n[1] * rows))
            return MASK_RAMP[int(grid[r][c])]
        return MASK_RAMP[1] if f in ('left', 'right', 'top') else MASK_RAMP[0]
    if p == '/head':
        if t.cube != 0:
            return None
        if f == 'front':
            ax = abs(x)
            arch = 3.4 - max(0.0, -5.6 - y) * 0.95
            if ax < arch and y > -7.4 and not (death_eater and y > -0.55):
                return None
            if ax < arch + 0.6 and y > -8.0:
                if death_eater:
                    return SILVER[1] if ax < 3.65 else NIGHT[0]
                return CRIMSON[3] if ax < 3.7 else CRIMSON[1]
            return pick(cloth, 2.4 + folds(x, 0.2, 0.5))
        if f in ('left', 'right'):
            front = t.n[2]
            lvl = 2.6 + folds(z * 0.8 + y * 0.35, 1.1, 0.9) - (front < 0.1) * 1.2 - max(0.0, y + 1.0) * 0.4
            return pick(cloth, lvl)
        if f == 'back':
            seam = abs(x) < 0.3
            return pick(cloth, 2.4 + folds(x, 2.0, 0.7) - seam * 1.3)
        if f == 'top':
            return pick(cloth, 3.0 + folds(x, 0.7, 0.6) - (abs(x) < 0.3) * 1.0)
        return cloth[0]
    if p == '/head/brow':
        if f == 'bottom':
            return CRIMSON[2] if not death_eater else NIGHT[0]
        if f == 'front':
            if t.n[1] > 0.72:
                return SILVER[2] if death_eater else CRIMSON[3]
            return pick(cloth, 3.3 - t.n[1] * 1.6)
        return pick(cloth, 2.8 + folds(x, 0.5, 0.5))
    if p == '/head/nape':
        return pick(cloth, 2.2 + 0.9 * math.sin(y * 2.6) - t.n[1] * 0.6)
    if p == '/head/peak':
        return pick(cloth, 2.6 + folds(x, 1.6, 0.7))
    return None


# -------------------------------------------------------------------------------- trousers / boots

LEGS = ('/right_leg', '/left_leg')


def trousers(t, death_eater):
    p, f = t.part, t.face
    x, y, z = t.local
    ramp = NIGHT if death_eater else CHARCOAL
    base = 2.4 if death_eater else 1.8
    if p == '/body':
        if t.n[1] < 0.72 or f in ('top', 'bottom'):
            return None
        return pick(ramp, base - (t.n[1] > 0.9) * 0.8)
    if p not in LEGS:
        return None
    if f in ('top', 'bottom'):
        return ramp[0]
    crease = f == 'front' and abs(t.n[0] - 0.5) < 0.07
    return pick(ramp, base + folds(y * 0.7, 0.3 + len(p), 0.5) + crease * 1.0)


def boots(t, death_eater):
    p, f = t.part, t.face
    x, y, z = t.local
    if p not in LEGS:
        return None
    top = 9.3
    if f == 'top':
        return None
    if f == 'bottom':
        return LEATHER[0] if not death_eater else NIGHT[0]
    if y < top:
        return None
    leather = NIGHT if death_eater else LEATHER
    if y < top + 1.25:
        # the turned-down top of the boot
        return pick(leather, (4.4 if death_eater else 3.6) - (y - top) * 0.8)
    if y > 12.1:
        return leather[0]
    if death_eater:
        if f == 'front' and y > 11.6:
            return SILVER[3] if y < 12.0 else SILVER[2]
        for sy in (11.05,):
            if abs(y - sy) < 0.28:
                return SILVER[2] if (int(across(t) / 0.6) % 3 == 0) else NIGHT[0]
        gloss = f == 'front' and abs(t.n[0] - 0.35) < 0.08
        return pick(NIGHT, 3.0 + gloss * 2.0 - (y - top) * 0.1)
    for sy in (11.2,):
        if abs(y - sy) < 0.33:
            if f in ('front', 'right', 'left') and abs(t.n[0 if f == 'front' else 2] - 0.7) < 0.1:
                return SILVER[3]
            return LEATHER[0]
    gloss = f == 'front' and abs(t.n[0] - 0.35) < 0.08
    return pick(LEATHER, 2.6 + gloss * 1.4 + folds(y, 0.2, 0.3))


# --------------------------------------------------------------------------------------------- preview

def render_scene(layers, az, el, size=300):
    """layers: list of (quads, texture array). z-buffered over all of them."""
    allp = np.concatenate([W for qs, _ in layers for _, W, _ in qs])
    lo, hi = allp.min(0), allp.max(0)
    center = (lo + hi) / 2
    radius = max(1e-3, np.linalg.norm(hi - lo) / 2)
    a, e = math.radians(az), math.radians(el)
    fwd = -np.array([math.cos(e) * math.sin(a), math.sin(e), math.cos(e) * math.cos(a)])
    right = np.cross(fwd, [0, 1, 0]); right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    scale = size * 0.48 / radius
    img = np.zeros((size, size, 3)); img[:] = (0.55, 0.62, 0.70)
    zb = np.full((size, size), np.inf)
    light = np.array([0.3, 0.9, 0.5]); light /= np.linalg.norm(light)
    for qs, T in layers:
        th, tw = T.shape[:2]
        for _, W, UV in qs:
            W = W - center
            n = np.cross(W[1] - W[0], W[2] - W[0])
            if np.linalg.norm(n) < 1e-9:
                continue
            n /= np.linalg.norm(n)
            if n @ fwd > 0:
                n = -n
            bright = 0.55 + 0.45 * max(0.0, n @ light)
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
                zz = w0 * p[0, 2] + w1 * p[1, 2] + w2 * p[2, 2]
                uu = (w0 * q[0, 0] + w1 * q[1, 0] + w2 * q[2, 0]) * tw
                vv = (w0 * q[0, 1] + w1 * q[1, 1] + w2 * q[2, 1]) * th
                ui = np.clip(uu.astype(int), 0, tw - 1); vi = np.clip(vv.astype(int), 0, th - 1)
                texel = T[vi, ui]
                m &= texel[..., 3] > 0.1
                yy, xx = np.nonzero(m)
                Y, X = yy + ymin, xx + xmin
                c = zz[yy, xx] < zb[Y, X]
                yy, xx, Y, X = yy[c], xx[c], Y[c], X[c]
                zb[Y, X] = zz[yy, xx]
                img[Y, X] = texel[yy, xx, :3] * bright
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype('uint8'))


def quads(data, model, keep):
    out = []
    for q in data[model]['quads']:
        if keep(q['p']):
            v = np.array(q['v'], dtype=float).reshape(4, 5)
            out.append((q['p'], v[:, :3], v[:, 3:]))
    return out


def arr(img):
    return np.asarray(img.convert('RGBA'), dtype=float) / 255


def preview(data, textures, out):
    steve = Image.open(os.path.join(HERE, 'out', 'assets', 'minecraft', 'textures', 'entity', 'player', 'wide', 'steve.png'))
    player = quads(data, 'player', lambda p: p not in ('/cloak', '/ear'))
    rows = []
    for set_name, de in (('auror', False), ('death_eater', True)):
        tx = textures[set_name]
        robe_keep = lambda p: (p == '/body' or p in ('/right_arm', '/left_arm') or p.startswith('/body/')) and (
            not p.endswith('cloak') if not de else not (p.endswith('mantle') or p.endswith('collar')))
        hood_keep = lambda p: p.startswith('/head') and (de or not (p.endswith('mask') or p.endswith('nose')))
        layers = [
            (player, arr(steve)),
            (quads(data, 'inner', lambda p: p in ('/body',) + LEGS), arr(tx['trousers'])),
            (quads(data, 'outer', lambda p: p in LEGS), arr(tx['boots'])),
            (quads(data, 'robe', robe_keep), arr(tx['coat'])),
            (quads(data, 'hood', hood_keep), arr(tx['hood'])),
        ]
        views = [(0, 8), (35, 18), (90, 5), (180, 12), (215, 20)]
        tiles = [render_scene(layers, az, el) for az, el in views]
        head = render_scene([(player, arr(steve)), (quads(data, 'hood', hood_keep), arr(tx['hood']))], 20, 8)
        tiles.append(head)
        row = Image.new('RGB', (300 * len(tiles), 300))
        for i, t in enumerate(tiles):
            row.paste(t, (i * 300, 0))
        rows.append(row)
    sheet = Image.new('RGB', (rows[0].width, 300 * len(rows)))
    for i, r in enumerate(rows):
        sheet.paste(r, (0, i * 300))
    sheet.save(out)
    print('preview', out)


def main():
    data = load()
    textures = {}
    for set_name, de in (('auror', False), ('death_eater', True)):
        tx = {
            'hood': texture_for('hood', lambda t: hood(t, de)),
            'coat': texture_for('robe', coat_death_eater if de else coat_auror),
            'trousers': texture_for('inner', lambda t: trousers(t, de)),
            'boots': texture_for('outer', lambda t: boots(t, de)),
        }
        textures[set_name] = tx
        for part, img in tx.items():
            path = os.path.join(OUT_DIR, '%s_robes_%s.png' % (set_name, part))
            img.save(path)
            print('wrote', os.path.relpath(path, ROOT), img.size)
    if '--preview' in sys.argv:
        preview(data, textures, sys.argv[sys.argv.index('--preview') + 1])


if __name__ == '__main__':
    main()
