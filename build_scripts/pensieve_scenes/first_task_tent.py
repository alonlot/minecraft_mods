"""Builds the First Task memory's opening set: structure/first_task_tent.nbt, the champions' tent behind the arena's
stands, and the anchors first_task_scene.py stages `first_task` with.

    python build_scripts/pensieve_scenes/first_task_tent.py

Using the set from a scene (first_task_scene.py):

    import first_task_tent as FT
    'radius': FT.RADIUS,
    'set': {'templates': [{'id': 'hogwartsmagic:first_task_tent', 'at': list(FT.TEMPLATE_AT)}],
            'time_of_day': FT.TIME_OF_DAY}

The set
-------
  - The tent: a canvas marquee 15 x 12 x 20 (cells x -7..7, y 0..11, z -10..9), its long axis along z, the flap on the
    south (+z) end. Walls 4 high (y 0..3) of white wool in panels that alternate with cream (mushroom stem; the two
    stay inside a 25 luma spread, so it reads as weathered canvas, not a chessboard), a light grey eave band at y 4
    hung with a valance of white banners, and a true 1:1 pitched roof to a ridge at y 11: smooth quartz and smooth
    sandstone stairs in 3-wide panels, an upside-down stair under every step so the ceiling inside is one continuous
    slope, one cell of overhang at each gable. Guy lines (a pole, a rope of posts, a stake) stand outside each pole
    line. Inside:
      * a frame of thin spruce poles: two centre poles (x 0, z -6 and z 1) under a ridge pole at y 10, rising
        through the ridge to finials with red and blue pennants; tie poles across the tent at y 4 (z -6, z 1) and
        wall poles; the four lantern points hang under the tie poles (`lanterns`);
      * a floor of packed earth in coherent patches (coarse dirt, dirt, packed mud, rooted dirt by the walls), two
        large layered rugs over the back half and a rush mat down the walk to the flap;
      * two braziers (campfires on blackstone pedestals) either side of the middle, light blocks low in the
        corners so the dim back of the tent still reads;
      * the bench the viewer waits on (spruce slabs, x -1..1, z 4), facing the flap;
      * a table on the east side with candles, a lantern and a water jug; a cot with a trunk at its foot in the
        back east corner; barrels in the back west corner; a second bench along the west wall; three faded
        school banners (blue, red, gold) on the back wall;
      * the flap: an opening 5 wide and 5 high (x -2..2, y 0..4) in the south gable, its canvas tied back on both
        sides.
  - Outside, in daylight: a strip of trampled ground (coarse dirt, path, packed mud, gravel) from the flap to the
    back of a timber stand that closes the view south (z 20..36, x -28..28, 16 high): posts and beams, boarded at
    the foot, open lattice above showing the stepped underside of its seat rows, zigzag bracing, an access stair
    up its back, house banners under the top beam and flags on poles along its crest, and a dark passage under it
    in line with the flap (the way into the arena): 14 deep, lined with dark oak darkening to deepslate tiles, its
    last 3 cells turning east past a dim glow, so it bends out of sight. Two smaller tents (red and blue) stand to
    either side, with barrels, a cart, a trough and fence posts, on grass that rises into a wooded bank at the
    set's edge.
  - Closed underneath: the ground is one layer at y -1 everywhere inside the set (the tent's floor included).

ANCHORS format
--------------
Every coordinate is a SCENE coordinate (template local + TEMPLATE_AT). The floor and the ground are the block layer
at y -1, so anyone in the tent or outside stands at y 0 (floor_y). yaw is Minecraft's: 0 faces +Z (south, out of
the flap), 90 -X (west), 180 -Z (north), -90 +X (east).

  floor_y        0.0
  viewer_start   {seat: [x, seat_top_y, z, yaw], pos: [x, y, z] the feet spot in front of the bench, yaw} the
                 viewer waits seated on the bench facing the flap; `pos` is where they stand up to
  walk_out       [[x, y, z], ...] feet points from `pos` out through the flap to the trampled strip (all clear)
  flap           {centre: [x, y, z] the middle of the opening at floor level, yaw: the outward yaw (0),
                 half_width, height, box: [[x1, y1, z1], [x2, y2, z2]] the open cells' bounds}
  champions      3 x {pos, yaw, pose ('stand' or 'seat'), seat (for 'seat': [x, seat_top_y, z, yaw])} spots in
                 the dim back half for the three other champions
  lanterns       4 x {pivot: [x, y, z] the underside of a tie beam, hang: drop to the lantern's centre} for
                 memory_block lanterns that swing on each thud
  dust_boxes     [[[x1, y1, z1], [x2, y2, z2]], ...] boxes just under the canvas roof that shed dust
  braziers       [[x, y, z], ...] the two campfires' centres (for embers or smoke)
  cam_spots      3 x {pos, look}: [0] the back corner looking down the tent past the bench to the bright flap;
                 [1] low beside the flap looking in at the bench; [2] outside on the strip looking back at the flap
  outside        {pos, yaw} a spot on the trampled strip, 5 out from the flap, facing the stand
  RADIUS         int
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import physics_problems, FREE, STRUCTURES  # noqa: E402
from great_hall_set import state, bid, parse_state, write_template  # noqa: E402

OUT = os.path.join(STRUCTURES, 'first_task_tent.nbt')
RADIUS = 48
TIME_OF_DAY = 6000                      # midday: the overcast arena day outside
MAX_BLOCKS = 40000
TEMPLATE_AT = (-48, -2, -48)
SIZE = (97, 26, 97)                      # x -48..48, y -2..23, z -48..48

TENT_X = 7                              # walls at |x - 0| = 7 (cells -7..7), so the tent's middle is x 0.5
TENT_Z = (-10, 9)                       # gables at z -10 (back) and z 9 (flap end)
WALL_TOP = 3                            # canvas walls y 0..3
RIDGE = 11                              # the ridge: a true 1:1 pitch from the eaves at y 4
EAVE = 4
FLAP_X = (-2, 2)                        # the opening's cells
FLAP_TOP = 4                            # y 0..4
POLE_Z = (-6, 1)                        # centre poles, tie beams and their wall poles
TIE_Y = 4
STAND_Z = 20                            # the stand's back frame
STAND_X = (-28, 28)
STAND_TOP = 16
STAND_ROWS = 5
PASSAGE_DEPTH = 14                      # the dark passage under the stand, turning east at its end


# ---------------------------------------------------------------- noise
def _hash(ix, iz, seed):
    h = (ix * 374761393 + iz * 668265263 + seed * 982451653) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFFFF) / float(0x1000000)


def _smooth(t):
    return t * t * (3 - 2 * t)


def vnoise(x, z, scale, seed):
    fx, fz = x / scale, z / scale
    ix, iz = math.floor(fx), math.floor(fz)
    tx, tz = _smooth(fx - ix), _smooth(fz - iz)
    a, b = _hash(ix, iz, seed), _hash(ix + 1, iz, seed)
    c, d = _hash(ix, iz + 1, seed), _hash(ix + 1, iz + 1, seed)
    return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz


def rnd(x, y, z, seed):
    return _hash(x * 31 + y * 7919, z * 17 + y * 104729, seed)


class World:
    def __init__(self):
        self.g = {}

    def set(self, p, s):
        if s is None or s == 'minecraft:air':
            self.g.pop(p, None)
        else:
            self.g[p] = s if ':' in s else 'minecraft:' + s

    def get(self, p):
        return self.g.get(p)


# ---------------------------------------------------------------- the ground
GROUND_X = (-46, 46)
GROUND_Z = (-46, 46)


def bank_height(x, z):
    """The wooded bank rising at the set's edge (0 inside), so nothing outside sees the set end."""
    d = max(abs(x) - 34, z - 40, -30 - z, 0)
    if d <= 0:
        return 0
    return int(min(7, d * 0.9 + vnoise(x, z, 6.0, 5) * 2.0))


