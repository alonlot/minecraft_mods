"""
Block models, blockstates, item models, loot tables and recipes for the cosy home pack.

Run from the repo root:  python tools/cosy_textures/gen_cosy_models.py
Everything is written as JSON under src/main/resources; a rerun overwrites with identical files.
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/hogwartsmagic")
DATA = os.path.join(RES, "data/hogwartsmagic")
FACES = ("north", "south", "east", "west", "up", "down")
HOUSES = ("gryffindor", "ravenclaw", "hufflepuff", "slytherin")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def model(name, obj):
    write(os.path.join(ASSETS, "models/block", name + ".json"), obj)


def blockstate(name, obj):
    write(os.path.join(ASSETS, "blockstates", name + ".json"), obj)


def item_block(name, parent=None, gui=True):
    obj = {"parent": parent or "hogwartsmagic:block/" + name}
    if gui:
        obj["display"] = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]}}
    write(os.path.join(ASSETS, "models/item", name + ".json"), obj)


def loot_self(name):
    write(os.path.join(DATA, "loot_table/blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": "hogwartsmagic:" + name}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
        "random_sequence": "hogwartsmagic:blocks/" + name,
    })


def recipe_shaped(name, pattern, key, result, count=1, category="misc"):
    write(os.path.join(DATA, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shaped",
        "category": category,
        "pattern": pattern,
        "key": {k: {"item": v} if ":" in v and not v.startswith("#") else {"tag": v[1:]} for k, v in key.items()},
        "result": {"id": "hogwartsmagic:" + result, "count": count},
    })


def recipe_shapeless(name, ingredients, result, count=1, category="misc"):
    write(os.path.join(DATA, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shapeless",
        "category": category,
        "ingredients": [{"item": i} if not i.startswith("#") else {"tag": i[1:]} for i in ingredients],
        "result": {"id": "hogwartsmagic:" + result, "count": count},
    })


def box(frm, to, tex, faces=FACES, uv=None, **extra):
    """One model element: the same texture on the given faces."""
    el = {"from": list(frm), "to": list(to), "faces": {}}
    for f in faces:
        face = {"texture": tex}
        if uv and f in uv:
            face["uv"] = list(uv[f])
        el["faces"][f] = face
    el.update(extra)
    return el


def plane(frm, to, tex, faces, **extra):
    el = {"from": list(frm), "to": list(to), "shade": False, "faces": {f: {"texture": tex, "uv": [0, 0, 16, 16]} for f in faces}}
    el.update(extra)
    return el


def rotations(model_id, prop="facing", extra=None):
    """Variants for the four horizontal facings of a north-based model."""
    out = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": model_id}
        if y:
            v["y"] = y
        out[prop + "=" + facing] = v
    return out


# ---------------------------------------------------------------------------------------------- A1: hearth

def hearth():
    textures = {
        "brick": "hogwartsmagic:block/decor/hearth_brick",
        "stone": "hogwartsmagic:block/decor/fireplace_stone",
        "soot": "hogwartsmagic:block/decor/fireplace_soot",
        "iron": "hogwartsmagic:block/decor/iron",
        "log": "minecraft:block/dark_oak_log",
        "flame": "hogwartsmagic:block/decor/hearth_flame",
        "particle": "hogwartsmagic:block/decor/hearth_brick",
    }
    shell = [
        box((0, 0, 0), (16, 2, 16), "#stone"),                      # hearthstone
        box((0, 2, 0), (16, 16, 4.5), "#brick"),                    # chimney breast
        box((3, 2, 4.5), (13, 12, 5), "#soot", faces=("south", "east", "west", "up")),  # sooted back
        box((0, 2, 5), (3, 12, 16), "#brick"),                      # left jamb
        box((13, 2, 5), (16, 12, 16), "#brick"),                    # right jamb
        box((0, 12, 5), (16, 14, 16), "#brick"),                    # lintel
        box((0, 14, 3), (16, 16, 16), "#stone"),                    # mantel shelf, proud of the breast
    ]
    fire_bed = [
        box((3.5, 2, 6), (12.5, 3, 13), "#iron"),                   # grate
        box((4, 3, 7), (12, 5.5, 9.5), "#log"),
        box((5, 3, 10), (11, 5.5, 12.5), "#log"),
    ]
    flames = [
        plane((3.5, 3, 9), (12.5, 11, 9), "#flame", ("north", "south")),
        plane((8, 3, 6.5), (8, 11, 11.5), "#flame", ("east", "west")),
    ]
    lit = {"parent": "block/block", "render_type": "minecraft:cutout", "textures": textures,
           "elements": shell + fire_bed + flames}
    unlit = {"parent": "block/block", "render_type": "minecraft:cutout", "textures": textures,
             "elements": shell + fire_bed}
    model("hearth", lit)
    model("hearth_unlit", unlit)
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for state, mid in (("true", "hogwartsmagic:block/hearth"), ("false", "hogwartsmagic:block/hearth_unlit")):
            v = {"model": mid}
            if y:
                v["y"] = y
            variants["facing=%s,lit=%s" % (facing, state)] = v
    blockstate("hearth", {"variants": variants})
    item_block("hearth")
    loot_self("hearth")
    recipe_shaped("hearth", ["CBC", "CFC", "CBC"],
                  {"C": "minecraft:cobblestone", "B": "minecraft:bricks", "F": "minecraft:campfire"}, "hearth")


def main():
    hearth()


if __name__ == "__main__":
    main()
