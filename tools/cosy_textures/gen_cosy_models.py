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


# ---------------------------------------------------------------------------- A2: floating candles

def candles():
    """Split the existing three-candle model into one model per candle, lit and unlit, for the bobbing renderer.
    The source model's elements come in groups of four: body, wick base, then two crossed flame planes."""
    src = json.load(open(os.path.join(ASSETS, "models/block/floating_candle.json"), encoding="utf-8"))
    els = src["elements"]
    assert len(els) == 12
    for i in range(3):
        group = els[i * 4:(i + 1) * 4]
        for name, elements in (("floating_candle_%d" % i, group), ("floating_candle_%d_unlit" % i, group[:2])):
            model(name, {"parent": "block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
                         "textures": src["textures"], "elements": elements})


# -------------------------------------------------------------------------------- A3: furniture

VELVETS = {
    "gryffindor": "gryffindor_velvet", "ravenclaw": "blue_velvet", "hufflepuff": "yellow_velvet",
    "slytherin": "green_velvet", "patchwork": "patchwork_velvet",
}
FACING_Y = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def facing_variants(model_id):
    out = {}
    for facing, y in FACING_Y:
        v = {"model": model_id}
        if y:
            v["y"] = y
        out["facing=" + facing] = v
    return out


def armchairs():
    for name, velvet in VELVETS.items():
        block_id = name + "_armchair"
        tex = "hogwartsmagic:block/decor/" + velvet
        model(block_id, {"parent": "hogwartsmagic:block/common_room_armchair",
                         "textures": {"velvet": tex, "particle": tex}})
        blockstate(block_id, {"variants": facing_variants("hogwartsmagic:block/" + block_id)})
        item_block(block_id)
        loot_self(block_id)
    # Shapeless from the common room armchair plus the house two dyes, so it never matches that chair's own recipe.
    house_dyes = {"gryffindor": ("red_dye", "yellow_dye"), "ravenclaw": ("blue_dye", "light_gray_dye"),
                  "hufflepuff": ("yellow_dye", "black_dye"), "slytherin": ("green_dye", "light_gray_dye")}
    for house, (d1, d2) in house_dyes.items():
        recipe_shapeless(house + "_armchair", ["hogwartsmagic:common_room_armchair", "minecraft:" + d1, "minecraft:" + d2],
                         house + "_armchair")
    recipe_shapeless("patchwork_armchair", ["hogwartsmagic:common_room_armchair", "minecraft:red_wool",
                                            "minecraft:blue_wool", "minecraft:yellow_wool"], "patchwork_armchair")


def sofa_model(left_arm, right_arm):
    t = {"velvet": "hogwartsmagic:block/decor/sofa_velvet", "leg": "minecraft:block/stripped_dark_oak_log",
         "particle": "hogwartsmagic:block/decor/sofa_velvet"}
    els = []
    for x in (0.5, 13.5):
        for z in (1.5, 12.5):
            els.append(box((x, 0, z), (x + 2, 2, z + 2), "#leg"))
    els.append(box((0, 2, 1), (16, 7, 15), "#velvet"))                                   # frame
    els.append(box((3 if left_arm else 0, 7, 1), (13 if right_arm else 16, 9, 11.5), "#velvet"))   # cushion
    els.append(box((0, 7, 11.5), (16, 18, 15), "#velvet"))                              # back
    els.append(box((0, 18, 11.5), (16, 19.5, 14.5), "#velvet"))                         # back roll
    if left_arm:
        els.append(box((0, 7, 1), (3, 12, 11.5), "#velvet"))
        els.append(box((0, 12, 0.5), (3.5, 13, 12), "#velvet"))
    if right_arm:
        els.append(box((13, 7, 1), (16, 12, 11.5), "#velvet"))
        els.append(box((12.5, 12, 0.5), (16, 13, 12), "#velvet"))
    return {"parent": "block/block", "textures": t, "elements": els}


def sofa():
    parts = {"single": (True, True), "left": (True, False), "middle": (False, False), "right": (False, True)}
    variants = {}
    for part, (l, r) in parts.items():
        model("sofa_" + part, sofa_model(l, r))
        for facing, y in FACING_Y:
            v = {"model": "hogwartsmagic:block/sofa_" + part}
            if y:
                v["y"] = y
            variants["facing=%s,part=%s" % (facing, part)] = v
    blockstate("sofa", {"variants": variants})
    item_block("sofa", parent="hogwartsmagic:block/sofa_single")
    loot_self("sofa")
    recipe_shaped("sofa", ["BBB", "BBB", "P P"], {"B": "minecraft:brown_wool", "P": "#minecraft:planks"}, "sofa")


def footstool():
    t = {"velvet": "hogwartsmagic:block/decor/patchwork_velvet", "leg": "minecraft:block/stripped_dark_oak_log",
         "particle": "hogwartsmagic:block/decor/patchwork_velvet"}
    els = []
    for x in (3.5, 10.5):
        for z in (3.5, 10.5):
            els.append(box((x, 0, z), (x + 2, 2, z + 2), "#leg"))
    els.append(box((3, 2, 3), (13, 5, 13), "#velvet"))
    els.append(box((3.5, 5, 3.5), (12.5, 6, 12.5), "#velvet"))
    model("footstool", {"parent": "block/block", "textures": t, "elements": els})
    blockstate("footstool", {"variants": facing_variants("hogwartsmagic:block/footstool")})
    item_block("footstool")
    loot_self("footstool")
    recipe_shaped("footstool", ["W", "P"], {"W": "#minecraft:wool", "P": "#minecraft:planks"}, "footstool")


