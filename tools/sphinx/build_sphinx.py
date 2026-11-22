"""Builds the Sphinx's geometry and texture from one description, so they can never disagree.

    python3 tools/sphinx/build_sphinx.py

Writes
  src/main/java/com/hogwartsmagic/client/model/SphinxGeometry.java   (the bone tree, generated)
  src/main/resources/assets/hogwartsmagic/textures/entity/sphinx.png (256x256)

Why it looks like an animal and not a block creature
----------------------------------------------------
* Anatomy, not a box per limb. A deep chest and a tucked waist, heavy haunches, shoulder and hip
  joints, elbows, hocks, wrists, separate toes, a tapering five-part tail, a human face with a nose
  and chin that stand out of it.
* Twice the texel density. The model is built at 32 units a block and drawn at 0.6 scale, so every
  model unit is half a block pixel and carries one texel: fur strokes and kohl lines are possible.
* The coat is painted from the body, not per box. Each texel is placed on the posed rest model, and
  its colour comes from where it is on the animal and which way it faces: pale belly, throat and
  inner legs, a darker back, darker low down. The colour flows across box seams the way a coat
  does, which is most of what separates an animal from a stack of painted crates.

Conventions: Minecraft model space, y down, the Sphinx faces -z. Ground is y=0 in "root" space
(the root bone sits at model y=24). Rotations are applied as in ModelPart: translate to the pivot,
then rotate Z * Y * X.
"""
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..')
JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'hogwartsmagic', 'client', 'model', 'SphinxGeometry.java')
PNG = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'hogwartsmagic', 'textures', 'entity', 'sphinx.png')
TW, TH = 256, 256
GROUND_Y = 24.0          # model y of the ground: the root bone's pivot

# --- the skeleton ---------------------------------------------------------------------------------
# bone: name -> (parent, pivot (x, y, z) in parent space, rotation (x, y, z) radians, [cubes])
# cube: (material, origin (x, y, z), size (w, h, d)) in bone space. Sizes are whole units so box UV
# lands on whole texels; origins may be halves.

BONES = {}
ORDER = []


def bone(name, parent, pivot, rot=(0.0, 0.0, 0.0), cubes=()):
    BONES[name] = (parent, pivot, rot, list(cubes))
    ORDER.append(name)


bone('root', None, (0.0, 24.0, 0.0))
# Torso: pivot 30 units off the ground, in the middle of the body.
bone('torso', 'root', (0.0, -30.0, 0.0), cubes=[
    ('fur', (-9.0, -10.0, -24.0), (18, 20, 18)),     # chest and shoulders, deep
    ('fur', (-6.0, 10.0, -22.0), (12, 4, 12)),       # brisket, the keel of the chest
    ('fur', (-8.0, -13.0, -22.0), (16, 3, 14)),      # withers, the shoulder hump
    ('fur', (-7.0, -7.0, -6.0), (14, 15, 17)),       # waist, tucked up underneath
    ('fur', (-8.0, -8.0, 10.0), (16, 17, 13)),       # hips and haunches
])
# Only the belly gets a bevel. Bevels on the chest front and the rump were tried and removed: a box
# can only add volume, never cut a corner, so they stood out as a plank and a wedge.
bone('belly_bevel', 'torso', (0.0, 8.0, -6.0), (0.35, 0.0, 0.0), [
    ('fur', (-6.5, -2.0, -1.0), (13, 4, 8)),
])

