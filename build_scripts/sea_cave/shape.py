"""Pass 1: the sea cave's shape in one stone.

The rock is a set of hand-placed masses (body, spire, two ledges, the west shoulder, the east
wave-cut bench, the satellite needle and the north-west stump), each a hand-drawn plan polygon that
leans, tapers and steps in and out with the beds (hard beds jut, soft beds recess), plus a broken
apron and boulders at the foot, a seabed flare out to the frame edge and a cap over the cavern. The
inside is carved from a list of named features: cleft, flooded tunnel, antechamber pool, arch
passage, the descent, the mouth and ledge, and the cavern with its lake, island, slab, ledges and
shore ramp. All coordinates come from frame.py.

    solid(x, y, z) -> bool          rock (frame.STONE, painted by strata.py)
    carved(x, y, z) -> 'air' | 'water' | None
"""

import bisect
import math

from . import frame as F

W, H, D = F.SIZE


# --- noise -------------------------------------------------------------------------------------

def _hash(ix, iz, seed):
    n = (ix * 374761393 + iz * 668265263 + seed * 1442695041 + 0x9E3779B9) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    n ^= n >> 16
    return n / 4294967296.0


def vn(x, z, scale, seed):
    """Smooth value noise in 0..1."""
    gx, gz = x / scale, z / scale
    i, j = math.floor(gx), math.floor(gz)
    fx, fz = gx - i, gz - j
    fx, fz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
    a, b = _hash(i, j, seed), _hash(i + 1, j, seed)
    c, d = _hash(i, j + 1, seed), _hash(i + 1, j + 1, seed)
    return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz


def sn(x, z, scale, seed):
    """Signed smooth noise in -1..1."""
    return 2.0 * vn(x, z, scale, seed) - 1.0


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


# --- plan polygons ----------------------------------------------------------------------------

def _seg_dist(px, pz, ax, az, bx, bz):
    dx, dz = bx - ax, bz - az
    L = dx * dx + dz * dz
    t = 0.0 if L == 0 else max(0.0, min(1.0, ((px - ax) * dx + (pz - az) * dz) / L))
    return math.hypot(px - ax - t * dx, pz - az - t * dz), t


def poly_sd(px, pz, poly):
    """Signed distance to a closed polygon: positive inside."""
    inside = False
    best = 1e9
    n = len(poly)
    for i in range(n):
        ax, az = poly[i]
        bx, bz = poly[(i + 1) % n]
        if (az > pz) != (bz > pz):
            if px < ax + (pz - az) * (bx - ax) / (bz - az):
                inside = not inside
        d, _ = _seg_dist(px, pz, ax, az, bx, bz)
        best = min(best, d)
    return best if inside else -best


class Field:
    """A polygon's signed distance sampled on the block grid, read back bilinearly."""
    PAD = 12

    def __init__(self, poly):
        self.poly = poly
        P = self.PAD
        self.g = [[poly_sd(x, z, poly) for z in range(-P, D + P)] for x in range(-P, W + P)]

    def __call__(self, x, z):
        P = self.PAD
        gx, gz = x + P, z + P
        i, j = int(math.floor(gx)), int(math.floor(gz))
        if i < 0 or j < 0 or i + 1 >= len(self.g) or j + 1 >= len(self.g[0]):
            return poly_sd(x, z, self.poly)
        fx, fz = gx - i, gz - j
        g = self.g
        return ((g[i][j] * (1 - fx) + g[i + 1][j] * fx) * (1 - fz)
                + (g[i][j + 1] * (1 - fx) + g[i + 1][j + 1] * fx) * fz)


def bed_eff(x, y, z):
    return F.bed_eff(x, y, z)


_BED_LO = [lo for lo, hi, r, i in F.BED_TABLE]


def bed(e):
    k = max(0, bisect.bisect_right(_BED_LO, e) - 1)
    lo, hi, r, i = F.BED_TABLE[k]
    return i, r, lo, hi


def snap_down(v):
    """The bed boundary at or below bed height v: tops follow the beds."""
    k = max(0, bisect.bisect_right(_BED_LO, v) - 1)
    return _BED_LO[k]


def snap_near(v):
    """The nearest bed boundary to bed height v."""
    k = max(0, bisect.bisect_right(_BED_LO, v) - 1)
    lo = _BED_LO[k]
    hi = _BED_LO[k + 1] if k + 1 < len(_BED_LO) else lo + 4
    return lo if v - lo <= hi - v else hi


def dip(x, z):
    return F.bed_eff(x, 0, z)


# --- the masses -------------------------------------------------------------------------------
# The stack is a blade along the joint set running north-east to south-west (Ball's Pyramid seen
# from the side, a spike end-on). Plans are drawn in blade coordinates: u along the blade (north-east
# +), v across it (south-east +), both centred on the waterline centroid. Edges keep to the two
# joint directions with jogs of one to four blocks, the way a jointed rock breaks, never a curve.
# Above its reference height a mass shifts by `lean` per block and pulls in by `taper` per block.

AXIS_U = (0.73, -0.68)
AXIS_V = (0.68, 0.73)
CU, CV = F.WATERLINE_CENTROID[0], F.WATERLINE_CENTROID[2]


def uv_to_xz(u, v):
    return (CU + u * AXIS_U[0] + v * AXIS_V[0], CV + u * AXIS_U[1] + v * AXIS_V[1])


def xz_to_uv(x, z):
    dx, dz = x - CU, z - CV
    return (dx * AXIS_U[0] + dz * AXIS_U[1], dx * AXIS_V[0] + dz * AXIS_V[1])


def _plan(uvs):
    return [uv_to_xz(u, v) for u, v in uvs]


