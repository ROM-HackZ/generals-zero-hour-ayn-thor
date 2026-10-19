# Thor RTS installer — automatic game updates

The installer now gets the newest published game release automatically. No more
separate APK download or changing a version pin every time I release a fix.

Choose your game folder, connect the Thor, and press **Install on Thor**. If your
game files are already there, tick **App update only**. It checks the new APK's
checksum and keeps your saves. If you already have that exact APK, it leaves the
game running.

Replace installer 0.1.0 with this one once. After that, new game releases appear
on your next install/update without needing another desktop download. Local APKs
still work under Advanced for offline installs and custom builds; that override
lasts for the current session.

## Downloads

| Computer | File |
|---|---|
| Windows 10/11 x64 | `Thor-RTS-Installer-0.1.1-Windows-x64.exe` |
| Apple Silicon Mac | `Thor-RTS-Installer-0.1.1-macOS-arm64.zip` |
| Intel Mac | `Thor-RTS-Installer-0.1.1-macOS-x64.zip` |
| Ubuntu/Debian x64 | `Thor-RTS-Installer-0.1.1-Linux-x64.deb` |
| Other Linux x64 desktops | `Thor-RTS-Installer-0.1.1-Linux-x64.tar.gz` |

[Install guide](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/blob/main/docs/HOWTO/THOR_INSTALL.md)
· [Controls](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/blob/main/docs/HOWTO/THOR_CONTROLS.md)

Published game previews are included; drafts and desktop-installer releases are
ignored. Missing or inconsistent checksums stop installation. Connection errors
do not silently select an old preview. No game data or USB tools are bundled.

Sixteen backend tests pass on Windows and Linux. A real USB update check found
alpha.7, downloaded and verified it, and kept the identical Thor installation.
The packaged Windows UI passed that same flow, and the packaged Linux UI opened
with its sandbox enabled. The application code inside all five downloads matches
the tested source.
Mac packages are ad-hoc signed, without an Apple Developer ID or notarization;
they still need testing on real Macs. Physical Linux USB access also needs
checking. These remain unsigned community desktop apps.

Source is tagged `installer-v0.1.1`. `SHA256SUMS.txt` covers the desktop downloads.
