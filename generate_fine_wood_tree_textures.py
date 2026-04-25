from __future__ import annotations

from pathlib import Path
from PIL import Image


ROOT = Path(__file__).resolve().parent
BLOCK_DIR = ROOT / "src" / "main" / "resources" / "assets" / "hogwartsmagic" / "textures" / "block"
BACKUP_DIR = ROOT / "tmp" / "fine_wood_tree_texture_backup"
SIZE = 16


def clamp(value: int) -> int:
    return max(0, min(255, value))


def shift(color: tuple[int, int, int, int], dr: int = 0, dg: int = 0, db: int = 0, da: int = 0) -> tuple[int, int, int, int]:
    r, g, b, a = color
    return (clamp(r + dr), clamp(g + dg), clamp(b + db), clamp(a + da))


TRUNK_DARK = (82, 53, 33, 255)
TRUNK_MID = (118, 78, 49, 255)
TRUNK_LIGHT = (164, 118, 78, 255)
LEAF_DARK = (74, 41, 58, 255)
LEAF_MID = (145, 91, 123, 255)
LEAF_LIGHT = (221, 167, 196, 255)
PETAL = (247, 206, 228, 255)
PETAL_BRIGHT = (255, 232, 244, 255)


def save_backup(path: Path) -> None:
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    if path.exists():
        (BACKUP_DIR / path.name).write_bytes(path.read_bytes())


def canvas() -> Image.Image:
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def generate_sapling() -> Image.Image:
    img = canvas()
    px = img.load()

    stem = {
        (7, 15): TRUNK_DARK,
        (8, 15): TRUNK_MID,
        (7, 14): TRUNK_MID,
        (8, 14): TRUNK_LIGHT,
        (7, 13): TRUNK_MID,
        (8, 13): TRUNK_LIGHT,
        (7, 12): TRUNK_DARK,
        (8, 12): TRUNK_MID,
        (6, 11): TRUNK_MID,
        (7, 11): TRUNK_LIGHT,
        (8, 11): TRUNK_LIGHT,
        (9, 11): TRUNK_MID,
        (6, 10): TRUNK_DARK,
        (7, 10): TRUNK_MID,
        (8, 10): TRUNK_LIGHT,
        (9, 10): TRUNK_LIGHT,
        (10, 10): TRUNK_MID,
    }
    for pos, color in stem.items():
        px[pos] = color

    for pos in [(5, 12), (10, 12), (5, 11), (10, 11), (6, 9), (9, 9)]:
        px[pos] = shift(TRUNK_MID, 8, 8, 4)

    leaf_map = {
        (5, 8): LEAF_DARK, (6, 8): LEAF_MID, (7, 8): LEAF_LIGHT, (8, 8): LEAF_LIGHT, (9, 8): LEAF_MID,
        (4, 7): LEAF_DARK, (5, 7): LEAF_MID, (6, 7): LEAF_LIGHT, (7, 7): PETAL, (8, 7): PETAL_BRIGHT,
        (9, 7): LEAF_LIGHT, (10, 7): LEAF_MID,
        (4, 6): LEAF_MID, (5, 6): LEAF_LIGHT, (6, 6): PETAL, (7, 6): PETAL_BRIGHT, (8, 6): PETAL,
        (9, 6): LEAF_LIGHT, (10, 6): LEAF_MID,
        (5, 5): LEAF_MID, (6, 5): LEAF_LIGHT, (7, 5): PETAL, (8, 5): PETAL_BRIGHT, (9, 5): LEAF_LIGHT,
        (6, 4): LEAF_MID, (7, 4): LEAF_LIGHT, (8, 4): LEAF_LIGHT, (9, 4): LEAF_MID,
    }
    for pos, color in leaf_map.items():
        px[pos] = color

    for pos in [(5, 9), (9, 9), (6, 3), (8, 3)]:
        px[pos] = shift(LEAF_MID, -10, -2, -6)

    return img


def generate_leaves() -> Image.Image:
    img = canvas()
    px = img.load()

    for y in range(SIZE):
        for x in range(SIZE):
            if (x + y) % 5 == 0 or (x * 3 + y * 2) % 7 == 0:
                continue

            color = LEAF_MID
            if (x + y) % 4 == 0:
                color = LEAF_LIGHT
            elif (x * 2 + y) % 5 == 0:
                color = LEAF_DARK

            if x in {0, 15} or y in {0, 15}:
                color = shift(color, -18, -10, -8)

            px[x, y] = color

    blossoms = [
        (3, 3), (7, 2), (11, 4), (5, 6), (10, 7), (3, 10), (8, 10), (12, 11), (6, 13)
    ]
    for x, y in blossoms:
        for dx, dy, color in [
            (0, 0, PETAL),
            (1, 0, PETAL_BRIGHT),
            (0, 1, PETAL_BRIGHT),
            (1, 1, PETAL),
        ]:
            tx = x + dx
            ty = y + dy
            if 0 <= tx < SIZE and 0 <= ty < SIZE:
                px[tx, ty] = color

    for x, y in [(1, 5), (13, 2), (14, 9), (2, 14)]:
        px[x, y] = shift(LEAF_DARK, -8, -6, -4)

    return img


def write_texture(path: Path, image: Image.Image) -> None:
    save_backup(path)
    image.save(path)


def main() -> None:
    BLOCK_DIR.mkdir(parents=True, exist_ok=True)
    write_texture(BLOCK_DIR / "fine_wood_sapling.png", generate_sapling())
    write_texture(BLOCK_DIR / "fine_wood_leaves.png", generate_leaves())
    print("Updated fine wood sapling and leaves textures.")


if __name__ == "__main__":
    main()
