"""The classrooms (Defence Against the Dark Arts, Transfiguration, Charms), the gatehouse's front door opened, and the
stair from the gatehouse down into the Library.

    from mini_hogwarts import rooms_classrooms
    anchors = rooms_classrooms.place_classrooms(grid, layers)   # castle frame, after the library and the towers;
                                                                # layers: rooms_library.cave_plan(old castle)[0]

THE GATEHOUSE AS IT WAS
-----------------------
The gatehouse is one block, x 27..49 by z 185..189 inside, between the viaduct (north) and the apse tower that holds
Potions (south). Its gate (x 37..39, z 185) was a sham: a pair of solid oak-log and plank leaves, and behind it a
vestibule three wide that dead-ended at the south wall with a stone bench. Either side of the vestibule, behind walls
with only one-block windows in them, a room 8 x 5 and 4 tall: the west one with a slab spiral round a cobblestone
newel up to the upper floor (and colonnades of bars and wall posts), the east one a little classroom the old castle
labelled "Defense Against the Dark Arts" (bookshelves, skulls, four chairs). The upper floor (feet 32) is three rooms:
the west one where the spiral arrives, the middle one with a spruce door into the apse labelled "Transfiguration" on
its side, and the east one labelled "Mirror of Erised". Nothing of it could be walked into from the main entrance.

WHAT IT BECOMES (feet: ground floor y 26, upper floor y 32, Defence y 25)
------------------------------------------------------------------------
    the gate         its two leaves stand open (dark-oak doors folded back against the jambs, R18); the vestibule is
                     the entrance hall, with doorways west (the stair hall), east (Charms) and south (Defence) in
                     place of its windows and its bench, and a red runner
    the stair hall   the old west room keeps its slab spiral up to the upper floor and gains the stair down into the
                     Library (R31): a slot in the floor along the north wall, then dark-oak treads cut into the
                     Library's north shelving wall, down to its north gallery (feet 18); a lantern over each end
    Charms           the old east room: a raised tiered floor (three tiers of birch desks rising east, a step up the
                     aisle at each tier), the teacher's desk by the door, the SPELL_STUDY chest, and feathers floating
                     over the front of the class (end rods hung in the air at odd angles; they are the room's light)
    Transfiguration  the room behind the labelled door, run on into the old Mirror of Erised room (the partition
                     between them comes down, its lintel kept as a beam), 12 x 5: a blackboard on the west wall, the
                     teacher's desk before it, twelve spruce desks facing it, cages at the back (a cabinet in the
                     corner and birdcages hung from the higher ceiling), and the Mirror of Erised (the old room's white
                     panes, R41) set up in the east-wall bay on a gilded plinth between dark-oak posts, candles either
                     side; its door from the upper landing moves to the north corner so the board has the wall to
                     itself; the labelled door still opens into Potions
    Defence          carved into the hill's hollow under the apse (where the library's rock fill was), the way the
                     Library was: a hall 9 x 10 and 6 tall under the Potions floor, entered from the vestibule down a
                     step, stone-brick walls with dark-oak pilasters, a dark-oak ceiling with beams; twelve desks
                     either side of a deepslate aisle, the teacher's desk on a dais, a wardrobe and skull shelves, soul
                     lanterns, and a dragon skeleton of bone blocks hung from the ceiling on chains over the aisle

Potions (the apse, x 32..45, z 191..204, floor y 32) is never touched; with the labelled door now walked to, it is
part of the walk too. Every interior here is a sealed room (build_castle.ROOMS): the gate is open now, so the flood
that used to seal the gatehouse as an enclosed pocket no longer reaches it.
"""

from collections import deque

from . import checks, hall
from .grid import air_at
from .hall import G
from .rooms_library import _box_cells

# ---------------------------------------------------------------- levels (castle frame)
GROUND_FEET = 26                       # the gatehouse ground floor (floor blocks y 25)
UPPER_FEET = 32                        # the upper floor (floor blocks y 31)
DEF_FLOOR_Y = 24                       # Defence's floor block layer, on the library's ceiling (y 23)
DEF_FEET = DEF_FLOOR_Y + 1             # 25
DEF_CEIL_Y = 31                        # its ceiling layer, under the Potions floor (y 32)

# ---------------------------------------------------------------- the gatehouse
GATE_X = (37, 39)
GATE_Z = 185
GATE_CELL = (38, GROUND_FEET, GATE_Z)
VESTIBULE_X = (37, 39)
VESTIBULE_Z = (186, 189)
VESTIBULE_CELL = (38, GROUND_FEET, 187)
SIDE_DOOR_Z = (187, 188)               # the vestibule's old windows, opened into doorways
SOUTH_WALL_Z = 190                     # the gatehouse's south wall; Defence is behind it
LABELLED_DOOR = (38, UPPER_FEET + 1, SOUTH_WALL_Z)   # the spruce door labelled "Transfiguration" (its lower half)
POTIONS_CELL = (38, UPPER_FEET + 1, 193)             # a floor cell of Potions, walked to through that door
POTIONS_BOX = (32, UPPER_FEET, 191, 45, 70, 204)     # the apse room above its floor, walls and roof included
GATEHOUSE_BOX = (27, 24, 184, 49, 45, 190)           # the gatehouse, walls, attic and sub-floor void included

