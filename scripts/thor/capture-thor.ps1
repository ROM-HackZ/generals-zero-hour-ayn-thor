param([string]$Adb = 'adb', [string]$Serial = '', [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$Name = 'capture')
$ErrorActionPreference = 'Stop'
# GeneralsX @build Codex 14/10/2026 Portable Thor device helper; select an ADB device with -Serial.
$adbPrefix = if ($Serial) { @('-s', $Serial) } else { @() }
$destination = Join-Path $PSScriptRoot '..\..\artifacts'
$outputRoot = [IO.Path]::GetFullPath($destination)
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$displays = & $Adb @adbPrefix shell dumpsys SurfaceFlinger --display-id
$ids = @([regex]::Matches(($displays -join "`n"), 'Display (\d+)') | ForEach-Object { $_.Groups[1].Value })
if ($ids.Count -eq 0) { throw 'Could not discover physical display IDs' }
for ($i = 0; $i -lt $ids.Count; $i++) {
    $remote = "/sdcard/Download/generalsthor-$Name-$i.png"
    & $Adb @adbPrefix shell screencap -d $ids[$i] -p $remote
    if ($LASTEXITCODE -ne 0) { throw 'Screen capture failed' }
    & $Adb @adbPrefix pull $remote (Join-Path $outputRoot "$Name-screen-$i.png")
    if ($LASTEXITCODE -ne 0) { throw 'Screenshot transfer failed' }
}
$pidOutput = & $Adb @adbPrefix shell pidof com.generalsthor.zerohour
if ($pidOutput) {
    $gamePid = "$pidOutput".Trim()
    & $Adb @adbPrefix logcat -d --pid=$gamePid -t 1500 | Set-Content -LiteralPath (Join-Path $outputRoot "$Name-logcat.txt") -Encoding UTF8
}
