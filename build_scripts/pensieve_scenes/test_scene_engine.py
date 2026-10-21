"""Checks for scene_engine.py. Plain asserts; run with:  python -I build_scripts/pensieve_scenes/test_scene_engine.py

  letter_outputs_unchanged: regenerating the Letter scenes (and the Wand scenes, which share the maths) rewrites
      their JSON byte for byte (md5 of the committed files);
  spline_moves_flags_linear_track: a 6-key linear moving camera is one problem, the same keys as spline none;
  wand_sample_cam_matches: wand_scene.sample_cam on a spline track is engine_cam;
  rider_grip: a broom rider's fists close on the handle (gap at most 0.06, sunk at most 0.005) with the whole fist
      box (sleeve on) at least 0.1 px clear of the thigh box (pants on), wide and slim arms, scales 0.75 / 0.85 / 1.0,
      speeds 0 to 0.6 and broom pitch 0 to 70 (the dive tuck), as BroomFlightTest.handsOnHandle;
  rider_body_on_broom: a rider's look turns his head (clamped to 75), never his body off the broom.
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


def rider_grip():
    gap, sink, clear = E.check_rider_grip()
    assert gap <= E.GRIP_GAP_MAX and sink >= -E.GRIP_SINK_MAX and clear >= E.FIST_CLEAR_PX, (gap, sink, clear)


def rider_body_on_broom():
    sc = {'poses': {}, 'actors': [
        {'id': 'b', 'type': 'hogwartsmagic:memory_broom', 'path': [{'t': 0, 'pos': [0.0, 5.0, 0.0]}],
         'look': [{'t': 0, 'yaw': 30}]},
        {'id': 'r', 'type': 'player', 'ride': 'b', 'look': [{'t': 0, 'yaw': 40}, {'t': 50, 'yaw': 200}]}]}
    for t in (0, 25, 50):
        parts = {p[0]: p for p in E.posed_parts(sc, sc['actors'][1], t)}
        assert parts['head'][2].yaw == 30, parts['head'][2].yaw
        assert abs(parts['head'][5][1]) <= E.HEAD_TURN, parts['head'][5]
    assert E.rider_head_turn(200) == -75 and E.rider_head_turn(40) == 40


def ball_box_and_lowest():
    lo, hi = E.ball_box('hogwartsmagic:memory_quaffle', 0.85)
    assert all(abs(c + 0.55 / 2 * 0.85) < 1e-12 for c in lo) and all(abs(c - 0.55 / 2 * 0.85) < 1e-12 for c in hi)
    assert E.is_ball('hogwartsmagic:memory_remembrall') and not E.is_ball('hogwartsmagic:memory_broom')
    sc = {'poses': {}, 'actors': [{'id': 'q', 'type': 'hogwartsmagic:memory_bludger', 'scale': 2.0,
                                   'path': [{'t': 0, 'pos': [0.0, 1.0, 0.0]}, {'t': 10, 'pos': [4.0, 3.0, 0.0]}]}]}
    assert abs(E.ball_lowest(sc, 'q', 0) - (1.0 - 0.45)) < 1e-12
    assert abs(E.ball_pos(sc, 'q', 10)[0] - 4.0) < 1e-9


if __name__ == '__main__':
    for fn in (spline_moves_flags_linear_track, wand_sample_cam_matches, rider_grip, rider_body_on_broom,
               ball_box_and_lowest, letter_outputs_unchanged):
        fn()
        print('ok', fn.__name__)
