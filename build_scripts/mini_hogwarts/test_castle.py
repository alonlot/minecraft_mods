"""Tests for the Mini Hogwarts generator's grid, schematic writer and checks.

    python -I build_scripts/mini_hogwarts/test_castle.py

Plain asserts, run as a script (no pytest needed). -I puts nothing on sys.path, so the
build_scripts folder is added by hand below.
"""

import os
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

from mini_hogwarts import checks, grid  # noqa: E402
from mini_hogwarts.grid import Grid, load_schem, paste, rotate_state, write_schem  # noqa: E402

V1 = os.path.join(HERE, 'source', 'mini_hogwarts_v1.schem')
STONE = 'minecraft:stone'


def props(state):
    name, _, rest = state.partition('[')
    return name, set(rest.rstrip(']').split(',')) if rest else set()


def test_grid_basics():
    g = Grid()
    g.set(1, 2, 3, STONE)
    assert g.get(1, 2, 3) == STONE and g.get(0, 0, 0) == 'minecraft:air'
    g.fill(0, 0, 0, 2, 0, 2, 'minecraft:dirt')
    assert len(g.cells) == 10 and g.bounds() == (0, 0, 0, 2, 2, 3)
    g.set(1, 2, 3, 'minecraft:air')          # placing air clears
    g.clear(0, 0, 0)
    assert (1, 2, 3) not in g.cells and (0, 0, 0) not in g.cells
    assert Grid().bounds() == (0, 0, 0, 0, 0, 0)


def test_roundtrip():
    a = load_schem(V1)
    assert len(a.cells) > 10000, len(a.cells)
    with tempfile.TemporaryDirectory() as tmp:
        out = os.path.join(tmp, 'rt.schem')
        write_schem(a, out)
        b = load_schem(out)
        assert a.cells == b.cells
        # the header fields MiniHogwartsSchematic.java reads, with the tag types it reads them as
        from mini_hogwarts.grid import _nbt
        root = _nbt.read_nbt(out)
        s = root['Schematic']
        assert s['Version'] == 3 and s['DataVersion'] == 3953
        mx, my, mz = (m + 1 for m in a.bounds()[3:])
        assert (s['Width'], s['Height'], s['Length']) == (mx, my, mz)
        assert s['Blocks']['Palette']['minecraft:air'] == 0
        assert isinstance(s['Blocks']['Data'], (bytes, bytearray))
        w, h, l, by_id, cells = _nbt.decode_schem(root)
        assert len(cells) == w * h * l


def test_roundtrip_wide_palette():
    # >127 palette entries forces two-byte varints
    g = Grid()
    for i in range(300):
        g.set(i, 0, 0, 'minecraft:test_block_%d' % i)
    with tempfile.TemporaryDirectory() as tmp:
        out = os.path.join(tmp, 'wide.schem')
        write_schem(g, out)
        assert load_schem(out).cells == g.cells


def test_rotate_state():
    n, p = props(rotate_state('minecraft:oak_stairs[facing=north,half=bottom,shape=straight]', 1))
    assert 'facing=east' in p and 'half=bottom' in p and 'shape=straight' in p
    assert 'facing=north' in props(rotate_state('minecraft:oak_stairs[facing=east,half=bottom,shape=straight]', 3))[1]
    assert rotate_state('minecraft:oak_log[axis=x]', 1) == 'minecraft:oak_log[axis=z]'
    assert rotate_state('minecraft:oak_log[axis=x]', 2) == 'minecraft:oak_log[axis=x]'
    assert rotate_state('minecraft:oak_log[axis=y]', 1) == 'minecraft:oak_log[axis=y]'
    assert rotate_state('minecraft:white_banner[rotation=2]', 1) == 'minecraft:white_banner[rotation=6]'
    assert rotate_state('minecraft:white_banner[rotation=14]', 1) == 'minecraft:white_banner[rotation=2]'
    # east -> south, south -> west, west -> north, north -> east
    wall = 'minecraft:cobblestone_wall[east=low,north=none,south=tall,up=true,west=none]'
    n, p = props(rotate_state(wall, 1))
    assert n == 'minecraft:cobblestone_wall'
    assert p == {'east=none', 'north=none', 'south=low', 'up=true', 'west=tall'}, p
    assert props(rotate_state(wall, 4))[1] == props(wall)[1]
    # hinge and stair shape survive; up/down facing is not a compass direction
    door = 'minecraft:oak_door[facing=north,half=lower,hinge=left,open=false]'
    assert props(rotate_state(door, 2))[1] == {'facing=south', 'half=lower', 'hinge=left', 'open=false'}
    assert 'facing=up' in props(rotate_state('minecraft:dispenser[facing=up]', 1))[1]
    assert rotate_state('minecraft:stone', 1) == 'minecraft:stone'


