"""Generates the Wand memory: memories/wand_wall.json -> wand_alley.json -> wand_ollivanders.json (end_action wake).

Usage (from the repo root): python -I build_scripts/pensieve_scenes/wand_scene.py

The first wand bought from the wandmaker plays wand_wall (WandMemory starts it with the bought wand as the start
context, so {viewer_wand} is that wand in every scene of the chain). Sets and anchors: diagon_set.py (ANCHORS; a
memory_block's path position is the block's CENTRE, so a brick resting in cell c is at c + 0.5).

SHOT LIST (seconds from each scene's start; ~171 s in all; beats timed to the measured voice lines)
==========================================================
wand_wall (21.8 s; set wand_diagon, the Leaky Cauldron's back yard; next wand_alley)
  W1  0.0- 7.8  First person at the boy's eye (0.75), eyes_open from black (2.6 s): the brick wall, Hagrid (1.5)
                steps up beside him. "Three up... two across... Right. Stand back." He taps three bricks with his
                umbrella (a stick), the third is the first brick of the arch; a brick_tap on each.
  W2  7.8-10.5  Low wide from the yard's north side: Hagrid and the boy step back from the wall.
  W3 10.5-13.4  Close on the wall over the boy's shoulder, slow push: the tapped brick quivers (10.5-11.6), a hole,
                then the ripple: 68 memory_block bricks, the alley layer 0.22 s ahead of the yard layer, staggered
                0.42 s per block of distance from the first, each out of the wall plane first, then twisting and
                sliding on its own arc (one full turn about a random axis) into the arch's surround on the alley
                side; wall_grind, dust (white_ash, poof), warm light_rays pouring through into the yard.
  W4  13.4-21.8 One continuous move: wide from behind them as the arch completes and the street noise rises, then
                the slow push through the arch behind Hagrid and the boy as they walk into the street (fov 72->66);
                a few shoppers pass beyond the arch. Chain fade to black over the last second.
wand_alley (56 s; set wand_diagon, daylight; the arch's bricks sit in their surround; next wand_ollivanders)
  A1  0.0- 5.0  High wide over the arch exit drifting forward, the crooked street full of shoppers below; Hagrid
                and the boy walk into frame. "Welcome to Diagon Alley."
  A2  5.0-11.6  Leading track at their eye line, the camera backing up the street ahead of them.
  A3  11.6-17.0 Quality Quidditch Supplies: children with their hands and noses on the glass, the broom on its
                stand; the boy runs over and joins them, Hagrid waits.
  A4  17.0-21.5 Eeylops: owls in the cages turning their heads and hooting, shoppers passing.
                (Hagrid and the boy jump ahead up the street on the cut at 21.5, unseen.)
  A5  21.5-25.5 The apothecary window: jars, brewing stands, browsers.
  A6  25.5-30.5 Trailing track behind Hagrid and the boy past the hanging cauldrons.
  A7  30.5-34.5 Flourish and Blotts' stacked books, browsers at the window.
  A8  34.5-40.0 High over the street toward Gringotts' white marble: they walk away under the camera toward it.
  A9  40.0-44.5 On the bank steps: the goblin guards flanking the bronze doors.
  A10 44.5-50.4 Two-shot at Ollivanders: Hagrid turns to the boy. "Go on, then. Ollivanders. Only place for
                wands." He points at the shop.
  A11 50.4-56.0 The narrow, dusty front with its gold letter board: the boy walks to the door alone, looks back,
                Hagrid waves; he reaches for the door. Chain fade.
wand_ollivanders (93 s; set wand_ollivanders, dim; end_action wake)
  O0  0.0- 4.4  From the door: the bell tinkles, the door shuts; the boy walks in slowly; dust in the window light.
  O1  4.4- 8.6  Low, tilting up the towering wand-box shelves into the dark; the ladder rolls in (ladder sfx 7.4).
  O2  8.6-13.4  Low beside the boy, up at Ollivander riding the rolling ladder (15 memory_block ladder blocks) to a
                stop. "Ah. I wondered when I'd be seeing you."
  O3  13.4-16.0 Wide from the door side: he climbs down.
  O3b 16.0-19.0 Wide from the front window: he walks to the counter (the ladder actor swaps for the walking one,
                below the floor, on this cut).
  O4  19.0-24.0 Over the boy's shoulder on Ollivander: "Which is your wand arm? Hold out your hand." The boy holds
                out his right arm.
  O5  24.0-29.0 Close on the boy: the measuring tape spirals up round him by itself (tape sfx) while Ollivander
                turns to the stack behind him and takes a wand.
  O6  29.0-34.0 Over the counter: the tape runs along the outstretched arm, then drops over the counter.
  W1  34.0-36.85 Side two-shot: the first wrong wand. "Give it a wave."
      36.85-40.0 Cut on the wave, wide from the door: 28 wand boxes burst out of the shelves and tumble to the floor
                (memory_block boxes, boxes_fall). "No, no, apparently not." He snatches it back.
      40.0-42.6 Close on Ollivander as he turns away to the stack.
  W2  42.6-46.1 Wide over the counter: the second wrong wand.
      46.1-48.6 Cut on the wave, close on the counter end: the vase shatters (block to air, shards, poof, vase).
                "No. Definitely not." Snatched back.
      48.6-51.0 Wide from the door: he fetches a third.
  W3  51.0-55.9 Close two-shot: "Give it a wave." A wretched fizzle and smoke; the boy flinches.
      55.9-60.4 From the boy's side past Ollivander: "A tricky customer. I wonder..." He goes to the stack, reaches
                high, comes back with one more box.
  T1  60.4-63.6 Close on the counter: he holds out the right wand ({viewer_wand}); the boy takes it.
  T2  63.6-68.6 Low hero angle, slow arc: the boy raises it; wand_choice (gold glow, motes spiralling up, the
                screen bloom), a light block for the glow, choice sfx; his free arm lifts in the wind.
  T3  68.6-72.0 Close on Ollivander: soft applause.
  T4  72.0-75.0 Side medium: the settle; the boy lowers the wand.
  C   75.0-88.4 A very still close on Ollivander over the boy's shoulder, his voice only: "Curious... very
                curious. The wand chooses the wizard. It is not always clear why." Then "I think we must expect
                great things from you."
  X   88.4-93.0 First person: the boy looks down at his wand; eyes_close from 90.0; the scene ends on shut lids
                and the wake returns him to the real world, wand in hand.

Voices: the plan's line ids (pensieve.voice.wand_*). A voice cue is emitted only when that sound is registered in
sounds.json; the subtitle always is, lasting the measured length from build_scripts/sounds/wand_clip_lengths.json
when the id is there, else an estimate (words / 2.6 s + 0.4). Each line has a slot: an assert fails if the real or
estimated length runs past it, so rerun this after the voices land and retime any line it names.

Asserts: each JSON under the 900,000-char cap; unique actor ids; every key time sorted and inside the scene;
every camera key and straight camera move in an air cell; no moving player within 0.6 blocks (horizontal) of any
other player at any tick (sampled every 2 ticks) and no walker's feet or head in a solid shape (the crowd is built
on the lane graph and each walker is accepted only if it keeps clear of everyone already placed and of the camera);
nobody is seated in this memory (so no leg boxes), asserted; subtitles inside their slots; every brick's path in
air and never through Hagrid or the boy; every flying box and shard in the room, clear of the counter, the stack
and both actors; eyes_close the last event of the wake scene.
"""
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from scene_common import write_scene, FREE, MEMORIES  # noqa: E402
import diagon_set as DS  # noqa: E402

A = DS.ANCHORS
MAX_JSON = 900000
WALL_ID, ALLEY_ID, OLLI_ID = 'wand_wall', 'wand_alley', 'wand_ollivanders'
KID, ADULT, GIANT = 0.75, 1.0, 1.5
EYE = 1.62
BOY_EYE = EYE * KID
SFX = 'hogwartsmagic:pensieve.sfx.wand_'
RES = os.path.normpath(os.path.join(MEMORIES, '..', '..', '..'))
REGISTERED = set(json.load(open(os.path.join(RES, 'assets', 'hogwartsmagic', 'sounds.json'), encoding='utf-8')))
CLIP_LENGTHS = json.load(open(os.path.join(HERE, '..', 'sounds', 'wand_clip_lengths.json'), encoding='utf-8'))
SKINS = set(json.load(open(os.path.join(MEMORIES, '_skins.json'), encoding='utf-8')))
_MOD_SRC = open(os.path.join(RES, '..', 'java', 'com', 'hogwartsmagic', 'ExampleMod.java'), encoding='utf-8').read()

# The three wands he tries and rejects: the server fills these with registered wands that differ from the viewer's
# (ItemText.rejects), so the wand that finally chooses the boy is never one of them.
WRONG_WANDS = ('{reject_wand_1}', '{reject_wand_2}', '{reject_wand_3}')
UMBRELLA = 'minecraft:stick'          # no pink umbrella item in the mod: a stick reads as its shaft
PARCELS = ('minecraft:bundle', 'minecraft:book', 'minecraft:paper', 'minecraft:writable_book', 'minecraft:potion',
           'minecraft:feather', 'minecraft:glass_bottle', 'minecraft:flower_pot')

# ================================================================ the lines
LINES = {
    'wand_hagrid_bricks': ('Hagrid', 'Three up... two across... Right. Stand back.'),
    'wand_hagrid_welcome': ('Hagrid', 'Welcome to Diagon Alley.'),
    'wand_hagrid_wand': ('Hagrid', 'Go on, then. Ollivanders. Only place for wands.'),
    'wand_olli_wondered': ('Mr Ollivander', "Ah. I wondered when I'd be seeing you."),
    'wand_olli_wandarm': ('Mr Ollivander', 'Which is your wand arm? Hold out your hand.'),
    'wand_olli_try': ('Mr Ollivander', 'Give it a wave.'),
    'wand_olli_no': ('Mr Ollivander', 'No, no, apparently not.'),
    'wand_olli_no2': ('Mr Ollivander', 'No. Definitely not.'),
    'wand_olli_tricky': ('Mr Ollivander', 'A tricky customer. I wonder...'),
    'wand_olli_curious': ('Mr Ollivander', 'Curious... very curious. The wand chooses the wizard. '
                                           'It is not always clear why.'),
    'wand_olli_great': ('Mr Ollivander', 'I think we must expect great things from you.'),
}


def line_len(line):
    """The measured clip length when there is one, else words / 2.6 s + 0.4."""
    if line in CLIP_LENGTHS:
        return float(CLIP_LENGTHS[line]), True
    return len(LINES[line][1].split()) / 2.6 + 0.4, False


# ================================================================ maths
def tk(s):
    return int(round(s * 20))


def r3(v):
    return round(v, 3)


def yaw_to(dx, dz):
    """Minecraft yaw facing along (dx, dz): 0 = +Z, 90 = -X, -90 = +X, 180 = -Z."""
    return math.degrees(math.atan2(-dx, dz))


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def smooth(f):
    f = clamp(f, 0.0, 1.0)
    return f * f * (3 - 2 * f)


def ease_out(f):
    f = clamp(f, 0.0, 1.0)
    return 1 - (1 - f) ** 3


def ease_f(e, f):
    if e == 'linear':
        return f
    if e == 'in':
        return f ** 3
    if e == 'out':
        return 1 - (1 - f) ** 3
    return 4 * f ** 3 if f < 0.5 else 1 - (-2 * f + 2) ** 3 / 2


def lerp(a, b, f):
    return [a[k] + (b[k] - a[k]) * f for k in range(len(a))]


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


def samples_fast(path, ticks):
    """sample_path at every tick of an increasing list, in one pass."""
    out, j, n = [], 0, len(path)
    for t in ticks:
        if t <= path[0]['t']:
            out.append(path[0]['pos'])
            continue
        while j + 1 < n and path[j + 1]['t'] < t:
            j += 1
        if j + 1 >= n:
            out.append(path[-1]['pos'])
            continue
        a, b = path[j], path[j + 1]
        if b['t'] == a['t']:
            out.append(b['pos'])
            continue
        f = ease_f(b.get('ease', 'in_out'), (t - a['t']) / (b['t'] - a['t']))
        out.append([a['pos'][k] + (b['pos'][k] - a['pos'][k]) * f for k in range(3)])
    return out


def sample_cam(cam, t):
    """The camera position at tick t: cut keys hold the previous key until their own tick."""
    if t <= cam[0]['t']:
        return cam[0]['pos']
    for a, b in zip(cam, cam[1:]):
        if t < b['t'] or (t == b['t'] and not b.get('cut')):
            if b.get('cut') or b['t'] == a['t']:
                return a['pos']
            f = ease_f(b.get('ease', 'in_out'), (t - a['t']) / (b['t'] - a['t']))
            return lerp(a['pos'], b['pos'], f)
    return cam[-1]['pos']


# ================================================================ limbs, clips, movers
class Channel:
    """One animated limb value: a list of changes, each easing from the value before it over dur (hold=True writes
    the old value at t first). Overlapping changes are pushed back so keys always run forward in time."""

    def __init__(self, v0):
        self.changes = [(0.0, v0, 0.0, True, 0)]
        self.n = 1

    def change(self, t, v, dur=0.4, hold=True):
        self.changes.append((t, v, dur, hold, self.n))
        self.n += 1

    def keys(self, end):
        ch = sorted(self.changes, key=lambda c: (c[0], c[4]))
        out = [(0, ch[0][1])]
        prev, last = ch[0][1], 0
        for t, v, dur, hold, _ in ch[1:]:
            tt = tk(t)
            if tt >= end - 1:
                break
            if hold:
                tt = max(tt, last + 1)
                out.append((tt, prev))
                last = tt
                te = tt + max(1, tk(dur))
            else:
                te = max(tk(t + dur), last + 1)
            te = min(max(te, last + 1), end)
            if te <= last:
                break
            out.append((te, v))
            last, prev = te, v
        clean = []
        for t, v in out:
            if clean and clean[-1][0] == t:
                clean[-1] = (t, v)
            else:
                clean.append((t, v))
        return clean


class Life:
    """An actor's hand-keyed limbs (added on top of life, poses and clips)."""

    def __init__(self):
        self.ch = {}

    def __getitem__(self, part):
        if part not in self.ch:
            self.ch[part] = Channel([0, 0, 0])
        return self.ch[part]

    def pulse(self, part, t0, t1, v, ramp=0.3):
        self[part].change(t0, v, ramp)
        self[part].change(max(t0 + ramp + 0.05, t1 - ramp), [0, 0, 0], ramp)

    def emit(self, end):
        out = {}
        for part, ch in self.ch.items():
            ks = ch.keys(end)
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
        self.cue(t0, name, blend)
        self.cue(t1, 'IDLE', end_blend)

    def emit(self, end, taken=None):
        """The cues as anims keys. taken (a scene-wide set of (tick, clip)) keeps any two people from starting the
        same clip on the same tick: a clash moves the later one on a tick."""
        out, last = [], -1
        for t, name, blend in sorted(self.cues, key=lambda c: c[0]):
            tt = max(tk(t), last + 1)
            if taken is not None and name != 'IDLE':
                while (tt, name) in taken:
                    tt += 1
                taken.add((tt, name))
            if tt > end:
                break
            k = {'t': tt, 'name': name}
            if blend:
                k['blend'] = blend
            out.append(k)
            last = tt
        return out


class Mover:
    """Path and look keys. Walks are linear (the life layer eases the gait) and turn into each segment's direction;
    yaw stays continuous. emit(end) cuts everything at the scene's end."""

    def __init__(self, aid, pos, yaw):
        self.aid = aid
        self.pos = list(pos)
        self.yaw = yaw
        self.t = 0.0
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

    def hold(self, t):
        if t > self.t + 1e-9:
            self.path.append((t, list(self.pos), 'linear'))
            self.t = t

    def to(self, t1, p, ease='linear', turn=0.3, face=True):
        dx, dz = p[0] - self.pos[0], p[2] - self.pos[2]
        if face and math.hypot(dx, dz) > 0.08:
            y = self.near(yaw_to(dx, dz))
            if abs(y - self.yaw) > 1:
                self.look += [(self.t, self.yaw), (self.t + min(turn, (t1 - self.t) / 2), y)]
                self.yaw = y
        self.path.append((t1, list(p), ease))
        self.pos = list(p)
        self.t = t1

    def walk(self, t0, pts, speed, turn=0.3):
        self.hold(t0)
        for p in pts:
            d = math.hypot(p[0] - self.pos[0], p[2] - self.pos[2])
            self.to(self.t + max(0.15, d / speed), p, turn=turn)
        return self.t

    def shift(self, t0, t1, p, ease='in_out'):
        self.hold(t0)
        self.to(t1, p, ease, face=False)

    def jump(self, t, p):
        self.hold(t)
        self.path.append((t + 0.05, list(p), 'linear'))
        self.pos = list(p)
        self.t = t + 0.05

    def emit(self, end_s):
        end = tk(end_s)
        keys = []
        for i, (t, p, e) in enumerate(self.path):
            if t > end_s + 1e-9:
                t0, p0, _ = self.path[i - 1]
                f = (end_s - t0) / (t - t0)
                keys.append((end_s, lerp(p0, p, ease_f(e or 'in_out', f)), e))
                break
            keys.append((t, p, e))
        out_p, last = [], -1
        for t, p, e in keys:
            tt = tk(t)
            k = {'t': tt, 'pos': [r3(p[0]), r3(p[1]), r3(p[2])]}
            if e and e != 'in_out':
                k['ease'] = e
            if out_p and tt == last:
                out_p[-1] = k
            else:
                assert tt > last, (self.aid, 'path out of order', t)
                out_p.append(k)
            last = tt
        out_l, last, prev = [], -1, None
        for t, y in sorted(self.look, key=lambda k: k[0]):
            tt = tk(t)
            if tt > end:
                break
            if prev is not None:              # keep yaw continuous (the engine does not unwrap it)
                while y - prev > 180:
                    y -= 360
                while y - prev < -180:
                    y += 360
            prev = y
            k = {'t': tt, 'yaw': round(y, 1)}
            if out_l and tt == last:
                out_l[-1] = k
            else:
                out_l.append(k)
            last = tt
        return out_p, out_l


def pose(t, name, blend=None):
    p = {'t': t, 'name': name}
    if blend:
        p['blend'] = blend
    return p


def act(t, do, **kw):
    a = {'t': t, 'do': do}
    a.update(kw)
    return a


