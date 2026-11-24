"""Block-level checks on the sea cave template. Each returns problem strings; none means it passed.

These are the shape's acceptance floor, not its acceptance test: the visual gate (renders written up
in the reference note) is the real one. They exist to catch what a picture can hide: a sealed
cleft, a one-block leak, a two-block step on the island, a broken route, a cavern that is secretly
an ellipse.
"""

import math
from collections import deque

from . import frame as F

SOLID_SKIP = (F.CAVE_AIR, F.WATER, 'minecraft:air')
VOID = 'minecraft:structure_void'


def _kind(blocks, p):
    """'out' (outside the template frame), None (unlisted), 'air', 'water' or 'solid'."""
    x, y, z = p
    if not (0 <= x < F.SIZE[0] and 0 <= y < F.SIZE[1] and 0 <= z < F.SIZE[2]):
        return 'out'
    v = blocks.get(p)
    if v is None:
        return None
    if v[0] in (F.CAVE_AIR, 'minecraft:air'):
        return 'air'
    if v[0] == F.WATER:
        return 'water'
    if v[0] == VOID:
        return None          # a structure void places nothing: the world shows through
    return 'solid'


def _in_box(p, box):
    return all(box[i][0] <= p[i] <= box[i][1] for i in range(3))


N6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
N4 = ((1, 0), (-1, 0), (0, 1), (0, -1))


def _tunnel_cells(blocks):
    """Carved cells within 1.5 of the tunnel polyline: what the cleft must reach."""
    out = set()
    pts = F.TUNNEL
    for (ax, ay, az), (bx, by, bz) in zip(pts, pts[1:]):
        n = int(max(abs(bx - ax), abs(by - ay), abs(bz - az))) * 2 + 1
        for i in range(n + 1):
            t = i / n
            c = (round(ax + (bx - ax) * t), round(ay + (by - ay) * t), round(az + (bz - az) * t))
            if _kind(blocks, c) in ('air', 'water'):
                out.add(c)
    return out


def cleft_open(blocks):
    """A 3-wide, at least 8-tall opening of CARVED cells cut into the rock of the south face (open
    sea does not count), spanning the waterline, with rock on both sides of it and the open sea in
    front of it, that connects through carved space to the flooded tunnel. The 3-wide window may
    shift one block per row, to follow the cleft's lean."""
    (x0, x1), (y0, y1), (z0, z1) = F.CLEFT_ZONE

    def window(x, y, z):
        return all(_kind(blocks, (x + i, y, z)) in ('air', 'water') for i in range(3))

    def run(x, z, step):
        n, y, xs = 0, F.SEA_Y + step, x
        while y0 <= y <= y1:
            nxt = [xx for xx in (xs, xs - 1, xs + 1) if window(xx, y, z)]
            if not nxt:
                break
            xs = nxt[0]
            n += 1
            y += step
        return n

    def open_to_sea(x, z):
        for i in range(3):
            for dz in range(1, 5):
                k = _kind(blocks, (x + i, F.SEA_Y, z + dz))
                if k is None:
                    break
                if k not in ('air', 'water'):
                    return False
            else:
                return False
        return True

    best = None
    for z in range(z1, z0 - 1, -1):
        for x in range(x0, x1 - 1):
            if not window(x, F.SEA_Y, z):
                continue
            if _kind(blocks, (x - 1, F.SEA_Y, z)) != 'solid' or _kind(blocks, (x + 3, F.SEA_Y, z)) != 'solid':
                continue
            if not open_to_sea(x, z):
                continue
            tall = 1 + run(x, z, 1) + run(x, z, -1)
            if tall >= 8:
                best = (x, z, [(x + i, F.SEA_Y, z) for i in range(3)])
                break
        if best:
            break
    if not best:
        return ['cleft_open: no 3-wide, 8-tall carved opening in the rock across the waterline on '
                'the south face']
    targets = _tunnel_cells(blocks)
    if not targets:
        return ['cleft_open: the flooded tunnel is not carved']
    seen = set(best[2])
    q = deque(seen)
    while q:
        p = q.popleft()
        if p in targets:
            return []
        for d in N6:
            n = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
            if n not in seen and _kind(blocks, n) in ('air', 'water'):
                seen.add(n)
                q.append(n)
    return ['cleft_open: the opening at x%d..%d z%d does not reach the tunnel' % (best[0], best[0] + 2, best[1])]