def test_paste_rotation():
    # a 3 (x) by 1 by 2 (z) piece: stairs facing north at its north-west corner
    src = {(0, 0, 0): 'minecraft:oak_stairs[facing=north]', (2, 0, 1): STONE}
    for turns, stair_at, stone_at, size, facing in [
            (0, (0, 0, 0), (2, 0, 1), (3, 1, 2), 'north'),
            (1, (1, 0, 0), (0, 0, 2), (2, 1, 3), 'east'),
            (2, (2, 0, 1), (0, 0, 0), (3, 1, 2), 'south'),
            (3, (0, 0, 2), (1, 0, 0), (2, 1, 3), 'west')]:
        g = Grid()
        got = paste(g, src, (0, 0, 0), turns, (3, 1, 2))
        assert got == size, (turns, got)
        assert g.get(*stair_at) == 'minecraft:oak_stairs[facing=%s]' % facing, (turns, g.cells)
        assert g.get(*stone_at) == STONE, (turns, g.cells)
    g = Grid()
    paste(g, src, (10, 5, 20), 1, (3, 1, 2))
    assert g.get(11, 5, 20).startswith('minecraft:oak_stairs')


def _floor_with_wall(gap):
    g = Grid()
    g.fill(0, 0, 0, 4, 0, 4, STONE)
    g.fill(0, 1, 2, 4, 2, 2, STONE)        # a 2-tall wall across the middle
    if gap:
        g.clear(2, 1, 2)
        g.clear(2, 2, 2)
    return g


def test_reachable_small():
    seen = checks.reachable(_floor_with_wall(True), (0, 1, 0))
    assert (4, 1, 4) in seen and (0, 1, 0) in seen and (2, 1, 2) in seen
    seen = checks.reachable(_floor_with_wall(False), (0, 1, 0))
    assert (0, 1, 1) in seen and (4, 1, 4) not in seen and (0, 1, 4) not in seen


def test_reachable_steps_doors_and_drops():
    g = Grid()
    g.fill(0, 0, 0, 9, 0, 0, STONE)
    g.set(3, 1, 0, STONE)                   # a one-block step up
    g.fill(5, 1, 0, 9, 1, 0, STONE)         # a floor at y=1 on x 5..9 (the step block is the way up)
    seen = checks.reachable(g, (0, 1, 0))
    assert (3, 2, 0) in seen                # stood on the step
    assert (4, 1, 0) in seen                # stepped back down
    # a two-high ledge cannot be climbed
    g2 = Grid()
    g2.fill(0, 0, 0, 5, 0, 0, STONE)
    g2.fill(3, 1, 0, 3, 2, 0, STONE)
    assert (4, 1, 0) not in checks.reachable(g2, (0, 1, 0))
    # drops of up to 3 are fine, 4 is not
    g3 = Grid()
    g3.fill(0, 3, 0, 1, 3, 0, STONE)        # high floor, feet at y=4
    g3.fill(2, 0, 0, 3, 0, 0, STONE)        # low floor, feet at y=1: a drop of 3
    assert (2, 1, 0) in checks.reachable(g3, (0, 4, 0))
    g4 = Grid()
    g4.fill(0, 4, 0, 1, 4, 0, STONE)        # feet at y=5: a drop of 4
    g4.fill(2, 0, 0, 3, 0, 0, STONE)
    assert (2, 1, 0) not in checks.reachable(g4, (0, 5, 0))
    # a door, a carpet and a ladder do not stop the walk
    g5 = Grid()
    g5.fill(0, 0, 0, 4, 0, 0, STONE)
    g5.fill(1, 1, 0, 1, 2, 0, 'minecraft:oak_wall_sign')   # not a door, but passable decoration
    g5.set(2, 1, 0, 'minecraft:oak_door[facing=east,half=lower,hinge=left,open=false]')
    g5.set(2, 2, 0, 'minecraft:oak_door[facing=east,half=upper,hinge=left,open=false]')
    g5.set(3, 1, 0, 'minecraft:red_carpet')
    assert (4, 1, 0) in checks.reachable(g5, (0, 1, 0))


