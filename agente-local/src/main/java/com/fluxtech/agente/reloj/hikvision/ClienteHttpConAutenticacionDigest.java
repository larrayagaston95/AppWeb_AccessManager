package com.fluxtech.agente.reloj.hikvision;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Cliente HTTP hacia el reloj en la red local, con autenticación Digest (la que usan los
 * Hikvision por defecto) y Basic como alternativa.
 *
 * Recorrido del dato:
 *   LectorHikvisionIsapi llama a pedirConGet() o enviarJsonConPost().
 *   1. Se envía el pedido sin credenciales.
 *   2. Si el reloj responde 401, se lee el desafío (WWW-Authenticate) y se calcula la respuesta.
 *   3. Se reenvía con la cabecera Authorization y se devuelve código + cuerpo de la respuesta.
 *   Los errores de red se propagan como IOException: quien llama los registra y decide.
 */
final class ClienteHttpConAutenticacionDigest {

    /** Código de estado y cuerpo devueltos por el reloj. */
    static final class RespuestaDelReloj {
        final int codigoDeEstado;
        final String cuerpo;

        RespuestaDelReloj(int codigoDeEstado, String cuerpo) {
            this.codigoDeEstado = codigoDeEstado;
            this.cuerpo = cuerpo;
        }
    }

    private final HttpClient clienteHttp;
    private final String direccionBase;
    private final String usuario;
    private final String contrasenia;

    ClienteHttpConAutenticacionDigest(String direccionEnLaRedLocal, String usuario, String contrasenia) {
        String direccion = direccionEnLaRedLocal.trim();
        boolean yaTieneProtocolo = direccion.startsWith("http://") || direccion.startsWith("https://");
        this.direccionBase = yaTieneProtocolo ? direccion : "http://" + direccion;
        this.usuario = usuario;
        this.contrasenia = contrasenia;
        this.clienteHttp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    RespuestaDelReloj pedirConGet(String ruta) throws IOException, InterruptedException {
        return enviarConAutenticacion("GET", ruta, null, null);
    }

    RespuestaDelReloj enviarJsonConPost(String ruta, String cuerpoJson) throws IOException, InterruptedException {
        return enviarConAutenticacion("POST", ruta, cuerpoJson, "application/json");
    }

    private RespuestaDelReloj enviarConAutenticacion(String metodoHttp, String ruta, String cuerpo, String tipoDeContenido)
            throws IOException, InterruptedException {

        // Paso 1: primer intento sin credenciales (así el reloj indica qué autenticación quiere)
        HttpResponse<String> primeraRespuesta = clienteHttp.send(
                armarPedido(metodoHttp, ruta, cuerpo, tipoDeContenido, null), leerCuerpoComoUtf8());
        if (primeraRespuesta.statusCode() != 401) {
            return new RespuestaDelReloj(primeraRespuesta.statusCode(), primeraRespuesta.body());
        }

        // Paso 2: elegir Digest si el reloj lo ofrece; si no, Basic
        String cabeceraDeAutorizacion = elegirAutenticacion(
                primeraRespuesta.headers().allValues("WWW-Authenticate"), metodoHttp, ruta);
        if (cabeceraDeAutorizacion == null) {
            return new RespuestaDelReloj(401, primeraRespuesta.body());
        }

        // Paso 3: reenviar con credenciales
        HttpResponse<String> respuestaAutenticada = clienteHttp.send(
                armarPedido(metodoHttp, ruta, cuerpo, tipoDeContenido, cabeceraDeAutorizacion), leerCuerpoComoUtf8());
        return new RespuestaDelReloj(respuestaAutenticada.statusCode(), respuestaAutenticada.body());
    }

    /** Devuelve la cabecera Authorization adecuada al desafío, o null si el reloj pide algo desconocido. */
    private String elegirAutenticacion(List<String> desafiosDelReloj, String metodoHttp, String ruta) {
        String desafioDigest = null;
        boolean ofreceBasic = false;
        for (String desafio : desafiosDelReloj) {
            if (desafio.regionMatches(true, 0, "Digest", 0, 6)) {
                desafioDigest = desafio;
            } else if (desafio.regionMatches(true, 0, "Basic", 0, 5)) {
                ofreceBasic = true;
            }
        }
        if (desafioDigest != null) {
            return CalculadorDeAutenticacionDigest.calcularCabecera(desafioDigest, usuario, contrasenia, metodoHttp, ruta);
        }
        if (ofreceBasic) {
            String credenciales = usuario + ":" + contrasenia;
            return "Basic " + Base64.getEncoder().encodeToString(credenciales.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    private HttpRequest armarPedido(String metodoHttp, String ruta, String cuerpo, String tipoDeContenido,
                                    String cabeceraDeAutorizacion) {
        HttpRequest.Builder constructor = HttpRequest.newBuilder(URI.create(direccionBase + ruta))
                .timeout(Duration.ofSeconds(20));
        if (tipoDeContenido != null) {
            constructor.header("Content-Type", tipoDeContenido);
        }
        if (cabeceraDeAutorizacion != null) {
            constructor.header("Authorization", cabeceraDeAutorizacion);
        }
        constructor.method(metodoHttp, cuerpo == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8));
        return constructor.build();
    }

    private static HttpResponse.BodyHandler<String> leerCuerpoComoUtf8() {
        return HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
    }
}
