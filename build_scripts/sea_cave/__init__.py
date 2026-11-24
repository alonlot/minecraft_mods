"""The Locket sea cave: a leaning sea stack with a cleft at the waterline, a flooded tunnel, an
antechamber above the sea, the blood arch, a descent through the eastern flank and a cavern with a
black lake under the seabed. Pass 1 is shape only, in one stone.

    blocks, entities = sea_cave.build()
"""

from . import frame, shape


CRUMB = 8      # loose rock at most this big is dropped; anything larger is left for the audit


def _drop_crumbs(blocks):
    """Remove tiny bits of rock that the carving and the notch leave hanging in the air, joined to
    nothing. Bigger loose pieces are left so the `connected` audit reports them."""
    solid = {p for p, v in blocks.items() if v[0] == frame.STONE}
    seen = set()
    for s0 in list(solid):
        if s0 in seen:
            continue
        comp, q, grounded = [s0], [s0], s0[1] == 0
        seen.add(s0)
        while q:
            x, y, z = q.pop()
            for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                n = (x + d[0], y + d[1], z + d[2])
                if n in solid and n not in seen:
                    seen.add(n)
                    comp.append(n)
                    q.append(n)
                    grounded = grounded or n[1] == 0
        if not grounded and len(comp) <= CRUMB:
            for p in comp:
                del blocks[p]


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
    _drop_crumbs(blocks)
    # A structure void in the far top corner fixes the template at its full frame size, so the
    # footprint's 2-block margin is real in the written template (a void places nothing).
    corner = (frame.SIZE[0] - 1, frame.SIZE[1] - 1, frame.SIZE[2] - 1)
    blocks[corner] = ('minecraft:structure_void', None, None)
    return blocks, []
