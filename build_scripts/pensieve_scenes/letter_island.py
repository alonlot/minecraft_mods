"""Builds the Letter memory's storm set: structure/letter_island.nbt, the night sea with the bare black rock and its
ramshackle one-room hut, and the anchors letter_scene.py stages `letter_crossing` and `letter_hut` with.

    python -I build_scripts/pensieve_scenes/letter_island.py

Using the set from a scene (letter_scene.py):

    import letter_island as LI
    'radius': LI.RADIUS,
    'set': {'templates': [{'id': 'hogwartsmagic:letter_island', 'at': list(LI.TEMPLATE_AT)}],
            'time_of_day': LI.TIME_OF_DAY}
    rough_sea:  {'at': LI.ANCHORS['sea_center'], 'spread': LI.ANCHORS['sea_spread'], ...}
    rain:       the box LI.ANCHORS['rain_box'] ([[x1,y1,z1],[x2,y2,z2]], min and max corners)

The set
-------
  - The sea: 160 x 160 blocks (scene x, z -80..79), three layers of water (cells y -13..-11) on a deepslate bed
    (y -14), closed all round by a barrier rim, so physics_problems is empty. The `rough_sea` surface is drawn over
    it at the still-water level SEA_Y = -8.0 (its `at` y). The top water block is at.y - 3 (SKILL asks at least
    at.y - 2), so the block water's own surface (-10.11) stays under the deepest trough even at intensity 2
    (-9.2): the water blocks are only the deep body under the animated surface. Without a rough_sea event the
    block water reads as low tide.
  - The rock: an irregular black crag about 36 x 30 at the waterline, with broken cliffs 7-9 blocks high (buttresses
    and gullies, chamfered lips), a flat top round the hut (block layer y -1, so people stand at y 0), basalt crags
    on the windward north and west up to y 6, and a skirt of wave-washed boulders round the foot. One dark tonal
    family: cobbled deepslate and deepslate on the tops, columnar basalt and deepslate on the faces (by column),
    wet blackstone in the band the waves wash (its top ragged by column). Seven sea stacks stand off it.
  - The landing: six worn polished-deepslate steps cut down a rock spur on the south-east from the porch to a flat
    ledge one block above the still water (stand y -7, dry under the crests to intensity 1.5), a rickety dark oak
    handrail on the seaward kerb, a mooring post and a stray board; a clear channel for the boat runs in from the
    south-east (rock cut to the bed under every hull pose).
  - The hut: one room, 8 x 7 inside (x -4..3, z -3..3), walls of weathered dark oak boards (planks, stripped and
    barked wood by board, a few spruce) on a
    deepslate footing with log posts and nailed-on patch boards, a shallow sagging slab roof (ridge 6.5 at the
    gables, 6.0 in the middle, ragged eaves with missing boards) on two tie beams, a rubble chimney stack on the
    west wall with a smoking campfire in its flue, and:
      * the fireplace in the west wall: a recessed firebox with a burnt-down log fire on glowing embers (magma),
        a polished blackstone hearth;
      * the old faded-red sofa under the north wall where Dudley sleeps (1.5 deep, seat top 0.5);
      * a brown blanket on the floor in front of the hearth where the boy lies;
      * the Dursleys' corner (north-east): a low bed, a barrel with candles, a chair under the window;
      * the one window (east wall), lit from inside so it glows from the sea, with a sill and one shutter;
      * barrels and a crate (south-east), a log pile (south-west), a cobweb high in the north-east corner.
  - The doorway (south gable, facing the landing): 2 wide x 2.5 high under a timber lintel, so Hagrid at 1.5
    (2.81 tall) must stoop; the door is two memory_door leaves (scale 1: 1 x 3 x 0.2 each) hung on the wall's
    inner face, a plain one and a mirrored one meeting in the middle. One 1:3 leaf cannot be both wide enough
    for Hagrid's shoulders (1.41 at 1.5, more with arm sway) and low: a single leaf 1.65 wide would be 4.95 tall.

ANCHORS format
--------------
Every coordinate is a SCENE coordinate (template local + TEMPLATE_AT), the frame great_hall_set.py, diagon_set.py
and privet_drive.py use. The hut floor and the rock top are a block layer at scene y -1, so anyone in the hut, on
the porch or on the rock top stands at y 0; the landing ledge is lower (stand at y -7). The hut's door faces south
(+z). yaw is Minecraft's: 0 faces +Z (south), 90 -X (west), 180 -Z (north), -90 +X (east). Seats are
[x, seat_top_y, z, yaw] as in great_hall_set.py (root at human_motion.seated_root_y(seat_top, scale), `seated`).

  sea_center            [x, y, z]  the rough_sea `at`: the still-water surface centre (y = SEA_Y)
  sea_spread            [hx, 0, hz] the rough_sea `spread`: the whole sea to the barrier rim
  boat_start            [x, y, z]  the boat's centre at the still surface, out in the storm (south-east)
  boat_path             [[x, y, z], ...] the crossing, boat centres at the still surface, start to landing; every
                                   segment is clear (hull 4.4 x 1.9, heaving +-0.6) of rock from the water up
  boat_landing          [x, y, z]  the last boat_path point: the bow (2.0 ahead, heading north) at the ledge
  boat_landing_yaw      float      the boat's heading there (180: the bow points -Z, at the ledge)
  boat_start_yaw        float      the boat's heading at the start (along the first segment)
  landing_ledge         [x, y, z]  a feet spot on the ledge by the bow (stand y -7)
  landing_steps         [[x, y, z], ...] feet points from the ledge up the cut steps to the porch (on each stair's
                                   upper half), ledge first
  porch                 {pos, yaw} the spot outside the door facing it (a person at 1.0)
  hagrid_outside        {pos, yaw} Hagrid's mark outside the door for the pounding (scale 1.5), facing north
  hagrid_inside         {pos, yaw} his mark inside once he is through and upright, standing on the fallen leaves
                                   (y 0.2: the leaves' top face), facing north into the room
  hagrid_entry          {from, to, stoop_deg, head_deg} his walk through the doorway (feet, x 0): stooped forward
                                   by stoop_deg at the hips, head tipped back head_deg from the torso line, the body
                                   clears the doorway with the margins the checks print; upright he hits the lintel
  hut_door_hinge        {pos, yaw, scale, mirrored: false, fallen_pitch, ajar_inward_yaw}  the plain memory_door
                                   leaf: hinge (bottom corner, near face) at the east jamb, closed yaw 180 (leaf
                                   spans -X to the middle, thickness toward -Z, into the hut). `look` pitch 0 -> -90
                                   lays it flat INTO the hut; ajar_inward_yaw is a yaw a few degrees off closed that
                                   moves its free edge inward (for the shudder under the pounding; the other way
                                   sinks it into the wall)
  hut_door_hinge_mirrored the same for the memory_door_mirrored leaf at the west jamb (spans +X to the middle)
  hut_door_scale        float      the leaf scale (1.0: 1 wide, 3 tall, 0.2 thick each)
  hut_door_fallen_box   [[x1,y1,z1],[x2,y2,z2]] where the two flat leaves lie (floor, inside, clear)
  hut_doorway           {x: [x0, x1], y: [0, 2.5], z: [4, 5]} the opening
  hut_interior_box      [[x1,y1,z1],[x2,y2,z2]] the room's air (for keeping rain and spray out of it)
  boy_floor             {feet, head, yaw, surface_y} the boy lying on the blanket before the hearth; yaw is the
                                   direction from feet to head (180: head toward -Z, the fire end)
  dudley_sofa           {center, head, feet, yaw, seat_top, seat}  Dudley lying along the sofa (head west,
                                   yaw 90 from feet to head); `seat` is a seated mark facing south if he sits up
  vernon_corner         {pos, yaw, seat} Vernon's standing mark by his chair, facing the door; seat = the chair
  petunia_corner        {pos, yaw, bed_seat} Petunia's standing mark by the bed, facing the door
  fireplace             {embers, mouth, yaw, flue_top} embers: top centre of the burnt-down fire (in the firebox);
                                   mouth: the opening's centre on the room face; yaw faces out of it (-90, +X)
  window                {center, inside, outside} the glass's centre, a spot 1 in front of it inside / outside
  rain_box              [[x1,y1,z1],[x2,y2,z2]] the crossing and the islet (the hut is inside it: see
                                   hut_interior_box)
  RADIUS                int
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import physics_problems, FREE, STRUCTURES  # noqa: E402
from great_hall_set import state, bid, parse_state, write_template  # noqa: E402

OUT = os.path.join(STRUCTURES, 'letter_island.nbt')
TIME_OF_DAY = 18000
SEA_HALF = 80                          # sea cells x, z in [-80, 79]; the outer ring is barrier
RADIUS = 80
TEMPLATE_AT = (-80, -14, -80)
SIZE = (160, 23, 160)                  # scene y -14..8

SEA_Y = -8.0                           # the rough_sea still surface (its `at` y)
WATER_TOP = -11                        # top water cell, at.y - 3: its block surface (-10.11) stays under the
WATER_BOTTOM = -13                     # deepest trough up to intensity 2 (-9.2); SKILL asks at least at.y - 2
BED_Y = -14

# hut (scene cells)
HX0, HX1 = -5, 4                       # wall cells x (west, east)
HZ0, HZ1 = -4, 4                       # wall cells z (north, south / door gable)
IX0, IX1, IZ0, IZ1 = -4, 3, -3, 3      # interior cells
DOOR_X = (-1, 0)                       # door opening cells
LINTEL_Y = 2.5                         # underside of the lintel (top slab in cells y 2)
LEAF_SCALE = 1.0
LEAF_GAP = 0.02                        # the leaves' back face stands this far off the wall's inner face
HINGE_Z = HZ1 - LEAF_GAP               # 3.98
FALLEN_Z = (HINGE_Z - 0.2 * LEAF_SCALE - 3 * LEAF_SCALE, HINGE_Z - 0.2 * LEAF_SCALE)

# landing
STEPS = [(9, -2), (10, -3), (11, -4), (12, -5), (13, -6), (14, -7)]   # (z, stair cell y), x 5..6, rising north
STEP_X = (5, 6)
LEDGE = (4, 8, 15, 16, -8)             # x0, x1, z0, z1, top cell y (stand at -7, one above the still water)

BOAT_PATH = [(30.5, 64.6), (27.6, 57.1), (23.9, 48.9), (19.6, 40.7), (15.4, 33.3), (11.4, 27.7), (8.2, 24.1),
             (6.2, 21.7), (6.0, 19.6)]
HULL_HALF_LEN, HULL_HALF_W = 2.2, 0.95

# (x, z, radius, top cell y): basalt crags on the cliff band and sea stacks off the islet
SPIRES = [(-8, -10, 3.4, 6), (-3, -12, 2.6, 3), (4, -11, 2.4, 2), (-13, -4, 3.0, 4), (-12, 3, 2.0, 1),
          (13, -6, 2.8, 4), (13, 3, 2.0, 0), (-9, 9, 2.2, 0)]
SKERRIES = [(-24, -15, 2.8, -2), (-19, 17, 2.0, -5), (21, -13, 2.4, -1), (26, 6, 1.6, -5), (-7, 26, 1.7, -6),
            (-28, 2, 1.5, -6), (33, 24, 1.3, -7)]


# ---------------------------------------------------------------- noise
def _hash(ix, iz, seed):
    h = (ix * 374761393 + iz * 668265263 + seed * 982451653) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFFFF) / float(0x1000000)


def _smooth(t):
    return t * t * (3 - 2 * t)


def vnoise(x, z, scale, seed):
    """Value noise in [0, 1): bilinear between hashed lattice values, smoothstepped."""
    fx, fz = x / scale, z / scale
    ix, iz = math.floor(fx), math.floor(fz)
    tx, tz = _smooth(fx - ix), _smooth(fz - iz)
    a = _hash(ix, iz, seed)
    b = _hash(ix + 1, iz, seed)
    c = _hash(ix, iz + 1, seed)
    d = _hash(ix + 1, iz + 1, seed)
    return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz


def fbm(x, z, seed):
    return 0.6 * vnoise(x, z, 6.0, seed) + 0.3 * vnoise(x, z, 2.7, seed + 7) + 0.1 * vnoise(x, z, 1.3, seed + 13)


def rnd01(x, y, z, seed):
    return _hash(x * 31 + y * 7919, z * 17 + y * 104729, seed)


# ---------------------------------------------------------------- the rock's height field
def _seg_dist(px, pz, ax, az, bx, bz):
    dx, dz = bx - ax, bz - az
    L = dx * dx + dz * dz
    t = 0.0 if L == 0 else max(0.0, min(1.0, ((px - ax) * dx + (pz - az) * dz) / L))
    qx, qz = ax + t * dx, az + t * dz
    return math.hypot(px - qx, pz - qz)


def path_dist(px, pz):
    return min(_seg_dist(px, pz, *BOAT_PATH[i], *BOAT_PATH[i + 1]) for i in range(len(BOAT_PATH) - 1))


def boat_poses(step=0.25):
    """(cx, cz, heading unit dx, dz) along the path, sampled every `step` blocks."""
    out = []
    for i in range(len(BOAT_PATH) - 1):
        (ax, az), (bx, bz) = BOAT_PATH[i], BOAT_PATH[i + 1]
        L = math.hypot(bx - ax, bz - az)
        dx, dz = (bx - ax) / L, (bz - az) / L
        n = max(1, int(L / step))
        for k in range(n + (1 if i == len(BOAT_PATH) - 2 else 0)):
            t = k / n
            out.append((ax + t * (bx - ax), az + t * (bz - az), dx, dz))
    return out


def in_hull(px, pz, pose, grow=0.0):
    cx, cz, dx, dz = pose
    rx, rz = px - cx, pz - cz
    along = rx * dx + rz * dz
    side = -rx * dz + rz * dx
    return abs(along) <= HULL_HALF_LEN + grow and abs(side) <= HULL_HALF_W + grow


def _hull_cells():
    cells = set()
    for pose in boat_poses(0.5):
        cx, cz = pose[0], pose[1]
        for x in range(int(cx) - 4, int(cx) + 5):
            for z in range(int(cz) - 4, int(cz) + 5):
                # any part of the cell within the hull grown by 0.75 (a cell is 1 wide; keep the channel generous)
                if in_hull(x + 0.5, z + 0.5, pose, 1.45):
                    cells.add((x, z))
    return cells


HULL_CELLS = _hull_cells()


def flat_zone(x, z):
    """Cells forced to the plateau top (y -1): hut, chimney, eaves, porch and the walk to the steps."""
    if -8 <= x <= 7 and -6 <= z <= 6:
        return True
    if -3 <= x <= 7 and 6 <= z <= 8:
        return True
    return False


def _crag(cx, cz, x0, z0, r, top, seed):
    """A cluster of basalt columns: a jagged cone, each column its own height."""
    d = math.hypot(cx - x0, cz - z0) + (vnoise(cx, cz, 1.6, seed) - 0.5) * 1.3
    if d >= r:
        return None
    jitter = (_hash(int(math.floor(cx)), int(math.floor(cz)), seed + 1) - 0.5) * 2.6
    return top - (d / r) ** 1.3 * (top + 4.0) + jitter


def height(x, z):
    """Top rock cell y of column (x, z); BED_Y for open sea floor (bed only)."""
    if flat_zone(x, z):
        return -1
    if (x, z) in HULL_CELLS:
        return BED_Y
    cx, cz = x + 0.5, z + 0.5
    n = fbm(cx, cz, 11)
    r = math.hypot(cx / 18.0, (cz - 0.5) / 15.0) + (n - 0.5) * 0.30
    if r < 0.6:
        h = -2 if (n < 0.2 and r > 0.52) else -1
        h += max(0.0, vnoise(cx, cz, 4.0, 27) - 0.5) * 9.0 * min(1.0, r / 0.35)    # knolls of bare rock
    elif r < 0.97:
        t = (r - 0.6) / 0.37
        h = -1 - 8.6 * t ** 0.8
        h += (vnoise(cx, cz, 3.5, 25) - 0.5) * 5.0 * math.sin(math.pi * t)      # buttresses and gullies
        h += (_hash(x, z, 24) - 0.5) * 1.4                                       # column-to-column ragged edge
    elif r < 1.12:
        h = SEA_Y - 1.5 + (vnoise(cx, cz, 2.2, 29) - 0.55) * 5.0
    else:
        h = SEA_Y - 3 - (r - 1.12) * 25
    # the spur the landing steps are cut through: rock walls a little above the steps on both sides, falling
    # away east and west, down to the ledge
    if 1 <= x <= 11 and 8 <= z <= 17:
        step_y = -1 - (z - 8) if z <= STEPS[-1][0] else LEDGE[4]
        if cx < 6.0:                                    # the landward (west) wall stands over the steps
            spur = step_y + 1.6 - max(0.0, 5.0 - cx) * 1.1 + (_hash(x, z, 26) - 0.5) * 1.2
        else:                                           # seaward it falls away, so the steps show from the boat
            spur = step_y - 0.6 - max(0.0, cx - 7.0) * 1.3 + (_hash(x, z, 26) - 0.5) * 0.8
        h = max(h, spur)
    for i, (sx, sz, sr, top) in enumerate(SPIRES):
        c = _crag(cx, cz, sx, sz, sr, top, 31 + i)
        if c is not None:
            h = max(h, c)
    for i, (sx, sz, sr, top) in enumerate(SKERRIES):
        c = _crag(cx, cz, sx, sz, sr * 1.3, top, 61 + i)
        if c is not None:
            h = max(h, c)
    return max(BED_Y, min(8, int(math.floor(h))))


# ---------------------------------------------------------------- materials
def rock_state(x, y, z, exposed_up, exposed_side):
    """Dark rock in one tonal family (deepslate, cobbled deepslate, basalt: all within a few luma of each other).
    Cliff faces take their material by column, so columnar basalt reads as jointed columns; the band the waves
    wash is wet blackstone, its top edge ragged by column."""
    col = vnoise(x + 0.5, z + 0.5, 2.4, 51)
    patch = vnoise(x + 0.5, z + 0.5, 4.0, 53)
    r = rnd01(x, y, z, 52)
    wet_top = SEA_Y + 0.5 + _hash(x, z, 54) * 1.8
    if y < wet_top:
        return 'minecraft:blackstone' if r < 0.85 else state('basalt', axis='y')
    if exposed_up:
        if patch < 0.5:
            return 'minecraft:cobbled_deepslate'
        return state('deepslate', axis='y') if r < 0.7 else 'minecraft:cobbled_deepslate'
    if exposed_side:
        if y >= -2:
            return state('basalt', axis='y') if col < 0.72 else state('deepslate', axis='y')
        if col < 0.5:
            return state('basalt', axis='y')
        if col < 0.78:
            return state('deepslate', axis='y')
        return 'minecraft:cobbled_deepslate'
    return 'minecraft:deepslate'


# ---------------------------------------------------------------- building
class World:
    def __init__(self, g=None):
        self.g = {} if g is None else g

    def set(self, p, s):
        if s is None or s == 'minecraft:air':
            self.g.pop(p, None)
        else:
            self.g[p] = s

    def get(self, p):
        return self.g.get(p)

    def fill(self, x0, y0, z0, x1, y1, z1, s):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set((x, y, z), s(x, y, z) if callable(s) else s)

    def clear(self, x0, y0, z0, x1, y1, z1):
        self.fill(x0, y0, z0, x1, y1, z1, None)


def build_sea_and_rock(w):
    H = {}
    for x in range(-SEA_HALF, SEA_HALF):
        for z in range(-SEA_HALF, SEA_HALF):
            H[(x, z)] = height(x, z)
    # carve the steps and the ledge into the height field
    for x in range(LEDGE[0], LEDGE[1] + 1):
        for z in range(LEDGE[2], LEDGE[3] + 1):
            H[(x, z)] = LEDGE[4]
    for z, y in STEPS:
        for x in STEP_X:
            H[(x, z)] = y
        H[(STEP_X[1] + 1, z)] = y                       # the seaward kerb the handrail stands on
    rim = SEA_HALF
    for (x, z), h in H.items():
        edge = x in (-rim, rim - 1) or z in (-rim, rim - 1)
        w.set((x, BED_Y, z), 'minecraft:deepslate')
        for y in range(BED_Y + 1, WATER_TOP + 1):
            if y <= h:
                continue
            w.set((x, y, z), 'minecraft:barrier' if edge else 'minecraft:water')
    # rock columns with exposure-aware materials
    def hh(x, z):
        return H.get((x, z), BED_Y)
    for (x, z), h in H.items():
        if h <= BED_Y:
            continue
        for y in range(BED_Y + 1, h + 1):
            up = y == h
            side = any(hh(x + dx, z + dz) < y for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            w.set((x, y, z), rock_state(x, y, z, up, side))
    # slab caps where a column steps down one to its neighbour (softens the terraces), outside the walk zones
    for (x, z), h in H.items():
        if h < SEA_Y or flat_zone(x, z) or (x, z) in HULL_CELLS:
            continue
        if any(LEDGE[0] - 1 <= x <= LEDGE[1] + 1 and LEDGE[2] - 1 <= z <= LEDGE[3] + 1 for _ in (0,)):
            continue
        if x in STEP_X and 8 <= z <= LEDGE[2]:
            continue
        higher = sum(1 for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if hh(x + dx, z + dz) == h + 1)
        if higher and rnd01(x, h, z, 61) < 0.55:
            s = 'cobbled_deepslate_slab' if rnd01(x, h, z, 62) < 0.7 or h < SEA_Y + 2 else 'polished_deepslate_slab'
            if h < SEA_Y + 2:
                s = 'blackstone_slab'
            w.set((x, h + 1, z), state(s, type='bottom', waterlogged='false'))
    # chamfered edges: the top block of a column standing 2 or more over a neighbour becomes a stair whose low
    # side faces the drop, so crag tops and cliff lips are not all square boxes
    for (x, z), h in H.items():
        if h < SEA_Y + 1 or flat_zone(x, z) or (x, z) in HULL_CELLS or (x in STEP_X and 8 <= z <= LEDGE[3] + 1):
            continue
        if LEDGE[0] - 1 <= x <= LEDGE[1] + 1 and LEDGE[2] - 1 <= z <= LEDGE[3] + 1:
            continue
        drops = [(hh(x + dx, z + dz), f) for dx, dz, f in ((1, 0, 'west'), (-1, 0, 'east'), (0, 1, 'north'),
                                                           (0, -1, 'south'))]
        low, facing = min(drops)
        highs = [d for d in drops if d[0] >= h]
        if h - low >= 2 and len(highs) <= 2 and rnd01(x, h, z, 63) < 0.6:
            w.set((x, h, z), state('cobbled_deepslate_stairs', facing=facing, half='bottom', shape='straight',
                                   waterlogged='false'))
    return H


def build_landing(w, H):
    for z, y in STEPS:
        for x in STEP_X:
            w.set((x, y, z), state('polished_deepslate_stairs', facing='north', half='bottom', shape='straight',
                                   waterlogged='false'))
            w.clear(x, y + 1, z, x, y + 4, z)
        # a rickety handrail on the seaward kerb: dark oak posts, every other one missing near the bottom
        kx = STEP_X[1] + 1
        w.set((kx, y, z), 'minecraft:cobbled_deepslate')
        w.clear(kx, y + 1, z, kx, y + 3, z)
        if z <= 12 or z % 2 == 0:
            w.set((kx, y + 1, z), state('dark_oak_fence', north='false', south='false', east='false', west='false',
                                        waterlogged='false'))
    # the walk from the porch to the top step: keep it clear
    w.clear(1, 0, 6, 7, 3, 8)
    # ledge: flat dark flags with a mooring post and a stray board
    for x in range(LEDGE[0], LEDGE[1] + 1):
        for z in range(LEDGE[2], LEDGE[3] + 1):
            w.set((x, LEDGE[4], z), 'minecraft:polished_blackstone' if rnd01(x, 0, z, 81) < 0.3
                  else 'minecraft:cobbled_deepslate')
            w.clear(x, LEDGE[4] + 1, z, x, LEDGE[4] + 4, z)
    w.set((8, -7, 15), state('stripped_dark_oak_log', axis='y'))
    w.set((8, -6, 15), state('dark_oak_fence', north='false', south='false', east='false', west='false',
                             waterlogged='false'))
    w.set((4, -7, 16), state('spruce_trapdoor', facing='east', half='bottom', open='false', powered='false',
                             waterlogged='false'))


def plank(x, y, z):
    """Weathered boards: whole vertical boards of one wood (by wall position), mostly dark oak."""
    r = _hash(x * 3 + 1, z * 5 + 2, 91)
    if r < 0.6:
        return 'minecraft:dark_oak_planks'
    if r < 0.8:
        return state('stripped_dark_oak_wood', axis='y')
    if r < 0.93:
        return state('dark_oak_wood', axis='y')
    return 'minecraft:spruce_planks'


def roof_top(x, z):
    """Roof surface height at cell column (x, z): shallow pitch from the eaves to the ridge, sagging mid-length."""
    d = abs(x + 0.5)
    top = 6.5 - 0.5 * (d - 0.5)
    if d <= 1.5 and abs(z) <= 1:
        top -= 0.5
    if d <= 2.5 and z == 0:
        top -= 0.0 if d <= 1.5 else 0.5
    return top


def roof_cell(top):
    """(cell y, slab type) for a slab roof whose top surface is `top` (a multiple of 0.5)."""
    if abs(top - round(top)) < 1e-6:
        return int(round(top)) - 1, 'top'
    return int(math.floor(top)), 'bottom'


def build_hut(w):
    # footing and floor
    for x in range(HX0, HX1 + 1):
        for z in range(HZ0, HZ1 + 1):
            interior = IX0 <= x <= IX1 and IZ0 <= z <= IZ1
            if interior:
                r = _hash(x, 7, 92)                       # floorboards run north-south, one wood per board
                fl = 'minecraft:dark_oak_planks' if r < 0.72 else state('stripped_dark_oak_wood', axis='z')
                w.set((x, -1, z), fl)
            else:
                w.set((x, -1, z), 'minecraft:cobbled_deepslate')
                w.set((x, 0, z), 'minecraft:cobbled_deepslate' if rnd01(x, 0, z, 93) < 0.7
                      else 'minecraft:polished_blackstone_bricks')
                for y in range(1, 4):
                    w.set((x, y, z), plank(x, y, z))
    # posts at the corners and along the long walls
    for x, z in ((HX0, HZ0), (HX1, HZ0), (HX0, HZ1), (HX1, HZ1), (HX0, -2), (HX0, 2), (HX1, -2), (HX1, 2)):
        for y in range(0, 4):
            w.set((x, y, z), state('dark_oak_log', axis='y'))
    # gables (north and south) up under the roof
    for z in (HZ0, HZ1):
        for x in range(HX0, HX1 + 1):
            cy, typ = roof_cell(roof_top(x, z))
            for y in range(4, cy):
                w.set((x, y, z), plank(x, y, z))
    # roof slabs over x -6..5, z -5..5; full blocks where a top slab sits on a gable (closes the gable)
    for x in range(HX0 - 1, HX1 + 2):
        for z in range(HZ0 - 1, HZ1 + 2):
            cy, typ = roof_cell(roof_top(x, z))
            r = rnd01(x, 0, z, 94)
            wood = 'spruce' if r < 0.025 else 'dark_oak'
            edge = x in (HX0 - 1, HX1 + 1) or z in (HZ0 - 1, HZ1 + 1)
            if edge and r > 0.86 and not (x == HX0 - 1 and -1 <= z <= 1):
                continue                                        # a missing board at the ragged eaves
            if z in (HZ0, HZ1) and HX0 <= x <= HX1 and typ == 'top':
                w.set((x, cy, z), 'minecraft:%s_planks' % wood)
            else:
                w.set((x, cy, z), state('%s_slab' % wood, type=typ, waterlogged='false'))
    # tie beams under the roof
    for z in (-2, 2):
        for x in range(IX0 + 1, IX1):
            w.set((x, 4, z), state('stripped_dark_oak_log', axis='x'))
    # the doorway: posts, lintel (top slab: opening 2.5 high), beam over it
    for y in range(0, 3):
        w.set((DOOR_X[0] - 1, y, HZ1), state('stripped_dark_oak_log', axis='y'))
        w.set((DOOR_X[1] + 1, y, HZ1), state('stripped_dark_oak_log', axis='y'))
    for x in DOOR_X:
        w.clear(x, 0, HZ1, x, 1, HZ1)
        w.set((x, 2, HZ1), state('dark_oak_slab', type='top', waterlogged='false'))
        w.set((x, -1, HZ1), 'minecraft:dark_oak_planks')                       # threshold board
    for x in range(DOOR_X[0] - 1, DOOR_X[1] + 2):
        w.set((x, 3, HZ1), state('stripped_dark_oak_log', axis='x'))
    # porch flags and a doorstep
    for x in range(-2, 2):
        for z in (5, 6):
            w.set((x, -1, z), 'minecraft:polished_deepslate' if rnd01(x, 0, z, 95) < 0.5
                  else 'minecraft:cobbled_deepslate')
    # the window (east wall, z 0, y 1): pane, frame, sill, one shutter
    w.set((HX1, 1, 0), state('glass_pane', north='true', south='true', east='false', west='false',
                             waterlogged='false'))
    w.set((HX1, 2, 0), state('stripped_dark_oak_log', axis='z'))
    w.set((HX1 + 1, 0, 0), state('dark_oak_trapdoor', facing='west', half='top', open='false', powered='false',
                                 waterlogged='false'))
    w.set((HX1 + 1, 1, 1), state('spruce_trapdoor', facing='east', half='bottom', open='true', powered='false',
                                 waterlogged='false'))
    # board patches nailed over the outside of the walls (open trapdoors flat against the face)
    for p, f in (((HX1 + 1, 2, -2 + 1), 'east'), ((HX1 + 1, 1, 3), 'east'), ((HX0 - 1, 2, 3), 'west'),
                 ((2, 1, HZ0 - 1), 'north'), ((-3, 2, HZ0 - 1), 'north'), ((3, 2, HZ1 + 1), 'south')):
        w.set(p, state('spruce_trapdoor', facing=f, half='bottom',
                       open='true', powered='false', waterlogged='false'))
    build_fireplace(w)
    build_furniture(w)


def stone(x, y, z):
    """Rough rubble masonry from the island's own rock."""
    r = rnd01(x, y, z, 97)
    if r < 0.6:
        return 'minecraft:cobbled_deepslate'
    if r < 0.85:
        return 'minecraft:deepslate_bricks'
    return 'minecraft:cracked_deepslate_bricks'


