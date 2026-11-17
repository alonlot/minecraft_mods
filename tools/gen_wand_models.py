#!/usr/bin/env python3
"""Generates the 3D held models of the wands.

Each wand is drawn in the hand as a real 3D wand (a tapered shaft, a handle, rings and a pommel built from
thin boxes), and keeps its flat sprite in the inventory, in item frames and as the GUI icon. The boxes lie
along the same 45-degree diagonal as the sprite (handle low left, tip high right, in the plane z = 8), so the
hand transforms and the tip point (WandCastAnimation.TIP, 0.40/0.40 in centred item space) still fit.

Writes, for every wand:
  models/item/<wand>.json         forge:separate_transforms: 3D base, sprite for gui and fixed
  models/item/<wand>_sprite.json  the old flat handheld sprite
  textures/item/<wand>_model.png  16x16 wood grain for the boxes

Run from the repository root: python3 tools/gen_wand_models.py
"""
import json
import os
import random

from PIL import Image

ASSETS = "src/main/resources/assets/hogwartsmagic"

# The tip sits this far along the diagonal from the model centre (8, 8, 8): 6.4 px on x and y.
TIP_S = 6.4 * 2 ** 0.5

# One display block for every wand: the one the Harry Potter wand was tuned with.
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -135, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, -135, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
}

# Texture columns: 0-3 handle, 4-7 shaft, 8-11 rings and caps, 12-15 tip.
HANDLE, SHAFT, RING, TIP = 0, 4, 8, 12


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, k):
    return tuple(round(a[i] + (b[i] - a[i]) * k) for i in range(3))


def scale(c, k):
    return tuple(max(0, min(255, round(v * k))) for v in c)


