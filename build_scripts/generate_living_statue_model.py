"""Build the Living Statue's model from one spec: .bbmodel, Java model, and texture.

    python build_scripts/generate_living_statue_model.py

The part tree is declared once, in PARTS, and three things are emitted from it so they can never
drift apart:

  * blockbench_models/living_statue.bbmodel  -- openable and editable in Blockbench
  * .../client/LivingStatueModel.java        -- the runtime model, with the animation hooks
  * .../textures/entity/living_statue.png    -- painted from the same UV layout

Hand-transcribing a 20-cube humanoid into Java is how you end up with a mob whose head is boxed
inside its own chest, so none of it is transcribed by hand.

Coordinates
-----------
Everything below is in MINECRAFT MODEL SPACE, which is what the Java wants directly:
+Y is DOWN, y=0 is the shoulder line, -Z is the direction the mob faces. The .bbmodel is emitted
by converting back the other way (Blockbench is Y-up), so the two always agree.

The statue is a suit of plate holding a halberd, built to read at a glance from the silhouette
alone: a squared-off helm with a crest, heavy pauldrons wider than the chest, and a skirt of
tassets. Those three are what separate it from "a player in armour" at 20 blocks away.
"""

import json
import math
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BB_OUT = os.path.join(ROOT, "blockbench_models", "living_statue.bbmodel")
JAVA_OUT = os.path.join(ROOT, "src", "main", "java", "com", "hogwartsmagic", "client",
                        "LivingStatueModel.java")
TEX_OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                       "textures", "entity", "living_statue.png")

TEX_W, TEX_H = 128, 128

# name: (parent, pivot(x,y,z), [ (cube_name, origin(x,y,z), size(w,h,d), uv(u,v), inflate) ])
# Origins are the cube's minimum corner in model space, absolute (not pivot-relative) -- the
# emitters subtract the pivot themselves, which is the step most easily got wrong by hand.
PARTS = {
    "root": (None, (0.0, 0.0, 0.0), []),

    # UV boxes must not overlap each other: a box net is 2*(d+w) wide by d+h tall, and two cubes
    # sharing texels means whichever the painter reaches last silently repaints the other's face.
    # The crest sat at (0,20) -- 20x11, so it ran into the chest's net at (0,30) -- and the gorget
    # at (56,0) ran two columns into the visor's. Both are parked clear of everything now.
    "head": ("root", (0.0, 0.0, 0.0), [
        ("helm",     (-5.0, -10.0, -5.0), (10, 9, 10), (0, 0), 0.0),
        ("crest",    (-1.0, -12.5, -4.0), (2, 3, 8),  (90, 0), 0.0),
        ("visor",    (-4.0, -6.0, -5.8),  (8, 3, 1),  (40, 0), 0.0),
        ("gorget",   (-4.0, -1.5, -4.0),  (8, 2, 8),  (58, 0), 0.0),
    ]),

    "body": ("root", (0.0, 0.0, 0.0), [
        ("chest",    (-6.0, 0.0, -4.0), (12, 12, 8), (0, 30), 0.0),
        ("tasset",   (-7.0, 11.0, -4.5), (14, 5, 9), (0, 52), 0.0),
    ]),

    "right_arm": ("root", (-7.0, 1.5, 0.0), [
        ("r_pauldron", (-11.0, -1.0, -4.5), (7, 6, 9), (48, 30), 0.0),
        ("r_upper",    (-10.0, 3.0, -2.5),  (5, 8, 5), (48, 46), 0.0),
        ("r_gaunt",    (-10.0, 11.0, -2.5), (5, 7, 5), (68, 46), 0.0),
    ]),

    "left_arm": ("root", (7.0, 1.5, 0.0), [
        ("l_pauldron", (4.0, -1.0, -4.5), (7, 6, 9), (48, 30), 0.0),
        ("l_upper",    (5.0, 3.0, -2.5),  (5, 8, 5), (48, 46), 0.0),
        ("l_gaunt",    (5.0, 11.0, -2.5), (5, 7, 5), (68, 46), 0.0),
    ]),

    # Each leg is TWO bones with a knee between them. It was one bone to begin with, and a single
    # rotation from the hip cannot kneel -- it swings the whole limb out horizontally like a high
    # kick, which is exactly how it rendered. A kneel needs the thigh to go one way and the shin
    # the other, so the shin gets its own pivot at the knee (y=25, the top of the greave).
    "right_leg": ("root", (-3.0, 17.0, 0.0), [
        ("r_thigh",  (-6.0, 17.0, -3.0), (6, 8, 6), (88, 30), 0.0),
    ]),
    "right_shin": ("right_leg", (-3.0, 25.0, 0.0), [
        ("r_greave", (-5.5, 25.0, -3.0), (5, 7, 6), (88, 46), 0.0),
        ("r_foot",   (-6.0, 31.0, -4.5), (6, 3, 8), (88, 62), 0.0),
    ]),

    "left_leg": ("root", (3.0, 17.0, 0.0), [
        ("l_thigh",  (0.0, 17.0, -3.0), (6, 8, 6), (88, 30), 0.0),
    ]),
    "left_shin": ("left_leg", (3.0, 25.0, 0.0), [
        ("l_greave", (0.5, 25.0, -3.0), (5, 7, 6), (88, 46), 0.0),
        ("l_foot",   (0.0, 31.0, -4.5), (6, 3, 8), (88, 62), 0.0),
    ]),

    # Carried in the right hand. The haft runs down -Y so the Java can just tip it forward for
    # the guard pose.
    #
    # The first pass made this 2x2 with a 4x5 head tucked hard against the body, and from the
    # side it read as a broomstick -- no blade silhouette at all. A polearm is recognised almost
    # entirely by the shape at its top end, so the haft is thicker, it is pushed clear of the
    # arm on -X, and the head is now three separate masses: a broad blade one side, a beak the
    # other, and a spike above. That asymmetry is what says "halberd" rather than "spear".
    # Carried a clear 7 units in FRONT of the torso (z), which is not decoration -- at the old
    # depth the shaft was simply inside the mob. The haft spans y -16..22 at x -10.5..-7.5, and
    # the pauldron occupies x -11..-4 at the same depth, so the shaft ran straight through the
    # shoulder in the rest pose and every standing pose; rolling it for the kneel then dragged it
    # through the chest, gorget and helm as well. A polearm is held in front of the body, so the
    # whole part moves forward and the clash goes away at the source rather than per pose.
    # audit_clearance() re-checks this against every pose on each build.
    "halberd": ("right_arm", (-9.0, 13.0, -8.0), [
        ("haft",  (-10.5, -16.0, -9.5), (3, 38, 3), (0, 66), 0.0),
        ("blade", (-15.0, -15.0, -9.0), (5, 9, 2), (16, 66), 0.0),
        ("beak",  (-7.5, -13.0, -9.0),  (3, 5, 2), (32, 66), 0.0),
        ("spike", (-10.0, -22.0, -9.0), (2, 6, 2), (48, 66), 0.0),
    ]),
}

ORDER = ["root", "head", "body", "right_arm", "left_arm", "right_leg", "right_shin",
         "left_leg", "left_shin", "halberd"]

