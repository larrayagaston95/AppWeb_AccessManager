package com.atomg.accessmanager.dto;

import java.util.List;

/**
 * Resumen que la nube devuelve al agente después de procesar un lote de fichadas.
 * El agente borra el lote de su cola al recibir esta respuesta (sin importar cuántas se descartaron:
 * las descartadas no se arreglan reintentando).
 *
 * @param cantidadRecibida    fichadas que venían en el lote
 * @param cantidadGuardada    fichadas nuevas guardadas
 * @param cantidadDuplicada   ya estaban guardadas (reenvío seguro)
 * @param cantidadDescartada  inválidas: reloj ajeno, legajo inexistente, fecha imposible, doble apoyo
 * @param avisos              motivos legibles de los descartes (máximo 20, sin repetir)
 */
public record ResultadoDelLoteDeFichadasDTO(int cantidadRecibida, int cantidadGuardada, int cantidadDuplicada,
                                            int cantidadDescartada, List<String> avisos) {
}
