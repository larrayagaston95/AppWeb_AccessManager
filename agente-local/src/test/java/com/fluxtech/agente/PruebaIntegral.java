package com.fluxtech.agente;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prueba de punta a punta: Hikvision simulado (Digest real, paginación ISAPI) + nube simulada.
 */
public class PruebaIntegral {

    // ── Hikvision simulado ──────────────────────────────────────────────────
    static final String USER = "admin", PASS = "Clave123", REALM = "DS-K1A802MF", NONCE = "abc123nonce";
    static final List<String[]> EVENTOS = Collections.synchronizedList(new ArrayList<>()); // legajo, fecha, minor
    static int peticionesSinAuth = 0, authFallidas = 0;

    // ── Nube simulada ───────────────────────────────────────────────────────
    static final String CLAVE = "fxa_abcdefghjk_SecretoDePruebaSecretoDePruebaSecretoDeP";
    static String serieEnPanel = "AAWR123456789";
    static boolean nubeCaida = false;
    static final Set<String> guardadasNube = Collections.synchronizedSet(new LinkedHashSet<>());
    static final List<String> ordenGuardado = Collections.synchronizedList(new ArrayList<>());
    static int duplicadasNube = 0, heartbeats = 0;
    static String ultimoHeartbeat = "";

    static final DateTimeFormatter F = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static void main(String[] a) throws Exception {
        LocalDateTime hoy = LocalDateTime.now().withNano(0);
        // 3 días de fichadas: 101 y 102 entrada/salida, más rechazos y eventos sin legajo
        for (int d = 3; d >= 0; d--) {
            LocalDateTime dia = hoy.minusDays(d).withHour(8).withMinute(0).withSecond(0);
            if (dia.isAfter(hoy)) continue;
            EVENTOS.add(new String[]{"101", F.format(dia), "38"});
            EVENTOS.add(new String[]{"102", F.format(dia.plusMinutes(3)), "1"});
            EVENTOS.add(new String[]{"101", F.format(dia.plusMinutes(1)), "39"});   // huella rechazada
            EVENTOS.add(new String[]{"",    F.format(dia.plusMinutes(2)), "39"});   // sin legajo
            if (dia.plusHours(9).isBefore(hoy)) {
                EVENTOS.add(new String[]{"101", F.format(dia.plusHours(9)), "38"});
                EVENTOS.add(new String[]{"102", F.format(dia.plusHours(9).plusMinutes(5)), "38"});
            }
        }
        // Muchos eventos para forzar paginación (>30)
        for (int k = 0; k < 40; k++) EVENTOS.add(new String[]{"9" + k, F.format(hoy.minusDays(10).plusMinutes(k * 7)), "38"});

        HttpServer hik = HttpServer.create(new InetSocketAddress("127.0.0.1", 18080), 0);
        hik.createContext("/", PruebaIntegral::hikvision);
        hik.start();
        HttpServer nube = HttpServer.create(new InetSocketAddress("127.0.0.1", 18081), 0);
        nube.createContext("/", PruebaIntegral::nube);
        nube.start();

        Path dir = Files.createTempDirectory("agente");
        Files.writeString(dir.resolve("agente.properties"),
                "servidor.url=http://127.0.0.1:18081\nagente.clave=" + CLAVE + "\nreceptor.puerto=18095\nlog.detalle=false\n");

        int fallas = 0;
        Config cfg = Config.cargar(dir);
        Log.iniciar(cfg.datos(), false);

        long esperadas = EVENTOS.stream().filter(e -> !e[0].isEmpty() && !e[2].equals("39")).count();

        // 1) Primer ciclo: lee 31 días con paginación y sube todo
        Agente ag = new Agente(cfg);
        ag.ciclo();
        fallas += check("1. Primer ciclo sube todas las fichadas válidas (" + esperadas + ")", guardadasNube.size() == esperadas);
        fallas += check("1b. Sin rechazos ni eventos sin legajo", guardadasNube.stream().noneMatch(k -> k.startsWith("|")));
        fallas += check("1c. Subidas en orden cronológico", ordenCronologico());
        fallas += check("1d. Autenticación Digest aceptada (0 fallidas)", authFallidas == 0 && peticionesSinAuth > 0);
        fallas += check("1e. Heartbeat recibido con reloj OK", heartbeats == 1 && ultimoHeartbeat.contains("\"ok\":true"));

        // 2) Segundo ciclo sin eventos nuevos: no reenvía nada
        int antes = guardadasNube.size();
        duplicadasNube = 0;
        ag.ciclo();
        fallas += check("2. Ciclo sin novedades no reenvía ni duplica", guardadasNube.size() == antes && duplicadasNube == 0);

        // 3) Nube caída: las nuevas quedan en cola en disco, sobreviven a un reinicio y se suben al volver
        EVENTOS.add(new String[]{"101", F.format(hoy.minusMinutes(2)), "38"});
        EVENTOS.add(new String[]{"102", F.format(hoy.minusMinutes(1)), "38"});
        nubeCaida = true;
        ag.ciclo();
        fallas += check("3. Con la nube caída quedan 2 en cola", new Cola(cfg.datos()).cantidadPendientes() == 2);
        Agente reiniciado = new Agente(cfg); // simula apagar y prender la PC
        nubeCaida = false;
        reiniciado.ciclo();
        fallas += check("3b. Tras reiniciar y volver internet se suben las 2", guardadasNube.size() == antes + 2);
        fallas += check("3c. Cola vacía al final", new Cola(cfg.datos()).cantidadPendientes() == 0);

        // 4) Receptor: el reloj empuja un evento (multipart con JSON) desde su IP
        Cola colaR = new Cola(cfg.datos());
        Receptor rec = new Receptor(18095, colaR, ip -> ip.equals("127.0.0.1") ? 5L : null);
        rec.iniciar();
        String evento = "--MIME_boundary\r\nContent-Disposition: form-data; name=\"event_log\"\r\n\r\n"
                + "{\"ipAddress\":\"127.0.0.1\",\"dateTime\":\"" + F.format(hoy) + "-03:00\",\"eventType\":\"AccessControllerEvent\","
                + "\"AccessControllerEvent\":{\"majorEventType\":5,\"subEventType\":38,\"employeeNoString\":\"103\",\"currentVerifyMode\":\"fingerPrint\"}}"
                + "\r\n--MIME_boundary--\r\n";
        String latido = "{\"eventType\":\"heartBeat\",\"dateTime\":\"" + F.format(hoy) + "\"}";
        String rechazo = "{\"dateTime\":\"" + F.format(hoy) + "\",\"AccessControllerEvent\":{\"subEventType\":39,\"employeeNoString\":\"104\"}}";
        int c1 = post("http://127.0.0.1:18095/", evento);
        post("http://127.0.0.1:18095/", latido);
        post("http://127.0.0.1:18095/", rechazo);
        fallas += check("4. Receptor responde 200", c1 == 200);
        fallas += check("4b. Receptor encola solo la fichada válida (no latido ni rechazo)", colaR.cantidadPendientes() == 1);
        rec.detener();
        Agente conReceptor = new Agente(cfg);
        conReceptor.ciclo();
        fallas += check("4c. La fichada del receptor llega a la nube", guardadasNube.stream().anyMatch(k -> k.startsWith("103|")));

        // 5) La IP apunta a otro equipo: no se lee nada
        serieEnPanel = "OTRO999";
        EVENTOS.add(new String[]{"101", F.format(hoy), "38"});
        int previo = guardadasNube.size();
        new Agente(cfg).ciclo();
        fallas += check("5. Serie distinta → no lee y reporta error", guardadasNube.size() == previo
                && ultimoHeartbeat.contains("otro equipo"));
        serieEnPanel = "AAWR123456789";
        new Agente(cfg).ciclo(); // config corregida (y se sube lo pendiente)
        EVENTOS.add(new String[]{"102", F.format(hoy.plusSeconds(1)), "38"});

        // 6) Clave revocada: no sube pero sigue leyendo a la cola
        String claveOk = Files.readString(dir.resolve("agente.properties"));
        Files.writeString(dir.resolve("agente.properties"), claveOk.replace(CLAVE, "fxa_otraclave1_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"));
        new Agente(Config.cargar(dir)).ciclo();
        fallas += check("6. Clave inválida: lo leído queda en cola, no se pierde", new Cola(cfg.datos()).cantidadPendientes() == 1);
        Files.writeString(dir.resolve("agente.properties"), claveOk);
        new Agente(Config.cargar(dir)).ciclo();
        fallas += check("6b. Con la clave corregida se sube", new Cola(cfg.datos()).cantidadPendientes() == 0);

        // 7) Config validada: http a internet rechazado
        Path d2 = Files.createTempDirectory("agente2");
        Files.writeString(d2.resolve("agente.properties"), "servidor.url=http://fluxtech.com.ar\nagente.clave=" + CLAVE + "\n");
        boolean rechazada = false;
        try { Config.cargar(d2); } catch (java.io.IOException e) { rechazada = e.getMessage().contains("https"); }
        fallas += check("7. Rechaza http:// hacia internet (la clave viajaría sin cifrar)", rechazada);

        hik.stop(0);
        nube.stop(0);
        System.out.println();
        System.out.println(fallas == 0 ? "TODAS LAS PRUEBAS OK" : fallas + " PRUEBA(S) FALLARON");
        System.exit(fallas == 0 ? 0 : 1);
    }

