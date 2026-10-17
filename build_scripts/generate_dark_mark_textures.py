"""Textures for the Dark Mark (Morsmordre), drawn procedurally so they can be re-tuned.

Outputs (assets/hogwartsmagic/textures/entity/):
  dark_mark_skull.png   2048x2048 - 4x4 frames (512px) of the smoke skull, looping in time.
                        Straight alpha, drawn with normal translucent blending.
  dark_mark_fx.png      1024x1024 - atlas of the parts DarkMarkRenderer assembles:
        (0,0)    512x512  skull bloom            premultiplied, additive
        (512,0)  256x256  halo                   premultiplied, additive
        (768,0)  256x256  burst flash            premultiplied, additive
        (512,256)256x256  snake head             straight alpha
        (768,256)256x256  smoke puff             straight alpha (white, tinted by vertex colour)
        (0,512)  1024x128 snake body strip       straight alpha, period 512px along x
        (0,640)  1024x128 snake body glow strip  premultiplied, additive, period 512px
        (0,768)  256x256  snake head glow        premultiplied, additive
  dark_mark_vortex.png  1024x1024 - spiral of storm cloud the mark hangs under. Straight alpha.

The film mark is a skull of luminous green smoke - bright mint at the brow and cheekbones, deep emerald
in the hollows, eye sockets and nose almost black - with a jaw hanging open and a serpent pouring out of it.
Everything is built from signed distance fields so the silhouette stays clean, then roughened with
time-periodic noise so 16 frames loop without a seam.

Run:  python build_scripts/generate_dark_mark_textures.py
"""
import os

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets",
                   "hogwartsmagic", "textures", "entity")
RNG = np.random.default_rng(1981)
FRAMES = 16
FRAME = 512

# ---------------------------------------------------------------------------------------------
# noise: value noise periodic in z (time) and optionally in x (for tiling strips)
# ---------------------------------------------------------------------------------------------
_LATTICES = {}


def _lattice(key, shape):
    if key not in _LATTICES:
        _LATTICES[key] = RNG.random(shape).astype(np.float32)
    return _LATTICES[key]


def vnoise(x, y, z, freq, zperiod, xperiod=None, key=0):
    """Value noise at lattice frequency `freq`; z in [0,1) loops over `zperiod` cells."""
    nx = int(xperiod) if xperiod else 256
    lat = _lattice((key, freq, zperiod, nx), (nx, 256, zperiod))
    fx, fy, fz = x * freq, y * freq, z * zperiod
    ix, iy, iz = np.floor(fx).astype(int), np.floor(fy).astype(int), np.floor(fz).astype(int)
    tx, ty, tz = fx - ix, fy - iy, fz - iz
    tx, ty, tz = (t * t * (3 - 2 * t) for t in (tx, ty, tz))
    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (tx if dx else 1 - tx) * (ty if dy else 1 - ty) * (tz if dz else 1 - tz)
                out = out + w * lat[(ix + dx) % nx, (iy + dy) % 256, (iz + dz) % zperiod]
    return out


def fbm(x, y, t, freq=4, octaves=5, zperiod=3, xperiod=None, key=0):
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        xp = xperiod * 2 ** o if xperiod else None
        total = total + amp * vnoise(x, y, t, freq * 2 ** o, zperiod, xp, key * 31 + o)
        norm += amp
        amp *= 0.5
    return total / norm


# ---------------------------------------------------------------------------------------------
# SDF helpers (negative inside)
# ---------------------------------------------------------------------------------------------
def sd_ellipse(px, py, cx, cy, rx, ry, rot=0.0):
    x, y = px - cx, py - cy
    if rot:
        c, s = np.cos(rot), np.sin(rot)
        x, y = c * x + s * y, -s * x + c * y
    return (np.sqrt((x / rx) ** 2 + (y / ry) ** 2) - 1.0) * min(rx, ry)


