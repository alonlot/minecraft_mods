"""Software preview of Minecraft block models (cuboid elements, single-axis rotation, auto UV).

Run:  python tools/horcrux/preview_model.py OUT.png model_a.json [model_b.json ...]
Each model is drawn from two angles at day light, and lit models also at night (non-emissive faces
darkened, faces with forge_data block_light left bright) to judge the glow.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets")

SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def tex_path(ref, textures):
    while ref.startswith("#"):
        ref = textures[ref[1:]]
    ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    return os.path.join(ASSETS, ns, "textures", path + ".png")


_cache = {}


def load_tex(path):
    if path not in _cache:
        _cache[path] = np.asarray(Image.open(path).convert("RGBA")).astype(np.float32)
    return _cache[path]


def rot_matrix(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def face_frame(name, f, t):
    """Corner A and edges E1, E2 of a face (unrotated), plus a function giving (u, v) from a point."""
    x0, y0, z0 = f
    x1, y1, z1 = t
    if name == "north":
        return (np.array([x0, y0, z0]), np.array([x1 - x0, 0, 0]), np.array([0, y1 - y0, 0]), lambda p: (16 - p[0], 16 - p[1]))
    if name == "south":
        return (np.array([x0, y0, z1]), np.array([x1 - x0, 0, 0]), np.array([0, y1 - y0, 0]), lambda p: (p[0], 16 - p[1]))
    if name == "east":
        return (np.array([x1, y0, z0]), np.array([0, 0, z1 - z0]), np.array([0, y1 - y0, 0]), lambda p: (16 - p[2], 16 - p[1]))
    if name == "west":
        return (np.array([x0, y0, z0]), np.array([0, 0, z1 - z0]), np.array([0, y1 - y0, 0]), lambda p: (p[2], 16 - p[1]))
    if name == "up":
        return (np.array([x0, y1, z0]), np.array([x1 - x0, 0, 0]), np.array([0, 0, z1 - z0]), lambda p: (p[0], p[2]))
    return (np.array([x0, y0, z0]), np.array([x1 - x0, 0, 0]), np.array([0, 0, z1 - z0]), lambda p: (p[0], 16 - p[2]))


NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def render(model, yaw, pitch=30.0, night=False, size=420, scale=20.0):
    textures = model["textures"]
    img = np.zeros((size, size, 3), np.float32)
    img[:] = (24, 26, 30) if not night else (6, 7, 10)
    zbuf = np.full((size, size), -1e9, np.float32)
    cam = rot_matrix("x", pitch) @ rot_matrix("y", yaw)
    centre = np.array([8, 8, 8])
    ys, xs = np.mgrid[0:size, 0:size]
    for el in model["elements"]:
        R = np.eye(3)
        origin = np.zeros(3)
        if "rotation" in el:
            r = el["rotation"]
            R = rot_matrix(r["axis"], r["angle"])
            origin = np.array(r["origin"], float)
        for name, face in el["faces"].items():
            A, E1, E2, uvf = face_frame(name, el["from"], el["to"])

            def world(p):
                return R @ (p - origin) + origin

            n = cam @ (R @ np.array(NORMALS[name], float))
            if n[2] < -1e-6:  # facing away from the camera (camera looks down -z)
                continue
            pA = cam @ (world(A) - centre)
            pE1 = cam @ (R @ E1)
            pE2 = cam @ (R @ E2)
            # Screen: x right, y down.
            a = np.array([size / 2 + pA[0] * scale, size / 2 - pA[1] * scale])
            e1 = np.array([pE1[0] * scale, -pE1[1] * scale])
            e2 = np.array([pE2[0] * scale, -pE2[1] * scale])
            det = e1[0] * e2[1] - e1[1] * e2[0]
            if abs(det) < 1e-6:
                continue
            corners = np.array([a, a + e1, a + e2, a + e1 + e2])
            xmin, ymin = np.floor(corners.min(0)).astype(int)
            xmax, ymax = np.ceil(corners.max(0)).astype(int)
            xmin, ymin = max(xmin, 0), max(ymin, 0)
            xmax, ymax = min(xmax, size - 1), min(ymax, size - 1)
            if xmin > xmax or ymin > ymax:
                continue
            px = xs[ymin:ymax + 1, xmin:xmax + 1] + 0.5 - a[0]
            py = ys[ymin:ymax + 1, xmin:xmax + 1] + 0.5 - a[1]
            s = (px * e2[1] - py * e2[0]) / det
            t = (py * e1[0] - px * e1[1]) / det
            inside = (s >= 0) & (s <= 1) & (t >= 0) & (t <= 1)
            if not inside.any():
                continue
            p3 = A[None, None, :] + s[..., None] * E1[None, None, :] + t[..., None] * E2[None, None, :]
            u, v = uvf((p3[..., 0], p3[..., 1], p3[..., 2]))
            tex = load_tex(tex_path(face["texture"], textures))
            th, tw = tex.shape[:2]
            ui = np.clip((np.asarray(u) / 16 * tw).astype(int), 0, tw - 1)
            vi = np.clip((np.asarray(v) / 16 * th).astype(int), 0, th - 1)
            col = tex[vi, ui]
            depth = pA[2] + s * pE1[2] + t * pE2[2]
            sub = zbuf[ymin:ymax + 1, xmin:xmax + 1]
            ok = inside & (col[..., 3] > 127) & (depth > sub)
            emissive = "forge_data" in face and face["forge_data"].get("block_light", 0) > 0
            shade = 1.0 if (emissive or el.get("shade", True) is False) else SHADE[name]
            light = 1.0 if (emissive or not night) else 0.4
            out = img[ymin:ymax + 1, xmin:xmax + 1]
            out[ok] = col[..., :3][ok] * shade * light
            sub[ok] = depth[ok]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


def main():
    out = sys.argv[1]
    panels = []
    for path in sys.argv[2:]:
        with open(path) as f:
            model = json.load(f)
        lit = any("forge_data" in fc for el in model["elements"] for fc in el["faces"].values())
        panels.append(render(model, 35))
        panels.append(render(model, 215, pitch=18))
        if lit:
            panels.append(render(model, 35, night=True))
    w = sum(p.width for p in panels)
    sheet = Image.new("RGB", (w, panels[0].height))
    x = 0
    for p in panels:
        sheet.paste(p, (x, 0))
        x += p.width
    sheet.save(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
