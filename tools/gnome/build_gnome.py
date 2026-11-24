"""Builds the garden gnome's geometry and texture from one description, so they can never disagree.

    python tools/gnome/build_gnome.py

Writes
  src/main/java/com/hogwartsmagic/client/model/GnomeGeometry.java            (the bone tree, generated)
  src/main/resources/assets/hogwartsmagic/textures/entity/gnome.png          (128x128)
  src/main/resources/assets/hogwartsmagic/textures/item/held_gnome.png       (16x16, the inventory face)
  src/main/resources/assets/hogwartsmagic/textures/item/blank.png            (16x16, fully transparent)
  tools/gnome/preview.png                                                    (front, three-quarter and side views)

The creature
------------
A Harry Potter garden gnome: a bald, knobbly, potato-shaped head that is half the whole height, on a squat
round body with a pot belly; bat-wide ears, a bulbous nose under a heavy brow, bead eyes, a wide grin, stubby
arms with knobbly fists, short legs and big flat feet. Leathery brown-grey skin, darker warts on the knobs, a
ragged sackcloth loincloth round the hips.

Texel density: 32 units a block before the renderer's 0.5 scale; the gnome stands 16 units high (0.5 blocks).
The skin is painted from the body, as on the Niffler: each texel is placed on the rest model and its colour
comes from where it is and which way it faces, plus value-noise mottling, creases and warts.

Conventions: Minecraft model space, y down, the gnome faces -z. Ground is y=0 in "root" space (the root bone
sits at model y=24). Rotations are applied as in ModelPart: translate to the pivot, then rotate Z * Y * X.
A positive x rotation tips the front of a bone down.
"""
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'hogwartsmagic', 'textures')
JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'hogwartsmagic', 'client', 'model', 'GnomeGeometry.java')
PNG = os.path.join(RES, 'entity', 'gnome.png')
ITEM_PNG = os.path.join(RES, 'item', 'held_gnome.png')
BLANK_PNG = os.path.join(RES, 'item', 'blank.png')
PREVIEW = os.path.join(HERE, 'preview.png')
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


# --- the bone tree -------------------------------------------------------------------------------------
# Heights are measured up from the ground. Feet 0-1, legs 1-3, body 3-9 (centre 6), head 8-15, crown knob to 16.

bone('root', None, (0.0, 24.0, 0.0))

# The body: a small round barrel with a pot belly pushed out in front and a loincloth round the hips.
bone('body', 'root', (0.0, -6.0, 0.0), cubes=(
    rounded('skin', (0.0, 0.0, 0.0), (6, 6, 5), 1)
    + [('skin', (-2.0, -0.5, -3.0), (4, 3, 1))]            # the pot belly
    + [('cloth', (-3.5, 1.0, -3.0), (7, 2, 6))]            # the sackcloth band at the hips
    + [('cloth', (-2.0, 3.0, -3.0), (4, 1, 1))]            # one ragged flap hanging in front
    + [('cloth', (1.0, 3.0, 2.0), (3, 1, 1))]              # and one behind
))

# The head: a bald potato, half the gnome's height, hardly a neck. Pivot at its base, 2 units above the body's
# centre. Asymmetric on purpose: a lump on one cheek, a bigger knob off the crown.
bone('head', 'body', (0.0, -2.0, 0.0), cubes=(
    rounded('skin', (0.0, -3.0, 0.0), (10, 6, 9), 1)
    + [('skin', (-4.0, -7.0, -3.5), (8, 1, 7))]            # the domed cap of the skull
    + [('skin', (-1.5, -8.0, -2.5), (3, 1, 3))]            # the crown knob
    + [('skin', (2.0, -8.0, 0.5), (2, 1, 2))]              # a second, flatter lump off the crown
    + [('wart', (-3.5, -7.9, 0.5), (1, 1, 1))]             # a wart on the cap
    + [('skin', (-4.5, -5.3, -5.3), (9, 1, 1))]            # the heavy brow ridge
    + [('skin', (-6.0, -3.0, -3.0), (1, 2, 2))]            # left cheek lump
    + [('wart', (5.0, -2.5, -2.0), (1, 2, 3))]             # right cheek lump, bigger and darker
    + [('skin', (-6.0, -6.0, 0.0), (1, 2, 2))]             # a left temple bump
    + [('skin', (-3.0, -6.0, 4.5), (4, 3, 1))]             # a lump at the back of the skull
    + [('wart', (-3.5, -6.6, -5.0), (1, 1, 1))]            # a wart on the forehead
    + [('nose', (-1.5, -4.5, -6.5), (3, 3, 2))]            # the bulbous nose
    + [('eye', (-4.0, -4.3, -5.0), (2, 2, 1))]
    + [('eye', (2.0, -4.3, -5.0), (2, 2, 1))]
))

