from __future__ import annotations

from pathlib import Path
from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parent
BLOCK_DIR = ROOT / "src" / "main" / "resources" / "assets" / "hogwartsmagic" / "textures" / "block"
ITEM_DIR = ROOT / "src" / "main" / "resources" / "assets" / "hogwartsmagic" / "textures" / "item"
BACKUP_DIR = ROOT / "tmp" / "fine_wood_texture_backup"

SIZE = 16


def clamp(value: int) -> int:
    return max(0, min(255, value))


def shift(color: tuple[int, int, int, int], dr: int = 0, dg: int = 0, db: int = 0, da: int = 0) -> tuple[int, int, int, int]:
    r, g, b, a = color
    return (clamp(r + dr), clamp(g + dg), clamp(b + db), clamp(a + da))


BASE = (150, 114, 67, 255)
MID = (123, 87, 48, 255)
DARK = (86, 57, 31, 255)
DEEP = (59, 36, 18, 255)
LIGHT = (193, 154, 102, 255)
GLOW = (221, 189, 136, 255)


def save_backup(path: Path) -> None:
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    if path.exists():
        (BACKUP_DIR / path.name).write_bytes(path.read_bytes())


def new_canvas() -> Image.Image:
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def generate_log_side() -> Image.Image:
    img = new_canvas()
    pixels = img.load()

    stripe_cycle = [shift(BASE, -12, -10, -8), BASE, shift(BASE, 10, 9, 6), MID]
    edge_shadow = {0: -34, 1: -20, 14: -16, 15: -30}

    for y in range(SIZE):
        for x in range(SIZE):
            color = stripe_cycle[x % len(stripe_cycle)]
            ripple = ((y * 3 + x * 5) % 7) - 3
            color = shift(color, ripple * 2, ripple, 0)

            if x in edge_shadow:
                color = shift(color, edge_shadow[x], edge_shadow[x] // 2, edge_shadow[x] // 3)

            if (x + y) % 5 == 0:
                color = shift(color, -10, -8, -6)
            elif (x * 2 + y) % 6 == 0:
                color = shift(color, 10, 8, 4)

            pixels[x, y] = color

    for y in range(1, SIZE - 1, 3):
        notch_x = 2 + ((y * 5) % 11)
        for offset in range(2):
            x = min(SIZE - 3, notch_x + offset)
            pixels[x, y] = shift(pixels[x, y], -22, -16, -10)
            pixels[x + 1, y + 1] = shift(pixels[x + 1, y + 1], 15, 10, 6)

    return img


def generate_log_top() -> Image.Image:
    img = new_canvas()
    pixels = img.load()
    center_x, center_y = 7.2, 7.6

    for y in range(SIZE):
        for x in range(SIZE):
            dx = x - center_x
            dy = y - center_y
            distance = (dx * dx + dy * dy) ** 0.5

            color = LIGHT if distance < 2.4 else BASE
            ring = int(distance * 2.2) % 4
            if ring == 0:
                color = shift(color, 22, 18, 10)
            elif ring == 1:
                color = shift(color, -8, -8, -4)
            elif ring == 2:
                color = shift(color, -20, -18, -10)

            if distance > 6.2:
                color = shift(MID, -12, -8, -5)
            if distance > 7.0:
                color = DARK

            if abs(dx) < 1 and y in {1, 2, 12, 13}:
                color = shift(color, -18, -12, -8)

            pixels[x, y] = color

    draw = ImageDraw.Draw(img)
    draw.point((7, 7), fill=DEEP)
    draw.point((8, 7), fill=shift(DEEP, 10, 6, 2))
    draw.line((7, 2, 8, 5), fill=shift(DARK, -4, -3, 0))
    draw.line((6, 10, 5, 13), fill=shift(DARK, -4, -3, 0))
    return img


def generate_planks() -> Image.Image:
    img = new_canvas()
    pixels = img.load()

    board_tops = [0, 5, 10]
    board_colors = [shift(BASE, 4, 4, 0), shift(BASE, -8, -6, -2), shift(BASE, 12, 10, 4)]

    for board_index, top in enumerate(board_tops):
        base_color = board_colors[board_index]
        bottom = SIZE if board_index == len(board_tops) - 1 else board_tops[board_index + 1]

        for y in range(top, bottom):
            for x in range(SIZE):
                color = base_color
                grain = ((x * 3 + y * 2 + board_index) % 6) - 2
                color = shift(color, grain * 3, grain * 2, grain)

                if y == top and top != 0:
                    color = shift(DEEP, 10, 6, 2)
                elif y == bottom - 1 and bottom != SIZE:
                    color = shift(base_color, -18, -14, -8)

                if x in {3, 9, 13}:
                    color = shift(color, -8, -6, -2)
                elif x in {1, 6, 11, 15}:
                    color = shift(color, 10, 7, 3)

                pixels[x, y] = color

        knot_x = 4 + board_index * 4
        knot_y = top + 2
        if knot_y < SIZE - 1:
            pixels[knot_x, knot_y] = DEEP
            pixels[min(SIZE - 1, knot_x + 1), knot_y] = shift(DARK, 8, 4, 0)
            pixels[knot_x, min(SIZE - 1, knot_y + 1)] = shift(DARK, 16, 10, 3)

    return img


def generate_stick() -> Image.Image:
    img = new_canvas()
    pixels = img.load()

    shaft = [
        (3, 13), (4, 12), (5, 11), (6, 10), (7, 9),
        (8, 8), (9, 7), (10, 6), (11, 5), (12, 4)
    ]

    for i, (x, y) in enumerate(shaft):
        core = BASE if i < 4 else shift(BASE, 8, 7, 2)
        pixels[x, y] = shift(core, -6, -5, -3)
        if x + 1 < SIZE and y - 1 >= 0:
            pixels[x + 1, y - 1] = GLOW
        if x - 1 >= 0 and y + 1 < SIZE:
            pixels[x - 1, y + 1] = DARK

    for x, y in [(2, 14), (13, 3)]:
        pixels[x, y] = DEEP

    pixels[5, 12] = LIGHT
    pixels[9, 7] = LIGHT
    pixels[11, 5] = shift(LIGHT, 10, 8, 3)
    return img


def write_texture(path: Path, image: Image.Image) -> None:
    save_backup(path)
    image.save(path)


def main() -> None:
    BLOCK_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_DIR.mkdir(parents=True, exist_ok=True)

    write_texture(BLOCK_DIR / "log_fine.png", generate_log_side())
    write_texture(BLOCK_DIR / "log_fine_top.png", generate_log_top())
    write_texture(BLOCK_DIR / "planks_fine.png", generate_planks())
    write_texture(ITEM_DIR / "fine_wood_stick.png", generate_stick())

    print("Updated fine wood textures.")


if __name__ == "__main__":
    main()
