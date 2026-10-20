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

The film's hanging banners: four of each house in a row high over its table and two Hogwarts crest banners in the
staff apse (memory_banner_<design> actors, in the hall and the four reveals), each lit by a light block in front of
it, with the floating candles' box split round them.

Asserts: scene JSON under the 900,000-char cap; every camera key and straight camera move in an air cell of the set;
every banner in clear air, no candle box reaching one, the camera never within 0.4 of one and no camera key looking
at its subject through one;
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
import human_motion as HM  # noqa: E402

A = GH.ANCHORS
MAX_JSON = 900000
HALL_ID, INTRO_ID, CHOICE_ID = 'sorting_hall', 'sorting', 'sorting_choice'
REVEAL_PREFIX = 'sorting_reveal_'

KID, OLD, ADULT, GIANT = 0.75, 0.9, 1.0, 1.5
EYE = 1.62                       # player eye height at scale 1

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
    """A seated actor's feet root (human_motion.seated_root_y): level thighs rest on the seat top."""
    return round(HM.seated_root_y(seat_top, scale), 4)


def ahead(p, yaw, d, y=None):
    """The point d blocks ahead of p along yaw (Minecraft convention), at height y (default p's)."""
    r = math.radians(yaw)
    return [round(p[0] - math.sin(r) * d, 4), p[1] if y is None else y, round(p[2] + math.cos(r) * d, 4)]


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
T_HAT_R = 77.8
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
AISLE_Z = 0.5
WALK_Z = (-0.5, 1.5)             # the two files walking in (pre-jump)
FILE_Z = (0.35, 1.45)            # the two files after the jump and while waiting (cells z 0 and 1)
LANE_Z = -0.55                   # the free lane on the Gryffindor side: to the stool and to the tables
H_SEAT = [12.5, seat_root(0.5, KID), -1.5]           # Gryffindor aisle bench, turned to face the aisle (yaw 0)
R_SEAT = [13.5, seat_root(0.5, KID), -1.5]
BENCH_YAW = 0
H_STAND = ahead(H_SEAT, BENCH_YAW, HM.stand_ahead(KID), 0)
R_STAND = ahead(R_SEAT, BENCH_YAW, HM.stand_ahead(KID), 0)
SKIRT_SIGNS = [[12, 0, -1], [13, 0, -1]]                # the bench-skirt signs in front of their seats, removed

# ================================================================ scene parts
actors = []
events = []
words = []
camera = []
POSES = {
    'seated': {'right_leg': [-90, 8, 0], 'left_leg': [-90, -8, 0]},     # legs only: life and the clips own the arms
    'seated_table': {'right_leg': [-65, 8, 0], 'left_leg': [-65, -8, 0]},  # legs angled down under a table top
    'standing': {},                                                    # hands everything back
    'welcome': {'right_arm': [-85, 0, 38], 'left_arm': [-85, 0, -38]},
}
CHEER_CLIPS = {'CLAP', 'CHEER', 'CHEER_STAND', 'WAVE'}
SIT_WINDOWS = {}                 # actor id -> [(t0, t1)] seconds when it sits down or stands up (not walking)
LEGS = []                        # seated holds and seat clips, for the leg-box check: (kind, id, t, a, b, yaw, scale, pitch)


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


class Clips:
    """An actor's player-clip cues (anims). The engine plays a clip until the next cue; IDLE ends one."""

    def __init__(self):
        self.cues = []

    def cue(self, t, name, blend=None):
        self.cues.append((t, name, blend))

    def span(self, t0, t1, name, blend=None, end_blend=None):
        """name from t0, back to IDLE at t1."""
        self.cue(t0, name, blend)
        self.cue(t1, 'IDLE', end_blend)

    def emit(self):
        out, last = [], -1
        for t, name, blend in sorted(self.cues, key=lambda c: c[0]):
            tt = max(tk(t), last + 1)
            if tt > tk(LENGTH):
                break
            k = {'t': tt, 'name': name}
            if blend:
                k['blend'] = blend
            out.append(k)
            last = tt
        return out

    def at(self, t):
        """The clip in force at t (seconds)."""
        cur = 'IDLE'
        for k in self.emit():
            if k['t'] <= tk(t):
                cur = k['name']
        return cur


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

    def shift(self, t0, t1, p):
        """A small step to p over t0..t1 without turning (stepping in to the stool and back)."""
        self.path.append((t0, list(self.pos), None))
        self.path.append((t1, list(p), 'in_out'))
        self.pos = list(p)

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

    def sit_down(self, t, p, clips, poses, scale):
        """SIT_DOWN at t: the path at the standing spot at t (stand_ahead in front of the seated hip, where the
        actor already is) and at the seat root p at t+1 (the clip steers the root between); the seated pose at t+1
        (not walking: the cell check skips it)."""
        clips.cue(t, 'SIT_DOWN')
        stand = list(self.pos)
        self.path.append((t, stand, None))
        self.path.append((t + 1.0, list(p), 'linear'))
        self.pos = list(p)
        poses.append(pose(t + 1.0, 'seated', 1))
        SIT_WINDOWS.setdefault(self.aid, []).append((t, t + 1.0))
        LEGS.append(('sit', self.aid, t, stand, list(p), self.yaw, scale, -90))
        LEGS.append(('hold', self.aid, t + 1.0, list(p), None, self.yaw, scale, -90))

    def stand_up(self, t, p, clips, poses, scale):
        """STAND_UP at t: the path at the seat root at t and at the standing spot p at t+1; a leg-free pose by
        t+1.05, IDLE at t+1.15."""
        clips.cue(t, 'STAND_UP')
        seat = list(self.pos)
        self.path.append((t, seat, None))
        self.path.append((t + 1.0, list(p), 'linear'))
        self.pos = list(p)
        poses.append(pose(t + 1.05, 'standing', 1))
        clips.cue(t + 1.15, 'IDLE', 6)
        SIT_WINDOWS.setdefault(self.aid, []).append((t, t + 1.0))
        LEGS.append(('stand', self.aid, t, seat, list(p), self.yaw, scale, -90))

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
          atype='player', clips=None):
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
    if clips is not None and clips.cues:
        a['anims'] = clips.emit()
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

# ================================================================ the hanging banners (the film's)
# Long cloth banners hung from just under the enchanted ceiling: four of each house in a row over that house's
# table, and two Hogwarts crest banners in the staff apse flanking the window. Each is a memory_banner_<design>
# actor: its position is the middle of its rod and the cloth hangs below, 2 wide x 8 tall at scale 1, its front
# (the art reading normally) facing its yaw; -90 faces the doors (+X), so the hall sees the art as it comes in and
# the staff table sees it the right way round from behind. Light blocks two blocks in front of each lift the cloth
# out of the dark (the floating candles light nothing). The candles' box is split round the banners (CANDLE_BOXES).
BANNER = 'hogwartsmagic:memory_banner_'
BANNER_W, BANNER_H = 2.0, 8.0                 # BannerCloth.WIDTH, HEIGHT at scale 1
BANNER_SWAY = 0.31                            # BannerCloth.SWING + RIPPLE: out of the cloth's plane, at the foot
BANNER_DRIFT = 0.07                           # BannerCloth.DRIFT: sideways
BANNER_TOP = A['ceiling_center'][1] - 0.08    # the rod's line, just under the ceiling plane (scene y 20)
BANNER_YAW = -90
BANNER_X = (10.5, 20.5, 31.5, 42.5)           # along the tables (x 6..55), in columns clear of the set's floating
                                              # block candles over every table
CREST_SCALE = 1.1
CREST_AT = ((-16.5, -7.5), (-16.5, 8.5))      # the apse (local x 2.5, z 14.5 and 30.5), either side of the window
TABLE_MID_Z = {h: sum(s[2] for s in seats) / len(seats) for h, seats in A['table_seats'].items()}
BANNERS = ([(h, x, TABLE_MID_Z[h], 1.0) for h in ('hufflepuff', 'gryffindor', 'ravenclaw', 'slytherin')
            for x in BANNER_X] + [('hogwarts', x, z, CREST_SCALE) for x, z in CREST_AT])
BANNER_LIGHT_DX, BANNER_LIGHT_Y = 2.0, 16     # a light block this far in front of each banner, at this height


def banner_actors():
    for i, (design, x, z, scale) in enumerate(BANNERS):
        actor('banner_%s_%d' % (design, i), None, scale, path=[{'t': 0, 'pos': [x, round(BANNER_TOP, 2), z]}],
              look=[{'t': 0, 'yaw': BANNER_YAW}], atype=BANNER + design)


_grid = GH.build().g                          # the hall template, local cells (the checks use it too)


def banner_lights():
    """The light block cells, one in front of each banner (the front faces +X at yaw -90): the first empty cell of
    a few near BANNER_LIGHT_DX, BANNER_LIGHT_Y (the set has floating block candles up there)."""
    out = []
    for _, x, z, _ in BANNERS:
        for dx, y in ((BANNER_LIGHT_DX, BANNER_LIGHT_Y), (BANNER_LIGHT_DX, BANNER_LIGHT_Y - 1),
                      (BANNER_LIGHT_DX + 1, BANNER_LIGHT_Y), (BANNER_LIGHT_DX, BANNER_LIGHT_Y + 1)):
            c = [math.floor(x + dx), y, math.floor(z)]
            local = (c[0] - GH.TEMPLATE_AT[0], c[1] - GH.TEMPLATE_AT[1], c[2] - GH.TEMPLATE_AT[2])
            if local not in _grid or _grid[local].split('[')[0] in FREE:
                out.append(c)
                break
        else:
            raise AssertionError('no room for the light of the banner at %s' % [x, z])
    return out


