"""Register the goblin sound events in sounds.json and draw the two new item
textures (Galleon coin, goblin-forged silver dagger).

Idempotent - safe to re-run. From the repo root:
    python build_scripts/add_goblin_assets.py
"""

import json
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic")

SOUND_KEYS = ["ambient1", "ambient2", "ambient3", "hurt", "death", "trade", "no", "angry"]


def patch_sounds_json():
    path = os.path.join(ASSETS, "sounds.json")
    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    added = 0
    for key in SOUND_KEYS:
        event = "goblin." + key
        if event in data:
            continue
        data[event] = {
            "subtitle": "subtitles.hogwartsmagic." + event,
            "sounds": [{"name": "hogwartsmagic:goblin/" + key, "stream": False}],
        }
        added += 1
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    print("sounds.json: added", added, "entries")


def draw_galleon(path):
    """A fat gold coin stamped with the Gringotts 'G'."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    rim_dark = (128, 96, 26)
    rim = (176, 138, 44)
    face = (214, 176, 66)
    face_lit = (238, 208, 116)
    stamp = (122, 92, 24)

    d.ellipse([2, 2, 13, 13], fill=face, outline=rim_dark)
    d.ellipse([3, 3, 12, 12], fill=face, outline=rim)
    # Light catches the upper-left of the coin.
    d.arc([3, 3, 12, 12], start=150, end=300, fill=face_lit)
    d.point((5, 5), fill=(250, 232, 168))

    # A blocky serif "G" struck into the face.
    for x, y in [(7, 6), (8, 6), (9, 6), (6, 7), (6, 8), (6, 9), (7, 10), (8, 10),
                 (9, 10), (9, 9), (8, 8), (9, 8)]:
        d.point((x, y), fill=stamp)
    img.save(path)
    print("wrote", path)


def draw_dagger(path):
    """Short goblin-forged blade: pale silver, dark iron guard, wrapped grip."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    silver = (214, 220, 228)
    silver_lit = (244, 248, 252)
    silver_dark = (150, 158, 170)
    guard = (74, 70, 66)
    guard_lit = (108, 102, 96)
    grip = (58, 40, 28)
    grip_lit = (86, 60, 42)
    brass = (170, 137, 58)

    # Blade runs from the lower-left to the upper-right, tapering to a point.
    for i in range(9):
        x = 4 + i
        y = 11 - i
        d.point((x, y), fill=silver_lit)
        if i < 8:
            d.point((x, y + 1), fill=silver)
            d.point((x + 1, y + 1), fill=silver_dark)
    d.point((12, 3), fill=silver_lit)
    d.point((13, 2), fill=silver_lit)

    # Cross guard.
    d.line([(2, 12), (5, 9)], fill=guard)
    d.point((3, 11), fill=guard_lit)
    d.point((5, 12), fill=guard)
    d.point((2, 9), fill=guard)

    # Wrapped grip and pommel.
    d.line([(1, 14), (3, 12)], fill=grip)
    d.point((2, 13), fill=grip_lit)
    d.point((1, 15), fill=brass)
    img.save(path)
    print("wrote", path)


def main():
    patch_sounds_json()
    item_dir = os.path.join(ASSETS, "textures", "item")
    os.makedirs(item_dir, exist_ok=True)
    draw_galleon(os.path.join(item_dir, "galleon.png"))
    draw_dagger(os.path.join(item_dir, "goblin_silver_dagger.png"))


if __name__ == "__main__":
    main()
