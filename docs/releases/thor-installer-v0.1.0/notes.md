# Thor RTS desktop installer — first preview

Pick your game folder, connect the Thor, and press **Install on Thor**. The new
desktop app handles the APK and the transfer, so you don't need to download the
source or type an ADB command.

## Downloads

| Computer | File |
|---|---|
| Windows 10/11 x64 | `Thor-RTS-Installer-0.1.0-Windows-x64.exe` — single portable app |
| Apple Silicon Mac | `Thor-RTS-Installer-0.1.0-macOS-arm64.zip` |
| Intel Mac | `Thor-RTS-Installer-0.1.0-macOS-x64.zip` |
| Ubuntu/Debian x64 | `Thor-RTS-Installer-0.1.0-Linux-x64.deb` |
| Other Linux x64 desktops | `Thor-RTS-Installer-0.1.0-Linux-x64.tar.gz` |

**[Install guide](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/blob/main/docs/HOWTO/THOR_INSTALL.md)**
· [Controls](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/blob/main/docs/HOWTO/THOR_CONTROLS.md)
· [Installer source/build instructions](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/blob/installer-v0.1.0/docs/BUILD/THOR_INSTALLER.md)

## What it handles

- Browse for your own Steam PC installation. Common Steam libraries are detected.
- Pick a connected device and get help with the USB debugging prompt.
- Use existing ADB or download Google's USB tools after accepting its SDK terms.
- Download game preview **0.1.0-alpha.1** and check its published SHA-256.
- Check free space, copy your archives and loose files, and verify each transfer.
- Cancel and resume. Files already correct on the Thor are skipped.
- Update only the app when the game data is already there. Existing saves are kept.
- Use local APK/ADB paths for offline installs and save an install log for reports.

No retail game files are included. You need your own copy. A Mac needs a copy of
the complete Windows Steam game folder; this is a tool for installing the Android
port, not a Mac game build. The game APK and controller interface are the same
first preview as before.

## What has been checked

Windows was checked with the actual Thor: automatic folder detection, USB
discovery, all 279 game files, matching-APK detection, cancellation, automatic
download/checksum and app-update-only. A real temporary USB transfer also passed
verification and skipped the file when repeated. Nine backend tests pass on
Windows and Linux. The Linux UI opens with its sandbox enabled under Ubuntu/WSL.

The Mac packages are built and ad-hoc signed, but **not tested on a real Mac**.
Physical Linux USB access and other distributions still need testing. These are
community builds without a trusted Windows/Apple publisher certificate; Macs
may require **Privacy & Security → Open Anyway**. The guide covers that and Linux
USB permissions. Please report the exact message if a package fails to open.

Source is tagged `installer-v0.1.0`. `SHA256SUMS.txt` covers the desktop downloads.
No game data, credentials, signing keys or CI workflows are included.
