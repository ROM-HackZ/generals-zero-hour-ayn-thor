# Installing Thor RTS

You need an AYN Thor and your own Windows installation of Command & Conquer:
Generals Zero Hour. The Steam edition is the one this port has been tested with.
The APK contains the engine. Your installation supplies the maps, units, art,
sound and movies.

## From your PC

1. Download `Thor-RTS-0.1.0-alpha.1.apk` from
   [Releases](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/releases/tag/v0.1.0-alpha.1).
   Download this repository too (Code -> Download ZIP), and unzip it.
2. Get Google's [Android platform tools](https://developer.android.com/tools/releases/platform-tools).
   On the Thor, enable Developer options and USB debugging. Connect it to your PC
   and accept the debugging prompt on the Thor. `adb devices` should list it as
   `device`, rather than `unauthorized`.
3. Open PowerShell in the unzipped repository and run:

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\thor\install-thor.ps1 `
     -SteamRoot "C:\Program Files (x86)\Steam\steamapps\common\Command & Conquer Generals - Zero Hour" `
     -Apk "C:\Downloads\Thor-RTS-0.1.0-alpha.1.apk" `
     -Adb "C:\platform-tools\adb.exe"
   ```

   Change those three paths to match your PC. If more than one Android device is
   connected, add `-Serial` followed by your Thor's serial from `adb devices`.
4. Open **Thor RTS**, then **Play**. Use the D-pad and A in menus. A skirmish is a
   good place to start; [the controls](THOR_CONTROLS.md) explain the wheels and deck.

The first transfer takes a while. The script checks each archive's header and
SHA-256, skips matching files already on the Thor, and copies the loose `Data`
folders too. It reads your PC installation without changing it. The tested Steam
installation has 36 BIG archives, about 2.25 GB, plus its loose data.

The right game folder contains `INIZH.big`, `TexturesZH.big`, `MapsZH.big`, and a
`ZH_Generals` folder with `INI.big` inside. Copy the whole set, not just the
expansion's archives.

## Updates and saves

Install newer releases over the existing app. With the script, add `-SkipAssets`
if your game data is already there. Android accepts updates signed with the same
key; the published Thor builds use their own key.

The app keeps its files here:

```text
/sdcard/Android/data/com.generalsthor.zerohour/files/
  GameData/                         your PC game data
  UserData/Command and Conquer Generals Zero Hour Data/Save/
```

Updating in place keeps these files. Uninstalling removes them, so back up your
saves before uninstalling or switching to a build you signed yourself.

## If it won't start

- **ADB says unauthorized:** accept the USB debugging prompt on the Thor.
- **The launcher says files are missing:** check the PC folder you passed to the
  script, particularly `ZH_Generals\INI.big`, and run the transfer again.
- **The lower screen is blank:** report it with your Android version and whether
  both screens were enabled when the app started. This build was tested on Android 13.
- **An update reports a signature mismatch:** it was signed with a different key.
  Back up your saves before changing installations; don't uninstall just to see
  whether it helps.

Bug reports: [Issues](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor/issues).
Say what you did, what happened, and whether you were using buttons or touch.
