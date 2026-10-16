package com.fluxtech.agente.cola;

import com.fluxtech.agente.json.EscritorJson;
import com.fluxtech.agente.json.LectorJson;
import com.fluxtech.agente.modelo.FichadaLeida;
import com.fluxtech.agente.util.ConversorDeFechas;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recuerda hasta dónde se leyó cada reloj (archivo datos/estado.json).
 *
 * Guarda dos cosas:
 *   - Por reloj, la hora de la última fichada que la nube confirmó: la próxima lectura arranca desde ahí.
 *   - Las claves de las últimas fichadas confirmadas: como cada lectura repite 10 minutos hacia atrás
 *     (por seguridad), estas claves evitan volver a encolar lo que ya se subió.
 *
 * Recorrido del dato:
 *   ColaDeFichadasPendientes llama a registrarConfirmadas() cuando la nube confirma un lote.
 *   LecturaDeRelojes consulta obtenerUltimaFichadaConfirmada() para calcular desde cuándo leer.
 */
final class EstadoDeLecturaDeRelojes {

    private static final int CANTIDAD_MAXIMA_DE_CLAVES_RECORDADAS = 5000;

    private final Path archivoDeEstado;
    private final Map<Long, LocalDateTime> ultimaFichadaConfirmadaPorReloj = new HashMap<>();
    private final Set<String> clavesConfirmadasRecientemente = new LinkedHashSet<>();

    EstadoDeLecturaDeRelojes(Path carpetaDeDatos) {
        this.archivoDeEstado = carpetaDeDatos.resolve("estado.json");
        cargarDesdeDisco();
    }

    boolean fueConfirmadaRecientemente(String claveUnica) {
        return clavesConfirmadasRecientemente.contains(claveUnica);
    }

    LocalDateTime obtenerUltimaFichadaConfirmada(long idDelReloj) {
        return ultimaFichadaConfirmadaPorReloj.get(idDelReloj);
    }

    /** Anota las fichadas confirmadas por la nube y guarda el estado en disco. */
    void registrarConfirmadas(List<FichadaLeida> fichadasConfirmadas) {
        for (FichadaLeida fichada : fichadasConfirmadas) {
            clavesConfirmadasRecientemente.add(fichada.obtenerClaveUnica());
            // Se queda con la más nueva de cada reloj
            ultimaFichadaConfirmadaPorReloj.merge(fichada.obtenerIdDelReloj(), fichada.obtenerFechaHoraDeLaFichada(),
                    (actual, nueva) -> actual.isAfter(nueva) ? actual : nueva);
        }
        // Se olvidan las claves más viejas para que el archivo no crezca sin límite
        while (clavesConfirmadasRecientemente.size() > CANTIDAD_MAXIMA_DE_CLAVES_RECORDADAS) {
            clavesConfirmadasRecientemente.remove(clavesConfirmadasRecientemente.iterator().next());
        }
        guardarEnDisco();
    }

    private void guardarEnDisco() {
        Map<String, Object> ultimasPorReloj = new LinkedHashMap<>();
        for (Map.Entry<Long, LocalDateTime> entrada : ultimaFichadaConfirmadaPorReloj.entrySet()) {
            ultimasPorReloj.put(String.valueOf(entrada.getKey()), ConversorDeFechas.formatearConSegundos(entrada.getValue()));
        }
        Map<String, Object> estadoCompleto = new LinkedHashMap<>();
        estadoCompleto.put("ultimaFichadaConfirmadaPorReloj", ultimasPorReloj);
        estadoCompleto.put("clavesConfirmadasRecientemente", new ArrayList<>(clavesConfirmadasRecientemente));
        ArchivoConEscrituraSegura.escribir(archivoDeEstado, EscritorJson.convertirEnTexto(estadoCompleto));
    }

    @SuppressWarnings("unchecked")
    private void cargarDesdeDisco() {
        String contenido = ArchivoConEscrituraSegura.leerSiExiste(archivoDeEstado);
        if (contenido == null) {
            return;
        }
        try {
            Map<String, Object> estadoCompleto = LectorJson.convertirEnMapa(contenido);
            Object ultimasPorReloj = estadoCompleto.get("ultimaFichadaConfirmadaPorReloj");
            if (ultimasPorReloj instanceof Map) {
                for (Map.Entry<String, Object> entrada : ((Map<String, Object>) ultimasPorReloj).entrySet()) {
                    LocalDateTime fechaHora = ConversorDeFechas.interpretarFechaDelReloj(String.valueOf(entrada.getValue()));
                    if (fechaHora != null) {
                        ultimaFichadaConfirmadaPorReloj.put(Long.parseLong(entrada.getKey()), fechaHora);
                    }
                }
            }
            Object claves = estadoCompleto.get("clavesConfirmadasRecientemente");
            if (claves instanceof List) {
                for (Object clave : (List<Object>) claves) {
                    clavesConfirmadasRecientemente.add(String.valueOf(clave));
                }
            }
        } catch (RuntimeException archivoDaniado) {
            // Sin estado se relee desde lo que informa la nube; la nube descarta duplicados
            ArchivoConEscrituraSegura.guardarCopiaDeArchivoDaniado(archivoDeEstado);
        }
    }
}
