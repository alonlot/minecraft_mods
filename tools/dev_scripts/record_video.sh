#!/usr/bin/env bash
# Records a dev script as a video, on a private X display (Linux, Xvfb, Mesa, ffmpeg).
#
#   bash tools/dev_scripts/record_video.sh tools/dev_scripts/tomb_curses_video.txt curse_video OUT_DIR
#
# Writes OUT_DIR/raw.mkv and OUT_DIR/client.log. The script's "mark start" and "mark end" lines log
# wall-clock times; cut between them and speed the cut up by the script's slow-down (see README.md).
set -u
SCRIPT=$(realpath "$1"); WORLD=$2; S=$(realpath "$3")
cd "$(dirname "$0")/../.."
Xvfb :77 -screen 0 1280x720x24 -nolisten tcp > $S/xvfb.log 2>&1 &
XPID=$!
sleep 2
export DISPLAY=:77 LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.5 MESA_GLSL_VERSION_OVERRIDE=450
export HOGWARTSMAGIC_DEV_SCRIPT=$SCRIPT HOGWARTSMAGIC_DEV_WORLD=$WORLD
./gradlew runClient --args="--width 1280 --height 720" > $S/client.log 2>&1 &
GPID=$!
# Start recording once the world is open and the script is running.
until grep -q 'Dev script: running' $S/client.log; do
  sleep 1
  kill -0 $GPID 2>/dev/null || { echo "client died"; kill $XPID; exit 1; }
done
date +%s%3N > $S/rec_start_ms
ffmpeg -y -loglevel error -f x11grab -framerate 30 -video_size 1280x720 -i :77.0 \
  -c:v libx264 -preset ultrafast -crf 18 $S/raw.mkv &
FPID=$!
wait $GPID
kill -INT $FPID; wait $FPID
kill $XPID
echo done
