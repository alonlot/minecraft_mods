"""Wizard and witch hats for the Pensieve crowd: item textures and block-element item models.

Writes, under src/main/resources/assets/hogwartsmagic/:
  textures/item/{wizard,witch}_hat_<colour>.png   32x32, regions listed in REGIONS (16-unit uv space, 2px per unit)
  models/item/{wizard,witch}_hat_base.json        the shape, with the head display transform
  models/item/{wizard,witch}_hat_<colour>.json    one child per colour

Worn through the Pensieve actor head slot: vanilla's CustomHeadLayer draws a non-armour item with its HEAD display
transform, scaled 0.625, with the model centre on the middle of the head. The models are drawn with the head top at
y=1.44 and lifted onto the head by the display translation (+13).

Run with plain `python`.
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic")
TEX_DIR = os.path.join(ASSETS, "textures", "item")
MODEL_DIR = os.path.join(ASSETS, "models", "item")

COLOURS = {
    "purple": (92, 50, 138),
    "green": (34, 96, 58),
    "navy": (28, 42, 100),
    "black": (34, 32, 40),
    "brown": (104, 70, 40),
    "burgundy": (112, 30, 46),
}

# uv regions in 16-unit space: (u0, v0, u1, v1)
REGIONS = {
    "fabric": (0, 0, 8, 8),
    "band": (8, 0, 16, 4),
    "buckle": (8, 4, 12, 8),
    "brim_top": (0, 8, 8, 16),
    "brim_under": (8, 8, 16, 16),
}
PX = 2  # texture pixels per uv unit


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def region_px(name):
    u0, v0, u1, v1 = REGIONS[name]
    return u0 * PX, v0 * PX, u1 * PX, v1 * PX


def make_texture(shape, colour_name, base):
    rng = random.Random("hat:%s:%s" % (shape, colour_name))
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    band_col = shade(base, 0.55) if shape == "wizard" else (shade(base, 0.45) if sum(base) > 150 else (70, 62, 56))
    gold = (206, 164, 56)

    def fill(name, colour, twill, wear, grain=0.05):
        x0, y0, x1, y1 = region_px(name)
        for y in range(y0, y1):
            for x in range(x0, x1):
                k = 1.0
                k += 0.07 if (x + y) % 4 < 2 else -0.05 if twill else 0  # a faint diagonal weave
                k += rng.uniform(-grain, grain)
                c = shade(colour, k)
                if wear and rng.random() < wear:  # worn, sun-faded flecks
                    c = tuple(int(v + (200 - v) * 0.22) for v in c)
                img.putpixel((x, y), c + (255,))

    fill("fabric", base, True, 0.05)
    fill("brim_top", shade(base, 1.12), True, 0.09)
    fill("brim_under", shade(base, 0.62), False, 0.02)
    fill("band", band_col, False, 0.03)
    # a little wear: a darker crease and one mended patch on the crown
    for _ in range(3):
        cx, cy = rng.randrange(1, 15), rng.randrange(1, 15)
        for dx in range(rng.randrange(2, 5)):
            if 0 <= cx + dx < 16 and 0 <= cy < 16:
                r, g, b, a = img.getpixel((cx + dx, cy))
                img.putpixel((cx + dx, cy), shade((r, g, b), 0.82) + (255,))
    px, py = rng.randrange(3, 12), rng.randrange(3, 12)
    patch = shade(base, 1.35 if sum(base) < 260 else 0.8)
    for dy in range(2):
        for dx in range(2):
            img.putpixel((px + dx, py + dy), patch + (255,))
    # buckle: gold with a dark centre
    x0, y0, x1, y1 = region_px("buckle")
    for y in range(y0, y1):
        for x in range(x0, x1):
            edge = x in (x0, x1 - 1) or y in (y0, y1 - 1)
            img.putpixel((x, y), (gold if edge else shade(gold, 0.55)) + (255,))
    return img


# ---------------------------------------------------------------------------------------------------------------
# Models
# ---------------------------------------------------------------------------------------------------------------

def cube(frm, to, side="fabric", top=None, bottom=None, rot=None):
    side_uv = list(REGIONS[side])
    faces = {}
    for f in ("north", "south", "east", "west"):
        faces[f] = {"uv": side_uv, "texture": "#texture"}
    faces["up"] = {"uv": list(REGIONS[top or side]), "texture": "#texture"}
    faces["down"] = {"uv": list(REGIONS[bottom or side]), "texture": "#texture"}
    el = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces}
    if rot:
        el["rotation"] = rot
    return el


def chain(start_y, start_z, segs, sign):
    """Stacked, shrinking segments whose cumulative bend grows; each is a cuboid rotated about its own base centre
    (the model format rotates an element about a single origin), positioned where the previous one ended."""
    out = []
    y, z = start_y, start_z
    for width, height, angle in segs:
        h = width / 2
        if angle == 0:
            out.append(cube((8 - h, y, z - h), (8 + h, y + height, z + h)))
        else:
            out.append(cube((8 - h, y, z - h), (8 + h, y + height, z + h),
                            rot={"origin": [8, y, z], "axis": "x", "angle": angle * sign}))
        a = math.radians(angle * sign)
        # +x rotation sends +y towards +z: the segment's top centre moves by (0, h cos, h sin)
        y += height * math.cos(a)
        z += height * math.sin(a)
    return out


def wizard_elements():
    els = [
        cube((-0.5, -1.2, -0.5), (16.5, -0.2, 16.5), "brim_top", "brim_top", "brim_under"),
        cube((1.0, -0.2, 1.0), (15.0, 3.2, 15.0)),
        cube((0.8, 0.0, 0.8), (15.2, 1.8, 15.2), "band"),
    ]
    # straight lower cone, then a tip that droops forward (north, so a negative x angle)
    els += chain(3.2, 8.0, [(12.0, 2.6, 0), (10.6, 2.6, 0), (9.2, 2.6, 0), (7.8, 2.6, 0), (6.4, 2.6, 0)], -1)
    # the bend starts where the straight part ends
    y = 3.2 + 2.6 * 5
    els += chain(y, 8.0, [(5.0, 2.6, 22.5), (4.0, 2.6, 22.5), (3.0, 2.4, 45), (2.0, 2.2, 45)], -1)
    return els


def witch_elements():
    els = [
        cube((-4.0, -1.2, -4.0), (20.0, -0.4, 20.0), "brim_top", "brim_top", "brim_under"),
        cube((-1.5, -0.4, -1.5), (17.5, 0.5, 17.5), "brim_top", "brim_top", "brim_under"),
        cube((1.0, 0.5, 1.4), (15.0, 3.6, 15.4)),
        cube((0.8, 0.5, 1.2), (15.2, 2.3, 15.6), "band"),
        # buckle on the front of the band
        cube((6.0, 0.6, 0.9), (10.0, 2.2, 1.3), "buckle"),
    ]
    # the crown leans back (south, positive x angle) from the first layer up
    y, z = 3.6, 8.4
    els += chain(y, z, [(12.0, 3.0, 0)], 1)
    y += 3.0
    els += chain(y, z, [(10.2, 3.0, 22.5), (8.4, 3.0, 22.5), (6.8, 2.8, 22.5), (5.2, 2.8, 45), (3.8, 2.6, 45),
                        (2.4, 2.4, 45)], 1)
    return els


def display(kind):
    if kind == "wizard":
        gui = {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.5, 0.5, 0.5]}
    else:
        gui = {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.46, 0.46, 0.46]}
    return {
        "head": {"rotation": [0, 0, 0], "translation": [0, 13, 0], "scale": [1, 1, 1]},
        "gui": gui,
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, -4, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.4, 0.4, 0.4]},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.4, 0.4, 0.4]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    }


def write_json(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    os.makedirs(TEX_DIR, exist_ok=True)
    os.makedirs(MODEL_DIR, exist_ok=True)
    for kind, builder in (("wizard", wizard_elements), ("witch", witch_elements)):
        write_json(os.path.join(MODEL_DIR, kind + "_hat_base.json"), {
            "textures": {"particle": "#texture"},
            "elements": builder(),
            "display": display(kind),
        })
        for cname, base in COLOURS.items():
            make_texture(kind, cname, base).save(os.path.join(TEX_DIR, "%s_hat_%s.png" % (kind, cname)))
            write_json(os.path.join(MODEL_DIR, "%s_hat_%s.json" % (kind, cname)), {
                "parent": "hogwartsmagic:item/%s_hat_base" % kind,
                "textures": {"texture": "hogwartsmagic:item/%s_hat_%s" % (kind, cname)},
            })
    print("wrote 12 hat textures and models")


if __name__ == "__main__":
    main()
