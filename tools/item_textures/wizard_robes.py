"""Auror and Death Eater robe sprites: hood, coat/robe, trousers, boots.

    python tools/item_textures/wizard_robes.py [out_dir]   (default: the mod textures/item dir)

Hand-authored 16x16 grids (texturing-minecraft-items skill). The worn textures are painted by
tools/wizard_robes/paint_robes.py; these sprites use the same colours.
"""
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
SKILL = os.path.join(ROOT, ".claude", "skills", "texturing-minecraft-items", "scripts")
if not os.path.isdir(SKILL):  # a worktree without the untracked skill: use the main checkout's
    SKILL = os.path.join(os.path.expanduser("~"), "OneDrive", "Desktop", "harry potter mod", ".claude", "skills",
                         "texturing-minecraft-items", "scripts")
sys.path.insert(0, SKILL)
from pixkit import sprite, save  # noqa: E402

OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "textures", "item")

# Black wool, darkest..light (the hood and the Auror's mantle).
WOOL = {"k": "#07070a", "1": "#121116", "2": "#1d1c23", "3": "#2b2a33", "4": "#424150", "5": "#5c5b6c"}
# The Death Eaters' blue-black.
NIGHT = {"k": "#050508", "1": "#0e0e15", "2": "#181823", "3": "#262636", "4": "#3a3a52", "5": "#55557a"}
CRIMSON = {"1": "#3a070b", "2": "#5a0b10", "3": "#7e1218", "4": "#a01d22", "5": "#c43a3a"}
SILVER = {"s": "#c8ccd5", "S": "#8a8f9c", "m": "#e4e6ec", "n": "#a4a9b6", "e": "#1c1d24"}

# --------------------------------------------------------------------------- hood
# A cowl seen from the front: a pointed crown, the cloth widening to the shoulders, the face opening
# ringed by its lining (L). The Auror's opening is shadow; the Death Eater's holds the silver mask.
AUROR_HOOD = """
................
......kkkk......
....kk4443kk....
...k44433322k...
..k443LLLL322k..
..k43LooooL32k..
.k443LooooL321k.
.k43LooooooL31k.
.k43LooooooL21k.
.k43LooooooL21k.
.k43LooooooL21k.
k443LooooooL221k
k433LLooooLL221k
k4332LLLLLL2211k
.kk3322222211kk.
...kkkkkkkkkk...
"""
DE_HOOD = """
................
......kkkk......
....kk4443kk....
...k44433322k...
..k443LLLL322k..
..k43LmmmnL32k..
.k443LmmnnL321k.
.k43LeemmeeL31k.
.k43LnmnnmnL21k.
.k43LmmnnmnL21k.
.k43LnmeenSL21k.
k443LSnnnnSL221k
k433LLSSSSLL221k
k4332LLLLLL2211k
.kk3322222211kk.
...kkkkkkkkkk...
"""

# --------------------------------------------------------------------------- coat / robe
# Auror: a black mantle piped in silver (s) over a crimson greatcoat, black lapels (l) round a charcoal
# waistcoat (w), a gold badge (G), black cuffs (c), a leather belt (b) with a silver buckle (B),
# and the coat open over charcoal trousers (t) down to the hem.
AUROR_COAT = """
................
.kkkkk....kkkkk.
kMMMMMk..kMMMMMk
kMNMMMMkkMMMMMMk
kMMMMMlwwlMMMMMk
kssssslwwlsssssk
k54k54lwwl43k32k
k43k44lwwlG3k32k
kcck43lwwl43kcck
.kkk44lwwl43kkk.
...kbbbBBbbbk...
...k43lwwl32k...
..k443lttl332k..
..k433lttl322k..
.k5433lttl3221k.
.kkkkkkkkkkkkkk.
"""
AUROR_COAT_PAL = {".": None, "k": "#1a0406", "M": WOOL["2"], "N": WOOL["4"], "s": SILVER["s"], "l": WOOL["1"],
                  "w": "#2e2e36", "G": "#e8c050", "c": WOOL["2"], "b": "#4a3322", "B": SILVER["m"],
                  "t": "#26262c", **CRIMSON}

