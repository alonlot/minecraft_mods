"""Final block polish.

- Slytherin's locket was a solid silver blob at sigil size; hollowing it out and putting
  the serpent S inside gives it an actual read.
- The cauldron's iron sat almost at black once vanilla's side shading was applied.
- The hearth needed more floo-green to look lit rather than cold.
"""
import os, random
from pixlib import Canvas, hx, ramp, shade

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "proposed", "block")


def tex(name, size=16):
    def deco(fn):
        cv = Canvas(size, size, seed=abs(hash(name)) % 9999)
        fn(cv)
        cv.save(os.path.join(OUT, name + ".png"))
        print("redrew", name)
        return fn
    return deco


SWORD = {"main": [(7, 5), (8, 5), (7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8),
                  (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9),
                  (7, 10), (8, 10), (6, 11), (7, 11), (8, 11), (9, 11)],
         "hi": [(7, 5), (7, 6), (7, 7), (5, 9), (6, 11)]}
CUP = {"main": [(6, 5), (7, 5), (8, 5), (9, 5), (6, 6), (7, 6), (8, 6), (9, 6),
                (5, 6), (10, 6), (5, 7), (10, 7), (7, 7), (8, 7),
                (7, 8), (8, 8), (7, 9), (8, 9), (6, 10), (7, 10), (8, 10), (9, 10)],
       "hi": [(6, 5), (7, 5), (5, 6), (6, 10)]}
DIADEM = {"main": [(7, 5), (8, 5), (6, 6), (9, 6), (5, 7), (10, 7), (5, 8), (10, 8),
                   (6, 8), (7, 7), (8, 7), (9, 8),
                   (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9)],
          "hi": [(7, 5), (8, 5), (5, 9), (6, 9)]}
# hollow locket: a rim, a bail, a chain, and the serpent inside
LOCKET = {"main": [(5, 4), (6, 4), (9, 4), (10, 4),          # chain
                   (7, 5), (8, 5),                            # bail
                   (6, 6), (7, 6), (8, 6), (9, 6),            # rim top
                   (5, 7), (10, 7), (5, 8), (10, 8), (5, 9), (10, 9),
                   (6, 10), (7, 10), (8, 10), (9, 10),        # rim bottom
                   (7, 7), (8, 8), (7, 9)],                   # serpent S
          "hi": [(5, 4), (7, 5), (6, 6), (7, 7)]}


def banner(cv, cloth, trim, relic):
    cv.rect(0, 0, 15, 15, cloth[1])
    cv.hline(0, 15, 0, hx("4a3a22"))
    cv.hline(0, 15, 1, hx("6b5533"))
    for x in (2, 7, 13):
        cv.put(x, 1, hx("b3a06a"))
    for x in (3, 8, 12):
        cv.vline(x, 2, 15, cloth[0])
        cv.vline(x + 1, 2, 15, cloth[2])
    cv.vline(0, 2, 15, trim[0]); cv.vline(15, 2, 15, trim[0])
    cv.hline(0, 15, 15, trim[0])
    cv.hline(0, 15, 14, trim[1])
    cv.rect(4, 3, 11, 12, cloth[0])
    cv.frame(4, 3, 11, 12, cloth[1])
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
mk("slytherin_banner_block", ("0c3a24", "12522f", "1a6b3e"), ("4a5058", "aab2bc", "eef2f6"), LOCKET)


@tex("wizard_cauldron")
def _(cv):
    """Iron lifted off black -- vanilla darkens west/east faces to 0.6, which was
    crushing the old values. UVs: base sides y0-1, body sides x2-13 y2-9,
    rim top x4-11 y4-11 (overlaps the body), rim sides x4-11 y0-1."""
    fe = ramp("23262c", "343941", "474d57", "5c636f", "757d8a")
    brw = ramp("0d2e1c", "17512f", "237a43", "3aa862", "6fd88a")
    for x in range(16):
        cv.put(x, 0, fe[3]); cv.put(x, 1, fe[2])
    cv.hline(4, 11, 0, fe[4])
    for x in (5, 8, 10):
        cv.put(x, 1, fe[1])
    # body
    cv.rect(2, 2, 13, 9, fe[2])
    cv.hline(2, 13, 2, fe[4])
    cv.hline(2, 13, 3, fe[3])
    cv.hline(2, 13, 8, fe[1])
    cv.hline(2, 13, 9, fe[0])
    cv.vline(2, 2, 9, fe[3]); cv.vline(13, 2, 9, fe[1])
    for x in (4, 7, 11):
        cv.put(x, 5, fe[4]); cv.put(x, 6, fe[0])
    # brew: bright enough to read as a lit surface from above, dark enough at the
    # edges to still pass as shadowed iron where the body sides share these texels
    cv.rect(4, 4, 11, 11, brw[1])
    cv.frame(4, 4, 11, 11, brw[0])
    cv.rect(5, 5, 10, 10, brw[2])
    cv.rect(6, 6, 9, 9, brw[3])
    for x, y in ((6, 6), (9, 9), (7, 8)):
        cv.put(x, y, brw[4])
    cv.put(8, 7, hx("b6ffcf"))
    cv.put(5, 10, brw[0]); cv.put(10, 5, brw[0])
    cv.noise(0.06)


@tex("floo_fireplace")
def _(cv):
    """More floo-green in the bed so the hearth reads as lit.
    UVs: y0-3 wall band, x1-14 y3-12 hearth floor, x0-1 y2-13 wall top."""
    r = random.Random(3)
    bk = ramp("241a17", "35251f", "453027", "573d31", "6b4c3d")
    ash = ramp("1a191c", "27252a", "393639")
    grn = ramp("0d5c3a", "16a86a", "3ff0a0", "b6ffdc")
    for y in range(0, 4):
        for x in range(16):
            cv.put(x, y, r.choice([bk[1], bk[1], bk[2], bk[0]]))
    cv.hline(0, 15, 2, hx("1b1512"))
    for x in (0, 5, 10, 15):
        cv.vline(x, 0, 1, hx("1b1512"))
    for x in (2, 8, 13):
        cv.vline(x, 3, 3, hx("1b1512"))
    cv.hline(0, 15, 0, bk[4])
    # soot creeping up the brick
    for x in (3, 7, 11, 14):
        cv.put(x, 1, hx("1f1714"))
    cv.rect(0, 4, 1, 13, bk[2])
    cv.vline(0, 4, 13, bk[3])
    # hearth bed
    for y in range(4, 13):
        for x in range(2, 15):
            cv.put(x, y, r.choice([ash[1], ash[1], ash[0], ash[2]]))
    # floo fire burning through the ash, hottest at the centre
    for x, y in ((4, 9), (5, 6), (6, 11), (7, 5), (9, 10), (10, 6), (12, 8), (13, 10), (3, 7)):
        cv.put(x, y, grn[1]); cv.put(x, y - 1, grn[2])
    for x, y in ((6, 8), (7, 7), (8, 8), (9, 7), (8, 9), (7, 9)):
        cv.put(x, y, grn[2])
    cv.put(8, 8, grn[3]); cv.put(7, 8, grn[3])
    for x, y in ((5, 9), (10, 8), (6, 6)):
        cv.put(x, y, grn[0])
    cv.hline(3, 13, 12, hx("151314"))
    cv.hline(4, 12, 4, hx("1a1715"))
