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
    x 118..132  the square, with the fountain, closed on both sides by four more shops
    x 133..232  Gringotts -- the building the mod already ships, imported from its schematic
                rather than hand-built here: a hundred blocks deep, thirty-four across, and a
                cathedral inside

Behind the shops, yards with a floor (the alley dimension is void underneath), and round the whole
thing a boundary built as the backs of more buildings -- windows, gables, chimneys -- rather than a
blank wall.

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
    crate_pile, flower_box, shash, fence_gate, wall_banner, door_x, PARTY,
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

# Every shop's spec once it is laid out, by name, so later passes ask rather than recompute.
SPECS = {}


def curve(x):
    """How far the whole street has wandered in Z by this point along it.

    Two sines of different period: the long one bends the street so Gringotts is hidden until you
    are two thirds of the way down, the short one keeps the bend from reading as a single arc.
    """
    return int(round(7.0 * math.sin(x * math.pi / 132.0)
                     - 2.6 * math.sin(x * math.pi / 43.0)))


def jitter(name):
    """A stable -1/0/+1 per shop, so the building line is never straight."""
    return (shash(name) % 3) - 1


# =================================================================================================
# The street surface
# =================================================================================================

def pave(b):
    """Cobbles, with pavement bands, a kerb line and a gutter down the middle.

    The carriageway mix is deliberately tight -- cobblestone, andesite, stone, a little moss and a
    very little gravel -- because paving is the largest single surface in the build and anything
    with real contrast in it turns the whole street into a chessboard.
    """
    # The section, measured from the crown of the road: carriageway out to 2, a kerb at 3, and
    # pavement from 4 to the shopfronts, which stand at about 5. The first version put the pavement
    # at 5 and 6 and the kerb at 5 -- which is *under* the front walls -- so the street was cobbles
    # wall to wall and the kerb line existed only inside the shops' floors.
    for x in range(0, X_SQUARE1 + 1):
        c = curve(x)
        z0, z1 = ZC - 8 + c, ZC + 8 + c
        for z in range(z0, z1 + 1):
            d = abs(z - (ZC + c))
            if d >= 4:
                blk = tex(KERB, x, 0, z, 12)            # pavement flags
            elif d == 3:
                blk = 'minecraft:polished_andesite'     # kerb stones
            elif d == 0 and x % 7 != 0:
                blk = tex(GUTTER, x, 0, z, 13)          # gutter channel down the crown
            else:
                blk = tex(COBBLE, x, 0, z, 14)
            put(b, (x, 0, z), blk)
        # foundation course so the street is not floating when the placer sinks it
        for z in range(z0, z1 + 1):
            put(b, (x, -1, z), 'minecraft:stone')


def street_furniture(b):
    """Lamps, barrows, crates and puddles down the carriageway.

    Everything is hung off something -- lamp posts have a base and a bracket, barrows have wheels --
    because the fastest way to make a street look generated is to leave objects floating or to space
    them evenly.  Position and spacing are both hashed.
    """
    # Lamps stand on the kerb. They used to stand at five out from the crown, which is the plane of
    # the shopfronts themselves, and since this pass runs after the shops it punched lamp posts
    # straight through windows, fascias and -- now and then -- both halves of a door.
    x = X_ALLEY0 + 6
    while x < X_ALLEY1 - 4:
        c = curve(x)
        side = 1 if noise(x, 0, 0, 21) > 0.5 else -1
        z = ZC + c + side * 3
        if not _near_door(b, x, z, side):
            gas_lamp(b, x, z)

        # a barrow or a crate pile on the pavement opposite, further along
        bx = x + 4 + int(noise(x, 1, 0, 22) * 5)
        if bx < X_ALLEY1 - 6:
            bc = curve(bx)
            bz = ZC + bc - side * 4
            if (not _near_door(b, bx, bz, -side) and not _near_door(b, bx + 1, bz, -side)
                    and all((bx + i, 1, bz) not in b for i in (-1, 0, 1, 2))):
                if noise(bx, 0, bz, 23) < 0.45:
                    _barrow(b, bx, bz)
                else:
                    crate_pile(b, bx, 1, bz, 24)
        x += 11 + int(noise(x, 2, 0, 25) * 5)

    # puddles in the gutter, where a gutter would actually hold water
    for x in range(X_ALLEY0, X_ALLEY1, 3):
        c = curve(x)
        if noise(x, 0, 0, 26) > 0.72 and b.get((x, 0, ZC + c), ('',))[0] != 'minecraft:water':
            put(b, (x, 0, ZC + c), 'minecraft:water')


def gas_lamp(b, x, z, base_y=1):
    """A street lamp: a dark iron post with the lantern standing on top, like a gas lamp.

    The old standard was three panes of iron bars on a cobblestone wall -- a post the thickness of a
    pencil, which disappeared against the facades -- with a fence bracket and a chain floating next
    to it. A wall-block post reads as cast iron at this scale, and a lantern standing on the post
    reads as a lamp from the far end of the street.
    """
    for y in range(base_y, base_y + 4):
        put(b, (x, y, z), 'minecraft:polished_blackstone_wall')
    lantern(b, (x, base_y + 4, z), hanging=False)


def _near_door(b, x, z, out):
    """Is there a door in the facade this pavement cell faces, at this column or the next one?

    `out` is the sign of the cell's offset from the crown of the road (+1 or -1): the facade lies
    further out in that direction, so the search walks away from the street until it meets a wall.
    """
    for dx in (-1, 0, 1):
        for dz in range(1, 5):
            cell = b.get((x + dx, 1, z + out * dz))
            if cell is None:
                continue
            if cell[0].endswith('_door'):
                return True
            break
    return False


def _barrow(b, x, z):
    """A market barrow, two long along the street: a bed of planks, a handle, goods and a canopy.

    Laid along X. Laid across the street it ran its second block into the shopfront behind it on the
    south side of the road.
    """
    slab(b, (x, 1, z), 'minecraft:dark_oak_slab', 'top')
    slab(b, (x + 1, 1, z), 'minecraft:dark_oak_slab', 'top')
    b[(x - 1, 1, z)] = ('minecraft:oak_trapdoor',
                        {'facing': 'west', 'half': 'bottom', 'open': 'true',
                         'powered': 'false', 'waterlogged': 'false'}, None)
    barrel(b, (x, 2, z))
    if noise(x, 2, z, 31) > 0.5:
        put(b, (x + 1, 2, z), 'minecraft:hay_block')
    else:
        put(b, (x + 1, 2, z), 'minecraft:carved_pumpkin', {'facing': 'south'})
    # a striped canopy on two poles, so the barrow reads as a stall from down the street
    put(b, (x, 3, z), 'minecraft:dark_oak_fence')
    put(b, (x + 1, 3, z), 'minecraft:dark_oak_fence')
    put(b, (x, 4, z), 'minecraft:red_wool')
    put(b, (x + 1, 4, z), 'minecraft:white_wool')


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
                awning=(shash(name) % 3 == 0),
                chimneys=(0.25, 0.8) if len(storeys) >= 4 else (0.35,),
            )
            # Explicit lines were declared for Ollivanders and then thrown away -- this used to
            # read `spec.sign = name`, so the most important sign in the street just said its name.
            spec.sign_lines = lines
            SPECS[name] = spec
            shopfront(b, spec)
            _shop_interior(b, ents, spec, prof, keeper)