def build_fireplace(w):
    # outer stack west of the wall (x -6), the wall line (x -5), the breast in the room (x -4); z -1..1
    for z in (-1, 0, 1):
        for y in range(-1, 4):
            w.set((-6, y, z), stone(-6, y, z))
        for y in range(0, 4):
            w.set((-5, y, z), stone(-5, y, z))
            w.set((-4, y, z), stone(-4, y, z))
        # the stack through and above the roof (x -6..-4), y 4..7
        for x in (-6, -5, -4):
            for y in range(4, 7):
                w.set((x, y, z), stone(x, y, z))
    # firebox: opening (-4, 0, 0), box (-5, 0, 0) with a burnt-down log fire on embers; flue (-5, 1..7, 0)
    w.clear(-4, 0, 0, -4, 0, 0)
    w.set((-4, 0, 0), state('light', level='8', waterlogged='false'))
    w.set((-5, 0, 0), state('campfire', facing='east', lit='false', signal_fire='false', waterlogged='false'))
    w.set((-5, -1, 0), 'minecraft:magma_block')
    w.set((-4, -1, 0), 'minecraft:magma_block')
    for y in range(1, 7):
        w.clear(-5, y, 0, -5, y, 0)
    w.set((-5, 5, 0), state('campfire', facing='east', lit='true', signal_fire='false', waterlogged='false'))
    # chimney cap: slabs on the corners
    for x, z in ((-6, -1), (-4, 1)):
        w.set((x, 7, z), state('deepslate_brick_slab', type='bottom', waterlogged='false'))
    # hearth stones in front
    for z in (-1, 0, 1):
        w.set((-3, -1, z), 'minecraft:polished_blackstone')
    # mantel board across the breast
    for z in (-1, 0, 1):
        w.set((-3, 2, z), state('dark_oak_trapdoor', facing='east', half='bottom', open='false', powered='false',
                                waterlogged='false'))


