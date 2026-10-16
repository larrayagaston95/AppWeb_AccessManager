package com.fluxtech.agente;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuración local del agente (archivo agente.properties junto al .jar).
 * Lo único obligatorio es la dirección del servidor y la clave del agente; los relojes
 * se configuran en el panel web y el agente los descarga.
 */
public final class Config {

    public final Path carpeta;
    public final String servidorUrl;
    public final String clave;
    public final boolean receptorHabilitado;
    public final int receptorPuerto;
    public final boolean lecturaHabilitada;
    public final int diasIniciales;
    /** 0 = usar el intervalo que indica el servidor. */
    public final int intervaloSegundos;
    public final boolean logDetalle;

    private Config(Path carpeta, Properties p) {
        this.carpeta = carpeta;
        this.servidorUrl = quitarBarraFinal(p.getProperty("servidor.url", "").trim());
        this.clave = p.getProperty("agente.clave", "").trim();
        this.receptorHabilitado = Boolean.parseBoolean(p.getProperty("receptor.habilitado", "true").trim());
        this.receptorPuerto = entero(p, "receptor.puerto", 8095);
        this.lecturaHabilitada = Boolean.parseBoolean(p.getProperty("lectura.habilitada", "true").trim());
        this.diasIniciales = entero(p, "lectura.dias-iniciales", 31);
        this.intervaloSegundos = entero(p, "intervalo.segundos", 0);
        this.logDetalle = Boolean.parseBoolean(p.getProperty("log.detalle", "false").trim());
    }

    public static Config cargar(Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("agente.properties");
        if (!Files.exists(archivo)) {
            throw new IOException("No se encontró " + archivo
                    + ". Copiá agente.properties.ejemplo como agente.properties y completalo.");
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(archivo)) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        Config c = new Config(carpeta, p);
        c.validar();
        return c;
    }

    public Path datos() {
        return carpeta.resolve("datos");
    }

    private void validar() throws IOException {
        if (servidorUrl.isEmpty()) throw new IOException("Falta servidor.url en agente.properties");
        if (!clave.startsWith("fxa_")) {
            throw new IOException("agente.clave falta o no tiene el formato correcto (empieza con fxa_). "
                    + "Se genera en el panel de FluxTech, sección Agentes locales.");
        }
        URI uri;
        try {
            uri = new URI(servidorUrl);
        } catch (URISyntaxException e) {
            throw new IOException("servidor.url no es una dirección válida: " + servidorUrl);
        }
        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (esquema.equals("http") && !esLocal(host)) {
            // La clave viajaría sin cifrar por internet
            throw new IOException("servidor.url debe usar https:// (solo se permite http:// para pruebas en la red local)");
        }
        if (!esquema.equals("http") && !esquema.equals("https")) {
            throw new IOException("servidor.url debe empezar con https://");
        }
    }

    static boolean esLocal(String host) {
        return host.equals("localhost") || host.startsWith("127.") || host.startsWith("10.")
                || host.startsWith("192.168.") || host.matches("172\\.(1[6-9]|2\\d|3[01])\\..*");
    }

    private static int entero(Properties p, String clave, int porDefecto) {
        try {
            return Integer.parseInt(p.getProperty(clave, String.valueOf(porDefecto)).trim());
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    private static String quitarBarraFinal(String u) {
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }
}
