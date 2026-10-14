"""The table of every spell's Hogwarts Assignment: teacher, quote and task lines.

    python build_scripts/generate_assignments.py

Writes data/hogwartsmagic/assignments/<spell>.json and puts each quote and task line into en_us.json
(assignment.hogwartsmagic.<spell>.quote and .<index>). Edit the table below, run this, then run the
tests: AssignmentDataTest checks the size rule by rarity and the other design rules.
"""
import json, os, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "src/main/resources/data/hogwartsmagic/assignments")

def o(type_, text, count=1, target=None, **params):
    d = {"type": type_}
    if target is not None:
        d["target"] = target
    d["count"] = count
    d.update(params)
    return (d, text)

TORCHES = ["minecraft:torch", "minecraft:wall_torch", "minecraft:soul_torch", "minecraft:soul_wall_torch"]
SPIDERS = ["minecraft:spider", "minecraft:cave_spider"]

A = {
# ---------------- COMMON ----------------
"lumos": ("flitwick", False, "A wizard who fears the dark has not yet learned to make light.", [
    o("place", "Light %s dark places with torches, where no sky light reaches", 12, TORCHES, no_sky=True),
    o("stand", "Spend %s seconds deep underground, below Y 0", 60, max_y=0)]),
"accio": ("flitwick", False, "Summoning begins with patience. Let the water teach you to pull.", [
    o("fish", "Fish up %s catches", 5)]),
"alohomora": ("flitwick", False, "Every lock is a question. Go and find some answers.", [
    o("loot_chest", "Open %s chests nobody has opened before (villages, ruins, temples)", 3)]),
"ascendio": ("hooch", False, "Before you rise into the air, learn how it feels to stand high.", [
    o("stand", "Stand above Y 160 for %s seconds", 10, min_y=160)]),
"depulso": ("lupin", False, "Force is useful. Knowing where it sends things is more useful.", [
    o("kill", "Knock %s monsters to their death by a fall", 3, "monster", cause="fall")]),
"diffindo": ("mcgonagall", False, "Precision first. The cut comes second.", [
    o("break", "Cut %s leaf blocks", 32, "#minecraft:leaves"),
    o("break", "Cut %s cobwebs", 4, "minecraft:cobweb")]),
# ---------------- UNCOMMON ----------------
"expelliarmus": ("lupin", False, "Disarm first. A wizard without a wand is no threat to anyone.", [
    o("kill", "Defeat %s monsters that carry a weapon", 5, "monster", victim="armed"),
    o("duel_win", "Win %s duel against a Hogwarts Student or a Death Eater", 1)]),
"protego": ("lupin", False, "A good defence starts with your feet and your shield.", [
    o("block_shield", "Block %s attacks with a shield", 20),
    o("kill", "Defeat %s skeletons", 5, ["minecraft:skeleton", "minecraft:stray", "minecraft:bogged"])]),
"stupefy": ("lupin", False, "You can take a wand away. Now learn to stop the hand that holds it.", [
    o("know_spell", "Know Expelliarmus", 1, "expelliarmus"),
    o("duel_win", "Win %s duels", 2)]),
"petrificus_totalus": ("lupin", False, "Stun them, then bind them. In that order.", [
    o("know_spell", "Know Stupefy", 1, "stupefy"),
    o("cast", "Hit %s creatures with Stupefy", 15, "stupefy", on="living")]),
"wingardium_leviosa": ("flitwick", False, "Swish and flick. And it is Levi-O-sa, not Levio-SA.", [
    o("deliver", "Bring %s feathers", 16, "minecraft:feather"),
    o("cast", "Cast Lumos %s times to practise the wand movement", 20, "lumos")]),
"glacius": ("flitwick", False, "Cold is only slow fire. Learn its weight first.", [
    o("break", "Mine %s blocks of ice", 24, ["minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice"]),
    o("deliver", "Bring %s snowballs", 16, "minecraft:snowball")]),
"incendio": ("flitwick", False, "Fire is a servant. Teach it to wait for you.", [
    o("smelt", "Smelt %s items in a furnace", 32),
    o("place", "Build %s campfires", 3, ["minecraft:campfire", "minecraft:soul_campfire"])]),
"confringo": ("moody", False, "Fire that bursts. Do not practise it indoors.", [
    o("know_spell", "Know Incendio", 1, "incendio"),
    o("kill", "Defeat %s monsters while they burn", 10, "monster", cause="fire")]),
"aqua_eructo": ("pomfrey", False, "Half my patients come in on fire. Learn to put them out.", [
    o("kill", "Defeat %s Blazes or Magma Cubes", 4, ["minecraft:blaze", "minecraft:magma_cube"]),
    o("stand", "Swim for %s seconds", 30, in_water=True)]),
"heal": ("pomfrey", False, "Healing is patience and good ingredients.", [
    o("brew", "Brew %s Potions of Healing", 3, ["minecraft:healing", "minecraft:strong_healing"]),
    o("breed", "Breed %s animals", 4)]),
"revelio": ("moody", False, "Constant vigilance! Things hide. Find them.", [
    o("loot_chest", "Open %s hidden chest: buried treasure or a monster dungeon", 1,
      ["minecraft:chests/buried_treasure", "minecraft:chests/simple_dungeon"]),
    o("break", "Mine %s diamond ore", 1, ["minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"])]),
"reparo": ("flitwick", False, "Anything broken can be mended, if you understand how it was made.", [
    o("craft", "Build %s anvil", 1, "minecraft:anvil"),
    o("repair", "Repair %s items at an anvil", 5)]),
"arresto_momentum": ("hooch", False, "Every flyer falls one day. The good ones land.", [
    o("place", "Stack %s hay bales to land on", 4, "minecraft:hay_block"),
    o("fall", "Survive %s falls of 25 blocks or more (a hay bale helps)", 3, min=25)]),
"alatus": ("hooch", False, "A broom is wood first and magic second.", [
    o("break", "Cut %s Fine Wood logs", 16, "hogwartsmagic:fine_wood_log"),
    o("craft", "Build %s Broom Workbench", 1, "hogwartsmagic:broom_workbench")]),
"colloportus": ("filch", False, "Doors are for keeping students OUT. Learn it, or I tell the Headmaster.", [
    o("place", "Hang %s iron doors", 4, "minecraft:iron_door"),
    o("place", "Wire %s levers or buttons", 4, ["minecraft:lever", "#minecraft:buttons"])]),
"confundo": ("mcgonagall", False, "A muddled mind turns on its friends. Watch it happen first.", [
    o("mob_kills_mob", "Watch %s creatures kill another creature (a skeleton that shoots a zombie counts)", 5),
    o("kill", "Defeat %s witches, who brew the potions that muddle minds", 2, "minecraft:witch")]),
"incarcerous": ("moody", False, "Spiders bind their prey. Study the masters.", [
    o("kill", "Defeat %s spiders", 10, SPIDERS),
    o("deliver", "Bring %s string", 16, "minecraft:string")]),
"levicorpus": ("prince", True, "Lift them by the ankle. Written small, in the margin of an old potions book.", [
    o("cast", "Cast Wingardium Leviosa %s times", 10, "wingardium_leviosa"),
    o("kill", "Defeat %s monsters", 15, "monster")]),
# ---------------- RARE ----------------
"protego_maxima": ("flitwick", False, "One shield protects one wizard. This one protects a village.", [
    o("know_spell", "Know Protego", 1, "protego"),
    o("advancement", "Defend a village from a raid and become its Hero", 1, "minecraft:adventure/hero_of_the_village"),
    o("block_shield", "Block %s attacks with a shield", 30)]),
"cave_inimicum": ("flitwick", False, "On the run, a camp they cannot see is a camp they cannot find.", [
    o("craft", "Make %s Ward", 1, "hogwartsmagic:ward"),
    o("stand", "Spend %s seconds outdoors at night", 600, night=True, sky=True),
    o("kill", "Defeat %s monsters", 20, "monster")]),
"bombarda": ("moody", False, "A big bang for a small target. Control it, or it controls you.", [
    o("kill", "Defeat %s monsters with an explosion", 5, "monster", cause="explosion"),
    o("kill", "Defeat %s Death Eaters", 3, "hogwartsmagic:wizard"),
    o("break", "Mine %s obsidian", 8, "minecraft:obsidian")]),
"piertotum_locomotor": ("mcgonagall", False, "The castle has defenders of stone. Earn the right to command them.", [
    o("know_spell", "Know Reparo", 1, "reparo"),
    o("kill", "Defeat %s Living Statue", 1, "hogwartsmagic:living_statue"),
    o("craft", "Make %s armour stands", 4, "minecraft:armor_stand")]),
"obliviate": ("lockhart", False, "Memory charms are my speciality. Ask anyone. Well, they will not remember.", [
    o("deliver", "Bring %s signed books (your memoirs)", 3, "minecraft:written_book"),
    o("kill", "Defeat %s Troll (and tell everyone about it)", 1, "hogwartsmagic:troll"),
    o("duel_win", "Win %s duels", 3)]),
"sectumsempra": ("prince", True, "For enemies. Written under the potion notes, in the same small hand.", [
    o("know_spell", "Know Levicorpus", 1, "levicorpus"),
    o("brew", "Brew %s different potions", 5, distinct=True),
    o("cast", "Hit %s creatures with Diffindo", 15, "diffindo", on="living")]),
"dolohov_curse": ("death_eater", True, "The Dark Lord has no use for the weak. Break their guardians.", [
    o("kill", "Defeat %s Iron Golems", 3, "minecraft:iron_golem"),
    o("duel_win", "Win %s duels", 2),
    o("kill", "Defeat %s witches who would not serve", 3, "minecraft:witch")]),
"serpensortia": ("death_eater", True, "The serpent answers only those who have walked among spiders.", [
    o("kill", "Defeat %s cave spiders", 10, "minecraft:cave_spider"),
    o("kill", "Defeat %s Acromantulas", 2, "hogwartsmagic:acromantula"),
    o("deliver", "Bring %s Acromantula Venom", 2, "hogwartsmagic:acromantula_venom")]),
# ---------------- EPIC ----------------
"expecto_patronum": ("lupin", False, "Think of your happiest memory. Then go and face them.", [
    o("know_spell", "Know Protego", 1, "protego"),
    o("hurt_by", "Survive %s Dementor attacks", 3, "hogwartsmagic:dementor"),
    o("kill", "Defeat %s Dementors", 3, "hogwartsmagic:dementor"),
    o("eat", "Eat %s cookies afterwards. Chocolate. It really helps.", 5, "minecraft:cookie")]),
"apparition": ("twycross", False, "Destination. Determination. Deliberation.", [
    o("floo", "Travel %s times by the Floo Network", 5),
    o("visit", "Visit a Black Lake ruin", 1, "hogwartsmagic:black_lake_ruin"),
    o("visit", "Visit a Whomping Willow clearing", 1, "hogwartsmagic:willow_clearing"),
    o("visit", "Visit a merfolk village", 1, "hogwartsmagic:merfolk_village")]),
"tenebris_flight": ("death_eater", True, "Become smoke. But first, learn the sky.", [
    o("kill", "Defeat %s Phantoms", 5, "minecraft:phantom"),
    o("stand", "Stand above Y 200 for %s seconds", 30, min_y=200),
    o("kill", "Defeat %s Ghasts", 2, "minecraft:ghast")]),
"morsmordre": ("death_eater", True, "Raise the Mark only when you have earned it.", [
    o("visit", "Find a Death Eater camp", 1, "hogwartsmagic:death_eater_camp"),
    o("deliver", "Bring %s Cursed Galleons", 3, "hogwartsmagic:cursed_galleon"),
    o("kill", "Defeat %s Iron Golems", 5, "minecraft:iron_golem")]),
"fiendfyre": ("death_eater", True, "Cursed fire eats everything. Including the careless.", [
    o("know_spell", "Know Confringo", 1, "confringo"),
    o("kill", "Defeat %s Hungarian Horntail", 1, "hogwartsmagic:hungarian_horntail"),
    o("kill", "Defeat %s monsters while they burn", 25, "monster", cause="fire")]),
"imperio": ("death_eater", True, "Watch the spider dance. Then learn to make it dance.", [
    o("know_spell", "Know Confundo", 1, "confundo"),
    o("kill", "Defeat %s spiders", 20, SPIDERS),
    o("kill", "Defeat %s Acromantula", 1, "hogwartsmagic:acromantula")]),
"crucio": ("death_eater", True, "You need to mean it.", [
    o("know_spell", "Know Imperio", 1, "imperio"),
    o("kill", "Defeat %s lycanthrope", 1, "hogwartsmagic:lycanthrope_villager"),
    o("kill", "Defeat %s Iron Golems", 5, "minecraft:iron_golem")]),
"avada_kedavra": ("death_eater", True, "There is no counter-curse. Prove you can face what has no cure.", [
    o("know_spell", "Know Crucio", 1, "crucio"),
    o("visit", "Enter the Chamber of Secrets", 1, "hogwartsmagic:chamber_hall"),
    o("kill", "Defeat %s Basilisk", 1, "hogwartsmagic:basilisk")]),
}