def build_furniture(w):
    # Dudley's sofa (north wall): back stairs at z -3, seat slabs at z -2, arms of trapdoors
    for x in (-2, -1, 0):
        w.set((x, 0, -3), state('mangrove_stairs', facing='north', half='bottom', shape='straight',
                                waterlogged='false'))
        w.set((x, 0, -2), state('mangrove_slab', type='bottom', waterlogged='false'))
    for z in (-3, -2):
        w.set((-3, 0, z), state('dark_oak_trapdoor', facing='west', half='bottom', open='true', powered='false',
                                waterlogged='false'))
        w.set((1, 0, z), state('dark_oak_trapdoor', facing='east', half='bottom', open='true', powered='false',
                               waterlogged='false'))
    # the boy's blanket before the hearth
    for z in (1, 2):
        w.set((-3, 0, z), 'minecraft:brown_carpet')
    # the Dursleys' corner (north-east): a low bed, a barrel with candles, a chair under the window
    w.set((3, 0, -3), state('brown_bed', facing='north', part='head', occupied='false'))
    w.set((3, 0, -2), state('brown_bed', facing='north', part='foot', occupied='false'))
    w.set((3, 0, -1), state('barrel', facing='up', open='false'))
    w.set((3, 1, -1), state('candle', candles='3', lit='true', waterlogged='false'))
    w.set((3, 0, 0), state('spruce_stairs', facing='east', half='bottom', shape='straight', waterlogged='false'))
    w.set((3, 1, 0), state('light', level='10', waterlogged='false'))          # firelight on the window glass
    # crates and barrels (south-east)
    w.set((3, 0, 3), state('barrel', facing='north', open='false'))
    w.set((3, 1, 3), state('barrel', facing='up', open='false'))
    w.set((2, 0, 3), state('barrel', facing='west', open='false'))
    w.set((3, 0, 2), state('spruce_trapdoor', facing='north', half='bottom', open='false', powered='false',
                           waterlogged='false'))
    w.set((2, 1, 3), state('candle', candles='1', lit='false', waterlogged='false'))
    # log pile (south-west)
    for p in ((-4, 0, 2), (-4, 0, 3), (-4, 1, 3), (-3, 0, 3)):
        w.set(p, state('stripped_spruce_log', axis='z' if p[0] == -4 else 'x'))
    # cobwebs high in two corners
    w.set((3, 3, -3), 'minecraft:cobweb')
    # soft fill light so the room reads (the embers and candles do the colour)
    w.set((0, 2, -1), state('light', level='5', waterlogged='false'))
    # outside: a rain barrel by the east wall
    w.set((5, 0, 3), state('barrel', facing='up', open='false'))