def _ladder_tower(ladder):
    """Ground floor, a wall, a platform at y=6 up on x 1..3, and (optionally) a ladder to it."""
    g = Grid()
    g.fill(-1, 0, -1, 3, 0, 1, STONE)                 # ground
    g.fill(-1, 1, -1, -1, 8, 1, STONE)                # the wall the ladder hangs on
    g.fill(1, 6, -1, 3, 6, 1, STONE)                  # the platform, feet at y=7
    if ladder:
        for y in range(1, 7):                         # a 6-tall column, x=0, z=0
            g.set(0, y, 0, 'minecraft:ladder[facing=east]')
    return g


def test_reachable_ladder():
    seen = checks.reachable(_ladder_tower(True), (1, 1, 0))
    assert (0, 6, 0) in seen and (2, 7, 0) in seen, 'ladder should reach the platform'
    # and back down again
    assert (1, 1, 0) in checks.reachable(_ladder_tower(True), (2, 7, 0))
    assert (2, 7, 0) not in checks.reachable(_ladder_tower(False), (1, 1, 0))
    # scaffolding and vines climb too
    g = _ladder_tower(False)
    for y in range(1, 7):
        g.set(0, y, 0, 'minecraft:scaffolding[bottom=false,distance=0,waterlogged=false]' if y % 2 else
              'minecraft:vine[east=true,north=false,south=false,up=false,west=false]')
    assert (2, 7, 0) in checks.reachable(g, (1, 1, 0))


def test_write_negative_coordinates():
    g = Grid()
    g.set(0, 0, 0, STONE)
    g.set(-1, 0, 0, STONE)
    with tempfile.TemporaryDirectory() as tmp:
        try:
            write_schem(g, os.path.join(tmp, 'neg.schem'))
        except ValueError:
            return
    raise AssertionError('write_schem accepted a block at a negative coordinate')


def test_dark_floor():
    g = Grid()
    g.fill(0, 0, 0, 19, 0, 0, STONE)
    g.set(0, 2, 0, 'minecraft:lantern[hanging=true]')
    dark = checks.dark_floor_cells(g, [(0, 1, 0, 19, 2, 0)])
    assert sorted(dark) == [(x, 1, 0) for x in range(7, 20)], dark
    # a lit candle counts, an unlit one does not
    g.set(19, 1, 0, 'minecraft:candle[candles=1,lit=true]')
    dark = checks.dark_floor_cells(g, [(0, 1, 0, 19, 2, 0)])
    assert sorted(dark) == [(x, 1, 0) for x in range(7, 12)], dark
    g.set(19, 1, 0, 'minecraft:candle[candles=1,lit=false]')
    dark = checks.dark_floor_cells(g, [(0, 1, 0, 19, 2, 0)])
    assert (13, 1, 0) in dark and (6, 1, 0) not in dark


def test_cave_air_is_air():
    # a later task stores minecraft:cave_air in grid.cells to mark interior air to be cleared in the world: every
    # air test must treat it as air (walkable headroom, never support, no wall for the walk or for light)
    cave = 'minecraft:cave_air'
    g = Grid()
    g.fill(0, 0, 0, 4, 0, 0, STONE)
    g.cells[(2, 1, 0)] = cave                 # feet and head cells marked, not stored as absent
    g.cells[(2, 2, 0)] = cave
    g.cells[(4, 0, 1)] = cave                 # a marked cell beside the floor: not a floor
    assert g.is_air(2, 1, 0) and g.is_air(9, 9, 9) and not g.is_air(0, 0, 0)
    assert grid.air_at(g.cells, (2, 2, 0)) and not grid.air_at(g.cells, (1, 0, 0))
    assert checks.passable(cave) and not checks.supports(cave)
    seen = checks.reachable(g, (0, 1, 0))
    assert (4, 1, 0) in seen and (2, 1, 0) in seen and (4, 1, 1) not in seen
    g.set(0, 2, 0, 'minecraft:lantern[hanging=true]')
    dark = checks.dark_floor_cells(g, [(0, 1, 0, 4, 2, 0)])
    assert not dark, dark                     # light crosses the marked cells


