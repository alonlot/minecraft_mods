"""Paint the Whomping Willow's texture from its own .bbmodel.

    python build_scripts/generate_whomping_willow_texture.py
    python build_scripts/generate_whomping_willow_texture.py --uv-debug

Like the other texture generators here this reads each cube's baked per-face rectangle out of
`element["faces"][side]["uv"]` rather than carrying a hand-copied UV table, so the paint stays
honest after a re-pack. Run generate_whomping_willow_model.py first.

`--uv-debug` flat-fills each face direction with a distinct hue instead of painting properly. That
is the cheapest possible check that the box-UV layout is right: summon the tree and the trunk
front should be RED and its top WHITE. A face-order mistake found that way costs five minutes;
found after a real paint it costs a full repaint.

NOTE on the box-UV layout, because there is a wrong version of it in this directory: the comment at
the top of generate_giant_texture.py has up/down and east/west SWAPPED. It is harmless there because
that script also reads baked rects, but do not copy it. Verified against ModelPart.Cube in the 1.21
sources, the real layout is

    down  (u+d,     v)      w x d          west  (u,        v+d)  d x h
    up    (u+d+w,   v)      w x d          north (u+d,      v+d)  w x h   <- the face you look at
                                           east  (u+d+w,    v+d)  d x h
                                           south (u+2d+w,   v+d)  w x h
"""

import json
import math
import os
import random
import re
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BBMODEL = os.path.join(ROOT, "blockbench_models", "whomping_willow.bbmodel")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                   "textures", "entity", "whomping_willow.png")

rng = random.Random(0x5EEDED)

# A grey-brown storm-beaten willow, not a warm oak. The leaves are deliberately drab and yellowing:
# this tree is hostile, not pretty.
BARK_DARK = (38, 31, 24)
BARK_MID = (66, 55, 42)
BARK_LIGHT = (98, 84, 65)
BARK_GREY = (112, 106, 94)
MOSS = (58, 74, 44)
ROOT_DARK = (32, 26, 20)
ROOT_MID = (54, 44, 33)
EARTH = (46, 36, 26)
LEAF_DARK = (44, 56, 33)
LEAF_MID = (74, 92, 48)
LEAF_LIGHT = (108, 124, 68)
LEAF_SICK = (128, 118, 54)
MAW_BLACK = (16, 13, 10)
MAW_GUM = (72, 50, 36)
TOOTH = (196, 172, 132)
SAP = (86, 62, 20)

DEBUG_FACE_COLOURS = {
    "north": (220, 40, 40), "south": (40, 200, 60), "west": (50, 90, 230),
    "east": (230, 210, 50), "up": (245, 245, 245), "down": (25, 25, 25),
}


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def jitter(c, amount):
    return tuple(max(0, min(255, c[i] + rng.randint(-amount, amount))) for i in range(3))


def kind_of(name):
    """Material by name prefix. The names are generated, so a regex is honest here in a way the
    giant's hand-maintained name sets are not."""
    if re.match(r"^crown_leaf|^branch\d_leaf", name):
        return "leaf"
    if re.match(r"^maw_gum", name):
        return "maw"
    if re.match(r"^root_", name):
        return "root"
    if re.match(r"^branch\d_s[45]_c$", name):
        return "twig"
    return "bark"


def face_rects(element):
    """Blockbench stores [x1,y1,x2,y2] and may store them flipped; normalise to (x, y, w, h)."""
    out = {}
    for side, face in element["faces"].items():
        x1, y1, x2, y2 = face["uv"]
        out[side] = (int(math.floor(min(x1, x2))), int(math.floor(min(y1, y2))),
                     int(math.ceil(abs(x2 - x1))), int(math.ceil(abs(y2 - y1))))
    return out


def fill(px, x, y, w, h, colour):
    for i in range(x, x + w):
        for j in range(y, y + h):
            px[i, j] = colour + (255,)


def paint_bark(px, x, y, w, h, grey=False):
    """Vertical fissures with a long correlation length, so cracks run and branch instead of
    reading as per-pixel noise. This is the difference between bark and static."""
    base = BARK_GREY if grey else BARK_MID
    fissure = [rng.random() for _ in range(max(1, w))]
    for i in range(w):
        # smooth the per-column value so a crack persists across several columns
        s = sum(fissure[max(0, i - 1):i + 2]) / len(fissure[max(0, i - 1):i + 2])
        for j in range(h):
            t = j / max(1, h - 1)
            c = lerp(BARK_LIGHT, base, t * 0.75 + 0.15)
            if s < 0.22:
                c = lerp(c, BARK_DARK, 0.85)          # deep fissure
            elif s < 0.34:
                c = lerp(c, BARK_DARK, 0.4)
            if rng.random() < 0.05:
                c = lerp(c, BARK_DARK, 0.5)            # horizontal checking
            if t > 0.72 and rng.random() < 0.10:
                c = lerp(c, MOSS, 0.55)                # moss creeps up from the damp base
            px[x + i, y + j] = jitter(c, 5) + (255,)


