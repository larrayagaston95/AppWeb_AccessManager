package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.ComandoReloj;
import com.atomg.accessmanager.model.Reloj;
import com.atomg.accessmanager.repository.ComandoRelojRepository;
import com.atomg.accessmanager.repository.RelojRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/relojes")
@CrossOrigin(origins = "*")
public class RelojController {

    @Autowired
    private RelojRepository relojRepository;

    @Autowired
    private ComandoRelojRepository comandoRelojRepository;

    @PostMapping("/{id}/limpiar-memoria")
    public ResponseEntity<?> limpiarMemoria(@PathVariable Long id, HttpServletRequest request) {
        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) return ResponseEntity.status(401).build();

        Optional<Reloj> relojOpt = relojRepository.findById(id);
        if (relojOpt.isEmpty() || !relojOpt.get().getEmpresa().getId().equals(empresaId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Reloj no encontrado o no pertenece a su empresa."));
        }

        ComandoReloj comando = new ComandoReloj();
        comando.setReloj(relojOpt.get());
        comando.setComando("CLEAR ATTLOG");
        comando.setEjecutado(false);
        comandoRelojRepository.save(comando);

        return ResponseEntity.ok(Map.of("message", "Comando CLEAR ATTLOG encolado correctamente."));
    }
}
