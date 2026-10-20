"""Generates the Sorting memory: memories/sorting.json (the mind intro) and memories/sorting_hall.json (the Great Hall).

Usage (from the repo root): python -I build_scripts/pensieve_scenes/great_hall_scene.py

Putting the Sorting Hat on plays `sorting` (SortingHatManager.MEMORY_SCENE), then the chain plays `sorting_hall`, then
the voiced quiz.

  sorting (12 s): inside your own mind. A black void; the boy (the viewer's own skin, a first-year at 0.75) sits on
      a lone chair in a soft pool of light while silver wisps drift; the eyes open from black, the camera orbits
      slowly, and the Hat speaks ("Let us begin where every sorting begins"). It chains into sorting_hall.
  sorting_hall (~108 s): the entrance hall among the first-years; McGonagall at the doors; the doors open like an
      animated film (anticipation, stagger, slow crack, heavy swing 1.4x the Addendum C timings, overshoot and settle,
      a boom with shake and dust, soft god-rays at intensity 0.45); the crane up through the candles as the storm
      ceiling dissolves in; the staff table; the procession; Dumbledore; Hermione and Ron sorted, their table cheering
      only once the hat's shout has finished; silence, the name large on screen; the boy walks up, sits, and the hat
      comes down in first person.

Feedback round 1 (plan Addendum 3): every walker is routed through free floor (a lane on the Gryffindor side of the
aisle keeps the walk to the tables clear of the waiting first-years); every student wears a unique skin; every
actor has its own desynchronised idle life (glances at neighbours, head turns, body shifts, talking gestures,
leaning), and group reactions are staggered over 1.5-3 s with some late and some not reacting at all; slower shots
(4-7 s holds).

Asserts: scene JSON under the 900,000-char cap; every camera key and straight camera move in an air cell of the set;
unique actor ids; eyes_close the last event of the hall; no moving actor within 0.6 blocks (horizontal) of any other
actor at any tick (sampled every 2 ticks); no walker's feet or head in a table, bench or any other solid cell.
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import write_scene, FREE, MEMORIES  # noqa: E402
import great_hall_set as GH  # noqa: E402

A = GH.ANCHORS
MAX_JSON = 900000
HALL_ID, INTRO_ID = 'sorting_hall', 'sorting'

KID, OLD, ADULT, GIANT = 0.75, 0.9, 1.0, 1.5
EYE = 1.62                       # player eye height at scale 1
LEG = 0.75                       # player leg length at scale 1 (12 px)

HAT = 'hogwartsmagic:sorting_hat'
SFX = 'hogwartsmagic:pensieve.sfx.hall_'
VOICE = 'hogwartsmagic:pensieve.voice.hall_'
MUSIC = 'hogwartsmagic:pensieve.music.hall_'
SHOUT = 'hogwartsmagic:sorting.voice.gryffindor'
SHOUT_LEN = 2.68                 # sounds/sorting/gryffindor.ogg (ffprobe 2.6805 s): tables cheer only after it


def tk(s):
    return int(round(s * 20))


def r2(v):
    return round(v, 2)


def yaw_to(dx, dz):
    """Minecraft yaw facing along (dx, dz): 0 = +Z, 90 = -X, -90 = +X, 180 = -Z."""
    return math.degrees(math.atan2(-dx, dz))


def wrap(d):
    return (d + 180.0) % 360.0 - 180.0


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def seat_root(seat_top, scale):
    """A seated actor's feet root: the scaled leg hangs forward, so the hips sit on the seat top."""
    return round(seat_top - LEG * scale, 4)


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


# ================================================================ shots (start times, seconds)
SHOTS = [('A', 4.6), ('B1', 4.6), ('B2', 4.8), ('C1', 4.4), ('C2', 5.0), ('C3', 4.0), ('D1', 7.0), ('D2', 4.2),
         ('D3', 5.4), ('E1', 4.6), ('E2', 4.4), ('E3', 6.4), ('F1', 4.2), ('F2', 4.8), ('F3', 5.0), ('G1', 4.4),
         ('G2', 6.2), ('H1', 4.4), ('H2', 5.0), ('I1', 6.0), ('I3', 4.2), ('J', 4.8)]
S = {}
_t = 0.0
for _n, _d in SHOTS:
    S[_n] = round(_t, 2)
    _t += _d
LENGTH = round(_t, 2)            # 108.4

# ================================================================ beat sheet (seconds)
T_MCG_IN = 4.4        # McGonagall reaches the doors
T_MCG_LINE = 5.6      # hall_mcg_doors, 7.54 s
T_FOLLOW = 13.4       # "Follow me": she turns to the doors
T_RON_LINE = 14.6     # hall_ron_nervous, 1.59 s
T0 = 16.6             # the doors: lock thud, first groan, the south leaf starts (north 4 ticks later)
DOOR_KEYS = [(0, 0, None), (8, -2, 'in_out'), (36, 10, 'in_out'), (76, 92, 'in'), (81, 86, 'out'),
             (90, 90, 'out')]     # Addendum C at 1.4x: anticipation, crack, heavy swing, overshoot, settle
BOOM = T0 + 76 / 20   # 20.4: the south leaf hits 92 degrees
T_WALK = 23.6         # McGonagall leads in (2.6 blocks/s)
T_CEIL = S['D1'] + 2.6  # enchanted_ceiling reveal (4 s) and ceiling_shimmer, as the crane tilts up
T_JUMP = S['D2'] + 0.5  # the procession's hidden jump (the camera is on the staff table)
T_LEAD_END = 41.5     # McGonagall at the foot of the dais
T_HERM_LINE = 39.4    # hall_hermione_nervous, 3.78 s
T_ARRIVE = 43.6       # first-years at their places
T_HUSH = 44.2
T_DUMB = 47.2         # hall_dumbledore, 3.84 s
T_CALL = 53.2         # hall_mcg_call, 6.19 s
T_GRANGER = 59.8      # hall_mcg_granger, 1.94 s
T_HAT_H = 64.4
T_SHOUT_H = 65.6
T_WEASLEY = 73.8      # hall_mcg_weasley, 1.95 s
T_HAT_R = 77.4
T_SHOUT_R = 78.8
T_SCROLL = 84.2       # McGonagall looks down at the scroll
T_NAME = 88.6         # the name card (size 3, 4.5 s), whispers, prologue music
T_BOY_WALK = 93.6
T_BOY_SIT = 99.1
T_HAT_B = 106.4       # the hat comes down
T_CLOSE = 106.3       # eyes_close to the end
CHEER_H = T_SHOUT_H + SHOUT_LEN + 0.05
CHEER_R = T_SHOUT_R + SHOUT_LEN + 0.05

# ================================================================ places (scene coordinates)
HINGE_N, HINGE_S = A['doors_hinge_n'], A['doors_hinge_s']          # [65, 0, -3.5], [65, 0, 4.5]
DOOR_SCALE = A['door_leaf_scale']
DOOR_FACE_X = 67.0               # the entrance-hall face of the portal (wall x 64..66)
STOOL = A['stool']               # [0.5, 1.5, 0.5] seat top
STOOL_ROOT_KID = [STOOL[0], seat_root(STOOL[1], KID), STOOL[2]]
MCG_STOOL = [0.5, 1.0, -0.8]     # on the top step beside the stool, close enough to reach a head
MCG_IN_FRONT = [2.5, 0, -0.75]   # where she stands for the boy's hat (it comes over the top of his view)
AISLE_Z = 0.5
WALK_Z = (-0.5, 1.5)             # the two files walking in (pre-jump)
FILE_Z = (0.35, 1.45)            # the two files after the jump and while waiting (cells z 0 and 1)
LANE_Z = -0.55                   # the free lane on the Gryffindor side: to the stool and to the tables
H_SEAT = [12.5, seat_root(0.5, KID), -1.5]           # Gryffindor aisle bench, faces -Z (yaw 180)
R_SEAT = [13.5, seat_root(0.5, KID), -1.5]

# ================================================================ scene parts
actors = []
events = []
words = []
camera = []
POSES = {
    'seated': {'right_leg': [-90, 8, 0], 'left_leg': [-90, -8, 0], 'right_arm': [-30, 0, 0],
               'left_arm': [-30, 0, 0]},
    'relax': {'head': [0, 0, 0]},
    'scroll': {'left_arm': [-55, 12, 0]},
    'scroll_read': {'left_arm': [-62, 14, 0], 'head': [28, 0, 0]},
    'welcome': {'right_arm': [-85, 0, 38], 'left_arm': [-85, 0, -38]},
    'hands_clasped': {'right_arm': [-38, -20, 0], 'left_arm': [-38, 20, 0]},
}
SIT_WINDOWS = {}                 # actor id -> [(t0, t1)] seconds when it sits down or stands up (not walking)


def ev(t, kind, eid, **kw):
    e = {'t': tk(t), 'kind': kind, 'id': eid}
    e.update(kw)
    events.append(e)


def sfx(t, name, volume=1.0):
    ev(t, 'sfx', SFX + name, volume=volume)


def voice(t, name, volume=1.0):
    ev(t, 'voice', VOICE + name, volume=volume)


