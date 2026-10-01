"""Procedural texture painter for the movie-accurate broom entity models (v2).

The Blockbench models use standard Minecraft box-UV, so every cube occupies a
net of size (2*(d+w), d+h) at its uv_offset. This script paints each face of
each net with the right material (mahogany / willow bristles / brushed gold /
weathered branch), oriented so the wood and straw grain runs along the broom.

UV space is 128x128; the PNG is written at SCALE x that for extra detail --
Minecraft normalises UVs, so a 4x sheet just looks sharper.

These write *_v2.png; the original broom textures are deliberately left alone.
"""

import json
import os
import random

from PIL import Image

SCALE = 4
BLEED = 2   # pixels of edge padding around each face rect
UV_W, UV_H = 128, 128
OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "hogwartsmagic", "textures", "entity",
)

# --- materials ---------------------------------------------------------------
# A material paints one texel given two random streams:
#   lrng -- constant along a whole grain line, so streaks stay continuous
#   prng -- per texel, for fine noise on top
# gu runs along the broom's long axis, gv across it (span_v texels wide).

MATS = {}


def mat(name):
    def deco(fn):
        MATS[name] = fn
        return fn
    return deco


def jitter(rng, base, amount):
    d = rng.randint(-amount, amount)
    return tuple(max(0, min(255, c + d)) for c in base)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def barrel(dark, light, base, gv, span_v, peak=0.34, falloff=1.35, mix=0.35):
    """Cylinder shading across the grain: bright band, darker toward the edges."""
    t = (gv + 0.5) / max(span_v, 1)
    shade = max(0.0, min(1.0, 1.0 - abs(t - peak) * falloff))
    return lerp(lerp(dark, light, shade), base, mix)


@mat("mahogany")
def _mahogany(lrng, prng, gu, gv, span_v):
    """Polished reddish mahogany: continuous grain streaks, glossy highlight."""
    col = barrel((92, 40, 20), (206, 116, 62), (152, 74, 40), gv, span_v)
    r = lrng.random()
    if r < 0.22:
        col = lerp(col, (78, 33, 16), lrng.uniform(0.25, 0.55))    # dark grain line
    elif r < 0.36:
        col = lerp(col, (222, 138, 84), lrng.uniform(0.2, 0.45))   # figured highlight
    if prng.random() < 0.10:
        col = lerp(col, (70, 30, 15), prng.uniform(0.1, 0.3))      # pore flecks
    return jitter(prng, col, 4)


@mat("mahogany_tip")
def _mahogany_tip(lrng, prng, gu, gv, span_v):
    return lerp(_mahogany(lrng, prng, gu, gv, span_v), (66, 28, 14), 0.28)


def _twigs(lrng, prng, gu, gv, span_v, dark, base, light, gap, peak=0.38):
    """Shared bundled-twig look: every grain line is one straw."""
    col = barrel(dark, light, base, gv, span_v, peak=peak, falloff=1.05, mix=0.3)
    r = lrng.random()
    if r < 0.30:
        col = lerp(col, light, lrng.uniform(0.3, 0.85))     # a lit straw
    elif r < 0.62:
        col = lerp(col, gap, lrng.uniform(0.35, 0.9))       # shadow between straws
    if prng.random() < 0.18:
        col = lerp(col, gap, prng.uniform(0.15, 0.45))      # breaks along the straw
    return jitter(prng, col, 5)


@mat("willow")
def _willow(lrng, prng, gu, gv, span_v):
    """Nimbus tail: neatly bundled willow, warm reddish brown."""
    return _twigs(lrng, prng, gu, gv, span_v,
                  (78, 42, 21), (146, 86, 47), (204, 142, 84), (58, 30, 15))


@mat("willow_dark")
def _willow_dark(lrng, prng, gu, gv, span_v):
    return lerp(_willow(lrng, prng, gu, gv, span_v), (44, 24, 12), 0.3)


