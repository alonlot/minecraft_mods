"""Paint the Inferius entity texture.

Reads the UV layout straight out of ``blockbench_models/inferius.bbmodel`` -
each cube's baked per-face rectangle lives in ``element["faces"][side]["uv"]``
in UV units - so a re-pack in Blockbench only needs a re-run here.

The model's UV space is 128 x 64; the PNG is painted at twice that (256 x 128)
so ribs, sockets and hair strands get half-pixel detail. Minecraft samples UVs
normalised, so the Java ``LayerDefinition.create(mesh, 128, 64)`` is unchanged.

The look: a drowned corpse kept in black water. Pale grey-white skin gone
bluish and livid in the hollows and at the hands and feet, every rib showing,
a sunken belly, dark eye sockets with nothing lit in them, long black wet hair
hanging over the face in strands, and the rags of whatever it drowned in.

    python build_scripts/generate_inferius_texture.py
"""

import json
import os
import random

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL = os.path.join(ROOT, "blockbench_models", "inferius.bbmodel")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                   "textures", "entity", "inferius.png")

S = 2  # painted pixels per UV unit
UV_W, UV_H = 128, 64

# --------------------------------------------------------------------------- #
# palette
# --------------------------------------------------------------------------- #

SKIN = np.array((185, 189, 180), float)
SKIN_HI = np.array((216, 220, 212), float)
SKIN_SHADOW = np.array((132, 141, 150), float)
SKIN_DEEP = np.array((86, 94, 106), float)
LIVID = np.array((116, 114, 132), float)
GRIME = np.array((84, 82, 72), float)

HAIR = np.array((26, 26, 30), float)
HAIR_MID = np.array((40, 43, 50), float)
HAIR_SHEEN = np.array((80, 92, 104), float)

CLOTH = np.array((58, 63, 58), float)
CLOTH_DARK = np.array((32, 36, 34), float)
CLOTH_LIGHT = np.array((84, 90, 82), float)
ALGAE = np.array((60, 74, 52), float)

SOCKET = np.array((22, 23, 27), float)
SOCKET_RIM = np.array((70, 76, 86), float)
PUPIL = np.array((92, 98, 94), float)  # dull, filmed over - never lit
TEETH = np.array((168, 162, 136), float)
MOUTH = np.array((30, 24, 27), float)
NAIL = np.array((58, 56, 54), float)

HAIR_CUBES = {"hair_cap", "hair_back", "hair_left", "hair_right",
              "strand_a", "strand_b", "strand_c"}
CLOTH_CUBES = {"pelvis_cloth", "flap_front", "flap_back", "right_sleeve"}
FINGER_CUBES = {"left_finger_1", "left_finger_2", "left_finger_3",
                "right_finger_1", "right_finger_2", "right_finger_3"}
ARM_CUBES = {"left_arm_cube", "right_arm_cube"}
LEG_CUBES = {"left_leg_cube", "right_leg_cube"}
FOOT_CUBES = {"left_foot", "right_foot"}


def lerp(a, b, t):
    t = np.clip(t, 0.0, 1.0)
    if np.ndim(t):
        t = t[..., None]
    return a + (b - a) * t


