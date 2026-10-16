@echo off
REM Instala el agente FluxTech para que arranque solo con Windows.
REM Ejecutar con clic derecho > "Ejecutar como administrador".
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar.ps1"
pause
