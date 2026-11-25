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



# Card names and one-line flavour texts (original wording). Keys match WizardCards.
CARDS = {
    "merlin": ("Merlin", "Every legend of magic is measured against his, and he never once explained a trick."),
    "albus_dumbledore": ("Albus Dumbledore", "Headmaster, duellist and keeper of sweets; he saw far more than he ever said."),
    "godric_gryffindor": ("Godric Gryffindor", "A founder who prized nerve above nearly everything and seldom walked away from a fight."),
    "helga_hufflepuff": ("Helga Hufflepuff", "A founder who took in every student the others turned away, and cooked rather well besides."),
    "rowena_ravenclaw": ("Rowena Ravenclaw", "A founder who held that a sharp mind is the finest treasure, and wore hers as a diadem."),
    "salazar_slytherin": ("Salazar Slytherin", "A founder of great cunning and a narrow welcome, whose quarrels divided the school."),
    "nicolas_flamel": ("Nicolas Flamel", "An alchemist who outlived his century; his famous stone is best left out of polite talk."),
    "morgana": ("Morgana", "An enchantress of the old courts, feared by some and quietly borrowed from by many."),
    "circe": ("Circe", "An ancient sorceress who turned unwelcome guests into animals and took no complaints."),
    "agrippa": ("Agrippa", "A scholar who wrote the book on hidden wisdom, and perhaps a few on things best left hidden."),
    "ptolemy": ("Ptolemy", "An astronomer who charted the heavens so carefully that even wizards still cite him."),
    "paracelsus": ("Paracelsus", "A physician-alchemist who mixed medicine with mystery and trusted neither blindly."),
    "cliodna": ("Cliodna", "A queen of the Irish hills in the old tales, said to heal with song and vanish at dawn."),
    "hengist_of_woodcroft": ("Hengist of Woodcroft", "A hermit-wizard who founded a quiet village and kept it quiet with discreet charms."),
    "bowman_wright": ("Bowman Wright", "A goldsmith whose small winged invention has been chased across the sky ever since."),
    "elfrida_clagg": ("Elfrida Clagg", "A council chief who argued with great force and was rarely outvoted."),
    "gulliver_pokeby": ("Gulliver Pokeby", "A wizard who wrote to everyone he ever met and filed the replies in tidy bundles."),
    "herpo_the_foul": ("Herpo the Foul", "A dark wizard who dabbled in serpents and forbidden magic, and earned the nickname."),
    "bertie_bott": ("Bertie Bott", "An inventor of sweets that taste of everything, which is not always a kindness."),
    "newt_scamander": ("Newt Scamander", "A magizoologist who carried a suitcase of impossible creatures and endless patience."),
}


# Action-bar lines for each bean flavour (original wording). Keys match BeanTable.
BEANS = {
    "toffee": "Toffee. Your feet feel light.",
    "peppermint": "Peppermint, cool and clear. The dark seems friendlier.",
    "honeycomb": "Honeycomb. Warmth spreads through you.",
    "lemon_sherbet": "Lemon sherbet. You could bounce off the walls.",
    "treacle_fudge": "Treacle fudge. Wonderfully filling.",
    "candy_floss": "Candy floss. What a sugar rush!",
    "marshmallow": "Marshmallow. Perfectly pleasant.",
    "buttered_toast": "Buttered toast. Nothing to report.",
    "cabbage": "Boiled cabbage. Hm.",
    "parsley": "Parsley. Like a mouthful of garden.",
    "plain_vanilla": "Plain vanilla. Not a thing wrong with it.",
    "earwax": "Earwax!",
    "soap": "Soap. Ugh.",
    "bogey": "Bogey. Who thought of that one?",
    "fizzing_whizbee": "Fizzing whizbee. Your feet leave the ground!",
    "burp": "Burp-berry. Excuse you.",
    "sardine": "Sardine. Your stomach turns.",
}


# ---- block model helpers ----------------------------------------------------------------------------
def auto_uv(face, frm, to):
    """Blockbench-style box UV (in 0..16) for one face of an axis-aligned box, so a 16 px texture tiles in block scale."""
    fx, fy, fz = frm
    tx, ty, tz = to
    if face == "north":
        return [16 - tx, 16 - ty, 16 - fx, 16 - fy]
    if face == "south":
        return [fx, 16 - ty, tx, 16 - fy]
    if face == "east":
        return [16 - tz, 16 - ty, 16 - fz, 16 - fy]
    if face == "west":
        return [fz, 16 - ty, tz, 16 - fy]
    if face == "up":
        return [fx, fz, tx, tz]
    return [fx, 16 - tz, tx, 16 - fz]  # down


