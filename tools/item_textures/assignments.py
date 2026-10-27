"""Hogwarts Assignment sprites: one rolled scroll, ribbon and wax seal recoloured per tier.

    python tools/item_textures/assignments.py

A rolled scroll on the vanilla diagonal instead of a front-on sheet; the tier
reads from the seal and ribbon (the original tier colours), Dark Orders from
the black parchment.
"""
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, ".claude", "skills", "texturing-minecraft-items", "scripts"))
from pixkit import sprite, save  # noqa: E402

OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "textures", "item")

# k outline; 3/2/1 parchment light/mid/shadow; e/c rolled end: paper edge / spiral;
# b/B ribbon light/dark; V/W/X seal light/mid/dark.
SCROLL = """
................
..........kkk...
.........k3eck..
........k332cck.
.......k33222eek
......k3322211k.
.....kbB22211k..
....k3BBVW11k...
...k332VWX1k....
..k3322WXXk.....
.k3322211k......
k1122211k.......
.k11211k........
..k111k.........
...kkk..........
................
"""

PARCHMENT = {"k": "#4a2e17", "3": "#fce9ba", "2": "#ead7a8", "1": "#c9ae78", "e": "#f4e2b4", "c": "#9a7a4a"}
DARK_PARCHMENT = {"k": "#141218", "3": "#5a5662", "2": "#46434c", "1": "#34313a", "e": "#625e6a", "c": "#222026"}

# variant: (parchment, ribbon light, ribbon dark, seal light, seal mid, seal dark)
VARIANTS = {
    "assignment":          (PARCHMENT, "#c8ccd4", "#7a808c", "#eef0f4", "#9aa0ac", "#5a606c"),
    "assignment_uncommon": (PARCHMENT, "#e8c547", "#a8841e", "#fff0a0", "#e8c547", "#a8841e"),
    "assignment_rare":     (PARCHMENT, "#55d6e0", "#248c9c", "#c0fbff", "#55d6e0", "#248c9c"),
    "assignment_epic":     (PARCHMENT, "#c77dff", "#8040ba", "#f0d0ff", "#c77dff", "#8040ba"),
    "assignment_dark":     (DARK_PARCHMENT, "#62c474", "#2fa84f", "#a8f0a0", "#2fa84f", "#16622c"),
}


def build(name):
    paper, bl, bd, sl, sm, sd = VARIANTS[name]
    return sprite(SCROLL, {".": None, **paper, "b": bl, "B": bd, "V": sl, "W": sm, "X": sd})


if __name__ == "__main__":
    for n in VARIANTS:
        save(build(n), os.path.join(OUT, f"{n}.png"))
    print("wrote", len(VARIANTS), "assignments")
