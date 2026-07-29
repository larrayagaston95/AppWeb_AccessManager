package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.ReporteAsistencia;
import com.atomg.accessmanager.service.AsistenciaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/asistencia")
@CrossOrigin(origins = "*")
public class AsistenciaController {

    @Autowired
    private AsistenciaService asistenciaService;

    // Seteamos la clave maestra del sistema (Hardcodeada temporalmente para el MVP rápido)
    private static final String SECURITY_TOKEN = "AtomgAccess2026";

    @GetMapping("/reporte-mensual") // o /mensual según la ruta exacta que tengas en tu controller
    public ResponseEntity<?> obtenerReporteMensual(
            @RequestParam String legajo,
            @RequestParam int anio,
            @RequestParam int mes) {

        List<ReporteAsistencia> reportes = asistenciaService.obtenerReporteMensual(legajo, anio, mes);

        // Convertimos la lista de entidades a un mapa plano seguro para Jackson
        List<Map<String, Object>> respuestaLimpia = reportes.stream().map(rep -> {
            Map<String, Object> fila = new HashMap<>();
            fila.put("id", rep.getId());
            fila.put("fecha", rep.getFecha() != null ? rep.getFecha().toString() : "");
            fila.put("entrada", rep.getEntrada() != null ? rep.getEntrada().toString() : "");
            fila.put("salida", rep.getSalida() != null ? rep.getSalida().toString() : "");
            fila.put("horasTrabajadas", rep.getHorasTrabajadas());
            fila.put("horasExtras", rep.getHorasExtras());
            fila.put("observaciones", rep.getObservaciones());
            return fila;
        }).toList();

        return ResponseEntity.ok(respuestaLimpia);
    }

    // ... Mantené el endpoint /procesar-dia tal cual estaba abajo
}