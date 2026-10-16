"""Hand-shaded replacements for five placeholder textures.

    python build_scripts/generate_core_and_misc_textures.py [output_root]

Writes (under assets/hogwartsmagic/textures, or output_root/{item,block} when given):

  item/phoenix_feather_core.png    red-gold feather with a glowing ember tip
  item/unicorn_hair_core.png       a silver-white strand coiled into a loop, with a pale glow
  item/dragon_heartstring_core.png a dark red twisted sinew cord with a wet sheen
  item/clankers.png                three small iron bells hanging from a ring
  block/owl_perch.png              dark oak wood for the perch model

All 16x16, like the neighbouring item art. Uses texture_proposals/_generators/pixlib.py (light from
the upper left, 3-4 step ramps, a dark non-black outline). The owl perch model maps the post to
u0-2 / v2-16 (vertical grain), the arm to u0-10 / v6-8 and v0-2 (horizontal grain) and both caps to
u7-9 / v7-9, so the texture is laid out in those bands.
"""
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "texture_proposals", "_generators"))
from pixlib import Canvas, hx, mix, shade  # noqa: E402

TEX = os.path.join(HERE, "..", "src", "main", "resources", "assets", "hogwartsmagic", "textures")


def feather():
    cv = Canvas(16, 16, seed=3)
    # base to tip, bottom-left to top-right
    x0, y0, x1, y1 = 2.0, 14.0, 13.0, 2.0
    length = math.hypot(x1 - x0, y1 - y0)
    ux, uy = (x1 - x0) / length, (y1 - y0) / length
    px, py = -uy, ux
    ramp = [hx("5a0e0e"), hx("a01c14"), hx("d8381c"), hx("f0701c"), hx("fba81e"), hx("ffd84a")]
    for y in range(16):
        for x in range(16):
            dx, dy = x + .5 - x0, y + .5 - y0
            t = (dx * ux + dy * uy) / length
            d = dx * px + dy * py
            if t < 0 or t > 1.02:
                continue
            if t < 0.2:
                width = 0.45          # bare quill
            else:
                u = (t - 0.2) / 0.8
                width = 3.3 * math.sin(math.pi * min(1.0, u * 0.85 + 0.12)) ** 0.8
                width *= 1 - 0.55 * max(0, u - 0.8) / 0.2 * 0.5
            if abs(d) > width:
                continue
            if t < 0.2:
                cv.put(x, y, hx("e8d6a0") if d < 0 else hx("b89a60"))
                continue
            idx = min(5, int(t * 6.2))
            col = ramp[idx]
            if d > 0.35:
                col = shade(col, 0.78)     # shaded lower-right vane
            elif d < -1.2:
                col = mix(col, ramp[min(5, idx + 1)], 0.5)
            # barbs: faint diagonal striping
            if (int(round(t * 22 + d * 1.5)) % 3) == 0 and abs(d) > 0.9:
                col = shade(col, 0.88)
            cv.put(x, y, col)
    # rachis (spine) line, brighter gold
    for i in range(int(length * 3)):
        t = i / (length * 3.0)
        if t < 0.12:
            continue
        cv.put(int(x0 + ux * t * length), int(y0 + uy * t * length), hx("ffe27a") if t > 0.4 else hx("e8b04a"))
    cv.outline(hx("2a0808"))
    # ember glow at the tip
    for (x, y, c) in ((13, 2, "fff4b0"), (12, 3, "ffe27a"), (14, 1, "ffb030"), (14, 3, "ff8a24"),
                      (12, 1, "ff8a24"), (15, 0, "ffd060"), (11, 2, "ffcf50")):
        cv.put(x, y, hx(c))
    cv.put(15, 4, hx("ff9a30cc"))
    cv.put(10, 1, hx("ff6a2090"))
    cv.put(2, 14, hx("4a3a20"))
    return cv


def unicorn_hair():
    cv = Canvas(16, 16, seed=4)
    # one strand: a spiral loop of 1.3 turns (outer end flicks out to the lower left)
    pts = []
    for i in range(0, 161):
        t = i / 160.0
        a = math.pi * 0.9 + t * 2 * math.pi * 1.3
        r = 6.4 - 4.0 * t
        pts.append((7.5 + r * math.cos(a), 7.5 + r * math.sin(a)))
    cells = []
    for (x, y) in pts:
        c = (int(round(x)), int(round(y)))
        if not cells or cells[-1] != c:
            cells.append(c)
    n = len(cells)
    for k, (x, y) in enumerate(cells):
        # light from upper left: brighter where the strand runs along the top / left of the loop
        ang = math.atan2(y - 7.5, x - 7.5)
        lit = -math.cos(ang - math.radians(225))   # +1 toward upper-left
        lit = -lit
        col = hx("f2f6ff") if lit > 0.45 else hx("b4bfdc") if lit > -0.35 else hx("8792b8")
        cv.put(x, y, col)
    # the free tail tapers into a thin flick
    for (x, y, c) in ((2, 12, "d6def4"), (1, 13, "aab6d8"), (1, 14, "8792b8")):
        cv.put(x, y, hx(c))
    cv.outline(hx("38426e"))
    # glow motes
    for (x, y, c) in ((13, 2, "ffffff"), (2, 3, "dce4ff"), (14, 13, "dce4ff")):
        cv.put(x, y, hx(c))
    for (x, y) in ((13, 1), (13, 3), (12, 2), (14, 2)):
        cv.put(x, y, hx("c8d4ff80"))
    cv.put(2, 2, hx("c8d4ff80")); cv.put(2, 4, hx("c8d4ff80")); cv.put(1, 3, hx("c8d4ff80")); cv.put(3, 3, hx("c8d4ff80"))
    return cv


