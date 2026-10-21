"""The Pensieve scene generators' shared mirrors of the game engine.

Every generator that emits a memory checks it against the engine's own maths, never a private copy:
  - the camera (CameraTrack.sample, its spline and its eased blends): engine_blend, engine_spline, engine_cam,
    engine_look, camera_samples, camera_frames, camera_motion, and the checks on them (check_camera_smoothness,
    check_camera_clearance, check_spline_moves); rekey and turn_between re-key a shot through them;
  - the player model as ActorPoser and HumanoidModel pose it: actor_eye_height (EyeEase), posed_parts, the held-item
    raise (item_raise_steps), emitted_arm, life_arm_corners, part_corners, inside_part, box_distance, box_surface;
  - the world's block shapes: shape_hit, in_air;
  - actor tracks (Track.sample, spline keys included; Vehicle's ride windows): track_sample, sample_path, own_sample;
  - the memory brooms and their riders (BroomFlight, Vehicle): broom_pose, broom_frame, broom_bank, broom_climb,
    broom_heading, hover_bob, broom_box, broom_lowest, rider_root, rider_pose, rider_lean, rider_weight, and
    posed_parts, emitted_arm, arm_tip and eye_of composing a rider's frame (a RiderFrame in the yaw slot);
  - the hand contacts' sway corners (BOB_Z, BOB_X, BOB_MID) and arm_tip, eye_of.

`python -I scene_engine.py --write-broom-cases` rewrites broom_cases.json (BroomFlightTest.pythonMirrorAgrees).

Imports only the standard library, scene_common and human_motion (no generator), so any generator can import it.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import FREE  # noqa: E402
import human_motion as HM  # noqa: E402


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def ease_f(e, f):
    if e == 'linear':
        return f
    if e == 'in':
        return f ** 3
    if e == 'out':
        return 1 - (1 - f) ** 3
    return 4 * f ** 3 if f < 0.5 else 1 - (-2 * f + 2) ** 3 / 2


def sample_path(path, t):
    """An emitted path's position at tick t (the engine's rule: hold the ends, ease per segment, spline keys)."""
    return track_sample(path, t)


EASE_SLOPES = {'linear': (1, 1), 'in': (0, 3), 'out': (3, 0), 'in_out': (0, 0), 'spline': (1, 1)}   # Ease start/end


def track_sample(keys, t, field='pos', lo=0, hi=None):
    """Track.sample over keys[lo:hi] (all by default) at tick t: hold the ends; a segment takes the ease of the key
    it ends at; a `spline` key's segment is CameraTrack's Hermite (inner velocity (h1*d0 + h0*d1)/(h0+h1), the eased
    neighbour's own speed at a run end, natural first and last ends). None when the range has no keys."""
    hi = len(keys) if hi is None else hi
    if hi <= lo:
        return None
    if t <= keys[lo]['t']:
        return keys[lo][field]
    for i in range(lo + 1, hi):
        a, b = keys[i - 1], keys[i]
        if t <= b['t']:
            if b['t'] == a['t']:
                return b[field]
            if b.get('ease') == 'spline':
                return _track_spline(keys, i, t, field, lo, hi)
            f = ease_f(b.get('ease', 'in_out'), (t - a['t']) / (b['t'] - a['t']))
            return [a[field][k] + (b[field][k] - a[field][k]) * f for k in range(min(len(a[field]), len(b[field])))]
    return keys[hi - 1][field]


def _track_spline(keys, b, t, field, lo, hi):
    def spl(i):
        return lo < i < hi and keys[i].get('ease') == 'spline' and keys[i]['t'] > keys[i - 1]['t']

    def mov(i):
        return lo < i < hi and keys[i]['t'] > keys[i - 1]['t']

    a = b - 1
    ta, tb = keys[a]['t'], keys[b]['t']
    dt = tb - ta
    va, vb = keys[a][field], keys[b][field]
    n = min(len(va), len(vb))
    vp = keys[a - 1][field] if a - 1 >= lo else None
    vn = keys[b + 1][field] if b + 1 < hi else None
    tp = keys[a - 1]['t'] if a - 1 >= lo else 0
    tn = keys[b + 1]['t'] if b + 1 < hi else 0
    start = 0 if spl(a) else 1 if mov(a) else 2
    end = 0 if spl(b + 1) else 1 if mov(b + 1) else 2
    if vp is not None and len(vp) < n:
        start = 2
    if vn is not None and len(vn) < n:
        end = 2
    s_in = EASE_SLOPES[keys[a].get('ease', 'in_out')][1] if mov(a) else 0
    s_out = EASE_SLOPES[keys[b + 1].get('ease', 'in_out')][0] if mov(b + 1) else 0
    u = max(0.0, min(1.0, (t - ta) / dt))
    u2, u3 = u * u, u * u * u
    h00, h10, h01, h11 = 2 * u3 - 3 * u2 + 1, u3 - 2 * u2 + u, -2 * u3 + 3 * u2, u3 - u2

    def tangent(p0, p1, p2, g0, g1):
        return (g1 * (p1 - p0) / g0 + g0 * (p2 - p1) / g1) / (g0 + g1)

    out = []
    for c in range(n):
        sec = (vb[c] - va[c]) / dt
        ma = (tangent(vp[c], va[c], vb[c], ta - tp, dt) if start == 0
              else s_in * (va[c] - vp[c]) / (ta - tp) if start == 1 else None)
        mb = (tangent(va[c], vb[c], vn[c], dt, tn - tb) if end == 0
              else s_out * (vn[c] - vb[c]) / (tn - tb) if end == 1 else None)
        if ma is None and mb is None:
            ma = mb = sec
        elif ma is None:
            ma = (3 * sec - mb) / 2
        elif mb is None:
            mb = (3 * sec - ma) / 2
        out.append(h00 * va[c] + h10 * dt * ma + h01 * vb[c] + h11 * dt * mb)
    return out


INF = float('inf')


def ride_of(a):
    """(vehicle id, from, until) of an actor's `ride` (a string rides for the whole scene), or None."""
    r = a.get('ride')
    if r is None:
        return None
    if isinstance(r, str):
        return r, -INF, INF
    return r['on'], r.get('from', -INF), r.get('until', INF)


def rides_at(a, t):
    """Vehicle.ridesAt: in its ride window [from, until) at t."""
    r = ride_of(a)
    return r is not None and r[1] <= t < r[2]


def window_part(a, t):
    """Vehicle.window: the part [lo, hi) of the actor's ride window that t falls in (all time without one)."""
    r = ride_of(a)
    if r is None:
        return -INF, INF
    if t < r[1]:
        return -INF, r[1]
    if t >= r[2]:
        return r[2], INF
    return r[1], r[2]


def own_sample(a, key, t, field=None):
    """Vehicle.path / Vehicle.look: the actor's own `path` (or look vectors [yaw, pitch, roll]) at t from the keys
    of the window part t is in; None when that part has no keys."""
    if key == 'look':
        keys = [{'t': k['t'], 'v': [k.get('yaw', 0.0), k.get('pitch', 0.0), k.get('roll', 0.0)],
                 'ease': k.get('ease', 'in_out')} for k in a.get('look') or []]
        field = 'v'
    else:
        keys = a.get(key) or []
        field = field or 'pos'
    if not keys:
        return None
    lo_t, hi_t = window_part(a, t)
    if lo_t == -INF and hi_t == INF:
        return track_sample(keys, t, field)
    lo = 0
    while lo < len(keys) and keys[lo]['t'] < lo_t:
        lo += 1
    hi = lo
    while hi < len(keys) and keys[hi]['t'] < hi_t:
        hi += 1
    return track_sample(keys, t, field, lo, hi)


class RiderFrame:
    """The yaw slot of a posed part for a rider whose vehicle tilts it: the rider's body yaw and the tilt Q
    (row-major 3x3) the renderer turns its whole model by about its root (MemoryActors.rideTilt)."""
    def __init__(self, yaw, tilt):
        self.yaw, self.tilt = yaw, tilt


def model_to_world(root, yaw, scale, mx, my, mz):
    """HumanMotion.modelToWorld, with the yaw slot either a number or a RiderFrame (root + Q * the yawed point)."""
    if not isinstance(yaw, RiderFrame):
        return HM.model_to_world(root, yaw, scale, mx, my, mz)
    v = HM.model_to_world([0.0, 0.0, 0.0], yaw.yaw, scale, mx, my, mz)
    q = yaw.tilt
    return [root[i] + q[3 * i] * v[0] + q[3 * i + 1] * v[1] + q[3 * i + 2] * v[2] for i in range(3)]


PASS = set(FREE) | {'minecraft:light', 'minecraft:cobweb', 'minecraft:rose_bush', 'minecraft:poppy',
                    'minecraft:pink_tulip', 'minecraft:white_tulip', 'minecraft:red_tulip', 'minecraft:cornflower',
                    'minecraft:oxeye_daisy', 'minecraft:barrier_air'}


def bid_of(st):
    return None if st is None else st.split('[')[0]


def props_of(st):
    if st is None or '[' not in st:
        return {}
    return dict(kv.split('=') for kv in st[st.index('[') + 1:-1].split(','))


def cell(p):
    return (math.floor(p[0]), math.floor(p[1]), math.floor(p[2]))


def shape_hit(w, p):
    """The block state whose real shape holds point p, or None (vanilla collision shapes, near enough)."""
    c = cell(p)
    st = w.get(c)
    b = bid_of(st)
    if b in PASS:
        return None
    pr = props_of(st)
    fx, fy, fz = p[0] - c[0], p[1] - c[1], p[2] - c[2]
    n = b.split(':')[1]
    th = 3 / 16.0
    if n.endswith('_slab'):
        t = pr.get('type', 'bottom')
        return st if t == 'double' or (fy < 0.5 if t == 'bottom' else fy >= 0.5) else None
    if n.endswith('_stairs'):
        top = pr.get('half') == 'top'
        if (fy >= 0.5) == top:
            return st
        f = pr.get('facing', 'north')
        return st if {'north': fz < 0.5, 'south': fz >= 0.5, 'east': fx >= 0.5, 'west': fx < 0.5}[f] else None
    if n.endswith('_carpet'):
        return st if fy < 1 / 16.0 else None
    if n.endswith('_pressure_plate'):
        return st if fy < 1 / 16.0 else None
    if n.endswith('_trapdoor'):
        if pr.get('open') == 'true':
            f = pr.get('facing')
            return st if {'north': fz > 1 - th, 'south': fz < th, 'east': fx < th, 'west': fx > 1 - th}[f] else None
        return st if (fy >= 1 - th if pr.get('half') == 'top' else fy < th) else None
    if n.endswith('_door'):
        f, op, right = pr.get('facing'), pr.get('open') == 'true', pr.get('hinge') == 'right'
        box = {'south': 'S', 'west': 'W', 'north': 'N', 'east': 'E'}[f]
        if op:
            box = {'south': 'E' if right else 'W', 'west': 'S' if right else 'N', 'north': 'W' if right else 'E',
                   'east': 'N' if right else 'S'}[f]
        return st if {'S': fz < th, 'N': fz > 1 - th, 'W': fx > 1 - th, 'E': fx < th}[box] else None
    if n.endswith('_bed'):
        return st if fy < 9 / 16.0 else None
    if n.endswith('_fence') or n.endswith('_wall') or n == 'iron_bars':
        return st if 0.375 <= fx <= 0.625 and 0.375 <= fz <= 0.625 else None
    if n == 'campfire' or n == 'soul_campfire':
        return st if fy < 7 / 16.0 else None
    if 'candle' in n or n in ('lantern', 'soul_lantern'):
        return st if 0.3 <= fx <= 0.7 and 0.3 <= fz <= 0.7 and fy < 0.6 else None
    if n.startswith('potted_') or n == 'flower_pot':
        return st if 0.31 <= fx <= 0.69 and 0.31 <= fz <= 0.69 and fy < 0.4 else None
    return st


def in_air(w, p, margin=0.0):
    if margin <= 0:
        return shape_hit(w, p) is None
    for dx in (-margin, margin):
        for dy in (-margin, margin):
            for dz in (-margin, margin):
                if shape_hit(w, [p[0] + dx, p[1] + dy, p[2] + dz]):
                    return False
    return True


REST = {'head': (0, 0, 0), 'body': (0, 0, 0), 'right_arm': (-5, 2, 0), 'left_arm': (5, 2, 0),
        'right_leg': (-1.9, 12, 0), 'left_leg': (1.9, 12, 0)}


def fwd_vec(yaw):
    r = math.radians(yaw)
    return (-math.sin(r), math.cos(r))


ITEM_ARM_X = -18.0      # vanilla ArmPose.ITEM: an arm holding something is raised pi/10 (xRot * 0.5 - pi/10 at rest)


def sample_keys(keys, t):
    """A limb track's value at tick t (a rot or offset key list), with the engine's hold-the-ends rule."""
    return sample_path([{'t': k['t'], 'pos': k['v'], 'ease': k.get('ease', 'in_out')} for k in keys], t)


def emitted_arm(actor, part, t, vanilla_pivot, vanilla_rot):
    """(pivot px, rot degrees) of a part at tick t as ActorPoser.applyLimbs leaves it, from the emitted actor JSON:
    mode add puts the keyed values on top of the vanilla state, mode set replaces it (rotation and, where there is
    an offset track, position) outright."""
    tr = actor.get('limbs', {}).get(part, {})
    setm = tr.get('mode') == 'set'
    rot = list(vanilla_rot)
    piv = list(vanilla_pivot)
    if tr.get('rot'):
        v = sample_keys(tr['rot'], t)
        rot = [v[i] if setm else rot[i] + v[i] for i in range(3)]
    if tr.get('offset'):
        v = sample_keys(tr['offset'], t)
        piv = [v[i] if setm else piv[i] + v[i] for i in range(3)]
    return piv, rot


ITEM_STEP_EPS = 0.001     # ticks: the compensating step runs over the last thousandth of a tick before the swap


def item_raise_steps(actor, part='right_arm', slot='mainhand'):
    """Cancel vanilla's held-item raise in an add-mode arm track where the held item comes and goes.

    MemoryActors sets the item on the client tick whose scene time reaches the cue (an integer tick T), so every
    frame from T on (scene time T + partial) draws the arm raised ITEM_ARM_X, or no longer raised. Here the keyed
    arm steps by the opposite amount over (T - ITEM_STEP_EPS, T], so at T and after it the two cancel: the hand
    stays where it was. The step carries through the hold the track is in, and the next move then lands where it
    was going. T must fall on a key or inside a held stretch, so splitting the track there changes no curve.
    Returns [(T, step degrees)]."""
    tr = actor['limbs'][part]
    assert tr.get('mode', 'add') == 'add', (actor['id'], part, 'item raise steps need add mode')
    keys = tr['rot']
    out = []
    swaps = sorted({c['t'] for c in actor.get('actions', []) if c['do'] in ('hold', 'equip', 'use_item')})
    for T in swaps:
        before, after = EquipTimeline_item(actor, slot, T - 1), EquipTimeline_item(actor, slot, T)
        if (before is None) == (after is None):
            continue
        d = -ITEM_ARM_X if after else ITEM_ARM_X
        v = sample_keys(keys, T)
        i = next((j for j, k in enumerate(keys) if k['t'] >= T), len(keys))
        on_key = i < len(keys) and keys[i]['t'] == T
        if not on_key:
            assert 0 < i < len(keys) and keys[i - 1]['v'] == keys[i]['v'], (actor['id'], T, 'swap inside a move')
            keys.insert(i, {'t': T, 'v': list(keys[i - 1]['v'])})
        keys[i] = dict(keys[i], t=T - ITEM_STEP_EPS)
        stepped = [v[0] + d, v[1], v[2]]
        keys.insert(i + 1, {'t': T, 'v': [int(round(c)) for c in stepped], 'ease': 'linear'})
        j = i + 2
        while j < len(keys) and keys[j]['v'] == [int(round(c)) for c in v]:
            keys[j] = dict(keys[j], v=[int(round(c)) for c in stepped])
            j += 1
        out.append((T, d))
    return out


def life_arm_corners(gain=0.35, energy=1.25):
    """The extremes life can add to an add-mode right arm while standing still (HumanMotion.life): breathing
    (arm pivot down 0.35 px, arm zRot +0.8 gained, torso pitch -0.7), the weight shift (every part across by
    0.5 * energy px, torso roll -0.9) and the roll noise (0.4), the torso turns going through hipTurn at the
    body's gain. Returns [(pivot delta px, rot delta deg)] over the corners of breath, shift and noise."""
    out = []
    for b in (0.0, 0.5, 1.0):
        for ws in (-1.0, 0.0, 1.0):
            for n in (-1.0, 0.0, 1.0):
                pitch, roll = -0.7 * b, -0.9 * ws + 0.4 * n
                sh = HM._turned(-5.0, -10.0, 0.0, math.radians(pitch), 0.0, math.radians(roll))
                dp = [0.5 * energy * ws + (sh[0] + 5.0) * gain, -0.35 * b + (sh[1] + 10.0) * gain, sh[2] * gain]
                out.append((dp, [0.0, 0.0, 0.8 * b * gain]))
    return out


def part_corners(root, yaw, scale, pivot, rot, box):
    """World corners of a model part's box (px ranges ((x0, x1), (y0, y1), (z0, z1)) about its pivot)."""
    out = []
    for x in box[0]:
        for y in box[1]:
            for z in box[2]:
                v = HM._turned(x, y, z, *[math.radians(c) for c in rot])
                out.append(model_to_world(root, yaw, scale, pivot[0] + v[0], pivot[1] + v[1], pivot[2] + v[2]))
    return out


def inside_part(root, yaw, scale, pivot, rot, box, p, margin_px=0.0):
    """Whether world point p lies inside the part's box grown by margin_px."""
    o = model_to_world(root, yaw, scale, *pivot)
    loc = []
    for e in ((1, 0, 0), (0, 1, 0), (0, 0, 1)):
        v = HM._turned(*e, *[math.radians(c) for c in rot])
        w = model_to_world(root, yaw, scale, pivot[0] + v[0], pivot[1] + v[1], pivot[2] + v[2])
        a = [w[i] - o[i] for i in range(3)]
        loc.append(sum((p[i] - o[i]) * a[i] for i in range(3)) / sum(c * c for c in a))
    return all(box[i][0] - margin_px <= loc[i] <= box[i][1] + margin_px for i in range(3))


ARM_BOX = ((-3.25, 1.25), (-2.25, 10.25), (-2.25, 2.25))          # the right arm with its sleeve layer


HAND_BOX = ((-3.25, 1.25), (6.0, 10.25), (-2.25, 2.25))           # its last 4 px: the hand


HEAD_BOX = ((-4.5, 4.5), (-8.5, 0.5), (-4.5, 4.5))                # the head with its hat layer


# every player part with its outer layer (wide arms, the conservative choice)
PART_BOX = {'head': HEAD_BOX, 'body': ((-4.25, 4.25), (-0.25, 12.25), (-2.25, 2.25)), 'right_arm': ARM_BOX,
            'left_arm': ((-1.25, 3.25), (-2.25, 10.25), (-2.25, 2.25)),
            'right_leg': ((-2.25, 2.25), (-0.25, 12.25), (-2.25, 2.25)),
            'left_leg': ((-2.25, 2.25), (-0.25, 12.25), (-2.25, 2.25))}


# HumanoidModel.setupAnim's crouch: absolute pivots and the rotation it adds
CROUCH_PIVOT = {'head': (0, 4.2, 0), 'body': (0, 3.2, 0), 'right_arm': (-5, 5.2, 0), 'left_arm': (5, 5.2, 0),
                'right_leg': (-1.9, 12.2, 4.0), 'left_leg': (1.9, 12.2, 4.0)}


CROUCH_ROT = {'body': math.degrees(0.5), 'right_arm': math.degrees(0.4), 'left_arm': math.degrees(0.4)}


def _cam_spline(cam, i):
    """CameraTrack.isSpline: the segment ending at key i is a spline key, not a cut, of nonzero length."""
    return (0 < i < len(cam) and cam[i].get('ease') == 'spline' and not cam[i].get('cut')
            and cam[i]['t'] > cam[i - 1]['t'])


def _cam_moves(i, cam):
    """CameraTrack.moves: the segment ending at key i exists, is not a cut and has length."""
    return 0 < i < len(cam) and not cam[i].get('cut') and cam[i]['t'] > cam[i - 1]['t']


def engine_blend(cam, t):
    """(index of the key before, index of the key after, k, spline) at scene tick t exactly as CameraTrack.sample
    picks them: a cut key holds the key before it until its own tick (k 0); a spline segment is flagged (k unused);
    otherwise k is the after key's ease of the way between them."""
    n = len(cam)
    if t >= cam[-1]['t']:
        return n - 1, n - 1, 0.0, False
    if t <= cam[0]['t']:
        return 0, 0, 0.0, False
    for i in range(1, n):
        b = cam[i]
        if t < b['t']:
            a = cam[i - 1]
            span = b['t'] - a['t']
            if _cam_spline(cam, i):
                return i - 1, i, 0.0, True
            return i - 1, i, (0.0 if b.get('cut') or span <= 0 else ease_f(b.get('ease', 'in_out'),
                                                                           (t - a['t']) / span)), False
    return n - 1, n - 1, 0.0, False


def engine_spline(cam, b, t, values):
    """CameraTrack.spline: the spline segment ending at key b at tick t; values(i) gives key i's interpolated values
    (any length). A cubic Hermite whose key velocities are shared with the neighbouring segments: the parabola slope
    between two spline segments, the eased neighbour's own speed, or a natural end."""
    a = b - 1
    ta, tb = cam[a]['t'], cam[b]['t']
    dt = tb - ta
    va, vb = values(a), values(b)
    vp = values(a - 1) if a - 1 >= 0 else None
    vn = values(b + 1) if b + 1 < len(cam) else None
    tp = cam[a - 1]['t'] if a - 1 >= 0 else 0
    tn = cam[b + 1]['t'] if b + 1 < len(cam) else 0
    start = 0 if _cam_spline(cam, a) else 1 if _cam_moves(a, cam) else 2
    end = 0 if _cam_spline(cam, b + 1) else 1 if _cam_moves(b + 1, cam) else 2
    s_in = EASE_SLOPES[cam[a].get('ease', 'in_out')][1] if _cam_moves(a, cam) else 0
    s_out = EASE_SLOPES[cam[b + 1].get('ease', 'in_out')][0] if _cam_moves(b + 1, cam) else 0
    u = max(0.0, min(1.0, (t - ta) / dt))
    u2, u3 = u * u, u * u * u
    h00, h10, h01, h11 = 2 * u3 - 3 * u2 + 1, u3 - 2 * u2 + u, -2 * u3 + 3 * u2, u3 - u2

    def tangent(p0, p1, p2, g0, g1):
        return (g1 * (p1 - p0) / g0 + g0 * (p2 - p1) / g1) / (g0 + g1)

    out = []
    for c in range(len(va)):
        sec = (vb[c] - va[c]) / dt
        ma = (tangent(vp[c], va[c], vb[c], ta - tp, dt) if start == 0
              else s_in * (va[c] - vp[c]) / (ta - tp) if start == 1 else None)
        mb = (tangent(va[c], vb[c], vn[c], dt, tn - tb) if end == 0
              else s_out * (vn[c] - vb[c]) / (tn - tb) if end == 1 else None)
        if ma is None and mb is None:
            ma = mb = sec
        elif ma is None:
            ma = (3 * sec - mb) / 2
        elif mb is None:
            mb = (3 * sec - ma) / 2
        out.append(h00 * va[c] + h10 * dt * ma + h01 * vb[c] + h11 * dt * mb)
    return out


def engine_cam(cam, t):
    """The camera position at scene tick t as CameraTrack.sample computes it."""
    a, b, k, spl = engine_blend(cam, t)
    if spl:
        return engine_spline(cam, b, t, lambda i: cam[i]['pos'])
    pa, pb = cam[a]['pos'], cam[b]['pos']
    return [pa[c] + (pb[c] - pa[c]) * k for c in range(3)]


def camera_samples(cam, length, per_tick=4):
    """(t, camera position, shot index) every 1/per_tick of a tick over the whole scene; a shot starts at each cut."""
    cuts = [k['t'] for k in cam if k.get('cut')]
    for i in range(int(length * per_tick) + 1):
        t = i / per_tick
        yield t, engine_cam(cam, t), sum(1 for c in cuts if c <= t)


def _yaw_pitch(a, t):
    lk = a.get('look') or [{'t': 0, 'yaw': 0}]
    ks = [{'t': x['t'], 'pos': [x.get('yaw', 0.0), x.get('pitch', 0.0), 0], 'ease': x.get('ease', 'in_out')}
          for x in lk]
    v = sample_path(ks, t)
    return v[0], v[1]


def _last_at(cues, t):
    best = None
    for c in cues:
        if c['t'] <= t:
            best = c
    return best


def posed_parts(scene, a, t):
    """A player actor's six parts at tick t from its emitted JSON: (part, root, yaw, scale, pivot px, rot deg);
    for a rider on a broom the yaw slot is a RiderFrame (_broom_rider_parts).
    Vanilla rest (or the crouch from a `sneak` until the next `stand`, with the held-item arm raise), the pose cue in force (absolute),
    the look pitch on the head, then the limbs (add or set). Life and clips are left out: a few px at most."""
    if broom_rider_at(scene, a, t) is not None:
        return _broom_rider_parts(scene, a, t)
    scale = a.get('scale', 1.0)
    if isinstance(a.get('ride'), dict):
        # a ride window: the path and look are world space outside it, in the vehicle frame inside it
        root = list(own_sample(a, 'path', t) or [0.0, 0.0, 0.0])
        lk = own_sample(a, 'look', t)
        yaw, pitch = (lk[0], lk[1]) if lk is not None else (0.0, 0.0)
    else:
        root = sample_path(a['path'], t)
        yaw, pitch = _yaw_pitch(a, t)
    if a.get('ride') and rides_at(a, t):
        boat = next(x for x in scene['actors'] if x['id'] == ride_of(a)[0])
        bp = sample_path(boat['path'], t)
        by, _ = _yaw_pitch(boat, t)
        f, lft = fwd_vec(by), (math.cos(math.radians(by)), math.sin(math.radians(by)))
        root = [bp[0] + lft[0] * root[0] + f[0] * root[2], bp[1] + root[1], bp[2] + lft[1] * root[0] + f[1] * root[2]]
        yaw += by
    acts = a.get('actions', [])
    crouch = False                                  # the last `sneak` or `stand` at or before t decides it
    for x in sorted(acts, key=lambda x: x['t']):
        if x['t'] <= t and x['do'] in ('sneak', 'stand'):
            crouch = x['do'] == 'sneak'
    if crouch:
        root = [root[0], root[1] - 2.0 / 16.0 * scale, root[2]]
    held = EquipTimeline_item(a, 'mainhand', t)
    pose = _last_at(a.get('poses', []), t)
    pz = scene.get('poses', {}).get(pose['name'], {}) if pose else {}
    out = []
    for part in PART_BOX:
        piv = list(CROUCH_PIVOT[part] if crouch else REST[part])
        rot = [CROUCH_ROT.get(part, 0.0) if crouch else 0.0, 0.0, 0.0]
        if part == 'right_arm' and held:
            rot[0] += ITEM_ARM_X
        if part == 'head':
            rot[0] += pitch
        if part in pz:
            rot = list(pz[part])
        piv, rot = emitted_arm(a, part, t, piv, rot)
        out.append((part, root, yaw, scale, piv, rot))
    return out


def EquipTimeline_item(a, slot, t):
    """The item in a slot at tick t (EquipTimeline): the last equip (or, for the mainhand, hold) at or before t;
    None when empty or air."""
    item = a.get('items', {}).get(slot)
    best = -1e9
    for c in a.get('actions', []):
        if c['t'] > t or c['t'] < best:
            continue
        if (c['do'] == 'equip' and c.get('slot') == slot) or (c['do'] in ('hold', 'use_item') and slot == 'mainhand'
                                                              and c.get('item')):
            best, item = c['t'], c.get('item')
    return None if item in (None, 'minecraft:air') else item


def box_distance(root, yaw, scale, pivot, rot, box, p):
    """Blocks from world point p to a posed part's box (0 inside it)."""
    o = model_to_world(root, yaw, scale, *pivot)
    loc = []
    axes = []
    for e in ((1, 0, 0), (0, 1, 0), (0, 0, 1)):
        v = HM._turned(*e, *[math.radians(c) for c in rot])
        w = model_to_world(root, yaw, scale, pivot[0] + v[0], pivot[1] + v[1], pivot[2] + v[2])
        ax = [w[i] - o[i] for i in range(3)]
        axes.append(ax)
        loc.append(sum((p[i] - o[i]) * ax[i] for i in range(3)) / sum(c * c for c in ax))
    q = [min(max(loc[i], box[i][0]), box[i][1]) for i in range(3)]
    w = [o[i] + sum(q[j] * axes[j][i] for j in range(3)) for i in range(3)]
    return math.dist(w, p)


CAM_CLEAR = 0.15          # blocks from the camera to any part box of any player actor (the near plane is 0.05)


NEAR_PLANE = 0.05


EYE_EASE_TICKS = 4.0


def actor_eye_height(a, t):
    """A player actor's eye above its root at tick t, as MemoryActors.actorEye reads it (EyeEase: 1.62 standing,
    1.27 crouched, smoothstepped over 4 ticks from each `sneak`/`stand`, an interrupted change continuing from where
    it had got to, times its scale)."""
    def at(frm, tgt, start, tt):
        u = (tt - start) / EYE_EASE_TICKS
        if not u > 0:
            return frm
        if u >= 1:
            return tgt
        return frm + (tgt - frm) * (u * u * (3 - 2 * u))
    frm, tgt, start = 0.0, 0.0, float('-inf')
    for x in sorted(a.get('actions', []), key=lambda x: x['t']):
        if x['do'] in ('sneak', 'stand') and x['t'] <= t:
            frm = at(frm, tgt, start, x['t'])
            tgt = 1.0 if x['do'] == 'sneak' else 0.0
            start = x['t']
    f = at(frm, tgt, start, t)
    return (1.62 + (1.27 - 1.62) * f) * a.get('scale', 1.0)


def engine_look(scene, cam, t):
    """(the point the camera looks at, its vertical fov) at tick t, as CameraTrack.sample blends them (an actor's
    eye: its path plus the player eye height at its scale, crouched after a sneak)."""
    def target(k):
        la = k.get('look_at')
        if isinstance(la, str):
            a_ = next(x for x in scene['actors'] if x['id'] == la[6:])
            if is_broom(a_):                                                  # its eye is its origin, the seat
                return broom_pose(scene, a_, t)['pos']
            if broom_rider_at(scene, a_, t) is not None:
                return rider_eye(scene, a_, t)
            r = own_sample(a_, 'path', t) if isinstance(a_.get('ride'), dict) else sample_path(a_['path'], t)
            if a_.get('type', 'player') == 'hogwartsmagic:memory_rowboat':     # its entity eye: 0.85 of 0.8 high
                return [r[0], r[1] + 0.68 * a_.get('scale', 1.0), r[2]]
            return [r[0], r[1] + actor_eye_height(a_, t), r[2]]
        if la is not None:
            return la
        return [k['pos'][0], k['pos'][1], k['pos'][2] + 1]
    ia, ib, k, spl = engine_blend(cam, t)
    if spl:
        v = engine_spline(cam, ib, t, lambda i: list(target(cam[i])) + [cam[i].get('fov', 70)])
        return v[:3], max(10.0, min(150.0, v[3]))
    a, b = cam[ia], cam[ib]
    fa, fb = target(a), target(b)
    fov = a.get('fov', 70) + (b.get('fov', 70) - a.get('fov', 70)) * k
    return [fa[c] + (fb[c] - fa[c]) * k for c in range(3)], fov


ASPECT = 2.0              # screen width over height assumed for the frustum (wider than 16:9, to be safe)


def in_view(c, look, fov, p, aspect=ASPECT):
    """Whether world point p is inside the camera's view frustum past the near plane (`aspect`: width over height)."""
    ax = [look[i] - c[i] for i in range(3)]
    n = math.sqrt(sum(v * v for v in ax)) or 1.0
    ax = [v / n for v in ax]
    rg = [ax[2], 0.0, -ax[0]]
    nr = math.sqrt(rg[0] ** 2 + rg[2] ** 2) or 1.0
    rg = [v / nr for v in rg]
    up = [rg[1] * ax[2] - rg[2] * ax[1], rg[2] * ax[0] - rg[0] * ax[2], rg[0] * ax[1] - rg[1] * ax[0]]
    d = [p[i] - c[i] for i in range(3)]
    z = sum(d[i] * ax[i] for i in range(3))
    if z < NEAR_PLANE:
        return False
    tv = math.tan(math.radians(fov) / 2)
    return (abs(sum(d[i] * up[i] for i in range(3))) <= z * tv
            and abs(sum(d[i] * rg[i] for i in range(3))) <= z * tv * aspect)


def box_surface(root, yaw, scale, pivot, rot, box, n=6):
    """World points on a posed part box's six faces, an n x n grid on each."""
    out = []
    g = [i / (n - 1) for i in range(n)]
    for axis in range(3):
        for side in box[axis]:
            u, w = [j for j in range(3) if j != axis]
            for fu in g:
                for fw in g:
                    q = [0.0, 0.0, 0.0]
                    q[axis] = side
                    q[u] = box[u][0] + (box[u][1] - box[u][0]) * fu
                    q[w] = box[w][0] + (box[w][1] - box[w][0]) * fw
                    v = HM._turned(*q, *[math.radians(c) for c in rot])
                    out.append(model_to_world(root, yaw, scale, pivot[0] + v[0], pivot[1] + v[1],
                                                 pivot[2] + v[2]))
    return out


def check_camera_clearance(sid, scene, problems):
    """The interpolated camera, four times a tick over the whole scene, against every part of every player actor:
    at least CAM_CLEAR from each part box, unless no point of that box is in the picture (inside the view frustum
    past the near plane, as the head behind a first-person eye). Returns (smallest clearance of a part in the
    picture, t, actor, part)."""
    cam = scene['camera']
    best = (9.0, None, None, None)
    for t, c, shot in camera_samples(cam, scene['length']):
        view = None
        for a in scene['actors']:
            if a.get('type', 'player') != 'player':
                continue
            r = sample_path(a['path'], t)
            if not a.get('ride') and math.dist(r, c) > 3.0 * a.get('scale', 1.0):
                continue
            for part, root, yaw, scale, piv, rot in posed_parts(scene, a, t):
                d = box_distance(root, yaw, scale, piv, rot, PART_BOX[part], c)
                if d >= best[0] and d >= CAM_CLEAR:
                    continue
                view = view or engine_look(scene, cam, t)
                if not any(in_view(c, view[0], view[1], q)
                           for q in box_surface(root, yaw, scale, piv, rot, PART_BOX[part])):
                    continue
                if d < CAM_CLEAR:
                    problems.append('%s: the camera at t %.2f is %.3f from %s %s' % (sid, t, d, a['id'], part))
                    return best
                best = (d, t, a['id'], part)
    return best


FPS_SUB = 3               # 60 fps: three frames a tick


def camera_frames(scene):
    """The camera as the player sees it at 60 fps through the engine's maths (engine_cam, engine_look; partial
    ticks included), split into shots at the cuts: {shot: [(t, pos, unit view direction), ...]}."""
    cam = scene['camera']
    cuts = [k['t'] for k in cam if k.get('cut')]
    out = {}
    for i in range(scene['length'] * FPS_SUB + 1):
        t = i / FPS_SUB
        c = engine_cam(cam, t)
        lk, _ = engine_look(scene, cam, t)
        dx, dy, dz = lk[0] - c[0], lk[1] - c[1], lk[2] - c[2]
        n = math.sqrt(dx * dx + dy * dy + dz * dz) or 1.0
        out.setdefault(sum(1 for x in cuts if x <= t), []).append((t, c, [dx / n, dy / n, dz / n]))
    return out


def camera_motion(scene):
    """Each shot's frame-to-frame motion: {shot: dict}. Per frame: v, the move (blocks a frame), and w, the turn
    (the angular velocity of the view direction, axis times degrees a frame: free of the yaw/pitch pole and of
    the centripetal turn of the direction itself); a = |v - v before| and aw = |w - w before|, the change of
    velocity and of turn rate from one frame to the next (an acceleration: what a key corner or a snap shows up as);
    the spikes ja = a - max(a of the frames either side) and jw likewise: how far one frame's change stands out
    from its neighbours (a corner is all spike; an eased move, however brisk, has none). Keys: t0, t1, speed, turn
    (the largest), a, aw, ja, jw (the largest, each with its tick in t_<key>)."""
    out = {}
    for shot, fr in camera_frames(scene).items():
        vs, ws = [], []
        for p, q in zip(fr, fr[1:]):
            vs.append([q[1][j] - p[1][j] for j in range(3)])
            a_, b_ = p[2], q[2]
            ws.append([math.degrees(a_[1] * b_[2] - a_[2] * b_[1]), math.degrees(a_[2] * b_[0] - a_[0] * b_[2]),
                       math.degrees(a_[0] * b_[1] - a_[1] * b_[0])])
        acc = [math.dist(vs[i - 1], vs[i]) for i in range(1, len(vs))]
        accw = [math.dist(ws[i - 1], ws[i]) for i in range(1, len(ws))]
        st = {'t0': fr[0][0], 't1': fr[-1][0], 'speed': max([math.sqrt(sum(x * x for x in v)) for v in vs] or [0]),
              'turn': max([math.sqrt(sum(x * x for x in w)) for w in ws] or [0])}
        for key, arr in (('a', acc), ('aw', accw)):
            st[key], st['t_' + key] = 0.0, fr[0][0]
            for i, x in enumerate(arr):
                if x > st[key]:
                    st[key], st['t_' + key] = x, fr[i + 2][0]
        for key, arr in (('ja', acc), ('jw', accw)):
            st[key], st['t_' + key] = 0.0, fr[0][0]
            for i in range(1, len(arr) - 1):
                x = arr[i] - max(arr[i - 1], arr[i + 1])
                if x > st[key]:
                    st[key], st['t_' + key] = x, fr[i + 2][0]
        out[shot] = st
    return out


# Smoothness bounds at 60 fps, from the shots that play smoothly (the eased moves of all four scenes before the
# boat-scene fix: a 0.0011 blocks/frame and aw 0.055 deg/frame at most, spikes ja 0.00003 and jw 0.0022 at most) with
# about 2x / 4x headroom. The boat scene's old linear X2/X3 keys broke them by 6-40x (a 0.012, ja 0.012, jw 0.18),
# X4's look at the stepped boat course by 26x (jw 0.058), and an actor look target snapping on a crouch by 100x.
CAM_A_MAX = 0.002         # blocks a frame: the camera's velocity change from one frame to the next


CAM_AW_MAX = 0.08         # degrees a frame: the turn rate's change from one frame to the next


CAM_JA_MAX = 0.0003       # blocks a frame: a velocity change standing out from both neighbouring frames' (a corner)


CAM_JW_MAX = 0.01         # degrees a frame: likewise for the turn rate


def check_camera_smoothness(sid, scene, problems):
    """Every shot through the engine's camera maths at 60 fps (camera_motion): no velocity or turn-rate jump from one
    frame to the next beyond the bounds above. Cuts are left out. Returns the per-shot motion."""
    m = camera_motion(scene)
    for shot, s in sorted(m.items()):
        for key, lim, unit in (('a', CAM_A_MAX, 'blocks'), ('aw', CAM_AW_MAX, 'deg'), ('ja', CAM_JA_MAX, 'blocks'),
                               ('jw', CAM_JW_MAX, 'deg')):
            if s[key] > lim:
                problems.append('%s shot %d (t %.0f-%.0f): camera %s %.5f %s/frame at t %.2f (limit %g)' % (
                    sid, shot, s['t0'], s['t1'], key, s[key], unit, s['t_' + key], lim))
    return m


def rekey(cam, actors, t0, t1, n, look=None, smooth=0.0, keep_first=True):
    """Re-key the camera from tick t0 to tick t1 (one shot, or part of one) as n spline segments sampled from the
    engine's own path (engine_cam, the fov of engine_look), so the run is C1 through every key. The look target is
    look(t) when given, else engine_look's, averaged over a triangle of +-smooth ticks inside [t0, t1) (which turns a
    look target that snaps, an actor's eye height on a crouch, into a glide). The key at t0 stays unless keep_first
    is False (its cut flag stays either way)."""
    orig = [dict(k) for k in cam]
    view = {'actors': actors, 'camera': orig}
    i0 = max(i for i, k in enumerate(cam) if k['t'] == t0)
    i1 = i0
    while i1 + 1 < len(cam) and cam[i1 + 1]['t'] <= t1 and not cam[i1 + 1].get('cut'):
        i1 += 1
    assert cam[i1]['t'] == t1, (t0, t1, cam[i1]['t'])
    hi = t1 - 1e-3

    def target(t):
        if look:
            return look(t)
        if not smooth:
            return engine_look(view, orig, t)[0]
        acc, wsum = [0.0, 0.0, 0.0], 0.0
        steps = int(smooth * 2)
        for j in range(-steps, steps + 1):
            w_ = steps + 1 - abs(j)
            q = engine_look(view, orig, clamp(t + j / 2.0, t0, hi))[0]
            acc = [acc[c] + q[c] * w_ for c in range(3)]
            wsum += w_
        return [c / wsum for c in acc]
    new = []
    for i in range(n + 1):
        t = t0 + (t1 - t0) * i / n
        tt = min(t, hi)
        k = {'t': int(round(t)), 'pos': [round(c, 4) for c in engine_cam(orig, tt)],     # 4 places: dense keys
             'look_at': [round(c, 4) for c in target(tt)]}                              # rounded to 3 jitter
        fov = round(engine_look(view, orig, tt)[1], 2)
        if fov != 70:
            k['fov'] = fov
        if i:
            k['ease'] = 'spline'
        new.append(k)
    assert all(a['t'] < b['t'] for a, b in zip(new, new[1:])), (t0, t1, n)
    if keep_first:
        new[0] = cam[i0]
    elif cam[i0].get('cut'):
        new[0]['cut'] = True
    cam[i0:i1 + 1] = new


def turn_between(cam, actors, ta, tb, n):
    """Re-key the eased move between the keys at ticks ta and tb (fixed look points, the camera on its old path) so
    the view direction turns on the great circle between the two keys' directions on a quintic smoothstep (no step
    in the turn's acceleration, at rest at both ends) instead of the look point sliding on a chord past the lens: a
    spline run of n segments."""
    ia = max(i for i, k in enumerate(cam) if k['t'] == ta)
    ib = ia + 1
    assert cam[ib]['t'] == tb and not cam[ib].get('cut'), (ta, tb)
    pa, pb, la, lb = cam[ia]['pos'], cam[ib]['pos'], cam[ia]['look_at'], cam[ib]['look_at']
    da, db = [la[i] - pa[i] for i in range(3)], [lb[i] - pb[i] for i in range(3)]
    na, nb = math.sqrt(sum(c * c for c in da)), math.sqrt(sum(c * c for c in db))
    ua, ub = [c / na for c in da], [c / nb for c in db]
    om = math.acos(clamp(sum(ua[i] * ub[i] for i in range(3)), -1.0, 1.0))
    orig = [dict(k) for k in cam]

    def look(t):
        u = clamp((t - ta) / (tb - ta), 0.0, 1.0)
        e = u * u * u * (u * (u * 6 - 15) + 10)
        s0, s1 = math.sin((1 - e) * om) / math.sin(om), math.sin(e * om) / math.sin(om)
        p = engine_cam(orig, t)
        d = na + (nb - na) * e
        return [p[i] + (ua[i] * s0 + ub[i] * s1) * d for i in range(3)]
    rekey(cam, actors, ta, tb, n, look=look)
    return math.degrees(om)


def view_angle(cam, look, p):
    """Degrees between the camera's axis (cam -> look) and the ray cam -> p."""
    u = [look[i] - cam[i] for i in range(3)]
    v = [p[i] - cam[i] for i in range(3)]
    c = sum(u[i] * v[i] for i in range(3)) / (math.sqrt(sum(x * x for x in u)) * math.sqrt(sum(x * x for x in v)))
    return math.degrees(math.acos(max(-1.0, min(1.0, c))))


SPLINE_MOVE_EPS = 0.05    # blocks: a camera position or look target moving more than this over a segment is a move
LONE_MOVE_KEYS = 2        # a lone eased move may be at most this many segments


def check_spline_moves(sid, scene, problems):
    """Every moving camera segment (its end key is not a cut, and the position or the look target ends more than
    SPLINE_MOVE_EPS from where it started) belongs to a run of moving segments that is either all `spline` keys (an
    `in_out` last key may settle the run) or a lone `in_out` move of at most two segments. A run of dense `linear`
    (or eased `in`/`out`) keys turns at each key, which the 60 fps smoothness metrics only catch after the fact.
    One problem per offending run. Returns the number of moving runs checked."""
    cam = scene['camera']
    pts = [(k['pos'], engine_look(scene, cam, k['t'])[0]) for k in cam]
    runs, run = [], []
    for i in range(1, len(cam)):
        moving = (_cam_moves(i, cam) and (math.dist(pts[i - 1][0], pts[i][0]) > SPLINE_MOVE_EPS
                                          or math.dist(pts[i - 1][1], pts[i][1]) > SPLINE_MOVE_EPS))
        if moving:
            run.append(i)
        elif run:
            runs.append(run)
            run = []
    if run:
        runs.append(run)
    for run in runs:
        eases = [cam[i].get('ease', 'in_out') for i in run]
        body = eases[:-1] if len(eases) > 1 and eases[-1] == 'in_out' and 'spline' in eases[:-1] else eases
        if all(e == 'spline' for e in body):
            continue
        if len(eases) <= LONE_MOVE_KEYS and all(e == 'in_out' for e in eases):
            continue
        problems.append('%s: camera keys t %s..%s move %d segments by %s, not a spline run (or a lone in_out move of at'
                        ' most %d keys)' % (sid, cam[run[0] - 1]['t'], cam[run[-1]]['t'], len(run), '/'.join(eases),
                                          LONE_MOVE_KEYS))
    return len(runs)


# ================================================================ hand contacts: the hand, the eye, the arm sway

def arm_tip(root, yaw, scale, side, rot, pivot_px=None):
    """World position of the hand (the arm box's far end) for an arm at absolute rot [x, y, z] degrees. `yaw` may
    be a RiderFrame (a broom rider: the broom's tilt about the rider's root)."""
    px = pivot_px or REST[side]
    tip = (-1.0 if side == 'right_arm' else 1.0, 10.0, 0.0)
    v = HM._turned(tip[0], tip[1], tip[2], math.radians(rot[0]), math.radians(rot[1]), math.radians(rot[2]))
    return model_to_world(root, yaw, scale, px[0] + v[0], px[1] + v[1], px[2] + v[2])


def eye_of(root, yaw, scale, head_pivot=(0, 0, 0), head_rot=(0, 0, 0), fwd=0.0):
    """The eye point (4 px up the head on its front face), moved fwd blocks out along the face: for first-person
    cameras (fwd about 0.25 at 0.75 keeps the camera clear of the head, which renders without culling). `yaw` may be
    a RiderFrame."""
    def at(z):
        v = HM._turned(0.0, -4.0, z, math.radians(head_rot[0]), math.radians(head_rot[1]), math.radians(head_rot[2]))
        return model_to_world(root, yaw, scale, head_pivot[0] + v[0], head_pivot[1] + v[1], head_pivot[2] + v[2])
    e, f = at(-4.0), at(-5.0)
    d = [f[i] - e[i] for i in range(3)]
    n = math.sqrt(sum(c * c for c in d)) or 1.0
    return [e[i] + d[i] / n * fwd for i in range(3)]


# AnimationUtils.bobModelPart (HumanoidModel, every arm pose but the spyglass): zRot += cos(age * 0.09) * 0.05 +
# 0.05 and xRot += sin(age * 0.067) * 0.05 on the right arm, after the held-item pose and the crouch and before the
# scene's limbs. The puppet's age (MemoryActors: tickCount from its seed, then one a client tick) is not tied to the
# scene clock, so the hand-over aims at the sway's middle and asserts the gap at its corners. (A broom rider's arms
# are set by the rider layer, which replaces the sway along with the rest of the vanilla arm pose.)
BOB_Z = (0.0, math.degrees(0.1))
BOB_X = (-math.degrees(0.05), math.degrees(0.05))
BOB_MID = (0.0, sum(BOB_Z) / 2)


# ================================================================ memory brooms (BroomFlight, Vehicle)

BROOM_TYPES = ('hogwartsmagic:memory_broom', 'hogwartsmagic:memory_broom_racing')
ROWBOAT_TYPE = 'hogwartsmagic:memory_rowboat'
BROOM_MAX_RIDERS = 2
SEAT_TOP, SEAT_BACK = 0.06, 0.05
BANK_GAIN, MAX_BANK, BANK_WINDOW, BANK_FADE_SPEED = 1000.0, 45.0, 3.0, 0.05
MAX_CLIMB, CLIMB_FADE_SPEED = 70.0, 0.05
BOB_AMPLITUDE, BOB_HZ, BOB_FADE_SPEED = 0.04, 0.9, 0.1
LEAN_REST, LEAN_SPEED, LEAN_FULL_SPEED = 8.0, 22.0, 0.6
TUCK_LEAN, TUCK_FROM, TUCK_TO = 40.0, 20.0, 40.0
LEG_X, LEG_Y, ARM_X, ARM_Y = -70.0, 10.0, -50.0, -25.0
SHOULDER_DROP, SHOULDER_FORWARD = 3.5, 2.0      # px: the hunched grip lowers the shoulders and brings them forward
HEAD_TURN = 75.0                                # degrees: the most a rider's look turns his head off the broom
RIDER_BLEND = 10.0
FLY_SPEED, LAND_SPEED = 0.025, 0.0125          # Airborne: a ridden broom flies above, has landed under (hysteresis)
# The broom models' bounds in the broom frame at scale 1 (BroomFlight.BOX_SCHOOL / BOX_RACING): min xyz, max xyz.
BROOM_BOX = {'hogwartsmagic:memory_broom': ((-0.156, -0.381, -1.969), (0.156, 0.166, 1.678)),
             'hogwartsmagic:memory_broom_racing': ((-0.344, -0.508, -1.744), (0.344, 0.368, 1.217))}
MASK64 = (1 << 64) - 1


def _smooth(x):
    if x <= 0:
        return 0.0
    if x >= 1:
        return 1.0
    return x * x * (3 - 2 * x)


def _mix64(z):
    z = ((z ^ (z >> 30)) * 0xbf58476d1ce4e5b9) & MASK64
    z = ((z ^ (z >> 27)) * 0x94d049bb133111eb) & MASK64
    return z ^ (z >> 31)


def motion_seed(actor_id):
    """HumanMotion.seed: FNV-1a over the id's UTF-16 units, then a splitmix finaliser (as an unsigned 64-bit int)."""
    h = 0xcbf29ce484222325
    data = actor_id.encode('utf-16-be')
    for i in range(0, len(data), 2):
        h ^= (data[i] << 8) | data[i + 1]
        h = (h * 0x100000001b3) & MASK64
    return _mix64(h)


def is_broom(a):
    return a is not None and a.get('type') in BROOM_TYPES


def actor_by_id(scene, aid):
    found = None
    for x in scene['actors']:
        if x['id'] == aid:
            found = x
    return found


def vehicle_of(scene, rider):
    r = ride_of(rider)
    if r is None:
        return None
    v = actor_by_id(scene, r[0])
    return v if v is not None and (v.get('type') in BROOM_TYPES or v.get('type') == ROWBOAT_TYPE) else None


def path_speed(keys, t):
    """Airborne.speed: blocks per tick, 3D, over [t - 1, t + 1]; 0 with fewer than two keys."""
    if not keys or len(keys) < 2:
        return 0.0
    a, b = track_sample(keys, t - 1), track_sample(keys, t + 1)
    return math.sqrt(sum((b[c] - a[c]) ** 2 for c in range(3))) / 2.0


def broom_bank(keys, t):
    """BroomFlight.bank: degrees, right side down positive (a right turn, yaw increasing, banks right side down)."""
    if not keys or len(keys) < 2:
        return 0.0
    w = BANK_WINDOW
    a, b, c = track_sample(keys, t - w), track_sample(keys, t), track_sample(keys, t + w)
    vx, vz = (c[0] - a[0]) / (2 * w), (c[2] - a[2]) / (2 * w)
    ax, az = (c[0] - 2.0 * b[0] + a[0]) / (w * w), (c[2] - 2.0 * b[2] + a[2]) / (w * w)
    v = math.sqrt(vx * vx + vz * vz)
    cross = vx * az - vz * ax
    side = cross / v if v >= BANK_FADE_SPEED else cross / BANK_FADE_SPEED
    return MAX_BANK * math.tanh(BANK_GAIN * side / MAX_BANK)


def broom_climb(keys, t):
    """BroomFlight.climb: the climb angle as a nose-down pitch, clamped to 70, faded to level when slow."""
    if not keys or len(keys) < 2:
        return 0.0
    a, b = track_sample(keys, t - 1), track_sample(keys, t + 1)
    vx, vy, vz = (b[0] - a[0]) / 2, (b[1] - a[1]) / 2, (b[2] - a[2]) / 2
    h = math.sqrt(vx * vx + vz * vz)
    s = math.sqrt(h * h + vy * vy)
    if s == 0:
        return 0.0
    ang = -math.degrees(math.atan2(vy, h))
    return max(-MAX_CLIMB, min(MAX_CLIMB, ang)) * _smooth(s / CLIMB_FADE_SPEED)


def broom_heading(keys, t):
    """BroomFlight.heading: the Minecraft yaw the path heads, the last (or first) one while it holds."""
    if not keys:
        return 0.0
    a, b = track_sample(keys, t - 2), track_sample(keys, t + 2)
    dx, dz = b[0] - a[0], b[2] - a[2]
    if dx * dx + dz * dz < 1e-6:
        dt = 10
        while dt <= 400:
            c, d = track_sample(keys, t - dt), track_sample(keys, t + dt)
            dx, dz = d[0] - c[0], d[2] - c[2]
            if dx * dx + dz * dz > 1e-6:
                break
            dt += 10
        if dx * dx + dz * dz < 1e-6:
            return 0.0
    return math.degrees(math.atan2(-dx, dz))


def hover_bob(speed, t, seed):
    """BroomFlight.hoverBob: blocks added to a ridden broom's height, 0.04 at 0.9 Hz, gone by 0.1 blocks a tick."""
    fade = 1 - _smooth(speed / BOB_FADE_SPEED)
    if fade <= 0:
        return 0.0
    phase = (seed >> 11) * 2.0 ** -53
    return BOB_AMPLITUDE * fade * math.sin(2 * math.pi * (BOB_HZ * t / 20.0 + phase))


def broom_matrix(yaw, pitch, roll):
    """BroomFlight.frame: M = Yaw * Pitch * Roll row-major; pitch nose down positive, roll right side down positive."""
    y, p, r = math.radians(yaw), math.radians(-pitch), math.radians(roll)
    cy, sy, cp, sp, cr, sr = math.cos(y), math.sin(y), math.cos(p), math.sin(p), math.cos(r), math.sin(r)
    return _mat_mul(_mat_mul([cy, 0, -sy, 0, 1, 0, sy, 0, cy], [1, 0, 0, 0, cp, sp, 0, -sp, cp]),
                    [cr, -sr, 0, sr, cr, 0, 0, 0, 1])


def _mat_mul(a, b):
    return [a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c] for r in range(3) for c in range(3)]


