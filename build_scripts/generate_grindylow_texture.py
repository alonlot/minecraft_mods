"""Generate the Grindylow's entity textures.

Reads the UV layout straight out of ``blockbench_models/grindylow.bbmodel`` — each
cube's baked per-face rectangle lives in ``element["faces"][side]["uv"]`` already
in texture pixels — so a re-pack in Blockbench only needs a re-run here.

Two files come out:

    grindylow.png       the hide: sickly pale green, slick and wet-looking, with
                        a bloated pale belly and darker mottling on top.
    grindylow_eyes.png  emissive overlay for the two bulging eyes.

Built to the Fantastic Beasts concept art rather than the book's one-line
description: olive-khaki, a head far too big for the body, eyes set wide and
high, a mouth full of long needles, curved spines swept back off the skull, and
— the thing that decides the whole silhouette — a mass of curling tentacles
where legs ought to be. Nothing here is allowed to look sturdy: it should read
as something that would snap if you pulled on it, which is exactly how you get
away from one.
"""

import json
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL = os.path.join(ROOT, "blockbench_models", "grindylow.bbmodel")
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                       "textures", "entity")

SIDES = ("north", "south", "east", "west", "up", "down")

# --------------------------------------------------------------------------- #
# palette
# --------------------------------------------------------------------------- #

HIDE = (116, 116, 80)
HIDE_DARK = (72, 74, 50)
HIDE_LIGHT = (148, 146, 106)
BELLY = (170, 164, 126)
BELLY_DARK = (130, 126, 96)
SLIME = (150, 156, 104)

HORN = (86, 78, 58)
HORN_TIP = (40, 36, 28)
SUCKER = (188, 184, 152)
SUCKER_DARK = (96, 92, 70)

TOOTH = (226, 222, 196)
TOOTH_DARK = (150, 148, 128)
GUM = (96, 46, 48)
GUM_DARK = (58, 26, 30)

CLAW = (52, 48, 40)
SOCKET = (30, 34, 26)
EYE = (176, 214, 78)
EYE_HOT = (226, 246, 168)
PUPIL = (10, 12, 8)

SPINE_CUBES = {"spine_l1", "spine_l2", "spine_l3",
               "spine_r1", "spine_r2", "spine_r3",
               "spine_top_l", "spine_top_r", "crest"}
TOOTH_CUBES = {"teeth_top", "teeth_bottom"}
EYE_CUBES = {"eye_left", "eye_right"}
CLAW_CUBES = {"claw_l1", "claw_l2", "claw_l3",
              "claw_r1", "claw_r2", "claw_r3"}
BELLY_CUBES = {"torso", "paunch"}


# --------------------------------------------------------------------------- #
# painting primitives
# --------------------------------------------------------------------------- #

def jitter(colour, rng, amount):
    n = rng.uniform(-amount, amount)
    return tuple(max(0, min(255, int(c + c * n))) for c in colour)


def rect_of(uv):
    x0, y0, x1, y1 = uv
    return (int(math.floor(min(x0, x1))), int(math.floor(min(y0, y1))),
            int(math.ceil(max(x0, x1))), int(math.ceil(max(y0, y1))))


def flat(px, r, colour, rng, amount=0.10):
    x0, y0, x1, y1 = r
    for y in range(y0, y1):
        for x in range(x0, x1):
            px[x, y] = jitter(colour, rng, amount) + (255,)


def hide(px, r, base, dark, light, rng, wet=True):
    """Slick amphibian skin: mottled, with a wet highlight along the top edge."""
    x0, y0, x1, y1 = r
    w, h = x1 - x0, y1 - y0
    if w <= 0 or h <= 0:
        return
    flat(px, r, base, rng, 0.10)
    for _ in range(max(1, int(w * h / 7))):
        cx, cy = rng.uniform(x0, x1), rng.uniform(y0, y1)
        rad = rng.uniform(0.8, 2.0)
        tone = dark if rng.random() < 0.66 else light
        for y in range(y0, y1):
            for x in range(x0, x1):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= rad * rad:
                    px[x, y] = jitter(tone, rng, 0.10) + (255,)
    if wet and h >= 2:
        for x in range(x0, x1):
            if rng.random() < 0.55:
                px[x, y0] = jitter(SLIME, rng, 0.10) + (255,)


