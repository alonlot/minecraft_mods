"""Proposed replacements for the placeholder block textures.

Several of these blocks use custom element models with hand-written UV rectangles,
so the art has to be painted INSIDE those rectangles or it lands on the wrong face.
The regions are noted per texture and were read out of models/block/*.json.
"""
import os
from pixlib import Canvas, hx, ramp, mix, shade

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "proposed", "block")
os.makedirs(OUT, exist_ok=True)
REG = []


def tex(name, size=16):
    def deco(fn):
        cv = Canvas(size, size, seed=abs(hash(name)) % 9999)
        fn(cv)
        cv.save(os.path.join(OUT, name + ".png"))
        REG.append(name)
        return fn
    return deco


def tile_noise(cv, x0, y0, x1, y1, cols, weights=None, seed_mul=1):
    """Fill a rect with weighted random texels -- wraps cleanly because it is per-texel."""
    import random
    r = random.Random(1234 * seed_mul)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            cv.put(x, y, r.choice(cols))


# =============================================================================
# candle_cluster -- model UVs: candle A sides x4-5 y8-15, top x4-5 y6-7
#                              candle B sides x7-8 y6-15, top x7-8 y4-5
#                              candle C sides x10-11 y9-15, top x10-11 y7-8
@tex("candle_cluster")
def _(cv):
    wax = ramp("6f5f45", "9c8a68", "cbb890", "eadfc2")
    flame = ramp("8a3b06", "e07a12", "ffc040", "fff3c0")
    def candle(x, ytop, ybot, tx, ty):
        # body with a lit left column and a shaded right column
        cv.vline(x, ytop + 2, ybot, wax[2])
        cv.vline(x + 1, ytop + 2, ybot, wax[1])
        cv.put(x, ybot, wax[0]); cv.put(x + 1, ybot, wax[0])
        # wax runs down the side
        cv.put(x, ytop + 4, wax[3]); cv.put(x + 1, ytop + 6, wax[0])
        cv.put(x, ybot - 2, wax[3])
        # flame at the top of the side face
        cv.put(x, ytop, flame[2]); cv.put(x + 1, ytop, flame[1])
        cv.put(x, ytop + 1, flame[3]); cv.put(x + 1, ytop + 1, flame[2])
        # up face: wax rim with a dark wick
        cv.rect(tx, ty, tx + 1, ty + 1, wax[2])
        cv.put(tx, ty, wax[3])
        cv.put(tx + 1, ty + 1, hx("3a2f1c"))
    candle(4, 8, 15, 4, 6)
    candle(7, 6, 15, 7, 4)
    candle(10, 9, 15, 10, 7)


# enchanted_book_pile -- slab sides x2-13 y12-15 / x3-12 y12-15; slab top x2-13 y3-12;
#                        upper book sides x4-11 y8-11; upper book top x4-11 y4-11
@tex("enchanted_book_pile")
def _(cv):
    lea = [ramp("3a1418", "5e2027", "7d2d35"), ramp("13233f", "1e3a63", "2b5187"),
           ramp("2a1a38", "422a56", "5b3b74")]
    pg = ramp("8f8265", "c3b795", "e6dcc0")
    gold = ramp("6b4a12", "b3861f", "e8c452")
    # --- slab sides: three stacked books seen edge-on
    for i, y in enumerate((12, 13, 14, 15)):
        pass
    # book 1 (bottom two rows), book 2 (top two rows)
    cv.rect(2, 14, 13, 15, lea[0][1])
    cv.hline(2, 13, 14, lea[0][2])
    cv.rect(3, 12, 13, 13, lea[1][1])
    cv.hline(3, 13, 12, lea[1][2])
    # page blocks showing between the covers
    for x in range(3, 13, 1):
        cv.put(x, 15, pg[1] if x % 2 else pg[0])
    for x in range(4, 13):
        cv.put(x, 13, pg[2] if x % 2 else pg[1])
    # --- slab top (x2-13 y3-12): the cover of the top-most book of the pile
    cv.rect(2, 3, 13, 12, lea[2][1])
    cv.frame(2, 3, 13, 12, lea[2][0])
    cv.frame(3, 4, 12, 11, gold[1])
    # --- upper book region (x4-11 y4-11): cover top half, page edges lower half
    cv.rect(4, 4, 11, 7, lea[0][1])
    cv.frame(4, 4, 11, 7, lea[0][0])
    cv.put(5, 5, gold[2]); cv.put(10, 5, gold[2])
    cv.put(5, 6, gold[1]); cv.put(10, 6, gold[1])
    # an arcane sigil embossed on the cover
    cv.put(7, 5, gold[2]); cv.put(8, 5, gold[2])
    cv.put(7, 6, gold[2]); cv.put(8, 6, gold[2])
    cv.put(7, 6, hx("7fe8ff"))
    # page edges (also the upper book's sides)
    for x in range(4, 12):
        cv.put(x, 8, pg[2] if x % 2 else pg[1])
        cv.put(x, 9, pg[1] if x % 2 else pg[0])
        cv.put(x, 10, pg[2] if x % 2 else pg[1])
    cv.hline(4, 11, 11, lea[0][0])
    # a clasp
    cv.put(11, 9, gold[2]); cv.put(11, 10, gold[1])


