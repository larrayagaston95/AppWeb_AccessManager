package com.fluxtech.agente;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cola persistente de fichadas pendientes de subir + estado de lectura de cada reloj.
 * Todo se guarda en disco (datos/pendientes.json y datos/estado.json) en cada cambio, con
 * escritura atómica: si se corta la luz no se pierde lo leído ni se corrompe el archivo.
 */
public final class Cola {

    private static final int MAX_RECIENTES = 5000;

    private final Path archivoPendientes;
    private final Path archivoEstado;

    /** clave → fichada, en orden de llegada. */
    private final Map<String, Fichada> pendientes = new LinkedHashMap<>();
    /** Claves ya confirmadas por la nube (para no volver a encolarlas por el solapamiento de lectura). */
    private final Set<String> recientes = new LinkedHashSet<>();
    /** relojId → fecha/hora de la última fichada confirmada por la nube. */
    private final Map<Long, LocalDateTime> cursores = new HashMap<>();

    public Cola(Path datos) throws IOException {
        Files.createDirectories(datos);
        this.archivoPendientes = datos.resolve("pendientes.json");
        this.archivoEstado = datos.resolve("estado.json");
        cargar();
    }

    /** Agrega si no está pendiente ni fue confirmada. Devuelve true si es nueva. */
    public synchronized boolean agregar(Fichada f) {
        String k = f.clave();
        if (pendientes.containsKey(k) || recientes.contains(k)) return false;
        pendientes.put(k, f);
        guardarPendientes();
        return true;
    }

    public synchronized int agregarTodas(List<Fichada> lista) {
        int nuevas = 0;
        for (Fichada f : lista) {
            String k = f.clave();
            if (pendientes.containsKey(k) || recientes.contains(k)) continue;
            pendientes.put(k, f);
            nuevas++;
        }
        if (nuevas > 0) guardarPendientes();
        return nuevas;
    }

    /** Próximo lote a subir, en orden cronológico. */
    public synchronized List<Fichada> lote(int max) {
        List<Fichada> todas = new ArrayList<>(pendientes.values());
        todas.sort(Comparator.comparing((Fichada f) -> f.fechaHora).thenComparing(f -> f.legajo));
        return new ArrayList<>(todas.subList(0, Math.min(max, todas.size())));
    }

    /** La nube confirmó el lote: sacarlo de pendientes y avanzar los cursores. */
    public synchronized void confirmar(List<Fichada> lote) {
        for (Fichada f : lote) {
            String k = f.clave();
            pendientes.remove(k);
            recientes.add(k);
            cursores.merge(f.relojId, f.fechaHora, (a, b) -> a.isAfter(b) ? a : b);
        }
        while (recientes.size() > MAX_RECIENTES) {
            recientes.remove(recientes.iterator().next());
        }
        guardarPendientes();
        guardarEstado();
    }

    public synchronized int cantidadPendientes() {
        return pendientes.size();
    }

    public synchronized LocalDateTime cursor(long relojId) {
        return cursores.get(relojId);
    }

    // ── Persistencia ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void cargar() throws IOException {
        if (Files.exists(archivoPendientes)) {
            try {
                Object v = Json.parse(Files.readString(archivoPendientes, StandardCharsets.UTF_8));
                if (v instanceof List) {
                    for (Object o : (List<Object>) v) {
                        if (!(o instanceof Map)) continue;
                        Fichada f = Fichada.desdeMapa((Map<String, Object>) o);
                        if (f != null) pendientes.put(f.clave(), f);
                    }
                }
            } catch (RuntimeException e) {
                // Archivo dañado: se conserva una copia y se sigue (la nube descarta duplicados al releer)
                Path copia = archivoPendientes.resolveSibling("pendientes.danado-" + System.currentTimeMillis() + ".json");
                Files.copy(archivoPendientes, copia, StandardCopyOption.REPLACE_EXISTING);
                Log.error("pendientes.json estaba dañado; copia guardada en " + copia.getFileName());
            }
        }
        if (Files.exists(archivoEstado)) {
            try {
                Map<String, Object> e = Json.parseObjeto(Files.readString(archivoEstado, StandardCharsets.UTF_8));
                if (e.get("cursores") instanceof Map) {
                    for (Map.Entry<String, Object> c : ((Map<String, Object>) e.get("cursores")).entrySet()) {
                        LocalDateTime fh = Fichada.parsearFecha(String.valueOf(c.getValue()));
                        if (fh != null) cursores.put(Long.parseLong(c.getKey()), fh);
                    }
                }
                if (e.get("recientes") instanceof List) {
                    for (Object o : (List<Object>) e.get("recientes")) recientes.add(String.valueOf(o));
                }
            } catch (RuntimeException ex) {
                Log.error("estado.json dañado; se reconstruye desde la nube", ex);
            }
        }
    }

    private void guardarPendientes() {
        List<Object> lista = new ArrayList<>();
        for (Fichada f : pendientes.values()) lista.add(f.aMapa());
        escribirAtomico(archivoPendientes, Json.escribir(lista));
    }

    private void guardarEstado() {
        Map<String, Object> c = new LinkedHashMap<>();
        for (Map.Entry<Long, LocalDateTime> e : cursores.entrySet()) c.put(String.valueOf(e.getKey()), e.getValue().toString());
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("cursores", c);
        estado.put("recientes", new ArrayList<>(recientes));
        escribirAtomico(archivoEstado, Json.escribir(estado));
    }

    private static void escribirAtomico(Path destino, String contenido) {
        Path tmp = destino.resolveSibling(destino.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, contenido, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Log.error("No se pudo guardar " + destino.getFileName(), e);
        }
    }
}
