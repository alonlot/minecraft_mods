"""Generates the walk-in Floo fireplace: block models for the hearth and its eight parts, the
blockstates for both blocks, and the scaled-down item model.

The fireplace is designed once, in one 48 x 48 x 16 pixel space (3 blocks wide, 3 tall, 1 deep),
written for FACING north: the back wall is at z = 0 and the opening looks toward +z. Every box is
then cut along the block grid and each block gets the pieces that fall in its cell. Faces that only
exist because of a cut are dropped, so neighbouring parts never draw back-to-back faces.

The hearth (column 1, row 0) is the real block with the block entity; its fire may reach up into
the cell above (block models may go to y = 32), so flames are drawn only by the hearth models.

Run from the repository root:  python tools/floo_fireplace/gen_models.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "hogwartsmagic")
MODELS = os.path.join(ROOT, "models")
NS = "hogwartsmagic"

TEXTURES = {
    "stone": f"{NS}:block/decor/fireplace_stone",
    "soot": f"{NS}:block/decor/fireplace_soot",
    "wood": "minecraft:block/dark_oak_planks",
    "iron": f"{NS}:block/decor/iron",
    "log": "minecraft:block/dark_oak_log",
    "log_end": "minecraft:block/dark_oak_log_top",
    "pot": f"{NS}:block/decor/floo_pot",
    "wax": f"{NS}:block/decor/candle_wax",
    "particle": f"{NS}:block/decor/fireplace_stone",
}

W, H, D = 48, 48, 16  # whole fireplace in pixels
FACES = ("north", "south", "east", "west", "up", "down")


def box(frm, to, tex="stone", **overrides):
    """A solid box in fireplace space. overrides: face name -> texture key."""
    faces = {f: overrides.get(f, tex) for f in FACES}
    return {"from": list(frm), "to": list(to), "faces": faces}


# --------------------------------------------------------------------------- the design
#  x: 0..48 left to right seen from the front, y: 0..48 up, z: 0 (back) .. 16 (front)
STRUCTURE = [
    # hearth floor; the firebox part is sooted
    box((0, 0, 0), (9, 2, 16)),
    box((9, 0, 0), (39, 2, 16), up="soot"),
    box((39, 0, 0), (48, 2, 16)),
    # fire-back: the whole back wall, sooted where the opening shows it
    box((0, 2, 0), (9, 34, 3)),
    box((9, 2, 0), (39, 34, 3), south="soot"),
    box((39, 2, 0), (48, 34, 3)),
    box((0, 34, 0), (48, 48, 3)),
    # jambs: plinth, shaft (sooted on the inside), capital
    box((0, 2, 3), (11, 5, 16)),
    box((1, 5, 3), (9, 31, 15), east="soot"),
    box((0, 31, 3), (11, 34, 16), down="stone"),
    box((37, 2, 3), (48, 5, 16)),
    box((39, 5, 3), (47, 31, 15), west="soot"),
    box((37, 31, 3), (48, 34, 16)),
    # lintel over the opening, sooted underneath, with a keystone
    box((0, 34, 3), (48, 39, 15), down="soot"),
    box((21, 33, 15), (27, 39, 16)),
    # corbel strip and the wooden mantel shelf
    box((0, 39, 3), (48, 40, 16)),
    box((-1, 40, 1), (49, 42, 17), "wood"),
    # chimney breast rising behind the shelf
    box((3, 42, 3), (45, 48, 6)),
]

# Small things on the mantel. Kept with explicit UVs (taken from the old one-block fireplace).
MANTEL = []


def candle(x, z):
    MANTEL.append({"from": [x, 42, z], "to": [x + 1.5, 46, z + 1.5],
                   "faces": {"north": {"texture": "#wax", "uv": [13, 4, 14.5, 8]},
                             "south": {"texture": "#wax", "uv": [1.5, 4, 3, 8]},
                             "east": {"texture": "#wax", "uv": [7.5, 4, 9, 8]},
                             "west": {"texture": "#wax", "uv": [7, 4, 8.5, 8]},
                             "up": {"texture": "#wax", "uv": [7, 7, 8.5, 8.5]},
                             "down": {"texture": "#wax", "uv": [7, 7, 8.5, 8.5]}}})
    MANTEL.append({"from": [x + 0.5, 46, z + 0.5], "to": [x + 1, 47, z + 1],
                   "faces": {f: {"texture": "#wax", "uv": [14, 0, 15, 1]} for f in FACES}})
    MANTEL.append({"from": [x + 0.25, 47, z + 0.75], "to": [x + 1.25, 48.5, z + 0.75], "shade": False,
                   "faces": {"north": {"texture": "#wax", "uv": [12, 0, 14, 3]},
                             "south": {"texture": "#wax", "uv": [12, 0, 14, 3]}}})
    MANTEL.append({"from": [x + 0.75, 47, z + 0.25], "to": [x + 0.75, 48.5, z + 1.25], "shade": False,
                   "faces": {"east": {"texture": "#wax", "uv": [12, 0, 14, 3]},
                             "west": {"texture": "#wax", "uv": [12, 0, 14, 3]}}})


candle(4.5, 10)
candle(8, 12)
candle(38.5, 12)
candle(42, 10)
# the Floo powder pot, centre of the shelf, with a lip
MANTEL.append({"from": [21.5, 42, 8.5], "to": [26.5, 46.5, 13.5],
               "faces": {"north": {"texture": "#pot", "uv": [5.5, 3.5, 10.5, 8]},
                         "south": {"texture": "#pot", "uv": [5.5, 3.5, 10.5, 8]},
                         "east": {"texture": "#pot", "uv": [5.5, 3.5, 10.5, 8]},
                         "west": {"texture": "#pot", "uv": [5.5, 3.5, 10.5, 8]},
                         "up": {"texture": "#pot", "uv": [5.5, 5.5, 10.5, 10.5]},
                         "down": {"texture": "#pot", "uv": [5.5, 5.5, 10.5, 10.5]}}})
MANTEL.append({"from": [21, 46.5, 8], "to": [27, 47.5, 14],
               "faces": {f: {"texture": "#pot", "uv": [5, 2.5, 11, 3.5] if f not in ("up", "down") else [5, 5, 11, 11]}
                         for f in FACES}})

# The fire, owned by the hearth (column 1, row 0); coordinates are hearth-local (x 0..16).
GRATE_AND_LOGS = [
    {"from": [1, 2, 4], "to": [15, 3, 10], "faces": {f: {"texture": "#iron"} for f in FACES}},
    {"from": [1.5, 3, 4], "to": [2.5, 7, 5], "faces": {f: {"texture": "#iron"} for f in FACES}},
    {"from": [13.5, 3, 4], "to": [14.5, 7, 5], "faces": {f: {"texture": "#iron"} for f in FACES}},
    {"from": [2, 3, 4.5], "to": [14, 5.5, 7], "faces": {"north": {"texture": "#log"}, "south": {"texture": "#log"},
                                                        "east": {"texture": "#log_end"}, "west": {"texture": "#log_end"},
                                                        "up": {"texture": "#log"}, "down": {"texture": "#log"}}},
    {"from": [3.5, 4.5, 6.5], "to": [12.5, 7, 9], "faces": {"north": {"texture": "#log"}, "south": {"texture": "#log"},
                                                           "east": {"texture": "#log_end"}, "west": {"texture": "#log_end"},
                                                           "up": {"texture": "#log"}, "down": {"texture": "#log"}}},
]


def flame_plane(x1, x2, y1, y2, z, angle=0.0):
    e = {"from": [x1, y1, z], "to": [x2, y2, z], "shade": False,
         "faces": {"north": {"texture": "#flame", "uv": [0, 0, 16, 16]},
                   "south": {"texture": "#flame", "uv": [0, 0, 16, 16]}}}
    if angle:
        e["rotation"] = {"origin": [8, y1, z], "axis": "y", "angle": angle}
    return e


HEARTH_FLAMES = [  # ordinary fire on the grate, at the back of the firebox
    flame_plane(1, 15, 3, 17, 6.5, 22.5),
    flame_plane(1, 15, 3, 17, 6.5, -22.5),
    flame_plane(2, 14, 3, 14, 8.5),
]
FLOO_FLAMES = [  # the green Floo fire: fills the opening up to the lintel's shadow
    flame_plane(-6, 22, 2, 30, 6, 22.5),
    flame_plane(-6, 22, 2, 30, 6, -22.5),
    flame_plane(-5, 21, 2, 28, 9.5),
    flame_plane(-4, 20, 2, 24, 12.5),
]


# --------------------------------------------------------------------------- cutting into cells
def uv_for(face, lo, hi):
    """Vanilla's automatic UV for a face of the box lo..hi (cell-local), shifted into 0..16."""
    (x1, y1, z1), (x2, y2, z2) = lo, hi
    if face == "north":
        u = [16 - x2, 16 - x1]; v = [16 - y2, 16 - y1]
    elif face == "south":
        u = [x1, x2]; v = [16 - y2, 16 - y1]
    elif face == "west":
        u = [z1, z2]; v = [16 - y2, 16 - y1]
    elif face == "east":
        u = [16 - z2, 16 - z1]; v = [16 - y2, 16 - y1]
    elif face == "up":
        u = [x1, x2]; v = [z1, z2]
    else:
        u = [x1, x2]; v = [16 - z2, 16 - z1]

    def fit(r):
        a, b = r
        if b - a > 16:
            return [0, 16]
        if a < 0:
            return [0, b - a]
        if b > 16:
            return [16 - (b - a), 16]
        return [a, b]

    u, v = fit(u), fit(v)
    return [round(u[0], 4), round(v[0], 4), round(u[1], 4), round(v[1], 4)]