# The floating candles: the hall's box (x -6..60, y 6.5..15.5, z -15..16, 900 candles) cut in two at the banners'
# feet. Below, one box the full size of the hall; above, among the banners, five boxes between the banner rows
# along X, each stopping CANDLE_CLEAR short of a row's cloth plane (sway, drift and the candle's own width). The
# counts follow the volumes, so the density is the hall's as before.
CANDLE_BOX = ((-6.0, 60.0), (6.5, 15.5), (-15.0, 16.0))
CANDLE_TOTAL = 900
CANDLE_FLAME = 1.1                            # wax (0.8 at most) and flame above a candle's foot
CANDLE_CLEAR = 0.65
CANDLE_SPLIT_Y = round(BANNER_TOP - BANNER_H - CANDLE_FLAME - 0.05, 2)


def candle_boxes():
    (x0, x1), (y0, y1), (z0, z1) = CANDLE_BOX
    boxes = [((x0, x1), (y0, CANDLE_SPLIT_Y))]
    edges = [x0] + [e for bx in BANNER_X for e in (bx - CANDLE_CLEAR, bx + CANDLE_CLEAR)] + [x1]
    for a, b in zip(edges[::2], edges[1::2]):
        boxes.append(((a, b), (CANDLE_SPLIT_Y, y1)))
    vols = [(bx[1] - bx[0]) * (by[1] - by[0]) for bx, by in boxes]
    counts = [round(CANDLE_TOTAL * v / sum(vols)) for v in vols]
    out = []
    for ((a, b), (c, d)), n in zip(boxes, counts):
        out.append({'at': [round((a + b) / 2, 3), round((c + d) / 2, 3), round((z0 + z1) / 2, 3)],
                    'spread': [round((b - a) / 2, 3), round((d - c) / 2, 3), round((z1 - z0) / 2, 3)],
                    'count': n})
    return out


CANDLE_BOXES = candle_boxes()
banner_actors()

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
             (T_LEAD_END + 1.4, [2.45, 0, 0.5]), (T_LEAD_END + 1.8, [2.05, 0.5, 0.4]),
             (T_LEAD_END + 2.3, [1.2, 0.5, 0.0]), (T_LEAD_END + 2.55, [1.03, 1.0, -0.2]), (T_LEAD_END + 3.0, MCG_STOOL)]
mcg.go(T_WALK, MCG_ROUTE, start_ease='in')
mcg.face(T_LEAD_END + 3.1, -90, 0.6)                     # faces the first-years at the stool
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
mcg_clips = Clips()
mcg_poses = []
mcg_actions = [{'t': S['E1'], 'do': 'equip', 'slot': 'mainhand', 'item': HAT}]
PLACE_SPOT = [0.5, 1.0, -0.25]   # facing +Z, the stool's head 0.75 in front (REACH_PLACE's reach)
BOY_PLACE = [0.95, 1.0, -0.05]   # in front of the boy on the stool, a little to his left: in his first person
mcg_clips.span(T_MCG_IN + 1.1, T_WALK - 0.3, 'HANDS_CLASPED', 10, 8)       # the formal stance at the doors
mcg_clips.cue(T_LEAD_END + 3.5, 'READ_SCROLL', 10)                        # at the stool with the list


def mcg_crown(t_on, back=True):
    """The hat goes on the head on the stool at t_on: she steps in, REACH_PLACE 19 ticks before, steps back."""
    mcg.shift(t_on - 2.0, t_on - 1.4, PLACE_SPOT)
    mcg.face(t_on - 1.4, 0, 0.4)
    mcg_clips.cue(t_on - 0.95, 'REACH_PLACE', 3)
    mcg_actions.append({'t': t_on + 0.05, 'do': 'equip', 'slot': 'mainhand', 'item': 'minecraft:air'})
    if back:
        mcg.shift(t_on + 0.9, t_on + 1.5, MCG_STOOL)
        mcg.face(t_on + 1.5, -90, 0.5)
        mcg_clips.cue(t_on + 1.7, 'READ_SCROLL', 10)


def mcg_take_back(t):
    """She takes the hat off the head on the stool at t."""
    mcg_clips.cue(t - 1.3, 'IDLE', 6)
    mcg.shift(t - 1.2, t - 0.6, PLACE_SPOT)
    mcg.face(t - 0.6, 0, 0.35)
    mcg_actions.append({'t': t, 'do': 'hold', 'item': HAT})
    mcg.shift(t + 0.25, t + 0.85, MCG_STOOL)
    mcg.face(t + 0.85, -90, 0.5)
    mcg_clips.cue(t + 1.4, 'READ_SCROLL', 10)


T_TAKE_H = CHEER_H + 0.6
T_TAKE_R = CHEER_R + 0.6
mcg_clips.cue(T_HAT_H - 2.2, 'IDLE', 6)
mcg_crown(T_HAT_H)
mcg_take_back(T_TAKE_H)
mcg_clips.cue(T_HAT_R - 2.2, 'IDLE', 6)
mcg_crown(T_HAT_R)
mcg_take_back(T_TAKE_R)
# she reads the name, looks up from the scroll at the boy, and steps in before him for the hat
mcg_clips.cue(T_NAME + 1.8, 'IDLE', 12)
mcg.go(T_BOY_SIT + 0.6, [(T_BOY_SIT + 1.5, BOY_PLACE)], start_ease='in_out')
mcg.face_point(T_BOY_SIT + 1.5, STOOL, 0.5)
mcg_clips.cue(T_HAT_B - 0.95, 'REACH_PLACE', 3)
mcg_actions.append({'t': T_HAT_B + 0.05, 'do': 'equip', 'slot': 'mainhand', 'item': 'minecraft:air'})
# small life while she waits at the doors: a glance along the line, a turn of the head
rnd_m = random.Random(41)
for t in (8.5, 11.2):
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
    if k == 'ron':                                       # turns to the boy to whisper, then back to the doors
        m.face_point(T_RON_LINE - 0.4, SPOT['boy'], 0.4)
        m.face(T_RON_LINE + 1.8, 90, 0.5)
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

kid_clips = {k: Clips() for k in KIDS}
kid_poses = {k: [] for k in KIDS}
STOOL_STAND = ahead(STOOL_ROOT_KID, -90, HM.stand_ahead(KID), 0.5)   # on the half step, in front of the stool

# ---- Hermione: called, rushes up, sits, the hat, Gryffindor, walks to her table by the lane and sits
hm, hc, hp = kid_m['hermione'], kid_clips['hermione'], kid_poses['hermione']
hm.go(T_GRANGER + 0.7, [(T_GRANGER + 1.3, [2.45, 0, 0.45]), (T_GRANGER + 1.7, [2.05, 0.5, 0.5]),
                        (T_GRANGER + 2.1, STOOL_STAND)])
hm.face(T_GRANGER + 2.1, -90, 0.3)
hm.sit_down(T_GRANGER + 2.35, STOOL_ROOT_KID, hc, hp, KID)
hc.cue(T_GRANGER + 3.5, 'HANDS_CLASPED', 8)
H_UP = CHEER_H + 1.5
hm.stand_up(H_UP, STOOL_STAND, hc, hp, KID)
hm.go(H_UP + 1.1, [(H_UP + 1.45, [2.05, 0.5, 0.5]), (H_UP + 1.75, [2.45, 0, 0.45]), (H_UP + 2.15, [3.2, 0, LANE_Z]),
                   (H_UP + 4.2, [12.0, 0, LANE_Z]), (H_UP + 4.6, H_STAND)])
hm.face(H_UP + 4.6, BENCH_YAW, 0.45)                     # turns her back to the bench
hm.sit_down(H_UP + 5.1, H_SEAT, hc, hp, KID)
herm_actions = [{'t': T_HAT_H, 'do': 'equip', 'slot': 'head', 'item': HAT},
                {'t': CHEER_H + 0.6, 'do': 'equip', 'slot': 'head', 'item': 'minecraft:air'}]

# ---- Ron: called, walks up, sits, the hat, Gryffindor, sags with relief, walks to his table by the lane
rm, rc, rp = kid_m['ron'], kid_clips['ron'], kid_poses['ron']
rm.go(T_WEASLEY + 0.8, [(T_WEASLEY + 1.5, [2.45, 0, 1.0]), (T_WEASLEY + 1.9, [2.05, 0.5, 0.65]),
                        (T_WEASLEY + 2.35, STOOL_STAND)])
rm.face(T_WEASLEY + 2.35, -90, 0.3)
rm.sit_down(T_WEASLEY + 2.6, STOOL_ROOT_KID, rc, rp, KID)
rc.span(T_WEASLEY + 3.7, T_SHOUT_R + SHOUT_LEN, 'NERVOUS', 8, 6)
R_UP = CHEER_R + 1.5
rm.stand_up(R_UP, STOOL_STAND, rc, rp, KID)
rm.go(R_UP + 1.1, [(R_UP + 1.5, [2.05, 0.5, 0.5]), (R_UP + 1.85, [2.45, 0, 0.45]), (R_UP + 2.3, [3.2, 0, LANE_Z]),
                   (R_UP + 5.1, [13.0, 0, LANE_Z]), (R_UP + 5.5, R_STAND)])
rm.face(R_UP + 5.5, BENCH_YAW, 0.45)
rm.sit_down(R_UP + 6.0, R_SEAT, rc, rp, KID)
ron_actions = [{'t': T_HAT_R, 'do': 'equip', 'slot': 'head', 'item': HAT},
               {'t': CHEER_R + 0.6, 'do': 'equip', 'slot': 'head', 'item': 'minecraft:air'}]
# Hermione, at her table now, jumps up for him (her seat faces the free aisle) and claps
hc.cue(CHEER_R + 0.4, 'CHEER_STAND', 3)
hc.span(CHEER_R + 3.8, CHEER_R + 6.0, 'CLAP', 4, 8)
LEGS.append(('cheer', 'hermione', CHEER_R + 0.4, H_SEAT, None, BENCH_YAW, KID, -90))

