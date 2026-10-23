"""The Flying memory's scenes (Task 10) - WORK IN PROGRESS.

Done: the shared helpers, the engine-mirror checks (spline moves, 60 fps smoothness, clearance against player parts,
brooms and balls, camera air, hand contacts, items, ride edges, fliers, lying brooms, lines, keys, walkers,
framing, lawn void fraction) and the lesson scene builder lesson(h, world), which passes all of them.
Not done: the chase, match and snitch scenes, main() (writing memories/flying_*.json), the mutation bites, the
build-budget assert, the SceneDataTest tests. See .superpowers/sdd/2026-10-21-flying-memory/task-10-report.md.
Nothing here writes a scene yet.
"""
import copy
import hashlib
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from scene_common import MEMORIES, dump  # noqa: E402
import scene_engine as E  # noqa: E402
import human_motion as HM  # noqa: E402

MAX_JSON = 900000
HOUSES = ('gryffindor', 'hufflepuff', 'ravenclaw', 'slytherin')
KID, THIRD, ADULT = 0.75, 0.85, 1.0
SFX = 'hogwartsmagic:pensieve.sfx.flying_'
VOICE = 'hogwartsmagic:pensieve.voice.'
RES = os.path.normpath(os.path.join(MEMORIES, '..', '..', '..'))
REGISTERED = set(json.load(open(os.path.join(RES, 'assets', 'hogwartsmagic', 'sounds.json'), encoding='utf-8')))
CLIP = json.load(open(os.path.join(HERE, '..', 'sounds', 'flying_clip_lengths.json'), encoding='utf-8'))
SKINS = set(json.load(open(os.path.join(MEMORIES, '_skins.json'), encoding='utf-8')))
REMEMBRALL, QUAFFLE, BLUDGER, SNITCH = ('hogwartsmagic:memory_remembrall', 'hogwartsmagic:memory_quaffle',
                                        'hogwartsmagic:memory_bludger', 'hogwartsmagic:golden_snitch')
BROOM, RACING = 'hogwartsmagic:memory_broom', 'hogwartsmagic:memory_broom_racing'
WHISTLE, BAT = 'hogwartsmagic:whistle', 'minecraft:mace'
SNITCH_ITEM, REMEMBRALL_ITEM = 'hogwartsmagic:golden_snitch', 'hogwartsmagic:memory_remembrall'


def rival(h):
    """The viewer's opponent: Slytherin, or Gryffindor when the viewer is a Slytherin."""
    return 'gryffindor' if h == 'slytherin' else 'slytherin'


def robes(h):
    return 'hogwartsmagic:%s_robes' % h


# The spoken lines: (speaker shown in the subtitle, subtitle text). Wood's subtitle reads "got to" (his take says
# "gotta"; Task 9's ruling). No line names the boy: he is "you" or "the Seeker", his name only ever {viewer}.
LINES = {
    'flying_hooch_welcome': ('Madam Hooch', "Good afternoon, class. Don't stand there gawping, find a broom."),
    'flying_hooch_left': ('Madam Hooch', 'Stand on the left of it. Right hand out, flat, over the handle.'),
    'flying_hooch_up': ('Madam Hooch', "Now, firmly, and mean it: say 'Up!'"),
    'flying_class_up': ('The class', 'Up!'),
    'flying_viewer_up': ('{viewer}', 'Up!'),
    'flying_neville_whoa': ('Neville', "It's going on its own! I can't stop it, help!"),
    'flying_hooch_nobody': ('Madam Hooch', "Nobody moves! Feet on the grass until I'm back, or you'll answer for it."),
    'flying_malfoy_look': ('Malfoy', 'Look what the clumsy idiot dropped.'),
    'flying_malfoy_come': ('Malfoy', 'Want it? Come up and take it, then.'),
    'flying_malfoy_catch': ('Malfoy', 'Go on, then. Fetch!'),
    'flying_mcg_follow': ('Professor McGonagall', 'You. With me. Now.'),
    'flying_hooch_clean': ('Madam Hooch', 'I want a clean game, from all of you.'),
    'flying_captain': ('Wood', "Just catch the Snitch. That's all you've got to do."),
    'flying_comm_off': ('Commentator', "And they're off!"),
    'flying_comm_goal': ('Commentator', "And it's in!"),
    'flying_comm_seeker': ('Commentator', 'The Seeker has seen something!'),
}
for _l, (_who, _txt) in LINES.items():
    assert 'Harry' not in _txt and 'Potter' not in _txt and '{' not in _txt, _l
assert LINES['flying_captain'][1].endswith("you've got to do."), 'Wood: got to'

# Music: the user has not supplied the tracks yet (ruling: build without music, leave the slots). Each slot is the
# scene it starts in and its time there, with about 2 s of overlap across a hand-over (the outgoing cue fades over
# the chain's last 20 ticks while the next one starts at 0). flying_music.py cuts the user's tracks to these when
# they arrive; until then no music event is emitted (an unregistered id would fail validation).
MUSIC_SLOTS = {
    'flying_lesson': ('', 0.0, "The Norwegian Ridgeback and a Change of Season / Hedwig's Theme: the lesson"),
    'flying_chase': ('_chase', 0.0, 'the Remembrall chase and the catch'),
    'flying_match': ('_match', 0.0, 'Quidditch, Third Year: the walk-out to the hunt'),
    'flying_snitch': ('_snitch', 0.0, 'Quidditch, Third Year: the dive and the catch'),
}


def tk(s):
    return int(round(s * 20))


def r4(v):
    return [round(c, 4) for c in v]


def r3(v):
    return [round(c, 3) for c in v]


def add(a, b, f=1.0):
    return [a[i] + b[i] * f for i in range(3)]


def sub(a, b):
    return [a[i] - b[i] for i in range(3)]


def lerp(a, b, f):
    return [a[i] + (b[i] - a[i]) * f for i in range(len(a))]


def smooth(f):
    f = E.clamp(f, 0.0, 1.0)
    return f * f * (3 - 2 * f)


def smoother(f):
    f = E.clamp(f, 0.0, 1.0)
    return f * f * f * (f * (f * 6 - 15) + 10)


def yaw_to(dx, dz):
    return math.degrees(math.atan2(-dx, dz))


def ahead(p, yaw, d, side=0.0, y=None):
    """p moved d along yaw and `side` to the actor's right."""
    fx, fz = E.fwd_vec(yaw)
    rx, rz = -fz, fx
    return [p[0] + fx * d + rx * side, p[1] if y is None else y, p[2] + fz * d + rz * side]


def unwrap(y, ref):
    while y - ref > 180:
        y -= 360
    while y - ref < -180:
        y += 360
    return y


def norm(v):
    n = math.sqrt(sum(c * c for c in v)) or 1.0
    return [c / n for c in v]


# ================================================================ key lists
class Keys:
    """An actor `path` (or a camera-like list): keys in ticks, kept in order. `ease` is the segment ending at the
    key. A key at the same tick as the last replaces it."""

    def __init__(self, field='pos'):
        self.k = []
        self.field = field

    def at(self, t, v, ease=None):
        k = {'t': t, self.field: r4(v)}
        if ease and ease != 'in_out':
            k['ease'] = ease
        if self.k and abs(self.k[-1]['t'] - t) < 1e-9:
            self.k[-1] = k
        else:
            assert not self.k or t > self.k[-1]['t'], (t, self.k[-1]['t'])
            self.k.append(k)
        return self

    def last(self):
        return self.k[-1][self.field]

    def hold(self, t):
        """Stay where the last key is until t (an eased hold: rest at both ends)."""
        if t > self.k[-1]['t'] + 1e-9:
            self.at(t, self.last())
        return self

    def run(self, t0, t1, fn, step, settle=True):
        """A spline run from the last key (at t0) to t1, sampled from fn(t) every `step` ticks; the last key is
        in_out when settle (the run comes to rest there) else spline."""
        assert abs(self.k[-1]['t'] - t0) < 1e-9, (self.k[-1]['t'], t0)
        n = max(1, int(round((t1 - t0) / step)))
        for i in range(1, n + 1):
            t = t0 + (t1 - t0) * i / n
            self.at(t, fn(t), 'in_out' if (i == n and settle) else 'spline')
        return self

    def through(self, pts, settle=True):
        """Spline keys through [(t, pos)], the last at rest when settle."""
        for i, (t, p) in enumerate(pts):
            self.at(t, p, 'in_out' if (i == len(pts) - 1 and settle) else 'spline')
        return self


def look_keys(pts):
    """[(t, yaw, pitch, roll or None, ease)] -> look keys."""
    out = []
    for t, y, p, r, e in pts:
        k = {'t': t, 'yaw': round(y, 3), 'pitch': round(p, 3)}
        if r is not None:
            k['roll'] = round(r, 3)
        if e and e != 'in_out':
            k['ease'] = e
        if out and abs(out[-1]['t'] - t) < 1e-9:
            out[-1] = k
        else:
            out.append(k)
    return out


def limb(rot=None, offset=None, mode='add'):
    d = {}
    if rot:
        d['rot'] = rot
    if offset:
        d['offset'] = offset
    if mode != 'add':
        d['mode'] = mode
    return d


class Channel:
    """A limb channel (rot or offset): keys of [x, y, z] in ticks; `to` eases from the held value."""

    def __init__(self, v0=(0.0, 0.0, 0.0)):
        self.k = [{'t': 0, 'v': [round(c, 2) for c in v0]}]

    def v(self):
        return self.k[-1]['v']

    def to(self, t0, t1, v, ease=None):
        """Hold the current value to t0, then move to v by t1."""
        if t0 > self.k[-1]['t'] + 1e-9:
            self.k.append({'t': t0, 'v': list(self.v())})
        k = {'t': t1, 'v': [round(c, 2) for c in v]}
        if ease and ease != 'in_out':
            k['ease'] = ease
        assert t1 > self.k[-1]['t'], (t1, self.k[-1]['t'])
        self.k.append(k)
        return self

    def set(self, t, v, ease='linear'):
        k = {'t': t, 'v': [round(c, 2) for c in v]}
        if ease and ease != 'in_out':
            k['ease'] = ease
        if abs(self.k[-1]['t'] - t) < 1e-9:
            self.k[-1] = k
        else:
            assert t > self.k[-1]['t'], (t, self.k[-1]['t'])
            self.k.append(k)
        return self

    def used(self):
        return len(self.k) > 1 or any(abs(c) > 1e-6 for c in self.k[0]['v'])


class Rig:
    """An actor's add-mode limbs: rot and offset channels per part."""

    def __init__(self):
        self.rot, self.off = {}, {}

    def r(self, part):
        return self.rot.setdefault(part, Channel())

    def o(self, part):
        return self.off.setdefault(part, Channel())

    def emit(self):
        out = {}
        for part in sorted(set(self.rot) | set(self.off)):
            d = {}
            if part in self.rot and self.rot[part].used():
                d['rot'] = self.rot[part].k
            if part in self.off and self.off[part].used():
                d['offset'] = self.off[part].k
            if d:
                out[part] = d
        return out


def bend_pose(a):
    """Add-mode (rot, offset px) for the torso pitched `a` degrees forward about the hips, the neck and shoulders
    carried round with it (HumanMotion.hipTurn done whole): {part: (rot add, offset add)}."""
    r = math.radians(a)
    nk = [0.0, 12 - 12 * math.cos(r), -12 * math.sin(r)]
    sh = [0.0, 10 - 10 * math.cos(r), -10 * math.sin(r)]
    return {'body': ([a, 0, 0], nk), 'head': ([0, 0, 0], nk), 'right_arm': ([a, 0, 0], sh),
            'left_arm': ([a, 0, 0], sh)}


# ================================================================ a scene under construction
class Sc:
    def __init__(self, sid, title, length_s, world, house):
        self.sid, self.title, self.world, self.h = sid, title, world, house
        self.L = tk(length_s)
        self.length = length_s
        self.actors, self.events, self.words, self.camera, self.poses = [], [], [], [], {}
        self.lines = []
        self.notes = {}           # facts the checks need: contacts, fliers, windows, ...
        self.contacts = []        # (label, actor id, side, t, target fn(scene) -> point, kind)
        self.lying = []           # (broom id, until tick)
        self.flier_ok = {}        # broom id -> [(t0, t1)] ticks where it may touch blocks (lying, mount, landing)
        self.speed_ok = {}        # broom id -> [(t0, t1, reason)] scripted speed-change exceptions
        self.first_person = []    # (t0, t1, actor id) ticks: first-person shots (the head is exempt)
        self.cause = []           # (tick, kind, label): goals, near misses, catches, entrances: what a reaction answers
        self.reactions = []       # (tick, style, label)
        self.sfx_hits = []        # (sfx name, tick, contact tick, label)
        self.hidden = {}          # actor id -> [(t0, t1)] ticks off stage (a teleport, out of every shot)
        self.framing = []         # (t0, t1, fn(scene, t) -> point, label): what the shot list says is in frame

    def unseen_at(self, t):
        return {aid for aid, wins in self.hidden.items() if in_windows(t, wins)}

    def ev(self, t, kind, eid, **kw):
        assert 0 <= t <= self.L, (self.sid, kind, eid, t)
        e = {'t': t, 'kind': kind, 'id': eid}
        e.update(kw)
        self.events.append(e)
        return e

    def sfx(self, t, name, volume=1.0, loop=False, duration=None, hit=None, label=None):
        assert 'pensieve.sfx.flying_' + name in REGISTERED, name
        kw = {'volume': volume}
        if loop:
            kw['loop'] = True
            if duration:
                kw['duration'] = duration
        self.ev(t, 'sfx', SFX + name, **kw)
        if hit is not None:
            self.sfx_hits.append((name, t, hit, label))

    def say(self, t, line, slot_end=None, volume=1.0):
        who, text = LINES[line]
        secs = float(CLIP[line])
        assert 'pensieve.voice.' + line in REGISTERED, line
        self.ev(t, 'voice', VOICE + line, volume=volume)
        self.words.append({'t': t, 'text': text, 'subtitle': True, 'speaker': who,
                           'duration': max(20, int(math.ceil(secs * 20)) + 6)})
        self.lines.append((line, t, secs, slot_end))
        return t + tk(secs)

    def key(self, t, pos, look, fov=70, cut=False, ease=None):
        k = {'t': t, 'pos': r4(pos)}
        if look is not None:
            k['look_at'] = look if isinstance(look, str) else r4(look)
        if abs(fov - 70) > 1e-6:
            k['fov'] = round(fov, 2)
        if ease and ease != 'in_out':
            k['ease'] = ease
        if cut:
            k['cut'] = True
        if self.camera and self.camera[-1]['t'] == t and not cut:
            self.camera[-1] = k
        else:
            assert not self.camera or t >= self.camera[-1]['t'], (self.sid, t, self.camera[-1]['t'])
            self.camera.append(k)

    def shot(self, t0, t1, cam_fn, look_fn, fov_fn, step=4, settle_end=False):
        """A shot from a cut at t0 to t1: keys every `step` ticks from the functions (positions, a look point, the
        fov), a spline run (the first key a cut), so position, look and fov keep a continuous velocity."""
        n = max(1, int(round((t1 - t0) / float(step))))
        for i in range(n + 1):
            t = t0 + (t1 - t0) * i / n
            tt = int(round(t))
            self.key(tt, cam_fn(tt), look_fn(tt), fov_fn(tt), cut=(i == 0),
                     ease=None if i == 0 else ('in_out' if (i == n and settle_end) else 'spline'))

    def actor(self, a):
        self.actors.append(a)
        return a

    def by_id(self, aid):
        return next(a for a in self.actors if a['id'] == aid)

    def scene(self, set_, radius, nxt=None, end_action=None, fog_far=None):
        s = {'title': self.title, 'length': self.L, 'radius': radius}
        if fog_far:
            s['fog_far'] = fog_far
        if nxt:
            s['next'] = nxt
        if end_action:
            s['end_action'] = end_action
        s['set'] = set_
        if self.poses:
            s['poses'] = self.poses
        s['actors'] = self.actors
        s['events'] = sorted(self.events, key=lambda e: e['t'])
        s['words'] = sorted(self.words, key=lambda w: w['t'])
        s['camera'] = self.camera
        return s


# ================================================================ the player model: hands, eyes, the arm solver
def parts_of(scene, a, t):
    return {p[0]: p for p in E.posed_parts(scene, a, t)}


def is_riding(scene, a, t):
    return E.broom_rider_at(scene, a, t) is not None


def hand(scene, a, side, t, bx=None, bz=None, life=None):
    """The hand (arm tip) at tick t from the emitted JSON (posed_parts: rest or crouch, held-item raise, pose,
    look, limbs add/set, the rider frame), with vanilla's bobModelPart sway (bx, bz; the left arm mirrored) and a
    life corner added on a standing actor (a rider's arms are the rider layer: no sway, no life)."""
    _, root, yaw, s, piv, rot = parts_of(scene, a, t)[side]
    if not is_riding(scene, a, t):
        sign = 1.0 if side == 'right_arm' else -1.0
        bx = E.BOB_MID[0] if bx is None else bx
        bz = E.BOB_MID[1] if bz is None else bz
        rot = [rot[0] + sign * bx, rot[1], rot[2] + sign * bz]
        if life:
            dp, dr = life
            piv = [piv[0] + dp[0], piv[1] + dp[1], piv[2] + dp[2]]
            rot = [rot[0] + dr[0], rot[1] + dr[1], rot[2] + sign * dr[2]]
    return E.arm_tip(root, yaw, s, side, rot, pivot_px=piv)


def hand_corners(scene, a, side, t):
    """The hand at every sway corner (zRot 0 and 0.1 rad, xRot +-0.05; mirrored on the left) and, for a living
    actor whose arm is keyed, every corner of what life adds (life_arm_corners); a rider's one hand."""
    if is_riding(scene, a, t):
        return [hand(scene, a, side, t)]
    lifes = [None]
    if a.get('life', True) and a.get('type') == 'player':
        lifes = E.life_arm_corners()
    out = []
    for bz in E.BOB_Z:
        for bx in E.BOB_X:
            for lf in lifes:
                out.append(hand(scene, a, side, t, bx, bz, lf))
    return out