# ---------------------------------------------------------------- a channel of keys over time
class Channel:
    """One animated value (a limb rotation, a body yaw): a list of changes, each easing from the value before it
    over dur (hold=True writes the old value at t first) or interpolating straight from the previous key (hold=
    False). Overlapping changes are pushed back so keys always run forward in time."""

    def __init__(self, v0):
        self.changes = [(0.0, v0, 0.0, True, 0)]
        self.n = 1

    def change(self, t, v, dur=0.4, hold=True):
        self.changes.append((t, v, dur, hold, self.n))
        self.n += 1

    def keys(self):
        ch = sorted(self.changes, key=lambda c: (c[0], c[4]))
        out = [(0, ch[0][1])]
        prev = ch[0][1]
        last = 0
        end = tk(LENGTH)
        for t, v, dur, hold, _ in ch[1:]:
            tt = tk(t)
            if tt >= end - 1:
                break
            if hold:
                if tt <= last:
                    tt = last + 1
                if tt >= end:
                    break
                out.append((tt, prev))
                last = tt
                te = tt + max(1, tk(dur)) if dur > 0 else tt
            else:
                te = max(tk(t + dur), last + 1)
            if te <= last:
                te = last + 1
            te = min(te, end)
            if te <= last:
                break
            out.append((te, v))
            last = te
            prev = v
        # drop repeats (a hold with the same value as the key before it)
        clean = []
        for t, v in out:
            if clean and clean[-1][0] == t:
                clean[-1] = (t, v)
            else:
                clean.append((t, v))
        return clean


def limb_json(channels):
    out = {}
    for part, ch in channels.items():
        ks = ch.keys()
        if len(ks) <= 1 and all(abs(c) < 0.5 for c in ks[0][1]):
            continue
        out[part] = {'rot': [{'t': t, 'v': [int(round(c)) for c in v]} for t, v in ks]}
    return out


class Life:
    """An actor's limbs: head, body, arms; all add on top of poses and look."""

    def __init__(self):
        self.ch = {}

    def __getitem__(self, part):
        if part not in self.ch:
            self.ch[part] = Channel([0, 0, 0])
        return self.ch[part]

    def emit(self):
        return limb_json(self.ch)


# ---------------------------------------------------------------- a moving actor
class Mover:
    """Path and look keys for a walking/standing actor. Look overrides movement facing, so every walk writes its own
    facing: a short turn into each segment's direction. Yaw stays continuous (unwrapped near the current yaw)."""

    def __init__(self, aid, pos, yaw):
        self.aid = aid
        self.pos = list(pos)
        self.yaw = yaw
        self.path = [(0.0, list(pos), None)]
        self.look = [(0.0, yaw)]

    def near(self, y):
        while y - self.yaw > 180:
            y -= 360
        while y - self.yaw < -180:
            y += 360
        return y

    def face(self, t, yaw, dur=0.4):
        y = self.near(yaw)
        self.look.append((t, self.yaw))
        self.look.append((t + dur, y))
        self.yaw = y

    def face_point(self, t, pt, dur=0.4):
        self.face(t, yaw_to(pt[0] - self.pos[0], pt[2] - self.pos[2]), dur)

    def go(self, t, pts, turn=0.25, end_ease='out', start_ease='linear'):
        """Hold until t, then through pts [(time, pos)], turning into each segment's direction."""
        self.path.append((t, list(self.pos), None))
        prev_t, prev = t, self.pos
        for i, (ti, p) in enumerate(pts):
            dx, dz = p[0] - prev[0], p[2] - prev[2]
            if math.hypot(dx, dz) > 0.08 and ti - prev_t > 0.1:
                y = self.near(yaw_to(dx, dz))
                if abs(y - self.yaw) > 1:
                    if i == 0:
                        self.look += [(prev_t, self.yaw), (prev_t + turn, y)]
                    else:
                        self.look += [(prev_t - turn / 2, self.yaw), (prev_t + turn / 2, y)]
                    self.yaw = y
            if len(pts) == 1:
                ease = 'in_out'
            elif i == len(pts) - 1:
                ease = end_ease
            elif i == 0:
                ease = start_ease
            else:
                ease = 'linear'
            self.path.append((ti, list(p), ease))
            prev_t, prev = ti, p
        self.pos = list(prev)

    def sit(self, t0, t1, p):
        """Moves into (or out of) a seat over t0..t1: not walking, so the cell check skips it."""
        self.path.append((t0, list(self.pos), None))
        self.path.append((t1, list(p), 'in_out'))
        self.pos = list(p)
        SIT_WINDOWS.setdefault(self.aid, []).append((t0, t1))

    def emit(self):
        out_p, last = [], -1
        for t, p, e in self.path:
            tt = tk(t)
            k = {'t': tt, 'pos': [r2(p[0]), round(p[1], 4), r2(p[2])]}
            if e and e != 'in_out':
                k['ease'] = e
            if out_p and tt == last:
                out_p[-1] = k
            else:
                assert tt > last, (self.aid, 'path out of order', t)
                out_p.append(k)
            last = tt
        out_l, last = [], -1
        for t, y in self.look:
            tt = tk(t)
            k = {'t': tt, 'yaw': round(y, 1)}
            if out_l and tt == last:
                out_l[-1] = k
            else:
                assert tt > last, (self.aid, 'look out of order', t, self.look)
                out_l.append(k)
            last = tt
        return out_p, out_l


def actor(aid, skin, scale, mover=None, path=None, look=None, life=None, pose_cues=None, actions=None, items=None,
          atype='player'):
    a = {'id': aid, 'type': atype}
    if skin is not None:
        a['skin'] = skin
    if scale != 1:
        a['scale'] = scale
    if items:
        a['items'] = items
    if mover is not None:
        path, look = mover.emit()
    if path:
        a['path'] = path
    if look:
        a['look'] = look
    if life is not None:
        lm = life.emit()
        if lm:
            a['limbs'] = lm
    if pose_cues:
        a['poses'] = [dict(p, t=tk(p['t'])) for p in sorted(pose_cues, key=lambda p: p['t'])]
    if actions:
        a['actions'] = [dict(x, t=tk(x['t'])) for x in sorted(actions, key=lambda x: x['t'])]
    actors.append(a)
    return a


def pose(t, name, blend=None):
    p = {'t': t, 'name': name}
    if blend:
        p['blend'] = blend
    return p


# ---------------------------------------------------------------- skins
SKINS = set(json.load(open(os.path.join(MEMORIES, '_skins.json'))))


def skin_pool(prefix, n, fallback):
    names = ['%s_%02d' % (prefix, i) for i in range(1, n + 1)]
    have = [s for s in names if s in SKINS]
    if len(have) < len(names):
        print('WARNING: %d of %d %s_NN skins missing from _skins.json' % (len(names) - len(have), n, prefix))
    return have + [s for s in fallback if s in SKINS]


# ---------------------------------------------------------------- idle life helpers
def gesture(life, rnd, t, side=None):
    """A talking gesture: one forearm lifts, turns a little and settles."""
    part = side or rnd.choice(['right_arm', 'left_arm'])
    s = 1 if part == 'right_arm' else -1
    x = rnd.uniform(-60, -30)
    z = s * rnd.uniform(4, 16)
    hold = rnd.uniform(0.6, 1.5)
    life[part].change(t, [x, 0, z], rnd.uniform(0.3, 0.5))
    life[part].change(t + 0.4 + hold * 0.5, [x - rnd.uniform(8, 18), s * rnd.uniform(-10, 10), z], 0.35, False)
    life[part].change(t + 0.9 + hold, [0, 0, 0], rnd.uniform(0.35, 0.6))
    return t + 1.5 + hold


def lean(life, rnd, t, dur):
    """Leans forward (onto the table, or just shifting), then back."""
    a = rnd.uniform(6, 13)
    life['body'].change(t, [a, rnd.uniform(-4, 4), 0], rnd.uniform(0.6, 1.0))
    life['body'].change(t + dur, [0, 0, 0], rnd.uniform(0.6, 1.0))


def free_windows(busy, lo, hi):
    """Gaps in [lo, hi] not covered by the busy intervals."""
    out, cur = [], lo
    for a, b in sorted(busy):
        if a > cur:
            out.append((cur, min(a, hi)))
        cur = max(cur, b)
        if cur >= hi:
            break
    if cur < hi:
        out.append((cur, hi))
    return [(a, b) for a, b in out if b - a > 0.3]


def react(rnd, base, spread=(1.5, 3.0), late=0.12, never=0.06):
    """A group reaction time for one actor: within a 1.5-3 s window, some late, some not at all (None)."""
    r = rnd.random()
    if r < never:
        return None
    d = rnd.uniform(0.05, rnd.uniform(*spread))
    if r < never + late:
        d += rnd.uniform(1.0, 2.5)
    return base + d


# ================================================================ the doors (Addendum C, 1.4x)
def door_actor(aid, atype, hinge, sign, lead_ticks):
    look = []
    for rel, th, e in DOOR_KEYS:
        k = {'t': tk(T0) + lead_ticks + rel, 'yaw': -90 + sign * th}
        if e and e != 'in_out':
            k['ease'] = e
        look.append(k)
    actor(aid, None, DOOR_SCALE, path=[{'t': 0, 'pos': list(hinge)}], look=look, atype=atype)


# low-z (north) leaf turns -90 -> -180; high-z (south, mirrored) leaf turns -90 -> 0. Seen from the entrance hall
# (looking -X) the south leaf is on the left: it leads by 4 ticks.
door_actor('door_north', 'hogwartsmagic:memory_door', HINGE_N, -1, 4)
door_actor('door_south', 'hogwartsmagic:memory_door_mirrored', HINGE_S, +1, 0)

# ================================================================ McGonagall
mcg = Mover('mcgonagall', [69.0, 0, -9.0], 0)
mcg.go(0.3, [(T_MCG_IN, [68.5, 0, 0.5])], start_ease='in_out')
mcg.face(T_MCG_IN + 0.2, -90, 0.8)                       # turns to the first-years
mcg.face(T_FOLLOW, 90, 0.7)                              # "Follow me": back to the doors
MCG_SPEED = 2.6
X_27 = 68.5 - MCG_SPEED * (S['D1'] - T_WALK)
X_JUMP = 68.5 - MCG_SPEED * (T_JUMP - T_WALK)
X_AFTER = 6.5 + MCG_SPEED * (T_LEAD_END - T_JUMP - 0.05)   # back to walking pace after the jump
MCG_ROUTE = [(S['D1'], [X_27, 0, AISLE_Z]), (T_JUMP, [X_JUMP, 0, AISLE_Z]),
             (T_JUMP + 0.05, [X_AFTER, 0, AISLE_Z]),     # hidden jump: the camera is on the staff table
             (T_LEAD_END, [X_AFTER - MCG_SPEED * (T_LEAD_END - T_JUMP - 0.05), 0, AISLE_Z]),
             (T_LEAD_END + 1.4, [2.45, 0, 0.5]), (T_LEAD_END + 1.8, [1.9, 1, 0.3]), (T_LEAD_END + 2.6, MCG_STOOL)]
