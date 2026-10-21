"""Builds the Letter memory's Privet Drive set: structure/privet_drive.nbt and the anchors letter_scene.py stages
the scenes `letter` and `letter_flood` with.

    python -I build_scripts/pensieve_scenes/privet_drive.py

The street: a suburban close (paving-slab pavements, grass verges with concrete lamp posts and young trees, kerbs
one block above the tarmac) leaving a main road at its west end and ending in a turning head at the east. Five
identical detached houses line the north side and four the south, the south row staggered by half a plot, with a
corner lot and a small green filling its ends; a twelfth house faces down the close from beyond the turning head
and an eleventh looks across the main road at its mouth, so every view along the street ends on a house, not the
void. Each house is two storeys of brick with a 2:1 tiled roof (verges, ridge cap, white fascias), a chimney stack
with a pot on the west gable, small gable windows, a ground-floor bay window with its own lean-to roof, a canopied
front door, white sills, an attached flat-roofed garage with a white up-and-over door, a driveway over a dropped
kerb, a paved front path, a lawn with rose bushes and flower borders, a privet hedge along the pavement and a fenced
back garden with a shed and a patio. Number 4 is the middle house of the north row and the only one with an
interior; the others hide their empty rooms behind net curtains (so do No. 4's upstairs windows).

Number 4 inside (ground floor complete, the upper floor a dressed shell), all at real block scale with double outer
walls (brick outside, plaster inside):
  - the hall straight back from the front door, the doormat and letter slot, the staircase rising along the east
    wall with a panelled string, a banister and a stairwell to the landing;
  - the cupboard under the stairs at child scale: 1 wide, 2 long, its ceiling the stepped underside of the stairs
    (2.5 at the door end down to 1.0 at the foot, lined with upside-down stairs), a 1x2 cot (a brown bed) that fills
    it, a pigeonhole shelf closing the foot end, a lantern for the bulb in a niche over the pillow and a door in the
    stair string;
  - the living room: fitted carpet with a rug, a brick chimney breast with a dark fireplace on a stone hearth, a
    mantel with ornaments, alcove units (a TV, a glass display cabinet), the bay window with curtains drawn back, a
    three-seat sofa on the back wall, Vernon's armchair facing the fireplace, a standard lamp;
  - the kitchen-diner across the back: fitted units, sink under the window, cooker, fridge, a pedestal table with
    six chairs, the back door.

Using the set from a scene (letter_scene.py):

    import privet_drive as PD
    'radius': PD.RADIUS,
    'set': {'templates': [{'id': 'hogwartsmagic:privet_drive', 'at': list(PD.TEMPLATE_AT)}],
            'time_of_day': PD.TIME_OF_DAY}

ANCHORS format
--------------
Every coordinate is a SCENE coordinate (template local + TEMPLATE_AT), the same frame as great_hall_set.py and
diagon_set.py. Number 4's ground floor (and the lawns, paths and pavements) are a block layer at scene y -1, so
anyone standing in the house, the garden or on the pavement stands at y 0; the road is one block lower (y -1).
Number 4 faces south (+z): its hall runs north from the front door. yaw is Minecraft's: 0 faces +Z (south), 90 -X
(west), 180 -Z (north), -90 +X (east). Seats are [x, seat_top_y, z, yaw] as in great_hall_set.py (a seated actor's
root goes at human_motion.seated_root_y(seat_top, scale) with the `seated` pose); the yaw is the way the sitter
faces.

  cupboard_bed           [x, y, z]  the lying boy's feet: the foot of the cot at the mattress top (y 0.5625)
  cupboard_bed_head      [x, y, z]  the head of the cot at the mattress top (the pillow end)
  cupboard_bed_yaw       float      the direction from the feet to the head (lay the boy along it)
  cupboard_eye           [x, y, z]  the first-person camera over the pillow, looking up at the stair underside
  cupboard_look_at       [x, y, z]  a point on the stair underside above the eye
  cupboard_door          {...}      the real door in the stair string (template blocks, closed):
                                    'lower'/'upper' cells, 'closed'/'open' [lower, upper] states. Block events
                                    place only default states, so to open it place minecraft:air at both cells
                                    and show two memory_block actors with the 'open' states there (or swing
                                    them). 'hinge' [x, y, z] is the south jamb's bottom on the hall face (the
                                    block door's hinge side), with 'yaw_closed' 90 / 'yaw_open' 180 for a
                                    plain memory_door leaf at 'leaf_scale'
                                    0.667 (0.67 wide, 2 tall) if a swinging leaf is wanted instead. 'outside'
                                    {pos, yaw}: the knocking spot in the hall, facing the door
  hall_mat               [x, y, z]  where letters land inside the front door (on the mat, y 0.0625)
  letter_slot            [x, y, z]  the slot on the front door's outer (street) face, y 0.9
  letter_slot_inner      [x, y, z]  the same slot on the door's inner face (a letter leaves it here)
  letter_plate           {...}      the brass letter plate, which the SCENE must add (no block reads as one on a
                                    door): 'block' a light weighted pressure plate state, 'scale' 0.3, and
                                    'inner' / 'outer' {pos, pitch}: memory_block actors (pos = the block centre)
                                    that stand the plate on the door's hall face (pitch 90) and street face
                                    (pitch -90), a 0.26 square centred on the slot at y 0.9
  front_door             [x, y, z]  the front door's lower cell (int corner; upper cell is y + 1)
  front_door_states      [lower, upper] the closed door's block states
  front_step             {pos, yaw} the postman's / Vernon's spot outside the door, facing it
  hall_inside_door       {pos, yaw} just inside the door on the hall side, on the mat (y 0.0625), facing the
                                    door (Vernon nails the slot from here)
  hall_spots             {name: {pos, yaw}} standing marks along the hall (foot_of_stairs, by_cupboard,
                                    kitchen_doorway, living_doorway, mid_hall)
  stairs_path            [[x, y, z], ...] feet points down the stair treads, landing to hall (Dudley thumps
                                    down over the cupboard)
  stairs_top             [x, y, z]  the landing at the head of the stairs (y 4)
  kitchen_table          [x, y, z]  the middle of the table top (y 1.1875)
  kitchen_seats          {name: seat} vernon (head of the table, facing west), petunia, dudley, boy, spare_n,
                                    spare_s
  vernon_chair           seat       = kitchen_seats['vernon']
  kitchen_spots          {name: {pos, yaw}} standing marks (by_sink, by_cooker, by_fridge, back_door, doorway)
  armchair               seat       Vernon's armchair on the east side, facing the fireplace (yaw 90)
  sofa_seats             [seat, seat, seat] the sofa on the back wall, facing the bay (yaw 0)
  living_spots           {name: {pos, yaw}} standing marks (boy_wall: the boy by the wall in the north-east
                                    corner, doorway, bay, hearth, centre)
  living_fireplace_mouth [x, y, z]  the fireplace opening on the room face of the breast, y 0 (the floor top):
                                    the letter_flood emitter
  living_fireplace_yaw   float      -90: the flood bursts east into the room
  living_room_box        [[x1, y1, z1], [x2, y2, z2]] min and max corners of the living room's clear air (the
                                    flood `box`); every cell in it is air (no furniture, no light block), and
                                    the fireplace interior lies outside it (the mouth is on its west face)
  street_wide_cam        {pos, look_at} the establishing shot: 9 up over the road 36 west of No. 4, looking
                                    up the close at it (the end house closes the vista)
  cameras                {name: {pos, look_at}} more framings (street_opposite: No. 4 square on from the far
                                    pavement; end_of_close, street_low, front_garden, hall_from_door,
                                    hall_from_kitchen, cupboard_out, living_wide, living_low, kitchen_wide,
                                    roofs_wide)
  roof_owl_spots         [[x, y, z], ...] 66 owl feet spots on every ridge cap, the chimney tops, garage roofs,
                                    porch canopies, bay roofs, hedges, lamp posts, the street sign and the
                                    green's bench; each on a solid top with 0.6 of air above
  owl_look_at            [x, y, z]  No. 4's front door (turn the owls' heads to it)
  no4_front              [x, y, z]  the front door's outer face at the doorstep, y 0
  RADIUS, TIME_OF_DAY    int

build(world) writes the set into a dict as {scene cell: full block state} and returns ANCHORS; write_nbt() checks
it and writes the template.

Asserts on every run: inside the template and the radius, physics clean, every standing anchor and owl spot on a
solid top (a full block, or a slab, stair, carpet or bed top at its real height) with air up to 1.8 (owls 0.6)
above it, every seat a bottom stair with two clear cells above and a clear cell where the legs go, the sitter facing
away from the back, the cupboard eye in air (the real shape: 0.1 all round it clear of the cot's 9/16 mattress and the
stepped ceiling), the cupboard closed on every side but the door with a ceiling rising 1.0 -> 2.5 foot to head, the
flood box all air (no furniture, no light block) over a solid floor with the mouth on its west face and the firebox
outside it and closed behind and above, the letter slot on the door, every camera in air.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import physics_problems, FREE, STRUCTURES  # noqa: E402
from great_hall_set import state, bid, Builder, write_template  # noqa: E402

OUT = os.path.join(STRUCTURES, 'privet_drive.nbt')
TIME_OF_DAY = 1000                     # early morning

# ---------------------------------------------------------------- the street (local frame)
# x runs along the street: 0 the junction house's back fence, 31..43 the main road the close leaves, 45..184 the
# two rows, 185..197 the turning head, 198..228 the end house facing back down the close. z: 0 the north row's back
# fences .. 76 the south row's. Local y = scene y + LY.
LY = 2
SIZE = (230, 26, 77)
PLOT = 28
HOUSE_W = 22                           # hx 0..16 the house, 17..21 the garage
N_X0 = [48 + PLOT * i for i in range(5)]       # north row: house hx 0 at these local x (plots x 45..184)
S_X0 = [62 + PLOT * i for i in range(4)]       # south row (rotated 180), staggered by half a plot (59..170)
NO4 = 2                                # number 4 is north row house 2
N_HF, S_HF = 24, 52                    # local z of each row's front wall (d 0)
PAVE_N = (32, 33)                      # pavements, verges, road (local z)
VERGE_N = 34
ROAD = (35, 41)
VERGE_S = 42
PAVE_S = (43, 44)
MAIN_X = (31, 43)                      # the main road the close leaves (runs along z): pavement 31-32, verge 33,
                                       # carriageway 34..40, verge 41, pavement 42-43
HEAD_X = (185, 195)                    # the turning head (carriageway z 33..43), pavement round it to x 197
END_W = (205, 30)                      # the end house facing west down the close (row W): front wall x, z of hx 0
END_E = (23, 46)                       # the junction house across the main road facing east (row E)
GREEN_X = (171, 184)                   # a small green on the south side by the turning head

# No. 4's front path at d -3 is the scene origin
TEMPLATE_AT = (-(N_X0[NO4] + 12), -LY, -(N_HF + 3))
RADIUS = 118

BRICK = 'minecraft:bricks'
TILE = 'deepslate_tile'
WHITE = 'minecraft:smooth_quartz'
PLASTER = 'minecraft:smooth_sandstone'      # cream emulsion on every inside face
GLASS = 'glass_pane'
NET = 'white_stained_glass_pane'
LEAVES = state('oak_leaves', persistent=True, distance=1, waterlogged=False)

REL = {'N': {'F': 'south', 'B': 'north', 'E': 'east', 'W': 'west'},
       'S': {'F': 'north', 'B': 'south', 'E': 'west', 'W': 'east'},
       'E': {'F': 'east', 'B': 'west', 'E': 'north', 'W': 'south'},
       'W': {'F': 'west', 'B': 'east', 'E': 'south', 'W': 'north'}}
YAW = {'south': 0.0, 'west': 90.0, 'north': 180.0, 'east': -90.0}


def lsc(p):
    """Local float point to scene."""
    return [round(p[0] + TEMPLATE_AT[0], 4), round(p[1] + TEMPLATE_AT[1], 4), round(p[2] + TEMPLATE_AT[2], 4)]


class Grid(Builder):
    def __init__(self):
        super().__init__({})


class House:
    """One house in its own frame: hx 0..21 across (west to east as seen from the street), d the depth from the
    front wall (d 0) to the back (d 16; negative d is the front garden), hy the scene y."""

    def __init__(self, g, x0, hf, row, full, seed):
        self.g, self.x0, self.hf, self.row, self.full = g, x0, hf, row, full
        self.rnd = random.Random(seed)
        self.rel = REL[row]

    # -- frames
    def p(self, hx, hy, d):
        """x0/hf: for rows N and S the local x of hx 0 (S: of hx 21) and the front wall's local z; for rows E
        and W the front wall's local x and the local z of hx 0."""
        if self.row == 'N':
            return (self.x0 + hx, hy + LY, self.hf - d)
        if self.row == 'S':
            return (self.x0 + HOUSE_W - 1 - hx, hy + LY, self.hf + d)
        if self.row == 'E':
            return (self.x0 - d, hy + LY, self.hf - hx)
        return (self.x0 + d, hy + LY, self.hf + hx)

    def pt(self, hx, hy, d):
        """A continuous house point (hx, d in blocks, cell hx spans hx..hx+1) to a scene point."""
        if self.row == 'N':
            loc = (self.x0 + hx, hy + LY, self.hf + 1 - d)
        elif self.row == 'S':
            loc = (self.x0 + HOUSE_W - hx, hy + LY, self.hf + d)
        elif self.row == 'E':
            loc = (self.x0 + 1 - d, hy + LY, self.hf + 1 - hx)
        else:
            loc = (self.x0 + d, hy + LY, self.hf + hx)
        return lsc(loc)

    def along_x(self):
        """The connection keys for something running along hx (a pane or fence across the house)."""
        return ('east', 'west') if self.row in 'NS' else ('north', 'south')

    def along_d(self):
        return ('north', 'south') if self.row in 'NS' else ('east', 'west')

    def axis_x(self):
        return 'x' if self.row in 'NS' else 'z'

    def cpt(self, hx, hy, d):
        """The centre of cell (hx, d) at height hy, scene."""
        return self.pt(hx + 0.5, hy, d + 0.5)

    def yaw(self, r):
        return YAW[self.rel[r]]

    def s(self, name, **props):
        out = {}
        for k, v in props.items():
            out[k] = self.rel[v] if k == 'facing' and v in self.rel else v
        return state(name, **out)

    def pane_x(self, name=GLASS):
        a, b = self.along_x()
        return state(name, **{a: True, b: True})

    def pane_d(self, name=GLASS):
        a, b = self.along_d()
        return state(name, **{a: True, b: True})

    def put(self, hx, hy, d, st):
        self.g.set(self.p(hx, hy, d), st)

    def get(self, hx, hy, d):
        return self.g.get(self.p(hx, hy, d))

    def fill(self, hx0, hy0, d0, hx1, hy1, d1, st):
        for hx in range(min(hx0, hx1), max(hx0, hx1) + 1):
            for hy in range(min(hy0, hy1), max(hy0, hy1) + 1):
                for d in range(min(d0, d1), max(d0, d1) + 1):
                    self.put(hx, hy, d, st() if callable(st) else st)

    def clear(self, hx0, hy0, d0, hx1, hy1, d1):
        self.fill(hx0, hy0, d0, hx1, hy1, d1, None)

    # ------------------------------------------------------------ the whole house
    def build(self):
        self.grounds()
        self.shell()
        self.roof()
        self.chimney()
        self.garage()
        self.front()
        self.windows()
        if self.full:
            self.interior()

    def grounds(self):
        """Front garden (d -1..-6), hedge (d -7), back garden (d 17..22), back fence (d 23), plot hx -3..24."""
        rnd = self.rnd
        grass = 'minecraft:grass_block'
        for hx in range(-3, 25):
            for d in range(-7, 24):
                self.put(hx, -1, d, grass)
        # the front path to the door, the doorstep, the driveway
        for d in range(-7, 0):
            self.put(12, -1, d, 'minecraft:smooth_stone')
        for hx in range(17, 22):
            for d in range(-7, 1):
                self.put(hx, -1, d, 'minecraft:polished_andesite')
        # the privet hedge along the pavement, gaps for the path and the drive; low hedges between plots
        for hx in range(-3, 25):
            if hx == 12 or 17 <= hx <= 21:
                continue
            self.put(hx, 0, -7, LEAVES)
        for d in range(-6, 0):
            self.put(-3, 0, d, LEAVES)
        # flower borders: under the front wall and along the inside of the hedge
        flowers = ('minecraft:poppy', 'minecraft:red_tulip', 'minecraft:pink_tulip', 'minecraft:oxeye_daisy',
                   'minecraft:allium', 'minecraft:azure_bluet', 'minecraft:white_tulip', 'minecraft:cornflower')
        for hx in list(range(0, 3)) + list(range(9, 12)) + list(range(13, 17)):
            if rnd.random() < 0.75:
                self.put(hx, 0, -1, rnd.choice(flowers))
        for hx in range(-2, 11):
            if rnd.random() < 0.55:
                self.put(hx, 0, -6, rnd.choice(flowers))
        for hx in (0, 2, 15):
            self.put(hx, 0, -1, state('rose_bush', half='lower'))
            self.put(hx, 1, -1, state('rose_bush', half='upper'))
        # back garden: fence round it, a shed corner, a rotary line post
        fence_x = self.pane_x('spruce_fence')
        fence_d = self.pane_d('spruce_fence')
        for hx in range(-3, 25):
            self.put(hx, 0, 23, fence_x)
        for d in range(17, 23):
            self.put(-3, 0, d, fence_d)
        self.put(-3, 0, 23, state('spruce_fence', **{self.rel['E']: True, self.rel['F']: True}))
        for hx in range(-2, 2):
            for d in range(20, 23):
                self.put(hx, 0, d, 'minecraft:spruce_planks')
                self.put(hx, 1, d, 'minecraft:spruce_planks')
                self.put(hx, 2, d, self.s('spruce_slab', type='bottom'))
        self.put(10, 0, 20, 'minecraft:iron_bars')
        self.put(10, 1, 20, 'minecraft:iron_bars')
        for hx in range(2, 12):
            self.put(hx, -1, 17, 'minecraft:smooth_stone')       # a patio along the back
        for hx in range(2, 12):
            self.put(hx, -1, 18, 'minecraft:smooth_stone')

    def shell(self):
        brick = BRICK
        plaster = PLASTER
        for hy in range(-1, 8):
            for d in range(0, 17):
                self.put(0, hy, d, brick)
                self.put(16, hy, d, brick)
            for hx in range(0, 17):
                self.put(hx, hy, 0, brick)
                self.put(hx, hy, 16, brick)
        for hy in range(0, 8):
            for d in range(1, 16):
                self.put(1, hy, d, plaster)
                self.put(15, hy, d, plaster)
            for hx in range(1, 16):
                self.put(hx, hy, 1, plaster)
                self.put(hx, hy, 15, plaster)
        floor = 'minecraft:spruce_planks'
        self.fill(1, -1, 1, 15, -1, 15, floor)
        self.fill(2, 3, 2, 14, 3, 14, 'minecraft:white_concrete')           # ceiling / first floor
        self.fill(2, 7, 2, 14, 7, 14, 'minecraft:white_concrete')           # upper ceiling
        if not self.full:
            # a spine wall so a window never shows the far side of the house
            self.fill(2, 0, 9, 14, 2, 9, plaster)
            self.fill(2, 4, 9, 14, 6, 9, plaster)
            self.fill(10, 0, 2, 10, 2, 8, plaster)

    def roof(self):
        """2:1 tiled roof, ridge along hx at d 8, eaves one block proud of the front and back walls."""
        tile_slab_b = state(TILE + '_slab', type='bottom', waterlogged=False)
        tile_slab_t = state(TILE + '_slab', type='top', waterlogged=False)
        tile_full = 'minecraft:deepslate_tiles'
        for d in range(-1, 18):
            k = min(d + 1, 17 - d)
            if k % 2 == 0:
                cell, st = 8 + k // 2, tile_slab_b
            else:
                cell, st = 8 + (k - 1) // 2, tile_slab_t
            for hx in (-1, 17):
                self.put(hx, cell, d, st)
            for hx in range(0, 17):
                gable = hx in (0, 16)
                if 0 <= d <= 16:
                    for hy in range(8, cell):
                        self.put(hx, hy, d, BRICK)
                self.put(hx, cell, d, tile_full if (gable and k % 2 == 1 and 0 <= d <= 16) else st)
        for hx in range(-1, 18):
            self.put(hx, 13, 8, tile_slab_b)                  # the ridge cap
            self.put(hx, 7, -1, state('smooth_quartz_slab', type='top', waterlogged=False))   # fascias
            self.put(hx, 7, 17, state('smooth_quartz_slab', type='top', waterlogged=False))

    def chimney(self):
        for hy in range(8, 15):
            for hx in (0, 1):
                self.put(hx, hy, 8, BRICK)
        for hx in (0, 1):
            self.put(hx, 15, 8, state('brick_slab', type='bottom', waterlogged=False))
        self.put(1, 15, 8, self.s('decorated_pot', facing='F', cracked=False, waterlogged=False))
        self.put(0, 15, 8, state('brick_slab', type='bottom', waterlogged=False))   # an owl perch

    def garage(self):
        """Attached single garage, hx 17..21, d 1..10, flat roof with a white fascia; white up-and-over door."""
        for hy in range(-1, 3):
            for d in range(1, 11):
                self.put(21, hy, d, BRICK)
            for hx in range(17, 22):
                self.put(hx, hy, 1, BRICK)
                self.put(hx, hy, 10, BRICK)
        self.fill(17, -1, 2, 20, -1, 9, 'minecraft:smooth_stone')
        for hx in range(17, 22):
            for d in range(0, 11):
                self.put(hx, 3, d, state(TILE + '_slab', type='bottom', waterlogged=False))
            self.put(hx, 2, 0, state('smooth_quartz_slab', type='top', waterlogged=False))
        door = state('quartz_pillar', axis=self.axis_x())
        for hx in range(18, 21):
            for hy in (0, 1):
                self.put(hx, hy, 1, door)

    def front(self):
        """The front door with its canopy and step, the bay window."""
        # front door (outer wall, slab flush with the facade), the reveal behind it
        lower = self.s('dark_oak_door', facing='B', half='lower', hinge='left', open=False, powered=False)
        upper = self.s('dark_oak_door', facing='B', half='upper', hinge='left', open=False, powered=False)
        self.put(12, 0, 0, lower)
        self.put(12, 1, 0, upper)
        self.put(12, 0, 1, None)
        self.put(12, 1, 1, None)
        self.door_states = [lower, upper]
        self.put(12, -1, 1, 'minecraft:spruce_planks')
        self.put(12, -1, 0, 'minecraft:polished_andesite')            # threshold
        # canopy on brackets
        for hx in (11, 12, 13):
            self.put(hx, 3, -1, self.s(TILE + '_stairs', facing='B', half='bottom', shape='straight',
                                       waterlogged=False))
        for hx in (11, 13):
            self.put(hx, 2, -1, self.s('spruce_stairs', facing='B', half='top', shape='straight',
                                       waterlogged=False))
        self.put(12, -1, -1, 'minecraft:smooth_stone')
        # the bay: posts, base, glass, its own lean-to roof; the walls behind it open (hx 4..7)
        for hy in range(0, 3):
            self.put(3, hy, -1, WHITE)
            self.put(8, hy, -1, WHITE)
        for hx in range(4, 8):
            self.put(hx, 0, -1, BRICK)
            self.put(hx, 1, -1, self.pane_x())
            self.put(hx, 2, -1, self.pane_x())
            for d in (0, 1):
                for hy in range(0, 3):
                    self.put(hx, hy, d, None)
                self.put(hx, -1, d, 'minecraft:spruce_planks')
        for hx in range(3, 9):
            self.put(hx, 3, -1, self.s(TILE + '_stairs', facing='B', half='bottom', shape='straight',
                                       waterlogged=False))

    def window(self, hx0, hx1, hy0, hy1, d, inner, sill=True):
        """A window in the outer wall at d (0 front, 16 back) with a reveal in the inner layer: `inner` is 'air'
        (No. 4's rooms), 'net' (net curtains) or a block state."""
        out = -1 if d == 0 else 17
        din = 1 if d == 0 else 15
        for hx in range(hx0, hx1 + 1):
            for hy in range(hy0, hy1 + 1):
                self.put(hx, hy, d, self.pane_x())
                self.put(hx, hy, din, None if inner == 'air' else self.pane_x(NET) if inner == 'net' else inner)
            if sill:
                self.put(hx, hy0 - 1, out, state('smooth_quartz_slab', type='top', waterlogged=False))

    def side_window(self, hx, d0, d1, hy0, hy1, inner):
        out = -1 if hx == 0 else 17
        hin = 1 if hx == 0 else 15
        for d in range(d0, d1 + 1):
            for hy in range(hy0, hy1 + 1):
                self.put(hx, hy, d, self.pane_d())
                self.put(hin, hy, d, None if inner == 'air' else self.pane_d(NET))
            self.put(out, hy0 - 1, d, state('smooth_quartz_slab', type='top', waterlogged=False))

    def windows(self):
        up = 'net'
        down = 'air' if self.full else 'net'
        if not self.full:
            for hx in range(4, 8):                          # net curtains across the bay
                for hy in (1, 2):
                    self.put(hx, hy, 1, self.pane_x(NET))
        self.window(4, 7, 5, 6, 0, up)                      # front bedroom
        self.window(12, 13, 5, 6, 0, up)                    # landing
        self.window(14, 14, 1, 2, 0, down)                  # the hall's side light
        self.window(5, 6, 1, 2, 16, down)                   # kitchen, over the sink
        self.window(3, 4, 5, 6, 16, up)                     # back bedrooms
        self.window(11, 12, 5, 6, 16, up)
        # back door (glazed)
        lower = self.s('oak_door', facing='F', half='lower', hinge='right', open=False, powered=False)
        upper = self.s('oak_door', facing='F', half='upper', hinge='right', open=False, powered=False)
        self.put(11, 0, 16, lower)
        self.put(11, 1, 16, upper)
        self.put(11, 0, 15, None)
        self.put(11, 1, 15, None)
        self.put(11, -1, 15, 'minecraft:spruce_planks')
        self.put(11, -1, 17, 'minecraft:smooth_stone')
        self.side_window(16, 12, 13, 1, 2, down)            # kitchen side window (east, beyond the garage)
        self.side_window(0, 12, 13, 1, 2, down)             # kitchen side window (west)
        self.side_window(0, 12, 12, 5, 6, up)               # back bedroom (west gable)
        self.side_window(16, 4, 4, 5, 6, up)                # landing (east gable, over the garage roof)
        self.side_window(0, 3, 3, 5, 6, up)                 # front bedroom (west gable)

    # ------------------------------------------------------------ No. 4 inside
    def interior(self):
        self.hall()
        self.cupboard()
        self.living()
        self.kitchen()
        self.upstairs()
        self.lights()

    def hall(self):
        plaster = PLASTER
        # partitions: living | hall (hx 10, d 2..8), the back wall of hall and living (d 9)
        self.fill(10, 0, 2, 10, 2, 8, plaster)
        self.fill(2, 0, 9, 14, 2, 9, plaster)
        self.clear(10, 0, 3, 10, 1, 3)                      # living-room doorway
        self.clear(12, 0, 9, 12, 1, 9)                      # kitchen doorway
        # hall floor and the mat
        self.fill(11, -1, 2, 14, -1, 8, 'minecraft:spruce_planks')
        self.put(12, -1, 9, 'minecraft:spruce_planks')
        self.put(10, -1, 3, 'minecraft:spruce_planks')
        self.put(12, 0, 1, state('gray_carpet'))                  # the doormat, grey on the boards
        self.put(12, 0, 2, state('gray_carpet'))
        self.put(12, 2, 0, self.pane_x())                          # a fanlight over the front door
        self.put(12, 2, 1, None)
        for hy in (0, 1, 2):                                       # a glazed side panel beside it
            self.put(13, hy, 0, self.pane_x())
            self.put(13, hy, 1, None)
        self.frame(11, 2, 6, 'W', 'dark_oak')                      # a picture over the radiator
        # stairs along the east wall (hx 14), rising north: treads d 4..7 at hy 0..3
        for i, d in enumerate(range(4, 8)):
            self.put(14, i, d, self.s('spruce_stairs', facing='B', half='bottom', shape='straight',
                                      waterlogged=False))
        # the string (hx 13): panelled under the treads, the banister on top
        string = 'minecraft:birch_planks'
        for i, d in enumerate(range(4, 8)):
            for hy in range(0, i + 1):
                self.put(13, hy, d, string)
            self.put(13, i + 1, d, state('dark_oak_fence', north=True, south=True, waterlogged=False))
        self.put(13, 0, 3, state('dark_oak_fence', north=True, waterlogged=False))         # newel post
        # under the first two treads: closed
        self.put(14, 0, 5, string)
        # behind the cupboard: solid to the kitchen wall
        self.fill(13, 0, 8, 14, 2, 8, plaster)
        # the stairwell through the first floor
        self.clear(13, 3, 4, 14, 3, 6)
        self.put(13, 3, 6, state('dark_oak_fence', north=True, south=True, waterlogged=False))
        self.put(13, 4, 7, state('dark_oak_fence', north=True, south=True, waterlogged=False))
        self.put(13, 3, 7, string)
        # dressing: a radiator, a telephone table, coat hooks
        self.put(11, 0, 6, self.s('iron_trapdoor', facing='E', half='bottom', open=True, powered=False,
                                  waterlogged=False))                   # radiator against the wall
        self.put(11, 0, 2, self.s('spruce_trapdoor', facing='E', half='top', open=False, powered=False,
                                  waterlogged=False))
        self.put(14, 0, 2, state('spruce_fence', waterlogged=False))
        self.put(14, 1, 2, state('potted_fern'))

    def cupboard(self):
        """Under the stairs at hx 14: the cot fills d 6..7 (foot at d 6), the ceiling is the stair underside lined
        with upside-down stairs, the door in the string at d 7."""
        lining = 'spruce_stairs'
        self.put(14, 1, 6, self.s(lining, facing='F', half='top', shape='straight', waterlogged=False))
        self.put(14, 2, 7, self.s(lining, facing='F', half='top', shape='straight', waterlogged=False))
        self.put(14, 0, 6, self.s('brown_bed', facing='B', part='foot', occupied=False))
        self.put(14, 0, 7, self.s('brown_bed', facing='B', part='head', occupied=False))
        self.put(14, -1, 6, 'minecraft:spruce_planks')
        self.put(14, -1, 7, 'minecraft:spruce_planks')
        # boards on the cupboard's outer side and back, so it reads as a cupboard, not a plastered room
        for d in (6, 7):
            for hy in (0, 1, 2):
                self.put(15, hy, d, 'minecraft:spruce_planks')
        self.put(14, 0, 8, 'minecraft:spruce_planks')
        self.put(14, 2, 8, 'minecraft:spruce_planks')
        # the bulb, in a niche in the back wall over the pillow
        self.put(14, 1, 8, state('lantern', hanging=False, waterlogged=False))
        # the shelf: a pigeonhole unit closing the foot end, under the second tread
        self.put(14, 0, 5, self.s('chiseled_bookshelf', facing='B', slot_0_occupied=True, slot_1_occupied=False,
                                  slot_2_occupied=True, slot_3_occupied=False, slot_4_occupied=True,
                                  slot_5_occupied=False))
        # the door in the string, slab on the hall face
        lower = self.s('spruce_door', facing='E', half='lower', hinge='right', open=False, powered=False)
        upper = self.s('spruce_door', facing='E', half='upper', hinge='right', open=False, powered=False)
        self.put(13, 0, 7, lower)
        self.put(13, 1, 7, upper)
        self.cup_states = {'closed': [lower, upper],
                           'open': [lower.replace('open=false', 'open=true'), upper.replace('open=false', 'open=true')]}

    def frame(self, hx, hy, d, back, wood='dark_oak'):
        """A framed picture: an open trapdoor flat against the wall behind it (`back` is the side the wall is
        on), 3/16 thick."""
        face = {'W': 'E', 'E': 'W', 'F': 'B', 'B': 'F'}[back]
        self.put(hx, hy, d, self.s(wood + '_trapdoor', facing=face, half='top', open=True, powered=False,
                                   waterlogged=False))

    def living(self):
        """Living room hx 2..9, d 2..8, the bay alcove hx 4..7, d 0..1. The flood box is the clear middle (hx 3..8,
        d 2..7, hy 0..2); everything here stands in the strips round it (hx 2, hx 9, d 8, the bay) or is flush in
        the walls, floor and ceiling."""
        carpet = 'minecraft:brown_wool'
        self.fill(2, -1, 2, 9, -1, 8, carpet)
        for hx in range(4, 8):
            for d in (0, 1):
                self.put(hx, -1, d, carpet)
        # a rug laid flush in the fitted carpet in front of the hearth
        for hx in range(4, 8):
            for d in range(3, 8):
                edge = hx in (4, 7) or d in (3, 7)
                self.put(hx, -1, d, 'minecraft:brown_terracotta' if edge else 'minecraft:white_terracotta')
        # the bay: half-nets in the lower lights, curtains drawn back under a pelmet
        for hx in range(4, 8):
            self.put(hx, 1, -1, self.pane_x(NET))
            self.put(hx, 2, 1, 'minecraft:pink_terracotta')                 # pelmet across the bay head
        for hx in (4, 7):
            self.put(hx, 0, 0, None)
            for hy in (1, 2):
                self.put(hx, hy, 0, 'minecraft:pink_terracotta')
        # chimney breast hx 2, d 4..6, standing free between the alcoves
        for d in (4, 5, 6):
            for hy in (0, 1, 2):
                self.put(2, hy, d, BRICK)
                self.put(1, hy, d, BRICK)
        # the fire surround: quartz pilasters and a quartz-lined head round a black firebox
        self.put(2, 0, 4, WHITE)
        self.put(2, 0, 6, WHITE)
        self.put(2, 0, 5, None)                                     # the mouth
        self.put(1, 0, 5, None)                                     # the firebox, in the wall
        self.put(0, 0, 5, 'minecraft:polished_blackstone_bricks')  # its fireback (the outer wall)
        self.put(1, 0, 4, 'minecraft:polished_blackstone_bricks')  # its cheeks
        self.put(1, 0, 6, 'minecraft:polished_blackstone_bricks')
        self.put(1, -1, 5, 'minecraft:polished_blackstone')
        self.put(2, -1, 5, 'minecraft:polished_blackstone')
        for d in (4, 5, 6):
            self.put(1, 1, d, WHITE)                                # the surround's head, under the shelf
        # hearth in front of the breast, flush with the carpet
        for d in (4, 5, 6):
            self.put(3, -1, d, 'minecraft:polished_andesite')
        # the mantel shelf, one wider than the breast each side, ornaments on its ends
        for d in range(3, 8):
            self.put(2, 1, d, self.s('dark_oak_slab', type='top', waterlogged=False))
        self.put(2, 2, 3, state('candle', candles=3, lit=False, waterlogged=False))
        self.put(2, 2, 7, state('potted_red_tulip'))
        # over the mantel the breast carries on to the ceiling with a picture set in its face
        self.put(2, 2, 4, BRICK)
        self.put(2, 2, 6, BRICK)
        self.frame(2, 2, 5, 'W', 'dark_oak')
        # the alcoves: the TV in the front corner, a glass display cabinet in the back one, pictures over them
        self.put(2, 0, 2, self.s('spruce_trapdoor', facing='E', half='top', open=False, powered=False,
                                 waterlogged=False))
        self.put(2, 1, 2, 'minecraft:black_concrete')               # the television
        self.frame(2, 2, 2, 'W', 'dark_oak')
        self.put(2, 0, 8, 'minecraft:dark_oak_planks')
        self.put(2, 1, 8, 'minecraft:glass')
        self.put(2, 2, 8, self.s('dark_oak_slab', type='bottom', waterlogged=False))
        # three-seat sofa on the back wall (d 8), facing the front, arms at hx 4 and 8
        sofa = 'polished_granite_stairs'
        for hx in (5, 6, 7):
            self.put(hx, 0, 8, self.s(sofa, facing='B', half='bottom', shape='straight', waterlogged=False))
        self.put(4, 0, 8, self.s('dark_oak_trapdoor', facing='W', half='bottom', open=True, powered=False,
                                 waterlogged=False))
        self.put(8, 0, 8, self.s('dark_oak_trapdoor', facing='E', half='bottom', open=True, powered=False,
                                 waterlogged=False))
        # a sideboard in the corner beside the sofa
        self.put(3, 0, 8, self.s('dark_oak_trapdoor', facing='F', half='top', open=False, powered=False,
                                 waterlogged=False))
        self.put(3, 1, 8, state('potted_fern'))
        # Vernon's armchair on the east side (hx 9, d 5) facing the fireplace, arms at d 4 and d 6
        self.put(9, 0, 5, self.s(sofa, facing='E', half='bottom', shape='straight', waterlogged=False))
        self.put(9, 0, 4, self.s('dark_oak_trapdoor', facing='F', half='bottom', open=True, powered=False,
                                 waterlogged=False))
        self.put(9, 0, 6, self.s('dark_oak_trapdoor', facing='B', half='bottom', open=True, powered=False,
                                 waterlogged=False))
        # the photo wall: Dudley, framed, over the armchair and over the sofa
        self.frame(9, 2, 4, 'E', 'dark_oak')
        self.frame(9, 2, 6, 'E', 'dark_oak')
        self.frame(5, 2, 8, 'B', 'dark_oak')
        self.frame(7, 2, 8, 'B', 'dark_oak')
        # a lamp table in the corner, a pot plant by the door
        self.put(9, 0, 7, state('dark_oak_fence', waterlogged=False))
        self.put(9, 1, 7, state('lantern', hanging=False, waterlogged=False))
        self.put(9, 0, 2, state('potted_azalea_bush'))
        # the ceiling: a quartz band round the edge for the cornice, a rose round the light
        for hx in range(2, 10):
            for d in range(2, 9):
                if hx in (2, 9) or d in (2, 8):
                    self.put(hx, 3, d, WHITE)
        for hx, d in ((4, 5), (6, 5), (5, 4), (5, 6)):
            self.put(hx, 3, d, 'minecraft:chiseled_quartz_block')

    def kitchen(self):
        """Kitchen-diner hx 2..14, d 10..14."""
        self.fill(2, -1, 10, 14, -1, 14, 'minecraft:polished_diorite')
        tiles = 'minecraft:white_concrete'
        for hy in range(0, 3):
            for hx in range(2, 15):
                if self.get(hx, hy, 15) == PLASTER:
                    self.put(hx, hy, 15, tiles)
        # units along the back wall (d 14), hx 2..9: fridge, units, sink under the window, cooker
        for hx in range(2, 10):
            self.put(hx, 0, 14, 'minecraft:birch_planks')
        self.put(2, 0, 14, 'minecraft:white_concrete')
        self.put(2, 1, 14, 'minecraft:white_concrete')             # fridge
        self.put(5, 0, 14, state('water_cauldron', level=3))     # sink
        self.put(6, 0, 14, state('cauldron'))
        self.put(8, 0, 14, self.s('smoker', facing='F', lit=False))  # cooker
        for hx in (3, 4, 7, 9):
            self.put(hx, 2, 14, self.s('birch_trapdoor', facing='F', half='bottom', open=True, powered=False,
                                       waterlogged=False))
        # units down the west wall (hx 2), d 11..13
        for d in (11, 12, 13):
            self.put(2, 0, d, 'minecraft:birch_planks')
        self.put(2, 1, 11, state('potted_red_tulip'))
        # the pedestal table, hx 6..8 at d 12: a birch board top at hy 1 on a post under the middle
        for hx in (6, 7, 8):
            self.put(hx, 1, 12, state('birch_trapdoor', facing='north', half='bottom', open=False, powered=False,
                                      waterlogged=False))
        self.put(7, 0, 12, state('birch_fence', waterlogged=False))
        # chairs: backs away from the table
        ch = 'spruce_stairs'
        self.chairs = {
            'vernon': (9, 12, 'E'),       # head of the table, sits facing W
            'boy': (5, 12, 'W'),
            'petunia': (6, 11, 'F'),      # front side, faces B
            'spare_s': (8, 11, 'F'),
            'dudley': (8, 13, 'B'),       # back side, faces F
            'spare_n': (6, 13, 'B'),
        }
        for name, (hx, d, back) in self.chairs.items():
            self.put(hx, 0, d, self.s(ch, facing=back, half='bottom', shape='straight', waterlogged=False))
        # a pot plant on the back-door side, the bin
        self.put(14, 0, 14, 'minecraft:white_concrete')
        self.put(14, 1, 14, 'minecraft:white_concrete')            # tall cupboard

    def upstairs(self):
        """The first floor as a dressed shell: landing, three rooms, the stairwell balustrade."""
        plaster = PLASTER
        self.fill(2, 4, 2, 14, 6, 14, None)
        self.fill(10, 4, 2, 10, 6, 8, plaster)
        self.fill(2, 4, 9, 14, 6, 9, plaster)
        self.fill(8, 4, 10, 8, 6, 14, plaster)
        self.clear(10, 4, 3, 10, 5, 3)
        self.clear(12, 4, 9, 12, 5, 9)
        self.clear(6, 4, 9, 6, 5, 9)
        for hx in range(11, 15):
            for d in range(2, 9):
                if self.get(hx, 3, d) == 'minecraft:white_concrete':
                    self.put(hx, 4, d, state('light_gray_carpet'))
        # balustrade round the stairwell (hx 13..14, d 4..6): along hx 12 and across d 3
        for d in (4, 5, 6):
            self.put(12, 4, d, state('dark_oak_fence', north=True, south=True, waterlogged=False))
        for hx in (12, 13, 14):
            self.put(hx, 4, 3, state('dark_oak_fence', east=True, west=True, waterlogged=False))
        self.put(13, 4, 7, state('dark_oak_fence', north=True, waterlogged=False))
        self.put(14, 4, 8, None)
        self.put(13, 4, 8, state('light_gray_carpet'))
        # a bed in the front bedroom, a wardrobe
        self.put(5, 4, 6, self.s('white_bed', facing='B', part='foot', occupied=False))
        self.put(5, 4, 7, self.s('white_bed', facing='B', part='head', occupied=False))
        self.put(6, 4, 6, self.s('white_bed', facing='B', part='foot', occupied=False))
        self.put(6, 4, 7, self.s('white_bed', facing='B', part='head', occupied=False))
        for d in (6, 7):
            for hy in (4, 5):
                self.put(2, hy, d, 'minecraft:dark_oak_planks')

    def lights(self):
        """Morning: daylight through the glass, light blocks where the camera would find the rooms dark, never
        inside the flood box."""
        lt = lambda lv: state('light', level=lv, waterlogged=False)
        for hx, hy, d, lv in ((12, 2, 2, 13), (12, 1, 1, 12), (12, 2, 6, 11), (11, 2, 8, 10), (14, 2, 3, 11),
                              (9, 2, 3, 12), (9, 2, 8, 12), (9, 2, 5, 11), (6, 2, 8, 12), (3, 2, 8, 10),
                              (5, 2, 0, 10), (2, 2, 3, 10),
                              (4, 2, 12, 12), (10, 2, 12, 12), (13, 2, 11, 12), (7, 2, 13, 11),
                              (12, 6, 5, 10), (5, 6, 4, 9), (11, 6, 12, 9)):
            if self.get(hx, hy, d) is None:
                self.put(hx, hy, d, lt(lv))
        self.put(5, 3, 5, 'minecraft:pearlescent_froglight')       # living-room ceiling light
        self.put(7, 3, 12, 'minecraft:pearlescent_froglight')      # kitchen
        self.put(12, 3, 5, 'minecraft:pearlescent_froglight')      # hall
        self.put(12, 3, 2, 'minecraft:pearlescent_froglight')      # hall, by the front door
        # a picture rail: the top course of every plastered wall downstairs in a second cream
        for hx in range(1, 16):
            for d in range(1, 16):
                if self.get(hx, 2, d) == PLASTER:
                    self.put(hx, 2, d, 'minecraft:cut_sandstone')