for s, side in ((-1, 'left'), (1, 'right')):
    # Foreleg: shoulder -> elbow -> wrist. Lions carry the elbow back of the shoulder.
    bone(f'shoulder_{side}', 'torso', (s * 5.5, -3.0, -15.0), (0.2, 0.0, 0.0), [
        ('fur', (-3.5, -1.0, -5.0), (7, 17, 10)),   # upper arm: the shoulder muscle, inside the chest's width
    ])
    bone(f'elbow_{side}', f'shoulder_{side}', (0.0, 16.0, 0.5), (-0.2, 0.0, 0.0), [
        ('fur', (-3.0, 0.0, -3.0), (6, 13, 6)),
    ])
    bone(f'wrist_{side}', f'elbow_{side}', (0.0, 13.0, 0.0), cubes=[
        ('paw', (-4.0, 0.0, -6.0), (8, 4, 9)),
        ('toe', (-4.0, 1.0, -8.0), (2, 3, 2)),
        ('toe', (-2.0, 1.0, -9.0), (2, 3, 3)),
        ('toe', (0.0, 1.0, -9.0), (2, 3, 3)),
        ('toe', (2.0, 1.0, -8.0), (2, 3, 2)),
    ])
    # Hind leg: hip -> stifle -> hock -> paw. The thigh angles forward, the shin back.
    bone(f'hip_{side}', 'torso', (s * 6.0, -2.0, 16.0), (-0.3, 0.0, 0.0), [
        ('fur', (-4.0, -4.0, -7.0), (8, 17, 13)),
    ])
    bone(f'stifle_{side}', f'hip_{side}', (0.0, 12.0, -3.0), (0.95, 0.0, 0.0), [
        ('fur', (-3.0, 0.0, -3.0), (6, 11, 6)),
    ])
    bone(f'hock_{side}', f'stifle_{side}', (0.0, 11.0, 0.0), (-0.65, 0.0, 0.0), [
        ('fur', (-2.0, 0.0, -2.0), (4, 8, 4)),
    ])
    bone(f'hpaw_{side}', f'hock_{side}', (0.0, 8.0, 0.0), cubes=[
        ('paw', (-3.5, 0.0, -6.0), (7, 4, 8)),
        ('toe', (-3.5, 1.0, -8.0), (2, 3, 2)),
        ('toe', (-1.5, 1.0, -8.5), (2, 3, 3)),
        ('toe', (0.5, 1.0, -8.0), (2, 3, 2)),
    ])

# Neck rises forward out of the chest; the head is held upright on it.
bone('neck', 'torso', (0.0, -6.0, -19.0), (0.55, 0.0, 0.0), [
    ('fur', (-6.0, -15.0, -6.0), (12, 16, 12)),
])
bone('head', 'neck', (0.0, -14.0, -1.0), (-0.55, 0.0, 0.0), [
    ('skin', (-6.0, -14.0, -6.0), (12, 14, 12)),     # skull and face; the face is the -z side
    # The nose: small and set between the eyes and the lip. A first nose, two deep and four long,
    # hung down to the mouth; a chin box under it stood out like a shelf and was removed.
    ('skin', (-1.0, -7.0, -7.0), (2, 3, 1)),
])
bone('nemes', 'head', (0.0, 0.0, 0.0), cubes=[
    ('nemes', (-7.0, -16.0, -7.0), (14, 6, 14)),     # the crown: a narrow gold band at its foot, stripes above
    ('nemes', (-6.0, -17.0, -6.0), (12, 1, 12)),     # a rounded top, so it is cloth and not a box hat
    ('nemes', (-3.0, -9.0, 6.0), (6, 12, 3)),        # the queue down the back of the neck
    ('gold', (-1.0, -14.0, -8.0), (2, 3, 1)),        # the uraeus, the cobra rising from the band
])
for s, side in ((-1, 'left'), (1, 'right')):
    # The side panels flare outward toward the shoulders: the nemes' trapezoid outline.
    bone(f'nemes_panel_{side}', 'nemes', (s * 6.0, -11.0, 0.0), (0.0, 0.0, -s * 0.2), [
        ('nemes', (-2.0 + s * 2.0, 0.0, -4.0), (4, 12, 10)),
    ])
    bone(f'lappet_{side}', f'nemes_panel_{side}', (s * 1.5, 11.0, -3.0), (0.0, 0.0, s * 0.2), [
        ('nemes', (-1.5, 0.0, -1.5), (3, 13, 3)),
    ])

# Tail: four tapering segments and the tuft.
bone('tail1', 'torso', (0.0, -6.0, 23.0), (-1.0, 0.0, 0.0), [('fur', (-2.0, -2.0, 0.0), (4, 4, 9))])
bone('tail2', 'tail1', (0.0, 0.0, 8.5), (0.25, 0.0, 0.0), [('fur', (-1.5, -1.5, 0.0), (3, 3, 8))])
bone('tail3', 'tail2', (0.0, 0.0, 7.5), (0.3, 0.0, 0.0), [('fur', (-1.5, -1.5, 0.0), (3, 3, 7))])
bone('tail4', 'tail3', (0.0, 0.0, 6.5), (0.35, 0.0, 0.0), [('fur', (-1.0, -1.0, 0.0), (2, 2, 6))])
bone('tuft', 'tail4', (0.0, 0.0, 5.5), cubes=[('tuft', (-2.5, -2.5, 0.0), (5, 5, 6))])


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