@tex("fine_wood_leaves")
def _(cv):
    """Foliage-TINTED in code, so this has to be a near-neutral light/dark mask.
    Saturated colour here would be multiplied by the biome green and go muddy."""
    import random
    r = random.Random(7)
    base = ramp("6d7358", "838a66", "9aa278", "b3bb8d", "545a44")
    for y in range(16):
        for x in range(16):
            cv.put(x, y, r.choice(base[:4]))
    # leaf clumps: darker cores with a lit upper-left edge
    for cx, cy in ((3, 3), (9, 2), (13, 6), (6, 7), (2, 11), (11, 12), (7, 13), (14, 14)):
        for dx in range(-1, 2):
            for dy in range(-1, 2):
                if abs(dx) + abs(dy) <= 1:
                    cv.put((cx + dx) % 16, (cy + dy) % 16, base[4])
        cv.put((cx - 1) % 16, (cy - 1) % 16, base[3])
    # gaps so the cutout reads as foliage rather than a solid block
    for x, y in ((0, 5), (5, 0), (8, 5), (4, 9), (12, 2), (15, 10), (10, 8), (1, 14), (6, 15), (13, 9)):
        cv.put(x, y, (0, 0, 0, 0))
    cv.noise(0.05)


@tex("fine_wood_sapling")
def _(cv):
    """Wandwood seedling: pale straight stem, a small tight crown."""
    st = ramp("6b4f2c", "9a7845", "c3a066")
    lf = ramp("2f4a2a", "43663a", "5c8a4c", "7cae63")
    cv.vline(7, 8, 15, st[1]); cv.vline(8, 8, 15, st[0])
    cv.put(7, 8, st[2]); cv.put(7, 12, st[2])
    for cx, cy, r_ in ((7, 5, 3), (5, 7, 2), (10, 7, 2)):
        for y in range(16):
            for x in range(16):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r_ * r_:
                    cv.put(x, y, lf[1])
    for x, y in ((6, 3), (8, 3), (5, 5), (9, 5), (7, 2), (4, 7), (11, 7)):
        cv.put(x, y, lf[3])
    for x, y in ((8, 6), (9, 8), (6, 8), (5, 8)):
        cv.put(x, y, lf[0])
    cv.noise(0.07)


# floo_fireplace -- y0-3 is the stone band shared by every wall face;
#                   x1-14 y3-12 is the hearth floor; x0-1 y2-13 is the wall top
@tex("floo_fireplace")
def _(cv):
    st = ramp("2b2b30", "40404a", "585863", "70707c")
    ash = ramp("232227", "37353c", "4d4a52")
    grn = ramp("0d5c3a", "16a86a", "3ff0a0", "b6ffdc")
    # stone band (all wall side faces)
    import random
    r = random.Random(3)
    for y in range(0, 4):
        for x in range(16):
            cv.put(x, y, r.choice([st[1], st[1], st[2], st[0], st[3]]))
    for x in range(0, 16, 4):
        cv.put(x, 1, st[0]); cv.put(x + 2, 3, st[0])
    cv.hline(0, 15, 0, st[3])
    # wall tops
    cv.rect(0, 4, 1, 13, st[2])
    cv.vline(0, 4, 13, st[3])
    # hearth floor: ash bed with embers and floo flame
    for y in range(4, 13):
        for x in range(2, 15):
            cv.put(x, y, r.choice([ash[1], ash[1], ash[0], ash[2]]))
    for x, y in ((5, 7), (9, 6), (7, 10), (11, 9), (4, 10), (12, 6)):
        cv.put(x, y, grn[1])
        cv.put(x, y - 1, grn[2])
    cv.put(8, 8, grn[3]); cv.put(7, 8, grn[2]); cv.put(9, 8, grn[2])
    cv.put(6, 9, grn[0]); cv.put(10, 7, grn[0])
    # charred log ends laid in the bed
    cv.hline(3, 13, 12, hx("1d1b1f"))
    cv.hline(4, 12, 4, hx("22201f"))