@mat("birch_twig")
def _birch_twig(lrng, prng, gu, gv, span_v):
    """Firebolt tail: near-black birch, wispier and much darker than willow."""
    return _twigs(lrng, prng, gu, gv, span_v,
                  (30, 18, 13), (78, 44, 31), (148, 92, 60), (20, 12, 9))


@mat("birch_twig_dark")
def _birch_twig_dark(lrng, prng, gu, gv, span_v):
    return lerp(_birch_twig(lrng, prng, gu, gv, span_v), (16, 10, 8), 0.3)


@mat("scrub_twig")
def _scrub_twig(lrng, prng, gu, gv, span_v):
    """Shooting Star tail: scraggly pale twigs, dusty and uneven."""
    return _twigs(lrng, prng, gu, gv, span_v,
                  (96, 64, 34), (162, 118, 68), (214, 178, 122), (74, 48, 24))


@mat("scrub_twig_dark")
def _scrub_twig_dark(lrng, prng, gu, gv, span_v):
    return lerp(_scrub_twig(lrng, prng, gu, gv, span_v), (70, 46, 24), 0.28)


@mat("gold")
def _gold(lrng, prng, gu, gv, span_v):
    """Brushed brass with fine ribbing running along the broom axis."""
    col = barrel((96, 60, 14), (255, 218, 116), (198, 142, 40),
                 gv, span_v, peak=0.3, falloff=1.5, mix=0.28)
    if gv % 2 == 0:
        col = lerp(col, (92, 58, 14), 0.26)                 # ribs
    if lrng.random() < 0.12:
        col = lerp(col, (255, 240, 178), 0.35)              # specular catch
    return jitter(prng, col, 3)


@mat("gold_plain")
def _gold_plain(lrng, prng, gu, gv, span_v):
    col = barrel((100, 64, 16), (253, 214, 112), (200, 146, 46),
                 gv, span_v, peak=0.3, falloff=1.5, mix=0.28)
    return jitter(prng, col, 3)


@mat("branch")
def _branch(lrng, prng, gu, gv, span_v):
    """Shooting Star handle: raw knobbly hazel, bark on, sun-bleached."""
    col = barrel((58, 37, 20), (168, 124, 78), (112, 74, 42),
                 gv, span_v, peak=0.35, falloff=1.2, mix=0.4)
    r = lrng.random()
    if r < 0.20:
        col = lerp(col, (48, 30, 16), lrng.uniform(0.25, 0.6))     # bark grooves
    elif r < 0.30:
        col = lerp(col, (186, 146, 96), lrng.uniform(0.25, 0.5))   # rubbed smooth
    if prng.random() < 0.09:
        col = lerp(col, (40, 25, 13), prng.uniform(0.3, 0.7))      # knots and scars
    return jitter(prng, col, 6)


@mat("twine")
def _twine(lrng, prng, gu, gv, span_v):
    """Plain waxed twine binding -- no metal anywhere on a school broom."""
    base = (146, 118, 74)
    col = base if gu % 2 else lerp(base, (192, 166, 118), 0.55)
    if gv % 3 == 0:
        col = lerp(col, (88, 68, 42), 0.45)
    return jitter(prng, col, 6)


@mat("ash_wood")
def _ash_wood(lrng, prng, gu, gv, span_v):
    """Firebolt handle sides: dark scorched-walnut, weathered but warm.

    Deliberately kept off the blue/green axis -- every tone here has more red
    than blue -- so it reads as dark wood rather than cold grey.
    """
    col = barrel((34, 27, 22), (140, 120, 100), (84, 70, 57),
                 gv, span_v, peak=0.35, falloff=1.25, mix=0.35)
    if lrng.random() < 0.24:
        col = lerp(col, (30, 24, 19), lrng.uniform(0.25, 0.55))   # deep grain
    elif lrng.random() < 0.3:
        col = lerp(col, (152, 128, 102), lrng.uniform(0.2, 0.4))  # worn sheen
    if prng.random() < 0.08:
        col = lerp(col, (26, 20, 16), prng.uniform(0.3, 0.6))
    return jitter(prng, col, 5)


