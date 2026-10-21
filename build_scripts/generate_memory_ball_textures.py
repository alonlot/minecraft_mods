"""Textures for the memory balls (client/MemoryBallRenderer) and the two display-only items (Remembrall, whistle).

Entity textures are 64 x 32 equirectangular maps of the unit sphere (u = longitude, v = latitude from the top pole):
  memory_quaffle.png          scuffed red leather: a seam along the equator and two meridian seams, stitch marks,
                              pale scuffs and darker creases.
  memory_bludger.png          iron-black: a cold blue-black grain, dents (a dark rim and a lit lip), a few rivet-like
                              scratches. The renderer also dents the mesh (MemoryBall.dent).
  memory_remembrall.png       clear glass, mostly transparent with a faint blue tint and white window glints.
  memory_remembrall_smoke.png a 16 x 16 soft white puff for the smoke inside it.
Items (16 x 16): memory_remembrall.png (the glass ball with its smoke: the particle and GUI sprite), the item model's
memory_remembrall_glass.png and memory_remembrall_smoke.png, and whistle.png (a brass whistle).
Run from the repo root: python build_scripts/generate_memory_ball_textures.py
"""
import math
import random
from pathlib import Path

from PIL import Image

ASSETS = Path("src/main/resources/assets/hogwartsmagic/textures")
W, H = 64, 32


def clamp(v):
    return max(0, min(255, int(v)))


def direction(x, y):
    """The unit-sphere direction of texel (x, y) of an equirectangular map."""
    theta = 2 * math.pi * (x + 0.5) / W
    phi = math.pi * (y + 0.5) / H
    s = math.sin(phi)
    return s * math.cos(theta), math.cos(phi), s * math.sin(theta)