@tex("gillyweed_plant")
def _(cv):
    """Cross model: a lake weed with a holdfast, fronds leaning as if in current."""
    g = ramp("16301c", "27512b", "3d7a3c", "62a755", "8ecb6b")
    cv.rect(6, 14, 9, 15, g[0])
    cv.hline(5, 10, 14, g[1])
    fronds = [(7, 14, 2, 4), (8, 14, 5, 1), (8, 14, 8, 0), (8, 14, 11, 2),
              (8, 14, 13, 5), (7, 14, 4, 7), (8, 14, 10, 6)]
    for x0, y0, x1, y1 in fronds:
        cv.line(x0, y0, x1, y1, g[2])
        cv.line(x0, y0 - 2, x1, y1 + 1, g[1])
    for x, y in ((2, 4), (5, 1), (8, 0), (11, 2), (13, 5), (4, 7), (10, 6)):
        cv.put(x, y, g[4])
        cv.put(x, y + 1, g[3])
    # a few air bladders
    for x, y in ((6, 8), (10, 9), (4, 10)):
        cv.put(x, y, g[3])
    cv.noise(0.08)


@tex("willow_sapling")
def _(cv):
    """Cross model: a young willow, already weeping."""
    st = ramp("3a2a1a", "55402a", "6f5638")
    lf = ramp("2b3d1e", "3d5629", "537437", "6d9448")
    cv.vline(7, 9, 15, st[1]); cv.vline(8, 9, 15, st[0])
    cv.put(7, 11, st[2])
    # crown
    for y in range(16):
        for x in range(16):
            if (x - 7.5) ** 2 * 0.6 + (y - 5) ** 2 <= 12:
                cv.put(x, y, lf[1])
    # weeping strands hanging off the crown
    for x, top, bot in ((3, 6, 11), (5, 7, 13), (10, 7, 12), (12, 6, 10), (7, 8, 14)):
        cv.vline(x, top, bot, lf[2])
        cv.put(x, bot, lf[0])
    for x, y in ((6, 2), (9, 2), (4, 4), (11, 4), (7, 1)):
        cv.put(x, y, lf[3])
    for x, y in ((8, 6), (5, 6), (10, 6)):
        cv.put(x, y, lf[0])
    cv.noise(0.08)


@tex("moving_portrait")
def _(cv):
    """cube_all: a gilt frame around a dark oil portrait of a seated figure."""
    g = ramp("5c3f10", "8f6a1c", "c39a30", "e8c452")
    bg = ramp("15181f", "1e2430", "2a3444")
    sk = ramp("6b4a3a", "9a7159", "c09a7c")
    # frame
    cv.rect(0, 0, 15, 15, g[1])
    cv.frame(0, 0, 15, 15, g[0])
    cv.frame(1, 1, 14, 14, g[3])
    cv.frame(2, 2, 13, 13, g[2])
    # corner bosses
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        cv.put(x, y, g[3])
    # canvas
    cv.rect(3, 3, 12, 12, bg[1])
    cv.rect(3, 3, 12, 6, bg[2])
    cv.hline(3, 12, 12, bg[0])
    # a figure: head, shoulders, dark robe
    cv.rect(7, 5, 8, 6, sk[1])
    cv.put(7, 5, sk[2]); cv.put(8, 6, sk[0])
    cv.put(6, 5, hx("3a3038")); cv.put(9, 5, hx("3a3038"))   # hair
    cv.poly([(5, 8), (10, 8), (11, 12), (4, 12)], hx("2b2233"))
    cv.hline(6, 9, 8, hx("3d3247"))
    cv.put(7, 9, hx("6f5a8a")); cv.put(8, 10, hx("4a3d5e"))
    # varnish highlight
    cv.put(4, 4, hx("39465c")); cv.put(5, 4, hx("2f3a4c"))


