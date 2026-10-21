"""Checks for scene_engine.py. Plain asserts; run with:  python -I build_scripts/pensieve_scenes/test_scene_engine.py

  letter_outputs_unchanged: regenerating the Letter scenes (and the Wand scenes, which share the maths) rewrites
      their JSON byte for byte (md5 of the committed files);
  spline_moves_flags_linear_track: a 6-key linear moving camera is one problem, the same keys as spline none;
  wand_sample_cam_matches: wand_scene.sample_cam on a spline track is engine_cam.
"""
import hashlib
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import scene_engine as E  # noqa: E402
import wand_scene as W  # noqa: E402
from scene_common import MEMORIES  # noqa: E402

MD5 = {
    'letter.json': '1f16ac538fb82bdf164cb5694ce22fba',
    'letter_crossing.json': '327393da951fffa8b90d560545990cc7',
    'letter_flood.json': 'fec30fafeceed683bed1827e8f9489cc',
    'letter_hut.json': '522ccc19646ee38da6dc67f100b6c785',
    'wand_alley.json': '027ee25cef5bd084f37de7b14cbb0f5f',
    'wand_ollivanders.json': '609e55155c2d8c24334fbafd13420337',
    'wand_wall.json': 'd66d81def2af6d3e65ec6df616b03ab1',
}


def letter_outputs_unchanged():
    for script in ('letter_scene.py', 'wand_scene.py'):
        subprocess.run([sys.executable, '-I', os.path.join(HERE, script)], check=True, stdout=subprocess.DEVNULL)
    for name, md5 in MD5.items():
        got = hashlib.md5(open(os.path.join(MEMORIES, name), 'rb').read()).hexdigest()
        assert got == md5, (name, got, md5)


def track(ease):
    keys = [{'t': 0, 'pos': [0.0, 2.0, 0.0], 'look_at': [0.0, 2.0, 10.0]}]
    for i in range(1, 6):
        keys.append({'t': i * 20, 'pos': [i * 2.0, 2.0 + (i % 2) * 0.5, 0.0], 'look_at': [0.0, 2.0, 10.0 + i],
                     'ease': ease})
    return {'camera': keys, 'actors': [], 'length': 100}


def spline_moves_flags_linear_track():
    bad = []
    E.check_spline_moves('lin', track('linear'), bad)
    assert len(bad) == 1, bad
    ok = []
    E.check_spline_moves('spl', track('spline'), ok)
    assert not ok, ok


def wand_sample_cam_matches():
    sc = track('spline')
    for i in range(0, 401):
        t = i / 4.0
        a, b = W.sample_cam(sc['camera'], t), E.engine_cam(sc['camera'], t)
        assert max(abs(a[c] - b[c]) for c in range(3)) < 1e-12, (t, a, b)


if __name__ == '__main__':
    for fn in (spline_moves_flags_linear_track, wand_sample_cam_matches, letter_outputs_unchanged):
        fn()
        print('ok', fn.__name__)
