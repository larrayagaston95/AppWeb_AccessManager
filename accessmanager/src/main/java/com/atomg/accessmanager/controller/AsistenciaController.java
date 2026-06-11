package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.ReporteAsistencia;
import com.atomg.accessmanager.service.AsistenciaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/asistencia")
@CrossOrigin(origins = "*")
public class AsistenciaController {

    @Autowired
    private AsistenciaService asistenciaService;

    // Seteamos la clave maestra del sistema (Hardcodeada temporalmente para el MVP rápido)
    private static final String SECURITY_TOKEN = "AtomgAccess2026";

    @GetMapping("/reporte-mensual")
    public ResponseEntity<?> getReporteMensual(
            @RequestHeader(value = "X-Access-Token", required = false) String token,
            @RequestParam String legajo,
            @RequestParam int anio,
            @RequestParam int mes) {

        // VALIDACIÓN DE SEGURIDAD
        if (token == null || !token.equals(SECURITY_TOKEN)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Acceso denegado: Token inválido o ausente.");
        }

        List<ReporteAsistencia> reporte = asistenciaService.obtenerReporteMensual(legajo, anio, mes);
        return ResponseEntity.ok(reporte);
    }

    // ... Mantené el endpoint /procesar-dia tal cual estaba abajo
}