#!/usr/bin/env bash
# Bake the robe models (and the vanilla layers the trousers and boots ride on) and dump their quads to
# out/quads.json for paint_robes.py. Needs this worktree compiled (gradlew compileJava).
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
CLASSES="${1:-$ROOT/build/sourcesSets/main}"
JDK="${JDK:-/c/Program Files/Java/jdk-21.0.10/bin}"
OUT="$HERE/out"
MCV=$(grep '^minecraft_version=' "$ROOT/gradle.properties" | cut -d= -f2 | tr -d '\r')
FV=$(grep '^forge_version=' "$ROOT/gradle.properties" | cut -d= -f2 | tr -d '\r')
MC=$(ls ~/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/${MCV}-${FV}_mapped_official_*/forge-*-recomp.jar | head -1)
LIBS=$(find ~/.gradle/caches/modules-2/files-2.1 -name "*.jar" | grep -v -- -sources | xargs -n1 cygpath -m | tr '\n' ';')
mkdir -p "$OUT/classes"
printf -- '-cp\n"%s;%s;%s;%s"\n' "$(cygpath -m "$OUT/classes")" "$(cygpath -m "$CLASSES")" "$(cygpath -m "$MC")" "$LIBS" > "$OUT/cp.args"
"$JDK/javac" -nowarn -implicit:none @"$OUT/cp.args" -d "$OUT/classes" \
  "$(cygpath -m "$ROOT/tools/texture_audit/AtlasDump.java")" "$(cygpath -m "$HERE/ArmorLayers.java")"
M=com.hogwartsmagic.client.model
"$JDK/java" @"$OUT/cp.args" AtlasDump "$OUT/quads.json" \
  hood=$M.WizardHoodModel#createBodyLayer robe=$M.WizardRobeModel#createBodyLayer \
  player=ArmorLayers#player inner=ArmorLayers#inner outer=ArmorLayers#outer 2>&1 | grep -v SLF4J
