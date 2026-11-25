"""
Block textures (16x16) and the Warmed effect icon (18x18) for the cosy home pack.

Run from the repo root:  python tools/cosy_textures/gen_cosy_textures.py
Output: src/main/resources/assets/hogwartsmagic/textures/block/decor/<name>.png and mob_effect/warmed.png
Every texture is painted per pixel from a small palette with a seeded RNG, so a rerun gives identical files.
"""
import os
import random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/hogwartsmagic/textures")
DECOR = os.path.join(ASSETS, "block/decor")


def hexc(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def save(im, folder, name):
    os.makedirs(folder, exist_ok=True)
    im.save(os.path.join(folder, name + ".png"))
    print("wrote", name)


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), 255)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def canvas(base):
    return Image.new("RGBA", (16, 16), base)


# ---------------------------------------------------------------------------------------------- hearth

def hearth_brick():
    """Soot-warmed red-brown brick, four courses of four pixels, running bond, grey mortar."""
    rnd = random.Random(1101)
    mortar = hexc("#6f6760")
    bricks = [hexc(h) for h in ("#8f4a3a", "#9c5440", "#84432f", "#a35c46", "#7b3f2e")]
    im = canvas(mortar)
    px = im.load()
    for row in range(4):
        y0 = row * 4
        shift = 0 if row % 2 == 0 else 4
        for k in range(-1, 3):
            x0 = shift + k * 8
            col = rnd.choice(bricks)
            for y in range(y0, y0 + 3):
                for x in range(x0, x0 + 7):
                    if 0 <= x < 16:
                        f = 1.0 + rnd.uniform(-0.07, 0.07)
                        if y == y0:
                            f += 0.07
                        if y == y0 + 2:
                            f -= 0.09
                        px[x, y] = shade(col, f)
    # soot at the top edge so the brick reads as lived-in
    for x in range(16):
        for y in range(0, 2):
            if rnd.random() < 0.5:
                px[x, y] = mix(px[x, y], hexc("#2b2623"), 0.35)
    return im


# ------------------------------------------------------------------------------------- effect icon

def warmed_icon():
    """18x18: an orange flame over two logs on the dark rounded plate the other effect icons use."""
    S = 18
    bg = (28, 24, 40, 255)
    edge = (60, 54, 80, 255)
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = im.load()
    for y in range(S):
        for x in range(S):
            corner = min(x, S - 1 - x) + min(y, S - 1 - y)
            if corner < 2 and min(x, S - 1 - x) < 2 and min(y, S - 1 - y) < 2:
                continue
            on_edge = x in (0, S - 1) or y in (0, S - 1)
            px[x, y] = edge if on_edge else bg
    flame_outer = hexc("#e8541c")
    flame_mid = hexc("#ff9a2e")
    flame_core = hexc("#ffe27a")
    rows = {
        3: (9, 9), 4: (8, 9), 5: (8, 10), 6: (7, 10), 7: (6, 11), 8: (6, 11), 9: (5, 12), 10: (5, 12), 11: (5, 12),
    }
    for y, (a, b) in rows.items():
        for x in range(a, b + 1):
            px[x, y] = flame_outer
    for y in range(5, 12):
        a, b = rows[y]
        for x in range(a + 1, b):
            px[x, y] = flame_mid
    for y in range(8, 12):
        for x in range(8, 10):
            px[x, y] = flame_core
    px[9, 7] = flame_core
    log = hexc("#6b4423")
    log_hi = hexc("#8a5a2e")
    log_end = hexc("#c9a06a")
    for x in range(3, 15):
        px[x, 13] = log_hi
        px[x, 14] = log
    for x in range(4, 14):
        px[x, 12] = log
    px[3, 13] = log_end
    px[3, 14] = log_end
    px[14, 13] = log_end
    px[14, 14] = log_end
    return im


def main():
    save(hearth_brick(), DECOR, "hearth_brick")
    save(warmed_icon(), os.path.join(ASSETS, "mob_effect"), "warmed")


if __name__ == "__main__":
    main()
