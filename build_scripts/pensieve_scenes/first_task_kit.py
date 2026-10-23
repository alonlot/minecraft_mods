"""The First Task memory's additions to the scene generator kit: what flying_scene's kit (Sc, Keys, Rig, the checks)
does not know, for a memory with a dragon, her fire, her chain, a bridge she breaks and a triwizard crowd.

It imports flying_scene read-only (a Flying fix round owns that file); Task 10 of the First Task plan moves the shared
kit into scene_kit.py, and these join it then:
  - FSc: flying_scene.Sc with this memory's sound prefix and lines (sfx takes the whole sound name, say() reads LINES
    and first_task_clip_lengths.json);
  - Dragon: a memory Horntail under construction (path, cues, actions, head look, and the look baked from her own
    path with the held heading, as Broom/Flier bake the brooms');
  - the chain (catenary links from her neck to the stake, the snap and the fall), fire geometry, the crowd with
    causes, and every check the plan's asserts 11-17 ask for (dragon clearance, fire, wingbeats, crash and smash,
    the crowd, the build budget, the egg), plus the generalised fliers, walkers, yaw-rate and backward-slide checks
    (flying_scene's assume a lawn at y 0).

No function here has a scene_engine or dragon_engine name (assert 1): every pose, eye, capsule, jet and seat comes
from those mirrors.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import flying_scene as F  # noqa: E402  (read-only: Sc, Keys, Rig, Channel, the checks and helpers)
import scene_engine as E  # noqa: E402
import dragon_engine as D  # noqa: E402
import first_task_props as P  # noqa: E402  (registers the Firebolt and the egg into scene_engine)
import json  # noqa: E402

SFX = 'hogwartsmagic:pensieve.sfx.'
VOICE = 'hogwartsmagic:pensieve.voice.'
MUSIC = 'hogwartsmagic:pensieve.music.'
CLIP = json.load(open(os.path.join(HERE, '..', 'sounds', 'first_task_clip_lengths.json'), encoding='utf-8'))
FLY_CLIP = F.CLIP
REGISTERED = F.REGISTERED

# The spoken lines: (speaker shown in the subtitle, subtitle text), first_task_voices.py's table. No line names the
# viewer: the champion is "our champion" or "them"; the name appears only as the {viewer} speaker.
LINES = {
    'first_task_comm_call_muffled': ('Commentator', 'And now, ladies and gentlemen, our final champion!'),
    'first_task_comm_horntail': ('Commentator', 'And here she is, the Hungarian Horntail! Nerves of steel, now!'),
    'first_task_comm_accio': ('Commentator', "A Summoning Charm! Oh, that's clever, very clever!"),
    'first_task_comm_chain': ('Commentator', "Her chain's gone! She's loose, she's after them!"),
    'first_task_comm_egg': ('Commentator', "They've done it! The golden egg! What a flight!"),
    'first_task_viewer_accio': ('{viewer}', 'Accio Firebolt!'),
}
for _l, (_who, _txt) in LINES.items():
    assert 'Harry' not in _txt and 'Potter' not in _txt and '{' not in _txt, _l

tk, r3, r4, add, sub, lerp, smooth, smoother, yaw_to, unwrap, norm = (F.tk, F.r3, F.r4, F.add, F.sub, F.lerp, F.smooth,
                                                                     F.smoother, F.yaw_to, F.unwrap, F.norm)


def clip_secs(name):
    return float(CLIP.get(name, FLY_CLIP.get(name)))


# ================================================================ a scene under construction
class FSc(F.Sc):
    """flying_scene.Sc for this memory: sounds by their whole name (first_task_* or the reused flying_* layers),
    lines from LINES, and the notes the First Task checks read."""

    def __init__(self, sid, title, length_s, world):
        F.Sc.__init__(self, sid, title, length_s, world, None)
        self.dragon_ok = []       # (t0, t1, part prefixes or None for all, reason): assert 11's named exceptions
        self.dragon_speed_ok = []  # (t0, t1, reason)
        self.broom_dragon_ok = []  # (t0, t1, gap, reason): the bite: the broom may come to `gap` of her capsules
        self.fire_hits = []       # (t0, t1, label, box): a breathe's ray must meet this box between t0 and t1
        self.near_misses = []     # (tick, kind, label): what a gasp answers
        self.passes = []          # (tick, stand, label): the Firebolt over a stand (a cheer answers it)
        self.cannons = []         # (tick, label, smoke): a cannon shot; smoke says it must have its puff at a muzzle
        self.smashes = []         # {'tick', 'label', 'cells', 'box', 'parts', 'sfx'}: the bridge and the slates
        self.wingbeats = []       # ticks of first_task_wingbeat cues (assert 13)
        self.grab = None          # {'tick', 'egg', 'rider', 'broom'}
        self.seated = {}          # actor id -> [(t0, t1)]: seated (the walker check skips them)
        self.chain = None         # {'links', 'snap', 'neck': fn(t), 'stake'}
        self.reactions = []       # (tick, style, stand, (cause kind, cause tick))
        self.crowd_boxes = {}     # stand -> (centre, half size)

    def sfx(self, t, name, volume=1.0, loop=False, duration=None, hit=None, label=None, at=None):
        assert 'pensieve.sfx.' + name in REGISTERED, name
        kw = {'volume': round(volume, 3)}
        if loop:
            kw['loop'] = True
            if duration:
                kw['duration'] = duration
        self.ev(t, 'sfx', SFX + name, **kw)
        if hit is not None:
            self.sfx_hits.append((name, t, hit, label))

    def say(self, t, line, slot_end=None, volume=1.0):
        who, text = LINES[line]
        secs = clip_secs(line)
        assert 'pensieve.voice.' + line in REGISTERED, line
        self.ev(t, 'voice', VOICE + line, volume=volume)
        self.words.append({'t': t, 'text': text, 'subtitle': True, 'speaker': who,
                           'duration': max(20, int(math.ceil(secs * 20)) + 6)})
        self.lines.append((line, t, secs, slot_end))
        return t + tk(secs)

    def music(self, t, cue, volume=1.0):
        assert 'pensieve.music.' + cue in REGISTERED, cue
        self.ev(t, 'music', MUSIC + cue, volume=volume)


def view(sc):
    return F.view(sc)


# ================================================================ the dragon
DRAGON_YAW_RATE = 6.0         # degrees a tick: the most a look key turns her from the key before
DRAGON_YAW_LIMIT = 10.0       # the drawn yaw rate assert
DRAGON_CRAB = 65.0            # drawn yaw within this of her path's heading at 0.25 blocks a tick or more


class Dragon:
    """A memory Horntail under construction: her path (scene space, the ground point under her body), cues,
    breathes, head look, and her look baked from her own path (bake): the held heading blended in by horizontal
    speed and turned at most DRAGON_YAW_RATE a tick (flying_scene.steady_yaws, BroomFlight.heldHeading's mirror),
    the climb pitch and the auto-bank sampled into the keys (so a scripted tumble can override them)."""

    def __init__(self, sc, did, pos, yaw, scale=1.0):
        self.sc, self.id = sc, did
        self.path = F.Keys().at(0, pos)
        self.anims, self.actions = [], []
        self.head = [{'t': 0, 'v': [0.0, 0.0, 0.0]}]
        self.yaw0 = yaw
        self.pins = []            # (t0, t1, yaw): face that yaw whatever the path does (turning on the spot)
        self.hover = []           # (t0, t1, yaw): turn to that yaw as she slows
        self.overrides = []       # (t0, t1, fn(t, auto) -> (yaw, pitch, roll)): a scripted tumble
        self.a = sc.actor({'id': did, 'type': D.HORNTAIL_TYPE})
        if scale != 1.0:
            self.a['scale'] = scale
        self.sync()

    def cue(self, t, name, blend=None):
        c = {'t': t, 'name': name}
        if blend:
            c['blend'] = blend
        self.anims.append(c)
        self.anims.sort(key=lambda c: c['t'])
        self.sync()
        return t

    def breathe(self, t, duration=40, target=None):
        a = {'t': t, 'do': 'breathe', 'duration': duration}
        if target is not None:
            a['target'] = target if isinstance(target, str) else r3(target)
        self.actions.append(a)
        self.actions.sort(key=lambda a: a['t'])
        self.sync()

    def look_head(self, t, pitch, yaw, ease='linear'):
        """A head look key [pitch, yaw, 0] at t (relative to her body; DragonFlight.headLook clamps it)."""
        k = {'t': t, 'v': [round(pitch, 2), round(yaw, 2), 0.0]}
        if ease and ease != 'in_out':
            k['ease'] = ease
        if abs(self.head[-1]['t'] - t) < 1e-9:
            self.head[-1] = k
        else:
            assert t > self.head[-1]['t'], (t, self.head[-1]['t'])
            self.head.append(k)
        self.sync()

    def hold_head(self, t):
        if t > self.head[-1]['t'] + 1e-9:
            self.look_head(t, self.head[-1]['v'][0], self.head[-1]['v'][1], None)

    def sync(self):
        a = self.a
        a['path'] = self.path.k
        if self.anims:
            a['anims'] = self.anims
        if self.actions:
            a['actions'] = self.actions
        if len(self.head) > 1:
            a['limbs'] = {D.HEAD_LOOK: {'rot': self.head}}
        return a

    def bake(self, step=4):
        """The look keys from her own path: yaw (steady heading, pins and hovers), pitch (DragonFlight's climb pitch)
        and roll (the auto-bank), every `step` ticks as one spline run; the overrides' windows replace them."""
        self.a.pop('look', None)
        L = self.sc.L
        keys = self.path.k
        ts = list(range(step, L + 1, step))
        if ts[-1] != L:
            ts.append(L)
        yaws = dict(F.steady_yaws(keys, ts, self.yaw0, self.hover, self.pins, rate=DRAGON_YAW_RATE, t_start=0))
        yaws[0] = self.yaw0
        look = []
        for t in [0] + ts:
            yw, pi, ro = yaws[t], D.dragon_climb(keys, t), D.dragon_bank(keys, t)
            for t0, t1, fn in self.overrides:
                if t0 <= t <= t1:
                    yw, pi, ro = fn(t, (yw, pi, ro))
            k = {'t': t, 'yaw': round(yw, 3), 'pitch': round(pi, 3), 'roll': round(ro, 3)}
            if t > 0 and t < L:
                k['ease'] = 'spline'
            look.append(k)
        self.a['look'] = look
        self.sync()
        return look

    def pos(self, t):
        return E.track_sample(self.path.k, t)


def dragon_eye(scene, aid, t):
    """Her entity eye (what a camera `look_at` her aims at: 2.7 x scale above the root, not tilted)."""
    a = E.actor_by_id(scene, aid)
    p = D.horntail_frame(scene, aid, t)[0]
    return [p[0], p[1] + 2.7 * a.get('scale', 1.0), p[2]]


def dragon_head(scene, aid, t):
    """The middle of her head (between the head capsule's ends, unswept), scene space."""
    for name, (a, b, r) in D.horntail_parts(scene, aid, t, swept=False):
        if name == 'head':
            return [(a[i] + b[i]) / 2 for i in range(3)]
    raise KeyError('head')


def neck_point(scene, aid, t):
    """The collar on her neck where the chain hooks: the middle of neck1's capsule, as drawn."""
    for name, (a, b, r) in D.horntail_parts(scene, aid, t, swept=False):
        if name == 'neck1':
            return [(a[i] + b[i]) / 2 for i in range(3)]
    raise KeyError('neck1')


def solve_head(scene, aid, t, target, keys_before, pitches=range(-40, 41, 2), yaws=range(-60, 61, 3)):
    """The head look (pitch, yaw) that points her head (horntail_snout) at the target at t: a grid search on the
    mirror, as dev_dragon_fire.solve_look; returns ((pitch, yaw), error degrees)."""
    h = E.actor_by_id(scene, aid)
    saved = h.get('limbs')
    best = (1e9, None)
    for pitch in pitches:
        for yaw in yaws:
            h['limbs'] = {D.HEAD_LOOK: {'rot': keys_before + [{'t': t, 'v': [pitch, yaw, 0]}]}}
            p, d = D.horntail_snout(scene, aid, t)
            v = norm(sub(target, p))
            e = math.degrees(math.acos(max(-1.0, min(1.0, sum(v[i] * d[i] for i in range(3))))))
            if e < best[0]:
                best = (e, (pitch, yaw))
    if saved is None:
        h.pop('limbs', None)
    else:
        h['limbs'] = saved
    return best[1], best[0]


def aim_error(scene, aid, t, target):
    p, d = D.horntail_snout(scene, aid, t)
    v = norm(sub(target, p))
    return math.degrees(math.acos(max(-1.0, min(1.0, sum(v[i] * d[i] for i in range(3))))))


# ================================================================ parts and solids
_PARTS = {}


def parts_at(scene, aid, t, swept=True):
    """horntail_parts cached per (actor object, tick): the entry keeps the actor it was made for, so a new or
    copied actor (a mutation, another run) never reads a stale one."""
    a = E.actor_by_id(scene, aid)
    key = (id(a), t, swept)
    got = _PARTS.get(key)
    if got is None or got[0] is not a:
        if len(_PARTS) > 400000:
            _PARTS.clear()
        got = (a, D.horntail_parts(scene, aid, t, swept))
        _PARTS[key] = got
    return got[1]


def part_group(name):
    n = name.split('~')[0]
    if n.startswith('tail'):
        return 'tail'
    if any(w in n for w in ('humerus', 'forearm', 'finger')):
        return 'wing'
    if any(w in n for w in ('thigh', 'shank', 'foot')):
        return 'leg'
    if n.startswith(('head', 'jaw')):
        return 'head'
    if n.startswith('neck'):
        return 'neck'
    return 'body'


def seg_box_distance(a, b, lo, hi, n=8):
    """Least distance from segment a-b to the box [lo, hi] (sampled along the segment, n + 1 points)."""
    best = 1e9
    for k in range(n + 1):
        f = k / float(n)
        p = [a[i] + (b[i] - a[i]) * f for i in range(3)]
        q = [max(lo[i] - p[i], 0.0, p[i] - hi[i]) for i in range(3)]
        d = math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2])
        if d < best:
            best = d
    return best


