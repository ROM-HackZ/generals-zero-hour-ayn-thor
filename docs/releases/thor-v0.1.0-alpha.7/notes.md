# Thor RTS — D-pad menu fixes

Quick D-pad taps were getting dropped, and moving a stick could make a held
D-pad direction repeat unexpectedly. This fixes both. Each released press counts
once; holding a direction repeats steadily after a short delay.

The main menu and campaign choices now run down one column on both screens.
Down chooses the next item you see. Larger setup menus keep their bottom-screen
grid, and disabled controls are skipped.

Focus waits for the game's menu animation to finish, so Solo Play starts on USA
instead of landing on a button still moving into place. A confirms or edits;
B also works with Skirmish's Main Menu button.

This includes the alpha.6 cinematic and dialogue fixes and keeps the original
30 Hz simulation. No game files, saves, signing keys or test app are included.

## Install / update

Use [desktop installer 0.1.1](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/releases/tag/installer-v0.1.1).
It automatically finds the newest game release and checks its checksum. Tick
**App update only** if your game files are already installed. Replace installer
0.1.0 once; its automatic download was pinned to alpha.1.

For offline use, download `Thor-RTS-0.1.0-alpha.7.apk` and choose it under
**Advanced → Local APK**.

Save your match before updating. Install over the existing app to keep its data
and saves; do not uninstall it first. `SHA256SUMS.txt` contains the APK checksum.

## Checked

- Reproduced dropped rapid D-pad presses on the previous build using the Thor's
  actual hat-axis input path, then verified the fix on the device.
- Checked rapid taps, held repeat, duplicate key/hat reports, all six main menu
  actions, Solo Play focus, skirmish dropdown editing and A/B navigation.
- Every enabled control in the Skirmish and Options menus was reachable across
  their pages without focusing disabled controls. Options also reopened cleanly.
- Visually checked the main and Solo Play menus on the bottom screen.
- All seven host checks and the local native/APK build passed.
- Alpha.7 installed in place on the Thor and reached its main menu. Existing
  saves were checked byte-for-byte after updating.

No match or later campaign was started for these menu tests. Full campaign and
Challenge playthroughs, longer sessions and other devices still need testing.
