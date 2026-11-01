"""Worn textures for the film school robes, the house scarves and the first-year pointed hat.

    python tools/armor_textures/house_cosmetics.py [--preview out.png]

Writes textures/models/armor/<house>_robes.png (128x128), <house>_scarf.png (128x64) and
pointed_hat.png (128x64). Each is drawn at twice the box-UV density of its model
(HouseRobeModel / HouseScarfModel / PointedHatModel lay out 64x64 / 64x32), so a model unit is
2x2 pixels. The UV boxes below must match the models' texOffs and box sizes exactly.

The robe follows the films: black, open down the front over a grey V-neck jumper with a
two-stripe house trim, white collar and striped tie; hood lined in the house colour, crest on
the left breast. --preview renders front/back/side orthographic views over a stand-in body.
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "textures", "models", "armor")
S = 2  # pixels per model unit


def hexc(c):
    c = c.lstrip("#")
    return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


# --------------------------------------------------------------------------- palettes
# Robe black is cool and never pure black: a pure #000 robe reads as a hole in the world.
ROBE = {"x": "#0a0a0d", "k": "#111115", "r": "#18181d", "R": "#202027", "L": "#2a2a33", "H": "#383844"}
SHIRT = {"w": "#eeeee8", "s": "#c9c9c2"}
JUMPER = {"g": "#45454b", "j": "#55555b", "J": "#67676d", "G": "#7a7a80"}
TROUSER = {"D": "#2c2c31", "d": "#222226"}

# P/p/Q = house colour mid/dark/light, A/a/B = second colour mid/dark/light (scarf, tie, trim),
# M/m/N = crest metal mid/dark/light, E = the crest animal.
HOUSES = {
    "gryffindor": dict(P="#8e1f1d", p="#5e1214", Q="#b3332b", A="#c99a26", a="#8f6a14", B="#ecc451",
                       M="#c99a26", m="#7d5b12", N="#f0cd62", E="#e2b33c"),
    "slytherin": dict(P="#1d5a33", p="#103a21", Q="#2b7845", A="#9aa0a6", a="#686d74", B="#d0d4d8",
                      M="#a7adb3", m="#5d6268", N="#dde1e4", E="#c4c9ce"),
    "ravenclaw": dict(P="#1f3f7c", p="#132854", Q="#2f58a0", A="#8e9399", a="#5f646b", B="#c0c4c8",
                      M="#a5702e", m="#673f16", N="#d29e58", E="#c48e46"),
    "hufflepuff": dict(P="#d4a41c", p="#94700c", Q="#f0c63a", A="#1c1c1f", a="#0f0f11", B="#3a3a3f",
                       M="#26262a", m="#121214", N="#45454b", E="#1c1c1f"),
}


def palette(house):
    pal = {}
    for d in (ROBE, SHIRT, JUMPER, TROUSER, HOUSES[house]):
        pal.update({k: hexc(v) for k, v in d.items()})
    return pal


# --------------------------------------------------------------------------- box UV
def faces(u, v, w, h, d):
    """Vanilla box UV in model units: right is the -x side (the wearer's right)."""
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }


def put(img, rect, grid, pal):
    """Paint a character grid into a face rect (model units); the grid is at pixel resolution."""
    x0, y0 = rect[0] * S, rect[1] * S
    rows = [r for r in grid.strip("\n").split("\n")]
    assert len(rows) == rect[3] * S and all(len(r) == rect[2] * S for r in rows), (rect, [len(r) for r in rows])
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x0 + x, y0 + y), pal[ch])


def cloth(w, h, folds, hem=True, top=None):
    """A black-cloth face, w x h pixels: vertical folds given as {column: (light, shadow)} pairs.

    Folds are long and break up near the top, where the cloth is held flat across the shoulders.
    """
    rows = []
    for y in range(h):
        row = []
        for x in range(w):
            c = "r"
            if x in folds:
                kind = folds[x]
                start = 0 if kind in "kx" else 3
                if y >= start:
                    c = kind
            row.append(c)
        rows.append(row)
    if hem:
        rows[h - 1] = ["k"] * w
        rows[h - 2] = ["L" if ch != "x" else "x" for ch in rows[h - 2]]
    if top:
        rows[0] = list(top) if len(top) == w else rows[0]
    return "\n".join("".join(r) for r in rows)


