# Build the desktop installer

The player downloads are prebuilt. These instructions are for working on the
installer, not for installing the game.

The UI uses Electron 44.7.2. A Node backend runs ADB without a host shell;
Android shell arguments are quoted individually. The page is local, sandboxed
and isolated, and its preload exposes only the specific installer operations.
`installer/releases.js` discovers the newest published game version on GitHub,
including previews. It checks the release checksum against GitHub asset digests
when available, then the downloader verifies the actual APK. Desktop releases
and drafts are excluded. There is no pinned game version to bump.
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

The sixteen backend tests cover folder/header validation, remote quoting, device
states, checksum failures, resumable transfers, cancellation, signature mismatch,
and keeping an identical installed app running. They use temporary fake game
data, a fake Android device, and a local download server. ZIP path traversal is
also checked before extraction. Release discovery covers numeric version order,
previews, pagination, missing/conflicting checksums and network/cancellation
failures. An automatic update of an identical installed app leaves it running.
No retail data is needed.

The packaged UI also has a repeatable smoke check. From `installer/`, run:

```sh
node test/ui-smoke.js /path/to/thor-rts-installer /path/to/test-output
```

It opens the actual app, checks the automatic default and captures its window.
To check a real USB app update too, append your ADB path, device serial and the
expected published game version. Save first; that mode performs an actual update
and expects the same APK to be installed already.

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

Publish the game under its `v<version>` tag with `Thor-RTS-<version>.apk` and
`SHA256SUMS.txt`. Upload both assets before making the release public. Keep the
APK signing key consistent for in-place updates. The existing desktop installer
will select the highest published game version on its next install/update; no
desktop rebuild, version pin or README game-download URL needs changing.

Check discovery from `installer/` after publication:

```sh
node -e "require('./releases').latestRelease().then(console.log)"
```

If the newest release is incomplete or inconsistent, installation stops rather
than choosing an older build. Offline installs use an explicit local APK.
Local APK overrides are not saved between app launches.

When the desktop installer itself changes, bump `installer/package.json` and
its lockfile, rebuild the platform packages and publish them with matching
source/tag and `SHA256SUMS.txt`. Installer 0.1.0 predates release discovery and
must be replaced once; installer 0.1.1 and later do not need rebuilding for new
game previews.

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

Installer 0.1.1's packaged Windows UI passed automatic release discovery,
download/checksum and a real USB app-only check against the identical alpha.7
installation. Its old remembered local-APK setting was ignored as intended.
The packaged Linux UI also opened with its sandbox enabled and passed the
automatic-default check. Each platform's embedded application source and desktop
version were checked against this repository.
