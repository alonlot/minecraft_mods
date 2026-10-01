"""Banner sigils, take two.

Recognisable animals do not survive a 6x7 pixel panel -- pass one's lion read as a bee.
Each house gets its founder's relic instead, which is both legible at this size and
already part of this mod's vocabulary. Also darkens the fireplace stone.
"""
import os
from pixlib import Canvas, hx, ramp

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "proposed", "block")


def tex(name, size=16):
    def deco(fn):
        cv = Canvas(size, size, seed=abs(hash(name)) % 9999)
        fn(cv)
        cv.save(os.path.join(OUT, name + ".png"))
        print("redrew", name)
        return fn
    return deco


# --- relics, drawn inside the x5-10 / y5-11 panel ----------------------------
SWORD = {  # Sword of Gryffindor
    "main": [(7, 5), (8, 5), (7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8),
             (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9),
             (7, 10), (8, 10), (6, 11), (7, 11), (8, 11), (9, 11)],
    "hi": [(7, 5), (7, 6), (7, 7), (5, 9), (6, 11)],
}
CUP = {  # Hufflepuff's Cup
    "main": [(6, 5), (7, 5), (8, 5), (9, 5), (6, 6), (7, 6), (8, 6), (9, 6),
             (5, 6), (10, 6), (5, 7), (10, 7), (7, 7), (8, 7),
             (7, 8), (8, 8), (7, 9), (8, 9), (6, 10), (7, 10), (8, 10), (9, 10)],
    "hi": [(6, 5), (7, 5), (5, 6), (6, 10)],
}
DIADEM = {  # Ravenclaw's Diadem
    "main": [(7, 5), (8, 5), (6, 6), (9, 6), (5, 7), (10, 7), (5, 8), (10, 8),
             (6, 8), (7, 7), (8, 7), (9, 8),
             (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9)],
    "hi": [(7, 5), (8, 5), (5, 9), (6, 9)],
}
LOCKET = {  # Slytherin's Locket
    "main": [(5, 5), (6, 5), (9, 5), (10, 5), (7, 6), (8, 6),
             (6, 7), (7, 7), (8, 7), (9, 7),
             (6, 8), (7, 8), (8, 8), (9, 8),
             (6, 9), (7, 9), (8, 9), (9, 9),
             (7, 10), (8, 10)],
    "hi": [(5, 5), (6, 7), (7, 8)],
}


def banner(cv, cloth, trim, relic):
    cv.rect(0, 0, 15, 15, cloth[1])
    # rod and rings
    cv.hline(0, 15, 0, hx("4a3a22"))
    cv.hline(0, 15, 1, hx("6b5533"))
    for x in (2, 7, 13):
        cv.put(x, 1, hx("b3a06a"))
    # folds in the cloth
    for x in (3, 8, 12):
        cv.vline(x, 2, 15, cloth[0])
        cv.vline(x + 1, 2, 15, cloth[2])
    # border
    cv.vline(0, 2, 15, trim[0]); cv.vline(15, 2, 15, trim[0])
    cv.hline(0, 15, 15, trim[0])
    cv.hline(0, 15, 14, trim[1])
    # sigil panel, sunk slightly darker so the relic pops
    cv.rect(4, 4, 11, 12, cloth[0])
    cv.frame(4, 4, 11, 12, cloth[1])
    for x, y in relic["main"]:
        cv.put(x, y, trim[1])
    for x, y in relic["hi"]:
        cv.put(x, y, trim[2])
    cv.noise(0.045)


def mk(name, cloth, trim, relic):
    @tex(name)
    def _(cv, cloth=cloth, trim=trim, relic=relic):
        banner(cv, ramp(*cloth), ramp(*trim), relic)


mk("gryffindor_banner_block", ("5e1219", "7d1a22", "97262f"), ("4d3308", "c39a30", "f2dc8e"), SWORD)
mk("hufflepuff_banner_block", ("8a6a08", "b3890f", "cfa42b"), ("1a1508", "2f2716", "f2dc8e"), CUP)
mk("ravenclaw_banner_block", ("102045", "18305e", "213f76"), ("4a3a0e", "b98d3e", "e6cd8a"), DIADEM)
mk("slytherin_banner_block", ("0c3a24", "12522f", "1a6b3e"), ("59606a", "a8b0ba", "e2e8ef"), LOCKET)


@tex("floo_fireplace")
def _(cv):
    """Darker brick so it reads as a hearth, not a stone slab.
    UV regions: y0-3 = wall side band, x1-14 y3-12 = hearth floor, x0-1 y2-13 = wall top."""
    import random
    r = random.Random(3)
    bk = ramp("241a17", "35251f", "45302708".replace("08", ""), "573d31")
    bk = ramp("241a17", "35251f", "453027", "573d31")
    ash = ramp("1a191c", "27252a", "393639")
    grn = ramp("0d5c3a", "16a86a", "3ff0a0", "b6ffdc")
    # brick band used by every wall face
    for y in range(0, 4):
        for x in range(16):
            cv.put(x, y, r.choice([bk[1], bk[1], bk[2], bk[0]]))
    # mortar courses + staggered joints
    cv.hline(0, 15, 2, hx("1b1512"))
    for x in (0, 5, 10, 15):
        cv.vline(x, 0, 1, hx("1b1512"))
    for x in (2, 8, 13):
        cv.vline(x, 3, 3, hx("1b1512"))
    cv.hline(0, 15, 0, bk[3])
    # wall tops
    cv.rect(0, 4, 1, 13, bk[2])
    cv.vline(0, 4, 13, bk[3])
    # hearth floor: cold ash with floo fire burning through it
    for y in range(4, 13):
        for x in range(2, 15):
            cv.put(x, y, r.choice([ash[1], ash[1], ash[0], ash[2]]))
    for x, y in ((5, 7), (9, 6), (7, 10), (11, 9), (4, 10), (12, 7)):
        cv.put(x, y, grn[1]); cv.put(x, y - 1, grn[2])
    cv.put(8, 8, grn[3]); cv.put(7, 8, grn[2]); cv.put(9, 8, grn[2])
    cv.put(6, 9, grn[0]); cv.put(10, 7, grn[0])
    # charred logs
    cv.hline(3, 13, 12, hx("151314"))
    cv.hline(4, 12, 4, hx("1a1715"))
