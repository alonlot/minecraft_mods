"""Portkey sprites: a worn old boot in profile (toe right), and the cracked Old Portkey, each with the two glow
stages the item shows while it charges (`hogwartsmagic:charge` overrides at 0.35 and 0.8).

    python tools/item_textures/portkeys.py

Hand-placed 16x16 grids (texturing-minecraft-items skill), light from the top-left. One master grid: the shaft
rises on the left with its laces down the front, the instep runs down to a scuffed toe whose sole has split and
hangs open (the dark wedge of the mouth), a patch is stitched on the ankle and a frayed lace end sticks out at the
top. The Old Portkey is the same boot gone grey and dry: cracks, a missing heel corner, green-grey mould specks.
The charging stage rims the lit edges in #BFE0FF and puts three cold pixels in the creases; the blazing stage lifts
the whole body two steps toward #D6ECFF with a white-hot core.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, ".claude", "skills", "texturing-minecraft-items", "scripts"))
from pixkit import sprite, save  # noqa: E402

OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "textures", "item")

# O lit outline (top-left), o dark outline, 1-4 leather light -> dark, w/v lace light/shadow, p/q patch and its
# stitch, s/S sole and heel, k the dark inside of the split toe, c crease pixels (cold in the glow stages).
BOOT = """
................
..OOOOOo........
.O11122wwv......
.O1122v3o.......
.O1122w3o.......
.O1223v3o.......
.O1pp2w33o......
.O1pq22v33o.....
.O1pp223w33oo...
.O1122233v333o..
.O1222c33w3344o.
.O222333c34444o.
.osssssssckkk...
.oSSSo...ssssSo.
.ooooo...ooooo..
................
"""

# A crack ('x') across the shaft and one over the instep, mould specks ('m'), the heel's back corner gone.
OLD_EDITS = [
    (3, 4, "x"), (4, 5, "x"), (5, 5, "x"),
    (9, 10, "x"), (10, 10, "x"), (11, 9, "x"),
    (2, 9, "m"), (6, 11, "m"), (12, 10, "m"),
    (2, 13, "."), (1, 13, "o"), (1, 14, "."), (2, 14, "o"),
]

LEATHER = {
    "O": "#7a4a26", "o": "#3d2412",
    "1": "#d9a066", "2": "#b8804a", "3": "#94592e", "4": "#6e3f1f",
    "w": "#e8d8b0", "v": "#a89870",
    "p": "#c79a5a", "q": "#5a3418",
    "s": "#4a2e1a", "S": "#2b1a0e", "k": "#1e1209", "c": "#94592e",
}

OLD_LEATHER = {
    "O": "#6b5c46", "o": "#2e261c",
    "1": "#a08a6a", "2": "#88745a", "3": "#6e5d48", "4": "#544636",
    "w": "#c8bea0", "v": "#8c846c",
    "p": "#9a8a6a", "q": "#40362a",
    "s": "#3e3428", "S": "#2e261c", "k": "#1a150f", "c": "#6e5d48",
    "x": "#3e3428", "m": "#7a8a62",
}

RIM = "#BFE0FF"
BLAZE_RIM = "#EAF6FF"
COLD = "#8FC8F0"
CORE = "#D6ECFF"


def lift(hex_colour, steps):
    """Move a colour `steps` ramp steps (a fifth of the way each) toward the Portkey's core light."""
    c = [int(hex_colour[i:i + 2], 16) for i in (1, 3, 5)]
    t = [int(CORE[i:i + 2], 16) for i in (1, 3, 5)]
    f = min(1.0, steps / 5.0)
    return "#%02x%02x%02x" % tuple(round(a + (b - a) * f) for a, b in zip(c, t))


def grid_with(grid, edits):
    rows = [list(r.strip()) for r in grid.strip("\n").splitlines() if r.strip()]
    for x, y, ch in edits:
        rows[y][x] = ch
    return "\n".join("".join(r) for r in rows)


def charging(palette):
    p = dict(palette)
    p["O"] = RIM
    p["c"] = COLD
    return p


def blazing(palette):
    p = {k: (lift(v, 2) if k not in ("k", "o", "S", ".") else v) for k, v in palette.items()}
    p["O"] = BLAZE_RIM
    p["c"] = "#ffffff"
    p["1"] = "#ffffff"
    return p


# The blazing core: two white-hot pixels at the heart of the ankle, plus a third cold crease on the shaft.
BLAZE_EDITS = [(4, 9, "c"), (5, 9, "c")]
CHARGE_EDITS = [(4, 8, "c")]


def main():
    old_grid = grid_with(BOOT, OLD_EDITS)
    sprites = {
        "portkey": sprite(BOOT, LEATHER),
        "portkey_charging": sprite(grid_with(BOOT, CHARGE_EDITS), charging(LEATHER)),
        "portkey_blazing": sprite(grid_with(BOOT, CHARGE_EDITS + BLAZE_EDITS), blazing(LEATHER)),
        "old_portkey": sprite(old_grid, OLD_LEATHER),
        "old_portkey_charging": sprite(grid_with(old_grid, CHARGE_EDITS), charging(OLD_LEATHER)),
        "old_portkey_blazing": sprite(grid_with(old_grid, CHARGE_EDITS + BLAZE_EDITS), blazing(OLD_LEATHER)),
    }
    for name, img in sprites.items():
        print(save(img, os.path.join(OUT, name + ".png")))


if __name__ == "__main__":
    main()
