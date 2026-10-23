"""Leaf backing: leaves render see-through (the texture is full of holes), so a leaf that forms a SURFACE shows
whatever is behind it: sky, void, the hollow inside a hill. Every set generator that places leaves runs this check.

The rule. A leaf L has a viewing side d on each of the 6 axes where L+d is open (air, a plant, anything that is not
a leaf and not an opaque cube). A camera on that side looks through L along -d. Walk -d from L through the run of
consecutive leaves (L counts as the first):
  * the run ends on an opaque cube (moss, dirt, stone, a log ...): backed;
  * the run ends on anything else (air, a plant, the set's edge): backed only if the run is THICK (3+) leaves deep,
    which is a crown or a bush dense enough to read as a mass. Shallower than that, the sky shows through.
So a leaf layer over terrain must sit on solid ground (one-block skin over moss or dirt), a hedge needs a solid core
or enough depth, and a crown needs to be thick or have a log or leaf core. The set's edge is air: a leaf on the edge
has nothing behind it.

`unbacked(g)` returns the offending (pos, d) pairs; `assert_bites(g)` removes one backing block from a thin-skin leaf
and asserts the check then fails (the mutation bite).
"""
THICK = 3
SPINE_BELOW = 2                                  # a conifer's log spine ends this far under its tip
DIRS = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))
_SEE_THROUGH = set(('glass', 'pane', 'slab', 'stairs', 'fence', 'wall', 'vine', 'grass', 'fern', 'flower', 'torch',
                'lantern', 'door', 'carpet', 'button', 'ladder', 'chain', 'bars', 'allium', 'dandelion', 'daisy',
                'sapling', 'rail', 'cobweb', 'water', 'lava', 'light', 'banner', 'sign', 'skull', 'head', 'candle',
                'pot', 'fire', 'snow', 'bush', 'rose', 'tulip', 'orchid', 'cornflower', 'lily', 'bamboo', 'roots',
                'azalea', 'moss', 'lever', 'bed', 'chest', 'scaffolding', 'trapdoor', 'pressure',
                'cluster', 'bud', 'litter', 'berries', 'tripwire', 'wire', 'pearlescent'))


def bid(s):
    return None if s is None else s.split('[', 1)[0]


def is_leaf(s):
    return s is not None and bid(s).endswith('_leaves')


def is_opaque(s):
    """A full cube nothing shows through. Leaves, azalea bushes, plants, glass, slabs, stairs and the like are not."""
    if s is None or is_leaf(s):
        return False
    b = bid(s).split(':')[-1]
    if b == 'air':
        return False
    if b.endswith(('_block', '_log', '_wood', '_planks', '_bricks', '_terracotta', '_wool', '_concrete')):
        return True                              # (grass_block, moss_block, stone_bricks ... are full cubes)
    return not (set(b.split('_')) & _SEE_THROUGH)


def unbacked(g, extra=None, thick=THICK, seen=None):
    """Leaves with a see-through view. g: pos -> state; extra: more solids (a castle) consulted but not scanned;
    seen(pos, d): whether a camera can be on side d of pos (default: every side; a set whose far faces are left
    open on purpose passes its own camera-region test, as the lawn does with face_seen)."""
    def at(p):
        s = g.get(p)
        return s if s is not None else (extra.get(p) if extra else None)

    bad = []
    for p, s in g.items():
        if not is_leaf(s):
            continue
        for d in DIRS:
            if is_leaf(at((p[0] + d[0], p[1] + d[1], p[2] + d[2]))) or is_opaque(at((p[0] + d[0], p[1] + d[1], p[2] + d[2]))):
                continue                         # not a viewing side
            if seen is not None and not seen(p, d):
                continue                         # no camera on that side
            run, q = 1, p
            while True:
                q = (q[0] - d[0], q[1] - d[1], q[2] - d[2])
                t = at(q)
                if is_leaf(t):
                    run += 1
                    if run >= thick:
                        break                    # thick enough to read as a mass
                    continue
                if t is not None and is_opaque(t):
                    run = thick
                break
            if run < thick:
                bad.append((p, d))
                break
    return bad