# ---------------------------------------------------------------- the street
def tree(g, x, z, h, rnd, base_y=0):
    """A young street tree: a trunk and a rounded crown (local x, z; base_y scene)."""
    for y in range(h):
        g.set((x, base_y + y + LY, z), state('oak_log', axis='y'))
    top = base_y + h + LY
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in range(-2, 2):
                r = dx * dx + dz * dz + (dy + 0.5) ** 2 * 1.3
                if r <= 4.6 and not (dx == 0 and dz == 0 and dy < 0) and rnd.random() > 0.08:
                    p = (x + dx, top + dy, z + dz)
                    if g.get(p) is None:
                        g.set(p, LEAVES)


def lamp_post(g, x, z, dx, dz):
    """A grey concrete lamp column with an arm over the road (dx, dz: the arm direction) and a lantern under it."""
    for y in range(0, 6):
        g.set((x, y + LY, z), state('andesite_wall', up=True, north='none', south='none', east='none',
                                    west='none', waterlogged=False))
    g.set((x, 6 + LY, z), 'minecraft:polished_andesite')
    g.set((x + dx, 6 + LY, z + dz), state('polished_andesite_slab', type='bottom', waterlogged=False))
    g.set((x + dx, 5 + LY, z + dz), state('lantern', hanging=True, waterlogged=False))
    return [x + 0.5, 7.0 + LY, z + 0.5]


