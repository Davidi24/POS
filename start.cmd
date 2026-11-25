@echo off
rem Runs the POS on Windows, like start.sh on Linux and macOS. From PowerShell or cmd, or with a double click:
rem   .\start.cmd                    backend (in Docker Desktop) + desktop app
rem   .\start.cmd app 192.168.1.20   only the desktop app, for the backend on another computer on this Wi-Fi
rem   .\start.cmd help               everything it can do
rem The work is done by start.ps1, run here past PowerShell's script policy, so nothing has to change on the computer.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" %*
exit /b %ERRORLEVEL%
