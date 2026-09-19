#Requires -Version 5.1
# Install Health Sync Watch via adb. No Gradle. No phone APK.
#
# On the watch: Developer options -> Wireless debugging ON.
# Open "Pair with pairing code". Leave that screen open. Two different ports.
#
#   cd C:\IT\Cursor\health-connect-drive-export
#   git pull
#   .\install-watch.cmd -Pair 192.168.2.142:PAIR_PORT -PairCode 123456 -Watch 192.168.2.142:CONNECT_PORT
#
# PAIR_PORT = the port on the pairing-code screen (plus 6-digit code).
# CONNECT_PORT = the IP:port on the main Wireless debugging screen.
# Do not type the word IP. Numbers only.
param(
    [string]$Watch = "",
    [string]$Pair = "",
    [string]$PairCode = "",
    [switch]$Download
)

$ErrorActionPreference = "Continue"
if ($PSVersionTable.PSVersion.Major -ge 7) {
    $PSNativeCommandUseErrorActionPreference = $false
}
Set-Location $PSScriptRoot

$ApkUrl = "https://github.com/sdvirk-tech/health-connect-drive-export/raw/cursor/watch-vitals-export-8125/dist/HealthSync-wear-0.3.0-debug.apk"
$WearPackage = "ru.sdvirk.healthsync.wear"
$WearActivity = "ru.sdvirk.healthsync.wear/.WearMainActivity"

function Find-AndroidSdk {
    $candidates = New-Object System.Collections.Generic.List[string]
    foreach ($item in @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        (Join-Path $env:LOCALAPPDATA "Android\Sdk"),
        (Join-Path $env:USERPROFILE "AppData\Local\Android\Sdk"),
        "C:\Users\Di\AppData\Local\Android\Sdk",
        "$env:ProgramFiles\Android\android-sdk",
        "${env:ProgramFiles(x86)}\Android\android-sdk"
    )) {
        if ($item) { [void]$candidates.Add($item) }
    }
    $localProps = Join-Path $PSScriptRoot "local.properties"
    if (Test-Path $localProps) {
        Get-Content $localProps | ForEach-Object {
            if ($_ -match '^\s*sdk\.dir\s*=\s*(.+)\s*$') {
                $fromFile = $Matches[1].Trim().Trim('"').Replace('/', '\')
                if ($fromFile) { [void]$candidates.Add($fromFile) }
            }
        }
    }
    foreach ($sdk in $candidates) {
        $adbPath = Join-Path $sdk "platform-tools\adb.exe"
        if (Test-Path $adbPath) {
            return [System.IO.Path]::GetFullPath($sdk)
        }
    }
    return $null
}

function Resolve-WatchTarget([string]$raw) {
    if (-not $raw) { return $null }
    if ($raw -match '(\d{1,3}(?:\.\d{1,3}){3}:\d{1,5})') {
        $endpoint = $Matches[1]
        if ($raw -ne $endpoint) {
            Write-Host "Watch argument was '$raw' - using $endpoint" -ForegroundColor Yellow
            Write-Host "Do not type the word IP. Numbers only." -ForegroundColor Yellow
        }
        return $endpoint
    }
    Write-Host "Bad IP:port value: '$raw'" -ForegroundColor Red
    Write-Host "Copy ONLY the numbers from the watch, for example:" -ForegroundColor Red
    Write-Host "  .\install-watch.cmd -Watch 192.168.2.142:38959" -ForegroundColor Red
    Write-Host "Not: -Watch IP:192.168.2.142:38959" -ForegroundColor Red
    exit 1
}

function Get-DeviceSerials([string]$adb) {
    return @(& $adb devices) |
        Where-Object { $_ -match "\tdevice$" } |
        ForEach-Object { ($_ -split "\s+")[0] }
}

function Test-IsWatch([string]$adb, [string]$serial) {
    $chars = (& $adb -s $serial shell getprop ro.build.characteristics 2>$null | Out-String).ToLowerInvariant()
    if ($chars -match "watch") { return $true }
    $feat = (& $adb -s $serial shell pm list features 2>$null | Out-String)
    if ($feat -match "android.hardware.type.watch") { return $true }
    $hasProps = ($chars.Trim().Length -gt 0) -or ($feat.Trim().Length -gt 0)
    if ($hasProps) { return $false }
    if ($serial -match "^\d+\.\d+\.\d+\.\d+:") { return $true }
    return $false
}

function Find-WearApk {
    $distDir = Join-Path $PSScriptRoot "dist"
    if (Test-Path $distDir) {
        $found = @(Get-ChildItem $distDir -Filter "HealthSync-wear-*-debug.apk" -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending)
        if ($found.Count -gt 0) { return $found[0].FullName }
    }
    $here = Get-ChildItem $PSScriptRoot -Filter "HealthSync-wear-*-debug.apk" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending
    if ($here) { return $here[0].FullName }
    $built = Join-Path $PSScriptRoot "wear\build\outputs\apk\debug\wear-debug.apk"
    if (Test-Path $built) { return $built }
    return $null
}

function Get-WearApk {
    $apk = Find-WearApk
    if ($apk -and -not $Download) { return $apk }

    $distDir = Join-Path $PSScriptRoot "dist"
    if (-not (Test-Path $distDir)) {
        New-Item -ItemType Directory -Path $distDir | Out-Null
    }
    $dest = Join-Path $distDir "HealthSync-wear-0.3.0-debug.apk"
    Write-Host "==> Downloading Wear APK..."
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $ApkUrl -OutFile $dest -UseBasicParsing
    } catch {
        Write-Host "Download failed: $_" -ForegroundColor Red
        Write-Host "Save this file next to the script or into dist\:" -ForegroundColor Red
        Write-Host "  $ApkUrl" -ForegroundColor Red
        exit 1
    }
    if (-not (Test-Path $dest) -or ((Get-Item $dest).Length -lt 1000000)) {
        Write-Host "Downloaded file looks too small. Open the URL in a browser:" -ForegroundColor Red
        Write-Host "  $ApkUrl" -ForegroundColor Red
        exit 1
    }
    return $dest
}

