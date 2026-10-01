"""Robe armour layers, take two.

Pass one went too dark: on the body the whole chestplate read as a near-black box and
lost the house identity the originals had. This keeps the saturated house cloth and
spends the detail budget on drape, collar, cuffs and crest instead.
"""
import os
from pixlib import Canvas, hx, ramp, shade

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "proposed", "models_armor")
os.makedirs(OUT, exist_ok=True)


def tex(name, w, h):
    def deco(fn):
        cv = Canvas(w, h, seed=abs(hash(name)) % 9999)
        fn(cv)
        cv.save(os.path.join(OUT, name + ".png"))
        print("redrew", name)
        return fn
    return deco


def robe(cv, cloth, edge, lin, crest):
    """cloth: 4-step house ramp. edge: near-black trim ramp. lin: collar/lining.
    crest: single accent colour."""
    d1, d2, m, l = cloth

    def drape(x0, x1, y0=20, y1=31, folds=(), lit_col=None):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                cv.put(x, y, m)
        for fx in folds:
            # a fold is a dark crease with a lit ridge beside it
            cv.vline(fx, y0, y1, d2)
            if fx + 1 <= x1:
                cv.vline(fx + 1, y0, y1, lit_col or l)
        # cloth gathers and darkens toward the hem
        cv.hline(x0, x1, y1 - 1, d2)
        cv.hline(x0, x1, y1, d1)

    # ---- torso front -------------------------------------------------------
    drape(20, 27, folds=(22, 25))
    # the robe hangs open: near-black lapels either side of a pale shirt
    cv.rect(20, 20, 21, 31, edge[1])
    cv.rect(26, 20, 27, 31, edge[1])
    cv.vline(21, 20, 31, edge[2])
    cv.vline(26, 20, 31, edge[0])
    cv.rect(23, 22, 24, 31, lin[2])
    cv.vline(24, 22, 31, lin[1])
    # collar standing at the neck
    cv.hline(20, 27, 20, edge[2])
    cv.hline(22, 25, 20, lin[2])
    cv.hline(22, 25, 21, lin[1])
    # house tie
    cv.put(23, 22, crest); cv.put(24, 22, crest)
    cv.put(23, 23, d1); cv.put(24, 23, crest)
    cv.put(23, 25, crest); cv.put(24, 26, crest)
    # crest patch on the breast
    cv.rect(21, 25, 22, 27, edge[0])
    cv.put(21, 25, crest); cv.put(22, 26, crest)

    # ---- torso back --------------------------------------------------------
    drape(32, 39, folds=(34, 37))
    # hood lying across the shoulders
    cv.rect(33, 20, 38, 23, d2)
    cv.hline(33, 38, 20, m)
    cv.hline(34, 37, 23, d1)
    cv.put(35, 21, d1); cv.put(36, 21, l)
    # a long centre seam
    cv.vline(35, 24, 30, d2); cv.vline(36, 24, 30, l)
    cv.rect(32, 20, 32, 31, edge[1])
    cv.rect(39, 20, 39, 31, edge[1])

    # ---- torso sides -------------------------------------------------------
    drape(16, 19, folds=(17,))
    drape(28, 31, folds=(29,))
    cv.vline(16, 20, 31, edge[1])
    cv.vline(31, 20, 31, edge[1])
    cv.vline(19, 20, 31, d2)
    cv.vline(28, 20, 31, d2)

    # ---- shoulders / underside --------------------------------------------
    for y in range(16, 20):
        for x in range(20, 28):
            cv.put(x, y, m)
        for x in range(28, 36):
            cv.put(x, y, d1)
    cv.frame(20, 16, 27, 19, edge[1])
    cv.hline(21, 26, 17, l)
    cv.rect(23, 16, 24, 17, lin[2])       # collar opening from above

    # ---- sleeve ------------------------------------------------------------
    for y in range(20, 32):
        for x in range(40, 56):
            cv.put(x, y, m)
    for fx in (41, 45, 49, 53):
        cv.vline(fx, 20, 31, d2)
        cv.vline(fx + 1, 20, 31, l)
    # shoulder seam at the top, turned-back cuff at the wrist
    cv.hline(40, 55, 20, edge[2])
    cv.hline(40, 55, 21, d2)
    for y in (28, 29, 30):
        for x in range(40, 56):
            cv.put(x, y, edge[1])
    cv.hline(40, 55, 28, edge[2])
    cv.hline(40, 55, 29, crest)
    cv.hline(40, 55, 31, edge[0])
    # shoulder cap
    for y in range(16, 20):
        for x in range(44, 48):
            cv.put(x, y, l)
        for x in range(48, 52):
            cv.put(x, y, d1)
    cv.frame(44, 16, 47, 19, edge[1])
    cv.noise(0.035)


def mk(name, cloth, edge, lin, crest):
    @tex(name, 64, 32)
    def _(cv, cloth=cloth, edge=edge, lin=lin, crest=crest):
        robe(cv, ramp(*cloth), ramp(*edge), ramp(*lin), hx(crest))


mk("gryffindor_robes_layer_1", ("4a0d12", "6e141b", "9c1f28", "c23a41"),
   ("120c0e", "1d1417", "2b1f23"), ("6b5c4a", "9c8a70", "d8c9ac"), "e8c452")
mk("hufflepuff_robes_layer_1", ("6b5006", "96700a", "c99a12", "e8bd35"),
   ("14110a", "201b10", "2f2818"), ("6b5c4a", "9c8a70", "d8c9ac"), "2a2010")
mk("ravenclaw_robes_layer_1", ("0c1a3a", "142a5c", "1d4285", "2e63b8"),
   ("0a0e18", "121826", "1c2436"), ("6b5c4a", "9c8a70", "d8c9ac"), "b98d3e")
mk("slytherin_robes_layer_1", ("06301c", "0a482a", "0f6a3c", "1a9455"),
   ("081210", "0f1c17", "16281f"), ("6b5c4a", "9c8a70", "d8c9ac"), "c0c6cc")
