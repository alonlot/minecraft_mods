"""The castle's moving parts: the Grand Staircase's moving flight and the Headmaster's gargoyle stair.

    from mini_hogwarts import mechanisms
    anchors = mechanisms.close(grid)      # after verify(grid): the grid as built is the open, docked castle

The rooms build every moving part in the state a visitor walks through: the moving flight docked on its way up to the
Astronomy gallery, the office stair standing behind the gargoyle. verify() checks that castle. close() then records
each part's other states for the Java side (CastleMechanisms) and turns the grid into the state the schematic ships in:

THE MOVING FLIGHT  ships docked (config 0). Its other config is the flight swung a quarter turn clockwise about
                   rooms_towers.FLIGHT_PIVOT, docked in the middle of the well with the gates moved over. The anchor
                   lists, for both configs, the state of every cell either config uses, and the docked flight's blocks
                   (plus the rail its wall side gets once swung) for the swing's animation.
THE GARGOYLE STAIR ships closed: the gargoyle stands in the stair's door, the treads are sunk (their cells empty, the
                   gallery's floor under them) and a plank cover closes the opening in the office floor. On the
                   password the gargoyle leaps aside and the treads rise out of the floor, bottom first.

check() holds what the closed and swung states promise: in each, nobody walks beside an open drop, every walked floor
is lit, the swung flight's cells and gates are empty in the docked castle (so the swing never overwrites a block it
does not own), and with the stair closed the office is reached by nobody from the entrance.
"""

import math

from . import checks, rooms_towers
from .grid import Grid

T = rooms_towers
CAVE_AIR = 'minecraft:cave_air'
FENCE = 'minecraft:dark_oak_fence'


def _state(grid, p):
    s = grid.get(*p)
    return CAVE_AIR if s is None or checks.block_id(s) in ('air', 'cave_air') else s


def _entries(states):
    return [[p[0], p[1], p[2], s] for p, s in sorted(states.items())]


# ---------------------------------------------------------------- the moving flight

def flight_configs(grid):
    """({cell: state} docked, {cell: state} swung, [(cell, state)] the docked flight's moving blocks, wall rail
    included). Both dicts cover the same cells."""
    flight = T.flight_cells()
    docked = {p: _state(grid, p) for p in flight}
    docked.update({p: _state(grid, p) for p in T.FLIGHT_GATES[0]})
    moving = [(p, docked[p]) for p in sorted(flight) if docked[p] != CAVE_AIR]
    # the swung flight: every block of the docked one turned a quarter clockwise, the wall-side rail added, the gates
    # moved; fences joined as they stand there
    work = Grid()
    work.cells = dict(grid.cells)
    for p in list(flight) + T.FLIGHT_GATES[0]:
        work.set(*p, CAVE_AIR)
    swung_cells = []
    for (x, y, z), s in moving:
        nx, nz = T.flight_turn(x, z)
        work.set(nx, y, nz, rotate(s, 1))
        swung_cells.append((nx, y, nz))
    wall_rail = []
    for x, y, z in T.FLIGHT_WALL_RAIL:
        nx, nz = T.flight_turn(x, z)
        work.set(nx, y, nz, FENCE)
        wall_rail.append((nx, y, nz))
    for p in T.FLIGHT_GATES[1]:
        work.set(*p, FENCE)
    fences = [p for p in swung_cells + wall_rail + T.FLIGHT_GATES[1] if checks.block_id(work.get(*p)).endswith('fence')]
    T.S.connect_fences(work, fences)
    cells = set(docked) | set(swung_cells) | set(wall_rail) | set(T.FLIGHT_GATES[1])
    docked = {p: _state(grid, p) for p in cells}
    swung = {p: _state(work, p) for p in cells}
    # the wall rail swings out of the wall with the flight: drawn in its docked place (inside the wall), turned back
    for (x, y, z), (sx, sy, sz) in zip(T.FLIGHT_WALL_RAIL, wall_rail):
        moving.append(((x, y, z), rotate(swung[(sx, sy, sz)], 3)))
    return docked, swung, moving, work


def rotate(state, turns):
    from .grid import rotate_state
    return rotate_state(state, turns)


def swept_cells(moving, steps=18):
    """{cell} the moving blocks pass through as they swing from docked to swung (sampled every 5 degrees, each block's
    four bottom corners and centre)."""
    px, pz = T.FLIGHT_PIVOT
    out = set()
    for (x, y, z), _ in moving:
        for k in range(steps + 1):
            a = math.radians(90.0 * k / steps)
            for ox, oz in ((0.5, 0.5), (0.05, 0.05), (0.95, 0.05), (0.05, 0.95), (0.95, 0.95)):
                cx, cz = x + ox - px, z + oz - pz
                rx = cx * math.cos(a) - cz * math.sin(a)
                rz = cx * math.sin(a) + cz * math.cos(a)
                out.add((int(math.floor(px + rx)), y, int(math.floor(pz + rz))))
    return out


