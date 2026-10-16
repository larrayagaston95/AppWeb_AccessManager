package com.atomg.accessmanager.service;

import com.atomg.accessmanager.dto.FichadaRecibidaDeAgenteDTO;
import com.atomg.accessmanager.model.Reloj;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Reglas de validación de las fichadas que sube un agente, antes de tocar la base de datos.
 *
 * Recorrido del dato:
 *   ProcesadorDeLotesDeFichadasService llama a validarLote() con las fichadas recibidas y los
 *   relojes que pertenecen al agente. Devuelve las válidas, ordenadas de la más vieja a la más
 *   nueva (el emparejamiento entrada/salida depende del orden), y anota los descartes con su motivo.
 *
 * Reglas:
 *   1. El reloj tiene que pertenecer al agente (un agente no puede cargar fichadas en relojes ajenos).
 *   2. El legajo no puede estar vacío.
 *   3. La fecha tiene que ser real: posterior a 2000 y no más de un día en el futuro (reloj mal configurado).
 */
@Component
public class ValidadorDeFichadasDeAgente {

    private static final ZoneId ZONA_HORARIA_DEL_NEGOCIO = ZoneId.of("America/Argentina/Cordoba");
    private static final int CANTIDAD_MAXIMA_DE_AVISOS = 20;

    /** Fichada que pasó las validaciones, lista para guardar. */
    public record FichadaValidada(Reloj reloj, String legajoDelEmpleado, LocalDateTime fechaHora, String modoDeVerificacion) {
    }

    /** Resultado de la validación: fichadas válidas en orden cronológico + descartes con sus motivos. */
    public record ResultadoDeLaValidacion(List<FichadaValidada> fichadasValidas, int cantidadDescartada, List<String> avisos) {
    }

    public ResultadoDeLaValidacion validarLote(List<FichadaRecibidaDeAgenteDTO> fichadasRecibidas, Map<Long, Reloj> relojesDelAgente) {
        List<FichadaValidada> fichadasValidas = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        int cantidadDescartada = 0;
        LocalDateTime limiteDeFechaFutura = LocalDateTime.now(ZONA_HORARIA_DEL_NEGOCIO).plusDays(1);

        for (FichadaRecibidaDeAgenteDTO fichada : fichadasRecibidas) {
            // Regla 1: el reloj pertenece al agente
            Reloj reloj = fichada.idDelReloj() != null ? relojesDelAgente.get(fichada.idDelReloj()) : null;
            if (reloj == null) {
                cantidadDescartada++;
                agregarAviso(avisos, "El reloj " + fichada.idDelReloj() + " no pertenece a este agente");
                continue;
            }
            // Regla 2: legajo presente
            if (fichada.legajoDelEmpleado() == null || fichada.legajoDelEmpleado().isBlank()) {
                cantidadDescartada++;
                continue;
            }
            // Regla 3: fecha real
            LocalDateTime fechaHora = interpretarFecha(fichada.fechaHoraComoTexto());
            if (fechaHora == null || fechaHora.isAfter(limiteDeFechaFutura) || fechaHora.getYear() < 2000) {
                cantidadDescartada++;
                agregarAviso(avisos, "Fecha inválida '" + fichada.fechaHoraComoTexto() + "' (¿reloj con la hora mal configurada?)");
                continue;
            }
            String modo = fichada.modoDeVerificacion() != null && !fichada.modoDeVerificacion().isBlank()
                    ? fichada.modoDeVerificacion() : "AGENTE";
            fichadasValidas.add(new FichadaValidada(reloj, fichada.legajoDelEmpleado().trim(),
                    fechaHora.truncatedTo(ChronoUnit.SECONDS), modo));
        }

        fichadasValidas.sort(Comparator.comparing(FichadaValidada::fechaHora));
        return new ResultadoDeLaValidacion(fichadasValidas, cantidadDescartada, avisos);
    }

    /** Agrega un aviso sin repetir y con tope, para que la respuesta no crezca sin límite. */
    public static void agregarAviso(List<String> avisos, String aviso) {
        if (avisos.size() < CANTIDAD_MAXIMA_DE_AVISOS && !avisos.contains(aviso)) {
            avisos.add(aviso);
        }
    }

    /** Acepta "2026-10-16T08:00:00", con o sin zona ("...-03:00"). La zona se descarta: vale la hora de pared del reloj. */
    static LocalDateTime interpretarFecha(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String textoNormalizado = texto.trim().replace(' ', 'T');
        try {
            return OffsetDateTime.parse(textoNormalizado).toLocalDateTime();
        } catch (DateTimeParseException sinZonaHoraria) {
            try {
                return LocalDateTime.parse(textoNormalizado);
            } catch (DateTimeParseException formatoInvalido) {
                return null;
            }
        }
    }
}