def solve_arm(scene, a, side, t, target, iters=60):
    """The [dx, dy, dz] degrees to add to an arm's rotation at tick t so its hand (at the sway's middle) lands on
    target (damped least squares over the three angles, small turns preferred); returns (delta, residual)."""
    _, root, yaw, s, piv, rot = parts_of(scene, a, t)[side]
    rider = is_riding(scene, a, t)
    sign = 1.0 if side == 'right_arm' else -1.0

    def tip(d):
        r = [rot[0] + d[0], rot[1] + d[1], rot[2] + d[2]]
        if not rider:
            r = [r[0] + sign * E.BOB_MID[0], r[1], r[2] + sign * E.BOB_MID[1]]
        return E.arm_tip(root, yaw, s, side, r, pivot_px=piv)
    best = None
    for start in ([0.0, 0.0, 0.0], [-60.0, 0.0, 0.0], [-90.0, 0.0, sign * 20.0], [-40.0, 0.0, sign * 30.0]):
        d = list(start)
        lam = 1e-3
        for _ in range(iters):
            p = tip(d)
            e = sub(target, p)
            err = math.sqrt(sum(c * c for c in e))
            if err < 1e-5:
                break
            J = []
            for j in range(3):
                dd = list(d)
                dd[j] += 0.25
                q = tip(dd)
                J.append([(q[i] - p[i]) / 0.25 for i in range(3)])
            reg = (lam, lam * 6.0, lam)              # a turn about the arm's own axis is the last resort
            A = [[sum(J[r][i] * J[c][i] for i in range(3)) + (reg[r] if r == c else 0.0) for c in range(3)]
                 for r in range(3)]
            g = [sum(J[r][i] * e[i] for i in range(3)) for r in range(3)]
            step = _solve3(A, g)
            n = math.sqrt(sum(c * c for c in step))
            if n > 20.0:
                step = [c * 20.0 / n for c in step]
            nd = [d[i] + step[i] for i in range(3)]
            if math.dist(tip(nd), target) < err:
                d, lam = nd, max(lam * 0.3, 1e-6)
            else:
                lam *= 10.0
        r = math.dist(tip(d), target)
        if best is None or r < best[1] - 1e-6 or (abs(r - best[1]) < 1e-6 and sum(map(abs, d)) < sum(map(abs, best[0]))):
            best = (d, r)
    return best


def _solve3(A, b):
    import copy as _c
    M = [_c.copy(A[i]) + [b[i]] for i in range(3)]
    for c in range(3):
        piv = max(range(c, 3), key=lambda r: abs(M[r][c]))
        M[c], M[piv] = M[piv], M[c]
        if abs(M[c][c]) < 1e-12:
            return [0.0, 0.0, 0.0]
        for r in range(3):
            if r != c:
                f = M[r][c] / M[c][c]
                M[r] = [M[r][k] - f * M[c][k] for k in range(4)]
    return [M[i][3] / M[i][i] for i in range(3)]


def head_eye(scene, a, t, fwd=0.0):
    """The eye from the posed head (posed_parts: the look, the rider frame and lean), fwd blocks out of the face."""
    _, root, yaw, s, piv, rot = parts_of(scene, a, t)['head']
    return E.eye_of(root, yaw, s, piv, rot, fwd)


def mouth(scene, a, t):
    """The mouth: 2.5 px under the eye on the face."""
    _, root, yaw, s, piv, rot = parts_of(scene, a, t)['head']
    v = HM._turned(0.0, -1.5, -4.0, *[math.radians(c) for c in rot])
    return E.model_to_world(root, yaw, s, piv[0] + v[0], piv[1] + v[1], piv[2] + v[2])


def broom_point(scene, bid, t, local):
    """A point given in the broom's own frame (x left, y up, z to the tip) in the world at tick t."""
    b = E.broom_pose(scene, E.actor_by_id(scene, bid), t)
    v = E._mat_vec(b['m'], local)
    return [b['pos'][i] + v[i] for i in range(3)]


def broom_under(root, yaw, rider_scale):
    """The broom position (its seat point) that seats a rider of rider_scale whose root is `root`, broom level at
    `yaw` (pitch and roll 0): root - M x seat."""
    seat = E.rider_seat(rider_scale)
    v = E._mat_vec(E.broom_matrix(yaw, 0.0, 0.0), seat)
    return [root[i] - v[i] for i in range(3)]


LIE_Y = -E.BROOM_BOX[BROOM][0][1] + 0.004       # a school broom lying level on the grass: its brush rests on it
GRIP_BELOW = 0.035                              # the handle axis sits this far under the arm tip in a closed fist


def view(sc):
    """The scene so far, as the engine mirrors read it."""
    return {'actors': sc.actors, 'poses': sc.poses, 'length': sc.L, 'camera': sc.camera, 'events': sc.events}


def refresh(a, rig):
    lm = rig.emit()
    if lm:
        a['limbs'] = lm
    else:
        a.pop('limbs', None)


def reach(sc, a, rig, side, t0, t1, target, ease=None):
    """Move an add-mode arm from its held value at t0 to where its hand (at the sway's middle) is on `target` at t1.
    Returns the residual (blocks)."""
    ch = rig.r(side)
    held = list(ch.v())
    ch.to(t0, t1, held)
    refresh(a, rig)
    d, res = solve_arm(view(sc), a, side, t1, target)
    ch.k[-1]['v'] = [round(held[0] + d[0], 2), round(held[1] + d[1], 2), round(held[2] + d[2], 2)]
    if ease:
        ch.k[-1]['ease'] = ease
    refresh(a, rig)
    return math.dist(hand(view(sc), a, side, t1), target)


def park_keys(keys, t, park):
    """Jump an actor out of sight at tick t: a key at t - 0.25 where it is, the parked key at t (a cut: under half a
    tick, the ball speed rule's exemption)."""
    keys.at(t - 0.25, keys.last(), 'linear')
    keys.at(t, park, 'linear')


# ================================================================ the lesson: flying_<h>
import flying_lawn as FL  # noqa: E402
import quidditch_pitch_set as QP  # noqa: E402

LA = FL.ANCHORS
ROWS = LA['broom_line']
LAWN_Y = LA['lawn_y']
STEP_IN = 0.25                   # students stand this much nearer their broom than the anchor (the hand reaches it)
HAND_H = 0.72                    # a first-year's flat hand out over the handle
PARK_LAWN = [-12.0, -36.0, 36.0]  # under the lawn's shell, out of every shot
LAWN_SET = {'templates': [{'id': 'hogwartsmagic:flying_lawn', 'at': list(FL.TEMPLATE_AT)}],
            'schematics': [{'id': 'mini_hogwarts', 'at': list(FL.SCHEMATIC['at']), 'rotation': FL.SCHEMATIC['rotation']}],
            'time_of_day': FL.TIME_OF_DAY}
# (flying_lawn.SCHEMATIC names it 'hogwartsmagic:mini_hogwarts'; the validator knows schematics by bare id)

L1 = 43.0
T1 = {'hooch_in': (0.8, 5.6), 'welcome': 4.6, 'left': 10.0, 'hands': (12.0, 13.4), 'up': 16.4, 'chorus': 21.5,
      'viewer_up': 24.4, 'leap': 25.05, 'contact': 25.25, 'v_mount': 27.4, 'n_mount': 27.9, 'whistle': (27.9, 28.6),
      'n_lift': 29.2, 'whoa': 29.8, 'bolt': 30.2, 'buck': 31.0, 'fall': 33.2, 'land': 34.0, 'hooch_run': (29.6, 35.0),
      'kneel': 35.1, 'nobody': 35.8, 'stand': 38.4, 'leave': 39.6}

# who does what when the class says "Up!"
SUCCESS = {'e1': 22.0, 'e7': 22.3, 'w3': 22.55, 'w6': 22.8}          # brooms up into the hand at these times
MOUNT_AT = {'viewer': T1['v_mount'], 'neville': T1['n_mount'], 'e1': 27.6, 'e7': 28.0, 'w3': 27.7, 'w6': 28.1}
SMACK = 'w5'
ROLLERS = {'e3': 21.9, 'e8': 22.4, 'w4': 22.1}
TWITCHERS = {'e0': 21.7, 'e2': 22.2, 'e5': 21.8, 'e9': 22.6, 'w0': 22.0, 'w2': 21.75, 'malfoy': 22.3, 'w9': 22.5}
NERVOUS = {'e2': (1.0, 9.5), 'w0': (2.2, 10.4), 'e8': (3.0, 8.0), 'neville': (0.5, 11.2), 'w6': (4.0, 10.9)}


def lesson_people(h):
    """The two rows: east (the viewer's house, the viewer 5th from the north), west (the rival house, Neville and
    Malfoy among them). Each entry: id, house, skin, spot, yaw, broom spot, broom yaw."""
    r = rival(h)
    own = [i for i in range(2, 31) if not (h == 'gryffindor' and i == 1) and not (h == 'slytherin' and i == 27)]
    oth = [i for i in range(2, 31) if not (r == 'gryffindor' and i == 1) and not (r == 'slytherin' and i == 27)]
    out = []
    for side, house, pool in (('east', h, own), ('west', r, oth)):
        for i in range(10):
            e = ROWS[side][i]
            aid = ('e' if side == 'east' else 'w') + str(i)
            skin, hs = 'student_%s_%02d' % (house, pool[(i * 3 + 1) % len(pool)]), house
            if aid == 'e4':
                aid, skin = 'viewer', None
            elif aid == 'w1':
                aid, skin, hs = 'neville', 'student_gryffindor_01', 'gryffindor'
            elif aid == 'w8':
                aid, skin, hs = 'malfoy', 'student_slytherin_27', 'slytherin'
            yaw = e['student_yaw']
            spot = ahead(e['student'], yaw, 0.0, side=STEP_IN)
            out.append({'id': aid, 'house': hs, 'skin': skin, 'spot': spot, 'yaw': yaw,
                        'broom': [e['broom'][0], LIE_Y, e['broom'][2]], 'broom_yaw': e['broom_yaw']})
    for p in out:
        assert p['skin'] is None or p['skin'] in SKINS, p['skin']
    return out


def lying_y(yaw, pitch, roll):
    """The seat-point height that rests a school broom's box on the grass (lawn_y) at this orientation."""
    m = E.broom_matrix(yaw, pitch, roll)
    lo, hi = E.BROOM_BOX[BROOM]
    low = min(E._mat_vec(m, [x, y, z])[1] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2]))
    return LAWN_Y - low + 0.004


class Broom:
    """A memory broom's path and look (yaw, pitch, roll) under construction, ticks."""

    def __init__(self, sc, bid, pos, yaw, btype=BROOM, scale=1.0, roll_keys=True):
        self.sc, self.id, self.type = sc, bid, btype
        self.path = Keys().at(0, pos)
        self.look = [[0, yaw, 0.0, 0.0 if roll_keys else None, None]]
        self.roll_keys = roll_keys
        self.a = sc.actor({'id': bid, 'type': btype})
        if scale != 1.0:
            self.a['scale'] = scale
        self.sync()

    def yaw(self):
        return self.look[-1][1]

    def turn(self, t, yaw, pitch=0.0, roll=0.0, ease=None):
        """A look key at t (yaw unwrapped to the last)."""
        y = unwrap(yaw, self.look[-1][1])
        k = [t, y, pitch, roll if self.roll_keys else None, ease]
        if abs(self.look[-1][0] - t) < 1e-9:
            self.look[-1] = k
        else:
            assert t > self.look[-1][0], (self.id, t, self.look[-1][0])
            self.look.append(k)
        return self

    def hold_look(self, t):
        l = self.look[-1]
        if t > l[0] + 1e-9:
            self.turn(t, l[1], l[2], l[3] or 0.0)
        return self

    def sync(self):
        self.a['path'] = self.path.k
        self.a['look'] = look_keys([tuple(k) for k in self.look])
        return self.a


def student_actor(sc, p, life=True):
    a = {'id': p['id'], 'type': 'player', 'scale': KID,
         'skin': {'viewer': True} if p['id'] == 'viewer' else {'texture': p['skin']},
         'items': {'chest': robes(p['house'])}}
    if not life:
        a['life'] = False
    return sc.actor(a)


def lesson(h, world):
    sc = Sc('flying_' + h, 'The Flying Lesson', L1, world, h)
    people = lesson_people(h)
    P = {p['id']: p for p in people}
    V = view(sc)
    hooch_spot = LA['hooch_spot']['pos']

    # ---- the brooms (vehicles first: riders name them)
    B = {}
    for p in people:
        B[p['id']] = Broom(sc, 'broom_' + p['id'], p['broom'], p['broom_yaw'])
        sc.lying.append(('broom_' + p['id'], None))

    # ---- the people: path, look, limbs (add), actions
    S, R, LOOK, ACT, CLIPS = {}, {}, {}, {}, {}
    for p in people:
        S[p['id']] = student_actor(sc, p)
        R[p['id']] = Rig()
        LOOK[p['id']] = [(0, p['yaw'], 0.0)]
        ACT[p['id']] = []
        CLIPS[p['id']] = []
        S[p['id']]['path'] = Keys().at(0, p['spot']).k
    hooch = sc.actor({'id': 'hooch', 'type': 'player', 'skin': {'texture': 'witch_05'},
                      'items': {'mainhand': WHISTLE}})
    hrig = Rig()

    def set_look(aid, pts):
        a = S[aid]
        a['look'] = [{'t': t, 'yaw': round(y, 2), 'pitch': round(pi, 2)} for t, y, pi in pts]

    # while Hooch talks the class turn a little toward her, then back to their brooms, eyes down, for the hands
    for p in people:
        aid = p['id']
        to_h = yaw_to(hooch_spot[0] - p['spot'][0], hooch_spot[2] - p['spot'][2])
        part = unwrap(to_h, p['yaw'])
        rnd = random.Random('look' + aid)
        tilt = p['yaw'] + (part - p['yaw']) * rnd.uniform(0.25, 0.45)
        t_a = tk(T1['welcome'] + rnd.uniform(-0.3, 0.6))
        t_b = tk(T1['hands'][0] - rnd.uniform(0.2, 0.6))
        LOOK[aid] = [(0, p['yaw'], 0.0), (t_a, p['yaw'], 0.0), (t_a + 12, tilt, 4.0), (t_b - 8, tilt, 4.0),
                     (t_b, p['yaw'], 22.0)]
    for aid, (c0, c1) in NERVOUS.items():
        CLIPS[aid] += [{'t': tk(c0), 'name': 'NERVOUS', 'blend': 8}, {'t': tk(c1), 'name': 'IDLE', 'blend': 8}]
    for aid in S:
        if CLIPS[aid]:
            S[aid]['anims'] = CLIPS[aid]
        set_look(aid, LOOK[aid])
    vy = P['viewer']['yaw']
    LOOK['viewer'] = LOOK['viewer'] + [(470, vy, 22.0), (486, vy, 48.0), (505, vy, 46.0), (540, vy, 40.0)]
    set_look('viewer', LOOK['viewer'])
    sc.notes['look_world'] = LOOK

    # ---- right hands out flat over the handles (12.0-13.4, each on its own beat)
    out_t = {}
    for p in people:
        aid = p['id']
        rnd = random.Random('hand' + aid)
        t0 = tk(T1['hands'][0] + rnd.uniform(0.0, 1.0))
        t1 = t0 + 10
        out_t[aid] = (t0, t1)
        tgt = [p['broom'][0], HAND_H, p['broom'][2]]
        res = reach(sc, S[aid], R[aid], 'right_arm', t0, t1, tgt)
        assert res < 0.06, (aid, res)

    # in his own eyes (S8) he looks down and right at the broom by his hand
    R['viewer'].r('head').to(474, 486, [0, 20, 0])
    R['viewer'].r('head').to(548, 562, [0, 0, 0])
    refresh(S['viewer'], R['viewer'])
    lesson_brooms(sc, people, P, B, S, R, ACT, out_t)
    lesson_neville(sc, P, B, S, R, ACT)
    lesson_hooch(sc, hooch, hrig, S)
    lesson_remembrall(sc, S)
    lesson_finish_people(sc, people, P, B, S, R, ACT)
    lesson_camera(sc, P, B, S, hooch)
    sc.ev(0, 'effect', 'eyes_open', at=[0, 0, 0], duration=60)
    sc.ev(0, 'effect', 'sky', at=r3(LA['lawn_centre']), style='day')
    sc.sfx(0, 'lawn_air', 0.55, loop=True)
    scene = sc.scene(LAWN_SET, FL.RADIUS, nxt='flying_%s_chase' % h, fog_far=FL.FOG_FAR)
    sc.notes.update(lawn=True, moments=[check_moments_lesson], ball_paths=[('remembrall', [tk(T1['land'])])])
    return sc, scene


def twitch(b, T, pos, yaw, rnd, big=1.0):
    """A broom that stirs on the grass: two little hops with its nose and roll wobbling, resting again by T + 14."""
    b.path.hold(T)
    b.hold_look(T)
    hop = [(3, 0.07 * big, -4.0, 3.0), (6, 0.0, 2.0, -2.0), (9, 0.04 * big, -2.5, 2.0), (12, 0.0, 1.0, -1.0),
           (14, 0.0, 0.0, 0.0)]
    for dt, up, pi, ro in hop:
        pi *= big * rnd.uniform(0.7, 1.2)
        ro *= big * rnd.uniform(0.7, 1.2)
        if dt == 14:
            pi = ro = 0.0
        y = lying_y(yaw, pi, ro) + up
        b.path.at(T + dt, [pos[0], y, pos[2]], 'spline' if dt < 14 else None)
        b.turn(T + dt, yaw, pi, ro, 'spline' if dt < 14 else None)
    return T + 14


def roll_over(b, T, pos, yaw, sign=1.0, n=26):
    """A broom that rolls once right over about its handle, its brush swinging over the top, back where it lay."""
    b.path.hold(T)
    b.hold_look(T)
    for i in range(1, n + 1):
        f = smooth(i / float(n))
        ro = sign * 360.0 * f
        e = 'spline' if i < n else None
        b.path.at(T + i, [pos[0], lying_y(yaw, 0.0, ro), pos[2]], e)
        b.turn(T + i, yaw, 0.0, ro, e)
    return T + n


