"""Builds the Letter memory's storm set: structure/letter_island.nbt, the night sea with the bare black rock and its
ramshackle one-room hut, and the anchors letter_scene.py stages `letter_crossing` and `letter_hut` with.

    python -I build_scripts/pensieve_scenes/letter_island.py

Using the set from a scene (letter_scene.py):

    import letter_island as LI
    'radius': LI.RADIUS,
    'set': {'templates': [{'id': 'hogwartsmagic:letter_island', 'at': list(LI.TEMPLATE_AT)}],
            'time_of_day': LI.TIME_OF_DAY}
    rough_sea:  {'at': LI.ANCHORS['sea_center'], 'spread': LI.ANCHORS['sea_spread'], ...}
    rain:       {'at': LI.ANCHORS['rain']['at'], 'spread': LI.ANCHORS['rain']['spread'], ...} (the effect takes a
                centre and half-size; ANCHORS['rain_box'] is the same box as min and max corners)

The set
-------
  - The sea: 160 x 160 blocks (scene x, z -80..79), three layers of water (cells y -13..-11) on a deepslate bed
    (y -14), closed all round by a barrier rim, so physics_problems is empty. The `rough_sea` surface is drawn over
    it at the still-water level SEA_Y = -8.0 (its `at` y). The top water block is at.y - 3 (SKILL asks at least
    at.y - 2), so the block water's own surface (-10.11) stays under the deepest trough even at intensity 2
    (-9.2): the water blocks are only the deep body under the animated surface. Without a rough_sea event the
    block water reads as low tide.
  - The rock: a craggy, sloping, wave-worn lump about 26 x 23 at the waterline (radii 13 east-west, 10.5 north and
    12.5 south toward the landing, domain-warped so the coastline wanders). Its profile falls 9.5 blocks from the
    hut to the sea over a smoothstep with buttress and gully noise (mostly 40-55 degrees, steeper in patches);
    the crown shoulders up 2-4 blocks behind the hut on the windward north and west and falls away to the south
    and east, so the hut sits in a shallow notch on the skyline seen from the sea. Only the hut footprint plus
    one, a porch and a two-wide trail are forced flat (stand y 0); the rock round them slopes off about 1.4 a
    cell. Dressing: overhanging lips on the north and west faces, wave notches cut in at -8..-7, scree aprons of
    stairs, slabs and loose blocks at the foot of the faces, and about 34 boulder clusters along the waterline.
    Wetness is graded: blackstone below -7, a noisy ramp of blackstone into the dry rock up to -4, dark streaks
    up the windward gullies, a few polished blackstone patches. Five basalt crags stand on the windward
    shoulders and five sea stacks off the islet, every one a tapered, uneven cone falling to the seabed with a
    boulder apron at its foot (two are broken stumps awash at -7 / -6).
  - The landing: cut steps down a rock spur on the south-east from the trail to a flat ledge one block above the
    still water (stand y -7, dry under the crests to intensity 1.5). The rises are irregular (a double tread at a
    halfway landing); the walking line is worn polished deepslate, the edge rough cobbled deepslate, with a
    rickety dark oak handrail on the seaward kerb, a mooring post and a stray board. The porch trail is a groove
    edged with slabs. A clear channel for the boat runs in from the south-east (rock cut to the bed under every
    hull pose).
  - The hut: one room, 8 x 7 inside (x -4..3, z -3..3), walls of weathered dark oak boards (planks, stripped and
    barked wood by board, a few spruce) on a
    deepslate footing with log posts and nailed-on patch boards, a shallow sagging slab roof (ridge 6.5 at the
    gables, 6.0 in the middle, ragged eaves with missing boards) on two tie beams, a rubble chimney stack on the
    west wall with a smoking campfire in its flue, and:
      * the fireplace in the west wall: a recessed firebox with a burnt-down log fire on glowing embers (magma),
        a polished blackstone hearth;
      * the old faded-red sofa under the north wall where Dudley sleeps (1.5 deep, seat top 0.5);
      * a brown blanket on the floor in front of the fire where the boy lies, his head at the hearth;
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
  hagrid_entry          {from, to, stoop_deg, head_deg, x_tolerance} his walk through the doorway (feet, x 0):
                                   stooped forward stoop_deg (45) at the hips with the chin tucked head_deg (+30,
                                   forward of the torso line); the body clears the doorway at x 0 and at
                                   +-x_tolerance (0.2); the least stoop with this head is 40; upright he hits the
                                   lintel. The jambs leave him 0.25 a side at x 0 with the arms hanging
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
  hut_interior_box      [[x1,y1,z1],[x2,y2,z2]] the room inside the walls, floor to wall top (it includes the
                                   chimney breast at x -4, z -1..1); for keeping rain and spray out of it
  boy_floor             {feet, head, yaw, surface_y} the boy lying on the blanket in front of the fire, along x;
                                   yaw is the direction from feet to head (90: head toward -X, the fire)
  dudley_sofa           {center, head, feet, yaw, seat_top, seat}  Dudley lying along the sofa (head west,
                                   yaw 90 from feet to head); `seat` is a seated mark facing south if he sits up
  vernon_corner         {pos, yaw, seat} Vernon's standing mark by his chair, facing the door; seat = the chair
  petunia_corner        {pos, yaw, bed_seat} Petunia's standing mark by the bed, facing the door
  fireplace             {embers, mouth, yaw, flue_top, fire_cell, flue_fire_cell} embers: top centre of the
                                   burnt-down fire (in the firebox); mouth: the opening's centre on the room face;
                                   yaw faces out of it (-90, +X); fire_cell [-5, 0, 0] and flue_fire_cell [-5, 5, 0]
                                   are the whole-block cells of the unlit and the lit campfire, for `block` events
  window                {center, inside, outside} the glass's centre, a spot 1 in front of it inside / outside
  rain_box              [[x1,y1,z1],[x2,y2,z2]] the crossing and the islet as min and max corners (the hut is
                                   inside it: the rain stops at its roof)
  rain                  {at, spread} the same box as the `rain` effect takes it (centre, half-size)
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
# (z, cell y, kind): the cut steps from the porch trail down to the ledge, rising north. Irregular: a double tread
# (z 11/12, the halfway landing is a full block) and a deeper last drop onto the ledge.
STEPS = [(9, -2, 'stair'), (10, -3, 'stair'), (11, -4, 'stair'), (12, -4, 'landing'), (13, -5, 'stair'),
         (14, -6, 'stair'), (15, -7, 'stair')]
STEP_X = (5, 6)
LEDGE = (4, 8, 16, 17, -8)             # x0, x1, z0, z1, top cell y (stand at -7, one above the still water)

BOAT_PATH = [(30.5, 64.6), (27.6, 57.1), (23.9, 48.9), (19.6, 40.7), (15.4, 33.3), (11.4, 27.9), (8.2, 25.1),
             (6.2, 22.7), (6.0, 20.6)]
HULL_HALF_LEN, HULL_HALF_W = 2.2, 0.95

# (x, z, radius, top cell y, lean x, lean z): basalt crags on the windward (north and west) shoulders only, so the
# hut stands on the skyline from the sea, and sea stacks off the islet (two broken stumps awash, so they foam).
# Every crag falls to the seabed; lean shifts its centre by that many blocks per block of height.
SPIRES = [(-6, -8, 3.0, 4, 0.0, -0.15), (-2, -9.5, 2.4, 2, 0.1, -0.1), (-10, -3, 2.8, 3, -0.15, 0.0),
          (-9, 3, 2.0, 0, -0.1, 0.05), (8, -7, 2.2, 1, 0.1, -0.1)]
SKERRIES = [(-21, -13, 2.6, 1, -0.12, 0.05), (19, -12, 2.2, -2, 0.1, -0.08), (-17, 15, 1.8, -4, -0.08, 0.1),
            (24, 7, 1.6, -7, 0.0, 0.0), (-9, 22, 1.5, -6, 0.0, 0.0)]


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
    """Cells forced to the stand height (top cell y -1): the hut footprint plus one (chimney and eaves inside it),
    the porch, and the two-wide trail from the porch to the top step."""
    if -7 <= x <= 5 and -5 <= z <= 5:
        return True
    if -2 <= x <= 1 and 5 <= z <= 7:
        return True
    if 1 <= x <= 6 and 6 <= z <= 8:
        return True
    if x == 6 and -1 <= z <= 1:                            # a foothold under the window
        return True
    return False


def trail_zone(x, z):
    """The porch and the trail (outside the hut footprint): kept flat, with its own edging."""
    return flat_zone(x, z) and not (-7 <= x <= 5 and -5 <= z <= 5)


def _smoothstep(a, b, t):
    t = max(0.0, min(1.0, (t - a) / (b - a)))
    return t * t * (3 - 2 * t)


def _crag(cx, cz, x0, z0, r, top, lean, seed):
    """A tapered, uneven stack of columns falling all the way to the seabed: the column top is `top` at the centre
    and BED_Y at distance r, each column jittered, the centre leaning by `lean` blocks per block of height."""
    best = None
    for k in range(3):                                   # sample the lean at three heights, keep the highest
        y_guess = top - k * (top - BED_Y) / 3.0
        ox, oz = x0 + lean[0] * (y_guess - BED_Y), z0 + lean[1] * (y_guess - BED_Y)
        d = max(0.0, math.hypot(cx - ox, cz - oz) + (vnoise(cx, cz, 1.6, seed) - 0.5) * 1.1)
        if d >= r * 1.45:
            continue
        if d < r:
            jitter = (_hash(int(math.floor(cx)), int(math.floor(cz)), seed + 1) - 0.5) * 2.2
            h = top - (d / r) ** 1.7 * (top - BED_Y) + jitter * (1 - d / r)
        else:                                            # a flared foot: a boulder apron at the waterline
            h = WATER_TOP + 0.5 + (vnoise(cx, cz, 1.2, seed + 2) - 0.5) * 3.0 - (d - r) / (0.45 * r) * 2.0
        best = h if best is None else max(best, h)
    return best


def _bias(cx, cz):
    """How much the summit rises on this side: about 1 north and west (windward), about 0.3 south and east."""
    L = math.hypot(cx, cz - 0.5) or 1.0
    ux, uz = cx / L, (cz - 0.5) / L
    return max(0.25, min(1.05, 0.65 - 0.4 * (ux + uz)))


def height(x, z):
    """Top rock cell y of column (x, z); BED_Y for open sea floor (bed only)."""
    if flat_zone(x, z):
        return -1
    if (x, z) in HULL_CELLS:
        return BED_Y
    cx, cz = x + 0.5, z + 0.5
    n = fbm(cx, cz, 11)
    rz = 12.5 if cz > 0.5 else 10.5                       # the front (south) runs out longer toward the landing
    r0 = math.hypot(cx / 13.0, (cz - 0.5) / rz)
    rw = r0 + (n - 0.5) * 0.36                            # domain-warped: the coastline wanders
    b = _bias(cx, cz)
    h = -1 - 9.5 * _smoothstep(0.5, 1.08, rw)
    if rw < 0.8:                                          # the crown rises behind the hut, not a plateau
        h += 4.5 * min(1.0, (0.8 - rw) / 0.35) * (b - 0.25) * 1.3
    h += (fbm(cx, cz, 23) - 0.5) * 3.0
    butt = vnoise(cx, cz, 3.2, 25)
    h += (butt - 0.5) * 4.0 * math.sin(math.pi * min(1.0, max(0.0, (rw - 0.3) / 0.75)))
    h += (_hash(x, z, 24) - 0.5) * 1.2
    if rw > 1.05:
        h = min(h, WATER_TOP - 0.5 - (rw - 1.05) * 24)
    # the spur the landing steps are cut down: a landward wall over the steps, falling away seaward
    if 1 <= x <= 11 and 8 <= z <= LEDGE[3] + 1:
        step_y = LEDGE[4]
        for sz, sy, _ in STEPS:
            if sz == z:
                step_y = sy
        if z < STEPS[0][0]:
            step_y = -1
        if cx < 6.0:
            spur = step_y + 1.6 - max(0.0, 5.0 - cx) * 1.0 + (_hash(x, z, 26) - 0.5) * 1.2
        else:
            spur = step_y - 0.6 - max(0.0, cx - 7.0) * 1.3 + (_hash(x, z, 26) - 0.5) * 0.8
        h = max(h, spur)
    for i, (sx, sz, sr, top, lx, lz) in enumerate(SPIRES):
        c = _crag(cx, cz, sx, sz, sr, top, (lx, lz), 31 + i)
        if c is not None:
            h = max(h, c)
    for i, (sx, sz, sr, top, lx, lz) in enumerate(SKERRIES):
        c = _crag(cx, cz, sx, sz, sr, top, (lx, lz), 61 + i)
        if c is not None:
            h = max(h, c)
    return max(BED_Y, min(8, int(math.floor(h))))


# ---------------------------------------------------------------- materials
def rock_state(x, y, z, exposed_up, exposed_side):
    """Dark rock in one tonal family (deepslate, cobbled deepslate, basalt, blackstone). Faces take their material
    by column, so basalt reads as jointed columns. Wetness is graded: blackstone below -7, a noisy ramp of
    blackstone into the dry rock from -7 to -4, dark streaks running up the windward gullies to -2, and a few
    polished blackstone patches for a wet sheen."""
    col = vnoise(x + 0.5, z + 0.5, 2.4, 51)
    patch = vnoise(x + 0.5, z + 0.5, 4.0, 53)
    r = rnd01(x, y, z, 52)
    wet = vnoise(x + 0.5 + y * 0.6, z + 0.5 - y * 0.4, 1.8, 55)
    gully = vnoise(x + 0.5, z + 0.5, 3.2, 25) < 0.3 and _bias(x + 0.5, z + 0.5) > 0.7
    if y <= -8:
        return 'minecraft:polished_blackstone' if r < 0.03 else 'minecraft:blackstone'
    if y <= -4 and wet > (y + 8) / 4.0:
        return 'minecraft:polished_blackstone' if r < 0.04 else 'minecraft:blackstone'
    if exposed_side and gully and y <= -2 and wet < 0.6:
        return 'minecraft:blackstone'
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
    # the rock round the walks shoulders up to them: no cell near the forced flat falls away as a sheer wall,
    # it slopes off about 1.4 per cell
    flat = [c for c in H if flat_zone(*c)]
    near = {}
    for fx, fz in flat:
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                c = (fx + dx, fz + dz)
                if c in H and not flat_zone(*c):
                    d = max(abs(dx), abs(dz))
                    near[c] = min(near.get(c, 9), d)
    for (x, z), d in near.items():
        if (x, z) in HULL_CELLS or (STEP_X[0] - 2 <= x <= STEP_X[1] + 5 and z >= 8):
            continue
        floor_h = -1 - int(round((d - 1) * 1.4 + (_hash(x, z, 27) - 0.5) * 1.2))
        if H[(x, z)] < floor_h:
            H[(x, z)] = floor_h
    # carve the steps and the ledge into the height field
    for x in range(LEDGE[0], LEDGE[1] + 1):
        for z in range(LEDGE[2], LEDGE[3] + 1):
            H[(x, z)] = LEDGE[4]
    for z, y, _ in STEPS:
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
        if LEDGE[0] - 1 <= x <= LEDGE[1] + 1 and LEDGE[2] - 1 <= z <= LEDGE[3] + 1:
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
    weather_rock(w, H)
    return H


def _protected(x, z, grow=1):
    """Cells the rock dressing must not touch: the boat channel (plus `grow`), the walks, steps and ledge."""
    for dx in range(-grow, grow + 1):
        for dz in range(-grow, grow + 1):
            if (x + dx, z + dz) in HULL_CELLS:
                return True
    if flat_zone(x, z):
        return True
    if LEDGE[0] - 1 <= x <= LEDGE[1] + 1 and LEDGE[2] - 1 <= z <= LEDGE[3] + 1:
        return True
    if STEP_X[0] - 1 <= x <= STEP_X[1] + 1 and 8 <= z <= LEDGE[3] + 1:
        return True
    return False


SIDES = ((1, 0, 'east', 'west'), (-1, 0, 'west', 'east'), (0, 1, 'south', 'north'), (0, -1, 'north', 'south'))


def weather_rock(w, H):
    """Overhangs on the windward faces, wave notches at the waterline, scree aprons at the foot of the faces and
    boulders along the waterline."""
    def hh(x, z):
        return H.get((x, z), BED_Y)
    dark = ('minecraft:blackstone', state('basalt', axis='y'))
    # overhangs: the lip of a north or west face juts a cell out over a drop of 3 or more
    for (x, z), h in list(H.items()):
        if h < -6 or _protected(x, z, 2) or _bias(x + 0.5, z + 0.5) < 0.7:
            continue
        for dx, dz, _, _ in SIDES:
            nx, nz = x + dx, z + dz
            if (dx, dz) not in ((-1, 0), (0, -1)) or _protected(nx, nz, 2):
                continue
            if hh(nx, nz) <= h - 3 and rnd01(x, h, z, 71) < 0.3:
                depth = 1 if rnd01(x, h, z, 72) < 0.6 else 2
                for k in range(depth):
                    w.set((nx, h - k, nz), w.get((x, h - k, z)) or 'minecraft:deepslate')
                break
    # wave notches: one block cut back into a face at -8..-7 where 3 or more of rock stands above
    for (x, z), h in list(H.items()):
        if h < -4 or _protected(x, z, 2):
            continue
        if any(hh(x + dx, z + dz) < -8 for dx, dz, _, _ in SIDES) and rnd01(x, 0, z, 73) < 0.55:
            for y in (-8, -7):
                if y <= h - 3:
                    w.set((x, y, z), None)
    # scree: aprons at the foot of the faces, stairs facing out, slabs and loose blocks, 2-4 cells
    for (x, z), h in list(H.items()):
        if not (-10 <= h <= -5) or _protected(x, z, 2):
            continue
        for dx, dz, out_face, in_face in SIDES:
            if hh(x - dx, z - dz) >= h + 3:              # the face rises on the far side: the apron runs this way
                length = 2 + int(rnd01(x, h, z, 74) * 3)
                for k in range(length):
                    cx_, cz_ = x + dx * k, z + dz * k
                    if _protected(cx_, cz_, 2):
                        break
                    top = hh(cx_, cz_)
                    y = top + 1
                    if y > WATER_TOP + 4 or w.get((cx_, y, cz_)) is not None:
                        continue
                    r = rnd01(cx_, y, cz_, 75)
                    m = 'blackstone' if y <= -7 else 'cobbled_deepslate'
                    if k == 0 or r < 0.45:
                        w.set((cx_, y, cz_), state('%s_stairs' % m, facing=in_face, half='bottom',
                                                   shape='straight', waterlogged='false'))
                    elif r < 0.8:
                        w.set((cx_, y, cz_), state('%s_slab' % m, type='bottom', waterlogged='false'))
                    else:
                        w.set((cx_, y, cz_), 'minecraft:' + m)
                break
    # boulders: clusters of 1-3 blocks, tops at -8..-6, round the waterline and a little offshore
    n = 0
    for i in range(400):
        if n >= 34:
            break
        ang = _hash(i, 1, 81) * 2 * math.pi
        rad = 0.98 + _hash(i, 2, 82) * 0.32
        bx = int(math.floor(math.cos(ang) * 13.0 * rad))
        bz = int(math.floor(0.5 + math.sin(ang) * (12.5 if math.sin(ang) > 0 else 10.5) * rad))
        if _protected(bx, bz, 2) or hh(bx, bz) > -8:
            continue
        n += 1
        size = 1 + int(_hash(i, 3, 83) * 3)
        for k in range(size):
            px, pz = bx + (k % 2), bz + (k // 2)
            if _protected(px, pz, 1):
                continue
            top = -8 + int(_hash(i, 4 + k, 84) * 3) - (1 if k else 0)
            for y in range(BED_Y + 1, top + 1):
                w.set((px, y, pz), dark[int(_hash(px, y, 85) * 2)])
            H[(px, pz)] = max(hh(px, pz), top)
            f = SIDES[int(_hash(i, 9 + k, 86) * 4)][3]
            if top + 1 <= -6:
                w.set((px, top + 1, pz), state('blackstone_stairs', facing=f, half='bottom', shape='straight',
                                               waterlogged='false'))


def build_landing(w, H):
    fence = state('dark_oak_fence', north='false', south='false', east='false', west='false', waterlogged='false')
    for z, y, kind in STEPS:
        worn = True                                      # the walking line (x 5) is worn smooth, the edge rough
        for x in STEP_X:
            if kind == 'landing':
                w.set((x, y, z), 'minecraft:polished_deepslate' if worn and x == STEP_X[0] else
                      'minecraft:cobbled_deepslate')
            else:
                m = 'polished_deepslate' if x == STEP_X[0] or rnd01(x, y, z, 87) < 0.25 else 'cobbled_deepslate'
                w.set((x, y, z), state('%s_stairs' % m, facing='north', half='bottom', shape='straight',
                                       waterlogged='false'))
            w.clear(x, y + 1, z, x, y + 4, z)
        # a rickety handrail on the seaward kerb: dark oak posts, one missing
        kx = STEP_X[1] + 1
        w.set((kx, y, z), 'minecraft:cobbled_deepslate')
        w.clear(kx, y + 1, z, kx, y + 3, z)
        if z != 13:
            w.set((kx, y + 1, z), fence)
    # the porch and the trail to the stair head: a groove worn into the rock, edged by slabs where the rock
    # beside it is no higher than the trail
    for (x, z), h in H.items():
        if not trail_zone(x, z):
            continue
        w.clear(x, 0, z, x, 4, z)
        for dx, dz, _, _ in SIDES:
            nx, nz = x + dx, z + dz
            if flat_zone(nx, nz) or (nx in STEP_X and nz >= STEPS[0][0]) or (nx, nz) in HULL_CELLS:
                continue
            if H.get((nx, nz), BED_Y) == -1 and w.get((nx, 0, nz)) is None:
                w.set((nx, 0, nz), state('cobbled_deepslate_slab', type='bottom', waterlogged='false'))
    # ledge: flat dark flags with a mooring post and a stray board
    for x in range(LEDGE[0], LEDGE[1] + 1):
        for z in range(LEDGE[2], LEDGE[3] + 1):
            w.set((x, LEDGE[4], z), 'minecraft:polished_blackstone' if rnd01(x, 0, z, 81) < 0.3
                  else 'minecraft:cobbled_deepslate')
            w.clear(x, LEDGE[4] + 1, z, x, LEDGE[4] + 4, z)
    w.set((LEDGE[1], LEDGE[4] + 1, LEDGE[2]), state('dark_oak_log', axis='y'))
    w.set((LEDGE[1], LEDGE[4] + 2, LEDGE[2]), fence)
    w.set((LEDGE[0], LEDGE[4] + 1, LEDGE[3]), state('spruce_trapdoor', facing='east', half='bottom', open='false',
                                                    powered='false', waterlogged='false'))


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
        w.set((DOOR_X[0] - 1, y, HZ1), state('dark_oak_log', axis='y'))
        w.set((DOOR_X[1] + 1, y, HZ1), state('dark_oak_log', axis='y'))
    for x in DOOR_X:
        w.clear(x, 0, HZ1, x, 1, HZ1)
        w.set((x, 2, HZ1), state('dark_oak_slab', type='top', waterlogged='false'))
        w.set((x, -1, HZ1), 'minecraft:dark_oak_planks')                       # threshold board
    for x in range(DOOR_X[0] - 1, DOOR_X[1] + 2):
        w.set((x, 3, HZ1), state('dark_oak_log', axis='x'))
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
    for x in (-3, -2):
        w.set((x, 0, 0), 'minecraft:brown_carpet')
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
    _build(World({} if world is None else world))
    return ANCHORS


def _build(w):
    H = build_sea_and_rock(w)
    build_landing(w, H)
    build_hut(w)
    return H


def build_world():
    w = World()
    return w, _build(w)


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
    for z, y, kind in reversed(STEPS):
        pts.append(_p(6.0, y + 1, z + (0.5 if kind == 'landing' else 0.3)))
    pts.append(_p(6.0, 0, 7.6))
    pts.append(_p(3.0, 0, 7.2))
    pts.append(_p(0.0, 0, 6.6))
    return pts


_b0, _b1 = BOAT_PATH[0], BOAT_PATH[1]
HAGRID_STOOP, HAGRID_HEAD = 45.0, 30.0
HAGRID_X_TOL = 0.2

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
                     'head_deg': HAGRID_HEAD, 'x_tolerance': HAGRID_X_TOL},
    'hut_door_hinge': {'pos': _p(DOOR_X[1] + 1.0, 0, HINGE_Z), 'yaw': 180.0, 'scale': LEAF_SCALE,
                       'mirrored': False, 'fallen_pitch': -90.0, 'ajar_inward_yaw': 174.0},
    'hut_door_hinge_mirrored': {'pos': _p(DOOR_X[0] * 1.0, 0, HINGE_Z), 'yaw': 180.0, 'scale': LEAF_SCALE,
                                'mirrored': True, 'fallen_pitch': -90.0, 'ajar_inward_yaw': 186.0},
    'hut_door_scale': LEAF_SCALE,
    'hut_door_fallen_box': [_p(DOOR_X[0], 0, FALLEN_Z[0]), _p(DOOR_X[1] + 1, 0.2 * LEAF_SCALE, FALLEN_Z[1])],
    'hut_doorway': {'x': [DOOR_X[0], DOOR_X[1] + 1], 'y': [0, LINTEL_Y], 'z': [HZ1, HZ1 + 1]},
    'hut_interior_box': [[IX0, 0, IZ0], [IX1 + 1, 4, IZ1 + 1]],
    'boy_floor': {'feet': _p(-1.55, 1 / 16.0, 0.5), 'head': _p(-2.9, 1 / 16.0, 0.5), 'yaw': 90.0,
                  'surface_y': 1 / 16.0},
    'dudley_sofa': {'center': _p(-0.5, 0.5, -1.85), 'head': _p(-1.2, 0.5, -1.85), 'feet': _p(0.15, 0.5, -1.85),
                    'yaw': 90.0, 'seat_top': 0.5, 'seat': [-0.5, 0.5, -1.7, 0.0]},
    'vernon_corner': {'pos': _p(2.4, 0, 0.3), 'yaw': 33.0, 'seat': [3.4, 0.5, 0.5, 90.0]},
    'petunia_corner': {'pos': _p(2.2, 0, -2.4), 'yaw': 19.0, 'bed_seat': [3.3, 0.5625, -2.5, 90.0]},
    'fireplace': {'embers': _p(-4.5, 0.4375, 0.5), 'mouth': _p(-3.0, 0.5, 0.5), 'yaw': -90.0,
                  'fire_cell': [-5, 0, 0], 'flue_fire_cell': [-5, 5, 0],
                  'flue_top': _p(-4.5, 7.0, 0.5)},
    'window': {'center': _p(HX1 + 0.5, 1.5, 0.5), 'inside': _p(HX1 - 1.8, 0, 0.5), 'outside': _p(HX1 + 2.5, 0, 0.5)},
    'rain_box': [[-26, int(SEA_Y) - 1, -24], [40, 16, 70]],
    'rain': {'at': [7.0, 3.5, 23.0], 'spread': [33.0, 12.5, 47.0]},
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


def hagrid_pass(w, stoop, head, z_from, z_to, y_at, x=0.0):
    """Worst clearance problems for the walk through the doorway: list of (root z, point) that hit a block."""
    hits = []
    n = int(abs(z_from - z_to) / 0.05)
    for i in range(n + 1):
        z = z_from + (z_to - z_from) * i / n
        root = (x, y_at(z), z)
        for q in hagrid_points(root, stoop, head):
            if occupied(w, q):
                hits.append((round(z, 2), tuple(round(v, 3) for v in q)))
                break
    return hits


def check(w):
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
    for xo in (0.0, -HAGRID_X_TOL, HAGRID_X_TOL):
        hits = hagrid_pass(w, HAGRID_STOOP, HAGRID_HEAD, 7.2, 2.1, y_at, xo)
        if hits:
            problems.append('stooped Hagrid at x %+.2f hits the doorway: %s' % (xo, hits[:3]))
    ms = min_stoop(w, HAGRID_HEAD)
    if ms is None or ms > HAGRID_STOOP - 4:
        problems.append('the anchor stoop %.0f is not 4 degrees past the least that clears (%s)' % (HAGRID_STOOP, ms))
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
    rc = [(rx1 + rx2) / 2.0, (ry1 + ry2) / 2.0, (rz1 + rz2) / 2.0]
    rs = [(rx2 - rx1) / 2.0, (ry2 - ry1) / 2.0, (rz2 - rz1) / 2.0]
    if rc != A['rain']['at'] or rs != A['rain']['spread'] or not all(1 <= v <= 100 for v in rs):
        problems.append('rain at/spread %s does not match rain_box %s' % (A['rain'], A['rain_box']))
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


def min_stoop(w, head=None, x=0.0):
    head = HAGRID_HEAD if head is None else head

    def y_at(z):
        return 0.2 if z < FALLEN_Z[1] else 0.0
    for a in range(0, 91):
        if not hagrid_pass(w, a, head, 7.2, 2.1, y_at, x):
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
    problems = check(w)
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
          ' the least stoop that clears the walk: %s deg (at x +-%.2f: %s / %s)' % (
              HAGRID_STOOP, HAGRID_HEAD, top, LINTEL_Y, side, min_stoop(w), HAGRID_X_TOL,
              min_stoop(w, None, -HAGRID_X_TOL), min_stoop(w, None, HAGRID_X_TOL)))
    for hd in (-25, 0, 20, 30, 40):
        print('    head %+d: least stoop %s' % (hd, min_stoop(w, hd)))
    for k, v in ANCHORS.items():
        print('  %s: %s' % (k, v))


if __name__ == '__main__':
    main()
