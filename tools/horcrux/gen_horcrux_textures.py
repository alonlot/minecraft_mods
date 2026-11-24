"""Hand-laid 16x16 textures for the Horcrux reliquary (block model + floating heart).

Run:  python tools/horcrux/gen_horcrux_textures.py
Writes into src/main/resources/assets/hogwartsmagic/textures/block/.

The pedestal is cold carved slate, the claws tarnished silver, the heart a black stone with
veins. When the vessel holds a soul the serpent on the column and the rune ring on the step
burn through overlay textures (horcrux_serpent_glow, horcrux_rune_glow), drawn emissive by the
lit block model; the floating heart is drawn by HorcruxHeartRenderer from horcrux_heart_glow,
a grey texture the renderer tints crimson (rite) or green (bound).
"""
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "textures", "block")

CLEAR = (0, 0, 0, 0)

# Slate: blue-black with a faint green cast, so it sits with the green soul light.
S = ["#0a0b0e", "#121419", "#191c22", "#20242b", "#292e36", "#343a43", "#414852"]
# Tarnished silver.
M = ["#1e2125", "#33383e", "#4d545c", "#6c747d", "#8f99a3", "#b9c3cc", "#dde4ea"]
# Soul green (glow overlays).
G = ["#0c3a1c", "#16632f", "#249a46", "#45d066", "#8cf5a0", "#d4ffdc"]
# Dormant heart: black stone with dried-blood and green veins.
H = ["#100508", "#1c080d", "#2a0c14", "#3a121c", "#561a28"]
HV = ["#1d3524", "#2b5236"]


def rgba(c, a=255):
    c = c.lstrip("#")
    return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), a)


def new(fill=CLEAR):
    return Image.new("RGBA", (16, 16), fill)


def save(img, name):
    path = os.path.join(OUT, name + ".png")
    img.save(path)
    print("wrote", path)


def noisy(rng, palette, lo, hi, x, y):
    return rgba(palette[rng.randint(lo, hi)])


# ---------------------------------------------------------------------------------------------
# Stone: four courses of ashlar, staggered, dark mortar, light top-left bevel.
def stone(seed=3):
    rng = random.Random(seed)
    img = new()
    px = img.load()
    courses = [(0, 4, 0), (4, 8, 5), (8, 12, 2), (12, 16, 7)]  # (y0, y1, joint offset)
    for y0, y1, off in courses:
        joints = [(off + k * 8) % 16 for k in range(2)]
        for y in range(y0, y1):
            for x in range(16):
                if y == y1 - 1 or x in joints:
                    px[x, y] = rgba(S[0])
                    continue
                base = noisy(rng, S, 2, 3, x, y)
                if y == y0 or (x - 1) % 16 in joints:
                    base = rgba(S[4] if rng.random() < 0.75 else S[3])
                elif y == y1 - 2 or (x + 1) % 16 in joints:
                    base = rgba(S[1])
                px[x, y] = base
    # A few chips and hairline cracks.
    for _ in range(6):
        x, y = rng.randrange(16), rng.randrange(16)
        if px[x, y] != rgba(S[0]):
            px[x, y] = rgba(S[1])
    return img


# ---------------------------------------------------------------------------------------------
# Plinth top: smooth slate with a cut rune channel on the 3..13 step (visible band 3..5 / 11..13).
def stone_top(seed=5):
    rng = random.Random(seed)
    img = new()
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = noisy(rng, S, 2, 3, x, y)
            if x in (0, 15) or y in (0, 15):
                px[x, y] = rgba(S[1])
    # The groove of the rune band, cut into the step: a dark channel between rings.
    for y in range(16):
        for x in range(16):
            ring = max(abs(x - 7.5), abs(y - 7.5))
            if 3.0 < ring < 5.0:
                px[x, y] = rgba(S[1])
            if 4.0 < ring < 5.0 and (x + y) % 3 == 0:
                px[x, y] = rgba(S[0])
    # Bevel lines at the step edges.
    for i in range(3, 13):
        px[i, 3] = rgba(S[4])
        px[3, i] = rgba(S[4])
    return img


def rune_glow():
    """Cutout overlay for the rune band, drawn emissive over the step top when the vessel is lit."""
    img = new()
    px = img.load()
    for y in range(16):
        for x in range(16):
            ring = max(abs(x - 7.5), abs(y - 7.5))
            if 4.0 < ring < 5.0 and (x + y) % 3 == 0:
                px[x, y] = rgba(G[3])
            elif 3.0 < ring < 4.0 and (x * 7 + y * 3) % 5 == 0:
                px[x, y] = rgba(G[1])
    return img


