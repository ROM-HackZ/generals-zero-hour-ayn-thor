# Thor device helpers

These PowerShell scripts work with ADB and the preview app's diagnostic snapshot.
They use `adb` on PATH by default. Pass `-Adb` for another location and `-Serial`
when more than one Android device is connected. Run them from the repository root.

| Script | What it does |
|---|---|
| `scripts/thor/capture-thor.ps1` | Captures each physical display and the app's recent logcat. Writes to the ignored `artifacts/` folder. `-Name` chooses a filename prefix. |
| `scripts/thor/save-current-match.ps1` | Opens the original save menus and verifies that one new save was created. Existing saves remain in the list. |
| `scripts/thor/resume-latest-save.ps1` | Restarts the app and loads the newest save through the original menus. `-PauseAfterLoad` pauses as soon as the match loads. |
| `scripts/thor/install-thor.ps1` | Installs an APK and copies owned PC game data. See the [install guide](../HOWTO/THOR_INSTALL.md). |

Save your current match before using the load script or running instrumentation.
Loading replaces the running match. The helpers need a working connection and
the expected game/menu state; they fail if the original controls do not appear.

```powershell
# Capture both screens without restarting the game.
.\scripts\thor\capture-thor.ps1 -Adb 'C:\platform-tools\adb.exe' -Name report

# Save the active match to a separate slot before testing or updating.
.\scripts\thor\save-current-match.ps1 -Adb 'C:\platform-tools\adb.exe'

# Restore the newest save and leave it paused.
.\scripts\thor\resume-latest-save.ps1 -Adb 'C:\platform-tools\adb.exe' -PauseAfterLoad
```

The public scripts contain no maintainer-specific device serial or tool path.
Keep captured logs, screenshots, APKs, game data, saves and signing keys out of git.
