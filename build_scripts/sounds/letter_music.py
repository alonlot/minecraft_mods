"""Letter memory music cues: cuts the user's soundtrack files into sounds/pensieve/music/letter_*.ogg.

    python -I build_scripts/sounds/letter_music.py          cut, fade and level every cue
    python -I build_scripts/sounds/letter_music.py check    print each cue's length and loudness

The sources are film soundtrack excerpts the user supplied for private use only (run/music/letter/, not in git):
zoo_and_letters.mp3 ("Visit to the Zoo and Letters from Hogwarts"), wish_and_hagrid.mp3 and hedwigs_theme.mp3.
Each cue is cut at CUES' source seconds, faded in and out over the given seconds (baked in: a music event plays the
whole file from its t), kept stereo at 44.1 kHz like the hall cues, and given one gain per source file so the
loudness of the cues cut from one track keeps the track's own shape (the flood swell stays louder than the quiet
opening under the cupboard). letter_scene.py cues them at the scene times in its MUSIC_AT, which these cut points
are timed against:
  letter_cupboard  letter t 2.0: the quiet opening of "Letters from Hogwarts" under the cupboard, building through
                   the montage of letters, out with the chain fade
  letter_flood     letter_flood t 0: source 168.3 (the big swell) lands on the burst at t 10.5
  letter_crossing  letter_crossing t 0: the storm crossing
  letter_hut       letter_hut t 0: the quiet bed under the sleeping hut, the hit at source 40.3 on the door crash
                   (t 20.8), faded by the reveal
  letter_wizard    letter_hut t 34.6: the celesta of Hedwig's theme from "You're a wizard" to the end
"""
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
SRC = os.path.join(REPO, 'run', 'music', 'letter')
OUT = os.path.join(REPO, 'src', 'main', 'resources', 'assets', 'hogwartsmagic', 'sounds', 'pensieve', 'music')

# cue: (source file, start s, end s, fade in s, fade out s)
CUES = {
    'letter_cupboard': ('zoo_and_letters', 100.0, 154.0, 1.0, 2.5),
    'letter_flood': ('zoo_and_letters', 157.8, 187.8, 0.3, 1.2),
    'letter_crossing': ('wish_and_hagrid', 0.0, 17.0, 0.8, 1.5),
    'letter_hut': ('wish_and_hagrid', 19.5, 55.0, 1.5, 3.5),
    'letter_wizard': ('hedwigs_theme', 0.0, 26.6, 0.6, 2.0),
}
# per source: the cue its gain is set from, and that cue's target integrated loudness (LUFS); the hall cues sit at
# about -21 to -23
LEVEL = {'zoo_and_letters': ('letter_flood', -20.0), 'wish_and_hagrid': ('letter_hut', -21.5),
         'hedwigs_theme': ('letter_wizard', -24.0)}
# per cue: extra dB on top of its source's gain (the quiet cupboard opening and the crossing under the storm need
# more presence than the track's own shape gives them)
TRIM = {'letter_cupboard': 4.0, 'letter_crossing': 3.0}
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
    out = {}
    for cue, (src, a, b, fi, fo) in CUES.items():
        dst = os.path.join(OUT, cue + '.ogg')
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-ss', '%.3f' % a, '-t', '%.3f' % (b - a),
                        '-i', os.path.join(SRC, src + '.mp3'), '-af', filt(cue, gains[src] + TRIM.get(cue, 0.0)), '-ac', '2',
                        '-ar', '44100', '-c:a', 'libvorbis', '-q:a', '5', dst], check=True)
        i, p = loudness(['-i', dst])
        g = gains[src] + TRIM.get(cue, 0.0)
        out[cue] = {'seconds': round(b - a, 2), 'gain_db': round(g, 2), 'lufs': i, 'peak_dbfs': p}
        print('%-16s %-16s %6.1f-%6.1f  gain %+5.1f dB  %5.1f LUFS  peak %s' % (cue, src, a, b, g, i, p))
    return out


def check():
    for cue in CUES:
        dst = os.path.join(OUT, cue + '.ogg')
        dur = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of',
                              'default=nw=1:nk=1', dst], capture_output=True, text=True).stdout.strip()
        i, p = loudness(['-i', dst])
        print('%-16s %s s  %5.1f LUFS  peak %s' % (cue, dur, i, p))


if __name__ == '__main__':
    if sys.argv[1:] == ['check']:
        check()
    else:
        cut()
