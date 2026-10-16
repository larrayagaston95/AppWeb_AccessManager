package com.fluxtech.agente.reloj.hikvision.sdk;

import com.fluxtech.agente.reloj.CredencialesDelRelojIncorrectasException;
import com.sun.jna.Memory;

import java.io.IOException;

/**
 * Sesión abierta con un reloj a través del SDK (inicio y cierre de sesión).
 *
 * Recorrido del dato:
 *   LectorHikvisionSdk llama a iniciar() con IP, puerto (8000), usuario y contraseña.
 *   1. Se intenta NET_DVR_Login_V40 (método actual).
 *   2. Si falla por un motivo que no sea la contraseña, se intenta NET_DVR_Login_V30 (equipos viejos).
 *   3. El SDK escribe la información del equipo en un bloque de memoria; de ahí se lee el número de serie.
 *   Con la sesión abierta el lector busca eventos; al terminar llama a cerrar().
 */
final class SesionDelSdkHikvision {

    /** Bloque de memoria holgado para NET_DVR_DEVICEINFO_V40/V30 (solo se lee el número de serie, al principio). */
    private static final int TAMANIO_DEL_BLOQUE_DE_INFORMACION = 1024;
    private static final int SESION_NO_INICIADA = -1;

    private final BibliotecaHcNetSdk biblioteca;
    private int identificadorDeSesion = SESION_NO_INICIADA;
    private String numeroDeSerieDelEquipo = "";

    SesionDelSdkHikvision(BibliotecaHcNetSdk biblioteca) {
        this.biblioteca = biblioteca;
    }

    /** Inicia sesión; si ya estaba iniciada no hace nada. Lanza IOException con la causa traducida. */
    void iniciar(String direccionIp, int puerto, String usuario, String contrasenia) throws IOException {
        if (estaIniciada()) {
            return;
        }
        // Paso 1: método actual (V40)
        Memory informacionDelEquipo = crearBloqueDeInformacion();
        EstructurasDeSesionSdk.DatosDeInicioDeSesion datosDeInicio = new EstructurasDeSesionSdk.DatosDeInicioDeSesion();
        datosDeInicio.completar(direccionIp, puerto, usuario, contrasenia);
        datosDeInicio.write();
        int resultado = biblioteca.NET_DVR_Login_V40(datosDeInicio, informacionDelEquipo);
        int codigoDeError = resultado < 0 ? biblioteca.NET_DVR_GetLastError() : 0;

        // Paso 2: si falló por algo distinto de la contraseña, se prueba el método antiguo (V30)
        if (resultado < 0 && !CodigosDeErrorDelSdk.esErrorDeCredenciales(codigoDeError)) {
            informacionDelEquipo = crearBloqueDeInformacion();
            resultado = biblioteca.NET_DVR_Login_V30(direccionIp, (short) puerto, usuario, contrasenia, informacionDelEquipo);
            codigoDeError = resultado < 0 ? biblioteca.NET_DVR_GetLastError() : 0;
        }

        // Paso 3: interpretar el resultado
        if (resultado < 0) {
            if (CodigosDeErrorDelSdk.esErrorDeCredenciales(codigoDeError)) {
                throw new CredencialesDelRelojIncorrectasException();
            }
            throw new IOException("no se pudo iniciar sesión por SDK: " + CodigosDeErrorDelSdk.describir(codigoDeError));
        }
        identificadorDeSesion = resultado;
        // El número de serie es el primer campo de la estructura (48 bytes, SERIALNO_LEN)
        byte[] bytesDelNumeroDeSerie = informacionDelEquipo.getByteArray(0, EstructurasDeSesionSdk.LARGO_NUMERO_DE_SERIE);
        numeroDeSerieDelEquipo = EstructurasDeSesionSdk.leerTextoDeArreglo(bytesDelNumeroDeSerie);
    }

    /** Cierra la sesión si estaba abierta. */
    void cerrar() {
        if (estaIniciada()) {
            biblioteca.NET_DVR_Logout(identificadorDeSesion);
            identificadorDeSesion = SESION_NO_INICIADA;
        }
    }

    boolean estaIniciada() {
        return identificadorDeSesion != SESION_NO_INICIADA;
    }

    int obtenerIdentificadorDeSesion() {
        return identificadorDeSesion;
    }

    String obtenerNumeroDeSerieDelEquipo() {
        return numeroDeSerieDelEquipo;
    }

    /** Bloque de memoria en cero donde el SDK escribe la información del equipo. */
    private static Memory crearBloqueDeInformacion() {
        Memory bloque = new Memory(TAMANIO_DEL_BLOQUE_DE_INFORMACION);
        bloque.clear();
        return bloque;
    }
}
