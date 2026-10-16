package com.fluxtech.agente.pruebas;

import com.fluxtech.agente.json.EscritorJson;
import com.fluxtech.agente.json.LectorJson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Nube de FluxTech simulada para las pruebas: implementa /api/agente/config, /fichadas y /heartbeat
 * con las mismas reglas que la real (clave obligatoria, duplicados descartados).
 */
final class SimuladorDeNubeFluxTech {

    static final String CLAVE_VALIDA = "fxa_abcdefghjk_SecretoDePruebaSecretoDePruebaSecretoDeP";

    String numeroDeSerieEnElPanel = "AAWR123456789";
    String direccionDelRelojEnElPanel = "127.0.0.1:18080";
    volatile boolean caida = false;
    final Set<String> fichadasGuardadas = Collections.synchronizedSet(new LinkedHashSet<>());
    final List<String> ordenDeGuardado = Collections.synchronizedList(new ArrayList<>());
    int duplicadasRecibidas = 0;
    int heartbeatsRecibidos = 0;
    String ultimoHeartbeat = "";
    private HttpServer servidor;

    void iniciar(int puerto) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", puerto), 0);
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    void detener() {
        servidor.stop(0);
    }

    @SuppressWarnings("unchecked")
    private void atender(HttpExchange intercambio) throws IOException {
        String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (!CLAVE_VALIDA.equals(intercambio.getRequestHeaders().getFirst("X-Agent-Key"))) {
            responder(intercambio, 403, "");
            return;
        }
        switch (intercambio.getRequestURI().getPath()) {
            case "/api/agente/config":
                responder(intercambio, 200, armarConfiguracion());
                return;
            case "/api/agente/fichadas":
                if (caida) {
                    responder(intercambio, 503, "servicio caído");
                    return;
                }
                List<Object> fichadas = (List<Object>) LectorJson.convertirEnMapa(cuerpo).get("fichadas");
                int guardadas = 0;
                int duplicadas = 0;
                for (Object elemento : fichadas) {
                    Map<String, Object> fichada = (Map<String, Object>) elemento;
                    String clave = fichada.get("legajo") + "|" + fichada.get("fechaHora");
                    if (fichadasGuardadas.add(clave)) {
                        guardadas++;
                        ordenDeGuardado.add((String) fichada.get("fechaHora"));
                    } else {
                        duplicadas++;
                        duplicadasRecibidas++;
                    }
                }
                Map<String, Object> resumen = new LinkedHashMap<>();
                resumen.put("recibidas", fichadas.size());
                resumen.put("guardadas", guardadas);
                resumen.put("duplicadas", duplicadas);
                resumen.put("descartadas", 0);
                resumen.put("avisos", new ArrayList<>());
                responder(intercambio, 200, EscritorJson.convertirEnTexto(resumen));
                return;
            case "/api/agente/heartbeat":
                heartbeatsRecibidos++;
                ultimoHeartbeat = cuerpo;
                responder(intercambio, 200, "{\"ok\":true}");
                return;
            default:
                responder(intercambio, 404, "");
        }
    }

    private String armarConfiguracion() {
        Map<String, Object> reloj = new LinkedHashMap<>();
        reloj.put("id", 5);
        reloj.put("nombre", "Reloj entrada");
        reloj.put("numeroSerie", numeroDeSerieEnElPanel);
        reloj.put("marca", "Hikvision");
        reloj.put("ip", direccionDelRelojEnElPanel);
        reloj.put("usuario", SimuladorDeRelojHikvisionIsapi.USUARIO);
        reloj.put("password", SimuladorDeRelojHikvisionIsapi.CONTRASENIA);
        reloj.put("ultimaFichada", "");
        Map<String, Object> configuracion = new LinkedHashMap<>();
        configuracion.put("agenteId", 1);
        configuracion.put("nombre", "PC de prueba");
        configuracion.put("intervaloSegundos", 60);
        configuracion.put("relojes", List.of(reloj));
        return EscritorJson.convertirEnTexto(configuracion);
    }

    /** true si las fichadas se guardaron de la más vieja a la más nueva. */
    boolean seGuardaronEnOrdenCronologico() {
        synchronized (ordenDeGuardado) {
            for (int indice = 1; indice < ordenDeGuardado.size(); indice++) {
                if (ordenDeGuardado.get(indice).compareTo(ordenDeGuardado.get(indice - 1)) < 0) return false;
            }
        }
        return true;
    }

    private static void responder(HttpExchange intercambio, int codigo, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.sendResponseHeaders(codigo, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(bytes);
            }
        }
        intercambio.close();
    }
}
