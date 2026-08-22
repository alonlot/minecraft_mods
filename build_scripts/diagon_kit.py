"""The building kit Diagon Alley is assembled from.

Diagon Alley is a *street*, not a set of buildings, and a street only reads if every shop obeys the
same rules while breaking none of them the same way twice. So the shared grammar lives here and the
per-shop character lives in generate_diagon_alley.py.

THE RULES, and why each one is here
-----------------------------------

* **Storeys are 5 then 4.** A shopfront needs four blocks of headroom to hold a door, a window and a
  fascia; living floors above only need three. Equal storeys read as an office block.

* **Upper floors jetty out over the street, one block per floor, twice.** This is the single most
  important rule in the file. The film's street is a canyon: nine blocks wide at the cobbles and
  five at the eaves, with four storeys of building leaning in overhead. Take the jetty away and you
  have a village high street. Every jetty gets a course of upside-down stairs under it as corbels,
  because an overhang with nothing holding it up reads as a bug.

* **The frontage zigzags.** Each shop is a rectangular box, but consecutive shops sit at different
  Z, following `curve()`. That staggering is what makes the street bend and what makes the shopfronts
  jostle. Curving each individual wall would just look melted.

* **Timber frames, plaster infill.** The frame is corner posts, a beam at every floor line, and
  intermediate studs -- not a decorative grid stuck on afterwards. Bays between studs get the paint
  colour. Diagonal braces go in the end bays only; braces everywhere read as a pattern.

* **One palette per element, blended by position hash.** Never one flat material, never two blocks
  60 luma apart next to each other. `tex()` does the blending; the palettes below are all built to
  sit inside roughly a 40-luma spread.

* **Depth beats detail.** Plinths stand one block proud, jetties throw a shadow line, pilasters and
  recessed bays break the flat. A facade with no Z variation catches no light whatever you texture
  it with.

* **Light is warm and it comes from inside.** Shop windows mix clear panes with orange and yellow
  stained ones and have a lantern behind them, so the glass reads as lit even in daylight. Street
  lamps hang off brackets, never float.

Everything is deterministic -- `noise()` is a position hash, there is no RNG -- so the same source
always produces byte-identical .nbt and the diffs stay honest.
"""

import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from generate_biome_structures import (  # noqa: E402
    noise, tex, put, fill, fill_tex, stair, slab, entity, build_template, write_nbt,
)

__all__ = [
    'noise', 'tex', 'put', 'fill', 'fill_tex', 'stair', 'slab', 'entity',
    'build_template', 'write_nbt',
    'COBBLE', 'KERB', 'GUTTER', 'PLINTH', 'BRICK', 'PARTY', 'MARBLE', 'MARBLE_TRIM',
    'FRAME_DARK', 'FRAME_MID', 'FRAME_GREY', 'PAINTS', 'ROOFS',
    'door', 'trapdoor', 'hanging_sign', 'wall_sign', 'standing_sign',
    'villager', 'lantern', 'chain_lamp', 'connect_all', 'pitched_roof',
    'shopfront', 'ShopSpec', 'text_component', 'loot_chest', 'barrel',
    'flower_box', 'crate_pile', 'awning', 'bay_window',
]


# ---------------------------------------------------------------------------------------------
# Palettes.  Weights are relative; the first member of each list is meant to dominate.
# Luma figures in the comments are eyeballed averages of the 1.21 textures -- the point is only
# that no member of a list is more than ~40 off any other, which is the threshold above which a
# blend stops reading as texture and starts reading as a chessboard.
# ---------------------------------------------------------------------------------------------

# Street.  London cobbles: grey, damp, worn smooth in the middle and mossy at the edges.
COBBLE = [('minecraft:cobblestone', 10), ('minecraft:andesite', 5),
          ('minecraft:stone', 4), ('minecraft:mossy_cobblestone', 2),
          ('minecraft:gravel', 1)]
# Pavement flags either side of the roadway -- slightly lighter and much more regular than the road.
KERB = [('minecraft:stone_bricks', 8), ('minecraft:polished_andesite', 5),
        ('minecraft:cracked_stone_bricks', 2), ('minecraft:mossy_stone_bricks', 1)]
GUTTER = [('minecraft:polished_andesite', 6), ('minecraft:andesite', 3),
          ('minecraft:mossy_cobblestone', 2)]

# The stone footing every shop stands on.  Grounding a building on a plinth is what stops it
# looking like a box dropped onto the pavement.
PLINTH = [('minecraft:cobblestone', 6), ('minecraft:stone_bricks', 5),
          ('minecraft:mossy_cobblestone', 3), ('minecraft:andesite', 2)]

# The courtyard wall behind the Leaky Cauldron, and chimney stacks.
BRICK = [('minecraft:bricks', 9), ('minecraft:granite', 2), ('minecraft:terracotta', 2)]

