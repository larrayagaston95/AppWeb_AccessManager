package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.service.HardwareSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/iclock")
public class ZKTecoController {

    @Autowired
    private HardwareSyncService hardwareSyncService;

    /**
     * Endpoint para recibir los fichajes desde los relojes ZKTeco (protocolo ADMS/Push).
     */
    @PostMapping(value = "/cdata", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> recibirFichajes(
            @RequestParam(value = "SN", required = false) String serialNumber,
            @RequestParam(value = "table", required = false) String table,
            @RequestBody(required = false) String datosCrudos) {
            
        // El reloj puede mandar peticiones de "handshake" vacias o con otra info.
        // Si hay datos, los procesamos.
        if (datosCrudos != null && !datosCrudos.isEmpty()) {
            hardwareSyncService.procesarFichajesZKTeco(serialNumber, datosCrudos);
        }
        
        // ZKTeco requiere estrctamente un "OK" en texto plano con status 200
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("OK");
    }
}