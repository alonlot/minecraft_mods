"""Old-vs-new picking sheet: one row per item, the current texture first, then each Flow variant.

usage: python compare_sheet.py <out.png> --row <name> <old.png|-> <new1> [<new2> ...] [--row ...]
  e.g. python compare_sheet.py pick.png \
         --row accio magic_book_accio.png flow/scene-01-v1.jpeg flow/scene-01-v2.jpeg \
         --row brand_new_item - flow/scene-02-v1.jpeg flow/scene-02-v2.jpeg

Pass "-" as <old> for an item that has no texture yet. Old textures are drawn on white (like the
Flow images) so the user compares like with like; small pixel sprites are scaled with NEAREST so
they are not smeared. Send the sheet to the user with SendUserFile.
"""
import argparse
from PIL import Image, ImageDraw

ap = argparse.ArgumentParser()
ap.add_argument("out")
ap.add_argument("--row", action="append", nargs="+", required=True, metavar="NAME OLD NEW")
args = ap.parse_args()

T = 300
cols = max(len(r) - 1 for r in args.row)
sheet = Image.new("RGB", (cols * T, len(args.row) * T), "white")
d = ImageDraw.Draw(sheet)
for r, (name, old, *news) in enumerate(args.row):
    if old != "-":
        src = Image.open(old).convert("RGBA")
        src = src.resize((T, T), Image.NEAREST if src.width <= 64 else Image.LANCZOS)
        tile = Image.new("RGBA", (T, T), (255, 255, 255, 255))
        tile.alpha_composite(src)
        sheet.paste(tile.convert("RGB"), (0, r * T))
    d.text((6, r * T + 6), f"{name} OLD" if old != "-" else f"{name} (new item)", fill=(200, 0, 0))
    for v, path in enumerate(news, 1):
        sheet.paste(Image.open(path).convert("RGB").resize((T, T), Image.LANCZOS), (v * T, r * T))
        d.text((v * T + 6, r * T + 6), f"new v{v}", fill=(0, 0, 0))
sheet.save(args.out)
print(args.out, f"{len(args.row)} rows x {cols} cols")