# the library stair: treads along the north wall row z 185, from the stair hall's floor (x 30) down to the Library's
# north gallery (x 37, feet 18, on the shelving's stripped-oak band at y 17)
STAIR_Z = 185
STAIR_TOP_X = 30
STAIR_TREADS = [(STAIR_TOP_X + i, GROUND_FEET - 2 - i) for i in range(7)]   # (x, tread block y): (30, 24)..(36, 18)
LIBRARY_STAIR_FOOT = (37, 18, STAIR_Z)
LIBRARY_STAIR_TOP = (STAIR_TOP_X, GROUND_FEET - 1, STAIR_Z)

# ---------------------------------------------------------------- Defence, under the apse
DEF_X = (34, 42)                       # the interior
DEF_Z = (192, 201)
DEF_AXIS = 38
DEF_DOOR_X = (37, 39)                  # the doorway through the gatehouse's south wall (z 190) and the hall's (z 191)
DEF_WALL_Z = 191
DEF_BACK_Z = 202
DEFENCE_SHELL_BOX = (DEF_X[0] - 1, DEF_FLOOR_Y, DEF_WALL_Z, DEF_X[1] + 1, DEF_CEIL_Y, DEF_BACK_Z)
PILASTERS_Z = (194, 197, 200)          # dark-oak pilasters on the side walls and ceiling beams across, at these z
DAIS_X = (36, 40)
DAIS_Z = (200, 201)

# ---------------------------------------------------------------- rooms (inclusive boxes) for build_castle.ROOMS
ROOMS_BY_NAME = {
    'gatehouse vestibule': (VESTIBULE_X[0], GROUND_FEET, GATE_Z, VESTIBULE_X[1], GROUND_FEET + 3, VESTIBULE_Z[1]),
    'gatehouse vestibule west door': (36, GROUND_FEET, SIDE_DOOR_Z[0], 36, GROUND_FEET + 1, SIDE_DOOR_Z[1]),
    'gatehouse vestibule east door': (40, GROUND_FEET, SIDE_DOOR_Z[0], 40, GROUND_FEET + 1, SIDE_DOOR_Z[1]),
    'gatehouse stair hall': (28, GROUND_FEET, 185, 35, GROUND_FEET + 4, 189),
    'gatehouse stair hall spiral': (31, UPPER_FEET - 1, 186, 33, UPPER_FEET - 1, 188),
    'gatehouse sub-floor': (28, GROUND_FEET - 2, 185, 48, GROUND_FEET - 2, 189),
    'library stair': (STAIR_TOP_X, LIBRARY_STAIR_FOOT[1], STAIR_Z, LIBRARY_STAIR_FOOT[0], GROUND_FEET - 1, STAIR_Z),
    'charms': (41, GROUND_FEET, 185, 48, GROUND_FEET + 3, 189),
    'gatehouse upper landing': (29, UPPER_FEET, 185, 34, UPPER_FEET + 4, 189),   # up into its vault's recess (y 36)
    'transfiguration door': (35, UPPER_FEET, 185, 35, UPPER_FEET + 1, 185),
    'transfiguration': (36, UPPER_FEET, 185, 41, UPPER_FEET + 3, 189),
    'transfiguration east': (42, UPPER_FEET, 185, 47, UPPER_FEET + 5, 189),
    'defence doorway (gatehouse wall)': (DEF_DOOR_X[0], GROUND_FEET, SOUTH_WALL_Z, DEF_DOOR_X[1], GROUND_FEET + 2,
                                         SOUTH_WALL_Z),
    'defence doorway (hill)': (DEF_DOOR_X[0], GROUND_FEET, DEF_WALL_Z, DEF_DOOR_X[1], GROUND_FEET + 2, DEF_WALL_Z),
    'defence': (DEF_X[0], DEF_FEET, DEF_Z[0], DEF_X[1], DEF_CEIL_Y - 1, DEF_Z[1]),
}

# ---------------------------------------------------------------- the classrooms
# Each: its boxes, the student desk (a top slab) and chair (bottom stairs, its back away from the desk) and the
# teacher's desk cells with their block.
CLASSROOMS = {
    'defence': {
        'boxes': [ROOMS_BY_NAME['defence']],
        'desk': 'dark_oak_slab', 'chair': 'dark_oak_stairs',
        'teacher': [(x, DEF_FEET + 1, DAIS_Z[0]) for x in (37, 38, 39)], 'teacher_block': 'dark_oak_planks',
    },
    'transfiguration': {
        'boxes': [ROOMS_BY_NAME['transfiguration'], ROOMS_BY_NAME['transfiguration east']],
        'desk': 'spruce_slab', 'chair': 'spruce_stairs',
        'teacher': [(38, UPPER_FEET, 186), (38, UPPER_FEET, 187)], 'teacher_block': 'spruce_planks',
    },
    'charms': {
        'boxes': [ROOMS_BY_NAME['charms']],
        'desk': 'birch_slab', 'chair': 'birch_stairs',
        'teacher': [(42, GROUND_FEET, 188), (42, GROUND_FEET, 189)], 'teacher_block': 'birch_planks',
    },
}

