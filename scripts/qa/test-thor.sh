#!/usr/bin/env bash
# GeneralsX @test Codex 14/10/2026 Small host checks for the controller UI and artwork bridge.
set -euo pipefail
REPO=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
JAVA="$REPO/android/app/src/main/java/com/generalsx/zerohour"
TEST="$REPO/build/thor-tests"
mkdir -p "$TEST"
javac -d "$TEST" "$JAVA/ThorCommands.java" "$JAVA/ThorControls.java" "$JAVA/ThorDeckLayout.java" "$JAVA/ThorRadarTouch.java" \
    "$REPO/scripts/qa/ThorCommandsTest.java" \
    "$REPO/scripts/qa/ThorControlsTest.java" "$REPO/scripts/qa/ThorDeckLayoutTest.java" "$REPO/scripts/qa/ThorRadarTouchTest.java"
java -cp "$TEST" com.generalsx.zerohour.ThorControlsTest
java -cp "$TEST" com.generalsx.zerohour.ThorCommandsTest
java -cp "$TEST" com.generalsx.zerohour.ThorDeckLayoutTest
java -cp "$TEST" com.generalsx.zerohour.ThorRadarTouchTest
c++ -std=c++17 -I "$REPO/GeneralsMD/Code/GameEngineDevice/Include" "$REPO/scripts/qa/ThorArtworkTest.cpp" -o "$TEST/artwork"
"$TEST/artwork"
c++ -std=c++17 -I "$REPO/Core/GameEngineDevice/Include" "$REPO/scripts/qa/ThorRadarTest.cpp" -o "$TEST/radar"
"$TEST/radar"

c++ -std=c++17 -I "$REPO/Core/GameEngine/Include" "$REPO/scripts/qa/ThorBriefingTimingTest.cpp" -o "$TEST/briefing"
"$TEST/briefing"
