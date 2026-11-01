"""Write the Flow reference images for spellbooks: approved books flattened onto white at 1024 px.

usage: python book_refs.py <out_dir> [spell ...]     (default: protego crucio serpensortia)

Opaque on white so the references match the white background the prompt asks for. The default trio covers blue/silver, red/iron and green/gold, so Flow does not copy one cover colour.
"""
import os
import sys
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", ".."))
BOOKS = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "textures",
                     "item", "magic_books")

if len(sys.argv) < 2:
    sys.exit(__doc__)
out_dir = sys.argv[1]
spells = sys.argv[2:] or ["protego", "crucio", "serpensortia"]
os.makedirs(out_dir, exist_ok=True)
for s in spells:
    im = Image.open(os.path.join(BOOKS, f"magic_book_{s}.png")).convert("RGBA")
    im = im.resize((1024, 1024), Image.LANCZOS)
    bg = Image.new("RGBA", im.size, (255, 255, 255, 255))
    bg.alpha_composite(im)
    path = os.path.abspath(os.path.join(out_dir, f"ref_{s}.png"))
    bg.convert("RGB").save(path)
    print(path)