def ground(g, x, z, block):
    """Sets the ground at (x, z): the road one block down (nothing at the ground layer), everything else at it."""
    if block == 'road':
        g.set((x, -1 + LY, z), None)
        g.set((x, -2 + LY, z), 'minecraft:gray_concrete')
    else:
        g.set((x, -2 + LY, z), None)
        g.set((x, -1 + LY, z), block)


PAVE = 'minecraft:smooth_stone'
GRASS = 'minecraft:grass_block'
DRIVE = 'minecraft:polished_andesite'


def build_street(g):
    rnd = random.Random(4)
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            ground(g, x, z, GRASS)
    # the close: pavements, verges, carriageway from the main road to the turning head
    for x in range(MAIN_X[0], HEAD_X[1] + 1):
        for z in range(PAVE_N[0], PAVE_S[1] + 1):
            if PAVE_N[0] <= z <= PAVE_N[1] or PAVE_S[0] <= z <= PAVE_S[1]:
                ground(g, x, z, PAVE)
            elif z in (VERGE_N, VERGE_S):
                ground(g, x, z, GRASS)
            else:
                ground(g, x, z, 'road')
    # the main road the close leaves, running the length of the set along z
    x0 = MAIN_X[0]
    for z in range(SIZE[2]):
        for x in range(MAIN_X[0], MAIN_X[1] + 1):
            k = x - x0
            if k in (0, 1, 11, 12):
                ground(g, x, z, PAVE)
            elif k in (2, 10):
                ground(g, x, z, PAVE if PAVE_N[0] - 2 <= z <= PAVE_S[1] + 2 else GRASS)
            else:
                ground(g, x, z, 'road')
    # the mouth of the close: carriageway through the main road's east pavement
    for x in range(MAIN_X[0] + 10, MAIN_X[1] + 1):
        for z in range(ROAD[0], ROAD[1] + 1):
            ground(g, x, z, 'road')
    # the turning head: carriageway z 33..43, pavement round it
    for x in range(HEAD_X[0], HEAD_X[1] + 3):
        for z in range(PAVE_N[0] - 1, PAVE_S[1] + 2):
            if x <= HEAD_X[1] and PAVE_N[0] + 1 <= z <= PAVE_S[0] - 1:
                ground(g, x, z, 'road')
            else:
                ground(g, x, z, PAVE)
    owls = []
    # lamp posts on the close's verges (clear of the driveways) and on the main road
    for x in (58, 86, 130, 158):
        owls.append(lamp_post(g, x, VERGE_N, 0, 1))
    for x in (74, 102, 132, 160):
        owls.append(lamp_post(g, x, VERGE_S, 0, -1))
    for z in (8, 62):
        owls.append(lamp_post(g, MAIN_X[0] + 2, z, 1, 0))
    for z in (20, 72):
        owls.append(lamp_post(g, MAIN_X[0] + 10, z, -1, 0))
    # young trees on the verges between the lamps
    for x in (72, 144, 172):
        tree(g, x, VERGE_N, 4, rnd)
    for x in (98, 125, 147 + 10):
        tree(g, x, VERGE_S, 4, rnd)
    for z in (2, 50):
        tree(g, MAIN_X[0] + 2, z, 4, rnd)
    # the street sign on the north verge at the mouth of the close: a white plate on two black posts
    sx = MAIN_X[1] + 3
    for x in (sx, sx + 2):
        g.set((x, 0 + LY, VERGE_N), state('polished_blackstone_wall', up=True, north='none', south='none',
                                          east='none', west='none', waterlogged=False))
    for x in range(sx, sx + 3):
        g.set((x, 1 + LY, VERGE_N), WHITE)
    owls.append([sx + 1.5, 2.0 + LY, VERGE_N + 0.5])
    # dropped kerbs: each driveway crosses the pavement and verge in andesite
    for x0 in N_X0:
        for hx in range(17, 22):
            for z in range(PAVE_N[0], VERGE_N + 1):
                ground(g, x0 + hx, z, DRIVE)
    for x0 in S_X0:
        for hx in range(17, 22):
            for z in range(VERGE_S, PAVE_S[1] + 1):
                ground(g, x0 + HOUSE_W - 1 - hx, z, DRIVE)
    for hx in range(17, 22):                                   # the junction house drives over the main road verge
        for x in range(MAIN_X[0], MAIN_X[0] + 3):
            ground(g, x, END_E[1] - hx, DRIVE)
        for x in range(HEAD_X[1] + 1, HEAD_X[1] + 3):          # the end house over the head's pavement
            ground(g, x, END_W[1] + hx, DRIVE)
    # the corner lot by the junction (south side, before the first south plot): hedged, two trees
    for x in range(MAIN_X[1] + 1, S_X0[0] - 3):
        g.set((x, 0 + LY, PAVE_S[1] + 1), LEAVES)
    for z in range(PAVE_S[1] + 1, SIZE[2]):
        g.set((MAIN_X[1] + 1, 0 + LY, z), LEAVES)
    tree(g, MAIN_X[1] + 6, 54, 6, rnd)
    tree(g, MAIN_X[1] + 11, 66, 5, rnd)
    # the green by the turning head: a bench, two trees, a hedge behind
    gx0, gx1 = GREEN_X
    for x in range(gx0, gx1 + 1):
        g.set((x, 0 + LY, SIZE[2] - 2), LEAVES)
    tree(g, gx0 + 4, 56, 5, rnd)
    tree(g, gx1 - 3, 66, 5, rnd)
    for x in (gx0 + 6, gx0 + 7):
        g.set((x, 0 + LY, 48), state('spruce_stairs', facing='south', half='bottom', shape='straight',
                                     waterlogged=False))
    owls.append([gx0 + 6.5, 0.5 + LY, 48.5])
    return owls


