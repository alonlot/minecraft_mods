"""The castle frame every Mini Hogwarts generator module shares.

The old castle's cells move by BASE_SHIFT into the new schematic's frame (x east, y up, z south,
everything >= 0). The 60 the plan started from left no room north of the Great Hall for its rock
skirt (the hall's north end stands 24 blocks above open ground), so it is 60 + the skirt's reach.
MAIN_FLOOR_Y is the floor block layer of the castle's main level (the gatehouse and the viaduct
deck); players walk at MAIN_FLOOR_Y + 1.

Kept in its own module so room modules can import it without importing build_castle (which
imports them).
"""

BASE_SHIFT = (0, 0, 84)
MAIN_FLOOR_Y = 24