def footprint_rim(blocks, margin=2, band=6.0, low=10):
    """Nothing listed within `margin` of the frame edge, and nothing solid above y `low` within
    `band` of the footprint's rim (the edge of the set of columns the template lists at all), so the
    template sinks into the natural sea floor instead of ending in a square cliff."""
    size = [max(p[i] for p in blocks) + 1 for i in range(3)]       # the template as written
    cols = {}
    for (x, y, z), v in blocks.items():
        if v[0] == VOID:
            continue
        top = cols.get((x, z), -1)
        if v[0] not in SOLID_SKIP and y > top:
            cols[(x, z)] = y
        elif (x, z) not in cols:
            cols[(x, z)] = -1
    out = []
    near = [q for q in cols if min(q[0], q[1], size[0] - 1 - q[0], size[2] - 1 - q[1]) < margin]
    if near:
        out.append('footprint_rim: %d listed columns within %d of the frame edge, e.g. %s'
                   % (len(near), margin, near[0]))
    rim = [(x, z) for x in range(-1, size[0] + 1) for z in range(-1, size[2] + 1)
           if (x, z) not in cols and any((x + dx, z + dz) in cols for dx, dz in N4)]
    high = []
    for (x, z), top in cols.items():
        if top <= low:
            continue
        if any((x - a) ** 2 + (z - b) ** 2 <= band * band for a, b in rim):
            high.append(((x, z), top))
    if high:
        out.append('footprint_rim: %d columns stand above y%d within %g of the rim, e.g. %s top %d'
                   % (len(high), low, band, high[0][0], high[0][1]))
    return out


def watertight(blocks):
    """No carved cell touches a cell outside the template or one the template leaves unlisted,
    except in the cleft mouth, which is meant to be open to the sea."""
    bad = []
    for p, v in blocks.items():
        if v[0] not in (F.CAVE_AIR, F.WATER, 'minecraft:air'):
            continue
        if _in_box(p, F.CLEFT_ZONE):
            continue
        for d in N6:
            n = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
            if _kind(blocks, n) in ('out', None):
                bad.append((p, n))
                break
    if bad:
        return ['watertight: %d carved cells touch unlisted or outside cells, e.g. %s next to %s'
                % (len(bad), bad[0][0], bad[0][1])]
    return []


def _floor_tops(blocks, x, z):
    """Highest solid y with carved space directly above it, in the lake's height range."""
    for y in range(F.LAKE_Y + 8, 0, -1):
        if _kind(blocks, (x, y, z)) == 'solid' and _kind(blocks, (x, y + 1, z)) in ('air', 'water'):
            return y
    return None


def _island_dry(blocks):
    """Dry columns (solid top above the lake) joined to the island crest, 4-connected."""
    start = (F.ISLAND_TOP[0], F.ISLAND_TOP[2])

    def dry(q):
        t = _floor_tops(blocks, q[0], q[1])
        return t is not None and t > F.LAKE_Y

    if not dry(start):
        return set()
    seen, q = {start}, [start]
    while q and len(seen) < 2000:
        a = q.pop()
        for dx, dz in N4:
            n = (a[0] + dx, a[1] + dz)
            if n not in seen and dry(n):
                seen.add(n)
                q.append(n)
    return seen


def column_drop(blocks, limit=6):
    """Under the sea the rock surface never drops more than `limit` between neighbouring columns
    (outside the cleft mouth): no plumb underwater walls. A column's surface is its highest solid
    at or below the sea's top water block; columns the template leaves to the world are skipped."""
    tops = {}
    cols = {(x, z) for (x, y, z), v in blocks.items() if v[0] != VOID}
    (cx0, cx1), _, (cz0, cz1) = F.CLEFT_ZONE
    for (x, z) in cols:
        if cx0 <= x <= cx1 and cz0 <= z <= cz1:
            continue
        for y in range(F.SEA_Y, -1, -1):
            if _kind(blocks, (x, y, z)) == 'solid':
                tops[(x, z)] = y
                break
    bad = []
    for (x, z), t in tops.items():
        for dx, dz in ((1, 0), (0, 1)):
            u = tops.get((x + dx, z + dz))
            if u is not None and abs(u - t) > limit:
                bad.append((abs(u - t), (x, z), t, (x + dx, z + dz), u))
    if bad:
        bad.sort(reverse=True)
        return ['column_drop: %d underwater steps over %d blocks, worst %d: %s top %d next to %s top %d'
                % ((len(bad), limit) + bad[0])]
    return []


