package com.fluxtech.agente.nube;

import com.fluxtech.agente.json.EscritorJson;
import com.fluxtech.agente.json.LectorJson;
import com.fluxtech.agente.modelo.FichadaLeida;
import com.fluxtech.agente.registro.RegistroDeEventos;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio que consume la API REST de la nube de FluxTech. Es el ÚNICO lugar del agente que
 * habla con la nube; el resto del agente no conoce direcciones ni formatos HTTP.
 *
 * Recorrido del dato (los tres endpoints, todos con el header X-Agent-Key):
 *   1. descargarConfiguracion(): GET /api/agente/config
 *        → la nube responde con los relojes que este agente tiene que leer.
 *   2. subirLoteDeFichadas(): POST /api/agente/fichadas con {"fichadas":[...]}
 *        → la nube responde cuántas guardó, cuántas eran duplicadas y cuántas descartó.
 *   3. informarEstado(): POST /api/agente/heartbeat con el estado de cada reloj
 *        → la nube actualiza "En línea" y el detalle en el panel.
 *
 * Errores: cada método captura la falla, la registra con el formato obligatorio y la vuelve a
 * lanzar para que quien llamó decida (por ejemplo, dejar las fichadas en la cola).
 */
public final class ServicioNubeFluxTech {

    private static final String NOMBRE_DEL_ARCHIVO = "ServicioNubeFluxTech.java";

    private final HttpClient clienteHttp;
    private final String direccionDelServidor;
    private final String claveDelAgente;

    public ServicioNubeFluxTech(String direccionDelServidor, String claveDelAgente) {
        this.direccionDelServidor = direccionDelServidor;
        this.claveDelAgente = claveDelAgente;
        this.clienteHttp = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                // No se siguen redirecciones: evita que la clave termine enviada a otro servidor
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** GET /api/agente/config → Map con "relojes", "intervaloSegundos" y "nombre" del agente. */
    public Map<String, Object> descargarConfiguracion() throws IOException, InterruptedException {
        try {
            String respuesta = enviarPedido("GET", "/api/agente/config", null);
            return LectorJson.convertirEnMapa(respuesta);
        } catch (IOException | RuntimeException excepcion) {
            RegistroDeEventos.registrarError(NOMBRE_DEL_ARCHIVO, "descargarConfiguracion",
                    RegistroDeEventos.describirFallo(excepcion));
            throw excepcion;
        }
    }

    /** POST /api/agente/fichadas → Map con "guardadas", "duplicadas", "descartadas" y "avisos". */
    public Map<String, Object> subirLoteDeFichadas(List<FichadaLeida> loteDeFichadas) throws IOException, InterruptedException {
        // Paso 1: convertir cada fichada al formato que espera la nube
        List<Object> fichadasEnFormatoDeLaNube = new ArrayList<>();
        for (FichadaLeida fichada : loteDeFichadas) {
            fichadasEnFormatoDeLaNube.add(fichada.convertirEnMapa());
        }
        Map<String, Object> cuerpoDelPedido = new LinkedHashMap<>();
        cuerpoDelPedido.put("fichadas", fichadasEnFormatoDeLaNube);
        try {
            // Paso 2: enviar y leer el resumen que devuelve la nube
            String respuesta = enviarPedido("POST", "/api/agente/fichadas", EscritorJson.convertirEnTexto(cuerpoDelPedido));
            return LectorJson.convertirEnMapa(respuesta);
        } catch (IOException | RuntimeException excepcion) {
            RegistroDeEventos.registrarError(NOMBRE_DEL_ARCHIVO, "subirLoteDeFichadas",
                    "no se pudieron subir " + loteDeFichadas.size() + " fichada(s): " + RegistroDeEventos.describirFallo(excepcion));
            throw excepcion;
        }
    }

    /** POST /api/agente/heartbeat → la nube solo confirma con {"ok":true}. */
    public void informarEstado(Map<String, Object> estadoDelAgente) throws IOException, InterruptedException {
        try {
            enviarPedido("POST", "/api/agente/heartbeat", EscritorJson.convertirEnTexto(estadoDelAgente));
        } catch (IOException | RuntimeException excepcion) {
            RegistroDeEventos.registrarError(NOMBRE_DEL_ARCHIVO, "informarEstado", RegistroDeEventos.describirFallo(excepcion));
            throw excepcion;
        }
    }

    /**
     * Envía el pedido HTTPS con la clave del agente y devuelve el cuerpo de la respuesta.
     * 401/403 → ClaveDelAgenteRechazadaException; cualquier otro código fuera de 2xx → IOException.
     */
    private String enviarPedido(String metodoHttp, String ruta, String cuerpoJson) throws IOException, InterruptedException {
        HttpRequest.Builder constructorDelPedido = HttpRequest.newBuilder(URI.create(direccionDelServidor + ruta))
                .timeout(Duration.ofSeconds(60))
                .header("X-Agent-Key", claveDelAgente)
                .header("Accept", "application/json");
        if (cuerpoJson != null) {
            constructorDelPedido.header("Content-Type", "application/json");
            constructorDelPedido.method(metodoHttp, HttpRequest.BodyPublishers.ofString(cuerpoJson, StandardCharsets.UTF_8));
        } else {
            constructorDelPedido.method(metodoHttp, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> respuesta = clienteHttp.send(constructorDelPedido.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int codigoDeEstado = respuesta.statusCode();
        if (codigoDeEstado == 401 || codigoDeEstado == 403) {
            throw new ClaveDelAgenteRechazadaException();
        }
        if (codigoDeEstado < 200 || codigoDeEstado >= 300) {
            String detalle = respuesta.body() == null ? "" : respuesta.body();
            throw new IOException("la nube respondió HTTP " + codigoDeEstado + " en " + ruta + " "
                    + (detalle.length() > 300 ? detalle.substring(0, 300) : detalle));
        }
        return respuesta.body();
    }
}