def _mat_vec(m, v):
    return [m[3 * i] * v[0] + m[3 * i + 1] * v[1] + m[3 * i + 2] * v[2] for i in range(3)]


def _transpose(m):
    return [m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]]


def broom_ridden(scene, broom_id, t):
    """BroomFlight.ridden: how much the broom is ridden at t, the largest rider weight (0 with no rider)."""
    w = 0.0
    for x in scene['actors']:
        r = ride_of(x)
        if r and r[0] == broom_id and rides_at(x, t):
            w = max(w, rider_weight(r[1], r[2], t))
    return w


def broom_pose(scene, broom, t):
    """BroomFlight.place: {'pos' (the bob included), 'yaw', 'pitch', 'roll', 'm', 'speed'}."""
    keys = broom.get('path') or []
    p = list(track_sample(keys, t)) if keys else [0.0, 0.0, 0.0]
    speed = path_speed(keys, t)
    look = broom.get('look')
    if look:
        v = own_sample(broom, 'look', t)
        yaw, pitch = v[0], v[1]
        roll = v[2] if any('roll' in k for k in look) else broom_bank(keys, t)
    else:
        yaw, pitch, roll = broom_heading(keys, t), broom_climb(keys, t), broom_bank(keys, t)
    w = broom_ridden(scene, broom['id'], t)
    if w > 0:
        p[1] += hover_bob(speed, t, motion_seed(broom['id'])) * w
    return {'pos': p, 'yaw': yaw, 'pitch': pitch, 'roll': roll, 'm': broom_matrix(yaw, pitch, roll), 'speed': speed}