# Party walls -- the walls between one shop and the next.  These are never painted: in a real
# terrace they are the bare brick flank that only becomes visible where a taller neighbour rises
# above a shorter one, and every shop in this street is a different height, so that happens
# fourteen times.  Painting them the shop's own colour produced four- and eight-block slabs of flat
# green and flat orange standing over the roofline, which was the most conspicuous fault in the
# first render of the street.
PARTY = [('minecraft:bricks', 6), ('minecraft:terracotta', 4),
         ('minecraft:granite', 3), ('minecraft:brown_terracotta', 2)]

# Gringotts, and nothing else in the alley.  It has to read as a different order of building.
MARBLE = [('minecraft:quartz_block', 9), ('minecraft:smooth_quartz', 5),
          ('minecraft:chiseled_quartz_block', 2), ('minecraft:calcite', 3)]
MARBLE_TRIM = [('minecraft:smooth_quartz', 7), ('minecraft:quartz_bricks', 4),
               ('minecraft:chiseled_quartz_block', 2)]

# Timber.  Three weights of frame so neighbouring shops do not share a silhouette.
FRAME_DARK = [('minecraft:dark_oak_log', 8), ('minecraft:stripped_dark_oak_log', 3),
              ('minecraft:dark_oak_wood', 2)]
FRAME_MID = [('minecraft:spruce_log', 8), ('minecraft:stripped_dark_oak_log', 3),
             ('minecraft:dark_oak_log', 2)]
FRAME_GREY = [('minecraft:stripped_dark_oak_log', 6), ('minecraft:spruce_log', 4),
              ('minecraft:dark_oak_log', 3)]

# Paint.  Terracotta throughout: it is the only family muted enough to read as painted render rather
# than as plastic, and every member sits in the same saturation band so shops differ in hue without
# one of them screaming.  `plaster` is the near-white default for the timber-framed bays.
#
# Every one of these is checked by build_scripts/audit_palettes.py, which opens the real textures
# and measures the actual luma spread among the members that carry real weight.  The first draft of
# this table failed on eleven of sixteen -- `dust` mixed brown_terracotta with white_terracotta, 128
# luma apart, and Ollivanders came out looking like a checkerboard from across the street.  Where a
# light accent is wanted it goes in at weight 1, which makes it a speckle rather than a pattern.
PAINTS = {
    'plaster':  [('minecraft:white_terracotta', 9), ('minecraft:smooth_sandstone', 4),
                 ('minecraft:sandstone', 2), ('minecraft:bone_block', 1)],
    # Genuinely ochre.  An earlier pass made plain terracotta the dominant member to satisfy the
    # luma audit, and plain terracotta is a pink-brown that is very nearly the colour of brick --
    # so Eeylops came out looking like the party walls either side of it.  Yellow and orange
    # terracotta are 40 luma apart but the same hue, which the audit now allows for.
    'ochre':    [('minecraft:yellow_terracotta', 8), ('minecraft:orange_terracotta', 3),
                 ('minecraft:terracotta', 1)],
    'green':    [('minecraft:green_terracotta', 9), ('minecraft:dark_oak_planks', 2),
                 ('minecraft:moss_block', 1)],
    'bottle':   [('minecraft:green_terracotta', 7), ('minecraft:gray_terracotta', 3),
                 ('minecraft:black_terracotta', 1)],
    'maroon':   [('minecraft:red_terracotta', 9), ('minecraft:brown_terracotta', 3),
                 ('minecraft:nether_bricks', 1)],
    'teal':     [('minecraft:cyan_terracotta', 8), ('minecraft:blue_terracotta', 3),
                 ('minecraft:gray_terracotta', 1)],
    'navy':     [('minecraft:blue_terracotta', 8), ('minecraft:gray_terracotta', 3),
                 ('minecraft:cyan_terracotta', 1)],
    'plum':     [('minecraft:purple_terracotta', 8), ('minecraft:magenta_terracotta', 2),
                 ('minecraft:brown_terracotta', 1)],
    'orange':   [('minecraft:orange_terracotta', 9), ('minecraft:terracotta', 2),
                 ('minecraft:yellow_terracotta', 1)],
    'purple':   [('minecraft:purple_terracotta', 9), ('minecraft:magenta_terracotta', 3)],
    'cream':    [('minecraft:smooth_sandstone', 8), ('minecraft:white_terracotta', 4),
                 ('minecraft:sandstone', 2)],
    'brown':    [('minecraft:brown_terracotta', 9), ('minecraft:dark_oak_planks', 3),
                 ('minecraft:spruce_planks', 1)],
    'soot':     [('minecraft:black_terracotta', 8), ('minecraft:gray_terracotta', 3),
                 ('minecraft:blackstone', 2)],
    # Ollivanders.  Faded, grimy, and the darkest painted shop in the street on purpose -- it is
    # the one building the player is meant to walk into rather than look at.
    'dust':     [('minecraft:brown_terracotta', 6), ('minecraft:gray_terracotta', 4),
                 ('minecraft:dark_oak_planks', 3), ('minecraft:spruce_planks', 1)],
    'iron':     [('minecraft:gray_terracotta', 7), ('minecraft:blackstone', 2),
                 ('minecraft:black_terracotta', 2)],
}