    static boolean ordenCronologico() {
        for (int k = 1; k < ordenGuardado.size(); k++) {
            if (ordenGuardado.get(k).compareTo(ordenGuardado.get(k - 1)) < 0) return false;
        }
        return true;
    }

    static int check(String nombre, boolean ok) {
        System.out.println((ok ? "  [OK]    " : "  [FALLA] ") + nombre);
        return ok ? 0 : 1;
    }

    static int post(String url, String body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    static void responder(HttpExchange ex, int code, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, b.length == 0 ? -1 : b.length);
        if (b.length > 0) try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        ex.close();
    }

    // Verifica Digest como lo haría el reloj real
    static boolean digestValido(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        if (h == null || !h.startsWith("Digest ")) return false;
        Map<String, String> p = new HashMap<>();
        Matcher m = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^,\\s]+))").matcher(h.substring(7));
        while (m.find()) p.put(m.group(1), m.group(2) != null ? m.group(2) : m.group(3));
        String ha1 = RelojHttp.md5(USER + ":" + REALM + ":" + PASS);
        String ha2 = RelojHttp.md5(ex.getRequestMethod() + ":" + p.get("uri"));
        String esperado = RelojHttp.md5(ha1 + ":" + NONCE + ":" + p.get("nc") + ":" + p.get("cnonce") + ":" + p.get("qop") + ":" + ha2);
        boolean uriOk = ex.getRequestURI().toString().equals(p.get("uri"));
        return USER.equals(p.get("username")) && esperado.equals(p.get("response")) && uriOk;
    }

    @SuppressWarnings("unchecked")
    static void hikvision(HttpExchange ex) throws java.io.IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (ex.getRequestHeaders().getFirst("Authorization") == null) {
            peticionesSinAuth++;
            ex.getResponseHeaders().add("WWW-Authenticate", "Digest qop=\"auth\", realm=\"" + REALM + "\", nonce=\"" + NONCE + "\", stale=\"FALSE\"");
            responder(ex, 401, "");
            return;
        }
        if (!digestValido(ex)) { authFallidas++; responder(ex, 401, ""); return; }

        String path = ex.getRequestURI().getPath();
        if (path.equals("/ISAPI/System/deviceInfo")) {
            responder(ex, 200, "<?xml version=\"1.0\"?><DeviceInfo><deviceName>Reloj</deviceName><model>DS-K1A802MF</model>"
                    + "<serialNumber>DS-K1A802MF20180709AAWR123456789</serialNumber><firmwareVersion>V1.2.0</firmwareVersion></DeviceInfo>");
            return;
        }
        if (path.equals("/ISAPI/AccessControl/AcsEvent")) {
            Map<String, Object> cond = (Map<String, Object>) Json.parseObjeto(body).get("AcsEventCond");
            LocalDateTime desde = Fichada.parsearFecha((String) cond.get("startTime"));
            LocalDateTime hasta = Fichada.parsearFecha((String) cond.get("endTime"));
            int pos = ((Number) cond.get("searchResultPosition")).intValue();
            int max = ((Number) cond.get("maxResults")).intValue();
            List<Map<String, Object>> match = new ArrayList<>();
            synchronized (EVENTOS) {
                for (String[] e : EVENTOS) {
                    LocalDateTime t = LocalDateTime.parse(e[1]);
                    if (t.isBefore(desde) || t.isAfter(hasta)) continue;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("major", 5); m.put("minor", Long.parseLong(e[2]));
                    m.put("time", e[1] + "-03:00");
                    if (!e[0].isEmpty()) m.put("employeeNoString", e[0]);
                    m.put("currentVerifyMode", "fingerPrint");
                    match.add(m);
                }
            }
            List<Map<String, Object>> pagina = match.subList(Math.min(pos, match.size()), Math.min(pos + max, match.size()));
            Map<String, Object> acs = new LinkedHashMap<>();
            acs.put("searchID", cond.get("searchID"));
            acs.put("totalMatches", match.size());
            acs.put("responseStatusStrg", pos + max < match.size() ? "MORE" : (match.isEmpty() ? "NO MATCH" : "OK"));
            acs.put("numOfMatches", pagina.size());
            if (!pagina.isEmpty()) acs.put("InfoList", pagina);
            responder(ex, 200, Json.escribir(Map.of("AcsEvent", acs)));
            return;
        }
        responder(ex, 404, "");
    }

    @SuppressWarnings("unchecked")
    static void nube(HttpExchange ex) throws java.io.IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (!CLAVE.equals(ex.getRequestHeaders().getFirst("X-Agent-Key"))) { responder(ex, 403, ""); return; }
        String path = ex.getRequestURI().getPath();
        switch (path) {
            case "/api/agente/config": {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", 5); r.put("nombre", "Reloj entrada"); r.put("numeroSerie", serieEnPanel);
                r.put("marca", "Hikvision"); r.put("ip", "127.0.0.1:18080"); r.put("usuario", USER); r.put("password", PASS);
                r.put("ultimaFichada", "");
                responder(ex, 200, Json.escribir(Map.of("agenteId", 1, "nombre", "PC prueba", "intervaloSegundos", 60, "relojes", List.of(r))));
                return;
            }
            case "/api/agente/fichadas": {
                if (nubeCaida) { responder(ex, 503, "caida"); return; }
                List<Object> items = (List<Object>) Json.parseObjeto(body).get("fichadas");
                int g = 0, d = 0;
                for (Object o : items) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    String k = m.get("legajo") + "|" + m.get("fechaHora");
                    if (guardadasNube.add(k)) { g++; ordenGuardado.add((String) m.get("fechaHora")); } else { d++; duplicadasNube++; }
                }
                responder(ex, 200, Json.escribir(Map.of("recibidas", items.size(), "guardadas", g, "duplicadas", d, "descartadas", 0, "avisos", List.of())));
                return;
            }
            case "/api/agente/heartbeat":
                heartbeats++;
                ultimoHeartbeat = body;
                responder(ex, 200, "{\"ok\":true}");
                return;
            default:
                responder(ex, 404, "");
        }
    }
}
