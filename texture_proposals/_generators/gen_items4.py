"""Polish round: the icons that still were not carrying their read, plus the snitch net.

broom_workbench and ravenclaw_diadem_fragment are on their third and fourth attempt --
both were failing because the subject was drawn too thin to survive 16 pixels.
"""
import os
from pixlib import Canvas, hx, ramp, shade, shaft

ITEM = os.path.join(os.path.dirname(os.path.abspath(__file__)), "proposed", "item")
ENT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "proposed", "entity")


def tex(name, out=ITEM, size=16, scale=1):
    def deco(fn):
        cv = Canvas(size, size, seed=abs(hash(name)) % 9999)
        fn(cv)
        im = cv.image()
        if scale > 1:
            im = im.resize((size * scale, size * scale))
        im.save(os.path.join(out, name + ".png"))
        print("redrew", name)
        return fn
    return deco


@tex("broom_workbench", scale=4)
def _(cv):
    """Broom above, bench below -- two clear shapes instead of one ambiguous blob."""
    w = ramp("2e1a10", "4a2a17", "6b3f22", "8c5730")
    st = ramp("6b4f2c", "9a7845", "c3a066", "e6cd9c")
    br = ramp("6f5620", "a8842f", "d8b45e", "f0d68e")
    # --- broom, laid across the top
    cv.line(15, 2, 8, 5, st[2])
    cv.line(15, 3, 8, 6, st[1])
    cv.line(14, 2, 9, 4, st[3])
    cv.put(15, 2, st[3])
    # bristle fan sweeping left
    for i, y in enumerate((2, 3, 4, 5, 6, 7, 8)):
        c = br[2] if i % 2 else br[1]
        cv.line(8, 5 if y < 5 else 6, 1, y, c)
    cv.put(1, 2, br[3]); cv.put(1, 8, br[0])
    cv.put(2, 4, br[3]); cv.put(3, 7, br[0])
    # twine binding at the neck
    cv.vline(8, 4, 6, hx("efe3c0"))
    cv.put(9, 5, hx("b3a479"))
    # --- bench below
    cv.rect(0, 10, 15, 10, w[3])
    cv.rect(0, 11, 15, 12, w[2])
    cv.hline(0, 15, 12, w[0])
    cv.rect(2, 13, 3, 15, w[1]); cv.rect(12, 13, 13, 15, w[1])
    cv.vline(3, 13, 15, w[0]); cv.vline(13, 13, 15, w[0])
    cv.rect(4, 14, 11, 14, w[1])
    # shavings on the bench top
    cv.put(5, 10, hx("d6bb8a")); cv.put(10, 10, hx("d6bb8a"))
    cv.outline(hx("120a06"))


@tex("ravenclaw_diadem_fragment")
def _(cv):
    """A thick, solid band segment -- thin arcs read as birds at this size.
    Both ends torn, sapphire seated in the band."""
    s = ramp("343c4a", "5e6b80", "97a6bc", "d3dfef", "f4f9ff")
    blu = ramp("0e2050", "22499e", "5f9bff", "cfe4ff")
    # top edge of the band per column, then 3 rows thick
    top = {2: 12, 3: 11, 4: 10, 5: 9, 6: 9, 7: 8, 8: 8, 9: 8, 10: 9, 11: 10, 12: 11, 13: 12}
    for x, y in top.items():
        cv.put(x, y, s[3])
        cv.put(x, y + 1, s[2])
        cv.put(x, y + 2, s[1])
        cv.put(x, y + 3, s[0])
    # engraved bead line running along the band
    for x in (4, 6, 9, 11):
        cv.put(x, top[x] + 1, s[4])
    for x in (5, 8, 12):
        cv.put(x, top[x] + 2, s[0])
    # sapphire seated on the crest of the band
    cv.rect(7, 5, 8, 7, blu[1])
    cv.put(7, 5, blu[3]); cv.put(8, 5, blu[2]); cv.put(8, 7, blu[0])
    cv.put(7, 6, blu[2])
    cv.put(6, 6, s[2]); cv.put(9, 6, s[2])          # claws
    cv.put(6, 7, s[1]); cv.put(9, 7, s[1])
    # torn ends: ragged, and the metal is going dark where it snapped
    for x, y in ((2, 12), (2, 15), (13, 12), (13, 15)):
        cv.put(x, y, (0, 0, 0, 0))
    cv.put(1, 13, hx("2a3040")); cv.put(1, 14, hx("20242f"))
    cv.put(14, 13, hx("2a3040")); cv.put(14, 14, hx("20242f"))
    cv.put(3, 14, hx("2b2038")); cv.put(12, 14, hx("2b2038"))   # horcrux tarnish
    cv.outline(hx("12161e"))


