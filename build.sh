#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAR="${JAVA_HOME:+$JAVA_HOME/bin/}jar"
if ! command -v "$JAVAC" >/dev/null 2>&1; then
    echo 'JDK 17+ required, including javac. Set JAVA_HOME or install a JDK.' >&2
    exit 1
fi
rm -rf build/classes
mkdir -p build/classes
find src -name '*.java' -print | LC_ALL=C sort > build/sources.txt
"$JAVAC" --release 17 -encoding UTF-8 -d build/classes @build/sources.txt
mkdir -p build/classes/models
for model in models/*.nn; do
    if [ -f "$model" ]; then cp "$model" build/classes/models/; fi
done
"$JAR" --create --file build/ia-arcade.jar --main-class arcade.Launcher -C build/classes .
echo 'Built: build/ia-arcade.jar'