def build_grid():
    """The set's template grid {local (x, y, z): block state}, the houses and the street's owl perches."""
    g = Grid()
    houses = []
    for i, x0 in enumerate(N_X0):
        houses.append(House(g, x0, N_HF, 'N', i == NO4, 100 + i))
    for i, x0 in enumerate(S_X0):
        houses.append(House(g, x0, S_HF, 'S', False, 200 + i))
    houses.append(House(g, END_W[0], END_W[1], 'W', False, 300))
    houses.append(House(g, END_E[0], END_E[1], 'E', False, 301))
    street_owls = build_street(g)
    for h in houses:
        h.build()
    # woods round the junction house and behind the end house, where the plots stop
    rnd = random.Random(9)
    for x, z in ((4, 4), (14, 8), (6, 16), (8, 58), (20, 64), (5, 72), (16, 74), (210, 6), (222, 14),
                 (204, 20), (214, 62), (224, 70), (206, 72), (196, 6), (196, 70)):
        tree(g, x, z, 5 + rnd.randint(0, 2), rnd)
    return g, houses, street_owls


# ---------------------------------------------------------------- anchors
def anchors(houses, street_owls):
    h = houses[NO4]
    A = {}
    # cupboard: cot hx 14, foot d 6, head d 7, mattress top 9/16
    top = 9 / 16.0
    A['cupboard_bed'] = h.pt(14.5, top, 6.15)
    A['cupboard_bed_head'] = h.pt(14.5, top, 7.8)
    A['cupboard_bed_yaw'] = h.yaw('B')
    A['cupboard_eye'] = h.pt(14.5, 0.86, 7.55)
    A['cupboard_look_at'] = h.pt(14.5, 2.4, 6.6)
    lower = h.p(13, 0, 7)
    lc = [lower[0] + TEMPLATE_AT[0], lower[1] + TEMPLATE_AT[1], lower[2] + TEMPLATE_AT[2]]
    A['cupboard_door'] = {
        'lower': lc, 'upper': [lc[0], lc[1] + 1, lc[2]],
        'closed': h.cup_states['closed'], 'open': h.cup_states['open'],
        # a memory_door leaf instead: hinged like the block door, on the hall face at the south jamb, the leaf
        # spanning north (-Z) when closed (yaw 90), swung open into the hall (-X) at yaw 180
        'hinge': h.pt(13.0, 0, 7.0), 'leaf_scale': 0.667, 'yaw_closed': 90.0, 'yaw_open': 180.0,
        'outside': {'pos': h.cpt(12, 0, 7), 'yaw': h.yaw('E')},
    }
    A['hall_mat'] = h.pt(12.5, 1 / 16.0, 1.5)
    A['letter_slot'] = h.pt(12.5, 0.9, 0.0)
    A['letter_slot_inner'] = h.pt(12.5, 0.9, 3 / 16.0)
    fd = h.p(12, 0, 0)
    A['front_door'] = [fd[0] + TEMPLATE_AT[0], 0, fd[2] + TEMPLATE_AT[2]]
    A['front_door_states'] = h.door_states
    A['front_step'] = {'pos': h.cpt(12, 0, -1), 'yaw': h.yaw('B')}
    A['no4_front'] = h.pt(12.5, 0, 0.0)
    A['hall_inside_door'] = {'pos': h.cpt(12, 1 / 16.0, 2), 'yaw': h.yaw('F')}
    # the brass letter plate: no block reads as one on a door, so the scene adds it as a memory_block actor on
    # each face (path = the block centre, scale about it). A light weighted pressure plate turned on its side:
    # pitch +90 lays its plate on the block's north face (SKILL.md: positive pitch tips the south face down),
    # -90 on the south face; at scale 0.3 the plate is 0.26 square and 0.02 thick, centred on the slot.
    s_ = 0.3
    slot = A['letter_slot']
    A['letter_plate'] = {
        'block': 'minecraft:light_weighted_pressure_plate[power=0]', 'scale': s_,
        'inner': {'pos': [slot[0], 0.9, round(A['letter_slot_inner'][2] - s_ / 16 + s_ / 2, 5)], 'pitch': 90.0},
        'outer': {'pos': [slot[0], 0.9, round(slot[2] - s_ * 7 / 16, 5)], 'pitch': -90.0},
    }
    A['hall_spots'] = {
        'foot_of_stairs': {'pos': h.cpt(14, 0, 3), 'yaw': h.yaw('B')},
        'by_cupboard': {'pos': h.cpt(12, 0, 7), 'yaw': h.yaw('E')},
        'kitchen_doorway': {'pos': h.cpt(12, 0, 8), 'yaw': h.yaw('B')},
        'living_doorway': {'pos': h.cpt(11, 0, 3), 'yaw': h.yaw('W')},
        'mid_hall': {'pos': h.cpt(11, 0, 5), 'yaw': h.yaw('F')},
    }
    A['stairs_path'] = [h.cpt(14, 4, 8)] + [h.pt(14.5, i + 0.5, 4 + i + 0.25) for i in (3, 2, 1, 0)] + [
        h.cpt(14, 0, 3)]
    A['stairs_top'] = h.cpt(14, 4, 8)
    # kitchen
    A['kitchen_table'] = h.pt(7.5, 1.1875, 12.5)
    seats = {}
    for name, (hx, d, back) in h.chairs.items():
        face = {'E': 'W', 'W': 'E', 'F': 'B', 'B': 'F'}[back]
        seats[name] = h.cpt(hx, 0.5, d) + [h.yaw(face)]
    A['kitchen_seats'] = seats
    A['vernon_chair'] = seats['vernon']
    A['kitchen_spots'] = {
        'by_sink': {'pos': h.cpt(5, 0, 13), 'yaw': h.yaw('B')},
        'by_cooker': {'pos': h.cpt(9, 0, 13), 'yaw': h.yaw('B')},
        'back_door': {'pos': h.cpt(11, 0, 14), 'yaw': h.yaw('B')},
        'doorway': {'pos': h.cpt(12, 0, 10), 'yaw': h.yaw('B')},
        'by_fridge': {'pos': h.cpt(3, 0, 13), 'yaw': h.yaw('B')},
    }
    # living room
    A['armchair'] = h.cpt(9, 0.5, 5) + [h.yaw('W')]
    A['sofa_seats'] = [h.cpt(hx, 0.5, 8) + [h.yaw('F')] for hx in (5, 6, 7)]
    A['living_spots'] = {
        'boy_wall': {'pos': h.cpt(9, 0, 8), 'yaw': h.yaw('W')},
        'doorway': {'pos': h.cpt(9, 0, 3), 'yaw': h.yaw('W')},
        'bay': {'pos': h.cpt(5, 0, 1), 'yaw': h.yaw('B')},
        'hearth': {'pos': h.cpt(4, 0, 5), 'yaw': h.yaw('W')},
        'centre': {'pos': h.cpt(6, 0, 5), 'yaw': h.yaw('W')},
    }
    A['living_fireplace_mouth'] = h.pt(3.0, 0, 5.5)
    A['living_fireplace_yaw'] = h.yaw('E')
    a = h.pt(3.0, 0, 8.0)           # box corners: hx 3..9 (faces), d 2..8 (faces), y 0..3
    b = h.pt(9.0, 3, 2.0)
    A['living_room_box'] = [[min(a[i], b[i]) for i in range(3)], [max(a[i], b[i]) for i in range(3)]]
    # cameras
    door = h.pt(12.5, 1.2, 0.0)
    A['street_wide_cam'] = {'pos': h.pt(-24.0, 9.0, -14.0), 'look_at': h.pt(14.0, 3.0, 2.0)}
    A['cameras'] = {
        'street_opposite': {'pos': h.pt(12.5, 2.5, -19.5), 'look_at': h.pt(12.5, 3.0, 2.0)},
        'end_of_close': {'pos': h.pt(52.0, 4.0, -11.0), 'look_at': h.pt(10.0, 3.0, -2.0)},
        'street_low': {'pos': h.pt(-6.0, 0.6, -10.5), 'look_at': h.pt(10.0, 3.0, 0.0)},
        'front_garden': {'pos': h.pt(12.5, 1.6, -5.5), 'look_at': door},
        'hall_from_door': {'pos': h.pt(12.5, 1.6, 2.2), 'look_at': h.pt(12.5, 1.2, 8.5)},
        'hall_from_kitchen': {'pos': h.pt(11.6, 1.5, 8.6), 'look_at': h.pt(12.5, 0.3, 1.5)},
        'cupboard_out': {'pos': h.pt(11.3, 1.0, 7.5), 'look_at': h.pt(14.5, 0.7, 7.0)},
        'living_wide': {'pos': h.pt(8.6, 2.4, 2.4), 'look_at': h.pt(2.5, 0.8, 5.5)},
        'living_low': {'pos': h.pt(7.5, 0.5, 6.5), 'look_at': h.pt(3.0, 0.8, 5.5)},
        'kitchen_wide': {'pos': h.pt(13.5, 2.3, 10.4), 'look_at': h.pt(6.0, 0.8, 13.0)},
        'roofs_wide': {'pos': h.pt(-14.0, 9.0, -14.0), 'look_at': h.pt(10.0, 9.0, 4.0)},
    }
    # owls: ridges, chimney tops, garage roofs, canopies, bay roofs, hedges, back fences, lamps and the sign
    owls = []
    for i, hh in enumerate(houses):
        near = abs(i - NO4) <= 1 or i >= 5
        owls.append(hh.pt(3.5, 13.5, 8.5))
        owls.append(hh.pt(12.5, 13.5, 8.5))
        if near:
            owls.append(hh.pt(0.5, 15.5, 8.5))                 # chimney top, beside the pot
            owls.append(hh.pt(19.5, 3.5, 2.5))                 # garage roof
        if i == NO4 or abs(i - NO4) == 1:
            owls.append(hh.pt(3.5, 3.5, -0.7))                 # bay roof, its west end
            owls.append(hh.pt(11.5, 3.5, -0.7))                # porch canopy, west end
            owls.append(hh.pt(4.5, 1.0, -6.5))                 # hedge
            owls.append(hh.pt(8.5, 1.0, -6.5))
    for p in street_owls:
        owls.append(lsc(p))
    A['roof_owl_spots'] = owls
    A['owl_look_at'] = door
    A['RADIUS'] = RADIUS
    A['TIME_OF_DAY'] = TIME_OF_DAY
    return A


