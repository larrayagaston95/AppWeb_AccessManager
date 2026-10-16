package com.atomg.accessmanager.dto;

/**
 * Estado de un reloj que el agente informa en cada heartbeat (POST /api/agente/heartbeat).
 *
 * @param idDelReloj              id del reloj en la base
 * @param funcionaCorrectamente   true si el agente pudo leerlo en este ciclo
 * @param descripcionDelError     motivo si no se pudo leer (apagado, contraseña, otro equipo en la IP...)
 * @param numeroDeSerieDetectado  número de serie que informó el equipo conectado
 * @param metodoDeLectura         "ISAPI" o "SDK" (cómo se leyó)
 */
public record EstadoDeRelojInformadoDTO(Long idDelReloj, boolean funcionaCorrectamente, String descripcionDelError,
                                        String numeroDeSerieDetectado, String metodoDeLectura) {
}