def broom_frame(scene, broom_id, t):
    """(pos, yaw, pitch, roll) of broom `broom_id` at tick t, as MemoryActors places it."""
    b = broom_pose(scene, actor_by_id(scene, broom_id), t)
    return b['pos'], b['yaw'], b['pitch'], b['roll']


def broom_box(btype, scale=1.0):
    """The broom model's bounds in its own frame, ((min x, y, z), (max x, y, z)), times scale."""
    lo, hi = BROOM_BOX[btype]
    return tuple(c * scale for c in lo), tuple(c * scale for c in hi)


def broom_corners(scene, broom, t):
    """World corners of a broom's model box at tick t."""
    b = broom_pose(scene, broom, t)
    lo, hi = broom_box(broom['type'], broom.get('scale', 1.0))
    out = []
    for x in (lo[0], hi[0]):
        for y in (lo[1], hi[1]):
            for z in (lo[2], hi[2]):
                v = _mat_vec(b['m'], [x, y, z])
                out.append([b['pos'][i] + v[i] for i in range(3)])
    return out


def broom_lowest(scene, broom_id, t):
    """BroomFlight.lowest: the lowest world y of the broom's model box at tick t."""
    return min(c[1] for c in broom_corners(scene, actor_by_id(scene, broom_id), t))