def needles(px, r, rng, pointing_down):
    """A row of fine needle teeth against dark gum."""
    x0, y0, x1, y1 = r
    w, h = x1 - x0, y1 - y0
    if w <= 0 or h <= 0:
        return
    flat(px, r, GUM_DARK, rng, 0.14)
    for x in range(x0, x1):
        if (x - x0) % 2 == 1:
            continue
        length = max(1, int(h * rng.uniform(0.5, 0.95)))
        if pointing_down:
            span = range(y1 - length, y1)
        else:
            span = range(y0, y0 + length)
        for y in span:
            px[x, y] = jitter(TOOTH, rng, 0.08) + (255,)
        if length < h:
            edge = y1 - length - 1 if pointing_down else y0 + length
            if y0 <= edge < y1:
                px[x, edge] = jitter(TOOTH_DARK, rng, 0.10) + (255,)


def spike(px, r, rng):
    """Horn: dark at the base, near-black at the tip."""
    x0, y0, x1, y1 = r
    h = y1 - y0
    if h <= 0 or x1 - x0 <= 0:
        return
    for i, y in enumerate(range(y0, y1)):
        t = i / max(1, h - 1)
        colour = tuple(int(HORN[c] + (HORN_TIP[c] - HORN[c]) * t) for c in range(3))
        for x in range(x0, x1):
            px[x, y] = jitter(colour, rng, 0.09) + (255,)


def tentacle(px, r, rng):
    """Tentacle: dark and mottled on the outside, a row of pale suckers within.

    The suckers run down the middle of the strip rather than covering it, which
    is what stops six identical limbs reading as six identical sticks.
    """
    x0, y0, x1, y1 = r
    w, h = x1 - x0, y1 - y0
    if w <= 0 or h <= 0:
        return
    hide(px, r, HIDE_DARK, HIDE_DARK, HIDE, rng, wet=False)
    if w < 2:
        return
    mid = x0 + w // 2
    for i, y in enumerate(range(y0, y1)):
        if i % 2:
            continue
        px[mid, y] = jitter(SUCKER, rng, 0.10) + (255,)
        if mid + 1 < x1:
            px[mid + 1, y] = jitter(SUCKER_DARK, rng, 0.12) + (255,)


def finger(px, r, rng):
    """Long spindly claw: pale, knuckled, tipped with a dark point."""
    x0, y0, x1, y1 = r
    h = y1 - y0
    if h <= 0 or x1 - x0 <= 0:
        return
    flat(px, r, BELLY_DARK, rng, 0.12)
    for i, y in enumerate(range(y0, y1)):
        tone = HIDE_LIGHT if i % 3 == 0 else BELLY_DARK
        for x in range(x0, x1):
            px[x, y] = jitter(tone, rng, 0.10) + (255,)
    # The claw is at the far end of the finger, which is the bottom of the strip.
    for y in range(max(y0, y1 - 2), y1):
        for x in range(x0, x1):
            px[x, y] = jitter(CLAW, rng, 0.10) + (255,)


def socket(px, r, rng):
    x0, y0, x1, y1 = r
    flat(px, r, SOCKET, rng, 0.16)


def living_eye(px, r, rng):
    """A big round yellow eye with a small hard pupil - a frog's, not a snake's."""
    x0, y0, x1, y1 = r
    w, h = x1 - x0, y1 - y0
    if w <= 0 or h <= 0:
        return
    cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
    for y in range(y0, y1):
        for x in range(x0, x1):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d > w / 2.0:
                continue
            px[x, y] = jitter(EYE_HOT if d > w / 2.0 - 1.0 else EYE, rng, 0.07) + (255,)
    px[int(cx), int(cy)] = PUPIL + (255,)
    if w >= 3:
        px[int(cx) - 1, int(cy)] = PUPIL + (255,)


