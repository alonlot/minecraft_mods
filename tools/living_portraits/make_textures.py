"""Build a portrait's puppet textures from Flow images.

  python tools/living_portraits/make_textures.py fat_lady
  python tools/living_portraits/make_textures.py sir_cadogan

Every face variant Flow painted (blink, glances, mouth shapes, moods) is aligned onto the base painting with
an ECC affine fit around the face, colour-matched, and saved as an RGBA overlay whose alpha is a feathered
ellipse over only the part that changed, so stacking overlays on base.png never shows a seam. The Flow frame
is cropped to the canvas shape (1:2 tall or 2:1 wide) and scaled to a power-of-two texture.
"""
import os, sys
import cv2
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FLOW = r"C:\Users\alonl\flow-mcp-out"


def _f(folder, *names):
    return [os.path.join(FLOW, folder, n) for n in names]


# Per portrait. crop = (x0, y0, w, h) in Flow pixels; masks and fit are in Flow pixels too, found by eye on
# face crops with a grid. overlays: name -> (mask, candidate Flow images; the best-aligned one wins).
# look_l = pupils toward the image's LEFT edge (verify on enlarged crops: Flow mixes up left/right).
PORTRAITS = {
    "fat_lady": {
        "base": _f("FatLady-base-2026-11-01", "scene-01-v2.jpeg")[0],
        "crop": (40, 0, 688, 1376), "size": (512, 1024),
        "masks": {"eyes": (383, 440, 100, 48), "mouth": (386, 528, 70, 52), "face": (385, 470, 130, 140)},
        "fit": (230, 330, 570, 620),
        "overlays": {
            "blink": ("eyes", _f("FatLady-variants-2026-11-01", "scene-02-v1.jpeg", "scene-02-v2.jpeg")),
            "look_l": ("eyes", _f("FatLady-variants2-2026-11-01", "scene-04-v2.jpeg")),
            "look_r": ("eyes", _f("FatLady-variants3-2026-11-01", "scene-01-v1.jpeg")),
            "talk_ah": ("mouth", _f("FatLady-variants2-2026-11-01", "scene-01-v1.jpeg", "scene-01-v2.jpeg")),
            "talk_oh": ("mouth", _f("FatLady-variants2-2026-11-01", "scene-02-v1.jpeg", "scene-02-v2.jpeg")),
            "mood_scared": ("face", _f("FatLady-variants2-2026-11-01", "scene-03-v1.jpeg", "scene-03-v2.jpeg")),
            "mood_happy": ("face", _f("FatLady-final-2026-11-01", "scene-01-v1.jpeg", "scene-01-v2.jpeg")),
            "mood_cross": ("face", _f("FatLady-final-2026-11-01", "scene-02-v1.jpeg", "scene-02-v2.jpeg")),
        },
    },
    "sir_cadogan": {
        "base": _f("Cadogan-base-2026-11-01", "scene-01-v1.jpeg")[0],
        "crop": (0, 40, 1376, 688), "size": (1024, 512),
        "masks": {"eyes": (693, 163, 58, 22), "mouth": (693, 216, 46, 28), "face": (693, 188, 82, 80)},
        "fit": (600, 100, 790, 290),
        "overlays": {
            "blink": ("eyes", _f("Cadogan-variants-2026-11-01", "scene-01-v1.jpeg", "scene-01-v2.jpeg")),
            # every glance Flow painted looks to the image's right, so the left one is that glance mirrored
            # about the line between his eyes
            "look_l": ("eyes", _f("Cadogan-variants-2026-11-01", "scene-02-v1.jpeg", "scene-02-v2.jpeg",
                                  "scene-03-v1.jpeg", "scene-03-v2.jpeg"), 693),
            "look_r": ("eyes", _f("Cadogan-variants-2026-11-01", "scene-02-v1.jpeg", "scene-02-v2.jpeg",
                                  "scene-03-v1.jpeg", "scene-03-v2.jpeg")),
            "talk_ah": ("mouth", _f("Cadogan-variants-2026-11-01", "scene-04-v1.jpeg", "scene-04-v2.jpeg")),
            "talk_oh": ("mouth", _f("Cadogan-variants-2026-11-01", "scene-05-v1.jpeg", "scene-05-v2.jpeg")),
            "mood_happy": ("face", _f("Cadogan-variants-2026-11-01", "scene-06-v1.jpeg", "scene-06-v2.jpeg")),
            "mood_cross": ("face", _f("Cadogan-variants-2026-11-01", "scene-07-v1.jpeg", "scene-07-v2.jpeg")),
            "mood_scared": ("face", _f("Cadogan-variants-2026-11-01", "scene-08-v1.jpeg", "scene-08-v2.jpeg")),
        },
    },
}


def ellipse_mask(shape, ellipse):
    cx, cy, rx, ry = ellipse
    m = np.zeros(shape[:2], np.float32)
    cv2.ellipse(m, (cx, cy), (rx, ry), 0, 0, 360, 1.0, -1)
    k = int(max(rx, ry) * 0.5) | 1
    return cv2.GaussianBlur(m, (k, k), 0)


def align(base, var, fit):
    x0, y0, x1, y1 = fit
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


def main(name):
    p = PORTRAITS[name]
    out = os.path.join(ROOT, "src/main/resources/assets/hogwartsmagic/textures/entity/portrait", name)
    os.makedirs(out, exist_ok=True)
    x0, y0, w, h = p["crop"]
    to_canvas = lambda img: cv2.resize(img[y0:y0 + h, x0:x0 + w], p["size"], interpolation=cv2.INTER_AREA)
    base = cv2.imread(p["base"])
    cv2.imwrite(os.path.join(out, "base.png"), to_canvas(base))
    ring = ellipse_mask(base.shape, p["masks"]["face"])
    for overlay, (kind, files, *mirror) in p["overlays"].items():
        found = [f for f in files if os.path.exists(f)]
        if not found:
            print("skip", overlay, "(no Flow image yet)")
            continue
        cc, aligned = max((align(base, cv2.imread(f), p["fit"]) for f in found), key=lambda t: t[0])
        aligned = colour_match(aligned, base, ring)
        if mirror:
            xs = np.clip(2 * mirror[0] - np.arange(base.shape[1]), 0, base.shape[1] - 1)
            aligned = aligned[:, xs]
        alpha = ellipse_mask(base.shape, p["masks"][kind])
        rgba = np.dstack([to_canvas(aligned.astype(np.uint8)), to_canvas((alpha * 255).astype(np.uint8))])
        rgba[rgba[..., 3] == 0, :3] = 0  # invisible pixels compress to nothing
        cv2.imwrite(os.path.join(out, overlay + ".png"), rgba)
        print(f"{overlay}: ecc {cc:.3f}")
    cv2.imwrite(os.path.join(out, "frame.png"), frame_texture())


if __name__ == "__main__":
    main(sys.argv[1])