def lesson_brooms(sc, people, P, B, S, R, ACT, out_t):
    V = view(sc)
    mounts = sc.notes.setdefault('mounts', {})
    sc.notes['first_up'] = {}
    rest = {}                                         # broom id -> tick it is lying still again (or moves for good)
    for aid, t in sorted(TWITCHERS.items()):
        p = P[aid]
        rnd = random.Random('tw' + aid)
        end = twitch(B[aid], tk(t), p['broom'], p['broom_yaw'], rnd)
        if rnd.random() < 0.5:
            end = twitch(B[aid], end + 4, p['broom'], p['broom_yaw'], rnd, 0.6)
        rest['broom_' + aid] = end
        sc.notes['first_up'][aid] = tk(T1['chorus'])
    for aid, t in sorted(ROLLERS.items()):
        p = P[aid]
        end = roll_over(B[aid], tk(t), p['broom'], p['broom_yaw'], 1.0 if aid != 'e8' else -1.0)
        rest['broom_' + aid] = end
        sc.notes['first_up'][aid] = tk(T1['chorus'])

    # brooms that come up into the hand: the four quick ones at the chorus, then the viewer's, first try
    holds = dict((aid, t) for aid, t in SUCCESS.items())
    holds['viewer'] = T1['contact']
    for aid, t in sorted(holds.items(), key=lambda x: x[1]):
        p, b = P[aid], B[aid]
        tc = tk(t)
        h = hand(V, S[aid], 'right_arm', tc)
        in_hand = [h[0], h[1] - GRIP_BELOW, h[2]]
        lead = 6 if aid != 'viewer' else 9
        b.path.hold(tc - lead)
        b.hold_look(tc - lead)
        if aid == 'viewer':                           # it stirs, then shoots up into his palm
            b.path.at(tc - 6, [p['broom'][0], lying_y(p['broom_yaw'], -3.0, 2.0) + 0.02, p['broom'][2]], 'spline')
            b.turn(tc - 6, p['broom_yaw'], -3.0, 2.0, 'spline')
            b.path.at(tc - 4, [p['broom'][0], lying_y(p['broom_yaw'], 2.0, -1.5), p['broom'][2]], 'spline')
            b.turn(tc - 4, p['broom_yaw'], 2.0, -1.5, 'spline')
        mid = lerp(p['broom'], in_hand, 0.55)
        b.path.at(tc - 2, [mid[0], mid[1] + 0.03, mid[2]], 'spline')
        b.turn(tc - 2, p['broom_yaw'], -2.0, 0.0, 'spline')
        b.path.at(tc, in_hand)
        b.turn(tc, p['broom_yaw'], 0.0, 0.0)
        sc.contacts.append(('%s: the broom into the hand' % aid, aid, 'right_arm', tc,
                            ('broom', b.id, [0.0, GRIP_BELOW, 0.0]), 'broom_up'))
        sc.sfx(tc, 'broom_up', 1.0 if aid == 'viewer' else 0.35, hit=tc, label='broom into %s hand' % aid)
        rest['broom_' + aid] = tc - lead
        sc.notes['first_up'][aid] = tk(T1['chorus']) if aid != 'viewer' else tk(T1['viewer_up'])
        # the mount: the broom swings under him (out of shot) and he rides from MOUNT_AT
        tm = tk(MOUNT_AT[aid])
        under = broom_under(p['spot'], p['broom_yaw'], KID)
        b.path.hold(tm - 10)
        b.hold_look(tm - 10)
        b.path.at(tm, under)
        b.turn(tm, p['broom_yaw'], 0.0, 0.0)
        b.path.at(tm + 12, [under[0], under[1] + 0.05, under[2]])
        b.turn(tm + 12, p['broom_yaw'], 0.0, 0.0)
        R[aid].r('right_arm').to(tm - 10, tm, [0, 0, 0])
        refresh(S[aid], R[aid])
        mounts[aid] = tm
        sc.flier_ok.setdefault(b.id, []).append((tm - 10, tm + 14))

    # the smack: w5's broom leaps, spins round and cracks him across the face, then drops back to the grass
    p, b = P[SMACK], B[SMACK]
    tc = tk(22.15)
    F = head_eye(V, S[SMACK], tc, fwd=0.03)
    y1 = unwrap(p['broom_yaw'] + 180.0, p['broom_yaw'])
    d = E._mat_vec(E.broom_matrix(y1, -10.0, 0.0), [0.0, 0.0, 1.0])
    at_face = [F[i] - 1.6 * d[i] for i in range(3)]
    b.path.hold(tc - 6)
    b.hold_look(tc - 6)
    b.path.at(tc - 3, lerp(p['broom'], at_face, 0.45), 'spline')
    b.turn(tc - 3, p['broom_yaw'] + (y1 - p['broom_yaw']) * 0.5, -25.0, 30.0, 'spline')
    b.path.at(tc, at_face, 'spline')
    b.turn(tc, y1, -10.0, 0.0, 'spline')
    away = add(at_face, [1.0, 0.3, 0.25])
    b.path.at(tc + 6, away, 'spline')
    b.turn(tc + 6, y1 + 25.0, 18.0, 35.0, 'spline')
    land = [p['broom'][0] + 1.4, 0.0, p['broom'][2] + 0.2]
    b.path.at(tc + 14, [land[0], lying_y(y1 + 10.0, 0.0, 0.0), land[2]])
    b.turn(tc + 14, y1 + 10.0, 0.0, 0.0)
    rest[b.id] = tc - 6
    sc.speed_ok[b.id] = [(tc - 6, tc + 14, 'the broom that leaps up and smacks a boy')]
    sc.notes['smack'] = (tc, SMACK)
    sc.notes['first_up'][SMACK] = tk(T1['chorus'])
    sc.contacts.append(('%s: the broom tip into his face' % SMACK, SMACK, 'face', tc,
                        ('broom', b.id, [0.0, 0.0, 1.6]), 'smack'))
    sc.sfx(tc, 'broom_up', 0.7, hit=tc, label='the smack')
    a, rg = S[SMACK], R[SMACK]
    ACT[SMACK].append({'t': tc, 'do': 'hurt'})
    rg.r('head').to(tc, tc + 3, [-30, 8, 0])
    rg.r('head').to(tc + 14, tc + 30, [-6, 0, 0])
    rg.r('right_arm').to(tc, tc + 6, [-20, 0, 25])
    rg.r('left_arm').to(tc + 1, tc + 7, [-125, -15, -8])
    rg.r('left_arm').to(tc + 34, tc + 46, [0, 0, 0])
    rg.r('right_arm').to(tc + 30, tc + 40, [0, 0, 0])
    refresh(a, rg)
    back = ahead(p['spot'], p['yaw'], -0.38)
    a['path'] = Keys().at(0, p['spot']).hold(tc).at(tc + 6, back).k
    sc.notes['rest'] = rest

    # the rest of the class give up: hands down once the chorus has failed them
    for p_ in people:
        aid = p_['id']
        if aid in holds or aid in ('neville', SMACK):
            continue
        rnd = random.Random('down' + aid)
        t0 = tk(25.4 + rnd.uniform(0.0, 1.6))
        R[aid].r('right_arm').to(t0, t0 + 12, [0, 0, 0])
        refresh(S[aid], R[aid])


NEVILLE_FLIGHT = [   # (tick, broom seat point): the lift, the bolt, the bucking loop, the throw (664) and away
    (584, None), (590, [-3.92, 0.62, 22.6]), (596, [-4.05, 0.95, 22.5]), (602, [-3.9, 1.35, 22.75]),
    (608, [-4.3, 2.1, 23.3]), (614, [-5.2, 3.3, 24.4]), (620, [-6.5, 4.6, 26.0]), (630, [-8.9, 6.0, 29.0]),
    (640, [-12.3, 6.2, 30.9]), (650, [-14.9, 6.4, 33.8]), (658, [-14.6, 5.7, 36.0]), (664, [-13.4, 5.0, 36.9]),
    (670, [-11.9, 6.6, 37.6]), (678, [-14.0, 10.5, 39.6]), (688, [-19.5, 17.5, 42.0]), (698, [-26.5, 27.0, 44.5]),
    (710, [-38.0, 42.0, 48.0]), (724, [-55.0, 58.0, 53.0]), (742, [-80.0, 70.0, 60.0])]


def lesson_neville(sc, P, B, S, R, ACT):
    V = view(sc)
    p, b, a, rg = P['neville'], B['neville'], S['neville'], R['neville']
    tm = tk(T1['n_mount'])
    T_FALL, T_LAND = tk(T1['fall']), tk(T1['land'])
    sc.notes['neville'] = {'mount': tm, 'fall': T_FALL, 'land': T_LAND, 'lift': tk(T1['n_lift'])}
    under = broom_under(p['spot'], p['broom_yaw'], KID)
    # offstage (Hooch's close shot) his broom finally stirs and he gets on it
    b.path.hold(tm - 10)
    b.hold_look(tm - 10)
    b.path.at(tm, under)
    b.turn(tm, p['broom_yaw'], 0.0, 0.0)
    rest = [under[0], under[1] + 0.05, under[2]]
    b.path.at(tm + 12, rest)
    b.turn(tm + 12, p['broom_yaw'], 0.0, 0.0)
    sc.notes['rest']['broom_neville'] = tm - 10
    sc.flier_ok.setdefault(b.id, []).append((tm - 10, tm + 14))
    rg.r('right_arm').to(tm - 10, tm, [0, 0, 0])
    sc.notes['mounts']['neville'] = tm
    sc.notes['first_up']['neville'] = tk(T1['chorus'])
    # the flight: spline keys through NEVILLE_FLIGHT, resting at the end
    b.path.hold(NEVILLE_FLIGHT[0][0])
    for i, (t, q) in enumerate(NEVILLE_FLIGHT[1:]):
        b.path.at(t, q, 'in_out' if i == len(NEVILLE_FLIGHT) - 2 else 'spline')
    # its look: the heading and climb of its own path, with the bucking on top (pitch and roll kicks)
    b.hold_look(NEVILLE_FLIGHT[0][0])
    prev = p['broom_yaw']
    rnd = random.Random('buck')
    last = NEVILLE_FLIGHT[-1][0]
    for t in range(NEVILLE_FLIGHT[0][0] + 3, last + 1, 3):
        hd = unwrap(E.broom_heading(b.path.k, t), prev)
        prev = hd
        cl = E.broom_climb(b.path.k, t)
        amp = (0.25 if t < 604 else 1.0 if t < 664 else max(0.0, 1.0 - (t - 664) / 70.0))
        ph = (t - 584) / 20.0
        pitch = cl + amp * (18.0 * math.sin(2 * math.pi * 2.6 * ph) + rnd.uniform(-4, 4))
        roll = amp * (32.0 * math.sin(2 * math.pi * 1.9 * ph + 0.7) + rnd.uniform(-6, 6))
        yj = amp * 14.0 * math.sin(2 * math.pi * 1.3 * ph + 1.9)
        e = 'spline' if t + 3 <= last else None
        b.turn(t, hd + yj, max(-70.0, min(70.0, pitch)), roll, e)
    b.turn(last + 6, prev, 0.0, 0.0)
    sc.speed_ok[b.id] = [(NEVILLE_FLIGHT[0][0], last + 6, "Neville's broom bolts and bucks")]
    b.sync()

    # the rider: on from tm until he is thrown at T_FALL; flailing from the bolt, still by 660; arms up as he falls
    a['ride'] = {'on': b.id, 'from': tm, 'until': T_FALL}
    seat = E.rider_seat(KID)
    path = Keys().at(0, p['spot']).hold(tm - 0.001)
    path.at(tm, seat).at(T_FALL - 0.001, seat)
    a['path'] = path.k
    a['look'] = [{'t': 0, 'yaw': p['yaw'], 'pitch': 0.0}]      # placeholder: the real look is set below
    off = E.rider_root(V, a, T_FALL - 0.001)[0]
    # the fall: a ballistic drop onto the grass at neville_fall, landing on T_LAND
    land = LA['neville_fall']['pos']
    n = T_LAND - T_FALL
    vy = 0.04
    g = 2.0 * (off[1] + vy * n - land[1]) / (n * n)
    path.at(T_FALL, off, 'linear')
    for k in range(2, n, 2):
        f = k / float(n)
        path.at(T_FALL + k, [off[0] + (land[0] - off[0]) * f, off[1] + vy * k - 0.5 * g * k * k,
                             off[2] + (land[2] - off[2]) * f], 'spline')
    path.at(T_LAND, land, 'linear')
    sc.notes['neville']['g'] = g
    # flailing (add mode on top of the grip), decaying to nothing by 660
    for part, amp, base in (('right_arm', 38, [-35, 0, 12]), ('left_arm', 38, [-35, 0, -12]),
                            ('right_leg', 24, [-10, 0, 6]), ('left_leg', 24, [-10, 0, -6])):
        ch = rg.r(part)
        ch.to(604, 610, [base[0] * 0.5, base[1], base[2]])
        sgn = 1 if part in ('right_arm', 'left_leg') else -1
        t = 614
        while t <= 650:
            fall = max(0.0, 1.0 - max(0, t - 636) / 16.0)
            ch.set(t, [base[0] + sgn * amp * fall, base[1], base[2] + sgn * 8 * fall], None)
            sgn = -sgn
            t += 4
        ch.to(652, 660, [0, 0, 0])
    sc.notes['neville']['flail_end'] = 660
    rg.r('right_arm').to(T_FALL, T_FALL + 8, [-150, 0, 25])
    rg.r('left_arm').to(T_FALL, T_FALL + 8, [-150, 0, -25])
    rg.r('right_arm').to(T_LAND, T_LAND + 10, [-38, 0, 14])
    rg.r('left_arm').to(T_LAND, T_LAND + 10, [-52, -18, -24])
    rg.r('head').to(T_LAND, T_LAND + 8, [28, 0, 0])
    rg.r('head').to(tk(T1['stand']), tk(T1['stand']) + 10, [10, 0, 0])
    refresh(a, rg)
    ACT['neville'] += [{'t': T_LAND, 'do': 'sneak'}, {'t': T_LAND, 'do': 'hurt'}, {'t': tk(T1['stand']), 'do': 'stand'}]
    # his look: world, then broom-relative (eyes down, darting), world again from the throw
    look = [{'t': 0, 'yaw': p['yaw'], 'pitch': 0.0}]
    for t, y, pi in sc.notes['look_world']['neville']:
        if 0 < t < tm - 1:
            look.append({'t': t, 'yaw': round(y, 2), 'pitch': round(pi, 2)})
    look.append({'t': tm - 0.001, 'yaw': look[-1]['yaw'], 'pitch': look[-1]['pitch']})
    look.append({'t': tm, 'yaw': 0.0, 'pitch': 12.0})
    for t, y, pi in ((584, 0.0, 12.0), (592, -10.0, 30.0), (604, 15.0, 35.0), (618, -25.0, 10.0), (630, 20.0, 25.0),
                     (642, -30.0, 5.0), (654, 10.0, 30.0)):
        look.append({'t': t, 'yaw': y, 'pitch': pi})
    look.append({'t': T_FALL - 0.001, 'yaw': 10.0, 'pitch': 30.0})
    by = E.broom_pose(V, b.a, T_FALL - 0.001)['yaw'] + 10.0
    look.append({'t': T_FALL, 'yaw': round(by, 2), 'pitch': 30.0})
    ly = unwrap(LA['neville_fall']['yaw'], by)
    look.append({'t': T_LAND, 'yaw': round(ly, 2), 'pitch': 20.0})
    to_h = unwrap(yaw_to(-12.3 - land[0], 35.2 - land[2]), ly)
    look.append({'t': 704, 'yaw': round(ly, 2), 'pitch': 20.0})
    look.append({'t': 716, 'yaw': round(to_h, 2), 'pitch': -10.0})
    a['look'] = look
    sc.notes['neville_look_done'] = True


def path_yaws(keys, t0, t1, step, start_yaw):
    """Look keys facing along a walker's path (its heading through the mirror), unwrapped."""
    out, prev = [], start_yaw
    for t in range(t0, t1 + 1, step):
        y = unwrap(E.broom_heading(keys, t), prev)
        prev = y
        out.append((t, y))
    return out


def lesson_hooch(sc, hooch, hrig, S):
    V = view(sc)
    spot = LA['hooch_spot']['pos']
    a, rg = hooch, hrig
    path = Keys().at(0, [-1.5, 0.0, 6.0]).hold(16)
    path.through([(64, [-0.6, 0.0, 11.4]), (112, spot)])
    T_RUN0, T_RUN1 = tk(T1['hooch_run'][0]), tk(T1['hooch_run'][1])
    path.hold(T_RUN0)
    run = [(608, [-4.2, 0.0, 17.4]), (628, [-8.5, 0.0, 19.8]), (650, [-10.8, 0.0, 26.0]), (672, [-11.6, 0.0, 31.0]),
           (T_RUN1, [-12.3, 0.0, 35.2])]
    path.through(run)
    T_STAND, T_LEAVE = tk(T1['stand']), tk(T1['leave'])
    path.hold(T_LEAVE)
    path.through([(826, [-13.3, 0.0, 38.4]), (sc.L, [-14.2, 0.0, 41.3])], settle=False)
    a['path'] = path.k
    look = [(0, 10.0, 0.0), (16, 10.0, 0.0)]
    look += [(t, y, 0.0) for t, y in path_yaws(path.k, 22, 104, 6, 10.0)]
    look += [(118, 0.0, 0.0), (150, -16.0, 3.0), (196, 14.0, 3.0), (236, 0.0, 6.0), (276, -12.0, 6.0),
             (330, 8.0, 2.0), (380, -6.0, 2.0), (420, 0.0, 4.0), (470, 18.0, 6.0), (520, -10.0, 4.0), (556, 0.0, 0.0),
             (580, 0.0, 0.0), (590, 33.0, -18.0)]
    for t, y in path_yaws(path.k, T_RUN0 + 6, T_RUN1 - 8, 6, 33.0):
        look.append((t, y, -12.0 if t < 664 else 6.0))
    nf = LA['neville_fall']['pos']
    face_n = unwrap(yaw_to(nf[0] - run[-1][1][0], nf[2] - run[-1][1][2]), look[-1][1])
    look += [(T_RUN1, face_n, 30.0), (T_STAND + 4, face_n, 10.0), (T_STAND + 14, unwrap(-80.0, face_n), 0.0),
             (T_LEAVE - 6, unwrap(-80.0, face_n), 0.0)]
    for t, y in path_yaws(path.k, T_LEAVE + 4, sc.L, 8, look[-1][1]):
        look.append((t, y, 4.0))
    a['look'] = [{'t': t, 'yaw': round(y, 2), 'pitch': round(pi, 2)} for t, y, pi in look]
    talk = lambda line: tk(T1[line] + float(CLIP['flying_hooch_' + line]) - 0.2)    # she gestures while she speaks
    a['anims'] = [{'t': tk(T1['welcome']), 'name': 'TALK', 'blend': 6},
                  {'t': talk('welcome'), 'name': 'IDLE', 'blend': 8},
                  {'t': tk(T1['left']), 'name': 'TALK', 'blend': 6},
                  {'t': talk('left'), 'name': 'IDLE', 'blend': 8},
                  {'t': tk(T1['up']), 'name': 'TALK', 'blend': 6},
                  {'t': talk('up'), 'name': 'IDLE', 'blend': 8}]
    a['actions'] = [{'t': tk(T1['kneel']), 'do': 'sneak'}, {'t': T_STAND, 'do': 'stand'}]
    # the whistle to her lips as the class waits mounted; down again when Neville's broom moves
    w0, w1 = tk(T1['whistle'][0]), tk(T1['whistle'][1])
    # a rigid arm's hand lies 10 px from the shoulder: the head turns a little from the hand and the shoulder
    # drops a pixel, so the lips come out to where the hand can reach them
    rg.r('head').to(w0, w1, [0, -25, 0])
    rg.o('right_arm').to(w0, w1, [0, 1.0, 1.5])
    refresh(a, rg)
    res = reach(sc, a, rg, 'right_arm', w0, w1, mouth(V, a, w1))
    assert res < 0.03, res
    rg.r('right_arm').to(586, 596, [0, 0, 0])
    rg.r('head').to(586, 596, [0, 0, 0])
    rg.o('right_arm').to(586, 596, [0, 0, 0])
    rg.r('left_arm').to(T_STAND + 16, T_STAND + 26, [-48, 0, -10])          # pointing the class to stay put
    rg.r('left_arm').to(T_LEAVE - 8, T_LEAVE + 4, [0, 0, 0])
    refresh(a, rg)
    sc.contacts.append(('hooch: the whistle to her lips', 'hooch', 'right_arm', w1, ('mouth', 'hooch'), 'whistle'))
    # the lines
    sc.say(tk(T1['welcome']), 'flying_hooch_welcome')
    sc.say(tk(T1['left']), 'flying_hooch_left')
    sc.say(tk(T1['up']), 'flying_hooch_up')
    sc.say(tk(T1['chorus']), 'flying_class_up')
    sc.say(tk(T1['viewer_up']), 'flying_viewer_up')
    sc.say(tk(T1['whoa']), 'flying_neville_whoa')
    sc.say(tk(T1['nobody']), 'flying_hooch_nobody')
    # Neville goes with her: up, then off toward the hospital wing
    nev = S['neville']
    np_ = Keys()
    np_.k = nev['path']
    np_.hold(T_LEAVE)
    np_.through([(826, [-14.6, 0.0, 39.2]), (sc.L, [-15.6, 0.0, 42.0])], settle=False)
    lk = nev['look']
    for t, y in path_yaws(np_.k, T_LEAVE + 4, sc.L, 8, lk[-1]['yaw']):
        lk.append({'t': t, 'yaw': round(y, 2), 'pitch': 12.0})


