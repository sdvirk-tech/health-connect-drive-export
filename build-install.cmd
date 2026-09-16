@echo off
setlocal
cd /d "%~dp0"
echo Running build-install.cmd (do not run Set-ExecutionPolicy)
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-install.ps1" %*
exit /b %ERRORLEVEL%
