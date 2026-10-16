@echo off
REM Prueba un reloj Hikvision sin necesidad de la nube ni de agente.properties.
REM Indica si se puede leer por ISAPI o por el SDK oficial (si esta copiado en sdk-hikvision\).
chcp 65001 >nul
cd /d "%~dp0"
where java >nul 2>&1
if errorlevel 1 (
  echo No se encontro Java. Instalar Java 17 o superior desde https://adoptium.net
  pause
  exit /b 1
)
set /p IP_DEL_RELOJ=IP del reloj (ej. 192.168.1.50): 
set /p USUARIO_DEL_RELOJ=Usuario del reloj (ej. admin): 
set /p CLAVE_DEL_RELOJ=Contrasena del reloj: 
java -Dsun.stdout.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "%~dp0agente-fluxtech.jar" --probar-reloj %IP_DEL_RELOJ% %USUARIO_DEL_RELOJ% %CLAVE_DEL_RELOJ%
echo.
pause
