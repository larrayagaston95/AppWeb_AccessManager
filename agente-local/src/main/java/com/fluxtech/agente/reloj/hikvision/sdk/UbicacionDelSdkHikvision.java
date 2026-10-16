package com.fluxtech.agente.reloj.hikvision.sdk;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dónde se busca el SDK de Hikvision y si está instalado.
 *
 * Esta clase NO usa JNA a propósito: el selector de lectores la consulta en cada ciclo, y si JNA
 * faltara por algún motivo, la lectura por ISAPI tiene que seguir funcionando igual.
 */
public final class UbicacionDelSdkHikvision {

    private UbicacionDelSdkHikvision() {
        // Clase de utilidades: no se instancia
    }

    /** "HCNetSDK.dll" en Windows, "libhcnetsdk.so" en Linux. */
    public static String obtenerNombreDelArchivoPrincipal() {
        boolean esWindows = System.getProperty("os.name", "").toLowerCase().contains("windows");
        return esWindows ? "HCNetSDK.dll" : "libhcnetsdk.so";
    }

    /** true si la carpeta tiene el archivo principal del SDK (no garantiza que cargue bien). */
    public static boolean estaInstaladoEn(Path carpetaDelSdk) {
        return Files.exists(carpetaDelSdk.resolve(obtenerNombreDelArchivoPrincipal()));
    }
}