# --------------------------------------------------------------------------- robe
# Torso front, 16 x 24: robe panels in columns 0-3 and 12-15, the uniform between them. The V-neck
# trim is two stripes (house colour outside, second colour inside) closing over the tie at row 4;
# the crest (rim M/m/N, field P, animal E) sits on the left breast.
OPENING = [  # columns 3-12 while the lapels fall open across the chest, then columns 4-11
    "PAwwPpwwAP", "PAswPAwsAP", "JPAsAPsAPJ", "JPAwPAwAPJ", "jJPAAPAPJj", "jJPAPAAPJj",
    "JJPAAPJJ", "JJPAAPJJ", "jJJPPJJj",
] + ["jGJJJJJj"] * 4 + ["jJJJJJJj"] * 5 + ["jgjgjgjg"] * 2 + ["DDDDDDDD", "DDDDDDDD", "DdDDDDdD", "DdDDDDdD"]
RIGHT_PANEL = ["rrp"] + ["rLx"] * 5 + ["rLrx"] * 18
LEFT_PANEL = ["prr"] + ["HLr"] * 5 + ["HrLr", "NMMm", "MPEm", "MEEm", "MPPm", "rMmr"] + ["HrLr"] * 12
TORSO_FRONT = "\n".join(r + o + l for r, o, l in zip(RIGHT_PANEL, OPENING, LEFT_PANEL))

# Torso top, 16 x 8, back edge first: the hood lining rises round the back and sides of the neck
# and runs out to the neckline at the front.
TORSO_TOP = """
rrrrrrrrrrrrrrrr
rrpPPPPPPPPPPprr
rrQPPPPPPPPPPQrr
rrpPpppppppPpPrr
rrpPrrrrrrrrpPrr
rrpPrrrrrrrrpPrr
rrpPrrrrrrrrpPrr
rrpPkkkkkkkkPprr
"""

def robe(house):
    pal = palette(house)
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))

    # Torso 8x12x4 at (16, 16).
    t = faces(16, 16, 8, 12, 4)
    put(img, t["front"], TORSO_FRONT, pal)
    put(img, t["back"], cloth(16, 24, {3: "L", 4: "k", 7: "x", 8: "R", 11: "L", 12: "k"}, hem=False), pal)
    put(img, t["right"], cloth(8, 24, {2: "L", 5: "k"}, hem=False), pal)
    put(img, t["left"], cloth(8, 24, {2: "L", 5: "k"}, hem=False), pal)
    # Shoulders: the lining of the folded hood rises round the back of the neck.
    put(img, t["top"], TORSO_TOP, pal)
    put(img, t["bottom"], "\n".join(["k" * 16] * 8), pal)

    # Hood 7x5x2 at (0, 52): folded down, so its lining shows as a point down the back.
    h = faces(0, 52, 7, 5, 2)
    put(img, h["top"], """
QQQQQQQQQQQQQQ
Pppppppppppppp
pPPPPPPPPPPPPp
PPPPPPPPPPPPPP
""", pal)
    put(img, h["back"], """
QQQQQQQQQQQQQQ
PPPPPPPPPPPPPP
rpPPPPPPPPPPpr
RrpPPPPPPPPprR
rLrpPPPPPPprLr
rLrrpPPPPprrLr
rLrrrpPPprrrLr
rrrrrrpPprrrrr
krrrrrrprrrrrk
kkkkkkkkkkkkkk
""", pal)
    side = """
PPPP
pppp
rrrr
rLrr
rLrr
rLrr
rrrr
rrrk
krrk
kkkk
"""
    put(img, h["right"], side, pal)
    put(img, h["left"], side, pal)
    put(img, h["front"], "\n".join(["k" * 14] * 10), pal)
    put(img, h["bottom"], "\n".join(["x" * 14] * 4), pal)

    # Sleeves 4x8x4 at (40, 16), mirrored for the left arm: plain long folds.
    a = faces(40, 16, 4, 8, 4)
    put(img, a["top"], "\n".join(["RRRRRRRR"] * 8), pal)
    put(img, a["bottom"], "\n".join(["kkkkkkkk"] * 8), pal)
    for f, folds in (("front", {2: "L", 6: "k"}), ("back", {1: "L", 5: "k"}),
                     ("right", {3: "L", 6: "k"}), ("left", {2: "L", 5: "k"})):
        put(img, a[f], cloth(8, 16, folds, hem=False), pal)

    # Bell cuff 4x4x4 at (0, 32): flares over the wrist, open underneath.
    c = faces(0, 32, 4, 4, 4)
    for f in ("front", "back", "right", "left"):
        put(img, c[f], cloth(8, 8, {2: "L", 5: "k"}, hem=True), pal)
    put(img, c["top"], "\n".join(["rrrrrrrr"] * 8), pal)
    put(img, c["bottom"], """
kkkkkkkk
kxxxxxxk
kx....xk
kx....xk
kx....xk
kx....xk
kxxxxxxk
kkkkkkkk
""".replace(".", "x"), pal)

    # Skirt panels. Front: 4x10x1 at (0, 40) and (10, 40); the inner edges are the open front.
    fr = faces(0, 40, 4, 10, 1)
    fl = faces(10, 40, 4, 10, 1)
    put(img, fr["front"], cloth(8, 20, {1: "L", 4: "k", 7: "x"}), pal)
    put(img, fl["front"], cloth(8, 20, {0: "H", 3: "L", 6: "k"}), pal)
    for p in (fr, fl):
        put(img, p["back"], "\n".join(["x" * 8] * 20), pal)
        for f in ("right", "left"):
            put(img, p[f], "\n".join(["k" * 2] * 20), pal)
        put(img, p["top"], "\n".join(["r" * 8] * 2), pal)
        put(img, p["bottom"], "\n".join(["k" * 8] * 2), pal)
    # Back: 8x10x1 at (20, 40), one deep box pleat down the middle.
    b = faces(20, 40, 8, 10, 1)
    put(img, b["back"], cloth(16, 20, {2: "L", 5: "k", 7: "x", 8: "L", 10: "k", 13: "L"}), pal)
    put(img, b["front"], "\n".join(["x" * 16] * 20), pal)
    for f in ("right", "left"):
        put(img, b[f], "\n".join(["k" * 2] * 20), pal)
    put(img, b["top"], "\n".join(["r" * 16] * 2), pal)
    put(img, b["bottom"], "\n".join(["k" * 16] * 2), pal)
    # Sides: 1x10x4 at (38, 40) and (48, 40).
    for u in (38, 48):
        s = faces(u, 40, 1, 10, 4)
        for f in ("right", "left"):
            put(img, s[f], cloth(8, 20, {2: "L", 5: "k"}), pal)
        for f in ("front", "back"):
            put(img, s[f], "\n".join(["k" * 2] * 20), pal)
        put(img, s["top"], "\n".join(["r" * 2] * 8), pal)
        put(img, s["bottom"], "\n".join(["k" * 2] * 8), pal)
    return img