def texture(name, colors, seed):
    """A 16x16 grain texture: each 4-px column block is one part, grain running along v (the wand's length)."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for block, (dark, light) in ((HANDLE, colors["handle"]), (SHAFT, colors["shaft"]),
                                 (RING, colors["ring"]), (TIP, colors["tip"])):
        dark, light = hexrgb(dark), hexrgb(light)
        stripe = [0.9 + rnd.random() * 0.2 for _ in range(4)]
        for x in range(4):
            for v in range(16):
                k = v / 15.0 if block == SHAFT else 0.5  # the shaft lightens towards its tip (v = 0 end is the base)
                base = mix(dark, light, k)
                c = scale(base, stripe[x] * (0.94 + rnd.random() * 0.12))
                if block in (HANDLE, SHAFT) and rnd.random() < 0.08:
                    c = scale(c, 0.8)  # a knot or dark fleck in the grain
                img.putpixel((block + x, 15 - v), c + (255,))
    path = f"{ASSETS}/textures/item/{name}_model.png"
    img.save(path)


def box(s0, s1, width, block, v0=0.0, v1=16.0):
    """A box along the wand from s0 to s1 (px from the model centre), `width` px across, on the diagonal."""
    h = width / 2.0
    side_uv = [block, 16 - v1, block + 4, 16 - v0]
    cap_uv = [block, 0, block + min(4, max(1, round(width * 2))), min(4, max(1, round(width * 2)))]
    faces = {f: {"uv": side_uv, "texture": "#wand"} for f in ("north", "south", "east", "west")}
    faces["up"] = {"uv": cap_uv, "texture": "#wand"}
    faces["down"] = {"uv": cap_uv, "texture": "#wand"}
    return {
        "from": [round(8 - h, 4), round(8 + s0, 4), round(8 - h, 4)],
        "to": [round(8 + h, 4), round(8 + s1, 4), round(8 + h, 4)],
        "rotation": {"origin": [8, 8, 8], "axis": "z", "angle": -45, "rescale": False},
        "faces": faces,
    }


def shaft(parts, s0, s1, w0, w1, steps):
    """A tapering shaft from s0 (width w0) to s1 (width w1), in `steps` boxes, the grain gradient across all."""
    for i in range(steps):
        a = s0 + (s1 - s0) * i / steps
        b = s0 + (s1 - s0) * (i + 1) / steps
        w = w0 + (w1 - w0) * (i + 0.5) / steps
        parts.append(box(a, b, w, SHAFT, 16.0 * i / steps, 16.0 * (i + 1) / steps))


def build(style):
    """The boxes of one wand. All wands share the length; the style sets the handle and the rings."""
    p = []
    end = -10.0
    if style == "elder":
        # A knobbly shaft: a run of short segments with raised knots, and a carved bone handle.
        p.append(box(end, end + 0.9, 2.3, RING))
        p.append(box(end + 0.9, -4.4, 1.8, HANDLE))
        p.append(box(-6.9, -6.3, 2.1, RING))
        p.append(box(-4.4, -3.7, 2.2, RING))
        shaft(p, -3.7, TIP_S - 0.9, 1.45, 0.75, 6)
        for s in (-0.6, 2.6, 5.4):
            p.append(box(s, s + 0.55, 1.75 - 0.08 * s, RING))
        p.append(box(TIP_S - 0.9, TIP_S, 0.6, TIP))
    elif style == "plain":
        # One even taper from a slim handle (the core wands).
        p.append(box(end, end + 0.7, 1.9, RING))
        p.append(box(end + 0.7, -4.6, 1.6, HANDLE))
        p.append(box(-4.6, -4.0, 1.85, RING))
        shaft(p, -4.0, TIP_S - 0.8, 1.2, 0.6, 5)
        p.append(box(TIP_S - 0.8, TIP_S, 0.55, TIP))
    else:
        # "carved": a shaped handle with a collar and a ring, then a long fine taper (Harry's holly).
        p.append(box(end, end + 0.8, 2.1, RING))
        p.append(box(end + 0.8, -7.0, 1.75, HANDLE))
        p.append(box(-7.0, -6.4, 2.0, HANDLE))
        p.append(box(-6.4, -4.4, 1.6, HANDLE))
        p.append(box(-4.4, -3.8, 1.9, RING))
        shaft(p, -3.8, TIP_S - 0.8, 1.25, 0.55, 6)
        p.append(box(TIP_S - 0.8, TIP_S, 0.5, TIP))
    return p


WANDS = {
    "harry_potter_wand": ("carved", {"handle": ("#4a2c18", "#6b4226"), "shaft": ("#8a5f36", "#c69a63"),
                                     "ring": ("#3a2212", "#5a3a22"), "tip": ("#c9a06e", "#dcb684")}),
    "elder_wand": ("elder", {"handle": ("#b89a62", "#d9c08a"), "shaft": ("#4f3a28", "#7a604a"),
                             "ring": ("#9a7a4a", "#c8a66a"), "tip": ("#806650", "#9a8068")}),
    "dragon_core_wand": ("plain", {"handle": ("#141414", "#262626"), "shaft": ("#1e1e1e", "#3a3a3a"),
                                   "ring": ("#9a9a9a", "#d8d8d8"), "tip": ("#cfcfcf", "#eeeeee")}),
    "phoenix_core_wand": ("plain", {"handle": ("#2e0d08", "#4a160e"), "shaft": ("#5a140c", "#8e2a18"),
                                    "ring": ("#a83a28", "#c94536"), "tip": ("#ee3e00", "#ff7a2a")}),
    "unicorn_core_wand": ("plain", {"handle": ("#7a6a4c", "#a08a64"), "shaft": ("#9a9a98", "#d0d2d2"),
                                    "ring": ("#c8c0aa", "#e0d8c4"), "tip": ("#dcecec", "#f4ffff")}),
}


def main():
    for i, (name, (style, colors)) in enumerate(WANDS.items()):
        texture(name, colors, seed=1000 + i)
        sprite = {"parent": "minecraft:item/handheld", "textures": {"layer0": f"hogwartsmagic:item/{name}"},
                  "display": DISPLAY}
        # No parent: item/handheld leads to item/generated, which builds the model from layer0 and drops the boxes.
        base = {
            "ambientocclusion": False,
            "textures": {"wand": f"hogwartsmagic:item/{name}_model", "particle": f"hogwartsmagic:item/{name}"},
            "elements": build(style),
            "display": DISPLAY,
        }
        model = {
            "loader": "forge:separate_transforms",
            "base": base,
            "perspectives": {
                "gui": {"parent": f"hogwartsmagic:item/{name}_sprite"},
                "fixed": {"parent": f"hogwartsmagic:item/{name}_sprite"},
            },
        }
        with open(f"{ASSETS}/models/item/{name}_sprite.json", "w") as f:
            json.dump(sprite, f, indent=2)
            f.write("\n")
        with open(f"{ASSETS}/models/item/{name}.json", "w") as f:
            json.dump(model, f, indent=2)
            f.write("\n")
        print(name, len(base["elements"]), "boxes")


if __name__ == "__main__":
    os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    main()