# ---- the boy: last in line; walks up alone by the lane, sits, the hat comes down
bm, bc, bp_ = kid_m['boy'], kid_clips['boy'], kid_poses['boy']
bs = slot('boy')
bm.go(T_BOY_WALK, [(T_BOY_WALK + 0.7, [bs[0] - 0.6, 0, LANE_Z]), (T_BOY_WALK + 4.3, [2.6, 0, LANE_Z]),
                   (T_BOY_WALK + 4.7, [2.45, 0, 0.1]), (T_BOY_WALK + 5.0, [2.05, 0.5, 0.3]),
                   (T_BOY_WALK + 5.3, STOOL_STAND)], start_ease='in')
bm.face(T_BOY_WALK + 5.3, -90, 0.3)
bm.sit_down(T_BOY_SIT + 0.1, STOOL_ROOT_KID, bc, bp_, KID)
bc.cue(T_BOY_SIT + 1.3, 'HANDS_CLASPED', 10)
boy_actions = [{'t': T_HAT_B, 'do': 'equip', 'slot': 'head', 'item': HAT}]

# ---- waiting: NERVOUS in the entrance hall until the doors, and in the hall until called (or until the name)
rnd_n = random.Random('nervous')
for k in KIDS:
    c = kid_clips[k]
    p2, _ = pair_of(k, post)
    if k == 'ron':
        c.span(rnd_n.uniform(5.5, 7.5), T_RON_LINE - 0.6, 'NERVOUS', 8, 6)
        c.span(T_RON_LINE - 0.2, T_RON_LINE + 1.8, 'WHISPER', 5, 6)
    else:
        c.span(rnd_n.uniform(5.5, 10.0), T0 - rnd_n.uniform(0.0, 1.0), 'NERVOUS', 8, 6)
    if k == 'hermione':
        c.span(T_HERM_LINE - 0.1, T_HERM_LINE + 3.8, 'WHISPER', 5, 6)
    t_in = T_ARRIVE + 0.15 * p2 + 0.8 + rnd_n.uniform(0, 2.5)
    end = {'hermione': T_GRANGER + 0.5, 'ron': T_WEASLEY + 0.6, 'boy': T_BOY_WALK - 0.3}.get(
        k, T_NAME + rnd_n.uniform(0.2, 1.5))
    c.span(t_in, end, 'NERVOUS', 10, 6)
    if k not in ('hermione', 'ron', 'boy'):
        c.cue(T_BOY_SIT + 1.5 + rnd_n.uniform(0, 2.5), 'NERVOUS', 10)       # their own turn is coming


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
kl['boy']['head'].change(T_RON_LINE + 0.1, head_toward(kid_m['boy'], T_RON_LINE, [SPOT['ron'][0], 1.1, SPOT['ron'][2]]), 0.45)
kl['boy']['head'].change(T_RON_LINE + 2.0, [0, 0, 0], 0.4)
kl['hermione']['head'].change(T_HERM_LINE - 0.2, [4, -55, 0], 0.4)     # whispers to Ron (on her +z: her left)
kl['hermione']['head'].change(T_HERM_LINE + 3.7, [0, 0, 0], 0.4)
kl['hermione']['head'].change(T_SHOUT_H + SHOUT_LEN, [-18, 0, 0], 0.4)  # beams
kl['hermione']['head'].change(T_SHOUT_H + SHOUT_LEN + 1.0, [0, 0, 0], 0.4)
kl['ron']['head'].change(T_HAT_R + 0.3, [-12, 0, 0], 0.4)                # eyes up at the brim
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
    cues = kid_clips[k].emit()
    for c0, c1 in zip(cues, cues[1:] + [{'t': tk(LENGTH)}]):
        if c0['name'] != 'IDLE':
            busy.append((c0['t'] / 20 - 0.5, c1['t'] / 20 + 0.5))
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
            t = back + rnd.uniform(1.0, 4.0)
    # the name: the children turn to the boy behind them (staggered, some late)
    if k not in ('boy', 'hermione', 'ron'):
        tr = react(rnd, T_NAME + 0.2, never=0.0)
        bp = m_pos(kid_m['boy'], T_NAME)
        kid_life[k]['head'].change(tr, head_toward(kid_m[k], tr, [bp[0], 1.1, bp[2]], 80), rnd.uniform(0.4, 0.7))
        kid_life[k]['head'].change(T_BOY_WALK + 2.0 + rnd.uniform(0, 1.5), [0, 0, 0], 0.6)

FIRST_YEAR_SKINS = skin_pool('first_year', 10, ['first_year'])
for i, k in enumerate(KIDS):
    if k == 'boy':
        skin, ac = {'viewer': True}, boy_actions
    elif k == 'hermione':
        skin, ac = {'texture': 'hermione'}, herm_actions
    elif k == 'ron':
        skin, ac = {'texture': 'ron'}, ron_actions
    else:
        n = int(k[1:]) - 1
        skin, ac = {'texture': FIRST_YEAR_SKINS[n % len(FIRST_YEAR_SKINS)]}, None
    actor(k if k in ('boy', 'hermione', 'ron') else 'first_year_' + k[1:], skin, KID, mover=kid_m[k],
          life=kid_life[k], pose_cues=kid_poses[k] or None, actions=ac, clips=kid_clips[k])

actor('mcgonagall', {'texture': 'mcgonagall'}, ADULT, mover=mcg, life=mcg_life,
      items={'offhand': 'minecraft:paper'}, actions=mcg_actions, clips=mcg_clips)

# ================================================================ the staff table
STAFF = A['staff_seats']        # [x, seat_top, z, yaw]; 0 is the throne


def staff_idle(life, rnd, every, span, busy=()):
    t = rnd.uniform(0.5, every[0])
    while t < LENGTH - 2:
        if not any(a - 0.5 < t < b for a, b in busy):
            life['head'].change(t, [rnd.uniform(-6, 12), rnd.uniform(-span, span), rnd.uniform(-4, 4)],
                                rnd.uniform(0.4, 0.9))
        t += rnd.uniform(*every)


STAFF_HIP_FWD = 0.2              # the hip this far forward of the chair's centre, clear of its back


def staff_seated(aid, skin, seat, scale, life, look=None, clips=None):
    x, top, z, yaw = seat
    root = ahead([x, seat_root(top, scale), z], yaw, STAFF_HIP_FWD)
    actor(aid, {'texture': skin}, scale, path=[{'t': 0, 'pos': root}],
          look=look or [{'t': 0, 'yaw': yaw}], life=life, pose_cues=[pose(0, 'seated_table', 1)], clips=clips)
    LEGS.append(('hold', aid, 0.0, root, None, yaw, scale, -65))


# Dumbledore rises for his welcome, arms open, and sits again; the staff clap for each new Gryffindor
THRONE = [STAFF[0][0], seat_root(STAFF[0][1], ADULT), STAFF[0][2]]
DUMB_UP = ahead(THRONE, STAFF[0][3], HM.stand_ahead(ADULT), 1.0)      # standing in front of the throne
dm = Mover('dumbledore', THRONE, STAFF[0][3])
d_clips, d_poses = Clips(), [pose(0, 'seated', 1)]
LEGS.append(('hold', 'dumbledore', 0.0, THRONE, None, STAFF[0][3], ADULT, -90))
dm.stand_up(T_DUMB - 1.2, DUMB_UP, d_clips, d_poses, ADULT)
d_poses.append(pose(T_DUMB, 'welcome', 12))
d_poses.append(pose(T_DUMB + 3.6, 'standing', 10))
dm.sit_down(T_DUMB + 4.4, THRONE, d_clips, d_poses, ADULT)
d_clips.span(CHEER_H + 0.9, CHEER_H + 3.4, 'CLAP', 6, 8)
d_clips.span(CHEER_R + 1.1, CHEER_R + 3.2, 'CLAP', 6, 8)
d_life = Life()
staff_idle(d_life, random.Random(3), (3.5, 7.0), 25, busy=[(T_DUMB - 1.5, T_DUMB + 5.5)])
actor('dumbledore', {'texture': 'dumbledore'}, ADULT, mover=dm, life=d_life, pose_cues=d_poses, clips=d_clips)

# Snape: still, staring; Quirrell: twitchy little jerks and a nervous clap; Hagrid (1.5): waves, claps
s_life = Life()
staff_idle(s_life, random.Random(5), (6.0, 11.0), 12)
staff_seated('snape', 'snape', STAFF[4], ADULT, s_life)
q_life = Life()
rq = random.Random(7)
t = 0.4
while t < LENGTH - 1:
    q_life['head'].change(t, [rq.uniform(-8, 14), rq.uniform(-35, 35), rq.uniform(-6, 6)], rq.uniform(0.1, 0.25))
    t += rq.uniform(0.8, 1.8)
q_clips = Clips()
q_clips.span(CHEER_H + 1.6, CHEER_H + 2.9, 'CLAP', 4, 6)
q_clips.span(2.0, 9.0, 'NERVOUS', 10, 10)
staff_seated('quirrell', 'quirrell', STAFF[6], ADULT, q_life, clips=q_clips)
h_life = Life()
staff_idle(h_life, random.Random(11), (2.5, 6.0), 30)
h_clips = Clips()
h_clips.span(S['D3'] + 1.0, S['D3'] + 3.8, 'WAVE', 6, 10)              # a big wave at the first-years
h_clips.span(CHEER_H + 0.3, CHEER_H + 4.2, 'CHEER', 4, 10)
h_clips.span(CHEER_R + 0.4, CHEER_R + 3.4, 'CLAP', 4, 8)
staff_seated('hagrid', 'hagrid', STAFF[7], GIANT, h_life, clips=h_clips)
t_life = Life()
staff_idle(t_life, random.Random(13), (2.5, 5.5), 35, busy=[(7.5, 11.5), (29.5, 33.5)])
t_clips = Clips()
t_clips.span(8.0, 11.2, 'TALK', 8, 8)                    # chatting with the empty chair's neighbour
t_clips.span(30.0, 33.0, 'TALK', 8, 8)
t_clips.span(CHEER_H + 1.0, CHEER_H + 3.2, 'CLAP', 5, 8)
t_clips.span(CHEER_R + 0.8, CHEER_R + 2.6, 'CLAP', 5, 8)
staff_seated('teacher', 'sirius', STAFF[3], ADULT, t_life, clips=t_clips)

