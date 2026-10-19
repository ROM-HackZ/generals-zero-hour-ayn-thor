# Thor RTS — intro controls and stuck menus

B could bring up a frozen mission menu during the USA opening. It now leaves
videos and scripted scenes alone. The intro controls are:

| Where you are | Controls |
|---|---|
| Video playing | A skips. B and Start leave it alone. |
| Loading | Wait for loading to finish. A cannot skip the load. |
| Scripted campaign scene | Start pauses/resumes the scene, including its sound. Unit and camera controls stay locked. |
| Normal play | Start opens/closes the usual mission menu. |

A skip belongs to the video you pressed it for. It cannot turn into a pause menu
after that video ends. The bottom screen shows the controls for the current scene.

Also fixes short animated menu labels never reporting that they finished. This
could leave the campaign difficulty screen visible but its bottom-screen buttons
missing. USA, GLA and China difficulty choices now finish opening properly.

Includes the earlier cinematic, dialogue and D-pad fixes. The original 30 Hz
simulation stays unchanged. No retail files, saves, keys or test app are included.

## Install / update

Use [desktop installer 0.1.1](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/releases/tag/installer-v0.1.1).
It automatically finds this release and checks its checksum. For an existing
installation, tick **App update only**. No installer rebuild is needed.
If you still have installer 0.1.0, replace it with 0.1.1 once.

For offline use, download `Thor-RTS-0.1.0-alpha.8.apk` and choose it under
**Advanced → Local APK**. Save before updating and install over the existing app;
do not uninstall it. `SHA256SUMS.txt` contains the APK checksum.

## Checked

- USA mission 1 on the Thor: B/Start during the briefing, A skip, stale skip/menu
  requests, scene pause/resume, sound pause lifecycle, camera stillness while
  paused, selection lock and normal control/menu return after the opening.
- All three campaign difficulty screens, B back, rapid/repeated D-pad input,
  duplicate hat/key reports, Skirmish editing and isolated Options.
- Eight host checks, including 1,000 concurrent video-end/skip-request races.
- Local native/APK build at retail 30 Hz with the existing update certificate.
- Installer 0.1.1 automatically found the public alpha.8 release, downloaded and
  verified its checksum, and updated the Thor over USB. Its packaged Windows UI
  also found alpha.8 and verified the already-installed APK without replacing it.
- Confirmed version 10408 / alpha.8 and the published APK hash on the Thor,
  reached the main menu, checked the save byte-for-byte and closed the game.

Later missions were not opened. Full campaign/Challenge playthroughs and longer
sessions still need testing; the low-memory voice-only briefing path was reviewed
from source. The inherited Generals text-animation fix is not a tested Generals build.