def banner(cv, cloth, sigil, trim):
    """cube_all: a hanging house tapestry with a rod, folds, and a sigil block."""
    cv.rect(0, 0, 15, 15, cloth[1])
    # rod and rings at the top
    cv.hline(0, 15, 0, hx("4a3a22"))
    cv.hline(0, 15, 1, hx("6b5533"))
    for x in (2, 7, 13):
        cv.put(x, 1, hx("b3a06a"))
    # vertical folds
    for x in (3, 8, 12):
        cv.vline(x, 2, 15, cloth[0])
        cv.vline(x + 1, 2, 15, cloth[2])
    # trim border
    cv.vline(0, 2, 15, trim[1]); cv.vline(15, 2, 15, trim[1])
    cv.hline(0, 15, 15, trim[1])
    cv.hline(0, 15, 14, trim[0])
    # sigil panel
    cv.rect(5, 5, 10, 11, cloth[0])
    for (x, y) in sigil:
        cv.put(x, y, trim[2])
    cv.noise(0.05)


# sigils drawn inside the 5..10 x 5..11 panel
LION = [(7, 5), (8, 5), (6, 6), (9, 6), (7, 6), (8, 6), (6, 7), (9, 7),
        (7, 8), (8, 8), (6, 9), (9, 9), (7, 10), (8, 10), (6, 11), (9, 11)]
BADGER = [(6, 6), (9, 6), (7, 6), (8, 6), (6, 7), (9, 7), (7, 8), (8, 8),
          (6, 9), (7, 9), (8, 9), (9, 9), (7, 10), (8, 10), (6, 11), (9, 11)]
EAGLE = [(7, 5), (8, 5), (5, 6), (6, 6), (9, 6), (10, 6), (7, 7), (8, 7),
         (5, 8), (10, 8), (6, 8), (9, 8), (7, 9), (8, 9), (7, 10), (8, 11)]
SNAKE = [(6, 5), (7, 5), (8, 5), (9, 6), (8, 7), (7, 7), (6, 8), (7, 9),
         (8, 9), (9, 10), (8, 11), (7, 11), (6, 11), (9, 5)]


def mkbanner(name, cloth, trim, sigil):
    @tex(name)
    def _(cv, cloth=cloth, trim=trim, sigil=sigil):
        banner(cv, ramp(*cloth), sigil, ramp(*trim))


mkbanner("gryffindor_banner_block", ("5e1219", "7d1a22", "97262f"), ("6b4a12", "b3861f", "e8c452"), LION)
mkbanner("hufflepuff_banner_block", ("8a6a08", "b3890f", "cfa42b"), ("2a2418", "3d3422", "0f0d09"), BADGER)
mkbanner("ravenclaw_banner_block", ("102045", "18305e", "213f76"), ("5c4712", "8f6f1f", "b98d3e"), EAGLE)
mkbanner("slytherin_banner_block", ("0c3a24", "12522f", "1a6b3e"), ("6f757c", "9aa1a9", "c0c6cc"), SNAKE)


# owl_perch -- post sides x0-1 y2-15; bar sides x0-9 y6-7; bar top x0-9 y0-1;
#              post top x7-8 y7-8
@tex("owl_perch")
def _(cv):
    w = ramp("3a2a18", "584022", "755530", "97754a")
    # post: grain running vertically
    cv.rect(0, 2, 1, 15, w[1])
    cv.vline(0, 2, 15, w[2])
    for y in (4, 7, 11, 14):
        cv.put(1, y, w[0])
    cv.put(0, 9, w[3])
    # perch bar side: worn, with talon scratches
    cv.rect(0, 6, 9, 7, w[2])
    cv.hline(0, 9, 6, w[3])
    cv.hline(0, 9, 7, w[1])
    for x in (2, 4, 5, 8):
        cv.put(x, 7, w[0])
    # perch bar top: paler where claws have polished it
    cv.rect(0, 0, 9, 1, w[3])
    cv.hline(0, 9, 1, w[2])
    for x in (1, 3, 6, 8):
        cv.put(x, 0, hx("b8996e"))
    for x in (2, 7):
        cv.put(x, 1, w[1])
    # post end grain
    cv.rect(7, 7, 8, 8, w[1])
    cv.put(7, 7, w[2]); cv.put(8, 8, w[0])


