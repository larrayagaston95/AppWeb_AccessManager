package com.fluxtech.agente.json;

import java.util.Map;

/**
 * Convierte objetos de Java en texto JSON (Map → objeto, List → arreglo, String → texto, etc.).
 *
 * Recorrido del dato:
 *   El servicio de la nube arma un Map con las fichadas a subir y llama a convertirEnTexto().
 *   Esta clase devuelve el texto JSON que viaja en el cuerpo del pedido HTTPS.
 *   La cola de pendientes también la usa para guardar las fichadas en disco.
 */
public final class EscritorJson {

    private EscritorJson() {
        // Clase de utilidades: no se instancia
    }

    /** Devuelve el texto JSON equivalente al objeto recibido. */
    public static String convertirEnTexto(Object valor) {
        StringBuilder resultado = new StringBuilder();
        escribirValor(valor, resultado);
        return resultado.toString();
    }

    /** Escribe un valor según su tipo. Los tipos no reconocidos se escriben como texto. */
    private static void escribirValor(Object valor, StringBuilder resultado) {
        if (valor == null) {
            resultado.append("null");
        } else if (valor instanceof String) {
            escribirTexto((String) valor, resultado);
        } else if (valor instanceof Number || valor instanceof Boolean) {
            resultado.append(valor);
        } else if (valor instanceof Map) {
            escribirObjeto((Map<?, ?>) valor, resultado);
        } else if (valor instanceof Iterable) {
            escribirArreglo((Iterable<?>) valor, resultado);
        } else {
            escribirTexto(valor.toString(), resultado);
        }
    }

    /** {"clave":valor,...} */
    private static void escribirObjeto(Map<?, ?> objeto, StringBuilder resultado) {
        resultado.append('{');
        boolean esElPrimero = true;
        for (Map.Entry<?, ?> entrada : objeto.entrySet()) {
            if (!esElPrimero) resultado.append(',');
            esElPrimero = false;
            escribirTexto(String.valueOf(entrada.getKey()), resultado);
            resultado.append(':');
            escribirValor(entrada.getValue(), resultado);
        }
        resultado.append('}');
    }

    /** [valor,valor,...] */
    private static void escribirArreglo(Iterable<?> arreglo, StringBuilder resultado) {
        resultado.append('[');
        boolean esElPrimero = true;
        for (Object elemento : arreglo) {
            if (!esElPrimero) resultado.append(',');
            esElPrimero = false;
            escribirValor(elemento, resultado);
        }
        resultado.append(']');
    }

    /** Texto entre comillas, escapando comillas, barras y caracteres de control. */
    private static void escribirTexto(String texto, StringBuilder resultado) {
        resultado.append('"');
        for (int indice = 0; indice < texto.length(); indice++) {
            char caracter = texto.charAt(indice);
            switch (caracter) {
                case '"': resultado.append("\\\""); break;
                case '\\': resultado.append("\\\\"); break;
                case '\n': resultado.append("\\n"); break;
                case '\r': resultado.append("\\r"); break;
                case '\t': resultado.append("\\t"); break;
                default:
                    if (caracter < 0x20) {
                        resultado.append(String.format("\\u%04x", (int) caracter));
                    } else {
                        resultado.append(caracter);
                    }
            }
        }
        resultado.append('"');
    }
}
