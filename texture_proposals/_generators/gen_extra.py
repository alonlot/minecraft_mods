"""Armor overlay layers and the two entity sheets that were flat fills.

Armor layer_1 net (64x32), vanilla humanoid:
  body 8x12x4 @ (16,16): up x20-27 y16-19 | down x28-35 y16-19
                         sides y20-31: east x16-19, FRONT x20-27, west x28-31, BACK x32-39
  arm  4x12x4 @ (40,16): up x44-47 y16-19 | down x48-51 y16-19
                         sides y20-31: east x40-43, front x44-47, west x48-51, back x52-55
The robes are CHESTPLATEs, so only the body and arm boxes are ever drawn -- the head and
leg regions are left empty on purpose.
"""
import os, random
from pixlib import Canvas, hx, ramp, shade

OUT_A = os.path.join(os.path.dirname(os.path.abspath(__file__)), "proposed", "models_armor")
OUT_E = os.path.join(os.path.dirname(os.path.abspath(__file__)), "proposed", "entity")
os.makedirs(OUT_A, exist_ok=True)
os.makedirs(OUT_E, exist_ok=True)
REG = []


def tex(name, w, h, out):
    def deco(fn):
        cv = Canvas(w, h, seed=abs(hash(name)) % 9999)
        fn(cv)
        cv.save(os.path.join(out, name + ".png"))
        REG.append(name)
        return fn
    return deco


# =============================================================================
def robe_layer(cv, cloth, lining, trim):
    """cloth/lining/trim are 3-step ramps: [dark, mid, light]."""
    dk, md, lt = cloth

    def panel(x0, x1, folds=(), lit=False):
        """A 12-row torso/sleeve panel with vertical folds."""
        for y in range(20, 32):
            for x in range(x0, x1 + 1):
                cv.put(x, y, md)
        for fx in folds:
            cv.vline(fx, 20, 31, dk)
            if fx + 1 <= x1:
                cv.vline(fx + 1, 20, 31, lt if lit else md)
        # hem: two darker rows, with a lighter lip
        cv.hline(x0, x1, 30, dk)
        cv.hline(x0, x1, 31, shade(dk, .7))
        cv.hline(x0, x1, 29, md)

    # --- torso front: robe hanging open over a house-coloured lining
    panel(20, 27, folds=(21, 26))
    for y in range(20, 32):
        for x in range(23, 25):
            cv.put(x, y, lining[1])
    cv.vline(23, 20, 31, lining[2])
    cv.vline(24, 20, 31, lining[0])
    # lapels folding away from the opening
    cv.line(22, 20, 21, 27, lt)
    cv.line(25, 20, 26, 27, dk)
    # collar
    cv.hline(20, 27, 20, lt)
    cv.hline(20, 27, 21, md)
    cv.put(22, 21, trim[1]); cv.put(25, 21, trim[1])
    # tie knot under the collar
    cv.put(23, 22, trim[1]); cv.put(24, 22, trim[1])
    cv.put(23, 23, trim[0]); cv.put(24, 23, trim[2])
    # house crest on the breast
    cv.rect(21, 24, 22, 25, trim[1])
    cv.put(21, 24, trim[2]); cv.put(22, 25, trim[0])

    # --- torso back: unbroken cloth with a hood laid across the shoulders
    panel(32, 39, folds=(34, 37))
    cv.rect(33, 20, 38, 22, dk)
    cv.hline(33, 38, 20, md)
    cv.hline(34, 37, 22, shade(dk, .75))
    cv.vline(35, 23, 29, dk); cv.vline(36, 23, 29, lt)

    # --- torso sides
    panel(16, 19, folds=(17,))
    panel(28, 31, folds=(29,))
    cv.vline(16, 20, 31, dk)
    cv.vline(31, 20, 31, dk)

    # --- shoulders (up face) and underside (down face)
    for y in range(16, 20):
        for x in range(20, 28):
            cv.put(x, y, md)
        for x in range(28, 36):
            cv.put(x, y, shade(dk, .8))
    cv.frame(20, 16, 27, 19, dk)
    cv.hline(21, 26, 17, lt)
    cv.hline(23, 24, 16, lining[1])   # collar opening seen from above

    # --- sleeve: one net, mirrored onto the other arm by the renderer
    for y in range(20, 32):
        for x in range(40, 56):
            cv.put(x, y, md)
    for fx in (41, 45, 49, 53):
        cv.vline(fx, 20, 31, dk)
        cv.vline(fx + 1, 20, 31, lt)
    # wide turned-back cuff in the house colour
    for y in (28, 29):
        for x in range(40, 56):
            cv.put(x, y, lining[1])
    cv.hline(40, 55, 28, lining[2])
    cv.hline(40, 55, 30, dk)
    cv.hline(40, 55, 31, shade(dk, .7))
    # shoulder cap
    for y in range(16, 20):
        for x in range(44, 48):
            cv.put(x, y, lt)
        for x in range(48, 52):
            cv.put(x, y, shade(dk, .8))
    cv.frame(44, 16, 47, 19, dk)
    cv.noise(0.04)