CUBES = []      # (bone, index, material, origin, size, u, v)


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


TAWNY = hx('C99A5B')
TAWNY_DARK = hx('A9783F')
PALE = hx('EAD6AE')
TUFT = hx('3B2716')
PAD = hx('5A4032')
SKIN = hx('C8946A')
SKIN_SHADE = hx('A87650')
SKIN_LIGHT = hx('D9A87E')
BROW = hx('4A3020')
KOHL = hx('17110F')
IRIS = hx('3A2414')
SCLERA = hx('D9C7A8')
LIP = hx('9A5446')
LIP_DARK = hx('6E3A30')
GOLD = hx('E2B447')
GOLD_DARK = hx('A87B22')
GOLD_LIGHT = hx('F6D77C')
LAPIS = hx('2B4F9E')
LAPIS_DARK = hx('1D3672')

rng = random.Random(1907)
TORSO_BONES = {'torso', 'belly_bevel'}
_noise_cache = {}


def vnoise(x, y, sx, sy, salt):
    """Smooth value noise, stretched by (sx, sy): long along y gives fur strokes."""
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


def fur_colour(p, n, uvx, uvy, salt, torso):
    """Coat colour at model point p (y down, the ground at y=24) facing n."""
    height = GROUND_Y - p[1]
    side = abs(p[0])
    # Pale underneath: the belly, the throat, the insides of the legs.
    down = max(0.0, n[1])                       # facing the ground
    inward = max(0.0, -n[0] * (1 if p[0] > 0 else -1)) if side > 2.5 else 0.0
    # On the body, the flank pales toward the belly line; legs only pale a little toward the paw.
    low = (1.0 - (height - 16.0) / 9.0) if torso else (0.35 * (1.0 - height / 22.0))
    pale = max(down * 0.9, inward * 0.7, low)
    c = lerp(TAWNY, PALE, pale)
    # A darker saddle along the back.
    up = max(0.0, -n[1])
    c = lerp(c, TAWNY_DARK, up * max(0.0, 1.0 - side / 8.0) * 0.6)
    # Fur strokes, running down the body.
    stroke = vnoise(uvx, uvy, 1.2, 4.0, salt) - 0.5
    fine = vnoise(uvx, uvy, 0.6, 1.6, salt + 7) - 0.5
    c = mul(c, 1.0 + stroke * 0.12 + fine * 0.07)
    # Soft shadow low down, where the light does not reach.
    if height < 8:
        c = mul(c, 0.86 + 0.14 * height / 8.0)
    return c


def face_texel(fx, fy, fw, fh):
    """The face, 12 wide and 14 tall. The crown covers rows 0-3; the face shows from row 4.

    Laid out as a face is: eyes at the middle of the head, the nose cube over rows 7-9, the
    mouth two rows under it. Shading is soft and round (lighter in the middle, darker toward the
    jaw), never per column: column bands read as stripes painted on a box.
    """
    cx = (fx - (fw - 1) / 2.0) / ((fw - 1) / 2.0)          # -1 .. 1 across the face
    c = mul(SKIN, 1.04 - 0.14 * cx * cx)
    if fy == 4:
        c = mul(c, 0.86)                                  # shadow under the brow band
    if fy >= 12:
        c = mul(c, 0.97 - 0.05 * abs(cx))                 # the chin turns under
    eye_l, eye_r = (2, 3, 4), (7, 8, 9)
    # Brows: thin, arched (the middle a little stronger), tapering at the ends.
    if fy == 5:
        strength = {2: 0.45, 3: 0.65, 4: 0.5, 7: 0.5, 8: 0.65, 9: 0.45, 1: 0.2, 10: 0.2}
        if fx in strength:
            c = lerp(c, BROW, strength[fx])
    # Upper lid lined in kohl, one texel thick.
    if fy == 6 and (fx in eye_l or fx in eye_r):
        c = lerp(c, KOHL, 0.85)
    # The open eye: white, the dark iris, white; the kohl wing drawn out past the outer corner.
    if fy == 7:
        row = {2: SCLERA, 3: IRIS, 4: SCLERA, 7: SCLERA, 8: IRIS, 9: SCLERA}
        if fx in row:
            c = row[fx]
        if fx in (1, 10):
            c = lerp(c, KOHL, 0.8)
    if fy == 8 and (fx in eye_l or fx in eye_r):
        c = lerp(c, SKIN_SHADE, 0.35)                     # the lower lid
    if fy == 9 and fx in (4, 7):
        c = lerp(c, SKIN_SHADE, 0.4)                      # beside the nose
    if fy == 10 and fx in (5, 6):
        c = lerp(c, SKIN_SHADE, 0.45)                     # under the nose
    if fy == 11:
        if fx in (5, 6):
            c = LIP
        elif fx in (4, 7):
            c = lerp(c, LIP_DARK, 0.55)                   # the corners of the mouth
    if fy == 12 and fx in (5, 6):
        c = lerp(c, LIP, 0.55)                            # the lower lip, fuller in the middle
    return c


