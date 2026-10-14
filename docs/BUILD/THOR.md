# Building Thor RTS

The tested host is Ubuntu 22.04 under WSL2, with Java 17, NDK 27.2.12479018,
Android SDK 35, Gradle 8.9, CMake 3.31.6, Meson 1.7.2 and Ninja 1.11.1.4.
This build uses the original 30 Hz simulation. CI is not needed.

## Tools

Install Git, a C/C++ compiler, Java 17, ccache, pkg-config, Python/pip, curl,
unzip and the normal build tools. Install the Android command-line tools under
`$HOME/Android/Sdk` (or set `ANDROID_HOME` to your SDK).

```bash
sudo apt install build-essential git openjdk-17-jdk ccache pkg-config python3-pip curl unzip autoconf automake libtool
python3 -m pip install --user cmake==3.31.6 meson==1.7.2 ninja==1.11.1.4
export PATH="$HOME/.local/bin:$PATH"
export ANDROID_HOME="$HOME/Android/Sdk"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" 'ndk;27.2.12479018' 'platforms;android-35' 'build-tools;35.0.0'
```

Install Gradle 8.9 from [Gradle's distributions](https://services.gradle.org/distributions/),
check its published SHA-256, and put its `bin` folder on `PATH`.

The vcpkg revision used for this preview is pinned:

```bash
git clone https://github.com/microsoft/vcpkg.git "$HOME/vcpkg-thor"
git -C "$HOME/vcpkg-thor" checkout 42e4e33e1505c9f47b58c21e0f557c1571b751ee
"$HOME/vcpkg-thor/bootstrap-vcpkg.sh" -disableMetrics
export VCPKG_ROOT="$HOME/vcpkg-thor"
```

## Source and build

```bash
git clone https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor.git
cd generals-zero-hour-ayn-thor
git submodule update --init --recursive references/fbraz3-dxvk
bash scripts/qa/test-thor.sh
bash scripts/build/android/build-thor.sh
```

The first build downloads and compiles dependencies; it takes much longer than
later builds. CMake and the packaging helper fetch the other dependencies they
need. The DXVK submodule is pinned in git, including its nested submodules.

The APK is `artifacts/Thor-RTS.apk`. Logs are in `logs/`; the unstripped engine
used for debugging is `artifacts/symbols/libmain.so`. Set `THOR_BUILD_JOBS` to
change the build's parallelism, and `THOR_RECONFIGURE=1` to rerun configuration.

If you already have a configured checkout, do not move it with its `build/` folder:
CMake caches the source path. A new checkout needs its own configuration.

## Signing and installing

The helper creates `$HOME/.android/thor-rts-debug.keystore` for local builds.
Keep that key if you want later local builds to install as updates. Set
`THOR_SIGNING_KEY` to another keystore to use it instead; the current helper expects
the `androiddebugkey` alias and `android` store/key passwords. The published preview
uses the maintainer's private key, which is not in the repository.

Your own build normally has a different signature from the download. Back up
saves before switching between them. The APK is a development build, not a store
release. Install it with [the PC script](../HOWTO/THOR_INSTALL.md).

The engine's inherited platform/build notes remain in [ANDROID_PORT.md](../port/ANDROID_PORT.md).
Some describe upstream features and releases; the Thor README describes this fork.