def lesson_remembrall(sc, S):
    nev = S['neville']
    T_LAND = tk(T1['land'])
    r = E.ball_radius(REMEMBRALL, KID) + 0.002
    land = LA['neville_fall']['pos']
    ly = E._yaw_pitch(nev, T_LAND)[0]
    pocket = ahead(land, ly, 0.05, side=0.2, y=0.33)
    drop = [LA['remembrall_drop'][0], r, LA['remembrall_drop'][2]]
    rest = [-6.4, r, 37.3]
    k = Keys().at(0, PARK_LAWN).hold(T_LAND - 0.25)
    # the jump out of the park is an in_out key: the spline run after it then starts from rest (a `linear` key
    # would hand the run the jump's own speed, 150 blocks a tick, and fling the ball 40 blocks up)
    k.at(T_LAND, pocket)
    for dt in (2, 4, 6, 8):
        f = dt / 10.0
        q = lerp(pocket, drop, f)
        k.at(T_LAND + dt, [q[0], q[1] + 0.3 * 4 * f * (1 - f), q[2]], 'spline')
    k.at(T_LAND + 10, drop, 'linear')
    hop = lerp(drop, rest, 0.06)
    k.at(T_LAND + 14, [hop[0], r + 0.06, hop[2]], 'spline')
    start = lerp(drop, rest, 0.12)
    k.at(T_LAND + 18, start, 'spline')
    t0, t1 = T_LAND + 18, T_LAND + 66
    for t in range(t0 + 4, t1, 4):
        u = (t - t0) / float(t1 - t0)
        k.at(t, lerp(start, rest, 1 - (1 - u) ** 2), 'spline')
    k.at(t1, rest)
    sc.actor({'id': 'remembrall', 'type': REMEMBRALL, 'scale': KID, 'path': k.k})
    sc.notes['remembrall'] = {'pocket': (T_LAND, pocket), 'rest': (t1, rest), 'drop': drop}


def lesson_finish_people(sc, people, P, B, S, R, ACT):
    """Paths, looks and ride windows for the class once every broom is keyed."""
    V = view(sc)
    mounts = sc.notes['mounts']
    for p in people:
        aid = p['id']
        a = S[aid]
        if ACT[aid]:
            a['actions'] = sorted(ACT[aid], key=lambda x: x['t'])
        refresh(a, R[aid])
        if aid == 'neville':
            continue
        world = [(t, y, pi) for t, y, pi in sc.notes['look_world'][aid]]
        if aid in mounts:
            tm = mounts[aid]
            a['ride'] = {'on': 'broom_' + aid, 'from': tm}
            path = Keys().at(0, p['spot'])
            path.hold(tm - 0.001).at(tm, E.rider_seat(KID))
            a['path'] = path.k
            look = [{'t': t, 'yaw': round(y, 2), 'pitch': round(pi, 2)} for t, y, pi in world if t < tm - 1]
            look.append({'t': tm - 0.001, 'yaw': look[-1]['yaw'], 'pitch': look[-1]['pitch']})
            look.append({'t': tm, 'yaw': 0.0, 'pitch': look[-1]['pitch']})
            # eyes on Neville from the lift to the fall, then on Hooch; the head only (the body faces the broom)
            look.append({'t': 580, 'yaw': 0.0, 'pitch': look[-1]['pitch']})
            for t in range(590, 781, 10):
                tgt = E.rider_root(V, S['neville'], t)[0] if t < 680 else E.sample_path(S['neville']['path'], t)
                d = sub(tgt, p['spot'])
                yw = unwrap(yaw_to(d[0], d[2]) - p['broom_yaw'], 0.0)
                yw = max(-70.0, min(70.0, yw)) * (0.9 if aid == 'viewer' else 0.75)
                pi = -math.degrees(math.atan2(d[1] + 0.6 - 1.2, math.hypot(d[0], d[2])))
                look.append({'t': t, 'yaw': round(yw, 2), 'pitch': round(max(-40.0, min(30.0, pi)), 2),
                             'ease': 'spline'})
            look[-1].pop('ease')
            a['look'] = look
        else:
            look = [{'t': t, 'yaw': round(y, 2), 'pitch': round(pi, 2)} for t, y, pi in world]
            body = p['yaw']
            rnd = random.Random('watch' + aid)
            t_w = 600 + rnd.randint(0, 8)
            if look[-1]['t'] < t_w - 12:
                look.append({'t': t_w - 12, 'yaw': look[-1]['yaw'], 'pitch': look[-1]['pitch']})
            for t in range(t_w, 781, 12):
                tgt = E.rider_root(V, S['neville'], t)[0] if t < 680 else E.sample_path(S['neville']['path'], t)
                d = sub(tgt, p['spot'])
                yw = unwrap(yaw_to(d[0], d[2]), body)
                yw = body + max(-60.0, min(60.0, yw - body)) * 0.55
                pi = -math.degrees(math.atan2(d[1] + 0.4 - 1.2, math.hypot(d[0], d[2])))
                if t > look[-1]['t']:
                    look.append({'t': t, 'yaw': round(yw, 2), 'pitch': round(max(-40.0, min(30.0, pi)), 2),
                                 'ease': 'spline'})
            look[-1].pop('ease', None)
            a['look'] = look
    for b in B.values():
        b.sync()


# ================================================================ checks (the Global Constraints' asserts and the Flying ones)
PLANTS = {'minecraft:dandelion', 'minecraft:oxeye_daisy', 'minecraft:allium', 'minecraft:short_grass',
          'minecraft:tall_grass', 'minecraft:fern', 'minecraft:poppy', 'minecraft:cornflower', 'minecraft:azure_bluet'}


def cam_world(world):
    """The set as the camera's air check reads it: see-through plants (a flower, a tuft) are no wall."""
    return {c: s for c, s in world.items() if E.bid_of(s) not in PLANTS}


def in_windows(t, wins):
    return any(a <= t <= b for a, b in wins)


def broom_box_distance(scene, broom, t, p):
    """Blocks from point p to a broom's model box at tick t (0 inside), and the box's world corners."""
    b = E.broom_pose(scene, broom, t)
    lo, hi = E.broom_box(broom['type'], broom.get('scale', 1.0))
    loc = E._mat_vec(E._transpose(b['m']), sub(p, b['pos']))
    q = [E.clamp(loc[i], lo[i], hi[i]) for i in range(3)]
    corners = []
    for x in (lo[0], hi[0]):
        for y in (lo[1], hi[1]):
            for z in (lo[2], hi[2]):
                corners.append(add(b['pos'], E._mat_vec(b['m'], [x, y, z])))
    return math.dist(loc, q), corners


def check_clearance(sc, scene, problems):
    """Assert 4: the camera, four times a tick, at least CAM_CLEAR from every player part box (posed from the JSON:
    crouch, item raise, pose, look, limbs, the rider frame) and every broom and ball box, unless no point of it is
    in the frustum (aspect 2.0); the first-person head is exempt. Returns the worst (d, t, what)."""
    cam = scene['camera']
    best = [9.0, None, None]
    players = [a for a in scene['actors'] if a.get('type') == 'player']
    brooms = [a for a in scene['actors'] if E.is_broom(a)]
    balls = [a for a in scene['actors'] if a['type'] in E.FAST_MOVERS]
    fp = sc.first_person
    for t, c, shot in E.camera_samples(cam, scene['length']):
        view_ = None
        for a in players:
            root = E.rider_root(scene, a, t)[0]
            if math.dist(root, c) > 3.6 * a.get('scale', 1.0):
                continue
            for part, rt, yaw, s, piv, rot in E.posed_parts(scene, a, t):
                if part == 'head' and any(t0 <= t <= t1 and aid == a['id'] for t0, t1, aid in fp):
                    continue
                d = E.box_distance(rt, yaw, s, piv, rot, E.PART_BOX[part], c)
                if d >= best[0] and d >= E.CAM_CLEAR:
                    continue
                view_ = view_ or E.engine_look(scene, cam, t)
                if not any(E.in_view(c, view_[0], view_[1], q)
                           for q in E.box_surface(rt, yaw, s, piv, rot, E.PART_BOX[part])):
                    continue
                if d < E.CAM_CLEAR:
                    problems.append('%s: the camera at t %.2f is %.3f from %s %s' % (sc.sid, t, d, a['id'], part))
                    return best
                best = [d, t, a['id'] + ' ' + part]
        for b in brooms:
            bp = E.track_sample(b['path'], t)
            if math.dist(bp, c) > 2.6 * b.get('scale', 1.0):
                continue
            d, corners = broom_box_distance(scene, b, t, c)
            if d >= best[0] and d >= E.CAM_CLEAR:
                continue
            view_ = view_ or E.engine_look(scene, cam, t)
            if not any(E.in_view(c, view_[0], view_[1], q) for q in corners):
                continue
            if d < E.CAM_CLEAR:
                problems.append('%s: the camera at t %.2f is %.3f from broom %s' % (sc.sid, t, d, b['id']))
                return best
            best = [d, t, b['id']]
        for b in balls:
            bp = ball_world(scene, b['id'], t)
            rad = E.ball_radius(b['type'], b.get('scale', 1.0)) if E.is_ball(b['type']) else 0.15 * b.get('scale', 1.0)
            d = math.dist(bp, c) - rad
            if d >= best[0] and d >= E.CAM_CLEAR:
                continue
            view_ = view_ or E.engine_look(scene, cam, t)
            if not E.in_view(c, view_[0], view_[1], bp):
                continue
            if d < E.CAM_CLEAR:
                problems.append('%s: the camera at t %.2f is %.3f from ball %s' % (sc.sid, t, d, b['id']))
                return best
            best = [d, t, b['id']]
    return best


def check_camera_air(sc, scene, problems, margin=0.06):
    """Assert 4, blocks: every camera key, and the real path between keys in steps of 0.08 (the spline's own path),
    clear of block shapes by `margin`. Returns the number of points tested."""
    w = sc.cam_world
    cam = scene['camera']
    n = 0
    for i, k in enumerate(cam):
        n += 1
        if not E.in_air(w, k['pos'], margin):
            problems.append('%s camera key %d t %s pos %s is in %s' % (sc.sid, i, k['t'], k['pos'],
                                                                      w.get(E.cell(k['pos']))))
        if i and not k.get('cut') and k['t'] > cam[i - 1]['t']:
            a0, a1 = cam[i - 1]['pos'], k['pos']
            steps = int(max(abs(a1[j] - a0[j]) for j in range(3)) / 0.08) + 1
            spl = E._cam_spline(cam, i)
            if spl:
                steps *= 2
            for s_ in range(1, steps):
                tt = cam[i - 1]['t'] + (k['t'] - cam[i - 1]['t']) * s_ / steps
                p = E.engine_cam(cam, tt)
                n += 1
                if not E.in_air(w, p, margin):
                    problems.append('%s camera move into key %d (t %s) passes %s (%s)' % (
                        sc.sid, i, k['t'], r3(p), w.get(E.cell(p))))
                    break
    return n


def contact_target(scene, spec, t):
    kind = spec[0]
    if kind == 'broom':
        return broom_point(scene, spec[1], t, spec[2])
    if kind == 'ball':
        return ball_world(scene, spec[1], t if len(spec) < 3 else spec[2])
    if kind == 'mouth':
        return mouth(scene, E.actor_by_id(scene, spec[1]), t)
    if kind == 'point':
        return spec[1]
    raise ValueError(kind)


CONTACT_GAP = 0.15


def check_contacts(sc, scene, problems):
    """Assert 5: every hand contact recomputed from the emitted JSON (posed_parts: add/set, the item raise, the
    crouch, the rider frame; the sway corners and life's corners on a standing actor): the gap at most 0.15 at every
    corner. A face contact (the smack) is the broom tip against the face point. Returns [(label, worst gap)]."""
    out = []
    for label, aid, side, t, spec, kind in sc.contacts:
        a = E.actor_by_id(scene, aid)
        tgt = contact_target(scene, spec, t)
        if side == 'face':
            pts = [head_eye(scene, a, t, fwd=0.03)]
        elif side == 'bat':
            pts = [bat_point(scene, a, t, h) for h in hand_corners(scene, a, 'right_arm', t)]
        else:
            pts = hand_corners(scene, a, side, t)
        gap = max(math.dist(p, tgt) for p in pts)
        out.append((label, gap))
        if gap > CONTACT_GAP:
            problems.append('%s: %s at t %s: the hand is %.3f from it (limit %.2f)' % (sc.sid, label, t, gap,
                                                                                     CONTACT_GAP))
    return out


def bat_point(scene, a, t, tip):
    """The Beater's bat: its striking face 0.5 x scale out along the forearm from the fist."""
    _, root, yaw, s, piv, rot = parts_of(scene, a, t)['right_arm']
    sh = E.model_to_world(root, yaw, s, *piv)
    d = norm(sub(tip, sh))
    return add(tip, d, 0.5 * s)


def check_items(sc, scene, problems):
    """Assert 6: every main-hand change between empty and held on a standing actor's add-mode arm is balanced by
    item_raise_steps (the hand jumps under 0.01 across the item tick, at the sway's middle); a rider's arms are the
    rider layer (no raise), checked the same way. Hand motion in a first-person close-up at most 0.03 a tick.
    Returns (worst jump, worst first-person hand step)."""
    worst = [0.0, 0.0]
    for a in scene['actors']:
        if a.get('type') != 'player':
            continue
        ticks = sorted({x['t'] for x in a.get('actions', []) if x['do'] in ('hold', 'equip', 'use_item')
                        and (x['do'] != 'equip' or x.get('slot') == 'mainhand')})
        for T in ticks:
            before = E.EquipTimeline_item(a, 'mainhand', T - 1)
            after = E.EquipTimeline_item(a, 'mainhand', T)
            if (before is None) == (after is None):
                continue
            j = math.dist(hand(scene, a, 'right_arm', T), hand(scene, a, 'right_arm', T - 2 * E.ITEM_STEP_EPS))
            worst[0] = max(worst[0], j)
            if j > 0.01:
                problems.append('%s: %s hand jumps %.3f at the item change at t %s' % (sc.sid, a['id'], j, T))
    for t0, t1, aid in sc.first_person:
        a = E.actor_by_id(scene, aid)
        for side in ('right_arm', 'left_arm'):
            prev = None
            for t in range(t0, t1 + 1):
                cam = E.engine_cam(scene['camera'], t)
                h = hand(scene, a, side, t)
                rel = sub(h, cam)                       # the hand as the viewer sees it: relative to his own eye
                if prev is not None:
                    st = math.dist(rel, prev)
                    lk = E.engine_look(scene, scene['camera'], t)
                    if E.in_view(cam, lk[0], lk[1], h) and math.dist(cam, h) < 1.5:
                        worst[1] = max(worst[1], st)
                        if st > sc.notes.get('fp_hand_limit', {}).get((aid, side, t), 0.03):
                            problems.append('%s: %s %s hand moves %.3f in one tick at t %d in a first-person close-up' % (
                                sc.sid, aid, side, st, t))
                            break
                prev = rel
    return worst


def check_rides(sc, scene, problems):
    """Assert 9: at every mount and dismount the rider's root is continuous within 0.02, and the path is bracketed
    (a key at T - 0.001 and one at T). Returns the worst jump."""
    worst = 0.0
    for a in scene['actors']:
        r = E.ride_of(a)
        if r is None or isinstance(a.get('ride'), str):
            continue
        for T in (r[1], r[2]):
            if T in (E.INF, -E.INF):
                continue
            j = math.dist(E.rider_root(scene, a, T - 0.0005)[0], E.rider_root(scene, a, T)[0])
            worst = max(worst, j)
            if j > 0.02:
                problems.append('%s: %s root jumps %.3f at the ride edge t %s' % (sc.sid, a['id'], j, T))
            ts = [k['t'] for k in a.get('path', [])]
            if not (any(abs(x - (T - 0.001)) < 1e-6 for x in ts) and any(abs(x - T) < 1e-6 for x in ts)):
                problems.append('%s: %s path is not bracketed at the ride edge t %s' % (sc.sid, a['id'], T))
    return worst