def build(world=None):
    """Fills `world` (a dict scene cell -> block state; a new one when None) with the set and returns ANCHORS."""
    w = World({} if world is None else world)
    H = build_sea_and_rock(w)
    build_landing(w, H)
    build_hut(w)
    return ANCHORS


def build_world():
    w = World()
    H = build_sea_and_rock(w)
    build_landing(w, H)
    build_hut(w)
    return w, H


# ---------------------------------------------------------------- anchors
def _p(x, y, z):
    return [round(x, 4), round(y, 4), round(z, 4)]


def _boat_points():
    return [_p(x, SEA_Y, z) for x, z in BOAT_PATH]


def _yaw_of(dx, dz):
    """Minecraft yaw facing direction (dx, dz): 0 = +Z, 90 = -X."""
    return round(math.degrees(math.atan2(-dx, dz)), 2)


def _landing_steps():
    pts = [_p(6.0, LEDGE[4] + 1, LEDGE[2] + 0.6)]
    for z, y in reversed(STEPS):
        pts.append(_p(6.0, y + 1, z + 0.3))
    pts.append(_p(6.0, 0, 7.6))
    pts.append(_p(3.0, 0, 7.2))
    pts.append(_p(0.0, 0, 6.6))
    return pts


_b0, _b1 = BOAT_PATH[0], BOAT_PATH[1]
HAGRID_STOOP, HAGRID_HEAD = 62.0, -25.0