@tex("wolf_fang")
def _(cv):
    """A tooth, not a bone: heavy root, real curve, one sharp point."""
    iv = ramp("6f6047", "9c8b6b", "cdbf9e", "eee5cc", "fdf8ec")
    span = {15: (4, 7), 14: (4, 7), 13: (4, 8), 12: (5, 8), 11: (5, 9), 10: (6, 9),
            9: (6, 10), 8: (7, 10), 7: (7, 10), 6: (8, 11), 5: (8, 11), 4: (9, 11),
            3: (10, 12), 2: (11, 12), 1: (12, 12)}
    for y, (x0, x1) in span.items():
        for x in range(x0, x1 + 1):
            cv.put(x, y, iv[2])
        cv.put(x0, y, iv[3])          # lit left edge
        cv.put(x1, y, iv[1])          # shaded right edge
    # enamel gloss down the front of the tooth
    for y in (4, 5, 6, 7, 8, 9):
        cv.put(span[y][0] + 1, y, iv[4])
    # the root: duller, with a hollow and a growth line
    for y in (12, 13, 14, 15):
        for x in range(span[y][0], span[y][1] + 1):
            cv.put(x, y, iv[1])
    cv.put(5, 14, iv[0]); cv.put(6, 14, iv[0]); cv.put(5, 15, iv[0])
    cv.hline(4, 8, 12, iv[2])
    # hairline crack
    cv.put(8, 9, iv[0]); cv.put(8, 8, iv[0])
    cv.put(12, 1, iv[4])
    cv.outline(hx("2b241a"))


@tex("goblin_silver_dagger")
def _(cv):
    """Stronger crossguard -- the guard is what makes a diagonal streak read as a blade."""
    sil = ramp("454d59", "78838f", "b3bfcc", "e6edf5", "ffffff")
    lea = ramp("2b1c12", "48301d", "6b4629")
    # blade
    cv.line(14, 1, 8, 7, sil[3])
    cv.line(13, 1, 7, 7, sil[2])
    cv.line(13, 2, 8, 8, sil[1])
    cv.line(12, 2, 7, 8, sil[2])
    cv.put(15, 0, sil[4]); cv.put(14, 0, sil[3])
    for x, y in ((12, 3), (10, 5)):
        cv.put(x, y, sil[4])
    # crossguard: a solid dark bar across the blade, with bright tips
    cv.line(4, 7, 10, 4, hx("2f3540"))
    cv.line(4, 8, 10, 5, hx("515a68"))
    cv.put(4, 7, sil[2]); cv.put(3, 8, sil[1])
    cv.put(10, 4, sil[2]); cv.put(11, 4, sil[1])
    # grip
    cv.line(6, 9, 3, 12, lea[2])
    cv.line(7, 9, 4, 13, lea[1])
    cv.line(7, 10, 4, 14, lea[0])
    for x, y in ((5, 10), (4, 12)):
        cv.put(x, y, hx("8e7346"))
    # pommel
    cv.rect(2, 13, 3, 14, sil[1])
    cv.put(2, 13, sil[3]); cv.put(3, 14, sil[0])
    cv.outline(hx("14161c"))


