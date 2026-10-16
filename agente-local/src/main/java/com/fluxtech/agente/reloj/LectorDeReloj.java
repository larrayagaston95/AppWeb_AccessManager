package com.fluxtech.agente.reloj;

import com.fluxtech.agente.modelo.EventoDeReloj;
import com.fluxtech.agente.modelo.InformacionDelEquipo;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Contrato que cumple cualquier forma de leer un reloj (Hikvision por ISAPI, Hikvision por SDK,
 * y en el futuro otras marcas). El resto del agente solo conoce esta interfaz: para sumar una
 * marca nueva alcanza con crear otra clase que la implemente.
 *
 * Recorrido del dato:
 *   LecturaDeRelojes pide al SeleccionadorDeLectorDeReloj el lector adecuado para cada reloj.
 *   Llama a obtenerInformacionDelEquipo() para confirmar el número de serie
 *   y después a leerEventosEntre() para traer los eventos de la memoria del reloj.
 *   Al terminar llama a cerrarConexion() (el SDK necesita cerrar la sesión; ISAPI no).
 */
public interface LectorDeReloj {

    /** Nombre del método para el registro y el diagnóstico ("ISAPI" o "SDK"). */
    String obtenerNombreDelMetodo();

    /** Modelo, número de serie y firmware que informa el propio equipo. */
    InformacionDelEquipo obtenerInformacionDelEquipo() throws IOException, InterruptedException;

    /** Todos los eventos de la memoria entre las dos fechas (incluye rechazos: se filtran después). */
    List<EventoDeReloj> leerEventosEntre(LocalDateTime desde, LocalDateTime hasta) throws IOException, InterruptedException;

    /** Libera la conexión con el reloj. Por defecto no hace nada. */
    default void cerrarConexion() {
        // ISAPI no mantiene sesión abierta
    }
}