def test_reachable_no_step_onto_fence():
    # a fence, a wall or a closed fence gate is a block and a half tall: a player cannot jump onto it, so a rail in an
    # opening is a rail (a full block one high is still a step)
    for rail in ('minecraft:dark_oak_fence[east=false,north=false,south=false,west=false,waterlogged=false]',
                 'minecraft:cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]',
                 'minecraft:oak_fence_gate[facing=east,in_wall=false,open=false,powered=false]'):
        g = Grid()
        g.fill(0, 0, 0, 2, 0, 0, STONE)        # a floor, feet at y=1, ending at x=2
        g.set(3, 1, 0, rail)                   # the rail on the sill of an opening, on top of the wall below it
        g.set(3, 0, 0, STONE)
        seen = checks.reachable(g, (0, 1, 0))
        assert (3, 2, 0) not in seen and (4, 1, 0) not in seen, (rail, sorted(seen))
    g = Grid()
    g.fill(0, 0, 0, 3, 0, 0, STONE)
    g.set(3, 1, 0, STONE)
    assert (3, 2, 0) in checks.reachable(g, (0, 1, 0))


def test_seal_interiors():
    # a closed stone box with air inside, a room box reaching out through its open doorway, and open air outside
    g = Grid()
    g.fill(0, 0, 0, 6, 4, 6, STONE)
    g.fill(1, 1, 1, 5, 3, 5, 'minecraft:air')               # hollow
    g.set(3, 2, 3, 'minecraft:lantern[hanging=false,waterlogged=false]')
    n, filled = grid.seal_interiors(g, [])
    assert filled == 0 and n == 5 * 3 * 5 - 1 and g.cells[(1, 1, 1)] == 'minecraft:cave_air', n
    assert g.cells[(3, 2, 3)].startswith('minecraft:lantern')  # blocks are never touched
    assert (9, 9, 9) not in g.cells and (-1, 0, 0) not in g.cells
    # cut a doorway: the inside now connects to the outside, so only the room box keeps it sealed, and the cells
    # outside both stay plain air
    h = Grid()
    h.fill(0, 0, 0, 6, 4, 6, STONE)
    h.fill(1, 1, 1, 5, 3, 5, 'minecraft:air')
    h.fill(3, 1, 0, 3, 2, 0, 'minecraft:air')
    room = (1, 1, 1, 5, 3, 5)
    door = (3, 1, 0, 3, 2, 0)
    n, filled = grid.seal_interiors(h, [room, door])
    assert n == 5 * 3 * 5 + 2 and filled == 0, n
    assert h.cells[(3, 1, 0)] == 'minecraft:cave_air' and h.cells[(5, 3, 5)] == 'minecraft:cave_air'
    assert (3, 1, -1) not in h.cells and (3, 5, 3) not in h.cells
    # a second pass changes nothing (cave_air is already air)
    assert grid.seal_interiors(h, [room, door]) == (0, 0)
    # R35: a pocket in solid rock (every face-neighbour is rock) that touches no box is filled, not sealed; a pocket
    # with a building block on its rim, or one a box reaches, is sealed
    rock = lambda s: s == STONE
    k = Grid()
    k.fill(0, 0, 0, 8, 4, 4, STONE)
    k.fill(1, 1, 1, 2, 2, 2, 'minecraft:air')              # a rock pocket
    k.fill(4, 1, 1, 5, 2, 2, 'minecraft:air')              # a pocket with a plank on its rim
    k.set(6, 1, 1, 'minecraft:oak_planks')
    k.fill(7, 1, 1, 7, 2, 2, 'minecraft:air')              # a rock pocket a room box reaches
    n, filled = grid.seal_interiors(k, [(7, 1, 1, 7, 1, 1)], rock=rock)
    assert filled == 8 and k.cells[(1, 1, 1)] == STONE, (n, filled)
    assert n == 8 + 4 and k.cells[(4, 1, 1)] == 'minecraft:cave_air' and k.cells[(7, 2, 2)] == 'minecraft:cave_air'