# The jaw: hinged at the back of the mouth so it can drop for the bite, the eat and the giggle.
bone('jaw', 'head', (0.0, -1.0, -1.0), cubes=[
    ('jaw', (-3.5, -0.5, -3.5), (7, 2, 4)),
])

for s, side in ((-1, 'left'), (1, 'right')):
    # Ears: a root where the head ends, a wide thin flap facing forward, and a pointed tip. Tilted up and back.
    x0 = 0.0 if s > 0 else -2.0
    bone(f'ear_{side}', 'head', (s * 5.0, -4.0, 0.5), (0.0, s * 0.35, -s * 0.3), [
        ('skin', (x0, -1.5, -1.0), (2, 3, 2)),
        ('ear', (2.0 if s > 0 else -5.0, -3.5, -0.5), (3, 6, 1)),
        ('ear', (5.0 if s > 0 else -6.0, -5.0, -0.5), (1, 3, 1)),
    ])

for s, side in ((-1, 'left'), (1, 'right')):
    # Arms: stubby, a knobbly fist at the end.
    bone(f'arm_{side}', 'body', (s * 4.0, -2.0, 0.0), cubes=[
        ('skin', (-1.0, -1.0, -1.0), (2, 5, 2)),
        ('skin', (-1.5, 4.0, -1.5), (3, 2, 3)),
        ('wart', (-0.5 + s * 0.5, 5.5, -2.0), (1, 1, 1)),
    ])
    # Legs: short and slightly bowed, each ending in an oversized flat foot.
    bone(f'leg_{side}', 'body', (s * 2.1, 3.0, 0.0), (0.0, 0.0, -s * 0.1), [
        ('skin', (-1.0, 0.0, -1.0), (2, 2, 2)),
        ('foot', (-2.0, 2.0, -3.5), (4, 1, 5)),
    ])


# --- forward kinematics (rest pose) ---------------------------------------------------------------------

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


# --- UV packing (box UV) -----------------------------------------------------------------------------------

CUBES = []


def pack():
    items = []
    for name in ORDER:
        for i, (mat, origin, size) in enumerate(BONES[name][3]):
            w, h, d = size
            items.append((name, i, mat, origin, size, int(2 * (w + d)), int(d + h)))
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


# --- painting ----------------------------------------------------------------------------------------------

def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def mul(c, f):
    return tuple(max(0, min(255, v * f)) for v in c)


BASE = hx('7D6E5D')          # leathery brown-grey
SHADOW = hx('5A4E42')
HILITE = hx('9A8B78')
WART = hx('4B3F35')
WART_LIGHT = hx('6A5A4A')
INNER_EAR = hx('8F6A5E')
INNER_EAR_DARK = hx('6E4F47')
MOUTH = hx('2E2420')
TONGUE = hx('7A3A3A')
TOOTH = hx('CFC3A2')
EYE = hx('050505')
GLINT = hx('6E6C64')
CLOTH = hx('6B5A3E')
CLOTH_DARK = hx('4A3E2A')
CLOTH_LIGHT = hx('86724F')
NOSE = hx('94705F')
NOSE_HI = hx('B08672')
NOSTRIL = hx('2A1A16')