# body: narrow at the south-west tip, broad at the north-east end that holds the spire and the
# descent; the cleft's recess on the south-east face at u -18..-15
BODY = _plan([(25.5, -1), (25.5, 1.5), (23.5, 4), (20.5, 7), (17, 10), (13.5, 13.5), (11, 16),
              (10, 13.5), (4, 13), (3, 14), (-3, 13.5), (-4, 11.5), (-9, 11), (-10, 11.5), (-13, 10.5),
              (-13.5, 12.6), (-15.3, 12.4), (-15.6, 9.5), (-18.4, 9.3), (-18.6, 11.2),
              (-20, 10.8), (-23.5, 9.5), (-24.5, 5), (-28.5, 3), (-28.5, -1.5),
              (-26, -3), (-25.5, -7.5), (-21, -8.5), (-20.5, -12), (-13, -11.5), (-11.5, -13.5),
              (-7.5, -13), (-7, -8.5), (-3.5, -8), (-3, -10.5), (3, -11), (5, -13.5), (12, -12.5), (13.5, -9.5), (15, -7.5), (17, -8.5),
              (19.5, -10), (20, -6), (24, -6.5)])
# the spire stands on the north-east end with its north-west face flush with the body's, so that
# side is one cliff from the sea to the summit; the ledges keep the same face
SPIRE = _plan([(19.5, -10), (20, -6), (19, -1), (17, 1.5), (14, 1), (12.5, 3), (9, 2.5),
               (7, -1), (7.5, -6), (5.5, -9), (6, -12.5), (12, -12.5), (13.5, -9.5), (15, -7.5),
               (17, -8.5)])
LEDGE76 = _plan([(7.5, -12.5), (8, -8), (10, -7.5), (10, -1), (9, 3), (5.5, 4), (5.5, 1),
                 (2.5, 0.5), (2.5, -4), (3.5, -4.5), (3, -11), (5, -13.5)])
LEDGE72 = _plan([(3.5, -11), (4, -6), (2, -5.5), (2.5, 1), (4.5, 1.5), (4, 5), (-1, 5.5), (-1, 2.5),
                 (-3.5, 2), (-3.5, -4), (-1.5, -4.5), (-3, -10.5), (0, -11.5)])
# The shoulder is cut by the cross-joint set, square to the map: faces a few blocks long that
# meet at corners, so it reads as a broken block, not a turret.
SHOULDER = [(27, 40), (33, 40), (33, 42), (37, 42), (37, 47), (35, 47), (35, 51), (29, 51), (29, 49),
            (26, 49), (26, 44), (27, 44)]
# Drawn in x, z: it has to stop 9 blocks short of the frame's east edge so the seabed rim can sink
# below the natural sea floor before the template ends.
BENCH = [(58, 19), (62.5, 19.5), (64.5, 24), (63.5, 29), (65, 33), (63.5, 37), (65, 41),
         (65, 46), (65, 49.5), (63, 53), (60, 55.5), (56, 56), (52, 54), (49, 48.5), (54, 42.5),
         (60, 34), (59, 28)]
# the satellite needle, pulled 4 west of the note's x 72 for the same reason
NEEDLE = [(57, 61), (60, 60), (63, 61), (63.5, 63.5), (61.5, 65.5), (58.5, 65), (57, 63.5)]
STUMP = [(15, 19), (19, 18), (22, 20), (21.5, 24), (18, 26), (14, 24)]

# Top of the body along the blade, before the beds snap it: low at the south-west tip, the saddle
# between the shoulder and the ledges, highest under the spire, dropping off the north-east end.
BODY_PROFILE = [(-30, 50), (-25, 52), (-22, 56), (-19, 60), (-9, 60), (-7, 56), (-3, 57),
                (-1, 60), (9, 62), (18, 65), (21, 60), (25, 55)]


def _interp(table, u):
    if u <= table[0][0]:
        return table[0][1]
    for (a, ha), (b, hb) in zip(table, table[1:]):
        if u <= b:
            return ha + (hb - ha) * (u - a) / (b - a)
    return table[-1][1]


def _body_top(x, z, d):
    u, v = xz_to_uv(x, z)
    h = _interp(BODY_PROFILE, u) + 1.5 * sn(x, z, 7, 11)
    if v > 9:
        # the south-east flank stands lower, by a different amount along its length
        h -= 2.0 + 6.0 * vn(x, z, 6, 12)
    return snap_near(h + dip(x, z))


def _spire_top(x, z, d):
    # Highest at the summit on the north corner; the top falls away to the south-west and the
    # south-east in bed steps, so the crown is a broken corner, never a dome.
    su, sv = xz_to_uv(F.SUMMIT[0], F.SUMMIT[2])
    u, v = xz_to_uv(x, z)
    drop = (0.75 * max(0.0, su - u) + 2.2 * max(0.0, v - sv - 1.0) + 1.2 * max(0.0, u - su - 2.5)
            + 0.5 * max(0.0, sv - v - 3.0) + 0.5 * sn(x, z, 3, 21))
    if drop <= 0.5:
        return F.SUMMIT[1] + 0.99 + dip(x, z)
    return F.SUMMIT[1] + 0.99 - drop + dip(x, z)


def _tilted(y_top, high, fall, slope, rough, seed):
    """A bed-plane top: highest along the edge through `high`, falling at `slope` per block in the
    blade direction `fall` (u, v), with pieces broken out of it in steps. A plane, never a dome."""
    hu, hv = xz_to_uv(*high)
    fu, fv = fall
    n = math.hypot(fu, fv)

    def top(x, z, d):
        u, v = xz_to_uv(x, z)
        along = ((u - hu) * fu + (v - hv) * fv) / n
        broken = rough * math.floor(2.0 * vn(x, z, 3.0, seed))     # 0 or rough: a missing piece
        return dip(x, z) + y_top + 0.99 - slope * max(0.0, along) - broken
    return top


def _needle_top(x, z, d):
    # a tilted top, high on its north-west corner
    return dip(x, z) + F.NEEDLE[1] + 0.99 - 0.6 * max(0.0, (x - 60.5) + (z - 63))


def _stump_top(x, z, d):
    # high on the north-west, awash on the south-east
    return dip(x, z) + F.STUMP[1] + 0.99 - 0.9 * max(0.0, (x - 17) + (z - 21))


BENCH_PROFILE = [(8, 52), (13, 51), (16, 49), (20, 47.5), (24, 45.5), (28, 43), (32, 40)]


def _bench_top(x, z, d):
    # the east flank steps down across the blade toward the sea, bed by bed
    u, v = xz_to_uv(x, z)
    # a wave-cut platform: broken and uneven on top, not a terrace
    h = _interp(BENCH_PROFILE, v) + 1.0 * sn(x, z, 5, 31) + 1.1 * sn(x, z, 2.2, 32)         - 0.6 * max(0.0, 2.5 - d)
    return h + dip(x, z)