# The dormant kneel. These are SOLVED, not chosen, and audit_kneel() re-solves the contacts on
# every run -- which is the only reason to trust them. Four earlier attempts were swept by eye
# against renders and every one of them was broken in a way no amount of tuning could reach: the
# figure hung 7.4 units clear of the floor, the right leg's roll carried its knee across to
# x=+1.3 so the legs swapped sides, one boot sat 3.4 units underground, and the left knee bent
# forwards. Magnitudes cannot fix a wrong sign, so the pose is derived instead.
#
# Style is two knobs; everything else follows from them:
#   * the down-thigh's roll (how far the knee splays outboard), which has to carry the knee clear
#     of a skirt that overhangs the hips by 4 units either side, or the kneel is hidden behind it
#   * how far ahead the bracing foot plants, which sets that knee's bend -- plant it close and the
#     leg folds double like a catcher's crouch, plant it far and the leg cannot reach the floor
# Then the down-knee touching the floor fixes DROP, and the bracing sole touching it fixes that
# leg by two-link IK. Contact is not negotiable: the hip sits 17 above the floor and the thigh is
# only 8 long, so a kneel that does not drop the whole figure by ~12 is a kneel in mid-air.
KNEEL = {
    "drop":      12.68,   # solved from the down-knee reaching the floor, NOT picked
    "body_x":     0.28,
    "head_x":     0.16,
    "r_leg_x":    0.25,   # down-thigh tipped slightly back, knee under/behind the hip
    "r_leg_z":    0.98,   # ...and rolled outboard; this is what clears the tassets
    "r_shin_x":   1.32,   # folds flat, so the shin lies along the floor behind the knee
    "l_leg_x":   -2.00,   # bracing thigh forward, knee high
    "l_shin_x":   1.45,   # 83 degrees of bend, planting the boot 12 ahead
    "r_arm_x":   -0.62,
    "l_arm_x":   -0.48,
    "l_arm_z":    0.30,
    # The diagonal is a Z-ROLL. X is the depth axis, so using it to MAKE the diagonal tips the
    # haft at the camera and reads as a couched lance -- that was the original mistake and it
    # stands.
    #
    # The small X here does the opposite job: it CANCELS the parent arm's forward pitch. The
    # halberd hangs off right_arm, and the shaft rises from a pivot below that hinge, so pitching
    # the arm forward by 0.62 tips the shaft's top backwards -- into the head. Measured, the world
    # shaft direction was (0.644, -0.622, +0.444): that +0.444 of depth is the shaft leaning back
    # out of the frontal plane, which both buried it in the helm and quietly undid the roll. With
    # 0.30 of counter-pitch it reads (0.718, -0.685, +0.126) -- same diagonal, now actually flat
    # against the body. The roll opens to 0.85 to keep the same on-screen angle once flattened.
    "halberd_x":  0.30,
    "halberd_z":  0.85,
}

# Rest geometry the kneel is solved against, read straight off PARTS above: hips at y=17, knees 8
# below them, soles 9 below the knees -- so with every angle zero the soles sit exactly on y=34,
# and that is the floor.
HIP_Y, KNEE_D, SOLE_D, FLOOR = 17.0, 8.0, 9.0, 34.0
TASSET_X = 7.0


# --- texture -------------------------------------------------------------------------------
def noise(x, y, salt=0):
    h = (x * 73856093) ^ (y * 19349663) ^ (salt * 2654435761)
    h &= 0x7FFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0x7FFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


STONE = [(96, 96, 92), (108, 108, 103), (118, 118, 112), (86, 86, 83), (128, 128, 121)]
MOSS = [(74, 92, 62), (86, 104, 70)]


def paint(px, u, v, w, h, salt, lit=1.0):
    """Weathered granite: mottled grey, moss creeping in low, a few chipped edges."""
    for y in range(h):
        for x in range(w):
            n = noise(u + x, v + y, salt)
            c = STONE[int(n * len(STONE)) % len(STONE)]
            # moss gathers toward the bottom of each face
            if y > h * 0.55 and noise(u + x, v + y, salt + 7) > 0.80:
                c = MOSS[int(n * 2) % 2]
            # a chipped highlight along the top edge catches the light
            if y == 0 or (y == 1 and n > 0.6):
                c = STONE[4]
            if x == 0 or x == w - 1:
                c = tuple(int(v2 * 0.88) for v2 in c)
            c = tuple(max(0, min(255, int(v2 * lit))) for v2 in c)
            px[(u + x, v + y)] = c


def write_png(path, w, h, px):
    raw = b""
    for y in range(h):
        raw += b"\x00"
        for x in range(w):
            c = px.get((x, y))
            raw += bytes(c) + b"\xff" if c else b"\x00\x00\x00\x00"
    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(png)


def build_texture():
    px = {}
    for pname in ORDER:
        for i, (cname, org, size, uv, _inf) in enumerate(PARTS[pname][2]):
            w, hgt, d = size
            u, v = uv
            salt = abs(hash(cname)) % 999
            # standard box net: up/down on the top strip, then E N W S across the side strip
            paint(px, u + d, v, w, d, salt, 1.06)                 # up
            paint(px, u + d + w, v, w, d, salt + 1, 0.80)         # down
            paint(px, u, v + d, d, hgt, salt + 2, 0.90)           # east
            paint(px, u + d, v + d, w, hgt, salt + 3, 1.00)       # north (face)
            paint(px, u + d + w, v + d, d, hgt, salt + 4, 0.90)   # west
            paint(px, u + 2 * d + w, v + d, w, hgt, salt + 5, 0.86)  # south
    # The eye slit: two points of cold light in the visor, which is the whole tell that the
    # thing is awake. Painted onto the visor's north face.
    vu, vv = 40, 0
    for dx in (2, 5):
        for dy in (1,):
            px[(vu + 1 + dx, vv + 1 + dy)] = (150, 220, 255)
            px[(vu + 2 + dx, vv + 1 + dy)] = (110, 190, 245)
    write_png(TEX_OUT, TEX_W, TEX_H, px)


# --- animation clips -----------------------------------------------------------------------
# The five states, baked into the .bbmodel as real keyframes so they can be scrubbed.
#
# These are SAMPLED from the same arithmetic setupAnim runs, never hand-keyframed. That is the
# whole point: a hand-authored clip is a second implementation of the animation, and the two
# drift the moment anyone touches either. Sampling means what Blockbench shows is what the game
# draws, and it means the dormant pose can finally be LOOKED at -- there was no animation data in
# this file at all before, which is why four broken kneels shipped unnoticed.
#
# The runtime stays procedural. It has to: the walk tracks limbSwing, the rise tracks the entity's
# wake counter and the chop tracks getAttackAnim, and none of those are clocks a fixed-length
# keyframe track can follow. These clips are the preview, not the source.
ANIM_BONES = ["head", "body", "right_arm", "left_arm", "right_leg", "right_shin",
              "left_leg", "left_shin", "halberd"]
# Only the root-level parts take the kneel's drop; shins and halberd inherit it from their parent.
ANIM_ROOTS = ["head", "body", "right_arm", "left_arm", "right_leg", "left_leg"]
DEG = 180.0 / math.pi


def _blank():
    return {b: {"rot": [0.0, 0.0, 0.0], "pos": [0.0, 0.0, 0.0]} for b in ANIM_BONES}


