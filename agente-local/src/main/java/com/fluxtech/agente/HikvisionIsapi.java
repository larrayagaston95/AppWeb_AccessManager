package com.fluxtech.agente;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lectura de la memoria de eventos de un Hikvision por ISAPI (red local).
 *
 *  - GET  /ISAPI/System/deviceInfo                 → modelo y número de serie
 *  - POST /ISAPI/AccessControl/AcsEvent?format=json → búsqueda paginada de eventos por rango de fechas
 *
 * Se consideran fichadas los eventos de control de acceso (major 5) que identifican a un
 * empleado (employeeNoString) y no son un rechazo conocido.
 */
public final class HikvisionIsapi {

    public static final class InfoEquipo {
        public final String modelo;
        public final String serie;
        public final String firmware;

        InfoEquipo(String modelo, String serie, String firmware) {
            this.modelo = modelo;
            this.serie = serie;
            this.firmware = firmware;
        }
    }

    /** Evento crudo tal como lo devuelve el reloj (para diagnóstico y filtrado). */
    public static final class Evento {
        public final String legajo;
        public final LocalDateTime fechaHora;
        public final long minor;
        public final String modo;

        Evento(String legajo, LocalDateTime fechaHora, long minor, String modo) {
            this.legajo = legajo;
            this.fechaHora = fechaHora;
            this.minor = minor;
            this.modo = modo;
        }
    }

    /**
     * Sub-eventos (minor, major 5) que son RECHAZOS aunque traigan legajo: tarjeta/huella/rostro
     * no coincide, fuera de horario, etc. (códigos del SDK de Hikvision). Todo otro evento con
     * legajo se toma como fichada. Verificar con "--probar" en cada modelo nuevo: si aparece algún
     * otro rechazo con legajo, se agrega acá.
     */
    static final Set<Long> MINOR_RECHAZOS = new HashSet<>(Arrays.asList(
            3L,   // 0x03 tarjeta + contraseña: falla
            4L,   // 0x04 tarjeta + contraseña: tiempo agotado
            5L,   // 0x05 tarjeta + contraseña: demasiados intentos
            6L,   // 0x06 tarjeta sin permiso
            7L,   // 0x07 tarjeta fuera de período válido
            8L,   // 0x08 tarjeta vencida
            9L,   // 0x09 tarjeta inválida
            39L,  // 0x27 huella no coincide
            76L   // 0x4C rostro no coincide
    ));

    private static final int POR_PAGINA = 30;
    private static final int MAX_PAGINAS = 5000;
    private static final DateTimeFormatter ISO_CON_ZONA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final RelojHttp http;

    public HikvisionIsapi(String ip, String usuario, String password) {
        this.http = new RelojHttp(ip, usuario, password);
    }

    public InfoEquipo info() throws IOException, InterruptedException {
        RelojHttp.Respuesta r = http.get("/ISAPI/System/deviceInfo");
        if (r.estado == 401) throw new IOException("Usuario o contraseña del reloj incorrectos");
        if (r.estado != 200) throw new IOException("El reloj respondió HTTP " + r.estado + " a deviceInfo");
        return new InfoEquipo(xml(r.cuerpo, "model"), xml(r.cuerpo, "serialNumber"), xml(r.cuerpo, "firmwareVersion"));
    }

    /** Todos los eventos con legajo entre desde y hasta (incluye rechazos; filtrar con esFichada). */
    @SuppressWarnings("unchecked")
    public List<Evento> eventos(LocalDateTime desde, LocalDateTime hasta) throws IOException, InterruptedException {
        List<Evento> resultado = new ArrayList<>();
        String searchId = UUID.randomUUID().toString();
        ZoneId zona = ZoneId.systemDefault();
        int posicion = 0;

        for (int pagina = 0; pagina < MAX_PAGINAS; pagina++) {
            Map<String, Object> cond = new LinkedHashMap<>();
            cond.put("searchID", searchId);
            cond.put("searchResultPosition", posicion);
            cond.put("maxResults", POR_PAGINA);
            cond.put("major", 5);
            cond.put("minor", 0);
            cond.put("startTime", ISO_CON_ZONA.format(ZonedDateTime.of(desde, zona)));
            cond.put("endTime", ISO_CON_ZONA.format(ZonedDateTime.of(hasta, zona)));
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("AcsEventCond", cond);

            RelojHttp.Respuesta r = http.postJson("/ISAPI/AccessControl/AcsEvent?format=json", Json.escribir(cuerpo));
            if (r.estado == 401) throw new IOException("Usuario o contraseña del reloj incorrectos");
            if (r.estado == 404 || r.estado == 403) {
                throw new IOException("Este reloj no permite leer eventos por ISAPI (HTTP " + r.estado
                        + "). Usar el modo receptor (el reloj envía al agente).");
            }
            if (r.estado != 200) {
                throw new IOException("El reloj respondió HTTP " + r.estado + " a la búsqueda de eventos: " + recortar(r.cuerpo));
            }

            Map<String, Object> raiz = Json.parseObjeto(r.cuerpo);
            Object acs = raiz.get("AcsEvent");
            if (!(acs instanceof Map)) throw new IOException("Respuesta inesperada del reloj: " + recortar(r.cuerpo));
            Map<String, Object> ev = (Map<String, Object>) acs;
            String estado = Json.texto(ev, "responseStatusStrg");

            int recibidos = 0;
            if (ev.get("InfoList") instanceof List) {
                for (Object o : (List<Object>) ev.get("InfoList")) {
                    recibidos++;
                    if (!(o instanceof Map)) continue;
                    Evento e = convertir((Map<String, Object>) o);
                    if (e != null) resultado.add(e);
                }
            }

            if (!"MORE".equalsIgnoreCase(estado) || recibidos == 0) break;
            posicion += recibidos;
        }
        return resultado;
    }

    public static boolean esFichada(Evento e) {
        return e.legajo != null && !e.legajo.isBlank() && !MINOR_RECHAZOS.contains(e.minor);
    }

    private static Evento convertir(Map<String, Object> m) {
        String legajo = Json.texto(m, "employeeNoString");
        if (legajo.isBlank() && m.get("employeeNo") != null) legajo = Json.texto(m, "employeeNo");
        LocalDateTime fh = Fichada.parsearFecha(Json.texto(m, "time"));
        if (fh == null) return null;
        String modo = Json.texto(m, "currentVerifyMode");
        return new Evento(legajo.trim(), fh, Json.numero(m, "minor", -1), modo.isBlank() ? "HIKVISION" : modo);
    }

    static String xml(String cuerpo, String etiqueta) {
        if (cuerpo == null) return "";
        Matcher m = Pattern.compile("<" + etiqueta + ">([^<]*)</" + etiqueta + ">").matcher(cuerpo);
        return m.find() ? m.group(1).trim() : "";
    }

    private static String recortar(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ");
        return t.length() > 200 ? t.substring(0, 200) + "..." : t;
    }
}
