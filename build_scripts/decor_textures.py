"""Pixel art for the Hogwarts decorations. Every function returns a PIL image.

Textures on thin elements use Minecraft's automatic UVs, so a front texture is drawn the way the
player sees it: texture pixel (u, v) shows block position x = 16 - u (north face), y = 16 - v.
"""
import random
from PIL import Image


def hx(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))),
            max(0, min(255, int(c[2] * f))), c[3])


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


CLEAR = (0, 0, 0, 0)


def new(w=16, h=16, fill=CLEAR):
    return Image.new("RGBA", (w, h), fill)


def noise_fill(img, base, amount, seed, box=None):
    rng = random.Random(seed)
    x0, y0, x1, y1 = box or (0, 0, img.width, img.height)
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), shade(base, 1 + rng.uniform(-amount, amount)))


def sprite(img, rows, palette, ox, oy, clip=None):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            c = palette.get(ch)
            if c is None:
                continue
            x, y = ox + i, oy + j
            if clip and not clip(x, y):
                continue
            if 0 <= x < img.width and 0 <= y < img.height:
                img.putpixel((x, y), c)


# ---- houses ---------------------------------------------------------------------------------------

HOUSES = {
    "gryffindor": dict(main=hx("8c0c10"), dark=hx("5c0709"), light=hx("b01c20"),
                       acc=hx("e0b040"), acc_dark=hx("9c7420"), gem=hx("d0182e")),
    "ravenclaw": dict(main=hx("14265e"), dark=hx("0a1538"), light=hx("27408c"),
                      acc=hx("c8925a"), acc_dark=hx("7a5326"), gem=hx("2a5ad8")),
    "hufflepuff": dict(main=hx("e8b830"), dark=hx("b58a18"), light=hx("f6d464"),
                       acc=hx("2a2622"), acc_dark=hx("111010"), gem=hx("f8dc40")),
    "slytherin": dict(main=hx("1a5030"), dark=hx("0e2e1a"), light=hx("2a7444"),
                      acc=hx("c4c8cc"), acc_dark=hx("767a80"), gem=hx("22b058")),
}

# 8 x 9 emblems. X = accent, o = dark accent, . = nothing.
EMBLEMS = {
    "gryffindor": [  # lion head with a mane
        ".o.oo.o.",
        "oXXXXXXo",
        "oXoXXoXo",
        "XXXXXXXX",
        "oXXooXXo",
        ".XXXXXX.",
        "oXo..oXo",
        ".oXXXXo.",
        "..o..o..",
    ],
    "ravenclaw": [  # eagle, wings spread
        "X..oo..X",
        "XX.XX.XX",
        "oXXXXXXo",
        ".oXXXXo.",
        "..XXXX..",
        "...XX...",
        "..oXXo..",
        "..X..X..",
        ".o....o.",
    ],
    "hufflepuff": [  # badger face with a white stripe (stripe drawn in cloth colour)
        ".o....o.",
        "oXX..XXo",
        "XXX..XXX",
        "XoX..XoX",
        "XXX..XXX",
        ".XX..XX.",
        "..XooX..",
        "...oo...",
        "........",
    ],
    "slytherin": [  # coiled serpent
        "..XXXo..",
        ".Xo..oX.",
        ".X...oo.",
        "..XXo...",
        "....oXX.",
        ".o....X.",
        "oX...oX.",
        ".oXXXX..",
        "......o.",
    ],
}


def banner_full(house):
    """16 x 32 banner, as seen from the front. Rows 0-1 are behind the rod (left clear)."""
    h = HOUSES[house]
    img = new(16, 32)
    for y in range(2, 32):
        for x in range(2, 14):
            # Bottom: a V-shaped notch cut into the hem.
            notch = y - 26
            if notch > 0 and (abs(x - 7.5) < notch * 1.25):
                continue
            edge = min(x - 2, 13 - x)
            c = h["main"]
            if edge == 0:
                c = h["acc_dark"]
            elif edge == 1:
                c = h["acc"]
            elif edge == 2:
                c = h["dark"]
            elif (x + y) % 7 == 0:
                c = h["light"]
            img.putpixel((x, y), c)
    # Gold hem stripe near the top and above the notch.
    for x in range(2, 14):
        img.putpixel((x, 4), h["acc"])
        img.putpixel((x, 5), h["acc_dark"])
        img.putpixel((x, 24), h["acc"])
    pal = {"X": h["acc"], "o": h["acc_dark"]}
    if house == "hufflepuff":
        pal = {"X": h["acc"], "o": hx("5a5048")}
    sprite(img, EMBLEMS[house], pal, 4, 11)
    # Hem tassels at the two tips.
    for y in range(29, 32):
        for x in (2, 13):
            if img.getpixel((x, y))[3]:
                img.putpixel((x, y), h["acc"])
    return img