def sd_box(px, py, cx, cy, hx, hy, r=0.0):
    qx, qy = np.abs(px - cx) - hx + r, np.abs(py - cy) - hy + r
    return np.sqrt(np.maximum(qx, 0) ** 2 + np.maximum(qy, 0) ** 2) + np.minimum(np.maximum(qx, qy), 0) - r


def sd_tri_down(px, py, cx, cy, hw, h):
    """Inverted triangle (point at the bottom) with half-width hw at the top and height h."""
    y = (py - (cy - h / 2)) / h          # 0 at the tip, 1 at the top
    half = hw * np.clip(y, 0, 1)
    d_side = np.abs(px - cx) - half
    d_y = np.maximum(-y, y - 1) * h
    return np.maximum(d_side, d_y) * 0.85


def smin(a, b, k):
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0, 1)
    return b * (1 - h) + a * h - k * h * (1 - h)


def smax(a, b, k):
    return -smin(-a, -b, k)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


# ---------------------------------------------------------------------------------------------
# colour
# ---------------------------------------------------------------------------------------------
PALETTE = np.array([
    [0.00, 6, 26, 12],
    [0.25, 18, 92, 44],
    [0.50, 46, 178, 84],
    [0.72, 120, 236, 140],
    [0.88, 196, 255, 200],
    [1.00, 240, 255, 236],
], dtype=np.float32)


def ramp(v):
    v = np.clip(v, 0, 1)
    out = np.zeros(v.shape + (3,), np.float32)
    for c in range(3):
        out[..., c] = np.interp(v, PALETTE[:, 0], PALETTE[:, c + 1])
    return out


def grid(n, lo=-0.5, hi=0.5):
    a = (np.arange(n, dtype=np.float32) + 0.5) / n * (hi - lo) + lo
    x, y = np.meshgrid(a, -a)          # y up
    return x, y


# ---------------------------------------------------------------------------------------------
# skull
# ---------------------------------------------------------------------------------------------
def skull_sdf(x, y):
    """Front-on skull, jaw dropped open. Returns (solid, holes) SDFs; the mark is solid minus holes."""
    cranium = sd_ellipse(x, y, 0.0, 0.165, 0.315, 0.285)
    face = sd_box(x, y, 0.0, -0.02, 0.235, 0.11, 0.07)
    cheeks = np.minimum(sd_ellipse(x, y, -0.245, -0.035, 0.08, 0.06, 0.35),
                        sd_ellipse(x, y, 0.245, -0.035, 0.08, 0.06, -0.35))
    maxilla = sd_box(x, y, 0.0, -0.135, 0.15, 0.05, 0.035)
    upper = smin(smin(smin(cranium, face, 0.07), cheeks, 0.05), maxilla, 0.05)

    # upper teeth hang off the maxilla
    teeth_u = 1e3
    for i in range(-3, 3):
        tx = (i + 0.5) * 0.046
        teeth_u = np.minimum(teeth_u, sd_box(x, y, tx, -0.188 - 0.004 * abs(i + 0.5), 0.017, 0.03, 0.013))
    upper = smin(upper, teeth_u, 0.012)

    # mandible: U-shaped band hanging below an open mouth, joined to the skull by the rami
    jaw_outer = sd_ellipse(x, y, 0.0, -0.255, 0.185, 0.12)
    jaw_inner = sd_ellipse(x, y, 0.0, -0.235, 0.135, 0.085)
    jaw = smax(jaw_outer, -jaw_inner, 0.015)
    jaw = smax(jaw, y - (-0.225), 0.02)                    # keep the lower half of the ring
    rami = np.minimum(sd_box(x, y, -0.19, -0.165, 0.028, 0.085, 0.02),
                      sd_box(x, y, 0.19, -0.165, 0.028, 0.085, 0.02))
    teeth_l = 1e3
    for i in range(-2, 2):
        tx = (i + 0.5) * 0.045
        teeth_l = np.minimum(teeth_l, sd_box(x, y, tx, -0.298, 0.016, 0.028, 0.012))
    jaw = smin(smin(jaw, rami, 0.03), teeth_l, 0.012)
    solid = smin(upper, jaw, 0.02)

    eyes = np.minimum(sd_ellipse(x, y, -0.125, 0.035, 0.092, 0.074, -0.22),
                      sd_ellipse(x, y, 0.125, 0.035, 0.092, 0.074, 0.22))
    nose = smin(sd_ellipse(x, y, -0.02, -0.07, 0.026, 0.05, -0.35),
                sd_ellipse(x, y, 0.02, -0.07, 0.026, 0.05, 0.35), 0.01)
    holes = np.minimum(eyes, nose)
    return solid, holes