# ================================================================ the house tables
SEATS = A['table_seats']
PER_SIDE = 16
RESERVED = {(12.5, -1.5), (13.5, -1.5)}                   # Hermione's and Ron's seats
AISLE_BENCH_Z = (-1.5, 2.5)                              # the two benches with their backs to the aisle
DOOR_TARGET = [64.5, 5.5, 0.5]
STOOL_TARGET = [0.5, 2.4, -0.2]                          # the stool and McGonagall beside it
DUMB_TARGET = [-13.9, 2.6, 0.5]


def pick_seats(house, rnd):
    out = []
    for side_z in sorted({s[2] for s in SEATS[house]}):
        side = [s for s in SEATS[house] if s[2] == side_z and (s[0], s[2]) not in RESERVED
                and 7 < s[0] < 54]                       # not the end seats: the table ends are stairs
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
    """A seated student's attention: head turns (limbs, added on the look); the body stays square to the table,
    since its legs are angled down under the table top and any turn swings them into the bench."""

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
        body = False                 # square to the table: a turned body swings the legs into the bench
        shift = 0.0
        hy = clamp(d - (shift if body else 0), -72, 72)
        self.life['head'].change(t, [clamp(pitch, -30, 35), hy, 0], dur, hold)
        if body:
            self.body.change(t, shift, dur * 1.3, hold)

    def at(self, t, p, dur=0.45, hold=True, body=True):
        d, pitch = self.rel(p)
        self.set(t, d, pitch, dur, hold, body)

    def look_json(self):
        return [{'t': t, 'yaw': round(self.yaw0 + v, 1)} for t, v in self.body.keys()]


def pick(rnd, weights):
    r = rnd.random() * sum(w for _, w in weights)
    for name, w in weights:
        r -= w
        if r <= 0:
            return name
    return weights[-1][0]


def track_pos(m, t, y=1.0):
    p = m_pos(m, t)
    return [p[0], y, p[2]]


def partners(seats):
    """Across-the-table conversation partners: the k-th seat of one bench with the k-th of the other."""
    sides = {}
    for st in seats:
        sides.setdefault(st[2], []).append(st)
    zs = sorted(sides)
    out = {}
    for z0, z1 in zip(zs[0::2], zs[1::2]):
        a_, b_ = sorted(sides[z0], key=lambda q: q[0]), sorted(sides[z1], key=lambda q: q[0])
        for p_, q_ in zip(a_, b_):
            out[id(p_)], out[id(q_)] = q_, p_
    return out


def conversation(rnd, t0, t1, first):
    """Turns of TALK for one side of a two-person chat between t0 and t1: [(start, end)]."""
    turns, t, me = [], t0, first
    while t < t1 - 1.0:
        d = rnd.uniform(1.6, 3.8)
        if me:
            turns.append((t, min(t + d, t1)))
        t += d + rnd.uniform(0.2, 0.8)
        if rnd.random() < 0.75:
            me = not me
    return turns


STUDENT_SKINS = {h: skin_pool('student_' + h, 30, ['student_' + h, 'student_%s_b' % h]) for h in SEATS}
CHATS_BEFORE = (0.4, BOOM - 0.4)             # the hall chatting before the doors
CHATS_PASS = (S['D1'] + 1.5, T_ARRIVE - 0.5)  # a word to a neighbour as the first-years pass
CHATS_AFTER = (CHEER_R + 5.2, T_NAME - 0.4)   # excited chatter after Ron, before the silence
n_students = 0
for house in ('hufflepuff', 'gryffindor', 'ravenclaw', 'slytherin'):
    rnd_seat = random.Random('seats' + house)
    seats = pick_seats(house, rnd_seat)
    skins = list(STUDENT_SKINS[house])
    random.Random('skins' + house).shuffle(skins)
    partner = partners(seats)
    rnd_pairs = random.Random('chat' + house)
    pair_plan = {}                           # id(seat) -> (chats?, first speaker?, pass chat?, after chat?)
    for st in sorted(seats, key=lambda q: (q[0], q[2])):
        if id(st) in pair_plan:
            continue
        mate = partner.get(id(st))
        plan = (rnd_pairs.random() < 0.8, rnd_pairs.random() < 0.5, rnd_pairs.random() < 0.3,
                rnd_pairs.random() < 0.45, rnd_pairs.randrange(1 << 30))
        pair_plan[id(st)] = plan
        if mate is not None:
            pair_plan[id(mate)] = (plan[0], not plan[1], plan[2], plan[3], plan[4])
    for k, seat in enumerate(seats):
        n_students += 1
        aid = '%s_%d' % (house, k)
        rnd = random.Random(aid)
        life = Life()
        clips = Clips()
        g = Gaze(seat, OLD, life)
        mate = partner.get(id(seat))
        chats, first, chat_pass, chat_after, pseed = pair_plan[id(seat)]
        neighbours = [q for q in seats if q is not seat and abs(q[0] - seat[0]) < 6]
        # ---- the chats: both of a pair face each other; turns of TALK, the other listening
        chat_windows = []
        if mate is not None and chats:
            chat_windows.append(CHATS_BEFORE)
        if mate is not None and chat_pass:
            c0 = rnd.uniform(CHATS_PASS[0], CHATS_PASS[1] - 4)
            chat_windows.append((c0, c0 + rnd.uniform(2.5, 4.0)))
        if mate is not None and chat_after:
            chat_windows.append(CHATS_AFTER)
        for w0, w1 in chat_windows:
            for t0, t1 in conversation(random.Random(pseed + int(w0 * 10)), w0, w1, first):
                clips.span(t0, t1, 'TALK', rnd.randint(4, 8), rnd.randint(6, 10))
        # ---- the reactions: (time or None, what), each staggered for this student
        R = []
        for w0, w1 in chat_windows:
            R.append((w0 + rnd.uniform(0, 0.6), 'pt', [mate[0], 1.3, mate[2]], True))
        tr = react(rnd, BOOM - 0.2, (1.5, 3.0))                                   # the doors
        if tr:
            R.append((tr, 'pt', DOOR_TARGET, True))
        for tt in (S['D1'] + 1.0, S['D1'] + 4.5, 37.5, S['D3'] + 1.5, S['D3'] + 4.0):
            tt2 = tt + rnd.uniform(-0.4, 0.8)
            if not any(w0 - 0.5 < tt2 < w1 + 0.5 for w0, w1 in chat_windows):
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
        tr = react(rnd, H_UP + 1.6, (1.0, 2.0))
        if tr:
            R.append((tr, 'track', track_pos(hm, tr), False))
        R.append((T_WEASLEY + 0.5 + rnd.uniform(0, 1.5), 'pt', STOOL_TARGET, False))
        tr = react(rnd, R_UP + 1.8, (1.0, 2.5))
        if tr and not (chat_after and CHATS_AFTER[0] - 0.5 < tr):
            R.append((tr, 'track', track_pos(rm, tr), False))
        R.append((T_SCROLL + rnd.uniform(0, 2.0) if not chat_after else T_NAME - 0.3 + rnd.uniform(0, 0.4),
                  'pt', STOOL_TARGET, True))
        tr = react(rnd, T_NAME + 0.2, (1.5, 3.0), late=0.15, never=0.05)       # the name: turn to the boy
        if tr:
            R.append((tr, 'pt', track_pos(bm, T_NAME, 1.1), True))
            R.append((T_BOY_WALK + 2.0 + rnd.uniform(0, 0.8), 'track', track_pos(bm, T_BOY_WALK + 2.4, 1.1), True))
            R.append((T_BOY_SIT + rnd.uniform(0, 1.2), 'pt', [STOOL[0], 2.1, STOOL[2]], True))
        R.sort(key=lambda r: r[0])
        # ---- in every quiet gap: glances at neighbours and idle turns of the head
        busy = [(r[0] - 0.6, r[0] + 1.2) for r in R] + [(w0 - 0.5, w1 + 0.5) for w0, w1 in chat_windows]
        busy.append((T_HUSH - 0.5, T_DUMB + 5.5))                            # the hush and the welcome: still
        busy.append((T_NAME, T_BOY_SIT + 2.0))                               # the name: everyone watching
        idle = []
        for a_, b_ in free_windows(busy, 0.3, LENGTH - 1.5):
            t = a_ + rnd.uniform(0.2, 2.0)
            while t < b_ - 1.2:
                idle.append(t)
                t += rnd.uniform(2.0, 5.5)
        events_s = [(r[0], r) for r in R] + [(t, None) for t in idle]
        events_s.sort(key=lambda e: e[0])
        g.set(0.0, rnd.uniform(-45, 45), rnd.uniform(-6, 10), body=False)
        cur = None                                       # the current attention point (None = idle)
        for t, r in events_s:
            if r is None:
                if rnd.random() < 0.6 and neighbours:
                    nb = rnd.choice(neighbours)
                    g.at(t, [nb[0], 1.25, nb[2]], rnd.uniform(0.35, 0.7), body=rnd.random() < 0.4)
                else:
                    base = g.rel(cur)[0] if cur else 0
                    g.set(t, base + rnd.uniform(-35, 35), rnd.uniform(-8, 10), rnd.uniform(0.35, 0.7),
                          body=rnd.random() < 0.3)
                if cur and rnd.random() < 0.6:
                    g.at(t + rnd.uniform(1.0, 2.2), cur, rnd.uniform(0.4, 0.7), body=False)
            else:
                _, kind, pt, body = r
                g.at(t, pt, rnd.uniform(0.35, 0.65) if kind == 'pt' else 0.6, hold=(kind == 'pt'), body=body)
                cur = pt
        # ---- leaning on the table now and then (not over the doors or the cheers)
        t = rnd.uniform(4, 20)
        while t < LENGTH - 8:
            d = rnd.uniform(3, 8)
            if not any(a_ - 1 < t < b_ + 1 or a_ - 1 < t + d < b_ + 1 for a_, b_ in
                       ((BOOM - 1, BOOM + 3), (T_SHOUT_H - 1, CHEER_H + 7), (T_SHOUT_R - 1, CHEER_R + 7))):
                lean(life, rnd, t, d)
            t += d + rnd.uniform(10, 25)
        # ---- the cheers: each table only after the hat's shout has finished, staggered per student
        for c0, big in ((CHEER_H, True), (CHEER_R, False)):
            if house == 'gryffindor':
                tr = react(rnd, c0, (0.3, 2.0), late=0.12, never=0.0 if big else 0.15)
                if tr:
                    clip = pick(rnd, [('CHEER', 0.65), ('CLAP', 0.35)] if big else [('CLAP', 0.55), ('CHEER', 0.45)])
                    d = rnd.uniform(2.5, 4.5) * (1 if big else 0.8)
                    clips.span(tr, tr + d, clip, rnd.randint(2, 5), rnd.randint(6, 10))
            else:
                prob = {'slytherin': (0.12, 0.08)}.get(house, (0.75, 0.6))[0 if big else 1]
                if rnd.random() < prob:
                    t0 = c0 + (rnd.uniform(1.0, 3.0) if house == 'slytherin' else rnd.uniform(0.3, 3.0))
                    d = rnd.uniform(1.0, 1.8) if house == 'slytherin' else rnd.uniform(1.6, 3.4)
                    clips.span(t0, t0 + d, 'CLAP', rnd.randint(4, 8), rnd.randint(6, 10))
        x, top, z, yaw = seat
        actor(aid, {'texture': skins[k % len(skins)]}, OLD,
              path=[{'t': 0, 'pos': [x, seat_root(top, OLD), z]}], look=g.look_json(), life=life,
              pose_cues=[pose(0, 'seated_table', 1)], clips=clips)
        LEGS.append(('hold', aid, 0.0, [x, seat_root(top, OLD), z], None, yaw, OLD, -65))

