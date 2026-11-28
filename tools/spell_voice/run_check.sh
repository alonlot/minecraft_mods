#!/usr/bin/env bash
# Recognise every spell voice line with the game's whisper model + IncantationMatcher. Args: optional spell ids.
set -e
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JAR="$(find ~/.gradle/caches/modules-2 -name 'whisper-jni-1.7.1.jar' | head -1)"
OUT="${TMPDIR:-${TEMP:-/tmp}}/spell_voice_check"
mkdir -p "$OUT"
SEP=":"; case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";"; JAR="$(cygpath -w "$JAR")"; OUT="$(cygpath -w "$OUT")";; esac
javac -d "$OUT" -cp "$JAR" "$ROOT/src/main/java/com/hogwartsmagic/spell/voice/IncantationMatcher.java" \
    "$ROOT/tools/spell_voice/CheckSpellVoice.java"
java -cp "$OUT$SEP$JAR" CheckSpellVoice "$ROOT" "$@"
