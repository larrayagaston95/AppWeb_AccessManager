package com.atomg.accessmanager.service;

import com.atomg.accessmanager.dto.EstadoDeRelojInformadoDTO;
import com.atomg.accessmanager.model.Agente;
import com.atomg.accessmanager.model.Reloj;
import com.atomg.accessmanager.repository.AgenteRepository;
import com.atomg.accessmanager.repository.RelojRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registra el "estoy vivo" (heartbeat) de cada agente y el estado de sus relojes.
 *
 * Recorrido del dato:
 *   1. El agente envía POST /api/agente/heartbeat en cada ciclo (cada minuto).
 *   2. AgenteController llama a registrarHeartbeat() con la versión, las fichadas pendientes y el
 *      estado de cada reloj.
 *   3. Se actualiza la última conexión del agente (panel: "En línea") y de cada reloj leído bien,
 *      y se guarda un resumen legible con los errores (panel: columna "Detalle").
 */
@Service
public class EstadoDeAgentesService {

    private static final Logger registro = LoggerFactory.getLogger(EstadoDeAgentesService.class);
    private static final ZoneId ZONA_HORARIA_DEL_NEGOCIO = ZoneId.of("America/Argentina/Cordoba");
    private static final int LARGO_MAXIMO_DEL_RESUMEN = 2000;

    @Autowired
    private AgenteRepository agenteRepository;

    @Autowired
    private RelojRepository relojRepository;

    @Autowired
    private RelojesDelAgenteService relojesDelAgenteService;

    public void registrarHeartbeat(Agente agente, String versionDelAgente, int fichadasPendientes,
                                   List<EstadoDeRelojInformadoDTO> estadoDeCadaReloj) {
        // Paso 1: solo se aceptan estados de relojes que pertenecen al agente
        Map<Long, Reloj> relojesDelAgente = new HashMap<>();
        for (Reloj reloj : relojesDelAgenteService.obtenerRelojesDelAgente(agente)) {
            relojesDelAgente.put(reloj.getId(), reloj);
        }

        // Paso 2: actualizar relojes y armar el resumen de errores
        LocalDateTime ahora = LocalDateTime.now(ZONA_HORARIA_DEL_NEGOCIO);
        int cantidadDeRelojesFuncionando = 0;
        StringBuilder detalleDeErrores = new StringBuilder();
        for (EstadoDeRelojInformadoDTO estado : estadoDeCadaReloj) {
            Reloj reloj = relojesDelAgente.get(estado.idDelReloj());
            if (reloj == null) {
                continue;
            }
            if (estado.funcionaCorrectamente()) {
                cantidadDeRelojesFuncionando++;
                guardarUltimaConexionDelReloj(reloj, ahora);
            } else {
                String nombreDelReloj = reloj.getNombre() != null ? reloj.getNombre() : reloj.getNumeroSerie();
                detalleDeErrores.append(" | ").append(nombreDelReloj).append(": ")
                        .append(Objects.toString(estado.descripcionDelError(), "sin respuesta"));
            }
        }

        // Paso 3: estado del agente
        String resumen = cantidadDeRelojesFuncionando + "/" + estadoDeCadaReloj.size() + " relojes OK, "
                + fichadasPendientes + " fichadas pendientes" + detalleDeErrores;
        agente.setUltimaConexion(ahora);
        agente.setVersionAgente(versionDelAgente != null && versionDelAgente.length() <= 32 ? versionDelAgente : null);
        agente.setUltimoEstado(resumen.length() > LARGO_MAXIMO_DEL_RESUMEN ? resumen.substring(0, LARGO_MAXIMO_DEL_RESUMEN) : resumen);
        try {
            agenteRepository.save(agente);
        } catch (RuntimeException excepcion) {
            registro.error("Error en EstadoDeAgentesService.java -> registrarHeartbeat: agente {}: {}", agente.getId(), excepcion.getMessage());
            throw excepcion;
        }
    }

    private void guardarUltimaConexionDelReloj(Reloj reloj, LocalDateTime ahora) {
        try {
            reloj.setUltimaConexion(ahora);
            relojRepository.save(reloj);
        } catch (RuntimeException excepcion) {
            registro.error("Error en EstadoDeAgentesService.java -> guardarUltimaConexionDelReloj: reloj {}: {}",
                    reloj.getId(), excepcion.getMessage());
        }
    }
}
