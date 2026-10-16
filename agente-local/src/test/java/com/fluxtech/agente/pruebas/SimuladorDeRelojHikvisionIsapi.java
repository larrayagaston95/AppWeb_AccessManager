package com.fluxtech.agente.pruebas;

import com.fluxtech.agente.json.EscritorJson;
import com.fluxtech.agente.json.LectorJson;
import com.fluxtech.agente.util.ConversorDeFechas;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reloj Hikvision simulado por ISAPI para las pruebas: exige autenticación Digest real (verifica el
 * hash como lo hace el equipo) y pagina la búsqueda de eventos de a 30 con "MORE", como el reloj.
 */
final class SimuladorDeRelojHikvisionIsapi {

    static final String USUARIO = "admin";
    static final String CONTRASENIA = "Clave123";
    static final String NUMERO_DE_SERIE = "DS-K1A802MF20180709AAWR123456789";
    private static final String REALM = "DS-K1A802MF";
    private static final String NONCE = "nonceDePrueba123";

    /** Cada evento: {legajo, fecha "yyyy-MM-ddTHH:mm:ss", código minor}. */
    final List<String[]> eventosEnMemoria = Collections.synchronizedList(new ArrayList<>());
    int pedidosSinCredenciales = 0;
    int autenticacionesFallidas = 0;
    /** Si es true, la búsqueda de eventos responde 404 (firmware sin ISAPI de eventos). */
    volatile boolean busquedaDeEventosNoSoportada = false;
    private HttpServer servidor;

    void iniciar(int puerto) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", puerto), 0);
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    void detener() {
        servidor.stop(0);
    }

    void agregarEvento(String legajo, LocalDateTime fechaHora, int codigo) {
        eventosEnMemoria.add(new String[]{legajo, ConversorDeFechas.formatearConSegundos(fechaHora), String.valueOf(codigo)});
    }

    private void atender(HttpExchange intercambio) throws IOException {
        String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (intercambio.getRequestHeaders().getFirst("Authorization") == null) {
            pedidosSinCredenciales++;
            intercambio.getResponseHeaders().add("WWW-Authenticate",
                    "Digest qop=\"auth\", realm=\"" + REALM + "\", nonce=\"" + NONCE + "\", stale=\"FALSE\"");
            responder(intercambio, 401, "");
            return;
        }
        if (!esDigestValido(intercambio)) {
            autenticacionesFallidas++;
            responder(intercambio, 401, "");
            return;
        }
        String ruta = intercambio.getRequestURI().getPath();
        if (ruta.equals("/ISAPI/System/deviceInfo")) {
            responder(intercambio, 200, "<DeviceInfo><model>DS-K1A802MF</model><serialNumber>" + NUMERO_DE_SERIE
                    + "</serialNumber><firmwareVersion>V1.2.0</firmwareVersion></DeviceInfo>");
        } else if (ruta.equals("/ISAPI/AccessControl/AcsEvent")) {
            if (busquedaDeEventosNoSoportada) {
                responder(intercambio, 404, "notSupport");
            } else {
                responder(intercambio, 200, armarPaginaDeEventos(cuerpo));
            }
        } else {
            responder(intercambio, 404, "");
        }
    }

    @SuppressWarnings("unchecked")
    private String armarPaginaDeEventos(String cuerpo) {
        Map<String, Object> condicion = (Map<String, Object>) LectorJson.convertirEnMapa(cuerpo).get("AcsEventCond");
        LocalDateTime desde = ConversorDeFechas.interpretarFechaDelReloj((String) condicion.get("startTime"));
        LocalDateTime hasta = ConversorDeFechas.interpretarFechaDelReloj((String) condicion.get("endTime"));
        int posicion = ((Number) condicion.get("searchResultPosition")).intValue();
        int maximo = ((Number) condicion.get("maxResults")).intValue();

        List<Map<String, Object>> coincidencias = new ArrayList<>();
        synchronized (eventosEnMemoria) {
            for (String[] evento : eventosEnMemoria) {
                LocalDateTime momento = LocalDateTime.parse(evento[1]);
                if (momento.isBefore(desde) || momento.isAfter(hasta)) continue;
                Map<String, Object> registro = new LinkedHashMap<>();
                registro.put("major", 5);
                registro.put("minor", Long.parseLong(evento[2]));
                registro.put("time", evento[1] + "-03:00");
                if (!evento[0].isEmpty()) registro.put("employeeNoString", evento[0]);
                coincidencias.add(registro);
            }
        }
        List<Map<String, Object>> pagina = coincidencias.subList(Math.min(posicion, coincidencias.size()),
                Math.min(posicion + maximo, coincidencias.size()));
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("responseStatusStrg", posicion + maximo < coincidencias.size() ? "MORE" : (coincidencias.isEmpty() ? "NO MATCH" : "OK"));
        resultado.put("numOfMatches", pagina.size());
        if (!pagina.isEmpty()) resultado.put("InfoList", new ArrayList<>(pagina));
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("AcsEvent", resultado);
        return EscritorJson.convertirEnTexto(respuesta);
    }

    /** Recalcula el hash Digest como lo haría el reloj real. */
    private static boolean esDigestValido(HttpExchange intercambio) {
        String cabecera = intercambio.getRequestHeaders().getFirst("Authorization");
        if (!cabecera.startsWith("Digest ")) return false;
        Map<String, String> parametros = new HashMap<>();
        Matcher buscador = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^,\\s]+))").matcher(cabecera.substring(7));
        while (buscador.find()) parametros.put(buscador.group(1), buscador.group(2) != null ? buscador.group(2) : buscador.group(3));
        String hashCredenciales = md5(USUARIO + ":" + REALM + ":" + CONTRASENIA);
        String hashPedido = md5(intercambio.getRequestMethod() + ":" + parametros.get("uri"));
        String esperado = md5(hashCredenciales + ":" + NONCE + ":" + parametros.get("nc") + ":" + parametros.get("cnonce")
                + ":" + parametros.get("qop") + ":" + hashPedido);
        return USUARIO.equals(parametros.get("username")) && esperado.equals(parametros.get("response"))
                && intercambio.getRequestURI().toString().equals(parametros.get("uri"));
    }

    private static String md5(String texto) {
        try {
            StringBuilder hexadecimal = new StringBuilder();
            for (byte valor : MessageDigest.getInstance("MD5").digest(texto.getBytes(StandardCharsets.UTF_8))) {
                hexadecimal.append(String.format("%02x", valor));
            }
            return hexadecimal.toString();
        } catch (Exception excepcion) {
            throw new IllegalStateException(excepcion);
        }
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