MASSES = [
    # name, plan, ref y, lean (x, z) per block, taper, taper side (u, v) or None for all round,
    # recess scale, top(x, z, d), notch, seed, flute and bed-noise amplitudes
    dict(name='body', poly=BODY, ref=42, lean=(0.03, -0.028), taper=0.025, side=None, recess=1.0,
         top=_body_top, notch=True, seed=101, shared=0.7, band=0.45),
    # the spire only steps back on its south-west side, toward the ledges; its other faces run
    # straight up from the body
    dict(name='spire', poly=SPIRE, ref=62, lean=(0.035, -0.035), taper=0.16, side=(-1.0, 0.25),
         recess=0.25, top=_spire_top, notch=False, seed=202, shared=0.5, band=0.2),
    dict(name='ledge76', poly=LEDGE76, ref=62, lean=(0.06, -0.05), taper=0.04, side=None,
         recess=0.8, top=_tilted(76, (47.5, 31.5), (-1.0, 0.6), 0.35, 1.0, 41), notch=False, seed=303,
         shared=0.3, band=0.3),
    dict(name='ledge72', poly=LEDGE72, ref=62, lean=(0.05, -0.04), taper=0.04, side=None,
         recess=0.8, top=_tilted(72, (43.5, 35.5), (-0.6, 1.0), 0.4, 1.0, 42), notch=False, seed=404,
         shared=0.3, band=0.3),
    dict(name='shoulder', poly=SHOULDER, ref=58, lean=(0.03, -0.02), taper=0.0, side=None,
         recess=0.8, top=_tilted(68, (F.SHOULDER[0], F.SHOULDER[2]), (-1.0, -0.5), 0.35, 1.0, 43), notch=False,
         seed=505, shared=0.3, band=0.3),
    dict(name='bench', poly=BENCH, ref=42, lean=(0.0, 0.0), taper=0.0, side=None, recess=0.6,
         top=_bench_top, notch=False, seed=606, shared=0.55, band=0.4),
    dict(name='needle', poly=NEEDLE, ref=40, lean=(0.05, 0.04), taper=0.07, side=None, recess=0.4,
         top=_needle_top, notch=False, seed=707, shared=0.35, band=0.25),
    dict(name='stump', poly=STUMP, ref=40, lean=(0.0, 0.0), taper=0.05, side=None, recess=0.5,
         top=_stump_top, notch=False, seed=808, shared=0.5, band=0.3),
]
for _m in MASSES:
    _m['centre'] = xz_to_uv(sum(p[0] for p in _m['poly']) / len(_m['poly']),
                            sum(p[1] for p in _m['poly']) / len(_m['poly']))

# Vertical joints: gullies cut into the faces across the blade, the way a stack splits.
# In blade coordinates: (u, v0, v1, depth, y-from, y-to); a gully runs across v at one u.
JOINTS_UV = [
    (-22.0, -14, -6, 2.4, 36, 70),     # north-west face, splits the tip from the shoulder
    (-6.0, -15, -9, 2.2, 38, 66),      # north-west face, under the saddle
    (6.5, -15, -9, 2.0, 38, 66),       # north-west face, under the ledges
    (20.0, 8, 16, 2.0, 40, 64),        # south-east face, north-east end
    (-26.0, 1, 10, 1.8, 36, 60),       # south-west tip
    (12.0, 1, 6, 1.6, 64, 88),         # spire, south-east side
    (11.0, -8, -3, 1.6, 64, 84),       # spire, north-west side
    (9.0, 24, 33, 2.6, 36, 52),        # surge channels across the east platform
    (19.0, 18, 28, 2.4, 36, 52),
]
JOINTS = [uv_to_xz(u, a) + uv_to_xz(u, b) + (dep, ya, yb) for u, a, b, dep, ya, yb in JOINTS_UV]


def _joint_inset(x, y, z, joints=None):
    out = 0.0
    for x0, z0, x1, z1, depth, ya, yb in (JOINTS if joints is None else joints):
        if not (ya <= y <= yb):
            continue
        d, t = _seg_dist(x, z, x0, z0, x1, z1)
        w = 1.0 + 0.6 * vn(x + z, y, 3, 77)
        if d < w:
            out = max(out, depth * (1 - d / w))
    return out


def _notch_inset(x, y, z):
    # wave-cut notch at y 41..45, deepest on the exposed west and south-west
    prof = {41: 0.5, 42: 1.0, 43: 1.0, 44: 0.7, 45: 0.35}.get(y, 0.0)
    if not prof:
        return 0.0
    west = smooth((44 - x) / 16.0)
    sw = smooth((z - 44) / 14.0) * smooth((40 - x) / 12.0)
    depth = 0.8 + 2.2 * max(west, sw) + 0.6 * sn(x, z, 3, 91)
    # the face round the cleft is left standing, so the cleft reads as a crack, not a cave mouth
    depth *= 1.0 - 0.9 * math.exp(-((x - 38.5) ** 2) / 10.0 - ((z - 57.5) ** 2) / 12.0)
    return prof * depth


# --- the overhang and the cracks ----------------------------------------------------------------

def _flake(x, y, z):
    """North overhang: on the spire's north face at x 51..58, y 74..80, a slab that juts 3 out over
    -z, with a crack behind it. Placed against wherever the spire's face is at y 73."""
    c = _cache()
    zf = c.spire_face.get(x)
    if zf is None or not (74 <= y <= 80):
        return False
    out = zf - 3 + (1 if (x in (51, 58) or y in (74, 80)) else 0) + (1 if vn(x, y, 2.5, 95) > 0.72 else 0)
    bottom = 74 + (1 if x <= 52 else 0) + (1 if x == 58 else 0)
    return out <= z <= zf + 2 and y >= bottom


