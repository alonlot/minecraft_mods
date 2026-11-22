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