def cell_range(i, n):
    lo = -1e9 if i == 0 else i * 16
    hi = 1e9 if i == n - 1 else i * 16 + 16
    return lo, hi


def cut(col, row):
    """Elements of STRUCTURE + MANTEL that fall in this cell, translated to cell-local pixels."""
    out = []
    xlo, xhi = cell_range(col, 3)
    ylo, yhi = cell_range(row, 3)
    ox, oy = col * 16, row * 16
    for b in STRUCTURE:
        (x1, y1, z1), (x2, y2, z2) = b["from"], b["to"]
        cx1, cx2 = max(x1, xlo), min(x2, xhi)
        cy1, cy2 = max(y1, ylo), min(y2, yhi)
        if cx1 >= cx2 or cy1 >= cy2:
            continue
        lo = (cx1 - ox, cy1 - oy, z1)
        hi = (cx2 - ox, cy2 - oy, z2)
        faces = {}
        # a face is kept only where it lies on the original box's own surface
        keep = {
            "west": cx1 == x1, "east": cx2 == x2,
            "down": cy1 == y1, "up": cy2 == y2,
            "north": True, "south": True,
        }
        outer = {"west": x1 <= 0, "east": x2 >= W, "down": y1 <= 0, "up": y2 >= H,
                 "north": z1 <= 0, "south": z2 >= D}
        on_cell_edge = {"west": lo[0] == 0, "east": hi[0] == 16, "down": lo[1] == 0, "up": hi[1] == 16,
                        "north": lo[2] == 0, "south": hi[2] == 16}
        for f in FACES:
            if not keep[f]:
                continue
            face = {"texture": "#" + b["faces"][f], "uv": uv_for(f, lo, hi)}
            if outer[f] and on_cell_edge[f]:
                face["cullface"] = f
            faces[f] = face
        out.append({"from": [round(c, 4) for c in lo], "to": [round(c, 4) for c in hi], "faces": faces})
    for m in MANTEL:
        # mantel pieces are small and never straddle a cell: place by their centre
        cx = (m["from"][0] + m["to"][0]) / 2
        cy = (m["from"][1] + m["to"][1]) / 2
        if int(cx // 16) != col or min(int(cy // 16), 2) != row:
            continue
        e = json.loads(json.dumps(m))
        e["from"] = [e["from"][0] - ox, e["from"][1] - oy, e["from"][2]]
        e["to"] = [e["to"][0] - ox, e["to"][1] - oy, e["to"][2]]
        out.append(e)
    return out


def model(elements, extra_textures=None):
    tex = dict(TEXTURES)
    if extra_textures:
        tex.update(extra_textures)
    return {"parent": "block/block", "render_type": "minecraft:cutout", "textures": tex, "elements": elements}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(data, fh, indent=1)
        fh.write("\n")


def main():
    hearth = cut(1, 0)
    write(os.path.join(MODELS, "block", "floo_fireplace.json"),
          model(hearth + GRATE_AND_LOGS + HEARTH_FLAMES, {"flame": f"{NS}:block/decor/hearth_flame"}))
    write(os.path.join(MODELS, "block", "floo_fireplace_active.json"),
          model(hearth + GRATE_AND_LOGS + FLOO_FLAMES, {"flame": f"{NS}:block/decor/floo_flame"}))
    write(os.path.join(MODELS, "block", "floo_fireplace_unlit.json"), model(hearth + GRATE_AND_LOGS))

    parts = [(c, r) for r in range(3) for c in range(3) if (c, r) != (1, 0)]
    for c, r in parts:
        write(os.path.join(MODELS, "block", f"floo_fireplace_part_{c}_{r}.json"), model(cut(c, r)))

    # blockstates ---------------------------------------------------------------------------
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for facing, y in rot.items():
        for c in range(3):
            for r in range(3):
                name = f"floo_fireplace_part_{c}_{r}" if (c, r) != (1, 0) else "floo_fireplace_unlit"
                v = {"model": f"{NS}:block/{name}"}
                if y:
                    v["y"] = y
                variants[f"column={c},facing={facing},row={r}"] = v
    write(os.path.join(ROOT, "blockstates", "floo_fireplace_part.json"), {"variants": variants})

    variants = {}
    for facing, y in rot.items():
        for lit in ("true", "false"):
            for sig in ("true", "false"):
                for wl in ("true", "false"):
                    name = "floo_fireplace_unlit" if lit == "false" else (
                        "floo_fireplace_active" if sig == "true" else "floo_fireplace")
                    v = {"model": f"{NS}:block/{name}"}
                    if y:
                        v["y"] = y
                    variants[f"facing={facing},lit={lit},signal_fire={sig},waterlogged={wl}"] = v
    write(os.path.join(ROOT, "blockstates", "floo_fireplace.json"), {"variants": variants})

    # item: the whole fireplace at a third of the size ---------------------------------------
    s = 1.0 / 3.0
    elements = []

    def add(es, ox, oy):
        for e in es:
            e = json.loads(json.dumps(e))
            e["from"] = [round((e["from"][0] + ox) * s, 4), round((e["from"][1] + oy) * s, 4), round(8 + (e["from"][2] - 8) * s, 4)]
            e["to"] = [round((e["to"][0] + ox) * s, 4), round((e["to"][1] + oy) * s, 4), round(8 + (e["to"][2] - 8) * s, 4)]
            if "rotation" in e:
                o = e["rotation"]["origin"]
                e["rotation"]["origin"] = [round((o[0] + ox) * s, 4), round((o[1] + oy) * s, 4), round(8 + (o[2] - 8) * s, 4)]
            for f in e["faces"].values():
                f.pop("cullface", None)
            elements.append(e)

    for c in range(3):
        for r in range(3):
            add(cut(c, r), c * 16, r * 16)
    add(GRATE_AND_LOGS + HEARTH_FLAMES, 16, 0)
    item = model(elements, {"flame": f"{NS}:block/decor/hearth_flame"})
    item.pop("render_type")
    item["display"] = {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.75, 0.75, 0.75]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.5, 0.5, 0.5]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    }
    write(os.path.join(MODELS, "item", "floo_fireplace.json"), item)
    print("hearth elements:", len(hearth), " item elements:", len(elements))


if __name__ == "__main__":
    main()
