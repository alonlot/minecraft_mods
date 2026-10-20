"""Generates data/hogwartsmagic/memories/sorting.json: the Sorting memory, staged in the Great Hall (stage 2).

Usage (from the repo root): python -I build_scripts/pensieve_scenes/great_hall_scene.py

Putting the Sorting Hat on plays this (SortingHatManager.MEMORY_SCENE = "sorting") before the voiced quiz. About
78 s, film-shaped: the eyes open in the torch-lit entrance hall among the first-years; McGonagall at the doors; the
doors open like an animated film (anticipation, stagger, slow crack, heavy swing, overshoot and settle, a boom with
camera shake and lintel dust, god-rays and the murmur flooding out); the hall revealed by a crane up through the
floating candles as the storm ceiling dissolves in; the staff table; the procession up the aisle; Dumbledore's
welcome; Hermione and Ron sorted to cheers; the hall falls silent, the viewer's name large on screen; he walks up,
sits, and the hat comes down in first person. Then black, and the quiz.

The set is great_hall_set.py's template (TEMPLATE_AT, RADIUS, ANCHORS); the door pair follows the ledger's DOOR PAIR
RECIPE (low-z leaf memory_door closed -90 open -180, high-z leaf memory_door_mirrored closed -90 open 0, both opening
toward -X into the hall). Timing is cut to the real voice and music lengths (Task 5 report): the plan's ~75 s beat
table cannot hold McGonagall's 7.5 s and 6.2 s lines plus Dumbledore, Hermione and Ron, so the beats run a few
seconds late; each beat keeps the plan's order.

Asserts: scene JSON under the 262,144-char cap; every camera key, and every straight camera move between keys, in
an air cell of the set; unique actor ids; eyes_close the last event.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import write_scene, FREE  # noqa: E402
import great_hall_set as GH  # noqa: E402

A = GH.ANCHORS
MAX_JSON = 262144
LENGTH = 78.4

KID, OLD, ADULT, GIANT = 0.75, 0.9, 1.0, 1.5
EYE = 1.62                       # player eye height at scale 1
LEG = 0.75                       # player leg length at scale 1 (12 px)

HAT = 'hogwartsmagic:sorting_hat'
SFX = 'hogwartsmagic:pensieve.sfx.hall_'
VOICE = 'hogwartsmagic:pensieve.voice.hall_'
MUSIC = 'hogwartsmagic:pensieve.music.hall_'


def tk(s):
    return int(round(s * 20))


def r2(v):
    return round(v, 2)


def r0(v):
    return int(round(v))


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


# ================================================================ beat sheet (seconds)
T_EYES = 0.0          # eyes_open (3.5 s); hall_hat_open 6.35 s
T_MCG_IN = 4.6        # McGonagall reaches the doors
T_MCG_LINE = 6.4      # hall_mcg_doors, 7.54 s
T_FOLLOW = 13.2       # "Follow me": she turns to the doors
T_RON_LINE = 13.95    # hall_ron_nervous, 1.59 s
T0 = 17.0             # the doors: lock thud, first groan, the south leaf starts (north 3 ticks later)
BOOM = T0 + 54 / 20   # 19.7: the south leaf hits 92 degrees
T_WALK = 20.4         # McGonagall leads in
T_CEIL = 24.8         # enchanted_ceiling reveal (3 s) and ceiling_shimmer
T_JUMP = 28.6         # the procession's hidden jump (the camera is on the staff table)
T_HERM_LINE = 32.0    # hall_hermione_nervous, 3.78 s
T_ARRIVE = 35.0       # first-years at their places
T_HUSH = 35.4
T_DUMB = 37.2         # hall_dumbledore, 3.84 s
T_CALL = 41.9         # hall_mcg_call, 6.19 s
T_GRANGER = 48.3      # hall_mcg_granger, 1.94 s
T_HAT_H = 51.4        # the hat on Hermione
T_SHOUT_H = 53.1      # sorting.voice.gryffindor, 2.68 s
T_WEASLEY = 57.1      # hall_mcg_weasley, 1.95 s
T_HAT_R = 59.6
T_SHOUT_R = 60.8
T_SCROLL = 64.2       # McGonagall looks down at the scroll
T_NAME = 65.9         # the name card (size 3, 4 s), whispers, prologue music
T_BOY_WALK = 69.4
T_BOY_SIT = 73.4
T_FP = 75.6           # first person on the stool
T_HAT_B = 77.4        # the hat comes down
T_CLOSE = 77.2        # eyes_close (1.2 s)

# ================================================================ places (scene coordinates)
HINGE_N, HINGE_S = A['doors_hinge_n'], A['doors_hinge_s']          # [65, 0, -3.5], [65, 0, 4.5]
DOOR_SCALE = A['door_leaf_scale']
DOOR_FACE_X = 67.0               # the entrance-hall face of the portal (wall x 64..66)
STOOL = A['stool']               # [0.5, 1.5, 0.5] seat top
STOOL_ROOT_KID = [STOOL[0], seat_root(STOOL[1], KID), STOOL[2]]
MCG_STOOL = [0.5, 1.0, -0.8]     # on the top step beside the stool, close enough to reach a head
AISLE_Z = 0.5
COL_Z = (-0.5, 1.5)              # the two walking files / waiting files in the 3-wide aisle (cells z -1..1)
SPOTS = A['first_year_spots']    # 12, three rows of four in front of the doors

# ================================================================ scene parts
actors = []
events = []
words = []
camera = []
poses = {
    'seated': {'right_leg': [-90, 8, 0], 'left_leg': [-90, -8, 0], 'right_arm': [-30, 0, 0],
               'left_arm': [-30, 0, 0]},
    'relax': {'head': [0, 0, 0]},
    'scroll': {'left_arm': [-55, 12, 0]},
    'scroll_read': {'left_arm': [-62, 14, 0], 'head': [28, 0, 0]},
    'welcome': {'right_arm': [-85, 0, 38], 'left_arm': [-85, 0, -38]},
    'hands_clasped': {'right_arm': [-38, -20, 0], 'left_arm': [-38, 20, 0]},
}


def ev(t, kind, eid, **kw):
    e = {'t': tk(t), 'kind': kind, 'id': eid}
    e.update(kw)
    events.append(e)


def sfx(t, name, volume=1.0):
    ev(t, 'sfx', SFX + name, volume=volume)


def voice(t, name, volume=1.0):
    ev(t, 'voice', VOICE + name, volume=volume)


# ---------------------------------------------------------------- a moving actor
class Mover:
    """Path and look keys for a walking/standing actor. Look overrides movement facing, so every walk writes its own
    facing: a short turn into each segment's direction. Yaw stays continuous (unwrapped near the current yaw)."""

    def __init__(self, pos, yaw, pitch=0.0):
        self.pos = list(pos)
        self.yaw = yaw
        self.pitch = pitch
        self.path = [(0.0, list(pos), None)]
        self.look = [(0.0, yaw, pitch, None)]

    def near(self, y):
        while y - self.yaw > 180:
            y -= 360
        while y - self.yaw < -180:
            y += 360
        return y

    def face(self, t, yaw, dur=0.4, pitch=None):
        p = self.pitch if pitch is None else pitch
        y = self.near(yaw)
        self.look.append((t, self.yaw, self.pitch, None))
        self.look.append((t + dur, y, p, None))
        self.yaw, self.pitch = y, p

    def face_point(self, t, pt, dur=0.4, pitch=None):
        self.face(t, yaw_to(pt[0] - self.pos[0], pt[2] - self.pos[2]), dur, pitch)

    def go(self, t, pts, turn=0.2, end_ease='out'):
        """Hold until t, then through pts [(time, pos)], turning into each segment's direction."""
        self.path.append((t, list(self.pos), None))
        prev_t, prev = t, self.pos
        for i, (ti, p) in enumerate(pts):
            dx, dz = p[0] - prev[0], p[2] - prev[2]
            if math.hypot(dx, dz) > 0.08 and ti - prev_t > 0.1:
                y = self.near(yaw_to(dx, dz))
                if abs(y - self.yaw) > 1:
                    if i == 0:
                        self.look += [(prev_t, self.yaw, self.pitch, None), (prev_t + turn, y, 0, None)]
                    else:
                        self.look += [(prev_t - turn / 2, self.yaw, self.pitch, None),
                                      (prev_t + turn / 2, y, 0, None)]
                    self.yaw, self.pitch = y, 0
            if len(pts) == 1:
                ease = None
            elif i == len(pts) - 1:
                ease = end_ease
            else:
                ease = 'linear'
            self.path.append((ti, list(p), ease))
            prev_t, prev = ti, p
        self.pos = list(prev)

    def place(self, t, p, ease='linear'):
        """A single key (continuing the path)."""
        self.path.append((t, list(p), ease))
        self.pos = list(p)

    def emit(self):
        path, look = dedupe(self.path), dedupe(self.look)
        out_p = []
        for t, p, e in path:
            k = {'t': tk(t), 'pos': [r2(p[0]), round(p[1], 4), r2(p[2])]}
            if e:
                k['ease'] = e
            out_p.append(k)
        out_l = []
        for t, y, pitch, e in look:
            k = {'t': tk(t), 'yaw': round(y, 1)}
            if abs(pitch) > 0.05:
                k['pitch'] = round(pitch, 1)
            if e:
                k['ease'] = e
            out_l.append(k)
        return out_p, out_l