def banner_halves(house):
    full = banner_full(house)
    return full.crop((0, 0, 16, 16)), full.crop((0, 16, 16, 32))


def banner_item(house):
    full = banner_full(house).crop((2, 2, 14, 32))  # 12 x 30
    small = full.resize((7, 15), Image.NEAREST)
    img = new()
    # Rod.
    for x in range(3, 13):
        img.putpixel((x, 0), hx("7a5326") if x in (3, 12) else hx("c8963c"))
    img.alpha_composite(small, (4, 1))
    return img


def shield(house):
    """Crest shield for the wall plaque. Shield covers u 2..13, v 1..14: a 10 x 12 field inside a
    one-pixel rim, so the emblem keeps a margin of house colour around it."""
    h = HOUSES[house]
    img = new()

    def inside(x, y):
        if not (2 <= x <= 13 and 1 <= y <= 14):
            return False
        if y >= 10:  # taper to a point
            d = y - 9
            return 2 + d <= x <= 13 - d
        return True

    for y in range(16):
        for x in range(16):
            if not inside(x, y):
                continue
            border = not (inside(x - 1, y) and inside(x + 1, y) and inside(x, y - 1) and inside(x, y + 1))
            if border:
                img.putpixel((x, y), h["acc"] if y < 9 else h["acc_dark"])
            else:
                img.putpixel((x, y), h["light"] if (x < 7 and y < 6) else h["main"])
    pal = {"X": h["acc"], "o": h["acc_dark"]}
    if house == "hufflepuff":
        pal = {"X": h["acc"], "o": hx("5a5048")}
    sprite(img, EMBLEMS[house], pal, 4, 3,
           clip=lambda x, y: all(inside(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1)))
    return img


def gem_pile(house):
    h = HOUSES[house]
    g = h["gem"]
    img = new()
    rng = random.Random(house)
    for y in range(16):
        for x in range(16):
            f = rng.choice([0.55, 0.7, 0.85, 1.0, 1.0, 1.15])
            img.putpixel((x, y), shade(g, f))
    for _ in range(14):
        x, y = rng.randrange(16), rng.randrange(16)
        img.putpixel((x, y), mix(g, hx("ffffff"), 0.6))
    return img


def glass():
    img = new()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            img.putpixel((x, y), hx("e8f4f8", 150) if edge else hx("cfe6ee", 46))
    for i in range(3, 9):
        img.putpixel((i - 1, i), hx("ffffff", 120))
    return img


# ---- metals and materials -------------------------------------------------------------------------

def metal(base, seed):
    """Smooth polished metal with a soft diagonal sheen."""
    img = new()
    rng = random.Random(seed)
    for y in range(16):
        for x in range(16):
            d = ((x + y) % 16) / 16.0
            f = 0.82 + 0.3 * (1 - abs(d - 0.35) * 2.2 if abs(d - 0.35) < 0.45 else 0) + rng.uniform(-0.04, 0.04)
            img.putpixel((x, y), shade(base, f))
    return img


def brass():
    return metal(hx("b8862e"), "brass")


def iron():
    return metal(hx("4a4a52"), "iron")