def mk_robe(name, cloth, lining, trim):
    @tex(name, 64, 32, OUT_A)
    def _(cv, cloth=cloth, lining=lining, trim=trim):
        robe_layer(cv, ramp(*cloth), ramp(*lining), ramp(*trim))


mk_robe("gryffindor_robes_layer_1", ("241014", "3a181e", "532730"),
        ("6e0e12", "a8161c", "d4444a"), ("8a6a1a", "e8c452", "fff0b8"))
mk_robe("hufflepuff_robes_layer_1", ("20190f", "342a17", "4a3c22"),
        ("8a6a08", "d9a80f", "f2cf55"), ("2a2418", "f7de7a", "fff4c6"))
mk_robe("ravenclaw_robes_layer_1", ("0d1428", "16203c", "203055"),
        ("123a7a", "1d5bb5", "4b8ae0"), ("6b5117", "b98d3e", "e6cd8a"))
mk_robe("slytherin_robes_layer_1", ("0b1811", "12261c", "1a3628"),
        ("0d5a34", "158f50", "45c483"), ("6f757c", "c0c6cc", "eef2f5"))


# =============================================================================
# golden_snitch entity, 32x32. Box nets read out of GoldenSnitchModel:
#   body  3x3x3 @ (0,0):  up x3-5 y0-2 | down x6-8 y0-2 | sides y3-5:
#                         east x0-2, north x3-5, west x6-8, south x9-11
#   wings 6x1x3 @ (0,8) and (0,12): up x3-8 | down x9-14 (3 rows) | sides on the last row
@tex("golden_snitch", 32, 32, OUT_E)
def _(cv):
    g = ramp("5c3f0d", "8f6415", "c99a26", "f0c94e", "fff0b0")
    w = ramp("6f7d92", "9fb0c6", "cfdcea", "eef5fc", "ffffff")

    def body_face(x0, y0, lit):
        for y in range(y0, y0 + 3):
            for x in range(x0, x0 + 3):
                cv.put(x, y, g[2] if lit else g[1])
        cv.put(x0, y0, g[3] if lit else g[2])
        cv.put(x0 + 2, y0 + 2, g[0])
        # the engraved equatorial seam
        cv.put(x0 + 1, y0 + 1, g[3] if lit else g[2])

    body_face(3, 0, True)     # up
    body_face(6, 0, False)    # down
    for i, x0 in enumerate((0, 3, 6, 9)):
        body_face(x0, 3, i in (0, 1))
    # seam ring around the sides
    for x0 in (0, 3, 6, 9):
        cv.put(x0, 4, g[4]); cv.put(x0 + 1, 4, g[3]); cv.put(x0 + 2, 4, g[3])
    cv.put(4, 1, g[4])        # a catch-light on top

    def wing(y0):
        # up face x3-8, three rows deep
        for y in range(y0, y0 + 3):
            for x in range(3, 9):
                cv.put(x, y, w[2])
        # leading edge bright, trailing edge feathered and translucent-looking
        for x in range(3, 9):
            cv.put(x, y0, w[4])
            cv.put(x, y0 + 2, w[1])
        # vein struts running out along the wing
        for x in range(3, 9, 2):
            cv.put(x, y0 + 1, w[3])
        cv.put(8, y0 + 1, w[0])
        # down face x9-14, dimmer
        for y in range(y0, y0 + 3):
            for x in range(9, 15):
                cv.put(x, y, w[1])
        for x in range(9, 15, 2):
            cv.put(x, y0 + 1, w[2])
        cv.put(9, y0, w[3])
        # the thin side faces on the bottom row
        for x in range(0, 3):
            cv.put(x, y0 + 3, w[1])
        for x in range(3, 9):
            cv.put(x, y0 + 3, w[3])
        for x in range(9, 12):
            cv.put(x, y0 + 3, w[1])
        for x in range(12, 18):
            cv.put(x, y0 + 3, w[2])
        # a gold root where the wing meets the body
        cv.put(3, y0 + 3, g[2]); cv.put(12, y0 + 3, g[2])

    wing(8)
    wing(12)