# ================================================================ a scene
class Scene:
    def __init__(self, sid, title, length, world):
        self.sid, self.title, self.length, self.world = sid, title, length, world
        self.L = tk(length)
        self.actors, self.events, self.words, self.camera = [], [], [], []
        self.poses = {}
        self.lines = []          # (line, t, length, measured, slot_end)
        self.taken = set()       # (tick, clip) starts already used: nobody starts a clip in lockstep
        self.skip_pairs = set()  # {rider, mount}: a child riding on a parent's shoulders

    def ev(self, t, kind, eid, **kw):
        assert 0 <= t <= self.length, (self.sid, kind, eid, t)
        e = {'t': tk(t), 'kind': kind, 'id': eid}
        e.update(kw)
        self.events.append(e)

    def sfx(self, t, name, volume=1.0):
        assert 'pensieve.sfx.wand_' + name in REGISTERED, name
        self.ev(t, 'sfx', SFX + name, volume=volume)

    def sound(self, t, sid, at=None, volume=1.0, pitch=1.0):
        kw = {'volume': volume}
        if pitch != 1.0:
            kw['pitch'] = pitch
        if at is not None:
            kw['at'] = [r3(c) for c in at]
        self.ev(t, 'sound', sid, **kw)

    def particles(self, t, pid, at, count, spread):
        self.ev(t, 'particles', pid, at=[r3(c) for c in at], count=count, spread=[r3(c) for c in spread])

    def say(self, t, line, slot_end, clips=None):
        """The voice cue (only when the sound is registered), the subtitle and an optional TALK span."""
        who, text = LINES[line]
        secs, measured = line_len(line)
        sid = 'pensieve.voice.' + line
        if sid in REGISTERED:
            self.ev(t, 'voice', 'hogwartsmagic:' + sid, volume=1.0)
        self.words.append({'t': tk(t), 'text': text, 'subtitle': True, 'speaker': who,
                           'duration': max(20, tk(secs))})
        if clips is not None:
            clips.span(t, t + secs, 'TALK', 5, 8)
        self.lines.append((line, t, secs, measured, slot_end))
        return secs

    def key(self, t, pos, look, fov=70, cut=False, ease=None):
        k = {'t': tk(t), 'pos': [r3(c) for c in pos]}
        if look is not None:
            k['look_at'] = look if isinstance(look, str) else [r3(c) for c in look]
        if fov != 70:
            k['fov'] = fov
        if ease:
            k['ease'] = ease
        if cut:
            k['cut'] = True
        self.camera.append(k)

    def actor(self, aid, atype='player', skin=None, scale=1.0, mover=None, path=None, look=None, life=None,
              poses=None, actions=None, items=None, clips=None, block=None, alive=True):
        a = {'id': aid, 'type': atype}
        if block:
            a['block'] = block
        if skin is not None:
            a['skin'] = skin
        if abs(scale - 1) > 1e-9:
            a['scale'] = round(scale, 3)
        if atype == 'player' and not alive:
            a['life'] = False
        if items:
            a['items'] = items
        if mover is not None:
            path, look = mover.emit(self.length)
        if path:
            a['path'] = path
        if look:
            a['look'] = look
        if life is not None:
            lm = life.emit(self.L)
            if lm:
                a['limbs'] = lm
        poses = [p for p in poses or () if tk(p['t']) <= self.L]
        actions = [x for x in actions or () if tk(x['t']) <= self.L]
        if poses:
            a['poses'] = [dict(p, t=tk(p['t'])) for p in sorted(poses, key=lambda p: p['t'])]
        if actions:
            a['actions'] = [dict(x, t=tk(x['t'])) for x in sorted(actions, key=lambda x: x['t'])]
        if clips is not None and clips.cues:
            a['anims'] = clips.emit(self.L, self.taken)
        self.actors.append(a)
        return a

    def scene(self, set_, radius, nxt=None, end_action=None):
        s = {'title': self.title, 'length': self.L, 'radius': radius}
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


# ================================================================ worlds and checks
def scene_grid(g, at):
    return {(x + at[0], y + at[1], z + at[2]): s for (x, y, z), s in g.items()}


DIAGON_W = scene_grid(DS._DIAGON.g, DS.DIAGON_TEMPLATE_AT)
OLLI_W = scene_grid(DS._OLL.g, DS.OLLIVANDERS_TEMPLATE_AT)
AIR = set(FREE)


def bid_of(st):
    return None if st is None else st.split('[')[0]


def cell(p):
    return (math.floor(p[0]), math.floor(p[1]), math.floor(p[2]))


def solid_at(w, p, sign_margin=0.0):
    """The block state whose real shape holds point p, or None: slabs by half, stairs by their step, wall signs as
    a thin plate, everything else not FREE as a full cube."""
    c = cell(p)
    st = w.get(c)
    b = bid_of(st)
    if b in AIR:
        return None
    fx, fy, fz = p[0] - c[0], p[1] - c[1], p[2] - c[2]
    if b.endswith('_slab'):
        if 'type=top' in st:
            return st if fy > 0.5 else None
        if 'type=bottom' in st:
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


def standable(w, p, head=2):
    c = cell(p)
    if bid_of(w.get((c[0], c[1] - 1, c[2]))) in AIR:
        return False
    return all(bid_of(w.get((c[0], c[1] + dy, c[2]))) in AIR for dy in range(head))


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
    assert cam[0]['t'] == 0


def check_keys(acts, length_ticks):
    ids_ = [a['id'] for a in acts]
    assert len(ids_) == len(set(ids_)), 'duplicate actor ids'
    for a in acts:
        for kk in ('path', 'look', 'anims', 'poses', 'actions'):
            ts = [k['t'] for k in a.get(kk, [])]
            assert ts == sorted(ts), (a['id'], kk)
            if kk in ('path', 'look', 'anims'):
                assert len(ts) == len(set(ts)), (a['id'], kk)
            assert all(0 <= t <= length_ticks for t in ts), (a['id'], kk, ts)
        for part, d in a.get('limbs', {}).items():
            ts = [k['t'] for k in d['rot']]
            assert ts == sorted(ts) and len(ts) == len(set(ts)), (a['id'], part)
            assert all(0 <= t <= length_ticks for t in ts), (a['id'], part, ts)
        # nobody sits in this memory, so there are no leg boxes to check
        for k in a.get('anims', []):
            assert k['name'] not in ('SIT_DOWN', 'STAND_UP', 'CHEER_STAND'), (a['id'], 'seated clip')


def check_walkers(w, acts, length_ticks, problems, hidden_y=-2.0, skip_pairs=()):
    """No moving player within 0.6 blocks (horizontal) of any other player, sampled every 2 ticks; no walker's feet
    or head inside a solid shape. Positions below hidden_y are actors parked under the floor (a swap) and skipped."""
    people = [a for a in acts if a['type'] == 'player']
    paths = {a['id']: a['path'] for a in people}
    moving = [aid for aid, p in paths.items() if len(p) > 1]
    static = {aid: p[0]['pos'] for aid, p in paths.items() if len(p) == 1}
    ticks = list(range(0, length_ticks + 1, 2))
    tracks = {aid: samples_fast(paths[aid], ticks) for aid in moving}
    n = 0
    for i, t in enumerate(ticks):
        pos = {aid: tracks[aid][i] for aid in moving}
        pos.update(static)
        for aid in moving:
            cur = pos[aid]
            prev = tracks[aid][max(0, i - 1)]
            if (i and math.dist(prev, cur) < 1e-4) or cur[1] < hidden_y:
                continue
            for other, op in pos.items():
                if other == aid or op[1] < hidden_y or frozenset((aid, other)) in skip_pairs:
                    continue
                n += 1
                d = math.hypot(cur[0] - op[0], cur[2] - op[2])
                if d < 0.6 and abs(cur[1] - op[1]) < 1.6:
                    problems.append('t %d: %s at %s is %.2f from %s at %s' % (
                        t, aid, [r3(c) for c in cur], d, other, [r3(c) for c in op]))
            for dy in (0.05, 0.9):
                q = [cur[0], cur[1] + dy, cur[2]]
                hit = solid_at(w, q, sign_margin=0.28)
                if hit:
                    problems.append('t %d: %s walks through %s at %s' % (t, aid, bid_of(hit), [r3(c) for c in q]))
    return n


def check_lines(sc, problems):
    for line, t, secs, measured, slot_end in sc.lines:
        if t + secs > slot_end + 1e-6:
            problems.append('%s: line %s at %.2f lasts %.2f s (%s), past its slot end %.2f' % (
                sc.sid, line, t, secs, 'measured' if measured else 'estimated', slot_end))
        if t + secs > sc.length + 1e-6:
            problems.append('%s: line %s runs past the scene end' % (sc.sid, line))


def finish(sc, scene, extra=None, hidden_y=-2.0):
    problems = []
    check_camera(sc.world, scene['camera'], problems)
    check_keys(scene['actors'], scene['length'])
    check_lines(sc, problems)
    for e in scene['events']:
        if e['kind'] == 'block' and e['id'] == 'minecraft:light' and not is_air(sc.world, e['at']):
            problems.append('light block at %s replaces %s' % (e['at'], sc.world.get(cell(e['at']))))
    if extra:
        extra(problems)
    n_prox = check_walkers(sc.world, scene['actors'], scene['length'], problems, hidden_y, sc.skip_pairs)
    if problems:
        print('\n'.join(problems[:60]))
        print('... %d problems in %s' % (len(problems), sc.sid))
        raise SystemExit(1)
    txt = write_scene(scene, sc.sid, width=2000)
    assert len(txt) < MAX_JSON, '%s.json is %d chars (cap %d)' % (sc.sid, len(txt), MAX_JSON)
    assert len(txt.encode('utf-8')) < 1000000
    return len(txt), n_prox


def daylight(sc):
    """The memory biome's sky and fog are black, so a day scene reads as night: switch the `sky` effect on from t 0
    (a soft blue dome and a pale hazy horizon, which the fog turns to) for the whole scene."""
    sc.ev(0, 'effect', 'sky', at=[0, 0, 0], style='day')


# ================================================================ the crowd
LANE_NAMES = ('north', 'mid_north', 'mid_south', 'south')
NODES = A['lanes']['nodes']
NCOL = len([k for k in NODES if k.startswith('north_')])


def nd(l, c):
    return NODES['%s_%02d' % (LANE_NAMES[l], c)]


def segment_clear(w, a, b, radius=0.35):
    n = max(2, int(math.dist(a, b) / 0.25))
    dx, dz = b[0] - a[0], b[2] - a[2]
    L_ = math.hypot(dx, dz) or 1.0
    px, pz = -dz / L_ * radius, dx / L_ * radius
    for i in range(n + 1):
        f = i / float(n)
        for ox, oz in ((0, 0), (px, pz), (-px, -pz)):
            p = [a[0] + dx * f + ox, a[1], a[2] + dz * f + oz]
            if not standable(w, p):
                return False
    return True


DIAG_OK = {}
for _l in range(3):
    for _c in range(NCOL - 1):
        for _a, _b in (((_l, _c), (_l + 1, _c + 1)), ((_l + 1, _c), (_l, _c + 1))):
            ok = segment_clear(DIAGON_W, nd(*_a), nd(*_b))
            DIAG_OK[(_a, _b)] = DIAG_OK[(_b, _a)] = ok


def skin_pool(prefix, n):
    names = ['%s_%02d' % (prefix, i) for i in range(1, n + 1)]
    have = [s for s in names if s in SKINS]
    assert have, prefix
    return have


ADULT_SKINS = skin_pool('witch', 20) + skin_pool('wizard', 20)
KEEPER_SKINS = skin_pool('shopkeeper', 6)
CHILD_SKINS = skin_pool('first_year', 10)


class Occupancy:
    """Who is where at every sampled tick (a 2-block spatial hash per tick), to accept a new walker only if it keeps
    clear of everyone already placed and of the camera."""

    def __init__(self, length_ticks, step=2):
        self.ticks = list(range(0, length_ticks + 1, step))
        self.grid = [dict() for _ in self.ticks]

    def samples(self, path):
        return samples_fast(path, self.ticks)

    def add(self, pts, r):
        for i, p in enumerate(pts):
            self.grid[i].setdefault((math.floor(p[0] / 2), math.floor(p[2] / 2)), []).append((p[0], p[1], p[2], r))

    def add_static(self, p, r):
        self.add([p] * len(self.ticks), r)

    def clear(self, pts, r):
        for i, p in enumerate(pts):
            kx, kz = math.floor(p[0] / 2), math.floor(p[2] / 2)
            g = self.grid[i]
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    for (x, y, z, r2) in g.get((kx + dx, kz + dz), ()):
                        if math.hypot(p[0] - x, p[2] - z) < r + r2 and abs(p[1] - y) < 1.6:
                            return False
        return True

    def add_camera(self, cam):
        for i, t in enumerate(self.ticks):
            p = sample_cam(cam, t)
            self.grid[i].setdefault((math.floor(p[0] / 2), math.floor(p[2] / 2)), []).append(
                (p[0], p[1] - 1.0, p[2], 0.45))


def body_r(scale):
    """Half the clearance kept between two people: 0.74 between two grown-ups (the assert's floor is 0.6)."""
    return 0.33 * scale + 0.04


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


# ---------------------------------------------------------------- what they wear
HAT_RGB = {'purple': (84, 52, 118), 'green': (42, 86, 52), 'navy': (34, 42, 90), 'black': (28, 28, 32),
           'brown': (96, 66, 42), 'burgundy': (110, 34, 46)}
SKIN_DIR = os.path.join(RES, 'assets', 'hogwartsmagic', 'textures', 'memory_skin')
STUDENT_SKINS = sorted(s for s in SKINS if s.startswith('student_') and s[-1].isdigit())
SMALL_SKINS = list(CHILD_SKINS)
KID_SKINS = SMALL_SKINS + STUDENT_SKINS[::5]
CAULDRON = 'hogwartsmagic:wizard_cauldron'
CAGE_BLOCK = 'minecraft:spawner'           # a dark wire cage
_ROBE = {}


def png_rgba(path):
    """A minimal PNG reader (8-bit RGB or RGBA, not interlaced): rows of (r, g, b, a)."""
    import struct
    import zlib
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    i, idat, hdr = 8, b'', None
    while i < len(data):
        n, typ = struct.unpack('>I4s', data[i:i + 8])
        body = data[i + 8:i + 8 + n]
        if typ == b'IHDR':
            hdr = struct.unpack('>IIBBBBB', body)
        elif typ == b'IDAT':
            idat += body
        i += 12 + n
    w, h, depth, ctype, _, _, inter = hdr
    assert depth == 8 and ctype in (2, 6) and not inter
    bpp = 4 if ctype == 6 else 3
    raw, stride = zlib.decompress(idat), w * bpp
    rows, prev, p = [], bytearray(stride), 0
    for _ in range(h):
        f, line = raw[p], bytearray(raw[p + 1:p + 1 + stride])
        p += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append([tuple(line[k:k + bpp]) + ((255,) if bpp == 3 else ()) for k in range(0, stride, bpp)])
        prev = line
    return rows


def robe_rgb(skin):
    """The mean colour of the skin's robe front (the body's front face), or None."""
    if skin not in _ROBE:
        try:
            rows = png_rgba(os.path.join(SKIN_DIR, skin + '.png'))
            px = [rows[y][x] for y in range(21, 31) for x in range(20, 28) if rows[y][x][3] > 200]
            _ROBE[skin] = tuple(sum(q[k] for q in px) / len(px) for k in range(3)) if px else None
        except (OSError, AssertionError, ValueError, TypeError):
            _ROBE[skin] = None
    return _ROBE[skin]


def hat_for(rnd, skin, kind=None):
    """A witch's hat on a witch, a wizard's hat on anyone else, its colour loosely the robe's (some at random, and
    more of them at random over the near-black robes so the street is not all black hats)."""
    kind = kind or ('witch' if skin.startswith('witch') else 'wizard')
    rgb = robe_rgb(skin)
    cols = sorted(HAT_RGB)
    dull = rgb is not None and max(rgb) - min(rgb) < 14 and max(rgb) < 70
    if rgb is None or rnd.random() < 0.3 or (dull and rnd.random() < 0.6):
        col = rnd.choice(cols)
    else:
        col = min(cols, key=lambda c: sum((rgb[k] - HAT_RGB[c][k]) ** 2 for k in range(3)))
    return 'hogwartsmagic:%s_hat_%s' % (kind, col)


# ---------------------------------------------------------------- routes on the lane graph
LZ = [nd(_l, 0)[2] for _l in range(4)]
COLX = [nd(0, _c)[0] for _c in range(NCOL)]


def lane_pt(l, x):
    return [x, 0.0, LZ[l]]


def vadd(p, o):
    return [p[0] + o[0], p[1] + o[1], p[2] + o[2]]


def route(rnd, l, x, d, dist, cols, switch=0.18, turn_back=True, lanes=(0, 1, 2, 3), jit=0.0):
    """[(pos, node)] along the lanes from lane l at x heading d for about dist blocks: on to the next column, then
    column to column, now and then drifting diagonally to a neighbouring lane in `lanes` (only where that diagonal
    is clear). At the street's end it turns back after a ('hold', s) entry, or with turn_back False it stops. jit
    shifts the whole route across the street a little, so the lanes are bands rather than single files."""
    out, walked, cur = [], 0.0, [x, 0.0, LZ[l] + jit]
    at = next((k for k in range(cols[0], cols[1] + 1) if abs(COLX[k] - x) < 0.05), None)

    def ahead(dd):
        cs = [k for k in range(cols[0], cols[1] + 1) if (COLX[k] - cur[0]) * dd > 0.05]
        return (min(cs) if dd > 0 else max(cs)) if cs else None

    c = ahead(d)
    while walked < dist:
        if c is None or not cols[0] <= c <= cols[1]:
            if not turn_back:
                break
            d = -d
            out.append(('hold', rnd.uniform(0.4, 1.2)))
            c = ahead(d)
            if c is None:
                break
        nl = l
        if at is not None and rnd.random() < switch:
            cand = l + rnd.choice((-1, 1))
            if cand in lanes and DIAG_OK.get(((l, at), (cand, c))):
                nl = cand
        p = vadd(nd(nl, c), [0.0, 0.0, jit])
        walked += math.hypot(p[0] - cur[0], p[2] - cur[2])
        out.append((p, (nl, c)))
        cur, l, at = p, nl, c
        c += d
    return out


def walk_pts(m, pts, speed, t_end, rnd=None, pause_p=0.0, pauses=None, moments=None):
    """Walk a route. At a node: a random pause facing the shop front (pause_p), or a planned moment
    (moments: {node index: (seconds, kind)}) appended to pauses as (t0, t1, kind, node)."""
    k = 0
    for it in pts:
        if m.t >= t_end:
            break
        if isinstance(it[0], str):
            m.hold(m.t + it[1])
            continue
        p, node = it
        d = math.hypot(p[0] - m.pos[0], p[2] - m.pos[2])
        if d < 1e-3:
            continue
        m.to(m.t + max(0.2, d / speed), p, turn=0.35)
        if node is None:
            continue
        k += 1
        plan = (moments or {}).get(k)
        if plan is None and pause_p and rnd.random() < pause_p and m.t < t_end - 2:
            plan = (rnd.uniform(1.4, 3.6), 'browse')
        if plan:
            secs, kind = plan
            t0, back = m.t, m.yaw
            m.face(t0 + 0.05, 180 if node[0] < 2 else 0, 0.5)
            m.hold(t0 + secs)
            m.face(t0 + secs - 0.45, back, 0.4)
            if pauses is not None:
                pauses.append((t0, t0 + secs, kind, node))
    return m.t