def strip_block(x, z):
    """The trampled ground in front of the flap and along the stand's foot."""
    n = vnoise(x, z, 3.0, 11)
    r = rnd(x, 0, z, 12)
    if r < 0.06:
        return 'minecraft:gravel'         # (build_ground props it with stone)
    if n < 0.35:
        return 'minecraft:coarse_dirt'
    if n < 0.6:
        return 'minecraft:dirt_path'
    if n < 0.8:
        return 'minecraft:packed_mud'
    return 'minecraft:dirt'


def in_strip(x, z):
    # the flap's apron and the worn band along the stand's foot
    if 10 <= z <= 19 and abs(x - 0.5) <= 6 + (z - 10) * 0.8 + vnoise(x, z, 4.0, 3) * 2.5:
        return True
    return 16 <= z <= 19 and STAND_X[0] - 2 <= x <= STAND_X[1] + 2 and vnoise(x, z, 5.0, 9) < 0.75


def build_ground(w):
    for x in range(GROUND_X[0], GROUND_X[1] + 1):
        for z in range(GROUND_Z[0], GROUND_Z[1] + 1):
            h = bank_height(x, z)
            if h == 0:
                top = strip_block(x, z) if in_strip(x, z) else 'minecraft:grass_block'
                w.set((x, -1, z), top)
                if top == 'minecraft:gravel':
                    w.set((x, -2, z), 'minecraft:stone')
                continue
            # the bank: grass on top, coarse dirt and moss down its inner face to the next column in
            lo = min(bank_height(x + dx, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            for y in range(lo - 1, h - 1):
                w.set((x, y, z), 'minecraft:dirt' if rnd(x, y, z, 2) < 0.6 else 'minecraft:coarse_dirt')
            w.set((x, h - 1, z), 'minecraft:grass_block')


# ---------------------------------------------------------------- the tent
CANVAS = 'minecraft:white_wool'
CREAM = 'minecraft:mushroom_stem[down=true,east=true,north=true,south=true,up=true,west=true]'
VALANCE = 'minecraft:light_gray_wool'


ROOF_WHITE = 'smooth_quartz_stairs'
ROOF_CREAM = 'smooth_sandstone_stairs'
ROOF_IDS = ('minecraft:' + ROOF_WHITE, 'minecraft:' + ROOF_CREAM, 'minecraft:smooth_quartz', 'minecraft:spruce_fence')


def roof_h(x):
    """The roof's outer stair y over column x: 1:1 from the eave band (y 4 at |x| 7) to the ridge (y 11 at x 0)."""
    return EAVE + TENT_X - abs(x)


def canvas(x, z):
    """White and cream panels along the walls (3 wide), grey seams at the poles."""
    if z in POLE_Z or z in TENT_Z:
        return VALANCE
    return CANVAS if ((z + 30) // 3) % 2 == 0 else CREAM


def roof_kind(z):
    return ROOF_WHITE if ((z + 30) // 3) % 2 == 0 else ROOF_CREAM


def log(axis, kind='stripped_spruce_log'):
    return state(kind, axis=axis)


def build_tent(w):
    """The marquee: wool walls, a true pitched roof of stairs (smooth quartz and smooth sandstone panels) with an
    upside-down stair under every step so the ceiling inside is one continuous slope, a valance of white banners
    from the eave band, finials with pennants on the centre poles, the flap tied back, guy lines outside."""
    z0, z1 = TENT_Z
    for x in range(-TENT_X, TENT_X + 1):
        h = roof_h(x)
        up = 'east' if x < 0 else 'west'                       # the high side of each step, toward the ridge
        for z in range(z0 - 1, z1 + 2):                         # one cell of overhang over each gable
            kind = roof_kind(z)
            if abs(x) == TENT_X:
                if z0 <= z <= z1:
                    for y in range(0, WALL_TOP + 1):
                        w.set((x, y, z), canvas(x, z))
                    w.set((x, EAVE, z), VALANCE)               # the eave band
                continue
            if x == 0:
                w.set((x, h, z), 'minecraft:smooth_quartz')    # the ridge
                continue
            w.set((x, h, z), state(kind, facing=up, half='bottom', shape='straight', waterlogged='false'))
            w.set((x, h - 1, z), state(kind, facing=OPP[up], half='top', shape='straight', waterlogged='false'))
        # the gables, closed up to the roof's underside
        for z in (z0, z1):
            if abs(x) >= TENT_X:
                continue
            for y in range(0, h - 1 if x else h):
                if z == z1 and FLAP_X[0] <= x <= FLAP_X[1] and y <= FLAP_TOP:
                    continue
                w.set((x, y, z), CANVAS if abs(x) % 4 != 1 else CREAM)
    # the tied-back flaps: the canvas folded outward either side of the opening
    for sx in (FLAP_X[0] - 1, FLAP_X[1] + 1):
        for y in range(0, FLAP_TOP + 1):
            w.set((sx, y, z1 + 1), CREAM if y % 2 else CANVAS)
        w.set((sx, FLAP_TOP + 1, z1 + 1), VALANCE)
    for sx in (FLAP_X[0], FLAP_X[1]):
        w.set((sx, FLAP_TOP + 1, z1 + 1), CANVAS)             # the fold top, over the opening edge
    # a valance of white banners hanging from the eave band, and along the back gable
    for z in range(z0, z1 + 1):
        if z in POLE_Z:
            continue
        for sx, f in ((-TENT_X - 1, 'west'), (TENT_X + 1, 'east')):
            w.set((sx, EAVE, z), state('white_wall_banner', facing=f))
    for x in range(-TENT_X + 2, TENT_X - 1):
        if x % 2:
            continue
        w.set((x, EAVE - 1, z0 - 1), state('white_wall_banner', facing='north'))
    # the floor: packed earth in coherent patches - coarse dirt and dirt, worn to packed mud, rooted dirt by the walls
    for x in range(-TENT_X, TENT_X + 1):
        for z in range(z0, z1 + 1):
            r = vnoise(x, z, 2.5, 21) * 0.75 + rnd(x, 0, z, 22) * 0.25
            m = vnoise(x, z, 4.0, 23)
            if m < 0.3:
                s = 'minecraft:packed_mud'
            elif abs(x) >= TENT_X - 1 and m > 0.65:
                s = 'minecraft:rooted_dirt'
            else:
                s = 'minecraft:coarse_dirt' if r < 0.5 else 'minecraft:dirt'
            w.set((x, -1, z), s)
    # the frame, in thin spruce poles (fences; connect_fences joins them): ridge pole, centre poles through the
    # ridge to finials with pennants, tie poles across the tent, wall poles
    for z in range(z0 + 1, z1):
        w.set((0, RIDGE - 1, z), POLE)
    for i, pz in enumerate(POLE_Z):
        for y in range(0, RIDGE - 1):
            w.set((0, y, pz), POLE)
        for y in range(RIDGE + 1, RIDGE + 4):
            w.set((0, y, pz), POLE)                            # the finial, standing out of the ridge
        w.set((0, RIDGE + 4, pz), state(('red', 'blue')[i] + '_banner', rotation=4))
        for x in range(-TENT_X + 2, TENT_X - 1):
            w.set((x, TIE_Y, pz), POLE)
        for sx in (-TENT_X + 1, TENT_X - 1):
            for y in range(0, TIE_Y):
                w.set((sx, y, pz), POLE)
    for sx in (-TENT_X + 1, TENT_X - 1):
        for sz in (z0 + 1, z1 - 1):
            for y in range(0, WALL_TOP + 1):
                w.set((sx, y, sz), POLE)
    # guy lines: a pole outside each pole line and corner, a rope of fence posts stepping down to a stake
    for pz in POLE_Z + (z0 + 1, z1 - 1):
        for sgn in (-1, 1):
            gx = sgn * (TENT_X + 2)
            for y in range(0, 3):
                w.set((gx, y, pz), POLE)
            for k, (dx, y) in enumerate(((1, 2), (2, 1), (3, 0))):
                w.set((gx + sgn * dx, y, pz), POLE if k < 2 else state('spruce_trapdoor', facing='north',
                      half='bottom', open='false', powered='false', waterlogged='false'))


OPP = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}


POLE = 'minecraft:spruce_fence'
FENCES = ('minecraft:spruce_fence', 'minecraft:oak_fence', 'minecraft:dark_oak_fence')


def connect_fences(w):
    """Sets every fence's north/south/east/west the way the game would: toward a fence, or a full block."""
    for p, s in list(w.g.items()):
        name = bid(s)
        if name not in FENCES:
            continue
        x, y, z = p
        props = {}
        for side, (dx, dz) in (('north', (0, -1)), ('south', (0, 1)), ('east', (1, 0)), ('west', (-1, 0))):
            n = bid(w.get((x + dx, y, z + dz)))
            ok = n in FENCES or (n is not None and n in FULL_CONNECT)
            props[side] = 'true' if ok else 'false'
        w.g[p] = state(name, waterlogged='false', **props)


FULL_CONNECT = {'minecraft:spruce_planks', 'minecraft:dark_oak_planks', 'minecraft:spruce_log',
                'minecraft:stripped_spruce_log'}          # (not the canvas: a pole stands clear of it)


def build_furniture(w):
    # the bench the viewer waits on, facing the flap
    for x in (-1, 0, 1):
        w.set((x, 0, 4), state('spruce_slab', type='bottom', waterlogged='false'))
    # the table on the east side
    for x in (4, 5):
        for z in (3, 4):
            w.set((x, 0, z), state('spruce_slab', type='top', waterlogged='false'))
    w.set((4, 1, 3), state('candle', candles=3, lit='true', waterlogged='false'))
    w.set((5, 1, 4), state('lantern', hanging='false', waterlogged='false'))
    w.set((4, 1, 4), state('decorated_pot', cracked='false', facing='west', waterlogged='false'))
    w.set((5, 1, 3), state('candle', candles=1, lit='true', waterlogged='false'))
    # a stool at the table
    w.set((3, 0, 4), state('spruce_trapdoor', facing='west', half='top', open='false', powered='false',
                           waterlogged='false'))
    # braziers: campfires on blackstone pedestals either side of the middle
    for x in (-4, 5):
        w.set((x, 0, -1), state('polished_blackstone_wall', east='none', north='none', south='none', up='true',
                                waterlogged='false', west='none'))
        w.set((x, 1, -1), state('campfire', facing='south', lit='true', signal_fire='false', waterlogged='false'))
    # the cot in the back east corner, with a trunk at its foot
    w.set((5, 0, -9), state('white_bed', facing='north', occupied='false', part='head'))
    w.set((5, 0, -8), state('white_bed', facing='north', occupied='false', part='foot'))
    w.set((5, 0, -7), state('chest', facing='west', type='single', waterlogged='false'))
    w.set((4, 0, -9), state('brown_carpet'))
    # barrels and a crate in the back west corner
    w.set((-5, 0, -9), state('barrel', facing='up', open='false'))
    w.set((-4, 0, -9), state('barrel', facing='up', open='false'))
    w.set((-5, 1, -9), state('barrel', facing='north', open='false'))
    w.set((-5, 0, -8), 'minecraft:spruce_planks')
    w.set((-4, 1, -9), state('lantern', hanging='false', waterlogged='false'))
    # the second bench along the west wall (where one of the others sits)
    for z in (-4, -3, -2):
        w.set((-5, 0, z), state('spruce_slab', type='bottom', waterlogged='false'))
    # two large layered rugs over the back half (a border, a field, an inner panel), on the empty floor only
    def rug(xr, zr, border, field, inner):
        for x in range(xr[0], xr[1] + 1):
            for z in range(zr[0], zr[1] + 1):
                if w.get((x, 0, z)) is not None:
                    continue
                edge = x in xr or z in zr
                core = xr[0] + 2 <= x <= xr[1] - 2 and zr[0] + 2 <= z <= zr[1] - 2
                w.set((x, 0, z), 'minecraft:%s_carpet' % (border if edge else inner if core else field))
    rug((-4, 3), (-9, -6), 'brown', 'red', 'orange')
    rug((-3, 4), (-5, -1), 'black', 'blue', 'cyan')
    # a rush mat of yellow and brown down the walk to the flap
    for z in range(5, 9):
        for x in (0, 1):
            if w.get((x, 0, z)) is None:
                w.set((x, 0, z), 'minecraft:yellow_carpet' if (z + x) % 3 == 0 else 'minecraft:brown_carpet')
    # three faded school banners on the back wall
    for x, c in ((-2, 'blue'), (0, 'red'), (2, 'yellow')):
        w.set((x, 3, TENT_Z[0] + 1), state(c + '_wall_banner', facing='south'))
    # a standing lamp by the bench (the key light on the viewer), a trunk, a basin, straw
    w.set((-3, 0, 6), POLE)
    w.set((-3, 1, 6), state('lantern', hanging='false', waterlogged='false'))
    w.set((-5, 0, 6), state('chest', facing='east', type='single', waterlogged='false'))
    w.set((-5, 0, 7), state('barrel', facing='up', open='false'))
    w.set((-6, 0, -1), state('water_cauldron', level=3))
    for p in ((-6, 0, 3), (-6, 0, 4), (-6, 1, 3)):
        w.set(p, state('hay_block', axis='z'))
    w.set((-5, 0, 4), state('yellow_carpet'))
    # a rug in the front half, off the walk to the flap
    for x in range(-4, -1):
        for z in range(5, 8):
            w.set((x, 0, z), 'minecraft:green_carpet' if x in (-4, -2) or z in (5, 7) else 'minecraft:lime_carpet')
    # a clothes line of hanging robes on the east wall behind the table
    for z in range(5, 8):
        w.set((6, 3, z), log('z'))
    for z, c in ((5, 'black'), (6, 'gray'), (7, 'black')):
        w.set((5, 3, z), state(c + '_wall_banner', facing='west'))
    # low light in the corners, so the dim back still reads
    for p, lv in (((-5, 1, -7), 7), ((4, 1, -6), 7), ((-5, 2, 6), 6), ((5, 2, 7), 6), ((0, 3, -3), 5),
                  ((0, 3, 3), 5)):
        w.set(p, state('light', level=lv, waterlogged='false'))


# ---------------------------------------------------------------- outside: the stand's back, tents, props
def build_stand(w):
    """The back of a timber stand closing the view south. Its rows face +z (the arena, beyond the set); the
    high back is at z STAND_Z, the rows step down 1 every 2 toward +z."""
    x0, x1 = STAND_X
    passage = (-2, 2)
    for x in range(x0, x1 + 1):
        post = (x - x0) % 4 == 0
        for y in range(0, STAND_TOP + 1):
            in_pass = passage[0] <= x <= passage[1] and y <= 4
            if post:
                if not in_pass:
                    w.set((x, y, STAND_Z), log('y', 'spruce_log'))
                continue
            if y in (5, 10, STAND_TOP - 1):
                w.set((x, y, STAND_Z), log('x', 'spruce_log'))
            elif y <= 4 and not in_pass:
                w.set((x, y, STAND_Z), 'minecraft:spruce_planks' if (x % 3) else 'minecraft:dark_oak_planks')
        # the seat rows behind the frame, seen from below through the lattice
        for r in range(STAND_ROWS):
            yy = STAND_TOP - 1 - r * 2
            for dz in (1, 2, 3, 4):
                z = STAND_Z + r * 4 + dz
                if yy < 0:
                    continue
                w.set((x, yy, z), 'minecraft:spruce_planks' if dz < 3 else state('spruce_slab', type='bottom',
                                                                                   waterlogged='false'))
            if post:
                for y in range(0, max(0, yy)):
                    w.set((x, y, STAND_Z + r * 4 + 4), log('y', 'spruce_log'))
        # the crest rail and flags
        w.set((x, STAND_TOP + 1, STAND_Z), state('spruce_fence', east='true' if x < x1 else 'false',
                                                west='true' if x > x0 else 'false', north='false',
                                                south='false', waterlogged='false'))
    # zigzag bracing in the open bays (stairs climbing one way, then the other)
    for bay in range(x0, x1, 4):
        flip = ((bay - x0) // 4) % 2
        for band, ylo in ((0, 6), (1, 11)):
            for i in range(3):
                bx = bay + 1 + (i if not flip else 2 - i)
                w.set((bx, ylo + i, STAND_Z), state('spruce_stairs', facing='east' if not flip else 'west',
                                                    half='bottom', shape='straight', waterlogged='false'))
    # the dark passage under the stand, in line with the flap (the way to the arena)
    depth = PASSAGE_DEPTH
    for x in range(passage[0], passage[1] + 1):
        for z in range(STAND_Z, STAND_Z + depth):
            for y in range(0, 5):
                w.set((x, y, z), None)
            w.set((x, -1, z), 'minecraft:coarse_dirt' if z < STAND_Z + depth - 5 else 'minecraft:packed_mud')
            w.set((x, 5, z), 'minecraft:dark_oak_planks')
        w.set((x, 4, STAND_Z), log('x', 'spruce_log'))

    def lining(z, y):
        # dark oak by the mouth, darkening to deepslate tiles toward the end
        deep = z >= STAND_Z + depth - 6
        if deep and (y + z) % 3:
            return 'minecraft:deepslate_tiles'
        return 'minecraft:dark_oak_planks'
    turn = range(STAND_Z + depth - 3, STAND_Z + depth)
    for z in range(STAND_Z, STAND_Z + depth):
        for y in range(0, 5):
            w.set((passage[0] - 1, y, z), lining(z, y))
            if z not in turn:
                w.set((passage[1] + 1, y, z), lining(z, y))
    for x in range(passage[0] - 1, passage[1] + 2):
        for y in range(0, 6):
            w.set((x, y, STAND_Z + depth), 'minecraft:deepslate_tiles')     # the end wall
    # the turn: the last 3 cells open east into a dark side passage that bends out of sight
    for x in range(passage[1] + 1, passage[1] + 6):
        for z in turn:
            for y in range(0, 5):
                w.set((x, y, z), None)
            w.set((x, -1, z), 'minecraft:packed_mud')
            w.set((x, 5, z), 'minecraft:dark_oak_planks')
        for y in range(0, 5):
            w.set((x, y, turn[0] - 1), 'minecraft:deepslate_tiles')
            w.set((x, y, turn[-1] + 1), 'minecraft:deepslate_tiles')
    for z in turn:
        for y in range(0, 6):
            w.set((passage[1] + 6, y, z), 'minecraft:deepslate_tiles')
    w.set((passage[1], 3, turn[1]), state('light', level=7, waterlogged='false'))     # a glow at the bend
    # posts either side of the passage mouth
    for x in (passage[0] - 1, passage[1] + 1):
        for y in range(0, 6):
            w.set((x, y, STAND_Z - 1), log('y', 'spruce_log'))
        w.set((x, 3, STAND_Z - 2), state('wall_torch', facing='north'))
    # house banners under the top beam, flags along the crest
    colours = ['red', 'yellow', 'blue', 'green', 'light_blue', 'red', 'yellow', 'blue', 'green', 'brown',
               'red', 'yellow', 'blue', 'green']
    for i, x in enumerate(range(x0 + 2, x1, 4)):
        w.set((x, STAND_TOP - 1, STAND_Z - 1), state(colours[i % len(colours)] + '_wall_banner', facing='north'))
    for i, x in enumerate(range(x0, x1 + 1, 8)):
        for y in range(STAND_TOP + 2, STAND_TOP + 5):
            w.set((x, y, STAND_Z), state('spruce_fence', east='false', west='false', north='false',
                                         south='false', waterlogged='false'))
        w.set((x, STAND_TOP + 5, STAND_Z), state(colours[(i * 3) % len(colours)] + '_banner', rotation=0))
    # the access stair up the stand's back, from the east
    for i in range(STAND_TOP - 1):
        x, y = 8 + i, i
        if x > x1 - 1:
            break
        w.set((x, y, STAND_Z - 1), state('spruce_stairs', facing='east', half='bottom', shape='straight',
                                         waterlogged='false'))
        for yy in range(0, y):
            if i % 3 == 0:
                w.set((x, yy, STAND_Z - 1), log('y', 'spruce_log'))


def small_tent(w, cx, cz, half_x, z0, z1, wall, stripe, door_z):
    """A smaller ridge tent along z with a closed door on its z1 end (the other champions' tents)."""
    top = 2
    ridge = top + half_x
    for x in range(cx - half_x, cx + half_x + 1):
        ax = abs(x - cx)
        h = ridge - ax
        for z in range(z0, z1 + 1):
            c = stripe if ((z - z0) // 2) % 2 else wall
            for y in range(max(0, h - 1), h + 1):
                w.set((x, y, z), c)
            if ax == half_x:
                for y in range(0, h + 1):
                    w.set((x, y, z), c)
            w.set((x, -1, z), 'minecraft:coarse_dirt')
        for z in (z0, z1):
            for y in range(0, h):
                w.set((x, y, z), wall)
    for y in range(0, 3):
        w.set((cx, y, door_z), stripe)
    w.set((cx, ridge + 1, (z0 + z1) // 2), state('spruce_fence', east='false', west='false', north='false',
                                                  south='false', waterlogged='false'))


def tree(w, x, z, height, seed):
    base = bank_height(x, z)
    for y in range(base, base + height):
        w.set((x, y, z), log('y', 'spruce_log'))
    top = base + height
    for y in range(base + 2, top + 2):
        r = max(0, int((top + 1 - y) * 0.45 + 0.6))
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) + abs(dz) > r + (1 if r > 1 else 0):
                    continue
                if (dx, dz) == (0, 0) and y < top:
                    continue
                if rnd(x + dx, y, z + dz, seed) < 0.12 and r > 1:
                    continue
                p = (x + dx, y, z + dz)
                if max(abs(p[0]), abs(p[2])) > RADIUS:
                    continue
                if w.get(p) is None:
                    w.set(p, state('spruce_leaves', distance=1, persistent='true', waterlogged='false'))


def build_props(w):
    small_tent(w, -24, -6, 4, -12, 2, 'minecraft:red_wool', 'minecraft:white_wool', 2)
    small_tent(w, 25, -4, 4, -10, 4, 'minecraft:blue_wool', 'minecraft:light_blue_wool', 4)
    # barrels and crates by the stand's foot and the tents
    for p in ((-10, 0, 17), (-11, 0, 17), (-10, 1, 17), (-12, 0, 16), (11, 0, 16), (12, 0, 17), (-19, 0, 4),
              (-20, 0, 4), (20, 0, 7)):
        w.set(p, state('barrel', facing='up', open='false'))
    for p in ((10, 0, 17), (13, 0, 16)):
        w.set(p, 'minecraft:spruce_planks')
    w.set((10, 1, 17), state('lantern', hanging='false', waterlogged='false'))
    # a trough
    for x in (-14, -15):
        w.set((x, 0, 12), state('water_cauldron', level=3))
    # a hand cart
    for x in (14, 15, 16):
        w.set((x, 1, 11), state('spruce_slab', type='bottom', waterlogged='false'))
    for x in (14, 16):
        w.set((x, 0, 11), state('spruce_fence', east='false', west='false', north='false', south='false',
                                waterlogged='false'))
    w.set((17, 0, 11), state('spruce_trapdoor', facing='east', half='bottom', open='true', powered='false',
                             waterlogged='false'))
    # a rope line of posts along the strip's sides
    for z in range(11, 17, 2):
        for x in (-8, 9):
            w.set((x, 0, z), state('spruce_fence', east='false', west='false', north='false', south='false',
                                   waterlogged='false'))
    w.set((-8, 1, 11), state('lantern', hanging='false', waterlogged='false'))
    w.set((9, 1, 11), state('lantern', hanging='false', waterlogged='false'))
    # grass tufts on the open grass
    for x in range(GROUND_X[0], GROUND_X[1] + 1):
        for z in range(GROUND_Z[0], GROUND_Z[1] + 1):
            if bank_height(x, z) == 0 and w.get((x, -1, z)) == 'minecraft:grass_block' and w.get((x, 0, z)) is None:
                if rnd(x, 0, z, 31) < 0.09 and not (-9 <= x <= 10 and -12 <= z <= 20):
                    w.set((x, 0, z), 'minecraft:short_grass')
    # the wooded bank
    for i in range(70):
        a = _hash(i, 0, 41) * 2 * math.pi
        x = int(round(math.cos(a) * 60))
        z = int(round(math.sin(a) * 60))
        x = max(GROUND_X[0] + 2, min(GROUND_X[1] - 2, x))
        z = max(GROUND_Z[0] + 2, min(GROUND_Z[1] - 2, z))
        if bank_height(x, z) >= 2 and not (STAND_X[0] - 3 <= x <= STAND_X[1] + 3 and z > 10):
            tree(w, x, z, 6 + int(_hash(i, 1, 41) * 6), i)


# ---------------------------------------------------------------- anchors
def _p(x, y, z):
    return [round(x, 4), round(y, 4), round(z, 4)]


FLAP_C = (0.5, 0.0, TENT_Z[1] + 0.5)
ANCHORS = {
    'floor_y': 0.0,
    'viewer_start': {'seat': [0.5, 0.5, 4.5, 0.0], 'pos': _p(0.5, 0.0, 5.6), 'yaw': 0.0},
    'walk_out': [_p(0.5, 0.0, 5.6), _p(0.5, 0.0, 7.0), _p(0.5, 0.0, 8.5), _p(0.5, 0.0, 10.0), _p(0.5, 0.0, 12.0),
                 _p(0.5, 0.0, 14.0)],
    'flap': {'centre': _p(*FLAP_C), 'yaw': 0.0, 'half_width': 2.5, 'height': 5.0,
             'box': [[FLAP_X[0], 0, TENT_Z[1]], [FLAP_X[1] + 1, FLAP_TOP + 1, TENT_Z[1] + 1]]},
    'champions': [
        {'pos': _p(-4.6, 0.0, -3.0), 'yaw': -90.0, 'pose': 'seat', 'seat': [-4.5, 0.5, -3.0, -90.0]},
        {'pos': _p(4.0, 0.0, -4.0), 'yaw': 120.0, 'pose': 'stand'},
        {'pos': _p(1.6, 0.0, -7.6), 'yaw': 20.0, 'pose': 'stand'},
    ],
    'lanterns': [{'pivot': _p(x, TIE_Y, z + 0.5), 'hang': 0.9} for z in POLE_Z for x in (-2.5, 3.5)],
    'dust_boxes': [[[-4, 4.6, -9], [0, 5.9, 9]], [[1, 4.6, -9], [5, 5.9, 9]]],
    'braziers': [_p(-3.5, 1.3, -0.5), _p(5.5, 1.3, -0.5)],
    'cam_spots': [
        {'pos': _p(-4.6, 2.6, -8.4), 'look': _p(0.8, 1.2, 9.5)},
        {'pos': _p(2.6, 0.9, 8.2), 'look': _p(0.3, 1.0, 4.0)},
        {'pos': _p(-1.8, 1.6, 15.5), 'look': _p(0.5, 1.6, 7.0)},
    ],
    'outside': {'pos': _p(0.5, 0.0, 14.5), 'yaw': 0.0},
    'RADIUS': RADIUS,
}


def build(world=None):
    """Fills `world` (a dict scene cell -> block state; a new one when None) with the set and returns ANCHORS."""
    w = World()
    if world is not None:
        w.g = world
    _build(w)
    return ANCHORS


def _build(w):
    build_ground(w)
    build_tent(w)
    build_furniture(w)
    build_stand(w)
    build_props(w)
    connect_fences(w)
    return w


# ---------------------------------------------------------------- checks
PASSABLE = set(FREE) | {'minecraft:light', 'minecraft:short_grass'}
CARPETS = ('_carpet',)


def passable(g, c):
    b = bid(g.get(c))
    return b in PASSABLE


def floor_solid(g, x, z, y=0):
    """Whether the cell under feet at y (a block layer at y - 1) is a full block a person stands on."""
    b = bid(g.get((x, y - 1, z)))
    return b is not None and b not in PASSABLE and not b.endswith('_carpet')


def cell(p):
    return (int(math.floor(p[0])), int(math.floor(p[1] + 1e-6)), int(math.floor(p[2])))


def body_clear(g, pos, half=0.3, height=1.8):
    """Problems for a standing body box at pos: every cell it touches must be passable (carpet allowed at the
    feet), and the cells under its corners solid."""
    out = []
    for dx in (-half, half):
        for dz in (-half, half):
            x, z = int(math.floor(pos[0] + dx)), int(math.floor(pos[2] + dz))
            if not floor_solid(g, x, z):
                out.append('no floor under %s at (%d, %d)' % (pos, x, z))
            for y in range(0, int(math.ceil(height))):
                b = g.get((x, y, z))
                if not (passable(g, (x, y, z)) or (y == 0 and b and bid(b).endswith('_carpet'))):
                    out.append('%s blocks the body at %s' % (b, (x, y, z)))
    return out


def check(w):
    g = w.g
    problems = list(physics_problems(g, RADIUS))
    a = ANCHORS
    # the viewer's seat: a bottom slab, its top at the seat y, 2 air above
    sx, sy, sz, _ = a['viewer_start']['seat']
    c = (int(math.floor(sx)), int(math.floor(sy)), int(math.floor(sz)))
    s = g.get(c)
    if not s or not s.startswith('minecraft:spruce_slab') or 'type=bottom' not in s or abs(sy - c[1] - 0.5) > 1e-6:
        problems.append('viewer seat %s is not on a bottom slab (%s)' % (a['viewer_start']['seat'], s))
    for y in (c[1] + 1, c[1] + 2):
        if not passable(g, (c[0], y, c[2])):
            problems.append('viewer seat has %s above it at y %d' % (g.get((c[0], y, c[2])), y))
    for ch in a['champions']:
        if ch['pose'] == 'seat':
            sx, sy, sz, _ = ch['seat']
            c = (int(math.floor(sx)), int(math.floor(sy)), int(math.floor(sz)))
            s = g.get(c)
            if not s or '_slab' not in s or 'type=bottom' not in s:
                problems.append('champion seat %s not on a bottom slab (%s)' % (ch['seat'], s))
            for y in (c[1] + 1, c[1] + 2):
                if not passable(g, (c[0], y, c[2])):
                    problems.append('champion seat %s has %s above' % (ch['seat'], g.get((c[0], y, c[2]))))
        else:
            problems += ['champion ' + p for p in body_clear(g, ch['pos'])]
        if ch['pos'][2] > -1.0:
            problems.append('champion %s is not in the back half' % ch['pos'])
    # standing anchors: solid ground, 2 air
    for name, pos in [('viewer_start.pos', a['viewer_start']['pos']), ('outside', a['outside']['pos'])] + \
            [('walk_out', p) for p in a['walk_out']]:
        problems += ['%s: %s' % (name, p) for p in body_clear(g, pos)]
    # the walk between the walk_out points stays clear (sampled every 0.25)
    pts = a['walk_out']
    for p0, p1 in zip(pts, pts[1:]):
        n = int(math.dist(p0, p1) / 0.25) + 1
        for i in range(n + 1):
            t = i / float(n)
            p = [p0[k] + (p1[k] - p0[k]) * t for k in range(3)]
            problems += ['walk_out: ' + q for q in body_clear(g, p, half=0.3, height=1.6)]
    # the flap opening is all air
    (fx0, fy0, fz0), (fx1, fy1, fz1) = a['flap']['box']
    for x in range(fx0, fx1):
        for y in range(fy0, fy1):
            for z in range(fz0, fz1):
                if g.get((x, y, z)) is not None:
                    problems.append('flap opening has %s at %s' % (g.get((x, y, z)), (x, y, z)))
    # cameras in air
    for i, cs in enumerate(a['cam_spots']):
        cc = cell(cs['pos'])
        if not passable(g, cc):
            problems.append('cam_spots[%d] is inside %s' % (i, g.get(cc)))
    # lantern pivots sit under a tie beam, with air below for the swing
    for ln in a['lanterns']:
        px, py, pz = ln['pivot']
        above = g.get((int(math.floor(px)), int(round(py)), int(math.floor(pz))))
        if not above or 'fence' not in above:
            problems.append('lantern pivot %s is not under a beam (%s)' % (ln['pivot'], above))
        for y in range(1, int(round(py))):
            for dx in (-1, 0, 1):
                cc = (int(math.floor(px)) + dx, y, int(math.floor(pz)))
                if not passable(g, cc) and y >= int(round(py)) - 2:
                    problems.append('lantern at %s swings into %s at %s' % (ln['pivot'], g.get(cc), cc))
    # dust boxes are under canvas: every column has wool above the box
    for (x0, y0, z0), (x1, y1, z1) in a['dust_boxes']:
        for x in range(int(x0), int(x1)):
            for z in range(int(z0), int(z1)):
                if not any(bid(g.get((x, y, z))) in ROOF_IDS for y in range(int(math.floor(y0)), RIDGE + 1)):
                    problems.append('dust box column (%d, %d) has no canvas above' % (x, z))
    # nothing outside the template or beyond the radius
    for (x, y, z) in g:
        lx, ly, lz = x - TEMPLATE_AT[0], y - TEMPLATE_AT[1], z - TEMPLATE_AT[2]
        if not (0 <= lx < SIZE[0] and 0 <= ly < SIZE[1] and 0 <= lz < SIZE[2]):
            problems.append('cell %s outside the template' % ((x, y, z),))
        if abs(x) > RADIUS or abs(z) > RADIUS:
            problems.append('cell %s beyond the radius' % ((x, y, z),))
    if len(g) > MAX_BLOCKS:
        problems.append('first_task_tent has %d blocks, over %d' % (len(g), MAX_BLOCKS))
    return problems


def local_grid(g):
    return {(x - TEMPLATE_AT[0], y - TEMPLATE_AT[1], z - TEMPLATE_AT[2]): s for (x, y, z), s in g.items()}


def write_nbt():
    """Builds the set, runs every check and writes structure/first_task_tent.nbt; returns (palette, blocks, w)."""
    w = _build(World())
    problems = check(w)
    assert not problems, '\n'.join(problems[:40]) + ('\n... %d in all' % len(problems))
    n_pal, n_blocks = write_template(local_grid(w.g), SIZE, OUT)
    return n_pal, n_blocks, w


def main():
    n_pal, n_blocks, w = write_nbt()
    print('first_task_tent.nbt: size %s, %d blocks (cap %d), %d palette states, %d bytes; TEMPLATE_AT %s RADIUS %d'
          % (SIZE, n_blocks, MAX_BLOCKS, n_pal, os.path.getsize(OUT), TEMPLATE_AT, RADIUS))
    counts = {}
    for s in w.g.values():
        counts[bid(s)] = counts.get(bid(s), 0) + 1
    print('  census: ' + ', '.join('%s %d' % (k.split(':')[1], v) for k, v in
                                    sorted(counts.items(), key=lambda kv: -kv[1])[:28]))
    for k, v in ANCHORS.items():
        print('  %s: %s' % (k, v))


if __name__ == '__main__':
    main()
