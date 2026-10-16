# Agente local FluxTech

Programa que se instala en una PC del cliente (la que ya tenga el dueño). Lee los relojes de la red del local y sube las fichadas a la nube de FluxTech por HTTPS, con una clave propia del agente.

```
 LOCAL DEL CLIENTE                                 NUBE
 Reloj Hikvision ──(red local)──► Agente ──HTTPS + clave──► /api/agente/*
```

- **No hace falta que la PC esté siempre prendida.** Los relojes guardan las fichadas en su memoria (el DS-K1A802MF, hasta 150.000 registros de asistencia). Cuando la PC se prende, el agente lee todo lo que falta y lo sube.
- **No se pierden fichadas:** lo leído se guarda en disco (`datos/pendientes.json`) y solo se borra cuando la nube confirma que lo recibió. Si no hay internet, sube todo cuando vuelve.
- **No se duplican:** la nube ignora fichadas repetidas (mismo legajo y mismo segundo), así que reenviar es seguro.
- **Un solo `.jar`, sin dependencias.** Necesita Java 11 o superior (recomendado: Java 17 de https://adoptium.net).

## Instalación (una vez por local)

### 1. En el panel de FluxTech
1. **Cargar el reloj** (pantalla Relojes):
   - Marca: `Hikvision`.
   - Número de serie: el de la etiqueta del equipo.
   - **IP del reloj en la red del local** (ej. `192.168.1.50`), con su usuario y contraseña de administrador.
   - Sucursal: la del local.
2. **Crear el agente** (SuperAdmin → *Agentes locales*): elegir empresa y sucursal. **Copiar la clave**: se muestra una sola vez.

> El reloj necesita una **IP fija**, porque si cambia el agente no lo encuentra. Se configura en el reloj (Red → IP manual) o reservando la IP en el router.

### 2. En la PC del cliente
1. Instalar Java 17 si no lo tiene (https://adoptium.net, opción "JRE").
2. Descomprimir el paquete en `C:\FluxTechAgente`.
3. Copiar `agente.properties.ejemplo` como `agente.properties` y completar:
   ```
   servidor.url=https://tu-servidor
   agente.clave=fxa_...la clave copiada...
   ```
4. Doble clic en **`probar.bat`**. Tiene que terminar en `Resultado: TODO OK`.
5. Clic derecho en **`instalar.bat`** → *Ejecutar como administrador*.

Listo. En 1 o 2 minutos el agente aparece **"En línea"** en el panel.

Qué deja configurado `instalar.bat`:
- Una tarea de Windows que arranca el agente al prender la PC, sin abrir ventanas ni requerir iniciar sesión. Cada 10 minutos verifica que siga corriendo y, si se detuvo, lo vuelve a abrir.
- Una regla de firewall que abre el puerto del receptor (8095) **solo para la red local**.

## Probar un reloj antes de crear el agente

```
java -jar agente-fluxtech.jar --probar-reloj 192.168.1.50 admin CLAVE_DEL_RELOJ
```

Muestra el modelo y el número de serie, y las últimas fichadas leídas de la memoria con su código. Sirve para confirmar que ese modelo permite leer la memoria por la red (ISAPI).

## Modo receptor (opcional, tiempo real)

Además de leer la memoria cada minuto, el reloj puede **enviar** cada fichada al agente apenas ocurre. En la configuración del Hikvision (por web o iVMS-4200), en la sección de servidor de eventos / *HTTP Listening* (el nombre varía según el firmware), cargar:

- IP de destino: la IP de la PC del agente.
- Puerto: `8095`.

Solo se aceptan eventos que lleguen desde la IP de un reloj cargado en el panel. Si el reloj no tiene esa opción, no pasa nada: la lectura de la memoria alcanza.

## Archivos

| Archivo | Para qué |
|---|---|
| `agente.properties` | Servidor y clave (no compartir: la clave identifica al local) |
| `datos/agente.log` | Registro de lo que hace el agente (rota a los 5 MB) |
| `datos/pendientes.json` | Fichadas leídas que todavía no confirmó la nube |
| `datos/estado.json` | Hasta dónde se leyó cada reloj |
| `datos/config-cache.json` | Última lista de relojes, para seguir leyendo sin internet (incluye la contraseña de los relojes) |

## Problemas comunes

| Síntoma | Causa probable |
|---|---|
| `La nube rechazó la clave` | La clave se copió mal o fue revocada. Generar una nueva en el panel. |
| `Usuario o contraseña del reloj incorrectos` | Corregir los datos del reloj en el panel. |
| `no responde (¿apagado, desconectado o IP incorrecta?)` | El reloj cambió de IP o está fuera de la red. Revisar que tenga IP fija. |
| `En 192.168.x.x hay otro equipo` | La IP del panel apunta a otro reloj. Se bloquea a propósito para no cargar fichadas en el reloj equivocado. |
| `Este reloj no permite leer eventos por ISAPI` | Modelo o firmware sin esa función. Usar el modo receptor. |
| Fichadas con el legajo correcto pero descartadas | El legajo no existe en la empresa (cargar al empleado con el mismo número que en el reloj). |

## Desinstalar

Clic derecho en `desinstalar.bat` → *Ejecutar como administrador*. Las fichadas pendientes quedan en `datos\` por si se reinstala.

## Para desarrolladores

- Compilar: `mvn package` (genera `target/agente-fluxtech.jar`), o sin Maven:
  `javac --release 11 -d out $(find src/main/java -name '*.java') && jar cfe agente-fluxtech.jar com.fluxtech.agente.Main -C out .`
- Prueba de punta a punta con un Hikvision simulado (Digest real, paginación ISAPI) y una nube simulada:
  `javac -cp out -d out-test src/test/java/com/fluxtech/agente/PruebaIntegral.java && java -cp out:out-test com.fluxtech.agente.PruebaIntegral`
- API de la nube (header `X-Agent-Key`): `GET /api/agente/config`, `POST /api/agente/fichadas`, `POST /api/agente/heartbeat`.
- Para sumar otra marca de reloj: una clase como `HikvisionIsapi` y su caso en `Agente.leerReloj`. La nube no cambia.
