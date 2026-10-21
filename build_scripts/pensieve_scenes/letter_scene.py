"""Generates the Letter memory: memories/letter.json -> letter_flood.json -> letter_crossing.json -> letter_hut.json
(end_action wake).

Usage (from the repo root): python -I build_scripts/pensieve_scenes/letter_scene.py

The first time a new player opens their Hogwarts letter, LetterMemory starts `letter`; the chain ends in a wake and the
real letter screen opens. Sets: privet_drive.py (letter, letter_flood) and letter_island.py (letter_crossing,
letter_hut), whose ANCHORS this script stages against. The world dicts both sets build are the ground truth for every
collision check here.

SHOT LIST (seconds from each scene's start; 170 s of film in all, plus the chain fades)
==========================================================
letter (56 s; Privet Drive, an overcast morning; next letter_flood)
  C1   0.0- 7.6  First person on the cot under the stairs, eyes_open from black (3 s): the stepped stair underside.
                 Feet thud down the stairs overhead (Dudley, five treads), dust shakes down at each one. Three
                 knocks; the cupboard door swings open (memory_block door, hinged in Python); Petunia: "Up! Get up!
                 Now!" and she stalks off to the kitchen.
  C2   7.6-14.8  The same take: the camera lifts off the boy's face and pulls back out through the open door into
                 the hall, revealing him lying on the tiny cot (a life:false lying rig), the stairs over him; he
                 turns his head to the light.
  C3  14.8-20.0  The close of identical houses under a grey English morning, a slow crane down toward No. 4; a tabby
                 cat sits on the hedge.
  C4  20.0-23.6  Low on the hall floor, looking at the front door: the brass plate flaps (slot sfx) and the post
                 drops through onto the mat: a bill, a postcard, then the heavy parchment envelope.
  C5  23.6-28.0  Over the boy's shoulder as he picks it up and turns it over: the green-ink address as narration,
                 "Mr. {viewer}, The Cupboard under the Stairs, 4 Privet Drive, Little Whinging, Surrey".
  C6  28.0-31.0  Kitchen wide: Vernon behind his paper at the head of the table, Petunia and Dudley at breakfast; the
                 boy walks in reading the envelope.
  C7  31.0-35.0  Low two-shot over the table: Vernon lowers the paper, sees it, snatches it. "Who'd be writing to
                 you? Give that here, boy!"
  C8  35.0-38.6  The days pile up. The mat again: three letters through the slot.
  C9  38.6-42.4  Down the hall from the kitchen: eight letters shoot through; the boy runs for them and Vernon steps
                 out of the living room into his way, arm out.
  C10 42.4-46.4  Low at the door: a stream of twenty, rattling the plate, piling on the mat.
  C11 46.4-51.0  Behind Vernon at the door: "No more post! Not one more letter comes through that door!" He nails two
                 boards over the slot (hammer sfx, the boards pressed on, a swing per blow).
  C12 51.0-56.0  The street: owls on every ridge, chimney, canopy, hedge and lamp post (66), their heads turning one
                 after another to No. 4; the owls sfx. Slow push on the house; chain fade.
letter_flood (30 s; the same set; a Sunday; next letter_crossing)
  F1   0.0- 5.4  The living room wide from the bay: Vernon smug in his armchair, Petunia and Dudley on the sofa, the
                 boy against the wall. "Fine day, Sunday. No post on Sundays!"
  F2   5.4-10.3  Close on Vernon settling back with a nod; a rumble in the chimney (soot puffs), every head turns.
  F3  10.3-13.4  Cut on the burst, low on the hearth: 300 letters erupt from the fireplace (letter_flood in the
                 living_room_box), the whoosh, the music's big swell lands here.
  F4  13.4-17.6  High and wide: the room in a blizzard of envelopes. Vernon gets up out of the chair (STAND_UP):
                 "Get away from them! Out! Out!" Petunia and Dudley shield their faces on the sofa.
  F5  17.6-21.6  Low, tracking the boy as he jumps and grabs, arms out, reaching high.
  F6  21.6-25.4  From the doorway: Vernon flailing at letters in the foreground, the boy leaping beyond him.
  F7  25.4-30.0  A slow push-in on the boy's raised hand as one letter spirals down into it and his hand closes on
                 it (hold hogwarts_invite); chain fade.
letter_crossing (17 s; letter_island at night; next letter_hut)
  X1   0.0- 4.4  High and wide over the storm: the little boat far below, heading for the rock, lightning shows the
                 hut on top.
  X2   4.4- 8.4  Low beside the boat at the waterline, tracking: the boat heaving and rolling on rough_sea (intensity
                 1.0), rain, spray; Vernon rowing (arms keyed to the oar stroke).
  X3   8.4-12.0  Astern, over the transom: Vernon rowing toward the camera, Petunia clutching Dudley in the stern,
                 the boy at the bow beyond; lightning.
  X4  12.0-17.0  From the rock by the landing: the boat comes in on the swell to the ledge; the lit window above;
                 a push-in, chain fade.
letter_hut (60 s; letter_island; end_action wake)
  H1   0.0- 5.0  The hut on its rock from the sea, the window glowing, lightning, the boat moored at the landing.
  H2   5.0-10.0  Inside, low by the dying fire: Dudley asleep on the sofa, Petunia asleep on the bed, Vernon asleep
                 in his chair, the boy lying awake on the floor.
  H3  10.0-14.4  Close on the boy's face in the firelight. BOOM: the first pound; he startles up onto an elbow and
                 sits up.
  H4  14.4-19.4  Wide toward the door from the sofa end: the two leaves shudder at each blow. Vernon jumps up with
                 his gun, Petunia sits up, Dudley sits up. "Who's there? I'm warning you, I'm armed!"
  H5  19.4-20.6  Close on the door: one more blow.
  H6  20.6-25.6  Cut on the crash: low on the floor facing the doorway, the leaves break and fall flat into the hut
                 (yaw kick, then pitch -90), dust, lightning behind: Hagrid, huge, stoops through the low doorway
                 (45 degrees, chin tucked, on the door's centre line) and straightens up on the fallen door.
  H7  25.6-29.6  Low hero angle on Hagrid: "Sorry about that." The Dursleys cower in their corner.
  H8  29.6-35.0  Hagrid looks round the room, finds the boy, his face softening: "There you are. Last time I saw you,
                 you were only a baby." He steps over and crouches.
  H9  35.0-39.6  Close on the boy, looking up: "You're a wizard." (the celesta enters)
  H10 39.6-45.6  Over the boy on Hagrid crouched: he takes the letter from his coat and holds it out. "This is for
                 you. It's been trying to reach you for weeks."
  H11 45.6-60.0  First person at the boy's eye: Hagrid's great hand holding the letter out; the boy's own hand
                 reaches up into frame and takes it, draws it in; eyes_close from 56.4; the scene ends on shut
                 lids and the wake returns him to the real world, where the letter opens.

Voices: the plan's line ids (pensieve.voice.letter_*), each subtitled for its measured length
(build_scripts/sounds/letter_clip_lengths.json); an assert fails if a line runs past its slot. Music: the user's
tracks, cut by build_scripts/sounds/letter_music.py into pensieve.music.letter_* (see MUSIC below).

Asserts: each JSON under the 900,000-char cap; unique actor ids; every key time sorted and inside the scene; every
camera key and every straight camera move clear of every block shape (doors, slabs, stairs, carpets, beds...), and
over the sea above the highest crest; no two standing players within 0.6 blocks; no walker's feet, waist or head
inside a block shape; every seated hold and STAND_UP (Vernon's armchair, the kitchen chairs, the hut chair) has
both leg boxes clear of the set; subtitles inside their slots; every `next` names a generated scene; the flood box
is the anchor's living_room_box and all air in the set; Hagrid's whole body (the player model's boxes at 1.5, the
keyed stoop sampled every tick) clears the doorway; the door leaves fall from yaw 180 into the hut; the boat only
rides seas of intensity 1 or less; eyes_open is the first event of `letter` and eyes_close the last of `letter_hut`.
"""
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from scene_common import write_scene, FREE, MEMORIES  # noqa: E402
from wand_scene import (tk, r3, yaw_to, clamp, lerp, sample_path, samples_fast, sample_cam,  # noqa: E402
                        Life, Clips, Mover, pose, act)
import human_motion as HM  # noqa: E402
import privet_drive as PD  # noqa: E402
import letter_island as LI  # noqa: E402

MAX_JSON = 900000
IDS = ('letter', 'letter_flood', 'letter_crossing', 'letter_hut')
KID, ADULT, GIANT = 0.75, 1.0, 1.5
SFX = 'hogwartsmagic:pensieve.sfx.letter_'
RES = os.path.normpath(os.path.join(MEMORIES, '..', '..', '..'))
REGISTERED = set(json.load(open(os.path.join(RES, 'assets', 'hogwartsmagic', 'sounds.json'), encoding='utf-8')))
CLIP_LENGTHS = json.load(open(os.path.join(HERE, '..', 'sounds', 'letter_clip_lengths.json'), encoding='utf-8'))
SKINS = set(json.load(open(os.path.join(MEMORIES, '_skins.json'), encoding='utf-8')))
INVITE = 'hogwartsmagic:hogwarts_invite'
GUN = 'minecraft:crossbow'               # Vernon's shotgun: no gun in the game, a crossbow reads as a weapon
PA, IA = PD.ANCHORS, LI.ANCHORS
SKIN = {'boy': {'viewer': True}, 'vernon': {'texture': 'vernon'}, 'petunia': {'texture': 'petunia'},
        'dudley': {'texture': 'dudley'}, 'hagrid': {'texture': 'hagrid'}}
for _s in SKIN.values():
    assert _s.get('texture', 'hagrid') in SKINS, _s

LINES = {
    'letter_petunia_up': ('Aunt Petunia', 'Up! Get up! Now!'),
    'letter_vernon_mine': ('Uncle Vernon', "Who'd be writing to you? Give that here, boy!"),
    'letter_vernon_nomail': ('Uncle Vernon', 'No more post! Not one more letter comes through that door!'),
    'letter_vernon_sunday': ('Uncle Vernon', 'Fine day, Sunday. No post on Sundays!'),
    'letter_vernon_shout': ('Uncle Vernon', 'Get away from them! Out! Out!'),
    'letter_vernon_gun': ('Uncle Vernon', "Who's there? I'm warning you, I'm armed!"),
    'letter_hagrid_sorry': ('Hagrid', 'Sorry about that.'),
    'letter_hagrid_found': ('Hagrid', 'There you are. Last time I saw you, you were only a baby.'),
    'letter_hagrid_wizard': ('Hagrid', "You're a wizard."),
    'letter_hagrid_letter': ('Hagrid', "This is for you. It's been trying to reach you for weeks."),
}
for _l, (_who, _txt) in LINES.items():
    assert '{viewer}' not in _txt and 'Harry' not in _txt, _l

# The music (user-supplied film soundtrack, private use only): build_scripts/sounds/letter_music.py cuts each cue
# from run/music/letter/ (its CUES: source file, start, end, fades) with the cut points timed against MUSIC_AT here.
sys.path.insert(0, os.path.join(HERE, '..', 'sounds'))
from letter_music import CUES as MUSIC  # noqa: E402
MUSIC_AT = {'letter_cupboard': ('letter', 2.0), 'letter_flood': ('letter_flood', 0.0),
            'letter_crossing': ('letter_crossing', 0.0), 'letter_hut': ('letter_hut', 0.0),
            'letter_wizard': ('letter_hut', 34.6)}


def line_len(line):
    return float(CLIP_LENGTHS[line])


# ================================================================ block shapes
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


# ================================================================ the player model in the world
def kpx(scale):
    return HM.px_blocks(scale)


def lying_root_y(surface, scale):
    """Root of a body lying on its back (or sitting on the floor with the legs straight out) on `surface`: the
    leg boxes' and the torso's backs rest on it (the head's back sinks a hair into the mattress)."""
    return surface - (HM.MODEL_ORIGIN * 16 - (HM.HIP_PX + HM.LEG_HALF_WIDTH_PX + HM.LAYER_PX)) * kpx(scale)


REST = {'head': (0, 0, 0), 'body': (0, 0, 0), 'right_arm': (-5, 2, 0), 'left_arm': (5, 2, 0),
        'right_leg': (-1.9, 12, 0), 'left_leg': (1.9, 12, 0)}


def torso_pose(a, head=(0, 0, 0), r_arm=(0, 0, 0), l_arm=(0, 0, 0), legs=(-90, 0), legs_off=(0, 0, 0)):
    """The six parts (absolute rot, absolute pivot px) of a body whose torso is pitched `a` degrees about the hips
    (negative leans back: -90 lies on the back), the head and arms turned `head`/`*_arm` relative to the torso, the
    legs at x `legs[0]`, splayed +-legs[1]. The same mechanics as HumanMotion.hipTurn, done whole: the neck and the
    shoulders go where the turned torso puts them, so a model part pivoting at its top swings the shoulders."""
    r = math.radians(a)
    nk = (0.0, 12 - 12 * math.cos(r), -12 * math.sin(r))
    sh = (0.0, 10 - 10 * math.cos(r), -10 * math.sin(r))
    out = {
        'body': ([a, 0, 0], list(nk)),
        'head': ([a + head[0], head[1], head[2]], list(nk)),
        'right_arm': ([a + r_arm[0], r_arm[1], r_arm[2]], [-5 + sh[0], 2 + sh[1], sh[2]]),
        'left_arm': ([a + l_arm[0], l_arm[1], l_arm[2]], [5 + sh[0], 2 + sh[1], sh[2]]),
        'right_leg': ([legs[0], legs[1], 0], [-1.9 + legs_off[0], 12 + legs_off[1], legs_off[2]]),
        'left_leg': ([legs[0], -legs[1], 0], [1.9 + legs_off[0], 12 + legs_off[1], legs_off[2]]),
    }
    return out


def rig_limbs(keys):
    """limbs (mode set, rot + offset) for a life:false rig from [(t_sec, torso_pose dict, ease)]."""
    out = {}
    for part in REST:
        rot, off, last = [], [], -1
        for t, pz, e in keys:
            tt = tk(t)
            rv, ov = pz[part]
            kr = {'t': tt, 'v': [round(v, 2) for v in rv]}
            ko = {'t': tt, 'v': [round(v, 3) for v in ov]}
            if e and e != 'in_out':
                kr['ease'] = e
                ko['ease'] = e
            if tt == last:
                rot[-1], off[-1] = kr, ko
            else:
                assert tt > last, (part, t)
                rot.append(kr)
                off.append(ko)
            last = tt
        out[part] = {'rot': rot, 'offset': off, 'mode': 'set'}
    return out


def on_arc(keys):
    """Rig keys with the torso's swing sampled on its arc: between two key poses whose torso pitch differs by
    more than 15 degrees, extra keys every 3 ticks put the neck and shoulders where the turned torso has them
    (a straight blend of the pivots would cut the chord and part the torso from the hips)."""
    out = []
    for (t0, p0, _), (t1, p1, _) in zip(keys, keys[1:]):
        out.append((t0, p0, None))
        a0, a1 = p0['body'][0][0], p1['body'][0][0]
        if abs(a1 - a0) > 15:
            n = max(2, int((t1 - t0) * 20 / 3))
            for i in range(1, n):
                f = i / n
                e = 3 * f * f - 2 * f ** 3
                q = lerp_pose(p0, p1, e)
                rr = torso_pose(a0 + (a1 - a0) * e)
                for part in ('body', 'head', 'right_arm', 'left_arm'):
                    q[part] = (q[part][0], rr[part][1])
                out.append((t0 + f * (t1 - t0), q, 'linear'))
    out.append(keys[-1])
    return out


