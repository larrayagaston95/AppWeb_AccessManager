package com.fluxtech.agente.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Convierte texto JSON en objetos de Java.
 * Se escribió a mano (sin bibliotecas) para que la lectura de JSON no agregue dependencias.
 *
 * Recorrido del dato:
 *   El servicio de la nube o el lector del reloj recibe un texto JSON y llama a convertirEnObjeto().
 *   Esta clase recorre el texto carácter por carácter y devuelve:
 *     objetos → Map (en el mismo orden), arreglos → List, números → Long o Double,
 *     textos → String, true/false → Boolean, null → null.
 *   Si el texto no es JSON válido lanza IllegalArgumentException indicando la posición del problema.
 */
public final class LectorJson {

    private final String textoCompleto;
    private int posicionActual;

    private LectorJson(String textoCompleto) {
        this.textoCompleto = textoCompleto;
    }

    /** Convierte un texto JSON en el objeto Java equivalente. */
    public static Object convertirEnObjeto(String textoJson) {
        if (textoJson == null) {
            throw new IllegalArgumentException("El texto JSON está vacío");
        }
        LectorJson lector = new LectorJson(textoJson);
        lector.saltarEspacios();
        Object valor = lector.leerValor();
        lector.saltarEspacios();
        // Si quedó texto después del valor, el JSON está mal formado
        if (lector.posicionActual != lector.textoCompleto.length()) {
            throw lector.errorDeFormato("hay contenido extra al final");
        }
        return valor;
    }

    /** Igual que convertirEnObjeto(), pero exige que el JSON sea un objeto {...}. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> convertirEnMapa(String textoJson) {
        Object valor = convertirEnObjeto(textoJson);
        if (!(valor instanceof Map)) {
            throw new IllegalArgumentException("Se esperaba un objeto JSON {...}");
        }
        return (Map<String, Object>) valor;
    }

    /** Lee el valor que empieza en la posición actual, según su primer carácter. */
    private Object leerValor() {
        if (posicionActual >= textoCompleto.length()) {
            throw errorDeFormato("el texto terminó antes de tiempo");
        }
        char primerCaracter = textoCompleto.charAt(posicionActual);
        if (primerCaracter == '{') return leerObjeto();
        if (primerCaracter == '[') return leerArreglo();
        if (primerCaracter == '"') return leerTexto();
        if (primerCaracter == 't') { consumirPalabra("true"); return Boolean.TRUE; }
        if (primerCaracter == 'f') { consumirPalabra("false"); return Boolean.FALSE; }
        if (primerCaracter == 'n') { consumirPalabra("null"); return null; }
        if (primerCaracter == '-' || Character.isDigit(primerCaracter)) return leerNumero();
        throw errorDeFormato("carácter inesperado '" + primerCaracter + "'");
    }

    /** Lee {"clave": valor, ...} respetando el orden original de las claves. */
    private Map<String, Object> leerObjeto() {
        Map<String, Object> objeto = new LinkedHashMap<>();
        posicionActual++; // se consume la llave de apertura
        saltarEspacios();
        if (consumirCaracterSiEs('}')) return objeto;
        while (true) {
            saltarEspacios();
            if (posicionActual >= textoCompleto.length() || textoCompleto.charAt(posicionActual) != '"') {
                throw errorDeFormato("se esperaba el nombre de una clave entre comillas");
            }
            String clave = leerTexto();
            saltarEspacios();
            if (!consumirCaracterSiEs(':')) throw errorDeFormato("se esperaba ':' después de la clave");
            saltarEspacios();
            objeto.put(clave, leerValor());
            saltarEspacios();
            if (consumirCaracterSiEs('}')) return objeto;
            if (!consumirCaracterSiEs(',')) throw errorDeFormato("se esperaba ',' o '}'");
        }
    }

    /** Lee [valor, valor, ...]. */
    private List<Object> leerArreglo() {
        List<Object> arreglo = new ArrayList<>();
        posicionActual++; // se consume el corchete de apertura
        saltarEspacios();
        if (consumirCaracterSiEs(']')) return arreglo;
        while (true) {
            saltarEspacios();
            arreglo.add(leerValor());
            saltarEspacios();
            if (consumirCaracterSiEs(']')) return arreglo;
            if (!consumirCaracterSiEs(',')) throw errorDeFormato("se esperaba ',' o ']'");
        }
    }

    /** Lee un texto entre comillas interpretando las secuencias de escape (\n, \", á, etc.). */
    private String leerTexto() {
        posicionActual++; // se consume la comilla de apertura
        StringBuilder texto = new StringBuilder();
        while (posicionActual < textoCompleto.length()) {
            char caracter = textoCompleto.charAt(posicionActual++);
            if (caracter == '"') return texto.toString();
            if (caracter != '\\') { texto.append(caracter); continue; }
            if (posicionActual >= textoCompleto.length()) break;
            char escape = textoCompleto.charAt(posicionActual++);
            switch (escape) {
                case '"': texto.append('"'); break;
                case '\\': texto.append('\\'); break;
                case '/': texto.append('/'); break;
                case 'b': texto.append('\b'); break;
                case 'f': texto.append('\f'); break;
                case 'n': texto.append('\n'); break;
                case 'r': texto.append('\r'); break;
                case 't': texto.append('\t'); break;
                case 'u':
                    if (posicionActual + 4 > textoCompleto.length()) throw errorDeFormato("escape \\u incompleto");
                    texto.append((char) Integer.parseInt(textoCompleto.substring(posicionActual, posicionActual + 4), 16));
                    posicionActual += 4;
                    break;
                default: throw errorDeFormato("secuencia de escape inválida");
            }
        }
        throw errorDeFormato("un texto quedó sin la comilla de cierre");
    }

    /** Lee un número: entero como Long, con decimales o exponente como Double. */
    private Object leerNumero() {
        int inicio = posicionActual;
        if (textoCompleto.charAt(posicionActual) == '-') posicionActual++;
        while (posicionActual < textoCompleto.length() && "0123456789+-.eE".indexOf(textoCompleto.charAt(posicionActual)) >= 0) {
            posicionActual++;
        }
        String numeroComoTexto = textoCompleto.substring(inicio, posicionActual);
        try {
            boolean tieneDecimales = numeroComoTexto.contains(".") || numeroComoTexto.contains("e") || numeroComoTexto.contains("E");
            return tieneDecimales ? (Object) Double.parseDouble(numeroComoTexto) : (Object) Long.parseLong(numeroComoTexto);
        } catch (NumberFormatException excepcion) {
            throw errorDeFormato("número inválido '" + numeroComoTexto + "'");
        }
    }

    private void consumirPalabra(String palabraEsperada) {
        if (!textoCompleto.startsWith(palabraEsperada, posicionActual)) throw errorDeFormato("se esperaba " + palabraEsperada);
        posicionActual += palabraEsperada.length();
    }

    private boolean consumirCaracterSiEs(char caracterEsperado) {
        if (posicionActual < textoCompleto.length() && textoCompleto.charAt(posicionActual) == caracterEsperado) {
            posicionActual++;
            return true;
        }
        return false;
    }

    private void saltarEspacios() {
        while (posicionActual < textoCompleto.length() && Character.isWhitespace(textoCompleto.charAt(posicionActual))) {
            posicionActual++;
        }
    }

    private IllegalArgumentException errorDeFormato(String descripcion) {
        return new IllegalArgumentException("JSON inválido (posición " + posicionActual + "): " + descripcion);
    }
}