def _shop_interior(b, ents, s, profession, keeper):
    """Counter, shelves, stock and a fire.

    The first version of this put a half-slab on the floor and called it a counter, stocked the back
    wall with barrels, and left it there. Standing inside one, the counter read as nothing at all and
    the barrels read as a solid brown wall -- the whole room was one brown, with no waist-height line
    for the eye and nothing to look at behind the keeper.

    So: the counter is a full block with a slab on top, which is the only way to get a waist-height
    horizontal in Minecraft; the shelving is chosen per trade and set into the back wall rather than
    stacked against it; and there is a runner on the floor and a second lamp, because a shop lit by
    one lantern four blocks up is a cave.
    """
    side = s.side
    fz = s.front_z(0)
    back = s.back_z
    zlo, zhi = min(fz, back), max(fz, back)
    x0, x1 = s.x0 + 1, s.x1 - 1
    cx = (x0 + x1) // 2
    inz = fz - side * 3                      # the counter line
    bz = back + side                          # one in from the back wall

    # --- plaster and a pale floor --------------------------------------------------------
    # Everything in here used to be dark oak: floor, counter, shelving, ceiling. A room made
    # of one brown has nothing for the eye to catch on -- the counter vanished into the floor
    # and the shelves vanished into the wall. Plastering the inside faces pale gives the brown
    # furniture something to sit against, which is the whole reason a shop interior reads.
    for z in range(zlo + 1, zhi):
        for y in range(1, s.storeys[0]):
            put(b, (s.x0, y, z), tex(PAINTS['plaster'], s.x0, y, z, s.salt + 21))
            put(b, (s.x1, y, z), tex(PAINTS['plaster'], s.x1, y, z, s.salt + 21))
    for x in range(x0, x1 + 1):
        for y in range(1, s.storeys[0]):
            put(b, (x, y, back), tex(PAINTS['plaster'], x, y, back, s.salt + 22))
    for x in range(x0, x1 + 1):
        for z in range(zlo + 1, zhi):
            put(b, (x, 0, z), tex([('minecraft:spruce_planks', 6),
                                   ('minecraft:oak_planks', 3)], x, 0, z, s.salt + 23))

    # --- the counter --------------------------------------------------------------------------
    # A full block with a slab on it: waist height, and the one strong horizontal in the room.
    for x in range(x0, x1 + 1):
        if x == cx:
            # A gate, not a gap. Villagers open doors but not gates, so with a plain gap every
            # keeper in the street eventually walked out from behind the counter, out of the shop
            # door and off down the alley. The player can still let themselves through.
            fence_gate(b, (x, 1, inz), 'dark_oak', s.facing_out)
            continue
        put(b, (x, 1, inz), 'minecraft:dark_oak_planks')
        slab(b, (x, 2, inz), 'minecraft:dark_oak_slab', 'bottom')
    # a lip along the customer side, so it reads as a counter and not a bench
    for x in range(x0, x1 + 1):
        if x != cx:
            stair(b, (x, 1, inz + side), 'minecraft:dark_oak_stairs', s.facing_out, 'top')

    # --- shelving, per trade -------------------------------------------------------------------
    # chiseled_bookshelf is the one vanilla block with visible compartments, which is exactly what
    # a wall of wand boxes or apothecary jars wants. Plain bookshelves for the booksellers.
    if 'wandmaker' in profession or 'apothecary' in profession or 'scribe' in profession:
        shelf = 'minecraft:chiseled_bookshelf'
    elif 'book' in profession:
        shelf = 'minecraft:bookshelf'
    else:
        shelf = 'minecraft:barrel'
    for x in range(x0, x1 + 1):
        for y in (2, 3, 4):
            if noise(x, y, bz, s.salt) > 0.22:
                if shelf == 'minecraft:chiseled_bookshelf':
                    b[(x, y, bz)] = (shelf, {'facing': s.facing_out}, None)
                else:
                    put(b, (x, y, bz), shelf)

    # --- floor, fire and light ------------------------------------------------------------------
    # A runner down the middle: the one piece of colour in a room of brown, and it points at the
    # counter.
    runner = {'plum': 'minecraft:purple_carpet', 'teal': 'minecraft:cyan_carpet',
              'maroon': 'minecraft:red_carpet', 'green': 'minecraft:green_carpet',
              'navy': 'minecraft:blue_carpet', 'orange': 'minecraft:orange_carpet',
              'dust': 'minecraft:brown_carpet', 'soot': 'minecraft:gray_carpet',
              }.get(s.paint, 'minecraft:brown_carpet')
    for z in range(zlo + 1, zhi):
        if (cx, 1, z) not in b:
            put(b, (cx, 1, z), runner)

    b[(x0, 1, bz)] = ('minecraft:campfire',
                      {'facing': s.facing_out, 'lit': 'true', 'signal_fire': 'false',
                       'waterlogged': 'false'}, None)

    ceiling = s.storeys[0] - 1
    chain_lamp(b, cx, ceiling, inz - side, drop=1)
    chain_lamp(b, cx, ceiling, fz - side * 1, drop=1)

    # A portrait on the customers' side wall, opposite the door, and candles floating over the
    # keeper's side of the counter. Nothing else in the room says "wizarding" rather than "shop".
    if door_x(s) == s.x0 + 1:
        put(b, (x1, 3, fz - 2 * side), 'hogwartsmagic:moving_portrait', {'facing': 'west'})
    else:
        put(b, (x0, 3, fz - 2 * side), 'hogwartsmagic:moving_portrait', {'facing': 'east'})
    for fx in (x0 + 1, x1 - 1):
        if (fx, 3, inz - 2 * side) not in b:
            put(b, (fx, 3, inz - 2 * side), 'hogwartsmagic:candle_cluster')

    ents.append(villager(cx, 1, inz - side, profession, keeper))
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
    # Thousands of boxes, up both walls. chiseled_bookshelf rather than barrels: it is the one
    # vanilla block with visible individual compartments, so a wall of it reads as a wall of
    # boxes. Barrels stacked the same way read as a wall of brown.
    # Three high, not four, and with gaps. Floor-to-ceiling on both walls with no break turned
    # the shop into a slot you could not see down: the lantern lit nothing and the ladder and
    # the workbench were hidden behind a solid brown mass. The gaps are where the ladder runs
    # and where light gets in.
    for z in range(inz, backz + 1):
        for y in range(1, 4):
            if noise(ox0, y, z, 31) > 0.18:
                b[(ox0 + 1, y, z)] = ('minecraft:chiseled_bookshelf', {'facing': 'east'}, None)
            if noise(ox1, y, z, 32) > 0.18:
                b[(ox1 - 1, y, z)] = ('minecraft:chiseled_bookshelf', {'facing': 'west'}, None)
    # the ladder on its rail
    for y in range(1, 5):
        b[(ox0 + 2, y, backz)] = ('minecraft:ladder', {'facing': 'north', 'waterlogged': 'false'},
                                  None)
    # The wand in the window: a single glowing wand standing on a faded purple plinth. This used to
    # be a purple carpet at ozf - 1, which on the south terrace is the far side of the glass -- the
    # cushion was lying on the pavement outside, with nothing on it.
    wx = (ox0 + ox1) // 2
    put(b, (wx, 1, ozf + 1), 'minecraft:purple_wool')
    put(b, (wx, 2, ozf + 1), 'minecraft:end_rod', {'facing': 'up'})
    # the workbench, which is also the wandmaker's POI so he stays put
    put(b, (wx, 1, inz), 'hogwartsmagic:wand_workbench')
    # Dust, in the corners where the ceiling meets the wall. Hung in mid-air they read as
    # snowflakes rather than as a shop nobody has swept in six hundred years.
    for dz in (3, 6, 9):
        put(b, (ox0 + 2, 4, ozf + dz), 'minecraft:cobweb')
        put(b, (ox1 - 2, 4, ozf + dz), 'minecraft:cobweb')

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

    # --- Potage's: the heap of cauldrons outside, every size, spilling across the pavement -------
    p = _spec_of(SOUTH, "Potages Cauldron Shop")
    px0, pzf = p[0], p[2]
    heap = [(0, 1, 0), (1, 1, 0), (2, 1, 0), (0, 1, -1), (1, 1, -1),
            (0, 2, 0), (1, 2, 0), (0, 3, 0)]
    for i, (dx, y, dz) in enumerate(heap):
        pos = (px0 + 3 + dx, y, pzf - 1 + dz)
        if pos in b and y == 1:
            continue
        if i % 3 == 1:
            put(b, pos, 'hogwartsmagic:wizard_cauldron', {'level': '0'})
        else:
            put(b, pos, 'minecraft:cauldron')

    # --- Flourish and Blotts: a book cart outside, so the bookshop reads from up the street -------
    fbs = SPECS['Flourish and Blotts']
    bx = fbs.x0 + 3 if door_x(fbs) != fbs.x0 + 1 else fbs.x1 - 4
    for dx in (0, 1):
        put(b, (bx + dx, 1, fbs.zf + 1), 'minecraft:bookshelf')
        put(b, (bx + dx, 2, fbs.zf + 1), 'hogwartsmagic:enchanted_book_pile')
    b[(bx + 2, 1, fbs.zf + 1)] = ('minecraft:lectern', {'facing': 'south', 'has_book': 'false',
                                                       'powered': 'false'}, None)

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
    s = SPECS[name]
    return (s.x0, s.x1, s.zf)


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
    """A gap between two shops that turns into a lane. Narrower, darker and dirtier than the alley:
    five wide at the cobbles against nine, soul lanterns instead of lanterns, and the buildings on
    either side jetty out twice so that by the third storey they almost touch -- the lane is a slot
    of sky for its first few yards and roofed after that.

    The first version had the lane and none of the buildings: two flat fourteen-high walls with no
    window or door in either, a lid, and mushrooms. It read as a service corridor. The lane is all
    shopfronts now, every one of them shuttered except the one at the end.
    """
    x0, x1 = 98, 104
    c = curve((x0 + x1) // 2)
    z0 = SOUTH_FRONT + c
    zb = z0 + 15                     # the front of Borgin and Burkes
    z1 = zb + 10                     # its back wall

    lane = [('minecraft:cobblestone', 6), ('minecraft:mossy_cobblestone', 4),
            ('minecraft:andesite', 2), ('minecraft:gravel', 1)]
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            put(b, (x, 0, z), tex(lane, x, 0, z, 51))
            put(b, (x, -1, z), 'minecraft:stone')

    # The walls, jettied twice: at the cobbles they stand at x0/x1, from 7 up one block in, from 11
    # up two in. Each jetty is carried on a course of upside-down stairs.
    tiers = ((1, 6, 0), (7, 10, 1), (11, 14, 2))
    for z in range(z0, zb):
        for (ya, yb, inset) in tiers:
            for y in range(ya, yb + 1):
                put(b, (x0 + inset, y, z), tex(PAINTS['soot'], x0 + inset, y, z, 52))
                put(b, (x1 - inset, y, z), tex(PAINTS['dust'], x1 - inset, y, z, 53))
            if inset:
                stair(b, (x0 + inset, ya - 1, z), 'minecraft:dark_oak_stairs', 'west', 'top')
                stair(b, (x1 - inset, ya - 1, z), 'minecraft:dark_oak_stairs', 'east', 'top')
        # floor beams along each jetty, timber against the render
        for (ya, yb, inset) in tiers:
            b[(x0 + inset, yb, z)] = ('minecraft:dark_oak_log', {'axis': 'z'}, None)
            b[(x1 - inset, yb, z)] = ('minecraft:dark_oak_log', {'axis': 'z'}, None)
        # the slot of sky at the top: open near the street, roofed deeper in
        if z >= z0 + 5 or noise(101, 0, z, 55) > 0.6:
            for x in range(x0 + 2, x1 - 1):
                put(b, (x, 15, z), tex(ROOF_SOOT, x, 15, z, 54))

    # Shopfronts down both sides: a window and a door in turn, all of them shut up.
    names_w = ['Noggin and Bonce', 'Cobbs and Co', 'Spindle and Bauble']
    names_e = ['Shrunken Heads', 'Mr Mulpepper', 'Noctem Candles']
    for wall_x, out, inward, names, salt in ((x0, -1, 'east', names_w, 0),
                                             (x1, 1, 'west', names_e, 7)):
        for k, z in enumerate(range(z0 + 2, zb - 2, 4)):
            if k % 2 == 0:
                # a window of grimy glass with something dim behind it
                for dz in (0, 1):
                    for y in (2, 3):
                        b[(wall_x, y, z + dz)] = (tex(GLASS_DARK, wall_x, y, z + dz, 62 + salt),
                                                  {}, None)
                        back = (wall_x + out, y, z + dz)
                        if back not in b:
                            glow = noise(wall_x, y, z + dz, 63 + salt) > 0.75
                            b[back] = ('minecraft:verdant_froglight' if glow
                                       else 'minecraft:black_terracotta',
                                       {'axis': 'y'} if glow else None, None)
                    slab(b, (wall_x - out, 1, z + dz), 'minecraft:dark_oak_slab', 'top')
            else:
                # an iron door: shut, and nobody in here is going to open it for you
                door(b, wall_x, 1, z, 'minecraft:iron_door', inward)
                for y in (1, 2):
                    if (wall_x + out, y, z) not in b:
                        put(b, (wall_x + out, y, z), 'minecraft:blackstone')
                put(b, (wall_x, 3, z), 'minecraft:dark_oak_log', {'axis': 'z'})
            # a projecting sign on each shop, readable from the far end of the lane
            if k < len(names):
                hanging_sign(b, (wall_x - out, 4, z), 'dark_oak', 'north',
                             _split_name(names[k]), 'gray')
            if noise(wall_x, 1, z, 64 + salt) > 0.55 and (wall_x - out, 1, z + 2) not in b:
                put(b, (wall_x - out, 1, z + 2), 'minecraft:skeleton_skull',
                    {'rotation': str(int(noise(wall_x, 2, z, 65) * 16))})

    # A gateway at the mouth: a black beam across, and the name hanging from it.
    for x in range(x0 + 1, x1):
        b[(x, 6, z0)] = ('minecraft:dark_oak_log', {'axis': 'x'}, None)
    b[(x0 + 3, 5, z0)] = ('minecraft:dark_oak_hanging_sign',
                          {'rotation': '8', 'attached': 'false', 'waterlogged': 'false'},
                          _ceiling_sign_nbt('minecraft:dark_oak_hanging_sign',
                                            ['', 'Knockturn', 'Alley', ''], 'gray'))

    # Soul lanterns, sparse, hung from the jetties, so it is genuinely dim.
    for z in range(z0 + 3, zb, 5):
        chain_lamp(b, x0 + 1, 6, z, drop=1, soul=True)
        chain_lamp(b, x1 - 1, 6, z + 2, drop=1, soul=True)

    # Damp: puddles, and fungus only where the roof keeps the sun off it.
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, zb):
            n = noise(x, 1, z, 56)
            if (x, 1, z) in b:
                continue
            if n > 0.92 and z >= z0 + 6:
                put(b, (x, 1, z), 'minecraft:brown_mushroom')
            elif n > 0.88:
                put(b, (x, 0, z), 'minecraft:water')
    for z in range(z0 + 3, zb, 4):
        put(b, (x0 + 2, 13, z), 'minecraft:cobweb')
        put(b, (x1 - 2, 12, z + 2), 'minecraft:cobweb')

    _borgin_and_burkes(b, ents, x0, x1, zb, z1)


