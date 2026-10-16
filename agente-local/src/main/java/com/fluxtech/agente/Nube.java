package com.fluxtech.agente;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Comunicación con la nube de FluxTech (HTTPS, header X-Agent-Key). */
public final class Nube {

    /** La nube rechazó la clave: no tiene sentido reintentar hasta que se corrija. */
    public static final class ClaveInvalidaException extends IOException {
        private static final long serialVersionUID = 1L;

        ClaveInvalidaException() {
            super("La nube rechazó la clave del agente (revocada o mal copiada). "
                    + "Generá una nueva en el panel y actualizá agente.properties.");
        }
    }

    private final HttpClient http;
    private final String base;
    private final String clave;

    public Nube(String servidorUrl, String clave) {
        this.base = servidorUrl;
        this.clave = clave;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public Map<String, Object> config() throws IOException, InterruptedException {
        return Json.parseObjeto(enviar("GET", "/api/agente/config", null));
    }

    public Map<String, Object> subir(List<Fichada> lote) throws IOException, InterruptedException {
        List<Object> items = new java.util.ArrayList<>();
        for (Fichada f : lote) items.add(f.aMapa());
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("fichadas", items);
        return Json.parseObjeto(enviar("POST", "/api/agente/fichadas", Json.escribir(cuerpo)));
    }

    public void heartbeat(Map<String, Object> estado) throws IOException, InterruptedException {
        enviar("POST", "/api/agente/heartbeat", Json.escribir(estado));
    }

    private String enviar(String metodo, String ruta, String cuerpo) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + ruta))
                .timeout(Duration.ofSeconds(60))
                .header("X-Agent-Key", clave)
                .header("Accept", "application/json");
        if (cuerpo != null) {
            b.header("Content-Type", "application/json");
            b.method(metodo, HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8));
        } else {
            b.method(metodo, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int s = r.statusCode();
        if (s == 401 || s == 403) throw new ClaveInvalidaException();
        if (s < 200 || s >= 300) {
            String detalle = r.body() == null ? "" : r.body();
            if (detalle.length() > 300) detalle = detalle.substring(0, 300);
            throw new IOException("La nube respondió HTTP " + s + " en " + ruta + " " + detalle);
        }
        return r.body();
    }
}