def check_fliers(sc, scene, problems):
    """Assert 8: every broom path at least 0.5 from every block, except where it rests by the ground (lying on the
    lawn, standing under a mounted rider) and its mount and landing windows; at least 1.2 from every other broom
    while either flies; speed changes at most 0.12 blocks/tick^2 (the scripted exceptions by name). Returns (least
    flier gap, largest speed change)."""
    w = sc.world
    brooms = [a for a in scene['actors'] if E.is_broom(a)]
    least_pair, worst_acc = 9.0, 0.0
    flying = {}
    n = scene['length']
    for b in brooms:
        ok = sc.flier_ok.get(b['id'], [])
        pos = [E.track_sample(b['path'], t) for t in range(n + 1)]
        fl = []
        for t, p in enumerate(pos):
            spd = math.dist(pos[min(n, t + 1)], pos[max(0, t - 1)]) / 2.0
            ridden = E.broom_ridden(scene, b['id'], t) > 0
            grounded = p[1] < 1.0 and (spd < 0.06 or not ridden)     # lying, stirring, in a hand, stood astride
            if grounded or in_windows(t, ok):
                continue
            if not E.in_air(w, p, 0.5) or not E.in_air(w, p, 0.0):
                problems.append('%s: broom %s at t %d (%s) is within 0.5 of a block' % (sc.sid, b['id'], t, r3(p)))
                break
        fl = []
        for t, p in enumerate(pos):
            spd = math.dist(pos[min(n, t + 1)], pos[max(0, t - 1)]) / 2.0
            ridden = E.broom_ridden(scene, b['id'], t) > 0
            fl.append(not (p[1] < 1.0 and (spd < 0.06 or not ridden)))
        exc = sc.speed_ok.get(b['id'], [])
        for t in range(2, n + 1):
            if any(t0 <= t <= t1 for t0, t1, _ in exc):
                continue
            acc = math.dist(sub(pos[t], pos[t - 1]), sub(pos[t - 1], pos[t - 2]))
            worst_acc = max(worst_acc, acc)
            if acc > 0.12 + 1e-9:
                problems.append('%s: broom %s speed changes %.3f blocks/tick^2 at t %d' % (sc.sid, b['id'], acc, t))
                break
        flying[b['id']] = (pos, fl)
    ids = sorted(flying)
    for i in range(len(ids)):
        for j in range(i + 1, len(ids)):
            pa, fa = flying[ids[i]]
            pb, fb = flying[ids[j]]
            for t in range(n + 1):
                if not (fa[t] or fb[t]):
                    continue
                d = math.dist(pa[t], pb[t])
                least_pair = min(least_pair, d)
                if d < 1.2:
                    problems.append('%s: brooms %s and %s are %.2f apart at t %d' % (sc.sid, ids[i], ids[j], d, t))
                    break
    return least_pair, worst_acc


def check_lying(sc, scene, problems):
    """Every lawn broom lies on the grass (its box bottom within 0.05 of lawn_y) until it first moves."""
    worst = 0.0
    for bid, _ in sc.lying:
        b = E.actor_by_id(scene, bid)
        keys = b['path']
        first_move = None
        p0 = keys[0]['pos']
        l0 = (b.get('look') or [{}])[0]
        for k in keys[1:]:
            if math.dist(k['pos'], p0) > 1e-6:
                first_move = keys[keys.index(k) - 1]['t']
                break
        for k in (b.get('look') or [])[1:]:
            if any(abs(k.get(f, 0.0) - l0.get(f, 0.0)) > 1e-6 for f in ('yaw', 'pitch', 'roll')):
                pt = b['look'][b['look'].index(k) - 1]['t']
                first_move = pt if first_move is None else min(first_move, pt)
                break
        end = first_move if first_move is not None else scene['length']
        for t in range(0, int(end) + 1, 5):
            low = E.broom_lowest(scene, bid, t) - LAWN_Y
            worst = max(worst, abs(low))
            if not (-1e-6 <= low <= 0.05):
                problems.append('%s: broom %s lies %.3f off the grass at t %d' % (sc.sid, bid, low, t))
                break
    return worst


def check_lines(sc, scene, problems):
    """Every voice line has its subtitle with the clip's measured length, no two lines overlap, and every line ends
    before the scene does. No spoken text names the boy."""
    for line, t, secs, slot in sc.lines:
        w = [x for x in scene['words'] if x['t'] == t and x.get('subtitle') and x['text'] == LINES[line][1]]
        if not w or w[0]['duration'] < int(math.ceil(secs * 20)):
            problems.append('%s: %s has no subtitle covering its %.2f s' % (sc.sid, line, secs))
        if t + secs * 20 > scene['length'] - 6:
            problems.append('%s: %s runs into the scene end' % (sc.sid, line))
        if slot is not None and t + secs * 20 > slot + 1e-6:
            problems.append('%s: %s runs past its slot' % (sc.sid, line))
    sl = sorted(sc.lines, key=lambda l: l[1])
    for a, b in zip(sl, sl[1:]):
        if b[1] < a[1] + a[2] * 20 + 2:
            problems.append('%s: %s starts before %s ends' % (sc.sid, b[0], a[0]))
    for w in scene['words']:
        txt = w['text'] + ' ' + w.get('speaker', '')
        if any(x in txt for x in ('Harry', 'Potter')) or ('{' in txt and txt.replace('{viewer}', '').count('{')):
            problems.append('%s: word text %r names the boy or holds a placeholder other than {viewer}' % (sc.sid, txt))


def check_keys(sc, scene, problems):
    """Ids unique, key times sorted and inside the scene."""
    ids = [a['id'] for a in scene['actors']]
    if len(ids) != len(set(ids)):
        problems.append('%s: duplicate actor ids' % sc.sid)
    for a in scene['actors']:
        for kk in ('path', 'look', 'anims', 'poses', 'actions'):
            ts = [k['t'] for k in a.get(kk, [])]
            if ts != sorted(ts) or any(not (0 <= t <= scene['length']) for t in ts):
                problems.append('%s: %s %s keys out of order or outside the scene' % (sc.sid, a['id'], kk))
            if kk in ('path', 'look', 'anims') and len(ts) != len(set(ts)):
                problems.append('%s: %s %s has two keys at one time' % (sc.sid, a['id'], kk))
        for part, d in a.get('limbs', {}).items():
            for kind in ('rot', 'offset'):
                ts = [k['t'] for k in d.get(kind, [])]
                if ts != sorted(ts) or len(ts) != len(set(ts)):
                    problems.append('%s: %s %s %s keys out of order' % (sc.sid, a['id'], part, kind))
        if a.get('type') == 'player':
            s = a.get('scale', 1.0)
            if s not in (KID, THIRD, ADULT):
                problems.append('%s: %s has scale %s' % (sc.sid, a['id'], s))
    cam = scene['camera']
    if [k['t'] for k in cam] != sorted(k['t'] for k in cam) or cam[0]['t'] != 0:
        problems.append('%s: camera keys out of order or not from 0' % sc.sid)
    ev = [e['t'] for e in scene['events']]
    if ev != sorted(ev):
        problems.append('%s: events out of order' % sc.sid)


def check_void(sc, scene, problems, solid):
    """Lawn cameras (ruling for Task 7): every camera key's frame, and the camera every 10 ticks, shows at most
    VOID_BOUND of empty space below the horizon, and stays inside CAM_REGION. Returns the worst."""
    cam = scene['camera']
    worst = (0.0, None)
    x0, x1, z0, z1 = FL.CAM_REGION
    cache = sc.notes.setdefault('void_cache', {})
    ts = sorted(set([k['t'] for k in cam] + list(range(0, scene['length'] + 1, 10))))
    for t in ts:
        c = E.engine_cam(cam, t)
        lk, fov = E.engine_look(scene, cam, t)
        if not (x0 - 1e-6 <= c[0] <= x1 + 1e-6 and z0 - 1e-6 <= c[2] <= z1 + 1e-6):
            problems.append('%s: camera at t %s (%s) is outside CAM_REGION' % (sc.sid, t, r3(c)))
        key = (tuple(round(v, 2) for v in c), tuple(round(v, 1) for v in lk), round(fov))
        if key not in cache:
            cache[key] = FL.void_fraction(c, lk, solid, fov=max(fov, 40.0))
        v = cache[key]
        if v > worst[0]:
            worst = (v, t)
        if v > FL.VOID_BOUND:
            problems.append('%s: camera at t %s shows %.3f void below the horizon' % (sc.sid, t, v))
    return worst


# ================================================================ camera helpers
def smoothed(fn, sigma, lo=None, hi=None):
    """fn(t) averaged with Gaussian weights over +-2 sigma ticks (whole-tick samples, clamped to [lo, hi]): a look
    target that follows an actor without his bob, his bank or his keys' corners."""
    if sigma <= 0:
        return fn
    n = int(math.ceil(2 * sigma))
    ws = [math.exp(-0.5 * (j / sigma) ** 2) for j in range(-n, n + 1)]
    tot = sum(ws)
    cache = {}

    def g(t):
        if t in cache:
            return cache[t]
        acc = [0.0, 0.0, 0.0]
        for j, wt in zip(range(-n, n + 1), ws):
            tt = t + j
            if lo is not None:
                tt = max(lo, tt)
            if hi is not None:
                tt = min(hi, tt)
            v = fn(tt)
            acc = [acc[i] + v[i] * wt for i in range(3)]
        cache[t] = [c / tot for c in acc]
        return cache[t]
    return g


def slerp_dir(d0, d1, e):
    d0, d1 = norm(d0), norm(d1)
    c = E.clamp(sum(d0[i] * d1[i] for i in range(3)), -1.0, 1.0)
    om = math.acos(c)
    if om < 1e-6:
        return d0
    s0, s1 = math.sin((1 - e) * om) / math.sin(om), math.sin(e * om) / math.sin(om)
    return [d0[i] * s0 + d1[i] * s1 for i in range(3)]


def move(p0, p1, t0, t1, ease=smoother):
    return lambda t: lerp(p0, p1, ease((t - t0) / float(t1 - t0)))


def const(v):
    return lambda t: v


def ramp(a, b, t0, t1, ease=smoother):
    return lambda t: a + (b - a) * ease((t - t0) / float(t1 - t0))


def turn_look(pos_fn, l0, l1, t0, t1, dist=30.0, ease=smoother):
    """A look point that turns on the great circle from looking at l0 (from pos_fn(t0)) to l1 (from pos_fn(t1))."""
    d0 = sub(l0, pos_fn(t0))
    d1 = sub(l1, pos_fn(t1))
    return lambda t: add(pos_fn(t), slerp_dir(d0, d1, ease((t - t0) / float(t1 - t0))), dist)


def eye_fn(scene, aid, up=0.0):
    a = E.actor_by_id(scene, aid)

    def f(t):
        if E.broom_rider_at(scene, a, t) is not None:
            p = E.rider_eye(scene, a, t)
        else:
            r = E.own_sample(a, 'path', t) if isinstance(a.get('ride'), dict) else E.sample_path(a['path'], t)
            p = [r[0], r[1] + E.actor_eye_height(a, t), r[2]]
        return [p[0], p[1] + up, p[2]]
    return f


def first_person(sc, scene, aid, t0, t1, fwd=0.2, dist=2.5, fov=72.0, step=2, look_fn=None, sigma=0.0):
    """A first-person shot at the actor's posed eye (fwd blocks out of his face), looking where his head looks
    (or at look_fn(t)); the head is exempt from the clearance check inside the shot. `sigma` (ticks) steadies the
    eye a little (a flier's lean and bank), as a camera operator would."""
    a = E.actor_by_id(scene, aid)
    cam = smoothed(lambda t: head_eye(scene, a, t, fwd), sigma)

    def look(t):
        if look_fn:
            return look_fn(t)
        e0, e1 = head_eye(scene, a, t, 0.0), head_eye(scene, a, t, 1.0)
        return add(cam(t), norm(sub(e1, e0)), dist)
    sc.shot(t0, t1, cam, look, const(fov), step)
    sc.first_person.append((t0, t1, aid))


def lesson_camera(sc, P, B, S, hooch):
    V = view(sc)
    hs = LA['hooch_spot']['pos']
    # S1 0.0-6.4 the establishing crane: high over the lawn, the castle on its crag, down to the class
    P0, P1 = [16.0, 24.0, 64.0], [7.6, 3.6, 47.5]
    pf = move(P0, P1, 0, 128)
    sc.shot(0, 128, pf, turn_look(pf, [-6.0, 34.0, -120.0], [0.0, 1.0, 24.5], 0, 128), ramp(70.0, 62.0, 0, 128), 4)
    # S2 6.4-10.0 Madam Hooch, medium, a slow push
    pf = move([1.35, 1.55, 20.7], [1.15, 1.55, 20.15], 128, 200)
    sc.shot(128, 200, pf, const([hs[0], 1.58, hs[2]]), ramp(46.0, 43.0, 128, 200), 8)
    # S3 10.0-13.8 down the east row at hand height as the hands go out over the handles
    pf = move([1.25, 1.05, 38.6], [1.25, 1.05, 31.4], 200, 276)
    sc.shot(200, 276, pf, lambda t: [4.0, 0.72, pf(t)[2] - 3.2], const(55.0), 6)
    # S4 13.8-16.6 the boy, front left, his hand out over his broom
    v = P['viewer']['spot']
    pf = move([v[0] - 1.65, 0.95, v[2] + 0.95], [v[0] - 1.55, 0.97, v[2] + 0.8], 276, 332)
    sc.shot(276, 332, pf, const([v[0] - 0.35, 0.8, v[2] - 0.1]), const(50.0), 8)
    # S5 16.6-19.4 Hooch close: "Now, firmly, and mean it"
    pf = move([0.6, 1.62, 18.75], [0.5, 1.62, 18.45], 332, 388)
    sc.shot(332, 388, pf, const([hs[0], 1.58, hs[2]]), ramp(38.0, 36.0, 332, 388), 8)
    # S6 19.4-21.4 over his right shoulder, along the arm, the west row beyond
    pf = move([v[0] + 0.85, 1.42, v[2] - 0.65], [v[0] + 0.8, 1.4, v[2] - 0.6], 388, 428)
    sc.shot(388, 428, pf, const([v[0] - 1.8, 0.35, v[2] - 0.45]), const(55.0), 8)
    # S7 21.4-24.2 the chorus, across the aisle at the west row: brooms twitch and roll, one smacks a boy
    pf = move([1.35, 0.92, 26.0], [1.4, 0.95, 26.9], 428, 484)
    sc.shot(428, 484, pf, move([-4.0, 0.62, 30.9], [-4.0, 0.65, 31.3], 428, 484), const(56.0), 8)
    # S8 24.2-27.0 first person: his broom leaps into his hand on the first try
    bv = P['viewer']['broom']
    aim = smoothed(lambda t: lerp([bv[0] - 0.3, 0.42, bv[2] + 0.05], [bv[0] - 0.3, 0.62, bv[2] + 0.05],
                                   smooth((t - 498) / 14.0)), 3.0)
    first_person(sc, V, 'viewer', 484, 540, fwd=0.05, fov=70.0, look_fn=aim)
    # S9 27.0-29.6 Hooch, the whistle to her lips (the class mounts behind the camera)
    pf = move([0.95, 1.58, 19.6], [0.85, 1.58, 19.3], 540, 592)
    sc.shot(540, 592, pf, const([hs[0], 1.62, hs[2]]), const(42.0), 8)
    # S10 29.6-33.0 Neville's broom lifts him, bolts and bucks; Hooch runs through the foreground
    nev = smoothed(eye_fn(V, 'neville'), 6.0)
    pf = move([-0.2, 0.75, 18.6], [-0.6, 0.9, 19.2], 592, 660)
    sc.shot(592, 660, pf, lambda t: lerp(nev(t), [-6.0, 2.5, 28.0], 0.25), ramp(60.0, 66.0, 592, 660), 4)
    # S11 33.0-35.6 wide: he is thrown, falls; the broom shoots off over the wall
    pf = move([8.5, 6.4, 29.5], [8.3, 6.2, 30.6], 660, 712)
    sc.shot(660, 712, pf, move([-11.5, 3.5, 35.0], [-12.5, 1.0, 36.0], 660, 712), const(62.0), 8)
    # S12 35.6-37.6 the Remembrall rolls out of the grass and comes to rest
    pf = move([-5.75, 0.3, 36.75], [-5.8, 0.3, 36.8], 712, 752)
    sc.shot(712, 752, pf, const([-8.0, 0.12, 37.45]), const(48.0), 8)
    # S13 37.6-43.0 Hooch gets Neville up, "Nobody moves!", and leads him away
    mid = smoothed(lambda t: lerp(eye_fn(V, 'hooch')(t), eye_fn(V, 'neville')(t), 0.5), 10.0)
    pf = move([-8.4, 1.45, 33.4], [-9.2, 1.5, 34.6], 752, sc.L)
    sc.shot(752, sc.L, pf, mid, ramp(50.0, 47.0, 752, sc.L), 6)
    eye = lambda aid: (lambda sc_, t: eye_fn(sc_, aid)(t))
    sc.framing += [(130, 198, eye('hooch'), 'Hooch (S2)'), (280, 330, eye('viewer'), 'the boy (S4)'),
                   (334, 386, eye('hooch'), 'Hooch close (S5)'), (430, 470, eye(SMACK), 'the smacked boy (S7)'),
                   (494, 512, lambda sc_, t: broom_point(sc_, 'broom_viewer', t, [0.0, 0.0, 0.0]), 'his broom (S8)'),
                   (505, 530, lambda sc_, t: hand(sc_, E.actor_by_id(sc_, 'viewer'), 'right_arm', t), 'his hand (S8)'),
                   (560, 586, lambda sc_, t: hand(sc_, E.actor_by_id(sc_, 'hooch'), 'right_arm', t), 'the whistle (S9)'),
                   (596, 656, eye('neville'), 'Neville aloft (S10)'), (662, 690, eye('neville'), 'Neville falls (S11)'),
                   (716, 750, lambda sc_, t: E.ball_pos(sc_, 'remembrall', t), 'the Remembrall (S12)'),
                   (756, 850, eye('neville'), 'Neville led away (S13)')]


def check_walkers(sc, scene, problems, step=2):
    """People on their feet: no body point (feet, middle, head, the corners of the torso) inside a block, and no two
    standing people closer than 0.6 while one of them walks. Riders are the fliers' check; parked actors (under
    the set) and unseen ones are skipped. Returns the least gap seen."""
    w = sc.world
    people = [a for a in scene['actors'] if a.get('type') == 'player']
    least = 9.0
    for t in range(0, scene['length'] + 1, step):
        here = {}
        for a in people:
            if E.ride_of(a) is not None and E.rides_at(a, t):
                continue
            p = E.own_sample(a, 'path', t) if isinstance(a.get('ride'), dict) else E.sample_path(a['path'], t)
            if p is None or p[1] < -5 or a['id'] in sc.unseen_at(t):
                continue
            here[a['id']] = (p, a.get('scale', 1.0), a)
        for aid, (p, s, a) in here.items():
            for dy in (0.06, 0.9 * s, 1.7 * s):
                for dx, dz in ((0, 0), (0.17 * s, 0.17 * s), (-0.17 * s, -0.17 * s), (0.17 * s, -0.17 * s),
                               (-0.17 * s, 0.17 * s)):
                    q = [p[0] + dx, p[1] + dy, p[2] + dz]
                    hit = E.shape_hit(w, q)
                    if hit and E.bid_of(hit) not in PLANTS:
                        problems.append('%s t %d: %s body point %s in %s' % (sc.sid, t, aid, r3(q), E.bid_of(hit)))
                        return least
        ids = sorted(here)
        for i in range(len(ids)):
            for j in range(i + 1, len(ids)):
                pa, pb = here[ids[i]][0], here[ids[j]][0]
                if abs(pa[1] - pb[1]) > 1.6:
                    continue
                d = math.hypot(pa[0] - pb[0], pa[2] - pb[2])
                least = min(least, d)
                if d < 0.6:
                    problems.append('%s t %d: %s and %s stand %.2f apart' % (sc.sid, t, ids[i], ids[j], d))
                    return least
    return least