mcg.go(T_WALK, MCG_ROUTE, start_ease='in')
mcg.face(T_LEAD_END + 2.7, -90, 0.6)                     # faces the first-years at the stool
_MCG_X = [(T_WALK, 68.5)] + [(t, p[0]) for t, p in MCG_ROUTE[:4]]


def mcg_x(t):
    """McGonagall's x along the aisle while she leads (piecewise linear, the jump included)."""
    if t <= _MCG_X[0][0]:
        return _MCG_X[0][1]
    for (t0, x0), (t1, x1) in zip(_MCG_X, _MCG_X[1:]):
        if t <= t1:
            return x0 + (x1 - x0) * (t - t0) / (t1 - t0)
    return _MCG_X[-1][1]


def mcg_x_inv(x, lo, hi):
    a, b = lo, hi
    for _ in range(40):
        m = (a + b) / 2
        if mcg_x(m) > x:
            a = m
        else:
            b = m
    return (a + b) / 2


mcg_life = Life()
mcg_actions = [{'t': S['E1'], 'do': 'equip', 'slot': 'mainhand', 'item': HAT}]


def mcg_crown(t_on):
    """The hat goes on the head on the stool at t_on: she turns to it, lifts the hat, lowers it, turns back."""
    mcg.face(t_on - 1.2, 0, 0.5)
    arm = mcg_life['right_arm']
    arm.change(t_on - 0.9, [-125, 0, -8], 0.6)
    arm.change(t_on + 0.1, [-95, 0, -8], 0.3, False)
    arm.change(t_on + 0.4, [0, 0, 0], 0.6)
    mcg_actions.append({'t': t_on, 'do': 'equip', 'slot': 'mainhand', 'item': 'minecraft:air'})
    mcg.face(t_on + 1.0, -90, 0.6)


def mcg_take_back(t):
    mcg.face(t - 0.8, 0, 0.45)
    arm = mcg_life['right_arm']
    arm.change(t - 0.45, [-110, 0, -8], 0.4)
    arm.change(t + 0.2, [0, 0, 0], 0.5)
    mcg_actions.append({'t': t, 'do': 'hold', 'item': HAT})
    mcg.face(t + 0.5, -90, 0.6)


mcg_crown(T_HAT_H)
mcg_take_back(CHEER_H + 1.0)
mcg_crown(T_HAT_R)
mcg_take_back(CHEER_R + 1.0)
# she reads the name, looks up at the boy, and steps down in front of him for the hat
mcg_life['head'].change(T_NAME + 1.8, [-20, -10, 0], 0.7)
mcg_life['head'].change(T_BOY_SIT + 0.1, [-28, 0, 0], 0.5)
mcg.go(T_BOY_SIT + 0.6, [(T_BOY_SIT + 1.4, [1.9, 1, -0.85]), (T_BOY_SIT + 1.8, [2.45, 0, -0.8]),
                          (T_BOY_SIT + 2.1, MCG_IN_FRONT)], start_ease='in')
mcg.face_point(T_BOY_SIT + 2.1, STOOL, 0.6)
mcg_life['right_arm'].change(S['J'] + 0.8, [-150, 0, -10], 1.0)       # the hat high over him, into his view
mcg_life['right_arm'].change(T_HAT_B, [-125, 0, -8], 0.4, False)
mcg_life['right_arm'].change(T_HAT_B + 0.2, [-105, 0, -8], 0.4, False)
mcg_actions.append({'t': T_HAT_B, 'do': 'equip', 'slot': 'mainhand', 'item': 'minecraft:air'})
# small life while she waits: a glance along the line, a turn of the head at the hall
rnd_m = random.Random(41)
for t in (8.5, 11.2, 30.5, 45.6, 56.5, 71.5, 83.0):
    mcg_life['head'].change(t, [rnd_m.uniform(-6, 6), rnd_m.uniform(-25, 25), 0], 0.6)
    mcg_life['head'].change(t + rnd_m.uniform(1.2, 2.2), [0, 0, 0], 0.6)

# ================================================================ the first-years
# Twelve in two files in the entrance hall (pairs 0..5, file A on the -z side). The boy and Ron are the front pair
# (Ron on the boy's +z side), Hermione right behind. After the hidden jump the files re-form: Hermione and Ron first,
# the boy last in file A.
rnd_k = random.Random(1991)
SPOT = {}
pre = [('boy', 'ron'), ('hermione', 'k1'), ('k2', 'k3'), ('k4', 'k5'), ('k6', 'k7'), ('k8', 'k9')]
post = [('hermione', 'ron'), ('k1', 'k2'), ('k3', 'k4'), ('k5', 'k6'), ('k7', 'k8'), ('boy', 'k9')]
for p, pair in enumerate(pre):
    for c, kid in enumerate(pair):
        SPOT[kid] = [round(70.6 + 1.3 * p + rnd_k.uniform(-0.15, 0.15), 2), 0,
                     round((-0.6, 1.6)[c] + rnd_k.uniform(-0.12, 0.12), 2)]


def pair_of(kid, pairs):
    for p, pr in enumerate(pairs):
        if kid in pr:
            return p, pr.index(kid)
    raise KeyError(kid)


def slot(kid):
    p, c = pair_of(kid, post)
    return [3.8 + 1.4 * p, 0, FILE_Z[c]]


KIDS = [k for pr in pre for k in pr]
kid_m = {k: Mover(k, SPOT[k], 90) for k in KIDS}
kid_life = {k: Life() for k in KIDS}
for k in KIDS:
    m = kid_m[k]
    m.face_point(T_MCG_IN - 0.6 + rnd_k.uniform(0, 1.2), [68.5, 0, 0.5], 0.6)
    p, c = pair_of(k, pre)
    off = 2.1 + 1.3 * p
    zc = WALK_Z[c]
    t_start = T_WALK + 0.15 * p + rnd_k.uniform(0, 0.15)
    t_door = mcg_x_inv(67.5 - off, T_WALK, T_JUMP)
    pts = [(t_door, [67.5, 0, zc])]
    for tt in (S['D1'], 31.0):
        if tt > t_door + 0.4:
            pts.append((tt, [mcg_x(tt) + off, 0, zc]))
    pts.append((T_JUMP, [mcg_x(T_JUMP) + off, 0, zc]))
    p2, c2 = pair_of(k, post)
    off2 = 2.1 + 1.3 * p2
    pts.append((T_JUMP + 0.05, [mcg_x(T_JUMP + 0.05) + off2, 0, FILE_Z[c2]]))
    pts.append((T_LEAD_END, [mcg_x(T_LEAD_END) + off2, 0, FILE_Z[c2]]))
    pts.append((T_ARRIVE + 0.15 * p2, slot(k)))
    m.go(t_start, pts, start_ease='in')
    m.face_point(T_ARRIVE + 0.15 * p2 + 0.15, STOOL, 0.6)

# ---- Hermione: called, rushes up, sits, the hat, Gryffindor, runs to her table by the lane
hm = kid_m['hermione']
hm.go(T_GRANGER + 0.7, [(T_GRANGER + 1.3, [2.45, 0, 0.45]), (T_GRANGER + 1.7, [1.9, 1, 0.5]),
                        (T_GRANGER + 2.1, [1.15, 1, 0.5])])
hm.face(T_GRANGER + 2.1, -90, 0.35)
hm.sit(T_GRANGER + 2.15, T_GRANGER + 2.55, STOOL_ROOT_KID)
H_UP = CHEER_H + 1.3
hm.sit(H_UP, H_UP + 0.35, [1.15, 1, 0.45])
hm.go(H_UP + 0.35, [(H_UP + 0.7, [1.9, 1, 0.45]), (H_UP + 1.0, [2.45, 0, 0.45]), (H_UP + 1.4, [3.2, 0, LANE_Z]),
                    (H_UP + 3.6, [12.5, 0, LANE_Z])])
hm.face(H_UP + 3.65, 180, 0.35)
hm.sit(H_UP + 3.7, H_UP + 4.1, H_SEAT)
herm_poses = [pose(T_GRANGER + 2.3, 'seated', 5), pose(T_GRANGER + 2.9, 'hands_clasped', 8), pose(H_UP - 0.1, 'relax', 4),
              pose(H_UP + 3.9, 'seated', 5)]
herm_actions = [{'t': T_HAT_H, 'do': 'equip', 'slot': 'head', 'item': HAT},
                {'t': CHEER_H + 1.0, 'do': 'equip', 'slot': 'head', 'item': 'minecraft:air'}]

# ---- Ron: called, walks up, sits, the hat, Gryffindor, sags with relief, walks to his table by the lane
rm = kid_m['ron']
rm.go(T_WEASLEY + 0.8, [(T_WEASLEY + 1.5, [2.45, 0, 1.0]), (T_WEASLEY + 1.9, [1.9, 1, 0.65]),
                        (T_WEASLEY + 2.35, [1.15, 1, 0.5])])
