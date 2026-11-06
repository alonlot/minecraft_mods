"""Draws the mod's magic particle textures into textures/particle/.

Run from anywhere:  python tools/particle_textures/gen_particles.py

Every sprite is 16x16 RGBA. The glowing ones (mote, ember, spark, glint) are WHITE with a soft alpha
falloff: the client tints them per spawn and draws them additively, so alpha is the brightness.
The smoke is a painterly grey blob (light from the top left, a darker belly) tinted the same way.
No hard pixel edges anywhere: every sprite ends in a smooth fade to alpha 0 well inside the border.
"""
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "src", "main", "resources",
                                    "assets", "hogwartsmagic", "textures", "particle"))
N = 16
C = (N - 1) / 2.0


def smooth(x):
    x = max(0.0, min(1.0, x))
    return x * x * (3 - 2 * x)


def save(name, px):
    img = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            r, g, b, a = px[y][x]
            img.putpixel((x, y), (int(max(0, min(255, r))), int(max(0, min(255, g))),
                                  int(max(0, min(255, b))), int(max(0, min(255, a)))))
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, name + ".png"))


def blank():
    return [[(255, 255, 255, 0) for _ in range(N)] for _ in range(N)]


def mote(frame):
    """A soft glowing dot: a hot core inside a wide halo. Frames differ in halo size (the twinkle)."""
    px = blank()
    halo = 7.4 - frame * 0.9      # frame 0 widest halo .. frame 3 tight bead
    core = 2.0 + (3 - frame) * 0.25
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - C, y - C)
            a = smooth(1 - d / halo) ** 2 * 0.55 + smooth(1 - d / core) ** 1.5 * 0.9
            a = min(1.0, a)
            px[y][x] = (255, 255, 255, 255 * a)
    return px


def ember(frame):
    """A tiny hot spark: a bright pip with a short warm halo, slightly lopsided like a flame lick."""
    px = blank()
    rnd = random.Random(40 + frame)
    sx = rnd.uniform(-0.5, 0.5)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x - C - sx, (y - C) * 0.85)
            a = smooth(1 - d / 5.2) ** 2 * 0.6 + smooth(1 - d / 2.1) * 0.95
            # a faint lick upwards
            up = (C - y) / 6.0
            if up > 0:
                a += 0.35 * smooth(1 - abs(x - C - sx) / (1.6 - 0.8 * up)) * smooth(1 - up) * smooth(up * 3)
            a = min(1.0, a)
            # core white, rim slightly warmer (tint multiplies on top)
            t = smooth(1 - d / 3.0)
            px[y][x] = (255, 255 - 40 * (1 - t), 255 - 90 * (1 - t), 255 * a)
    return px


def spark():
    """A streak along U: bright head on the right, long tapering tail to the left. Squashed in V."""
    px = blank()
    for y in range(N):
        for x in range(N):
            u = (x - 1.0) / (N - 3.0)          # 0 tail .. 1 head
            if u < 0 or u > 1:
                continue
            width = 0.35 + 2.3 * (u ** 1.3)    # thin tail, fat head
            d = abs(y - C) / width
            a = smooth(1 - d) ** 1.6 * (u ** 0.9) * (smooth((1 - u) * 9) if u > 0.88 else 1)
            a = min(1.0, a * 1.15)
            px[y][x] = (255, 255, 255, 255 * a)
    return px


def glint(frame):
    """A four point star: two long thin spikes through a small bright core. Frame 1 is the fat flash,
    0 and 2 the thin ones, 3 a diagonal cross (the sparkle turning)."""
    px = blank()
    long_ = [7.2, 7.6, 6.2, 6.6][frame]
    thick = [0.55, 0.9, 0.5, 0.45][frame]
    diag = False
    for y in range(N):
        for x in range(N):
            dx, dy = x - C, y - C
            if diag:
                dx, dy = (dx + dy) * 0.7071, (dy - dx) * 0.7071
            a1 = smooth(1 - abs(dx) / long_) ** 1.8 * smooth(1 - abs(dy) / (thick + abs(dx) * 0.06 + 0.35))
            a2 = smooth(1 - abs(dy) / long_) ** 1.8 * smooth(1 - abs(dx) / (thick + abs(dy) * 0.06 + 0.35))
            core = smooth(1 - math.hypot(dx, dy) / 2.6) ** 1.4
            a = min(1.0, max(a1, a2) * 0.95 + core * 0.9)
            px[y][x] = (255, 255, 255, 255 * a)
    return px


def value_noise(rnd, size):
    """Smooth tileless value noise sampled as a function."""
    g = [[rnd.random() for _ in range(size + 2)] for _ in range(size + 2)]

    def f(x, y):
        ix, iy = int(x), int(y)
        fx, fy = smooth(x - ix), smooth(y - iy)
        a = g[iy][ix] * (1 - fx) + g[iy][ix + 1] * fx
        b = g[iy + 1][ix] * (1 - fx) + g[iy + 1][ix + 1] * fx
        return a * (1 - fy) + b * fy
    return f


def smoke(frame):
    """A big soft puff, painted: a lumpy round body, lit from the top left with a darker belly, a
    ragged billowing edge and a smooth alpha fade so it overlaps cleanly with its neighbours."""
    px = blank()
    rnd = random.Random(900 + frame * 7)
    n1 = value_noise(rnd, 6)
    n2 = value_noise(rnd, 12)
    for y in range(N):
        for x in range(N):
            dx, dy = (x - C) / 7.5, (y - C) / 7.5
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            # billowing radius: noise bumps the edge in and out
            bump = (n1(3 + 2 * math.cos(ang), 3 + 2 * math.sin(ang)) - 0.5) * 0.55
            edge = 0.97 + bump
            body = smooth((edge - d) / 0.55)
            tex = n2(x * 0.55 + 1, y * 0.55 + 1)
            # light from the top left, painted as a soft gradient plus lumps
            lit = 0.5 + (-dx - dy) * 0.28 + (tex - 0.5) * 0.38
            lit = max(0.0, min(1.0, lit))
            shade = 120 + 120 * lit
            a = body * (0.7 + 0.3 * tex)
            px[y][x] = (shade, shade, shade * 1.02, 255 * a)
    return px


def main():
    for i in range(4):
        save("magic_mote_%d" % i, mote(i))
        save("ember_%d" % i, ember(i))
        save("glint_%d" % i, glint(i))
        save("soft_smoke_%d" % i, smoke(i))
    save("spark_0", spark())
    print("wrote particle textures to", OUT)


if __name__ == "__main__":
    main()
