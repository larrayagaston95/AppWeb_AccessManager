# Agente local FluxTech

Programa que se instala en la PC del cliente (la que ya tenga el dueño). Lee los relojes de la red del local y sube las fichadas a la nube de FluxTech por HTTPS, con una clave propia del agente.

```
 LOCAL DEL CLIENTE                                       NUBE
 Reloj Hikvision ──(red local: ISAPI o SDK)──► Agente ──HTTPS + clave──► /api/agente/*
```

- **La PC no necesita estar siempre prendida.** Los relojes guardan las fichadas en su memoria (el DS-K1A802MF, hasta 150.000). Al prenderse, el agente lee lo que falta y lo sube.
- **No se pierden fichadas.** Lo leído se guarda en disco y solo se borra cuando la nube confirma que lo recibió.
- **No se duplican.** La nube descarta fichadas repetidas (mismo legajo y segundo).
- **Dos formas de leer los Hikvision**, elegidas solas en modo `automatico`:
  1. **ISAPI**: por la red, sin instalar nada. Es lo que usan los modelos recientes.
  2. **SDK oficial de Hikvision**: el mismo método que usa iVMS-4200. Sirve para modelos y firmwares viejos que no tienen ISAPI de eventos. Requiere copiar el SDK (ver más abajo).
- Requiere Java 11 o superior (recomendado Java 17 de https://adoptium.net, de 64 bits).

## Instalación (una vez por local)

### 1. En el panel de FluxTech
1. **Cargar el reloj** (pantalla Relojes): marca `Hikvision`, número de serie (etiqueta del equipo), **IP en la red del local** (ej. `192.168.1.50`), usuario y contraseña de administrador del reloj, y la sucursal del local.
2. **Crear el agente** (SuperAdmin → *Agentes locales*): empresa y sucursal. **Copiar la clave**: se muestra una sola vez.

> El reloj necesita **IP fija** (configurada en el reloj o reservada en el router).

### 2. En la PC del cliente
1. Instalar Java 17 (JRE de 64 bits) si no lo tiene.
2. Descomprimir el paquete en `C:\FluxTechAgente`.
3. **Probar el reloj:** doble clic en `probar-reloj.bat`, con su IP, usuario y contraseña. El resultado indica si se lee por ISAPI o hace falta el SDK.
4. Si hace falta el SDK: ver **"SDK de Hikvision"** más abajo.
5. Copiar `agente.properties.ejemplo` como `agente.properties` y completar `servidor.url` y `agente.clave`.
6. Doble clic en `probar.bat`. Tiene que terminar en `Resultado: TODO OK`.
7. Clic derecho en `instalar.bat` → *Ejecutar como administrador*.

En 1 o 2 minutos el agente aparece **"En línea"** en el panel. `instalar.bat` crea una tarea de Windows que lo arranca al prender la PC (sin ventanas y sin iniciar sesión) y lo reabre si se detiene. También abre el puerto del receptor (8095) solo para la red local.

## SDK de Hikvision (solo si ISAPI no funciona)

1. Descargar **Device Network SDK (HCNetSDK) para Windows de 64 bits** del sitio de Hikvision. Es gratuito.
2. Copiar el contenido de su carpeta `lib` (`HCNetSDK.dll`, `HCCore.dll`, la carpeta `HCNetSDKCom` y las `libcrypto`/`libssl`) en `C:\FluxTechAgente\sdk-hikvision\`.
3. Volver a correr `probar-reloj.bat`. Tiene que aparecer `Método SDK ... [OK]`.

No se incluye en el paquete porque lo distribuye Hikvision con sus propias condiciones. Java y el SDK tienen que ser **ambos de 64 bits**. El SDK se conecta al puerto **8000** del reloj (configurable con `hikvision.sdk.puerto`).

> **Estado:** el adaptador del SDK se armó siguiendo la estructura oficial de HCNetSDK y se probó contra una biblioteca que la imita (tamaños de estructuras verificados contra C). Todavía no se probó con un reloj real. La primera prueba con el DS-K1A802MF puede requerir un ajuste menor; `probar-reloj.bat` muestra lo que lee para compararlo con la pantalla del reloj.

## Modo receptor (opcional, tiempo real)

Además de leer la memoria cada minuto, el reloj puede **enviar** cada fichada al agente apenas ocurre. Se configura en el Hikvision, en servidor de eventos / *HTTP Listening*, con la IP de la PC y el puerto `8095`. Solo se aceptan eventos que lleguen desde la IP de un reloj cargado en el panel.

## Problemas comunes

| Síntoma en el registro (`datos\agente.log`) | Qué hacer |
|---|---|
| `La nube rechazó la clave` | Generar una clave nueva en el panel y cargarla en `agente.properties`. |
| `Usuario o contraseña del reloj incorrectos` | Corregir los datos del reloj en el panel. |
| `no responde (¿apagado, desconectado o dirección incorrecta?)` | El reloj cambió de IP o está fuera de la red. Revisar la IP fija. |
| `hay otro equipo (serie ...)` | La IP del panel apunta a otro reloj. Se bloquea para no cargar fichadas en el reloj equivocado. |
| `el reloj no permite ... por ISAPI` | Instalar el SDK de Hikvision (sección anterior). |
| `el SDK de Hikvision no está instalado` | Falta copiar el SDK en `sdk-hikvision\`. |
| `El legajo X no existe en la empresa` | Cargar al empleado en el panel con el mismo número que en el reloj. |

## Desinstalar

Clic derecho en `desinstalar.bat` → *Ejecutar como administrador*. Las fichadas pendientes quedan en `datos\`.

---

## Para desarrolladores

### Mapa del código (`src/main/java/com/fluxtech/agente/`)

| Paquete | Responsabilidad |
|---|---|
| `Main` | Punto de entrada: modos `--probar`, `--probar-reloj`, `--probar-sdk` e instancia única. |
| `configuracion/` | Lee y valida `agente.properties` (exige https hacia internet). |
| `sincronizacion/` | El ciclo: `CicloDeSincronizacion` coordina `ActualizadorDeConfiguracion` → `LecturaDeRelojes` → `SubidaDeFichadas` → heartbeat. Las reglas de negocio están en `ReglasDeLectura`. |
| `reloj/` | `LectorDeReloj` (interfaz común) y `SeleccionadorDeLectorDeReloj` (ISAPI/SDK/automático). |
| `reloj/hikvision/` | Lectura por ISAPI (Digest) y `CodigosDeEventoHikvision` (qué cuenta como fichada). |
| `reloj/hikvision/sdk/` | Adaptador del SDK oficial (JNA): carga, estructuras de C, sesión, búsqueda y conversión. |
| `cola/` | Cola persistente y estado de lectura, con escritura atómica. |
| `receptor/` | Servidor HTTP local para los relojes que envían eventos. |
| `nube/` | `ServicioNubeFluxTech`: único lugar que habla con la API de la nube. |
| `json/`, `util/`, `registro/` | JSON sin dependencias, fechas y registro de eventos (`Error en [archivo] -> [función]: ...`). |

Para sumar otra marca de reloj alcanza con implementar `LectorDeReloj` y agregarla al selector. La nube no cambia.

### Compilar
- `mvn package` → `target/agente-fluxtech.jar`, con JNA incluido.

### Pruebas
```
# 1. Biblioteca nativa que imita al SDK de Hikvision (Linux)
gcc -shared -fPIC -o sdk-simulado/libhcnetsdk.so src/test/c/hcnetsdk_simulado.c
# 2. Compilar (JNA en el classpath)
javac -cp jna.jar -d clases $(find src/main/java -name '*.java')
javac -cp jna.jar:clases -d pruebas $(find src/test/java -name '*.java')
# 3. Lector por SDK: tamaños de estructuras Java = C, flujo completo, errores
java -cp clases:pruebas:jna.jar com.fluxtech.agente.reloj.hikvision.sdk.PruebaDelSdkHikvision sdk-simulado
# 4. Agente de punta a punta (reloj ISAPI y nube simulados) + paso automático a SDK
java -cp clases:pruebas:jna.jar com.fluxtech.agente.pruebas.PruebaIntegralDelAgente sdk-simulado
```
