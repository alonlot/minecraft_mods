"""Colloportus spell book on the shared base book.

Most spell books (the 1024/2048 ones) are the same tilted leather book with metal clasps, recoloured,
with a glowing emblem on the cover. This script:
  1. stacks every book that shares that silhouette and takes a low per-pixel percentile of their
     normalised brightness, which leaves the bare cover (cracks, seams, clasps) without any emblem;
  2. takes a reference book in the wanted colour (Stupefy, red with silver clasps),
     wipes its emblem by repainting the cover centre from the bare base in the reference's own palette;
  3. paints the new emblem on a 128 grid (8 px cells) with a pixelated glow and scales it up.

Run from the mod root: python3 tools/gen_shared_base_books.py (needs pillow and numpy).
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
import numpy as np

BOOKS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/hogwartsmagic/textures/item/magic_books"
SIZE, GRID = 1024, 128
CELL = SIZE // GRID
# books drawn on the shared base (Arresto, Reparo and Depulso are redrawn variants, so they stay out)
SHARED = ["alatus", "alohomora", "aqua_eructo", "ascendio", "avada_kedavra", "bombarda", "cave_inimicum",
          "confundo", "crucio", "diffindo", "expecto_patronum", "expelliarmus", "fiendfyre", "imperio",
          "incendio", "lumos", "morsmordre", "obliviate", "petrificus_totalus", "protego", "protego_maxima",
          "sectumsempra", "serpensortia", "stupefy"]


def load_stack():
    ref = np.array(Image.open(BOOKS / "magic_book_confundo.png").convert("RGBA"))[..., 3] > 128
    stack = {}
    for name in SHARED:
        im = Image.open(BOOKS / f"magic_book_{name}.png").convert("RGBA")
        a = np.array(im.resize((SIZE, SIZE), Image.LANCZOS)).astype(float)
        mask = a[..., 3] > 128
        _, dx, dy = max(((mask == np.roll(np.roll(ref, dy, 0), dx, 1)).mean(), dx, dy)
                        for dx in range(-3, 4) for dy in range(-3, 4))
        stack[name] = np.roll(np.roll(a, -dy, 0), -dx, 1)
    return stack, ref


def lum(rgb):
    return rgb[..., :3] @ np.array([0.299, 0.587, 0.114])


def bare_base(stack, ref):
    norm = []
    for a in stack.values():
        l = lum(a)
        norm.append(l / np.median(l[ref]))
    return np.percentile(np.stack(norm), 20, axis=0)


CX, CY, RX, RY = 530, 432, 200, 178          # cover centre and the emblem zone, in 1024 px


def wipe_emblem(book, base, alpha):
    """Repaint the cover centre of a reference book from the bare base, in that book's own cover palette."""
    ys, xs = np.mgrid[0:SIZE, 0:SIZE]
    r = np.hypot((xs - CX) / RX, (ys - CY) / RY)
    l = lum(book)
    nb = l / np.median(l[alpha])
    ring = alpha & (r > 1.05) & (r < 1.35) & (np.abs(nb - base) < 0.12)
    # brightness -> colour lookup from the clean cover around the emblem zone
    bins = np.linspace(base[ring].min(), base[ring].max(), 25)
    idx = np.clip(np.digitize(base[ring], bins), 1, len(bins) - 1)
    lut = np.array([book[ring][idx == i, :3].mean(0) if (idx == i).any() else [np.nan] * 3
                    for i in range(1, len(bins))])
    good = ~np.isnan(lut[:, 0])
    centres = (bins[:-1] + bins[1:]) / 2
    lut = np.stack([np.interp(centres, centres[good], lut[good, c]) for c in range(3)], 1)
    fill = np.stack([np.interp(base, centres, lut[:, c]) for c in range(3)], -1)
    m = np.clip((1.12 - r) / 0.12, 0, 1)[..., None] * alpha[..., None]
    out = book.copy()
    out[..., :3] = m * fill + (1 - m) * book[..., :3]
    out[..., 3] = alpha * 255
    return out


def to_px(x, y):
    """1024 px coordinates to grid cells."""
    return x / CELL, y / CELL


def glow_layer(core_mask, colour, radius, strength):
    """Pixelated halo: blur on the grid, quantise to 4 levels, scale up nearest."""
    im = Image.fromarray((core_mask * 255).astype(np.uint8))
    halo = np.array(im.filter(ImageFilter.GaussianBlur(radius))).astype(float) / 255
    halo = np.round(np.clip(halo * strength, 0, 1) * 4) / 4
    return halo, np.array(colour, float)