def approach(rnd, aid, spot, l, d, t_arr, speed, cols, yaw_end, switch=0.12, jit=None, max_walk=None):
    """A Mover that walks the lanes (heading d) and steps off lane l onto `spot` exactly at t_arr, facing yaw_end;
    it starts far enough back to be under way at t 0 (or waits at the street's end if the lanes run out). With
    max_walk (an outer lane, l 0 or 3) the walk is at most that long, and before it the person stands looking in
    at another shop front."""
    jit = rnd.uniform(-0.4, 0.4) if jit is None else jit
    if max_walk is not None:
        switch = 0.0
    back = [p for p, _ in route(rnd, l, spot[0], -d, speed * t_arr + 4, cols, switch, False, jit=jit)]
    chain = [list(spot), vadd(lane_pt(l, spot[0]), [0.0, 0.0, jit])] + back
    need, acc, start, si = speed * t_arr, 0.0, None, 0
    if max_walk is not None and need > max_walk:
        need = max_walk
        acc_ = 0.0
        for i in range(len(chain) - 1):
            seg = math.hypot(chain[i + 1][0] - chain[i][0], chain[i + 1][2] - chain[i][2])
            if seg > 1e-6 and acc_ + seg >= need:
                lp = lerp(chain[i], chain[i + 1], (need - acc_) / seg)
                front = [lp[0], 0.0, FRONT_Z[l]]
                chain = chain[:i + 1] + [lp, front]
                need = acc_ + seg * (need - acc_) / seg + math.dist(lp, front)
                break
            acc_ += seg
    for i in range(len(chain) - 1):
        seg = math.hypot(chain[i + 1][0] - chain[i][0], chain[i + 1][2] - chain[i][2])
        if seg > 1e-6 and acc + seg >= need:
            start, si = lerp(chain[i], chain[i + 1], (need - acc) / seg), i
            break
        acc += seg
    if start is None:
        start, si, need = chain[-1], len(chain) - 2, acc
    fwd = [start] + chain[si::-1]
    nxt = next((p for p in fwd[1:] if math.hypot(p[0] - start[0], p[2] - start[2]) > 0.05), spot)
    m = Mover(aid, start, yaw_to(nxt[0] - start[0], nxt[2] - start[2]) if nxt is not spot or
              math.hypot(spot[0] - start[0], spot[2] - start[2]) > 0.05 else yaw_end)
    m.hold(max(0.0, t_arr - need / speed))
    for p in fwd[1:]:
        dd = math.hypot(p[0] - m.pos[0], p[2] - m.pos[2])
        if dd > 1e-3:
            m.to(m.t + dd / speed, p, turn=0.35)
    m.face(m.t, yaw_end, 0.5)
    return m


FRONT_Z = {0: -3.7, 1: -3.7, 2: 4.7, 3: 4.7}
FRONT_YAW = {0: 180, 1: 180, 2: 0, 3: 0}


def errands(rnd, aid, cols, t_end, speed, pause=(3.0, 9.0), stretch=(8.0, 30.0), start_front=None, first=None):
    """A shopper's afternoon: a stretch along the lanes, a stop at a shop front looking in, another stretch...
    Half start under way, half at a front (start_front forces it; first is then the time they set off)."""
    x = rnd.uniform(COLX[cols[0]], COLX[cols[1]])
    stops = []
    if start_front if start_front is not None else rnd.random() < 0.5:
        side = rnd.choice((0, 3))
        m = Mover(aid, [x, 0.0, FRONT_Z[side]], FRONT_YAW[side] + rnd.uniform(-20, 20))
        t = rnd.uniform(0.5, 7.0) if first is None else first
        stops.append((0.0, t))
        lw = None
    else:
        lw = rnd.randrange(4)
        jit = rnd.uniform(-0.45, 0.45)
        m = Mover(aid, [x, 0.0, LZ[lw] + jit], 0)
        t = 0.0
    while m.t < t_end:
        d = rnd.choice((-1, 1))
        if lw is None:
            lw = rnd.randrange(4)
            jit = rnd.uniform(-0.45, 0.45)
            m.hold(t)
            p = [m.pos[0], 0.0, LZ[lw] + jit]
            m.to(m.t + max(0.3, math.dist(p, m.pos) / speed), p, turn=0.3)
        else:
            m.yaw = yaw_to(d, 0)
            m.look = [(0.0, m.yaw)]
        pts = route(rnd, lw, m.pos[0], d, rnd.uniform(*stretch), cols, switch=0.15, turn_back=True, jit=jit)
        walk_pts(m, pts, speed, t_end, rnd, 0.0, [])
        last = next((it[1] for it in reversed(pts) if not isinstance(it[0], str)), (lw, 0))
        side = 0 if last[0] < 2 else 3
        front = [m.pos[0] + rnd.uniform(-0.6, 0.6), 0.0, FRONT_Z[side]]
        m.to(m.t + max(0.3, math.dist(front, m.pos) / speed), front, turn=0.3)
        m.face(m.t, FRONT_YAW[side] + rnd.uniform(-20, 20), 0.5)
        t = m.t + rnd.uniform(*pause)
        stops.append((m.t, t))
        lw = None
    return m, stops


def depart(m, rnd, t, l, d, speed, cols, t_end, pause_p=0.08, turn_back=True, lanes=(0, 1, 2, 3), dist=None):
    """From wherever m stands, at t: onto lane l and away along the lanes heading d until t_end. With dist, only
    that far along the outer lane l (0 or 3), then on to the next shop front, to stand there looking in."""
    m.hold(t)
    jit = rnd.uniform(-0.35, 0.35) if dist is None else rnd.uniform(-0.3, 0.1) * (1 if l == 0 else -1)
    if dist is None:
        pts = [(vadd(lane_pt(l, m.pos[0]), [0.0, 0.0, jit]), None)] + route(
            rnd, l, m.pos[0], d, speed * (t_end - t) + 4, cols, turn_back=turn_back, lanes=lanes, jit=jit)
        walk_pts(m, pts, speed, t_end, rnd, pause_p, [])
        return
    pts = [(vadd(lane_pt(l, m.pos[0]), [0.0, 0.0, jit]), None)] + route(
        rnd, l, m.pos[0], d, dist, cols, switch=0.0, turn_back=True, lanes=(l,), jit=jit)
    walk_pts(m, pts, speed, t_end, rnd, 0.0, [])
    if m.t < t_end - 1:
        front = [m.pos[0] + rnd.uniform(-0.8, 0.8), 0.0, -3.7 if l == 0 else 4.7]
        m.to(m.t + math.dist(front, m.pos) / speed + 0.1, front, turn=0.3)
        m.face(m.t, 180 if l == 0 else 0, 0.5)


def mover_pos(m, t):
    """A Mover's raw position at t (linear between its keys)."""
    path = m.path
    if t <= path[0][0]:
        return list(path[0][1])
    for (t0, p0, _), (t1, p1, _) in zip(path, path[1:]):
        if t <= t1:
            return lerp(p0, p1, 0.0 if t1 == t0 else (t - t0) / (t1 - t0))
    return list(path[-1][1])


def mover_yaw(m, t):
    ks = sorted(m.look, key=lambda k: k[0])
    if t <= ks[0][0]:
        return ks[0][1]
    for (t0, y0), (t1, y1) in zip(ks, ks[1:]):
        if t <= t1:
            return y0 + (y1 - y0) * (0.0 if t1 == t0 else (t - t0) / (t1 - t0))
    return ks[-1][1]


def follow(aid, leader, off, t_from=0.0, pre=None):
    """A Mover keeping a constant world offset from the leader's path (and the leader's facing) from t_from on.
    With pre = (pos, yaw, t_go) it stands at pos until t_go, then walks into its slot by t_from."""
    if pre is None:
        m = Mover(aid, vadd(leader.path[0][1], off), leader.look[0][1])
        m.path = [(t, vadd(p, off), e) for t, p, e in leader.path]
        m.look = list(leader.look)
        return m
    pos, yaw, t_go = pre
    m = Mover(aid, pos, yaw)
    m.hold(t_go)
    m.to(t_from, vadd(mover_pos(leader, t_from), off), turn=0.3)
    m.path += [(t, vadd(p, off), e) for t, p, e in leader.path if t > t_from + 1e-6]
    m.look += [(t, y) for t, y in leader.look if t > t_from + 1e-6]
    return m


def world_ok(w, pts):
    for p in pts:
        for dy in (0.05, 0.9):
            if solid_at(w, [p[0], p[1] + dy, p[2]], sign_margin=0.28):
                return False
    return True


def set_limb(life, part, v):
    life.ch[part] = Channel(list(v))


def glance(life, r, t0, t1, every=(1.8, 4.2), yaw=35, pitch=(-12, 6)):
    """The head looking about now and then between t0 and t1."""
    t = t0 + r.uniform(0.2, 1.6)
    while t < t1 - 0.8:
        life['head'].change(t, [round(r.uniform(*pitch)), round(r.uniform(-yaw, yaw)), 0], r.uniform(0.3, 0.6))
        t += r.uniform(*every)
    if t1 - 0.6 > t0:
        life['head'].change(t1 - 0.6, [0, 0, 0], 0.4)


# ---------------------------------------------------------------- the people
class Person:
    def __init__(self, aid, skin, scale, mover=None, pos=None, yaw=0.0, r=None):
        self.aid, self.skin, self.scale, self.mover = aid, skin, scale, mover
        self.pos, self.yaw = pos, yaw
        self.r = body_r(scale) if r is None else r
        self.clips, self.life, self.poses, self.actions, self.items = Clips(), Life(), [], [], {}
        self.path = self.look = None
        self.rider_of = None
        self.props = None          # fn(person) -> [(aid, type, kwargs)] built from the final path

    def emit(self, length):
        if self.mover is not None:
            self.path, self.look = self.mover.emit(length)
        elif self.path is None:
            self.path = [{'t': 0, 'pos': [r3(c) for c in self.pos]}]
            self.look = [{'t': 0, 'yaw': round(self.yaw, 1)}]


PURPOSES = ('family', 'browser', 'chase', 'bank_queue', 'vendor', 'haggler', 'sweeper', 'arranger', 'caller',
            'chatting_pair', 'friends', 'couple', 'scroll_reader', 'late', 'cauldron', 'owl_cage', 'shopper')


