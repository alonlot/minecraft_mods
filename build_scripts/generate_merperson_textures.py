"""Generate the merperson entity textures.

Unlike the basilisk generator, this one does not carry a hand-copied UV table.
It reads ``blockbench_models/merperson.bbmodel`` and pulls each cube's baked
per-face rectangle out of ``element["faces"][side]["uv"]``, which is already in
texture pixels. That keeps the texture honest after a re-pack: change a cube
size in Blockbench, re-pack the UVs, save, re-run this, done.

Four files come out:

    merperson.png            the common selkie: grey-green hide, bottle-green
                             hair, a dull pewter tail, broken yellow teeth.
    merperson_eyes.png       emissive overlay holding only the living eyes.
    merchieftainess.png      the pod leader: paler, greyer, white-green hair,
                             and the shell crown actually painted.
    merchieftainess_eyes.png the leader's eyes, colder and paler.

These are the Black Lake merpeople, so they are the *selkie* variety - the
rough, grey, fierce-looking kind, not the Greek siren. Nothing here is allowed
to be pretty.
"""

import json
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL = os.path.join(ROOT, "blockbench_models", "merperson.bbmodel")
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                       "textures", "entity")

SIDES = ("north", "south", "east", "west", "up", "down")


# --------------------------------------------------------------------------- #
# palettes
# --------------------------------------------------------------------------- #

class Palette:
    """One merperson colour scheme."""

    def __init__(self, **kw):
        self.__dict__.update(kw)


SELKIE = Palette(
    skin=(94, 107, 90), skin_dark=(58, 68, 56), skin_light=(118, 130, 110),
    belly=(132, 136, 118), belly_dark=(101, 106, 92),
    hair=(34, 48, 30), hair_dark=(20, 30, 18), hair_light=(58, 78, 50),
    scale=(74, 90, 85), scale_edge=(48, 60, 57), scale_light=(104, 122, 114),
    scute=(146, 152, 136), scute_dark=(110, 116, 102),
    fin=(96, 112, 108), fin_ray=(56, 68, 66), fin_edge=(132, 146, 140),
    tooth=(198, 188, 130), tooth_dark=(120, 112, 74),
    gum=(72, 34, 34), gum_dark=(44, 20, 22),
    claw=(176, 168, 142),
    pebble=(96, 96, 88), pebble_dark=(54, 54, 50), cord=(42, 36, 30),
    crown=(214, 202, 172), crown_dark=(150, 138, 112),
    socket=(48, 52, 42), socket_dark=(24, 26, 22),
    eye=(226, 202, 74), eye_hot=(248, 236, 150), pupil=(14, 14, 10),
)

CHIEFTAINESS = Palette(
    skin=(122, 130, 122), skin_dark=(82, 90, 86), skin_light=(150, 156, 146),
    belly=(176, 178, 166), belly_dark=(138, 142, 132),
    hair=(190, 196, 180), hair_dark=(138, 146, 132), hair_light=(224, 228, 214),
    scale=(112, 124, 124), scale_edge=(76, 88, 88), scale_light=(148, 162, 158),
    scute=(186, 190, 176), scute_dark=(146, 150, 138),
    fin=(140, 152, 150), fin_ray=(88, 100, 100), fin_edge=(184, 194, 188),
    tooth=(214, 206, 158), tooth_dark=(136, 130, 92),
    gum=(84, 42, 42), gum_dark=(52, 24, 26),
    claw=(206, 200, 178),
    pebble=(196, 178, 140), pebble_dark=(132, 118, 90), cord=(60, 52, 42),
    crown=(240, 228, 202), crown_dark=(190, 172, 142),
    socket=(78, 82, 74), socket_dark=(40, 44, 38),
    eye=(206, 224, 214), eye_hot=(242, 250, 246), pupil=(12, 16, 16),
)


# --------------------------------------------------------------------------- #
# cube -> which paint routine
# --------------------------------------------------------------------------- #

HAIR_CUBES = {"hair_cap", "strand_back_left", "strand_back_right",
              "strand_left", "strand_right"}
FIN_CUBES = {"finear_left", "finear_right", "finarm_left", "finarm_right",
             "dorsal", "fin_t2", "fin_t3", "fluke_front", "fluke_rear"}
TAIL_CUBES = {"seg1", "seg2", "seg3", "seg4", "seg5"}
TOOTH_CUBES = {"teeth_top", "teeth_bottom"}
CROWN_CUBES = {"crown_band", "crown_spike_mid", "crown_spike_left",
               "crown_spike_right"}
