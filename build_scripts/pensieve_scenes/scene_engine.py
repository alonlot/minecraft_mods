"""The Pensieve scene generators' shared mirrors of the game engine.

Every generator that emits a memory checks it against the engine's own maths, never a private copy:
  - the camera (CameraTrack.sample, its spline and its eased blends): engine_blend, engine_spline, engine_cam,
    engine_look, camera_samples, camera_frames, camera_motion, and the checks on them (check_camera_smoothness,
    check_camera_clearance, check_spline_moves); rekey and turn_between re-key a shot through them;
  - the player model as ActorPoser and HumanoidModel pose it: actor_eye_height (EyeEase), posed_parts, the held-item
    raise (item_raise_steps), emitted_arm, life_arm_corners, part_corners, inside_part, box_distance, box_surface;
  - the world's block shapes: shape_hit, in_air.

Imports only the standard library, scene_common and human_motion (no generator), so any generator can import it.
"""
import math

from scene_common import FREE
import human_motion as HM


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
    """An emitted path's position at tick t (the engine's rule: hold the ends, ease per segment)."""
    if t <= path[0]['t']:
        return path[0]['pos']
    for a, b in zip(path, path[1:]):
        if t <= b['t']:
            if b['t'] == a['t']:
                return b['pos']
            f = ease_f(b.get('ease', 'in_out'), (t - a['t']) / (b['t'] - a['t']))
            return [a['pos'][k] + (b['pos'][k] - a['pos'][k]) * f for k in range(3)]
    return path[-1]['pos']


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
                out.append(HM.model_to_world(root, yaw, scale, pivot[0] + v[0], pivot[1] + v[1], pivot[2] + v[2]))
    return out


def inside_part(root, yaw, scale, pivot, rot, box, p, margin_px=0.0):
    """Whether world point p lies inside the part's box grown by margin_px."""
    o = HM.model_to_world(root, yaw, scale, *pivot)
    loc = []
    for e in ((1, 0, 0), (0, 1, 0), (0, 0, 1)):
        v = HM._turned(*e, *[math.radians(c) for c in rot])
        w = HM.model_to_world(root, yaw, scale, pivot[0] + v[0], pivot[1] + v[1], pivot[2] + v[2])
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


EASE_SLOPES = {'linear': (1, 1), 'in': (0, 3), 'out': (3, 0), 'in_out': (0, 0), 'spline': (1, 1)}   # Ease start/end


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
    """A player actor's six parts at tick t from its emitted JSON: (part, root, yaw, scale, pivot px, rot deg).
    Vanilla rest (or the crouch from a `sneak` until the next `stand`, with the held-item arm raise), the pose cue in force (absolute),
    the look pitch on the head, then the limbs (add or set). Life and clips are left out: a few px at most."""
    scale = a.get('scale', 1.0)
    root = sample_path(a['path'], t)
    yaw, pitch = _yaw_pitch(a, t)
    if a.get('ride'):
        boat = next(x for x in scene['actors'] if x['id'] == a['ride'])
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
    o = HM.model_to_world(root, yaw, scale, *pivot)
    loc = []
    axes = []
    for e in ((1, 0, 0), (0, 1, 0), (0, 0, 1)):
        v = HM._turned(*e, *[math.radians(c) for c in rot])
        w = HM.model_to_world(root, yaw, scale, pivot[0] + v[0], pivot[1] + v[1], pivot[2] + v[2])
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
            r = sample_path(a_['path'], t)
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
                    out.append(HM.model_to_world(root, yaw, scale, pivot[0] + v[0], pivot[1] + v[1],
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
