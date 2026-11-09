#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
./build.sh
mkdir -p build/test-classes
find tests -name '*.java' -print | LC_ALL=C sort > build/tests.txt
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" --release 17 -encoding UTF-8 -cp build/classes -d build/test-classes @build/tests.txt
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -Djava.awt.headless=true -cp build/classes:build/test-classes ArcadeTests
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -Djava.awt.headless=true -cp build/classes:build/test-classes NeuralTests