def box(frm, to, tex, faces=("north", "south", "east", "west", "up", "down"), end_tex=None, uv=None, tint=None):
    """One model element. `tex` is the texture variable for the long faces, `end_tex` for east/west if given."""
    el = {"from": list(frm), "to": list(to), "faces": {}}
    for f in faces:
        t = end_tex if (end_tex and f in ("east", "west")) else tex
        face = {"uv": (uv or {}).get(f) or auto_uv(f, frm, to), "texture": "#" + t}
        if f in ("up", "down") or True:
            face["cullface"] = None
            del face["cullface"]
        el["faces"][f] = face
    return el


def write_block_model(name, textures, elements, parent="minecraft:block/block"):
    write_json(os.path.join(ASSETS, "models", "block", name + ".json"),
               {"parent": parent, "textures": textures, "elements": elements})


def facing_blockstate(name, model_for):
    """A blockstate with one variant per facing (and any extra state key/value pairs model_for returns)."""
    variants = {}
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for key, model in model_for(facing):
            variants[(f"facing={facing}," + key) if key else f"facing={facing}"] = {"model": model, **({"y": rot} if rot else {})}
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})


def keg_models():
    wood = "wood"
    elements = [
        box([0, 2, 2], [16, 11, 14], wood, end_tex="end"),
        box([0, 0, 3], [16, 2, 13], wood, end_tex="end"),
        box([0, 11, 3], [16, 13, 13], wood, end_tex="end"),
        box([1, 0.5, 1.5], [3, 12.5, 14.5], "iron"),
        box([13, 0.5, 1.5], [15, 12.5, 14.5], "iron"),
        box([7, 4.5, 0.2], [9, 6.5, 2], "tap"),
        box([7.5, 2.5, 0.4], [8.5, 4.5, 1.4], "tap"),
        {"from": [10.5, 3, 1.6], "to": [12.5, 10, 2.0],
         "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#gauge"}}},
    ]
    for level in range(4):
        write_block_model(f"butterbeer_keg_{level}", {
            "wood": f"{MOD}:block/food_keg_wood", "end": f"{MOD}:block/food_keg_end",
            "iron": f"{MOD}:block/food_keg_iron", "tap": f"{MOD}:block/food_keg_tap",
            "gauge": f"{MOD}:block/food_keg_gauge_{level}", "particle": f"{MOD}:block/food_keg_wood"}, elements)
    facing_blockstate("butterbeer_keg", lambda f: [(f"fill={n}", f"{MOD}:block/butterbeer_keg_{n}") for n in range(4)])
    write_json(os.path.join(ASSETS, "models", "item", "butterbeer_keg.json"), {"parent": f"{MOD}:block/butterbeer_keg_0"})
    block_loot("butterbeer_keg")
    write_json(os.path.join(DATA, "recipe", "butterbeer_keg.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:barrel"}, {"item": "minecraft:iron_ingot"}, {"item": "minecraft:copper_ingot"}],
        "result": {"id": f"{MOD}:butterbeer_keg", "count": 1}})



def glow_face(texture):
    return {"texture": "#" + texture,
            "forge_data": {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}}


def stove_models():
    base_tex = {"front": f"{MOD}:block/kitchen_stove_front", "side": f"{MOD}:block/kitchen_stove_side",
                "top": f"{MOD}:block/kitchen_stove_top", "particle": f"{MOD}:block/kitchen_stove_side",
                "glow": f"{MOD}:block/kitchen_stove_glow", "top_glow": f"{MOD}:block/kitchen_stove_top_glow"}
    body = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
        "north": {"uv": [0, 0, 16, 16], "texture": "#front"}, "south": {"uv": [0, 0, 16, 16], "texture": "#side"},
        "east": {"uv": [0, 0, 16, 16], "texture": "#side"}, "west": {"uv": [0, 0, 16, 16], "texture": "#side"},
        "up": {"uv": [0, 0, 16, 16], "texture": "#top"}, "down": {"uv": [0, 0, 16, 16], "texture": "#side"}}}
    front_glow = {"from": [0, 0, -0.02], "to": [16, 16, 0], "faces": {"north": {"uv": [0, 0, 16, 16], **glow_face("glow")}}}
    top_glow = {"from": [0, 16, 0], "to": [16, 16.02, 16], "faces": {"up": {"uv": [0, 0, 16, 16], **glow_face("top_glow")}}}
    write_json(os.path.join(ASSETS, "models", "block", "enchanted_stove.json"), {
        "parent": "minecraft:block/block", "textures": base_tex, "elements": [body]})
    write_json(os.path.join(ASSETS, "models", "block", "enchanted_stove_lit.json"), {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": base_tex,
        "elements": [body, front_glow, top_glow]})
    variants = {}
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit, model in (("false", "enchanted_stove"), ("true", "enchanted_stove_lit")):
            variants[f"facing={facing},lit={lit}"] = {"model": f"{MOD}:block/{model}", **({"y": rot} if rot else {})}
    write_json(os.path.join(ASSETS, "blockstates", "enchanted_stove.json"), {"variants": variants})
    block_item_model("enchanted_stove")
    block_loot("enchanted_stove")
    shaped("enchanted_stove", ["III", "IFI", " B "],
           {"I": {"item": "minecraft:iron_ingot"}, "F": {"item": "minecraft:furnace"}, "B": {"item": "minecraft:blaze_powder"}},
           f"{MOD}:enchanted_stove")


