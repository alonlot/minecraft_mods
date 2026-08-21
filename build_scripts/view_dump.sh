#!/bin/bash
# Put a procedurally-carved undercroft dump in front of the deepslate viewer.
#
#   build_scripts/view_dump.sh run/undercroft_dumps/corridor_d122.nbt
#
# The undercroft is generated rather than authored, so unlike every .nbt in this repo it has no
# file to render until /undercroftdump writes one. This copies that file next to the viewer's
# other templates, refreshes the shared texture atlas, and prints the URL to open.
set -e

NBT="$1"
[ -f "$NBT" ] || { echo "no such dump: $NBT" >&2; exit 1; }

SKILL=.claude/skills/rendering-minecraft-structures
JAR="$HOME/.gradle/caches/forge_gradle/minecraft_repo/versions/1.21/client-extra.jar"

cp "$NBT" "$SKILL/textured/build/"

# Only the authored structures plus this dump. Passing the build directory's own copies back
# in makes export_assets try to copy each file onto itself.
python "$SKILL/textured/export_assets.py" \
    src/main/resources/data/hogwartsmagic/structure/*.nbt "$NBT" \
    --jar "$JAR" --mod src/main/resources/assets/hogwartsmagic

NAME=$(basename "$NBT")
echo
echo "open:  http://localhost:8731/?nbt=$NAME&cut=z"
echo "       (the undercroft is sealed rock -- without a cutaway you see a grey box)"