# --------------------------------------------------------------------------- scarf
def stripes(w, h, period, band, horizontal):
    """Knitted stripes: wide house-colour bands split by narrower second-colour bands.

    Each band is lit on its first row or column and shaded on its last, so the knit reads as ridges.
    """
    rows = []
    for y in range(h):
        row = ""
        for x in range(w):
            i = (y if horizontal else x) % period
            if i < band:
                row += "Q" if i == 0 else ("p" if i == band - 1 else "P")
            else:
                row += "B" if i == band else ("a" if i == period - 1 else "A")
        rows.append(row)
    return rows


def scarf(house):
    pal = palette(house)
    img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    # The wrap 8x2x4 at (0, 0): stripes run round the neck.
    wf = faces(0, 0, 8, 2, 4)
    for f in ("front", "back", "right", "left"):
        w = wf[f][2] * S
        put(img, wf[f], "\n".join(stripes(w, 4, 8, 5, False)), pal)
    put(img, wf["top"], "\n".join(stripes(16, 8, 8, 5, False)), pal)
    put(img, wf["bottom"], "\n".join(stripes(16, 8, 8, 5, False)), pal)
    # The ends 2x9x1 at (24, 0) and 2x7x1 at (30, 0): stripes across, fringe at the bottom.
    for u, h in ((24, 9), (30, 7)):
        ef = faces(u, 0, 2, h, 1)
        for f in ("front", "back", "right", "left"):
            w = ef[f][2] * S
            rows = stripes(w, h * S, 8, 5, True)
            rows[-2] = "".join("A" if x % 2 == 0 else "P" for x in range(w))
            rows[-1] = "".join("a" if x % 2 == 0 else "." for x in range(w))
            put(img, ef[f], "\n".join(rows), pal)
        put(img, ef["top"], "\n".join(["P" * 4] * 2), pal)
        put(img, ef["bottom"], "\n".join(["a" * 4] * 2), pal)
    return img


