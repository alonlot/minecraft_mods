"""Paint the Memory of the Cave item texture (32x32, 8-frame strip): a corked glass phial of swirling silver memory.

    python build_scripts/generate_cave_memory_texture.py

Writes textures/item/cave_memory.png (32x256) and cave_memory.png.mcmeta (frametime 4, no interpolation).
Hue-shifted silver ramp (blue-violet shadows to cyan-white highlights), glass as partial alpha, a cork stopper,
selective blue outline. Wisps drift upward on a seamless sine loop; glass, cork and outline never change, so the
silhouette does not flicker. Fully deterministic: no randomness.
"""

import math
import os

from PIL import Image

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                    "assets", "hogwartsmagic", "textures", "item")
OUT = os.path.join(BASE, "cave_memory.png")
FRAMES = 8
CX = 15.5  # the phial is 2px-symmetric about the seam between x15 and x16


def hexc(h, a=255):
    return (int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), a)


M = [hexc(c) for c in ("#26304f", "#4b5a86", "#7d92bf", "#b3c8e6", "#e0eefa", "#ffffff")]
CORK = [hexc(c) for c in ("#3a2312", "#6b4423", "#94622f", "#bd8a4a", "#dcb06a")]
OUT_LIT, OUT_MID, OUT_DARK = hexc("#4a5f88"), hexc("#33446a"), hexc("#1e2a44")
G_HI = hexc("#f2fbff")
G_RIM_LIT, G_RIM_MID, G_RIM_DARK = hexc("#9db6d8"), hexc("#5c6f9c"), hexc("#3a4b78")
G_TINT = hexc("#9cc0e8", 120)
G_SHADE = hexc("#5c6f9c", 130)


def glass_mask():
    g = set()
    for y in (7, 8):
        g |= {(x, y) for x in range(12, 20)}  # lip, 8 wide
    for y in range(9, 16):
        g |= {(x, y) for x in range(13, 19)}  # neck, 6 wide
    for y in range(32):
        for x in range(32):
            if ((x - CX) / 9.2) ** 2 + ((y - 22) / 8.4) ** 2 <= 1:
                g.add((x, y))
    return g


def cork_mask():
    c = {(x, y) for y in range(3, 7) for x in range(13, 19)}
    c -= {(13, 3), (18, 3)}
    return c


def edge1(p, region):
    x, y = p
    return any((x + dx, y + dy) not in region for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))


def outline(mask, lit, mid, dark, skip):
    """Selective outline px -> colour; lit where the shape lies to the bottom-right of the outline pixel."""
    acc = {}
    for (x, y) in mask:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q in mask or q in skip or not (0 <= q[0] < 32 and 0 <= q[1] < 32):
                continue
            acc[q] = acc.get(q, 0) - dx - dy
    return {q: (lit if k > 0 else dark if k < 0 else mid) for q, k in acc.items()}


def ell(x, y, cx, cy, rx, ry):
    return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2


GLASS = glass_mask()
CORK_M = cork_mask()
# Inner region of the body below the neck: everything in the glass not on its 1px rim.
INNER = {p for p in GLASS if not edge1(p, GLASS)}
BODY_C = (CX, 22.0)


def liquid_mask():
    liq = set()
    for (x, y) in INNER:
        if y < 17:
            continue
        if y >= 19 or ell(x, y, CX, 18.0, 7.0, 1.5) <= 1:
            liq.add((x, y))
    return liq


LIQ = liquid_mask()


def liquid_index(x, y, f):
    """Silver ramp index for the liquid mass: lit top-left, a crescent of shadow bottom-right."""
    if y <= 19 and ell(x, y, CX, 18.0, 7.0, 1.5) <= 1:  # surface ellipse
        top = 5 if (f % 2 == 0) else 4
        if (x, y) in ((11, 18), (12, 18)) or (y == 17 and x in (12, 13)):
            return top
        if y >= 19:
            return 3
        return 4
    if ell(x, y, CX - 2.0, 22 - 2.4, 9.2, 8.0) > 1:
        return 0 if ell(x, y, CX - 3.6, 22 - 4.2, 9.2, 8.0) > 1 else 1
    if ell(x, y, CX - 3.4, 22 - 3.4, 5.4, 4.6) <= 1:
        return 2
    return 1


