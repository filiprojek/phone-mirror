#!/usr/bin/env bash
# Regenerate the README screenshots in docs/screenshots. The screens are rendered on the JVM
# (Robolectric + Roborazzi) with fake state; the camera footage is a drawn stand-in.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
mkdir -p docs/screenshots
./gradlew :app:testDebugUnitTest -Pshowcase --rerun "$@"
ls -1 docs/screenshots
