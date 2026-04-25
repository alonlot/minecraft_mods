from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parent
BLOCK_DIR = ROOT / "src/main/resources/assets/hogwartsmagic/textures/block"
ITEM_DIR = ROOT / "src/main/resources/assets/hogwartsmagic/textures/item"

SCALE = 4
TILE = 16
SIZE = TILE * SCALE


def rgba(hex_color, alpha=255):
    hex_color = hex_color.lstrip("#")
    return tuple(int(hex_color[i:i + 2], 16) for i in (0, 2, 4)) + (alpha,)


def make_canvas():
    return Image.new("RGBA", (TILE, TILE), (0, 0, 0, 0))


def save_scaled(img, dest):
    dest.parent.mkdir(parents=True, exist_ok=True)
    img.resize((SIZE, SIZE), Image.Resampling.NEAREST).save(dest)


def draw_planks(img, base, dark, light, seam, horizontal=True):
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            stripe = y // 4 if horizontal else x // 4
            tone = [base, light, base, dark][stripe % 4]
            r, g, b, a = tone
            grain = ((x * 7 + y * 11 + stripe * 13) % 9) - 4
            px[x, y] = (
                max(0, min(255, r + grain)),
                max(0, min(255, g + grain)),
                max(0, min(255, b + grain)),
                a,
            )

    draw = ImageDraw.Draw(img)
    if horizontal:
        for y in (3, 7, 11):
            draw.line((0, y, TILE - 1, y), fill=seam)
    else:
        for x in (3, 7, 11):
            draw.line((x, 0, x, TILE - 1), fill=seam)


def add_border(draw, color):
    draw.rectangle((0, 0, TILE - 1, TILE - 1), outline=color)


def draw_wand_top():
    img = make_canvas()
    draw_planks(
        img,
        rgba("#7F552C"),
        rgba("#5A3518"),
        rgba("#A67445"),
        rgba("#4A2B13"),
        horizontal=True,
    )
    draw = ImageDraw.Draw(img)

    draw.rectangle((2, 2, 13, 13), fill=rgba("#9B6B3F"), outline=rgba("#D6B673"))
    draw.rectangle((4, 3, 11, 12), fill=rgba("#E6D8B6"), outline=rgba("#8B6A3D"))

    for x, y in ((4, 3), (11, 3), (4, 12), (11, 12)):
        draw.point((x, y), fill=rgba("#F6DA7A"))

    glow = rgba("#8FD6FF")
    rune = rgba("#62C0FF")
    gold = rgba("#E3C56F")

    draw.arc((3, 4, 12, 11), start=25, end=155, fill=gold)
    draw.arc((3, 4, 12, 11), start=205, end=335, fill=gold)
    draw.line((5, 8, 11, 8), fill=gold)
    draw.arc((4, 5, 11, 10), start=45, end=135, fill=glow)
    draw.arc((4, 5, 11, 10), start=225, end=315, fill=glow)

    wand = [
        (3, 11), (4, 10), (5, 9), (6, 8), (7, 7), (8, 7), (9, 6), (10, 5), (11, 4)
    ]
    for x, y in wand:
        draw.point((x, y), fill=rgba("#4B2C17"))
    draw.point((8, 7), fill=rgba("#C89B61"))
    draw.point((9, 6), fill=rgba("#DAB071"))
    draw.point((11, 4), fill=rgba("#FFD981"))

    for x, y in ((7, 5), (9, 9), (6, 10), (5, 6), (10, 10)):
        draw.point((x, y), fill=rgba("#F3E7AA"))

    for x, y in ((6, 4), (8, 4), (6, 11), (8, 11), (4, 8), (11, 8)):
        draw.point((x, y), fill=rune)
    draw.point((7, 6), fill=glow)
    draw.point((8, 9), fill=glow)

    add_border(draw, rgba("#3C220F"))
    return img


def draw_wand_side():
    img = make_canvas()
    draw_planks(
        img,
        rgba("#6D4622"),
        rgba("#4F2F16"),
        rgba("#8E6136"),
        rgba("#3A210F"),
        horizontal=True,
    )
    draw = ImageDraw.Draw(img)

    draw.rectangle((1, 1, 14, 3), fill=rgba("#4B2A13"))
    draw.line((1, 3, 14, 3), fill=rgba("#D4AF68"))

    draw.rectangle((2, 5, 13, 13), fill=rgba("#5A3419"), outline=rgba("#B4874C"))
    draw.line((7, 5, 7, 13), fill=rgba("#3D2311"))
    draw.line((2, 9, 13, 9), fill=rgba("#3D2311"))

    for x, y in ((4, 7), (10, 7), (4, 11), (10, 11)):
        draw.ellipse((x, y, x + 1, y + 1), fill=rgba("#DABA6D"))

    draw.line((3, 6, 5, 7), fill=rgba("#D8C7A0"))
    draw.line((9, 6, 11, 7), fill=rgba("#D8C7A0"))
    draw.line((3, 10, 5, 11), fill=rgba("#D8C7A0"))
    draw.line((9, 10, 11, 11), fill=rgba("#D8C7A0"))

    for x, y in ((6, 6), (8, 6), (6, 12), (8, 12)):
        draw.point((x, y), fill=rgba("#71C8FF"))
    draw.line((6, 6, 8, 6), fill=rgba("#71C8FF"))
    draw.line((6, 12, 8, 12), fill=rgba("#71C8FF"))

    add_border(draw, rgba("#311A0C"))
    return img


