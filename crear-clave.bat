@echo off
title ScudoDNS - crear clave de firma
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0crear-clave.ps1"
echo.
pause