ANCHORS = {
    'sea_center': _p(0.0, SEA_Y, 0.0),
    'sea_spread': [SEA_HALF - 1, 0, SEA_HALF - 1],
    'boat_start': _p(BOAT_PATH[0][0], SEA_Y, BOAT_PATH[0][1]),
    'boat_start_yaw': _yaw_of(_b1[0] - _b0[0], _b1[1] - _b0[1]),
    'boat_path': _boat_points(),
    'boat_landing': _p(BOAT_PATH[-1][0], SEA_Y, BOAT_PATH[-1][1]),
    'boat_landing_yaw': _yaw_of(BOAT_PATH[-1][0] - BOAT_PATH[-2][0], BOAT_PATH[-1][1] - BOAT_PATH[-2][1]),
    'landing_ledge': _p(6.0, LEDGE[4] + 1, LEDGE[2] + 0.6),
    'landing_steps': _landing_steps(),
    'porch': {'pos': _p(0.0, 0, 6.2), 'yaw': 180.0},
    'hagrid_outside': {'pos': _p(0.0, 0, 6.35), 'yaw': 180.0},
    'hagrid_inside': {'pos': _p(0.0, 0.2, 2.1), 'yaw': 180.0},
    'hagrid_entry': {'from': _p(0.0, 0, 6.35), 'to': _p(0.0, 0.2, 2.1), 'stoop_deg': HAGRID_STOOP,
                     'head_deg': HAGRID_HEAD},
    'hut_door_hinge': {'pos': _p(DOOR_X[1] + 1.0, 0, HINGE_Z), 'yaw': 180.0, 'scale': LEAF_SCALE,
                       'mirrored': False, 'fallen_pitch': -90.0, 'ajar_inward_yaw': 174.0},
    'hut_door_hinge_mirrored': {'pos': _p(DOOR_X[0] * 1.0, 0, HINGE_Z), 'yaw': 180.0, 'scale': LEAF_SCALE,
                                'mirrored': True, 'fallen_pitch': -90.0, 'ajar_inward_yaw': 186.0},
    'hut_door_scale': LEAF_SCALE,
    'hut_door_fallen_box': [_p(DOOR_X[0], 0, FALLEN_Z[0]), _p(DOOR_X[1] + 1, 0.2 * LEAF_SCALE, FALLEN_Z[1])],
    'hut_doorway': {'x': [DOOR_X[0], DOOR_X[1] + 1], 'y': [0, LINTEL_Y], 'z': [HZ1, HZ1 + 1]},
    'hut_interior_box': [[IX0, 0, IZ0], [IX1 + 1, 4, IZ1 + 1]],
    'boy_floor': {'feet': _p(-2.5, 1 / 16.0, 2.45), 'head': _p(-2.5, 1 / 16.0, 1.1), 'yaw': 180.0,
                  'surface_y': 1 / 16.0},
    'dudley_sofa': {'center': _p(-0.5, 0.5, -1.85), 'head': _p(-1.2, 0.5, -1.85), 'feet': _p(0.15, 0.5, -1.85),
                    'yaw': 90.0, 'seat_top': 0.5, 'seat': [-0.5, 0.5, -1.7, 0.0]},
    'vernon_corner': {'pos': _p(2.4, 0, 0.3), 'yaw': 33.0, 'seat': [3.4, 0.5, 0.5, 90.0]},
    'petunia_corner': {'pos': _p(2.2, 0, -2.4), 'yaw': 19.0, 'bed_seat': [3.3, 0.5625, -2.5, 90.0]},
    'fireplace': {'embers': _p(-4.5, 0.4375, 0.5), 'mouth': _p(-3.0, 0.5, 0.5), 'yaw': -90.0,
                  'flue_top': _p(-4.5, 7.0, 0.5)},
    'window': {'center': _p(HX1 + 0.5, 1.5, 0.5), 'inside': _p(HX1 - 1.8, 0, 0.5), 'outside': _p(HX1 + 2.5, 0, 0.5)},
    'rain_box': [[-26, int(SEA_Y) - 1, -24], [40, 16, 70]],
    'RADIUS': RADIUS,
}


