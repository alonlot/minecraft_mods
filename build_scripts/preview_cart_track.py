"""Design and inspect the Gringotts cart line before a single block is carved.

    python build_scripts/preview_cart_track.py [--depth 122] [--out build/track]

WHY THIS EXISTS
---------------
The undercroft is carved along a spline, and the spline is the one part of the build that
cannot be judged from a block render. Two faults in particular are invisible in a picture
of the finished tunnel and fatal in it:

  * a self-intersection, where the corridor bores through a stretch of itself; and
  * a gradient spike, where the track pitches over hard enough to throw the cart off it.

Both are trivially visible in a plan, an elevation and a gradient chart, and both are
cheap to fix while the line is still 40 lines of Python. So the layout gets settled here
first, and the Java carve is handed a shape that is already known to work.

The curve is a CENTRIPETAL Catmull-Rom (alpha = 0.5), not a uniform one. Uniform
Catmull-Rom forms cusps and little self-intersecting loops wherever the control points
bunch up, and on this curve a cusp is a corridor that pinches to zero width and a cart
that reverses direction at full speed. Centripetal provably cannot do either.

Everything is sampled by ARC LENGTH rather than by the raw curve parameter, because the
raw parameter runs at wildly different speeds through tight and loose segments -- a cart
driven by it would accelerate in the corners, which is exactly backwards.
"""

import argparse
import math
import os

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np


# --- the line ------------------------------------------------------------------------
# Depth offsets are fractions of D, the station-floor-to-chasm-floor budget, because that
# budget is terrain-dependent: a bank on a mountain gets a longer, gentler ride and one at
# sea level a shorter, steeper one. The destination is the constant, not the journey.
#
# Three axes, not one. A single axis makes the whole thing a cylinder, and a cylinder is
# the failure mode this build is most likely to fall into.

# The line is ONE SPIRAL ABOUT ONE CENTRE, with a radius that breathes and a centre that
# drifts slowly. Everything else was tried first and each failed on one of two rocks:
#
#   * arcs about fixed axes stay contained but do not leave tangent to the line arriving at
#     them, so every join was a kink and two were near-cusps -- seven blocks of arc covering
#     two blocks of ground. The corridor swept around one of those laid its own deck through
#     its own clearance.
#   * dead reckoning is tangent-continuous by construction but wanders: a 180-degree turn at
#     radius 30 displaces the line 60 blocks on its own, and no combination of sweeps and
#     straights kept it inside a 96-block box.
#
# A spiral has neither problem. It is smooth everywhere because r and theta are smooth, and
# it is contained because every point is r from a centre that barely moves. The variety comes
# from the radius: swinging between 14 and 32 pushes the line out against the chasm wall and
# pulls it back in over the void, which is where the ledges and trestles come from, and it
# crosses over itself vertically the way a real mine road does. The centre's slow drift stops
# it reading as a perfect cylinder.

# Revolutions of descent, scaled so each turn drops about the same amount however deep the
# bank happens to sit. Holding the turn count fixed makes the gradient a function of depth --
# the same 104 blocks of plunge carrying 44 blocks of drop under a sea-level bank and 65
# under a mountain one, which puts the steep bands past 1:2 and the cart off the rail.
DROP_PER_TURN = 24.0
TURNS_MIN, TURNS_MAX = 4.0, 7.5


def turns_for(depth):
    return max(TURNS_MIN, min(TURNS_MAX, depth / DROP_PER_TURN))
R_BASE = 22.0           # mean radius
R_AMP = 9.0             # how far the radius breathes either side of the mean
R_CYCLES = 1.5          # breaths over the whole descent
R_HARMONIC = 0.45       # strength of the second, faster breath
R_HARMONIC_CYCLES = 4.3 # deliberately not a multiple of R_CYCLES
DRIFT = 6.0             # how far the centre wanders, blocks
APRON = 16.0            # the straight out of the station before the spiral takes over

