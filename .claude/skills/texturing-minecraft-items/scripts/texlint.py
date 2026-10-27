"""Lint Minecraft item sprites against what vanilla actually does.

    python texlint.py a.png b.png ...          lint files
    python texlint.py --audit <assets/modid>   lint every texture an item model uses

Thresholds are calibrated on the 604 vanilla 1.21 16x16 item sprites:
semi-transparent pixels 0 (603 of 604), colours median 10 / p90 15 / max 26,
bounding box median 14x14, outline darker than interior in 87%.
Exit status 1 if any ERROR.
"""
import glob, json, os, sys
from PIL import Image

SLOT_LUM = 139          # vanilla inventory slot #8b8b8b
GUI_PARENTS = ("item/generated", "item/handheld", "minecraft:item/generated", "minecraft:item/handheld",
               "builtin/generated", "minecraft:item/handheld_rod", "item/handheld_rod")


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def is_pot(n):
    return n > 0 and n & (n - 1) == 0


def frames(path, im):
    w, h = im.size
    if h > w and h % w == 0 and os.path.exists(path + ".mcmeta"):
        return [im.crop((0, i * w, w, (i + 1) * w)) for i in range(h // w)], True
    return [im], False


def lint_sprite(im):
    """Findings for one 16x16-ish frame: list of (level, code, message)."""
    out = []
    w, h = im.size
    px = im.load()
    op = [(x, y) for y in range(h) for x in range(w) if px[x, y][3] > 0]
    if not op:
        return [("ERROR", "empty", "no opaque pixels")]
    semi = [p for p in op if px[p][3] < 255]
    if semi:
        out.append(("ERROR", "semi-alpha", f"{len(semi)} semi-transparent pixels (vanilla: 0); "
                    "they render as ghost voxels in hand and halos in the GUI -- snap alpha to 0/255"))
    counts = {}
    for p in op:
        counts[px[p][:3]] = counts.get(px[p][:3], 0) + 1
    n = len(counts)
    scale = (w * h) / 256
    if n > 26 * max(1, scale ** 0.5):
        out.append(("WARN", "too-many-colours", f"{n} colours (vanilla median 10, max 26): ramps are not controlled"))
    if n <= 2:
        out.append(("WARN", "flat", f"only {n} colour(s): no shading at all"))
    singles = sum(1 for v in counts.values() if v == 1)
    if n >= 12 and singles / n > 0.4:
        out.append(("WARN", "noise", f"{singles}/{n} colours are used by a single pixel: "
                    "downscaled-painting or dithering noise, not authored ramps"))
    cols = list(counts)
    near = sum(1 for i in range(len(cols)) for j in range(i + 1, len(cols))
               if max(abs(cols[i][k] - cols[j][k]) for k in range(3)) <= 4)
    if near > 3:
        out.append(("WARN", "near-duplicates", f"{near} colour pairs differ by <=4 levels: merge them into one shade"))
    xs = [p[0] for p in op]; ys = [p[1] for p in op]
    bw, bh = max(xs) - min(xs) + 1, max(ys) - min(ys) + 1
    if max(bw, bh) < 0.5 * w:
        out.append(("WARN", "small", f"bounding box {bw}x{bh} on a {w}x{h} canvas: will read as a speck in the hotbar"))

    def t(x, y):
        return not (0 <= x < w and 0 <= y < h) or px[x, y][3] == 0
    # 8-neighbour: a pixel joined only diagonally is a normal 1px line (vanilla does it everywhere)
    orphans = [(x, y) for x, y in op if all(t(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1) if dx or dy)]
    if orphans:   # vanilla uses 1-2 for sparkles (46 of 604 sprites); more is debris
        out.append(("WARN" if len(orphans) > 2 else "INFO", "orphans", f"{len(orphans)} isolated pixel(s) at {orphans[:4]}: float as loose voxels in hand"))
    edge = [(x, y) for x, y in op if t(x + 1, y) or t(x - 1, y) or t(x, y + 1) or t(x, y - 1)]
    inner = [p for p in op if p not in set(edge)]
    el = sum(lum(px[p]) for p in edge) / len(edge)
    il = sum(lum(px[p]) for p in inner) / len(inner) if inner else el
    if inner and el >= il - 8 and il > 60:
        out.append(("WARN", "no-outline", f"edge lum {el:.0f} >= interior {il:.0f}: no darker silhouette edge"))
    lost = sum(1 for p in edge if abs(lum(px[p]) - SLOT_LUM) < 16)
    if lost / len(edge) > 0.45:
        out.append(("WARN", "slot-contrast", f"{100 * lost // len(edge)}% of the outline is the slot grey: silhouette vanishes in inventory"))
    x0, y0, x1, y1 = min(xs), min(ys), max(xs), max(ys)
    corners = sum(1 for cx, cy in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)) if px[cx, cy][3])
    if bw >= 0.75 * w and bh >= 0.75 * h and len(op) / (bw * bh) > 0.85 and corners >= 3:
        out.append(("WARN", "box-silhouette", f"sprite fills {100 * len(op) // (bw * bh)}% of its {bw}x{bh} box: "
                    "a front-on rectangle reads as a tile or placeholder -- give it a 3/4 or diagonal silhouette"))
    lums = [lum(c) for c in counts]
    if min(lums) > 75 and max(lums) > 150:
        out.append(("WARN", "no-dark", f"darkest pixel lum {min(lums):.0f}: nothing dark enough to hold the "
                    "silhouette on the #8b8b8b slot (fine only for glowing items -- blaze rod style)"))
    if max(lums) < 105:
        out.append(("INFO", "no-light", f"lightest pixel lum {max(lums):.0f}: no highlight to carry it on the dark hotbar"))
    dark, light = min(counts, key=lum), max(counts, key=lum)
    if lum(light) - lum(dark) > 150:
        touch = sum(1 for x, y in op if px[x, y][:3] == dark and any(
            0 <= x + dx < w and 0 <= y + dy < h and px[x + dx, y + dy][3] and px[x + dx, y + dy][:3] == light
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))))
        if touch > 2:
            out.append(("WARN", "harsh-step", f"darkest touches lightest at {touch} pixels: put a transition shade between"))
    black = counts.get((0, 0, 0), 0)
    if black > 4:
        out.append(("INFO", "pure-black", f"{black} #000000 pixels: vanilla outlines with the darkest shade of the "
                    "local hue (7 of 605 items use black)"))
    big = max(counts.values())
    if n <= 5 and big / len(op) > 0.55:
        out.append(("WARN", "placeholder-fill", f"one colour covers {100 * big // len(op)}% of the sprite: flat placeholder fill"))
    return out


