param([string]$Adb='adb',[string]$Serial='')
$ErrorActionPreference='Stop'
# GeneralsX @build Codex 14/10/2026 Portable Thor device helper; select an ADB device with -Serial.
$adbPrefix = if ($Serial) { @('-s', $Serial) } else { @() }
function Read-State { (& $Adb @adbPrefix shell cat /sdcard/Android/data/com.generalsthor.zerohour/files/thor-state.json) | ConvertFrom-Json }
function Click-Label([string]$Label) {
    for($attempt=0;$attempt -lt 12;$attempt++) {
        $state=Read-State
        $target=@($state.widgets | Where-Object { $_.label -eq $Label -and $_.enabled })
        if($target.Count -gt 0) {
            $widget=$target[0]
            & $Adb @adbPrefix shell input -d 0 tap ([int]($widget.x+$widget.w/2)) ([int]($widget.y+$widget.h/2))
            if($LASTEXITCODE -ne 0) { throw 'Save menu input failed' }
            Start-Sleep -Milliseconds 1500
            return
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Save menu did not expose $Label"
}
$before=@(& $Adb @adbPrefix shell 'find /sdcard/Android/data/com.generalsthor.zerohour/files/UserData -type f -name *.sav')
$state=Read-State
if($state.battle) { & $Adb @adbPrefix shell input -d 0 keyevent 108; Start-Sleep -Milliseconds 1500 }
if(@((Read-State).widgets | Where-Object label -eq 'SAVE/LOAD').Count -eq 0) { throw 'No active match to save' }
Click-Label 'SAVE/LOAD'
Click-Label 'SAVE GAME'
Click-Label 'SAVE GAME'
$after=@(& $Adb @adbPrefix shell 'find /sdcard/Android/data/com.generalsthor.zerohour/files/UserData -type f -name *.sav')
$created=@($after | Where-Object { $_ -notin $before })
if($created.Count -ne 1) { throw 'Could not verify creation of a separate save; app update must wait' }
Write-Output "Saved current match: $($created[0])"