rm.face(T_WEASLEY + 2.35, -90, 0.35)
rm.sit(T_WEASLEY + 2.4, T_WEASLEY + 2.8, STOOL_ROOT_KID)
R_UP = CHEER_R + 1.4
rm.sit(R_UP, R_UP + 0.4, [1.15, 1, 0.45])
rm.go(R_UP + 0.4, [(R_UP + 0.8, [1.9, 1, 0.45]), (R_UP + 1.15, [2.45, 0, 0.45]), (R_UP + 1.6, [3.2, 0, LANE_Z]),
                   (R_UP + 4.6, [13.5, 0, LANE_Z])])
rm.face(R_UP + 4.65, 180, 0.35)
rm.sit(R_UP + 4.7, R_UP + 5.1, R_SEAT)
ron_poses = [pose(T_WEASLEY + 2.6, 'seated', 5), pose(T_WEASLEY + 3.2, 'hands_clasped', 8), pose(R_UP - 0.1, 'relax', 4),
             pose(R_UP + 4.9, 'seated', 5)]
ron_actions = [{'t': T_HAT_R, 'do': 'equip', 'slot': 'head', 'item': HAT},
               {'t': CHEER_R + 1.0, 'do': 'equip', 'slot': 'head', 'item': 'minecraft:air'}]

# ---- the boy: last in line; walks up alone by the lane, sits, the hat comes down
bm = kid_m['boy']
bs = slot('boy')
bm.go(T_BOY_WALK, [(T_BOY_WALK + 0.7, [bs[0] - 0.6, 0, LANE_Z]), (T_BOY_WALK + 4.3, [2.6, 0, LANE_Z]),
                   (T_BOY_WALK + 4.7, [2.45, 0, 0.1]), (T_BOY_WALK + 5.0, [1.9, 1, 0.3]),
                   (T_BOY_WALK + 5.3, [1.15, 1, 0.5])], start_ease='in')
bm.face(T_BOY_WALK + 5.3, -90, 0.3)
bm.sit(T_BOY_SIT - 0.2, T_BOY_SIT + 0.2, STOOL_ROOT_KID)
boy_poses = [pose(T_BOY_SIT - 0.1, 'seated', 5), pose(T_BOY_SIT + 0.8, 'hands_clasped', 10)]
boy_actions = [{'t': T_HAT_B, 'do': 'equip', 'slot': 'head', 'item': HAT}]


def m_pos(m, t):
    """A mover's position at t (seconds), from its emitted path."""
    return sample_path(m.emit()[0], t * 20)


def head_toward(m, t, pt, cap=70):
    """Head yaw offset for mover m at t to look at pt (clamped), and pitch."""
    p = m_pos(m, t)
    yaw = None
    for tt, y in m.look:
        if tt <= t:
            yaw = y
    d = clamp(wrap(yaw_to(pt[0] - p[0], pt[2] - p[2]) - yaw), -cap, cap)
    hp = math.hypot(pt[0] - p[0], pt[2] - p[2])
    pitch = clamp(math.degrees(math.atan2(p[1] + EYE * KID - pt[1], max(hp, 0.3))), -30, 35)
    return [pitch, d, 0]


# special head business for the three named children
kl = kid_life
kl['ron']['head'].change(11.6, [22, 0, 0], 0.3)                         # swallows on "be sorted"
kl['ron']['head'].change(12.6, [0, 0, 0], 0.4)
kl['ron']['head'].change(T_RON_LINE - 0.2, head_toward(kid_m['ron'], T_RON_LINE, [SPOT['boy'][0], 1.1, SPOT['boy'][2]]), 0.4)
kl['ron']['head'].change(T_RON_LINE + 1.9, [0, 0, 0], 0.4)
kl['boy']['head'].change(T_RON_LINE + 0.1, head_toward(kid_m['boy'], T_RON_LINE, [SPOT['ron'][0], 1.1, SPOT['ron'][2]]), 0.45)
kl['boy']['head'].change(T_RON_LINE + 2.0, [0, 0, 0], 0.4)
kl['hermione']['head'].change(T_HERM_LINE - 0.2, [4, -55, 0], 0.4)     # whispers to Ron (on her +z: her left)
kl['hermione']['head'].change(T_HERM_LINE + 3.7, [0, 0, 0], 0.4)
kl['hermione']['right_arm'].change(T_HERM_LINE + 0.6, [-40, 0, 10], 0.4)
kl['hermione']['right_arm'].change(T_HERM_LINE + 2.6, [0, 0, 0], 0.5)
kl['hermione']['head'].change(T_SHOUT_H + SHOUT_LEN, [-18, 0, 0], 0.4)  # beams
kl['hermione']['head'].change(T_SHOUT_H + SHOUT_LEN + 1.0, [0, 0, 0], 0.4)
kl['ron']['head'].change(T_HAT_R + 0.2, [-12, 0, 0], 0.4)                # eyes up at the brim
kl['ron']['head'].change(T_SHOUT_R + SHOUT_LEN, [32, 0, 0], 0.5)         # sags with relief
kl['ron']['body'].change(T_SHOUT_R + SHOUT_LEN, [14, 0, 0], 0.5)
kl['ron']['head'].change(R_UP - 0.2, [0, 0, 0], 0.4)
kl['ron']['body'].change(R_UP - 0.2, [0, 0, 0], 0.4)

# idle life for every first-year: glances at neighbours, head turns, gestures, a weight shift
KID_BUSY = {'ron': [(11.4, 16.6), (T_WEASLEY, 90)], 'boy': [(13.8, 17.0), (T_BOY_WALK - 1, 120)],
            'hermione': [(T_HERM_LINE - 0.5, T_HERM_LINE + 4.3), (T_GRANGER, 120)]}
for k in KIDS:
    rnd = random.Random('kid' + k)
    busy = KID_BUSY.get(k, []) + [(T0 - 0.5, BOOM + 1.5)]        # everyone freezes for the doors
    if k not in ('boy',):
        busy.append((T_NAME + 0.3, T_BOY_SIT))                   # turned to the boy (below)
    for a, b in free_windows(busy, 1.0, LENGTH - 2):
        t = a + rnd.uniform(0.3, 2.5)
        while t < b - 2.0:
            r = rnd.random()
            others = [o for o in KIDS if o != k]
            if r < 0.55:
                o = rnd.choice(others)
                pt = m_pos(kid_m[o], t)
                v = head_toward(kid_m[k], t, [pt[0], pt[1] + 1.1, pt[2]])
                v[1] = clamp(v[1], -60, 60)
            else:
                v = [rnd.uniform(-10, 14), rnd.uniform(-45, 45), rnd.uniform(-4, 4)]
            kid_life[k]['head'].change(t, v, rnd.uniform(0.3, 0.6))
            back = t + rnd.uniform(1.0, 2.6)
            if back < b:
                kid_life[k]['head'].change(back, [0, 0, 0], rnd.uniform(0.3, 0.6))
            if rnd.random() < 0.3 and back + 0.2 < b:
                gesture(kid_life[k], rnd, t + 0.2)
            t = back + rnd.uniform(1.0, 4.0)
    # a weight shift now and then
    t = rnd.uniform(1, 6)
    while t < LENGTH - 6:
        if not any(a - 1 < t < b + 1 for a, b in busy):
            kid_life[k]['body'].change(t, [0, 0, rnd.uniform(-3, 3)], 0.8)
        t += rnd.uniform(5, 11)
    # the name: the children turn to the boy behind them (staggered, some late)
    if k not in ('boy', 'hermione', 'ron'):
        tr = react(rnd, T_NAME + 0.2, never=0.0)
        bp = m_pos(kid_m['boy'], T_NAME)
        kid_life[k]['head'].change(tr, head_toward(kid_m[k], tr, [bp[0], 1.1, bp[2]], 80), rnd.uniform(0.4, 0.7))
        kid_life[k]['head'].change(T_BOY_WALK + 2.0 + rnd.uniform(0, 1.5), [0, 0, 0], 0.6)

FIRST_YEAR_SKINS = skin_pool('first_year', 10, ['first_year'])
for i, k in enumerate(KIDS):
    if k == 'boy':
        skin, pc, ac = {'viewer': True}, boy_poses, boy_actions
    elif k == 'hermione':
        skin, pc, ac = {'texture': 'hermione'}, herm_poses, herm_actions
    elif k == 'ron':
        skin, pc, ac = {'texture': 'ron'}, ron_poses, ron_actions
    else:
        n = int(k[1:]) - 1
        skin, pc, ac = {'texture': FIRST_YEAR_SKINS[n % len(FIRST_YEAR_SKINS)]}, None, None
    actor(k if k in ('boy', 'hermione', 'ron') else 'first_year_' + k[1:], skin, KID, mover=kid_m[k],
          life=kid_life[k], pose_cues=pc, actions=ac)

actor('mcgonagall', {'texture': 'mcgonagall'}, ADULT, mover=mcg, life=mcg_life,
      items={'offhand': 'minecraft:paper'},
      pose_cues=[pose(0, 'scroll', 1), pose(T_CALL - 0.3, 'scroll_read', 8), pose(T_SHOUT_H - 0.4, 'scroll', 8),
                 pose(T_WEASLEY - 0.6, 'scroll_read', 8), pose(T_SHOUT_R - 0.4, 'scroll', 8),
                 pose(T_SCROLL, 'scroll_read', 12), pose(T_BOY_SIT, 'scroll', 10)],
      actions=mcg_actions)

# ================================================================ the staff table
STAFF = A['staff_seats']        # [x, seat_top, z, yaw]; 0 is the throne


def staff_idle(life, rnd, every, span, busy=()):
    t = rnd.uniform(0.5, every[0])
    while t < LENGTH - 2:
        if not any(a - 0.5 < t < b for a, b in busy):
            life['head'].change(t, [rnd.uniform(-6, 12), rnd.uniform(-span, span), rnd.uniform(-4, 4)],
                                rnd.uniform(0.4, 0.9))
        t += rnd.uniform(*every)


