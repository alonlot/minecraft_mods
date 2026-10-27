"""Preview item sprites the way a player sees them, next to vanilla.

    python preview.py out.png a.png b.png ... [--ref diamond,potion,book] [--vanilla DIR]

Each row: the sprite at true GUI size (32px = GUI scale 2) on an inventory
slot, on the dark hotbar and on a sky-blue world background, then an 8x
zoom with a pixel grid for judging individual pixels. --ref adds vanilla
sprites (from vanilla_refs.py's output dir) as rows to judge style against:
same canvas use, same outline weight, same contrast?
"""
import os, sys
from PIL import Image, ImageDraw

SLOT, SLOT_DARK, SLOT_LIGHT = (139, 139, 139), (55, 55, 55), (255, 255, 255)
HOTBAR = (24, 24, 24)
SKY = (120, 167, 255)
DEFAULT_VANILLA = os.path.join(os.environ.get("TEMP", "/tmp"), "mc_vanilla_items")


def slot(img, scale):
    s = 16 * scale
    bg = Image.new("RGB", (s + 2 * scale, s + 2 * scale), SLOT)
    d = ImageDraw.Draw(bg)
    e = s + 2 * scale - 1
    d.rectangle([0, 0, e, scale - 1], fill=SLOT_DARK); d.rectangle([0, 0, scale - 1, e], fill=SLOT_DARK)
    d.rectangle([0, e - scale + 1, e, e], fill=SLOT_LIGHT); d.rectangle([e - scale + 1, 0, e, e], fill=SLOT_LIGHT)
    big = img.resize((s, s), Image.NEAREST)
    bg.paste(big, (scale, scale), big)
    return bg


def flat(img, scale, color):
    s = 16 * scale
    bg = Image.new("RGB", (s + 4, s + 4), color)
    big = img.resize((s, s), Image.NEAREST)
    bg.paste(big, (2, 2), big)
    return bg


def zoom(img, z=8):
    w, h = img.size
    bg = Image.new("RGB", (w * z, h * z), (0, 0, 0))
    d = ImageDraw.Draw(bg)
    for y in range(h):              # checkerboard shows transparency honestly
        for x in range(w):
            c = (70, 70, 70) if (x + y) % 2 else (90, 90, 90)
            d.rectangle([x * z, y * z, x * z + z - 1, y * z + z - 1], fill=c)
    big = img.resize((w * z, h * z), Image.NEAREST)
    bg.paste(big, (0, 0), big)
    for i in range(0, w * z, z):
        d.line([(i, 0), (i, h * z)], fill=(0, 0, 0))
    for j in range(0, h * z, z):
        d.line([(0, j), (w * z, j)], fill=(0, 0, 0))
    return bg


def load(path):
    im = Image.open(path).convert("RGBA")
    w, h = im.size
    if h > w:
        im = im.crop((0, 0, w, w))          # first animation frame
    if im.size != (16, 16):
        im = im.resize((16, 16), Image.NEAREST)   # show oversized art as the 16px it competes with
    return im


def main(argv):
    out, rest = argv[0], argv[1:]
    refs, vdir, files = [], DEFAULT_VANILLA, []
    i = 0
    while i < len(rest):
        if rest[i] == "--ref":
            refs = rest[i + 1].split(","); i += 2
        elif rest[i] == "--vanilla":
            vdir = rest[i + 1]; i += 2
        else:
            files.append(rest[i]); i += 1
    rows = [(os.path.basename(f)[:-4], load(f)) for f in files]
    for r in refs:
        p = os.path.join(vdir, r + ".png")
        if os.path.exists(p):
            rows.append(("vanilla:" + r, load(p)))
        else:
            print("no vanilla ref", p)
    rowh = 16 * 8 + 22
    W = 4 + 36 + 8 + 36 + 8 + 36 + 8 + 128 + 8 + 70 + 8
    sheet = Image.new("RGB", (W, rowh * len(rows) + 4), (30, 30, 30))
    d = ImageDraw.Draw(sheet)
    for k, (name, im) in enumerate(rows):
        d.text((4, 4 + k * rowh), name[:60], fill=(255, 220, 120) if name.startswith("vanilla") else (255, 255, 255))
        y = 4 + k * rowh + 16
        x = 4
        for tile in (slot(im, 2), flat(im, 2, HOTBAR), flat(im, 2, SKY)):
            sheet.paste(tile, (x, y)); x += tile.size[0] + 8
        z = zoom(im)
        sheet.paste(z, (x, y)); x += z.size[0] + 8
        sheet.paste(slot(im, 4), (x, y))
    sheet.save(out)
    print(out, sheet.size)


if __name__ == "__main__":
    main(sys.argv[1:])
