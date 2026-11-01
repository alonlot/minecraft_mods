"""Check cut-out PNGs the way players see them: on dark and on slot grey, full size and at 32 px.

usage: python preview.py <out.png> <img.png> [<img.png> ...]
       python preview.py <out.png> --dir <folder>      (every PNG in a folder: the whole-set check)

Each image gets a 128 px tile on dark (20,20,20), one on slot grey (139,139,139), and a 32 px
copy under each. Look for a light fringe on the dark tile, and whether you can still name the
item at 32 px. (texturing-minecraft-items' preview.py is for 16x16 sprites; it NEAREST-resizes
HD art into mush.)
"""
import argparse
import os
from PIL import Image, ImageDraw

ap = argparse.ArgumentParser()
ap.add_argument("out")
ap.add_argument("images", nargs="*")
ap.add_argument("--dir")
args = ap.parse_args()
paths = list(args.images)
if args.dir:
    paths += sorted(os.path.join(args.dir, f) for f in os.listdir(args.dir) if f.endswith(".png"))
if not paths:
    ap.error("give image paths or --dir")

T, S = 128, 32
cell_w, cell_h = 2 * T, T + S + 18
cols = max(1, min(len(paths), 1600 // cell_w))
rows = (len(paths) + cols - 1) // cols
sheet = Image.new("RGB", (cols * cell_w, rows * cell_h), (60, 60, 60))
d = ImageDraw.Draw(sheet)
for i, p in enumerate(paths):
    im = Image.open(p).convert("RGBA")
    x, y = (i % cols) * cell_w, (i // cols) * cell_h
    for k, bg in enumerate([(20, 20, 20), (139, 139, 139)]):
        tile = Image.new("RGBA", (T, T), bg + (255,))
        tile.alpha_composite(im.resize((T, T), Image.LANCZOS))
        sheet.paste(tile.convert("RGB"), (x + k * T, y))
        small = Image.new("RGBA", (S, S), bg + (255,))
        small.alpha_composite(im.resize((S, S), Image.LANCZOS))
        sheet.paste(small.convert("RGB"), (x + k * T + (T - S) // 2, y + T + 2))
    d.text((x + 2, y + T + S + 4), os.path.basename(p)[:-4][:36], fill=(255, 255, 255))
sheet.save(args.out)
print(args.out, f"{len(paths)} images")
