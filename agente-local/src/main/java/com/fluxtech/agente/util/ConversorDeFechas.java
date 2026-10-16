package com.fluxtech.agente.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Conversión de fechas entre el formato de los relojes, el de la nube y el de los archivos locales.
 *
 * Regla de negocio: se usa siempre la "hora de pared" del reloj (la que ve el empleado en la
 * pantalla). Si el reloj manda la zona horaria ("-03:00"), se descarta: no se convierte a otra hora.
 */
public final class ConversorDeFechas {

    /** Formato único para enviar y guardar: siempre con segundos ("2026-10-16T08:00:00"). */
    public static final DateTimeFormatter FORMATO_CON_SEGUNDOS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private ConversorDeFechas() {
        // Clase de utilidades: no se instancia
    }

    /**
     * Interpreta los formatos que mandan los relojes y la nube:
     *   "2026-10-16T08:00:00-03:00", "2026-10-16T08:00:00+0300", "2026-10-16T08:00:00Z",
     *   "2026-10-16T08:00:00", "2026-10-16 08:00:00" y "2026-10-16T08:00".
     * Devuelve null si el texto no es una fecha válida.
     */
    public static LocalDateTime interpretarFechaDelReloj(String textoDeFecha) {
        if (textoDeFecha == null || textoDeFecha.isBlank()) {
            return null;
        }
        // Paso 1: unificar el separador entre fecha y hora
        String textoNormalizado = textoDeFecha.trim().replace(' ', 'T');
        // Paso 2: algunos firmwares mandan "+0300" en lugar de "+03:00"
        if (textoNormalizado.matches(".*[+-]\\d{4}$")) {
            int largo = textoNormalizado.length();
            textoNormalizado = textoNormalizado.substring(0, largo - 2) + ":" + textoNormalizado.substring(largo - 2);
        }
        try {
            // Paso 3a: con zona horaria → se conserva la hora de pared tal cual
            return OffsetDateTime.parse(textoNormalizado).toLocalDateTime().withNano(0);
        } catch (DateTimeParseException sinZonaHoraria) {
            try {
                // Paso 3b: sin zona horaria
                return LocalDateTime.parse(textoNormalizado).withNano(0);
            } catch (DateTimeParseException formatoDesconocido) {
                return null;
            }
        }
    }

    /** Fecha en el formato único con segundos. */
    public static String formatearConSegundos(LocalDateTime fechaHora) {
        return FORMATO_CON_SEGUNDOS.format(fechaHora);
    }
}