def connected(blocks):
    """Every piece of rock is joined (6-connected) to rock standing on the template floor: nothing
    floats, nothing hangs cut off by the notch."""
    solid = {p for p, v in blocks.items() if v[0] not in SOLID_SKIP and v[0] != VOID}
    seen = set()
    loose = []
    for s0 in solid:
        if s0 in seen:
            continue
        comp, q, grounded = [s0], [s0], s0[1] == 0
        seen.add(s0)
        while q:
            x, y, z = q.pop()
            for d in N6:
                n = (x + d[0], y + d[1], z + d[2])
                if n in solid and n not in seen:
                    seen.add(n)
                    comp.append(n)
                    q.append(n)
                    if n[1] == 0:
                        grounded = True
        if not grounded:
            loose.append(comp)
    if loose:
        loose.sort(key=len, reverse=True)
        desc = ', '.join('%d at %s' % (len(c), min(c, key=lambda p: p[1])) for c in loose[:4])
        return ['connected: %d loose pieces of rock not joined to the ground: %s' % (len(loose), desc)]
    return []


def island_steps(blocks):
    """Every island or lake-bed column within the island's radius + 4 differs from each such
    neighbour by at most 1, so the island slopes into the lake on every side with no step over 1
    block. Island columns are the dry ground joined to the crest; lake-bed columns have water
    directly above their top. Dry shore under the cavern walls is neither."""
    cx, cz = F.ISLAND_CENTRE
    reach = F.ISLAND_RADIUS + 4
    island = _island_dry(blocks)
    tops = {}
    for x in range(int(cx - reach) - 1, int(cx + reach) + 2):
        for z in range(int(cz - reach) - 1, int(cz + reach) + 2):
            if math.hypot(x - cx, z - cz) <= reach:
                t = _floor_tops(blocks, x, z)
                if t is None:
                    continue
                if (x, z) in island or _kind(blocks, (x, t + 1, z)) == 'water':
                    tops[(x, z)] = t
    if not island or max(tops.values()) < F.LAKE_Y + 3:
        return ['island_steps: no island rises 3 above the lake within %d of %s' % (reach, (cx, cz))]
    bad = []
    for (x, z), t in tops.items():
        for dx, dz in ((1, 0), (0, 1)):
            u = tops.get((x + dx, z + dz))
            if u is not None and abs(u - t) > 1:
                bad.append(((x, z), t, (x + dx, z + dz), u))
    if bad:
        return ['island_steps: %d steps over 1 block, e.g. %s top %d next to %s top %d'
                % ((len(bad),) + bad[0])]
    return []


def island_isolated(blocks):
    """The island's dry ground (solid top above the lake) is its own: a flood over dry columns from
    its crest stays within its radius + 1, and it spans 10 to 12 blocks."""
    cx, cz = F.ISLAND_CENTRE
    seen = _island_dry(blocks)
    if not seen:
        return ['island_isolated: the island crest is not dry ground']
    far = [a for a in seen if math.hypot(a[0] - cx, a[1] - cz) > F.ISLAND_RADIUS + 1]
    if far:
        return ['island_isolated: dry ground runs from the island to %s, beyond its radius + 1 '
                '(%d dry columns joined)' % (max(far, key=lambda a: math.hypot(a[0] - cx, a[1] - cz)), len(seen))]
    span = max(max(p[0] for p in seen) - min(p[0] for p in seen) + 1,
               max(p[1] for p in seen) - min(p[1] for p in seen) + 1)
    if not 10 <= span <= 12:
        return ['island_isolated: the island is %d across, not 10 to 12' % span]
    return []


