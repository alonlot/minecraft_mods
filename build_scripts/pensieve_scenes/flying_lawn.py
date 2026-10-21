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
    foot of the curtain wall (x -26) to x 21. Beyond it the grounds roll away over a convex brow: gently at first,
    then faster (the fall is 0.035 d + 0.0026 d^2 at a distance d past the plateau), so from anywhere on the lawn
    the far edge of the set sits below the brow and the eye runs out to open sky. South and east the land falls
    about 25 blocks to a ragged, tree-dotted rim. North it falls into a valley floor (top y VALLEY_TOP) that runs
    to the foot of the Mini Hogwarts' crag: the castle stands on ground, it never floats. One-layer ground (each
    column is filled down only to its lowest neighbour, so no slope shows a hole), grass with a little dirt and
    rock where the fall is steep, a worn dirt path from the plateau down toward the castle.
  - The flank (full scale, people 1.8 tall), on the west, in Hogwarts grey (stone bricks with cracked, mossy, cobble
    and plain stone blocks, moss gathering at the foot; ivy climbing the lawn faces in ragged patches):
      * the curtain range: a 2-thick wall, face x -26, from the tower (z 23) to the south turret (z 70): a chamfered
        plinth, a string course at y 6, stepped buttresses every 9, two-light mullioned windows (dark glass at
        y 8..11) under hood moulds with arrow slits below; a corbel table at y 16 carrying a crenellated parapet
        (merlons two wide, capped at y 20.5) in front of the walk at y 18; a low tiled roof behind (ridge y 22);
      * the gate tower (x -31..-24, z 57..67, crenellated at y 23..25) with the hospital-wing door in its base at
        z 61..63: a shouldered arch 3 wide and 5 high under a hood mould, lanterns on brackets either side, worn
        flags at the threshold, a dark stone vestibule 11 deep with a closed door at its back;
      * the corner tower (an octagon 16 across, x -39..-24, z 8..23): shell to y 36, a corbelled machicolation and
        crenellated parapet at y 37..40, a tiled spire to y 66; slit windows; and McGonagall's oriel on its east
        face: 6 wide, projecting 2 (x -23, -22) on corbels, its floor at y 8 with a step up to 9.5 (she stands at
        9.5), a stone sill flush with the step that rises to 10 under the glass, a 4 x 4 leaded window (clear
        glass panes) at y 10..13 with a shouldered head, side lights, a lead roof. Behind it her study: walls of
        bookshelves, a desk with lit candles, a red rug bordered green, a beamed ceiling at y 15, a lantern on a
        chain and light blocks, so the window is lit when seen from the lawn;
      * the south turret (round, r 4.2, to y 25, a tiled cone to y 37) closing the range at z 72.
  - The Mini Hogwarts (SCHEMATIC, rotation 90 so its long front faces south, over the lawn) on its crag at
    x -72..72, z -180..-85, its base at y CASTLE_Y (-6) on the valley grass. Rock fills the ragged foot of the crag
    shell on the seen side (no sky through it), and stone props the schematic's loose gravel.
  - The chase volume (chase_box) over the lawn between the tower and the castle is open sky.
  - Off the set's edges is the sky dome's haze: from the lawn the brow hides them; from high in the chase, look
    toward the castle, the tower or the lawn rather than outward over the south and east rims, or behind the
    range (x < -42), where the set simply ends.

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
from great_hall_set import state, bid, parse_state, write_template  # noqa: E402

OUT = os.path.join(STRUCTURES, 'flying_lawn.nbt')
RADIUS = 200
TIME_OF_DAY = 8000                     # a bright afternoon, the sun past noon toward the west
FOG_FAR = 260
MAX_BLOCKS = 60000

# lawn frame
CX, CZ = 0, 30                          # the lawn centre (middle of the aisle)
PLATEAU = (-27, 21, 2, 47)              # x0, x1, z0, z1 of the flat lesson ground (top y -1)
WALL_WALK = (-27, -12, 47, 69)          # and the level strip along the wall foot to the door
VALLEY_TOP = -7                        # grass top of the valley floor before the castle crag

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
BAY_FLOOR = 8                           # floor cells y 8, she stands at y 9
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


def _crag_dist():
    """Chebyshev distance (up to 10) from each cell near the crag to its nearest crag column."""
    d = {}
    frontier = list(CASTLE_COLS)
    for c in frontier:
        d[c] = 0
    for step in range(1, 11):
        nxt = []
        for (x, z) in frontier:
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    n = (x + dx, z + dz)
                    if n not in d:
                        d[n] = step
                        nxt.append(n)
        frontier = nxt
    return d


CRAG_DIST = _crag_dist()


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


