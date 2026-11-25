"""Static check of the cosy food and kitchen assets: the client-side half of the data check.

    python tools/cosy_textures/validate_cosy_food.py

The dedicated-server boot (runServer) parses recipes, loot tables and tags but never loads a model, so a missing
model or texture would only show on a client. This walks every blockstate, block model and item model of the cosy food
ids and checks that what they point at exists, that every item/block/entity/effect has a lang entry, that every block
has a loot table, and that every sprite is a power-of-two square. Exits 1 on any problem.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
A = os.path.join(RES, "assets", "hogwartsmagic")
D = os.path.join(RES, "data", "hogwartsmagic")

ITEMS = ["chocolate_bar", "pumpkin_pasty", "treacle_tart", "cauldron_cake", "tankard", "tankard_of_butterbeer",
         "chocolate_frog", "wizard_card", "card_album", "bertie_botts_beans", "butterbeer_keg", "enchanted_stove",
         "cooking_pot"]
BLOCKS = ["butterbeer_keg", "enchanted_stove", "cooking_pot"]
CARD_KEYS = ["merlin", "albus_dumbledore", "godric_gryffindor", "helga_hufflepuff", "rowena_ravenclaw",
             "salazar_slytherin", "nicolas_flamel", "morgana", "circe", "agrippa", "ptolemy", "paracelsus", "cliodna",
             "hengist_of_woodcroft", "bowman_wright", "elfrida_clagg", "gulliver_pokeby", "herpo_the_foul",
             "bertie_bott", "newt_scamander"]

problems = []


def bad(msg):
    problems.append(msg)


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def tex_path(ref):
    ns, _, p = ref.partition(":") if ":" in ref else ("minecraft", "", ref)
    return None if ns != "hogwartsmagic" else os.path.join(A, "textures", p + ".png")


def model_path(ref):
    ns, _, p = ref.partition(":") if ":" in ref else ("minecraft", "", ref)
    return None if ns != "hogwartsmagic" else os.path.join(A, "models", p + ".json")


def check_model(ref, seen):
    path = model_path(ref)
    if path is None or ref in seen:
        return
    seen.add(ref)
    if not os.path.exists(path):
        bad(f"missing model {ref}")
        return
    m = load(path)
    for name, t in m.get("textures", {}).items():
        if t.startswith("#"):
            continue
        tp = tex_path(t)
        if tp and not os.path.exists(tp):
            bad(f"model {ref}: missing texture {t}")
    used = set()
    for el in m.get("elements", []):
        for face in el.get("faces", {}).values():
            used.add(face["texture"])
    for t in used:
        if t.startswith("#") and t[1:] not in m.get("textures", {}) and "parent" not in m:
            bad(f"model {ref}: face uses undefined {t}")
        if t.startswith("#") and t[1:] not in m.get("textures", {}):
            bad(f"model {ref}: face uses undefined {t}")
    for o in m.get("overrides", []):
        check_model(o["model"], seen)
    parent = m.get("parent")
    if parent:
        check_model(parent, seen)


def main():
    lang = load(os.path.join(A, "lang", "en_us.json"))
    seen = set()
    for b in BLOCKS:
        sp = os.path.join(A, "blockstates", b + ".json")
        if not os.path.exists(sp):
            bad(f"missing blockstate {b}")
            continue
        bs = load(sp)
        for key, v in bs.get("variants", {}).items():
            for entry in (v if isinstance(v, list) else [v]):
                check_model(entry["model"], seen)
        if not os.path.exists(os.path.join(D, "loot_table", "blocks", b + ".json")):
            bad(f"block {b} has no loot table")
        if f"block.hogwartsmagic.{b}" not in lang:
            bad(f"no lang for block {b}")
    for i in ITEMS:
        check_model(f"hogwartsmagic:item/{i}", seen)
        if i not in BLOCKS and f"item.hogwartsmagic.{i}" not in lang:  # block items use the block lang key
            bad(f"no lang for item {i}")
    for k in ["entity.hogwartsmagic.chocolate_frog", "effect.hogwartsmagic.chocolate_courage",
              "subtitles.hogwartsmagic.cosy.pot_bubble_loop", "gui.hogwartsmagic.card_album.title"]:
        if k not in lang:
            bad(f"missing lang {k}")
    for k in CARD_KEYS:
        for suffix in ("name", "line"):
            if f"card.hogwartsmagic.{k}.{suffix}" not in lang:
                bad(f"missing lang card {k}.{suffix}")
    for k in ["toffee", "peppermint", "honeycomb", "lemon_sherbet", "treacle_fudge", "candy_floss", "marshmallow",
              "buttered_toast", "cabbage", "parsley", "plain_vanilla", "earwax", "soap", "bogey", "fizzing_whizbee",
              "burp", "sardine"]:
        if f"bean.hogwartsmagic.{k}" not in lang:
            bad(f"missing bean line {k}")
    from PIL import Image
    for path in [os.path.join(A, "textures", "item", i + ".png") for i in ITEMS if i not in BLOCKS] + [
            os.path.join(A, "textures", "item", n + ".png") for n in ("wizard_card_silver", "wizard_card_gold")]:
        if not os.path.exists(path):
            bad(f"missing sprite {os.path.basename(path)}")
            continue
        w, h = Image.open(path).size
        if w != h or (w & (w - 1)):
            bad(f"sprite {os.path.basename(path)} is {w}x{h}")
    if not os.path.exists(os.path.join(A, "textures", "mob_effect", "chocolate_courage.png")):
        bad("missing effect icon")
    if not os.path.exists(os.path.join(A, "textures", "entity", "chocolate_frog.png")):
        bad("missing frog texture")
    sounds = load(os.path.join(A, "sounds.json"))
    for key, entry in sounds.items():
        if not key.startswith("cosy."):
            continue
        for s in entry["sounds"]:
            name = s["name"] if isinstance(s, dict) else s
            f = os.path.join(A, "sounds", name.split(":")[1] + ".ogg")
            if not os.path.exists(f):
                bad(f"sound {key}: missing {f}")
        sub = entry.get("subtitle")
        if sub and sub not in lang:
            bad(f"sound {key}: missing subtitle lang {sub}")
    print(f"checked {len(seen)} models, {len(ITEMS)} items, {len(BLOCKS)} blocks, {len(CARD_KEYS)} cards")
    for p in problems:
        print("PROBLEM:", p)
    print("OK" if not problems else f"{len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