def route_walkable(blocks):
    """Antechamber floor to the island shore: walk with steps of at most 1 and two blocks of
    headroom, or swim in the lake. The sea pool and tunnel do not count as swimmable."""
    def air(p):
        return _kind(blocks, p) == 'air'

    def solid(p):
        return _kind(blocks, p) == 'solid'

    def lake(p):
        return _kind(blocks, p) == 'water' and p[1] <= F.LAKE_Y and _in_box(p, F.CAVERN_BOX)

    def stand(p):
        x, y, z = p
        return air(p) and air((x, y + 1, z)) and solid((x, y - 1, z))

    start = ('w',) + F.ANTE_START
    if not stand(F.ANTE_START):
        return ['route_walkable: the antechamber start %s is not a standing place' % (F.ANTE_START,)]
    cx, cz = F.ISLAND_CENTRE

    def goal(p):
        return (math.hypot(p[0] - cx, p[2] - cz) <= F.ISLAND_RADIUS + 1 and p[1] > F.LAKE_Y)

    seen = {start}
    q = deque([start])
    while q:
        s = q.popleft()
        mode, x, y, z = s
        if mode == 'w' and goal((x, y, z)):
            return []
        nxt = []
        if mode == 'w':
            for dx, dz in N4:
                for dy in (0, 1, -1):
                    n = (x + dx, y + dy, z + dz)
                    if dy == 1 and not air((x, y + 2, z)):
                        continue
                    if stand(n):
                        nxt.append(('w',) + n)
                for dy in (-1, -2, -3):
                    n = (x + dx, y + dy, z + dz)
                    if lake(n) and all(air((x + dx, yy, z + dz)) for yy in range(y + dy + 1, y + 2)):
                        nxt.append(('s',) + n)
                        break
        else:
            for d in N6:
                n = (x + d[0], y + d[1], z + d[2])
                if lake(n):
                    nxt.append(('s',) + n)
            for dx, dz in N4:
                for dy in (0, 1):
                    n = (x + dx, y + dy, z + dz)
                    if stand(n):
                        nxt.append(('w',) + n)
        for n in nxt:
            if n not in seen:
                seen.add(n)
                q.append(n)
    reached = [s for s in seen if s[0] == 'w']
    low = min(reached, key=lambda s: s[2])[1:] if reached else F.ANTE_START
    return ['route_walkable: no way from the antechamber %s to the island shore (lowest standing '
            'place reached %s, %d places)' % (F.ANTE_START, low, len(reached))]


def _cavern_section(blocks, y):
    (x0, x1), _, (z0, z1) = F.CAVERN_BOX
    cells = {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)
             if _kind(blocks, (x, y, z)) in ('air', 'water')}
    if not cells:
        return set()
    # keep the largest connected region, then fill its holes (island, pillars) so only the outer
    # wall is judged
    comps, left = [], set(cells)
    while left:
        s = left.pop()
        comp, q = {s}, [s]
        while q:
            a, b = q.pop()
            for dx, dz in N4:
                n = (a + dx, b + dz)
                if n in left:
                    left.discard(n)
                    comp.add(n)
                    q.append(n)
        comps.append(comp)
    region = max(comps, key=len)
    outside, q = set(), [(x0 - 1, z0 - 1)]
    outside.add(q[0])
    while q:
        a, b = q.pop()
        for dx, dz in N4:
            n = (a + dx, b + dz)
            if x0 - 1 <= n[0] <= x1 + 1 and z0 - 1 <= n[1] <= z1 + 1 and n not in outside \
                    and n not in region:
                outside.add(n)
                q.append(n)
    return {(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1) if (x, z) not in outside}