_noise_cache = {}
_hash_cache = {}


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


def hrand(x, y, salt):
    key = (x, y, salt)
    if key not in _hash_cache:
        _hash_cache[key] = random.Random('h%d,%d,%d' % key).random()
    return _hash_cache[key]


LIGHT = (-0.35, -0.75, -0.55)
_ll = math.sqrt(sum(v * v for v in LIGHT))
LIGHT = tuple(v / _ll for v in LIGHT)


def ramp(t):
    t = max(0.0, min(1.0, t))
    return lerp(SHADOW, BASE, t * 2.0) if t < 0.5 else lerp(BASE, HILITE, (t - 0.5) * 2.0)


def skin_colour(p, n, ux, uy, salt, tint=None):
    """Leathery skin at model point p (y down, the ground at y=24) facing n."""
    height = GROUND_Y - p[1]
    lam = n[0] * LIGHT[0] + n[1] * LIGHT[1] + n[2] * LIGHT[2]
    t = 0.5 + 0.30 * lam + max(0.0, height - 8.0) / 8.0 * 0.06 - max(0.0, 2.5 - height) * 0.05
    c = ramp(t)
    if tint is not None:
        c = lerp(c, tint[0], tint[1])
    # Soft mottled patches, finer grain, and horizontal creases: it is old leather.
    patch = vnoise(ux, uy, 3.5, 3.5, salt) - 0.5
    grain = vnoise(ux, uy, 0.9, 0.9, salt + 5) - 0.5
    crease = vnoise(ux, uy, 5.0, 0.7, salt + 11)
    f = 1.0 + patch * 0.16 + grain * 0.11
    if crease > 0.78:
        f *= 0.86
    # Forehead wrinkles: dark horizontal lines across the front of the skull.
    if n[2] < -0.9 and 12.2 <= height <= 13.9 and int(height * 2) % 3 == 0 and vnoise(ux, uy, 3.0, 9.0, salt + 2) > 0.38:
        f *= 0.80
    r = hrand(ux, uy, salt)
    if r < 0.04:
        f *= 0.80          # a pit or a dark pore
    elif r > 0.97:
        f *= 1.10          # a dry, pale scale
    return mul(c, f)


def hits(face, s, t, rw, rh, name):
    return False


SAMPLES = []      # (point, normal, rgb): every painted texel, for the preview