# --------------------------------------------------------------------------- pointed hat
HAT = {"x": "#0b0b0e", "k": "#131317", "r": "#1b1b21", "R": "#24242c", "L": "#30303a", "H": "#40404c"}


def felt(w, h, light_col, shade_col):
    rows = []
    for y in range(h):
        row = ""
        for x in range(w):
            c = "r"
            if x == light_col:
                c = "L"
            elif x == shade_col:
                c = "k"
            if y == h - 1:
                c = "k" if c != "L" else "R"
            row += c
        rows.append(row)
    return "\n".join(rows)


# (u, v, w, h, d) of each cone tier, base first: PointedHatModel's TIER_UV and TIER_WIDTHS.
HAT_TIERS = ((0, 11, 8, 3, 8), (32, 11, 7, 2, 7), (0, 21, 6, 2, 6), (24, 21, 5, 2, 5),
             (44, 21, 4, 2, 4), (40, 0, 3, 2, 3), (52, 0, 2, 2, 2))


def pointed_hat():
    pal = {k: hexc(v) for k, v in HAT.items()}
    img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    # Brim 10x1x10 at (0, 0).
    b = faces(0, 0, 10, 1, 10)
    top = []
    for y in range(20):
        row = ""
        for x in range(20):
            edge = x in (0, 19) or y in (0, 19)
            row += "H" if edge and (x < 10 or y < 10) else ("k" if edge else ("R" if (x + y) % 7 == 0 else "r"))
        top.append(row)
    put(img, b["top"], "\n".join(top), pal)
    put(img, b["bottom"], "\n".join(["k" * 20] * 20), pal)
    for f in ("front", "back", "right", "left"):
        put(img, b[f], "\n".join(["L" * 20, "k" * 20]), pal)
    # Cone tiers.
    for (u, v, w, h, d) in HAT_TIERS:
        c = faces(u, v, w, h, d)
        for f in ("front", "back"):
            put(img, c[f], felt(w * S, h * S, 1, w * S - 2), pal)
        for f in ("right", "left"):
            put(img, c[f], felt(d * S, h * S, 1, d * S - 2), pal)
        put(img, c["top"], "\n".join(["R" * (w * S)] * (d * S)), pal)
        put(img, c["bottom"], "\n".join(["x" * (w * S)] * (d * S)), pal)
    # A darker band round the base of the crown, where the felt is stitched to the brim.
    c = faces(0, 11, 8, 3, 8)
    for f in ("front", "back", "right", "left"):
        x0, y0, w, h = c[f]
        for x in range(w * S):
            img.putpixel((x0 * S + x, y0 * S + h * S - 2), pal["x"])
    return img