def skull_frame(t, n=FRAME):
    x, y = grid(n)
    # roll the shape gently: a slow warp keeps it legible, a faster fine one roughens the edge
    wx = fbm(x + 3.1, y, t, freq=3, octaves=3, zperiod=2, key=1) - 0.5
    wy = fbm(x, y + 7.7, t, freq=3, octaves=3, zperiod=2, key=2) - 0.5
    fx = fbm(x, y, t, freq=12, octaves=3, zperiod=3, key=3) - 0.5
    fy = fbm(x + 1.3, y, t, freq=12, octaves=3, zperiod=3, key=4) - 0.5
    sx = x + wx * 0.018 + fx * 0.012
    sy = y + wy * 0.018 + fy * 0.012
    solid, holes = skull_sdf(sx, sy)
    shape = smax(solid, -holes, 0.012)

    inside = smoothstep(0.006, -0.010, shape)
    # height field for fake relief: domed cranium, sunken temples, raised cheekbones and brow
    depth = np.clip(-shape / 0.06, 0, 1)
    relief = np.sqrt(depth)
    relief += 0.35 * np.exp(-((sx - 0.0) ** 2 / 0.05 + (sy - 0.2) ** 2 / 0.03))
    relief += 0.25 * (np.exp(-((sx + 0.24) ** 2 + (sy + 0.035) ** 2) / 0.003)
                      + np.exp(-((sx - 0.24) ** 2 + (sy + 0.035) ** 2) / 0.003))
    relief += 0.25 * np.exp(-(sx ** 2 / 0.06 + (sy - 0.10) ** 2 / 0.0015))      # brow ridge
    relief -= 0.25 * (np.exp(-((sx + 0.27) ** 2 + (sy - 0.12) ** 2) / 0.004)
                      + np.exp(-((sx - 0.27) ** 2 + (sy - 0.12) ** 2) / 0.004))  # temples
    relief = gaussian_filter(relief, n / 256)
    gy, gx = np.gradient(relief * 6.0)
    nz = np.ones_like(gx) * (2.0 / n * 40)
    norm = np.sqrt(gx * gx + gy * gy + nz * nz)
    lx, ly, lz = -0.35, 0.55, 0.76
    lambert = np.clip((-gx * lx + gy * ly + nz * lz) / norm, 0, 1)

    smoke = fbm(sx, sy, t, freq=7, octaves=5, zperiod=3, key=5)
    churn = fbm(sx * 1.7, sy * 1.7 - 0.3, t, freq=5, octaves=4, zperiod=4, key=6)

    # hollows: the holes glow faintly from deep inside, darkest just past the rim
    hole_depth = np.clip(-holes / 0.05, 0, 1)
    hole_dark = smoothstep(-0.004, 0.012, -holes)

    lum = 0.22 + 0.42 * lambert + 0.16 * relief
    lum *= 0.55 + 0.75 * smoke ** 1.3
    # smoke streaks curling through the bone
    streak = fbm(sx * 0.6 + wy * 0.4, sy * 2.2, t, freq=6, octaves=4, zperiod=3, key=8)
    lum *= 0.80 + 0.40 * smoothstep(0.35, 0.75, streak)

    density = inside * np.clip(0.62 + 0.55 * churn, 0, 1)
    in_holes = smoothstep(0.004, -0.004, solid) * smoothstep(0.004, -0.006, holes)

    # wisps that boil off the edges and drift upward
    out_d = np.maximum(shape, 0)
    wisp_n = fbm(sx * 1.4, sy * 0.9 - t * 0.0, t, freq=6, octaves=5, zperiod=4, key=7)
    up = smoothstep(-0.2, 0.4, sy)                         # smoke rises: more wisps over the crown
    wisp = np.exp(-out_d / (0.03 + 0.04 * up)) * smoothstep(0.45, 0.78, wisp_n) * (1 - inside)
    wisp += np.exp(-out_d / 0.010) * (1 - inside) * 0.5
    density = np.clip(density + wisp * 0.8, 0, 1)
    lum = np.where(inside > 0.5, lum, 0.30 + 0.30 * wisp_n)
    # the sockets and the nose are near-black voids with a faint ember far back
    lum = lum * (1 - in_holes) + in_holes * (0.03 + 0.10 * hole_depth * churn)
    density = np.maximum(density * (1 - in_holes), in_holes * (0.55 + 0.30 * hole_dark))

    frame_edge = smoothstep(0.5, 0.44, np.maximum(np.abs(x), np.abs(y)))
    alpha = np.clip(density * 1.05, 0, 1) * frame_edge
    rgb = ramp(lum)
    return np.dstack([rgb, alpha * 255]).astype(np.uint8), np.clip(inside * (1 - in_holes) * (0.3 + lum), 0, 1)


