package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.dto.DashboardResumenDTO;
import com.atomg.accessmanager.service.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoint REST para el Dashboard de asistencia en tiempo real.
 * El empresaId siempre se obtiene del JWT (multi-tenant seguro).
 */
@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    /**
     * GET /api/dashboard/resumen?sucursalId={id}
     *
     * @param sucursalId (opcional) filtra por sucursal. Si es null o 0 -> toda la empresa.
     * @param request    contiene empresaId extraido del JWT por el filtro de seguridad.
     */
    @GetMapping("/resumen")
    public ResponseEntity<?> getResumenHoy(
            @RequestParam(required = false) Long sucursalId,
            HttpServletRequest request) {

        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }

        DashboardResumenDTO resumen = dashboardService.calcularResumenHoy(empresaId, sucursalId);
        return ResponseEntity.ok(resumen);
    }
}