@tex("petrified_stone", 64, 64, OUT_E)
def _(cv):
    """Applied over whatever mob got petrified, so it has to work as a generic
    stone surface at any UV -- weathered grey with cracks and mineral flecks."""
    r = random.Random(5)
    base = ramp("6e6e73", "7b7b81", "87878e", "94949b", "a1a1a8")
    dark = hx("55555b")
    dark2 = hx("46464b")
    lite = hx("b2b2ba")
    for y in range(64):
        for x in range(64):
            cv.put(x, y, r.choice(base))
    # broad tonal blotches so it does not read as uniform noise
    for _ in range(26):
        cx, cy, rr = r.randint(0, 63), r.randint(0, 63), r.randint(3, 8)
        c = r.choice([dark, lite, base[0], base[4]])
        for y in range(cy - rr, cy + rr + 1):
            for x in range(cx - rr, cx + rr + 1):
                if (x - cx) ** 2 + (y - cy) ** 2 <= rr * rr and r.random() > .45:
                    cv.put(x % 64, y % 64, c)
    # cracks: short random walks, with a lighter lip on one side
    for _ in range(22):
        x, y = r.randint(0, 63), r.randint(0, 63)
        for _ in range(r.randint(6, 16)):
            cv.put(x % 64, y % 64, dark2)
            cv.put((x + 1) % 64, y % 64, base[4])
            x += r.choice([-1, 0, 1, 1])
            y += r.choice([-1, 0, 1, 1])
    # pitting and mineral flecks
    for _ in range(160):
        cv.put(r.randint(0, 63), r.randint(0, 63), r.choice([dark, lite, base[0]]))
    cv.noise(0.05)


@tex("gillyweed_gills", 16, 16, OUT_E)
def _(cv):
    """Worn on the neck: three fleshy gill slits with a wet sheen."""
    g = ramp("1c3a24", "2e5c33", "469046", "6bbd5f", "9ad97f")
    cv.poly([(4, 2), (11, 2), (12, 8), (10, 13), (5, 13), (3, 8)], g[1])
    cv.poly([(5, 3), (10, 3), (11, 8), (9, 12), (6, 12), (4, 8)], g[2])
    for i, y in enumerate((5, 7, 9)):
        cv.hline(5, 10, y, g[0])
        cv.hline(5, 10, y + 1, g[3])
        cv.put(5, y, g[1]); cv.put(10, y, g[1])
    cv.put(6, 4, g[4]); cv.put(8, 4, g[4]); cv.put(5, 11, g[0])
    cv.inner_outline(g[0], sides=("r", "b"))
    cv.outline(hx("0c1a10"))


print("wrote:", ", ".join(REG))
