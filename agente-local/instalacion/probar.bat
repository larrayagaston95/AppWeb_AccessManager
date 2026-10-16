@echo off
REM Diagnostico del agente FluxTech: revisa configuracion, nube y relojes.
chcp 65001 >nul
cd /d "%~dp0"
where java >nul 2>&1
if errorlevel 1 (
  echo No se encontro Java. Instalar Java 17 o superior desde https://adoptium.net
  pause
  exit /b 1
)
java -Dsun.stdout.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar "%~dp0agente-fluxtech.jar" --probar
echo.
pause