# ---------------------------------------------------------------- the gargoyle stair

GARGOYLE_OPEN = [(T.GARGOYLE[0], y, T.GARGOYLE[1]) for y in range(T.GALLERY_FEET, T.GALLERY_FEET + 3)]
GARGOYLE_CLOSED = [(T.SPIRAL_DOOR[0], y, T.SPIRAL_DOOR[2]) for y in range(T.GALLERY_FEET, T.GALLERY_FEET + 3)]
COVER = [(x, T.OFFICE_FLOOR_Y, z) for x, z in T.OFFICE_HOLE]
COVER_STATE = 'minecraft:dark_oak_planks'


def tread_columns():
    """[[cell bottom..top]] each tread's column of blocks (the solid under it and the stair), in climbing order."""
    out = []
    for (x, z), feet, _ in T.SPIRAL:
        out.append([(x, y, z) for y in range(T.GALLERY_FEET, feet)])
    return out


def stair_states(grid):
    """{'gargoyle': (open, closed), 'treads': [(open, closed)], 'cover': (open, closed)}, each a {cell: state} over the
    part's cells."""
    gar_open = {p: _state(grid, p) for p in GARGOYLE_OPEN + GARGOYLE_CLOSED}
    gar_closed = {p: CAVE_AIR for p in gar_open}
    for o, c in zip(GARGOYLE_OPEN, GARGOYLE_CLOSED):
        gar_closed[c] = gar_open[o]
    treads = []
    for col in tread_columns():
        treads.append(({p: _state(grid, p) for p in col}, {p: CAVE_AIR for p in col}))
    cover = ({p: _state(grid, p) for p in COVER}, {p: COVER_STATE for p in COVER})
    return {'gargoyle': (gar_open, gar_closed), 'treads': treads, 'cover': cover}


def _apply(grid, states):
    for p, s in states.items():
        grid.set(*p, s)


# ---------------------------------------------------------------- checks

def _open_edges(grid, walk, box, drop_limit=3):
    """[(cell, neighbour)] walked cells in box beside a column a player would fall more than drop_limit down."""
    x0, y0, z0, x1, y1, z1 = box
    cells = grid.cells
    air = 'minecraft:air'
    out = []
    for x, y, z in walk:
        if not (x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1):
            continue
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (checks.passable(cells.get((nx, y, nz), air)) and checks.passable(cells.get((nx, y + 1, nz), air))):
                continue
            drop = 0
            while drop <= drop_limit and not checks.supports(cells.get((nx, y - 1 - drop, nz), air)) \
                    and not checks.climbable(cells.get((nx, y - drop, nz), air)):
                drop += 1
            if drop > drop_limit:
                out.append(((x, y, z), (nx, y, nz)))
    return out


UPPER_WELL = (53, T.NORTH_LANDING_FEET, 139, 69, T.OFFICE_FEET + 1, 157)


