"""Second pass -- redraws the icons from gen_items.py that did not read at 16x16.
Lesson from pass 1: no flood-thickening, keep strands 1-2px, never enclose a dark void."""
import os
from pixlib import Canvas, hx, ramp, mix, shade, shaft

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "proposed", "item")
os.makedirs(OUT, exist_ok=True)
REG = []


def tex(name, size=16, scale=1):
    def deco(fn):
        cv = Canvas(size, size, seed=abs(hash(name)) % 9999)
        fn(cv)
        im = cv.image()
        if scale > 1:
            im = im.resize((size * scale, size * scale))
        im.save(os.path.join(OUT, name + ".png"))
        REG.append(name)
        return fn
    return deco


@tex("dragon_heartstring_core")
def _(cv):
    """One thin length of heartstring, loosely coiled and still faintly live."""
    s = ramp("4a0a0e", "8e161d", "cf2f2c", "ff7a5e")
    path = [(3, 14), (5, 13), (7, 11), (6, 9), (4, 8), (5, 6),
            (7, 5), (9, 6), (10, 8), (12, 8), (13, 6), (13, 3)]
    for i in range(len(path) - 1):
        cv.line(*path[i], *path[i + 1], s[1])
    # top edge of the strand catches light, underside goes dark
    for x in range(16):
        for y in range(16):
            if cv.solid(x, y):
                if not cv.solid(x, y - 1):
                    cv.put(x, y, s[2])
    for x, y in ((6, 10), (9, 7), (11, 8), (4, 13)):
        cv.put(x, y, s[0])
    for x, y in ((7, 5), (13, 4), (5, 12)):
        cv.put(x, y, s[3])
    # frayed ends
    cv.put(2, 15, s[0]); cv.put(13, 2, s[0])
    # the faint pulse of something once alive
    cv.put(8, 5, hx("ff7a5e")); cv.put(12, 7, hx("ff7a5eaa"))
    cv.outline(hx("240508"))


@tex("enchanted_inkwell")
def _(cv):
    """Squat glass well, brass collar, quill standing in the ink."""
    ink = ramp("0d1642", "1c2c78", "3651bd", "6f8ce8")
    br = ramp("6b4a12", "a8791c", "e0b23c")
    gl = ramp("2b3550", "51648c", "93a9d0")
    # quill: shaft goes down into the well mouth
    cv.line(13, 1, 8, 9, hx("cfd7e4"))
    cv.line(13, 2, 9, 9, hx("9aa6b8"))
    # feather barbs, up the right side of the shaft
    for (x, y, n) in ((12, 2, 2), (12, 3, 3), (11, 4, 3), (11, 5, 2), (10, 6, 2)):
        for k in range(n):
            cv.put(x + 1 + k, y - 1, hx("eef3fa") if k == 0 else hx("cfd7e4"))
    cv.put(13, 1, hx("ffffff"))
    # well body -- wider at the base
    cv.poly([(4, 9), (11, 9), (12, 14), (3, 14)], gl[1])
    cv.poly([(5, 10), (10, 10), (11, 13), (4, 13)], ink[1])
    cv.hline(5, 10, 10, ink[2])
    cv.hline(4, 11, 13, ink[0])
    cv.vline(4, 10, 13, gl[2])          # glass highlight, left
    cv.put(11, 12, gl[0])
    # brass collar around the mouth
    cv.rect(4, 8, 11, 9, br[1])
    cv.hline(4, 11, 8, br[2])
    cv.put(11, 9, br[0])
    # ink surface visible in the mouth
    cv.hline(6, 9, 8, ink[3])
    cv.hline(3, 12, 15, hx("2b3550"))   # foot
    # enchantment
    cv.put(2, 6, hx("a98cffcc")); cv.put(6, 11, hx("6f8ce8"))
    cv.outline(hx("0a0d1c"))


