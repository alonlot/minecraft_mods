"""Builds the Flying memory's lesson set: structure/flying_lawn.nbt, the castle lawn of the Philosopher's Stone flying
lesson, with a full-scale castle flank on its west side (a curtain range, a doorway, and the corner tower with
McGonagall's lit bay window), and the anchors flying_scene.py stages `flying_<h>` and `flying_<h>_chase` with. The
Mini Hogwarts stands to the north on its crag across a valley, as the skyline (SCHEMATIC).

    python -I build_scripts/pensieve_scenes/flying_lawn.py

Using the set from a scene (flying_scene.py):

    import flying_lawn as FL
    'radius': FL.RADIUS,
    'set': {'templates': [{'id': 'hogwartsmagic:flying_lawn', 'at': list(FL.TEMPLATE_AT)}],
            'schematics': [FL.SCHEMATIC], 'time_of_day': FL.TIME_OF_DAY}
    sky effect: {'kind': 'effect', 'id': 'sky', 'style': 'day', 'at': FL.ANCHORS['lawn_centre']}
    fog_far: FL.FOG_FAR (the castle is 110-200 blocks off the lawn; without fog_far the void fog eats it)

The set
-------
  - The lawn: a mown plateau (grass top at y -1, so everyone stands at y 0) about 48 x 45 round the lesson, from the
    foot of the curtain wall (x -26) to x 21, in a level grass glen. North it falls over a brow to a valley floor,
    level at VALLEY_TOP (-7), that runs to the foot of the Mini Hogwarts' crag: the castle stands on ground.
  - The hills: a ring of Highland hillsides closing the glen and the valley, so no camera in the lesson or the chase
    sees the set end. They rise from the glen's edge (GLEN_R 41 from the lawn centre, 29 behind the range, wandering
    +-10) and the valley's sides (x +-46 wandering +-7) over HILL_W (22; the valley's sides over 22 / 1.4) to a
    crest line of sharp summits and low saddles (CREST_LIMITS 31..44), and ridges flank the crag's low sides
    (SIDE_RIDGE_X / _Z). Knolls (KNOLLS) stand out in the glen in front of them, for depth. The surface:
      * broad rock outcrops (stone, andesite, tuff, ragged at the edges, a jagged top) high on the slopes, with
        scree (coarse dirt, andesite, cobble) fanning downhill below each, and cliffs of rock wherever a face drops
        6 or more;
      * woods (patches of spruce and dark oak leaves);
      * a darker band along the crests (green terracotta, moss, more woods), so the far skyline reads darker;
      * elsewhere a mixed sward: moss, grassy patches, coarse dirt, grass and podzol on the level tops, heather
        (alliums) in patches;
      * sparse single-trunk firs in clumps of one to four down the slopes and thicker along the valley's edges,
        and low scrub between.
    It is one block thick and is closed only on the faces a camera in CAM_REGION can see; faces turned away from
    every camera there (the backs of the hills) are open, and a hill's face beside the crag is closed only
    CRAG_FACE_DEPTH (30) down, where the crag's rock hides the rest.
  - The ground is one block thick in the same way, and every step in the glen shows a moss riser, not dirt.
  - The flank (full scale, people 1.8 tall), on the west, in Hogwarts grey (stone bricks with cracked, mossy, cobble
    and plain stone blocks, moss gathering at the foot; ivy climbing the lawn faces in ragged patches):
      * the curtain range: a 2-thick wall, face x -26, from the tower (z 23) to the south turret (z 70): a chamfered
        plinth, a string course at y 6, stepped buttresses every 9, two-light mullioned windows (dark glass at
        y 8..11) under hood moulds with arrow slits below; a corbel table at y 16 carrying a crenellated parapet
        (merlons two wide, capped at y 20.5) in front of the walk at y 18; a low oak roof behind (ridge y 22);
      * the gate tower (x -31..-24, z 57..67, crenellated at y 23..25) with the hospital-wing door in its base at
        z 61..63: a shouldered arch 3 wide and 5 high under a hood mould, lanterns on brackets either side, worn
        flags at the threshold, a dark stone vestibule 11 deep with a closed door at its back;
      * the corner tower (an octagon 16 across, x -39..-24, z 8..23): shell to y 36, a corbelled machicolation and
        crenellated parapet at y 37..40, an oak-shingled spire to y 66; slit windows; and McGonagall's oriel on its east
        face: 6 wide, projecting 2 (x -23, -22) on corbels, its floor at y 8 with a step up to 9.5 (she stands at
        9.5), a stone sill flush with the step that rises to 10 under the glass, a 4 x 4 leaded window (clear
        glass panes) at y 10..13 with a shouldered head, side lights, a lead roof. Behind it her study: walls of
        bookshelves, a desk with lit candles, a red rug bordered green, a beamed ceiling at y 15, a lantern on a
        chain and light blocks, so the window is lit when seen from the lawn;
      * the south turret (round, r 4.2, to y 25, an oak cone to y 37) closing the range at z 72.
  - The Mini Hogwarts (SCHEMATIC, rotation 90 so its long front faces south, over the lawn) on its crag at
    x -72..72, z -180..-85, its base at y CASTLE_Y (-6) on the valley grass. Rock fills the ragged foot of the crag
    shell on the seen side (no sky through it), and stone props the schematic's loose gravel.
  - The chase volume (chase_box) over the lawn between the tower and the castle is open sky.

Cameras and the edge of the world
---------------------------------
  - Keep every camera inside CAM_REGION (x -27..40, z -40..70), the region the hills are built to be seen from.
    Outside it, the open backs of the hills and of the ground can show.
  - void_fraction(cam, look) gives the share of a frame (fov 70, aspect 16:9, no roll) that is empty space below
    the horizon: rays that point below level and hit nothing of the set, template or castle. Task 10 asserts every
    lesson and chase camera key against VOID_BOUND (0.02). write_nbt() asserts it for 86 probe cameras: the
    review's views (the dive west at the tower, hovering east and south, the lawn to the north-west) and the chase
    box's top corners and centre, looking eight ways, level and 15 degrees down. The worst is printed.
  - Cameras up to the chase box's top (y 34) stay clear. Higher than that, check with void_fraction.

ANCHORS format
--------------
Every coordinate is a SCENE coordinate (template local + TEMPLATE_AT). The lawn's grass top is the block layer at
y -1, so anyone on the lawn stands at y 0 (lawn_y). yaw is Minecraft's: 0 faces +Z (south), 90 -X (west), 180 -Z
(north), -90 +X (east).

  lawn_y          0.0, the feet y of everyone on the lesson plateau
  lawn_centre     [x, y, z]  the middle of the aisle between the broom rows
  broom_line      {'west': [10 x row entry], 'east': [10 x row entry]} the two facing rows, north to south. A row
                  entry is {'broom': [x, y, z] the broom's centre lying on the grass (y 0), 'broom_yaw': the
                  direction its handle points (the bristles trail behind), 'student': [x, y, z] a first-year's
                  feet on the broom's left (the broom lies on his right, at his right hand), 'student_yaw'}. The
                  west row faces east (-90) and the east row west (90), across a 5.8-wide aisle at x 0
  hooch_spot      {pos, yaw} Madam Hooch at the north head of the aisle, facing down it (south)
  viewer_spot     {pos, yaw, row, index} the viewer's place: broom_line[row][index]['student'] (east row, 5th)
  neville_fall    {pos, yaw} the grass Neville lands on (between the rows and the wall)
  remembrall_drop [x, y, z] where the Remembrall falls from his pocket (on the grass, y 0.0)
  hospital_exit   {door, inside, yaw} the hospital-wing doorway: `door` the threshold outside, `inside` a spot in
                  the dark vestibule (out of frame from the lawn), yaw facing in (90, -X)
  chase_box       [[x1, y1, z1], [x2, y2, z2]] an all-air box (min and max corners) over the lawn for the chase
  catch_spot      {pos, yaw} the grass under the diving catch, below McGonagall's window
  window_catch    {pos, clear} the film's version: a point in the air at her window's height, 8 blocks out from the
                  glass, with `clear` blocks of air all round it, seen from her eye through the glass
  mcg_window      {stand, centre, yaw} McGonagall's feet behind the sill (y 9.5), the window glass's centre, and the
                  outward yaw (-90: the window faces +X, over the lawn)
  castle_view     {pos, look} a camera spot on the lawn framing the castle on its crag over the lesson, and its
                  look target (the castle's middle)
  castle_box      [[x1, y1, z1], [x2, y2, z2]] the Mini Hogwarts' footprint and height
  RADIUS          int
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import (physics_problems, FREE, STRUCTURES, schematic_grid, rotate_xz,  # noqa: E402
                          SCHEMATIC_SKIPPED)
import leaf_backing as LB  # noqa: E402
from great_hall_set import state, bid, parse_state, write_template  # noqa: E402

OUT = os.path.join(STRUCTURES, 'flying_lawn.nbt')
RADIUS = 200
TIME_OF_DAY = 8000                     # a bright afternoon, the sun past noon toward the west
FOG_FAR = 260
MAX_BLOCKS = 63500                      # the template; with the castle it must leave Task 10 room under 100,000
OPENING_CAP = 99500                     # template + castle: 500 kept free for the scenes' own fills

# lawn frame
CX, CZ = 0, 30                          # the lawn centre (middle of the aisle)
PLATEAU = (-27, 21, 2, 47)              # x0, x1, z0, z1 of the flat lesson ground (top y -1)
WALL_WALK = (-27, -12, 47, 69)          # and the level strip along the wall foot to the door
VALLEY_TOP = -7                        # grass top of the valley floor before the castle crag (level)
VALLEY_HALF = 46                        # the valley floor's half-width (x), from the lawn's brow north to the crag
VALLEY_Z = (-150, -6)
GLEN_R = 41                             # the open glen round the lawn centre before the hills begin
GLEN_WEST_PULL = 12                     # ... nearer behind the range (west): 34
GLEN_WANDER = 10                        # the glen's edge wanders in and out by this much
VALLEY_WANDER = 7                       # ... and the valley's sides
GLEN_DIP = 0.0                          # the glen floor round the lawn stays level to the hills' foot
VALLEY_STEEP = 1.4                      # the valley's sides rise over HILL_W / VALLEY_STEEP
HILL_W = 22                             # the hills' inner slope, foot to crest
SIDE_RIDGE_X = (57, 72)                 # |x| of the ridges beside the crag ...
SIDE_RIDGE_Z = (-160, -92)              # ... and their run along it
CREST_LIMITS = (31, 44)                 # the hills' crest: saddles to summits
CHASE_BOX_XZ = [[-20, 5, -40], [36, 35, 14]]   # = ANCHORS['chase_box'] (the terrain keeps out of it)
CRAG_FACE_DEPTH = 30                    # how far a hill's face beside the crag is closed down
KNOLLS = [  # (x, z, radius, height): knolls standing out in the glen in front of the hills, for depth
    (40, 50, 13, 9), (12, 82, 12, 8), (-42, 88, 11, 7), (47, 17, 9, 7),
]

# the Mini Hogwarts
CASTLE_ROT = 90
CASTLE_Y = VALLEY_TOP + 1               # its lowest blocks sit on the valley grass
CASTLE_AT = (-72, CASTLE_Y, -180)
SCHEMATIC = {'id': 'hogwartsmagic:mini_hogwarts', 'at': list(CASTLE_AT), 'rotation': CASTLE_ROT}

# the flank
WALL_FACE = -26                         # the curtain's lawn face (its cells are x -28, -27)
WALL_X = (-28, -27)
WALL_Z = (23, 70)                       # from the tower's south face to the turret
WALL_TOP = 17                           # the walk's floor cells (walk at y 18)
TOWER_C = (-31.0, 16.0)                 # octagon centre (a cell corner)
TOWER_HALF = 7.5                        # cells x -39..-24, z 8..23
TOWER_CHAMFER = 11.0
TOWER_TOP = 36                          # shell to y 36, machicolation at 37, parapet 38..40
SPIRE_BASE, SPIRE_TOP = 40, 66
BAY_Z = (13, 18)                        # the oriel's cells (front glass z 14..17)
BAY_X = (-23, -22)
BAY_FLOOR = 8                           # floor cells y 8; a step up to 9.5, where she stands
ROOM_CEIL = 15
DOOR_Z = (61, 63)                       # the hospital-wing doorway cells
DOOR_TOP = 4                            # opening y 0..4 (5 high), pointed head at y 4
DTOWER_Z = (57, 67)                     # the gate tower round the door
TURRET_C = (-27.5, 72.5)
TURRET_R = 4.2

TEMPLATE_AT = (-96, -40, -170)
SIZE = (194, 110, 298)                  # x -96..97, y -40..69, z -170..127


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


def fbm(x, z, seed):
    return 0.55 * vnoise(x, z, 23.0, seed) + 0.3 * vnoise(x, z, 9.0, seed + 7) + 0.15 * vnoise(x, z, 4.0, seed + 13)


def rnd(x, y, z, seed):
    return _hash(x * 31 + y * 7919, z * 17 + y * 104729, seed)


# ---------------------------------------------------------------- the castle's crag
def castle_cells():
    """{scene cell: block id} of the Mini Hogwarts as SetBuilder places it (ids only, enough for the checks)."""
    w, h, l, cells = schematic_grid('mini_hogwarts')
    out = {}
    for y in range(h):
        for z in range(l):
            for x in range(w):
                b = cells[x + z * w + y * w * l]
                if b in SCHEMATIC_SKIPPED:
                    continue
                rx, rz = rotate_xz(x, z, w, l, CASTLE_ROT)
                out[(CASTLE_AT[0] + rx, CASTLE_AT[1] + y, CASTLE_AT[2] + rz)] = b
    return out


CASTLE = castle_cells()
CASTLE_COLS = {}
for (_x, _y, _z) in CASTLE:
    if (_x, _z) not in CASTLE_COLS or _y < CASTLE_COLS[(_x, _z)]:
        CASTLE_COLS[(_x, _z)] = _y
CASTLE_TOP_Y = max(p[1] for p in CASTLE)
CASTLE_TOPS = {}
for (_x, _y, _z) in CASTLE:
    if _y > CASTLE_TOPS.get((_x, _z), -999):
        CASTLE_TOPS[(_x, _z)] = _y


def _crag_dist():
    """Chebyshev distance (up to 10) from each cell near the crag to its nearest crag column, and the crag's rock
    height there (its top, capped at CRAG_ROCK_CAP so a tower does not count as rock): the hills meet it at that
    height and rise away from it."""
    d, near = {}, {}
    frontier = list(CASTLE_COLS)
    for c in frontier:
        d[c] = 0
        near[c] = min(CASTLE_TOPS[c], CRAG_ROCK_CAP)
    for step in range(1, 11):
        nxt = {}
        for (x, z) in frontier:
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    n = (x + dx, z + dz)
                    if n not in d:
                        nxt[n] = min(nxt.get(n, 999), near[(x, z)])
        for n in sorted(nxt):
            d[n] = step
            near[n] = nxt[n]
        frontier = sorted(nxt)
    return d, near


CRAG_ROCK_CAP = 40
CRAG_DIST, CRAG_NEAR_TOP = _crag_dist()


# ---------------------------------------------------------------- the ground
def range_footprint(x, z):
    """Cells under the curtain range's roof (inside, behind the wall): no ground is needed there."""
    return -41 <= x <= -29 and WALL_Z[0] <= z <= WALL_Z[1]


