package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.Sector;
import com.atomg.accessmanager.repository.SectorRepository;
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

    @GetMapping
    public ResponseEntity<?> listarPorSucursal(@RequestParam(name = "sucursal") String sucursalNombre) {
        List<Sector> sectores = sectorRepository.findBySucursalNombre(sucursalNombre);

        List<Map<String, Object>> resultado = sectores.stream()
                .map(sec -> Map.<String, Object>of(
                        "id", sec.getId(),
                        "nombre", sec.getNombre()
                ))
                .toList();

        return ResponseEntity.ok(resultado);
    }
}