# wizard_cauldron -- base sides x2-13 y0-1; body sides x2-13 y2-9;
#                    rim top x4-11 y4-11 (overlaps body); rim sides x4-11 y0-1
@tex("wizard_cauldron")
def _(cv):
    fe = ramp("14161a", "22262c", "33383f", "474d56")
    brw = ramp("0f2a1c", "1a4a2c", "27713f", "44a35e")
    # base + rim side band
    for x in range(16):
        cv.put(x, 0, fe[2]); cv.put(x, 1, fe[1])
    cv.hline(4, 11, 0, fe[3])
    for x in (5, 8, 10):
        cv.put(x, 1, fe[0])
    # body sides: cast iron, lit at the top, sooted at the bottom
    cv.rect(2, 2, 13, 9, fe[1])
    cv.hline(2, 13, 2, fe[3])
    cv.hline(2, 13, 3, fe[2])
    cv.hline(2, 13, 9, fe[0])
    cv.vline(2, 2, 9, fe[2]); cv.vline(13, 2, 9, fe[0])
    # rivets around the belly
    for x in (4, 7, 11):
        cv.put(x, 5, fe[3]); cv.put(x, 6, fe[0])
    # brew surface in the rim region -- kept dark so it still passes as
    # shadowed iron where the body sides overlap this rectangle
    cv.rect(4, 4, 11, 11, brw[1])
    cv.frame(4, 4, 11, 11, brw[0])
    cv.rect(5, 5, 10, 10, brw[2])
    for x, y in ((6, 6), (9, 7), (7, 9), (8, 5)):
        cv.put(x, y, brw[3])
    cv.put(7, 7, hx("6fd88a")); cv.put(8, 8, hx("6fd88a"))
    cv.put(6, 9, brw[0]); cv.put(10, 6, brw[0])
    cv.noise(0.06)


@tex("log_fine")
def _(cv):
    """Pale wandwood bark: tight vertical grain, a couple of knots."""
    import random
    r = random.Random(11)
    c = ramp("5c421f", "77572c", "8f6d3b", "a8854c", "c0a066")
    for x in range(16):
        col = r.choice([c[1], c[2], c[2], c[3]])
        for y in range(16):
            cv.put(x, y, col if r.random() > .25 else r.choice(c))
    for x in (2, 6, 11, 14):
        cv.vline(x, 0, 15, c[0])
    for x in (4, 9):
        cv.vline(x, 0, 15, c[4])
    # knots
    for cx, cy in ((5, 4), (12, 11)):
        cv.disc(cx, cy, 1.8, c[0])
        cv.put(cx, cy, hx("41301a"))
        cv.put(cx - 1, cy - 1, c[3])
    cv.noise(0.07)


@tex("log_fine_top")
def _(cv):
    """End grain: growth rings around an off-centre heart."""
    c = ramp("5c421f", "77572c", "8f6d3b", "a8854c", "c0a066")
    cv.rect(0, 0, 15, 15, c[1])
    for i, rr in enumerate((7.4, 6.2, 5.0, 3.8, 2.6, 1.4)):
        cv.ring(7.2, 8.0, rr, c[4] if i % 2 else c[0], t=1.0)
    cv.disc(7.2, 8.0, 1.2, c[0])
    cv.put(7, 8, hx("41301a"))
    # a radial check in the wood
    cv.line(7, 8, 13, 3, c[0])
    # squared-off bark edge
    cv.frame(0, 0, 15, 15, c[0])
    cv.noise(0.08)


@tex("planks_fine")
def _(cv):
    """Fine planks: four courses, staggered joints, subtle grain."""
    import random
    r = random.Random(19)
    c = ramp("6b4f2c", "83653a", "9a7845", "b08d55", "c6a56d")
    for y in range(16):
        for x in range(16):
            cv.put(x, y, r.choice([c[2], c[2], c[3], c[1]]))
    for y in (3, 7, 11, 15):
        cv.hline(0, 15, y, c[0])
    # staggered end joints
    for x, y0, y1 in ((5, 0, 3), (11, 4, 7), (3, 8, 11), (9, 12, 15)):
        cv.vline(x, y0, y1, c[0])
    # grain streaks and a nail head
    for y, x0, x1 in ((1, 2, 9), (5, 6, 14), (9, 1, 7), (13, 8, 15)):
        cv.hline(x0, x1, y, c[1])
    for y, x0, x1 in ((2, 4, 11), (6, 1, 5), (10, 9, 15), (12, 3, 8)):
        cv.hline(x0, x1, y, c[4])
    cv.noise(0.06)


print("wrote", len(REG), "block textures:", ", ".join(REG))