def _crack(x, y, z):
    """Open cracks above the sea: behind the flake, and the cleft's hairline up the face."""
    zf = _cache().spire_face.get(x)
    if zf is not None and z == zf + 3 and 52 <= x <= 57 and 73 <= y <= 81:
        return True
    if 52 <= y <= F.CLEFT_HAIR_TOP:
        # one continuous hairline, stepping west at y 55 with both cells open on that row, cut
        # right through to the face so it reads as a crack, not a row of pits
        if (x == 37 and y <= 55 or x == 36 and y >= 55) and 54 <= z <= 62:
            return True
    return False


# --- boulders at the foot ---------------------------------------------------------------------
# (x, y, z, rx, ry, rz): broken blocks off the stack, mostly on the exposed west. Some break the
# surface (white water later), some lie under it.
BOULDERS = [
    (17, 40, 44, 2.6, 2.4, 2.0), (15, 39, 50, 2.0, 2.0, 2.4), (19, 41, 38, 2.2, 2.6, 1.8),
    (20, 39, 57, 2.4, 2.0, 2.0), (25, 41, 63, 2.0, 2.4, 1.6), (14, 38, 34, 1.8, 1.8, 2.2),
    (31, 40, 64, 1.6, 2.2, 1.8), (48, 40, 62, 1.8, 2.0, 1.6), (53, 39, 61, 1.6, 1.6, 2.0),
    (30, 40, 23, 2.0, 2.2, 1.8), (42, 39, 18, 1.8, 2.0, 1.6), (23, 39, 28, 2.2, 2.0, 2.0),
    (61, 38, 63, 1.4, 1.8, 1.4),
]


def _boulder(x, y, z):
    for bx, by, bz, rx, ry, rz in _cache().boulders:
        dx, dy, dz = abs(x - bx) / rx, abs(y - by) / ry, abs(z - bz) / rz
        if dx > 1.6 or dy > 1.6 or dz > 1.6:
            continue
        r = (dx ** 3 + dy ** 3 + dz ** 3) ** (1 / 3.0)
        if r < 1.0 + 0.25 * sn(x + 3 * y, z - 2 * y, 2.0, 61):
            return True
    return False


# --- interior features --------------------------------------------------------------------------

CAVERN = [(15, 31), (17, 28), (21, 26), (26, 27), (31, 25), (37, 26), (43, 27), (48, 29),
          (51, 31), (53, 35), (52.5, 40), (54, 43), (55, 47), (52, 49.5), (52, 54), (51, 59),
          (48, 63), (44, 64), (37, 63), (31, 64), (26, 63), (21, 62), (17, 60), (15, 57),
          (14, 52), (15, 46), (13, 40), (14, 35)]
LAKE = [(17, 31), (20, 30), (24, 31), (30, 30), (36, 30), (42, 31), (47, 33), (50, 37),
        (51, 42), (49, 47), (47, 51), (45, 55), (42, 57), (37, 58), (31, 57), (26, 59),
        (21, 57), (18, 54), (16, 50), (15, 45), (15, 39), (16, 34)]
ANTE = [(43, 38), (45, 36), (48, 35), (50, 37), (52, 39), (51, 42), (49, 44), (46, 44), (43, 42)]
ARCH_PASSAGE = [(46.5, 38.5), (48.2, 37.2), (49.6, 35.4), (51.0, 33.6), (52.0, 33.2)]

# Island slabs: (cx, cz, half_u, half_v, angle_deg, height at centre, gx, gz). Tilted planes,
# like the film island's broken plates; the north side is left without one (the bite).
ISLAND_SLABS = [
    # (cx, cz, half_u, half_v, angle_deg, gx, gz): each slab tilts the mound under it, so the top
    # breaks into plates with raised edges rather than a smooth cone
    (24.0, 41.5, 3.0, 2.2, 28, 0.30, -0.25),
    (28.0, 44.0, 2.8, 2.0, -18, -0.35, 0.30),
    (22.0, 45.0, 2.6, 2.0, 62, 0.35, 0.30),
    (28.5, 40.0, 2.2, 1.6, 12, -0.40, -0.35),
    (20.5, 40.5, 2.2, 1.8, -40, 0.40, 0.10),
]


def island_height(x, z):
    """The island's ground (solid top): a low mound, 14 on its crest, about 11 x 10 above the lake,
    with a bite in its north side, faceted by tilted slabs, and shelving under the water to the bed."""
    cx, cz = F.ISLAND_CENTRE
    ex, ez = x - cx, z - cz
    rho = math.hypot(ex / 1.08, ez) + 0.7 * sn(x, z, 3, 89)
    bite = math.exp(-((x - 26.5) ** 2 + (z - 37.2) ** 2) / 5.0)
    rho += 2.6 * bite
    tx, tz = F.ISLAND_TOP[0], F.ISLAND_TOP[2]
    crest = math.hypot(x - tx, z - tz)
    h = 14.4 - 0.55 * crest if rho <= 5.0 else None
    edge = 10.9 - 1.0 * (rho - 5.0)
    h = max(h if h is not None else -9, edge if rho > 5.0 else -9)
    if rho <= 5.0:
        h = max(h, edge)
    for scx, scz, hu, hv, ang, gx, gz in ISLAND_SLABS:
        a = math.radians(ang)
        u = (x - scx) * math.cos(a) + (z - scz) * math.sin(a)
        v = -(x - scx) * math.sin(a) + (z - scz) * math.cos(a)
        if abs(u) <= hu and abs(v) <= hv:
            h += max(-1.0, min(1.0, gx * (x - scx) + gz * (z - scz)))
            break
    return int(math.floor(min(h, F.ISLAND_TOP[1] + 0.99)))


# Ceiling panels: (x, z, highest air). Flat panels meeting in steps, lowest at the walls.
PANELS = [
    (36, 40, 35), (42, 34, 33), (29, 35, 32), (46, 45, 31), (25, 45, 30), (33, 50, 31),
    (52, 46, 31), (50, 38, 30), (18, 38, 27), (20, 52, 24), (15, 58, 23), (30, 59, 25),
    (47, 61, 24), (40, 56, 27), (22, 30, 25), (12, 46, 24), (54, 54, 26), (36, 29, 28),
    (44, 51, 29), (30, 44, 33),
]


def _ceiling_panel(x, z):
    best, lvl = 1e9, 20
    for px, pz, h in PANELS:
        d = math.hypot(x - px, z - pz) + 2.6 * sn(x, z, 4, 1000 + px * 7 + pz)
        if d < best:
            best, lvl = d, h
    return lvl


