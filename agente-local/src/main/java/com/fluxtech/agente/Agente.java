package com.fluxtech.agente;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ciclo principal del agente. En cada vuelta:
 *  1. Descarga de la nube la lista de relojes (si no hay internet, usa la última guardada).
 *  2. Lee la memoria de cada reloj desde la última fichada confirmada (con 10 min de solapamiento).
 *  3. Sube lo pendiente en orden cronológico; solo lo borra de la cola cuando la nube confirma.
 *  4. Avisa a la nube que está vivo y el estado de cada reloj.
 * Ningún error corta el agente: se registra y se reintenta en la próxima vuelta.
 */
public final class Agente {

    public static final String VERSION = "1.0.0";

    static final class RelojConf {
        long id;
        String nombre;
        String numeroSerie;
        String marca;
        String ip;
        String usuario;
        String password;
        LocalDateTime ultimaFichadaNube;

        boolean esHikvision() {
            return marca != null && marca.toLowerCase(Locale.ROOT).contains("hik");
        }

        String host() {
            String h = ip == null ? "" : ip.trim().replaceFirst("^https?://", "");
            int dosPuntos = h.indexOf(':');
            return dosPuntos >= 0 ? h.substring(0, dosPuntos) : h;
        }
    }

    private static final int SOLAPAMIENTO_MIN = 10;
    private static final int TAMANIO_LOTE = 200;

    private final Config config;
    private final Nube nube;
    private final Cola cola;
    private volatile List<RelojConf> relojes = new ArrayList<>();
    private volatile int intervaloSegundos = 60;
    private volatile boolean corriendo = true;

    public Agente(Config config) throws IOException {
        this.config = config;
        this.nube = new Nube(config.servidorUrl, config.clave);
        this.cola = new Cola(config.datos());
        // Sin internet al arrancar, se sigue leyendo los relojes con la última configuración conocida
        cargarConfigGuardada();
    }

    public void ejecutar() throws InterruptedException {
        Log.info("Agente FluxTech " + VERSION + " iniciado. Servidor: " + config.servidorUrl
                + " | Pendientes en cola: " + cola.cantidadPendientes());

        Receptor receptor = null;
        if (config.receptorHabilitado) {
            receptor = new Receptor(config.receptorPuerto, cola, this::relojPorIp);
            try {
                receptor.iniciar();
            } catch (IOException e) {
                Log.error("No se pudo abrir el puerto " + config.receptorPuerto
                        + " para el receptor (¿otro programa lo usa?). Se sigue solo con lectura", e);
                receptor = null;
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> corriendo = false));

        while (corriendo) {
            long inicio = System.currentTimeMillis();
            try {
                ciclo();
            } catch (InterruptedException e) {
                throw e;
            } catch (Throwable t) {
                Log.error("Error inesperado en el ciclo (se reintenta)", t);
            }
            long espera = intervaloSegundos * 1000L - (System.currentTimeMillis() - inicio);
            if (espera > 0) Thread.sleep(espera);
        }

        if (receptor != null) receptor.detener();
        Log.info("Agente detenido.");
    }

    void ciclo() throws IOException, InterruptedException {
        actualizarConfig();

        List<Map<String, Object>> estados = new ArrayList<>();
        if (config.lecturaHabilitada) {
            for (RelojConf r : relojes) {
                estados.add(leerReloj(r));
            }
        }

        subirPendientes();

        Map<String, Object> hb = new LinkedHashMap<>();
        hb.put("version", VERSION);
        hb.put("pendientes", cola.cantidadPendientes());
        hb.put("relojes", estados);
        try {
            nube.heartbeat(hb);
        } catch (IOException e) {
            Log.aviso("Sin conexión con la nube (heartbeat): " + e.getMessage());
        }
    }

    // ── Configuración desde la nube ──────────────────────────────────────────