def smooth_noise(rng, w, h, cell):
    """Value noise: a coarse random grid blown up with bilinear filtering."""
    gw, gh = max(2, w // cell + 2), max(2, h // cell + 2)
    grid = (rng.random((gh, gw)) * 255).astype(np.uint8)
    img = Image.fromarray(grid, "L").resize((gw * cell, gh * cell), Image.BILINEAR)
    return np.asarray(img, float)[:h, :w] / 255.0


class Face:
    """One face rectangle, painted in local pixel coordinates (y=0 at the top)."""

    def __init__(self, canvas, alpha, rect, rng):
        self.canvas, self.alpha, self.rng = canvas, alpha, rng
        self.x0, self.y0, self.w, self.h = rect
        ys, xs = np.mgrid[0:self.h, 0:self.w]
        self.fx = (xs + 0.5) / self.w
        self.fy = (ys + 0.5) / self.h
        self.xs, self.ys = xs, ys

    @property
    def view(self):
        return self.canvas[self.y0:self.y0 + self.h, self.x0:self.x0 + self.w]

    @property
    def aview(self):
        return self.alpha[self.y0:self.y0 + self.h, self.x0:self.x0 + self.w]

    def fill(self, colour):
        self.view[...] = colour
        self.aview[...] = 255

    def blend(self, mask, colour, strength=1.0):
        m = np.clip(mask * strength, 0, 1)[..., None]
        v = self.view
        v[...] = v * (1 - m) + colour * m

    def px(self, x, y, colour, strength=1.0):
        if 0 <= x < self.w and 0 <= y < self.h:
            c = self.canvas[self.y0 + y, self.x0 + x]
            c[...] = c * (1 - strength) + colour * strength

    def cut(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.alpha[self.y0 + y, self.x0 + x] = 0

    def ragged_bottom(self, depth, min_keep=0):
        """Cut a frayed, uneven bottom edge out of the face (alpha)."""
        for x in range(self.w):
            n = self.rng.integers(min_keep, depth + 1)
            for k in range(n):
                self.cut(x, self.h - 1 - k)


# --------------------------------------------------------------------------- #
# materials
# --------------------------------------------------------------------------- #

def skin(f, dark_bottom=0.0, livid_bottom=0.0, edge=0.35):
    """Mottled wet dead skin with soft edge occlusion and sparse wet sheen."""
    f.fill(SKIN)
    n = smooth_noise(f.rng, f.w, f.h, 3)
    f.blend(n, SKIN_SHADOW, 0.45)
    blotch = smooth_noise(f.rng, f.w, f.h, 5)
    f.blend(np.clip((blotch - 0.62) * 3.0, 0, 1), LIVID, 0.6)
    edge_d = np.minimum(f.fx, 1 - f.fx)
    f.blend(np.clip(1 - edge_d * 6, 0, 1), SKIN_SHADOW, edge)
    if dark_bottom:
        f.blend(f.fy ** 2, SKIN_SHADOW, dark_bottom)
    if livid_bottom:
        f.blend(np.clip((f.fy - 0.55) * 2.2, 0, 1), LIVID, livid_bottom)
    fine = f.rng.random((f.h, f.w))
    f.blend((fine > 0.93).astype(float), SKIN_HI, 0.55)
    f.blend((fine < 0.05).astype(float), SKIN_DEEP, 0.35)
    # water running down: a few faint darker vertical streaks
    for _ in range(max(1, f.w // 6)):
        x = int(f.rng.integers(0, f.w))
        y0 = int(f.rng.integers(0, max(1, f.h // 2)))
        for y in range(y0, min(f.h, y0 + int(f.rng.integers(3, 8)))):
            f.px(x, y, SKIN_SHADOW, 0.35)


def hair(f, fringe=False, ragged=0):
    """Long black hair in wet clumped strands with a cold sheen."""
    f.fill(HAIR)
    for x in range(f.w):
        r = f.rng.random()
        base = HAIR_MID if r < 0.45 else HAIR
        f.view[:, x] = lerp(f.view[:, x], base, 0.8)
        if r > 0.82:
            for y in range(f.h):
                if f.rng.random() < 0.55:
                    f.px(x, y, HAIR_SHEEN, 0.55 + 0.3 * f.rng.random())
    n = smooth_noise(f.rng, f.w, f.h, 2)
    f.blend(n, HAIR, 0.4)
    # drips of sheen near strand tips
    for _ in range(max(1, f.w // 3)):
        f.px(int(f.rng.integers(0, f.w)), int(f.rng.integers(f.h // 2, f.h)), HAIR_SHEEN, 0.7)
    if ragged:
        f.ragged_bottom(ragged)
    if fringe:
        f.ragged_bottom(max(2, f.h // 2), 1)


def cloth(f, ragged=0, holes=0):
    f.fill(CLOTH)
    n = smooth_noise(f.rng, f.w, f.h, 3)
    f.blend(n, CLOTH_DARK, 0.6)
    for x in range(0, f.w, 2):  # coarse weave
        f.view[:, x] = lerp(f.view[:, x], CLOTH_DARK, 0.15)
    f.blend(np.clip((smooth_noise(f.rng, f.w, f.h, 4) - 0.7) * 3, 0, 1), ALGAE, 0.5)
    f.blend(np.clip(f.fy - 0.5, 0, 1), CLOTH_DARK, 0.6)
    # tears: short jagged dark lines with a pale lip above
    for _ in range(max(1, f.w * f.h // 60)):
        x, y = int(f.rng.integers(0, f.w)), int(f.rng.integers(0, f.h))
        for k in range(int(f.rng.integers(2, 5))):
            f.px(x + k, y, CLOTH_DARK, 0.9)
            f.px(x + k, y - 1, CLOTH_LIGHT, 0.5)
            y += int(f.rng.integers(-1, 2))
    for _ in range(holes):
        x, y = int(f.rng.integers(1, max(2, f.w - 2))), int(f.rng.integers(1, max(2, f.h - 3)))
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            f.cut(x + dx, y + dy)
    if ragged:
        f.ragged_bottom(ragged)


def transparent(f):
    f.aview[...] = 0


# --------------------------------------------------------------------------- #
# per-cube details
# --------------------------------------------------------------------------- #

def torso_front(f):
    skin(f, edge=0.5)
    w, h = f.w, f.h
    # collarbones
    for x in range(1, w - 1):
        y = 1 + int(abs(x - w / 2) < 2)
        f.px(x, y, SKIN_HI, 0.6)
        f.px(x, y + 1, SKIN_DEEP, 0.55)
    # ribs: dark groove with a lit edge above, bowing down toward the flanks
    for k, row in enumerate(range(4, int(h * 0.62), 3)):
        for x in range(w):
            off = abs((x + 0.5) / w - 0.5) * 2
            if off < 0.12:
                continue  # sternum
            y = row + int(round(off ** 1.4 * 2.0))
            f.px(x, y, SKIN_DEEP, 0.75)
            f.px(x, y - 1, SKIN_HI, 0.45)
    # sternum ridge
    for y in range(3, int(h * 0.55)):
        f.px(w // 2 - 1, y, SKIN_HI, 0.35)
        f.px(w // 2, y, SKIN_HI, 0.25)
    # caved-in belly under the ribcage and a dark navel
    belly = np.exp(-(((f.fx - 0.5) / 0.32) ** 2 + ((f.fy - 0.78) / 0.16) ** 2))
    f.blend(belly, SKIN_SHADOW, 0.7)
    f.px(w // 2, int(h * 0.82), SKIN_DEEP, 0.9)
    f.px(w // 2 - 1, int(h * 0.82), SKIN_DEEP, 0.5)
    # flank shadows
    f.blend(np.clip(1 - np.minimum(f.fx, 1 - f.fx) * 7, 0, 1), SKIN_DEEP, 0.35)


def torso_back(f):
    skin(f, edge=0.5)
    w, h = f.w, f.h
    for y in range(1, h - 1):  # spine knobs
        f.px(w // 2 - 1, y, SKIN_HI if y % 2 else SKIN_DEEP, 0.6)
        f.px(w // 2, y, SKIN_DEEP if y % 2 else SKIN_HI, 0.4)
    for side in (-1, 1):  # shoulder blades
        cx = w / 2 + side * w * 0.27
        for y in range(2, int(h * 0.42)):
            span = max(0, 3 - (y - 2) // 3)
            for dx in range(-span, span + 1):
                f.px(int(cx + dx), y, SKIN_HI, 0.35)
            f.px(int(cx - side * span), y + 1, SKIN_DEEP, 0.5)
    for row in range(int(h * 0.45), int(h * 0.75), 3):  # back ribs
        for x in range(w):
            if abs((x + 0.5) / w - 0.5) > 0.2:
                f.px(x, row, SKIN_DEEP, 0.4)


def torso_side(f):
    skin(f, edge=0.4)
    for row in range(4, int(f.h * 0.65), 3):
        for x in range(f.w):
            f.px(x, row + (x % 3 == 0), SKIN_DEEP, 0.6)
            f.px(x, row - 1, SKIN_HI, 0.3)


def face(f):
    """The skull's front: hollow sockets, collapsed cheeks, a slack mouth."""
    skin(f, edge=0.3)
    w, h = f.w, f.h  # 14 x 14 at S=2
    # brow shadow under the hairline
    f.blend(np.clip(1.5 - f.fy * 3.4, 0, 1), SKIN_DEEP, 0.75)
    # eye sockets: deep, wide, rimmed in bruise
    for cx in (w * 0.32, w * 0.68):
        d = np.sqrt(((f.xs + 0.5 - cx) / 2.4) ** 2 + ((f.ys + 0.5 - h * 0.47) / 1.7) ** 2)
        f.blend(np.clip(1.9 - d, 0, 1), SOCKET_RIM, 0.8)
        f.blend(np.clip((1.25 - d) * 2, 0, 1), SOCKET, 1.0)
        f.px(int(cx), int(h * 0.47), PUPIL, 0.4)
    # nose: thin lit bridge, dark nostrils
    for y in range(int(h * 0.42), int(h * 0.68)):
        f.px(w // 2 - 1, y, SKIN_HI, 0.45)
        f.px(w // 2, y, SKIN_SHADOW, 0.3)
    f.px(w // 2 - 2, int(h * 0.7), SKIN_DEEP, 0.85)
    f.px(w // 2 + 1, int(h * 0.7), SKIN_DEEP, 0.85)
    # sunken cheeks
    for cx in (w * 0.12, w * 0.88):
        d = np.sqrt(((f.xs + 0.5 - cx) / 2.0) ** 2 + ((f.ys + 0.5 - h * 0.74) / 2.6) ** 2)
        f.blend(np.clip(1.4 - d, 0, 1), SKIN_DEEP, 0.55)
    # cheekbone highlight above the hollow
    for x in list(range(1, 4)) + list(range(w - 4, w - 1)):
        f.px(x, int(h * 0.62), SKIN_HI, 0.5)
    # mouth: dark slit with broken teeth
    my = h - 2
    for x in range(3, w - 3):
        f.px(x, my, MOUTH, 0.95)
        f.px(x, my + 1, MOUTH, 0.7)
        if x % 2 == 0 and f.rng.random() < 0.7:
            f.px(x, my, TEETH, 0.8)


def jaw(f, side):
    skin(f, edge=0.3)
    f.blend(np.ones((f.h, f.w)), SKIN_SHADOW, 0.35)
    if side == "north":
        for x in range(1, f.w - 1):
            f.px(x, 0, MOUTH, 0.9)
            if x % 2 == 1 and f.rng.random() < 0.7:
                f.px(x, 0, TEETH, 0.75)
    if side == "up":
        f.blend(np.ones((f.h, f.w)), MOUTH, 0.8)


def skull_other(f, side):
    if side == "down":
        skin(f)
        f.blend(np.ones((f.h, f.w)), SKIN_DEEP, 0.4)
        return
    hair(f)
    if side in ("east", "west"):
        # the skull side shows skin at the lower front (temple and cheek) only;
        # which end is "front" depends on the face, so do both narrow ends
        cols = [0] if side == "west" else [f.w - 1]
        for x in cols:
            for y in range(f.h // 2, f.h):
                f.px(x, y, SKIN_DEEP, 0.8)


def arm(f, side):
    if side == "up":
        skin(f)
        return
    if side == "down":
        skin(f)
        f.blend(np.ones((f.h, f.w)), LIVID, 0.6)
        return
    skin(f, dark_bottom=0.35, livid_bottom=0.75)
    # elbow: bony lit knob with a shadow under it
    ey = int(f.h * 0.42)
    if side in ("south", "east", "west"):
        for x in range(1, f.w - 1):
            f.px(x, ey, SKIN_HI, 0.5)
            f.px(x, ey + 1, SKIN_DEEP, 0.45)
    # tendons down the forearm
    if side == "north":
        for x in (1, f.w - 2):
            for y in range(int(f.h * 0.5), int(f.h * 0.88)):
                f.px(x, y, SKIN_DEEP, 0.3)
    # wasted bicep shadow
    f.blend(np.exp(-((f.fy - 0.25) / 0.08) ** 2), SKIN_SHADOW, 0.3)
    # wrist crease and knuckles at the bottom
    for x in range(f.w):
        f.px(x, int(f.h * 0.86), SKIN_DEEP, 0.55)
        if x % 2 == 0:
            f.px(x, f.h - 1, SKIN_HI, 0.3)


def finger(f, side):
    skin(f, edge=0.2)
    f.blend(np.ones((f.h, f.w)), LIVID, 0.55)
    f.px(0, 1, SKIN_DEEP, 0.5)
    if side not in ("up", "down"):
        for x in range(f.w):
            f.px(x, f.h - 1, NAIL, 0.9)
            f.px(x, f.h - 2, NAIL, 0.5)
    if side == "down":
        f.fill(NAIL)


def leg(f, side):
    if side in ("up", "down"):
        skin(f)
        f.blend(np.ones((f.h, f.w)), SKIN_DEEP if side == "up" else GRIME, 0.5)
        return
    skin(f, dark_bottom=0.45, livid_bottom=0.6)
    ky = int(f.h * 0.48)
    if side == "north":
        for x in range(1, f.w - 1):  # kneecap
            f.px(x, ky - 1, SKIN_HI, 0.55)
            f.px(x, ky, SKIN_HI, 0.35)
            f.px(x, ky + 1, SKIN_DEEP, 0.6)
        for y in range(ky + 2, f.h - 3):  # shin bone
            f.px(f.w // 2, y, SKIN_HI, 0.3)
    if side == "south":
        for x in range(f.w):
            f.px(x, ky, SKIN_DEEP, 0.5)
    # thigh shadow under the rags, mud up the ankle
    f.blend(np.clip(1 - f.fy * 6, 0, 1), CLOTH_DARK, 0.6)
    f.blend(np.clip((f.fy - 0.84) * 6, 0, 1), GRIME, 0.55)


def foot(f, side):
    skin(f, edge=0.2)
    f.blend(np.ones((f.h, f.w)), LIVID, 0.45)
    f.blend(np.ones((f.h, f.w)), GRIME, 0.3)
    if side == "north":
        for x in range(0, f.w, 2):
            f.px(x, 0, NAIL, 0.6)
        for x in range(1, f.w, 2):
            f.px(x, f.h - 1, SKIN_DEEP, 0.7)
            f.px(x, f.h - 2, SKIN_DEEP, 0.5)
    if side == "up":
        for y in range(0, f.h, 2):
            f.px(f.w // 2, y, SKIN_HI, 0.3)


def neck(f, side):
    skin(f, edge=0.4)
    f.blend(np.ones((f.h, f.w)), SKIN_SHADOW, 0.35)
    if side == "north":
        for y in range(f.h):
            f.px(0, y, SKIN_DEEP, 0.5)
            f.px(f.w - 1, y, SKIN_DEEP, 0.5)
            f.px(f.w // 2, y, SKIN_HI, 0.3)


def paint_cube(canvas, alpha, name, side, rect, rng):
    f = Face(canvas, alpha, rect, rng)
    if name == "torso":
        if side == "north":
            torso_front(f)
        elif side == "south":
            torso_back(f)
        elif side in ("east", "west"):
            torso_side(f)
        else:
            skin(f)
            f.blend(np.ones((f.h, f.w)), SKIN_SHADOW, 0.4)
    elif name == "skull":
        if side == "north":
            face(f)
        else:
            skull_other(f, side)
    elif name == "jaw":
        jaw(f, side)
    elif name == "neck":
        neck(f, side)
    elif name in HAIR_CUBES:
        if side == "down":
            if name in ("strand_a", "strand_b", "strand_c"):
                transparent(f)
            else:
                hair(f)
                f.blend(np.ones((f.h, f.w)), HAIR, 0.6)
        elif side == "up":
            hair(f)
        elif name == "hair_cap":
            hair(f, fringe=(side == "north"), ragged=0 if side == "north" else 2)
        elif name in ("strand_a", "strand_b", "strand_c"):
            hair(f, ragged=3)
        else:
            hair(f, ragged=4)
    elif name in CLOTH_CUBES:
        if side == "down":
            transparent(f)
        elif side == "up":
            cloth(f)
        elif name == "pelvis_cloth":
            cloth(f, ragged=3, holes=1)
        elif name == "right_sleeve":
            cloth(f, ragged=4, holes=1)
        else:
            cloth(f, ragged=4)
    elif name in ARM_CUBES:
        arm(f, side)
    elif name in FINGER_CUBES:
        finger(f, side)
    elif name in LEG_CUBES:
        leg(f, side)
    elif name in FOOT_CUBES:
        foot(f, side)
    else:
        skin(f)


def main():
    with open(MODEL, encoding="utf-8") as fh:
        model = json.load(fh)
    w, h = UV_W * S, UV_H * S
    canvas = np.zeros((h, w, 3), float)
    alpha = np.zeros((h, w), np.uint8)
    seed = 1997
    for element in model["elements"]:
        name = element["name"]
        for side, data in element["faces"].items():
            if data.get("texture") is None:
                continue
            u1, v1, u2, v2 = data["uv"]
            x0, x1 = sorted((u1, u2))
            y0, y1 = sorted((v1, v2))
            rect = (int(round(x0 * S)), int(round(y0 * S)),
                    int(round((x1 - x0) * S)), int(round((y1 - y0) * S)))
            if rect[2] <= 0 or rect[3] <= 0:
                continue
            seed += 1
            paint_cube(canvas, alpha, name, side, rect, np.random.default_rng(seed))
    rgba = np.dstack([np.clip(canvas, 0, 255).astype(np.uint8), alpha])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    Image.fromarray(rgba, "RGBA").save(OUT)
    print("wrote", OUT, rgba.shape)


if __name__ == "__main__":
    random.seed(0)
    main()
