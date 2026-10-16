package com.atomg.accessmanager.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generación y verificación de las claves de los agentes locales.
 *
 * Formato de la clave: fxa_<prefijo de 10 caracteres>_<secreto de 43 caracteres>
 *   - El prefijo se guarda tal cual: sirve para encontrar al agente en la base.
 *   - De la clave completa se guarda solo su hash SHA-256: si alguien roba la base, no obtiene claves.
 * SHA-256 alcanza porque la clave es aleatoria y larga (32 bytes); no es una contraseña elegida por una persona.
 */
public final class AgenteClaves {

    private static final SecureRandom GENERADOR_ALEATORIO = new SecureRandom();
    /** Sin letras ni números que se confundan al leerlos (l, o, 0, 1). */
    private static final String CARACTERES_DEL_PREFIJO = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final int LARGO_DEL_PREFIJO = 10;
    private static final int BYTES_DEL_SECRETO = 32;

    private AgenteClaves() {
        // Clase de utilidades: no se instancia
    }

    /** Clave recién generada: la completa (se muestra una vez), su prefijo y su hash (se guardan). */
    public record ClaveNueva(String clave, String prefijo, String hash) {
    }

    public static ClaveNueva generar() {
        StringBuilder prefijo = new StringBuilder();
        for (int posicion = 0; posicion < LARGO_DEL_PREFIJO; posicion++) {
            prefijo.append(CARACTERES_DEL_PREFIJO.charAt(GENERADOR_ALEATORIO.nextInt(CARACTERES_DEL_PREFIJO.length())));
        }
        byte[] secreto = new byte[BYTES_DEL_SECRETO];
        GENERADOR_ALEATORIO.nextBytes(secreto);
        String claveCompleta = "fxa_" + prefijo + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secreto);
        return new ClaveNueva(claveCompleta, prefijo.toString(), calcularHash(claveCompleta));
    }

    /** Prefijo de una clave con formato válido, o null si el texto no tiene el formato fxa_..._... */
    public static String extraerPrefijo(String claveRecibida) {
        if (claveRecibida == null) return null;
        String clave = claveRecibida.trim();
        if (!clave.startsWith("fxa_") || clave.length() < 20) return null;
        int posicionDelSegundoGuion = clave.indexOf('_', 4);
        return posicionDelSegundoGuion < 0 ? null : clave.substring(4, posicionDelSegundoGuion);
    }

    public static String calcularHash(String clave) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(clave.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException excepcion) {
            throw new IllegalStateException("SHA-256 no está disponible en esta instalación de Java", excepcion);
        }
    }

    /** Compara contra el hash guardado en tiempo constante (no revela por timing cuántos caracteres coinciden). */
    public static boolean coincide(String claveRecibida, String hashGuardado) {
        if (claveRecibida == null || hashGuardado == null) return false;
        return MessageDigest.isEqual(
                calcularHash(claveRecibida).getBytes(StandardCharsets.US_ASCII),
                hashGuardado.getBytes(StandardCharsets.US_ASCII));
    }
}