# --------------------------------------------------------------------------- #
# build
# --------------------------------------------------------------------------- #

def load_layout():
    with open(MODEL, "r", encoding="utf-8") as fh:
        data = json.load(fh)
    res = data["resolution"]
    cubes = {}
    for el in data["elements"]:
        if el.get("type", "cube") != "cube":
            continue
        cubes[el["name"]] = {s: rect_of(el["faces"][s]["uv"]) for s in SIDES}
    return res["width"], res["height"], cubes


def build():
    tex_w, tex_h, cubes = load_layout()
    rng = random.Random(31337)
    img = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
    eyes = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
    px, epx = img.load(), eyes.load()

    for name, faces in sorted(cubes.items()):
        if name in SPINE_CUBES:
            for s in SIDES:
                spike(px, faces[s], rng)

        elif name.startswith("tent"):
            for s in SIDES:
                tentacle(px, faces[s], rng)

        elif name in TOOTH_CUBES:
            down = name == "teeth_top"      # upper teeth hang down
            for s in ("north", "south", "east", "west"):
                needles(px, faces[s], rng, down)
            flat(px, faces["up"], GUM if down else GUM_DARK, rng, 0.15)
            flat(px, faces["down"], GUM_DARK if down else GUM, rng, 0.15)

        elif name in CLAW_CUBES:
            for s in SIDES:
                finger(px, faces[s], rng)

        elif name in EYE_CUBES:
            outward = "east" if name == "eye_left" else "west"
            for s in SIDES:
                socket(px, faces[s], rng)
            for s in ("north", outward):
                living_eye(epx, faces[s], rng)

        elif name in BELLY_CUBES:
            for s in ("north", "east", "west", "up"):
                hide(px, faces[s], HIDE, HIDE_DARK, HIDE_LIGHT, rng)
            # A bloated pale underside, and the same on the front of the belly.
            hide(px, faces["south"], BELLY, BELLY_DARK, BELLY, rng, wet=False)
            hide(px, faces["down"], BELLY, BELLY_DARK, BELLY, rng, wet=False)

        elif name == "jaw_bone":
            for s in ("north", "south", "east", "west"):
                hide(px, faces[s], HIDE, HIDE_DARK, HIDE_LIGHT, rng)
            flat(px, faces["up"], GUM, rng, 0.16)          # mouth floor
            hide(px, faces["down"], BELLY, BELLY_DARK, BELLY, rng, wet=False)

        elif name == "skull":
            for s in ("north", "south", "east", "west"):
                hide(px, faces[s], HIDE, HIDE_DARK, HIDE_LIGHT, rng)
            hide(px, faces["up"], HIDE_DARK, HIDE_DARK, HIDE, rng)
            hide(px, faces["down"], BELLY, BELLY_DARK, BELLY, rng, wet=False)
            # Dark hollows where the eyes sit, so the sockets read even before
            # the emissive layer goes on.
            x0, y0, x1, y1 = faces["north"]
            for y in range(y0, min(y0 + 3, y1)):
                for x in range(x0, x1):
                    if rng.random() < 0.5:
                        px[x, y] = jitter(HIDE_DARK, rng, 0.12) + (255,)

        else:
            for s in ("north", "south", "east", "west"):
                hide(px, faces[s], HIDE, HIDE_DARK, HIDE_LIGHT, rng)
            hide(px, faces["up"], HIDE_DARK, HIDE_DARK, HIDE, rng)
            hide(px, faces["down"], BELLY, BELLY_DARK, BELLY, rng, wet=False)

    os.makedirs(OUT_DIR, exist_ok=True)
    base_path = os.path.join(OUT_DIR, "grindylow.png")
    eyes_path = os.path.join(OUT_DIR, "grindylow_eyes.png")
    img.save(base_path)
    eyes.save(eyes_path)
    print("wrote", base_path)
    print("wrote", eyes_path)
    print("layout: %d cubes on a %dx%d sheet" % (len(cubes), tex_w, tex_h))


if __name__ == "__main__":
    build()
