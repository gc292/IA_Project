#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
./scripts/build.sh
exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" -jar build/ia-arcade.jar "$@"
