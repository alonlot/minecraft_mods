"""Small pixel-art toolkit: a Canvas with the primitives you actually want when hand-placing
texels for Minecraft-style 16x16 art (ramps, outlines, diagonal shafts, AO, sparkle)."""
from PIL import Image
import random


def hx(s):
    s = s.lstrip("#")
    if len(s) == 6:
        return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), int(s[6:8], 16))


def ramp(*hexes):
    return [hx(h) for h in hexes]


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def shade(c, f):
    """Multiply RGB by f, keep alpha."""
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))),
            max(0, min(255, int(c[2] * f))), c[3])


class Canvas:
    def __init__(self, w, h=None, seed=0):
        h = w if h is None else h
        self.w, self.h = w, h
        self.px = [[(0, 0, 0, 0)] * w for _ in range(h)]
        self.rng = random.Random(seed)

    # --- primitives ---------------------------------------------------------
    def put(self, x, y, c):
        if c is None:
            return
        x, y = int(x), int(y)
        if 0 <= x < self.w and 0 <= y < self.h:
            if c[3] == 255:
                self.px[y][x] = c
            elif c[3] > 0:
                self.px[y][x] = self.blend(self.px[y][x], c)

    def blend(self, dst, src):
        a = src[3] / 255.0
        if dst[3] == 0:
            return src
        return (int(src[0] * a + dst[0] * (1 - a)), int(src[1] * a + dst[1] * (1 - a)),
                int(src[2] * a + dst[2] * (1 - a)), max(dst[3], src[3]))

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[int(y)][int(x)]
        return (0, 0, 0, 0)

    def solid(self, x, y):
        return self.get(x, y)[3] > 0

    def rect(self, x0, y0, x1, y1, c):
        for y in range(int(y0), int(y1) + 1):
            for x in range(int(x0), int(x1) + 1):
                self.put(x, y, c)

    def frame(self, x0, y0, x1, y1, c):
        for x in range(int(x0), int(x1) + 1):
            self.put(x, y0, c); self.put(x, y1, c)
        for y in range(int(y0), int(y1) + 1):
            self.put(x0, y, c); self.put(x1, y, c)

    def hline(self, x0, x1, y, c):
        for x in range(int(min(x0, x1)), int(max(x0, x1)) + 1):
            self.put(x, y, c)

    def vline(self, x, y0, y1, c):
        for y in range(int(min(y0, y1)), int(max(y0, y1)) + 1):
            self.put(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        x0, y0, x1, y1 = int(x0), int(y0), int(x1), int(y1)
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.put(x0, y0, c)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy; x0 += sx
            if e2 <= dx:
                err += dx; y0 += sy

    def disc(self, cx, cy, r, c, sq=1.0):
        for y in range(self.h):
            for x in range(self.w):
                dx, dy = (x + .5 - cx), (y + .5 - cy) * sq
                if dx * dx + dy * dy <= r * r:
                    self.put(x, y, c)

    def ring(self, cx, cy, r, c, sq=1.0, t=1.0):
        for y in range(self.h):
            for x in range(self.w):
                dx, dy = (x + .5 - cx), (y + .5 - cy) * sq
                d = (dx * dx + dy * dy) ** .5
                if r - t <= d <= r:
                    self.put(x, y, c)

    def poly(self, pts, c):
        """Scanline fill of a polygon given in pixel-centre coords."""
        ys = [p[1] for p in pts]
        for y in range(int(min(ys)), int(max(ys)) + 1):
            xs = []
            n = len(pts)
            for i in range(n):
                (ax, ay), (bx, by) = pts[i], pts[(i + 1) % n]
                if (ay <= y < by) or (by <= y < ay):
                    xs.append(ax + (y - ay) * (bx - ax) / (by - ay))
            xs.sort()
            for i in range(0, len(xs) - 1, 2):
                for x in range(int(round(xs[i])), int(round(xs[i + 1])) + 1):
                    self.put(x, y, c)

    # --- effects ------------------------------------------------------------
    def outline(self, c, diag=False, only_outside=True):
        """Draw an outline in the transparent cells adjacent to opaque ones."""
        n4 = [(1, 0), (-1, 0), (0, 1), (0, -1)]
        if diag:
            n4 += [(1, 1), (1, -1), (-1, 1), (-1, -1)]
        adds = []
        for y in range(self.h):
            for x in range(self.w):
                if self.solid(x, y):
                    continue
                if any(self.solid(x + dx, y + dy) for dx, dy in n4):
                    adds.append((x, y))
        for x, y in adds:
            self.put(x, y, c)

    def inner_outline(self, c, sides=("l", "r", "t", "b")):
        """Darken the opaque pixels that sit on the silhouette edge."""
        n = {"l": (-1, 0), "r": (1, 0), "t": (0, -1), "b": (0, 1)}
        adds = []
        for y in range(self.h):
            for x in range(self.w):
                if not self.solid(x, y):
                    continue
                if any(not self.solid(x + n[s][0], y + n[s][1]) for s in sides):
                    adds.append((x, y))
        for x, y in adds:
            self.put(x, y, c)

    def shade_edges(self, f_dark=0.72, sides=("r", "b")):
        n = {"l": (-1, 0), "r": (1, 0), "t": (0, -1), "b": (0, 1)}
        adds = []
        for y in range(self.h):
            for x in range(self.w):
                if not self.solid(x, y):
                    continue
                if any(not self.solid(x + n[s][0], y + n[s][1]) for s in sides):
                    adds.append((x, y))
        for x, y in adds:
            self.put(x, y, shade(self.get(x, y), f_dark))

    def noise(self, amt=0.06, mask=None):
        for y in range(self.h):
            for x in range(self.w):
                if not self.solid(x, y):
                    continue
                if mask and not mask(x, y):
                    continue
                f = 1.0 + self.rng.uniform(-amt, amt)
                self.put(x, y, shade(self.get(x, y), f))

    def replace(self, old, new):
        for y in range(self.h):
            for x in range(self.w):
                if self.get(x, y) == old:
                    self.px[y][x] = new

    def paste(self, other, ox=0, oy=0):
        for y in range(other.h):
            for x in range(other.w):
                c = other.get(x, y)
                if c[3]:
                    self.put(x + ox, y + oy, c)

    # --- output -------------------------------------------------------------
    def image(self):
        im = Image.new("RGBA", (self.w, self.h))
        im.putdata([self.px[y][x] for y in range(self.h) for x in range(self.w)])
        return im

    def save(self, path):
        self.image().save(path)


def shaft(cv, x0, y0, x1, y1, cols, width=2):
    """Draw a thick diagonal shaft (wand/spear handle) with a lit and a shaded side.
    cols = [light, mid, dark] applied across the width."""
    steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 3) + 1
    for i in range(steps + 1):
        t = i / steps
        x = x0 + (x1 - x0) * t
        y = y0 + (y1 - y0) * t
        for k in range(width):
            c = cols[min(k, len(cols) - 1)]
            cv.put(round(x + k), round(y), c)
