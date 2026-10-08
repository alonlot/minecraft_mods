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
G_TINT = hexc("#cfe2f5", 160)
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


def surface_top(x, f):
    """Ragged, slowly breathing top edge of the memory (y16..19); loops over 8 frames."""
    return 18 + int(round(1.1 * math.sin(0.85 * x + 2 * math.pi * f / FRAMES)))


def liquid_mask(f):
    return {(x, y) for (x, y) in INNER if y >= 16 and y >= surface_top(x, f)}


def rim_touch(p):
    x, y = p
    return any((x + dx, y + dy) not in INNER for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))


def liquid_index(x, y, f):
    """Silver mass: M3 base, M4 lit upper-left, a 2px M2 crescent bottom-right, M1 only where it touches the rim."""
    shade_side = x > CX or y > 24
    if shade_side and rim_touch((x, y)):
        return 1
    if shade_side and ell(x, y, CX - 2.2, 22 - 2.2, 8.0, 7.0) > 1:
        return 2
    if ell(x, y, CX - 3.6, 22 - 3.8, 5.4, 4.6) <= 1:
        return 5 if ell(x, y, CX - 5.0, 22 - 5.0, 2.2, 1.8) <= 1 else 4
    return 3


# Soft curls of memory drifting upward: (x, top y, bottom y, phase offset, curl offsets). Brightness fades in and
# out over the cycle (zero at the wrap), so each loop is seamless and the curls never cross.
CURLS = (
    (10, 21, 28, 0, ((0, 0), (1, 0), (2, -1), (3, -1), (4, -2))),
    (17, 20, 27, 3, ((0, 0), (1, -1), (1, -2), (2, -3), (3, -3))),
    (13, 23, 28, 5, ((0, 0), (1, 0), (2, -1), (3, -1))),
    (19, 24, 28, 7, ((0, 0), (1, -1), (2, -1))),
)


def wisp_pixels(f):
    """{(x,y): RGBA} for the curls and the neck wisp."""
    out = {}
    for x0, top, bot, off, shape in CURLS:
        t = ((f + off) % FRAMES) / FRAMES
        env = math.sin(math.pi * t)
        if env < 0.12:
            continue
        y0 = bot - int(round(t * (bot - top)))
        sway = int(round(math.sin(2 * math.pi * t)))
        for k, (dx, dy) in enumerate(shape):
            c = M[5] if (env > 0.45 and k in (1, 2)) else M[4]
            out[(x0 + sway + dx, y0 + dy)] = c

    # Neck wisp: a soft M4 column with M3 edges, one pixel of wobble, fading out at the top.
    ph = f / FRAMES
    top = 11 + int(round(1.0 * math.sin(2 * math.pi * ph)))
    for y in range(top, 18):
        w = int(round(0.5 + 0.5 * math.sin(2 * math.pi * (y / 7.0 + ph))))
        fade = 90 if y == top else 150 if y == top + 1 else 220 if y == top + 2 else 255
        core = M[4][:3] + (fade,)
        edge = M[3][:3] + (fade * 0.8 if fade < 255 else 200,)
        edge = edge[:3] + (int(edge[3]),)
        out[(15 + w, y)] = core
        out[(16 + w, y)] = core
        out[(14 + w, y)] = edge
        out[(17 + w, y)] = edge
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


def over(px, p, c):
    """Alpha-composite c over the pixel already at p, so overlays never leave the glass more transparent."""
    sa = c[3] / 255.0
    d = px[p]
    da = d[3] / 255.0
    oa = sa + da * (1 - sa)
    if oa <= 0:
        return
    px[p] = tuple(int(round((c[i] * sa + d[i] * da * (1 - sa)) / oa)) for i in range(3)) + (int(round(oa * 255)),)


def frame(f):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    # Outlines first (selective blue for glass, dark brown for cork).
    for p, c in outline(GLASS, OUT_LIT, OUT_MID, OUT_DARK, CORK_M).items():
        px[p] = c
    for p, c in outline(CORK_M, CORK[0], CORK[0], CORK[0], GLASS).items():
        if p not in GLASS:
            px[p] = c
    # Glass body: lit-from-within silver-blue tint, see-through, brighter against the inner rim.
    for (x, y) in GLASS:
        if edge1((x, y), GLASS):
            px[x, y] = glass_rim_colour(x, y)
        elif y >= 14 and (x > CX + 1 and y > 20):
            px[x, y] = G_SHADE
        else:
            px[x, y] = G_TINT
    for (x, y) in INNER:
        if y < 18 and rim_touch((x, y)):
            over(px, (x, y), M[3][:3] + (150,))
    # Cork.
    for (x, y) in CORK_M:
        px[x, y] = cork_colour(x, y)
    # Liquid, with a one-step pulse of the lit mass on frames 3-4.
    liq = liquid_mask(f)
    pulse = f in (3, 4)
    for (x, y) in liq:
        i = liquid_index(x, y, f)
        px[x, y] = M[5 if (pulse and i == 4) or i == 5 else i]
    # Feathered surface: partial-alpha silver above the ragged edge so it reads as neither gas nor liquid.
    for x in range(8, 24):
        t = surface_top(x, f)
        for dy, a_ in ((1, 215), (2, 130)):
            q = (x, t - dy)
            if q in INNER and q not in liq:
                over(px, q, M[4][:3] + (a_,))
    # Curls and neck wisp (never over the glass rim).
    for (x, y), c in wisp_pixels(f).items():
        if (x, y) in INNER:
            over(px, (x, y), c)
    # Glass highlights stay fixed: a short curved streak on the body, one on the neck, a dot on the shoulder.
    for p in ((10, 18), (9, 19), (9, 20), (9, 21), (9, 22), (10, 23)):
        px[p] = G_HI
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
        if y >= 30 and edge1((x, y), GLASS):
            px[x, y] = G_RIM_DARK
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
