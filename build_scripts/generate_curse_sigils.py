"""The glowing hieroglyph sigils the tomb curses are drawn with: a 4x2 atlas of 32x32 cells.

    python build_scripts/generate_curse_sigils.py

White on black (premultiplied, alpha too), so the renderer tints each one the colour of its curse, and drawn with a soft
glow round every line, so under the additive blend (and the mod's bloom pass) a sigil reads as light,
not as a sticker. Each glyph is drawn at 8x with real strokes, then reduced: at 32 px a hand-placed
pixel glyph looks like a font, and these should look like fire written in the air.

Cells, left to right, top row then bottom: ankh, Eye of Horus, scarab, was sceptre, djed pillar,
feather of Maat, uraeus cobra, shen ring. The order is the renderer's sigil index.
"""
import math
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets',
                   'hogwartsmagic', 'textures', 'fx', 'curse_sigils.png')
CELL = 32
S = 8                      # supersampling
W = CELL * S
LINE = int(1.8 * S)        # stroke width at full size


def canvas():
    return Image.new('L', (W, W), 0)


def P(x, y):
    """Cell coordinates 0..32 to supersampled pixels."""
    return (x * S, y * S)


def ankh(d):
    d.ellipse([P(11, 3), P(21, 14)], outline=255, width=LINE)
    d.line([P(16, 14), P(16, 29)], fill=255, width=LINE)
    d.line([P(8, 16), P(24, 16)], fill=255, width=LINE)


def eye(d):
    d.arc([P(5, 6), P(27, 26)], 200, 340, fill=255, width=LINE)          # upper lid
    d.arc([P(7, 10), P(25, 22)], 20, 160, fill=255, width=LINE)          # lower lid
    d.ellipse([P(13, 11), P(19, 17)], fill=255)                          # iris
    d.line([P(6, 7), P(26, 5)], fill=255, width=int(LINE * 0.8))         # brow
    d.line([P(13, 19), P(11, 28)], fill=255, width=int(LINE * 0.8))      # the tear line
    d.arc([P(17, 18), P(26, 28)], 180, 360, fill=255, width=int(LINE * 0.8))  # the curl


def scarab(d):
    d.ellipse([P(10, 9), P(22, 26)], outline=255, width=LINE)            # wing cases
    d.line([P(16, 10), P(16, 25)], fill=255, width=int(LINE * 0.7))
    d.ellipse([P(12, 3), P(20, 9)], outline=255, width=LINE)             # head
    for y, dx in ((13, 5), (18, 6), (23, 5)):                            # legs
        d.line([P(10, y), P(10 - dx, y - 2)], fill=255, width=int(LINE * 0.7))
        d.line([P(22, y), P(22 + dx, y - 2)], fill=255, width=int(LINE * 0.7))


def was(d):
    d.line([P(15, 8), P(15, 28)], fill=255, width=LINE)
    d.line([P(15, 28), P(12, 30)], fill=255, width=LINE)
    d.line([P(15, 28), P(18, 30)], fill=255, width=LINE)
    d.line([P(15, 8), P(20, 4)], fill=255, width=LINE)                   # the animal head
    d.line([P(20, 4), P(23, 7)], fill=255, width=LINE)
    d.line([P(15, 8), P(12, 6)], fill=255, width=int(LINE * 0.8))


def djed(d):
    d.line([P(16, 6), P(16, 29)], fill=255, width=LINE)
    for y in (6, 10, 14, 18):
        d.line([P(9, y), P(23, y)], fill=255, width=int(LINE * 0.9))
    d.line([P(11, 29), P(21, 29)], fill=255, width=LINE)


def feather(d):
    d.line([P(16, 29), P(16, 4)], fill=255, width=int(LINE * 0.8))
    d.arc([P(9, 3), P(23, 30)], 270, 450, fill=255, width=LINE)
    d.arc([P(13, 2), P(23, 8)], 180, 360, fill=255, width=LINE)
    for y in range(9, 27, 4):
        d.line([P(16, y), P(21, y - 2)], fill=255, width=int(LINE * 0.6))


def uraeus(d):
    d.ellipse([P(11, 4), P(21, 15)], outline=255, width=LINE)            # the raised hood
    d.ellipse([P(14, 7), P(18, 11)], fill=255)
    d.arc([P(9, 13), P(23, 27)], 270, 90, fill=255, width=LINE)          # the body coiling down
    d.arc([P(9, 20), P(23, 31)], 90, 270, fill=255, width=LINE)


def shen(d):
    d.ellipse([P(8, 4), P(24, 20)], outline=255, width=LINE)
    d.line([P(8, 24), P(24, 24)], fill=255, width=LINE)
    d.line([P(12, 19), P(12, 24)], fill=255, width=int(LINE * 0.8))
    d.line([P(20, 19), P(20, 24)], fill=255, width=int(LINE * 0.8))


GLYPHS = [ankh, eye, scarab, was, djed, feather, uraeus, shen]


def cell(draw_fn):
    core = canvas()
    draw_fn(ImageDraw.Draw(core))
    core = core.resize((CELL * 2, CELL * 2), Image.LANCZOS)
    # The glow: the strokes blurred wide and soft, kept under the strokes themselves.
    halo = Image.eval(core.filter(ImageFilter.GaussianBlur(3.0)), lambda v: int(v * 0.75))
    return ImageChops.lighter(core, halo).resize((CELL, CELL), Image.LANCZOS)


def main():
    atlas = Image.new('RGBA', (CELL * 4, CELL * 2), (0, 0, 0, 0))
    for i, fn in enumerate(GLYPHS):
        a = cell(fn)
        # White light, premultiplied: the shape is in the colour as well as the alpha. The glow render type
        # adds colour and ignores alpha, so a texture white everywhere drew every sigil as a solid square.
        rgb = Image.merge('RGB', [a] * 3)
        tile = Image.merge('RGBA', (*rgb.split(), a))
        atlas.paste(tile, ((i % 4) * CELL, (i // 4) * CELL))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    atlas.save(OUT)
    print('wrote', OUT)


if __name__ == '__main__':
    main()