# Roofs.  Steep, dark and slightly varied; the skyline is most of the silhouette so it gets three
# distinct materials shared out along the street.
ROOFS = {
    'slate':  ('minecraft:deepslate_tile_stairs', 'minecraft:deepslate_tiles',
               'minecraft:deepslate_tile_slab'),
    'lead':   ('minecraft:polished_deepslate_stairs', 'minecraft:polished_deepslate',
               'minecraft:polished_deepslate_slab'),
    'shingle': ('minecraft:dark_oak_stairs', 'minecraft:dark_oak_planks',
                'minecraft:dark_oak_slab'),
    'tile':   ('minecraft:blackstone_stairs', 'minecraft:blackstone',
               'minecraft:blackstone_slab'),
    'copper': ('minecraft:oxidized_cut_copper_stairs', 'minecraft:oxidized_cut_copper',
               'minecraft:oxidized_cut_copper_slab'),
}

# Glazing is the one place the 40-luma rule is deliberately broken, and audit_palettes.py exempts
# these three by name.  The rule exists to stop a large flat surface reading as a chessboard; a
# window is the opposite of a large flat surface -- it is a few panes inside a timber frame, divided
# by mullions, and panes that differ from each other are exactly what makes a window read as glass
# with a room behind it instead of a blue rectangle.  The extremes still came out too harsh in the
# first render, so the darkest and lightest members are gone.
GLASS_LIT = [('minecraft:glass_pane', 6), ('minecraft:orange_stained_glass_pane', 3),
             ('minecraft:yellow_stained_glass_pane', 2)]
GLASS_PLAIN = [('minecraft:glass_pane', 8), ('minecraft:light_gray_stained_glass_pane', 2)]
GLASS_DARK = [('minecraft:gray_stained_glass_pane', 7), ('minecraft:black_stained_glass_pane', 3)]


# ---------------------------------------------------------------------------------------------
# Small block helpers
# ---------------------------------------------------------------------------------------------

OPPOSITE = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}
LEFT_OF = {'north': 'west', 'west': 'south', 'south': 'east', 'east': 'north'}
STEP = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}
# Direction.toYRot(): the rotation a sign uses, in sixteenths.
ROT16 = {'south': 0, 'west': 4, 'north': 8, 'east': 12}


def text_component(s):
    """A 1.21 text component, JSON-encoded, as sign lines and CustomName both want."""
    return '{"text":"%s"}' % s.replace('\\', '\\\\').replace('"', '\\"')


def door(b, x, y, z, name, facing, hinge='left', open_=False):
    """Both halves.  A door written as one block is invisible in game and a magenta cube in the
    renderer, and it is the single easiest thing to get wrong in a template."""
    common = {'facing': facing, 'hinge': hinge,
              'open': 'true' if open_ else 'false', 'powered': 'false'}
    b[(x, y, z)] = (name, dict(common, half='lower'), None)
    b[(x, y + 1, z)] = (name, dict(common, half='upper'), None)


def trapdoor(b, pos, name, facing, half='top', open_=False):
    b[pos] = (name, {'facing': facing, 'half': half,
                     'open': 'true' if open_ else 'false',
                     'powered': 'false', 'waterlogged': 'false'}, None)


def _sign_nbt(block_id, lines, colour='black', glowing=False):
    msgs = [text_component(l) for l in (list(lines) + ['', '', '', ''])[:4]]
    face = ('compound', {
        'messages': ('list', 'string', [('string', m) for m in msgs]),
        'color': ('string', colour),
        'has_glowing_text': ('byte', 1 if glowing else 0),
    })
    return {
        'id': block_id,
        'front_text': face,
        'back_text': face,
        'is_waxed': ('byte', 1),          # waxed so the text cannot be scrubbed off by a right-click
    }


def hanging_sign(b, pos, wood, facing, lines, colour='black', glowing=False):
    """A shop sign on a bracket off the wall -- the Diagon Alley silhouette.

    `facing` is the direction a reader stands in, i.e. the way the board looks.
    """
    name = 'minecraft:%s_wall_hanging_sign' % wood
    b[pos] = (name, {'facing': facing, 'waterlogged': 'false'},
              _sign_nbt(name, lines, colour, glowing))