ROOF_SOOT = [('minecraft:blackstone', 6), ('minecraft:polished_blackstone_bricks', 3),
             ('minecraft:deepslate_tiles', 2)]


def _split_name(text):
    """Two lines for a narrow projecting sign: 'Noggin and Bonce' -> 'Noggin', 'and Bonce'."""
    words = text.split()
    if len(words) == 1:
        return ['', words[0], '', '']
    return ['', words[0], ' '.join(words[1:]), '']


def _ceiling_sign_nbt(block_id, lines, colour, glowing=False):
    msgs = [text_component(l) for l in (list(lines) + ['', '', '', ''])[:4]]
    face = ('compound', {
        'messages': ('list', 'string', [('string', m) for m in msgs]),
        'color': ('string', colour),
        'has_glowing_text': ('byte', 1 if glowing else 0),
    })
    return {'id': block_id, 'front_text': face, 'back_text': face, 'is_waxed': ('byte', 1)}


def _borgin_and_burkes(b, ents, x0, x1, zb, z1):
    """The black shop at the dead end. Its window is the only warm light in the lane, and what is in
    the window is a skull on a cushion beside a cluster of candles.

    Inside: a counter with a gate (Mr Borgin stays behind it), the vanishing cabinet against the
    back wall, cases of things nobody should buy, and the chest of stock.
    """
    # front: timber posts, a bay of dark glass, a door
    for y in range(1, 7):
        for x in range(x0 + 1, x1):
            put(b, (x, y, zb), tex(PAINTS['soot'], x, y, zb, 61))
        put(b, (x0 + 1, y, zb), 'minecraft:dark_oak_log', {'axis': 'y'})
        put(b, (x1 - 1, y, zb), 'minecraft:dark_oak_log', {'axis': 'y'})
    for x in range(x0 + 1, x1):
        b[(x, 5, zb)] = ('minecraft:dark_oak_log', {'axis': 'x'}, None)
    for x in (x0 + 3, x0 + 4):
        for y in (2, 3, 4):
            b[(x, y, zb)] = (tex(GLASS_DARK, x, y, zb, 62), {}, None)
        put(b, (x, 1, zb), 'minecraft:polished_blackstone_bricks')
    door(b, x0 + 2, 1, zb, 'minecraft:dark_oak_door', 'north', hinge='right')
    hanging_sign(b, (x1 - 2, 4, zb - 1), 'dark_oak', 'west',
                 ['Borgin', 'and Burkes', '', ''], 'red', True)

    # the shell
    zi0, zi1 = zb + 1, z1 - 1
    for z in range(zi0, z1 + 1):
        for y in range(0, 7):
            for x in (x0, x1):
                put(b, (x, y, z), tex(PAINTS['soot'], x, y, z, 61))
    for x in range(x0 + 1, x1):
        for y in range(1, 7):
            put(b, (x, y, z1), tex(PAINTS['soot'], x, y, z1, 61))
        for z in range(zi0, zi1 + 1):
            put(b, (x, 0, z), tex([('minecraft:dark_oak_planks', 5),
                                   ('minecraft:spruce_planks', 2)], x, 0, z, 66))
            for y in range(1, 6):
                b[(x, y, z)] = ('minecraft:air', None, None)
            put(b, (x, 6, z), 'minecraft:dark_oak_planks')
    pitched_roof(b, x0, x1, zb, z1, 7, 'tile', ridge_axis='z', overhang=0)

    # the window display, behind the glass
    for x in (x0 + 3, x0 + 4):
        put(b, (x, 1, zi0), 'minecraft:dark_oak_planks')
    put(b, (x0 + 3, 2, zi0), 'hogwartsmagic:candle_cluster')
    put(b, (x0 + 4, 2, zi0), 'minecraft:skeleton_skull', {'rotation': '8'})

    # the counter, with a gate, across the shop
    cz = zi0 + 3
    for x in range(x0 + 1, x1):
        if x == x0 + 2:
            fence_gate(b, (x, 1, cz), 'dark_oak', 'north')
            continue
        put(b, (x, 1, cz), 'minecraft:polished_blackstone_bricks')
        slab(b, (x, 2, cz), 'minecraft:polished_blackstone_slab')
    put(b, (x0 + 4, 3, cz), 'hogwartsmagic:crystal_goblet')

    # The vanishing cabinet: a tall black wardrobe against the back wall. It is a real cupboard --
    # open the door and there is room inside to step in, which is rather the point of it.
    for z in (zi1 - 1, zi1):
        for y in (1, 2, 3):
            put(b, (x0 + 3, y, z), 'minecraft:dark_oak_planks')
            put(b, (x0 + 5, y, z), 'minecraft:dark_oak_planks')
        put(b, (x0 + 4, 3, z), 'minecraft:dark_oak_planks')
    door(b, x0 + 4, 1, zi1 - 1, 'minecraft:dark_oak_door', 'north')
    stair(b, (x0 + 4, 4, zi1 - 1), 'minecraft:dark_oak_stairs', 'south', 'top')

    # cases and curios along the walls
    for z in range(zi0 + 1, cz):
        b[(x1 - 1, 1, z)] = ('minecraft:tinted_glass', None, None)
    put(b, (x1 - 1, 2, zi0 + 1), 'minecraft:wither_skeleton_skull', {'rotation': '4'})
    put(b, (x1 - 1, 2, zi0 + 2), 'hogwartsmagic:candle_cluster')
    for z in range(cz + 1, zi1):
        put(b, (x0 + 1, 3, z), 'minecraft:chiseled_bookshelf', {'facing': 'east'})
        put(b, (x0 + 1, 2, z), 'minecraft:chiseled_bookshelf', {'facing': 'east'})
    for z in (zi0 + 1, cz + 2):
        put(b, (x0 + 1, 4, z), 'minecraft:cobweb')
    chain_lamp(b, x0 + 3, 5, cz - 1, drop=1, soul=True)
    chain_lamp(b, x0 + 3, 5, cz + 2, drop=1, soul=True)

    ents.append(villager(x0 + 3, 1, cz + 1, 'hogwartsmagic:curio_dealer', 'Mr Borgin'))
    loot_chest(b, (x1 - 1, 1, cz + 2), 'west', 'hogwartsmagic:chests/knockturn')