# ---------------------------------------------------------------- checks
PASSABLE = set(FREE) | {'minecraft:light'}


def to_cell(p):
    """Scene point to local cell."""
    return (int(math.floor(p[0])) - TEMPLATE_AT[0], int(math.floor(p[1] + 1e-6)) - TEMPLATE_AT[1],
            int(math.floor(p[2])) - TEMPLATE_AT[2])


def top_height(st):
    """The height of a block's top surface within its cell, for the shapes an actor or owl can stand on."""
    b = bid(st)
    if b is None or b in PASSABLE:
        return None
    if b.endswith('_carpet'):
        return 1 / 16.0
    if b.endswith('_bed'):
        return 9 / 16.0
    if b.endswith('_slab'):
        return 0.5 if 'type=bottom' in st else 1.0
    if b.endswith('_stairs'):
        return 0.5 if 'half=bottom' in st else 1.0
    if b.endswith('_trapdoor'):
        if 'open=true' in st:
            return None
        return 3 / 16.0 if 'half=bottom' in st else 1.0
    if b.endswith('_fence') or b.endswith('_wall'):
        return 1.0                                  # model top (owls perch on it)
    return 1.0


def point_in_air(g, p):
    """Whether point p (scene) is outside every block's real shape, for the shapes this set uses."""
    c = to_cell(p)
    st = g.get(c)
    b = bid(st)
    if b is None or b in PASSABLE:
        return True
    fy = p[1] - math.floor(p[1] + 1e-6)
    if b.endswith('_bed'):
        return fy > 9 / 16.0
    if b.endswith('_slab'):
        return ('type=bottom' in st and fy > 0.5) or ('type=top' in st and fy < 0.5)
    if b.endswith('_carpet'):
        return fy > 1 / 16.0
    if b.endswith('_stairs') and 'half=top' in st:
        f = st.split('facing=')[1].split(',')[0].rstrip(']')
        fx = p[0] - math.floor(p[0])
        fz = p[2] - math.floor(p[2])
        quarter = {'west': fx < 0.5, 'east': fx > 0.5, 'north': fz < 0.5, 'south': fz > 0.5}[f]
        return fy < 0.5 and not quarter
    if b == 'minecraft:lantern':
        return False
    return False


