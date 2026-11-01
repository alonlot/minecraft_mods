"""Build the Fat Lady's puppet textures from Flow images.

  python tools/living_portraits/make_textures.py

Every face variant Flow painted (blink, glances, mouth shapes, moods) is aligned onto the base painting with
an ECC affine fit around the face, colour-matched, and saved as an RGBA overlay whose alpha is a feathered
ellipse over only the part that changed, so stacking overlays on base.png never shows a seam. The 768x1376
Flow frame is cropped to 688x1376 (x 40..728, a 1:2 canvas) and scaled to 512x1024.
"""
import os
import cv2
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src/main/resources/assets/hogwartsmagic/textures/entity/portrait/fat_lady")
FLOW = r"C:\Users\alonl\flow-mcp-out"
BASE = os.path.join(FLOW, "FatLady-base-2026-11-01", "scene-01-v2.jpeg")
CROP_X0, CROP_W, SIZE = 40, 688, (512, 1024)

# (cx, cy, rx, ry) ellipses in Flow pixels (768x1376), found by eye on face crops
MASKS = {"eyes": (383, 440, 100, 48), "mouth": (386, 528, 70, 52), "face": (385, 470, 130, 140)}
FIT = (230, 330, 570, 620)  # region the ECC alignment fits on

V1, V2, V3, FIN = (os.path.join(FLOW, d) for d in
                   ("FatLady-variants-2026-11-01", "FatLady-variants2-2026-11-01",
                    "FatLady-variants3-2026-11-01", "FatLady-final-2026-11-01"))
# overlay name -> (mask, candidate Flow images; the best-aligned one wins). look_l = pupils toward image left.
OVERLAYS = {
    "blink": ("eyes", [f"{V1}/scene-02-v1.jpeg", f"{V1}/scene-02-v2.jpeg"]),
    "look_l": ("eyes", [f"{V2}/scene-04-v2.jpeg"]),
    "look_r": ("eyes", [f"{V3}/scene-01-v1.jpeg"]),
    "talk_ah": ("mouth", [f"{V2}/scene-01-v1.jpeg", f"{V2}/scene-01-v2.jpeg"]),
    "talk_oh": ("mouth", [f"{V2}/scene-02-v1.jpeg", f"{V2}/scene-02-v2.jpeg"]),
    "mood_scared": ("face", [f"{V2}/scene-03-v1.jpeg", f"{V2}/scene-03-v2.jpeg"]),
    "mood_happy": ("face", [f"{FIN}/scene-01-v1.jpeg", f"{FIN}/scene-01-v2.jpeg"]),
    "mood_cross": ("face", [f"{FIN}/scene-02-v1.jpeg", f"{FIN}/scene-02-v2.jpeg"]),
}


def ellipse_mask(shape, kind):
    cx, cy, rx, ry = MASKS[kind]
    m = np.zeros(shape[:2], np.float32)
    cv2.ellipse(m, (cx, cy), (rx, ry), 0, 0, 360, 1.0, -1)
    k = int(max(rx, ry) * 0.5) | 1
    return cv2.GaussianBlur(m, (k, k), 0)


def align(base, var):
    x0, y0, x1, y1 = FIT
    g0 = cv2.cvtColor(base[y0:y1, x0:x1], cv2.COLOR_BGR2GRAY).astype(np.float32)
    g1 = cv2.cvtColor(var[y0:y1, x0:x1], cv2.COLOR_BGR2GRAY).astype(np.float32)
    warp = np.eye(2, 3, dtype=np.float32)
    cc, warp = cv2.findTransformECC(g0, g1, warp, cv2.MOTION_AFFINE,
                                    (cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 200, 1e-6), None, 5)
    warp[:, 2] += warp[:, :2] @ np.array([x0, y0], np.float32) - np.array([x0, y0], np.float32)
    return cc, cv2.warpAffine(var, warp, (base.shape[1], base.shape[0]),
                              flags=cv2.INTER_LINEAR | cv2.WARP_INVERSE_MAP, borderMode=cv2.BORDER_REFLECT)


def colour_match(src, ref, mask):
    w = mask > 0.5
    out = src.astype(np.float32)
    for c in range(3):
        s, r = out[..., c][w], ref[..., c][w].astype(np.float32)
        out[..., c] = (out[..., c] - s.mean()) / (s.std() + 1e-6) * r.std() + r.mean()
    return np.clip(out, 0, 255)


def to_canvas(img):
    return cv2.resize(img[:, CROP_X0:CROP_X0 + CROP_W], SIZE, interpolation=cv2.INTER_AREA)


def frame_texture():
    """64x64 gilded moulding: dark outer edge, bright bevel, carved beads, dark inner lip."""
    t = np.zeros((64, 64, 3), np.float32)
    d = np.minimum(np.minimum(np.arange(64)[None, :], 63 - np.arange(64)[None, :]),
                   np.minimum(np.arange(64)[:, None], 63 - np.arange(64)[:, None])).astype(np.float32)
    gold = np.array([60, 150, 205], np.float32)  # BGR
    shade = 0.45 + 0.75 * np.exp(-((d - 6) / 3.5) ** 2) + 0.35 * np.exp(-((d - 16) / 5) ** 2)
    shade *= 1 + 0.12 * np.sin(np.arange(64)[None, :] * 0.9) * np.sin(np.arange(64)[:, None] * 0.9)
    t[:] = gold * shade[..., None]
    t[d < 2] *= 0.35
    t[d > 26] *= 0.55
    return np.clip(t, 0, 255).astype(np.uint8)


def main():
    os.makedirs(OUT, exist_ok=True)
    base = cv2.imread(BASE)
    cv2.imwrite(os.path.join(OUT, "base.png"), to_canvas(base))
    ring = ellipse_mask(base.shape, "face")
    for name, (kind, files) in OVERLAYS.items():
        found = [f for f in files if os.path.exists(f)]
        if not found:
            print("skip", name, "(no Flow image yet)")
            continue
        cc, aligned = max((align(base, cv2.imread(f)) for f in found), key=lambda t: t[0])
        aligned = colour_match(aligned, base, ring)
        alpha = ellipse_mask(base.shape, kind)
        rgba = np.dstack([to_canvas(aligned.astype(np.uint8)), to_canvas((alpha * 255).astype(np.uint8))])
        rgba[rgba[..., 3] == 0, :3] = 0  # invisible pixels compress to nothing
        cv2.imwrite(os.path.join(OUT, name + ".png"), rgba)
        print(f"{name}: ecc {cc:.3f}")
    cv2.imwrite(os.path.join(OUT, "frame.png"), frame_texture())


if __name__ == "__main__":
    main()
