package com.fluxtech.agente.configuracion;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Configuración local del agente: archivo agente.properties ubicado junto al .jar.
 *
 * Recorrido del dato:
 *   Main llama a cargarDesdeCarpeta() al arrancar.
 *   Esta clase lee el archivo, completa los valores por defecto y valida lo obligatorio
 *   (dirección del servidor y clave del agente) con ValidadorDeConfiguracion.
 *   El resto del agente consulta los valores ya validados con los métodos obtener...().
 *   Los relojes NO se configuran acá: se cargan en el panel web y el agente los descarga.
 */
public final class ConfiguracionLocal {

    /** Cómo leer los relojes Hikvision. */
    public enum ModoDeLecturaHikvision {
        /** Prueba ISAPI (red, sin instalar nada) y, si el reloj no lo permite, usa el SDK oficial. */
        AUTOMATICO,
        /** Solo ISAPI. */
        ISAPI,
        /** Solo el SDK oficial de Hikvision (HCNetSDK, requiere sus archivos en la carpeta del SDK). */
        SDK
    }

    private final Path carpetaDelAgente;
    private final String direccionDelServidor;
    private final String claveDelAgente;
    private final boolean receptorHabilitado;
    private final int puertoDelReceptor;
    private final boolean lecturaDeMemoriaHabilitada;
    private final int diasALeerLaPrimeraVez;
    private final int segundosEntreCiclosForzados;
    private final boolean registrarDetalleDeEventos;
    private final ModoDeLecturaHikvision modoDeLecturaHikvision;
    private final Path carpetaDelSdkHikvision;
    private final int puertoDelSdkHikvision;

    private ConfiguracionLocal(Path carpetaDelAgente, Properties propiedades) {
        this.carpetaDelAgente = carpetaDelAgente;
        this.direccionDelServidor = quitarBarrasFinales(leerTexto(propiedades, "servidor.url", ""));
        this.claveDelAgente = leerTexto(propiedades, "agente.clave", "");
        this.receptorHabilitado = Boolean.parseBoolean(leerTexto(propiedades, "receptor.habilitado", "true"));
        this.puertoDelReceptor = leerEntero(propiedades, "receptor.puerto", 8095);
        this.lecturaDeMemoriaHabilitada = Boolean.parseBoolean(leerTexto(propiedades, "lectura.habilitada", "true"));
        this.diasALeerLaPrimeraVez = leerEntero(propiedades, "lectura.dias-iniciales", 31);
        this.segundosEntreCiclosForzados = leerEntero(propiedades, "intervalo.segundos", 0);
        this.registrarDetalleDeEventos = Boolean.parseBoolean(leerTexto(propiedades, "log.detalle", "false"));
        this.modoDeLecturaHikvision = leerModo(leerTexto(propiedades, "hikvision.modo", "automatico"));
        this.carpetaDelSdkHikvision = carpetaDelAgente.resolve(leerTexto(propiedades, "hikvision.sdk.carpeta", "sdk-hikvision"));
        this.puertoDelSdkHikvision = leerEntero(propiedades, "hikvision.sdk.puerto", 8000);
    }

    /** Lee y valida agente.properties. Lanza IOException con un mensaje claro si falta algo obligatorio. */
    public static ConfiguracionLocal cargarDesdeCarpeta(Path carpetaDelAgente) throws IOException {
        Path archivoDeConfiguracion = carpetaDelAgente.resolve("agente.properties");
        if (!Files.exists(archivoDeConfiguracion)) {
            throw new IOException("No se encontró " + archivoDeConfiguracion
                    + ". Copiá agente.properties.ejemplo como agente.properties y completalo.");
        }
        Properties propiedades = new Properties();
        try (InputStream entrada = Files.newInputStream(archivoDeConfiguracion)) {
            propiedades.load(new InputStreamReader(entrada, StandardCharsets.UTF_8));
        }
        ConfiguracionLocal configuracion = new ConfiguracionLocal(carpetaDelAgente, propiedades);
        ValidadorDeConfiguracion.validarObligatorios(configuracion.direccionDelServidor, configuracion.claveDelAgente);
        return configuracion;
    }

    public Path obtenerCarpetaDeDatos() { return carpetaDelAgente.resolve("datos"); }

    public String obtenerDireccionDelServidor() { return direccionDelServidor; }

    public String obtenerClaveDelAgente() { return claveDelAgente; }

    public boolean estaHabilitadoElReceptor() { return receptorHabilitado; }

    public int obtenerPuertoDelReceptor() { return puertoDelReceptor; }

    public boolean estaHabilitadaLaLecturaDeMemoria() { return lecturaDeMemoriaHabilitada; }

    public int obtenerDiasALeerLaPrimeraVez() { return diasALeerLaPrimeraVez; }

    /** 0 = usar el intervalo que indica la nube. */
    public int obtenerSegundosEntreCiclosForzados() { return segundosEntreCiclosForzados; }

    public boolean debeRegistrarDetalleDeEventos() { return registrarDetalleDeEventos; }

    public ModoDeLecturaHikvision obtenerModoDeLecturaHikvision() { return modoDeLecturaHikvision; }

    public Path obtenerCarpetaDelSdkHikvision() { return carpetaDelSdkHikvision; }

    public int obtenerPuertoDelSdkHikvision() { return puertoDelSdkHikvision; }

    private static ModoDeLecturaHikvision leerModo(String textoDelModo) {
        switch (textoDelModo.toLowerCase(Locale.ROOT)) {
            case "isapi": return ModoDeLecturaHikvision.ISAPI;
            case "sdk": return ModoDeLecturaHikvision.SDK;
            default: return ModoDeLecturaHikvision.AUTOMATICO;
        }
    }

    private static String leerTexto(Properties propiedades, String clave, String valorPorDefecto) {
        return propiedades.getProperty(clave, valorPorDefecto).trim();
    }

    private static int leerEntero(Properties propiedades, String clave, int valorPorDefecto) {
        try {
            return Integer.parseInt(leerTexto(propiedades, clave, String.valueOf(valorPorDefecto)));
        } catch (NumberFormatException excepcion) {
            return valorPorDefecto;
        }
    }

    private static String quitarBarrasFinales(String direccion) {
        String resultado = direccion;
        while (resultado.endsWith("/")) {
            resultado = resultado.substring(0, resultado.length() - 1);
        }
        return resultado;
    }
}
