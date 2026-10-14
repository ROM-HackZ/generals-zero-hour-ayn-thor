# Thor RTS — input fixes preview

Building placement now has two clear steps: **A chooses the spot, left stick
chooses facing, A builds**. **B cancels** before confirmation. The preview stays
at its chosen world position while you rotate or zoom the camera. Touch players
can use **Choose spot**, **Build** and **Cancel** on the bottom screen.

Touch drags on the battlefield now draw a selection rectangle straight away,
with selection committed on release. Two-finger drag/pinch/twist controls the
camera. Adding a second finger cancels an unfinished rectangle, keeping the
previous selection; the remaining camera finger is consumed until you lift it.

The bottom-screen minimap's image, camera outline and input now share north-up
orientation and the same aspect-fitted map bounds. **Tap to look at the spot you
touched; drag to pan across those spots.** Empty map borders ignore taps. Touch
on the minimap does not send unit orders.

## Install / update

Download `Thor-RTS-0.1.0-alpha.2.apk`. In desktop installer 0.1.0, open **Advanced →
Local APK**, select this file and tick **App update only** if the game data is
already on your Thor. Its automatic download still selects alpha.1.
Save your match before updating. This APK uses the same signing key, so an
in-place update retains your app data and local saves.

[Install guide](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/blob/main/docs/HOWTO/THOR_INSTALL.md)
· [Updated controls](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/blob/main/docs/HOWTO/THOR_CONTROLS.md)

## Checked on the Thor

Native/Java builds and five host test programs pass. Separate on-device
instrumentation checks passed quick box selection without camera movement,
second-finger cancellation and pan, pinch/twist, minimap tap/held-drag coordinates,
first-A lock without spending, analog facing, a fixed world site during camera
movement, B cancellation without spending, and final-A construction. The saved
USA skirmish was preserved and restored after the checks.

This remains a development preview. Long sessions, all building types, every
placement edge case and physical feel still need broader player testing. The
simulation remains retail 30 Hz. No game data or test APK is included.
Source is tagged `v0.1.0-alpha.2`; `SHA256SUMS.txt` covers the APK.
