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

@RestController
@RequestMapping("/api/v1/sincronizacion")
@CrossOrigin(origins = "*")
public class SincronizacionController {

    @Autowired
    private FichajeCrudoRepository fichajeRepository;

    // Simulación de tokens por empresa
    // El token del gateway es el api_token de la empresa (tabla empresas), no un valor fijo en el código
    @Autowired
    private com.atomg.accessmanager.repository.EmpresaRepository empresaRepository;

    @PostMapping("/subir-fichajes")
    public ResponseEntity<?> recibirFichajes(
            @RequestHeader("X-Gateway-Token") String gatewayToken,
            @RequestBody List<FichajeDTO> listaFichajes
    ) {
        // Validar el Token del emisor
        java.util.Optional<com.atomg.accessmanager.model.Empresa> empresaOpt =
                gatewayToken == null ? java.util.Optional.empty() : empresaRepository.findByApiToken(gatewayToken.trim());
        if (empresaOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Error: El token del dispositivo no es válido.");
        }

        Long empresaId = empresaOpt.get().getId();
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