def dedupe(keys):
    """Sorted by tick; on one tick the last key wins; asserts the input was already in time order."""
    out = {}
    last = -1
    for k in keys:
        t = tk(k[0])
        assert t >= last, ('keys out of order', keys)
        last = t
        out[t] = k
    return [out[t] for t in sorted(out)]


class Limbs:
    def __init__(self):
        self.parts = {}

    def key(self, part, t, v, ease=None):
        self.parts.setdefault(part, []).append((t, v, ease))

    def emit(self):
        out = {}
        for part, keys in self.parts.items():
            ks = []
            for t, v, e in dedupe(keys):
                k = {'t': tk(t), 'v': [r0(c) for c in v]}
                if e:
                    k['ease'] = e
                ks.append(k)
            out[part] = {'rot': ks}
        return out


def actor(aid, skin, scale, mover=None, path=None, look=None, limbs=None, pose_cues=None, actions=None, items=None,
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
    if limbs is not None:
        lm = limbs.emit()
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


# ================================================================ the doors (Addendum C)
def door_theta(rel):
    """Opening angle keys (ticks from the leaf's own start, degrees into the hall, ease of the segment ending there):
    anticipation, slow crack, heavy accelerating swing, overshoot, settle."""
    return [(0, 0, None), (6, -2, 'in_out'), (26, 10, 'in_out'), (54, 92, 'in'), (58, 86, 'out'), (64, 90, 'out')]


def door_actor(aid, atype, hinge, sign, lead_ticks):
    look = []
    for rel, th, e in door_theta(0):
        k = {'t': tk(T0) + lead_ticks + rel, 'yaw': -90 + sign * th}
        if e and e != 'in_out':
            k['ease'] = e
        look.append(k)
    actor(aid, None, DOOR_SCALE, path=[{'t': 0, 'pos': list(hinge)}], look=look, atype=atype)


# low-z (north) leaf turns -90 -> -180; high-z (south, mirrored) leaf turns -90 -> 0. Seen from the entrance hall
# (looking -X) the south leaf is on the left: it leads by 3 ticks.
door_actor('door_north', 'hogwartsmagic:memory_door', HINGE_N, -1, 3)
door_actor('door_south', 'hogwartsmagic:memory_door_mirrored', HINGE_S, +1, 0)

# ================================================================ McGonagall
mcg = Mover([69.0, 0, -9.0], 0)
mcg.go(0.3, [(T_MCG_IN, [68.5, 0, 0.5])])
mcg.face(T_MCG_IN + 0.1, -90, 0.7)                       # turns to the first-years
mcg.face(T_FOLLOW, 90, 0.6)                              # "Follow me": back to the doors
MCG_ROUTE = [(23.0, [60.0, 0, 0.5]), (T_JUMP, [42.0, 0, 0.5]),
             (T_JUMP + 0.05, [22.5, 0, 0.5]),            # hidden jump: the camera is on the staff table
             (33.6, [6.5, 0, 0.5]), (34.7, [2.7, 0, -0.3]), (35.1, [1.5, 1, -0.7]), (35.5, MCG_STOOL)]
mcg.go(T_WALK, MCG_ROUTE)
mcg.face(35.6, -90, 0.5)                                 # faces the first-years at the stool
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
    """The time in [lo, hi] when McGonagall's x passes x (bisection; mcg_x falls monotonically there)."""
    a, b = lo, hi
    for _ in range(40):
        m = (a + b) / 2
        if mcg_x(m) > x:
            a = m
        else:
            b = m
    return (a + b) / 2


# the hat placements: she turns to the stool (yaw 0), lifts the hat, lowers it on, and turns back to the scroll
mcg_limbs = Limbs()
mcg_actions = [{'t': 36.2, 'do': 'equip', 'slot': 'mainhand', 'item': HAT}]


def mcg_crown(t_on, t_back=None):
    """Hat goes on a head at t_on (arm up before, down after); if t_back, she takes it back then."""
    mcg.face(t_on - 1.0, 0, 0.4)
    mcg_limbs.key('right_arm', t_on - 0.8, [0, 0, 0])
    mcg_limbs.key('right_arm', t_on - 0.2, [-120, 0, -8])
    mcg_limbs.key('right_arm', t_on + 0.1, [-95, 0, -8])
    mcg_limbs.key('right_arm', t_on + 0.6, [0, 0, 0])
    mcg_actions.append({'t': t_on, 'do': 'equip', 'slot': 'mainhand', 'item': 'minecraft:air'})
    mcg.face(t_on + 0.7, -90, 0.5)


def mcg_take_back(t):
    mcg.face(t - 0.6, 0, 0.35)
    mcg_limbs.key('right_arm', t - 0.4, [0, 0, 0])
    mcg_limbs.key('right_arm', t - 0.05, [-110, 0, -8])
    mcg_limbs.key('right_arm', t + 0.4, [0, 0, 0])
    mcg_actions.append({'t': t, 'do': 'hold', 'item': HAT})
    mcg.face(t + 0.3, -90, 0.5)


mcg_crown(T_HAT_H)
mcg_take_back(54.4)
mcg_crown(T_HAT_R)
mcg_take_back(62.2)
# she reads the name, looks up at the boy, turns to the stool as he sits, and lifts the hat over him
mcg_limbs.key('head', T_NAME + 1.6, [0, 0, 0])
mcg_limbs.key('head', T_NAME + 2.2, [-20, -10, 0])       # looks up from the scroll (pose head 28 -> ~8)
mcg_limbs.key('head', T_BOY_SIT - 0.3, [-20, -10, 0])
mcg_limbs.key('head', T_BOY_SIT + 0.2, [-28, 0, 0])
# she steps down in front of him, so in his first person the hat comes over the top of the frame
mcg.go(T_BOY_SIT + 0.3, [(T_BOY_SIT + 0.9, [1.4, 1, -0.9]), (T_BOY_SIT + 1.4, [2.1, 0, -0.8])])
mcg.face_point(T_BOY_SIT + 1.5, STOOL, 0.5)
mcg_limbs.key('right_arm', 76.0, [0, 0, 0])
mcg_limbs.key('right_arm', 76.9, [-152, 0, -10])        # the hat high over him, into the top of his view
mcg_limbs.key('right_arm', T_HAT_B, [-135, 0, -8])
mcg_limbs.key('right_arm', T_HAT_B + 0.4, [-110, 0, -8])
mcg_actions.append({'t': T_HAT_B + 0.25, 'do': 'equip', 'slot': 'mainhand', 'item': 'minecraft:air'})
actor('mcgonagall', {'texture': 'mcgonagall'}, ADULT, mover=mcg, limbs=mcg_limbs,
      items={'offhand': 'minecraft:paper'},
      pose_cues=[pose(0, 'scroll', 1), pose(T_CALL - 0.3, 'scroll_read', 8), pose(T_SHOUT_H - 0.4, 'scroll', 8),
                 pose(T_WEASLEY - 0.6, 'scroll_read', 8), pose(T_SHOUT_R - 0.4, 'scroll', 8),
                 pose(T_SCROLL, 'scroll_read', 12)],
      actions=mcg_actions)

# ================================================================ the first-years
# spots: rows of four by distance from the doors; within a row by z. Walking pairs (pre-jump) follow the rows so
# nobody walks through anybody; after the hidden jump the files re-form (Hermione and Ron first, the boy last).
rows = [sorted(range(r * 4, r * 4 + 4), key=lambda i: SPOTS[i][2]) for r in range(3)]
FRONT = rows[0]                                          # front row, z ascending
RON_I, BOY_I, HERM_I = FRONT[0], FRONT[1], FRONT[2]
pre_pairs = []
for r in rows:
    pre_pairs.append((r[0], r[2]))                       # (left file, right file)
    pre_pairs.append((r[1], r[3]))
others = [i for i in range(12) if i not in (RON_I, BOY_I, HERM_I)]
post_pairs = [(HERM_I, RON_I), (others[0], others[1]), (others[2], others[3]), (others[4], others[5]),
              (others[6], others[7]), (others[8], BOY_I)]


def pair_of(i, pairs):
    for p, (a, b) in enumerate(pairs):
        if i == a:
            return p, 0
        if i == b:
            return p, 1
    raise KeyError(i)


SLOT_X0, SLOT_DX = 3.8, 1.4


def slot(i):
    p, c = pair_of(i, post_pairs)
    if i == BOY_I:
        return [SLOT_X0 + SLOT_DX * p - 0.6, 0, AISLE_Z]       # the boy waits last, in the middle lane
    return [SLOT_X0 + SLOT_DX * p, 0, COL_Z[c]]


def off_of(p):
    return 1.8 + 1.2 * p


kid_movers = {}
kid_limbs = {}
for i in range(12):
    spot = SPOTS[i]
    m = Mover(spot, 90)                                  # facing the doors
    kid_movers[i] = m
    kid_limbs[i] = Limbs()

rnd_kids = random.Random(1991)
for i in range(12):
    m = kid_movers[i]
    spot = SPOTS[i]
    # McGonagall arrives: they turn to her
    m.face_point(T_MCG_IN - 0.4 + rnd_kids.uniform(0, 0.6), [68.5, 0, 0.5], 0.5)
    # the procession, before the jump
    p, c = pair_of(i, pre_pairs)
    off = off_of(p)
    zc = COL_Z[c]
    t_start = mcg_x_inv(spot[0] - off - 0.3, T_WALK, T_JUMP)
    t_door = mcg_x_inv(67.5 - off, T_WALK, T_JUMP)
    pts = [(t_door, [67.5, 0, zc])]
    for tt in (23.0, 25.8):
        if tt > t_door + 0.3:
            pts.append((tt, [mcg_x(tt) + off, 0, zc]))
    pts.append((T_JUMP, [mcg_x(T_JUMP) + off, 0, zc]))
    # after the jump: the files re-formed
    p2, c2 = pair_of(i, post_pairs)
    off2 = off_of(p2)
    z2 = COL_Z[c2]
    pts.append((T_JUMP + 0.05, [mcg_x(T_JUMP + 0.05) + off2, 0, z2]))
    pts.append((33.6, [mcg_x(33.6) + off2, 0, z2]))
    s = slot(i)
    pts.append((T_ARRIVE + 0.08 * p2, s))
    m.go(t_start, pts)
    m.face_point(T_ARRIVE + 0.08 * p2 + 0.1, STOOL, 0.5)

# small life in the entrance hall: glances, Ron swallowing, Hermione fidgeting
for i in range(12):
    lm = kid_limbs[i]
    t = 7.2 + rnd_kids.uniform(0, 1.5)
    if i not in (BOY_I,):
        d = rnd_kids.choice([-1, 1]) * rnd_kids.uniform(25, 45)
        lm.key('head', t, [0, 0, 0])
        lm.key('head', t + 0.4, [-4, d, 0])
        lm.key('head', t + 1.6, [-4, d, 0])
        lm.key('head', t + 2.0, [0, 0, 0])
# Ron: swallows (head dips) on "be sorted", then turns to the boy (on his +z side) for his line
rl = kid_limbs[RON_I]
rl.key('head', 11.6, [0, 0, 0])
rl.key('head', 11.9, [22, 0, 0])
rl.key('head', 12.6, [22, 0, 0])
rl.key('head', 13.0, [0, 0, 0])
rl.key('head', T_RON_LINE - 0.2, [0, 0, 0])
rl.key('head', T_RON_LINE + 0.2, [6, -40, 0])
rl.key('head', T_RON_LINE + 1.7, [6, -40, 0])
rl.key('head', T_RON_LINE + 2.1, [0, 0, 0])
# the boy looks at Ron while he speaks
bl = kid_limbs[BOY_I]
bl.key('head', T_RON_LINE + 0.1, [0, 0, 0])
bl.key('head', T_RON_LINE + 0.5, [4, 40, 0])
bl.key('head', T_RON_LINE + 1.8, [4, 40, 0])
bl.key('head', T_RON_LINE + 2.2, [0, 0, 0])
# Hermione whispers to Ron while they walk (Ron is on her +z side, she walks facing -X: his side is her right)
hl = kid_limbs[HERM_I]
hl.key('head', T_HERM_LINE - 0.3, [0, 0, 0])
hl.key('head', T_HERM_LINE + 0.1, [5, 45, 0])
hl.key('head', T_HERM_LINE + 3.5, [5, 45, 0])
hl.key('head', T_HERM_LINE + 3.9, [0, 0, 0])
# every first-year turns to the boy when his name is read (he stands behind them all)
for i in range(12):
    if i in (BOY_I, HERM_I, RON_I):
        continue
    s = slot(i)
    lm = kid_limbs[i]
    t = T_NAME + 0.4 + rnd_kids.uniform(0, 0.9)
    m_yaw = kid_movers[i].yaw
    d = clamp(wrap(yaw_to(slot(BOY_I)[0] - s[0], slot(BOY_I)[2] - s[2]) - m_yaw), -80, 80)
    lm.key('head', t, [0, 0, 0])
    lm.key('head', t + 0.5, [0, d, 0])
    lm.key('head', T_BOY_WALK + 1.2, [0, d * 0.6, 0])
    lm.key('head', T_BOY_WALK + 2.4, [0, 0, 0])

# ---- Hermione: called, rushes up, sits, the hat, Gryffindor, runs to the table
H_SEAT = [12.5, seat_root(0.5, KID), -1.5]                # Gryffindor bench on the aisle side, faces -Z (yaw 180)
R_SEAT = [13.5, seat_root(0.5, KID), -1.5]
hm = kid_movers[HERM_I]
hm.go(T_GRANGER + 0.6, [(T_GRANGER + 1.05, [2.6, 0, 0.2]), (T_GRANGER + 1.4, [1.6, 1, 0.5]),
                        (T_GRANGER + 1.7, [0.9, 1, 0.5])])
hm.face(T_GRANGER + 1.7, -90, 0.3)
hm.place(T_GRANGER + 2.05, STOOL_ROOT_KID, 'in_out')
hm.place(54.5, STOOL_ROOT_KID, None)
hm.go(54.5, [(54.8, [1.0, 1, 0.5]), (55.2, [2.4, 0, 0.0]), (56.9, [12.0, 0, -0.8])])
hm.face(56.95, 180, 0.3)
hm.place(57.3, H_SEAT, 'in_out')
herm_poses = [pose(T_GRANGER + 2.0, 'seated', 5), pose(T_GRANGER + 2.6, 'hands_clasped', 8), pose(54.4, 'relax', 4),
              pose(57.2, 'seated', 5)]
herm_actions = [{'t': T_HAT_H, 'do': 'equip', 'slot': 'head', 'item': HAT},
                {'t': 54.4, 'do': 'equip', 'slot': 'head', 'item': 'minecraft:air'}]
# she beams on the shout: chin up, a little bounce of the arms
hl.key('head', T_SHOUT_H + 0.2, [0, 0, 0])
hl.key('head', T_SHOUT_H + 0.6, [-18, 0, 0])
hl.key('head', T_SHOUT_H + 1.3, [-18, 0, 0])
hl.key('head', T_SHOUT_H + 1.6, [0, 0, 0])

# ---- Ron: called, walks up quicker than he would like, sits, the hat, Gryffindor, sags with relief
rm = kid_movers[RON_I]
rm.go(T_WEASLEY + 0.5, [(T_WEASLEY + 1.05, [2.6, 0, 0.8]), (T_WEASLEY + 1.4, [1.6, 1, 0.5]),
                        (T_WEASLEY + 1.65, [0.9, 1, 0.5])])
rm.face(T_WEASLEY + 1.65, -90, 0.3)
rm.place(T_WEASLEY + 1.95, STOOL_ROOT_KID, 'in_out')
rm.place(62.3, STOOL_ROOT_KID, None)
rm.go(62.3, [(62.6, [1.0, 1, 0.5]), (63.0, [2.4, 0, 0.1]), (65.3, [13.0, 0, -0.8])])
rm.face(65.35, 180, 0.3)
rm.place(65.7, R_SEAT, 'in_out')
ron_poses = [pose(T_WEASLEY + 1.9, 'seated', 5), pose(T_WEASLEY + 2.4, 'hands_clasped', 8), pose(62.2, 'relax', 4),
             pose(65.6, 'seated', 5)]
ron_actions = [{'t': T_HAT_R, 'do': 'equip', 'slot': 'head', 'item': HAT},
               {'t': 62.2, 'do': 'equip', 'slot': 'head', 'item': 'minecraft:air'}]
rl.key('head', T_HAT_R - 0.2, [0, 0, 0])
rl.key('head', T_HAT_R + 0.3, [-12, 0, 0])               # eyes up at the brim
rl.key('head', T_SHOUT_R + 0.3, [-12, 0, 0])
rl.key('head', T_SHOUT_R + 0.8, [32, 0, 0])              # sags with relief
rl.key('head', 62.1, [32, 0, 0])
rl.key('head', 62.5, [0, 0, 0])
rl.key('body', T_SHOUT_R + 0.3, [0, 0, 0])
rl.key('body', T_SHOUT_R + 0.8, [14, 0, 0])
rl.key('body', 62.1, [14, 0, 0])
rl.key('body', 62.5, [0, 0, 0])

# ---- the boy: last in line; walks up alone, sits, the hat comes down
bm = kid_movers[BOY_I]
b_start = slot(BOY_I)
bm.go(T_BOY_WALK + 0.1, [(T_BOY_WALK + 2.9, [2.6, 0, 0.5]), (T_BOY_WALK + 3.3, [1.6, 1, 0.5]),
                         (T_BOY_WALK + 3.55, [0.9, 1, 0.5])])
bm.face(T_BOY_WALK + 3.55, -90, 0.3)
bm.place(T_BOY_SIT, STOOL_ROOT_KID, 'in_out')
boy_poses = [pose(T_BOY_SIT - 0.1, 'seated', 5), pose(T_BOY_SIT + 0.6, 'hands_clasped', 8)]
boy_actions = [{'t': T_HAT_B + 0.25, 'do': 'equip', 'slot': 'head', 'item': HAT}]


def boy_pos(t):
    """Where the boy is (for heads turning to him), from his own path keys."""
    keys = dedupe(bm.path)
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, p0, _), (t1, p1, _) in zip(keys, keys[1:]):
        if t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 1
            return [p0[k] + (p1[k] - p0[k]) * f for k in range(3)]
    return keys[-1][1]


