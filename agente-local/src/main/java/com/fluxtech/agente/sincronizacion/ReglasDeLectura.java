package com.fluxtech.agente.sincronizacion;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Reglas de negocio de la lectura de relojes: desde cuándo leer y cómo confirmar que el equipo
 * conectado es el correcto.
 */
public final class ReglasDeLectura {

    /** Cada lectura repite este margen hacia atrás por seguridad (fichadas cargadas con retraso en el reloj). */
    static final int MINUTOS_DE_SOLAPAMIENTO = 10;

    private ReglasDeLectura() {
        // Clase de reglas: no se instancia
    }

    /**
     * Desde cuándo leer un reloj:
     *   - Se toma la última fichada confirmada, la más nueva entre la que recuerda el agente y la que
     *     informa la nube (por si se borró la carpeta datos/ o se reinstaló el agente).
     *   - Se le restan 10 minutos de solapamiento (lo repetido se descarta sin costo).
     *   - Si nunca se leyó, se lee la cantidad de días configurada hacia atrás (31 por defecto).
     */
    public static LocalDateTime calcularInicioDeLectura(LocalDateTime ultimaConfirmadaLocalmente,
                                                        LocalDateTime ultimaGuardadaEnLaNube,
                                                        LocalDateTime ahora, int diasALeerLaPrimeraVez) {
        LocalDateTime ultimaConocida = ultimaConfirmadaLocalmente;
        if (ultimaGuardadaEnLaNube != null && (ultimaConocida == null || ultimaGuardadaEnLaNube.isAfter(ultimaConocida))) {
            ultimaConocida = ultimaGuardadaEnLaNube;
        }
        if (ultimaConocida == null) {
            return ahora.minusDays(diasALeerLaPrimeraVez);
        }
        LocalDateTime inicioConSolapamiento = ultimaConocida.minusMinutes(MINUTOS_DE_SOLAPAMIENTO);
        // Si la última fichada quedó "en el futuro" (reloj adelantado), se lee igual el margen reciente
        return inicioConSolapamiento.isAfter(ahora) ? ahora.minusMinutes(MINUTOS_DE_SOLAPAMIENTO) : inicioConSolapamiento;
    }

    /**
     * true si el número de serie del panel corresponde al equipo conectado.
     * En el panel a veces se carga solo una parte del número (el final de la etiqueta), por eso
     * alcanza con que uno contenga al otro. Si alguno de los dos está vacío no se puede verificar y se acepta.
     */
    public static boolean coincideElNumeroDeSerie(String numeroEnElPanel, String numeroInformadoPorElEquipo) {
        if (numeroEnElPanel == null || numeroEnElPanel.isBlank()
                || numeroInformadoPorElEquipo == null || numeroInformadoPorElEquipo.isBlank()) {
            return true;
        }
        String panel = numeroEnElPanel.trim().toUpperCase(Locale.ROOT);
        String equipo = numeroInformadoPorElEquipo.trim().toUpperCase(Locale.ROOT);
        return panel.contains(equipo) || equipo.contains(panel);
    }
}