def lerp_pose(p, q, f):
    return {k: ([p[k][0][i] + (q[k][0][i] - p[k][0][i]) * f for i in range(3)],
                [p[k][1][i] + (q[k][1][i] - p[k][1][i]) * f for i in range(3)]) for k in p}


def arm_tip(root, yaw, scale, side, rot, pivot_px=None):
    """World position of the hand (the arm box's far end) for an arm at absolute rot [x, y, z] degrees."""
    px = pivot_px or REST[side]
    tip = (-1.0 if side == 'right_arm' else 1.0, 10.0, 0.0)
    v = HM._turned(tip[0], tip[1], tip[2], math.radians(rot[0]), math.radians(rot[1]), math.radians(rot[2]))
    return HM.model_to_world(root, yaw, scale, px[0] + v[0], px[1] + v[1], px[2] + v[2])


def eye_of(root, yaw, scale, head_pivot=(0, 0, 0), head_rot=(0, 0, 0), fwd=0.0):
    """The eye point (4 px up the head on its front face), moved fwd blocks out along the face: for first-person
    cameras (fwd about 0.25 at 0.75 keeps the camera clear of the head, which renders without culling)."""
    def at(z):
        v = HM._turned(0.0, -4.0, z, math.radians(head_rot[0]), math.radians(head_rot[1]), math.radians(head_rot[2]))
        return HM.model_to_world(root, yaw, scale, head_pivot[0] + v[0], head_pivot[1] + v[1], head_pivot[2] + v[2])
    e, f = at(-4.0), at(-5.0)
    d = [f[i] - e[i] for i in range(3)]
    n = math.sqrt(sum(c * c for c in d)) or 1.0
    return [e[i] + d[i] / n * fwd for i in range(3)]


def fwd_vec(yaw):
    r = math.radians(yaw)
    return (-math.sin(r), math.cos(r))


def ahead(p, yaw, d, side=0.0, y=None):
    """p moved d along yaw and side to the actor's right."""
    fx, fz = fwd_vec(yaw)
    rx, rz = -fz, fx
    return [p[0] + fx * d + rx * side, p[1] if y is None else y, p[2] + fz * d + rz * side]


# ================================================================ a scene
class Scene:
    def __init__(self, sid, title, length, world):
        self.sid, self.title, self.length, self.world = sid, title, length, world
        self.L = tk(length)
        self.actors, self.events, self.words, self.camera = [], [], [], []
        self.poses = {}
        self.lines = []
        self.taken = set()
        self.legs = []            # (kind, id, t, root_a, root_b, yaw, scale, pitch)
        self.rigs = set()         # actor ids that are lying / floor-sitting rigs (no walker checks)
        self.teleports = {}       # actor id -> [(t0, t1) ticks] path jumps (no walker checks inside)
        self.skip_pairs = set()
        self.over_sea = None      # (still y, max crest above it, [x0, x1, z0, z1]) for the camera-over-water check
        self.hagrid_ticks = set()  # ticks Hagrid passes the doorway (checked by check_hagrid instead)
        self.unseen = {}          # actor id -> [(t0, t1) seconds] heard, not seen (no body-in-block checks)
        self.seated = {}          # actor id -> [(t0, t1) seconds] seated (the leg boxes are checked instead)

    def ev(self, t, kind, eid, **kw):
        assert 0 <= t <= self.length, (self.sid, kind, eid, t)
        e = {'t': tk(t), 'kind': kind, 'id': eid}
        e.update(kw)
        self.events.append(e)
        return e

    def sfx(self, t, name, volume=1.0):
        assert 'pensieve.sfx.letter_' + name in REGISTERED, name
        self.ev(t, 'sfx', SFX + name, volume=volume)

    def sound(self, t, sid, at=None, volume=1.0, pitch=1.0):
        kw = {'volume': volume}
        if pitch != 1.0:
            kw['pitch'] = round(pitch, 3)
        if at is not None:
            kw['at'] = [r3(c) for c in at]
        self.ev(t, 'sound', sid, **kw)

    def particles(self, t, pid, at, count, spread):
        self.ev(t, 'particles', pid, at=[r3(c) for c in at], count=count, spread=[r3(c) for c in spread])

    def music(self, cue, volume=1.0):
        sid, t = MUSIC_AT[cue]
        assert sid == self.sid
        assert 'pensieve.music.' + cue in REGISTERED, cue
        self.ev(t, 'music', 'hogwartsmagic:pensieve.music.' + cue, volume=volume)

    def say(self, t, line, slot_end, clips=None):
        who, text = LINES[line]
        secs = line_len(line)
        sid = 'pensieve.voice.' + line
        assert sid in REGISTERED, sid
        self.ev(t, 'voice', 'hogwartsmagic:' + sid, volume=1.0)
        self.words.append({'t': tk(t), 'text': text, 'subtitle': True, 'speaker': who,
                           'duration': max(20, tk(secs) + 6)})
        if clips is not None:
            clips.span(t, t + secs, 'TALK', 5, 8)
        self.lines.append((line, t, secs, slot_end))
        return secs

    def narrate(self, t, text, dur, size=None):
        w = {'t': tk(t), 'text': text, 'narration': True, 'duration': tk(dur)}
        if size:
            w['size'] = size
        self.words.append(w)

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
        if self.camera and self.camera[-1]['t'] == k['t'] and not cut:
            self.camera[-1] = k
        else:
            self.camera.append(k)

    def actor(self, aid, atype='player', skin=None, scale=1.0, mover=None, path=None, look=None, life=None,
              limbs=None, poses=None, actions=None, items=None, clips=None, block=None, alive=True, ride=None,
              name=None):
        a = {'id': aid, 'type': atype}
        if block:
            a['block'] = block
        if skin is not None:
            a['skin'] = skin
        if name:
            a['name'] = name
        if abs(scale - 1) > 1e-9:
            a['scale'] = round(scale, 3)
        if atype == 'player' and not alive:
            a['life'] = False
        if ride:
            a['ride'] = ride
        if items:
            a['items'] = items
        if mover is not None:
            path, look = mover.emit(self.length)
        if path:
            a['path'] = path
        if look:
            a['look'] = look
        lm = dict(limbs or {})
        if life is not None:
            lm.update(life.emit(self.L))
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


def block_path(sc, aid, keys, hide_before=None, hide_after=None, park=None):
    """A path that sits parked under the floor (park) until hide_before and after hide_after (seconds)."""
    out = []
    if hide_before is not None:
        out.append({'t': 0, 'pos': park})
        out.append({'t': max(0, tk(hide_before) - 1), 'pos': park, 'ease': 'linear'})
    for t, p, e in keys:
        k = {'t': tk(t), 'pos': [r3(c) for c in p]}
        if e and e != 'in_out':
            k['ease'] = e
        if out and out[-1]['t'] == k['t']:
            out[-1] = k
        else:
            assert not out or k['t'] > out[-1]['t'], (aid, t, out[-1])
            out.append(k)
    if hide_after is not None:
        if tk(hide_after) > out[-1]['t']:
            out.append({'t': tk(hide_after), 'pos': out[-1]['pos'], 'ease': 'linear'})
        out.append({'t': out[-1]['t'] + 1, 'pos': park, 'ease': 'linear'})
    return out


# ================================================================ checks
def check_camera(sc, cam, problems, margin=0.06):
    w = sc.world
    for i, k in enumerate(cam):
        if not in_air(w, k['pos'], margin):
            problems.append('%s camera key %d t %d pos %s is in %s' % (sc.sid, i, k['t'], k['pos'],
                                                                    w.get(cell(k['pos']))))
        if i and not k.get('cut'):
            a0, a1 = cam[i - 1]['pos'], k['pos']
            n = int(max(abs(a1[j] - a0[j]) for j in range(3)) / 0.08) + 1
            for s_ in range(1, n):
                p = [a0[j] + (a1[j] - a0[j]) * s_ / n for j in range(3)]
                if not in_air(w, p, margin):
                    problems.append('%s camera move into key %d (t %d) passes %s (%s)' % (
                        sc.sid, i, k['t'], [r3(c) for c in p], w.get(cell(p))))
                    break
    assert all(cam[i]['t'] <= cam[i + 1]['t'] for i in range(len(cam) - 1)), (sc.sid, 'camera keys out of order')
    assert cam[0]['t'] == 0, sc.sid
    if sc.over_sea:
        sea_y, crest, box = sc.over_sea
        for t in range(0, sc.L + 1, 2):
            p = sample_cam(cam, t)
            if box[0] <= p[0] <= box[1] and box[2] <= p[2] <= box[3] and p[1] < sea_y + crest + 0.5:
                problems.append('%s camera at t %d (%s) is within reach of the waves' % (sc.sid, t, [r3(c) for c in p]))
                break


def check_keys(sc, acts):
    ids_ = [a['id'] for a in acts]
    assert len(ids_) == len(set(ids_)), (sc.sid, 'duplicate actor ids')
    for a in acts:
        for kk in ('path', 'look', 'anims', 'poses', 'actions'):
            ts = [k['t'] for k in a.get(kk, [])]
            assert ts == sorted(ts), (sc.sid, a['id'], kk, ts)
            if kk in ('path', 'look', 'anims'):
                assert len(ts) == len(set(ts)), (sc.sid, a['id'], kk)
            assert all(0 <= t <= sc.L for t in ts), (sc.sid, a['id'], kk, ts)
        for part, d in a.get('limbs', {}).items():
            for kind in ('rot', 'offset'):
                ts = [k['t'] for k in d.get(kind, [])]
                assert ts == sorted(ts) and len(ts) == len(set(ts)), (sc.sid, a['id'], part, kind)
                assert all(0 <= t <= sc.L for t in ts), (sc.sid, a['id'], part, ts)
        if a['type'] == 'player' and 'scale' in a:
            assert a['scale'] in (KID, GIANT), (a['id'], a['scale'])


def in_teleport(sc, aid, t):
    return any(t0 - 2 <= t <= t1 + 2 for t0, t1 in sc.teleports.get(aid, ()))


def find_teleports(sc, a):
    """Path segments of 1-3 ticks that move over 0.6 blocks: jumps the camera never sees."""
    out = []
    p = a.get('path', [])
    for k0, k1 in zip(p, p[1:]):
        if k1['t'] - k0['t'] <= 3 and math.dist(k0['pos'], k1['pos']) > 0.6:
            out.append((k0['t'], k1['t']))
    return out


def check_walkers(sc, problems, hidden_y=-2.5):
    people = [a for a in sc.actors if a['type'] == 'player' and 'ride' not in a]
    for a in people:
        sc.teleports.setdefault(a['id'], []).extend(find_teleports(sc, a))
    ticks = list(range(0, sc.L + 1, 2))
    tracks = {a['id']: samples_fast(a['path'], ticks) for a in people}
    scale = {a['id']: a.get('scale', 1.0) for a in people}
    n = 0
    for i, t in enumerate(ticks):
        for a in people:
            aid = a['id']
            cur = tracks[aid][i]
            if cur[1] < hidden_y or in_teleport(sc, aid, t):
                continue
            moving = i and math.dist(tracks[aid][i - 1], cur) > 1e-4
            if moving:
                for b in people:
                    if b['id'] == aid or frozenset((aid, b['id'])) in sc.skip_pairs:
                        continue
                    op = tracks[b['id']][i]
                    if op[1] < hidden_y:
                        continue
                    n += 1
                    d = math.hypot(cur[0] - op[0], cur[2] - op[2])
                    if d < 0.6 and abs(cur[1] - op[1]) < 1.6:
                        problems.append('%s t %d: %s at %s is %.2f from %s at %s' % (
                            sc.sid, t, aid, [r3(c) for c in cur], d, b['id'], [r3(c) for c in op]))
            if aid in sc.rigs or any(tk(u0) <= t <= tk(u1) for u0, u1 in list(sc.unseen.get(aid, [])) + list(sc.seated.get(aid, []))):
                continue
            s = scale[aid]
            for dy in (0.06, 0.9 * s, 1.7 * s):
                for dx, dz in ((0, 0), (0.17 * s, 0.17 * s), (-0.17 * s, -0.17 * s), (0.17 * s, -0.17 * s),
                               (-0.17 * s, 0.17 * s)):
                    q = [cur[0] + dx, cur[1] + dy, cur[2] + dz]
                    hit = shape_hit(sc.world, q)
                    if hit and not (aid == 'hagrid' and t in sc.hagrid_ticks):
                        problems.append('%s t %d: %s body point %s in %s' % (sc.sid, t, aid, [r3(c) for c in q],
                                                                            bid_of(hit)))
                        break
                else:
                    continue
                break
    return n


BOXES = {'head': ((-4, 4), (-8, 0), (-4, 4)), 'body': ((-4, 4), (0, 12), (-2, 2)),
         'right_arm': ((-3, 1), (-2, 10), (-2, 2)), 'left_arm': ((-1, 3), (-2, 10), (-2, 2)),
         'right_leg': ((-2, 2), (0, 12), (-2, 2)), 'left_leg': ((-2, 2), (0, 12), (-2, 2))}
RIG_SINK = 0.15           # a rig's box may sink this far into what it lies on (the head into a mattress)


def check_rigs(sc, problems):
    """Every lying / floor-sitting rig at every limb key: no part box, shrunk by RIG_SINK, inside a block shape."""
    n = 0
    for a in sc.actors:
        if a['id'] not in sc.rigs or 'limbs' not in a:
            continue
        root = a['path'][-1]['pos'] if len(a['path']) == 1 else None
        s = a.get('scale', 1.0)
        sh = RIG_SINK / kpx(s)
        for i, kr in enumerate(a['limbs']['body']['rot']):
            t = kr['t']
            r = root or sample_path(a['path'], t)
            if r[1] < -2.5:
                continue
            yaw = sample_path([{'t': k['t'], 'pos': [k['yaw'], 0, 0], 'ease': k.get('ease', 'in_out')}
                               for k in a['look']], t)[0]
            for part, (bx, by, bz) in BOXES.items():
                rot = a['limbs'][part]['rot'][i]['v']
                piv = a['limbs'][part]['offset'][i]['v']
                for x in (bx[0] + sh, bx[1] - sh):
                    for y in (by[0] + sh, by[1] - sh):
                        for z in (bz[0] + sh, bz[1] - sh):
                            v = HM._turned(x, y, z, *[math.radians(c) for c in rot])
                            q = HM.model_to_world(r, yaw, s, piv[0] + v[0], piv[1] + v[1], piv[2] + v[2])
                            n += 1
                            hit = shape_hit(sc.world, q)
                            if hit:
                                problems.append('%s t %d: rig %s %s corner %s in %s' % (
                                    sc.sid, t, a['id'], part, [r3(c) for c in q], bid_of(hit)))
                                break
                        else:
                            continue
                        break
                    else:
                        continue
                    break
    return n


LEG_TOUCH = 0.03


def check_legs(sc, problems):
    n = 0

    def test(tag, root, yaw, scale, pitch):
        nonlocal n
        for side, splay in (('right', 8), ('left', -8)):
            for c in HM.leg_box(root, yaw, scale, pitch, splay, 0, side):
                n += 1
                hit = shape_hit(sc.world, [c[0], c[1] + LEG_TOUCH, c[2]])
                if hit:
                    problems.append('%s %s: %s leg corner %s in %s' % (sc.sid, tag, side, [r3(v) for v in c],
                                                                       bid_of(hit)))
                    return

    for kind, aid, t, a, b, yaw, scale, pitch in sc.legs:
        if kind == 'hold':
            for dy in (-20, 0, 20):
                test('%s seated (t %.1f, yaw %+d)' % (aid, t, dy), a, yaw + dy, scale, pitch)
        elif kind == 'stand':
            for i in range(21):
                s_ = i * 0.05
                root, lp = HM.stand_up_root(s_, a, b)
                test('%s STAND_UP %.2f s in' % (aid, s_), root, yaw, scale, lp)
    return n


