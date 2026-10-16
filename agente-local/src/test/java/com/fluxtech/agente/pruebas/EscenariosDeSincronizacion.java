package com.fluxtech.agente.pruebas;

import com.fluxtech.agente.sincronizacion.CicloDeSincronizacion;

/**
 * Escenarios del flujo normal: primera lectura, ciclos sin novedades y corte de la nube con reinicio.
 */
final class EscenariosDeSincronizacion {

    private EscenariosDeSincronizacion() {
        // Agrupador de escenarios: no se instancia
    }

    static void ejecutar(ContextoDePrueba contexto) throws Exception {
        cargarMemoriaDelReloj(contexto);
        long fichadasEsperadas = contexto.reloj.eventosEnMemoria.stream()
                .filter(evento -> !evento[0].isEmpty() && !evento[2].equals("39")).count();

        // 1) Primer ciclo: lee 31 días (con paginación) y sube todo
        CicloDeSincronizacion ciclo = contexto.ejecutarCicloConAgenteNuevo();
        contexto.verificar("1. Primer ciclo sube todas las fichadas válidas (" + fichadasEsperadas + ")",
                contexto.nube.fichadasGuardadas.size() == fichadasEsperadas);
        contexto.verificar("1b. No sube rechazos ni eventos sin legajo",
                contexto.nube.fichadasGuardadas.stream().noneMatch(clave -> clave.startsWith("|")));
        contexto.verificar("1c. Sube en orden cronológico", contexto.nube.seGuardaronEnOrdenCronologico());
        contexto.verificar("1d. Autenticación Digest aceptada por el reloj",
                contexto.reloj.autenticacionesFallidas == 0 && contexto.reloj.pedidosSinCredenciales > 0);
        contexto.verificar("1e. Heartbeat con el reloj OK leído por ISAPI",
                contexto.nube.ultimoHeartbeat.contains("\"ok\":true") && contexto.nube.ultimoHeartbeat.contains("\"metodo\":\"ISAPI\""));

        // 2) Segundo ciclo sin novedades: no reenvía nada
        int guardadasAntes = contexto.nube.fichadasGuardadas.size();
        contexto.nube.duplicadasRecibidas = 0;
        ciclo.ejecutarUnCiclo();
        contexto.verificar("2. Ciclo sin novedades no reenvía ni duplica",
                contexto.nube.fichadasGuardadas.size() == guardadasAntes && contexto.nube.duplicadasRecibidas == 0);

        // 3) Nube caída: lo nuevo queda en disco, sobrevive a un reinicio y se sube al volver
        contexto.reloj.agregarEvento("101", contexto.ahora.minusMinutes(2), 38);
        contexto.reloj.agregarEvento("102", contexto.ahora.minusMinutes(1), 38);
        contexto.nube.caida = true;
        ciclo.ejecutarUnCiclo();
        contexto.verificar("3. Con la nube caída quedan 2 fichadas en la cola", contexto.contarPendientesEnDisco() == 2);
        contexto.nube.caida = false;
        contexto.ejecutarCicloConAgenteNuevo();
        contexto.verificar("3b. Tras reiniciar y volver internet se suben las 2",
                contexto.nube.fichadasGuardadas.size() == guardadasAntes + 2);
        contexto.verificar("3c. La cola queda vacía", contexto.contarPendientesEnDisco() == 0);
    }

    /** 4 días de fichadas de 101 y 102 con rechazos y eventos sin legajo, más 40 viejos para forzar paginación. */
    private static void cargarMemoriaDelReloj(ContextoDePrueba contexto) {
        for (int diasAtras = 3; diasAtras >= 0; diasAtras--) {
            java.time.LocalDateTime inicioDelDia = contexto.ahora.minusDays(diasAtras).withHour(8).withMinute(0).withSecond(0);
            if (inicioDelDia.isAfter(contexto.ahora)) continue;
            contexto.reloj.agregarEvento("101", inicioDelDia, 38);
            contexto.reloj.agregarEvento("102", inicioDelDia.plusMinutes(3), 1);
            contexto.reloj.agregarEvento("101", inicioDelDia.plusMinutes(1), 39);   // huella rechazada
            contexto.reloj.agregarEvento("", inicioDelDia.plusMinutes(2), 39);      // sin legajo
            if (inicioDelDia.plusHours(9).isBefore(contexto.ahora)) {
                contexto.reloj.agregarEvento("101", inicioDelDia.plusHours(9), 38);
                contexto.reloj.agregarEvento("102", inicioDelDia.plusHours(9).plusMinutes(5), 38);
            }
        }
        for (int numero = 0; numero < 40; numero++) {
            contexto.reloj.agregarEvento("9" + numero, contexto.ahora.minusDays(10).plusMinutes(numero * 7L), 38);
        }
    }
}