# =================================================================================================
# The square and Gringotts
# =================================================================================================

def square(b, ents):
    """A widening at the end of the alley, so Gringotts has somewhere to be seen from."""
    for x in range(X_ALLEY1 + 1, X_SQUARE1 + 1):
        c = curve(x)
        for z in range(ZC - 16 + c, ZC + 17 + c):
            d = abs(z - (ZC + c))
            put(b, (x, 0, z), tex(KERB if d > 9 else COBBLE, x, 0, z, 71))
            put(b, (x, -1, z), 'minecraft:stone')

    cx, cz = (X_ALLEY1 + X_SQUARE1) // 2, ZC + curve((X_ALLEY1 + X_SQUARE1) // 2)
    fountain(b, cx, cz)

    # A ring of flags round the fountain, so it stands on something rather than on road.
    for dx in range(-6, 7):
        for dz in range(-6, 7):
            r2 = dx * dx + dz * dz
            if 20 <= r2 <= 36 and (cx + dx, 1, cz + dz) not in b:
                put(b, (cx + dx, 0, cz + dz), tex(MARBLE_TRIM if r2 <= 26 else KERB,
                                                  cx + dx, 0, cz + dz, 74))

    # Benches facing the water, on the two sides people actually walk past.
    for sgn in (-1, 1):
        for dx in (-1, 0, 1):
            stair(b, (cx + dx, 1, cz + sgn * 6), 'minecraft:dark_oak_stairs',
                  'south' if sgn < 0 else 'north')
        for dx in (-2, 2):
            put(b, (cx + dx, 1, cz + sgn * 6), 'minecraft:dark_oak_trapdoor',
                {'facing': 'east' if dx < 0 else 'west', 'half': 'bottom', 'open': 'true',
                 'powered': 'false', 'waterlogged': 'false'})

    # Lamps at the corners: a cast-iron standard with three lanterns, the square's chandelier.
    for dx, dz in ((-6, -9), (6, -9), (-6, 9), (6, 9)):
        triple_lamp(b, cx + dx, cz + dz)

    # Market stalls along the shopfronts of the square.
    _stall(b, cx - 3, cz - 11, 'south', 'minecraft:yellow_wool', [
        'hogwartsmagic:candle_cluster', 'minecraft:barrel', 'hogwartsmagic:candle_cluster'])
    _stall(b, cx + 1, cz + 11, 'north', 'minecraft:purple_wool', [
        'hogwartsmagic:wizard_cauldron', 'hogwartsmagic:crystal_goblet', 'minecraft:brewing_stand'])

    square_shops(b, ents)