def compose(book, layers, alpha):
    """layers: list of (grid mask 0..1, rgb, mode) with mode 'add' (glow) or 'over' (solid)."""
    out = book.copy()
    for mask, rgb, mode in layers:
        big = np.kron(mask, np.ones((CELL, CELL)))[..., None]
        if mode == "add":
            out[..., :3] = out[..., :3] + big * rgb * 0.85
        else:
            out[..., :3] = big * rgb + (1 - big) * out[..., :3]
        out[..., 3] = np.maximum(out[..., 3], (big[..., 0] > 0.01) * 255 * (mode == "over"))
    out[..., :3] = np.clip(out[..., :3], 0, 255)
    out[..., 3] = np.where(alpha | (out[..., 3] > 0), out[..., 3], 0)
    return out


def grid_canvas():
    return Image.new("L", (GRID, GRID), 0)


def arr(im):
    return np.array(im).astype(float) / 255


# ---------------------------------------------------------------- Colloportus: chained padlock
def colloportus_layers():
    cx, cy = to_px(CX, CY)
    cx, cy = round(cx), round(cy) + 2
    chains = grid_canvas(); d = ImageDraw.Draw(chains)
    links = grid_canvas(); dl = ImageDraw.Draw(links)
    # two chains crossing behind the lock, following the cover's tilt
    for (x0, y0, x1, y1) in [(cx - 22, cy - 17, cx + 22, cy + 13), (cx - 22, cy + 13, cx + 22, cy - 17)]:
        n = 11
        for i in range(n + 1):
            t = i / n
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            if i % 2 == 0:
                d.ellipse([x - 2, y - 1.5, x + 2, y + 1.5], outline=255)
            else:
                d.ellipse([x - 1.5, y - 2, x + 1.5, y + 2], outline=255)
    lock = grid_canvas(); dk = ImageDraw.Draw(lock)
    shackle = grid_canvas(); ds = ImageDraw.Draw(shackle)
    ds.arc([cx - 6, cy - 14, cx + 6, cy - 2], 180, 360, fill=255, width=2)
    ds.line([cx - 6, cy - 8, cx - 6, cy - 3], fill=255, width=2)
    ds.line([cx + 5, cy - 8, cx + 5, cy - 3], fill=255, width=2)
    dk.rectangle([cx - 9, cy - 3, cx + 9, cy + 11], fill=255)
    hole = grid_canvas(); dh = ImageDraw.Draw(hole)
    dh.ellipse([cx - 2, cy + 1, cx + 2, cy + 5], fill=255)
    dh.rectangle([cx - 1, cy + 4, cx + 1, cy + 8], fill=255)
    rim = grid_canvas(); dr = ImageDraw.Draw(rim)
    dr.rectangle([cx - 9, cy - 3, cx + 9, cy + 11], outline=255)
    hi = grid_canvas(); dhi = ImageDraw.Draw(hi)
    dhi.line([cx - 7, cy - 1, cx - 7, cy + 9], fill=255)
    dhi.line([cx - 7, cy - 1, cx + 6, cy - 1], fill=255)

    c, k, s, h, r, hl = (arr(x) for x in (chains, lock, shackle, hole, rim, hi))
    c = c * (1 - np.clip(k + s, 0, 1))
    everything = np.clip(c + k + s, 0, 1)
    halo, _ = glow_layer(everything, 0, 3, 1.6)
    return [
        (halo, np.array([235, 170, 60]), "add"),
        (c, np.array([205, 210, 220]), "over"),
        (s, np.array([200, 205, 215]), "over"),
        (k, np.array([232, 178, 52]), "over"),
        (hl, np.array([255, 236, 150]), "over"),
        (r, np.array([140, 88, 22]), "over"),
        (h, np.array([45, 18, 12]), "over"),
    ]


def main():
    stack, alpha = load_stack()
    base = bare_base(stack, alpha)
    for name, ref, layers in [("colloportus", "stupefy", colloportus_layers())]:
        blank = wipe_emblem(stack[ref], base, alpha)
        out = compose(blank, layers, alpha)
        Image.fromarray(out.astype(np.uint8)).save(BOOKS / f"magic_book_{name}.png")
        print("wrote", f"magic_book_{name}.png")


if __name__ == "__main__":
    main()
