"""The Advanced Potion-Making textbook: a worn green book lying at 3/4, pages showing, a flask on the cover.
Run from the repo root; draws src/main/resources/assets/hogwartsmagic/textures/item/advanced_potion_making.png."""
import sys
sys.path.insert(0, r".claude/skills/texturing-minecraft-items/scripts")
from PIL import Image
from pixkit import ramp, save, hexc, outline

G = ramp("#3f7a4a", 4)  # darkest .. lightest cover
P = {
    "1": G[0], "2": G[1], "3": G[2], "4": G[3],
    "p": "#f1e6c6", "q": "#c9b98f",
    "y": "#d9b04a", "c": "#8a5a2b",
    "f": "#cfe6f4", "g": "#7fb0d4", "l": "#9a4fc2", "m": "#c58be6",
}
# The cover, flat (10 wide x 10 tall); it is sheared to give the 3/4 view.
COVER = [
    "23333333",
    "21yyyy33",
    "213cc333",
    "21gffg33",
    "21gffg33",
    "2gffffg3",
    "2gmllfg3",
    "2gllllg3",
    "213ggg33",
    "11111111",
]
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()
H = len(COVER)
for r, row in enumerate(COVER):
    shift = (H - 1 - r) // 3          # the top of the cover leans right
    y = 3 + r
    for c, ch in enumerate(row):
        px[1 + shift + c, y] = hexc(P[ch])
    # the page edge down the right-hand side, one shade lit, one in shadow
    px[1 + shift + 8, y] = hexc(P["p"])
    px[1 + shift + 9, y] = hexc(P["q"]) if r else hexc(P["p"])
# the bottom page block under the cover
for x in range(1, 1 + 10):
    px[x, 2 + H] = hexc(P["p"])
    px[x, 3 + H] = hexc(P["q"])
img = outline(img, darken=0.30)
save(img, "src/main/resources/assets/hogwartsmagic/textures/item/advanced_potion_making.png")