# One walkable cell in each part, for the reachability check.
CLASSROOM_CELLS = {
    'vestibule': VESTIBULE_CELL,
    'stair hall': (30, GROUND_FEET, 187),
    'library stair top': LIBRARY_STAIR_TOP,
    'library stair foot': LIBRARY_STAIR_FOOT,
    'charms': (41, GROUND_FEET, 187),
    'charms top tier': (48, GROUND_FEET + 2, 187),
    'upper landing': (31, UPPER_FEET, 185),
    'transfiguration': (39, UPPER_FEET, 187),
    'transfiguration back': (46, UPPER_FEET, 187),
    'defence': (DEF_AXIS, DEF_FEET, 197),
    'defence dais': (37, DEF_FEET + 1, DAIS_Z[1]),
}

SPELL_STUDY_CHEST = {'at': [41, GROUND_FEET, 185], 'facing': 'east', 'theme': 'SPELL_STUDY'}
STUDENT_SPOTS = (
    (37, DEF_FEET, 193), (34, DEF_FEET, 196), (42, DEF_FEET, 198), (39, DEF_FEET, 199),        # Defence
    (39, UPPER_FEET, 185), (41, UPPER_FEET, 187), (43, UPPER_FEET, 187), (46, UPPER_FEET, 188),  # Transfiguration
    (41, GROUND_FEET, 186), (43, GROUND_FEET, 187), (45, GROUND_FEET + 1, 187),                # Charms
)

STEP = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}


def _stairs(name, facing, half='bottom'):
    return G.state(name, facing=facing, half=half, shape='straight', waterlogged=False)


def _slab(name, kind='top'):
    return G.state(name, type=kind, waterlogged=False)


def _lantern(hanging, soul=False):
    return G.state('soul_lantern' if soul else 'lantern', hanging=hanging, waterlogged=False)


def _candles(n=3, colour=None):
    return G.state(('%s_candle' % colour) if colour else 'candle', candles=n, lit=True, waterlogged=False)


CHAIN = G.state('chain', axis='y', waterlogged=False)


def room_boxes():
    """{name: box} every interior this task builds or opens (for build_castle.ROOMS)."""
    return dict(ROOMS_BY_NAME)


def light_boxes():
    """The boxes the no-dark-floor check covers: every interior here."""
    return list(ROOMS_BY_NAME.values())


def hill_cells():
    """Every cell these rooms keep in the hill's hollow (the library stair and Defence with its doorway): the library's
    rock-fill check leaves them to the rooms."""
    out = set()
    for name in ('library stair', 'defence', 'defence doorway (hill)'):
        out |= _box_cells(*ROOMS_BY_NAME[name])
    return out


def changed_boxes():
    """(name, inclusive box) for build_castle.CHANGED_ON_PURPOSE: every old-castle block this task changes. Defence and
    the library stair below the gatehouse floor stand in the hill's hollow (cells that were air), so they need none."""
    return [
        ('classrooms: the gatehouse gate, its leaves opened (R18)', (GATE_X[0], GROUND_FEET, GATE_Z, GATE_X[1],
                                                                       GROUND_FEET + 1, GATE_Z)),
        ("classrooms: the vestibule's bench and its two side windows opened into doorways",
         (36, GROUND_FEET, SIDE_DOOR_Z[0], 40, GROUND_FEET + 1, VESTIBULE_Z[1])),
        ("classrooms: the doorway from the vestibule through the gatehouse's south wall into Defence",
         ROOMS_BY_NAME['defence doorway (gatehouse wall)']),
        ("classrooms: the library stair's slot in the stair hall's floor (R31)",
         (STAIR_TOP_X, GROUND_FEET - 1, STAIR_Z, STAIR_TOP_X + 2, GROUND_FEET - 1, STAIR_Z)),
        ("classrooms: Charms, the gatehouse ground floor's east room (and the sea lantern's fence in its ceiling)",
         (41, GROUND_FEET, 185, 48, GROUND_FEET + 4, 189)),
        ("classrooms: Transfiguration, the labelled room and the Mirror of Erised room run together (floor, partition, "
         "furniture; the Mirror of Erised's panes moved to the east-wall bay, R41)",
         (36, UPPER_FEET - 1, 185, 47, UPPER_FEET + 3, 189)),
        ("classrooms: Transfiguration's door from the upper landing moved to the north corner (a bar out of the landing's "
         "colonnade, the old opening walled up)", (34, UPPER_FEET, 185, 35, UPPER_FEET + 2, 187)),
    ]


# ---------------------------------------------------------------- audits (build_castle.verify_classrooms)

def _in(p, box):
    return box[0] <= p[0] <= box[3] and box[1] <= p[1] <= box[4] and box[2] <= p[2] <= box[5]


