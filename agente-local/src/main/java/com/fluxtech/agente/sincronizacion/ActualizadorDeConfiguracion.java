package com.fluxtech.agente.sincronizacion;

import com.fluxtech.agente.json.AyudaMapasJson;
import com.fluxtech.agente.json.EscritorJson;
import com.fluxtech.agente.json.LectorJson;
import com.fluxtech.agente.modelo.ConfiguracionDeReloj;
import com.fluxtech.agente.nube.ServicioNubeFluxTech;
import com.fluxtech.agente.registro.RegistroDeEventos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Mantiene actualizada la lista de relojes que tiene que leer el agente.
 *
 * Recorrido del dato:
 *   1. Al crear el agente se carga la última lista guardada (datos/config-cache.json), así se
 *      puede seguir leyendo los relojes aunque la PC arranque sin internet.
 *   2. En cada ciclo, actualizarDesdeLaNube() pide GET /api/agente/config al ServicioNubeFluxTech.
 *   3. Si responde, se reemplaza la lista y se guarda la copia local; si falla, se sigue con la anterior.
 */
final class ActualizadorDeConfiguracion {

    private static final String NOMBRE_DEL_ARCHIVO = "ActualizadorDeConfiguracion.java";
    private static final int SEGUNDOS_MINIMOS_ENTRE_CICLOS = 15;
    private static final int SEGUNDOS_MAXIMOS_ENTRE_CICLOS = 3600;

    private final ServicioNubeFluxTech servicioNube;
    private final Path archivoDeCopiaLocal;
    private final int segundosEntreCiclosForzados;
    private volatile List<ConfiguracionDeReloj> relojesConfigurados = Collections.emptyList();
    private volatile int segundosEntreCiclos = 60;

    ActualizadorDeConfiguracion(ServicioNubeFluxTech servicioNube, Path carpetaDeDatos, int segundosEntreCiclosForzados) {
        this.servicioNube = servicioNube;
        this.archivoDeCopiaLocal = carpetaDeDatos.resolve("config-cache.json");
        this.segundosEntreCiclosForzados = segundosEntreCiclosForzados;
        cargarCopiaLocal();
    }

    /** Pide la configuración a la nube. Si falla, conserva la lista anterior (el error ya lo registró el servicio). */
    void actualizarDesdeLaNube() throws InterruptedException {
        try {
            Map<String, Object> configuracionRecibida = servicioNube.descargarConfiguracion();
            aplicar(configuracionRecibida);
            guardarCopiaLocal(configuracionRecibida);
        } catch (IOException | RuntimeException excepcion) {
            RegistroDeEventos.avisar("Se sigue con la última lista de relojes conocida (" + relojesConfigurados.size() + " reloj/es)");
        }
    }

    List<ConfiguracionDeReloj> obtenerRelojesConfigurados() {
        return relojesConfigurados;
    }

    int obtenerSegundosEntreCiclos() {
        return segundosEntreCiclos;
    }

    /** Id del reloj cuya IP coincide con la recibida, o null. Lo usa el receptor para validar el origen. */
    Long buscarIdDeRelojPorIp(String ipDeOrigen) {
        for (ConfiguracionDeReloj reloj : relojesConfigurados) {
            String ipDelReloj = reloj.obtenerIpSinPuerto();
            if (!ipDelReloj.isEmpty() && ipDelReloj.equals(ipDeOrigen)) {
                return reloj.obtenerIdDelReloj();
            }
        }
        return null;
    }

    /** Convierte la respuesta de la nube en la lista de relojes y el intervalo entre ciclos. */
    @SuppressWarnings("unchecked")
    private void aplicar(Map<String, Object> configuracionRecibida) {
        List<ConfiguracionDeReloj> relojesNuevos = new ArrayList<>();
        Object listaDeRelojes = configuracionRecibida.get("relojes");
        if (listaDeRelojes instanceof List) {
            for (Object elemento : (List<Object>) listaDeRelojes) {
                if (elemento instanceof Map) {
                    ConfiguracionDeReloj reloj = ConfiguracionDeReloj.crearDesdeMapaDeLaNube((Map<String, Object>) elemento);
                    if (reloj.obtenerIdDelReloj() > 0) {
                        relojesNuevos.add(reloj);
                    }
                }
            }
        }
        relojesConfigurados = Collections.unmodifiableList(relojesNuevos);
        // Intervalo: el de agente.properties si está forzado; si no, el que indica la nube (acotado)
        long intervaloPedido = segundosEntreCiclosForzados > 0
                ? segundosEntreCiclosForzados
                : AyudaMapasJson.obtenerNumero(configuracionRecibida, "intervaloSegundos", 60);
        segundosEntreCiclos = (int) Math.max(SEGUNDOS_MINIMOS_ENTRE_CICLOS, Math.min(SEGUNDOS_MAXIMOS_ENTRE_CICLOS, intervaloPedido));
    }

    private void guardarCopiaLocal(Map<String, Object> configuracionRecibida) {
        try {
            Files.writeString(archivoDeCopiaLocal, EscritorJson.convertirEnTexto(configuracionRecibida), StandardCharsets.UTF_8);
        } catch (IOException excepcion) {
            RegistroDeEventos.registrarError(NOMBRE_DEL_ARCHIVO, "guardarCopiaLocal", excepcion.getMessage());
        }
    }

    private void cargarCopiaLocal() {
        if (!Files.exists(archivoDeCopiaLocal)) {
            return;
        }
        try {
            aplicar(LectorJson.convertirEnMapa(Files.readString(archivoDeCopiaLocal, StandardCharsets.UTF_8)));
            RegistroDeEventos.informar("Lista de relojes guardada cargada: " + relojesConfigurados.size() + " reloj/es");
        } catch (IOException | RuntimeException excepcion) {
            RegistroDeEventos.registrarError(NOMBRE_DEL_ARCHIVO, "cargarCopiaLocal",
                    "config-cache.json ilegible, se espera a la nube: " + RegistroDeEventos.describirFallo(excepcion));
        }
    }
}
