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
- Selecting that unfinished building exposes Cancel Construction in the wheel
  and bottom-screen grid; touching it refunds the full reactor cost.
- Every visible registered game action is exported; production stays separate.
- Tactical Tools opens from the deck; matching selection, camera reset and B back
  use the original input paths.
- A stale production token cannot cancel a new item; the actual queue tap refunds
  the original $1,000 dozer cost.

## Fresh and extended fixtures

`-e fresh true -e matching true` runs the focused LT+L3 check after training a
second USA dozer. It dispatches controller keys through the activity, moves the
cursor away, selects both visible dozers, repeats the shortcut, pans away and
checks that it cannot recruit an off-screen match. It also checks an empty
selection and that unmodified L3 still centres the camera.

Use `-e fresh true` with `am instrument` to start a new skirmish using your saved
skirmish setup instead of loading a save. The normal/extended checks need USA,
radar, sufficient money and clear ground around the command center. With
`-e extended true`, the harness builds a reactor, barracks and fire base through
ordinary gameplay, produces/garrisons two Rangers, then checks individual exit
buttons and stale occupant protection. `-e inventory true` runs only that longer
fixture after starting the skirmish. It also checks the original construction
radar alert. No test map, retail override or engine cheat is installed.

`-e fresh true -e faction China` chooses China in the setup dropdown and runs a
separate reactor Overcharge on/off check. This changes your saved skirmish faction
preference; record/restore your setup before using it. The fixture's construction
checks wait for a foundation to enter construction and then complete, and wait
for a production queue to appear before treating its empty state as completion.

These checks still restart the app. Save first, and reload your own match after
testing. They do not save the temporary skirmishes over your existing saves.

Gesture events go through the activity's normal dispatch and SDL. Controller
axes use the same JNI queue as the physical controller. The tests complement
hands-on checks of actual buttons, hardware touch delivery and comfort; they do
not measure long-session stability or cover every building/placement edge case.

## Menu, movie and snapped-press regressions

The October 18 fixes have focused fixtures, using the same test APK and runner:

```sh
adb shell am instrument -w -e menus true com.generalsthor.zerohour.test/com.generalsx.zerohour.ThorInteractionChecks
adb shell am instrument -w -e movies true com.generalsthor.zerohour.test/com.generalsx.zerohour.ThorInteractionChecks
adb shell am instrument -w -e fresh true -e snap true com.generalsthor.zerohour.test/com.generalsx.zerohour.ThorInteractionChecks
```

`menus` checks all six main-menu buttons, D-pad focus against the visible deck,
Options isolation, unique window IDs and one Extras button after reopening.
`movies` starts USA on Easy through the bottom-screen buttons, skips the briefing
with Start, checks that controller input cannot select during the opening scene,
then waits for the scripted camera to finish and control to return without a
queued pause. It takes about two minutes and does not complete the mission.
`snap` starts a USA skirmish, finds an off-center snapped dozer and holds A before
releasing it. The cursor must stay fixed and the intended dozer must be selected.

For uninterrupted video playback, enable the existing audio trace option and
restart the game before recording. Play the startup or briefing video, collect
`generals-stderr.log`, then check the actual decoder/presentation trace:

```sh
python scripts/qa/check-movie-playback.py generals-stderr.log --require-complete 1
```

Add `--require-skipped 1` when the recording also contains an intentional skip.
The check expects sequential full-screen movies, without overlapping window
videos. It rejects missing/out-of-order presentation frames and incomplete log
recordings. Retail movies and extracted mission scripts stay outside source
history. The tests do not establish that every campaign mission works.

## Briefing audio and offline campaign review

The seventh host check in `test-thor.sh` covers the duration calculation used by
frozen black-and-white briefings: narration fits before the next cut, fade offsets
stay intact, and missing audio or already-long timers receive no extension. The
engine uses asset duration and fixed simulation frames, never hardware playback
state, to compute this extension. An explicit reviewed catalog also covers
individual dialogue waits in ordinary campaign scripts. Its map, script, timer
and authored duration must match. Skirmish, multiplayer, replay, random timers and
unlisted camera/combat/FX waits retain their timing.

To check an actual briefing recording, collect the existing audio trace and pass
the expected event names in order. For the USA 1 opening briefing and handoff:

```sh
python scripts/qa/check-briefing-playback.py generals-stderr.log --events MisUSA01xBrf01,MisUSA01xBrf02,MisUSA01xBrf03,MisUSA01xBrf04,MisUSA01xBrf06,MisUSA01xBrf05,MisUSA01xBrf07
```

This requires complete start/end traces. It checks order, completion and overlap;
the player still judges shot choice, effects and comfortable volume.

The offline audit does not connect to the Thor or launch a mission:

```sh
python scripts/qa/audit-campaign-cinematics.py "PATH/TO/OWNED/ZERO HOUR" --out artifacts/campaign-audit
python scripts/qa/audit-campaign-cinematics.py "PATH/TO/OWNED/ZERO HOUR" --out artifacts/campaign-audit --ffmpeg "PATH/TO/ffmpeg"
```

Python 3.9+ is required; FFmpeg is optional. Point it at the English Steam install
containing the `ZH_Generals` base-data folder. It reads BIG archives and loose
overrides, resolves default/expansion video and speech definitions, dumps mission
scripts and writes `audit.json`. The FFmpeg option decodes video and audio to a
null output and compares decoded video-frame counts with the Bink header.

Outputs include owned maps and script dumps. Keep them in ignored `artifacts/`,
never source history. A short timer is a review candidate: camera effects, radio
chatter, independent timer branches and inactive scripts can overlap on purpose.
The audit does not rewrite retail assets or certify visual/playthrough parity.
Missing main-campaign movies, decoder failures or unhandled cinematic actions
return a failure status. Other unresolved references remain in the report for
review, including retail's unused training/victory video definitions.

The [October 19 review](../WORKDIR/reports/THOR-CINEMATICS-2026-10-19.md) records
the checked scope and separates actual Thor observations from offline checks.

## Reviewed dialogue dependencies

`Core/GameEngine/Include/Common/CampaignDialogueCues.def` is the source for the
reviewed waits. Each record identifies the timer writer, speech source and next
dialogue/completion script. It contains identifiers and timing metadata, with no
retail map or audio bytes. The two USA 3 sibling-script entries are intentional.

Check it against your owned English install and exercise those actual durations
through the same C++ timing/identity functions used by the engine:

```sh
python scripts/qa/check-campaign-dialogue-timing.py "PATH/TO/OWNED/ZERO HOUR" --out artifacts/dialogue-review
bash scripts/qa/test-thor.sh
build/thor-tests/briefing artifacts/dialogue-review/timing-vectors.tsv
```

The Python check verifies unique writers, authored timer values, speech assets,
sibling triggers, all timer readers and their presentation actions. Inactive
readers with no script reference are reported separately; adding an activation
or a new non-presentation action makes verification fail. It writes raw owned
maps/dumps alongside vectors, so keep the output ignored and delete it after
checking if desired. The C++ check verifies every catalog entry and guards
against applying a correction to a different map, changed wait or adjacent
camera/FX timer. Already-long waits stay unchanged; unavailable speech does not
invent a replacement duration.

The October 19 pass checked 37 dependencies in eight missions. Twenty-nine
English waits required an extension. These are offline checks, not a later-mission
playthrough or a claim of exact camera/voice synchronization throughout the game.