def _kneel_pose(p, t):
    k = KNEEL
    p["body"]["rot"][0] = k["body_x"] * t
    p["head"]["rot"][0] = k["head_x"] * t
    p["right_leg"]["rot"][0] = k["r_leg_x"] * t
    p["right_leg"]["rot"][2] = k["r_leg_z"] * t
    p["right_shin"]["rot"][0] = k["r_shin_x"] * t
    p["left_leg"]["rot"][0] = k["l_leg_x"] * t
    p["left_shin"]["rot"][0] = k["l_shin_x"] * t
    p["right_arm"]["rot"][0] = k["r_arm_x"] * t
    p["left_arm"]["rot"][0] = k["l_arm_x"] * t
    p["left_arm"]["rot"][2] = k["l_arm_z"] * t
    p["halberd"]["rot"][0] = k["halberd_x"] * t
    p["halberd"]["rot"][2] = k["halberd_z"] * t
    for b in ANIM_ROOTS:
        p[b]["pos"][1] = k["drop"] * t
    return p


# Beats of the three-blow combo, in ticks; must match SWING_TICKS in LivingStatueEntity.
SWING_TICKS = [34, 34, 46]

# Coefficients for one blow, shared by the Java (substituted into chop()) and by _chop below.
# Held once for the same reason KNEEL is: the sampler that draws the Blockbench preview and the
# code the game runs have to be the same animation, and two hand-kept copies of thirty numbers
# are two animations that agree only until someone edits one of them.
#
# There is deliberately NO torso twist here, which is the obvious way to sell a heavy swing. It
# cannot work on this rig: `body` and the arms are SIBLINGS under root, so yawing the body turns
# the chest while the pauldrons -- which belong to the arms -- stay where they were, and the
# shoulders visibly shear apart. A real twist needs an upper-body bone the model does not have.
# The power comes from the arm arcs and the recovery instead.
CHOP = {
    "wind_end": 0.34, "wind_end_heavy": 0.42,
    "hit_end": 0.48, "hit_end_heavy": 0.54,
    "power_heavy": 1.3,
    # The raise stops just past vertical. It was -2.6 (149 degrees, the arm swung back BEHIND the
    # head) and audit_clearance rejected it: combined with the roll below it put the halberd's
    # grip at world (2.5, -8.9, -1.6) on the mirrored swing -- x across to the far side, y nearly
    # nine units above the shoulder line, z at body depth. That is the statue's own hand inside
    # its skull, and no amount of tuning the WEAPON fixes a hand in the wrong place. The forward
    # offset the halberd carries is rotated away entirely once the arm goes that far back.
    "r_arm_up": -1.80, "r_arm_down": 1.35,
    "l_arm_up": -1.65, "l_arm_down": 1.15,
    # Rolls stay shallow for the same reason: the arm pivots at x=-7, so a big roll swings the
    # hand through a wide arc and lands it on the opposite side of the chest.
    "r_arm_z_up": -0.60, "r_arm_z_down": -0.15,
    "l_arm_z_up": 0.54, "l_arm_z_down": 0.14,
    "body_x_up": -0.26, "body_x_down": 0.52,
    "head_x_up": -0.18, "head_x_down": 0.32,
    # The head follows the arc; it is a root child that pivots on its own neck, so unlike the
    # torso it can yaw without tearing anything.
    "head_y_up": 0.34, "head_y_down": -0.38,
    # Pitched further forward the harder it swings, which keeps the shaft clear of the body
    # exactly when the arm is crossing it.
    "halberd_x_base": 0.18, "halberd_x_down": 0.85,
    "halberd_z_base": 0.20, "halberd_z_swing": 0.55,
    "lurch_r_leg": -0.34, "lurch_l_leg": 0.26,
    "lurch_r_shin": 0.40, "lurch_l_shin": 0.12,
    "lurch_body_x": 0.20, "lurch_head_x": 0.14, "lurch_drop": 1.6,
    "shudder_amp": 0.10, "shudder_freq": 21.0,
}


def _chop(p, index, t):
    """Mirror of LivingStatueModel.chop. Two diagonals then a heavy overhead split."""
    c = CHOP
    side = 1.0 if index == 0 else (-1.0 if index == 1 else 0.0)
    heavy = index == 2
    power = c["power_heavy"] if heavy else 1.0
    wind_end = c["wind_end_heavy"] if heavy else c["wind_end"]
    hit_end = c["hit_end_heavy"] if heavy else c["hit_end"]

    wind = min(1.0, max(0.0, t / wind_end))
    wind = 1.0 - (1.0 - wind) * (1.0 - wind)
    hit = min(1.0, max(0.0, (t - wind_end) / (hit_end - wind_end)))
    hit *= hit
    rec = min(1.0, max(0.0, (t - hit_end) / (1.0 - hit_end)))

    up, down = wind * (1.0 - hit), hit
    hold = 1.0 - rec * rec

    p["right_arm"]["rot"][0] = (c["r_arm_up"] * up + c["r_arm_down"] * down) * power * hold
    p["left_arm"]["rot"][0] = (c["l_arm_up"] * up + c["l_arm_down"] * down) * power * hold
    p["right_arm"]["rot"][2] = ((c["r_arm_z_up"] * up + c["r_arm_z_down"] * down)
                                * side * power * hold)
    p["left_arm"]["rot"][2] = ((c["l_arm_z_up"] * up + c["l_arm_z_down"] * down)
                               * side * power * hold)
    p["body"]["rot"][0] = (c["body_x_up"] * up + c["body_x_down"] * down) * power * hold
    p["head"]["rot"][0] = (c["head_x_up"] * up + c["head_x_down"] * down) * power * hold
    p["head"]["rot"][1] = (c["head_y_up"] * up + c["head_y_down"] * down) * side * hold
    p["halberd"]["rot"][0] = c["halberd_x_base"] + c["halberd_x_down"] * down * hold
    p["halberd"]["rot"][2] = (c["halberd_z_base"]
                              + c["halberd_z_swing"] * (up - down) * side) * hold

    lurch = rec * (1.0 - rec) * 4.0
    shudder = (1.0 - rec) * math.sin(rec * c["shudder_freq"]) * c["shudder_amp"] * power
    p["right_leg"]["rot"][0] += c["lurch_r_leg"] * lurch * power
    p["left_leg"]["rot"][0] += c["lurch_l_leg"] * lurch * power
    p["right_shin"]["rot"][0] += c["lurch_r_shin"] * lurch * power
    p["left_shin"]["rot"][0] += c["lurch_l_shin"] * lurch * power
    p["body"]["rot"][0] += c["lurch_body_x"] * lurch * power + shudder
    p["head"]["rot"][0] += c["lurch_head_x"] * lurch - shudder * 0.8
    for b in ANIM_ROOTS:
        p[b]["pos"][1] = c["lurch_drop"] * lurch * power
    return p