# ---------------------------------------------------------------------------------------------
# Column: the element spans x 5..11, y 4..10, so its faces read texture u 5..11, v 6..12.
# A serpent coils down it as a groove; the glow overlay fills the same groove.
SERPENT = [
    "..###.",
    ".#...#",
    "..##..",
    "....#.",
    "#...#.",
    ".###..",
]
EYE = (5, 1)  # (col, row) of the serpent's head, a brighter pixel in the glow


def carved(seed=9):
    img = stone(seed)
    px = img.load()
    # Smooth dressed face where the column shows (u 5..11, v 6..12): no mortar joints across it.
    rng = random.Random(seed + 1)
    for v in range(6, 12):
        for u in range(5, 11):
            px[u, v] = noisy(rng, S, 3, 4, u, v)
    for u in range(5, 11):
        px[u, 6] = rgba(S[5])
        px[u, 11] = rgba(S[1])
    for r, row in enumerate(SERPENT):
        for c, ch in enumerate(row):
            if ch == "#":
                px[5 + c, 6 + r] = rgba(S[0])
                # Relief: the groove's lower lip catches light.
                below = (5 + c, 6 + r + 1)
                if r + 1 < 6 and SERPENT[r + 1][c] != "#":
                    px[below] = rgba(S[5])
    return img


def serpent_glow():
    img = new()
    px = img.load()
    for r, row in enumerate(SERPENT):
        for c, ch in enumerate(row):
            if ch == "#":
                px[5 + c, 6 + r] = rgba(G[3] if r < 3 else G[2])
    px[5 + EYE[0], 6 + EYE[1]] = rgba(G[5])
    return img


# ---------------------------------------------------------------------------------------------
# Tarnished silver for the capital rim and the four claws.
def metal(seed=13):
    rng = random.Random(seed)
    img = new()
    px = img.load()
    for y in range(16):
        for x in range(16):
            band = 3 if (x + y) % 7 == 0 else 2
            px[x, y] = rgba(M[band if rng.random() < 0.8 else band - 1])
            if rng.random() < 0.1:
                px[x, y] = rgba(M[1])  # tarnish pits
    # Engraved scale pattern, every other row.
    for y in range(1, 16, 3):
        for x in range((y // 3) % 2, 16, 2):
            px[x, y] = rgba(M[2])
    for x in range(16):
        px[x, 0] = rgba(M[4])
    return img


# ---------------------------------------------------------------------------------------------
# The heart. Dormant: black stone, a seam of dried blood and a faint green vein.
HEART_VEINS = [(3, 4), (4, 5), (5, 5), (6, 6), (7, 7), (8, 7), (9, 8), (10, 9), (11, 10), (8, 10), (7, 11), (6, 12)]


def heart(seed=21):
    rng = random.Random(seed)
    img = new()
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = rgba(H[rng.randint(1, 3)])
            if (x * 3 + y * 5) % 11 == 0:
                px[x, y] = rgba(H[4])
    for x, y in HEART_VEINS:
        px[x, y] = rgba(HV[1])
        if y + 1 < 16:
            px[x, y + 1] = rgba(HV[0])
    for x in range(16):
        px[x, 0] = rgba(H[4])
    return img


def heart_glow(seed=22):
    """Grey heart for the renderer to tint: bright veins, mid flesh, darker crust."""
    rng = random.Random(seed)
    img = new()
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = rng.randint(120, 160)
            if (x * 3 + y * 5) % 11 == 0:
                v = 95
            px[x, y] = (v, v, v, 255)
    for x, y in HEART_VEINS:
        px[x, y] = (255, 255, 255, 255)
        if y + 1 < 16:
            px[x, y + 1] = (215, 215, 215, 255)
    for x in range(16):
        px[x, 0] = (200, 200, 200, 255)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    save(stone(), "horcrux_stone")
    save(stone_top(), "horcrux_stone_top")
    save(rune_glow(), "horcrux_rune_glow")
    save(carved(), "horcrux_carved")
    save(serpent_glow(), "horcrux_serpent_glow")
    save(metal(), "horcrux_metal")
    save(heart(), "horcrux_heart")
    save(heart_glow(), "horcrux_heart_glow")


if __name__ == "__main__":
    main()
