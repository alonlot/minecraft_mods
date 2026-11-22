"""Builds the Yeti's geometry and texture from one description, so they can never disagree.

    python3 tools/yeti/build_yeti.py

Writes
  src/main/java/com/hogwartsmagic/client/model/YetiGeometry.java   (the bone tree, generated)
  src/main/resources/assets/hogwartsmagic/textures/entity/yeti.png (256x512)

The animal
----------
A Yeti is a great white ape of the high snows, three blocks tall and stooped: a deep chest and a
hump of muscle across the shoulders, a small head carried low and forward, arms long enough to
reach its knees when it stands, short thick legs, and broad flat feet. The skin of its face, palms
and soles is blue-grey.

What makes it read as shaggy and not as a stack of white crates is the fur on its outline:
"fringe" boxes hang off the forearms, the backs of the upper arms, the flanks, the backs of the
thighs and the chin, and their side faces are painted with strands of uneven length whose ends
are transparent (the mob render type is cutout). Each fringe's lower edge is ragged, so the
silhouette breaks up the way long hair does.

Texel density: 32 units a block before the renderer's 0.5 scale, one texel a unit.

Conventions: Minecraft model space, y down, the Yeti faces -z. Ground is y=0 in "root" space
(the root bone sits at model y=24). Rotations are applied as in ModelPart: translate to the pivot,
then rotate Z * Y * X. A positive x rotation tips the front of a bone down.
"""
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..')
JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'hogwartsmagic', 'client', 'model', 'YetiGeometry.java')
PNG = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'hogwartsmagic', 'textures', 'entity', 'yeti.png')
TW, TH = 256, 512
GROUND_Y = 24.0

BONES = {}
ORDER = []


def bone(name, parent, pivot, rot=(0.0, 0.0, 0.0), cubes=()):
    BONES[name] = (parent, pivot, rot, list(cubes))
    ORDER.append(name)


def rounded(mat, centre, size, b=1):
    """A box with its twelve edges cut back by b: three crossed boxes."""
    cx, cy, cz = centre
    w, h, d = size
    out = []
    for sw, sh, sd in ((w, h - 2 * b, d - 2 * b), (w - 2 * b, h, d - 2 * b), (w - 2 * b, h - 2 * b, d)):
        out.append((mat, (cx - sw / 2.0, cy - sh / 2.0, cz - sd / 2.0), (sw, sh, sd)))
    return out


bone('root', None, (0.0, 24.0, 0.0))
# Pelvis, 38 units (1.2 blocks) off the ground.
bone('hips', 'root', (0.0, -38.0, 0.0), cubes=rounded('fur', (0.0, 0.0, 1.0), (26, 14, 18), 3))
# The torso rises from the waist, stooped forward.
bone('torso', 'hips', (0.0, -5.0, 0.0), (0.42, 0.0, 0.0), cubes=(
    rounded('fur', (0.0, -8.0, 0.0), (26, 16, 20), 3)            # belly
    + rounded('fur', (0.0, -25.0, -1.0), (34, 20, 24), 4)        # the barrel chest
    + rounded('fur', (0.0, -37.0, 2.0), (26, 8, 18), 3)          # the hump across the shoulders
    + [('fringe', (-12.0, -41.0, 10.0), (24, 26, 3))]            # the mane, hanging down the back
))
# The head sits low and forward of the hump, held up against the stoop.
bone('head', 'torso', (0.0, -36.0, -10.0), (-0.42, 0.0, 0.0), cubes=[
    ('fur', (-7.0, -13.0, -10.0), (14, 13, 13)),                 # skull
    ('fur', (-8.0, -9.0, -9.0), (16, 9, 11)),                    # cheeks and jowls
    ('fur', (-5.0, -15.0, -7.0), (10, 2, 9)),                    # crest
    ('fur', (-7.0, -11.0, -12.0), (14, 3, 2)),                   # brow ridge, overhanging the eyes
    ('skin', (-6.0, -8.0, -11.0), (12, 7, 1)),                   # the face: eyes on its front
    ('skin', (-5.0, -4.0, -14.0), (10, 5, 4)),                   # muzzle
    ('tooth', (-4.0, 1.0, -14.0), (1, 2, 1)),                    # upper fangs
    ('tooth', (3.0, 1.0, -14.0), (1, 2, 1)),
])
bone('jaw', 'head', (0.0, 1.0, -8.0), cubes=[
    ('mouth', (-5.0, 0.0, -6.0), (10, 2, 6)),
    ('tooth', (-3.0, -1.0, -6.0), (1, 1, 1)),                    # lower fangs
    ('tooth', (2.0, -1.0, -6.0), (1, 1, 1)),
    ('fringe', (-6.0, 1.0, -6.0), (12, 4, 7)),                   # the beard under the chin
])