def _setup_anim(wake=1.0, crumble=0.0, limb_swing=0.0, limb_amt=0.0, age=0.0, combo=-1, cp=0.0):
    """Mirrors LivingStatueModel.setupAnim branch for branch, early returns included."""
    p = _blank()
    if wake < 1.0:
        t = 1.0 - (1.0 - wake) * (1.0 - wake)
        _kneel_pose(p, 1.0 - t)
        judder = (1.0 - t) * 0.055
        p["body"]["rot"][0] += math.sin(age * 2.7) * judder
        p["head"]["rot"][0] += math.sin(age * 3.9) * judder
        p["right_arm"]["rot"][2] = math.sin(age * 3.1) * judder * 2.0
        p["left_arm"]["rot"][2] = -p["right_arm"]["rot"][2]
        p["head"]["rot"][0] *= t
        return p
    if crumble > 0.0:
        return _kneel_pose(p, crumble)
    p["right_leg"]["rot"][0] = math.cos(limb_swing * 0.6) * 0.75 * limb_amt
    p["left_leg"]["rot"][0] = math.cos(limb_swing * 0.6 + math.pi) * 0.75 * limb_amt
    p["right_arm"]["rot"][0] = math.cos(limb_swing * 0.6 + math.pi) * 0.35 * limb_amt
    p["left_arm"]["rot"][0] = math.cos(limb_swing * 0.6) * 0.35 * limb_amt
    p["right_shin"]["rot"][0] = max(0.0, -p["right_leg"]["rot"][0] * 0.45)
    p["left_shin"]["rot"][0] = max(0.0, -p["left_leg"]["rot"][0] * 0.45)
    p["right_arm"]["rot"][2] = -0.12
    p["left_arm"]["rot"][2] = 0.12
    p["halberd"]["rot"][0] = 0.18
    p["halberd"]["rot"][2] = 0.20
    idle = (1.0 - limb_amt) * 0.02
    p["body"]["rot"][0] += math.sin(age * 0.06) * idle
    p["head"]["rot"][0] += math.sin(age * 0.05) * idle
    if combo >= 0:
        _chop(p, combo, cp)
    return p


def clips():
    """Blockbench-space samples. The .bbmodel is bb_y = ROOT_H - java_y with x passing straight
    through, and that Y flip negates rotation about X and Z while leaving Y alone -- so a clip
    authored with the raw Java numbers previews mirrored at the hips."""
    def frame(p):
        return {b: {"r": [round(-p[b]["rot"][0] * DEG, 3), round(p[b]["rot"][1] * DEG, 3),
                          round(-p[b]["rot"][2] * DEG, 3)],
                    "p": [0.0, round(-p[b]["pos"][1], 3), 0.0]} for b in ANIM_BONES}

    out = {}
    out["dormant"] = ("hold", 0.5, [(0.0, frame(_kneel_pose(_blank(), 1.0)))])
    # WAKE_TICKS is 50, and the ease-out plus the judder are both non-linear, so sample densely.
    out["wake"] = ("hold", 2.5, [(round(i / 20.0 * 2.5, 3),
                                  frame(_setup_anim(wake=i / 20.0, age=i / 20.0 * 50.0)))
                                 for i in range(21)])
    # one full stride: limbSwing * 0.6 has to cover a whole turn
    span = 2.0 * math.pi / 0.6
    out["walk"] = ("loop", 1.2, [(round(i / 16.0 * 1.2, 3),
                                  frame(_setup_anim(limb_swing=i / 16.0 * span, limb_amt=1.0,
                                                    age=i / 16.0 * 24.0)))
                                 for i in range(17)])
    # The whole three-blow combo end to end, sampled every tick. Coarser sampling was tried and
    # the strike is only about five ticks long -- at two-tick steps the fast part of each swing
    # collapses to three samples and the blow reads as a glide, which is the exact quality the
    # animation exists to avoid.
    combo, tick = [], 0
    for idx, ticks in enumerate(SWING_TICKS):
        for j in range(ticks):
            combo.append((round(tick / 20.0, 3),
                          frame(_setup_anim(combo=idx, cp=j / float(ticks)))))
            tick += 1
    combo.append((round(tick / 20.0, 3), frame(_setup_anim())))
    out["attack_combo"] = ("hold", round(tick / 20.0, 3), combo)
    # CRUMBLE_TICKS is 30
    out["crumble"] = ("hold", 1.5, [(round(i / 12.0 * 1.5, 3),
                                     frame(_setup_anim(crumble=i / 12.0)))
                                    for i in range(13)])
    return out


def build_animations(group_uuid, uid):
    """Blockbench keys each animator by the GROUP's uuid, so this has to run after the outliner.

    X and Y are negated on the way out, and that is not the same flip clips() already applied.
    Blockbench's ANIMATION codec negates X and Y when it reads keyframes from a .bbmodel -- Z and
    position pass through untouched -- so a file holding the numbers we actually want to see
    previews with both legs pitched the wrong way. Verified by measuring mesh.rotation after a
    load: authoring -14.32 in the file yields +0.25 rad on the bone. Note this bites ONLY the
    file; keyframes made in-session through createKeyframe are stored verbatim, which is exactly
    why a clip can look right in the editor and wrong the moment it is reloaded.
    """
    anims = []
    for name, (loop, length, frames) in clips().items():
        animators = {}
        for bone in ANIM_BONES:
            kfs = []
            for t, pose in frames:
                d = pose[bone]
                kfs.append({"channel": "rotation",
                            "data_points": [{"x": str(-d["r"][0]), "y": str(-d["r"][1]),
                                             "z": str(d["r"][2])}],
                            "uuid": uid(), "time": t, "color": -1, "interpolation": "linear"})
                if bone in ANIM_ROOTS:
                    kfs.append({"channel": "position",
                                "data_points": [{"x": "0", "y": str(d["p"][1]), "z": "0"}],
                                "uuid": uid(), "time": t, "color": -1,
                                "interpolation": "linear"})
            animators[group_uuid[bone]] = {"name": bone, "type": "bone", "keyframes": kfs}
        anims.append({"uuid": uid(), "name": name, "loop": loop, "override": False,
                      "length": length, "snapping": 24, "selected": False,
                      "anim_time_update": "", "blend_weight": "", "start_delay": "",
                      "loop_delay": "", "animators": animators})
    return anims