def check_stand(g, name, p, problems, height=1.8):
    """p's y must be the top of what is under it (a full cell below for integer y, or a partial block in its
    own cell), with every cell clear from the surface up to `height` above it (1.8 a person, 0.6 an owl)."""
    c = to_cell(p)
    fy = p[1] - math.floor(p[1] + 1e-6)
    own = g.get(c)
    if fy < 1e-6:
        below = g.get((c[0], c[1] - 1, c[2]))
        th = top_height(below)
        if th is None or abs(th - 1.0) > 1e-6:
            problems.append('%s %s: nothing solid under it (%s)' % (name, p, below))
        start = 0
        if bid(own) not in PASSABLE:
            problems.append('%s %s: its cell %s is %s' % (name, p, c, own))
    else:
        th = top_height(own)
        if th is None or abs(th - fy) > 1e-3:
            problems.append('%s %s: no surface at %.4f in %s (%s)' % (name, p, fy, c, own))
        start = 1
    top = int(math.floor(p[1] + height - 1e-6)) - int(math.floor(p[1] + 1e-6))
    for dy in range(start, top + 1):
        cc = (c[0], c[1] + dy, c[2])
        if bid(g.get(cc)) not in PASSABLE:
            problems.append('%s %s: cell %s above is %s' % (name, p, cc, g.get(cc)))