# ================================================================ subtitles
# every spoken line, with its speaker and its real clip length (ffprobe; checked against the files below)
LINES = {
    'hat_open': ('The Sorting Hat', 'Hmm... So. Let us begin where every sorting begins.', 6.40,
                 'pensieve/voice/hall_hat_open'),
    'mcg_doors': ('Professor McGonagall', 'In a few moments you will pass through these doors and be sorted. '
                  'Smarten yourselves up. Follow me.', 7.60, 'pensieve/voice/hall_mcg_doors'),
    'ron_nervous': ('Ron', "My brothers said we'd have to wrestle a troll.", 1.59, 'pensieve/voice/hall_ron_nervous'),
    'hermione_nervous': ('Hermione', "I've read all about the Sorting. It's only a hat... isn't it?", 3.78,
                         'pensieve/voice/hall_hermione_nervous'),
    'dumbledore': ('Professor Dumbledore', 'Welcome! Welcome to a new year at Hogwarts.', 3.84,
                   'pensieve/voice/hall_dumbledore'),
    'mcg_call': ('Professor McGonagall', 'When I call your name, you will come forward. '
                 'I shall place the Sorting Hat on your head.', 6.20, 'pensieve/voice/hall_mcg_call'),
    'mcg_granger': ('Professor McGonagall', 'Granger, Hermione.', 1.94, 'pensieve/voice/hall_mcg_granger'),
    'mcg_weasley': ('Professor McGonagall', 'Weasley, Ronald.', 1.95, 'pensieve/voice/hall_mcg_weasley'),
    'gryffindor': ('The Sorting Hat', 'GRYFFINDOR!', 2.68, 'sorting/gryffindor'),
}


def subtitle(t, line):
    who, text, secs, _ = LINES[line]
    return {'t': tk(t), 'text': text, 'subtitle': True, 'speaker': who, 'duration': max(20, tk(secs))}


def _check_clip_lengths():
    import shutil
    import subprocess
    exe = shutil.which('ffprobe')
    if not exe:
        print('note: ffprobe not found, subtitle lengths not re-measured')
        return
    snd = os.path.join(os.path.dirname(MEMORIES), '..', '..', 'assets', 'hogwartsmagic', 'sounds')
    for line, (_, _, secs, rel) in LINES.items():
        f = os.path.normpath(os.path.join(snd, rel + '.ogg'))
        out = subprocess.run([exe, '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', f],
                             capture_output=True, text=True).stdout.strip()
        assert abs(float(out) - secs) < 0.06, (line, out, secs)


_check_clip_lengths()

# ================================================================ events
ev(0, 'effect', 'eyes_open', at=[0, 0, 0], duration=tk(2.6))
for cb in CANDLE_BOXES:
    ev(0, 'effect', 'floating_candles', **cb)
for c in banner_lights():
    ev(0, 'block', 'minecraft:light', at=c)
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
for t_, line in ((T_MCG_LINE, 'mcg_doors'), (T_RON_LINE, 'ron_nervous'), (T_HERM_LINE, 'hermione_nervous'),
                 (T_DUMB, 'dumbledore'), (T_CALL, 'mcg_call'), (T_GRANGER, 'mcg_granger'),
                 (T_SHOUT_H, 'gryffindor'), (T_WEASLEY, 'mcg_weasley'), (T_SHOUT_R, 'gryffindor')):
    words.append(subtitle(t_, line))
for c in SKIRT_SIGNS:                                    # room in front of Hermione's and Ron's seats
    ev(0, 'block', 'minecraft:air', at=c)

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
fp = [STOOL[0] + 0.35, STOOL_ROOT_KID[1] + BOY_EYE, STOOL[2]]
key(S['J'], fp, [20.0, 1.5, 0.5], 72, cut=True)
key(S['J'] + 1.6, fp, [12.0, 2.6, -5.0], 72)                  # she is before him, the hat in her hands
key(T_HAT_B, [fp[0] - 0.03, fp[1] - 0.04, fp[2]], [10.0, 5.0, -6.0], 74)
key(LENGTH, [fp[0] - 0.05, fp[1] - 0.07, fp[2]], [10.0, 3.6, -5.0], 74)

hall = {
    'title': 'The Sorting',
    'length': tk(LENGTH),
    'radius': GH.RADIUS,
    'next': CHOICE_ID,
    'set': {'templates': [{'id': 'hogwartsmagic:great_hall', 'at': list(GH.TEMPLATE_AT)}], 'time_of_day': 18000,
            'build_ticks': 100},
    'poses': POSES,
    'actors': actors,
    'events': sorted(events, key=lambda e: e['t']),
    'words': words,
    'camera': camera,
}

# ================================================================ checks (shared by every scene)
AIR = set(FREE)


def make_world(grid=None, fills=(), removed=()):
    """{scene cell: full block state} for the checks: the hall template (grid) or the scene's fills."""
    w = {}
    if grid is not None:
        for (x, y, z), st in grid.items():
            w[(x + GH.TEMPLATE_AT[0], y + GH.TEMPLATE_AT[1], z + GH.TEMPLATE_AT[2])] = st
    for f in fills:
        for x in range(f['from'][0], f['to'][0] + 1):
            for y in range(f['from'][1], f['to'][1] + 1):
                for z in range(f['from'][2], f['to'][2] + 1):
                    w[(x, y, z)] = f['block']
    for c in removed:
        w.pop(tuple(c), None)
    return w


def bid_of(st):
    return None if st is None else st.split('[')[0].split('{')[0]


def cell(p):
    return (math.floor(p[0]), math.floor(p[1]), math.floor(p[2]))


def solid_at(w, p, sign_margin=0.0):
    """The block state whose real shape holds point p, or None: slabs by half, stairs by their step, wall signs
    as a thin plate (sign_margin widens it for a body), everything else not FREE as a full cube."""
    c = cell(p)
    st = w.get(c)
    b = bid_of(st)
    if b in AIR:
        return None
    fx, fy, fz = p[0] - c[0], p[1] - c[1], p[2] - c[2]
    if b.endswith('_slab'):
        if 'type=top' in st:
            return st if fy > 0.5 else None
        if 'type=bottom' in st or '[' not in st:
            return st if fy < 0.5 else None
        return st
    if b.endswith('_stairs'):
        if fy < 0.5:
            return st
        f = st.split('facing=')[1].split(',')[0].rstrip(']') if 'facing=' in st else 'north'
        return st if {'west': fx < 0.5, 'east': fx > 0.5, 'north': fz < 0.5, 'south': fz > 0.5}[f] else None
    if b.endswith('_wall_sign'):
        f = st.split('facing=')[1].split(',')[0].rstrip(']')
        th = 0.125 + sign_margin
        hit = {'south': fz < th, 'north': fz > 1 - th, 'east': fx < th, 'west': fx > 1 - th}[f]
        return st if hit and (0.28 - sign_margin <= fy <= 0.78 + sign_margin) else None
    return st


def is_air(w, p):
    return bid_of(w.get(cell(p))) in AIR


