package com.atomg.accessmanager.service;

import com.atomg.accessmanager.model.Agente;
import com.atomg.accessmanager.model.Empleado;
import com.atomg.accessmanager.model.Fichada;
import com.atomg.accessmanager.model.Reloj;
import com.atomg.accessmanager.repository.AgenteRepository;
import com.atomg.accessmanager.repository.EmpleadoRepository;
import com.atomg.accessmanager.repository.FichadaRepository;
import com.atomg.accessmanager.repository.RelojRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lógica de los agentes locales: qué relojes atiende cada uno y cómo se guardan las
 * fichadas que suben.
 *
 * Reglas al recibir un lote:
 *  - Se procesa en orden cronológico (el emparejamiento entrada/salida depende del orden).
 *  - El reloj tiene que pertenecer al agente (misma empresa y, si el agente tiene sucursal, misma sucursal).
 *  - Una fichada idéntica ya guardada (empresa + legajo + fecha/hora) cuenta como duplicada: el
 *    agente puede reenviar sin riesgo.
 *  - Legajos que no existen en la empresa o fechas imposibles se descartan (no se reintentan).
 */
@Service
public class AgenteService {

    private static final Logger log = LoggerFactory.getLogger(AgenteService.class);
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Cordoba");

    @Autowired
    private AgenteRepository agenteRepository;

    @Autowired
    private RelojRepository relojRepository;

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Autowired
    private FichadaRepository fichadaRepository;

    @Autowired
    private AsistenciaService asistenciaService;

    /** Relojes que atiende el agente: los de su empresa y, si tiene sucursal, solo los de esa sucursal. */
    public List<Reloj> relojesDelAgente(Agente agente) {
        Long empresaId = agente.getEmpresa().getId();
        Long sucursalId = agente.getSucursal() != null ? agente.getSucursal().getIdsucursal() : null;
        return relojRepository.findByEmpresaId(empresaId).stream()
                .filter(r -> sucursalId == null
                        || (r.getSucursal() != null && sucursalId.equals(r.getSucursal().getIdsucursal())))
                .toList();
    }

    /** Fecha/hora de la última fichada guardada de ese reloj (para que el agente sepa desde dónde leer). */
    public LocalDateTime ultimaFichadaDeReloj(Long relojId) {
        return fichadaRepository.findFirstByRelojIdOrderByFechaHoraDesc(relojId)
                .map(Fichada::getFechaHora)
                .orElse(null);
    }

    public record ItemFichada(Long relojId, String legajo, String fechaHora, String modo) {}

    public record ResultadoLote(int recibidas, int guardadas, int duplicadas, int descartadas, List<String> avisos) {}

