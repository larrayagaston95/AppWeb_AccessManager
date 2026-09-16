package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.Empleado;
import com.atomg.accessmanager.model.Licencia;
import com.atomg.accessmanager.repository.EmpleadoRepository;
import com.atomg.accessmanager.repository.LicenciaRepository;
import com.atomg.accessmanager.service.EmpleadoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * CRUD de Licencias y Vacaciones.
 * Todos los endpoints validan empresa_id del JWT para garantizar multi-tenant.
 *
 * GET  /api/licencias?empleadoId=X
 * POST /api/licencias
 */
@RestController
@RequestMapping("/api/licencias")
@CrossOrigin(origins = "*")
public class LicenciaController {

    @Autowired
    private LicenciaRepository licenciaRepository;

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Autowired
    private EmpleadoService empleadoService;

    // =========================================================================
    // GET /api/licencias?empleadoId=X  — Listar licencias de un empleado
    // =========================================================================
    @GetMapping
    public ResponseEntity<?> listarPorEmpleado(
            @RequestParam Long empleadoId,
            HttpServletRequest request) {

        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }

        // Validacion multi-tenant: el empleado debe pertenecer a la empresa del JWT
        try {
            empleadoService.obtenerPorId(empleadoId, empresaId);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(403).body(Map.of("error", ex.getMessage()));
        }

        List<Licencia> licencias = licenciaRepository
                .findByEmpleadoIdAndEmpresaId(empleadoId, empresaId);

        List<Map<String, Object>> resultado = licencias.stream()
                .map(l -> Map.<String, Object>of(
                        "id",            l.getId(),
                        "fechaInicio",   l.getFechaInicio().toString(),
                        "fechaFin",      l.getFechaFin().toString(),
                        "tipoLicencia",  l.getTipoLicencia(),
                        "observaciones", l.getObservaciones() != null ? l.getObservaciones() : ""
                ))
                .toList();

        return ResponseEntity.ok(resultado);
    }

    // =========================================================================
    // POST /api/licencias  — Crear una nueva licencia
    // Payload: { empleadoId, fechaInicio, fechaFin, tipoLicencia, observaciones? }
    // =========================================================================
    @PostMapping
    public ResponseEntity<?> crearLicencia(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {

        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }

        Object empIdObj = payload.get("empleadoId");
        if (empIdObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "empleadoId es requerido"));
        }
        Long empleadoId = ((Number) empIdObj).longValue();

        // Validacion multi-tenant
        Empleado empleado;
        try {
            empleado = empleadoService.obtenerPorId(empleadoId, empresaId);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(403).body(Map.of("error", ex.getMessage()));
        }

        String fechaInicioStr = (String) payload.get("fechaInicio");
        String fechaFinStr    = (String) payload.get("fechaFin");
        String tipoLicencia   = (String) payload.get("tipoLicencia");

        if (fechaInicioStr == null || fechaFinStr == null || tipoLicencia == null || tipoLicencia.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Faltan campos: fechaInicio, fechaFin, tipoLicencia"));
        }

        LocalDate fechaInicio = LocalDate.parse(fechaInicioStr);
        LocalDate fechaFin    = LocalDate.parse(fechaFinStr);

        if (fechaFin.isBefore(fechaInicio)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "La fecha de fin no puede ser anterior a la de inicio"));
        }

        Licencia licencia = new Licencia();
        licencia.setEmpleado(empleado);
        licencia.setEmpresaId(empresaId);
        licencia.setFechaInicio(fechaInicio);
        licencia.setFechaFin(fechaFin);
        licencia.setTipoLicencia(tipoLicencia);
        licencia.setObservaciones((String) payload.getOrDefault("observaciones", ""));

        licenciaRepository.save(licencia);

        return ResponseEntity.ok(Map.of(
                "message",    "Licencia registrada con exito",
                "id",         licencia.getId(),
                "tipoLicencia", licencia.getTipoLicencia(),
                "fechaInicio", licencia.getFechaInicio().toString(),
                "fechaFin",   licencia.getFechaFin().toString()
        ));
    }
}