for i in range(12):
    if i == BOY_I:
        aid, skin = 'boy', {'viewer': True}
        pc, ac = boy_poses, boy_actions
    elif i == HERM_I:
        aid, skin = 'hermione', {'texture': 'hermione'}
        pc, ac = herm_poses, herm_actions
    elif i == RON_I:
        aid, skin = 'ron', {'texture': 'ron'}
        pc, ac = ron_poses, ron_actions
    else:
        aid, skin = 'first_year_%d' % i, {'texture': 'first_year'}
        pc, ac = None, None
    actor(aid, skin, KID, mover=kid_movers[i], limbs=kid_limbs[i], pose_cues=pc, actions=ac)

# ================================================================ the staff table
STAFF = A['staff_seats']        # [x, seat_top, z, yaw]; 0 is the throne


def staff_seated(aid, skin, seat, scale, limbs=None, pose_cues=None, look=None):
    x, top, z, yaw = seat
    root = [x, seat_root(top, scale), z]
    actor(aid, {'texture': skin}, scale, path=[{'t': 0, 'pos': root}],
          look=look or [{'t': 0, 'yaw': yaw}], limbs=limbs,
          pose_cues=pose_cues or [pose(0, 'seated', 1)])


# Dumbledore rises for his welcome, arms open, and sits again
THRONE = [STAFF[0][0], seat_root(STAFF[0][1], ADULT), STAFF[0][2]]
DUMB_UP = [-13.9, 1.0, 0.5]                              # standing behind the table, in front of the throne
dm = Mover(THRONE, STAFF[0][3])
dm.place(T_DUMB - 0.6, THRONE, None)
dm.place(T_DUMB - 0.1, DUMB_UP, 'in_out')
dm.place(T_DUMB + 4.3, DUMB_UP, None)
dm.place(T_DUMB + 4.9, THRONE, 'in_out')
dlm = Limbs()
for t, v in ((0, [6, 0, 0]), (14.0, [6, 0, 0]), (19.9, [6, 0, 0]), (20.6, [0, 12, 0]), (26.0, [0, 4, 0]),
             (31.0, [8, 0, 0]), (T_NAME + 0.3, [8, 0, 0]), (T_NAME + 1.0, [10, 18, 0]), (T_BOY_SIT, [6, 0, 0])):
    dlm.key('head', t, v)
