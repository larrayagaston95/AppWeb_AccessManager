package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.dto.ResumenSectorDTO;
import com.atomg.accessmanager.model.Sector;
import com.atomg.accessmanager.repository.SectorRepository;
import com.atomg.accessmanager.service.AsistenciaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sectores")
@CrossOrigin(origins = "*")
public class SectorController {

    @Autowired
    private SectorRepository sectorRepository;

    @Autowired
    private AsistenciaService asistenciaService;

    // ── 1. Lista de sectores por sucursal (cascada de filtros) ────────────────
    /**
     * GET /api/sectores?sucursalId={id}
     * Devuelve los sectores que pertenecen a la sucursal indicada por ID numérico.
     */
    @GetMapping
    public ResponseEntity<?> listarPorSucursal(@RequestParam(name = "sucursalId") Long sucursalId) {
        List<Sector> sectores = sectorRepository.findBySucursalIdsucursal(sucursalId);

        List<Map<String, Object>> resultado = sectores.stream()
                .map(sec -> Map.<String, Object>of(
                        "id",     sec.getId(),
                        "nombre", sec.getNombre()
                ))
                .toList();

        return ResponseEntity.ok(resultado);
    }

    // ── 2. Resumen agregado del sector (vista consolidada en la grilla) ────────
    /**
     * GET /api/sectores/resumen?sectorId={id}&anio={año}&mes={mes}
     * Devuelve un registro por empleado con sus totales de asistencia del período.
     * Respuesta: [{ legajo, nombre, apellido, diasTrabajados, totalHoras, totalExtras }]
     */
    @GetMapping("/resumen")
    public ResponseEntity<?> obtenerResumenSector(
            @RequestParam(name = "sectorId") Long sectorId,
            @RequestParam(name = "anio")     int  anio,
            @RequestParam(name = "mes")      int  mes) {

        List<ResumenSectorDTO> resumen = asistenciaService.obtenerResumenSector(sectorId, anio, mes);

        // Convertimos a mapas planos para no exponer detalles del DTO internamente
        List<Map<String, Object>> resultado = resumen.stream()
                .map(dto -> Map.<String, Object>of(
                        "legajo",         dto.getLegajo(),
                        "nombre",         dto.getNombre(),
                        "apellido",       dto.getApellido(),
                        "diasTrabajados", dto.getDiasTrabajados(),
                        "totalHoras",     dto.getTotalHoras(),
                        "totalExtras",    dto.getTotalExtras()
                ))
                .toList();

        return ResponseEntity.ok(resultado);
    }
}