def student_desks(cells, name):
    """[(desk, chair)] the student desks of a classroom: its desk block (a top slab) with its chair (bottom stairs)
    beside it, the chair's back away from the desk so the sitter faces it. One chair per desk."""
    room = CLASSROOMS[name]
    desk_id, chair_id = room['desk'], room['chair']
    out = []
    for b in room['boxes']:
        for p in sorted(_box_cells(*b)):
            s = cells.get(p)
            if s is None or checks.block_id(s) != desk_id or 'type=top' not in s:
                continue
            for facing, (dx, dz) in STEP.items():
                c = (p[0] + dx, p[1], p[2] + dz)
                cs = cells.get(c, '')
                if checks.block_id(cs) == chair_id and 'half=bottom' in cs and 'facing=%s' % facing in cs:
                    out.append((p, c))
                    break
    return out


def dragon_report(cells):
    """Defence's dragon skeleton: {'bones': bone blocks in the room, 'low': those below head height plus one (a player
    walks under every bone), 'loose': bones in no group hung from the ceiling, 'chains': chains that hang a group from
    a solid ceiling block}."""
    box = ROOMS_BY_NAME['defence']
    bones = {p for p, s in cells.items() if _in(p, box) and checks.block_id(s) == 'bone_block'}
    chains = {p for p, s in cells.items() if _in(p, box) and checks.block_id(s) == 'chain'}
    low = sorted(p for p in bones if p[1] < DEF_FEET + 3)
    hung, loose, anchors = set(), [], 0
    left = set(bones | chains)
    while left:
        seed = left.pop()
        comp, queue = {seed}, deque([seed])
        while queue:
            x, y, z = queue.popleft()
            for n in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
                if n in left:
                    left.remove(n)
                    comp.add(n)
                    queue.append(n)
        tops = [c for c in comp if c in chains and checks.supports(cells.get((c[0], c[1] + 1, c[2]), 'minecraft:air'))]
        if tops:
            hung |= comp
            anchors += len(tops)
        else:
            loose += sorted(c for c in comp if c in bones)
    return {'bones': len(bones), 'low': low, 'loose': loose, 'chains': anchors}


def transfiguration_props(cells):
    """(blackboard cells, cages) in Transfiguration: black concrete with a solid wall block behind it (west), and iron
    bars cells (each a cage's mesh)."""
    boxes = CLASSROOMS['transfiguration']['boxes']
    board = cages = 0
    for p, s in cells.items():
        if not any(_in(p, b) for b in boxes):
            continue
        name = checks.block_id(s)
        if name == 'black_concrete' and checks.supports(cells.get((p[0] - 1, p[1], p[2]), 'minecraft:air')):
            board += 1
        elif name == 'iron_bars':
            cages += 1
    return board, cages


def floating_feathers(cells):
    """End rods in Charms with nothing they could rest on round them (every face neighbour air or decoration)."""
    box = ROOMS_BY_NAME['charms']
    n = 0
    for (x, y, z), s in cells.items():
        if _in((x, y, z), box) and checks.block_id(s) == 'end_rod':
            around = [cells.get(q, 'minecraft:air') for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z),
                                                                (x, y - 1, z), (x, y, z + 1), (x, y, z - 1))]
            if not any(checks.supports(t) for t in around):
                n += 1
    return n


# ---------------------------------------------------------------- the gate and the vestibule

def open_gate(grid):
    """The gate's two leaves open: dark-oak doors folded back against the jambs (x 37 and 39) and the middle clear, under
    the old plank-and-log transom (y 28 and up, unchanged)."""
    z = GATE_Z
    for y, half in ((GROUND_FEET, 'lower'), (GROUND_FEET + 1, 'upper')):
        grid.set(GATE_X[0], y, z, G.state('dark_oak_door', facing='south', half=half, hinge='right', open=True,
                                          powered=False))
        grid.clear(GATE_X[0] + 1, y, z)
        grid.set(GATE_X[1], y, z, G.state('dark_oak_door', facing='south', half=half, hinge='left', open=True,
                                          powered=False))


def build_vestibule(grid):
    """The vestibule as the entrance hall: its bench gone, its two side windows opened into doorways (the stair-arch
    heads at y 28 kept), a doorway three wide under a pointed head through the south wall into Defence, and a red
    runner from the gate."""
    for x in range(VESTIBULE_X[0], VESTIBULE_X[1] + 1):
        grid.clear(x, GROUND_FEET, VESTIBULE_Z[1])
    for x in (36, 40):
        for z in SIDE_DOOR_Z:
            for y in (GROUND_FEET, GROUND_FEET + 1):
                grid.clear(x, y, z)
    for z in range(VESTIBULE_Z[0], VESTIBULE_Z[1] + 1):
        grid.set(38, GROUND_FEET, z, 'minecraft:red_carpet')
    _doorway(grid, SOUTH_WALL_Z)


def _doorway(grid, z):
    """A doorway three wide and three tall (x 37..39, y 26..28) with a pointed head: the corners of its top row are
    upside-down stone-brick stairs."""
    for x in range(DEF_DOOR_X[0], DEF_DOOR_X[1] + 1):
        for y in (GROUND_FEET, GROUND_FEET + 1):
            grid.clear(x, y, z)
    grid.clear(DEF_AXIS, GROUND_FEET + 2, z)
    grid.set(DEF_DOOR_X[0], GROUND_FEET + 2, z, _stairs('stone_brick_stairs', 'west', 'top'))
    grid.set(DEF_DOOR_X[1], GROUND_FEET + 2, z, _stairs('stone_brick_stairs', 'east', 'top'))