# --- .bbmodel ------------------------------------------------------------------------------
def build_bbmodel():
    """Blockbench is Y-up with the feet near y=0, so convert: bb_y = ROOT_H - java_y."""
    ROOT_H = 34.0
    elements, outliner = [], []
    uuid_n = [0]

    def uid():
        uuid_n[0] += 1
        return "0000-0000-0000-%04d" % uuid_n[0]

    nodes = {}
    for pname in ORDER:
        parent, pivot, cubes = PARTS[pname]
        if not cubes:
            continue
        children = []
        for cname, org, size, uv, inf in cubes:
            w, hgt, d = size
            e_uuid = uid()
            elements.append({
                "name": cname, "box_uv": True, "rescale": False, "locked": False,
                "type": "cube", "uuid": e_uuid, "inflate": inf,
                "from": [org[0], ROOT_H - (org[1] + hgt), org[2]],
                "to":   [org[0] + w, ROOT_H - org[1], org[2] + d],
                "origin": [pivot[0], ROOT_H - pivot[1], pivot[2]],
                "uv_offset": list(uv), "faces": {},
            })
            children.append(e_uuid)
        nodes[pname] = {
            "name": pname, "uuid": uid(), "export": True, "isOpen": True,
            "origin": [pivot[0], ROOT_H - pivot[1], pivot[2]],
            "rotation": [0, 0, 0], "children": children,
        }

    # Nest the groups the same way the Java nests its PartDefinitions. This was a flat list, and a
    # flat outliner is worse than useless for checking a pose: it still LOOKS like the model, so a
    # render off it is trusted, but rotating right_leg left the shin and foot bolted in place and
    # rotating right_arm left the halberd hanging in the air -- while the Java, where right_shin is
    # a child of right_leg, carried both. Every kneel judged in Blockbench was therefore judged
    # against a figure the game would never draw. Origins stay absolute because that is what
    # Blockbench stores even for a nested group; only the tree changes.
    outliner = []
    for pname in ORDER:
        if pname not in nodes:
            continue
        parent = PARTS[pname][0]
        (nodes[parent]["children"] if parent in nodes else outliner).append(nodes[pname])

    model = {
        "meta": {"format_version": "4.5", "model_format": "modded_entity", "box_uv": True},
        "name": "living_statue",
        "model_identifier": "living_statue",
        "resolution": {"width": TEX_W, "height": TEX_H},
        "elements": elements, "outliner": outliner,
        "textures": [{"path": TEX_OUT, "name": "living_statue.png", "folder": "entity",
                      "namespace": "hogwartsmagic", "id": "0", "particle": False,
                      "mode": "link", "uuid": uid()}],
        "animations": build_animations({n: g["uuid"] for n, g in nodes.items()}, uid),
    }
    os.makedirs(os.path.dirname(BB_OUT), exist_ok=True)
    json.dump(model, open(BB_OUT, "w"), indent=2)


# --- Java ----------------------------------------------------------------------------------
def f(v):
    return ("%.1fF" % v) if v == v else "0.0F"


