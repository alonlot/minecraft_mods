"""Audits for the Mini Hogwarts generator: block census, walkability and lighting.

    from mini_hogwarts import checks
    checks.census(grid)                       # Counter of block ids
    checks.reachable(grid, start)             # every feet cell a player can walk to from start
    checks.dark_floor_cells(grid, boxes)      # walkable interior cells too far from any light

The walk model is deliberately the player's, not the mob's: two cells of headroom, a solid
(or stair / slab) block underfoot, a step up of one block, a drop of up to three. Doors,
carpets, pressure plates, light blocks, ladders and scaffolding do not stop the walk.
"""

from collections import Counter, deque

from . import grid as _grid

_split = _grid._nbt.split_state

# Cells a player passes straight through (or stands inside): decoration with no collision box
# worth blocking on. Matched on the block id after `minecraft:`.
_PASSABLE_NAMES = frozenset((
    'torch', 'wall_torch', 'soul_torch', 'soul_wall_torch', 'redstone_torch', 'redstone_wall_torch',
    'lever', 'light', 'ladder', 'scaffolding', 'vine', 'grass', 'short_grass', 'tall_grass', 'fern',
    'large_fern', 'dead_bush', 'string', 'tripwire', 'snow', 'fire', 'soul_fire', 'redstone_wire',
    'rail', 'powered_rail', 'detector_rail', 'activator_rail', 'lantern', 'soul_lantern', 'end_rod',
    'candle', 'cave_vines', 'cave_vines_plant', 'hanging_roots', 'glow_lichen', 'item_frame',
    'glow_item_frame', 'painting', 'moss_carpet', 'pale_moss_carpet', 'flower_pot',
))
_PASSABLE_SUFFIXES = (
    '_door', '_carpet', '_pressure_plate', '_sign', '_wall_sign', '_hanging_sign', '_wall_hanging_sign',
    '_banner', '_wall_banner', '_button', '_candle', '_torch', '_wall_torch', '_head', '_wall_head',
    '_skull', '_wall_skull', '_sapling', '_tulip', '_orchid',
    '_fungus', '_roots', '_rail',
)
_PASSABLE_FLOWERS = frozenset((
    'dandelion', 'poppy', 'azure_bluet', 'oxeye_daisy', 'cornflower', 'lily_of_the_valley',
    'sunflower', 'lilac', 'rose_bush', 'peony', 'wither_rose', 'torchflower',
))

_LIGHT_NAMES = frozenset((
    'torch', 'wall_torch', 'soul_torch', 'soul_wall_torch', 'lantern', 'soul_lantern', 'campfire',
    'soul_campfire', 'glowstone', 'sea_lantern', 'fire', 'soul_fire', 'shroomlight', 'end_rod',
    'jack_o_lantern', 'candle',
))

DARK_RADIUS = 7                   # a floor cell farther than this from a light is "dark"


def _id(state):
    name = state.split('[', 1)[0]
    return name[len('minecraft:'):] if name.startswith('minecraft:') else name


def _is_air(state):
    return _grid.is_air(state)


def passable(state):
    """True when a player can occupy the cell: air or collision-free decoration (doors count)."""
    if _is_air(state):
        return True
    name = _id(state)
    if name.endswith('_trapdoor'):
        return 'open=true' in state
    return (name in _PASSABLE_NAMES or name in _PASSABLE_FLOWERS
            or any(name.endswith(s) for s in _PASSABLE_SUFFIXES))


def supports(state):
    """True when a player can stand on top of the block (solid, stair or slab; not decoration/liquid)."""
    if _is_air(state) or passable(state):
        return False
    name = _id(state)
    return name not in ('water', 'lava', 'bubble_column', 'powder_snow')


def is_light(state):
    """True for blocks that give off light for the purposes of the dark-floor audit."""
    name = _id(state)
    if name == 'light':
        return 'level=0' not in state
    if name.endswith('candle'):                       # candle, white_candle, ... (not the cake)
        return 'lit=true' in state
    if name in ('campfire', 'soul_campfire'):
        return 'lit=false' not in state
    return name in _LIGHT_NAMES or name.endswith('_froglight')