# The shops that close the square, so it is a room rather than a gap in front of a blank wall.
# name, x0, x1, storeys, paint, roof, ridge, frame, profession, keeper
SQUARE_NORTH = [
    ('Obscurus Books', 118, 124, (5, 4, 4, 4), 'teal', 'slate', 'z', FRAME_DARK,
     'hogwartsmagic:bookseller', 'Mr Obscurus'),
    ('The Daily Prophet', 125, 132, (5, 4, 4, 4, 4), 'cream', 'lead', 'x', FRAME_GREY,
     'hogwartsmagic:scribe', 'Mr Cuffe'),
]
SQUARE_SOUTH = [
    ('Amanuensis Quills', 118, 124, (5, 4, 4), 'plum', 'tile', 'x', FRAME_MID,
     'hogwartsmagic:scribe', 'Miss Quill'),
    ('Junk Shop', 125, 132, (5, 4, 4, 4), 'brown', 'shingle', 'z', FRAME_DARK,
     'hogwartsmagic:curio_dealer', 'Mr Hoarde'),
]


def square_shops(b, ents):
    for terrace, side in ((SQUARE_NORTH, 1), (SQUARE_SOUTH, -1)):
        for (name, x0, x1, storeys, paint, roof, ridge, frame, prof, keeper) in terrace:
            xm = (x0 + x1) // 2
            zf = ZC + curve(xm) - side * 15
            spec = ShopSpec(
                name, x0, x1, zf, side, depth=10, storeys=storeys, paint=paint, frame=frame,
                roof=roof, ridge=ridge, sign=name, jetty=[0, 1, 2, 2, 2][:len(storeys)],
                glass=GLASS_LIT, awning=(shash(name) % 2 == 0),
                chimneys=(0.25, 0.8) if len(storeys) >= 4 else (0.35,),
            )
            SPECS[name] = spec
            shopfront(b, spec)
            _shop_interior(b, ents, spec, prof, keeper)