for s, side in ((-1, 'left'), (1, 'right')):
    # Arms: long enough that the knuckles hang at the knee. Fringes on the backs of the arms.
    bone(f'shoulder_{side}', 'torso', (s * 18.0, -33.0, -2.0), (-0.42, 0.0, -s * 0.08), [
        ('fur', (-5.5, -3.0, -5.5), (11, 26, 11)),
        ('fringe', (-6.0 + (3 if s > 0 else 0), 2.0, 4.0), (9, 20, 3)),
    ])
    bone(f'elbow_{side}', f'shoulder_{side}', (0.0, 22.0, 0.0), (-0.15, 0.0, 0.0), [
        ('fur', (-5.0, 0.0, -5.0), (10, 23, 10)),
        ('fringe', (-6.0, 4.0, 3.0), (12, 21, 4)),
        ('fringe', (-7.0 if s < 0 else 5.0, 6.0, -4.0), (2, 18, 8)),
    ])
    bone(f'hand_{side}', f'elbow_{side}', (0.0, 23.0, 0.0), (0.1, 0.0, 0.0), [
        ('skin', (-5.0, 0.0, -5.0), (10, 6, 10)),                # palm
        ('skin', (-5.0, 5.0, -6.0), (10, 4, 5)),                 # knuckles
        ('claw', (-5.0, 8.0, -7.0), (2, 2, 2)),
        ('claw', (-1.0, 8.0, -7.0), (2, 2, 2)),
        ('claw', (3.0, 8.0, -7.0), (2, 2, 2)),
        ('fur', (-5.5, -1.0, -5.5), (11, 3, 11)),                # the wrist cuff of fur
    ])
    # Legs: short, thick, with big flat feet.
    bone(f'thigh_{side}', 'hips', (s * 8.0, 4.0, 1.0), cubes=[
        ('fur', (-6.5, -3.0, -6.5), (13, 19, 13)),
        ('fringe', (-6.0, 4.0, 5.0), (12, 14, 3)),
    ])
    bone(f'shin_{side}', f'thigh_{side}', (0.0, 15.0, 0.0), cubes=[
        ('fur', (-5.5, 0.0, -5.5), (11, 15, 11)),
    ])
    bone(f'foot_{side}', f'shin_{side}', (0.0, 15.0, 0.0), cubes=[
        ('skin', (-6.0, 0.0, -12.0), (12, 4, 17)),
        ('claw', (-6.0, 2.0, -14.0), (3, 2, 2)),
        ('claw', (-1.5, 2.0, -14.0), (3, 2, 2)),
        ('claw', (3.0, 2.0, -14.0), (3, 2, 2)),
        ('fur', (-6.5, -2.0, -7.0), (13, 3, 12)),                # fur spilling over the foot
    ])