def candle_wax():
    """Ivory wax with drips. The top-right 2 x 2 pixels are the dark wick (used by explicit UVs)."""
    img = new()
    rng = random.Random("wax")
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(hx("efe4c8"), 1 + rng.uniform(-0.05, 0.03) - (0.06 if x % 4 == 3 else 0)))
    for x in (1, 6, 10, 13):
        for y in range(0, rng.randint(2, 5)):
            img.putpixel((x, y), hx("fbf4e0"))
    for x in (14, 15):
        for y in (0, 1):
            img.putpixel((x, y), hx("2a2018"))
    # Flame sprite at u 12..13, v 0..2 (top to bottom: tip, body, base), clear around it.
    for x in (12, 13):
        for y in (0, 1, 2):
            img.putpixel((x, y), CLEAR)
    img.putpixel((12, 0), hx("fff2a0"))
    img.putpixel((12, 1), hx("ffd040"))
    img.putpixel((13, 1), hx("ffb020"))
    img.putpixel((12, 2), hx("ff8a10"))
    img.putpixel((13, 2), hx("ffd040"))
    return img


def velvet(base, seed):
    """Tufted velvet with buttons every 5 pixels."""
    img = new()
    rng = random.Random(seed)
    for y in range(16):
        for x in range(16):
            f = 1 + rng.uniform(-0.06, 0.06)
            bx, by = (x - 2) % 5, (y - 2) % 5
            if bx == 0 and by == 0:
                img.putpixel((x, y), shade(base, 0.55))
                continue
            dist = min(bx, 5 - bx) + min(by, 5 - by)
            f *= 0.82 + 0.06 * dist
            img.putpixel((x, y), shade(base, f))
    return img


def parchment(seed="parchment", written=False):
    img = new()
    rng = random.Random(seed)
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            f = 1 + rng.uniform(-0.04, 0.04) - (0.12 if edge == 0 else 0)
            img.putpixel((x, y), shade(hx("e6d4a8"), f))
    if written:
        for y in range(3, 14, 2):
            length = rng.randint(7, 12)
            x = 2
            while x < 2 + length:
                if rng.random() < 0.82:
                    img.putpixel((x, y), hx("3a2a1e"))
                x += 1
    return img


def scroll_end():
    """The rolled end of a scroll: a parchment spiral."""
    img = new()
    rows = [
        "................",
        "................",
        "................",
        "................",
        "......pppp......",
        ".....pdddp......",
        "....pdpppdp.....",
        "....pdpddpdp....",
        "....pdpdpdpp....",
        "....pdppdpdp....",
        ".....pdddpdp....",
        "......ppppd.....",
        "................",
        "................",
        "................",
        "................",
    ]
    sprite(img, rows, {"p": hx("e6d4a8"), "d": hx("a08858")}, 0, 0)
    # Fill the rest so any UV slice stays parchment coloured.
    out = parchment("end")
    out.alpha_composite(img)
    return out


def quill():
    img = new()
    rng = random.Random("quill")
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(hx("f2f0ea"), 1 + rng.uniform(-0.08, 0.02)))
    for y in range(16):
        img.putpixel((7, y), hx("b8b2a4"))
    return img


def ink():
    img = new()
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), hx("1a1a2a") if (x + y) % 5 else hx("2c2c48"))
    return img


def straw():
    img = new()
    rng = random.Random("straw")
    cols = [hx("8a6a2a"), hx("a88238"), hx("c49c4c"), hx("d8b462")]
    for x in range(16):
        c = rng.choice(cols)
        for y in range(16):
            img.putpixel((x, y), shade(c, 1 + rng.uniform(-0.06, 0.06)))
    for x in range(16):  # binding cord, just below the top of the bristle element (v 9.5)
        img.putpixel((x, 10), hx("5a3a1e"))
        img.putpixel((x, 11), hx("6e4826"))
    return img


def cork():
    img = new()
    noise_fill(img, hx("a07a4c"), 0.12, "cork")
    return img


def potion(color, seed):
    """Glass bottle full of potion: a glass rim on top, glossy liquid below."""
    img = new()
    rng = random.Random(seed)
    for y in range(16):
        for x in range(16):
            if y < 3:
                img.putpixel((x, y), hx("d8ecf0"))
                continue
            f = 0.75 + 0.35 * (1 - y / 16) + rng.uniform(-0.05, 0.05)
            img.putpixel((x, y), shade(color, f))
    for y in range(4, 12):
        img.putpixel((3, y), mix(color, hx("ffffff"), 0.55))
    return img


