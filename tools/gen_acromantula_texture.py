"""
Generate acromantula.png and acromantula_eyes.png (128x128) for AcromantulaModel.

Every texel is painted from the 3D point it lands on: the cube table below mirrors
AcromantulaModel.createBodyLayer, and face_texels() reproduces the vertex/UV
assignment of vanilla ModelPart.Cube, so each face gets the pattern for where it
sits on the spider (eyes on the real face, joint bands at the real knees, the
abdomen pattern running front to back) instead of a hand-placed rectangle.

The look: a black, coarse-haired giant spider (Aragog's kind) - charcoal hair with
grey bristle tips, a dark lancet and brown-grey chevrons down the abdomen, a
radially grooved carapace, pale rings at the leg joints, glossy black fangs, and
eight eyes (two big front medians, four laterals, two on the crown) that glow red
through the eyes layer.

Run:   python tools/gen_acromantula_texture.py
       python tools/gen_acromantula_texture.py --preview out.png   (also renders a check sheet)
Re-run after any change to the model's cubes or texOffs.
"""

import math
import os
import random
import sys

from PIL import Image

TEX = 128
HERE = os.path.dirname(os.path.abspath(__file__))
TEXDIR = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'hogwartsmagic',
                      'textures', 'entity')

# ----------------------------------------------------------------- model table
# name, parent, texOffs, box origin, box size, mirror, pose (offset, rotation)
LEG_SWEEP = [math.pi / 4, math.pi / 9, -math.pi / 9, -math.pi / 4]


def build_parts():
    parts = {
        'thorax':  dict(parent=None, tex=(0, 0), origin=(-5, -4, -6), size=(10, 8, 12),
                        offset=(0, 18, 0), rot=(0, 0, 0)),
        # setupAnim resets the abdomen to xRot -0.1 every frame
        'abdomen': dict(parent=None, tex=(0, 20), origin=(-7, -6, 0), size=(14, 12, 18),
                        offset=(0, 18, 5), rot=(-0.1, 0, 0)),
        'head':    dict(parent=None, tex=(64, 20), origin=(-4, -4, -6), size=(8, 8, 6),
                        offset=(0, 18, -6), rot=(0, 0, 0)),
        'left_fang':  dict(parent='head', tex=(92, 20), origin=(-1, 0, -1.5), size=(2, 6, 3),
                           offset=(2, 4, -5), rot=(0.1, 0.1, 0.1)),
        'right_fang': dict(parent='head', tex=(92, 20), origin=(-1, 0, -1.5), size=(2, 6, 3),
                           offset=(-2, 4, -5), rot=(0.1, -0.1, -0.1), mirror=True),
    }
    for i in range(8):
        left = i < 4
        pair = i % 4
        base = math.pi / 2 if left else -math.pi / 2
        yrot = base + (LEG_SWEEP[pair] if left else -LEG_SWEEP[pair])
        lower_x = -0.4 if pair in (0, 3) else -0.5
        parts['leg%d_upper' % i] = dict(parent=None, tex=(0, 52), origin=(-1.5, -1.5, 0),
                                        size=(3, 3, 16), offset=(5 if left else -5, 18, -3 + pair * 3),
                                        rot=(0, yrot, 0))
        parts['leg%d_lower' % i] = dict(parent='leg%d_upper' % i, tex=(40, 52), origin=(-1, -1, 0),
                                        size=(2, 2, 20), offset=(0, 0, 16), rot=(lower_x, 0, 0))
    return parts


PARTS = build_parts()


