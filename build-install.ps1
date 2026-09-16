#Requires -Version 5.1
<#
.SYNOPSIS
  Сборка Health Sync (телефон + часы) на JDK 17 и установка через adb.

.DESCRIPTION
  Gradle 8.11 / AGP 8.9 не работают на Java 25 из Android Studio JBR
  (ошибка сборки ровно "25.0.3"). Скрипт находит или ставит JDK 17,
  собирает APK и ставит их на USB-телефон и Wi-Fi часы.

.PARAMETER Watch
  Опционально: IP:порт беспроводной отладки часов, например 192.168.2.142:41234.
  Старый порт 36169 уже недействителен — на часах выключи/включи отладку и возьми новый.
#>
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
            Write-Host "Пропускаю Java $major ($dir) — Gradle 8.11 на ней падает с ошибкой $major.x" -ForegroundColor DarkYellow
        }
    }
    return $null
}

Write-Host "==> Ищу JDK 17 (не Java 25 из Android Studio\jbr)..."
$jdk = Find-Jdk17
if (-not $jdk) {
    Write-Host "==> JDK 17 нет. Ставлю Eclipse Temurin 17 (winget)..."
    $winget = Get-Command winget -ErrorAction SilentlyContinue
    if (-not $winget) {
        Write-Host "winget не найден. Поставь JDK 17: https://adoptium.net/temurin/releases/?version=17" -ForegroundColor Red
        Write-Host "Не используй JAVA_HOME=C:\Program Files\Android\Android Studio\jbr (это Java 25)." -ForegroundColor Red
        exit 1
    }
    & winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-package-agreements --accept-source-agreements --disable-interactivity
    $jdk = Find-Jdk17
}
if (-not $jdk) {
    Write-Host "JDK 17 так и не найден. Установи https://adoptium.net/temurin/releases/?version=17" -ForegroundColor Red
    Write-Host "Закрой это окно, открой новое PowerShell и запусти .\build-install.ps1 снова." -ForegroundColor Red
    Write-Host "Не ставь JAVA_HOME на Android Studio\jbr — оттуда Java 25 и Gradle пишет только: 25.0.3" -ForegroundColor Red
    exit 1
}

$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;" + $env:PATH
$major = Get-JavaMajor "$jdk\bin\java.exe"
if ($major -ne 17) {
    Write-Host "JAVA_HOME всё ещё Java $major: $jdk" -ForegroundColor Red
    exit 1
}

Write-Host "==> JAVA_HOME=$env:JAVA_HOME"
& "$jdk\bin\java.exe" -version

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adb = "C:\Users\Di\AppData\Local\Android\Sdk\platform-tools\adb.exe"
}
if (-not (Test-Path $adb)) {
    Write-Host "adb не найден: $adb" -ForegroundColor Red
    Write-Host "В Android Studio: Settings → Android SDK → скопируй SDK Location, затем:"
    Write-Host "  Join-Path <SDK> 'platform-tools\adb.exe'"
    exit 1
}

Write-Host "==> Собираю :app и :wear (Gradle 8.11 на JDK 17)..."
& .\gradlew.bat :app:assembleDebug :wear:assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$phoneApk = "app\build\outputs\apk\debug\app-debug.apk"
$wearApk = "wear\build\outputs\apk\debug\wear-debug.apk"
if (-not (Test-Path $phoneApk) -or -not (Test-Path $wearApk)) {
    Write-Host "APK не собрались." -ForegroundColor Red
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
    Write-Host "Сборка OK, но adb не видит устройства." -ForegroundColor Yellow
    Write-Host "Телефон: USB, отладка, на экране «разрешить этот компьютер»."
    Write-Host "Часы Galaxy Watch Ultra (SM-L705F):"
    Write-Host "  Параметры разработчика → Беспроводная отладка ВЫКЛ, затем ВКЛ."
    Write-Host "  Порт каждый раз новый. 192.168.2.142:36169 уже мёртвый."
    Write-Host "  С экрана часов скопируй IP:порт и выполни:"
    Write-Host ""
    Write-Host "  & `"$adb`" connect IP:ПОРТ"
    Write-Host "  .\build-install.ps1 -Watch IP:ПОРТ"
    Write-Host ""
    exit 2
}

$installed = $false
foreach ($serial in $serials) {
    if ($serial -match "^\d+\.\d+\.\d+\.\d+:") {
        Write-Host "==> Часы $serial ← wear APK (ru.sdvirk.healthsync.wear)"
        & $adb -s $serial install -r $wearApk
        $installed = $true
    } else {
        Write-Host "==> Телефон $serial ← app APK (ru.sdvirk.healthsync)"
        & $adb -s $serial install -r $phoneApk
        $installed = $true
    }
}

if ($installed) {
    Write-Host "==> Готово. На часах открой Health Sync Watch, на телефоне — Health Sync."
}