def lens():
    img = new()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d > 6.5:
                img.putpixel((x, y), hx("7a5524"))
            elif d > 5.2:
                img.putpixel((x, y), hx("e0b85a"))
            else:
                img.putpixel((x, y), mix(hx("1a2a4a"), hx("5a8ac0"), max(0, 1 - d / 5)))
    img.putpixel((6, 5), hx("ffffff"))
    img.putpixel((5, 6), hx("cfe6ff"))
    return img


def chess_board():
    img = new()
    for y in range(16):
        for x in range(16):
            light = ((x // 2) + (y // 2)) % 2 == 0
            c = hx("e8dcc0") if light else hx("2a1e18")
            img.putpixel((x, y), shade(c, 1.0 if (x % 2 == 0 or y % 2 == 0) else 0.94))
    return img


def owl_bars():
    """Brass bars of the owl cage, cut out between them."""
    img = new()
    for y in range(16):
        for x in range(16):
            if x % 3 == 0 or y in (0, 15):
                img.putpixel((x, y), shade(hx("c8963c"), 0.85 + 0.15 * ((y % 4) / 3)))
    return img


def owl_feathers():
    img = new()
    rng = random.Random("owl")
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(hx("f4f2ee"), 1 + rng.uniform(-0.05, 0.02)))
    for _ in range(22):
        img.putpixel((rng.randrange(16), rng.randrange(16)), hx("3a3430"))
    return img


def owl_face():
    """Snowy owl face, for the front of the head element (u 6..9, v 7..10)."""
    img = owl_feathers()
    sprite(img, [
        "yy.yy",
        "kY.Yk",
        "..o..",
    ], {"y": hx("f0c020"), "Y": hx("f0c020"), "k": hx("101010"), "o": hx("3a3430")}, 5, 0)
    # Repeat in the middle rows so the auto UV of a centred head finds the eyes.
    sprite(img, [
        "y..y",
        "k..k",
        ".oo.",
    ], {"y": hx("e8b818"), "k": hx("101010"), "o": hx("6a5a48")}, 6, 8)
    return img


def trunk_front():
    """School trunk front as seen: body rows 8..15 (y 0..8), lid rows 5..7 (y 8..11)."""
    img = new()
    rng = random.Random("trunk")
    leather = hx("5a2e1a")
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(leather, 1 + rng.uniform(-0.07, 0.07)))
    brass_c, brass_d = hx("d4a840"), hx("8a6420")
    for x in range(16):  # trim bands at lid seam and base
        img.putpixel((x, 5), brass_d)
        img.putpixel((x, 8), brass_c)
        img.putpixel((x, 15), brass_d)
    for y in range(5, 16):  # two straps
        for x in (4, 11):
            img.putpixel((x, y), hx("2a1a10"))
            img.putpixel((x + 1, y), hx("3a2416"))
    for (x, y) in ((1, 6), (14, 6), (1, 14), (14, 14)):  # corner caps
        img.putpixel((x, y), brass_c)
    return img


def trunk_top():
    img = new()
    rng = random.Random("trunktop")
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(hx("5a2e1a"), 1 + rng.uniform(-0.07, 0.07)))
    for y in range(16):
        for x in (4, 11):
            img.putpixel((x, y), hx("2a1a10"))
            img.putpixel((x + 1, y), hx("3a2416"))
    for x in range(16):
        img.putpixel((x, 3), hx("8a6420"))
        img.putpixel((x, 12), hx("8a6420"))
    return img


def stone(seed, base=hx("a8a294")):
    img = new()
    noise_fill(img, base, 0.07, seed)
    return img


def pensieve_side():
    img = stone("pside")
    # A band of carved runes around the basin.
    runes = [
        "X.X..XX..X.X..XX",
        "XX...X..XXX..X..",
        "X.X..XX..X...XX.",
    ]
    # The basin side shows rows 2..6 (y 9..14), so the band sits there.
    sprite(img, runes, {"X": hx("6a6458")}, 0, 3)
    for x in range(16):
        img.putpixel((x, 2), hx("8a8478"))
        img.putpixel((x, 6), hx("8a8478"))
    return img