def plateau_dist(x, z):
    """Distance past the flat lesson ground: a rounded rectangle (corner radius 7) with a wandering edge, joined
    to the level strip along the wall foot; 0 on the flat."""
    best = 1e9
    for (x0, x1, z0, z1), rr in ((PLATEAU, 7.0), (WALL_WALK, 3.0)):
        dx = max(x0 + rr - x, 0, x - x1 + rr)
        dz = max(z0 + rr - z, 0, z - z1 + rr)
        best = min(best, math.hypot(dx, dz) - rr)
    wobble = (vnoise(x, z, 8.0, 17) - 0.5) * 6.0
    return max(0.0, best + wobble) if best > -3 else 0.0


def glen_radius(x, z):
    """How far the open glen round the lawn reaches before the hills begin, by direction from the lawn centre:
    closest behind the range (west), farthest to the south-east."""
    u, v = x - CX, z - CZ
    r = math.hypot(u, v) or 1.0
    west = max(0.0, -u / r) ** 2
    return GLEN_R - GLEN_WEST_PULL * west + (vnoise(x, z, 26.0, 5) - 0.5) * 2 * GLEN_WANDER


def open_dist(x, z):
    """Distance from (x, z) into the hills: 0 inside the glen (a disc round the lawn) or the valley floor (a band
    north to the castle crag); the hills rise over the next HILL_W blocks to their crest."""
    u, v = x - CX, z - CZ
    d_disc = max(0.0, math.hypot(u, v) - glen_radius(x, z))
    half = VALLEY_HALF + (vnoise(x * 0.3, z, 22.0, 6) - 0.5) * 2 * VALLEY_WANDER
    dx = max(0.0, abs(x) - half)
    dz = max(0.0, VALLEY_Z[0] - z, z - VALLEY_Z[1])
    d_valley = math.hypot(dx, dz) * VALLEY_STEEP     # the valley's sides rise steeper (fewer columns)
    return min(d_disc, d_valley)


def crest_y(x, z):
    """The hills' crest here: low saddles (CREST_LIMITS[0]) between sharp summits (to CREST_LIMITS[1]), the summits
    spaced about 26 blocks apart round the ring, with rougher detail on top."""
    v = vnoise(x, z, 26.0, 61)
    lo, hi = CREST_LIMITS
    return lo + (hi - lo) * v ** 1.4 + (vnoise(x, z, 9.0, 63) - 0.5) * 4.0


def knoll(x, z):
    """Height of the knolls standing out in the glen in front of the hills (0 elsewhere)."""
    best = 0.0
    for kx, kz, kr, kh in KNOLLS:
        d = math.hypot(x + 0.5 - kx, z + 0.5 - kz)
        if d < kr:
            q = 1 - (d / kr) ** 2
            best = max(best, kh * q * q * (0.8 + 0.4 * vnoise(x, z, 4.0, 65)))
    return best


def in_domain(x, z):
    """Whether the set has ground at column (x, z): the glen, the valley and the hills to their crest (nothing is
    built behind a crest, where no camera inside the glen can see)."""
    if (x, z) in CASTLE_COLS or range_footprint(x, z):
        return False
    od = open_dist(x, z)
    return od <= HILL_W + 1 or side_ridge(x, z)


def side_ridge(x, z):
    """The ridges flanking the crag (beside and behind its low west and east rims), so a ray skimming past the
    castle from the chase lands on hillside, not on the bare haze."""
    return SIDE_RIDGE_Z[0] <= z <= SIDE_RIDGE_Z[1] and SIDE_RIDGE_X[0] <= abs(x) <= SIDE_RIDGE_X[1]


def base_height(x, z):
    """The glen floor before the hills: the flat plateau, level ground round it, the brow down into the valley."""
    d = plateau_dist(x, z)
    if d == 0:
        return -1.0
    fall = min(0.035 * d + 0.0026 * d * d, GLEN_DIP)
    top = -1.0 - fall
    if z < PLATEAU[2]:                                 # north: down into the valley's flat floor
        top = max(VALLEY_TOP, -1.0 - (0.035 * d + 0.0026 * d * d))
        cd = CRAG_DIST.get((x, z))
        if cd is not None:                             # a little apron up to the crag
            top = max(top, VALLEY_TOP + max(0.0, 3.0 - cd * 0.6))
    return top


def on_valley_floor(x, z):
    return z < PLATEAU[2] - 4 and base_height(x, z) <= VALLEY_TOP + 0.01


def height(x, z):
    """Top cell y of the ground at (x, z): the glen floor (with its knolls) rising over the hills' slopes to their
    crest."""
    base = base_height(x, z)
    od = open_dist(x, z)
    k = 0.0 if on_valley_floor(x, z) or plateau_dist(x, z) < 8 else knoll(x, z)
    if od <= 0:
        if on_valley_floor(x, z):
            return VALLEY_TOP                          # the valley floor is level: no stray steps
        return int(math.floor(base + k))
    c = crest_y(x, z)
    t = min(1.0, od / HILL_W)
    s = t ** 1.7 * (2.0 - t) ** 0.7                  # concave foot rising out of the glen, rounding at the crest
    top = base + (c - base) * s + fbm(x, z, 71) * 3.0 * math.sin(math.pi * t)   # bumps, never a dip
    top = max(top, base + k)
    if side_ridge(x, z):
        top = max(top, CREST_LIMITS[1] - 4.0 + (vnoise(x, z, 12.0, 64) - 0.5) * 6.0)
    cd = CRAG_DIST.get((x, z))
    if cd is not None:                                 # the hills meet the crag's rock where they touch it
        top = min(top, CRAG_NEAR_TOP[(x, z)] + cd * (5.0 if side_ridge(x, z) else 3.0))
    (bx1, by1, bz1), (bx2, _, bz2) = CHASE_BOX_XZ
    dbox = math.hypot(max(bx1 - x - 0.5, 0, x + 0.5 - bx2), max(bz1 - z - 0.5, 0, z + 0.5 - bz2))
    top = min(top, by1 - 1 + dbox * 3.0)              # the hills stand back from the chase box
    return int(math.floor(top))


CAM_REGION = (-27, 40, -40, 70)        # x0, x1, z0, z1: where the lesson and chase cameras stand (and the window)


def face_seen(x, z, dx, dz):
    """Whether the vertical face of column (x, z) toward (x + dx, z + dz) can be seen from the camera region: some
    point of the region lies on that side. Faces turned away from every camera are left open (no fill)."""
    x0, x1, z0, z1 = CAM_REGION
    return any((px - (x + 0.5)) * dx + (pz - (z + 0.5)) * dz > 0 for px in (x0, x1) for pz in (z0, z1))


def _rock_noise(x, z):
    return vnoise(x, z, 17.0, 45) * 0.75 + vnoise(x, z, 6.0, 46) * 0.25


def _outward(x, z):
    """A unit step up the hill (toward larger open_dist) at (x, z)."""
    gx = open_dist(x + 1, z) - open_dist(x - 1, z)
    gz = open_dist(x, z + 1) - open_dist(x, z - 1)
    n = math.hypot(gx, gz) or 1.0
    return gx / n, gz / n