os.makedirs(OUT, exist_ok=True)
lang = {}
for spell, (teacher, dark, quote, objectives) in A.items():
    doc = {"teacher": teacher}
    if dark:
        doc["dark"] = True
    doc["objectives"] = [d for d, _ in objectives]
    with open(os.path.join(OUT, spell + ".json"), "w", encoding="utf-8") as f:
        json.dump(doc, f, indent=2)
        f.write("\n")
    lang["assignment.hogwartsmagic.%s.quote" % spell] = quote
    for i, (_, text) in enumerate(objectives):
        lang["assignment.hogwartsmagic.%s.%d" % (spell, i)] = text

LANG = os.path.join(ROOT, "src/main/resources/assets/hogwartsmagic/lang/en_us.json")
text = open(LANG, encoding="utf-8").read()
kept = [line for line in text.rstrip().rstrip("}").rstrip().split("\n")
        if not any(line.strip().startswith(json.dumps(k) + ":") for k in lang)]
body = "\n".join(kept).rstrip().rstrip(",")
added = ",\n".join("  %s: %s" % (json.dumps(k), json.dumps(v, ensure_ascii=False)) for k, v in lang.items())
result = body + ",\n" + added + "\n}\n"
json.loads(result)
open(LANG, "w", encoding="utf-8").write(result)
print("wrote", len(A), "assignments and", len(lang), "lang lines")
