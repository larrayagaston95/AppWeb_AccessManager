package com.atomg.accessmanager.dto;

/**
 * Una fichada tal como la envía el agente local en POST /api/agente/fichadas.
 *
 * Recorrido del dato:
 *   Agente (PC del cliente) → JSON {"relojId", "legajo", "fechaHora", "modo"}
 *   → AgenteController lo convierte en este DTO
 *   → ValidadorDeFichadasDeAgente lo valida
 *   → ProcesadorDeLotesDeFichadasService lo guarda como Fichada.
 *
 * @param idDelReloj         id del reloj en la base (tiene que pertenecer al agente)
 * @param legajoDelEmpleado  número de empleado tal como está en el reloj
 * @param fechaHoraComoTexto "2026-10-16T08:00:00" (hora de pared del reloj)
 * @param modoDeVerificacion huella, tarjeta, rostro... según el reloj
 */
public record FichadaRecibidaDeAgenteDTO(Long idDelReloj, String legajoDelEmpleado, String fechaHoraComoTexto,
                                         String modoDeVerificacion) {
}