def check_lines(sc, problems):
    for line, t, secs, slot_end in sc.lines:
        if t + secs > slot_end + 1e-6:
            problems.append('%s: line %s at %.2f lasts %.2f s, past its slot end %.2f' % (sc.sid, line, t, secs,
                                                                                         slot_end))
        if t + secs > sc.length - 0.3:
            problems.append('%s: line %s runs into the scene end' % (sc.sid, line))
    sl = sorted(sc.lines, key=lambda l: l[1])
    for a, b in zip(sl, sl[1:]):
        if b[1] < a[1] + a[2] + 0.15:
            problems.append('%s: line %s starts before %s ends' % (sc.sid, b[0], a[0]))


def finish(sc, scene, extra=None):
    problems = []
    check_camera(sc, scene['camera'], problems)
    check_keys(sc, scene['actors'])
    check_lines(sc, problems)
    for e in scene['events']:
        if e['kind'] == 'block' and e['id'] == 'minecraft:light' and not in_air(sc.world, [c + 0.5 for c in e['at']]):
            problems.append('%s light block at %s replaces %s' % (sc.sid, e['at'], sc.world.get(tuple(e['at']))))
        if e['kind'] == 'effect' and e['id'] == 'rough_sea':
            assert e.get('intensity', 1.0) <= 1.0 + 1e-9 or not any(
                a['type'] == 'hogwartsmagic:memory_rowboat' for a in scene['actors']), 'boat on a sea over 1'
    if extra:
        extra(problems)
    n_prox = check_walkers(sc, problems)
    n_legs = check_legs(sc, problems) + check_rigs(sc, problems)
    if problems:
        print('\n'.join(problems[:60]))
        print('... %d problems in %s' % (len(problems), sc.sid))
        raise SystemExit(1)
    txt = write_scene(scene, sc.sid, width=2000)
    assert len(txt) < MAX_JSON, '%s.json is %d chars (cap %d)' % (sc.sid, len(txt), MAX_JSON)
    assert len(txt.encode('utf-8')) < 1000000
    return len(txt), n_prox, n_legs


# ================================================================ worlds
def privet_world():
    w = {}
    PD.build(w)
    return w


def island_world():
    w = {}
    LI.build(w)
    return w


SET_PRIVET = {'templates': [{'id': 'hogwartsmagic:privet_drive', 'at': list(PD.TEMPLATE_AT)}],
              'time_of_day': PD.TIME_OF_DAY, 'build_ticks': 80}
SET_ISLAND = {'templates': [{'id': 'hogwartsmagic:letter_island', 'at': list(LI.TEMPLATE_AT)}],
              'time_of_day': LI.TIME_OF_DAY, 'build_ticks': 80}


# ================================================================ limb tracks with offsets
class Limbs(Life):
    """Hand-keyed limbs (mode add) with pivot offsets as well as rotations: change(part, t, rot, dur) and
    off(part, t, px, dur). bend() turns the torso about the hips (HumanMotion.hipTurn done by hand)."""

    def __init__(self):
        super().__init__()
        self.offs = {}

    def off(self, part, t, v, dur=0.4, hold=True):
        if part not in self.offs:
            from wand_scene import Channel
            self.offs[part] = Channel([0, 0, 0])
        self.offs[part].change(t, v, dur, hold)

    def bend(self, t, a, dur=0.4, head=0.0, arms=None, hold=True):
        """Lean the torso a degrees forward at the hips from t over dur; the head tips by `head` on top of it (an
        absolute add), the arms by `arms` (default: they follow the torso)."""
        r = math.radians(a)
        nk = [0, round(12 - 12 * math.cos(r), 3), round(-12 * math.sin(r), 3)]
        sh = [0, round(10 - 10 * math.cos(r), 3), round(-10 * math.sin(r), 3)]
        self['body'].change(t, [a, 0, 0], dur, hold)
        self.off('body', t, nk, dur, hold)
        self['head'].change(t, [head, 0, 0], dur, hold)
        self.off('head', t, nk, dur, hold)
        for side in ('right_arm', 'left_arm'):
            v = arms.get(side) if isinstance(arms, dict) else None
            self[side].change(t, v if v is not None else [a, 0, 0], dur, hold)
            self.off(side, t, sh, dur, hold)

    def emit(self, end):
        out = super().emit(end)
        for part, ch in self.offs.items():
            ks = ch.keys(end)
            if len(ks) <= 1 and all(abs(c) < 0.01 for c in ks[0][1]):
                continue
            out.setdefault(part, {})['offset'] = [{'t': t, 'v': [round(c, 3) for c in v]} for t, v in ks]
        return out


SEATED = {'right_leg': [-90, 8, 0], 'left_leg': [-90, -8, 0]}


def seat_hip(seat, scale):
    """The seated hip on a stair seat: half a leg's width ahead of the cell centre, so the back of the thighs
    clears the stair's raised back half (whose front face runs through the cell centre)."""
    p = ahead([seat[0], 0, seat[2]], seat[3], HM.leg_half_width(scale) + 0.012)
    return [round(p[0], 4), p[1], round(p[2], 4)]


def seat_root(seat, scale):
    h = seat_hip(seat, scale)
    return [h[0], round(HM.seated_root_y(seat[1], scale), 4), h[2]]


def stand_spot(seat, scale):
    """Where STAND_UP leaves the root: stand_ahead in front of the seated hip, on the floor (seat top - 0.5)."""
    return ahead(seat_hip(seat, scale), seat[3], HM.stand_ahead(scale), y=round(seat[1] - 0.5, 4))


def hinge_keys(centre0, hinge_rel, yaws):
    """memory_block keys that turn a block about a vertical hinge line: [(t_sec, yaw)] -> (path, look) with the
    hinge point (hinge_rel from the block centre, x/z) held fixed."""
    hx, hz = centre0[0] + hinge_rel[0], centre0[2] + hinge_rel[1]
    path, look = [], []
    for t, y in yaws:
        a = math.radians(y)
        rx = hinge_rel[0] * math.cos(a) - hinge_rel[1] * math.sin(a)
        rz = hinge_rel[0] * math.sin(a) + hinge_rel[1] * math.cos(a)
        c = [hx - rx, centre0[1], hz - rz]
        path.append({'t': tk(t), 'pos': [round(v, 4) for v in c], 'ease': 'linear'})
        look.append({'t': tk(t), 'yaw': round(y, 2), 'ease': 'linear'})
    return path, look


def swing_curve(t0, dur, y0, y1, overshoot=0.0, step=0.1):
    """A door swing sampled every step: ease-out from y0 to y1 past it by overshoot, settling back."""
    out = []
    n = int(round(dur / step))
    for i in range(n + 1):
        f = i / n
        if overshoot:
            g = 1 - (1 - f) ** 3
            g = g + overshoot * math.sin(math.pi * f) * f
        else:
            g = 1 - (1 - f) ** 3
        out.append((t0 + f * dur, y0 + (y1 - y0) * g))
    return out


def plate_centre_inner(face_z, s, proud=0.0):
    """The centre z of a pressure-plate memory_block (scale s, pitch 90) that lays its plate flush on a door's
    north face at face_z (or proud of it)."""
    return face_z - proud - s / 16.0 + s / 2.0


def flat_block_y(surface, s):
    """Centre y of a carpet / pressure plate memory_block (scale s) lying on surface."""
    return surface + s / 2.0 + 0.002


PARK_PRIVET = [0.5, -6.0, -20.5]     # under the set, out of every shot (the void below the template)
PARK_ISLAND = [0.5, -20.0, 0.5]


# ================================================================ letter: the cupboard, the first letter, more letters
POUND_HITS = (0.0, 0.35, 0.58, 0.85, 1.07, 1.35, 1.55, 1.83, 2.08)   # letter_pound.ogg onsets (measured)
CRASH_IMPACT = 0.58                                                 # letter_door_crash.ogg: the slam
HAMMER_HITS = (0.05, 0.40, 0.75, 1.07, 1.40, 1.73, 2.05)            # letter_hammer.ogg onsets
SLOT = PA['letter_slot']
SLOT_IN = PA['letter_slot_inner']          # the door's hall face (z -2.1875)
PLATE_S = 0.5                              # the brass plate, scaled up from the anchor's 0.3 to take an envelope
MAT_Y = PA['hall_mat'][1]
LETTER_Y = MAT_Y + 0.012                   # a memory_letter lies 0.025 above its path (renderer lift)


def plate_actors(sc):
    lp = PA['letter_plate']
    st = lp['block']
    x, y = SLOT[0], lp['inner']['pos'][1]
    zi = round(plate_centre_inner(SLOT_IN[2], PLATE_S), 5)
    zo = round(SLOT[2] - PLATE_S / 2.0 + PLATE_S / 16.0, 5)
    sc.actor('plate_in', 'hogwartsmagic:memory_block', block=st, scale=PLATE_S,
             path=[{'t': 0, 'pos': [x, y, zi]}], look=[{'t': 0, 'yaw': 0, 'pitch': 90}])
    sc.actor('plate_out', 'hogwartsmagic:memory_block', block=st, scale=PLATE_S,
             path=[{'t': 0, 'pos': [x, y, zo]}], look=[{'t': 0, 'yaw': 0, 'pitch': -90}])


def post_through(sc, aid, t0, rest, kind='letter', rnd=None, park=PARK_PRIVET, gone=None, spin=None):
    """One piece of post pushed through the slot at t0 and falling onto the mat at `rest` ([x, z]); parked under
    the floor before t0 (and from `gone` on). kind 'letter' is a memory_letter, else a memory_block carpet state."""
    rnd = rnd or random.Random(aid)
    if gone is not None and gone >= sc.length - 0.05:
        gone = None
    x0 = SLOT[0] + rnd.uniform(-0.05, 0.05)
    s = 0.32 if kind != 'letter' else 1.0
    if kind == 'letter':
        y_slot, y_rest = SLOT[1] - 0.02, LETTER_Y + rnd.uniform(0, 0.012)
    else:
        y_slot, y_rest = SLOT[1] - s / 2.0, flat_block_y(MAT_Y, s) + rnd.uniform(0, 0.01)
    z_in = SLOT_IN[2] - 0.05
    z_out = SLOT_IN[2] - 0.32
    fall = rnd.uniform(0.42, 0.55)
    keys = [(t0, [x0, y_slot, z_in], 'linear'), (t0 + 0.16, [x0, y_slot - 0.03, z_out], 'linear'),
            (t0 + 0.16 + fall * 0.6, [lerp([x0], [rest[0]], 0.6)[0], y_rest + 0.25, (z_out + rest[1]) / 2], 'in'),
            (t0 + 0.16 + fall, [rest[0], y_rest, rest[1]], 'out')]
    path = block_path(sc, aid, keys, hide_before=t0, hide_after=gone, park=park)
    yaw = spin if spin is not None else rnd.uniform(-35, 35)
    if kind == 'letter':
        look = [{'t': 0, 'yaw': 0, 'pitch': 90}, {'t': tk(t0) + 3, 'yaw': 0, 'pitch': 90},
                {'t': tk(t0 + 0.16 + fall * 0.6), 'yaw': round(yaw * 0.6, 1), 'pitch': 62},
                {'t': tk(t0 + 0.16 + fall), 'yaw': round(yaw, 1), 'pitch': 90}]
        sc.actor(aid, 'hogwartsmagic:memory_letter', path=path, look=look)
    else:
        look = [{'t': 0, 'yaw': 0, 'pitch': 0}, {'t': tk(t0) + 3, 'yaw': 0, 'pitch': 0},
                {'t': tk(t0 + 0.16 + fall * 0.6), 'yaw': round(yaw * 0.6, 1), 'pitch': 28},
                {'t': tk(t0 + 0.16 + fall), 'yaw': round(yaw, 1), 'pitch': 0}]
        sc.actor(aid, 'hogwartsmagic:memory_block', block=kind, scale=s, path=path, look=look)


def mat_spot(rnd, i, n):
    """Where the i-th of n letters comes to rest on the mat (the reveal and the hall cell behind the door)."""
    a = rnd.uniform(0, 1)
    return [round(SLOT[0] + rnd.uniform(-0.32, 0.32), 3), round(-2.55 - 1.35 * a * (0.5 + 0.5 * i / max(1, n)), 3)]