def fountain(b, cx, cz):
    """A tiered fountain: a sunken stone basin, a fluted pedestal, an upper bowl and a bronze finial.

    The old one was a white quartz drum with a column in it -- a blob, from the street, the same
    white as Gringotts behind it so the two ran together. Grey stone against the bank's marble, and
    weathered copper on top, separates them and gives the square a centre of its own colour.

    Every source block is boxed on all four sides and from below, and nothing above the bowl is
    water, so the fountain cannot leak however the game updates it. (The first version flooded the
    square, round Gringotts and down the street.)
    """
    pool = {(dx, dz) for dx in range(-5, 6) for dz in range(-5, 6) if dx * dx + dz * dz <= 13}
    rim = set()
    for dx, dz in pool:
        for ax, az in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)):
            if (dx + ax, dz + az) not in pool:
                rim.add((dx + ax, dz + az))

    # sunk one course: the water sits at street level inside a knee-high kerb
    for dx, dz in pool:
        # Nothing below -1: that is the bottom course of the whole template, and DiagonAlleyManager
        # finds the street by expecting the paving one course above the template's floor.
        put(b, (cx + dx, -1, cz + dz), 'minecraft:prismarine_bricks')
        put(b, (cx + dx, 0, cz + dz), 'minecraft:water')
        put(b, (cx + dx, 1, cz + dz), 'minecraft:water')
    for dx, dz in rim:
        for y in (-1, 0):
            put(b, (cx + dx, y, cz + dz), 'minecraft:stone_bricks')
        put(b, (cx + dx, 1, cz + dz), tex([('minecraft:polished_andesite', 5),
                                           ('minecraft:stone_bricks', 3)], cx + dx, 1, cz + dz, 75))
        slab(b, (cx + dx, 2, cz + dz), 'minecraft:smooth_stone_slab')

    # the pedestal: a 3x3 plinth, then a single fluted shaft
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            put(b, (cx + dx, 0, cz + dz), 'minecraft:chiseled_stone_bricks')
            put(b, (cx + dx, 1, cz + dz), 'minecraft:stone_bricks')
    for d, (fx, fz) in (('north', (0, -1)), ('south', (0, 1)), ('east', (1, 0)), ('west', (-1, 0))):
        stair(b, (cx + fx, 2, cz + fz), 'minecraft:stone_brick_stairs', OPP[d])
    put(b, (cx, 2, cz), 'minecraft:stone_bricks')
    put(b, (cx, 3, cz), 'minecraft:polished_andesite')
    put(b, (cx, 4, cz), 'minecraft:chiseled_stone_bricks')

    # The upper bowl. Its floor is the 3x3 ring at 4, carried on upside-down stairs flaring out
    # from the shaft; the ring of water at 5 is held in by a stone lip on the 5x5 ring, whose
    # corners are left off so the bowl reads round rather than square.
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            ring = max(abs(dx), abs(dz))
            if ring == 1:
                put(b, (cx + dx, 4, cz + dz), 'minecraft:stone_bricks')
                put(b, (cx + dx, 5, cz + dz), 'minecraft:water')
            elif ring == 2 and not (abs(dx) == 2 and abs(dz) == 2):
                stair(b, (cx + dx, 4, cz + dz), 'minecraft:stone_brick_stairs',
                      _inward(dx, dz), 'top')
                # a low lip, so the water stands brim-full above it (slabs still hold it in)
                slab(b, (cx + dx, 5, cz + dz), 'minecraft:smooth_stone_slab')
    # the finial: a slender weathered-bronze post rising out of the bowl, a crystal at its tip
    put(b, (cx, 5, cz), 'minecraft:chiseled_stone_bricks')
    put(b, (cx, 6, cz), 'minecraft:oxidized_cut_copper')
    put(b, (cx, 7, cz), 'minecraft:oxidized_copper_bulb', {'lit': 'true', 'powered': 'false'})
    put(b, (cx, 8, cz), 'hogwartsmagic:arcane_cluster', {'facing': 'up', 'waterlogged': 'false'})


OPP = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}


def _inward(dx, dz):
    if abs(dx) > abs(dz):
        return 'west' if dx > 0 else 'east'
    return 'north' if dz > 0 else 'south'


def triple_lamp(b, x, z):
    """A cast-iron standard with a lantern on top and two more hung from arms either side."""
    for y in range(1, 6):
        put(b, (x, y, z), 'minecraft:polished_blackstone_wall')
    lantern(b, (x, 6, z), hanging=False)
    for dx in (-1, 1):
        put(b, (x + dx, 5, z), 'minecraft:iron_bars')
        lantern(b, (x + dx, 4, z), hanging=True)


def _stall(b, x, z, facing, canopy, goods):
    """A three-wide market stall: a counter of goods, corner poles and a striped canopy."""
    for i, good in enumerate(goods):
        put(b, (x + i, 1, z), 'minecraft:spruce_planks')
        if good == 'minecraft:barrel':
            barrel(b, (x + i, 2, z))
        elif good == 'minecraft:brewing_stand':
            put(b, (x + i, 2, z), good, {'has_bottle_0': 'true', 'has_bottle_1': 'false',
                                         'has_bottle_2': 'true'})
        elif good == 'hogwartsmagic:wizard_cauldron':
            put(b, (x + i, 2, z), good, {'level': '3'})
        else:
            put(b, (x + i, 2, z), good)
    back = -1 if facing == 'south' else 1
    for px in (x - 1, x + 3):
        for y in (1, 2, 3):
            put(b, (px, y, z), 'minecraft:spruce_fence')
    for i in range(-1, 4):
        for dz in (0, back):
            colour = canopy if (i % 2 == 0) else 'minecraft:white_wool'
            put(b, (x + i, 4, z + dz), colour)


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
# The first view, and what hangs over the street
# =================================================================================================

def entrance_arch(b):
    """A wrought-iron arch over the street just through the wall, with the name hanging from it.

    This is the frame for the first view of the alley -- the shot the films hold on as the bricks
    fold away. It stands in the one-block slot between the wall and the first two shops, the only
    place in the street with no jetty overhead, so it can rise to a point instead of being squashed
    under a floor.
    """
    x = X_ALLEY0 + 1
    zc = ZC + curve(x)
    cells = set()
    for y in range(1, 7):
        cells.add((-4, y))
        cells.add((4, y))
    cells |= {(-4, 7), (-3, 7), (3, 7), (4, 7), (-3, 8), (-2, 8), (2, 8), (3, 8),
              (-2, 9), (-1, 9), (0, 9), (1, 9), (2, 9)}
    for d, y in cells:
        if abs(d) == 4 and y <= 6:
            put(b, (x, y, zc + d), 'minecraft:polished_blackstone_wall')
        else:
            put(b, (x, y, zc + d), 'minecraft:iron_bars')
    # gilded finials on the posts and the crown
    for d in (-4, 4):
        put(b, (x, 8, zc + d), 'minecraft:gold_block')
    put(b, (x, 10, zc), 'minecraft:lightning_rod', {'facing': 'up', 'powered': 'false',
                                                   'waterlogged': 'false'})
    # the name, in gold, hanging from the crown and readable from both ends
    b[(x, 8, zc)] = ('minecraft:dark_oak_hanging_sign',
                     {'rotation': '4', 'attached': 'false', 'waterlogged': 'false'},
                     _ceiling_sign_nbt('minecraft:dark_oak_hanging_sign',
                                       ['', 'DIAGON', 'ALLEY', ''], 'yellow', True))
    # a lantern on each post, hung from the arch
    for d in (-3, 3):
        lantern(b, (x, 6, zc + d), hanging=True)


