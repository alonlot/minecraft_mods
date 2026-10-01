"""Third pass -- the last two icons that still did not read."""
import os
from pixlib import Canvas, hx, ramp, shade, shaft

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "proposed", "item")


def tex(name, size=16, scale=1):
    def deco(fn):
        cv = Canvas(size, size, seed=abs(hash(name)) % 9999)
        fn(cv)
        im = cv.image()
        if scale > 1:
            im = im.resize((size * scale, size * scale))
        im.save(os.path.join(OUT, name + ".png"))
        print("redrew", name)
        return fn
    return deco


@tex("ravenclaw_diadem_fragment")
def _(cv):
    """A broken-off segment of the band -- jagged at BOTH ends, so it reads as a fragment
    rather than an intact tiara. Sapphire sits in the band, not floating above it."""
    s = ramp("39414f", "6f7d92", "aab8cc", "e8f0fa")
    blu = ramp("102455", "244aa8", "5f9bff", "cfe4ff")
    # the band: a curved segment running lower-left to upper-right
    band = [(2, 12), (3, 10), (5, 8), (7, 7), (9, 6), (11, 6), (13, 7)]
    for i in range(len(band) - 1):
        cv.line(*band[i], *band[i + 1], s[2])                       # lit top edge
        cv.line(band[i][0], band[i][1] + 1, band[i + 1][0], band[i + 1][1] + 1, s[1])
        cv.line(band[i][0], band[i][1] + 2, band[i + 1][0], band[i + 1][1] + 2, s[0])
    # filigree scrolls riding the band
    cv.put(4, 8, s[3]); cv.put(6, 6, s[3]); cv.put(10, 5, s[3])
    cv.put(8, 8, s[0]); cv.put(12, 8, s[0])
    # sapphire set into the band
    cv.rect(8, 4, 9, 6, blu[1])
    cv.put(8, 4, blu[3]); cv.put(9, 4, blu[2])
    cv.put(9, 6, blu[0])
    cv.put(7, 5, s[2]); cv.put(10, 5, s[2])                          # claws
    # jagged snap at both ends
    cv.put(14, 8, hx("2c313d")); cv.put(13, 9, hx("242835"))
    cv.put(1, 13, hx("2c313d")); cv.put(2, 14, hx("242835"))
    # the horcrux tarnish creeping out of the break
    cv.put(12, 8, hx("2a2038")); cv.put(3, 12, hx("2a2038"))
    cv.outline(hx("12161e"))


@tex("broom_workbench", scale=4)
def _(cv):
    """Same 3/4 bench read as the wand bench, with a bristle bundle and twine on top."""
    w = ramp("2e1a10", "4a2a17", "6b3f22", "8c5730")
    br = ramp("6f5620", "a8842f", "d8b45e", "f0d68e")
    # bench top in slight perspective, then the apron
    cv.poly([(2, 7), (13, 7), (15, 9), (1, 9)], w[3])
    cv.hline(1, 15, 9, w[2])
    cv.rect(1, 10, 15, 11, w[1])
    cv.hline(1, 15, 11, w[0])
    # legs
    cv.rect(2, 12, 3, 15, w[1]); cv.rect(12, 12, 13, 15, w[1])
    cv.vline(3, 12, 15, w[0]); cv.vline(13, 12, 15, w[0])
    cv.rect(4, 14, 11, 14, w[0])
    # bristle bundle standing on the bench, splayed at the top
    for i, x in enumerate(range(4, 11)):
        top = 2 + abs(7 - x) // 2
        c = br[2] if i % 2 else br[1]
        cv.vline(x, top, 6, c)
        cv.put(x, top, br[3])
    cv.put(4, 4, br[0]); cv.put(10, 4, br[0])
    # twine binding the bundle
    cv.hline(4, 10, 5, hx("efe3c0"))
    cv.hline(4, 10, 6, hx("b3a479"))
    # a shaped handle stub poking out of the bundle
    cv.put(7, 1, hx("c3a066")); cv.put(7, 0, hx("e6cd9c"))
    # shavings on the bench
    cv.put(2, 8, hx("d6bb8a")); cv.put(13, 8, hx("d6bb8a"))
    cv.outline(hx("120a06"))