def build_java():
    L = []
    for pname in ORDER:
        parent, pivot, cubes = PARTS[pname]
        if not cubes:
            continue
        var = pname
        L.append('        PartDefinition %s = %s.addOrReplaceChild("%s", CubeListBuilder.create()'
                 % (var, "part" if parent == "root" else parent, pname))
        for cname, org, size, uv, inf in cubes:
            w, hgt, d = size
            L.append('                .texOffs(%d, %d).addBox(%s, %s, %s, %d.0F, %d.0F, %d.0F)'
                     % (uv[0], uv[1], f(org[0] - pivot[0]), f(org[1] - pivot[1]),
                        f(org[2] - pivot[2]), w, hgt, d))
        ppiv = PARTS[parent][1] if parent and parent != "root" else (0.0, 0.0, 0.0)
        L.append('                , PartPose.offset(%s, %s, %s));'
                 % (f(pivot[0] - ppiv[0]), f(pivot[1] - ppiv[1]), f(pivot[2] - ppiv[2])))
        L.append('')
    body = "\n".join(L)

    src = '''package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.LivingStatueEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * GENERATED by build_scripts/generate_living_statue_model.py from
 * blockbench_models/living_statue.bbmodel -- edit the .bbmodel (or the PARTS table in that
 * script) and re-run it. Do not hand-edit the geometry below.
 *
 * The animation, however, IS hand-written and lives in setupAnim: a statue has four states and
 * the whole point of the mob is that you can tell which one it is in from across the room.
 *
 *   DORMANT  a fixed kneeling guard pose, halberd planted. Nothing moves, not even idle sway --
 *            any motion at all and it stops reading as stone.
 *   WAKING   over {@link LivingStatueEntity#WAKE_TICKS} it rises out of that kneel to standing
 *            and brings the halberd up. Deliberately slow; this is the moment the player is
 *            meant to notice and back away.
 *   ACTIVE   a heavy, slightly stiff walk with a two-handed overhead swing.
 *   CRUMBLE  it folds back down toward the kneel as it dies.
 */
public class LivingStatueModel<T extends LivingStatueEntity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "living_statue"), "main");

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart rightShin;
    private final ModelPart leftLeg;
    private final ModelPart leftShin;
    private final ModelPart halberd;

    public LivingStatueModel(ModelPart root) {
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.rightShin = this.rightLeg.getChild("right_shin");
        this.leftLeg = root.getChild("left_leg");
        this.leftShin = this.leftLeg.getChild("left_shin");
        this.halberd = this.rightArm.getChild("halberd");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition part = mesh.getRoot();

__PARTS__
        return LayerDefinition.create(mesh, __TW__, __TH__);
    }

    /**
     * The kneeling guard pose from the reference: right knee down, left foot planted, halberd
     * braced. {@code t} blends it in, so 0 is standing and 1 is fully knelt.
     *
     * <p>The whole body drops 8 units as well as the joints bending. Without that the knee ends
     * up hanging 9 units clear of the ground -- the legs fold correctly and the figure kneels in
     * mid-air, which looks worse than not kneeling at all.
     */
    private void kneel(float t) {
        this.body.xRot = __BODY_X__F * t;
        this.head.xRot = __HEAD_X__F * t;
        // The down leg. Its roll is what carries the knee round the tassets, which overhang the
        // hips by four units either side -- a kneel is recognised by seeing the down-knee
        // OUTBOARD of the body, and a knee tucked behind the skirt reads as a squat. The sign
        // matters more than the size: rolling the other way drives the knee ACROSS the midline,
        // which put this leg's boot out past the far hip and swapped the legs over.
        this.rightLeg.xRot = __R_LEG_X__F * t;
        this.rightLeg.zRot = __R_LEG_Z__F * t;
        // Folded to lie flat, so the shin rests along the floor behind the knee.
        this.rightShin.xRot = __R_SHIN_X__F * t;
        // The bracing leg: thigh forward with the knee high, boot planted well ahead. Both of
        // these are positive-bend; a negative shin pitch here hinges the knee backwards.
        this.leftLeg.xRot = __L_LEG_X__F * t;
        this.leftShin.xRot = __L_SHIN_X__F * t;
        // Both hands come to the haft, which is what sells "braced" over "carrying".
        this.rightArm.xRot = __R_ARM_X__F * t;
        this.leftArm.xRot = __L_ARM_X__F * t;
        this.leftArm.zRot = __L_ARM_Z__F * t;
        // Braced diagonally ACROSS the body. The DIAGONAL is the Z roll -- never make it with X,
        // which is the depth axis and tips the haft at the camera like a couched lance. The small
        // X below is a different job: it cancels the arm's own forward pitch, which otherwise
        // leans the shaft's top backwards into the helm. See KNEEL in the generator.
        this.halberd.xRot = __HALBERD_X__F * t;
        this.halberd.zRot = __HALBERD_Z__F * t;
        // Drop the WHOLE figure, not just the torso. head/body/arms/legs are all root-level
        // siblings, so setting body.y alone slid the chest down through the head and shoulders
        // and left them hanging in the air. The size is not a taste question: the hip sits 17
        // above the floor and the thigh is only 8 long, so without roughly this much drop the
        // knee cannot reach the ground however the joints are bent, and the statue kneels in
        // mid-air. build_scripts/generate_living_statue_model.py solves it and re-checks it.
        float drop = __DROP__F * t;
        this.head.y = this.body.y = drop;
        this.rightArm.y = this.leftArm.y = 1.5F + drop;
        this.rightLeg.y = this.leftLeg.y = 17.0F + drop;
    }

    @Override
    public void setupAnim(T e, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        this.head.xRot = headPitch * ((float) Math.PI / 180F);
        this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
        this.body.xRot = 0.0F;
        this.body.yRot = 0.0F;
        this.body.y = 0.0F;
        this.rightArm.xRot = this.leftArm.xRot = 0.0F;
        this.rightArm.zRot = this.leftArm.zRot = 0.0F;
        this.rightLeg.xRot = this.leftLeg.xRot = 0.0F;
        this.rightLeg.zRot = this.leftLeg.zRot = 0.0F;
        this.halberd.xRot = 0.0F;
        this.rightShin.xRot = this.leftShin.xRot = 0.0F;
        this.head.y = this.body.y = 0.0F;
        this.rightArm.y = this.leftArm.y = 1.5F;
        this.rightLeg.y = this.leftLeg.y = 17.0F;
        this.halberd.xRot = this.halberd.zRot = 0.0F;

        float wake = e.getWakeProgress();          // 0 asleep .. 1 fully risen
        if (wake < 1.0F) {
            // Ease out, so it comes up off the knee fast and settles slowly. A linear rise
            // reads as a lift rather than as something heaving itself upright.
            float t = 1.0F - (1.0F - wake) * (1.0F - wake);
            kneel(1.0F - t);
            // A shudder through the rise: stone does not glide upright, it catches and jerks.
            // Sampled off ageInTicks rather than a random so every client sees the same shake.
            float judder = (1.0F - t) * 0.055F;
            this.body.xRot += (float) Math.sin(ageInTicks * 2.7F) * judder;
            this.head.xRot += (float) Math.sin(ageInTicks * 3.9F) * judder;
            this.rightArm.zRot = (float) Math.sin(ageInTicks * 3.1F) * judder * 2.0F;
            this.leftArm.zRot = -this.rightArm.zRot;
            this.head.yRot *= t;
            this.head.xRot *= t;
            return;
        }
        if (e.isCrumbling()) {
            kneel(e.getCrumbleProgress());
            return;
        }

        // Heavy stride: legs at half the usual amplitude, arms barely swinging, because a
        // ton of granite does not stroll.
        this.rightLeg.xRot = (float) Math.cos(limbSwing * 0.6F) * 0.75F * limbSwingAmount;
        this.leftLeg.xRot = (float) Math.cos(limbSwing * 0.6F + Math.PI) * 0.75F * limbSwingAmount;
        this.rightArm.xRot = (float) Math.cos(limbSwing * 0.6F + Math.PI) * 0.35F * limbSwingAmount;
        this.leftArm.xRot = (float) Math.cos(limbSwing * 0.6F) * 0.35F * limbSwingAmount;
        // A trailing bend in the shins, a quarter of the thigh swing. Straight-legged walking
        // reads as a mannequin sliding; even a little knee makes it read as weight.
        this.rightShin.xRot = Math.max(0.0F, -this.rightLeg.xRot * 0.45F);
        this.leftShin.xRot = Math.max(0.0F, -this.leftLeg.xRot * 0.45F);
        this.rightArm.zRot = -0.12F;
        this.leftArm.zRot = 0.12F;
        // Same counter-pitch as the kneel: without it the arm's swing leans the shaft back
        // through the helm while it walks.
        this.halberd.xRot = 0.18F;
        this.halberd.zRot = 0.20F;

        // Idle settle: a slow, shallow breath in the shoulders while it stands its ground.
        // Amplitude is deliberately tiny -- anything you can clearly see stops it reading as
        // stone, and the whole mob depends on the difference between "still" and "alive".
        float idle = (1.0F - limbSwingAmount) * 0.02F;
        this.body.xRot += (float) Math.sin(ageInTicks * 0.06F) * idle;
        this.head.xRot += (float) Math.sin(ageInTicks * 0.05F) * idle;

        int combo = e.getComboIndex();
        if (combo >= 0) {
            chop(combo, e.getSwingProgress(ageInTicks % 1.0F));
        }
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    /**
     * One blow of the three-hit combo. {@code p} runs 0..1 across the whole beat, strike AND
     * the stagger that follows it.
     *
     * <p>Swing 0 falls from the statue's high left down across to its low right, swing 1 mirrors
     * it back, and swing 2 is a two-handed overhead split straight down the middle -- the heavy
     * one that cracks the floor. The two diagonals are the same code with {@code side} flipped;
     * the vertical is that code with {@code side} at zero, which drops every left/right term out
     * and leaves a symmetric chop. Writing it three times over is how they drift apart.
     *
     * <p>Weight comes from the SHAPE of the timing, not the size of the numbers:
     * <ul>
     *   <li>the wind-up eases OUT, so the halberd hangs at the top of the backswing. That pause
     *       is what reads as mass, and it is the tell the player reacts to.</li>
     *   <li>the strike is squared, so it accelerates into the target instead of gliding.</li>
     *   <li>it does not stop dead on impact -- the follow-through carries past, then the knees
     *       give and the body rocks while it hauls the thing back up. A blow that snaps back to
     *       idle reads as weightless however hard the swing looked.</li>
     * </ul>
     */
    private void chop(int index, float p) {
        float side = index == 0 ? 1.0F : (index == 1 ? -1.0F : 0.0F);
        boolean heavy = index == 2;
        float power = heavy ? __POWER_HEAVY__F : 1.0F;
        float windEnd = heavy ? __WIND_END_HEAVY__F : __WIND_END__F;
        float hitEnd = heavy ? __HIT_END_HEAVY__F : __HIT_END__F;

        float wind = clamp01(p / windEnd);
        wind = 1.0F - (1.0F - wind) * (1.0F - wind);          // ease out: hang at the top
        float hit = clamp01((p - windEnd) / (hitEnd - windEnd));
        hit = hit * hit;                                       // accelerate, because gravity
        float rec = clamp01((p - hitEnd) / (1.0F - hitEnd));

        float up = wind * (1.0F - hit);
        float down = hit;
        // The struck pose unwinds over the stagger rather than snapping back.
        float hold = 1.0F - rec * rec;

        this.rightArm.xRot = (__R_ARM_UP__F * up + __R_ARM_DOWN__F * down) * power * hold;
        this.leftArm.xRot = (__L_ARM_UP__F * up + __L_ARM_DOWN__F * down) * power * hold;
        this.rightArm.zRot = (__R_ARM_Z_UP__F * up + __R_ARM_Z_DOWN__F * down)
                * side * power * hold;
        this.leftArm.zRot = (__L_ARM_Z_UP__F * up + __L_ARM_Z_DOWN__F * down)
                * side * power * hold;
        this.body.xRot = (__BODY_X_UP__F * up + __BODY_X_DOWN__F * down) * power * hold;
        this.head.xRot = (__HEAD_X_UP__F * up + __HEAD_X_DOWN__F * down) * power * hold;
        // The head turns with the arc. body.yRot would be the obvious place to twist instead,
        // and it is wrong: body and the arms are siblings, so yawing the torso leaves the
        // pauldrons behind and tears the shoulders open. The head pivots on its own neck.
        this.head.yRot = (__HEAD_Y_UP__F * up + __HEAD_Y_DOWN__F * down) * side * hold;
        // Pitched further forward the harder it swings. This is what keeps a 38-unit shaft out
        // of the statue while the arm is crossing its own chest.
        this.halberd.xRot = __HALBERD_X_BASE__F + __HALBERD_X_DOWN__F * down * hold;
        this.halberd.zRot = (__HALBERD_Z_BASE__F
                + __HALBERD_Z_SWING__F * (up - down) * side) * hold;

        // The stagger. One bump peaking mid-recovery -- the knees take the shock and the body
        // dips over them -- with a decaying shudder on top so it settles rather than stopping.
        // Driven off the swing's own clock, not ageInTicks, so every client sees the same thing.
        float lurch = rec * (1.0F - rec) * 4.0F;
        float shudder = (1.0F - rec)
                * (float) Math.sin(rec * __SHUDDER_FREQ__F) * __SHUDDER_AMP__F * power;
        this.rightLeg.xRot += __LURCH_R_LEG__F * lurch * power;
        this.leftLeg.xRot += __LURCH_L_LEG__F * lurch * power;
        this.rightShin.xRot += __LURCH_R_SHIN__F * lurch * power;
        this.leftShin.xRot += __LURCH_L_SHIN__F * lurch * power;
        this.body.xRot += __LURCH_BODY_X__F * lurch * power + shudder;
        this.head.xRot += __LURCH_HEAD_X__F * lurch - shudder * 0.8F;
        // It sinks under the weight of the recovery. Small -- the legs already sold it.
        float sink = __LURCH_DROP__F * lurch * power;
        this.head.y = this.body.y = sink;
        this.rightArm.y = this.leftArm.y = 1.5F + sink;
        this.rightLeg.y = this.leftLeg.y = 17.0F + sink;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buf, int light, int overlay,
                               int colour) {
        this.head.render(pose, buf, light, overlay, colour);
        this.body.render(pose, buf, light, overlay, colour);
        this.rightArm.render(pose, buf, light, overlay, colour);
        this.leftArm.render(pose, buf, light, overlay, colour);
        this.rightLeg.render(pose, buf, light, overlay, colour);
        this.leftLeg.render(pose, buf, light, overlay, colour);
    }
}
'''
    src = src.replace("__PARTS__", body).replace("__TW__", str(TEX_W)).replace("__TH__", str(TEX_H))
    # The pose lives in KNEEL so that audit_kneel() checks the same numbers the Java runs. Held
    # in two places they drift, and a pose that drifts is one nobody can trust a render of.
    for key, val in list(KNEEL.items()) + list(CHOP.items()):
        src = src.replace("__%s__" % key.upper(), "%.2f" % val)
    left = [tok for tok in src.split("__") if tok.isupper() and tok.isidentifier()]
    if left:
        raise SystemExit("unsubstituted placeholder(s) in the Java template: %s" % left)
    os.makedirs(os.path.dirname(JAVA_OUT), exist_ok=True)
    open(JAVA_OUT, "w", encoding="utf-8").write(src)


