# Dev scripts

Scripts for `client/DevScript.java`: the client runs them with nobody at the keyboard and saves
screenshots to `run/screenshots/`.

Headless (Linux, Xvfb and Mesa), for example the tomb curse effects:

    HOGWARTSMAGIC_DEV_SCRIPT=$PWD/tools/dev_scripts/tomb_curses.txt \
    HOGWARTSMAGIC_DEV_WORLD=curse_demo \
    LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 MESA_GLSL_VERSION_OVERRIDE=450 \
    xvfb-run -a -s "-screen 0 1400x900x24" ./gradlew runClient --args="--width 1280 --height 720"

The world is created flat (creative, commands on) the first time. On a new `run/` folder, put
`onboardAccessibility:false` and `mipmapLevels:0` in `run/options.txt`: the first speeds past the
first-launch screen, the second saves minutes of texture work on software OpenGL. Start-up takes
about fifteen minutes that way; the script itself runs in game time.

`niffler.txt` reviews the Niffler (model, full pouch, sitting, the tickle, a theft, a treasure find).
Run it in its own flat world, for example `HOGWARTSMAGIC_DEV_WORLD=niffler_demo`: its coordinates
expect the flat world's surface at y -60.

`yeti.txt` reviews the Yeti and its cave (the cave outside and in, the sleeping Yeti, then a fight in
the open with a held torch and with fire). Run it in its own flat world, for example
`HOGWARTSMAGIC_DEV_WORLD=yeti_demo`. The player gets night vision and full resistance so the script
survives the fight.

## Videos

`castle_mechanisms.txt` records the Mini Hogwarts moving parts as a video: it spawns a castle in a new flat
world (`HOGWARTSMAGIC_DEV_WORLD=castle_video`), says the password to the gargoyle in chat (the `chat` line),
then shows the stair rising, the office and its book gallery, the moving flight swinging away and back, and
the stair sinking. It slows the game with `/tick rate 4` and saves one frame per game tick
(`screenshots/vid_0001.png` and on), so frames joined at 20 fps play at real speed:

    ffmpeg -framerate 20 -i run/screenshots/vid_%04d.png -c:v libx264 -pix_fmt yuv420p mini_hogwarts.mp4

Add `tutorialStep:none` to `run/options.txt` to hide the "Move with W, A, S and D" box. There is no sound:
Xvfb has no audio device.

`tomb_curses_video.txt` is the tomb curses as a video. Record it with
`bash tools/dev_scripts/record_video.sh tools/dev_scripts/tomb_curses_video.txt curse_video OUT_DIR`.
The script slows the game to a quarter speed (`/tick rate 5`) between `mark start` and `mark end`, so
software rendering still draws every moment; the log gives each mark's wall-clock time, and
`rec_start_ms` the recording's start. Then:

    ffmpeg -ss <start - rec_start, seconds> -t <(end - start) seconds> -i OUT_DIR/raw.mkv \
      -vf "setpts=PTS/4,fps=30" -c:v libx264 -crf 20 -pix_fmt yuv420p out.mp4

The Collapse buries the third-person camera at the very end: cut before the frame goes black.
