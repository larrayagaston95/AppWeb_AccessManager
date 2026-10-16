package com.atomg.accessmanager.service;

import com.atomg.accessmanager.dto.FichadaRecibidaDeAgenteDTO;
import com.atomg.accessmanager.dto.ResultadoDelLoteDeFichadasDTO;
import com.atomg.accessmanager.model.Agente;
import com.atomg.accessmanager.model.Empleado;
import com.atomg.accessmanager.model.Fichada;
import com.atomg.accessmanager.model.Reloj;
import com.atomg.accessmanager.repository.EmpleadoRepository;
import com.atomg.accessmanager.repository.FichadaRepository;
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

/**
 * Guarda el lote de fichadas que sube un agente local.
 *
 * Recorrido del dato:
 *   1. AgenteController recibe POST /api/agente/fichadas y llama a procesarLote().
 *   2. ValidadorDeFichadasDeAgente descarta lo inválido y ordena por fecha.
 *   3. Por cada fichada válida, en orden cronológico:
 *        - si ya existe (misma empresa + legajo + segundo) → duplicada (el agente puede reenviar sin riesgo);
 *        - si el legajo no existe en la empresa → descartada;
 *        - si no → AsistenciaService.guardarFichada() la guarda y calcula entrada/salida/horas.
 *   4. Se marca la hora de última conexión de los relojes y se devuelve el resumen al agente.
 */
@Service
public class ProcesadorDeLotesDeFichadasService {

    private static final Logger registro = LoggerFactory.getLogger(ProcesadorDeLotesDeFichadasService.class);
    private static final ZoneId ZONA_HORARIA_DEL_NEGOCIO = ZoneId.of("America/Argentina/Cordoba");

    @Autowired
    private RelojesDelAgenteService relojesDelAgenteService;

    @Autowired
    private ValidadorDeFichadasDeAgente validadorDeFichadas;

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Autowired
    private FichadaRepository fichadaRepository;

    @Autowired
    private RelojRepository relojRepository;

    @Autowired
    private AsistenciaService asistenciaService;

    public ResultadoDelLoteDeFichadasDTO procesarLote(Agente agente, List<FichadaRecibidaDeAgenteDTO> fichadasRecibidas) {
        // Paso 1: relojes del agente indexados por id
        Map<Long, Reloj> relojesDelAgente = new HashMap<>();
        for (Reloj reloj : relojesDelAgenteService.obtenerRelojesDelAgente(agente)) {
            relojesDelAgente.put(reloj.getId(), reloj);
        }

        // Paso 2: validación y orden cronológico
        ValidadorDeFichadasDeAgente.ResultadoDeLaValidacion validacion = validadorDeFichadas.validarLote(fichadasRecibidas, relojesDelAgente);
        List<String> avisos = validacion.avisos();
        int cantidadDescartada = validacion.cantidadDescartada();
        int cantidadGuardada = 0;
        int cantidadDuplicada = 0;
        Long idDeLaEmpresa = agente.getEmpresa().getId();
        // Por id (no Set<Reloj>): Reloj usa Lombok @Data y su hash recorrería relaciones de la base
        Map<Long, Reloj> relojesConFichadas = new HashMap<>();

        // Paso 3: guardar una por una
        for (ValidadorDeFichadasDeAgente.FichadaValidada fichada : validacion.fichadasValidas()) {
            try {
                if (fichadaRepository.existsByIdEmpresaAndLegajoRelojAndFechaHora(idDeLaEmpresa, fichada.legajoDelEmpleado(), fichada.fechaHora())) {
                    cantidadDuplicada++;
                    continue;
                }
                Empleado empleado = empleadoRepository.findByLegajoRelojAndEmpresaId(fichada.legajoDelEmpleado(), idDeLaEmpresa).orElse(null);
                if (empleado == null) {
                    cantidadDescartada++;
                    ValidadorDeFichadasDeAgente.agregarAviso(avisos, "El legajo " + fichada.legajoDelEmpleado() + " no existe en la empresa");
                    continue;
                }
                if (asistenciaService.guardarFichada(construirFichada(idDeLaEmpresa, fichada, empleado)) != null) {
                    cantidadGuardada++;
                } else {
                    cantidadDescartada++; // regla de 5 minutos: doble apoyo del dedo
                }
                relojesConFichadas.put(fichada.reloj().getId(), fichada.reloj());
            } catch (RuntimeException excepcion) {
                // Un error puntual no frena el lote ni provoca reintentos infinitos del agente
                registro.error("Error en ProcesadorDeLotesDeFichadasService.java -> procesarLote: agente {}, legajo {}, {}: {}",
                        agente.getId(), fichada.legajoDelEmpleado(), fichada.fechaHora(), excepcion.getMessage());
                cantidadDescartada++;
                ValidadorDeFichadasDeAgente.agregarAviso(avisos, "Error interno guardando el legajo " + fichada.legajoDelEmpleado());
            }
        }

        // Paso 4: los relojes que mandaron fichadas quedan como "vistos ahora"
        marcarRelojesComoConectados(relojesConFichadas.values());
        return new ResultadoDelLoteDeFichadasDTO(fichadasRecibidas.size(), cantidadGuardada, cantidadDuplicada, cantidadDescartada, avisos);
    }

    /** Arma la entidad Fichada con el sector y la sucursal del empleado (igual que la carga manual). */
    private static Fichada construirFichada(Long idDeLaEmpresa, ValidadorDeFichadasDeAgente.FichadaValidada fichadaValidada, Empleado empleado) {
        Fichada fichada = new Fichada();
        fichada.setIdEmpresa(idDeLaEmpresa);
        fichada.setLegajoReloj(fichadaValidada.legajoDelEmpleado());
        fichada.setFechaHora(fichadaValidada.fechaHora());
        fichada.setModoVerificacion(fichadaValidada.modoDeVerificacion());
        fichada.setReloj(fichadaValidada.reloj());
        fichada.setSector(empleado.getSector());
        if (empleado.getSector() != null) {
            fichada.setSucursal(empleado.getSector().getSucursal());
        }
        return fichada;
    }

    private void marcarRelojesComoConectados(java.util.Collection<Reloj> relojes) {
        LocalDateTime ahora = LocalDateTime.now(ZONA_HORARIA_DEL_NEGOCIO);
        for (Reloj reloj : relojes) {
            try {
                reloj.setUltimaConexion(ahora);
                relojRepository.save(reloj);
            } catch (RuntimeException excepcion) {
                registro.error("Error en ProcesadorDeLotesDeFichadasService.java -> marcarRelojesComoConectados: reloj {}: {}",
                        reloj.getId(), excepcion.getMessage());
            }
        }
    }
}