def wall_sign(b, pos, wood, facing, lines, colour='black', glowing=False):
    name = 'minecraft:%s_wall_sign' % wood
    b[pos] = (name, {'facing': facing, 'waterlogged': 'false'},
              _sign_nbt(name, lines, colour, glowing))


def standing_sign(b, pos, wood, facing, lines, colour='black', glowing=False):
    name = 'minecraft:%s_sign' % wood
    b[pos] = (name, {'rotation': str(ROT16[facing]), 'waterlogged': 'false'},
              _sign_nbt(name, lines, colour, glowing))


def lantern(b, pos, hanging=False, soul=False):
    b[pos] = ('minecraft:soul_lantern' if soul else 'minecraft:lantern',
              {'hanging': 'true' if hanging else 'false', 'waterlogged': 'false'}, None)


def chain_lamp(b, x, y, z, drop=1, soul=False):
    """A lantern on a chain.  A lantern floating with nothing above it is the classic tell."""
    for i in range(drop):
        b[(x, y - i, z)] = ('minecraft:chain', {'axis': 'y', 'waterlogged': 'false'}, None)
    lantern(b, (x, y - drop, z), hanging=True, soul=soul)


def villager(x, y, z, profession, name, level=5, biome='plains'):
    """A shopkeeper baked into the template, already at his counter and already at max level.

    PersistenceRequired because a villager that despawns takes the shop with it, and NoAI left off
    so they still turn to face a customer.
    """
    return entity(x + 0.5, y, z + 0.5, 'minecraft:villager', {
        'VillagerData': ('compound', {
            'profession': ('string', profession),
            'type': ('string', 'minecraft:' + biome),
            'level': ('int', level),
        }),
        'CustomName': ('string', text_component(name)),
        'CustomNameVisible': ('byte', 1),
        'PersistenceRequired': ('byte', 1),
        'Invulnerable': ('byte', 1),
    })


def loot_chest(b, pos, facing, loot, chest_type='single'):
    b[pos] = ('minecraft:chest', {'facing': facing, 'type': chest_type, 'waterlogged': 'false'},
              {'id': 'minecraft:chest', 'LootTable': loot})


def barrel(b, pos, facing='up'):
    b[pos] = ('minecraft:barrel', {'facing': facing, 'open': 'false'}, None)


# ---------------------------------------------------------------------------------------------
# Connection pass
# ---------------------------------------------------------------------------------------------
#
# Panes, bars, fences and walls store their connections in their blockstate.  A template writes the
# state literally, so a pane written with no connections renders -- and, until something updates it,
# places -- as a lone post floating in the middle of a window.  Rather than track neighbours at
# every call site, the whole build gets one pass at the end.

# Blocks that are NOT full cubes, so a pane must not connect to them.  Everything not listed is
# treated as solid, which is safe because this build's palette is known.
_NOT_SOLID_EXACT = {
    'minecraft:air', 'minecraft:water', 'minecraft:lava', 'minecraft:chain',
    'minecraft:lantern', 'minecraft:soul_lantern', 'minecraft:torch', 'minecraft:ladder',
    'minecraft:campfire', 'minecraft:soul_campfire', 'minecraft:cauldron', 'minecraft:lever',
    'minecraft:flower_pot', 'minecraft:end_rod', 'minecraft:candle', 'minecraft:bell',
    'minecraft:iron_bars', 'minecraft:scaffolding', 'minecraft:composter', 'minecraft:lectern',
    'minecraft:bookshelf',           # solid, but listed nowhere else; kept solid below
}
_NOT_SOLID_EXACT.discard('minecraft:bookshelf')

_NOT_SOLID_SUFFIX = (
    '_pane', '_fence', '_fence_gate', '_wall', '_sign', '_hanging_sign', '_banner', '_door',
    '_trapdoor', '_carpet', '_button', '_pressure_plate', '_rail', '_slab', '_stairs',
    '_torch', '_candle', '_sapling', '_pot', '_head', '_skull', '_bed', '_rod',
)


def _solid(entry):
    if entry is None:
        return False
    name = entry[0]
    if name in _NOT_SOLID_EXACT:
        return False
    if any(name.endswith(s) for s in _NOT_SOLID_SUFFIX):
        return False
    return True


def _is_pane(name):
    return name.endswith('_pane') or name == 'minecraft:iron_bars'


def _is_fence(name):
    return name.endswith('_fence')


def _is_wallblock(name):
    return (name.endswith('_wall')
            and not name.endswith('_hanging_sign')
            and 'sign' not in name and 'banner' not in name and 'torch' not in name)


def _is_gate(name):
    return name.endswith('_fence_gate')


