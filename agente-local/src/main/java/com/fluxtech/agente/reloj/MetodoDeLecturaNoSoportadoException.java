package com.fluxtech.agente.reloj;

import java.io.IOException;

/**
 * El reloj responde, pero no permite este método de lectura (por ejemplo, un firmware viejo sin
 * búsqueda de eventos por ISAPI). En modo automático provoca que se pruebe el SDK oficial.
 */
public final class MetodoDeLecturaNoSoportadoException extends IOException {

    private static final long serialVersionUID = 1L;

    public MetodoDeLecturaNoSoportadoException(String descripcion) {
        super(descripcion);
    }
}
