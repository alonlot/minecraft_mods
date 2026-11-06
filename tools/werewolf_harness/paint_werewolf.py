"""Paints the werewolf texture onto the model's real UV faces.

    python paint_werewolf.py faces_rest.json faces_quad.json OUT_DIR

faces_*.json come from `run.sh --faces` (rest = bind pose, quad = idle_wolf@0, the all-fours
stance). Every polygon carries its owning bone path, its cube size and its posed corners with UVs,
so materials come from anatomy (bone names) and fur tone from which way the face points when the
wolf is on all fours: dark along the spine, ashen on the belly and inner limbs, grizzled between.

Writes lycan_werewolf.png (512x512, the model's 256 UV space at 2x) and lycan_werewolf_eyes.png
(emissive eye layer, transparent everywhere but the irises).
"""
import json
import math
import sys

import numpy as np
from PIL import Image

S = 512          # texture size
UVS = 256        # model UV space
K = S / UVS      # texels per UV unit
rng = np.random.default_rng(1729)

# ---- palette ------------------------------------------------------------------------------
FUR_DORSAL = np.array([62, 54, 48])
FUR_SIDE = np.array([86, 77, 68])
FUR_VENTRAL = np.array([128, 117, 102])
FUR_MUZZLE = np.array([116, 107, 96])
GRIZZLE = np.array([140, 132, 120])
SKIN = np.array([44, 38, 38])
PAD = np.array([26, 22, 23])
CLAW_ROOT = np.array([24, 21, 20])
CLAW_TIP = np.array([196, 186, 160])
TOOTH = np.array([222, 214, 190])
TOOTH_ROOT = np.array([170, 150, 120])
GUM = np.array([92, 34, 38])
TONGUE = np.array([150, 66, 72])
NOSE = np.array([18, 16, 17])
INNER_EAR = np.array([88, 62, 62])
CLOTH = np.array([104, 72, 46])       # the villager's brown work clothes, torn
CLOTH_DARK = np.array([70, 47, 30])
IRIS = np.array([255, 178, 40])
IRIS_HOT = np.array([255, 226, 120])
PUPIL = np.array([20, 10, 4])


def hash2(x, y, seed):
    h = (x * 374761393 + y * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, y, seed):
    xi, yi = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64)
    xf, yf = x - xi, y - yi
    xf, yf = xf * xf * (3 - 2 * xf), yf * yf * (3 - 2 * yf)
    a = hash2(xi, yi, seed); b = hash2(xi + 1, yi, seed)
    c = hash2(xi, yi + 1, seed); d = hash2(xi + 1, yi + 1, seed)
    return (a * (1 - xf) + b * xf) * (1 - yf) + (c * (1 - xf) + d * xf) * yf


def short(bone):
    parts = bone.split("/")
    return parts[-2] if parts[-1].startswith("cube_r") else parts[-1]


def normal(vs):
    P = np.array([v[:3] for v in vs])
    n = np.cross(P[1] - P[0], P[2] - P[0])
    l = np.linalg.norm(n)
    return n / l if l > 1e-9 else n


def uv_rect(vs):
    us = [v[3] * S for v in vs]
    ws = [v[4] * S for v in vs]
    return min(us), min(ws), max(us), max(ws)


def world_dir_to_uv(vs, d):
    """Least-squares Jacobian world<-uv over the polygon, then map a world direction into uv."""
    P = np.array([v[:3] for v in vs]); U = np.array([[v[3] * S, v[4] * S] for v in vs])
    dP = P[1:] - P[0]; dU = U[1:] - U[0]
    J, *_ = np.linalg.lstsq(dU, dP, rcond=None)      # dU @ J = dP  -> J: 2x3
    g = np.linalg.pinv(J.T) @ d                       # uv step whose world image best matches d
    n = np.linalg.norm(g)
    return g / n if n > 1e-9 else np.array([0.0, 1.0])