def _mul(A, B):
    return [[sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _rot(xr, yr, zr):
    """Minecraft composes a part as Rz @ Ry @ Rx -- ModelPart.translateAndRotate applies zRot,
    then yRot, then xRot. Getting this order wrong silently mixes roll into pitch."""
    cx, sx, cy, sy, cz, sz = (math.cos(xr), math.sin(xr), math.cos(yr),
                              math.sin(yr), math.cos(zr), math.sin(zr))
    return _mul([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]],
                _mul([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]],
                     [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]))


def _down(R):
    """Where a part's own downward axis points once rotated."""
    return [R[i][1] for i in range(3)]


def _leg(hip_x, drop, leg_rot, shin_rot):
    Rl = _rot(*leg_rot)
    dl = _down(Rl)
    knee = [hip_x + KNEE_D * dl[0], HIP_Y + drop + KNEE_D * dl[1], KNEE_D * dl[2]]
    ds = _down(_mul(Rl, _rot(*shin_rot)))
    return knee, [knee[i] + SOLE_D * ds[i] for i in range(3)]


def audit_kneel():
    """Re-solve the dormant pose's contacts from KNEEL and complain if it does not describe a
    body standing on the floor.

    None of this is visible in a Blockbench render taken from one angle, which is exactly how the
    earlier versions survived review: a figure floating half a block reads as 'kneeling' from the
    front, and legs that have swapped sides read as 'splayed'. Numbers do not have that problem.
    """
    k, out = KNEEL, []
    rk, rs = _leg(-3.0, k["drop"], (k["r_leg_x"], 0.0, k["r_leg_z"]), (k["r_shin_x"], 0.0, 0.0))
    lk, ls = _leg(+3.0, k["drop"], (k["l_leg_x"], 0.0, 0.0), (k["l_shin_x"], 0.0, 0.0))

    if abs(rk[1] - FLOOR) > 0.75:
        out.append("kneel: down-knee is %+.2f off the floor (y=%.2f, floor %.1f) -- the statue "
                   "kneels in mid-air" % (rk[1] - FLOOR, rk[1], FLOOR))
    if abs(ls[1] - FLOOR) > 0.75:
        out.append("kneel: bracing sole is %+.2f off the floor (y=%.2f) -- %s"
                   % (ls[1] - FLOOR, ls[1], "buried" if ls[1] > FLOOR else "floating"))
    # A knee is a hinge. Negative relative shin pitch bends it the wrong way, and the walk in
    # setupAnim clamps to >= 0 for the same reason.
    for nm, v in (("right", k["r_shin_x"]), ("left", k["l_shin_x"])):
        if v < 0.0:
            out.append("kneel: %s knee bends backwards (shin xRot %+.2f); knees are hinges"
                       % (nm, v))
    # The down-knee has to come ROUND the skirt. Behind it, the pose reads as a squat.
    if abs(rk[0]) <= TASSET_X:
        out.append("kneel: down-knee at x=%+.2f is inside the tassets (+-%.1f), so it is hidden "
                   "-- a kneel only reads when the knee is outboard of the body"
                   % (rk[0], TASSET_X))
    # Right limbs live at -x and left at +x. If that ever inverts, the legs have crossed.
    if not (rk[0] < 0.0 < lk[0]):
        out.append("kneel: legs have crossed the midline (right knee x=%+.2f, left x=%+.2f)"
                   % (rk[0], lk[0]))
    if min(rk[1], rs[1], lk[1], ls[1]) < FLOOR - 0.75:
        pass  # a raised bracing knee is the point of a kneel; only CONTACTS are checked above
    return out


def _cube_lookup(name):
    for pn, (_p, _piv, cubes) in PARTS.items():
        for c in cubes:
            if c[0] == name:
                return pn, c
    raise KeyError(name)


def _pose_transforms(pose):
    """{part: (world origin, world rotation)} -- the same walk ModelPart does at render time."""
    out = {}
    for pn in ORDER:
        if pn == "root":
            continue
        parent, pivot, _cubes = PARTS[pn]
        p = pose.get(pn, {"rot": [0.0, 0.0, 0.0], "pos": [0.0, 0.0, 0.0]})
        R = _rot(*p["rot"])
        if parent in (None, "root"):
            out[pn] = ([pivot[0], pivot[1] + p["pos"][1], pivot[2]], R)
        else:
            po, pR = out[parent]
            ppiv = PARTS[parent][1]
            d = [pivot[i] - ppiv[i] for i in range(3)]
            out[pn] = ([po[i] + sum(pR[i][k] * d[k] for k in range(3)) for i in range(3)],
                       _mul(pR, R))
    return out


