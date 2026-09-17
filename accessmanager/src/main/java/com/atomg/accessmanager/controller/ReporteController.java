package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.AsistenciaDiaria;
import com.atomg.accessmanager.repository.AsistenciaDiariaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reportes")
@CrossOrigin("*")
public class ReporteController {

    @Autowired
    private AsistenciaDiariaRepository asistenciaDiariaRepository;

    @GetMapping("/asistencia/rango")
    public List<AsistenciaDiaria> obtenerAsistencia(
            @RequestParam Long empresaId,
            @RequestParam String fechaInicio,
            @RequestParam String fechaFin) {
        LocalDate inicio = LocalDate.parse(fechaInicio);
        LocalDate fin = LocalDate.parse(fechaFin);
        return asistenciaDiariaRepository.findByIdEmpresaAndFechaBetweenOrderByFechaDesc(empresaId, inicio, fin);
    }
}