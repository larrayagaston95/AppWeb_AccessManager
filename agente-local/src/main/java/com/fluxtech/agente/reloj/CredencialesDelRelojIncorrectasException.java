package com.fluxtech.agente.reloj;

import java.io.IOException;

/**
 * El reloj rechazó el usuario o la contraseña cargados en el panel.
 * No tiene sentido probar otro método de lectura: hay que corregir los datos del reloj en el panel.
 */
public final class CredencialesDelRelojIncorrectasException extends IOException {

    private static final long serialVersionUID = 1L;

    public CredencialesDelRelojIncorrectasException() {
        super("Usuario o contraseña del reloj incorrectos (corregirlos en el panel, pantalla Relojes)");
    }
}
