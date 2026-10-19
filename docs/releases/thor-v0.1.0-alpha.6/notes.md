# Thor RTS — cinematics, dialogue and menu fixes

This update fixes the rough USA 1 opening: the missile shots, black-and-white
objective footage, quiet briefing voice and lines talking over each other.
Intro movies keep their frames while audio buffers, and A/B/Start can skip a
briefing without leaving a pause queued for the mission.

The bottom-screen menus have their own labelled layout. Options shows one Extras
button, and holding A keeps a snapped selection point in place. Idle sticks and
cinematic input locks no longer fight the scripted camera.

I also went through the campaign files. There are 37 reviewed dialogue waits
across eight missions; 29 need more time for their English audio. Those waits
now use the selected speech file's length. This includes two USA 3 cases where
speech and its timer are in separate scripts. Adjacent camera, combat and sound-
effect timers keep their authored timing. Fallback loading also gets audio
updates and a valid final movie frame.

This includes the earlier previews and keeps the original 30 Hz simulation.
No game files, saves, signing keys or test app are included.

## Install / update

Download `Thor-RTS-0.1.0-alpha.6.apk`. With desktop installer 0.1.0, choose it under
**Advanced → Local APK**. That installer still automatically downloads alpha.1.
Tick **App update only** if your game files are already installed.

Save your match before updating. The same signing key allows an in-place update
that keeps your app data and saves. Do not uninstall the existing app to update.
`SHA256SUMS.txt` contains the APK checksum.

## Checked

- USA 1's missile shots, upright black-and-white briefing, speech order/volume and
  return to play were checked on the Thor. The player confirmed the fixes.
- All 15 campaign intro movies fully decoded offline with their expected frame
  counts. All 748 speech references in those mission maps resolved.
- All 37 reviewed dialogue dependencies passed checks against the owned maps and
  audio, then passed the engine's C++ timing functions. Seven host checks and the
  local native/APK build passed.
- Menu, movie-skip and snapped-press device regressions passed during development.
- Alpha.6 installed in place on the Thor and reached its main menu. The existing
  save was checked byte-for-byte after updating.

Later missions have not been played through on the Thor for this update. Some
mixed objective, RNG and ownership branches retain their original timing; this
is not a claim that every cutscene now matches the PC game perfectly. The
[cinematic review](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/blob/v0.1.0-alpha.6/docs/WORKDIR/reports/THOR-CINEMATICS-2026-10-19.md)
has the checked scope and remaining limits.
