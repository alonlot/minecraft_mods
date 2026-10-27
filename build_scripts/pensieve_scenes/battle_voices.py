"""Voices the Battle of Hogwarts memory's lines with Fish Audio and registers them.

    python -I build_scripts/pensieve_scenes/battle_voices.py [--redo name ...]

Reads battle_voice_lines.json (written by battle_scenes.py), and for every line without an OGG yet: Fish TTS
(speech-1.6, the character's voice clone below; the key is read from C:/Users/alonl/fish_key.txt and never
printed) -> ffmpeg: silence trimmed at both ends, loudnorm I=-16 TP=-1.5, mono 44.1 kHz Vorbis ->
assets/hogwartsmagic/sounds/pensieve/voice/<name>.ogg. Then every line is registered as pensieve.voice.<name> in
sounds.json and in ModSounds (between the BATTLE VOICES markers). Re-run battle_scenes.py afterwards: the subtitles
take each clip's length.

The voice ids are actor clones the user curated for private use (harry-potter-dnd/game.js HP_FISH_VOICES); swap
them before any release. Molly has no clone on Fish: the adult (post-war) Hermione voice stands in, and Seamus uses
the Fred Weasley clone.
"""
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
ASSETS = os.path.join(REPO, 'src', 'main', 'resources', 'assets', 'hogwartsmagic')
VOICE_DIR = os.path.join(ASSETS, 'sounds', 'pensieve', 'voice')
SOUNDS_JSON = os.path.join(ASSETS, 'sounds.json')
MOD_SOUNDS = os.path.join(REPO, 'src', 'main', 'java', 'com', 'hogwartsmagic', 'ModSounds.java')
KEY_FILE = r'C:\Users\alonl\fish_key.txt'

VOICES = {   # speaker: (reference_id, speed)
    'harry': ('63efa34dab914c5085b89bcc8356ea94', 1.0),
    'ron': ('5087254d8fe8466cb5e750a1b0dd44a4', 1.05),
    'hermione': ('4a5f1d19bfed442bb0ff388a669077a3', 1.05),
    'mcgonagall': ('d625beac29e2417faba8ace4c3d6ccdd', 0.95),
    'voldemort': ('db84ac02993a4f2f8ff4370258d61d7f', 0.85),
    'neville': ('ebe169afb0934b5bbcc5c1ed62a024b5', 1.0),
    'bellatrix': ('239e968e73804926b586790018c42db1', 1.0),
    'flitwick': ('041630e2d9c34042b6c0c09733916bcc', 1.0),
    'hagrid': ('f6fd51174e2743db91c4e91fe5de4ec1', 0.95),
    'molly': ('7c4851eb9296426dac8935cc0e2c8828', 1.0),
    'seamus': ('c787028a18454607958de8a2458154b4', 1.05),
    'narrator': ('90dfa283adee46eea0f69383dc5ab8f3', 0.9),
}
BEGIN = '    // BATTLE VOICES BEGIN (battle_voices.py writes this block)'
END = '    // BATTLE VOICES END'


def tts(text, ref, speed, out_mp3):
    key = open(KEY_FILE).read().strip()
    body = json.dumps({'text': text, 'reference_id': ref, 'format': 'mp3', 'prosody': {'speed': speed}}).encode()
    req = urllib.request.Request('https://api.fish.audio/v1/tts', data=body, method='POST',
                                 headers={'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json',
                                          'model': 'speech-1.6'})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                data = r.read()
            if len(data) < 1000:
                raise RuntimeError('short response %d bytes' % len(data))
            with open(out_mp3, 'wb') as f:
                f.write(data)
            return
        except Exception as e:      # noqa: BLE001 (retry any network hiccup)
            if attempt == 2:
                raise
            print('  retry after', e)
            time.sleep(3)


def to_ogg(mp3, ogg):
    af = ('silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,areverse,'
          'silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.08,areverse,'
          'loudnorm=I=-16:TP=-1.5:LRA=11')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', mp3, '-af', af, '-ac', '1', '-ar', '44100',
                    '-c:a', 'libvorbis', '-q:a', '5', ogg], check=True)


def register(names):
    with open(SOUNDS_JSON, encoding='utf-8') as f:
        sounds = json.load(f)
    for n in names:
        sounds['pensieve.voice.' + n] = {'sounds': [{'name': 'hogwartsmagic:pensieve/voice/' + n, 'stream': False}]}
    with open(SOUNDS_JSON, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(sounds, f, indent=2, ensure_ascii=False)
        f.write('\n')
    src = open(MOD_SOUNDS, encoding='utf-8').read()
    nl = '\r\n' if '\r\n' in src else '\n'
    block = [BEGIN, '    // Voice lines of the Battle of Hogwarts memory (Fish Audio takes; played by the scenes\' voice cues).']
    for n in names:
        block.append('    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_%s = register("pensieve.voice.%s");'
                     % (n.upper(), n))
    block.append(END)
    text = nl.join(block)
    if BEGIN in src:
        i0 = src.index(BEGIN)
        i1 = src.index(END, i0) + len(END)
        src = src[:i0] + text + src[i1:]
    else:
        anchor = '    // Great Hall memory: ElevenLabs effects and Fish Audio voices'
        i = src.index(anchor)
        src = src[:i] + text + nl + nl + src[i:]
    with open(MOD_SOUNDS, 'w', encoding='utf-8', newline='') as f:
        f.write(src)


def main():
    redo = set(sys.argv[sys.argv.index('--redo') + 1:]) if '--redo' in sys.argv else set()
    lines = json.load(open(os.path.join(HERE, 'battle_voice_lines.json'), encoding='utf-8'))
    os.makedirs(VOICE_DIR, exist_ok=True)
    tmp = tempfile.mkdtemp(prefix='battle_voices_')
    for ln in lines:
        ogg = os.path.join(VOICE_DIR, ln['name'] + '.ogg')
        if os.path.exists(ogg) and ln['name'] not in redo:
            continue
        ref, speed = VOICES[ln['speaker']]
        mp3 = os.path.join(tmp, ln['name'] + '.mp3')
        print('voicing', ln['name'], '(%s)' % ln['speaker'])
        tts(ln['text'], ref, speed, mp3)
        to_ogg(mp3, ogg)
    register([ln['name'] for ln in lines])
    print('registered', len(lines), 'lines')


if __name__ == '__main__':
    main()