def paint():
    im = Image.new('RGBA', (TW, TH), (0, 0, 0, 0))
    for name, idx, mat, origin, size, u, v in CUBES:
        w, h, d = size
        ox, oy, oz = origin
        w, h, d = int(w), int(h), int(d)
        m = world(name)
        faces = {
            'up': ((u + d, v, w, d), lambda s, t: (ox + s, oy, oz + d - t), (0, -1, 0)),
            'down': ((u + d + w, v, w, d), lambda s, t: (ox + s, oy + h, oz + d - t), (0, 1, 0)),
            'west': ((u, v + d, d, h), lambda s, t: (ox, oy + t, oz + d - s), (-1, 0, 0)),
            'north': ((u + d, v + d, w, h), lambda s, t: (ox + s, oy + t, oz), (0, 0, -1)),
            'east': ((u + d + w, v + d, d, h), lambda s, t: (ox + w, oy + t, oz + s), (1, 0, 0)),
            'south': ((u + 2 * d + w, v + d, w, h), lambda s, t: (ox + w - s, oy + t, oz + d), (0, 0, 1)),
        }
        salt = sum(map(ord, name)) + idx * 13
        for face, ((rx, ry, rw, rh), at, nrm) in faces.items():
            n = apply(m, nrm, 0.0)
            for t in range(rh):
                for s in range(rw):
                    p = apply(m, at(s + 0.5, t + 0.5))
                    px, py = rx + s, ry + t
                    if mat == 'skin':
                        c = skin_colour(p, n, px, py, salt)
                        # Skull: the underside is the roof of the mouth.
                        if name == 'head' and idx < 3 and face == 'down':
                            c = MOUTH
                    elif mat == 'wart':
                        c = lerp(WART, WART_LIGHT, 0.35 * vnoise(px, py, 0.8, 0.8, salt) + 0.25 * max(0.0, -n[1]))
                        if hrand(px, py, salt) > 0.8:
                            c = mul(c, 0.8)
                    elif mat == 'nose':
                        up = max(0.0, -n[1])
                        c = lerp(NOSE, NOSE_HI, up * 0.7 + (0.25 if face == 'north' else 0.0))
                        c = mul(c, 1.0 + 0.12 * (vnoise(px, py, 0.8, 0.8, salt) - 0.5))
                        if face == 'down' and rw >= 3 and t == 0 and s in (0, rw - 1):
                            c = NOSTRIL
                        if face == 'north' and s == 0 and t == 0:
                            c = NOSE_HI
                    elif mat == 'eye':
                        if face == 'north':
                            c = GLINT if (s == 0 and t == 0) else EYE
                        else:
                            c = skin_colour(p, n, px, py, salt, (SHADOW, 0.5))
                    elif mat == 'ear':
                        if face == 'north':
                            # The inside of the ear: a pink-brown with darker veins running outward.
                            c = lerp(INNER_EAR, INNER_EAR_DARK, 0.5 * vnoise(px, py, 2.5, 0.6, salt))
                            if s == 0 or t == 0 or t == rh - 1:
                                c = lerp(c, BASE, 0.5)       # the rim is plain leather
                        elif face == 'south':
                            c = skin_colour(p, n, px, py, salt + 3, (SHADOW, 0.1))
                        else:
                            c = skin_colour(p, n, px, py, salt, (SHADOW, 0.15))
                    elif mat == 'jaw':
                        if face == 'north':
                            if t == 0:
                                c = MOUTH
                                if s in (1, rw - 2):
                                    c = TOOTH             # two crooked teeth
                            else:
                                c = skin_colour(p, n, px, py, salt)
                        elif face == 'up':
                            c = TONGUE if 1 <= s <= rw - 2 and t >= 1 else MOUTH
                        else:
                            c = skin_colour(p, n, px, py, salt)
                    elif mat == 'cloth':
                        warp = vnoise(px, py, 0.6, 2.4, salt)
                        weft = vnoise(px, py, 2.4, 0.6, salt + 3)
                        c = lerp(CLOTH_DARK, CLOTH_LIGHT, 0.25 + 0.5 * (warp * 0.5 + weft * 0.5))
                        if face == 'down':
                            c = mul(c, 0.7)
                        if hrand(px, py, salt) < 0.08:
                            c = CLOTH_DARK
                        if face in ('north', 'south', 'east', 'west') and t == 0:
                            c = mul(c, 0.85)
                    elif mat == 'foot':
                        c = skin_colour(p, n, px, py, salt, (SHADOW, 0.2))
                        if face == 'down':
                            c = mul(SHADOW, 0.9 + 0.2 * hrand(px, py, salt))
                        if face == 'north' and t == 0 and s in (0, 1, 2, 3):
                            # toes: a dark line between each
                            c = mul(c, 0.78) if s in (1, 3) else c
                    else:
                        c = (255, 0, 255)
                    col = tuple(int(x) for x in c) + (255,)
                    im.putpixel((px, py), col)
                    SAMPLES.append((p, n, col[:3]))
    return im


# --- preview -------------------------------------------------------------------------------------------------