class _Cache:
    pass


_C = None


def _segments(points):
    return list(zip(points, points[1:]))


def _build():
    c = _Cache()
    c.fields = [Field(m['poly']) for m in MASSES]
    c.cav = Field(CAVERN)
    c.lake = Field(LAKE)
    c.ante = Field(ANTE)
    carve = {}

    def put(p, kind):
        carve[p] = kind

    # ---- cleft: a 3-wide crack at the waterline, leaning west, narrowing upward --------------
    cleft = set()
    for y in range(38, F.CLEFT_Y[1] + 1):
        x0 = F.CLEFT_X[0] - (1 if y >= 46 else 0)
        width = 3 if y <= 50 else 2
        back = 53 if y <= F.SEA_Y else (54 if y <= 46 else (55 if y <= 49 else 56))
        for x in range(x0, x0 + width):
            for z in range(back, 62):
                cleft.add((x, y, z))
    c.cleft = cleft
    for p in cleft:
        put(p, 'water' if p[1] <= F.SEA_Y else 'air')

    # ---- flooded tunnel ------------------------------------------------------------------------
    tunnel = set()
    for (ax, ay, az), (bx, by, bz) in _segments(F.TUNNEL):
        lo = [min(ax, bx) - 3, min(ay, by) - 3, min(az, bz) - 3]
        hi = [max(ax, bx) + 3, max(ay, by) + 3, max(az, bz) + 3]
        L2 = (bx - ax) ** 2 + (by - ay) ** 2 + (bz - az) ** 2
        for x in range(lo[0], hi[0] + 1):
            for y in range(lo[1], hi[1] + 1):
                for z in range(lo[2], hi[2] + 1):
                    t = max(0.0, min(1.0, ((x - ax) * (bx - ax) + (y - ay) * (by - ay)
                                            + (z - az) * (bz - az)) / L2))
                    px, py, pz = ax + t * (bx - ax), ay + t * (by - ay), az + t * (bz - az)
                    d = math.sqrt((x - px) ** 2 + ((y - py) * 1.1) ** 2 + (z - pz) ** 2)
                    if d <= 1.45 + 0.35 * sn(x + y, z, 2.5, 55):
                        tunnel.add((x, y, z))
    for p in tunnel:
        put(p, 'water' if p[1] <= F.SEA_Y else 'air')

    # ---- antechamber: pool, step, lip, floor, sloping ceiling --------------------------------
    ante = set()
    (px0, px1), (py0, py1), (pz0, pz1) = F.POOL
    for x in range(40, 56):
        for z in range(32, 48):
            sd = c.ante(x, z) - 0.6 * sn(x, z, 2.5, 66)
            if sd <= 0:
                continue
            # first solid of the ceiling: 51 over the pool falling to 48 at the north-east exit
            k = smooth(((41.5 - z) + 0.35 * (x - 46.5)) / 4.5)
            ceil = int(round(51 - 3 * k + 0.6 * sn(x, z, 3, 67)))
            ceil = max(48, min(52, ceil))
            pool = px0 <= x <= px1 and pz0 <= z <= pz1
            step = (z == pz0 - 1 and px0 + 1 <= x <= px1 + 1)
            if pool:
                floor = py0 - 1
            elif step:
                floor = F.ANTE_FLOOR - 1
            else:
                floor = F.ANTE_FLOOR
            for y in range(floor + 1, ceil):
                ante.add((x, y, z))
    for p in ante:
        put(p, 'water' if p[1] <= F.SEA_Y else 'air')

    # ---- arch passage and the arch itself ------------------------------------------------------
    arch = set()
    for x in range(44, 53):
        for z in range(30, 41):
            d = min(_seg_dist(x, z, a[0], a[1], b[0], b[1])[0] for a, b in _segments(ARCH_PASSAGE))
            if d <= 0.95 + 0.25 * vn(x, z, 2, 68):
                if x == F.ARCH_X:
                    continue
                for y in range(F.ARCH_FEET, F.ARCH_FEET + 3):
                    arch.add((x, y, z))
    # the arch: z 32..34, air 45..47, rough and pointed (the top row only on two cells, off-centre)
    for z in range(F.ARCH_Z[0], F.ARCH_Z[1] + 1):
        for y in (45, 46):
            arch.add((F.ARCH_X, y, z))
    for z in (33, 34):
        arch.add((F.ARCH_X, 47, z))
    for p in arch:
        put(p, 'air')

    # ---- descent -------------------------------------------------------------------------------
    descent_all, descent_guard = set(), set()
    segs = _segments(F.DESCENT)
    for si, ((ax, af, az), (bx, bf, bz)) in enumerate(segs):
        for x in range(min(ax, bx) - 4, max(ax, bx) + 5):
            for z in range(min(az, bz) - 4, max(az, bz) + 5):
                d, t = _seg_dist(x, z, ax, az, bx, bz)
                hw = 1.05 + 0.45 * vn(x, z, 3, 71)
                hx, hy, hz = F.DESCENT_HALFWAY
                hw += 1.4 * max(0.0, 1 - math.hypot(x - hx, z - hz) / 3.2)
                if d > hw:
                    continue
                if si == 0 and x <= F.ARCH_X:
                    continue
                feet = int(round(af + (bf - af) * t))
                tall = 3 + (1 if vn(x, z, 2.5, 72) > 0.72 else 0)
                if math.hypot(x - hx, z - hz) < 2.5:
                    tall += 1
                for y in range(feet, feet + tall):
                    descent_all.add((x, y, z))
                    if si < F.DESCENT_TAIL_FROM:
                        descent_guard.add((x, y, z))
    for p in descent_all:
        put(p, 'air')
    c.descent = descent_guard

    # ---- protect: nothing of the cavern within 4 blocks of the way in ------------------------
    ball = [(dx, dy, dz) for dx in range(-5, 6) for dy in range(-5, 6) for dz in range(-5, 6)
            if dx * dx + dy * dy + dz * dz <= 4.5 * 4.5]
    protect = set()
    for p in tunnel | ante | arch | descent_guard:
        if p[1] > 40 and not (p in descent_guard):
            # high parts of the way in sit far above the cavern; only their lowest cells matter
            pass
        for dx, dy, dz in ball:
            protect.add((p[0] + dx, p[1] + dy, p[2] + dz))

    # ---- cavern floor -----------------------------------------------------------------------
    floor, ceil = {}, {}
    (bx0, bx1), _, (bz0, bz1) = F.CAVERN_BOX
    cav_cols = []
    for x in range(bx0, bx1 + 1):
        for z in range(bz0, bz1 + 1):
            if c.cav(x, z) - 1.3 * sn(x, z, 3.5, 80) > 0:
                cav_cols.append((x, z))
    cav_set = set(cav_cols)

    for x, z in cav_cols:
        sl = c.lake(x, z) + 1.1 * sn(x, z, 3, 81)
        if sl > 0:
            cliff = (z < 36) or (x < 19)
            k = smooth(sl / (3.0 if cliff else 7.0))
            f = 8 - int(round(5 * k + 0.6 * sn(x, z, 2.5, 82)))
            f = max(3, min(8, f))
        else:
            dl = -sl
            beach = F.BEACH[0][0] <= x <= F.BEACH[0][1] and F.BEACH[1][0] - 2 <= z <= F.BEACH[1][1] + 2
            rate = 0.35 if beach else 0.65
            f = 10 + int(math.floor(dl * rate + 0.8 * vn(x, z, 2.5, 83)))
            if x > 50:
                f += int((x - 50) * 0.5)
            f = min(f, 17)
        floor[(x, z)] = f

    # north ledge: a shelf on the north wall, solid top 18, with talus spilling to the water
    (lx0, lx1), ltop, (lz0, lz1) = F.NORTH_LEDGE
    for x, z in cav_cols:
        if lx0 <= x <= lx1:
            depth = lz1 + (1 if vn(x, 0, 3, 84) > 0.55 else 0)
            if z <= depth:
                floor[(x, z)] = max(floor[(x, z)], ltop - (1 if vn(x, z, 2, 85) > 0.8 else 0))
            elif 27 <= x <= 32 and c.lake(x, z) + 1.1 * sn(x, z, 3, 81) <= 0:
                # talus spills from the ledge but stops at the water's edge
                spill = ltop - int(1.3 * (z - depth) + 0.7 * vn(x, z, 2, 86))
                floor[(x, z)] = max(floor[(x, z)], spill)

    # island: tilted slabs over a root mound, then no step over 1 anywhere near it
    icx, icz = F.ISLAND_CENTRE
    reach = F.ISLAND_RADIUS + 5
    island_cols = [(x, z) for x, z in cav_cols if math.hypot(x - icx, z - icz) <= reach + 1]
    for x, z in island_cols:
        floor[(x, z)] = max(floor[(x, z)], island_height(x, z))
    # relax only inside the audited reach and only against neighbours inside it, so nothing from
    # the walls or the north ledge is dragged out into the lake
    relax = {(x, z) for x, z in island_cols if math.hypot(x - icx, z - icz) <= F.ISLAND_RADIUS + 4.6
             and c.lake(x, z) + 1.1 * sn(x, z, 3, 81) > 0}
    for _ in range(30):
        changed = False
        for x, z in relax:
            f = floor[(x, z)]
            m = max(floor[(x + dx, z + dz)] if (x + dx, z + dz) in relax else -9
                    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if m - 1 > f:
                floor[(x, z)] = m - 1
                changed = True
        if not changed:
            break

    # mouth ledge
    (mx0, mx1), mtop, (mz0, mz1) = F.MOUTH_LEDGE
    for x, z in cav_cols:
        if mx0 <= x <= mx1 + 2 and mz0 - 1 <= z <= mz1 + 1:
            edge = (x == mx0 and z in (mz0 - 1, mz1 + 1)) or (x >= mx1 + 2 and z == mz0 - 1)
            if not edge:
                floor[(x, z)] = mtop

    # shore ramp: a 4-connected walk through the waypoints, drops placed unevenly
    path = []
    pts = F.SHORE_PATH
    for (ax, af, az), (bx, bf, bz) in _segments(pts):
        cells = [(ax, az)]
        x, z = ax, az
        while (x, z) != (bx, bz):
            ex, ez = bx - x, bz - z
            if abs(ex) * 1.0 / max(1, abs(bx - ax)) >= abs(ez) * 1.0 / max(1, abs(bz - az)) and ex:
                x += 1 if ex > 0 else -1
            elif ez:
                z += 1 if ez > 0 else -1
            else:
                x += 1 if ex > 0 else -1
            cells.append((x, z))
        steps = len(cells) - 1
        drop = af - bf
        order = sorted(range(steps), key=lambda i: -_hash(i, ax * 31 + az, 97))
        drops = set(order[:drop])
        top = af - 1
        seg = [(cells[0], top)]
        for i in range(steps):
            if i in drops:
                top -= 1
            seg.append((cells[i + 1], top))
        if path:
            seg = seg[1:]
        path += seg
    c.ramp = path
    ramp_top = {}
    for (px, pz), t in path:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                q = (px + dx, pz + dz)
                if q not in cav_set or c.lake(*q) > -1.0:
                    continue
                if (dx, dz) != (0, 0) and vn(q[0], q[1], 2, 98) < 0.25:
                    continue
                if q not in ramp_top or (dx, dz) == (0, 0):
                    ramp_top[q] = max(ramp_top.get(q, -9), t) if (dx, dz) != (0, 0) else t
    for (px, pz), t in path:
        ramp_top[(px, pz)] = t
    for q, t in ramp_top.items():
        floor[q] = max(floor[q], t) if q not in [p for p, _ in path] else t

    # ---- cavern ceiling ------------------------------------------------------------------------
    for x, z in cav_cols:
        dw = c.cav(x, z) - 1.3 * sn(x, z, 3.5, 80)
        cl = min(_ceiling_panel(x, z), int(19 + 2.6 * dw + 1.2 * sn(x, z, 3, 90)))
        if math.hypot(x - 52, z - 46) <= 4.5:
            cl = max(cl, 30)
        (sx0, sx1), _, (sz0, sz1) = F.FALLEN_SLAB
        if sx0 <= x <= sx1 and sz0 <= z <= sz1:
            cl = max(cl, 24)       # the scar the slab fell from
        if (x, z) in ramp_top:
            cl = max(cl, ramp_top[(x, z)] + 3)
        if F.NORTH_LEDGE[0][0] <= x <= F.NORTH_LEDGE[0][1] and z <= F.NORTH_LEDGE[2][1] + 1:
            cl = max(cl, F.NORTH_LEDGE[1] + 3)
        ceil[(x, z)] = min(35, cl)

    # ---- fallen slab: a tilted plate half in the lake at the north-west wall --------------------
    slab = set()
    (sx0, sx1), (sy0, sy1), (sz0, sz1) = F.FALLEN_SLAB
    for x in range(sx0, sx1 + 1):
        for z in range(sz0, sz1 + 1):
            if (x, z) in ((sx1, sz0), (sx0, sz1)):
                continue
            top = int(round(sy1 - (x - sx0) * (sy1 - sy0 - 1) / float(sx1 - sx0) + 0.4 * (z - sz0) * 0.5))
            for y in (top - 1, top):
                slab.add((x, y, z))
    c.slab = slab

    # Walls lean in as they rise (the ceiling is lowest where they close), roughened in 3D, except
    # where a ledge or the shore ramp is cut along them.
    cut_along = set(ramp_top)
    for x, z in cav_cols:
        if math.hypot(x - 52, z - 46) <= 3.5:
            cut_along.add((x, z))
    cavern = set()
    for x, z in cav_cols:
        dw = c.cav(x, z) - 1.3 * sn(x, z, 3.5, 80)
        for y in range(floor[(x, z)] + 1, ceil[(x, z)] + 1):
            p = (x, y, z)
            if p in protect or p in slab:
                continue
            if (x, z) not in cut_along:
                need = 0.55 * max(0, y - 12) + 1.8 * sn(x + 1.7 * y, z - 1.3 * y, 3.2, 79) - 0.8
                if y <= F.LAKE_Y + 1:
                    need -= 0.8          # the waterline is undercut
                if dw < need:
                    continue
            cavern.add(p)
    # the mouth passage tail is part of the descent and opens onto the ledge regardless
    for p in cavern:
        if p not in carve:
            put(p, 'water' if p[1] <= F.LAKE_Y else 'air')
    for p in slab:
        carve.pop(p, None)
    c.cavern = cavern
    c.floor, c.ceil = floor, ceil
    c.cav_cols = cav_set

    # ---- seabed, apron and the cap over the cavern -----------------------------------------
    c.carve = carve
    c.base = _seabed(c)
    # boulders are seated on the seabed where they fall, half buried; any whose spot lies outside
    # the footprint is dropped
    c.boulders = []
    for bx, by, bz, rx, ry, rz in BOULDERS:
        if (bx, bz) not in c.base or c.rim_dist[(bx, bz)] <= 7:
            continue
        f0 = c.base[(bx, bz)]
        ring = [c.base.get((bx + dx, bz + dz), -99) for dx in range(-3, 4) for dz in range(-3, 4)]
        if min(ring) < f0 - 2:
            continue                  # on a steep flank it would stand proud as a pillar
        c.boulders.append((bx, f0 + 0.4 * ry, bz, rx, ry, rz))
    si = [m['name'] for m in MASSES].index('spire')
    c.spire_face = {}
    for x in range(51, 59):
        for z in range(10, 45):
            if _mass_solid(c, si, MASSES[si], x, 73, z):
                c.spire_face[x] = z
                break
    return c


RIM_MARGIN = 2       # nothing is listed within this many blocks of the frame edge


def _seabed(c):
    outline = [c.fields[i] for i, m in enumerate(MASSES) if m['name'] in ('body', 'bench', 'needle', 'stump')]
    main = [c.fields[i] for i, m in enumerate(MASSES) if m['name'] in ('body', 'bench', 'stump')]
    needle = c.fields[[m['name'] for m in MASSES].index('needle')]
    base = {}
    for x in range(W):
        for z in range(D):
            d_out = -max(f(x, z) for f in main)
            west = smooth((44 - x) / 20.0)
            sw = smooth((z - 46) / 16.0) * smooth((38 - x) / 14.0)
            A = 1.5 + 6.5 * max(west, sw) + 1.5 * vn(x, z, 5, 120)
            f_apron = 39.0 + 1.6 * sn(x, z, 3, 121) + 0.8 * sn(x, z, 1.7, 122)
            e = min(x, W - 1 - x, z, D - 1 - z)
            f_edge = 23.5 + 2.5 * vn(x, z, 7, 123)
            if d_out <= A:
                f = f_apron
            else:
                t = (d_out - A) / (d_out - A + e + 0.5)
                f = f_apron + (f_edge - f_apron) * smooth(t * 1.15)
            # the needle's own foot, lower, with a channel between it and the main foot
            dn = -needle(x, z)
            fn = 37.0 - 1.1 * max(0.0, dn) + 0.6 * sn(x, z, 2, 124)
            if dn < 6 and d_out > 1.0:
                f = min(f, 36.0) if dn < 4.5 else f
            f = max(f, fn)
            # keep the sea open in front of the cleft
            if 33 <= x <= 43 and 58 <= z <= 70:
                f = min(f, 36.0 - 0.15 * (z - 58))
            base[(x, z)] = f
    # the cap: at least y 28 and 4 solid over the cavern plus a 4-block margin, feathered beyond
    hi = {}
    for (x, z) in c.cav_cols:
        top = c.ceil[(x, z)]
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                if dx * dx + dz * dz <= 16:
                    q = (x + dx, z + dz)
                    hi[q] = max(hi.get(q, -1), top)
    cap = {}
    for q, top in hi.items():
        cap[q] = max(28.0, top + 5.0) + 1.6 * vn(q[0], q[1], 2.5, 126)
    # feather: each step out loses about 1.3, so the cap's edge slopes into the seabed
    for _ in range(12):
        for x in range(W):
            for z in range(D):
                best = cap.get((x, z), -99)
                for dx, dz, cost in ((1, 0, 1.3), (-1, 0, 1.3), (0, 1, 1.3), (0, -1, 1.3),
                                     (1, 1, 1.8), (-1, 1, 1.8), (1, -1, 1.8), (-1, -1, 1.8)):
                    v = cap.get((x + dx, z + dz))
                    if v is not None and v - cost > best:
                        best = v - cost
                if best > -99:
                    cap[(x, z)] = best
    for q, v in cap.items():
        if q in base and q in hi:
            base[q] = max(base[q], v)
        elif q in base:
            base[q] = max(base[q], v + 0.6 * sn(q[0], q[1], 2.2, 127))
    # --- the footprint: everything the template writes stands inside it, and its rim sinks to
    # local 2..8 (world ~23..29) before it ends, so the natural sea floor runs up to the rock
    # instead of meeting a square cliff at the frame edge. Outside it nothing is listed.
    core = set()
    for x in range(W):
        for z in range(D):
            if any(c.fields[i](x, z) > -2.5 for i in range(len(MASSES))):
                core.add((x, z))
    core |= {(p[0], p[2]) for p in c.carve}
    for (x, z) in c.cav_cols:
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                core.add((x + dx, z + dz))
    edge = [q for q in core if any((q[0] + dx, q[1] + dz) not in core
                                   for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    foot = set()
    for x in range(RIM_MARGIN, W - RIM_MARGIN):
        for z in range(RIM_MARGIN, D - RIM_MARGIN):
            if (x, z) in core:
                foot.add((x, z))
                continue
            dmin = min((x - a) ** 2 + (z - b) ** 2 for a, b in edge)
            if dmin <= (11.0 + 3.0 * vn(x, z, 6, 130)) ** 2:
                foot.add((x, z))
    outside = [(x, z) for x in range(-1, W + 1) for z in range(-1, D + 1) if (x, z) not in foot]
    rim_edge = [q for q in outside if any((q[0] + dx, q[1] + dz) in foot
                                          for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    out = {}
    for (x, z) in foot:
        d = math.sqrt(min((x - a) ** 2 + (z - b) ** 2 for a, b in rim_edge))
        if d <= 6.0:
            lim = 2.0 + 1.3 * d + 0.5 * vn(x, z, 3, 131)        # at most 10 within 6 of the rim
        else:
            # then a flank whose steepness wanders, so it is not a frustum
            # then a flank of 4.5..5.5 a block, steep enough to reach the stack's foot from the
            # rim and never more than 6 between neighbours
            lim = (9.8 + (4.5 + 1.0 * vn(x, z, 7, 132)) * (d - 6.0)
                   + 0.5 * sn(x, z, 2.5, 133) * min(1.0, (d - 6.0) / 3.0))
        out[(x, z)] = int(math.floor(min(base[(x, z)], lim)))
        if d <= 6.0:
            out[(x, z)] = min(out[(x, z)], 10)
    c.rim_dist = {q: math.sqrt(min((q[0] - a) ** 2 + (q[1] - b) ** 2 for a, b in rim_edge))
                  for q in foot}
    return out


def _cache():
    global _C
    if _C is None:
        _C = _build()
    return _C


def _mass_solid(c, i, m, x, y, z):
    lx, lz = m['lean']
    ref = m['ref']
    up = max(0, y - ref)
    px, pz = x - lx * (y - ref), z - lz * (y - ref)
    d = c.fields[i](px, pz)
    if d < -4.5:
        return False
    e = bed_eff(x, y, z)
    if e >= m['top'](x, z, d):
        return False
    bi, rec, lo, hi = bed(e)
    # Walls are fluted, not ringed: narrow vertical facets that drift slowly with height carry the
    # face, and only the softest beds cut a recess, and only along part of the perimeter.
    rec = {0: 0.0, 1: 0.35, 2: 1.4}[rec] * smooth(1.6 * vn(x, z, 8, m['seed'] + 31 * bi) - 0.3)
    flute = sn(x + 0.12 * y, z - 0.09 * y, 2.6, m['seed']) + 0.7 * sn(x, z, 7, m['seed'] + 5)
    taper = m['taper'] * up
    if m['side'] is not None:
        u, v = xz_to_uv(x, z)
        cu, cv = m['centre']
        su, sv = m['side']
        k = ((u - cu) * su + (v - cv) * sv) / (math.hypot(u - cu, v - cv) * math.hypot(su, sv) + 1e-6)
        taper *= max(0.0, k)
    inset = (taper + rec * m['recess'] + m['shared'] * flute
             + m['band'] * 0.4 * sn(x, z, 3.5, m['seed'] + 13 * bi))
    if m['notch']:
        inset += _notch_inset(x, y, z)
    if m['name'] in ('body', 'spire'):
        inset += _joint_inset(x, y, z, JOINTS[:-2])
    elif m['name'] == 'bench':
        inset += _joint_inset(x, y, z, JOINTS[-2:])       # the surge channels only
    return d > inset


def envelope(x, y, z):
    """Rock before carving: masses, flake, boulders, seabed and cap."""
    if not (0 <= x < W and 0 <= y < H and 0 <= z < D):
        return False
    c = _cache()
    if (x, z) not in c.base:
        return False         # outside the footprint the world's own sea floor and sea stay
    if y <= c.base[(x, z)]:
        return True
    if c.rim_dist[(x, z)] <= 6.0 and y > 10:
        return False         # the rim stays low: nothing stands near the footprint's edge
    if _crack(x, y, z):
        return False
    for i, m in enumerate(MASSES):
        if _mass_solid(c, i, m, x, y, z):
            return True
    if _flake(x, y, z):
        return True
    if 20 <= y <= 45 and _boulder(x, y, z):
        return True
    return False


def carved(x, y, z):
    c = _cache()
    k = c.carve.get((x, y, z))
    if k is None:
        return None
    if (x, y, z) in c.cleft and not envelope(x, y, z):
        return None      # the cleft is only carved where it cuts rock; outside is open sea
    return k


def solid(x, y, z):
    c = _cache()
    if (x, y, z) in c.carve:
        return False
    return envelope(x, y, z)


def feature_cells():
    c = _cache()
    return {'descent': c.descent, 'cavern': c.cavern}