def check_camera(w, cam, problems):
    for i, k in enumerate(cam):
        if not is_air(w, k['pos']):
            problems.append('camera key %d t %d pos %s is in %s' % (i, k['t'], k['pos'], w.get(cell(k['pos']))))
        if i and not k.get('cut'):
            a0, a1 = cam[i - 1]['pos'], k['pos']
            n = int(max(abs(a1[j] - a0[j]) for j in range(3)) / 0.1) + 1
            for s_ in range(1, n):
                p = [a0[j] + (a1[j] - a0[j]) * s_ / n for j in range(3)]
                if not is_air(w, p):
                    problems.append('camera move into key %d (t %d) passes %s (%s)' % (i, k['t'], p, w.get(cell(p))))
                    break
    assert all(cam[i]['t'] <= cam[i + 1]['t'] for i in range(len(cam) - 1)), 'camera keys out of order'


def banner_boxes(acts, pad_plane=0.0, pad_edge=0.0):
    """[(id, (x0, x1), (y0, y1), (z0, z1))] the volume each banner actor's cloth can sway through (the rod and its
    knobs included), grown by pad_plane through the cloth and pad_edge round its edges. Banners hang at yaw +-90 (the
    cloth in a plane of constant x)."""
    out = []
    for a in acts:
        if not a['type'].startswith(BANNER):
            continue
        sc_ = a.get('scale', 1)
        x, y, z = a['path'][0]['pos']
        assert len(a['path']) == 1 and abs(abs(a['look'][0]['yaw']) - 90) < 1e-6, (a['id'], 'banners hang still at +-90')
        half_w = (BANNER_W / 2 + 0.25 + BANNER_DRIFT) * sc_ + pad_edge          # the rod overhangs 0.14, knobs 0.14
        out.append((a['id'], (x - BANNER_SWAY * sc_ - 0.08 - pad_plane, x + BANNER_SWAY * sc_ + 0.08 + pad_plane),
                    (y - BANNER_H * sc_ - pad_edge, y + 0.05 + pad_edge), (z - half_w, z + half_w)))
    return out


def in_box(p, box):
    return all(box[k + 1][0] <= p[k] <= box[k + 1][1] for k in range(3))


def look_target(scene, k):
    """The point a camera key looks at: its look_at, or the named actor's chest at that tick."""
    la = k.get('look_at')
    if la is None:
        return None
    if not isinstance(la, str):
        return la
    aid = la.split(':', 1)[1]
    a = next(x for x in scene['actors'] if x['id'] == aid)
    p = sample_path(a['path'], k['t'])
    return [p[0], p[1] + 1.2 * a.get('scale', 1), p[2]]


def check_banners(scene, w, problems):
    """The banners hang in clear air, no candle box reaches them, the camera never enters one (with a margin for
    the near plane and the track's curve between keys), and no camera key looks at its subject through one."""
    boxes = banner_boxes(scene['actors'])
    for aid, bx, by, bz in boxes:
        for x in range(math.floor(bx[0]), math.floor(bx[1]) + 1):
            for y in range(math.floor(by[0]), math.floor(by[1]) + 1):
                for z in range(math.floor(bz[0]), math.floor(bz[1]) + 1):
                    if not is_air(w, (x + 0.5, y + 0.5, z + 0.5)):
                        problems.append('banner %s hangs through %s at %s' % (aid, w.get((x, y, z)), (x, y, z)))
        for e in scene['events']:
            if e['id'] != 'floating_candles':
                continue
            lo = [e['at'][k] - e['spread'][k] - (0.06 if k != 1 else 0) for k in range(3)]
            hi = [e['at'][k] + e['spread'][k] + (0.06 if k != 1 else CANDLE_FLAME) for k in range(3)]
            if all(lo[k] < (bx, by, bz)[k][1] and hi[k] > (bx, by, bz)[k][0] for k in range(3)):
                problems.append('candle box at %s reaches banner %s' % (e['at'], aid))
    cam = scene['camera']
    near = banner_boxes(scene['actors'], pad_plane=0.4, pad_edge=0.4)
    for i, k in enumerate(cam):
        pts = [k['pos']]
        if i and not k.get('cut'):
            a0, a1 = cam[i - 1]['pos'], k['pos']
            n = int(max(abs(a1[j] - a0[j]) for j in range(3)) / 0.1) + 1
            pts += [[a0[j] + (a1[j] - a0[j]) * s_ / n for j in range(3)] for s_ in range(1, n)]
        for p in pts:
            hit = [b[0] for b in near if in_box(p, b)]
            if hit:
                problems.append('camera key %d (t %d) at or into it passes %s by banner %s' % (i, k['t'], p, hit[0]))
                break
        tgt = look_target(scene, k)
        if tgt is not None:
            p0 = k['pos']
            n = int(math.dist(p0, tgt) / 0.1) + 1
            for s_ in range(1, n):
                p = [p0[j] + (tgt[j] - p0[j]) * s_ / n for j in range(3)]
                hit = [b[0] for b in boxes if in_box(p, b)]
                if hit:
                    problems.append('camera key %d (t %d) looks at its subject through banner %s' % (i, k['t'], hit[0]))
                    break


def check_keys(acts, length_ticks):
    ids_ = [a['id'] for a in acts]
    assert len(ids_) == len(set(ids_)), 'duplicate actor ids'
    for a in acts:
        for kk in ('path', 'look', 'anims', 'poses'):
            ts = [k['t'] for k in a.get(kk, [])]
            assert ts == sorted(ts), (a['id'], kk)
            if kk in ('path', 'look', 'anims'):
                assert len(ts) == len(set(ts)), (a['id'], kk)
            assert all(0 <= t <= length_ticks for t in ts), (a['id'], kk, ts)
        for part, d in a.get('limbs', {}).items():
            ts = [k['t'] for k in d['rot']]
            assert ts == sorted(ts) and len(ts) == len(set(ts)), (a['id'], part)
            assert all(0 <= t <= length_ticks for t in ts), (a['id'], part, ts)


def check_walkers(w, acts, length_ticks, sit_windows, problems):
    """No moving actor within 0.6 blocks (horizontal) of any other actor, sampled every 2 ticks; no walker's feet or
    head inside a solid shape (a bench-skirt sign counts with a body's width)."""
    people = [a for a in acts if a['type'] == 'player']
    paths = {a['id']: a['path'] for a in people}
    moving = [aid for aid, p in paths.items() if len(p) > 1]
    sit = {aid: [(tk(t0), tk(t1)) for t0, t1 in ww] for aid, ww in sit_windows.items()}
    static = {aid: p[0]['pos'] for aid, p in paths.items() if len(p) == 1}
    n = 0
    for t in range(0, length_ticks + 1, 2):
        pos = {aid: sample_path(paths[aid], t) for aid in moving}
        pos.update(static)
        for aid in moving:
            prev = sample_path(paths[aid], max(0, t - 2))
            cur = pos[aid]
            if math.dist(prev, cur) < 1e-4:
                continue
            for other, op in pos.items():
                if other == aid:
                    continue
                n += 1
                d = math.hypot(cur[0] - op[0], cur[2] - op[2])
                if d < 0.6 and abs(cur[1] - op[1]) < 1.6:
                    problems.append('t %d: %s at %s is %.2f from %s at %s' % (
                        t, aid, [r2(c) for c in cur], d, other, [r2(c) for c in op]))
            if any(a0 - 1 <= t <= a1 + 1 for a0, a1 in sit.get(aid, [])):
                continue
            for dy in (0.05, 0.9):
                q = [cur[0], cur[1] + dy, cur[2]]
                hit = solid_at(w, q, sign_margin=0.28)
                if hit:
                    problems.append('t %d: %s walks through %s at %s' % (t, aid, bid_of(hit), [r2(c) for c in q]))
    return n


LEG_TOUCH = 0.03                 # a leg box resting on a surface may graze it by the pants layer (0.25 px)


def leg_problems(w, legs):
    """Every seated hold (at the seat yaw and the most a body turns, +-28) and every SIT_DOWN, STAND_UP and
    CHEER_STAND sampled through the clip: no corner of either leg box inside a solid shape."""
    out = []
    n = 0

    def test(tag, root, yaw, scale, pitch):
        nonlocal n
        for side, splay in (('right', 8), ('left', -8)):
            for c in HM.leg_box(root, yaw, scale, pitch, splay, 0, side):
                n += 1
                hit = solid_at(w, [c[0], c[1] + LEG_TOUCH, c[2]])
                if hit:
                    out.append('%s: %s leg corner %s in %s' % (tag, side, [r2(v) for v in c], bid_of(hit)))
                    return

    for kind, aid, t, a, b, yaw, scale, pitch in legs:
        if kind in ('hold', 'hold_turn'):
            for dy in ((-28, 0, 28) if kind == 'hold_turn' else (0,)):
                test('%s seated (t %.1f, yaw %+d)' % (aid, t, dy), a, yaw + dy, scale, pitch)
        elif kind in ('sit', 'stand'):
            for i in range(21):
                s_ = i * 0.05
                root, lp = (HM.sit_down_root if kind == 'sit' else HM.stand_up_root)(s_, a, b)
                test('%s %s %.2f s in' % (aid, 'SIT_DOWN' if kind == 'sit' else 'STAND_UP', s_), root, yaw, scale, lp)
        elif kind == 'cheer':
            for i in range(61):
                q = i * 0.05
                root, lp = HM.cheer_stand_root(q, a, yaw, scale)
                test('%s CHEER_STAND q %.2f' % (aid, q), root, yaw, scale, lp)
    return out, n


def finish(scene, sid, w, sit_windows, legs, extra=None, cap=MAX_JSON):
    """Run every check on a generated scene, then write it; returns (chars, proximity checks, leg corners)."""
    problems = []
    check_camera(w, scene['camera'], problems)
    check_banners(scene, w, problems)
    check_keys(scene['actors'], scene['length'])
    for e in scene['events']:
        if e['kind'] == 'block' and e['id'] == 'minecraft:light' and not is_air(w, e['at']):
            problems.append('light block at %s replaces %s' % (e['at'], w.get(cell(e['at']))))
    if extra:
        extra(problems)
    n_prox = check_walkers(w, scene['actors'], scene['length'], sit_windows, problems)
    lp, n_legs = leg_problems(w, legs)
    problems += lp
    if problems:
        print('\n'.join(problems[:60]))
        print('... %d problems in %s' % (len(problems), sid))
        raise SystemExit(1)
    txt = write_scene(scene, sid, width=2000)
    assert len(txt) < cap, '%s.json is %d chars (cap %d)' % (sid, len(txt), cap)
    return len(txt), n_prox, n_legs


