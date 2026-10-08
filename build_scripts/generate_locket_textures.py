"""Paint the Unquenchable Thirst effect icon (18x18) and the Slytherin's Locket item texture (32x32, 8-frame strip).

    python build_scripts/generate_locket_textures.py

Writes textures/mob_effect/unquenchable_thirst.png, textures/item/slytherins_locket.png and its .png.mcmeta.
The locket is hand-ramped (gold / emerald), selectively outlined, with a gleam sweep and a travelling sparkle.
Fully deterministic: no randomness.
"""

import math
import os

from PIL import Image, ImageDraw

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                      "assets", "hogwartsmagic", "textures")


def outline(image, colour):
    w, h = image.size
    src = image.copy().load()
    out = image.load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src[nx, ny][3] != 0:
                    out[x, y] = colour + (255,)
                    break
    return image


def thirst():
    """A cracked, dry green droplet-shaped basin cup: a pale-green drop with a dry crack through it."""
    image = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    green = (62, 107, 72, 255)
    d.polygon([(9, 2), (13, 9), (5, 9)], fill=green)
    d.ellipse((4, 7, 14, 16), fill=green)
    d.ellipse((10, 11, 13, 15), fill=(40, 76, 50, 255))
    image = outline(image, (14, 32, 18))
    for x, y in ((9, 6), (8, 8), (9, 10), (8, 12), (9, 14)):
        image.putpixel((x, y), (14, 32, 18, 255))
    image.putpixel((7, 9), (190, 235, 170, 255))
    image.putpixel((7, 10), (130, 190, 120, 255))
    return image


# ---- Slytherin's Locket ----------------------------------------------------------------------------------
def hexc(h):
    return (int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255)


GOLD = [hexc(c) for c in ("#2a1608", "#5a3410", "#8e5416", "#c27d1c", "#e0a526", "#f6cf48", "#fff0a0", "#fffbe6")]
EMER = [hexc(c) for c in ("#07201a", "#0d4a2e", "#168a4b", "#35c46f", "#8ef0ac", "#e2fff0")]


def mute(c, k=0.10):
    lum = int(0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2])
    return tuple(int(round(v + (lum - v) * k)) for v in c[:3]) + (255,)


GOLD[4], GOLD[5] = mute(GOLD[4]), mute(GOLD[5])
FRAMES = 8
CX, CY = 16, 19

# The serpentine S, 12 wide x 14 tall, 2-3px stroke; top hook opens right, bottom hook opens left.
S_ROWS = (
    "...######...",
    "..########..",
    ".###....###.",
    ".###........",
    ".###........",
    "..####......",
    "...######...",
    "....######..",
    "......####..",
    "........###.",
    "........###.",
    ".###....###.",
    "..########..",
    "...######...",
)
S_X0, S_Y0 = 10, 12


def ell(x, y, rx, ry):
    return ((x - CX) / rx) ** 2 + ((y - CY) / ry) ** 2


def lit(x, y, rx, ry):
    """Light factor -1..1 from the ellipse normal; +1 faces the top-left light."""
    nx, ny = (x - CX) / (rx * rx), (y - CY) / (ry * ry)
    n = math.hypot(nx, ny) or 1.0
    return -(nx + ny) / n * 0.7071


def edge_layer(p, region, n=1):
    x, y = p
    for d in range(1, n + 1):
        for dx, dy in ((d, 0), (-d, 0), (0, d), (0, -d)):
            if (x + dx, y + dy) not in region:
                return d
    return 99


