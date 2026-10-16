package com.atomg.accessmanager.service;

import com.atomg.accessmanager.model.Agente;
import com.atomg.accessmanager.model.Fichada;
import com.atomg.accessmanager.model.Reloj;
import com.atomg.accessmanager.repository.FichadaRepository;
import com.atomg.accessmanager.repository.RelojRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Qué relojes atiende cada agente local.
 *
 * Regla de negocio: un agente lee los relojes de SU empresa; si al crearlo se eligió una sucursal,
 * solo los de esa sucursal (cada local tiene su propio agente, porque está en su propia red).
 *
 * Recorrido del dato:
 *   AgenteController (config) y los servicios de fichadas/heartbeat llaman a obtenerRelojesDelAgente().
 *   Se consultan en la base los relojes de la empresa (RelojRepository) y se filtran por sucursal.
 */
@Service
public class RelojesDelAgenteService {

    private static final Logger registro = LoggerFactory.getLogger(RelojesDelAgenteService.class);

    @Autowired
    private RelojRepository relojRepository;

    @Autowired
    private FichadaRepository fichadaRepository;

    /** Relojes de la empresa del agente, filtrados por su sucursal si la tiene. */
    public List<Reloj> obtenerRelojesDelAgente(Agente agente) {
        Long idDeLaEmpresa = agente.getEmpresa().getId();
        Long idDeLaSucursal = agente.getSucursal() != null ? agente.getSucursal().getIdsucursal() : null;
        try {
            return relojRepository.findByEmpresaId(idDeLaEmpresa).stream()
                    .filter(reloj -> idDeLaSucursal == null
                            || (reloj.getSucursal() != null && idDeLaSucursal.equals(reloj.getSucursal().getIdsucursal())))
                    .toList();
        } catch (RuntimeException excepcion) {
            registro.error("Error en RelojesDelAgenteService.java -> obtenerRelojesDelAgente: no se pudieron consultar los relojes "
                    + "de la empresa {}: {}", idDeLaEmpresa, excepcion.getMessage());
            throw excepcion;
        }
    }

    /** Hora de la última fichada guardada de ese reloj; el agente la usa para saber desde dónde leer. */
    public LocalDateTime obtenerUltimaFichadaDelReloj(Long idDelReloj) {
        try {
            return fichadaRepository.findFirstByRelojIdOrderByFechaHoraDesc(idDelReloj)
                    .map(Fichada::getFechaHora)
                    .orElse(null);
        } catch (RuntimeException excepcion) {
            registro.error("Error en RelojesDelAgenteService.java -> obtenerUltimaFichadaDelReloj: reloj {}: {}",
                    idDelReloj, excepcion.getMessage());
            throw excepcion;
        }
    }
}