def rider_seat(rider_scale):
    """BroomFlight.seat: the rest offset in the broom frame, hips over the seat point, thighs on the seat top."""
    return [0.0, HM.seated_root_y(SEAT_TOP, rider_scale), -SEAT_BACK]


def rider_lean(speed, pitch):
    """BroomFlight.lean: 8 + 22 x clamp(speed/0.6), eased to 40 in a dive between pitch 20 and 40."""
    base = LEAN_REST + LEAN_SPEED * max(0.0, min(1.0, speed / LEAN_FULL_SPEED))
    return base + (TUCK_LEAN - base) * _smooth((pitch - TUCK_FROM) / (TUCK_TO - TUCK_FROM))


def rider_pose(speed, pitch):
    """BroomFlight.riderPose: [head, body, right arm, left arm, right leg, left leg] rotations, degrees."""
    l = rider_lean(speed, pitch)
    return [[-l, 0.0, 0.0], [l, 0.0, 0.0], [ARM_X, ARM_Y, 0.0], [ARM_X, -ARM_Y, 0.0], [LEG_X, LEG_Y, 0.0],
            [LEG_X, -LEG_Y, 0.0]]


def rider_weight(frm, until, t):
    """BroomFlight.riderWeight: the rider layer eased in over 10 ticks from `from` and out over 10 before `until`."""
    i = 1.0 if frm == -INF else _smooth((t - frm) / RIDER_BLEND)
    o = 1.0 if until == INF else _smooth((until - t) / RIDER_BLEND)
    return i * o


