"""The Flying memory's scenes: memories/flying_<h>.json, _chase, _match and _snitch for each house h.

    python -I build_scripts/pensieve_scenes/flying_scene.py           build, check and write the 16 scenes
    python -I build_scripts/pensieve_scenes/flying_scene.py --bite    show that every assert bites (writes nothing)

The chains differ only in robes, skins, crowd reactions and the rival (rival(h): Slytherin, or Gryffindor for a
Slytherin); the staging, timing and cameras are shared. Every assert runs on the emitted JSON through the
scene_engine mirrors, on every scene of every house, prints its worst number, and fails the run on any problem.

Shot list (seconds; ticks in the code)
--------------------------------------
flying_<h>, 43.0 s, the castle lawn under a day sky (opens on eyes_open)
  S1  0.0-6.4    establishing crane from high over the lawn, the castle on its crag, down to the class
  S2  6.4-10.0   Madam Hooch, medium, a slow push ("Good afternoon, class...")
  S3  10.0-13.8  dolly down the east row at hand height as the hands go out over the handles
  S4  13.8-16.6  the boy, front left, his hand over his broom
  S5  16.6-19.4  Hooch close ("Now, firmly, and mean it: say 'Up!'")
  S6  19.4-21.4  over his right shoulder, the west row beyond
  S7  21.4-24.2  the chorus across the aisle: brooms twitch and roll, one leaps and smacks a boy in the face
  S8  24.2-27.0  FIRST PERSON: his broom leaps into his hand on the first try
  S9  27.0-29.6  Hooch raises the whistle; the class mounts
  S10 29.6-33.0  Neville's broom lifts him, bolts and bucks ("It's going on its own!")
  S11 33.0-35.6  wide: he is thrown and falls; the broom shoots off over the wall
  S12 35.6-37.6  the Remembrall rolls out of the grass and comes to rest
  S13 37.6-43.0  "Nobody moves!" Hooch gets Neville up and leads him away
flying_<h>_chase, 40.5 s, the lawn
  C1  0.0-3.4    low in the grass behind the Remembrall: Malfoy turns, sees it, bends for it
  C2  3.4-6.4    Malfoy holds it up to the light ("Look what the clumsy idiot dropped.")
  C3  6.4-9.3    the boy astride his broom, watching (Malfoy mounts out of shot)
  C4  9.3-12.2   Malfoy kicks off and rises, from low by the west row
  C5  12.2-14.5  Malfoy hovering, holding it up ("Want it? Come up and take it, then.")
  C6  14.5-16.8  the boy kicks off and shoots up over the camera
  C7  16.8-21.0  the chase: behind and above the boy, Malfoy ahead, the castle beyond
  C8  21.0-24.4  wide from high in the east: both bank round a U-turn, the corner tower beyond
  C9  24.4-26.6  over the boy's shoulder: "Go on, then. Fetch!" and the throw
  C10 26.6-28.6  from the grass ahead: the ball arcing over, the boy plunging after it, pulling out low at us
  C11 28.6-29.7  FIRST PERSON: skimming the grass, it drops out of the sky into his hand
  C12 29.7-32.2  over McGonagall's shoulder through her window: he skims on and glides down the aisle
  C13 32.2-34.3  her face through the glass; she turns away
  C14 34.3-36.4  the boy landed in the aisle, the ball held up, his house cheering
  C15 36.4-38.5  McGonagall in the hospital wing door: "You. With me. Now."
  C16 38.5-40.5  the boy turns to her: a slow push in
flying_<h>_match, 50.0 s, the pitch beside Hogwarts under the storm (rain, lightning, 1,530 in the stands)
  M1  0.0-5.0    high over the west stand: from the castle lit through the rain down to the empty pitch
  M2  5.0-9.4    outside the stand the team waits astride; the captain to his Seeker ("Just catch the Snitch...")
  M3  9.4-13.4   low by the tunnel mouth: the team bursts out into the roar (the home stands on their feet)
  M4  13.4-17.0  wide from high in the east: both teams sweep round the stadium
  M5  17.0-21.0  under the two lines behind Hooch: "I want a clean game, from all of you."
  M6  21.0-23.4  the crate thrown open: the Bludgers and the Snitch burst out, the whistle, the Quaffle up
  M7a 23.4-27.4  alongside the Chasers racing up the pitch, passing ("And they're off!")
  M7b 27.4-29.2  behind the far middle hoop: the shot comes through the ring at the camera
  M8  29.2-32.0  the boy's house on its feet in the stand, roaring ("And it's in!")
  M10 32.0-34.5  the hunt: behind the boy high over the game and the storm (the stand chants)
  M9  34.5-36.6  behind a Beater: he cracks the Bludger away at a rival's head; the stadium gasps
  M11 36.6-41.0  the rival Seeker drifts up on the boy's shoulder
  M12 41.0-45.0  wide over the far stands: the crowd, the rain sheets, the game below, the lightning
  M13 45.0-50.0  under him, looking up past him into the storm; then his face, searching
flying_<h>_snitch, 40.0 s, the pitch (ends on eyes_close, end_action wake)
  N1  0.0-3.2    high behind the boy as he circles, hunting
  N2  3.2-5.2    the glint: low by the foot of the dive column, the Snitch hangs in the rain
  N3  5.2-7.2    his face: he has seen it ("The Seeker has seen something!") and swings away toward it
  N4  7.2-9.0    from the grass, straight up the column: he tips over and comes down at us, the rival after him
  N5  9.0-10.8   falling beside them: the two Seekers side by side, the grass rushing up
  N6  10.8-12.3  out of the dive just over the grass; the rival peels away above
  N7  12.3-14.7  FIRST PERSON: skimming the grass, the Snitch just ahead; his hand closes on it (the stands erupt)
  N7b 14.7-17.0  cut away: his house's stand on its feet, erupting in the rain
  N8  17.0-19.2  he slows over the grass and lifts it to the stands
  N9  19.2-23.0  his team swoops in round him
  N10 23.0-28.0  the crane up and out: the stands on their feet in the storm
  N11 28.0-34.0  closer, among them: the Snitch held high
  N12 34.0-40.0  his face, the rain on it, a slow push in as the eyes close
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


def quidditch_robes(h):
    return 'hogwartsmagic:%s_quidditch_robes' % h


SHIN_GUARDS = 'hogwartsmagic:quidditch_shin_guards'


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

# Music (flying_music.py cuts the user's tracks to these): each cue is the scene suffix it starts in and its time
# there in seconds. Each runs a second past its scene's end; the engine fades the outgoing cue over the hand-over's
# last 20 ticks while the next starts at 0, so they overlap by about 2 s. The hits land on Neville's broom bolting,
# the Remembrall catch and McGonagall's call, the team bursting into the stadium, the glint and the Snitch catch.
MUSIC_SLOTS = {
    'flying_lesson': ('', 2.0, 'Mr. Longbottom Flies: the lesson, its first hit on the bolting broom'),
    'flying_chase': ('_chase', 0.0, 'Mr. Longbottom Flies: the chase, the dive and the catch'),
    'flying_match': ('_match', 0.0, 'Quidditch, Third Year: the storm, the fly-out, the game, the hunt'),
    'flying_snitch': ('_snitch', 0.0, 'The Quidditch Match: the glint, the dive and the catch'),
}


def score(sc, suffix):
    """The scene's music cue from MUSIC_SLOTS (when its file is registered)."""
    for cue, (suf, at, _) in sorted(MUSIC_SLOTS.items()):
        if suf == suffix and 'pensieve.music.' + cue in REGISTERED:
            sc.ev(tk(at), 'music', 'hogwartsmagic:pensieve.music.' + cue, volume=1.0)


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

    def say(self, t, line, slot_end=None, volume=1.0, speaker=None):
        who, text = LINES[line]
        who = speaker or who
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
                aid, skin, hs = 'malfoy', 'malfoy_young', 'slytherin'
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
    hooch = sc.actor({'id': 'hooch', 'type': 'player', 'skin': {'texture': 'hooch'},
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
    score(sc, '')
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
    sc.notes.setdefault('yaw_ok', {})[b.id] = [(tc - 6, tc + 14, 'the broom spins round and smacks a boy')]
    sc.notes['smack'] = (tc, SMACK)
    sc.notes['first_up'][SMACK] = tk(T1['chorus'])
    sc.contacts.append(('%s: the broom tip into his face' % SMACK, SMACK, 'face', tc,
                        ('broom', b.id, [0.0, 0.0, 1.6]), 'smack'))
    sc.sfx(tc, 'thud', 0.85, hit=tc, label='the smack')
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
    ts = list(range(NEVILLE_FLIGHT[0][0] + 3, last + 1, 3))
    for t, hd in steady_yaws(b.path.k, ts, prev, t_start=NEVILLE_FLIGHT[0][0]):
        prev = hd
        cl = E.broom_climb(b.path.k, t)
        amp = (0.25 if t < 604 else 1.0 if t < 664 else max(0.0, 1.0 - (t - 664) / 70.0))
        ph = (t - 584) / 20.0
        pitch = cl + amp * (18.0 * math.sin(2 * math.pi * 2.6 * ph) + rnd.uniform(-4, 4))
        roll = amp * (32.0 * math.sin(2 * math.pi * 1.9 * ph + 0.7) + rnd.uniform(-6, 6))
        yj = amp * 14.0 * math.sin(2 * math.pi * 1.3 * ph + 1.9) * max(0.0, min(1.0, (672 - t) / 8.0))   # still after
        e = 'spline' if t + 3 <= last else None
        b.turn(t, hd + yj, max(-70.0, min(70.0, pitch)), roll, e)
    b.turn(last + 6, prev, 0.0, 0.0)
    sc.speed_ok[b.id] = [(NEVILLE_FLIGHT[0][0], last + 6, "Neville's broom bolts and bucks")]
    sc.sfx(tk(T1['bolt']), 'broom_buck', 0.85, loop=True, duration=68)        # the bucking, to the throw
    sc.notes.setdefault('yaw_ok', {})[b.id] = [(604, 672, "Neville's broom bucks")]
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
    sc.sfx(T_LAND, 'thud', 1.0, hit=T_LAND, label='Neville hits the grass')
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
        y = unwrap(E.broom_heading_held(keys, t), prev)
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
# the dive: at the throw he wheels off the stand-off and plunges, pulls out low by FP0 and skims the lawn after the
# falling Remembrall (its flight under REM_G, a light glass ball hurled down at the lawn), which drops into his hand
# about 1.9 over the grass (his raised hand); then on, and the glide down the aisle
FP0, REM_G = 572, 0.002
SKIM_Y, SKIM_SPEED, SKIM_AT = 1.25, 0.75, (-0.5, 13.3)     # the skim's seat height, speed at the catch, and (x, z) there
V_LAND = (660, [0.0, 0.0, 33.2], -4.0)          # tick, his feet in the aisle, the broom's yaw
PARK_MCG = [-30.0, -30.0, 40.0]
REACH_DIR = [-0.05, 1.3, 1.0]                   # the catching arm: up and out ahead, palm to the sky (broom frame)


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


YAW_RATE = 9.0           # degrees a tick: the most a look key turns a broom from the key before (a spline run's
                         # middle turns up to 1.5x its keys' rate, so the drawn yaw stays under YAW_RATE_LIMIT)
CRAB_LIMIT, CRAB_SPEED = 65.0, 0.25     # drawn yaw within CRAB_LIMIT degrees of the path heading at CRAB_SPEED blocks a tick or more
YAW_RATE_LIMIT = 15.0    # the drawn yaw rate assert (the spline between keys may overshoot the keys' own rate a little)


def horizontal_speed(keys, t):
    a, b = E.track_sample(keys, t - 2), E.track_sample(keys, t + 2)
    return math.hypot(b[0] - a[0], b[2] - a[2]) / 4.0


def steady_yaws(keys, ts, start_yaw, hover=(), pins=(), rate=YAW_RATE, t_start=None):
    """The yaw a broom faces at each tick of ts, from its own path: the held heading (E.broom_heading_held, which
    holds through a hover and ignores a spline creeping back) blended in by horizontal speed (0 at 0.03 blocks a
    tick, all of it from 0.15: in a straight dive or a slow drift the broom keeps the yaw it had); inside a `hover`
    window (t0, t1, yaw) it turns to that yaw as it slows; inside a `pins` window it faces that yaw whatever the path
    does (a keeper sliding side-on to the play). Each key turns at most `rate` degrees a tick from the last (the
    first from start_yaw at t_start, when given)."""
    out, prev, tp = [], start_yaw, t_start
    for t in ts:
        hd = unwrap(E.broom_heading_held(keys, t), prev)
        want = prev + (hd - prev) * smooth((horizontal_speed(keys, t) - 0.03) / 0.12)
        hv = next((y for t0, t1, y in hover if t0 <= t <= t1), None)
        if hv is not None:
            hy = unwrap(hv, prev)
            want = hy + (want - hy) * smooth((E.path_speed(keys, t) - 0.04) / 0.12)
        pin = next((y for t0, t1, y in pins if t0 <= t <= t1), None)
        if pin is not None:
            want = unwrap(pin, prev)
        if tp is not None:
            lim = rate * (t - tp)
            want = prev + max(-lim, min(lim, want - prev))
        prev, tp = want, t
        out.append((t, want))
    return out


def flight_look(b, t0, t1, start_yaw, step=4, pins=()):
    """Look keys for a flying broom from its own path: the steady heading (steady_yaws) and the climb (the
    mirror's), spline; the roll stays the auto-bank (a broom made with roll_keys False)."""
    prev = unwrap(start_yaw, b.yaw())
    b.hold_look(t0)
    for t, hd in steady_yaws(b.path.k, list(range(t0 + step, t1 + 1, step)), prev, pins=pins, t_start=t0):
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
    hooch = sc.actor({'id': 'hooch', 'type': 'player', 'skin': {'texture': 'hooch'}, 'items': {'mainhand': WHISTLE}})
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
    score(sc, '_chase')
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
    vb, mb = VIEWER_CHASE[-1][1], MALFOY_FLIGHT[-1][1]
    face = yaw_to(vb[0] - mb[0], vb[2] - mb[2])           # round to face the boy as he slows into the stand-off
    flight_look(b, T_FLEE, T_CORNER, b.yaw(), pins=[(T_CORNER - 10, T_CORNER, face)])
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
    at the throw he wheels and plunges, pulls out just over the grass and skims it after the falling Remembrall
    (first person), which drops into his hand; then on down the aisle between the two rows to land."""
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
    mb = MALFOY_FLIGHT[-1][1]
    vs = VIEWER_CHASE[-1][1]
    face = yaw_to(mb[0] - vs[0], mb[2] - vs[2])
    flight_look(b, T_OFF + 6, T_CORNER, b.yaw(), pins=[(T_CORNER - 10, T_CORNER, face)])
    b.path.hold(T_THROW + 1)
    b.hold_look(T_THROW + 1)
    sc.notes['viewer_off'] = T_OFF
    # the dive and the catch: he wheels off the stand-off and plunges almost straight down, nose over, while the
    # ball arcs out over him; he pulls out low and skims the lawn level at SKIM_Y, a little faster than the falling
    # ball, so it drops out of the sky into his outstretched hand about 1.9 over the grass; on past it, then
    # down the aisle between the rows to land
    p0 = hand(V, E.actor_by_id(V, 'malfoy'), 'right_arm', T_THROW)
    t1 = T_C - 0.25
    r0, r1 = tk(T2['reach'][0]), tk(T2['reach'][1])
    land_at, feet, land_yaw = V_LAND
    v_lev = [0.02, 0.0, SKIM_SPEED]
    T_LEVEL = FP0 - 4                                               # level, and steady, before his own eyes
    level = lambda t: add([SKIM_AT[0], LAWN_Y + SKIM_Y, SKIM_AT[1]], v_lev, t - T_C)
    # the pull-out starts ahead of the stand-off, so the plunge never runs back against the wheel
    p_pull, v_pull = [-0.4, LAWN_Y + 7.4, vs[2] + 1.4], [0.02, -0.5, 0.15]
    T_PULL, T_ON = T_LEVEL - 18, T_C + 8
    dive = hermite(vs, [0.0, 0.0, 0.0], p_pull, v_pull, T_THROW + 1, T_PULL)
    pull = hermite(p_pull, v_pull, level(T_LEVEL), v_lev, T_PULL, T_LEVEL)
    lp = broom_under(feet, land_yaw, KID)
    lp[1] += 0.05
    glide = hermite(level(T_ON), v_lev, lp, [0.0, 0.0, 0.0], T_ON, land_at)
    for t in range(T_THROW + 5, land_at - 3, 4):
        f = dive if t <= T_PULL else pull if t <= T_LEVEL else level if t <= T_ON else glide
        b.path.at(t, f(t), 'spline')
    b.path.at(land_at, lp)
    wheel = yaw_to(v_lev[0], v_lev[2])                              # round to face down the ball's line
    last = flight_look(b, T_THROW + 1, land_at - 12, b.yaw(), pins=[(T_THROW + 1, T_PULL + 4, wheel)])
    b.turn(land_at, unwrap(land_yaw, last), 0.0)
    b.sync()
    sc.speed_ok.setdefault(b.id, []).append((T_PULL - 2, T_LEVEL + 2, 'the pull-out over the grass'))
    # his right hand off the handle, forward and low into the ball's path; held up high once he has it
    res = reach(sc, a, rg, 'right_arm', r0, r1, arm_target(V, a, b.id, r1, REACH_DIR))
    assert res < 0.05, res
    res2 = reach(sc, a, rg, 'right_arm', T_C + 6, T_C + 40, arm_target(V, a, b.id, T_C + 40, [-0.3, 1.0, 0.35]))
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
    aim_rider(sc, 'viewer', look, T_THROW + 2, FP0 - 12, lambda t: fly(min(t, t1)), step=6, max_pitch=70.0)
    look.append({'t': FP0 - 2, 'yaw': 4.0, 'pitch': -6.0})         # still through his own eyes: up the ball's line
    look.append({'t': T_C + 2, 'yaw': 4.0, 'pitch': -6.0})
    look.append({'t': T_C + 12, 'yaw': -4.0, 'pitch': 12.0})
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
    # C10 26.6-28.6 from the grass ahead of him: the ball arcing over, the boy plunging after it and pulling out low,
    # straight at us
    pf = move([-4.4, 0.9, 8.0], [-4.2, 0.85, 7.4], 532, FP0)
    sc.shot(532, FP0, pf, smoothed(lambda t: lerp(boy(t), rem(min(t, T_C - 1)), 0.45), 5.0), ramp(64.0, 60.0, 532, FP0), 4)
    # C11 28.6-29.7 FIRST PERSON: skimming the grass, eyes on it as it drops out of the sky into his hand
    v_hand = lambda t: hand(V, E.actor_by_id(V, 'viewer'), 'right_arm', t)
    vb_ = E.actor_by_id(V, 'broom_viewer')
    eye_v = lambda t: head_eye(V, E.actor_by_id(V, 'viewer'), t, 0.12)

    # the camera does not chase the ball (it comes in steeply and meets the hand beside his face): it tilts slowly
    # from up the ball's line, as it falls toward him, down to his outstretched hand, both in frame throughout
    e0 = eye_v(FP0)
    q0 = sub(ball_world(V, 'remembrall', FP0), e0)
    hq = math.hypot(q0[0], q0[2])
    d0 = [q0[0] / hq, math.tan(math.radians(28.0)), q0[2] / hq]
    eC = eye_v(T_C)
    ahead_ = E._mat_vec(E.broom_pose(V, vb_, T_C)['m'], [0.0, 0.0, 1.0])
    d1 = slerp_dir(ahead_, sub(v_hand(T_C), eC), 0.62)
    hd1 = math.hypot(d1[0], d1[2])
    d1 = [d1[0] / hd1, math.tan(math.radians(-13.0)), d1[2] / hd1]
    tilt = lambda t: smoother((t - FP0 + 6) / float(T_C - FP0 + 14))     # the ease runs past both cuts: no jolt
    aim = lambda t: add(eye_v(t), slerp_dir(d0, d1, tilt(t)), 4.0)
    first_person(sc, V, 'viewer', FP0, T_C + 2, fwd=0.12, fov=84.0, look_fn=aim, sigma=3.0)
    # C12 29.7-32.2 over McGonagall's shoulder through her window: he comes down the aisle, the ball held high
    st = MCG['stand']
    pf = move([st[0] - 0.95, st[1] + 1.85, st[2] + 0.55], [st[0] - 0.9, st[1] + 1.82, st[2] + 0.5], T_C + 2, 644)
    sc.shot(T_C + 2, 644, pf, smoothed(boy, 8.0), ramp(44.0, 40.0, T_C + 2, 644), 6)
    # C13 32.2-34.3 her face through the glass from outside: she watches him land, then turns away
    pf = move([-19.6, 11.55, 15.35], [-19.75, 11.5, 15.6], 644, 686)
    mc = eye_fn(V, 'mcgonagall')
    sc.shot(644, 686, pf, smoothed(lambda t: mc(min(t, tk(T2['mcg_turn']) + 6)), 4.0), const(40.0), 6)
    # C14 34.3-36.4 the boy in the aisle with it held up, the class cheering round him
    land_at, feet, _ = V_LAND
    pf = move([feet[0] + 0.9, 0.85, feet[2] + 2.5], [feet[0] + 0.85, 0.87, feet[2] + 2.2], 686, 728)
    sc.shot(686, 728, pf, smoothed(boy, 4.0), const(50.0), 4)
    # C15 36.4-38.5 McGonagall in the hospital wing door: "You. With me. Now." (her line starts on the cut)
    pf = move([-15.0, 1.45, 58.4], [-15.3, 1.47, 58.8], 728, 770)
    sc.shot(728, 770, pf, smoothed(mc, 4.0), const(44.0), 4)
    # C16 38.5-40.5 the boy turns to look at her: a slow push in
    pf = move([feet[0] + 0.55, 1.02, feet[2] + 2.3], [feet[0] + 0.32, 1.1, feet[2] + 1.55], 770, sc.L)
    sc.shot(770, sc.L, pf, smoothed(boy, 5.0), ramp(48.0, 42.0, 770, sc.L), 6)
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
                   (T_C - 2, T_C + 1, lambda sc_, t: hand(sc_, E.actor_by_id(sc_, 'viewer'), 'right_arm', t), 'his hand (C11)'),
                   (600, 642, eye('viewer'), 'the boy below the window (C12)'), (646, 684, eye('mcgonagall'), 'McGonagall (C13)'),
                   (690, 726, eye('viewer'), 'the boy (C14)'), (732, 768, eye('mcgonagall'), 'McGonagall at the door (C15)'),
                   (774, sc.L - 2, eye('viewer'), 'the boy (C16)')]


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
        if d > 0.5:                                   # 2 a tick, the engine's own cut
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


def check_yaw_rates(sc, scene, problems):
    """No broom (and so no rider) turns faster than YAW_RATE_LIMIT degrees a tick: the drawn yaw (E.broom_pose, as
    BroomFlight.place draws it) every quarter tick, except the scripted windows by name (sc.notes['yaw_ok']).
    Also none flies sideways: wherever it moves 0.25 blocks a tick or faster, its drawn yaw is within 65 degrees of
    its path's heading (the Flier pins, the keepers', and the yaw_ok windows exempt). Returns (worst rate, broom, tick)."""
    worst = (0.0, None, None)
    ok = sc.notes.get('yaw_ok', {})
    crab_ok = sc.notes.get('crab_ok', {})
    for b in [a for a in scene['actors'] if E.is_broom(a)]:
        wins = ok.get(b['id'], [])
        prev = None
        for t in range(0, scene['length']):
            a0, a1 = E.track_sample(b['path'], t), E.track_sample(b['path'], t + 1)
            if math.hypot(a1[0] - a0[0], a1[2] - a0[2]) < CRAB_SPEED or any(
                    t0 <= t <= t1 for t0, t1, _ in wins + crab_ok.get(b['id'], [])):
                continue
            c = abs(((E.broom_pose(scene, b, t)['yaw'] - E.broom_heading(b['path'], t)) % 360 + 540) % 360 - 180)
            if c > CRAB_LIMIT:
                problems.append('%s: broom %s flies %.0f degrees off its own heading at t %d' % (sc.sid, b['id'], c, t))
                break
        for q in range(0, scene['length'] * 4 + 1):
            t = q / 4.0
            y = E.broom_pose(scene, b, t)['yaw']
            if prev is not None and not any(t0 <= t <= t1 for t0, t1, _ in wins):
                r = abs(((y - prev) % 360 + 540) % 360 - 180) * 4
                if r > worst[0]:
                    worst = (r, b['id'], t)
                if r > YAW_RATE_LIMIT:
                    problems.append('%s: broom %s turns %.1f degrees a tick at t %.2f' % (sc.sid, b['id'], r, t))
                    break
            prev = y
    return (round(worst[0], 2), worst[1], worst[2])


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
    out['yaw rate'] = check_yaw_rates(sc, scene, P)
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


# ================================================================ the pitch: flying_<h>_match and flying_<h>_snitch
QA = None              # quidditch_pitch_set ANCHORS (set_pitch)
CROWD_BOXES = None     # house -> [(centre, half size)]: boxes that hold that house's seats and no other's
OWN = ['o_k', 'o_b1', 'o_c3', 'o_c1', 'o_c2', 'o_b2', 'viewer']     # in the order they line up, left to right
RIV = ['r_k', 'r_b1', 'r_c3', 'r_c1', 'r_c2', 'r_b2', 'r_s']
ROLE = {'k': 'keeper', 'c1': 'chaser', 'c2': 'chaser', 'c3': 'chaser', 'b1': 'beater', 'b2': 'beater', 's': 'seeker'}
KIT_POOL = (3, 7, 11, 15, 19, 23, 29)
PARK_PITCH = [-60.0, -40.0, 150.0]
# the strikes, 6-12 s apart: (seconds, where it would strike if it could be anywhere); storm() moves each to the
# nearest spot on the ring round the stadium that its shot can see, so every bolt is on screen
STORM_STRIKES = {'match': [(2.0, [150, 0, -60]), (10.5, [-120, 0, 90]), (18.5, [140, 0, 80]), (27.0, [-130, 0, -100]),
                           (35.4, [150, 0, 20]), (43.0, [-96, 0, 115]), (49.2, [-140, 0, 40])],
                 'snitch': [(1.5, [140, 0, -70]), (7.5, [-96, 0, 115]), (14.5, [150, 0, 30]), (21.5, [-130, 0, 60]),
                            (28.5, [-120, 0, -90]), (35.5, [140, 0, 90])]}
SOUND_SPEED = 17.0     # blocks a tick: thunder comes after the flash by the distance to the camera
STRIKE_YS = (25.0, 50.0, 80.0)          # points up the bolt that must be on screen


def strike_seen(scene, t, at):
    c = E.engine_cam(scene['camera'], t)
    lk, fov = E.engine_look(scene, scene['camera'], t)
    seen = [E.in_view(c, lk, fov, [at[0], y, at[2]], aspect=16 / 9.0) for y in STRIKE_YS]
    return seen[1] and (seen[0] or seen[2])          # the middle of the bolt and one end of it


STRIKE_CLEAR = 25.0                     # a bolt lands at least this far (horizontally) from every seat
_SEATS_XZ = []


def seats_xz(events):
    """(x, z) of every seat the crowd events in `events` fill."""
    out = []
    for e in events:
        if e.get('kind') == 'effect' and e.get('id') == 'crowd':
            out.extend((p[0], p[2]) for p, _ in E.crowd_seats(e))
    return out


def strike_clear(at, seats):
    return all(math.hypot(at[0] - x, at[2] - z) >= STRIKE_CLEAR for x, z in seats)


def strike_spot(sc, t, want):
    """want if its shot sees it at t and it is clear of the stands, else the nearest spot that is clear and that the
    shot sees, on rings 150-190 blocks out (the end stands' seats reach 141.5); failing that the nearest clear spot."""
    V = view(sc)
    if not _SEATS_XZ:
        _SEATS_XZ.extend(seats_xz([crowd_event(hh) for hh in HOUSES]))
    if strike_clear(want, _SEATS_XZ) and strike_seen(V, t, want):
        return list(want)
    best = unseen = None
    for r in (150.0, 160.0, 170.0, 180.0, 190.0):
        for k in range(48):
            a = 2 * math.pi * k / 48.0
            p = [round(r * math.cos(a)), 0, round(r * math.sin(a))]
            if not strike_clear(p, _SEATS_XZ):
                continue
            d = math.dist(p, want)
            if unseen is None or d < unseen[0]:
                unseen = (d, p)
            if strike_seen(V, t, p) and (best is None or d < best[0]):
                best = (d, p)
    return (best or unseen)[1]


def check_lightning(sc, scene, problems):
    """The lightning: at least two in three strikes on screen at their tick (the middle of the bolt and one end in
    the 16:9 frame; a face close-up only sees the flicker), none within STRIKE_CLEAR blocks of a seat, the strikes 6 to
    12 s apart, each followed by its thunder
    after the distance to the camera at the speed of sound. Returns (strikes on screen, strikes)."""
    ev = [e for e in scene['events'] if e.get('id') == 'lightning']
    seats = seats_xz(scene['events'])
    for e in ev:
        if not strike_clear(e['at'], seats):
            problems.append('%s: the lightning at t %d strikes within %.0f blocks of the stands' % (
                sc.sid, e['t'], STRIKE_CLEAR))
    th = [e['t'] for e in scene['events'] if e.get('id') == SFX + 'thunder']
    seen = sum(1 for e in ev if strike_seen(scene, e['t'], e['at']))
    if 3 * seen < 2 * len(ev):
        problems.append('%s: only %d of %d lightning strikes are on screen' % (sc.sid, seen, len(ev)))
    for t0, t1, label in sc.notes.get('strike_shots', []):
        if not any(t0 <= e['t'] <= t1 and strike_seen(scene, e['t'], e['at']) for e in ev):
            problems.append('%s: no lightning on screen in %s' % (sc.sid, label))
    for e in ev:
        delay = math.dist(E.engine_cam(scene['camera'], e['t']), [e['at'][0], 40.0, e['at'][2]]) / SOUND_SPEED
        if not any(abs(x - (e['t'] + delay)) <= 1.0 for x in th):
            problems.append('%s: no thunder %.0f ticks after the lightning at t %d' % (sc.sid, delay, e['t']))
    ts = sorted(e['t'] for e in ev)
    for a, b in zip(ts, ts[1:]):
        if not 120 <= b - a <= 240:
            problems.append('%s: lightning %d ticks apart at t %d' % (sc.sid, b - a, b))
    return seen, len(ev)


def set_pitch(anchors, world):
    global QA, CROWD_BOXES
    QA = anchors
    CROWD_BOXES = crowd_boxes()


def pitch_set():
    return {'templates': [{'id': 'hogwartsmagic:quidditch_pitch', 'at': list(QP.TEMPLATE_AT)}],
            'schematics': [{'id': QP.SCHEMATIC['id'], 'at': list(QP.SCHEMATIC['at']),
                            'rotation': QP.SCHEMATIC['rotation']}],
            'time_of_day': 9000}


def crowd_rows(house):
    rows = []
    for key in ('stands', 'rail_rows', 'standing_rows'):
        for st in QA[key]:
            if st['house'] == house:
                for r in st['rows']:
                    rows.append([round(v, 3) for v in r] + ([1] if key != 'stands' else []))
    return rows


def crowd_event(house):
    return {'t': 0, 'kind': 'effect', 'id': 'crowd', 'at': [0.0, 0.0, 0.0], 'rows': crowd_rows(house), 'style': house}


def crowd_boxes():
    """Boxes per house, each holding seats of that house only: the house's seats split (along the longest side, at
    the median) until no other house's seat lies inside a box. The houses' sectors are interleaved round the ring,
    so a stand never roars for another house's goal."""
    seats = {hh: [p for p, _ in E.crowd_seats(crowd_event(hh))] for hh in HOUSES}
    out = {}

    def box(ps):
        lo = [min(p[i] for p in ps) - 0.6 for i in range(3)]
        hi = [max(p[i] for p in ps) + 0.6 for i in range(3)]
        lo[1] -= 1.0
        hi[1] += 2.6
        return lo, hi

    def inside(p, lo, hi):
        return all(lo[i] <= p[i] <= hi[i] for i in range(3))
    for hh in HOUSES:
        others = [p for o in HOUSES if o != hh for p in seats[o]]
        todo, done = [seats[hh]], []
        while todo:
            ps = todo.pop()
            lo, hi = box(ps)
            if not any(inside(q, lo, hi) for q in others) or len(ps) < 2:
                done.append((lo, hi, len(ps)))
                continue
            ax = max((0, 2), key=lambda i: hi[i] - lo[i])
            ps = sorted(ps, key=lambda p: p[ax])
            todo += [ps[:len(ps) // 2], ps[len(ps) // 2:]]
        out[hh] = [([round((lo[i] + hi[i]) / 2, 2) for i in range(3)], [round((hi[i] - lo[i]) / 2, 2) for i in range(3)])
                   for lo, hi, n in done]
        for lo, hi, n in done:
            assert not any(inside(q, lo, hi) for q in others), hh
    return out


def react(sc, t, house, style, cause, duration=None):
    """crowd_react on every box of one house's seats; recorded with its cause (a goal, the entrance, a near miss,
    the catch) for assert 7."""
    for at, spread in CROWD_BOXES[house]:
        kw = {'at': at, 'spread': spread, 'style': style}
        if duration:
            kw['duration'] = duration
        sc.ev(t, 'effect', 'crowd_react', **kw)
    sc.reactions.append((t, style, house, cause))


def storm(sc, part):
    sc.ev(0, 'effect', 'sky', at=[0.0, 0.0, 0.0], style='storm')
    sc.ev(0, 'effect', 'rain', at=QA['rain']['at'], spread=QA['rain']['spread'], intensity=1.3)
    prev = None
    for s, want in STORM_STRIKES[part]:
        # at its time or as near it as keeps the 6-12 s rhythm, where its shot can see it
        V = view(sc)
        fits = [tk(s) + dt for dt in [0] + [d * sg for d in range(4, 81, 4) for sg in (1, -1)]
                if 0 < tk(s) + dt < sc.L - 20 and (prev is None or 120 <= tk(s) + dt - prev <= 240)]
        if not fits:
            continue                                 # no room left for it in the rhythm
        t, at = fits[0], strike_spot(sc, fits[0], want)                # a face close-up may not see the sky: then it only flickers
        for t_ in fits:
            at_ = strike_spot(sc, t_, want)
            if strike_seen(V, t_, at_):
                t, at = t_, at_
                break
        prev = t
        sc.ev(t, 'effect', 'lightning', at=at)
        delay = math.dist(E.engine_cam(sc.camera, t), [at[0], 40.0, at[2]]) / SOUND_SPEED
        tt = min(sc.L - 1, int(round(t + delay)))
        sc.sfx(tt, 'thunder', 0.9 if delay < 8 else 0.75)
    sc.sfx(0, 'rain', 0.75, loop=True)
    sc.sfx(0, 'crowd_bed', 0.45, loop=True)
    for hh in HOUSES:
        e = crowd_event(hh)
        sc.ev(0, 'effect', 'crowd', at=e['at'], rows=e['rows'], style=hh)


def team_kits(h):
    """id -> (house, skin) for both teams: the viewer's house and rival(h)."""
    r = rival(h)
    out = {}
    for ids, house in ((OWN, h), (RIV, r)):
        for i, aid in enumerate(ids):
            n = KIT_POOL[i]
            if house == 'gryffindor' and n == 1 or house == 'slytherin' and n == 27:
                n += 1
            out[aid] = (house, None if aid == 'viewer' else 'student_%s_%02d' % (house, n))
    return out


class Flier:
    """A third-year on a racing broom for the whole scene: the broom's route as waypoints [(tick, pos, ease)], its
    look from its own heading and climb (the auto-bank kept), facing `hover` yaws while it hangs still."""

    def __init__(self, sc, aid, house, skin):
        self.sc, self.id, self.house = sc, aid, house
        self.route = []
        self.hover = []           # (t0, t1, yaw)
        self.pins = []            # (t0, t1, yaw): faces yaw whatever the path does
        self.rig = Rig()
        self.items = {'chest': quidditch_robes(house), 'legs': SHIN_GUARDS}
        self.skin = {'viewer': True} if skin is None else {'texture': skin}

    def at(self, t, p, ease='spline'):
        self.route.append((int(round(t)), [round(c, 4) for c in p], ease))
        return self

    def build(self, sigma=7.0, step=4):
        """The route through its waypoints, then smoothed (a Gaussian of sigma ticks: no corner at a waypoint, so
        the speed changes gently everywhere) and keyed every `step` ticks as one spline run."""
        sc = self.sc
        r = sorted(self.route, key=lambda x: x[0])
        raw = Keys().at(r[0][0], r[0][1])
        for t, p, e in r[1:]:
            raw.at(t, p, e)
        g = smoothed(lambda t: E.track_sample(raw.k, max(0, min(sc.L, t))), sigma)
        b = Broom(sc, 'broom_' + self.id, g(0), self.hover[0][2] if self.hover else 0.0, btype=RACING,
                  roll_keys=False)
        for t in range(step, sc.L + 1, step):
            b.path.at(t, g(t), 'spline')            # the last key too: the flight runs on into the cut
        self.b = b
        b.sync()
        # the look: the heading and climb while it flies, the hover yaw while it hangs, blended by speed
        keys = b.path.k
        prev = self.hover[0][2] if self.hover and self.hover[0][0] <= 0 else E.broom_heading_held(keys, 0)
        b.look = [[0, prev, 0.0, None, None]]
        for t, hd in steady_yaws(keys, list(range(4, sc.L + 1, 4)), prev, self.hover, self.pins, t_start=0):
            pitch = E.broom_climb(keys, t)
            b.look.append([t, hd, round(pitch, 3), None, 'spline' if t + 4 <= sc.L else None])
        b.look[-1][4] = None
        b.sync()
        sc.notes.setdefault('crab_ok', {})[b.id] = [(t0, t1, 'pinned') for t0, t1, _ in self.pins]
        a = {'id': self.id, 'type': 'player', 'scale': THIRD, 'skin': self.skin, 'items': self.items,
             'ride': b.id, 'path': [{'t': 0, 'pos': r4(E.rider_seat(THIRD))}],
             'look': [{'t': 0, 'yaw': 0.0, 'pitch': 0.0}]}
        self.a = sc.actor(a)
        return self

    def pos(self, t):
        return E.track_sample(self.b.path.k, t)


L3, L4 = 50.0, 40.0
MATCH_FUNCS = {}                   # aid -> route fn(t) on the match's clock that the Snitch scene carries on with
SEEK_PERIOD, SEEK_BEARING, SEEK_TURN = 360.0, 112.0, 84
T3 = {'captain': 6.0, 'entrance': 10.3, 'clean': 17.2, 'crate': 21.0, 'snitch_out': 21.3, 'whistle': (21.6, 22.0),
      'throw': 22.5, 'off': 22.7, 'c1_catch': 23.7, 'pass1': 25.3, 'catch2': 26.0, 'pass2': 26.6, 'catch3': 27.5,
      'shot': 27.8, 'ring': 28.6, 'comm_goal': 29.2, 'whip': 34.7, 'crack': 35.4, 'miss': 36.1, 'chant': 32.5}


def match(h, world, chase_scene=None):
    sc = Sc('flying_%s_match' % h, 'The Storm Match', L3, world, h)
    sc.notes['pitch'] = True
    kits = team_kits(h)
    F_ = {aid: Flier(sc, aid, kits[aid][0], kits[aid][1]) for aid in OWN + RIV}
    match_routes(sc, F_)
    for f in F_.values():
        f.build()
    hooch = sc.actor({'id': 'hooch', 'type': 'player', 'skin': {'texture': 'hooch'}, 'items': {'offhand': WHISTLE},
                      'path': [{'t': 0, 'pos': list(QA['hooch_spot'])}],
                      'look': [{'t': 0, 'yaw': -90.0, 'pitch': 10.0}]})
    match_hooch(sc, F_, hooch)
    match_balls(sc, F_, hooch)
    match_bludger_play(sc, F_)
    match_play_on(sc, F_)
    match_riders(sc, F_)
    for f in F_.values():
        refresh(f.a, f.rig)
    match_camera(sc, F_)
    match_sound(sc, F_, h)
    storm(sc, 'match')
    score(sc, '_match')
    scene = sc.scene(pitch_set(), QP.RADIUS, nxt='flying_%s_snitch' % h)
    qc = sc.notes['quaffle_contacts']
    sc.notes.update(extra_checks=[('crowd gap', check_crowd_gap), ('quaffle ring', check_quaffle_ring),
                                  ('reactions', check_reactions), ('bludgers', check_bludgers),
                                  ('lightning', check_lightning)],
                    ball_paths=[('quaffle_%d' % (i + 1), qc) for i in range(3)] + sc.notes['ball_paths_extra'])
    return sc, scene


GATE_LINE = {'own': 40.9, 'riv': -40.6}      # through the middle of each tunnel's narrower outer opening


def gate(team):
    return GATE_LINE[team]


def match_routes(sc, F_):
    """Every flier's route: waiting outside the west stand, the fly-out through the team tunnels into the roar, a
    swooping climb round the stadium into two facing lines over Madam Hooch, then the game."""
    for team, ids, s in (('own', OWN, 1.0), ('riv', RIV, -1.0)):
        zg = gate(team)
        order = [ids[0], ids[6]] + ids[1:6]          # the captain and the Seeker wait at the front, out first
        for i, aid in enumerate(ids):
            j = order.index(aid)
            f = F_[aid]
            slot = [-92.0 - 3.5 * (j // 2), 1.5, zg + (-1.2 if j % 2 == 0 else 1.2)]
            tE = 206 + 8 * j + (4 if team == 'riv' else 0)
            f.at(0, slot, 'in_out').at(tE - 10 - 4 * (j // 2), slot, 'in_out')
            f.hover.append((0, tE - 12, -90.0))
            f.at(tE, [-89.5, 2.0, zg]).at(tE + 16, [-76.5, 2.2, zg])
            f.at(tE + 32, [-61.0, 5.0, zg + s * 1.5 * (j % 3 - 1)])
            f.at(tE + 58, [-37.0 + 3 * (j % 2), 13.0 + j, zg + s * (17.0 + 2.0 * j)])
            fx = (-9.0 + 3.0 * i) * s
            f.at(tE + 92, [fx * 0.6 - 6.0, 20.0 + 0.6 * i, s * (50.0 - i)])
            f.at(362, [fx, 15.0 + 0.3 * i, s * 22.0])
            fy = 12.0 + (2.0 if aid in ('viewer', 'r_s') else 0.0) + (0.6 if i % 2 else 0.0)
            f.at(392, [fx, fy, s * 6.0], 'in_out').at(444, [fx, fy, s * 6.0], 'in_out')
            f.hover.append((392, 444, 180.0 if team == 'own' else 0.0))
            f.slot = [fx, fy, s * 6.0]
    sc.notes['entrance'] = 206 + 16                   # the captain bursts out of the tunnel
    game_routes(sc, F_)


def orbit(cx, cz, rx, rz, y0, period, ph, sense=1.0, yamp=1.5):
    w = 2 * math.pi / period

    def f(t):
        a = ph + sense * w * t
        return [cx + rx * math.cos(a), y0 + yamp * math.sin(0.5 * a + ph), cz + rz * math.sin(a)]
    return f


def orbit_from(p, t0, rx, rz, y0, period, sense=1.0, yamp=1.5, cx=0.0, cz=0.0):
    """An orbit whose phase puts it over p's bearing at tick t0 (so a flier joins it without a lunge)."""
    a = math.atan2((p[2] - cz) / rz, (p[0] - cx) / rx)
    ph = a - sense * 2 * math.pi / period * t0
    return orbit(cx, cz, rx, rz, y0, period, ph, sense, yamp)


def follow(F_, aid, fn, t0, t1, step=10, ease_last='in_out'):
    for t in range(t0, t1 + 1, step):
        F_[aid].at(t, fn(t), ease_last if t + step > t1 else 'spline')


def game_routes(sc, F_):
    T = {k: (tk(v) if not isinstance(v, tuple) else tuple(tk(x) for x in v)) for k, v in T3.items()}
    sc.notes['T'] = T
    # the attack: c1 takes the Quaffle out of the air over Hooch and the three race up the pitch, climbing to the
    # far hoops; c1 passes to c2 (right), c2 to c3 (left), c3 shoots through the middle hoop
    hoop = QA['hoops'][4]['centre']
    P_c = [-3.4, 9.4, 2.6]
    P_s = [-2.2, 46.6, 88.0]
    A0 = hermite(P_c, [0.0, 0.18, 0.55], P_s, [0.05, 0.3, 1.0], T['c1_catch'], T['shot'])
    A = lambda t: (add(P_c, [0.0, 0.18, 0.55], t - T['c1_catch']) if t < T['c1_catch'] else
                   add(P_s, [0.05, 0.3, 1.0], t - T['shot']) if t > T['shot'] else A0(t))
    sc.notes['attack'] = A
    off = {'o_c1': lambda t: [0.0, 0.0, 0.0], 'o_c2': lambda t: [6.5, 1.2, -3.0], 'o_c3': lambda t: [-6.0, 0.6, -1.5]}
    # off the line the three swoop round in tight banked U-turns (their line faced the rivals, their hoops are
    # behind them), c1 under the Quaffle as it goes up, c2 round to the right, c3 to the left
    c = F_['o_c1']
    c.at(452, [0.2, 12.2, 3.6]).at(460, [-1.6, 11.2, 0.8]).at(467, [-3.6, 10.2, 0.6])
    follow(F_, 'o_c1', lambda t: A(t), T['c1_catch'], T['shot'], 8, 'spline')
    F_['o_c2'].at(450, [3.3, 12.0, 4.2]).at(458, [4.8, 11.9, 1.8]).at(466, [6.6, 11.9, 1.6])
    F_['o_c2'].at(474, [6.6, 12.0, 3.8]).at(482, [5.2, 12.2, 6.4])
    follow(F_, 'o_c2', lambda t: add(A(t), off['o_c2'](t)), 490, T['shot'], 8, 'spline')
    F_['o_c3'].at(450, [-3.3, 11.8, 4.2]).at(458, [-4.8, 11.4, 1.8]).at(466, [-6.6, 11.0, 1.6])
    F_['o_c3'].at(474, [-7.4, 11.0, 3.6]).at(482, [-8.4, 11.3, 6.4])
    follow(F_, 'o_c3', lambda t: add(A(t), off['o_c3'](t)), 490, T['shot'], 8, 'spline')
    # after the shot: c3 pulls up over the hoops, c1 and c2 bank away, all three loop back down the pitch
    vS = [0.05, 0.3, 1.0]
    F_['o_c3'].at(T['shot'] + 12, [-6.0, 52.0, 100.5]).at(T['shot'] + 26, [-11.0, 59.0, 106.0])
    F_['o_c3'].at(T['shot'] + 50, [-26.0, 52.0, 92.0])
    F_['o_c1'].at(T['shot'] + 14, [6.0, 44.0, 98.0]).at(T['shot'] + 40, [24.0, 40.0, 88.0])
    F_['o_c2'].at(T['shot'] + 14, [12.0, 46.0, 92.0]).at(T['shot'] + 40, [34.0, 44.0, 76.0])
    # the rival chasers chase them up the pitch, a beat behind
    lagged = lambda dt, o: (lambda t: add(A(max(T['c1_catch'], t - dt)), o))
    rc = {'r_c1': ([1.0, -2.5, -2.0], 20), 'r_c2': ([-8.0, -1.5, -4.0], 26), 'r_c3': ([7.0, -4.0, -6.0], 32)}
    for aid, (o, dt) in rc.items():
        f = F_[aid]
        f.at(462, add(f.slot, [o[0] * 0.3, -1.5, 2.0]))          # forward off the line (+z for the rivals)
        follow(F_, aid, lagged(dt, o), 490, T['shot'] + 6, 8, 'spline')
    # the beaters ride shotgun on the attack, high and wide
    for aid, o, dt in (('o_b1', [-13.0, 7.0, -6.0], 6), ('o_b2', [16.0, 8.0, -4.0], 10),
                       ('r_b1', [14.0, 3.0, -4.0], 30), ('r_b2', [-12.0, 2.0, -6.0], 36)):
        f = F_[aid]
        f.at(462, add(f.slot, [o[0] * 0.08, 4.0, -2.0 if aid[0] == 'o' else 2.0]))     # forward off the line
        t_f = max(486, T['c1_catch'] + dt)                     # on the attack once it moves
        if aid == 'o_b1':                  # and round, wide, to ride with their Chasers up the pitch behind them
            f.at(474, add(f.slot, [o[0] * 0.55, 4.6, -7.0]))
        elif aid == 'o_b2':
            f.at(474, add(f.slot, [5.6, 4.6, -5.2])).at(486, add(f.slot, [8.0, 5.4, -3.4]))
            t_f = 496
        follow(F_, aid, lagged(dt, o), t_f, T['shot'] - 8, 10, 'spline')
    # the keepers to their hoops
    for aid, z, y, ta in (('o_k', -100.0, 46.0, 600), ('r_k', 101.0, 48.0, 524)):      # r_k turned before the shot
        f = F_[aid]
        run = move(f.slot, [0.0, y, z], 446, ta)
        for t in range(452, ta, 8):
            f.at(t, add(run(t), [0.0, 6.0 * math.sin(math.pi * (t - 446) / (ta - 446)), 0.0]))
        f.at(ta, [0.0, y, z], 'in_out')
        f.hover.append((ta, sc.L, 0.0 if z < 0 else 180.0))
        # as he comes in he turns to face the play, and holds it through his lunges (sliding side-on)
        f.pins.append((ta - 4, ta + 72 if z < 0 else sc.L, 0.0 if z < 0 else 180.0))
    # the rival keeper guards the ring and lunges the wrong way at the shot
    rk = F_['r_k']
    rk.at(T['shot'] - 4, [1.2, 48.0, 101.0], 'in_out').at(T['shot'] + 10, [-4.2, 47.4, 101.4], 'in_out')
    rk.at(T['shot'] + 40, [-1.5, 48.0, 101.0], 'in_out').at(sc.L, [0.5, 48.2, 101.0], 'in_out')
    ok = F_['o_k']
    ok.at(700, [-2.0, 46.4, -100.0], 'in_out').at(sc.L, [1.5, 46.0, -100.0], 'in_out')
    # the Seekers climb out of the game and circle high over it, hunting
    # their phase is set by the Snitch scene: SEEK_TURN ticks into it the boy is at the bearing SEEK_BEARING, the
    # nearest he comes to the dive column (both orbits run on through the hand-over on the match's clock)
    w = 2 * math.pi / SEEK_PERIOD
    ph = math.radians(SEEK_BEARING) - w * (sc.L + SEEK_TURN)
    vo = orbit(0.0, 4.0, 34.0, 34.0, 60.0, SEEK_PERIOD, ph, 1.0, 1.2)
    so = orbit(0.0, 4.0, 31.0, 31.0, 55.5, SEEK_PERIOD, ph - 0.2, 1.0, 1.0)   # a little behind, inside, below
    sc.notes['seeker_orbits'] = (vo, so)
    MATCH_FUNCS['viewer'], MATCH_FUNCS['r_s'] = vo, so
    F_['viewer'].at(458, [10.5, 18.0, 2.0]).at(470, [13.0, 25.0, -1.0])     # up and out off the line, forward
    follow(F_, 'viewer', vo, 540, sc.L, 10, 'spline')
    F_['r_s'].at(458, [-10.5, 17.0, -2.0]).at(470, [-13.0, 24.0, 1.0])
    follow(F_, 'r_s', so, 540, sc.L, 10, 'spline')
    # everyone else, once their part is played: loose circuits round the pitch below the Seekers
    loops = {'o_c1': (40.0, 70.0, 34.0, 420.0, 1.1, 1.0), 'o_c2': (48.0, 78.0, 38.0, 460.0, 0.75, 1.0),
             'o_c3': (32.0, 62.0, 42.0, 400.0, 1.55, 1.0), 'r_c1': (44.0, 74.0, 26.0, 440.0, 2.4, -1.0),
             'r_c2': (36.0, 66.0, 30.0, 400.0, 2.0, -1.0), 'r_c3': (52.0, 82.0, 22.0, 480.0, 2.9, -1.0),
             'o_b1': (30.0, 58.0, 46.0, 380.0, 1.9, 1.0), 'o_b2': (56.0, 86.0, 18.0, 500.0, 0.4, 1.0),
             'r_b1': (24.0, 50.0, 50.0, 360.0, 3.4, -1.0), 'r_b2': (60.0, 88.0, 14.0, 520.0, 3.9, -1.0)}
    sc.notes['loops'] = {}
    for aid, (rx, rz, y0, per, ph, sense) in loops.items():
        last, lp, _ = max(F_[aid].route, key=lambda x: x[0])
        t0 = last + 40
        fn = orbit_from(lp, t0, rx, rz, y0, per, sense, 2.0)
        sc.notes['loops'][aid] = fn
        MATCH_FUNCS[aid] = fn
        follow(F_, aid, fn, t0, sc.L, 10, 'spline')


CARRY_DIR = [-0.55, -0.35, 0.75]      # a Chaser's right hand out at his side with the Quaffle (broom frame)
RELEASE_DIR = [-0.15, 0.45, 0.88]     # the hand thrown forward and up as it lets go


def arm_target(scene, a, bid, t, d, frac=1.0, side='right_arm'):
    """The point an arm's length from the rider's shoulder along d (broom frame): a rigid arm's hand lies on that
    sphere, so this is the one place along d it can reach."""
    _, root, yaw, s, piv, rot = parts_of(scene, a, t)[side]
    sh = E.model_to_world(root, yaw, s, *piv)
    b = E.broom_pose(scene, E.actor_by_id(scene, bid), t)
    n = norm(d)
    return add(sh, E._mat_vec(b['m'], n), frac * math.sqrt(101.0) / 16.0 * s * 15.0 / 16.0)
QG = 0.012                            # the Quaffle's fall, blocks a tick squared


def match_hooch(sc, F_, hooch):
    """Madam Hooch at the centre circle by the crate: she talks up to the players, the whistle to her lips in her
    left hand, and throws the Quaffle up with her right."""
    V = view(sc)
    T = sc.notes['T']
    rg = Rig()
    rg.r('right_arm').set(0, [-38, 0, 6], None)
    refresh(hooch, rg)
    w0, w1 = T['whistle']
    rg.r('head').to(w0, w1, [10, 32, 0])
    rg.o('left_arm').to(w0, w1, [0.5, 1.5, 1.5])
    refresh(hooch, rg)
    res = reach(sc, hooch, rg, 'left_arm', w0, w1, mouth(V, hooch, w1))
    assert res < 0.03, res
    sc.contacts.append(('hooch: the whistle to her lips', 'hooch', 'left_arm', w1, ('mouth', 'hooch'), 'whistle'))
    for part in ('left_arm', 'head'):
        rg.r(part).to(w1 + 6, w1 + 16, [0, 0, 0])
    rg.o('left_arm').to(w1 + 6, w1 + 16, [0, 0, 0])
    Tt = T['throw']
    rg.r('right_arm').to(Tt - 9, Tt - 4, [-10, 0, 8])
    rg.r('right_arm').to(Tt - 4, Tt + 4, [-172, 0, 8])
    rg.r('right_arm').to(Tt + 14, Tt + 30, [0, 0, 0])
    refresh(hooch, rg)
    sc.notes['hooch_rig'] = rg
    cl = tk(T3['clean'])
    hooch['anims'] = [{'t': cl, 'name': 'TALK', 'blend': 6},
                      {'t': cl + tk(float(CLIP['flying_hooch_clean'])) - 4, 'name': 'IDLE', 'blend': 8}]
    hooch['look'] = [{'t': 0, 'yaw': -90.0, 'pitch': 10.0}, {'t': cl - 20, 'yaw': -90.0, 'pitch': 10.0},
                     {'t': cl, 'yaw': -150.0, 'pitch': -42.0}, {'t': cl + 40, 'yaw': -30.0, 'pitch': -42.0},
                     {'t': cl + 62, 'yaw': -90.0, 'pitch': -30.0}, {'t': w0 - 8, 'yaw': -90.0, 'pitch': 5.0},
                     {'t': w1 + 4, 'yaw': -90.0, 'pitch': 5.0},
                     {'t': Tt + 6, 'yaw': -100.0, 'pitch': -60.0}, {'t': Tt + 30, 'yaw': -160.0, 'pitch': -35.0}]


def ride_offset(scene, bid, t, p):
    """World point p in the frame of broom bid at tick t (a rider's path offset there)."""
    b = E.broom_pose(scene, E.actor_by_id(scene, bid), t)
    s = E.actor_by_id(scene, bid).get('scale', 1.0)
    v = E._mat_vec(E._transpose(b['m']), sub(p, b['pos']))
    return [c / s for c in v]


def flight_keys(k, fn, t0, t1, step=3):
    """Spline keys from fn every `step` ticks after t0 up to t1, the last (at t1) linear so the run keeps the ball's
    speed into it."""
    t = t0 + step
    while t < t1 - 1.0:
        k.at(t, fn(t), 'spline')
        t += step
    k.at(t1, fn(t1), 'linear')


def match_balls(sc, F_, hooch):
    """The Quaffle (three actors, one per carrier: an actor rides one broom), the Bludgers, the Snitch, the crate."""
    V = view(sc)
    T = sc.notes['T']
    Tt = T['throw']
    seq = [('o_c1', T['c1_catch'], T['pass1']), ('o_c2', T['catch2'], T['pass2']), ('o_c3', T['catch3'], T['shot'])]
    # the Chasers' arms: out for the catch, held at the side, thrown forward at the pass
    for aid, tc, tp in seq:
        f = F_[aid]
        r = reach(sc, f.a, f.rig, 'right_arm', tc - 16, tc - 1, arm_target(V, f.a, f.b.id, tc - 1, CARRY_DIR))
        assert r < 0.06, (aid, r)
        r = reach(sc, f.a, f.rig, 'right_arm', max(tc + 1, tp - 7), tp, arm_target(V, f.a, f.b.id, tp, RELEASE_DIR),
                  'in')
        assert r < 0.06, (aid, r)
        f.rig.r('right_arm').to(tp + 2, tp + 16, [0, 0, 0])
        refresh(f.a, f.rig)
    hq = lambda t: hand(V, hooch, 'right_arm', t)
    rh = lambda aid, t: hand(V, F_[aid].a, 'right_arm', t)
    contacts = []
    hit = []
    for i, (aid, tc, tp) in enumerate(seq):
        qid = 'quaffle_%d' % (i + 1)
        bid = F_[aid].b.id
        k = Keys()
        if i == 0:
            # in Hooch's hand, up with her arm, out of it at the throw into the air to c1
            k.at(0, hq(0)).hold(Tt - 9)
            for t in range(Tt - 8, Tt + 1):
                k.at(t, hq(t), 'spline')
            fly = parabola(hq(Tt), rh(aid, tc), Tt, tc, 0.02)
            flight_keys(k, fly, Tt, tc - 0.001)
            sc.contacts.append(('hooch: the Quaffle up out of her hand', 'hooch', 'right_arm', Tt,
                                ('ball', qid, Tt), 'throw'))
            contacts.append(Tt)
        else:
            k.at(0, PARK_PITCH).hold(tc - 0.25)
            k.at(tc - 0.001, rh(aid, tc), 'linear')
            contacts.append(tc)
        # carried: an offset in the broom frame, on his hand (keyed with the hand as it throws)
        for t in sorted(set([tc] + list(range(tc + 8, tp - 8, 8)) + [x for x in range(tp - 8, tp, 2) if x > tc])):
            k.at(t, ride_offset(V, bid, t, rh(aid, t)), 'spline' if t > tc else None)
        k.at(tp - 0.001, ride_offset(V, bid, tp - 0.001, rh(aid, tp)), 'linear')
        k.at(tp, rh(aid, tp))
        sc.contacts.append(('%s: the Quaffle into his hand' % aid, aid, 'right_arm', tc, ('ball', qid, tc), 'catch'))
        sc.contacts.append(('%s: the Quaffle away' % aid, aid, 'right_arm', tp, ('ball', qid, tp), 'throw'))
        contacts += [tc, tp]
        hit.append((aid, tc))
        if i < 2:
            nxt, tn = seq[i + 1][0], seq[i + 1][1]
            fly = parabola(rh(aid, tp), rh(nxt, tn), tp, tn, QG)
            flight_keys(k, fly, tp, tn - 0.25)
            k.at(tn, PARK_PITCH, 'linear')
            contacts.append(tn)
        else:
            # the shot: through the middle of the far middle hoop, on past it and down behind the hoops
            ring = QA['hoops'][4]['centre']
            fly = parabola(rh(aid, tp), ring, tp, T['ring'], QG)
            sc.notes['shot_fly'] = fly
            flight_keys(k, fly, tp, T['ring'] + 30)
            k.at(T['ring'] + 30.25, PARK_PITCH, 'linear')
            contacts.append(T['ring'] + 30)
        sc.actor({'id': qid, 'type': QUAFFLE, 'scale': THIRD, 'ride': {'on': bid, 'from': tc, 'until': tp}, 'path': k.k})
    sc.notes['quaffle_contacts'] = contacts
    sc.notes['goal_tick'] = T['ring']
    sc.notes['quaffle_seq'] = seq
    sc.notes['catches'] = hit
    # the crate: a barrel shut on the grass, open from the release
    cr = QA['ball_crate']
    cc = [cr[0], 0.5, cr[2]]
    T_CR = T['crate']
    sc.actor({'id': 'crate', 'type': 'hogwartsmagic:memory_block', 'block': 'minecraft:barrel[facing=up,open=false]',
              'path': Keys().at(0, cc).hold(T_CR - 0.25).at(T_CR, PARK_PITCH, 'linear').k})
    sc.actor({'id': 'crate_open', 'type': 'hogwartsmagic:memory_block', 'block': 'minecraft:barrel[facing=up,open=true]',
              'path': Keys().at(0, PARK_PITCH).hold(T_CR - 0.25).at(T_CR, cc, 'linear').k})
    top = [cr[0], 1.15, cr[2]]
    # the Bludgers burst out and away; B1 roams back into the game, past the camera, into o_b1's bat and away at
    # r_c1's head; B2 climbs away over the far stands
    b1 = Keys().at(0, PARK_PITCH).hold(T_CR - 0.25).at(T_CR, top)
    b1.through([(T_CR + 6, [cr[0] + 0.4, 6.0, cr[2] + 0.3]), (T_CR + 14, [cr[0] + 2.0, 16.0, cr[2] + 2.0]),
                (T_CR + 26, [9.0, 28.0, 7.0]), (T_CR + 50, [20.0, 34.0, 16.0]), (T_CR + 90, [26.0, 30.0, 40.0]),
                (T_CR + 130, [16.0, 38.0, 60.0])], settle=False)
    sc.notes['b1_keys'] = b1
    b2 = Keys().at(0, PARK_PITCH).hold(T_CR + 3.75).at(T_CR + 4, top)
    b2.through([(T_CR + 10, [cr[0] - 0.5, 7.0, cr[2] - 0.5]), (T_CR + 20, [1.0, 18.0, -3.0]),
                (T_CR + 36, [-6.0, 30.0, -14.0]), (T_CR + 70, [-22.0, 42.0, -40.0]), (T_CR + 110, [-34.0, 50.0, -62.0])],
               settle=False)
    # then it swings round and back through the game for the rest of the match, on the loop clearest of the riders
    t_b = T_CR + 110
    v_b = sub(E.track_sample(b2.k, t_b), E.track_sample(b2.k, t_b - 1))
    loop, least, pick = bludger_loop(sc, F_, t_b, b2.last(), v_b)
    for t in range(t_b + 4, sc.L + 1, 4):
        b2.at(t, loop(t), 'spline')
    sc.notes['b2_loop'] = (round(least, 2), pick)
    MATCH_FUNCS['bludger_2'] = loop
    sc.actor({'id': 'bludger_2', 'type': BLUDGER, 'path': b2.k})
    # the Snitch: a flicker of gold up out of the crate, a hover, and gone
    sn = Keys().at(0, PARK_PITCH).hold(tk(T3['snitch_out']) - 0.25).at(tk(T3['snitch_out']), top)
    ts = tk(T3['snitch_out'])
    sn.through([(ts + 4, [cr[0] + 0.2, 2.6, cr[2] + 0.1]), (ts + 10, [cr[0] - 0.4, 4.2, cr[2] - 0.6]),
                (ts + 16, [cr[0] - 0.2, 4.6, cr[2] - 0.2]), (ts + 22, [1.5, 8.0, -3.0]), (ts + 32, [-6.0, 14.0, -12.0]),
                (ts + 44, [-18.0, 22.0, -26.0])], settle=False)
    sn.k[-1]['ease'] = 'linear'                 # a linear last key: no spline tangent into the jump away
    sn.at(ts + 44.25, PARK_PITCH, 'linear')
    sc.actor({'id': 'snitch', 'type': SNITCH, 'path': sn.k})
    sc.notes['ball_paths_extra'] = [('snitch', [ts, ts + 44]), ('bludger_2', [T_CR + 4])]


def match_bludger_play(sc, F_):
    """B1 after its roam: it comes at o_b1, who swings and cracks it (bat contact) at r_c1, who ducks; it passes a
    block over his head (the near miss) and away out of the stadium."""
    V = view(sc)
    T = sc.notes['T']
    f = F_['o_b1']
    # when to crack it: the moment (after the goal) a rival is in reach of o_b1's bat, so it can be sent a block
    # over his head no faster than 1.25 blocks a tick
    cand = []
    for tc_ in range(T['crack'] - 30, T['crack'] + 70, 2):
        e0 = E.rider_eye(V, f.a, tc_)
        for vid in ('r_c1', 'r_c2', 'r_c3', 'r_b1', 'r_b2'):
            for tm in range(tc_ + 16, tc_ + 34, 2):
                d = math.dist(add(E.rider_eye(V, F_[vid].a, tm), [0.0, 1.0, 0.0]), e0)
                if d / (tm - tc_) <= 1.1:
                    cand.append((tm - tc_ + abs(tc_ - T['crack']) * 0.2, tc_, vid))
                    break
    assert cand, 'no rival ever in reach of the Bludger'
    T['crack'] = min(cand)[1]
    tcr, tmiss = T['crack'], T['miss']
    rg = f.rig
    rg.r('right_arm').to(tcr - 22, tcr - 8, [-150, 0, 40])            # the bat back over the shoulder
    rg.r('right_arm').to(tcr - 8, tcr + 3, [-40, 0, -20], 'in')        # the swing
    rg.r('right_arm').to(tcr + 10, tcr + 24, [0, 0, 0])
    refresh(f.a, rg)
    a = f.a
    bat = bat_point(V, a, tcr, hand(V, a, 'right_arm', tcr))
    # the rival it is cracked at: the soonest one it can reach a block over his head, out of the bat no faster than
    # 1.25 blocks a tick
    best = None
    for vid in ('r_c1', 'r_c2', 'r_c3', 'r_b1', 'r_b2'):
        for tm in range(tcr + 14, tcr + 40):
            tgt = add(E.rider_eye(V, F_[vid].a, tm), [0.0, 1.0, 0.0])
            if math.dist(tgt, bat) / (tm - tcr) <= 1.25:
                if best is None or tm < best[1]:
                    best = (vid, tm, tgt)
                break
    assert best, 'no rival in reach of the Bludger'
    vid, tmiss, target = best
    rc = F_[vid]
    T['miss'] = tmiss
    b1 = sc.notes['b1_keys']
    last_t = b1.k[-1]['t']
    v_in = norm(sub(bat, b1.last()))
    b1.at(tcr - 10, add(bat, v_in, -9.0), 'spline')
    b1.at(tcr - 4, add(bat, v_in, -4.2), 'spline')
    b1.at(tcr, bat, 'linear')
    out = sub(target, bat)
    n = float(tmiss - tcr)
    ts = list(range(tcr + 3, tmiss + 31, 3))
    for t in ts:
        b1.at(t, add(bat, out, (t - tcr) / n), 'spline' if t != ts[-1] else 'linear')
    b1.at(ts[-1] + 0.25, PARK_PITCH, 'linear')
    sc.actor({'id': 'bludger_1', 'type': BLUDGER, 'path': b1.k})
    sc.contacts.append(('o_b1: the bat into the Bludger', 'o_b1', 'bat', tcr, ('ball', 'bludger_1', tcr), 'hit'))
    sc.notes['near_misses'] = [('bludger_1', vid, tmiss)]
    sc.notes['bludger_ok'] = [('bludger_1', 'o_b1', tcr - 6, tcr + 6), ('bludger_1', vid, tmiss - 6, tmiss + 6)]
    sc.notes['ball_paths_extra'].append(('bludger_1', [T['crate'], ts[-1]]))
    # he ducks under it
    rr = rc.rig
    for part, v in (('head', [35, 0, 0]), ('body', [18, 0, 0])):
        rr.r(part).to(tmiss - 8, tmiss - 1, v)
        rr.r(part).to(tmiss + 6, tmiss + 20, [0, 0, 0])
    refresh(rc.a, rr)
    a['items'] = dict(a['items'], mainhand=BAT)
    F_['o_b2'].a['items'] = dict(F_['o_b2'].a['items'], mainhand=BAT)
    F_['r_b1'].a['items'] = dict(F_['r_b1'].a['items'], mainhand=BAT)
    F_['r_b2'].a['items'] = dict(F_['r_b2'].a['items'], mainhand=BAT)


def carry_quaffle(sc, F_, qid, aid, t_in, t_out, scale=THIRD):
    """A Quaffle actor on aid's hand from t_in (in his hand) to t_out (his throw; None: to the end): parked before,
    an offset in his broom frame while he has it (keyed with the hand), world from the throw (the caller keys the
    flight and the park). Returns its Keys."""
    V = view(sc)
    f = F_[aid]
    rh = lambda t: hand(V, f.a, 'right_arm', t)
    k = Keys()
    k.at(0, PARK_PITCH).hold(t_in - 0.25)
    k.at(t_in - 0.001, rh(t_in), 'linear')
    end = sc.L if t_out is None else t_out
    ts = sorted(set([t_in] + list(range(t_in + 8, end - 8, 8)) + [x for x in range(end - 8, end, 2) if x > t_in]))
    for t in ts:
        k.at(t, ride_offset(V, f.b.id, t, rh(t)), 'spline' if t > t_in else None)
    if t_out is not None:
        k.at(t_out - 0.001, ride_offset(V, f.b.id, t_out - 0.001, rh(t_out)), 'linear')
        k.at(t_out, rh(t_out))
    ride = {'on': f.b.id, 'from': t_in}
    if t_out is not None:
        ride['until'] = t_out
    sc.actor({'id': qid, 'type': QUAFFLE, 'scale': scale, 'ride': ride, 'path': k.k})
    return k


def chaser_arms(sc, F_, aid, tc, tp):
    """aid's right arm out for a catch at tc, held at his side with it, thrown forward at tp (None: kept)."""
    V = view(sc)
    f = F_[aid]
    r = reach(sc, f.a, f.rig, 'right_arm', tc - 16, tc - 1, arm_target(V, f.a, f.b.id, tc - 1, CARRY_DIR))
    assert r < 0.06, (aid, r)
    if tp is not None:
        r = reach(sc, f.a, f.rig, 'right_arm', max(tc + 1, tp - 7), tp, arm_target(V, f.a, f.b.id, tp, RELEASE_DIR),
                  'in')
        assert r < 0.06, (aid, r)
        f.rig.r('right_arm').to(tp + 2, tp + 16, [0, 0, 0])
    refresh(f.a, f.rig)


def find_pass(V, F_, a, b, t0, t1, speed=1.0, lo=14, hi=50):
    """The first (throw, catch) ticks from t0 where a can reach b with a flight no faster than `speed` a tick."""
    for tp in range(t0, t1, 2):
        e0 = E.rider_eye(V, F_[a].a, tp)
        for tn in range(tp + lo, tp + hi, 2):
            if math.dist(E.rider_eye(V, F_[b].a, tn), e0) / (tn - tp) <= speed:
                return tp, tn
    return None


def match_play_on(sc, F_):
    """The game goes on after the goal. The rival keeper has a new Quaffle (quaffle_4, in his hand from ring + 30,
    out of every shot) and throws it out to r_c1 (quaffle_5) at ring + 40; r_c1 carries it round his loop and
    passes it to r_c2 (quaffle_6), who carries it on into the Snitch scene."""
    V = view(sc)
    T = sc.notes['T']
    t_get, t_out = T['ring'] + 30, T['ring'] + 40
    rk = F_['r_k']
    r = reach(sc, rk.a, rk.rig, 'right_arm', t_get - 14, t_get, arm_target(V, rk.a, rk.b.id, t_get, CARRY_DIR))
    assert r < 0.06, r
    r = reach(sc, rk.a, rk.rig, 'right_arm', t_out - 7, t_out, arm_target(V, rk.a, rk.b.id, t_out, RELEASE_DIR), 'in')
    assert r < 0.06, r
    rk.rig.r('right_arm').to(t_out + 2, t_out + 16, [0, 0, 0])
    refresh(rk.a, rk.rig)
    p1 = find_pass(V, F_, 'r_k', 'r_c1', t_out, t_out + 1, speed=1.2, hi=70)
    assert p1, 'r_c1 is never in reach of the keeper'
    tc1 = p1[1]
    p2 = find_pass(V, F_, 'r_c1', 'r_c2', tc1 + 60, sc.L - 120)
    assert p2, 'r_c1 never comes in reach of r_c2'
    tp2, tc2 = p2
    chaser_arms(sc, F_, 'r_c1', tc1, tp2)
    chaser_arms(sc, F_, 'r_c2', tc2, None)
    rh = lambda aid, t: hand(V, F_[aid].a, 'right_arm', t)
    k4 = carry_quaffle(sc, F_, 'quaffle_4', 'r_k', t_get, t_out)
    fly1 = parabola(rh('r_k', t_out), rh('r_c1', tc1), t_out, tc1, QG)
    flight_keys(k4, fly1, t_out, tc1 - 0.25)
    k4.at(tc1, PARK_PITCH, 'linear')
    k5 = carry_quaffle(sc, F_, 'quaffle_5', 'r_c1', tc1, tp2)
    fly2 = parabola(rh('r_c1', tp2), rh('r_c2', tc2), tp2, tc2, QG)
    flight_keys(k5, fly2, tp2, tc2 - 0.25)
    k5.at(tc2, PARK_PITCH, 'linear')
    carry_quaffle(sc, F_, 'quaffle_6', 'r_c2', tc2, None)
    for label, aid, t, qid, kind in (('r_k: the new Quaffle in his hand', 'r_k', t_get, 'quaffle_4', 'catch'),
                                     ('r_k: the Quaffle thrown out', 'r_k', t_out, 'quaffle_4', 'throw'),
                                     ('r_c1: the Quaffle into his hand', 'r_c1', tc1, 'quaffle_5', 'catch'),
                                     ('r_c1: the Quaffle away', 'r_c1', tp2, 'quaffle_5', 'throw'),
                                     ('r_c2: the Quaffle into his hand', 'r_c2', tc2, 'quaffle_6', 'catch')):
        sc.contacts.append((label, aid, 'right_arm', t, ('ball', qid, t), kind))
    sc.notes['ball_paths_extra'] += [('quaffle_4', [t_get, tc1]), ('quaffle_5', [tc1, tc2]), ('quaffle_6', [tc2])]
    sc.notes['play_on'] = [('quaffle_3', T['ring'], t_get), ('quaffle_4', t_get, tc1), ('quaffle_5', tc1, tc2),
                           ('quaffle_6', tc2, sc.L + 1)]
    sc.notes['catches'] += [('r_c1', tc1), ('r_c2', tc2)]
    MATCH_FUNCS['carrier'] = 'r_c2'


def bludger_loop(sc, F_, t0, p0, v0):
    """Bludger 2's loop back through the game from (t0, p0, v0): the orbit (of a few candidates) that keeps furthest
    from every rider, joined on by a Hermite. Returns (fn(t), least distance to a rider's seat or head, its
    parameters)."""
    best = None
    ts = list(range(t0, sc.L + 1, 2))
    seats = {aid: [E.track_sample(f.b.path.k, t) for t in ts] for aid, f in F_.items()}
    for y0 in (30.0, 36.0, 42.0):
        for ph in (0.0, 1.2, 2.4, 3.6, 4.8):
            for per in (260.0, 320.0):
                orb = orbit(0.0, 0.0, 30.0, 62.0, y0, per, ph, 1.0, 6.0)
                tj = t0 + 60
                jn = hermite(p0, v0, orb(tj), sub(orb(tj + 1), orb(tj)), t0, tj)
                fn = (lambda jn_, orb_, tj_: (lambda t: jn_(t) if t < tj_ else orb_(t)))(jn, orb, tj)
                if max(math.dist(fn(t), fn(t + 1)) for t in range(t0, tj + 1)) > 1.3:
                    continue                                       # no faster than a Bludger flies elsewhere
                least = 99.0
                for i, t in enumerate(ts):
                    p = fn(t)
                    for aid in seats:
                        s = seats[aid][i]
                        least = min(least, math.dist(p, s), math.dist(p, [s[0], s[1] + 1.0, s[2]]))
                if best is None or least > best[1]:
                    best = (fn, least, (y0, ph, per))
    return best


def match_riders(sc, F_):
    """Where every rider looks: ahead before the whistle; then at the Quaffle in play, the Seekers scanning."""
    V = view(sc)
    T = sc.notes['T']
    seq = sc.notes['quaffle_seq']

    def quaffle(t):
        if t >= T['ring']:                         # the play goes on: the ball after the goal, then the new one
            qid = next(q for q, a, b in sc.notes['play_on'] if a <= t < b)
            return ball_world(V, qid, min(t, T['ring'] + 29) if qid == 'quaffle_3' else t)
        for i, (aid, tc, tp) in enumerate(seq):
            if t < tp or i == 2:
                return ball_world(V, 'quaffle_%d' % (i + 1), t)
        return ball_world(V, 'quaffle_3', t)

    def bludger(aid):
        # a Beater watches the nearest Bludger in play (the Quaffle before they are out)
        def f(t):
            me = E.rider_eye(V, F_[aid].a, t)
            near = [ball_world(V, b, t) for b in ('bludger_1', 'bludger_2')] if t >= T['crate'] + 4 else []
            near = [p for p in near if p[1] > -5]
            return min(near, key=lambda p: math.dist(p, me)) if near else quaffle(t)
        return f
    # the captain and his Seeker, waiting at the front outside the tunnel, turn to each other
    aim_rider(sc, 'o_k', F_['o_k'].a['look'], 104, 180, lambda t: E.rider_eye(V, F_['viewer'].a, t), step=16)
    aim_rider(sc, 'viewer', F_['viewer'].a['look'], 112, 186, lambda t: E.rider_eye(V, F_['o_k'].a, t), step=16)
    for aid, f in F_.items():
        look = f.a['look']
        if aid in ('viewer', 'r_s'):
            continue
        rnd = random.Random('look' + aid)
        aim_rider(sc, aid, look, T['throw'] - 6, sc.L - 4, bludger(aid) if ROLE[aid[2:]] == 'beater' else quaffle,
                  step=16 + rnd.randint(0, 4), gain=0.8)
    # the Seekers: eyes on the whistle and the Snitch out of the crate, then scanning the storm as they circle
    for aid in ('viewer', 'r_s'):
        f = F_[aid]
        look = f.a['look']
        look.append({'t': T['crate'] - 10, 'yaw': 0.0, 'pitch': 0.0})
        look.append({'t': T['crate'] + 10, 'yaw': 0.0, 'pitch': 30.0})
        look.append({'t': T['throw'] + 10, 'yaw': 0.0, 'pitch': 5.0})
        rnd = random.Random('scan' + aid)
        t = T['throw'] + 40
        while t < sc.L - 30:
            look.append({'t': t, 'yaw': round(rnd.uniform(-60, 60), 1), 'pitch': round(rnd.uniform(5, 30), 1),
                         'ease': 'spline'})
            t += rnd.randint(28, 44)
        look.append({'t': sc.L - 8, 'yaw': 25.0, 'pitch': 20.0})


def match_sound(sc, F_, h):
    T = sc.notes['T']
    sc.say(tk(T3['captain']), 'flying_captain', speaker='Wood' if h == 'gryffindor' else 'The Captain')
    sc.say(tk(T3['clean']), 'flying_hooch_clean')
    sc.say(tk(T3['off']), 'flying_comm_off')
    sc.say(tk(T3['comm_goal']), 'flying_comm_goal')
    sc.sfx(T['crate'], 'crate', 1.0, hit=T['crate'], label='the crate thrown open')
    w1 = T['whistle'][1]
    sc.sfx(w1, 'whistle', 1.0, hit=w1, label="Hooch's whistle")
    for aid, tc in sc.notes['catches']:
        sc.sfx(tc, 'quaffle_catch', 0.6, hit=tc, label='%s catches the Quaffle' % aid)
    sc.sfx(T['crack'], 'bludger_crack', 1.0, hit=T['crack'], label='the Beater cracks the Bludger')
    # the crowd: the roar as the home team bursts out, the goal, the gasp, the chant
    te = sc.notes['entrance']
    sc.sfx(te, 'crowd_swell', 1.0)
    react(sc, te + 2, h, 'goal', ('entrance', te), 70)
    for o in HOUSES:
        if o != h:
            react(sc, te + 6, o, 'cheer', ('entrance', te), 50)
    tg = sc.notes['goal_tick']
    sc.sfx(tg + 1, 'crowd_swell', 1.0)
    react(sc, tg + 2, h, 'goal', ('goal', tg), 80)
    for o in HOUSES:
        if o not in (h, rival(h)):
            react(sc, tg + 6, o, 'cheer', ('goal', tg), 60)
    tm = sc.notes['near_misses'][0][2]
    sc.sfx(tm + 4, 'crowd_gasp', 0.9)
    for o in HOUSES:
        react(sc, tm + 6, o, 'gasp', ('near miss', tm))
    tc = tk(T3['chant'])
    sc.sfx(tc, 'crowd_chant', 0.7)
    react(sc, tc, h, 'chant', ('goal', tg), 150)


def check_crowd_gap(sc, scene, problems):
    """Task 5's exclusion: every real actor at least 0.7 from every crowd seat (within 1.5 of its height)."""
    seats = []
    for e in scene['events']:
        if e.get('kind') == 'effect' and e.get('id') == 'crowd':
            seats.extend(p for p, _ in E.crowd_seats(e))
    best = 9.0
    for a in scene['actors']:
        if a.get('type') != 'player':
            continue
        for t in range(0, scene['length'] + 1, 5):
            p = E.rider_root(scene, a, t)[0]
            for s in seats:
                if abs(s[1] - p[1]) <= 1.5:
                    d = math.hypot(s[0] - p[0], s[2] - p[2])
                    best = min(best, d)
                    if d < 0.7:
                        problems.append('%s: %s is %.2f from a crowd seat at t %d' % (sc.sid, a['id'], d, t))
                        return best
    return best


def ring_crossing(scene, qid, t0, t1, hoop):
    """Where and when the Quaffle crosses the hoop's plane (z = the hoop's z) between t0 and t1: (tick, distance
    from the ring's centre in the plane), or None."""
    c = hoop['centre']
    prev = ball_world(scene, qid, t0)
    t = t0
    while t < t1:
        t2 = t + 0.05
        p = ball_world(scene, qid, t2)
        if (prev[2] - c[2]) * (p[2] - c[2]) <= 0 and abs(p[2] - prev[2]) > 1e-9:
            f = (c[2] - prev[2]) / (p[2] - prev[2])
            q = lerp(prev, p, f)
            return t + 0.05 * f, math.hypot(q[0] - c[0], q[1] - c[1])
        prev, t = p, t2
    return None


def check_quaffle_ring(sc, scene, problems):
    """The Quaffle passes inside the ring it scores through, at least 0.3 from the rim; the goal tick it gives is
    the one the crowd answers."""
    T = sc.notes['T']
    hoop = QA['hoops'][4]
    x = ring_crossing(scene, 'quaffle_3', T['shot'], T['ring'] + 20, hoop)
    if x is None:
        problems.append('%s: the Quaffle never crosses the hoop' % sc.sid)
        return None
    margin = hoop['radius'] - x[1]
    if margin < 0.3:
        problems.append('%s: the Quaffle passes %.2f inside the rim (at least 0.3)' % (sc.sid, margin))
    if abs(math.ceil(x[0]) - sc.notes['goal_tick']) > 1:
        problems.append('%s: the goal is at t %.1f but the crowd answers t %d' % (sc.sid, x[0], sc.notes['goal_tick']))
    return round(margin, 3)


def check_reactions(sc, scene, problems):
    """Assert 7, the crowd: every crowd_react in the JSON belongs to a reaction with a cause; a goal/cheer within 0.5 s
    after its goal or entrance, a gasp within 1 s of its near miss, the erupt within 0.3 s of the catch; nobody
    reacts before the cause."""
    limits = {'goal': 10, 'cheer': 10, 'gasp': 20, 'erupt': 6, 'chant': 200, 'sit': 10}
    n_events = sum(1 for e in scene['events'] if e.get('id') == 'crowd_react')
    n_rec = sum(len(CROWD_BOXES[hh]) for _, _, hh, _ in sc.reactions)
    if n_events != n_rec:
        problems.append('%s: %d crowd_react events, %d with a recorded cause' % (sc.sid, n_events, n_rec))
    worst = 0
    for t, style, hh, (kind, tc) in sc.reactions:
        d = t - tc
        worst = max(worst, d)
        if d < 0 or d > limits[style]:
            problems.append('%s: the %s %s at t %d is %d ticks after its %s (t %d)' % (sc.sid, hh, style, t, d, kind, tc))
    for bid, aid, tm in sc.notes.get('near_misses', []):
        if not any(st == 'gasp' and 0 <= t - tm <= 20 for t, st, _, _ in sc.reactions):
            problems.append('%s: no gasp follows the near miss at t %d' % (sc.sid, tm))
    return worst


def player_points(scene, a, t):
    root = E.rider_root(scene, a, t)[0]
    if E.broom_rider_at(scene, a, t) is not None:
        eye = E.rider_eye(scene, a, t)
    else:
        eye = [root[0], root[1] + E.actor_eye_height(a, t), root[2]]
    return root, eye


def check_bludgers(sc, scene, problems):
    """No Bludger comes within 1.5 of a player except at its hit or its near miss, and each near miss really passes
    within 1.5 of its player. Returns the least distance outside those windows."""
    best = 9.0
    ok = sc.notes.get('bludger_ok', [])
    players = [a for a in scene['actors'] if a.get('type') == 'player']
    for b in [a for a in scene['actors'] if a['type'] == BLUDGER]:
        for t in range(0, scene['length'] + 1):
            p = ball_world(scene, b['id'], t)
            if p[1] < -5:
                continue
            for a in players:
                root, eye = player_points(scene, a, t)
                d = min(math.dist(p, root), math.dist(p, eye))
                if any(bb == b['id'] and aa == a['id'] and t0 <= t <= t1 for bb, aa, t0, t1 in ok):
                    continue
                best = min(best, d)
                if d < 1.5:
                    problems.append('%s: %s passes %.2f from %s at t %d with no cause' % (sc.sid, b['id'], d, a['id'], t))
                    return best
    for bid, aid, tm in sc.notes.get('near_misses', []):
        a = E.actor_by_id(scene, aid)
        d = min(math.dist(ball_world(scene, bid, tm), q) for q in player_points(scene, a, tm))
        if d > 1.5:
            problems.append('%s: the near miss at t %d is %.2f away' % (sc.sid, tm, d))
    return best


def heading_fn(pos_fn, sigma=10.0, dt=4.0):
    """A smoothed horizontal heading (unit [x, 0, z]) of a moving point."""
    def d(t):
        v = sub(pos_fn(t + dt), pos_fn(t - dt))
        return norm([v[0], 0.0, v[2]])
    return smoothed(d, sigma)


def extrap(fn, L):
    """fn past the scene's end carried on at its last velocity (a smoothing window at the end then sees no stop)."""
    def f(t):
        if 0 <= t <= L:
            return fn(t)
        a, b = (fn(L - 1), fn(L)) if t > L else (fn(1), fn(0))
        return [b[i] + (b[i] - a[i]) * abs(t - (L if t > L else 0)) for i in range(3)]
    return f


def follow_cam(subject, offset, sigma=12.0, L=None):
    """A camera riding with a moving subject: subject(t) smoothed, plus offset(heading, right) in the subject's
    heading frame [back/forward, up, right]."""
    s = smoothed(extrap(subject, L) if L else subject, sigma)
    hd = heading_fn(s, sigma + 4.0)

    def cam(t):
        h = hd(t)
        r = [-h[2], 0.0, h[0]]
        o = offset(t)
        return [s(t)[0] + h[0] * o[0] + r[0] * o[2], s(t)[1] + o[1], s(t)[2] + h[2] * o[0] + r[2] * o[2]]
    return cam, s, hd


def best_box(h, ymax=30.0):
    """The viewer's house's biggest crowd box on the low gallery (its stands answer the goal on screen)."""
    boxes = [b for b in CROWD_BOXES[h] if b[0][1] < ymax]
    return max(boxes, key=lambda b: b[1][0] * b[1][2])


def match_camera(sc, F_):
    V = view(sc)
    T = sc.notes['T']
    eye = lambda aid: (lambda t: E.rider_eye(V, F_[aid].a, t))
    pos = lambda aid: (lambda t: F_[aid].pos(t))
    A = sc.notes['attack']
    zg = gate('own')
    # M1 0-5 the storm over the stadium: high over the west stand, from the castle lit through the rain down to the
    # empty pitch
    pf = move([-75.0, 78.0, -48.0], [-66.0, 66.0, -32.0], 0, 100)
    sc.shot(0, 100, pf, turn_look(pf, [150.0, 60.0, 0.0], [10.0, 16.0, 10.0], 0, 100, 60.0), ramp(76.0, 70.0, 0, 100), 4)
    # M2 5-9.4 outside the west stand, the team waiting astride in the rain: the captain to his Seeker
    mid = lambda t: lerp(eye('o_k')(t), eye('viewer')(t), 0.5)
    pf = move([-88.9, 2.15, zg + 0.05], [-89.1, 2.1, zg], 100, 188)
    sc.shot(100, 188, pf, smoothed(mid, 6.0), ramp(48.0, 44.0, 100, 188), 6)
    # M3 9.4-13.4 into the roar: low on the grass by the tunnel mouth, the team bursting out past the camera
    pf = move([-69.0, 1.1, zg - 8.5], [-68.4, 1.3, zg - 8.1], 188, 268)
    first = lambda t: lerp([-77.0, 2.2, zg], eye('viewer')(t), smooth((t - 206) / 36.0))
    sc.shot(188, 268, pf, smoothed(first, 10.0), ramp(64.0, 72.0, 188, 268), 4)
    # M4 13.4-17 wide from high in the east: both teams sweep round the stadium under the storm
    every = lambda t: [sum(F_[a].pos(t)[i] for a in OWN + RIV) / 14.0 for i in range(3)]
    pf = move([42.0, 36.0, -4.0], [38.0, 34.0, 6.0], 268, 340)
    sc.shot(268, 340, pf, smoothed(every, 10.0), const(72.0), 6)
    # M5 17-21 under the two lines, behind Madam Hooch: "I want a clean game, from all of you."
    pf = move([-3.4, 1.15, 2.4], [-3.0, 1.2, 2.0], 340, 420)
    sc.shot(340, 420, pf, move([1.5, 4.0, 0.5], [1.2, 4.6, 0.5], 340, 420), ramp(76.0, 73.0, 340, 420), 8)
    # M6 21-23.4 the crate: the lid thrown open, the Bludgers and the Snitch burst out, the whistle, the Quaffle up
    pf = move([7.0, 0.9, -2.2], [6.8, 1.1, -2.0], 420, 468)
    cr = QA['ball_crate']
    up = lambda t: lerp([cr[0], 0.9, cr[2]], [2.5, 9.0, 0.3], smooth((t - 422) / 40.0))
    sc.shot(420, 468, pf, smoothed(up, 3.0), ramp(58.0, 66.0, 420, 468), 4)
    # M7a 23.4-27.4 alongside the Chasers as they race up the pitch, passing
    sA = smoothed(A, 14.0)
    sc.shot(468, 548, lambda t: add(sA(t), [14.0, 3.5, -7.0]), lambda t: add(sA(t), [-2.0, 0.0, 4.0]), const(66.0), 4)
    # M7b 27.4-29.2 behind the far middle hoop: the shot comes through the ring at the camera
    ring = QA['hoops'][4]['centre']
    pf = move([1.4, 51.6, 117.5], [1.3, 51.5, 117.0], 548, 584)
    sc.shot(548, 584, pf, smoothed(lambda t: lerp(eye('o_c3')(t), ring, smooth((t - 552) / 20.0)), 3.0),
            const(52.0), 4)
    # M8 29.2-32 the boy's house on its feet in the stand, roaring
    c, half = best_box(sc.h)
    toward = norm([-c[0], 0.0, -c[2]])
    side = [-toward[2], 0.0, toward[0]]
    base = [c[0] + toward[0] * 21.0, c[1] + 3.0, c[2] + toward[2] * 21.0]
    pf = move(add(base, side, -1.0), add(base, side, 1.0), 584, 640)
    sc.shot(584, 640, pf, const([c[0], c[1] + 1.4, c[2]]), ramp(56.0, 52.0, 584, 640), 8)
    sc.notes['stand_shot'] = (584, 640, c)
    # M10 32-34.5 the hunt: behind the boy, high over the game and the storm
    cam, s_v, h_v = follow_cam(eye('viewer'), lambda t: [-4.4, 1.1, 1.3], 16.0, sc.L)
    sc.shot(640, 690, cam, lambda t: add(add(s_v(t), h_v(t), 14.0), [0.0, -2.0, 0.0]), const(62.0), 4)
    # M9 34.5-36.6 the Bludger: behind o_b1 as it comes at him; he cracks it away at a rival's head
    tcr = T['crack']
    vid, tmiss = sc.notes['near_misses'][0][1], sc.notes['near_misses'][0][2]
    b1 = lambda t: ball_world(V, 'bludger_1', t)
    bat = b1(tcr)
    outd = norm(sub(b1(tmiss), bat))
    perp = norm([-outd[2], 0.0, outd[0]])
    camp = add(add(add(bat, outd, -5.0), perp, 2.0), [0.0, 1.2, 0.0])
    sc.notes['whip_cam'] = camp
    cam, s_b, h_b = follow_cam(eye('o_b1'), lambda t: [-4.6, 1.3, 2.2], 14.0, sc.L)
    focus = lambda t: lerp(add(s_b(t), h_b(t), 6.0), eye(vid)(t), 0.7 * smooth((t - tcr + 10) / float(tmiss - tcr + 26)))
    sc.shot(690, 732, cam, smoothed(lambda t: focus(min(t, tmiss + 4)), 10.0), ramp(62.0, 68.0, 690, 732), 4)
    # M11 36.6-41 the rival Seeker drifts up on the boy's shoulder: both, from ahead and outside the circle
    two = lambda t: lerp(eye('viewer')(t), eye('r_s')(t), 0.5)
    cam, s2, h2 = follow_cam(two, lambda t: [9.5, 1.0, -6.0], 16.0, sc.L)
    sc.shot(732, 820, cam, smoothed(two, 8.0), ramp(56.0, 52.0, 732, 820), 4)
    # M12 41-45 wide over the far stands: the crowd, the rain sheets, the game below, the lightning
    pf = move([60.0, 64.0, -88.0], [56.0, 67.0, -78.0], 820, 900)
    sc.shot(820, 900, pf, move([0.0, 30.0, 10.0], [0.0, 34.0, 14.0], 820, 900), const(74.0), 6)
    sc.notes['strike_shots'] = [(820, 900, 'M12, which promises it')]
    # M13a 45-47.5 under him, looking up past him into the storm, searching
    cam, s_v, h_v = follow_cam(eye('viewer'), lambda t: [-3.0, -1.6, -1.6], 16.0, sc.L)
    sc.shot(900, 950, cam, lambda t: add(add(s_v(t), h_v(t), 10.0), [0.0, 2.5, 0.0]), const(60.0), 4)
    # M13b 47.5-50 his face, the rain on it, the eyes searching: a slow push in
    cam, s_v, h_v = follow_cam(eye('viewer'), lambda t: [1.6 - 0.25 * smooth((t - 950) / 50.0), 0.05, 0.5], 8.0, sc.L)
    sc.shot(950, sc.L, cam, s_v, ramp(48.0, 40.0, 950, sc.L), 4)
    whoosh(sc, 'broom_viewer', 196, 268)
    E_ = lambda aid: (lambda sc_, t: E.rider_eye(sc_, E.actor_by_id(sc_, aid), t))
    sc.framing += [(104, 186, E_('o_k'), 'the captain (M2)'), (104, 186, E_('viewer'), 'the boy (M2)'),
                   (232, 250, E_('viewer'), 'the boy bursts out (M3)'),
                   (344, 416, lambda sc_, t: [0.5, 1.6, 0.5], 'Madam Hooch (M5)'),
                   (422, 432, lambda sc_, t: [cr[0], 0.8, cr[2]], 'the crate (M6)'),
                   (476, 546, lambda sc_, t: ball_world(sc_, 'quaffle_1' if t < T['pass1'] else 'quaffle_2' if
                                                         t < T['pass2'] else 'quaffle_3', t), 'the Quaffle (M7a)'),
                   (560, 574, lambda sc_, t: ball_world(sc_, 'quaffle_3', t), 'the shot (M7b)'),
                   (588, 638, lambda sc_, t: [c[0], c[1] + 1.0, c[2]], 'the stand (M8)'),
                   (644, 688, E_('viewer'), 'the boy hunting (M10)'),
                   (tcr - 4, tcr + 2, lambda sc_, t: ball_world(sc_, 'bludger_1', t), 'the crack (M9)'),
                   (tmiss - 2, tmiss + 2, E_(vid), 'the near miss (M9)'),
                   (762, 816, E_('viewer'), 'the boy (M11)'), (762, 816, E_('r_s'), 'the rival Seeker (M11)'),
                   (904, 946, E_('viewer'), 'the boy (M13a)'), (954, sc.L - 2, E_('viewer'), 'his face (M13b)')]


# ================================================================ the Snitch: flying_<h>_snitch
T4 = {'leave': 100, 'top': 142, 'dive_full': 170, 'pull': 220, 'level': 240, 'catch': 280, 'hover': 340,
      'r_top': 160, 'r_pull': 212, 'r_pull_end': 232, 'seeker': 108, 'fp': (246, 294), 'raise': (310, 344),
      'ring': 430, 'close': 760}
SNITCH_CRY = 0.9                        # the dive's speed, blocks a tick


def snitch_scene(h, world, match_scene=None):
    sc = Sc('flying_%s_snitch' % h, 'The Snitch', L4, world, h)
    sc.notes['pitch'] = True
    kits = team_kits(h)
    F_ = {aid: Flier(sc, aid, kits[aid][0], kits[aid][1]) for aid in OWN + RIV}
    dc = QA['dive_column']
    cx, cz = dc['at'][0], dc['at'][2]
    sc.notes['column'] = (cx, cz)
    off = int(round(L3 * 20))
    carry = lambda aid: (lambda s: MATCH_FUNCS[aid](s + off))
    # ---- the boy: on round his circle, away to the top of the column, down it, out above the grass, the chase
    v = F_['viewer']
    follow(F_, 'viewer', carry('viewer'), 0, T4['leave'], 10, 'spline')
    p_top = [cx + 1.3, 63.0, cz]
    # he breaks off his circle in one sweeping turn up to the top of the column, heading in toward it
    v.at(112, [cx + 3.5, 61.0, cz - 16.5]).at(124, [cx + 0.5, 62.0, cz - 11.5]).at(134, [cx + 0.7, 62.8, cz - 5.5])
    v.at(T4['top'], p_top)
    full = [cx + 1.3, p_top[1] - 9.0, cz]
    dive_in = hermite(p_top, [0.0, -0.1, 0.3], full, [0.0, -SNITCH_CRY, 0.0], T4['top'], T4['dive_full'])
    for t in range(T4['top'] + 4, T4['dive_full'], 4):
        v.at(t, dive_in(t))
    y_line = lambda t: full[1] - SNITCH_CRY * (t - T4['dive_full'])
    for t in range(T4['dive_full'], T4['pull'] + 1, 6):
        v.at(t, [cx + 1.3, y_line(t), cz])
    p_pull = [cx + 1.3, y_line(T4['pull']), cz]
    p_lev = [cx + 2.3, 1.6, cz + 8.5]
    v_lev = [0.04, 0.0, 0.62]
    pull = hermite(p_pull, [0.0, -SNITCH_CRY, 0.0], p_lev, v_lev, T4['pull'], T4['level'])
    for t in range(T4['pull'] + 4, T4['level'] + 1, 4):
        v.at(t, pull(t))
    p_hov = [cx + 3.8, 3.0, cz + 38.5]
    level = hermite(p_lev, v_lev, p_hov, [0.0, 0.0, 0.0], T4['level'], T4['hover'])
    for t in range(T4['level'] + 4, T4['hover'], 4):
        v.at(t, level(t))
    v.at(T4['hover'], p_hov, 'in_out').at(sc.L, p_hov, 'in_out')
    v.hover.append((T4['hover'], sc.L, 0.0))
    sc.notes['viewer_line'] = {'y': y_line, 'pull': pull, 'level': level, 'hover': p_hov}
    # ---- the rival Seeker: on round, then the column beside the boy, catching him up; he pulls out first
    r = F_['r_s']
    follow(F_, 'r_s', carry('r_s'), 0, 112, 10, 'spline')
    r_top = [cx - 1.3, 64.6, cz]
    r.at(140, [-18.0, 62.0, 42.5]).at(T4['r_top'], r_top)
    y_r = lambda t: y_line(t) + 7.5 * (1.0 - smooth((t - T4['r_top']) / 40.0)) if t >= T4['dive_full'] else None
    for t in range(T4['r_top'] + 6, T4['r_pull'] + 1, 6):
        yy = y_r(t) if t >= T4['dive_full'] else r_top[1] - (r_top[1] - y_r(T4['dive_full'])) * smooth(
            (t - T4['r_top']) / float(T4['dive_full'] - T4['r_top']))
        r.at(t, [cx - 1.3, yy, cz])
    rp = [cx - 1.3, y_r(T4['r_pull']), cz]
    rpe = add(rp, [-1.5, -5.0, -9.0])
    rpull = hermite(rp, [0.0, -SNITCH_CRY, 0.0], rpe, [0.0, 0.15, -0.6], T4['r_pull'], T4['r_pull_end'])
    for t in range(T4['r_pull'] + 4, T4['r_pull_end'] + 1, 4):
        r.at(t, rpull(t))
    r.at(270, [cx - 3.0, 16.0, cz - 30.0]).at(320, [cx - 1.0, 21.0, cz - 46.0])
    r.at(370, [cx + 2.0, 22.0, cz - 50.0], 'in_out').at(sc.L, [cx + 2.5, 22.4, cz - 50.5], 'in_out')
    r.hover.append((370, sc.L, 0.0))
    sc.notes['rival_pull'] = T4['r_pull']
    # ---- the rest: the game goes on round them; once he has it his own team swoops in round him
    ring = ['o_c1', 'o_c2', 'o_c3', 'o_b1', 'o_b2']
    for aid in OWN + RIV:
        if aid in ('viewer', 'r_s'):
            continue
        f = F_[aid]
        if aid in ('o_k', 'r_k'):
            p = (final_state(match_scene, 'broom_' + aid)['pos'] if match_scene else
                 [0.0, 46.0, -100.0] if aid == 'o_k' else [0.0, 48.0, 101.0])
            f.at(0, p, 'in_out').at(sc.L, add(p, [0.6, 0.2, 0.0]), 'in_out')
            f.hover.append((0, sc.L, 0.0 if aid == 'o_k' else 180.0))
            continue
        if aid in ring:
            k = ring.index(aid)
            follow(F_, aid, carry(aid), 0, T4['catch'] + 30, 10, 'spline')
            th = math.radians(200 + 72 * k)
            slot = [p_hov[0] + 6.2 * math.cos(th), p_hov[1] + 0.6 + 0.5 * (k % 3), p_hov[2] + 6.2 * math.sin(th)]
            t0 = T4['catch'] + 30
            gl = hermite(carry(aid)(t0), sub(carry(aid)(t0 + 1), carry(aid)(t0)), slot, [0.0, 0.0, 0.0], t0,
                         T4['ring'] + 10)
            for t in range(t0 + 6, T4['ring'] + 10, 6):
                f.at(t, gl(t))
            f.at(T4['ring'] + 10, slot, 'in_out').at(sc.L, add(slot, [0.0, 0.3, 0.0]), 'in_out')
            f.hover.append((T4['ring'], sc.L, unwrap(yaw_to(p_hov[0] - slot[0], p_hov[2] - slot[2]), 0.0)))
        else:
            follow(F_, aid, carry(aid), 0, sc.L, 10, 'spline')
    for f in F_.values():
        f.build()
    snitch_viewer(sc, F_)
    snitch_play(sc, F_)
    snitch_ball(sc, F_)
    snitch_riders(sc, F_)
    for f in F_.values():
        refresh(f.a, f.rig)
    snitch_camera(sc, F_)
    snitch_sound(sc, F_, h)
    storm(sc, 'snitch')
    score(sc, '_snitch')
    sc.ev(T4['close'], 'effect', 'eyes_close', at=[0, 0, 0], duration=40)
    scene = sc.scene(pitch_set(), QP.RADIUS, end_action='wake')
    sc.notes.update(moments=[check_moments_snitch], extra_checks=[('crowd gap', check_crowd_gap), ('reactions', check_reactions),
                                  ('snitch framed', check_snitch_seen), ('bludgers', check_bludgers),
                                  ('lightning', check_lightning)],
                    dives=[('broom_viewer', 'viewer', T4['pull'] - 10, T4['catch'] + 20, 0.0),
                           ('broom_r_s', 'r_s', T4['r_pull'] - 10, T4['r_pull_end'] + 10, 0.0)],
                    ball_paths=[('snitch', [T4['catch']])])
    return sc, scene


def snitch_viewer(sc, F_):
    """His right hand forward off the handle for the catch; the Snitch in it from the catch; held up high."""
    V = view(sc)
    f = F_['viewer']
    T_C = T4['catch']
    r = reach(sc, f.a, f.rig, 'right_arm', T4['fp'][0] - 18, T_C - 2,
              arm_target(V, f.a, f.b.id, T_C - 2, [-0.12, -0.04, 1.0]))
    assert r < 0.05, r
    r = reach(sc, f.a, f.rig, 'right_arm', T4['raise'][0], T4['raise'][1],
              arm_target(V, f.a, f.b.id, T4['raise'][1], [-0.18, 1.0, 0.12]))
    assert r < 0.06, r
    refresh(f.a, f.rig)
    f.a['actions'] = [{'t': T_C, 'do': 'hold', 'item': SNITCH_ITEM}]
    sc.notes['catch_hand'] = hand(V, f.a, 'right_arm', T_C)


def snitch_play(sc, F_):
    """The game goes on round the hunt, carried over from the match: r_c2 still has the Quaffle (quaffle_6 there),
    Bludger 2 still loops through the game."""
    V = view(sc)
    off = int(round(L3 * 20))
    aid = MATCH_FUNCS.get('carrier')
    if aid:
        f = F_[aid]
        r = reach(sc, f.a, f.rig, 'right_arm', 0, 2, arm_target(V, f.a, f.b.id, 2, CARRY_DIR))
        assert r < 0.06, r
        refresh(f.a, f.rig)
        rh = lambda t: hand(V, f.a, 'right_arm', t)
        k = Keys()
        for t in [0] + list(range(2, sc.L, 8)) + [sc.L]:
            k.at(t, ride_offset(V, f.b.id, t, rh(t)), 'spline' if t > 0 else None)
        sc.actor({'id': 'quaffle', 'type': QUAFFLE, 'scale': THIRD, 'ride': f.b.id, 'path': k.k})
    loop = MATCH_FUNCS.get('bludger_2')
    if loop:
        k = Keys()
        for t in range(0, sc.L + 1, 4):
            k.at(t, loop(t + off), 'spline' if t else None)
        sc.actor({'id': 'bludger_2', 'type': BLUDGER, 'path': k.k})


def snitch_ball(sc, F_):
    """The Snitch: fluttering low by the foot of the column, then away low across the grass just ahead of him,
    closing into his hand at the catch (parked the same tick, the item in his hand)."""
    V = view(sc)
    cx, cz = sc.notes['column']
    T_C = T4['catch']
    va = F_['viewer'].a
    base = [cx + 2.0, 3.2, cz + 2.5]
    hov = lambda t: [base[0] + 1.2 * math.sin(0.11 * t), base[1] + 0.5 * math.sin(0.17 * t + 1.0),
                     base[2] + 1.0 * math.cos(0.13 * t)]
    hn = lambda t: hand(V, va, 'right_arm', t)
    tb = 226
    d0 = sub(hov(tb), hn(tb))
    fwd = norm(sub(hn(T_C), hn(T_C - 10)))

    def chase(t):
        u = (t - tb) / float(T_C - tb)
        g = 1.0 - smoother(u)
        bump = math.sin(math.pi * u) * 3.0
        wig = 0.25 * math.sin(0.9 * t) * math.sin(math.pi * u)
        return [hn(t)[i] + d0[i] * g + fwd[i] * bump + (wig if i == 1 else 0.0) for i in range(3)]
    k = Keys().at(0, hov(0))
    for t in range(3, tb, 3):
        k.at(t, hov(t), 'spline')
    blend = lambda t: lerp(hov(t), chase(t), smooth((t - tb) / 10.0))
    for t in range(tb, T_C - 1, 2):
        k.at(t, blend(t) if t < tb + 10 else chase(t), 'spline')
    k.at(T_C - 0.25, sc.notes['catch_hand'], 'linear')
    k.at(T_C, PARK_PITCH, 'linear')
    sc.actor({'id': 'snitch', 'type': SNITCH, 'path': k.k})
    sc.contacts.append(('viewer: his hand closes on the Snitch', 'viewer', 'right_arm', T_C,
                        ('ball', 'snitch', T_C - 0.25), 'catch'))
    sc.notes['snitch_hover'] = hov


def snitch_riders(sc, F_):
    V = view(sc)
    sn = lambda t: ball_world(V, 'snitch', min(t, T4['catch'] - 1))
    vl = F_['viewer'].a['look']
    rnd = random.Random('scan2')
    t = 8
    while t < 92:
        vl.append({'t': t, 'yaw': round(rnd.uniform(-50, 50), 1), 'pitch': round(rnd.uniform(5, 25), 1),
                   'ease': 'spline'})
        t += rnd.randint(28, 40)
    vl.append({'t': 96, 'yaw': vl[-1]['yaw'], 'pitch': vl[-1]['pitch']})
    aim_rider(sc, 'viewer', vl, 104, T4['fp'][0] - 8, sn, step=10, max_pitch=70.0)
    vl.append({'t': T4['fp'][0] - 2, 'yaw': -4.0, 'pitch': 6.0})
    vl.append({'t': T4['fp'][1] + 6, 'yaw': -4.0, 'pitch': 6.0})
    aim_rider(sc, 'viewer', vl, T4['raise'][0], T4['raise'][1] + 20,
              lambda t: hand(V, F_['viewer'].a, 'right_arm', t), step=10, max_pitch=60.0)
    rl = F_['r_s'].a['look']
    aim_rider(sc, 'r_s', rl, 120, T4['r_pull'], sn, step=10, max_pitch=70.0)
    boy = lambda t: E.rider_eye(V, F_['viewer'].a, t)
    has = {a['id'] for a in V['actors']}
    play = lambda t: ball_world(V, 'quaffle', t) if 'quaffle' in has else boy(t)

    def watch(aid):
        # the play (the Beaters the Bludger) while the game goes on; from the dive, everyone turns to the Seekers
        me = lambda t: E.rider_eye(V, F_[aid].a, t)
        ball = (lambda t: ball_world(V, 'bludger_2', t)) if ROLE[aid[2:]] == 'beater' and 'bludger_2' in has else play
        w = lambda t: smooth((t - T4['top']) / 24.0)
        return lambda t: add(me(t), slerp_dir(sub(ball(t), me(t)), sub(boy(t), me(t)), w(t)), 20.0)
    for aid, f in F_.items():
        if aid in ('viewer', 'r_s'):
            continue
        aim_rider(sc, aid, f.a['look'], 20, sc.L - 4, watch(aid), step=18, gain=0.7)


def snitch_sound(sc, F_, h):
    T_C = T4['catch']
    sc.say(T4['seeker'], 'flying_comm_seeker')
    sc.sfx(64, 'snitch_flutter', 0.5, loop=True, duration=T_C - 64)
    sc.sfx(T_C, 'quaffle_catch', 0.5, hit=T_C, label='the Snitch caught')
    sc.sfx(T_C + 1, 'crowd_erupt', 1.0)
    for o in HOUSES:
        if o != rival(h):
            react(sc, T_C + 2, o, 'erupt', ('catch', T_C), 200)
    react(sc, T_C + 4, rival(h), 'sit', ('catch', T_C))
    sc.sfx(T_C + 80, 'crowd_chant', 0.8)
    react(sc, T_C + 180, h, 'chant', ('catch', T_C), 160)


def check_moments_snitch(sc, scene, problems):
    """Assert 7 in the Snitch scene: once the rival Seeker has pulled out (r_pull_end) he stops diving: his broom
    never sinks faster than 0.05 blocks a tick again, and its pitch is out of the tuck (under TUCK_FROM, 20)."""
    b = E.actor_by_id(scene, 'broom_r_s')
    for t in range(T4['r_pull_end'], scene['length'] + 1):
        vy = (E.track_sample(b['path'], t + 1)[1] - E.track_sample(b['path'], t - 1)[1]) / 2.0
        pitch = E.broom_pose(scene, b, t)['pitch']
        if vy < -0.05 or pitch >= 20.0:
            problems.append('%s: the rival Seeker still dives at t %d (%.2f blocks a tick, pitch %.0f)' % (
                sc.sid, t, vy, pitch))
            break


def check_snitch_seen(sc, scene, problems):
    """The Snitch is in the 16:9 frame for at least 0.6 s (12 ticks running) before the dive begins."""
    best, run = 0, 0
    for t in range(0, T4['top'] + 1):
        c = E.engine_cam(scene['camera'], t)
        lk, fov = E.engine_look(scene, scene['camera'], t)
        p = ball_world(scene, 'snitch', t)
        if E.in_view(c, lk, fov, p, aspect=16 / 9.0) and math.dist(c, p) < 12.0:
            run += 1
            best = max(best, run)
        else:
            run = 0
    if best < 12:
        problems.append('%s: the Snitch is in frame only %d ticks running before the dive' % (sc.sid, best))
    return best


def snitch_camera(sc, F_):
    V = view(sc)
    eye = lambda aid: (lambda t: E.rider_eye(V, F_[aid].a, t))
    cx, cz = sc.notes['column']
    vlin = sc.notes['viewer_line']
    p_hov = vlin['hover']
    sn = sc.notes['snitch_hover']
    T_C = T4['catch']
    # N1 0-3.2 high over the game, behind the boy as he circles, hunting
    cam, s_v, h_v = follow_cam(eye('viewer'), lambda t: [-4.5, 1.2, 1.4], 16.0, sc.L)
    sc.shot(0, 64, cam, lambda t: add(add(s_v(t), h_v(t), 14.0), [0.0, -3.0, 0.0]), const(64.0), 4)
    # N2 3.2-5.2 the glint: low by the foot of the column, the Snitch hangs in the rain, a flash of gold
    sp = smoothed(sn, 6.0)
    pf = move([cx + 4.4, 3.9, cz + 0.6], [cx + 4.3, 3.85, cz + 0.8], 64, 104)
    sc.shot(64, 104, pf, sp, ramp(38.0, 34.0, 64, 104), 4)
    # N3 5.2-7.2 his face: he has seen it, and swings away toward it
    cam, s_v, h_v = follow_cam(eye('viewer'), lambda t: [4.4, 0.35, 1.6], 26.0, sc.L)
    sc.shot(104, 144, cam, smoothed(eye('viewer'), 9.0), ramp(40.0, 36.0, 104, 144), 4)
    # N4 7.2-9 from the grass at the foot of the column, looking straight up it: he tips over and comes down at us
    pf = move([cx + 6.5, 1.3, cz + 6.0], [cx + 6.4, 1.35, cz + 5.9], 144, 180)
    sc.shot(144, 180, pf, smoothed(lambda t: lerp(eye('viewer')(t), [cx, 30.0, cz], 0.3), 8.0),
            ramp(70.0, 66.0, 142, 180), 4)
    # N5 9-10.8 falling beside them: the two Seekers side by side down the column, the grass rushing up
    mid = lambda t: lerp(eye('viewer')(t), eye('r_s')(t), 0.5)
    ym = smoothed(lambda t: [0.0, vlin['y'](t), 0.0], 6.0)
    sc.shot(180, 216, lambda t: [cx + 0.3, ym(t)[1] + 1.4, cz + 5.6], smoothed(mid, 6.0), const(64.0), 4)
    # N6 10.8-12.3 out of the dive just over the grass, the rival peeling away above
    pf = move([cx + 6.0, 0.9, cz + 14.0], [cx + 6.1, 0.92, cz + 14.2], 216, 246)
    sc.shot(216, 246, pf, smoothed(eye('viewer'), 9.0), ramp(60.0, 64.0, 216, 246), 4)
    # N7 12.3-14.7 FIRST PERSON: skimming the grass, the Snitch just ahead, his hand closes on it
    vb_ = E.actor_by_id(V, 'broom_viewer')
    ahead = [-0.14, math.sin(math.radians(-30.0)), math.cos(math.radians(-30.0))]
    eye_v = lambda t: head_eye(V, E.actor_by_id(V, 'viewer'), t, 0.12)
    aim = smoothed(lambda t: add(eye_v(t), E._mat_vec(E.broom_pose(V, vb_, t)['m'], ahead), 4.0), 5.0)
    first_person(sc, V, 'viewer', T4['fp'][0], T4['fp'][1], fwd=0.12, fov=78.0, look_fn=aim, sigma=1.5)
    # N7b 14.7-17.0 cut away to his house's stand erupting, on their feet in the rain (as M8)
    c, half = best_box(sc.h)
    toward = norm([-c[0], 0.0, -c[2]])
    side = [-toward[2], 0.0, toward[0]]
    base = [c[0] + toward[0] * 21.0, c[1] + 3.0, c[2] + toward[2] * 21.0]
    pf = move(add(base, side, 1.0), add(base, side, -1.0), 294, 340)
    sc.shot(294, 340, pf, const([c[0], c[1] + 1.4, c[2]]), ramp(56.0, 52.0, 294, 340), 8)
    sc.notes['stand_shot'] = (294, 340, c)
    # N8 17.0-19.2 he slows over the grass and lifts it up to the stands
    e_h = [p_hov[0], p_hov[1] + 0.95, p_hov[2]]
    pf = move(add(e_h, [-3.7, -0.25, 4.3]), add(e_h, [-3.9, -0.2, 4.0]), 340, 384)
    sc.shot(340, 384, pf, smoothed(eye('viewer'), 6.0), ramp(50.0, 48.0, 340, 384), 6)
    # N9 19.2-23 his team swoops in round him
    pf = move(add(e_h, [12.0, 3.0, -4.0]), add(e_h, [11.0, 3.4, -1.5]), 384, 460)
    sc.shot(384, 460, pf, const(add(e_h, [0.0, 0.6, 0.0])), ramp(58.0, 54.0, 384, 460), 6)
    # N10 23-28 the crane up and out: the stands on their feet in the storm
    pf = move(add(e_h, [5.0, 1.0, 7.0]), add(e_h, [17.0, 22.0, 22.0]), 460, 560)
    sc.shot(460, 560, pf, const(add(e_h, [0.0, 0.5, 0.0])), ramp(56.0, 74.0, 460, 560), 6)
    # N11 28-34 closer, among them: the Snitch held high
    pf = move(add(e_h, [-4.6, 1.8, -2.6]), add(e_h, [-4.3, 1.9, -1.9]), 560, 680)
    sc.shot(560, 680, pf, const(add(e_h, [0.0, 0.5, 0.0])), ramp(50.0, 46.0, 560, 680), 8)
    # N12 34-40 his face, the rain on it, a slow push in as the memory closes
    pf = move(add(e_h, [0.55, 0.05, 1.75]), add(e_h, [0.42, 0.03, 1.3]), 680, sc.L)
    sc.shot(680, sc.L, pf, const(e_h), ramp(46.0, 40.0, 680, sc.L), 8)
    whoosh(sc, 'broom_viewer', 144, 180)            # down the column at us
    whoosh(sc, 'broom_r_s', 180, 216)               # the rival past the falling camera
    whoosh(sc, 'broom_viewer', 216, 246)
    E_ = lambda aid: (lambda sc_, t: E.rider_eye(sc_, E.actor_by_id(sc_, aid), t))
    sc.framing += [(4, 60, E_('viewer'), 'the boy hunting (N1)'),
                   (66, 102, lambda sc_, t: ball_world(sc_, 'snitch', t), 'the Snitch (N2)'),
                   (108, 142, E_('viewer'), 'his face (N3)'), (150, 178, E_('viewer'), 'the dive (N4)'),
                   (184, 212, E_('viewer'), 'the boy diving (N5)'), (184, 212, E_('r_s'), 'the rival diving (N5)'),
                   (226, 244, E_('viewer'), 'the pull-out (N6)'),
                   (T4['fp'][0] + 2, T_C - 2, lambda sc_, t: ball_world(sc_, 'snitch', t), 'the Snitch (N7)'),
                   (T_C - 3, T_C + 6, lambda sc_, t: hand(sc_, E.actor_by_id(sc_, 'viewer'), 'right_arm', t), 'his hand (N7)'),
                   (298, 338, lambda sc_, t: [c[0], c[1] + 1.0, c[2]], 'the stand erupting (N7b)'),
                   (344, 382, E_('viewer'), 'the boy (N8)'),
                   (T4['raise'][1], 382, lambda sc_, t: hand(sc_, E.actor_by_id(sc_, 'viewer'), 'right_arm', t), 'the Snitch held up (N8)'),
                   (388, 458, E_('viewer'), 'the boy (N9)'), (562, 678, E_('viewer'), 'the boy (N11)'),
                   (684, T4['close'], E_('viewer'), 'his face (N12)')]


# ================================================================ the chain: wiring, budget, sizes, mirrors
ENGINE_NAMES = {n for n in dir(E) if callable(getattr(E, n)) and not n.startswith('_')}


def check_mirrors(src, problems):
    """Assert 1: every camera, look, eye, pose, rider and broom position comes from scene_engine: this generator
    defines no function of the engine's own names (a private copy of a mirror)."""
    import re
    own = set(re.findall(r'^def ([a-z_0-9]+)\(', src, re.M))
    clash = sorted(own & ENGINE_NAMES)
    if clash:
        problems.append('flying_scene.py defines private copies of engine mirrors: %s' % ', '.join(clash))
    return len(own)


def check_chain(scenes, problems):
    """Assert 10, wiring: every house's four scenes exist and hand over in order, the first opens on eyes_open at t 0,
    the Snitch scene ends in a wake on shut lids; no next anywhere else."""
    for h in HOUSES:
        ids = ['flying_' + h, 'flying_%s_chase' % h, 'flying_%s_match' % h, 'flying_%s_snitch' % h]
        for i, sid in enumerate(ids):
            s = scenes.get(sid)
            if s is None:
                problems.append('%s is missing' % sid)
                continue
            want = ids[i + 1] if i < 3 else None
            if s.get('next') != want:
                problems.append('%s hands over to %s, not %s' % (sid, s.get('next'), want))
            if (s.get('end_action') == 'wake') != (i == 3):
                problems.append('%s end_action %s' % (sid, s.get('end_action')))
        first = scenes.get(ids[0])
        if first and not any(e['id'] == 'eyes_open' and e['t'] == 0 for e in first['events']):
            problems.append('%s does not open on eyes_open at t 0' % ids[0])
        last = scenes.get(ids[3])
        if last and not any(e['id'] == 'eyes_close' and e['t'] + e.get('duration', 40) <= last['length']
                            for e in last['events']):
            problems.append('%s does not end on shut lids' % ids[3])


def template_blocks(name):
    from scene_common import template_grid
    return sum(1 for s in template_grid(name).values() if not s.startswith('minecraft:air'))


def check_budget(scenes, problems, counts):
    """The build budget: the lawn scenes' set (template + castle) at most 100,000 blocks, built at the opening; the
    pitch set is built as a standby during the chase and the match, each at least twice its build ticks."""
    lawn = counts['flying_lawn'] + counts['castle']
    pitch = counts['quidditch_pitch'] + counts['castle']
    ticks = int(math.ceil(pitch / 4000.0))
    if lawn > 100000:
        problems.append('the lawn set is %d blocks (at most 100,000)' % lawn)
    for h in HOUSES:
        for sid in ('flying_%s_chase' % h, 'flying_%s_match' % h):
            if scenes[sid]['length'] < 2 * ticks:
                problems.append('%s lasts %d ticks, under twice the pitch build (2 x %d)' % (sid, scenes[sid]['length'],
                                                                                       ticks))
    return lawn, pitch, ticks


def check_size(sid, txt, problems):
    n, b = len(txt), len(txt.encode('utf-8'))
    if n > MAX_JSON or b >= 1000000:
        problems.append('%s is %d characters, %d bytes' % (sid, n, b))
    return n


def build_all(lawn, pitch_w, pitch_a):
    """All sixteen scenes: {id: (sc, scene)}."""
    set_pitch(pitch_a, pitch_w)
    out = {}
    for h in HOUSES:
        MATCH_FUNCS.clear()
        sc1, s1 = lesson(h, lawn)
        sc2, s2 = chase(h, lawn, s1)
        sc3, s3 = match(h, pitch_w, s2)
        sc4, s4 = snitch_scene(h, pitch_w, s3)
        for sc, s in ((sc1, s1), (sc2, s2), (sc3, s3), (sc4, s4)):
            sc.cam_world = cam_world(lawn if sc.notes.get('lawn') else pitch_w)
            out[sc.sid] = (sc, s)
    return out


def worlds():
    w, _ = FL.build_world()
    lawn = dict(w.g)
    lawn.update(FL.CASTLE)
    pw = {}
    pa = QP.build(pw)
    return lawn, pw, pa


def bites(built, lawn, pitch_w):
    """Each assert shown to bite: one mutation of the emitted JSON (or the source) per assert, which must give it a
    problem. Returns [(assert, problems the mutation raised)]."""
    out = []

    def bite(name, sid, mutate, run):
        sc, s = built[sid]
        m = copy.deepcopy(s)
        sc2 = copy.copy(sc)
        sc2.notes = dict(sc.notes)
        mutate(m, sc2)
        P = []
        run(sc2, m, P)
        out.append((name, len(P), P[0] if P else None))
        assert P, 'the %s assert did not bite' % name
    h = 'gryffindor'
    L1_, L2_, L3_, L4_ = ('flying_' + h, 'flying_%s_chase' % h, 'flying_%s_match' % h, 'flying_%s_snitch' % h)
    src = open(os.path.abspath(__file__), encoding='utf-8').read()
    P = []
    check_mirrors(src + '\ndef track_sample(keys, t):\n    pass\n', P)
    out.append(('1 mirrors (a private track_sample)', len(P), P[0] if P else None))
    assert P

    def run_keys(i, ease):
        def f(m, sc):
            for k in m['camera'][i:i + 3]:
                k['ease'] = ease
        return f
    bite('2 spline moves (three linear keys)', L1_, run_keys(5, 'linear'),
         lambda sc, m, P: E.check_spline_moves(sc.sid, m, P))

    def kink(m, sc):
        m['camera'][6]['pos'] = [m['camera'][6]['pos'][0] + 0.4] + m['camera'][6]['pos'][1:]
    bite('3 smoothness (a key 0.4 off its line)', L1_, kink, lambda sc, m, P: E.check_camera_smoothness(sc.sid, m, P))

    def into_head(m, sc):
        k = next(k for k in m['camera'] if k['t'] >= 140 and not k.get('cut'))
        a = E.actor_by_id(m, 'hooch')
        k['pos'] = r4(head_eye(m, a, k['t'], 0.0))
    bite('4 clearance (a camera key in Hooch\'s head)', L1_, into_head, lambda sc, m, P: check_clearance(sc, m, P))

    def into_ground(m, sc):
        k = next(k for k in m['camera'] if k['t'] >= 140)
        k['pos'] = [k['pos'][0], -1.5, k['pos'][2]]
    bite('4 camera air (a key in the ground)', L1_, into_ground, lambda sc, m, P: check_camera_air(sc, m, P))

    def drift_ball(m, sc):
        tc = sc.notes['T']['c1_catch']
        q = E.actor_by_id(m, 'quaffle_1')
        k = next(k for k in q['path'] if abs(k['t'] - tc) < 1e-6)            # its offset in c1's broom frame
        k['pos'] = [k['pos'][0] + 0.4] + k['pos'][1:]
    bite('5 hand contacts (the Quaffle 0.4 off c1\'s hand)', L3_, drift_ball, lambda sc, m, P: check_contacts(sc, m, P))

    def late_item(m, sc):
        a = E.actor_by_id(m, 'malfoy')
        x = next(x for x in a['actions'] if x['do'] == 'hold' and x.get('item') == REMEMBRALL_ITEM)
        x['t'] += 3
    bite('6 held items (Malfoy takes it 3 ticks late)', L2_, late_item, lambda sc, m, P: check_items(sc, m, P))

    def late_goal(m, sc):
        tg = sc.notes['goal_tick']
        sc.reactions = [(t + 15 if c[0] == 'goal' else t, st, hh, c) for t, st, hh, c in sc.reactions]
    bite('7 crowd timing (the goal roar 0.75 s late)', L3_, late_goal, lambda sc, m, P: check_reactions(sc, m, P))

    def late_crack(m, sc):
        sc.sfx_hits = [(n, t + (4 if n == 'bludger_crack' else 0), hit, lab) for n, t, hit, lab in sc.sfx_hits]
    bite('7 sound timing (the crack 4 ticks late)', L3_, late_crack, lambda sc, m, P: check_sfx_hits(sc, m, P))

    def early_twitch(m, sc):
        b = E.actor_by_id(m, 'broom_e0')
        b['path'].insert(1, {'t': 60, 'pos': [b['path'][0]['pos'][0], b['path'][0]['pos'][1] + 0.1,
                                              b['path'][0]['pos'][2]]})
    bite('7 actions in their moment (a broom stirs before its Up!)', L1_, early_twitch,
         lambda sc, m, P: check_moments_lesson(sc, m, P))

    def into_stand(m, sc):
        b = E.actor_by_id(m, 'broom_o_c2')
        k = next(k for k in b['path'] if k['t'] >= 900)
        k['pos'] = [86.0, 15.0, 0.0]
    bite('8 fliers (a broom into the east stand)', L3_, into_stand, lambda sc, m, P: check_fliers(sc, m, P))

    def low_dive(m, sc):
        b = E.actor_by_id(m, 'broom_viewer')
        for k in b['path']:
            if T4['pull'] <= k['t'] <= T4['level'] + 8:
                k['pos'] = [k['pos'][0], k['pos'][1] - 1.2, k['pos'][2]]
    bite('8 the dive (pulled out 1.2 lower)', L4_, low_dive, lambda sc, m, P: check_dives(sc, m, P))

    def jolt_ride(m, sc):
        a = E.actor_by_id(m, 'malfoy')
        tm = a['ride']['from']
        k = next(k for k in a['path'] if abs(k['t'] - (tm - 0.001)) < 1e-6)
        k['pos'] = [k['pos'][0] + 0.1] + k['pos'][1:]
    bite('9 ride windows (Malfoy mounts 0.1 off the seat)', L2_, jolt_ride, lambda sc, m, P: check_rides(sc, m, P))

    def miswire(m, sc):
        m['next'] = 'flying_%s_snitch' % h
    def chain_run(sc, m, P):
        sc_all = {k: v[1] for k, v in built.items()}
        sc_all[L2_] = m
        check_chain(sc_all, P)
    bite('10 wiring (the chase hands over to the Snitch)', L2_, miswire, chain_run)

    def onto_seat(m, sc):
        e = next(e for e in m['events'] if e['id'] == 'crowd')
        p = E.crowd_seats(e)[0][0]
        a = E.actor_by_id(m, 'hooch')
        a['path'] = [{'t': 0, 'pos': [p[0], p[1], p[2]]}]
    bite('crowd seats (Hooch on a seat)', L3_, onto_seat, lambda sc, m, P: check_crowd_gap(sc, m, P))

    def wide_shot(m, sc):
        q = E.actor_by_id(m, 'quaffle_3')
        for k in q['path']:
            if sc.notes['T']['shot'] < k['t'] < 0.5 + sc.notes['T']['ring'] + 30:
                k['pos'] = [k['pos'][0] + 2.7] + k['pos'][1:]
    bite('Quaffle ring margin (the shot 2.7 wide)', L3_, wide_shot, lambda sc, m, P: check_quaffle_ring(sc, m, P))

    def look_away(m, sc):
        for k in m['camera']:
            if 64 <= k['t'] <= 104:
                k['look_at'] = [0.0, 30.0, -60.0]
    bite('the Snitch seen before the dive (S2 looking away)', L4_, look_away, lambda sc, m, P: check_snitch_seen(sc, m, P))

    def lift_broom(m, sc):
        b = E.actor_by_id(m, 'broom_e0')
        b['path'][0]['pos'] = [b['path'][0]['pos'][0], b['path'][0]['pos'][1] + 0.2, b['path'][0]['pos'][2]]
    bite('lying brooms (one 0.2 off the grass)', L1_, lift_broom, lambda sc, m, P: check_lying(sc, m, P))

    def jump_rem(m, sc):
        r = E.actor_by_id(m, 'remembrall')
        i = next(i for i, k in enumerate(r['path']) if k['t'] > sc.notes['throw'] + 10)
        k = r['path'][i]
        r['path'].insert(i + 1, {'t': k['t'] + 0.3, 'pos': [k['pos'][0] + 3.0] + k['pos'][1:], 'ease': 'linear'})
    bite('the Remembrall\'s path (a 3-block jump in flight)', L2_, jump_rem,
         lambda sc, m, P: check_ball_path(sc, m, P, 'remembrall', [sc.notes['malfoy_pick'], sc.notes['throw'],
                                                                   sc.notes['catch']]))

    def spin_key(m, sc):
        b = E.actor_by_id(m, 'broom_o_c2')
        k = next(k for k in b['look'] if k['t'] >= 700)
        k['yaw'] += 150.0
    def rate_run(sc, m, P):
        Q = []
        check_yaw_rates(sc, m, Q)
        P.extend(q for q in Q if 'degrees a tick' in q)
    bite('yaw rate (one Flier look key turned 150 degrees)', L3_, spin_key, rate_run)

    def dive_slides_back(m, sc):
        b = E.actor_by_id(m, 'broom_viewer')
        k = min(b['path'], key=lambda k: abs(k['t'] - 540))
        k['pos'] = [k['pos'][0], k['pos'][1], k['pos'][2] - 4.0]
    def crab_run(sc, m, P):
        Q = []
        check_yaw_rates(sc, m, Q)
        P.extend(q for q in Q if 'own heading' in q)
    bite('yaw rate (crab: a dive key 4 blocks back, the broom flies sideways)', L2_, dive_slides_back, crab_run)

    def rival_dives_on(m, sc):
        b = E.actor_by_id(m, 'broom_r_s')
        k = min(b['path'], key=lambda k: abs(k['t'] - 260))
        k['pos'] = [k['pos'][0], k['pos'][1] - 3.0, k['pos'][2]]
    bite('7 the rival stays out of the dive (a key 3 lower at 260)', L4_, rival_dives_on,
         lambda sc, m, P: check_moments_snitch(sc, m, P))

    def strikes_behind(m, sc):
        for e in m['events']:
            if e.get('id') == 'lightning':
                c = E.engine_cam(m['camera'], e['t'])
                lk, _ = E.engine_look(m, m['camera'], e['t'])
                d = norm([c[0] - lk[0], 0.0, c[2] - lk[2]])
                e['at'] = [round(c[0] + d[0] * 140), 0, round(c[2] + d[2] * 140)]
    def screen_run(sc, m, P):
        Q = []
        check_lightning(sc, m, Q)
        P.extend(q for q in Q if 'on screen' in q)
    bite('lightning on screen (every strike behind the camera)', L3_, strikes_behind, screen_run)

    def strike_on_seat(m, sc):
        e = [e for e in m['events'] if e.get('id') == 'lightning'][0]
        cr = [c for c in m['events'] if c.get('id') == 'crowd'][0]
        p = E.crowd_seats(cr)[0][0]
        e['at'] = [p[0], 0, p[2]]
    def stands_run(sc, m, P):
        Q = []
        check_lightning(sc, m, Q)
        P.extend(q for q in Q if 'stands' in q)
    bite('lightning clear of the stands (one strike on a seat)', L3_, strike_on_seat, stands_run)

    def stand_away(m, sc):
        for k in m['camera']:
            if 296 <= k['t'] <= 340:
                k['look_at'] = [0.0, 60.0, 0.0]
    bite('framing (the cutaway to the stand looks away)', L4_, stand_away, lambda sc, m, P: check_framing(sc, m, P))

    def short_chase(m, sc):
        m['length'] = 100
    def budget_run(sc, m, P):
        sc_all = {k: v[1] for k, v in built.items()}
        sc_all[L2_] = m
        check_budget(sc_all, P, COUNTS)
    bite('build budget (a 5 s chase)', L2_, short_chase, budget_run)
    return out


COUNTS = {}


def main():
    import time
    t0 = time.time()
    bite_only = '--bite' in sys.argv
    lawn, pw, pa = worlds()
    COUNTS.update(flying_lawn=template_blocks('flying_lawn'), quidditch_pitch=template_blocks('quidditch_pitch'),
                  castle=len(QP.schematic_cells()[0]))
    built = build_all(lawn, pw, pa)
    print('built %d scenes in %.0f s' % (len(built), time.time() - t0))
    problems = []
    check_mirrors(open(os.path.abspath(__file__), encoding='utf-8').read(), problems)
    scenes = {k: v[1] for k, v in built.items()}
    check_chain(scenes, problems)
    lawn_n, pitch_n, ticks = check_budget(scenes, problems, COUNTS)
    print('sets: lawn %d blocks (cap 100,000), pitch %d blocks = %d build ticks (chase and match at least %d)' % (
        lawn_n, pitch_n, ticks, 2 * ticks))
    if bite_only:
        for name, n, first in bites(built, lawn, pw):
            print('  bites: %-58s %d problem(s), e.g. %s' % (name, n, (first or '')[:110]))
        return
    from scene_common import write_scene
    worst = {}
    for sid in sorted(built):
        sc, s = built[sid]
        nums = run_checks(sc, s, lawn if sc.notes.get('lawn') else pw)
        problems += nums.pop('problems')
        txt = write_scene(s, sid)
        n = check_size(sid, txt, problems)
        print('%-28s %5.1f s  %7d chars  md5 %s' % (sid, s['length'] / 20.0, n, hashlib.md5(txt.encode('utf-8')).hexdigest()))
        for k, v in nums.items():
            print('    %-16s %s' % (k, v if not isinstance(v, float) else round(v, 5)))
        worst[sid] = nums
    print('%.0f s' % (time.time() - t0))
    assert not problems, '\n'.join(problems[:80]) + '\n%d problems' % len(problems)
    print('0 problems')


if __name__ == '__main__':
    main()