@tex("fine_wood_stick")
def _(cv):
    """A wand blank: thin, pale, tapered -- not a branch."""
    w = ramp("6b4f2c", "9a7845", "c3a066", "e6cd9c")
    cv.line(13, 2, 3, 13, w[1])
    cv.line(13, 1, 3, 12, w[2])
    cv.line(12, 1, 4, 11, w[3])
    # taper: the tip end is a single texel, the butt is squared
    cv.put(13, 1, w[3]); cv.put(14, 1, w[2])
    cv.put(3, 13, w[0]); cv.put(2, 13, w[0]); cv.put(3, 14, w[0])
    for x, y in ((10, 4), (7, 8)):
        cv.put(x, y, w[0])
    cv.outline(hx("2a1d10"))


@tex("merperson_spear")
def _(cv):
    """Three separated prongs on a lashed shaft -- reads as a trident at a glance."""
    br = ramp("4a3a1c", "7d6529", "b09144", "e2c67e")
    wd = ramp("241a12", "3d2c1d", "574029")
    # shaft
    cv.line(8, 7, 5, 15, wd[1])
    cv.line(9, 7, 6, 15, wd[2])
    cv.line(10, 7, 7, 15, wd[0])
    # crossbar the prongs rise from
    cv.hline(5, 12, 6, br[1])
    cv.hline(5, 12, 5, br[2])
    # three prongs, 1px wide with clear gaps
    for x, top in ((5, 2), (8, 0), (11, 2)):
        cv.vline(x, top, 5, br[2])
        cv.put(x, top, br[3])
        cv.put(x + 1, top + 1, br[0])
    # inward barbs so it reads as a fishing trident
    cv.put(6, 3, br[1]); cv.put(10, 3, br[1])
    # cord lashing where head meets shaft
    cv.hline(6, 10, 7, hx("bfae86"))
    cv.put(7, 8, hx("8f8262")); cv.put(9, 8, hx("8f8262"))
    cv.outline(hx("0f0b07"))


@tex("ravenclaw_diadem_fragment")
def _(cv):
    """A snapped-off arc of the diadem, sapphire still set, metal going dark at the break."""
    s = ramp("434e60", "7c8a a0".replace(" ", ""), "b4c2d6", "eef4fb")
    blu = ramp("14306b", "2b5cc4", "5f9bff", "c4dcff")
    # shallow tiara arc, 2px thick
    arc = [(1, 11), (2, 9), (4, 7), (6, 6), (8, 6), (10, 7), (12, 9)]
    for i in range(len(arc) - 1):
        cv.line(*arc[i], *arc[i + 1], s[2])
        cv.line(arc[i][0], arc[i][1] + 1, arc[i + 1][0], arc[i + 1][1] + 1, s[1])
        cv.line(arc[i][0], arc[i][1] + 2, arc[i + 1][0], arc[i + 1][1] + 2, s[0])
    # filigree points rising off the band
    for x, y in ((4, 6), (11, 8)):
        cv.put(x, y - 1, s[3])
    # sapphire set at the crest
    cv.rect(7, 3, 8, 5, blu[1])
    cv.put(7, 3, blu[3]); cv.put(8, 3, blu[2]); cv.put(8, 5, blu[0])
    cv.put(6, 5, s[2]); cv.put(9, 5, s[2])       # claw setting
    # the break: jagged, and the metal is tarnished there
    cv.put(13, 10, hx("2f3340")); cv.put(13, 11, hx("242835"))
    cv.put(12, 11, hx("2f3340")); cv.put(14, 11, hx("1d2029"))
    cv.put(1, 12, s[0])
    cv.outline(hx("13171f"))


@tex("acromantula_venom")
def _(cv):
    """A collector's vial of spider venom -- fanged stopper, thick green, one bead."""
    ven = ramp("174d1c", "2f8a26", "5cc23a", "a6f06e")
    gl = ramp("2b3a44", "5d727f", "a9c1cd")
    # flask: narrow neck, round shoulders, flat base
    cv.rect(6, 2, 9, 4, gl[1])                    # neck
    cv.poly([(5, 5), (10, 5), (11, 7), (11, 14), (4, 14), (4, 7)], gl[1])
    # venom
    cv.poly([(5, 8), (10, 8), (10, 13), (5, 13)], ven[1])
    cv.hline(5, 10, 8, ven[2])
    cv.hline(5, 10, 13, ven[0])
    cv.put(6, 9, ven[3])                          # a bead of light on the surface
    cv.put(9, 12, ven[0])
    # glass
    cv.vline(5, 6, 12, gl[2])
    cv.put(6, 3, gl[2])
    cv.put(10, 12, gl[0]); cv.put(10, 6, gl[0])
    # stopper with two etched fangs
    cv.rect(5, 0, 10, 1, hx("6b4a2a"))
    cv.hline(5, 10, 0, hx("8a6438"))
    cv.put(10, 1, hx("4d341d"))
    cv.put(6, 1, hx("ddd5c4")); cv.put(9, 1, hx("ddd5c4"))
    # a drip on the outside
    cv.put(11, 9, ven[2]); cv.put(11, 10, ven[1])
    cv.outline(hx("0d1a11"))


