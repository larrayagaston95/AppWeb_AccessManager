package com.fluxtech.agente.reloj.hikvision.sdk;

import com.fluxtech.agente.registro.RegistroDeEventos;
import com.fluxtech.agente.reloj.MetodoDeLecturaNoSoportadoException;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Platform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Carga la biblioteca nativa de Hikvision (HCNetSDK) una sola vez por ejecución del agente.
 * Dependencias de terceros: HCNetSDK y JNA (justificación en BibliotecaHcNetSdk.java).
 *
 * Recorrido del dato:
 *   LectorHikvisionSdk pide obtenerBibliotecaInicializada() con la carpeta del SDK.
 *   1. Se verifica que la carpeta tenga HCNetSDK.dll (Windows) o libhcnetsdk.so (Linux).
 *   2. JNA carga la biblioteca y la expone como la interfaz BibliotecaHcNetSdk.
 *   3. Se configuran las rutas internas del SDK y se llama a NET_DVR_Init.
 *   Si algo falla se lanza MetodoDeLecturaNoSoportadoException con un mensaje para quien instala.
 */
public final class CargadorDelSdkHikvision {

    private static final String NOMBRE_DEL_ARCHIVO = "CargadorDelSdkHikvision.java";

    private static BibliotecaHcNetSdk bibliotecaInicializada;

    private CargadorDelSdkHikvision() {
        // Clase de utilidades: no se instancia
    }

    /** Devuelve el SDK listo para usar; lo carga e inicializa la primera vez. */
    public static synchronized BibliotecaHcNetSdk obtenerBibliotecaInicializada(Path carpetaDelSdk) throws IOException {
        if (bibliotecaInicializada != null) {
            return bibliotecaInicializada;
        }
        Path archivoPrincipal = carpetaDelSdk.resolve(UbicacionDelSdkHikvision.obtenerNombreDelArchivoPrincipal()).toAbsolutePath();
        // Paso 1: el SDK no viene con el agente; si no lo copiaron, se explica qué hacer
        if (!Files.exists(archivoPrincipal)) {
            throw new MetodoDeLecturaNoSoportadoException("el SDK de Hikvision no está instalado: falta "
                    + archivoPrincipal + " (descargarlo de Hikvision y copiarlo en esa carpeta, ver README)");
        }
        try {
            // Paso 2: cargar la DLL con su ruta completa (así Windows encuentra también sus dependencias)
            BibliotecaHcNetSdk biblioteca = Native.load(archivoPrincipal.toString(), BibliotecaHcNetSdk.class);
            // Paso 3: rutas internas y arranque del SDK
            configurarRutasInternas(biblioteca, carpetaDelSdk.toAbsolutePath());
            if (!biblioteca.NET_DVR_Init()) {
                throw new IOException("NET_DVR_Init falló: " + CodigosDeErrorDelSdk.describir(biblioteca.NET_DVR_GetLastError()));
            }
            biblioteca.NET_DVR_SetConnectTime(5000, 1);
            biblioteca.NET_DVR_SetReconnect(10000, true);
            Runtime.getRuntime().addShutdownHook(new Thread(biblioteca::NET_DVR_Cleanup));
            bibliotecaInicializada = biblioteca;
            RegistroDeEventos.informar("SDK de Hikvision cargado desde " + archivoPrincipal);
            return biblioteca;
        } catch (UnsatisfiedLinkError errorDeCarga) {
            String descripcion = "no se pudo cargar el SDK (" + errorDeCarga.getMessage() + "). Verificar que el SDK sea de "
                    + (Platform.is64Bit() ? "64" : "32") + " bits, igual que Java, y que estén todos sus archivos";
            RegistroDeEventos.registrarError(NOMBRE_DEL_ARCHIVO, "obtenerBibliotecaInicializada", descripcion);
            throw new MetodoDeLecturaNoSoportadoException(descripcion);
        } catch (IOException | RuntimeException excepcion) {
            RegistroDeEventos.registrarError(NOMBRE_DEL_ARCHIVO, "obtenerBibliotecaInicializada", RegistroDeEventos.describirFallo(excepcion));
            throw excepcion;
        }
    }

    /**
     * Le indica al SDK dónde están sus componentes (HCNetSDKCom) y las bibliotecas de cifrado.
     * Es lo que hace el ejemplo oficial de Hikvision; los SDK viejos no tienen esta función y se ignora.
     */
    private static void configurarRutasInternas(BibliotecaHcNetSdk biblioteca, Path carpetaDelSdk) {
        try {
            EstructurasDeSesionSdk.RutaLocalDelSdk rutaDeComponentes = new EstructurasDeSesionSdk.RutaLocalDelSdk();
            EstructurasDeSesionSdk.copiarTextoEnArreglo(carpetaDelSdk + File.separator, rutaDeComponentes.rutaDeLaCarpeta);
            rutaDeComponentes.write();
            biblioteca.NET_DVR_SetSDKInitCfg(BibliotecaHcNetSdk.CONFIGURACION_INICIAL_CARPETA_DEL_SDK, rutaDeComponentes.getPointer());
            configurarRutaDeArchivo(biblioteca, carpetaDelSdk, "libcrypto", BibliotecaHcNetSdk.CONFIGURACION_INICIAL_RUTA_LIBCRYPTO);
            configurarRutaDeArchivo(biblioteca, carpetaDelSdk, "libssl", BibliotecaHcNetSdk.CONFIGURACION_INICIAL_RUTA_LIBSSL);
        } catch (UnsatisfiedLinkError sdkAntiguoSinEstaFuncion) {
            RegistroDeEventos.detallar("SDK sin NET_DVR_SetSDKInitCfg (versión antigua): se usan las rutas por defecto");
        }
    }

    /** Busca en la carpeta un archivo que empiece con el prefijo (ej. libcrypto-1_1-x64.dll) y se lo pasa al SDK. */
    private static void configurarRutaDeArchivo(BibliotecaHcNetSdk biblioteca, Path carpetaDelSdk, String prefijo, int tipo) {
        File[] coincidencias = carpetaDelSdk.toFile().listFiles((carpeta, nombre) ->
                nombre.toLowerCase().startsWith(prefijo) && (nombre.endsWith(".dll") || nombre.contains(".so")));
        if (coincidencias == null || coincidencias.length == 0) {
            return;
        }
        byte[] rutaComoBytes = new byte[256];
        EstructurasDeSesionSdk.copiarTextoEnArreglo(coincidencias[0].getAbsolutePath(), rutaComoBytes);
        Memory memoriaConLaRuta = new Memory(rutaComoBytes.length);
        memoriaConLaRuta.write(0, rutaComoBytes, 0, rutaComoBytes.length);
        biblioteca.NET_DVR_SetSDKInitCfg(tipo, memoriaConLaRuta);
    }
}