# Death Eater: a closed black robe with silver clasps (x) down the front, a chain (s) slung across the
# chest, silver thread (S) beside the closure and a ragged hem.
DE_ROBE = """
................
.kkkkk....kkkkk.
k45443k..k44432k
k544443kk344432k
k44s44xxxx44s32k
k43ks344443sk32k
k43k34sxxs43k32k
kcck34411443kcck
.kkk4431134kkkk.
...k44S11S43k...
...k43S11S32k...
..k443xxxx332k..
..k433S11S322k..
.k4433S11S3221k.
.k3k22211222k1k.
.k.k.kk.kk.k.kk.
"""

# --------------------------------------------------------------------------- trousers
TROUSERS = """
................
................
..kkkkkkkkkkkk..
..k5444333322k..
..k4333333221k..
..k443kkkk321k..
..k452k..k321k..
..k442k..k431k..
..k452k..k321k..
..k442k..k431k..
..k452k..k321k..
..k442k..k431k..
..k452k..k321k..
..k432k..k211k..
..kkkkk..kkkkk..
................
"""
AUROR_TROUSERS_PAL = {".": None, "k": "#0e0e11", "1": "#1a1a1f", "2": "#24242a", "3": "#2f2f36", "4": "#3c3c45",
                      "5": "#55555f"}
DE_TROUSERS = TROUSERS.replace("452", "4S2").replace("431", "4S1")

# --------------------------------------------------------------------------- boots
# A pair of tall boots in profile, toes to the right: turned-down tops, a strap (s) round the shaft,
# a dark sole. The Death Eater's carry silver toe caps (S).
AUROR_BOOTS = """
................
................
.kkkk....kkkk...
.k55k....k55k...
.k43k....k43k...
.k43k....k43k...
.kssk....kssk...
.k43k....k43k...
.k432k...k432k..
.k4332k..k4332k.
.k43322k.k43322k
.k433322kk43332k
.k1111111kk1111k
.kkkkkkkkkkkkkkk
................
................
"""
DE_BOOTS = """
................
................
.kkkk....kkkk...
.k55k....k55k...
.k43k....k43k...
.ks3k....ks3k...
.k43k....k43k...
.ks3k....ks3k...
.k432k...k432k..
.k4332k..k4332k.
.k4332Sk.k4332Sk
.k43332SkS4333Sk
.k1111111kk1111k
.kkkkkkkkkkkkkkk
................
................
"""
LEATHER = {"k": "#140d08", "1": "#21160e", "2": "#382718", "3": "#4e3826", "4": "#6a4d34", "5": "#8a6848"}


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else OUT
    os.makedirs(out, exist_ok=True)
    hood_pal = {".": None, **WOOL, "L": CRIMSON["4"], "o": "#1b0b0c"}
    de_hood_pal = {".": None, **NIGHT, "L": "#202030", **SILVER}
    jobs = [
        ("auror_hood", AUROR_HOOD, hood_pal),
        ("death_eater_hood", DE_HOOD, de_hood_pal),
        ("auror_coat", AUROR_COAT, AUROR_COAT_PAL),
        ("death_eater_robe", DE_ROBE, {".": None, **NIGHT, "s": SILVER["s"], "S": SILVER["S"], "x": SILVER["m"],
                                      "c": NIGHT["2"]}),
        ("auror_trousers", TROUSERS, AUROR_TROUSERS_PAL),
        ("death_eater_trousers", DE_TROUSERS, {".": None, **NIGHT, "S": SILVER["S"]}),
        ("auror_boots", AUROR_BOOTS, {".": None, **LEATHER, "s": SILVER["s"]}),
        ("death_eater_boots", DE_BOOTS, {".": None, **NIGHT, "s": SILVER["S"], "S": SILVER["s"]}),
    ]
    for name, grid, pal in jobs:
        rows = [r for r in grid.strip("\n").split("\n")]
        bad = [i for i, r in enumerate(rows) if len(r) != 16]
        assert len(rows) == 16 and not bad, (name, len(rows), bad)
        save(sprite(grid, pal), os.path.join(out, name + ".png"))
        print("wrote", name)


if __name__ == "__main__":
    main()