# ---------------------------------------------------------------- the stair hall and the library stair

def build_library_stair(grid):
    """The stair down into the Library (R31). A slot in the stair hall's floor along its north wall (x 30..32), then a
    tread a block lower every cell eastward along the row z 185: through the void under the gatehouse floor, the
    Library's ceiling and the top of its north shelving wall (the shelves stay under every tread), to the north gallery
    at x 37 (feet 18, on the shelving's band). Each tread keeps three cells clear above it. A lantern hangs over the
    slot from the upper floor's slabs and one over the lower flight from the Library's ceiling."""
    z = STAIR_Z
    for x, y in STAIR_TREADS:
        grid.set(x, y, z, _stairs('dark_oak_stairs', 'west'))
        for yy in range(y + 1, y + 4):
            if yy <= GROUND_FEET - 1:                         # the floor and below; the room above is already air
                grid.clear(x, yy, z)
    fx, fy, _ = LIBRARY_STAIR_FOOT
    for yy in range(fy, fy + 3):
        grid.clear(fx, yy, z)
    grid.set(36, fy + 4, z, _lantern(True))                   # (36, 22, 185), under the Library's ceiling
    grid.set(31, GROUND_FEET + 4, z, _lantern(True))          # (31, 30, 185), under the upper floor's slabs


# ---------------------------------------------------------------- Charms

CH_X0 = 41                                                    # the teacher's side, by the door
CH_TIERS = ((43, 0), (45, 1), (47, 2))                        # (desk x, tier height); the chair is east of the desk
CH_ROWS = (185, 186, 188, 189)                                # the aisle is z 187
# the feathers: a cell over a head (y 28) over the front of the class, a checkerboard on the room's middle three rows
# so none touches another, a wall, the ceiling's beams (y 30) or a desk (the room is 5 deep, so that is five)
FEATHERS = (
    (42, 28, 186, 'up'), (42, 28, 188, 'east'), (43, 28, 187, 'north'), (44, 28, 186, 'west'), (44, 28, 188, 'south'),
)


def build_charms(grid):
    """Charms in the old east room: everything inside cleared to its beamed ceiling (y 30; the sea lantern's fence
    there becomes a slab like its neighbours), a raised tiered floor of birch rising east, three tiers of four desks
    with their chairs (students face west, to the teacher), the aisle a step up at each tier, the teacher's desk and
    chair by the door with candles, and feathers floating over the front (end rods at odd angles, the room's light)."""
    for x in range(CH_X0, 49):
        for y in range(GROUND_FEET, GROUND_FEET + 4):
            for z in range(185, 190):
                grid.clear(x, y, z)
    grid.set(44, GROUND_FEET + 4, 187, _slab('oak_slab'))
    planks = 'minecraft:birch_planks'
    for desk_x, tier in CH_TIERS:
        for x in (desk_x, desk_x + 1):
            for z in range(185, 190):
                for y in range(GROUND_FEET, GROUND_FEET + tier):
                    grid.set(x, y, z, planks)
        if tier:
            grid.set(desk_x, GROUND_FEET + tier - 1, 187, _stairs('birch_stairs', 'east'))
        for z in CH_ROWS:
            grid.set(desk_x, GROUND_FEET + tier, z, _slab('birch_slab'))
            grid.set(desk_x + 1, GROUND_FEET + tier, z, _stairs('birch_stairs', 'east'))
    for x, y, z in CLASSROOMS['charms']['teacher']:
        grid.set(x, y, z, planks)
    grid.set(42, GROUND_FEET + 1, 189, _candles(3))
    grid.set(CH_X0, GROUND_FEET, 189, _stairs('birch_stairs', 'west'))
    grid.set(45, GROUND_FEET + 2, 185, _candles(2, 'white'))
    grid.set(47, GROUND_FEET + 3, 189, _candles(1, 'white'))
    for x, y, z, facing in FEATHERS:
        grid.set(x, y, z, G.state('end_rod', facing=facing))


# ---------------------------------------------------------------- Transfiguration

TR_X = (36, 47)
TR_DESKS_X = (40, 42, 44)                                     # the chair is east of each (students face west)
TR_ROWS = (185, 186, 188, 189)
TR_STEPS = [(x, UPPER_FEET, 189) for x in (37, 38, 39)]       # the old steps up to the labelled door, kept
TR_FLOOR_CAGES = ((47, 185),)
TR_HANGING_CAGES = ((44, 186), (44, 188), (42, 188), (42, 186))
# the Mirror of Erised (ruling R41): the old castle's 2 x 2 of white glass panes (old room, x 45), set back up in the
# east-wall bay in front of the dark window, on a gilded plinth between stripped dark-oak posts, candles either side
MIRROR_X = 47
MIRROR_Z = (187, 188)
MIRROR_CELLS = [(MIRROR_X, y, z) for y in (UPPER_FEET + 1, UPPER_FEET + 2) for z in MIRROR_Z]
MIRROR_POSTS_Z = (186, 189)