def letter_scene():
    LEN = 56.0
    w = privet_world()
    door = PA['cupboard_door']
    for c in (door['lower'], door['upper']):
        w[tuple(c)] = 'minecraft:air'                     # replaced by the two memory_block leaves below
    sc = Scene('letter', 'The Letters', LEN, w)
    sc.poses = {'seated': SEATED, 'standing': {}}
    sc.ev(0, 'effect', 'eyes_open', at=[0, 0, 0], duration=60)
    sc.ev(0, 'effect', 'sky', at=[0, 0, 0], style='overcast')
    for c in (door['lower'], door['upper']):
        sc.ev(0, 'block', 'minecraft:air', at=c)
    sc.music('letter_cupboard')
    plate_actors(sc)

    # ---- the cupboard door: two memory_block halves of the real spruce door, swung open about the hinge
    T_KNOCK = (5.8, 6.15, 6.5)
    T_OPEN = 6.8
    swing = [(0.0, 0.0), (T_OPEN, 0.0)] + swing_curve(T_OPEN + 0.05, 0.55, 0.0, -86.0, overshoot=0.06)   # out into the hall
    hrel = (-0.5 + 1.5 / 16.0, 0.5)                       # the panel's mid-thickness at the south (hinge) edge
    for half, c, st in (('lower', door['lower'], door['closed'][0]), ('upper', door['upper'], door['closed'][1])):
        c0 = [c[0] + 0.5, c[1] + 0.5, c[2] + 0.5]
        path, look = hinge_keys(c0, hrel, swing)
        sc.actor('cupboard_door_' + half, 'hogwartsmagic:memory_block', block=st, path=path, look=look)
    sc.sound(T_OPEN, 'minecraft:block.wooden_door.open', at=[1.5, 1.0, -9.5], volume=0.9, pitch=0.9)

    # ---- the boy on the cot: a lying rig (head north at the tall end), then standing in the hall from C4 on
    k = kpx(KID)
    head_top_z = PA['cupboard_bed_head'][2] + 0.08
    hip = [PA['cupboard_bed'][0], round(lying_root_y(PA['cupboard_bed'][1], KID), 4),
           round(head_top_z + 20 * k, 4)]
    lie = torso_pose(-90, r_arm=(0, 0, 4), l_arm=(0, 0, -4))
    lie_turn = torso_pose(-90, head=(0, 0, -24), r_arm=(0, 0, 4), l_arm=(0, 0, -4))
    T_TURN = 10.9
    sc.actor('boy_cot', skin=SKIN['boy'], scale=KID, alive=False,
             path=block_path(sc, 'boy_cot', [(0, hip, None)], hide_after=14.8, park=PARK_PRIVET),
             look=[{'t': 0, 'yaw': 0}],
             limbs=rig_limbs([(0, lie, None), (T_TURN, lie, None), (T_TURN + 0.8, lie_turn, None)]))
    sc.rigs.add('boy_cot')
    COT_EYE = [hip[0], round(hip[1] + 16.016 * k + 0.09, 3), round(hip[2] - 16 * k, 3)]

    # ---- Dudley thumps down the stairs over the cupboard, then off to the kitchen (heard, not seen)
    dm = Mover('dudley', PA['stairs_top'], 0)
    dm.hold(2.9)
    stairs = PA['stairs_path']
    t = 2.9
    for i, p in enumerate(stairs[1:]):
        t += 0.42
        dm.to(t, p, 'linear')
        sc.sound(t - 0.05, 'minecraft:block.wood.step', at=[p[0], p[1] + 0.1, p[2]], volume=1.0, pitch=0.62)
        sc.sound(t - 0.02, 'minecraft:block.wood.fall', at=[p[0], p[1], p[2]], volume=0.6, pitch=0.55)
        if p[2] < -6.6:
            sc.particles(t, 'minecraft:white_ash', [2.5, 1.95 - 0.2 * i, -8.8 - 0.1 * i], 10, [0.25, 0.08, 0.45])
            sc.particles(t + 0.1, 'minecraft:ash', [2.5, 1.9 - 0.2 * i, -8.9], 6, [0.2, 0.05, 0.35])
    dm.walk(t + 0.1, [[1.5, 0, -5.15], [0.2, 0, -5.6], [-0.45, 0, -6.6], [-0.45, 0, -11.6], [-0.2, 0, -12.9],
                      [-1.5, 0, -13.6]], 2.4)
    T_DUD_GONE = dm.t + 0.05
    dm.jump(14.8, seat_root(PA['kitchen_seats']['dudley'], KID))
    sc.unseen['dudley'] = [(0.0, 14.8)]
    dc = Clips()
    sc.actor('dudley', skin=SKIN['dudley'], scale=KID, mover=dm,
             poses=[pose(14.85, 'seated', 1)], clips=dc)
    dud_seat = PA['kitchen_seats']['dudley']
    sc.legs.append(('hold', 'dudley', 15.0, seat_root(dud_seat, KID), None, dud_seat[3], KID, -90))
    sc.seated['dudley'] = [(14.8, LEN)]

    # ---- Petunia: knocks, flings the door open, "Up!", and stalks off to the kitchen
    knock_at = [PA['cupboard_door']['outside']['pos'][0] - 0.05, 0, -9.6]
    pm = Mover('petunia', [0.55, 0, -12.6], 0)
    pm.walk(3.9, [[0.55, 0, -11.2], knock_at], 1.9)
    pm.face(pm.t + 0.05, -90, 0.35)
    pl = Limbs()
    pa = []
    for t in T_KNOCK:
        pl['right_arm'].change(t - 0.14, [-78, 0, 6], 0.1)
        pl['right_arm'].change(t + 0.02, [-66, 0, 6], 0.1)
        sc.sound(t, 'minecraft:block.wood.hit', at=[1.1, 1.2, -9.5], volume=1.0, pitch=0.85)
    pl['right_arm'].change(T_OPEN - 0.15, [-72, -10, 0], 0.15)          # the handle, and a yank
    pl['right_arm'].change(T_OPEN + 0.15, [-30, 20, 0], 0.25)
    pl['right_arm'].change(T_OPEN + 0.9, [0, 0, 0], 0.5)
    pl['head'].change(T_OPEN + 0.1, [22, 0, 0], 0.3)                    # down at him in the cot
    pl['head'].change(9.2, [0, 0, 0], 0.4)
    pc = Clips()
    T_UP = 7.0
    sc.say(T_UP, 'letter_petunia_up', 9.4, pc)
    pm.walk(9.35, [[0.55, 0, -11.6], [0.3, 0, -13.1]], 1.8)
    pm.jump(14.8, seat_root(PA['kitchen_seats']['petunia'], ADULT))
    pet_seat = PA['kitchen_seats']['petunia']
    sc.legs.append(('hold', 'petunia', 15.0, seat_root(pet_seat, ADULT), None, pet_seat[3], ADULT, -90))
    sc.seated['petunia'] = [(14.8, LEN)]
    pm.face(14.81, pet_seat[3], 0.05)
    sc.actor('petunia', skin=SKIN['petunia'], mover=pm, life=pl, clips=pc, poses=[pose(14.85, 'seated', 1)])

    # ---- C1: first person on the cot; C2: the pull-out through the door
    sc.key(0, COT_EYE, [2.5, 2.4, -8.6], 72)
    sc.key(5.6, [COT_EYE[0], COT_EYE[1], COT_EYE[2] + 0.01], [2.46, 2.42, -8.45], 72)
    sc.key(6.9, [COT_EYE[0], COT_EYE[1], COT_EYE[2]], [1.6, 1.6, -9.3], 74)         # a glance to the light
    sc.key(7.6, [COT_EYE[0], COT_EYE[1] + 0.02, COT_EYE[2]], [1.7, 1.5, -9.25], 74)
    face = [hip[0], hip[1] + 0.72, hip[2] - 0.66]
    sc.key(9.4, [2.38, 1.62, -9.66], face, 66)
    sc.key(12.2, [0.42, 1.38, -9.55], [2.35, 0.75, -9.05], 58)
    sc.key(14.8, [0.36, 1.36, -9.52], [2.35, 0.72, -9.1], 56)

    # ---- the street, then the post
    T_ST = 14.8
    sc.key(T_ST, [-36.0, 9.0, 12.0], [2.0, 3.0, -4.0], 72, cut=True)
    sc.key(20.0, [-28.5, 6.4, 10.0], [0.5, 2.4, -3.0], 64)
    cat = [-7.5, 1.0, 4.5]
    sc.actor('cat', 'minecraft:cat', path=[{'t': 0, 'pos': cat}],
             look=[{'t': 0, 'yaw': 160}, {'t': tk(17.5), 'yaw': 160}, {'t': tk(18.3), 'yaw': 205},
                   {'t': tk(30.0), 'yaw': 205}, {'t': tk(31.0), 'yaw': 175}])
    T_POST = 20.6
    sc.key(T_POST - 0.6, [0.16, 0.34, -5.45], [0.5, 0.55, -2.3], 56, cut=True)
    sc.key(23.6, [0.2, 0.31, -5.2], [0.5, 0.42, -2.6], 54)
    rnd = random.Random('first post')
    T_PICK = 24.5
    post_through(sc, 'bill', T_POST + 0.1, [0.32, -2.78], 'minecraft:white_carpet', rnd, gone=35.0)
    post_through(sc, 'postcard', T_POST + 0.45, [0.7, -3.12], 'minecraft:light_blue_carpet', rnd, gone=35.0)
    post_through(sc, 'first_letter', T_POST + 0.85, [0.52, -3.42], 'letter', rnd, gone=T_PICK, spin=12)
    for t in (T_POST, T_POST + 0.36, T_POST + 0.78):
        sc.sfx(t, 'slot', 0.9)

    # ---- the boy: up the hall, picks the letter up, reads it, takes it to the kitchen
    bm = Mover('boy', PARK_PRIVET, 180)
    B_MAT = [0.62, MAT_Y, -3.98]
    bm.jump(21.2, [0.62, 0, -8.9])
    bm.face(21.26, 0, 0.05)
    bm.walk(21.4, [[0.64, 0, -5.4], [0.62, MAT_Y, -4.4], B_MAT], 2.0)
    bl = Limbs()
    ba = []
    bl.bend(T_PICK - 0.5, 34, 0.45, head=16, arms={'right_arm': [-30, 0, 0], 'left_arm': [6, 0, 0]})
    bl.bend(T_PICK + 0.15, 0, 0.5, head=26, arms={'right_arm': [-48, 0, 6], 'left_arm': [-38, 0, -4]})
    ba.append(act(T_PICK, 'hold', item=INVITE))
    bm.face(25.4, 10, 0.4)
    T_ADDR = 24.9
    sc.narrate(T_ADDR, 'Mr. {viewer}, The Cupboard under the Stairs, 4 Privet Drive, Little Whinging, Surrey', 3.1)
    sc.key(T_PICK - 0.9, [0.28, 1.5, -4.75], [0.6, 0.55, -3.55], 52, cut=True)
    sc.key(28.0, [0.3, 1.46, -4.62], [0.62, 0.72, -3.6], 46)
    # into the kitchen, reading
    T_GO = 28.0
    BV = [-2.72, 0, -13.25]                                    # beside Vernon, at his right front
    bm.jump(T_GO - 0.06, [0.45, 0, -10.7])                    # on the cut: down the hall, unseen
    bm.face(T_GO - 0.01, 180, 0.01)
    bm.walk(T_GO, [[0.45, 0, -11.6], [-0.4, 0, -12.9], BV], 1.9)
    T_AT = bm.t
    assert T_AT < 31.6, T_AT
    bm.face(T_AT + 0.05, 160, 0.4)
    bl['head'].change(T_AT + 0.6, [8, 0, 0], 0.4)

    # ---- the kitchen: Vernon behind the paper; the snatch
    vs = PA['kitchen_seats']['vernon']
    VR = seat_root(vs, ADULT)
    vm = Mover('vernon', VR, vs[3])
    sc.legs.append(('hold', 'vernon', 15.0, VR, None, vs[3], ADULT, -90))
    sc.seated['vernon'] = [(0.0, 38.45)]
    vl = Limbs()
    vc = Clips()
    va = []
    vc.cue(15.0, 'READ_SCROLL', 6)
    T_NOTICE = 31.3
    vc.cue(T_NOTICE, 'IDLE', 6)
    vm.face(T_NOTICE + 0.1, 72, 0.5)                          # twists round toward the boy
    vl['head'].change(T_NOTICE + 0.2, [-4, -38, 0], 0.4)
    T_LINE = 31.5
    secs = sc.say(T_LINE, 'letter_vernon_mine', 35.0)
    T_SNATCH = T_LINE + 1.95
    vl['left_arm'].change(T_SNATCH - 0.35, [-82, 22, 0], 0.25)
    vl['left_arm'].change(T_SNATCH + 0.25, [-40, 10, 0], 0.4)
    va.append(act(T_SNATCH, 'equip', slot='offhand', item=INVITE))
    ba.append(act(T_SNATCH, 'equip', slot='mainhand', item='minecraft:air'))
    bl['right_arm'].change(T_SNATCH - 0.1, [-12, 0, 0], 0.25)
    bl['head'].change(T_SNATCH, [-6, 0, 0], 0.2)
    vc.span(T_LINE, T_LINE + secs, 'TALK', 4, 8)
    vl['head'].change(T_LINE + secs + 0.2, [6, -10, 0], 0.6)
    sc.key(28.0, [1.5, 2.3, -12.4], [-4.5, 0.9, -14.7], 66, cut=True)
    sc.key(31.0, [1.3, 2.2, -12.6], [-2.6, 0.9, -14.0], 62)
    sc.key(31.0, [-4.5, 1.62, -14.55], [-2.7, 1.12, -13.85], 52, cut=True)
    sc.key(35.0, [-4.4, 1.6, -14.5], [-2.75, 1.1, -13.8], 48)

    # ---- more letters: three, eight, twenty
    T3 = 35.6
    rnd = random.Random('more post')
    sc.key(35.0, [0.86, 0.4, -5.05], [0.45, 0.4, -2.7], 56, cut=True)
    sc.key(38.6, [0.8, 0.38, -4.9], [0.45, 0.35, -2.8], 54)
    for i in range(3):
        sc.sfx(T3 + 0.42 * i - 0.05, 'slot', 0.85)
        post_through(sc, 'three_%d' % i, T3 + 0.42 * i, mat_spot(rnd, i, 3), 'letter', rnd, gone=38.6)
    T8 = 39.0
    for i in range(8):
        if i % 3 == 0:
            sc.sfx(T8 + 0.2 * i - 0.05, 'slot', 0.8)
        post_through(sc, 'eight_%d' % i, T8 + 0.2 * i, mat_spot(rnd, i, 8), 'letter', rnd, gone=46.4)
    # the boy runs for them; Vernon steps out of the living room into his way
    bm.jump(38.5, [0.62, 0, -9.2])
    bm.face(38.56, 0, 0.05)
    bm.walk(38.9, [[0.6, 0, -7.55]], 2.6)
    bm.face(bm.t + 0.05, 20, 0.3)
    bm.walk(41.2, [[0.62, 0, -8.9]], 1.2)                     # backs off
    bm.face(bm.t, 10, 0.3)
    bl['head'].change(38.7, [-4, 0, 0], 0.3)
    vm.jump(38.5, [-3.2, 0, -5.5])
    va.append(act(38.45, 'equip', slot='mainhand', item='minecraft:air'))
    va.append(act(38.45, 'equip', slot='offhand', item='minecraft:air'))
    vm.face(38.56, -90, 0.05)
    vm.walk(39.6, [[-1.5, 0, -5.5], [-0.8, 0, -5.5], [-0.05, 0, -6.35]], 2.4)
    vm.face(vm.t + 0.05, 165, 0.3)
    vl['right_arm'].change(39.9, [-82, 0, -8], 0.3)          # arm out across the hall
    vl['right_arm'].change(41.6, [-30, 0, 0], 0.5)
    vl['head'].change(39.9, [4, 0, 0], 0.4)
    sc.key(38.6, [-0.42, 1.55, -10.7], [0.4, 0.45, -3.6], 62, cut=True)
    sc.key(42.4, [-0.38, 1.52, -10.4], [0.4, 0.4, -3.7], 60)
    T20 = 42.8
    for i in range(20):
        if i % 4 == 0:
            sc.sfx(T20 + 0.125 * i - 0.03, 'slot', 0.75)
        post_through(sc, 'twenty_%02d' % i, T20 + 0.125 * i, mat_spot(rnd, i, 20), 'letter', rnd, gone=56.0)
    sc.key(42.4, [0.74, 0.27, -3.78], [0.5, 0.86, -2.2], 64, cut=True)
    sc.key(46.4, [0.76, 0.26, -3.82], [0.5, 0.82, -2.2], 62)
    # Vernon nails the slot shut
    VD = [0.5, MAT_Y, -4.45]
    vm.jump(46.3, VD)
    vm.face(46.36, 0, 0.05)
    T_NAIL = 46.6
    secs = sc.say(T_NAIL, 'letter_vernon_nomail', 51.0)
    vc.span(T_NAIL, T_NAIL + 1.3, 'TALK', 4, 6)
    boards = []
    for bi, (tb, yb) in enumerate(((47.3, 1.03), (48.9, 0.73))):
        vl['right_arm'].change(tb - 0.5, [-88, 0, -8], 0.3)
        vl['left_arm'].change(tb - 0.5, [-88, 0, 8], 0.3)
        for j, xb in enumerate((0.2, 0.5, 0.8)):
            aid = 'board_%d_%d' % (bi, j)
            s = 0.34
            zf = round(plate_centre_inner(SLOT_IN[2], s, proud=0.035), 5)
            hold = [xb, yb - 0.02, -3.9]
            keys = [(tb - 0.5, hold, 'linear'), (tb, [xb, yb, zf], 'out')]
            sc.actor(aid, 'hogwartsmagic:memory_block', block='minecraft:spruce_pressure_plate', scale=s,
                     path=block_path(sc, aid, keys, hide_before=tb - 0.5, park=PARK_PRIVET),
                     look=[{'t': 0, 'yaw': 0, 'pitch': 90}])
            boards.append(aid)
        vl['left_arm'].change(tb + 0.1, [-80, 0, 10], 0.2)    # holds it with the left
        for h in HAMMER_HITS:
            th = tb + 0.4 + h
            vl['right_arm'].change(th - 0.16, [-150, 0, -6], 0.12)
            vl['right_arm'].change(th, [-92, 0, -6], 0.08)
            sc.sound(th, 'minecraft:block.wood.hit', at=[0.5, yb, -2.3], volume=0.7, pitch=1.1)
        sc.sfx(tb + 0.4, 'hammer', 0.95)
    vl['right_arm'].change(50.6, [0, 0, 0], 0.5)
    vl['left_arm'].change(50.6, [0, 0, 0], 0.5)
    bm.jump(46.3, [0.6, 0, -9.3])
    sc.key(46.4, [-0.55, 1.78, -6.15], [0.5, 0.95, -2.3], 52, cut=True)
    sc.key(51.0, [-0.5, 1.74, -5.9], [0.5, 0.92, -2.3], 48)
    sc.actor('vernon', skin=SKIN['vernon'], mover=vm, life=vl, clips=vc, actions=va,
             poses=[pose(0, 'seated', 1), pose(38.4, 'standing', 1)], items={'mainhand': 'minecraft:paper'})
    sc.unseen['vernon'] = [(38.4, 38.7), (46.2, 46.5)]
    sc.actor('boy', skin=SKIN['boy'], scale=KID, mover=bm, life=bl, actions=ba)

    # ---- the owls
    T_OWL = 51.0
    sc.key(T_OWL, [0.5, 2.5, 17.5], [0.5, 3.6, -4.0], 72, cut=True)
    sc.key(LEN, [0.5, 2.9, 13.2], [0.5, 3.4, -4.0], 62, ease='in')
    sc.sfx(T_OWL + 0.2, 'owls', 0.9)
    rnd = random.Random('owls')
    look_at = PA['owl_look_at']
    for i, p in enumerate(PA['roof_owl_spots']):
        y0 = rnd.uniform(-180, 180)
        yt = yaw_to(look_at[0] - p[0], look_at[2] - p[2])
        while yt - y0 > 180:
            yt -= 360
        while yt - y0 < -180:
            yt += 360
        tt = T_OWL + 0.5 + rnd.uniform(0, 2.6)
        pitch = clamp(math.degrees(math.atan2(p[1] - look_at[1], math.hypot(look_at[0] - p[0], look_at[2] - p[2]))),
                      0, 40)
        sc.actor('owl_%02d' % i, 'hogwartsmagic:owl', scale=round(rnd.uniform(0.8, 0.95), 2),
                 path=[{'t': 0, 'pos': p}],
                 look=[{'t': 0, 'yaw': round(y0, 1)}, {'t': tk(tt), 'yaw': round(y0, 1)},
                       {'t': tk(tt + 0.35), 'yaw': round(yt, 1), 'pitch': round(pitch, 1)}])
        if i % 9 == 0:
            sc.sound(tt + 0.2, 'hogwartsmagic:owl.ambient', at=p, volume=0.8, pitch=rnd.uniform(0.9, 1.1))

    scene = sc.scene(SET_PRIVET, PD.RADIUS, nxt='letter_flood')
    evs = scene['events']
    assert evs[0]['id'] == 'eyes_open' and evs[0]['t'] == 0

    def extra(problems):
        # the pull-out leaves the cupboard only after the door has swung clear
        for t in range(0, sc.L + 1):
            p = sample_cam(scene['camera'], t)
            if 1.0 <= p[0] <= 2.0 and -10.0 <= p[2] <= -9.0 and t < tk(T_OPEN + 0.6):
                problems.append('camera in the cupboard door cell before it opens (t %d)' % t)
                break
        # the swung door clears the camera's way out: the panel runs from the hinge along R(yaw)(0, -1)
        c0 = door['lower']
        hx, hz = c0[0] + 0.5 + hrel[0], c0[2] + 0.5 + hrel[1]
        y = math.radians(swing[-1][1])
        for f in (0.0, 0.5, 1.0):
            px, pz = hx + f * math.sin(y), hz - f * math.cos(y)
            for t in range(tk(T_OPEN + 0.7), tk(14.8)):
                cp = sample_cam(scene['camera'], t)
                if math.hypot(cp[0] - px, cp[2] - pz) < 0.3 and cp[1] < 2.2:
                    problems.append('camera within 0.3 of the open cupboard door at t %d' % t)
                    break
    return sc, scene, extra