actor('dumbledore', {'texture': 'dumbledore'}, ADULT, mover=dm, limbs=dlm,
      pose_cues=[pose(0, 'seated', 1), pose(T_DUMB - 0.6, 'relax', 6), pose(T_DUMB, 'welcome', 10),
                 pose(T_DUMB + 3.6, 'relax', 10), pose(T_DUMB + 4.8, 'seated', 6)])

# Snape: still, staring at the newcomers, then at the boy
slm = Limbs()
for t, v in ((0, [10, 0, 0]), (20.0, [10, 0, 0]), (20.8, [2, 10, 0]), (31.0, [6, 0, 0]),
             (T_NAME + 0.8, [6, 0, 0]), (T_NAME + 1.3, [4, -14, 0])):
    slm.key('head', t, v)
staff_seated('snape', 'snape', STAFF[4], ADULT, limbs=slm)

# Quirrell: twitchy little jerks of the head
qlm = Limbs()
rq = random.Random(7)
t = 0.4
while t < LENGTH - 1:
    v = [rq.uniform(-8, 14), rq.uniform(-35, 35), rq.uniform(-6, 6)]
    qlm.key('head', t, v, 'out')
    t += rq.uniform(0.7, 1.6)
staff_seated('quirrell', 'quirrell', STAFF[6], ADULT, limbs=qlm)

