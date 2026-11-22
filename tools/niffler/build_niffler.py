"""Builds the Niffler's geometry and texture from one description, so they can never disagree.

    python3 tools/niffler/build_niffler.py

Writes
  src/main/java/com/hogwartsmagic/client/model/NifflerGeometry.java   (the bone tree, generated)
  src/main/resources/assets/hogwartsmagic/textures/entity/niffler.png (128x128)

The animal
----------
A Niffler is a small burrowing beast, about the size of a large rabbit: a round, low body in black
velvet fur, a long flat snout like a duck's bill, bead eyes, short legs with broad digging paws and
long pale claws, and a short flat tail. Under the belly is the pouch, which holds far more than it
looks as if it could. The model is built round those parts, not round a box per limb:

* The body is a barrel, rounded by smaller boxes at the chest, the rump, the back and the flanks.
* The pouch is its own bone, so the animation can swell it as the Niffler fills it.
* The snout is three boxes that narrow and then widen again at the tip, the bill shape that makes
  a Niffler a Niffler. It has its own bone so it can twitch while the Niffler sniffs.
* The fore paws are broad spades with three claws each; the hind feet are flat and long.

Texel density: 32 units a block before the renderer's scale, one texel a unit, so the fur can carry
a sheen and the bill a nostril.

The coat is painted from the body, as on the Sphinx: each texel is placed on the rest model and its
colour comes from where it is and which way it faces. Black fur reads as a hole in the world in
Minecraft's lighting, so the coat is near-black with a cool blue-grey sheen on the faces that look
up, and a little warmer and browner on the belly and the pouch.

Conventions: Minecraft model space, y down, the Niffler faces -z. Ground is y=0 in "root" space
(the root bone sits at model y=24). Rotations are applied as in ModelPart: translate to the pivot,
then rotate Z * Y * X. A positive x rotation tips the front of a bone down.
"""
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..')
JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'hogwartsmagic', 'client', 'model', 'NifflerGeometry.java')
PNG = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'hogwartsmagic', 'textures', 'entity', 'niffler.png')
TW, TH = 128, 128
GROUND_Y = 24.0

BONES = {}
ORDER = []


def bone(name, parent, pivot, rot=(0.0, 0.0, 0.0), cubes=()):
    BONES[name] = (parent, pivot, rot, list(cubes))
    ORDER.append(name)


def rounded(mat, centre, size, b=1):
    """A box with its twelve edges cut back by b: three crossed boxes. Enough to read as round."""
    cx, cy, cz = centre
    w, h, d = size
    out = []
    for sw, sh, sd in ((w, h - 2 * b, d - 2 * b), (w - 2 * b, h, d - 2 * b), (w - 2 * b, h - 2 * b, d)):
        out.append((mat, (cx - sw / 2.0, cy - sh / 2.0, cz - sd / 2.0), (sw, sh, sd)))
    return out


bone('root', None, (0.0, 24.0, 0.0))
# The body: a pear, round and low, deepest and widest at the haunches. Its pivot is 6.5 units off the
# ground; the belly clears the ground by one unit, so the legs hardly show, as on the real animal.
bone('body', 'root', (0.0, -6.5, 0.0), cubes=(
    rounded('fur', (0.0, -0.5, 2.5), (12, 11, 10), 2)          # the haunches and the round rump
    + rounded('fur', (0.0, 0.0, -3.0), (10, 9, 7), 2)          # the chest, a little narrower
    + [('fur', (-3.0, -6.0, -1.0), (6, 1, 6))]                 # the crown of the back
))
# The pouch hangs from the belly. Its pivot is at its top, so scaling it swells it downward.
bone('pouch', 'body', (0.0, 4.5, 1.5), cubes=[
    ('belly', (-3.5, 0.0, -3.5), (7, 1, 7)),
])
# The head: small and round, set low on the chest, hardly a neck at all.
bone('head', 'body', (0.0, 0.5, -5.5), (0.05, 0.0, 0.0), cubes=rounded('fur', (0.0, -0.5, -2.5), (8, 7, 6), 1))
for s, side in ((-1, 'left'), (1, 'right')):
    # Bead eyes, high on the sides of the head just behind the bill.
    bone(f'eye_{side}', 'head', (s * 4.0, -1.5, -3.5), cubes=[
        ('eye', (-0.5, -0.5, -0.5), (1, 1, 1)),
    ])