# ================================================================ letter_flood: Sunday
FLOOD_BOX = PA['living_room_box']
T_BURST = 10.4


def in_box(p, box, margin=0.3):
    return all(box[0][i] + margin <= p[i] <= box[1][i] - margin for i in (0, 2)) and \
        box[0][1] <= p[1] <= box[1][1] - margin


def flood_scene():
    LEN = 30.0
    w = privet_world()
    sc = Scene('letter_flood', 'No Post on Sundays', LEN, w)
    sc.poses = {
        'seated': SEATED, 'standing': {},
        'seated_cower': dict(SEATED, head=[28, 0, 0], right_arm=[-122, 0, -18], left_arm=[-122, 0, 18]),
        'reach_r': {'right_arm': [-172, 0, -6], 'left_arm': [-42, 0, 32]},
        'reach_l': {'left_arm': [-170, 0, 6], 'right_arm': [-40, 0, -30]},
        'catch': {'right_arm': [-166, 0, -4], 'left_arm': [-34, 0, 22]},
        'clutch': {'right_arm': [-64, 0, -20], 'left_arm': [-50, 0, 24], 'head': [24, 0, 0]},
    }
    sc.ev(0, 'effect', 'sky', at=[0, 0, 0], style='overcast')
    sc.music('letter_flood')
    plate_actors(sc)
    # the boards stay over the slot (the house remembers last week)
    for bi, yb in enumerate((1.03, 0.73)):
        for j, xb in enumerate((0.2, 0.5, 0.8)):
            s = 0.34
            sc.actor('board_%d_%d' % (bi, j), 'hogwartsmagic:memory_block', block='minecraft:spruce_pressure_plate',
                     scale=s, path=[{'t': 0, 'pos': [xb, yb, round(plate_centre_inner(SLOT_IN[2], s, 0.035), 5)]}],
                     look=[{'t': 0, 'yaw': 0, 'pitch': 90}])

    # ---- Vernon in his armchair, smug; up and shouting when the letters come
    A_ = PA['armchair']
    VR = seat_root(A_, ADULT)
    VS = stand_spot(A_, ADULT)
    T_STAND = 12.5
    vm = Mover('vernon', VR, A_[3])
    vm.hold(T_STAND)
    vm.to(T_STAND + 1.0, VS, 'linear', face=False)
    sc.legs.append(('hold', 'vernon', 0.0, VR, None, A_[3], ADULT, -90))
    sc.legs.append(('stand', 'vernon', T_STAND, VR, VS, A_[3], ADULT, -90))
    sc.seated['vernon'] = [(0.0, T_STAND + 1.05)]
    vl = Limbs()
    vc = Clips()
    T_SUN = 1.2
    secs = sc.say(T_SUN, 'letter_vernon_sunday', 5.6)
    vc.span(T_SUN, T_SUN + secs, 'TALK', 5, 8)
    vl['head'].change(T_SUN + 0.3, [-4, 44, 0], 0.6)        # a gloat at the boy by the wall
    vl['head'].change(T_SUN + secs + 0.1, [14, 0, 0], 0.35)  # the nod
    vl['head'].change(T_SUN + secs + 0.6, [-6, 0, 0], 0.4)
    vl['head'].change(7.4, [-2, -6, 0], 0.3)                 # the rumble: the smile goes
    vl['body'].change(9.6, [-6, 0, 0], 0.6)
    vl['body'].change(T_STAND - 0.2, [0, 0, 0], 0.3)
    vc.cue(T_STAND, 'STAND_UP', 2)
    vc.cue(T_STAND + 1.1, 'IDLE', 4)
    T_OUT = 14.0
    secs = sc.say(T_OUT, 'letter_vernon_shout', 17.8)
    vm.walk(T_STAND + 1.25, [[-3.62, 0, -6.72]], 1.6)
    rnd = random.Random('vernon flails')
    t = T_OUT
    while t < 25.2:                                           # swatting at letters
        side = 'right_arm' if rnd.random() < 0.6 else 'left_arm'
        sg = -1 if side == 'right_arm' else 1
        vl[side].change(t, [rnd.uniform(-160, -120), 0, sg * rnd.uniform(5, 25)], 0.18)
        vl[side].change(t + 0.25, [rnd.uniform(-60, -20), 0, sg * 10], 0.25)
        t += rnd.uniform(0.55, 0.9)
    vl['right_arm'].change(25.6, [0, 0, 0], 0.5)
    vl['left_arm'].change(25.6, [0, 0, 0], 0.5)
    for t, y in ((15.6, 120), (17.0, 70), (18.8, 135), (20.4, 95), (22.0, 140), (23.6, 110)):
        vm.face(t, y, 0.4)
    vm.walk(21.2, [[-3.42, 0, -6.3]], 0.8)
    sc.actor('vernon', skin=SKIN['vernon'], mover=vm, life=vl, clips=vc,
             poses=[pose(0, 'seated', 1), pose(T_STAND + 1.15, 'standing', 1)])

    # ---- Petunia and Dudley on the sofa
    for aid, seat, scale, t_c in (('petunia', PA['sofa_seats'][1], ADULT, 10.9), ('dudley', PA['sofa_seats'][0], KID, 11.1)):
        R = seat_root(seat, scale)
        l = Limbs()
        l['head'].change(7.3 + (0.2 if aid == 'dudley' else 0), [0, 48, 0], 0.35)
        l['head'].change(t_c, [0, 0, 0], 0.3)
        sc.actor(aid, skin=SKIN[aid], scale=scale, path=[{'t': 0, 'pos': R}], look=[{'t': 0, 'yaw': seat[3]}],
                 life=l, poses=[pose(0, 'seated', 1), pose(t_c, 'seated_cower', 4)])
        sc.legs.append(('hold', aid, 0.0, R, None, seat[3], scale, -90))
        sc.seated[aid] = [(0.0, LEN)]
    sc.sound(10.9, 'minecraft:entity.villager.hurt', at=[-5.5, 1.3, -10.5], volume=0.5, pitch=1.5)

    # ---- the boy: against the wall, then in among the letters, jumping and grabbing
    bw = PA['living_spots']['boy_wall']
    bm = Mover('boy', bw['pos'], bw['yaw'])
    bl = Limbs()
    bl['head'].change(7.4, [-6, 0, 0], 0.3)
    poses_b = [pose(13.0, 'arms_out', 4)]
    T_RUN = 13.0
    spots = [(14.3, [-4.7, 0, -9.0]), (15.6, [-5.3, 0, -8.4]), (16.9, [-6.1, 0, -8.9]), (18.2, [-6.6, 0, -8.0]),
             (19.6, [-5.9, 0, -7.3]), (21.0, [-5.2, 0, -8.05]), (22.4, [-5.8, 0, -8.7]), (23.8, [-5.45, 0, -7.9])]
    bm.walk(T_RUN, [spots[0][1]], 2.8)
    rnd = random.Random('boy jumps')
    heights = (0.5, 0.42, 0.56, 0.46, 0.52, 0.4, 0.55, 0.48)
    for i, (tj, p) in enumerate(spots):
        if i:
            bm.to(tj - 0.06, p, 'linear')
        bm.hold(tj)
        top = [p[0] + rnd.uniform(-0.08, 0.08), heights[i], p[2] + rnd.uniform(-0.08, 0.08)]
        bm.to(tj + 0.24, top, 'out', face=False)
        bm.to(tj + 0.5, [top[0], 0, top[2]], 'in', face=False)
        bm.face(tj - 0.2, rnd.choice((-150, -110, -60, 30, 60, 120, 160)) if i < 7 else -30, 0.25)
        poses_b.append(pose(tj - 0.12, 'reach_r' if i % 3 != 1 else 'reach_l', 3))
        poses_b.append(pose(tj + 0.55, 'arms_out', 5))
        bl['head'].change(tj - 0.15, [-32, 0, 0], 0.15)
        bl['head'].change(tj + 0.5, [-14, 0, 0], 0.3)
    END = bm.pos
    T_CATCH = 26.9
    bm.face(24.6, -30, 0.5)
    poses_b.append(pose(25.2, 'catch', 8))
    bl['head'].change(25.2, [-40, 0, 0], 0.6)
    bl['head'].change(T_CATCH + 0.3, [10, 0, 0], 0.6)
    poses_b.append(pose(T_CATCH + 0.35, 'clutch', 9))
    ba = [act(T_CATCH, 'hold', item=INVITE)]
    sc.actor('boy', skin=SKIN['boy'], scale=KID, mover=bm, life=bl, poses=poses_b, actions=ba)

    # ---- the hero letter, spiralling down into his hand
    hand = arm_tip(END, -30, KID, 'right_arm', sc.poses['catch']['right_arm'])
    hl = []
    T0 = 25.2
    n = 17
    for i in range(n + 1):
        f = i / n
        ang = 2 * math.pi * 1.6 * f + 0.7
        r = 1.15 * (1 - f) ** 1.2
        p = [hand[0] + r * math.cos(ang), hand[1] + 0.95 * (1 - f) ** 1.5, hand[2] + r * math.sin(ang)]
        hl.append((T0 + f * (T_CATCH - T0), p, 'linear'))
    path = block_path(sc, 'hero_letter', hl, hide_before=T0, hide_after=T_CATCH, park=PARK_PRIVET)
    look = [{'t': 0, 'yaw': 0, 'pitch': 40}]
    for i in range(0, n + 1, 2):
        t, p, _ = hl[i]
        look.append({'t': tk(t), 'yaw': round(30 + 400 * i / n, 1), 'pitch': round(40 + 30 * math.sin(i), 1)})
    sc.actor('hero_letter', 'hogwartsmagic:memory_letter', path=path, look=look)

    # ---- the chimney, the burst
    mouth = PA['living_fireplace_mouth']
    sc.sfx(6.8, 'chimney_rumble', 1.0)
    for t, c in ((7.1, 6), (8.3, 10), (9.4, 14), (10.15, 22)):
        sc.particles(t, 'minecraft:large_smoke', [mouth[0] - 0.4, 0.7, mouth[2]], c, [0.25, 0.3, 0.3])
        sc.particles(t + 0.1, 'minecraft:ash', [mouth[0] - 0.2, 1.4, mouth[2]], c * 2, [0.4, 0.6, 0.5])
    sc.ev(T_BURST, 'effect', 'letter_flood', at=[mouth[0], mouth[1], mouth[2]], yaw=PA['living_fireplace_yaw'],
          count=300, duration=120, box=[list(FLOOD_BOX[0]), list(FLOOD_BOX[1])])
    sc.sfx(T_BURST - 0.05, 'whoosh', 1.0)
    sc.sfx(T_BURST + 1.6, 'whoosh', 0.75)
    sc.sfx(T_BURST + 3.4, 'whoosh', 0.55)
    sc.particles(T_BURST, 'minecraft:large_smoke', [mouth[0] + 0.3, 0.8, mouth[2]], 30, [0.4, 0.4, 0.5])

    # ---- camera
    sc.key(0, [-6.5, 1.7, -3.3], [-3.6, 0.95, -8.4], 72)
    sc.key(5.4, [-6.1, 1.66, -3.55], [-3.4, 0.95, -8.5], 70)
    sc.key(5.4, [-5.6, 1.35, -7.05], 'actor:vernon', 48, cut=True)
    sc.key(T_BURST - 0.1, [-5.3, 1.32, -7.2], 'actor:vernon', 44)
    sc.key(T_BURST - 0.1, [-6.6, 0.55, -6.0], [-9.0, 0.7, -7.5], 74, cut=True)
    sc.key(13.4, [-6.3, 0.6, -5.75], [-8.6, 0.85, -7.5], 76)
    sc.key(13.4, [-3.35, 2.55, -10.62], [-6.0, 0.9, -6.6], 80, cut=True)
    sc.key(17.6, [-3.5, 2.5, -10.55], [-6.2, 0.8, -6.9], 78)
    sc.key(17.6, [-3.6, 0.75, -9.7], 'actor:boy', 62, cut=True)
    sc.key(21.6, [-3.5, 0.8, -8.9], 'actor:boy', 58)
    sc.key(21.6, [-1.4, 1.6, -5.5], [-5.6, 1.0, -8.2], 66, cut=True)
    sc.key(25.4, [-1.6, 1.55, -5.6], [-5.5, 1.0, -8.1], 64)
    cam0 = ahead(END, -30, 1.75, side=-0.35, y=1.05)
    cam1 = ahead(END, -30, 1.05, side=-0.2, y=1.25)
    sc.key(25.4, cam0, [hand[0], hand[1] - 0.05, hand[2]], 52, cut=True)
    sc.key(LEN, cam1, [hand[0], hand[1] - 0.12, hand[2]], 38, ease='in')

    scene = sc.scene(SET_PRIVET, PD.RADIUS, nxt='letter_crossing')

    def extra(problems):
        fl = [e for e in scene['events'] if e['id'] == 'letter_flood']
        assert len(fl) == 1 and fl[0]['box'] == [list(FLOOD_BOX[0]), list(FLOOD_BOX[1])]
        b = FLOOD_BOX
        for x in range(math.floor(b[0][0]), math.ceil(b[1][0])):
            for y in range(math.floor(b[0][1]), math.ceil(b[1][1])):
                for z in range(math.floor(b[0][2]), math.ceil(b[1][2])):
                    if w.get((x, y, z)) not in (None, 'minecraft:air'):
                        problems.append('flood box cell %s is %s' % ((x, y, z), w.get((x, y, z))))
        for i in range(0, len(hl)):
            if not in_box(hl[i][1], b, 0.3):
                problems.append('hero letter leaves the room at %s' % [r3(c) for c in hl[i][1]])
                break
    return sc, scene, extra


