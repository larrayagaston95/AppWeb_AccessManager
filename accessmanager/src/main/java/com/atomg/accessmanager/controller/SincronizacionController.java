package com.atomg.accessmanager.controller;

// Importaciones locales corregidas bajo la raíz de atomg
import com.atomg.accessmanager.dto.FichajeDTO;
import com.atomg.accessmanager.model.FichajeCrudo;
import com.atomg.accessmanager.repository.FichajeCrudoRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sincronizacion")
@CrossOrigin(origins = "*")
public class SincronizacionController {

    @Autowired
    private FichajeCrudoRepository fichajeRepository;

    // Simulación de tokens por empresa
    private final Map<String, Long> tokensEmpresas = Map.of(
            "TOKEN-RELOJ-KDL-2026", 1L,
            "TOKEN-RELOJ-CLIENTE2", 2L
    );

    @PostMapping("/subir-fichajes")
    public ResponseEntity<?> recibirFichajes(
            @RequestHeader("X-Gateway-Token") String gatewayToken,
            @RequestBody List<FichajeDTO> listaFichajes
    ) {
        // Validar el Token del emisor
        if (!tokensEmpresas.containsKey(gatewayToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Error: El token del dispositivo no es válido.");
        }

        Long empresaId = tokensEmpresas.get(gatewayToken);
        List<FichajeCrudo> fichajesAGuardar = new ArrayList<>();

        // Mapear DTOs a Entidades de Base de Datos
        for (FichajeDTO dto : listaFichajes) {
            FichajeCrudo nuevoFichaje = new FichajeCrudo(
                    empresaId,
                    dto.getIdEmpleadoRelog(),
                    dto.getFechaHora()
            );
            fichajesAGuardar.add(nuevoFichaje);
        }

        // Guardado masivo eficiente
        fichajeRepository.saveAll(fichajesAGuardar);

        return ResponseEntity.ok("Sincronización exitosa. Procesados: " + listaFichajes.size() + " fichajes.");
    }
}