def check(grid, start, open_walk):
    """Assert the swung flight's and the closed stair's promises (see the module doc). `grid` is the built (open,
    docked) castle, `open_walk` its walk from `start`."""
    docked, swung, moving, work = flight_configs(grid)
    # 63. the swing owns its cells: every cell the swung flight or its gates fill is empty in the docked castle, and
    #     the docked blocks it lifts are the flight's own; nothing solid stands in the path it sweeps
    for p, s in swung.items():
        if s != CAVE_AIR and docked[p] == s:
            continue
        assert docked[p] == CAVE_AIR or p in T.flight_cells() or p in T.FLIGHT_GATES[0], ('swung flight over a block', p,
                                                                                         docked[p])
    wall = set(T.FLIGHT_WALL_RAIL)
    clash = sorted(p for p in swept_cells([m for m in moving if m[0] not in wall])
                   if p not in docked and not checks.passable(grid.get(*p) or 'minecraft:air'))
    check.clash = clash
    # the only blocks in its way are brushed as it leaves or docks: each touches (same level, one cell over, diagonals
    # too) a cell the flight fills docked or swung, like the landing's corner its bottom tread brushes
    ends = {p for p, s in docked.items() if s != CAVE_AIR} | {p for p, s in swung.items() if s != CAVE_AIR}
    far = [p for p in clash if not any((p[0] + dx, p[1], p[2] + dz) in ends for dx in (-1, 0, 1) for dz in (-1, 0, 1))]
    assert not far and len(clash) <= 6, ('the swing passes through blocks', clash)
    # 64. swung: the landing and the flight are walked to, the gallery is not (the flight has left it), nobody walks
    #     beside a drop in the upper well, every walked step there is lit
    walk_b = checks.reachable(work, start)
    assert (58, T.NORTH_LANDING_FEET, 143) in walk_b, 'swung: the north landing is cut off'
    top = T.flight_turn(66, 143)
    assert (top[0], 58, top[1]) in walk_b, 'swung: the flight\'s far end is not walked to'
    assert T.HELIX_ARRIVAL not in walk_b, 'swung: the gallery is still walked to'
    edges = _open_edges(work, walk_b, UPPER_WELL)
    assert not edges, ('swung: open edges in the upper well', edges[:5])
    dark = [c for c in checks.dark_floor_cells(work, [(53, T.NORTH_LANDING_FEET, 141, 69, 58, 155)]) if c in walk_b]
    assert not dark, ('swung: dark steps', dark[:5])
    edges = _open_edges(grid, open_walk, UPPER_WELL)
    assert not edges, ('docked: open edges in the upper well', edges[:5])
    # 65. the stair closed: the gargoyle in the door, the office reached by nobody from the entrance, no open edge
    #     round the sunk stair or the covered opening, the gallery floor still lit
    st = stair_states(grid)
    closed = Grid()
    closed.cells = dict(grid.cells)
    _apply(closed, st['gargoyle'][1])
    for _, c in st['treads']:
        _apply(closed, c)
    _apply(closed, st['cover'][1])
    walk_c = checks.reachable(closed, start)
    office = T.TOWER_CELLS['office']
    assert office not in walk_c, 'closed: the office is walked to past the gargoyle'
    assert T.TOWER_CELLS['astronomy gallery'] in walk_c, 'closed: the gallery is cut off'
    edges = _open_edges(closed, walk_c, UPPER_WELL)
    assert not edges, ('closed: open edges', edges[:5])
    dark = [c for c in checks.dark_floor_cells(closed, T._row_boxes(T.BAND_ROWS, T.GALLERY_FEET, T.GALLERY_FEET))
            if c in walk_c]
    assert not dark, ('closed: dark gallery floor', dark[:5])
    # and the treads, open, are walked: the office is up them (verify's check 23 on the built castle)
    assert office in open_walk
    return closed


def close(grid, start, open_walk):
    """Check the other states, then write the anchors and turn `grid` into the shipped state (flight docked, stair
    closed). Returns the anchors added."""
    closed = check(grid, start, open_walk)
    docked, swung, moving, _ = flight_configs(grid)
    st = stair_states(grid)
    gar_open, gar_closed = st['gargoyle']
    anchors = {
        'moving_stairs': [{
            'name': 'grand_staircase_upper_flight',
            'pivot': list(T.FLIGHT_PIVOT),
            'turns': [0, 1],
            'configs': [_entries(docked), _entries(swung)],
            'moving': [[p[0], p[1], p[2], s] for p, s in moving],
            'gates': [[list(p) for p in T.FLIGHT_GATES[0]], [list(p) for p in T.FLIGHT_GATES[1]]],
            'riders': sorted([x, feet, z] for cells, feet in T.FLIGHT for x, z in cells)
                      + sorted([T.flight_turn(x, z)[0], feet, T.flight_turn(x, z)[1]] for cells, feet in T.FLIGHT
                               for x, z in cells),
            'near': [61, T.NORTH_LANDING_FEET, 148],
        }],
        'headmaster_stair': {
            'listen': list(GARGOYLE_CLOSED[0]),
            'office_call': list(T.SPIRAL_ARRIVAL),
            'gargoyle': {'open': _entries(gar_open), 'closed': _entries(gar_closed),
                         'slide': [GARGOYLE_OPEN[0][i] - GARGOYLE_CLOSED[0][i] for i in range(3)]},
            'treads': [{'open': _entries(o), 'closed': _entries(c)} for o, c in st['treads']],
            'cover': {'open': _entries(st['cover'][0]), 'closed': _entries(st['cover'][1])},
            'riders': [[x, feet, z] for (x, z), feet, _ in T.SPIRAL] + [list(T.SPIRAL_DOOR), list(T.SPIRAL_ARRIVAL)],
        },
    }
    grid.cells = closed.cells
    return anchors