# ================================================================ letter_crossing: the storm at sea
SEA_Y = IA['sea_center'][1]
CREST = 0.6                                  # RoughSea: at most 0.6 from the still surface at intensity 1
SEATS_BOAT = {'oars': (0.0, 0.15, 180), 'stern_left': (0.42, -1.38, 0), 'stern_right': (-0.38, -1.38, 0),
              'bow': (0.0, 1.3, 0)}
OAR_PERIOD = 52
BOAT_SEAT_TOP = 0.12


def seat_offset(name, scale):
    x, z, _ = SEATS_BOAT[name]
    return [x, round(HM.seated_root_y(BOAT_SEAT_TOP, scale), 4), z]


def polyline_at(pts, s):
    """Point and heading yaw at arc length s along pts."""
    for a, b in zip(pts, pts[1:]):
        d = math.dist([a[0], a[2]], [b[0], b[2]])
        if s <= d or b is pts[-1]:
            f = clamp(s / d, 0, 1)
            return [a[0] + (b[0] - a[0]) * f, a[1], a[2] + (b[2] - a[2]) * f], yaw_to(b[0] - a[0], b[2] - a[2])
        s -= d
    return list(pts[-1]), yaw_to(pts[-1][0] - pts[-2][0], pts[-1][2] - pts[-2][2])


def rower_limbs(length_ticks):
    arms, body = [], []
    t = 0
    while t <= length_ticks:
        s = (t % OAR_PERIOD) / OAR_PERIOD
        reach = 0.5 + 0.5 * math.cos(2 * math.pi * s)
        arms.append({'t': t, 'v': [round(-38 - 42 * reach, 1), 0, 0], 'ease': 'linear'})
        body.append({'t': t, 'v': [round(-10 + 24 * reach, 1), 0, 0], 'ease': 'linear'})
        t += OAR_PERIOD // 8
    return {'right_arm': {'rot': arms, 'mode': 'set'}, 'left_arm': {'rot': [dict(k) for k in arms], 'mode': 'set'},
            'body': {'rot': body, 'mode': 'set'}}


class Boat:
    """The crossing's course: boat_path from its 4th point to the landing, at a steady pull easing in at the
    ledge; pos(t) and yaw(t) (the heading smoothed over a second)."""

    def __init__(self, t0, t1):
        self.pts = IA['boat_path'][3:]
        self.L = sum(math.dist([a[0], a[2]], [b[0], b[2]]) for a, b in zip(self.pts, self.pts[1:]))
        self.t0, self.t1 = t0, t1

    def s(self, t):
        f = clamp((t - self.t0) / (self.t1 - self.t0), 0, 1)
        # steady, then easing over the last 25%
        g = f / 0.75 * 0.85 if f < 0.75 else 0.85 + 0.15 * (1 - (1 - (f - 0.75) / 0.25) ** 2)
        return self.L * g

    def pos(self, t):
        return polyline_at(self.pts, self.s(t))[0]

    def yaw(self, t):
        ys = [polyline_at(self.pts, self.s(t + d))[1] for d in (-0.6, -0.3, 0.0, 0.3, 0.6)]
        base = ys[2]
        acc = 0.0
        for y in ys:
            while y - base > 180:
                y -= 360
            while y - base < -180:
                y += 360
            acc += y
        return acc / len(ys)

    def frame(self, t, fwd=0.0, side=0.0, up=0.0):
        """A point fixed to the still-water boat frame: fwd toward the bow, side to the port (left) side."""
        p = self.pos(t)
        f = fwd_vec(self.yaw(t))
        lx, lz = math.cos(math.radians(self.yaw(t))), math.sin(math.radians(self.yaw(t)))   # port (left)
        return [p[0] + f[0] * fwd + lx * side, p[1] + up, p[2] + f[1] * fwd + lz * side]


def crossing_scene():
    LEN = 17.0
    w = island_world()
    sc = Scene('letter_crossing', 'The Crossing', LEN, w)
    sc.over_sea = (SEA_Y, CREST, (-79, 79, -79, 79))
    sc.poses = {
        'boat_seat': {'right_leg': [-65, 8, 0], 'left_leg': [-65, -8, 0]},
        'boat_clutch': {'right_leg': [-65, 8, 0], 'left_leg': [-65, -8, 0], 'right_arm': [-52, -24, -34],
                        'left_arm': [-38, 0, 12], 'head': [14, 0, 0]},
        'boat_huddle': {'right_leg': [-65, 8, 0], 'left_leg': [-65, -8, 0], 'right_arm': [-62, 0, -14],
                        'left_arm': [-62, 0, 14], 'head': [24, 0, 0]},
        'boat_grip': {'right_leg': [-65, 8, 0], 'left_leg': [-65, -8, 0], 'right_arm': [-26, 0, 22],
                      'left_arm': [-26, 0, -22], 'head': [-6, 0, 0]},
    }
    T_LAND = 16.4
    boat = Boat(0.0, T_LAND)
    bp, bl_ = [], []
    t = 0.0
    while t <= LEN + 1e-9:
        p = boat.pos(t)
        bp.append({'t': tk(t), 'pos': [r3(p[0]), r3(SEA_Y), r3(p[2])], 'ease': 'linear'})
        bl_.append({'t': tk(t), 'yaw': round(boat.yaw(t), 1), 'ease': 'linear'})
        t += 0.5
    sc.actor('boat', 'hogwartsmagic:memory_rowboat', path=bp, look=bl_)
    riders = (('vernon', 'oars', ADULT, 'boat_seat'), ('petunia', 'stern_left', ADULT, 'boat_clutch'),
              ('dudley', 'stern_right', KID, 'boat_huddle'), ('boy', 'bow', KID, 'boat_grip'))
    for aid, seat, scale, pz in riders:
        limbs = rower_limbs(sc.L) if aid == 'vernon' else None
        l = Limbs()
        if aid == 'boy':
            for t, v in ((2.0, [-8, -25, 0]), (5.5, [-4, 20, 0]), (9.6, [-14, 0, 0]), (12.6, [-10, -10, 0])):
                l['head'].change(t, v, 0.6)
        if aid == 'petunia':
            l['head'].change(9.7, [6, -30, 0], 0.3)
            l['head'].change(11.0, [14, 0, 0], 0.6)
        sc.actor(aid, skin=SKIN[aid], scale=scale, ride='boat', path=[{'t': 0, 'pos': seat_offset(seat, scale)}],
                 look=[{'t': 0, 'yaw': SEATS_BOAT[seat][2]}], poses=[pose(0, pz, 1)], limbs=limbs,
                 life=l if aid != 'vernon' else None)

    # ---- the storm
    sea = IA
    sc.ev(0, 'effect', 'rough_sea', at=list(sea['sea_center']), spread=list(sea['sea_spread']), intensity=1.0)
    sc.ev(0, 'effect', 'rain', at=list(sea['rain']['at']), spread=list(sea['rain']['spread']), intensity=1.35)
    sc.sfx(0.0, 'storm', 1.0)
    for t in (3.6, 8.2, 12.4):
        sc.sfx(t, 'waves', 0.85)
    for t, at, thunder in ((1.3, [-12, 0, -30], 1.8), (9.5, [34, 0, 14], 9.9), (14.0, [-24, 0, 2], 14.4)):
        sc.ev(t, 'effect', 'lightning', at=at)
        sc.sfx(thunder, 'thunder', 0.9)
    sc.music('letter_crossing')
    # dim storm light along the course so the boat and its people read between the flashes
    fills = []
    for i in range(0, 9):
        p = boat.pos(T_LAND * i / 8)
        c = [int(math.floor(p[0])), int(SEA_Y) + 5, int(math.floor(p[2]))]
        if w.get(tuple(c)) in (None, 'minecraft:air'):
            fills.append({'block': 'minecraft:light', 'from': c, 'to': c})
    # ---- camera
    sc.key(0, [31.5, 4.0, 58.0], 'actor:boat', 66)
    sc.key(4.4, [28.0, 2.4, 53.0], 'actor:boat', 60)
    t = 4.4
    first = True
    while t <= 8.4 + 1e-9:
        p = boat.frame(t, fwd=-0.6, side=-3.4, up=1.95)
        la = boat.frame(t + 0.15, fwd=0.3, side=0.0, up=0.7)
        sc.key(t, p, la, 58, cut=first, ease=None if first else 'linear')
        first = False
        t += 0.4
    t = 8.4
    first = True
    while t <= 12.0 + 1e-9:
        p = boat.frame(t, fwd=-3.3, side=0.25, up=1.85)
        la = boat.frame(t + 0.1, fwd=0.6, side=0.0, up=1.05)
        sc.key(t, p, la, 54, cut=first, ease=None if first else 'linear')
        first = False
        t += 0.4
    sc.key(12.0, [9.4, -5.4, 15.6], 'actor:boat', 60, cut=True)
    sc.key(LEN, [8.7, -5.5, 16.5], 'actor:boat', 52)
    set_ = dict(SET_ISLAND, fills=fills)
    scene = sc.scene(set_, LI.RADIUS, nxt='letter_hut')

    def extra(problems):
        for a in scene['actors']:
            if a.get('ride'):
                assert a['ride'] == 'boat'
        c, sp = IA['sea_center'], IA['sea_spread']
        for t in range(0, sc.L + 1, 5):
            p = boat.pos(t / 20.0)
            if abs(p[0] - c[0]) > sp[0] - 6 or abs(p[2] - c[2]) > sp[2] - 6:
                problems.append('the boat leaves the rough_sea area at t %d' % t)
    return sc, scene, extra


# ================================================================ letter_hut: Hagrid


ITEM_ARM_X = -18.0      # vanilla ArmPose.ITEM: an arm holding something is raised pi/10 (xRot * 0.5 - pi/10 at rest)


def hagrid_hand(root, yaw, arm_add, crouch=True, holding=True):
    """Hagrid's right hand (scale 1.5) as PlayerRenderer poses it: vanilla's held-item arm (-18 degrees), then the
    crouch (arms +0.4 rad, arm pivots down to y 5.2, the model lowered 2/16 of a block per scale), then the keyed
    arm_add (limbs run last, in add mode)."""
    x = arm_add[0] + (ITEM_ARM_X if holding else 0.0)
    if crouch:
        r = [root[0], root[1] - 2.0 / 16.0 * GIANT, root[2]]
        return arm_tip(r, yaw, GIANT, 'right_arm', [x + math.degrees(0.4), arm_add[1], arm_add[2]],
                       pivot_px=(-5, 5.2, 0))
    return arm_tip(root, yaw, GIANT, 'right_arm', [x, arm_add[1], arm_add[2]])


def solve_reach(root, yaw, scale, torso_a, target, seed=(-105.0, -25.0)):
    """The boy's right arm (absolute x and y rotation) that puts his hand tip nearest `target`: coordinate descent
    from seed over the rig's arm pivot for a torso pitched torso_a. Returns ((rx, ry), tip, distance)."""
    piv = torso_pose(torso_a)['right_arm'][1]
    rx, ry = seed
    tip = arm_tip(root, yaw, scale, 'right_arm', [rx, ry, 0], pivot_px=piv)
    d = math.dist(tip, target)
    step = 8.0
    while step > 0.05:
        moved = False
        for dx, dy in ((step, 0), (-step, 0), (0, step), (0, -step)):
            t2 = arm_tip(root, yaw, scale, 'right_arm', [rx + dx, ry + dy, 0], pivot_px=piv)
            d2 = math.dist(t2, target)
            if d2 < d - 1e-9:
                rx, ry, tip, d, moved = rx + dx, ry + dy, t2, d2, True
        if not moved:
            step /= 2
    return (round(rx, 2), round(ry, 2)), tip, d


def view_angle(cam, look, p):
    """Degrees between the camera's axis (cam -> look) and the ray cam -> p."""
    u = [look[i] - cam[i] for i in range(3)]
    v = [p[i] - cam[i] for i in range(3)]
    c = sum(u[i] * v[i] for i in range(3)) / (math.sqrt(sum(x * x for x in u)) * math.sqrt(sum(x * x for x in v)))
    return math.degrees(math.acos(max(-1.0, min(1.0, c))))


