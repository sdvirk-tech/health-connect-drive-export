#Requires -Version 5.1
# Build phone + watch APKs on JDK 17 and install via adb.
# Gradle 8.11 cannot run on Java 25 from Android Studio JBR (error text is just "25.0.3").
param(
    [string]$Watch = ""
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Get-JavaMajor([string]$javaExe) {
    $ver = & $javaExe -version 2>&1 | Out-String
    if ($ver -match 'version "1\.(\d+)') { return [int]$Matches[1] }
    if ($ver -match 'version "(\d+)') { return [int]$Matches[1] }
    return 0
}

function Find-Jdk17 {
    $dirs = New-Object System.Collections.Generic.List[string]
    $roots = @(
        "$env:ProgramFiles\Eclipse Adoptium",
        "$env:ProgramFiles\Microsoft",
        "$env:ProgramFiles\Java",
        "$env:ProgramFiles\Amazon Corretto",
        "$env:ProgramFiles\Zulu",
        "${env:ProgramFiles(x86)}\Eclipse Adoptium",
        "$env:LOCALAPPDATA\Programs\Eclipse Adoptium",
        "$env:USERPROFILE\.jdks"
    )
    foreach ($root in $roots) {
        if (Test-Path $root) {
            Get-ChildItem $root -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                [void]$dirs.Add($_.FullName)
            }
        }
    }
    if ($env:JAVA_HOME) { [void]$dirs.Add($env:JAVA_HOME) }

    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd -and $javaCmd.Source) {
        $homeFromPath = Split-Path (Split-Path $javaCmd.Source -Parent) -Parent
        if ($homeFromPath) { [void]$dirs.Add($homeFromPath) }
    }

    foreach ($dir in $dirs) {
        $java = Join-Path $dir "bin\java.exe"
        if (-not (Test-Path $java)) { continue }
        $major = Get-JavaMajor $java
        if ($major -eq 17) { return $dir }
        if ($major -ge 25) {
            Write-Host "Skip Java $major ($dir) - Gradle 8.11 fails with error $major.x" -ForegroundColor DarkYellow
        }
    }
    return $null
}

Write-Host "==> Looking for JDK 17 (not Java 25 from Android Studio\jbr)..."
$jdk = Find-Jdk17
if (-not $jdk) {
    Write-Host "==> JDK 17 not found. Installing Eclipse Temurin 17 (winget)..."
    $winget = Get-Command winget -ErrorAction SilentlyContinue
    if (-not $winget) {
        Write-Host "winget not found. Install JDK 17: https://adoptium.net/temurin/releases/?version=17" -ForegroundColor Red
        Write-Host "Do not set JAVA_HOME to C:\Program Files\Android\Android Studio\jbr (that is Java 25)." -ForegroundColor Red
        exit 1
    }
    & winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-package-agreements --accept-source-agreements --disable-interactivity
    $jdk = Find-Jdk17
}
if (-not $jdk) {
    Write-Host "JDK 17 still not found. Install https://adoptium.net/temurin/releases/?version=17" -ForegroundColor Red
    Write-Host "Then open a NEW PowerShell window and run .\build-install.cmd again." -ForegroundColor Red
    Write-Host "Do not set JAVA_HOME to Android Studio\jbr - that is Java 25 and Gradle prints only: 25.0.3" -ForegroundColor Red
    exit 1
}

$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;" + $env:PATH
$major = Get-JavaMajor "$jdk\bin\java.exe"
if ($major -ne 17) {
    Write-Host "JAVA_HOME is still Java $major : $jdk" -ForegroundColor Red
    exit 1
}

Write-Host "==> JAVA_HOME=$env:JAVA_HOME"
& "$jdk\bin\java.exe" -version

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adb = "C:\Users\Di\AppData\Local\Android\Sdk\platform-tools\adb.exe"
}
if (-not (Test-Path $adb)) {
    Write-Host "adb not found: $adb" -ForegroundColor Red
    Write-Host "Android Studio: Settings -> Android SDK -> copy SDK Location, then platform-tools\adb.exe"
    exit 1
}

Write-Host "==> Building :app and :wear (Gradle 8.11 on JDK 17)..."
& .\gradlew.bat :app:assembleDebug :wear:assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$phoneApk = "app\build\outputs\apk\debug\app-debug.apk"
$wearApk = "wear\build\outputs\apk\debug\wear-debug.apk"
if (-not (Test-Path $phoneApk) -or -not (Test-Path $wearApk)) {
    Write-Host "APK files were not produced." -ForegroundColor Red
    exit 1
}

Write-Host "==> adb: $adb"
& $adb start-server | Out-Null
if ($Watch) {
    Write-Host "==> adb connect $Watch"
    & $adb connect $Watch
}

$serials = @(& $adb devices) |
    Select-Object -Skip 1 |
    Where-Object { $_ -match "\tdevice$" } |
    ForEach-Object { ($_ -split "\s+")[0] }

if ($serials.Count -eq 0) {
    Write-Host ""
    Write-Host "Build OK, but adb sees no devices." -ForegroundColor Yellow
    Write-Host "Phone: USB debugging, tap Allow this computer on the phone screen."
    Write-Host "Galaxy Watch Ultra (SM-L705F):"
    Write-Host "  Developer options -> Wireless debugging OFF, then ON."
    Write-Host "  The port changes every time. 192.168.2.142:36169 is already dead."
    Write-Host "  Copy IP:PORT from the watch and run:"
    Write-Host ""
    Write-Host ("  .\build-install.cmd -Watch IP:PORT")
    Write-Host ""
    exit 2
}

$installed = $false
foreach ($serial in $serials) {
    if ($serial -match "^\d+\.\d+\.\d+\.\d+:") {
        Write-Host "==> Watch $serial <- wear APK (ru.sdvirk.healthsync.wear)"
        & $adb -s $serial install -r $wearApk
        $installed = $true
    } else {
        Write-Host "==> Phone $serial <- app APK (ru.sdvirk.healthsync)"
        & $adb -s $serial install -r $phoneApk
        $installed = $true
    }
}

if ($installed) {
    Write-Host "==> Done. Open Health Sync Watch on the watch, Health Sync on the phone."
}