def main(rest_path, quad_path, out_dir):
    rest = json.load(open(rest_path))["polys"]
    quad = json.load(open(quad_path))["polys"]
    assert len(rest) == len(quad)

    allP = np.array([v[:3] for p in quad for v in p["v"]])
    jaw_c = np.mean([v[:3] for p in quad if "/jaw" in p["bone"] for v in p["v"]], axis=0)
    tail_c = np.mean([v[:3] for p in quad if "tail_4" in p["bone"] for v in p["v"]], axis=0)
    fwd = jaw_c - tail_c; fwd[1] = 0; fwd /= np.linalg.norm(fwd)
    up = np.array([0.0, 1.0, 0.0])
    snout_tip = max((v[:3] for p in quad if "upperHead" in p["bone"] for v in p["v"]), key=lambda q: q @ fwd)
    tip_f = float(np.asarray(snout_tip) @ fwd)
    head_c = np.mean([v[:3] for p in quad if "/head" in p["bone"] for v in p["v"]], axis=0)

    img = np.zeros((S, S, 4), dtype=np.float64)
    eyes = np.zeros((S, S, 4), dtype=np.uint8)
    yy, xx = np.mgrid[0:S, 0:S]
    xs, ys = xx + 0.5, yy + 0.5

    # big faces first so small detail faces sharing texels win
    order = sorted(range(len(rest)), key=lambda i: -(lambda r: (r[2] - r[0]) * (r[3] - r[1]))(uv_rect(rest[i]["v"])))
    for i in order:
        pr, pq = rest[i], quad[i]
        bone = pr["bone"]; name = short(bone)
        x0, y0, x1, y1 = uv_rect(pr["v"])
        if x1 - x0 < 0.5 or y1 - y0 < 0.5:
            continue
        X0, Y0, X1, Y1 = int(math.floor(x0 + 1e-6)), int(math.floor(y0 + 1e-6)), int(math.ceil(x1 - 1e-6)), int(math.ceil(y1 - 1e-6))
        sl = (slice(Y0, Y1), slice(X0, X1))
        lx, ly = xs[sl] - x0, ys[sl] - y0            # texel centre within the face rect
        fw, fh = x1 - x0, y1 - y0
        nq = normal(pq["v"])
        thin = 0.0 in [round(s, 3) for s in pr["size"]]
        centroid = np.mean([v[:3] for v in pq["v"]], axis=0)
        seed = (hash(bone) & 0xFFFF) + pr["face"] * 7

        # fur flow: down the limb in the standing pose, swept back toward the tail
        flow_w = np.array([0.0, -1.0, 0.0]) - 0.35 * fwd
        flow_w /= np.linalg.norm(flow_w)
        nr = normal(pr["v"])
        if np.linalg.norm(flow_w - (flow_w @ nr) * nr) < 0.45:
            g = np.array([0.0, 1.0])                  # flow runs into the face: fall back to the face's own down
        else:
            g = world_dir_to_uv(pr["v"], flow_w)
        along = (xs[sl] * g[0] + ys[sl] * g[1])
        across = (xs[sl] * -g[1] + ys[sl] * g[0])
        strand = vnoise(across * 0.75, along * 0.16, seed)
        clump = vnoise(across * 0.22, along * 0.07, seed + 99)
        fine = rng.random(lx.shape)
        grz = vnoise(across * 1.0, along * 0.45, seed + 5)
        edge = np.minimum(np.minimum(lx, fw - lx), np.minimum(ly, fh - ly))
        ao = np.where(edge < 1.0, 0.9, 1.0)
        alpha = np.ones(lx.shape)

        def fur(base):
            t = 0.62 + 0.55 * strand + 0.25 * (clump - 0.5)
            t = np.round(t * 5) / 5                  # posterised: reads as pixel fur, not noise
            col = base[None, None, :] * t[..., None]
            gr = (grz > 0.86) & (strand > 0.5)        # grizzled guard hairs, short streaks along the flow
            col[gr] = col[gr] * 0.55 + GRIZZLE * 0.45
            return col

        up_d = float(nq @ up)
        f_d = float(nq @ fwd)
        # continuous: spine dark -> flanks -> ashen belly, so stacked boxes do not band
        if up_d >= 0:
            base = FUR_SIDE + (FUR_DORSAL - FUR_SIDE) * min(1.0, up_d * 1.15)
        else:
            base = FUR_SIDE + (FUR_VENTRAL - FUR_SIDE) * min(1.0, -up_d * 1.25)
        # inner limbs (faces turned toward the body midline) and chest go ashen
        lateral = centroid - head_c
        side_axis = np.cross(up, fwd)
        if any(k in bone for k in ("tigh", "leg", "arm", "Arm")) and abs(float(nq @ side_axis)) > 0.6 \
                and float(nq @ side_axis) * float(centroid @ side_axis) < 0:
            base = base * 0.5 + FUR_VENTRAL * 0.5
        col = fur(base)

        if name == "necklace":
            alpha[:] = 0                              # old bead necklace: gone
        elif name == "tongue":
            t = 0.85 + 0.25 * vnoise(lx * 0.5, ly * 0.5, seed)
            col = TONGUE[None, None, :] * t[..., None]
            col[np.abs(ly - fh / 2) < 0.7] *= 0.75    # centre groove
        elif "Nail" in name and "Ring" not in name:
            # claws: black root to bone-ivory tip along the face's long axis
            if name.startswith("fingerNail"):
                # 1x2 plane: draw a hooked taper with cutout
                tt = ly / fh
                wdt = (1 - tt) * fw * 0.5 + 0.25
                alpha = (np.abs(lx - fw / 2) <= wdt).astype(float)
            else:
                tt = lx / fw if fw >= fh else ly / fh
            tt = np.clip(tt, 0, 1)
            col = (CLAW_ROOT[None, None, :] * (1 - tt[..., None] ** 1.6) + CLAW_TIP[None, None, :] * tt[..., None] ** 1.6)
        elif any(k in name for k in ("inger", "footToe", "nailRing")):
            col = SKIN[None, None, :] * (0.85 + 0.3 * strand[..., None])
            if up_d > 0.3 and "nailRing" not in name:
                col = col * 0.4 + fur(FUR_DORSAL) * 0.6
        elif name in ("soleLeft", "soleRight") and up_d < -0.3:
            col = PAD[None, None, :] * (0.9 + 0.2 * fine[..., None])
        elif ("/jaw" in bone or "upperHead" in bone) and thin and not (
                "/jaw" in bone and pr["size"][1] >= 2 and not (name == "jaw" and pr["cube"] == 3)):
            # (2-tall jaw planes hang under the jaw: cheek tufts, painted as fringes below)
            # teeth planes: a row of fangs with cutout gaps
            lower = "/jaw" in bone
            n_teeth = max(1, int(round(fw / 2.2)))
            ph = (lx / fw * n_teeth) % 1.0
            tri = 1 - np.abs(ph - 0.5) * 2              # 1 at a tooth's centre
            Pv = np.array([v[:3] for v in pr["v"]]); Uv = np.array([[v[3] * S, v[4] * S] for v in pr["v"]])
            J, *_ = np.linalg.lstsq(Uv[1:] - Uv[0], Pv[1:] - Pv[0], rcond=None)
            wy = (xs[sl] - Uv[0][0]) * J[0][1] + (ys[sl] - Uv[0][1]) * J[1][1]
            lo_, hi_ = wy.min(), wy.max()
            h = (wy - lo_) / max(1e-6, hi_ - lo_)      # 0 at the bottom of the plane
            if not lower:
                h = 1 - h                              # upper fangs hang from the gum down
            # along-the-row coordinate: whichever uv axis is the plane's long one
            if fh > fw:
                ph = (ly / fh * max(1, int(round(fh / 2.2)))) % 1.0
                tri = 1 - np.abs(ph - 0.5) * 2
            alpha = (h <= tri * 0.95 + 0.05).astype(float)
            col = TOOTH_ROOT[None, None, :] * (1 - h[..., None]) + TOOTH[None, None, :] * h[..., None]
            gum = h < 0.18
            col[gum] = GUM
            alpha[gum] = 1
        elif "/jaw" in bone and up_d > 0.55:
            col = GUM[None, None, :] * (0.8 + 0.3 * vnoise(lx * 0.6, ly * 0.6, seed)[..., None])
        elif "upperHead" in bone and up_d < -0.55 and float(centroid @ fwd) > float(head_c @ fwd):
            col = GUM[None, None, :] * (0.75 + 0.3 * vnoise(lx * 0.6, ly * 0.6, seed)[..., None])
        elif "upperHead" in bone and float(centroid @ fwd) > tip_f - 0.16 and (f_d > 0.5 or up_d > 0.5) \
                and float(centroid @ fwd) > tip_f - (0.16 if f_d > 0.5 else 0.09):
            col = NOSE[None, None, :] * (0.9 + 0.5 * (fine[..., None] > 0.8))
            if f_d > 0.5:                             # front face: nose on top, lip fur under it
                col = np.where((ly > fh * 0.5)[..., None], fur(FUR_MUZZLE) * 0.8, col)
        elif name.startswith("ear") and f_d > 0.25:
            col = INNER_EAR[None, None, :] * (0.75 + 0.35 * strand[..., None])
            col[edge < 1.0] = FUR_DORSAL
        elif "/jaw" in bone or ("upperHead" in bone and up_d < 0.4 and float(centroid @ fwd) > float(head_c @ fwd)):
            col = fur(FUR_MUZZLE if up_d < 0.45 else FUR_SIDE)
        elif thin:
            # hanging fur fringes: ragged lower edge cut out
            col = fur(base * 0.95)
            cut = fh * (0.55 + 0.45 * vnoise(lx * 0.9, np.zeros_like(lx) + seed, seed))
            alpha = (ly <= cut).astype(float)

        # torn villager clothes: a brown band over the hips and the tops of the thighs
        if name in ("lowerBody", "tighLeft", "tighRight"):
            hgt = centroid[1]
            # band in world height (standing pose): hip region
            Pv = np.array([v[:3] for v in pr["v"]]); Uv = np.array([[v[3] * S, v[4] * S] for v in pr["v"]])
            dP = Pv[1:] - Pv[0]; dU = Uv[1:] - Uv[0]
            J, *_ = np.linalg.lstsq(dU, dP, rcond=None)
            wy = Pv[0][1] + (xs[sl] - Uv[0][0]) * J[0][1] + (ys[sl] - Uv[0][1]) * J[1][1]
            rag = 0.06 * (vnoise(xs[sl] * 0.45, ys[sl] * 0.05, 77) - 0.5) + 0.05 * (fine - 0.5)
            lo_cut = 1.18 + rag * 2.2                 # world y (blocks) where the tatters end
            hi_cut = 1.62
            m = (wy > lo_cut) & (wy < hi_cut)
            if m.any():
                cl = CLOTH[None, None, :] * (0.8 + 0.3 * vnoise(xs[sl] * 0.15, ys[sl] * 0.6, 5)[..., None])
                fold = vnoise(xs[sl] * 0.5, ys[sl] * 0.08, 9) > 0.62
                cl[fold] = CLOTH_DARK
                hem = m & (wy < lo_cut + 0.035)
                cl[hem] = CLOTH_DARK * 0.8
                col = np.where(m[..., None], cl, col)

        col = col * ao[..., None]
        sub = img[sl]
        sub[..., :3] = np.where(alpha[..., None] > 0, col, sub[..., :3])
        sub[..., 3] = np.where(alpha > 0, 255, sub[..., 3] if name not in ("necklace",) else 0)
        if name == "necklace" or (thin and alpha.min() == 0):
            sub[..., 3] = np.where(alpha > 0, 255, 0)
            if name == "necklace":
                sub[..., 3] = 0

    # ---- eyes: amber irises with a slit, on the two brow cubes that carry them ----------------
    for p in rest:
        if p["bone"].endswith(("upperHead/cube_r89", "upperHead/cube_r90")):
            nq = normal(p["v"])
            x0, y0, x1, y1 = uv_rect(p["v"])
            r = (int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1)))
            if (r == (257, 48, 262, 53)) or (r == (162, 268, 167, 273)):
                ex = r[0] + (1 if r[0] == 257 else 1)
                ey = r[1] + 1
                # 3x2 almond, dark rim above
                img[ey - 1, ex - 1:ex + 4, :3] = FUR_DORSAL * 0.5
                for dx in range(3):
                    for dy in range(2):
                        c = IRIS_HOT if dy == 0 and dx != 1 else IRIS
                        img[ey + dy, ex + dx, :3] = c
                        eyes[ey + dy, ex + dx] = (*c, 255)
                img[ey:ey + 2, ex + 1, :3] = PUPIL
                eyes[ey:ey + 2, ex + 1] = (120, 60, 10, 255)

    out = np.clip(img, 0, 255).astype(np.uint8)
    Image.fromarray(out, "RGBA").save(f"{out_dir}/lycan_werewolf.png")
    Image.fromarray(eyes, "RGBA").save(f"{out_dir}/lycan_werewolf_eyes.png")
    print("wrote", out_dir)


if __name__ == "__main__":
    main(*sys.argv[1:4])