def problems(g, name='', extra=None, limit=6, seen=None):
    bad = unbacked(g, extra, seen=seen)
    if not bad:
        return []
    return ['%s: %d leaves are see-through (air or the set edge behind a thin leaf layer), e.g. %s' % (
        name, len(bad), bad[:limit])]


def assert_bites(g, extra=None, seen=None):
    """Mutation: take a thin-skin leaf whose backing is an opaque block, remove the backing, require a failure."""
    base = len(unbacked(g, extra, seen=seen))
    for p, s in sorted(g.items()):
        if not is_leaf(s):
            continue
        for d in DIRS:
            q = (p[0] - d[0], p[1] - d[1], p[2] - d[2])
            t = g.get(q)
            f = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
            if t is not None and is_opaque(t) and not is_leaf(g.get(f)) and not is_opaque(g.get(f)):
                m = dict(g)
                del m[q]
                n = len(unbacked(m, extra, seen=seen))
                if n > base:
                    return (p, q)
    raise AssertionError('leaf backing check found no backed leaf to mutate (or the check does not bite)')


def back_leaves(w, cells, bounds, seen=None, fill='minecraft:moss_block', extra=None, rounds=6):
    """Backing pass. For each leaf in `cells` (a world position list) with a see-through view, put `fill` in the
    first empty cell behind it (one block in, past any leaves that already stand there). If that cell is outside
    `bounds` ((x0,y0,z0), (x1,y1,z1) inclusive) the leaf itself becomes `fill`. Returns the number of blocks changed.
    w: an object with .g (pos -> state) and .set(pos, state)."""
    lo, hi = bounds
    changed = 0
    cells = [c for c in cells if is_leaf(w.g.get(c))]
    for _ in range(rounds):
        todo = [(p, d) for p, d in unbacked(w.g, extra, seen=seen) if p in set(cells)]
        if not todo:
            break
        for p, d in todo:
            if not is_leaf(w.g.get(p)):
                continue
            q = p
            while is_leaf(w.g.get(q)):
                q = (q[0] - d[0], q[1] - d[1], q[2] - d[2])
            inside = all(lo[i] <= q[i] <= hi[i] for i in range(3))
            if (w.g.get(q) is None or not is_opaque(w.g.get(q))) and (extra is None or q not in extra) and inside:
                w.set(q, fill)
            elif not inside:
                w.set(p, fill)
            else:
                continue
            changed += 1
    return changed


def trim_crown(w, cells, extra=None, seen=None, rounds=8):
    """Free-standing crowns: shave off the thin see-through leaves (noisy outline bits one or two deep) until what is
    left is a mass. Cells outside `cells` are never touched. Returns the number of leaves removed."""
    cs, removed = set(cells), 0
    for _ in range(rounds):
        bad = [p for p, d in unbacked(w.g, extra, seen=seen) if p in cs]
        if not bad:
            break
        for p in set(bad):
            w.set(p, None)
            removed += 1
    return removed


def spruce_cone(x, z, y_first, y_top):
    """Leaf cells of a backed conifer: rows y_first..y_top (inclusive) round a log spine at (x, z) that the caller
    runs up to y_top - SPINE_BELOW only: the top two rows also take a leaf over the spine, so the tip is leaves
    over leaves and no bare log pokes out of the crown. The radius steps down every 3 rows from the top (a short bottom group joins the one
    above), and a row is an octagon (|dx| + |dz| <= r + (r + 1) // 2), so every open face of a leaf has a log or three
    leaves behind it."""
    rows = list(range(y_first, y_top + 1))
    out = []
    for k, y in enumerate(reversed(rows)):             # k = 0 at the top
        g = k // 3
        if len(rows) - g * 3 < 3 and g > 0:
            g -= 1
        r = g + 1
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if (dx or dz or y > y_top - SPINE_BELOW) and abs(dx) + abs(dz) <= r + (r + 1) // 2:
                    out.append((x + dx, y, z + dz))
    return out