def lint_file(path, gui_sprite=True):
    im = Image.open(path)
    w, h = im.size
    out = []
    if im.mode != "RGBA":
        im = im.convert("RGBA")
    frs, animated = frames(path, im)
    fw, fh = frs[0].size
    if not (is_pot(w) and is_pot(h if not animated else fw)):
        out.append(("ERROR", "non-pot", f"{w}x{h} is not a power of two: lowers the mipmap level of the WHOLE block atlas "
                    "(log: 'limits mip level')"))
    if gui_sprite:
        if fw != fh:
            out.append(("ERROR", "non-square", f"{w}x{h} frame is not square and has no .mcmeta animation"))
        if fw > 32:
            out.append(("ERROR", "oversized", f"{fw}x{fh}: item sprites are 16x16 (vanilla) -- {fw * fh // 256}x the atlas "
                        "space, and item/generated builds side quads per pixel edge"))
        elif fw == 32:
            out.append(("WARN", "hd", "32x32: double vanilla resolution, reads as a different art style next to vanilla items"))
        if fw <= 32:
            for i, fr in enumerate(frs):
                for f in lint_sprite(fr):
                    out.append((f[0], f[1], (f"frame {i}: " if animated else "") + f[2]))
                if animated and i == 0:
                    break      # frames share a style; one is enough to judge
    elif w * h > 256 * 256:
        out.append(("WARN", "big-model-texture", f"{w}x{h} model texture"))
    return out, (w, h)


def audit(assets):
    tex_users = {}
    for f in sorted(glob.glob(os.path.join(assets, "models", "item", "**", "*.json"), recursive=True)):
        try:
            j = json.load(open(f, encoding="utf-8"))
        except Exception as e:
            print("BADJSON", f, e)
            continue
        parent = j.get("parent", "")
        gui = parent in GUI_PARENTS and "elements" not in j
        for k, v in (j.get("textures") or {}).items():
            if v.startswith("#") or not v.startswith(os.path.basename(assets.rstrip("/\\")) + ":"):
                continue
            path = os.path.join(assets, "textures", v.split(":", 1)[1] + ".png")
            tex_users.setdefault(path, [set(), gui])[0].add(os.path.basename(f)[:-5])
            tex_users[path][1] = tex_users[path][1] and gui
    rows = []
    for path, (users, gui) in tex_users.items():
        if not os.path.exists(path):
            rows.append((100, path, (0, 0), [("ERROR", "missing", "texture file does not exist")], users))
            continue
        res, size = lint_file(path, gui_sprite=gui)
        score = sum(10 if l == "ERROR" else 3 for l, _, _ in res)
        rows.append((score, path, size, res, users))
    rows.sort(key=lambda r: (-r[0], r[1]))
    for score, path, size, res, users in rows:
        if not res:
            continue
        rel = os.path.relpath(path, assets).replace("\\", "/")
        print(f"[{score:3}] {rel} {size[0]}x{size[1]}  used by: {', '.join(sorted(users))}")
        for l, code, msg in res:
            print(f"       {l:5} {code}: {msg}")
    clean = sum(1 for r in rows if not r[3])
    print(f"\n{len(rows)} textures, {clean} clean, {len(rows) - clean} with findings")
    return any(l == "ERROR" for r in rows for l, _, _ in r[3])


def main(argv):
    if not argv or argv[0] in ("-h", "--help"):
        print(__doc__)
        return 0
    if argv[:1] == ["--audit"]:
        return 1 if audit(argv[1]) else 0
    bad = False
    for p in argv:
        res, size = lint_file(p)
        print(f"{p} {size[0]}x{size[1]}: " + ("clean" if not res else ""))
        for l, code, msg in res:
            print(f"   {l:5} {code}: {msg}")
            bad |= l == "ERROR"
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