def pensieve_top():
    """Stone rim with a silver, swirling memory pool inside."""
    img = stone("ptop")
    for y in range(2, 14):
        for x in range(2, 14):
            # A two-arm swirl in silver and pale blue.
            import math
            dx, dy = x - 7.5, y - 7.5
            r = (dx * dx + dy * dy) ** 0.5
            a = math.atan2(dy, dx)
            s = math.sin(a * 2 + r * 0.9)
            c = mix(hx("9fb6c8"), hx("f4f8ff"), (s + 1) / 2)
            img.putpixel((x, y), c)
    for i in range(2, 14):
        for (x, y) in ((i, 2), (i, 13), (2, i), (13, i)):
            img.putpixel((x, y), hx("6e6a60"))
    return img


def open_book():
    img = new()
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), hx("6a1a1a"))  # cover edge
    for y in range(1, 15):
        for x in range(1, 15):
            img.putpixel((x, y), hx("efe4c8") if x != 7 and x != 8 else hx("c8b890"))
    for y in range(3, 13, 2):
        for x in list(range(2, 6)) + list(range(10, 14)):
            img.putpixel((x, y), hx("9a8a70"))
    return img


# ---- remakes of the older decorations ------------------------------------------------------------

import math


def flame_frames(colors, seed, frames=16):
    """Looping animated flames, one 16 x 16 frame under the other. colors run hot core -> cool tip."""
    rng = random.Random(seed)
    pal = [hx(c) for c in colors]
    phase = [rng.uniform(0, 2 * math.pi) for _ in range(16)]
    speed = [rng.choice((1, 2, 3)) for _ in range(16)]
    img = new(16, 16 * frames)
    for f in range(frames):
        t = 2 * math.pi * f / frames
        for x in range(16):
            # Taller in the middle, flickering per column, looping cleanly over the frames.
            profile = 1 - abs(x - 7.5) / 9.0
            h = 15 * profile * (0.72 + 0.28 * math.sin(t * speed[x] + phase[x]))
            for y in range(16):
                above = 15 - y  # height above the bottom row
                if above > h:
                    continue
                r = above / max(h, 1)  # 0 at the base, 1 at the tip
                core = abs(x - 7.5) / 8.0
                k = min(len(pal) - 1, int((r * 0.75 + core * 0.45) * len(pal)))
                img.putpixel((x, f * 16 + y), pal[k])
        # A spark or two drifting up.
        for s in range(2):
            sx = (3 + s * 7 + f) % 14 + 1
            sy = 15 - ((f * 2 + s * 9) % 16)
            if img.getpixel((sx, f * 16 + sy))[3] == 0:
                img.putpixel((sx, f * 16 + sy), pal[1])
    return img


def bubbling_potion(frames=8):
    """Green potion surface: a swirl that turns and bubbles that rise and pop."""
    img = new(16, 16 * frames)
    base, light, dark = hx("2fae48"), hx("7ef08a"), hx("1a7030")
    bubbles = [(3, 4, 0), (10, 3, 2), (6, 11, 4), (12, 10, 6), (8, 7, 3)]
    for f in range(frames):
        for y in range(16):
            for x in range(16):
                a = math.atan2(y - 7.5, x - 7.5) + 2 * math.pi * f / frames
                r = math.hypot(x - 7.5, y - 7.5)
                s = math.sin(a * 2 + r * 0.8)
                img.putpixel((x, f * 16 + y), mix(dark, base, (s + 1) / 2) if s < 0.6 else mix(base, light, s - 0.6))
        for (bx, by, start) in bubbles:
            age = (f - start) % frames
            if age < 3:
                size = age  # 0, 1, 2: grow then pop
                for dx in range(-size, size + 1):
                    for dy in range(-size, size + 1):
                        if abs(dx) + abs(dy) == size:
                            img.putpixel(((bx + dx) % 16, f * 16 + (by + dy) % 16), light)
            elif age == 3:
                img.putpixel((bx, f * 16 + by), hx("d8ffd8"))
    return img


def cast_iron():
    img = new()
    rng = random.Random("castiron")
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(hx("34343a"), 1 + rng.uniform(-0.12, 0.08)))
    return img


BOOK_COLORS = {"red": "7a1a1e", "blue": "1e3a78", "green": "1e5a32", "brown": "5a3a20"}


