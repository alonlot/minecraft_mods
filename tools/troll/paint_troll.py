"""Paints troll.png onto the model's real UV faces.

    python tools/troll/paint_troll.py out/faces_rest.json out/faces_zero.json OUT.png

Both dumps come from run.sh faces: rest is the bind pose (light, occlusion, mottling that runs on across
cube seams), zero has every rotation cleared, so a polygon's corners are exact Blockbench-space cube
coordinates and face details can be placed by plain numbers from build_model.js.

Tone is a 0..1 value quantised onto a short hue-shifted ramp per material, so the result is pixel art
with shape-driven banding rather than noise on a flat colour.
"""
import json
import sys

import numpy as np
from PIL import Image

S = 256
PX = 16.0

SKIN = np.array([(52, 50, 42), (70, 68, 54), (90, 87, 68), (110, 106, 82), (130, 124, 96), (150, 142, 112), (170, 160, 130)], float)
MOSS = np.array([(46, 54, 32), (60, 72, 40), (78, 92, 50), (96, 110, 60), (116, 126, 72), (136, 142, 86), (152, 156, 100)], float)
BELLY = np.array([(70, 60, 50), (92, 80, 64), (116, 102, 80), (138, 122, 96), (158, 140, 112), (176, 158, 128), (190, 174, 144)], float)
HIDE = np.array([(38, 28, 20), (54, 40, 28), (72, 54, 36), (90, 68, 46), (108, 84, 56), (126, 100, 68), (142, 116, 82)], float)
ROPE = np.array([(58, 48, 30), (84, 70, 44), (110, 94, 60), (136, 118, 78), (158, 140, 96), (172, 156, 112), (186, 172, 128)], float)
WOOD = np.array([(34, 24, 16), (52, 36, 22), (72, 50, 30), (94, 66, 40), (116, 84, 52), (136, 102, 66), (152, 120, 82)], float)
BONE = np.array([(120, 108, 76), (150, 138, 100), (178, 166, 128), (200, 190, 152), (218, 208, 174), (230, 222, 192), (240, 234, 208)], float)
NAIL = np.array([(40, 32, 22), (58, 46, 32), (78, 64, 44), (98, 82, 58), (118, 100, 74), (136, 118, 90), (150, 134, 106)], float)


def noise3(p, scale, seed):
    """Value noise on a 3D lattice, smooth interpolation; p is (n, 3) in model pixels."""
    q = p / scale + seed * 17.13
    i = np.floor(q).astype(np.int64)
    f = q - i
    f = f * f * (3 - 2 * f)

    def h(a, b, c):
        v = (i[:, 0] + a) * 73856093 ^ (i[:, 1] + b) * 19349663 ^ (i[:, 2] + c) * 83492791 ^ (seed * 2654435761 & 0xFFFFFFF)
        v = (v ^ (v >> 13)) * 1274126177
        return ((v ^ (v >> 16)) & 0xFFFF) / 65535.0

    x0 = h(0, 0, 0) * (1 - f[:, 0]) + h(1, 0, 0) * f[:, 0]
    x1 = h(0, 1, 0) * (1 - f[:, 0]) + h(1, 1, 0) * f[:, 0]
    x2 = h(0, 0, 1) * (1 - f[:, 0]) + h(1, 0, 1) * f[:, 0]
    x3 = h(0, 1, 1) * (1 - f[:, 0]) + h(1, 1, 1) * f[:, 0]
    y0 = x0 * (1 - f[:, 1]) + x1 * f[:, 1]
    y1 = x2 * (1 - f[:, 1]) + x3 * f[:, 1]
    return y0 * (1 - f[:, 2]) + y1 * f[:, 2]


def fbm(p, scale, seed):
    return noise3(p, scale, seed) * 0.6 + noise3(p, scale / 2.0, seed + 1) * 0.28 + noise3(p, scale / 4.0, seed + 2) * 0.12