def solid_cells(world, passable):
    return frozenset(c for c, s in world.items() if E.bid_of(s) not in passable)


def cells_in_box(solid, lo, hi):
    out = []
    for x in range(int(math.floor(lo[0])), int(math.floor(hi[0])) + 1):
        for y in range(int(math.floor(lo[1])), int(math.floor(hi[1])) + 1):
            for z in range(int(math.floor(lo[2])), int(math.floor(hi[2])) + 1):
                if (x, y, z) in solid:
                    out.append((x, y, z))
    return out


def capsules_box(caps, margin):
    lo = [min(min(a[i], b[i]) - r for _, (a, b, r) in caps) - margin for i in range(3)]
    hi = [max(max(a[i], b[i]) + r for _, (a, b, r) in caps) + margin for i in range(3)]
    return lo, hi


def caps_to_cells(caps, cells, groups=None, reach=1.0):
    """(least gap, part, cell) from the capsules (optionally only those of the given groups) to the unit cells; only
    the cells within `reach` of a capsule's own box are measured (the gaps that matter are under it)."""
    best = (1e9, None, None)
    cs = cells if isinstance(cells, (set, frozenset)) else set(cells)
    for name, (a, b, r) in caps:
        if groups is not None and part_group(name) not in groups:
            continue
        lo = [min(a[i], b[i]) - r - reach for i in range(3)]
        hi = [max(a[i], b[i]) + r + reach for i in range(3)]
        for x in range(int(math.floor(lo[0])), int(math.floor(hi[0])) + 1):
            for y in range(int(math.floor(lo[1])), int(math.floor(hi[1])) + 1):
                for z in range(int(math.floor(lo[2])), int(math.floor(hi[2])) + 1):
                    c = (x, y, z)
                    if c not in cs:
                        continue
                    d = seg_box_distance(a, b, c, (x + 1, y + 1, z + 1)) - r
                    if d < best[0]:
                        best = (d, name, c)
    return best