def book_cover(color):
    c = hx(BOOK_COLORS[color])
    img = new()
    rng = random.Random("cover" + color)
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(c, 1 + rng.uniform(-0.06, 0.06)))
    gold = hx("d8a838")
    for i in range(3):  # gold corner pieces
        for (x, y) in ((i, 0), (0, i), (15 - i, 0), (15, i), (i, 15), (0, 15 - i), (15 - i, 15), (15, 15 - i)):
            img.putpixel((x, y), gold)
    for (x, y) in ((7, 6), (8, 6), (6, 7), (9, 7), (6, 8), (9, 8), (7, 9), (8, 9)):  # a small gold seal
        img.putpixel((x, y), gold)
    return img


def book_pages(color):
    """Rows 6 and 9 are the covers seen edge-on, rows 7 and 8 the page block. Models use UV rows 6..10."""
    img = new()
    c = hx(BOOK_COLORS[color])
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), hx("ece0c0") if y % 2 else hx("d8caa4"))
    for x in range(16):
        img.putpixel((x, 6), c)
        img.putpixel((x, 9), shade(c, 0.8))
    return img


def book_spine(color):
    c = hx(BOOK_COLORS[color])
    img = new()
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(c, 0.9 + 0.1 * (x % 3 == 0)))
    for x in (3, 4, 11, 12):  # gold bands across the spine
        for y in range(16):
            img.putpixel((x, y), hx("d8a838"))
    return img


def leather_wrap():
    img = new()
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), hx("6a4226") if (x + y) % 4 < 2 else hx("8a5a34"))
    return img


def fireplace_stone():
    """Warm grey cut stone in 8 x 4 courses."""
    img = new()
    rng = random.Random("hearth")
    for y in range(16):
        for x in range(16):
            row = y // 4
            off = 4 if row % 2 else 0
            mortar = y % 4 == 3 or (x + off) % 8 == 7
            if mortar:
                img.putpixel((x, y), hx("5e5850"))
            else:
                img.putpixel((x, y), shade(hx("9a9286"), 1 + rng.uniform(-0.08, 0.06) - (0.06 if y % 4 == 2 else 0)))
    return img


def fireplace_soot():
    img = fireplace_stone()
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(img.getpixel((x, y)), 0.35 + 0.02 * y))
    return img


def floo_pot():
    img = new()
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), hx("c8b8a0") if y % 5 else hx("2e8a48"))
    return img


# ---- Moving Portrait: five frames, a wizard who blinks, glances and waves --------------------------

PORTRAIT_IDLE = [
    "bbbbbPbbbbbb",
    "bbbbPPpbbbbb",
    "bbbPPsPpbbbb",
    "bbPPPPPPpbbb",
    "bPPPPPPPPpbo",
    "bbbFFFFFbbbc",
    "bbbFkFkFbbbc",
    "bbbFFfFFbbbc",
    "bbWWWWWWWbbc",
    "bRWWWWWWWRbt",
    "RRRWWWWWRRRb",
    "RRRRWWWRRRRR",
]


def _portrait_frame(rows, flame):
    img = new()
    gold, dark, hi = hx("e0b040"), hx("8a6420"), hx("f8dc70")
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                img.putpixel((x, y), dark)
            elif edge == 1:
                img.putpixel((x, y), hi if (x + y) % 5 == 0 else gold)
    pal = {"b": hx("1e3a34"), "P": hx("5a2a7a"), "p": hx("3e1a5a"), "s": hx("f0d040"),
           "F": hx("e8c49a"), "f": hx("c89a70"), "k": hx("202020"), "W": hx("eeeeee"),
           "R": hx("7a1a2a"), "o": hx(flame), "c": hx("efe4c8"), "t": hx("8a6420")}
    sprite(img, rows, pal, 2, 2)
    # Soft background texture.
    for (x, y) in ((3, 3), (11, 9), (2, 8), (12, 3)):
        if img.getpixel((x, y)) == pal["b"]:
            img.putpixel((x, y), hx("16302a"))
    return img