def staff_seated(aid, skin, seat, scale, life, look=None):
    x, top, z, yaw = seat
    actor(aid, {'texture': skin}, scale, path=[{'t': 0, 'pos': [x, seat_root(top, scale), z]}],
          look=look or [{'t': 0, 'yaw': yaw}], life=life, pose_cues=[pose(0, 'seated', 1)])


# Dumbledore rises for his welcome, arms open, and sits again
THRONE = [STAFF[0][0], seat_root(STAFF[0][1], ADULT), STAFF[0][2]]
DUMB_UP = [-13.9, 1.0, 0.5]                              # standing behind the table, in front of the throne
dm = Mover('dumbledore', THRONE, STAFF[0][3])
dm.sit(T_DUMB - 0.9, T_DUMB - 0.2, DUMB_UP)
dm.sit(T_DUMB + 4.4, T_DUMB + 5.1, THRONE)
d_life = Life()
staff_idle(d_life, random.Random(3), (3.5, 7.0), 25, busy=[(T_DUMB - 1, T_DUMB + 5.5)])
d_life['head'].change(BOOM + 0.6, [0, 14, 0], 0.8)
actor('dumbledore', {'texture': 'dumbledore'}, ADULT, mover=dm, life=d_life,
      pose_cues=[pose(0, 'seated', 1), pose(T_DUMB - 0.9, 'relax', 8), pose(T_DUMB, 'welcome', 12),
                 pose(T_DUMB + 3.7, 'relax', 10), pose(T_DUMB + 5.0, 'seated', 6)])

# Snape: still, staring; Quirrell: twitchy little jerks; Hagrid (1.5): waves, claps after each Gryffindor
s_life = Life()
staff_idle(s_life, random.Random(5), (6.0, 11.0), 12)
staff_seated('snape', 'snape', STAFF[4], ADULT, s_life)
q_life = Life()
rq = random.Random(7)
t = 0.4
while t < LENGTH - 1:
    q_life['head'].change(t, [rq.uniform(-8, 14), rq.uniform(-35, 35), rq.uniform(-6, 6)], rq.uniform(0.1, 0.25))
    t += rq.uniform(0.8, 1.8)
staff_seated('quirrell', 'quirrell', STAFF[6], ADULT, q_life)
h_life = Life()
rh = random.Random(11)
h_life['right_arm'].change(S['D3'] + 1.0, [-160, 0, 14], 0.5)
for k in range(4):
    h_life['right_arm'].change(S['D3'] + 1.6 + 0.45 * k, [-160, 0, 34 if k % 2 == 0 else 8], 0.2, False)
h_life['right_arm'].change(S['D3'] + 3.6, [0, 0, 0], 0.6)
for base in (CHEER_H + 0.4, CHEER_R + 0.6):
    for k in range(4):
        tt = base + 0.34 * k
        h_life['right_arm'].change(tt, [-50, 0, 6], 0.15, False)
        h_life['right_arm'].change(tt + 0.17, [-55, 0, -20], 0.15, False)
        h_life['left_arm'].change(tt, [-50, 0, -6], 0.15, False)
        h_life['left_arm'].change(tt + 0.17, [-55, 0, 20], 0.15, False)
    h_life['right_arm'].change(base + 1.6, [0, 0, 0], 0.4)
    h_life['left_arm'].change(base + 1.6, [0, 0, 0], 0.4)
staff_idle(h_life, rh, (2.5, 6.0), 30)
staff_seated('hagrid', 'hagrid', STAFF[7], GIANT, h_life)
t_life = Life()
staff_idle(t_life, random.Random(13), (2.5, 5.5), 35)
gesture(t_life, random.Random(14), 9.0)
gesture(t_life, random.Random(15), 31.0)
staff_seated('teacher', 'sirius', STAFF[3], ADULT, t_life)

# ================================================================ the house tables
SEATS = A['table_seats']
PER_SIDE = 15
RESERVED = {(12.5, -1.5), (13.5, -1.5)}                   # Hermione's and Ron's seats
AISLE_BENCH_Z = (-1.5, 2.5)                              # the two benches with their backs to the aisle
DOOR_TARGET = [64.5, 5.5, 0.5]
STOOL_TARGET = [0.5, 2.4, -0.2]                          # the stool and McGonagall beside it
DUMB_TARGET = [-13.9, 2.6, 0.5]


def pick_seats(house, rnd):
    out = []
    for side_z in sorted({s[2] for s in SEATS[house]}):
        side = [s for s in SEATS[house] if s[2] == side_z and (s[0], s[2]) not in RESERVED]
        if side_z in AISLE_BENCH_Z:
            side = [s for s in side if s[0] >= 14.5]      # leave room behind the waiting first-years
        xs = sorted(side, key=lambda s: s[0])
        n = len(xs)
        chosen = []
        for k in range(PER_SIDE):
            j = int((k + 0.5) * n / PER_SIDE + rnd.uniform(-0.8, 0.8))
            j = int(clamp(j, 0, n - 1))
            if chosen and j <= chosen[-1] + 1:
                j = chosen[-1] + 2
            if j < n:
                chosen.append(j)
        out += [xs[j] for j in chosen]
    return out


class Gaze:
    """A seated student's attention: head turns (limbs, added on the look) and a share of it in the body (look
    yaw, at most 28 degrees so the folded legs stay under the table)."""

    def __init__(self, seat, scale, life):
        self.x, top, self.z, self.yaw0 = seat
        self.eye = [self.x, seat_root(top, scale) + EYE * scale, self.z]
        self.life = life
        self.body = Channel(0.0)

    def rel(self, p):
        dx, dy, dz = p[0] - self.eye[0], p[1] - self.eye[1], p[2] - self.eye[2]
        d = wrap(yaw_to(dx, dz) - self.yaw0)
        pitch = math.degrees(math.atan2(-dy, math.hypot(dx, dz)))
        return d, pitch

    def set(self, t, d, pitch, dur=0.45, hold=True, body=True):
        shift = clamp(0.35 * d, -28, 28)
        hy = clamp(d - (shift if body else 0), -72, 72)
        self.life['head'].change(t, [clamp(pitch, -30, 35), hy, 0], dur, hold)
        if body:
            self.body.change(t, shift, dur * 1.3, hold)

    def at(self, t, p, dur=0.45, hold=True, body=True):
        d, pitch = self.rel(p)
        self.set(t, d, pitch, dur, hold, body)

    def look_json(self):
        return [{'t': t, 'yaw': round(self.yaw0 + v, 1)} for t, v in self.body.keys()]


def arms_cheer(life, rnd, t0, big):
    n = rnd.randint(4, 6) if big else rnd.randint(2, 4)
    for part, s in (('right_arm', 1), ('left_arm', -1)):
        life[part].change(t0, [rnd.uniform(-150, -135), 0, s * 15], rnd.uniform(0.2, 0.35))
    t = t0 + 0.35
    for _ in range(n):
        t += rnd.uniform(0.3, 0.45)
        for part, s in (('right_arm', 1), ('left_arm', -1)):
            life[part].change(t, [rnd.uniform(-155, -110), 0, s * rnd.uniform(8, 28)], 0.3, False)
    for part in ('right_arm', 'left_arm'):
        life[part].change(t + rnd.uniform(0.4, 0.9), [0, 0, 0], rnd.uniform(0.4, 0.7))
    return t + 1.2


def arms_clap(life, rnd, t0):
    claps = rnd.randint(3, 6)
    rate = rnd.uniform(0.28, 0.38)
    for k in range(claps):
        t = t0 + 0.2 + rate * k
        life['right_arm'].change(t, [-30, 0, 8], 0.12, False)
        life['left_arm'].change(t, [-30, 0, -8], 0.12, False)
        life['right_arm'].change(t + rate / 2, [-32, 0, -22], 0.12, False)
        life['left_arm'].change(t + rate / 2, [-32, 0, 22], 0.12, False)
    end = t0 + 0.2 + rate * claps + 0.3
    life['right_arm'].change(end, [0, 0, 0], 0.4)
    life['left_arm'].change(end, [0, 0, 0], 0.4)
    return end + 0.4


def arms_free(t):
    """No talking gestures from just before each shout to well after its cheer."""
    return not (T_SHOUT_H - 4 < t < CHEER_H + 7 or T_SHOUT_R - 4 < t < CHEER_R + 7)


def track_pos(m, t, y=1.0):
    p = m_pos(m, t)
    return [p[0], y, p[2]]