# ---------------------------------------------------------------------------------------------
# snake
# ---------------------------------------------------------------------------------------------
def snake_strip(w=1024, h=128, glow=False):
    """Body seen side-on, flowing along +x. Period 512px so the renderer can slide a window over it."""
    u = (np.arange(w, dtype=np.float32) + 0.5) / 512.0           # 0..2, period 1
    v = (np.arange(h, dtype=np.float32) + 0.5) / h * 2 - 1        # -1..1 across the body
    U, V = np.meshgrid(u, v)
    rough = vnoise(U, V * 0.25 + 3, 0.0, 16, 1, xperiod=16, key=11) - 0.5
    edge = np.abs(V) + rough * 0.25
    body = smoothstep(0.95, 0.55, edge)
    roundness = np.sqrt(np.clip(1 - V * V, 0, 1))
    # scales: a diamond lattice, dark seams
    a = U * 24 + V * 2.2
    b = U * 24 - V * 2.2
    seams = np.minimum(np.abs(a - np.round(a)), np.abs(b - np.round(b)))
    scales = smoothstep(0.01, 0.12, seams) * 0.5 + 0.5
    smoke = fbm(U, V * 0.3, 0.0, freq=8, octaves=4, zperiod=1, xperiod=8, key=12)
    lum = (0.20 + 0.50 * roundness + 0.10 * smoothstep(-0.6, 0.6, V)) * (0.6 + 0.4 * scales) * (0.6 + 0.7 * smoke)
    if glow:
        g = smoothstep(1.0, 0.0, np.abs(V)) ** 1.6
        rgb = np.array([40, 210, 90], np.float32) * g[..., None]
        return np.dstack([rgb, g * 255]).astype(np.uint8)
    rgb = ramp(lum)
    alpha = body * (0.85 + 0.15 * smoke)
    return np.dstack([rgb, alpha * 255]).astype(np.uint8)


