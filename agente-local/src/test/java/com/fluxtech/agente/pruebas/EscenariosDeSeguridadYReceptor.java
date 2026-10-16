package com.fluxtech.agente.pruebas;

import com.fluxtech.agente.cola.ColaDeFichadasPendientes;
import com.fluxtech.agente.receptor.ReceptorDeEventosHttp;
import com.fluxtech.agente.util.ConversorDeFechas;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Escenarios de receptor (eventos enviados por el reloj) y de seguridad (otro equipo en la IP,
 * clave revocada, http sin cifrar hacia internet).
 */
final class EscenariosDeSeguridadYReceptor {

    private EscenariosDeSeguridadYReceptor() {
        // Agrupador de escenarios: no se instancia
    }

    static void ejecutar(ContextoDePrueba contexto) throws Exception {
        probarReceptor(contexto);
        probarEquipoDistinto(contexto);
        probarClaveRevocada(contexto);
        probarHttpHaciaInternet(contexto);
    }

    /** 4) El reloj envía un evento multipart desde su IP: solo entra la fichada válida. */
    private static void probarReceptor(ContextoDePrueba contexto) throws Exception {
        ColaDeFichadasPendientes cola = new ColaDeFichadasPendientes(contexto.cargarConfiguracion().obtenerCarpetaDeDatos());
        ReceptorDeEventosHttp receptor = new ReceptorDeEventosHttp(ContextoDePrueba.PUERTO_DEL_RECEPTOR, cola,
                ipDeOrigen -> ipDeOrigen.equals("127.0.0.1") ? 5L : null);
        receptor.iniciar();
        String hora = ConversorDeFechas.formatearConSegundos(contexto.ahora);
        String eventoValido = "--LIMITE\r\nContent-Disposition: form-data; name=\"event_log\"\r\n\r\n"
                + "{\"dateTime\":\"" + hora + "-03:00\",\"eventType\":\"AccessControllerEvent\",\"AccessControllerEvent\":"
                + "{\"majorEventType\":5,\"subEventType\":38,\"employeeNoString\":\"103\"}}\r\n--LIMITE--\r\n";
        int codigoDeRespuesta = enviarAlReceptor(eventoValido);
        enviarAlReceptor("{\"eventType\":\"heartBeat\",\"dateTime\":\"" + hora + "\"}");
        enviarAlReceptor("{\"dateTime\":\"" + hora + "\",\"AccessControllerEvent\":{\"subEventType\":39,\"employeeNoString\":\"104\"}}");
        receptor.detener();
        contexto.verificar("4. El receptor responde 200", codigoDeRespuesta == 200);
        contexto.verificar("4b. Encola solo la fichada válida (no el latido ni el rechazo)", cola.contarPendientes() == 1);
        contexto.ejecutarCicloConAgenteNuevo();
        contexto.verificar("4c. La fichada del receptor llega a la nube",
                contexto.nube.fichadasGuardadas.stream().anyMatch(clave -> clave.startsWith("103|")));
    }

    /** 5) En la IP del panel responde otro equipo: no se lee nada y se informa. */
    private static void probarEquipoDistinto(ContextoDePrueba contexto) throws Exception {
        contexto.nube.numeroDeSerieEnElPanel = "OTRO999";
        contexto.reloj.agregarEvento("101", contexto.ahora, 38);
        int guardadasAntes = contexto.nube.fichadasGuardadas.size();
        contexto.ejecutarCicloConAgenteNuevo();
        contexto.verificar("5. Otro equipo en la IP → no lee y lo informa en el heartbeat",
                contexto.nube.fichadasGuardadas.size() == guardadasAntes && contexto.nube.ultimoHeartbeat.contains("otro equipo"));
        contexto.nube.numeroDeSerieEnElPanel = "AAWR123456789";
        contexto.ejecutarCicloConAgenteNuevo(); // se corrige y se sube lo pendiente
    }

    /** 6) Clave revocada: no sube, pero lo leído queda en la cola; con la clave corregida se sube. */
    private static void probarClaveRevocada(ContextoDePrueba contexto) throws Exception {
        contexto.reloj.agregarEvento("102", contexto.ahora.plusSeconds(1), 38);
        contexto.escribirConfiguracion("fxa_otraclave1_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", "automatico");
        contexto.ejecutarCicloConAgenteNuevo();
        contexto.verificar("6. Clave revocada: lo leído queda en la cola, no se pierde", contexto.contarPendientesEnDisco() == 1);
        contexto.escribirConfiguracion(SimuladorDeNubeFluxTech.CLAVE_VALIDA, "automatico");
        contexto.ejecutarCicloConAgenteNuevo();
        contexto.verificar("6b. Con la clave corregida se sube", contexto.contarPendientesEnDisco() == 0);
    }

    /** 7) La configuración rechaza http:// hacia internet (la clave viajaría sin cifrar). */
    private static void probarHttpHaciaInternet(ContextoDePrueba contexto) throws Exception {
        Path otraCarpeta = Files.createTempDirectory("agente-http");
        Files.writeString(otraCarpeta.resolve("agente.properties"),
                "servidor.url=http://fluxtech.com.ar\nagente.clave=" + SimuladorDeNubeFluxTech.CLAVE_VALIDA + "\n");
        boolean fueRechazada = false;
        try {
            com.fluxtech.agente.configuracion.ConfiguracionLocal.cargarDesdeCarpeta(otraCarpeta);
        } catch (IOException excepcion) {
            fueRechazada = excepcion.getMessage().contains("https");
        }
        contexto.verificar("7. Rechaza http:// hacia internet", fueRechazada);
    }

    private static int enviarAlReceptor(String cuerpo) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + ContextoDePrueba.PUERTO_DEL_RECEPTOR + "/"))
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build();
        return HttpClient.newHttpClient().send(pedido, HttpResponse.BodyHandlers.ofString()).statusCode();
    }
}