def hill_material(x, z, od, steep):
    """(top, fill) of a hill column. Rock outcrops in broad patches high on the slopes, with scree fanning down
    below them; cliffs of rock wherever a face drops 6 or more; woods (leaves, over moss on the level) in
    patches; a darker band of woods and terracotta along the crests (the far tone); grass and
    podzol on the level tops (their earth sides would show on a step), moss elsewhere."""
    t = od / HILL_W
    if steep >= 6:
        return 'rock', 'rock'
    rn = _rock_noise(x, z)
    if t > 0.3 and rn > 0.65 + (_hash(x, z, 90) - 0.5) * 0.08:
        if rn < 0.72 and _hash(x, z, 91) < 0.3:       # a ragged edge: turf and heather between the stones
            return 'minecraft:moss_block', 'rock'
        return 'rock', 'rock'
    ox, oz = _outward(x, z)
    if t > 0.12 and any(_rock_noise(int(math.floor(x + ox * d)), int(math.floor(z + oz * d))) > 0.65
                        for d in (3, 6, 9)):
        return 'scree', 'scree'
    band = t > 0.78 + (vnoise(x, z, 8.0, 86) - 0.5) * 0.2
    wood = vnoise(x, z, 11.0, 87) > (0.5 if band else 0.58)
    if wood:
        m = 'minecraft:spruce_leaves' if band or _hash(x, z, 88) < 0.4 else 'minecraft:dark_oak_leaves'
        return m, m
    if band:
        return ('minecraft:green_terracotta' if _hash(x, z, 85) < 0.6 else 'minecraft:moss_block'), \
            'minecraft:green_terracotta'
    v = vnoise(x, z, 14.0, 81) + (vnoise(x, z, 4.0, 89) - 0.5) * 0.25
    if steep == 0:
        return ('minecraft:grass_block' if v < 0.55 else 'minecraft:podzol'), 'minecraft:dirt'
    if v < 0.3:                                        # grassy patches: a grass edge over a moss bank
        return 'minecraft:grass_block', 'minecraft:moss_block'
    if v > 0.82:
        return 'minecraft:coarse_dirt', 'minecraft:moss_block'
    return 'minecraft:moss_block', 'minecraft:moss_block'


def _pick(kind, x, y, z):
    r = rnd(x, y, z, 44)
    if kind == 'rock':
        return 'minecraft:stone' if r < 0.5 else 'minecraft:andesite' if r < 0.78 else \
            'minecraft:tuff' if r < 0.9 else 'minecraft:mossy_cobblestone'
    if kind == 'scree':
        return 'minecraft:coarse_dirt' if r < 0.4 else 'minecraft:andesite' if r < 0.62 else \
            'minecraft:cobblestone' if r < 0.8 else 'minecraft:tuff' if r < 0.9 else 'minecraft:stone'
    if kind == 'minecraft:grass_block':
        return state('grass_block', snowy='false')
    if kind == 'minecraft:podzol':
        return state('podzol', snowy='false')
    if kind.endswith('_leaves'):
        return state(kind.split(':')[1], distance='1', persistent='true', waterlogged='false')
    return kind


CROWN = []                              # the oak crown's leaves: thin outline bits are trimmed off
SCRUB = []                              # the scrub clumps' leaves: backed by moss at the foot of the clump
WOODS = []                              # (x, y, z) of every leaf the hill woods place: the backing pass starts here


def build_ground(w):
    """One-block skin over the height field. A column is filled down to a lower neighbour only on the faces a
    camera in CAM_REGION can see; faces turned away (the backs of the hills) stay open, which no camera inside the
    glen can tell. Beside a column the set leaves out (past a crest), the face closes down to where the hill there
    would be."""
    H = {}
    x0, z0 = TEMPLATE_AT[0], TEMPLATE_AT[2]
    for x in range(x0, x0 + SIZE[0]):
        for z in range(z0, z0 + SIZE[2]):
            if in_domain(x, z):
                H[(x, z)] = height(x, z)
    HILLY.clear()
    WOODS.clear()
    SCRUB.clear()
    CROWN.clear()
    for (x, z), h in H.items():
        bottom = h
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, z + dz)
            nh = H.get(n)
            if nh is None:                             # beside the crag or under the range: close down to them
                nh = CASTLE_TOPS.get(n, -1 if range_footprint(*n) else None)
                if n in CASTLE_TOPS:                   # ... the crag's own rock hides all but the top of the face
                    nh = max(nh, h - CRAG_FACE_DEPTH)
                if nh is None and face_seen(x, z, dx, dz):
                    nh = height(*n)                    # the hill that would stand there, past the edge
            if nh is not None and nh < bottom and face_seen(x, z, dx, dz):
                bottom = nh
        steep = h - bottom
        od = open_dist(x, z) if not side_ridge(x, z) else HILL_W
        hilly = od / HILL_W > 0.02 + vnoise(x, z, 7.0, 43) * 0.12 or (od <= 0 and h > base_height(x, z) + 0.5)
        if hilly:
            HILLY.add((x, z))
            top, fill = hill_material(x, z, od, steep)
            if top.endswith('_leaves'):
                # woods: ONE layer of leaves, a skin over solid moss. Leaves are see-through, so a column of them
                # (or a lid over the hollow) shows the void inside the hill; the moss behind makes them read as woods.
                bottom, fill = min(bottom, h - 1), 'minecraft:moss_block'
                WOODS.append((x, h, z))
            for y in range(bottom, h + 1):
                w.set((x, y, z), _pick(top if y == h else fill, x, y, z))
            if top == 'rock' and rnd(x, h, z, 47) < 0.35 and w.get((x, h + 1, z)) is None:
                w.set((x, h + 1, z), _pick('rock', x, h + 1, z))   # a jagged edge to the crag
            continue
        for y in range(bottom, h + 1):
            if y == h and steep >= 1:
                s = 'minecraft:moss_block'             # a step's riser in green, not a dirt stripe
            elif y == h:
                s = 'minecraft:grass_block'
            elif steep >= 1:
                s = 'minecraft:moss_block'
            elif h - y <= 1 and steep < 4:
                s = 'minecraft:dirt'
            else:
                r = rnd(x, y, z, 42)
                s = ('minecraft:stone' if r < 0.45 else 'minecraft:andesite' if r < 0.7 else
                     'minecraft:cobblestone' if r < 0.85 else 'minecraft:dirt')
            w.set((x, y, z), state('grass_block', snowy='false') if s == 'minecraft:grass_block' else s)
    return H


HILLY = set()


def build_path(w, H):
    """A worn dirt path from the plateau's north edge, past the tower, down into the valley toward the castle."""
    pts = [(-8, 2), (-10, -6), (-9, -16), (-5, -28), (-2, -42), (-4, -56), (-8, -70), (-6, -86), (-2, -100)]
    cells = set()
    for (ax, az), (bx, bz) in zip(pts, pts[1:]):
        L = math.hypot(bx - ax, bz - az)
        for i in range(int(L * 4) + 1):
            t = i / (L * 4)
            px, pz = ax + (bx - ax) * t, az + (bz - az) * t
            wob = (vnoise(px, pz, 5.0, 77) - 0.5) * 1.2
            for dx in (-1, 0, 1):
                cx = int(math.floor(px + wob + dx * 0.9))
                cells.add((cx, int(math.floor(pz))))
    for c in cells:
        if c in H and rnd(c[0], 0, c[1], 78) < 0.92:
            y = H[c]
            if bid(w.get((c[0], y, c[1]))) == 'minecraft:grass_block':
                w.set((c[0], y, c[1]), 'minecraft:dirt_path' if rnd(c[0], 1, c[1], 79) < 0.8
                      else 'minecraft:coarse_dirt')
    return cells


# ---------------------------------------------------------------- masonry
def masonry(x, y, z, base=0):
    """Hogwarts grey: stone bricks with cracked, mossy and rough cobble blocks; moss gathers near the ground."""
    r = rnd(x, y, z, 51)
    moss = 0.03 + (0.14 * max(0.0, 1 - (y - base) / 5.0))
    if r < moss:
        return 'minecraft:mossy_stone_bricks'
    if r < moss + 0.11:
        return 'minecraft:cracked_stone_bricks'
    if r < moss + 0.14:
        return 'minecraft:cobblestone'
    if r < moss + 0.2:
        return 'minecraft:stone'
    return 'minecraft:stone_bricks'


def stairs(facing, half='bottom', m='stone_brick'):
    return state('%s_stairs' % m, facing=facing, half=half, shape='straight', waterlogged='false')


def slab(typ='bottom', m='stone_brick'):
    return state('%s_slab' % m, type=typ, waterlogged='false')


def pane(axis, m='gray_stained_glass_pane'):
    """A pane running along `axis` ('x' or 'z'), joined to its neighbours along it."""
    if axis == 'z':
        return state(m, north='true', south='true', east='false', west='false', waterlogged='false')
    return state(m, north='false', south='false', east='true', west='true', waterlogged='false')


ROOF = 'oak'                            # the Mini Hogwarts' own roofs (oak planks, stairs and slabs): one castle
ROOF_BLOCK = 'minecraft:oak_planks'


class World:
    def __init__(self):
        self.g = {}

    def set(self, p, s):
        if s is None or s == 'minecraft:air':
            self.g.pop(p, None)
        else:
            self.g[p] = s

    def get(self, p):
        return self.g.get(p)


def ground_top(H, x, z):
    return H.get((x, z), -1)


def footing(w, H, x, z, y_from, base_fn=masonry):
    """Masonry from y_from down to the ground (one below the grass) so no wall stands on air."""
    g = ground_top(H, x, z)
    for y in range(min(y_from, g), y_from + 1):
        w.set((x, y, z), base_fn(x, y, z, g))


