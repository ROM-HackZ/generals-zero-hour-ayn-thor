# Local Thor interaction checks

`android/app/src/androidTest/` contains a separate instrumentation app that checks
the actual SDL gesture handling, native controller intents and second-screen deck.
It is not included in the player APK and does not need a CI runner or extra test
libraries. The smaller host checks remain `bash scripts/qa/test-thor.sh`.

## Before running

Build and install the current Thor APK using the [local build guide](THOR.md).
Use the same `THOR_SIGNING_KEY` environment setting for the test APK.

**Save your current match first.** Android instrumentation restarts the app. The
checks load your newest local save and temporarily construct a building in that
loaded match. They do not save over it. The fixture needs a USA skirmish with radar,
an idle construction dozer, enough funds for a reactor and free space near the
command center. A different save may fail fixture assumptions.

## Build and run

With the Android SDK, Gradle and signing environment from the build guide:

```sh
cd android
gradle --no-daemon :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r \
  com.generalsthor.zerohour.test/com.generalsx.zerohour.ThorInteractionChecks
```

Use `adb -s SERIAL` when more than one device is connected. When building under
WSL with a Thor connected to Windows, use Windows ADB to install the test APK via
its `\\wsl.localhost\Ubuntu\...` path; WSL does not automatically own the USB device.

Read the final output: **`INSTRUMENTATION_CODE: 0`** and the passed message mean
success. ADB itself may return exit code zero even if the test reports a failure.
Afterward, open Thor RTS and reload the saved match to discard test construction.
The test package can be removed with `adb uninstall com.generalsthor.zerohour.test`;
that is separate from the game and its saves.

## What it checks

- Quick one-finger rectangle selection, with a stationary camera.
- Adding a second finger cancels the box and preserves the previous selection.
- Two-finger pan, pinch, twist and consumption of the remaining camera finger.
- Minimap taps and held-finger drag at measured view coordinates, compared with
  the expected world cells on a rectangular map.
- First-A lock without spending; left-stick facing with a stationary camera.
- A world-fixed building site while the camera rotates and zooms.
- B cancellation without spending, then a new preview and final-A construction.

Gesture events go through the activity's normal dispatch and SDL. Controller
axes use the same JNI queue as the physical controller. The tests complement
hands-on checks of actual buttons, hardware touch delivery and comfort; they do
not measure long-session stability or cover every building/placement edge case.