def face_texels(part):
    """Yield (face, u, v, local_point) for every texel of the cube, using vanilla's
    ModelPart.Cube vertex order and Polygon UV assignment (v[0]->(u2,v1), v[1]->(u1,v1),
    v[2]->(u1,v2), v[3]->(u2,v2))."""
    tu, tv = part['tex']
    (x0, y0, z0), (dx, dy, dz) = part['origin'], part['size']
    x1, y1, z1 = x0 + dx, y0 + dy, z0 + dz
    if part.get('mirror'):
        x0, x1 = x1, x0
    V = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
         (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    u0, u1, u2 = tu, tu + dz, tu + dz + dx
    u3, u4, u5 = tu + dz + dx + dx, tu + dz + dx + dz, tu + dz + dx + dz + dx
    va, vb, vc = tv, tv + dz, tv + dz + dy
    faces = {
        'top':    ((5, 4, 0, 1), u1, va, u2, vb),   # model -Y, drawn as the top
        'bottom': ((2, 3, 7, 6), u2, vb, u3, va),
        'right':  ((0, 4, 7, 3), u0, vb, u1, vc),   # model -X, the spider's right
        'front':  ((1, 0, 3, 2), u1, vb, u2, vc),
        'left':   ((5, 1, 2, 6), u2, vb, u4, vc),
        'back':   ((4, 5, 6, 7), u4, vb, u5, vc),
    }
    for name, (idx, ua, v_a, ub, v_b) in faces.items():
        P = [V[k] for k in idx]
        for v in range(int(min(v_a, v_b)), int(max(v_a, v_b))):
            for u in range(int(min(ua, ub)), int(max(ua, ub))):
                s = (u + 0.5 - ua) / (ub - ua)
                t = (v + 0.5 - v_a) / (v_b - v_a)
                # P[1] sits at (ua, v_a), P[0] at (ub, v_a), P[2] at (ua, v_b)
                pt = tuple(P[1][k] + s * (P[0][k] - P[1][k]) + t * (P[2][k] - P[1][k])
                           for k in range(3))
                yield name, u, v, pt


def face_quads(part):
    """The six quads as (face, [4 local vertices], [4 uvs]) for the preview renderer."""
    tu, tv = part['tex']
    (x0, y0, z0), (dx, dy, dz) = part['origin'], part['size']
    x1, y1, z1 = x0 + dx, y0 + dy, z0 + dz
    if part.get('mirror'):
        x0, x1 = x1, x0
    V = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
         (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    u0, u1, u2 = tu, tu + dz, tu + dz + dx
    u3, u4, u5 = tu + dz + dx + dx, tu + dz + dx + dz, tu + dz + dx + dz + dx
    va, vb, vc = tv, tv + dz, tv + dz + dy
    spec = [((5, 4, 0, 1), u1, va, u2, vb), ((2, 3, 7, 6), u2, vb, u3, va),
            ((0, 4, 7, 3), u0, vb, u1, vc), ((1, 0, 3, 2), u1, vb, u2, vc),
            ((5, 1, 2, 6), u2, vb, u4, vc), ((4, 5, 6, 7), u4, vb, u5, vc)]
    for idx, ua, v_a, ub, v_b in spec:
        yield [V[k] for k in idx], [(ub, v_a), (ua, v_a), (ua, v_b), (ub, v_b)]


# ----------------------------------------------------------------- palette
HAIR = [(9, 8, 8), (17, 15, 14), (26, 23, 21), (37, 33, 30), (52, 47, 42), (72, 66, 59),
        (98, 91, 82), (128, 120, 108)]
BRN = [(30, 23, 18), (44, 34, 26), (62, 49, 37), (84, 68, 52), (108, 90, 70)]
GLOSS = (14, 13, 16)
SHEEN = (66, 64, 74)
EYE = (22, 6, 7)
GLINT = (170, 168, 176)
TRANSPARENT = (0, 0, 0, 0)

rng = random.Random(20261106)


def hair(level=0.0, bristle=0.10, tint=None):
    """Coarse black hair: mostly charcoal, a few near-black gaps, sparse grey bristle
    tips. level shifts the whole ramp lighter (+) or darker (-)."""
    r = rng.random()
    if r < 0.15:
        k = 1
    elif r < 0.62:
        k = 2
    elif r < 0.92:
        k = 3
    else:
        k = 4
    if rng.random() < bristle * 0.7:
        k = 4 if rng.random() < 0.75 else 5
    k = max(0, min(len(HAIR) - 1, int(round(k + level))))
    c = HAIR[k]
    if tint is not None:
        c = tuple(int(a + (b - a) * 0.45) for a, b in zip(c, BRN[min(len(BRN) - 1, max(0, k - 1))]))
    return c


def norm(part, p):
    (x0, y0, z0), (dx, dy, dz) = part['origin'], part['size']
    return ((p[0] - x0) / dx, (p[1] - y0) / dy, (p[2] - z0) / dz)


# ----------------------------------------------------------------- shaders
# nx: 0 = spider's right, 1 = left.  ny: 0 = top, 1 = underside.  nz: 0 = front, 1 = back.

def shade_abdomen(face, nx, ny, nz):
    cx = abs(nx - 0.5)
    if face == 'bottom':
        # dark, browner belly; pale book-lung patches at the front, spinnerets at the back
        if nz < 0.22 and 0.16 < cx < 0.34:
            return BRN[3] if rng.random() < 0.6 else BRN[2]
        if nz > 0.88 and cx < 0.12:
            return BRN[4] if rng.random() < 0.5 else BRN[3]
        return hair(-0.6, bristle=0.04, tint=True)
    if face == 'back':
        if ny > 0.62 and cx < 0.12:
            return BRN[4] if (ny > 0.75 and rng.random() < 0.6) else BRN[2]
        return hair(-0.2 + 0.0 * ny, bristle=0.10)
    if face == 'front':
        return hair(-0.5, bristle=0.05)
    level = 0.0
    if face in ('left', 'right'):
        level = -0.9 * ny          # the flanks darken toward the belly
    if face in ('left', 'right') and ny < 0.08:
        level += 0.6               # pale fringe where the back meets the flank
    if face == 'top':
        # dark lancet (heart mark) down the front half, a pale rim around it
        half = 0.13 * max(0.0, 1.0 - nz / 0.48)
        if nz < 0.48 and cx < half:
            return HAIR[0] if rng.random() < 0.7 else HAIR[1]
        if nz < 0.50 and cx < half + 0.08:
            return BRN[3] if rng.random() < 0.55 else BRN[2]
        # brown-grey chevrons pointing forward down the back half
        # in whole texels: 14 across, 18 front to back; each chevron is a pale row
        # with a black row behind it, its arms swept back one row per two columns
        tz = int(nz * 18)
        side = int(abs(nx * 14 - 7))            # 0 at the midline .. 6 at the edge
        if tz >= 9 and side <= 5:
            r = (tz - 9 - side // 2) % 3
            if tz - 9 - side // 2 >= 0:
                if r == 0:
                    return BRN[4] if (side < 2 or rng.random() < 0.5) else BRN[3]
                if r == 1:
                    return HAIR[0] if rng.random() < 0.7 else HAIR[1]
        # paler hair along the two edges of the back
        if cx > 0.44:
            return hair(0.7, bristle=0.2, tint=True)
    return hair(level, bristle=0.12)


def shade_thorax(face, nx, ny, nz):
    if face == 'top':
        # carapace: dark fovea pit, radial grooves, a hairy rim
        dx, dz = (nx - 0.5) * 10, (nz - 0.55) * 12
        r = math.hypot(dx, dz)
        if r < 0.8:
            return HAIR[0]
        ang = math.atan2(dz, dx)
        groove = abs(math.sin(ang * 4))
        if r < 4.2 and groove < 0.22:
            return HAIR[1]
        if nx < 0.1 or nx > 0.9 or nz > 0.92:
            return hair(0.8, bristle=0.3)
        return HAIR[2] if rng.random() < 0.7 else (HAIR[3] if r > 3 else HAIR[1])
    if face == 'bottom':
        return BRN[0] if rng.random() < 0.7 else BRN[1]   # sternum
    return hair(-0.3 if face in ('front', 'back') else 0.0, bristle=0.12)


# 8x8 face, columns run spider's right -> left, rows top -> bottom.
# E big median eye, e small eye, g glint, b bristle, c chelicera hair, m mouth
FACE = [
    "hbhhhhbh",
    "ehhbbhhe",
    "hgEhhgEh",
    "hEEbbEEh",
    "hbhhhhbh",
    "hcchhcch",
    "hccmmcch",
    "hccmmcch",
]
EYE_PIXELS = {}     # (u, v) -> glow colour, filled while painting


def shade_head(face, nx, ny, nz, u, v):
    if face == 'front':
        col = min(7, int(nx * 8))
        row = min(7, int(ny * 8))
        ch = FACE[row][col]
        if ch == 'E':
            EYE_PIXELS[(u, v)] = (190, 24, 20)
            return EYE
        if ch == 'g':
            return GLINT                # no glow: the highlight stays a highlight
        if ch == 'e':
            EYE_PIXELS[(u, v)] = (150, 18, 16)
            return EYE
        if ch == 'b':
            return hair(2.0, bristle=0.4, tint=True)
        if ch == 'c':
            return hair(0.2, bristle=0.2)
        if ch == 'm':
            return HAIR[0]
        return hair(-0.2, bristle=0.06)
    if face == 'top':
        col = min(7, int(nx * 8))
        row = min(5, int(nz * 6))       # 0 = front edge
        if row == 1 and col in (2, 5):
            EYE_PIXELS[(u, v)] = (150, 18, 16)
            return EYE
        if row == 0:
            return hair(0.6, bristle=0.3)
        return hair(0.0, bristle=0.12)
    if face == 'bottom':
        return BRN[1] if rng.random() < 0.6 else BRN[0]
    if face in ('left', 'right'):
        # second lateral eye on each cheek, one texel back from the face
        col = min(5, int(nz * 6))
        row = min(7, int(ny * 8))
        if col == 1 and row == 2:
            EYE_PIXELS[(u, v)] = (150, 18, 16)
            return EYE
    return hair(-0.1, bristle=0.12)


def shade_fang(face, nx, ny, nz):
    if ny < 0.4:
        return hair(0.0, bristle=0.2)          # hairy chelicera base
    if ny > 0.84:
        return (74, 44, 34)                     # dull red-brown tip
    if face == 'front' and nx < 0.5:
        return SHEEN if rng.random() < 0.6 else HAIR[3]
    return GLOSS if rng.random() < 0.8 else HAIR[1]


def shade_leg_upper(face, nx, ny, nz):
    z = nz * 16
    if z > 13.5:
        return hair(2.4, bristle=0.3)           # pale ring at the knee
    if z < 1.2:
        return hair(-0.8, bristle=0.0)          # coxa, in the body's shadow
    if face == 'top' and abs(nx - 0.5) < 0.2 and 3 < z < 12:
        return hair(1.0, bristle=0.2)           # faint stripe down the femur
    if face == 'bottom':
        return hair(-0.6, bristle=0.06)
    return hair(0.0, bristle=0.18)


def shade_leg_lower(face, nx, ny, nz):
    z = nz * 20
    if z < 1.5 or 9.5 < z < 11.0:
        return hair(2.2, bristle=0.3)           # patella and metatarsus rings
    if face == 'back' or z > 18.5:
        return GLOSS if rng.random() < 0.6 else HAIR[0]   # tarsus claw
    if z > 15:
        return hair(-0.4, bristle=0.1)
    if face == 'bottom':
        return hair(-0.5, bristle=0.06)
    return hair(0.1, bristle=0.2)


def shade(name, part, face, p, u, v):
    nx, ny, nz = norm(part, p)
    if name == 'abdomen':
        return shade_abdomen(face, nx, ny, nz)
    if name == 'thorax':
        return shade_thorax(face, nx, ny, nz)
    if name == 'head':
        return shade_head(face, nx, ny, nz, u, v)
    if name.endswith('fang'):
        if part.get('mirror'):
            nx = 1 - nx
        return shade_fang(face, nx, ny, nz)
    if name.endswith('upper'):
        return shade_leg_upper(face, nx, ny, nz)
    return shade_leg_lower(face, nx, ny, nz)


def generate():
    img = Image.new('RGBA', (TEX, TEX), TRANSPARENT)
    px = img.load()
    painted = set()
    for name, part in PARTS.items():
        for face, u, v, p in face_texels(part):
            if (u, v) in painted:
                continue                # shared regions (8 legs, 2 fangs) paint once
            painted.add((u, v))
            c = shade(name, part, face, p, u, v)
            px[u, v] = c + (255,)
    eyes = Image.new('RGBA', (TEX, TEX), TRANSPARENT)
    epx = eyes.load()
    for (u, v), c in EYE_PIXELS.items():
        epx[u, v] = c + (255,)
    return img, eyes


# ----------------------------------------------------------------- preview
def rot_matrix(rx, ry, rz):
    import numpy as np
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def to_model(name, pts):
    """Local cube points -> model space, walking the parent chain (ModelPart.translateAndRotate)."""
    import numpy as np
    P = np.array(pts, dtype=float)
    while name is not None:
        part = PARTS[name]
        P = P @ rot_matrix(*part['rot']).T + np.array(part['offset'], dtype=float)
        name = part['parent']
    return P


def render_view(tex, eyes, az, el, size=420, glow=True, zoom=1.0, center=(0, 0, 0)):
    import numpy as np
    T = np.asarray(tex.convert('RGBA'), dtype=float) / 255
    G = np.asarray(eyes.convert('RGBA'), dtype=float) / 255
    a, e = math.radians(az), math.radians(el)
    # world = (x, -y, -z): renderer flips y, entity yaw 0 faces +Z
    fwd = -np.array([math.cos(e) * math.sin(a), math.sin(e), math.cos(e) * math.cos(a)])
    right = np.cross(fwd, [0, 1, 0]); right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    img = np.zeros((size, size, 3)); img[:] = (0.55, 0.62, 0.55)
    zb = np.full((size, size), np.inf)
    scale = size / 92.0 * zoom
    light = np.array([0.3, 0.9, 0.5]); light /= np.linalg.norm(light)
    for name, part in PARTS.items():
        for verts, uvs in face_quads(part):
            W = to_model(name, verts) * np.array([1, -1, -1]) - np.array([0, -14, 4]) - np.array(center, dtype=float)
            n = np.cross(W[1] - W[0], W[2] - W[0])
            if np.linalg.norm(n) == 0:
                continue
            n /= np.linalg.norm(n)
            if n @ fwd > 0:
                n = -n
            bright = 0.45 + 0.55 * max(0.0, n @ light)
            S = np.stack([size / 2 + (W @ right) * scale, size / 2 - (W @ up) * scale, W @ fwd], axis=1)
            UV = np.array(uvs, dtype=float)
            for tri in ((0, 1, 2), (0, 2, 3)):
                p = S[list(tri)]; q = UV[list(tri)]
                xmin, xmax = int(max(0, p[:, 0].min())), int(min(size - 1, p[:, 0].max() + 1))
                ymin, ymax = int(max(0, p[:, 1].min())), int(min(size - 1, p[:, 1].max() + 1))
                if xmin > xmax or ymin > ymax:
                    continue
                xs, ys = np.meshgrid(np.arange(xmin, xmax + 1) + 0.5, np.arange(ymin, ymax + 1) + 0.5)
                d = (p[1, 1] - p[2, 1]) * (p[0, 0] - p[2, 0]) + (p[2, 0] - p[1, 0]) * (p[0, 1] - p[2, 1])
                if abs(d) < 1e-9:
                    continue
                w0 = ((p[1, 1] - p[2, 1]) * (xs - p[2, 0]) + (p[2, 0] - p[1, 0]) * (ys - p[2, 1])) / d
                w1 = ((p[2, 1] - p[0, 1]) * (xs - p[2, 0]) + (p[0, 0] - p[2, 0]) * (ys - p[2, 1])) / d
                w2 = 1 - w0 - w1
                m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
                z = w0 * p[0, 2] + w1 * p[1, 2] + w2 * p[2, 2]
                uu = w0 * q[0, 0] + w1 * q[1, 0] + w2 * q[2, 0]
                vv = w0 * q[0, 1] + w1 * q[1, 1] + w2 * q[2, 1]
                ui = np.clip(uu.astype(int), 0, TEX - 1); vi = np.clip(vv.astype(int), 0, TEX - 1)
                texel = T[vi, ui]
                m &= texel[..., 3] > 0.5
                yy, xx = np.nonzero(m)
                yy_i, xx_i = yy + ymin, xx + xmin
                closer = z[yy, xx] < zb[yy_i, xx_i]
                yy, xx, yy_i, xx_i = yy[closer], xx[closer], yy_i[closer], xx_i[closer]
                zb[yy_i, xx_i] = z[yy, xx]
                col = texel[yy, xx, :3] * bright
                if glow:
                    col = np.clip(col + G[vi[yy, xx], ui[yy, xx], :3], 0, 1)
                img[yy_i, xx_i] = col
    return Image.fromarray((img * 255).astype('uint8'))


def preview(tex, eyes, out):
    views = [(0, 10, 1, (0, 0, 0)), (35, 30, 1, (0, 0, 0)), (160, 35, 1, (0, 0, 0)),
             (0, 8, 3.2, (0, 0, 10)), (180, 70, 2.4, (0, 2, -10)), (60, 20, 3.0, (2, 0, 6))]
    tiles = [render_view(tex, eyes, az, el, zoom=zm, center=c) for az, el, zm, c in views]
    sheet = Image.new('RGB', (420 * 3, 420 * 2 + 256))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % 3) * 420, (i // 3) * 420))
    bg = Image.new('RGBA', tex.size, (255, 0, 255, 255)); bg.alpha_composite(tex)
    sheet.paste(bg.convert('RGB').resize((256, 256), Image.NEAREST), (0, 840))
    eb = Image.new('RGBA', tex.size, (40, 40, 40, 255)); eb.alpha_composite(eyes)
    sheet.paste(eb.convert('RGB').resize((256, 256), Image.NEAREST), (260, 840))
    sheet.save(out)
    print('preview', os.path.abspath(out))


if __name__ == '__main__':
    tex, eyes = generate()
    tex.save(os.path.join(TEXDIR, 'acromantula.png'))
    eyes.save(os.path.join(TEXDIR, 'acromantula_eyes.png'))
    print('wrote acromantula.png and acromantula_eyes.png in', os.path.abspath(TEXDIR))
    if '--preview' in sys.argv:
        preview(tex, eyes, sys.argv[sys.argv.index('--preview') + 1])
