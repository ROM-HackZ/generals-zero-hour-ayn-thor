#!/usr/bin/env bash
# GeneralsX @test Codex 14/10/2026 Small host checks for the controller UI and artwork bridge.
set -euo pipefail
REPO=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
JAVA="$REPO/android/app/src/main/java/com/generalsx/zerohour"
TEST="$REPO/build/thor-tests"
mkdir -p "$TEST"
javac -d "$TEST" "$JAVA/ThorControls.java" "$JAVA/ThorDeckLayout.java" \
    "$REPO/scripts/qa/ThorControlsTest.java" "$REPO/scripts/qa/ThorDeckLayoutTest.java"
java -cp "$TEST" com.generalsx.zerohour.ThorControlsTest
java -cp "$TEST" com.generalsx.zerohour.ThorDeckLayoutTest
c++ -std=c++17 -I "$REPO/GeneralsMD/Code/GameEngineDevice/Include" "$REPO/scripts/qa/ThorArtworkTest.cpp" -o "$TEST/artwork"
"$TEST/artwork"