def hut_scene():
    LEN = 60.0
    w = island_world()
    sc = Scene('letter_hut', 'Hagrid', LEN, w)
    sc.over_sea = (SEA_Y, CREST, (-79, 79, -79, 79))
    sc.poses = {'seated': SEATED, 'standing': {},
                'aim': {'right_arm': [-88, -12, 0], 'left_arm': [-86, 32, 0], 'head': [4, 0, 0]},
                'cower': {'right_arm': [-118, 0, -16], 'left_arm': [-104, 0, 20], 'head': [22, 0, 0],
                          'body': [8, 0, 0]}}
    sea = IA
    sc.ev(0, 'effect', 'rough_sea', at=list(sea['sea_center']), spread=list(sea['sea_spread']), intensity=1.0)
    sc.ev(0, 'effect', 'rain', at=list(sea['rain']['at']), spread=list(sea['rain']['spread']), intensity=1.35)
    sc.music('letter_hut')
    sc.music('letter_wizard')
    sc.sfx(0.0, 'storm', 1.0)
    sc.sfx(19.6, 'storm', 0.5)
    sc.sfx(39.2, 'storm', 0.32)
    for t in (5.0, 13.6, 22.2, 30.8, 39.4, 48.0):
        sc.sfx(t, 'fire', 0.55)
    sc.sfx(1.8, 'waves', 0.8)
    for t, at, th, vol in ((0.9, [-20, 0, -18], 1.4, 1.0), (3.9, [22, 0, -24], 4.5, 0.8), (9.2, [30, 0, 30], 9.9, 0.4),
                           (24.0, [-14, 0, 26], 24.6, 0.5), (31.5, [36, 0, -6], 32.3, 0.35)):
        sc.ev(t, 'effect', 'lightning', at=at)
        sc.sfx(th, 'thunder', vol)

    # ---- the boat, moored at the landing, riding the swell
    bl_ = IA['boat_landing']
    sc.actor('boat', 'hogwartsmagic:memory_rowboat', path=[{'t': 0, 'pos': list(bl_)}],
             look=[{'t': 0, 'yaw': IA['boat_landing_yaw']}])

    # ---- the door: two leaves; they shudder at every blow, then break and fall flat into the hut
    T_P1, T_P2 = 12.0, 18.3
    T_BREAK = T_P2 + POUND_HITS[-1] + 0.25       # 20.63
    T_FLAT = T_BREAK + 0.48
    sc.sfx(T_P1, 'pound', 1.0)
    sc.sfx(T_P2, 'pound', 1.0)
    sc.sfx(T_FLAT - CRASH_IMPACT, 'door_crash', 1.0)
    hits = [T_P1 + h for h in POUND_HITS] + [T_P2 + h for h in POUND_HITS]
    for name, atype in (('hut_door_hinge', 'hogwartsmagic:memory_door'),
                        ('hut_door_hinge_mirrored', 'hogwartsmagic:memory_door_mirrored')):
        hg = IA[name]
        y0, ya = hg['yaw'], hg['ajar_inward_yaw']
        look = [{'t': 0, 'yaw': y0, 'pitch': 0}]
        for h in hits:
            look.append({'t': tk(h) + 1, 'yaw': round(y0 + (ya - y0) * 0.9, 1), 'pitch': 0, 'ease': 'out'})
            look.append({'t': tk(h) + 4, 'yaw': y0, 'pitch': 0, 'ease': 'in_out'})
        look.append({'t': tk(T_BREAK), 'yaw': y0, 'pitch': 0})
        look.append({'t': tk(T_BREAK) + 2, 'yaw': round(y0 + (ya - y0) * 1.4, 1), 'pitch': -4, 'ease': 'out'})
        look.append({'t': tk(T_FLAT), 'yaw': y0, 'pitch': hg['fallen_pitch'], 'ease': 'in'})
        look.append({'t': tk(T_FLAT) + 2, 'yaw': y0, 'pitch': hg['fallen_pitch'] + 3, 'ease': 'out'})
        look.append({'t': tk(T_FLAT) + 4, 'yaw': y0, 'pitch': hg['fallen_pitch'], 'ease': 'in'})
        sc.actor(name.replace('hut_door_hinge', 'door_leaf'), atype, scale=IA['hut_door_scale'],
                 path=[{'t': 0, 'pos': list(hg['pos'])}], look=look)
    fb = IA['hut_door_fallen_box']
    for k in range(3):
        sc.particles(T_FLAT + 0.05 * k, 'minecraft:poof', [0, 0.25, (fb[0][2] + fb[1][2]) / 2], 14,
                     [1.0, 0.15, 1.4])
    sc.particles(T_FLAT, 'minecraft:white_ash', [0, 0.6, 2.2], 30, [1.2, 0.5, 1.6])
    # lightning behind him in the doorway, and the porch lit by it
    sc.ev(T_BREAK + 0.08, 'effect', 'lightning', at=[3, 0, 22])
    sc.sfx(T_BREAK + 0.5, 'thunder', 1.0)
    for t0, t1 in ((T_BREAK + 0.1, T_BREAK + 0.32), (T_BREAK + 0.55, T_BREAK + 0.7), (23.95, 24.15)):
        sc.ev(t0, 'block', 'minecraft:light', at=[0, 1, 6])
        sc.ev(t1, 'block', 'minecraft:air', at=[0, 1, 6])

    # ---- Hagrid: pounding outside, then stooping through on the door's centre line
    ent = IA['hagrid_entry']
    out_p, in_p = IA['hagrid_outside']['pos'], IA['hagrid_inside']['pos']
    hm = Mover('hagrid', PARK_ISLAND, 180)
    hm.jump(10.0, out_p)
    hm.face(10.06, 180, 0.05)
    hl = Limbs()
    for h in hits:
        hl['right_arm'].change(h - 0.16, [-118, 0, 0], 0.1)
        hl['right_arm'].change(h + 0.01, [-92, 0, 0], 0.06)
    hl['right_arm'].change(T_BREAK + 0.1, [0, 0, 0], 0.3)
    T_STOOP, T_IN = T_BREAK + 0.12, T_FLAT + 0.28           # ducks as the door goes down, then in
    stoop, head_rel = ent['stoop_deg'], ent['head_deg']
    hl.bend(T_STOOP, stoop, 0.5, head=stoop + head_rel,
            arms={'right_arm': [0, 0, 0], 'left_arm': [0, 0, 0]})
    z_step = fb[1][2] + 0.1                                   # stepping up onto the fallen leaves
    v_in = 1.45
    t_step = T_IN + (out_p[2] - z_step) / v_in
    hm.hold(T_IN)
    hm.to(t_step, [0.0, 0.0, z_step], 'linear', face=False)
    hm.to(t_step + 0.12, [0.0, in_p[1], z_step - 0.18], 'linear', face=False)
    z_slow = in_p[2] + 0.4
    t_slow = t_step + 0.12 + (z_step - 0.18 - z_slow) / v_in
    hm.to(t_slow, [0.0, in_p[1], z_slow], 'linear', face=False)
    T_INSIDE = t_slow + 0.6
    hm.to(T_INSIDE, list(in_p), 'out', face=False)
    T_UP = T_INSIDE - 0.55
    hl.bend(T_UP, 0, 1.1, head=-10, arms={'right_arm': [0, 0, 0], 'left_arm': [0, 0, 0]})
    hl['head'].change(T_UP + 1.3, [-14, 0, 0], 0.5)          # up to his full height, looking round
    T_SORRY = 26.0
    hc = Clips()
    ha = []
    sc.say(T_SORRY, 'letter_hagrid_sorry', 28.4, hc)
    hl['head'].change(T_SORRY - 0.3, [34, 0, 0], 0.5)         # down at the door under his boots
    hl['head'].change(T_SORRY + 1.6, [4, 0, 0], 0.5)
    hm.face(T_SORRY + 1.8, 214, 0.6)                          # a look at the Dursleys
    # finds the boy. The hand-over is solved (offline, then fixed here and asserted below): Hagrid crouches 1.13
    # from the boy on the floor off the fallen leaves, his body turned 33 degrees so his right hand comes across
    # in front of the boy, who leans in 9 degrees and turns 19 toward it; his outstretched right hand then meets
    # Hagrid's within a few centimetres, 0.3 in front of his eyes.
    BOY = [-2.02, 0.5]                                        # the boy's hips (below)
    CROUCH = [-0.895, 0.035, 0.428]                         # half on the blanket (0.0625), half on the boards
    y_boy = yaw_to(BOY[0] - CROUCH[0], BOY[1] - CROUCH[2])   # Hagrid square to the boy
    Y_OFFER = y_boy - 33.0                                    # and turned into the offer
    BOY_YAW = y_boy - 180 - 19.0                              # the boy facing him, turned toward the letter
    LEAN, LOOK = 9.0, -3.0
    hm.face(29.4, 150, 0.7)
    hm.face(30.1, yaw_to(BOY[0] - in_p[0], BOY[1] - in_p[2]), 0.5)
    T_FOUND = 30.4
    secs = sc.say(T_FOUND, 'letter_hagrid_found', 34.6, hc)
    hl['head'].change(T_FOUND, [20, 0, 0], 0.6)
    hm.walk(31.4, [[-0.45, in_p[1], 1.0], [-0.62, 0.035, 0.7], CROUCH], 0.8)
    hm.face(hm.t + 0.05, y_boy, 0.4)
    T_CROUCH = 33.4
    ha.append(act(T_CROUCH, 'sneak'))
    hl['head'].change(T_CROUCH, [26, 0, 0], 0.6)
    T_WIZ = 35.9
    sc.say(T_WIZ, 'letter_hagrid_wizard', 38.0)
    hc.span(T_WIZ - 0.05, T_WIZ + 0.9, 'TALK', 3, 6)
    # the letter, from his coat
    T_COAT, T_HOLD, T_OFFER = 39.9, 40.5, 41.0
    ARM_OFFER = [-34.8, -14.0, 0]
    hl['right_arm'].change(T_COAT, [-70, 0, -40], 0.4)        # into the coat
    hl['right_arm'].change(T_HOLD, [-40, 0, -24], 0.3)
    hl['right_arm'].change(T_OFFER, ARM_OFFER, 0.6)
    hm.face(T_OFFER - 0.2, Y_OFFER, 0.7)
    ha.append(act(T_HOLD, 'hold', item=INVITE))
    T_LET = 41.6
    secs = sc.say(T_LET, 'letter_hagrid_letter', 45.6)
    T_TAKE = 49.6
    ha.append(act(T_TAKE, 'equip', slot='mainhand', item='minecraft:air'))
    hl['right_arm'].change(T_TAKE + 0.2, [-40, 0, 0], 0.8)
    hl['head'].change(T_TAKE + 0.4, [16, 0, 0], 0.8)
    sc.actor('hagrid', skin=SKIN['hagrid'], scale=GIANT, mover=hm, life=hl, clips=hc, actions=ha)
    sc.unseen['hagrid'] = [(0.0, 10.2)]
    HAND = hagrid_hand(CROUCH, Y_OFFER, ARM_OFFER)

    # ---- the boy on the floor by the fire: a rig (lying, then up on his elbows, sitting, reaching)
    k = kpx(KID)
    bf = IA['boy_floor']
    root = [BOY[0], round(lying_root_y(bf['surface_y'], KID), 4), BOY[1]]
    assert abs((root[0] - 20 * k) - bf['head'][0]) < 0.15, (root, bf)
    yaw_lie = -90.0
    yaw_door = -42.0
    T_JOLT = T_P1 + 0.05
    seq = [(0.0, torso_pose(-90, head=(0, 0, 18)), yaw_lie),             # awake, face toward the fire
           (9.5, torso_pose(-90, head=(0, 0, 18)), yaw_lie),
           (10.6, torso_pose(-90, head=(0, 0, 0)), yaw_lie),
           (T_JOLT, torso_pose(-90), yaw_lie),
           (T_JOLT + 0.35, torso_pose(-62, head=(-12, 0, 0), r_arm=(30, 0, 8), l_arm=(30, 0, -8)), yaw_lie),
           (T_JOLT + 1.4, torso_pose(-24, head=(-8, 0, 0), r_arm=(52, 0, 10), l_arm=(52, 0, -10)), yaw_lie),
           (14.6, torso_pose(-12, head=(-6, 0, 0), r_arm=(40, 0, 10), l_arm=(40, 0, -10)), yaw_lie),
           (15.8, torso_pose(-12, head=(-4, 0, 0), r_arm=(40, 0, 10), l_arm=(40, 0, -10)), yaw_door),
           (T_BREAK, torso_pose(-12, head=(-4, 0, 0), r_arm=(40, 0, 10), l_arm=(40, 0, -10)), yaw_door),
           (T_BREAK + 0.25, torso_pose(-26, head=(6, 0, 0), r_arm=(-100, 0, -10), l_arm=(-96, 0, 12)), yaw_door),
           (23.4, torso_pose(-20, head=(-26, 0, 0), r_arm=(-30, 0, 8), l_arm=(40, 0, -10)), yaw_door),
           (25.0, torso_pose(-14, head=(-30, 0, 0), r_arm=(40, 0, 10), l_arm=(40, 0, -10)), yaw_door),
           (31.0, torso_pose(-14, head=(-30, 0, 0), r_arm=(40, 0, 10), l_arm=(40, 0, -10)), yaw_door),
           (32.4, torso_pose(-8, head=(-32, 0, 0), r_arm=(30, 0, 8), l_arm=(30, 0, -8)), y_boy - 180),
           (36.6, torso_pose(-6, head=(-30, 0, 0), r_arm=(20, 0, 6), l_arm=(30, 0, -8)), y_boy - 180),
           (T_OFFER, torso_pose(-6, head=(-26, 0, 0), r_arm=(20, 0, 6), l_arm=(30, 0, -8)), y_boy - 180),
           (T_OFFER + 1.0, torso_pose(-2, head=(-14, 0, 0), r_arm=(16, 0, 6), l_arm=(30, 0, -8)), BOY_YAW),
           ]
    (rx, ry), TIP, D_TAKE = solve_reach(root, BOY_YAW, KID, LEAN, HAND)
    REACH = (rx - LEAN, ry, 0)                                # relative to the leaning torso
    T_REACH = 47.9
    R0 = -44.0                                                # the hand comes up, hesitant, before the reach
    seq += [(T_REACH - 0.9, torso_pose(-2, head=(LOOK - 6, 0, 0), r_arm=(16, 0, 6), l_arm=(30, 0, -8)), BOY_YAW),
            (T_REACH, torso_pose(-2, head=(LOOK - 6, 0, 0), r_arm=(R0, 0, 4), l_arm=(30, 0, -8)), BOY_YAW),
            (T_TAKE - 0.12, torso_pose(LEAN, head=(LOOK, 0, 0), r_arm=REACH, l_arm=(30, 0, -8)), BOY_YAW),
            (T_TAKE + 0.15, torso_pose(LEAN, head=(LOOK, 0, 0), r_arm=REACH, l_arm=(30, 0, -8)), BOY_YAW),
            (T_TAKE + 1.6, torso_pose(-4, head=(6, 0, 0), r_arm=(-62, 0, -14), l_arm=(-30, 0, 10)), BOY_YAW),
            (LEN, torso_pose(-4, head=(10, 0, 0), r_arm=(-60, 0, -14), l_arm=(-30, 0, 10)), BOY_YAW)]
    yaw_keys, limb_keys = [], []
    for t, pz, y in seq:
        limb_keys.append((t, pz, None))
        yaw_keys.append({'t': tk(t), 'yaw': round(y, 1)})
    dense = on_arc(limb_keys)
    ba = [act(T_TAKE, 'hold', item=INVITE)]
    sc.actor('boy', skin=SKIN['boy'], scale=KID, alive=False, path=[{'t': 0, 'pos': root}], look=yaw_keys,
             limbs=rig_limbs(dense), actions=ba)
    sc.rigs.add('boy')

    # ---- the Dursleys
    vs = IA['vernon_corner']['seat']
    VR = seat_root(vs, ADULT)
    VS = stand_spot(vs, ADULT)
    vcp = IA['vernon_corner']['pos']
    vm = Mover('vernon', VR, vs[3])
    T_VUP = T_P1 + 0.9
    vm.hold(T_VUP)
    vm.to(T_VUP + 1.0, VS, 'linear', face=False)
    sc.legs.append(('hold', 'vernon', 0.0, VR, None, vs[3], ADULT, -90))
    sc.legs.append(('stand', 'vernon', T_VUP, VR, VS, vs[3], ADULT, -90))
    sc.seated['vernon'] = [(0.0, T_VUP + 1.05)]
    vm.walk(T_VUP + 1.3, [vcp], 0.9)
    vm.face(vm.t + 0.05, IA['vernon_corner']['yaw'], 0.4)
    vl = Limbs()
    vl['head'].change(0, [34, 0, 0], 0.1)                     # asleep, chin on his chest
    vl['head'].change(T_P1 + 0.1, [-6, 0, 0], 0.25)
    vc = Clips()
    vc.cue(T_VUP, 'STAND_UP', 2)
    vc.cue(T_VUP + 1.1, 'IDLE', 4)
    T_GUN = 16.3
    sc.say(T_GUN, 'letter_vernon_gun', 19.8)
    vposes = [pose(0, 'seated', 1), pose(T_VUP + 1.15, 'standing', 1), pose(T_GUN - 0.4, 'aim', 6),
              pose(T_BREAK + 0.2, 'cower', 5), pose(28.6, 'aim', 8), pose(31.0, 'cower', 10)]
    vm.walk(T_BREAK + 0.3, [[2.6, 0, -0.1]], 1.4)             # backs into the corner
    vm.face(vm.t + 0.05, 25, 0.3)
    sc.actor('vernon', skin=SKIN['vernon'], mover=vm, life=vl, clips=vc, poses=vposes, items={'mainhand': GUN})
    # Petunia asleep on the bed (a rig), sits up at the pounding, cowers
    pb = IA['petunia_corner']['bed_seat']
    kp = kpx(ADULT)
    proot = [3.5, round(lying_root_y(0.5625, ADULT), 4), round(-2.95 + 20 * kp, 4)]
    pseq = [(0.0, torso_pose(-90, head=(0, 0, -12)), None),
            (T_P1 + 0.3, torso_pose(-90), None),
            (T_P1 + 0.8, torso_pose(-55, head=(-6, 0, 0), r_arm=(30, 0, 8), l_arm=(30, 0, -8)), None),
            (T_P1 + 1.6, torso_pose(-8, head=(4, 0, 0), r_arm=(-20, 0, -10), l_arm=(-20, 0, 10)), None),
            (T_BREAK, torso_pose(-8, head=(4, 0, 0), r_arm=(-20, 0, -10), l_arm=(-20, 0, 10)), None),
            (T_BREAK + 0.3, torso_pose(4, head=(24, 0, 0), r_arm=(-112, 0, -18), l_arm=(-110, 0, 18)), None),
            (LEN, torso_pose(4, head=(26, 0, 0), r_arm=(-110, 0, -18), l_arm=(-108, 0, 18)), None)]
    sc.actor('petunia', skin=SKIN['petunia'], alive=False, path=[{'t': 0, 'pos': proot}],
             look=[{'t': 0, 'yaw': 0}, {'t': tk(T_P1 + 1.6), 'yaw': 0}, {'t': tk(T_P1 + 2.6), 'yaw': 18}],
             limbs=rig_limbs(on_arc(pseq)))
    sc.rigs.add('petunia')
    # Dudley asleep along the sofa (a rig), sits up, cowers
    ds = IA['dudley_sofa']
    droot = [round(ds['head'][0] - 0.05 + 20 * k, 4), round(lying_root_y(ds['seat_top'], KID), 4), ds['center'][2]]
    dseq = [(0.0, torso_pose(-90, head=(0, 0, 14)), None),
            (T_P1 + 0.5, torso_pose(-90), None),
            (T_P1 + 1.3, torso_pose(-30, head=(-4, 0, 0), r_arm=(40, 0, 8), l_arm=(40, 0, -8)), None),
            (T_BREAK, torso_pose(-30, head=(-4, 0, 0), r_arm=(40, 0, 8), l_arm=(40, 0, -8)), None),
            (T_BREAK + 0.3, torso_pose(-12, head=(20, 0, 0), r_arm=(-104, 0, -16), l_arm=(-100, 0, 16)), None),
            (LEN, torso_pose(-12, head=(22, 0, 0), r_arm=(-104, 0, -16), l_arm=(-100, 0, 16)), None)]
    sc.actor('dudley', skin=SKIN['dudley'], scale=KID, alive=False, path=[{'t': 0, 'pos': droot}],
             look=[{'t': 0, 'yaw': -90}, {'t': tk(T_P1 + 1.3), 'yaw': -90}, {'t': tk(T_P1 + 2.2), 'yaw': -120}],
             limbs=rig_limbs(on_arc(dseq)))
    sc.rigs.add('dudley')

    # ---- camera
    sc.key(0, [14.0, -3.0, 34.0], [0.0, 1.5, 1.0], 60)
    sc.key(5.0, [12.6, -3.3, 31.0], [0.0, 1.6, 1.0], 54)
    sc.key(5.0, [2.6, 1.9, 2.75], [-1.8, 0.5, -0.6], 70, cut=True)
    sc.key(10.0, [2.25, 1.8, 2.5], [-1.9, 0.45, -0.3], 66)
    face = [root[0] - 16 * k, root[1] + 16 * k, root[2]]
    sc.key(10.0, [-2.2, 0.95, 1.3], face, 50, cut=True)
    sc.key(T_JOLT, [-2.15, 0.97, 1.32], face, 48)
    sc.key(T_JOLT + 1.4, [-1.75, 1.2, 1.75], [root[0] + 0.1, root[1] + 1.1, root[2]], 56)
    sc.key(14.4, [-1.7, 1.22, 1.8], [root[0] + 0.15, root[1] + 1.05, root[2] + 0.1], 56)
    sc.key(14.4, [-0.6, 1.75, -0.9], [0.0, 1.25, 4.0], 72, cut=True)
    sc.key(19.4, [-0.55, 1.72, -0.75], [0.0, 1.2, 4.0], 70)
    sc.key(19.4, [0.6, 1.35, 1.4], [0.0, 1.25, 3.95], 50, cut=True)
    sc.key(T_BREAK - 0.05, [0.55, 1.35, 1.5], [0.0, 1.25, 3.95], 48)
    sc.key(T_BREAK - 0.05, [-0.5, 0.45, -0.6], [0.0, 1.4, 4.6], 82, cut=True)
    sc.key(T_IN + 0.6, [-0.5, 0.45, -0.6], [0.0, 1.9, 4.2], 82)
    sc.key(T_INSIDE + 0.2, [-0.55, 0.45, -0.65], 'actor:hagrid', 78)
    sc.key(25.6, [-0.6, 0.45, -0.7], 'actor:hagrid', 76)
    sc.key(25.6, [-1.7, 0.6, 0.95], 'actor:hagrid', 60, cut=True)
    sc.key(27.6, [-1.65, 0.62, 0.9], 'actor:hagrid', 58)
    sc.key(27.6, [0.9, 1.45, 1.3], [2.8, 0.95, -1.1], 58, cut=True)
    sc.key(29.6, [0.95, 1.42, 1.2], [2.8, 0.95, -1.15], 56)
    over = ahead([root[0], 0, root[2]], y_boy - 180, -0.8, side=0.12, y=1.2)
    sc.key(29.6, over, 'actor:hagrid', 58, cut=True)
    sc.key(35.0, ahead([root[0], 0, root[2]], y_boy - 180, -0.72, side=0.12, y=1.15), 'actor:hagrid', 54)
    # H9: close on the boy from his front left, clear of the crouching Hagrid
    bface = eye_of(root, y_boy - 180, KID, torso_pose(-6)['head'][1], (-36, 0, 0))
    h9 = ahead([root[0], 0, root[2]], y_boy - 180, 0.6, side=-0.78, y=1.28)
    sc.key(35.0, h9, bface, 46, cut=True)
    sc.key(39.6, ahead([root[0], 0, root[2]], y_boy - 180, 0.62, side=-0.74, y=1.25), bface, 42)
    # H10: over the boy's right shoulder again (H8's side of the line), on the true hand and the letter
    over2 = ahead([root[0], 0, root[2]], y_boy - 180, -0.7, side=0.3, y=1.08)
    sc.key(39.6, over2, [HAND[0], HAND[1] + 0.25, HAND[2]], 56, cut=True)
    sc.key(42.6, over2, [HAND[0], HAND[1] + 0.45, HAND[2]], 54)
    sc.key(45.6, ahead([root[0], 0, root[2]], y_boy - 180, -0.62, side=0.28, y=1.06),
           [HAND[0], HAND[1] + 0.4, HAND[2]], 52)

    # H11, first person: the camera sits just off the boy's face (the eye is on the head's front face, so 0.07 out
    # along the view clears the head and its hat layer), looking at Hagrid's hand, then at the two hands meeting
    def fp_cam(a, head_abs, yaw, target):
        e = eye_of(root, yaw, KID, torso_pose(a)['head'][1], (head_abs, 0, 0))
        d = [target[i] - e[i] for i in range(3)]
        n = math.sqrt(sum(c * c for c in d))
        return [e[i] + d[i] / n * 0.07 for i in range(3)]
    MEET = [(HAND[i] + TIP[i]) / 2 for i in range(3)]
    LOOK0 = [HAND[0], HAND[1] - 0.08, HAND[2]]
    C0 = fp_cam(-2, -2 + LOOK - 6, BOY_YAW, LOOK0)
    CT = fp_cam(LEAN, LEAN + LOOK, BOY_YAW, MEET)
    sc.key(45.6, C0, LOOK0, 66, cut=True)
    sc.key(T_REACH, C0, LOOK0, 66)
    sc.key(T_TAKE - 0.12, CT, MEET, 70)
    sc.key(T_TAKE + 0.15, CT, MEET, 70)
    pull = arm_tip(root, BOY_YAW, KID, 'right_arm', [-66, 0, -14], pivot_px=torso_pose(-4)['right_arm'][1])
    C1 = fp_cam(-4, 2, BOY_YAW, pull)
    C2 = fp_cam(-4, 6, BOY_YAW, pull)
    sc.key(T_TAKE + 1.6, C1, pull, 64)
    sc.key(LEN, C2, [pull[0], pull[1] - 0.03, pull[2]], 58)
    T_CLOSE = 56.4
    sc.ev(T_CLOSE, 'effect', 'eyes_close', at=[0, 0, 0], duration=50)
    assert T_CLOSE + 2.5 < LEN

    scene = sc.scene(SET_ISLAND, LI.RADIUS, end_action='wake')
    evs = scene['events']
    assert evs[-1]['id'] == 'eyes_close' and all(e['t'] < evs[-1]['t'] for e in evs[:-1]), 'eyes_close must be last'

    # Hagrid's doorway pass, checked with the real keyed stoop instead of the generic body points
    t_a, t_b = tk(T_IN - 0.3), tk(T_INSIDE + 0.6)
    sc.hagrid_ticks = set(range(t_a, t_b + 1))
    hag = next(a for a in scene['actors'] if a['id'] == 'hagrid')

    def track_x(part, t):
        ks = hag['limbs'][part]['rot']
        return sample_path([{'t': kk['t'], 'pos': kk['v'], 'ease': kk.get('ease', 'in_out')} for kk in ks], t)[0]

    def extra(problems):
        n_hit = 0
        for t in range(t_a, t_b + 1):
            p = sample_path(hag['path'], t)
            s_ = track_x('body', t)
            hd = track_x('head', t) - s_
            if abs(p[0]) > ent['x_tolerance']:
                problems.append('hagrid off the door centre line at t %d (x %.2f)' % (t, p[0]))
            for q in LI.hagrid_points(p, s_, hd, 180.0):
                if LI.occupied(w, q):
                    n_hit += 1
                    problems.append('hagrid at t %d (z %.2f, stoop %.1f, head %.1f) touches %s at %s' % (
                        t, p[2], s_, hd, w.get(cell(q)), [r3(c) for c in q]))
                    break
            if 3.2 < p[2] < 5.9 and s_ < 40.0:          # anywhere near the doorway he is stooped 40 or more
                problems.append('hagrid under the lintel at t %d with only %.1f stoop' % (t, s_))
        for name in ('door_leaf', 'door_leaf_mirrored'):
            a = next(x for x in scene['actors'] if x['id'] == name)
            assert a['look'][-1]['pitch'] == -90 and a['look'][-1]['yaw'] == 180, name
        assert T_IN > T_FLAT, 'Hagrid steps in only once the leaves are down'
        # the take happens on screen: the boy's hand tip meets Hagrid's hand (the two held letters swap there),
        # both inside the first-person view cone, and the reaching hand comes into frame on its way there
        if D_TAKE > 0.08:
            problems.append("the boy's hand is %.3f from Hagrid's at the take" % D_TAKE)
        cam = sample_cam(scene['camera'], tk(T_TAKE))
        for what, p_ in (("Hagrid's hand", HAND), ("the boy's hand", TIP)):
            va = view_angle(cam, MEET, p_)
            if va > 26.0:
                problems.append('%s is %.1f degrees off the camera axis at the take' % (what, va))
            if not 0.12 < math.dist(cam, p_) < 0.7:
                problems.append('%s is %.2f from the camera at the take' % (what, math.dist(cam, p_)))
        print('  the take: hands %.3f apart; off the axis Hagrid %.1f, boy %.1f degrees; %.2f from the camera' % (
            D_TAKE, view_angle(cam, MEET, HAND), view_angle(cam, MEET, TIP), math.dist(cam, MEET)))
        for f in (0.55, 0.7, 0.85):                           # the second half of the way in, inside fov 66
            t_ = T_REACH + f * (T_TAKE - 0.12 - T_REACH)
            g = 3 * f * f - 2 * f ** 3
            a_ = -2 + (LEAN + 2) * g
            rx_ = (R0 + (REACH[0] - R0) * g) + a_
            ry_ = REACH[1] * g
            tip_ = arm_tip(root, BOY_YAW, KID, 'right_arm', [rx_, ry_, 0], pivot_px=torso_pose(a_)['right_arm'][1])
            c_ = sample_cam(scene['camera'], tk(t_))
            look_ = [LOOK0[i] + (MEET[i] - LOOK0[i]) * g for i in range(3)]
            va = view_angle(c_, look_, tip_)
            if va > 32.0:
                problems.append('the reaching hand is %.1f degrees off the axis %.0f%% of the way in' % (va, f * 100))
        held = [a_ for a_ in scene['actors'] if a_['id'] in ('hagrid', 'boy')]
        swaps = sorted((x['t'], a_['id'], x.get('item')) for a_ in held for x in a_.get('actions', [])
                       if x['do'] in ('hold', 'equip') and x['t'] >= tk(T_TAKE) - 2)
        assert [s_[0] for s_ in swaps] == [tk(T_TAKE)] * 2, swaps
    return sc, scene, extra