# Named bands of the descent. `at` is the fraction of the way down the band ENDS.
SEGMENTS = [
    dict(name='station apron',    at=0.00, dy=0.00),
    dict(name='the plunge',       at=0.10, dy=0.36),
    dict(name='loading gallery',  at=0.22, dy=0.04),
    dict(name='stalactite reach', at=0.40, dy=0.10),
    dict(name='high chasm ledge', at=0.56, dy=0.12),
    dict(name="thief's downfall", at=0.66, dy=0.10),
    dict(name='dragon lap',       at=0.80, dy=0.12),
    dict(name='low chasm ledge',  at=0.92, dy=0.09),
    dict(name='deep approach',    at=1.00, dy=0.07),
]

FOOTPRINT = 96          # the carve's horizontal box, centred on the station
# The corridor is 7 wide and 6 tall, so the clearance it needs is not a sphere. Two passes
# running side by side need 9 blocks between their centre lines; two passes STACKED need
# only 8, and 8 is a perfectly ordinary 2-block rock slab between a ledge and the ledge
# below it -- which is what the chasm is supposed to look like. Testing a single radius
# flagged that stacking as a collision and sent me tuning away a feature.
BORE_CLEARANCE_H = 9.0
BORE_CLEARANCE_V = 8.0
# Arc-length gap past which two samples count as genuinely different track.
#
# This number is derived, not chosen, and getting it wrong twice cost real time. Two points
# Ds apart along a turn of radius r are 2*r*sin(Ds/2r) apart in space, so ANY turn looks like
# a near-miss if the window is short enough: at Ds=6 an r=13 turn is only 5.9 blocks across,
# well inside the 9 blocks a corridor needs. Too long, though, and a genuine cusp hides inside
# it -- at 40 the line doubled back within 7 blocks of arc and the check never looked.
#
# 12 is the window where the tightest turn the layout actually uses (r=12) is just clear at
# 2*12*sin(0.5) = 11.5, while a cusp of effective radius 4 comes out at 7.6 and is caught.
SELF_APPROACH_WINDOW = 12.0
MAX_GRADE = 0.50        # |dy/ds|. 1:2 is the plunge; anything steeper and the cart leaves the rail
KNOT_SPACING = 9.0      # target distance between control points
VERTICAL_CURVE = 16.0   # blocks over which a grade change is eased in

# The depth budget this layout is valid across, station floor to chasm floor. Both ends are
# measured, not guessed -- run --sweep to re-check them after any change to SEGMENTS.
#   Below DEPTH_MIN the ledges stack too close: less total drop means less height between a
#   pass and the pass beneath it, and the chasm's two lobes start sharing rock.
#   Above DEPTH_MAX the same horizontal line has to carry more drop, and the gradient runs
#   past 1:2 -- at which point the cart is not on a rail any more.
# A bank whose terrain cannot give at least DEPTH_MIN gets no undercroft and stays a bank.
DEPTH_MIN = 96
DEPTH_MAX = 180


# --- knot generation ------------------------------------------------------------------

def spiral_point(t, turns):
    """Plan position at t in [0, 1] along the descent."""
    theta = -2.0 * math.pi * turns * t
    # Two harmonics, not one. A single sine makes every lap the same circle at a slightly
    # different radius, and in plan that reads as concentric rings -- which is the exact
    # "it is just a spiral" failure the reference spec warns about. Adding a faster, weaker
    # term makes each lap a different shape: the line bulges out against the chasm wall in
    # one place and pulls in over the void in another, and no two laps do it in the same
    # place. It costs almost nothing in reach because the second term is small.
    r = (R_BASE
         + R_AMP * math.sin(2.0 * math.pi * R_CYCLES * t + 0.6)
         + R_AMP * R_HARMONIC * math.sin(2.0 * math.pi * R_HARMONIC_CYCLES * t + 2.1))
    cx = DRIFT * math.sin(2.0 * math.pi * 0.5 * t)
    cz = DRIFT * math.cos(2.0 * math.pi * 0.5 * t) - DRIFT
    return cx + r * math.cos(theta), cz + r * math.sin(theta)