class Box:
    """A posed cube as origin + three edge vectors, for the occlusion test."""

    def __init__(self, faces, idx):
        # Corners from all six faces; the extremes along the first face's edges give the frame.
        pts = np.array([v[:3] for f in faces for v in f["v"]], float) * PX
        a = np.array(faces[3]["v"][0][:3], float) * PX
        b = np.array(faces[3]["v"][1][:3], float) * PX
        c = np.array(faces[3]["v"][3][:3], float) * PX
        self.ex = b - a
        self.ey = c - a
        n = np.cross(self.ex, self.ey)
        n /= max(np.linalg.norm(n), 1e-9)
        d = (pts - a) @ n
        self.ez = n * (d.max() - d.min())
        self.o = a + n * d.min()
        self.idx = idx

    def inside(self, p):
        def along(e):
            l2 = max(float(e @ e), 1e-9)
            return ((p - self.o) @ e) / l2
        u, v, w = along(self.ex), along(self.ey), along(self.ez)
        return (u > 0.0) & (u < 1.0) & (v > 0.0) & (v < 1.0) & (w > 0.0) & (w < 1.0)


def rasterise(poly_rest, poly_zero):
    """Every texel of one polygon: its pixel index, posed position and zero-pose position."""
    vr = np.array(poly_rest["v"], float)
    vz = np.array(poly_zero["v"], float)
    uv = vr[:, 3:5] * S
    u0, v0 = uv.min(axis=0)
    u1, v1 = uv.max(axis=0)
    iu0, iv0, iu1, iv1 = int(round(u0)), int(round(v0)), int(round(u1)), int(round(v1))
    if iu1 <= iu0 or iv1 <= iv0:
        return None
    us, vs = np.meshgrid(np.arange(iu0, iu1), np.arange(iv0, iv1))
    us, vs = us.ravel(), vs.ravel()
    # Corners map (u, v) bilinearly: solve by which corner holds which extreme.
    s = (us + 0.5 - u0) / (u1 - u0)
    t = (vs + 0.5 - v0) / (v1 - v0)
    corner = {}
    for k in range(4):
        corner[(uv[k, 0] > (u0 + u1) * 0.5, uv[k, 1] > (v0 + v1) * 0.5)] = k

    def lerp(v):
        a = v[corner[(False, False)], :3]
        b = v[corner[(True, False)], :3]
        c = v[corner[(False, True)], :3]
        d = v[corner[(True, True)], :3]
        w = np.stack([(1 - s) * (1 - t), s * (1 - t), (1 - s) * t, s * t], axis=1)
        return (w[:, 0, None] * a + w[:, 1, None] * b + w[:, 2, None] * c + w[:, 3, None] * d) * PX

    p_rest = lerp(vr)
    p_zero = lerp(vz)
    e1 = vr[1, :3] - vr[0, :3]
    e2 = vr[3, :3] - vr[0, :3]
    n = np.cross(e1, e2)
    n /= max(np.linalg.norm(n), 1e-9)
    # The harness uses world axes (+z is the troll's front); Blockbench's are mirrored on x and z.
    bb_zero = p_zero * np.array([-1, 1, -1], float)
    return us, vs, p_rest, bb_zero, n


