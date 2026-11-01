"""Cut a spellbook off a flat light background and frame it like the approved painted set.
# Kept in tools/ from the 2026-11-01 Flow spellbook pass; see texturing-spellbooks SKILL.md.

usage: python cutout.py <in.png> <out.png> [size=1024]
Background = near-background-colour pixels connected to the image border (so cream pages and
white glows inside the book's dark outline survive). Edge pixels get soft alpha and are
un-blended from the background colour so no light fringe shows on dark slots.
"""
import sys
import numpy as np
from PIL import Image
from scipy import ndimage

src, dst = sys.argv[1], sys.argv[2]
size = int(sys.argv[3]) if len(sys.argv) > 3 else 1024

im = np.asarray(Image.open(src).convert("RGB")).astype(np.float32)
h, w, _ = im.shape
border = np.concatenate([im[0], im[-1], im[:, 0], im[:, -1]])
bgc = np.median(border, axis=0)

dist = np.sqrt(((im - bgc) ** 2).sum(-1))
cand = dist < 38
lab, _ = ndimage.label(cand)
edge_labels = np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))
bg = np.isin(lab, edge_labels[edge_labels > 0])
# the generator rims the dark outline with a light, grey anti-alias halo: eat pale, unsaturated
# pixels within a few px of the background (cream pages are saturated enough to survive)
sat = (im.max(-1) - im.min(-1)) / np.maximum(im.max(-1), 1)
for _ in range(4):
    bg |= ndimage.binary_dilation(bg) & (dist < 150) & (sat < 0.18)

# soft edge: ring of 2px inside the book gets alpha from how far it is from the bg colour
alpha = (~bg).astype(np.float32)
ring = (~bg) & ndimage.binary_dilation(bg, iterations=3)
# the silhouette is always the near-black outline, so a rim pixel is outline blended with the bg:
# solve alpha against the bg luminance (grey anti-alias -> partial alpha, pure outline -> 1)
lum = im @ np.array([0.299, 0.587, 0.114], np.float32)
bg_lum = float(bgc @ np.array([0.299, 0.587, 0.114]))
grey = ring & (sat < 0.25)
alpha[grey] = np.clip((bg_lum - lum[grey]) / (bg_lum - 20.0), 0.0, 1.0) ** 1.5
alpha[ring & ~grey] = np.clip(dist[ring & ~grey] / 90.0, 0.0, 1.0)

a = alpha[..., None]
rgb = np.where(a > 0.02, (im - (1 - a) * bgc) / np.maximum(a, 1e-3), 0)
rgb = np.clip(rgb, 0, 255)
out = np.dstack([rgb, alpha * 255]).astype(np.uint8)

ys, xs = np.where(alpha > 0.05)
crop = Image.fromarray(out, "RGBA").crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
side = max(crop.size)
inner = round(size * 0.92)
scale = inner / side
crop = crop.resize((max(1, round(crop.width * scale)), max(1, round(crop.height * scale))), Image.LANCZOS)
canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
canvas.alpha_composite(crop, ((size - crop.width) // 2, (size - crop.height) // 2))
canvas.save(dst)
print(dst, "bg", bgc.round(), "bbox", crop.size)
