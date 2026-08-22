"""Draw the profession overlays for the Diagon Alley shopkeepers.

    python build_scripts/generate_villager_professions.py

A villager profession texture is a 64x64 overlay drawn over the base villager by
VillagerProfessionLayer, so anything left transparent shows the villager through. It is NOT a free
canvas: every pixel belongs to a specific face of a specific box in VillagerModel, and painting
outside those boxes paints nothing while painting the wrong one puts a shop apron on the back of a
villager's head.

The existing wandmaker.png got both of those wrong. It was **128x64** -- every vanilla profession is
64x64, and the model bakes its UVs against a 64-wide texture, so the whole overlay sampled at half
scale horizontally -- and only two small patches of it were painted at all, so the wandmaker was a
floating hat brim and a scrap of apron over an otherwise blank villager.

THE BOX LAYOUT
--------------
Read straight off VillagerModel.createBodyModel(). For a box at texture offset (u,v) with size
(w,h,d), Minecraft lays the six faces out as:

    top    (u+d,     v    )  w x d          right  (u,       v+d)  d x h
    bottom (u+d+w,   v    )  w x d          front  (u+d,     v+d)  w x h
                                            left   (u+d+w,   v+d)  d x h
                                            back   (u+d+w+d, v+d)  w x h

which gives the four regions that matter here:

    hat        (32, 0)  8 x 10 x 8     the crown
    hat_rim    (30,47) 16 x 16 x 1     the brim, drawn as a disc on the front face
    jacket     ( 0,38)  8 x 20 x 6     the robe -- the biggest and most visible area
    arms       (44,22)  4 x  8 x 4     sleeves

WHY THESE ARE DRAWN AND NOT RECOLOURED
--------------------------------------
Tinting vanilla's artwork would be quicker and would land in the right places by construction, but
it also produces fourteen shopkeepers who are recognisably a librarian in different colours. These
are drawn from a shared kit -- robe, collar, hem, hat, emblem -- so a wandmaker and an apothecary
differ in silhouette and not only in hue.
"""

import os

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources',
                   'assets', 'hogwartsmagic', 'textures', 'entity')

N = 64

# How much of the ten-row head box the hat covers. The rest is the villager's face.
HAT_ROWS = 4


def faces(u, v, w, h, d):
    """The six face rectangles of a Minecraft box, as (x, y, w, h)."""
    return {
        'top':    (u + d, v, w, d),
        'bottom': (u + d + w, v, w, d),
        'right':  (u, v + d, d, h),
        'front':  (u + d, v + d, w, h),
        'left':   (u + d + w, v + d, d, h),
        'back':   (u + d + w + d, v + d, w, h),
    }


HAT = faces(32, 0, 8, 10, 8)
RIM = faces(30, 47, 16, 16, 1)
ROBE = faces(0, 38, 8, 20, 6)
ARMS = faces(44, 22, 4, 8, 4)
XARMS = faces(40, 38, 8, 4, 4)


def _hash(x, y, salt=0):
    h = (x * 73856093) ^ (y * 19349663) ^ (salt * 83492791)
    h &= 0x7FFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0x7FFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def shade(colour, amount):
    return tuple(max(0, min(255, int(c * (1.0 + amount)))) for c in colour[:3]) + (255,)


def fill(im, rect, colour, grain=0.06, salt=0, top_light=True, rows=None):
    """Fill a face, with a little per-pixel grain and a vertical light gradient.

    Flat fills are what make a hand-made overlay look like a colour swatch; the gradient is what
    makes cloth read as cloth at sixteen pixels tall.
    """
    x0, y0, w, h = rect
    px = im.load()
    for dy in range(h):
        if rows is not None and dy >= rows:
            continue                # leave the rest of the face bare -- see build()
        lift = (0.16 * (1.0 - dy / max(1, h - 1)) - 0.08) if top_light else 0.0
        for dx in range(w):
            g = (_hash(x0 + dx, y0 + dy, salt) - 0.5) * 2 * grain
            px[x0 + dx, y0 + dy] = shade(colour, lift + g)


def band(im, rect, colour, rows, from_bottom=False, grain=0.05, salt=0):
    x0, y0, w, h = rect
    px = im.load()
    for i in range(rows):
        dy = (h - 1 - i) if from_bottom else i
        for dx in range(w):
            g = (_hash(x0 + dx, y0 + dy, salt + 5) - 0.5) * 2 * grain
            px[x0 + dx, y0 + dy] = shade(colour, g)