# ================================================================ the hall
HALL_WORLD = make_world(_grid, removed=SKIRT_SIGNS)
evs = hall['events']
assert evs[-1]['id'] == 'eyes_close' and all(e['t'] < evs[-1]['t'] for e in evs[:-1]), 'eyes_close must be last'


def hall_shout_checks(problems):
    """The cheers start only once the hat's shout has finished: no cheering clip in force during a shout."""
    for a in actors:
        cues = a.get('anims', [])
        for shout in (T_SHOUT_H, T_SHOUT_R):
            s0, s1 = tk(shout), tk(shout + SHOUT_LEN)
            in_force = [c['name'] for c in cues if c['t'] <= s0][-1:] + [c['name'] for c in cues if s0 < c['t'] < s1]
            bad = [n for n in in_force if n in CHEER_CLIPS]
            if bad:
                problems.append('%s cues %s during the shout at %.1f s' % (a['id'], bad, shout))
        for part in ('right_arm', 'left_arm'):
            if a['id'].startswith(('gryffindor_', 'hufflepuff_', 'ravenclaw_')) and part in a.get('limbs', {}):
                for k in a['limbs'][part]['rot']:
                    for shout in (T_SHOUT_H, T_SHOUT_R):
                        if tk(shout) <= k['t'] < tk(shout + SHOUT_LEN) and any(abs(c) > 0.5 for c in k['v']):
                            problems.append('%s %s moves at tick %d, during the shout' % (a['id'], part, k['t']))


n_chars, n_prox, n_legs = finish(hall, HALL_ID, HALL_WORLD, SIT_WINDOWS, LEGS, hall_shout_checks)
print('wrote %s.json: %d chars (cap %d), %d actors (%d students), %d events, %d camera keys, %.1f s (%d ticks), '
      '%d proximity checks, %d leg corners' % (HALL_ID, n_chars, MAX_JSON, len(actors), n_students, len(evs),
                                                len(camera), LENGTH, tk(LENGTH), n_prox, n_legs))
HALL_SUMMARY = (n_chars, tk(LENGTH))

# ================================================================ the reveals (sorting_reveal_<house>)
# The hall again, from the boy's own eyes on the stool, the hat on his head: black, his new house's table roaring
# (the hat's shout itself is played by the server at SORTED), the eyes open a slit onto the hall with that table up
# and cheering and the rest clapping, McGonagall beside him; the eyes close and he wakes in the real world.
R_LEN = 160                       # ticks
R_PEEK, R_PEEK_DUR = 92, 58       # shut again by 150, ten ticks before the end
TABLE_Z = {'hufflepuff': -11.5, 'gryffindor': -3.5, 'ravenclaw': 4.5, 'slytherin': 12.5}
HOUSES = ('gryffindor', 'hufflepuff', 'ravenclaw', 'slytherin')


def reveal_scene(house):
    global actors, LEGS, SIT_WINDOWS
    actors, LEGS, SIT_WINDOWS = [], [], {}
    rt = R_LEN / 20.0
    # the doors stand open
    for aid, atype, hinge, yaw in (('door_north', 'hogwartsmagic:memory_door', HINGE_N, -180),
                                   ('door_south', 'hogwartsmagic:memory_door_mirrored', HINGE_S, 0)):
        actor(aid, None, DOOR_SCALE, path=[{'t': 0, 'pos': list(hinge)}], look=[{'t': 0, 'yaw': yaw}], atype=atype)
    banner_actors()
    # McGonagall beside him, the scroll held, looking down at him
    m_life = Life()
    m_life['head'].change(0.0, [18, 45, 0], 0.0)
    m_clips = Clips()
    m_clips.cue(0.0, 'HANDS_CLASPED', 1)
    actor('mcgonagall', {'texture': 'mcgonagall'}, ADULT, path=[{'t': 0, 'pos': list(MCG_STOOL)}],
          look=[{'t': 0, 'yaw': -90}], life=m_life, items={'offhand': 'minecraft:paper'}, clips=m_clips)
    # the first-years still waiting in the aisle (Hermione and Ron are at the Gryffindor table)
    for k in KIDS:
        if k in ('boy', 'hermione', 'ron'):
            continue
        rnd = random.Random('reveal' + house + k)
        sp = slot(k)
        c = Clips()
        if rnd.random() < 0.6:
            c.cue(rnd.uniform(0.2, 2.5), 'CLAP', 4)
        else:
            c.cue(0.0, 'NERVOUS', 1)
        n = int(k[1:]) - 1
        actor('first_year_' + k[1:], {'texture': FIRST_YEAR_SKINS[n % len(FIRST_YEAR_SKINS)]}, KID,
              path=[{'t': 0, 'pos': sp}], look=[{'t': 0, 'yaw': round(yaw_to(STOOL[0] - sp[0], STOOL[2] - sp[2]), 1)}],
              clips=c)
    # Hermione and Ron in their seats, turned to the aisle: up on their feet for a Gryffindor
    for aid, seat in (('hermione', H_SEAT), ('ron', R_SEAT)):
        c = Clips()
        if house == 'gryffindor':
            t0 = 0.3 if aid == 'hermione' else 0.8
            c.cue(t0, 'CHEER_STAND', 3)
            c.cue(t0 + 3.4, 'CHEER', 4)
            LEGS.append(('cheer', aid, t0, seat, None, BENCH_YAW, KID, -90))
        else:
            c.cue(random.Random(aid + house).uniform(0.3, 2.0), 'CLAP', 4)
        LEGS.append(('hold', aid, 0.0, seat, None, BENCH_YAW, KID, -90))
        life = Life()
        life['head'].change(0.0, [4, -60, 0], 0.0)                  # looking along the aisle to the stool
        actor(aid, {'texture': aid}, KID, path=[{'t': 0, 'pos': seat}], look=[{'t': 0, 'yaw': BENCH_YAW}],
              life=life, pose_cues=[pose(0, 'seated', 1)], clips=c)
    # the staff
    for aid, skin, si, scale, clip in (('snape', 'snape', 4, ADULT, 'CLAP' if house == 'slytherin' else None),
                                       ('quirrell', 'quirrell', 6, ADULT, 'CLAP'),
                                       ('hagrid', 'hagrid', 7, GIANT, 'CHEER' if house == 'gryffindor' else 'CLAP'),
                                       ('teacher', 'sirius', 3, ADULT, 'CLAP')):
        c = Clips()
        if clip:
            c.cue(random.Random(aid + house).uniform(0.4, 2.0), clip, 4)
        staff_seated(aid, skin, STAFF[si], scale, Life(), clips=c)
    d_c = Clips()
    d_c.cue(0.6, 'CLAP', 4)
    actor('dumbledore', {'texture': 'dumbledore'}, ADULT, path=[{'t': 0, 'pos': THRONE}],
          look=[{'t': 0, 'yaw': STAFF[0][3]}], pose_cues=[pose(0, 'seated', 1)], clips=d_c)
    LEGS.append(('hold', 'dumbledore', 0.0, THRONE, None, STAFF[0][3], ADULT, -90))
    # the house tables: the same seats and faces as the hall
    for h in ('hufflepuff', 'gryffindor', 'ravenclaw', 'slytherin'):
        seats = pick_seats(h, random.Random('seats' + h))
        skins = list(STUDENT_SKINS[h])
        random.Random('skins' + h).shuffle(skins)
        for k, seat in enumerate(seats):
            aid = '%s_%d' % (h, k)
            rnd = random.Random('reveal' + house + aid)
            life = Life()
            g = Gaze(seat, OLD, life)
            g.at(0.0, STOOL_TARGET if h != house else [STOOL[0], 2.2 + rnd.uniform(-0.3, 0.6), STOOL[2]])
            c = Clips()
            if h == house:                                   # the whole table roaring, arms up, bouncing
                c.cue(rnd.uniform(0.0, 1.2), pick(rnd, [('CHEER', 0.8), ('CLAP', 0.2)]), rnd.randint(2, 5))
            elif h == 'slytherin' or house == 'slytherin':
                if rnd.random() < 0.25:
                    c.cue(rnd.uniform(0.8, 3.0), 'CLAP', 6)
            elif rnd.random() < 0.7:
                c.cue(rnd.uniform(0.3, 3.0), 'CLAP', rnd.randint(4, 8))
            x, top, z, yaw = seat
            root = [x, seat_root(top, OLD), z]
            actor(aid, {'texture': skins[k % len(skins)]}, OLD, path=[{'t': 0, 'pos': root}], look=g.look_json(),
                  life=life, pose_cues=[pose(0, 'seated_table', 1)], clips=c)
            LEGS.append(('hold', aid, 0.0, root, None, yaw, OLD, -65))
    ev_ = [{'t': 0, 'kind': 'effect', 'id': 'eyes_close', 'at': [0, 0, 0], 'duration': 1},
           {'t': 0, 'kind': 'effect', 'id': 'enchanted_ceiling', 'at': A['ceiling_center'],
            'spread': A['ceiling_spread'], 'duration': 1, 'style': 'storm'},
           {'t': 4, 'kind': 'sfx', 'id': SFX + 'cheer_big', 'volume': 1.0},
           {'t': 66, 'kind': 'sfx', 'id': SFX + 'cheer_big', 'volume': 0.9},
           {'t': R_PEEK, 'kind': 'effect', 'id': 'eyes_peek', 'at': [0, 0, 0], 'duration': R_PEEK_DUR,
            'intensity': 0.35}]
    for c in SKIRT_SIGNS:
        ev_.append({'t': 0, 'kind': 'block', 'id': 'minecraft:air', 'at': c})
    for cb in CANDLE_BOXES:
        ev_.append(dict({'t': 0, 'kind': 'effect', 'id': 'floating_candles'}, **cb))
    for c in banner_lights():
        ev_.append({'t': 0, 'kind': 'block', 'id': 'minecraft:light', 'at': c})
    eye = [STOOL[0] + 0.35, STOOL_ROOT_KID[1] + EYE * KID, STOOL[2]]
    tz = TABLE_Z[house]
    cam = [{'t': 0, 'pos': [round(c, 3) for c in eye], 'look_at': [22.0, 1.4, round(tz * 0.9, 2)], 'fov': 76},
           {'t': R_LEN, 'pos': [round(eye[0] + 0.02, 3), round(eye[1] - 0.03, 3), round(eye[2], 3)],
            'look_at': [22.0, 1.9, round(tz * 0.85, 2)], 'fov': 74}]
    scene = {
        'title': 'The Sorting',
        'length': R_LEN,
        'radius': GH.RADIUS,
        'end_action': 'wake',
        'set': {'templates': [{'id': 'hogwartsmagic:great_hall', 'at': list(GH.TEMPLATE_AT)}], 'time_of_day': 18000,
                'build_ticks': 100},
        'poses': POSES,
        'actors': actors,
        'events': sorted(ev_, key=lambda e: e['t']),
        'camera': cam,
    }
    assert R_PEEK + R_PEEK_DUR <= R_LEN - 10
    return finish(scene, REVEAL_PREFIX + house, HALL_WORLD, SIT_WINDOWS, LEGS)


