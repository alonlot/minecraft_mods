"""Build a living card's textures from Flow images.

  python tools/living_cards/make_cards.py merlin albus_dumbledore ...
  python tools/living_cards/make_cards.py --all

For each card, Flow painted a base portrait and three edits of it: the room with the person removed (empty),
eyes closed (blink) and eyes glancing to the image's right (look_r). Every edit is aligned onto the base with an
ECC affine fit and colour-matched. The script writes these textures for CardArt (see
docs/superpowers/specs/2026-11-25-living-wizard-cards-design.md):

  textures/card/<key>/empty.png   the room. It is the base painting everywhere except under the figure, so the walk-out never shows a seam.
  textures/card/<key>/figure.png  the base painting, cut out by the |base - empty| matte (RGBA)
  textures/card/<key>/base.png    the painting
  textures/card/<key>/blink.png   RGBA overlay, feathered over the eyes only
  textures/card/<key>/look_r.png  RGBA overlay, feathered over the eyes only
  textures/card/<key>/look_l.png  look_r with each eye mirrored about its own centre (Flow mixes up left and right)
  cards/<key>.json                eyes / face ellipses in texture pixels (exit_clip is kept if already present)

It also writes a debug sheet per card to tools/living_cards/debug/<key>.png. Check every one: the matte must
cover the whole figure and the eye boxes must sit on the eyes.
"""
import json
import os
import sys

import cv2
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FLOW = r"C:\Users\alonl\flow-mcp-out"
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic")
DEBUG = os.path.join(ROOT, "tools", "living_cards", "debug")
OUT_W, OUT_H = 256, 384


def f(folder, scene, *variants):
    return [os.path.join(FLOW, folder, f"scene-{scene:02d}-v{v}.jpeg") for v in variants]


# key -> base image, then candidate files per edit (the best-aligned candidate wins; empty is checked by eye).
B1, V1 = "Cards-batch1-2026-11-25", "Cards-var1-2026-11-25"
CARDS = {
    "merlin": {"base": f(B1, 1, 1)[0], "empty": f(V1, 1, 1, 2), "blink": f(V1, 2, 1, 2), "look_r": f(V1, 3, 1, 2)},
    "albus_dumbledore": {"base": f(B1, 2, 1)[0], "empty": f(V1, 4, 1, 2), "blink": f(V1, 5, 1, 2), "look_r": f(V1, 6, 1, 2)},
    "godric_gryffindor": {"base": f(B1, 3, 1)[0], "empty": f(V1, 7, 1, 2), "blink": f(V1, 8, 1, 2), "look_r": f(V1, 9, 1, 2)},
    "helga_hufflepuff": {"base": f(B1, 4, 1)[0], "empty": f(V1, 10, 1, 2), "blink": f(V1, 11, 1, 2), "look_r": f(V1, 12, 1, 2)},
    "rowena_ravenclaw": {"base": f(B1, 5, 1)[0], "empty": f(V1, 13, 1, 2), "blink": f(V1, 14, 1, 2), "look_r": f(V1, 15, 1, 2)},
}
B2, V2 = "Cards-batch2-2026-11-25", "Cards-var2-2026-11-25"
for i, (key, v) in enumerate([("salazar_slytherin", 2), ("nicolas_flamel", 1), ("morgana", 2), ("circe", 1), ("agrippa", 3)]):
    CARDS[key] = {"base": f(B2, i + 1, v)[0], "empty": f(V2, 3 * i + 1, 1, 2), "blink": f(V2, 3 * i + 2, 1, 2),
                  "look_r": f(V2, 3 * i + 3, 1, 2)}


def load(path):
    img = cv2.imread(path, cv2.IMREAD_COLOR)
    if img is None:
        raise FileNotFoundError(path)
    return img.astype(np.float32) / 255.0