def quilt():
    t = {"quilt": "hogwartsmagic:block/decor/patchwork_quilt", "particle": "hogwartsmagic:block/decor/patchwork_quilt"}
    model("patchwork_quilt", {"parent": "block/block", "textures": t,
                              "elements": [box((0, 0, 0), (16, 1, 16), "#quilt")]})
    # Seven pixels down: a bed is nine pixels tall, so the layer rests on the mattress.
    model("patchwork_quilt_on_bed", {"parent": "block/block", "textures": t,
                                     "elements": [box((0, -7, 0), (16, -6, 16), "#quilt")]})
    blockstate("patchwork_quilt", {"variants": {
        "on_bed=false": {"model": "hogwartsmagic:block/patchwork_quilt"},
        "on_bed=true": {"model": "hogwartsmagic:block/patchwork_quilt_on_bed"}}})
    item_block("patchwork_quilt", gui=False)
    loot_self("patchwork_quilt")
    recipe_shaped("patchwork_quilt", ["RB", "YG"],
                  {"R": "minecraft:red_wool", "B": "minecraft:blue_wool", "Y": "minecraft:yellow_wool",
                   "G": "minecraft:green_wool"}, "patchwork_quilt", count=2)


def rug_elements(sides):
    """Border strips for the given sides. East and west run the full length; north and south fit between them."""
    strips = {
        "north": box((2, 0, 0), (14, 1, 2), "#border", faces=("up", "north"),
                     uv={"up": (2, 0, 14, 2), "north": (2, 0, 14, 1)}),
        "south": box((2, 0, 14), (14, 1, 16), "#border", faces=("up", "south"),
                     uv={"up": (2, 0, 14, 2), "south": (2, 0, 14, 1)}),
        "east": box((14, 0, 0), (16, 1, 16), "#border", faces=("up", "east"),
                    uv={"up": (0, 0, 16, 2), "east": (0, 0, 16, 1)}),
        "west": box((0, 0, 0), (2, 1, 16), "#border", faces=("up", "west"),
                    uv={"up": (0, 0, 16, 2), "west": (0, 0, 16, 1)}),
    }
    strips["south"]["faces"]["up"]["rotation"] = 180
    strips["east"]["faces"]["up"]["rotation"] = 90
    strips["west"]["faces"]["up"]["rotation"] = 270
    return [strips[d] for d in sides]


def rugs():
    names = list(HOUSES) + ["rag"]
    for n in names:
        block_id = n + "_rug"
        centre = "hogwartsmagic:block/decor/" + block_id
        border = centre + "_border"
        # The base sits a hair below the border so the two never share a plane. It has no sides; the border has them.
        base = box((0, 0, 0), (16, 0.98, 16), "#centre", faces=("up", "down"))
        textures = {"centre": centre, "border": border, "particle": centre}
        model(block_id + "_base", {"parent": "block/block", "textures": textures, "elements": [base]})
        for d in ("north", "east", "south", "west"):
            model("%s_border_%s" % (block_id, d), {"parent": "block/block", "textures": textures,
                                                   "elements": rug_elements([d])})
        model(block_id + "_item", {"parent": "block/block", "textures": textures,
                                   "elements": [base] + rug_elements(["north", "east", "south", "west"])})
        parts = [{"apply": {"model": "hogwartsmagic:block/" + block_id + "_base"}}]
        for d in ("north", "east", "south", "west"):
            parts.append({"when": {d: "true"}, "apply": {"model": "hogwartsmagic:block/%s_border_%s" % (block_id, d)}})
        blockstate(block_id, {"multipart": parts})
        item_block(block_id, parent="hogwartsmagic:block/" + block_id + "_item", gui=False)
        loot_self(block_id)
    for house, wool in (("gryffindor", "red"), ("ravenclaw", "blue"), ("hufflepuff", "yellow"), ("slytherin", "green")):
        recipe_shaped(house + "_rug", ["WWW"], {"W": "minecraft:%s_wool" % wool}, house + "_rug", count=3)
    recipe_shaped("rag_rug", ["WSW"], {"W": "#minecraft:wool", "S": "minecraft:string"}, "rag_rug", count=2)


def reading_nook():
    t = {"books": "minecraft:block/bookshelf", "planks": "minecraft:block/oak_planks",
         "brass": "hogwartsmagic:block/decor/brass", "shade": "hogwartsmagic:block/decor/parchment",
         "green": "hogwartsmagic:block/decor/book_cover_green", "red": "hogwartsmagic:block/decor/book_cover_red",
         "particle": "minecraft:block/oak_planks"}
    body = box((0, 0, 3), (16, 9, 15), "#planks")
    body["faces"]["north"] = {"texture": "#books", "uv": [0, 0, 16, 9]}
    els = [body,
           box((2, 9, 5.5), (8, 10.5, 11), "#green"),
           box((3, 10.5, 6), (7.5, 12, 10.5), "#red"),
           box((10, 9, 9), (14, 10, 13), "#brass"),
           box((11.5, 10, 10.5), (12.5, 13, 11.5), "#brass"),
           box((10, 12, 9), (14, 16, 13), "#shade", faces=("north", "south", "east", "west", "up"))]
    model("reading_nook_shelf", {"parent": "block/block", "render_type": "minecraft:cutout", "textures": t, "elements": els})
    blockstate("reading_nook_shelf", {"variants": facing_variants("hogwartsmagic:block/reading_nook_shelf")})
    item_block("reading_nook_shelf")
    loot_self("reading_nook_shelf")
    recipe_shaped("reading_nook_shelf", ["L", "B"], {"L": "minecraft:lantern", "B": "minecraft:bookshelf"},
                  "reading_nook_shelf")


def main():
    hearth()
    candles()
    armchairs()
    sofa()
    footstool()
    quilt()
    rugs()
    reading_nook()

if __name__ == "__main__":
    main()