# ---------------------------------------------------------------- checks
def occupied(w, p):
    """Whether the point p (scene coordinates) lies inside a block's collision-ish shape."""
    c = (int(math.floor(p[0])), int(math.floor(p[1])), int(math.floor(p[2])))
    s = w.get(c)
    b = bid(s)
    if b in FREE or b == 'minecraft:cobweb':
        return False
    fx, fy, fz = p[0] - c[0], p[1] - c[1], p[2] - c[2]
    name, props = parse_state(s)
    if name.endswith('_slab'):
        t = props.get('type', 'bottom')
        return t == 'double' or (fy < 0.5 if t == 'bottom' else fy >= 0.5)
    if name.endswith('_carpet'):
        return fy < 1 / 16.0
    if name.endswith('_trapdoor'):
        th = 3 / 16.0
        if props.get('open') == 'true':
            f = props.get('facing')
            return {'north': fz > 1 - th, 'south': fz < th, 'east': fx < th, 'west': fx > 1 - th}[f]
        return fy >= 1 - th if props.get('half') == 'top' else fy < th
    if name.endswith('_stairs'):
        if fy < 0.5:
            return True                                       # every stair here is half=bottom
        f = props.get('facing')
        return {'north': fz < 0.5, 'south': fz >= 0.5, 'east': fx >= 0.5, 'west': fx < 0.5}[f]
    if name.endswith('_fence') or name == 'minecraft:glass_pane':
        return 0.375 <= fx <= 0.625 and 0.375 <= fz <= 0.625 or name == 'minecraft:glass_pane'
    if name == 'minecraft:campfire':
        return fy < 7 / 16.0
    if name.endswith('_bed'):
        return fy < 9 / 16.0
    if name == 'minecraft:candle':
        return 0.3 <= fx <= 0.7 and 0.3 <= fz <= 0.7 and fy < 0.4
    return True


def solid_below(w, p):
    return occupied(w, (p[0], p[1] - 0.02, p[2]))


# --- Hagrid's body (scale 1.5), the player model's boxes, for the doorway check
PX = 1.5 * 15 / 16 / 16.0          # one model pixel in blocks
MARGIN = 0.5 * PX                  # the 0.25 px outer layer plus a hair


def _box_points(cx, cy, cz, hx, hy, hz, n=4):
    pts = []
    for i in range(n + 1):
        for j in range(n + 1):
            for k in range(n + 1):
                if 0 < i < n and 0 < j < n and 0 < k < n:
                    continue
                pts.append((cx + hx * (2 * i / n - 1), cy + hy * (2 * j / n - 1), cz + hz * (2 * k / n - 1)))
    return pts