def connect_all(b):
    """Fix up pane / bar / fence / wall connection states from actual neighbours."""
    for (x, y, z), (name, props, be) in list(b.items()):
        pane, fence, wallb = _is_pane(name), _is_fence(name), _is_wallblock(name)
        if not (pane or fence or wallb):
            continue
        props = dict(props or {})
        sides = {}
        for d, (dx, dz) in STEP.items():
            nb = b.get((x + dx, y, z + dz))
            if nb is None:
                sides[d] = False
                continue
            nn = nb[0]
            if pane:
                sides[d] = _solid(nb) or _is_pane(nn) or _is_gate(nn) or _is_wallblock(nn)
            elif fence:
                sides[d] = _solid(nb) or _is_fence(nn) or _is_gate(nn)
            else:
                sides[d] = _solid(nb) or _is_wallblock(nn) or _is_gate(nn)
        if wallb:
            above = b.get((x, y + 1, z))
            # A wall post is needed when the wall is not a clean straight run, or carries something.
            ns, ew = sides['north'] and sides['south'], sides['east'] and sides['west']
            straight = (ns and not sides['east'] and not sides['west']) or \
                       (ew and not sides['north'] and not sides['south'])
            props['up'] = 'false' if (straight and not _solid(above)) else 'true'
            for d in STEP:
                props[d] = 'low' if sides[d] else 'none'
            props['waterlogged'] = props.get('waterlogged', 'false')
        else:
            for d in STEP:
                props[d] = 'true' if sides[d] else 'false'
            props['waterlogged'] = props.get('waterlogged', 'false')
        b[(x, y, z)] = (name, props, be)


# ---------------------------------------------------------------------------------------------
# Roofs
# ---------------------------------------------------------------------------------------------

def pitched_roof(b, x0, x1, z0, z1, y_eave, kind, ridge_axis='x', overhang=1, hip=False):
    """A steep pitched roof, one block of rise per block of run.

    `ridge_axis='x'` puts the ridge along the street and slopes the roof toward front and back;
    `'z'` turns the gable end to the street.  Alternating the two along a terrace is most of what
    breaks up the skyline.

    The outer surface is stairs and there is a solid course beneath them, because a stair-only roof
    is see-through from below at every step -- which no render shows and every player notices.
    """
    stairs, solid, slab_block = ROOFS[kind]
    ox0, ox1 = x0 - overhang, x1 + overhang
    oz0, oz1 = z0 - overhang, z1 + overhang

    if ridge_axis == 'x':
        span = oz1 - oz0
        steps = (span + 1) // 2
        for i in range(steps):
            y = y_eave + i
            zl, zr = oz0 + i, oz1 - i
            xa, xb = (ox0 + i, ox1 - i) if hip else (ox0, ox1)
            if xa > xb:
                break
            for x in range(xa, xb + 1):
                stair(b, (x, y, zl), stairs, 'south')
                stair(b, (x, y, zr), stairs, 'north')
                fill(b, x, y - 1, zl, x, y - 1, zl, solid)
                fill(b, x, y - 1, zr, x, y - 1, zr, solid)
            if hip:
                for z in range(zl, zr + 1):
                    stair(b, (xa, y, z), stairs, 'east')
                    stair(b, (xb, y, z), stairs, 'west')
        y = y_eave + steps
        zl, zr = oz0 + steps, oz1 - steps
        xa, xb = (ox0 + steps, ox1 - steps) if hip else (ox0, ox1)
        for x in range(xa, xb + 1):
            for z in range(min(zl, zr), max(zl, zr) + 1):
                slab(b, (x, y, z), slab_block)
    else:
        span = ox1 - ox0
        steps = (span + 1) // 2
        for i in range(steps):
            y = y_eave + i
            xl, xr = ox0 + i, ox1 - i
            za, zb = (oz0 + i, oz1 - i) if hip else (oz0, oz1)
            if za > zb:
                break
            for z in range(za, zb + 1):
                stair(b, (xl, y, z), stairs, 'east')
                stair(b, (xr, y, z), stairs, 'west')
                fill(b, xl, y - 1, z, xl, y - 1, z, solid)
                fill(b, xr, y - 1, z, xr, y - 1, z, solid)
            if hip:
                for x in range(xl, xr + 1):
                    stair(b, (x, y, za), stairs, 'south')
                    stair(b, (x, y, zb), stairs, 'north')
        y = y_eave + steps
        xl, xr = ox0 + steps, ox1 - steps
        za, zb = (oz0 + steps, oz1 - steps) if hip else (oz0, oz1)
        for z in range(za, zb + 1):
            for x in range(min(xl, xr), max(xl, xr) + 1):
                slab(b, (x, y, z), slab_block)
    return y_eave + steps