def crop_2x3(img):
    """Centre-crop to 2:3 (Flow paints 3:4)."""
    h, w = img.shape[:2]
    cw = int(round(h * 2 / 3))
    if cw <= w:
        x0 = (w - cw) // 2
        return img[:, x0:x0 + cw]
    ch = int(round(w * 3 / 2))
    y0 = (h - ch) // 2
    return img[y0:y0 + ch]


def align(base, img, mask=None):
    """Warp img onto base with an ECC affine fit (on grey, optionally masked). Returns (warped, correlation)."""
    g0 = cv2.cvtColor(base, cv2.COLOR_BGR2GRAY)
    g1 = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    warp = np.eye(2, 3, dtype=np.float32)
    crit = (cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 200, 1e-6)
    m = None if mask is None else (mask > 0.5).astype(np.uint8)
    try:
        cc, warp = cv2.findTransformECC(g0, g1, warp, cv2.MOTION_AFFINE, crit, m, 5)
    except cv2.error:
        cc = -1.0
    h, w = base.shape[:2]
    out = cv2.warpAffine(img, warp, (w, h), flags=cv2.INTER_LINEAR + cv2.WARP_INVERSE_MAP, borderMode=cv2.BORDER_REFLECT)
    return out, float(cc)


def colour_match(src, ref, mask):
    """Match src's per-channel mean and std to ref inside mask."""
    m = mask > 0.5
    if m.sum() < 100:
        return src
    out = src.copy()
    for c in range(3):
        s, r = src[..., c][m], ref[..., c][m]
        out[..., c] = (src[..., c] - s.mean()) * (r.std() / max(s.std(), 1e-4)) + r.mean()
    return np.clip(out, 0, 1)


def diff_map(a, b):
    la = cv2.cvtColor(a, cv2.COLOR_BGR2LAB)
    lb = cv2.cvtColor(b, cv2.COLOR_BGR2LAB)
    d = np.sqrt(((la - lb) ** 2).sum(axis=2))
    return cv2.GaussianBlur(d, (0, 0), 2.0)


