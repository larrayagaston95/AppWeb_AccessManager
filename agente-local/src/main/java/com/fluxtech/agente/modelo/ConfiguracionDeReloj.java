package com.fluxtech.agente.modelo;

import com.fluxtech.agente.json.AyudaMapasJson;
import com.fluxtech.agente.util.ConversorDeFechas;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * Datos de un reloj tal como están cargados en el panel de FluxTech.
 *
 * Recorrido del dato:
 *   La nube los envía en GET /api/agente/config (lista "relojes").
 *   El actualizador de configuración crea una ConfiguracionDeReloj por cada uno con crearDesdeMapaDeLaNube().
 *   El lector de relojes la usa para saber dónde conectarse y desde cuándo leer.
 */
public final class ConfiguracionDeReloj {

    private final long idDelReloj;
    private final String nombreVisible;
    private final String numeroDeSerieEnElPanel;
    private final String marca;
    /** IP en la red local, con puerto opcional ("192.168.1.50" o "192.168.1.50:80"). */
    private final String direccionEnLaRedLocal;
    private final String usuarioDelReloj;
    private final String contraseniaDelReloj;
    /** Última fichada de este reloj que la nube ya tiene guardada (null si nunca recibió ninguna). */
    private final LocalDateTime ultimaFichadaGuardadaEnLaNube;

    private ConfiguracionDeReloj(long idDelReloj, String nombreVisible, String numeroDeSerieEnElPanel, String marca,
                                 String direccionEnLaRedLocal, String usuarioDelReloj, String contraseniaDelReloj,
                                 LocalDateTime ultimaFichadaGuardadaEnLaNube) {
        this.idDelReloj = idDelReloj;
        this.nombreVisible = nombreVisible;
        this.numeroDeSerieEnElPanel = numeroDeSerieEnElPanel;
        this.marca = marca;
        this.direccionEnLaRedLocal = direccionEnLaRedLocal;
        this.usuarioDelReloj = usuarioDelReloj;
        this.contraseniaDelReloj = contraseniaDelReloj;
        this.ultimaFichadaGuardadaEnLaNube = ultimaFichadaGuardadaEnLaNube;
    }

    /** Crea la configuración a partir de un elemento de la lista "relojes" que manda la nube. */
    public static ConfiguracionDeReloj crearDesdeMapaDeLaNube(Map<String, Object> mapa) {
        return new ConfiguracionDeReloj(
                AyudaMapasJson.obtenerNumero(mapa, "id", -1),
                AyudaMapasJson.obtenerTexto(mapa, "nombre"),
                AyudaMapasJson.obtenerTexto(mapa, "numeroSerie"),
                AyudaMapasJson.obtenerTexto(mapa, "marca"),
                AyudaMapasJson.obtenerTexto(mapa, "ip"),
                AyudaMapasJson.obtenerTexto(mapa, "usuario"),
                AyudaMapasJson.obtenerTexto(mapa, "password"),
                ConversorDeFechas.interpretarFechaDelReloj(AyudaMapasJson.obtenerTexto(mapa, "ultimaFichada")));
    }

    /** Por ahora el agente lee solo relojes Hikvision (por ISAPI o por el SDK oficial). */
    public boolean esMarcaHikvision() {
        return marca != null && marca.toLowerCase(Locale.ROOT).contains("hik");
    }

    /** Sin IP, usuario o contraseña no hay forma de conectarse al reloj. */
    public boolean tieneDatosDeConexionCompletos() {
        return !direccionEnLaRedLocal.isBlank() && !usuarioDelReloj.isBlank() && !contraseniaDelReloj.isBlank();
    }

    /** IP sin "http://" ni puerto: se usa para reconocer los eventos que el reloj envía al receptor. */
    public String obtenerIpSinPuerto() {
        String sinProtocolo = direccionEnLaRedLocal.trim().replaceFirst("^https?://", "");
        int posicionDeDosPuntos = sinProtocolo.indexOf(':');
        return posicionDeDosPuntos >= 0 ? sinProtocolo.substring(0, posicionDeDosPuntos) : sinProtocolo;
    }

    /** Nombre para mostrar en el registro: el del panel, o el número de serie si no tiene nombre. */
    public String obtenerNombreParaMostrar() {
        return nombreVisible.isBlank() ? numeroDeSerieEnElPanel : nombreVisible;
    }

    public long obtenerIdDelReloj() { return idDelReloj; }

    public String obtenerNumeroDeSerieEnElPanel() { return numeroDeSerieEnElPanel; }

    public String obtenerMarca() { return marca; }

    public String obtenerDireccionEnLaRedLocal() { return direccionEnLaRedLocal; }

    public String obtenerUsuarioDelReloj() { return usuarioDelReloj; }

    public String obtenerContraseniaDelReloj() { return contraseniaDelReloj; }

    public LocalDateTime obtenerUltimaFichadaGuardadaEnLaNube() { return ultimaFichadaGuardadaEnLaNube; }
}
