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
    public org.springframework.http.ResponseEntity<?> obtenerAsistencia(
            @RequestParam String fechaInicio,
            @RequestParam String fechaFin,
            jakarta.servlet.http.HttpServletRequest request) {
        // La empresa sale siempre del JWT, nunca de un parámetro del cliente
        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) {
            return org.springframework.http.ResponseEntity.status(401).build();
        }
        LocalDate inicio = LocalDate.parse(fechaInicio);
        LocalDate fin = LocalDate.parse(fechaFin);
        List<AsistenciaDiaria> datos =
                asistenciaDiariaRepository.findByIdEmpresaAndFechaBetweenOrderByFechaDesc(empresaId, inicio, fin);
        return org.springframework.http.ResponseEntity.ok(datos);
    }
}