@tex("phoenix_feather_core")
def _(cv):
    """A single feather with separated barbs, cooling from the tip down."""
    f = ramp("7a2405", "c14f0c", "ef8f1c", "ffd873")
    # rachis
    cv.line(3, 14, 12, 2, hx("ffeab4"))
    cv.line(4, 14, 13, 2, hx("e0b25e"))
    # barbs: alternate sides, spaced so they stay individually visible
    barbs = [(4, 13), (5, 11), (6, 10), (7, 8), (8, 7), (9, 5), (10, 4), (11, 2)]
    for i, (x, y) in enumerate(barbs):
        hot = i >= len(barbs) - 3
        cu = f[3] if hot else (f[2] if i % 2 else f[1])
        cd = f[2] if hot else (f[1] if i % 2 else f[0])
        ln = 3 if 1 <= i <= 6 else 2
        cv.line(x, y, x - ln, y + ln - 1, cd)
        cv.line(x + 1, y - 1, x + ln, y - ln + 1, cu)
    cv.put(12, 1, f[3]); cv.put(13, 1, hx("fff0c4"))
    # embers coming off the hot end
    cv.put(14, 3, hx("ffd873cc")); cv.put(13, 5, hx("ef8f1c99"))
    cv.outline(hx("2e0e04"))


@tex("cave_inimicum_totem")
def _(cv):
    """A carved wooden ward-totem: hollow eyes, a rune band, green protective light."""
    wd = ramp("3a2716", "573a20", "77522d", "9a6d3f")
    grn = ramp("14513a", "2e9a68", "6ef2a8")
    # head: rounded top, carved from one billet
    cv.poly([(5, 1), (10, 1), (11, 3), (11, 8), (10, 9), (5, 9), (4, 8), (4, 3)], wd[2])
    cv.vline(5, 2, 8, wd[3])                      # lit side
    cv.vline(10, 2, 8, wd[1])                     # shaded side
    cv.hline(5, 10, 1, wd[3])
    # carved features
    cv.rect(6, 4, 6, 5, hx("241809")); cv.rect(9, 4, 9, 5, hx("241809"))
    cv.put(6, 4, grn[2]); cv.put(9, 4, grn[2])    # ward-light in the eyes
    cv.hline(6, 9, 7, hx("3d2814"))               # set mouth
    cv.put(7, 3, wd[1]); cv.put(8, 3, wd[1])      # brow
    # neck + post
    cv.rect(6, 10, 9, 15, wd[1])
    cv.vline(6, 10, 15, wd[2]); cv.vline(9, 10, 15, wd[0])
    # rune band burning green
    cv.hline(6, 9, 11, grn[1])
    cv.put(7, 12, grn[0]); cv.put(8, 13, grn[0])
    # outstretched warding arms
    cv.rect(2, 6, 3, 7, wd[1]); cv.rect(12, 6, 13, 7, wd[1])
    cv.put(2, 6, wd[3]); cv.put(13, 7, wd[0])
    cv.outline(hx("140d06"))