def broom_rider_at(scene, a, t):
    """The broom `a` rides at tick t, or None."""
    if not rides_at(a, t):
        return None
    v = vehicle_of(scene, a)
    return v if is_broom(v) else None


def rider_root(scene, rider, t):
    """(root, M): a broom rider's root at tick t (broom + M x its offset in the broom frame, the seat when the window
    has no path keys) and the broom's M; for anyone not on a broom then, its own path in world space and None."""
    v = broom_rider_at(scene, rider, t)
    if v is None:
        p = own_sample(rider, 'path', t)
        return (list(p) if p is not None else [0.0, 0.0, 0.0]), None
    b = broom_pose(scene, v, t)
    off = own_sample(rider, 'path', t)
    if off is None:
        off = rider_seat(rider.get('scale', 1.0))
    s = v.get('scale', 1.0)
    w = _mat_vec(b['m'], [off[0] * s, off[1] * s, off[2] * s])
    return [b['pos'][i] + w[i] for i in range(3)], b['m']


def rider_frame(scene, rider, t):
    """(root, RiderFrame (the body on the broom's yaw), broom pose, look pitch, head turn) of a broom rider at tick
    t, or None when not on a broom then."""
    v = broom_rider_at(scene, rider, t)
    if v is None:
        return None
    root, m = rider_root(scene, rider, t)
    b = broom_pose(scene, v, t)
    lk = own_sample(rider, 'look', t)
    ly, lp = (lk[0], lk[1]) if lk is not None else (0.0, 0.0)
    tilt = _mat_mul(m, _transpose(broom_matrix(b['yaw'], 0.0, 0.0)))
    return root, RiderFrame(b['yaw'], tilt), b, lp, rider_head_turn(ly)