class Crowd:
    """Builds the street's people purpose by purpose; each group is accepted only if every member keeps clear of
    everyone already placed (and the camera), of solid blocks, and of the rest of its own group."""

    def __init__(self, sc, seed, occ, cols):
        self.sc, self.occ, self.cols = sc, occ, cols
        self.rnd = random.Random(seed)
        self.seed = seed
        self.pre = sc.sid.split('_')[1]
        ad = list(ADULT_SKINS)
        self.rnd.shuffle(ad)
        self.witches = [s for s in ad if s.startswith('witch')]
        self.wizards = [s for s in ad if s.startswith('wizard')]
        self.kids = list(KID_SKINS)
        self.rnd.shuffle(self.kids)
        self.keepers = list(KEEPER_SKINS)
        self.rnd.shuffle(self.keepers)
        self.i = {'witch': 0, 'wizard': 0, 'kid': 0, 'keeper': 0}
        self.n = {p: 0 for p in PURPOSES}
        self.groups = {p: 0 for p in PURPOSES}
        self.adults = self.hatted = self.kid_hats = 0
        self.serial = 0

    def aid(self, kind):
        self.serial += 1
        return '%s_%s%d' % (self.pre, kind, self.serial)

    def r(self, *tag):
        return random.Random('%s|%s' % (self.seed, '|'.join(str(t) for t in tag)))

    def skin(self, kind):
        pool = {'witch': self.witches, 'wizard': self.wizards, 'kid': self.kids, 'keeper': self.keepers}[kind]
        s = pool[self.i[kind] % len(pool)]
        self.i[kind] += 1
        return s

    def adult_skin(self, r):
        return self.skin('witch' if r.random() < 0.5 else 'wizard')

    def dress(self, p, r, hat_p=0.6, main=None, off=None, kind=None):
        """Hats on about 60% of grown-ups (a witch's on a witch, a wizard's on a wizard), props in the hands."""
        if r.random() < hat_p:
            p.items['head'] = hat_for(r, p.skin, kind)
        if main:
            p.items['mainhand'] = main
        if off:
            p.items['offhand'] = off

    def parcel(self, r, p_none=0.3):
        if r.random() < p_none:
            return None
        return r.choice(PARCELS)

    def place(self, people, purpose):
        L = self.sc.length
        samples = {}
        for p in people:
            p.emit(L)
            if p.rider_of:
                continue
            pts = self.occ.samples(p.path)
            if not self.occ.clear(pts, p.r) or not world_ok(self.sc.world, pts):
                return False
            samples[p.aid] = pts
        ids = list(samples)
        for i in range(len(ids)):
            for j in range(i + 1, len(ids)):
                for a, b in zip(samples[ids[i]], samples[ids[j]]):
                    if math.hypot(a[0] - b[0], a[2] - b[2]) < 0.62 and abs(a[1] - b[1]) < 1.6:
                        return False
        by = {p.aid: p for p in people}
        for aid, pts in samples.items():
            self.occ.add(pts, by[aid].r)
        for p in people:
            self.sc.actor(p.aid, skin={'texture': p.skin}, scale=p.scale, path=p.path, look=p.look, life=p.life,
                          clips=p.clips, poses=p.poses or None, actions=p.actions or None, items=p.items or None)
            if p.rider_of:
                self.sc.skip_pairs.add(frozenset((p.aid, p.rider_of)))
            if p.props:
                for aid, typ, kw in p.props(p):
                    self.sc.actor(aid, typ, **kw)
            if p.scale >= 0.9:
                self.adults += 1
                self.hatted += 'head' in p.items
            else:
                self.kid_hats += 'head' in p.items
        self.n[purpose] += len(people)
        self.groups[purpose] += 1
        return True

    # ------------------------------------------------------------ walkers with a purpose
    def walker(self, kind, tries=250):
        L, cols = self.sc.length, self.cols
        for k in range(tries):
            r = self.r(kind, self.groups[kind], k)
            scale = r.uniform(0.93, 1.03)
            sp = {'shopper': r.uniform(0.95, 1.45), 'scroll_reader': r.uniform(0.6, 0.8),
                  'late': r.uniform(2.2, 2.7), 'cauldron': r.uniform(0.7, 0.85),
                  'owl_cage': r.uniform(0.9, 1.15)}[kind] * scale
            d = r.choice((-1, 1))
            one_way = kind == 'owl_cage'
            lanes = ((0, 1, 2) if d > 0 else (1, 2, 3)) if one_way else (0, 1, 2, 3)
            l = r.choice(lanes)
            x = r.uniform(COLX[cols[0]], COLX[cols[1]])
            aid = self.aid({'shopper': 'shopper', 'scroll_reader': 'reader', 'late': 'late',
                            'cauldron': 'hauler', 'owl_cage': 'owler'}[kind])
            m = Mover(aid, lane_pt(l, x), yaw_to(d, 0))
            if kind == 'late':           # waiting at a front, then one dash up or down the street
                m, _ = errands(r, aid, cols, L, sp, pause=(60.0, 61.0), stretch=(25.0, 45.0), start_front=True,
                               first=r.uniform(2.0, L - 18.0))
            elif kind == 'owl_cage':
                m, _ = errands(r, aid, cols, L, sp, pause=(4.0, 10.0), stretch=(10.0, 24.0))
            elif kind in ('shopper', 'cauldron', 'scroll_reader'):
                m, _ = errands(r, aid, cols, L, sp, pause=(2.0, 6.0) if kind == 'scroll_reader' else (5.0, 13.0),
                               stretch=(6.0, 20.0))
            else:
                jit = r.uniform(-0.45, 0.45) if not one_way else r.uniform(-0.2, 0.0) * d
                m = Mover(aid, vadd(lane_pt(l, x), [0.0, 0.0, jit]), yaw_to(d, 0))
                pts = route(r, l, x, d, sp * L + 6, cols, switch=0.35 if kind == 'late' else 0.18,
                            turn_back=not one_way, lanes=lanes, jit=jit)
                walk_pts(m, pts, sp, L, r, 0.0, [])
            p = Person(aid, self.adult_skin(r), scale, mover=m)
            if kind == 'shopper':
                self.dress(p, r, main=self.parcel(r), off=r.choice(PARCELS) if r.random() < 0.2 else None)
                if r.random() < 0.2:
                    t0 = r.uniform(1, max(1.5, L - 5))
                    p.clips.span(t0, t0 + r.uniform(2.0, 4.0), 'TALK', 6, 8)
                glance(p.life, r, 0, L, every=(3, 7), yaw=30)
            elif kind == 'scroll_reader':
                self.dress(p, r, main='minecraft:paper')
                t = r.uniform(0.0, 2.5)
                while t < L - 2:
                    d_ = r.uniform(6, 15)
                    p.clips.span(t, min(L - 0.5, t + d_), 'READ_SCROLL', r.randint(5, 8), r.randint(6, 10))
                    t += d_ + r.uniform(1.2, 3.0)
            elif kind == 'late':
                self.dress(p, r, hat_p=0.8, main=r.choice(('minecraft:book', 'hogwartsmagic:magic_book', None)))
                set_limb(p.life, 'head', [12, 0, 0])
            elif kind == 'cauldron':
                self.dress(p, r, main=CAULDRON)
                set_limb(p.life, 'right_arm', [-40, 0, -8])
                set_limb(p.life, 'left_arm', [-40, 0, 8])
                set_limb(p.life, 'head', [8, 0, 0])
            else:
                self.dress(p, r, main=None)
                set_limb(p.life, 'right_arm', [-6, 0, 10])
                p.r = 0.62
                p.props = lambda q, r=r: self.cage_props(q, 0.45 * q.scale, r)
            if self.place([p], kind):
                return True
        return False

    def cage_props(self, p, side, r):
        """An owl in a wire cage hanging from the carrier's right hand: the cage and the owl keep to his right side
        as he walks and turns (sampled at every key of his path and his look)."""
        s = p.scale
        ticks = sorted(set(k['t'] for k in p.path) | set(k['t'] for k in p.look))
        cage, owl, clook = [], [], []
        for t in ticks:
            q = sample_path(p.path, t)
            y = sample_look_yaw(p.look, t)
            fx, fz = -math.sin(math.radians(y)), math.cos(math.radians(y))
            rx, rz = -fz, fx                     # his right hand side
            c = [r3(q[0] + rx * side), r3(q[1] + 0.42 * s), r3(q[2] + rz * side)]
            cage.append({'t': t, 'pos': c, 'ease': 'linear'})
            owl.append({'t': t, 'pos': [c[0], r3(q[1] + 0.24 * s), c[2]], 'ease': 'linear'})
            clook.append({'t': t, 'yaw': round(y, 1), 'ease': 'linear'})
        olook, t = [], 0
        while t <= self.sc.L:
            y = sample_look_yaw(p.look, t)
            olook.append({'t': t, 'yaw': round(y + r.uniform(-70, 70), 1), 'pitch': round(r.uniform(-10, 15), 1)})
            t += tk(r.uniform(1.2, 3.0))
        return [(p.aid + '_cage', 'hogwartsmagic:memory_block', {'block': CAGE_BLOCK, 'scale': 0.4,
                                                                 'path': cage, 'look': clook}),
                (p.aid + '_owl', 'hogwartsmagic:owl', {'scale': 0.3, 'path': owl, 'look': olook})]

    # ------------------------------------------------------------ families
    FORMS = {
        # name: [(role, along, lateral)], hand pairs (i, j)
        'kid_between': ([('P', 0, 0), ('C', 0.05, 0.8), ('P', 0, 1.6)], [(0, 1), (1, 2)]),
        'parent_two_kids': ([('P', 0, 0), ('C', 0.0, 0.82), ('C', 1.0, 0.45)], [(0, 1)]),
        'ducklings': ([('P', 0, 0), ('P', 0.05, 0.8), ('C', -0.95, 0.15), ('C', -1.9, 0.3)], []),
        'shoulders': ([('P', 0, 0), ('P', 0.05, 0.85), ('C', 0.95, 0.85)], []),
        'couple_kid': ([('P', 0, 0), ('P', 0, 0.75), ('C', 1.0, 0.35)], [(0, 1)]),
        'big_family': ([('P', 0, 0), ('C', 0, 0.82), ('P', -1.0, 0.4), ('C', -1.0, 1.25)], [(0, 1), (2, 3)]),
        'parent_kid': ([('P', 0, 0), ('C', 0, 0.8)], [(0, 1)]),
        'couple': ([('P', 0, 0), ('P', 0.1, 0.8)], []),
    }

    def family(self, form, x_at=None, moments=('list', 'point'), purpose='family', tries=120):
        L, cols = self.sc.length, self.cols
        members, hands = self.FORMS[form]
        width = max(abs(o[2]) for o in members)
        for k in range(tries):
            r = self.r(form, self.groups[purpose], k)
            d = r.choice((-1, 1))
            l = r.randrange(4)
            s = 1 if l <= 1 else -1
            lanes = (0, 1, 2) if s > 0 else (1, 2, 3)
            if width < 0.9:
                lanes = (0, 1, 2, 3)
            sp = r.uniform(0.75, 1.15)
            if x_at:
                t_at, x_t = x_at
                x0 = x_t - d * sp * 0.85 * t_at + r.uniform(-3, 3)
            else:
                x0 = r.uniform(COLX[cols[0]], COLX[cols[1]])
            x0 = clamp(x0, COLX[cols[0]] + 0.5, COLX[cols[1]] - 0.5)
            lead_id = self.aid('fam')
            jit = r.uniform(-0.25, 0.25)
            lm = Mover(lead_id, vadd(lane_pt(l, x0), [0.0, 0.0, jit]), yaw_to(d, 0))
            pts = route(r, l, x0, d, sp * L + 6, cols, switch=0.12, turn_back=False, lanes=lanes, jit=jit)
            plan = {}
            if form != 'shoulders':
                for kind in moments:
                    plan[r.randint(1, 6)] = (r.uniform(3.0, 5.0), kind)
            pauses = []
            walk_pts(lm, pts, sp, L, r, 0.0, pauses, plan)
            if lm.t < L:
                pauses.append((lm.t, L + 1, 'end', None))
            people = self.family_people(r, form, lm, d, s, pauses, members, hands)
            if self.place(people, purpose):
                return True
        return False

    def family_people(self, r, form, lm, d, s, pauses, members, hands, pres=None, t_from=0.0):
        """The family's people from the leader's mover: everyone follows at a fixed world offset (`along` in the
        walking direction, `lateral` across it), hands joined while walking and let go at the stops."""
        L = self.sc.length
        people = []
        dad_first = r.random() < 0.5
        n_par = 0
        for i, (role, along, lat) in enumerate(members):
            off = [along * d, 0.0, lat * s]
            if i == 0:
                m = lm
            else:
                pre = pres.get(i) if pres else None
                m = follow(self.aid('fam'), lm, off, t_from if pre else 0.0, pre)
            if role == 'P':
                kind = 'wizard' if (n_par == 0) == dad_first else 'witch'
                n_par += 1
                p = Person(m.aid, self.skin(kind), r.uniform(0.94, 1.0), mover=m)
            else:
                small = r.random() < 0.6
                p = Person(m.aid, self.skin('kid'), r.uniform(0.7, 0.76) if small else r.uniform(0.76, 0.8),
                           mover=m)
            p.role, p.off = role, off
            people.append(p)
        stops = [(t0, t1) for t0, t1, _, _ in pauses]
        # hands
        for i, j in hands:
            a, b = people[i], people[j]
            for me, other in ((a, b), (b, a)):
                side = other.off[2] - me.off[2]
                right = d * side > 0
                part = 'right_arm' if right else 'left_arm'
                sg = 1 if right else -1
                if me.role == 'P' and other.role == 'C':
                    v = [-12, 0, 22 * sg]
                elif me.role == 'C':
                    v = [-36, 0, 12 * sg]
                else:
                    v = [-10, 0, 14 * sg]
                me.hand = part
                ch = Channel(v if t_from == 0 else [0, 0, 0])
                if t_from:
                    ch.change(t_from, v, 0.5)
                for t0, t1 in stops:
                    ch.change(t0 + 0.1, [0, 0, 0], 0.4)
                    if t1 < L:
                        ch.change(t1 - 0.3, v, 0.4)
                me.life.ch[part] = ch
        parents = [p for p in people if p.role == 'P']
        kids = [p for p in people if p.role == 'C']
        # dress: hats on most grown-ups, parcels in a free hand, a small hat on the odd little one
        for p in parents:
            free = 'left_arm' if getattr(p, 'hand', None) == 'right_arm' else 'right_arm'
            main = self.parcel(r, 0.4) if free == 'right_arm' else None
            self.dress(p, r, hat_p=0.65, main=main)
        for c in kids:
            if c.scale < 0.74 and self.kid_hats < 2 and r.random() < 0.6:
                c.items['head'] = hat_for(r, c.skin, 'wizard')
            glance(c.life, r, 0, L, every=(1.5, 3.5), yaw=45, pitch=(-20, 5))
        # the parents chat as they go
        if len(parents) == 2:
            first = r.random() < 0.5
            t = r.uniform(1, 6)
            while t < L - 4:
                for t0, t1 in conversation(r, t, t + r.uniform(4, 8), first):
                    parents[0 if first else 1].clips.span(t0, t1, 'TALK', r.randint(4, 8), r.randint(6, 10))
                    first = not first
                t += r.uniform(12, 20)
        # the stops
        lead = people[0]
        for t0, t1, kind, node in pauses:
            if kind == 'list':
                lead.items.setdefault('mainhand', 'minecraft:paper')
                lead.items['mainhand'] = 'minecraft:paper'
                lead.clips.span(t0 + 0.3, min(t1, L) - 0.3, 'READ_SCROLL', 6, 8)
                if len(parents) == 2:
                    parents[1].life['head'].change(t0 + 0.6, [18, -25 * s * d, 0], 0.5)
                    parents[1].life['head'].change(min(t1, L) - 0.5, [0, 0, 0], 0.4)
            elif kind == 'point':
                arm = 'right_arm' if getattr(lead, 'hand', None) != 'right_arm' else 'left_arm'
                base = lead.life.ch[arm].changes[0][1] if arm in lead.life.ch else [0, 0, 0]
                lead.life[arm].change(t0 + 0.5, [-100, 0, 0], 0.35)
                lead.life[arm].change(t0 + 2.3, base, 0.35)
                for c in kids:
                    c.life['head'].change(t0 + 0.7, [-18, 0, 0], 0.4)
            elif kind == 'end' and len(parents) == 2:
                for t0_, t1_ in conversation(r, t0 + 0.5, L - 0.5, True):
                    parents[1].clips.span(t0_, t1_, 'TALK', 5, 8)
        if form == 'shoulders':
            dad = people[0]
            dad.r = 0.62
            set_limb(dad.life, 'right_arm', [-100, 0, -12])
            set_limb(dad.life, 'left_arm', [-100, 0, 12])
            dad.items.pop('mainhand', None)
            rs = r.uniform(0.55, 0.6)
            h = 1.5 * dad.scale + 0.05 - 0.75 * rs
            tod = Person(self.aid('fam'), self.skin('kid'), rs)
            tod.role = 'C'
            tod.rider_of = dad.aid
            tod.mover = follow(tod.aid, dad.mover, [-0.22 * d, h, 0.0])
            tod.poses = [pose(0, 'shoulders', 1)]
            glance(tod.life, r, 0, L, every=(1.2, 3.0), yaw=55, pitch=(-15, 10))
            people.append(tod)
        if form == 'ducklings':
            parents[0].items['mainhand'] = CAULDRON
            set_limb(parents[0].life, 'right_arm', [-40, 0, -8])
            set_limb(parents[0].life, 'left_arm', [-40, 0, 8])
            parents[1].items['mainhand'] = 'hogwartsmagic:magic_book'
            parents[1].items['offhand'] = 'minecraft:book'
        return people

    def broom_tug(self):
        """At the broom window when the memory opens: a child with both hands on the glass, a parent holding the
        other hand and pulling, the other parent waiting a step up the street; at ~4.5 s they give in and go."""
        L, cols = self.sc.length, self.cols
        for k in range(60):
            r = self.r('tug', k)
            d, s, l = 1, -1, 0
            kid_spot = [-40.5, 0.0, -4.5]
            dad_spot = [-39.95, 0.0, -3.35]
            t_go = r.uniform(4.3, 4.8)
            lead_id = self.aid('fam')
            lm = Mover(lead_id, dad_spot, yaw_to(kid_spot[0] - dad_spot[0], kid_spot[2] - dad_spot[2]))
            lm.hold(t_go)
            sp = r.uniform(0.85, 1.0)
            start = lane_pt(l, -39.3)
            lm.to(t_go + math.dist(dad_spot, start) / sp, start, turn=0.3)
            pts = route(r, l, start[0], d, sp * L + 6, cols, switch=0.1, turn_back=False, lanes=(0, 1))
            pauses = []
            walk_pts(lm, pts, sp, L, r, 0.0, pauses, {r.randint(3, 6): (r.uniform(3, 4.5), 'point')})
            if lm.t < L:
                pauses.append((lm.t, L + 1, 'end', None))
            members = [('P', 0, 0), ('C', 0.05, 0.82), ('P', 0.3, -0.85)]
            t_kid = t_go + 1.3
            t_mum = t_go + 2.4
            mum_pos = vadd(mover_pos(lm, t_mum), [0.3 * d, 0, 0.85])
            pres = {1: (kid_spot, 180, t_go + 0.2), 2: (mum_pos, yaw_to(-1, 0), t_mum - 0.01)}
            people = self.family_people(r, 'tug', lm, d, s, pauses, members, [(0, 1)], pres, t_kid)
            dad, kid, mum = people
            # the second parent's slot is where she waits: she simply sets off when they reach her
            mum.mover = follow(mum.aid, lm, mum.off, t_mum, (mum_pos, yaw_to(-1, 0), t_mum - 0.05))
            set_limb(dad.life, 'right_arm', [-55, 0, 0])
            dad.life['right_arm'].change(t_go, [0, 0, 0], 0.5)
            dad.life['body'].change(0.8, [-6, 0, 0], 0.6)
            dad.life['body'].change(t_go - 0.2, [0, 0, 0], 0.4)
            dad.clips.span(1.4, 3.3, 'TALK', 5, 6)
            c0 = kid.life.ch['right_arm'].changes[0]
            kid.life.ch['right_arm'].changes[0] = (c0[0], [40, 0, 10]) + c0[2:]
            kid.life['right_arm'].change(t_kid, [-36, 0, 12], 0.5)
            set_limb(kid.life, 'left_arm', [-115, 0, -10])
            kid.life['left_arm'].change(t_go - 0.3, [0, 0, 0], 0.4)
            kid.life['body'].change(0.3, [10, 0, 0], 0.5)
            kid.life['body'].change(t_go - 0.3, [0, 0, 0], 0.4)
            kid.life['head'].change(2.0, [-10, 40, 0], 0.4)          # a pleading look back
            kid.life['head'].change(3.0, [-14, 0, 0], 0.4)
            mum.life['head'].change(1.0, [0, 20, 0], 0.6)
            mum.clips.span(2.6, 3.9, 'TALK', 5, 6)
            if self.place(people, 'family'):
                return True
        return False

    # ------------------------------------------------------------ at the windows
    def browser(self, spot, yaw, t_arr, t_leave, tag, child=False, talk=False, point=False):
        L, cols = self.sc.length, self.cols
        l = 0 if spot[2] < 0 else 3
        for k in range(24):
            r = self.r('browse', tag, k)
            scale = r.uniform(0.72, 0.78) if child else r.uniform(0.93, 1.02)
            sp = r.uniform(0.9, 1.35) * (1.0 if child else scale)
            aid = self.aid('browser')
            if t_arr <= 0:
                m = Mover(aid, spot, yaw)
            else:
                m = approach(r, aid, spot, l, r.choice((-1, 1)), t_arr, sp, cols, yaw, max_walk=r.uniform(10, 26))
            if t_leave < L - 0.5:
                depart(m, r, t_leave, l, r.choice((-1, 1)), sp, cols, L, dist=r.uniform(10, 28))
            p = Person(aid, self.skin('kid') if child else self.adult_skin(r), scale, mover=m)
            if child:
                p.poses = [pose(max(0.0, t_arr) + 0.4, 'glass', 6)]
                if t_leave < L:
                    p.poses.append(pose(t_leave - 0.2, 'standing', 5))
            else:
                self.dress(p, r, main=self.parcel(r, 0.5))
            a, b = max(0.0, t_arr) + 0.6, min(L, t_leave)
            glance(p.life, r, a, b, every=(1.6, 3.4), yaw=28, pitch=(-14, 6))
            if talk and b - a > 4:
                for t0, t1 in conversation(r, a + r.uniform(0.5, 2), b - 0.5, r.random() < 0.5):
                    p.clips.span(t0, t1, 'TALK', r.randint(4, 8), r.randint(6, 10))
            if point and b - a > 3:
                t0 = r.uniform(a + 0.5, b - 2.5)
                p.life.pulse('right_arm', t0, t0 + r.uniform(1.2, 2.0), [-95, 0, r.uniform(-8, 8)], 0.3)
            if self.place([p], 'browser'):
                return True
        return False

    def window(self, name, shot):
        """Browsers at a shop window coming and going all through the scene; the window's own mark is sure to be
        taken through its shot, and one or two of the others turn over during it."""
        W = {w['name']: w for w in A['window_spots']}[name]
        spots = [W['view']['pos']] + [c['pos'] for c in W['crowd_spots']]
        yaw = W['view']['yaw']
        L = self.sc.length
        for i, sp in enumerate(spots):
            r = self.r('window', name, i)
            if i == 0 and shot:
                visits = [(shot[0] - r.uniform(3, 8), shot[1] + r.uniform(-1.5, 4))]
                t = visits[0][1] + r.uniform(3, 6)
            else:
                visits, t = [], r.uniform(-10, 5)
            while t < L - 4:
                stay = r.uniform(5, 14)
                visits.append((t, t + stay))
                t += stay + r.uniform(2.5, 7)
            if i == 0 and shot:
                t, visits_before = r.uniform(-10, 0), []
                while t + 5 < visits[0][0] - 3:
                    stay = min(r.uniform(5, 12), visits[0][0] - 3 - t)
                    visits_before.append((t, t + stay))
                    t += stay + r.uniform(2.5, 6)
                visits = visits_before + visits
            for j, (a, b) in enumerate(visits):
                if b < 2.0:
                    continue
                self.browser(sp, yaw + r.uniform(-15, 15), a, b, '%s%d.%d' % (name, i, j),
                             talk=r.random() < 0.35, point=r.random() < 0.3)

    # ------------------------------------------------------------ children chasing each other
    def chase(self, centre, a, b, t_end, n=3):
        L = self.sc.length
        for k in range(20):
            r = self.r('chase', k)
            speed = r.uniform(2.6, 3.0)
            perim = 2 * math.pi * math.sqrt((a * a + b * b) / 2)
            lap = perim / speed
            people = []
            for i in range(n):
                ph = i / float(n) + r.uniform(-0.03, 0.03)
                wob = r.uniform(-0.08, 0.08)

                def at(f, ph=ph, wob=wob):
                    ang = 2 * math.pi * (f + ph)
                    return [centre[0] + (a + wob) * math.cos(ang), 0.0, centre[2] + (b + wob) * math.sin(ang)]
                aid = self.aid('chaser')
                m = Mover(aid, at(0.0), 0)
                f, step = 0.0, 1.0 / 20
                stop = t_end + 0.35 * i
                while m.t < stop:
                    f += step
                    m.to(min(stop, m.t + lap * step), at(f), turn=0.12)
                p = Person(aid, self.skin('kid'), r.uniform(0.7, 0.78), mover=m)
                set_limb(p.life, 'head', [-8, 0, 0])
                people.append(p)
            if self.place(people, 'chase'):
                return True
        return False

    # ------------------------------------------------------------ Gringotts' queue
    @staticmethod
    def stair_pts(xa, xb, z):
        """Waypoints over Gringotts' steps from x xa to xb at z: up a step is a rise then a stride, down a stride
        then a drop (stations: the street edge 44.75 y 0, the steps 45.75..50.75 y 1..6, the portico y 6)."""
        def level(x):
            return 0.0 if x < 45 else float(min(6, math.floor(x - 45) + 1))
        lo, hi = min(xa, xb), max(xa, xb)
        pts = [[lo, level(lo), z]]
        x, y = lo, level(lo)
        for kk in range(0, 6):
            xs = 44.75 + kk
            if xs < x - 1e-6 or xs >= hi - 1e-6:
                continue
            if xs > x + 1e-6:
                pts.append([xs, y, z])
            pts.append([xs, y + 1, z])
            x, y = xs + 1.0, y + 1
            pts.append([min(x, hi), y, z])
            x = min(x, hi)
        if hi > x + 1e-6:
            pts.append([hi, y, z])
        return pts if xa <= xb else pts[::-1]

    def stair_walk(self, m, t, xb, speed=1.4):
        m.hold(t)
        for p in self.stair_pts(m.pos[0], xb, m.pos[2])[1:]:
            dx = abs(p[0] - m.pos[0])
            m.to(m.t + (max(0.15, dx / speed) if dx > 1e-6 else 0.22), p, turn=0.25)
        return m.t

    def bank_queue(self, n0=7):
        L, cols = self.sc.length, self.cols
        Z, EZ = 1.7, -0.6
        SLOTS = [53.6, 52.6, 51.6, 50.75, 49.75, 48.75, 47.75, 46.75, 45.75]
        gob = A['goblin_doors'][1]['pos']

        def slot_pos(i):
            x = SLOTS[i]
            return [x, 6.0 if x >= 50.7 else float(math.floor(x - 45) + 1), Z]

        for k in range(30):
            r = self.r('bank', k)
            people, queue = [], []
            for i in range(n0):
                aid = self.aid('bank')
                m = Mover(aid, slot_pos(i), -90 + r.uniform(-15, 15))
                p = Person(aid, self.adult_skin(r), r.uniform(0.93, 1.02), mover=m)
                people.append(p)
                queue.append(p)
            T = r.uniform(2.5, 4.0)
            while T < L - 3 and queue:
                front = queue.pop(0)
                fm = front.mover
                fm.hold(T)
                fm.face_point(T, gob, 0.5)
                front.clips.span(T + 0.3, T + 2.2, 'TALK', 5, 7)
                fm.hold(T + 2.6)
                fm.to(fm.t + 2.5 / 1.3, [52.6, 6.0, EZ], turn=0.3)
                self.stair_walk(fm, fm.t, 44.0)
                depart(fm, r, fm.t, 0, -1, r.uniform(1.0, 1.3), cols, L, dist=r.uniform(8, 24))
                for i, p in enumerate(queue):
                    ts = max(p.mover.t, T + 3.0 + 0.5 * i + r.uniform(0, 0.25))
                    self.stair_walk(p.mover, ts, SLOTS[i], speed=1.2)
                ta = T + 6.2
                if len(queue) < len(SLOTS) and ta < L - 2 and r.random() < 0.8:
                    aid = self.aid('bank')
                    idx = len(queue)
                    climb = self.stair_pts(44.0, SLOTS[idx], Z)
                    t_climb = sum(max(0.15, abs(q[0] - p_[0]) / 1.2) if abs(q[0] - p_[0]) > 1e-6 else 0.22
                                  for p_, q in zip(climb, climb[1:]))
                    sp = r.uniform(1.0, 1.3)
                    m = approach(r, aid, [44.0, 0.0, Z], 3, 1, ta - t_climb, sp, cols, -90, max_walk=r.uniform(10, 22))
                    self.stair_walk(m, m.t, SLOTS[idx], speed=1.2)
                    p = Person(aid, self.adult_skin(r), r.uniform(0.93, 1.02), mover=m)
                    people.append(p)
                    queue.append(p)
                T += r.uniform(6.6, 8.0)
            for i, p in enumerate(people):
                self.dress(p, r, main=self.parcel(r, 0.5))
                glance(p.life, r, 0, L, every=(2.0, 4.5), yaw=30)
                q = r.random()
                if q < 0.15:
                    t0 = r.uniform(2, L - 10)
                    p.clips.span(t0, t0 + r.uniform(4, 8), 'NERVOUS', 6, 8)
                elif q < 0.25:
                    p.items['mainhand'] = 'minecraft:paper'
                    p.clips.span(r.uniform(0.5, 3), L - 0.5, 'READ_SCROLL', 6, 8)
            if self.place(people, 'bank_queue'):
                return True
        return False

    # ------------------------------------------------------------ stalls: vendors and hagglers
    def stall(self, side, x0, x1, haggle_at=None):
        L, cols = self.sc.length, self.cols
        north = side == 'N'
        vz = -4.5 if north else 5.5
        street_yaw = 0 if north else 180
        for k in range(20):
            r = self.r('stall', x0, k)
            vx = (x0 - 0.5) if k % 2 == 0 else (x1 + 1.5)
            vpos = [vx, 0.0, vz]
            if not standable(self.sc.world, vpos):
                continue
            aid = self.aid('vendor')
            vm = Mover(aid, vpos, street_yaw + r.uniform(-20, 20))
            vendor = Person(aid, self.skin('keeper'), r.uniform(0.95, 1.02), mover=vm)
            self.dress(vendor, r, hat_p=0.45, main=r.choice(('minecraft:potion', 'minecraft:flower_pot',
                                                             'minecraft:apple', 'minecraft:glass_bottle')))
            people = [vendor]
            busy = []
            if haggle_at is not None:
                cx = (x0 + 0.5) if k % 2 == 0 else (x1 + 0.5)
                cpos = [cx, 0.0, -3.5 if north else 4.5]
                t_h = haggle_at + r.uniform(-1.5, 1.5)
                dur = r.uniform(7, 11)
                t_e = t_h + dur
                cid = self.aid('haggler')
                cm = approach(r, cid, cpos, 0 if north else 3, r.choice((-1, 1)), max(0.0, t_h), r.uniform(0.9, 1.25),
                              cols, 180 if north else 0, max_walk=r.uniform(10, 26))
                if t_e < L - 0.5:
                    depart(cm, r, t_e, 0 if north else 3, r.choice((-1, 1)), r.uniform(0.9, 1.25), cols, L,
                           dist=r.uniform(10, 28))
                cust = Person(cid, self.adult_skin(r), r.uniform(0.93, 1.02), mover=cm)
                self.dress(cust, r)
                vm.face_point(max(0.0, t_h) + 0.2, cpos, 0.5)
                vm.face(min(L - 0.5, t_e + 0.3), street_yaw + r.uniform(-15, 15), 0.6)
                first = r.random() < 0.5
                for who in (vendor, cust):
                    for t0, t1 in conversation(r, max(0.0, t_h) + 0.6, t_e - 2.0, first):
                        who.clips.span(t0, t1, 'TALK', r.randint(4, 8), r.randint(6, 10))
                    first = not first
                tm = max(0.0, t_h) + dur * 0.45
                for i, yv in enumerate((-22, 22, -18, 0)):              # a shake of the head: too dear
                    cust.life['head'].change(tm + 0.22 * i, [0, yv, 0], 0.18)
                vendor.life.pulse('right_arm', t_e - 1.9, t_e - 0.7, [-70, 0, 0], 0.3)
                cust.life.pulse('right_arm', t_e - 1.6, t_e - 0.5, [-62, 0, 0], 0.3)
                cust.actions.append(act(t_e - 1.1, 'equip', slot='mainhand',
                                        item=r.choice(('minecraft:bundle', 'minecraft:potion', 'minecraft:book'))))
                busy.append((max(0.0, t_h) - 1, t_e + 1))
                people.append(cust)
            # calling out to the street between customers
            t = r.uniform(0.5, 4)
            while t < L - 3:
                if any(a <= t <= b for a, b in busy):
                    t = max(b for a, b in busy if a <= t <= b) + 0.5
                    continue
                if r.random() < 0.3:
                    d_ = r.uniform(1.2, 1.8)
                    vendor.clips.span(t, t + d_, 'WAVE', 5, 6)
                else:
                    d_ = r.uniform(1.6, 2.6)
                    vendor.clips.span(t, t + d_, 'TALK', 5, 8)
                t += d_ + r.uniform(2.5, 6.5)
            if self.place(people, 'vendor'):
                self.n['vendor'] -= len(people) - 1
                self.n['haggler'] += len(people) - 1
                self.groups['haggler'] += len(people) > 1
                return True
        return False

    # ------------------------------------------------------------ shopkeepers
    def doors(self):
        out = []
        x_lo, x_hi = COLX[self.cols[0]] - 2, COLX[self.cols[1]] + 2
        for s, rows in (('N', DS.NORTH), ('S', DS.SOUTH)):
            for x0, x1, _, spec in rows:
                if spec.get('shop') in ('ollivanders', 'quidditch'):
                    continue
                dx = (x0 + 1 if spec.get('door_left') else x1 - 1) + DS.DIAGON_TEMPLATE_AT[0] + 0.5
                if x_lo <= dx <= x_hi:
                    out.append(([dx, 0.0, -4.4 if s == 'N' else 5.4], 0 if s == 'N' else 180))
        self.rnd.shuffle(out)
        return out

    def keeper(self, kind, doors):
        L = self.sc.length
        for door, yaw in list(doors):
            r = self.r(kind, door[0])
            aid = self.aid(kind)
            people = []
            if kind == 'caller':
                if not standable(self.sc.world, door):
                    continue
                p = Person(aid, self.skin('keeper'), r.uniform(0.95, 1.02), pos=door, yaw=yaw + r.uniform(-25, 25))
                t = r.uniform(1, 6)
                while t < L - 3:
                    d_ = r.uniform(1.6, 3.0)
                    p.clips.span(t, t + d_, 'TALK', 5, 8)
                    t += d_ + r.uniform(4, 9)
                self.dress(p, r, hat_p=0.4)
            elif kind == 'sweeper':
                xa, xb = door[0] - 1.3, door[0] + 1.3
                m = Mover(aid, [xa, 0.0, door[2]], yaw - 35)
                p = Person(aid, self.skin('keeper'), r.uniform(0.95, 1.02), mover=m)
                p.items['mainhand'] = 'hogwartsmagic:broom'
                self.dress(p, r, hat_p=0.35, main='hogwartsmagic:broom')
                t, there = r.uniform(0, 1.5), xb
                ra, la = p.life['right_arm'], p.life['left_arm']
                while t < L - 1:
                    d_ = min(r.uniform(6, 10), L - 0.5 - t)
                    m.shift(t, t + d_, [there, 0.0, door[2]], 'linear')
                    m.face(t, yaw + (35 if there < door[0] else -35) * (1 if yaw == 0 else -1), 0.5)
                    p.life['head'].change(t, [24, 0, 0], 0.4)
                    p.life['body'].change(t, [10, 0, 0], 0.4)
                    tt, sg, per = t + 0.3, 1, r.uniform(0.45, 0.6)
                    while tt < t + d_ - 0.3:
                        ra.change(tt, [-40, 22 * sg, 0], per, hold=False)
                        la.change(tt, [-34, 22 * sg, 0], per, hold=False)
                        tt += per
                        sg = -sg
                    ra.change(t + d_ - 0.2, [-14, 0, 0], 0.4, hold=False)
                    la.change(t + d_ - 0.2, [-10, 0, 0], 0.4, hold=False)
                    p.life['head'].change(t + d_, [0, 0, 0], 0.4)
                    p.life['body'].change(t + d_, [0, 0, 0], 0.4)
                    rest = r.uniform(3, 6)
                    if r.random() < 0.4:
                        p.clips.span(t + d_ + 0.5, t + d_ + rest - 0.4, 'TALK', 5, 8)
                    there = xa if there == xb else xb
                    t += d_ + rest
            else:   # arranger: carries goods between two spots along the front, stooping to set each down
                xa, xb = door[0] - 1.6, door[0] + 0.0
                m = Mover(aid, [xa, 0.0, door[2]], yaw)
                p = Person(aid, self.skin('keeper'), r.uniform(0.95, 1.02), mover=m)
                self.dress(p, r, hat_p=0.4)
                t, here = r.uniform(0.5, 2), xa
                goods = ('minecraft:book', 'minecraft:potion', 'minecraft:flower_pot', 'minecraft:bundle',
                         'hogwartsmagic:magic_book', 'minecraft:glass_bottle')
                while t < L - 3:
                    p.life['body'].change(t, [30, 0, 0], 0.4)
                    p.life['right_arm'].change(t, [-75, 0, 4], 0.4)
                    p.life['left_arm'].change(t, [-75, 0, -4], 0.4)
                    p.actions.append(act(t + 0.5, 'equip', slot='mainhand', item=r.choice(goods)))
                    p.life['body'].change(t + 1.3, [0, 0, 0], 0.4)
                    p.life['right_arm'].change(t + 1.3, [-38, 0, 0], 0.4)
                    p.life['left_arm'].change(t + 1.3, [-38, 0, 0], 0.4)
                    there = xb if here == xa else xa
                    m.hold(t + 1.6)
                    m.to(t + 1.6 + abs(there - here) / 0.9, [there, 0.0, door[2]], turn=0.3)
                    m.face(m.t, yaw, 0.4)
                    t2 = m.t + 0.2
                    p.life['body'].change(t2, [30, 0, 0], 0.4)
                    p.life['right_arm'].change(t2, [-75, 0, 4], 0.4)
                    p.life['left_arm'].change(t2, [-75, 0, -4], 0.4)
                    p.actions.append(act(t2 + 0.6, 'equip', slot='mainhand', item='minecraft:air'))
                    p.life['body'].change(t2 + 1.2, [0, 0, 0], 0.4)
                    p.life['right_arm'].change(t2 + 1.2, [0, 0, 0], 0.4)
                    p.life['left_arm'].change(t2 + 1.2, [0, 0, 0], 0.4)
                    here = there
                    t = t2 + r.uniform(2.2, 4.5)
            people.append(p)
            if self.place(people, kind):
                doors.remove((door, yaw))
                return True
        return False

    # ------------------------------------------------------------ people meeting
    def chatting_pair(self, tries=200):
        L = self.sc.length
        x_lo, x_hi = COLX[self.cols[0]] - 2, COLX[self.cols[1]] + 2
        for k in range(tries):
            r = self.r('pair', self.groups['chatting_pair'], k)
            side = r.choice((-1, 1))
            z = -4.35 if side < 0 else 4.65
            x = round(r.uniform(x_lo, x_hi - 1.2), 2)
            a, b = [x, 0.0, z], [x + 1.1, 0.0, z]
            if not (standable(self.sc.world, a) and standable(self.sc.world, b)):
                continue
            first = r.random() < 0.5
            people = []
            for i, (p_, q) in enumerate(((a, b), (b, a))):
                y = yaw_to(q[0] - p_[0], q[2] - p_[2]) + (22 if (side < 0) == (i == 0) else -22)
                p = Person(self.aid('chat'), self.adult_skin(r), r.uniform(0.92, 1.02), pos=p_, yaw=y)
                p.r += 0.2
                for t0, t1 in conversation(r, r.uniform(0.2, 1.5), L - 0.4, first if i == 0 else not first):
                    p.clips.span(t0, t1, 'TALK', r.randint(4, 8), r.randint(6, 10))
                self.dress(p, r, main=self.parcel(r, 0.55))
                people.append(p)
            if self.place(people, 'chatting_pair'):
                return True
        return False

    def friends(self, t_meet=None, tries=120):
        """Two friends coming up the same lane from opposite ends spot each other, wave, stop for a chat, and go
        off together side by side."""
        L, cols = self.sc.length, self.cols
        for k in range(tries):
            r = self.r('friends', self.groups['friends'], k)
            tm = t_meet + r.uniform(-2, 2) if t_meet else r.uniform(5, L - 14)
            d = r.choice((-1, 1))
            l = r.randrange(4)
            s = 1 if l <= 1 else -1
            xm = r.uniform(COLX[cols[0]] + 2, COLX[cols[1]] - 3)
            sa, sb = r.uniform(0.95, 1.35), r.uniform(0.95, 1.35)
            ida, idb = self.aid('friend'), self.aid('friend')
            ma = approach(r, ida, lane_pt(l, xm), l, d, tm, sa, cols, yaw_to(d, 0), jit=0.0, max_walk=r.uniform(10, 24))
            tb = tm + r.uniform(0.2, 0.6)
            mb = approach(r, idb, lane_pt(l, xm + 1.25 * d), l, -d, tb, sb, cols, yaw_to(-d, 0), jit=0.0,
                          max_walk=r.uniform(10, 24))
            t_go = tm + r.uniform(5, 9)
            sp = min(sa, sb) * 0.9
            ma.hold(t_go)
            pts = route(r, l, xm, d, sp * (L - t_go) + 4, cols, switch=0.12, turn_back=False,
                        lanes=(0, 1, 2) if s > 0 else (1, 2, 3))
            walk_pts(ma, pts, sp, L, r, 0.0, [])
            off = [0.3 * d, 0.0, 0.75 * s]
            mb.hold(t_go)
            mb.to(t_go + 0.7, vadd(mover_pos(ma, t_go + 0.7), off), turn=0.3)
            mb.path += [(t, vadd(p, off), e) for t, p, e in ma.path if t > t_go + 0.7 + 1e-6]
            mb.look += [(t, y) for t, y in ma.look if t > t_go + 0.7 + 1e-6]
            A_ = Person(ida, self.adult_skin(r), r.uniform(0.93, 1.02), mover=ma)
            B_ = Person(idb, self.adult_skin(r), r.uniform(0.93, 1.02), mover=mb)
            A_.clips.span(tm - 1.6, tm + 0.4, 'WAVE', 5, 6)
            B_.clips.span(tm - 1.1, tm + 0.7, 'WAVE', 5, 6)
            first = r.random() < 0.5
            for who in (A_, B_):
                for t0, t1 in conversation(r, tm + 1.0, t_go - 0.3, first):
                    who.clips.span(t0, t1, 'TALK', r.randint(4, 8), r.randint(6, 10))
                first = not first
            for t0, t1 in conversation(r, t_go + 1.5, min(L - 0.5, t_go + 9), True):
                A_.clips.span(t0, t1, 'TALK', 5, 8)
            for p in (A_, B_):
                self.dress(p, r, main=self.parcel(r, 0.4))
            if self.place([A_, B_], 'friends'):
                return True
        return False


