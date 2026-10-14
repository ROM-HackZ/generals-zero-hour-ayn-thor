# Thor RTS — more of the original UI

Found a few more buttons hiding outside the usual command slots. This update
exports every visible registered game action, including timer structures'
Sell/Rally Point controls. Individual passengers now keep their own exit buttons,
names and portraits. Overcharge and other toggles show when they're active.

The bottom screen gets construction progress, reinforcement countdowns,
passenger counts, veterancy and rank progress. Radar alerts and hero reticles
were separate from the map textures, so those now come across too. The duplicate
floating group panel is removed from the top display when using the second screen.
Tapping the selected unit/building portrait centres the camera, just like L3.

**Tactical Tools** opens from the bottom-screen header or RT wheel. It brings
back Last Alert, Select Army/Aircraft/Hero/Matching, Scatter, Create Formation,
Reset Camera and Next Builder. Busy builders are included there; D-pad Up still
finds idle builders. Your existing button bindings stay the same.

Production and passenger actions check their original target before activating,
so a reused slot can't cancel or eject something else from an old UI snapshot.
Beacon/observer controls and live dialogs are exported, and menu checkboxes,
radio buttons and sliders show their current state. Includes the previous
placement, touch and minimap fixes. The simulation remains retail 30 Hz.

## Install / update

Download `Thor-RTS-0.1.0-alpha.4.apk`. Installer 0.1.0 still automatically gets
alpha.1: choose this APK under **Advanced → Local APK**, and tick **App update
only** if your game files are already installed. Save your match before updating.
The same signing key keeps app data and saves during an in-place update.
No game data or test APK is included. `SHA256SUMS.txt` covers the player APK.

## Scope

Native/Java builds and six host checks pass. On the Thor, fresh-skirmish checks
passed placement/touch/minimap controls, Tactical Tools, camera reset, production
refunds and stale queue protection. Two Rangers were built and garrisoned in a
Fire Base: both exit entries appeared, one tap released exactly one Ranger, and
a stale slot couldn't eject the remaining one. A construction radar alert reached
the deck. China's Overcharge toggled on/off with the correct Active state.
The owner's saved match was preserved and restored after testing.

[The UI audit](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/blob/main/docs/WORKDIR/reports/THOR-UI-COVERAGE-2026-10-14.md)
lists the checked source paths and device fixtures. Multiplayer beacon/observer
flows, timer structures, every ability, campaign-specific dialogs and long
sessions still need hands-on testing. This remains a development preview.
