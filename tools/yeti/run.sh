#!/usr/bin/env bash
# Yeti preview harness (Linux).
#
#   ./gradlew compileJava && bash tools/yeti/run.sh OUT.json idle=NONE,0,0,0,0,0,0
#   python3 tools/werewolf_harness/render_werewolf.py OUT.json \
#       src/main/resources/assets/hogwartsmagic/textures/entity/yeti.png sheet.png 30,120,210
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
OUT="${OUT_DIR:-$HERE/out}"
MC=$(ls ~/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/*_mapped_official_*/forge-*.jar | grep -v sources | head -1)
mkdir -p "$OUT/classes"
LIBS=$(find ~/.gradle/caches/modules-2/files-2.1 -name "*.jar" | grep -v -- -sources | tr '\n' ':')
CP="$OUT/classes:$ROOT/build/sourcesSets/main:$MC:$LIBS"
javac -nowarn -cp "$CP" -d "$OUT/classes" "$HERE/YetiHarness.java"
java -cp "$CP" YetiHarness "$@" 2>&1 | grep -v SLF4J
