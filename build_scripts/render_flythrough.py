"""Render an MP4 flythrough of a Minecraft structure template.

    python build_scripts/render_flythrough.py                    # the whole alley, 1280x720
    python build_scripts/render_flythrough.py --probe            # three stills, and how slow it is
    python build_scripts/render_flythrough.py --nbt gringotts.nbt --seconds 12

Why this drives its own browser
-------------------------------
The textured viewer in .claude/skills/rendering-minecraft-structures is a WebGL page, and the tools
that normally drive it only exist inside an agent session. A script somebody can re-run has to bring
its own browser, so this launches headless Chrome through Playwright and talks to the page itself.

Playwright is installed here but has **no downloaded browsers**, so it is pointed at the system
Chrome with `channel="chrome"`. Chrome 129 and later also refuse to run WebGL on the software
rasteriser without `--enable-unsafe-swiftshader`, which is exactly what headless does when there is
no GPU -- without that flag the page loads and renders nothing at all, silently.

It also serves the viewer itself rather than reusing the skill's serve.py, because that one writes
saved frames into the skill's own shots/ directory and these belong in build/video/frames/.

The camera
----------
`mat4.lookAt` gave unreliable results against this renderer, so the camera is built the way the
viewer's own code builds it:

    m = I; translate(0,0,-dist); rotateX(pitch); rotateY(yaw); translate(-pivot)

which puts the eye at `pivot + (-d cos p sin y, d sin p, d cos p cos y)`. Inverting that gives a
look-at in terms the renderer definitely agrees with:

    d = |eye - pivot|;  pitch = asin(v.y / d);  yaw = atan2(-v.x, v.z)

Nothing here uses requestAnimationFrame -- a headless page throttles it to nothing. Each frame draws
synchronously, calls gl.finish(), and posts the canvas straight back.
"""

import argparse
import base64
import json
import math
import os
import shutil
import subprocess
import sys
import threading
import time
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VIEWER = os.path.join(ROOT, '.claude', 'skills', 'rendering-minecraft-structures', 'textured', 'build')
OUT_DIR = os.path.join(ROOT, 'build', 'video')
FRAME_DIR = os.path.join(OUT_DIR, 'frames')


# --- the little server ---------------------------------------------------------------------------

class Handler(SimpleHTTPRequestHandler):
    frame_dir = FRAME_DIR

    def do_POST(self):
        if not self.path.startswith('/save'):
            self.send_error(404)
            return
        length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(length).decode('utf-8', 'replace')
        name = os.path.basename(self.path.partition('?')[2] or 'frame.png')
        if not name.endswith('.png'):
            name += '.png'
        data = body.split(',', 1)[1] if ',' in body else body
        os.makedirs(self.frame_dir, exist_ok=True)
        with open(os.path.join(self.frame_dir, name), 'wb') as fh:
            fh.write(base64.b64decode(data))
        self.send_response(200)
        self.send_header('Content-Type', 'text/plain')
        self.end_headers()
        self.wfile.write(b'ok')

    def log_message(self, *args):
        pass


