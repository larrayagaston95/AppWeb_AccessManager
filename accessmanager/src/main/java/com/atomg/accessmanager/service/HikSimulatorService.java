package com.atomg.accessmanager.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HikSimulatorService {

    /**
     * Simula la respuesta JSON cruda del endpoint ISAPI de Hikvision.
     * @param fechaFiltro Formato "YYYY-MM-DD"
     */
    public List<Map<String, Object>> obtenerFichadasSimuladas(String fechaFiltro) {
        List<Map<String, Object>> eventos = new ArrayList<>();

        String emp1 = "101";
        String emp2 = "102";
        String emp3 = "103";

        // --- SIMULACIÓN DE JORNADAS REALISTAS ---
        // Empleado 101: Horario perfecto (08:00 a 16:00 -> 8 horas exactas)
        eventos.add(crearLogFichada(emp1, fechaFiltro + "T07:55:00"));
        eventos.add(crearLogFichada(emp1, fechaFiltro + "T16:00:00"));

        // Empleado 102: Clava HORAS EXTRAS (Entra 08:00, se queda hasta las 19:30 -> 11h 30m)
        eventos.add(crearLogFichada(emp2, fechaFiltro + "T08:00:00"));
        eventos.add(crearLogFichada(emp2, fechaFiltro + "T19:30:00"));

        // Empleado 103: Se olvidó de fichar la salida (Solo registra entrada)
        eventos.add(crearLogFichada(emp3, fechaFiltro + "T07:58:00"));

        return eventos;
    }

    private Map<String, Object> crearLogFichada(String idEmpleado, String timestamp) {
        Map<String, Object> log = new HashMap<>();
        log.put("employeeNoString", idEmpleado);
        log.put("time", timestamp);
        log.put("currentVerifyMode", "fp"); // fp = fingerprint (huella)
        return log;
    }
}