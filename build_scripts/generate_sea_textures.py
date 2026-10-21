"""Textures for the Pensieve rough_sea effect (client/pensieve/RoughSeaRenderer).

    python build_scripts/generate_sea_textures.py

writes, into assets/hogwartsmagic/textures/entity/:
  sea_ripple.png  128 x 128, opaque grey 0.80 to 1.00: fine tileable ripple texture the wave mesh multiplies its
                  shaded colour by, scrolled with the wind, so the water has detail finer than the mesh;
  sea_foam.png    128 x 128, white with alpha: lacy tileable foam (ridged noise threads with bubbly holes) drawn over
                  the crests with the foam amount as vertex alpha.
Both tile seamlessly (built from FFT-filtered periodic noise). Needs numpy and Pillow.
"""
import os

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets',
                   'hogwartsmagic', 'textures', 'entity')
N = 128


def periodic_noise(rng, lo, hi):
    """Band-limited periodic noise with spatial frequencies between lo and hi cycles per tile, normalised to -1..1."""
    white = rng.standard_normal((N, N))
    f = np.fft.fft2(white)
    fy = np.fft.fftfreq(N) * N
    fx = np.fft.fftfreq(N) * N
    r = np.sqrt(fx[None, :] ** 2 + fy[:, None] ** 2)
    band = np.exp(-((np.log(np.maximum(r, 1e-3)) - np.log((lo * hi) ** 0.5)) ** 2) / (2 * 0.35 ** 2))
    band[r < lo * 0.5] = 0
    out = np.real(np.fft.ifft2(f * band))
    out -= out.mean()
    return out / np.abs(out).max()


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def main():
    rng = np.random.default_rng(20261021)
    # Ripples: two octaves, slightly stretched along x so they read as wind-driven wavelets.
    a = periodic_noise(rng, 6, 12)
    b = periodic_noise(rng, 14, 28)
    ripple = 0.65 * a + 0.35 * b
    grey = 0.80 + 0.20 * (ripple - ripple.min()) / (ripple.max() - ripple.min())
    img = np.dstack([grey, grey, grey, np.ones_like(grey)])
    Image.fromarray((img * 255).round().astype(np.uint8), 'RGBA').save(os.path.join(OUT, 'sea_ripple.png'))

    # Foam: ridges of low noise make the threads, mid noise breaks them, high noise pocks them with bubbles.
    n1 = periodic_noise(rng, 2.5, 5)
    n2 = periodic_noise(rng, 5, 10)
    n3 = periodic_noise(rng, 10, 20)
    threads = 1 - np.abs(n1 * 0.75 + n2 * 0.25)
    lace = smoothstep(0.72, 0.96, threads)
    patches = smoothstep(0.30, 0.80, n2 * 0.6 + n1 * 0.4)
    bubbles = smoothstep(-0.45, 0.35, n3)
    alpha = np.clip(lace * 0.9 + patches * 0.7, 0, 1) * (0.45 + 0.55 * bubbles)
    shade = 0.88 + 0.12 * bubbles
    img = np.dstack([shade * 0.96, shade * 0.98, shade, alpha])
    Image.fromarray((img * 255).round().astype(np.uint8), 'RGBA').save(os.path.join(OUT, 'sea_foam.png'))
    print('wrote sea_ripple.png and sea_foam.png to', os.path.normpath(OUT))


if __name__ == '__main__':
    main()
