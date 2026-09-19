@echo off
setlocal
cd /d "%~dp0"
echo Running install-watch.cmd (do not run Set-ExecutionPolicy)
echo Wear APK goes to the Galaxy Watch only. Not the phone.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-watch.ps1" %*
exit /b %ERRORLEVEL%