def in_domain(x, z):
    """Whether the set has ground at column (x, z)."""
    if (x, z) in CASTLE_COLS or range_footprint(x, z):
        return False
    u, v = x - CX, z - CZ
    r = math.hypot(u, v)
    ang = math.atan2(v, u)
    edge = 92 + (vnoise(ang * 30.0, 0.0, 3.0, 5) - 0.5) * 16
    if x < -42 and 8 <= z <= WALL_Z[1]:
        return False                                    # behind the range (unseen)
    if r <= edge:
        return True
    # the valley to the crag: in front of and round the castle's south half
    if -84 <= x <= 84 and -142 <= z <= -20:
        return z >= -142 + (abs(x) > 74) * 24
    return False


def height(x, z):
    """Top cell y of the ground at (x, z)."""
    d = plateau_dist(x, z)
    if d == 0:
        return -1
    u, v = x - CX, z - CZ
    n = (fbm(x, z, 3) - 0.5) * 4.0 * min(1.0, d / 14.0)
    fall = 0.035 * d + 0.0026 * d * d
    # west, under the flank, the ground holds up at the wall foot a little longer
    if x < PLATEAU[0]:
        fall *= 0.8
    top = -1.0 - fall + n
    if z < PLATEAU[2]:                                 # north: into the valley, then up to the crag foot
        top = max(VALLEY_TOP + (vnoise(x, z, 11.0, 9) - 0.5) * 1.6, top)
        cd = CRAG_DIST.get((x, z))
        if cd is not None:                             # a little apron up to the crag
            top = max(top, VALLEY_TOP + max(0.0, 3.0 - cd * 0.6))
    return int(math.floor(top))