def locket_base():
    """gold[(x,y)] = gold ramp index, stone[(x,y)] = emerald ramp index, cells = S pixels in reading order."""
    gold, stone = {}, {}
    # Chain: two diagonals from the bail, links alternate G5/G3, the left side catches the light.
    for i in range(7):
        for side, (x, y) in enumerate(((13 - i, 6 - i), (18 + i, 6 - i))):
            col = 5 if (i // 2) % 2 == 0 else 3
            if side and i < 3:
                col -= 1
            gold[(x, y)] = col
            gold[(x + 1 if side == 0 else x - 1, y)] = col
            if i % 3 == 2:
                gold[(x, y)] = 1
    # Bail: 4x4 ring with a 2x2 hole.
    for y in range(5, 9):
        for x in range(14, 18):
            if x in (15, 16) and y in (6, 7):
                continue
            gold[(x, y)] = 4
    for p in ((14, 5), (15, 5), (14, 6)):
        gold[p] = 6
    for p in ((17, 7), (17, 8), (16, 8)):
        gold[p] = 2
    gold[(15, 8)] = 3
    # Body.
    body = {(x, y) for y in range(8, 30) for x in range(5, 28) if ell(x, y, 8.6, 9.6) <= 1}
    field = {p for p in body if ell(p[0], p[1], 7.4, 8.3) <= 1}
    for p in body:
        x, y = p
        l = lit(x, y, 8.6, 9.6)
        if p in field:
            if edge_layer(p, field) == 1:  # recess wall: in shadow top-left, catching light bottom-right
                gold[p] = 2 if l > 0.15 else 3
            else:
                gold[p] = 4 if (x + y) < 33 + (0, 1, 1, 0, 2)[y % 5] else 3
        else:
            e = edge_layer(p, body, 2)
            if e == 1 and l < -0.3:
                gold[p] = 3 if 19 <= y <= 21 else 2  # a short staggered bounce run, not a full highlight ring
            elif e == 2 and l < -0.45:
                gold[p] = 2
            elif l > 0.75:
                gold[p] = 6 if 13 <= y <= 15 else 5
            elif l > 0.45:
                gold[p] = 5
            elif l > -0.2:
                gold[p] = 4
            elif l > -0.55:
                gold[p] = 3
            else:
                gold[p] = 2
    # Hinge seam between the two doors.
    for y in range(12, 27):
        if (16, y) in field:
            gold[(16, y)] = 2
            gold[(17, y)] = 3
            if (15, y) in field and (y < 15 or y > 23):
                gold[(15, y)] = 5
    # Latch.
    for p, c in (((15, 27), 4), ((16, 27), 3), ((17, 27), 3), ((15, 28), 2), ((16, 28), 2), ((17, 28), 1)):
        gold[p] = c
    # Specular on the top-left rim.
    for p in ((9, 14), (9, 15), (10, 13)):
        if p in body and p not in field:
            gold[p] = 7 if p == (9, 14) else 6
    # The S, built from small stones, shaded by form.
    cells = [(S_X0 + c, S_Y0 + r) for r, row in enumerate(S_ROWS) for c, ch in enumerate(row) if ch == "#"]
    cellset = set(cells)
    for (x, y) in cells:
        up, left = (x, y - 1) in cellset, (x - 1, y) in cellset
        dn, rt = (x, y + 1) in cellset, (x + 1, y) in cellset
        if not up or not left:
            v = 3
        elif not dn or not rt:
            v = 1
        else:
            v = 2
        if not dn and not left:
            v = 2
        stone[(x, y)] = v
    # 1px inline on every gold pixel touching the S (also where it meets the rim); the lit side is warm brown,
    # the shaded side near-black, decided by the summed direction like the outer outline.
    acc = {}
    for (x, y) in cells:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in cellset and q in gold:
                acc[q] = acc.get(q, 0) - dx - dy
    for q, k in acc.items():
        gold[q] = 1 if k >= 0 else 0
    return gold, stone, cells


def outline_colours(opaque):
    """Selective outline: warm brown on the lit top-left, near-black brown on the shaded bottom-right."""
    acc = {}
    for (x, y) in opaque:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q in opaque or not (0 <= q[0] < 32 and 0 <= q[1] < 32):
                continue
            a = acc.setdefault(q, 0)
            acc[q] = a - dx - dy  # shape at (-dx,-dy) from q: +ve = shape lies bottom-right of the outline px
    res = {}
    for q, k in acc.items():
        res[q] = (0x5a, 0x34, 0x10, 255) if k > 0 else (0x2a, 0x16, 0x08, 255) if k < 0 else (0x40, 0x22, 0x0b, 255)
    return res


def clamp_ramp(i):
    return max(0, min(7, i))


def locket_frame(f, gold, stone, cells):
    image = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = image.load()
    gold = {p: i for p, i in gold.items() if 0 <= p[0] < 32 and 0 <= p[1] < 32}
    for p, col in outline_colours(set(gold) | set(stone)).items():
        px[p] = col
    for p, i in gold.items():
        px[p] = GOLD[i]
    # Gleam band sweeping top-left to bottom-right over frames 1..5, brightening gold only.
    if 1 <= f <= 5:
        centre = 21 + (f - 1) * 5
        for p, i in gold.items():
            if i <= 1:
                continue
            d = abs(p[0] + p[1] - centre)
            if d == 0:
                px[p] = GOLD[7 if i >= 4 else clamp_ramp(i + 3)]
            elif d == 1:
                px[p] = GOLD[clamp_ramp(i + 2)]
    for p, i in stone.items():
        px[p] = EMER[i]
    # A few lit stones along the upper curve.
    for p in ((14, 12), (15, 12), (12, 13), (12, 14), (11, 15)):
        if p in stone:
            px[p] = EMER[4] if p in ((14, 12), (12, 14)) else EMER[3]
    # Sparkle travels along the S: bright on odd frames, soft twinkle on even; frames 0 and 7 are the rest state.
    n = len(cells)
    if f in (1, 3, 5):
        px[cells[(f * 7 + 2) % n]] = EMER[5]
    elif f in (2, 4, 6):
        px[cells[(f * 7 + 9) % n]] = EMER[4]
    return image


def locket():
    gold, stone, cells = locket_base()
    strip = Image.new("RGBA", (32, 32 * FRAMES), (0, 0, 0, 0))
    for f in range(FRAMES):
        strip.paste(locket_frame(f, gold, stone, cells), (0, 32 * f))
    return strip


def main():
    for sub, name, img in (("mob_effect", "unquenchable_thirst", thirst()), ("item", "slytherins_locket", locket())):
        path = os.path.join(ASSETS, sub, name + ".png")
        img.save(path)
        print("wrote", os.path.normpath(path))
        if name == "slytherins_locket":
            with open(path + ".mcmeta", "w", newline="\n") as fh:
                fh.write('{"animation":{"frametime":3,"interpolate":false}}\n')


if __name__ == "__main__":
    main()
