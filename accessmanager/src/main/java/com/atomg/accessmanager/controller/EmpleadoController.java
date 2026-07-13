package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.Empleado;
import com.atomg.accessmanager.repository.EmpleadoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/empleados")
@CrossOrigin(origins = "*")
public class EmpleadoController {

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @GetMapping
    public ResponseEntity<?> listarPorSector(@RequestParam(name = "sectorId") Long sectorId) {
        List<Empleado> empleados = empleadoRepository.findBySectorId(sectorId);

        List<Map<String, Object>> resultado = empleados.stream()
                .map(emp -> Map.<String, Object>of(
                        "id", emp.getId(),
                        "legajo", emp.getLegajoReloj(),
                        "nombre", emp.getNombre(),
                        "apellido", emp.getApellido()
                ))
                .toList();

        return ResponseEntity.ok(resultado);
    }
}