def _inside(tf, cube_name, w):
    part, (_n, org, size, _uv, _i) = _cube_lookup(cube_name)
    origin, R = tf[part]
    piv = PARTS[part][1]
    d = [w[i] - origin[i] for i in range(3)]
    loc = [sum(R[k][i] * d[k] for k in range(3)) + piv[i] for i in range(3)]
    return all(org[i] <= loc[i] <= org[i] + size[i] for i in range(3))


def audit_clearance():
    """Does the halberd ever end up INSIDE the statue?

    It did, in every pose the mob has, and nothing caught it: the shaft is grey, the body is grey,
    and from most angles a weapon buried in a shoulder just looks like a weapon in front of a
    shoulder. It only became obvious once someone rotated the camera. The shaft is 38 units long
    and swings on two nested bones, so 'looks fine from the front' means nothing here -- the only
    honest test is to walk the poses and ask whether the haft's centreline is inside a body cube.
    """
    body = ["chest", "tasset", "helm", "gorget", "r_pauldron", "l_pauldron", "r_upper", "l_upper"]
    span = 2.0 * math.pi / 0.6
    checks = [("dormant", _kneel_pose(_blank(), 1.0))]
    checks += [("wake %.2f" % (i / 4.0), _setup_anim(wake=i / 4.0, age=i * 12.0))
               for i in range(5)]
    checks += [("walk %d" % i, _setup_anim(limb_swing=i / 8.0 * span, limb_amt=1.0, age=i * 3.0))
               for i in range(8)]
    for idx, ticks in enumerate(SWING_TICKS):
        checks += [("swing%d %.2f" % (idx, j / 8.0),
                    _setup_anim(combo=idx, cp=j / 8.0)) for j in range(9)]

    problems = []
    for label, pose in checks:
        tf = _pose_transforms(pose)
        hits = {}
        for hc in ("haft", "blade", "beak", "spike"):
            _pn, (_n, org, size, _uv, _i) = _cube_lookup(hc)
            cx, cz = org[0] + size[0] / 2.0, org[2] + size[2] / 2.0
            for s in range(41):
                y = org[1] + size[1] * s / 40.0
                origin, R = tf["halberd"]
                piv = PARTS["halberd"][1]
                d = [cx - piv[0], y - piv[1], cz - piv[2]]
                w = [origin[i] + sum(R[i][k] * d[k] for k in range(3)) for i in range(3)]
                for bc in body:
                    if _inside(tf, bc, w):
                        hits[bc] = hits.get(bc, 0) + 1
        if hits:
            problems.append("halberd is inside the statue during '%s': %s"
                            % (label, ", ".join("%s (%d samples)" % kv for kv in
                                                sorted(hits.items()))))
    return problems


def audit():
    """The one geometry mistake that is invisible in a 3/4 render: torso cubes overlapping the
    head's own Y range, which boxes the head in and ships a faceless mob."""
    problems = []
    head_lo = min(o[1] for _, o, _, _, _ in PARTS["head"][2])
    head_hi = max(o[1] + s[1] for _, o, s, _, _ in PARTS["head"][2])
    head_x0 = min(o[0] for _, o, _, _, _ in PARTS["head"][2])
    head_x1 = max(o[0] + s[0] for _, o, s, _, _ in PARTS["head"][2])
    # A cube only boxes the head in if it shares the head's X footprint AS WELL AS its Y range.
    # Checking Y alone flagged the pauldrons, which sit a clear 4 units outboard of the helm on
    # either side and could never occlude a face -- and a check that cries wolf gets ignored,
    # which is worse than no check at all.
    for pname in ("body", "right_arm", "left_arm"):
        for cname, org, size, _, _ in PARTS[pname][2]:
            lo, hi = org[1], org[1] + size[1]
            x0, x1 = org[0], org[0] + size[0]
            if lo < head_hi - 2.0 and hi > head_lo and x0 < head_x1 and x1 > head_x0:
                problems.append("%s/%s (y %.1f..%.1f, x %.1f..%.1f) is inside the head's box "
                                "(y %.1f..%.1f, x %.1f..%.1f)"
                                % (pname, cname, lo, hi, x0, x1,
                                   head_lo, head_hi, head_x0, head_x1))

    # The second invisible mistake: two cubes whose UV nets overlap. Nothing warns about it -- the
    # model loads, the texture bakes, and one cube quietly wears a strip of the other's face. Cubes
    # that share a net EXACTLY are how the mirrored limbs are painted once and used twice, so only
    # partial overlaps are wrong.
    nets = []
    for pname in ORDER:
        for cname, _org, size, uv, _inf in PARTS[pname][2]:
            w, hgt, d = size
            nets.append((pname + "/" + cname, uv[0], uv[1], 2 * (d + w), d + hgt))
    for i, (n1, u1, v1, w1, h1) in enumerate(nets):
        for n2, u2, v2, w2, h2 in nets[i + 1:]:
            if (u1, v1, w1, h1) == (u2, v2, w2, h2):
                continue
            if u1 < u2 + w2 and u2 < u1 + w1 and v1 < v2 + h2 and v2 < v1 + h1:
                problems.append("UV net of %s (%d,%d %dx%d) overlaps %s (%d,%d %dx%d)"
                                % (n1, u1, v1, w1, h1, n2, u2, v2, w2, h2))
    return problems


if __name__ == "__main__":
    build_texture()
    build_bbmodel()
    build_java()
    top = min(o[1] for _, o, _, _, _ in PARTS["head"][2])
    # the shins hold the feet now, so measure to those or the figure reports 9 units short
    bot = max(o[1] + s[1] for _, o, s, _, _ in PARTS["right_shin"][2])
    probs = audit() + audit_kneel() + audit_clearance()
    print("living_statue: %d parts, %d cubes, %.1f units tall (%.2f blocks)"
          % (len(ORDER) - 1, sum(len(v[2]) for v in PARTS.values()), bot - top, (bot - top) / 16.0))
    rk, _ = _leg(-3.0, KNEEL["drop"], (KNEEL["r_leg_x"], 0.0, KNEEL["r_leg_z"]),
                 (KNEEL["r_shin_x"], 0.0, 0.0))
    _, ls = _leg(+3.0, KNEEL["drop"], (KNEEL["l_leg_x"], 0.0, 0.0), (KNEEL["l_shin_x"], 0.0, 0.0))
    print("  kneel: down-knee y=%.2f x=%+.2f (floor %.0f, skirt +-%.0f), bracing sole y=%.2f"
          % (rk[1], rk[0], FLOOR, TASSET_X, ls[1]))
    print("  ->", os.path.relpath(BB_OUT, ROOT))
    print("  ->", os.path.relpath(JAVA_OUT, ROOT))
    print("  ->", os.path.relpath(TEX_OUT, ROOT))
    for p in probs:
        print("  PROBLEM:", p)
    raise SystemExit(1 if probs else 0)
