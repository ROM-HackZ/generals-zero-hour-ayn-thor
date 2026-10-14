#!/usr/bin/env bash
# GeneralsX @build Codex 14/10/2026 Portable local build for the Thor fork (retail 30 Hz).
set -euo pipefail
REPO=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
export ANDROID_SDK_ROOT=$ANDROID_HOME
export ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/27.2.12479018}
: "${VCPKG_ROOT:?Set VCPKG_ROOT to the pinned vcpkg checkout; see docs/BUILD/THOR.md}"
export PATH="$HOME/.local/bin:$PATH"
export GX_ANDROID_STAGING="$REPO/android-staging"
export THOR_SIGNING_KEY=${THOR_SIGNING_KEY:-$HOME/.android/thor-rts-debug.keystore}
if [[ ! -f "$THOR_SIGNING_KEY" ]]; then
    mkdir -p "$(dirname "$THOR_SIGNING_KEY")"
    keytool -genkeypair -keystore "$THOR_SIGNING_KEY" -storepass android -keypass android \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Thor RTS Local Build'
fi
cd "$REPO"
mkdir -p logs artifacts/symbols
configure=()
if command -v ccache >/dev/null; then configure+=(-DCMAKE_C_COMPILER_LAUNCHER=ccache -DCMAKE_CXX_COMPILER_LAUNCHER=ccache); fi
if [[ ! -f build/android-vulkan/CMakeCache.txt || "${THOR_RECONFIGURE:-0}" == 1 ]]; then
    cmake --preset android-vulkan -DSAGE_HIGH_FPS_SIM=OFF "${configure[@]}" > logs/configure-thor.log 2>&1
fi
cmake --build build/android-vulkan --target z_generals dxvk_d3d8_install main_hook file_redirect_hook gsl_alloc_hook hook_impl \
    --parallel "${THOR_BUILD_JOBS:-8}" > logs/build-thor.log 2>&1
cp build/android-vulkan/GeneralsMD/Code/Main/libmain.so artifacts/symbols/libmain.so
"$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded build/android-vulkan/GeneralsMD/Code/Main/libmain.so
bash scripts/build/android/package-android-zh.sh > logs/package-thor.log 2>&1
cp android/app/build/outputs/apk/debug/app-debug.apk artifacts/Thor-RTS.apk
echo "APK: $REPO/artifacts/Thor-RTS.apk"
