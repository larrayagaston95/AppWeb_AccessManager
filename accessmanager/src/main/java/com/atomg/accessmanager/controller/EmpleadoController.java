package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.Empleado;
import com.atomg.accessmanager.service.EmpleadoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/empleados")
@CrossOrigin(origins = "*")
public class EmpleadoController {

    @Autowired
    private EmpleadoService empleadoService;

    @GetMapping
    public ResponseEntity<?> listarEmpleados(
            HttpServletRequest request,
            @RequestParam(name = "sectorId", required = false) Long sectorId) {
        
        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) return ResponseEntity.status(401).build();

        List<Empleado> empleados;
        if (sectorId != null) {
            empleados = empleadoService.obtenerTodos(empresaId).stream()
                    .filter(e -> e.getSector() != null && e.getSector().getId().equals(sectorId))
                    .collect(Collectors.toList());
        } else {
            empleados = empleadoService.obtenerTodos(empresaId);
        }

        List<Map<String, Object>> resultado = empleados.stream()
                .map(emp -> Map.<String, Object>of(
                        "id", emp.getId(),
                        "legajo", emp.getLegajoReloj(),
                        "nombre", emp.getNombre(),
                        "apellido", emp.getApellido(),
                        "horasJornadaBase", emp.getHorasJornadaBase() != null ? emp.getHorasJornadaBase() : 0,
                        "telefono", emp.getTelefono() != null ? emp.getTelefono() : "",
                        "sectorId", emp.getSector() != null ? emp.getSector().getId() : 0,
                        "sectorNombre", emp.getSector() != null ? emp.getSector().getNombre() : "",
                        "sucursalId", (emp.getSector() != null && emp.getSector().getSucursal() != null) ? emp.getSector().getSucursal().getIdsucursal() : 0,
                        "sucursalNombre", (emp.getSector() != null && emp.getSector().getSucursal() != null) ? emp.getSector().getSucursal().getNombre() : ""
                ))
                .toList();

        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id, HttpServletRequest request) {
        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) return ResponseEntity.status(401).build();
        
        try {
            Empleado emp = empleadoService.obtenerPorId(id, empresaId);
            return ResponseEntity.ok(emp);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<?> crearEmpleado(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) return ResponseEntity.status(401).build();
        
        try {
            Empleado emp = empleadoService.crearEmpleado(payload, empresaId);
            return ResponseEntity.ok(Map.of("message", "Empleado creado con éxito", "id", emp.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarEmpleado(@PathVariable Long id, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) return ResponseEntity.status(401).build();
        
        try {
            Empleado emp = empleadoService.actualizarEmpleado(id, payload, empresaId);
            return ResponseEntity.ok(Map.of("message", "Empleado actualizado con éxito", "id", emp.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarEmpleado(@PathVariable Long id, HttpServletRequest request) {
        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) return ResponseEntity.status(401).build();
        
        try {
            empleadoService.eliminarEmpleado(id, empresaId);
            return ResponseEntity.ok(Map.of("message", "Empleado eliminado con éxito"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Devuelve el próximo número de legajo disponible para la empresa del usuario autenticado.
     * Lógica: MAX(legajo_reloj numérico) + 1. Si no hay empleados, retorna 101.
     */
    @GetMapping("/proximo-legajo")
    public ResponseEntity<?> proximoLegajo(HttpServletRequest request) {
        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) return ResponseEntity.status(401).build();

        int proximo = empleadoService.proximoLegajo(empresaId);
        return ResponseEntity.ok(Map.of("proximoLegajo", proximo));
    }
}