def rider_head_turn(look_yaw):
    """BroomFlight.headTurn: a broom rider's body faces along the broom; his look yaw (relative to the broom) turns
    only his head, wrapped to [-180, 180) and clamped to +-75."""
    d = (look_yaw + 180.0) % 360.0 - 180.0
    return max(-HEAD_TURN, min(HEAD_TURN, d))


def rider_eye(scene, rider, t):
    """MemoryActors.actorEye for a broom rider: root + Q x (0, eye height, 0)."""
    root, fr = rider_frame(scene, rider, t)[:2]
    v = _mat_vec(fr.tilt, [0.0, actor_eye_height(rider, t), 0.0])
    return [root[i] + v[i] for i in range(3)]


def _broom_rider_parts(scene, a, t):
    """posed_parts for a rider on a broom: the vanilla rest (and the held-item raise), the look pitch on the head,
    then the rider layer at its window weight (BroomFlight.riderLayer: body, arms and legs toward rider_pose, the
    head lifted, the lean turning the torso about the hips, the shoulders hunched down and forward for the grip),
    the pose cue, the limbs; all in the broom's frame. The body faces the broom; the look yaw turns the head only."""
    scale = a.get('scale', 1.0)
    root, fr, b, pitch, turn = rider_frame(scene, a, t)
    r = ride_of(a)
    w = rider_weight(r[1], r[2], t)
    pose_r = rider_pose(b['speed'], b['pitch'])
    lean = pose_r[1][0]
    ang = math.radians(lean)
    neck = HM._turned(0.0, -12.0, 0.0, ang, 0.0, 0.0)
    held = EquipTimeline_item(a, 'mainhand', t)
    pose = _last_at(a.get('poses', []), t)
    pz = scene.get('poses', {}).get(pose['name'], {}) if pose else {}
    out = []
    for i, part in enumerate(PART_BOX):
        piv = list(REST[part])
        rot = [0.0, 0.0, 0.0]
        if part == 'right_arm' and held:
            rot[0] += ITEM_ARM_X
        if part == 'head':
            rot[0] += pitch
            rot[1] += turn
            rot[0] += pose_r[0][0] * w
        else:
            tgt = [0.0, 0.0, 0.0] if part == 'body' else pose_r[i]
            rot = [rot[c] + (tgt[c] - rot[c]) * w for c in range(3)]
        if part == 'body':
            rot[0] += lean * w
        if part in ('head', 'body'):
            piv = [piv[0] + neck[0] * w, piv[1] + (neck[1] + 12.0) * w, piv[2] + neck[2] * w]
        elif part in ('right_arm', 'left_arm'):
            x = -5.0 if part == 'right_arm' else 5.0
            sh = HM._turned(x, -10.0 + SHOULDER_DROP, -SHOULDER_FORWARD, ang, 0.0, 0.0)
            piv = [piv[0] + (sh[0] - x) * w, piv[1] + (sh[1] + 10.0) * w, piv[2] + sh[2] * w]
        if part in pz:
            rot = list(pz[part])
        piv, rot = emitted_arm(a, part, t, piv, rot)
        out.append((part, root, fr, scale, piv, rot))
    return out


