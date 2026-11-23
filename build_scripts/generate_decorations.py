"""Generates every asset and data file for the Hogwarts decorations (ModDecorations.java).

Writes textures, block and item models, blockstates, loot tables, recipes, mineable tags and
en_us.json names. Run from the repository root:  python build_scripts/generate_decorations.py

Model convention: every directional model faces NORTH (front toward -Z). Wall pieces hang on the
south face of their cell. Blockstates turn them with y = 0 / 90 / 180 / 270 for N / E / S / W, the
same turn DecorShapes.rotate uses for hit boxes.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import decor_textures as T  # noqa: E402

MOD = "hogwartsmagic"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets", MOD)
DATA = os.path.join(ROOT, "src/main/resources/data", MOD)
MC_DATA = os.path.join(ROOT, "src/main/resources/data/minecraft")
HOUSES = ["gryffindor", "ravenclaw", "hufflepuff", "slytherin"]

written = []


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")
    written.append(os.path.relpath(path, ROOT))


def save_png(img, rel):
    path = os.path.join(ASSETS, "textures", rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    written.append(os.path.relpath(path, ROOT))


# ---- textures ------------------------------------------------------------------------------------

def D(name):
    """Resource id of a decoration texture."""
    return f"{MOD}:block/decor/{name}"


V = lambda name: f"minecraft:block/{name}"  # noqa: E731  vanilla texture

DARK_WOOD = V("dark_oak_planks")
DARK_LOG = V("stripped_dark_oak_log")
GOLD = V("gold_block")


def make_textures():
    save_png(T.candle_wax(), "block/decor/candle_wax")
    save_png(T.brass(), "block/decor/brass")
    save_png(T.iron(), "block/decor/iron")
    save_png(T.glass(), "block/decor/hourglass_glass")
    save_png(T.velvet(T.hx("8a1a22"), "velvet"), "block/decor/red_velvet")
    save_png(T.parchment(), "block/decor/parchment")
    save_png(T.parchment("written", written=True), "block/decor/parchment_written")
    save_png(T.scroll_end(), "block/decor/scroll_end")
    save_png(T.quill(), "block/decor/quill")
    save_png(T.ink(), "block/decor/ink")
    save_png(T.straw(), "block/decor/broom_straw")
    save_png(T.cork(), "block/decor/cork")
    for name, col in (("red", "c0202a"), ("green", "2aa84a"), ("blue", "2a5ad0"), ("purple", "8a3ac0")):
        save_png(T.potion(T.hx(col), name), f"block/decor/potion_{name}")
    save_png(T.lens(), "block/decor/telescope_lens")
    save_png(T.chess_board(), "block/decor/chess_board")
    save_png(T.owl_bars(), "block/decor/owl_cage_bars")
    save_png(T.owl_feathers(), "block/decor/owl_feathers")
    save_png(T.owl_face(), "block/decor/owl_face")
    save_png(T.trunk_front(), "block/decor/school_trunk_front")
    save_png(T.trunk_top(), "block/decor/school_trunk_top")
    save_png(T.open_book(), "block/decor/open_book")
    for h in HOUSES:
        top, bottom = T.banner_halves(h)
        save_png(top, f"block/decor/{h}_banner_top")
        save_png(bottom, f"block/decor/{h}_banner_bottom")
        save_png(T.banner_item(h), f"item/{h}_hanging_banner")
        save_png(T.shield(h), f"block/decor/{h}_crest")
        save_png(T.gem_pile(h), f"block/decor/{h}_gems")


# ---- model helpers -------------------------------------------------------------------------------

FACES = ("north", "south", "east", "west", "up", "down")


def auto_uv(face, frm, to):
    """The UV Minecraft would derive for a face from the element position."""
    x1, y1, z1 = frm
    x2, y2, z2 = to
    return {
        "down": [x1, 16 - z2, x2, 16 - z1],
        "up": [x1, z1, x2, z2],
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "south": [x1, 16 - y2, x2, 16 - y1],
        "west": [z1, 16 - y2, z2, 16 - y1],
        "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
    }[face]


def _fit(a, b):
    """Move a UV span that leaves 0..16 back inside it, keeping its length (at most 16)."""
    if 0 <= a and b <= 16:
        return a, b
    length = min(b - a, 16)
    start = a % 16
    if start + length > 16:
        start = 16 - length
    return start, start + length


def el(frm, to, tex, faces=FACES, uv=None, rot=None, tex_for=None, shade=True):
    """One model element. tex is the default texture key, tex_for overrides per face, uv sets
    explicit UVs per face. Otherwise the UV is the one Minecraft would derive, except that a face
    of an element reaching outside the block gets its UV moved back inside the texture: a derived
    UV out there samples the NEIGHBOURING sprite in the atlas (white speckle on a red chair)."""
    out = {"from": list(frm), "to": list(to), "faces": {}}
    for f in faces:
        face = {"texture": "#" + ((tex_for or {}).get(f, tex))}
        if uv and f in uv:
            face["uv"] = uv[f]
        else:
            u1, v1, u2, v2 = auto_uv(f, frm, to)
            fu, fv = _fit(u1, u2), _fit(v1, v2)
            if (fu, fv) != ((u1, u2), (v1, v2)):
                face["uv"] = [fu[0], fv[0], fu[1], fv[1]]
        out["faces"][f] = face
    if rot:
        axis, angle, origin = rot
        out["rotation"] = {"origin": list(origin), "axis": axis, "angle": angle}
    if not shade:
        out["shade"] = False
    return out


def model(name, textures, elements, render_type=None, parent="block/block", display=None, ao=True):
    m = {"parent": parent}
    if render_type:
        m["render_type"] = f"minecraft:{render_type}"
    if not ao:
        m["ambientocclusion"] = False
    m["textures"] = textures
    m["elements"] = elements
    if display:
        m["display"] = display
    write_json(os.path.join(ASSETS, "models/block", name + ".json"), m)


def item_from_block(name, block_model=None, display=None):
    m = {"parent": f"{MOD}:block/{block_model or name}"}
    if display:
        m["display"] = display
    write_json(os.path.join(ASSETS, "models/item", name + ".json"), m)


def item_flat(name):
    write_json(os.path.join(ASSETS, "models/item", name + ".json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{name}"}})


ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def blockstate_facing(name, model_name=None):
    m = f"{MOD}:block/{model_name or name}"
    variants = {}
    for f, y in ROT.items():
        v = {"model": m}
        if y:
            v["y"] = y
        variants[f"facing={f}"] = v
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})


def blockstate_single(name):
    write_json(os.path.join(ASSETS, "blockstates", name + ".json"),
               {"variants": {"": {"model": f"{MOD}:block/{name}"}}})


def small_gui(scale):
    return {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [scale, scale, scale]}}


# ---- models --------------------------------------------------------------------------------------

def floating_candle():
    wick = {f: [14, 0, 15, 1] for f in FACES}
    flame = {f: [12, 0, 14, 3] for f in FACES}
    els = []
    for (x, z, y0, y1) in ((4, 5, 4, 13), (9, 4, 2, 11), (7, 10, 1, 10)):
        els.append(el((x, y0, z), (x + 2, y1, z + 2), "wax"))
        els.append(el((x + 0.75, y1, z + 0.75), (x + 1.25, y1 + 1, z + 1.25), "wax", uv=wick))
        # A small steady flame, so the candle reads as lit between flame particles.
        els.append(el((x + 0.5, y1 + 1, z + 1), (x + 1.5, y1 + 2.5, z + 1), "wax",
                      faces=("north", "south"), uv=flame, shade=False))
        els.append(el((x + 1, y1 + 1, z + 0.5), (x + 1, y1 + 2.5, z + 1.5), "wax",
                      faces=("east", "west"), uv=flame, shade=False))
    model("floating_candle", {"wax": D("candle_wax"), "particle": D("candle_wax")}, els, ao=False,
          render_type="cutout")
    blockstate_single("floating_candle")
    item_from_block("floating_candle")


def hanging_banners():
    for h in HOUSES:
        side_uv = {"east": [2, 0, 3, 16], "west": [2, 0, 3, 16]}
        upper = [
            el((0.5, 14, 13), (15.5, 15.5, 14.5), "rod"),
            el((0, 13.5, 12.5), (1.5, 16, 15), "rod"),
            el((14.5, 13.5, 12.5), (16, 16, 15), "rod"),
            el((2, 0, 14.5), (14, 14, 15.5), "cloth", faces=("north", "south", "east", "west", "up"),
               uv={"east": [2, 2, 3, 16], "west": [2, 2, 3, 16]}),
        ]
        lower = [
            el((2, 0, 14.5), (14, 16, 15.5), "cloth", faces=("north", "south", "east", "west", "down"),
               uv=side_uv),
        ]
        model(f"{h}_hanging_banner_upper",
              {"cloth": D(f"{h}_banner_top"), "rod": D("brass"), "particle": D(f"{h}_banner_top")},
              upper, render_type="cutout")
        model(f"{h}_hanging_banner_lower",
              {"cloth": D(f"{h}_banner_bottom"), "particle": D(f"{h}_banner_bottom")},
              lower, render_type="cutout")
        variants = {}
        for f, y in ROT.items():
            for half in ("upper", "lower"):
                v = {"model": f"{MOD}:block/{h}_hanging_banner_{half}"}
                if y:
                    v["y"] = y
                variants[f"facing={f},half={half}"] = v
        write_json(os.path.join(ASSETS, "blockstates", f"{h}_hanging_banner.json"), {"variants": variants})
        item_flat(f"{h}_hanging_banner")


def hourglasses():
    for h in HOUSES:
        els = [
            el((2, 0, 2), (14, 2, 14), "wood"),
            el((2, 14, 2), (14, 16, 14), "wood"),
        ]
        for x in (2.5, 12.5):
            for z in (2.5, 12.5):
                els.append(el((x, 2, z), (x + 1, 14, z + 1), "brass"))
        els += [
            el((4.5, 2, 4.5), (11.5, 5.5, 11.5), "gems"),
            el((5.5, 12.5, 5.5), (10.5, 14, 10.5), "gems"),
            el((7.5, 5.5, 7.5), (8.5, 12.5, 8.5), "gems"),
            el((4, 2, 4), (12, 7.5, 12), "glass", faces=("north", "south", "east", "west", "up")),
            el((6.5, 7.5, 6.5), (9.5, 8.5, 9.5), "glass", faces=("north", "south", "east", "west")),
            el((4, 8.5, 4), (12, 14, 12), "glass", faces=("north", "south", "east", "west", "down")),
        ]
        name = f"{h}_points_hourglass"
        model(name, {"wood": DARK_WOOD, "brass": D("brass"), "gems": D(f"{h}_gems"),
                     "glass": D("hourglass_glass"), "particle": D(f"{h}_gems")},
              els, render_type="translucent")
        blockstate_facing(name)
        item_from_block(name)


def school_trunk():
    els = [
        el((1, 0, 3), (15, 11, 13), "front", tex_for={"up": "top", "down": "top"}),
        el((7, 6, 2.5), (9, 9, 3), "brass"),
    ]
    model("school_trunk", {"front": D("school_trunk_front"), "top": D("school_trunk_top"),
                           "brass": D("brass"), "particle": D("school_trunk_top")}, els)
    blockstate_facing("school_trunk")
    item_from_block("school_trunk")


def owl_cage():
    els = [
        el((3, 0, 3), (13, 1.5, 13), "brass"),
        el((3.5, 1.5, 3.5), (12.5, 11, 12.5), "bars", faces=("north", "south", "east", "west")),
        el((3.5, 11, 3.5), (12.5, 12, 12.5), "brass"),
        el((5, 12, 5), (11, 13, 11), "brass"),
        el((7.5, 13, 7.5), (8.5, 14.5, 8.5), "brass"),
        el((6, 1.5, 6.5), (10, 6, 9.5), "feathers"),
        el((6.25, 6, 6.75), (9.75, 8.5, 9.25), "feathers", tex_for={"north": "face"}),
    ]
    # Inner faces of the bars, so the far side of the cage shows through the near side.
    inner = [
        el((3.5, 1.5, 12.4), (12.5, 11, 12.5), "bars", faces=("north",)),
        el((3.5, 1.5, 3.5), (12.5, 11, 3.6), "bars", faces=("south",)),
        el((3.5, 1.5, 3.5), (3.6, 11, 12.5), "bars", faces=("east",)),
        el((12.4, 1.5, 3.5), (12.5, 11, 12.5), "bars", faces=("west",)),
    ]
    model("owl_cage", {"brass": D("brass"), "bars": D("owl_cage_bars"), "feathers": D("owl_feathers"),
                       "face": D("owl_face"), "particle": D("brass")}, els + inner, render_type="cutout")
    blockstate_facing("owl_cage")
    item_from_block("owl_cage")


def chess_board():
    els = [el((0, 0, 0), (16, 1, 16), "wood", tex_for={"up": "board"})]

    def piece(i, j, height, tex):
        x, z = 2 * i, 2 * j
        out = [el((x + 0.5, 1, z + 0.5), (x + 1.5, 1 + height, z + 1.5), tex)]
        if height >= 2.5:  # a crown on the major pieces
            out.append(el((x + 0.25, 1 + height - 0.75, z + 0.25), (x + 1.75, 1 + height - 0.25, z + 1.75), tex))
        return out

    for i in (1, 2, 5, 6):
        els += piece(i, 6, 1.5, "white")
    for i, hgt in ((0, 2.5), (3, 3), (4, 3.5), (6, 2.5)):
        els += piece(i, 7, hgt, "white")
    for i in (0, 2, 4, 7):
        els += piece(i, 1, 1.5, "black")
    for i, hgt in ((1, 2.5), (3, 3), (4, 3.5), (7, 2.5)):
        els += piece(i, 0, hgt, "black")
    els += piece(3, 4, 2.5, "white") + piece(4, 3, 1.5, "black")
    model("wizard_chess_board", {"wood": DARK_WOOD, "board": D("chess_board"),
                                 "white": V("quartz_block_bottom"), "black": V("polished_blackstone"),
                                 "particle": DARK_WOOD}, els)
    blockstate_facing("wizard_chess_board")
    item_from_block("wizard_chess_board")


def crest_plaques():
    for h in HOUSES:
        clear = [0, 0, 1, 1]
        els = [
            el((1, 0, 15), (15, 16, 16), "wood"),
            el((2, 1, 14), (14, 15, 15), "shield", faces=("north", "east", "west", "up", "down"),
               uv={"east": [2, 2, 3, 8], "west": [2, 2, 3, 8], "up": [3, 1, 13, 2], "down": clear}),
        ]
        name = f"{h}_crest_plaque"
        model(name, {"wood": DARK_WOOD, "shield": D(f"{h}_crest"), "particle": D(f"{h}_crest")},
              els, render_type="cutout")
        blockstate_facing(name)
        item_from_block(name)


def potion_shelf():
    els = [
        el((0, 3, 10), (16, 4, 16), "wood"),
        el((1.5, 0, 14), (2.5, 3, 16), "iron"),
        el((13.5, 0, 14), (14.5, 3, 16), "iron"),
        # Red, squat
        el((1.5, 4, 11.5), (4.5, 8, 14.5), "red"),
        el((2.5, 8, 12.5), (3.5, 9.5, 13.5), "red"),
        el((2.25, 9.5, 12.25), (3.75, 10.5, 13.75), "cork"),
        # Green, tall
        el((5.5, 4, 12), (7.5, 10, 14), "green"),
        el((6, 10, 12.5), (7, 11, 13.5), "cork"),
        # Blue, round
        el((8.5, 4, 11), (12, 7.5, 14.5), "blue"),
        el((9.75, 7.5, 12.25), (10.75, 9, 13.25), "blue"),
        el((9.5, 9, 12), (11, 10, 13.5), "cork"),
        # Purple, small
        el((12.75, 4, 12.5), (14.75, 7, 14.5), "purple"),
        el((13.25, 7, 13), (14.25, 8, 14), "cork"),
    ]
    model("potion_shelf", {"wood": DARK_WOOD, "iron": D("iron"), "cork": D("cork"),
                           "red": D("potion_red"), "green": D("potion_green"), "blue": D("potion_blue"),
                           "purple": D("potion_purple"), "particle": DARK_WOOD}, els)
    blockstate_facing("potion_shelf")
    item_from_block("potion_shelf")



def brass_telescope():
    tube = ("x", -22.5, (8, 11.25, 8))  # negative raises the lens end (north)
    els = [
        el((7, 8, 7), (9, 10, 9), "brass"),
        el((7.5, 0, 7.5), (8.5, 9, 8.5), "leg", rot=("x", 22.5, (8, 9, 8))),
        el((7.5, 0, 7.5), (8.5, 9, 8.5), "leg", rot=("x", -22.5, (8, 9, 8))),
        el((7.5, 0, 7.5), (8.5, 9, 8.5), "leg", rot=("z", 22.5, (8, 9, 8))),
        el((6.75, 10, 3), (9.25, 12.5, 13), "brass", rot=tube),
        el((6.25, 9.5, 1), (9.75, 13, 3), "brass", tex_for={"north": "lens"}, rot=tube),
        el((7.25, 10.5, 13), (8.75, 12, 15.5), "brass", rot=tube),
    ]
    model("brass_telescope", {"brass": D("brass"), "leg": DARK_LOG, "lens": D("telescope_lens"),
                              "particle": D("brass")}, els)
    blockstate_facing("brass_telescope")
    item_from_block("brass_telescope")


def scroll_stack():
    def scroll(x1, x2, y, z):
        return el((x1, y, z), (x2, y + 2, z + 2), "paper", tex_for={"east": "end", "west": "end"})

    els = [
        scroll(2, 14, 0, 3),
        scroll(3, 13, 0, 5.25),
        scroll(4, 12, 2, 4.1),
        el((2, 0, 9), (11, 0.25, 15), "written", tex_for={"north": "paper", "south": "paper",
                                                          "east": "paper", "west": "paper", "down": "paper"}),
        el((12, 0, 9.5), (14.5, 2, 12), "ink"),
        el((12.5, 2, 10.75), (14, 9, 11.25), "quill", rot=("z", 22.5, (13.25, 2, 11))),
    ]
    model("scroll_stack", {"paper": D("parchment"), "end": D("scroll_end"), "written": D("parchment_written"),
                           "ink": D("ink"), "quill": D("quill"), "particle": D("parchment")}, els)
    blockstate_facing("scroll_stack")
    item_from_block("scroll_stack")


def quidditch():
    model("quidditch_hoop_pole", {"gold": GOLD, "particle": GOLD},
          [el((6.5, 0, 6.5), (9.5, 16, 9.5), "gold")])
    blockstate_facing("quidditch_hoop_pole")
    item_from_block("quidditch_hoop_pole")

    z = (7, 9)
    hoop = [
        el((6.5, 0, 6.5), (9.5, 3, 9.5), "gold"),
        el((4, 2, z[0]), (12, 4, z[1]), "gold"),
        el((4, 22, z[0]), (12, 24, z[1]), "gold"),
        el((-4, 9, z[0]), (-2, 17, z[1]), "gold"),
        el((18, 9, z[0]), (20, 17, z[1]), "gold"),
        # Diagonals join the flat sides into an octagon.
        el((10.5, 5.5, z[0]), (20.5, 7.5, z[1]), "gold", rot=("z", 45, (15.5, 6.5, 8))),
        el((-4.5, 5.5, z[0]), (5.5, 7.5, z[1]), "gold", rot=("z", -45, (0.5, 6.5, 8))),
        el((10.5, 18.5, z[0]), (20.5, 20.5, z[1]), "gold", rot=("z", -45, (15.5, 19.5, 8))),
        el((-4.5, 18.5, z[0]), (5.5, 20.5, z[1]), "gold", rot=("z", 45, (0.5, 19.5, 8))),
    ]
    model("quidditch_hoop", {"gold": GOLD, "particle": GOLD}, hoop)
    blockstate_facing("quidditch_hoop")
    item_from_block("quidditch_hoop", display={
        "gui": {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.38, 0.38, 0.38]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.2, 0.2, 0.2]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.4, 0.4, 0.4]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
    })

    rack = [
        el((1, 11, 15), (15, 14, 16), "wood"),
        el((3, 12, 12), (4, 13, 15), "wood"),
        el((12, 12, 12), (13, 13, 15), "wood"),
    ]
    for hx in (3.25, 11.75):
        rack.append(el((hx, 4, 12.75), (hx + 1, 15.5, 13.75), "handle"))
        rack.append(el((hx - 1.75, 0, 11.75), (hx + 2.75, 6.5, 14.75), "straw"))
        rack.append(el((hx - 1, 6.5, 12.25), (hx + 2, 8, 14.25), "straw"))
    model("broom_rack", {"wood": DARK_WOOD, "handle": V("stripped_spruce_log"), "straw": D("broom_straw"),
                         "particle": DARK_WOOD}, rack)
    blockstate_facing("broom_rack")
    item_from_block("broom_rack")

    cup = [
        el((4, 0, 5), (12, 1.5, 11), "wood"),
        el((5, 1.5, 6), (11, 3, 10), "gold"),
        el((7, 3, 7), (9, 6, 9), "gold"),
        el((5, 6, 5), (11, 8, 11), "gold"),
        el((4, 8, 4), (12, 12, 12), "gold", tex_for={"up": "inside"}),
    ]
    for side in (-1, 1):
        x0 = 2 if side < 0 else 12
        cup.append(el((x0 + (0.5 if side < 0 else 0), 10, 7.25), (x0 + (2 if side < 0 else 1.5), 11, 8.75), "gold"))
        cup.append(el((2 if side < 0 else 13, 7.5, 7.25), (3 if side < 0 else 14, 10.5, 8.75), "gold"))
        cup.append(el((x0 + (0.5 if side < 0 else 0), 7, 7.25), (x0 + (2 if side < 0 else 1.5), 8, 8.75), "gold"))
    model("quidditch_cup", {"gold": GOLD, "wood": DARK_WOOD, "inside": V("raw_gold_block"), "particle": GOLD}, cup)
    blockstate_facing("quidditch_cup")
    item_from_block("quidditch_cup")


def furniture():
    # Table: a multipart top plus one leg per corner that has no neighbour on either side.
    model("wizard_table_top", {"wood": DARK_WOOD, "particle": DARK_WOOD},
          [el((0, 13, 0), (16, 16, 16), "wood")])
    legs = {"nw": (1, 1), "ne": (12, 1), "se": (12, 12), "sw": (1, 12)}
    for k, (x, z) in legs.items():
        model(f"wizard_table_leg_{k}", {"leg": DARK_LOG, "particle": DARK_LOG},
              [el((x, 0, z), (x + 3, 13, z + 3), "leg")])
    model("wizard_table_inventory", {"wood": DARK_WOOD, "leg": DARK_LOG, "particle": DARK_WOOD},
          [el((0, 13, 0), (16, 16, 16), "wood")] +
          [el((x, 0, z), (x + 3, 13, z + 3), "leg") for (x, z) in legs.values()])
    cond = {"nw": {"north": "false", "west": "false"}, "ne": {"north": "false", "east": "false"},
            "se": {"south": "false", "east": "false"}, "sw": {"south": "false", "west": "false"}}
    multipart = [{"apply": {"model": f"{MOD}:block/wizard_table_top"}}]
    for k, c in cond.items():
        multipart.append({"when": c, "apply": {"model": f"{MOD}:block/wizard_table_leg_{k}"}})
    write_json(os.path.join(ASSETS, "blockstates", "wizard_table.json"), {"multipart": multipart})
    item_from_block("wizard_table", "wizard_table_inventory")

    tall_gui = small_gui(0.5)
    chair = []
    for x in (2.5, 11.5):
        for z in (2.5, 11.5):
            chair.append(el((x, 0, z), (x + 2, 7, z + 2), "leg"))
    chair += [
        el((2, 7, 2), (14, 8.5, 14), "wood"),
        el((3, 8.5, 3), (13, 9.5, 12), "velvet"),
        el((2.5, 8.5, 12), (4, 22, 13.5), "leg"),
        el((12, 8.5, 12), (13.5, 22, 13.5), "leg"),
        el((4, 11, 12.5), (12, 20, 13), "wood"),
        el((2, 20, 11.75), (14, 22.5, 13.75), "wood"),
    ]
    model("wizard_chair", {"wood": DARK_WOOD, "leg": DARK_LOG, "velvet": D("red_velvet"),
                           "particle": DARK_WOOD}, chair)
    blockstate_facing("wizard_chair")
    item_from_block("wizard_chair", display=tall_gui)

    bench = [
        el((0, 6, 3), (16, 8, 13), "wood"),
        el((1, 0, 4), (3, 6, 12), "leg"),
        el((13, 0, 4), (15, 6, 12), "leg"),
        el((3, 2, 7), (13, 3.5, 9), "leg"),
    ]
    model("great_hall_bench", {"wood": DARK_WOOD, "leg": DARK_LOG, "particle": DARK_WOOD}, bench)
    blockstate_facing("great_hall_bench")
    item_from_block("great_hall_bench")

    arm = []
    for x in (1.5, 12.5):
        for z in (1.5, 12.5):
            arm.append(el((x, 0, z), (x + 2, 2, z + 2), "leg"))
    arm += [
        el((1, 2, 1), (15, 7, 15), "velvet"),
        el((3, 7, 1.5), (13, 9, 12.5), "velvet"),
        el((1, 7, 1), (3, 12, 15), "velvet"),
        el((13, 7, 1), (15, 12, 15), "velvet"),
        el((1, 7, 12.5), (15, 22, 15), "velvet"),
        el((0.5, 12, 0.5), (3.5, 13, 15), "velvet"),
        el((12.5, 12, 0.5), (15.5, 13, 15), "velvet"),
    ]
    model("common_room_armchair", {"velvet": D("red_velvet"), "leg": DARK_LOG, "particle": D("red_velvet")}, arm)
    blockstate_facing("common_room_armchair")
    item_from_block("common_room_armchair", display=tall_gui)

    desk = [
        el((0, 13, 1), (16, 15, 15), "wood"),
        el((1, 0, 2), (5, 13, 14), "wood"),
        el((13, 0, 2), (15, 13, 4), "leg"),
        el((13, 0, 12), (15, 13, 14), "leg"),
        el((5, 6, 13), (13, 13, 14), "wood"),
        el((1, 6.5, 1.75), (5, 7, 2), "leg"),
        el((2.75, 9, 1.5), (3.25, 9.5, 2), "brass"),
        el((2.75, 3.5, 1.5), (3.25, 4, 2), "brass"),
        el((2, 15, 3), (8, 15.5, 8), "book", tex_for={"north": "cover", "south": "cover", "east": "cover",
                                                      "west": "cover", "down": "cover"}),
        el((10, 15, 4), (12, 17, 6), "ink"),
        el((10.25, 17, 4.75), (11.75, 22, 5.25), "quill", rot=("z", -22.5, (11, 17, 5))),
        el((9, 15, 8), (14, 15.1, 13), "written", tex_for={"north": "paper", "south": "paper", "east": "paper",
                                                           "west": "paper", "down": "paper"}),
    ]
    model("writing_desk", {"wood": DARK_WOOD, "leg": DARK_LOG, "brass": D("brass"), "book": D("open_book"),
                           "cover": V("red_wool"), "ink": D("ink"), "quill": D("quill"),
                           "written": D("parchment_written"), "paper": D("parchment"), "particle": DARK_WOOD},
          desk)
    blockstate_facing("writing_desk")
    item_from_block("writing_desk")


def moving_portrait_fix():
    """The portrait was a full cube with a thin hit box. Make it a thin frame that turns with FACING."""
    full = [0, 0, 16, 16]
    edge = [0, 0, 1, 16]
    model("moving_portrait", {"art": f"{MOD}:block/moving_portrait", "particle": f"{MOD}:block/moving_portrait"},
          [el((1, 1, 14), (15, 15, 16), "art",
              uv={"north": full, "south": full, "east": edge, "west": edge, "up": [0, 0, 16, 1], "down": [0, 15, 16, 16]})])
    blockstate_facing("moving_portrait")


# ---- data ----------------------------------------------------------------------------------------

def loot(name, lower_half_only=False):
    entry = {"type": "minecraft:item", "name": f"{MOD}:{name}"}
    pool = {"rolls": 1, "entries": [entry], "conditions": [{"condition": "minecraft:survives_explosion"}]}
    if lower_half_only:
        pool["conditions"].append({"condition": "minecraft:block_state_property", "block": f"{MOD}:{name}",
                                   "properties": {"half": "lower"}})
    write_json(os.path.join(DATA, "loot_table/blocks", name + ".json"),
               {"type": "minecraft:block", "pools": [pool], "random_sequence": f"{MOD}:blocks/{name}"})


def shaped(name, pattern, key, count=1):
    write_json(os.path.join(DATA, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "building",
        "pattern": pattern, "key": {k: {"item": v} for k, v in key.items()},
        "result": {"id": f"{MOD}:{name}", "count": count}})


def shapeless(name, items, count=1):
    write_json(os.path.join(DATA, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shapeless", "category": "building",
        "ingredients": [{"item": i} for i in items],
        "result": {"id": f"{MOD}:{name}", "count": count}})


GEM_ITEM = {"gryffindor": "minecraft:redstone", "ravenclaw": "minecraft:lapis_lazuli",
            "hufflepuff": "minecraft:glowstone_dust", "slytherin": "minecraft:emerald"}

NAMES = {
    "floating_candle": "Floating Candles",
    "school_trunk": "School Trunk",
    "owl_cage": "Owl Cage",
    "wizard_chess_board": "Wizard Chess Board",
    "potion_shelf": "Potion Shelf",
    "brass_telescope": "Brass Telescope",
    "scroll_stack": "Scrolls and Quill",
    "quidditch_hoop_pole": "Quidditch Hoop Pole",
    "quidditch_hoop": "Quidditch Hoop",
    "broom_rack": "Broom Rack",
    "quidditch_cup": "Quidditch Cup",
    "wizard_table": "Wizard Table",
    "wizard_chair": "Wizard Chair",
    "great_hall_bench": "Great Hall Bench",
    "common_room_armchair": "Common Room Armchair",
    "writing_desk": "Writing Desk",
}
for _h in HOUSES:
    NAMES[f"{_h}_hanging_banner"] = f"{_h.capitalize()} Hanging Banner"
    NAMES[f"{_h}_points_hourglass"] = f"{_h.capitalize()} House Points Hourglass"
    NAMES[f"{_h}_crest_plaque"] = f"{_h.capitalize()} Crest Plaque"

AXE = ["school_trunk", "wizard_chess_board", "potion_shelf", "broom_rack", "wizard_table", "wizard_chair",
       "great_hall_bench", "common_room_armchair", "writing_desk"] + [f"{h}_crest_plaque" for h in HOUSES]
PICKAXE = ["owl_cage", "brass_telescope", "quidditch_hoop_pole", "quidditch_hoop", "quidditch_cup"]


def data():
    for name in NAMES:
        loot(name, lower_half_only=name.endswith("_hanging_banner"))

    DO = "minecraft:dark_oak_planks"
    DS = "minecraft:dark_oak_slab"
    shapeless("floating_candle", ["minecraft:candle"] * 3 + [f"{MOD}:arcane_dust"])
    for h in HOUSES:
        shaped(f"{h}_hanging_banner", ["NSN", " B ", " B "],
               {"N": "minecraft:gold_nugget", "S": "minecraft:stick", "B": f"{MOD}:{h}_banner_block"})
        shaped(f"{h}_points_hourglass", ["PPP", "GXG", "PPP"],
               {"P": DS, "G": "minecraft:glass", "X": GEM_ITEM[h]})
        shaped(f"{h}_crest_plaque", [" N ", "PBP"],
               {"N": "minecraft:gold_nugget", "P": DO, "B": f"{MOD}:{h}_banner_block"}, 2)
    shaped("school_trunk", ["LIL", "LCL", "LLL"],
           {"L": "minecraft:leather", "I": "minecraft:iron_ingot", "C": "minecraft:chest"})
    shaped("owl_cage", ["NNN", "N N", "GGG"], {"N": "minecraft:gold_nugget", "G": "minecraft:gold_ingot"})
    shaped("wizard_chess_board", ["QBQ", "PPP"],
           {"Q": "minecraft:quartz", "B": "minecraft:blackstone", "P": DO})
    shaped("potion_shelf", ["BBB", "SSS"], {"B": "minecraft:glass_bottle", "S": DS})
    shaped("brass_telescope", [" Y ", " C ", "S S"],
           {"Y": "minecraft:spyglass", "C": "minecraft:copper_ingot", "S": "minecraft:stick"})
    shapeless("scroll_stack", ["minecraft:paper"] * 3 + ["minecraft:feather", "minecraft:ink_sac"])
    shaped("quidditch_hoop_pole", ["N", "S", "N"], {"N": "minecraft:gold_nugget", "S": "minecraft:stick"}, 2)
    shaped("quidditch_hoop", [" G ", "G G", " G "], {"G": "minecraft:gold_ingot"})
    shaped("broom_rack", ["SSS", "W W"], {"S": DS, "W": "minecraft:wheat"})
    shaped("quidditch_cup", ["GXG", " G ", "SSS"],
           {"G": "minecraft:gold_ingot", "X": f"{MOD}:golden_snitch", "S": DS})
    shaped("wizard_table", ["PPP", "S S", "S S"], {"P": DO, "S": "minecraft:stick"}, 2)
    shaped("wizard_chair", ["P  ", "PWP", "S S"], {"P": DO, "W": "minecraft:red_wool", "S": "minecraft:stick"}, 2)
    shaped("great_hall_bench", ["SSS", "P P"], {"S": DS, "P": DO}, 2)
    shaped("common_room_armchair", ["W  ", "WWW", "P P"], {"W": "minecraft:red_wool", "P": DO})
    shaped("writing_desk", ["FBI", "PPP", "P P"],
           {"F": "minecraft:feather", "B": "minecraft:book", "I": "minecraft:ink_sac", "P": DO})

    for tool, ids in (("axe", AXE), ("pickaxe", PICKAXE)):
        path = os.path.join(MC_DATA, "tags/block/mineable", tool + ".json")
        with open(path, encoding="utf-8") as f:
            tag = json.load(f)
        for i in ids:
            rid = f"{MOD}:{i}"
            if rid not in tag["values"]:
                tag["values"].append(rid)
        write_json(path, tag)

    lang_path = os.path.join(ASSETS, "lang/en_us.json")
    with open(lang_path, encoding="utf-8") as f:
        lang = json.load(f)
    for k, v in NAMES.items():
        lang[f"block.{MOD}.{k}"] = v
    lang[f"container.{MOD}.school_trunk"] = "School Trunk"
    lang[f"entity.{MOD}.seat"] = "Seat"
    with open(lang_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    written.append(os.path.relpath(lang_path, ROOT))


# ---- remakes of the older decorations ------------------------------------------------------------

ORANGE_FLAME = ["fff6c0", "ffd040", "ff9a1a", "e05a10", "a02a08"]
GREEN_FLAME = ["f0fff0", "a0ff90", "40e050", "18a838", "0a6a20"]


def save_animated(img, rel, frametime=2, meta=None):
    save_png(img, rel)
    path = os.path.join(ASSETS, "textures", rel + ".png.mcmeta")
    write_json(path, meta or {"animation": {"frametime": frametime}})


def remake_textures():
    save_animated(T.flame_frames(ORANGE_FLAME, "hearth"), "block/decor/hearth_flame")
    save_animated(T.flame_frames(GREEN_FLAME, "floo"), "block/decor/floo_flame")
    for c in ("red", "blue", "green"):
        save_png(T.book_cover(c), f"block/decor/book_cover_{c}")
        save_png(T.book_pages(c), f"block/decor/book_pages_{c}")
        save_png(T.book_spine(c), f"block/decor/book_spine_{c}")
    save_png(T.leather_wrap(), "block/decor/leather_wrap")
    save_png(T.fireplace_stone(), "block/decor/fireplace_stone")
    save_png(T.fireplace_soot(), "block/decor/fireplace_soot")
    save_png(T.floo_pot(), "block/decor/floo_pot")
    save_animated(T.moving_portrait(), "block/moving_portrait", meta=T.PORTRAIT_ANIMATION)
    for h in HOUSES:
        save_png(T.house_banner_block(h), f"block/{h}_banner_block")


def candle(x, y, z, w, h):
    """A candle with a wick and a two-plane flame. Returns the elements."""
    wick = {f: [14, 0, 15, 1] for f in FACES}
    flame = {f: [12, 0, 14, 3] for f in FACES}
    cx, cz = x + w / 2, z + w / 2
    top = y + h
    return [
        el((x, y, z), (x + w, top, z + w), "wax"),
        el((cx - 0.25, top, cz - 0.25), (cx + 0.25, top + 1, cz + 0.25), "wax", uv=wick),
        el((cx - 0.5, top + 1, cz), (cx + 0.5, top + 2.5, cz), "wax", faces=("north", "south"), uv=flame, shade=False),
        el((cx, top + 1, cz - 0.5), (cx, top + 2.5, cz + 0.5), "wax", faces=("east", "west"), uv=flame, shade=False),
    ]


# Candle cluster wicks: (x, z, width, height) on a dish 1 px high. CandleClusterBlock.WICKS matches.
CLUSTER_CANDLES = [(4, 5, 2.5, 9), (8, 3.5, 2, 7), (10, 7, 2.5, 10), (5.5, 9.5, 2, 6), (9, 10.5, 2, 4.5)]


def remake_candle_cluster():
    els = [el((2.5, 0, 2.5), (13.5, 1, 13.5), "brass")]
    for (x, z, w, h) in CLUSTER_CANDLES:
        els += candle(x, 1, z, w, h)
    model("candle_cluster", {"wax": D("candle_wax"), "brass": D("brass"), "particle": D("candle_wax")},
          els, render_type="cutout", ao=False)
    blockstate_single("candle_cluster")
    item_from_block("candle_cluster")


def remake_book_pile():
    def book(frm, to, color, rot=None):
        length_x = to[0] - frm[0]
        length_z = to[2] - frm[2]
        return el(frm, to, "c_" + color, rot=rot,
                  tex_for={"north": "p_" + color, "south": "p_" + color, "east": "p_" + color,
                           "west": "s_" + color},
                  uv={"north": [0, 6, length_x, 10], "south": [0, 6, length_x, 10],
                      "east": [0, 6, length_z, 10]})

    els = [
        book((2, 0, 3), (14, 4, 13), "red"),
        book((3, 4, 3.5), (13, 8, 12.5), "blue", rot=("y", 22.5, (8, 4, 8))),
        # The open book on top: an open spread on a thin page block.
        el((3, 8, 4.5), (13, 9, 11.5), "pages", tex_for={"up": "open"},
           uv={"north": [0, 6, 10, 7], "south": [0, 6, 10, 7], "east": [0, 6, 7, 7], "west": [0, 6, 7, 7]},
           rot=("y", -22.5, (8, 8, 8))),
    ]
    tex = {"open": D("open_book"), "pages": D("book_pages_green"), "particle": D("book_cover_red")}
    for c in ("red", "blue", "green"):
        tex["c_" + c] = D(f"book_cover_{c}")
        tex["p_" + c] = D(f"book_pages_{c}")
        tex["s_" + c] = D(f"book_spine_{c}")
    model("enchanted_book_pile", tex, els)
    blockstate_single("enchanted_book_pile")
    item_from_block("enchanted_book_pile")



def remake_owl_perch():
    els = [
        el((2, 0, 7), (14, 1.5, 9), "log"),
        el((7, 0, 2), (9, 1.5, 14), "log"),
        el((6, 0, 6), (10, 2.5, 10), "log"),
        el((7, 2.5, 7), (9, 13, 9), "log"),
        el((6.5, 12, 6.5), (9.5, 13, 9.5), "brass"),
        # The perch bar ends the post: a T, not a cross.
        el((1.5, 13, 7), (14.5, 15, 9), "wrap"),
        el((0.5, 12.75, 6.75), (1.5, 15.25, 9.25), "brass"),
        el((14.5, 12.75, 6.75), (15.5, 15.25, 9.25), "brass"),
    ]
    model("owl_perch", {"log": DARK_LOG, "wrap": D("leather_wrap"), "brass": D("brass"), "particle": DARK_LOG}, els)
    blockstate_single("owl_perch")
    item_from_block("owl_perch")


def remake_floo_fireplace():
    """A stone fireplace with a mantel. The back wall is NORTH in model space: FlooFireplaceBlock is a
    campfire, whose FACING is the way the placing player looked, so the opening faces the player."""
    shell = [
        el((0, 0, 0), (16, 2, 16), "stone"),
        el((0, 2, 0), (16, 30, 3), "stone", tex_for={"south": "soot"}),
        el((0, 2, 3), (2.5, 22, 14), "stone", tex_for={"east": "soot"}),
        el((13.5, 2, 3), (16, 22, 14), "stone", tex_for={"west": "soot"}),
        el((2.5, 18, 3), (13.5, 22, 14), "stone", tex_for={"down": "soot"}),
        el((-1, 22, 0), (17, 24, 15.5), "wood"),
        el((1, 24, 0), (15, 30, 4), "stone"),
        el((3, 2, 7.5), (13, 3, 8.5), "iron"),
        el((3.5, 2, 4.5), (12.5, 4, 6.5), "log", tex_for={"east": "log_end", "west": "log_end"}),
        el((5, 3, 6.5), (11, 5, 8.5), "log", tex_for={"east": "log_end", "west": "log_end"}),
        # On the mantel: a pot of Floo powder between two candles.
        el((6.5, 24, 6), (9.5, 27, 9), "pot"),
    ]
    shell += candle(1.5, 24, 7, 1.5, 3)
    shell += candle(13, 24, 7, 1.5, 3)

    def flames(top, width):
        half = width / 2
        return [
            el((8 - half, 2, 7), (8 + half, top, 7), "flame", faces=("north", "south"),
               uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]}, rot=("y", 22.5, (8, 2, 7)), shade=False),
            el((8 - half, 2, 7), (8 + half, top, 7), "flame", faces=("north", "south"),
               uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]}, rot=("y", -22.5, (8, 2, 7)), shade=False),
            el((8 - half, 2, 9.5), (8 + half, top - 2, 9.5), "flame", faces=("north", "south"),
               uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]}, shade=False),
        ]

    tex = {"stone": D("fireplace_stone"), "soot": D("fireplace_soot"), "wood": DARK_WOOD, "iron": D("iron"),
           "log": V("dark_oak_log"), "log_end": V("dark_oak_log_top"), "pot": D("floo_pot"),
           "wax": D("candle_wax"), "particle": D("fireplace_stone")}
    model("floo_fireplace_unlit", tex, shell, render_type="cutout")
    model("floo_fireplace", dict(tex, flame=D("hearth_flame")), shell + flames(15, 10), render_type="cutout")
    model("floo_fireplace_active", dict(tex, flame=D("floo_flame")), shell + flames(18, 11), render_type="cutout")
    variants = {}
    for f, y in ROT.items():
        for lit in ("true", "false"):
            for sig in ("true", "false"):
                for wl in ("true", "false"):
                    name = "floo_fireplace_unlit" if lit == "false" else (
                        "floo_fireplace_active" if sig == "true" else "floo_fireplace")
                    v = {"model": f"{MOD}:block/{name}"}
                    if y:
                        v["y"] = y
                    variants[f"facing={f},lit={lit},signal_fire={sig},waterlogged={wl}"] = v
    write_json(os.path.join(ASSETS, "blockstates", "floo_fireplace.json"), {"variants": variants})
    item_from_block("floo_fireplace", display=small_gui(0.42))


# The Pensieve and the Wizard Cauldron are working blocks on the main line (memories, brewing with
# fill levels), with their own models. This script used to make a decorative Pensieve and a cauldron
# remake; both were dropped in the merge so they cannot overwrite the real ones.


def remakes():
    remake_textures()
    remake_candle_cluster()
    remake_book_pile()
    remake_owl_perch()
    remake_floo_fireplace()


# ---- workbenches ---------------------------------------------------------------------------------

FINE_PLANKS = f"{MOD}:block/planks_fine"
FINE_LOG = f"{MOD}:block/log_fine"


def workbench_textures():
    save_png(T.wand_box_wall(), "block/decor/wand_box_wall")
    save_png(T.wand_box("3a2418", "box_a"), "block/decor/wand_box_brown")
    save_png(T.wand_box("5a1a22", "box_b"), "block/decor/wand_box_red")
    save_png(T.wand_box("1e2a4a", "box_c"), "block/decor/wand_box_blue")
    save_png(T.phoenix_feather(), "block/decor/phoenix_feather")
    save_png(T.twine_spool(), "block/decor/twine")


def wand_workbench():
    """Ollivanders-style desk. Front faces NORTH; the box shelf runs along the back (south). The middle
    of the top stays clear: WandWorkbenchRenderer floats the ingredients there."""
    els = []
    for (x, z) in ((0.5, 0.5), (13, 0.5), (0.5, 13), (13, 13)):
        els.append(el((x, 0, z), (x + 2.5, 11, z + 2.5), "leg"))
    els += [
        # Ends at 12.5, under the tabletop board: both topping out at 13 z-fought.
        el((0, 11, 0), (16, 12.5, 16), "top"),
        el((-0.5, 12.5, -0.5), (16.5, 13, 16.5), "top"),
        el((1, 3, 1), (15, 4, 15), "top"),
        # Slim wand boxes stacked on the low shelf.
        el((2, 4, 2.5), (14, 5.5, 5), "box_b", tex_for={"east": "end", "west": "end"}),
        el((2.5, 4, 5.5), (13.5, 5.5, 8), "box_r", tex_for={"east": "end", "west": "end"}),
        el((2, 5.5, 3.5), (14, 7, 6), "box_u", tex_for={"east": "end", "west": "end"}),
        el((3, 4, 10), (15, 5.5, 12.5), "box_r", tex_for={"east": "end", "west": "end"}),
        # Shelf of wand boxes along the back.
        el((0, 13, 13), (16, 20, 16), "top", tex_for={"north": "wall"}),
        el((-0.5, 20, 12.5), (16.5, 21, 16), "top"),
        # Corner pieces: a jar with a phoenix feather, a candle, a single open box with a wand.
        el((1, 13, 1), (4.5, 17, 4.5), "glass", faces=("north", "south", "east", "west", "up")),
        el((1.5, 17, 1.5), (4, 17.5, 4), "leg"),
        el((2.5, 13, 2.75), (3, 18.5, 2.75), "feather", faces=("north", "south"), shade=False),
        el((2.75, 13, 2.5), (2.75, 18.5, 3), "feather", faces=("east", "west"), shade=False),
        el((11, 13, 1.5), (15, 14, 3.5), "box_b", tex_for={"up": "box_r"}),
        el((11.5, 14, 2.25), (14.5, 14.5, 2.75), "leg"),
    ]
    els += candle(13.25, 13, 9, 1.5, 4)
    model("wand_workbench", {"leg": FINE_LOG, "top": FINE_PLANKS, "wall": D("wand_box_wall"),
                             "box_b": D("wand_box_brown"), "box_r": D("wand_box_red"), "box_u": D("wand_box_blue"),
                             "end": D("wand_box_wall"), "glass": D("hourglass_glass"),
                             "feather": D("phoenix_feather"), "wax": D("candle_wax"), "particle": FINE_PLANKS},
          els, render_type="translucent")
    blockstate_facing("wand_workbench")
    item_from_block("wand_workbench", display=small_gui(0.55))


def broom_workbench():
    """Broommaker's trestle bench. Front faces NORTH; the tool board runs along the back. The middle
    of the top stays clear: BroomWorkbenchRenderer floats the ingredients there."""
    els = []
    for x in (1, 13):
        els += [
            el((x, 0, 2), (x + 2, 10, 4), "leg"),
            el((x, 0, 12), (x + 2, 10, 14), "leg"),
            el((x, 4, 4), (x + 2, 5.5, 12), "leg"),
        ]
    els += [
        el((0, 10, 1), (16, 12, 15), "top"),
        el((3, 2, 7), (13, 3.5, 9), "leg"),
        # A bundle of birch twigs on the stretchers, tied with cord.
        el((2, 5.5, 5), (14, 8, 9), "straw", tex_for={"east": "twig_end", "west": "twig_end"}),
        el((7, 5.25, 4.75), (8, 8.25, 9.25), "twine"),
        # A small vise on the front edge.
        el((6.5, 9, 0), (9.5, 12, 1), "iron"),
        el((5.5, 9.75, -0.5), (10.5, 10.25, 0), "iron"),
        # Tool board along the back, with a twine spool and a broom head hanging from it.
        el((0, 12, 13.5), (16, 21, 15), "top"),
        el((-0.5, 21, 13), (16.5, 22, 15.5), "top"),
        el((2, 12, 10), (5, 15, 13), "twine", tex_for={"up": "top", "down": "top"}),
        el((3, 15, 11), (4, 16, 12), "top"),
        # A finished broom hung across the board on two pegs.
        el((3, 17.5, 12), (4, 18.5, 13.5), "leg"),
        el((9, 17.5, 12), (10, 18.5, 13.5), "leg"),
        el((0.5, 18.5, 12.25), (12, 19.5, 13.25), "handle"),
        el((12, 17.5, 11.75), (13.5, 20.5, 13.5), "twine"),
        el((13.5, 16.5, 11.25), (17.5, 21.5, 13.75), "straw", tex_for={"east": "twig_end"}),
    ]
    model("broom_workbench", {"leg": V("stripped_spruce_log"), "top": V("spruce_planks"),
                              "straw": D("broom_straw"), "twig_end": V("birch_log_top"), "twine": D("twine"),
                              "handle": V("stripped_oak_log"),
                              "iron": D("iron"), "particle": V("spruce_planks")}, els)
    blockstate_facing("broom_workbench")
    # Keep the hand/ground transforms the item model already had; only the gui scale changes.
    item_from_block("broom_workbench", display={
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "gui": {"rotation": [30, 225, 0], "translation": [0, -1, 0], "scale": [0.55, 0.55, 0.55]},
        "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    })


def workbenches():
    workbench_textures()
    wand_workbench()
    broom_workbench()


def main():
    make_textures()
    floating_candle()
    hanging_banners()
    hourglasses()
    school_trunk()
    owl_cage()
    chess_board()
    crest_plaques()
    potion_shelf()
    brass_telescope()
    scroll_stack()
    quidditch()
    furniture()
    moving_portrait_fix()
    remakes()
    workbenches()
    data()
    print(f"wrote {len(written)} files")


if __name__ == "__main__":
    main()