# ---------------------------------------------------------------- the range
def build_range(w, H):
    """The curtain range: wall cells x -28, -27 from y 0 to the walk (y WALL_TOP), a corbel table and a
    projecting crenellated parapet on the lawn face, buttresses, mullioned windows, a low roof behind."""
    z0, z1 = WALL_Z
    for z in range(z0, z1 + 1):
        footing(w, H, -27, z, -1)
        for y in range(0, WALL_TOP + 1):
            w.set((-27, y, z), masonry(-27, y, z, 0))
        for y in range(WALL_TOP - 1, WALL_TOP + 1):          # the inner course only under the walk
            w.set((-28, y, z), masonry(-28, y, z, 0))
        w.set((WALL_FACE, 0, z), stairs('west'))                    # chamfered plinth
        w.set((WALL_FACE, 6, z), stairs('west', 'top'))             # string course
        w.set((WALL_FACE, WALL_TOP - 1, z), stairs('west', 'top'))  # corbel table
        w.set((WALL_FACE, WALL_TOP, z), masonry(WALL_FACE, WALL_TOP, z))
        w.set((WALL_FACE, WALL_TOP + 1, z), masonry(WALL_FACE, WALL_TOP + 1, z))
        if z % 3 != 2:                                              # merlons two wide, embrasures one
            w.set((WALL_FACE, WALL_TOP + 2, z), masonry(WALL_FACE, WALL_TOP + 2, z))
            w.set((WALL_FACE, WALL_TOP + 3, z), slab('bottom'))
        else:
            w.set((WALL_FACE, WALL_TOP + 2, z), slab('bottom'))
        w.set((-28, WALL_TOP + 1, z), slab('bottom'))               # the walk's inner kerb
    # buttresses every 9, 2 wide, stepped back at y 3 and y 7
    for zb in range(z0 + 5, z1 - 2, 9):
        if DTOWER_Z[0] - 2 <= zb + 1 and zb <= DTOWER_Z[1] + 2:
            continue
        for z in (zb, zb + 1):
            footing(w, H, WALL_FACE, z, -1)
            footing(w, H, WALL_FACE + 1, z, -1)
            for y in range(0, 9):
                w.set((WALL_FACE, y, z), masonry(WALL_FACE, y, z, 0))
            for y in range(0, 3):
                w.set((WALL_FACE + 1, y, z), masonry(WALL_FACE + 1, y, z, 0))
            w.set((WALL_FACE + 1, 3, z), stairs('west'))
            w.set((WALL_FACE, 9, z), stairs('west'))
        # mullioned windows between the buttresses: two lights at y 8..11 and a hood mould, an arrow slit below
        zc = zb + 4
        if zc + 2 > z1 - 1 or DTOWER_Z[0] - 3 <= zc + 2 and zc - 1 <= DTOWER_Z[1] + 3:
            continue
        for z in (zc, zc + 2):
            for y in range(8, 12):
                w.set((-27, y, z), pane('z'))
                w.set((-28, y, z), 'minecraft:black_concrete')
        for y in range(8, 12):
            w.set((-27, y, zc + 1), 'minecraft:polished_andesite')     # the mullion
        w.set((WALL_FACE, 7, zc + 1), slab('top'))                     # sill
        for z in range(zc, zc + 3):
            w.set((WALL_FACE, 7, z), slab('top'))
        for z in range(zc - 1, zc + 4):
            w.set((WALL_FACE, 12, z), stairs('west', 'top'))           # hood mould
        w.set((WALL_FACE, 11, zc - 1), stairs('west', 'top'))
        w.set((WALL_FACE, 11, zc + 3), stairs('west', 'top'))
        for y in (2, 3, 4):
            w.set((-27, y, zc + 1), pane('z'))
            w.set((-28, y, zc + 1), 'minecraft:black_concrete')
    # the low roof behind the parapet: ridge along z at x -35 (y WALL_TOP + 5), eaves at the walk
    for z in range(z0, z1 + 1):
        for x in range(-41, -28):
            d = abs(x + 35)
            top = WALL_TOP + 5 - (d + 1) // 2
            if d == 0:
                w.set((x, top, z), ROOF_BLOCK)
                w.set((x, top + 1, z), slab('bottom', ROOF))
            elif d % 2 == 1:
                w.set((x, top, z), stairs('west' if x > -35 else 'east', m=ROOF))
            else:
                w.set((x, top, z), slab('top', ROOF))
        w.set((-41, WALL_TOP, z), masonry(-41, WALL_TOP, z, 0))  # the back eave (the back wall faces the hill)
    for x in range(-41, -28):
        for y in range(WALL_TOP + 1, WALL_TOP + 6 - (abs(x + 35) + 1) // 2):
            w.set((x, y, z1), masonry(x, y, z1, 0))
    build_door_tower(w, H)


def build_door_tower(w, H):
    """A square gate tower on the range front (x -31..-24, z DTOWER_Z) with the hospital-wing door in its base."""
    zt0, zt1 = DTOWER_Z
    top = WALL_TOP + 6
    for x in range(-31, -23):
        for z in range(zt0, zt1 + 1):
            edge = x in (-31, -24) or z in (zt0, zt1)
            if not edge and x > -29:
                continue
            footing(w, H, x, z, -1)
            for y in range(0, top + 1):
                w.set((x, y, z), masonry(x, y, z, 0))
    for x in range(-29, -24):
        for z in range(zt0 + 1, zt1):
            w.set((x, top, z), 'minecraft:stone_bricks')
    # corbel table and parapet one cell out on the three free faces
    for z in range(zt0 - 1, zt1 + 2):
        for x in range(-31, -22):
            front = x == -23 and zt0 - 1 <= z <= zt1 + 1
            side = z in (zt0 - 1, zt1 + 1) and -31 <= x <= -23
            if not (front or side):
                continue
            if x <= -26 and z in (zt0 - 1, zt1 + 1) and x < -26:
                continue                                         # the parapet stops at the curtain's face
            f = 'west' if front else ('south' if z == zt0 - 1 else 'north')
            w.set((x, top - 1, z), stairs(f, 'top'))
            w.set((x, top, z), masonry(x, top, z))
            w.set((x, top + 1, z), masonry(x, top + 1, z))
            if (x + z) % 2 == 0:
                w.set((x, top + 2, z), masonry(x, top + 2, z))
    # plinth on its front and sides
    for z in range(zt0, zt1 + 1):
        w.set((-23, 0, z), stairs('west'))
        if (-23, z) in H:
            w.set((-23, -1, z), masonry(-23, -1, z))
    for x in range(-26, -23):
        for z in (zt0 - 1, zt1 + 1):
            w.set((x, 0, z), stairs('south' if z == zt0 - 1 else 'north'))
    # a window over the door, string course
    zc = (zt0 + zt1) // 2
    for y in range(11, 15):
        w.set((-24, y, zc), pane('z'))
        w.set((-25, y, zc), 'minecraft:black_concrete')
    w.set((-23, 10, zc), slab('top'))
    w.set((-23, 15, zc), stairs('west', 'top'))
    for z in range(zt0, zt1 + 1):
        w.set((-23, 7, z), stairs('west', 'top'))


def build_door(w, H):
    """The hospital-wing doorway in the gate tower's front: a shouldered arch 3 wide, 5 high, into a dark vestibule."""
    z0, z1 = DOOR_Z
    xf = -24                                                    # the gate tower's front wall cells
    for z in range(z0, z1 + 1):
        for x in range(-35, xf + 1):
            for y in range(0, DOOR_TOP + 1):
                w.set((x, y, z), None)
        w.set((xf + 1, 0, z), None)                             # no plinth across the opening
    # the arch head (shoulders at y 3, solid on the jamb side), a hood mould over it
    for x in (xf, xf - 1):
        w.set((x, 3, z0), stairs('north', 'top'))
        w.set((x, 3, z1), stairs('south', 'top'))
        w.set((x, 4, z0), masonry(x, 4, z0))
        w.set((x, 4, z1), masonry(x, 4, z1))
    for z in range(z0 - 1, z1 + 2):
        w.set((xf + 1, 5 if z0 <= z <= z1 else 4, z), stairs('west', 'top'))
    w.set((xf + 1, 0, z0 - 1), 'minecraft:chiseled_stone_bricks')
    w.set((xf + 1, 0, z1 + 1), 'minecraft:chiseled_stone_bricks')
    # the vestibule: stone floor, side walls, a ceiling, a closed door at the back (x -35)
    for x in range(-35, xf + 1):
        for z in range(z0 - 1, z1 + 2):
            w.set((x, -1, z), 'minecraft:polished_andesite' if (x + z) % 2 else 'minecraft:stone_bricks')
        for z in (z0 - 1, z1 + 1):
            for y in range(0, DOOR_TOP + 2):
                w.set((x, y, z), masonry(x, y, z))
        for z in range(z0, z1 + 1):
            w.set((x, DOOR_TOP + 1, z), masonry(x, DOOR_TOP + 1, z))
    for z in range(z0, z1 + 1):
        for y in range(0, DOOR_TOP + 1):
            w.set((-36, y, z), masonry(-36, y, z))
    w.set((-35, 0, z0 + 1), state('dark_oak_door', facing='east', half='lower', hinge='left', open='false',
                                  powered='false'))
    w.set((-35, 1, z0 + 1), state('dark_oak_door', facing='east', half='upper', hinge='left', open='false',
                                  powered='false'))
    w.set((-35, 2, z0 + 1), None)
    # lanterns on brackets either side of the door, outside
    for z in (z0 - 2, z1 + 2):
        w.set((xf + 1, 4, z), stairs('west', 'top'))
        w.set((xf + 1, 3, z), state('lantern', hanging='true', waterlogged='false'))
    # worn flags at the threshold
    for z in range(z0 - 1, z1 + 2):
        for x in (xf + 1, xf + 2, xf + 3):
            if (x, z) in H and H[(x, z)] == -1 and w.get((x, 0, z)) is None:
                w.set((x, -1, z), 'minecraft:stone_bricks' if rnd(x, 0, z, 61) < 0.6 else 'minecraft:cobblestone')


# ---------------------------------------------------------------- the tower
def oct_in(x, z, half, chamfer, c=TOWER_C):
    dx, dz = x + 0.5 - c[0], z + 0.5 - c[1]
    return abs(dx) <= half and abs(dz) <= half and abs(dx) + abs(dz) <= chamfer


def tower_shell(x, z):
    return oct_in(x, z, TOWER_HALF, TOWER_CHAMFER) and not oct_in(x, z, TOWER_HALF - 2, TOWER_CHAMFER - 2)


def outward(x, z, c=TOWER_C):
    dx, dz = x + 0.5 - c[0], z + 0.5 - c[1]
    if abs(dx) >= abs(dz):
        return 'east' if dx > 0 else 'west'
    return 'south' if dz > 0 else 'north'


def opposite(f):
    return {'east': 'west', 'west': 'east', 'north': 'south', 'south': 'north'}[f]


def build_tower(w, H):
    x0, x1 = int(TOWER_C[0] - TOWER_HALF), int(TOWER_C[0] + TOWER_HALF) - 1
    z0, z1 = int(TOWER_C[1] - TOWER_HALF), int(TOWER_C[1] + TOWER_HALF) - 1
    cells = [(x, z) for x in range(x0 - 2, x1 + 3) for z in range(z0 - 2, z1 + 3)]
    shell = [(x, z) for x, z in cells if tower_shell(x, z)]
    for x, z in shell:
        outer = not oct_in(x, z, TOWER_HALF - 1, TOWER_CHAMFER - 1)
        if outer:
            footing(w, H, x, z, -1)
        # the inner course of the shell is only needed at the top (the walk round the spire); lower down nothing
        # sees it, so the shell is one block (slits and the study set their own backing)
        for y in range(0 if outer else TOWER_TOP - 1, TOWER_TOP + 1):
            w.set((x, y, z), masonry(x, y, z, 0))
    # battered plinth (one ring out at y 0, -1) and string courses
    ring1 = [(x, z) for x, z in cells if oct_in(x, z, TOWER_HALF + 1, TOWER_CHAMFER + 1)
             and not oct_in(x, z, TOWER_HALF, TOWER_CHAMFER)]
    for x, z in ring1:
        if w.get((x, 0, z)) is None and not (x <= -27 and z >= WALL_Z[0]):
            footing(w, H, x, z, -1)
            w.set((x, 0, z), stairs(opposite(outward(x, z))))
        # machicolation: corbels at TOWER_TOP + 1 (37) and the parapet ring at 38..40
        w.set((x, TOWER_TOP + 1, z), stairs(opposite(outward(x, z)), 'top'))
        for y in (TOWER_TOP + 2, TOWER_TOP + 3):
            w.set((x, y, z), masonry(x, y, z))
        if (x + z) % 2 == 0:
            w.set((x, TOWER_TOP + 4, z), masonry(x, TOWER_TOP + 4, z))
    for x, z in shell:
        if (x, z) not in ring1:
            for y in (TOWER_TOP + 1, TOWER_TOP + 2):
                w.set((x, y, z), masonry(x, y, z))
    for x, z in ring1:
        y = TOWER_TOP - 13                             # a string course at y 23
        if w.get((x, y, z)) is None and not (x <= -27 and z >= WALL_Z[0] - 1):
            w.set((x, y, z), stairs(opposite(outward(x, z)), 'top'))
    # slit windows: dark recesses on each face at three levels (not where the bay is)
    for x, z in shell:
        if not oct_in(x, z, TOWER_HALF, TOWER_CHAMFER) or oct_in(x, z, TOWER_HALF - 1, TOWER_CHAMFER - 1):
            continue
        dx, dz = x + 0.5 - TOWER_C[0], z + 0.5 - TOWER_C[1]
        on_face = (abs(abs(dx) - 7.0) < 0.01 and abs(dz) < 0.6) or (abs(abs(dz) - 7.0) < 0.01 and abs(dx) < 0.6)
        if not on_face:
            continue
        if x >= -27 and z >= WALL_Z[0] - 1:
            continue
        axis = 'z' if abs(dx) > abs(dz) else 'x'
        inner = (x - (1 if dx > 0 else -1), z) if axis == 'z' else (x, z - (1 if dz > 0 else -1))
        for ys in ((3, 5), (18, 21), (24, 27)):
            if axis == 'z' and dx > 0 and ys[0] < 15:
                continue                                    # the east face has the bay low down
            for y in range(ys[0], ys[1] + 1):
                w.set((x, y, z), pane(axis))
                w.set((inner[0], y, inner[1]), 'minecraft:black_concrete')
            w.set((x, ys[1] + 1, z), 'minecraft:chiseled_stone_bricks')
    # the spire: an octagonal cone of roof from SPIRE_BASE (40) to SPIRE_TOP (66)
    span = SPIRE_TOP - SPIRE_BASE
    for y in range(SPIRE_BASE, SPIRE_TOP + 1):
        t = (y - SPIRE_BASE) / span
        half = (TOWER_HALF - 0.4) * (1 - t) ** 1.15 + 0.35
        half_up = (TOWER_HALF - 0.4) * max(0.0, 1 - (y + 1 - SPIRE_BASE) / span) ** 1.15 + 0.35
        ch = half * TOWER_CHAMFER / TOWER_HALF
        ch_up = half_up * TOWER_CHAMFER / TOWER_HALF
        for x, z in cells:
            if not oct_in(x, z, half, ch):
                continue
            if oct_in(x, z, half_up, ch_up) and oct_in(x, z, half - 1.1, ch - 1.1):
                continue                                        # hollow inside (one cell of skin)
            if not oct_in(x, z, half_up, ch_up):
                w.set((x, y, z), stairs(opposite(outward(x, z)), m=ROOF))
            else:
                w.set((x, y, z), ROOF_BLOCK)
    w.set((int(TOWER_C[0]), SPIRE_TOP + 1, int(TOWER_C[1])), state('lightning_rod', facing='up', powered='false',
                                                                    waterlogged='false'))
    build_bay(w)
    build_study(w)


def build_bay(w):
    bx0, bx1 = BAY_X
    bz0, bz1 = BAY_Z
    for x in (bx0, bx1):
        for z in range(bz0, bz1 + 1):
            w.set((x, BAY_FLOOR, z), masonry(x, BAY_FLOOR, z))
            for y in range(BAY_FLOOR + 1, 16):
                w.set((x, y, z), masonry(x, y, z))
    # corbels under the bay
    for z in range(bz0, bz1 + 1):
        w.set((bx1, BAY_FLOOR - 1, z), stairs('west', 'top'))
        w.set((bx0, BAY_FLOOR - 1, z), masonry(bx0, BAY_FLOOR - 1, z))
        w.set((bx0, BAY_FLOOR - 2, z), stairs('west', 'top'))
    # interior of the bay (x -23), the opening through the tower wall (x -25, -24)
    for z in range(bz0 + 1, bz1):
        for x in (bx0, -24, -25):
            for y in range(BAY_FLOOR + 1, 14):
                w.set((x, y, z), None)
            w.set((x, BAY_FLOOR, z), 'minecraft:spruce_planks')
            w.set((x, BAY_FLOOR + 1, z), slab('bottom', 'spruce'))  # a step up into the window: she stands at 9.5
    # front: stone sill y 9, glass y 10..13 (z 14..17), shouldered head y 14, parapet cap
    for z in range(bz0 + 1, bz1):
        # the sill: low inside (flush with her step, 9.5), up to 10 under the glass
        w.set((bx1, BAY_FLOOR + 1, z), stairs('east', m='polished_andesite'))
        for y in range(BAY_FLOOR + 2, BAY_FLOOR + 6):
            w.set((bx1, y, z), pane('z', 'glass_pane'))
    w.set((bx1, BAY_FLOOR + 5, bz0 + 1), stairs('north', 'top'))
    w.set((bx1, BAY_FLOOR + 5, bz1 - 1), stairs('south', 'top'))
    w.set((bx1, BAY_FLOOR + 6, bz0 + 1), 'minecraft:chiseled_stone_bricks')
    w.set((bx1, BAY_FLOOR + 6, bz1 - 1), 'minecraft:chiseled_stone_bricks')
    # side lights in the bay's short walls
    for z in (bz0, bz1):
        for y in range(BAY_FLOOR + 2, BAY_FLOOR + 5):
            w.set((bx0, y, z), pane('x', 'glass_pane'))
        w.set((bx0, BAY_FLOOR + 1, z), 'minecraft:polished_andesite')
    # the bay's lead roof sloping back to the tower
    for z in range(bz0, bz1 + 1):
        w.set((bx1, 16, z), stairs('west', m=ROOF))
        w.set((bx0, 16, z), ROOF_BLOCK)
        w.set((bx0, 17, z), stairs('west', m=ROOF))
    # inner sill: the window seat ledge (a slab is the sill top inside, at 9.5)
    # pointed relieving arch over the opening through the tower wall
    for z in range(bz0 + 1, bz1):
        for x in (-24, -25):
            w.set((x, 14, z), masonry(x, 14, z))


def build_study(w):
    """McGonagall's study behind the bay: floor at y 8, ceiling at 15, warm light, books."""
    inner = [(x, z) for x in range(-39, -23) for z in range(8, 24)
             if oct_in(x, z, TOWER_HALF - 2, TOWER_CHAMFER - 2)]
    for x, z in inner:
        w.set((x, BAY_FLOOR, z), 'minecraft:dark_oak_planks' if (x // 2 + z) % 3 else 'minecraft:spruce_planks')
        w.set((x, ROOM_CEIL, z), 'minecraft:dark_oak_planks')
        if (x - 1) % 4 == 0:
            w.set((x, ROOM_CEIL - 1, z), state('stripped_dark_oak_log', axis='z'))
        for y in range(BAY_FLOOR + 1, ROOM_CEIL - (1 if (x - 1) % 4 == 0 else 0)):
            w.set((x, y, z), None)
    # tartan-ish rug: red with green border
    for x, z in inner:
        if -35 <= x <= -28 and 12 <= z <= 19:
            edge = x in (-35, -28) or z in (12, 19)
            w.set((x, BAY_FLOOR + 1, z), 'minecraft:green_carpet' if edge else 'minecraft:red_carpet')
    # bookshelves on the back (west) and side walls, floor to y 13
    for x, z in inner:
        back = not oct_in(x - 1, z, TOWER_HALF - 2, TOWER_CHAMFER - 2) and x < -31
        side = (not oct_in(x, z - 1, TOWER_HALF - 2, TOWER_CHAMFER - 2) or
                not oct_in(x, z + 1, TOWER_HALF - 2, TOWER_CHAMFER - 2)) and x < -27
        if back or side:
            for y in range(BAY_FLOOR + 1, ROOM_CEIL - 1):
                w.set((x, y, z), 'minecraft:bookshelf' if y < ROOM_CEIL - 2 else 'minecraft:dark_oak_planks')
    # desk by the north side, chair, candles
    for z in (11, 12):
        w.set((-30, BAY_FLOOR + 1, z), state('spruce_stairs', facing='west', half='top', shape='straight',
                                             waterlogged='false'))
    w.set((-30, BAY_FLOOR + 2, 11), state('candle', candles='3', lit='true', waterlogged='false'))
    w.set((-30, BAY_FLOOR + 2, 12), state('candle', candles='1', lit='true', waterlogged='false'))
    w.set((-29, BAY_FLOOR + 1, 12), state('dark_oak_stairs', facing='west', half='bottom', shape='straight',
                                          waterlogged='false'))
    # a lantern chandelier and warm fill light
    w.set((-31, ROOM_CEIL - 1, 16), state('chain', axis='y', waterlogged='false'))
    w.set((-31, ROOM_CEIL - 2, 16), state('lantern', hanging='true', waterlogged='false'))
    for p in ((-28, 12, 16), (-33, 12, 15), (-25, 12, 15), (-23, 12, 16), (-30, 11, 19)):
        if w.get(p) is None:
            w.set(p, state('light', level='15', waterlogged='false'))


# ---------------------------------------------------------------- the south turret
def build_turret(w, H):
    cx, cz = TURRET_C
    TT = WALL_TOP + 8
    cells = [(x, z) for x in range(int(cx) - 6, int(cx) + 7) for z in range(int(cz) - 6, int(cz) + 7)]
    for x, z in cells:
        d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
        if d <= TURRET_R:
            footing(w, H, x, z, -1)
            for y in range(0, TT):
                if d > TURRET_R - 1.6:
                    w.set((x, y, z), masonry(x, y, z, 0))
            if TURRET_R - 1.6 < d:
                w.set((x, TT, z), masonry(x, TT, z))
        if TURRET_R < d <= TURRET_R + 1:
            w.set((x, TT - 1, z), stairs(opposite(outward(x, z, TURRET_C)), 'top'))
            w.set((x, TT, z), masonry(x, TT, z))
            if (x + z) % 2:
                w.set((x, TT + 1, z), masonry(x, TT + 1, z))
            if w.get((x, 0, z)) is None and x >= -27:
                footing(w, H, x, z, -1)
                w.set((x, 0, z), stairs(opposite(outward(x, z, TURRET_C))))
    # cone
    for y in range(TT + 1, TT + 13):
        r = (TURRET_R + 0.3) * (1 - (y - TT - 1) / 12.0) ** 1.1 + 0.3
        r_up = (TURRET_R + 0.3) * max(0.0, 1 - (y - TT) / 12.0) ** 1.1 + 0.3
        for x, z in cells:
            d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
            if d <= r and not (d <= r - 1.1 and d <= r_up):
                w.set((x, y, z), stairs(opposite(outward(x, z, TURRET_C)), m=ROOF) if d > r_up
                      else ROOF_BLOCK)


# ---------------------------------------------------------------- trees
def oak(w, H, x, z, height, crown, seed, leaves='oak_leaves', log='oak_log'):
    """A broad old tree: a trunk with a flared foot, two or three limbs, a lumpy crown of leaf blobs."""
    g = H[(x, z)]
    lv = state(leaves, distance='1', persistent='true', waterlogged='false')
    for y in range(g + 1, g + height + 1):
        w.set((x, y, z), state(log, axis='y'))
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if rnd(x + dx, 0, z + dz, seed) < 0.6 and (x + dx, z + dz) in H:
            w.set((x + dx, H[(x + dx, z + dz)] + 1, z + dz), state(log, axis='y'))
    top = g + height
    blobs = [(0.0, top + 1, 0.0, crown)]
    for k in range(4):
        a = _hash(k, seed, 3) * 2 * math.pi
        rr = crown * (0.55 + 0.35 * _hash(k, seed, 4))
        blobs.append((math.cos(a) * rr, top - 1 + _hash(k, seed, 5) * 3, math.sin(a) * rr, crown * 0.7))
        # a limb out to it
        for i in range(1, int(rr) + 1):
            lx, lz = int(round(x + math.cos(a) * i)), int(round(z + math.sin(a) * i))
            w.set((lx, int(top - 2 + i * 0.6), lz), state(log, axis='x' if abs(math.cos(a)) > 0.7 else 'z'))
    R = int(crown) + 3
    for bx, by, bz, br in blobs:
        for dx in range(-R - 3, R + 4):
            for dz in range(-R - 3, R + 4):
                for dy in range(-R, R + 1):
                    px, py, pz = x + dx, int(by) + dy, z + dz
                    d = math.sqrt((dx - bx) ** 2 + (dy * 1.5) ** 2 + (dz - bz) ** 2)
                    if d <= br + (rnd(px, py, pz, seed + 9) - 0.5) * 1.2 and w.get((px, py, pz)) is None:
                        if py > H.get((px, pz), -99) + 2:
                            w.set((px, py, pz), lv)
                            CROWN.append((px, py, pz))


TREES = [  # (x, z, height, crown, kind): framing oaks at the lawn's edge, a stand down the south-east slope
    (31, 20, 7, 4.5, 'oak'),
]


def fir(w, x, y0, z, big):
    """A single-trunk fir: a log spine up the middle of a stacked cone of spruce leaves (about 30 blocks, 60 when big).
    Leaves are see-through, so the cone is built to be backed: rows are 3 deep wherever a row changes width, a row of
    half-width 1 is a full 3x3, and the spine is a log to the tip (the leaf_backing rule holds from every side)."""
    fl = state('spruce_leaves', distance='1', persistent='true', waterlogged='false')
    log = state('spruce_log', axis='y')
    rows = [2, 2, 2, 1, 1, 1, 0, 0] if big else [1, 1, 1, 0, 0]
    cells = [(x, y0, z)] + [(x, y0 + 1 + k, z) for k in range(len(rows))]
    leaves = [(x + dx, y0 + 1 + k, z + dz) for k, a in enumerate(rows) for dx in range(-a, a + 1)
              for dz in range(-a, a + 1) if (dx or dz) and (a < 2 or abs(dx) + abs(dz) <= 2)]
    if any(w.get(c) is not None for c in cells):
        return False
    if any(w.get(c) is not None and not LB.is_opaque(w.get(c)) for c in leaves):
        return False                                   # another tree or a plant in the way: no half-grown cone
    for c in cells:
        w.set(c, log)
    for c in leaves:
        if w.get(c) is None:                           # (the hill itself may bite a corner out of the cone)
            w.set(c, fl)
            SCRUB.append(c)                            # (backed by moss like the scrub where the bite leaves a view)
    return True


def near_chase_box(x, z, margin=3):
    (bx1, _, bz1), (bx2, _, bz2) = CHASE_BOX_XZ
    return bx1 - margin <= x <= bx2 + margin and bz1 - margin <= z <= bz2 + margin


def hill_trees(w, H):
    """Sparse firs in clumps of one to four down the hill slopes and along the valley's edges, scrub (low clumps
    of leaves) between, and heather (purple alliums) in patches on the tops. Returns the fir count."""
    lv = state('dark_oak_leaves', distance='1', persistent='true', waterlogged='false')
    az = state('azalea_leaves', distance='1', persistent='true', waterlogged='false')
    sward = ('minecraft:grass_block', 'minecraft:moss_block', 'minecraft:podzol', 'minecraft:green_terracotta')
    firs = 0
    for (x, z), h in sorted(H.items()):
        if (x, z) not in HILLY or bid(w.get((x, h, z))) not in sward:
            continue
        od = open_dist(x, z)
        r = _hash(x, z, 151)
        valley_edge = z < VALLEY_Z[1] and od <= 6
        if r < (0.012 if valley_edge else 0.0075) and od <= HILL_W:
            n = 1 + int(_hash(x, z, 155) * 4)
            for i in range(n):
                fx = x + int((_hash(i, x, 156) - 0.5) * 7)
                fz = z + int((_hash(i, z, 157) - 0.5) * 7)
                if (fx, fz) in HILLY and not near_chase_box(fx, fz) and \
                        bid(w.get((fx, H[(fx, fz)], fz))) in sward and w.get((fx, H[(fx, fz)] + 1, fz)) is None:
                    firs += fir(w, fx, H[(fx, fz)] + 1, fz, _hash(fx, fz, 158) < 0.3)
        elif r < 0.0105:                               # scrub: 3 to 6 leaves on and over the sward
            m = lv if _hash(x, z, 152) < 0.7 else az
            for dx, dz in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1), (1, 1)):
                if _hash(x + dx, z + dz, 153) < 0.75 and (x + dx, z + dz) in H:
                    hy = H[(x + dx, z + dz)] + 1
                    if w.get((x + dx, hy, z + dz)) is None:
                        w.set((x + dx, hy, z + dz), m)
                        SCRUB.append((x + dx, hy, z + dz))
        elif vnoise(x, z, 10.0, 84) > 0.7 and rnd(x, h, z, 159) < 0.08 and w.get((x, h + 1, z)) is None:
            w.set((x, h + 1, z), 'minecraft:allium')           # heather
    return firs


def build_trees(w, H):
    hill_trees(w, H)
    for i, (x, z, hgt, crown, kind) in enumerate(TREES):
        if (x, z) not in H:
            continue
        if kind == 'dark':
            oak(w, H, x, z, hgt, crown, 100 + i, 'dark_oak_leaves', 'dark_oak_log')
        else:
            oak(w, H, x, z, hgt, crown, 100 + i)


def dress_ground(w, H, path):
    """Sparse tufts and a few flowers on the slopes (none on the mown lesson ground), rocks on the steep falls."""
    for (x, z), h in H.items():
        if (x, z) in path or plateau_dist(x, z) < 6:
            continue
        top = w.get((x, h, z))
        if bid(top) != 'minecraft:grass_block' or w.get((x, h + 1, z)) is not None:
            continue
        r = rnd(x, h, z, 91)
        near = plateau_dist(x, z) < 22
        if r < (0.025 if near else 0.006):
            w.set((x, h + 1, z), 'minecraft:short_grass')
        elif r < (0.03 if near else 0.008):
            w.set((x, h + 1, z), 'minecraft:dandelion' if rnd(x, 1, z, 92) < 0.5 else 'minecraft:oxeye_daisy')


IVY = [  # (z, height, half-width): ivy climbing the lawn faces of the flank from the ground
    (27, 13, 3.5), (36, 7, 2.0), (45, 15, 4.0), (53, 9, 2.5), (12, 6, 2.0), (19, 5, 1.5),
]
MASONRY = {'minecraft:stone_bricks', 'minecraft:cracked_stone_bricks', 'minecraft:mossy_stone_bricks',
           'minecraft:cobblestone', 'minecraft:stone', 'minecraft:chiseled_stone_bricks'}


def build_ivy(w):
    """Ivy in ragged patches up the flank's lawn faces: vines in the air cells against full masonry."""
    for x in range(-27, -21):
        for z in range(8, 77):
            for y in range(1, 20):
                p = (x, y, z)
                if w.get(p) is not None:
                    continue
                faces = {}
                for f, (dx, dz) in (('west', (-1, 0)), ('north', (0, -1)), ('south', (0, 1))):
                    faces[f] = bid(w.get((x + dx, y, z + dz))) in MASONRY
                if not any(faces.values()):
                    continue
                if DOOR_Z[0] - 1 <= z <= DOOR_Z[1] + 1 and y <= DOOR_TOP + 2:
                    continue
                if BAY_Z[0] - 1 <= z <= BAY_Z[1] + 1 and y >= BAY_FLOOR - 4:
                    continue
                inside = False
                for i, (sz, hgt, half) in enumerate(IVY):
                    taper = half * (1.0 - 0.6 * y / hgt) + (rnd(x, y, z, 120 + i) - 0.5) * 1.6
                    if y <= hgt + (rnd(x, y, z, 130 + i) - 0.5) * 3 and abs(z + 0.5 - sz) <= taper:
                        inside = True
                if inside and rnd(x, y, z, 140) < 0.85:
                    w.set(p, state('vine', east='false', north=str(faces['north']).lower(),
                                   south=str(faces['south']).lower(), up='false', west=str(faces['west']).lower()))


def build(world=None):
    """Fills `world` (a dict scene cell -> block state; a new one when None) with the set and returns ANCHORS."""
    w = World()
    if world is not None:
        w.g = world
    _build(w)
    return ANCHORS


def _build(w):
    H = build_ground(w)
    path = build_path(w, H)
    build_range(w, H)
    build_door(w, H)
    build_tower(w, H)
    build_turret(w, H)
    build_ivy(w)
    build_trees(w, H)
    dress_ground(w, H, path)
    support_castle(w)
    back_woods(w)
    return H


def support_castle(w):
    """The schematic has loose gravel on its lowest layer: stone under each such cell, so nothing can fall. And its
    crag is a shell whose foot is ragged: where a rim column of the crag starts above the valley grass, the gap
    under it (sky seen through the foot of the rock) is filled with rock up to it, two columns deep."""
    from scene_common import is_gravity
    for (x, y, z), b in CASTLE.items():
        if is_gravity(b) and (x, y - 1, z) not in CASTLE:
            w.set((x, y - 1, z), 'minecraft:stone')
    rim = set()
    for (x, z) in CASTLE_COLS:
        if any((x + dx, z + dz) not in CASTLE_COLS for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            rim.add((x, z))
    near = set(rim)
    for (x, z) in rim:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if (x + dx, z + dz) in CASTLE_COLS:
                    near.add((x + dx, z + dz))
    for (x, z) in near:
        y0 = CASTLE_COLS[(x, z)]
        if y0 <= CASTLE_Y or y0 - CASTLE_Y > 16 or z < -150:
            continue                                     # (the far side of the crag is never seen)
        for y in range(CASTLE_Y - 1, y0):
            if (x, y, z) not in CASTLE:
                r = rnd(x, y, z, 47)
                w.set((x, y, z), 'minecraft:stone' if r < 0.6 else 'minecraft:andesite' if r < 0.8 else
                      'minecraft:cobblestone')


def leaf_seen(p, d):
    """A camera can be above or below a leaf, or on the side of it that faces the camera region."""
    return d[1] != 0 or face_seen(p[0], p[2], d[0], d[2])


def set_bounds():
    return (TEMPLATE_AT, tuple(TEMPLATE_AT[i] + SIZE[i] - 1 for i in range(3)))


def back_woods(w):
    """The woods are a skin of leaves; every one a camera can see through gets a moss block one in (leaf_backing)."""
    LB.trim_crown(w, CROWN, CASTLE, leaf_seen)
    return LB.back_leaves(w, list(WOODS) + list(SCRUB), set_bounds(), leaf_seen, extra=CASTLE)


def build_world():
    w = World()
    return w, _build(w)


# ---------------------------------------------------------------- anchors
def _p(x, y, z):
    return [round(x, 4), round(y, 4), round(z, 4)]


def _yaw_vec(yaw):
    r = math.radians(yaw)
    return -math.sin(r), math.cos(r)


def _rows():
    rows = {'west': [], 'east': []}
    for side, sx, yaw in (('west', -4.0, -90.0), ('east', 4.0, 90.0)):
        fx, fz = _yaw_vec(yaw)
        rx, rz = -fz, fx                                  # the student's right hand side
        for i in range(10):
            sz = CZ - 9.9 + 2.2 * i
            bxp, bzp = sx + fx * 0.3 + rx * 0.65, sz + fz * 0.3 + rz * 0.65
            rows[side].append({'broom': _p(bxp, 0.0, bzp), 'broom_yaw': yaw,
                               'student': _p(sx, 0.0, sz), 'student_yaw': yaw})
    return rows


ROWS = _rows()
WIN_C = (BAY_X[1] + 0.5, BAY_FLOOR + 4.0, (BAY_Z[0] + BAY_Z[1] + 1) / 2.0)
CHASE_BOX = CHASE_BOX_XZ

ANCHORS = {
    'lawn_y': 0.0,
    'lawn_centre': _p(CX, 0.0, CZ),
    'broom_line': ROWS,
    'hooch_spot': {'pos': _p(0.0, 0.0, CZ - 13.5), 'yaw': 0.0},
    'viewer_spot': {'pos': ROWS['east'][4]['student'], 'yaw': ROWS['east'][4]['student_yaw'], 'row': 'east',
                    'index': 4},
    'neville_fall': {'pos': _p(-13.5, 0.0, 36.5), 'yaw': 60.0},
    'remembrall_drop': _p(-12.2, 0.0, 38.1),
    'hospital_exit': {'door': _p(-21.5, 0.0, (DOOR_Z[0] + DOOR_Z[1] + 1) / 2.0),
                      'inside': _p(-32.5, 0.0, (DOOR_Z[0] + DOOR_Z[1] + 1) / 2.0), 'yaw': 90.0},
    'chase_box': CHASE_BOX,
    'catch_spot': {'pos': _p(-5.0, 0.0, 12.0), 'yaw': -60.0},
    'window_catch': {'pos': _p(-14.0, 11.0, 15.0), 'clear': 1.5},
    'mcg_window': {'stand': _p(BAY_X[0] + 0.65, BAY_FLOOR + 1.5, WIN_C[2]), 'centre': _p(*WIN_C), 'yaw': -90.0},
    'castle_view': {'pos': _p(6.0, 1.5, 44.0), 'look': _p(0.0, 52.0, -150.0)},
    'castle_box': [list(CASTLE_AT), [CASTLE_AT[0] + 144, CASTLE_AT[1] + 108, CASTLE_AT[2] + 95]],
    'RADIUS': RADIUS,
}


# ---------------------------------------------------------------- checks
def occupied(g, p):
    """Whether point p (scene coordinates) lies inside a block's collision shape (the shapes this set uses)."""
    c = (int(math.floor(p[0])), int(math.floor(p[1])), int(math.floor(p[2])))
    s = g.get(c)
    b = bid(s)
    if b in FREE or b in ('minecraft:dandelion', 'minecraft:oxeye_daisy'):
        return False
    fx, fy, fz = p[0] - c[0], p[1] - c[1], p[2] - c[2]
    name, props = parse_state(s)
    if name.endswith('_carpet'):
        return fy < 1 / 16.0
    if name.endswith('_slab'):
        t = props.get('type', 'bottom')
        return t == 'double' or (fy < 0.5 if t == 'bottom' else fy >= 0.5)
    if name.endswith('_stairs'):
        top = props.get('half') == 'top'
        if (fy >= 0.5) == top:
            return True
        f = props.get('facing')
        return {'north': fz < 0.5, 'south': fz >= 0.5, 'east': fx >= 0.5, 'west': fx < 0.5}[f]
    if name.endswith('_pane'):
        if props.get('north') == 'true':
            return 7 / 16.0 <= fx <= 9 / 16.0
        return 7 / 16.0 <= fz <= 9 / 16.0
    if name == 'minecraft:dirt_path':
        return fy < 15 / 16.0
    if name in ('minecraft:lantern', 'minecraft:chain', 'minecraft:candle', 'minecraft:lightning_rod'):
        return 0.3 <= fx <= 0.7 and 0.3 <= fz <= 0.7
    return True


GROUND = {'minecraft:grass_block', 'minecraft:dirt', 'minecraft:coarse_dirt', 'minecraft:moss_block',
          'minecraft:dirt_path', 'minecraft:stone', 'minecraft:andesite', 'minecraft:cobblestone',
          'minecraft:mossy_cobblestone'}


def world_with_castle(g):
    """The whole set as SetBuilder places it: the template first, then the schematic over it."""
    out = {p: bid(s) for p, s in g.items()}
    out.update(CASTLE)
    return out


# ---------------------------------------------------------------- the edge of the world, seen from a camera
VOID_FOV = 70.0                        # vertical, degrees (Minecraft's fov is vertical)
VOID_ASPECT = 16.0 / 9.0
VOID_BOUND = 0.02                      # at most 2% of a frame may be empty space below the horizon
_SOLID = None


def solid_set(g=None):
    """The cells a ray stops on: every placed block of the set (template and castle) but the see-through plants.
    Built once from build_world() when g is None."""
    global _SOLID
    if g is None:
        if _SOLID is None:
            _SOLID = solid_set(build_world()[0].g)
        return _SOLID
    see_through = FREE | {'minecraft:dandelion', 'minecraft:oxeye_daisy'}
    return frozenset(p for p, b in world_with_castle(g).items() if b not in see_through)


def _ray_hits(solid, o, d, max_dist):
    """Voxel walk (Amanatides-Woo) from o along unit d; True when it enters a solid cell within max_dist."""
    x, y, z = int(math.floor(o[0])), int(math.floor(o[1])), int(math.floor(o[2]))
    steps, tmax, tdelta = [], [], []
    for k, c in enumerate((x, y, z)):
        if d[k] > 1e-12:
            steps.append(1); tmax.append((c + 1 - o[k]) / d[k]); tdelta.append(1.0 / d[k])
        elif d[k] < -1e-12:
            steps.append(-1); tmax.append((o[k] - c) / -d[k]); tdelta.append(-1.0 / d[k])
        else:
            steps.append(0); tmax.append(1e30); tdelta.append(1e30)
    sx, sy, sz = steps
    lo_y = TEMPLATE_AT[1] - 1
    t = 0.0
    while t <= max_dist:
        if (x, y, z) in solid:
            return True
        if y < lo_y and sy <= 0 or abs(x) > RADIUS + 2 or abs(z) > RADIUS + 2:
            return False                                # below or beyond everything the set holds
        if tmax[0] < tmax[1]:
            if tmax[0] < tmax[2]:
                t = tmax[0]; x += sx; tmax[0] += tdelta[0]
            else:
                t = tmax[2]; z += sz; tmax[2] += tdelta[2]
        elif tmax[1] < tmax[2]:
            t = tmax[1]; y += sy; tmax[1] += tdelta[1]
        else:
            t = tmax[2]; z += sz; tmax[2] += tdelta[2]
    return False


def void_fraction(cam, look, solid=None, fov=VOID_FOV, aspect=VOID_ASPECT, cols=48, rows=27, max_dist=420.0):
    """The share of a camera's frame that is empty space below the horizon: rays (a cols x rows grid over the frame,
    camera at `cam` looking at the point `look`, no roll) that point below level and hit no block of the set,
    template or castle. Task 10 asserts every camera key of the lesson and chase against VOID_BOUND with it.
    The sky dome draws above the horizon; below it, such a ray shows the bare haze where the set ends."""
    solid = solid_set() if solid is None else solid
    f = [look[k] - cam[k] for k in range(3)]
    n = math.sqrt(sum(v * v for v in f)) or 1.0
    f = [v / n for v in f]
    r = [-f[2], 0.0, f[0]]                             # right = f x up (y up), horizontal
    rn = math.hypot(r[0], r[2]) or 1.0
    r = [r[0] / rn, 0.0, r[2] / rn]
    u = [r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]]
    ty = math.tan(math.radians(fov) / 2)
    tx = ty * aspect
    void = 0
    for j in range(rows):
        sy = (1 - 2 * (j + 0.5) / rows) * ty
        for i in range(cols):
            sx = (2 * (i + 0.5) / cols - 1) * tx
            d = [f[k] + r[k] * sx + u[k] * sy for k in range(3)]
            dn = math.sqrt(sum(v * v for v in d))
            d = [v / dn for v in d]
            if d[1] >= 0:
                continue
            if not _ray_hits(solid, cam, d, max_dist):
                void += 1
    return void / float(cols * rows)


def _yaw_look(cam, yaw, pitch=0.0, dist=50.0):
    """The look point `dist` ahead of cam at Minecraft yaw/pitch (pitch positive looks down)."""
    fx, fz = _yaw_vec(yaw)
    c = math.cos(math.radians(pitch))
    return [cam[0] + fx * c * dist, cam[1] - math.sin(math.radians(pitch)) * dist, cam[2] + fz * c * dist]


def void_probe_cameras():
    """(name, cam, look) cameras round the lesson and the chase the set must hold up from: the review's views (the
    dive west at the tower, hovering east and south, the lawn to the north-west), and the chase box's corners and
    centre at its top looking out each of eight ways, level and 15 degrees down."""
    (x1, y1, z1), (x2, y2, z2) = CHASE_BOX
    cams = [('dive_west_at_tower', [25, 30, 30], [-31, 30, 16]), ('chase_over_tower', [20, 26, 40], [-10, 20, -60]),
            ('chase_at_tower', [10, 34, -10], [-31, 34, 16]),
            ('hover_east', [5, 28, 0], [60, 28, 0]), ('hover_south', [5, 28, 0], [5, 28, 60]),
            ('lawn_nw', [0, 1.6, 30], [-40, 1.6, -10]), ('mcg_view', [-22.35, 11.12, 16.0], [-5, 0, 12])]
    top = y2 - 1
    for name, p in (('ne', [x2, top, z1]), ('nw', [x1, top, z1]), ('se', [x2, top, z2]), ('sw', [x1, top, z2]),
                    ('mid', [(x1 + x2) / 2.0, (y1 + y2) / 2.0, (z1 + z2) / 2.0])):
        for yaw in range(0, 360, 45):
            for pitch in (0.0, 15.0):
                cams.append(('box_%s_y%d_p%d' % (name, yaw, pitch), p, _yaw_look(p, yaw, pitch)))
    return cams


def check(w, H=None):
    problems = []
    if H is None:
        H = {}
        for (x, y, z), st in w.g.items():
            if bid(st) in GROUND and y > H.get((x, z), -999):
                H[(x, z)] = y
    g = w.g
    A = ANCHORS
    full = dict(g)
    for p, b in CASTLE.items():
        full[p] = b

    def stand(name, p, height=1.9, half=0.3):
        if not occupied(full, (p[0], p[1] - 0.02, p[2])):
            problems.append('%s %s: nothing solid under the feet (%s)' % (name, p, full.get(
                (int(math.floor(p[0])), int(math.floor(p[1] - 0.02)), int(math.floor(p[2]))))))
        for dx in (-half, half):
            for dz in (-half, half):
                for dy in (0.05, height / 2, height - 0.05):
                    q = (p[0] + dx, p[1] + dy, p[2] + dz)
                    if occupied(full, q):
                        problems.append('%s %s: body point %s inside %s' % (name, p, q, full.get(
                            tuple(int(math.floor(v)) for v in q))))
                        return

    def grass_under(name, p):
        c = (int(math.floor(p[0])), int(math.floor(p[1] - 0.5)), int(math.floor(p[2])))
        if bid(full.get(c)) != 'minecraft:grass_block':
            problems.append('%s %s: not on grass (%s)' % (name, p, full.get(c)))

    for side in ('west', 'east'):
        for i, e in enumerate(A['broom_line'][side]):
            stand('broom_line.%s[%d].student' % (side, i), e['student'], 1.45, 0.25)
            grass_under('broom_line.%s[%d].student' % (side, i), e['student'])
            # the broom lies flat on the grass: its 1.6 length along its yaw, all on grass, nothing above
            fx, fz = _yaw_vec(e['broom_yaw'])
            for t in (-0.8, 0.0, 0.8):
                q = (e['broom'][0] + fx * t, 0.0, e['broom'][2] + fz * t)
                grass_under('broom_line.%s[%d].broom%+.1f' % (side, i, t), q)
                for dy in (0.05, 0.3, 0.6):
                    if occupied(full, (q[0], dy, q[2])):
                        problems.append('broom %s[%d] point %s blocked' % (side, i, q))
    stand('hooch_spot', A['hooch_spot']['pos'])
    grass_under('hooch_spot', A['hooch_spot']['pos'])
    stand('viewer_spot', A['viewer_spot']['pos'], 1.45, 0.25)
    if A['viewer_spot']['pos'] != A['broom_line']['east'][4]['student']:
        problems.append('viewer_spot is not its row entry')
    stand('neville_fall', A['neville_fall']['pos'], 1.45, 0.6)
    grass_under('neville_fall', A['neville_fall']['pos'])
    stand('remembrall_drop', A['remembrall_drop'], 0.3, 0.1)
    grass_under('remembrall_drop', A['remembrall_drop'])
    stand('hospital_exit.door', A['hospital_exit']['door'])
    stand('hospital_exit.inside', A['hospital_exit']['inside'])
    # the walk from the door into the vestibule is clear
    d0, d1 = A['hospital_exit']['door'], A['hospital_exit']['inside']
    for i in range(21):
        t = i / 20.0
        q = [d0[k] + (d1[k] - d0[k]) * t for k in range(3)]
        stand('hospital_exit walk %.2f' % t, q, 1.9, 0.3)
    stand('catch_spot', A['catch_spot']['pos'], 1.45, 0.6)
    grass_under('catch_spot', A['catch_spot']['pos'])
    stand('mcg_window.stand', A['mcg_window']['stand'], 1.9, 0.25)
    # the walk Hooch leads Neville on, from where he fell to the door (then into the vestibule, above)
    n0 = A['neville_fall']['pos']
    for i in range(41):
        t = i / 40.0
        q = [n0[k] + (d0[k] - n0[k]) * t for k in range(3)]
        stand('neville to the door %.3f' % t, q, 1.9, 0.6)
    # no dirt stripes on the glen and the valley: no dirt-sided ground block there shows a side face a camera can see
    # (their steps are moss), and the valley floor is one level
    dirty = []
    for (x, y, z), st in g.items():
        if bid(st) not in ('minecraft:dirt', 'minecraft:coarse_dirt', 'minecraft:grass_block') or \
                (x, z) not in H or (x, z) in HILLY:
            continue                                   # (the hills' own mixed sward may show earth banks)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nh = H.get((x + dx, z + dz))
            if nh is not None and nh < y and face_seen(x, z, dx, dz):     # a riser over lower ground
                dirty.append((x, y, z))
                break
    if dirty:
        problems.append('%d dirt-sided blocks with a seen side face, e.g. %s' % (len(dirty), dirty[:4]))
    uneven = []
    for x in range(-VALLEY_HALF + VALLEY_WANDER + 1, VALLEY_HALF - VALLEY_WANDER):
        for z in range(VALLEY_Z[0], PLATEAU[2] - 45):
            if (x, z) in CASTLE_COLS or open_dist(x, z) > 0 or CRAG_DIST.get((x, z), 99) <= 5:
                continue
            if bid(g.get((x, VALLEY_TOP, z))) not in ('minecraft:grass_block', 'minecraft:dirt_path',
                                                      'minecraft:coarse_dirt') or \
                    g.get((x, VALLEY_TOP + 1, z)) not in (None, 'minecraft:short_grass', 'minecraft:dandelion', 'minecraft:oxeye_daisy'):
                uneven.append((x, z))
    if uneven:
        problems.append('%d valley floor columns off the level, e.g. %s' % (len(uneven), uneven[:4]))
    # castle_view: a camera spot in the air, over ground
    cv = A['castle_view']['pos']
    if any(occupied(full, (cv[0] + dx, cv[1] + dy, cv[2] + dz)) for dx in (-0.3, 0.3) for dy in (-0.3, 0.3)
           for dz in (-0.3, 0.3)) or not occupied(full, (cv[0], -0.5, cv[2])):
        problems.append('castle_view.pos %s is not in the air over the lawn' % cv)
    # window_catch: an air pocket in front of her window at its height, seen from her eye through the glass
    wc, cl = A['window_catch']['pos'], A['window_catch']['clear']
    for dx in (-cl, 0, cl):
        for dy in (-cl, 0, cl):
            for dz in (-cl, 0, cl):
                if occupied(full, (wc[0] + dx, wc[1] + dy, wc[2] + dz)):
                    problems.append('window_catch %s: %s is not air' % (wc, (dx, dy, dz)))
    # chase_box: all air, template and castle
    (x1, y1, z1), (x2, y2, z2) = A['chase_box']
    if not (x2 - x1 >= 50 and y2 - y1 >= 30 and z2 - z1 >= 50):
        problems.append('chase_box smaller than 50 x 30 x 50: %s' % A['chase_box'])
    solid = [p for p in full if x1 <= p[0] < x2 and y1 <= p[1] < y2 and z1 <= p[2] < z2
             and full[p] not in FREE]
    if solid:
        problems.append('%d solid cells in chase_box, e.g. %s %s' % (len(solid), solid[0], full[solid[0]]))
    # the window glass lies between McGonagall's eyes and the lawn: rays from her eye to lawn points
    eye = (A['mcg_window']['stand'][0], A['mcg_window']['stand'][1] + 1.62, A['mcg_window']['stand'][2])
    targets = [A['catch_spot']['pos'], A['lawn_centre'], A['broom_line']['west'][0]['student'],
               [A['catch_spot']['pos'][0], 12.0, A['catch_spot']['pos'][2]], A['window_catch']['pos']]
    for tgt in targets:
        hit_glass, blocked = False, None
        n = int(math.dist(eye, tgt) / 0.02)
        for i in range(1, n):
            q = [eye[k] + (tgt[k] - eye[k]) * i / n for k in range(3)]
            c = tuple(int(math.floor(v)) for v in q)
            b = bid(full.get(c))
            if b == 'minecraft:glass_pane':
                if occupied(full, q):
                    hit_glass = True
                continue
            if occupied(full, q) and math.dist(q, tgt) > 0.3:
                blocked = (c, b)
                break
        if blocked or not hit_glass:
            problems.append('window ray to %s: glass %s, blocked by %s' % (tgt, hit_glass, blocked))
    # the window is a real glass pane at its centre, facing +X, with air behind it (her side)
    c = tuple(int(math.floor(v)) for v in A['mcg_window']['centre'])
    if bid(full.get(c)) != 'minecraft:glass_pane':
        problems.append('mcg_window.centre %s is %s' % (A['mcg_window']['centre'], full.get(c)))
    # nothing of the set beyond the radius, the template holds every cell
    for (x, y, z) in g:
        lx, ly, lz = x - TEMPLATE_AT[0], y - TEMPLATE_AT[1], z - TEMPLATE_AT[2]
        if not (0 <= lx < SIZE[0] and 0 <= ly < SIZE[1] and 0 <= lz < SIZE[2]):
            problems.append('cell %s outside the template' % ((x, y, z),))
            break
    beyond = [p for p in full if abs(p[0]) > RADIUS or abs(p[2]) > RADIUS]
    if beyond:
        problems.append('%d cells beyond the radius, e.g. %s' % (len(beyond), beyond[0]))
    # the castle stands on ground: every crag column at its base whose side faces the cameras (CAM_REGION) has
    # ground beside it at or above the base (the valley grass, or hillside burying the foot), never an open drop
    tops = {}
    for (x, y, z) in g:
        if y >= CASTLE_Y - 3:
            tops[(x, z)] = True
    floating = []
    for (x, z), y0 in sorted(CASTLE_COLS.items()):
        if y0 != CASTLE_Y or z < CASTLE_AT[2] + 30:
            continue
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, z + dz)
            if n in CASTLE_COLS or not face_seen(x, z, dx, dz):
                continue
            if n not in tops:
                floating.append(n)
    if floating:
        problems.append('%d open cells at the crag foot, e.g. %s' % (len(floating), floating[:4]))
    problems += LB.problems(g, 'flying_lawn', CASTLE, seen=leaf_seen)   # leaves are see-through: backed (leaf_backing)
    return problems