def placket(im, rect, colour, salt=0):
    """The vertical opening down the front of a robe -- two pixels wide, centred."""
    x0, y0, w, h = rect
    px = im.load()
    for dy in range(1, h):
        for dx in (w // 2 - 1, w // 2):
            g = (_hash(x0 + dx, y0 + dy, salt + 9) - 0.5) * 0.1
            px[x0 + dx, y0 + dy] = shade(colour, g)


def disc(im, rect, colour, radius, salt=0):
    """A brim: a filled circle on the hat_rim front face, transparent outside it."""
    x0, y0, w, h = rect
    px = im.load()
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    for dy in range(h):
        for dx in range(w):
            r = ((dx - cx) ** 2 + (dy - cy) ** 2) ** 0.5
            if r <= radius:
                edge = -0.22 if r > radius - 1.2 else 0.0
                g = (_hash(x0 + dx, y0 + dy, salt + 3) - 0.5) * 0.12
                px[x0 + dx, y0 + dy] = shade(colour, edge + g)


def stamp(im, rect, art, palette, ox=None, oy=1):
    """A small emblem on the chest, from string art. '.' is transparent."""
    x0, y0, w, h = rect
    aw = max(len(r) for r in art)
    ox = (w - aw) // 2 if ox is None else ox
    px = im.load()
    for dy, row in enumerate(art):
        for dx, ch in enumerate(row):
            if ch == '.':
                continue
            x, y = x0 + ox + dx, y0 + oy + dy
            if x0 <= x < x0 + w and y0 <= y < y0 + h:
                px[x, y] = palette[ch] + (255,)


# --- emblems -------------------------------------------------------------------------------------
# Eight pixels of chest is not much, so each of these is a silhouette rather than a picture: the
# point is that two shopkeepers standing next to each other are not the same person in two colours.

WAND = ['..w', '.w.', 'w..']
FLASK = ['.g.', 'ggg', 'ggg']
BOOK = ['ccc', 'cdc', 'ccc']
OWL = ['o.o', 'ooo', '.o.']
BROOM = ['..s', '.s.', 'bb.']
NEEDLE = ['s.s', '.s.', 's.s']
CONE = ['ppp', '.p.', '..']
SPARK = ['y.y', '.y.', 'y.y']
PAW = ['p.p', 'ppp', '.p.']
QUILL = ['..q', '.q.', 'q..']
POT = ['i.i', 'iii', '.i.']
SKULL = ['rrr', 'r.r', '.r.']


class Look:
    def __init__(self, name, robe, trim, hat, brim=None, emblem=None, art_palette=None,
                 sleeve=None):
        self.name = name
        self.robe = robe
        self.trim = trim
        self.hat = hat
        self.brim = brim                 # None = no brim; the crown alone reads as a cap
        self.emblem = emblem
        self.art_palette = art_palette or {}
        self.sleeve = sleeve or robe


# Colours track each shop's paint in the build, so the keeper matches his own shopfront.
LOOKS = [
    # Ollivanders. Dust and faded cloth: the shop has not been redecorated in six hundred years.
    Look('wandmaker', (108, 96, 82), (198, 186, 160), (74, 66, 58), brim=(74, 66, 58),
         emblem=WAND, art_palette={'w': (222, 208, 172)}),
    Look('apothecary', (56, 78, 50), (168, 134, 60), (40, 56, 38), brim=(40, 56, 38),
         emblem=FLASK, art_palette={'g': (150, 196, 148)}),
    Look('bookseller', (48, 74, 54), (206, 196, 168), (36, 54, 42),
         emblem=BOOK, art_palette={'c': (206, 196, 168), 'd': (92, 66, 44)}),
    Look('owl_merchant', (138, 106, 46), (86, 62, 34), (110, 84, 38), brim=(110, 84, 38),
         emblem=OWL, art_palette={'o': (232, 218, 186)}),
    Look('broom_merchant', (106, 46, 46), (188, 156, 72), (74, 34, 34),
         emblem=BROOM, art_palette={'s': (188, 156, 72), 'b': (96, 72, 44)}),
    Look('robemaker', (42, 90, 96), (196, 200, 204), (34, 72, 78),
         emblem=NEEDLE, art_palette={'s': (214, 218, 222)}),
    Look('confectioner', (198, 190, 160), (206, 140, 156), (206, 200, 176),
         emblem=CONE, art_palette={'p': (214, 148, 162)}),
    Look('joke_merchant', (184, 96, 28), (120, 62, 148), (120, 62, 148), brim=(120, 62, 148),
         emblem=SPARK, art_palette={'y': (238, 214, 96)}),
    Look('menagerist', (82, 54, 96), (204, 192, 168), (62, 40, 74), brim=(62, 40, 74),
         emblem=PAW, art_palette={'p': (210, 198, 174)}),
    Look('scribe', (48, 60, 96), (206, 198, 172), (38, 48, 78),
         emblem=QUILL, art_palette={'q': (226, 220, 196)}),
    Look('cauldron_smith', (112, 110, 116), (176, 96, 44), (86, 84, 90),
         emblem=POT, art_palette={'i': (176, 96, 44)}, sleeve=(96, 72, 52)),
    # Borgin and Burkes. Black on black, and the only red in the set.
    Look('curio_dealer', (74, 68, 76), (128, 34, 34), (52, 48, 54), brim=(52, 48, 54),
         emblem=SKULL, art_palette={'r': (150, 44, 44)}),
]


def build(look):
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    salt = sum(ord(c) for c in look.name)

    # --- robe -----------------------------------------------------------------------------------
    for key in ('front', 'back', 'left', 'right'):
        fill(im, ROBE[key], look.robe, salt=salt)
    fill(im, ROBE['top'], shade(look.robe, 0.10)[:3], salt=salt, top_light=False)
    # collar at the shoulders, hem at the ankles, opening down the front
    for key in ('front', 'back', 'left', 'right'):
        band(im, ROBE[key], look.trim, 1, salt=salt)
        band(im, ROBE[key], shade(look.robe, -0.28)[:3], 2, from_bottom=True, salt=salt)
    placket(im, ROBE['front'], look.trim, salt=salt)

    # --- sleeves --------------------------------------------------------------------------------
    for key in ('front', 'back', 'left', 'right'):
        fill(im, ARMS[key], look.sleeve, salt=salt + 1)
        band(im, ARMS[key], look.trim, 1, from_bottom=True, salt=salt + 1)
    for key in ('front', 'back'):
        fill(im, XARMS[key], look.sleeve, salt=salt + 2)

    # --- hat ------------------------------------------------------------------------------------
    # Only the crown. The head box is ten rows tall and the face lives in the lower six of them,
    # so filling all ten -- which the first version did -- produced a solid block of colour with
    # no eyes, no nose and no expression. Every vanilla profession leaves the face alone and
    # draws a band or a cap; four rows is a cap.
    for key in ('front', 'back', 'left', 'right'):
        fill(im, HAT[key], look.hat, salt=salt + 3, rows=HAT_ROWS)
    for key in ('front', 'back', 'left', 'right'):
        x0, y0, w, _h = HAT[key]
        band(im, (x0, y0 + HAT_ROWS - 1, w, 1), shade(look.hat, -0.35)[:3], 1, salt=salt + 3)
    fill(im, HAT['top'], shade(look.hat, 0.12)[:3], salt=salt + 3, top_light=False)
    if look.brim:
        disc(im, RIM['front'], look.brim, 7.2, salt=salt + 4)
        disc(im, RIM['back'], shade(look.brim, -0.25)[:3], 7.2, salt=salt + 4)

    # --- emblem ---------------------------------------------------------------------------------
    if look.emblem:
        stamp(im, ROBE['front'], look.emblem, look.art_palette, oy=3)

    return im


# --- the zombie variant ---------------------------------------------------------------------------
# ZombieVillagerModel is a HumanoidModel, a completely different layout from VillagerModel, so the
# villager overlay cannot simply be copied across. Without a file the game draws a missing texture,
# so each profession gets a plain robe in the humanoid layout: body, both arms, both legs.

HUMANOID = {
    'body':      faces(16, 16, 8, 12, 4),
    'right_arm': faces(40, 16, 4, 12, 4),
    'left_arm':  faces(32, 48, 4, 12, 4),
    'right_leg': faces(0, 16, 4, 12, 4),
    'left_leg':  faces(16, 48, 4, 12, 4),
}


def build_zombie(look):
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    salt = sum(ord(c) for c in look.name) + 40
    for part, box in HUMANOID.items():
        colour = look.sleeve if 'arm' in part else look.robe
        for key in ('front', 'back', 'left', 'right'):
            fill(im, box[key], colour, salt=salt)
        band(im, box[key], shade(colour, -0.3)[:3], 1, from_bottom=True, salt=salt)
    band(im, HUMANOID['body']['front'], look.trim, 1, salt=salt)
    band(im, HUMANOID['body']['back'], look.trim, 1, salt=salt)
    return im


if __name__ == '__main__':
    vdir = os.path.join(OUT, 'villager', 'profession')
    zdir = os.path.join(OUT, 'zombie_villager', 'profession')
    os.makedirs(vdir, exist_ok=True)
    os.makedirs(zdir, exist_ok=True)
    for look in LOOKS:
        build(look).save(os.path.join(vdir, look.name + '.png'))
        build_zombie(look).save(os.path.join(zdir, look.name + '.png'))
        print('  %-16s villager + zombie' % look.name)
    print('%d professions written to %s' % (len(LOOKS), os.path.normpath(vdir)))