def main():
    rest = json.load(open(sys.argv[1]))["polys"]
    zero = json.load(open(sys.argv[2]))["polys"]
    out = np.zeros((S, S, 4), np.uint8)

    groups = {}
    for pr, pz in zip(rest, zero):
        groups.setdefault((pr["bone"], pr["cube"]), []).append((pr, pz))
    boxes = {}
    for key, polys in groups.items():
        if "club_loose" not in key[0]:
            boxes[key] = Box([x[0] for x in polys], key)
    box_list = list(boxes.items())

    light = np.array([0.32, 0.86, 0.40])
    light /= np.linalg.norm(light)

    for key, polys in groups.items():
        bone, cube = key
        leaf = bone.split("/")[-1]
        for face, (pr, pz) in enumerate(polys):
            ras = rasterise(pr, pz)
            if ras is None:
                continue
            us, vs, p, b, n = ras
            n_arr = np.tile(n, (len(p), 1))

            # Occlusion: step off the surface and count how often the point is inside another cube.
            occ = np.zeros(len(p))
            for dist, weight in ((0.8, 0.5), (1.8, 0.3), (3.5, 0.2)):
                q = p + n_arr * dist
                hit = np.zeros(len(p), bool)
                for other_key, box in box_list:
                    # The fingers must not bake in the handle's shadow: they are also seen with the club gone.
                    if other_key != key and not (leaf == "fingers_r" and other_key[0].endswith("/club")):
                        hit |= box.inside(q)
                occ += hit * weight
            # Light from above and slightly in front; undersides sit in shade.
            diffuse = 0.5 + 0.5 * float(n @ light)
            height = np.clip(p[:, 1] / 58.0, 0, 1)

            mat = SKIN
            tone = 0.20 + 0.42 * diffuse + 0.20 * height - 0.42 * occ
            mottle = fbm(p, 9.0, 3)
            detail = fbm(p, 3.0, 11)
            tone += (mottle - 0.5) * 0.16 + (detail - 0.5) * 0.07
            colour_mix = np.zeros(len(p))
            mix_ramp = None

            if leaf in ("club",) or leaf == "club_loose":
                mat = WOOD
                # Grain runs along the club's axis (its local y), ringed where cubes change girth.
                grain = fbm(b * np.array([1.0, 0.16, 1.0]), 3.2, 21)
                tone = 0.20 + 0.40 * diffuse - 0.34 * occ + (grain - 0.5) * 0.7
            elif leaf in ("loin_front", "loin_back"):
                mat = HIDE
                tone = 0.26 + 0.34 * diffuse - 0.3 * occ + (fbm(p, 2.6, 31) - 0.5) * 0.6
            elif leaf == "jaw" and cube >= 2:
                mat = BONE
                tone = 0.30 + 0.45 * diffuse - 0.3 * occ
            elif "belly" in bone:
                # Same skin as the rest of it, paling toward the middle of the gut.
                tone += 0.05
                colour_mix = np.clip(1.0 - np.abs(b[:, 0]) / 13.0, 0, 1) * (0.25 + 0.75 * max(0.0, float(n[2])))
                mix_ramp = BELLY
            else:
                # Moss and lichen settle on what faces the sky: shoulders, hump, crown.
                moss = fbm(p, 7.0, 41)
                facing_up = max(0.0, float(n[1]))
                back = np.clip((p[:, 2] * -1 + 2) / 14.0, 0, 1)
                colour_mix = np.clip((moss - 0.62) * 5.0 + facing_up * 0.35 + back * 0.12 * (height > 0.6) - 0.05, 0, 0.8) * (height > 0.55)
                mix_ramp = MOSS

            # Belly folds and skin creases cut straight into the tone, in cube coordinates.
            if "belly" in bone:
                y = b[:, 1]
                tone -= 0.10 * (np.abs(y - 15.6) < 0.6)
                navel = (np.abs(b[:, 0]) < 1.1) & (np.abs(y - 23.0) < 1.1) & (n[2] > 0.5)
                tone[navel] -= 0.34
            if leaf in ("arm_r", "arm_l", "forearm_r", "forearm_l", "leg_r", "leg_l", "shin_r", "shin_l"):
                # Creases that break up the long plain boxes.
                tone -= 0.08 * ((np.abs(b[:, 1] % 9.0 - 4.5) < 0.5) & (detail > 0.55))

            # Warts: pale blisters with a dark ring, sparse, seeded in space so they cross seams.
            if mat is SKIN or mat is BELLY:
                w = fbm(p, 2.2, 53)
                tone += 0.16 * (w > 0.79) - 0.08 * ((w > 0.74) & (w <= 0.79))

            i = np.clip((np.clip(tone, 0, 0.999) * len(mat)).astype(int), 0, len(mat) - 1)
            rgb = mat[i]
            if mix_ramp is not None:
                rgb = rgb * (1 - colour_mix[:, None]) + mix_ramp[i] * colour_mix[:, None]

            # Details by Blockbench coordinates, on the zero pose.
            x, y, z = b[:, 0], b[:, 1], b[:, 2]
            ax = np.abs(x)
            front = n[2] > 0.5  # world +z is the troll's front

            if leaf == "head":
                # Sunken eye sockets under the brow, tiny bright eyes.
                if front:
                    socket = (ax > 1.2) & (ax < 5.5) & (y > 49.5) & (y < 52.0)
                    rgb[socket] = rgb[socket] * 0.42
                    eye = (ax > 2.4) & (ax < 4.4) & (y > 50.2) & (y < 51.8)
                    rgb[eye] = (214, 176, 60)
                    pupil = (ax > 2.9) & (ax < 3.9) & (y > 50.4) & (y < 51.6)
                    rgb[pupil] = (28, 18, 10)
                    # Flat broad nose with wide nostrils.
                    nostril = (ax > 0.7) & (ax < 2.0) & (y > 48.2) & (y < 49.4) & (z < -21.0)
                    rgb[nostril] = (34, 26, 20)
                    # Brow furrows.
                    furrow = (ax < 0.7) & (y > 52) & (z < -20)
                    rgb[furrow] = rgb[furrow] * 0.55
                    # The dark of the mouth above the lower lip.
                    mouth = (y < 47.0) & (z < -19.5) & (ax < 5.0)
                    rgb[mouth] = (38, 24, 22)
            elif leaf == "jaw" and cube < 2:
                if n[1] > 0.5:
                    # Inside the mouth, seen when it opens.
                    rgb[:] = (70, 34, 34)
                elif front:
                    lip = y > 46.0
                    rgb[lip] = rgb[lip] * 0.7
            elif leaf == "jaw":
                # Teeth: stained toward the root.
                root = y < 47.0
                rgb[root] = rgb[root] * np.array([0.72, 0.66, 0.5])
            elif leaf in ("ear_r", "ear_l"):
                if front or n[2] < -0.5:
                    inner = (ax > 7.0) & (ax < 10.5) & (y > 49.8) & (y < 52.2)
                    rgb[inner] = rgb[inner] * 0.8
            elif leaf == "fingers_r":
                # Knuckle lines between the three finger rings; each ring is its own cube.
                ridge = np.abs(y - np.round(y / 3.0) * 3.0 - 0.0) < 0.5
                rgb[ridge] = rgb[ridge] * 0.66
                if cube < 3 and n[2] < -0.5:
                    nail_rows = (x > 14.0) & (x < 20.0)
                    rgb[nail_rows] = rgb[nail_rows] * 1.1
            elif leaf == "hand_l" and cube >= 1:
                tip = y < 8.6 if cube <= 3 else y > 14.5
                rgb[tip] = NAIL[np.clip((tone[tip] * len(NAIL)).astype(int), 0, 6)]
            elif leaf == "hand_r" or leaf == "hand_l":
                knuckle = (np.abs(x - np.round(x / 3.0) * 3.0) < 0.5) & (y < 13.5)
                rgb[knuckle] = rgb[knuckle] * 0.72
            elif "foot_" in leaf and cube >= 1:
                tip = (z < -12.2) & (y < 2.6)
                rgb[tip] = NAIL[np.clip((tone[tip] * len(NAIL)).astype(int), 0, 6)]
            elif "loin" in leaf:
                # Tattered bottom edge, cut per column; a stitched hem at the top.
                r = noise3(np.stack([x, np.zeros(len(x)), z], 1), 1.6, 61)
                bottom = {"loin_front": 4.0, "loin_back": 5.0}[leaf]
                rgb[(y - bottom) < r * 3.0] *= 0.0
                hem = (y > (15.0 if leaf == "loin_front" else 16.0) - 1.4)
                rgb[hem] = rgb[hem] * 0.7
                alpha_cut = (y - bottom) < r * 3.0
                a = np.where(alpha_cut, 0, 255)
                out[vs, us, :3] = np.clip(rgb, 0, 255).astype(np.uint8)
                out[vs, us, 3] = a
                continue
            elif leaf == "body" and cube == 9:
                # The belt: a twist of rope.
                twist = ((x + y * 2.0) % 4.0) < 1.8
                i2 = np.clip((np.clip(0.2 + 0.5 * diffuse + 0.25 * twist, 0, 0.99) * 7).astype(int), 0, 6)
                rgb = ROPE[i2]

            out[vs, us, :3] = np.clip(rgb, 0, 255).astype(np.uint8)
            out[vs, us, 3] = 255

    Image.fromarray(out, "RGBA").save(sys.argv[3])
    print("wrote", sys.argv[3], "covered", int((out[..., 3] > 0).sum()))


if __name__ == "__main__":
    main()
