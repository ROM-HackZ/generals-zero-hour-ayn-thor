param(
    [Parameter(Mandatory=$true)][string]$SteamRoot,
    [string]$Adb='adb',
    [string]$Serial,
    [string]$Apk=(Join-Path $PSScriptRoot '..\..\artifacts\Thor-RTS.apk'),
    [switch]$SkipAssets
)
$ErrorActionPreference='Stop'
$packageName='com.generalsthor.zerohour'
$adbPrefix=if($Serial) { @('-s',$Serial) } else { @() }
function Invoke-Adb([string[]]$Arguments) {
    & $Adb @adbPrefix @Arguments
    if($LASTEXITCODE -ne 0) { throw "ADB failed: $($Arguments[0])" }
}
function Quote-Shell([string]$Value) {
    $quote=[string][char]39
    $escape=$quote+[char]34+$quote+[char]34+$quote
    return $quote+$Value.Replace($quote,$escape)+$quote
}
if(!(Test-Path -LiteralPath $Apk -PathType Leaf)) { throw "APK not found: $Apk" }
$root=(Resolve-Path -LiteralPath $SteamRoot).Path
if(!(Test-Path -LiteralPath (Join-Path $root 'INIZH.big')) -or !(Test-Path -LiteralPath (Join-Path $root 'ZH_Generals\INI.big'))) {
    throw 'Choose the Zero Hour installation containing INIZH.big and ZH_Generals\INI.big.'
}
Invoke-Adb @('install','-r',(Resolve-Path -LiteralPath $Apk).Path)
# Create the app's external files directory before transferring data.
Invoke-Adb @('shell','am','start','-n',"$packageName/com.generalsx.zerohour.ThorLauncher")
if(!$SkipAssets) {
    $destination="/sdcard/Android/data/$packageName/files/GameData"
    Invoke-Adb @('shell','mkdir','-p',"$destination/ZH_Generals")
    $archives=@(Get-ChildItem -LiteralPath $root -File -Filter '*.big') + @(Get-ChildItem -LiteralPath (Join-Path $root 'ZH_Generals') -File -Filter '*.big')
    foreach($file in $archives) {
        $relative=$file.FullName.Substring($root.Length+1).Replace('\','/')
        if($file.Name -notmatch '^[A-Za-z0-9._-]+$') { throw "Unexpected archive name: $($file.Name)" }
        $stream=[IO.File]::OpenRead($file.FullName)
        try {
            $magic=New-Object byte[] 4
            if($stream.Read($magic,0,4) -ne 4 -or [Text.Encoding]::ASCII.GetString($magic) -notin @('BIGF','BIG4')) { throw "Invalid archive: $relative" }
        } finally { $stream.Dispose() }
        $hash=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        $remote="$destination/$relative"
        $existing=[string](& $Adb @adbPrefix shell "sha256sum $remote 2>/dev/null")
        if($LASTEXITCODE -ne 0 -or !$existing.StartsWith($hash)) {
            Write-Host "Copy $relative"
            Invoke-Adb @('push',$file.FullName,"$remote.incoming")
            $result=[string](& $Adb @adbPrefix shell sha256sum "$remote.incoming")
            if($LASTEXITCODE -ne 0 -or !$result.StartsWith($hash)) { throw "Checksum mismatch: $relative" }
            Invoke-Adb @('shell','mv','-f',"$remote.incoming",$remote)
        }
    }
    foreach($relative in @('Data','ZH_Generals\Data')) {
        $local=Join-Path $root $relative
        if(Test-Path -LiteralPath $local) {
            $directories=@(Get-Item -LiteralPath $local)+@(Get-ChildItem -LiteralPath $local -Directory -Recurse)
            foreach($directory in $directories) {
                $dirRelative=$directory.FullName.Substring($root.Length+1).Replace('\','/')
                Invoke-Adb @('shell',('mkdir -p '+(Quote-Shell "$destination/$dirRelative")))
            }
            $parent=if($relative.StartsWith('ZH_Generals')) { "$destination/ZH_Generals/" } else { "$destination/" }
            Invoke-Adb @('push','--sync',$local,$parent)
        }
    }
}
Invoke-Adb @('shell','am','start','-n',"$packageName/com.generalsx.zerohour.ThorLauncher")
Write-Host 'Installed. Reopen Thor RTS and press Play. Your PC installation was read only.'
