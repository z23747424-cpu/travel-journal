@echo off
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0desktop-preview\scrapbook\start-preview.ps1"
if errorlevel 1 pause
