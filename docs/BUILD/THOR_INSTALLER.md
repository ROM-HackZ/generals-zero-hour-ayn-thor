# Build the desktop installer

The player downloads are prebuilt. These instructions are for working on the
installer, not for installing the game.

The UI uses Electron 44.7.2. A Node backend runs ADB without a host shell;
Android shell arguments are quoted individually. The page is local, sandboxed
and isolated, and its preload exposes only the specific installer operations.
APK downloads use the fixed release URL and SHA-256 in `installer/release.json`.
No game data or Google SDK tools are bundled in the desktop releases.

## Develop

Use Node **24.13.0** or a compatible supported Node version (the packaging tools
require at least 22.12). From the repository root:

```sh
cd installer
npm ci
npm test
npm start
```

The nine backend tests cover folder/header validation, remote quoting, device
states, checksum failures, resumable transfers, cancellation, signature mismatch,
and keeping an identical installed app running. They use temporary fake game
data, a fake Android device, and a local download server. ZIP path traversal is
also checked before extraction. No retail data is needed.

## Package locally

Run these from `installer/`. Outputs go to `artifacts/installer/`, outside git.
Windows packaging runs on Windows; Debian packaging runs on Linux. No CI is used.

```sh
node package-app.js win32 x64 portable
node package-app.js linux x64 deb
node package-app.js darwin arm64
node package-app.js darwin x64
```

The helper copies the repository's full license into the app and packages only
production dependencies. It creates a single portable Windows EXE, a Debian
package, and ordinary portable folders. Zip each Mac `.app` with symlinks and
executable modes preserved (`zip -qry ...zip 'Thor RTS Installer.app'` from its
parent). A Linux folder can be distributed with `tar -czf`.

Mac apps are ad-hoc signed so their modified bundles have consistent signatures.
They are **not Developer ID signed or notarized**. On a Mac, the helper uses
`codesign`; on another OS, set `THOR_RCODESIGN` to the path of
[rcodesign 0.29.0](https://github.com/indygreg/apple-platform-rs/releases/tag/apple-codesign%2F0.29.0).
Verify its download against the published SHA-256 first. The release packages
were built on Linux with that tool. Cross-packaging does not verify Mac launch
behavior; test on physical Intel/Apple Silicon Macs before claiming it supported.

The icon is an original drawing. Generated PNG/ICO/ICNS files are checked in;
`python make-icon.py` regenerates them with Pillow. Python is a build convenience,
not a player dependency.

## New game previews

Update `release.json` with the published Thor APK name, URL, version and SHA-256,
then rebuild the desktop apps. Keep the APK signing key consistent for updates.
The installer intentionally does not pick arbitrary newer prereleases via an API.
Publish matching source/tag, platform files and `SHA256SUMS.txt` together.

## Device acceptance checks

On Windows, the packaged app was exercised with the actual AYN Thor: Steam-folder
detection, USB discovery, verification of 35 archives and 244 loose files, matching
APK detection, cancellation, automatic APK download/checksum and update-only.
A separate real USB test copied and verified a temporary file whose name contained
spaces and an apostrophe, then skipped it on the second run and removed the test
file. Existing game files and saves were retained.

The Linux UI was launched with its sandbox enabled in Ubuntu 22.04/WSLg after
installing `libgbm1`; the backend tests pass on Linux too. USB access from physical
Linux, fresh installs on clean desktop machines, and Mac runtime behavior remain
acceptance work. Windows/Mac bundles have no trusted publisher certificate.
