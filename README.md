# Thor RTS

A fan-made Android port for playing your own copy of **Command & Conquer:
Generals Zero Hour** on the **AYN Thor**, with a controller interface built for
its buttons, sticks and two screens.

The top screen is the battlefield. The bottom holds the radar, your selection,
commands, production and General's Powers. Move the camera with the left stick,
rotate and zoom with the right, and use the wheels for commands and control groups.
Touch works too.

This is a modified community port, built from the released engine source. It runs
natively on Android and uses your PC installation's game data and the original
30 Hz simulation. **No retail game data is included. You need your own copy.**

**[Desktop installer](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/releases/tag/installer-v0.1.0)**
· [APK only](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/releases/tag/v0.1.0-alpha.1)
· [Install it](docs/HOWTO/THOR_INSTALL.md)
· [Controls](docs/HOWTO/THOR_CONTROLS.md)
· [Build from source](docs/BUILD/THOR.md)

## What's in it

| Part | What it does |
|---|---|
| Camera | Left-stick movement; right-stick rotation and zoom. Hold R3 for a precision cursor. |
| Command wheel | Hold RT, choose an icon with the left stick, press A. Costs and short details are in the center; hold Y for the full description. |
| Control groups | Hold LB to recall, assign, add to or center one of ten groups. The wheel shows unit counts and empty slots. |
| Bottom screen | The map, unit/building portrait and health, a command grid, and a separate production strip. View puts controller focus here. |
| Radar | The game's radar and fog, with an outline showing the camera's view. RB switches to map control. |
| General's Powers | Names, point costs and learned state. Choose a power through the same deck or wheel. |
| Menus | Controller focus, value editing and an on-screen text keyboard. |
| Settings | Button remapping, dead zone, camera speed, pan/zoom direction and snapping. |

Game preview **0.1.0-alpha.2** adds two-step building placement (A chooses the
site, left stick chooses facing, A builds; B cancels), one-finger touch selection,
and corrected bottom-screen minimap taps plus drag-to-pan. Pinch/twist camera
controls continue to work. [Download that APK](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/releases/tag/v0.1.0-alpha.2).
The desktop installer 0.1.0 still installs the first preview; use **Advanced →
Local APK** to select this newer APK for an update. The source build uses alpha.2.

### A few pictures

| Command wheel | Bottom screen |
|---|---|
| ![Command wheel with original building icons](docs/images/thor-command-wheel.png) | ![GLA worker, commands and production on the bottom screen](docs/images/thor-deck.png) |

## Getting started

You need an AYN Thor, your own PC installation of Zero Hour, a USB data cable,
and a Windows, Mac or Linux computer for the first transfer. The **Steam edition**
is the one tested so far.

1. Download the [desktop installer](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/releases/tag/installer-v0.1.0)
   for your computer. Windows gets a single EXE; Mac gets an app; Linux gets a
   Debian package or a portable folder. You don't need to download the source.
2. Open it, browse to your game folder, and connect the Thor with USB debugging
   enabled. It can get the USB tools from Google if needed.
3. Press **Install on Thor**. It downloads the app, copies your own files and
   checks them. Interrupted transfers resume. [The guide covers each computer](docs/HOWTO/THOR_INSTALL.md).
4. Open **Thor RTS** on the handheld, press **Play**, and try a skirmish.

The Windows installer has been checked with the actual Thor. The Linux UI has
been checked under Ubuntu/WSL; physical Linux USB access and the Mac packages
still need testing. The desktop apps are unsigned community builds.

Installing an update over the same app keeps its data and saves. Uninstalling
removes them. The transfer reads your PC installation without changing it.

## What has been tested

This is a **development preview**, tested on a real Thor running Android 13.

- Both screens and full top-screen output, including pause/menu screens.
- USA, China and GLA artwork and command layouts; controller camera movement,
  rotation, zoom, quick selection, command/group wheels and pages.
- Skirmish startup and AI play; legal/illegal building placement and production.
- Queue cancellation and its original refund: a $1,000 dozer returned the $1,000.
- Radar availability, China's radar upgrade, fog and the camera outline.
- General's Powers names/points/back navigation and a learned power purchase.
- Local saves kept through APK updates and loaded again.
- Alpha.2 device checks: minimap taps/drags match their world coordinates,
  quick touch rectangle selection, second-finger cancellation, two-finger pan,
  pinch/twist, a fixed building site during camera changes, B cancellation without
  spending, and final-A construction through the original validator.
- Native/APK builds and small host tests for controller input, deck focus and artwork.

The observed game rate is around 30 fps. That is a device check, not a long-run
performance promise. The revised wheel and deck have also been tried by hand.

Campaigns and Challenge, every unit ability, all group combinations, suspend and
reconnect, the single-screen fallback, and long sessions still need proper testing.
Some disabled commands give a general unavailable message rather than the exact
missing prerequisite. Multiplayer, mods and importing PC saves are outside this
preview's scope. Other handhelds have not been checked.

Found something wrong? [Open an issue](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/issues).
Say what you selected, what you pressed, and what happened. Screenshots of both
screens help. [Contributing](CONTRIBUTING.md) covers code and testing.

## Source

The source for the engine, Android app and Thor interface is here. The build
helper makes an APK locally; it does not need GitHub Actions or your game files.
[Build instructions and dependency versions](docs/BUILD/THOR.md).

| Path | What's there |
|---|---|
| `GeneralsMD/` | Zero Hour engine and the native Thor input/artwork bridge |
| `Core/` | Shared engine and rendering code, including the radar export |
| `android/` | The app, controller input, wheels and command deck |
| `scripts/thor/` | PC installation helper |
| `installer/` | Windows, macOS and Linux desktop installer |
| `scripts/build/android/build-thor.sh` | Local Thor APK build |
| `scripts/qa/test-thor.sh` | Controller, layout and artwork checks |
| `docs/` | Player/build guides, preview notes and inherited engine documentation |

Some inherited docs describe the upstream ports rather than this build. This
README and the Thor guides describe what this fork does.

## Credits

This stands on a lot of other people's work: [EA's source release](https://github.com/electronicarts/CnC_Generals_Zero_Hour),
[TheSuperHackers](https://github.com/TheSuperHackers/GeneralsGameCode),
[Fighter19](https://github.com/Fighter19/CnC_Generals_Zero_Hour),
[Ammaar Reshi's Mac/iPad port](https://github.com/ammaarreshi/Generals-Mac-iOS-iPad),
[fbraz3's GeneralsX](https://github.com/fbraz3/GeneralsX), and
[MYSOREZ's Android port](https://github.com/MYSOREZ/GeneralsZH-Android-Port).

The Thor changes were developed for ROM-HackZ with Codex assistance and hands-on
device testing. [Lineage, the exact starting revision and dependency credits](docs/ETC/THOR_CREDITS.md).

## License

Thor RTS is an independent, modified fan port. EA and AYN have no involvement.
The engine and this derivative use the [upstream GPL v3 license and EA's additional
terms](LICENSE.md). Copyright notices stay with the source. The game names and
retail art belong to their owners; each player supplies their own game data.
