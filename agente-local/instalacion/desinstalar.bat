@echo off
REM Quita el agente FluxTech del inicio de Windows (no borra la carpeta ni los datos).
REM Ejecutar con clic derecho > "Ejecutar como administrador".
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0desinstalar.ps1"
pause