def chimney(b, x, z, y0, y1, salt=0):
    """A brick stack with a wall-block pot on top.  Chimneys are half the reason a roofline reads
    as a city rather than a row of sheds, so they run tall and they are never all the same height."""
    for y in range(y0, y1):
        put(b, (x, y, z), tex(BRICK, x, y, z, salt))
    b[(x, y1, z)] = ('minecraft:brick_wall', {}, None)


# ---------------------------------------------------------------------------------------------
# The shopfront engine
# ---------------------------------------------------------------------------------------------

class ShopSpec:
    """One shop.  `side` is +1 for the north terrace (front face looks toward +Z) and -1 for the
    south terrace.  `zf` is the Z of the ground-floor front wall."""

    def __init__(self, name, x0, x1, zf, side, depth=12, storeys=(5, 4, 4, 4),
                 paint='plaster', frame=FRAME_DARK, roof='slate', ridge='x',
                 sign=None, sign_colour='black', sign_glow=False, sign_wood='dark_oak',
                 jetty=(0, 1, 2, 2), glass=None, door_wood='dark_oak',
                 chimneys=(0.3,), hip=False, awning=False, bay=True, salt=0):
        self.name = name
        self.x0, self.x1, self.zf, self.side = x0, x1, zf, side
        self.depth, self.storeys = depth, list(storeys)
        self.paint, self.frame, self.roof, self.ridge = paint, frame, roof, ridge
        self.sign = sign if sign is not None else name
        self.sign_colour, self.sign_glow, self.sign_wood = sign_colour, sign_glow, sign_wood
        self.jetty = list(jetty)[:len(storeys)]
        self.glass = glass or GLASS_LIT
        self.door_wood = door_wood
        self.chimneys, self.hip, self.awning, self.bay = chimneys, hip, awning, bay
        self.salt = salt or (abs(hash(name)) % 9973)

    # -- derived geometry ----------------------------------------------------------------------
    @property
    def width(self):
        return self.x1 - self.x0 + 1

    def floor_y(self, k):
        return sum(self.storeys[:k])

    @property
    def eaves(self):
        return sum(self.storeys)

    def front_z(self, k):
        return self.zf + self.side * self.jetty[k]

    @property
    def back_z(self):
        return self.zf - self.side * (self.depth - 1)

    def z_span(self, k):
        f, bk = self.front_z(k), self.back_z
        return (min(f, bk), max(f, bk))

    @property
    def facing_out(self):
        return 'south' if self.side > 0 else 'north'

    @property
    def facing_in(self):
        return 'north' if self.side > 0 else 'south'