def ellipse_fit_share(region):
    """Share of the region's outer boundary cells lying within 1 block of the ellipse with the
    region's own area and second moments (the best-fitting ellipse for a filled shape)."""
    n = len(region)
    mx = sum(p[0] for p in region) / n
    mz = sum(p[1] for p in region) / n
    sxx = sum((p[0] - mx) ** 2 for p in region) / n + 1 / 12.0
    szz = sum((p[1] - mz) ** 2 for p in region) / n + 1 / 12.0
    sxz = sum((p[0] - mx) * (p[1] - mz) for p in region) / n
    tr, det = sxx + szz, sxx * szz - sxz * sxz
    disc = math.sqrt(max(0.0, tr * tr / 4 - det))
    l1, l2 = tr / 2 + disc, tr / 2 - disc
    ang = 0.5 * math.atan2(2 * sxz, sxx - szz)
    a, b = 2 * math.sqrt(l1), 2 * math.sqrt(max(l2, 1e-6))     # uniform ellipse: var = a^2 / 4
    ca, sa = math.cos(ang), math.sin(ang)
    boundary = [p for p in region if any((p[0] + dx, p[1] + dz) not in region for dx, dz in N4)]
    near = 0
    for x, z in boundary:
        u, v = (x - mx) * ca + (z - mz) * sa, -(x - mx) * sa + (z - mz) * ca
        rho = math.hypot(u / a, v / b)
        r = math.hypot(u, v)
        if rho == 0 or abs(rho - 1) * r / rho <= 1.0:
            near += 1
    return near / float(len(boundary))


NO_PRIMITIVE_YS = (12, 16, 20, 24, 28)


def no_primitive(blocks):
    """The cavern's horizontal sections at five heights must not be ellipses: no section may lie
    within 1 block of its best-fit ellipse on more than 60% of its outer boundary."""
    out = []
    for y in NO_PRIMITIVE_YS:
        region = _cavern_section(blocks, y)
        if len(region) < 30:
            out.append('no_primitive: the cavern section at y%d is missing or tiny (%d cells)' % (y, len(region)))
            continue
        share = ellipse_fit_share(region)
        if share > 0.60:
            out.append('no_primitive: the cavern at y%d is an ellipse on %d%% of its boundary'
                       % (y, round(share * 100)))
    return out


def _sphere(r):
    rr = int(math.ceil(r))
    return [(dx, dy, dz) for dx in range(-rr, rr + 1) for dy in range(-rr, rr + 1)
            for dz in range(-rr, rr + 1) if 0 < dx * dx + dy * dy + dz * dz <= r * r]


def rock_cover(blocks, descent=None, cavern=None):
    """Extra to the brief's five: every carved cell outside the cleft mouth keeps at least 4 blocks
    of template rock between it and the open sea (no unlisted cell within 4.5), and, when the shape
    hands over its descent and cavern cells, the descent keeps 4 blocks from the cavern too."""
    ball = _sphere(4.5)
    bad = []
    for p, v in blocks.items():
        if v[0] not in (F.CAVE_AIR, F.WATER, 'minecraft:air'):
            continue
        if _in_box(p, ((30, 46), (30, 62), (48, 66))):     # the cleft and the tunnel's first leg
            continue
        if all(_kind(blocks, (p[0] + d[0], p[1] + d[1], p[2] + d[2])) in ('air', 'water') for d in N6):
            continue        # interior cell: a boundary cell is always nearer the outside
        for d in ball:
            n = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
            if _kind(blocks, n) in ('out', None):
                bad.append((p, n))
                break
    out = []
    if bad:
        out.append('rock_cover: %d carved cells within 4 of the open sea or frame, e.g. %s near %s'
                   % (len(bad), bad[0][0], bad[0][1]))
    if descent and cavern:
        close = []
        for p in descent:
            for d in ball:
                n = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
                if n in cavern:
                    close.append((p, n))
                    break
        if close:
            out.append('rock_cover: %d descent cells within 4 of the cavern, e.g. %s near %s'
                       % (len(close), close[0][0], close[0][1]))
    return out


def problems(blocks, extra=None):
    if extra is None:
        from . import shape
        extra = shape.feature_cells()
    out = []
    out += cleft_open(blocks)
    out += watertight(blocks)
    out += footprint_rim(blocks)
    out += column_drop(blocks)
    out += connected(blocks)
    out += island_steps(blocks)
    out += island_isolated(blocks)
    out += route_walkable(blocks)
    out += no_primitive(blocks)
    out += rock_cover(blocks, extra.get('descent'), extra.get('cavern'))
    return out
