package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.service.MotorCalculoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/calculo")
public class CalculoController {

    @Autowired
    private MotorCalculoService motorCalculoService;

    @GetMapping("/test")
    public ResponseEntity<String> probarCalculo(@RequestParam String legajo, @RequestParam String fecha) {
        LocalDate date = LocalDate.parse(fecha);
        motorCalculoService.procesarDiaEmpleado(legajo, date);
        return ResponseEntity.ok("Cálculo ejecutado exitosamente para el legajo " + legajo + " en la fecha " + fecha + ". Revisa la consola o la base de datos.");
    }
}