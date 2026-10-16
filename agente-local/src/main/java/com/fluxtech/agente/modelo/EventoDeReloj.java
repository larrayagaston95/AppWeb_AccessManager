package com.fluxtech.agente.modelo;

import java.time.LocalDateTime;

/**
 * Evento crudo leído de la memoria de un reloj (puede ser una fichada válida o un rechazo).
 *
 * Recorrido del dato:
 *   El lector del reloj (ISAPI o SDK) crea un EventoDeReloj por cada registro que trae la memoria.
 *   Las reglas de negocio de la marca (por ejemplo CodigosDeEventoHikvision) deciden si es una fichada.
 *   Solo los que son fichada se convierten en FichadaLeida y van a la cola.
 */
public final class EventoDeReloj {

    /** Número de empleado; vacío si el evento no identificó a nadie (huella desconocida, puerta, alarma). */
    private final String legajoDelEmpleado;
    private final LocalDateTime fechaHoraDelEvento;
    /** Código del tipo de evento según el fabricante (en Hikvision, el "minor" del evento de acceso). */
    private final long codigoDeTipoDeEvento;
    private final String modoDeVerificacion;

    public EventoDeReloj(String legajoDelEmpleado, LocalDateTime fechaHoraDelEvento, long codigoDeTipoDeEvento,
                         String modoDeVerificacion) {
        this.legajoDelEmpleado = legajoDelEmpleado == null ? "" : legajoDelEmpleado.trim();
        this.fechaHoraDelEvento = fechaHoraDelEvento;
        this.codigoDeTipoDeEvento = codigoDeTipoDeEvento;
        this.modoDeVerificacion = modoDeVerificacion == null ? "" : modoDeVerificacion;
    }

    public boolean identificoAUnEmpleado() {
        return !legajoDelEmpleado.isBlank();
    }

    public String obtenerLegajoDelEmpleado() { return legajoDelEmpleado; }

    public LocalDateTime obtenerFechaHoraDelEvento() { return fechaHoraDelEvento; }

    public long obtenerCodigoDeTipoDeEvento() { return codigoDeTipoDeEvento; }

    public String obtenerModoDeVerificacion() { return modoDeVerificacion; }
}