@tex("broom_workbench", scale=4)
def _(cv):
    """A trestle with an unfinished broom clamped to it, bristles fanned to the left."""
    w = ramp("2e1a10", "4a2a17", "6b3f22", "8c5730")
    st = ramp("6b4f2c", "9a7845", "c3a066", "e6cd9c")
    # bench top
    cv.rect(1, 8, 14, 8, w[3])
    cv.rect(1, 9, 14, 10, w[2])
    cv.hline(1, 14, 10, w[0])
    # legs
    cv.rect(2, 11, 3, 15, w[1]); cv.rect(12, 11, 13, 15, w[1])
    cv.vline(3, 11, 15, w[0]); cv.vline(13, 11, 15, w[0])
    cv.rect(4, 13, 11, 13, w[1])
    # broom laid across the top: handle to the right
    cv.line(7, 5, 14, 3, st[2])
    cv.line(7, 6, 14, 4, st[1])
    cv.put(14, 2, st[3])
    # bristle fan to the left, individual straws
    for i, y in enumerate((2, 3, 4, 5, 6, 7)):
        c = hx("d8b45e") if i % 2 else hx("a8842f")
        cv.line(6, 5 if y < 5 else 6, 1, y, c)
    cv.put(1, 2, hx("e6cd8a")); cv.put(1, 7, hx("7a5f22"))
    # twine binding at the neck
    cv.vline(6, 4, 7, hx("efe3c0"))
    cv.put(7, 5, hx("b3a479"))
    cv.outline(hx("120a06"))


# --- house robes, take two ---------------------------------------------------
def robe(cv, cloth, lining, tie, crest):
    """Front-facing school robe: shoulders, open front over a house-coloured lining,
    a rolled hood behind the neck. No dark voids -- that is what killed pass one."""
    dk = cloth[0]
    # rolled hood sitting behind the shoulders
    cv.rect(5, 1, 10, 2, cloth[1])
    cv.hline(5, 10, 1, cloth[2])
    cv.put(4, 2, cloth[0]); cv.put(11, 2, cloth[0])
    # shoulders and body, flaring to the hem
    cv.poly([(4, 3), (11, 3), (12, 7), (13, 15), (2, 15), (3, 7)], cloth[1])
    # sleeves: darker bands down the outer edges
    cv.vline(2, 8, 15, dk); cv.vline(3, 6, 15, cloth[0])
    cv.vline(13, 8, 15, dk); cv.vline(12, 6, 15, cloth[0])
    # lit edge, upper left
    cv.vline(4, 4, 14, cloth[2])
    # open front: lining panel showing between the lapels
    cv.poly([(7, 3), (8, 3), (9, 15), (6, 15)], lining[1])
    cv.vline(7, 4, 14, lining[2])
    cv.put(8, 6, lining[0]); cv.put(8, 11, lining[0])
    # lapels folding back either side of the opening
    cv.line(6, 3, 5, 8, cloth[2]); cv.line(9, 3, 10, 8, dk)
    # collar and tie
    cv.hline(5, 10, 3, cloth[2])
    cv.put(7, 4, tie[1]); cv.put(8, 4, tie[1])
    cv.put(7, 5, tie[0]); cv.put(8, 5, tie[1])
    cv.put(7, 6, tie[1])
    # crest patch on the chest
    cv.rect(5, 6, 5, 7, crest)
    # hem shadow
    cv.hline(2, 13, 15, dk)
    cv.outline(hx("0e0e12"))


def mk(name, cloth, lining, tie, crest):
    @tex(name)
    def _(cv, cloth=cloth, lining=lining, tie=tie, crest=crest):
        robe(cv, ramp(*cloth), ramp(*lining), ramp(*tie), hx(crest))


mk("gryffindor_robes", ("241014", "3a181e", "532730"), ("6e0e12", "a8161c", "d4444a"), ("8a1418", "e8c452"), "e8c452")
mk("hufflepuff_robes", ("20190f", "342a17", "4a3c22"), ("8a6a08", "d9a80f", "f2cf55"), ("2a2418", "f7de7a"), "f7de7a")
mk("ravenclaw_robes", ("0d1428", "16203c", "203055"), ("123a7a", "1d5bb5", "4b8ae0"), ("0f2a5c", "b98d3e"), "b98d3e")
mk("slytherin_robes", ("0b1811", "12261c", "1a3628"), ("0d5a34", "158f50", "45c483"), ("0a3c22", "c0c6cc"), "c0c6cc")

print("redrew:", ", ".join(REG))
