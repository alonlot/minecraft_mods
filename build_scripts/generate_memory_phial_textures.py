"""Draw the Pensieve phials: the empty corked glass phial and the Memory it becomes once a memory is drawn into it.

    python build_scripts/generate_memory_phial_textures.py

Writes textures/item/empty_memory_phial.png (16x16), textures/item/memory.png (16x128, 8 frames) with
memory.png.mcmeta, and the two item models. Both sprites follow the texturing-minecraft-items contract: 16x16, binary
alpha, light-blue glass outline with a transparent interior (the vanilla glass bottle recipe), a cork, and for the
Memory the silver ramp of Memory of the Cave with wisps drifting up the neck. Fully deterministic.
"""

import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", ".claude", "skills", "texturing-minecraft-items", "scripts"))

from pixkit import hexc, save, sprite  # noqa: E402

ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "hogwartsmagic")
TEX = os.path.join(ASSETS, "textures", "item")
MODELS = os.path.join(ASSETS, "models", "item")
FRAMES = 8

# k/m/l cork dark..light; g/b/d/D glass light..darkest (vanilla glass bottle ramp, plus one darker step so the
# bottom-right edge reads on the grey slot); q a pale glint inside the glass.
PALETTE = {
    ".": None,
    "k": "#4a2c14", "m": "#8f5a2e", "l": "#c08a4f",
    "g": "#d4e5f7", "b": "#b3cfec", "d": "#6d97c8", "D": "#3f6aa0", "q": "#eef6fd",
}

EMPTY = """
................
......kkkk......
......lmmk......
......lmmk......
.....gbbbbd.....
......g..d......
......g..d......
.....g....d.....
....g......d....
...q........d...
...q........d...
...b........D...
...b........D...
....b......D....
.....DDDDDD.....
................
"""

# Silver of the Memory of the Cave, darkest first.
S = [hexc(c) for c in ("#4b5a86", "#7d92bf", "#b3c8e6", "#e0eefa", "#ffffff")]

# Interior columns of each row, where the glass leaves room (first, last).
INSIDE = {5: (7, 8), 6: (7, 8), 7: (6, 9), 8: (5, 10), 9: (4, 11), 10: (4, 11), 11: (4, 11), 12: (4, 11), 13: (5, 10)}


def surface_up(x, f):
    """Whether the liquid rises to row 9 at column x on frame f: a slow wave that loops over the 8 frames."""
    return math.sin(2 * math.pi * (f / FRAMES) + 0.9 * x) > 0.35


def liquid_pixels(f):
    out = {}
    for y in range(9, 14):
        lo, hi = INSIDE[y]
        for x in range(lo, hi + 1):
            if y == 9 and not surface_up(x, f):
                continue
            top = y == 9 or (y == 10 and not surface_up(x, f))
            if top:
                c = S[3]
            elif x >= 11 and y >= 11 or y >= 13 and x >= 9:
                c = S[0]
            elif x >= 9 or y >= 12:
                c = S[1]
            elif x <= 5:
                c = S[3]
            else:
                c = S[2]
            out[(x, y)] = c
    return out


def wisp_pixels(f):
    """Two curls of memory climbing the neck, half a cycle apart, and a sparkle on the surface."""
    out = {}
    for phase, x0 in ((0, 7), (4, 8)):
        t = (f + phase) % FRAMES
        if t > 4:
            continue
        y = 9 - t
        x = x0 if t % 2 == 0 else (x0 + 1 if x0 == 7 else x0 - 1)
        lo, hi = INSIDE.get(y, (7, 8))
        x = min(max(x, lo), hi)
        out[(x, y)] = S[4] if t < 4 else S[3]
        if t > 0:
            out[(x0, y + 1)] = S[2]
    return out


def memory_frame(f):
    img = sprite(EMPTY, PALETTE)
    px = img.load()
    for p, c in liquid_pixels(f).items():
        px[p] = c
    for p, c in wisp_pixels(f).items():
        px[p] = c
    # Keep the glass highlight over the silver, and a sparkle on the lit left side on two frames.
    px[3, 9] = hexc(PALETTE["q"])
    px[3, 10] = hexc(PALETTE["q"])
    if f in (2, 6):
        px[5 if f == 2 else 6, 11] = S[4]
    return img


def write_json(name, layer):
    path = os.path.join(MODELS, name + ".json")
    with open(path, "w", newline="\n") as fh:
        fh.write('{\n  "parent": "minecraft:item/generated",\n  "textures": {\n    "layer0": "hogwartsmagic:item/%s"\n  }\n}\n'
                 % layer)
    print("wrote", os.path.normpath(path))


def main():
    from PIL import Image

    empty = sprite(EMPTY, PALETTE)
    p = save(empty, os.path.join(TEX, "empty_memory_phial.png"))
    print("wrote", os.path.normpath(p))

    strip = Image.new("RGBA", (16, 16 * FRAMES), (0, 0, 0, 0))
    for f in range(FRAMES):
        strip.paste(memory_frame(f), (0, 16 * f))
    p = save(strip, os.path.join(TEX, "memory.png"))
    with open(p + ".mcmeta", "w", newline="\n") as fh:
        fh.write('{"animation":{"frametime":4,"interpolate":false}}\n')
    print("wrote", os.path.normpath(p))

    write_json("empty_memory_phial", "empty_memory_phial")
    write_json("memory", "memory")


if __name__ == "__main__":
    main()
