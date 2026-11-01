"""Paint the Caelum Fascino spellbook icon.

    python build_scripts/generate_caelum_fascino_book.py

The mod's spellbooks are painted 1024x1024 art on one shared base book (see .claude/skills/texturing-spellbooks).
The art for those came out of Flow with the user picking a variant; this spell's book is made here instead, from
the approved Protego book, so it is exactly on-model (same tome, angle, hinge clasps, gem and page block):

  1. the Protego emblem is wiped off the cover (the cover colour around it is blurred across the gap and
     mottled again so the leather does not look wiped),
  2. the cover is turned from navy to a deeper twilight violet-indigo, so it does not sit next to Protego's or
     Alohomora's blues, and the grey hinge clasps are tinted gold,
  3. a new emblem is drawn on the cover on a coarse grid that matches the base book's pixel size: a glowing
     crescent moon with three stars, the night sky the spell hangs on a ceiling. It is bright pale gold with a
     periwinkle glow (the beam colour 0x7FA8FF, SpellType.CAELUM_FASCINO), one solid shape, so it still reads at
     hotbar size.

If the user prefers a Flow-painted book, the normal skill flow overwrites the texture this writes; nothing else
depends on how it was made.
"""

import colorsys
import os
import random

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BOOKS = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "textures", "item", "magic_books")
BASE = os.path.join(BOOKS, "magic_book_protego.png")
OUT = os.path.join(BOOKS, "magic_book_caelum_fascino.png")

# The cover's hue after the shift, in degrees (violet-indigo), and how much brighter its glow is than the leather.
COVER_HUE = 262.0
BLOCK = 14  # the base book's pixel size in the 1024 image
EMBLEM_BLOCK = 19  # the emblem is drawn a little coarser so it carries at hotbar size
GLOW = (127, 168, 255)  # 0x7FA8FF
GOLD_EMBLEM = (255, 236, 168)
GOLD_EDGE = (255, 205, 96)
CLASP_GOLD = (214, 170, 58)

# The area the old emblem and its glow covered (x0, y0, x1, y1), with room to spare; cover lies around it.
EMBLEM_BOX = (350, 215, 790, 610)


def luminance(a):
    return 0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]


def dilate(mask, r):
    """A box dilation by r pixels, no scipy needed."""
    out = mask.copy()
    for _ in range(r):
        grown = out.copy()
        grown[1:, :] |= out[:-1, :]
        grown[:-1, :] |= out[1:, :]
        grown[:, 1:] |= out[:, :-1]
        grown[:, :-1] |= out[:, 1:]
        out = grown
    return out


def box_blur(a, r):
    """A separable box blur of an HxWxC float array by radius r."""
    k = 2 * r + 1
    pad = np.pad(a, ((r, r), (0, 0), (0, 0)), mode="edge")
    csum = np.cumsum(np.concatenate([np.zeros((1,) + pad.shape[1:]), pad], axis=0), axis=0)
    a = (csum[k:] - csum[:-k]) / k
    pad = np.pad(a, ((0, 0), (r, r), (0, 0)), mode="edge")
    csum = np.cumsum(np.concatenate([np.zeros((pad.shape[0], 1, pad.shape[2])), pad], axis=1), axis=1)
    return (csum[:, k:] - csum[:, :-k]) / k


def wipe_emblem(img):
    """Replaces the old emblem and its glow with blurred cover colour plus a coarse mottle."""
    rgb = img[..., :3].astype(np.float64)
    x0, y0, x1, y1 = EMBLEM_BOX
    box = np.zeros(img.shape[:2], bool)
    box[y0:y1, x0:x1] = True
    lum = luminance(rgb)
    blue_glow = (rgb[..., 2] > 150) & (rgb[..., 2] - rgb[..., 0] > 70) & (lum > 95)
    emblem = dilate(blue_glow & box, 16)
    cover = (img[..., 3] > 0) & ~emblem
    weights = cover.astype(np.float64)[..., None]
    # Normalised convolution: the blur of cover pixels over the blur of the cover mask, so the hole fills from its
    # edges inwards instead of fading to black.
    num = box_blur(rgb * weights, 40)
    den = np.maximum(box_blur(weights, 40), 1e-3)
    fill = num / den
    rng = random.Random(15)
    mottle = np.zeros(img.shape[:2])
    for by in range(0, img.shape[0], BLOCK):
        for bx in range(0, img.shape[1], BLOCK):
            mottle[by:by + BLOCK, bx:bx + BLOCK] = rng.uniform(-7, 7)
    fill = np.clip(fill + mottle[..., None], 0, 255)
    out = img.copy()
    out[emblem & (img[..., 3] > 0), :3] = fill[emblem & (img[..., 3] > 0)].astype(np.uint8)
    return out