# The bill: broad and flat, waisted, and broad again at the tip, a little turned down.
bone('snout', 'head', (0.0, 0.5, -5.5), (0.18, 0.0, 0.0), [
    ('bill', (-2.5, -1.5, -2.0), (5, 3, 2)),
    ('bill', (-2.0, -1.0, -5.0), (4, 2, 3)),
    ('bill', (-2.5, -1.0, -7.0), (5, 2, 2)),
])

for s, side in ((-1, 'left'), (1, 'right')):
    # Fore legs: short and strong, set wide, the paw a broad spade with long pale claws.
    bone(f'arm_{side}', 'body', (s * 4.0, 3.5, -4.0), cubes=[
        ('fur', (-1.5, -1.0, -1.5), (3, 3, 3)),
    ])
    bone(f'paw_{side}', f'arm_{side}', (0.0, 2.0, 0.0), (0.0, -s * 0.25, 0.0), [
        ('pad', (-2.5, 0.0, -2.5), (5, 1, 4)),
        ('claw', (-2.5, 0.0, -4.5), (1, 1, 2)),
        ('claw', (-0.5, 0.0, -5.0), (1, 1, 3)),
        ('claw', (1.5, 0.0, -4.5), (1, 1, 2)),
    ])
    # Hind legs: a round haunch in the body's side and a long flat foot.
    bone(f'leg_{side}', 'body', (s * 4.5, 3.0, 4.5), cubes=[
        ('fur', (-2.0, -2.0, -2.5), (4, 4, 5)),
    ])
    bone(f'foot_{side}', f'leg_{side}', (0.0, 2.0, 0.0), cubes=[
        ('fur', (-1.5, 0.0, -1.0), (3, 1, 2)),
        ('pad', (-1.5, 0.5, -3.0), (3, 1, 4)),
    ])

# The tail: short, broad and flat, held just off the ground.
bone('tail', 'body', (0.0, 2.0, 7.0), (-0.1, 0.0, 0.0), [
    ('fur', (-2.0, -0.5, 0.0), (4, 1, 3)),
])


# --- forward kinematics (rest pose) -------------------------------------------------------------

def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def rot_x(t):
    c, s = math.cos(t), math.sin(t)
    return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]


def rot_y(t):
    c, s = math.cos(t), math.sin(t)
    return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]


def rot_z(t):
    c, s = math.cos(t), math.sin(t)
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def trans(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


WORLD = {}


def world(name):
    if name in WORLD:
        return WORLD[name]
    parent, pivot, (rx, ry, rz), _ = BONES[name]
    local = mat_mul(trans(*pivot), mat_mul(rot_z(rz), mat_mul(rot_y(ry), rot_x(rx))))
    m = local if parent is None else mat_mul(world(parent), local)
    WORLD[name] = m
    return m


def apply(m, p, w=1.0):
    return tuple(m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] * w for i in range(3))


# --- UV packing (box UV) --------------------------------------------------------------------------

CUBES = []


def pack():
    items = []
    for name in ORDER:
        for i, (mat, origin, size) in enumerate(BONES[name][3]):
            w, h, d = size
            items.append((name, i, mat, origin, size, 2 * (w + d), d + h))
    items.sort(key=lambda it: (-it[6], -it[5]))
    x = y = row = 0
    for name, i, mat, origin, size, fw, fh in items:
        if x + fw > TW:
            x, y, row = 0, y + row + 1, 0
        if y + fh > TH:
            raise SystemExit('texture too small at ' + name)
        CUBES.append((name, i, mat, origin, size, x, y))
        x += fw + 1
        row = max(row, fh)
    return y + row


# --- painting -------------------------------------------------------------------------------------

def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def mul(c, f):
    return tuple(max(0, min(255, v * f)) for v in c)


FUR = hx('2A2830')          # near-black velvet
SHEEN = hx('626A82')        # the cool sheen where the fur faces the light
UNDER = hx('4A3C34')        # the warmer, browner belly
BILL = hx('3C3437')
BILL_LIGHT = hx('5A4C4E')
BILL_DARK = hx('221C1E')
NOSTRIL = hx('0E0A0B')
PAD = hx('4A3C3A')
CLAW = hx('C9BB9C')
CLAW_DARK = hx('8C7E66')
EYE = hx('050507')
GLINT = hx('C8D0E0')

_noise_cache = {}