def build_transfiguration(grid):
    """Transfiguration: the labelled room and the old Mirror of Erised room as one, 12 x 5. The partition between them
    comes down to its lintel (y 35, kept as a beam), the old furniture goes (shelves, steps but the ones up to the
    labelled door, the counter, the posts, the mirror, the torches) and the floor is oak planks throughout. The door
    from the upper landing moves to the north corner (one bar out of the landing's colonnade, the old opening walled
    up) so the blackboard has the west wall to itself; the teacher's desk stands before it, twelve desks face it, and
    at the back are the cages (a cabinet in the north-east corner, birdcages hung on chains from the higher ceiling)
    and the Mirror of Erised (build_mirror)."""
    grid.clear(34, UPPER_FEET, 185)
    for y in (UPPER_FEET, UPPER_FEET + 1):
        grid.clear(35, y, 185)
        grid.set(35, y, 187, 'minecraft:cobblestone')
    grid.set(35, UPPER_FEET + 2, 187, 'minecraft:cobblestone')
    keep = set(TR_STEPS)
    for x in range(TR_X[0], TR_X[1] + 1):
        for z in range(185, 190):
            for y in range(UPPER_FEET, UPPER_FEET + 3):
                if (x, y, z) not in keep:
                    grid.clear(x, y, z)
            if x >= 41:
                grid.set(x, UPPER_FEET - 1, z, 'minecraft:oak_planks')
            if 42 <= x <= 46:
                grid.clear(x, UPPER_FEET + 3, z)
    # the blackboard on the west wall, over the floor (a chalk ledge of candles is too loud; the board stays plain)
    for z in (186, 187, 188):
        for y in (UPPER_FEET + 1, UPPER_FEET + 2):
            grid.set(TR_X[0], y, z, 'minecraft:black_concrete')
    for x, y, z in CLASSROOMS['transfiguration']['teacher']:
        grid.set(x, y, z, 'minecraft:spruce_planks')
    grid.set(37, UPPER_FEET, 186, _stairs('spruce_stairs', 'west'))
    grid.set(38, UPPER_FEET + 1, 187, _candles(3))
    grid.set(38, UPPER_FEET + 1, 186, 'minecraft:potted_red_tulip')
    for dx in TR_DESKS_X:
        for z in TR_ROWS:
            grid.set(dx, UPPER_FEET, z, _slab('spruce_slab'))
            grid.set(dx + 1, UPPER_FEET, z, _stairs('spruce_stairs', 'east'))
    grid.set(40, UPPER_FEET + 1, 185, _candles(1))
    grid.set(44, UPPER_FEET + 1, 189, _candles(1))
    bars = G.state('iron_bars', east=True, north=True, south=True, west=True, waterlogged=False)
    for x, z in TR_FLOOR_CAGES:
        grid.set(x, UPPER_FEET, z, 'minecraft:spruce_planks')
        grid.set(x, UPPER_FEET + 1, z, bars)
        grid.set(x, UPPER_FEET + 2, z, _slab('spruce_slab', 'bottom'))
    for x, z in TR_HANGING_CAGES:
        grid.set(x, UPPER_FEET + 4, z, CHAIN)
        grid.set(x, UPPER_FEET + 3, z, bars)
        grid.set(x, UPPER_FEET + 2, z, G.state('spruce_trapdoor', facing='north', half='top', open=False,
                                               powered=False, waterlogged=False))
    build_mirror(grid)
    grid.set(39, UPPER_FEET + 3, 187, _lantern(True))         # from the middle room's ceiling, between its beams
    grid.set(43, UPPER_FEET + 3, 187, _lantern(True))         # from the old cobblestone beam over the back rows' aisle


def build_mirror(grid):
    """The Mirror of Erised at the back of Transfiguration (R41): its four white panes (joined to each other and to the
    posts, so it reads as one flat glass) on a plinth of gilded blackstone, framed by stripped dark-oak posts to the old
    window arch above (y 35, kept), with a candle stand (a dark-oak fence post and four candles) either side in front."""
    for z in MIRROR_Z:
        grid.set(MIRROR_X, UPPER_FEET, z, 'minecraft:gilded_blackstone')
    for x, y, z in MIRROR_CELLS:
        grid.set(x, y, z, G.state('white_stained_glass_pane', east=False, north=True, south=True, west=False,
                                  waterlogged=False))
    for z in MIRROR_POSTS_Z:
        for y in range(UPPER_FEET, UPPER_FEET + 3):
            grid.set(MIRROR_X, y, z, G.state('stripped_dark_oak_log', axis='y'))
        grid.set(MIRROR_X - 1, UPPER_FEET, z, G.state('dark_oak_fence', east=False, north=False, south=False,
                                                      west=False, waterlogged=False))
        grid.set(MIRROR_X - 1, UPPER_FEET + 1, z, _candles(4))


def mirror_ok(cells):
    """True while the Mirror of Erised stands: its four white panes in place, inside Transfiguration."""
    boxes = CLASSROOMS['transfiguration']['boxes']
    return all(checks.block_id(cells.get(p, '')) == 'white_stained_glass_pane' and any(_in(p, b) for b in boxes)
               for p in MIRROR_CELLS)