def hagrid_points(root, stoop_deg, head_deg, yaw_deg=180.0):
    """Surface sample points of Hagrid's body boxes (legs, torso, arms, head), stooped forward stoop_deg at the
    hips, head tipped head_deg relative to the torso (negative lifts the face), facing yaw."""
    a = math.radians(stoop_deg)
    hd = math.radians(stoop_deg + head_deg)
    hip = 0.7041 * 1.5
    m = MARGIN
    local = []
    # legs: 8 px wide in all, 12 px down from the hip, 4 deep (not stooped)
    for p in _box_points(0, -hip / 2, 0, 4 * PX + m, hip / 2, 2 * PX + m):
        local.append(p)

    # torso: 8 x 12 x 4 px, pivot at the hip
    for x, y, z in _box_points(0, 6 * PX, 0, 4 * PX + m, 6 * PX + m, 2 * PX + m):
        ty = y * math.cos(a) - z * math.sin(a)
        tz = y * math.sin(a) + z * math.cos(a)
        local.append((x, ty, tz))
    # the neck point and shoulders on the tilted torso
    neck = (12 * PX * math.cos(a), 12 * PX * math.sin(a))
    sh = (10 * PX * math.cos(a), 10 * PX * math.sin(a))
    # arms hang straight down from the shoulders (12 px, 2 px above the pivot)
    for side in (-1, 1):
        for x, y, z in _box_points(side * 6 * PX, -4 * PX, 0, 2 * PX + m, 6 * PX + m, 2 * PX + m):
            local.append((x, sh[0] + y, sh[1] + z))
    # head: 8 px cube on the neck, tilted by hd
    for x, y, z in _box_points(0, 4 * PX, 0, 4 * PX + m, 4 * PX + m, 4 * PX + m):
        ty = y * math.cos(hd) - z * math.sin(hd)
        tz = y * math.sin(hd) + z * math.cos(hd)
        local.append((x, neck[0] + ty, neck[1] + tz))
    yaw = math.radians(yaw_deg)
    fx, fz = -math.sin(yaw), math.cos(yaw)          # forward
    rx, rz = -fz, fx                                 # right-hand side (only symmetry matters)
    out = []
    for x, y, z in local:
        out.append((root[0] + x * rx + z * fx, root[1] + hip + y, root[2] + x * rz + z * fz))
    return out


def hagrid_pass(w, stoop, head, z_from, z_to, y_at):
    """Worst clearance problems for the walk through the doorway: list of (root z, point) that hit a block."""
    hits = []
    n = int(abs(z_from - z_to) / 0.05)
    for i in range(n + 1):
        z = z_from + (z_to - z_from) * i / n
        root = (0.0, y_at(z), z)
        for q in hagrid_points(root, stoop, head):
            if occupied(w, q):
                hits.append((round(z, 2), tuple(round(v, 3) for v in q)))
                break
    return hits


def check(w, H):
    problems = []
    A = ANCHORS
    g = w.g

    def stand(name, p, height=1.9):
        if not solid_below(w, p):
            problems.append('%s %s: nothing solid under the feet (%s)' % (name, p, g.get(
                (int(math.floor(p[0])), int(math.floor(p[1] - 0.02)), int(math.floor(p[2]))))))
        for dx in (-0.25, 0.25):
            for dz in (-0.25, 0.25):
                for dy in (0.05, height / 2, height - 0.05):
                    q = (p[0] + dx, p[1] + dy, p[2] + dz)
                    if occupied(w, q):
                        problems.append('%s %s: body point %s inside %s' % (name, p, q, g.get(
                            tuple(int(math.floor(v)) for v in q))))
                        return

    stand('landing_ledge', A['landing_ledge'])
    for i, p in enumerate(A['landing_steps']):
        stand('landing_steps[%d]' % i, p)
    stand('porch', A['porch']['pos'])
    stand('hagrid_outside', A['hagrid_outside']['pos'], 2.85)
    stand('window.outside', A['window']['outside'])
    stand('window.inside', A['window']['inside'])
    stand('vernon_corner', A['vernon_corner']['pos'])
    stand('petunia_corner', A['petunia_corner']['pos'])
    # Hagrid inside, upright on the fallen leaves: clear to 2.81 + margin above y 0.2
    for q in hagrid_points(A['hagrid_inside']['pos'], 0, 0):
        if occupied(w, q):
            problems.append('hagrid_inside upright: %s inside %s' % (q, g.get(tuple(int(math.floor(v)) for v in q))))
            break
    # the doorway: stooped he clears it, upright he does not
    def y_at(z):
        return 0.2 if z < FALLEN_Z[1] else 0.0
    hits = hagrid_pass(w, HAGRID_STOOP, HAGRID_HEAD, 7.2, 2.1, y_at)
    if hits:
        problems.append('stooped Hagrid hits the doorway: %s' % hits[:3])
    if not hagrid_pass(w, 0, 0, 7.2, 2.1, y_at):
        problems.append('upright Hagrid clears the doorway: it is not low enough')
    # the closed leaves: their volume (back face to front face, 3 tall) is air
    for hinge in (A['hut_door_hinge'], A['hut_door_hinge_mirrored']):
        hx, hy, hz = hinge['pos']
        x_lo, x_hi = (hx - 1, hx) if not hinge['mirrored'] else (hx, hx + 1)
        for i in range(11):
            for j in range(31):
                for k in range(3):
                    q = (x_lo + 0.02 + 0.96 * i / 10, 0.02 + 2.96 * j / 30, hz - 0.01 - 0.18 * k / 2)
                    if occupied(w, q):
                        problems.append('closed leaf point %s inside %s' % (q, g.get(
                            tuple(int(math.floor(v)) for v in q))))
                        break
        # the fall: the leaf sweeps a quarter circle about its bottom front edge (z = hz - 0.2) toward -Z
        pz = hz - 0.2 * LEAF_SCALE
        for deg in range(0, 91, 3):
            r = math.radians(deg)
            for rr in [i * 0.15 for i in range(1, 21)]:
                q_y = rr * math.cos(r) + 0.01
                q_z = pz - rr * math.sin(r)
                for xx in (x_lo + 0.05, x_lo + 0.5, x_lo + 0.95):
                    if occupied(w, (xx, q_y, q_z)):
                        problems.append('falling leaf at %d deg hits %s at %s' % (deg, g.get(
                            (int(math.floor(xx)), int(math.floor(q_y)), int(math.floor(q_z)))), (xx, q_y, q_z)))
                        break
    # the fallen leaves lie on floor, inside the hut, on nothing else
    (x1, y1, z1), (x2, y2, z2) = A['hut_door_fallen_box']
    if not (IZ0 <= z1 and z2 <= HZ1 and IX0 <= x1 and x2 <= IX1 + 1):
        problems.append('fallen leaves outside the room: %s' % A['hut_door_fallen_box'])
    for x in range(int(math.floor(x1)), int(math.ceil(x2))):
        for z in range(int(math.floor(z1)), int(math.ceil(z2))):
            if bid(g.get((x, 0, z))) not in (None, 'minecraft:light'):
                problems.append('fallen leaf over %s at %s' % (g.get((x, 0, z)), (x, 0, z)))
            if bid(g.get((x, -1, z))) in FREE:
                problems.append('fallen leaf over no floor at %s' % ((x, -1, z),))
    # seats and lying spots
    s = A['dudley_sofa']
    for k in ('center', 'head', 'feet'):
        p = s[k]
        if not occupied(w, (p[0], p[1] - 0.02, p[2])) or occupied(w, (p[0], p[1] + 0.05, p[2])):
            problems.append('dudley_sofa.%s %s: not on the seat top' % (k, p))
    for k in ('feet', 'head'):
        p = A['boy_floor'][k]
        if not occupied(w, (p[0], p[1] - 0.02, p[2])) or occupied(w, (p[0], p[1] + 0.05, p[2])):
            problems.append('boy_floor.%s %s: not on the blanket' % (k, p))
        for dy in (0.1, 0.3):
            for dx in (-0.3, 0.3):
                if occupied(w, (p[0] + dx, p[1] + dy, p[2])):
                    problems.append('boy_floor.%s: body hits a block' % k)
    for name, seat in (('vernon_corner.seat', A['vernon_corner']['seat']),
                       ('petunia_corner.bed_seat', A['petunia_corner']['bed_seat'])):
        x, top, z, _ = seat
        if not occupied(w, (x, top - 0.02, z)) or occupied(w, (x, top + 0.05, z)):
            problems.append('%s %s: not on a seat top' % (name, seat))
    # the boy, Dudley and the Dursleys are clear of the leaves' landing
    for name, p in (('boy feet', A['boy_floor']['feet']), ('boy head', A['boy_floor']['head']),
                    ('vernon', A['vernon_corner']['pos']), ('petunia', A['petunia_corner']['pos'])):
        if x1 - 0.4 <= p[0] <= x2 + 0.4 and z1 - 0.4 <= p[2] <= z2 + 0.4:
            problems.append('%s %s lies in the leaves\' landing' % (name, p))
    # embers: the firebox
    e = A['fireplace']['embers']
    if bid(g.get((int(math.floor(e[0])), 0, int(math.floor(e[2]))))) != 'minecraft:campfire':
        problems.append('fireplace.embers not over the fire')
    if bid(g.get((int(math.floor(e[0])), -1, int(math.floor(e[2]))))) != 'minecraft:magma_block':
        problems.append('no embers under the fire')
    # the boat: every hull pose along the path is clear of rock from y -8 up (it heaves +-0.6 about SEA_Y)
    for pose in boat_poses(0.25):
        cx, cz = pose[0], pose[1]
        for x in range(int(math.floor(cx)) - 3, int(math.floor(cx)) + 4):
            for z in range(int(math.floor(cz)) - 3, int(math.floor(cz)) + 4):
                touches = any(in_hull(x + fx, z + fz, pose, 0.15) for fx in (0.02, 0.5, 0.98)
                              for fz in (0.02, 0.5, 0.98))
                if not touches:
                    continue
                for y in range(WATER_TOP, int(SEA_Y) + 3):
                    b = bid(g.get((x, y, z)))
                    if b not in (None, 'minecraft:water'):
                        problems.append('boat at %s touches %s at %s' % ((round(cx, 2), round(cz, 2)), b, (x, y, z)))
                        break
    # the bow at the landing is near the ledge (a beached boat), and the ledge is above the water
    lx, lz = BOAT_PATH[-1]
    bow_z = lz - HULL_HALF_LEN
    ledge_face = LEDGE[3] + 1
    if not (0.0 < bow_z - ledge_face <= 0.6):
        problems.append('bow %.2f is not just off the ledge face %d' % (bow_z, ledge_face))
    # rain box covers the path and the islet; the sea spread covers the path with margin
    (rx1, ry1, rz1), (rx2, ry2, rz2) = A['rain_box']
    for x, z in BOAT_PATH + [(-13, -12), (13, 13)]:
        if not (rx1 + 6 <= x <= rx2 - 6 and rz1 + 4 <= z <= rz2 - 4):
            problems.append('rain box does not cover %s with margin' % ((x, z),))
    hx, _, hz = A['sea_spread']
    for x, z in BOAT_PATH:
        if abs(x) > hx - 10 or abs(z) > hz - 10:
            problems.append('sea spread too small for %s' % ((x, z),))
    if not (2 <= hx <= 100 and 2 <= hz <= 100):
        problems.append('sea spread outside the effect range')
    # the islet: top of rock never higher than the spires allow, and nothing outside the template
    for (x, y, z) in g:
        lx_, ly_, lz_ = x - TEMPLATE_AT[0], y - TEMPLATE_AT[1], z - TEMPLATE_AT[2]
        if not (0 <= lx_ < SIZE[0] and 0 <= ly_ < SIZE[1] and 0 <= lz_ < SIZE[2]):
            problems.append('cell %s outside the template' % ((x, y, z),))
            break
    return problems