def render(yaw_deg, pitch_deg, scale=22):
    """Painter's-algorithm render of the textured rest model; returns an RGB image."""
    yaw, pit = math.radians(yaw_deg), math.radians(pitch_deg)
    cy, sy = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pit), math.sin(pit)
    W, H = 22 * scale, 22 * scale
    im = Image.new('RGB', (W, H), (172, 190, 205))
    # a ground line
    pts = []
    for p, n, col in SAMPLES:
        x, y, z = p[0], p[1] - 15.0, p[2]
        x, z = x * cy + z * sy, -x * sy + z * cy                # yaw about the vertical
        y, z = y * cp - z * sp, y * sp + z * cp                 # pitch
        nx, ny, nz = n
        nx, nz = nx * cy + nz * sy, -nx * sy + nz * cy
        ny, nz = ny * cp - nz * sp, ny * sp + nz * cp
        if nz > 0.02:                                           # facing away from the camera at -z
            continue
        pts.append((z, x, y, col))
    pts.sort(key=lambda q: -q[0])
    px = int(scale * 1.12)
    for z, x, y, col in pts:
        X = int(W / 2 + x * scale - px / 2)
        Y = int(H / 2 + y * scale - px / 2)
        for dx in range(px):
            for dy in range(px):
                xx, yy = X + dx, Y + dy
                if 0 <= xx < W and 0 <= yy < H:
                    im.putpixel((xx, yy), col)
    return im


def preview():
    views = [render(0, 8), render(35, 14), render(90, 8), render(180, 8)]
    w, h = views[0].size
    sheet = Image.new('RGB', (w * 2, h * 2))
    for i, v in enumerate(views):
        sheet.paste(v, ((i % 2) * w, (i // 2) * h))
    sheet.save(PREVIEW)


# --- the inventory face (never seen in play: the placeholder item draws nothing in the hand) ------------------

def item_icon():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    skin, hi, sh, wart = BASE, HILITE, SHADOW, WART
    # potato head
    for y in range(2, 13):
        half = {2: 3, 3: 5, 4: 6, 5: 6, 6: 6, 7: 6, 8: 6, 9: 5, 10: 5, 11: 4, 12: 3}[y]
        for x in range(8 - half, 8 + half):
            c = lerp(hi, skin, (y - 2) / 5.0) if y < 7 else lerp(skin, sh, (y - 7) / 6.0)
            im.putpixel((x, y), tuple(int(v) for v in c) + (255,))
    for x, y in ((1, 6), (1, 7), (14, 6), (14, 7), (0, 5), (15, 5)):          # ears
        im.putpixel((x, y), tuple(int(v) for v in INNER_EAR) + (255,))
    for x, y in ((6, 6), (9, 6)):
        im.putpixel((x, y), EYE + (255,))
    im.putpixel((6, 5), GLINT + (255,))
    for x, y in ((7, 8), (8, 8), (7, 9), (8, 9)):
        im.putpixel((x, y), tuple(int(v) for v in NOSE) + (255,))
    for x in range(5, 11):
        im.putpixel((x, 11), MOUTH + (255,))
    im.putpixel((6, 11), TOOTH + (255,))
    im.putpixel((9, 11), TOOTH + (255,))
    for x, y in ((10, 4), (5, 9)):
        im.putpixel((x, y), tuple(int(v) for v in wart) + (255,))
    for x in range(5, 11):
        im.putpixel((x, 13), tuple(int(v) for v in CLOTH) + (255,))
        im.putpixel((x, 14), tuple(int(v) for v in CLOTH_DARK) + (255,))
    return im


# --- Java ------------------------------------------------------------------------------------------------------

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
    out.append(' * GENERATED by tools/gnome/build_gnome.py from the same description that paints the texture.')
    out.append(' * Do not edit by hand: change the script and run it again.')
    out.append(' */')
    out.append('public final class GnomeGeometry {')
    out.append('    private GnomeGeometry() {\n    }\n')
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
    with open(JAVA, 'w', newline='\n') as fh:
        fh.write(java())
    preview()
    item_icon().save(ITEM_PNG)
    Image.new('RGBA', (16, 16), (0, 0, 0, 0)).save(BLANK_PNG)
    top = min(GROUND_Y - apply(world('head'), (0, -8, 0))[1], 99)
    print('crown knob top at height %.1f' % top)
    print('wrote', os.path.relpath(PNG, ROOT), 'and', os.path.relpath(JAVA, ROOT))
