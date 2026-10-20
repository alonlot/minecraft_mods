"""Builds the Wand memory's two sets and the anchors wand_scene.py stages them with:

    python -I build_scripts/pensieve_scenes/diagon_set.py

  structure/wand_diagon.nbt       the Leaky Cauldron back yard, the brick wall with its archway cut-out, and a
                                  narrow, crooked Diagon Alley rebuilt in the film's manner (jettied upper storeys,
                                  steep gables, hanging signs, bunting) ending at Gringotts' white steps.
  structure/wand_ollivanders.nbt  Ollivanders inside: narrow and tall, wand-box shelving to the ceiling, a counter,
                                  a ladder rail, a dim window and a single lamp.

The street is rebuilt rather than cropped from structure/diagon_alley.nbt: that build is a 233-long box whose
eastern half is one bank, so its shops, lanes and windows could not be placed where the scene needs them. It kept
its palette ideas (terracotta shop fronts, dark oak framing, slate roofs, white Gringotts). No new block: the wand
boxes are banks of mud bricks, tuff bricks and nearly empty chiseled bookshelves (pigeonholes), banded by dark-oak
shelf boards and uprights.

Using a set from a scene (wand_scene.py):

    import diagon_set as DS
    'radius': DS.DIAGON_RADIUS,
    'set': {'templates': [{'id': 'hogwartsmagic:wand_diagon', 'at': list(DS.DIAGON_TEMPLATE_AT)}],
            'time_of_day': DS.TIME_OF_DAY}
    (and OLLIVANDERS_TEMPLATE_AT / OLLIVANDERS_RADIUS / 'hogwartsmagic:wand_ollivanders' for the shop)

ANCHORS format
--------------
Every coordinate is in SCENE coordinates of its own set (template local + that set's TEMPLATE_AT). The street
set's ground block layer is scene y -1, so anyone standing in the street, the yard or the shop stands at y 0
(Gringotts' portico is higher: its anchors say so). Directions: x runs along the street (+x from the arch toward
Gringotts), the street's centre line is scene z 0.5 (cell row z 0). yaw is Minecraft's: 0 faces +Z, 90 faces -X,
-90 faces +X, 180 faces -Z.

  pos / spot / feet     [x, y, z]                 feet position (bottom centre), standable: air at the feet and
                                                  head cells, something solid under the feet
  cell                  [x, y, z] ints            a block cell's min corner; its centre is cell + 0.5
  {'pos', 'yaw'}                                  a standing mark with a facing

DIAGON_ANCHORS (set 'wand_diagon'):
  wall_block            'minecraft:bricks'        the block state the static wall and the moving bricks use
  wall_bricks           [{cell, pos, target_cell, target_pos, order, rank}]  the archway cut-out: one entry per
                        cell left AIR in the template. `cell` is where the brick starts (flush in the wall: put a
                        memory_block brick there at t 0 so the wall looks whole), `target_cell` is where it ends as
                        part of the arch's brick surround on the alley side (also air in the template). `pos` and
                        `target_pos` are the cells' bottom centres (cell + [0.5, 0, 0.5]). `order` is the distance
                        in blocks from the tapped brick (stagger the moves by it), `rank` its index in that order
                        (0 = the tapped brick). Sorted by rank.
  first_brick           cell of the brick Hagrid taps (rank 0)
  tap_bricks            three cells Hagrid's umbrella counts across before the first one (yard face)
  arch_center           [x, y, z] the opening's centre on the yard face of the wall (x = the face plane)
  arch_opening          {'x': [x0, x1] wall cells, 'z': [z0, z1], 'y': [y0, y1], 'face_yard_x', 'face_alley_x'}
  arch_exit             feet spot just through the arch on the alley side, facing +x
  hagrid_yard, boy_yard {'pos', 'yaw'}            their marks in the yard facing the wall (Hagrid's is for scale 1.5)
  yard_camera           {'pos', 'look_at'}        first-person eye height over boy_yard, looking at the wall
  lanes                 {'nodes': {id: [x, y, z]}, 'edges': [[id, id], ...], 'lane_z': {...}}  the crowd's walk
                        graph: four lanes along the street joined at every column; every edge is clear for a
                        0.35-radius body at feet and head height, of stalls, cages, fronts, the arch and Gringotts.
  hagrid_path           [[x, y, z], ...]          Hagrid and the boy's walk, centre line, arch exit to Ollivanders
  window_spots          [{name, side, glass, view: {pos, yaw}, camera: {pos, look_at}, crowd_spots}] each shop
                        window: `glass` is the glass plane's centre at child-face height, `view` a mark right in
                        front of it facing it, `camera` a framing from across the street, `crowd_spots` extra
                        marks along the window (the children at the broom window). Names: quidditch, eeylops,
                        apothecary, flourish_blotts, cauldrons, ollivanders.
  broom_stand           [x, y, z] where the broom prop rests in the Quidditch window (on a fence-post stand)
  owl_cages             [{pos, yaw}] owl feet spots inside the cages outside Eeylops (and on its window perches)
  goblin_doors          [{pos, yaw}] the two guards' marks on Gringotts' portico, flanking the door
  gringotts_door        {'pos', 'yaw'} the door's centre at portico level
  ollivanders_door      {'outside': {pos, yaw}, 'door_cell', 'bell_side'}  the street mark at the shop's door
  street_cameras        named {'pos', 'look_at'} establishing framings (through the arch, down the street, ...)

OLLIVANDERS_ANCHORS (set 'wand_ollivanders'; the floor block is scene y -1, people stand at y 0):
  door_inside           {'pos', 'yaw'} just inside the door, facing into the shop
  door_bell             [x, y, z] the bell's cell centre (a ceiling bell over the door, hanging from a beam)
  boy_counter           {'pos', 'yaw'} the boy's mark in front of the counter, facing it
  counter               {'pos', 'yaw'} Ollivander's mark behind the counter, facing the boy
  counter_top           [x, y, z] the middle of the counter top (wands and boxes go here)
  tape_center           [x, y, z] the boy's chest centre, where the tape spirals
  vase_spot             cell of the decorated pot on the counter end (the block the second wand shatters)
  ollivander_ladder_rail {'from', 'to', 'rail_y', 'face_z', 'ladder_z', 'appear'}  the rail along the north
                        shelves behind the counter; a ladder actor rolls between from/to (feet points on the floor
                        against the shelf face), `appear` is Ollivander's spot on the ladder, `ladder_z` the ladder's
                        plane
  shelves_box_cells     [{cell, pos, normal}] shelf-face cells round the counter that boxes can burst out of; `pos`
                        is the face centre, `normal` the unit direction into the room
  window                {'center', 'normal'} the dim front window's centre (light comes in from it)
  cameras               named {'pos', 'look_at'} framings (from the door, the ladder, over the counter)

ANCHORS is the two dicts merged (their keys do not overlap), so ANCHORS['wall_bricks'] and ANCHORS['counter'] both
work; remember which set each belongs to.

Asserts on every run: physics clean, everything inside the radius, every standing anchor in air on solid ground,
the archway cut-out and its targets air, the push through the arch clear, every lane edge clear.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import physics_problems, FREE, STRUCTURES  # noqa: E402
from great_hall_set import state, bid, Builder, write_template  # noqa: E402

OUT_DIAGON = os.path.join(STRUCTURES, 'wand_diagon.nbt')
OUT_OLLIVANDERS = os.path.join(STRUCTURES, 'wand_ollivanders.nbt')
TIME_OF_DAY = 4000

# ---------------------------------------------------------------- the street set (local frame)
# x: 0 the Leaky Cauldron's back wall .. 161 Gringotts' back. z: 0..40, the street's air z 15..25 at ground, centre
# row z 20 (centre line 20.5). y 0 is the ground block layer.
DIAGON_SIZE = (162, 34, 41)
DIAGON_TEMPLATE_AT = (-81, -1, -20)
DIAGON_RADIUS = 110

STREET_Z0, STREET_Z1 = 15, 25          # street air at ground
N_FRONTS = (14, 15, 16, 16)            # north row facade plane per storey (ground, 2nd, 3rd, 4th): the jetties
S_FRONTS = (26, 25, 24, 24)
N_BACK, S_BACK = 2, 38
STOREYS = ((1, 5), (6, 9), (10, 13), (14, 17))

YARD_X0, YARD_X1 = 1, 11               # yard interior
YARD_Z0, YARD_Z1 = 13, 27
WALL_X = (12, 13)                      # the archway wall, two bricks thick
WALL_TOP = 9
OPEN_Z = (18, 22)                      # opening cells, centre row 20
OPEN_ROWS = {1: 2, 2: 2, 3: 2, 4: 2, 5: 2, 6: 2, 7: 1, 8: 0}   # y -> half width about z 20 (a round head)
RING_X = (14, 15)                      # the arch's brick surround on the alley side (where the bricks go)
STREET_X0, STREET_X1 = 16, 125         # shop rows
STEPS_X0 = 126                         # Gringotts' steps begin
PORTICO_X = (132, 135)
BANK_FACE_X = 136
LANE_X = (18, 124)


def dsc(x, y, z):
    return [x + DIAGON_TEMPLATE_AT[0], y + DIAGON_TEMPLATE_AT[1], z + DIAGON_TEMPLATE_AT[2]]


def opening_cells():
    out = []
    for y, hw in OPEN_ROWS.items():
        for z in range(20 - hw, 20 + hw + 1):
            out.append((y, z))
    return out


# ---------------------------------------------------------------- helpers
def pane(name, axis):
    """A glass pane strip along x ('x') or z ('z')."""
    if axis == 'x':
        return state(name, east=True, west=True)
    return state(name, north=True, south=True)


GROUND_MIX = (('minecraft:cobblestone', 0.42), ('minecraft:stone', 0.18), ('minecraft:andesite', 0.2),
              ('minecraft:mossy_cobblestone', 0.08), ('minecraft:tuff', 0.12))


def ground_block(rnd):
    r, acc = rnd.random(), 0.0
    for s, p in GROUND_MIX:
        acc += p
        if r < acc:
            return s
    return GROUND_MIX[0][0]


SHOP_COLOURS = ('green_terracotta', 'red_terracotta', 'blue_terracotta', 'cyan_terracotta', 'black_terracotta',
                'brown_terracotta', 'purple_terracotta', 'gray_terracotta')
UPPER_FILLS = ('white_terracotta', 'light_gray_terracotta', 'bricks', 'stone_bricks', 'mud_bricks',
               'smooth_sandstone', 'yellow_terracotta', 'packed_mud')
FRAMES = ('dark_oak_log', 'spruce_log', 'stripped_dark_oak_log')
ROOFS = ('deepslate_tile', 'deepslate_tile', 'polished_blackstone_brick', 'dark_oak', 'cobbled_deepslate')
ROOF_FULL = {'deepslate_tile': 'deepslate_tiles', 'polished_blackstone_brick': 'polished_blackstone_bricks',
             'dark_oak': 'dark_oak_planks', 'cobbled_deepslate': 'cobbled_deepslate'}


class Street(Builder):
    def __init__(self):
        super().__init__({})
        self.rnd = random.Random(1991)
        self.windows = []          # window_spots entries (local)
        self.extra = {}            # other local anchors

    def side(self, s):
        """(fronts, back, out): out is +1 when the facade faces +z (the north row)."""
        return (N_FRONTS, N_BACK, 1) if s == 'N' else (S_FRONTS, S_BACK, -1)

    # ------------------------------------------------------------ one house
    def house(self, s, x0, x1, storeys, spec):
        rnd = self.rnd
        fronts, back, out = self.side(s)
        frame = spec.get('frame') or rnd.choice(FRAMES)
        upper = spec.get('upper') or rnd.choice(UPPER_FILLS)
        colour = spec.get('colour') or rnd.choice(SHOP_COLOURS)
        roof = spec.get('roof') or rnd.choice(ROOFS)
        log_y = state(frame, axis='y')
        log_x = state(frame, axis='x')
        for k in range(storeys):
            ya, yb = STOREYS[k]
            f = fronts[k]
            zlo, zhi = (back, f) if out > 0 else (f, back)
            # party walls and back wall
            for y in range(ya, yb + 1):
                for z in range(zlo, zhi + 1):
                    self.set((x0, y, z), state(upper if k else colour))
                    self.set((x1, y, z), state(upper if k else colour))
                for x in range(x0, x1 + 1):
                    self.set((x, y, back), state(upper if k else colour))
            if k:
                # the floor plate (the jetty's underside seen from the street) and corbels under its lip
                for x in range(x0, x1 + 1):
                    for z in range(zlo, zhi + 1):
                        self.set((x, ya, z), 'minecraft:dark_oak_planks')
                if f != fronts[k - 1]:
                    for x in (x0, x1):
                        self.set((x, ya - 1, f), state('dark_oak_stairs', facing='north' if out > 0 else 'south',
                                                       half='top'))
            # the facade
            for y in range(ya, yb + 1):
                self.set((x0, y, f), log_y)
                self.set((x1, y, f), log_y)
            if k == 0:
                self.shopfront(s, x0, x1, f, out, colour, spec)
            else:
                for x in range(x0 + 1, x1):
                    self.set((x, ya, f), log_x)
                    for y in range(ya + 1, yb + 1):
                        self.set((x, y, f), state(upper))
                # windows: pairs of panes with a dark room behind
                w = x1 - x0 - 1
                cols = [x0 + 1 + i for i in range(w) if i % 3 != 2] if w >= 4 else [x0 + 1 + w // 2]
                for x in cols:
                    for y in (ya + 1, ya + 2):
                        self.set((x, y, f), pane(rnd.choice(('glass_pane', 'glass_pane', 'brown_stained_glass_pane')),
                                                 'x'))
        ytop = STOREYS[storeys - 1][1]
        self.roof(s, x0, x1, ytop, fronts[storeys - 1], back, out, roof, upper, spec.get('pitch', rnd.choice((1, 2))))
        if spec.get('chimney', rnd.random() < 0.45):
            cx = x0 if rnd.random() < 0.5 else x1
            cz = back + out * 2
            top = ytop + 2 + (x1 - x0) // 2 * 2 + 2
            for y in range(ytop + 1, min(top, DIAGON_SIZE[1] - 1)):
                self.set((cx, y, cz), 'minecraft:bricks')
        sign = spec.get('sign', rnd.random() < 0.6)
        if sign:
            sx = x0 if spec.get('door_left') else x1
            self.set((sx, 5, fronts[0] + out), state('dark_oak_wall_hanging_sign', facing='east'))

    def roof(self, s, x0, x1, ytop, f, back, out, roof, gable, pitch):
        """A gable facing the street, ridge along z, one block of overhang over the street."""
        stair = roof + '_stairs'
        full = ROOF_FULL[roof]
        zlo, zhi = (back, f + 1) if out > 0 else (f - 1, back)
        w = x1 - x0 + 1
        for x in range(x0, x1 + 1):
            d = min(x - x0, x1 - x)
            h = ytop + 1 + d * pitch
            mid = (w % 2 == 1 and x - x0 == w // 2)
            for z in range(zlo, zhi + 1):
                for y in range(ytop + 1, h):
                    if z in (back, f):
                        self.set((x, y, z), state(gable))          # the gable wall under the roof
                    elif pitch == 2 and y == h - 1:
                        self.set((x, y, z), state(full))           # the steep roof's second course
                if mid:
                    self.set((x, h, z), state(roof + '_slab', type='bottom') if roof != 'cobbled_deepslate'
                             else state('cobbled_deepslate_slab', type='bottom'))
                else:
                    self.set((x, h, z), state(stair, facing='east' if x - x0 < w / 2 else 'west', half='bottom'))
        # a small gable window
        if w >= 5:
            cx = x0 + w // 2
            self.set((cx, ytop + 1 + pitch, f), pane('glass_pane', 'x'))

    # ------------------------------------------------------------ the ground floor
    def shopfront(self, s, x0, x1, f, out, colour, spec):
        rnd = self.rnd
        door_x = x0 + 1 if spec.get('door_left') else x1 - 1
        win = [x for x in range(x0 + 1, x1) if x != door_x]
        riser = state(spec.get('riser', 'dark_oak_planks'))
        for x in range(x0 + 1, x1):
            self.set((x, 5, f), state(colour))                     # the fascia
        # the door
        facing = 'south' if out > 0 else 'north'
        self.set((door_x, 1, f), state('dark_oak_door', facing=facing, half='lower', hinge='left', open=False,
                                        powered=False))
        self.set((door_x, 2, f), state('dark_oak_door', facing=facing, half='upper', hinge='left', open=False,
                                        powered=False))
        self.set((door_x, 3, f), pane('glass_pane', 'x'))
        self.set((door_x, 4, f), state(colour))
        # the window: a riser, glass from y 2 (a child's face height) to 4
        glass = spec.get('glass', 'glass_pane')
        for x in win:
            self.set((x, 1, f), riser)
            for y in (2, 3, 4):
                self.set((x, y, f), pane(glass, 'x'))
        # the shop room: display floor behind the glass, a lantern, light
        zin = f - out
        for x in win:
            self.set((x, 1, zin), 'minecraft:spruce_planks')
        mid = (x0 + x1) // 2
        self.set((mid, 5, f - 3 * out), state('lantern', hanging=True))
        self.set((mid, 3, f - 3 * out), state('light', level=12))
        # floor of the room
        kind = spec.get('shop')
        if kind:
            getattr(self, 'dress_' + kind)(s, x0, x1, f, out, win, door_x)
        else:
            for x in win:
                if rnd.random() < 0.5:
                    self.set((x, 2, zin), state(rnd.choice(('barrel', 'chiseled_bookshelf', 'flower_pot',
                                                            'potted_fern', 'lantern', 'cauldron'))))
        view = self.extra.pop('window_cols', win)
        if view:
            self.add_window(kind or 'shop_%d' % x0, s, view, f, out)

    def add_window(self, name, s, win, f, out):
        gx = (min(win) + max(win) + 1) / 2.0
        glass = [gx, 3.0, f + 0.5]
        view_z = f + out + 0.5
        cam_x = gx - 4.5
        cam_z = f + out * 6 + 0.5
        self.windows.append({
            'name': name, 'side': s,
            'glass': glass,
            'view': {'pos': [gx, 1, view_z], 'yaw': 180 if out > 0 else 0},
            'camera': {'pos': [cam_x, 2.6, cam_z], 'look_at': [gx, 2.6, f + 0.5]},
            'crowd_spots': [{'pos': [x + 0.5, 1, view_z], 'yaw': 180 if out > 0 else 0} for x in win
                            if abs(x + 0.5 - gx) > 0.6],
        })

    # ------------------------------------------------------------ the named shops
    def dress_quidditch(self, s, x0, x1, f, out, win, door_x):
        zin = f - out
        mid = win[len(win) // 2]
        self.set((mid, 2, zin), 'minecraft:dark_oak_fence')       # the broom's stand
        self.extra['broom_stand'] = [mid + 0.5, 3.0, zin + 0.5]
        for x in win:
            if x != mid:
                self.set((x, 2, zin), state('red_carpet'))
        self.set((win[0], 2, zin), 'minecraft:target')            # a Quaffle-red ball on the display
        for x in range(x0 + 1, x1):
            self.set((x, 5, f), 'minecraft:red_terracotta')
        self.set((x0, 4, f + out), state('red_wall_banner', facing='south' if out > 0 else 'north'))

    def dress_eeylops(self, s, x0, x1, f, out, win, door_x):
        zin = f - out
        for x in win:
            self.set((x, 2, zin), state('dark_oak_fence'))       # perches in the window
        perches = [{'pos': [x + 0.5, 3.0, zin + 0.5], 'yaw': 180 if out > 0 else 0} for x in win[:-5]]
        # cages stacked against the front, outside, beside the door: bars | owl | bars | owl | bars, two tiers
        cz = f + out
        xs = win[-5:]
        self.extra['window_cols'] = [x for x in win if x not in xs]
        cages = []
        for i, x in enumerate(xs):
            if i % 2 == 0:
                for y in range(1, 6):
                    self.set((x, y, cz), state('iron_bars', north=True, south=True))
            else:
                self.set((x, 1, cz), 'minecraft:barrel')
                self.set((x, 2, cz + out), state('iron_bars', east=True, west=True))
                self.set((x, 3, cz), 'minecraft:dark_oak_planks')
                self.set((x, 4, cz + out), state('iron_bars', east=True, west=True))
                self.set((x, 5, cz), state('dark_oak_slab', type='bottom'))
                for y in (2, 4):
                    cages.append({'pos': [x + 0.5, float(y), cz + 0.5], 'yaw': 0 if out > 0 else 180})
        # the cages are on the outside wall face, so the window loses the glass there: keep the window to the rest
        self.extra['owl_cages'] = cages + perches
        self.extra['eeylops_cage_x'] = xs

    def dress_apothecary(self, s, x0, x1, f, out, win, door_x):
        zin = f - out
        jars = ('green_stained_glass', 'lime_stained_glass', 'brown_stained_glass', 'purple_stained_glass',
                'orange_stained_glass')
        for i, x in enumerate(win):
            self.set((x, 2, zin), state('brewing_stand') if i % 2 == 0 else 'minecraft:' + jars[i % len(jars)])
            self.set((x, 1, zin - out), 'minecraft:spruce_planks')
            self.set((x, 2, zin - out), 'minecraft:' + jars[(i + 2) % len(jars)])
            self.set((x, 3, zin - out), state('candle', candles=3, lit=True))
        self.set((x0, 4, f + out), state('green_wall_banner', facing='south' if out > 0 else 'north'))

    def dress_flourish_blotts(self, s, x0, x1, f, out, win, door_x):
        zin = f - out
        for i, x in enumerate(win):
            h = 2 + (i % 3)
            for y in range(2, h + 1):
                self.set((x, y, zin), state('chiseled_bookshelf', facing='south' if out > 0 else 'north',
                                            **{'slot_%d_occupied' % k: True for k in range(6)}))
            self.set((x, 1, zin - out), 'minecraft:spruce_planks')
            for y in (2, 3, 4):
                self.set((x, y, zin - out), 'minecraft:bookshelf')

    def dress_cauldrons(self, s, x0, x1, f, out, win, door_x):
        zin = f - out
        for x in win:
            self.set((x, 2, zin), 'minecraft:cauldron')
        # stacks outside against the front and cauldrons hanging on chains under the jetty
        cz = f + out
        stacks = win[:2]
        self.extra['window_cols'] = win[2:]
        for i, x in enumerate(stacks):
            for y in range(1, 2 + (i % 2) + 1):
                self.set((x, y, cz), 'minecraft:cauldron')
        for x in range(x0 + 1, x1):
            if (x - x0) % 2 == 1:
                self.set((x, 5, cz), state('chain', axis='y'))
                self.set((x, 4, cz), 'minecraft:cauldron')
        self.extra['cauldron_front'] = (stacks, cz)

    def dress_ollivanders(self, s, x0, x1, f, out, win, door_x):
        zin = f - out
        mid = win[len(win) // 2]
        self.set((mid, 2, zin), state('purple_carpet'))           # the faded cushion with a single wand
        self.extra['ollivanders_wand'] = [mid + 0.5, 2.07, zin + 0.5]
        self.extra['ollivanders_door'] = (door_x, f, out)
        for x in range(x0 + 1, x1):
            self.set((x, 5, f), 'minecraft:black_terracotta')
        # the gilded letter board: pale gold boards on the black fascia
        for x in range(x0 + 2, x1):
            self.set((x, 5, f + out), state('bamboo_wall_sign', facing='south' if out > 0 else 'north'))

    # ------------------------------------------------------------ stalls
    def stall(self, s, x0, x1, kind):
        _, _, out = self.side(s)
        z = (N_FRONTS[0] + 1) if s == 'N' else (S_FRONTS[0] - 1)
        wool = ('red_wool', 'white_wool') if kind % 2 else ('green_wool', 'yellow_wool')
        for x in range(x0, x1 + 1):
            self.set((x, 3, z), 'minecraft:' + wool[(x - x0) % 2])
            if x in (x0, x1):
                for y in (1, 2):
                    self.set((x, y, z), 'minecraft:spruce_fence')
            else:
                self.set((x, 1, z), self.rnd.choice(('minecraft:barrel', 'minecraft:spruce_planks',
                                                     'minecraft:composter')))
                g = self.rnd.choice(('minecraft:pumpkin', 'minecraft:melon', 'minecraft:hay_block',
                                     'minecraft:flower_pot', 'minecraft:potted_red_tulip', None, None))
                if g:
                    self.set((x, 2, z), g)


# ---------------------------------------------------------------- the street layout
# (side, x0, x1, storeys, spec). The row is continuous; a gap of one is a dark ginnel.
NORTH = [
    (16, 21, 3, {}), (22, 28, 4, {'pitch': 2}), (29, 33, 3, {}),
    (34, 41, 3, {'shop': 'quidditch', 'colour': 'red_terracotta', 'door_left': True, 'pitch': 2}),
    (42, 47, 4, {}), (49, 55, 3, {}), (56, 61, 3, {'pitch': 2}),
    (62, 68, 4, {'shop': 'apothecary', 'colour': 'green_terracotta', 'glass': 'glass_pane'}),
    (69, 74, 3, {}), (75, 80, 3, {'pitch': 2}),
    (81, 87, 4, {'shop': 'cauldrons', 'colour': 'black_terracotta', 'door_left': True}),
    (88, 93, 3, {}), (94, 100, 4, {'pitch': 2}), (101, 105, 3, {}),
    (106, 110, 4, {'pitch': 2}),
    (111, 116, 4, {'shop': 'ollivanders', 'colour': 'black_terracotta', 'upper': 'gray_terracotta',
                   'frame': 'dark_oak_log', 'glass': 'gray_stained_glass_pane', 'pitch': 2, 'sign': False,
                   'door_left': True}),
    (117, 125, 3, {}),
]
SOUTH = [
    (16, 22, 3, {'pitch': 2}), (23, 28, 4, {}), (29, 35, 3, {}), (36, 41, 3, {'pitch': 2}),
    (42, 52, 3, {'shop': 'eeylops', 'colour': 'brown_terracotta', 'door_left': True, 'pitch': 2}),
    (53, 57, 4, {}), (58, 62, 3, {}), (63, 68, 3, {'pitch': 2}),
    (69, 76, 4, {'shop': 'flourish_blotts', 'colour': 'blue_terracotta', 'pitch': 2}),
    (77, 82, 3, {}), (83, 88, 4, {}), (89, 95, 3, {'pitch': 2}), (96, 101, 3, {}), (102, 108, 4, {}),
    (109, 114, 3, {'pitch': 2}), (115, 125, 3, {}),
]
STALLS = [('N', 18, 20, 0), ('N', 43, 46, 1), ('N', 89, 92, 0), ('S', 30, 33, 1), ('S', 59, 61, 0),
          ('S', 97, 100, 1), ('S', 116, 119, 0), ('N', 118, 121, 1)]
BUNTING_X = (24, 38, 52, 66, 80, 94, 108)


def build_yard(b):
    rnd = b.rnd
    # the yard floor and the alley's ground
    for x in range(0, BANK_FACE_X):
        for z in range(N_BACK - 1, S_BACK + 2):
            b.set((x, 0, z), ground_block(rnd) if x >= WALL_X[0] or YARD_Z0 <= z <= YARD_Z1
                  else 'minecraft:stone_bricks')
    for x in range(YARD_X0, YARD_X1 + 1):
        for z in range(YARD_Z0, YARD_Z1 + 1):
            b.set((x, 0, z), rnd.choice(('minecraft:cobblestone', 'minecraft:stone', 'minecraft:andesite')))
    # the Leaky Cauldron's back wall (x 0) with its back door, windows and an eave
    for z in range(YARD_Z0 - 1, YARD_Z1 + 2):
        for y in range(1, 14):
            b.set((0, y, z), 'minecraft:bricks' if rnd.random() > 0.12 else 'minecraft:mud_bricks')
        b.set((1, 13, z), state('dark_oak_stairs', facing='west', half='bottom'))
        b.set((1, 8, z), state('stripped_dark_oak_log', axis='z'))
    b.set((0, 1, 16), state('dark_oak_door', facing='east', half='lower', hinge='left', open=False, powered=False))
    b.set((0, 2, 16), state('dark_oak_door', facing='east', half='upper', hinge='left', open=False, powered=False))
    for z in (22, 23, 24):
        for y in (4, 5):
            b.set((0, y, z), pane('brown_stained_glass_pane', 'z'))
    for z in (16, 20, 24):
        for y in (10, 11):
            b.set((0, y, z), pane('glass_pane', 'z'))
    # yard side walls
    for x in range(0, WALL_X[0]):
        for y in range(1, 7):
            b.set((x, y, YARD_Z0 - 1), 'minecraft:bricks')
            b.set((x, y, YARD_Z1 + 1), 'minecraft:bricks')
        b.set((x, 7, YARD_Z0 - 1), state('stone_brick_slab', type='bottom'))
        b.set((x, 7, YARD_Z1 + 1), state('stone_brick_slab', type='bottom'))
    # bins, crates, a broken chair: against the north wall and the corner
    for (x, z, s) in ((2, 13, 'minecraft:cauldron'), (3, 13, 'minecraft:cauldron'), (5, 13, 'minecraft:barrel'),
                      (5, 14, 'minecraft:composter'), (2, 27, 'minecraft:barrel'), (3, 27, 'minecraft:barrel'),
                      (11, 27, 'minecraft:cauldron'), (10, 27, 'minecraft:barrel')):
        b.set((x, 1, z), s)
    b.set((2, 2, 27), 'minecraft:barrel')
    b.set((11, 1, 13), 'minecraft:cauldron')                     # the dustbin beside the wall (Hagrid counts from it)
    b.set((4, 1, 14), state('spruce_stairs', facing='north', half='bottom'))
    # the archway wall: x 12..13 across the whole width, and solid 14..15 outside the street
    open_set = set(opening_cells())
    for x in range(WALL_X[0], RING_X[1] + 1):
        for z in range(N_BACK - 1, S_BACK + 2):
            if x > WALL_X[1] and STREET_Z0 <= z <= STREET_Z1:
                continue
            for y in range(1, WALL_TOP + 1):
                if x <= WALL_X[1] and (y, z) in open_set:
                    continue
                b.set((x, y, z), 'minecraft:bricks')
            b.set((x, WALL_TOP + 1, z), state('stone_brick_slab', type='bottom'))


def build_street(b):
    for s, rows in (('N', NORTH), ('S', SOUTH)):
        for x0, x1, n, spec in rows:
            b.house(s, x0, x1, n, spec)
    for s, x0, x1, k in STALLS:
        b.stall(s, x0, x1, k)
    # bunting: strings of coloured panes sagging across between the third storeys
    colours = ('red', 'yellow', 'blue', 'green', 'purple', 'orange')
    for i, x in enumerate(BUNTING_X):
        for z in range(N_FRONTS[2] + 1, S_FRONTS[2]):
            t = (z - N_FRONTS[2]) / float(S_FRONTS[2] - N_FRONTS[2])
            y = 13 - int(round(1.6 * math.sin(math.pi * t)))
            b.set((x, y, z), pane('%s_stained_glass_pane' % colours[(z + i) % len(colours)], 'z'))


def build_gringotts(b):
    rnd = b.rnd
    marble = ('minecraft:smooth_quartz', 'minecraft:quartz_block', 'minecraft:calcite')

    def m():
        r = rnd.random()
        return marble[0] if r < 0.6 else marble[1] if r < 0.88 else marble[2]

    zlo, zhi = N_BACK - 1, S_BACK + 1
    # the steps: six, the full street width and a little more
    for i in range(6):
        x = STEPS_X0 + i
        for z in range(STREET_Z0 - 2, STREET_Z1 + 3):
            for y in range(1, i + 1):
                b.set((x, y, z), m())
            b.set((x, i + 1, z), state('quartz_stairs', facing='east', half='bottom'))
    # the portico platform (top y 6, people stand at y 7)
    for x in range(PORTICO_X[0], PORTICO_X[1] + 1):
        for z in range(STREET_Z0 - 2, STREET_Z1 + 3):
            for y in range(1, 7):
                b.set((x, y, z), m() if y < 6 else 'minecraft:polished_diorite')
    # flanking plinths so the steps sit in a cut between the side buildings
    for x in range(STEPS_X0, PORTICO_X[1] + 1):
        for z in list(range(zlo, STREET_Z0 - 2)) + list(range(STREET_Z1 + 3, zhi + 1)):
            for y in range(1, 7):
                b.set((x, y, z), m())
    # columns and the entablature
    for z in (13, 16, 24, 27):
        for y in range(7, 19):
            b.set((PORTICO_X[0] + 1, y, z), state('quartz_pillar', axis='y'))
        b.set((PORTICO_X[0] + 1, 6, z), 'minecraft:chiseled_quartz_block')
    for x in range(PORTICO_X[0], PORTICO_X[1] + 1):
        for z in range(STREET_Z0 - 3, STREET_Z1 + 4):
            b.set((x, 19, z), 'minecraft:smooth_quartz')
            b.set((x, 20, z), 'minecraft:quartz_bricks')
    # the pediment
    for z in range(STREET_Z0 - 3, STREET_Z1 + 4):
        h = 21 + int(min(z - (STREET_Z0 - 3), STREET_Z1 + 3 - z) * 0.5)
        for y in range(21, h + 1):
            b.set((PORTICO_X[0] + 1, y, z), 'minecraft:quartz_bricks' if y < h else 'minecraft:smooth_quartz')
        b.set((PORTICO_X[0], h, z), state('quartz_stairs', facing='east', half='bottom'))
    # the bank's face and body
    for x in range(BANK_FACE_X, DIAGON_SIZE[0]):
        for z in range(zlo, zhi + 1):
            for y in range(1, 31):
                edge = x in (BANK_FACE_X, DIAGON_SIZE[0] - 1) or z in (zlo, zhi) or y == 30
                if edge:
                    b.set((x, y, z), m())
    # tall windows and pilasters on the face
    for z in range(zlo + 2, zhi - 1):
        if (z - zlo) % 4 == 0 and not (16 <= z <= 24):
            for y in range(7, 30):
                b.set((BANK_FACE_X - 1, y, z), state('quartz_pillar', axis='y'))
        elif (z - zlo) % 4 == 2 and not (17 <= z <= 23):
            for y in list(range(9, 15)) + list(range(19, 26)):
                b.set((BANK_FACE_X, y, z), pane('black_stained_glass_pane', 'z'))
    for z in range(zlo, zhi + 1):
        b.set((BANK_FACE_X - 1, 30, z), state('quartz_stairs', facing='east', half='top'))
        b.set((BANK_FACE_X - 1, 31, z), 'minecraft:smooth_quartz')
    # the door: a tall opening with burnished bronze doors set back, and a gold lintel
    for z in range(18, 23):
        for y in range(7, 15):
            b.set((BANK_FACE_X, y, z), None)
            b.set((BANK_FACE_X + 1, y, z), 'minecraft:cut_copper' if z != 20 else 'minecraft:copper_block')
        b.set((BANK_FACE_X, 15, z), 'minecraft:gold_block')
    b.set((BANK_FACE_X + 1, 10, 19), 'minecraft:gold_block')
    b.set((BANK_FACE_X + 1, 10, 21), 'minecraft:gold_block')
    for y in range(7, 15):
        b.set((BANK_FACE_X, y, 17), state('quartz_pillar', axis='y'))
        b.set((BANK_FACE_X, y, 23), state('quartz_pillar', axis='y'))
    for z in (16, 24):
        b.set((BANK_FACE_X - 1, 7, z), state('lantern', hanging=False))


def build_diagon():
    b = Street()
    build_yard(b)
    build_street(b)
    build_gringotts(b)
    # nothing at the arch's surround cells or inside the opening (the bricks are actors)
    for (cell, _) in brick_plan():
        b.set(cell, None)
    for (_, t) in brick_plan():
        b.set(t, None)
    return b


# ---------------------------------------------------------------- the archway bricks
def brick_plan():
    """[(start cell, target cell)] in local coordinates, start sorted by distance from the tapped brick."""
    opening = opening_cells()
    open_set = set(opening)
    first = (WALL_X[0], 4, 21)
    starts = [(x, y, z) for x in WALL_X for (y, z) in opening]
    starts.sort(key=lambda c: (_d(c, first), c))

    def cheb(y, z):
        return min(max(abs(y - oy), abs(z - oz)) for (oy, oz) in opening)

    targets = []
    for x in RING_X:
        rings = (1, 2) if x == RING_X[0] else (1,)
        for y in range(1, WALL_TOP + 3):
            for z in range(OPEN_Z[0] - 2, OPEN_Z[1] + 3):
                if (y, z) in open_set:
                    continue
                if cheb(y, z) in rings:
                    targets.append((x, y, z))
    assert len(targets) >= len(starts), (len(targets), len(starts))
    # the bricks nearest the opening's edge fill the surround from the bottom of the jambs up
    free = set(targets)
    plan = []
    for c in starts:
        best = min(free, key=lambda t: (_d((t[0], t[1], t[2]), (RING_X[0], c[1], c[2])) + 0.15 * (t[0] - RING_X[0]),
                                        t))
        free.discard(best)
        plan.append((c, best))
    return plan


def _d(a, b):
    return math.sqrt(sum((p - q) ** 2 for p, q in zip(a, b)))


# ---------------------------------------------------------------- lanes
LANE_Z = {'north': 17.5, 'mid_north': 19.5, 'mid_south': 21.5, 'south': 23.5}
LANE_STEP = 6


def lane_graph_local():
    nodes, edges = {}, []
    names = list(LANE_Z)
    cols = list(range(LANE_X[0], LANE_X[1] + 1, LANE_STEP))
    for i, x in enumerate(cols):
        for j, n in enumerate(names):
            nid = '%s_%02d' % (n, i)
            nodes[nid] = [x + 0.5, 1, LANE_Z[n]]
            if i:
                edges.append(['%s_%02d' % (n, i - 1), nid])
            if j:
                edges.append(['%s_%02d' % (names[j - 1], i), nid])
    return nodes, edges


def hagrid_path_local(b):
    door_x, f, out = b.extra['ollivanders_door']
    stops = sorted(w['glass'][0] for w in b.windows
                   if w['name'] in ('quidditch', 'eeylops', 'apothecary', 'flourish_blotts', 'cauldrons'))
    return ([[RING_X[1] + 1.5, 1, 20.5]] + [[x, 1, 20.5] for x in stops]
            + [[door_x + 0.5, 1, 20.5], [door_x + 0.5, 1, f + out + 0.5]])


# ---------------------------------------------------------------- Ollivanders (local frame)
OL_SIZE = (22, 21, 11)
OLLIVANDERS_TEMPLATE_AT = (-7, -1, -5)
OLLIVANDERS_RADIUS = 30
OL_CEIL = 20
OL_COUNTER_X = 8
OL_COUNTER_Z = (2, 6)
OL_STACK_X = (14, 15)
OL_STACK_Z = (3, 7)
OL_RAIL = (16, 2, 9, 19)               # rail y, z, x from, x to
OL_DOOR_Z = 8
BANDS = (5, 9, 13, 17)


def osc(x, y, z):
    return [x + OLLIVANDERS_TEMPLATE_AT[0], y + OLLIVANDERS_TEMPLATE_AT[1], z + OLLIVANDERS_TEMPLATE_AT[2]]


SHELF_BLOCKS = ('minecraft:mud_bricks', 'minecraft:tuff_bricks', 'minecraft:chiseled_bookshelf')


def wand_shelf(rnd, facing):
    """A bank of wand-box ends: mostly mud bricks (stacked narrow pale-brown ends), some grey tuff-brick boxes and
    dark pigeonholes (a chiseled bookshelf with a box or two left in it). The loud full bookshelves read as a
    bookshop in the round-1 renders."""
    r = rnd.random()
    if r < 0.62:
        return 'minecraft:mud_bricks'
    if r < 0.78:
        return 'minecraft:tuff_bricks'
    return state('chiseled_bookshelf', facing=facing, **{'slot_%d_occupied' % k: rnd.random() < 0.2 for k in range(6)})


def build_ollivanders():
    b = Builder({})
    rnd = random.Random(382)
    X, Y, Z = OL_SIZE
    for x in range(X):
        for z in range(Z):
            b.set((x, 0, z), 'minecraft:dark_oak_planks' if rnd.random() < 0.55 else 'minecraft:spruce_planks')
            b.set((x, OL_CEIL, z), 'minecraft:dark_oak_planks')
    for y in range(1, OL_CEIL):
        for z in range(Z):
            b.set((0, y, z), 'minecraft:dark_oak_planks')
            b.set((X - 1, y, z), 'minecraft:dark_oak_planks')
        for x in range(X):
            b.set((x, y, 0), 'minecraft:dark_oak_planks')
            b.set((x, y, Z - 1), 'minecraft:dark_oak_planks')
    # the shelving: north (z 1, facing south), south (z 9, facing north), back (x 20, facing west), from x 2
    faces = []

    def shelf(p, facing, normal):
        x, y, z = p
        if y in BANDS:
            axis = 'x' if facing in ('north', 'south') else 'z'
            b.set(p, state('stripped_dark_oak_log', axis=axis))
            return
        along = x if facing in ('north', 'south') else z
        if along % 6 == 1:
            b.set(p, state('dark_oak_log', axis='y'))
            return
        b.set(p, wand_shelf(rnd, facing))
        faces.append((p, normal))

    for y in range(1, OL_CEIL):
        for x in range(2, X - 1):
            shelf((x, y, 1), 'south', (0, 0, 1))
            shelf((x, y, Z - 2), 'north', (0, 0, -1))
        for z in range(2, Z - 2):
            shelf((X - 2, y, z), 'west', (-1, 0, 0))
        # the freestanding double stack behind the counter
        for z in range(OL_STACK_Z[0], OL_STACK_Z[1] + 1):
            shelf((OL_STACK_X[0], y, z), 'west', (-1, 0, 0))
            shelf((OL_STACK_X[1], y, z), 'east', (1, 0, 0))
    # the front: the door, a dim window, framing
    for y in range(1, OL_CEIL):
        b.set((0, y, 1), state('dark_oak_log', axis='y'))
        b.set((0, y, Z - 2), state('dark_oak_log', axis='y'))
    b.set((0, 1, OL_DOOR_Z), state('dark_oak_door', facing='east', half='lower', hinge='left', open=False,
                                    powered=False))
    b.set((0, 2, OL_DOOR_Z), state('dark_oak_door', facing='east', half='upper', hinge='left', open=False,
                                    powered=False))
    for z in range(2, 6):
        for y in range(2, 6):
            b.set((0, y, z), pane('gray_stained_glass_pane' if (y + z) % 3 else 'brown_stained_glass_pane', 'z'))
        b.set((1, 1, z), 'minecraft:spruce_planks')               # the window seat with a few dusty boxes
        if z % 2:
            b.set((1, 2, z), wand_shelf(rnd, 'east'))
    # the beam over the door and the bell hanging from it
    for z in range(2, Z - 2):
        b.set((1, 5, z), state('stripped_dark_oak_log', axis='z'))
    b.set((1, 4, OL_DOOR_Z), state('bell', attachment='ceiling', facing='north', powered=False))
    # the counter: planks with a slab top, the vase on its open end
    for z in range(OL_COUNTER_Z[0], OL_COUNTER_Z[1] + 1):
        b.set((OL_COUNTER_X, 1, z), 'minecraft:dark_oak_planks')
        if z < OL_COUNTER_Z[1]:
            b.set((OL_COUNTER_X, 2, z), state('dark_oak_slab', type='bottom'))
    b.set((OL_COUNTER_X, 2, OL_COUNTER_Z[1]), state('decorated_pot', facing='west', cracked=False,
                                                     waterlogged=False))
    # the ladder rail: a chain along the north shelves behind the counter
    ry, rz, rx0, rx1 = OL_RAIL
    for x in range(rx0, rx1 + 1):
        b.set((x, ry, rz), state('chain', axis='x'))
    # the single lamp over the counter, a few cobwebs high in the corners, dim fill light
    lamp = (OL_COUNTER_X - 1, 6, 4)
    for y in range(lamp[1] + 1, OL_CEIL):
        b.set((lamp[0], y, lamp[2]), state('chain', axis='y'))
    b.set(lamp, state('lantern', hanging=True))
    for p in ((2, 18, 2), (19, 18, 8), (13, 19, 2), (3, 19, 8), (19, 19, 3)):
        b.set(p, 'minecraft:cobweb')
    for p in ((4, 3, 5), (11, 3, 5), (17, 3, 5), (11, 10, 5)):
        b.set(p, state('light', level=6))
    return b, faces


# ---------------------------------------------------------------- anchors
def diagon_anchors(b):
    plan = brick_plan()
    first = plan[0][0]
    bricks = []
    for rank, (c, t) in enumerate(plan):
        bricks.append({'cell': dsc(*c), 'pos': _bc(dsc(*c)), 'target_cell': dsc(*t), 'target_pos': _bc(dsc(*t)),
                       'order': round(_d(c, first), 3), 'rank': rank})
    nodes, edges = lane_graph_local()
    windows = []
    for w in b.windows:
        if w['name'].startswith('shop_'):
            continue
        windows.append({'name': w['name'], 'side': w['side'], 'glass': _s(w['glass']),
                        'view': {'pos': _s(w['view']['pos']), 'yaw': w['view']['yaw']},
                        'camera': {'pos': _s(w['camera']['pos']), 'look_at': _s(w['camera']['look_at'])},
                        'crowd_spots': [{'pos': _s(c['pos']), 'yaw': c['yaw']} for c in w['crowd_spots']]})
    door_x, f, out = b.extra['ollivanders_door']
    ring_mid = (RING_X[1] + 1.5, 1, 20.5)
    return {
        'wall_block': 'minecraft:bricks',
        'wall_bricks': bricks,
        'first_brick': dsc(*first),
        'tap_bricks': [dsc(WALL_X[0], 4, 23), dsc(WALL_X[0], 4, 22), dsc(WALL_X[0], 4, 21)],
        'arch_center': _s([WALL_X[0], 4.5, 20.5]),
        'arch_opening': {'x': [dsc(WALL_X[0], 0, 0)[0], dsc(WALL_X[1], 0, 0)[0]],
                         'z': [dsc(0, 0, OPEN_Z[0])[2], dsc(0, 0, OPEN_Z[1])[2]],
                         'y': [dsc(0, 1, 0)[1], dsc(0, max(OPEN_ROWS), 0)[1]],
                         'face_yard_x': dsc(WALL_X[0], 0, 0)[0], 'face_alley_x': dsc(WALL_X[1] + 1, 0, 0)[0]},
        'arch_exit': {'pos': _s(ring_mid), 'yaw': -90},
        'hagrid_yard': {'pos': _s([9.5, 1, 22.3]), 'yaw': -90},
        'boy_yard': {'pos': _s([9.2, 1, 19.6]), 'yaw': -90},
        'yard_camera': {'pos': _s([9.2, 1 + 1.25, 19.6]), 'look_at': _s([WALL_X[0], 3.5, 21.0])},
        'lanes': {'nodes': {k: _s(v) for k, v in nodes.items()}, 'edges': edges,
                  'lane_z': {k: v + DIAGON_TEMPLATE_AT[2] for k, v in LANE_Z.items()}},
        'hagrid_path': [_s(p) for p in hagrid_path_local(b)],
        'window_spots': windows,
        'broom_stand': _s(b.extra['broom_stand']),
        'owl_cages': [{'pos': _s(c['pos']), 'yaw': c['yaw']} for c in b.extra['owl_cages']],
        'goblin_doors': [{'pos': _s([PORTICO_X[1] - 0.5, 7, 17.5]), 'yaw': 90},
                         {'pos': _s([PORTICO_X[1] - 0.5, 7, 23.5]), 'yaw': 90}],
        'gringotts_door': {'pos': _s([PORTICO_X[1] + 0.5, 7, 20.5]), 'yaw': 90},
        'ollivanders_door': {'outside': {'pos': _s([door_x + 0.5, 1, f + out + 0.5]), 'yaw': 180 if out > 0 else 0},
                             'door_cell': dsc(door_x, 1, f), 'wand_in_window': _s(b.extra['ollivanders_wand'])},
        'street_cameras': {
            'through_arch': {'pos': _s([7.5, 2.6, 20.5]), 'look_at': _s([40.5, 4.0, 20.5])},
            'down_street': {'pos': _s([20.5, 7.5, 20.5]), 'look_at': _s([80.5, 3.0, 20.5])},
            'to_gringotts': {'pos': _s([100.5, 3.0, 19.5]), 'look_at': _s([BANK_FACE_X, 12.0, 20.5])},
            'ollivanders_front': {'pos': _s([door_x - 3.5, 2.2, 22.5]), 'look_at': _s([door_x + 0.5, 3.0, f + 0.5])},
        },
    }


def ollivanders_anchors(g, faces):
    ry, rz, rx0, rx1 = OL_RAIL
    near = [(p, n) for p, n in faces if 2 <= p[1] <= 11 and (p[0] <= 13 or p[0] == OL_STACK_X[0])
            and bid(g.get((p[0] + n[0], p[1], p[2] + n[2]))) in PASSABLE]
    return {
        'door_inside': {'pos': _os([1.5, 1, OL_DOOR_Z + 0.5]), 'yaw': -90},
        'door_bell': _os([1.5, 4.5, OL_DOOR_Z + 0.5]),
        'boy_counter': {'pos': _os([OL_COUNTER_X - 1.6, 1, 4.5]), 'yaw': -90},
        'counter': {'pos': _os([OL_COUNTER_X + 1.6, 1, 4.5]), 'yaw': 90},
        'counter_top': _os([OL_COUNTER_X + 0.5, 2.5, 4.0]),
        'tape_center': _os([OL_COUNTER_X - 1.6, 1.9, 4.5]),
        'vase_spot': osc(OL_COUNTER_X, 2, OL_COUNTER_Z[1]),
        'ollivander_ladder_rail': {'from': _os([rx0 + 0.5, 1, rz + 0.3]), 'to': _os([rx1 + 0.5, 1, rz + 0.3]),
                                   'rail_y': ry + OLLIVANDERS_TEMPLATE_AT[1] + 0.5,
                                   'face_z': 2 + OLLIVANDERS_TEMPLATE_AT[2], 'ladder_z': rz + 0.3
                                   + OLLIVANDERS_TEMPLATE_AT[2],
                                   'appear': _os([11.5, 5.0, rz + 0.75])},
        'shelves_box_cells': [{'cell': osc(*p), 'pos': _os([p[0] + 0.5 + n[0] * 0.5, p[1] + 0.5,
                                                             p[2] + 0.5 + n[2] * 0.5]), 'normal': list(n)}
                              for p, n in near],
        'window': {'center': _os([0.5, 4.0, 4.0]), 'normal': [1, 0, 0]},
        'cameras': {
            'from_door': {'pos': _os([1.6, 2.6, 7.2]), 'look_at': _os([18.0, 9.0, 4.5])},
            'up_the_shelves': {'pos': _os([5.5, 1.4, 3.0]), 'look_at': _os([12.0, 16.0, 2.0])},
            'over_counter': {'pos': _os([11.5, 2.9, 6.6]), 'look_at': _os([OL_COUNTER_X - 1.6, 1.6, 4.5])},
            'boy_closeup': {'pos': _os([OL_COUNTER_X - 0.2, 2.0, 5.6]), 'look_at': _os([OL_COUNTER_X - 1.6, 1.5,
                                                                                       4.5])},
        },
    }


def _bc(c):
    return [c[0] + 0.5, c[1], c[2] + 0.5]


def _s(p):
    return [round(p[0] + DIAGON_TEMPLATE_AT[0], 3), round(p[1] + DIAGON_TEMPLATE_AT[1], 3),
            round(p[2] + DIAGON_TEMPLATE_AT[2], 3)]


def _os(p):
    return [round(p[0] + OLLIVANDERS_TEMPLATE_AT[0], 3), round(p[1] + OLLIVANDERS_TEMPLATE_AT[1], 3),
            round(p[2] + OLLIVANDERS_TEMPLATE_AT[2], 3)]


# ---------------------------------------------------------------- checks
PASSABLE = set(FREE) | {'minecraft:light', 'minecraft:dark_oak_wall_hanging_sign', 'minecraft:dark_oak_wall_sign',
                        'minecraft:bamboo_wall_sign',
                        'minecraft:red_wall_banner', 'minecraft:green_wall_banner', 'minecraft:cobweb'}


def _cell(p, at):
    return (int(math.floor(p[0])) - at[0], int(math.floor(p[1] + 1e-6)) - at[1], int(math.floor(p[2])) - at[2])


def check_stand(g, name, p, at, problems, head=2):
    c = _cell(p, at)
    below = (c[0], c[1] - 1, c[2])
    if bid(g.get(below)) in PASSABLE:
        problems.append('%s %s: nothing solid under it (%s)' % (name, p, g.get(below)))
    for dy in range(head):
        cc = (c[0], c[1] + dy, c[2])
        if bid(g.get(cc)) not in PASSABLE:
            problems.append('%s %s: cell %s is %s' % (name, p, cc, g.get(cc)))


def check_air(g, name, p, at, problems):
    c = _cell(p, at)
    if bid(g.get(c)) not in PASSABLE:
        problems.append('%s %s: cell %s is %s' % (name, p, c, g.get(c)))


def check_diagon(g, A):
    at = DIAGON_TEMPLATE_AT
    problems = []
    for e in A['wall_bricks']:
        for k in ('cell', 'target_cell'):
            c = tuple(e[k][i] - at[i] for i in range(3))
            if c in g:
                problems.append('brick %s %s is %s, must be air' % (k, e[k], g[c]))
    if len({tuple(e['target_cell']) for e in A['wall_bricks']}) != len(A['wall_bricks']):
        problems.append('two bricks share a target')
    # the wall round the cut-out is whole (so the bricks read as part of it)
    for e in A['wall_bricks']:
        x, y, z = (e['cell'][i] - at[i] for i in range(3))
        for dz in (-1, 1):
            n = (x, y, z + dz)
            if n not in g and not any(tuple(b['cell'][i] - at[i] for i in range(3)) == n for b in A['wall_bricks']):
                problems.append('cut-out cell %s has a hole beside it at %s' % ((x, y, z), n))
    for k in ('hagrid_yard', 'boy_yard', 'arch_exit'):
        check_stand(g, k, A[k]['pos'], at, problems, head=3 if k == 'hagrid_yard' else 2)
    check_air(g, 'yard_camera', A['yard_camera']['pos'], at, problems)
    # the push through the arch: yard to street, opening width, up to head height of a 1.5 Hagrid
    for x in range(YARD_X0 + 4, STREET_X0 + 2):
        for z in range(19, 22):
            for y in range(1, 5):
                if x in WALL_X:
                    continue
                if bid(g.get((x, y, z))) not in PASSABLE:
                    problems.append('arch path cell %s is %s' % ((x, y, z), g.get((x, y, z))))
    nodes = A['lanes']['nodes']
    for nid, p in nodes.items():
        check_stand(g, 'lane node ' + nid, p, at, problems)
        if p[0] - at[0] < STREET_X0 + 1:
            problems.append('lane node %s inside the arch' % nid)
    for a, bb in A['lanes']['edges']:
        _check_segment(g, nodes[a], nodes[bb], at, problems, 'lane %s-%s' % (a, bb))
    hp = A['hagrid_path']
    for i in range(len(hp) - 1):
        _check_segment(g, hp[i], hp[i + 1], at, problems, 'hagrid_path %d' % i, head=3, radius=0.5)
    for w in A['window_spots']:
        check_stand(g, 'window %s view' % w['name'], w['view']['pos'], at, problems)
        for i, c in enumerate(w['crowd_spots']):
            check_stand(g, 'window %s crowd %d' % (w['name'], i), c['pos'], at, problems)
        check_air(g, 'window %s camera' % w['name'], w['camera']['pos'], at, problems)
        gc = _cell(w['glass'], at)
        if 'pane' not in str(g.get(gc)):
            problems.append('window %s glass cell %s is %s' % (w['name'], gc, g.get(gc)))
    for i, c in enumerate(A['owl_cages']):
        check_stand(g, 'owl_cages[%d]' % i, c['pos'], at, problems, head=1)
    check_air(g, 'broom_stand', A['broom_stand'], at, problems)
    for i, c in enumerate(A['goblin_doors']):
        check_stand(g, 'goblin_doors[%d]' % i, c['pos'], at, problems)
    check_stand(g, 'ollivanders_door', A['ollivanders_door']['outside']['pos'], at, problems)
    for k, cam in A['street_cameras'].items():
        check_air(g, 'street camera ' + k, cam['pos'], at, problems)
    return problems


def _check_segment(g, a, b, at, problems, name, head=2, radius=0.35):
    n = max(2, int(_d(a, b) / 0.25))
    dx, dz = b[0] - a[0], b[2] - a[2]
    L = math.hypot(dx, dz) or 1.0
    px, pz = -dz / L * radius, dx / L * radius
    seen = set()
    for i in range(n + 1):
        t = i / float(n)
        x, z = a[0] + dx * t, a[2] + dz * t
        for ox, oz in ((0, 0), (px, pz), (-px, -pz), (dx / L * radius, dz / L * radius)):
            c = _cell([x + ox, a[1], z + oz], at)
            if c in seen:
                continue
            seen.add(c)
            if bid(g.get((c[0], c[1] - 1, c[2]))) in PASSABLE:
                problems.append('%s: no ground at %s' % (name, c))
            for dy in range(head):
                cc = (c[0], c[1] + dy, c[2])
                if bid(g.get(cc)) not in PASSABLE:
                    problems.append('%s: cell %s is %s' % (name, cc, g.get(cc)))


def check_ollivanders(g, A):
    at = OLLIVANDERS_TEMPLATE_AT
    problems = []
    for k in ('door_inside', 'boy_counter', 'counter'):
        check_stand(g, k, A[k]['pos'], at, problems)
    check_air(g, 'tape_center', A['tape_center'], at, problems)
    bc = _cell(A['door_bell'], at)
    if bid(g.get(bc)) != 'minecraft:bell':
        problems.append('door_bell %s is %s' % (bc, g.get(bc)))
    v = tuple(A['vase_spot'][i] - at[i] for i in range(3))
    if bid(g.get(v)) != 'minecraft:decorated_pot':
        problems.append('vase_spot %s is %s' % (v, g.get(v)))
    r = A['ollivander_ladder_rail']
    _check_segment(g, r['from'], r['to'], at, problems, 'ladder foot line', head=3, radius=0.2)
    check_air(g, 'ladder appear', r['appear'], at, problems)
    for e in A['shelves_box_cells']:
        c = tuple(e['cell'][i] - at[i] for i in range(3))
        if bid(g.get(c)) not in SHELF_BLOCKS:
            problems.append('box cell %s is %s' % (c, g.get(c)))
        front = (c[0] + e['normal'][0], c[1], c[2] + e['normal'][2])
        if bid(g.get(front)) not in PASSABLE and bid(g.get(front)) != 'minecraft:chain':
            problems.append('box cell %s has %s in front' % (c, g.get(front)))
    for k, cam in A['cameras'].items():
        check_air(g, 'camera ' + k, cam['pos'], at, problems)
    # the walk from the door to the counter mark is clear
    _check_segment(g, A['door_inside']['pos'], A['boy_counter']['pos'], at, problems, 'door to counter')
    return problems


def scene_world(g, at):
    return {(x + at[0], y + at[1], z + at[2]): bid(s) for (x, y, z), s in g.items()}


def _bounds(g, size):
    xs = [p[0] for p in g]
    ys = [p[1] for p in g]
    zs = [p[2] for p in g]
    assert min(xs) >= 0 and min(ys) >= 0 and min(zs) >= 0, (min(xs), min(ys), min(zs))
    assert max(xs) < size[0] and max(ys) < size[1] and max(zs) < size[2], (max(xs), max(ys), max(zs), size)


def _radius(at, size, radius):
    for axis in (0, 2):
        lo, hi = at[axis], at[axis] + size[axis] - 1
        assert -radius <= lo and hi <= radius, ('template beyond radius', axis, lo, hi)


# built at import so a scene generator gets the anchors without writing files
_DIAGON = build_diagon()
_OLL, _FACES = build_ollivanders()
DIAGON_ANCHORS = diagon_anchors(_DIAGON)
OLLIVANDERS_ANCHORS = ollivanders_anchors(_OLL.g, _FACES)
assert not set(DIAGON_ANCHORS) & set(OLLIVANDERS_ANCHORS)
ANCHORS = dict(DIAGON_ANCHORS, **OLLIVANDERS_ANCHORS)


def main():
    for name, b, size, at, radius, check, A, out in (
            ('wand_diagon', _DIAGON, DIAGON_SIZE, DIAGON_TEMPLATE_AT, DIAGON_RADIUS, check_diagon, DIAGON_ANCHORS,
             OUT_DIAGON),
            ('wand_ollivanders', _OLL, OL_SIZE, OLLIVANDERS_TEMPLATE_AT, OLLIVANDERS_RADIUS, check_ollivanders,
             OLLIVANDERS_ANCHORS, OUT_OLLIVANDERS)):
        g = b.g
        _bounds(g, size)
        _radius(at, size, radius)
        problems = physics_problems(scene_world(g, at), radius)
        problems += check(g, A)
        assert not problems, '\n'.join(problems[:40]) + ('\n... %d in all' % len(problems))
        n_pal, n_blocks = write_template(g, size, out)
        print('%s.nbt: size %s, %d blocks, %d palette states, %d bytes; at %s radius %d; checks clean'
              % (name, size, n_blocks, n_pal, os.path.getsize(out), at, radius))
    A = DIAGON_ANCHORS
    print('  wall_bricks %d (first %s), lanes %d nodes %d edges, windows %s, owl_cages %d'
          % (len(A['wall_bricks']), A['first_brick'], len(A['lanes']['nodes']), len(A['lanes']['edges']),
             [w['name'] for w in A['window_spots']], len(A['owl_cages'])))
    print('  hagrid_path %s' % A['hagrid_path'])
    print('  ollivanders: shelves_box_cells %d, counter %s, boy %s'
          % (len(OLLIVANDERS_ANCHORS['shelves_box_cells']), OLLIVANDERS_ANCHORS['counter'],
             OLLIVANDERS_ANCHORS['boy_counter']))


if __name__ == '__main__':
    main()
