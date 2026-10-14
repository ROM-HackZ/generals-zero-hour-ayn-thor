param([string]$Adb='adb',[string]$Serial='',[switch]$PauseAfterLoad)
$ErrorActionPreference='Stop'
# GeneralsX @build Codex 14/10/2026 Portable Thor device helper; select an ADB device with -Serial.
$adbPrefix = if ($Serial) { @('-s', $Serial) } else { @() }
function Read-State {
    $raw=& $Adb @adbPrefix shell 'cat /sdcard/Android/data/com.generalsthor.zerohour/files/thor-state.json 2>/dev/null'
    if($LASTEXITCODE -eq 0) { try { return ($raw | ConvertFrom-Json) } catch { Write-Verbose 'Snapshot not ready' } }
    return $null
}
function Click-Label([string]$Label) {
    for($attempt=0;$attempt -lt 30;$attempt++) {
        $state=Read-State
        $targets=@($state.widgets | Where-Object { $_.label -eq $Label -and $_.enabled })
        if($targets.Count -gt 0) {
            $target=$targets[0]
            & $Adb @adbPrefix shell input -d 0 tap ([int]($target.x+$target.w/2)) ([int]($target.y+$target.h/2))
            if($LASTEXITCODE -ne 0) { throw 'Load menu input failed' }
            Start-Sleep -Seconds 2
            return
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Load menu did not expose $Label"
}
# Loading replaces the current match. Save it first if you want to keep it.
& $Adb @adbPrefix shell am force-stop com.generalsthor.zerohour
if($LASTEXITCODE -ne 0) { throw 'Could not stop the previous app process' }
& $Adb @adbPrefix shell rm -f /sdcard/Android/data/com.generalsthor.zerohour/files/thor-state.json
& $Adb @adbPrefix shell am start -n com.generalsthor.zerohour/com.generalsx.zerohour.GeneralsZHActivity
for($attempt=0;$attempt -lt 25;$attempt++) {
    $state=Read-State
    if(@($state.widgets | Where-Object label -eq 'SOLO PLAY').Count -gt 0) { break }
    & $Adb @adbPrefix shell input -d 0 keyevent 97
    Start-Sleep -Milliseconds 700
}
Start-Sleep -Seconds 2
Click-Label 'LOAD'
Click-Label 'LOAD GAME'
for($attempt=0;$attempt -lt 30;$attempt++) {
    $state=Read-State
    if(@($state.widgets | Where-Object { ($_.style -band 32) -ne 0 }).Count -gt 0) { break }
    Start-Sleep -Milliseconds 500
}
# The original game lists saves newest first and selects the first entry.
Click-Label 'LOAD GAME'
for($attempt=0;$attempt -lt 30;$attempt++) {
    $state=Read-State
    if($state.battle) {
        if($PauseAfterLoad) {
            & $Adb @adbPrefix shell input -d 0 keyevent 108
            if($LASTEXITCODE -ne 0) { throw 'Could not pause the loaded match' }
        }
        Write-Output "Latest save loaded: money=$($state.money), camera=$($state.cameraX),$($state.cameraY)"
        return
    }
    Start-Sleep -Milliseconds 500
}
throw 'Saved match did not load'