def test_unlit_windows():
    from mini_hogwarts import rooms_corridors as K
    g = Grid()
    g.fill(0, 0, 0, 6, 4, 0, STONE)                          # a wall along x, the outside to the north (z -1)
    g.cells.update({(x, y, z): 'minecraft:cave_air' for x in range(7) for y in range(5) for z in (1, 2, 3)})
                                                             # a sealed room to the south (Grid.set never stores air)
    g.set(2, 2, 0, 'minecraft:black_stained_glass_pane[east=false,north=false,south=false,west=false]')
    g.set(5, 2, 0, 'minecraft:glass')
    g.set(5, 2, 1, 'minecraft:bookshelf')                    # glass with a block behind it is not a window
    interior = K.sealed_interior(g.cells)
    faces = K.window_faces(g.cells, interior)
    assert [f[:2] for f in faces] == [((2, 2, 0), (2, 2, 1))], faces
    assert len(K.unlit_windows(g.cells, interior)) == 1
    g.cells[(2, 3, 2)] = 'minecraft:light[level=13,waterlogged=false]'      # 2 from the cell behind the pane
    assert K.unlit_windows(g.cells, interior) == []
    g.cells[(2, 3, 2)] = 'minecraft:light[level=0,waterlogged=false]'       # a dark light block lights nothing
    assert len(K.unlit_windows(g.cells, K.sealed_interior(g.cells))) == 1


def test_census():
    g = Grid()
    g.fill(0, 0, 0, 1, 0, 1, STONE)
    g.set(0, 1, 0, 'minecraft:oak_door[facing=east]')
    c = checks.census(g)
    assert c['minecraft:stone'] == 4 and c['minecraft:oak_door'] == 1


def test_kitchen_table_dressing():
    from mini_hogwarts import rooms_below as B
    # four tables three wide and fifty long, like the kitchens' (the middle column is x 1, 11, 21, 31)
    tables = {(x0 + dx, z) for x0 in (0, 10, 20, 30) for dx in range(3) for z in range(50)}
    laid = B.table_dressing(tables)
    assert laid == B.table_dressing(tables), 'the dressing is seeded: the same every build'
    assert set(laid) <= tables
    for run in B.table_groups({x for x, _ in tables}):
        on = {c: s for c, s in laid.items() if c[0] in run}
        ids = [checks.block_id(s) for s in on.values()]
        gold = ids.count('light_weighted_pressure_plate')
        wood = sum(1 for i in ids if i in B.WOODEN_PLATES)
        assert 0 < len(on) <= 150 / 3, ('at most one item per three cells', len(on))
        assert 3 <= gold <= 6 <= B.TABLE_GOLD_MAX, ('a few gold pieces a table', gold)
        assert wood > len(on) / 2, ('mostly wooden plates', wood, len(on))
        assert 2 <= ids.count('cake') <= 4 and 1 <= ids.count('candle') <= 3, ids
        # nothing in a grid: on no column do the items stand at one even spacing
        for x in run:
            zs = sorted(z for (cx, z) in on if cx == x)
            gaps = {b - a for a, b in zip(zs, zs[1:])}
            assert len(zs) < 3 or len(gaps) > 1, ('a regular row of items', x, zs)
    # build_kitchen_tables lays exactly that on the table tops, and the verify report reads it back
    g = Grid()
    B.build_kitchen_tables(g, tables)
    report = B.table_dressing_report(g.cells, tables)
    assert [t['items'] for t in report] == [sum(1 for c in laid if c[0] in run)
                                            for run in B.table_groups({x for x, _ in tables})]
    assert all(t['items'] <= B.TABLE_DRESSING_MAX * t['cells'] and t['gold'] <= B.TABLE_GOLD_MAX for t in report)


if __name__ == '__main__':
    tests = [(n, f) for n, f in sorted(globals().items()) if n.startswith('test_') and callable(f)]
    for name, fn in tests:
        fn()
        print('ok  ' + name)
    print('%d tests passed' % len(tests))