def climbable(state):
    """True for blocks a player climbs vertically (a column of them is a way up and down)."""
    return _id(state) in ('ladder', 'scaffolding', 'vine', 'cave_vines', 'cave_vines_plant')


def census(grid):
    """Counter of block ids (no properties, no air) over the whole grid."""
    return Counter(s.split('[', 1)[0] for s in grid.cells.values())


def _walkable(cells, x, y, z):
    get = cells.get
    return (passable(get((x, y, z), _grid.AIR)) and passable(get((x, y + 1, z), _grid.AIR))
            and supports(get((x, y - 1, z), _grid.AIR)))


def reachable(grid, start, max_cells=2_000_000):
    """Every walkable feet cell reachable from `start` (a feet cell), as a set of (x, y, z).

    A cell is walkable when its feet and head cells are passable and the block below supports
    the player, or the feet cell is climbable (ladder, scaffolding, vine). Moves are the four
    horizontal neighbours: level, one step up (needing headroom over the cell being left too,
    as a jump does), or a drop of up to three blocks (landing on a ladder counts); plus up and
    down through a column of climbable blocks. Stops expanding once max_cells cells are found.
    """
    cells = grid.cells
    air = _grid.AIR

    def free(x, y, z):
        return passable(cells.get((x, y, z), air))

    seen = {start}
    queue = deque([start])
    while queue and len(seen) < max_cells:
        x, y, z = queue.popleft()
        if climbable(cells.get((x, y, z), air)):                  # climb the column
            for dy in (1, -1):
                above = (x, y + dy, z)
                if above in seen or not free(*above) or not free(x, y + dy + 1, z):
                    continue
                if climbable(cells.get(above, air)) or (dy < 0 and supports(cells.get((x, y - 2, z), air))):
                    seen.add(above)
                    queue.append(above)
        for nx, nz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            dest = None
            if free(nx, y, nz) and free(nx, y + 1, nz):
                if supports(cells.get((nx, y - 1, nz), air)) or climbable(cells.get((nx, y, nz), air)):
                    dest = (nx, y, nz)
                else:
                    for drop in (1, 2, 3):                        # walk off an edge, land within 3
                        if not free(nx, y - drop, nz):
                            break
                        if (supports(cells.get((nx, y - drop - 1, nz), air))
                                or climbable(cells.get((nx, y - drop, nz), air))):
                            dest = (nx, y - drop, nz)
                            break
            elif (free(x, y + 2, z) and free(nx, y + 1, nz) and free(nx, y + 2, nz)
                  and supports(cells.get((nx, y, nz), air))):
                dest = (nx, y + 1, nz)                            # one step up
            if dest is not None and dest not in seen:
                seen.add(dest)
                queue.append(dest)
    return seen


def dark_floor_cells(grid, boxes):
    """Walkable cells inside `boxes` that are more than DARK_RADIUS from any light source.

    boxes: inclusive (x0, y0, z0, x1, y1, z1) interiors. Distance is Manhattan, travelling
    through passable cells only (light does not go through walls), so a torch behind a wall
    does not light the next room. Returns a sorted list of feet cells.
    """
    cells = grid.cells
    air = _grid.AIR
    dist = {}
    queue = deque()
    for pos, state in cells.items():
        if is_light(state):
            dist[pos] = 0
            queue.append(pos)
    while queue:
        pos = queue.popleft()
        d = dist[pos]
        if d >= DARK_RADIUS:
            continue
        x, y, z = pos
        for nxt in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
            if nxt not in dist and passable(cells.get(nxt, air)):
                dist[nxt] = d + 1
                queue.append(nxt)
    out = []
    for x0, y0, z0, x1, y1, z1 in boxes:
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                for x in range(min(x0, x1), max(x0, x1) + 1):
                    if _walkable(cells, x, y, z) and (x, y, z) not in dist:
                        out.append((x, y, z))
    return sorted(set(out))
