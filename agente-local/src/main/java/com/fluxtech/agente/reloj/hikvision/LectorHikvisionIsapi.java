package com.fluxtech.agente.reloj.hikvision;

import com.fluxtech.agente.json.AyudaMapasJson;
import com.fluxtech.agente.json.EscritorJson;
import com.fluxtech.agente.json.LectorJson;
import com.fluxtech.agente.modelo.EventoDeReloj;
import com.fluxtech.agente.modelo.InformacionDelEquipo;
import com.fluxtech.agente.registro.RegistroDeEventos;
import com.fluxtech.agente.reloj.CredencialesDelRelojIncorrectasException;
import com.fluxtech.agente.reloj.LectorDeReloj;
import com.fluxtech.agente.reloj.MetodoDeLecturaNoSoportadoException;
import com.fluxtech.agente.util.ConversorDeFechas;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee un reloj Hikvision por ISAPI (HTTP en la red local, sin instalar nada en la PC).
 *
 * Recorrido del dato:
 *   1. obtenerInformacionDelEquipo(): GET /ISAPI/System/deviceInfo → XML con modelo y número de serie.
 *   2. leerEventosEntre(): POST /ISAPI/AccessControl/AcsEvent?format=json con el rango de fechas.
 *      El reloj devuelve de a 30 eventos; mientras responda "MORE" se pide la página siguiente.
 *   Cada registro se convierte en EventoDeReloj (legajo, hora, código). El filtrado de rechazos
 *   lo hace CodigosDeEventoHikvision, no esta clase.
 */
public final class LectorHikvisionIsapi implements LectorDeReloj {

    private static final String NOMBRE_DEL_ARCHIVO = "LectorHikvisionIsapi.java";
    private static final int EVENTOS_POR_PAGINA = 30;
    private static final int LIMITE_DE_PAGINAS_POR_LECTURA = 5000;
    private static final DateTimeFormatter FORMATO_CON_ZONA_HORARIA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final ClienteHttpConAutenticacionDigest clienteDelReloj;

    public LectorHikvisionIsapi(String direccionEnLaRedLocal, String usuario, String contrasenia) {
        this.clienteDelReloj = new ClienteHttpConAutenticacionDigest(direccionEnLaRedLocal, usuario, contrasenia);
    }

    @Override
    public String obtenerNombreDelMetodo() {
        return "ISAPI";
    }

    @Override
    public InformacionDelEquipo obtenerInformacionDelEquipo() throws IOException, InterruptedException {
        try {
            ClienteHttpConAutenticacionDigest.RespuestaDelReloj respuesta = clienteDelReloj.pedirConGet("/ISAPI/System/deviceInfo");
            verificarCodigoDeEstado(respuesta, "deviceInfo");
            return new InformacionDelEquipo(extraerEtiquetaXml(respuesta.cuerpo, "model"),
                    extraerEtiquetaXml(respuesta.cuerpo, "serialNumber"),
                    extraerEtiquetaXml(respuesta.cuerpo, "firmwareVersion"));
        } catch (IOException | RuntimeException excepcion) {
            RegistroDeEventos.registrarError(NOMBRE_DEL_ARCHIVO, "obtenerInformacionDelEquipo", RegistroDeEventos.describirFallo(excepcion));
            throw excepcion;
        }
    }

    @Override
    public List<EventoDeReloj> leerEventosEntre(LocalDateTime desde, LocalDateTime hasta) throws IOException, InterruptedException {
        try {
            return leerTodasLasPaginas(desde, hasta);
        } catch (IOException | RuntimeException excepcion) {
            RegistroDeEventos.registrarError(NOMBRE_DEL_ARCHIVO, "leerEventosEntre", RegistroDeEventos.describirFallo(excepcion));
            throw excepcion;
        }
    }

    /** Pide página por página hasta que el reloj deja de responder "MORE". */
    private List<EventoDeReloj> leerTodasLasPaginas(LocalDateTime desde, LocalDateTime hasta) throws IOException, InterruptedException {
        List<EventoDeReloj> eventosLeidos = new ArrayList<>();
        String identificadorDeBusqueda = UUID.randomUUID().toString();
        int posicionEnElResultado = 0;

        for (int numeroDePagina = 0; numeroDePagina < LIMITE_DE_PAGINAS_POR_LECTURA; numeroDePagina++) {
            // Paso 1: pedir la página (misma búsqueda, avanzando la posición)
            String condicion = armarCondicionDeBusqueda(identificadorDeBusqueda, posicionEnElResultado, desde, hasta);
            ClienteHttpConAutenticacionDigest.RespuestaDelReloj respuesta =
                    clienteDelReloj.enviarJsonConPost("/ISAPI/AccessControl/AcsEvent?format=json", condicion);
            verificarCodigoDeEstado(respuesta, "búsqueda de eventos");

            // Paso 2: convertir los registros de esta página
            Map<String, Object> resultadoDeLaPagina = extraerResultado(respuesta.cuerpo);
            int registrosEnLaPagina = agregarEventosDeLaPagina(resultadoDeLaPagina, eventosLeidos);

            // Paso 3: ¿hay más páginas?
            boolean hayMasPaginas = "MORE".equalsIgnoreCase(AyudaMapasJson.obtenerTexto(resultadoDeLaPagina, "responseStatusStrg"));
            if (!hayMasPaginas || registrosEnLaPagina == 0) {
                break;
            }
            posicionEnElResultado += registrosEnLaPagina;
        }
        return eventosLeidos;
    }