    public ResultadoLote procesarLote(Agente agente, List<ItemFichada> items) {
        Map<Long, Reloj> relojes = new HashMap<>();
        for (Reloj r : relojesDelAgente(agente)) {
            relojes.put(r.getId(), r);
        }

        record Valida(Reloj reloj, String legajo, LocalDateTime fechaHora, String modo) {}

        List<String> avisos = new ArrayList<>();
        List<Valida> validas = new ArrayList<>();
        int descartadas = 0;
        LocalDateTime limiteFuturo = LocalDateTime.now(ZONA).plusDays(1);

        for (ItemFichada it : items) {
            Reloj reloj = it.relojId() != null ? relojes.get(it.relojId()) : null;
            if (reloj == null) {
                descartadas++;
                agregarAviso(avisos, "Reloj " + it.relojId() + " no pertenece a este agente");
                continue;
            }
            if (it.legajo() == null || it.legajo().isBlank()) {
                descartadas++;
                continue;
            }
            LocalDateTime fechaHora = parsearFecha(it.fechaHora());
            if (fechaHora == null || fechaHora.isAfter(limiteFuturo) || fechaHora.getYear() < 2000) {
                descartadas++;
                agregarAviso(avisos, "Fecha inválida '" + it.fechaHora() + "' (¿reloj con la hora mal configurada?)");
                continue;
            }
            validas.add(new Valida(reloj, it.legajo().trim(), fechaHora.truncatedTo(ChronoUnit.SECONDS),
                    it.modo() != null && !it.modo().isBlank() ? it.modo() : "AGENTE"));
        }

        validas.sort(Comparator.comparing(Valida::fechaHora));

        int guardadas = 0;
        int duplicadas = 0;
        Long empresaId = agente.getEmpresa().getId();
        Map<Long, LocalDateTime> ultimaPorReloj = new LinkedHashMap<>();

        for (Valida v : validas) {
            if (fichadaRepository.existsByIdEmpresaAndLegajoRelojAndFechaHora(empresaId, v.legajo(), v.fechaHora())) {
                duplicadas++;
                continue;
            }

            Empleado empleado = empleadoRepository.findByLegajoRelojAndEmpresaId(v.legajo(), empresaId).orElse(null);
            if (empleado == null) {
                descartadas++;
                agregarAviso(avisos, "Legajo " + v.legajo() + " no existe en la empresa");
                continue;
            }

            Fichada f = new Fichada();
            f.setIdEmpresa(empresaId);
            f.setLegajoReloj(v.legajo());
            f.setFechaHora(v.fechaHora());
            f.setModoVerificacion(v.modo());
            f.setReloj(v.reloj());
            f.setSector(empleado.getSector());
            if (empleado.getSector() != null) {
                f.setSucursal(empleado.getSector().getSucursal());
            }

            try {
                if (asistenciaService.guardarFichada(f) != null) {
                    guardadas++;
                } else {
                    descartadas++; // cooldown de 5 minutos (doble apoyo del dedo)
                }
            } catch (Exception e) {
                // Un error puntual no debe frenar el lote ni provocar reintentos infinitos
                log.error("Error guardando fichada de agente {} (legajo {}, {})", agente.getId(), v.legajo(), v.fechaHora(), e);
                descartadas++;
                agregarAviso(avisos, "Error interno guardando legajo " + v.legajo());
            }
            ultimaPorReloj.merge(v.reloj().getId(), v.fechaHora(), (a, b) -> a.isAfter(b) ? a : b);
        }

        // Marcar los relojes como "vistos"
        LocalDateTime ahora = LocalDateTime.now(ZONA);
        for (Long relojId : ultimaPorReloj.keySet()) {
            Reloj r = relojes.get(relojId);
            r.setUltimaConexion(ahora);
            relojRepository.save(r);
        }

        return new ResultadoLote(items.size(), guardadas, duplicadas, descartadas, avisos);
    }

    public record EstadoReloj(Long id, boolean ok, String error, String serialDetectado) {}

    public void registrarHeartbeat(Agente agente, String version, int pendientes, List<EstadoReloj> estados) {
        Map<Long, Reloj> relojes = new HashMap<>();
        for (Reloj r : relojesDelAgente(agente)) {
            relojes.put(r.getId(), r);
        }

        LocalDateTime ahora = LocalDateTime.now(ZONA);
        int ok = 0;
        StringBuilder errores = new StringBuilder();
        for (EstadoReloj e : estados) {
            Reloj r = relojes.get(e.id());
            if (r == null) continue;
            if (e.ok()) {
                ok++;
                r.setUltimaConexion(ahora);
                relojRepository.save(r);
            } else {
                String nombre = r.getNombre() != null ? r.getNombre() : r.getNumeroSerie();
                errores.append(" | ").append(nombre).append(": ").append(Objects.toString(e.error(), "sin respuesta"));
            }
        }

        String resumen = ok + "/" + estados.size() + " relojes OK, " + pendientes + " fichadas pendientes" + errores;
        agente.setUltimaConexion(ahora);
        agente.setVersionAgente(version != null && version.length() <= 32 ? version : null);
        agente.setUltimoEstado(resumen.length() > 2000 ? resumen.substring(0, 2000) : resumen);
        agenteRepository.save(agente);
    }

    /** Acepta "2026-10-16T08:00:00", con o sin zona ("...-03:00"); la zona se descarta (hora de pared del reloj). */
    static LocalDateTime parsearFecha(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String t = texto.trim().replace(' ', 'T');
        try {
            return OffsetDateTime.parse(t).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(t);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private static void agregarAviso(List<String> avisos, String aviso) {
        if (avisos.size() < 20 && !avisos.contains(aviso)) {
            avisos.add(aviso);
        }
    }
}
