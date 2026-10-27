"""Spellbook item sprites: one master book, recoloured per spell, with a cover emblem.

The master follows the format of the mod's original painted spellbooks, redrawn
as a true 16x16 sprite: a book lying at 3/4 view, cover tilted up-left, spine
down the lower-left edge with two metal hinge clasps and a gem between them,
cream page block along the lower-right edge, glowing emblem mid-cover.

    from spellbook import Spell, build, save_book
    save_book(Spell(cover="#2a5a9a", emblem="shield", glow="#d8ecff"), "out.png")

    python spellbook.py --sheet out.png     every library emblem on one sheet
"""
import colorsys, os, sys
from dataclasses import dataclass, field
from typing import Optional

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                                "texturing-minecraft-items", "scripts"))
from pixkit import sprite, ramp, hexc, save  # noqa: E402

# k outline; 1-4 cover ramp dark..light (4 = lit rim on the lower-left edge)
# S/s/t spine light/mid/dark; m/n metal clasp light/dark; j gem
# p/q/r page light/line/shadow
MASTER = """
.......kk.......
.....kk43k......
...kk43222k.....
.kk43222221k....
.S4322222221k...
.sm4222222221k..
.tmm4222222211k.
.knmS4222222111k
..knjS4222211kkp
...ktjm4221kkppq
....ktmm4kkppqqr
.....knmSppqqrrk
......knsqqrrkk.
.......ktrrkk...
........kkk.....
................
"""

# Cover cells an emblem may paint over (everything else -- outline, rim, spine,
# pages -- is protected so every book keeps the same silhouette).
COVER_CELLS = set("123")
EMBLEM_ORIGIN = {5: (6, 3), 7: (5, 3)}       # emblem width -> top-left cover cell

