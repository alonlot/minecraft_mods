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


# ---------------------------------------------------------------------------------------- velvets

REF_RED = (133, 25, 33)  # average of red_velvet.png: the other velvets are that texture re-dyed


def recolor_velvet(target):
    """The red velvet's tufts and weave in another colour, so every house chair matches the existing one."""
    src = Image.open(os.path.join(DECOR, "red_velvet.png")).convert("RGBA")
    out = canvas((0, 0, 0, 255))
    sp, op = src.load(), out.load()
    for y in range(16):
        for x in range(16):
            f = sp[x, y][0] / float(REF_RED[0])
            op[x, y] = (min(255, int(target[0] * f)), min(255, int(target[1] * f)), min(255, int(target[2] * f)), 255)
    return out


VELVET_TARGETS = {
    "blue_velvet": (46, 78, 150),
    "yellow_velvet": (206, 164, 44),
    "green_velvet": (38, 112, 66),
    "sofa_velvet": (150, 96, 58),
}


def gryffindor_velvet():
    """The red velvet with gold piping: two pinstripes a tile, so it never reads as the common room chair."""
    im = recolor_velvet(REF_RED)
    px = im.load()
    gold = hexc("#d9b04a")
    for y in range(16):
        for x in (0, 8):
            px[x, y] = shade(gold, 1.12 if y % 4 < 2 else 0.88)
    return im


def patchwork_velvet():
    """Four house velvets as 8x8 patches, with a cream running stitch along the seams."""
    patches = [Image.open(os.path.join(DECOR, "red_velvet.png")).convert("RGBA")] +         [recolor_velvet(VELVET_TARGETS[n]) for n in ("blue_velvet", "yellow_velvet", "green_velvet")]
    im = canvas((0, 0, 0, 255))
    px = im.load()
    for i, patch in enumerate(patches):
        ox, oy = (i % 2) * 8, (i // 2) * 8
        pp = patch.load()
        for y in range(8):
            for x in range(8):
                px[ox + x, oy + y] = pp[(x + ox) % 16, (y + oy) % 16]
    stitch = hexc("#efe3c4")
    for k in range(16):
        if k % 2 == 0:
            px[7, k] = stitch
            px[k, 7] = stitch
    return im


# ---------------------------------------------------------------------------------------------- quilt

QUILT_COLORS = ["#b8453a", "#e0b04a", "#3f7a8c", "#7a9a4a", "#d98a6a", "#5d4a8a", "#e7dcc0", "#a0623a"]


def patchwork_quilt():
    """A 4x4 grid of cloth patches, each with a lit upper edge, a shaded lower edge and a stitched corner."""
    rnd = random.Random(2203)
    order = list(range(8)) * 2
    rnd.shuffle(order)
    im = canvas((0, 0, 0, 255))
    px = im.load()
    for gy in range(4):
        for gx in range(4):
            col = hexc(QUILT_COLORS[order[gy * 4 + gx]])
            for y in range(4):
                for x in range(4):
                    f = 1.0 + rnd.uniform(-0.04, 0.04)
                    if y == 0 or x == 0:
                        f += 0.1
                    if y == 3 or x == 3:
                        f -= 0.14
                    px[gx * 4 + x, gy * 4 + y] = shade(col, f)
            px[gx * 4 + 1, gy * 4 + 1] = shade(col, 0.82)  # the stitch that holds the patch down
    return im


# ------------------------------------------------------------------------------------------------ rugs

RUG_PALETTES = {
    # name: (field, ring, dot, band_outer, band_inner)
    "gryffindor": ("#8c2a2a", "#e0b24a", "#f0d27a", "#e0b24a", "#5a1818"),
    "ravenclaw": ("#2c4a86", "#b07a4a", "#d9a878", "#b07a4a", "#1a2c52"),
    "hufflepuff": ("#d8aa30", "#2e2a26", "#4a443c", "#2e2a26", "#a07c1c"),
    "slytherin": ("#24603e", "#b4bcc2", "#dde2e5", "#b4bcc2", "#143a24"),
}


def rug_center(name):
    """The field of one rug tile: a woven ground with a ring and a dot, so a block of rugs repeats the motif."""
    field, ring, dot, _, _ = [hexc(c) for c in RUG_PALETTES[name]]
    rnd = random.Random(sum(ord(c) for c in name) * 17)
    im = canvas(field)
    px = im.load()
    for y in range(16):
        for x in range(16):
            weave = 0.045 if (x + y) % 2 == 0 else -0.045
            px[x, y] = shade(field, 1.0 + weave + rnd.uniform(-0.025, 0.025))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 4.6 <= d <= 5.9:
                px[x, y] = shade(ring, 1.0 + (0.05 if (x + y) % 2 == 0 else -0.05))
            elif d <= 1.9:
                px[x, y] = shade(dot, 1.0 + (0.04 if (x + y) % 2 == 0 else -0.04))
    return im


def rug_border(name):
    """The band along a rug edge. Row 0 is the outermost row; the models turn it to face each side."""
    _, _, _, outer, inner = [hexc(c) for c in RUG_PALETTES[name]]
    field = hexc(RUG_PALETTES[name][0])
    im = canvas(field)
    px = im.load()
    for x in range(16):
        px[x, 0] = shade(outer, 0.8)
        px[x, 1] = outer if (x // 2) % 2 == 0 else shade(outer, 0.88)
        for y in range(2, 16):
            px[x, y] = inner
    return im


RAG_COLORS = ["#c46a4a", "#e0b25a", "#6a9a8a", "#d9cfae", "#8a5a7a", "#4a7aa0"]


def rag_rug():
    """Hand-woven rag strips: horizontal bands of scrap cloth, each broken up by the weft."""
    rnd = random.Random(777)
    im = canvas((0, 0, 0, 255))
    px = im.load()
    rows = [0, 0, 1, 1, 1, 2, 2, 3, 3, 3, 4, 4, 5, 5, 5, 5]
    palette = [hexc(c) for c in RAG_COLORS]
    for y in range(16):
        col = palette[rows[y] % len(palette)]
        for x in range(16):
            f = 1.0 + (0.1 if (x + y) % 2 == 0 else -0.08) + rnd.uniform(-0.04, 0.04)
            px[x, y] = shade(col, f)
    return im


def rag_rug_border():
    palette = hexc("#7a6a52")
    im = canvas(palette)
    px = im.load()
    for x in range(16):
        px[x, 0] = shade(palette, 0.7)
        px[x, 1] = shade(hexc("#d9cfae"), 1.0 if x % 2 == 0 else 0.85)
        for y in range(2, 16):
            px[x, y] = shade(palette, 1.05 if (x + y) % 2 == 0 else 0.95)
    return im


def main():
    save(hearth_brick(), DECOR, "hearth_brick")
    save(warmed_icon(), os.path.join(ASSETS, "mob_effect"), "warmed")
    for name, target in VELVET_TARGETS.items():
        save(recolor_velvet(target), DECOR, name)
    save(gryffindor_velvet(), DECOR, "gryffindor_velvet")
    save(patchwork_velvet(), DECOR, "patchwork_velvet")
    save(patchwork_quilt(), DECOR, "patchwork_quilt")
    for house in RUG_PALETTES:
        save(rug_center(house), DECOR, house + "_rug")
        save(rug_border(house), DECOR, house + "_rug_border")
    save(rag_rug(), DECOR, "rag_rug")
    save(rag_rug_border(), DECOR, "rag_rug_border")

if __name__ == "__main__":
    main()
