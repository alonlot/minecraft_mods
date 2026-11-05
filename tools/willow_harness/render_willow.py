"""Z-buffered, textured, cutout software render of WillowHarness frames.

    python -I render_willow.py frames.json texture.png out.png [az_deg] [names,comma,sep]

Quads come straight from Minecraft's ModelPart polygons posed through LivingEntityRenderer's
transform (world blocks, y up), with their real UVs, so this shows the model as the game builds it.
The active limb's FK polyline (what the server hit-tests) is drawn over it in red.
"""
import json
import math
import sys

import numpy as np
from PIL import Image, ImageDraw

W, H = 520, 560


def look(az, el, dist, target):
    a, e = math.radians(az), math.radians(el)
    eye = np.array([target[0] + dist * math.cos(e) * math.sin(a),
                    target[1] + dist * math.sin(e),
                    target[2] + dist * math.cos(e) * math.cos(a)])
    f = np.array(target) - eye
    f /= np.linalg.norm(f)
    r = np.cross(f, [0, 1, 0]); r /= np.linalg.norm(r)
    u = np.cross(r, f)
    return eye, r, u, f


def project(P, cam, fov=38.0):
    eye, r, u, f = cam
    d = P - eye
    x, y, z = d @ r, d @ u, d @ f
    s = (H * 0.5) / math.tan(math.radians(fov) * 0.5)
    return np.stack([W * 0.5 + x / z * s, H * 0.5 - y / z * s, z], axis=-1)


def render(frame, tex, az, el=14.0, dist=27.0, target=(0, 5.0, 0)):
    cam = look(az, el, dist, target)
    th, tw = tex.shape[:2]
    img = np.zeros((H, W, 3), dtype=np.float32)
    # sky gradient
    for yy in range(H):
        t = yy / H
        img[yy, :] = (0.62 + 0.2 * t, 0.72 + 0.12 * t, 0.86 - 0.05 * t)
    zbuf = np.full((H, W), np.inf, dtype=np.float32)

    light = np.array([0.35, 0.85, 0.4]); light /= np.linalg.norm(light)

    tris = []
    # ground: a grass disc with block grid
    G = 12
    for gx in range(-G, G):
        for gz in range(-G, G):
            if gx * gx + gz * gz > G * G:
                continue
            c = (0.30, 0.45, 0.20) if (gx + gz) % 2 == 0 else (0.28, 0.42, 0.19)
            p = [np.array(v, dtype=np.float64) for v in
                 ((gx, 0, gz), (gx + 1, 0, gz), (gx + 1, 0, gz + 1), (gx, 0, gz + 1))]
            tris.append((p[0], p[1], p[2], None, c))
            tris.append((p[0], p[2], p[3], None, c))
    for q in frame["quads"]:
        v = np.array(q, dtype=np.float64).reshape(4, 5)
        tris.append((v[0], v[1], v[2], "t", None))
        tris.append((v[0], v[2], v[3], "t", None))

    for a, b, c, kind, col in tris:
        pa, pb, pc = a[:3], b[:3], c[:3]
        n = np.cross(pb - pa, pc - pa)
        nl = np.linalg.norm(n)
        if nl < 1e-9:
            continue
        n /= nl
        shade = 0.55 + 0.45 * abs(float(n @ light))
        S = project(np.stack([pa, pb, pc]), cam)
        if np.any(S[:, 2] <= 0.1):
            continue
        x0, x1 = int(max(0, math.floor(S[:, 0].min()))), int(min(W - 1, math.ceil(S[:, 0].max())))
        y0, y1 = int(max(0, math.floor(S[:, 1].min()))), int(min(H - 1, math.ceil(S[:, 1].max())))
        if x1 < x0 or y1 < y0:
            continue
        xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        (ax, ay, az_), (bx, by, bz), (cx, cy, cz) = S
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-9:
            continue
        w0 = ((by - cy) * (xs - cx) + (cx - bx) * (ys - cy)) / den
        w1 = ((cy - ay) * (xs - cx) + (ax - cx) * (ys - cy)) / den
        w2 = 1 - w0 - w1
        m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not m.any():
            continue
        # perspective-correct
        iz = w0 / az_ + w1 / bz + w2 / cz
        z = 1.0 / iz
        sub = zbuf[y0:y1 + 1, x0:x1 + 1]
        m &= z < sub
        if not m.any():
            continue
        if kind == "t":
            u = (w0 * a[3] / az_ + w1 * b[3] / bz + w2 * c[3] / cz) * z
            v = (w0 * a[4] / az_ + w1 * b[4] / bz + w2 * c[4] / cz) * z
            ui = np.clip((u * tw).astype(int), 0, tw - 1)
            vi = np.clip((v * th).astype(int), 0, th - 1)
            texel = tex[vi, ui]
            m &= texel[..., 3] > 25
            if not m.any():
                continue
            rgb = texel[..., :3] / 255.0 * shade
        else:
            rgb = np.broadcast_to(np.array(col) * shade, xs.shape + (3,))
        sub[m] = z[m]
        img[y0:y1 + 1, x0:x1 + 1][m] = rgb[m]
    out = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    # FK polyline of the active limb: this is the line the server tests contact against
    if frame.get("limb"):
        L = np.array(frame["limb"]).reshape(7, 3)
        S = project(L, cam)
        d = ImageDraw.Draw(out)
        pts = [(float(s[0]), float(s[1])) for s in S]
        d.line(pts, fill=(230, 30, 30), width=3)
        for p in pts:
            d.ellipse([p[0] - 3, p[1] - 3, p[0] + 3, p[1] + 3], fill=(255, 220, 40))
    ImageDraw.Draw(out).text((8, 8), frame["name"], fill=(0, 0, 0))
    return out


if __name__ == "__main__":
    frames = json.load(open(sys.argv[1]))["frames"]
    tex = np.array(Image.open(sys.argv[2]).convert("RGBA"))
    az = float(sys.argv[4]) if len(sys.argv) > 4 else 30.0
    names = sys.argv[5].split(",") if len(sys.argv) > 5 else [f["name"] for f in frames]
    pick = [f for f in frames if f["name"] in names]
    cols = min(4, len(pick))
    rows = (len(pick) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * W, rows * H), (255, 255, 255))
    for i, f in enumerate(pick):
        sheet.paste(render(f, tex, az), ((i % cols) * W, (i // cols) * H))
    sheet.save(sys.argv[3])
    print("wrote", sys.argv[3], len(pick), "frames")