def build_knots(depth):
    """Lay out the line in PLAN only. Height is applied afterwards, over arc length.

    Keeping Y out of here is deliberate. Distributing each segment's drop linearly across
    that segment makes dy/ds step at every junction, and because the curve also kinks
    there, ds shrinks at the same moment and the gradient chart spikes -- a cart would
    launch off the rail at a place the plan view shows as a perfectly ordinary corner.
    Real track eases into a grade change with a vertical curve, and so does this.
    """
    # The apron runs straight out of the station to where the spiral begins, so the cart
    # has somewhere to sit and be boarded that is not already leaning over.
    turns = turns_for(depth)
    entry = spiral_point(0.0, turns)
    knots = [(entry[0] - APRON, entry[1])]
    apron_steps = max(2, int(round(APRON / KNOT_SPACING)))
    for i in range(1, apron_steps + 1):
        knots.append((entry[0] - APRON * (1 - i / apron_steps), entry[1]))

    bounds = [len(knots) - 1]
    drops = [SEGMENTS[0]['dy'] * depth]

    steps = max(64, int(turns * 2.0 * math.pi * R_BASE / KNOT_SPACING))
    band = 1
    for i in range(1, steps + 1):
        t = i / steps
        knots.append(spiral_point(t, turns))
        while band < len(SEGMENTS) and t >= SEGMENTS[band]['at'] - 1e-9:
            bounds.append(len(knots) - 1)
            drops.append(SEGMENTS[band]['dy'] * depth)
            band += 1
    while band < len(SEGMENTS):
        bounds.append(len(knots) - 1)
        drops.append(SEGMENTS[band]['dy'] * depth)
        band += 1

    return knots, bounds, drops


def apply_profile(pts, s, bounds, drops, depth, samples_per_segment=16):
    """Set the Y column from a smoothed grade profile laid over arc length.

    Each segment asks for a drop; that becomes a target grade for its stretch of the line.
    The grade array is then smoothed over VERTICAL_CURVE blocks, so the transitions are
    ramps rather than steps, and integrated. A final rescale makes the total drop land
    exactly on the depth budget however much the smoothing moved things around.
    """
    edges = [0] + [min(b * samples_per_segment, len(pts) - 1) for b in bounds]
    grade = np.zeros(len(pts))
    for i in range(len(drops)):
        lo, hi = edges[i], edges[i + 1]
        run = s[hi] - s[lo]
        if hi > lo and run > 1e-6:
            grade[lo:hi] = drops[i] / run

    ds = np.diff(s, prepend=s[0])
    window = max(3, int(VERTICAL_CURVE / max(ds.mean(), 1e-6)))
    kernel = np.ones(window) / window
    grade = np.convolve(grade, kernel, mode='same')

    y = -np.cumsum(grade * ds)
    if y[-1] != 0:
        y *= depth / -y[-1]
    pts[:, 1] = y
    return pts


# --- centripetal Catmull-Rom ----------------------------------------------------------

def spline(knots, samples_per_segment=16):
    """-> Nx3 array of points along the plan curve, densely sampled. Y is left at zero."""
    flat = np.asarray(knots, dtype=float)
    p = np.zeros((len(flat), 3))
    p[:, 0] = flat[:, 0]
    p[:, 2] = flat[:, 1]
    # Duplicate the ends so the first and last segments are drawn rather than skipped.
    p = np.vstack([p[0] + (p[0] - p[1]), p, p[-1] + (p[-1] - p[-2])])

    out = []
    for i in range(len(p) - 3):
        p0, p1, p2, p3 = p[i], p[i + 1], p[i + 2], p[i + 3]
        # Centripetal knot parameterisation: t advances by the SQUARE ROOT of chord length.
        t = [0.0]
        for a, b in ((p0, p1), (p1, p2), (p2, p3)):
            d = float(np.linalg.norm(b - a))
            t.append(t[-1] + max(d, 1e-6) ** 0.5)
        t0, t1, t2, t3 = t

        for s in range(samples_per_segment):
            tt = t1 + (t2 - t1) * (s / samples_per_segment)
            a1 = (t1 - tt) / (t1 - t0) * p0 + (tt - t0) / (t1 - t0) * p1
            a2 = (t2 - tt) / (t2 - t1) * p1 + (tt - t1) / (t2 - t1) * p2
            a3 = (t3 - tt) / (t3 - t2) * p2 + (tt - t2) / (t3 - t2) * p3
            b1 = (t2 - tt) / (t2 - t0) * a1 + (tt - t0) / (t2 - t0) * a2
            b2 = (t3 - tt) / (t3 - t1) * a2 + (tt - t1) / (t3 - t1) * a3
            out.append((t2 - tt) / (t2 - t1) * b1 + (tt - t1) / (t2 - t1) * b2)
    out.append(p[-2])
    return np.asarray(out)