# Hagrid (1.5): grinning, a big wave at the first-years as they come up the aisle, and a clap for Hermione
hlm = Limbs()
for t, v in ((0, [0, 0, 0]), (31.5, [0, 0, 0]), (32.1, [-160, 0, 14]), (32.5, [-160, 0, 34]),
             (32.9, [-160, 0, 8]), (33.3, [-160, 0, 34]), (33.7, [-160, 0, 8]), (34.3, [0, 0, 0])):
    hlm.key('right_arm', t, v)
for base in (T_SHOUT_H + 0.4, T_SHOUT_R + 0.4):
    for k in range(4):
        hlm.key('right_arm', base + 0.3 * k, [-50, 0, 6])
        hlm.key('right_arm', base + 0.3 * k + 0.15, [-55, 0, -20])
        hlm.key('left_arm', base + 0.3 * k, [-50, 0, -6])
        hlm.key('left_arm', base + 0.3 * k + 0.15, [-55, 0, 20])
    hlm.key('right_arm', base + 1.5, [0, 0, 0])
    hlm.key('left_arm', base + 1.5, [0, 0, 0])
hlm.key('left_arm', 0, [0, 0, 0])
hlm.parts['left_arm'].sort(key=lambda k: k[0])
hlm.parts['right_arm'].sort(key=lambda k: k[0])
for t, v in ((0, [-4, 0, 0]), (20.0, [-4, 0, 0]), (20.8, [0, 14, 0]), (31.0, [4, 10, 0])):
    hlm.key('head', t, v)
staff_seated('hagrid', 'hagrid', STAFF[7], GIANT, limbs=hlm)

# another teacher, so the high table is not empty beside Dumbledore
tlm = Limbs()
for t, v in ((0, [4, -20, 0]), (6.0, [4, 15, 0]), (12.0, [0, -10, 0]), (20.2, [0, 0, 0]), (21.0, [4, 12, 0]),
             (31.0, [8, 0, 0])):
    tlm.key('head', t, v)
staff_seated('teacher', 'sirius', STAFF[3], ADULT, limbs=tlm)

# ================================================================ the house tables
SEATS = A['table_seats']
PER_SIDE = 14
HOUSE_SKINS = {h: ('student_' + h, 'student_%s_b' % h) for h in SEATS}
RESERVED = {(12.5, -1.5), (13.5, -1.5)}                   # Hermione's and Ron's seats
AISLE_BENCH_Z = (-1.5, 2.5)                              # the two benches with their backs to the aisle

DOOR_TARGET = [64.5, 5.5, 0.5]
STOOL_TARGET = [0.5, 2.4, -0.2]                           # the stool and McGonagall beside it
DUMB_TARGET = [-13.9, 2.6, 0.5]


def herm_pos(t):
    keys = dedupe(hm.path)
    for (t0, p0, _), (t1, p1, _) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 1
            return [p0[k] + (p1[k] - p0[k]) * f for k in range(3)]
    return keys[-1][1] if t > keys[-1][0] else keys[0][1]


def ron_pos(t):
    keys = dedupe(rm.path)
    for (t0, p0, _), (t1, p1, _) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 1
            return [p0[k] + (p1[k] - p0[k]) * f for k in range(3)]
    return keys[-1][1] if t > keys[-1][0] else keys[0][1]


def pick_seats(house, rnd):
    out = []
    for side_z in sorted({s[2] for s in SEATS[house]}):
        side = [s for s in SEATS[house] if s[2] == side_z and (s[0], s[2]) not in RESERVED]
        if side_z in AISLE_BENCH_Z:
            side = [s for s in side if s[0] >= 14.5]      # leave room behind the waiting first-years
        xs = sorted(side, key=lambda s: s[0])
        # spread PER_SIDE seats along the bench, each jittered, never two side by side
        n = len(xs)
        chosen = []
        for k in range(PER_SIDE):
            j = int((k + 0.5) * n / PER_SIDE + rnd.uniform(-0.9, 0.9))
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

    def __init__(self, seat, scale, limbs):
        self.x, top, self.z, self.yaw0 = seat
        self.eye = [self.x, seat_root(top, scale) + EYE * scale, self.z]
        self.limbs = limbs
        self.look = [(0.0, self.yaw0, 0.0, None)]
        self.hy, self.hx, self.body = 0.0, 0.0, 0.0
        limbs.key('head', 0.0, [0, 0, 0])

    def rel_to(self, p):
        dx, dy, dz = p[0] - self.eye[0], p[1] - self.eye[1], p[2] - self.eye[2]
        d = wrap(yaw_to(dx, dz) - self.yaw0)
        pitch = math.degrees(math.atan2(-dy, math.hypot(dx, dz)))
        return d, pitch

    def set(self, t, d, pitch, dur=0.0, hold=False, body=True):
        shift = clamp(0.35 * d, -28, 28) if body else self.body
        hy = clamp(d - shift, -72, 72)
        hx = clamp(pitch, -30, 35)
        if hold:
            self.limbs.key('head', t, [self.hx, self.hy, 0])
            if body and abs(shift - self.body) > 1:
                self.look.append((t, self.yaw0 + self.body, 0, None))
        self.limbs.key('head', t + dur, [hx, hy, 0])
        if body and abs(shift - self.body) > 1:
            self.look.append((t + dur, self.yaw0 + shift, 0, None))
            self.body = shift
        self.hy, self.hx = hy, hx

    def at(self, t, p, dur=0.0, hold=False, body=True):
        d, pitch = self.rel_to(p)
        self.set(t, d, pitch, dur, hold, body)

    def emit_look(self):
        return [dict({'t': tk(t), 'yaw': round(y, 1)}) for t, y, _, _ in dedupe(self.look)]


def arms_cheer(lm, t0, big):
    ups = [(0.25, -140, 15), (0.6, -115, 28), (0.95, -152, 10), (1.3, -120, 26), (1.7, -148, 12), (2.4, -132, 18)]
    if not big:
        ups = [(0.25, -135, 15), (0.65, -112, 28), (1.05, -145, 12), (1.6, -128, 18)]
    lm.key('right_arm', t0, [0, 0, 0])
    lm.key('left_arm', t0, [0, 0, 0])
    for dt, x, z in ups:
        lm.key('right_arm', t0 + dt, [x, 0, z])
        lm.key('left_arm', t0 + dt, [x, 0, -z])
    end = t0 + ups[-1][0] + 0.6
    lm.key('right_arm', end, [0, 0, 0])
    lm.key('left_arm', end, [0, 0, 0])