def check_framing(sc, scene, problems):
    """The film's own asserts: each moment the shot list promises is in the 16:9 frame (the subject's point inside
    the frustum, past the near plane). Returns the number of points checked."""
    n = 0
    for t0, t1, fn, label in sc.framing:
        for t in range(int(t0), int(t1) + 1, 2):
            p = fn(scene, t)
            c = E.engine_cam(scene['camera'], t)
            lk, fov = E.engine_look(scene, scene['camera'], t)
            n += 1
            if not E.in_view(c, lk, fov, p, aspect=16 / 9.0):
                problems.append('%s: %s is out of frame at t %d' % (sc.sid, label, t))
                break
    return n


# ================================================================ the chase: flying_<h>_chase
L2 = 40.5
T2 = {'turn': (1.0, 1.6), 'walk': (1.6, 2.6), 'bend': (2.6, 3.2), 'pick': 3.2, 'look': 3.9, 'back': (6.2, 7.4),
      'm_mount': 9.0, 'm_up': (10.0, 12.2), 'come': 12.5, 'v_off': 14.4, 'flee': 16.6, 'cornered': 24.4,
      'fetch': 24.5, 'throw': 26.4, 'reach': (26.5, 29.0), 'catch': 29.6, 'land': 33.0, 'mcg_in': 28.6,
      'mcg_turn': 34.4, 'mcg_gone': 35.0, 'mcg_door': 35.2, 'follow': 36.3}
DOOR = LA['hospital_exit']
MCG = LA['mcg_window']
REM_REST = [-6.4, E.ball_radius(REMEMBRALL, KID) + 0.002, 37.3]   # where the lesson leaves the Remembrall

# Malfoy's flight (seconds, seat point): from his hover over the west row away north into chase_box, round in a
# climbing U-turn to a stand-off facing back south; the boy's, a beat behind on the inside of his line.
MALFOY_FLIGHT = [(16.6, [-6.5, 7.2, 34.0]), (17.6, [-6.8, 8.4, 29.4]), (18.6, [-5.6, 10.4, 21.8]),
                 (19.6, [-2.4, 12.4, 12.8]), (20.6, [2.6, 14.0, 3.6]), (21.6, [7.4, 15.4, -5.2]),
                 (22.6, [10.2, 16.4, -13.4]), (23.4, [7.4, 17.0, -18.6]), (24.0, [3.2, 17.0, -18.3]),
                 (24.3, [1.9, 16.95, -17.3]), (24.6, [1.4, 16.9, -16.8]), (24.9, [1.25, 16.9, -16.65])]
VIEWER_CHASE = [(16.6, [-1.0, 6.8, 31.2]), (17.6, [-3.6, 8.0, 27.6]), (18.6, [-3.4, 9.8, 21.4]),
                (19.6, [-0.8, 11.6, 13.6]), (20.6, [3.2, 13.2, 5.6]), (21.6, [7.0, 14.6, -2.2]),
                (22.6, [8.6, 15.6, -8.0]), (23.4, [6.4, 16.1, -11.0]), (23.8, [4.6, 16.25, -11.1]),
                (24.1, [3.6, 16.3, -11.0]), (24.4, [3.1, 16.3, -10.85]), (24.8, [3.0, 16.3, -10.8])]
# the dive: at the throw he wheels off the stand-off into the Remembrall's own line by FP0 and runs down it,
# DIVE_LAG ticks of its travel behind it, closing to nothing at the catch (its flight under REM_G, a light glass
# ball hurled down at the lawn); then the pull-out two blocks over the grass and the glide down the aisle
FP0, DIVE_LAG, REM_G = 560, 7.0, 0.002
V_LAND = (660, [0.0, 0.0, 33.2], -4.0)          # tick, his feet in the aisle, the broom's yaw
PARK_MCG = [-30.0, -30.0, 40.0]
REACH_LOCAL = [-0.17, 0.42, 0.68]               # the catching hand, forward down the broom (broom frame)
RAISE_LOCAL = [-0.21, 0.77, 0.24]               # held up high


def ball_world(scene, bid, t):
    """A ball's centre in the world at tick t: its own path, or the broom frame it rides in (rider_root)."""
    return E.rider_root(scene, E.actor_by_id(scene, bid), t)[0]


def final_state(scene, aid, t=None):
    a = E.actor_by_id(scene, aid)
    t = scene['length'] if t is None else t
    if E.is_broom(a):
        b = E.broom_pose(scene, a, t)
        return {'pos': list(E.track_sample(a['path'], t)), 'yaw': b['yaw'], 'pitch': b['pitch'], 'roll': b['roll']}
    root, m = E.rider_root(scene, a, t)
    yaw, pitch = (E.own_sample(a, 'look', t) or [0.0, 0.0, 0.0])[:2]
    return {'pos': root, 'yaw': yaw, 'pitch': pitch, 'riding': m is not None}


def flight_look(b, t0, t1, start_yaw, step=4):
    """Look keys for a flying broom from its own path: the heading and the climb (the mirror's), unwrapped, spline;
    the roll stays the auto-bank (a broom made with roll_keys False)."""
    prev = unwrap(start_yaw, b.yaw())
    b.hold_look(t0)
    for t in range(t0 + step, t1 + 1, step):
        hd = unwrap(E.broom_heading(b.path.k, t), prev)
        prev = hd
        b.turn(t, hd, E.broom_climb(b.path.k, t), 0.0, 'spline')
    return prev


def rel_look(scene, rider, t, target, max_pitch=60.0):
    """(yaw, pitch) of a broom rider's look (relative to the broom) toward a world point, the head turn clamped."""
    a = E.actor_by_id(scene, rider)
    v = E.broom_rider_at(scene, a, t)
    b = E.broom_pose(scene, v, t)
    root = E.rider_root(scene, a, t)[0]
    eye = [root[0], root[1] + 1.2 * a.get('scale', 1.0), root[2]]
    d = sub(target, eye)
    yw = unwrap(yaw_to(d[0], d[2]) - b['yaw'], 0.0)
    yw = max(-E.HEAD_TURN + 3, min(E.HEAD_TURN - 3, yw))
    pi = -math.degrees(math.atan2(d[1], math.hypot(d[0], d[2]))) - b['pitch']
    return yw, max(-max_pitch, min(max_pitch, pi))


def aim_rider(sc, rider, keys, t0, t1, target_fn, step=6, max_pitch=60.0, gain=1.0):
    """Spline look keys (relative) for a rider from t0 to t1, his head on target_fn(t), a world point; at rest
    at t1. A hold key at t0 first (a spline run straight after a hold overshoots)."""
    V = view(sc)
    if keys and t0 > keys[-1]['t'] + 1e-9:
        keys.append({'t': t0, 'yaw': keys[-1]['yaw'], 'pitch': keys[-1]['pitch']})
    n = max(1, int(round((t1 - t0) / float(step))))
    for i in range(1, n + 1):
        t = t0 + (t1 - t0) * i / n
        yw, pi = rel_look(V, rider, t, target_fn(t), max_pitch)
        k = {'t': round(t, 3), 'yaw': round(yw * gain, 2), 'pitch': round(pi * gain, 2)}
        if i < n:
            k['ease'] = 'spline'
        keys.append(k)
    return keys


def world_look(sc, aid, keys, t0, t1, target_fn, step=12, body=None, span=None, max_pitch=40.0):
    """Spline look keys (world) for someone on his feet from t0 to t1 toward target_fn(t); the turn kept within
    `span` of `body` when given."""
    V = view(sc)
    a = E.actor_by_id(V, aid)
    if keys and t0 > keys[-1]['t'] + 1e-9:
        keys.append({'t': t0, 'yaw': keys[-1]['yaw'], 'pitch': keys[-1]['pitch']})
    n = max(1, int(round((t1 - t0) / float(step))))
    prev = keys[-1]['yaw']
    for i in range(1, n + 1):
        t = t0 + (t1 - t0) * i / n
        p = E.sample_path(a['path'], t) if not isinstance(a.get('ride'), dict) else E.own_sample(a, 'path', t)
        d = sub(target_fn(t), [p[0], p[1] + 1.2 * a.get('scale', 1.0), p[2]])
        yw = unwrap(yaw_to(d[0], d[2]), prev)
        if body is not None and span is not None:
            yw = body + max(-span, min(span, unwrap(yw, body) - body))
        prev = yw
        pi = -math.degrees(math.atan2(d[1], math.hypot(d[0], d[2])))
        k = {'t': round(t, 3), 'yaw': round(yw, 2), 'pitch': round(max(-max_pitch, min(max_pitch, pi)), 2)}
        if i < n:
            k['ease'] = 'spline'
        keys.append(k)
    return keys


def chase(h, world, lesson_scene):
    sc = Sc('flying_%s_chase' % h, 'The Remembrall', L2, world, h)
    people = lesson_people(h)
    P = {p['id']: p for p in people}
    LS = lesson_scene
    mounted = ('viewer', 'e1', 'e7', 'w3', 'w6')
    sc.notes['mounted'] = mounted
    # ---- the brooms where the lesson left them (Neville's has flown off for good)
    B = {}
    for p in people:
        if p['id'] == 'neville':
            continue
        f = final_state(LS, 'broom_' + p['id'])
        B[p['id']] = Broom(sc, 'broom_' + p['id'], f['pos'], f['yaw'], roll_keys=p['id'] not in ('viewer', 'malfoy'))
        if p['id'] not in mounted:
            sc.lying.append(('broom_' + p['id'], None))
    # ---- the class
    S, R, ACT = {}, {}, {}
    for p in people:
        aid = p['id']
        if aid == 'neville':
            continue
        a = student_actor(sc, p)
        S[aid], R[aid], ACT[aid] = a, Rig(), []
        f = final_state(LS, aid)
        if aid in mounted:
            a['ride'] = 'broom_' + aid
            a['path'] = [{'t': 0, 'pos': r4(E.rider_seat(KID))}]
            lf = E.own_sample(E.actor_by_id(LS, aid), 'look', LS['length'])
            a['look'] = [{'t': 0, 'yaw': round(lf[0], 2), 'pitch': round(lf[1], 2)}]
        else:
            a['path'] = Keys().at(0, f['pos']).k
            a['look'] = [{'t': 0, 'yaw': round(f['yaw'], 2), 'pitch': round(f['pitch'], 2)}]
    nev = sc.actor({'id': 'neville', 'type': 'player', 'scale': KID, 'skin': {'texture': P['neville']['skin']},
                    'items': {'chest': robes('gryffindor')}})
    hooch = sc.actor({'id': 'hooch', 'type': 'player', 'skin': {'texture': 'witch_05'}, 'items': {'mainhand': WHISTLE}})
    mcg = sc.actor({'id': 'mcgonagall', 'type': 'player', 'skin': {'texture': 'mcgonagall'}})
    chase_walkers(sc, LS, nev, hooch)
    chase_malfoy(sc, P, B, S, R, ACT)
    chase_viewer(sc, P, B, S, R, ACT)
    chase_remembrall(sc, S)
    chase_mcgonagall(sc, mcg)
    chase_class(sc, people, P, B, S, R, ACT)
    for aid, a in S.items():
        if ACT[aid]:
            a['actions'] = sorted(ACT[aid], key=lambda x: x['t'])
        refresh(a, R[aid])
    raise_steps_standing(view(sc), S['malfoy'])
    for b in B.values():
        b.sync()
    chase_camera(sc, P, B, S)
    sc.ev(0, 'effect', 'sky', at=r3(LA['lawn_centre']), style='day')
    sc.sfx(0, 'lawn_air', 0.55, loop=True)
    scene = sc.scene(LAWN_SET, FL.RADIUS, nxt='flying_%s_match' % h, fog_far=FL.FOG_FAR)
    sc.notes.update(lawn=True, moments=[check_moments_chase],
                    ball_paths=[('remembrall', [sc.notes['malfoy_pick'], sc.notes['throw'], sc.notes['catch']])],
                    dives=[('broom_viewer', 'viewer', 548, tk(T2['catch']) + 20, LAWN_Y)])
    return sc, scene


def chase_walkers(sc, LS, nev, hooch):
    """Hooch leads Neville on to the hospital wing and in: through the door by 12 s, out of sight in the dark
    vestibule from 14.5 (Hooch is away for the whole chase)."""
    door, inside = DOOR['door'], DOOR['inside']
    for a, side in ((hooch, 0.75), (nev, -0.75)):
        f = final_state(LS, a['id'])
        k = Keys().at(0, f['pos'])
        k.through([(60, [-16.4 + side * 0.83, 0.0, 47.0 + side * 0.55]),
                   (130, [-19.4 + side * 0.9, 0.0, 54.8 + side * 0.4]),
                   (200, [door[0] + 1.5, 0.0, door[2] + side]), (240, [door[0] - 2.0, 0.0, door[2] + side]),
                   (300, [inside[0] + 3.0, 0.0, inside[2] + side])])
        a['path'] = k.k
        lk = [{'t': 0, 'yaw': round(f['yaw'], 2), 'pitch': 6.0}]
        for t, y in path_yaws(k.k, 8, 296, 8, f['yaw']):
            lk.append({'t': t, 'yaw': round(y, 2), 'pitch': 6.0})
        a['look'] = lk
        sc.hidden.setdefault(a['id'], []).append((290, sc.L))
    sc.notes['hooch_away'] = 0
    nr = Rig()
    nr.r('right_arm').set(0, [-38, 0, 14], None)
    nr.r('left_arm').set(0, [-52, -18, -24], None)
    nr.r('head').set(0, [10, 0, 0], None)
    refresh(nev, nr)


def chase_malfoy(sc, P, B, S, R, ACT):
    p, b, a, rg = P['malfoy'], B['malfoy'], S['malfoy'], R['malfoy']
    spot = list(a['path'][0]['pos'])
    rest = REM_REST
    t_turn0, t_turn1 = tk(T2['turn'][0]), tk(T2['turn'][1])
    t_w0, t_w1 = tk(T2['walk'][0]), tk(T2['walk'][1])
    T_PICK = tk(T2['pick'])
    stand = [rest[0] + 0.6, 0.0, rest[2] + 0.12]          # facing west, the ball just ahead and to his right
    k = Keys().at(0, spot).hold(t_w0)
    k.through([(t_w1, stand)])
    y0 = a['look'][0]['yaw']
    west = unwrap(90.0, y0)
    look = [{'t': 0, 'yaw': y0, 'pitch': 5.0}, {'t': t_turn0, 'yaw': y0, 'pitch': 10.0},
            {'t': t_turn1, 'yaw': round(west, 2), 'pitch': 30.0}]
    a['path'] = k.k
    a['look'] = look
    # the bend: crouch, the torso pitched forward about the hips, the right hand down to the ball
    tb0, tb1 = tk(T2['bend'][0]), tk(T2['bend'][1])
    ACT['malfoy'] += [{'t': tb0, 'do': 'sneak'}, {'t': T_PICK + 8, 'do': 'stand'}]
    a['actions'] = list(ACT['malfoy'])
    bp = bend_pose(70.0)
    for part, (rot, off) in bp.items():
        if part != 'right_arm':
            rg.r(part).to(tb0, tb1, rot if part != 'head' else [-25, 0, 0])
        rg.o(part).to(tb0, tb1, off)
    refresh(a, rg)
    res = reach(sc, a, rg, 'right_arm', tb0, tb1, rest)
    assert res < 0.05, res
    rg.r('right_arm').to(T_PICK, T_PICK + 1, rg.r('right_arm').v())
    refresh(a, rg)
    sc.contacts.append(('malfoy: the Remembrall off the grass', 'malfoy', 'right_arm', T_PICK,
                        ('point', list(rest)), 'pick'))
    ACT['malfoy'].append({'t': T_PICK, 'do': 'hold', 'item': REMEMBRALL_ITEM})
    sc.notes['malfoy_pick'] = T_PICK
    # up again, holding it up to the light to look at it, then back to his broom
    tu1 = T_PICK + 14
    for part, (rot, off) in bp.items():
        if part != 'right_arm':
            rg.r(part).to(T_PICK + 2, tu1, [0, 0, 0])
        rg.o(part).to(T_PICK + 2, tu1, [0, 0, 0])
    refresh(a, rg)
    T_LOOK = tk(T2['look'])
    rg.r('right_arm').to(T_PICK + 2, T_LOOK, [-105, 0, 10])
    look += [{'t': T_PICK + 4, 'yaw': round(west, 2), 'pitch': 20.0},
             {'t': T_LOOK, 'yaw': round(west + 8, 2), 'pitch': -12.0}]
    tb_0, tb_1 = tk(T2['back'][0]), tk(T2['back'][1])
    k.hold(tb_0)
    k.through([(tb_1, spot)])
    east = unwrap(-90.0, west + 8)
    look += [{'t': tb_0 - 4, 'yaw': round(west + 8, 2), 'pitch': -5.0},
             {'t': tb_0 + 10, 'yaw': round(east, 2), 'pitch': 5.0}, {'t': tb_1, 'yaw': round(east, 2), 'pitch': 5.0}]
    rg.r('right_arm').to(tb_0 - 6, tb_0 + 6, [-40, 0, 8])
    sc.say(T_LOOK, 'flying_malfoy_look')
    # the mount (out of shot: the boy's close-up), the kick-off and the rise, backward, facing the class
    tm = tk(T2['m_mount'])
    under = broom_under(spot, p['broom_yaw'], KID)
    b.path.hold(tm - 10)
    b.hold_look(tm - 10)
    b.path.at(tm, under)
    b.turn(tm, p['broom_yaw'], 0.0)
    sc.flier_ok.setdefault(b.id, []).append((tm - 10, tm + 4))
    rg.r('right_arm').to(tm - 10, tm, [0, 0, 0])
    a['ride'] = {'on': b.id, 'from': tm}
    k.hold(tm - 0.001).at(tm, E.rider_seat(KID))
    look.append({'t': tm - 0.001, 'yaw': round(east, 2), 'pitch': 5.0})
    look.append({'t': tm, 'yaw': 0.0, 'pitch': 5.0})
    tu0, tu1_ = tk(T2['m_up'][0]), tk(T2['m_up'][1])
    hover = MALFOY_FLIGHT[0][1]
    b.path.hold(tu0)
    arc = lambda t: add(lerp(under, hover, smoother((t - tu0) / float(tu1_ - tu0))),
                        [0.0, 0.0, 0.0])
    b.path.run(tu0, tu1_, arc, 4)
    b.hold_look(tu0)
    b.turn(tu0 + 14, p['broom_yaw'] + 8, -12.0)
    b.turn(tu0 + 28, p['broom_yaw'] + 4, -8.0)
    b.turn(tu1_, p['broom_yaw'], 0.0)
    # the taunt: he holds it up over his head at the class
    T_COME = tk(T2['come'])
    rg.r('right_arm').to(tu1_ - 8, T_COME, [-135, 0, 12])
    rg.r('right_arm').to(T_COME + 36, T_COME + 46, [-70, 0, 10])
    look += [{'t': tu0, 'yaw': 0.0, 'pitch': 5.0}, {'t': tu1_, 'yaw': 0.0, 'pitch': 14.0},
             {'t': T_COME, 'yaw': 0.0, 'pitch': 4.0}]
    sc.say(T_COME, 'flying_malfoy_come')
    # away north as the boy comes up at him, through chase_box, round to face him
    T_FLEE = tk(T2['flee'])
    b.path.hold(T_FLEE)
    b.hold_look(T_FLEE)
    b.path.through([(tk(t), q) for t, q in MALFOY_FLIGHT[1:]])
    rg.r('right_arm').to(T_FLEE - 6, T_FLEE + 6, [0, 0, 0])
    T_CORNER = tk(T2['cornered'])
    sc.notes['malfoy'] = {'hover': (tu1_, T_FLEE), 'taunt_end': T_FLEE + 6, 'cornered': T_CORNER, 'mount': tm}
    last = flight_look(b, T_FLEE, T_CORNER - 8, b.yaw())
    vb, mb = VIEWER_CHASE[-1][1], MALFOY_FLIGHT[-1][1]
    face = unwrap(yaw_to(vb[0] - mb[0], vb[2] - mb[2]), last)
    b.turn(T_CORNER, face, 0.0)
    look += [{'t': T_FLEE, 'yaw': 0.0, 'pitch': 4.0}, {'t': T_FLEE + 12, 'yaw': 55.0, 'pitch': -8.0},
             {'t': T_FLEE + 26, 'yaw': 0.0, 'pitch': 5.0}, {'t': T_CORNER - 30, 'yaw': 0.0, 'pitch': 5.0},
             {'t': T_CORNER - 14, 'yaw': -60.0, 'pitch': 0.0}, {'t': T_CORNER, 'yaw': 0.0, 'pitch': 0.0}]
    # "Go on, then. Fetch!" and the throw, past the boy toward the lawn
    T_THROW = tk(T2['throw'])
    sc.say(tk(T2['fetch']), 'flying_malfoy_catch')
    rg.r('right_arm').to(T_CORNER + 2, T_CORNER + 14, [-125, 0, 12])          # holding it up, teasing
    rg.r('right_arm').to(T_THROW - 14, T_THROW - 5, [-175, 0, 22])            # the wind-up, back over the shoulder
    rg.r('right_arm').to(T_THROW - 5, T_THROW, [-95, 0, 6], 'in')             # over the top, the release
    rg.r('right_arm').to(T_THROW + 4, T_THROW + 16, [0, 0, 0])
    ACT['malfoy'].append({'t': T_THROW, 'do': 'hold', 'item': 'minecraft:air'})
    look += [{'t': T_THROW - 16, 'yaw': 0.0, 'pitch': 0.0}, {'t': T_THROW + 8, 'yaw': 20.0, 'pitch': 25.0},
             {'t': T_THROW + 40, 'yaw': 10.0, 'pitch': 30.0}]
    sc.notes['throw'] = T_THROW
    # then back down to the lawn, well out of the boy's way, landing west of the west row
    land = broom_under([-8.0, 0.0, 41.0], 0.0, KID)
    land[1] += 0.05
    b.path.hold(T_THROW + 18)
    b.hold_look(T_THROW + 18)
    b.path.through([(T_THROW + 60, [-3.0, 14.0, -12.0]), (T_THROW + 110, [-10.0, 9.0, 12.0]),
                    (T_THROW + 160, [-11.0, 3.0, 32.0]), (T_THROW + 200, land)])
    flight_look(b, T_THROW + 18, T_THROW + 188, b.yaw())
    b.turn(T_THROW + 200, unwrap(0.0, b.yaw()), 0.0)
    sc.flier_ok.setdefault(b.id, []).append((T_THROW + 180, sc.L))
    sc.speed_ok.setdefault(b.id, []).append((tu0, tu1_ + 2, 'the kick-off from the grass'))
    a['path'] = k.k
    a['look'] = sorted(look, key=lambda x: x['t'])
    refresh(a, rg)
    b.sync()


