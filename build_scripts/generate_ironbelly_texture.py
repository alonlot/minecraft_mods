"""Paints the Ukrainian Ironbelly's hide for its own model (UkrainianIronbellyModel, 256x256).

The Ironbelly used to be the Horntail's texture desaturated, on the Horntail's model. It has its own
sculpt now, so it gets its own hide, painted box by box from the same UV table the Java model uses:

  * cold iron-grey scales, darker along the back, paler down the flanks, with old pale scars
  * the iron belly it is named for: a riveted gunmetal keel, a little rust in the seams
  * dull bone horns, frill, fangs and claws
  * wings torn and holed - real transparency, cut identically into both faces of each sail so the
    holes go through - which is how a dragon kept underground for decades reads as one that
    cannot fly
  * iron collar and shackle, rusted
  * small milky eyes, and a dim cold glow layer for them (it is nearly blind; the Horntail's
    burning amber would be wrong)

Run from the repo root:  python build_scripts/generate_ironbelly_texture.py
Writes:
  textures/entity/ukrainian_ironbelly.png
  textures/entity/ukrainian_ironbelly_eyes.png
"""
import math
import os

from PIL import Image

SIZE = 256
OUT_DIR = os.path.join('src', 'main', 'resources', 'assets', 'hogwartsmagic', 'textures', 'entity')

# --- the UV table: must match UkrainianIronbellyModel -------------------------------------------
# name: (u, v, dx, dy, dz, material)
BOXES = {
    'torso':         (0, 0, 16, 15, 30, 'hide'),
    'belly':         (92, 0, 12, 3, 26, 'keel'),
    'hump':          (88, 72, 18, 6, 15, 'hide'),
    'pelvis':        (190, 45, 14, 11, 11, 'hide'),
    'outer_sail':    (0, 45, 24, 1, 26, 'sail'),
    'inner_sail':    (154, 72, 11, 1, 20, 'sail'),
    'shoulder_sail': (186, 94, 13, 1, 14, 'sail'),
    'neck1':         (168, 0, 10, 10, 14, 'hide'),
    'neck2':         (0, 72, 8, 8, 14, 'hide'),
    'skull':         (0, 94, 11, 8, 13, 'head'),
    'jaw':           (78, 94, 8, 3, 14, 'head'),
    'snout':         (40, 115, 8, 5, 8, 'head'),
    'brow':          (172, 115, 12, 3, 6, 'head'),
    'tail1':         (100, 45, 10, 10, 14, 'hide'),
    'tail2':         (44, 72, 8, 8, 14, 'hide'),
    'tail3':         (216, 0, 6, 6, 14, 'hide'),
    'tail4':         (216, 72, 5, 5, 14, 'hide'),
    'club':          (72, 115, 8, 6, 7, 'hide'),
    'thigh':         (148, 45, 10, 12, 11, 'hide'),
    'shank':         (48, 94, 7, 10, 8, 'hide'),
    'foot':          (0, 115, 9, 3, 11, 'hide'),
    'humerus':       (102, 115, 13, 6, 6, 'hide'),
    'forearm':       (140, 115, 11, 5, 5, 'hide'),
    'finger':        (0, 129, 24, 3, 3, 'hide'),
    'plate':         (208, 115, 4, 3, 5, 'plate'),
    'eye':           (240, 45, 1, 2, 3, 'eye'),
}
# Flat regions shared by many small parts.
REGIONS = {
    'iron':  (122, 94, 40, 16),
    'ivory': (162, 94, 24, 16),
}

HIDE_BACK = (62, 66, 72)
HIDE_FLANK = (104, 108, 112)
HIDE_LOW = (146, 146, 140)
SCAR = (182, 176, 164)
KEEL = (52, 54, 58)
KEEL_SEAM = (26, 27, 30)
KEEL_RIVET = (124, 126, 128)
RUST = (112, 66, 40)
MEMBRANE = (78, 70, 66)
MEMBRANE_DARK = (54, 48, 46)
VEIN = (40, 35, 34)
IVORY = (198, 188, 156)
IVORY_DARK = (150, 138, 108)
IRON = (58, 57, 56)
IRON_HIGH = (96, 94, 92)
EYE = (196, 204, 208)
EYE_PUPIL = (150, 160, 168)


def noise(x, y, salt=0):
    h = (x * 73856093) ^ (y * 19349663) ^ (salt * 83492791)
    h &= 0x7FFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0x7FFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c)


def faces(u, v, dx, dy, dz):
    """The six face rectangles of a box, as Minecraft lays them out: name -> (x, y, w, h)."""
    return {
        'top':    (u + dz, v, dx, dz),
        'bottom': (u + dz + dx, v, dx, dz),
        'east':   (u, v + dz, dz, dy),
        'front':  (u + dz, v + dz, dx, dy),
        'west':   (u + dz + dx, v + dz, dz, dy),
        'back':   (u + dz + dx + dz, v + dz, dx, dy),
    }


