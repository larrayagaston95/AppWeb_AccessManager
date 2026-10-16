package com.fluxtech.agente;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON mínimo (sin dependencias externas, para que el agente sea un único .jar).
 * Objetos → LinkedHashMap, arreglos → ArrayList, números → Long o Double.
 */
public final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String texto) {
        if (texto == null) throw new IllegalArgumentException("JSON vacío");
        Json p = new Json(texto);
        p.espacios();
        Object v = p.valor();
        p.espacios();
        if (p.i != p.s.length()) throw p.error("Contenido extra al final");
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObjeto(String texto) {
        Object v = parse(texto);
        if (!(v instanceof Map)) throw new IllegalArgumentException("Se esperaba un objeto JSON");
        return (Map<String, Object>) v;
    }

    // ── Lectura ──────────────────────────────────────────────────────────────

    private Object valor() {
        if (i >= s.length()) throw error("Fin inesperado");
        char c = s.charAt(i);
        switch (c) {
            case '{': return objeto();
            case '[': return arreglo();
            case '"': return cadena();
            case 't': literal("true"); return Boolean.TRUE;
            case 'f': literal("false"); return Boolean.FALSE;
            case 'n': literal("null"); return null;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) return numero();
                throw error("Carácter inesperado '" + c + "'");
        }
    }

    private Map<String, Object> objeto() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; // {
        espacios();
        if (consumir('}')) return m;
        while (true) {
            espacios();
            if (i >= s.length() || s.charAt(i) != '"') throw error("Se esperaba una clave");
            String k = cadena();
            espacios();
            if (!consumir(':')) throw error("Se esperaba ':'");
            espacios();
            m.put(k, valor());
            espacios();
            if (consumir('}')) return m;
            if (!consumir(',')) throw error("Se esperaba ',' o '}'");
        }
    }

    private List<Object> arreglo() {
        List<Object> l = new ArrayList<>();
        i++; // [
        espacios();
        if (consumir(']')) return l;
        while (true) {
            espacios();
            l.add(valor());
            espacios();
            if (consumir(']')) return l;
            if (!consumir(',')) throw error("Se esperaba ',' o ']'");
        }
    }

    private String cadena() {
        i++; // "
        StringBuilder sb = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw error("Escape \\u incompleto");
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: throw error("Escape inválido");
                }
            } else {
                sb.append(c);
            }
        }
        throw error("Cadena sin cerrar");
    }

    private Object numero() {
        int ini = i;
        if (s.charAt(i) == '-') i++;
        while (i < s.length() && "0123456789+-.eE".indexOf(s.charAt(i)) >= 0) i++;
        String n = s.substring(ini, i);
        try {
            if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.parseDouble(n);
            return Long.parseLong(n);
        } catch (NumberFormatException e) {
            throw error("Número inválido '" + n + "'");
        }
    }

    private void literal(String palabra) {
        if (!s.startsWith(palabra, i)) throw error("Se esperaba " + palabra);
        i += palabra.length();
    }

    private boolean consumir(char c) {
        if (i < s.length() && s.charAt(i) == c) {
            i++;
            return true;
        }
        return false;
    }

    private void espacios() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException("JSON inválido (posición " + i + "): " + msg);
    }

    // ── Escritura ────────────────────────────────────────────────────────────

    public static String escribir(Object v) {
        StringBuilder sb = new StringBuilder();
        escribir(v, sb);
        return sb.toString();
    }

    private static void escribir(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            escribirCadena((String) v, sb);
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map) {
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!primero) sb.append(',');
                primero = false;
                escribirCadena(String.valueOf(e.getKey()), sb);
                sb.append(':');
                escribir(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof Iterable) {
            sb.append('[');
            boolean primero = true;
            for (Object o : (Iterable<?>) v) {
                if (!primero) sb.append(',');
                primero = false;
                escribir(o, sb);
            }
            sb.append(']');
        } else {
            escribirCadena(v.toString(), sb);
        }
    }

    private static void escribirCadena(String t, StringBuilder sb) {
        sb.append('"');
        for (int k = 0; k < t.length(); k++) {
            char c = t.charAt(k);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    // ── Ayudas para leer mapas ───────────────────────────────────────────────

    public static String texto(Map<String, Object> m, String clave) {
        Object v = m.get(clave);
        return v == null ? "" : v.toString();
    }

    public static long numero(Map<String, Object> m, String clave, long porDefecto) {
        Object v = m.get(clave);
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return v == null ? porDefecto : Long.parseLong(v.toString().trim());
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }
}