CLAW_CUBES = {"hand_left", "hand_right"}
EYE_CUBES = {"eye_left", "eye_right"}

# Faces that look at the sky get the dark dorsal tone; faces that look at the
# lakebed get the pale underside. Painting every cube uniformly is what makes a
# blocky model read as plastic.
TOP_FACES = ("up",)
BOTTOM_FACES = ("down",)


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


def mottle(px, r, base, dark, light, rng, blotch=0.16):
    """Skin: a noisy base with a few soft darker blotches over it."""
    x0, y0, x1, y1 = r
    w, h = x1 - x0, y1 - y0
    if w <= 0 or h <= 0:
        return
    flat(px, r, base, rng, 0.09)
    for _ in range(max(1, int(w * h * blotch / 6))):
        cx = rng.uniform(x0, x1)
        cy = rng.uniform(y0, y1)
        rad = rng.uniform(0.9, 2.4)
        tone = dark if rng.random() < 0.62 else light
        for y in range(y0, y1):
            for x in range(x0, x1):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= rad * rad:
                    px[x, y] = jitter(tone, rng, 0.10) + (255,)


def scales(px, r, base, edge, light, rng, size=3):
    """Offset rows of rounded scales - the tail, and the outer thighs of it."""
    x0, y0, x1, y1 = r
    flat(px, r, base, rng, 0.07)
    row = 0
    y = y0
    while y < y1:
        off = 0 if row % 2 == 0 else size // 2
        x = x0 - off
        while x < x1:
            for yy in range(y, min(y + size, y1)):
                for xx in range(max(x, x0), min(x + size, x1)):
                    on_edge = (xx == x) or (yy == y + size - 1)
                    if on_edge:
                        px[xx, yy] = jitter(edge, rng, 0.12) + (255,)
                    elif yy == y and rng.random() < 0.55:
                        px[xx, yy] = jitter(light, rng, 0.10) + (255,)
            x += size
        y += size
        row += 1


def rays(px, r, base, ray, edge, rng):
    """Webbed fin: parallel rays running along the fin's long axis."""
    x0, y0, x1, y1 = r
    w, h = x1 - x0, y1 - y0
    if w <= 0 or h <= 0:
        return
    flat(px, r, base, rng, 0.08)
    # Stripes run along the longer dimension, so they read as rays leaving the
    # body rather than as a grid.
    if w >= h:
        for y in range(y0, y1):
            if (y - y0) % 2 == 0:
                for x in range(x0, x1):
                    px[x, y] = jitter(ray, rng, 0.14) + (255,)
        for x in range(x0, x1):
            px[x, y1 - 1] = jitter(edge, rng, 0.10) + (255,)
    else:
        for x in range(x0, x1):
            if (x - x0) % 2 == 0:
                for y in range(y0, y1):
                    px[x, y] = jitter(ray, rng, 0.14) + (255,)
        for y in range(y0, y1):
            px[x1 - 1, y] = jitter(edge, rng, 0.10) + (255,)


def strands(px, r, dark, mid, light, rng):
    """Hair: hard vertical streaks, uneven, mostly dark."""
    x0, y0, x1, y1 = r
    w, h = x1 - x0, y1 - y0
    if w <= 0 or h <= 0:
        return
    # Base on the mid tone, not the darkest one: a flat near-black slab reads as
    # a hood rather than as hair.
    flat(px, r, mid, rng, 0.12)
    for x in range(x0, x1):
        tone = dark if rng.random() < 0.5 else (light if rng.random() < 0.3 else mid)
        run = rng.randint(2, max(2, h))
        start = y0 + rng.randint(0, max(0, h - 1))
        for y in range(start, min(start + run, y1)):
            px[x, y] = jitter(tone, rng, 0.18) + (255,)


def fangs(px, r, tooth, dark, rng):
    """Broken yellow teeth: alternating spikes with gaps where some are gone."""
    x0, y0, x1, y1 = r
    w, h = x1 - x0, y1 - y0
    if w <= 0 or h <= 0:
        return
    flat(px, r, dark, rng, 0.12)
    for x in range(x0, x1):
        if (x - x0) % 2 == 1:
            continue
        if rng.random() < 0.22:      # a gap: this one got knocked out
            continue
        length = max(1, int(h * rng.uniform(0.55, 1.0)))
        for y in range(y0, min(y0 + length, y1)):
            px[x, y] = jitter(tooth, rng, 0.10) + (255,)


