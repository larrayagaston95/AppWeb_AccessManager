package com.fluxtech.agente.reloj.hikvision.sdk;

import com.fluxtech.agente.modelo.EventoDeReloj;

import java.time.DateTimeException;
import java.time.LocalDateTime;

/**
 * Convierte un evento del SDK (estructura de C) en el EventoDeReloj que usa el resto del agente.
 *
 * Recorrido del dato:
 *   LectorHikvisionSdk recibe un EventoDeAccesoSdk de NET_DVR_GetNextRemoteConfig y llama a convertir().
 *   Se toman la hora, el sub-tipo del evento (para filtrar rechazos), el número de empleado y el modo
 *   de verificación. Devuelve null si el registro trae una fecha imposible (memoria dañada o vacía).
 *
 * Regla de Hikvision para el número de empleado: usar primero el campo de texto (byEmployeeNo);
 * si está vacío, usar el numérico (dwEmployeeNo) que usan los firmwares antiguos.
 */
final class ConversorDeEventosSdk {

    private ConversorDeEventosSdk() {
        // Clase de utilidades: no se instancia
    }

    static EventoDeReloj convertir(EstructurasDelEventoDeAccesoSdk.EventoDeAccesoSdk eventoDelSdk) {
        LocalDateTime fechaHora = convertirFechaHora(eventoDelSdk.fechaHora);
        if (fechaHora == null) {
            return null;
        }
        String legajo = obtenerLegajo(eventoDelSdk.detalle);
        String modo = describirModoDeVerificacion(eventoDelSdk.detalle.modoDeVerificacionActual);
        return new EventoDeReloj(legajo, fechaHora, eventoDelSdk.subTipo, modo);
    }

    /** byEmployeeNo (texto) si tiene contenido válido; si no, dwEmployeeNo (número) si es mayor que cero. */
    static String obtenerLegajo(EstructurasDelEventoDeAccesoSdk.DetalleDelEventoDeAcceso detalle) {
        String legajoComoTexto = EstructurasDeSesionSdk.leerTextoDeArreglo(detalle.numeroDeEmpleadoComoTexto);
        if (!legajoComoTexto.isEmpty() && esTextoImprimible(legajoComoTexto)) {
            return legajoComoTexto;
        }
        // dwEmployeeNo es un entero sin signo de 32 bits en C
        long legajoNumerico = detalle.numeroDeEmpleadoNumerico & 0xFFFFFFFFL;
        return legajoNumerico > 0 ? String.valueOf(legajoNumerico) : "";
    }

    /** NET_DVR_TIME → LocalDateTime; null si los valores no forman una fecha real. */
    private static LocalDateTime convertirFechaHora(EstructurasDeSesionSdk.FechaHoraSdk fecha) {
        try {
            return LocalDateTime.of(fecha.anio, fecha.mes, fecha.dia, fecha.hora, fecha.minuto, fecha.segundo);
        } catch (DateTimeException fechaImposible) {
            return null;
        }
    }

    /** Protección contra memoria con basura: un legajo válido son caracteres visibles comunes. */
    private static boolean esTextoImprimible(String texto) {
        for (int indice = 0; indice < texto.length(); indice++) {
            char caracter = texto.charAt(indice);
            if (caracter < 0x20 || caracter > 0x7E) {
                return false;
            }
        }
        return true;
    }

    /** byCurrentVerifyMode → texto corto (valores según la documentación del SDK). */
    private static String describirModoDeVerificacion(byte codigoDeModo) {
        switch (codigoDeModo) {
            case 3: return "tarjeta";
            case 5: return "huella";
            case 7: return "huella o tarjeta";
            case 8: return "huella y tarjeta";
            case 14: return "rostro";
            case 15: return "legajo y contraseña";
            default: return "HIKVISION_SDK";
        }
    }
}