def arc_lengths(pts):
    step = np.linalg.norm(np.diff(pts, axis=0), axis=1)
    return np.concatenate([[0.0], np.cumsum(step)])


# --- audits ---------------------------------------------------------------------------

def audit(pts, s, depth):
    problems = []

    grade = np.abs(np.diff(pts[:, 1]) / np.maximum(np.diff(s), 1e-6))
    worst = float(grade.max())
    if worst > MAX_GRADE:
        where = float(s[int(grade.argmax())])
        problems.append('gradient %.2f (1:%.1f) at s=%.0f exceeds the %.2f limit'
                        % (worst, 1 / worst, where, MAX_GRADE))

    # Self-intersection. Compare every sample against every sample far enough away along
    # the line that they are not simply neighbours. The chasm lobes are MEANT to stack, so
    # this is a 3D test: passing 18 blocks below an earlier ledge is the design, passing
    # 4 blocks beside it is a collision.
    stride = 4
    q = pts[::stride]
    qs = s[::stride]
    closest = None
    for i in range(len(q)):
        far = np.abs(qs - qs[i]) > SELF_APPROACH_WINDOW
        if not far.any():
            continue
        delta = q[far] - q[i]
        dh = np.hypot(delta[:, 0], delta[:, 2])
        dv = np.abs(delta[:, 1])
        # Ellipsoid: 1.0 means exactly touching the corridor envelope, below 1.0 overlaps.
        occupancy = np.hypot(dh / BORE_CLEARANCE_H, dv / BORE_CLEARANCE_V)
        j = int(occupancy.argmin())
        d = occupancy
        if closest is None or d[j] < closest[0]:
            closest = (float(d[j]), float(qs[i]), float(qs[far][j]),
                       q[i].copy(), q[far][j].copy())
    if closest and closest[0] < 1.0:
        a, b = closest[3], closest[4]
        problems.append('corridors overlap at %.0f%% of clearance (s=%.0f vs s=%.0f): '
                        '%.1f apart horizontally, %.1f vertically'
                        % (closest[0] * 100, closest[1], closest[2],
                           math.hypot(a[0] - b[0], a[2] - b[2]), abs(a[1] - b[1])))

    half = FOOTPRINT / 2.0
    reach = max(np.abs(pts[:, 0]).max(), np.abs(pts[:, 2]).max())
    if reach + 6 > half:
        problems.append('track reaches %.0f blocks from the station; the %d footprint allows %.0f'
                        % (reach, FOOTPRINT, half - 6))

    return problems, worst, closest, reach


# --- drawing --------------------------------------------------------------------------

SEG_COLOURS = plt.get_cmap('turbo')(np.linspace(0.08, 0.95, len(SEGMENTS)))