def arms_clap(lm, t0, claps=3):
    lm.key('right_arm', t0, [0, 0, 0])
    lm.key('left_arm', t0, [0, 0, 0])
    for k in range(claps):
        t = t0 + 0.2 + 0.32 * k
        lm.key('right_arm', t, [-30, 0, 8])
        lm.key('left_arm', t, [-30, 0, -8])
        lm.key('right_arm', t + 0.16, [-32, 0, -22])
        lm.key('left_arm', t + 0.16, [-32, 0, 22])
    end = t0 + 0.2 + 0.32 * claps + 0.3
    lm.key('right_arm', end, [0, 0, 0])
    lm.key('left_arm', end, [0, 0, 0])


rnd_s = random.Random(1991_09_01)
n_students = 0
for house in ('hufflepuff', 'gryffindor', 'ravenclaw', 'slytherin'):
    for k, seat in enumerate(pick_seats(house, rnd_s)):
        n_students += 1
        lm = Limbs()
        g = Gaze(seat, OLD, lm)
        J = lambda a, b: rnd_s.uniform(a, b)                       # noqa: E731
        # before the doors: chatting with neighbours, two idle turns
        g.set(0.0, J(-45, 45), J(-6, 10), body=False)
        g.set(J(4, 8), J(-50, 50), J(-6, 10), J(0.4, 0.8), hold=True, body=False)
        g.set(J(11, 16), J(-50, 50), J(-6, 10), J(0.4, 0.8), hold=True, body=False)
        # the boom: everyone turns to the doors, then follows the procession
        g.at(BOOM + J(0.05, 1.0), DOOR_TARGET, 0.45, hold=True)
        g.at(23.6 + J(0, 0.4), [mcg_x(23.6), 1.4, AISLE_Z])
        g.at(27.4 + J(0, 0.4), [mcg_x(27.4), 1.4, AISLE_Z])
        g.at(31.2 + J(0, 0.3), [mcg_x(31.2), 1.4, AISLE_Z])
        g.at(33.8 + J(0, 0.3), [mcg_x(33.8), 1.4, AISLE_Z])
        g.at(35.8 + J(0, 0.5), STOOL_TARGET)
        # Dumbledore's welcome, then McGonagall's instructions
        g.at(T_DUMB + J(0, 0.6), DUMB_TARGET, 0.5, hold=True, body=False)
        g.at(T_CALL + J(0, 0.6), STOOL_TARGET, 0.5, hold=True, body=False)
        # Hermione up and on the stool; running to her table; Ron up; Ron to his seat
        g.at(T_GRANGER + 1.1, herm_pos(T_GRANGER + 1.1), body=False)
        g.at(T_GRANGER + 2.2, STOOL_TARGET, body=False)
        g.at(56.0 + J(0, 0.3), [herm_pos(56.0)[0], 1.0, herm_pos(56.0)[2]], body=False)
        g.at(T_WEASLEY + 1.4, STOOL_TARGET, body=False)
        g.at(63.6 + J(0, 0.3), [ron_pos(63.6)[0], 1.0, ron_pos(63.6)[2]], body=False)
        g.at(T_SCROLL + J(0, 0.6), STOOL_TARGET)
        # the name: every head in the hall turns to the boy, and follows him to the stool
        bp = boy_pos(T_NAME)
        g.at(T_NAME + 0.3 + J(0, 1.2), [bp[0], 1.1, bp[2]], 0.5, hold=True)
        p = boy_pos(T_BOY_WALK + 1.6)
        g.at(T_BOY_WALK + 1.6, [p[0], 1.1, p[2]])
        g.at(T_BOY_SIT + 0.3, [STOOL[0], 2.1, STOOL[2]])
        # the cheers: Gryffindor on its feet (arms up, staggered 0-6 ticks); Hufflepuff and Ravenclaw clap
        if house == 'gryffindor':
            arms_cheer(lm, T_SHOUT_H + 0.25 + J(0, 0.3), True)
            if rnd_s.random() < 0.75:
                arms_cheer(lm, T_SHOUT_R + 0.25 + J(0, 0.3), False)
        elif house != 'slytherin':
            if rnd_s.random() < 0.55:
                arms_clap(lm, T_SHOUT_H + 0.4 + J(0, 0.4))
            if rnd_s.random() < 0.4:
                arms_clap(lm, T_SHOUT_R + 0.4 + J(0, 0.4))
        for part in ('right_arm', 'left_arm'):
            if part in lm.parts:
                lm.parts[part].sort(key=lambda kk: kk[0])
        x, top, z, yaw = seat
        skin = HOUSE_SKINS[house][(k + (1 if rnd_s.random() < 0.3 else 0)) % 2]
        actor('%s_%d' % (house, k), {'texture': skin}, OLD,
              path=[{'t': 0, 'pos': [x, seat_root(top, OLD), z]}], look=g.emit_look(), limbs=lm,
              pose_cues=[pose(0, 'seated', 1)])

# ================================================================ events
ev(0, 'effect', 'eyes_open', at=[0, 0, 0], duration=tk(3.5))
ev(0, 'effect', 'floating_candles', at=[27.0, 11.0, 0.5], spread=[33.0, 4.5, 15.5], count=900)
voice(T_EYES + 0.1, 'hat_open')
sfx(0.6, 'steps_group', 0.35)
sfx(4.2, 'murmur_a', 0.3)                                 # the hall, muffled behind the doors
voice(T_MCG_LINE, 'mcg_doors')
voice(T_RON_LINE, 'ron_nervous')
# the doors
ev(T0, 'sound', 'minecraft:block.wooden_door.close', at=[65.5, 2, 0.5], volume=1.6, pitch=0.5)    # the lock
ev(T0 + 0.05, 'sound', 'minecraft:block.iron_trapdoor.open', at=[65.5, 3, 0.5], volume=0.9, pitch=0.55)
sfx(T0, 'door_groan', 1.0)
ev(T0 + 1.0, 'sound', SFX + 'door_groan', at=[65.5, 6, 0.5], volume=1.4, pitch=0.98)   # x0.92: a deeper ~0.9
sfx(T0 - 0.1, 'murmur_b', 1.0)                            # the hall's noise rises as the gap opens
ev(T0 + 0.3, 'effect', 'light_rays', at=[DOOR_FACE_X, 0, 0.5], yaw=-90, spread=[3.5, 12, 24], duration=48)
for t, cells in ((T0 + 1.3, [[67, 1, 0]]), (T0 + 2.0, [[69, 1, -2], [69, 1, 3]]),
                 (BOOM, [[71, 2, 0], [73, 2, -3], [73, 2, 4], [75, 2, 0]])):
    for c in cells:
        ev(t, 'block', 'minecraft:light', at=c)
sfx(BOOM, 'door_boom', 1.0)
ev(BOOM, 'music', MUSIC + 'entry', volume=1.0)
ev(BOOM + 0.05, 'particles', 'minecraft:white_ash', at=[66.2, 11.6, 0.5], count=80, spread=[0.6, 0.3, 3.6])
ev(BOOM + 0.2, 'particles', 'minecraft:white_ash', at=[63.8, 11.4, 0.5], count=50, spread=[0.6, 0.3, 3.6])
sfx(T_WALK + 0.2, 'steps_group', 0.9)
sfx(T_JUMP - 0.1, 'steps_group', 0.7)
# the reveal
ev(T_CEIL, 'effect', 'enchanted_ceiling', at=A['ceiling_center'], spread=A['ceiling_spread'], duration=60,
   style='storm')