def snake_head(n=256, glow=False):
    """Serpent head in profile pointing +x, jaws open, neck entering at the left edge's centre."""
    x, y = grid(n, 0.0, 1.0)
    y = y + 1.0 - 0.5                                    # y in -0.5..0.5, x in 0..1
    neck = sd_box(x, y, 0.08, 0.0, 0.12, 0.115, 0.04)
    skull = sd_ellipse(x, y, 0.38, 0.03, 0.26, 0.15, 0.08)
    snout = sd_ellipse(x, y, 0.70, 0.07, 0.20, 0.085, 0.22)  # upper jaw tilted up
    lower = sd_ellipse(x, y, 0.62, -0.09, 0.20, 0.055, -0.38)  # lower jaw dropped
    head = smin(smin(smin(neck, skull, 0.08), snout, 0.06), lower, 0.03)
    mouth = sd_ellipse(x, y, 0.80, -0.025, 0.24, 0.045, -0.08)
    head = smax(head, -mouth, 0.012)
    fangs = np.minimum(sd_tri_down(x, y, 0.78, 0.0, 0.016, 0.075),
                       sd_tri_down(x, y, 0.66, 0.005, 0.014, 0.06))
    head = smin(head, fangs, 0.006)
    if glow:
        m = smoothstep(0.02, -0.02, head)
        g = gaussian_filter(m, n / 28) * 1.4
        g = np.clip(g, 0, 1) * smoothstep(0.5, 0.42, np.maximum(np.abs(x - 0.5), np.abs(y)))
        rgb = np.array([40, 210, 90], np.float32) * g[..., None]
        return np.dstack([rgb, g * 255]).astype(np.uint8)
    inside = smoothstep(0.006, -0.008, head)
    relief = gaussian_filter(np.clip(-head / 0.05, 0, 1), 2)
    gy, gx = np.gradient(relief * 5)
    lam = np.clip((-gx * -0.3 + gy * 0.6 + 0.08) / np.sqrt(gx * gx + gy * gy + 0.0064), 0, 1)
    smoke = fbm(x, y, 0.0, freq=10, octaves=4, zperiod=1, key=13)
    lum = (0.20 + 0.45 * lam + 0.15 * relief) * (0.65 + 0.6 * smoke)
    # eye: a bright slit in a dark socket, set back over the jaw hinge
    socket = sd_ellipse(x, y, 0.48, 0.085, 0.045, 0.028, 0.15)
    slit = sd_ellipse(x, y, 0.48, 0.085, 0.009, 0.024)
    lum = np.where(socket < 0, 0.08, lum)
    lum = np.where(slit < 0, 1.0, lum)
    # brow ridge over the eye
    lum += 0.25 * np.exp(-(((x - 0.47) / 0.07) ** 2 + ((y - 0.125) / 0.012) ** 2))
    rgb = ramp(lum)
    return np.dstack([rgb, inside * 255]).astype(np.uint8)


# ---------------------------------------------------------------------------------------------
# glows, puff, burst, vortex
# ---------------------------------------------------------------------------------------------
def premult(rgb, a):
    return np.dstack([rgb * a[..., None], a * 255]).astype(np.uint8)


def halo(n=256):
    x, y = grid(n)
    r = np.sqrt(x * x + y * y) * 2
    g = np.clip(np.exp(-r * r * 4.5) * 0.9 + np.exp(-r * 2.8) * 0.25, 0, 1) * smoothstep(1.0, 0.85, r)
    return premult(np.array([30, 170, 70], np.float32), g)


def burst(n=256):
    x, y = grid(n)
    r = np.sqrt(x * x + y * y) * 2
    ang = np.arctan2(y, x)
    rays = 0.5 + 0.5 * np.cos(ang * 9) * np.cos(ang * 5 + 1.3)
    g = np.exp(-r * 6) + 0.6 * np.exp(-r * 2.4) * rays ** 3
    g = np.clip(g, 0, 1) * smoothstep(1.0, 0.8, r)
    col = np.array([150, 255, 170], np.float32)
    return premult(col, g)