def scene_world(g):
    return {p: bid(s) for p, s in g.items()}


def local_grid(g):
    return {(x - TEMPLATE_AT[0], y - TEMPLATE_AT[1], z - TEMPLATE_AT[2]): s for (x, y, z), s in g.items()}


def min_stoop(w):
    def y_at(z):
        return 0.2 if z < FALLEN_Z[1] else 0.0
    for a in range(0, 91):
        if not hagrid_pass(w, a, HAGRID_HEAD, 7.2, 2.1, y_at):
            return a
    return None


def lintel_clearance(stoop, head):
    """The highest body point of the stooped Hagrid under the lintel, and the side margins at the jambs."""
    top, side = -1e9, 1e9
    for z in [4.0 + 0.05 * i for i in range(-60, 21)]:
        for q in hagrid_points((0.0, 0.0, z + 1.4), stoop, head):
            if 4.0 <= q[2] <= 5.0:
                top = max(top, q[1])
                side = min(side, 1.0 - abs(q[0]))
    return top, side


def main():
    w, H = build_world()
    g = w.g
    problems = physics_problems(scene_world(g), RADIUS)
    assert not problems, problems
    for axis in (0, 2):
        lo, hi = TEMPLATE_AT[axis], TEMPLATE_AT[axis] + SIZE[axis] - 1
        assert -RADIUS <= lo and hi <= RADIUS, ('template beyond radius', axis, lo, hi)
    assert WATER_TOP <= SEA_Y - 2, 'the block water must sit at least a block under the rough_sea surface'
    assert LEDGE[4] + 1 > SEA_Y + 0.6 * 1.5, 'the landing ledge must stay above the crests at intensity 1.5'
    problems = check(w, H)
    assert not problems, '\n'.join(problems[:40]) + ('\n... %d in all' % len(problems))
    n_pal, n_blocks = write_template(local_grid(g), SIZE, OUT)
    print('letter_island.nbt: size %s, %d blocks, %d palette states, %d bytes; TEMPLATE_AT %s RADIUS %d' % (
        SIZE, n_blocks, n_pal, os.path.getsize(OUT), TEMPLATE_AT, RADIUS))
    counts = {}
    for s in g.values():
        counts[bid(s)] = counts.get(bid(s), 0) + 1
    print('  census: ' + ', '.join('%s %d' % (k.split(':')[1], v) for k, v in
                                    sorted(counts.items(), key=lambda kv: -kv[1])[:24]))
    top, side = lintel_clearance(HAGRID_STOOP, HAGRID_HEAD)
    print('  Hagrid at 1.5: stooped %.0f deg (head %+.0f) top under the lintel %.3f (lintel %.2f), jamb margin %.3f;'
          ' the least stoop that clears the walk: %s deg' % (HAGRID_STOOP, HAGRID_HEAD, top, LINTEL_Y, side,
                                                             min_stoop(w)))
    for k, v in ANCHORS.items():
        print('  %s: %s' % (k, v))


if __name__ == '__main__':
    main()
