package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.service.HardwareSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/fichadas")
public class FichadaController {

    @Autowired
    private HardwareSyncService hardwareSyncService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFichadas(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sucursalId") Long sucursalId,
            @RequestParam("sectorId") Long sectorId,
            @RequestParam("marca") String marca) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("El archivo está vacío.");
        }

        try {
            int procesadas = hardwareSyncService.procesarArchivoFichadasOffline(file, sucursalId, sectorId, marca);
            return ResponseEntity.ok("Se procesaron " + procesadas + " fichadas exitosamente.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error procesando el archivo: " + e.getMessage());
        }
    }
}
