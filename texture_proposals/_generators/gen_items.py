"""Proposed replacements for the flat placeholder item icons.
Everything is hand-placed at 16x16 in vanilla style: light from the upper-left,
a dark (not black) silhouette outline, 3-4 step material ramps."""
import os
from pixlib import Canvas, hx, ramp, mix, shade, shaft

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "proposed", "item")
os.makedirs(OUT, exist_ok=True)

REG = {}


def tex(name, size=16, scale=1):
    def deco(fn):
        cv = Canvas(size, size, seed=abs(hash(name)) % 9999)
        fn(cv)
        im = cv.image()
        if scale > 1:
            im = im.resize((size * scale, size * scale))
        im.save(os.path.join(OUT, name + ".png"))
        REG[name] = True
        return fn
    return deco


# --- shared palettes ---------------------------------------------------------
GLASS_LO = hx("3b4a55")      # glass outline / shadow side
GLASS_MD = hx("7d95a3")
GLASS_HI = hx("cfe2ec")
CORK = ramp("6b4a2a", "8a6438", "a67c48")
DARK = hx("1c1620")


def glass_vial(cv, x0, x1, ytop, ybot, liquid, level, neck_w=2, cork=True):
    """A generic stoppered vial. liquid = [dark, mid, light]."""
    cx = (x0 + x1) // 2
    # body
    cv.rect(x0, ytop + 3, x1, ybot, GLASS_MD)
    # neck
    cv.rect(cx - neck_w // 2, ytop, cx - neck_w // 2 + neck_w - 1, ytop + 3, GLASS_MD)
    # shoulders
    cv.put(x0, ytop + 3, GLASS_MD); cv.put(x1, ytop + 3, GLASS_MD)
    # liquid
    ltop = ybot - level
    cv.rect(x0 + 1, ltop, x1 - 1, ybot - 1, liquid[1])
    cv.hline(x0 + 1, x1 - 1, ltop, liquid[2])
    cv.rect(x0 + 1, ybot - 2, x1 - 1, ybot - 1, liquid[0])
    cv.put(x1 - 1, ltop + 1, liquid[0])
    # glass highlight
    cv.vline(x0 + 1, ytop + 4, ybot - 3, GLASS_HI)
    cv.put(cx - neck_w // 2, ytop + 1, GLASS_HI)
    if cork:
        cv.rect(cx - 1, ytop - 2, cx + 1, ytop - 1, CORK[1])
        cv.hline(cx - 1, cx + 1, ytop - 2, CORK[2])
        cv.put(cx + 1, ytop - 1, CORK[0])
    cv.inner_outline(hx("2a3640"), sides=("r", "b"))
    cv.outline(DARK)


# =============================================================================
@tex("acromantula_venom")
def _(cv):
    """Spider venom: a specimen vial, sickly green, with a hanging drip."""
    ven = ramp("1d5e22", "3d9c2e", "7ede4a")
    glass_vial(cv, 4, 11, 3, 13, ven, 7)
    # venom sheen + a bright bead at the surface
    cv.put(6, 7, hx("b6f77a"))
    cv.put(9, 10, hx("2b7a24"))
    # drip running down the outside of the glass
    cv.put(11, 8, hx("7ede4a")); cv.put(11, 9, hx("3d9c2e"))
    # two fangs etched on the cork band
    cv.put(7, 2, hx("d8d0c0")); cv.put(9, 2, hx("d8d0c0"))


@tex("arcane_focus")
def _(cv):
    """A lens of raw arcane crystal held in a gold mount."""
    gold = ramp("6b4a12", "b3861f", "e8c452", "fff0b0")
    core = ramp("22265e", "3c53c8", "6f9bff", "cfe4ff")
    # gold ring
    cv.ring(8, 8, 6.4, gold[1], t=1.6)
    cv.ring(8, 8, 6.4, gold[2], t=0.9)
    # top-left of the ring catches the light, bottom-right falls off
    for x in range(16):
        for y in range(16):
            if cv.solid(x, y) and (x + y) > 17:
                cv.put(x, y, gold[0])
            elif cv.solid(x, y) and (x + y) < 10:
                cv.put(x, y, gold[3])
    # crystal core
    cv.disc(8, 8, 4.1, core[1])
    cv.disc(8, 8, 2.6, core[2])
    cv.put(7, 6, core[3]); cv.put(6, 7, core[3]); cv.put(8, 6, core[3])
    cv.put(9, 10, core[0]); cv.put(10, 9, core[0])
    # four mounting claws
    for x, y in ((8, 1), (8, 14), (1, 8), (14, 8)):
        cv.put(x, y, gold[2])
    # arcane sparks
    cv.put(13, 3, hx("cfe4ffcc")); cv.put(3, 12, hx("6f9bffaa"))
    cv.outline(hx("140f1e"))


@tex("cave_inimicum_totem")
def _(cv):
    """Carved warding totem: stone head, rune band, a green protective shimmer."""
    st = ramp("3a3a42", "5c5c66", "82828e", "a8a8b4")
    wd = ramp("3d2a18", "5c4126", "7a5731")
    grn = hx("6ef2a8")
    # post
    cv.rect(6, 9, 9, 15, wd[1])
    cv.vline(6, 9, 15, wd[2]); cv.vline(9, 9, 15, wd[0])
    # head block
    cv.rect(4, 2, 11, 9, st[1])
    cv.rect(4, 2, 11, 2, st[3])
    cv.vline(4, 2, 9, st[2]); cv.vline(11, 2, 9, st[0])
    cv.hline(4, 11, 9, st[0])
    # carved face: hollow eyes + a set mouth
    cv.rect(6, 4, 6, 5, DARK); cv.rect(9, 4, 9, 5, DARK)
    cv.put(6, 4, grn); cv.put(9, 4, grn)
    cv.hline(6, 9, 7, st[0])
    # rune band across the post
    cv.hline(6, 9, 11, grn)
    cv.put(7, 12, hx("2e8f5e")); cv.put(8, 12, hx("2e8f5e"))
    # shoulder wings of the totem
    cv.rect(2, 5, 3, 7, wd[1]); cv.rect(12, 5, 13, 7, wd[1])
    cv.put(2, 5, wd[2]); cv.put(13, 7, wd[0])
    cv.outline(hx("15110d"))


@tex("dragon_heartstring_core")
def _(cv):
    """A wand core: one length of dragon heartstring, coiled and faintly live."""
    s = ramp("5e0d12", "9c1a22", "d43a36", "ff8b6a")
    # an S-curl through the icon
    pts = [(3, 13), (4, 11), (6, 10), (8, 10), (10, 9), (11, 7),
           (10, 5), (8, 4), (6, 4), (4, 3), (3, 2)]
    for i in range(len(pts) - 1):
        cv.line(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], s[1])
    # thicken and light the upper-left of the strand
    for x in range(16):
        for y in range(16):
            if cv.solid(x, y):
                cv.put(x + 1, y, s[1])
    for x in range(16):
        for y in range(16):
            if cv.solid(x, y) and not cv.solid(x, y - 1):
                cv.put(x, y, s[2])
    # sinew fibres catching light
    cv.put(5, 10, s[3]); cv.put(9, 4, s[3]); cv.put(11, 7, s[3])
    cv.put(4, 12, s[0]); cv.put(9, 10, s[0])
    # frayed ends
    cv.put(2, 14, s[0]); cv.put(2, 1, s[0])
    cv.outline(hx("2a070a"))


@tex("enchanted_inkwell")
def _(cv):
    """Glass inkwell, brass collar, a quill standing in it."""
    ink = ramp("101a4a", "1e2f80", "3a56c4")
    br = ramp("6b4a12", "b3861f", "e8c452")
    # quill (drawn first so the well overlaps its base)
    cv.line(11, 1, 8, 8, hx("e8ecf2"))
    cv.line(12, 1, 9, 8, hx("b9c2cf"))
    for i, (x, y) in enumerate([(11, 2), (12, 3), (12, 4), (11, 5), (10, 6)]):
        cv.put(x, y, hx("f4f7fb"))
    cv.put(13, 2, hx("d9e0ea")); cv.put(13, 3, hx("cdd6e2"))
    cv.put(9, 8, hx("2a3550"))  # inked nib
    # well body
    cv.rect(3, 8, 10, 14, hx("2c3a52"))
    cv.rect(4, 9, 9, 13, ink[1])
    cv.rect(4, 9, 9, 10, ink[2])
    cv.rect(4, 12, 9, 13, ink[0])
    cv.vline(4, 9, 13, hx("6d86b8"))  # glass highlight
    # brass collar
    cv.rect(3, 7, 10, 8, br[1])
    cv.hline(3, 10, 7, br[2])
    cv.put(10, 8, br[0])
    # foot
    cv.hline(3, 10, 15, hx("22304a"))
    # enchantment glimmer
    cv.put(6, 10, hx("8fb0ff")); cv.put(2, 6, hx("a98cffcc"))
    cv.outline(hx("0d1020"))


@tex("fine_wood_stick")
def _(cv):
    """A wand blank of fine wood -- pale, straight-grained, squared off."""
    w = ramp("6b4f2c", "9a7845", "c3a066", "e0c48f")
    shaft(cv, 12, 3, 4, 12, [w[2], w[1], w[0]], width=3)
    cv.line(12, 2, 4, 11, w[3])
    # grain flecks
    for x, y in ((10, 5), (8, 7), (6, 9)):
        cv.put(x, y, w[0])
    cv.outline(hx("2a1d10"))


@tex("gillyweed_plant")
def _(cv):
    """The raw weed: a slick, rubbery clump of lake growth."""
    g = ramp("1d3a24", "2f5c33", "4b8443", "7fb85c")
    fronds = [(8, 14, 5, 3), (8, 14, 8, 2), (8, 14, 11, 4),
              (8, 14, 3, 6), (8, 14, 13, 6), (8, 14, 6, 5), (8, 14, 10, 5)]
    for x0, y0, x1, y1 in fronds:
        cv.line(x0, y0, x1, y1, g[1])
        cv.line(x0, y0 - 1, x1, y1 + 1, g[2])
    # holdfast at the base
    cv.rect(6, 13, 10, 15, g[0])
    cv.hline(6, 10, 13, g[1])
    # wet highlights on the up-facing edges
    for x, y in ((5, 4), (8, 3), (11, 5), (4, 7), (12, 7), (6, 6), (10, 6)):
        cv.put(x, y, g[3])
    # a couple of bladders
    cv.put(7, 8, g[3]); cv.put(10, 9, g[3])
    cv.outline(hx("0d1a11"))


@tex("goblin_silver_dagger")
def _(cv):
    """Goblin-wrought silver: narrow leaf blade, notched guard, bound grip."""
    sil = ramp("57606e", "8e99a8", "c3ccd8", "f2f6fb")
    lea = ramp("2b1c12", "48301d", "6b4629")
    # blade, lower-left to upper-right
    shaft(cv, 13, 2, 7, 8, [sil[3], sil[2], sil[1]], width=3)
    cv.line(14, 2, 8, 8, sil[3])   # bright edge
    cv.line(12, 4, 7, 9, sil[0])   # shaded back
    cv.put(14, 1, sil[3]); cv.put(15, 1, sil[2])  # point
    # crossguard, perpendicular to the blade
    cv.line(4, 8, 9, 5, hx("3f4652"))
    cv.line(5, 9, 10, 6, hx("6a7382"))
    cv.put(4, 8, sil[1]); cv.put(10, 6, sil[1])
    # grip with binding
    shaft(cv, 6, 9, 3, 12, [lea[2], lea[1], lea[0]], width=2)
    cv.put(5, 10, hx("8e7346")); cv.put(4, 11, hx("8e7346"))
    # pommel
    cv.rect(2, 12, 3, 13, sil[1])
    cv.put(2, 12, sil[2])
    cv.outline(hx("14161c"))


@tex("golden_snitch")
def _(cv):
    """Struck gold body with an engraved seam; filigree wings mid-beat."""
    g = ramp("6b4a12", "a8791c", "e0b23c", "ffe9a0")
    wg = ramp("6f7d92", "aebccf", "e2ecf7", "ffffff")
    # wings first
    for dx, sgn in ((0, -1), (0, 1)):
        pass
    cv.poly([(6, 7), (1, 3), (0, 6), (2, 8), (5, 8)], wg[1])
    cv.poly([(10, 7), (15, 3), (16, 6), (14, 8), (11, 8)], wg[1])
    cv.line(1, 4, 5, 7, wg[3]); cv.line(14, 4, 10, 7, wg[3])
    cv.line(1, 6, 4, 8, wg[0]); cv.line(14, 6, 11, 8, wg[0])
    cv.put(2, 5, wg[2]); cv.put(13, 5, wg[2])
    cv.put(3, 6, wg[2]); cv.put(12, 6, wg[2])
    # body
    cv.disc(8, 9, 3.4, g[1])
    cv.disc(8, 9, 2.4, g[2])
    cv.put(7, 7, g[3]); cv.put(6, 8, g[3])
    cv.put(9, 11, g[0]); cv.put(10, 10, g[0])
    # engraved seam + hinge
    cv.hline(6, 10, 9, g[0])
    cv.put(5, 9, g[3]); cv.put(11, 9, g[3])
    cv.outline(hx("2b2107"))


@tex("merchieftainess_crown")
def _(cv):
    """Cold-water coral: a spired circlet, sea-tarnished, one pearl at the brow."""
    c = ramp("2f5e5a", "4a8f86", "74bdb0", "b9e7dd")
    # band
    cv.rect(2, 10, 13, 12, c[1])
    cv.hline(2, 13, 10, c[2])
    cv.hline(2, 13, 12, c[0])
    # spires: tall in the middle, shorter outward
    for x, top in ((3, 7), (5, 5), (7, 2), (8, 2), (10, 5), (12, 7)):
        cv.vline(x, top, 9, c[1])
        cv.put(x, top, c[3])
    cv.vline(7, 2, 9, c[2])
    cv.put(8, 3, c[0]); cv.put(10, 6, c[0]); cv.put(12, 8, c[0])
    # pearl
    cv.rect(7, 10, 8, 11, hx("f2f7f4"))
    cv.put(8, 11, hx("bcd6cf"))
    # coral pitting
    cv.put(4, 11, c[0]); cv.put(11, 11, c[0])
    cv.outline(hx("102421"))


@tex("merperson_spear")
def _(cv):
    """Three tarnished prongs lashed to a dark, water-soaked shaft."""
    br = ramp("4a3a1c", "7d6529", "b09144", "dcc077")
    wd = ramp("241a12", "3d2c1d", "574029")
    # shaft
    shaft(cv, 8, 8, 2, 14, [wd[2], wd[1], wd[0]], width=3)
    # prongs fanning out to the upper right
    cv.line(9, 7, 13, 1, br[2]); cv.line(10, 8, 14, 2, br[1])
    cv.line(10, 6, 15, 5, br[2])
    cv.line(7, 5, 9, 1, br[2])
    cv.put(13, 0, br[3]); cv.put(9, 0, br[3]); cv.put(15, 4, br[3])
    # barbs
    cv.put(12, 2, br[0]); cv.put(10, 2, br[0]); cv.put(14, 5, br[0])
    # socket where head meets shaft
    cv.rect(7, 6, 9, 8, br[1])
    cv.put(7, 6, br[2]); cv.put(9, 8, br[0])
    # cord lashing
    cv.put(6, 9, hx("8a7a55")); cv.put(5, 10, hx("6d5f42"))
    cv.outline(hx("0f0b07"))


@tex("moonlit_phial")
def _(cv):
    """Distilled moonlight: a slim phial, silver-blue, glowing from within."""
    liq = ramp("2b4d8c", "5f8fd6", "b9dcff")
    glass_vial(cv, 5, 10, 3, 13, liq, 8, cork=False)
    # silver cap instead of cork
    cv.rect(6, 1, 9, 2, hx("aeb9c9"))
    cv.hline(6, 9, 1, hx("e2e9f2"))
    cv.put(9, 2, hx("77839a"))
    # crescent of light floating in the liquid
    cv.put(7, 7, hx("eaf6ff")); cv.put(8, 6, hx("eaf6ff"))
    cv.put(6, 8, hx("eaf6ff")); cv.put(6, 9, hx("cfe6ff"))
    cv.put(7, 10, hx("cfe6ff"))
    # glow escaping the neck
    cv.put(7, 3, hx("cfe6ff")); cv.put(4, 5, hx("9fc8ff88"))
    cv.put(12, 9, hx("9fc8ff88"))


@tex("owl_route_tag")
def _(cv):
    """A parchment routing tag: string loop, wax seal, two lines of ink."""
    pal = ramp("6f5a34", "a58f5e", "d6c294", "f0e4c4")
    # tag body: rectangle with a clipped upper-left corner
    cv.poly([(5, 2), (13, 2), (13, 14), (3, 14), (3, 4)], pal[2])
    cv.poly([(6, 3), (12, 3), (12, 13), (4, 13), (4, 4)], pal[3])
    # aged edges
    cv.inner_outline(pal[1])
    cv.put(12, 13, pal[0]); cv.put(11, 13, pal[0])
    # eyelet + string
    cv.put(5, 4, hx("6f5a34"))
    cv.line(5, 4, 2, 1, hx("9c8a6a")); cv.line(4, 4, 2, 2, hx("cbbb96"))
    # ink lines of an address
    cv.hline(6, 11, 6, hx("3b3350"))
    cv.hline(6, 9, 8, hx("4a4162"))
    cv.hline(6, 10, 10, hx("4a4162"))
    # wax seal
    cv.disc(9, 12, 1.9, hx("8e1f22"))
    cv.put(8, 11, hx("c0393a")); cv.put(10, 13, hx("5f1416"))
    cv.outline(hx("2a2214"))


@tex("phoenix_feather_core")
def _(cv):
    """One phoenix feather: gold rachis, ember barbs, still burning at the tip."""
    f = ramp("8a2a06", "d1580f", "f59b21", "ffd873")
    # rachis, curving from lower-left to upper-right
    pts = [(3, 14), (5, 12), (7, 9), (9, 6), (11, 3), (12, 1)]
    for i in range(len(pts) - 1):
        cv.line(*pts[i], *pts[i + 1], hx("f7d98a"))
    # barbs either side, longer at the middle of the vane
    vane = [(4, 13, 2), (5, 12, 3), (6, 10, 3), (7, 9, 4), (8, 7, 4),
            (9, 6, 3), (10, 4, 3), (11, 3, 2)]
    for i, (x, y, ln) in enumerate(vane):
        c = f[2] if i % 2 else f[1]
        cv.line(x, y, x - ln, y + 1, c)
        cv.line(x, y, x + ln - 1, y - 1, f[1] if i % 2 else f[0])
    # heat gradient: hotter toward the tip
    for x in range(16):
        for y in range(16):
            if cv.solid(x, y) and (x - y) > 3:
                cv.put(x, y, f[3])
    # rachis redrawn on top so it stays readable
    for i in range(len(pts) - 1):
        cv.line(*pts[i], *pts[i + 1], hx("ffe9ae"))
    # embers lifting off
    cv.put(13, 0, hx("ffd873")); cv.put(14, 2, hx("f59b21cc"))
    cv.put(1, 12, hx("d1580f99"))
    cv.outline(hx("2e0e04"))


@tex("ravenclaw_diadem_fragment")
def _(cv):
    """A broken shard of the diadem: filigree, one sapphire, a dark stain in the metal."""
    s = ramp("4a5568", "808fa6", "b9c6d8", "eef4fb")
    blu = ramp("14306b", "2b5cc4", "5f9bff", "c4dcff")
    # the arc of the tiara, snapped on the right
    cv.line(2, 11, 4, 7, s[2]); cv.line(4, 7, 7, 5, s[2])
    cv.line(7, 5, 10, 6, s[2]); cv.line(10, 6, 12, 9, s[2])
    cv.line(2, 12, 4, 8, s[1]); cv.line(4, 8, 7, 6, s[1])
    cv.line(7, 6, 10, 7, s[1]); cv.line(10, 7, 12, 10, s[1])
    # jagged break at the lower right
    cv.put(12, 11, s[0]); cv.put(13, 10, s[0]); cv.put(11, 12, s[0])
    cv.put(13, 11, hx("3a4152"))
    # filigree scrollwork
    cv.put(5, 8, s[3]); cv.put(9, 8, s[3]); cv.put(3, 10, s[3])
    cv.put(6, 9, s[0]); cv.put(8, 9, s[0])
    # sapphire at the crest
    cv.rect(7, 3, 8, 5, blu[1])
    cv.put(7, 3, blu[3]); cv.put(8, 5, blu[0])
    cv.put(7, 4, blu[2])
    cv.put(6, 4, s[1]); cv.put(9, 4, s[1])
    # the horcrux stain
    cv.put(10, 9, hx("2b2036")); cv.put(11, 10, hx("2b2036"))
    cv.outline(hx("13171f"))


@tex("unicorn_hair_core")
def _(cv):
    """A single unicorn hair, coiled -- pearl white with a cold rim light."""
    w = ramp("8c9bb5", "c3d0e4", "eef4fd", "ffffff")
    # a loose spiral
    coil = [(11, 2), (8, 2), (5, 4), (5, 7), (8, 8), (11, 9),
            (11, 12), (8, 14), (5, 14)]
    for i in range(len(coil) - 1):
        cv.line(*coil[i], *coil[i + 1], w[2])
    # rim light above, shadow below
    for x in range(16):
        for y in range(16):
            if cv.solid(x, y) and not cv.solid(x, y - 1):
                cv.put(x, y, w[3])
            elif cv.solid(x, y) and not cv.solid(x, y + 1):
                cv.put(x, y, w[1])
    # loose ends
    cv.put(12, 1, w[1]); cv.put(4, 15, w[1])
    # faint magic
    cv.put(13, 6, hx("cfe4ff99")); cv.put(3, 11, hx("cfe4ff99"))
    cv.outline(hx("3a4152"))


@tex("wolf_fang")
def _(cv):
    """A werewolf's tooth: heavy root, worn enamel, a hairline crack."""
    iv = ramp("8a7a5e", "bfae8d", "e6dcc2", "fbf6e6")
    # curved tooth, root bottom-left tapering to a point top-right
    cv.poly([(3, 15), (6, 14), (10, 7), (13, 1), (11, 4), (7, 11), (3, 13)], iv[1])
    cv.line(13, 1, 9, 8, iv[3])
    cv.line(12, 2, 8, 9, iv[2])
    cv.line(4, 14, 8, 8, iv[0])
    # root: wider, duller, with a hollow
    cv.rect(3, 12, 6, 15, iv[1])
    cv.put(4, 13, iv[0]); cv.put(5, 14, iv[0])
    cv.put(3, 12, iv[2])
    # hairline crack
    cv.put(7, 10, iv[0]); cv.put(8, 9, iv[0])
    cv.outline(hx("2b241a"))


# --- house robes -------------------------------------------------------------
def robe(cv, cloth, trim, crest):
    """Shared silhouette: a hooded school robe seen from the front."""
    dark = shade(cloth[0], 0.6)
    # hood
    cv.poly([(5, 1), (10, 1), (12, 4), (11, 5), (4, 5), (3, 4)], cloth[0])
    cv.poly([(6, 2), (9, 2), (10, 4), (5, 4)], hx("1a1a1e"))
    # body
    cv.poly([(4, 5), (11, 5), (13, 9), (12, 15), (3, 15), (2, 9)], cloth[1])
    # open front showing the lining
    cv.poly([(7, 5), (8, 5), (9, 15), (6, 15)], trim[1])
    cv.vline(7, 6, 14, trim[2])
    # sleeve shading and fold lines
    cv.vline(3, 10, 14, cloth[0]); cv.vline(12, 10, 14, dark)
    cv.vline(11, 8, 14, cloth[0])
    cv.vline(4, 7, 14, cloth[2])
    # collar
    cv.hline(4, 11, 5, cloth[2])
    cv.put(5, 6, cloth[2]); cv.put(10, 6, cloth[0])
    # hem
    cv.hline(3, 12, 15, dark)
    # house crest on the chest
    cv.put(5, 8, crest[0]); cv.put(5, 9, crest[1])
    cv.put(10, 8, cloth[0])
    cv.outline(hx("101014"))


@tex("gryffindor_robes")
def _(cv):
    robe(cv, ramp("2a1116", "43181f", "5c2029"), ramp("6e0e12", "a8161c", "e8c452"), ramp("e8c452", "a8161c"))


@tex("hufflepuff_robes")
def _(cv):
    robe(cv, ramp("241c10", "3a2d18", "4d3c21"), ramp("8a6a08", "d9a80f", "f7de7a"), ramp("f7de7a", "3a2d18"))


@tex("ravenclaw_robes")
def _(cv):
    robe(cv, ramp("0f1730", "182444", "23325c"), ramp("123a7a", "1d5bb5", "b98d3e"), ramp("b98d3e", "1d5bb5"))


@tex("slytherin_robes")
def _(cv):
    robe(cv, ramp("0d1c16", "142b21", "1c3a2c"), ramp("0d5a34", "158f50", "c0c6cc"), ramp("c0c6cc", "158f50"))


# --- workbench item icons ----------------------------------------------------
@tex("wand_workbench")
def _(cv):
    """Matches the block art: mahogany bench, a wand blank in the clamp, tools laid out."""
    w = ramp("2e1a10", "4a2a17", "6b3f22", "8c5730")
    # top surface, in slight perspective
    cv.poly([(2, 6), (13, 6), (15, 8), (1, 8)], w[3])
    cv.hline(1, 15, 8, w[2])
    cv.rect(1, 9, 15, 10, w[1])
    cv.hline(1, 15, 10, w[0])
    # legs
    cv.rect(2, 11, 3, 15, w[1]); cv.rect(12, 11, 13, 15, w[1])
    cv.vline(3, 11, 15, w[0]); cv.vline(13, 11, 15, w[0])
    cv.rect(4, 13, 11, 13, w[0])  # stretcher
    # wand blank clamped on the bench
    cv.line(4, 5, 11, 3, hx("c3a066"))
    cv.line(4, 6, 11, 4, hx("9a7845"))
    cv.put(12, 3, hx("e0c48f"))
    # clamp
    cv.rect(6, 4, 7, 6, hx("6b7382")); cv.put(6, 4, hx("a3aab6"))
    # shavings + a chisel
    cv.put(2, 5, hx("d6bb8a")); cv.put(13, 5, hx("d6bb8a"))
    cv.line(9, 7, 12, 7, hx("8e99a8")); cv.put(13, 7, hx("6b4a2a"))
    cv.outline(hx("120a06"))


@tex("broom_workbench", scale=4)
def _(cv):
    """A broom on the trestle: bound bristles, a half-shaped stick, twine."""
    w = ramp("2e1a10", "4a2a17", "6b3f22", "8c5730")
    st = ramp("6b4f2c", "9a7845", "c3a066", "e0c48f")
    # trestle
    cv.poly([(1, 8), (14, 8), (15, 10), (0, 10)], w[3])
    cv.hline(0, 15, 10, w[1])
    cv.line(2, 11, 4, 15, w[1]); cv.line(13, 11, 11, 15, w[1])
    cv.line(3, 11, 5, 15, w[0]); cv.line(12, 11, 10, 15, w[0])
    # broom lying across it
    shaft(cv, 12, 3, 5, 6, [st[3], st[2], st[1]], width=2)
    # bristle head
    for i, y in enumerate(range(4, 9)):
        cv.line(5, y, 1, y - 1 + i // 2, hx("b8933f") if i % 2 else hx("8f6f2c"))
    cv.put(1, 4, hx("d8b45e")); cv.put(1, 7, hx("6f5620"))
    # twine binding
    cv.vline(5, 4, 7, hx("d8cba6")); cv.put(6, 5, hx("a8996f"))
    cv.outline(hx("120a06"))


print("wrote", len(REG), "item textures ->", OUT)
