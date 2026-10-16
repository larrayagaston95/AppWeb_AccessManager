package com.fluxtech.agente.receptor;

import com.fluxtech.agente.modelo.EventoDeReloj;
import com.fluxtech.agente.modelo.FichadaLeida;
import com.fluxtech.agente.reloj.hikvision.CodigosDeEventoHikvision;
import com.fluxtech.agente.util.ConversorDeFechas;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrae la fichada de un evento que un Hikvision envió por HTTP al receptor.
 *
 * Los Hikvision mandan el evento en JSON, en XML o dentro de un formulario "multipart" (a veces
 * con la foto adjunta). Por eso se buscan los campos con patrones de texto en vez de interpretar
 * el formato completo: funciona igual en los tres casos.
 *
 * Recorrido del dato:
 *   ReceptorDeEventosHttp recibe el cuerpo del pedido y llama a interpretar() con el id del reloj.
 *   Si el evento identifica a un empleado y no es un rechazo → devuelve una FichadaLeida.
 *   Si es un latido, una alarma, una puerta o un rechazo → devuelve null (no se carga).
 */
final class InterpreteDeEventosEnviados {

    private static final Pattern LEGAJO_EN_JSON = Pattern.compile("\"employeeNoString\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern LEGAJO_EN_XML = Pattern.compile("<employeeNoString>([^<]*)</employeeNoString>");
    private static final Pattern LEGAJO_NUMERICO = Pattern.compile("\"employeeNo\"\\s*:\\s*\"?(\\d+)\"?");
    private static final Pattern FECHA_EN_JSON = Pattern.compile("\"dateTime\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern FECHA_EN_XML = Pattern.compile("<dateTime>([^<]+)</dateTime>");
    private static final Pattern FECHA_ALTERNATIVA_EN_JSON = Pattern.compile("\"time\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CODIGO_EN_JSON = Pattern.compile("\"(?:subEventType|minor)\"\\s*:\\s*(\\d+)");
    private static final Pattern CODIGO_EN_XML = Pattern.compile("<(?:subEventType|minor)>(\\d+)</(?:subEventType|minor)>");
    private static final Pattern MODO_EN_JSON = Pattern.compile("\"currentVerifyMode\"\\s*:\\s*\"([^\"]*)\"");

    private InterpreteDeEventosEnviados() {
        // Clase de utilidades: no se instancia
    }

    /** Devuelve la fichada contenida en el evento, o null si el evento no es una fichada válida. */
    static FichadaLeida interpretar(long idDelReloj, String cuerpoDelEvento) {
        if (cuerpoDelEvento == null || cuerpoDelEvento.isEmpty()) {
            return null;
        }
        // Paso 1: sin legajo no es una fichada (latidos, alarmas, aperturas de puerta)
        String legajo = buscarPrimerValor(cuerpoDelEvento, LEGAJO_EN_JSON, LEGAJO_EN_XML, LEGAJO_NUMERICO);
        if (legajo == null || legajo.isBlank()) {
            return null;
        }
        // Paso 2: hora del evento
        LocalDateTime fechaHora = ConversorDeFechas.interpretarFechaDelReloj(
                buscarPrimerValor(cuerpoDelEvento, FECHA_EN_JSON, FECHA_EN_XML, FECHA_ALTERNATIVA_EN_JSON));
        if (fechaHora == null) {
            return null;
        }
        // Paso 3: misma regla de negocio que la lectura de memoria (descartar rechazos)
        String codigoComoTexto = buscarPrimerValor(cuerpoDelEvento, CODIGO_EN_JSON, CODIGO_EN_XML);
        long codigoDeTipoDeEvento = codigoComoTexto != null ? Long.parseLong(codigoComoTexto) : -1;
        String modo = buscarPrimerValor(cuerpoDelEvento, MODO_EN_JSON);
        EventoDeReloj evento = new EventoDeReloj(legajo, fechaHora, codigoDeTipoDeEvento,
                modo == null || modo.isBlank() ? "HIKVISION" : modo);
        if (!CodigosDeEventoHikvision.esFichadaValida(evento)) {
            return null;
        }
        return new FichadaLeida(idDelReloj, evento.obtenerLegajoDelEmpleado(), evento.obtenerFechaHoraDelEvento(),
                evento.obtenerModoDeVerificacion());
    }

    /** Devuelve lo que capture el primer patrón que encuentre algo, o null. */
    private static String buscarPrimerValor(String texto, Pattern... patronesEnOrdenDePreferencia) {
        for (Pattern patron : patronesEnOrdenDePreferencia) {
            Matcher buscador = patron.matcher(texto);
            if (buscador.find()) {
                return buscador.group(1);
            }
        }
        return null;
    }
}