# ================================================================ the chain
CHAIN_BLOCK = 'minecraft:chain[axis=y,waterlogged=false]'


def link_look(d):
    """(yaw, pitch) that turn a chain block's own y axis along unit d (MemoryBlockRenderer: Ry(-yaw) Rx(pitch))."""
    h = math.hypot(d[0], d[2])
    pitch = math.degrees(math.atan2(h, d[1]))
    yaw = yaw_to(d[0], d[2]) if h > 1e-6 else 0.0
    return yaw, pitch


def ground_top(solid, x, z, below):
    """The top of the highest solid cell in column (x, z) whose top is at or under `below`."""
    cx, cz = int(math.floor(x)), int(math.floor(z))
    y = int(math.floor(below)) - 1
    for k in range(40):
        if (cx, y - k, cz) in solid:
            return float(y - k + 1)
    return -1e9


# ================================================================ fire geometry
def fire_geometry(scene, aid, t, world):
    """The jet at t as dragon_engine.fire_clearance measures it: (origin, dir, length, scale, splash) with splash the
    drawn tongues (centre, half size) where its ray meets a block, or None while no breathe emits."""
    jet = D.fire_jet(scene, aid, t, world)
    if jet is None:
        return None
    o, d, length = jet
    scale = E.actor_by_id(scene, aid).get('scale', 1.0)
    hit, n = D.ray_hit(world, o, d, length + 0.1)
    splash = D.splash_centres(world, o, d, hit, n, scale) if (n is not None and hit <= length + 1e-6) else []
    return o, d, length, scale, splash


def fire_gap(geom, point):
    """How far point is outside the drawn flame of geom (fire_clearance's sum, from the mirror's pieces)."""
    o, d, length, scale, splash = geom
    v = [point[i] - o[i] for i in range(3)]
    s = max(0.0, min(length, sum(v[i] * d[i] for i in range(3))))
    q = [o[i] + d[i] * s for i in range(3)]
    best = math.dist(point, q) - D.fire_visible_radius(s) * scale
    for c, half in splash:
        best = min(best, math.dist(c, point) - half)
    return best


FIRE_AFTER = 25               # ticks past a breathe's end its last gas still flies and splashes (Task 7 review R4)


def fire_frames(scene, aid, world, step=0.25):
    """[(t, geom)] every `step` ticks from each breathe's start to FIRE_AFTER past its end; past the end the last
    emitting jet stands in for the gas still in flight (conservative)."""
    out = []
    actor = E.actor_by_id(scene, aid)
    for t0, dur, _ in D.breathes(actor):
        last = None
        n = int(round((dur + FIRE_AFTER) / step))
        for k in range(n + 1):
            t = t0 + k * step
            if t <= t0 + dur:
                g = fire_geometry(scene, aid, t, world)
                if g is not None:
                    last = g
            else:
                g = last
            if g is not None:
                out.append((t, g))
    return out


# ================================================================ the crowd
def crowd_event(stand, at):
    return {'t': 0, 'kind': 'effect', 'id': 'crowd', 'at': r3(at), 'rows': [[round(v, 3) for v in r] for r in stand['rows']],
            'style': 'triwizard'}


def stand_box(event):
    """(centre, half size) of a crowd event's seats, a little round them (crowd_react's spread)."""
    seats = [p for p, _ in E.crowd_seats(event)]
    lo = [min(p[i] for p in seats) - 0.8 for i in range(3)]
    hi = [max(p[i] for p in seats) + 0.8 for i in range(3)]
    lo[1] -= 1.0
    hi[1] += 2.6
    return [round((lo[i] + hi[i]) / 2, 2) for i in range(3)], [round((hi[i] - lo[i]) / 2, 2) for i in range(3)]


def react(sc, t, stand, style, cause, duration=None):
    at, spread = sc.crowd_boxes[stand]
    kw = {'at': at, 'spread': spread, 'style': style}
    if duration:
        kw['duration'] = duration
    sc.ev(t, 'effect', 'crowd_react', **kw)
    sc.reactions.append((t, style, stand, cause))


REACT_LIMITS = {'sit': 10, 'gasp': 20, 'cheer': 10, 'erupt': 6, 'goal': 10, 'chant': 200}


def check_reactions(sc, scene, problems):
    """Assert 15: every crowd_react belongs to a reaction with a cause, inside its window after it (sit 0.5 s after
    the roar, gasp 1 s after a near miss, cheer 0.5 s after the Firebolt passes over the stand, erupt 0.3 s after
    the grab); every near miss has its gasp and every pass over a stand its cheer there. Returns the worst lag."""
    n_events = sum(1 for e in scene['events'] if e.get('id') == 'crowd_react')
    if n_events != len(sc.reactions):
        problems.append('%s: %d crowd_react events, %d with a recorded cause' % (sc.sid, n_events, len(sc.reactions)))
    worst = 0
    for t, style, stand, (kind, tc) in sc.reactions:
        d = t - tc
        worst = max(worst, d)
        if d < 0 or d > REACT_LIMITS[style]:
            problems.append('%s: the %s %s at t %d is %d ticks after its %s (t %d)' % (sc.sid, stand, style, t, d, kind,
                                                                                    tc))
    for tm, kind, label in sc.near_misses:
        if not any(st == 'gasp' and 0 <= t - tm <= 20 for t, st, _, _ in sc.reactions):
            problems.append('%s: no gasp follows the near miss at t %d (%s)' % (sc.sid, tm, label))
    for tp, stand, label in sc.passes:
        if not any(st == 'cheer' and s_ == stand and 0 <= t - tp <= 10 for t, st, s_, _ in sc.reactions):
            problems.append('%s: no cheer in the %s stand within 0.5 s of %s (t %d)' % (sc.sid, stand, label, tp))
    return worst