def vnoise(x, y, sx, sy, salt):
    gx, gy = x / sx, y / sy
    i, j = math.floor(gx), math.floor(gy)
    fx, fy = gx - i, gy - j
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)

    def h(a, b):
        key = (a, b, salt)
        if key not in _noise_cache:
            _noise_cache[key] = random.Random('%d,%d,%d' % key).random()
        return _noise_cache[key]
    top = h(i, j) * (1 - fx) + h(i + 1, j) * fx
    bot = h(i, j + 1) * (1 - fx) + h(i + 1, j + 1) * fx
    return top * (1 - fy) + bot * fy


def fur_colour(p, n, uvx, uvy, salt):
    """Coat colour at model point p (y down, the ground at y=24) facing n."""
    height = GROUND_Y - p[1]
    up = max(0.0, -n[1])
    down = max(0.0, n[1])
    # The sheen: strongest on top, along the spine, and fading down the flanks.
    side = abs(p[0])
    sheen = up * 0.55 + max(0.0, (height - 9.0) / 4.0) * 0.25 * (1.0 - min(1.0, side / 6.0))
    c = lerp(FUR, SHEEN, sheen)
    # The belly and the insides of the legs are warmer.
    c = lerp(c, UNDER, down * 0.7 + max(0.0, (4.0 - height) / 4.0) * 0.3)
    # Velvet: very fine, short strokes, so it reads as fur and not as paint.
    stroke = vnoise(uvx, uvy, 0.8, 2.2, salt) - 0.5
    fine = vnoise(uvx, uvy, 0.5, 0.9, salt + 7) - 0.5
    c = mul(c, 1.0 + stroke * 0.22 + fine * 0.12)
    return c


def paint():
    im = Image.new('RGBA', (TW, TH), (0, 0, 0, 0))
    for name, idx, mat, origin, size, u, v in CUBES:
        w, h, d = size
        ox, oy, oz = origin
        m = world(name)
        faces = {
            'up': ((u + d, v, w, d), lambda s, t: (ox + s, oy, oz + d - t), (0, -1, 0)),
            'down': ((u + d + w, v, w, d), lambda s, t: (ox + s, oy + h, oz + d - t), (0, 1, 0)),
            'west': ((u, v + d, d, h), lambda s, t: (ox, oy + t, oz + d - s), (-1, 0, 0)),
            'north': ((u + d, v + d, w, h), lambda s, t: (ox + s, oy + t, oz), (0, 0, -1)),
            'east': ((u + d + w, v + d, d, h), lambda s, t: (ox + w, oy + t, oz + s), (1, 0, 0)),
            'south': ((u + 2 * d + w, v + d, w, h), lambda s, t: (ox + w - s, oy + t, oz + d), (0, 0, 1)),
        }
        for face, ((rx, ry, rw, rh), at, nrm) in faces.items():
            n = apply(m, nrm, 0.0)
            for t in range(rh):
                for s in range(rw):
                    p = apply(m, at(s + 0.5, t + 0.5))
                    if mat == 'fur':
                        c = fur_colour(p, n, rx + s, ry + t, sum(map(ord, name)) + idx * 13)
                    elif mat == 'belly':
                        # The pouch: warm and a little lighter, a fold line across it.
                        c = mul(UNDER, 1.05 + 0.15 * (vnoise(rx + s, ry + t, 0.8, 1.6, 41) - 0.5))
                        if face == 'down' and t == rh // 2:
                            c = mul(c, 0.7)
                    elif mat == 'bill':
                        # Leathery: lighter on top and toward the tip, dark underneath.
                        tip = max(0.0, min(1.0, (-p[2] - 12.0) / 8.0))
                        if face == 'down':
                            c = BILL_DARK
                        elif face == 'up':
                            c = lerp(BILL, BILL_LIGHT, 0.4 + 0.5 * tip)
                        else:
                            c = lerp(BILL, BILL_LIGHT, 0.3 * tip)
                        # The mouth line along the bottom of the bill's sides.
                        if face in ('east', 'west') and t == rh - 1 and idx < 2:
                            c = BILL_DARK
                        # Two nostrils in the bulb's front.
                        if name == 'snout' and idx == 2 and face == 'north' and t == 0 and s in (1, 3):
                            c = NOSTRIL
                        c = mul(c, 1.0 + 0.08 * (vnoise(rx + s, ry + t, 0.7, 0.7, 77) - 0.5))
                    elif mat == 'pad':
                        c = PAD if face != 'up' else fur_colour(p, n, rx + s, ry + t, 5)
                    elif mat == 'claw':
                        c = CLAW_DARK if face in ('down', 'south') else CLAW
                        if face in ('east', 'west') and s == rw - 1:
                            c = CLAW_DARK               # the claw darkens toward the root
                    elif mat == 'eye':
                        c = GLINT if face == 'north' else EYE
                    else:
                        c = (255, 0, 255)
                    im.putpixel((rx + s, ry + t), tuple(int(v) for v in c) + (255,))
    return im


