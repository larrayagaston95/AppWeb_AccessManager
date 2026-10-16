package com.fluxtech.agente.pruebas;

import com.fluxtech.agente.cola.ColaDeFichadasPendientes;
import com.fluxtech.agente.configuracion.ConfiguracionLocal;
import com.fluxtech.agente.sincronizacion.CicloDeSincronizacion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Estado compartido por todos los escenarios de prueba: simuladores, carpeta del agente y
 * contador de fallas. Cada escenario usa verificar() para informar su resultado.
 */
final class ContextoDePrueba {

    static final int PUERTO_DEL_RELOJ = 18080;
    static final int PUERTO_DE_LA_NUBE = 18081;
    static final int PUERTO_DEL_RECEPTOR = 18095;

    final SimuladorDeRelojHikvisionIsapi reloj = new SimuladorDeRelojHikvisionIsapi();
    final SimuladorDeNubeFluxTech nube = new SimuladorDeNubeFluxTech();
    final LocalDateTime ahora = LocalDateTime.now().withNano(0);
    final Path carpetaDelAgente;
    private int cantidadDeFallas = 0;

    ContextoDePrueba() throws IOException {
        carpetaDelAgente = Files.createTempDirectory("agente-prueba");
        escribirConfiguracion(SimuladorDeNubeFluxTech.CLAVE_VALIDA, "automatico");
    }

    /** Escribe agente.properties apuntando a la nube simulada. */
    void escribirConfiguracion(String claveDelAgente, String modoHikvision) throws IOException {
        Files.writeString(carpetaDelAgente.resolve("agente.properties"),
                "servidor.url=http://127.0.0.1:" + PUERTO_DE_LA_NUBE + "\n"
                        + "agente.clave=" + claveDelAgente + "\n"
                        + "receptor.puerto=" + PUERTO_DEL_RECEPTOR + "\n"
                        + "hikvision.modo=" + modoHikvision + "\n");
    }

    ConfiguracionLocal cargarConfiguracion() throws IOException {
        return ConfiguracionLocal.cargarDesdeCarpeta(carpetaDelAgente);
    }

    /** Crea un agente nuevo (equivale a reiniciar la PC) y ejecuta un ciclo. */
    CicloDeSincronizacion ejecutarCicloConAgenteNuevo() throws IOException, InterruptedException {
        CicloDeSincronizacion ciclo = new CicloDeSincronizacion(cargarConfiguracion());
        ciclo.ejecutarUnCiclo();
        return ciclo;
    }

    int contarPendientesEnDisco() throws IOException {
        return new ColaDeFichadasPendientes(cargarConfiguracion().obtenerCarpetaDeDatos()).contarPendientes();
    }

    void verificar(String descripcion, boolean resultadoCorrecto) {
        System.out.println((resultadoCorrecto ? "  [OK]    " : "  [FALLA] ") + descripcion);
        if (!resultadoCorrecto) {
            cantidadDeFallas++;
        }
    }

    int obtenerCantidadDeFallas() {
        return cantidadDeFallas;
    }
}