REVEAL_SUMMARY = {}
for house in HOUSES:
    n_chars, _, n_legs = reveal_scene(house)
    REVEAL_SUMMARY[house] = n_chars
    print('wrote %s%s.json: %d chars, %d ticks, %d leg corners' % (REVEAL_PREFIX, house, n_chars, R_LEN, n_legs))

# ================================================================ the mind void (sorting and sorting_choice)
VOID_R = 12
VOID_FILLS = [
    {'block': 'minecraft:polished_blackstone', 'from': [-3, -1, -3], 'to': [3, -1, 3]},
    {'block': 'minecraft:polished_deepslate', 'from': [-1, -1, -1], 'to': [1, -1, 1]},
    {'block': 'minecraft:spruce_slab', 'from': [0, 0, 0], 'to': [0, 0, 0]},             # the seat
    {'block': 'minecraft:spruce_fence', 'from': [0, 0, 1], 'to': [0, 1, 1]},            # its back
    {'block': 'minecraft:light', 'from': [0, 4, 0], 'to': [0, 4, 0]},                   # the pool of light
]
VOID_WORLD = make_world(fills=VOID_FILLS)
VOID_SEAT = [0.5, seat_root(0.5, KID), 0.5]
VOID_YAW = 180


def wisps(t0, t1, seed):
    """Slow silver wisps drifting round him: end-rod motes and a little white ash."""
    rw = random.Random(seed)
    out = []
    t = t0
    while t < t1:
        ang = rw.uniform(0, 2 * math.pi)
        rr = rw.uniform(1.0, 3.2)
        out.append({'t': tk(t), 'kind': 'particles', 'id': 'minecraft:end_rod',
                    'at': [round(0.5 + rr * math.cos(ang), 2), round(rw.uniform(0.4, 2.6), 2),
                           round(0.5 + rr * math.sin(ang), 2)],
                    'count': rw.randint(1, 3), 'spread': [0.4, 0.3, 0.4]})
        if rw.random() < 0.35:
            out.append({'t': tk(t + 0.1), 'kind': 'particles', 'id': 'minecraft:white_ash',
                        'at': [0.5, 1.6, 0.5], 'count': rw.randint(4, 8), 'spread': [2.5, 1.2, 2.5]})
        t += rw.uniform(0.35, 0.7)
    return out


def orbit_key(t, ang_deg, rad, h, fov, ease=None):
    a = math.radians(ang_deg)
    k = {'t': tk(t), 'pos': [round(0.5 + rad * math.sin(a), 3), round(h, 3), round(0.5 - rad * math.cos(a), 3)],
         'look_at': 'actor:boy', 'fov': round(fov, 1)}
    if ease:
        k['ease'] = ease
    return k


def void_checks(scene, sid):
    legs = [('hold', 'boy', 0.0, VOID_SEAT, None, VOID_YAW, KID, -90)]
    for k in scene['camera']:
        p = k['pos']
        assert abs(p[0]) < VOID_R and abs(p[2]) < VOID_R and p[1] > 0
    return finish(scene, sid, VOID_WORLD, {}, legs)


# ---- sorting: the mind intro (12 s), Stage 1's stub grown
I_LEN = 12.0
i_life = Life()
i_life['head'].change(2.8, [8, 0, 0], 1.2)
i_life['head'].change(5.0, [-6, -18, 0], 1.6)       # he glances up and aside, as if at the voice
i_life['head'].change(8.2, [10, 4, 0], 1.6)
i_events = [{'t': 0, 'kind': 'effect', 'id': 'eyes_open', 'at': [0, 0, 0], 'duration': tk(3.5)},
            {'t': tk(1.4), 'kind': 'voice', 'id': VOICE + 'hat_open', 'volume': 1.0}] + wisps(0.4, I_LEN - 0.8, 1991)
i_cam = []
for i in range(7):                                        # a slow orbit, closing in a little
    f = i / 6
    i_cam.append(orbit_key(I_LEN * f, -120 + 95 * f, 3.4 - 0.9 * f, 1.75 - 0.25 * f, 56 - 8 * f,
                           'linear' if 0 < i < 6 else None))
intro = {
    'title': 'The Sorting',
    'length': tk(I_LEN),
    'radius': VOID_R,
    'next': HALL_ID,
    'set': {'fills': VOID_FILLS, 'time_of_day': 18000, 'build_ticks': 40},
    'poses': {'seated': POSES['seated']},
    'actors': [{
        'id': 'boy', 'type': 'player', 'skin': {'viewer': True}, 'scale': KID,
        'path': [{'t': 0, 'pos': VOID_SEAT}],
        'look': [{'t': 0, 'yaw': VOID_YAW}],
        'poses': [{'t': 0, 'name': 'seated', 'blend': 1}],
        'limbs': i_life.emit(),
    }],
    'events': sorted(i_events, key=lambda e: e['t']),
    'words': [subtitle(1.4, 'hat_open')],
    'camera': i_cam,
}
n_chars, _, _ = void_checks(intro, INTRO_ID)
print('wrote %s.json: %d chars, %d ticks, next %s' % (INTRO_ID, n_chars, tk(I_LEN), HALL_ID))
INTRO_SUMMARY = (n_chars, tk(I_LEN))

# ---- sorting_choice: the hold scene the quiz plays over (10 min; loops onto itself if it runs out)
# The quiz opens the moment it starts and its greeting and questions are voiced by the server, so the scene has no
# lines of its own: only the boy in his chair with the hat on, the light, the wisps and a slow orbit. It starts and
# ends on the same camera key and pose, so a loop is seamless; no eyes effect (the hand-over fades in from black).
C_LEN = 600.0
c_life = Life()
c_clips = Clips()
rc_ = random.Random('choice')
t = 6.0
while t < C_LEN - 20:
    if rc_.random() < 0.55:                              # thinking: a slow turn of the head, a look down
        c_life['head'].change(t, [rc_.uniform(-4, 20), rc_.uniform(-30, 30), rc_.uniform(-6, 6)], rc_.uniform(1.2, 2.4))
        c_life['head'].change(t + rc_.uniform(3.0, 7.0), [rc_.uniform(0, 8), 0, 0], rc_.uniform(1.2, 2.4))
    else:                                                # his hands: wringing, then clasped again
        d = rc_.uniform(5, 11)
        c_clips.cue(t, 'NERVOUS', 16)
        c_clips.cue(t + d, 'HANDS_CLASPED', 16)
    t += rc_.uniform(12, 26)
c_clips.cue(0.0, 'HANDS_CLASPED', 1)
c_cam = []
N_ORBIT = 40
for i in range(N_ORBIT + 1):                             # one full turn round him in 10 min, breathing in and out
    f = i / N_ORBIT
    c_cam.append(orbit_key(C_LEN * f, -150 + 360 * f, 3.1 + 0.4 * math.sin(f * 6 * math.pi),
                           1.7 + 0.25 * math.sin(f * 4 * math.pi), 52 + 4 * math.sin(f * 2 * math.pi),
                           'linear' if 0 < i < N_ORBIT else None))
choice = {
    'title': 'The Sorting',
    'length': tk(C_LEN),
    'radius': VOID_R,
    'set': {'fills': VOID_FILLS, 'time_of_day': 18000, 'build_ticks': 40},
    'poses': {'seated': POSES['seated']},
    'actors': [{
        'id': 'boy', 'type': 'player', 'skin': {'viewer': True}, 'scale': KID,
        'items': {'head': HAT},
        'path': [{'t': 0, 'pos': VOID_SEAT}],
        'look': [{'t': 0, 'yaw': VOID_YAW}],
        'poses': [{'t': 0, 'name': 'seated', 'blend': 1}],
        'limbs': c_life.emit(),
        'anims': c_clips.emit(),
        'actions': [{'t': 0, 'do': 'equip', 'slot': 'head', 'item': HAT}],
    }],
    'events': sorted(wisps(0.3, C_LEN - 0.5, 2026), key=lambda e: e['t']),
    'camera': c_cam,
}
n_chars, _, _ = void_checks(choice, CHOICE_ID)
print('wrote %s.json: %d chars, %d ticks (loops)' % (CHOICE_ID, n_chars, tk(C_LEN)))