# --- Java -------------------------------------------------------------------------------------------

def java():
    def f(v):
        return ('%.4f' % v).rstrip('0').rstrip('.') + 'F' if v != int(v) else '%d.0F' % v
    out = []
    out.append('package com.hogwartsmagic.client.model;\n')
    out.append('import net.minecraft.client.model.geom.ModelPart;')
    out.append('import net.minecraft.client.model.geom.PartPose;')
    out.append('import net.minecraft.client.model.geom.builders.CubeDeformation;')
    out.append('import net.minecraft.client.model.geom.builders.CubeListBuilder;')
    out.append('import net.minecraft.client.model.geom.builders.LayerDefinition;')
    out.append('import net.minecraft.client.model.geom.builders.MeshDefinition;')
    out.append('import net.minecraft.client.model.geom.builders.PartDefinition;\n')
    out.append('import java.util.HashMap;')
    out.append('import java.util.Map;\n')
    out.append('/**')
    out.append(' * GENERATED by tools/niffler/build_niffler.py from the same description that paints the texture.')
    out.append(' * Do not edit by hand: change the script and run it again.')
    out.append(' */')
    out.append('public final class NifflerGeometry {')
    out.append('    private NifflerGeometry() {\n    }\n')
    out.append('    public static LayerDefinition createBodyLayer() {')
    out.append('        MeshDefinition mesh = new MeshDefinition();')
    out.append('        CubeDeformation none = CubeDeformation.NONE;')
    uv = {(c[0], c[1]): (c[5], c[6]) for c in CUBES}
    for name in ORDER:
        parent, (px, py, pz), (rx, ry, rz), cubes = BONES[name]
        pvar = 'mesh.getRoot()' if parent is None else parent
        cl = 'CubeListBuilder.create()'
        for i, (mat, (x, y, z), (w, h, d)) in enumerate(cubes):
            u, v = uv[(name, i)]
            cl += f'\n                .texOffs({u}, {v}).addBox({f(x)}, {f(y)}, {f(z)}, {f(w)}, {f(h)}, {f(d)}, none)'
        out.append(f'        PartDefinition {name} = {pvar}.addOrReplaceChild("{name}", {cl},')
        out.append(f'                PartPose.offsetAndRotation({f(px)}, {f(py)}, {f(pz)}, {f(rx)}, {f(ry)}, {f(rz)}));')
    out.append(f'        return LayerDefinition.create(mesh, {TW}, {TH});')
    out.append('    }\n')
    out.append('    /** Every bone by name, for the animation code. */')
    out.append('    public static Map<String, ModelPart> bones(ModelPart top) {')
    out.append('        Map<String, ModelPart> m = new HashMap<>();')
    for name in ORDER:
        path, b = [], name
        while b is not None:
            path.append(b)
            b = BONES[b][0]
        expr = 'top' + ''.join(f'.getChild("{p}")' for p in reversed(path))
        out.append(f'        m.put("{name}", {expr});')
    out.append('        return m;')
    out.append('    }')
    out.append('}')
    return '\n'.join(out) + '\n'


if __name__ == '__main__':
    used = pack()
    print('UV rows used: %d of %d' % (used, TH))
    paint().save(PNG)
    with open(JAVA, 'w') as fh:
        fh.write(java())
    for n in ORDER:
        if n.startswith(('paw', 'foot')):
            cubes = BONES[n][3]
            lows = [apply(world(n), (o[0], o[1] + sz[1], o[2])) for _, o, sz in cubes]
            print('%-11s sole at height %.1f' % (n, min(GROUND_Y - q[1] for q in lows)))
    low = min(GROUND_Y - apply(world('pouch'), (0, 1, 0))[1], 99)
    print('pouch bottom at height %.1f' % low)
    print('wrote', os.path.relpath(PNG, ROOT), 'and', os.path.relpath(JAVA, ROOT))