STUDENT_SKINS = {h: skin_pool('student_' + h, 30, ['student_' + h, 'student_%s_b' % h]) for h in SEATS}
n_students = 0
for house in ('hufflepuff', 'gryffindor', 'ravenclaw', 'slytherin'):
    rnd_seat = random.Random('seats' + house)
    seats = pick_seats(house, rnd_seat)
    skins = list(STUDENT_SKINS[house])
    random.Random('skins' + house).shuffle(skins)
    for k, seat in enumerate(seats):
        n_students += 1
        aid = '%s_%d' % (house, k)
        rnd = random.Random(aid)
        life = Life()
        g = Gaze(seat, OLD, life)
        neighbours = [s for s in seats if s is not seat and abs(s[0] - seat[0]) < 6]
        # ---- the reactions: (time or None, what), each staggered for this student
        R = []
        tr = react(rnd, BOOM - 0.2, (1.5, 3.0))                                   # the doors
        if tr:
            R.append((tr, 'pt', DOOR_TARGET, True))
        for tt in (S['D1'] + 1.0, S['D1'] + 4.5, 37.5, S['D3'] + 1.5, S['D3'] + 4.0):
            tt2 = tt + rnd.uniform(-0.4, 0.8)
            R.append((tt2, 'track', [mcg_x(tt2) + 1.5, 1.3, AISLE_Z], True))
        tr = react(rnd, T_ARRIVE, (1.5, 2.5))
        if tr:
            R.append((tr, 'pt', STOOL_TARGET, True))
        tr = react(rnd, T_DUMB - 0.6, (1.5, 3.0))
        if tr:
            R.append((tr, 'pt', DUMB_TARGET, False))
        tr = react(rnd, T_DUMB + 4.4, (1.5, 3.0), never=0.0)
        R.append((tr, 'pt', STOOL_TARGET, False))
        tr = react(rnd, T_GRANGER + 0.6, (1.0, 2.0))
        if tr:
            R.append((tr, 'track', track_pos(hm, tr), False))
        R.append((T_GRANGER + 2.8 + rnd.uniform(0, 1.0), 'pt', STOOL_TARGET, False))
        tr = react(rnd, H_UP + 0.6, (1.0, 2.0))
        if tr:
            R.append((tr, 'track', track_pos(hm, tr), False))
        R.append((T_WEASLEY + 0.5 + rnd.uniform(0, 1.5), 'pt', STOOL_TARGET, False))
        tr = react(rnd, R_UP + 0.8, (1.0, 2.5))
        if tr:
            R.append((tr, 'track', track_pos(rm, tr), False))
        R.append((T_SCROLL + rnd.uniform(0, 2.0), 'pt', STOOL_TARGET, True))
        tr = react(rnd, T_NAME + 0.2, (1.5, 3.0), late=0.15, never=0.05)       # the name: turn to the boy
        if tr:
            R.append((tr, 'pt', track_pos(bm, T_NAME, 1.1), True))
            R.append((T_BOY_WALK + 2.0 + rnd.uniform(0, 0.8), 'track', track_pos(bm, T_BOY_WALK + 2.4, 1.1), True))
            R.append((T_BOY_SIT + rnd.uniform(0, 1.2), 'pt', [STOOL[0], 2.1, STOOL[2]], True))
        R.sort(key=lambda r: r[0])
        # ---- before the doors and in every quiet gap: chatter with neighbours, idle turns
        busy = [(r[0] - 0.6, r[0] + 1.2) for r in R]
        busy.append((T_HUSH - 0.5, T_DUMB + 5.5))                            # the hush and the welcome: still
        busy.append((T_NAME, T_BOY_SIT + 2.0))                               # the name: everyone watching
        idle = []
        for a, b in free_windows(busy, 0.3, LENGTH - 1.5):
            t = a + rnd.uniform(0.2, 2.0)
            while t < b - 1.2:
                idle.append(t)
                t += rnd.uniform(2.5, 6.5)
        events_s = [(r[0], r) for r in R] + [(t, None) for t in idle]
        events_s.sort(key=lambda e: e[0])
        g.set(0.0, rnd.uniform(-45, 45), rnd.uniform(-6, 10), body=False)
        cur = None                                       # the current attention point (None = idle chatter)
        for t, r in events_s:
            if r is None:
                if rnd.random() < 0.6 and neighbours:
                    nb = rnd.choice(neighbours)
                    g.at(t, [nb[0], 1.25, nb[2]], rnd.uniform(0.35, 0.7), body=rnd.random() < 0.4)
                    if rnd.random() < 0.35 and arms_free(t):
                        gesture(life, rnd, t + rnd.uniform(0.1, 0.6))
                else:
                    base = g.rel(cur)[0] if cur else 0
                    g.set(t, base + rnd.uniform(-35, 35), rnd.uniform(-8, 10), rnd.uniform(0.35, 0.7),
                          body=rnd.random() < 0.3)
                if cur and rnd.random() < 0.6:
                    g.at(t + rnd.uniform(1.0, 2.2), cur, rnd.uniform(0.4, 0.7), body=False)
            else:
                _, kind, p, body = r
                g.at(t, p, rnd.uniform(0.35, 0.65) if kind == 'pt' else 0.6, hold=(kind == 'pt'), body=body)
                cur = p
        # ---- leaning on the table now and then
        t = rnd.uniform(1, 12)
        while t < LENGTH - 8:
            d = rnd.uniform(3, 8)
            if not any(a - 1 < t < b + 1 or a - 1 < t + d < b + 1 for a, b in
                       ((BOOM - 1, BOOM + 3), (CHEER_H - 0.5, CHEER_H + 6), (CHEER_R - 0.5, CHEER_R + 6))):
                lean(life, rnd, t, d)
            t += d + rnd.uniform(6, 18)
        # ---- the cheers: Gryffindor on its feet (arms up), others clap; never before the shout has ended
        for c0, big in ((CHEER_H, True), (CHEER_R, False)):
            if house == 'gryffindor':
                tr = react(rnd, c0, (0.3, 2.0), late=0.1, never=0.0 if big else 0.2)
                if tr:
                    arms_cheer(life, rnd, tr, big)
            elif house != 'slytherin':
                if rnd.random() < (0.6 if big else 0.45):
                    arms_clap(life, rnd, c0 + rnd.uniform(0.3, 3.0))
        # talking gestures in the quiet stretches (not over the cheers)
        for t in idle:
            if rnd.random() < 0.12 and arms_free(t):
                gesture(life, rnd, t + 0.3)
        x, top, z, yaw = seat
        actor(aid, {'texture': skins[k % len(skins)]}, OLD,
              path=[{'t': 0, 'pos': [x, seat_root(top, OLD), z]}], look=g.look_json(), life=life,
              pose_cues=[pose(0, 'seated', 1)])

# ================================================================ events
ev(0, 'effect', 'eyes_open', at=[0, 0, 0], duration=tk(2.6))
ev(0, 'effect', 'floating_candles', at=[27.0, 11.0, 0.5], spread=[33.0, 4.5, 15.5], count=900)
sfx(0.3, 'steps_group', 0.3)
sfx(0.5, 'murmur_a', 0.3)                                 # the hall, muffled behind the doors
voice(T_MCG_LINE, 'mcg_doors')
voice(T_RON_LINE, 'ron_nervous')
# the doors
ev(T0, 'sound', 'minecraft:block.wooden_door.close', at=[65.5, 2, 0.5], volume=1.6, pitch=0.5)    # the lock
ev(T0 + 0.05, 'sound', 'minecraft:block.iron_trapdoor.open', at=[65.5, 3, 0.5], volume=0.9, pitch=0.55)
sfx(T0, 'door_groan', 1.0)
sfx(T0 + 1.6, 'door_groan', 0.9)                          # the second groan, under the heavy swing
sfx(T0 + 0.3, 'murmur_b', 1.0)                            # the hall's noise rises as the gap opens (to ~36.9)
sfx(S['C3'] + 0.6, 'murmur_a', 0.6)                       # and carries on until the hush
ev(T0 + 0.4, 'effect', 'light_rays', at=[DOOR_FACE_X, 0, 0.5], yaw=-90, spread=[3.5, 12, 22], duration=68,
   intensity=0.45)
ev(BOOM, 'block', 'minecraft:light', at=[70, 6, 0])        # one soft light high in the entrance hall
sfx(BOOM, 'door_boom', 1.0)
ev(BOOM, 'music', MUSIC + 'entry', volume=1.0)
ev(BOOM + 0.05, 'particles', 'minecraft:white_ash', at=[66.2, 11.6, 0.5], count=80, spread=[0.6, 0.3, 3.6])
ev(BOOM + 0.25, 'particles', 'minecraft:white_ash', at=[63.8, 11.4, 0.5], count=50, spread=[0.6, 0.3, 3.6])
sfx(T_WALK + 0.2, 'steps_group', 0.85)
sfx(T_WALK + 8.0, 'steps_group', 0.7)
sfx(T_ARRIVE - 7.6, 'steps_group', 0.5)
# the reveal
ev(T_CEIL, 'effect', 'enchanted_ceiling', at=A['ceiling_center'], spread=A['ceiling_spread'], duration=80,
   style='storm')
sfx(T_CEIL, 'ceiling_shimmer', 0.9)
voice(T_HERM_LINE, 'hermione_nervous')
sfx(T_HUSH, 'hush', 1.0)
voice(T_DUMB, 'dumbledore')
voice(T_CALL, 'mcg_call')
voice(T_GRANGER, 'mcg_granger')
sfx(T_GRANGER + 0.7, 'steps_one', 0.6)
sfx(T_GRANGER + 2.2, 'stool', 0.8)
ev(T_SHOUT_H, 'voice', SHOUT, volume=1.0)
sfx(CHEER_H, 'cheer_big', 1.0)
voice(T_WEASLEY, 'mcg_weasley')
sfx(T_WEASLEY + 0.8, 'steps_one', 0.6)
sfx(T_WEASLEY + 2.45, 'stool', 0.8)
ev(T_SHOUT_R, 'voice', SHOUT, volume=1.0)
sfx(CHEER_R, 'cheer_small', 1.0)
ev(T_NAME - 0.2, 'music', MUSIC + 'prologue', volume=1.0)
sfx(T_NAME + 0.3, 'whispers', 0.9)
sfx(T_BOY_WALK + 0.2, 'steps_one', 1.0)
sfx(T_BOY_WALK + 3.4, 'steps_one', 0.9)
sfx(T_BOY_SIT - 0.2, 'stool', 1.0)
sfx(T_HAT_B - 0.3, 'hat_brim', 1.0)
ev(T_CLOSE, 'effect', 'eyes_close', at=[0, 0, 0], duration=tk(LENGTH - T_CLOSE))

words.append({'t': tk(T_NAME), 'text': '{viewer}', 'narration': True, 'duration': tk(4.5), 'size': 3})

# ================================================================ camera