@mat("ash_wood_top")
def _ash_wood_top(lrng, prng, gu, gv, span_v):
    """The sanded, varnished upper face of the Firebolt handle."""
    col = barrel((104, 50, 20), (236, 152, 78), (176, 96, 42),
                 gv, span_v, peak=0.35, falloff=1.2, mix=0.35)
    if lrng.random() < 0.20:
        col = lerp(col, (96, 46, 18), lrng.uniform(0.2, 0.45))
    return jitter(prng, col, 5)


@mat("rune_top")
def _rune_top(lrng, prng, gu, gv, span_v):
    """Firebolt handle top: varnished wood with the gold registration runes."""
    col = _ash_wood_top(lrng, prng, gu, gv, span_v)
    centre = abs((gv + 0.5) / max(span_v, 1) - 0.45) < 0.3
    if centre and gu % 5 == 2:
        col = lerp(col, (244, 206, 118), 0.85)
    return col


@mat("feather")
def _feather(lrng, prng, gu, gv, span_v):
    """The little grey feather lashed to the Firebolt's handle."""
    t = (gv + 0.5) / max(span_v, 1)
    col = lerp((116, 106, 96), (214, 204, 188), max(0.0, min(1.0, 1.0 - abs(t - 0.4) * 1.4)))
    if abs(t - 0.5) < 0.14:
        col = lerp(col, (68, 60, 52), 0.7)             # central quill
    elif lrng.random() < 0.4:
        col = lerp(col, (92, 82, 72), lrng.uniform(0.15, 0.4))    # barb splits
    return jitter(prng, col, 5)


@mat("cord")
def _cord(lrng, prng, gu, gv, span_v):
    """Waxed black cord."""
    return jitter(prng, (38, 34, 32), 6)


# --- face layout -------------------------------------------------------------
# Box UV net for a cube of size (w, h, d) at offset (u, v). For each face we
# record its rect plus which model axis the U and V texture axes follow, so the
# grain can be oriented consistently.  axis codes: 0 = x, 1 = y, 2 = z

def net_faces(u, v, w, h, d):
    return [
        ("up",    u + d,          v,     w, d, 0, 2),
        ("down",  u + d + w,      v,     w, d, 0, 2),
        ("east",  u,              v + d, d, h, 2, 1),
        ("north", u + d,          v + d, w, h, 0, 1),
        ("west",  u + d + w,      v + d, d, h, 2, 1),
        ("south", u + 2 * d + w,  v + d, w, h, 0, 1),
    ]


def paint(img, cubes, materials, long_axis, seed=1234, face_overrides=None):
    """cubes: [name, u, v, w, h, d]. materials: name -> material key.
    long_axis: name -> model axis (0/1/2) the grain runs along.
    face_overrides: (name, face) -> material key, for one-off faces."""
    face_overrides = face_overrides or {}
    px = img.load()
    for name, u, v, w, h, d in cubes:
        key = materials.get(name)
        if key is None:
            raise KeyError("no material assigned for cube %r" % name)
        axis = long_axis.get(name, 2)
        for face, fu, fv, fw, fh, ua, va in net_faces(u, v, w, h, d):
            fn = MATS[face_overrides.get((name, face), key)]
            for j in range(fh):
                for i in range(fw):
                    if ua == axis:
                        gu, gv, span = i, j, fh
                    elif va == axis:
                        gu, gv, span = j, i, fw
                    else:
                        # end-grain cap: no length direction, fake concentric rings
                        gu, gv, span = i, (i + j) % max(fh, 1), max(fh, 1)
                    lrng = random.Random(hash((seed, name, face, "line", gv)) & 0xFFFFFFFF)
                    prng = random.Random(hash((seed, name, face, i, j)) & 0xFFFFFFFF)
                    col = fn(lrng, prng, gu, gv, span)
                    # a touch of baked ambient so the form reads in flat light
                    if face == "up":
                        col = lerp(col, (255, 255, 255), 0.09)
                    elif face == "down":
                        col = lerp(col, (0, 0, 0), 0.15)
                    # bleed one texel outward on the rect edges so that any
                    # sub-texel UV rounding still samples painted pixels
                    bx0 = -BLEED if i == 0 else 0
                    bx1 = SCALE + BLEED if i == fw - 1 else SCALE
                    by0 = -BLEED if j == 0 else 0
                    by1 = SCALE + BLEED if j == fh - 1 else SCALE
                    x0 = (fu + i) * SCALE
                    y0 = (fv + j) * SCALE
                    for yy in range(by0, by1):
                        for xx in range(bx0, bx1):
                            tx, ty = x0 + xx, y0 + yy
                            if not (0 <= tx < img.width and 0 <= ty < img.height):
                                continue
                            srng = random.Random(
                                hash((seed, name, face, i, j, xx, yy)) & 0xFFFFFFFF)
                            px[tx, ty] = jitter(srng, col, 4) + (255,)