def quaffle():
    rnd = random.Random(11)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    px = img.load()
    scuffs = [(rnd.uniform(-1, 1), rnd.uniform(-1, 1), rnd.uniform(-1, 1), rnd.uniform(0.012, 0.05)) for _ in range(26)]
    for y in range(H):
        for x in range(W):
            d = direction(x, y)
            n = rnd.randint(-9, 9)
            r, g, b = 150 + n, 24 + n // 3, 20 + n // 3
            # Creases: low-frequency darker bands.
            crease = math.sin(d[0] * 9 + d[1] * 5) * math.sin(d[2] * 7 - d[1] * 3)
            if crease > 0.82:
                r, g, b = r - 28, g - 6, b - 6
            for sx, sy, sz, size in scuffs:
                l = math.sqrt(sx * sx + sy * sy + sz * sz) or 1
                dot = (d[0] * sx + d[1] * sy + d[2] * sz) / l
                if 1 - dot < size:
                    k = 1 - (1 - dot) / size
                    r, g, b = r + 62 * k, g + 40 * k, b + 34 * k
            px[x, y] = (clamp(r), clamp(g), clamp(b), 255)
    seam = (46, 10, 8, 255)
    stitch = (205, 168, 130, 255)
    # An equator seam and meridian seams at u 0 and 0.5, with stitches either side.
    for x in range(W):
        for y in (H // 2 - 1, H // 2):
            px[x, y] = seam
        if x % 3 != 2:
            px[x, H // 2 - 3] = stitch
            px[x, H // 2 + 2] = stitch
    for mx in (0, W // 2):
        for y in range(H):
            for dx in (0, 1) if mx == 0 else (-1, 0):
                px[(mx + dx) % W, y] = seam
            if y % 3 != 2:
                px[(mx + 3) % W, y] = stitch
                px[(mx - 3) % W, y] = stitch
    return img


def bludger():
    rnd = random.Random(23)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    px = img.load()
    dents = [(0.62, 0.55, 0.56), (-0.71, 0.10, 0.69), (0.20, -0.85, -0.48), (-0.30, 0.50, -0.81), (0.90, -0.30, -0.31),
             (-0.55, -0.35, 0.76)]
    for y in range(H):
        for x in range(W):
            d = direction(x, y)
            n = rnd.randint(-7, 7)
            base = 34 + n
            r, g, b = base, base + 2, base + 8
            for dx, dy, dz in dents:
                l = math.sqrt(dx * dx + dy * dy + dz * dz)
                dot = (d[0] * dx + d[1] * dy + d[2] * dz) / l
                ang = math.acos(max(-1, min(1, dot)))
                if ang < 0.2:
                    k = 1 - ang / 0.2
                    r, g, b = r - 22 * k, g - 22 * k, b - 20 * k      # the dent's floor, darker
                elif ang < 0.27:
                    k = 1 - (ang - 0.2) / 0.07
                    r, g, b = r + 38 * k, g + 40 * k, b + 46 * k      # the lit lip
            px[x, y] = (clamp(r), clamp(g), clamp(b), 255)
    for _ in range(40):                                               # scratches
        x, y = rnd.randrange(W), rnd.randrange(H)
        for i in range(rnd.randint(2, 6)):
            px[(x + i) % W, min(H - 1, y + i // 2)] = (96, 100, 112, 255)
    return img


def glass():
    rnd = random.Random(5)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    for y in range(H):
        for x in range(W):
            d = direction(x, y)
            a = 54 + rnd.randint(-4, 4)
            col = (205, 225, 245)
            # A bright window glint high on the front-left and a long edge glint, so it reads as polished glass.
            for gx, gy, gz, size, alpha in ((0.55, 0.62, 0.56, 0.05, 210), (-0.4, 0.2, 0.89, 0.014, 150)):
                dot = d[0] * gx + d[1] * gy + d[2] * gz
                if 1 - dot < size:
                    k = 1 - (1 - dot) / size
                    a = max(a, int(alpha * k))
                    col = (255, 255, 255)
            px[x, y] = (col[0], col[1], col[2], clamp(a))
    return img


def smoke():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5) / 8
            a = max(0.0, 1 - r) ** 1.4
            px[x, y] = (248, 250, 255, clamp(255 * a))
    return img


def item_glass():
    """The Remembrall item model's glass cubes: pale blue, mostly clear, a bright glint corner."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            a = 62
            if x < 3 and y < 3 or (x, y) in ((3, 1), (1, 3)):
                a = 200
            if x in (0, 15) or y in (0, 15):
                a = 96
            px[x, y] = (210, 228, 246, a)
    return img


def item_remembrall():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if r < 6.3:
                shade = 1 - (r / 6.3) * 0.35
                px[x, y] = (clamp(190 * shade + 40), clamp(215 * shade + 30), clamp(240 * shade + 12), 230)
            if 5.2 < r < 6.3:
                px[x, y] = (150, 178, 210, 255)
    # The smoke, curling inside.
    for (x, y) in ((6, 8), (7, 7), (8, 7), (9, 8), (8, 9), (7, 9), (6, 9), (9, 6), (8, 6), (7, 8), (8, 8)):
        px[x, y] = (250, 250, 255, 255)
    for (x, y) in ((4, 4), (5, 3), (4, 5)):                              # the glint
        px[x, y] = (255, 255, 255, 255)
    return img


def item_whistle():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    brass, light, dark = (212, 168, 52, 255), (246, 214, 110, 255), (140, 104, 24, 255)
    for x in range(3, 13):                                               # the barrel
        for y in range(7, 11):
            px[x, y] = brass
        px[x, 7] = light
        px[x, 10] = dark
    for y in range(6, 12):                                               # the chamber, rounder at the left
        for x in range(2, 6):
            if (x - 4.5) ** 2 + (y - 8.5) ** 2 < 9:
                px[x, y] = brass
    for x in range(6, 10):                                               # the mouth slot
        px[x, 7] = (60, 40, 10, 255)
    for (x, y) in ((13, 8), (14, 8), (14, 9), (14, 10), (13, 11), (12, 11)):   # the ring
        px[x, y] = dark
    px[3, 7] = light
    px[4, 6] = light
    return img


def main():
    out = {
        "entity/memory_quaffle.png": quaffle(),
        "entity/memory_bludger.png": bludger(),
        "entity/memory_remembrall.png": glass(),
        "entity/memory_remembrall_smoke.png": smoke(),
        "item/memory_remembrall.png": item_remembrall(),
        "item/memory_remembrall_glass.png": item_glass(),
        "item/memory_remembrall_smoke.png": smoke(),
        "item/whistle.png": item_whistle(),
    }
    for name, img in out.items():
        path = ASSETS / name
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        print("wrote", path, img.size)


if __name__ == "__main__":
    main()