def figure_matte(base, empty):
    """The person: the largest connected region where base and empty differ, holes filled, edges feathered."""
    h, w = base.shape[:2]
    d = diff_map(base, empty)
    m = (d > 10.0).astype(np.uint8)
    k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (max(3, w // 60) | 1,) * 2)
    m = cv2.morphologyEx(m, cv2.MORPH_OPEN, k)
    m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (w // 20 | 1,) * 2))
    n, lab, stats, _ = cv2.connectedComponentsWithStats(m)
    if n <= 1:
        raise RuntimeError("no figure found: the empty plate equals the base")
    keep = np.zeros_like(m)
    biggest = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
    for i in range(1, n):
        # the person, and anything else big enough to be part of them (a staff, a held cup) next to them
        if i == biggest or stats[i, cv2.CC_STAT_AREA] > 0.15 * stats[biggest, cv2.CC_STAT_AREA]:
            keep[lab == i] = 1
    # fill holes: flood the outside from the border, everything not reached is inside
    flood = keep.copy()
    pad = np.zeros((h + 2, w + 2), np.uint8)
    cv2.floodFill(flood, pad, (0, 0), 1)
    keep = keep | (1 - flood)
    # the person stands on the bottom edge: wherever the figure already reaches the lower part of the card, carry
    # it down to the bottom, so robes that Flow's empty plate half-kept (dark folds, shadows) leave with him
    # A dark fold inside the robe can match the empty plate, leaving holes. GrabCut on the base painting, seeded from
    # the difference, separates the person from the room by colour as well.
    keep = grabcut_refine(base, d, keep)
    flood = keep.copy()
    pad = np.zeros((h + 2, w + 2), np.uint8)
    cv2.floodFill(flood, pad, (0, 0), 1)
    keep = keep | (1 - flood)
    rows = np.arange(h)[:, None]
    has = keep.any(axis=0)
    lowest = np.where(has, h - 1 - np.argmax(keep[::-1], axis=0), -1)
    reach = has & (lowest > int(h * 0.85))
    keep = keep | ((rows >= lowest[None, :]) & reach[None, :]).astype(np.uint8)
    keep = cv2.dilate(keep, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (w // 120 | 1,) * 2))
    return cv2.GaussianBlur(keep.astype(np.float32), (0, 0), w / 300.0)


def grabcut_refine(base, d, rough):
    """Refine a rough person mask with GrabCut at half size. Strong difference is certain person, no difference far
    from the person is certain room, and everything else is left for GrabCut to decide by colour."""
    h, w = base.shape[:2]
    s = 0.5
    small = cv2.resize((base * 255).astype(np.uint8), (int(w * s), int(h * s)), interpolation=cv2.INTER_AREA)
    ds = cv2.resize(d, small.shape[1::-1], interpolation=cv2.INTER_AREA)
    rs = cv2.resize(rough, small.shape[1::-1], interpolation=cv2.INTER_NEAREST)
    gc = np.full(ds.shape, cv2.GC_PR_BGD, np.uint8)
    gc[rs > 0] = cv2.GC_PR_FGD
    strong = cv2.erode((ds > 24).astype(np.uint8), np.ones((5, 5), np.uint8))
    gc[strong > 0] = cv2.GC_FGD
    near = cv2.dilate(rs, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (small.shape[1] // 12 | 1,) * 2))
    gc[(near == 0) & (ds < 5)] = cv2.GC_BGD
    bgd, fgd = np.zeros((1, 65), np.float64), np.zeros((1, 65), np.float64)
    try:
        cv2.grabCut(small, gc, None, bgd, fgd, 6, cv2.GC_INIT_WITH_MASK)
    except cv2.error:
        return rough
    out = ((gc == cv2.GC_FGD) | (gc == cv2.GC_PR_FGD)).astype(np.uint8)
    # thin props (a staff head against shelves) lose the colour vote but differ strongly from the empty plate
    out |= ((ds > 16) & (rs > 0)).astype(np.uint8)
    out = cv2.morphologyEx(out, cv2.MORPH_CLOSE, np.ones((3, 3), np.uint8))
    n, lab, stats, _ = cv2.connectedComponentsWithStats(out)
    if n > 1:
        big = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
        out = np.isin(lab, [i for i in range(1, n) if i == big or stats[i, cv2.CC_STAT_AREA] > 0.15 * stats[big, cv2.CC_STAT_AREA]]).astype(np.uint8)
    return cv2.resize(out, (w, h), interpolation=cv2.INTER_NEAREST)


def eye_boxes(base, blink, figure):
    """Two eye centres from where the blink differs from the base, inside the figure's upper half."""
    h, w = base.shape[:2]
    d = diff_map(base, blink) * (figure > 0.5)
    d[int(h * 0.6):] = 0
    t = max(8.0, float(d.max()) * 0.35)
    m = (d > t).astype(np.uint8)
    n, lab, stats, cents = cv2.connectedComponentsWithStats(m)
    if n < 2:
        raise RuntimeError("blink shows no change near the eyes")
    order = sorted(range(1, n), key=lambda i: -stats[i, cv2.CC_STAT_AREA])
    a = order[0]
    # the second eye: the next blob at about the same height, beside the first
    b = next((i for i in order[1:] if abs(cents[i][1] - cents[a][1]) < h * 0.03
              and h * 0.01 < abs(cents[i][0] - cents[a][0]) < w * 0.2), None)
    eyes = [cents[a]] + ([cents[b]] if b is not None else [])
    eyes.sort(key=lambda c: c[0])
    xs = [c[0] for c in eyes]
    cy = float(np.mean([c[1] for c in eyes]))
    span = (xs[-1] - xs[0]) if len(xs) == 2 else w * 0.035
    eye_r = max(span * 0.45, w * 0.012)
    return [(float(x), cy) for x in xs], eye_r, span


def ellipse_alpha(shape, cx, cy, rx, ry, feather):
    h, w = shape
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    r = np.sqrt(((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2)
    return np.clip((1.0 - r) / feather + 0.5, 0, 1)


def mirror_each_eye(img, base, eyes, span):
    """Mirror a small feathered ellipse around each eye about its own vertical centre line, so a glance right becomes
    a glance left; outside those ellipses the base shows, so nothing between the eyes (the nose bridge) is touched."""
    out = base.copy()
    h, w = img.shape[:2]
    rx, ry = max(span * 0.3, 4.0), max(span * 0.17, 3.0)
    for ex, ey in eyes:
        x0, x1 = max(0, int(ex - rx * 1.3)), min(w, int(ex + rx * 1.3) + 1)
        # keep the window symmetric about ex so the mirror maps the eye onto itself
        half = min(int(ex) - x0, x1 - 1 - int(ex))
        x0, x1 = int(ex) - half, int(ex) + half + 1
        flipped = img.copy()
        flipped[:, x0:x1] = img[:, x0:x1][:, ::-1]
        a = ellipse_alpha((h, w), ex, ey, rx, ry, 0.4)[..., None]
        out = out * (1 - a) + flipped * a
    return out


def to_out(img):
    return cv2.resize(img, (OUT_W, OUT_H), interpolation=cv2.INTER_AREA)


def save_rgba(path, rgb, alpha):
    a = np.clip(alpha, 0, 1)[..., None]
    out = np.concatenate([np.clip(rgb, 0, 1), a], axis=2)
    cv2.imwrite(path, (to_out(out) * 255 + 0.5).astype(np.uint8))


def save_rgb(path, rgb):
    cv2.imwrite(path, (to_out(np.clip(rgb, 0, 1)) * 255 + 0.5).astype(np.uint8))


def best(base, cands, mask=None):
    scored = [(align(base, crop_2x3(load(p)), mask), p) for p in cands if os.path.exists(p)]
    if not scored:
        raise FileNotFoundError(cands[0])
    (img, cc), path = max(scored, key=lambda s: s[0][1])
    return img, cc, path


def build(key, spec):
    base = crop_2x3(load(spec["base"]))
    h, w = base.shape[:2]
    # empty: align on everything, find the figure, then realign on the background only
    empty, cc0, epath = best(base, spec.get("empty_pick", spec["empty"]))
    fig = figure_matte(base, empty)
    bg = (fig < 0.05).astype(np.float32)
    empty, cc1 = align(base, crop_2x3(load(epath)), bg)
    empty = colour_match(empty, base, bg)
    fig = figure_matte(base, empty)
    grow = cv2.GaussianBlur(cv2.dilate((fig > 0.05).astype(np.uint8), np.ones((w // 40 | 1,) * 2, np.uint8))
                            .astype(np.float32), (0, 0), w / 150.0)
    plate = base * (1 - grow[..., None]) + empty * grow[..., None]

    head = np.zeros((h, w), np.float32)
    ys, xs = np.nonzero(fig > 0.5)
    top = ys.min()
    head[top:top + int(h * 0.35), max(0, xs.min()):xs.max() + 1] = 1
    blink, ccb, bpath = best(base, spec["blink"], head)
    look_r, ccl, lpath = best(base, spec["look_r"], head)
    eyes, eye_r, span = eye_boxes(base, blink, fig)
    ecx = float(np.mean([e[0] for e in eyes]))
    ecy = eyes[0][1]
    erx, ery = span / 2 + eye_r * 1.3, eye_r * 1.1
    eye_alpha = ellipse_alpha((h, w), ecx, ecy, erx, ery, 0.35)
    blink = colour_match(blink, base, eye_alpha > 0.01)
    look_r = colour_match(look_r, base, eye_alpha > 0.01)
    look_l = mirror_each_eye(look_r, base, eyes, span)

    tex = os.path.join(ASSETS, "textures", "card", key)
    os.makedirs(tex, exist_ok=True)
    save_rgb(os.path.join(tex, "base.png"), base)
    save_rgb(os.path.join(tex, "empty.png"), plate)
    save_rgba(os.path.join(tex, "figure.png"), base, fig)
    for name, img in (("blink", blink), ("look_r", look_r), ("look_l", look_l)):
        save_rgba(os.path.join(tex, name + ".png"), img, eye_alpha)

    s = OUT_W / w
    jpath = os.path.join(ASSETS, "cards", key + ".json")
    old = json.load(open(jpath)) if os.path.exists(jpath) else {}
    face_r = max(span * 1.6, eye_r * 4)
    data = {
        "eyes": [round(ecx * s, 1), round(ecy * s, 1), round(erx * s, 1), round(ery * s, 1)],
        "face": [round(ecx * s, 1), round((ecy + face_r * 0.35) * s, 1), round(face_r * s, 1), round(face_r * 1.25 * s, 1)],
        "exit_clip": old.get("exit_clip"),
    }
    os.makedirs(os.path.dirname(jpath), exist_ok=True)
    json.dump(data, open(jpath, "w"), indent=2)

    debug_sheet(key, base, plate, fig, blink, look_l, look_r, eye_alpha, (ecx, ecy, erx, ery))
    print(f"{key}: empty cc {cc0:.3f}->{cc1:.3f} ({os.path.basename(epath)}), blink {ccb:.3f}, look {ccl:.3f}, "
          f"eyes {len(eyes)} at y={ecy:.0f}, figure {100 * (fig > 0.5).mean():.0f}% of the card")


def debug_sheet(key, base, plate, fig, blink, look_l, look_r, eye_alpha, eb):
    os.makedirs(DEBUG, exist_ok=True)
    h, w = base.shape[:2]
    check = ((np.indices((h, w)).sum(axis=0) // 24) % 2).astype(np.float32)[..., None] * 0.3 + 0.5
    cut = base * fig[..., None] + check * (1 - fig[..., None])
    ex, ey, rx, ry = eb
    x0, x1 = int(max(0, ex - rx * 2)), int(min(w, ex + rx * 2))
    y0, y1 = int(max(0, ey - ry * 4)), int(min(h, ey + ry * 4))

    def comp(img):
        a = eye_alpha[..., None]
        return (base * (1 - a) + img * a)[y0:y1, x0:x1]

    crops = [cv2.resize(c, (w // 2, int((y1 - y0) * (w // 2) / (x1 - x0))), interpolation=cv2.INTER_CUBIC)
             for c in (base[y0:y1, x0:x1], comp(blink), comp(look_l), comp(look_r))]
    marked = base.copy()
    cv2.ellipse(marked, (int(ex), int(ey)), (int(rx), int(ry)), 0, 0, 360, (0, 1, 0), 2)
    top = np.concatenate([marked, plate, cut], axis=1)
    row = np.concatenate([np.concatenate(crops[:2], axis=1), np.concatenate(crops[2:], axis=1)], axis=1)
    row = cv2.resize(row, (top.shape[1], int(row.shape[0] * top.shape[1] / row.shape[1])))
    sheet = np.concatenate([top, row], axis=0)
    sheet = cv2.resize(sheet, (1200, int(sheet.shape[0] * 1200 / sheet.shape[1])), interpolation=cv2.INTER_AREA)
    cv2.imwrite(os.path.join(DEBUG, key + ".png"), (sheet * 255).astype(np.uint8))


if __name__ == "__main__":
    keys = list(CARDS) if sys.argv[1:] == ["--all"] else sys.argv[1:]
    for k in keys:
        try:
            build(k, CARDS[k])
        except Exception as e:  # keep going: one bad card should not stop the rest
            print(f"{k}: FAILED {e}")