def pebbles(px, r, pebble, dark, cord, rng):
    """The rope of lake pebbles every merperson wears."""
    x0, y0, x1, y1 = r
    w, h = x1 - x0, y1 - y0
    if w <= 0 or h <= 0:
        return
    flat(px, r, cord, rng, 0.12)
    cy = y0 + h / 2.0
    x = x0
    while x < x1:
        rad = rng.uniform(0.6, 1.0)
        for yy in range(y0, y1):
            for xx in range(x, min(x + 2, x1)):
                d = (xx + 0.5 - (x + 0.8)) ** 2 + (yy + 0.5 - cy) ** 2
                if d <= rad * rad:
                    tone = dark if d > (rad - 0.5) ** 2 else pebble
                    px[xx, yy] = jitter(tone, rng, 0.14) + (255,)
        x += 2


def shell(px, r, base, dark, rng):
    """Crown: nacre with fine radial banding."""
    x0, y0, x1, y1 = r
    w, h = x1 - x0, y1 - y0
    if w <= 0 or h <= 0:
        return
    flat(px, r, base, rng, 0.05)
    # Fine radial banding, then a darker rim so the crown has an edge instead of
    # dissolving into gravel.
    for i, y in enumerate(range(y0, y1)):
        for x in range(x0, x1):
            if (x - x0 + i) % 4 == 0:
                px[x, y] = jitter(dark, rng, 0.08) + (255,)
    for x in range(x0, x1):
        px[x, y1 - 1] = jitter(dark, rng, 0.06) + (255,)
    for y in range(y0, y1):
        px[x0, y] = jitter(dark, rng, 0.06) + (255,)
        px[x1 - 1, y] = jitter(dark, rng, 0.06) + (255,)


def socket(px, r, base, dark, rng):
    """The base skin under an eye: a dark hollow, so a blind merperson reads."""
    x0, y0, x1, y1 = r
    flat(px, r, dark, rng, 0.14)
    if x1 - x0 >= 2 and y1 - y0 >= 2:
        for y in range(y0 + 1, y1 - 1):
            for x in range(x0 + 1, x1 - 1):
                px[x, y] = jitter(base, rng, 0.18) + (255,)