def check_seat(g, name, seat, problems):
    c = to_cell(seat)
    st = g.get(c)
    if not (bid(st) or '').endswith('_stairs') or 'half=bottom' not in st or abs(seat[1] % 1 - 0.5) > 1e-6:
        problems.append('%s %s: no seat block at %s (%s)' % (name, seat, c, st))
    for dy in (1, 2):
        cc = (c[0], c[1] + dy, c[2])
        b = bid(g.get(cc))
        if b not in PASSABLE and not (dy == 2 and b.endswith('_trapdoor')
                                         and 'open=true' in g.get(cc)):        # a frame flat on the wall
            problems.append('%s %s: cell %s above is %s' % (name, seat, cc, g.get(cc)))
    # the sitter faces away from the stair's back
    back = st.split('facing=')[1].split(',')[0].rstrip(']') if st and 'facing=' in st else None
    if back and abs(((YAW[back] + 180.0) - seat[3] + 540) % 360 - 180) > 1e-6:
        problems.append('%s %s: yaw %s does not face away from the back (%s)' % (name, seat, seat[3], back))
    # the cell in front (where the legs go) is clear at seat height
    yaw = math.radians(seat[3])
    fx, fz = -math.sin(yaw), math.cos(yaw)
    front = to_cell([seat[0] + fx, seat[1], seat[2] + fz])
    if bid(g.get(front)) not in PASSABLE:
        problems.append('%s %s: the leg cell %s is %s' % (name, seat, front, g.get(front)))