def dragon_heartstring():
    cv = Canvas(16, 16, seed=5)
    # a thick cord winding S-wise from lower-left to upper-right
    pts = []
    for i in range(0, 90):
        t = i / 89.0
        pts.append((1.5 + 13 * t, 13.5 - 11 * t + 3.0 * math.sin(t * 2 * math.pi * 1.1)))
    base = [hx("3a0608"), hx("6e0e12"), hx("a6201e"), hx("d8503c")]
    for k, (x, y) in enumerate(pts):
        twist = (k // 4) % 2          # alternating strands give the twisted-rope look
        for dy in (0, 1):
            c = base[1] if twist else base[2]
            if dy == 1:
                c = shade(c, 0.78)
            cv.put(int(x), int(y) + dy, c)
    # sheen: a bright wet line along the upper edge
    for k, (x, y) in enumerate(pts):
        if k % 9 in (1, 2, 3):
            cv.put(int(x), int(y), base[3])
        if k % 17 == 4:
            cv.put(int(x), int(y), hx("ff9a80"))
    cv.outline(hx("1a0204"))
    # frayed ends
    cv.put(0, 13, hx("6e0e12")); cv.put(0, 14, hx("3a0608")); cv.put(1, 15, hx("3a0608"))
    cv.put(15, 2, hx("a6201e")); cv.put(15, 1, hx("6e0e12")); cv.put(14, 0, hx("3a0608"))
    return cv


def clankers():
    cv = Canvas(16, 16, seed=6)
    iron = [hx("30343c"), hx("5a606c"), hx("8a919e"), hx("c4cad6")]
    # a wide iron hoop with three small bells hanging from its lower arc
    cv.ring(8, 4.6, 4.6, iron[2], t=1.3)
    for (x, y) in ((4, 2), (5, 1), (6, 0), (3, 3)):
        cv.put(x, y, iron[3])
    for (x, y) in ((11, 8), (12, 7), (10, 8), (12, 3), (12, 2)):
        cv.put(x, y, iron[1])
    cv.put(3, 8, iron[1]); cv.put(13, 8, iron[1]); cv.put(8, 9, iron[1])
    # (centre x, top y, width, height)
    for cx, top, w, h in ((3, 9, 3, 5), (13, 9, 3, 5), (8, 10, 4, 5)):
        x0 = cx - w // 2
        x1 = x0 + w - 1
        cv.rect(x0, top, x1, top + h - 2, iron[2])
        cv.vline(x0, top, top + h - 2, iron[3])
        cv.vline(x1, top, top + h - 2, iron[1])
        cv.hline(x0, x1, top + h - 1, iron[1])
        cv.put(x0, top + h - 1, iron[2])
        cv.put(cx, top + h - 1, iron[0])   # clapper showing in the mouth
    cv.outline(hx("14161a"))
    for (x, y, c) in ((7, 12, "7a5a3c"), (3, 12, "6a4c32"), (13, 12, "6a4c32")):
        cv.put(x, y, hx(c))
    return cv


def owl_perch():
    cv = Canvas(16, 16, seed=7)
    rng = random.Random(11)
    wood = [hx("3a2412"), hx("4e3219"), hx("63421f"), hx("7a5429"), hx("90663a")]
    # vertical-grain base, column tone varies; rows 0-1 and 6-7 are the arm's horizontal bands
    col_tone = [rng.randint(1, 3) for _ in range(16)]
    for y in range(16):
        for x in range(16):
            horizontal = y in (0, 1, 6, 7) and x < 10
            if horizontal:
                tone = 2 + ((x // 3 + y * 2) % 2) + (1 if y in (0, 6) else 0)
            else:
                tone = col_tone[x] + (1 if rng.random() < 0.12 else 0) - (1 if rng.random() < 0.12 else 0)
            tone = max(0, min(4, tone))
            cv.put(x, y, wood[tone])
    # grain streaks
    for x in range(16):
        y = rng.randint(2, 10)
        for k in range(rng.randint(3, 6)):
            if 2 <= y + k < 16:
                cv.put(x, y + k, wood[max(0, col_tone[x] - 1)])
    # knots and highlights on the post / arm
    cv.put(1, 10, wood[0]); cv.put(1, 11, wood[1]); cv.put(0, 11, wood[1])
    cv.put(4, 6, wood[0]); cv.put(5, 7, wood[1]); cv.put(8, 0, wood[4]); cv.put(2, 0, wood[4])
    for y in range(2, 16):
        cv.put(0, y, shade(cv.get(0, y), 1.15))   # lit left edge of the post
        cv.put(1, y, shade(cv.get(1, y), 0.85))   # shaded right edge
    for x in range(0, 10):
        cv.put(x, 6, shade(cv.get(x, 6), 1.15))
        cv.put(x, 7, shade(cv.get(x, 7), 0.82))
    cv.put(7, 7, wood[4]); cv.put(8, 7, wood[3]); cv.put(7, 8, wood[3]); cv.put(8, 8, wood[2])
    return cv


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else TEX
    jobs = {"item/phoenix_feather_core": feather, "item/unicorn_hair_core": unicorn_hair,
            "item/dragon_heartstring_core": dragon_heartstring, "item/clankers": clankers,
            "block/owl_perch": owl_perch}
    for name, fn in jobs.items():
        path = os.path.normpath(os.path.join(root, name + ".png"))
        os.makedirs(os.path.dirname(path), exist_ok=True)
        fn().save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