def overhead(b):
    """Strings of lanterns across the street between the upper storeys, and a banner on each shop.

    Both are things that only exist because the street is narrow. The facades come within five
    blocks of each other at the third storey, close enough to hang a chain across -- and a chain of
    lights receding down a curving street is most of what makes it read as a street at night rather
    than as two rows of buildings that happen to face each other.
    """
    strung = 0
    x = X_ALLEY0 + 9
    while x < X_ALLEY1 - 2:
        zc = ZC + curve(x)
        y = 13 if noise(x, 0, 0, 111) > 0.4 else 12
        north = south = None
        for dz in range(1, 8):
            if north is None and (x, y, zc - dz) in b:
                north = zc - dz
            if south is None and (x, y, zc + dz) in b:
                south = zc + dz
        if north is not None and south is not None and 3 <= south - north - 1 <= 9 \
                and all((x, y, z) not in b for z in range(north + 1, south)):
            for z in range(north + 1, south):
                b[(x, y, z)] = ('minecraft:chain', {'axis': 'z', 'waterlogged': 'false'}, None)
            # two lanterns, each on a short drop so it is held from above
            span = south - north - 1
            for z in {north + 1 + span // 3, south - 1 - span // 3}:
                b[(x, y - 1, z)] = ('minecraft:chain', {'axis': 'y', 'waterlogged': 'false'}, None)
                lantern(b, (x, y - 2, z), hanging=True)
            strung += 1
        x += 8 + int(noise(x, 1, 0, 112) * 6)

    # Banners: one per shop, hung under the jetty above the first floor, in the shop's own colour.
    colours = {'ochre': 'yellow', 'green': 'green', 'bottle': 'green', 'maroon': 'red',
               'teal': 'cyan', 'navy': 'blue', 'plum': 'purple', 'orange': 'orange',
               'purple': 'purple', 'cream': 'white', 'brown': 'brown', 'soot': 'black',
               'dust': 'gray', 'iron': 'light_gray'}
    emblems = ['rhombus', 'cross', 'circle', 'triangle_top', 'stripe_center', 'flower']
    for name, s in SPECS.items():
        if len(s.storeys) < 3:
            continue
        colour = colours.get(s.paint, 'red')
        trim = 'yellow' if colour not in ('yellow', 'orange') else 'black'
        emblem = emblems[shash(name) % len(emblems)]
        bx = s.x1 if door_x(s) == s.x0 + 1 else s.x0
        y = s.floor_y(1) + s.storeys[1] - 1
        z = s.front_z(1) + s.side
        if (bx, y, z) in b:
            continue
        wall_banner(b, (bx, y, z), colour, s.facing_out,
                    [('border', trim), (emblem, trim)])
    return strung


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


def backyards(b):
    """Ground under everything inside the boundary that is not street, shop or bank.

    The terraces are twelve deep but the boundary is wherever the furthest building reaches, and
    Knockturn runs twenty-six back -- so behind most shops there was a strip up to fifteen blocks
    wide with no floor at all. In the alley dimension that is a hole straight down into the void, a
    long way below a street that players fly brooms over. Now it is yards: trodden earth, gravel,
    a little grass, and the junk that collects behind shops.
    """
    xs = [p[0] for p in b]
    zs = [p[2] for p in b]
    x0, x1 = max(min(xs), X_ALLEY0), max(xs)
    z0, z1 = min(zs), max(zs)
    yard = [('minecraft:coarse_dirt', 6), ('minecraft:packed_mud', 3), ('minecraft:gravel', 3),
            ('minecraft:cobblestone', 2), ('minecraft:mossy_cobblestone', 1)]
    floored = []
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x, 0, z) in b:
                continue
            put(b, (x, 0, z), tex(yard, x, 0, z, 81))
            put(b, (x, -1, z), 'minecraft:stone')
            floored.append((x, z))

    # Clutter, only up against a wall, because junk is stacked against walls and a barrel standing
    # in the middle of a yard reads as scattered by a script.
    for x, z in floored:
        if (x, 1, z) in b:
            continue
        walls = sum(1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                    if (x + dx, 1, z + dz) in b and _blocking(b[(x + dx, 1, z + dz)][0]))
        n = noise(x, 1, z, 82)
        near_door = any(b.get((x + dx, y, z + dz), ('',))[0].endswith('_door')
                        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) for y in (1, 2, 3))
        if near_door:
            continue                    # Gringotts has side doors onto the yards; keep them clear
        if walls and n > 0.93:
            barrel(b, (x, 1, z))
            if n > 0.975:
                barrel(b, (x, 2, z))
        elif walls and n > 0.9:
            put(b, (x, 1, z), 'minecraft:stripped_spruce_log', {'axis': 'x'})
        elif n < 0.06 and b[(x, 0, z)][0] == 'minecraft:coarse_dirt':
            put(b, (x, 1, z), 'minecraft:short_grass')
        elif n < 0.075 and b[(x, 0, z)][0] == 'minecraft:coarse_dirt':
            put(b, (x, 1, z), 'minecraft:fern')
    return len(floored)


# The backs of the buildings beyond the boundary. Three families, each inside the 40-luma rule,
# so neighbouring segments differ in colour and not in noise.
BACKDROP = [
    PARTY,
    [('minecraft:mud_bricks', 6), ('minecraft:packed_mud', 2), ('minecraft:terracotta', 2)],
    [('minecraft:stone_bricks', 6), ('minecraft:andesite', 3), ('minecraft:cracked_stone_bricks', 2)],
    [('minecraft:brown_terracotta', 6), ('minecraft:spruce_planks', 2),
     ('minecraft:dark_oak_planks', 2)],
]


