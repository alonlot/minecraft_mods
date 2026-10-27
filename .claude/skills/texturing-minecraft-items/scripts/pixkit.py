"""Hand-authored pixel sprites for Minecraft items.

Draw the sprite as a 16-row character grid and map each character to a colour.
Every pixel is a decision you made, which is what separates item art from
procedural mush. Import this from a per-item script:

    import sys; sys.path.insert(0, r"<skill>/scripts")
    from pixkit import sprite, ramp, save

    W = ramp("#8a5a2b", 4)                 # darkest .. lightest
    P = {".": None, "k": "#2b1a0e", "1": W[0], "2": W[1], "3": W[2], "4": W[3]}
    GRID = '''
    ................
    ...  (16 rows of 16 characters)
    '''
    save(sprite(GRID, P), "out.png")

Every shade gets its own single character, so the grid reads like the sprite.
"""
import colorsys
from PIL import Image

TRANSPARENT = (0, 0, 0, 0)


def hexc(c):
    """'#rrggbb' or (r,g,b[,a]) -> (r,g,b,255)."""
    if c is None:
        return TRANSPARENT
    if isinstance(c, str):
        c = c.lstrip("#")
        return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), 255)
    return tuple(c[:3]) + (255,)


def tohex(c):
    return "#%02x%02x%02x" % tuple(c[:3])


def _hue_toward(h, target, amount):
    """Rotate hue h (0..1) toward target (0..1) by `amount` (0..1 of a turn) along the short way."""
    d = ((target - h + 0.5) % 1.0) - 0.5
    step = max(-abs(amount), min(abs(amount), d))
    return (h + step) % 1.0


def ramp(base, n=5, spread=0.62, hue_shift=0.025, sat_curve=0.18):
    """Colour ramp the way vanilla builds them, darkest first, `base` in the middle.

    Measured on the 1.21 jar: values step 12-16 (of 100) per shade, saturation
    RISES toward the shadow and highlights wash toward white, and hue moves
    only 0-20 deg across the whole ramp -- shadows toward blue for cool
    materials, toward red for gold/orange/wood. Generic tutorials' 20 deg per
    swatch looks un-Minecraft.
    spread    -- total value range covered (0..1); 0.6 gives clear 16x16 steps
    hue_shift -- max hue rotation per end, in turns (0.025 = 9 deg)
    """
    r, g, b, _ = hexc(base)
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    mid = (n - 1) / 2
    out = []
    for i in range(n):
        t = (i - mid) / mid if mid else 0.0          # -1 darkest .. +1 lightest
        vv = min(1.0, max(0.03, v + t * spread / 2))
        if s < 0.08:                                  # greys: keep them near-neutral, cool shadows only
            hh, ss = 0.62, s + (0.06 * -t if t < 0 else 0)
        else:
            warm = 0.02 < h < 0.20                    # orange..yellow: gold shadows go red, not blue
            hh = _hue_toward(h, (0.0 if warm else 0.70) if t < 0 else 0.16, abs(t) * hue_shift)
            ss = s + (sat_curve * -t if t < 0 else -sat_curve * 2.2 * t)
        ss = min(1.0, max(0.0, ss))
        rr, gg, bb = colorsys.hsv_to_rgb(hh, ss, vv)
        out.append(tohex((round(rr * 255), round(gg * 255), round(bb * 255))))
    return out