def recolour(img):
    """Cover navy -> violet-indigo; grey clasps -> gold. Pages (tan) and the yellow gem stay as they are."""
    out = img.copy()
    h, w = img.shape[:2]
    for y in range(h):
        for x in range(w):
            r, g, b, a = img[y, x]
            if a == 0:
                continue
            hh, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            deg = hh * 360.0
            if 190.0 <= deg <= 260.0 and s > 0.28:
                # leather: keep value and saturation, move the hue; a touch more saturated for the violet
                nr, ng, nb = colorsys.hsv_to_rgb(COVER_HUE / 360.0, min(1.0, s * 1.05), v)
                out[y, x, :3] = (round(nr * 255), round(ng * 255), round(nb * 255))
            elif s < 0.2 and v > 0.33 and x < 470:
                # hinge clasps (grey metal on the spine side): tint to gold, keeping the highlight structure
                shade = v / 0.78
                out[y, x, :3] = tuple(int(max(0, min(255, c * shade))) for c in CLASP_GOLD)
    return out


# The emblem on a coarse grid (one character = one BLOCK x BLOCK pixel block of the 1024 image).
#   M = crescent body   m = crescent rim   * = star   . = nothing
EMBLEM = [
    "......mmmm........",
    "....mmMMMMm.......",
    "...mMMMMMMMm....*.",
    "..mMMMMmmmmm......",
    "..mMMMm...........",
    ".mMMMMm........*..",
    ".mMMMm............",
    ".mMMMm.........m..",
    ".mMMMm............",
    ".mMMMMm...........",
    "..mMMMMm...*......",
    "..mMMMMMmm....mm..",
    "...mMMMMMMMMMMMm..",
    "....mmMMMMMMMmm...",
    "......mmmmmmm.....",
]
# stars are small crosses so they stay visible: drawn by hand around these block coordinates (col, row)
STARS = [(16, 2), (15, 5), (10, 6), (13, 8)]


def draw_emblem(img):
    h, w = img.shape[:2]
    rows, cols = len(EMBLEM), len(EMBLEM[0])
    # centre the grid on the old emblem's box
    ox = (EMBLEM_BOX[0] + EMBLEM_BOX[2]) // 2 - cols * EMBLEM_BLOCK // 2 + 8
    oy = (EMBLEM_BOX[1] + EMBLEM_BOX[3]) // 2 - rows * EMBLEM_BLOCK // 2 - 6
    layer = np.zeros((h, w), np.uint8)  # 0 none, 1 rim, 2 body, 3 star core
    for r, line in enumerate(EMBLEM):
        for c, ch in enumerate(line):
            v = {"m": 1, "M": 2}.get(ch, 0)
            if v:
                layer[oy + r * EMBLEM_BLOCK: oy + (r + 1) * EMBLEM_BLOCK, ox + c * EMBLEM_BLOCK: ox + (c + 1) * EMBLEM_BLOCK] = v
    for sc, sr in STARS:
        cx, cy = ox + sc * EMBLEM_BLOCK, oy + sr * EMBLEM_BLOCK
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            layer[cy + dy * EMBLEM_BLOCK: cy + (dy + 1) * EMBLEM_BLOCK, cx + dx * EMBLEM_BLOCK: cx + (dx + 1) * EMBLEM_BLOCK] = 3
    # a glow: blur the emblem silhouette, tint it periwinkle, add it under the shape
    sil = (layer > 0).astype(np.float64)[..., None]
    halo = np.clip(box_blur(sil, 22) * 1.7, 0, 1)[..., 0]
    out = img.astype(np.float64)
    cover = img[..., 3] > 0
    for ch in range(3):
        out[..., ch] = np.where(cover, out[..., ch] * (1 - halo * 0.55) + GLOW[ch] * halo * 0.55, out[..., ch])
    for ch in range(3):
        out[..., ch] = np.where(layer == 2, GOLD_EMBLEM[ch], out[..., ch])
        out[..., ch] = np.where(layer == 1, GOLD_EDGE[ch], out[..., ch])
        out[..., ch] = np.where(layer == 3, 255, out[..., ch])
    return np.clip(out, 0, 255).astype(np.uint8)


def main():
    img = np.array(Image.open(BASE).convert("RGBA"))
    img = wipe_emblem(img)
    img = recolour(img)
    img = draw_emblem(img)
    Image.fromarray(img, "RGBA").save(OUT, optimize=True)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