# ================================================================ checks: the dragon (asserts 11, 13, 14)
def dragon_windows(sc, t):
    """(groups exempt everywhere, groups exempt only from the ground she stands on) at t, from sc.dragon_ok:
    entries (t0, t1, groups or None for all, reason[, 'floor']); a 'floor' entry exempts its groups only from the
    blocks whose tops are within FLOOR_REACH of her root's height (the rock under her feet), so a wing folded on the
    shelf is fine and the same wing in a boulder beside her is not."""
    out, floor = set(), set()
    for w in sc.dragon_ok:
        t0, t1, groups = w[0], w[1], w[2]
        if t0 <= t <= t1:
            if len(w) > 4 and w[4] == 'floor':
                floor |= set(groups)
            elif len(w) > 4 and w[4] == 'drawn':
                continue
            else:
                out |= set(groups) if groups else {'all'}
    return out, floor


def drawn_window(sc, t):
    """Whether a 'drawn' entry covers t: her wings are checked as drawn (unswept) there, the stroke being one the
    pose itself makes in a tick or two (a takeoff flicking into a dive), not a beat the sweep must hold."""
    return any(w[0] <= t <= w[1] for w in sc.dragon_ok if len(w) > 4 and w[4] == 'drawn')


FLOOR_REACH = 0.35
DEEP = 0.35                   # how far an exempt capsule may reach into a block (its box sits up to ~0.3 inside it)
DEEP_OF = {'leg': 0.65, 'tail': 0.5}    # her feet and claws stand on the rock: their capsules round a box 0.5 deep


def check_dragon_blocks(sc, scene, problems, aid, solid, margin=0.8):
    """Assert 11: every capsule (wings swept) at least `margin` from every block, every tick, except the named
    windows' part groups (her feet on the nest, the slate lashes, the crash); a block a `block` event turns to air
    is gone from its tick on. Returns (least gap, t, part)."""
    worst = (9.0, None, None)
    gone = {}
    for e in scene['events']:
        if e.get('kind') == 'block' and e.get('id') == 'minecraft:air':
            c = tuple(int(v) for v in e['at'])
            gone[c] = min(gone.get(c, 10 ** 9), e['t'])
    for t in range(0, scene['length'] + 1):
        ex, floor = dragon_windows(sc, t)
        if 'all' in ex:
            continue
        caps = parts_at(scene, aid, t, swept=not drawn_window(sc, t))
        lo, hi = capsules_box(caps, margin + 0.01)
        cells = cells_in_box(solid, lo, hi)
        if gone:
            cells = [c for c in cells if gone.get(tuple(c), 10 ** 9) > t]
        if not cells:
            continue
        keep = [c for c in caps if part_group(c[0]) not in ex]
        if floor:
            ry = D.horntail_frame(scene, aid, t)[0][1]
            under = [c for c in cells if c[1] + 1 <= ry + FLOOR_REACH]
            above = [c for c in cells if c[1] + 1 > ry + FLOOR_REACH]
            g1 = caps_to_cells([c for c in keep if part_group(c[0]) not in floor], under)
            g2 = caps_to_cells(keep, above)
            g, name, c = min(g1, g2, key=lambda x: x[0])
        else:
            g, name, c = caps_to_cells(keep, cells)
        # the exempt parts may touch, but never sink in: a capsule holds its box with up to ~0.3 to spare
        deep = caps_to_cells([cp for cp in caps if part_group(cp[0]) in ex or part_group(cp[0]) in floor], cells)
        if deep[0] < -DEEP_OF.get(part_group(deep[1] or ''), DEEP):
            problems.append('%s: %s %s at t %d sinks %.2f into the block at %s' % (sc.sid, aid, deep[1], t, -deep[0],
                                                                                 deep[2]))
            return worst
        if g < worst[0]:
            worst = (round(g, 3), t, name)
        if g < margin:
            problems.append('%s: %s %s at t %d is %.2f from the block at %s' % (sc.sid, aid, name, t, g, c))
            return worst
    return worst


def check_dragon_motion(sc, scene, problems, aid):
    """Assert 11, motion: her speed under 2 blocks a tick, its change at most 0.08 blocks/tick^2 (the launch and the
    crash named); her cue states match her path (no FLY or AIR_BREATH while she stands, no ground cue while she is
    up); her drawn yaw turns at most DRAGON_YAW_LIMIT a tick and never crabs (within DRAGON_CRAB of her heading
    wherever she moves 0.25 blocks a tick or faster). Returns (speed, acc, yaw rate)."""
    a = E.actor_by_id(scene, aid)
    keys = a['path']
    n = scene['length']
    pos = [E.track_sample(keys, t) for t in range(n + 1)]
    top_speed, worst_acc, worst_rate = 0.0, 0.0, 0.0
    for t in range(1, n + 1):
        top_speed = max(top_speed, math.dist(pos[t], pos[t - 1]))
    if top_speed >= 2.0:
        problems.append('%s: %s moves %.2f blocks in a tick' % (sc.sid, aid, top_speed))
    for t in range(2, n + 1):
        if any(t0 <= t <= t1 for t0, t1, _ in sc.dragon_speed_ok):
            continue
        acc = math.dist(sub(pos[t], pos[t - 1]), sub(pos[t - 1], pos[t - 2]))
        worst_acc = max(worst_acc, acc)
        if acc > 0.08 + 1e-9:
            problems.append('%s: %s speed changes %.3f blocks/tick^2 at t %d' % (sc.sid, aid, acc, t))
            break
    ground = sc.notes.get('dragon_ground', {}).get(aid)        # fn(t) -> the support under her root, or None
    for t in range(0, n + 1, 2):
        st = D.horntail_cue(a, t)
        if ground is None:
            break
        h = pos[t][1] - ground(t, pos[t])
        if h < 0.05 and st['flying'] and st['name'] not in ('TAKEOFF', 'LAND', 'STAGGER'):
            problems.append('%s: %s stands at t %d in %s (flying)' % (sc.sid, aid, t, st['name']))
            break
        if h > 1.5 and not st['flying'] and not any(w[0] <= t <= w[1] for w in sc.dragon_ok if w[2] is None):
            problems.append('%s: %s is %.1f up at t %d in the ground cue %s' % (sc.sid, aid, h, t, st['name']))
            break
    prev = None
    for q in range(0, n * 4 + 1):
        t = q / 4.0
        y = D.horntail_pose(a, t)['yaw']
        if prev is not None and not any(w[0] <= t <= w[1] for w in sc.dragon_ok if w[2] is None):
            r = abs(((y - prev) % 360 + 540) % 360 - 180) * 4
            worst_rate = max(worst_rate, r)
            if r > DRAGON_YAW_LIMIT:
                problems.append('%s: %s turns %.1f degrees a tick at t %.2f' % (sc.sid, aid, r, t))
                break
        prev = y
    for t in range(0, n):
        hs = math.hypot(pos[t + 1][0] - pos[t][0], pos[t + 1][2] - pos[t][2])
        if hs < F.CRAB_SPEED or any(w[0] <= t <= w[1] for w in sc.dragon_ok if w[2] is None):
            continue
        c = abs(((D.horntail_pose(a, t)['yaw'] - E.broom_heading(keys, t)) % 360 + 540) % 360 - 180)
        if c > DRAGON_CRAB:
            problems.append('%s: %s flies %.0f degrees off her own heading at t %d' % (sc.sid, aid, c, t))
            break
    return round(top_speed, 3), round(worst_acc, 4), round(worst_rate, 2)


