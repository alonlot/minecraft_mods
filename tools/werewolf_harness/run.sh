#!/usr/bin/env bash
# Werewolf preview harness.
#
#   bash tools/werewolf_harness/run.sh OUT.json label=Class.field@ms ...
#
# Compiles the werewolf model + clip classes fresh from src/ against a compiled sourcesSets/main
# (default the hp-verify side checkout), poses the real ModelPart tree, dumps textured quads.
# Then: python -I tools/werewolf_harness/render_werewolf.py OUT.json <texture> sheet.png 30,120,210 [labels]
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
CLASSES="${CLASSES:-C:/Users/alonl/hp-verify/build/sourcesSets/main}"
JDK="${JDK:-/c/Program Files/Java/jdk-21.0.10/bin}"
OUT="${OUT_DIR:-$HERE/out}"
MCV=$(grep '^minecraft_version=' "$ROOT/gradle.properties" | cut -d= -f2 | tr -d '\r')
FV=$(grep '^forge_version=' "$ROOT/gradle.properties" | cut -d= -f2 | tr -d '\r')
MC=$(ls ~/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/${MCV}-${FV}_mapped_official_*/forge-*-recomp.jar | head -1)
mkdir -p "$OUT/classes"
if [ ! -f "$OUT/libs.txt" ]; then
  find ~/.gradle/caches/modules-2/files-2.1 -name "*.jar" | grep -v -- -sources | xargs -n1 cygpath -m | tr '\n' ';' > "$OUT/libs.txt"
fi
printf -- '-cp\n"%s;%s;%s;%s"\n' "$(cygpath -m "$OUT/classes")" "$CLASSES" "$(cygpath -m "$MC")" "$(cat "$OUT/libs.txt")" > "$OUT/cp.args"
M="$ROOT/src/main/java/com/hogwartsmagic/client/model"
shopt -s nullglob
SRCS=("$M"/WhulvkWerewolfLycanModel.java "$M"/WhulvkLycan*.java "$M"/Werewolf*.java "$HERE/WerewolfHarness.java")
"$JDK/javac" -nowarn -implicit:none @"$OUT/cp.args" -d "$OUT/classes" "${SRCS[@]}"
"$JDK/java" @"$OUT/cp.args" WerewolfHarness "$@" 2>&1 | grep -v SLF4J
