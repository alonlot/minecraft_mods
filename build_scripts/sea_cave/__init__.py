"""The Locket sea cave: a leaning sea stack with a cleft at the waterline, a flooded tunnel, an
antechamber above the sea, the blood arch, a descent through the eastern flank and a cavern with a
black lake under the seabed. Pass 1 is shape only, in one stone.

    blocks, entities = sea_cave.build()
"""

from . import frame, shape


def build():
    blocks = {}
    for x in range(frame.SIZE[0]):
        for z in range(frame.SIZE[2]):
            for y in range(frame.SIZE[1]):
                c = shape.carved(x, y, z)
                if c == 'water':
                    blocks[(x, y, z)] = (frame.WATER, None, None)
                elif c == 'air':
                    blocks[(x, y, z)] = (frame.CAVE_AIR, None, None)
                elif shape.solid(x, y, z):
                    blocks[(x, y, z)] = (frame.STONE, {'axis': 'y'}, None)
    return blocks, []