def check_backslide(sc, scene, problems, ids, eps=0.005):
    """Task 7 review: no Horntail or Firebolt path slides backward: its horizontal velocity never opposes its
    segment's chord by more than eps blocks a tick (a spline overshoot out of a hold). Returns the worst."""
    worst = (0.0, None, None)
    for aid in ids:
        a = E.actor_by_id(scene, aid)
        keys = a['path']
        for i in range(1, len(keys)):
            k0, k1 = keys[i - 1], keys[i]
            if isinstance(a.get('ride'), dict):
                break
            ch = [k1['pos'][0] - k0['pos'][0], k1['pos'][2] - k0['pos'][2]]
            ln = math.hypot(*ch)
            if ln < 0.05 or k1['t'] - k0['t'] < 0.6:
                continue
            u = [ch[0] / ln, ch[1] / ln]
            n = max(2, int((k1['t'] - k0['t']) * 2))
            for j in range(n + 1):
                t = k0['t'] + (k1['t'] - k0['t']) * j / n
                p0, p1 = E.track_sample(keys, t - 0.25), E.track_sample(keys, t + 0.25)
                v = ((p1[0] - p0[0]) * 2 * u[0] + (p1[2] - p0[2]) * 2 * u[1])
                if v < worst[0]:
                    worst = (round(v, 4), aid, round(t, 2))
                if v < -eps:
                    problems.append('%s: %s slides backward %.3f blocks a tick at t %.2f (keys %s..%s)' % (
                        sc.sid, aid, -v, t, k0['t'], k1['t']))
                    break
    return worst


def check_wingbeats(sc, scene, problems, aid):
    """Assert 13: every first_task_wingbeat cue within a tick of a downstroke (dragon_engine.downstroke_ticks)."""
    a = E.actor_by_id(scene, aid)
    strokes = D.downstroke_ticks(a, 0, scene['length'])
    worst = 0
    cues = [e['t'] for e in scene['events'] if e.get('id') == SFX + 'first_task_wingbeat']
    for t in cues:
        d = min([abs(t - s) for s in strokes] or [99])
        worst = max(worst, d)
        if d > 1:
            problems.append('%s: the wingbeat at t %d is %d ticks off a downstroke' % (sc.sid, t, d))
    return len(cues), worst


def check_smashes(sc, scene, problems, aid):
    """Assert 14: for the bridge and each slate patch, her capsules (those of the named parts) first meet its box
    at the smash tick +-1; its block events (to air), its debris launches and its sfx all fall in that tick +-1, and
    nothing of it changes before. Returns [(label, first contact - tick)]."""
    out = []
    for sm in sc.smashes:
        T, (lo, hi) = sm['tick'], sm['box']
        first = None
        for t in range(max(0, T - 120), min(scene['length'], T + 20) + 1):
            caps = [c for c in parts_at(scene, aid, t) if sm['parts'] is None or part_group(c[0]) in sm['parts']]
            if any(seg_box_distance(a, b, lo, hi) - r <= 0.0 for _, (a, b, r) in caps):
                first = t
                break
        out.append((sm['label'], None if first is None else first - T))
        if first is None or abs(first - T) > 1:
            problems.append('%s: %s first meets its box at t %s, not at its tick %d' % (sc.sid, aid, first, T) +
                            ' (%s)' % sm['label'])
        cells = {tuple(c) for c in sm['cells']}
        evs = [e for e in scene['events'] if e.get('kind') == 'block' and tuple(e['at']) in cells]
        if len(evs) != len(cells) or any(e['id'] != 'minecraft:air' or abs(e['t'] - T) > 1 for e in evs):
            problems.append('%s: %s: %d of %d cells go to air at t %d +-1' % (
                sc.sid, sm['label'], sum(1 for e in evs if e['id'] == 'minecraft:air' and abs(e['t'] - T) <= 1),
                len(cells), T))
        cue = [e for e in scene['events'] if e.get('id') == SFX + sm['sfx'] and abs(e['t'] - T) <= 1]
        if not cue:
            problems.append('%s: no %s within a tick of %s' % (sc.sid, sm['sfx'], sm['label']))
        for did in sm.get('debris', []):
            d = E.actor_by_id(scene, did)
            moves = [k['t'] for k0, k in zip(d['path'], d['path'][1:]) if math.dist(k0['pos'], k['pos']) > 1e-6]
            if not moves or abs(moves[0] - T) > 1.5 and moves[0] > T + 1:
                problems.append('%s: debris %s of %s launches at %s, not at t %d' % (sc.sid, did, sm['label'],
                                                                                   moves[:1], T))
                break
            if d['path'][0]['t'] > 0 or any(math.dist(k['pos'], d['path'][0]['pos']) > 1e-6
                                            for k in d['path'] if k['t'] < T - 1):
                problems.append('%s: debris %s moves before %s' % (sc.sid, did, sm['label']))
                break
    return out


def check_chain(sc, scene, problems, aid):
    """The chain: its first link stays on her collar (neck_point) within 0.1 until the snap tick, and the
    first_task_chain_snap sfx lands within a tick of the snap. Returns the worst collar gap."""
    ch = sc.chain
    if not ch:
        return None
    worst = 0.0
    for t in range(0, min(ch['snap'], scene['length']) + 1, 2):
        end = ch['end_of'](scene, t)
        d = math.dist(end, neck_point(scene, aid, t))
        worst = max(worst, d)
        if d > 0.1:
            problems.append('%s: the chain is %.2f off her collar at t %d' % (sc.sid, d, t))
            break
    if ch['snap'] <= scene['length']:
        if not any(e.get('id') == SFX + 'first_task_chain_snap' and abs(e['t'] - ch['snap']) <= 1
                   for e in scene['events']):
            problems.append('%s: no chain_snap sfx within a tick of the snap (t %d)' % (sc.sid, ch['snap']))
        for lid in ch['links']:
            k = E.actor_by_id(scene, lid)['path']
            pre = [x for x in k if x['t'] < ch['snap'] - 1e-6]
            if not pre:
                continue
    return round(worst, 3)