def hermite(p0, v0, p1, v1, t0, t1):
    """A cubic from p0 (velocity v0, blocks a tick) at t0 to p1 (v1) at t1."""
    dt = float(t1 - t0)

    def f(t):
        u = (t - t0) / dt
        h00, h10, h01, h11 = 2 * u ** 3 - 3 * u ** 2 + 1, u ** 3 - 2 * u ** 2 + u, -2 * u ** 3 + 3 * u ** 2, u ** 3 - u ** 2
        return [h00 * p0[i] + h10 * dt * v0[i] + h01 * p1[i] + h11 * dt * v1[i] for i in range(3)]
    return f


def parabola(p0, p1, t0, t1, g):
    """A ball from p0 at t0 to p1 at t1 under gravity g (blocks a tick squared)."""
    n = float(t1 - t0)
    v0 = [(p1[i] - p0[i]) / n for i in range(3)]
    v0[1] += 0.5 * g * n

    def f(t):
        s = t - t0
        return [p0[0] + v0[0] * s, p0[1] + v0[1] * s - 0.5 * g * s * s, p0[2] + v0[2] * s]
    return f


def chase_viewer(sc, P, B, S, R, ACT):
    """The boy: astride for the whole scene. He watches Malfoy, kicks off after him, follows him round, faces him;
    at the throw he wheels and plunges down the Remembrall's own line, closing on it (first person), takes it out of
    the air two blocks over the grass, pulls out and glides down the aisle between the two rows to land."""
    V = view(sc)
    p, b, a, rg = P['viewer'], B['viewer'], S['viewer'], R['viewer']
    start = list(b.path.last())
    T_OFF, T_CORNER, T_THROW = tk(T2['v_off']), tk(T2['cornered']), tk(T2['throw'])
    T_C = tk(T2['catch'])
    mal = eye_fn(V, 'malfoy')
    # the kick-off and the chase
    b.path.hold(T_OFF)
    b.hold_look(T_OFF)
    rise = [(T_OFF + 12, [start[0] - 0.5, start[1] + 1.0, start[2] + 0.5]),
            (T_OFF + 28, [1.6, 4.2, 30.4])] + [(tk(t), q) for t, q in VIEWER_CHASE]
    b.path.through(rise)
    b.turn(T_OFF + 6, b.yaw(), -25.0)
    last = flight_look(b, T_OFF + 6, T_CORNER - 8, b.yaw())
    mb = MALFOY_FLIGHT[-1][1]
    vs = VIEWER_CHASE[-1][1]
    face = unwrap(yaw_to(mb[0] - vs[0], mb[2] - vs[2]), last)
    b.turn(T_CORNER, face, 0.0)
    b.path.hold(T_THROW + 4)
    b.hold_look(T_THROW + 4)
    sc.notes['viewer_off'] = T_OFF
    base_path, base_look = copy.deepcopy(b.path.k), copy.deepcopy(b.look)
    # the dive and the catch, solved round the ball's flight: the ball's line ends in his hand, his broom runs
    # down that line DIVE_LAG ticks of the ball's travel behind it at FP0, closing to nothing at the catch
    p0 = hand(V, E.actor_by_id(V, 'malfoy'), 'right_arm', T_THROW)
    t1 = T_C - 0.25
    C = [-0.6, 4.0, 12.0]
    Hoff = [0.0, 0.0, 0.7]
    r0, r1 = tk(T2['reach'][0]), tk(T2['reach'][1])
    land_at, feet, land_yaw = V_LAND
    for _ in range(4):
        b.path.k[:] = copy.deepcopy(base_path)
        b.look[:] = copy.deepcopy(base_look)
        fly = parabola(p0, C, T_THROW, T_C, REM_G)
        lag = lambda t: DIVE_LAG * (T_C - t) / float(T_C - FP0)
        line = lambda t: sub(fly(t - lag(t)), Hoff)
        vel = lambda t: [(line(t)[i] - line(t - 1.0)[i]) for i in range(3)]
        # off the stand-off (at rest) into the line by FP0
        entry = hermite(vs, [0.0, 0.0, 0.0], line(FP0), vel(FP0), T_THROW + 4, FP0)
        for t in range(T_THROW + 8, FP0, 4):
            b.path.at(t, entry(t), 'spline')
        for t in range(FP0, T_C + 1, 4):
            b.path.at(t, line(t), 'spline')
        # on down the line a beat past the catch, out of the dive just over the grass, then down the aisle
        vc = vel(T_C)
        ext = lambda t: add(line(T_C), vc, t - T_C)
        T_P = T_C + 4
        for t in (T_C + 2, T_P):
            b.path.at(t, ext(t), 'spline')
        hz = norm([vc[0], 0.0, vc[2]])
        bottom = [ext(T_P)[0] + hz[0] * 10.5, 1.48, ext(T_P)[2] + hz[2] * 10.5]
        pull = hermite(ext(T_P), vc, bottom, [hz[0] * 0.5, 0.0, hz[2] * 0.5], T_P, T_P + 20)
        for t in range(T_P + 4, T_P + 21, 4):
            b.path.at(t, pull(t), 'spline')
        lp = broom_under(feet, land_yaw, KID)
        lp[1] += 0.05
        glide = hermite(bottom, [hz[0] * 0.5, 0.0, hz[2] * 0.5], lp, [0.0, 0.0, 0.0], T_P + 20, land_at)
        ts = list(range(T_P + 24, land_at - 3, 4))
        for t in ts:
            b.path.at(t, glide(t), 'spline')
        b.path.at(land_at, lp)
        hd = unwrap(E.broom_heading(b.path.k, T_THROW + 16), face)
        b.turn(T_THROW + 16, hd, 25.0)
        last = flight_look(b, T_THROW + 16, land_at - 12, hd)
        b.turn(land_at, unwrap(land_yaw, last), 0.0)
        b.sync()
        # his right hand off the handle, forward into the ball's line; held up high once he has it
        rg.rot.pop('right_arm', None)
        refresh(a, rg)
        res = reach(sc, a, rg, 'right_arm', r0, r1, broom_point(V, b.id, r1, REACH_LOCAL))
        assert res < 0.05, res
        hc = hand(V, a, 'right_arm', T_C)
        Hoff = sub(hc, E.track_sample(b.path.k, T_C))
        C = list(hc)
    res2 = reach(sc, a, rg, 'right_arm', T_C + 6, T_C + 40, broom_point(V, b.id, T_C + 40, RAISE_LOCAL))
    assert res2 < 0.06, res2
    rg.r('right_arm').to(tk(T2['follow']) + 10, tk(T2['follow']) + 36, [-40, 0, 10])
    refresh(a, rg)
    ACT['viewer'].append({'t': T_C, 'do': 'hold', 'item': REMEMBRALL_ITEM})
    sc.flier_ok.setdefault(b.id, []).append((land_at - 16, sc.L))
    sc.notes['dive'] = (T_THROW, land_at)
    sc.notes['catch'] = T_C
    sc.notes['catch_hand'] = hand(V, a, 'right_arm', T_C)
    sc.notes['remembrall_fly'] = parabola(p0, sc.notes['catch_hand'], T_THROW, T_C, REM_G)
    # his head: on Malfoy until the throw, the ball as it goes, still in his own eyes, his prize, then McGonagall
    look = a['look']
    look[:] = look[:1]
    aim_rider(sc, 'viewer', look, 20, T_OFF, mal, step=12, gain=0.9)
    aim_rider(sc, 'viewer', look, T_OFF + 6, T_THROW, mal, step=8, gain=0.8)
    fly = sc.notes['remembrall_fly']
    aim_rider(sc, 'viewer', look, T_THROW + 2, FP0 - 10, lambda t: fly(min(t, t1)), step=6, max_pitch=70.0)
    look.append({'t': FP0 - 4, 'yaw': -4.0, 'pitch': 8.0})
    look.append({'t': T_C + 12, 'yaw': -4.0, 'pitch': 8.0})
    aim_rider(sc, 'viewer', look, T_C + 12, tk(T2['land']), lambda t: hand(view(sc), E.actor_by_id(view(sc), 'viewer'),
                                                                         'right_arm', t), step=10, max_pitch=70.0)
    door = [DOOR['door'][0] + 1.3, 1.6, DOOR['door'][2] - 0.7]
    aim_rider(sc, 'viewer', look, tk(T2['follow']) - 4, tk(T2['follow']) + 16, const(door), step=10)


def chase_remembrall(sc, S):
    """The Remembrall: on the grass where the lesson left it until Malfoy's hand closes on it (the item from that
    tick), out of the world until it leaves his hand at the throw, its flight from his hand to the boy's, out of the
    world again from the catch (the item in the boy's hand)."""
    T_PICK, T_THROW, T_C = sc.notes['malfoy_pick'], sc.notes['throw'], sc.notes['catch']
    fly = sc.notes['remembrall_fly']
    t1 = T_C - 0.25
    k = Keys().at(0, REM_REST).hold(T_PICK - 0.25)
    k.at(T_PICK, PARK_LAWN, 'linear')
    k.hold(T_THROW - 0.25)
    k.at(T_THROW, fly(T_THROW))
    for t in range(T_THROW + 3, int(t1) - 1, 3):
        k.at(t, fly(t), 'spline')
    k.at(t1, fly(T_C), 'linear')           # in the hand a quarter tick early; linear: the run keeps its speed
    k.at(T_C, PARK_LAWN, 'linear')
    sc.actor({'id': 'remembrall', 'type': REMEMBRALL, 'scale': KID, 'path': k.k})
    sc.notes['remembrall'] = {'pick': T_PICK, 'throw': T_THROW, 'catch': T_C, 'fly': fly, 'g': REM_G}
    sc.contacts.append(('malfoy: the throw leaves his hand', 'malfoy', 'right_arm', T_THROW,
                        ('ball', 'remembrall', T_THROW), 'throw'))
    sc.contacts.append(('viewer: the Remembrall into his hand', 'viewer', 'right_arm', T_C,
                        ('ball', 'remembrall', t1), 'catch'))
    sc.sfx(T_C, 'quaffle_catch', 0.55, hit=T_C, label='the Remembrall caught')


def chase_mcgonagall(sc, mcg):
    """McGonagall: at her window from mcg_in, watching the boy, turning away into the study at mcg_turn (gone at
    mcg_gone); out of the hospital wing door from mcg_door, calling him over. Off stage otherwise."""
    st = MCG['stand']
    t_in, t_turn, t_gone = tk(T2['mcg_in']), tk(T2['mcg_turn']), tk(T2['mcg_gone'])
    t_door, t_call = tk(T2['mcg_door']), tk(T2['follow'])
    k = Keys().at(0, PARK_MCG).hold(t_in - 0.25)
    k.at(t_in, st)
    k.hold(t_turn + 6)
    k.through([(t_gone, [st[0] - 1.7, st[1], st[2] + 0.2])])
    k.at(t_gone + 0.25, PARK_MCG, 'linear')
    k.hold(t_door - 0.25)
    inside = [DOOR['door'][0] - 2.4, 0.0, DOOR['door'][2]]
    out = [DOOR['door'][0] + 1.3, 0.0, DOOR['door'][2] - 0.7]
    k.at(t_door, inside)
    k.through([(t_call - 4, out)])
    mcg['path'] = k.k
    sc.hidden['mcgonagall'] = [(0, t_in - 1), (t_gone - 2, t_door + 2)]
    V = view(sc)
    boy = eye_fn(V, 'viewer')
    look = [{'t': 0, 'yaw': -90.0, 'pitch': 20.0}, {'t': t_in, 'yaw': -90.0, 'pitch': 20.0}]
    world_look(sc, 'mcgonagall', look, t_in + 4, t_turn, boy, step=10, max_pitch=40.0)
    look += [{'t': t_turn + 10, 'yaw': unwrap(90.0, look[-1]['yaw']), 'pitch': 0.0}]
    look += [{'t': t_gone + 0.25, 'yaw': look[-1]['yaw'], 'pitch': 0.0}]
    yb = unwrap(-90.0, look[-1]['yaw'])
    look += [{'t': t_door, 'yaw': yb, 'pitch': 0.0}]
    world_look(sc, 'mcgonagall', look, t_call - 4, sc.L, boy, step=14, max_pitch=20.0)
    mcg['look'] = look
    mcg['anims'] = [{'t': t_in, 'name': 'HANDS_CLASPED', 'blend': 8}, {'t': t_door, 'name': 'IDLE', 'blend': 4},
                    {'t': t_call, 'name': 'TALK', 'blend': 6},
                    {'t': t_call + tk(float(CLIP['flying_mcg_follow'])), 'name': 'HANDS_CLASPED', 'blend': 10}]
    sc.say(t_call, 'flying_mcg_follow')


def chase_class(sc, people, P, B, S, R, ACT):
    """The rest of the class keep their feet on the grass (Hooch said so): heads and bodies turn with the chase;
    the boy's own house cheer the catch; the ones astride turn their heads only."""
    V = view(sc)
    mal, boy = eye_fn(V, 'malfoy'), eye_fn(V, 'viewer')
    T_C = sc.notes['catch']
    door = [DOOR['door'][0] + 1.3, 1.6, DOOR['door'][2] - 0.7]

    def action(t):
        if t < 290:
            return mal(t)
        if t < 560:
            return lerp(mal(t), boy(t), smooth((t - 290) / 120.0))
        if t < 716:
            return boy(t)
        return door
    for p in people:
        aid = p['id']
        if aid in ('viewer', 'malfoy', 'neville'):
            continue
        a = S[aid]
        rnd = random.Random('chase' + aid)
        if aid in sc.notes['mounted']:
            aim_rider(sc, aid, a['look'], 12 + rnd.randint(0, 10), sc.L - 10, action, step=14, gain=0.85)
            continue
        look = a['look']
        world_look(sc, aid, look, 14 + rnd.randint(0, 12), sc.L - 8, action, step=14 + rnd.randint(0, 4))
        if p['house'] == sc.h and aid.startswith('e'):
            t0 = T_C + 10 + rnd.randint(0, 14)
            a['anims'] = [{'t': t0, 'name': 'CHEER' if rnd.random() < 0.5 else 'CLAP', 'blend': 6},
                          {'t': tk(T2['mcg_door']) + rnd.randint(0, 10), 'name': 'IDLE', 'blend': 10}]