# ---------------------------------------------------------------- Defence Against the Dark Arts

def _defence_interior(p):
    x, y, z = p
    if DEF_X[0] <= x <= DEF_X[1] and DEF_FEET <= y <= DEF_CEIL_Y - 1 and DEF_Z[0] <= z <= DEF_Z[1]:
        return True
    return z == DEF_WALL_Z and DEF_DOOR_X[0] <= x <= DEF_DOOR_X[1] and GROUND_FEET <= y <= GROUND_FEET + 2


def _defence_shell(p):
    """The block for a cell of Defence's walls, floor or ceiling."""
    x, y, z = p
    if y == DEF_FLOOR_Y:
        if DEF_X[0] <= x <= DEF_X[1] and DEF_Z[0] <= z <= DEF_Z[1]:
            if DEF_DOOR_X[0] <= x <= DEF_DOOR_X[1] and z < DAIS_Z[0]:
                return 'minecraft:polished_deepslate'        # the aisle under the dragon
            return 'minecraft:dark_oak_planks'
        return 'minecraft:stone_bricks'
    if y == DEF_CEIL_Y:
        return G.state('stripped_dark_oak_log', axis='x') if z in PILASTERS_Z else 'minecraft:dark_oak_planks'
    if x in (DEF_X[0] - 1, DEF_X[1] + 1) and z in PILASTERS_Z:
        return G.state('dark_oak_log', axis='y')
    if z in (DEF_WALL_Z, DEF_BACK_Z) and x in (DEF_X[0] + 1, DEF_X[1] - 1):
        return G.state('dark_oak_log', axis='y')
    return 'minecraft:stone_bricks'


def carve_defence(grid, layers):
    """Carve the hall out of the hill's hollow (the library's rock fill) and line it: every interior cell cleared,
    every cell of its walls, floor and ceiling that is the hollow's set to its block; a cell of them outside the
    hollow is the old castle's own (the hill's shell, the apse's walls) and is left as it is, but it must be solid.
    Raises if an interior cell is not the hollow's."""
    x0, y0, z0, x1, y1, z1 = DEFENCE_SHELL_BOX
    for y in range(y0, y1 + 1):
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                p = (x, y, z)
                hollow = (x, z) in layers.get(y, ())
                if _defence_interior(p):
                    assert hollow, ('Defence interior outside the hollow', p)
                    grid.clear(x, y, z)
                elif hollow:
                    grid.set(x, y, z, _defence_shell(p))
                else:
                    assert not air_at(grid.cells, p), ('Defence would open onto air outside the hollow', p)


def build_defence_doorway(grid):
    """The doorway's second leaf, in the hall's own north wall (z 191): the same pointed opening as in the gatehouse's
    wall, and three stone-brick stairs in it stepping down from the vestibule's floor (feet 26) to the hall's (25)."""
    _doorway(grid, DEF_WALL_Z)
    for x in range(DEF_DOOR_X[0], DEF_DOOR_X[1] + 1):
        grid.set(x, GROUND_FEET - 1, DEF_WALL_Z, _stairs('stone_brick_stairs', 'north'))


DEF_DESKS_X = (35, 36, 40, 41)
DEF_ROWS = ((193, 194), (195, 196), (197, 198))               # (chair z, desk z): students face south, to the dais


def furnish_defence(grid):
    """Twelve dark-oak desks in three rows either side of the aisle, the dais at the south end (dark oak, a step up)
    with the teacher's desk (candles and a skull on it) and chair, skull shelves and a wardrobe for a boggart at the
    back, soul lanterns from the ceiling and soul torches on the pilasters."""
    for x in DEF_DESKS_X:
        for chair_z, desk_z in DEF_ROWS:
            grid.set(x, DEF_FEET, desk_z, _slab('dark_oak_slab'))
            grid.set(x, DEF_FEET, chair_z, _stairs('dark_oak_stairs', 'north'))
    for x in range(DAIS_X[0], DAIS_X[1] + 1):
        for z in range(DAIS_Z[0], DAIS_Z[1] + 1):
            grid.set(x, DEF_FEET, z, 'minecraft:dark_oak_planks')
    for x in range(DAIS_X[0], DAIS_X[1] + 1):
        if not DEF_DOOR_X[0] <= x <= DEF_DOOR_X[1]:
            grid.set(x, DEF_FEET, DAIS_Z[0] - 1, _stairs('dark_oak_stairs', 'south'))   # the dais's front steps
    for x, y, z in CLASSROOMS['defence']['teacher']:
        grid.set(x, y, z, 'minecraft:dark_oak_planks')
    grid.set(37, DEF_FEET + 2, DAIS_Z[0], _candles(3, 'black'))
    grid.set(39, DEF_FEET + 2, DAIS_Z[0], G.state('skeleton_skull', rotation=8, powered=False))
    grid.set(DEF_AXIS, DEF_FEET + 1, DAIS_Z[1], _stairs('dark_oak_stairs', 'south'))
    for x in (DEF_X[0], DEF_X[0] + 1):                        # skull shelves in the south-west corner
        for y in range(DEF_FEET, DEF_FEET + 3):
            grid.set(x, y, DEF_Z[1], 'minecraft:bookshelf')
    grid.set(DEF_X[0], DEF_FEET + 3, DEF_Z[1], G.state('skeleton_skull', rotation=0, powered=False))
    grid.set(DEF_X[0] + 1, DEF_FEET + 3, DEF_Z[1], G.state('wither_skeleton_skull', rotation=0, powered=False))
    for x in (DEF_X[1] - 1, DEF_X[1]):                        # the boggart's wardrobe in the south-east corner
        hinge = 'right' if x == DEF_X[1] - 1 else 'left'
        for y, half in ((DEF_FEET, 'lower'), (DEF_FEET + 1, 'upper')):
            grid.set(x, y, DEF_Z[1], G.state('dark_oak_door', facing='north', half=half, hinge=hinge, open=False,
                                              powered=False))
        grid.set(x, DEF_FEET + 2, DEF_Z[1], _slab('dark_oak_slab', 'bottom'))
    for x in (DEF_X[0] + 1, DEF_X[1] - 1):
        for z in (193, 200):
            grid.set(x, DEF_CEIL_Y - 1, z, _lantern(True, soul=True))
    for z in (194, 200):
        grid.set(DEF_X[0], DEF_FEET + 2, z, G.state('soul_wall_torch', facing='east'))
        grid.set(DEF_X[1], DEF_FEET + 2, z, G.state('soul_wall_torch', facing='west'))