def check(g, houses, A):
    problems = []
    h = houses[NO4]
    # standing marks
    for k in ('hall_inside_door', 'front_step'):
        check_stand(g, k, A[k]['pos'], problems)
    for grp in ('hall_spots', 'kitchen_spots', 'living_spots'):
        for k, v in A[grp].items():
            check_stand(g, '%s.%s' % (grp, k), v['pos'], problems)
    check_stand(g, 'cupboard_door.outside', A['cupboard_door']['outside']['pos'], problems)
    check_stand(g, 'stairs_top', A['stairs_top'], problems)
    check_stand(g, 'hall_mat', A['hall_mat'], problems)
    check_stand(g, 'no4_front', A['no4_front'], problems)
    for i, p in enumerate(A['stairs_path']):
        check_stand(g, 'stairs_path[%d]' % i, p, problems)
    # the cot and the eye
    for k in ('cupboard_bed', 'cupboard_bed_head'):
        c = to_cell(A[k])
        if not (bid(g.get(c)) or '').endswith('_bed'):
            problems.append('%s %s is %s' % (k, A[k], g.get(c)))
        if not point_in_air(g, [A[k][0], A[k][1] + 0.01, A[k][2]]):
            problems.append('%s: no air on the mattress' % k)
    eye = A['cupboard_eye']
    for dx in (-0.1, 0, 0.1):
        for dy in (-0.1, 0, 0.1):
            for dz in (-0.1, 0, 0.1):
                if not point_in_air(g, [eye[0] + dx, eye[1] + dy, eye[2] + dz]):
                    problems.append('cupboard_eye %s +(%s, %s, %s) is not in air (%s)' % (
                        eye, dx, dy, dz, g.get(to_cell([eye[0] + dx, eye[1] + dy, eye[2] + dz]))))
    # the cupboard is 1 x 2 with a stepped ceiling 1.0..2.5 over its floor (sampled on the cot's centre line)
    ceil = []
    for fd in (6.25, 6.75, 7.25, 7.75):
        p = h.pt(14.5, 0, fd)
        y = 0.6
        while point_in_air(g, [p[0], y, p[2]]) and y < 4:
            y += 0.05
        ceil.append(round(y, 2))
    if not (0.95 <= min(ceil) <= 1.1 and 2.4 <= max(ceil) <= 2.6 and ceil == sorted(ceil)):
        problems.append('cupboard ceiling profile %s' % ceil)
    for hy in (0, 1):
        for d in (6, 7):
            for hx in (13, 15):
                st = h.get(hx, hy, d)
                if hx == 13 and d == 7:
                    if 'spruce_door' not in (st or ''):
                        problems.append('cupboard door cell %s is %s' % ((hx, hy, d), st))
                elif bid(st) in PASSABLE:
                    problems.append('cupboard side open at %s' % ((hx, hy, d),))
    # seats
    for k, s in A['kitchen_seats'].items():
        check_seat(g, 'kitchen_seats.' + k, s, problems)
    check_seat(g, 'armchair', A['armchair'], problems)
    for i, s in enumerate(A['sofa_seats']):
        check_seat(g, 'sofa_seats[%d]' % i, s, problems)
    tc = to_cell(A['kitchen_table'])
    if 'trapdoor' not in (g.get(tc) or ''):
        problems.append('kitchen_table cell %s is %s' % (tc, g.get(tc)))
    # the flood box: all air, the mouth on its west face, the firebox outside
    (x1, y1, z1), (x2, y2, z2) = A['living_room_box']
    n = 0
    fl, ce = math.floor, math.ceil
    for x in range(fl(x1), ce(x2)):
        for y in range(fl(y1), ce(y2)):
            for z in range(fl(z1), ce(z2)):
                c = to_cell([x + 0.5, y + 0.5, z + 0.5])
                n += 1
                if g.get(c) is not None:
                    problems.append('living_room_box cell %s (scene %s) is %s' % (c, (x, y, z), g.get(c)))
    for x in range(fl(x1), ce(x2)):
        for z in range(fl(z1), ce(z2)):
            below = g.get(to_cell([x + 0.5, -0.5, z + 0.5]))
            if bid(below) in PASSABLE:
                problems.append('living_room_box floor open at %s' % ((x, z),))
    m = A['living_fireplace_mouth']
    if not (abs(m[0] - x1) < 1e-6 and z1 < m[2] < z2 and m[1] == y1):
        problems.append('fireplace mouth %s is not on the box west face %s' % (m, A['living_room_box']))
    for c in (h.p(2, 0, 5), h.p(1, 0, 5)):
        sp = [c[0] + TEMPLATE_AT[0] + 0.5, 0.5, c[2] + TEMPLATE_AT[2] + 0.5]
        if x1 <= sp[0] <= x2 and z1 <= sp[2] <= z2:
            problems.append('firebox cell %s inside the box' % (c,))
        if g.get(c) is not None:
            problems.append('firebox cell %s is %s' % (c, g.get(c)))
    if not (g.get(h.p(0, 0, 5)) and g.get(h.p(2, 1, 5)) and g.get(h.p(1, 1, 5))):
        problems.append('the firebox is not closed behind and above')
    if A['living_fireplace_yaw'] != -90.0:
        problems.append('fireplace yaw %s' % A['living_fireplace_yaw'])
    # cameras in air
    for k, cam in [('street_wide_cam', A['street_wide_cam'])] + list(A['cameras'].items()):
        if not point_in_air(g, cam['pos']):
            problems.append('camera %s %s is in %s' % (k, cam['pos'], g.get(to_cell(cam['pos']))))
    # owls
    if len(A['roof_owl_spots']) < 20:
        problems.append('only %d owl spots' % len(A['roof_owl_spots']))
    for i, p in enumerate(A['roof_owl_spots']):
        check_stand(g, 'roof_owl_spots[%d]' % i, p, problems, height=0.6)
    # the slot is on the door's face
    dc = to_cell([A['letter_slot'][0], 0.5, A['letter_slot'][2] - 0.01])
    if 'dark_oak_door' not in (g.get(dc) or ''):
        problems.append('letter slot is not on the front door: %s is %s' % (dc, g.get(dc)))
    return problems, n


def scene_world(g):
    return {(x + TEMPLATE_AT[0], y + TEMPLATE_AT[1], z + TEMPLATE_AT[2]): bid(s) for (x, y, z), s in g.items()}


_G, _HOUSES, _STREET_OWLS = build_grid()
ANCHORS = anchors(_HOUSES, _STREET_OWLS)


def build(world):
    """Writes the set into world as {scene cell (x, y, z): full block state} (what great_hall_scene.make_world
    builds for its checks) and returns ANCHORS."""
    for (x, y, z), st in _G.g.items():
        world[(x + TEMPLATE_AT[0], y + TEMPLATE_AT[1], z + TEMPLATE_AT[2])] = st
    return ANCHORS


def cupboard_profile():
    """(ceiling heights above the cupboard floor along the cot's centre line, foot to head; the eye's clearance to
    the ceiling above it and to the mattress below it)."""
    h = _HOUSES[NO4]
    g = _G.g
    ceil = []
    for fd in (6.25, 6.75, 7.25, 7.75):
        p = h.pt(14.5, 0, fd)
        y = 0.6
        while point_in_air(g, [p[0], y, p[2]]) and y < 4:
            y += 0.01
        ceil.append(round(y, 2))
    e = ANCHORS['cupboard_eye']
    y = e[1]
    while point_in_air(g, [e[0], y, e[2]]) and y < 4:
        y += 0.01
    return ceil, round(y - e[1], 2), round(e[1] - 9 / 16.0, 2)


def write_nbt():
    """Checks the set and writes structure/privet_drive.nbt; returns (palette size, block count)."""
    g = _G.g
    xs = [p[0] for p in g]
    ys = [p[1] for p in g]
    zs = [p[2] for p in g]
    assert min(xs) >= 0 and min(ys) >= 0 and min(zs) >= 0, (min(xs), min(ys), min(zs))
    assert max(xs) < SIZE[0] and max(ys) < SIZE[1] and max(zs) < SIZE[2], (max(xs), max(ys), max(zs))
    for axis in (0, 2):
        lo, hi = TEMPLATE_AT[axis], TEMPLATE_AT[axis] + SIZE[axis] - 1
        assert -RADIUS <= lo and hi <= RADIUS, ('template beyond radius', axis, lo, hi)
    problems = physics_problems(scene_world(g), RADIUS)
    assert not problems, problems
    problems, n = check(g, _HOUSES, ANCHORS)
    assert not problems, '\n'.join(problems[:60]) + ('\n... %d in all' % len(problems))
    return write_template(g, SIZE, OUT) + (n,)


def main():
    n_pal, n_blocks, n_box = write_nbt()
    ceil, up, down = cupboard_profile()
    print('privet_drive.nbt: size %s, %d blocks, %d palette states, %d bytes; at %s radius %d; checks clean'
          % (SIZE, n_blocks, n_pal, os.path.getsize(OUT), TEMPLATE_AT, RADIUS))
    print('  flood box %s (%d air cells), mouth %s yaw %s' % (ANCHORS['living_room_box'], n_box,
                                                          ANCHORS['living_fireplace_mouth'],
                                                          ANCHORS['living_fireplace_yaw']))
    print('  cupboard ceiling, foot to head: %s; eye %.2f under the ceiling, %.2f over the mattress'
          % (ceil, up, down))
    print('  %d owl spots' % len(ANCHORS['roof_owl_spots']))
    for k, v in ANCHORS.items():
        if k in ('roof_owl_spots',):
            print('  %s (%d): %s ...' % (k, len(v), v[:4]))
        elif k not in ('living_room_box',):
            print('  %s: %s' % (k, v))


if __name__ == '__main__':
    main()