@tex("acromantula_venom")
def _(cv):
    """Rebalanced: more venom, less empty glass, a cork that reads as cork."""
    ven = ramp("0f4415", "1f7a1e", "45b830", "8ee85a", "cbff9e")
    gl = ramp("263540", "4d6472", "8fa9b8")
    # flask
    cv.rect(6, 3, 9, 5, gl[1])                       # neck
    cv.poly([(4, 6), (11, 6), (12, 8), (12, 14), (3, 14), (3, 8)], gl[1])
    # venom, filled high
    cv.poly([(4, 7), (11, 7), (11, 13), (4, 13)], ven[1])
    cv.hline(4, 11, 7, ven[2])
    cv.hline(4, 11, 8, ven[3])                       # bright meniscus
    cv.hline(4, 11, 13, ven[0])
    cv.put(5, 10, ven[3]); cv.put(9, 11, ven[0])
    cv.put(6, 9, ven[4])                             # bead of light
    # a suspended sac of venom
    cv.put(8, 10, ven[3]); cv.put(9, 10, ven[2])
    # glass edges
    cv.vline(4, 7, 12, gl[2])
    cv.put(6, 4, gl[2])
    cv.put(11, 12, gl[0]); cv.put(11, 8, gl[0])
    # cork
    cv.rect(5, 0, 10, 2, hx("8a6438"))
    cv.hline(5, 10, 0, hx("a67c48"))
    cv.hline(5, 10, 2, hx("5e4223"))
    cv.put(7, 1, hx("6b4a2a")); cv.put(9, 1, hx("a67c48"))
    # two fangs etched on the label band
    cv.put(6, 5, hx("e2dccc")); cv.put(9, 5, hx("e2dccc"))
    # a drip running down the outside
    cv.put(12, 9, ven[2]); cv.put(12, 10, ven[1])
    cv.outline(hx("0b1a0d"))


@tex("moonlit_phial")
def _(cv):
    """Brighter, with an unmistakable crescent floating in the liquid."""
    liq = ramp("1b3companion".replace("companion", "260"), "2f5da8", "76a9ea", "c2e0ff")
    liq = ramp("1b3260", "2f5da8", "76a9ea", "c2e0ff")
    gl = ramp("223040", "4a6076", "93b0c9")
    sil = ramp("6f7c8c", "a9b7c7", "e4ecf5")
    # phial
    cv.rect(6, 3, 9, 5, gl[1])
    cv.poly([(4, 6), (11, 6), (12, 8), (11, 14), (4, 14), (3, 8)], gl[1])
    # liquid, nearly full and luminous
    cv.poly([(4, 7), (11, 7), (11, 13), (4, 13)], liq[1])
    cv.hline(4, 11, 7, liq[2])
    cv.hline(4, 11, 13, liq[0])
    for x in range(4, 12):
        cv.put(x, 12, liq[0])
    # crescent moon
    for x, y in ((6, 8), (7, 8), (8, 9), (8, 10), (7, 11), (6, 11), (5, 10), (5, 9)):
        cv.put(x, y, liq[3])
    for x, y in ((6, 9), (6, 10), (7, 9), (7, 10)):
        cv.put(x, y, liq[1])
    cv.put(6, 8, hx("ffffff")); cv.put(5, 9, hx("eaf6ff"))
    # glass and cap
    cv.vline(4, 7, 12, gl[2])
    cv.put(11, 12, gl[0])
    cv.rect(5, 0, 10, 2, sil[1])
    cv.hline(5, 10, 0, sil[2])
    cv.hline(5, 10, 2, sil[0])
    cv.put(7, 1, sil[2])
    # light escaping the neck
    cv.put(7, 4, liq[3]); cv.put(8, 4, liq[2])
    cv.put(2, 6, hx("9fc8ff77")); cv.put(13, 10, hx("9fc8ff77"))
    cv.outline(hx("0d1220"))


