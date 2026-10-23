"""Flying memory music cues: cuts the user's soundtrack files into sounds/pensieve/music/flying_*.ogg.

    python -I build_scripts/sounds/flying_music.py          cut, fade and level every cue
    python -I build_scripts/sounds/flying_music.py check    print each cue's length and loudness

The sources are film soundtrack excerpts the user supplied for private use only (run/music/flying/, not in git):
mr_longbottom_flies.mp3 ("Mr. Longbottom Flies", 215 s), quidditch_third_year.mp3 ("Quidditch, Third Year",
227 s) and the_quidditch_match.mp3 ("The Quidditch Match", 509 s). Like the Letter music the cues are for the
user's own build and are kept out of any public release.

Each cue is cut at CUES' source seconds, faded in and out (baked in: a music event plays the whole file from its t),
kept stereo at 44.1 kHz, and given one gain per source file, so cues cut from one track keep its shape. Each cue runs
a second past its scene's end: the engine fades a scene's music over the hand-over's last 20 ticks while the next
scene's cue starts at its t 0, so consecutive cues overlap by about 2 s. The cut points were picked from the
tracks' loudness envelopes (ebur128 momentary/short-term, a second at a time) so the hits land on the beats;
flying_scene.py's MUSIC_SLOTS cue them at these scene times:
  flying_lesson   flying_<h> t 2.0, source 0.0: the quiet woodwind opening under Madam Hooch's lesson and the
                  "Up!" chorus; the track's first hit (source 28.0, -30 to -16 LUFS) lands on Neville's broom
                  bolting (t 30.2), its frantic run under the bucking, the fall and "Nobody moves!"
  flying_chase    flying_<h>_chase t 0.0, source 126.05: the chase section; its peak (source 150-152) under the
                  dive and the big hit at source 156.0 on the catch (t 29.6); the hit at 163.0 on McGonagall's
                  "You. With me. Now." (t 36.3)
  flying_match    flying_<h>_match t 0.0, source 3.5: the brooding storm intro under the waiting team and the
                  captain; the theme's entry at source 14.5 on the team bursting out of the tunnel into the roar
                  (t 11.1); full theme through the whistle, the passes and the goal, the build from 42.0 under the
                  hunt
  flying_snitch   flying_<h>_snitch t 0.0, source 444.0: the near-silence of "The Quidditch Match" before its
                  finale under the hunt; its hit at 448.0 on the glint (t 4.0), the build under the dive and the
                  pull-out, the fanfare at 458.0 on the catch (t 14.0), the triumph under the erupting stands, faded
                  out as the eyes close
"""
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
SRC = os.path.join(REPO, 'run', 'music', 'flying')
OUT = os.path.join(REPO, 'src', 'main', 'resources', 'assets', 'hogwartsmagic', 'sounds', 'pensieve', 'music')

# cue: (source file, start s, end s, fade in s, fade out s); end = start + (scene length - cue t) + 1 s overlap
CUES = {
    'flying_lesson': ('mr_longbottom_flies', 0.0, 42.0, 0.8, 2.0),
    'flying_chase': ('mr_longbottom_flies', 126.05, 167.55, 1.5, 2.0),
    'flying_match': ('quidditch_third_year', 3.5, 54.5, 1.0, 2.0),
    'flying_snitch': ('the_quidditch_match', 444.0, 484.0, 0.6, 3.0),
}
# per source: the cue its gain is set from and that cue's target integrated loudness (LUFS): under the voices, the
# rain and the crowd, a little louder than the Letter's hall cues
LEVEL = {'mr_longbottom_flies': ('flying_chase', -20.0), 'quidditch_third_year': ('flying_match', -19.5),
         'the_quidditch_match': ('flying_snitch', -20.0)}
# per cue: extra dB on top of its source's gain (the lesson's quiet opening needs some presence under the lines)
TRIM = {'flying_lesson': 3.0}
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
        g = gains[src] + TRIM.get(cue, 0.0)
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-ss', '%.3f' % a, '-t', '%.3f' % (b - a),
                        '-i', os.path.join(SRC, src + '.mp3'), '-af', filt(cue, g), '-ac', '2',
                        '-ar', '44100', '-c:a', 'libvorbis', '-q:a', '5', dst], check=True)
        i, p = loudness(['-i', dst])
        out[cue] = {'seconds': round(b - a, 2), 'gain_db': round(g, 2), 'lufs': i, 'peak_dbfs': p}
        print('%-14s %-22s %6.1f-%6.1f  gain %+5.1f dB  %5.1f LUFS  peak %s' % (cue, src, a, b, g, i, p))
    return out


def check():
    for cue in CUES:
        dst = os.path.join(OUT, cue + '.ogg')
        dur = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of',
                              'default=nw=1:nk=1', dst], capture_output=True, text=True).stdout.strip()
        i, p = loudness(['-i', dst])
        print('%-14s %s s  %5.1f LUFS  peak %s' % (cue, dur, i, p))


if __name__ == '__main__':
    if sys.argv[1:] == ['check']:
        check()
    else:
        cut()
