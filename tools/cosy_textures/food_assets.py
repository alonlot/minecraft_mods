"""Cosy food data assets: simple item models, loot tables, recipes, the effect icon, and the lang block.

    python tools/cosy_textures/food_assets.py

Idempotent: every file is rewritten from the tables below. Block textures have their own script
(food_blocks.py); item sprites are tools/item_textures/cosy.py. Names here are prefixed food_/kitchen_
where they could collide with the home stream's.
"""
import json
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "hogwartsmagic")
DATA = os.path.join(RES, "data", "hogwartsmagic")
MOD = "hogwartsmagic"


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def item_model(name, texture=None):
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{texture or name}"}})


def block_item_model(name):
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": f"{MOD}:block/{name}"})


def block_loot(name):
    write_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": f"{MOD}:blocks/{name}"})


def shaped(name, pattern, key, result, count=1):
    write_json(os.path.join(DATA, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": key,
        "result": {"id": result, "count": count}})


def effect_icon(name, draw):
    im = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, 17, 17), radius=4, fill=(28, 24, 40, 255), outline=(60, 54, 80, 255))
    draw(d)
    out = os.path.join(ASSETS, "textures", "mob_effect", name + ".png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    im.save(out)


def courage_icon(d):
    # A bar of chocolate (two rows of squares) with a small gold star above it: courage in a wrapper.
    brown = [(154, 96, 50, 255), (122, 70, 34, 255), (87, 48, 26, 255)]
    for row, y in enumerate((8, 12)):
        for col, x in enumerate((3, 9)):
            d.rectangle((x, y, x + 5, y + 2), fill=brown[1])
            d.line((x, y, x + 5, y), fill=brown[0])
            d.line((x, y + 3, x + 5, y + 3), fill=brown[2])
            d.line((x + 5, y, x + 5, y + 3), fill=brown[2])
    star = (255, 226, 122, 255)
    d.point((9, 2), fill=star)
    d.line((8, 3, 10, 3), fill=star)
    d.line((7, 4, 11, 4), fill=star)
    d.line((8, 5, 10, 5), fill=star)
    d.point((7, 6), fill=star)
    d.point((11, 6), fill=star)


# ------------------------------------------------------------------------------------------------
LANG = {}


def lang(key, value):
    LANG[key] = value


def write_lang():
    """Keeps every Cosy-food key as one contiguous block at the very end of en_us.json."""
    path = os.path.join(ASSETS, "lang", "en_us.json")
    text = open(path, encoding="utf-8", newline="").read()
    nl = "\r\n" if "\r\n" in text else "\n"
    lines = text.rstrip().split(nl)
    assert lines[-1].strip() == "}"
    body = lines[:-1]
    mine = set(LANG)
    # Drop a previously written block (our keys, at the tail) before re-appending it.
    while body and body[-1].strip().startswith('"') and json.loads("{" + body[-1].strip().rstrip(",") + "}").popitem()[0] in mine:
        body.pop()
    assert body[-1].strip().startswith('"'), body[-1]
    body[-1] = body[-1].rstrip().rstrip(",") + ","
    new = [f"  {json.dumps(k)}: {json.dumps(v, ensure_ascii=False)}," for k, v in LANG.items()]
    new[-1] = new[-1].rstrip(",")
    out = nl.join(body + new + ["}"]) + nl
    json.loads(out)
    open(path, "w", encoding="utf-8", newline="").write(out)


def build():
    # ---- B1 ----
    for n in ("chocolate_bar", "pumpkin_pasty", "treacle_tart", "cauldron_cake", "tankard", "tankard_of_butterbeer"):
        item_model(n)
    effect_icon("chocolate_courage", courage_icon)
    shaped("tankard", ["PN", "PP"],
           {"P": {"tag": "minecraft:planks"}, "N": {"item": "minecraft:iron_nugget"}}, f"{MOD}:tankard", 2)
    lang("item.hogwartsmagic.chocolate_bar", "Chocolate Bar")
    lang("item.hogwartsmagic.pumpkin_pasty", "Pumpkin Pasty")
    lang("item.hogwartsmagic.treacle_tart", "Treacle Tart")
    lang("item.hogwartsmagic.cauldron_cake", "Cauldron Cake")
    lang("item.hogwartsmagic.tankard", "Tankard")
    lang("item.hogwartsmagic.tankard_of_butterbeer", "Tankard of Butterbeer")
    lang("effect.hogwartsmagic.chocolate_courage", "Chocolate Courage")
    lang("effect.hogwartsmagic.chocolate_courage.description", "A Dementor's chill slides off you.")
    write_lang()


if __name__ == "__main__":
    build()
    print("food assets written")
