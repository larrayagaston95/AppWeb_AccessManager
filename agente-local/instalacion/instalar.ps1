# Instalacion del agente FluxTech como tarea de Windows (arranca sola, sin abrir nada).
$ErrorActionPreference = "Stop"
$Nombre = "FluxTech Agente"
$Dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Jar = Join-Path $Dir "agente-fluxtech.jar"
$Props = Join-Path $Dir "agente.properties"

function Fin($msg) { Write-Host ""; Write-Host $msg -ForegroundColor Red; exit 1 }

# 1. Permisos de administrador
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { Fin "Hay que ejecutar instalar.bat como administrador (clic derecho > Ejecutar como administrador)." }

# 2. Java
$javaw = (Get-Command javaw.exe -ErrorAction SilentlyContinue).Source
if (-not $javaw) { Fin "No se encontro Java. Instalar Java 17 o superior (https://adoptium.net) y volver a ejecutar." }
Write-Host "Java encontrado: $javaw"

if (-not (Test-Path $Jar)) { Fin "No se encontro agente-fluxtech.jar en $Dir" }

# 3. Configuracion
if (-not (Test-Path $Props)) {
    Copy-Item (Join-Path $Dir "agente.properties.ejemplo") $Props
    Write-Host "Se creo agente.properties. Completalo (servidor y clave), guardalo y cerra el Bloc de notas." -ForegroundColor Yellow
    Start-Process notepad.exe $Props -Wait
}

# 4. Diagnostico antes de instalar
Write-Host ""
Write-Host "Probando la configuracion..." -ForegroundColor Cyan
$java = Join-Path (Split-Path $javaw) "java.exe"
& $java "-Dsun.stdout.encoding=UTF-8" "-Dstdout.encoding=UTF-8" -jar $Jar --probar
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    $r = Read-Host "El diagnostico encontro problemas. Instalar igual? (s/n)"
    if ($r -ne "s") { Fin "Instalacion cancelada. Corregir lo indicado y volver a ejecutar." }
}

# 5. Tarea programada: al iniciar Windows + cada 10 minutos por si se detuvo.
#    Sin limite de tiempo de ejecucion; si ya esta corriendo, no abre otra copia.
$accion = New-ScheduledTaskAction -Execute $javaw -Argument "-jar `"$Jar`"" -WorkingDirectory $Dir
$alIniciar = New-ScheduledTaskTrigger -AtStartup
$vigilancia = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 10) -RepetitionDuration (New-TimeSpan -Days 3650)
$ajustes = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew
$usuario = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
Register-ScheduledTask -TaskName $Nombre -Action $accion -Trigger @($alIniciar, $vigilancia) -Settings $ajustes -Principal $usuario -Force | Out-Null
Write-Host "Tarea '$Nombre' creada."

# 6. Firewall: permitir que los relojes de la red local envien eventos al receptor
$puerto = 8095
$linea = Select-String -Path $Props -Pattern '^\s*receptor\.puerto\s*=\s*(\d+)' | Select-Object -First 1
if ($linea) { $puerto = [int]$linea.Matches[0].Groups[1].Value }
Get-NetFirewallRule -DisplayName $Nombre -ErrorAction SilentlyContinue | Remove-NetFirewallRule
New-NetFirewallRule -DisplayName $Nombre -Direction Inbound -Protocol TCP -LocalPort $puerto -RemoteAddress LocalSubnet -Action Allow | Out-Null
Write-Host "Firewall: puerto $puerto abierto solo para la red local."

# 7. Arrancar ahora
Start-ScheduledTask -TaskName $Nombre
Write-Host ""
Write-Host "Listo. El agente esta corriendo y va a arrancar solo cada vez que se prenda la PC." -ForegroundColor Green
Write-Host "Registro: $(Join-Path $Dir 'datos\agente.log')"
Write-Host "En el panel de FluxTech el agente deberia aparecer 'En linea' en 1 o 2 minutos."