def nemes_colour(p, n, face):
    if face == 'down':
        return LAPIS_DARK
    if face == 'up':
        band = int(math.floor((p[2] + 40) / 2.0)) % 2
        return GOLD if band == 0 else LAPIS
    band_y = (-p[1] + 200) / 2.0
    band = int(math.floor(band_y)) % 2
    frac = band_y - math.floor(band_y)
    base = GOLD if band == 0 else LAPIS
    return mul(base, 1.08 if frac > 0.5 and band == 0 else (0.88 if band == 1 and frac < 0.5 else 1.0))


def paint():
    im = Image.new('RGBA', (TW, TH), (0, 0, 0, 0))
    for name, idx, mat, origin, size, u, v in CUBES:
        w, h, d = size
        ox, oy, oz = origin
        m = world(name)
        # face: (rect, point-of-texel(s, t) in bone space, normal in bone space)
        # Box UV, as ModelPart.Cube unwraps it: the strip runs west | north | east | south round the
        # box, so each side shares its edge with the next; the top sits over the north face, the
        # bottom beside it. (s, t) is the texel in the face rect, t down the rect.
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
                        c = fur_colour(p, n, rx + s, ry + t, sum(map(ord, name)), name in TORSO_BONES)
                    elif mat == 'paw':
                        c = PAD if face == 'down' else mul(lerp(PALE, TAWNY, 0.35), 1.0 - 0.06 * (t % 2))
                    elif mat == 'toe':
                        c = PAD if face == 'down' else lerp(PALE, TAWNY, 0.3)
                        if face in ('east', 'west'):
                            c = mul(c, 0.75)                 # the dark line between toes
                        if face == 'north' and t == rh - 1:
                            c = hx('2A201A')                 # the claw sheath at the tip
                    elif mat == 'tuft':
                        c = mul(TUFT, 0.9 + 0.25 * vnoise(rx + s, ry + t, 0.7, 2.5, 99))
                    elif mat == 'gold':
                        c = GOLD_LIGHT if t == 0 else (GOLD_DARK if s == 0 else GOLD)
                    elif mat == 'nemes':
                        c = nemes_colour(p, n, face)
                        # The brow band: plain gold, a dark edge at its foot.
                        if name == 'nemes' and idx == 0 and face == 'north' and t >= rh - 2:
                            c = GOLD_DARK if t == rh - 1 else GOLD_LIGHT
                    elif mat == 'skin':
                        if name == 'head' and idx == 0 and face == 'north':
                            c = face_texel(s, t, rw, rh)
                        elif name == 'head' and idx == 1:
                            c = mul(SKIN_SHADE, 0.7) if face == 'down' else (SKIN_LIGHT if face in ('north', 'up') else SKIN)
                        else:
                            c = SKIN_SHADE if face in ('east', 'west', 'south') else SKIN
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
    out.append(' * GENERATED by tools/sphinx/build_sphinx.py from the same description that paints the texture.')
    out.append(' * Do not edit by hand: change the script and run it again.')
    out.append(' */')
    out.append('public final class SphinxGeometry {')
    out.append('    private SphinxGeometry() {\n    }\n')
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
    feet = {n: apply(world(n), (0, 4, 0)) for n in ORDER if n.startswith(('wrist', 'hpaw'))}
    for n, p in feet.items():
        print('%-14s paw sole at height %.1f, z %.1f' % (n, GROUND_Y - p[1], p[2]))
    print('wrote', os.path.relpath(PNG, ROOT), 'and', os.path.relpath(JAVA, ROOT))