# ================================================================ the parity cases (BroomFlightTest.pythonMirrorAgrees)

BROOM_CASES = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'broom_cases.json')


def _case_scenes():
    def rider(extra=None, scale=0.75):
        a = {'id': 'r', 'type': 'player', 'scale': scale, 'ride': 'b'}
        a.update(extra or {})
        return a

    def broom(path, look=None, btype='hogwartsmagic:memory_broom'):
        b = {'id': 'b', 'type': btype, 'path': path}
        if look:
            b['look'] = look
        return b

    circle = []
    for i in range(13):
        th = i * math.pi / 6
        circle.append({'t': i * 10, 'pos': [round(10 * math.sin(th), 4), 6.0, round(-10 * math.cos(th) + 10, 4)],
                       'ease': 'spline'})
    circle[0].pop('ease')
    climb = [{'t': 0, 'pos': [0.0, 2.0, 0.0]}, {'t': 20, 'pos': [0.0, 2.5, 6.0], 'ease': 'spline'},
             {'t': 40, 'pos': [0.0, 6.0, 14.0], 'ease': 'spline'}, {'t': 60, 'pos': [0.0, 12.0, 20.0], 'ease': 'spline'},
             {'t': 80, 'pos': [0.0, 14.0, 26.0], 'ease': 'in_out'}]
    dive = [{'t': 0, 'pos': [0.0, 30.0, 0.0]}, {'t': 20, 'pos': [0.0, 30.0, 10.0], 'ease': 'spline'},
            {'t': 40, 'pos': [0.0, 18.0, 18.0], 'ease': 'spline'}, {'t': 60, 'pos': [0.0, 5.0, 24.0], 'ease': 'spline'},
            {'t': 80, 'pos': [0.0, 2.0, 34.0], 'ease': 'spline'}, {'t': 100, 'pos': [0.0, 3.0, 46.0], 'ease': 'spline'}]
    seat = rider_seat(0.75)
    mount_root = [2.0, 0.0, 0.0]
    mount_broom = [mount_root[i] - seat[i] for i in range(3)]        # yaw, pitch and roll 0: M is the identity
    return {
        'straight': {'length': 100, 'actors': [
            broom([{'t': 0, 'pos': [0.0, 5.0, 0.0]}, {'t': 60, 'pos': [30.0, 5.0, 0.0], 'ease': 'linear'}]),
            rider()]},
        'turning': {'length': 140, 'actors': [broom(circle), rider({'look': [{'t': 0, 'yaw': 10, 'pitch': 5}]})]},
        'climbing': {'length': 100, 'actors': [broom(climb, btype='hogwartsmagic:memory_broom_racing'),
                                               rider(scale=0.85)]},
        'diving': {'length': 120, 'actors': [broom(dive), rider()]},
        'hovering': {'length': 100, 'actors': [
            broom([{'t': 0, 'pos': [1.0, 3.0, -2.0]}], [{'t': 0, 'yaw': 30}, {'t': 60, 'yaw': 75, 'ease': 'spline'}]),
            rider({'path': [{'t': 0, 'pos': [0.0, -0.3, 0.1]}]}, scale=1.0)]},
        'mount': {'length': 200, 'actors': [
            broom([{'t': 0, 'pos': mount_broom}, {'t': 40, 'pos': mount_broom},
                   {'t': 70, 'pos': [mount_broom[0], mount_broom[1] + 2.0, mount_broom[2] + 3.0], 'ease': 'spline'},
                   {'t': 110, 'pos': [mount_broom[0], mount_broom[1] + 3.0, mount_broom[2] + 12.0], 'ease': 'spline'},
                   {'t': 140, 'pos': [mount_broom[0], mount_broom[1], mount_broom[2] + 16.0], 'ease': 'in_out'}],
                  [{'t': 0, 'yaw': 0, 'pitch': 0, 'roll': 0}, {'t': 40, 'yaw': 0, 'pitch': 0, 'roll': 0},
                   {'t': 90, 'yaw': 10, 'pitch': -15, 'roll': 12, 'ease': 'spline'},
                   {'t': 140, 'yaw': 0, 'pitch': 0, 'roll': 0, 'ease': 'in_out'}]),
            {'id': 'r', 'type': 'player', 'scale': 0.75, 'ride': {'on': 'b', 'from': 40, 'until': 150},
             'path': [{'t': 0, 'pos': [-3.0, 0.0, 0.0]}, {'t': 30, 'pos': mount_root, 'ease': 'in_out'},
                      {'t': 39.999, 'pos': mount_root}, {'t': 40, 'pos': seat},
                      {'t': 149.999, 'pos': seat}, {'t': 150, 'pos': [2.0, 0.0, 16.0]},
                      {'t': 170, 'pos': [4.0, 0.0, 19.0], 'ease': 'in_out'}],
             'look': [{'t': 0, 'yaw': -90}, {'t': 39.999, 'yaw': 0}, {'t': 40, 'yaw': 0},
                      {'t': 149.999, 'yaw': 0}, {'t': 150, 'yaw': 0}]}]},
    }


