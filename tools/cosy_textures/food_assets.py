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
    write_lang()


if __name__ == "__main__":
    build()
    print("food assets written")