# ================================================================ main
def main():
    built = []
    for make in (letter_scene, flood_scene, crossing_scene, hut_scene):
        sc, scene, extra = make()
        n_chars, n_prox, n_legs = finish(sc, scene, extra)
        built.append((sc, scene, n_chars, n_prox, n_legs))
    ids = {sc.sid for sc, *_ in built}
    assert ids == set(IDS), ids
    for sc, scene, *_ in built:
        nxt = scene.get('next')
        assert nxt is None or nxt in ids, (sc.sid, nxt)
        assert scene['radius'] <= 200
    assert built[-1][1].get('end_action') == 'wake' and 'next' not in built[-1][1]
    chain, sid = [], 'letter'
    by = {sc.sid: scene for sc, scene, *_ in built}
    while sid:
        chain.append(sid)
        sid = by[sid].get('next')
    assert chain == list(IDS), chain
    total = 0.0
    for sc, scene, n_chars, n_prox, n_legs in built:
        total += sc.length
        print('wrote %s.json: %d chars (cap %d), %.1f s, %d actors, %d events, %d camera keys, %d proximity checks,'
              ' %d leg corners' % (sc.sid, n_chars, MAX_JSON, sc.length, len(scene['actors']), len(scene['events']),
                                  len(scene['camera']), n_prox, n_legs))
    print('total %.1f s in %d scenes' % (total, len(built)))
    for sc, *_ in built:
        for line, t, secs, slot_end in sc.lines:
            print('  %-16s %-22s t %5.1f  %4.2f s  slack %.2f' % (sc.sid, line, t, secs, slot_end - t - secs))


if __name__ == '__main__':
    main()
