"""First Task memory music cues: cuts the user's soundtrack files into sounds/pensieve/music/first_task_*.ogg.

    python -I build_scripts/sounds/first_task_music.py                 cut, fade and level every cue
    python -I build_scripts/sounds/first_task_music.py check           print each cue's length and loudness
    python -I build_scripts/sounds/first_task_music.py points <source> print the source's loudness envelope (0.5 s
                                                                       bins) and its 12 strongest onsets, to land
                                                                       the roar, the snap, the crash and the grab

The sources are film soundtrack excerpts the user supplied for private use only (run/music/first_task/, not in git):
harry_sees_dragons.mp3 (113 s) and golden_egg.mp3 (370 s). Cues are cut, faded (baked in: a music event plays the
whole file from its t), kept stereo 44.1 kHz and given one gain per source, as in letter_music.py. The scenes cue them
with about 2 s overlaps across each hand-over. Cut points come from the `points` envelope, retimed
to the scenes' final beats (Task 13):
  first_task_tent   harry_sees_dragons 20.5-38.5 (18 s): the track is silent at 20 s and re-enters on a swell at 20.8, then
                    climbs steadily (-29 to -24 dB by 38 s) with no hit to fight the scene: the tense waiting bed,
                    out with the white fade
  first_task_arena  golden_egg 0-33 (33 s, from scene t 0.3): the track opens after a silent second on a hit at 1.0 (the
                    reveal), its hit at 9.7 lands on the roar (scene t 10.0), the hush at 21.5-22.9 holds the summon
                    ("Accio Firebolt!" at 21.8) and the big onset at 23.1 meets the Firebolt over the stands (23.4);
                    2 s over the hand-over
  first_task_chase  golden_egg 146.0-195.0 (49 s, from scene t 0): the onset at 156.0 (+8.8 dB, its peak 156.15) lands
                    on the chain snap (10.0) and the accent at 185.5 (+7.9 dB over a quieter passage) on the bridge
                    crash (39.5), the lull after it under her fall and his climb out alone; it fades out at 192.5-195.0
                    (-23 dB there) under the egg cue's first 2 s (Task 13 fix round 1: 163.0 was no hit)
  first_task_egg    golden_egg 331-349 (18 s, from scene t 0): the onsets at 336.1 and 338.2 round the west end and
                    into the dive (5.1, 7.2), the hush, then the track's biggest hit at 341.0 (+37 dB) on the grab
                    (10.0), the triumph after it, out into the wake
"""
import array
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
SRC = os.path.join(REPO, 'run', 'music', 'first_task')
OUT = os.path.join(REPO, 'src', 'main', 'resources', 'assets', 'hogwartsmagic', 'sounds', 'pensieve', 'music')

# cue: (source file, start s, end s, fade in s, fade out s)
CUES = {
    'first_task_tent': ('harry_sees_dragons', 20.5, 38.5, 0.6, 2.5),
    'first_task_arena': ('golden_egg', 0.0, 33.0, 0.3, 2.5),
    'first_task_chase': ('golden_egg', 146.0, 195.0, 1.0, 2.5),
    'first_task_egg': ('golden_egg', 331.0, 349.0, 0.4, 2.5),
}
# per cue: the measured hits (file seconds from the cut's start; `points` and a 0.05 s onset scan) and the sfx each
# lands on in its scene (first_task_scene.check_music_hits)
HITS = {
    'first_task_arena': [(9.7, 'first_task_horntail_roar')],
    'first_task_chase': [(10.0, 'first_task_chain_snap'), (39.5, 'first_task_bridge_crash')],
    'first_task_egg': [(10.0, 'first_task_egg_grab')],
}
# per source: the cue its gain is set from, and that cue's target integrated loudness (LUFS)
LEVEL = {'harry_sees_dragons': ('first_task_tent', -22.0), 'golden_egg': ('first_task_chase', -20.0)}
# per cue: extra dB on top of its source's gain
TRIM = {'first_task_tent': 2.0, 'first_task_arena': -0.9, 'first_task_egg': -1.6}   # +1.0 each with the 146 s chase cut
PEAK = -1.5            # dBTP ceiling (a limiter only engages above it)


