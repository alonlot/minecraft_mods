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


def test_census():
    g = Grid()
    g.fill(0, 0, 0, 1, 0, 1, STONE)
    g.set(0, 1, 0, 'minecraft:oak_door[facing=east]')
    c = checks.census(g)
    assert c['minecraft:stone'] == 4 and c['minecraft:oak_door'] == 1


if __name__ == '__main__':
    tests = [(n, f) for n, f in sorted(globals().items()) if n.startswith('test_') and callable(f)]
    for name, fn in tests:
        fn()
        print('ok  ' + name)
    print('%d tests passed' % len(tests))
