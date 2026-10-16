package com.fluxtech.agente;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Receptor HTTP en la red local: el reloj se configura para enviar sus eventos a
 * http://<IP de esta PC>:<puerto>/ (en Hikvision: "HTTP Listening" / "Servidor de eventos").
 *
 * Solo se aceptan eventos que vengan desde la IP de un reloj configurado en el panel: así otra
 * PC de la red no puede inyectar fichadas. Siempre responde 200, porque un error haría que el
 * reloj reintente sin fin; los Hikvision reenvían los eventos que no pudieron entregar.
 */
public final class Receptor {

    private static final int MAX_CUERPO = 2 * 1024 * 1024;

    private static final Pattern P_LEGAJO_JSON = Pattern.compile("\"employeeNoString\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern P_LEGAJO_NUM = Pattern.compile("\"employeeNo\"\\s*:\\s*\"?(\\d+)\"?");
    private static final Pattern P_LEGAJO_XML = Pattern.compile("<employeeNoString>([^<]*)</employeeNoString>");
    private static final Pattern P_FECHA_JSON = Pattern.compile("\"dateTime\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern P_FECHA_JSON2 = Pattern.compile("\"time\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern P_FECHA_XML = Pattern.compile("<dateTime>([^<]+)</dateTime>");
    private static final Pattern P_MINOR = Pattern.compile("\"(?:subEventType|minor)\"\\s*:\\s*(\\d+)");
    private static final Pattern P_MINOR_XML = Pattern.compile("<(?:subEventType|minor)>(\\d+)</(?:subEventType|minor)>");
    private static final Pattern P_MODO = Pattern.compile("\"currentVerifyMode\"\\s*:\\s*\"([^\"]*)\"");

    private final int puerto;
    private final Cola cola;
    /** IP remota → id de reloj configurado (o null si no corresponde a ningún reloj). */
    private final Function<String, Long> relojPorIp;
    private HttpServer server;

    public Receptor(int puerto, Cola cola, Function<String, Long> relojPorIp) {
        this.puerto = puerto;
        this.cola = cola;
        this.relojPorIp = relojPorIp;
    }

    public void iniciar() throws IOException {
        server = HttpServer.create(new InetSocketAddress(puerto), 0);
        server.createContext("/", this::atender);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        Log.info("Receptor escuchando en el puerto " + puerto + " (para relojes que envían sus eventos)");
    }

    public void detener() {
        if (server != null) server.stop(1);
    }

    private void atender(HttpExchange ex) throws IOException {
        try {
            String ip = ex.getRemoteAddress().getAddress().getHostAddress();
            String cuerpo = leer(ex.getRequestBody());
            Long relojId = relojPorIp.apply(ip);

            if (relojId == null) {
                Log.aviso("Receptor: evento ignorado desde " + ip + " (no es la IP de ningún reloj configurado)");
            } else {
                Fichada f = interpretar(relojId, cuerpo);
                if (f != null) {
                    if (cola.agregar(f)) Log.info("Receptor: fichada legajo " + f.legajo + " " + f.fechaHora + " (reloj " + relojId + ")");
                } else {
                    Log.detalle("Receptor: mensaje sin fichada desde " + ip + ": " + recortar(cuerpo));
                }
            }
        } catch (Exception e) {
            Log.error("Receptor: error procesando un evento", e);
        } finally {
            byte[] ok = "OK".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, ok.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(ok);
            }
        }
    }

    /** Extrae una fichada de un evento Hikvision (JSON, XML o multipart). Null si no es una fichada. */
    static Fichada interpretar(long relojId, String cuerpo) {
        if (cuerpo == null || cuerpo.isEmpty()) return null;
        String legajo = primero(cuerpo, P_LEGAJO_JSON, P_LEGAJO_XML, P_LEGAJO_NUM);
        if (legajo == null || legajo.isBlank()) return null; // latidos, alarmas, puertas, etc.

        String fechaTxt = primero(cuerpo, P_FECHA_JSON, P_FECHA_XML, P_FECHA_JSON2);
        LocalDateTime fh = Fichada.parsearFecha(fechaTxt);
        if (fh == null) return null;

        String minorTxt = primero(cuerpo, P_MINOR, P_MINOR_XML);
        long minor = minorTxt != null ? Long.parseLong(minorTxt) : -1;
        if (HikvisionIsapi.MINOR_RECHAZOS.contains(minor)) return null;

        String modo = primero(cuerpo, P_MODO);
        return new Fichada(relojId, legajo, fh, modo == null || modo.isBlank() ? "HIKVISION" : modo);
    }

    private static String primero(String texto, Pattern... patrones) {
        for (Pattern p : patrones) {
            Matcher m = p.matcher(texto);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private static String leer(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        int total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > MAX_CUERPO) throw new IOException("Evento demasiado grande");
            out.write(buf, 0, n);
        }
        // Los eventos con foto (rostro) traen binario: se interpreta como latin-1 para no romper el texto
        return out.toString(StandardCharsets.ISO_8859_1);
    }

    private static String recortar(String s) {
        String t = s.replaceAll("[^\\x20-\\x7E]", ".");
        return t.length() > 300 ? t.substring(0, 300) + "..." : t;
    }
}