# The ice boulder it throws, carried in the right hand. Hidden except while it throws.
bone('boulder', 'hand_right', (0.0, 12.0, -1.0), cubes=[
    ('snowpack', (-7.0, -1.0, -7.0), (14, 14, 14)),
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


FUR = hx('E6ECF0')          # snow-white, a little cool
FUR_LIGHT = hx('F7FAFC')
FUR_SHADE = hx('A3B1C2')    # blue-grey where the light does not reach
FUR_DEEP = hx('7E8DA0')
SKIN = hx('6E8094')         # the blue-grey skin of the face, palms and soles
SKIN_LIGHT = hx('8798AC')
SKIN_DARK = hx('4A596B')
SOCKET = hx('1C2430')
IRIS = hx('BDEBFF')         # pale, icy eyes
NOSE = hx('2C3440')
MOUTH = hx('3A1F27')
LIP = hx('394654')
TOOTH = hx('EDE7D3')
TOOTH_SHADE = hx('BEB59E')
CLAW = hx('3A3F47')
CLAW_LIGHT = hx('5B626C')

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


def hashf(*k):
    return random.Random(','.join(str(v) for v in k)).random()


def fur_colour(p, n, uvx, uvy, salt):
    """Coat colour at model point p (y down, the ground at y=24) facing n."""
    up = max(0.0, -n[1])
    down = max(0.0, n[1])
    height = GROUND_Y - p[1]
    c = lerp(FUR, FUR_LIGHT, up * 0.8)
    # Only faces that look nearly straight down are shaded hard: the game shades them again, and a
    # stooped chest that faces a little downward came out as a grey slab.
    c = lerp(c, FUR_SHADE, max(0.0, down - 0.5) * 1.2 + max(0.0, (30.0 - height) / 30.0) * 0.25)
    # Long strands running down the body: stretched noise, and darker partings between locks.
    lock = vnoise(uvx, uvy, 1.6, 7.0, salt)
    fine = vnoise(uvx, uvy, 0.7, 2.5, salt + 7)
    c = lerp(c, FUR_SHADE, max(0.0, 0.34 - lock) * 0.8)
    c = mul(c, 1.0 + (fine - 0.5) * 0.10)
    # A face that leans toward the ground is shaded by the game's lighting already; painted a shade
    # lighter, a stooped chest reads as fur in shadow and not as a grey plate.
    if 0.2 < down < 0.6:
        c = lerp(c, FUR_LIGHT, down * 0.6)
    return c


def strand_end(column, salt, length):
    """How many texels of a fringe column are hair, counted from its top: ragged, never even."""
    a = hashf('end', column, salt)
    b = vnoise(column, 0, 2.3, 1, salt + 3)
    cut = int(round(length * (0.25 + 0.45 * a * 0.6 + 0.4 * b * 0.7)))
    return max(2, length - cut)


def face_texel(s, t):
    """The face plate, 12 wide and 7 tall, under the brow: deep-set eyes, the bridge of the nose."""
    c = SKIN
    cx = abs(s - 5.5) / 5.5
    c = mul(c, 1.05 - 0.15 * cx * cx)
    if t == 0:
        c = lerp(c, SKIN_DARK, 0.6)                 # shadow under the brow ridge
    if t in (1, 2) and (2 <= s <= 4 or 7 <= s <= 9):
        c = SOCKET                                  # the sockets
    if t == 1 and s in (3, 8):
        c = IRIS                                    # pale eyes, catching the light
    if t == 3 and (2 <= s <= 4 or 7 <= s <= 9):
        c = lerp(c, SKIN_DARK, 0.4)                 # heavy lower lids
    if s in (5, 6) and t >= 2:
        c = lerp(c, SKIN_LIGHT, 0.5)                # the bridge of the nose
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
        salt = sum(map(ord, name)) * 7 + idx * 31
        for face, ((rx, ry, rw, rh), at, nrm) in faces.items():
            n = apply(m, nrm, 0.0)
            for t in range(rh):
                for s in range(rw):
                    p = apply(m, at(s + 0.5, t + 0.5))
                    alpha = 255
                    if mat == 'fur':
                        c = fur_colour(p, n, rx + s, ry + t, salt)
                    elif mat == 'fringe':
                        c = fur_colour(p, n, rx + s, ry + t, salt)
                        if face == 'down':
                            alpha = 0                       # no flat bottom to a hank of hair
                        elif face != 'up':
                            if t >= strand_end(rx + s, salt, rh):
                                alpha = 0
                            elif t >= strand_end(rx + s, salt, rh) - 2:
                                c = mul(c, 0.92)            # the tips are thinner, a little darker
                    elif mat == 'skin':
                        if name == 'head' and idx == 4 and face == 'north':
                            c = face_texel(s, t)
                        elif name == 'head' and idx == 5:
                            # The muzzle: nostrils on its front, a nose pad on top, a lip line.
                            c = SKIN_LIGHT if face in ('up', 'north') else SKIN
                            if face == 'north':
                                if t <= 1 and s in (3, 6):
                                    c = NOSE
                                if t == rh - 1:
                                    c = LIP
                            if face == 'up' and t >= rh - 2 and 3 <= s <= 6:
                                c = NOSE
                            if face == 'down':
                                c = MOUTH
                        elif face == 'down':
                            c = SKIN_DARK                   # palms and soles
                        else:
                            c = mul(SKIN, 0.95 + 0.1 * vnoise(rx + s, ry + t, 1.5, 1.5, 61))
                    elif mat == 'mouth':
                        c = MOUTH if face == 'up' else LIP
                    elif mat == 'tooth':
                        c = TOOTH if face in ('north', 'up') else TOOTH_SHADE
                    elif mat == 'snowpack':
                        # Packed snow with blue ice showing through cracks.
                        k = vnoise(rx + s, ry + t, 2.0, 2.0, 88)
                        c = lerp(hx('F2F7FB'), hx('CFE0EE'), k)
                        if abs(vnoise(rx + s, ry + t, 3.5, 3.5, 89) - 0.5) < 0.05:
                            c = hx('8DB8DA')
                    elif mat == 'claw':
                        c = CLAW_LIGHT if face == 'up' else CLAW
                    else:
                        c = (255, 0, 255)
                    im.putpixel((rx + s, ry + t), tuple(int(q) for q in c) + (alpha,))
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
    out.append(' * GENERATED by tools/yeti/build_yeti.py from the same description that paints the texture.')
    out.append(' * Do not edit by hand: change the script and run it again.')
    out.append(' */')
    out.append('public final class YetiGeometry {')
    out.append('    private YetiGeometry() {\n    }\n')
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
        if n.startswith('foot'):
            lows = [apply(world(n), (o[0], o[1] + sz[1], o[2])) for _, o, sz in BONES[n][3]]
            print('%-11s sole at height %.1f' % (n, min(GROUND_Y - q[1] for q in lows)))
    tops = [GROUND_Y - apply(world('head'), (0, -15, -2))[1]]
    print('head top at height %.1f units' % tops[0])
    hand = [apply(world(n), (0, 10, 0)) for n in ('hand_left', 'hand_right')]
    print('knuckles at height %.1f' % (GROUND_Y - hand[0][1]))
    print('wrote', os.path.relpath(PNG, ROOT), 'and', os.path.relpath(JAVA, ROOT))