@tex("dragon_heartstring_core")
def _(cv):
    """Same coil, but fibrous now -- alternating strands so it reads as sinew, not a snake."""
    s = ramp("46080c", "7d1218", "b32720", "e8543c", "ff9a72")
    path = [(3, 14), (5, 13), (7, 11), (6, 9), (4, 8), (5, 6),
            (7, 5), (9, 6), (10, 8), (12, 8), (13, 6), (13, 3)]
    pts = []
    for i in range(len(path) - 1):
        x0, y0 = path[i]; x1, y1 = path[i + 1]
        n = max(abs(x1 - x0), abs(y1 - y0))
        for k in range(n + 1):
            pts.append((round(x0 + (x1 - x0) * k / n), round(y0 + (y1 - y0) * k / n)))
    # lay the strand down, alternating fibre tones along its length
    for i, (x, y) in enumerate(pts):
        cv.put(x, y, s[2] if i % 3 else s[1])
    # lit upper edge / dark underside
    for x in range(16):
        for y in range(16):
            if cv.solid(x, y) and not cv.solid(x, y - 1):
                cv.put(x, y, s[3])
            elif cv.solid(x, y) and not cv.solid(x, y + 1) and cv.get(x, y) != s[3]:
                cv.put(x, y, s[0])
    for i, (x, y) in enumerate(pts):
        if i % 5 == 2:
            cv.put(x, y, s[4])
    # frayed ends
    cv.put(2, 15, s[1]); cv.put(14, 2, s[1])
    # the faint heat still in it
    for x, y in ((8, 5), (12, 7), (6, 10)):
        cv.put(x, y, hx("ff9a72"))
    for x, y in ((15, 2), (1, 15), (10, 4)):
        cv.put(x, y, hx("b3272066"))
    cv.outline(hx("240508"))


# --- golden snitch entity net ------------------------------------------------
@tex("golden_snitch", out=ENT, size=32)
def _(cv):
    """32x32 net. body 3x3x3 @(0,0); wings 6x1x3 @(0,8) and @(0,12).
    Wings get a lengthwise gradient and strut veins so they read as filigree."""
    g = ramp("4a3208", "7d5711", "b8891f", "e0b23c", "ffe9a0")
    w = ramp("5d6a7e", "8b9bb0", "b9c9dd", "e2eef9", "ffffff")

    def face3(x0, y0, base, hi, lo):
        for y in range(y0, y0 + 3):
            for x in range(x0, x0 + 3):
                cv.put(x, y, base)
        cv.put(x0, y0, hi)
        cv.put(x0 + 2, y0 + 2, lo)
        cv.put(x0 + 1, y0, hi)

    face3(3, 0, g[3], g[4], g[2])      # up  -- struck brightest
    face3(6, 0, g[1], g[2], g[0])      # down -- in shadow
    for i, x0 in enumerate((0, 3, 6, 9)):
        face3(x0, 3, g[2] if i < 2 else g[1], g[3], g[0])
    # engraved seam ring around the middle row of every side face
    for x0 in (0, 3, 6, 9):
        for k in range(3):
            cv.put(x0 + k, 4, g[0])
        cv.put(x0 + 1, 4, g[4])
    cv.put(4, 1, g[4])

    def wing(y0):
        for row, y in enumerate(range(y0, y0 + 3)):
            for x in range(3, 9):
                # brighter at the root, thinning toward the tip
                t = (x - 3) / 5.0
                c = w[3] if t < .35 else (w[2] if t < .7 else w[1])
                if row == 0:
                    c = w[4] if t < .5 else w[3]
                if row == 2:
                    c = w[1] if t < .5 else w[0]
                cv.put(x, y, c)
        # struts running out along the wing
        for x in (4, 6, 8):
            cv.put(x, y0 + 1, w[4])
        cv.put(3, y0 + 1, g[3])        # gold root
        # underside, dimmer
        for row, y in enumerate(range(y0, y0 + 3)):
            for x in range(9, 15):
                t = (14 - x) / 5.0
                cv.put(x, y, w[1] if t < .5 else w[0])
        for x in (10, 12):
            cv.put(x, y0 + 1, w[2])
        cv.put(14, y0 + 1, g[2])
        # the thin edge faces on the last row
        for x in range(0, 3):
            cv.put(x, y0 + 3, w[1])
        for x in range(3, 9):
            cv.put(x, y0 + 3, w[3] if x < 6 else w[2])
        for x in range(9, 12):
            cv.put(x, y0 + 3, w[1])
        for x in range(12, 18):
            cv.put(x, y0 + 3, w[2] if x < 15 else w[1])
        cv.put(3, y0 + 3, g[2]); cv.put(12, y0 + 3, g[2])

    wing(8)
    wing(12)
