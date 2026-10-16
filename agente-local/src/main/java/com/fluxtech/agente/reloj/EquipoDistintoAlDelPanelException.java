package com.fluxtech.agente.reloj;

import java.io.IOException;

/**
 * En la IP cargada en el panel responde un equipo con otro número de serie.
 * Leerlo cargaría fichadas en el reloj equivocado, por eso es un error definitivo:
 * probar otro método de lectura no lo soluciona; hay que corregir la IP en el panel.
 */
public final class EquipoDistintoAlDelPanelException extends IOException {

    private static final long serialVersionUID = 1L;

    public EquipoDistintoAlDelPanelException(String direccion, String numeroDeSerieDetectado, String numeroDeSerieEsperado) {
        super("en " + direccion + " hay otro equipo (serie " + numeroDeSerieDetectado + ", esperado " + numeroDeSerieEsperado + ")");
    }
}