def chase_camera(sc, P, B, S):
    V = view(sc)
    mal, boy = eye_fn(V, 'malfoy'), eye_fn(V, 'viewer')
    T_THROW, T_C = sc.notes['throw'], sc.notes['catch']
    rem = sc.notes['remembrall']['fly']
    ball = lambda t: ball_world(V, 'remembrall', t)
    # C1 0-3.4 low in the grass behind the Remembrall: Malfoy turns, sees it, comes over and bends for it
    pf = move([-8.9, 0.34, 36.85], [-8.7, 0.36, 37.05], 0, 68)
    mid = smoothed(lambda t: lerp(REM_REST, mal(t), 0.55), 6.0)
    sc.shot(0, 68, pf, mid, ramp(52.0, 48.0, 0, 68), 6)
    # C2 3.4-6.4 Malfoy, medium close: he holds it up to the light ("Look what the clumsy idiot dropped")
    m_hand = lambda t: hand(V, E.actor_by_id(V, 'malfoy'), 'right_arm', t)
    pf = move([-7.55, 1.0, 36.55], [-7.4, 1.02, 36.75], 68, 128)
    sc.shot(68, 128, pf, smoothed(lambda t: lerp(mal(t), m_hand(t), 0.35), 5.0), const(44.0), 6)
    # C3 6.4-9.3 the boy astride his broom, watching him (Malfoy mounts out of shot)
    pf = move([2.35, 0.92, 27.95], [2.45, 0.93, 28.1], 128, 186)
    sc.shot(128, 186, pf, smoothed(boy, 4.0), ramp(44.0, 41.0, 128, 186), 6)
    # C4 9.3-12.2 Malfoy kicks off and rises, from low by the west row
    pf = move([-1.3, 0.55, 32.0], [-1.55, 0.75, 32.5], 186, 244)
    sc.shot(186, 244, pf, smoothed(mal, 6.0), ramp(58.0, 62.0, 186, 244), 6)
    # C5 12.2-14.5 Malfoy hovering, the Remembrall held up ("Want it? Come up and take it, then.")
    pf = move([-3.1, 6.35, 33.0], [-3.3, 6.5, 33.3], 244, 290)
    sc.shot(244, 290, pf, smoothed(lambda t: lerp(mal(t), m_hand(t), 0.3), 5.0), const(48.0), 6)
    # C6 14.5-16.8 the boy kicks off: from the grass in the aisle he shoots up over the camera
    pf = move([1.5, 0.45, 26.8], [1.45, 0.5, 26.9], 290, 336)
    sc.shot(290, 336, pf, smoothed(boy, 4.0), ramp(66.0, 72.0, 290, 336), 4)
    # C7 16.8-21.0 the chase: behind and above the boy's right shoulder, Malfoy ahead, the castle beyond
    sb = smoothed(boy, 20.0)
    dirf = smoothed(lambda t: norm(sub(sb(t + 4), sb(t - 4))), 16.0)

    def chase_cam(t):
        d = dirf(t)
        hx, hz = norm([d[0], 0.0, d[2]])[0], norm([d[0], 0.0, d[2]])[2]
        return add(sb(t), [-hx * 3.4 - hz * 1.1, 1.0, -hz * 3.4 + hx * 1.1])
    sc.shot(336, 420, chase_cam, smoothed(lambda t: lerp(boy(t), mal(t), 0.6), 8.0), const(68.0), 4)
    # C8 21.0-24.4 wide from high in the east: both bank round the U-turn, the corner tower beyond
    pf = move([27.0, 22.0, -5.0], [26.0, 21.5, -8.5], 420, 488)
    sc.shot(420, 488, pf, smoothed(lambda t: lerp(boy(t), mal(t), 0.5), 10.0), ramp(46.0, 42.0, 420, 488), 6)
    # C9 24.4-26.6 over the boy's shoulder at Malfoy: "Go on, then. Fetch!" and the throw
    vb = VIEWER_CHASE[-1][1]
    pf = move([vb[0] + 0.95, vb[1] + 1.35, vb[2] + 1.75], [vb[0] + 0.9, vb[1] + 1.3, vb[2] + 1.45], 488, 532)
    sc.shot(488, 532, pf, smoothed(lambda t: lerp(mal(t), m_hand(t), 0.25), 5.0), ramp(44.0, 40.0, 488, 532), 6)
    # C10 26.6-28.8 the dive: from the grass under it, the ball arcing over, the boy plunging after it
    pf = move([-5.2, 1.05, 16.5], [-5.0, 0.95, 16.0], 532, FP0)
    sc.shot(532, FP0, pf, smoothed(lambda t: lerp(boy(t), rem(min(t, T_C - 1)), 0.45), 5.0), ramp(62.0, 66.0, 532, FP0), 4)
    # C11 28.8-30.8 FIRST PERSON: racing level over the grass, it drops out of the sky into his hand
    v_hand = lambda t: hand(V, E.actor_by_id(V, 'viewer'), 'right_arm', t)
    vb_ = E.actor_by_id(V, 'broom_viewer')
    up_ahead = [-0.14, math.sin(math.radians(-30.0)), math.cos(math.radians(-30.0))]
    eye_v = lambda t: head_eye(V, E.actor_by_id(V, 'viewer'), t, 0.12)
    aim = smoothed(lambda t: add(eye_v(t), E._mat_vec(E.broom_pose(V, vb_, t)['m'], up_ahead), 4.0), 5.0)
    first_person(sc, V, 'viewer', FP0, T_C + 2, fwd=0.12, fov=80.0, look_fn=aim, sigma=1.5)
    # C12 30.8-32.8 over McGonagall's shoulder through her window: he comes down the aisle, the ball held high
    st = MCG['stand']
    pf = move([st[0] - 0.95, st[1] + 1.85, st[2] + 0.55], [st[0] - 0.9, st[1] + 1.82, st[2] + 0.5], T_C + 2, 650)
    sc.shot(T_C + 2, 650, pf, smoothed(boy, 8.0), ramp(44.0, 40.0, T_C + 2, 650), 6)
    # C13 32.8-34.8 her face through the glass from outside: she watches him land, then turns away
    pf = move([-19.6, 11.55, 15.35], [-19.75, 11.5, 15.6], 650, 696)
    mc = eye_fn(V, 'mcgonagall')
    sc.shot(650, 696, pf, smoothed(lambda t: mc(min(t, tk(T2['mcg_turn']) + 6)), 4.0), const(40.0), 6)
    # C14 34.8-36.2 the boy in the aisle with it held up, the class cheering round him
    land_at, feet, _ = V_LAND
    pf = move([feet[0] + 0.9, 0.85, feet[2] + 2.5], [feet[0] + 0.85, 0.87, feet[2] + 2.2], 696, 724)
    sc.shot(696, 724, pf, smoothed(boy, 4.0), const(50.0), 4)
    # C15 36.2-37.8 McGonagall in the hospital wing door: "You. With me. Now."
    pf = move([-15.0, 1.45, 58.4], [-15.3, 1.47, 58.8], 724, 756)
    sc.shot(724, 756, pf, smoothed(mc, 4.0), const(44.0), 4)
    # C16 37.8-40.5 the boy turns to look at her: a slow push in
    pf = move([feet[0] + 0.55, 1.02, feet[2] + 2.3], [feet[0] + 0.32, 1.1, feet[2] + 1.55], 756, sc.L)
    sc.shot(756, sc.L, pf, smoothed(boy, 5.0), ramp(48.0, 42.0, 756, sc.L), 6)
    # whooshes where a broom tears past the camera
    for bid, t0, t1 in (('broom_viewer', 290, 336), ('broom_viewer', 532, FP0)):
        whoosh(sc, bid, t0, t1)
    eye = lambda aid: (lambda sc_, t: eye_fn(sc_, aid)(t))
    sc.framing += [(40, 66, lambda sc_, t: REM_REST, 'the Remembrall in the grass (C1)'),
                   (70, 126, eye('malfoy'), 'Malfoy (C2)'), (130, 184, eye('viewer'), 'the boy (C3)'),
                   (190, 242, eye('malfoy'), 'Malfoy rising (C4)'), (246, 288, eye('malfoy'), 'Malfoy hovering (C5)'),
                   (292, 330, eye('viewer'), 'the boy kicks off (C6)'), (338, 418, eye('viewer'), 'the boy (C7)'),
                   (338, 418, eye('malfoy'), 'Malfoy ahead (C7)'), (422, 486, eye('viewer'), 'the boy (C8)'),
                   (422, 486, eye('malfoy'), 'Malfoy (C8)'), (490, 530, eye('malfoy'), 'Malfoy throws (C9)'),
                   (534, FP0 - 2, eye('viewer'), 'the dive (C10)'),
                   (FP0 + 2, T_C - 2, lambda sc_, t: ball_world(sc_, 'remembrall', t), 'the falling Remembrall (C11)'),
                   (T_C - 3, T_C + 1, lambda sc_, t: hand(sc_, E.actor_by_id(sc_, 'viewer'), 'right_arm', t), 'his hand (C11)'),
                   (600, 648, eye('viewer'), 'the boy below the window (C12)'), (652, 686, eye('mcgonagall'), 'McGonagall (C13)'),
                   (698, 722, eye('viewer'), 'the boy (C14)'), (728, 754, eye('mcgonagall'), 'McGonagall at the door (C15)'),
                   (758, sc.L - 2, eye('viewer'), 'the boy (C16)')]


def raise_steps_standing(scene, a, part='right_arm'):
    """item_raise_steps for the swaps a standing actor makes: on a broom the rider layer replaces the arm pose, the
    held-item raise included, so a swap in the saddle needs (and must get) no step."""
    keep = [x for x in a.get('actions', []) if not (x['do'] in ('hold', 'equip', 'use_item')
                                                    and E.broom_rider_at(scene, a, x['t']) is not None)]
    return E.item_raise_steps(dict(a, actions=keep), part)


def whoosh(sc, bid, t0, t1, volume=0.9):
    """A broom_whoosh where broom bid passes the camera closest between t0 and t1 (assert 7: within a tick)."""
    V = view(sc)
    b = E.actor_by_id(V, bid)
    best = min(range(t0, t1 + 1), key=lambda t: math.dist(E.engine_cam(sc.camera, t), E.track_sample(b['path'], t)))
    sc.sfx(best, 'broom_whoosh', volume, hit=best, label='%s passes the camera' % bid)
    sc.notes.setdefault('passes', []).append((bid, t0, t1, best))
    return best


# ================================================================ more checks, and the whole battery
def check_sfx_hits(sc, scene, problems):
    """Assert 7, sounds: every crack, catch, whistle and whoosh cue lands within a tick of its contact or pass;
    each whoosh's pass is found again from the emitted JSON (the broom's closest approach to the camera)."""
    worst = 0
    for name, t, hit, label in sc.sfx_hits:
        worst = max(worst, abs(t - hit))
        if abs(t - hit) > 1:
            problems.append('%s: the %s cue at t %s is %d ticks off its moment (%s)' % (sc.sid, name, t, t - hit, label))
    for bid, t0, t1, best in sc.notes.get('passes', []):
        b = E.actor_by_id(scene, bid)
        again = min(range(t0, t1 + 1), key=lambda t: math.dist(E.engine_cam(scene['camera'], t),
                                                                E.track_sample(b['path'], t)))
        cue = [e for e in scene['events'] if e['id'] == SFX + 'broom_whoosh' and abs(e['t'] - again) <= 1]
        if not cue:
            problems.append('%s: %s passes the camera at t %d with no whoosh' % (sc.sid, bid, again))
    return worst


def check_ball_path(sc, scene, problems, bid, contacts):
    """The Remembrall (or any ball): its world path is continuous (under 1.2 a tick) everywhere it is on stage, and it
    jumps on or off stage only at the listed contact ticks (a hand takes it, or lets it go)."""
    a = E.actor_by_id(scene, bid)
    if a is None:
        return 0.0
    worst = 0.0
    prev = ball_world(scene, bid, 0)
    for t4 in range(1, scene['length'] * 4 + 1):
        t = t4 / 4.0
        p = ball_world(scene, bid, t)
        d = math.dist(p, prev)
        if d > 0.3:                                   # 1.2 a tick
            if not any(abs(t - c) <= 0.5 for c in contacts):
                problems.append('%s: %s jumps %.2f at t %.2f, not at a contact' % (sc.sid, bid, d, t))
                return d
        else:
            worst = max(worst, d * 4)
        prev = p
    return worst


def lowest_leg(scene, a, t):
    lo = 99.0
    for part, rt, yaw, s, piv, rot in E.posed_parts(scene, a, t):
        if part in ('right_leg', 'left_leg'):
            for c in E.part_corners(rt, yaw, s, piv, rot, E.PART_BOX[part]):
                lo = min(lo, c[1])
    return lo


def check_dives(sc, scene, problems):
    """Assert 8, the dives: the lowest broom point at least 0.6 above the grass, the rider's leg boxes 0.1."""
    worst = (9.0, 9.0)
    for bid, rider, t0, t1, grass in sc.notes.get('dives', []):
        a = E.actor_by_id(scene, rider)
        for t in range(t0, t1 + 1):
            lb = E.broom_lowest(scene, bid, t) - grass
            ll = lowest_leg(scene, a, t) - grass
            worst = (min(worst[0], lb), min(worst[1], ll))
            if lb < 0.6 or ll < 0.1:
                problems.append('%s: in the dive at t %d broom %s is %.2f above the grass, the legs %.2f' % (
                    sc.sid, t, bid, lb, ll))
                break
    return worst


def check_moments_lesson(sc, scene, problems):
    """Assert 7 in the lesson: no classmate's broom stirs before his "Up!", Neville's flailing is over before he
    is thrown."""
    for aid, t_up in sc.notes['first_up'].items():
        b = E.actor_by_id(scene, 'broom_' + aid)
        p0, l0 = E.track_sample(b['path'], 0), E.own_sample(b, 'look', 0)
        for t in range(0, t_up):
            if math.dist(E.track_sample(b['path'], t), p0) > 1e-4 or max(abs(x - y) for x, y in zip(
                    E.own_sample(b, 'look', t), l0)) > 1e-3:
                problems.append('%s: %s broom stirs at t %d before his Up! (%d)' % (sc.sid, aid, t, t_up))
                break
    nv = sc.notes['neville']
    for part, d in E.actor_by_id(scene, 'neville').get('limbs', {}).items():
        if part in ('right_leg', 'left_leg'):
            if d['rot'][-1]['t'] > nv['fall']:
                problems.append('%s: Neville still flails at t %s after the fall' % (sc.sid, d['rot'][-1]['t']))
    if nv['flail_end'] > nv['fall']:
        problems.append('%s: the flailing ends after the fall' % sc.sid)


def check_moments_chase(sc, scene, problems):
    """Assert 7 in the chase: Malfoy's taunt and hover end when the boy reaches him; nobody on the lawn leaves the
    ground while Hooch is away but Malfoy and the boy."""
    m = sc.notes['malfoy']
    tf = m['hover'][1]
    mb, vb = E.actor_by_id(scene, 'broom_malfoy'), E.actor_by_id(scene, 'broom_viewer')
    d = math.dist(E.track_sample(mb['path'], tf), E.track_sample(vb['path'], tf))
    if d > 7.0:
        problems.append('%s: Malfoy flees at t %d with the boy still %.1f away' % (sc.sid, tf, d))
    arm = E.actor_by_id(scene, 'malfoy')['limbs']['right_arm']['rot']
    up = [k for k in arm if tf - 40 <= k['t'] <= tf + 30]
    if up and any(abs(c) > 1 for c in E.sample_keys(arm, m['taunt_end'])):
        problems.append('%s: Malfoy still holds it up at t %d' % (sc.sid, m['taunt_end']))
    hold = E.track_sample(mb['path'], m['hover'][0])
    if math.dist(E.track_sample(mb['path'], tf - 1), hold) > 0.05:
        problems.append('%s: Malfoy leaves his hover before t %d' % (sc.sid, tf))
    for a in scene['actors']:
        if a['id'] in ('viewer', 'malfoy', 'broom_viewer', 'broom_malfoy', 'remembrall', 'mcgonagall'):
            continue
        for t in range(0, scene['length'] + 1, 4):
            if E.is_broom(a):
                y = E.track_sample(a['path'], t)[1]
                bad = y > 1.0
            elif a.get('type') == 'player':
                y = E.rider_root(scene, a, t)[0][1]
                bad = y > 0.15
            else:
                break
            if bad:
                problems.append('%s: %s leaves the ground at t %d (y %.2f)' % (sc.sid, a['id'], t, y))
                break


_SOLID = {}


def solid_of(world):
    key = id(world)
    if key not in _SOLID:
        _SOLID[key] = frozenset(c for c, s in world.items() if E.bid_of(s) not in (PLANTS | set(FL.FREE)))
    return _SOLID[key]


def run_checks(sc, scene, world, skip=()):
    """Every assert on one scene: {name: worst number}, and 'problems'."""
    P = []
    out = {}
    if not hasattr(sc, 'cam_world'):
        sc.cam_world = cam_world(world)
    out['spline runs'] = E.check_spline_moves(sc.sid, scene, P)
    m = E.check_camera_smoothness(sc.sid, scene, P)
    out['smooth'] = 'a %.5f aw %.4f ja %.5f jw %.4f' % (max(s['a'] for s in m.values()), max(s['aw'] for s in m.values()),
                                                        max(s['ja'] for s in m.values()), max(s['jw'] for s in m.values()))
    out['air points'] = check_camera_air(sc, scene, P)
    cs = check_contacts(sc, scene, P)
    out['contacts'] = max([g for _, g in cs] or [0.0])
    out['contact list'] = ', '.join('%s %.3f' % (l.split(':')[0], g) for l, g in cs)
    out['items'] = check_items(sc, scene, P)
    out['rides'] = check_rides(sc, scene, P)
    out['fliers'] = check_fliers(sc, scene, P)
    out['dives'] = check_dives(sc, scene, P)
    if sc.lying:
        out['lying'] = check_lying(sc, scene, P)
    check_lines(sc, scene, P)
    check_keys(sc, scene, P)
    out['walkers'] = check_walkers(sc, scene, P)
    out['framing'] = check_framing(sc, scene, P)
    out['ball speed'] = E.check_ball_speeds(sc.sid, scene, P)
    out['sfx'] = check_sfx_hits(sc, scene, P)
    for fn in sc.notes.get('moments', []):
        fn(sc, scene, P)
    for bid, contacts in sc.notes.get('ball_paths', []):
        out['path ' + bid] = check_ball_path(sc, scene, P, bid, contacts)
    for name, fn in sc.notes.get('extra_checks', []):
        out[name] = fn(sc, scene, P)
    if 'clear' not in skip:
        out['clearance'] = check_clearance(sc, scene, P)
    if 'void' not in skip and sc.notes.get('lawn'):
        out['void'] = check_void(sc, scene, P, solid_of(world))
    out['problems'] = P
    return out