def wisp_pixels(f):
    """Strands of memory as {(x,y): ramp index}. Phase advances one eighth per frame so the loop is seamless."""
    out = {}
    ph = f / FRAMES
    # Two swirling strands inside the body plus a thin one near the shadow side.
    for x0, amp, k, lo, hi, off in ((13.0, 1.7, 1.6, 20, 28, 0.0), (18.0, 1.7, 1.6, 20, 28, 0.55)):
        for y in range(lo, hi + 1):
            x = int(round(x0 + amp * math.sin(2 * math.pi * ((y - lo) / 8.0 * k + ph + off))))
            idx = 5 if ((y + f) % 4 == 0) else 4
            if y in (hi, lo):
                idx = 3
            out[(x, y)] = idx
            out[(x + 1, y)] = 3 if idx == 4 else 4
    # Rising wisp: leaves the surface and climbs the neck, two pixels wide, wavering by one pixel.
    top = 11 + int(round(1.5 * math.sin(2 * math.pi * ph)))
    for y in range(top, 19):
        a = 14 + int(round(0.5 + 0.5 * math.sin(2 * math.pi * ((y / 6.0) + ph))))
        idx = 3 if y > 13 else 4
        if y == top:
            idx = 5
        out[(a, y)] = idx
        out[(a + 1, y)] = idx - 1 if y > top else 3
    return out


def cork_colour(x, y):
    if x == 13 or (y == 3 and x < 16):
        return CORK[4] if (x, y) in ((14, 3), (13, 4), (13, 5)) else CORK[3]
    if x >= 17:
        return CORK[1] if y >= 4 else CORK[2]
    if x == 16 and y == 6:
        return CORK[1]
    # deterministic grain: a couple of darker flecks
    if (x, y) in ((15, 5), (14, 6), (16, 4)):
        return CORK[2]
    return CORK[3]


def glass_rim_colour(x, y):
    if y < 14:  # lip and neck: left wall catches the light, right wall in shade
        if y in (7, 8) and x < 14:
            return G_RIM_LIT
        if x <= 13:
            return G_RIM_LIT
        if x >= 18 or y == 8 and x >= 17:
            return G_RIM_DARK
        return G_RIM_MID
    nx, ny = (x - CX) / 9.2 ** 2, (y - 22) / 8.4 ** 2
    n = math.hypot(nx, ny) or 1.0
    l = -(nx + ny) / n * 0.7071
    if l > 0.35:
        return G_RIM_LIT
    if l > -0.3:
        return G_RIM_MID
    return G_RIM_DARK


def frame(f):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    # Outlines first (selective blue for glass, dark brown for cork).
    for p, c in outline(GLASS, OUT_LIT, OUT_MID, OUT_DARK, CORK_M).items():
        px[p] = c
    for p, c in outline(CORK_M, CORK[0], CORK[0], CORK[0], GLASS).items():
        if p not in GLASS:
            px[p] = c
    # Glass body: tinted and see-through, with a shaded lower right.
    for (x, y) in GLASS:
        if edge1((x, y), GLASS):
            px[x, y] = glass_rim_colour(x, y)
        else:
            l = -((x - CX) / 9.2 ** 2 + (y - 22) / 8.4 ** 2)
            px[x, y] = G_SHADE if (y >= 14 and l < -0.07) else G_TINT
    # Cork.
    for (x, y) in CORK_M:
        px[x, y] = cork_colour(x, y)
    # Liquid, with a one-step brightness pulse on frames 3-4.
    pulse = 1 if f in (3, 4) else 0
    for (x, y) in LIQ:
        i = liquid_index(x, y, f)
        px[x, y] = M[i + 1 if (pulse and i == 2 and y > 19) else i]
    # Wisps (never over the glass rim).
    for (x, y), i in wisp_pixels(f).items():
        if (x, y) in INNER:
            px[x, y] = M[max(0, min(5, i))]
    # Glass highlights stay fixed: streak on the body, on the neck, and a dot on the shoulder.
    for y in range(18, 26):
        # follow the left wall: 1px gap inside the rim
        xs = [x for x in range(32) if (x, y) in GLASS]
        px[min(xs) + 2, y] = G_HI
    for y in (10, 11, 12):
        px[13, y] = G_HI
    px[11, 16] = G_HI
    px[12, 15] = G_HI
    # Sparkle inside the neck glass on frames 2 and 6, drifting.
    if f == 2:
        px[17, 11] = M[5]
    if f == 6:
        px[14, 10] = M[5]
    # Bottom shadow rows.
    for (x, y) in GLASS:
        if y >= 29 and edge1((x, y), GLASS):
            px[x, y] = G_RIM_DARK if (x + y) % 2 else M[0]
    return img


def main():
    strip = Image.new("RGBA", (32, 32 * FRAMES), (0, 0, 0, 0))
    for f in range(FRAMES):
        strip.paste(frame(f), (0, 32 * f))
    strip.save(OUT)
    with open(OUT + ".mcmeta", "w", newline="\n") as fh:
        fh.write('{"animation":{"frametime":4,"interpolate":false}}\n')
    print("wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