def key(t, pos, look, fov=70, cut=False, ease=None):
    k = {'t': tk(t), 'pos': [round(c, 3) for c in pos]}
    if look is not None:
        k['look_at'] = look if isinstance(look, str) else [round(c, 3) for c in look]
    if fov != 70:
        k['fov'] = fov
    if ease:
        k['ease'] = ease
    if cut:
        k['cut'] = True
    camera.append(k)


BOY_EYE = EYE * KID
bsp = SPOT['boy']
# A: first person at the boy's eye height in the entrance hall, looking up at the towering doors
fp0 = [bsp[0] - 0.3, BOY_EYE, bsp[2]]
key(0, fp0, [65.0, 10.5, 0.2], 74)
key(2.8, [fp0[0] - 0.04, BOY_EYE, fp0[2]], [65.0, 8.5, 0.2], 72)
key(S['B1'], [fp0[0] - 0.08, BOY_EYE, fp0[2]], [67.8, 2.0, -0.8], 68)
# B1: behind the first-years; McGonagall turns to them and speaks
key(S['B1'], [79.0, 2.4, 3.4], [68.5, 1.75, 0.5], 58, cut=True)
key(S['B2'], [78.0, 2.2, 3.0], [68.5, 1.7, 0.5], 53)
# B2: a slow sideways dolly across their faces, from beside McGonagall
key(S['B2'], [69.4, 1.35, -2.5], 'actor:boy', 50, cut=True)
key(S['B2'] + 2.4, [69.45, 1.35, 0.4], 'actor:ron', 50)
key(S['C1'], [69.4, 1.35, 2.9], 'actor:hermione', 50)
# C1: the low push-in on the doors among the children; Ron's whisper; the lock, the creak, the first line of light
key(S['C1'], [73.0, 0.55, 0.5], [65.0, 6.6, 0.5], 70, cut=True)
key(S['C2'], [70.8, 0.6, 0.5], [65.0, 6.2, 0.5], 64)
# C2: one held wide behind the first-years for the whole swing; the boom shakes it
C2A, C2B = S['C2'], S['C3']
P2A, P2B = [79.2, 3.0, 3.4], [78.6, 2.9, 3.1]
L2 = [65.0, 5.2, 0.5]


def c2(t):
    f = (t - C2A) / (C2B - C2A)
    return [P2A[k] + (P2B[k] - P2A[k]) * f for k in range(3)]


key(C2A, P2A, L2, 78, cut=True)
key(BOOM, c2(BOOM), L2, 78, ease='linear')
for dt, o in ((0.1, (0.08, 0.06, -0.05)), (0.2, (-0.06, -0.08, 0.05)), (0.3, (0.03, 0.04, -0.02))):
    p = c2(BOOM + dt)
    key(BOOM + dt, [p[0] + o[0], p[1] + o[1], p[2] + o[2]], L2, 78, ease='linear')
key(C2B, P2B, L2, 76, ease='linear')
# C3: the reverse, inside the hall: the doors settled, McGonagall leading the first-years in
key(S['C3'], [57.6, 1.45, -2.4], [66.0, 3.4, 0.5], 62, cut=True)
key(S['D1'], [57.2, 1.5, -2.1], [64.0, 2.4, 0.5], 60)
# D1: the crane from the aisle up through the candles, tilting up as the ceiling dissolves into storm
key(S['D1'], [52.0, 1.8, 0.5], [20.0, 3.0, 0.5], 74, cut=True)
key(S['D1'] + 2.4, [49.5, 6.5, 0.5], [20.0, 7.0, 0.5], 77)
key(S['D1'] + 4.4, [46.6, 11.0, 0.5], [24.0, 22.0, 0.5], 84)
key(S['D2'], [45.8, 11.8, 0.5], [30.0, 34.0, 0.5], 86)     # a long look up into the sky (the camera stays well under the plane)
# D2: a slow track along the staff table: Hagrid, Dumbledore, Snape, Quirrell
key(S['D2'], [-5.8, 3.0, -6.0], 'actor:hagrid', 50, cut=True)
key(S['D2'] + 2.4, [-5.8, 3.0, -1.0], 'actor:dumbledore', 48)
key(S['D3'], [-5.8, 3.0, 3.6], 'actor:quirrell', 48)
# D3: the procession comes up the aisle toward us (Hermione whispering), we back slowly away over the stool
key(S['D3'], [9.0, 2.0, 0.5], 'actor:mcgonagall', 56, cut=True)
key(S['D3'] + 2.8, [4.0, 2.5, 0.5], 'actor:mcgonagall', 58)
key(S['E1'], [-1.6, 3.1, 0.5], 'actor:hermione', 58)
# E1: wide from behind the first-years: the stool, McGonagall with the hat, the hush, Dumbledore rising
key(S['E1'], [14.0, 3.6, 0.5], [-3.0, 2.2, 0.5], 60, cut=True)
key(S['E2'], [13.0, 3.45, 0.5], [-4.0, 2.4, 0.5], 56)
# E2: Dumbledore, arms open
key(S['E2'], [-8.6, 2.9, -1.3], 'actor:dumbledore', 48, cut=True)
key(S['E3'], [-8.9, 2.85, -1.0], 'actor:dumbledore', 45)
# E3: McGonagall with the scroll ("When I call your name..."), the camera drifting round to the waiting faces
key(S['E3'], [3.4, 2.15, -2.3], 'actor:mcgonagall', 50, cut=True)
key(S['E3'] + 3.6, [2.6, 2.2, -1.9], 'actor:mcgonagall', 47)
key(S['F1'], [1.8, 1.95, -0.4], [5.6, 1.1, 0.8], 54)
# F1: "Granger, Hermione." She bolts up to the stool
key(S['F1'], [3.0, 1.9, 2.6], 'actor:hermione', 54, cut=True)
key(S['F2'], [2.6, 2.0, 2.2], 'actor:hermione', 50)
# F2: close: the hat comes down on her; the shout
key(S['F2'], [2.7, 1.8, 0.5], 'actor:hermione', 44, cut=True)
key(S['F3'], [2.4, 1.85, 0.5], 'actor:hermione', 40)
# F3: the shout has ended: the Gryffindor table erupts; she runs to it
key(S['F3'], [4.6, 3.6, -4.0], [26.0, 1.0, -4.0], 72, cut=True)
key(S['G1'], [5.4, 3.4, -3.2], [16.0, 0.8, -2.4], 64)
# G1: "Weasley, Ronald." Ron walks up
key(S['G1'], [6.2, 2.3, 3.6], 'actor:ron', 52, cut=True)
key(S['G2'], [5.4, 2.3, 3.2], 'actor:ron', 48)
# G2: close on Ron under the hat; Gryffindor; he sags; the camera eases back as he walks off to the table
key(S['G2'], [2.4, 2.05, 1.7], 'actor:ron', 44, cut=True)
key(S['G2'] + 3.6, [2.3, 2.05, 1.6], 'actor:ron', 44)
key(S['H1'], [6.5, 2.6, 1.4], 'actor:ron', 56)
# H1: silence: McGonagall looks down at the scroll (slow push-in)
key(S['H1'], [2.3, 2.6, -1.6], 'actor:mcgonagall', 40, cut=True)
key(S['H2'], [1.7, 2.65, -1.35], 'actor:mcgonagall', 34)
# H2: the name, large; high over the stool: every head in the hall turns to the boy
key(S['H2'], [-3.0, 6.2, 0.5], [16.0, 0.6, 0.5], 72, cut=True)
key(S['I1'], [-2.4, 5.9, 0.5], [15.0, 0.6, 0.5], 68)
# I1: he walks up alone: tracking from behind, coming round to his side as he reaches the stool
key(S['I1'], [13.4, 1.95, -0.4], 'actor:boy', 56, cut=True)
key(S['I1'] + 3.0, [7.6, 1.95, -0.5], 'actor:boy', 54)
key(S['I3'], [3.4, 2.1, -1.8], 'actor:boy', 52)
# I3: over his shoulder on the stool: the four tables, every face on him; McGonagall steps down before him
key(S['I3'], [-1.6, 2.7, -0.7], [20.0, 1.4, 0.6], 66, cut=True)
key(S['J'], [-1.2, 2.62, -0.5], [20.0, 1.5, 0.6], 64)
# J: first person on the stool; the hat rises over the camera, the eyes lift, the brim comes down
fp = [STOOL[0] + 0.22, STOOL_ROOT_KID[1] + BOY_EYE, STOOL[2]]
key(S['J'], fp, [20.0, 1.5, 0.5], 72, cut=True)
key(S['J'] + 1.6, fp, [20.0, 1.8, 0.2], 72)
key(T_HAT_B, [fp[0] - 0.03, fp[1] - 0.04, fp[2]], [20.0, 5.2, -2.5], 74)
key(LENGTH, [fp[0] - 0.05, fp[1] - 0.07, fp[2]], [20.0, 3.6, -2.0], 74)

hall = {
    'title': 'The Sorting',
    'length': tk(LENGTH),
    'radius': GH.RADIUS,
    'set': {'templates': [{'id': 'hogwartsmagic:great_hall', 'at': list(GH.TEMPLATE_AT)}], 'time_of_day': 18000,
            'build_ticks': 100},
    'poses': POSES,
    'actors': actors,
    'events': sorted(events, key=lambda e: e['t']),
    'words': words,
    'camera': camera,
}

# ================================================================ checks (the hall)
problems = []
_grid = GH.build().g
world = GH.scene_world(_grid)
AIR = set(FREE)
# wall signs (the bench backs) are thin plates against one face of their cell: a walker only meets the plate
SIGN_FACING = {}
for (x, y, z), st in _grid.items():
    if '_wall_sign' in st:
        SIGN_FACING[(x + GH.TEMPLATE_AT[0], y + GH.TEMPLATE_AT[1], z + GH.TEMPLATE_AT[2])] =             st.split('facing=')[1].split(',')[0].rstrip(']')