# ================================================================ checks: fire (assert 12)
def check_fire(sc, scene, problems, aid, world, viewer='viewer'):
    """Assert 12: every breathe lies inside a fire window; the drawn flame (cylinder and splash, sampled to
    FIRE_AFTER ticks past each breathe) stays at least 1.5 outside every part box of the viewer and 1.0 from the
    camera; each named hit's ray meets its box; a first_task_fire_roar cue starts within a tick of each breathe.
    Returns (least gap to the viewer, least to the camera, hits)."""
    a = E.actor_by_id(scene, aid)
    for t0, dur, _ in D.breathes(a):
        w = D.fire_window(a, t0)
        if w is None or t0 < w[0] - 1e-6 or t0 + dur > w[1] + 1e-6:
            problems.append('%s: breathe at %s (%s ticks) outside its fire window %s' % (sc.sid, t0, dur, w))
        if not any(e.get('id') == SFX + 'first_task_fire_roar' and abs(e['t'] - t0) <= 1 for e in scene['events']):
            problems.append('%s: no fire roar within a tick of the breathe at %s' % (sc.sid, t0))
    v = E.actor_by_id(scene, viewer)
    worst_v, worst_c = (9.0, None), (9.0, None)
    for t, g in fire_frames(scene, aid, world):
        o = g[0]
        root = E.rider_root(scene, v, t)[0]
        if math.dist(root, o) < 26.0:
            for part, rt, yaw, s, piv, rot in E.posed_parts(scene, v, t):
                for q in E.part_corners(rt, yaw, s, piv, rot, E.PART_BOX[part]):
                    d = fire_gap(g, q)
                    if d < worst_v[0]:
                        worst_v = (round(d, 3), t)
        c = E.engine_cam(scene['camera'], t)
        d = fire_gap(g, c)
        if d < worst_c[0]:
            worst_c = (round(d, 3), t)
    if worst_v[0] < 1.5:
        problems.append('%s: the fire comes %.2f from the viewer at t %s' % (sc.sid, worst_v[0], worst_v[1]))
    if worst_c[0] < 1.0:
        problems.append('%s: the fire comes %.2f from the camera at t %s' % (sc.sid, worst_c[0], worst_c[1]))
    hits = []
    for t0, t1, label, (lo, hi) in sc.fire_hits:
        best = None
        for t in range(int(t0), int(t1) + 1):
            jet = D.fire_jet(scene, aid, t, world)
            if jet is None:
                continue
            o, d, length = jet
            end = [o[i] + d[i] * (length + 0.06) for i in range(3)]
            if all(lo[i] - 0.05 <= end[i] <= hi[i] + 0.05 for i in range(3)):
                best = t
                break
        hits.append((label, best))
        if best is None:
            problems.append('%s: no jet meets %s between t %s and %s' % (sc.sid, label, t0, t1))
    return worst_v, worst_c, hits


def check_dragon_camera(sc, scene, problems, aid, margin=0.3):
    """Assert 4, the dragon: the camera four times a tick at least `margin` from every capsule (wings swept) that has
    a point in the frustum (aspect 2.0). Returns (least gap, t, part)."""
    worst = (9.0, None, None)
    cam = scene['camera']
    for t, c, shot in E.camera_samples(cam, scene['length']):
        p = D.horntail_frame(scene, aid, t)[0]
        if math.dist(p, c) > 14.0:
            continue
        caps = D.horntail_parts(scene, aid, t)
        lk = None
        for name, cap in caps:
            d = D.capsule_distance(cap, c)
            if d >= worst[0] and d >= margin:
                continue
            lk = lk or E.engine_look(scene, cam, t)
            a, b, r = cap
            pts = [lerp(a, b, f) for f in (0.0, 0.25, 0.5, 0.75, 1.0)]
            if not any(E.in_view(c, lk[0], lk[1], q) for q in pts):
                continue
            if d < margin:
                problems.append('%s: the camera at t %.2f is %.3f from %s %s' % (sc.sid, t, d, aid, name))
                return worst
            worst = (round(d, 3), t, name)
    return worst


# ================================================================ checks: fliers, walkers, eggs, the budget
def check_fliers(sc, scene, problems, solid_world, dragons=()):
    """Assert 8 for this memory: every broom path 0.5 from every block except its named windows (in the hand, the
    mount, the nest dive), 1.5 from every Horntail capsule (the bite at its own gap, named), its speed change at
    most 0.12 blocks/tick^2 (named exceptions). Returns (least block gap, least dragon gap, worst acc)."""
    w = solid_world
    brooms = [a for a in scene['actors'] if E.is_broom(a)]
    worst_b, worst_d, worst_acc = 9.0, (9.0, None), 0.0
    n = scene['length']
    for b in brooms:
        ok = sc.flier_ok.get(b['id'], [])
        pos = [E.track_sample(b['path'], t) for t in range(n + 1)]
        for t, p in enumerate(pos):
            if F.in_windows(t, ok) or p[1] < -40:
                continue
            lo, hi = E.broom_box(b['type'], b.get('scale', 1.0))
            bp = E.broom_pose(scene, b, t)
            corners = [add(bp['pos'], E._mat_vec(bp['m'], [x, y, z])) for x in (lo[0], hi[0]) for y in (lo[1], hi[1])
                       for z in (lo[2], hi[2])]
            mid = [add(bp['pos'], E._mat_vec(bp['m'], [0.0, y, z])) for y in (lo[1], hi[1]) for z in (lo[2], 0.0, hi[2])]
            for q in corners + mid:
                if not E.in_air(w, q, 0.5):
                    problems.append('%s: broom %s at t %d (%s) is within 0.5 of a block' % (sc.sid, b['id'], t, r3(q)))
                    break
            else:
                continue
            break
        exc = sc.speed_ok.get(b['id'], [])
        for t in range(2, n + 1):
            if any(t0 <= t <= t1 for t0, t1, _ in exc):
                continue
            acc = math.dist(sub(pos[t], pos[t - 1]), sub(pos[t - 1], pos[t - 2]))
            worst_acc = max(worst_acc, acc)
            if acc > 0.12 + 1e-9:
                problems.append('%s: broom %s speed changes %.3f blocks/tick^2 at t %d' % (sc.sid, b['id'], acc, t))
                break
        for aid in dragons:
            for t in range(0, n + 1):
                if pos[t][1] < -40:
                    continue
                d0 = math.dist(pos[t], D.horntail_frame(scene, aid, t)[0])
                if d0 > 14.0:
                    continue
                gap = next((g for t0, t1, g, _ in sc.broom_dragon_ok if t0 <= t <= t1), 1.5)
                lo, hi = E.broom_box(b['type'], b.get('scale', 1.0))
                bp = E.broom_pose(scene, b, t)
                pts = [add(bp['pos'], E._mat_vec(bp['m'], [x, y, z])) for x in (lo[0], 0.0, hi[0]) for y in (lo[1], hi[1])
                       for z in (lo[2], 0.0, hi[2])]
                rider = next((a for a in scene['actors'] if a.get('type') == 'player' and
                              E.broom_rider_at(scene, a, t) is b), None)
                if rider is not None:
                    pts.append(E.rider_eye(scene, rider, t))
                d = min(D.capsule_distance(cap, q) for _, cap in parts_at(scene, aid, t) for q in pts)
                if d < worst_d[0]:
                    worst_d = (round(d, 3), t)
                if d < gap:
                    problems.append('%s: broom %s at t %d is %.2f from %s (limit %.1f)' % (sc.sid, b['id'], t, d, aid,
                                                                                      gap))
                    break
    return worst_d, round(worst_acc, 4)


def check_dive_over(sc, scene, problems, solid):
    """Assert 8, the grab: through the nest dive the lowest broom point at least 0.6 above the blocks under every
    corner of its box. Returns the least height."""
    best = 9.0
    for bid, t0, t1 in sc.notes.get('nest_dive', []):
        b = E.actor_by_id(scene, bid)
        lo, hi = E.broom_box(b['type'], b.get('scale', 1.0))
        for t in range(t0, t1 + 1):
            bp = E.broom_pose(scene, b, t)
            for x in (lo[0], 0.0, hi[0]):
                for z in [lo[2] + (hi[2] - lo[2]) * k / 6.0 for k in range(7)]:
                    q = add(bp['pos'], E._mat_vec(bp['m'], [x, lo[1], z]))
                    top = ground_top(solid, q[0], q[2], q[1] + 0.5)
                    h = q[1] - top
                    best = min(best, h)
                    if h < 0.6:
                        problems.append('%s: in the nest dive at t %d broom %s is %.2f over the nest' % (sc.sid, t, bid, h))
                        return best
    return round(best, 3)