def living_eye(px, r, eye, hot, pupil, rng):
    """Emissive overlay: a yellow eye with a hard vertical slit."""
    x0, y0, x1, y1 = r
    w, h = x1 - x0, y1 - y0
    if w <= 0 or h <= 0:
        return
    for y in range(y0, y1):
        for x in range(x0, x1):
            near_top = (y - y0) < max(1, h // 3)
            px[x, y] = jitter(hot if near_top else eye, rng, 0.08) + (255,)
    # A single-pixel slit. At entity resolution an eye is only two or three
    # pixels across, so a two-pixel pupil swallows the whole eye.
    if w >= 3:
        slit = x0 + w // 2
        for y in range(y0, y1):
            px[slit, y] = pupil + (255,)


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
        cubes[el["name"]] = {
            "faces": {s: rect_of(el["faces"][s]["uv"]) for s in SIDES},
            "from": el["from"],
            "to": el["to"],
        }
    return res["width"], res["height"], cubes


def paint(pal, cubes, tex_w, tex_h, seed):
    rng = random.Random(seed)
    img = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
    eyes = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
    px, epx = img.load(), eyes.load()

    for name, cube in sorted(cubes.items()):
        f = cube["faces"]

        if name in HAIR_CUBES:
            for s in SIDES:
                strands(px, f[s], pal.hair_dark, pal.hair, pal.hair_light, rng)

        elif name in FIN_CUBES:
            for s in SIDES:
                rays(px, f[s], pal.fin, pal.fin_ray, pal.fin_edge, rng)

        elif name in TAIL_CUBES:
            for s in ("north", "south", "east", "west"):
                scales(px, f[s], pal.scale, pal.scale_edge, pal.scale_light, rng)
            scales(px, f["up"], pal.scale_edge, pal.scale_edge, pal.scale, rng)
            # Belly scutes: broad pale plates, the way a real fish underside runs.
            flat(px, f["down"], pal.scute, rng, 0.06)
            x0, y0, x1, y1 = f["down"]
            for y in range(y0, y1, 2):
                for x in range(x0, x1):
                    px[x, y] = jitter(pal.scute_dark, rng, 0.10) + (255,)

        elif name in TOOTH_CUBES:
            up = name == "teeth_bottom"
            for s in ("north", "south", "east", "west"):
                r = f[s]
                if up:
                    # Lower teeth point up, so the spikes start at the top of
                    # the strip and the gum is below them.
                    x0, y0, x1, y1 = r
                    flat(px, r, pal.gum, rng, 0.12)
                    fangs(px, (x0, y0, x1, y1), pal.tooth, pal.gum, rng)
                else:
                    fangs(px, r, pal.tooth, pal.gum_dark, rng)
            flat(px, f["up"], pal.gum, rng, 0.15)
            flat(px, f["down"], pal.gum_dark, rng, 0.15)

        elif name in CROWN_CUBES:
            for s in SIDES:
                shell(px, f[s], pal.crown, pal.crown_dark, rng)

        elif name in CLAW_CUBES:
            for s in ("north", "south", "east", "west", "up"):
                mottle(px, f[s], pal.skin_dark, pal.skin_dark, pal.skin, rng)
            flat(px, f["down"], pal.claw, rng, 0.16)

        elif name in EYE_CUBES:
            # The eye reads from the front AND from the side: a merperson seen in
            # profile should still have something looking back at you.
            outward = "east" if name == "eye_left" else "west"
            for s in SIDES:
                if s in ("north", outward):
                    socket(px, f[s], pal.socket, pal.socket_dark, rng)
                    living_eye(epx, f[s], pal.eye, pal.eye_hot, pal.pupil, rng)
                else:
                    flat(px, f[s], pal.socket_dark, rng, 0.15)

        elif name == "necklace":
            for s in SIDES:
                pebbles(px, f[s], pal.pebble, pal.pebble_dark, pal.cord, rng)

        elif name == "jaw_bone":
            for s in ("north", "south", "east", "west"):
                mottle(px, f[s], pal.skin, pal.skin_dark, pal.skin_light, rng)
            flat(px, f["up"], pal.gum, rng, 0.16)          # mouth floor
            flat(px, f["down"], pal.belly, rng, 0.10)      # pale throat
            # A dark seam where the jaw meets the skull, so the mouth line reads
            # even when it is shut.
            x0, y0, x1, y1 = f["north"]
            for x in range(x0, x1):
                px[x, y0] = jitter(pal.gum_dark, rng, 0.10) + (255,)

        elif name == "snout":
            for s in ("north", "south", "east", "west", "up"):
                mottle(px, f[s], pal.skin, pal.skin_dark, pal.skin_light, rng)
            flat(px, f["down"], pal.gum_dark, rng, 0.14)   # palate
            # Two nostril slits high on the muzzle.
            x0, y0, x1, y1 = f["north"]
            if x1 - x0 >= 4 and y1 - y0 >= 2:
                mid = (x0 + x1) // 2
                for dx in (-2, 1):
                    px[max(x0, min(x1 - 1, mid + dx)), y0] = pal.socket_dark + (255,)

        elif name == "skull":
            for s in ("north", "south", "east", "west"):
                mottle(px, f[s], pal.skin, pal.skin_dark, pal.skin_light, rng)
            mottle(px, f["up"], pal.skin_dark, pal.skin_dark, pal.skin, rng)
            mottle(px, f["down"], pal.belly, pal.belly_dark, pal.belly, rng, 0.10)
            # Shadow cast by the brow, and gaunt hollows under the cheekbones.
            x0, y0, x1, y1 = f["north"]
            for y in range(y0, min(y0 + 2, y1)):
                for x in range(x0, x1):
                    px[x, y] = jitter(pal.socket_dark, rng, 0.12) + (255,)
            for s in ("east", "west"):
                ex0, ey0, ex1, ey1 = f[s]
                for y in range(ey0, min(ey0 + 2, ey1)):
                    for x in range(ex0, ex1):
                        px[x, y] = jitter(pal.skin_dark, rng, 0.12) + (255,)

        elif name == "brow":
            # A heavy scowling ridge. It is skin, not a black bar - the shadow it
            # throws belongs on its underside and on the skull behind it.
            for s in ("north", "south", "east", "west", "up"):
                mottle(px, f[s], pal.skin_dark, pal.skin_dark, pal.skin, rng, 0.10)
            flat(px, f["down"], pal.socket_dark, rng, 0.12)

        elif name in ("gill_left", "gill_right"):
            outward = "east" if name == "gill_left" else "west"
            for s in SIDES:
                mottle(px, f[s], pal.skin, pal.skin_dark, pal.skin_light, rng)
            x0, y0, x1, y1 = f[outward]
            for i, y in enumerate(range(y0, y1)):
                if i % 2 == 0:
                    for x in range(x0, x1):
                        px[x, y] = jitter(pal.gum_dark, rng, 0.14) + (255,)

        elif name == "collar":
            for s in SIDES:
                mottle(px, f[s], pal.skin_dark, pal.skin_dark, pal.skin_light, rng, 0.14)

        elif name in ("chest", "ribs", "belly"):
            for s in ("north", "south", "east", "west"):
                mottle(px, f[s], pal.skin, pal.skin_dark, pal.skin_light, rng)
            mottle(px, f["up"], pal.skin_dark, pal.skin_dark, pal.skin, rng)
            mottle(px, f["down"], pal.belly, pal.belly_dark, pal.belly, rng, 0.10)
            # A sternum groove down the middle and a couple of rib creases: three
            # smooth boxes stacked up read as a mannequin without them.
            for s in ("north", "south"):
                x0, y0, x1, y1 = f[s]
                mid = (x0 + x1) // 2
                for y in range(y0, y1):
                    px[mid, y] = jitter(pal.skin_dark, rng, 0.10) + (255,)
                for y in range(y0 + 1, y1 - 1, 2):
                    for x in range(x0, x1):
                        if abs(x - mid) > 1 and rng.random() < 0.7:
                            px[x, y] = jitter(pal.skin_dark, rng, 0.12) + (255,)

        else:
            # Ordinary hide: dark on top, pale underneath.
            for s in ("north", "south", "east", "west"):
                mottle(px, f[s], pal.skin, pal.skin_dark, pal.skin_light, rng)
            for s in TOP_FACES:
                mottle(px, f[s], pal.skin_dark, pal.skin_dark, pal.skin, rng)
            for s in BOTTOM_FACES:
                mottle(px, f[s], pal.belly, pal.belly_dark, pal.belly, rng, 0.10)

    return img, eyes


def build_spear_item():
    """The 16x16 inventory icon for the dropped spear.

    Drawn on the usual bottom-left to top-right tool diagonal: a driftwood
    shaft, a sinew binding, and a knapped stone head with two barbs swept back
    so it reads as something you would not want pulled out again.
    """
    ITEM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                            "textures", "item")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    shaft = (86, 68, 48)
    shaft_hi = (118, 96, 68)
    shaft_lo = (58, 46, 34)
    cord = (176, 166, 138)
    stone = (128, 136, 132)
    stone_hi = (176, 184, 178)
    stone_lo = (78, 86, 84)

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = c + (255,)

    # Shaft: two pixels thick along the diagonal, lit on its upper edge.
    for i in range(2, 14):
        x, y = i, 15 - i
        put(x, y, shaft)
        put(x, y + 1, shaft_lo)
        put(x - 1, y, shaft_hi)

    # Sinew binding where the head is lashed on.
    for i in (9, 10):
        x, y = i, 15 - i
        put(x, y, cord)
        put(x - 1, y, cord)
        put(x, y + 1, cord)

    # Knapped head, and the two swept-back barbs.
    for x, y in ((12, 3), (13, 2), (14, 1), (13, 3), (12, 4)):
        put(x, y, stone)
    for x, y in ((14, 1), (13, 2)):
        put(x, y, stone_hi)
    for x, y in ((12, 5), (11, 4), (14, 3), (13, 4)):
        put(x, y, stone_lo)
    put(11, 3, stone)
    put(14, 2, stone)

    os.makedirs(ITEM_DIR, exist_ok=True)
    path = os.path.join(ITEM_DIR, "merperson_spear.png")
    img.save(path)
    print("wrote", path)


def build():
    tex_w, tex_h, cubes = load_layout()
    os.makedirs(OUT_DIR, exist_ok=True)

    for stem, pal, seed in (("merperson", SELKIE, 20260811),
                            ("merchieftainess", CHIEFTAINESS, 771)):
        img, eyes = paint(pal, cubes, tex_w, tex_h, seed)
        base_path = os.path.join(OUT_DIR, stem + ".png")
        eyes_path = os.path.join(OUT_DIR, stem + "_eyes.png")
        img.save(base_path)
        eyes.save(eyes_path)
        print("wrote", base_path)
        print("wrote", eyes_path)

    build_spear_item()
    print("layout: %d cubes on a %dx%d sheet" % (len(cubes), tex_w, tex_h))


if __name__ == "__main__":
    build()
