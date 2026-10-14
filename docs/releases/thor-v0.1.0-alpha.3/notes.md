# Thor RTS — cancel construction fix

Yep, the original game has a **Cancel Construction** button. Our command export
missed it because unfinished buildings use a separate button outside the usual
command slots. It's now available in the **RT wheel** and **bottom-screen grid**.

Select the unfinished building itself, then choose **Cancel Construction** to
remove it and get the original refund. B still discards an unconfirmed building
preview. This update includes all the placement, touch and minimap fixes from
alpha.2 and keeps the retail 30 Hz simulation.

## Install / update

Download `Thor-RTS-0.1.0-alpha.3.apk`. Desktop installer 0.1.0 still automatically
downloads alpha.1: use **Advanced → Local APK** to select this APK and tick
**App update only** if your game data is already installed. Save before updating.
The same signing key retains app data and local saves during an in-place update.
No game data or test APK is included. `SHA256SUMS.txt` covers the APK.

## Checked on the Thor

Native/Java builds and five host checks pass. On-device checks selected a new
unfinished reactor, verified Cancel Construction in both command surfaces,
tapped it on the bottom screen and received the full $800 refund. The placement,
touch selection, camera gestures and minimap checks also pass. The owner's saved
match was kept separately and restored after testing.

This remains a development preview. Other factions' construction cancellation,
rebuilding GLA holes and long sessions still need hands-on checks.