def shopfront(b, s):
    """Build one shop from its spec.  Returns the door position, for dressing the pavement."""
    pal = PAINTS[s.paint]
    salt = s.salt
    side, zf = s.side, s.zf
    x0, x1 = s.x0, s.x1

    # --- plinth: one course of stone standing one block proud of the facade -------------------
    pz0, pz1 = s.z_span(0)
    fz0 = s.front_z(0)
    fill_tex(b, x0, 0, pz0, x1, 0, pz1, PLINTH, salt)
    for x in range(x0, x1 + 1):
        put(b, (x, 0, fz0 + side), tex(PLINTH, x, 0, fz0 + side, salt))

    # --- shell, storey by storey --------------------------------------------------------------
    for k, h in enumerate(s.storeys):
        y0 = s.floor_y(k) + 1
        y1 = s.floor_y(k) + h
        z0, z1 = s.z_span(k)
        fz = s.front_z(k)
        bz = s.back_z

        # solid box then hollow it: the party walls, back wall and floor all come free
        fill_tex(b, x0, y0, z0, x1, y1, z1, pal, salt + k)
        fill(b, x0 + 1, y0, min(fz, bz) + 1, x1 - 1, y1 - 1, max(fz, bz) - 1, 'minecraft:air')
        # floor of this storey (= ceiling of the one below)
        fill(b, x0 + 1, y0 - 1, min(fz, bz) + 1, x1 - 1, y0 - 1, max(fz, bz) - 1,
             'minecraft:spruce_planks' if k else 'minecraft:dark_oak_planks')

        # timber frame: corner posts, floor beam, intermediate studs
        for y in range(y0, y1 + 1):
            put(b, (x0, y, fz), tex(s.frame, x0, y, fz, salt))
            put(b, (x1, y, fz), tex(s.frame, x1, y, fz, salt))
        for x in range(x0, x1 + 1):
            b[(x, y1, fz)] = (tex(s.frame, x, y1, fz, salt + 1), {'axis': 'x'}, None)
        studs = [x for x in range(x0 + 3, x1, 3)]
        for sx in studs:
            for y in range(y0, y1 + 1):
                put(b, (sx, y, fz), tex(s.frame, sx, y, fz, salt))
        # diagonal braces in the end bays only
        if k > 0 and s.width >= 7:
            for i in range(min(2, h - 2)):
                b[(x0 + 1 + i, y0 + i, fz)] = (tex(s.frame, x0 + 1 + i, y0 + i, fz, salt + 2),
                                               {'axis': 'y'}, None)
                b[(x1 - 1 - i, y0 + i, fz)] = (tex(s.frame, x1 - 1 - i, y0 + i, fz, salt + 2),
                                               {'axis': 'y'}, None)

        # corbels under a jetty
        if k > 0 and s.jetty[k] > s.jetty[k - 1]:
            prev = s.front_z(k - 1)
            for x in range(x0, x1 + 1):
                stair(b, (x, y0 - 1, fz), 'minecraft:dark_oak_stairs', s.facing_in, 'top')
            for x in range(x0, x1 + 1):
                for z in range(min(prev, fz), max(prev, fz) + 1):
                    if (x, y0 - 1, z) not in b:
                        put(b, (x, y0 - 1, z), 'minecraft:dark_oak_planks')

        # --- openings --------------------------------------------------------------------------
        if k == 0:
            _ground_floor(b, s, y0, y1, fz)
        else:
            _upper_windows(b, s, k, y0, y1, fz, studs)

    # --- party walls ----------------------------------------------------------------------------
    # Repaint both flanks in bare brick.  Normally invisible between two shops of equal height, and
    # the whole point wherever the neighbour is shorter.  The front plane is skipped so the timber
    # corner posts survive.
    for k, h in enumerate(s.storeys):
        y0, y1 = s.floor_y(k) + 1, s.floor_y(k) + h
        z0, z1 = s.z_span(k)
        fz = s.front_z(k)
        # The front two courses stay in the shop's own paint with their timber corner post: brick
        # running right down to the shopfront turned every junction between two shops into a wide
        # masonry band and made the street read as brick rather than as painted timber.  Brick
        # belongs on the part of the flank that is only ever seen above a shorter neighbour's roof.
        for flank in (x0, x1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    if abs(z - fz) <= 1:
                        continue
                    put(b, (flank, y, z), tex(PARTY, flank, y, z, salt + 9))

    # --- roof ---------------------------------------------------------------------------------
    rz0, rz1 = s.z_span(len(s.storeys) - 1)
    ridge_y = pitched_roof(b, x0, x1, rz0, rz1, s.eaves + 1, s.roof,
                           ridge_axis=s.ridge, overhang=1, hip=s.hip)

    # Chimneys stand ON the party walls, which is where a terrace actually puts them, and the stack
    # continues the brick flank straight up instead of erupting out of the middle of a slope.
    for i, frac in enumerate(s.chimneys):
        cx = x0 if frac < 0.5 else x1
        cz = int(rz0 + (0.3 + 0.4 * i) * (rz1 - rz0))
        chimney(b, cx, cz, 1, ridge_y + 2 + ((salt + i * 3) % 3), salt)

    return (int((x0 + x1) / 2), 1, s.front_z(0))


def _ground_floor(b, s, y0, y1, fz):
    """Shopfront: a door with a fanlight, a big display window, a stall board and a hanging sign."""
    pal = PAINTS[s.paint]
    salt = s.salt
    side = s.side
    x0, x1 = s.x0, s.x1
    dx = x0 + 1 if (salt % 2) else x1 - 1          # door at one end, not the middle
    out = s.facing_out

    # display window: the whole frontage except the door bay and the corner posts
    wx0, wx1 = (x0 + 3, x1 - 1) if dx == x0 + 1 else (x0 + 1, x1 - 3)
    for x in range(wx0, wx1 + 1):
        for y in (y0 + 1, y0 + 2):
            b[(x, y, fz)] = (tex(s.glass, x, y, fz, salt), {}, None)
        # stall riser below the window, and a fascia board above
        put(b, (x, y0, fz), tex(PLINTH, x, y0, fz, salt))
        b[(x, y0 + 3, fz)] = (tex(s.frame, x, y0 + 3, fz, salt + 4), {'axis': 'x'}, None)
    # mullions every third pane so the glazing reads as small panes, not a shop window
    for x in range(wx0 + 2, wx1, 3):
        for y in (y0 + 1, y0 + 2):
            put(b, (x, y, fz), tex(s.frame, x, y, fz, salt + 5))

    # a lantern immediately behind the glass: this is what makes the window read as lit
    inz = fz - side
    for x in range(wx0, wx1 + 1, 3):
        lantern(b, (x, y0 + 2, inz), hanging=False)
        put(b, (x, y0 + 1, inz), 'minecraft:dark_oak_slab')

    # door with a fanlight over it
    door(b, dx, y0, fz, 'minecraft:%s_door' % s.door_wood, s.facing_out,
         hinge='left' if dx == x0 + 1 else 'right')
    b[(dx, y0 + 2, fz)] = ('minecraft:brown_stained_glass_pane', {}, None)
    b[(dx, y0 + 3, fz)] = (tex(s.frame, dx, y0 + 3, fz, salt + 4), {'axis': 'x'}, None)
    # a step up to the door
    slab(b, (dx, 0, fz + side), 'minecraft:stone_brick_slab')

    # bay window: one bay pushed a block out into the street, for depth
    if s.bay and s.width >= 9:
        bx = int((wx0 + wx1) / 2)
        oz = fz + side
        for x in (bx - 1, bx, bx + 1):
            put(b, (x, y0, oz), tex(PLINTH, x, y0, oz, salt))
            for y in (y0 + 1, y0 + 2):
                b[(x, y, oz)] = (tex(s.glass, x, y, oz, salt), {}, None)
            slab(b, (x, y0 + 3, oz), 'minecraft:dark_oak_slab')
        for x in (bx - 1, bx + 1):
            for y in (y0 + 1, y0 + 2):
                put(b, (x, y, oz), tex(s.frame, x, y, oz, salt + 5))

    # awning over the stall board
    if s.awning:
        for x in range(wx0, wx1 + 1):
            stair(b, (x, y0 + 3, fz + side), 'minecraft:dark_oak_stairs', s.facing_in, 'bottom')

    # the sign, hung off a bracket at fascia height
    sx = x0 + 1 if dx != x0 + 1 else x1 - 1
    hanging_sign(b, (sx, y1 - 1, fz + side), s.sign_wood, s.facing_out,
                 _wrap_sign(s.sign), s.sign_colour, s.sign_glow)
    # street lamp on the other side of the door
    chain_lamp(b, dx + (1 if dx == x0 + 1 else -1), y1 - 1, fz + side, drop=1)


def _wrap_sign(text):
    """Shop names are long and a sign is four lines of fifteen-ish characters."""
    words, lines, cur = text.split(), [], ''
    for w in words:
        if len(cur) + len(w) + 1 > 15 and cur:
            lines.append(cur)
            cur = w
        else:
            cur = (cur + ' ' + w).strip()
    if cur:
        lines.append(cur)
    return lines[:4]


def _upper_windows(b, s, k, y0, y1, fz, studs):
    """One window per bay, set between the studs, with a sill and a lantern behind."""
    salt = s.salt
    side = s.side
    edges = [s.x0] + studs + [s.x1]
    for i in range(len(edges) - 1):
        a, c = edges[i] + 1, edges[i + 1] - 1
        if c < a:
            continue
        for x in range(a, c + 1):
            for y in (y0 + 1, y0 + 2):
                if y <= y1 - 1:
                    b[(x, y, fz)] = (tex(s.glass, x, y, fz, salt + k), {}, None)
            slab(b, (x, y0, fz), 'minecraft:dark_oak_slab', 'top')     # sill
        # a lantern one block in, so the panes glow
        if (salt + i + k) % 3 == 0:
            lantern(b, (a, y0 + 2, fz - side), hanging=False)
        # a flower box under an occasional window
        if (salt + i * 7 + k) % 5 == 0:
            flower_box(b, a, c, y0, fz + side, salt + i)


def flower_box(b, xa, xc, y, z, salt):
    for x in range(xa, xc + 1):
        b[(x, y, z)] = ('minecraft:spruce_trapdoor',
                        {'facing': 'north', 'half': 'bottom', 'open': 'false',
                         'powered': 'false', 'waterlogged': 'false'}, None)
        if noise(x, y, z, salt) > 0.4:
            b[(x, y + 1, z)] = (tex([('minecraft:red_tulip', 3), ('minecraft:orange_tulip', 2),
                                     ('minecraft:oxeye_daisy', 2), ('minecraft:cornflower', 2)],
                                    x, y, z, salt), {}, None)


def awning(b, x0, x1, y, z, facing, colour='red'):
    for x in range(x0, x1 + 1):
        stair(b, (x, y, z), 'minecraft:dark_oak_stairs', facing, 'bottom')


def bay_window(b, x0, x1, y0, y1, z, glass, frame, salt):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            b[(x, y, z)] = (tex(glass, x, y, z, salt), {}, None)


def crate_pile(b, x, y, z, salt):
    """Barrels, crates and a sack or two.  Clutter is what stops a pavement reading as a diagram."""
    n = int(noise(x, y, z, salt) * 3) + 1
    for i in range(n):
        ox = i % 2
        oy = i // 2
        pos = (x + ox, y + oy, z)
        if pos in b:
            continue
        roll = noise(x + ox, y + oy, z, salt + 3)
        if roll < 0.4:
            barrel(b, pos)
        elif roll < 0.7:
            put(b, pos, 'minecraft:spruce_planks')
        else:
            put(b, pos, 'minecraft:hay_block')
