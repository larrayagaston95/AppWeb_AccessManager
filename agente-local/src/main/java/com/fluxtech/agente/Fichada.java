package com.fluxtech.agente;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Una fichada leída de un reloj, pendiente de subir a la nube. */
public final class Fichada {

    /** Siempre con segundos ("2026-10-16T08:00:00"), también en horas en punto. */
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public final long relojId;
    public final String legajo;
    /** Hora de pared del reloj, sin zona, con precisión de segundos. */
    public final LocalDateTime fechaHora;
    public final String modo;

    public Fichada(long relojId, String legajo, LocalDateTime fechaHora, String modo) {
        this.relojId = relojId;
        this.legajo = legajo.trim();
        this.fechaHora = fechaHora.withNano(0);
        this.modo = modo == null ? "" : modo;
    }

    /** Clave única: mismo reloj, legajo y segundo = misma fichada. */
    public String clave() {
        return relojId + "|" + legajo + "|" + FMT.format(fechaHora);
    }

    public Map<String, Object> aMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("relojId", relojId);
        m.put("legajo", legajo);
        m.put("fechaHora", FMT.format(fechaHora));
        m.put("modo", modo);
        return m;
    }

    public static Fichada desdeMapa(Map<String, Object> m) {
        LocalDateTime fh = parsearFecha(Json.texto(m, "fechaHora"));
        if (fh == null) return null;
        return new Fichada(Json.numero(m, "relojId", -1), Json.texto(m, "legajo"), fh, Json.texto(m, "modo"));
    }

    /** "2026-10-16T08:00:00-03:00", "2026-10-16T08:00:00" o "2026-10-16 08:00:00" → hora de pared. */
    public static LocalDateTime parsearFecha(String t) {
        if (t == null || t.isBlank()) return null;
        String s = t.trim().replace(' ', 'T');
        // Algunos firmwares mandan "+0300" en vez de "+03:00"
        if (s.matches(".*[+-]\\d{4}$")) s = s.substring(0, s.length() - 2) + ":" + s.substring(s.length() - 2);
        if (s.endsWith("Z")) s = s.substring(0, s.length() - 1);
        try {
            return OffsetDateTime.parse(s).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(s);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }
}
