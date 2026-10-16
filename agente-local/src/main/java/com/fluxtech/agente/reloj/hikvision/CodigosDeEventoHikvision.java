package com.fluxtech.agente.reloj.hikvision;

import com.fluxtech.agente.modelo.EventoDeReloj;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Regla de negocio: qué eventos de un reloj Hikvision cuentan como fichada.
 *
 * Hikvision registra en la memoria todos los eventos de control de acceso (tipo principal 5):
 * fichadas aceptadas, pero también huellas que no coinciden, tarjetas vencidas, etc.
 * Se considera FICHADA todo evento que identifica a un empleado (trae legajo) y NO es un rechazo.
 *
 * Los códigos de rechazo son los del SDK de Hikvision. Si un modelo registra otro rechazo con
 * legajo, se ve en el diagnóstico (--probar-reloj muestra el código) y se agrega a esta lista.
 */
public final class CodigosDeEventoHikvision {

    /** Tipo principal "evento de control de acceso" (en ISAPI "major", en el SDK "dwMajor"). */
    public static final int TIPO_PRINCIPAL_EVENTO_DE_ACCESO = 5;

    /** Sub-tipos de evento ("minor") que son rechazos aunque el reloj haya reconocido al empleado. */
    private static final Set<Long> CODIGOS_DE_RECHAZO = new HashSet<>(Arrays.asList(
            3L,   // 0x03 tarjeta + contraseña: contraseña incorrecta
            4L,   // 0x04 tarjeta + contraseña: tiempo agotado
            5L,   // 0x05 tarjeta + contraseña: demasiados intentos
            6L,   // 0x06 tarjeta sin permiso
            7L,   // 0x07 tarjeta fuera del período válido
            8L,   // 0x08 tarjeta vencida
            9L,   // 0x09 tarjeta inválida
            39L,  // 0x27 huella no coincide
            76L   // 0x4C rostro no coincide
    ));

    private CodigosDeEventoHikvision() {
        // Clase de reglas: no se instancia
    }

    /** true si el evento identificó a un empleado y no es un rechazo conocido. */
    public static boolean esFichadaValida(EventoDeReloj evento) {
        return evento.identificoAUnEmpleado() && !esCodigoDeRechazo(evento.obtenerCodigoDeTipoDeEvento());
    }

    public static boolean esCodigoDeRechazo(long codigoDeTipoDeEvento) {
        return CODIGOS_DE_RECHAZO.contains(codigoDeTipoDeEvento);
    }
}
