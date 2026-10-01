"""The last two holdouts, recomposed rather than re-detailed.

broom_workbench: the bristles were a fan of thin lines, which reads as a cloud. Solid
  tapered wedge instead, the way vanilla draws a brush.
ravenclaw_diadem_fragment: a symmetric arch with a gem on top reads as an animal with a
  head, every time. Breaking the symmetry to a diagonal shard kills that read.
"""
import os
from pixlib import Canvas, hx, ramp, shade

ITEM = os.path.join(os.path.dirname(os.path.abspath(__file__)), "proposed", "item")


def tex(name, size=16, scale=1):
    def deco(fn):
        cv = Canvas(size, size, seed=abs(hash(name)) % 9999)
        fn(cv)
        im = cv.image()
        if scale > 1:
            im = im.resize((size * scale, size * scale))
        im.save(os.path.join(ITEM, name + ".png"))
        print("redrew", name)
        return fn
    return deco


@tex("broom_workbench", scale=4)
def _(cv):
    w = ramp("2e1a10", "4a2a17", "6b3f22", "8c5730")
    st = ramp("6b4f2c", "9a7845", "c3a066", "e6cd9c")
    br = ramp("5e4a1c", "8f6f2a", "bd943c", "e2b95c", "f5dc96")
    # --- broom laid on the bench: solid bristle wedge, then the handle
    span = {2: (3, 6), 3: (2, 6), 4: (1, 7), 5: (1, 7), 6: (1, 7), 7: (2, 7), 8: (3, 7)}
    for y, (x0, x1) in span.items():
        for x in range(x0, x1 + 1):
            cv.put(x, y, br[2])
    # straw runs lengthwise through the head
    for y in (2, 4, 6, 8):
        for x in range(span[y][0], span[y][1] + 1):
            cv.put(x, y, br[3])
    for y in (3, 5, 7):
        cv.put(span[y][0], y, br[1])
    cv.put(1, 4, br[4]); cv.put(1, 6, br[0]); cv.put(3, 8, br[0])
    # twine binding where the head meets the stick
    cv.vline(7, 3, 7, hx("efe3c0"))
    cv.vline(8, 3, 7, hx("b3a479"))
    # handle
    cv.line(8, 5, 15, 2, st[2])
    cv.line(8, 6, 15, 3, st[1])
    cv.line(9, 4, 15, 1, st[3])
    cv.put(15, 1, st[3])
    # --- bench
    cv.rect(0, 10, 15, 10, w[3])
    cv.rect(0, 11, 15, 12, w[2])
    cv.hline(0, 15, 12, w[0])
    cv.rect(2, 13, 3, 15, w[1]); cv.rect(12, 13, 13, 15, w[1])
    cv.vline(3, 13, 15, w[0]); cv.vline(13, 13, 15, w[0])
    cv.rect(4, 14, 11, 14, w[1])
    cv.put(6, 10, hx("d6bb8a")); cv.put(11, 10, hx("d6bb8a"))
    cv.outline(hx("120a06"))


@tex("ravenclaw_diadem_fragment")
def _(cv):
    """A diagonal shard of the band -- asymmetric, so it cannot read as a creature."""
    s = ramp("2f3644", "586477", "8f9db2", "cbd8e9", "f2f8ff")
    blu = ramp("0e2050", "22499e", "5f9bff", "cfe4ff")
    # band running lower-left to upper-right, 3 texels thick, gently curved
    top = {2: 12, 3: 11, 4: 11, 5: 10, 6: 9, 7: 9, 8: 8, 9: 7, 10: 7, 11: 6, 12: 5, 13: 5}
    for x, y in top.items():
        cv.put(x, y, s[3])
        cv.put(x, y + 1, s[2])
        cv.put(x, y + 2, s[1])
    # engraved bead line
    for x in (3, 6, 9, 12):
        cv.put(x, top[x] + 1, s[4])
    for x in (5, 8, 11):
        cv.put(x, top[x] + 2, s[0])
    # sapphire seated two-thirds along, sitting IN the band
    cv.rect(9, 5, 10, 6, blu[1])
    cv.put(9, 5, blu[3]); cv.put(10, 6, blu[0])
    cv.put(8, 6, s[3]); cv.put(11, 5, s[3])
    # torn ends -- ragged both sides, metal darkened at the snap
    cv.put(2, 14, (0, 0, 0, 0)); cv.put(13, 5, (0, 0, 0, 0))
    cv.put(1, 13, hx("2a3040")); cv.put(1, 14, hx("20242f"))
    cv.put(14, 6, hx("2a3040")); cv.put(14, 7, hx("20242f"))
    cv.put(3, 13, s[0]); cv.put(12, 6, s[0])
    # a chip that broke away with it
    cv.put(5, 14, s[2]); cv.put(6, 14, s[1]); cv.put(5, 15, s[0])
    # horcrux tarnish creeping from the break
    cv.put(4, 12, hx("2b2038")); cv.put(12, 7, hx("2b2038"))
    cv.outline(hx("12161e"))