def _bone(axis):
    return G.state('bone_block', axis=axis)


def dragon_cells():
    """{cell: state} the dragon skeleton over the aisle, its head north over the doorway: a skull (cranium with brow
    ridges, the snout, the jaw hanging open under it) on a short neck, a spine at y 29 hung by three chains from the
    ceiling, two ribs a cell apart hanging down each side and a short one behind them, wings spread flat under the
    ceiling from the shoulders (an arm, a spur forward, two fingers trailing back), and the tail curling down over the
    dais. Every bone is at y 28 or above (three clear over a walking head), and all of them are one piece with the
    chains (the wings join the spine through the shoulder blades)."""
    x, top = DEF_AXIS, DEF_CEIL_Y - 2                         # the spine's height, y 29
    out = {}
    out[(x, top, 192)] = _bone('z')                           # the snout
    out[(x, top - 1, 192)] = _bone('z')                       # the jaw, dropped open
    for dx in (-1, 0, 1):                                     # the cranium with its brow ridges
        out[(x + dx, top, 193)] = _bone('x')
    for z in range(194, 202):                                 # the neck, the spine and the tail
        out[(x, top, z)] = _bone('z')
    out[(x, top - 1, 201)] = _bone('y')                       # the tail's tip, curled down over the dais
    for z, reach, drop in ((197, 2, True), (199, 2, True), (200, 1, False)):
        for side in (-1, 1):                                  # the ribs: out along x, then hanging down
            for k in range(1, reach + 1):
                out[(x + side * k, top, z)] = _bone('x')
            if drop:
                out[(x + side * reach, top - 1, z)] = _bone('y')
    for side in (-1, 1):                                      # the wings, flat under the ceiling from the shoulders
        out[(x + side, top, 196)] = _bone('x')                # the shoulder blade, joining the wing to the spine
        for k in range(1, 5):
            out[(x + side * k, top + 1, 196)] = _bone('x')    # the arm
        out[(x + side * 3, top + 1, 195)] = _bone('z')        # the spur, forward
        for z in (197, 198):                                  # the inner finger, trailing back
            out[(x + side * 3, top + 1, z)] = _bone('z')
        for z in (197, 198, 199):                             # the outer finger, longer
            out[(x + side * 4, top + 1, z)] = _bone('z')
    for z in (193, 197, 200):                                 # the chains from the ceiling
        out[(x, top + 1, z)] = CHAIN
    return out


def build_dragon(grid):
    for (x, y, z), s in dragon_cells().items():
        grid.set(x, y, z, s)


# ---------------------------------------------------------------- placing it

def place_classrooms(grid, layers):
    """Open the gatehouse and build the three classrooms and the library stair into the castle grid. layers is the
    hill's hollow, {y: {(x, z)}} (rooms_library.cave_plan of the old castle): Defence is carved only out of it.
    Returns {'chests', 'student_spots'} to merge into the castle's anchors."""
    open_gate(grid)
    build_vestibule(grid)
    build_library_stair(grid)
    build_charms(grid)
    build_transfiguration(grid)
    carve_defence(grid, layers)
    build_defence_doorway(grid)
    furnish_defence(grid)
    build_dragon(grid)
    lights = hall.light_dark_floors(grid, light_boxes())
    board, cages = transfiguration_props(grid.cells)
    place_classrooms.stats = {
        'desks': {n: len(student_desks(grid.cells, n)) for n in sorted(CLASSROOMS)},
        'dragon_bones': dragon_report(grid.cells)['bones'], 'board': board, 'cages': cages,
        'feathers': floating_feathers(grid.cells), 'lights': lights,
    }
    return {
        'chests': [dict(SPELL_STUDY_CHEST)],
        'student_spots': [list(p) for p in STUDENT_SPOTS],
    }