def pot_models():
    tex = {"iron": f"{MOD}:block/kitchen_pot_iron", "rim": f"{MOD}:block/kitchen_pot_rim",
           "broth": f"{MOD}:block/kitchen_pot_broth", "particle": f"{MOD}:block/kitchen_pot_iron"}
    els = [
        box([2, 0, 2], [14, 1, 14], "iron"),
        box([1, 1, 1], [15, 9, 3], "iron"),
        box([1, 1, 13], [15, 9, 15], "iron"),
        box([1, 1, 3], [3, 9, 13], "iron"),
        box([13, 1, 3], [15, 9, 13], "iron"),
        box([0.5, 9, 0.5], [15.5, 10, 15.5], "rim", faces=("north", "south", "east", "west", "up")),
        box([0, 6, 6], [1, 9, 10], "rim"),
        box([15, 6, 6], [16, 9, 10], "rim"),
        box([1, 0, 1], [3, 1, 3], "rim"), box([13, 0, 1], [15, 1, 3], "rim"),
        box([1, 0, 13], [3, 1, 15], "rim"), box([13, 0, 13], [15, 1, 15], "rim"),
    ]
    # The broth: a flat sheet just under the rim, only in the filled model.
    broth = {"from": [3, 8, 3], "to": [13, 8.01, 13], "faces": {"up": {"uv": [3, 3, 13, 13], "texture": "#broth"}}}
    write_block_model("cooking_pot", tex, els)
    write_block_model("cooking_pot_filled", tex, els + [broth])
    write_json(os.path.join(ASSETS, "blockstates", "cooking_pot.json"), {"variants": {
        "filled=false": {"model": f"{MOD}:block/cooking_pot"}, "filled=true": {"model": f"{MOD}:block/cooking_pot_filled"}}})
    block_item_model("cooking_pot")
    block_loot("cooking_pot")
    shaped("cooking_pot", ["I I", "IBI", "III"],
           {"I": {"item": "minecraft:iron_ingot"}, "B": {"item": "minecraft:bucket"}}, f"{MOD}:cooking_pot")


POT_RECIPES = {
    # name: (ingredients, cook_ticks, result, count)
    "chocolate_bar": (["minecraft:cocoa_beans"] * 2 + ["minecraft:sugar", "minecraft:milk_bucket"], 300, f"{MOD}:chocolate_bar", 3),
    "chocolate_frog": ([f"{MOD}:chocolate_bar"] * 2 + ["minecraft:slime_ball"], 400, f"{MOD}:chocolate_frog", 2),
    "pumpkin_pasty": (["minecraft:pumpkin"] + ["minecraft:wheat"] * 2 + ["minecraft:egg", "minecraft:sugar"], 400, f"{MOD}:pumpkin_pasty", 3),
    "treacle_tart": (["minecraft:wheat"] * 2 + ["minecraft:honey_bottle", "minecraft:egg", "minecraft:sugar"], 400, f"{MOD}:treacle_tart", 2),
    "cauldron_cake": (["minecraft:wheat", "minecraft:cocoa_beans", "minecraft:sugar", "minecraft:egg", "minecraft:milk_bucket"], 400, f"{MOD}:cauldron_cake", 3),
    "bertie_botts_beans": (["minecraft:sugar"] * 2 + ["minecraft:sweet_berries", "minecraft:slime_ball", "minecraft:spider_eye", "minecraft:glow_berries"], 500, f"{MOD}:bertie_botts_beans", 1),
}