sfx(T_CEIL, 'ceiling_shimmer', 0.9)
voice(T_HERM_LINE, 'hermione_nervous')
sfx(T_HUSH, 'hush', 1.0)
voice(T_DUMB, 'dumbledore')
voice(T_CALL, 'mcg_call')
voice(T_GRANGER, 'mcg_granger')
sfx(T_GRANGER + 0.6, 'steps_one', 0.6)
sfx(T_GRANGER + 1.95, 'stool', 0.8)
ev(T_SHOUT_H, 'voice', 'hogwartsmagic:sorting.voice.gryffindor', volume=1.0)
sfx(T_SHOUT_H + 0.3, 'cheer_big', 1.0)
voice(T_WEASLEY, 'mcg_weasley')
sfx(T_WEASLEY + 0.5, 'steps_one', 0.6)
sfx(T_WEASLEY + 1.85, 'stool', 0.8)
ev(T_SHOUT_R, 'voice', 'hogwartsmagic:sorting.voice.gryffindor', volume=1.0)
sfx(T_SHOUT_R + 0.3, 'cheer_small', 1.0)
ev(T_NAME - 0.2, 'music', MUSIC + 'prologue', volume=1.0)
sfx(T_NAME + 0.2, 'whispers', 0.9)
sfx(T_BOY_WALK + 0.1, 'steps_one', 1.0)
sfx(T_BOY_SIT - 0.1, 'stool', 1.0)
sfx(T_HAT_B - 0.3, 'hat_brim', 1.0)
ev(T_CLOSE, 'effect', 'eyes_close', at=[0, 0, 0], duration=tk(LENGTH - T_CLOSE))

words.append({'t': tk(T_NAME), 'text': '{viewer}', 'narration': True, 'duration': tk(4.0), 'size': 3})

# ================================================================ camera
cam_lin = set()          # indices of keys written as linear (the shake shot)


def key(t, pos, look, fov=70, cut=False, ease=None, roll=None):
    k = {'t': tk(t), 'pos': [round(c, 3) for c in pos]}
    if look is not None:
        k['look_at'] = look if isinstance(look, str) else [round(c, 3) for c in look]
    if fov != 70:
        k['fov'] = fov
    if roll:
        k['roll'] = roll
    if ease:
        k['ease'] = ease
    if cut:
        k['cut'] = True
    camera.append(k)


boy_spot = SPOTS[BOY_I]
herm_spot = SPOTS[HERM_I]
ron_spot = SPOTS[RON_I]
BOY_EYE = EYE * KID
# A  0-4: first person at the boy's eye height in the entrance hall, looking up at the towering doors
fp0 = [boy_spot[0] - 0.3, BOY_EYE, boy_spot[2]]
key(0, fp0, [65.0, 10.5, 0.4], 74)
key(2.6, [fp0[0] - 0.05, BOY_EYE, fp0[2]], [65.0, 8.0, 0.3], 72)
key(4.0, [fp0[0] - 0.1, BOY_EYE, fp0[2]], [67.6, 1.9, -0.6], 68)
# B1 4-7.6: behind the first-years, McGonagall arrives at the doors and turns to them
key(4.0, [76.6, 2.3, 3.2], [68.5, 1.75, 0.5], 58, cut=True)
key(7.6, [75.2, 2.05, 2.5], [68.5, 1.7, 0.5], 52)
# B2 7.6-10.6: slow lateral dolly across their faces, from beside McGonagall
key(7.6, [69.3, 1.35, -2.4], 'actor:ron', 50, cut=True)
key(9.1, [69.35, 1.35, 0.3], 'actor:boy', 50)
key(10.6, [69.3, 1.35, 2.7], 'actor:hermione', 50)
# B3 10.6-13.6: McGonagall from a first-year's height ("Smarten yourselves up. Follow me.")
key(10.6, [69.85, 1.15, 0.5], 'actor:mcgonagall', 46, cut=True)
key(13.6, [69.95, 1.12, 0.5], 'actor:mcgonagall', 41)
# B4 13.6-15.7: Ron's whisper to the boy
key(13.6, [69.5, 1.3, -2.7], 'actor:ron', 46, cut=True)
key(15.7, [69.6, 1.28, -2.5], 'actor:ron', 43)
# C1 15.7-17.9: the low push-in on the doors (the lock, the creak, the first line of light)
key(15.7, [72.2, 0.55, 0.5], [65.0, 6.6, 0.5], 70, cut=True)
key(17.9, [70.3, 0.6, 0.5], [65.0, 6.2, 0.5], 64)
# C2 17.9-20.4: one held wide behind the first-years for the whole swing; the boom shakes it
C2A, C2B = 17.9, 20.4
P2A, P2B = [78.4, 2.6, 2.0], [77.4, 2.45, 1.8]
L2 = [65.0, 5.2, 0.5]


def c2(t):
    f = (t - C2A) / (C2B - C2A)
    return [P2A[k] + (P2B[k] - P2A[k]) * f for k in range(3)]


key(C2A, P2A, L2, 78, cut=True)
key(BOOM, c2(BOOM), L2, 78, ease='linear')
for dt, o in ((0.1, (0.08, 0.06, -0.05)), (0.2, (-0.06, -0.08, 0.05)), (0.3, (0.03, 0.04, -0.02))):
    p = c2(BOOM + dt)
    key(BOOM + dt, [p[0] + o[0], p[1] + o[1], p[2] + o[2]], L2, 78, ease='linear')