def perimeter(b):
    """The boundary: the backs of the city the alley is wedged into.

    Diagon Alley is a pocket, not a clearing -- the archway should be the only way in or out, and
    without a boundary the terraces enclose the middle of the street while the ends and corners are
    simply open onto void.

    It used to be a flat slab of hogwartsmagic:diagon_archway_brick, twenty-four high, all the way
    round. Two things wrong with that. It was 13,000 block entities -- every one of them ticking on
    both sides and on the renderer's always-draw list -- for a wall that never opens. And wherever it
    showed, round the square and down the flanks of Gringotts, it was an enormous blank brown
    rampart, which is not what stands behind a London street.

    So now it is the rear elevation of more buildings: segments of different heights and materials,
    each with its own rows of windows -- some lit, most dark -- stepped gables and chimney stacks
    against the sky. Windows are a pane in the boundary with a backing block one further out, lit
    (ochre froglight) or curtained, so they read as rooms without opening onto the void.

    Existing blocks are never overwritten, so where a terrace or Gringotts already reaches the
    boundary the building is the wall.
    """
    xs = [p[0] for p in b]
    zs = [p[2] for p in b]
    x0, x1 = min(xs), max(xs)
    z0, z1 = min(zs), max(zs)

    # (edge, positions along it as (x, z), outward step)
    edges = [
        ('n', [(x, z0) for x in range(X_ALLEY0, x1 + 1)], (0, -1)),
        ('s', [(x, z1) for x in range(X_ALLEY0, x1 + 1)], (0, 1)),
        ('e', [(x1, z) for z in range(z0 + 1, z1)], (1, 0)),
        # The entrance face: the back of the Leaky Cauldron, which is the view when you turn round
        # in the street. The player's own wall is only as wide as they built it while the alley is
        # fifty across, so without this face you could walk round the end of their wall.
        ('w', [(X_ALLEY0, z) for z in range(z0 + 1, z1)], (-1, 0)),
    ]
    entry_z = ZC + curve(X_ALLEY0)

    laid = 0
    for name, cells, (ox, oz) in edges:
        i = 0
        seg = 0
        while i < len(cells):
            length = 7 + int(noise(seg, 0, ord(name), 91) * 6)
            part = cells[i:i + length]
            if name == 'w':
                height, gable, pal = 17, False, PARTY
            else:
                height = 20 + int(noise(seg, 1, ord(name), 92) * 9)
                gable = noise(seg, 2, ord(name), 93) > 0.5
                pal = BACKDROP[int(noise(seg, 3, ord(name), 94) * len(BACKDROP))]
            n = len(part)
            for t, (x, z) in enumerate(part):
                edge_t = min(t, n - 1 - t)
                top = height + (min(edge_t, 4) if gable else 0)
                for y in range(0, top + 1):
                    pos = (x, y, z)
                    if pos in b:
                        continue
                    if name == 'w' and abs(z - entry_z) <= 3 and y <= 9:
                        continue            # the way through the arch, lined up with the street
                    if name == 'w' and abs(z - entry_z) <= 3 and y == 10:
                        b[pos] = (tex(PLINTH, x, y, z, 95), None, None)     # lintel over it
                        laid += 1
                        continue
                    window = (2 <= edge_t and (t - 2) % 3 == 0 and y >= 5 and y <= top - 3
                              and (y - 5) % 4 in (0, 1))
                    if name == 'w' and abs(z - entry_z) <= 5 and y <= 12:
                        window = False      # keep the masonry round the arch solid
                    if window:
                        lit = noise(x, (y - 5) // 4, z, 96) > 0.8
                        b[pos] = ('minecraft:orange_stained_glass_pane' if lit
                                  else 'minecraft:gray_stained_glass_pane', {}, None)
                        back = (x + ox, y, z + oz)
                        if back not in b:
                            b[back] = ('minecraft:ochre_froglight' if lit
                                       else 'minecraft:black_terracotta',
                                       {'axis': 'y'} if lit else None, None)
                    elif t == 0 or t == n - 1:
                        b[pos] = (tex(PLINTH, x, y, z, 97), None, None)     # a pier between
                    else:
                        b[pos] = (tex(pal, x, y, z, 98 + seg), None, None)
                    laid += 1
                # the skyline: stairs up a gable's slope, coping on a flat parapet
                cap = (x, top + 1, z)
                if cap not in b:
                    if gable and edge_t < 4 and 0 < t < n - 1:
                        rise = _toward(part, t)
                        stair(b, cap, 'minecraft:deepslate_tile_stairs', rise)
                    else:
                        slab(b, cap, 'minecraft:stone_brick_slab')
                    laid += 1
            # a chimney stack on most segments, never two the same height
            if n >= 5 and noise(seg, 4, ord(name), 99) > 0.3:
                t = 1 + int(noise(seg, 5, ord(name), 100) * (n - 2))
                x, z = part[t]
                edge_t = min(t, n - 1 - t)
                base = height + (min(edge_t, 4) if gable else 0) + 1     # replaces the cap
                stack = 3 + int(noise(seg, 6, ord(name), 101) * 4)
                for y in range(base, base + stack):
                    put(b, (x, y, z), tex(BRICK, x, y, z, 102))
                b[(x, base + stack, z)] = ('minecraft:brick_wall', {}, None)
            i += length
            seg += 1
    return laid


def _toward(part, t):
    """The facing of a gable stair at position t: up-slope, toward the middle of the segment."""
    (xa, za), (xb, zb) = part[0], part[-1]
    mid = (len(part) - 1) / 2.0
    ahead = t < mid
    if xa != xb:
        return 'east' if ahead else 'west'
    return 'south' if ahead else 'north'


def build():
    b, ents = {}, []
    pave(b)
    courtyard(b)
    build_shops(b, ents)
    character(b, ents)
    knockturn(b, ents)
    square(b, ents)
    gringotts(b, ents)
    street_furniture(b)
    entrance_arch(b)
    overhead(b)
    backyards(b)
    perimeter(b)
    connect_all(b)
    return b, ents


def audit(blocks, entities):
    """Cheap structural checks.  These do not tell you it looks right -- only the renderer does
    that -- but they catch the failures that are invisible in a picture."""
    problems = []

    def air(pos):
        cell = blocks.get(pos)
        return cell is None or cell[0] == 'minecraft:air'

    # Lanterns: a hanging one needs something above it, a standing one something below.
    floating = 0
    for (x, y, z), (name, props, _) in blocks.items():
        if name in ('minecraft:lantern', 'minecraft:soul_lantern'):
            hanging = (props or {}).get('hanging') == 'true'
            if air((x, y + 1, z) if hanging else (x, y - 1, z)):
                floating += 1
    if floating:
        problems.append('%d lanterns with nothing to hang from or stand on' % floating)

    # Water: every source must be boxed in on four sides and below, or the first block update
    # sends it across the street. (The first fountain flooded the square and half the alley.)
    leaks = []
    for (x, y, z), (name, _, _) in blocks.items():
        if name != 'minecraft:water':
            continue
        for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
            if air((x + d[0], y + d[1], z + d[2])):
                leaks.append((x, y, z))
                break
    if leaks:
        problems.append('%d water blocks that can leak, e.g. %s' % (len(leaks), leaks[:3]))

    # Doors: something walkable in front of and behind the lower half, or the door opens onto a
    # wall. Iron doors in Knockturn are meant to be blind, so they are skipped.
    blind = []
    step = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
    for (x, y, z), (name, props, _) in blocks.items():
        if not name.endswith('_door') or name == 'minecraft:iron_door':
            continue
        if x < X_WALL:
            continue                    # the pub's back door: the courtyard only exists for /spawnalley
        if (props or {}).get('half') != 'lower':
            continue
        dx, dz = step[props['facing']]
        for sgn in (1, -1):
            for h in (0, 1):
                cell = blocks.get((x + sgn * dx, y + h, z + sgn * dz))
                if cell is not None and _blocking(cell[0]):
                    blind.append((x, y, z))
    if blind:
        problems.append('%d door sides opening onto a solid block, e.g. %s'
                        % (len(blind), sorted(set(blind))[:4]))

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
    if shift[1] != 1:
        # The street's paving must land at template y=1, the anchoring code depends on it.
        print('  PROBLEM  template floor moved: y shift is %d, expected 1' % shift[1])
        raise SystemExit(1)
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