CASE_TIMES = {'straight': [0, 10.25, 30, 59.5, 61, 80, 99.75], 'turning': [5, 15.5, 33, 47.25, 60, 88.75, 121],
              'climbing': [1, 12.5, 25, 38.75, 50, 66, 79], 'diving': [10, 24.5, 33.25, 46, 55.5, 70, 88.75],
              'hovering': [0, 17.5, 40.25, 62, 91, 99.5],
              'mount': [20, 39.5, 45.25, 75.5, 120, 155]}


def broom_cases():
    """Every case: the broom's pose, its bank, and the rider's root, M (None off the broom), pose and weight."""
    scenes = _case_scenes()
    cases = []
    for name, times in CASE_TIMES.items():
        sc = scenes[name]
        b = actor_by_id(sc, 'b')
        r = actor_by_id(sc, 'r')
        for t in times:
            bp = broom_pose(sc, b, t)
            root, m = rider_root(sc, r, t)
            c = {'scene': name, 't': t, 'pos': bp['pos'], 'yaw': bp['yaw'], 'pitch': bp['pitch'], 'roll': bp['roll'],
                 'speed': bp['speed'], 'bank': broom_bank(b['path'], t), 'climb': broom_climb(b['path'], t),
                 'heading': broom_heading(b['path'], t), 'rider_root': root, 'rider_m': m,
                 'rider_pose': rider_pose(bp['speed'], bp['pitch']),
                 'rider_weight': rider_weight(*ride_of(r)[1:], t) if rides_at(r, t) else None,
                 'seed_bob': hover_bob(0.0, t, motion_seed('b'))}
            cases.append(c)
    return {'constants': {'SEAT_TOP': SEAT_TOP, 'SEAT_BACK': SEAT_BACK, 'BANK_GAIN': BANK_GAIN, 'MAX_BANK': MAX_BANK,
                          'BANK_WINDOW': BANK_WINDOW, 'BOB_AMPLITUDE': BOB_AMPLITUDE, 'BOB_HZ': BOB_HZ,
                          'RIDER_BLEND': RIDER_BLEND, 'BOX_SCHOOL': BROOM_BOX['hogwartsmagic:memory_broom'],
                          'BOX_RACING': BROOM_BOX['hogwartsmagic:memory_broom_racing']},
            'scenes': scenes, 'cases': cases}


def write_broom_cases():
    data = broom_cases()
    with open(BROOM_CASES, 'w', newline='\n', encoding='utf-8') as f:
        f.write(json.dumps(data, indent=1, sort_keys=True) + '\n')
    print('wrote', BROOM_CASES, len(data['cases']), 'cases')


if __name__ == '__main__':
    import sys
    if '--write-broom-cases' in sys.argv:
        write_broom_cases()


# ================================================================ the rider's grip (BroomFlightTest.handsOnHandle)

HANDLE_RADIUS = 1.0 / 16.0        # the training handle is 2 px across


LEFT_HAND_BOX = ((-1.25, 3.25), (6.0, 10.25), (-2.25, 2.25))      # the left arm's last 4 px
GRIP_GAP_MAX = 0.06               # blocks: a rider's fist box to the handle's surface, at most


def rider_hand_gaps(scale, speed, pitch):
    """A rider at the rest seat of a broom flying straight at `speed` blocks a tick with nose-down `pitch`, posed by
    posed_parts (the rider layer at full weight): per hand, (the gap from its fist box to the handle's surface, the
    clearance of its tip, arm_tip, from the thigh box on its side)."""
    sc = {'poses': {}, 'actors': [
        {'id': 'b', 'type': 'hogwartsmagic:memory_broom',
         'path': [{'t': 0, 'pos': [0.0, 50.0, 0.0]}, {'t': 200, 'pos': [0.0, 50.0, speed * 200], 'ease': 'linear'}],
         'look': [{'t': 0, 'yaw': 0, 'pitch': pitch, 'roll': 0}]},
        {'id': 'r', 'type': 'player', 'scale': scale, 'ride': 'b'}]}
    t = 100.0
    b = broom_pose(sc, sc['actors'][0], t)
    parts = {q[0]: q for q in posed_parts(sc, sc['actors'][1], t)}
    axis = [[b['pos'][i] + _mat_vec(b['m'], [0.0, 0.0, k * 0.02])[i] for i in range(3)] for k in range(-40, 41)]
    out = []
    for side, leg, box in (('right_arm', 'right_leg', HAND_BOX), ('left_arm', 'left_leg', LEFT_HAND_BOX)):
        _, root, fr, s, piv, rot = parts[side]
        gap = min(box_distance(root, fr, s, piv, rot, box, q) for q in axis) - HANDLE_RADIUS
        tip = arm_tip(root, fr, s, side, rot, piv)
        _, lr, lf, ls, lp, lro = parts[leg]
        out.append((gap, box_distance(lr, lf, ls, lp, lro, PART_BOX[leg], tip)))
    return out


def check_rider_grip():
    """The hands on the handle at every rider scale, speed and through the dive tuck. Returns (worst gap, worst
    thigh clearance)."""
    worst = (0.0, 9.0)
    for sc in (0.75, 0.85, 1.0):
        for sp in (0.0, 0.3, 0.6):
            for pi in (0.0, 20.0, 30.0, 45.0, 60.0):
                for gap, clear in rider_hand_gaps(sc, sp, pi):
                    assert gap <= GRIP_GAP_MAX, ('hand off the handle', sc, sp, pi, gap)
                    assert clear > 0.0, ('hand in the thigh', sc, sp, pi, clear)
                    worst = (max(worst[0], gap), min(worst[1], clear))
    return worst
