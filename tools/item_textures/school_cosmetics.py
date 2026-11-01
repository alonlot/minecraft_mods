"""School robes, house scarves and the pointed hat sprites.

    python tools/item_textures/school_cosmetics.py [out_dir]   (default: the mod textures/item dir)

Hand-authored 16x16 grids (texturing-minecraft-items skill). The robe is one master in black
cloth with the house showing where it does on the worn robe (tools/armor_textures/house_cosmetics.py):
the hood lining at the collar, the tie in the open front and the crest on the left breast. The
scarves share one folded-over-a-peg master, striped across in the house colours.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, ".claude", "skills", "texturing-minecraft-items", "scripts"))
from pixkit import sprite, save  # noqa: E402

OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "textures", "item")

# K/k outline (lit / shadow side), r/R/L/H black cloth (base, mid, light, highlight); the lit
# edges are pushed well above the slot grey so the robe still reads on the dark hotbar.
CLOTH = {"K": "#26262e", "k": "#08080a", "x": "#121216", "r": "#1c1c22", "R": "#2c2c35", "L": "#43434f",
         "H": "#74747f"}
UNIFORM = {"w": "#eeeee8", "J": "#77777d", "j": "#56565c"}

# P/p house colour light/dark, A/a second colour light/dark (scarf, tie); M crest metal.
HOUSES = {
    "gryffindor": dict(P="#b3332b", p="#6e1618", A="#e6b43a", a="#9c7416", M="#e6b43a"),
    "slytherin": dict(P="#2b7845", p="#123f24", A="#c8cdd2", a="#7c8188", M="#c8cdd2"),
    "ravenclaw": dict(P="#2f58a0", p="#16305e", A="#a9aeb4", a="#6b7077", M="#c48e46"),
    "hufflepuff": dict(P="#f0c63a", p="#a37a10", A="#2a2a2f", a="#121214", M="#1c1c1f"),
}

# A robe hung up: shoulders and sleeves, open down the front over the jumper and tie, crest on the
# wearer's left breast (the viewer's right), the skirt flaring a little to the hem.
ROBE = """
................
.....KPPPPk.....
...KKRpwwprkk...
..KRRrkPAkrrxk..
.KRLrrkAPkrMMxk.
KRLrrrkJJkrMMrxk
KRLkrrkJJkrrkrxk
KLk.KrkJJkrk.kxk
KLk.KrkjjkrxkkKk
.kk.KRrkkrrxk...
....KRrrkrrxk...
....KRrrkrrxk...
...KRLrrkrrrxk..
...KRLrrkrrrxk..
...KHLLLkLLLxk..
....kkkkkkkkkk..
"""

# A scarf folded over a peg: two striped ends hanging, fringe at the bottom.
SCARF = """
................
......KKKk......
.....KPPPpk.....
....KPPkkPpk....
....KAAk.kAak...
....KPPk.kPpk...
....KPpk.kPpk...
....KAAk.kAak...
....KPPk.kPpk...
...KPPpk.kPpk...
...KAAak.kAak...
...KPPpk.kPpk...
...KPPpk.kAak...
...KAAak.a.a....
...a.a.a........
................
"""

# The first-year hat: a felt cone bending back at the tip over a wide brim, three-quarter view.
HAT = """
................
...........Kk...
..........KRk...
.........KRxk...
........KRrk....
........KRrk....
.......KRrrxk...
.......KLrrxk...
......KLrrrrxk..
......KLrrrrxk..
.....KLrrrrrrxk.
.....KLrrrrrrxk.
..KKKLLrrrrrrxkk
.KHHLLLLLrrrrrrk
..kkkkkkkkkkkkk.
................
"""


def build_all(out=OUT):
    for house, colours in HOUSES.items():
        pal = dict(CLOTH, **UNIFORM, **colours)
        save(sprite(ROBE, pal), os.path.join(out, house + "_robes.png"))
        save(sprite(SCARF, pal), os.path.join(out, house + "_scarf.png"))
    save(sprite(HAT, CLOTH), os.path.join(out, "pointed_hat.png"))


if __name__ == "__main__":
    build_all(sys.argv[1] if len(sys.argv) > 1 else OUT)
