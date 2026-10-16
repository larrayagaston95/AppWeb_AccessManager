package com.fluxtech.agente.modelo;

/**
 * Datos que el reloj informa sobre sí mismo al conectarse.
 * Se usan para confirmar que la IP del panel apunta al equipo correcto (por su número de serie).
 */
public final class InformacionDelEquipo {

    private final String modelo;
    private final String numeroDeSerie;
    private final String versionDeFirmware;

    public InformacionDelEquipo(String modelo, String numeroDeSerie, String versionDeFirmware) {
        this.modelo = modelo == null ? "" : modelo;
        this.numeroDeSerie = numeroDeSerie == null ? "" : numeroDeSerie;
        this.versionDeFirmware = versionDeFirmware == null ? "" : versionDeFirmware;
    }

    public String obtenerModelo() { return modelo; }

    public String obtenerNumeroDeSerie() { return numeroDeSerie; }

    public String obtenerVersionDeFirmware() { return versionDeFirmware; }
}