key(C2B, P2B, L2, 77, ease='linear')
# C3 20.4-23.0: the reverse, inside the hall: the doors settled, McGonagall leading the first-years in
key(C2B, [57.6, 1.45, -2.2], [66.0, 3.4, 0.5], 62, cut=True)
key(23.0, [57.0, 1.5, -1.8], [64.0, 2.4, 0.5], 60)
# D1 23.0-28.0: the crane: from the aisle up through the candles, tilting up as the ceiling dissolves into storm
key(23.0, [53.5, 1.7, 0.5], [20.0, 3.0, 0.5], 74, cut=True)
key(25.0, [50.5, 7.5, 0.5], [20.0, 7.0, 0.5], 78)
key(28.0, [44.5, 12.5, 0.5], [22.0, 19.5, 0.5], 86)
# D2 28.0-31.4: a slow track along the staff table: Hagrid, Dumbledore, Snape, Quirrell
key(28.0, [-5.8, 3.0, -6.2], 'actor:hagrid', 50, cut=True)
key(29.8, [-5.8, 3.0, -1.0], 'actor:dumbledore', 48)
key(31.4, [-5.8, 3.0, 4.2], 'actor:quirrell', 48)
# D3 31.4-36.2: the procession comes up the aisle toward us (Hermione whispering), we back away over the stool
key(31.4, [7.5, 2.0, 0.5], 'actor:mcgonagall', 56, cut=True)
key(34.0, [3.2, 2.5, 0.5], 'actor:mcgonagall', 58)
key(36.2, [-1.6, 3.1, 0.5], 'actor:hermione', 58)
# E1 36.2-39.2: wide from behind the first-years: the stool, McGonagall with the hat, Dumbledore rising
key(36.2, [13.5, 3.6, 0.5], [-3.0, 2.2, 0.5], 60, cut=True)
key(39.2, [12.3, 3.4, 0.5], [-4.0, 2.4, 0.5], 56)
# E2 39.2-41.8: Dumbledore, arms open
key(39.2, [-8.6, 2.9, -1.3], 'actor:dumbledore', 48, cut=True)
key(41.8, [-8.9, 2.85, -1.1], 'actor:dumbledore', 45)
# E3 41.8-45.2: McGonagall with the scroll and the hat ("When I call your name...")
key(41.8, [3.4, 2.15, -2.3], 'actor:mcgonagall', 50, cut=True)
key(45.2, [2.9, 2.2, -1.9], 'actor:mcgonagall', 46)
# E4 45.2-48.4: the first-years' faces, waiting
key(45.2, [1.7, 1.9, 0.5], [5.6, 1.1, 0.5], 56, cut=True)
key(48.4, [1.7, 1.85, 0.2], [5.0, 1.0, 0.4], 52)
# F1 48.4-50.7: "Granger, Hermione." She bolts up to the stool
key(48.4, [3.0, 1.9, 2.4], 'actor:hermione', 54, cut=True)
key(50.7, [2.6, 2.0, 2.0], 'actor:hermione', 50)
# F2 50.7-53.2: close: the hat comes down on her; a beat
key(50.7, [2.7, 1.8, 0.5], 'actor:hermione', 44, cut=True)
key(53.2, [2.4, 1.85, 0.5], 'actor:hermione', 40)
# F3 53.2-57.0: cut on the shout: the Gryffindor table erupts; she runs to it
key(53.2, [4.6, 3.6, -4.0], [26.0, 1.0, -4.0], 72, cut=True)
key(57.0, [5.2, 3.4, -3.4], [16.0, 0.8, -2.4], 66)
# G1 57.0-59.4: "Weasley, Ronald." Ron walks up
key(57.0, [6.2, 2.3, 3.6], 'actor:ron', 52, cut=True)
key(59.4, [5.4, 2.3, 3.2], 'actor:ron', 48)
# G2 59.4-61.9: close on Ron under the hat; Gryffindor; he sags
key(59.4, [2.4, 2.05, 1.7], 'actor:ron', 44, cut=True)
key(61.9, [2.2, 2.0, 1.5], 'actor:ron', 42)
# G3 61.9-64.4: wider: the cheer, Ron off to his table
key(61.9, [11.2, 2.6, 0.5], 'actor:ron', 58, cut=True)
key(64.4, [11.0, 2.6, 0.1], 'actor:ron', 60)
# H1 64.4-66.7: silence: McGonagall looks down at the scroll (slow push-in)
key(64.4, [2.3, 2.6, -1.6], 'actor:mcgonagall', 40, cut=True)
key(66.7, [1.6, 2.65, -1.3], 'actor:mcgonagall', 33)
# H2 66.7-69.4: the name, large; high over the stool: every head in the hall turns to the boy
key(66.7, [-3.0, 6.2, 0.5], [16.0, 0.6, 0.5], 72, cut=True)
key(69.4, [-2.2, 5.9, 0.5], [15.0, 0.6, 0.5], 68)
# I1 69.4-71.6: he walks up alone: tracking from behind
key(69.4, [12.6, 1.9, 0.5], 'actor:boy', 56, cut=True)
key(71.6, [7.0, 1.9, 0.5], 'actor:boy', 54)
# I2 71.6-73.6: frontal, from behind the stool, as he comes
key(71.6, [-0.4, 2.35, 0.5], 'actor:boy', 52, cut=True)
key(73.6, [-0.5, 2.4, 0.5], 'actor:boy', 56)
# I3 73.6-75.6: over his shoulder on the stool: the four tables, every face on him
key(73.6, [-1.6, 2.7, -0.7], [20.0, 1.4, 0.6], 66, cut=True)
key(T_FP, [-1.2, 2.62, -0.5], [20.0, 1.5, 0.6], 64)
# J 75.6-78.4: first person on the stool; the hat rises over the camera, the eyes lift, the brim comes down
fp = [STOOL[0] + 0.22, STOOL_ROOT_KID[1] + BOY_EYE, STOOL[2]]
key(T_FP, fp, [20.0, 1.5, 0.5], 72, cut=True)
key(76.6, [fp[0], fp[1], fp[2]], [20.0, 1.8, 0.2], 72)
key(T_HAT_B, [fp[0] - 0.03, fp[1] - 0.04, fp[2]], [20.0, 5.2, -2.5], 74)
key(LENGTH, [fp[0] - 0.05, fp[1] - 0.07, fp[2]], [20.0, 3.6, -2.0], 74)

# ================================================================ scene
scene = {
    'title': 'The Sorting',
    'length': tk(LENGTH),
    'radius': GH.RADIUS,
    'set': {'templates': [{'id': 'hogwartsmagic:great_hall', 'at': list(GH.TEMPLATE_AT)}], 'time_of_day': 18000,
            'build_ticks': 100},
    'poses': poses,
    'actors': actors,
    'events': sorted(events, key=lambda e: e['t']),
    'words': words,
    'camera': camera,
}

# ================================================================ checks
problems = []
world = GH.scene_world(GH.build().g)
AIR = set(FREE)


def air(p):
    c = (math.floor(p[0]), math.floor(p[1]), math.floor(p[2]))
    return world.get(c) in AIR, world.get(c)


for i, k in enumerate(camera):
    ok, b = air(k['pos'])
    if not ok:
        problems.append('camera key %d t %d pos %s is in %s' % (i, k['t'], k['pos'], b))
    if i and not k.get('cut'):
        a0, a1 = camera[i - 1]['pos'], k['pos']
        n = int(max(abs(a1[j] - a0[j]) for j in range(3)) / 0.1) + 1
        for s in range(1, n):
            p = [a0[j] + (a1[j] - a0[j]) * s / n for j in range(3)]
            ok, b = air(p)
            if not ok:
                problems.append('camera move into key %d (t %d) passes %s (%s)' % (i, k['t'], p, b))
                break
assert all(camera[i]['t'] <= camera[i + 1]['t'] for i in range(len(camera) - 1)), 'camera keys out of order'
ids = [a['id'] for a in actors]
assert len(ids) == len(set(ids)), 'duplicate actor ids'
evs = scene['events']
assert evs[-1]['id'] == 'eyes_close' and all(e['t'] < evs[-1]['t'] for e in evs[:-1]), 'eyes_close must be last'
for a in actors:
    for kk in ('path', 'look'):
        ts = [k['t'] for k in a.get(kk, [])]
        assert ts == sorted(ts), (a['id'], kk)
        assert all(0 <= t <= tk(LENGTH) for t in ts), (a['id'], kk, ts)
    for part, d in a.get('limbs', {}).items():
        ts = [k['t'] for k in d['rot']]
        assert ts == sorted(ts) and all(0 <= t <= tk(LENGTH) for t in ts), (a['id'], part, ts)
# first-years' standing spots and the light blocks are free cells
for e in evs:
    if e['kind'] == 'block':
        ok, b = air(e['at'])
        if not ok and b != 'minecraft:light':
            problems.append('light block at %s replaces %s' % (e['at'], b))
assert not problems, '\n'.join(problems)

txt = write_scene(scene, 'sorting', width=2000)
assert len(txt) < MAX_JSON, 'sorting.json is %d chars (cap %d)' % (len(txt), MAX_JSON)
print('wrote sorting.json: %d chars (cap %d), %d actors (%d students), %d events, %d camera keys, %.1f s' % (
    len(txt), MAX_JSON, len(actors), n_students, len(evs), len(camera), LENGTH))
