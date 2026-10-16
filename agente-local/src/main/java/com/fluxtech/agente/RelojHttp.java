package com.fluxtech.agente;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente HTTP para la red local hacia el reloj, con autenticación Digest (la que usan los
 * Hikvision por defecto) y Basic como alternativa. Java no trae Digest incorporado.
 */
public final class RelojHttp {

    public static final class Respuesta {
        public final int estado;
        public final String cuerpo;

        Respuesta(int estado, String cuerpo) {
            this.estado = estado;
            this.cuerpo = cuerpo;
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern PARAM = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^,\\s]+))");

    private final HttpClient http;
    private final String base;
    private final String usuario;
    private final String password;

    public RelojHttp(String hostOIp, String usuario, String password) {
        String h = hostOIp.trim();
        this.base = h.startsWith("http://") || h.startsWith("https://") ? h : "http://" + h;
        this.usuario = usuario;
        this.password = password;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    public Respuesta get(String ruta) throws IOException, InterruptedException {
        return enviar("GET", ruta, null, null);
    }

    public Respuesta postJson(String ruta, String json) throws IOException, InterruptedException {
        return enviar("POST", ruta, json, "application/json");
    }

    private Respuesta enviar(String metodo, String ruta, String cuerpo, String tipo)
            throws IOException, InterruptedException {

        HttpResponse<String> r = http.send(armar(metodo, ruta, cuerpo, tipo, null), cuerpoUtf8());
        if (r.statusCode() != 401) return new Respuesta(r.statusCode(), r.body());

        // El reloj pide autenticación: elegir Digest si lo ofrece, si no Basic
        List<String> desafios = r.headers().allValues("WWW-Authenticate");
        String digest = null;
        boolean basic = false;
        for (String d : desafios) {
            if (d.regionMatches(true, 0, "Digest", 0, 6)) digest = d;
            else if (d.regionMatches(true, 0, "Basic", 0, 5)) basic = true;
        }

        String autorizacion;
        if (digest != null) {
            autorizacion = respuestaDigest(digest, metodo, ruta);
        } else if (basic) {
            autorizacion = "Basic " + Base64.getEncoder()
                    .encodeToString((usuario + ":" + password).getBytes(StandardCharsets.UTF_8));
        } else {
            return new Respuesta(401, r.body());
        }

        HttpResponse<String> r2 = http.send(armar(metodo, ruta, cuerpo, tipo, autorizacion), cuerpoUtf8());
        return new Respuesta(r2.statusCode(), r2.body());
    }

    private HttpRequest armar(String metodo, String ruta, String cuerpo, String tipo, String autorizacion) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + ruta)).timeout(Duration.ofSeconds(20));
        if (tipo != null) b.header("Content-Type", tipo);
        if (autorizacion != null) b.header("Authorization", autorizacion);
        b.method(metodo, cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8));
        return b.build();
    }

    private static HttpResponse.BodyHandler<String> cuerpoUtf8() {
        return HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
    }

    /** RFC 2617 / 7616 con MD5 (lo que usan los equipos Hikvision). */
    String respuestaDigest(String desafio, String metodo, String uri) {
        Map<String, String> p = new HashMap<>();
        Matcher m = PARAM.matcher(desafio.substring(6));
        while (m.find()) {
            p.put(m.group(1).toLowerCase(), m.group(2) != null ? m.group(2) : m.group(3));
        }
        String realm = p.getOrDefault("realm", "");
        String nonce = p.getOrDefault("nonce", "");
        String opaque = p.get("opaque");
        String qopOfrecido = p.getOrDefault("qop", "");
        boolean qopAuth = false;
        for (String q : qopOfrecido.split(",")) {
            if (q.trim().equalsIgnoreCase("auth")) qopAuth = true;
        }

        String ha1 = md5(usuario + ":" + realm + ":" + password);
        String ha2 = md5(metodo + ":" + uri);
        String nc = "00000001";
        byte[] cn = new byte[8];
        RANDOM.nextBytes(cn);
        String cnonce = toHex(cn);
        String response = qopAuth
                ? md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":auth:" + ha2)
                : md5(ha1 + ":" + nonce + ":" + ha2);

        StringBuilder sb = new StringBuilder("Digest ");
        sb.append("username=\"").append(usuario).append("\", ");
        sb.append("realm=\"").append(realm).append("\", ");
        sb.append("nonce=\"").append(nonce).append("\", ");
        sb.append("uri=\"").append(uri).append("\", ");
        sb.append("algorithm=MD5, ");
        sb.append("response=\"").append(response).append("\"");
        if (qopAuth) sb.append(", qop=auth, nc=").append(nc).append(", cnonce=\"").append(cnonce).append("\"");
        if (opaque != null) sb.append(", opaque=\"").append(opaque).append("\"");
        return sb.toString();
    }

    static String md5(String s) {
        try {
            return toHex(MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
