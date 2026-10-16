package com.atomg.accessmanager.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generación y verificación de claves de agente.
 * Formato: fxa_<prefijo 10>_<secreto 43>  (el secreto son 32 bytes aleatorios en base64url).
 * Al ser claves aleatorias largas alcanza con SHA-256 (no son contraseñas elegidas por personas).
 */
public final class AgenteClaves {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALFABETO_PREFIJO = "abcdefghijkmnpqrstuvwxyz23456789";

    private AgenteClaves() {}

    public record ClaveNueva(String clave, String prefijo, String hash) {}

    public static ClaveNueva generar() {
        StringBuilder prefijo = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            prefijo.append(ALFABETO_PREFIJO.charAt(RANDOM.nextInt(ALFABETO_PREFIJO.length())));
        }
        byte[] secreto = new byte[32];
        RANDOM.nextBytes(secreto);
        String clave = "fxa_" + prefijo + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secreto);
        return new ClaveNueva(clave, prefijo.toString(), hash(clave));
    }

    /** Devuelve el prefijo de una clave con formato válido, o null. */
    public static String extraerPrefijo(String clave) {
        if (clave == null) return null;
        String c = clave.trim();
        if (!c.startsWith("fxa_") || c.length() < 20) return null;
        int fin = c.indexOf('_', 4);
        if (fin < 0) return null;
        return c.substring(4, fin);
    }

    public static String hash(String clave) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(clave.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Comparación en tiempo constante contra el hash guardado. */
    public static boolean coincide(String clave, String hashGuardado) {
        if (clave == null || hashGuardado == null) return false;
        return MessageDigest.isEqual(
                hash(clave).getBytes(StandardCharsets.US_ASCII),
                hashGuardado.getBytes(StandardCharsets.US_ASCII));
    }
}