def sample_look_yaw(look, t):
    if t <= look[0]['t']:
        return look[0]['yaw']
    for a, b in zip(look, look[1:]):
        if t <= b['t']:
            return a['yaw'] + (b['yaw'] - a['yaw']) * (t - a['t']) / float(b['t'] - a['t'])
    return look[-1]['yaw']


def populate(sc, seed, occ, cols, prof):
    """The street's people for one scene, most fixed first (so the walkers flow round them)."""
    c = Crowd(sc, seed, occ, cols)
    if prof.get('tug'):
        c.broom_tug()
    if prof.get('chase'):
        c.chase(*prof['chase'])
    if prof.get('bank'):
        c.bank_queue()
    for spec in prof.get('glass_kids', ()):
        c.browser(*spec, child=True)
    for i, (s, x0, x1, k) in enumerate(DS.STALLS):
        h = prof.get('haggles', {}).get(i)
        sx0, sx1 = x0 + DS.DIAGON_TEMPLATE_AT[0], x1 + DS.DIAGON_TEMPLATE_AT[0]
        if COLX[cols[0]] - 3 <= sx0 and sx1 <= COLX[cols[1]] + 3:
            c.stall(s, sx0, sx1, h)
    for i, (form, x_at) in enumerate(prof.get('families', ())):
        c.family(form, x_at, moments=(('list',), ('point',), ('list', 'point'), ())[i % 4])
    for t in prof.get('friends', ()):
        c.friends(t)
    for _ in range(prof.get('owl_cage', 0)):
        c.walker('owl_cage')
    for _ in range(prof.get('late', 0)):
        c.walker('late')
    for name, shot in prof.get('windows', ()):
        c.window(name, shot)
    doors = c.doors()
    for kind in ('sweeper',) * prof.get('sweepers', 0) + ('arranger',) * prof.get('arrangers', 0) +             ('caller',) * prof.get('callers', 0):
        c.keeper(kind, doors)
    for _ in range(prof.get('pairs', 0)):
        c.chatting_pair()
    for _ in range(prof.get('couples', 0)):
        c.family('couple', None, moments=('point',), purpose='couple')
    for kind in ('scroll_reader', 'cauldron', 'shopper'):
        for _ in range(prof.get(kind, 0)):
            c.walker(kind)
    return c