def build_ground(w):
    H = {}
    x0, z0 = TEMPLATE_AT[0], TEMPLATE_AT[2]
    for x in range(x0, x0 + SIZE[0]):
        for z in range(z0, z0 + SIZE[2]):
            if in_domain(x, z):
                H[(x, z)] = height(x, z)
    for (x, z), h in H.items():
        nb = [H.get((x + dx, z + dz)) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        lows = [v for v in nb if v is not None]
        rim = len(lows) < 4
        bottom = min(lows + [h]) if lows else h
        if rim:
            bottom = min(bottom, h - 3)                # a bank at the set's edge, not a sheet
        steep = h - bottom
        for y in range(bottom, h + 1):
            if y == h:
                if steep >= 4 and rnd(x, y, z, 41) < 0.35:
                    s = 'minecraft:coarse_dirt'
                else:
                    s = 'minecraft:grass_block'
            elif h - y <= 1 and steep < 4:
                s = 'minecraft:dirt'
            else:
                r = rnd(x, y, z, 42)
                s = ('minecraft:stone' if r < 0.45 else 'minecraft:andesite' if r < 0.7 else
                     'minecraft:cobblestone' if r < 0.85 else 'minecraft:dirt')
            w.set((x, y, z), state('grass_block', snowy='false') if s == 'minecraft:grass_block' else s)
    return H


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


ROOF = 'deepslate_tile'


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
        for x in WALL_X:
            footing(w, H, x, z, -1)
            for y in range(0, WALL_TOP + 1):
                w.set((x, y, z), masonry(x, y, z, 0))
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
                w.set((x, top, z), 'minecraft:deepslate_tiles')
                w.set((x, top + 1, z), slab('bottom', ROOF))
            elif d % 2 == 1:
                w.set((x, top, z), stairs('west' if x > -35 else 'east', m=ROOF))
            else:
                w.set((x, top, z), slab('top', ROOF))
        for y in range(4, WALL_TOP + 2):
            w.set((-41, y, z), masonry(-41, y, z, 0))
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
        w.set((-24, y, zc + 0), pane('z'))
        w.set((-25, y, zc), 'minecraft:black_concrete')
    w.set((-23, 10, zc), slab('top'))
    w.set((-23, 15, zc), stairs('west', 'top'))
    for z in range(zt0, zt1 + 1):
        w.set((-23, 7, z), stairs('west', 'top'))


def build_door(w, H):
    """The hospital-wing doorway in the gate tower's front: a pointed arch 3 wide, 5 high, into a dark vestibule."""
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
        footing(w, H, x, z, -1)
        for y in range(0, TOWER_TOP + 1):
            w.set((x, y, z), masonry(x, y, z, 0))
    # battered plinth (one ring out at y 0, -1) and string courses
    ring1 = [(x, z) for x, z in cells if oct_in(x, z, TOWER_HALF + 1, TOWER_CHAMFER + 1)
             and not oct_in(x, z, TOWER_HALF, TOWER_CHAMFER)]
    for x, z in ring1:
        if w.get((x, 0, z)) is None and not (x <= -27 and z >= WALL_Z[0]):
            footing(w, H, x, z, -1)
            w.set((x, 0, z), stairs(opposite(outward(x, z))))
        # machicolation: corbels at y 30 and the parapet ring at 31..33
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
        for y in (TOWER_TOP - 13,):
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
    # the spire: an octagonal cone of tiles from y 33 to 57
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
            if oct_in(x, z, half_up, ch_up) and oct_in(x, z, half - 1.5, ch - 1.5):
                continue                                        # hollow inside (two cells of skin)
            if not oct_in(x, z, half_up, ch_up):
                w.set((x, y, z), stairs(opposite(outward(x, z)), m=ROOF))
            else:
                w.set((x, y, z), 'minecraft:deepslate_tiles')
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
        w.set((bx0, 16, z), 'minecraft:deepslate_tiles')
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
            if d <= r and not (d <= r - 1.5 and d <= r_up):
                w.set((x, y, z), stairs(opposite(outward(x, z, TURRET_C)), m=ROOF) if d > r_up
                      else 'minecraft:deepslate_tiles')


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


TREES = [  # (x, z, height, crown, kind): framing oaks at the lawn's edge, a stand down the south-east slope
    (31, 20, 7, 4.5, 'oak'), (27, 58, 6, 4.0, 'oak'), (-10, 74, 6, 4.2, 'oak'),
    (44, 4, 6, 3.6, 'oak'), (58, 40, 6, 4.0, 'dark'), (52, 70, 6, 4.2, 'dark'), (66, 60, 5, 3.8, 'dark'),
    (30, 92, 6, 4.0, 'dark'), (12, 100, 5, 3.6, 'dark'), (70, 22, 5, 3.4, 'oak'), (40, 84, 5, 3.8, 'dark'),
    (-30, 96, 5, 3.6, 'dark'), (60, -20, 5, 3.6, 'oak'),
]


def build_trees(w, H):
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
        if r < 0.025:
            w.set((x, h + 1, z), 'minecraft:short_grass')
        elif r < 0.03:
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
CHASE_BOX = [[-20, 6, -40], [40, 40, 14]]

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


def world_with_castle(g):
    """The whole set as SetBuilder places it: the template first, then the schematic over it."""
    out = {p: bid(s) for p, s in g.items()}
    out.update(CASTLE)
    return out


def check(w):
    problems = []
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
               [A['catch_spot']['pos'][0], 12.0, A['catch_spot']['pos'][2]]]
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
    # the castle stands on ground: every crag column on the rim (lowest block at the castle base) has grass or
    # crag under or beside it, never an open drop to the void on the lawn's side
    floating = []
    for (x, z), y0 in CASTLE_COLS.items():
        if y0 != CASTLE_Y or z > -104 - 0 or z < CASTLE_AT[2] + 40:
            continue
        nb = [(x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)]
        for n in nb:
            if n in CASTLE_COLS:
                continue
            if bid(g.get((n[0], CASTLE_Y - 1, n[1]))) is None and not any(
                    g.get((n[0], yy, n[1])) for yy in range(CASTLE_Y - 3, CASTLE_Y + 3)):
                floating.append(n)
    if floating:
        problems.append('%d open cells at the crag foot, e.g. %s' % (len(floating), floating[:4]))
    return problems


def local_grid(g):
    return {(x - TEMPLATE_AT[0], y - TEMPLATE_AT[1], z - TEMPLATE_AT[2]): s for (x, y, z), s in g.items()}


def write_nbt():
    """Builds the set, runs every check and writes structure/flying_lawn.nbt; returns (palette, blocks)."""
    w, H = build_world()
    g = w.g
    problems = physics_problems(world_with_castle(g), RADIUS)
    assert not problems, problems
    problems = check(w)
    assert not problems, '\n'.join(problems[:40]) + ('\n... %d in all' % len(problems))
    assert len(g) <= MAX_BLOCKS, 'flying_lawn has %d blocks, over %d' % (len(g), MAX_BLOCKS)
    assert len(g) + len(CASTLE) <= 100000, 'the opening build is %d blocks' % (len(g) + len(CASTLE))
    return write_template(local_grid(g), SIZE, OUT)


def main():
    n_pal, n_blocks = write_nbt()
    w, H = build_world()
    print('flying_lawn.nbt: size %s, %d blocks, %d palette states, %d bytes; TEMPLATE_AT %s RADIUS %d' % (
        SIZE, n_blocks, n_pal, os.path.getsize(OUT), TEMPLATE_AT, RADIUS))
    print('  with the Mini Hogwarts (%d): %d blocks at the opening (cap 100,000)' % (len(CASTLE),
                                                                                    n_blocks + len(CASTLE)))
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
