package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.config.HardwareAuthInterceptor;
import com.atomg.accessmanager.service.HardwareSyncService;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hardware/hikvision")
public class HikvisionController {

    @Autowired
    private HardwareSyncService hardwareSyncService;

    @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> recibirFichajesHikvision(@RequestBody JsonNode payload, HttpServletRequest request) {
        if (payload != null) {
            Long empresaEsperada = (Long) request.getAttribute(HardwareAuthInterceptor.HARDWARE_EMPRESA_ID);
            hardwareSyncService.procesarFichajeHikvision(payload, empresaEsperada);
        }
        return ResponseEntity.ok("OK");
    }
}