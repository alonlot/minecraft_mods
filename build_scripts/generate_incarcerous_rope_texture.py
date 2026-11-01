"""Texture for the Incarcerous ropes, drawn procedurally so it can be re-tuned.

Output (assets/hogwartsmagic/textures/entity/):
  incarcerous_rope.png  32x64 - three-strand twisted hemp. u runs round the rope, v along it, and it tiles in
                        both: each strand crosses u exactly once per v repeat, so the lay reads at about 45
                        degrees on a rope 0.05 blocks thick drawn at 0.3 blocks per repeat.

The film ropes are thick, dull, slightly hairy hemp: rounded strands with a lit crown and deep dark grooves,
the fibres in each strand twisted the opposite way to the strands themselves. Flat stripes read as a candy
cane, so the shading per strand is a half-cosine and the grooves go almost black.

Run:  python build_scripts/generate_incarcerous_rope_texture.py
"""
import os

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets",
                   "hogwartsmagic", "textures", "entity")
RNG = np.random.default_rng(1997)
W, H = 32, 64
STRANDS = 3

GROOVE = np.array([0.20, 0.14, 0.08])
BODY = np.array([0.60, 0.46, 0.28])
CROWN = np.array([0.84, 0.71, 0.49])


def main():
    u = (np.arange(W) + 0.5)[None, :] / W
    v = (np.arange(H) + 0.5)[:, None] / H
    # Strand phase: constant along a strand, so u + v (one crossing per repeat) keeps it tiling.
    lay = (u + v) * STRANDS
    f = lay - np.floor(lay)
    roundness = np.sin(np.pi * f)  # 0 in the groove, 1 on the crown
    shade = roundness ** 0.7

    # Fibres twisted against the lay: fine diagonal lines with the opposite slope, low contrast.
    fibre_phase = (u * 2.0 - v * 9.0) * 4.0
    fibres = 0.5 + 0.5 * np.sin(2.0 * np.pi * fibre_phase + RNG.random((1, W)) * 0.8)

    # Each strand a slightly different hemp so the twist reads at a glance.
    strand = np.floor(lay).astype(int) % STRANDS
    tint = np.array([1.0, 0.93, 1.05])[strand]

    noise = RNG.normal(0.0, 0.05, (H, W))
    lum = np.clip(shade * (0.86 + 0.14 * fibres) * tint + noise, 0.0, 1.15)

    rgb = np.where(lum[..., None] < 0.55,
                   GROOVE + (BODY - GROOVE) * (lum[..., None] / 0.55),
                   BODY + (CROWN - BODY) * ((lum[..., None] - 0.55) / 0.6))
    rgb = np.clip(rgb, 0.0, 1.0)
    img = np.concatenate([rgb, np.ones((H, W, 1))], axis=2)
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "incarcerous_rope.png")
    Image.fromarray((img * 255).round().astype(np.uint8), "RGBA").save(path)
    print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    main()