def puff(n=256):
    x, y = grid(n)
    r = np.sqrt(x * x + y * y) * 2
    nz = fbm(x, y, 0.0, freq=5, octaves=5, zperiod=1, key=21)
    a = smoothstep(1.0, 0.2, r + (nz - 0.5) * 0.7) * (0.55 + 0.45 * nz)
    rgb = np.full(x.shape + (3,), 255, np.float32) * (0.75 + 0.25 * nz)[..., None]
    return np.dstack([rgb, np.clip(a, 0, 1) * 255]).astype(np.uint8)


def vortex(n=1024):
    x, y = grid(n)
    r = np.sqrt(x * x + y * y) * 2 + 1e-4             # 0 centre .. 1 edge
    th = np.arctan2(y, x)
    swirl = th / (2 * np.pi) + 0.55 * np.log(r)       # logarithmic spiral arms
    # sample the noise on the spiral coordinate (periodic in angle via xperiod)
    s = (swirl % 1.0)
    arms = fbm(s, r * 1.8, 0.0, freq=6, octaves=5, zperiod=1, xperiod=6, key=31)
    detail = fbm(s * 3 % 1.0, r * 6, 0.0, freq=4, octaves=4, zperiod=1, xperiod=4, key=32)
    dens = smoothstep(0.35, 0.75, arms * 0.75 + detail * 0.35)
    ring = smoothstep(0.10, 0.30, r) * smoothstep(1.0, 0.70, r)
    a = np.clip(dens * ring * 0.95, 0, 1)
    # lit green from the mark beneath, fading to storm grey outward; arm tops a touch lighter
    lit = np.exp(-((r - 0.18) / 0.22) ** 2)
    base = np.array([34, 38, 40], np.float32)
    green = np.array([40, 120, 64], np.float32)
    rgb = base[None, None, :] * (1 - lit[..., None]) + green[None, None, :] * lit[..., None]
    rgb *= (0.75 + 0.5 * detail)[..., None]
    return np.dstack([np.clip(rgb, 0, 255), a * 255]).astype(np.uint8)


def clean(img):
    """Zero the colour of fully transparent pixels: the additive passes ignore alpha, and linear
    filtering would otherwise pull a neighbouring sprite's hidden colour across a quad's edge."""
    img[img[..., 3] == 0, :3] = 0
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    sheet = np.zeros((FRAME * 4, FRAME * 4, 4), np.uint8)
    bloom_acc = np.zeros((FRAME, FRAME), np.float32)
    for f in range(FRAMES):
        img, bright = skull_frame(f / FRAMES)
        r, c = divmod(f, 4)
        sheet[r * FRAME:(r + 1) * FRAME, c * FRAME:(c + 1) * FRAME] = img
        bloom_acc += bright
        print("frame", f)
    Image.fromarray(clean(sheet), "RGBA").save(os.path.join(OUT, "dark_mark_skull.png"), optimize=True)

    fx = np.zeros((1024, 1024, 4), np.uint8)
    bloom = gaussian_filter(bloom_acc / FRAMES, 14) * 0.9
    x, y = grid(FRAME)
    bloom = np.clip(bloom, 0, 1) * smoothstep(0.5, 0.42, np.maximum(np.abs(x), np.abs(y)))
    fx[0:512, 0:512] = premult(np.array([60, 230, 110], np.float32), bloom)
    fx[0:256, 512:768] = halo()
    fx[0:256, 768:1024] = burst()
    fx[256:512, 512:768] = snake_head()
    fx[256:512, 768:1024] = puff()
    fx[512:640, 0:1024] = snake_strip()
    fx[640:768, 0:1024] = snake_strip(glow=True)
    fx[768:1024, 0:256] = snake_head(glow=True)
    Image.fromarray(clean(fx), "RGBA").save(os.path.join(OUT, "dark_mark_fx.png"), optimize=True)

    Image.fromarray(clean(vortex()), "RGBA").save(os.path.join(OUT, "dark_mark_vortex.png"), optimize=True)
    print("done")


if __name__ == "__main__":
    main()