def draw(pts, s, bounds, knots, depth, out, stats):
    worst, closest, reach = stats
    fig = plt.figure(figsize=(17, 10), facecolor='#14141a')
    grid = fig.add_gridspec(2, 2, height_ratios=[2.0, 1.0], hspace=0.28, wspace=0.20)

    per = np.searchsorted(np.asarray([0] + bounds) * 16, np.arange(len(pts)), side='right') - 1
    per = np.clip(per, 0, len(SEGMENTS) - 1)

    def style(ax, title):
        ax.set_facecolor('#14141a')
        ax.set_title(title, color='#e8e8f0', fontsize=11, pad=8)
        ax.tick_params(colors='#8a8a98', labelsize=8)
        for spine in ax.spines.values():
            spine.set_color('#3a3a46')
        ax.grid(True, color='#26262e', linewidth=0.6)

    # --- plan
    ax = fig.add_subplot(grid[0, 0])
    style(ax, 'plan  (+X east, +Z south)  -- colour by segment')
    half = FOOTPRINT / 2.0
    ax.add_patch(plt.Rectangle((-half, -half), FOOTPRINT, FOOTPRINT, fill=False,
                               edgecolor='#d05050', linestyle='--', linewidth=1.2))
    for i in range(len(SEGMENTS)):
        m = per == i
        ax.plot(pts[m, 0], pts[m, 2], color=SEG_COLOURS[i], linewidth=2.0,
                label='%d %s' % (i, SEGMENTS[i]['name']))
    if closest is not None:
        a, b = closest[3], closest[4]
        ax.plot([a[0], b[0]], [a[2], b[2]], color='#ff4040', linewidth=1.4, zorder=5)
        ax.scatter([a[0], b[0]], [a[2], b[2]], color='#ff4040', s=42, zorder=6,
                   marker='x', label='tightest gap %.0f%%' % (closest[0] * 100))
    ax.plot(0, 0, marker='o', color='#f0d060', markersize=7)
    ax.set_aspect('equal')
    ax.invert_yaxis()
    ax.legend(fontsize=7, facecolor='#1c1c24', edgecolor='#3a3a46',
              labelcolor='#c8c8d4', loc='upper left', ncol=1)

    # --- elevation
    ax = fig.add_subplot(grid[0, 1])
    style(ax, 'elevation  (looking north; X across, Y up)')
    for i in range(len(SEGMENTS)):
        m = per == i
        ax.plot(pts[m, 0], pts[m, 1], color=SEG_COLOURS[i], linewidth=2.0)
    if closest is not None:
        a, b = closest[3], closest[4]
        ax.plot([a[0], b[0]], [a[1], b[1]], color='#ff4040', linewidth=1.4, zorder=5)
        ax.scatter([a[0], b[0]], [a[1], b[1]], color='#ff4040', s=42, zorder=6, marker='x')
    ax.axhline(-depth, color='#d05050', linestyle='--', linewidth=1.0)
    ax.annotate('chasm floor  -%d' % depth, (ax.get_xlim()[0], -depth), color='#d05050',
                fontsize=8, textcoords='offset points', xytext=(4, 4))
    ax.set_aspect('equal')

    # --- gradient
    ax = fig.add_subplot(grid[1, :])
    style(ax, 'gradient along the ride  (|dy/ds|; the cart leaves the rail above the limit)')
    grade = np.abs(np.diff(pts[:, 1]) / np.maximum(np.diff(s), 1e-6))
    window = 9
    smooth = np.convolve(grade, np.ones(window) / window, mode='same')
    ax.plot(s[1:], smooth, color='#60c0f0', linewidth=1.4)
    ax.axhline(MAX_GRADE, color='#d05050', linestyle='--', linewidth=1.0)
    if closest is not None:
        for marker in (closest[1], closest[2]):
            ax.axvline(marker, color='#ff4040', linewidth=1.0, alpha=0.7)
    for i, b in enumerate(bounds):
        edge = float(s[min(b * 16, len(s) - 1)])
        ax.axvline(edge, color='#3a3a46', linewidth=0.8)
        ax.annotate(str(i), (edge, MAX_GRADE * 1.06), color='#8a8a98', fontsize=7)
    ax.set_xlabel('distance along the line (blocks)', color='#8a8a98', fontsize=9)
    ax.set_ylim(0, max(MAX_GRADE * 1.25, float(smooth.max()) * 1.1))

    fig.suptitle('Gringotts cart line   %.0f blocks   depth %d   worst grade 1:%.1f   '
                 'tightest corridor gap %.0f%%   reach %.0f'
                 % (s[-1], depth, 1 / max(worst, 1e-6),
                    (closest[0] * 100) if closest else float('nan'), reach),
                 color='#e8e8f0', fontsize=13)

    os.makedirs(out, exist_ok=True)
    path = os.path.join(out, 'cart_track.png')
    fig.savefig(path, dpi=110, facecolor='#14141a')
    plt.close(fig)
    return path