# --------------------------------------------------------------------------- preview
def preview(robes, scarves, hat, path):
    """Orthographic front / back / right views of the robe at rest over a stand-in body."""
    scale = 6
    body = [  # (x0,x1,y0,y1,z0,z1, colour) in model units, absolute
        (-4, 4, -8, 0, -4, 4, (196, 150, 118)), (-4, 4, 0, 12, -2, 2, (40, 160, 170)),
        (-8, -4, 0, 12, -2, 2, (196, 150, 118)), (4, 8, 0, 12, -2, 2, (196, 150, 118)),
        (-4, 0, 12, 24, -2, 2, (50, 60, 140)), (0, 4, 12, 24, -2, 2, (50, 60, 140)),
    ]
    g = 0.6
    robe_boxes = [  # (x0,x1,y0,y1,z0,z1, u,v,w,h,d, mirrored)
        (-4 - g, 4 + g, -g, 12 + g, -2 - g, 2 + g, 16, 16, 8, 12, 4, False),
        (-3.5, 3.5, -0.4, 4.6, 2.4, 4.4, 0, 52, 7, 5, 2, False),
        (-4.55, -0.75, 12, 22, -2.65, -1.65, 0, 40, 4, 10, 1, False),
        (0.75, 4.55, 12, 22, -2.65, -1.65, 10, 40, 4, 10, 1, False),
        (-4.6, 4.6, 12, 22, 1.65, 2.65, 20, 40, 8, 10, 1, False),
        (-4.65, -3.65, 12, 22, -2.6, 2.6, 38, 40, 1, 10, 4, False),
        (3.65, 4.65, 12, 22, -2.6, 2.6, 48, 40, 1, 10, 4, False),
        (-8 - g, -4 + g, -g, 8 + g, -2 - g, 2 + g, 40, 16, 4, 8, 4, False),
        (-8.9, -3.1, 5.1, 10.9, -2.9, 2.9, 0, 32, 4, 4, 4, False),
        (4 - g, 8 + g, -g, 8 + g, -2 - g, 2 + g, 40, 16, 4, 8, 4, True),
        (3.1, 8.9, 5.1, 10.9, -2.9, 2.9, 0, 32, 4, 4, 4, True),
    ]
    scarf_boxes = [
        (-4.75, 4.75, -1.0, 2.5, -2.75, 2.75, 0, 0, 8, 2, 4, False),
        (-3.5, -1.1, 2.0, 11.4, -3.8, -2.4, 24, 0, 2, 9, 1, False),
        (-1.6, 0.8, 2.2, 9.6, -4.2, -2.8, 30, 0, 2, 7, 1, False),
    ]
    hat_boxes = [(-5.5, 5.5, -8.1, -7.1, -5.5, 5.5, 0, 0, 10, 1, 10, False)]
    top, z = -6.9, 0.2
    for i, (u, v, w, h, d) in enumerate(HAT_TIERS):  # the bend is left out: a straight cone
        g = 0.3 if i == 0 else 0.0
        hat_boxes.append((-w / 2 - g, w / 2 + g, top - h - g, top + g, z - d / 2 - g, z + d / 2 + g, u, v, w, h, d, False))
        top -= h
        z += 0.15

    def view(tex_sets, mode):
        W, H = 20 * scale, 44 * scale
        im = Image.new("RGBA", (W, H), (120, 140, 160, 255))
        quads = []
        for (x0, x1, y0, y1, z0, z1, c) in body:
            quads.append((x0, x1, y0, y1, z0, z1, None, None, c, False))
        for tex, boxes in tex_sets:
            for bx in boxes:
                quads.append(bx[:6] + (tex, bx[6:11], None, bx[11]))

        def distance(q):
            x0, x1, y0, y1, z0, z1 = q[:6]
            return {"front": z0, "back": -z1, "right": x0}[mode]
        for q in sorted(quads, key=distance, reverse=True):
            x0, x1, y0, y1, z0, z1, tex, uv, col, mir = q
            if mode == "front":
                a0, a1, face = x0, x1, "front"
            elif mode == "back":
                a0, a1, face = -x1, -x0, "back"
            else:
                a0, a1, face = -z1, -z0, "right"
            px0, px1 = int((a0 + 10) * scale), int((a1 + 10) * scale)
            py0, py1 = int((y0 + 20) * scale), int((y1 + 20) * scale)
            if px1 <= px0 or py1 <= py0:
                continue
            if tex is None:
                patch = Image.new("RGBA", (px1 - px0, py1 - py0), col + (255,))
            else:
                f = faces(*uv)
                if mir and face == "right":
                    face = "left"
                u, v, w, h = f[face]
                patch = tex.crop((int(u * S), int(v * S), int((u + w) * S), int((v + h) * S)))
                if mir:
                    patch = patch.transpose(Image.FLIP_LEFT_RIGHT)
                patch = patch.resize((px1 - px0, py1 - py0), Image.NEAREST)
            im.alpha_composite(patch, (px0, py0))
        return im

    houses = list(robes)
    sheet = Image.new("RGBA", (20 * scale * 3 * len(houses) + 10 * len(houses), 44 * scale), (60, 60, 60, 255))
    x = 0
    for i, house in enumerate(houses):
        sets_front = [(robes[house], robe_boxes), (scarves[house], scarf_boxes), (hat, hat_boxes)]
        for mode in ("front", "back", "right"):
            sheet.alpha_composite(view(sets_front if mode != "back" else sets_front, mode), (x, 0))
            x += 20 * scale
        x += 10
    sheet.save(path)


def build_all(preview_path=None):
    os.makedirs(OUT, exist_ok=True)
    robes, scarves = {}, {}
    for house in HOUSES:
        robes[house] = robe(house)
        robes[house].save(os.path.join(OUT, house + "_robes.png"))
        scarves[house] = scarf(house)
        scarves[house].save(os.path.join(OUT, house + "_scarf.png"))
    hat = pointed_hat()
    hat.save(os.path.join(OUT, "pointed_hat.png"))
    if preview_path:
        preview(robes, scarves, hat, preview_path)


if __name__ == "__main__":
    args = sys.argv[1:]
    build_all(args[1] if len(args) > 1 and args[0] == "--preview" else None)