    private void actualizarConfig() throws InterruptedException {
        try {
            Map<String, Object> cfg = nube.config();
            aplicarConfig(cfg);
            guardarConfig(cfg);
        } catch (Nube.ClaveInvalidaException e) {
            // Se sigue leyendo los relojes con la última configuración: nada se pierde mientras se corrige
            Log.error(e.getMessage());
        } catch (IOException | RuntimeException e) {
            Log.aviso("No se pudo descargar la configuración (se usa la última guardada): " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void aplicarConfig(Map<String, Object> cfg) {
        List<RelojConf> nuevos = new ArrayList<>();
        if (cfg.get("relojes") instanceof List) {
            for (Object o : (List<Object>) cfg.get("relojes")) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> m = (Map<String, Object>) o;
                RelojConf r = new RelojConf();
                r.id = Json.numero(m, "id", -1);
                r.nombre = Json.texto(m, "nombre");
                r.numeroSerie = Json.texto(m, "numeroSerie");
                r.marca = Json.texto(m, "marca");
                r.ip = Json.texto(m, "ip");
                r.usuario = Json.texto(m, "usuario");
                r.password = Json.texto(m, "password");
                r.ultimaFichadaNube = Fichada.parsearFecha(Json.texto(m, "ultimaFichada"));
                if (r.id > 0) nuevos.add(r);
            }
        }
        relojes = nuevos;
        long intervalo = config.intervaloSegundos > 0 ? config.intervaloSegundos : Json.numero(cfg, "intervaloSegundos", 60);
        intervaloSegundos = (int) Math.max(15, Math.min(3600, intervalo));
    }

    /** Se guarda la última configuración para seguir leyendo relojes aunque no haya internet. */
    private void guardarConfig(Map<String, Object> cfg) {
        try {
            Files.writeString(config.datos().resolve("config-cache.json"), Json.escribir(cfg), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.aviso("No se pudo guardar config-cache.json: " + e.getMessage());
        }
    }

    private void cargarConfigGuardada() {
        Path p = config.datos().resolve("config-cache.json");
        if (!Files.exists(p)) return;
        try {
            aplicarConfig(Json.parseObjeto(Files.readString(p, StandardCharsets.UTF_8)));
            Log.info("Configuración guardada cargada: " + relojes.size() + " reloj(es)");
        } catch (IOException | RuntimeException e) {
            Log.aviso("config-cache.json ilegible; se espera a la nube: " + e.getMessage());
        }
    }

    Long relojPorIp(String ip) {
        for (RelojConf r : relojes) {
            if (!r.host().isEmpty() && r.host().equals(ip)) return r.id;
        }
        return null;
    }

    // ── Lectura de relojes ───────────────────────────────────────────────────

    private Map<String, Object> leerReloj(RelojConf r) throws InterruptedException {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("id", r.id);

        if (!r.esHikvision()) {
            estado.put("ok", false);
            estado.put("error", "Marca '" + r.marca + "' todavía no soportada por el agente");
            return estado;
        }
        if (r.ip == null || r.ip.isBlank() || r.usuario.isBlank() || r.password.isBlank()) {
            estado.put("ok", false);
            estado.put("error", "Faltan IP, usuario o contraseña del reloj en el panel");
            return estado;
        }

        HikvisionIsapi isapi = new HikvisionIsapi(r.ip, r.usuario, r.password);
        try {
            HikvisionIsapi.InfoEquipo info = isapi.info();
            estado.put("serialDetectado", info.serie);
            if (!coincideSerie(r.numeroSerie, info.serie)) {
                // La IP apunta a otro equipo: leerlo cargaría fichadas en el reloj equivocado
                estado.put("ok", false);
                estado.put("error", "En " + r.ip + " hay otro equipo (serie " + info.serie
                        + ", esperado " + r.numeroSerie + ")");
                Log.aviso("Reloj " + r.nombre + ": " + estado.get("error"));
                return estado;
            }

            LocalDateTime ahora = LocalDateTime.now().withNano(0);
            LocalDateTime desde = desdeParaLeer(r, ahora);
            List<HikvisionIsapi.Evento> eventos = isapi.eventos(desde, ahora.plusMinutes(1));

            List<Fichada> fichadas = new ArrayList<>();
            for (HikvisionIsapi.Evento e : eventos) {
                Log.detalle("Reloj " + r.nombre + " evento: legajo=" + e.legajo + " hora=" + e.fechaHora + " minor=" + e.minor);
                if (HikvisionIsapi.esFichada(e)) fichadas.add(new Fichada(r.id, e.legajo, e.fechaHora, e.modo));
            }
            int nuevas = cola.agregarTodas(fichadas);
            if (nuevas > 0) Log.info("Reloj " + r.nombre + ": " + nuevas + " fichada(s) nueva(s) leída(s)");
            estado.put("ok", true);
        } catch (IOException | RuntimeException e) {
            estado.put("ok", false);
            estado.put("error", mensajeCorto(e));
            Log.aviso("Reloj " + r.nombre + " (" + r.ip + "): " + mensajeCorto(e));
        }
        return estado;
    }

    /** Desde la última fichada confirmada (local o según la nube) menos un margen; la primera vez, N días atrás. */
    LocalDateTime desdeParaLeer(RelojConf r, LocalDateTime ahora) {
        LocalDateTime local = cola.cursor(r.id);
        LocalDateTime base = local;
        if (r.ultimaFichadaNube != null && (base == null || r.ultimaFichadaNube.isAfter(base))) {
            base = r.ultimaFichadaNube;
        }
        if (base == null) return ahora.minusDays(config.diasIniciales);
        LocalDateTime desde = base.minusMinutes(SOLAPAMIENTO_MIN);
        return desde.isAfter(ahora) ? ahora.minusMinutes(SOLAPAMIENTO_MIN) : desde;
    }

    static boolean coincideSerie(String esperado, String detectado) {
        if (esperado == null || esperado.isBlank() || detectado == null || detectado.isBlank()) return true;
        String a = esperado.trim().toUpperCase(Locale.ROOT);
        String b = detectado.trim().toUpperCase(Locale.ROOT);
        // En el panel a veces se carga solo la parte final del número de serie
        return a.contains(b) || b.contains(a);
    }

    // ── Subida a la nube ─────────────────────────────────────────────────────

    void subirPendientes() throws InterruptedException {
        while (cola.cantidadPendientes() > 0) {
            List<Fichada> lote = cola.lote(TAMANIO_LOTE);
            try {
                Map<String, Object> r = nube.subir(lote);
                cola.confirmar(lote);
                Log.info("Subidas " + lote.size() + " fichada(s): guardadas=" + r.get("guardadas")
                        + " duplicadas=" + r.get("duplicadas") + " descartadas=" + r.get("descartadas")
                        + (r.get("avisos") instanceof List && !((List<?>) r.get("avisos")).isEmpty() ? " avisos=" + r.get("avisos") : ""));
            } catch (IOException | RuntimeException e) {
                Log.aviso("No se pudieron subir " + lote.size() + " fichada(s); quedan en cola: " + mensajeCorto(e));
                return;
            }
        }
    }

    static String mensajeCorto(Throwable e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) m = e.getClass().getSimpleName();
        if (e instanceof java.net.ConnectException || e instanceof java.net.http.HttpConnectTimeoutException
                || e instanceof java.net.http.HttpTimeoutException || e instanceof java.net.UnknownHostException
                || e instanceof java.net.NoRouteToHostException) {
            m = "no responde (¿apagado, desconectado o IP incorrecta?)";
        }
        return m.length() > 300 ? m.substring(0, 300) : m;
    }

    // Para el diagnóstico
    List<RelojConf> relojes() { return relojes; }

    void aplicarConfigParaDiagnostico(Map<String, Object> cfg) { aplicarConfig(cfg); }
}