def draw_wand_bottom():
    img = make_canvas()
    draw_planks(
        img,
        rgba("#65401E"),
        rgba("#442811"),
        rgba("#82572D"),
        rgba("#351C0D"),
        horizontal=False,
    )
    draw = ImageDraw.Draw(img)
    draw.rectangle((0, 0, TILE - 1, TILE - 1), outline=rgba("#2A160A"))
    return img


def draw_broom_top():
    img = make_canvas()
    draw_planks(
        img,
        rgba("#72502A"),
        rgba("#4B3017"),
        rgba("#94683A"),
        rgba("#3A220F"),
        horizontal=True,
    )
    draw = ImageDraw.Draw(img)

    draw.rectangle((2, 2, 13, 13), fill=rgba("#8B6235"), outline=rgba("#C49B60"))
    draw.line((3, 12, 10, 5), fill=rgba("#5B3518"))
    draw.line((4, 12, 11, 5), fill=rgba("#6A3F1B"))
    draw.line((5, 12, 12, 5), fill=rgba("#73471F"))

    for x in range(9, 14):
        draw.line((x, 4, x + 1, 2), fill=rgba("#C4A061"))
        draw.line((x, 5, x + 1, 3), fill=rgba("#E0C07B"))

    draw.line((7, 8, 9, 6), fill=rgba("#C7A15E"))
    draw.line((6, 9, 8, 7), fill=rgba("#C7A15E"))
    draw.line((5, 10, 7, 8), fill=rgba("#C7A15E"))

    draw.rectangle((2, 4, 5, 7), fill=rgba("#B68A51"), outline=rgba("#6D4621"))
    draw.line((3, 5, 4, 6), fill=rgba("#E9D39A"))
    draw.line((4, 5, 3, 6), fill=rgba("#E9D39A"))

    add_border(draw, rgba("#37200E"))
    return img


def draw_broom_side():
    img = make_canvas()
    draw_planks(
        img,
        rgba("#69451F"),
        rgba("#462A12"),
        rgba("#886033"),
        rgba("#341D0D"),
        horizontal=True,
    )
    draw = ImageDraw.Draw(img)

    draw.rectangle((1, 1, 14, 3), fill=rgba("#48280F"))
    draw.line((1, 3, 14, 3), fill=rgba("#C49B60"))

    draw.rectangle((2, 5, 13, 13), fill=rgba("#5A3518"), outline=rgba("#A97B43"))
    draw.line((4, 6, 10, 12), fill=rgba("#6E431D"))
    draw.line((5, 6, 11, 12), fill=rgba("#7F5025"))

    for x in (8, 9, 10, 11):
        draw.line((x, 10, x + 1, 12), fill=rgba("#D4B06E"))

    draw.line((6, 7, 7, 8), fill=rgba("#D7B671"))
    draw.line((7, 8, 8, 9), fill=rgba("#D7B671"))

    for x, y in ((3, 7), (11, 7), (3, 11)):
        draw.ellipse((x, y, x + 1, y + 1), fill=rgba("#D9BE7B"))

    add_border(draw, rgba("#2C180A"))
    return img


def draw_broom_bottom():
    img = make_canvas()
    draw_planks(
        img,
        rgba("#60401F"),
        rgba("#412611"),
        rgba("#7B562E"),
        rgba("#2E180C"),
        horizontal=False,
    )
    draw = ImageDraw.Draw(img)
    draw.rectangle((0, 0, TILE - 1, TILE - 1), outline=rgba("#281509"))
    return img


def draw_broom_item():
    img = Image.new("RGBA", (TILE, TILE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    draw.polygon([(2, 11), (7, 6), (10, 9), (5, 14)], fill=rgba("#8E6232"))
    draw.line((4, 12, 12, 4), fill=rgba("#6F421B"), width=1)
    draw.line((5, 12, 13, 4), fill=rgba("#835023"), width=1)

    for x in range(10, 14):
        draw.line((x, 4, x + 2, 1), fill=rgba("#E2C17B"))
        draw.line((x, 5, x + 2, 2), fill=rgba("#C9A461"))

    draw.line((8, 8, 10, 6), fill=rgba("#D6B36A"))
    draw.line((7, 9, 9, 7), fill=rgba("#D6B36A"))

    return img


def main():
    outputs = {
        BLOCK_DIR / "wand_workbench_top.png": draw_wand_top(),
        BLOCK_DIR / "wand_workbench_side.png": draw_wand_side(),
        BLOCK_DIR / "wand_workbench_bottom.png": draw_wand_bottom(),
        BLOCK_DIR / "broom_workbench_top.png": draw_broom_top(),
        BLOCK_DIR / "broom_workbench_side.png": draw_broom_side(),
        BLOCK_DIR / "broom_workbench_bottom.png": draw_broom_bottom(),
        ITEM_DIR / "broom_workbench.png": draw_broom_item(),
    }

    for path, img in outputs.items():
        save_scaled(img, path)
        print(f"saved {path}")


if __name__ == "__main__":
    main()
