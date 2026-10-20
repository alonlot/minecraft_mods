"""The Great Hall door leaf used by the hogwartsmagic:memory_door Pensieve actor.

    python build_scripts/generate_memory_door_texture.py

64 x 192 sheet; MemoryDoorRenderer lays the faces out (keep the RECTS there in step with this):
    front (+z)   u 0..32   v 0..96     back (-z)  u 32..64  v 0..96
    free edge    u 0..6    v 96..192   hinge edge u 6..12   v 96..192
    top          u 16..48  v 96..102   bottom     u 16..48  v 102..108
Dark oak vertical planks with grain, a darker 2 px border, three wrought-iron strap hinges that run
out from the hinge edge, studs, and an iron ring handle near the free edge.  The back is the front
mirrored, so the hinge straps sit on the same jamb whichever side you see.
Uses Pillow when present, otherwise writes the PNG with zlib.
"""

import math
import os
import struct
import zlib

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources',
                   'assets', 'hogwartsmagic', 'textures', 'entity', 'memory_door.png')

W, H = 64, 192
FW, FH = 32, 96
PLANK = 8
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


def planks(cv, ox, oy, w, h, seed):
    """Vertical planks with long grain streaks, knots and a seam between boards."""
    for x in range(w):
        board = x // PLANK
        base = OAK[(board + seed) % len(OAK)]
        for y in range(h):
            # grain: slow sine wobble along the plank plus per-column streak noise
            streak = _hash(x, 0, seed) - 0.5
            wob = math.sin(y * 0.11 + board * 2.3 + seed) * 0.5 + math.sin(y * 0.37 + x * 0.9) * 0.25
            f = 1.0 + streak * 0.30 + wob * 0.07 + (_hash(x, y // 3, seed + 5) - 0.5) * 0.10
            c = shade(base, f)
            if x % PLANK == 0:
                c = SEAM
            elif x % PLANK == 1:
                c = shade(c, 0.78)
            elif x % PLANK == PLANK - 1:
                c = shade(c, 1.10)
            cv.put(ox + x, oy + y, c)
    # a few knots
    for k in range(3):
        kx = int(_hash(k, 1, seed) * (w - 6)) + 3
        ky = int(_hash(k, 2, seed) * (h - 40)) + 20
        for dy in range(-2, 3):
            for dx in range(-1, 2):
                if (dx * dx) * 2 + dy * dy <= 5 and kx % PLANK not in (0, 1):
                    cv.put(ox + kx + dx, oy + ky + dy, shade(OAK[3], 0.55 if abs(dy) < 2 else 0.75))


def border(cv, ox, oy, w, h):
    for i in range(2):
        f = 0.8 if i == 0 else 1.0
        for x in range(w):
            cv.put(ox + x, oy + i, shade(BORDER, f))
            cv.put(ox + x, oy + h - 1 - i, shade(BORDER, f))
        for y in range(h):
            cv.put(ox + i, oy + y, shade(BORDER, f))
            cv.put(ox + w - 1 - i, oy + y, shade(BORDER, f))


def stud(cv, x, y):
    cv.put(x, y, IRON_HI)
    cv.put(x + 1, y, IRON)
    cv.put(x, y + 1, IRON)
    cv.put(x + 1, y + 1, IRON_LO)


def strap(cv, ox, y, length, hinge_left):
    """A wrought-iron strap hinge: a bar from the hinge edge that ends in a pointed tip, with studs."""
    h = 5
    for i in range(length):
        x = ox + i if hinge_left else ox + FW - 1 - i
        taper = max(0, i - (length - 5))          # last few pixels narrow to a point
        for j in range(h):
            if taper and (j < taper // 2 + (1 if taper > 2 else 0) or j >= h - taper // 2 - (1 if taper > 2 else 0)):
                continue
            c = IRON_HI if j == 0 else IRON_LO if j == h - 1 else IRON
            if _hash(i, j, y) > 0.9:
                c = shade(c, 1.25)
            cv.put(x, y + j, c)
    # the hinge knuckle: a taller block at the jamb
    kx = ox if hinge_left else ox + FW - 4
    cv.rect(kx, y - 2, kx + 4, y + h + 2, IRON)
    cv.rect(kx, y - 2, kx + 4, y - 1, IRON_HI)
    cv.rect(kx, y + h + 1, kx + 4, y + h + 2, IRON_LO)
    # studs along the strap
    for sx in (6, 11, 16):
        if sx + 2 < length - 4:
            stud(cv, (ox + sx) if hinge_left else (ox + FW - 1 - sx - 1), y + 1)


def ring_handle(cv, cx, cy):
    # backplate
    for dy in range(-5, 6):
        for dx in range(-3, 4):
            if abs(dx) + abs(dy) * 0.6 <= 5.2:
                c = IRON_HI if dy == -5 or dx == -3 else IRON_LO if dy == 5 or dx == 3 else IRON
                cv.put(cx + dx, cy + dy, c)
    stud(cv, cx - 1, cy - 4)
    # the ring hangs below its boss
    r = 5
    for a in range(0, 360, 6):
        x = cx + round(math.cos(math.radians(a)) * r)
        y = cy + 4 + round(math.sin(math.radians(a)) * r)
        lit = math.cos(math.radians(a) + 2.4)
        for t in (0, 1):
            cv.put(x + (t if lit < 0 else 0), y, IRON_HI if lit > 0.5 else IRON_LO if lit < -0.5 else IRON)


def face(cv, ox, oy, mirror, seed):
    planks(cv, ox, oy, FW, FH, seed)
    for y in (9, 45, 81):
        strap(cv, ox, oy + y, 20, not mirror)
    border(cv, ox, oy, FW, FH)
    cx = ox + (7 if mirror else FW - 8)
    ring_handle(cv, cx, oy + 58)


def build():
    cv = Canvas()
    face(cv, 0, 0, False, 1)
    face(cv, FW, 0, True, 3)
    # edges: plain oak end boards with the border darkening both rims
    for ox, oy, w, h, s in ((0, 96, 6, 96, 7), (6, 96, 6, 96, 9)):
        planks(cv, ox, oy, w, h, s)
        border(cv, ox, oy, w, h)
    for ox, oy, w, h, s in ((16, 96, 32, 6, 11), (16, 102, 32, 6, 13)):
        planks(cv, ox, oy, w, h, s)
        border(cv, ox, oy, w, h)
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