# Emblems are 5x5 (or 7x5) on the tilted cover. a = emblem colour, b = its
# shadow/second colour, c = hot core (near-white). Keep them to one bold shape:
# at 16x16 a 5x5 glyph is ~20 pixels, so draw a silhouette, not a scene.
EMBLEMS = {
    "star":         ["..a..", ".aca.", "aacaa", ".aaa.", ".a.a."],
    "burst":        ["a.a.a", ".aca.", "accca", ".aca.", "a.a.a"],
    "flame":        ["..a..", ".aa..", ".aca.", "accaa", ".aca."],
    "shield":       ["aaaaa", "abcba", "abcba", ".aba.", "..a.."],
    "shield_star":  ["aaaaa", "aacaa", "acccb", ".aca.", "..a.."],
    "key":          ["aa...", "ac...", ".a...", ".aa..", ".a..."],
    "key_wide":     [".aa....", "a..aaaa", ".aa.a.a", ".......", "......."],
    "lock":         [".aaa.", "a...a", "aaaaa", "aacaa", "abbba"],
    "chain_lock":   ["b.b.b.b", ".aaaaa.", "babcbab", ".aaaaa.", "b.b.b.b"],
    "drop":         ["..a..", ".aa..", "acaa.", "aaaab", ".abb."],
    "wave":         [".......", ".aa..aa", "a..aa..", ".......", "......."],
    "up":           ["..a..", ".aca.", "a.c.a", "..a..", "..a.."],
    "down":         ["..a..", "..a..", "a.c.a", ".aca.", "..a.."],
    "arrow_in":     ["aaa..", "ac...", "a.a..", "...a.", "....b"],
    "arrow_out":    ["b....", ".a...", "..a.a", "...ca", "..aaa"],
    "halt":         ["aaaaa", ".....", ".aca.", "..a..", "aaaaa"],
    "skull":        [".aaa.", "aaaaa", "abab a".replace(" ", ""), ".aaa.", ".a.a."],
    "dark_mark":    [".aaa.", "abab.", ".aaab", "..bb.", ".bb.."],
    "vortex":       [".aaa.", "a...a", "a.c.a", "a..a.", ".aa.."],
    "spiral":       ["aaaaa", "....a", ".aa.a", ".a..a", ".aaaa"],
    "bolt":         ["...a.", "..aa.", ".aca.", ".aa..", ".a..."],
    "slash":        ["....c", "...a.", "..a..", ".a...", "a...."],
    "cuts":         ["a.a.a", ".a.a.", "a.a.a", ".....", "....."],
    "cross_blades": ["a...a", ".a.a.", "..c..", ".b.b.", "b...b"],
    "stag":         ["a...a", "aa.aa", ".aaa.", "..a..", ".aaa."],
    "wand":         ["....c", "...a.", "..a..", ".b...", "b...."],
    "snowflake":    ["a.a.a", ".aca.", "accca", ".aca.", "a.a.a"],
    "heart":        ["aa.aa", "acaaa", "aaaaa", ".aaa.", "..a.."],
    "plus":         ["..a..", "..a..", "aacaa", "..a..", "..a.."],
    "eye":          [".aaa.", "abcba", "abcba", ".aaa.", "....."],
    "eye_wide":     [".aaaaa.", "abbcbba", ".aaaaa.", ".......", "......."],
    "dome":         [".aaa.", "a...a", "a.c.a", "a...a", "aaaaa"],
    "rope":         [".aaa.", "ab.ba", "a.a.a", "ab.ba", ".aaa."],
    "dangle":       ["..a..", ".aaa.", "..a..", ".a.a.", "a...a"],
    "figure":       ["..a..", ".aaa.", "a.a.a", "..a..", ".a.a."],
    "knight":       [".aaa.", "abcba", ".aaa.", "aaaaa", ".a.a."],
    "orb":          [".aaa.", "accca", "accca", "accca", ".aaa."],
    "sparkle":      ["a...a", ".a.a.", "..c..", ".a.a.", "a...a"],
    "fog":          [".aa..", "aaaa.", "..aaa", "aaa..", ".aaaa"],
    "hammer":       ["aaaa.", "aaaab", ".ab..", ".ab..", ".ab.."],
    "grin":         ["a...a", ".....", "a...a", ".aaa.", "....."],
    "snake":        [".aac.", "a....", ".aa..", "...a.", "aaa.."],
    "serpent":      [".aa..", "a..ac", ".aa..", "...a.", ".aa.."],
    "wing":         ["....a", "..aaa", "aaaab", ".abb.", "..b.."],
    "dark_wing":    ["a....", "aaa..", "baaaa", ".bba.", "..b.."],
    "feather":      ["...aa", "..aca", ".aca.", "aca..", "a...."],
    "hourglass":    ["aaaaa", ".aca.", "..a..", ".aca.", "aaaaa"],
    "moon":         [".aa..", "a....", "a....", "a....", ".aa.."],
}


@dataclass
class Spell:
    cover: str                    # cover leather base colour (the ramp midtone)
    emblem: str                   # key into EMBLEMS, or a list of rows
    glow: str                     # emblem colour 'a'
    shade: Optional[str] = None   # emblem second colour 'b' (default: glow darkened)
    core: str = "#ffffff"         # emblem hot core 'c'
    gem: Optional[str] = None     # spine gem (default: glow)
    metal: str = "silver"         # clasps: silver | gold | iron | bronze
    halo: float = 0.0             # 0..1 tint of the emblem colour on cover cells around it (glow)
    emboss: bool = True           # cover cell below-right of each emblem pixel darkens: pressed-in look
    pages: str = "cream"          # cream | aged | dark
    notes: str = field(default="", repr=False)


METALS = {
    "silver": ("#e2e6ee", "#8a909c"),
    "gold":   ("#f6d25a", "#a8701a"),
    "iron":   ("#a4a6ac", "#55575e"),
    "bronze": ("#d89a5a", "#7a4a22"),
}
PAGES = {
    "cream": ("#efe6cb", "#c4b48e", "#8f7c5c"),
    "aged":  ("#d8c79a", "#a8925e", "#6e5a38"),
    "dark":  ("#9a9488", "#6e685e", "#45413a"),
}