# ---------------------------------------------------------------- who is in each street
SHOULDERS = {'right_leg': [-75, 0, 28], 'left_leg': [-75, 0, -28], 'right_arm': [-55, 0, 8], 'left_arm': [-55, 0, -8]}
WALL_CROWD = {
    'haggles': {3: 13.0}, 'sweepers': 1, 'pairs': 2,
    'families': [('kid_between', (15, -58)), ('shoulders', (16, -50))],
    'friends': [14], 'couples': 1,
    'scroll_reader': 1, 'late': 1, 'cauldron': 1, 'owl_cage': 1, 'shopper': 10,
}
ALLEY_CROWD = {
    'tug': True,
    'windows': [('eeylops', (17.0, 21.5)), ('apothecary', (21.5, 25.5)), ('flourish_blotts', (30.5, 34.5)),
                ('cauldrons', None)],
    'chase': ([-52.0, 0.0, -3.3], 2.0, 0.85, 26.0),
    'bank': True,
    'haggles': {1: 18.0, 2: 31.0, 5: 36.0}, 'sweepers': 2, 'arrangers': 1, 'callers': 2, 'pairs': 4,
    'families': [('kid_between', (3, -50)), ('parent_two_kids', (9, -45)), ('shoulders', (14, -38)),
                 ('ducklings', (23, -14)), ('big_family', (28, -2)), ('couple_kid', (33, -9)),
                 ('parent_kid', (37, 22))],
    'friends': [8, 27, 46], 'couples': 2,
    'scroll_reader': 3, 'late': 2, 'cauldron': 2, 'owl_cage': 2, 'shopper': 26,
}


# ================================================================ the set pieces
SET_DIAGON = {'templates': [{'id': 'hogwartsmagic:wand_diagon', 'at': list(DS.DIAGON_TEMPLATE_AT)}],
              'time_of_day': DS.TIME_OF_DAY, 'build_ticks': 100}
SET_OLLI = {'templates': [{'id': 'hogwartsmagic:wand_ollivanders', 'at': list(DS.OLLIVANDERS_TEMPLATE_AT)}],
            'time_of_day': DS.TIME_OF_DAY, 'build_ticks': 60}
BRICK = A['wall_block']
FIRST = A['first_brick']


def centre(c):
    return [c[0] + 0.5, c[1] + 0.5, c[2] + 0.5]


def brick_actors(sc, moving, t_ripple=0.0, t_quiver=0.0):
    """The archway's bricks. Static: each in its place in the arch's surround. Moving: each starts flush in the
    wall and, at its stagger time, leaves the wall plane first (x eases out fast) and then twists and slides into
    its place (y and z smoothstep), one full turn about a random axis; the first brick quivers before."""
    plans = []
    rnd = random.Random(1991)
    for e in A['wall_bricks']:
        S, E = centre(e['cell']), centre(e['target_cell'])
        aid = 'brick_%02d' % e['rank']
        if not moving:
            sc.actor(aid, 'hogwartsmagic:memory_block', block=BRICK, path=[{'t': 0, 'pos': [r3(c) for c in E]}])
            continue
        dyz = math.hypot(e['cell'][1] - FIRST[1], e['cell'][2] - FIRST[2])
        yard_layer = e['cell'][0] == A['arch_opening']['face_yard_x']
        t0 = t_ripple + 0.42 * dyz + (0.22 if yard_layer else 0.0)
        D = rnd.uniform(1.5, 2.0)
        axis = rnd.choice(('yaw', 'pitch', 'roll'))
        spin = rnd.choice((-360, 360))
        path = [{'t': 0, 'pos': [r3(c) for c in S]}]
        look = [{'t': 0, 'yaw': 0, 'pitch': 0, 'roll': 0}]
        if e['rank'] == 0:                       # the quiver: a nervous shiver, then a jolt
            t = t_quiver
            i = 0
            while t < t_ripple - 0.05:
                a = 0.035 * (1 if i % 2 else -1) * (0.6 + 0.4 * i / 10)
                path.append({'t': tk(t), 'pos': [r3(S[0] + abs(a)), r3(S[1]), r3(S[2] + a * 0.5)], 'ease': 'linear'})
                look.append({'t': tk(t), 'yaw': 0, 'pitch': 0, 'roll': round(70 * a, 1), 'ease': 'linear'})
                t += 0.1
                i += 1
            path.append({'t': tk(t_ripple - 0.05) if tk(t_ripple - 0.05) > path[-1]['t'] else path[-1]['t'] + 1,
                         'pos': [r3(c) for c in S], 'ease': 'linear'})
            look.append({'t': path[-1]['t'], 'yaw': 0, 'pitch': 0, 'roll': 0, 'ease': 'linear'})
        samples = []
        for i in range(11):
            s = i / 10.0
            x = S[0] + (E[0] - S[0]) * ease_out(s / 0.4) + 0.7 * math.sin(math.pi * s)
            f = smooth((s - 0.3) / 0.7)
            p = [x, S[1] + (E[1] - S[1]) * f, S[2] + (E[2] - S[2]) * f]
            rot = spin * smooth((s - 0.1) / 0.8)
            samples.append((t0 + s * D, p, rot))
        for t, p, rot in samples:
            tt = tk(t)
            if tt <= path[-1]['t']:
                tt = path[-1]['t'] + 1
            path.append({'t': tt, 'pos': [r3(c) for c in p], 'ease': 'linear'})
            lk = {'t': tt, 'yaw': 0, 'pitch': 0, 'roll': 0, 'ease': 'linear'}
            lk[axis] = round(rot, 1)
            look.append(lk)
        sc.actor(aid, 'hogwartsmagic:memory_block', block=BRICK, path=path, look=look)
        plans.append((aid, t0, D, S, E))
    return plans


def actor_box(a, t):
    """The axis-aligned box of a player actor at tick t: (x0, x1, y0, y1, z0, z1)."""
    p = sample_path(a['path'], t)
    sc_ = a.get('scale', 1.0)
    r = 0.3 * sc_
    return (p[0] - r, p[0] + r, p[1], p[1] + 1.85 * sc_, p[2] - r, p[2] + r)


def cube_hits_box(c, h, b):
    return (c[0] + h > b[0] and c[0] - h < b[1] and c[1] + h > b[2] and c[1] - h < b[3]
            and c[2] + h > b[4] and c[2] - h < b[5])


def check_blocks(sc, scene, ids_prefix, half, people, problems, world_check=True, shrink=0.12):
    """Every memory_block whose id starts with ids_prefix: its cube (half size `half`) never inside one of the
    people's boxes, and (shrunk a little) never inside a solid cell, at every tick it moves."""
    acts = {a['id']: a for a in scene['actors']}
    for a in scene['actors']:
        if not a['id'].startswith(ids_prefix) or len(a['path']) < 2:
            continue
        for t in range(a['path'][1]['t'] - 1, a['path'][-1]['t'] + 1):
            c = sample_path(a['path'], t)
            for pid in people:
                if cube_hits_box(c, half, actor_box(acts[pid], t)):
                    problems.append('%s at t %d (%s) passes through %s' % (a['id'], t, [r3(v) for v in c], pid))
                    break
            if world_check:
                h = half - shrink
                for dx in (-h, h):
                    for dy in (-h, h):
                        for dz in (-h, h):
                            q = [c[0] + dx, c[1] + dy, c[2] + dz]
                            if not is_air(sc.world, q):
                                problems.append('%s at t %d passes through %s at %s' % (
                                    a['id'], t, sc.world.get(cell(q)), cell(q)))
                                return


# ================================================================ wand_wall
def wall_scene():
    LEN = 21.8
    sc = Scene(WALL_ID, 'The Wall', LEN, DIAGON_W)
    daylight(sc)
    sc.poses = {'standing': {}, 'shoulders': SHOULDERS}
    hy, by = A['hagrid_yard']['pos'], A['boy_yard']['pos']
    T_STEP, T_LINE, TAPS, T_BACK = 1.2, 2.2, (3.3, 4.7, 6.1), 8.0     # the taps on the counting; back on "Stand back"
    T_QUIVER, T_RIPPLE, T_WALK = 10.5, 11.7, 16.8

    # ---- Hagrid: steps up, taps, steps back, leads through the arch
    hm = Mover('hagrid', hy, -90)
    tap_x = A['arch_opening']['face_yard_x'] - 1.2
    hm.shift(T_STEP, T_STEP + 1.1, [tap_x, 0, hy[2]])
    hm.shift(T_BACK, T_BACK + 1.2, [-71.9, 0, 2.6])
    hm.walk(T_WALK, [[-70.0, 0, 1.3], [-66.0, 0, 1.2], [-58.0, 0, 1.2]], 1.6)
    hl = Life()
    hl['head'].change(T_STEP + 0.4, [-18, 0, 0], 0.5)          # looking up at the bricks
    hl['right_arm'].change(T_LINE + 0.2, [-148, 0, 0], 0.4)
    for t in TAPS:
        hl['right_arm'].change(t - 0.12, [-136, 0, 4], 0.1)
        hl['right_arm'].change(t + 0.05, [-150, 0, 0], 0.15)
        hl['head'].change(t + 0.1, [-20, 0, 6 if t == TAPS[1] else -4], 0.2)
    hl['right_arm'].change(T_BACK - 0.1, [0, 0, 0], 0.6)
    hl['head'].change(T_BACK, [-8, 0, 0], 0.6)
    hl['head'].change(T_RIPPLE + 1.0, [-16, 0, 0], 0.8)       # watching the bricks go
    hl['head'].change(T_RIPPLE + 3.5, [4, -40, 0], 0.5)       # a look down at the boy
    hl['head'].change(T_RIPPLE + 4.7, [0, 0, 0], 0.5)
    hc = Clips()
    sc.actor('hagrid', skin={'texture': 'hagrid'}, scale=GIANT, mover=hm, life=hl, clips=hc,
             items={'mainhand': UMBRELLA})
    sc.say(T_LINE, 'wand_hagrid_bricks', T_BACK + 0.4)

    # ---- the boy: watches, steps back, follows
    bm = Mover('boy', by, -90)
    bm.shift(T_BACK + 0.3, T_BACK + 1.3, [-72.4, 0, -0.6])
    bm.walk(T_WALK + 0.3, [[-70.2, 0, -0.2], [-66.0, 0, -0.1], [-58.0, 0, -0.1]], 1.6)
    bl = Life()
    bl['head'].change(0.4, [-14, 0, 0], 0.6)
    bl['head'].change(T_STEP + 0.6, [-24, 30, 0], 0.5)        # up at Hagrid's umbrella
    bl['head'].change(T_BACK + 0.4, [-12, 0, 0], 0.5)
    bl['head'].change(T_RIPPLE, [-22, 0, 0], 0.8)
    bl['head'].change(T_WALK, [0, 0, 0], 0.6)
    sc.actor('boy', skin={'viewer': True}, scale=KID, mover=bm, life=bl)

    # ---- the bricks
    brick_actors(sc, True, T_RIPPLE, T_QUIVER)

    # ---- sound, dust, light
    for t in TAPS:
        sc.sfx(t, 'brick_tap', 1.0)
    fb = centre(FIRST)
    sc.sound(T_QUIVER, 'minecraft:block.stone.hit', at=fb, volume=0.9, pitch=0.6)
    sc.sound(T_QUIVER + 0.5, 'minecraft:block.stone.hit', at=fb, volume=0.8, pitch=0.7)
    sc.particles(T_QUIVER + 0.2, 'minecraft:white_ash', [fb[0] - 0.6, fb[1], fb[2]], 12, [0.2, 0.3, 0.3])
    sc.sfx(T_RIPPLE - 0.1, 'wall_grind', 1.0)
    sc.sfx(T_RIPPLE + 2.3, 'wall_grind', 0.75)
    for e in A['wall_bricks']:
        if e['rank'] % 4 == 0:
            dyz = math.hypot(e['cell'][1] - FIRST[1], e['cell'][2] - FIRST[2])
            t = T_RIPPLE + 0.42 * dyz + 0.15
            c = centre(e['cell'])
            sc.particles(t, 'minecraft:white_ash', [c[0] - 0.4, c[1], c[2]], 10, [0.4, 0.4, 0.4])
            if e['rank'] % 8 == 0:
                sc.particles(t + 0.1, 'minecraft:poof', [c[0] - 0.6, c[1], c[2]], 3, [0.3, 0.3, 0.3])
    ao = A['arch_opening']
    sc.ev(T_RIPPLE + 0.6, 'effect', 'light_rays', at=[ao['face_yard_x'], 0, 0.5], yaw=90, spread=[2.5, 8, 12],
          duration=50, intensity=0.55)
    sc.sfx(T_RIPPLE + 1.0, 'street', 0.55)
    sc.ev(T_RIPPLE - 0.4, 'music', 'hogwartsmagic:pensieve.music.wand_wall', volume=1.0)   # the film's Diagon Alley theme as the bricks part

    # ---- camera
    fp = [by[0] + 0.35, BOY_EYE, by[2]]
    sc.ev(0, 'effect', 'eyes_open', at=[0, 0, 0], duration=tk(2.6))
    sc.key(0, fp, [-69.0, 3.2, 0.8], 72)
    sc.key(2.0, [fp[0] + 0.02, fp[1], fp[2]], [-69.6, 3.4, 2.2], 70)
    sc.key(T_BACK - 0.2, [fp[0] + 0.04, fp[1] - 0.02, fp[2]], [-69.2, 3.6, 1.6], 68)
    # W2: low wide from the yard's north side
    sc.key(T_BACK - 0.2, [-73.8, 1.7, -5.2], [-70.6, 2.4, 1.2], 64, cut=True)
    sc.key(T_QUIVER, [-73.4, 1.75, -5.0], [-70.4, 2.6, 1.0], 62)
    # W3: close on the wall, slow push
    sc.key(T_QUIVER, [-71.0, 2.2, -1.0], [-68.5, 3.5, 1.5], 56, cut=True)
    sc.key(T_RIPPLE + 1.7, [-70.7, 2.3, -0.7], [-68.5, 3.6, 1.0], 52)
    # W4: wide from behind them as the arch completes, then the push through it after them
    sc.key(T_RIPPLE + 1.7, [-78.0, 3.2, 0.55], [-68.0, 3.6, 0.5], 72, cut=True)
    sc.key(T_WALK + 0.4, [-76.4, 2.8, 0.55], [-60.0, 3.0, 0.5], 70)
    sc.key(LEN, [-67.6, 2.7, 0.55], [-30.0, 2.5, 0.5], 66, ease='in')

    # ---- a few shoppers beyond the arch
    occ = Occupancy(sc.L)
    for a in sc.actors:
        if a['type'] == 'player':
            occ.add(occ.samples(a['path']), 0.75 if a['id'] == 'hagrid' else 0.4)
    occ.add_camera(sc.camera)
    crowd = populate(sc, 'wall-crowd', occ, (0, 4), WALL_CROWD)

    scene = sc.scene(SET_DIAGON, DS.DIAGON_RADIUS, nxt=ALLEY_ID)

    def extra(problems):
        check_blocks(sc, scene, 'brick_', 0.5, ('hagrid', 'boy'), problems)

    n_chars, n_prox = finish(sc, scene, extra)
    return sc, n_chars, crowd