def local_grid(g):
    return {(x - TEMPLATE_AT[0], y - TEMPLATE_AT[1], z - TEMPLATE_AT[2]): s for (x, y, z), s in g.items()}


def void_problems(g):
    """The probe cameras' void fractions against VOID_BOUND; returns (problems, worst (fraction, name))."""
    solid = solid_set(g)
    worst, problems = (-1.0, ''), []
    for name, cam, look in void_probe_cameras():
        v = void_fraction(cam, look, solid)
        worst = max(worst, (v, name))
        if v > VOID_BOUND:
            problems.append('camera %s %s -> %s: %.3f of the frame is void (bound %.2f)' % (
                name, cam, look, v, VOID_BOUND))
    return problems, worst


def write_nbt():
    """Builds the set, runs every check and writes structure/flying_lawn.nbt; returns (palette, blocks, world)."""
    w, H = build_world()
    g = w.g
    problems = physics_problems(world_with_castle(g), RADIUS)
    assert not problems, problems
    problems = check(w, H)
    assert not problems, '\n'.join(problems[:40]) + ('\n... %d in all' % len(problems))
    problems, worst = void_problems(g)
    assert not problems, '\n'.join(problems[:20])
    print('  void: worst %.3f of a frame (%s), bound %.2f, %d probe cameras' % (
        worst[0], worst[1], VOID_BOUND, len(void_probe_cameras())))
    assert len(g) <= MAX_BLOCKS, 'flying_lawn has %d blocks, over %d' % (len(g), MAX_BLOCKS)
    assert len(g) + len(CASTLE) <= OPENING_CAP, 'the opening build is %d blocks' % (len(g) + len(CASTLE))
    n_pal, n_blocks = write_template(local_grid(g), SIZE, OUT)
    return n_pal, n_blocks, w


def main():
    n_pal, n_blocks, w = write_nbt()
    print('flying_lawn.nbt: size %s, %d blocks, %d palette states, %d bytes; TEMPLATE_AT %s RADIUS %d' % (
        SIZE, n_blocks, n_pal, os.path.getsize(OUT), TEMPLATE_AT, RADIUS))
    print('  with the Mini Hogwarts (%d): %d blocks at the opening (cap 100,000: %d left for fills)' % (
        len(CASTLE), n_blocks + len(CASTLE), 100000 - n_blocks - len(CASTLE)))
    counts = {}
    for s in w.g.values():
        counts[bid(s)] = counts.get(bid(s), 0) + 1
    print('  census: ' + ', '.join('%s %d' % (k.split(':')[1], v) for k, v in
                                    sorted(counts.items(), key=lambda kv: -kv[1])[:24]))
    for k, v in ANCHORS.items():
        if k == 'broom_line':
            for side in ('west', 'east'):
                print('  broom_line.%s: %s ... %s' % (side, v[side][0], v[side][-1]))
            continue
        print('  %s: %s' % (k, v))


if __name__ == '__main__':
    main()