def serve(directory, port):
    handler = partial(Handler, directory=directory)
    server = ThreadingHTTPServer(('127.0.0.1', port), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


# --- geometry ------------------------------------------------------------------------------------

def read_size(nbt_path):
    sys.path.insert(0, os.path.join(ROOT, '.claude', 'skills', 'rendering-minecraft-structures'))
    from render_structure import read_template
    blocks, _entities, size = read_template(nbt_path)
    if not size or size == [0, 0, 0]:
        size = [max(p[i] for p in blocks) + 1 for i in range(3)]
    return blocks, size


def open_columns(blocks, size):
    """Which columns are outdoor street: paved low down, and nothing at all above.

    "Nothing above" is the load-bearing half. An earlier version only asked for clear space at head
    height, which is just as true inside Gringotts -- its hall has thirty-three blocks of it -- so
    the street appeared to run the entire length of the build and the dolly drove into the marble.
    A roof is what separates a street from a room.
    """
    sx, sy, sz = size
    paved = [[False] * sz for _ in range(sx)]
    roofed = [[False] * sz for _ in range(sx)]
    for (x, y, z), (name, _props) in blocks.items():
        if name == 'minecraft:air':
            continue
        if y <= 2:
            paved[x][z] = True
        else:
            roofed[x][z] = True
    return [[paved[x][z] and not roofed[x][z] for z in range(sz)] for x in range(sx)]


def street_centreline(open_cols, size):
    """The middle of the widest open run at each x, so the camera follows the street's curve."""
    sx, _sy, sz = size
    line = []
    previous = sz // 2
    for x in range(sx):
        best, best_run, run = None, 0, 0
        for z in range(sz):
            if open_cols[x][z]:
                run += 1
                if run > best_run:
                    best_run, best = run, z - run // 2
            else:
                run = 0
        # Prefer continuity: a wide square offers two candidate runs and jumping between them makes
        # the camera lurch sideways.
        if best is None or abs(best - previous) > 12:
            best = previous
        previous = best
        line.append(best)
    smooth = []
    for x in range(sx):
        lo, hi = max(0, x - 6), min(sx, x + 7)
        smooth.append(sum(line[lo:hi]) / float(hi - lo))
    return smooth


def smoothstep(a, b, t):
    t = max(0.0, min(1.0, t))
    return a + (b - a) * t * t * (3.0 - 2.0 * t)


def street_end(open_cols, size):
    """The last x with real open street across it."""
    sx, _sy, sz = size
    for x in range(sx - 1, 0, -1):
        if sum(1 for z in range(sz) if open_cols[x][z]) >= 5:
            return x
    return sx - 1


def build_path(size, centre, frames, street_x1):
    """Three shots: an establishing orbit, a run down the street, and a rise onto the bank."""
    sx, sy, sz = size
    cx, cy, cz = sx / 2.0, sy / 2.0, sz / 2.0
    span = max(sx, sy, sz)

    path = []
    a_end, b_end = int(frames * 0.26), int(frames * 0.78)

    for i in range(frames):
        if i < a_end:
            # A: orbit the whole build, pulling in.
            t = i / float(max(1, a_end - 1))
            yaw = -0.9 + 1.5 * t
            pitch = smoothstep(0.95, 0.55, t)
            dist = smoothstep(span * 0.95, span * 0.62, t)
            pivot = (cx, cy * 0.7, cz)
            eye = (pivot[0] - dist * math.cos(pitch) * math.sin(yaw),
                   pivot[1] + dist * math.sin(pitch),
                   pivot[2] + dist * math.cos(pitch) * math.cos(yaw))
            path.append((eye, pivot))
        elif i < b_end:
            # B: down the street. Trapezoidal speed -- a single smoothstep across a shot this long
            # spends the middle of it going far too fast.
            t = (i - a_end) / float(max(1, b_end - a_end - 1))
            if t < 0.18:
                s = smoothstep(0.0, 0.18, t / 0.18)
            elif t > 0.82:
                s = smoothstep(0.82, 1.0, (t - 0.82) / 0.18)
            else:
                s = t
            x = 6.0 + s * (street_x1 - 6.0)
            zi = int(max(0, min(sx - 1, x)))
            z = centre[zi]
            # Crane over the wall near the start: it is solid, and a street-level dolly would go
            # straight into it.
            lift = 9.0 * math.exp(-((x - 14.0) ** 2) / 90.0)
            y = 5.5 + lift
            ahead = int(max(0, min(sx - 1, x + 26)))
            eye = (x, y, z)
            pivot = (x + 26.0, 6.0, centre[ahead])
            path.append((eye, pivot))
        else:
            # C: rise onto Gringotts.
            t = (i - b_end) / float(max(1, frames - b_end - 1))
            bank_x = street_x1 + (sx - street_x1) * 0.45
            pivot = (bank_x, smoothstep(8.0, 20.0, t), centre[min(sx - 1, int(bank_x))])
            dist = smoothstep(58.0, 92.0, t)
            pitch = smoothstep(0.06, 0.55, t)
            yaw = smoothstep(1.55, 1.15, t)
            eye = (pivot[0] - dist * math.cos(pitch) * math.sin(yaw),
                   pivot[1] + dist * math.sin(pitch),
                   pivot[2] + dist * math.cos(pitch) * math.cos(yaw))
            path.append((eye, pivot))
    return path


# --- the page ------------------------------------------------------------------------------------

CAMERA_JS = """
(() => {
  const mat4 = window.glMatrix.mat4;
  window.__shot = (eye, pivot, name) => {
    const vx = eye[0] - pivot[0], vy = eye[1] - pivot[1], vz = eye[2] - pivot[2];
    const d = Math.hypot(vx, vy, vz) || 1e-3;
    const pitch = Math.asin(Math.max(-1, Math.min(1, vy / d)));
    const yaw = Math.atan2(-vx, vz);
    const m = mat4.create();
    mat4.translate(m, m, [0, 0, -d]);
    mat4.rotateX(m, m, pitch);
    mat4.rotateY(m, m, yaw);
    mat4.translate(m, m, [-pivot[0], -pivot[1], -pivot[2]]);
    window.__renderer.drawStructure(m);
    window.__gl.finish();
    const c = document.querySelector('canvas');
    return fetch('/save?' + name, {method: 'POST', body: c.toDataURL('image/png')})
        .then(r => r.text());
  };
  return 'ok';
})()
"""


def render(args):
    from playwright.sync_api import sync_playwright

    nbt_path = os.path.join(VIEWER, args.nbt)
    if not os.path.exists(nbt_path):
        sys.exit('no such template in the viewer directory: %s\n'
                 '(run build_scripts/generate_diagon_alley.py, which copies it there)' % nbt_path)

    blocks, size = read_size(nbt_path)
    open_cols = open_columns(blocks, size)
    centre = street_centreline(open_cols, size)
    print('%s is %dx%dx%d, %d blocks' % (args.nbt, size[0], size[1], size[2], len(blocks)))

    if os.path.isdir(FRAME_DIR):
        shutil.rmtree(FRAME_DIR)
    os.makedirs(FRAME_DIR, exist_ok=True)

    if args.stills:
        # Named stills rather than a path. The in-session browser tools proved unreliable for
        # this -- they silently stopped applying the camera matrix partway through a batch and
        # returned sixteen copies of the same frame -- whereas this pipeline has already been
        # verified over six hundred varied frames.
        with open(args.stills, encoding='utf-8') as fh:
            spec = json.load(fh)
        path = [((r[1], r[2], r[3]), (r[4], r[5], r[6])) for r in spec]
        names = [r[0] for r in spec]
        return _run(args, path, names)

    street_x1 = street_end(open_cols, size)
    print('street runs to x=%d; the building at the end takes it from there' % street_x1)
    frames = int(args.seconds * args.fps)
    path = build_path(size, centre, frames, street_x1)
    if args.probe:
        # Sample the full-length path so each shot is represented as it will actually look.
        path = [path[int(frames * f)] for f in (0.10, 0.45, 0.70, 0.90)]

    return _run(args, path, None)


def _run(args, path, names):
    """Drive the page over a list of (eye, pivot) pairs and save one PNG each."""
    from playwright.sync_api import sync_playwright
    if os.path.isdir(FRAME_DIR):
        shutil.rmtree(FRAME_DIR)
    os.makedirs(FRAME_DIR, exist_ok=True)
    server = serve(VIEWER, args.port)
    started = time.time()
    try:
        with sync_playwright() as pw:
            browser = pw.chromium.launch(
                channel='chrome',
                headless=True,
                args=['--enable-unsafe-swiftshader',      # Chrome >=129 gates software WebGL
                      '--use-gl=angle',
                      '--use-angle=swiftshader',
                      '--disable-gpu-sandbox'])
            page = browser.new_page(viewport={'width': args.width, 'height': args.height})
            page.set_default_timeout(180000)
            url = 'http://127.0.0.1:%d/?nbt=%s&w=%d&h=%d' % (
                args.port, args.nbt, args.width, args.height)
            page.goto(url)

            print('meshing...', end='', flush=True)
            page.wait_for_function('window.__ready === "ok" || window.__ready === "error"',
                                   timeout=300000)
            ready = page.evaluate('window.__ready')
            if ready != 'ok':
                sys.exit('\nthe viewer failed to load the structure (__ready=%s)' % ready)
            missing = page.evaluate('window.__missing')
            print(' ready')
            if any(missing.get(k) for k in ('definitions', 'models', 'textures')):
                print('MISSING assets -- re-export first:')
                print(json.dumps(missing, indent=2)[:800])
                print('  python .claude/skills/rendering-minecraft-structures/textured/'
                      'export_assets.py src/main/resources/data/hogwartsmagic/structure/*.nbt '
                      '--jar <client-extra.jar> --mod src/main/resources/assets/hogwartsmagic '
                      '--out .claude/skills/rendering-minecraft-structures/textured/build')

            page.evaluate(CAMERA_JS)

            t0 = time.time()
            for i, (eye, pivot) in enumerate(path):
                label = (names[i] + '.png') if names else ('f%05d.png' % i)
                page.evaluate('([e,p,n]) => window.__shot(e,p,n)',
                              [list(eye), list(pivot), label])
                if i and i % 25 == 0:
                    rate = (time.time() - t0) / i
                    print('  %4d/%d  %.2fs/frame  eta %.0fs'
                          % (i, len(path), rate, rate * (len(path) - i)))
            browser.close()
    finally:
        server.shutdown()

    made = sorted(f for f in os.listdir(FRAME_DIR) if f.endswith('.png'))
    print('%d frames in %.0fs -> %s' % (len(made), time.time() - started, FRAME_DIR))
    if names:
        return 0                                  # stills only; nothing to encode
    if args.probe:
        print('probe only; look at the stills before rendering the rest')
        return 0
    if not made:
        sys.exit('no frames were written')

    os.makedirs(OUT_DIR, exist_ok=True)
    out = args.out or os.path.join(OUT_DIR, os.path.splitext(args.nbt)[0] + '.mp4')
    cmd = ['ffmpeg', '-y', '-framerate', str(args.fps),
           '-i', os.path.join(FRAME_DIR, 'f%05d.png'),
           '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '18',
           '-vf', 'scale=trunc(iw/2)*2:trunc(ih/2)*2', out]
    print(' '.join(cmd))
    subprocess.run(cmd, check=True)
    print('wrote %s (%.1f MB)' % (out, os.path.getsize(out) / 1e6))
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--nbt', default='diagon_alley.nbt')
    ap.add_argument('--out')
    ap.add_argument('--width', type=int, default=1280)
    ap.add_argument('--height', type=int, default=720)
    ap.add_argument('--fps', type=int, default=30)
    ap.add_argument('--seconds', type=float, default=24.0)
    ap.add_argument('--port', type=int, default=8749)
    ap.add_argument('--stills',
                    help='JSON file of [name, eyeX, eyeY, eyeZ, atX, atY, atZ] rows: render '
                         'those stills instead of a flythrough, and skip the video')
    ap.add_argument('--probe', action='store_true',
                    help='render three stills and report the per-frame cost')
    return render(ap.parse_args())


if __name__ == '__main__':
    raise SystemExit(main())