def paint_twig(px, x, y, w, h):
    for i in range(w):
        for j in range(h):
            c = lerp(BARK_GREY, BARK_MID, rng.random() * 0.6)
            if rng.random() < 0.08:
                c = lerp(c, BARK_DARK, 0.6)
            px[x + i, y + j] = jitter(c, 6) + (255,)


def paint_root(px, x, y, w, h):
    for i in range(w):
        for j in range(h):
            t = j / max(1, h - 1)
            c = lerp(ROOT_MID, ROOT_DARK, t * 0.6)
            if t > 0.5 and rng.random() < 0.45:
                c = lerp(c, EARTH, (t - 0.5) * 1.4)    # soil packed against the buttress
            if rng.random() < 0.06:
                c = lerp(c, MOSS, 0.5)
            px[x + i, y + j] = jitter(c, 5) + (255,)


def paint_leaf(px, x, y, w, h):
    """Clumped foliage with the outer ring eroded to alpha 0.

    HierarchicalModel renders as entityCutoutNoCull, so a transparent texel genuinely cuts out --
    which is what turns 22 flat slabs into a ragged canopy silhouette. This is the single biggest
    visual win available without a modelling tool.
    """
    for i in range(w):
        for j in range(h):
            edge = min(i, j, w - 1 - i, h - 1 - j)
            if edge == 0 and rng.random() < 0.72:
                px[x + i, y + j] = (0, 0, 0, 0)
                continue
            if edge == 1 and rng.random() < 0.22:
                px[x + i, y + j] = (0, 0, 0, 0)
                continue
            r = rng.random()
            c = LEAF_MID
            if r < 0.28:
                c = LEAF_DARK
            elif r > 0.82:
                c = LEAF_LIGHT
            elif r > 0.76:
                c = LEAF_SICK
            px[x + i, y + j] = jitter(c, 7) + (255,)


def paint_maw(px, x, y, w, h):
    for i in range(w):
        for j in range(h):
            c = MAW_BLACK
            if rng.random() < 0.10:
                c = lerp(MAW_GUM, SAP, rng.random())
            if j <= 1 and rng.random() < 0.45:
                c = TOOTH                              # splintered rim
            px[x + i, y + j] = jitter(c, 4) + (255,)


PAINTERS = {"bark": paint_bark, "twig": paint_twig, "root": paint_root,
            "leaf": paint_leaf, "maw": paint_maw}


def main():
    debug = "--uv-debug" in sys.argv
    model = json.load(open(BBMODEL, encoding="utf-8"))
    res = model["resolution"]
    img = Image.new("RGBA", (res["width"], res["height"]), (0, 0, 0, 0))
    px = img.load()

    # Fixed order so the RNG stream is stable across runs, and so a re-run is byte-identical.
    elements = sorted(model["elements"], key=lambda e: e["name"])
    painted = set()
    for element in elements:
        rects = face_rects(element)
        offset = tuple(element["uv_offset"])
        if offset in painted:
            continue                                   # shared UV class: paint it once
        painted.add(offset)
        kind = kind_of(element["name"])

        if debug:
            for side, (x, y, w, h) in rects.items():
                if w and h:
                    fill(px, x, y, w, h, DEBUG_FACE_COLOURS[side])
            continue

        # Flood the whole footprint with the darkest tone first so no seam can show through.
        floor_colour = {"leaf": LEAF_DARK, "maw": MAW_BLACK, "root": ROOT_DARK}.get(kind, BARK_DARK)
        xs = [r[0] for r in rects.values()]
        ys = [r[1] for r in rects.values()]
        xe = [r[0] + r[2] for r in rects.values()]
        ye = [r[1] + r[3] for r in rects.values()]
        fill(px, min(xs), min(ys), max(xe) - min(xs), max(ye) - min(ys), floor_colour)

        painter = PAINTERS[kind]
        for side, (x, y, w, h) in rects.items():
            if not w or not h:
                continue
            if kind == "bark" and side in ("up", "down"):
                paint_bark(px, x, y, w, h, grey=True)   # cut ends read as pale heartwood-ish grey
            else:
                painter(px, x, y, w, h)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("wrote %s (%dx%d), %d cubes, %d unique UV footprints%s"
          % (os.path.relpath(OUT, ROOT), res["width"], res["height"],
             len(elements), len(painted), "  [UV DEBUG]" if debug else ""))


if __name__ == "__main__":
    main()