def check_walkers(sc, scene, problems, world, step=2):
    """People on their feet (flying_scene.check_walkers without its lawn floor): no body point inside a block, the
    seated, the riders and the unseen left out. Returns the number of points checked."""
    n = 0
    for t in range(0, scene['length'] + 1, step):
        for a in scene['actors']:
            if a.get('type') != 'player':
                continue
            if E.ride_of(a) is not None and E.rides_at(a, t):
                continue
            if F.in_windows(t, sc.seated.get(a['id'], [])) or a['id'] in sc.unseen_at(t):
                continue
            p = E.own_sample(a, 'path', t) if isinstance(a.get('ride'), dict) else E.sample_path(a['path'], t)
            s = a.get('scale', 1.0)
            for dy in (0.08, 0.9 * s, 1.7 * s):                 # 0.08: over a carpet
                for dx, dz in ((0, 0), (0.17 * s, 0.17 * s), (-0.17 * s, -0.17 * s), (0.17 * s, -0.17 * s),
                               (-0.17 * s, 0.17 * s)):
                    q = [p[0] + dx, p[1] + dy, p[2] + dz]
                    n += 1
                    hit = E.shape_hit(world, q)
                    if hit and E.bid_of(hit) not in F.PLANTS:
                        problems.append('%s t %d: %s body point %s in %s' % (sc.sid, t, a['id'], r3(q), E.bid_of(hit)))
                        return n
    return n


def ray_clear(world, a, b, step=0.1, skip_end=0.2):
    """Whether the straight line from a to b meets no block shape (its last skip_end left out)."""
    d = math.dist(a, b)
    n = int((d - skip_end) / step)
    for k in range(1, n + 1):
        p = lerp(a, b, k * step / d)
        if E.shape_hit(world, p) is not None:
            return False
    return True


def check_seen(sc, scene, problems, world, aid_point, t0, t1, need, label, max_dist=60.0):
    """The point is in the 16:9 frame, unoccluded (a ray from the camera to it meets no block), for at least `need`
    ticks running between t0 and t1. Returns the best run."""
    best, run = 0, 0
    for t in range(t0, t1 + 1):
        c = E.engine_cam(scene['camera'], t)
        lk, fov = E.engine_look(scene, scene['camera'], t)
        p = aid_point(scene, t)
        if (E.in_view(c, lk, fov, p, aspect=16 / 9.0) and math.dist(c, p) < max_dist and ray_clear(world, c, p)):
            run += 1
            best = max(best, run)
        else:
            run = 0
    if best < need:
        problems.append('%s: %s is in frame and unoccluded only %d ticks running (needs %d)' % (sc.sid, label, best,
                                                                                               need))
    return best


def check_egg(sc, scene, problems):
    """Assert 17: the golden egg actor is parked on the tick the hand closes (a key at T - 0.25 where it rests, the
    park at T), `hold golden_egg_prop` lands on that tick and its swap is compensated (a rider's needs none), and
    the hand is within 0.15 of the egg centre at every corner. Returns the worst hand gap."""
    g = sc.grab
    if not g:
        return None
    T = g['tick']
    egg = E.actor_by_id(scene, g['egg'])
    ks = [k['t'] for k in egg['path']]
    if not (any(abs(x - (T - 0.25)) < 1e-6 for x in ks) and any(abs(x - T) < 1e-6 for x in ks)):
        problems.append('%s: the egg is not parked on the grab tick %d' % (sc.sid, T))
    rest = E.track_sample(egg['path'], T - 0.25)
    park = E.track_sample(egg['path'], T)
    if park[1] > -40:
        problems.append('%s: the egg is still in the world after the grab (%s)' % (sc.sid, r3(park)))
    rider = E.actor_by_id(scene, g['rider'])
    holds = [x for x in rider.get('actions', []) if x['do'] == 'hold' and x.get('item') == 'hogwartsmagic:golden_egg_prop']
    if not holds or abs(holds[0]['t'] - T) > 1e-6:
        problems.append('%s: the egg prop is not held from the grab tick %d' % (sc.sid, T))
    gap = max(math.dist(h, rest) for h in F.hand_corners(scene, rider, g['side'], T))
    if gap > F.CONTACT_GAP:
        problems.append('%s: the hand is %.3f from the egg at the grab' % (sc.sid, gap))
    return round(gap, 3)


def check_egg_camera(sc, scene, problems):
    """Assert 4, the egg's box: the camera at least 0.15 from it (four times a tick) while it is in the frustum."""
    worst = 9.0
    for a in scene['actors']:
        if a['type'] != P.EGG:
            continue
        lo, hi = P.egg_box(a.get('scale', 1.0))
        for t, c, _ in E.camera_samples(scene['camera'], scene['length']):
            p = E.track_sample(a['path'], t)
            q = [max(p[i] + lo[i] - c[i], 0.0, c[i] - p[i] - hi[i]) for i in range(3)]
            d = math.sqrt(sum(v * v for v in q))
            if d < worst:
                lk, fov = E.engine_look(scene, scene['camera'], t)
                if E.in_view(c, lk, fov, p):
                    worst = d
                    if d < E.CAM_CLEAR:
                        problems.append('%s: the camera at t %.2f is %.3f from the egg' % (sc.sid, t, d))
                        return worst
    return round(worst, 3)


def check_lines(sc, scene, problems):
    """flying_scene.check_lines for this memory's LINES: every voice line has its subtitle with the measured clip
    length, no two overlap, every line ends before the scene does, and no text names the player."""
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
            problems.append('%s: word text %r names the player or holds a placeholder other than {viewer}' % (sc.sid, txt))


def check_blend_depth(sc, scene, problems, aid):
    """Task 6's carry: a cue landing inside fades nested MAX_NESTED_BLENDS deep loses the oldest; warn (and fail)
    when any cue would nest deeper. Returns the deepest chain seen."""
    a = E.actor_by_id(scene, aid)
    deepest = 0
    for c in a.get('anims', []):
        for dt in (0.0, 0.5, 1.0):
            b = D.horntail_blend_tree(a, c['t'] + dt)
            n = len(D.blend_chain(b))
            deepest = max(deepest, n)
    if deepest > D.MAX_NESTED_BLENDS:
        problems.append('%s: a %s cue nests %d fades deep (the engine keeps %d)' % (sc.sid, aid, deepest,
                                                                                  D.MAX_NESTED_BLENDS))
    return deepest


def check_rolls(sc, scene, problems, aid):
    """Task 6's carry: DragonFlight.neckRoll clamps the roll unwrapped, so keep every roll key within +-180 (then no
    wrap is needed). Returns the largest."""
    a = E.actor_by_id(scene, aid)
    big = max([abs(k.get('roll', 0.0)) for k in a.get('look', [])] or [0.0])
    if big > 180.0:
        problems.append('%s: %s rolls %.0f degrees (keep within 180: neckRoll clamps it unwrapped)' % (sc.sid, aid, big))
    return round(big, 1)


