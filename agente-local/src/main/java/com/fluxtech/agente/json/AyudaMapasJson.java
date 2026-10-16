package com.fluxtech.agente.json;

import java.util.Map;

/**
 * Lectura segura de valores dentro de un Map que vino de un JSON.
 * Evita repetir en cada clase las comprobaciones de null y de tipo.
 */
public final class AyudaMapasJson {

    private AyudaMapasJson() {
        // Clase de utilidades: no se instancia
    }

    /** Devuelve el valor como texto, o "" si no existe. */
    public static String obtenerTexto(Map<String, Object> mapa, String clave) {
        Object valor = mapa.get(clave);
        return valor == null ? "" : valor.toString();
    }

    /** Devuelve el valor como número entero, o el valor por defecto si no existe o no es un número. */
    public static long obtenerNumero(Map<String, Object> mapa, String clave, long valorPorDefecto) {
        Object valor = mapa.get(clave);
        if (valor instanceof Number) {
            return ((Number) valor).longValue();
        }
        if (valor == null) {
            return valorPorDefecto;
        }
        try {
            return Long.parseLong(valor.toString().trim());
        } catch (NumberFormatException excepcion) {
            return valorPorDefecto;
        }
    }
}
