"""The Great Hall door leaf used by the hogwartsmagic:memory_door and memory_door_mirrored Pensieve actors.

    python build_scripts/generate_memory_door_texture.py

128 x 384 sheet (2x); MemoryDoorRenderer lays the faces out (keep the RECTS there in step with this):
    front (+z)   u 0..64    v 0..192    back (-z)    u 64..128  v 0..192
    free edge    u 0..12    v 192..384  hinge edge   u 12..24   v 192..384
    top          u 32..96   v 192..204  bottom       u 32..96   v 204..216
    iron swatch  u 100..116 v 192..208  (the 3D ring and studs are textured from this)
Dark oak vertical planks with fine grain and ringed knots, a darker 4 px border, three wrought-iron strap
hinges that run out from the hinge edge, studs, and a painted iron plate where the 3D ring hangs.  The back
is the front mirrored, so the hinge straps sit on the same jamb whichever side you see.  The mirrored leaf
reuses the same sheet (its faces keep their corners' texture coordinates, so the straps stay at the hinge).
The ring pivot (RING_X across the leaf from the hinge, RING_Y up from the floor, both in leaf units) must
match MemoryDoorRenderer.  Uses Pillow when present, otherwise writes the PNG with zlib.
"""

import math
import os
import struct
import zlib

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources',
                   'assets', 'hogwartsmagic', 'textures', 'entity', 'memory_door.png')

W, H = 128, 384
FW, FH = 64, 192
PLANK = 16
RING_X, RING_Y = 0.84, 1.35          # stud position on the leaf, as a fraction of width / metres up
OAK = [(58, 38, 24), (52, 34, 21), (64, 42, 27), (48, 31, 19)]
SEAM = (24, 15, 9)
BORDER = (30, 19, 12)
IRON = (46, 48, 54)
IRON_HI = (96, 100, 110)
IRON_LO = (22, 23, 27)


def _hash(a, b, salt=0):
    h = (a * 73856093) ^ (b * 19349663) ^ (salt * 83492791)
    h &= 0x7FFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0x7FFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


class Canvas:
    def __init__(self):
        self.px = [[OAK[0] + (255,) for _ in range(W)] for _ in range(H)]

    def put(self, x, y, c):
        if 0 <= x < W and 0 <= y < H:
            self.px[y][x] = tuple(c) + (255,)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.put(x, y, c)