def moving_portrait():
    def edit(changes):
        rows = [list(r) for r in PORTRAIT_IDLE]
        for (x, y, ch) in changes:
            rows[y][x] = ch
        return ["".join(r) for r in rows]

    blink = edit([(4, 6, "f"), (6, 6, "f")])
    glance = edit([(4, 6, "F"), (3, 6, "k"), (6, 6, "F"), (5, 6, "k")])
    wave_a = edit([(9, 3, "F"), (10, 3, "F"), (9, 4, "F"), (9, 5, "R"), (9, 6, "R"), (9, 7, "R")])
    wave_b = edit([(10, 2, "F"), (9, 3, "F"), (10, 3, "F"), (9, 4, "R"), (9, 5, "R"), (9, 6, "R"), (9, 7, "R")])
    frames = [_portrait_frame(PORTRAIT_IDLE, "ffb020"), _portrait_frame(blink, "ffd040"),
              _portrait_frame(glance, "ffb020"), _portrait_frame(wave_a, "ffd040"),
              _portrait_frame(wave_b, "ffb020")]
    img = new(16, 16 * len(frames))
    for i, fr in enumerate(frames):
        img.alpha_composite(fr, (0, 16 * i))
    return img


PORTRAIT_ANIMATION = {"animation": {"frametime": 4, "frames": [
    {"index": 0, "time": 60}, {"index": 1, "time": 3}, {"index": 0, "time": 40},
    {"index": 2, "time": 30}, {"index": 0, "time": 25}, {"index": 1, "time": 3},
    {"index": 0, "time": 50}, {"index": 3, "time": 6}, {"index": 4, "time": 6},
    {"index": 3, "time": 6}, {"index": 4, "time": 6}, {"index": 3, "time": 6}, {"index": 0, "time": 40}]}}


def house_banner_block(house):
    """Full-block house cloth for the wool building blocks, with the same emblem as the banners."""
    h = HOUSES[house]
    img = new()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                c = h["acc_dark"]
            elif edge == 1:
                c = h["acc"]
            elif (x + y) % 7 == 0:
                c = h["light"]
            else:
                c = h["main"]
            img.putpixel((x, y), c)
    pal = {"X": h["acc"], "o": h["acc_dark"]}
    if house == "hufflepuff":
        pal = {"X": h["acc"], "o": hx("5a5048")}
    sprite(img, EMBLEMS[house], pal, 4, 3)
    return img


# ---- workbenches ----------------------------------------------------------------------------------

WAND_BOX_COLORS = ["3a2418", "5a1a22", "1e2a4a", "4a4440", "2a3a2a", "6a4a2a", "222226"]


def wand_box_wall():
    """Ollivanders-style wall of wand-box ends: 4 x 3 px boxes with a pale label, dark gaps."""
    img = new(16, 16, hx("1a120c"))
    rng = random.Random("boxwall")
    for row in range(4):
        y0 = row * 4
        off = 2 if row % 2 else 0
        for col in range(-1, 4):
            x0 = col * 5 + off
            c = hx(rng.choice(WAND_BOX_COLORS))
            for y in range(y0, y0 + 3):
                for x in range(x0, x0 + 4):
                    if 0 <= x < 16:
                        img.putpixel((x, y), shade(c, 1.15 if y == y0 else 1.0))
            lx = x0 + 1
            if 0 <= lx < 16:
                img.putpixel((lx, y0 + 1), hx("d8ccb0"))
            if 0 <= lx + 1 < 16:
                img.putpixel((lx + 1, y0 + 1), hx("c8b898"))
    return img


def wand_box(color, seed):
    """Long side of a slim wand box: dark card with a gold band near one end."""
    img = new()
    rng = random.Random(seed)
    c = hx(color)
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(c, 1 + rng.uniform(-0.05, 0.05)))
    for y in range(16):
        img.putpixel((3, y), hx("c8963c"))
        img.putpixel((12, y), hx("c8963c"))
    return img


def phoenix_feather():
    img = new()
    for y in range(16):
        for x in range(16):
            t = y / 15
            img.putpixel((x, y), mix(hx("ffd040"), hx("c8201a"), t))
    for y in range(16):
        img.putpixel((7, y), hx("ffe8a0"))
    return img


def twine_spool():
    img = new()
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), hx("c8a870") if (x + y // 2) % 3 else hx("9a7a48"))
    return img
