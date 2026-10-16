# Quita el agente FluxTech del inicio de Windows. No borra la carpeta ni los datos.
$Nombre = "FluxTech Agente"
$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) { Write-Host "Ejecutar desinstalar.bat como administrador." -ForegroundColor Red; exit 1 }

Stop-ScheduledTask -TaskName $Nombre -ErrorAction SilentlyContinue
Unregister-ScheduledTask -TaskName $Nombre -Confirm:$false -ErrorAction SilentlyContinue
Get-NetFirewallRule -DisplayName $Nombre -ErrorAction SilentlyContinue | Remove-NetFirewallRule

# Cerrar el proceso del agente si sigue abierto
Get-CimInstance Win32_Process -Filter "Name='javaw.exe'" |
    Where-Object { $_.CommandLine -like "*agente-fluxtech.jar*" } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force }

Write-Host "Agente desinstalado. Las fichadas pendientes quedan guardadas en la carpeta datos\ por si se reinstala." -ForegroundColor Green