def evaluate(depth):
    knots, bounds, drops = build_knots(depth)
    pts = spline(knots)
    pts = apply_profile(pts, arc_lengths(pts), bounds, drops, depth)
    s = arc_lengths(pts)
    return (knots, bounds, pts, s) + audit(pts, s, depth)


def sweep():
    """Audit the whole depth range. The layout has to work under any bank, not just one."""
    print('depth   track   drop   worst grade   corridor gap   reach')
    failures = 0
    for depth in range(DEPTH_MIN, DEPTH_MAX + 1, 6):
        _, _, pts, s, problems, worst, closest, reach = evaluate(depth)
        print('%5d %7.0f %6.0f        1:%-5.1f %11.0f%% %7.0f   %s'
              % (depth, s[-1], -pts[-1][1], 1 / max(worst, 1e-6),
                 (closest[0] * 100) if closest else float('nan'), reach,
                 'ok' if not problems else 'FAIL'))
        for p in problems:
            print('        ' + p)
        failures += len(problems)
    print()
    print('%s across %d..%d' % ('all depths pass' if not failures else '%d FAILURES' % failures,
                                DEPTH_MIN, DEPTH_MAX))
    return 1 if failures else 0


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--depth', type=int, default=122,
                    help='station floor to chasm floor, in blocks (default 122, a sea-level bank)')
    ap.add_argument('--out', default='build/track')
    ap.add_argument('--sweep', action='store_true',
                    help='audit every depth in the valid range instead of drawing one')
    args = ap.parse_args()

    if args.sweep:
        raise SystemExit(sweep())

    knots, bounds, drops = build_knots(args.depth)
    pts = spline(knots)
    pts = apply_profile(pts, arc_lengths(pts), bounds, drops, args.depth)
    s = arc_lengths(pts)
    problems, worst, closest, reach = audit(pts, s, args.depth)

    print('%d knots -> %d samples   %.0f blocks of track   %.0f blocks of drop'
          % (len(knots), len(pts), s[-1], -pts[-1][1]))
    print('  worst gradient    1:%.1f  (limit 1:%.1f)' % (1 / max(worst, 1e-6), 1 / MAX_GRADE))
    if closest:
        a, b = closest[3], closest[4]
        print('  tightest corridor gap  %.0f%% of clearance  (%.1f apart in plan, %.1f in height)'
              % (closest[0] * 100, math.hypot(a[0] - b[0], a[2] - b[2]), abs(a[1] - b[1])))
    print('  reach from station     %.0f blocks  (footprint allows %.0f)'
          % (reach, FOOTPRINT / 2.0 - 6))
    print()
    start = 0
    for i, seg in enumerate(SEGMENTS):
        end = float(s[min(bounds[i] * 16, len(s) - 1)])
        print('  %d %-18s %6.0f blocks   drop %5.1f   1:%-5.1f' % (
            i, seg['name'], end - start, seg['dy'] * args.depth,
            (end - start) / max(seg['dy'] * args.depth, 1e-6)) if seg['dy'] else
            '  %d %-18s %6.0f blocks   level' % (i, seg['name'], end - start))
        start = end

    print()
    path = draw(pts, s, bounds, knots, args.depth, args.out, (worst, closest, reach))
    print('wrote %s' % path)
    if problems:
        print()
        for p in problems:
            print('  PROBLEM  ' + p)
    raise SystemExit(1 if problems else 0)


if __name__ == '__main__':
    main()