def filt(cue, gain_db):
    _, a, b, fi, fo = CUES[cue]
    d = b - a
    return ('afade=t=in:st=0:d=%.2f,afade=t=out:st=%.3f:d=%.2f,volume=%.2fdB,alimiter=limit=%.4f:level=false'
            % (fi, d - fo, fo, gain_db, 10 ** (PEAK / 20.0)))


def loudness(path_or_args):
    args = ['ffmpeg', '-hide_banner', '-nostats'] + path_or_args + ['-af', 'ebur128=peak=true', '-f', 'null', '-']
    err = subprocess.run(args, capture_output=True, text=True).stderr
    i = re.findall(r'I:\s+(-?[\d.]+) LUFS', err)
    p = re.findall(r'Peak:\s+(-?[\d.]+) dBFS', err)
    return float(i[-1]), float(p[-1]) if p else None


def excerpt_args(cue, gain_db=0.0):
    src, a, b, _, _ = CUES[cue]
    return ['-ss', '%.3f' % a, '-t', '%.3f' % (b - a), '-i', os.path.join(SRC, src + '.mp3'), '-af',
            filt(cue, gain_db).rsplit(',alimiter', 1)[0]]


def cut():
    os.makedirs(OUT, exist_ok=True)
    gains = {}
    for src, (ref, target) in LEVEL.items():
        i, _ = loudness(excerpt_args(ref))
        gains[src] = target - i
    for cue, (src, a, b, fi, fo) in CUES.items():
        dst = os.path.join(OUT, cue + '.ogg')
        g = gains[src] + TRIM.get(cue, 0.0)
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-ss', '%.3f' % a, '-t', '%.3f' % (b - a),
                        '-i', os.path.join(SRC, src + '.mp3'), '-af', filt(cue, g), '-ac', '2',
                        '-ar', '44100', '-c:a', 'libvorbis', '-q:a', '5', dst], check=True)
        i, p = loudness(['-i', dst])
        print('%-18s %-18s %6.1f-%6.1f  gain %+5.1f dB  %5.1f LUFS  peak %s' % (cue, src, a, b, g, i, p))


def check():
    for cue, (_, a, b, _, _) in CUES.items():
        dst = os.path.join(OUT, cue + '.ogg')
        dur = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of',
                              'default=nw=1:nk=1', dst], capture_output=True, text=True).stdout.strip()
        i, p = loudness(['-i', dst])
        print('%-18s %s s (want %.1f)  %5.1f LUFS  peak %s' % (cue, dur, b - a, i, p))


def points(src):
    """Loudness envelope in 0.5 s bins (dB RMS) and the 12 strongest onsets (0.1 s frames, jump over the prior 1 s)."""
    rate = 11025
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', os.path.join(SRC, src + '.mp3'), '-ac', '1', '-ar', str(rate),
                          '-f', 's16le', '-'], capture_output=True, check=True).stdout
    s = array.array('h')
    s.frombytes(raw)
    fr = rate // 10
    db = []
    for k in range(len(s) // fr):
        seg = s[k * fr:(k + 1) * fr]
        ms = sum(x * x for x in seg) / float(fr)
        db.append(10 * __import__('math').log10(ms / 1073676289.0 + 1e-12))
    print('envelope (dB RMS per 0.5 s):')
    for k in range(0, len(db) - 4, 5):
        v = sum(db[k:k + 5]) / 5
        print('%6.1f s %6.1f  %s' % (k / 10.0, v, '#' * max(0, int((v + 60) * 1.2))))
    ons = []
    for k in range(10, len(db) - 1):
        ons.append((db[k] - sum(db[k - 10:k]) / 10, k / 10.0))
    chosen = []
    for jump, t in sorted(ons, reverse=True):
        if all(abs(t - c[1]) >= 1.5 for c in chosen):
            chosen.append((jump, t))
        if len(chosen) == 12:
            break
    print('12 strongest onsets (time order):')
    for jump, t in sorted(chosen, key=lambda c: c[1]):
        print('  %6.1f s  +%4.1f dB  (level %5.1f dB)' % (t, jump, db[int(t * 10)]))


if __name__ == '__main__':
    if sys.argv[1:] == ['check']:
        check()
    elif len(sys.argv) == 3 and sys.argv[1] == 'points':
        points(sys.argv[2])
    else:
        cut()
