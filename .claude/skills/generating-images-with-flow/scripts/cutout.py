"""Cut a Flow image off its flat light background into a transparent, square, power-of-two PNG.

usage: python cutout.py <in.jpeg> <out.png> [--size 1024] [--fill 0.92]

  --size   output edge in px; keep it a power of two (atlas mipmapping breaks otherwise)
  --fill   fraction of the edge the subject's longer side fills (0.92 = the spellbook framing)

Background = pixels close to the border's median colour AND connected to the border, so cream
pages and white glows inside the subject survive. The generator rims a dark outline with a light
grey anti-alias halo; rim pixels are matted against the background instead of thresholded, so no
white fringe shows on dark hotbar slots. That matting assumes the subject has a DARK OUTLINE; for
an outline-less subject, check the edge on a dark background before shipping.
"""
import argparse
import numpy as np
from PIL import Image
from scipy import ndimage

ap = argparse.ArgumentParser()
ap.add_argument("src")
ap.add_argument("dst")
ap.add_argument("--size", type=int, default=1024)
ap.add_argument("--fill", type=float, default=0.92)
args = ap.parse_args()

im = np.asarray(Image.open(args.src).convert("RGB")).astype(np.float32)
border = np.concatenate([im[0], im[-1], im[:, 0], im[:, -1]])
bgc = np.median(border, axis=0)

dist = np.sqrt(((im - bgc) ** 2).sum(-1))
lab, _ = ndimage.label(dist < 38)
edge_labels = np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))
bg = np.isin(lab, edge_labels[edge_labels > 0])

# eat the pale, unsaturated halo within a few px of the background (saturated cream pages survive)
sat = (im.max(-1) - im.min(-1)) / np.maximum(im.max(-1), 1)
for _ in range(4):
    bg |= ndimage.binary_dilation(bg) & (dist < 150) & (sat < 0.18)

alpha = (~bg).astype(np.float32)
ring = (~bg) & ndimage.binary_dilation(bg, iterations=3)
# a grey rim pixel is the dark outline blended with the bg: solve alpha from luminance
lum = im @ np.array([0.299, 0.587, 0.114], np.float32)
bg_lum = float(bgc @ np.array([0.299, 0.587, 0.114]))
grey = ring & (sat < 0.25)
alpha[grey] = np.clip((bg_lum - lum[grey]) / (bg_lum - 20.0), 0.0, 1.0) ** 1.5
alpha[ring & ~grey] = np.clip(dist[ring & ~grey] / 90.0, 0.0, 1.0)

a = alpha[..., None]
rgb = np.clip(np.where(a > 0.02, (im - (1 - a) * bgc) / np.maximum(a, 1e-3), 0), 0, 255)
out = Image.fromarray(np.dstack([rgb, alpha * 255]).astype(np.uint8), "RGBA")

ys, xs = np.where(alpha > 0.05)
crop = out.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
scale = round(args.size * args.fill) / max(crop.size)
crop = crop.resize((max(1, round(crop.width * scale)), max(1, round(crop.height * scale))), Image.LANCZOS)
canvas = Image.new("RGBA", (args.size, args.size), (0, 0, 0, 0))
canvas.alpha_composite(crop, ((args.size - crop.width) // 2, (args.size - crop.height) // 2))
canvas.save(args.dst)
print(args.dst, "bg", bgc.round().tolist(), "subject", crop.size)