# ================================================================ wand_alley
def alley_scene():
    LEN = 56.0
    sc = Scene(ALLEY_ID, 'Diagon Alley', LEN, dict(DIAGON_W))
    daylight(sc)
    sc.ev(0, 'music', 'hogwartsmagic:pensieve.music.wand_alley', volume=1.0)   # the theme carries on from the wall
    sc.poses = {'standing': {}, 'glass': {'right_arm': [-118, 0, 12], 'left_arm': [-118, 0, -12]},
                'shoulders': SHOULDERS}
    # the arch's bricks rest in their surround: solid for the camera checks
    for e in A['wall_bricks']:
        sc.world[tuple(e['target_cell'])] = BRICK
    W = {w['name']: w for w in A['window_spots']}
    door = A['ollivanders_door']['outside']['pos']
    HZ, BZ = 1.0, -0.2
    T_JUMP, T_ARRIVE = 21.5, 45.0
    X_ARRIVE = door[0]
    SPEED = 1.6
    X_JUMP = X_ARRIVE - SPEED * (T_ARRIVE - T_JUMP - 0.05)

    # ---- Hagrid
    hm = Mover('hagrid', [-64.0, 0, HZ], -90)
    hm.walk(0.6, [[-45.0, 0, HZ]], 19.0 / 11.8)
    view = W['quidditch']['view']['pos']
    hm.face_point(13.4, [view[0], 0, view[2]], 0.6)
    hm.face(16.6, -90, 0.4)
    hm.walk(17.0, [[-45.0 + SPEED * (T_JUMP - 17.0), 0, HZ]], SPEED)
    hm.jump(T_JUMP, [X_JUMP, 0, HZ])
    hm.walk(T_JUMP + 0.05, [[X_ARRIVE, 0, HZ]], SPEED)
    hm.face(T_ARRIVE + 0.2, 180, 0.6)                          # to the boy, and the shop behind him
    hl = Life()
    hl.pulse('right_arm', 48.0, 49.8, [-92, 0, -6], 0.35)      # pointing at the shop over the boy's head
    hl['head'].change(1.0, [-6, 0, 0], 0.5)
    hl['head'].change(5.0, [0, 0, 0], 0.5)
    hc = Clips()
    sc.say(1.2, 'wand_hagrid_welcome', 5.0, hc)
    sc.say(45.4, 'wand_hagrid_wand', 50.3, hc)
    hc.span(53.4, 55.8, 'WAVE', 5, 6)
    sc.actor('hagrid', skin={'texture': 'hagrid'}, scale=GIANT, mover=hm, life=hl, clips=hc,
             items={'mainhand': UMBRELLA})

    # ---- the boy
    bm = Mover('boy', [-64.0, 0, BZ], -90)
    bm.walk(0.6, [[-45.0, 0, BZ]], 19.0 / 11.8)
    bm.walk(12.5, [[view[0], 0, view[2] + 0.3]], 2.5)          # an excited trot to the broom window
    bm.face(bm.t, 180, 0.3)
    t_back = 16.6
    bm.walk(t_back, [[-41.6, 0, BZ]], 2.6)
    bm.walk(bm.t + 0.1, [[-41.6 + SPEED * (T_JUMP - bm.t - 0.1), 0, BZ]], SPEED)
    bm.jump(T_JUMP, [X_JUMP, 0, BZ])
    bm.walk(T_JUMP + 0.05, [[X_ARRIVE, 0, BZ]], SPEED)
    bm.face(T_ARRIVE + 0.3, 0, 0.5)                            # up at Hagrid
    bm.face(49.9, 180, 0.5)                                    # to the shop
    t_door = bm.walk(50.6, [[door[0], 0, door[2] + 0.1]], 1.5)
    bl = Life()
    bl['head'].change(2.0, [-16, 20, 0], 0.6)     # gazing up at the overhangs
    bl['head'].change(5.5, [-10, -25, 0], 0.7)
    bl['head'].change(9.0, [0, 0, 0], 0.6)
    bl['head'].change(14.6, [-22, 0, 0], 0.4)                 # up at the broom
    bl['head'].change(16.4, [0, 0, 0], 0.4)
    bl['head'].change(36.0, [-18, 0, 0], 1.0)                 # Gringotts
    bl['head'].change(41.0, [0, 0, 0], 0.8)
    bl['head'].change(T_ARRIVE + 0.4, [-30, 0, 0], 0.5)       # up at Hagrid
    bl['head'].change(t_door + 0.3, [4, 62, 0], 0.4)          # a look back at Hagrid
    bl['head'].change(t_door + 1.2, [0, 0, 0], 0.4)
    bl.pulse('right_arm', 14.5, 16.4, [-112, 0, 10], 0.3)     # hand on the glass
    bl['right_arm'].change(54.3, [-72, 0, 0], 0.5)            # reaching for the door
    sc.actor('boy', skin={'viewer': True}, scale=KID, mover=bm, life=bl)
    assert t_door < 53.8, t_door

    # ---- the arch, props, animals, goblins
    brick_actors(sc, False)
    sc.actor('broom', 'hogwartsmagic:broom', path=[{'t': 0, 'pos': A['broom_stand']}], look=[{'t': 0, 'yaw': 90}])
    rnd = random.Random('owls')
    for i, c in enumerate(A['owl_cages']):
        look, t, y0 = [], 0.0, 180
        while t < LEN:
            look.append({'t': tk(t), 'yaw': round(y0 + rnd.uniform(-70, 70), 1), 'pitch': round(rnd.uniform(-15, 20), 1)})
            t += rnd.uniform(1.2, 3.5)
        sc.actor('owl_%d' % i, 'hogwartsmagic:owl', path=[{'t': 0, 'pos': c['pos']}], look=look,
                 scale=0.9 if c['pos'][1] != 2.0 else 0.8)
    for t, i in ((3.0, 0), (17.4, 1), (18.6, 4), (19.9, 2), (20.8, 6), (33.0, 3)):
        sc.sound(t, 'hogwartsmagic:owl.ambient', at=A['owl_cages'][i]['pos'], volume=0.9, pitch=rnd.uniform(0.9, 1.1))
    for i, g in enumerate(A['goblin_doors']):
        sc.actor('goblin_%d' % i, 'hogwartsmagic:goblin_guard', path=[{'t': 0, 'pos': g['pos']}],
                 look=[{'t': 0, 'yaw': g['yaw']}], clips=None)
        sc.actors[-1]['anims'] = [{'t': 0, 'name': 'IDLE'}]
    sc.sound(41.2, 'hogwartsmagic:goblin.ambient1', at=A['goblin_doors'][0]['pos'], volume=0.8)

    # ---- sound
    for t in (0.0, 18.6, 37.2):
        sc.sfx(t, 'street', 0.85)
    sc.sound(54.6, 'hogwartsmagic:ollivanders.door', at=[door[0], 1.0, door[2] - 1.0], volume=0.5)

    # ---- camera
    sc.key(0, [-62.0, 4.6, 3.6], [-40.0, 2.2, 0.5], 80)
    sc.key(5.0, [-59.6, 6.2, 2.6], [-30.0, 2.6, 0.5], 78)
    hx = lambda t: -64.0 + (19.0 / 11.8) * (t - 0.6)         # noqa: E731
    sc.key(5.0, [hx(5.0) + 4.2, 1.5, 0.4], 'actor:boy', 58, cut=True)
    sc.key(11.6, [hx(11.6) + 4.2, 1.5, 0.4], 'actor:boy', 56, ease='linear')
    sc.key(11.6, [-46.4, 1.35, -1.3], [-42.5, 1.5, -5.3], 58, cut=True)
    sc.key(17.0, [-46.0, 1.35, -1.6], [-42.5, 1.5, -5.4], 55)
    sc.key(17.0, [-38.6, 1.8, 3.0], [-32.5, 2.2, 5.5], 56, cut=True)
    sc.key(T_JUMP, [-38.0, 1.85, 3.1], [-32.5, 2.4, 5.5], 52)
    sc.key(T_JUMP, [-19.8, 1.75, -1.4], [-16.0, 1.8, -5.5], 54, cut=True)
    sc.key(25.5, [-19.4, 1.75, -1.6], [-16.0, 1.9, -5.5], 52)
    px = lambda t: X_JUMP + SPEED * (t - T_JUMP)               # noqa: E731
    sc.key(25.5, [px(25.5) - 3.4, 2.0, -1.6], 'actor:boy', 60, cut=True)
    sc.key(30.5, [px(30.5) - 3.4, 2.0, -1.6], 'actor:boy', 58, ease='linear')
    sc.key(30.5, [-12.8, 1.8, 2.4], [-8.5, 2.0, 6.5], 54, cut=True)
    sc.key(34.5, [-12.4, 1.8, 2.7], [-8.5, 2.1, 6.5], 52)
    sc.key(34.5, [17.0, 4.6, -3.0], [55.0, 10.5, 0.5], 70, cut=True)
    sc.key(40.0, [18.6, 4.9, -2.6], [55.0, 10.5, 0.5], 68)
    sc.key(40.0, [47.5, 8.6, 0.5], [54.5, 7.2, 0.5], 62, cut=True)
    sc.key(44.5, [48.3, 8.4, 0.5], [54.5, 7.4, 0.5], 58)
    sc.key(44.5, [35.2, 1.65, 2.6], [31.5, 1.5, 0.4], 50, cut=True)
    sc.key(50.4, [34.8, 1.65, 2.4], [31.5, 1.6, 0.3], 47)
    sc.key(50.4, [27.5, 1.2, 2.5], [31.5, 2.0, -5.5], 60, cut=True)
    sc.key(LEN, [28.6, 1.4, 0.9], [31.5, 1.6, -5.6], 50)

    # ---- the crowd
    occ = Occupancy(sc.L)
    for a in sc.actors:
        if a['type'] == 'player':
            occ.add(occ.samples(a['path']), 0.75 if a['id'] == 'hagrid' else 0.4)
    occ.add_camera(sc.camera)
    prof = dict(ALLEY_CROWD)
    # the children at the broom glass from the start (the fourth spot is the broom-tug family's), drifting off
    # once the boy has gone
    prof['glass_kids'] = [(c['pos'], c['yaw'], -1.0, lv, 'glass%d' % i) for i, (c, lv) in
                          enumerate(zip(W['quidditch']['crowd_spots'][:3], (18.2, 20.4, 23.0)))]
    crowd = populate(sc, 'alley-crowd', occ, (0, NCOL - 1), prof)

    scene = sc.scene(SET_DIAGON, DS.DIAGON_RADIUS, nxt=OLLI_ID)
    n_chars, n_prox = finish(sc, scene)
    return sc, n_chars, crowd


# ================================================================ wand_ollivanders
BOX_HALF = 0.225                  # a wand box: a memory_block at scale 0.45
SHARD_HALF = 0.08                 # a pot shard at scale 0.16
ROOM_X, ROOM_Z = (-4.8, 12.6), (-2.75, 3.75)
COUNTER = (0.8, 2.2, -3.2, 2.2)    # x0, x1, z0, z1 (with a margin)
STACK = (6.8, 9.2, -2.2, 3.2)


def in_rect(p, r):
    return r[0] <= p[0] <= r[1] and r[2] <= p[2] <= r[3]


def fly(rnd, p0, v0, half, rest_on, t0, avoid, landed, gravity=20.0):
    """A ballistic flight from p0 at t0, sampled every tick, until it comes to rest on the floor (or on what rest_on
    gives at x, z). None if it leaves the room, enters the counter or the stack from the side, comes near an actor
    in `avoid` [(x, z, radius, height)], or lands on another piece."""
    p, v = list(p0), list(v0)
    pts = [list(p)]
    for i in range(80):
        v[1] -= gravity * 0.05
        q = [p[k] + v[k] * 0.05 for k in range(3)]
        if not (ROOM_X[0] <= q[0] <= ROOM_X[1] and ROOM_Z[0] <= q[2] <= ROOM_Z[1]):
            return None
        if in_rect(q, STACK):
            return None
        top = rest_on(q)
        if top is None:
            return None
        if q[1] - half < top:
            if p[1] - half < top - 0.02:          # came in from the side, below the surface
                return None
            q[1] = top + half
            pts.append(q)
            break
        for ax, az, ar, ah in avoid:
            if math.hypot(q[0] - ax, q[2] - az) < ar + half and q[1] - half < ah:
                return None
        pts.append(q)
        p = q
    else:
        return None
    end = pts[-1]
    for lp in landed:
        if math.dist(end, lp) < 2 * half + 0.15:
            return None
    for ax, az, ar, ah in avoid:
        if math.hypot(end[0] - ax, end[2] - az) < ar + half + 0.1:
            return None
    return pts


def olli_floor(counter_open=False):
    def rest_on(q):
        x, z = q[0], q[2]
        if 1.0 <= x <= 2.0 and -3.0 <= z <= 1.0:
            return 1.5                                         # the counter top (bottom slabs on planks)
        if 1.0 <= x <= 2.0 and 1.0 < z <= 2.0:
            return 1.0 if counter_open else None              # the vase's end, once the vase is gone
        if in_rect(q, COUNTER):
            return None                                        # the margin round the counter: too close
        return 0.0
    return rest_on


def flying_block(sc, aid, state, scale, pts, t0, spin, yaw0):
    """A memory_block resting at pts[0] until t0, then flying through pts (one per tick), tumbling, and lying
    square where it lands."""
    path = [{'t': 0, 'pos': [r3(c) for c in pts[0]]}, {'t': tk(t0), 'pos': [r3(c) for c in pts[0]]}]
    look = [{'t': 0, 'yaw': yaw0, 'pitch': 0, 'roll': 0}, {'t': tk(t0), 'yaw': yaw0, 'pitch': 0, 'roll': 0}]
    n = len(pts) - 1
    pitch = roll = 0.0
    for i in range(2, n + 1, 2):
        pitch, roll = spin[0] * i, spin[1] * i
        tt = tk(t0) + i
        path.append({'t': tt, 'pos': [r3(c) for c in pts[i]], 'ease': 'linear'})
        look.append({'t': tt, 'yaw': yaw0, 'pitch': round(pitch, 1), 'roll': round(roll, 1), 'ease': 'linear'})
    tt = tk(t0) + n
    if path[-1]['t'] != tt:
        path.append({'t': tt, 'pos': [r3(c) for c in pts[n]], 'ease': 'linear'})
        look.append({'t': tt, 'yaw': yaw0, 'pitch': round(spin[0] * n, 1), 'roll': round(spin[1] * n, 1),
                     'ease': 'linear'})
    # lie square: the nearest quarter turns, a tick after landing
    look.append({'t': tt + 1, 'yaw': yaw0, 'pitch': 90 * round(look[-1]['pitch'] / 90.0),
                 'roll': 90 * round(look[-1]['roll'] / 90.0)})
    sc.actor(aid, 'hogwartsmagic:memory_block', block=state, scale=scale, path=path, look=look)