def scale_tex(x, y, base_dark, base_light, salt):
    """Overlapping rows of scales: a darker rim at the bottom of each, a sheen at the top."""
    row = y // 2
    ox = x + (1 if row % 2 else 0)
    in_scale = ox % 3
    t = 0.5 + 0.35 * (noise(ox // 3, row, salt) - 0.5)
    c = lerp(base_dark, base_light, t)
    if y % 2 == 1 and in_scale != 1:
        c = shade(c, 0.82)
    elif y % 2 == 0 and in_scale == 1:
        c = shade(c, 1.10)
    return c


def paint_hide(px, x0, y0, w, h, kind, gx, salt, head=False):
    """kind: 'top' (back), 'side', 'bottom' (underside). gx is a global offset for noise."""
    for j in range(h):
        for i in range(w):
            x, y = x0 + i, y0 + j
            if kind == 'top':
                dark, light = shade(HIDE_BACK, 0.85), HIDE_BACK
            elif kind == 'bottom':
                dark, light = shade(HIDE_LOW, 0.85), HIDE_LOW
            else:
                f = j / max(1, h - 1)
                dark = lerp(HIDE_BACK, HIDE_FLANK, f * 1.4)
                light = lerp(HIDE_FLANK, HIDE_LOW, (f - 0.5) * 2.0)
            c = scale_tex(x, y, dark, light, salt)
            # Old scars: pale, thin, mostly running across the body.
            if noise((x + gx) // 4, y, 11 + salt) > 0.965 and kind != 'bottom':
                c = lerp(c, SCAR, 0.75)
            elif noise(x, y, 12 + salt) < 0.06:
                c = shade(c, 0.78)
            if head and kind != 'bottom':
                c = shade(c, 0.94)
            px[x, y] = c + (255,)


def paint_keel(px, x0, y0, w, h, salt):
    """Riveted plates, seams every three rows, a little rust where water has run."""
    for j in range(h):
        for i in range(w):
            x, y = x0 + i, y0 + j
            c = lerp(KEEL, shade(KEEL, 1.25), noise(i // 3, j // 3, salt))
            if j % 3 == 2 or i % 6 == 5:
                c = KEEL_SEAM
            elif j % 3 == 0 and i % 6 == 1:
                c = KEEL_RIVET
            if noise(i, j // 2, salt + 3) > 0.92:
                c = lerp(c, RUST, 0.6)
            px[x, y] = c + (255,)


def paint_flat(px, x0, y0, w, h, base, high, salt, rust=0.0):
    for j in range(h):
        for i in range(w):
            x, y = x0 + i, y0 + j
            c = lerp(base, high, noise(i, j, salt) * 0.8)
            if rust and noise(i // 2, j, salt + 1) > 1.0 - rust:
                c = lerp(c, RUST, 0.7)
            px[x, y] = c + (255,)


def paint_ivory(px, x0, y0, w, h):
    for j in range(h):
        for i in range(w):
            # Grain along the length, darker toward the root.
            c = lerp(IVORY_DARK, IVORY, 0.35 + 0.65 * (1.0 - j / max(1, h - 1)))
            if noise(i, j // 3, 51) > 0.85:
                c = shade(c, 0.88)
            px[x0 + i, y0 + j] = c + (255,)


def tear_mask(ix, r, dx, dz, salt):
    """True where a sail is torn away. r is rows from the trailing edge (0 = the edge)."""
    # Ragged trailing edge.
    if r == 0 and noise(ix, 0, salt) < 0.35:
        return True
    # Rents torn in from the trailing edge: few, uneven, slanting, wide at the edge and closing
    # to a point. Evenly spaced narrow notches read as a comb, not as damage.
    notches = max(1, dx // 10)
    for k in range(notches):
        c = (k + 0.3 + 0.4 * noise(k, 1, salt)) * dx / notches
        depth = dz * (0.3 + 0.4 * noise(k, 2, salt))
        width = 1.5 + 2.5 * noise(k, 3, salt)
        lean = (noise(k, 8, salt) - 0.5) * 0.8
        if r < depth:
            half = width * (1.0 - r / depth) ** 0.7 + 0.6 * (noise(ix, r, salt + k) - 0.5)
            if abs(ix + 0.5 - (c + lean * r)) < half:
                return True
    # A hole or two through the middle of the membrane.
    if dx >= 11:
        for k in range(2 if dx > 16 else 1):
            hx = dx * (0.3 + 0.45 * noise(k, 4, salt))
            hr = dz * (0.35 + 0.3 * noise(k, 5, salt))
            rx = 1.2 + 1.2 * noise(k, 6, salt)
            ry = 1.0 + 1.5 * noise(k, 7, salt)
            if ((ix + 0.5 - hx) / rx) ** 2 + ((r + 0.5 - hr) / ry) ** 2 < 1.0:
                return True
    return False


def paint_sail(px, u, v, dx, dy, dz, salt):
    f = faces(u, v, dx, dy, dz)
    veins = [int(dx * t) for t in (0.18, 0.42, 0.66, 0.88)]
    for name in ('top', 'bottom'):
        x0, y0, w, h = f[name]
        for row in range(h):
            r = row                     # face row 0 is the trailing edge (maxZ)
            for ix in range(w):
                if tear_mask(ix, r, dx, dz, salt):
                    px[x0 + ix, y0 + row] = (0, 0, 0, 0)
                    continue
                c = lerp(MEMBRANE_DARK, MEMBRANE, 0.4 + 0.6 * noise(ix // 2, row // 2, salt + 9))
                lean = (dz - row) * 0.12
                if any(abs(ix - (vx + lean)) < 0.6 for vx in veins):
                    c = VEIN
                elif noise(ix, row, salt + 10) > 0.95:
                    c = shade(c, 1.25)       # thin patches the light comes through
                if name == 'bottom':
                    c = shade(c, 0.9)
                px[x0 + ix, y0 + row] = c + (255,)
    # The 1-px edges of a membrane: transparent, so a torn edge does not show a solid rim.
    for name in ('east', 'front', 'west', 'back'):
        x0, y0, w, h = f[name]
        for j in range(h):
            for i in range(w):
                px[x0 + i, y0 + j] = (0, 0, 0, 0)


def paint_eye(px, u, v, dx, dy, dz):
    f = faces(u, v, dx, dy, dz)
    for name, (x0, y0, w, h) in f.items():
        for j in range(h):
            for i in range(w):
                c = EYE
                if name in ('east', 'west') and i == w // 2:
                    c = EYE_PUPIL
                px[x0 + i, y0 + j] = c + (255,)


def build_skin():
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    for salt, (name, (u, v, dx, dy, dz, mat)) in enumerate(BOXES.items()):
        f = faces(u, v, dx, dy, dz)
        if mat == 'sail':
            paint_sail(px, u, v, dx, dy, dz, 300 + salt)
        elif mat == 'eye':
            paint_eye(px, u, v, dx, dy, dz)
        elif mat == 'keel':
            for face in f.values():
                paint_keel(px, *face, salt)
        elif mat == 'plate':
            for face in f.values():
                paint_flat(px, *face, shade(HIDE_BACK, 0.8), HIDE_FLANK, salt)
        else:
            head = mat == 'head'
            paint_hide(px, *f['top'], 'top', u, salt, head)
            paint_hide(px, *f['bottom'], 'bottom', u, salt, head)
            for side in ('east', 'front', 'west', 'back'):
                paint_hide(px, *f[side], 'side', u, salt, head)
    paint_flat(px, *REGIONS['iron'], IRON, IRON_HIGH, 77, rust=0.18)
    paint_ivory(px, *REGIONS['ivory'])
    return img


def build_eye_glow():
    """Only the eyes, dim and cold: an additive layer, so this is how much they glow in the dark."""
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    u, v, dx, dy, dz, _ = BOXES['eye']
    for x0, y0, w, h in faces(u, v, dx, dy, dz).values():
        for j in range(h):
            for i in range(w):
                px[x0 + i, y0 + j] = (88, 100, 112, 255)
    return img


def check_layout():
    """No two boxes may share texels: a sail and a flank painted into one place is a mess."""
    owner = {}
    rects = []
    for name, (u, v, dx, dy, dz, _) in BOXES.items():
        rects.append((name, u, v, 2 * (dx + dz), dz + dy))
    for name, (u, v, w, h) in REGIONS.items():
        rects.append((name, u, v, w, h))
    for name, u, v, w, h in rects:
        if u + w > SIZE or v + h > SIZE:
            raise SystemExit('%s runs off the sheet' % name)
        for x in range(u, u + w):
            for y in range(v, v + h):
                if (x, y) in owner:
                    raise SystemExit('%s overlaps %s at %d,%d' % (name, owner[(x, y)], x, y))
                owner[(x, y)] = name


def main():
    check_layout()
    os.makedirs(OUT_DIR, exist_ok=True)
    skin = build_skin()
    skin.save(os.path.join(OUT_DIR, 'ukrainian_ironbelly.png'))
    build_eye_glow().save(os.path.join(OUT_DIR, 'ukrainian_ironbelly_eyes.png'))
    # How much of each sail is left: a wing with nothing left is not torn, it is gone.
    for name in ('outer_sail', 'inner_sail', 'shoulder_sail'):
        u, v, dx, dy, dz, _ = BOXES[name]
        x0, y0, w, h = faces(u, v, dx, dy, dz)['top']
        kept = sum(1 for j in range(h) for i in range(w) if skin.getpixel((x0 + i, y0 + j))[3] > 0)
        print('%-14s %3d%% membrane left' % (name, 100 * kept // (w * h)))
    print('wrote ukrainian_ironbelly.png and ukrainian_ironbelly_eyes.png')


if __name__ == '__main__':
    main()