def planks(cv, ox, oy, w, h, seed, knots=False):
    """Vertical planks with fine grain streaks, a seam between boards and (optionally) ringed knots."""
    for x in range(w):
        board = x // PLANK
        base = OAK[(board + seed) % len(OAK)]
        for y in range(h):
            # grain: fibres a pixel wide that run in short dashes, a slow wobble along the board, fine speckle
            fibre = _hash(x, y // (5 + x % 4), seed) - 0.5
            streak = _hash(x, 0, seed) - 0.5
            wob = math.sin(y * 0.045 + board * 2.3 + seed) * 0.5 + math.sin(y * 0.19 + x * 0.45) * 0.3
            f = 1.0 + streak * 0.20 + fibre * 0.16 + wob * 0.06 + (_hash(x, y, seed + 5) - 0.5) * 0.07
            c = shade(base, f)
            if x % PLANK in (0, 1):
                c = SEAM
            elif x % PLANK == 2:
                c = shade(c, 0.80)
            elif x % PLANK == PLANK - 1:
                c = shade(c, 1.10)
            cv.put(ox + x, oy + y, c)
    if knots:
        for k in range(3):
            kx = int(_hash(k, 1, seed) * (w - 16)) + 8
            ky = int(_hash(k, 2, seed) * (h - 80)) + 40
            if kx % PLANK < 5 or kx % PLANK > 10:
                kx = kx - kx % PLANK + 8
            knot(cv, ox + kx, oy + ky, seed + k)


def knot(cv, cx, cy, seed):
    """A knot as swirled grain rings, never darker than the oak around it by more than ~28%: it reads as wood, not a hole."""
    for dy in range(-12, 13):
        for dx in range(-6, 7):
            d = math.hypot(dx / 3.4, dy / 7.0)
            if d > 1.45:
                continue
            old = cv.px[cy + dy][cx + dx][:3] if 0 <= cy + dy < H and 0 <= cx + dx < W else None
            if old is None:
                continue
            if d <= 1.0:
                rings = 0.5 + 0.5 * math.sin(d * 11.0 + _hash(seed, 3) * 3)
                f = 0.74 + 0.16 * rings - 0.06 * (1.0 - d)
            else:
                f = 0.93 + 0.07 * (d - 1.0) / 0.45    # a soft halo where the grain bends round the knot
            cv.put(cx + dx, cy + dy, shade(old, f))


def border(cv, ox, oy, w, h):
    for i in range(4):
        f = 0.8 if i < 2 else 1.0
        for x in range(w):
            cv.put(ox + x, oy + i, shade(BORDER, f))
            cv.put(ox + x, oy + h - 1 - i, shade(BORDER, f))
        for y in range(h):
            cv.put(ox + i, oy + y, shade(BORDER, f))
            cv.put(ox + w - 1 - i, oy + y, shade(BORDER, f))


def stud(cv, x, y):
    """A round 4 x 4 rivet head with a highlight at the top left."""
    for dy in range(4):
        for dx in range(4):
            if (dx in (0, 3)) and (dy in (0, 3)):
                continue
            c = IRON_HI if dx + dy <= 2 else IRON_LO if dx + dy >= 5 else IRON
            cv.put(x + dx, y + dy, c)


def strap(cv, ox, y, length, hinge_left):
    """A wrought-iron strap hinge: a bar from the hinge edge that ends in a pointed tip, with studs."""
    h = 10
    for i in range(length):
        x = ox + i if hinge_left else ox + FW - 1 - i
        taper = max(0, i - (length - 10))         # last few pixels narrow to a point
        for j in range(h):
            if taper and (j < taper // 2 + (1 if taper > 2 else 0) or j >= h - taper // 2 - (1 if taper > 2 else 0)):
                continue
            c = IRON_HI if j < 2 else IRON_LO if j >= h - 2 else IRON
            if _hash(i, j, y) > 0.88:
                c = shade(c, 1.25)
            cv.put(x, y + j, c)
    # the hinge knuckle: a taller block at the jamb
    kx = ox if hinge_left else ox + FW - 8
    cv.rect(kx, y - 4, kx + 8, y + h + 4, IRON)
    cv.rect(kx, y - 4, kx + 8, y - 2, IRON_HI)
    cv.rect(kx, y + h + 2, kx + 8, y + h + 4, IRON_LO)
    # studs along the strap
    for sx in (12, 22, 32):
        if sx + 4 < length - 8:
            stud(cv, (ox + sx) if hinge_left else (ox + FW - 1 - sx - 3), y + 3)


def ring_plate(cv, cx, cy):
    """The iron plate the 3D ring's stud sits on (the ring itself is real geometry)."""
    for dy in range(-12, 13):
        for dx in range(-7, 8):
            if (dx / 7.0) ** 2 + (dy / 12.0) ** 2 <= 1.0:
                edge = (dx / 7.0) ** 2 + (dy / 12.0) ** 2 > 0.72
                c = IRON_LO if edge else IRON_HI if (dx < -3 or dy < -8) else IRON
                cv.put(cx + dx, cy + dy, c)
    stud(cv, cx - 2, cy - 2)


def iron_swatch(cv, ox, oy, w, h):
    for y in range(h):
        for x in range(w):
            c = shade(IRON, 0.9 + (_hash(x, y, 21) - 0.5) * 0.35 + (0.12 if (x + y) < 6 else 0.0))
            cv.put(ox + x, oy + y, c)


def face(cv, ox, oy, mirror, seed):
    planks(cv, ox, oy, FW, FH, seed, knots=True)
    for y in (18, 90, 162):
        strap(cv, ox, oy + y - 5, 40, not mirror)
    border(cv, ox, oy, FW, FH)
    px = RING_X if not mirror else 1.0 - RING_X
    ring_plate(cv, ox + int(round(px * FW)), oy + int(round((1.0 - RING_Y / 3.0) * FH)))


def build():
    cv = Canvas()
    face(cv, 0, 0, False, 1)
    face(cv, FW, 0, True, 3)
    # edges: plain oak end boards with the border darkening both rims
    for ox, oy, w, h, s in ((0, 192, 12, 192, 7), (12, 192, 12, 192, 9)):
        planks(cv, ox, oy, w, h, s)
        border(cv, ox, oy, w, h)
    for ox, oy, w, h, s in ((32, 192, 64, 12, 11), (32, 204, 64, 12, 13)):
        planks(cv, ox, oy, w, h, s)
        border(cv, ox, oy, w, h)
    iron_swatch(cv, 100, 192, 16, 16)
    return cv


def write_png(path, px):
    try:
        from PIL import Image
        img = Image.new('RGBA', (W, H))
        img.putdata([c for row in px for c in row])
        img.save(path)
        return
    except ImportError:
        pass
    raw = b''.join(b'\x00' + bytes(v for c in row for v in c) for row in px)

    def chunk(tag, data):
        body = tag + data
        return struct.pack('>I', len(data)) + body + struct.pack('>I', zlib.crc32(body) & 0xFFFFFFFF)

    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', W, H, 8, 6, 0, 0, 0))
                + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


if __name__ == '__main__':
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    write_png(OUT, build().px)
    print('wrote', os.path.normpath(OUT))