def olli_scene():
    LEN = 93.0
    sc = Scene(OLLI_ID, 'Ollivanders', LEN, OLLI_W)
    daylight(sc)
    sc.poses = {'standing': {}, 'ladder': {'right_arm': [-158, 0, 6], 'left_arm': [-146, 0, -6]}}
    door_in = A['door_inside']['pos']
    B = A['boy_counter']['pos']                 # [-0.6, 0, -0.5], facing +x
    O = A['counter']['pos']                     # [2.6, 0, -0.5], facing -x
    rail = A['ollivander_ladder_rail']
    LZ = -2.25                                  # Ollivander's feet plane on the ladder (anchor 'appear')
    LAD_Z = rail['face_z'] + 0.5                # the ladder blocks' centre plane (cells at the shelf face)
    X_FAR, X_STOP = rail['to'][0], rail['appear'][0]
    T_ROLL, T_STOP = 7.4, 9.8
    T_DOWN0, T_DOWN1, T_SWAP = 13.6, 15.6, 16.0
    STACK_A = ([6.2, 0, -0.5], -90)             # the freestanding stack behind the counter
    SHELF_N = ([2.9, 0, -2.0], 180)             # the north wall shelves just behind him
    TIP = [B[0] + 0.45, 1.3, B[2] + 0.25]       # the raised wand's tip (right hand on +z when facing +x)
    W1, W2, W3 = WRONG_WANDS
    T_GIVE1, T_WAVE1 = 34.6, 36.9
    T_SNATCH1 = 39.8
    T_GIVE2, T_WAVE2 = 44.8, 46.2
    T_SNATCH2 = 48.4
    T_GIVE3, T_WAVE3 = 52.4, 54.6
    T_SNATCH3 = 56.0
    T_GIVE4 = 61.4
    T_RAISE, T_CHOICE = 63.8, 64.4
    T_X = 88.4                                  # the last shot: first person, down at the wand
    T_CLOSE = 90.0

    # ---- the ladder (15 blocks at the shelf face) and Ollivander riding it
    for y in range(15):
        sc.actor('ladder_%02d' % y, 'hogwartsmagic:memory_block', block='minecraft:ladder[facing=south]',
                 path=[{'t': 0, 'pos': [X_FAR, y + 0.5, LAD_Z]}, {'t': tk(T_ROLL), 'pos': [X_FAR, y + 0.5, LAD_Z]},
                       {'t': tk(T_STOP), 'pos': [X_STOP, y + 0.5, LAD_Z], 'ease': 'out'}])
    olm = Mover('ollivander_ladder', [X_FAR, 4.0, LZ], 180)
    olm.shift(T_ROLL, T_STOP, [X_STOP, 4.0, LZ], 'out')
    olm.face(T_STOP + 0.3, 118, 0.6)                           # half round on the ladder, down at the boy
    olm.face(12.9, 180, 0.5)
    olm.shift(T_DOWN0, T_DOWN1, [X_STOP, 0.0, LZ], 'linear')
    olm.jump(T_SWAP, [X_STOP, -6.0, LZ])                       # parked under the floor on the cut
    oll = Life()
    oll['head'].change(T_STOP + 0.3, [32, 0, 0], 0.6)
    oll['head'].change(12.9, [0, 0, 0], 0.5)
    t = T_DOWN0
    i = 0
    while t < T_DOWN1 - 0.2:
        oll['right_arm'].change(t, [-14 if i % 2 else 12, 0, 0], 0.22)
        oll['left_arm'].change(t, [12 if i % 2 else -14, 0, 0], 0.22)
        t += 0.45
        i += 1
    oll['right_arm'].change(T_DOWN1, [0, 0, 0], 0.2)
    oll['left_arm'].change(T_DOWN1, [0, 0, 0], 0.2)
    sc.actor('ollivander_ladder', skin={'texture': 'ollivander'}, mover=olm, life=oll, alive=False,
             poses=[pose(0, 'ladder', 1), pose(T_DOWN1, 'standing', 4)])
    sc.say(10.4, 'wand_olli_wondered', 15.4)

    # ---- Ollivander walking (from the swap on)
    om = Mover('ollivander', [X_STOP, -6.0, LZ], 180)
    om.jump(T_SWAP, [X_STOP, 0.0, LZ])
    om.walk(T_SWAP + 0.2, [[O[0], 0, O[2]]], 1.3)
    om.face(om.t + 0.05, 90, 0.4)
    ol = Life()
    oc = Clips()
    acts = []
    sc.say(19.4, 'wand_olli_wandarm', 24.0, oc)

    def fetch(t0, where, wand, high=False):
        """Turn to the shelves, reach in, take the wand, come back to the counter. Returns the arrival time."""
        spot, yaw = where
        om.walk(t0, [spot], 1.6)
        om.face(om.t, yaw, 0.3)
        t1 = om.t + 0.3
        ol.pulse('right_arm', t1, t1 + 0.7, [-150 if high else -100, 0, 0], 0.25)
        sc.sound(t1 + 0.35, 'hogwartsmagic:ollivanders.box', at=[spot[0], 1.5, spot[2]], volume=0.7)
        acts.append(act(t1 + 0.4, 'hold', item=wand))
        return om.walk(t1 + 0.75, [[O[0], 0, O[2]]], 1.6)

    def give(t, wand_actions_boy, item):
        om.face(t - 0.6, 90, 0.3)
        ol.pulse('right_arm', t - 0.5, t + 0.3, [-68, 0, 0], 0.25)
        acts.append(act(t, 'equip', slot='mainhand', item='minecraft:air'))
        wand_actions_boy.append(act(t, 'hold', item=item))

    def snatch(t, boy_actions, item):
        ol.pulse('right_arm', t - 0.3, t + 0.35, [-80, 0, 0], 0.2)
        boy_actions.append(act(t, 'equip', slot='mainhand', item='minecraft:air'))
        acts.append(act(t, 'hold', item=item))

    ba = []                                     # the boy's actions
    t_back = fetch(25.0, STACK_A, W1)
    assert t_back < T_GIVE1 - 0.8, t_back
    give(T_GIVE1, ba, W1)
    sc.say(34.4, 'wand_olli_try', 36.85, oc)
    sc.say(38.7, 'wand_olli_no', 41.4, oc)
    snatch(T_SNATCH1, ba, W1)
    t_back = fetch(40.6, SHELF_N, W2)
    assert t_back < T_GIVE2 - 0.5, t_back
    give(T_GIVE2, ba, W2)
    sc.say(46.9, 'wand_olli_no2', 49.4, oc)
    snatch(T_SNATCH2, ba, W2)
    t_back = fetch(48.7, SHELF_N, W3)
    assert t_back < T_GIVE3 - 0.5, t_back
    give(T_GIVE3, ba, W3)
    sc.say(52.3, 'wand_olli_try', 54.55, oc)
    snatch(T_SNATCH3, ba, W3)
    sc.say(56.3, 'wand_olli_tricky', 59.6)
    ol['head'].change(56.4, [10, 0, 6], 0.6)                   # a long, considering look at the boy
    ol['head'].change(57.4, [0, 0, 0], 0.5)
    t_back = fetch(57.5, SHELF_N, '{viewer_wand}', high=True)
    assert t_back < T_GIVE4 - 0.8, t_back
    om.face(T_GIVE4 - 0.9, 90, 0.3)
    ol['right_arm'].change(T_GIVE4 - 0.8, [-66, 0, 0], 0.4)    # holding it out, waiting
    ol['right_arm'].change(T_GIVE4 + 0.3, [0, 0, 0], 0.5)
    acts.append(act(T_GIVE4, 'equip', slot='mainhand', item='minecraft:air'))
    ba.append(act(T_GIVE4, 'hold', item='{viewer_wand}'))
    oc.span(69.0, 71.0, 'CLAP', 4, 8)
    ol['body'].change(75.4, [9, 0, 0], 1.2)                   # leaning in over the counter
    ol['head'].change(75.4, [8, 0, 0], 1.2)
    t_great = 75.2 + sc.say(75.2, 'wand_olli_curious', 84.5, oc) + 0.5
    sc.say(t_great, 'wand_olli_great', T_X, oc)
    sc.actor('ollivander', skin={'texture': 'ollivander'}, mover=om, life=ol, clips=oc, actions=acts)

    # ---- the boy
    bm = Mover('boy', door_in, -90)
    bm.walk(0.5, [[B[0], 0, B[2]]], 1.4)
    bm.face(bm.t + 0.05, -90, 0.4)
    bl = Life()
    bl['head'].change(1.0, [-28, -20, 0], 1.0)                # up at the shelves
    bl['head'].change(3.0, [-34, 25, 0], 1.0)
    bl['head'].change(5.6, [-20, 0, 0], 0.8)
    bl['head'].change(7.6, [-26, 55, 0], 0.4)                 # the ladder rolling in
    bl['head'].change(T_STOP + 0.2, [-40, 30, 0], 0.6)
    bl['head'].change(14.0, [-10, 20, 0], 0.8)
    bl['head'].change(17.4, [-12, 0, 0], 0.6)
    bl['right_arm'].change(22.6, [-88, 0, 6], 0.5)            # holding out the wand arm for the tape
    bl['right_arm'].change(31.0, [0, 0, 0], 0.6)
    bl['head'].change(24.6, [16, -30, 0], 0.5)                # watching the tape
    bl['head'].change(28.0, [6, 40, 0], 0.6)
    bl['head'].change(31.0, [-12, 0, 0], 0.6)

    def wave(t, wild=False):
        bl['right_arm'].change(t - 0.6, [-60, 0, 0], 0.3)
        bl['right_arm'].change(t - 0.2, [-150, 0, -10], 0.18)
        bl['right_arm'].change(t + 0.05, [-30, 0, 18 if wild else 10], 0.12)
        bl['right_arm'].change(t + 0.5, [-55, 0, 0], 0.3)

    for tg in (T_GIVE1, T_GIVE2, T_GIVE3):
        bl['right_arm'].change(tg - 0.3, [-55, 0, 0], 0.3)     # reaching to take it
    wave(T_WAVE1, True)
    bl['right_arm'].change(T_SNATCH1 + 0.2, [0, 0, 0], 0.4)
    bl['head'].change(T_WAVE1 + 0.2, [-20, 50, 0], 0.3)       # the boxes flying
    bl['head'].change(T_WAVE1 + 2.6, [-10, 0, 0], 0.5)
    wave(T_WAVE2)
    bl['head'].change(T_WAVE2 + 0.25, [10, 60, 0], 0.2)       # the vase
    bl['head'].change(T_WAVE2 + 2.0, [-10, 0, 0], 0.5)
    bl['right_arm'].change(T_SNATCH2 + 0.2, [0, 0, 0], 0.4)
    wave(T_WAVE3)
    bl['body'].change(T_WAVE3 + 0.15, [-12, 0, 0], 0.2)      # a flinch from the fizzle
    bl['body'].change(T_WAVE3 + 1.2, [0, 0, 0], 0.5)
    bl['head'].change(T_WAVE3 + 0.15, [-6, -30, 0], 0.2)
    bl['head'].change(T_WAVE3 + 1.3, [-12, 0, 0], 0.4)
    bl['right_arm'].change(T_SNATCH3 + 0.2, [0, 0, 0], 0.4)
    bl['right_arm'].change(T_GIVE4 - 0.3, [-55, 0, 0], 0.3)
    bl['right_arm'].change(T_RAISE, [-122, 0, 4], 0.8)       # the right wand rises
    bl['left_arm'].change(T_CHOICE + 0.3, [-24, 0, -28], 1.2)  # lifted by the wind
    bl['head'].change(T_CHOICE, [-20, 0, 0], 0.8)
    bl['body'].change(T_CHOICE + 0.3, [-4, 0, 0], 1.0)
    bl['left_arm'].change(70.0, [0, 0, 0], 1.6)
    bl['body'].change(70.0, [0, 0, 0], 1.4)
    bl['right_arm'].change(72.4, [-48, 0, 0], 1.2)            # the wand lowered in front of him
    bl['head'].change(72.4, [8, 0, 0], 1.0)
    bl['head'].change(75.4, [-14, 0, 0], 1.0)                 # up at Ollivander
    bl['head'].change(T_X - 0.2, [26, 0, 0], 1.2)             # down at the wand
    sc.actor('boy', skin={'viewer': True}, scale=KID, mover=bm, life=bl, actions=ba)

    # ---- the tape: a rising spiral round the boy, along the arm, then dropped over the counter
    tc = A['tape_center']
    tape = []
    t, T0, T1 = 24.2, 24.2, 29.0
    while t <= T1 + 1e-9:
        f = (t - T0)
        ang = 2 * math.pi * f / 2.0
        y = 0.25 + 0.2 * f
        tape.append((t, [B[0] + 0.55 * math.cos(ang), y, B[2] + 0.55 * math.sin(ang)]))
        t += 0.15
    shoulder = [B[0] + 0.05, 1.0, B[2] + 0.28]
    hand = [B[0] + 0.62, 1.0, B[2] + 0.28]
    tape += [(29.4, [shoulder[0], 1.08, shoulder[2]]), (29.9, [hand[0] + 0.1, 1.08, hand[2]]),
             (30.4, [shoulder[0], 1.1, shoulder[2]]), (30.9, [hand[0] + 0.15, 1.12, hand[2]]),
             (31.6, [1.25, 1.62, -1.6])]
    tpath = [{'t': 0, 'pos': [1.5, 1.45, -2.5]}]             # coiled inside the counter top, unseen
    tpath.append({'t': tk(T0) - 6, 'pos': tpath[0]['pos']})
    for t, p in tape:
        tpath.append({'t': tk(t), 'pos': [r3(c) for c in p], 'ease': 'linear'})
    sc.actor('tape', 'hogwartsmagic:memory_tape', path=tpath)
    for t in (24.2, 26.6, 29.3):
        sc.sfx(t, 'tape', 0.9)
    sc.sound(29.6, 'hogwartsmagic:ollivanders.measure', at=tc, volume=0.6)

    # ---- the boxes bursting from the shelves (wand 1)
    rnd = random.Random('boxes')
    avoid = [(B[0], B[2], 0.32, 1.45), (O[0], O[2], 0.36, 1.9), (X_STOP, LZ - 0.2, 0.5, 15.0)]
    cells = [e for e in A['shelves_box_cells'] if -5.0 <= e['pos'][0] <= 7.6 and 1 <= e['cell'][1] <= 6]
    rnd.shuffle(cells)
    landed, n_box = [], 0
    T_BURST = T_WAVE1 + 0.15
    for e in cells:
        if n_box >= 28:
            break
        nrm = e['normal']
        for _ in range(30):
            p0 = [e['pos'][k] + nrm[k] * (BOX_HALF + 0.02) for k in range(3)]
            sp = rnd.uniform(2.0, 4.2)
            lat = rnd.uniform(-1.4, 1.4)
            v0 = [nrm[0] * sp + (lat if nrm[0] == 0 else 0), rnd.uniform(0.6, 2.6), nrm[2] * sp + (lat if nrm[2] == 0 else 0)]
            t0 = T_BURST + rnd.uniform(0.0, 0.7)
            pts = fly(rnd, p0, v0, BOX_HALF, olli_floor(), t0, avoid, landed)
            if pts:
                break
        else:
            continue
        landed.append(pts[-1])
        spin = (rnd.choice((-1, 1)) * rnd.uniform(9, 22), rnd.choice((-1, 1)) * rnd.uniform(5, 16))
        flying_block(sc, 'box_%02d' % n_box, rnd.choice(('minecraft:mud_bricks', 'minecraft:tuff_bricks')),
                     0.45, pts, t0, spin, rnd.choice((0, 90)))
        n_box += 1
    assert n_box >= 20, n_box
    sc.sfx(T_BURST, 'boxes_fall', 1.0)
    sc.sound(T_BURST + 0.1, 'hogwartsmagic:ollivanders.fail_paper', at=[2.0, 2.0, -1.0], volume=0.5)
    for k in range(5):
        sc.particles(T_BURST + 0.2 * k, 'minecraft:white_ash', [rnd.uniform(-4, 6), rnd.uniform(1.5, 5), -2.6], 14,
                     [1.2, 1.0, 0.3])

    # ---- the vase (wand 2)
    vs = A['vase_spot']
    T_VASE = T_WAVE2 + 0.15
    vc = [vs[0] + 0.5, vs[1] + 0.45, vs[2] + 0.5]
    sc.ev(T_VASE, 'block', 'minecraft:air', at=vs)
    sc.particles(T_VASE, 'minecraft:poof', vc, 14, [0.3, 0.3, 0.3])
    sc.particles(T_VASE + 0.05, 'minecraft:white_ash', vc, 24, [0.4, 0.3, 0.4])
    sc.sfx(T_VASE, 'vase', 1.0)
    sc.sound(T_VASE + 0.05, 'hogwartsmagic:ollivanders.fail_bang', at=vc, volume=0.5)
    shards = []
    n_sh = 0
    for i in range(40):
        if n_sh >= 8:
            break
        ang = rnd.uniform(0, 2 * math.pi)
        sp = rnd.uniform(1.0, 2.8)
        v0 = [math.cos(ang) * sp, rnd.uniform(1.2, 3.0), math.sin(ang) * sp]
        pts = fly(rnd, vc, v0, SHARD_HALF, olli_floor(True), T_VASE, avoid, shards)
        if not pts:
            continue
        shards.append(pts[-1])
        flying_block(sc, 'shard_%d' % n_sh, 'minecraft:terracotta', 0.16, pts, T_VASE,
                     (rnd.uniform(-30, 30), rnd.uniform(-30, 30)), rnd.choice((0, 45, 90)))
        n_sh += 1
    assert n_sh >= 5, n_sh

    # ---- the fizzle (wand 3)
    T_FIZ = T_WAVE3 + 0.1
    sc.sfx(T_FIZ, 'fizzle', 1.0)
    sc.particles(T_FIZ, 'minecraft:smoke', TIP, 26, [0.15, 0.15, 0.15])
    sc.particles(T_FIZ + 0.3, 'minecraft:large_smoke', TIP, 6, [0.1, 0.2, 0.1])
    sc.particles(T_FIZ + 0.6, 'minecraft:smoke', [TIP[0], TIP[1] - 0.2, TIP[2]], 10, [0.1, 0.1, 0.1])

    # ---- the one
    sc.ev(T_CHOICE - 1.0, 'music', 'hogwartsmagic:pensieve.music.hall_prologue', volume=1.0)   # the celesta theme as the wand chooses
    sc.ev(T_CHOICE, 'effect', 'wand_choice', at=[r3(c) for c in [B[0] + 0.4, 1.3, B[2] + 0.2]], duration=110,
          intensity=1.1)
    sc.sfx(T_CHOICE - 0.1, 'choice', 1.0)
    sc.sound(T_CHOICE + 0.4, 'hogwartsmagic:ollivanders.chosen', at=[B[0], 1.2, B[2]], volume=0.5)
    sc.ev(T_CHOICE, 'block', 'minecraft:light', at=[0, 2, 0])
    sc.ev(70.4, 'block', 'minecraft:air', at=[0, 2, 0])

    # ---- the shop: the bell, the door, dust in the window light
    sc.sfx(0.2, 'shop_bell', 1.0)
    sc.sound(1.0, 'hogwartsmagic:ollivanders.door', at=[door_in[0] - 0.5, 1.0, door_in[2]], volume=0.6)
    sc.sfx(T_ROLL, 'ladder', 1.0)
    sc.sound(3.0, 'hogwartsmagic:ollivanders.shop', volume=0.5)
    win = A['window']['center']
    for t in range(1, int(LEN) - 4, 7):
        sc.particles(float(t), 'minecraft:white_ash', [win[0] + 2.0, win[1], win[2]], 8, [1.4, 1.2, 1.4])

    # ---- camera
    BE = [B[0], BOY_EYE, B[2]]
    sc.key(0, [-5.6, 1.9, 2.0], [6.0, 6.0, -1.0], 70)
    sc.key(4.4, [-5.2, 1.8, 1.8], [6.0, 5.2, -1.0], 66)
    sc.key(4.4, [-1.5, 0.4, -2.0], [5.0, 15.0, -3.0], 75, cut=True)
    sc.key(8.6, [-1.4, 0.45, -1.9], [10.0, 8.0, -2.5], 72)
    sc.key(8.6, [-1.4, 1.1, 0.6], 'actor:ollivander_ladder', 52, cut=True)
    sc.key(13.4, [-1.3, 1.05, 0.5], 'actor:ollivander_ladder', 48)
    sc.key(13.4, [-4.8, 2.6, 2.8], [3.6, 2.4, -1.6], 62, cut=True)
    sc.key(T_SWAP, [-4.5, 2.55, 2.6], [3.6, 1.6, -1.4], 60)
    sc.key(T_SWAP, [-4.6, 1.7, -2.2], [3.2, 1.3, -0.6], 58, cut=True)
    sc.key(19.0, [-4.3, 1.7, -2.0], [2.6, 1.4, -0.5], 56)
    sc.key(19.0, [-1.6, 1.5, 0.2], 'actor:ollivander', 46, cut=True)
    sc.key(24.0, [-1.5, 1.5, 0.15], 'actor:ollivander', 44)
    sc.key(24.0, [0.8, 1.0, 0.6], [B[0], 0.6, B[2]], 50, cut=True)
    sc.key(29.0, [0.7, 1.0, 0.5], [B[0], 0.75, B[2]], 48)
    sc.key(29.0, [4.5, 1.9, 1.6], [B[0] + 0.3, 0.8, B[2]], 58, cut=True)
    sc.key(34.0, [4.3, 1.9, 1.5], [B[0] + 0.5, 0.9, B[2]], 56)
    sc.key(34.0, [0.6, 1.6, 2.9], [1.0, 1.0, -0.5], 55, cut=True)
    sc.key(T_WAVE1 - 0.05, [0.55, 1.6, 2.8], [0.9, 1.0, -0.5], 53)
    sc.key(T_WAVE1 - 0.05, [-5.4, 2.8, 3.2], [2.0, 3.0, -2.0], 74, cut=True)
    sc.key(40.0, [-5.3, 2.7, 3.1], [2.0, 2.6, -1.6], 72)
    sc.key(40.0, [-1.4, 1.6, 0.4], 'actor:ollivander', 45, cut=True)
    sc.key(42.6, [-1.3, 1.6, 0.4], 'actor:ollivander', 44)
    sc.key(42.6, [5.0, 2.2, 1.8], [B[0], 0.8, B[2]], 58, cut=True)
    sc.key(T_WAVE2 - 0.1, [4.8, 2.2, 1.7], [B[0], 0.8, B[2]], 56)
    sc.key(T_WAVE2 - 0.1, [-1.2, 1.7, 2.8], [1.5, 1.3, 1.5], 50, cut=True)
    sc.key(48.6, [-1.1, 1.65, 2.7], [1.5, 1.1, 1.0], 48)
    sc.key(48.6, [-5.0, 2.4, 2.6], [4.5, 2.0, -0.5], 60, cut=True)
    sc.key(51.0, [-4.8, 2.35, 2.5], [4.5, 1.8, -0.5], 58)
    sc.key(51.0, [0.5, 1.4, 1.9], [0.2, 1.0, -0.5], 50, cut=True)
    sc.key(55.9, [0.45, 1.4, 1.8], [0.2, 1.0, -0.5], 48)
    sc.key(55.9, [-2.2, 1.3, 1.6], 'actor:ollivander', 52, cut=True)
    sc.key(60.4, [-2.1, 1.3, 1.5], 'actor:ollivander', 50)
    sc.key(60.4, [0.4, 1.6, 1.2], [0.6, 1.0, -0.5], 44, cut=True)
    sc.key(63.6, [0.35, 1.58, 1.1], [0.4, 1.0, -0.5], 42)
    sc.key(63.6, [0.55, 0.55, 0.9], [B[0], 1.0, B[2]], 52, cut=True)
    sc.key(68.6, [0.4, 0.5, -1.9], [B[0], 1.05, B[2]], 50)
    sc.key(68.6, [-0.8, 1.8, 0.9], 'actor:ollivander', 45, cut=True)
    sc.key(72.0, [-0.75, 1.8, 0.85], 'actor:ollivander', 44)
    sc.key(72.0, [-0.6, 1.5, 3.2], [1.0, 1.1, -0.5], 55, cut=True)
    sc.key(75.0, [-0.55, 1.5, 3.0], [1.0, 1.1, -0.5], 53)
    sc.key(75.0, [-0.9, 1.75, 0.55], [O[0], 1.55, O[2]], 38, cut=True)
    sc.key(T_X, [-0.9, 1.75, 0.55], [O[0], 1.55, O[2]], 38)
    fp = [BE[0] + 0.25, BE[1], BE[2]]
    sc.key(T_X, fp, [B[0] + 0.7, 0.75, B[2] + 0.2], 70, cut=True)
    sc.key(LEN, [fp[0] - 0.02, fp[1] - 0.03, fp[2]], [B[0] + 0.65, 0.7, B[2] + 0.2], 66)
    sc.ev(T_CLOSE, 'effect', 'eyes_close', at=[0, 0, 0], duration=50)
    assert T_CLOSE + 2.5 < LEN

    scene = sc.scene(SET_OLLI, DS.OLLIVANDERS_RADIUS, end_action='wake')
    evs = scene['events']
    assert evs[-1]['id'] == 'eyes_close' and all(e['t'] < evs[-1]['t'] for e in evs[:-1]), 'eyes_close must be last'

    def extra(problems):
        check_blocks(sc, scene, 'box_', BOX_HALF, ('boy', 'ollivander'), problems, world_check=False)
        check_blocks(sc, scene, 'shard_', SHARD_HALF, ('boy', 'ollivander'), problems, world_check=False)
        for y in range(15):                                    # the ladder rolls through air
            for x in range(int(X_STOP), int(X_FAR) + 1):
                if not is_air(OLLI_W, [x + 0.5, y + 0.5, LAD_Z]):
                    problems.append('ladder cell %s is %s' % ((x, y), OLLI_W.get(cell([x + 0.5, y + 0.5, LAD_Z]))))

    n_chars, n_prox = finish(sc, scene, extra, hidden_y=-2.0)
    return sc, n_chars, n_box, n_sh


# ================================================================ main
def crowd_note(c):
    parts = ['%s %d' % (k, v) + (' (%d)' % c.groups[k] if c.groups[k] != v else '') for k, v in c.n.items() if v]
    return 'crowd %d [%s]; hats on %d of %d grown-ups, %d children' % (
        sum(c.n.values()), ', '.join(parts), c.hatted, c.adults, c.kid_hats)


def main():
    out = []
    w, wc, wcr = wall_scene()
    out.append((w, wc, '%s; %d bricks' % (crowd_note(wcr), len(A['wall_bricks']))))
    a, ac, acr = alley_scene()
    out.append((a, ac, crowd_note(acr)))
    o, oc, nb, ns = olli_scene()
    out.append((o, oc, '%d boxes, %d shards' % (nb, ns)))
    total = 0.0
    for sc, n_chars, note in out:
        total += sc.length
        print('wrote %s.json: %d chars, %.1f s, %d actors; %s' % (sc.sid, n_chars, sc.length, len(sc.actors), note))
    print('total %.1f s' % total)
    voiced = [l for sc, _, _ in out for l in sc.lines if 'pensieve.voice.' + l[0] in REGISTERED]
    print('lines: %d (%d voiced, the rest subtitles only until the voices are registered)'
          % (sum(len(sc.lines) for sc, _, _ in out), len(voiced)))
    for sc, _, _ in out:
        for line, t, secs, measured, slot_end in sc.lines:
            print('  %-18s %-22s t %5.1f  %4.2f s %-9s slack %.2f' % (sc.sid, line, t, secs,
                                                                      'measured' if measured else 'estimate',
                                                                      slot_end - t - secs))


if __name__ == '__main__':
    main()
