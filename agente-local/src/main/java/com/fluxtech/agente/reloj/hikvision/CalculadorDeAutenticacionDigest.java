package com.fluxtech.agente.reloj.hikvision;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calcula la cabecera "Authorization: Digest ..." que exigen los relojes Hikvision (RFC 2617/7616, MD5).
 * Java no trae este cálculo incorporado, por eso se implementa acá.
 *
 * Recorrido del dato:
 *   El reloj responde 401 con "WWW-Authenticate: Digest realm=..., nonce=..., qop=auth".
 *   ClienteHttpConAutenticacionDigest llama a calcularCabecera() con ese desafío.
 *   Esta clase combina usuario, contraseña, realm, nonce, método y ruta en un hash MD5
 *   y devuelve la cabecera que se reenvía. La contraseña nunca viaja en texto plano.
 */
final class CalculadorDeAutenticacionDigest {

    private static final SecureRandom GENERADOR_ALEATORIO = new SecureRandom();
    private static final Pattern PATRON_DE_PARAMETRO = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^,\\s]+))");

    private CalculadorDeAutenticacionDigest() {
        // Clase de utilidades: no se instancia
    }

    /** Devuelve el valor completo de la cabecera Authorization para responder al desafío. */
    static String calcularCabecera(String desafioDelReloj, String usuario, String contrasenia, String metodoHttp, String ruta) {
        // Paso 1: separar los parámetros del desafío (realm, nonce, qop, opaque)
        Map<String, String> parametrosDelDesafio = new HashMap<>();
        Matcher buscador = PATRON_DE_PARAMETRO.matcher(desafioDelReloj.substring("Digest".length()));
        while (buscador.find()) {
            String valor = buscador.group(2) != null ? buscador.group(2) : buscador.group(3);
            parametrosDelDesafio.put(buscador.group(1).toLowerCase(), valor);
        }
        String realm = parametrosDelDesafio.getOrDefault("realm", "");
        String nonce = parametrosDelDesafio.getOrDefault("nonce", "");
        String opaque = parametrosDelDesafio.get("opaque");
        boolean usaCalidadDeProteccionAuth = ofreceCalidadDeProteccionAuth(parametrosDelDesafio.getOrDefault("qop", ""));

        // Paso 2: hashes intermedios definidos por el estándar
        String hashDeCredenciales = calcularMd5(usuario + ":" + realm + ":" + contrasenia);
        String hashDelPedido = calcularMd5(metodoHttp + ":" + ruta);
        String contadorDeUso = "00000001";
        String nonceDelCliente = generarNonceDelCliente();

        // Paso 3: respuesta final (con o sin qop según lo que pidió el reloj)
        String respuestaCalculada = usaCalidadDeProteccionAuth
                ? calcularMd5(hashDeCredenciales + ":" + nonce + ":" + contadorDeUso + ":" + nonceDelCliente + ":auth:" + hashDelPedido)
                : calcularMd5(hashDeCredenciales + ":" + nonce + ":" + hashDelPedido);

        // Paso 4: armar la cabecera
        StringBuilder cabecera = new StringBuilder("Digest ");
        cabecera.append("username=\"").append(usuario).append("\", ");
        cabecera.append("realm=\"").append(realm).append("\", ");
        cabecera.append("nonce=\"").append(nonce).append("\", ");
        cabecera.append("uri=\"").append(ruta).append("\", ");
        cabecera.append("algorithm=MD5, ");
        cabecera.append("response=\"").append(respuestaCalculada).append("\"");
        if (usaCalidadDeProteccionAuth) {
            cabecera.append(", qop=auth, nc=").append(contadorDeUso).append(", cnonce=\"").append(nonceDelCliente).append("\"");
        }
        if (opaque != null) {
            cabecera.append(", opaque=\"").append(opaque).append("\"");
        }
        return cabecera.toString();
    }

    /** El reloj puede ofrecer varias opciones separadas por coma ("auth,auth-int"); se usa "auth". */
    private static boolean ofreceCalidadDeProteccionAuth(String opcionesOfrecidas) {
        for (String opcion : opcionesOfrecidas.split(",")) {
            if (opcion.trim().equalsIgnoreCase("auth")) {
                return true;
            }
        }
        return false;
    }

    private static String generarNonceDelCliente() {
        byte[] bytesAleatorios = new byte[8];
        GENERADOR_ALEATORIO.nextBytes(bytesAleatorios);
        return convertirAHexadecimal(bytesAleatorios);
    }

    /** MD5 en hexadecimal (público dentro del paquete para poder verificarlo en las pruebas). */
    static String calcularMd5(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(texto.getBytes(StandardCharsets.UTF_8));
            return convertirAHexadecimal(hash);
        } catch (NoSuchAlgorithmException excepcion) {
            throw new IllegalStateException("MD5 no está disponible en esta instalación de Java", excepcion);
        }
    }

    private static String convertirAHexadecimal(byte[] bytes) {
        StringBuilder hexadecimal = new StringBuilder();
        for (byte valor : bytes) {
            hexadecimal.append(String.format("%02x", valor));
        }
        return hexadecimal.toString();
    }
}