def whoosh(sc, bid, t0, t1, volume=0.9):
    """flying_scene.whoosh with this memory's sfx naming: a flying_broom_whoosh where broom bid passes the camera
    closest between t0 and t1 (check_sfx_hits finds the pass again from the emitted JSON)."""
    V = view(sc)
    b = E.actor_by_id(V, bid)
    best = min(range(t0, t1 + 1), key=lambda t: math.dist(E.engine_cam(sc.camera, t), E.track_sample(b['path'], t)))
    sc.sfx(best, 'flying_broom_whoosh', volume, hit=best, label='%s passes the camera' % bid)
    sc.notes.setdefault('passes', []).append((bid, t0, t1, best))
    return best


# ================================================================ the camera: a shot's natural ends
def _cam_amax(cam, lo, hi):
    """The largest frame-to-frame velocity change (blocks a frame at 60 fps) of the camera between ticks lo and hi."""
    n = int((hi - lo) * 3)
    fr = [E.engine_cam(cam, hi - j / 3.0) for j in range(n + 1)]
    best = 0.0
    for j in range(1, len(fr) - 1):
        best = max(best, math.sqrt(sum((fr[j + 1][c] - 2 * fr[j][c] + fr[j - 1][c]) ** 2 for c in range(3))))
    return best


def tame_shot_ends(sc, limit=0.0016):
    """A spline shot's first and last segments end naturally (CameraTrack: no curvature at a cut), so a camera
    riding a turning subject swings 1.5x harder there than through the shot. Each shot whose end segments break
    `limit` gets its two end keys nudged (a few centimetres, a coordinate search on the frame-to-frame velocity
    change over its four end segments) until they hold it. Returns [(shot start, before, after)]."""
    cam = sc.camera
    cuts = [i for i, k in enumerate(cam) if k.get('cut')] + [len(cam)]
    out = []
    for s, e in zip(cuts, cuts[1:]):
        i0, i1 = s, e - 1
        if i1 - i0 < 6:
            continue
        for keys, lo, hi in (((i0, i0 + 1), cam[i0]['t'] + 0.34, cam[i0 + 4]['t']),
                             ((i1, i1 - 1), cam[i1 - 4]['t'], cam[i1]['t'] - 0.001)):
            base = _cam_amax(cam, lo, hi)
            before = base
            if base <= limit:
                continue
            step = 0.04
            while step > 0.0005:
                better = False
                for i in keys:
                    for c in range(3):
                        for sgn in (1.0, -1.0):
                            old = cam[i]['pos'][c]
                            cam[i]['pos'][c] = round(old + sgn * step, 4)
                            m = _cam_amax(cam, lo, hi)
                            if m < base - 1e-7:
                                base, better = m, True
                            else:
                                cam[i]['pos'][c] = old
                if not better:
                    step /= 2.0
            out.append((cam[i0]['t'], round(before, 5), round(base, 5)))
    return out


def check_cannons(sc, scene, problems, spots):
    """Assert 15, the cannons: each shot's first_task_cannon cue on its tick and, for a shot the stands see, a smoke
    burst (particles) at every cannon_spots muzzle on that same tick. Returns the number of shots."""
    for t, label, smoke in sc.cannons:
        if not any(e['id'] == SFX + 'first_task_cannon' and abs(e['t'] - t) < 1e-6 for e in scene['events']):
            problems.append('%s: %s has no first_task_cannon cue at t %d' % (sc.sid, label, t))
        if smoke:
            for q in spots:
                if not any(e['kind'] == 'particles' and abs(e['t'] - t) < 1e-6 and math.dist(e['at'], q) < 0.01
                           for e in scene['events']):
                    problems.append('%s: %s has no smoke at the muzzle %s on t %d' % (sc.sid, label, r3(q), t))
    return len(sc.cannons)


def soften_shots(sc, limit=0.0017, iters=60):
    """A camera riding a subject round its corners can swing harder than a camera should (frame-to-frame velocity
    change over `limit`): such a shot's inner keys are low-passed ([1, 2, 1] over the keys, the end keys kept)
    until it holds the limit. The look is left alone (the subject stays framed). Returns [(shot start, before,
    after, passes)]."""
    cam = sc.camera
    cuts = [i for i, k in enumerate(cam) if k.get('cut')] + [len(cam)]
    out = []
    for s, e in zip(cuts, cuts[1:]):
        i0, i1 = s, e - 1
        if any(abs(cam[i0]['t'] - f[0]) < 1e-6 for f in sc.first_person):
            continue                            # a first-person shot rides his eye: left as it is
        if i1 - i0 < 4:
            continue
        lo, hi = cam[i0]['t'] + 0.34, cam[i1]['t'] - 0.001
        before = m = _cam_amax(cam, lo, hi)
        n = 0
        while m > limit and n < iters:
            ps = [list(cam[i]['pos']) for i in range(i0, i1 + 1)]
            for j in range(1, len(ps) - 1):
                cam[i0 + j]['pos'] = [round((ps[j - 1][c] + 2 * ps[j][c] + ps[j + 1][c]) / 4.0, 4) for c in range(3)]
            m2 = _cam_amax(cam, lo, hi)
            if m2 >= m:                         # no better (its ends bend): back to the last pass, and stop
                for j in range(len(ps)):
                    cam[i0 + j]['pos'] = ps[j]
                break
            m = m2
            n += 1
        if n:
            out.append((cam[i0]['t'], round(before, 5), round(m, 5), n))
    return out


def spring_cam(target, t0, t1, omega=0.06, a_max=0.0014, j_max=0.00012):
    """A camera operator's follow: a point chasing target(t) (critically damped at omega a frame), its frame-to-frame
    velocity change held under a_max and the change of that under j_max (60 fps frames), starting on the target
    with its velocity. Returns fn(t) (linear between the simulated frames)."""
    n = int(round((t1 - t0) * 3))
    p = list(target(t0))
    q1 = target(t0 + 1 / 3.0)
    v = [q1[i] - p[i] for i in range(3)]
    acc = [0.0, 0.0, 0.0]
    pts = [list(p)]
    for k in range(1, n + 1):
        t = t0 + k / 3.0
        g = target(t)
        gv = [(target(t + 1 / 3.0)[i] - target(t - 1 / 3.0)[i]) / 2.0 for i in range(3)]
        want = [omega * omega * (g[i] - p[i]) + 2 * omega * (gv[i] - v[i]) for i in range(3)]
        m = math.sqrt(sum(c * c for c in want))
        if m > a_max:
            want = [c * a_max / m for c in want]
        dj = [want[i] - acc[i] for i in range(3)]
        m = math.sqrt(sum(c * c for c in dj))
        if m > j_max:
            dj = [c * j_max / m for c in dj]
        acc = [acc[i] + dj[i] for i in range(3)]
        v = [v[i] + acc[i] for i in range(3)]
        p = [p[i] + v[i] for i in range(3)]
        pts.append(list(p))

    def f(t):
        x = max(0.0, min(n, (t - t0) * 3.0))
        i = min(int(x), n - 1)
        u = x - i
        return [pts[i][c] + (pts[i + 1][c] - pts[i][c]) * u for c in range(3)]
    return f