def hits_sign(c, p, half=0.28):
    f = SIGN_FACING[c]
    if f == 'south':
        return p[2] - c[2] < 0.125 + half
    if f == 'north':
        return c[2] + 1 - p[2] < 0.125 + half
    if f == 'east':
        return p[0] - c[0] < 0.125 + half
    return c[0] + 1 - p[0] < 0.125 + half


def cell(p):
    return (math.floor(p[0]), math.floor(p[1]), math.floor(p[2]))


def is_air(p):
    return world.get(cell(p)) in AIR


for i, k in enumerate(camera):
    if not is_air(k['pos']):
        problems.append('camera key %d t %d pos %s is in %s' % (i, k['t'], k['pos'], world.get(cell(k['pos']))))
    if i and not k.get('cut'):
        a0, a1 = camera[i - 1]['pos'], k['pos']
        n = int(max(abs(a1[j] - a0[j]) for j in range(3)) / 0.1) + 1
        for s in range(1, n):
            p = [a0[j] + (a1[j] - a0[j]) * s / n for j in range(3)]
            if not is_air(p):
                problems.append('camera move into key %d (t %d) passes %s (%s)' % (i, k['t'], p, world.get(cell(p))))
                break
assert all(camera[i]['t'] <= camera[i + 1]['t'] for i in range(len(camera) - 1)), 'camera keys out of order'
ids = [a['id'] for a in actors]
assert len(ids) == len(set(ids)), 'duplicate actor ids'
evs = hall['events']
assert evs[-1]['id'] == 'eyes_close' and all(e['t'] < evs[-1]['t'] for e in evs[:-1]), 'eyes_close must be last'
for a in actors:
    for kk in ('path', 'look'):
        ts = [k['t'] for k in a.get(kk, [])]
        assert ts == sorted(ts) and len(ts) == len(set(ts)), (a['id'], kk)
        assert all(0 <= t <= tk(LENGTH) for t in ts), (a['id'], kk, ts)
    for part, d in a.get('limbs', {}).items():
        ts = [k['t'] for k in d['rot']]
        assert ts == sorted(ts) and len(ts) == len(set(ts)), (a['id'], part)
        assert all(0 <= t <= tk(LENGTH) for t in ts), (a['id'], part, ts)
for e in evs:
    if e['kind'] == 'block' and not is_air(e['at']):
        problems.append('light block at %s replaces %s' % (e['at'], world.get(cell(e['at']))))
# the cheers start only once the hat's shout has finished
for a in actors:
    for part in ('right_arm', 'left_arm'):
        if a['id'].startswith(('gryffindor_', 'hufflepuff_', 'ravenclaw_')) and part in a.get('limbs', {}):
            for k in a['limbs'][part]['rot']:
                for shout in (T_SHOUT_H, T_SHOUT_R):
                    if tk(shout) <= k['t'] < tk(shout + SHOUT_LEN) and any(abs(c) > 0.5 for c in k['v']):
                        problems.append('%s %s moves at tick %d, during the shout' % (a['id'], part, k['t']))
# collisions: no moving actor within 0.6 (horizontal) of any other actor; walkers' feet and heads in free cells
people = [a for a in actors if a['type'] == 'player']
paths = {a['id']: a['path'] for a in people}
moving_ids = [aid for aid, p in paths.items() if len(p) > 1]
sit = {aid: [(tk(t0), tk(t1)) for t0, t1 in w] for aid, w in SIT_WINDOWS.items()}
static_pos = {aid: p[0]['pos'] for aid, p in paths.items() if len(p) == 1}
n_checks = 0
for t in range(0, tk(LENGTH) + 1, 2):
    pos = {aid: sample_path(paths[aid], t) for aid in moving_ids}
    pos.update(static_pos)
    for aid in moving_ids:
        prev = sample_path(paths[aid], max(0, t - 2))
        cur = pos[aid]
        if math.dist(prev, cur) < 1e-4:
            continue
        for other, op in pos.items():
            if other == aid:
                continue
            n_checks += 1
            d = math.hypot(cur[0] - op[0], cur[2] - op[2])
            if d < 0.6 and abs(cur[1] - op[1]) < 1.6:
                problems.append('t %d: %s at %s is %.2f from %s at %s' % (t, aid, [r2(c) for c in cur], d, other,
                                                                          [r2(c) for c in op]))
        if any(a0 - 1 <= t <= a1 + 1 for a0, a1 in sit.get(aid, [])) or abs(cur[1] - prev[1]) > 0.3:
            continue
        for dy in (0, 1):
            c = (math.floor(cur[0]), math.floor(cur[1] + 0.4) + dy, math.floor(cur[2]))
            if c in SIGN_FACING and not hits_sign(c, cur):
                continue
            if world.get(c) not in AIR:
                problems.append('t %d: %s walks through %s at %s' % (t, aid, world.get(c), c))
if problems:
    print('\n'.join(problems[:60]))
    print('... %d problems' % len(problems))
    raise SystemExit(1)

txt = write_scene(hall, HALL_ID, width=2000)
assert len(txt) < MAX_JSON, '%s.json is %d chars (cap %d)' % (HALL_ID, len(txt), MAX_JSON)
print('wrote %s.json: %d chars (cap %d), %d actors (%d students), %d events, %d camera keys, %.1f s, %d proximity '
      'checks' % (HALL_ID, len(txt), MAX_JSON, len(actors), n_students, len(evs), len(camera), LENGTH, n_checks))

# ================================================================ the mind intro (scene `sorting`)
# Stage 1's stub, grown: the boy alone on a chair in a black void, inside his own head, the Hat's voice.
I_LEN = 12.0
I_R = 12
i_fills = [
    {'block': 'minecraft:polished_blackstone', 'from': [-3, -1, -3], 'to': [3, -1, 3]},
    {'block': 'minecraft:polished_deepslate', 'from': [-1, -1, -1], 'to': [1, -1, 1]},
    {'block': 'minecraft:spruce_slab', 'from': [0, 0, 0], 'to': [0, 0, 0]},             # the seat
    {'block': 'minecraft:spruce_fence', 'from': [0, 0, 1], 'to': [0, 1, 1]},            # its back
    {'block': 'minecraft:light', 'from': [0, 4, 0], 'to': [0, 4, 0]},                   # the pool of light
]
i_seat = [0.5, seat_root(0.5, KID), 0.5]
i_life = Life()
i_life['head'].change(2.8, [8, 0, 0], 1.2)
i_life['head'].change(5.0, [-6, -18, 0], 1.6)       # he glances up and aside, as if at the voice
i_life['head'].change(8.2, [10, 4, 0], 1.6)
i_events = [{'t': 0, 'kind': 'effect', 'id': 'eyes_open', 'at': [0, 0, 0], 'duration': tk(3.5)},
            {'t': tk(1.4), 'kind': 'voice', 'id': VOICE + 'hat_open', 'volume': 1.0}]
rw = random.Random(1991)
t = 0.4
while t < I_LEN - 0.8:                                   # slow silver wisps drifting round him
    ang = rw.uniform(0, 2 * math.pi)
    rr = rw.uniform(1.0, 3.2)
    i_events.append({'t': tk(t), 'kind': 'particles', 'id': 'minecraft:end_rod',
                     'at': [round(0.5 + rr * math.cos(ang), 2), round(rw.uniform(0.4, 2.6), 2),
                            round(0.5 + rr * math.sin(ang), 2)],
                     'count': rw.randint(1, 3), 'spread': [0.4, 0.3, 0.4]})
    if rw.random() < 0.35:
        i_events.append({'t': tk(t + 0.1), 'kind': 'particles', 'id': 'minecraft:white_ash',
                         'at': [0.5, 1.6, 0.5], 'count': rw.randint(4, 8), 'spread': [2.5, 1.2, 2.5]})
    t += rw.uniform(0.35, 0.7)
i_cam = []
for i in range(7):                                        # a slow orbit, closing in a little
    f = i / 6
    ang = math.radians(-120 + 95 * f)                    # round from his front-left to his right
    rad = 3.4 - 0.9 * f
    i_cam.append({'t': tk(I_LEN * f), 'pos': [round(0.5 + rad * math.sin(ang), 3), round(1.75 - 0.25 * f, 3),
                                              round(0.5 - rad * math.cos(ang), 3)],
                  'look_at': 'actor:boy', 'fov': round(56 - 8 * f, 1)})
    if 0 < i < 6:
        i_cam[-1]['ease'] = 'linear'
intro = {
    'title': 'The Sorting',
    'length': tk(I_LEN),
    'radius': I_R,
    'next': HALL_ID,
    'set': {'fills': i_fills, 'time_of_day': 18000, 'build_ticks': 40},
    'poses': {'seated': POSES['seated'], 'hands_on_knees': {'right_arm': [-38, -12, 0], 'left_arm': [-38, 12, 0]}},
    'actors': [{
        'id': 'boy', 'type': 'player', 'skin': {'viewer': True}, 'scale': KID,
        'path': [{'t': 0, 'pos': i_seat}],
        'look': [{'t': 0, 'yaw': 180}],
        'poses': [{'t': 0, 'name': 'seated', 'blend': 1}, {'t': 1, 'name': 'hands_on_knees', 'blend': 1}],
        'limbs': i_life.emit(),
    }],
    'events': sorted(i_events, key=lambda e: e['t']),
    'camera': i_cam,
}
for k in i_cam:
    p = k['pos']
    assert abs(p[0]) < I_R and abs(p[2]) < I_R and p[1] > 0
    assert not (math.floor(p[0]) == 0 and math.floor(p[2]) in (0, 1) and p[1] < 2.2), p
itxt = write_scene(intro, INTRO_ID, width=200)
print('wrote %s.json: %d chars, %.1f s, next %s' % (INTRO_ID, len(itxt), I_LEN, HALL_ID))
