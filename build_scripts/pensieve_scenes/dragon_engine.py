"""The memory Horntail's mirror of the game engine, for the scene generators (imports scene_engine, edits nothing in it).

The dragon (`hogwartsmagic:memory_horntail`) as the game draws her:
  - her cues (HorntailCues): horntail_cue, horntail_blend;
  - her flight (DragonFlight): horntail_frame, horntail_pose, dragon_bank, dragon_climb, climb_rate;
  - her wingbeats (DragonFlight): beat_rate, wing_amp, wing_phase, stroke_angle, bone_beat, downstroke_ticks;
  - her model as HungarianHorntailModel.setupAnim poses it (with MemoryHorntailModel's cross-fade): model_pose,
    part_matrices; and from it horntail_parts (conservative capsules for every part, the wings at their swept extent
    for the current amplitude) and horntail_snout (the mouth and the head's direction, as MemoryHorntailRenderer
    stores it for the fire).

`python -I dragon_engine.py --write-cases` rewrites horntail_cases.json (DragonFlightTest.pythonMirrorAgrees);
`python -I dragon_engine.py` runs the self-checks.

The model mirror follows setupAnim exactly with one simplification: the ground gait is not mirrored (a knuckle-walking
stride moves the legs and wing wrists up to about 0.3 blocks; the capsules of a walking dragon are the standing pose's).
Her head look is the actor's `limbs.head_look` rot channel (v: [pitch, yaw, 0], degrees, clamped: DragonFlight.headLook),
her neck counters her whole-body roll in level flight (DragonFlight.neckRoll), and the age (breathing, tail drift) is the
scene time, as MemoryActors sets it.
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import scene_engine as E  # noqa: E402

HORNTAIL_TYPE = 'hogwartsmagic:memory_horntail'

# ================================================================ DragonFlight

RENDER_SCALE = 1.15
PIVOT_Y = (24 - 9) / 16.0 * RENDER_SCALE
MODEL_LIFT = 1.501                     # LivingEntityRenderer's translate(0, -1.501, 0)
BANK_GAIN, MAX_BANK, BANK_FADE_SPEED, BANK_WINDOW = 1000.0, 50.0, 0.1, 3.0
MAX_CLIMB, CLIMB_FADE_SPEED = 45.0, 0.05
HOVER_RATE, CRUISE_RATE, HOVER_SPEED, CRUISE_SPEED = 0.24, 0.14, 0.1, 0.8
CLIMB_BOOST, CLIMB_FULL = 0.35, 0.3
GLIDE_SPEED, GLIDE_SINK, GLIDE_RAMP = 0.9, 0.15, 0.1
HOVER_AMP, CRUISE_AMP, GLIDE_AMP = 0.70, 0.60, 0.15
TAKEOFF_GAIN = 0.75                    # the memory takeoff's own beat, scaled
MIDPLANE_CLEARANCE = 0.4               # blocks (scale 1) a fingertip keeps from her midplane at any stroke phase
DOWNSTROKE_SHARE, FOREARM_LAG, FINGER_LAG = 0.42, 0.35, 0.70
MID_DOWNSTROKE = math.pi * DOWNSTROKE_SHARE - math.pi / 2
HEAD_YAW_LIMIT, HEAD_PITCH_LIMIT = 60.0, 40.0
HEAD_LOOK = 'head_look'

smooth = E._smooth


def path_of(actor):
    return actor.get('path') or []


def dragon_bank(keys, t):
    """DragonFlight.bank: BroomFlight.bank with gain 1000, limit 50, fade below 0.1 blocks/tick."""
    if not keys or len(keys) < 2:
        return 0.0
    w = BANK_WINDOW
    a, b, c = E.track_sample(keys, t - w), E.track_sample(keys, t), E.track_sample(keys, t + w)
    vx, vz = (c[0] - a[0]) / (2 * w), (c[2] - a[2]) / (2 * w)
    ax, az = (c[0] - 2.0 * b[0] + a[0]) / (w * w), (c[2] - 2.0 * b[2] + a[2]) / (w * w)
    v = math.sqrt(vx * vx + vz * vz)
    cross = vx * az - vz * ax
    side = cross / v if v >= BANK_FADE_SPEED else cross / BANK_FADE_SPEED
    return MAX_BANK * math.tanh(BANK_GAIN * side / MAX_BANK)


def dragon_climb(keys, t):
    """DragonFlight.climbPitch: the climb angle as a nose-down pitch, clamped to 45, faded to level when slow."""
    if not keys or len(keys) < 2:
        return 0.0
    a, b = E.track_sample(keys, t - 1), E.track_sample(keys, t + 1)
    vx, vy, vz = (b[0] - a[0]) / 2, (b[1] - a[1]) / 2, (b[2] - a[2]) / 2
    h = math.sqrt(vx * vx + vz * vz)
    s = math.sqrt(h * h + vy * vy)
    if s == 0:
        return 0.0
    ang = -math.degrees(math.atan2(vy, h))
    return max(-MAX_CLIMB, min(MAX_CLIMB, ang)) * smooth(s / CLIMB_FADE_SPEED)


def climb_rate(keys, t):
    """DragonFlight.climbRate: vertical speed, up positive, blocks/tick over [t - 1, t + 1]."""
    if not keys or len(keys) < 2:
        return 0.0
    return (E.track_sample(keys, t + 1)[1] - E.track_sample(keys, t - 1)[1]) / 2.0


def horntail_pose(actor, t):
    """DragonFlight.place: {'pos', 'yaw', 'pitch', 'roll', 'm', 'speed', 'climb'}. Without a look her yaw is the held
    heading (scene_engine.broom_heading_held): through a hover she keeps the way she came."""
    keys = path_of(actor)
    p = list(E.track_sample(keys, t)) if keys else [0.0, 0.0, 0.0]
    look = actor.get('look')
    if look:
        v = E.own_sample(actor, 'look', t)
        yaw, pitch = v[0], v[1]
        roll = v[2] if any('roll' in k for k in look) else dragon_bank(keys, t)
    else:
        yaw, pitch, roll = E.broom_heading_held(keys, t), dragon_climb(keys, t), dragon_bank(keys, t)
    return {'pos': p, 'yaw': yaw, 'pitch': pitch, 'roll': roll, 'm': E.broom_matrix(yaw, pitch, roll),
            'speed': E.path_speed(keys, t), 'climb': climb_rate(keys, t)}


def horntail_frame(scene, aid, t):
    """(pos, yaw, pitch, roll) of Horntail `aid` at tick t, as MemoryActors places her."""
    p = horntail_pose(E.actor_by_id(scene, aid), t)
    return p['pos'], p['yaw'], p['pitch'], p['roll']


def to_world(pose, scale, local):
    """DragonFlight.toWorld: a point in her local frame (blocks, scale 1, from the root) to scene space."""
    rel = [local[0], local[1] - PIVOT_Y, local[2]]
    w = E._mat_vec(pose['m'], rel)
    return [pose['pos'][0] + scale * w[0], pose['pos'][1] + scale * (w[1] + PIVOT_Y), pose['pos'][2] + scale * w[2]]


# ---------------------------------------------------------------- wingbeats

def glide(speed, climb):
    return (smooth((speed - (GLIDE_SPEED - GLIDE_RAMP)) / GLIDE_RAMP)
            * smooth((-climb - (GLIDE_SINK - GLIDE_RAMP)) / GLIDE_RAMP))


def _cruise(speed):
    return smooth((speed - HOVER_SPEED) / (CRUISE_SPEED - HOVER_SPEED))


def beat_rate(speed, climb):
    """DragonFlight.beatRate: rad/tick, 0.24 hovering, 0.14 cruising, up to x1.35 climbing, 0 gliding."""
    rate = HOVER_RATE + (CRUISE_RATE - HOVER_RATE) * _cruise(speed)
    rate *= 1 + CLIMB_BOOST * smooth(climb / CLIMB_FULL)
    return rate * (1 - glide(speed, climb))


def wing_amp(speed, climb):
    """DragonFlight.wingAmp: 0.70 hovering or climbing, 0.60 cruising, 0.15 gliding."""
    amp = HOVER_AMP + (CRUISE_AMP - HOVER_AMP) * _cruise(speed)
    amp += (HOVER_AMP - amp) * smooth(climb / CLIMB_FULL)
    return amp + (GLIDE_AMP - amp) * glide(speed, climb)


def rate_at(keys, k):
    return beat_rate(E.path_speed(keys, k), climb_rate(keys, k))


_PHASE = {}


def _sums(keys, n):
    key = id(keys)
    got = _PHASE.get(key)
    if got is None or got[0] is not keys:
        got = (keys, [0.0])
        _PHASE[key] = got
    s = got[1]
    while len(s) <= n:
        k = len(s) - 1
        s.append(s[-1] + rate_at(keys, k))
    return s


def wing_phase(actor, t):
    """DragonFlight.wingPhase: the rate summed over whole ticks from 0, linear within a tick (cached per path)."""
    keys = path_of(actor)
    if t <= 0:
        return t * rate_at(keys, 0)
    n = int(math.floor(t))
    return _sums(keys, n)[n] + (t - n) * rate_at(keys, n)


def stroke_angle(phase):
    """DragonFlight.strokeAngle: the phase warped so DOWNSTROKE_SHARE of a beat goes down; the top stays at -pi/2."""
    tau, d = 2 * math.pi, DOWNSTROKE_SHARE
    shifted = phase + math.pi / 2
    u = shifted - tau * math.floor(shifted / tau)
    psi = u / (2 * d) if u < tau * d else math.pi + (u - tau * d) / (2 * (1 - d))
    return psi - math.pi / 2 + (shifted - u)


def bone_beat(phase, bone):
    """DragonFlight.boneBeat: 0 humerus, 1 forearm (lags FOREARM_LAG), 2 finger (lags FINGER_LAG)."""
    lag = FOREARM_LAG if bone == 1 else FINGER_LAG if bone == 2 else 0.0
    return math.sin(stroke_angle(phase - lag))


def bone_beats(phase):
    return [bone_beat(phase, b) for b in range(3)]


def raw_downstrokes(keys, t0, t1):
    """DragonFlight.downstrokes(path, t0, t1): the ticks the humerus is mid-downstroke (stroke angle 0 mod 2 pi, the
    beat rising through 0, where the whump peaks), nearest whole tick."""
    out = []
    a_actor = {'path': keys}
    for n in range(max(0, t0 - 1), t1 + 1):
        a = wing_phase(a_actor, n)
        rate = rate_at(keys, n)
        if rate <= 0:
            continue
        b = a + rate
        m = math.ceil((a - MID_DOWNSTROKE) / (2 * math.pi))
        target = MID_DOWNSTROKE + 2 * math.pi * m
        while target < b:
            if target >= a:
                tick = int(math.floor(n + (target - a) / rate + 0.5))
                if t0 <= tick <= t1 and (not out or out[-1] != tick):
                    out.append(tick)
            target += 2 * math.pi
    return out


BEATING = ('FLY', 'TAKEOFF', 'AIR_BREATH', 'BITE')


def downstroke_ticks(actor, t0, t1):
    """DragonFlight.downstrokes(path, anims, t0, t1): the downstrokes while she flies in a state that shows the beat."""
    out = []
    for tick in raw_downstrokes(path_of(actor), t0, t1):
        s = horntail_cue(actor, tick)
        if s['flying'] and s['name'] in BEATING:
            out.append(tick)
    return out


# ================================================================ HorntailCues

DURATIONS = {'TAKEOFF': 30, 'LAND': 22, 'ROAR': 54, 'BITE': 20, 'TAIL_WHIP': 30, 'BREATH_WINDUP': 22, 'BREATH': 46,
             'WING_GUST': 26, 'DIVE': 44, 'STAGGER': 18, 'AIR_BREATH': 52}
HORNTAIL_ANIMS = ('IDLE', 'FLY', 'TAKEOFF', 'LAND', 'ROAR', 'BITE', 'TAIL_WHIP', 'BREATH_WINDUP', 'BREATH',
                  'WING_GUST', 'DIVE', 'STAGGER', 'AIR_BREATH')
AIR_BREATH_WINDUP = 12
DEFAULT_BLEND = 6.0


def state_name(cue):
    n = cue.upper()
    return 'NONE' if n == 'IDLE' else n


def _timeline(actor):
    cues = sorted(actor.get('anims') or [], key=lambda c: c['t'])
    segs = [('NONE', -E.INF, False, 0.0)]
    flying = False
    for i, c in enumerate(cues):
        name = state_name(c['name'])
        nxt = cues[i + 1]['t'] if i + 1 < len(cues) else E.INF
        if name in ('TAKEOFF', 'FLY'):
            flying = True
        if name == 'NONE':
            flying = False
        landing = name == 'LAND'
        segs.append((name, c['t'], landing or flying, float(c.get('blend', 0.0))))
        d = DURATIONS.get(name, -1)
        if landing:
            flying = False
        if d >= 0 and c['t'] + d < nxt:
            segs.append(('FLY' if flying else 'NONE', c['t'] + d, flying, 0.0))
    return segs


def _seg_at(segs, t):
    i = 0
    while i + 1 < len(segs) and segs[i + 1][1] <= t:
        i += 1
    return i


def _state(seg, t):
    time = 0.0 if seg[1] == -E.INF else t - seg[1]
    return {'name': seg[0], 'tick': int(math.floor(time)), 'flying': seg[2], 'time': time}


def horntail_cue(actor, t):
    """HorntailCues.at: {'name', 'tick', 'flying', 'time'} at tick t."""
    segs = _timeline(actor)
    return _state(segs[_seg_at(segs, t)], t)


MAX_NESTED_BLENDS = 3


def _blend_of(segs, i, t, depth):
    now = _state(segs[i], t)
    if i == 0:
        return {'now': now, 'before': None, 'weight': 1.0, 'older': None}
    into = t - segs[i][1]
    ticks = segs[i][3] if segs[i][3] > 0 else DEFAULT_BLEND
    if into >= ticks:
        return {'now': now, 'before': None, 'weight': 1.0, 'older': None}
    prev = _blend_of(segs, i - 1, t, depth - 1) if depth > 0 else None
    return {'now': now, 'before': _state(segs[i - 1], t), 'weight': smooth(into / ticks),
            'older': prev if prev is not None and prev['before'] is not None else None}


def horntail_blend_tree(actor, t):
    """HorntailCues.blendAt in full: {'now', 'before', 'weight', 'older'}; `older` is the fade `before` was still in
    when `now` began (nested up to MAX_NESTED_BLENDS deep), else None."""
    segs = _timeline(actor)
    return _blend_of(segs, _seg_at(segs, t), t, MAX_NESTED_BLENDS)


def blend_chain(b):
    """A blend as [[now, weight or None], ...] down its older fades: what the parity cases compare."""
    out = []
    while b is not None:
        out.append([b['now']['name'], b['before']['name'] if b['before'] else None, b['weight']])
        b = b['older']
    return out


def horntail_blend(actor, t):
    """HorntailCues.blendAt, the top fade only: (now, before or None, weight of now)."""
    b = horntail_blend_tree(actor, t)
    return b['now'], b['before'], b['weight']


# ================================================================ HungarianHorntailModel

GROUND_Y, BODY_Y = 24.0, 9.0
STAND_HUMERUS_Z, STAND_FOREARM_Z, STAND_FINGER_Z, STAND_FINGER_Y = 0.90, 1.20, -3.45, 0.35
STAND_THIGH_X, STAND_SHANK_X, STAND_FOOT_X = -0.55, 1.17, -0.62
STAND_NECK1_X, STAND_NECK2_X, STAND_HEAD_X = -0.50, 0.55, 0.30
TAIL_WIND_END, TAIL_SWEEP_END, TAIL_TRAIL = 0.28, 0.72, 1.15
SNOUT = (0.0, 3.0, -19.5)               # HungarianHorntailModel.SNOUT_Y / SNOUT_Z, head px

# part: (parent, offset px)
TREE = {
    'body': (None, (0.0, BODY_Y, 0.0)),
    'neck1': ('body', (0.0, -8.0, -14.0)), 'neck2': ('neck1', (0.0, 0.0, -13.0)), 'head': ('neck2', (0.0, 0.0, -13.0)),
    'jaw': ('head', (0.0, 3.5, -6.0)),
    'tail1': ('body', (0.0, -3.0, 13.0)), 'tail2': ('tail1', (0.0, 0.0, 13.0)), 'tail3': ('tail2', (0.0, 0.0, 13.0)),
    'tail4': ('tail3', (0.0, 0.0, 13.0)),
}
for _s, _p in ((1.0, 'left_'), (-1.0, 'right_')):
    TREE[_p + 'humerus'] = ('body', (7.0 * _s, -7.0, -6.0))
    TREE[_p + 'forearm'] = (_p + 'humerus', (13.0 * _s, 0.0, 0.0))
    TREE[_p + 'finger'] = (_p + 'forearm', (11.0 * _s, 0.0, 0.0))
    TREE[_p + 'thigh'] = ('body', (7.0 * _s, -3.0, 9.0))
    TREE[_p + 'shank'] = (_p + 'thigh', (0.0, 9.0, 0.0))
    TREE[_p + 'foot'] = (_p + 'shank', (0.0, 9.0, 0.0))
PARTS = list(TREE)


def _rest():
    return {p: [0.0, 0.0, 0.0] for p in PARTS}


def _set_wings(r, hum_z, hum_y, hum_x, fore_z, fore_y, fing_z, fing_y):
    r['left_humerus'] = [hum_x, hum_y, hum_z]
    r['left_forearm'] = [0.0, fore_y, fore_z]
    r['left_finger'] = [0.0, fing_y, fing_z]
    r['right_humerus'] = [hum_x, -hum_y, -hum_z]
    r['right_forearm'] = [0.0, -fore_y, -fore_z]
    r['right_finger'] = [0.0, -fing_y, -fing_z]


def _set_legs(r, lt, ls, lf, rt, rs, rf):
    r['left_thigh'] = [lt, 0.0, 0.0]
    r['left_shank'] = [ls, 0.0, 0.0]
    r['left_foot'] = [lf, 0.0, 0.0]
    r['right_thigh'] = [rt, 0.0, 0.0]
    r['right_shank'] = [rs, 0.0, 0.0]
    r['right_foot'] = [rf, 0.0, 0.0]


def _lerp(a, b, f):
    return a + (b - a) * f


def _c01(v):
    return max(0.0, min(1.0, v))


def tail_trail(p):
    """HungarianHorntailEntity.tailTrail."""
    if p < TAIL_WIND_END:
        return -TAIL_TRAIL * 0.55 * (p / TAIL_WIND_END)
    if p < TAIL_SWEEP_END:
        w = (p - TAIL_WIND_END) / (TAIL_SWEEP_END - TAIL_WIND_END)
        return math.sin(w * math.pi) * TAIL_TRAIL - TAIL_TRAIL * 0.55 * (1.0 - w)
    w = (p - TAIL_SWEEP_END) / (1.0 - TAIL_SWEEP_END)
    return TAIL_TRAIL * 0.25 * (1.0 - w)


def model_pose(state, age, phase, amp, head=(0.0, 0.0), neck_roll=0.0):
    """HungarianHorntailModel.setupAnim for the memory dragon in one state: {'rot': part -> [x, y, z] radians,
    'body_y': px}. `phase` is the wing phase (each bone's beat from it: bone_beat), `amp` the scene's wing amplitude,
    `head` her (yaw, pitch) look in degrees (netHeadYaw, headPitch) and `neck_roll` the roll her neck counters
    (radians): MemoryHorntailPose's hooks."""
    beats = bone_beats(phase)
    beat = beats[0]
    r = _rest()
    by = BODY_Y
    r['neck1'] = [STAND_NECK1_X, 0.0, 0.0]
    r['neck2'] = [STAND_NECK2_X, 0.0, 0.0]
    r['head'] = [STAND_HEAD_X, 0.0, 0.0]
    r['jaw'] = [0.04, 0.0, 0.0]
    r['tail1'], r['tail2'], r['tail3'], r['tail4'] = [0.14, 0, 0], [0.10, 0, 0], [0.04, 0, 0], [-0.05, 0, 0]
    _set_wings(r, STAND_HUMERUS_Z, 0.0, 0.0, STAND_FOREARM_Z, 0.0, STAND_FINGER_Z, STAND_FINGER_Y)
    _set_legs(r, STAND_THIGH_X, STAND_SHANK_X, STAND_FOOT_X, STAND_THIGH_X, STAND_SHANK_X, STAND_FOOT_X)
    # head tracking, split along the neck
    yaw, pitch = math.radians(head[0]), math.radians(head[1])
    r['neck1'][1] = yaw * 0.30
    r['neck2'][1] = yaw * 0.40
    r['head'][1] = yaw * 0.30
    r['neck2'][0] = STAND_NECK2_X + pitch * 0.35
    r['head'][0] = STAND_HEAD_X + pitch * 0.65
    breathe = math.sin(age * 0.055)
    by = BODY_Y + breathe * 0.35
    r['neck1'][0] = STAND_NECK1_X + breathe * 0.035
    r['jaw'][0] = 0.04 + breathe * 0.02
    drift = age * 0.035
    r['tail1'] = [0.14 + math.sin(drift) * 0.04, math.sin(drift * 0.9) * 0.10, 0.0]
    r['tail2'] = [0.10 + math.sin(drift - 0.7) * 0.05, math.sin(drift * 0.9 - 0.6) * 0.13, 0.0]
    r['tail3'] = [0.04 + math.sin(drift - 1.4) * 0.06, math.sin(drift * 0.9 - 1.2) * 0.16, 0.0]
    r['tail4'] = [-0.05 + math.sin(drift - 2.1) * 0.07, math.sin(drift * 0.9 - 1.8) * 0.19, 0.0]
    name, t = state['name'], state['time']
    if state['flying']:
        _set_wings(r, 0.10 + beats[0] * amp, 0.15, 0.0, 0.14 + beats[1] * amp * 0.55, -0.10,
                   0.06 + beats[2] * amp * 0.75, -0.22)
        _set_legs(r, 0.75, 0.55, 0.25, 0.75, 0.55, 0.25)
        r['neck1'][0] = -0.28
        r['neck2'][0] = 0.22
        r['tail1'][0] = -0.12 + beat * 0.05
        r['tail2'][0] = -0.08 + beat * 0.05
        r['tail3'][0] = -0.04 + beat * 0.06
        r['tail4'][0] = 0.0 + beat * 0.07
        if name == 'FLY':
            r['body'] = [beat * 0.05, 0.0, 0.0]        # flightBank 0, flightPitch = beat * 0.05
            r['neck1'][2] = -neck_roll * 0.55          # neckCounterRoll: her whole-body roll
            r['neck2'][2] = -neck_roll * 0.35
    # (the ground gait with no stride is the rest pose: see the module docstring)
    if name == 'ROAR':
        p = _c01(t / DURATIONS['ROAR'])
        pk = math.sin(p * math.pi)
        r['neck1'][0] = STAND_NECK1_X - 1.0 * pk
        r['neck2'][0] = STAND_NECK2_X - 0.55 * pk
        r['head'][0] = STAND_HEAD_X - 0.75 * pk
        r['jaw'][0] = 0.04 + 0.85 * pk
        r['body'][0] = -0.18 * pk
        by = BODY_Y - 1.6 * pk
        _set_wings(r, _lerp(STAND_HUMERUS_Z, -0.45, pk), 0.2 * pk, 0.0, _lerp(STAND_FOREARM_Z, 0.35, pk), -0.15 * pk,
                   _lerp(STAND_FINGER_Z, 0.15, pk), _lerp(STAND_FINGER_Y, -0.25, pk))
        _set_legs(r, STAND_THIGH_X + 0.25 * pk, STAND_SHANK_X + 0.2 * pk, STAND_FOOT_X - 0.15 * pk,
                  STAND_THIGH_X + 0.25 * pk, STAND_SHANK_X + 0.2 * pk, STAND_FOOT_X - 0.15 * pk)
        r['tail1'][1] += math.sin(p * math.pi * 4.0) * 0.18
        r['tail2'][1] += math.sin(p * math.pi * 4.0 - 0.7) * 0.22
        r['tail3'][1] += math.sin(p * math.pi * 4.0 - 1.4) * 0.26
    elif name == 'BITE':
        p = _c01(t / DURATIONS['BITE'])
        coil = p / 0.35 if p < 0.35 else 0.0
        lunge = 0.0 if p < 0.35 else ((p - 0.35) / 0.20 if p < 0.55 else 1.0 - (p - 0.55) / 0.45)
        r['neck1'][0] = STAND_NECK1_X - 0.35 * coil + 0.55 * lunge
        r['neck2'][0] = STAND_NECK2_X + 0.30 * coil + 0.35 * lunge
        r['head'][0] = STAND_HEAD_X + 0.35 * lunge
        r['body'][0] = 0.10 * lunge - 0.06 * coil
        by = BODY_Y - 1.0 * lunge
        jaw_open = p / 0.45 if p < 0.45 else max(0.0, 1.0 - (p - 0.45) / 0.12)
        r['jaw'][0] = 0.04 + 0.95 * jaw_open
        r['left_humerus'][0] = -0.25 * lunge
        r['right_humerus'][0] = -0.25 * lunge
        _set_legs(r, STAND_THIGH_X - 0.3 * lunge, STAND_SHANK_X + 0.25 * lunge, STAND_FOOT_X,
                  STAND_THIGH_X - 0.3 * lunge, STAND_SHANK_X + 0.25 * lunge, STAND_FOOT_X)
    elif name == 'TAIL_WHIP':
        p = _c01(t / DURATIONS['TAIL_WHIP'])
        sign = -1.0                                   # MemoryHorntailEntity.isSweepingLeft is false
        trail = -tail_trail(p) * sign
        r['tail1'][1] = trail * 0.22
        r['tail2'][1] = trail * 0.26
        r['tail3'][1] = trail * 0.26
        r['tail4'][1] = trail * 0.26
        flat = math.sin(p * math.pi)
        r['tail1'][0] = 0.14 - 0.16 * flat
        r['tail2'][0] = 0.10 - 0.12 * flat
        r['tail3'][0] = 0.04 - 0.08 * flat
        r['tail4'][0] = -0.05 - 0.05 * flat
        by = BODY_Y + 1.2 * flat
        r['body'][2] = -0.16 * flat * sign
        r['neck1'][1] = 0.45 * flat * sign
        r['neck2'][1] = 0.35 * flat * sign
        r['head'][1] = 0.25 * flat * sign
        r['jaw'][0] = 0.04 + 0.25 * flat
        _set_wings(r, STAND_HUMERUS_Z + 0.18 * flat, 0.0, 0.0, STAND_FOREARM_Z + 0.10 * flat, 0.0, STAND_FINGER_Z,
                   STAND_FINGER_Y)
        _set_legs(r, STAND_THIGH_X + 0.2 * flat, STAND_SHANK_X + 0.15 * flat, STAND_FOOT_X,
                  STAND_THIGH_X + 0.2 * flat, STAND_SHANK_X + 0.15 * flat, STAND_FOOT_X)
    elif name == 'BREATH_WINDUP':
        p = _c01(t / DURATIONS['BREATH_WINDUP'])
        e = p * p
        r['neck1'][0] = STAND_NECK1_X - 0.55 * e
        r['neck2'][0] = STAND_NECK2_X + 0.45 * e
        r['head'][0] = STAND_HEAD_X - 0.30 * e
        r['jaw'][0] = 0.04 + 0.45 * e
        r['body'][0] = -0.07 * e
        _set_wings(r, _lerp(STAND_HUMERUS_Z, 0.45, e), 0.0, 0.0, _lerp(STAND_FOREARM_Z, 0.65, e), 0.0,
                   _lerp(STAND_FINGER_Z, -1.4, e), STAND_FINGER_Y)
    elif name == 'BREATH':
        p = _c01(t / DURATIONS['BREATH'])
        thrust = math.sin(min(p / 0.15, 1.0) * (math.pi / 2.0))
        r['neck1'][0] = STAND_NECK1_X + 0.40 * thrust
        r['neck2'][0] = STAND_NECK2_X - 0.25 * thrust + pitch * 0.35
        r['head'][0] = STAND_HEAD_X + 0.25 * thrust + pitch * 0.65
        r['jaw'][0] = 1.05 + math.sin(age * 0.9) * 0.06
        shudder = math.sin(age * 1.3) * 0.022
        r['body'][0] = -0.05 * thrust + shudder
        r['body'][2] = math.sin(age * 0.7) * 0.02
        r['neck1'][1] += math.sin(age * 1.1) * 0.03
        _set_wings(r, 0.55 + shudder, 0.0, 0.0, 0.75, 0.0, -1.35, STAND_FINGER_Y * 0.6)
        _set_legs(r, STAND_THIGH_X + 0.15, STAND_SHANK_X + 0.1, STAND_FOOT_X,
                  STAND_THIGH_X + 0.15, STAND_SHANK_X + 0.1, STAND_FOOT_X)
        r['tail1'][0], r['tail2'][0], r['tail3'][0], r['tail4'][0] = 0.02, 0.0, -0.02, -0.04
    elif name == 'WING_GUST':
        p = _c01(t / DURATIONS['WING_GUST'])
        if p < 0.42:
            b = -1.0 * (p / 0.42)
        elif p < 0.55:
            b = -1.0 + 2.6 * ((p - 0.42) / 0.13)
        else:
            b = 1.6 * (1.0 - (p - 0.55) / 0.45)
        _set_wings(r, 0.05 + b * 0.85, 0.25, 0.0, 0.10 + b * 0.5, -0.1, 0.05 + b * 0.65, -0.2)
        rear = math.sin(p * math.pi)
        r['body'][0] = -0.28 * rear
        by = BODY_Y - 2.0 * rear
        _set_legs(r, STAND_THIGH_X + 0.35 * rear, STAND_SHANK_X + 0.25 * rear, STAND_FOOT_X - 0.2 * rear,
                  STAND_THIGH_X + 0.35 * rear, STAND_SHANK_X + 0.25 * rear, STAND_FOOT_X - 0.2 * rear)
        r['neck1'][0] = STAND_NECK1_X - 0.25 * rear
        r['jaw'][0] = 0.04 + 0.4 * rear
    elif name == 'TAKEOFF':
        p = _c01(t / DURATIONS['TAKEOFF'])
        crouch = p / 0.2 if p < 0.2 else max(0.0, 1.0 - (p - 0.2) / 0.25)
        launch = _c01((p - 0.2) / 0.8)
        b = [x * TAKEOFF_GAIN for x in beats]          # MemoryHorntailPose.takeoffBoneBeat: the scene's, scaled
        _set_wings(r, 0.08 + b[0] * 0.95, 0.2, 0.0, 0.12 + b[1] * 0.6, -0.1, 0.05 + b[2] * 0.8,
                   _lerp(STAND_FINGER_Y, -0.22, launch))
        r['body'][0] = -0.32 * launch
        by = BODY_Y + 2.5 * crouch
        r['neck1'][0] = STAND_NECK1_X - 0.25 * launch
        extend = math.sin(_c01(p / 0.35) * math.pi)
        tuck = _c01((p - 0.35) / 0.65)
        lt = _lerp(STAND_THIGH_X + 0.4 * extend, 0.75, tuck)
        ls = _lerp(STAND_SHANK_X - 0.9 * extend, 0.55, tuck)
        lf = _lerp(STAND_FOOT_X + 0.5 * extend, 0.25, tuck)
        _set_legs(r, lt, ls, lf, lt, ls, lf)
        r['tail1'][0] = -0.2 * launch
        r['tail2'][0] = -0.15 * launch
    elif name == 'AIR_BREATH':
        charge = _c01(t / AIR_BREATH_WINDUP)
        firing = t >= AIR_BREATH_WINDUP
        r['body'][0] = -0.22 + beat * 0.05             # airBreathBob: her own humerus beat
        r['neck1'][0] = STAND_NECK1_X + 0.25 * charge
        r['neck2'][0] = STAND_NECK2_X + 0.30 * charge + pitch * 0.4
        r['head'][0] = STAND_HEAD_X + pitch * 0.55
        r['jaw'][0] = 1.0 + math.sin(age * 0.9) * 0.06 if firing else 0.04 + 0.5 * charge
        if firing:
            r['body'][0] += math.sin(age * 1.3) * 0.03
        _set_legs(r, 0.45, 0.85, 0.1, 0.45, 0.85, 0.1)
        r['tail1'][0], r['tail2'][0], r['tail3'][0] = 0.25, 0.20, 0.15
    elif name == 'DIVE':
        _set_wings(r, 0.55, 0.85, 0.0, 0.45, 0.65, 0.30, 0.55)
        r['body'] = [0.62, 0.0, math.sin(age * 0.25) * 0.06]
        r['neck1'][0], r['neck2'][0], r['head'][0], r['jaw'][0] = -0.12, 0.10, 0.20, 0.35
        _set_legs(r, -0.55, 0.35, 0.6, -0.55, 0.35, 0.6)
        r['tail1'][0], r['tail2'][0], r['tail3'][0], r['tail4'][0] = -0.35, -0.25, -0.15, -0.10
    elif name == 'LAND':
        p = _c01(t / DURATIONS['LAND'])
        flare = 1.0 - p
        _set_wings(r, _lerp(-0.35, STAND_HUMERUS_Z, p), 0.55 * flare, 0.0, _lerp(0.75, STAND_FOREARM_Z, p), 0.4 * flare,
                   _lerp(0.9, STAND_FINGER_Z, p), _lerp(0.5, STAND_FINGER_Y, p))
        r['body'][0] = -0.45 * flare
        by = BODY_Y - 2.0 * flare
        r['neck1'][0] = STAND_NECK1_X - 0.2 * flare
        _set_legs(r, _lerp(-0.35, STAND_THIGH_X, p), _lerp(0.35, STAND_SHANK_X, p), _lerp(0.45, STAND_FOOT_X, p),
                  _lerp(-0.35, STAND_THIGH_X, p), _lerp(0.35, STAND_SHANK_X, p), _lerp(0.45, STAND_FOOT_X, p))
        r['tail1'][0] = -0.25 * flare
        r['tail2'][0] = -0.20 * flare
    elif name == 'STAGGER':
        p = _c01(t / DURATIONS['STAGGER'])
        rc = math.sin(p * math.pi)
        r['neck1'][0] = STAND_NECK1_X - 0.45 * rc
        r['neck2'][0] = STAND_NECK2_X + 0.30 * rc
        r['head'][0] = STAND_HEAD_X - 0.30 * rc
        r['jaw'][0] = 0.04 + 0.45 * rc
        r['body'][0] = -0.16 * rc
        r['body'][2] = math.sin(p * math.pi * 2.0) * 0.10
        by = BODY_Y + 1.0 * rc
        _set_wings(r, STAND_HUMERUS_Z - 0.35 * rc, 0.0, 0.0, STAND_FOREARM_Z + 0.2 * rc, 0.0,
                   STAND_FINGER_Z + 0.5 * rc, STAND_FINGER_Y)
    return {'rot': r, 'body_y': by}


def head_look(actor, t):
    """DragonFlight.headLook: (yaw, pitch) degrees from the actor's limbs.head_look rot (v: [pitch, yaw, 0]), clamped."""
    keys = ((actor.get('limbs') or {}).get(HEAD_LOOK) or {}).get('rot') or []
    if not keys:
        return 0.0, 0.0
    v = E.sample_keys(keys, t)
    pitch = v[0] if len(v) > 0 else 0.0
    yaw = v[1] if len(v) > 1 else 0.0
    return (max(-HEAD_YAW_LIMIT, min(HEAD_YAW_LIMIT, yaw)), max(-HEAD_PITCH_LIMIT, min(HEAD_PITCH_LIMIT, pitch)))


def neck_roll(roll):
    """DragonFlight.neckRoll: her whole-body roll (degrees, right side down positive) limited to MAX_BANK, as a model
    body zRot in radians (the model's y and z point the other way, so the sign flips)."""
    return -math.radians(max(-MAX_BANK, min(MAX_BANK, roll)))


def _mix(old, cur, w):
    rot = {p: [old['rot'][p][c] + (cur['rot'][p][c] - old['rot'][p][c]) * w for c in range(3)] for p in PARTS}
    return {'rot': rot, 'body_y': old['body_y'] + (cur['body_y'] - old['body_y']) * w}


def blended_model_pose(actor, t, phase=None):
    """MemoryHorntailModel.setupAnim at scene time t: the state's pose, cross-faded from the one before it (or from
    the whole fade that was still running when it began). `phase` overrides the wing phase (horntail_parts sweeps the
    wings with it)."""
    keys = path_of(actor)
    ph = wing_phase(actor, t) if phase is None else phase
    amp = wing_amp(E.path_speed(keys, t), climb_rate(keys, t))
    age = t
    head = head_look(actor, t)
    roll = neck_roll(horntail_pose(actor, t)['roll'])

    def pose_of(b):
        if b['before'] is None or b['weight'] >= 1.0:
            return model_pose(b['now'], age, ph, amp, head, roll)
        old = pose_of(b['older']) if b['older'] is not None else model_pose(b['before'], age, ph, amp, head, roll)
        return _mix(old, model_pose(b['now'], age, ph, amp, head, roll), b['weight'])

    return pose_of(horntail_blend_tree(actor, t))


# ---------------------------------------------------------------- the part chain

def _rot_zyx(x, y, z):
    """Quaternionf.rotationZYX(z, y, x) as a row-major 3x3: Rz * Ry * Rx."""
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = [1, 0, 0, 0, cx, -sx, 0, sx, cx]
    ry = [cy, 0, sy, 0, 1, 0, -sy, 0, cy]
    rz = [cz, -sz, 0, sz, cz, 0, 0, 0, 1]
    return E._mat_mul(E._mat_mul(rz, ry), rx)


def part_matrices(pose):
    """Each part's transform in model space (blocks, model y down): part -> (R, T), point = R * p/16 + T."""
    out = {}
    for name in PARTS:
        parent, off = TREE[name]
        off = list(off)
        if name == 'body':
            off[1] = pose['body_y']
        x, y, z = pose['rot'][name]
        local_r = _rot_zyx(x, y, z)
        local_t = [c / 16.0 for c in off]
        if parent is None:
            out[name] = (local_r, local_t)
        else:
            pr, pt = out[parent]
            v = E._mat_vec(pr, local_t)
            out[name] = (E._mat_mul(pr, local_r), [pt[i] + v[i] for i in range(3)])
    return out


def model_point(mats, part, p_px):
    r, t = mats[part]
    v = E._mat_vec(r, [c / 16.0 for c in p_px])
    return [t[i] + v[i] for i in range(3)]


def model_dir(mats, part, d):
    return E._mat_vec(mats[part][0], d)


def model_to_local(m):
    """Model space (blocks) to her local frame at scale 1 (blocks from the root, x left, y up, z forward)."""
    return [RENDER_SCALE * m[0], RENDER_SCALE * (MODEL_LIFT - m[1]), -RENDER_SCALE * m[2]]


def _local_dir(d):
    return [d[0], -d[1], -d[2]]


# ---------------------------------------------------------------- capsules

def _spike(name, parent, x, y, z, xrot, zrot, length, width=2.0):
    return {'name': name, 'part': parent, 'child': ((x, y, z), _rot_zyx(xrot, 0.0, zrot)), 'child_pose': [x, y, z, xrot, zrot],
            'box': ((-width / 2, -length, -width / 2), (width / 2, 0.0, width / 2))}


def _box(name, part, x, y, z, dx, dy, dz):
    return {'name': name, 'part': part, 'child': None, 'child_pose': None, 'box': ((x, y, z), (x + dx, y + dy, z + dz))}


def _capsule_specs():
    """Every capsule's part and its box in part px (spikes and horns with their own offset and rotation), from
    HungarianHorntailModel.createBodyLayer's cubes."""
    c = [_box('body', 'body', -7, -9, -15, 14, 14, 28), _box('body.shoulders', 'body', -8, -12, -12, 16, 5, 14),
         _box('body.pelvis', 'body', -6, -9, 5, 12, 10, 10), _box('body.ridge', 'body', -1, -14, -12, 2, 3, 26)]
    for i in range(7):
        c.append(_spike('body.spine_%d' % i, 'body', 0.0, -13.5, -11.0 + i * 3.8, -0.35, 0.0, 5.0 - abs(i - 2) * 0.4))
    c.append(_box('neck1', 'neck1', -4, -4, -14, 8, 8, 14))
    c.append(_box('neck2', 'neck2', -3, -3, -14, 6, 6, 14))
    for i in range(3):
        c.append(_spike('neck1.spike_%d' % i, 'neck1', 0.0, -4.0, -3.0 - i * 4.0, -0.5, 0.0, 3.5))
        c.append(_spike('neck2.spike_%d' % i, 'neck2', 0.0, -3.0, -3.0 - i * 4.0, -0.5, 0.0, 3.0))
    c += [_box('head', 'head', -4.5, -4, -13, 9, 8, 14), _box('head.snout', 'head', -3, -1, -20, 6, 5, 8),
          _box('head.brow_l', 'head', 2.5, -5.5, -12, 3, 2, 7), _box('head.brow_r', 'head', -5.5, -5.5, -12, 3, 2, 7)]
    for n, x, y, z, xr, zr, ln in (('horn_back_l', 4.0, -4.5, -2.0, 0.55, 0.45, 12.0),
                                   ('horn_back_r', -4.0, -4.5, -2.0, 0.55, -0.45, 12.0),
                                   ('horn_mid_l', 5.0, -3.5, -6.0, 0.25, 0.85, 9.0),
                                   ('horn_mid_r', -5.0, -3.5, -6.0, 0.25, -0.85, 9.0),
                                   ('horn_top_l', 2.0, -5.0, -4.0, 0.75, 0.18, 10.0),
                                   ('horn_top_r', -2.0, -5.0, -4.0, 0.75, -0.18, 10.0)):
        c.append(_spike('head.' + n, 'head', x, y, z, xr, zr, ln, 3.0))
    c.append(_spike('head.cheek_l', 'head', 4.5, -1.0, -8.0, 0.1, 1.3, 4.0))
    c.append(_spike('head.cheek_r', 'head', -4.5, -1.0, -8.0, 0.1, -1.3, 4.0))
    c.append(_box('jaw', 'jaw', -3.5, 0, -15, 7, 3, 15))
    for k, w in ((1, 8), (2, 6), (3, 5), (4, 4)):
        c.append(_box('tail%d' % k, 'tail%d' % k, -w / 2, -w / 2, 0, w, w, 14))
    for i in range(3):
        c.append(_spike('tail1.spike_%d' % i, 'tail1', 0.0, -4.0, 3.0 + i * 4.0, -0.4, 0.0, 4.0))
        c.append(_spike('tail2.spike_%d' % i, 'tail2', 0.0, -3.0, 3.0 + i * 4.0, -0.4, 0.0, 3.5))
        c.append(_spike('tail3.spike_%d' % i, 'tail3', 0.0, -2.5, 3.0 + i * 4.0, -0.4, 0.0, 3.0))
    for n, x, y, z, xr, zr, ln in (('sting_up', 0.0, -2.0, 12.0, -0.55, 0.0, 10.0),
                                   ('sting_l', 2.0, 0.0, 12.0, 0.0, 0.75, 9.0),
                                   ('sting_r', -2.0, 0.0, 12.0, 0.0, -0.75, 9.0),
                                   ('sting_dn', 0.0, 2.0, 11.0, 0.55, 0.0, 8.0),
                                   ('sting_back', 0.0, 0.0, 14.0, -1.57, 0.0, 9.0)):
        c.append(_spike('tail4.' + n, 'tail4', x, y, z, xr, zr, ln, 3.0))
    for s, p in ((1.0, 'left_'), (-1.0, 'right_')):
        def mir(name, part, x, y, z, dx, dy, dz):
            return _box(name, part, x if s > 0 else -x - dx, y, z, dx, dy, dz)
        c.append(mir(p + 'humerus', p + 'humerus', 0, -2.5, -2.5, 13, 5, 5))
        c.append(mir(p + 'humerus.sail', p + 'humerus', 0, -0.5, 2, 13, 1, 14))
        c.append(mir(p + 'forearm', p + 'forearm', 0, -2, -2, 11, 4, 4))
        c.append(mir(p + 'forearm.sail', p + 'forearm', 0, -0.5, 1, 11, 1, 20))
        c.append(_spike(p + 'forearm.claw', p + 'forearm', 11.0 * s, 0.0, 0.0, 2.05, 0.0, 5.0))
        c.append(mir(p + 'finger', p + 'finger', 0, -1.5, -1.5, 24, 3, 3))
        for k in range(3):
            c.append(mir(p + 'finger.sail_%d' % k, p + 'finger', 8 * k, -0.5, 0, 8, 1, 26))
        c.append(_box(p + 'thigh', p + 'thigh', -4, -1, -5, 8, 10, 10))
        c.append(_box(p + 'shank', p + 'shank', -3, -1, -3.5, 6, 10, 7))
        c.append(_box(p + 'foot', p + 'foot', -4, 0, -8, 8, 3, 11))
        for n, x, xr, zr, ln in (('claw_a', -2.5, 1.9, 0.25, 4.0), ('claw_b', 0.0, 1.9, 0.0, 4.5),
                                 ('claw_c', 2.5, 1.9, -0.25, 4.0)):
            c.append(_spike(p + 'foot.' + n, p + 'foot', x, 3.0, -8.5 if n == 'claw_b' else -8.0, xr, zr, ln))
    for spec in c:
        (x0, y0, z0), (x1, y1, z1) = spec['box']
        dims = [x1 - x0, y1 - y0, z1 - z0]
        axis = max(range(3), key=lambda i: dims[i])
        others = sorted([dims[i] for i in range(3) if i != axis], reverse=True)
        big, small = others
        centre = [(x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2]
        half = max(0.0, (dims[axis] - big) / 2)
        a, b = list(centre), list(centre)
        a[axis] -= half
        b[axis] += half
        spec['seg'] = (a, b)
        spec['r_px'] = math.sqrt(2 * big * big + small * small) / 2   # covers every corner of the box
    return c


CAPSULES = _capsule_specs()
WING_PARTS = ('humerus', 'forearm', 'finger')
SWEEP_PHASES = 12                       # wing poses sampled around a stroke for the swept capsules


def _spec_point(mats, spec, p):
    if spec['child'] is not None:
        (ox, oy, oz), rr = spec['child']
        v = E._mat_vec(rr, p)
        p = [ox + v[0], oy + v[1], oz + v[2]]
    return model_point(mats, spec['part'], p)


def _seg_dist(p, a, b):
    ab = [b[i] - a[i] for i in range(3)]
    ap = [p[i] - a[i] for i in range(3)]
    ll = sum(c * c for c in ab)
    f = 0.0 if ll == 0 else max(0.0, min(1.0, sum(ap[i] * ab[i] for i in range(3)) / ll))
    q = [a[i] + ab[i] * f for i in range(3)]
    return math.sqrt(sum((p[i] - q[i]) ** 2 for i in range(3)))


def _is_wing(spec):
    part = spec['part']
    return any(part.endswith(w) for w in WING_PARTS)


def _uses_beat(actor, t):
    now, before, w = horntail_blend(actor, t)
    return now['flying'] or (before is not None and before['flying'])


def horntail_parts(scene, aid, t, swept=True):
    """Conservative capsules of every part of Horntail `aid` at tick t: [(name, (a, b, r))], scene space, r in blocks.
    Each capsule holds its whole box (spikes and horns are capsules of their own). With `swept` (the default), while
    she flies each wing capsule is given at SWEEP_PHASES evenly spaced phases of the stroke at the current amplitude
    (names `<part>~0` to `<part>~11`; the bones lag each other, so a phase, not a beat, fixes the whole wing), each
    widened by half the way to its neighbours round the cycle, so together they hold the wing wherever it is in the
    stroke and a check at a few samples a tick cannot miss a wingtip. Without it, the wings are where they are drawn
    at t."""
    actor = E.actor_by_id(scene, aid)
    scale = actor.get('scale', 1.0)
    pose = horntail_pose(actor, t)
    mats = part_matrices(blended_model_pose(actor, t))
    sweep = ([part_matrices(blended_model_pose(actor, t, phase=2 * math.pi * k / SWEEP_PHASES))
              for k in range(SWEEP_PHASES)] if swept and _uses_beat(actor, t) else [])

    def world(m, spec, p):
        return to_world(pose, scale, model_to_local(_spec_point(m, spec, p)))

    out = []
    k = RENDER_SCALE / 16.0 * scale
    for spec in CAPSULES:
        r = spec['r_px'] * k
        if sweep and _is_wing(spec):
            segs = [(world(m, spec, spec['seg'][0]), world(m, spec, spec['seg'][1])) for m in sweep]
            for i, (a, b) in enumerate(segs):
                gap = 0.0
                for j in ((i - 1) % len(segs), (i + 1) % len(segs)):
                    gap = max(gap, math.dist(a, segs[j][0]) / 2, math.dist(b, segs[j][1]) / 2)
                out.append(('%s~%d' % (spec['name'], i), (a, b, r + gap)))
        else:
            out.append((spec['name'], (world(mats, spec, spec['seg'][0]), world(mats, spec, spec['seg'][1]), r)))
    return out


def capsule_distance(cap, p):
    """How far point p is outside capsule cap = (a, b, r) (negative inside)."""
    a, b, r = cap
    return _seg_dist(p, a, b) - r


def horntail_snout(scene, aid, t):
    """(point, dir) of Horntail `aid`'s mouth at tick t, scene space: the point between her jaws at the front of the
    upper snout, the direction her head faces (unit). As MemoryHorntailRenderer stores it for the fire."""
    actor = E.actor_by_id(scene, aid)
    scale = actor.get('scale', 1.0)
    pose = horntail_pose(actor, t)
    mats = part_matrices(blended_model_pose(actor, t))
    p = to_world(pose, scale, model_to_local(model_point(mats, 'head', SNOUT)))
    d = E._mat_vec(pose['m'], _local_dir(model_dir(mats, 'head', [0.0, 0.0, -1.0])))
    n = math.sqrt(sum(c * c for c in d))
    return p, [c / n for c in d]


# DragonFlight.MOUTH: per state {mouth x, y, z (local, scale 1), its direction at head look 0, the head look's pivot
# y and z, the share of the look's pitch that moves the mouth, the share that turns its direction}; fitted on
# horntail_snout over the head look's range (see mouth_estimate_check).
MOUTH_ESTIMATE = {
    'BREATH': (0.0, 0.53, 3.75, 0.0, -0.66, 0.75, 2.40, 1.95, 0.60, 1.05),
    'AIR_BREATH': (0.0, 0.83, 3.51, 0.0, -0.64, 0.77, 2.50, 2.05, 0.70, 0.90),
    'FLY': (0.0, 1.48, 4.14, 0.0, -0.22, 0.975, 2.30, 2.00, 0.40, 0.70),
}
REST_MOUTH_ESTIMATE = (0.0, 1.27, 3.99, 0.0, -0.37, 0.93, 2.25, 2.10, 0.70, 1.00)


def _turn(v, pitch, yaw):
    """DragonFlight.turn: a local vector pitched nose down by `pitch`, then yawed right by `yaw` (degrees)."""
    a, b = math.radians(pitch), -math.radians(yaw)
    y = v[1] * math.cos(a) - v[2] * math.sin(a)
    z = v[1] * math.sin(a) + v[2] * math.cos(a)
    x = v[0] * math.cos(b) + z * math.sin(b)
    z = -v[0] * math.sin(b) + z * math.cos(b)
    return [x, y, z]


def mouth_estimate(actor, t):
    """DragonFlight.mouthEstimate: her mouth from her pose and head look alone, scene space, (point, unit dir): the
    state's mouth offset turned about the look's pivot, then carried by her pose. What the fire uses when she was
    not drawn this frame (a late joiner's past puffs)."""
    pose = horntail_pose(actor, t)
    k = MOUTH_ESTIMATE.get(horntail_cue(actor, t)['name'], REST_MOUTH_ESTIMATE)
    yaw, pitch = head_look(actor, t)
    p = _turn([k[0], k[1] - k[6], k[2] - k[7]], pitch * k[8], yaw)
    p[1] += k[6]
    p[2] += k[7]
    d = _turn([k[3], k[4], k[5]], pitch * k[9], yaw)
    w = to_world(pose, actor.get('scale', 1.0), p)
    wd = E._mat_vec(pose['m'], d)
    n = math.sqrt(sum(c * c for c in wd))
    return w, [c / n for c in wd]


def model_snout(actor, t):
    """The mouth in model space (blocks, model y down) at scene time t: what the Java model test compares."""
    mats = part_matrices(blended_model_pose(actor, t))
    return model_point(mats, 'head', SNOUT), model_dir(mats, 'head', [0.0, 0.0, -1.0])


# ================================================================ the parity cases (DragonFlightTest.pythonMirrorAgrees)

CASES = os.path.join(HERE, 'horntail_cases.json')
WINGTIP = (24.0, 0.0, 0.0)              # the left finger's tip, part px (its bone is 24 px long)


def _spline(pts, t0, dt, last_ease='in_out'):
    keys = []
    for i, p in enumerate(pts):
        k = {'t': t0 + i * dt, 'pos': [round(c, 4) for c in p]}
        if i > 0:
            k['ease'] = 'spline'
        keys.append(k)
    if last_ease and len(keys) > 1:
        keys[-1]['ease'] = last_ease
    return keys


def _case_scenes():
    def dragon(path, anims, look=None, head=None):
        a = {'id': 'h', 'type': HORNTAIL_TYPE, 'path': path, 'anims': anims}
        if look:
            a['look'] = look
        if head:
            a['limbs'] = {HEAD_LOOK: {'rot': head}}
        return {'length': 200, 'actors': [a]}

    still = [{'t': 0, 'pos': [0.0, 0.0, 0.0]}]
    rise = [{'t': 0, 'pos': [0.0, 0.0, 0.0]}, {'t': 20, 'pos': [0.0, 0.0, 0.0]}] + \
        _spline([[0, 0.3 * i * i, 0.4 * i * i] for i in range(1, 8)], 25, 5)[0:]
    hover = _spline([[0.02 * i, 10 + 0.05 * math.sin(i), 0.03 * i] for i in range(0, 21)], 0, 10, None)
    cruise = _spline([[0.0, 12.0, 0.8 * 10 * i] for i in range(0, 21)], 0, 10, None)
    climb = _spline([[0.0, 12.0 + 0.3 * 10 * i, 0.5 * 10 * i] for i in range(0, 21)], 0, 10, None)
    glide_p = _spline([[0.0, 120.0 - 0.2 * 10 * i, 0.95 * 10 * i] for i in range(0, 21)], 0, 10, None)
    circle = []
    for i in range(0, 25):
        th = i * 10 * 0.6 / 20.0
        circle.append([20 * math.sin(th), 15.0, 20 - 20 * math.cos(th)])
    circle = _spline(circle, 0, 10, None)
    fall = _spline([[0.0, 30.0, 0.0], [0.0, 30.0, 4.0], [0.0, 29.5, 8.0], [0.5, 26.0, 10.0], [1.0, 18.0, 11.0],
                    [1.5, 8.0, 12.0], [1.8, 2.0, 12.5]], 0, 10)
    tumble = [{'t': 0, 'yaw': 0, 'pitch': 0, 'roll': 0}, {'t': 30, 'yaw': 0, 'pitch': 0, 'roll': 0},
              {'t': 40, 'yaw': 15, 'pitch': -25, 'roll': 40, 'ease': 'spline'},
              {'t': 50, 'yaw': 40, 'pitch': 30, 'roll': 110, 'ease': 'spline'},
              {'t': 60, 'yaw': 60, 'pitch': 50, 'roll': 160, 'ease': 'in_out'}]
    return {
        'perched': dragon(still, [{'t': 0, 'name': 'IDLE'}],
                          head=[{'t': 0, 'v': [0, 0, 0]}, {'t': 10, 'v': [15, 40, 0]}, {'t': 37, 'v': [-20, -75, 0]}]),
        'roar': dragon(still, [{'t': 0, 'name': 'idle'}, {'t': 20, 'name': 'ROAR', 'blend': 8}]),
        'takeoff': dragon(rise, [{'t': 0, 'name': 'IDLE'}, {'t': 20, 'name': 'TAKEOFF'}]),
        'hover': dragon(hover, [{'t': 0, 'name': 'FLY'}], head=[{'t': 0, 'v': [0, 0, 0]}, {'t': 60, 'v': [25, -35, 0]}]),
        'cruise': dragon(cruise, [{'t': 0, 'name': 'FLY'}]),
        'climb': dragon(climb, [{'t': 0, 'name': 'FLY'}]),
        'glide': dragon(glide_p, [{'t': 0, 'name': 'FLY'}]),
        'banked': dragon(circle, [{'t': 0, 'name': 'FLY'}]),
        'air_breath': dragon(hover, [{'t': 0, 'name': 'FLY'}, {'t': 20, 'name': 'AIR_BREATH'}]),
        'stagger': dragon(fall, [{'t': 0, 'name': 'FLY'}, {'t': 30, 'name': 'STAGGER'}, {'t': 52, 'name': 'LAND'}],
                          tumble),
        # cues inside each other's fades: the fades nest (no TAIL_WHIP: its pose calls HungarianHorntailEntity.tailTrail,
        # whose class cannot initialise in a unit test without the registries)
        'quick': dragon(still, [{'t': 0, 'name': 'IDLE'}, {'t': 20, 'name': 'ROAR', 'blend': 8},
                                {'t': 24, 'name': 'BITE'}, {'t': 27, 'name': 'BREATH_WINDUP'}]),
    }


CASE_TIMES = {'perched': [0, 10.5, 37, 80], 'roar': [15, 22.5, 47.25, 77], 'takeoff': [25, 35.5, 52, 60],
              'hover': [12, 40.5, 77.25, 150], 'cruise': [5, 33.5, 90, 151.75], 'climb': [8, 42.5, 99, 160],
              'glide': [10, 55.5, 100, 170.25], 'banked': [20, 61.25, 110, 180],
              'air_breath': [18, 23.5, 40, 75], 'stagger': [25, 33.5, 45.25, 70], 'quick': [22, 25.5, 28.5, 40]}


def horntail_cases():
    scenes = _case_scenes()
    cases = []
    for name, times in CASE_TIMES.items():
        sc = scenes[name]
        h = sc['actors'][0]
        for t in times:
            p = horntail_pose(h, t)
            cue = horntail_cue(h, t)
            now, before, w = horntail_blend(h, t)
            ms, md = model_snout(h, t)
            pt, dr = horntail_snout(sc, 'h', t)
            ti = int(math.floor(t))
            mats = part_matrices(blended_model_pose(h, t))
            points = [[_r5(_spec_point(mats, spec, spec['seg'][k])) for k in (0, 1)] for spec in CAPSULES]
            cases.append({'scene': name, 't': t, 'pos': p['pos'], 'yaw': p['yaw'], 'pitch': p['pitch'],
                          'head_look': list(head_look(h, t)), 'neck_roll': neck_roll(p['roll']),
                          'blend_chain': blend_chain(horntail_blend_tree(h, t)),
                          'part_points': points, 'wingtip': _r5(model_point(mats, 'left_finger', WINGTIP)),
                          'roll': p['roll'], 'speed': p['speed'], 'climb': p['climb'],
                          'bank': dragon_bank(h['path'], t), 'climb_pitch': dragon_climb(h['path'], t),
                          'beat_rate': beat_rate(p['speed'], p['climb']), 'wing_amp': wing_amp(p['speed'], p['climb']),
                          'phase': wing_phase(h, t), 'state': cue,
                          'before': before['name'] if before else None, 'weight': w,
                          'downstrokes': raw_downstrokes(h['path'], max(0, ti - 40), ti),
                          'beating_downstrokes': downstroke_ticks(h, max(0, ti - 40), ti),
                          'model_snout': ms, 'model_snout_dir': md, 'snout': pt, 'snout_dir': dr})
    return {'constants': {'PIVOT_Y': PIVOT_Y, 'BANK_GAIN': BANK_GAIN, 'MAX_BANK': MAX_BANK,
                          'BANK_FADE_SPEED': BANK_FADE_SPEED, 'MAX_CLIMB': MAX_CLIMB, 'HOVER_RATE': HOVER_RATE,
                          'CRUISE_RATE': CRUISE_RATE, 'HOVER_AMP': HOVER_AMP, 'CRUISE_AMP': CRUISE_AMP,
                          'GLIDE_AMP': GLIDE_AMP, 'DEFAULT_BLEND': DEFAULT_BLEND, 'DURATIONS': DURATIONS,
                          'SNOUT': list(SNOUT), 'CLIMB_BOOST': CLIMB_BOOST, 'TAKEOFF_GAIN': TAKEOFF_GAIN,
                          'DOWNSTROKE_SHARE': DOWNSTROKE_SHARE, 'FOREARM_LAG': FOREARM_LAG, 'FINGER_LAG': FINGER_LAG,
                          'HEAD_YAW_LIMIT': HEAD_YAW_LIMIT, 'HEAD_PITCH_LIMIT': HEAD_PITCH_LIMIT,
                          'MIDPLANE_CLEARANCE': MIDPLANE_CLEARANCE, 'WINGTIP': list(WINGTIP),
                          'TREE': {k: v[0] for k, v in TREE.items()},
                          'CAPSULES': [{'name': c['name'], 'part': c['part'], 'child': c['child_pose'],
                                        'seg': [list(c['seg'][0]), list(c['seg'][1])]} for c in CAPSULES]},
            'scenes': scenes, 'cases': cases}


def _r5(v):
    return [round(c, 5) for c in v]


def write_cases():
    from scene_common import dump
    data = horntail_cases()
    with open(CASES, 'w', newline='\n', encoding='utf-8') as f:
        f.write(dump(data, 0, 150) + '\n')
    print('wrote', CASES, len(data['cases']), 'cases')


# ================================================================ self-checks

def self_check():
    problems = []
    # rate: hover > cruise > glide = 0, continuous
    if not beat_rate(0.0, 0.0) > beat_rate(0.8, 0.0) > beat_rate(1.0, -0.3) == 0.0:
        problems.append('beat rate order')
    worst = 0.0
    for i in range(0, 700):
        s = i * 0.002
        for c in (-0.3, -0.12, 0.0, 0.15, 0.4):
            worst = max(worst, abs(beat_rate(s + 0.002, c) - beat_rate(s, c)))
    for i in range(-300, 300):
        c = i * 0.002
        for s in (0.0, 0.5, 0.85, 0.95):
            worst = max(worst, abs(beat_rate(s, c + 0.002) - beat_rate(s, c)))
    if worst > 0.01:
        problems.append('beat rate steps %.4f' % worst)
    # the snout is ahead of the body and the capsules hold the snout's neighbourhood
    sc = _case_scenes()['perched']
    p, d = horntail_snout(sc, 'h', 10)
    if not (p[2] > 3.0 and 0.5 < p[1] < 3.5):
        problems.append('perched snout at %s' % p)
    parts = dict(horntail_parts(sc, 'h', 10))
    if capsule_distance(parts['head.snout'], p) > 0.05:
        problems.append('the snout point is outside the snout capsule')
    # a wingbeat lands mid-downstroke: the humerus beat rising through 0
    fly = _case_scenes()['hover']['actors'][0]
    ds = raw_downstrokes(fly['path'], 0, 160)
    for tick in ds:
        ang = stroke_angle(wing_phase(fly, tick))
        if math.cos(ang) < 0.9:
            problems.append('downstroke at %d is not mid-downstroke (stroke angle %.2f)' % (tick, ang))
    # a positive beat lowers the wingtip (model y is down)
    st = {'name': 'FLY', 'tick': 0, 'time': 0.0, 'flying': True}
    low = model_point(part_matrices(model_pose(st, 0.0, math.pi / 2, HOVER_AMP)), 'left_finger', WINGTIP)
    high = model_point(part_matrices(model_pose(st, 0.0, -math.pi / 2, HOVER_AMP)), 'left_finger', WINGTIP)
    if not low[1] > high[1]:
        problems.append('a positive beat does not lower the wingtip (y %.2f vs %.2f)' % (low[1], high[1]))
    gap, where = midplane_clearance()
    if gap < MIDPLANE_CLEARANCE:
        problems.append('a fingertip comes %.2f from her midplane (%s)' % (gap, where))
    tilt = neck_counter_check()
    if tilt is not None:
        problems.append(tilt)
    problems += heading_hold_check()
    print('dragon_engine: beat rate worst step %.4f rad/tick, %d capsules, hover downstrokes %s, fingertips at least '
          '%.2f from the midplane (%s)' % (worst, len(parts), ds[:6], gap, where))
    return problems


def midplane_clearance(steps=720):
    """How near (blocks, scale 1) either fingertip comes to her body midplane (model x = 0) at any stroke phase, in
    every state that beats the wings at the largest amplitude (FLY, AIR_BREATH, BITE in the air) and through a
    takeoff: (gap, where). The model is mirrored, so the left tip answers for both."""
    worst = (1e9, None)
    states = [('FLY', [0.0]), ('AIR_BREATH', [0.0, 20.0]), ('BITE', [0.0, 9.0, 14.0]),
              ('TAKEOFF', [2.0 * k for k in range(16)])]
    for name, times in states:
        for time in times:
            st = {'name': name, 'tick': int(time), 'time': time, 'flying': True}
            for i in range(steps):
                ph = 2 * math.pi * i / steps
                m = part_matrices(model_pose(st, 0.0, ph, HOVER_AMP))
                x = model_point(m, 'left_finger', WINGTIP)[0] * RENDER_SCALE
                if x < worst[0]:
                    worst = (x, '%s t %.0f phase %.2f' % (name, time, ph))
    return worst


def heading_hold_check():
    """Her heading holds through a dead hover (the fire review's probes on broom_heading): a hover followed by a
    spline departure that speeds up (whose start runs backwards 0.375 blocks) never turns her round, and a hover
    between two directions keeps the arrival heading until she leaves."""
    problems = []
    depart = {'id': 'h', 'type': HORNTAIL_TYPE, 'anims': [{'t': 0, 'name': 'FLY'}],
              'path': [{'t': 0, 'pos': [0.0, 10.0, 0.0]}, {'t': 80, 'pos': [0.0, 10.0, 0.0], 'ease': 'in_out'},
                       {'t': 100, 'pos': [0.0, 10.0, 2.0], 'ease': 'spline'},
                       {'t': 120, 'pos': [0.0, 10.0, 22.0], 'ease': 'spline'},
                       {'t': 140, 'pos': [0.0, 10.0, 42.0], 'ease': 'spline'}]}
    worst = max(abs(((horntail_pose(depart, t / 2.0)['yaw'] + 180) % 360) - 180) for t in range(0, 280))
    if worst > 2.0:
        problems.append('a hover then a spline departure turns her %.1f degrees off her way' % worst)
    turn = {'id': 'h', 'type': HORNTAIL_TYPE, 'anims': [{'t': 0, 'name': 'FLY'}],
            'path': [{'t': 0, 'pos': [0.0, 10.0, -40.0]}, {'t': 20, 'pos': [0.0, 10.0, -20.0], 'ease': 'spline'},
                     {'t': 40, 'pos': [0.0, 10.0, 0.0], 'ease': 'in_out'},
                     {'t': 120, 'pos': [0.0, 10.0, 0.0], 'ease': 'in_out'},
                     {'t': 140, 'pos': [-2.0, 10.0, 0.0], 'ease': 'spline'},
                     {'t': 160, 'pos': [-22.0, 10.0, 0.0], 'ease': 'spline'},
                     {'t': 180, 'pos': [-42.0, 10.0, 0.0], 'ease': 'spline'}]}
    held = max(abs(((horntail_pose(turn, t)['yaw'] + 180) % 360) - 180) for t in range(20, 121))
    left = abs(horntail_pose(turn, 170)['yaw'] - 90)
    if held > 2.0 or left > 2.0:
        problems.append('a hover between two ways: %.1f off the arrival while hovering, %.1f off the departure' % (held, left))
    print('dragon_engine heading: a hover then a speeding departure %.2f degrees off her way at most; a hover between '
          'two ways %.2f off the arrival, %.2f off the departure' % (worst, held, left))
    return problems


def neck_counter_check():
    """In a banked turn her head rolls less than her body: the neck counters the whole-body roll (the head's left
    axis is at most half as far off the horizontal as without the counter)."""
    sc = _case_scenes()['banked']
    h = sc['actors'][0]
    t = 110
    pose = horntail_pose(h, t)
    if abs(pose['roll']) < 5:
        return 'the banked case does not bank (%.1f)' % pose['roll']

    def head_side(roll_in):
        st = horntail_cue(h, t)
        m = part_matrices(model_pose(st, t, wing_phase(h, t), HOVER_AMP, (0.0, 0.0), roll_in))
        left = E._mat_vec(pose['m'], _local_dir(model_dir(m, 'head', [1.0, 0.0, 0.0])))
        return abs(left[1])                     # the head's left axis off the horizontal
    if not head_side(neck_roll(pose['roll'])) < 0.5 * head_side(0.0):
        return 'the neck does not counter her roll (%.3f vs %.3f)' % (head_side(neck_roll(pose['roll'])), head_side(0.0))
    return None


# ================================================================ DragonFire (the `breathe` action)

FIRE_ACTION = 'breathe'
FIRE_SPEED = 1.2                        # blocks a tick, the gas and the jet's front
FIRE_MAX_REACH = 18.0
FIRE_MIN_DURATION, FIRE_MAX_DURATION, FIRE_DEFAULT_DURATION = 10, 80, 40
FIRE_RISE, FIRE_GUTTER = 3.0, 8.0
SCORCH_MAX, SCORCH_TICKS, EMBER_TICKS = 2.5, 30.0, 60.0
AIR_BREATH_OPEN = AIR_BREATH_WINDUP     # the jaw opens wide at this tick of an AIR_BREATH
FIRE_SPLASH_TICKS = 10.0                # DragonFire.SPLASH_TICKS
SPLASH_SPREAD = 3.2                     # DragonFire.SPLASH_SPREAD: how far a splash spreads along the surface (scale 1)
SPLASH_RAY_LIFT = 0.3                   # DragonFire.SPLASH_RAY_LIFT: the splash ray starts this far off the face
SPLASH_WALL_GAP = 0.25                  # DragonFire.SPLASH_WALL_GAP: a splash stops this far short of a block
SPLASH_ANGLE = 0.75 * math.pi           # a puff's splash angle lies within +-this of the jet's run (Look.splash)
SPLASH_SIZE = 1.2                       # the largest Look.size
FIRE_CASES = os.path.join(HERE, 'dragon_fire_cases.json')


def fire_front(age):
    """DragonFire.front: how far the jet reaches `age` ticks into the breathe."""
    return 0.0 if age <= 0 else min(FIRE_MAX_REACH, age * FIRE_SPEED)


def fire_strength(age, duration):
    """DragonFire.strength: up over 3 ticks, full, guttering over the last 8."""
    if age < 0 or age > duration:
        return 0.0
    return min(smooth(age / FIRE_RISE), smooth((duration - age) / FIRE_GUTTER))


def scorch_radius(hit_age):
    """DragonFire.scorchRadius: 0 to 2.5 blocks over 30 ticks of hit, easing out."""
    x = min(1.0, max(0.0, hit_age / SCORCH_TICKS))
    return SCORCH_MAX * (1 - (1 - x) * (1 - x))


def smoke_rate(age, duration):
    """DragonFire.smokeRate: the share of puffs born at `age` that leave smoke."""
    gutter = 1 - smooth((duration - age) / FIRE_GUTTER)
    return min(1.0, 0.35 + 0.4 * smooth(age / 30.0) + 0.6 * gutter)


def fire_radius(s):
    """DragonFire.radius: a puff's radius (scale 1) `s` blocks out."""
    return 0.24 + 0.095 * max(0.0, s)


def fire_visible_radius(s):
    """DragonFire.visibleRadius: how far from the centre line the flame shows, `s` blocks out (scale 1)."""
    return 2.1 * fire_radius(s)


def fire_window(actor, t):
    """DragonFire.fireWindow: (t0, t1), the ticks the BREATH or AIR_BREATH cue in force at t has her jaw open at full
    fire (from the cue plus its fade-in, default 6, at least 12 ticks in for an AIR_BREATH; to the end of the one-shot
    or the next cue), or None when neither is in force."""
    st = horntail_cue(actor, t)
    if st['name'] not in ('BREATH', 'AIR_BREATH'):
        return None
    cue_t = t - st['time']
    cues = sorted(actor.get('anims') or [], key=lambda c: c['t'])
    cue, nxt = None, E.INF
    for c in cues:
        if cue is None and abs(c['t'] - cue_t) < 1e-4 and c['name'].upper() == st['name']:
            cue = c
        elif cue is not None and c['t'] > cue['t']:
            nxt = c['t']
            break
    if cue is None:
        return None
    blend = cue.get('blend', 0) or DEFAULT_BLEND
    opened = max(AIR_BREATH_OPEN, blend) if st['name'] == 'AIR_BREATH' else blend
    return cue['t'] + opened, min(cue['t'] + DURATIONS[st['name']], nxt)


def breathes(actor):
    """The actor's breathe actions as (t, duration, target), in time order."""
    out = []
    for a in actor.get('actions') or []:
        if a.get('do') == FIRE_ACTION:
            out.append((a['t'], a.get('duration', FIRE_DEFAULT_DURATION), a.get('target')))
    return sorted(out, key=lambda b: b[0])


def fire_target(scene, target):
    """A breathe target as a scene point: [x, y, z] as given; "actor:<id>" its chest (its path at t, plus 0.65 x a
    player's 1.8 height, x scale), as DragonFireRenderer aims it; None for no target. Takes t for an actor. An actor
    target must be a player (SceneValidator.breathe), so the height is the player's."""
    if target is None:
        return None
    if isinstance(target, list):
        return lambda t: list(target)
    aid = target.split(':', 1)[1]
    other = E.actor_by_id(scene, aid)
    if other.get('type') != 'player':
        raise ValueError('a breathe actor target must be a player actor, not %s' % other.get('type'))
    h = 1.8

    def at(t):
        p = E.sample_path(other.get('path') or [], t)
        return [p[0], p[1] + 0.65 * h * other.get('scale', 1.0), p[2]]
    return at


def _norm(v):
    n = math.sqrt(sum(c * c for c in v))
    return [c / n for c in v] if n > 1e-9 else [0.0, 0.0, 1.0]


def fire_jet(scene, aid, t, world, step=0.05):
    """(origin, dir, length) of Horntail `aid`'s fire at tick t, scene space: from her mouth (horntail_snout) toward
    the breathe's target (else along her head), its length the front (fire_front) cut at the first solid cell
    (scene_engine.shape_hit in `world`, marched in `step`s); None when no breathe is emitting at t. The drawn jet bends
    where her head sweeps (each puff keeps its own birth ray) and widens to fire_visible_radius: this is its centre
    line now, the right thing for aiming and hits."""
    actor = E.actor_by_id(scene, aid)
    for t0, dur, target in breathes(actor):
        if not t0 <= t <= t0 + dur:
            continue
        o, hd = horntail_snout(scene, aid, t)
        to = fire_target(scene, target)
        d = hd
        if to is not None:
            p = to(t)
            v = [p[i] - o[i] for i in range(3)]
            if math.sqrt(sum(c * c for c in v)) >= 0.5:
                d = _norm(v)
        hit = ray_hit(world, o, d, fire_front(t - t0), step)
        return o, d, hit[0]
    return None


def ray_hit(world, o, d, reach, step=0.05):
    """(distance, normal) to the first solid cell along the ray from o (scene_engine.shape_hit, marched in `step`s),
    or (reach, None) when it meets nothing within `reach`. The normal is the face it came in through: the axis whose
    move alone carries the last free point into the block, else the ray's largest axis (a unit axis vector)."""
    s, prev = 0.0, list(o)
    while s < reach:
        p = [o[i] + d[i] * s for i in range(3)]
        if E.shape_hit(world, p) is not None:
            axis = None
            for i in (1, 0, 2):
                q = list(prev)
                q[i] = p[i]
                if E.shape_hit(world, q) is not None:
                    axis = i
                    break
            if axis is None:
                axis = max(range(3), key=lambda i: abs(d[i]))
            n = [0.0, 0.0, 0.0]
            n[axis] = -1.0 if d[axis] > 0 else 1.0
            return s, n
        prev = p
        s += step
    return reach, None


def _cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def fire_basis(d):
    """DragonFire.basis: (p1, p2), p1 horizontal across d (unless d is vertical), p2 = p1 x d."""
    a = _cross(d, [0.0, 1.0, 0.0])
    if math.sqrt(sum(c * c for c in a)) < 1e-4:
        a = _cross(d, [1.0, 0.0, 0.0])
    p1 = _norm(a)
    return p1, _cross(p1, d)


def splash_dir(d, n, angle):
    """DragonFire.splashDir: the way a puff with splash angle `angle` spreads along a face of normal n hit by a jet
    along d: the jet's run over the surface, turned within the face by the angle."""
    dn = sum(d[i] * n[i] for i in range(3))
    e1 = [d[i] - n[i] * dn for i in range(3)]
    if math.sqrt(sum(c * c for c in e1)) < 0.2:
        e1 = fire_basis(d)[0]
    else:
        e1 = _norm(e1)
    e2 = _cross(n, e1)
    cs, sn = math.cos(angle), math.sin(angle)
    return [e1[i] * cs + e2[i] * sn for i in range(3)]


def splash_reach(world, hit_point, n, e, scale, clamp=True):
    """DragonFire.splashReach of the renderer's splash ray: the free run along e from hit + n x SPLASH_RAY_LIFT, up
    to SPLASH_SPREAD x scale, less SPLASH_WALL_GAP (infinity when it meets nothing). `clamp` False: always infinity,
    the splash before this fix (for the mutation check)."""
    if not clamp:
        return E.INF
    start = [hit_point[i] + n[i] * SPLASH_RAY_LIFT for i in range(3)]
    reach = SPLASH_SPREAD * scale
    free, nn = ray_hit(world, start, e, reach)
    if nn is None:
        return E.INF
    return max(0.0, free - SPLASH_WALL_GAP)


def splash_centre(o, d, hit, n, angle, b, scale, reach, size=1.0):
    """DragonFire.flame for a SPLASHING puff `b` ticks into its splash: (centre, radius). Its spread along the face
    is held to `reach`."""
    e = splash_dir(d, n, angle)
    spread = min(SPLASH_SPREAD * scale * (1 - math.exp(-b / 3.5)), max(0.0, reach))
    lift = (0.25 + 0.06 * b) * scale
    c = [o[i] + d[i] * hit + n[i] * lift + e[i] * spread for i in range(3)]
    c[1] += 0.012 * b * b
    return c, fire_radius(hit) * scale * size * (1 + 0.09 * b)


def splash_centres(world, o, d, hit, n, scale, clamp=True, angles=13, steps=11):
    """Where the splash of a jet along d from o that hits at `hit` on a face of normal n can be drawn: (centre, half
    size) over the splash angles and its ten ticks, each centre's spread held by its own splash ray (as the renderer
    does at a puff's birth). The half size is the drawn tongue's (DragonFireRenderer.billows: 1.4 x 1.1 x the
    radius, the largest look), its centre raised by 0.35 of it."""
    hp = [o[i] + d[i] * hit for i in range(3)]
    out = []
    for k in range(angles):
        ang = -SPLASH_ANGLE + 2 * SPLASH_ANGLE * k / (angles - 1)
        reach = splash_reach(world, hp, n, splash_dir(d, n, ang), scale, clamp)
        for j in range(steps):
            b = FIRE_SPLASH_TICKS * j / (steps - 1)
            c, r = splash_centre(o, d, hit, n, ang, b, scale, reach, SPLASH_SIZE)
            half = 1.4 * 1.1 * r
            c[1] += 0.35 * half
            out.append((c, half))
    return out


def fire_clearance(scene, aid, t, world, point, clamp=True):
    """How far `point` is outside the drawn flame of the jet at t, or None when no jet: what a 'the fire never
    touches the viewer' assert compares. The lesser of the distance to the centre line minus the visible radius
    there (x scale), and where the jet has hit a block, the distance to its splash (splash_centres: each tongue's
    centre less its half size). `clamp` False measures the splash as it was before it was held off the rock."""
    jet = fire_jet(scene, aid, t, world)
    if jet is None:
        return None
    o, d, length = jet
    v = [point[i] - o[i] for i in range(3)]
    s = max(0.0, min(length, sum(v[i] * d[i] for i in range(3))))
    q = [o[i] + d[i] * s for i in range(3)]
    dist = math.sqrt(sum((point[i] - q[i]) ** 2 for i in range(3)))
    scale = E.actor_by_id(scene, aid).get('scale', 1.0)
    best = dist - fire_visible_radius(s) * scale
    hit, n = ray_hit(world, o, d, length + 0.1)
    if n is not None and hit <= length + 1e-6:
        for c, half in splash_centres(world, o, d, hit, n, scale, clamp):
            best = min(best, math.dist(c, point) - half)
    return best


def _f5(v):
    return round(v, 5)


def fire_cases():
    """The parity cases for DragonFireTest.pythonMirrorAgrees."""
    ages = [-1, 0, 0.5, 1.5, 3, 5, 14.9, 15, 30, 36, 39.5, 40, 41]
    anims = {
        'ground': [{'t': 10, 'name': 'BREATH_WINDUP'}, {'t': 32, 'name': 'BREATH'}, {'t': 120, 'name': 'IDLE'}],
        'blended': [{'t': 0, 'name': 'IDLE'}, {'t': 20, 'name': 'BREATH', 'blend': 9}, {'t': 50, 'name': 'ROAR'}],
        'air': [{'t': 0, 'name': 'FLY'}, {'t': 30, 'name': 'AIR_BREATH'}, {'t': 100, 'name': 'AIR_BREATH', 'blend': 14},
                {'t': 140, 'name': 'FLY'}],
    }
    windows = []
    for name, cues in anims.items():
        for t in (0, 15, 25, 33, 40, 60, 77, 79, 85, 101, 115, 125, 139, 150):
            w = fire_window({'anims': cues}, t)
            windows.append({'anims': name, 't': t, 'window': None if w is None else [_f5(w[0]), _f5(w[1])]})
    return {
        'constants': {'speed': FIRE_SPEED, 'max_reach': FIRE_MAX_REACH, 'scorch_max': SCORCH_MAX,
                      'scorch_ticks': SCORCH_TICKS, 'ember_ticks': EMBER_TICKS, 'min_duration': FIRE_MIN_DURATION,
                      'max_duration': FIRE_MAX_DURATION, 'default_duration': FIRE_DEFAULT_DURATION,
                      'air_breath_open': AIR_BREATH_OPEN},
        'front': [[a, _f5(fire_front(a))] for a in ages],
        'strength': [[a, d, _f5(fire_strength(a, d))] for d in (10, 40, 80) for a in ages],
        'scorch': [[h, _f5(scorch_radius(h))] for h in (-2, 0, 1, 5, 15, 29, 30, 45)],
        'smoke': [[a, d, _f5(smoke_rate(a, d))] for d in (10, 40) for a in ages],
        'radius': [[s, _f5(fire_radius(s)), _f5(fire_visible_radius(s))] for s in (0, 3, 10, 18)],
        'anims': anims,
        'windows': windows,
        'splash_constants': {'spread': SPLASH_SPREAD, 'ray_lift': SPLASH_RAY_LIFT, 'wall_gap': SPLASH_WALL_GAP},
        'splashes': splash_cases(),
        'mouth_scenes': _mouth_scenes(),
        'mouths': mouth_cases(),
    }


def splash_cases():
    """DragonFire.splashDir and a SPLASHING flame's centre for given aims, faces, angles, ticks and reaches."""
    out = []
    aims = [([0.0, -0.3, 1.0], [0.0, 0.0, -1.0]), ([0.2, -0.9, 0.4], [0.0, 1.0, 0.0]), ([0.0, 0.0, 1.0], [0.0, 0.0, -1.0]),
            ([-0.6, -0.5, 0.2], [1.0, 0.0, 0.0])]
    for d, n in aims:
        d = _norm(d)
        for ang in (-2.0, -0.4, 0.0, 1.1, 2.3):
            for b, reach in ((0.5, E.INF), (4.0, 1.2), (9.0, E.INF), (9.0, 0.0)):
                c, r = splash_centre([1.5, 6.0, -2.0], d, 7.25, n, ang, b, 1.3, reach, 0.9)
                out.append({'d': d, 'n': n, 'angle': ang, 'b': b,
                            'reach': None if reach == E.INF else reach,
                            'dir': [_f5(x) for x in splash_dir(d, n, ang)], 'centre': [_f5(x) for x in c],
                            'radius': _f5(r)})
    return out


def _mouth_scenes():
    """Small scenes for the mouth estimate's parity: a ground breath with a head look sweeping, an air breath, a
    flight and a rest pose, scaled."""
    def dragon(path, anims, head, scale=1.0):
        return {'length': 200, 'actors': [{'id': 'h', 'type': HORNTAIL_TYPE, 'path': path, 'anims': anims,
                                           'limbs': {HEAD_LOOK: {'rot': head}}, 'scale': scale}]}
    look = [{'t': 0, 'v': [0, 0, 0]}, {'t': 40, 'v': [20, 45, 0]}, {'t': 80, 'v': [-30, -60, 0]}]
    fly = _spline([[0.0, 12.0, 0.6 * 10 * i] for i in range(0, 21)], 0, 10, None)
    return {
        'breath': dragon([{'t': 0, 'pos': [3.0, 4.0, -2.0]}], [{'t': 0, 'name': 'IDLE'}, {'t': 10, 'name': 'BREATH'}],
                         look),
        'air': dragon(fly, [{'t': 0, 'name': 'FLY'}, {'t': 10, 'name': 'AIR_BREATH'}], look, 1.2),
        'fly': dragon(fly, [{'t': 0, 'name': 'FLY'}], look),
        'rest': dragon([{'t': 0, 'pos': [0.0, 0.0, 0.0]}], [{'t': 0, 'name': 'IDLE'}], look),
    }


MOUTH_TIMES = (12, 30, 47.5, 66, 80)


def mouth_cases():
    out = []
    for name, sc in _mouth_scenes().items():
        h = sc['actors'][0]
        for t in MOUTH_TIMES:
            p, d = mouth_estimate(h, t)
            q, dq = horntail_snout(sc, 'h', t)
            w = fire_window(h, t)
            settled = horntail_blend(h, t)[2] >= 1.0 and (name not in ('breath', 'air') or
                                                          (w is not None and w[0] <= t <= w[1]))
            out.append({'scene': name, 't': t, 'mouth': [_f5(x) for x in p], 'dir': [_f5(x) for x in d],
                        'snout': [_f5(x) for x in q], 'settled': settled})
    return out


def mouth_estimate_check():
    """The estimate stays within 0.5 of the drawn snout over the mouth scenes (worst gap, where), wherever she can
    breathe: inside the breath scenes' fire windows, anywhere once settled in the others. (Inside a cue's fade it
    does not follow the cross-fade.)"""
    worst = (0.0, None)
    for name, sc in _mouth_scenes().items():
        h = sc['actors'][0]
        for t in range(10, 100, 3):
            w = fire_window(h, t)
            if name in ('breath', 'air') and (w is None or not w[0] <= t <= w[1]) or horntail_blend(h, t)[2] < 1.0:
                continue
            p, _ = mouth_estimate(h, t)
            q, _ = horntail_snout(sc, 'h', t)
            g = math.dist(p, q) / h.get('scale', 1.0)
            if g > worst[0]:
                worst = (g, '%s t %d' % (name, t))
    return worst


def write_fire_cases():
    from scene_common import dump
    data = fire_cases()
    with open(FIRE_CASES, 'w', newline='\n', encoding='utf-8') as f:
        f.write(dump(data, 0, 150) + '\n')
    print('wrote', FIRE_CASES, len(data['windows']), 'windows')


def fire_self_check():
    problems = []
    if not (fire_front(5) == 6.0 and fire_front(100) == FIRE_MAX_REACH):
        problems.append('fire front')
    if not (fire_strength(20, 40) == 1.0 and fire_strength(40, 40) == 0.0 and fire_strength(36, 40) < 1.0):
        problems.append('fire strength')
    if abs(scorch_radius(30) - SCORCH_MAX) > 1e-9:
        problems.append('scorch radius')
    w = fire_window({'anims': [{'t': 0, 'name': 'FLY'}, {'t': 30, 'name': 'AIR_BREATH'}]}, 50)
    if w != (42, 82):
        problems.append('air breath window %s' % (w,))
    gap, where = mouth_estimate_check()
    if gap > 0.5:
        problems.append('the mouth estimate is %.2f from the snout (%s)' % (gap, where))
    # A splash on the floor just before a wall is held off it; unheld it would run on into the wall.
    world = {(x, y, z): 'minecraft:stone' for x in range(-6, 7) for z in range(-4, 12) for y in (-1,)}
    world.update({(x, y, 3): 'minecraft:stone' for x in range(-6, 7) for y in range(0, 4)})
    o, d = [0.5, 6.0, -8.0], _norm([0.0, -6.0, 9.5])
    hit, n = ray_hit(world, o, d, FIRE_MAX_REACH)
    held = max(c[2] for c, _ in splash_centres(world, o, d, hit, n, 1.0))
    loose = max(c[2] for c, _ in splash_centres(world, o, d, hit, n, 1.0, clamp=False))
    if n != [0.0, 1.0, 0.0] or not held < 3.0 <= loose:
        problems.append('the splash clamp: normal %s, held to z %.2f, unheld %.2f (the wall at z 3)' % (n, held, loose))
    print('dragon_engine fire: the mouth estimate within %.2f of the snout (%s); a floor splash before a wall held to '
          'z %.2f (unheld %.2f, the wall at 3)' % (gap, where, held, loose))
    return problems


if __name__ == '__main__':
    if '--write-cases' in sys.argv:
        write_cases()
    elif '--write-fire-cases' in sys.argv:
        write_fire_cases()
    else:
        pr = self_check() + fire_self_check()
        for x in pr:
            print('PROBLEM', x)
        sys.exit(1 if pr else 0)