function Show-PairHelp([string]$ipHint, [string]$connectHint) {
    Write-Host ""
    Write-Host "Galaxy Watch Ultra needs PAIRING first, then CONNECT. Two different ports." -ForegroundColor Yellow
    Write-Host "PC and watch must be on the same Wi-Fi (same 192.168.2.x)."
    Write-Host ""
    Write-Host "On the watch: Settings -> Developer options -> Wireless debugging ON."
    Write-Host "  Main screen IP:port           = CONNECT  ($ipHint`:$connectHint)"
    Write-Host "  Pair with pairing code        = another port + 6-digit code. Leave that screen open."
    Write-Host ""
    Write-Host "Then run (numbers only, no word IP):"
    Write-Host "  .\install-watch.cmd -Pair $ipHint`:PAIR_PORT -PairCode 123456 -Watch $ipHint`:$connectHint"
    Write-Host ""
}

$watchTarget = Resolve-WatchTarget $Watch
$pairTarget = $null
if ($Pair) { $pairTarget = Resolve-WatchTarget $Pair }

Write-Host "==> Looking for Android SDK (adb.exe)..."
$sdk = Find-AndroidSdk
if (-not $sdk) {
    Write-Host "Android SDK not found (need platform-tools\adb.exe)." -ForegroundColor Red
    Write-Host "Typical path: $env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    exit 1
}
$adb = Join-Path $sdk "platform-tools\adb.exe"
$env:PATH = "$(Join-Path $sdk 'platform-tools');" + $env:PATH
Write-Host "==> adb: $adb"

$apk = Get-WearApk
Write-Host "==> Wear APK: $apk"
Write-Host "==> Package:  $WearPackage (Galaxy Watch only)"

& $adb start-server | Out-Null

if ($pairTarget) {
    Write-Host "==> adb pair $pairTarget"
    Write-Host "Keep the pairing screen open on the watch (6-digit code)."
    if ($PairCode) {
        $pairOut = & $adb pair $pairTarget $PairCode 2>&1 | ForEach-Object { "$_" }
        Write-Host ($pairOut -join " ")
        $pairText = ($pairOut -join " ").ToLowerInvariant()
        if ($pairText -match "failed|error|wrong") {
            Write-Host "Pairing failed. Code and pairing port expire quickly. Open a NEW pairing screen on the watch." -ForegroundColor Red
            exit 2
        }
    } else {
        Write-Host "Type the 6-digit code from the watch, then Enter."
        & $adb pair $pairTarget
    }
}

if ($watchTarget) {
    Write-Host "==> adb connect $watchTarget"
    $connectOut = & $adb connect $watchTarget 2>&1 | ForEach-Object { "$_" }
    Write-Host ($connectOut -join " ")
    $connectText = ($connectOut -join " ").ToLowerInvariant()
    if ($connectText -match "failed to authenticate|failed to connect|unable to connect|cannot connect|no host") {
        $ipHint = "192.168.2.142"
        $connectHint = "36723"
        if ($watchTarget -match '^([^:]+):(\d+)$') {
            $ipHint = $Matches[1]
            $connectHint = $Matches[2]
        }
        Show-PairHelp $ipHint $connectHint
        exit 2
    }
}

$serials = Get-DeviceSerials $adb
$watchSerials = @()
foreach ($serial in $serials) {
    if (Test-IsWatch $adb $serial) {
        $watchSerials += $serial
    } else {
        Write-Host "==> Skip phone $serial (will not install Wear APK on a phone)" -ForegroundColor DarkYellow
    }
}

if ($watchTarget) {
    if ($watchSerials -contains $watchTarget) {
        $watchSerials = @($watchTarget)
    } elseif ($serials -contains $watchTarget) {
        Write-Host "$watchTarget is connected but is not a watch. Wear APK stays off the phone." -ForegroundColor Red
        exit 3
    } else {
        $watchSerials = @()
    }
}

if ($watchSerials.Count -eq 0) {
    $ipHint = "192.168.2.142"
    $connectHint = "36723"
    if ($watchTarget -and ($watchTarget -match '^([^:]+):(\d+)$')) {
        $ipHint = $Matches[1]
        $connectHint = $Matches[2]
    }
    Write-Host "adb did not see a watch." -ForegroundColor Yellow
    Write-Host "Current adb devices:"
    & $adb devices -l
    Show-PairHelp $ipHint $connectHint
    exit 2
}

$ok = $false
foreach ($serial in $watchSerials) {
    Write-Host "==> Watch $serial <- $apk"
    cmd.exe /c "`"$adb`" -s $serial install -r `"$apk`""
    if ($LASTEXITCODE -ne 0) {
        Write-Host "install failed on $serial" -ForegroundColor Red
        continue
    }
    Write-Host "==> Launch $WearPackage"
    cmd.exe /c "`"$adb`" -s $serial shell am start -n $WearActivity" | Out-Null
    $path = & $adb -s $serial shell pm path $WearPackage 2>$null
    Write-Host "==> Installed: $path"
    $ok = $true
}

if (-not $ok) {
    Write-Host "Wear APK was not installed." -ForegroundColor Red
    exit 3
}

Write-Host "==> Done. On the watch open Health Sync Watch, tap Permissions, then Background sensors."
exit 0
