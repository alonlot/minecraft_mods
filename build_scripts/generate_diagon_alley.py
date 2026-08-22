"""Diagon Alley, compiled to a structure template.

    python build_scripts/generate_diagon_alley.py

Why this is Python and not Java
-------------------------------
The old Diagon Alley was 1,600 lines of Java that wrote blocks straight into the world. It was
therefore invisible: nobody could look at it without launching the game, walking to it, and flying
around, so it had never actually been *seen* as a whole. Authoring it as a template instead means
the renderer in .claude/skills/rendering-minecraft-structures can draw it from any angle in a couple
of seconds, and a build you can look at is a build you can fix.

The shape of the street
-----------------------
West to east, along +X:

    x  0.. 13   the walled courtyard behind the Leaky Cauldron, with the dustbin
    x 14        THE BRICK WALL -- one panel of hogwartsmagic:diagon_archway_brick, which is what
                the archway animation opens
    x 15..117   the alley proper: eight shops on the north terrace, nine on the south, and the
                mouth of Knockturn Alley two thirds of the way down
    x 118..132  the square, with the fountain
    x 133..232  Gringotts -- the building the mod already ships, imported from its schematic
                rather than hand-built here: a hundred blocks deep, thirty-four across, and a
                cathedral inside

Across, before the curve is applied, the section is:

    z 16..27    north terrace (front wall at z=27, jettying out to z=29)
    z 28..29    north pavement -- overhung by the top two storeys
    z 30..34    carriageway
    z 35..36    south pavement
    z 37..48    south terrace (front wall at z=37, jettying out to z=35)

Nine blocks of street at the cobbles, five at the eaves, eighteen of building either side. That
ratio is the whole point: Diagon Alley is a canyon, and a canyon is what makes Gringotts landing at
the end of it feel like an event.

The street CURVES. `curve()` offsets the entire section in Z as a function of X, and each shop takes
the curve value at its own centre, so the frontage advances in steps rather than bending. Stepped
frontage is what makes the shops look like they are jostling each other for room; a genuinely curved
wall just looks melted. On top of that every shop gets a one-block jitter, so the building line is
never straight for more than two shops together.

Coordinates here are free -- negative is fine -- because `normalise()` shifts everything into the
positive octant at the end, which is the only frame a structure template can store.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from diagon_kit import (                                                        # noqa: E402
    noise, tex, put, fill, fill_tex, stair, slab, entity, build_template, write_nbt,
    COBBLE, KERB, GUTTER, PLINTH, BRICK, MARBLE, MARBLE_TRIM,
    FRAME_DARK, FRAME_MID, FRAME_GREY, PAINTS, ROOFS,
    GLASS_LIT, GLASS_PLAIN, GLASS_DARK,
    door, trapdoor, hanging_sign, wall_sign, standing_sign, villager, lantern, chain_lamp,
    connect_all, pitched_roof, chimney, shopfront, ShopSpec, text_component, loot_chest, barrel,
    crate_pile, flower_box,
)
# blit is not re-exported by diagon_kit, so it comes straight from the other build script.
from generate_biome_structures import blit                                      # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources',
                   'data', 'hogwartsmagic', 'structure')
VIEWER = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                      '.claude', 'skills', 'rendering-minecraft-structures', 'textured', 'build')

ARCH_BRICK = 'hogwartsmagic:diagon_archway_brick'

# --- the section -------------------------------------------------------------------------------
ZC = 32                 # centre line of the carriageway
NORTH_FRONT = ZC - 5    # z of the north terrace ground-floor wall
SOUTH_FRONT = ZC + 5
TERRACE_DEPTH = 12

X_WALL = 14             # the brick wall
X_ALLEY0 = 15
X_ALLEY1 = 117
X_SQUARE1 = 132
# The bank is the imported building, and it is a hundred blocks deep -- three times the frontage it
# shows the street. Its own aprons pave the ground under it, so the street's paving stops at the
# square rather than running on underneath it.
X_BANK0 = X_SQUARE1 + 1                 # local x=0 of the imported schematic
X_BANK1 = X_BANK0 + 99                  # ...which is 100 long

_GRINGOTTS_CACHE = None


def curve(x):
    """How far the whole street has wandered in Z by this point along it.

    Two sines of different period: the long one bends the street so Gringotts is hidden until you
    are two thirds of the way down, the short one keeps the bend from reading as a single arc.
    """
    return int(round(7.0 * math.sin(x * math.pi / 132.0)
                     - 2.6 * math.sin(x * math.pi / 43.0)))


def jitter(name):
    """A stable -1/0/+1 per shop, so the building line is never straight."""
    return (abs(hash(name)) % 3) - 1


# =================================================================================================
# The street surface
# =================================================================================================

def pave(b):
    """Cobbles, with pavement bands, a kerb line and a gutter down the middle.

    The carriageway mix is deliberately tight -- cobblestone, andesite, stone, a little moss and a
    very little gravel -- because paving is the largest single surface in the build and anything
    with real contrast in it turns the whole street into a chessboard.
    """
    for x in range(0, X_SQUARE1 + 1):
        c = curve(x)
        z0, z1 = ZC - 8 + c, ZC + 8 + c
        for z in range(z0, z1 + 1):
            d = abs(z - (ZC + c))
            if d >= 7:
                blk = tex(KERB, x, 0, z, 11)            # under the shopfronts
            elif d >= 5:
                blk = tex(KERB, x, 0, z, 12)            # pavement
            elif d == 0 and x % 7 != 0:
                blk = tex(GUTTER, x, 0, z, 13)          # gutter channel down the crown
            else:
                blk = tex(COBBLE, x, 0, z, 14)
            put(b, (x, 0, z), blk)
        # kerb stones between pavement and carriageway
        for s in (-1, 1):
            put(b, (x, 0, ZC + c + s * 5), 'minecraft:polished_andesite')
        # foundation course so the street is not floating when the placer sinks it
        for z in range(z0, z1 + 1):
            put(b, (x, -1, z), 'minecraft:stone')


def street_furniture(b):
    """Lamps, barrows, crates and puddles down the carriageway.

    Everything is hung off something -- lamp posts have a base and a bracket, barrows have wheels --
    because the fastest way to make a street look generated is to leave objects floating or to space
    them evenly.  Position and spacing are both hashed.
    """
    x = X_ALLEY0 + 6
    while x < X_ALLEY1 - 4:
        c = curve(x)
        side = 1 if noise(x, 0, 0, 21) > 0.5 else -1
        z = ZC + c + side * 5

        # lamp standard: plinth, post, bracket, hanging lantern
        put(b, (x, 1, z), 'minecraft:cobblestone_wall')
        for y in (2, 3, 4):
            put(b, (x, y, z), 'minecraft:iron_bars')
        put(b, (x, 5, z), 'minecraft:dark_oak_fence')
        chain_lamp(b, x, 5, z - side, drop=1)
        put(b, (x, 5, z - side), 'minecraft:chain')

        # a barrow or a crate pile opposite, further along
        bx = x + 4 + int(noise(x, 1, 0, 22) * 5)
        if bx < X_ALLEY1 - 6:
            bc = curve(bx)
            bz = ZC + bc - side * 4
            roll = noise(bx, 0, bz, 23)
            if roll < 0.45:
                _barrow(b, bx, bz, 'east' if side > 0 else 'west')
            else:
                crate_pile(b, bx, 1, bz, 24)
        x += 11 + int(noise(x, 2, 0, 25) * 5)

    # puddles in the gutter, where a gutter would actually hold water
    for x in range(X_ALLEY0, X_ALLEY1, 3):
        c = curve(x)
        if noise(x, 0, 0, 26) > 0.72:
            put(b, (x, 0, ZC + c), 'minecraft:water')


def _barrow(b, x, z, facing):
    """A market barrow: two wheels, a bed of planks, a canopy and goods on top."""
    put(b, (x, 1, z), 'minecraft:dark_oak_slab')
    put(b, (x, 1, z + 1), 'minecraft:dark_oak_slab')
    put(b, (x - 1, 1, z), 'minecraft:oak_trapdoor')
    b[(x - 1, 1, z)] = ('minecraft:oak_trapdoor',
                        {'facing': facing, 'half': 'bottom', 'open': 'true',
                         'powered': 'false', 'waterlogged': 'false'}, None)
    put(b, (x, 2, z), 'minecraft:barrel')
    if noise(x, 2, z, 31) > 0.5:
        put(b, (x, 2, z + 1), 'minecraft:hay_block')
    else:
        put(b, (x, 2, z + 1), 'minecraft:pumpkin')
    for dz in (0, 1):
        put(b, (x + 1, 3, z + dz), 'minecraft:dark_oak_fence')
    for dz in (0, 1):
        b[(x + 1, 4, z + dz)] = ('minecraft:red_wool', None, None)


# =================================================================================================
# The courtyard and the brick wall
# =================================================================================================

def courtyard(b):
    """The walled yard behind the Leaky Cauldron, and the wall that opens.

    The wall panel is one block type on purpose: the archway animation finds its own extent by
    flood-filling that block, so anything mixed into the panel would tear the arch.
    """
    c = curve(X_WALL)
    z0, z1 = ZC - 8 + c, ZC + 8 + c

    # yard floor and the walls around three sides
    for x in range(0, X_WALL):
        for z in range(z0, z1 + 1):
            put(b, (x, 0, z), tex(KERB, x, 0, z, 41))
            put(b, (x, -1, z), 'minecraft:stone')
    for x in range(0, X_WALL):
        for y in range(1, 7):
            put(b, (x, y, z0), tex(BRICK, x, y, z0, 42))
            put(b, (x, y, z1), tex(BRICK, x, y, z1, 42))
    for z in range(z0, z1 + 1):
        for y in range(1, 7):
            put(b, (0, y, z), tex(BRICK, 0, y, z, 42))
    # coping along the top of the yard walls
    for x in range(0, X_WALL):
        slab(b, (x, 7, z0), 'minecraft:stone_brick_slab')
        slab(b, (x, 7, z1), 'minecraft:stone_brick_slab')
    for z in range(z0, z1 + 1):
        slab(b, (0, 7, z), 'minecraft:stone_brick_slab')

    # the door back into the pub
    door(b, 1, 1, ZC + c, 'minecraft:dark_oak_door', 'east')
    wall_sign(b, (0, 3, ZC + c), 'dark_oak', 'east',
              ['The Leaky', 'Cauldron', '', 'this way'], 'gold')
    for dz in (-1, 1):
        chain_lamp(b, 1, 5, ZC + c + dz, drop=1)

    # THE WALL.  Full height, full width of the yard, so it is unmistakably a dead end.
    # lit=false explicitly: the block has a lit variant for the seam of light that runs across it
    # when the arch opens, and a template state with the property missing matches no variant at all.
    for z in range(z0 + 1, z1):
        for y in range(1, 8):
            b[(X_WALL, y, z)] = (ARCH_BRICK, {'lit': 'false'}, None)
    # a stone frame around the panel, so the arch has something to open within
    for y in range(1, 9):
        put(b, (X_WALL, y, z0), tex(PLINTH, X_WALL, y, z0, 43))
        put(b, (X_WALL, y, z1), tex(PLINTH, X_WALL, y, z1, 43))
    for z in range(z0, z1 + 1):
        put(b, (X_WALL, 8, z), tex(PLINTH, X_WALL, 8, z, 43))

    # THE DUSTBIN.  Three up and two across from it is the brick that opens the wall, so it is not
    # decoration -- it is the instruction, and it has to be the most obvious object in the yard.
    dz = ZC + c + 2
    put(b, (X_WALL - 1, 1, dz), 'minecraft:cauldron')
    # half=bottom, so the lid sits ON the bin. As a top-half trapdoor it hangs at the top of the
    # block above and reads as a dinner plate floating a block over the rubbish.
    b[(X_WALL - 1, 2, dz)] = ('minecraft:iron_trapdoor',
                              {'facing': 'west', 'half': 'bottom', 'open': 'false',
                               'powered': 'false', 'waterlogged': 'false'}, None)
    # rubbish around its foot
    put(b, (X_WALL - 2, 1, dz + 1), 'minecraft:brown_carpet')
    put(b, (X_WALL - 1, 1, dz - 2), 'minecraft:composter')

    # weeds and damp in the corners -- an unused yard
    for z in range(z0 + 1, z1):
        for x in range(1, X_WALL):
            if noise(x, 0, z, 44) > 0.86:
                put(b, (x, 1, z), 'minecraft:short_grass')
            elif noise(x, 0, z, 45) > 0.93:
                put(b, (x, 0, z), 'minecraft:mossy_cobblestone')

    standing_sign(b, (X_WALL - 3, 1, ZC + c - 4), 'dark_oak', 'west',
                  ['Three up', 'two across', 'from the bin', ''], 'gray')


# =================================================================================================
# The shops
# =================================================================================================
#
# name, x0, x1, storeys, paint, roof, ridge, frame, glass, sign colour, keeper profession + name
NORTH = [
    ('Eeylops Owl Emporium', 17, 28, (5, 4, 4), 'ochre', 'shingle', 'x', FRAME_MID,
     'hogwartsmagic:owl_merchant', 'Eeylops'),
    ('Flourish and Blotts', 29, 42, (5, 4, 4, 4), 'green', 'slate', 'z', FRAME_DARK,
     'hogwartsmagic:bookseller', 'Mr Blotts'),
    ('Slug and Jiggers Apothecary', 43, 53, (5, 4, 4), 'bottle', 'tile', 'x', FRAME_DARK,
     'hogwartsmagic:apothecary', 'Mr Jiggers'),
    ('Quality Quidditch Supplies', 54, 66, (5, 4, 4, 4), 'maroon', 'slate', 'z', FRAME_MID,
     'hogwartsmagic:broom_merchant', 'Mr Fenwick'),
    ('Scribbulus Writing Implements', 67, 76, (5, 4, 4), 'navy', 'lead', 'x', FRAME_GREY,
     'hogwartsmagic:scribe', 'Mr Scribbulus'),
    ("Weasleys Wizard Wheezes", 77, 93, (5, 4, 4, 4, 4), 'orange', 'copper', 'z', FRAME_MID,
     'hogwartsmagic:joke_merchant', 'George Weasley'),
    ('Magical Menagerie', 94, 105, (5, 4, 4), 'plum', 'shingle', 'x', FRAME_DARK,
     'hogwartsmagic:menagerist', 'Miss Prewett'),
    ('Twilfitt and Tattings', 106, 117, (5, 4, 4, 4), 'navy', 'slate', 'z', FRAME_DARK,
     'hogwartsmagic:robemaker', 'Mr Twilfitt'),
]

SOUTH = [
    ("Potages Cauldron Shop", 17, 27, (5, 4, 4), 'iron', 'tile', 'x', FRAME_GREY,
     'hogwartsmagic:cauldron_smith', 'Mr Potage'),
    ("Madam Malkins Robes", 28, 40, (5, 4, 4, 4), 'teal', 'slate', 'z', FRAME_MID,
     'hogwartsmagic:robemaker', 'Madam Malkin'),
    ('Ollivanders', 41, 52, (5, 4, 4), 'dust', 'shingle', 'x', FRAME_GREY,
     'hogwartsmagic:wandmaker', 'Garrick Ollivander'),
    ("Florean Fortescues", 53, 64, (5, 4), 'cream', 'tile', 'x', FRAME_MID,
     'hogwartsmagic:confectioner', 'Florean Fortescue'),
    ('Gambol and Japes', 65, 75, (5, 4, 4), 'maroon', 'lead', 'z', FRAME_DARK,
     'hogwartsmagic:joke_merchant', 'Mr Gambol'),
    ("Wiseacres Wizarding Equipment", 76, 88, (5, 4, 4, 4), 'brown', 'slate', 'x', FRAME_DARK,
     'hogwartsmagic:scribe', 'Mr Wiseacre'),
    ('Second Hand Brooms', 89, 97, (5, 4, 4), 'dust', 'shingle', 'z', FRAME_GREY,
     'hogwartsmagic:broom_merchant', 'Old Barnaby'),
    ('Mulpeppers Apothecary', 105, 117, (5, 4, 4, 4), 'soot', 'tile', 'z', FRAME_DARK,
     'hogwartsmagic:apothecary', 'Mr Mulpepper'),
]

# The sign on Ollivanders is the only one in the alley that is gilded and glowing: it is the shop
# the whole build exists to lead you to.
SIGN_STYLE = {
    'Ollivanders': ('gold', True, ['Ollivanders', 'Makers of Fine', 'Wands since', '382 BC']),
    "Weasleys Wizard Wheezes": ('yellow', True, None),
    'Gringotts': ('gold', True, None),
}


def build_shops(b, ents):
    for terrace, side in ((NORTH, 1), (SOUTH, -1)):
        for (name, x0, x1, storeys, paint, roof, ridge, frame, prof, keeper) in terrace:
            xm = (x0 + x1) // 2
            base = NORTH_FRONT if side > 0 else SOUTH_FRONT
            zf = base + curve(xm) + jitter(name) * side
            colour, glow, lines = SIGN_STYLE.get(name, ('black', False, None))
            spec = ShopSpec(
                name, x0, x1, zf, side,
                depth=TERRACE_DEPTH, storeys=storeys, paint=paint, frame=frame,
                roof=roof, ridge=ridge,
                sign=name, sign_colour=colour, sign_glow=glow,
                jetty=[0, 1, 2, 2, 2][:len(storeys)],
                glass=GLASS_DARK if paint in ('soot', 'bottle') else GLASS_LIT,
                awning=(abs(hash(name)) % 3 == 0),
                chimneys=(0.25, 0.8) if len(storeys) >= 4 else (0.35,),
            )
            if lines:
                spec.sign = name
            shopfront(b, spec)
            _shop_interior(b, ents, spec, prof, keeper)


def _shop_interior(b, ents, s, profession, keeper):
    """Counter, keeper, stock and a fire.  A shop you can see into through a lit window has to have
    something behind the glass or the lantern trick reads as an empty box with a lamp in it."""
    side = s.side
    fz = s.front_z(0)
    inz = fz - side * 3                      # three back from the glass: the counter line
    x0, x1 = s.x0 + 1, s.x1 - 1
    cx = (x0 + x1) // 2

    # counter across most of the width, with a gap to get behind it
    for x in range(x0, x1 + 1):
        if x == cx:
            continue
        put(b, (x, 1, inz), 'minecraft:dark_oak_slab')
        slab(b, (x, 1, inz), 'minecraft:dark_oak_slab', 'top')

    # shelves and stock on the back wall
    bz = s.back_z + side
    for x in range(x0, x1 + 1):
        for y in (2, 3):
            if noise(x, y, bz, s.salt) > 0.25:
                put(b, (x, y, bz), 'minecraft:bookshelf' if 'book' in profession
                    else 'minecraft:barrel')
    # hearth
    put(b, (x0, 1, bz), 'minecraft:campfire')
    b[(x0, 1, bz)] = ('minecraft:campfire',
                      {'facing': s.facing_out, 'lit': 'true', 'signal_fire': 'false',
                       'waterlogged': 'false'}, None)

    # ceiling lantern
    chain_lamp(b, cx, s.storeys[0] - 1, inz - side, drop=1)

    # the keeper, behind his counter
    ents.append(villager(cx, 1, inz - side, profession, keeper))

    # a stock chest the player can actually loot
    loot_chest(b, (x1, 1, bz - side), s.facing_out, 'hogwartsmagic:chests/diagon_shop')


# =================================================================================================
# Character pieces -- the things that make a shop recognisable at a glance
# =================================================================================================

def character(b, ents):
    """Per-shop flourishes.  The kit gives every shop the same bones; this is what makes one of
    them Ollivanders and another one Weasleys."""

    # --- Ollivanders: the wall of wand boxes, the ladder, the single wand in the window ---------
    o = _spec_of(SOUTH, 'Ollivanders')
    ox0, ox1, ozf, oside = o[0], o[1], o[2], -1
    inz = ozf + 3
    backz = ozf + TERRACE_DEPTH - 2
    # thousands of boxes: barrels stacked floor to ceiling on both party walls
    for z in range(inz, backz + 1):
        for y in range(1, 4):
            put(b, (ox0 + 1, y, z), 'minecraft:barrel')
            put(b, (ox1 - 1, y, z), 'minecraft:barrel')
    # the ladder on its rail
    for y in range(1, 5):
        b[(ox0 + 2, y, backz)] = ('minecraft:ladder', {'facing': 'north', 'waterlogged': 'false'},
                                  None)
    # the wand on a cushion in the window, on a faded purple stand
    wx = (ox0 + ox1) // 2
    put(b, (wx, 1, ozf - 1), 'minecraft:purple_carpet')
    b[(wx, 1, ozf - 1)] = ('minecraft:purple_carpet', None, None)
    # the workbench, which is also the wandmaker's POI so he stays put
    put(b, (wx, 1, inz), 'hogwartsmagic:wand_workbench')
    # dust: cobwebs, but eight of them, not eighty
    for i, (dx, dy, dz) in enumerate([(1, 4, 2), (-1, 4, 3), (2, 4, 6), (-2, 4, 7),
                                      (1, 4, 9), (-1, 4, 5), (0, 4, 8), (2, 4, 4)]):
        put(b, (wx + dx, dy, ozf + dz), 'minecraft:cobweb')

    # --- Weasleys: the giant figure, and a facade that refuses to match anything -----------------
    w = _spec_of(NORTH, "Weasleys Wizard Wheezes")
    wx0, wx1, wzf = w[0], w[1], w[2]
    _weasley_giant(b, (wx0 + wx1) // 2, wzf + 3, 6)
    # purple against the orange, in bands, because the film's facade is genuinely garish
    for x in range(wx0, wx1 + 1):
        for y in (10, 15):
            for dz in (0,):
                if (x, y, wzf + 2 + dz) in b:
                    put(b, (x, y, wzf + 2 + dz), 'minecraft:purple_terracotta')

    # --- the broom shops: a broom on a stand in the window, and a bench to work at ---------------
    # The bench is not decoration: it is the broom_merchant profession's point of interest, and a
    # profession that has one needs the block present or a passing villager can never take the job.
    for terrace, nm in ((NORTH, 'Quality Quidditch Supplies'), (SOUTH, 'Second Hand Brooms')):
        q = _spec_of(terrace, nm)
        sgn = 1 if terrace is NORTH else -1
        qx = (q[0] + q[1]) // 2
        b[(qx, 2, q[2] + sgn)] = ('minecraft:dark_oak_fence', None, None)
        b[(qx + 1, 2, q[2] + sgn)] = ('minecraft:dark_oak_fence', None, None)
        put(b, (qx - 2, 1, q[2] - sgn * 5), 'hogwartsmagic:broom_workbench')

    # --- Eeylops: cages, perches and owls --------------------------------------------------------
    e = _spec_of(NORTH, 'Eeylops Owl Emporium')
    ex0, ex1, ezf = e[0], e[1], e[2]
    for i, x in enumerate(range(ex0 + 2, ex1 - 1, 3)):
        for y in (2, 4):
            for z in (ezf - 3, ezf - 6):
                put(b, (x, y, z), 'minecraft:iron_bars')
                put(b, (x, y - 1, z), 'hogwartsmagic:owl_perch')
    for i, x in enumerate(range(ex0 + 2, ex1 - 1, 4)):
        ents.append(entity(x + 0.5, 3.0, ezf - 3.5, 'hogwartsmagic:owl', {
            'PersistenceRequired': ('byte', 1)}))

    # --- Fortescue's: tables and parasols spilling onto the pavement ----------------------------
    f = _spec_of(SOUTH, "Florean Fortescues")
    fx0, fx1, fzf = f[0], f[1], f[2]
    for i, x in enumerate(range(fx0 + 2, fx1 - 1, 4)):
        tz = fzf - 2
        put(b, (x, 1, tz), 'minecraft:dark_oak_fence')
        slab(b, (x, 2, tz), 'minecraft:dark_oak_slab', 'top')
        for dx, dz in ((-1, 0), (1, 0), (0, -1)):
            put(b, (x + dx, 1, tz + dz), 'minecraft:dark_oak_stairs')
            stair(b, (x + dx, 1, tz + dz), 'minecraft:dark_oak_stairs',
                  'east' if dx < 0 else ('west' if dx > 0 else 'south'))
        # Parasol.  Canvas, not confectionery: solid pink_wool read as a single hot pink mass at
        # eye height in a five-block street, which was the loudest thing in the whole build.  White
        # canopy with a coloured rim gives the same cheerful read at a fraction of the weight.
        put(b, (x, 3, tz), 'minecraft:dark_oak_fence')
        put(b, (x, 4, tz), 'minecraft:white_wool')
        rim = 'minecraft:pink_wool' if i % 2 else 'minecraft:light_gray_wool'
        for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            put(b, (x + dx, 4, tz + dz), rim)

    # --- the apothecaries: cauldrons, and jars that catch the light ------------------------------
    for terrace, nm in ((NORTH, 'Slug and Jiggers Apothecary'), (SOUTH, 'Mulpeppers Apothecary')):
        a = _spec_of(terrace, nm)
        ax0, ax1, azf = a[0], a[1], a[2]
        sgn = 1 if terrace is NORTH else -1
        # In the window, not behind the counter: z=-4 is the keeper's own row.
        for x in range(ax0 + 2, ax1 - 1, 3):
            put(b, (x, 1, azf - sgn * 2), 'hogwartsmagic:wizard_cauldron')
            put(b, (x, 3, azf - sgn * 5), 'hogwartsmagic:candle_cluster')

    # --- Flourish and Blotts: books, in quantity ------------------------------------------------
    fb = _spec_of(NORTH, 'Flourish and Blotts')
    for x in range(fb[0] + 2, fb[1] - 1):
        for y in (1, 2, 3):
            if noise(x, y, 0, 77) > 0.35:
                put(b, (x, y, fb[2] - 8), 'minecraft:bookshelf')
        if noise(x, 0, 1, 78) > 0.6:
            put(b, (x, 1, fb[2] - 3), 'hogwartsmagic:enchanted_book_pile')


def _spec_of(terrace, name):
    """(x0, x1, zf) for a shop already laid out, so character pieces can find it."""
    for row in terrace:
        if row[0] == name:
            x0, x1 = row[1], row[2]
            side = 1 if terrace is NORTH else -1
            base = NORTH_FRONT if side > 0 else SOUTH_FRONT
            zf = base + curve((x0 + x1) // 2) + jitter(name) * side
            return (x0, x1, zf)
    raise KeyError(name)


def _weasley_giant(b, x, z, y0):
    """The animated Weasley on the shopfront: a figure two storeys high, lifting his hat.

    Built as a flat relief on the facade rather than a free-standing statue, which is how the film
    does it and which is the only way it reads at this scale.
    """
    # head
    fill(b, x - 1, y0 + 4, z, x + 1, y0 + 6, z, 'minecraft:white_concrete')
    put(b, (x - 1, y0 + 5, z), 'minecraft:black_concrete')
    put(b, (x + 1, y0 + 5, z), 'minecraft:black_concrete')
    # hat, lifted clear of the head
    fill(b, x - 2, y0 + 8, z, x + 2, y0 + 8, z, 'minecraft:purple_concrete')
    fill(b, x - 1, y0 + 9, z, x + 1, y0 + 10, z, 'minecraft:purple_concrete')
    # body and the arm that holds the hat up
    fill(b, x - 2, y0, z, x + 2, y0 + 3, z, 'minecraft:orange_concrete')
    for i, y in enumerate(range(y0 + 3, y0 + 8)):
        put(b, (x + 2 + (i > 1), y, z), 'minecraft:white_concrete')
    # hair
    fill(b, x - 1, y0 + 7, z, x + 1, y0 + 7, z, 'minecraft:orange_terracotta')


# =================================================================================================
# Knockturn Alley
# =================================================================================================

def knockturn(b, ents):
    """A gap between two shops that turns into a lane.  Narrower, darker and dirtier: five blocks
    wide against the alley's nine, soul lanterns instead of lanterns, and the buildings meet
    overhead so it is roofed for most of its length."""
    x0, x1 = 98, 104
    c = curve((x0 + x1) // 2)
    z0 = SOUTH_FRONT + c
    z1 = z0 + 26

    # the lane floor, and the walls of the buildings that form it
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            put(b, (x, 0, z), tex([('minecraft:cobblestone', 6), ('minecraft:mossy_cobblestone', 5),
                                   ('minecraft:andesite', 2), ('minecraft:gravel', 2)], x, 0, z, 51))
            put(b, (x, -1, z), 'minecraft:stone')

    for z in range(z0, z1 + 1):
        for y in range(1, 15):
            put(b, (x0, y, z), tex(PAINTS['soot'], x0, y, z, 52))
            put(b, (x1, y, z), tex(PAINTS['dust'], x1, y, z, 53))
    # roofed over from z0+6 onward -- the buildings lean until they touch
    for z in range(z0 + 6, z1 + 1):
        for x in range(x0 + 1, x1):
            put(b, (x, 14, z), tex(PAINTS['soot'], x, 14, z, 54))
    for z in range(z0, z0 + 6):
        for x in range(x0 + 1, x1):
            if noise(x, 0, z, 55) > 0.55:
                put(b, (x, 14, z), tex(PAINTS['soot'], x, 14, z, 54))

    # soul lanterns, sparse, so it is genuinely dim
    for z in range(z0 + 4, z1, 6):
        chain_lamp(b, x0 + 1, 6, z, drop=1, soul=True)

    # damp, and things growing where nothing should
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            n = noise(x, 1, z, 56)
            if n > 0.9:
                put(b, (x, 1, z), 'minecraft:brown_mushroom')
            elif n > 0.86:
                put(b, (x, 0, z), 'minecraft:water')
    for x in (x0 + 1, x1 - 1):
        for z in range(z0 + 2, z1, 5):
            put(b, (x, 12, z), 'minecraft:cobweb')

    standing_sign(b, (x0 + 1, 1, z0 + 1), 'dark_oak', 'north',
                  ['Knockturn', 'Alley', '', ''], 'dark_gray')

    _borgin_and_burkes(b, ents, x0, x1, z0 + 12)


def _borgin_and_burkes(b, ents, x0, x1, z):
    """The black shop.  Its window is the only thing in the lane with any light in it, and what is
    in the window is a hand."""
    front = z - 1
    for x in range(x0 + 1, x1):
        for y in range(1, 6):
            put(b, (x, y, front), tex(PAINTS['soot'], x, y, front, 61))
    # window with a display
    for x in range(x0 + 2, x1 - 1):
        for y in (2, 3):
            b[(x, y, front)] = (tex(GLASS_DARK, x, y, front, 62), {}, None)
    door(b, x0 + 1, 1, front, 'minecraft:dark_oak_door', 'north')
    hanging_sign(b, (x1 - 1, 4, front - 1), 'dark_oak', 'north',
                 ['Borgin', 'and Burkes', '', ''], 'red')
    # the shop behind the glass
    fill(b, x0 + 1, 1, z, x1 - 1, 4, z + 6, 'minecraft:air')
    for x in range(x0 + 1, x1):
        for y in range(1, 6):
            put(b, (x, y, z + 7), tex(PAINTS['soot'], x, y, z + 7, 61))
            put(b, (x0, y, z + 7), tex(PAINTS['soot'], x0, y, z + 7, 61))
    for x in range(x0 + 1, x1):
        put(b, (x, 5, z + 3), tex(PAINTS['soot'], x, 5, z + 3, 61))
    put(b, (x0 + 2, 1, z + 1), 'minecraft:dark_oak_slab')
    slab(b, (x0 + 2, 1, z + 1), 'minecraft:dark_oak_slab', 'top')
    # the vanishing cabinet, and a skull or two
    fill(b, x1 - 1, 1, z + 5, x1 - 1, 3, z + 5, 'minecraft:dark_oak_planks')
    put(b, (x1 - 1, 2, z + 4), 'minecraft:dark_oak_door')
    door(b, x1 - 1, 1, z + 4, 'minecraft:dark_oak_door', 'north')
    put(b, (x0 + 1, 2, z + 2), 'minecraft:skeleton_skull')
    chain_lamp(b, x0 + 2, 4, z + 2, drop=1, soul=True)
    ents.append(villager(x0 + 2, 1, z + 2, 'hogwartsmagic:curio_dealer', 'Mr Borgin'))
    loot_chest(b, (x1 - 1, 1, z + 1), 'north', 'hogwartsmagic:chests/knockturn')


# =================================================================================================
# The square and Gringotts
# =================================================================================================

def square(b):
    """A widening at the end of the alley, so Gringotts has somewhere to be seen from."""
    for x in range(X_ALLEY1 + 1, X_SQUARE1 + 1):
        c = curve(x)
        for z in range(ZC - 14 + c, ZC + 15 + c):
            d = abs(z - (ZC + c))
            put(b, (x, 0, z), tex(KERB if d > 9 else COBBLE, x, 0, z, 71))
            put(b, (x, -1, z), 'minecraft:stone')

    cx, cz = (X_ALLEY1 + X_SQUARE1) // 2, ZC + curve((X_ALLEY1 + X_SQUARE1) // 2)
    # fountain: a raised kerb, water, and a column.  A pool flush with the paving reads as a puddle.
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            if dx * dx + dz * dz <= 18:
                put(b, (cx + dx, 1, cz + dz), 'minecraft:water')
            if 18 < dx * dx + dz * dz <= 26:
                put(b, (cx + dx, 1, cz + dz), tex(MARBLE_TRIM, cx + dx, 1, cz + dz, 72))
                slab(b, (cx + dx, 2, cz + dz), 'minecraft:quartz_slab')
    for y in range(1, 6):
        put(b, (cx, y, cz), tex(MARBLE, cx, y, cz, 73))
    put(b, (cx, 6, cz), 'minecraft:quartz_stairs')
    for d, f in (('north', (0, -1)), ('south', (0, 1)), ('east', (1, 0)), ('west', (-1, 0))):
        stair(b, (cx + f[0], 5, cz + f[1]), 'minecraft:quartz_stairs', d, 'top')
        put(b, (cx + f[0] * 2, 2, cz + f[1] * 2), 'minecraft:water')
    put(b, (cx, 7, cz), 'hogwartsmagic:arcane_cluster')

    # lamps around the square
    for dx, dz in ((-8, -8), (8, -8), (-8, 8), (8, 8)):
        put(b, (cx + dx, 1, cz + dz), 'minecraft:cobblestone_wall')
        for y in (2, 3, 4):
            put(b, (cx + dx, y, cz + dz), 'minecraft:iron_bars')
        lantern(b, (cx + dx, 5, cz + dz), hanging=False)


def load_gringotts():
    """The real Gringotts, read out of the schematic the mod itself ships.

    Read from `gringotts.schem` and not from the `gringotts.nbt` sitting in the render skill's build
    directory: that .nbt is gitignored and simply absent from a fresh clone, whereas the .schem is
    the tracked source of truth `GringottsSchematic.java` loads at runtime. Same building, one of
    them reproducible.

    It was lifted out of a downloaded 1.12 world by `extract_gringotts.py` -- 100 x 44 x 34 and
    21,286 blocks: a cathedral with a 59 x 23 banking hall, thirty-three blocks of clear height, a
    colonnade, six teller booths a side and a glass dome. It replaces the 34 x 24 x 24 portico that
    used to be hand-built here, which was a facade with a room behind it.
    """
    global _GRINGOTTS_CACHE
    if _GRINGOTTS_CACHE is not None:
        return _GRINGOTTS_CACHE

    import schem_to_nbt

    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main',
                        'resources', 'data', 'hogwartsmagic', 'schematic', 'gringotts.schem')
    root = schem_to_nbt.read_nbt(path)
    w, h, l, by_id, cells = schem_to_nbt.decode_schem(root)

    blocks = {}
    for index, state_id in enumerate(cells):
        state = by_id.get(state_id)
        if not state:
            continue
        name, props = schem_to_nbt.split_state(state)
        if name == 'minecraft:air':
            continue
        # Sponge indexes cells x + z*width + y*width*length -- the same order
        # GringottsSchematic.java reads them back in.
        x = index % w
        z = (index // w) % l
        y = index // (w * l)
        blocks[(x, y, z)] = (name, props or None, None)

    _GRINGOTTS_CACHE = (blocks, w, h, l)
    return _GRINGOTTS_CACHE


def gringotts(b, ents):
    """Paste the imported bank at the end of the street.

    Unrotated, because it already faces the right way. Its entrance is the -X face -- a three-wide,
    eight-tall double door on the centreline at local z=15 -- and the alley runs west to east with
    the bank closing the vista. So local x=0 goes at X_BANK0, the entrance-stair apron takes the
    first ten blocks, and the door lands about ten blocks in.

    Local y=0 is the first course above the ground the build was cut from, so lining it up with the
    street's top paving course puts the forecourt flush with the street and leaves the two shallow
    steps at local x 2..9 to do the climb to the threshold.

    Not mirrored, deliberately. `blit`'s mirror only rewrites `facing` and `shape`, and this
    building is full of `axis=` pillars, doors with `hinge`, and iron bars -- all of which would
    come out wrong.
    """
    src, w, h, l = load_gringotts()

    ox = X_BANK0
    oy = 0                                   # the street's paving course, in build coordinates
    oz = ZC + curve(X_BANK0) - 15            # local z=15 is the building's own centreline

    blit(b, src, ox, oy, oz)

    # The apse floor is open at local x 92..96, z 11..21 -- the descent to the vaults. With no
    # undercroft under the alley that is a pit in the middle of the banking hall floor, so deck it
    # over, and leave it as the obvious place to reconnect one later.
    #
    # Except for the one block that must survive: the undercroft station marker is a single
    # dark_prismarine at local (92, 2, 15), right in the middle of that hole, and
    # GringottsLandmarks.of() locates the whole undercroft by requiring *exactly one* of them and
    # refusing to guess otherwise. The first version of this decking paved straight over it and
    # silently took the station with it. Deck around whatever is already there rather than trusting
    # a hard-coded exception, so moving the marker in the schematic cannot break this again.
    for lx in range(92, 97):
        for lz in range(11, 22):
            pos = (ox + lx, oy + 2, oz + lz)
            if pos in b:
                continue
            put(b, pos, 'minecraft:polished_diorite')

    # The schematic carries no block entities -- the 1.12 tile entities were dropped during
    # extraction -- so the sign and the strongroom chests have to be added back.
    #
    # Their positions are *found*, not hard-coded. This building was not designed here, and the
    # first attempt at a wall sign beside the door would have overwritten its own quartz detailing:
    # local x=9 is already quartz_stairs at z=12 and chiseled_quartz_block at z=18. So look for a
    # cell that is genuinely empty with something solid under it, and put things only there.
    def free_spots(x0, x1, ly, z0, z1, want, full_block_below=True):
        """Empty cells with something to stand on. `full_block_below` because a chest on a half
        block sits wrong, while a sign on the forecourt steps -- which are slabs the whole way --
        is fine. Requiring a full block everywhere found nothing at all on the steps."""
        found = []
        for lx in range(x0, x1 + 1):
            for lz in range(z0, z1 + 1):
                here = (ox + lx, oy + ly, oz + lz)
                below = (ox + lx, oy + ly - 1, oz + lz)
                if here in b or below not in b:
                    continue
                partial = b[below][0].endswith(('_slab', '_stairs', '_pane', '_fence'))
                if full_block_below and partial:
                    continue
                if found and min(abs(lx - f[0]) + abs(lz - f[2]) for f in found) < 6:
                    continue                      # keep them apart
                found.append((lx, ly, lz))
                if len(found) >= want:
                    return found
        return found

    # Strongroom chests, in the vestibule between the door and the hall.
    for lx, ly, lz in free_spots(12, 24, 2, 10, 20, 4):
        loot_chest(b, (ox + lx, oy + ly, oz + lz), 'west', 'hogwartsmagic:chests/diagon_shop')

    # The sign, standing on the forecourt at the foot of the steps.
    spot = free_spots(2, 8, 2, 8, 22, 1, full_block_below=False)
    if spot:
        lx, ly, lz = spot[0]
        standing_sign(b, (ox + lx, oy + ly, oz + lz), 'dark_oak', 'west',
                      ['GRINGOTTS', 'Wizarding Bank'], 'gold', True)

    # No goblins are baked in. DiagonAlleyManager runs the bank's own furnishing pass after
    # placement, and that finds its teller positions from the gold pressure plates in the desk tops;
    # baking them here as well would staff the hall twice.
    return ox, oy, oz

# =================================================================================================
# Assembly
# =================================================================================================

def normalise(blocks, entities):
    """Shift into the positive octant.  Templates store no origin, so the minimum corner has to be
    (0,0,0); building in whatever frame suits the geometry and moving at the end is much less
    error-prone than threading an offset through every call."""
    mx = min(p[0] for p in blocks)
    my = min(p[1] for p in blocks)
    mz = min(p[2] for p in blocks)
    out = {(x - mx, y - my, z - mz): v for (x, y, z), v in blocks.items()}
    moved = []
    for e in entities:
        px, py, pz = [t[1] for t in e['pos'][2]]
        px, py, pz = px - mx, py - my, pz - mz
        e = dict(e)
        e['pos'] = ('list', 'double', [('double', px), ('double', py), ('double', pz)])
        e['blockPos'] = ('list', 'int', [('int', int(px)), ('int', int(py)), ('int', int(pz))])
        nbt = dict(e['nbt'][1])
        nbt['Pos'] = ('list', 'double', [('double', px), ('double', py), ('double', pz)])
        e['nbt'] = ('compound', nbt)
        moved.append(e)
    return out, moved, (-mx, -my, -mz)


def build():
    b, ents = {}, []
    pave(b)
    courtyard(b)
    build_shops(b, ents)
    character(b, ents)
    knockturn(b, ents)
    square(b)
    gringotts(b, ents)
    street_furniture(b)
    connect_all(b)
    return b, ents


def audit(blocks, entities):
    """Cheap structural checks.  These do not tell you it looks right -- only the renderer does
    that -- but they catch the failures that are invisible in a picture."""
    problems = []

    floating = 0
    for (x, y, z), (name, _, _) in blocks.items():
        if name in ('minecraft:lantern', 'minecraft:soul_lantern'):
            above = blocks.get((x, y + 1, z))
            below = blocks.get((x, y - 1, z))
            hang = (_, _, _) if False else None
            if above is None and below is None:
                floating += 1
    if floating:
        problems.append('%d lanterns with nothing above or below them' % floating)

    doors = {}
    for (x, y, z), (name, props, _) in blocks.items():
        if name.endswith('_door'):
            doors.setdefault((x, z), set()).add((props or {}).get('half'))
    half_doors = sum(1 for v in doors.values() if v != {'lower', 'upper'})
    if half_doors:
        problems.append('%d doors missing a half' % half_doors)

    for e in entities:
        px, py, pz = [t[1] for t in e['pos'][2]]
        feet = blocks.get((int(px), int(py), int(pz)))
        if feet is not None and feet[0] not in ('minecraft:air',) and _blocking(feet[0]):
            problems.append('entity %s embedded in %s at %d,%d,%d'
                            % (e['nbt'][1]['id'][1], feet[0], px, py, pz))

    return problems


def _blocking(name):
    partial = ('_slab', '_stairs', '_carpet', '_fence', '_pane', '_sign', '_door', '_trapdoor',
               '_button', '_pressure_plate', '_wall')
    if any(name.endswith(s) for s in partial):
        return False
    return name not in ('minecraft:air', 'minecraft:water', 'minecraft:cobweb',
                        'minecraft:short_grass', 'minecraft:lantern', 'minecraft:chain')


def census(blocks, top=22):
    from collections import Counter
    c = Counter(v[0] for v in blocks.values())
    total = sum(c.values())
    print('  %-44s %7s %6s' % ('block', 'count', 'share'))
    for name, n in c.most_common(top):
        print('  %-44s %7d %5.1f%%' % (name, n, 100.0 * n / total))
    lights = sum(n for name, n in c.items()
                 if any(k in name for k in ('lantern', 'campfire', 'torch', 'glowstone',
                                            'candle', 'froglight', 'sea_lantern')))
    print('  -- %d light sources, %d block types, %d blocks total' % (lights, len(c), total))
    return c


if __name__ == '__main__':
    blocks, entities = build()
    blocks, entities, shift = normalise(blocks, entities)
    size = [max(p[i] for p in blocks) + 1 for i in range(3)]

    problems = audit(blocks, entities)
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, 'diagon_alley.nbt')
    write_nbt(path, build_template(blocks, entities))

    print('diagon_alley  %dx%dx%d  %d blocks  %d entities  %d bytes  (shift %s)'
          % (size[0], size[1], size[2], len(blocks), len(entities),
             os.path.getsize(path), shift))
    census(blocks)
    for p in problems:
        print('  PROBLEM  ' + p)

    if os.path.isdir(VIEWER):
        import shutil
        shutil.copy(path, os.path.join(VIEWER, 'diagon_alley.nbt'))
        print('  copied to the viewer')
    raise SystemExit(1 if problems else 0)
