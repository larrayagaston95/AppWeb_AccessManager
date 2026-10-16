package com.fluxtech.agente.configuracion;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Reglas que tiene que cumplir agente.properties antes de que el agente arranque.
 *
 * Regla de seguridad principal: la clave del agente nunca puede viajar sin cifrar por internet.
 * Por eso se exige https://, salvo para direcciones de la red local (pruebas en la oficina).
 */
final class ValidadorDeConfiguracion {

    private ValidadorDeConfiguracion() {
        // Clase de utilidades: no se instancia
    }

    /** Lanza IOException con un mensaje para quien instala si falta algo o es inseguro. */
    static void validarObligatorios(String direccionDelServidor, String claveDelAgente) throws IOException {
        // Regla 1: la dirección del servidor es obligatoria
        if (direccionDelServidor.isEmpty()) {
            throw new IOException("Falta servidor.url en agente.properties");
        }
        // Regla 2: la clave tiene el formato que genera el panel (empieza con fxa_)
        if (!claveDelAgente.startsWith("fxa_")) {
            throw new IOException("agente.clave falta o no tiene el formato correcto (empieza con fxa_). "
                    + "Se genera en el panel de FluxTech, sección Agentes locales.");
        }
        // Regla 3: la dirección tiene que ser una URL válida
        URI direccion;
        try {
            direccion = new URI(direccionDelServidor);
        } catch (URISyntaxException excepcion) {
            throw new IOException("servidor.url no es una dirección válida: " + direccionDelServidor);
        }
        String protocolo = direccion.getScheme() == null ? "" : direccion.getScheme().toLowerCase(Locale.ROOT);
        String servidor = direccion.getHost() == null ? "" : direccion.getHost().toLowerCase(Locale.ROOT);
        // Regla 4: solo http o https
        if (!protocolo.equals("http") && !protocolo.equals("https")) {
            throw new IOException("servidor.url debe empezar con https://");
        }
        // Regla 5: http sin cifrar solo hacia la red local
        if (protocolo.equals("http") && !esDireccionDeRedLocal(servidor)) {
            throw new IOException("servidor.url debe usar https:// (solo se permite http:// para pruebas en la red local)");
        }
    }

    /** true para localhost y los rangos de IP privados (10.x, 172.16-31.x, 192.168.x). */
    static boolean esDireccionDeRedLocal(String servidor) {
        return servidor.equals("localhost")
                || servidor.startsWith("127.")
                || servidor.startsWith("10.")
                || servidor.startsWith("192.168.")
                || servidor.matches("172\\.(1[6-9]|2\\d|3[01])\\..*");
    }
}
