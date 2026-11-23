"""The mod's decorations, set into the finished rooms of Mini Hogwarts. The last room step: every other room has
built its walls, furniture and anchors before this runs, and the castle's checks run over the result.

    from mini_hogwarts import rooms_decor
    stats = rooms_decor.place_decor(grid)

Nothing here moves or replaces what a room built. A piece only goes into a cell that is air, on a floor a player can
stand on, with a solid wall beside it (or, for the trunks, at the foot of a bed), at least two cells from any anchor
(chests, student and elf spots, statues, paintings, the stool...) and from any door, and away from the other pieces.
The rooms' own checks (reachability, sealed air, free anchor cells) then hold it to that.

What goes where:
  entrance hall          the four House Points Hourglasses in a row against one wall, the four house banners and
                         the four house crest plaques
  Gryffindor common room two armchairs, the crest plaque and two banners, a chess board, an owl cage, a broom rack
                         and the Quidditch Cup
  Gryffindor dormitory   a school trunk at the foot of every bed, an owl cage, a banner
  Slytherin common room  crest plaques and banners, a chess board, a writing desk, candles
  Library                writing desks, scrolls, book piles, candles, floating candles over the floor
  Astronomy gallery      brass telescopes, looking out
  Headmaster's office    a writing desk, scrolls, books, a telescope, candles
  Charms, Transfiguration, Defence   a teacher's desk, scrolls, candles (books in Charms and Defence)
The Great Hall is furnished by its own step (hall.furnish_hall: tables, benches, staff chairs, banners).

Before the kits, every vanilla house-colour wall banner left in the castle (the west gallery's, the Slytherin
dungeon's) becomes the mod's two-high hanging house banner, its rod where the vanilla banner hung, when the cell
below it is free and walled; otherwise the vanilla banner stays.
"""

from . import checks

M = 'hogwartsmagic:'
HOUSES = ('gryffindor', 'ravenclaw', 'hufflepuff', 'slytherin')
DIRS = ((-1, 0, 'east'), (1, 0, 'west'), (0, -1, 'south'), (0, 1, 'north'))   # (offset to the wall, facing out of it)
TOWARD = {'east': 'west', 'west': 'east', 'south': 'north', 'north': 'south'}
NO_FACING = ('candle_cluster', 'enchanted_book_pile', 'floating_candle')

# room -> (box x0, y0, z0, x1, y1, z1 of the floor area, stand height, kit). A kit entry is 'piece', 'wall:piece'
# (hung on a wall one block up), 'banner:house' (two high, hung one block up), 'look:piece' (stands against a wall
# facing it: telescopes look out) or 'float:piece' (four blocks over the floor, under the ceiling).
ROOM_KITS = {
    'entrance hall': ((46, 25, 112, 76, 25, 137), 25,
                      ['hourglasses'] + ['banner:%s' % h for h in HOUSES]
                      + ['wall:%s_crest_plaque' % h for h in HOUSES]),
    'gryffindor common room': ((56, 45, 169, 66, 45, 179), 45,
                               ['common_room_armchair', 'common_room_armchair', 'wall:gryffindor_crest_plaque',
                                'banner:gryffindor', 'wizard_chess_board', 'owl_cage', 'broom_rack',
                                'quidditch_cup', 'banner:gryffindor']),
    'gryffindor dormitory': ((56, 52, 169, 66, 52, 179), 52, ['trunks', 'owl_cage', 'banner:gryffindor']),
    'slytherin common room': ((47, 9, 44, 75, 9, 63), 9,
                              ['wall:slytherin_crest_plaque', 'wall:slytherin_crest_plaque', 'banner:slytherin',
                               'banner:slytherin', 'wizard_chess_board', 'writing_desk', 'candle_cluster',
                               'candle_cluster']),
    'library': ((30, 12, 174, 46, 12, 203), (12, 13),
                ['writing_desk', 'writing_desk', 'scroll_stack', 'scroll_stack', 'enchanted_book_pile',
                 'enchanted_book_pile', 'candle_cluster', 'candle_cluster'] + ['float:floating_candle'] * 8),
    'astronomy gallery': ((53, 59, 140, 69, 59, 156), 59, ['look:brass_telescope'] * 4 + ['scroll_stack']),
    "headmaster's office": ((53, 64, 140, 69, 64, 156), 64,
                            ['writing_desk', 'scroll_stack', 'enchanted_book_pile', 'look:brass_telescope',
                             'candle_cluster', 'enchanted_book_pile']),
    'charms': ((41, 26, 185, 48, 26, 189), 26, ['writing_desk', 'scroll_stack', 'candle_cluster',
                                                'enchanted_book_pile']),
    'transfiguration': ((36, 32, 185, 47, 32, 189), 32, ['writing_desk', 'scroll_stack', 'candle_cluster']),
    'defence': ((34, 26, 192, 42, 26, 201), 26, ['writing_desk', 'scroll_stack', 'candle_cluster',
                                                 'enchanted_book_pile']),
}


