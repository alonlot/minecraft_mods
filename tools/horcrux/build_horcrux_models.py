"""Writes the Horcrux reliquary block models from one geometry list.

Run:  python tools/horcrux/build_horcrux_models.py

horcrux.json      dormant vessel: the black stone heart rests in the silver claws.
horcrux_lit.json  a soul is (being) bound: the cradle is empty because HorcruxHeartRenderer draws the
                  heart floating and beating above it, and the serpent and rune ring burn emissive.

Faces carry no explicit uv: vanilla derives it from the element's position, which is what the textures
in gen_horcrux_textures.py are laid out for (the column reads u 5..11, v 6..12, and so on).
HorcruxBlock.SHAPE follows the solid parts of this geometry.
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "models", "block")

SIDES = ("north", "south", "east", "west")
GLOW = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}


def box(frm, to, faces, rotation=None, shade=True):
    el = {"from": list(frm), "to": list(to), "faces": faces}
    if rotation:
        el["rotation"] = rotation
    if not shade:
        el["shade"] = False
    return el


def faces(tex, which=("north", "south", "east", "west", "up", "down"), glow=False, cull=None):
    out = {}
    for f in which:
        face = {"texture": tex}
        if glow:
            face["forge_data"] = dict(GLOW)
        if cull and f in cull:
            face["cullface"] = f
        out[f] = face
    return out


def merge(*dicts):
    out = {}
    for d in dicts:
        out.update(d)
    return out


def pedestal():
    els = [
        # Foot and step: carved slate, the rune channel cut into the step top.
        box((2, 0, 2), (14, 2, 14), merge(faces("#stone", SIDES), faces("#top", ("up",)), faces("#stone", ("down",), cull=("down",)))),
        box((3, 2, 3), (13, 4, 13), merge(faces("#stone", SIDES), faces("#top", ("up",)))),
        # The column with the serpent groove.
        box((5, 4, 5), (11, 10, 11), faces("#carved", SIDES)),
        # Silver capital and the shallow cradle on it.
        box((4, 10, 4), (12, 11, 12), faces("#metal")),
        box((5, 11, 5), (11, 12, 11), merge(faces("#metal", SIDES), faces("#stone", ("up",)))),
    ]
    # Four silver talons rising from the cradle's edges, leaning in to hold the heart.
    talon = faces("#metal")
    els += [
        box((7.5, 11, 4.5), (8.5, 14, 5.5), talon, {"angle": 22.5, "axis": "x", "origin": [8, 11, 5]}),
        box((7.5, 11, 10.5), (8.5, 14, 11.5), talon, {"angle": -22.5, "axis": "x", "origin": [8, 11, 11]}),
        box((4.5, 11, 7.5), (5.5, 14, 8.5), talon, {"angle": -22.5, "axis": "z", "origin": [5, 11, 8]}),
        box((10.5, 11, 7.5), (11.5, 14, 8.5), talon, {"angle": 22.5, "axis": "z", "origin": [11, 11, 8]}),
    ]
    return els


def resting_heart():
    h = faces("#heart")
    return [
        box((6.25, 12.5, 6.75), (9.75, 15, 9.25), h),    # body
        box((5.75, 14.5, 7), (7.9, 16, 9), h),           # left lobe
        box((8.1, 14.5, 7), (10.25, 16, 9), h),          # right lobe
        box((7, 11.75, 7.25), (9, 12.5, 8.75), h),       # point
        box((7.6, 15.5, 7.6), (8.4, 16.5, 8.4), h),      # a severed vessel at the top
    ]


def glow_overlays():
    eps = 0.01
    return [
        # The serpent groove burning through the column (cutout overlay, emissive).
        box((5 - eps, 4, 5 - eps), (11 + eps, 10, 11 + eps), faces("#serpent", SIDES, glow=True), shade=False),
        # The rune ring on the step.
        box((3, 4 + eps, 3), (13, 4 + eps, 13), faces("#runes", ("up",), glow=True), shade=False),
    ]


TEXTURES = {
    "particle": "hogwartsmagic:block/horcrux_stone",
    "stone": "hogwartsmagic:block/horcrux_stone",
    "top": "hogwartsmagic:block/horcrux_stone_top",
    "carved": "hogwartsmagic:block/horcrux_carved",
    "metal": "hogwartsmagic:block/horcrux_metal",
    "heart": "hogwartsmagic:block/horcrux_heart",
    "serpent": "hogwartsmagic:block/horcrux_serpent_glow",
    "runes": "hogwartsmagic:block/horcrux_rune_glow",
}


def model(elements, textures):
    return {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": textures,
        "elements": elements,
    }


def write(name, data):
    path = os.path.join(OUT, name + ".json")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print("wrote", path)


def main():
    dormant_tex = {k: v for k, v in TEXTURES.items() if k not in ("serpent", "runes")}
    lit_tex = {k: v for k, v in TEXTURES.items() if k != "heart"}
    write("horcrux", model(pedestal() + resting_heart(), dormant_tex))
    write("horcrux_lit", model(pedestal() + glow_overlays(), lit_tex))


if __name__ == "__main__":
    main()