def sprite(grid, palette, size=16):
    """Build an RGBA image from a character grid.

    grid    -- multi-line string; blank lines and surrounding whitespace are
               stripped, every row must be exactly `size` characters
    palette -- {char: '#hex' | None}; None (and '.') is transparent
    Unknown characters raise, so a typo never silently becomes a hole.
    """
    flat = {}
    for k, v in palette.items():
        if isinstance(v, list):
            raise ValueError(f"palette[{k!r}] is a list: give every shade its own character")
        flat[k] = hexc(v)
    flat.setdefault(".", TRANSPARENT)
    rows = [r.strip() for r in grid.strip("\n").splitlines() if r.strip()]
    if len(rows) != size:
        raise ValueError(f"grid has {len(rows)} rows, need {size}")
    img = Image.new("RGBA", (size, size), TRANSPARENT)
    px = img.load()
    for y, row in enumerate(rows):
        if len(row) != size:
            raise ValueError(f"row {y} has {len(row)} chars, need {size}: {row!r}")
        for x, ch in enumerate(row):
            if ch not in flat:
                raise ValueError(f"row {y} col {x}: char {ch!r} not in palette")
            px[x, y] = flat[ch]
    return img


def outline(img, color=None, darken=0.45, diagonal=False):
    """Add a 1px outline on transparent pixels touching the sprite.

    color=None draws a *selective* outline: each outline pixel is the touching
    fill colour darkened by `darken` (value) and shifted cool, the way vanilla
    outlines a gold ingot in dark brown rather than black. Use diagonal=False
    (4-neighbour) for vanilla-like clean corners.
    Only for quick blocking-in -- hand-placed outline pixels in the grid beat it.
    """
    w, h = img.size
    src = img.load()
    out = img.copy()
    dst = out.load()
    nb = [(1, 0), (-1, 0), (0, 1), (0, -1)] + ([(1, 1), (-1, -1), (1, -1), (-1, 1)] if diagonal else [])
    for y in range(h):
        for x in range(w):
            if src[x, y][3]:
                continue
            touch = [src[x + dx, y + dy] for dx, dy in nb
                     if 0 <= x + dx < w and 0 <= y + dy < h and src[x + dx, y + dy][3]]
            if not touch:
                continue
            if color is not None:
                dst[x, y] = hexc(color)
                continue
            r, g, b = (sum(c[i] for c in touch) // len(touch) for i in range(3))
            hh, ss, vv = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            hh = _hue_toward(hh, 0.70, 0.03)
            rr, gg, bb = colorsys.hsv_to_rgb(hh, min(1, ss + 0.15), vv * darken)
            dst[x, y] = (round(rr * 255), round(gg * 255), round(bb * 255), 255)
    return out


def mirror_x(grid):
    """Mirror the left 8 columns of each row onto the right (symmetric items: amulets, bottles, crowns)."""
    rows = [r.strip() for r in grid.strip("\n").splitlines() if r.strip()]
    return "\n".join(r[:len(r) // 2] + r[:len(r) // 2][::-1] for r in rows)


def recolor(img, mapping):
    """Swap exact colours {'#old': '#new'} -- palette variants (house colours, tiers) from one master sprite."""
    m = {hexc(k)[:3]: hexc(v) for k, v in mapping.items()}
    out = img.copy()
    px = out.load()
    for y in range(out.size[1]):
        for x in range(out.size[0]):
            c = px[x, y]
            if c[3] and c[:3] in m:
                px[x, y] = m[c[:3]]
    return out


def overlay(base, grid, palette, origin=(0, 0)):
    """Paint a smaller character grid onto a copy of `base` at `origin`; '.' leaves base showing.

    For families whose members differ by an emblem (house crests, tiers): one shared
    base sprite plus one small grid per member. Keep a 1px gap between an emblem and
    a frame of the same colour, or the two merge at 1x.
    """
    out = base.copy()
    px = out.load()
    rows = [r.strip() for r in grid.strip("\n").splitlines() if r.strip()]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch not in palette:
                raise ValueError(f"overlay row {y} col {x}: char {ch!r} not in palette")
            px[origin[0] + x, origin[1] + y] = hexc(palette[ch])
    return out


def save(img, path):
    """Save as a plain RGBA PNG, refusing anything that would not be a clean item sprite."""
    px = img.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            a = px[x, y][3]
            if 0 < a < 255:
                raise ValueError(f"semi-transparent pixel at {x},{y} (alpha {a}); items must be 0 or 255")
    img.save(path, optimize=True)
    return path