def kitchen_data():
    for name, (ings, ticks, result, count) in POT_RECIPES.items():
        write_json(os.path.join(DATA, "recipe", "pot", name + ".json"), {
            "type": f"{MOD}:pot_cooking", "ingredients": [{"item": i} for i in ings],
            "cook_ticks": ticks, "result": {"id": result, "count": count}})
    write_json(os.path.join(DATA, "tags", "block", "heat_sources.json"), {
        "replace": False, "values": [
            f"{MOD}:enchanted_stove", "minecraft:campfire", "minecraft:soul_campfire",
            {"id": f"{MOD}:hearth", "required": False},  # the home pack adds the hearth
            f"{MOD}:floo_fireplace", "minecraft:fire", "minecraft:soul_fire", "minecraft:lava", "minecraft:magma_block"]})


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
    # ---- B2 ----
    item_model("chocolate_frog")
    item_model("card_album")
    write_json(os.path.join(ASSETS, "models", "item", "wizard_card.json"), {
        "parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/wizard_card"},
        "overrides": [
            {"predicate": {"custom_model_data": 2.0}, "model": f"{MOD}:item/wizard_card_silver"},
            {"predicate": {"custom_model_data": 3.0}, "model": f"{MOD}:item/wizard_card_gold"}]})
    item_model("wizard_card_silver")
    item_model("wizard_card_gold")
    write_json(os.path.join(DATA, "recipe", "card_album.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": "minecraft:book"}, {"item": "minecraft:leather"}, {"item": "minecraft:gold_nugget"}],
        "result": {"id": f"{MOD}:card_album", "count": 1}})
    lang("item.hogwartsmagic.chocolate_frog", "Chocolate Frog")
    lang("item.hogwartsmagic.wizard_card", "Famous Wizard Card")
    lang("item.hogwartsmagic.card_album", "Card Album")
    lang("item.hogwartsmagic.card_album.progress", "Cards: %s / %s")
    lang("entity.hogwartsmagic.chocolate_frog", "Chocolate Frog")
    lang("message.hogwartsmagic.card_album.added", "%s new card(s) added to the album")
    lang("gui.hogwartsmagic.card_album.title", "Card Album")
    lang("gui.hogwartsmagic.card_album.count", "%s / %s")
    lang("gui.hogwartsmagic.card_album.hint", "Use the album to add cards from your pack. Duplicates stay with you.")
    lang("gui.hogwartsmagic.card_album.missing", "A card you have not found yet")
    lang("card.hogwartsmagic.rarity.0", "Common")
    lang("card.hogwartsmagic.rarity.1", "Rare")
    lang("card.hogwartsmagic.rarity.2", "Legendary")
    for key, (name, line) in CARDS.items():
        lang(f"card.hogwartsmagic.{key}.name", name)
        lang(f"card.hogwartsmagic.{key}.line", line)
    # ---- B3 ----
    item_model("bertie_botts_beans")
    lang("item.hogwartsmagic.bertie_botts_beans", "Bertie Bott's Every Flavour Beans")
    lang("item.hogwartsmagic.bertie_botts_beans.left", "Beans left: %s")
    for key, text in BEANS.items():
        lang(f"bean.hogwartsmagic.{key}", text)
    # ---- B4 ----
    keg_models()
    lang("block.hogwartsmagic.butterbeer_keg", "Butterbeer Keg")
    lang("message.hogwartsmagic.keg.needs", "The keg still wants: %s")
    lang("message.hogwartsmagic.keg.added", "Added to the keg.")
    lang("message.hogwartsmagic.keg.brewing", "The batch is complete. It needs a little while to age.")
    lang("message.hogwartsmagic.keg.not_ready", "The butterbeer is still ageing: %s left.")
    lang("message.hogwartsmagic.keg.ready", "Ready to pour: %s servings. Bring an empty tankard.")
    lang("message.hogwartsmagic.keg.empty", "The keg is empty.")
    lang("message.hogwartsmagic.keg.busy", "The keg is full; wait for the batch.")
    lang("message.hogwartsmagic.keg.has_that", "The keg already has that.")
    # ---- B5 ----
    stove_models()
    pot_models()
    kitchen_data()
    lang("block.hogwartsmagic.enchanted_stove", "Enchanted Stove")
    lang("block.hogwartsmagic.cooking_pot", "Self-Stirring Cooking Pot")
    lang("subtitles.hogwartsmagic.cosy.pot_bubble_loop", "Pot bubbles")
    lang("message.hogwartsmagic.stove.full", "The stove can hold no more fuel.")
    lang("message.hogwartsmagic.stove.fuel", "Fuel left: about %s coals")
    lang("message.hogwartsmagic.pot.enchanted", "The ladle takes up its own stirring.")
    lang("message.hogwartsmagic.pot.nothing_to_stir", "There is nothing in the pot to stir.")
    lang("message.hogwartsmagic.pot.done", "The dish is done. Take it with an empty hand.")
    lang("message.hogwartsmagic.pot.full", "The pot is full.")
    lang("message.hogwartsmagic.pot.empty", "The pot is empty.")
    lang("message.hogwartsmagic.pot.cold", "The pot is cold: stand it on a lit stove, campfire or hearth.")
    write_lang()


if __name__ == "__main__":
    build()
    print("food assets written")