def _points(value, out):
    """Every [x, y, z] number triple anywhere inside an anchors value."""
    if isinstance(value, dict):
        for v in value.values():
            _points(v, out)
    elif isinstance(value, (list, tuple)):
        if len(value) == 3 and all(isinstance(n, (int, float)) for n in value):
            out.append(tuple(int(n // 1) for n in value))
        else:
            for v in value:
                _points(v, out)
    return out


class _Placer:
    def __init__(self, grid):
        self.grid = grid
        self.cells = grid.cells
        self.keep_clear = set(_points(grid.anchors, []))
        self.doors = {p for p, s in grid.cells.items() if checks.block_id(s).endswith('_door')}
        self.used = []
        self.stats = {}

    def air(self, x, y, z):
        return self.grid.is_air(x, y, z)

    def solid_wall(self, x, y, z):
        s = self.grid.get(x, y, z)
        name = checks.block_id(s)
        return (not checks.passable(s) and checks.supports(s) and not name.endswith(('_slab', '_stairs'))
                and not checks.too_tall(s) and not name.startswith(M) and 'glass' not in name)

    def near(self, x, y, z, points, r):
        return any(abs(x - px) <= r and abs(z - pz) <= r and abs(y - py) <= 2 for px, py, pz in points)

    def free_floor(self, x, y, z):
        return (self.air(x, y, z) and self.air(x, y + 1, z) and checks.supports(self.grid.get(x, y - 1, z))
                and not self.near(x, y, z, self.keep_clear, 1) and not self.near(x, y, z, self.doors, 2)
                and all(max(abs(x - ux), abs(z - uz)) >= 2 or uy != y for ux, uy, uz in self.used))

    def spots(self, box, y, height=1):
        """(x, z, facing-out) for each free floor cell in the box with a solid wall beside it, `height` blocks up."""
        x0, _, z0, x1, _, z1 = box
        out = []
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not self.free_floor(x, y, z):
                    continue
                for dx, dz, facing in DIRS:
                    if all(self.solid_wall(x + dx, y + k, z + dz) for k in range(height)):
                        out.append((x, z, facing))
                        break
        return out

    def spread(self, spots):
        """Farthest from everything placed in this room first, so a kit spreads round the room."""
        if not self.used:
            return spots
        return sorted(spots, key=lambda s: -min(max(abs(s[0] - u[0]), abs(s[1] - u[2])) for u in self.used))

    def put(self, x, y, z, piece, **props):
        state = M + piece + ('[' + ','.join('%s=%s' % kv for kv in sorted(props.items())) + ']' if props else '')
        self.grid.set(x, y, z, state)
        self.used.append((x, y, z))
        self.stats[piece] = self.stats.get(piece, 0) + 1


def _hourglasses(p, box, y):
    """Four in a row, one cell apart, against the longest stretch of free wall."""
    best = None
    spots = p.spots(box, y, height=2)
    free = set(spots)
    for (x, z, facing) in spots:
        dx, dz = (1, 0) if facing in ('north', 'south') else (0, 1)
        row = [(x + dx * 2 * i, z + dz * 2 * i) for i in range(4)]
        if all((rx, rz, facing) in free for rx, rz in row):
            mid = ((box[0] + box[3]) / 2, (box[2] + box[5]) / 2)
            score = -abs((row[0][0] + row[3][0]) / 2 - mid[0]) - abs((row[0][1] + row[3][1]) / 2 - mid[1])
            if best is None or score > best[0]:
                best = (score, row, facing)
    if best:
        for house, (x, z) in zip(HOUSES, best[1]):
            p.put(x, y, z, house + '_points_hourglass', facing=best[2])
    return bool(best)


def _trunks(p, box, y):
    """A school trunk at the foot of every bed, front facing away from the bed."""
    x0, _, z0, x1, _, z1 = box
    placed = 0
    for (bx, by, bz), s in sorted(p.cells.items()):
        if not (x0 <= bx <= x1 and z0 <= bz <= z1 and by == y) or not checks.block_id(s).endswith('_bed'):
            continue
        if 'part=foot' not in s:
            continue
        facing = s.split('facing=')[1].split(',')[0].rstrip(']')
        step = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}[facing]
        # Four-poster beds have a post at the foot, so the trunk stands just beyond the post if there is one.
        for k in (1, 2):
            tx, tz = bx - step[0] * k, bz - step[1] * k
            if p.air(tx, y, tz) and checks.supports(p.grid.get(tx, y - 1, tz)) \
                    and not p.near(tx, y, tz, p.keep_clear, 0):
                p.put(tx, y, tz, 'school_trunk', facing=TOWARD[facing])
                placed += 1
                break
            if not checks.too_tall(p.grid.get(tx, y, tz)):
                break
    return placed


BANNER_HOUSE = {'red': 'gryffindor', 'yellow': 'hufflepuff', 'blue': 'ravenclaw', 'green': 'slytherin'}
FACING_STEP = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}


def _upgrade_banners(p):
    """Every vanilla house-colour wall banner to a hanging house banner, its upper half (the rod) where the vanilla
    banner hung and its lower half in the cell below, when that cell is air with a solid wall behind it and no
    anchor in it. Returns the number upgraded."""
    done = 0
    for (x, y, z), s in sorted(p.cells.items()):
        name = checks.block_id(s)
        colour = name[:-len('_wall_banner')] if name.endswith('_wall_banner') else None
        if colour not in BANNER_HOUSE:
            continue
        facing = s.split('facing=')[1].split(',')[0].rstrip(']')
        dx, dz = FACING_STEP[facing]
        if not (p.air(x, y - 1, z) and p.solid_wall(x - dx, y - 1, z - dz)) or (x, y - 1, z) in p.keep_clear:
            continue
        house = BANNER_HOUSE[colour]
        p.grid.set(x, y - 1, z, M + '%s_hanging_banner[facing=%s,half=lower]' % (house, facing))
        p.grid.set(x, y, z, M + '%s_hanging_banner[facing=%s,half=upper]' % (house, facing))
        done += 1
    p.stats['banners upgraded'] = done
    return done


def place_decor(grid):
    p = _Placer(grid)
    report = {'vanilla banners upgraded': _upgrade_banners(p)}
    for room, (box, ys, kit) in ROOM_KITS.items():
        p.used = []
        done = 0
        for i, entry in enumerate(kit):
            ys = ys if isinstance(ys, tuple) else (ys,)
            y = ys[i % len(ys)]
            kind, _, piece = entry.rpartition(':')
            if entry == 'hourglasses':
                done += _hourglasses(p, box, y)
                continue
            if entry == 'trunks':
                done += _trunks(p, box, y)
                continue
            if kind == 'float':
                x0, _, z0, x1, _, z1 = box
                cand = [(x, z, None) for x in range(x0 + 1, x1, 3) for z in range(z0 + 1, z1, 3)
                        if all(p.air(x, y + k, z) for k in range(0, 7))
                        and any(not p.air(x, y + k, z) for k in range(7, 13))
                        and not p.near(x, y, z, p.keep_clear, 1)]
                for (x, z, _) in p.spread(cand):
                    if all(max(abs(x - u[0]), abs(z - u[2])) >= 3 for u in p.used if u[1] == y + 5):
                        p.put(x, y + 5, z, piece)
                        done += 1
                        break
                continue
            if kind == 'banner':
                for (x, z, facing) in p.spread(p.spots(box, y, height=3)):
                    if p.air(x, y + 1, z) and p.air(x, y + 2, z):
                        p.grid.set(x, y + 1, z, M + piece + '_hanging_banner[facing=%s,half=lower]' % facing)
                        p.grid.set(x, y + 2, z, M + piece + '_hanging_banner[facing=%s,half=upper]' % facing)
                        p.used.append((x, y, z))
                        p.stats['hanging_banner'] = p.stats.get('hanging_banner', 0) + 1
                        done += 1
                        break
                continue
            if kind == 'wall':
                for (x, z, facing) in p.spread(p.spots(box, y, height=2)):
                    if p.air(x, y + 1, z):
                        p.grid.set(x, y + 1, z, M + piece + '[facing=%s]' % facing)
                        p.used.append((x, y, z))
                        p.stats[piece] = p.stats.get(piece, 0) + 1
                        done += 1
                        break
                continue
            for (x, z, facing) in p.spread(p.spots(box, y)):
                if piece in NO_FACING:
                    p.put(x, y, z, piece)
                else:
                    p.put(x, y, z, piece, facing=TOWARD[facing] if kind == 'look' else facing)
                done += 1
                break
        report[room] = done                      # pieces placed (the trunks count one per bed)
    place_decor.stats = dict(report, pieces=p.stats)
    return report
