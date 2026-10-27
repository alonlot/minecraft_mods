"""Read a painted spellbook (any size) and suggest Spell colours from it.

    python sample_colours.py book.png [...]

cover = median of the dark-to-mid, saturated pixels (the leather);
glow  = median of the brightest saturated pixels (the emblem).
Starting points for a Spell(...) -- check them on the preview, not blindly.
"""
import colorsys, sys
from PIL import Image


def median(cols):
    if not cols:
        return None
    return tuple(sorted(c[i] for c in cols)[len(cols) // 2] for i in range(3))


def sample(path):
    im = Image.open(path).convert("RGBA")
    im.thumbnail((128, 128), Image.BOX)
    cover, glow = [], []
    for r, g, b, a in im.getdata():
        if a < 200:
            continue
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        if 0.15 < v < 0.55 and s > 0.25:
            cover.append((r, g, b))
        elif v > 0.8 and s > 0.3:
            glow.append((r, g, b))
    hx = lambda c: "#%02x%02x%02x" % c if c else None
    return hx(median(cover)), hx(median(glow))


if __name__ == "__main__":
    for p in sys.argv[1:]:
        c, g = sample(p)
        print(f"{p}: cover={c} glow={g}")
