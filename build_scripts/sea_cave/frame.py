"""The sea cave's frame: template size, water heights and the named places the rock is built round.

Coordinates are template-local, before the jigsaw rotates the piece: x 0..79 (east +), y 0..89,
z 0..79 (south +). Every walkable place is named by its *feet* y, the air block a player's feet
occupy; the block they stand on is feet y - 1. Water positions are the top water block.
The numbers come from docs/superpowers/notes/2026-11-24-sea-cave-references.md.
"""

SIZE = (80, 90, 80)

# Sea level: the top water block. With the piece's start_height putting local y 0 at world y 21,
# local 42 is world 63, the vanilla sea surface.
SEA_Y = 42
# The cavern lake's top water block, well below the sea: the cave is a sealed pocket under the bed.
LAKE_Y = 10

STONE = 'minecraft:deepslate'
CAVE_AIR = 'minecraft:cave_air'
WATER = 'minecraft:water'

# --- strata ----------------------------------------------------------------------------------
# The beds dip gently to the east and a little south: a bed boundary sits lower by DIP_X per block
# east and DIP_Z per block south. Pass 1 uses the beds only to step the outline (hard beds jut, soft
# beds recess); Task 3 paints the same beds in stone so the steps and the colour bands agree.
DIP_X = 0.06
DIP_Z = 0.035
DIP_ORIGIN = (45, 40)
# (thickness, recess) from y_eff = -6 upward. recess 0 = hard bed that juts, 1 = soft, 2 = very soft.
BEDS = [
    (5, 0), (3, 1), (4, 0), (2, 2), (6, 0), (3, 1), (4, 0), (2, 1), (5, 0), (3, 2),
    (6, 0), (2, 1), (4, 0), (3, 1), (5, 0), (2, 2), (4, 0), (3, 1), (6, 0), (2, 1),
    (3, 0), (5, 1), (4, 0), (2, 2), (5, 0), (3, 1), (4, 0), (6, 1), (3, 0), (4, 1),
]
BEDS_BASE = -6

# The cleft is a joint at right angles to the beds. Beds dipping east put the joint's normal
# leaning west, so the cleft leans WEST going up: x 37..39 at the waterline, 36..38 by y 46.
CLEFT_LEAN = 'west'

# --- named places (from the reference note) ----------------------------------------------------
SUMMIT = (54, 87, 30)
SHOULDER = (32, 68, 46)
WATERLINE_CENTROID = (45, 42, 40)
NEEDLE = (68, 60, 67)          # note says x 72; pulled west to keep the rim inside the frame
STUMP = (18, 48, 22)

CLEFT_X = (37, 39)          # 3 wide at the waterline
CLEFT_FACE_Z = 57
CLEFT_Y = (39, 51)          # open, inclusive; a 1-wide hairline carries on to CLEFT_HAIR_TOP
CLEFT_HAIR_TOP = 58
# Carved cells inside this box may touch the open sea: it is the cleft mouth.
CLEFT_ZONE = ((33, 43), (36, 60), (53, 63))

TUNNEL = [(38, 39, 57), (40, 37, 52), (43, 36, 47), (45, 37, 44), (46, 41, 41)]   # centres
POOL = ((45, 48), (38, 42), (40, 42))       # x, y (water), z
ANTE_CENTRE = (47, 45, 40)
ANTE_FLOOR = 44                             # solid top; feet 45
ANTE_START = (50, 45, 41)                   # a feet cell on the antechamber floor
ARCH_X = 52
ARCH_Z = (32, 34)
ARCH_FEET = 45

# (x, feet y, z) through the eastern flank, then the mouth.
DESCENT = [(52, 45, 33), (55, 45, 33), (59, 43, 31), (63, 41, 34), (64, 39, 39), (62, 37, 43),
           (63, 35, 47), (65, 33, 51), (64, 31, 55), (61, 29, 52), (58, 27, 48), (56, 26, 46)]
DESCENT_HALFWAY = (62, 37, 43)
# The last two legs run into the cavern wall by design; everything before them keeps 4 blocks of rock.
DESCENT_TAIL_FROM = 9
MOUTH = (56, 26, 46)
MOUTH_LEDGE = ((53, 55), 25, (45, 47))      # x range, solid top, z range

# Shore ramp (x, feet y, z), never over the water.
SHORE_PATH = [(54, 26, 47), (53, 23, 50), (51, 20, 54), (48, 17, 58), (45, 14, 60), (42, 12, 61)]
RING = (41, 12, 62)
DOCK = (40, 10, 57)
SKIFF = [(40, 10, 57), (39, 10, 52), (35, 10, 48), (32, 10, 45)]

ISLAND_CENTRE = (25.5, 42.0)
ISLAND_RADIUS = 6
ISLAND_TOP = (24, 14, 41)                   # solid top 14, feet 15
BASIN = (24, 15, 41)
GOBLET = (25, 15, 42)

CAVERN_BOX = ((10, 60), (0, 36), (24, 66))   # x, y, z: where cavern cells can be
VAULT = (36, 35, 40)                        # highest cavern air
FALLEN_SLAB = ((16, 22), (9, 12), (31, 34))    # note: x 13..19; the west wall moved east
NORTH_LEDGE = ((20, 40), 18, (27, 29))      # x range, solid top, z range
DRIP_SW = ((17, 23), (22, 24), (52, 59))
DRIP_SE = ((44, 50), (23, 25), (60, 64))
BEACH = ((34, 50), (59, 63))                # x, z


def bed_eff(x, y, z):
    """Height measured along the beds: equal values lie on one bed boundary."""
    return y + DIP_X * (x - DIP_ORIGIN[0]) + DIP_Z * (z - DIP_ORIGIN[1])


def _bed_table():
    out, y = [], BEDS_BASE
    for i, (t, r) in enumerate(BEDS):
        out.append((y, y + t, r, i))
        y += t
    return out


BED_TABLE = _bed_table()


def bed_at(e):
    """(index, recess, bottom, top) of the bed containing bed height e."""
    for lo, hi, r, i in BED_TABLE:
        if lo <= e < hi:
            return i, r, lo, hi
    lo, hi, r, i = BED_TABLE[-1]
    return i, r, lo, hi
