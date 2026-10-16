package com.fluxtech.agente.nube;

import java.io.IOException;

/**
 * La nube respondió 401/403: la clave del agente fue revocada o se copió mal.
 * Reintentar no sirve hasta que alguien cargue una clave nueva en agente.properties.
 */
public final class ClaveDelAgenteRechazadaException extends IOException {

    private static final long serialVersionUID = 1L;

    public ClaveDelAgenteRechazadaException() {
        super("La nube rechazó la clave del agente (revocada o mal copiada). "
                + "Generá una nueva en el panel y actualizá agente.properties.");
    }
}