def _shift(hex_, dv, ds=0.0):
    r, g, b, _ = hexc(hex_)
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    rr, gg, bb = colorsys.hsv_to_rgb(h, min(1, max(0, s + ds)), min(1, max(0, v + dv)))
    return "#%02x%02x%02x" % (round(rr * 255), round(gg * 255), round(bb * 255))


def _floor(hex_, vmin=0.09):
    r, g, b, _ = hexc(hex_)
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    if v >= vmin:
        return hex_
    rr, gg, bb = colorsys.hsv_to_rgb(h, s, vmin)
    return "#%02x%02x%02x" % (round(rr * 255), round(gg * 255), round(bb * 255))


def _mix(a, b, t):
    a, b = hexc(a), hexc(b)
    return "#%02x%02x%02x" % tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def palette(spell):
    c = ramp(spell.cover, 5, spread=0.42)            # c[0] darkest .. c[4] lightest
    mt = METALS[spell.metal]
    pg = PAGES[spell.pages]
    return {
        ".": None,
        "k": _floor(_shift(c[0], -0.06, 0.05)), "1": c[1], "2": c[2], "3": c[3], "4": c[4],
        "S": c[2], "s": c[1], "t": c[0],
        "m": mt[0], "n": mt[1],
        "j": spell.gem or spell.glow,
        "p": pg[0], "q": pg[1], "r": pg[2],
    }


def build(spell):
    pal = palette(spell)
    img = sprite(MASTER, pal)
    rows = EMBLEMS[spell.emblem] if isinstance(spell.emblem, str) else spell.emblem
    w = len(rows[0])
    if w not in EMBLEM_ORIGIN or len(rows) != 5 or any(len(r) != w for r in rows):
        raise ValueError(f"emblem must be 5 rows of 5 or 7 chars: {rows}")
    ox, oy = EMBLEM_ORIGIN[w]
    grid = [r.strip() for r in MASTER.strip("\n").splitlines()]
    colours = {"a": spell.glow, "b": spell.shade or _shift(spell.glow, -0.28, 0.1), "c": spell.core}
    px = img.load()
    painted = set()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            gx, gy = ox + x, oy + y
            if ch == "." or grid[gy][gx] not in COVER_CELLS:
                continue
            px[gx, gy] = hexc(colours[ch])
            painted.add((gx, gy))
    if spell.emboss:
        for gx, gy in painted:
            hx, hy = gx + 1, gy + 1
            if (hx, hy) not in painted and grid[hy][hx] in COVER_CELLS:
                px[hx, hy] = hexc(pal["1"])
    if spell.halo:
        halo = hexc(_mix(pal["2"], spell.glow, spell.halo))
        for gx, gy in painted:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                hx, hy = gx + dx, gy + dy
                if (hx, hy) not in painted and grid[hy][hx] in COVER_CELLS and px[hx, hy] != hexc(pal["1"]):
                    px[hx, hy] = halo
    return img


def save_book(spell, path):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    return save(build(spell), path)


def sheet(out):
    from PIL import Image, ImageDraw
    names = sorted(EMBLEMS)
    cols = 8
    cell = 16 * 6
    im = Image.new("RGB", (cols * (cell + 8), ((len(names) + cols - 1) // cols) * (cell + 20)), (139, 139, 139))
    d = ImageDraw.Draw(im)
    for i, n in enumerate(names):
        b = build(Spell(cover="#2e3f6e", emblem=n, glow="#9fe0ff")).resize((cell, cell), Image.NEAREST)
        x, y = (i % cols) * (cell + 8), (i // cols) * (cell + 20)
        im.paste(b, (x, y), b)
        d.text((x, y + cell + 2), n, fill=(0, 0, 0))
    im.save(out)
    print(out)


if __name__ == "__main__":
    if sys.argv[1:2] == ["--sheet"]:
        sheet(sys.argv[2])