    /** JSON de AcsEventCond: tipo principal 5 (eventos de acceso), todos los sub-tipos, rango de fechas. */
    private String armarCondicionDeBusqueda(String identificadorDeBusqueda, int posicion, LocalDateTime desde, LocalDateTime hasta) {
        ZoneId zonaHorariaDeLaPc = ZoneId.systemDefault();
        Map<String, Object> condicion = new LinkedHashMap<>();
        condicion.put("searchID", identificadorDeBusqueda);
        condicion.put("searchResultPosition", posicion);
        condicion.put("maxResults", EVENTOS_POR_PAGINA);
        condicion.put("major", CodigosDeEventoHikvision.TIPO_PRINCIPAL_EVENTO_DE_ACCESO);
        condicion.put("minor", 0);
        condicion.put("startTime", FORMATO_CON_ZONA_HORARIA.format(ZonedDateTime.of(desde, zonaHorariaDeLaPc)));
        condicion.put("endTime", FORMATO_CON_ZONA_HORARIA.format(ZonedDateTime.of(hasta, zonaHorariaDeLaPc)));
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("AcsEventCond", condicion);
        return EscritorJson.convertirEnTexto(cuerpo);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extraerResultado(String cuerpoDeLaRespuesta) throws IOException {
        Object resultado = LectorJson.convertirEnMapa(cuerpoDeLaRespuesta).get("AcsEvent");
        if (!(resultado instanceof Map)) {
            throw new IOException("respuesta inesperada del reloj (falta AcsEvent)");
        }
        return (Map<String, Object>) resultado;
    }

    /** Agrega los eventos de la página a la lista y devuelve cuántos registros traía la página. */
    @SuppressWarnings("unchecked")
    private int agregarEventosDeLaPagina(Map<String, Object> resultadoDeLaPagina, List<EventoDeReloj> eventosLeidos) {
        Object listaDeRegistros = resultadoDeLaPagina.get("InfoList");
        if (!(listaDeRegistros instanceof List)) {
            return 0;
        }
        int cantidadDeRegistros = 0;
        for (Object registro : (List<Object>) listaDeRegistros) {
            cantidadDeRegistros++;
            if (registro instanceof Map) {
                EventoDeReloj evento = convertirRegistroEnEvento((Map<String, Object>) registro);
                if (evento != null) {
                    eventosLeidos.add(evento);
                }
            }
        }
        return cantidadDeRegistros;
    }

    /** Un registro de InfoList → EventoDeReloj. Devuelve null si no trae una hora válida. */
    private EventoDeReloj convertirRegistroEnEvento(Map<String, Object> registro) {
        String legajo = AyudaMapasJson.obtenerTexto(registro, "employeeNoString");
        if (legajo.isBlank()) {
            legajo = AyudaMapasJson.obtenerTexto(registro, "employeeNo"); // firmwares viejos usan el campo numérico
        }
        LocalDateTime fechaHora = ConversorDeFechas.interpretarFechaDelReloj(AyudaMapasJson.obtenerTexto(registro, "time"));
        if (fechaHora == null) {
            return null;
        }
        String modo = AyudaMapasJson.obtenerTexto(registro, "currentVerifyMode");
        return new EventoDeReloj(legajo, fechaHora, AyudaMapasJson.obtenerNumero(registro, "minor", -1),
                modo.isBlank() ? "HIKVISION" : modo);
    }

    /** Traduce los códigos HTTP del reloj a errores con significado de negocio. */
    private static void verificarCodigoDeEstado(ClienteHttpConAutenticacionDigest.RespuestaDelReloj respuesta, String operacion)
            throws IOException {
        if (respuesta.codigoDeEstado == 200) return;
        if (respuesta.codigoDeEstado == 401) throw new CredencialesDelRelojIncorrectasException();
        if (respuesta.codigoDeEstado == 403 || respuesta.codigoDeEstado == 404) {
            throw new MetodoDeLecturaNoSoportadoException("el reloj no permite " + operacion + " por ISAPI (HTTP "
                    + respuesta.codigoDeEstado + ")");
        }
        throw new IOException("el reloj respondió HTTP " + respuesta.codigoDeEstado + " en " + operacion);
    }

    private static String extraerEtiquetaXml(String xml, String nombreDeLaEtiqueta) {
        if (xml == null) return "";
        Matcher buscador = Pattern.compile("<" + nombreDeLaEtiqueta + ">([^<]*)</" + nombreDeLaEtiqueta + ">").matcher(xml);
        return buscador.find() ? buscador.group(1).trim() : "";
    }
}
