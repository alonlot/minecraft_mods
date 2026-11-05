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
KNOT_PALE = (156, 132, 96)
KNOT_DARK = (74, 58, 38)
KNOT_CORE = (40, 30, 20)

DEBUG_FACE_COLOURS = {
    "north": (220, 40, 40), "south": (40, 200, 60), "west": (50, 90, 230),
    "east": (230, 210, 50), "up": (245, 245, 245), "down": (25, 25, 25),
}


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def jitter(c, amount):
    return tuple(max(0, min(255, c[i] + rng.randint(-amount, amount))) for i in range(3))


def kind_of(element):
    """The generator writes each element's material into the .bbmodel as "kind". The name regex is
    the fallback for a model saved back out of Blockbench, which drops unknown keys."""
    if element.get("kind"):
        return element["kind"]
    name = element["name"]
    if "_cur" in name:
        return "curtain"
    if "leaf" in name:
        return "leaf"
    if re.match(r"^crown_leaf|^branch\d_leaf", name):
        return "leaf"
    if re.match(r"^maw_gum", name):
        return "maw"
    if re.match(r"^knot_", name):
        return "knot"
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
    """Deep vertical fissures between raised plates, with NO top-to-bottom gradient.

    An earlier version shaded each face light at the top and dark at the bottom. Every trunk ring
    is its own face, so the bole came out banded like a stack of barrels -- the single biggest thing
    that made it read as boxes rather than one trunk. Tone now varies across the face only, and the
    fissure pattern wanders a column at a time so cracks run the full height and fork."""
    base = BARK_GREY if grey else BARK_MID
    crack = [rng.random() for _ in range(max(1, w))]
    for i in range(1, w):
        crack[i] = crack[i] * 0.55 + crack[i - 1] * 0.45     # correlated across columns
    for i in range(w):
        drift = 0
        for j in range(h):
            if rng.random() < 0.06:
                drift = max(-1, min(1, drift + rng.choice((-1, 1))))
            ci = max(0, min(w - 1, i + drift))
            s = crack[ci]
            plate = 0.5 + 0.5 * math.sin(i * 0.9 + crack[ci] * 3.0)
            c = lerp(BARK_MID if not grey else BARK_GREY, BARK_LIGHT, plate * 0.45)
            if s < 0.30:
                c = lerp(c, BARK_DARK, 0.85)                  # deep fissure
            elif s < 0.40:
                c = lerp(c, BARK_DARK, 0.45)
            elif s > 0.80 and rng.random() < 0.5:
                c = lerp(c, BARK_GREY, 0.5)                   # weathered plate edge
            if rng.random() < 0.035:
                c = lerp(c, MOSS, 0.6)
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


def paint_curtain(px, x, y, w, h):
    """A hanging willow streamer: separate strands of narrow leaves, each its own length, with clear
    gaps between them. Cutout makes the gaps real, so the curtain reads as a fringe you can see the
    sky through rather than a green sheet."""
    strand_len = []
    for i in range(w):
        present = rng.random() < 0.62 or (i > 0 and strand_len[-1] == 0 and rng.random() < 0.5)
        strand_len.append(int(h * (0.45 + rng.random() * 0.55)) if present else 0)
    for i in range(w):
        for j in range(h):
            px[x + i, y + j] = (0, 0, 0, 0)
        L = strand_len[i]
        for j in range(min(h, 2)):                         # where it hangs from the twig
            if rng.random() < 0.8:
                px[x + i, y + j] = jitter(LEAF_DARK, 6) + (255,)
        phase = rng.random()
        for j in range(L):
            t = j / max(1, h - 1)
            # leaves come in beads along the strand; the gaps between beads are open
            if (j + int(phase * 3)) % 4 == 3 and rng.random() < 0.7:
                continue
            r = rng.random()
            c = LEAF_MID if r < 0.5 else (LEAF_DARK if r < 0.75 else LEAF_LIGHT)
            if t > 0.6 and rng.random() < 0.25:
                c = LEAF_SICK
            c = lerp(c, LEAF_DARK, 0.25 * (1.0 - t))       # shade under the canopy
            px[x + i, y + j] = jitter(c, 8) + (255,)


def paint_tooth(px, x, y, w, h):
    for i in range(w):
        for j in range(h):
            t = j / max(1, h - 1)
            c = lerp(TOOTH, KNOT_DARK, max(0.0, t - 0.55) * 1.6)
            px[x + i, y + j] = jitter(c, 6) + (255,)


def paint_maw(px, x, y, w, h):
    for i in range(w):
        for j in range(h):
            c = MAW_BLACK
            if rng.random() < 0.10:
                c = lerp(MAW_GUM, SAP, rng.random())
            if j <= 1 and rng.random() < 0.45:
                c = TOOTH                              # splintered rim
            px[x + i, y + j] = jitter(c, 4) + (255,)


def paint_knot(px, x, y, w, h):
    """Pale scarred wood in concentric rings. It has to be obviously different from the bark around
    it: the guide tells the player to look low on the trunk for the knot, so it must be findable at
    a glance rather than something you discover by right-clicking every face of a hostile tree."""
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    for i in range(w):
        for j in range(h):
            r = math.hypot((i - cx) / max(1.0, cx), (j - cy) / max(1.0, cy))
            ring = math.sin(r * 7.0)
            c = lerp(KNOT_PALE, KNOT_DARK, min(1.0, r * 0.7))
            if ring > 0.35:
                c = lerp(c, KNOT_DARK, 0.55)
            if r < 0.18:
                c = KNOT_CORE
            px[x + i, y + j] = jitter(c, 4) + (255,)


PAINTERS = {"bark": paint_bark, "twig": paint_twig, "root": paint_root,
            "leaf": paint_leaf, "maw": paint_maw, "knot": paint_knot,
            "curtain": paint_curtain, "tooth": paint_tooth}


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
        kind = kind_of(element)

        if debug:
            for side, (x, y, w, h) in rects.items():
                if w and h:
                    fill(px, x, y, w, h, DEBUG_FACE_COLOURS[side])
            continue

        # Flood the whole footprint with the darkest tone first so no seam can show through.
        floor_colour = {"leaf": LEAF_DARK, "maw": MAW_BLACK, "root": ROOT_DARK,
                        "knot": KNOT_DARK, "tooth": TOOTH}.get(kind, BARK_DARK)
        if kind != "curtain":
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
