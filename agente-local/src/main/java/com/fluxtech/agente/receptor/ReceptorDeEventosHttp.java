package com.fluxtech.agente.receptor;

import com.fluxtech.agente.cola.ColaDeFichadasPendientes;
import com.fluxtech.agente.modelo.FichadaLeida;
import com.fluxtech.agente.registro.RegistroDeEventos;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Receptor HTTP en la red local para los relojes que ENVÍAN sus eventos (modo tiempo real).
 * En el reloj Hikvision se configura el servidor de eventos / "HTTP Listening" con la IP de esta PC
 * y el puerto del receptor (8095 por defecto).
 *
 * Recorrido del dato:
 *   1. El reloj hace un POST con el evento a http://<IP de la PC>:8095/
 *   2. Se verifica que la IP de origen sea la de un reloj cargado en el panel (si no, se ignora:
 *      así otra PC de la red no puede inventar fichadas).
 *   3. InterpreteDeEventosEnviados extrae la fichada y se agrega a la cola de pendientes.
 *   4. Se responde siempre 200: un error haría que el reloj reintente sin fin.
 */
public final class ReceptorDeEventosHttp {

    private static final String NOMBRE_DEL_ARCHIVO = "ReceptorDeEventosHttp.java";
    private static final int TAMANIO_MAXIMO_DEL_EVENTO_EN_BYTES = 2 * 1024 * 1024;

    private final int puertoDeEscucha;
    private final ColaDeFichadasPendientes colaDePendientes;
    /** Recibe la IP de origen y devuelve el id del reloj con esa IP, o null si no es un reloj conocido. */
    private final Function<String, Long> buscadorDeRelojPorIp;
    private HttpServer servidorHttp;

    public ReceptorDeEventosHttp(int puertoDeEscucha, ColaDeFichadasPendientes colaDePendientes,
                                 Function<String, Long> buscadorDeRelojPorIp) {
        this.puertoDeEscucha = puertoDeEscucha;
        this.colaDePendientes = colaDePendientes;
        this.buscadorDeRelojPorIp = buscadorDeRelojPorIp;
    }

    /** Abre el puerto. Lanza IOException si otro programa lo está usando. */
    public void iniciar() throws IOException {
        try {
            servidorHttp = HttpServer.create(new InetSocketAddress(puertoDeEscucha), 0);
            servidorHttp.createContext("/", this::atenderEvento);
            servidorHttp.setExecutor(Executors.newFixedThreadPool(4));
            servidorHttp.start();
            RegistroDeEventos.informar("Receptor escuchando en el puerto " + puertoDeEscucha + " (relojes que envían sus eventos)");
        } catch (IOException excepcion) {
            RegistroDeEventos.registrarError(NOMBRE_DEL_ARCHIVO, "iniciar",
                    "no se pudo abrir el puerto " + puertoDeEscucha + " (¿otro programa lo usa?): " + excepcion.getMessage());
            throw excepcion;
        }
    }

    public void detener() {
        if (servidorHttp != null) {
            servidorHttp.stop(1);
        }
    }

    /** Atiende un POST del reloj. Pase lo que pase, responde 200 "OK". */
    private void atenderEvento(HttpExchange intercambio) throws IOException {
        try {
            String ipDeOrigen = intercambio.getRemoteAddress().getAddress().getHostAddress();
            String cuerpoDelEvento = leerCuerpo(intercambio.getRequestBody());
            Long idDelReloj = buscadorDeRelojPorIp.apply(ipDeOrigen);

            if (idDelReloj == null) {
                RegistroDeEventos.avisar("Receptor: evento ignorado desde " + ipDeOrigen + " (no es la IP de ningún reloj del panel)");
                return;
            }
            FichadaLeida fichada = InterpreteDeEventosEnviados.interpretar(idDelReloj, cuerpoDelEvento);
            if (fichada == null) {
                RegistroDeEventos.detallar("Receptor: mensaje sin fichada desde " + ipDeOrigen);
            } else if (colaDePendientes.agregarSiEsNueva(fichada)) {
                RegistroDeEventos.informar("Receptor: fichada legajo " + fichada.obtenerLegajoDelEmpleado() + " "
                        + fichada.obtenerFechaHoraDeLaFichada() + " (reloj " + idDelReloj + ")");
            }
        } catch (IOException | RuntimeException excepcion) {
            RegistroDeEventos.registrarError(NOMBRE_DEL_ARCHIVO, "atenderEvento", RegistroDeEventos.describirFallo(excepcion));
        } finally {
            responderOk(intercambio);
        }
    }

    private static void responderOk(HttpExchange intercambio) throws IOException {
        byte[] respuesta = "OK".getBytes(StandardCharsets.UTF_8);
        intercambio.sendResponseHeaders(200, respuesta.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(respuesta);
        }
    }

    /** Lee el cuerpo con un límite de tamaño. Se usa ISO-8859-1 para que una foto adjunta no rompa el texto. */
    private static String leerCuerpo(InputStream entrada) throws IOException {
        ByteArrayOutputStream acumulado = new ByteArrayOutputStream();
        byte[] bloque = new byte[8192];
        int bytesLeidos;
        while ((bytesLeidos = entrada.read(bloque)) > 0) {
            if (acumulado.size() + bytesLeidos > TAMANIO_MAXIMO_DEL_EVENTO_EN_BYTES) {
                throw new IOException("evento demasiado grande");
            }
            acumulado.write(bloque, 0, bytesLeidos);
        }
        return acumulado.toString(StandardCharsets.ISO_8859_1);
    }
}