LAYOUTS = json.load(open(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "broom_layouts.json"),
    encoding="utf-8"))


def blank():
    return Image.new("RGBA", (UV_W * SCALE, UV_H * SCALE), (0, 0, 0, 0))


def save(img, filename):
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, filename)
    img.save(path)
    print("wrote", path, img.size)
    return path


# --- Nimbus 2000 -------------------------------------------------------------

NIMBUS_CUBES = LAYOUTS["nimbus"]


def nimbus():
    mats, axes = {}, {}
    for name, u, v, w, h, d in NIMBUS_CUBES:
        if name == "handle_nose":
            mats[name] = "mahogany_tip"
        elif name.startswith("handle"):
            mats[name] = "mahogany"
        elif name.startswith("bristle"):
            mats[name] = "willow"
        elif name.startswith("wisp"):
            mats[name] = "willow_dark"
        elif name.startswith("ring"):
            mats[name] = "gold"
        else:
            mats[name] = "gold_plain"
        axes[name] = 0 if name == "foot_bar" else 2
    img = blank()
    # the gold Nimbus 2000 script sits on the top face of the tip
    paint(img, NIMBUS_CUBES, mats, axes, seed=20001,
          face_overrides={("handle_knob", "up"): "gold_plain"})
    return save(img, "broom_nimbus_2000_v2.png")




# --- Firebolt ----------------------------------------------------------------

FIREBOLT_CUBES = LAYOUTS["firebolt"]


def firebolt():
    mats, axes, overrides = {}, {}, {}
    for name, u, v, w, h, d in FIREBOLT_CUBES:
        if name == "feather_vane":
            mats[name] = "feather"
            axes[name] = 1
            continue
        if name == "feather_cord":
            mats[name] = "cord"
            axes[name] = 1
            continue
        axes[name] = 0 if name == "foot_bar" else 2
        if name.startswith("handle"):
            mats[name] = "ash_wood"
            # the sanded, varnished upper face with its row of gold runes
            overrides[(name, "up")] = "rune_top"
        elif name.startswith("bristle"):
            mats[name] = "birch_twig"
        elif name.startswith("wisp"):
            mats[name] = "birch_twig_dark"
        elif name in ("collar", "band"):
            mats[name] = "gold"
        else:
            mats[name] = "gold_plain"
    img = blank()
    paint(img, FIREBOLT_CUBES, mats, axes, seed=30007, face_overrides=overrides)
    return save(img, "broom_firebolt_v2.png")



# --- Shooting Star (Hogwarts school broom) -----------------------------------

STAR_CUBES = LAYOUTS["shooting_star"]


def shooting_star():
    mats, axes = {}, {}
    for name, u, v, w, h, d in STAR_CUBES:
        if name.startswith("twine"):
            mats[name] = "twine"
        elif name.startswith("shaft") or name.startswith("knot"):
            mats[name] = "branch"
        elif name.startswith("core"):
            mats[name] = "scrub_twig_dark"
        else:
            mats[name] = "scrub_twig"
        axes[name] = 2
    img = blank()
    paint(img, STAR_CUBES, mats, axes, seed=40013)
    return save(img, "broom_shooting_star_v2.png")


if __name__ == "__main__":
    nimbus()
    firebolt()
    shooting_star()
