package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.Sucursal;
import com.atomg.accessmanager.repository.SucursalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sucursales")
@CrossOrigin(origins = "*")
public class SucursalController {

    @Autowired
    private SucursalRepository sucursalRepository;

    @GetMapping
    public ResponseEntity<?> listarPorEmpresa(@RequestParam(name = "empresaId", defaultValue = "1") Long empresaId) {
        List<Sucursal> sucursales = sucursalRepository.findByEmpresaId(empresaId);

        // .toList() directo al final le dice a Java el tipo exacto sin dar vueltas
        List<Map<String, Object>> resultado = sucursales.stream()
                .map(suc -> Map.<String, Object>of(
                        "id", suc.getIdsucursal(),
                        "nombre", suc.getNombre()
                ))
                .toList();

        return ResponseEntity.ok(resultado);
    }
}