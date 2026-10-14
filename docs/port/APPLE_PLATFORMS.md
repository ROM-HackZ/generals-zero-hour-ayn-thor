# macOS, iPhone and iPad

> **Not maintained in this fork.** This repository is an Android port. The macOS and iOS/iPadOS
> builds come from the project it was forked from and are kept here as they were; they are not
> developed or tested any more, and GeneralsOnline multiplayer was never brought to them.

The same codebase built for Apple Silicon Macs, iPhone and iPad through DirectX 8 → DXVK →
[MoltenVK](https://github.com/KhronosGroup/MoltenVK) → Metal. The engineering log of that work is
[PORTING_PLAYBOOK.md](PORTING_PLAYBOOK.md) (start with
[§8, the bug archaeology](PORTING_PLAYBOOK.md#8-post-ship-bug-hunts-junejuly-2026--the-archaeology-section)),
and the general method is [PORTING_PATTERNS.md](PORTING_PATTERNS.md).

## macOS

Prerequisites (one time):

```sh
# Toolchain
xcode-select --install
brew install cmake ninja meson pkgconf
brew install --cask steamcmd

# vcpkg (full clone — a shallow clone breaks manifest baselines)
git clone https://github.com/microsoft/vcpkg ~/vcpkg && ~/vcpkg/bootstrap-vcpkg.sh
export VCPKG_ROOT=~/vcpkg          # add to your shell profile

# LunarG Vulkan SDK (NOT the Homebrew cask) — https://vulkan.lunarg.com/sdk/home
export VULKAN_SDK=$HOME/VulkanSDK/<version>/macOS   # add to your shell profile
```

Clone, build, get assets, play:

```sh
git clone https://github.com/ammaarreshi/Generals-Mac-iOS-iPad.git GeneralsX
cd GeneralsX
./scripts/build/macos/build-macos-zh.sh     # checks deps, configures, builds
./scripts/build/macos/deploy-macos-zh.sh    # creates ~/GeneralsX/GeneralsZH + run.sh
./scripts/get-assets.sh <your_steam_username>   # fetches game data you own
cd ~/GeneralsX/GeneralsZH && ./run.sh -win
```

## iPhone / iPad

On top of the macOS prerequisites: full Xcode (signed into your Apple ID),
`brew install xcodegen`, and a (free or paid) Apple Developer team.

```sh
cd GeneralsX
git submodule update --init references/fbraz3-dxvk   # iOS DXVK is built from this + Patches/dxvk-ios.patch
./scripts/build/ios/fetch-moltenvk.sh                # pinned MoltenVK.framework (checksummed)
./scripts/build/ios/stage-fonts.sh                   # Liberation fonts, renamed as the game expects
cmake --preset ios-vulkan
cmake --build build/ios-vulkan --target z_generals
GX_TEAM_ID=<your-team-id> GX_BUNDLE_ID=com.you.generalszh \
    ./scripts/build/ios/package-ios-zh.sh --install  # assembles, signs, installs
```

Find your team id in Xcode → Settings → Accounts. Assets ship inside the app
bundle (self-contained install); `--dev` skips the ~2.7 GB copy for fast code
iteration.

## Known issues (as last recorded)

- Long sessions on iPad can be killed by iOS for memory (~3 GB+ resident); the app
  exits to the home screen with no dialog. Session logs (current + previous) are in
  the Files app under the game's folder.
- Backgrounding mid-game can occasionally crash on iOS — the lifecycle pause covers
  the common paths; a rare race remains. Save often.
