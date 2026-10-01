package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.ComandoReloj;
import com.atomg.accessmanager.model.Reloj;
import com.atomg.accessmanager.repository.ComandoRelojRepository;
import com.atomg.accessmanager.repository.RelojRepository;
import com.atomg.accessmanager.repository.EmpresaRepository;
import com.atomg.accessmanager.repository.SucursalRepository;
import com.atomg.accessmanager.repository.SectorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import com.atomg.accessmanager.service.RelojService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/relojes")
@CrossOrigin(origins = "*")
public class RelojController {

    @Autowired
    private RelojRepository relojRepository;

    @Autowired
    private RelojService relojService;

    @Autowired
    private ComandoRelojRepository comandoRelojRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private SucursalRepository sucursalRepository;

    @Autowired
    private SectorRepository sectorRepository;

    @GetMapping
    public ResponseEntity<?> listarRelojes(HttpServletRequest request) {
        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) return ResponseEntity.status(401).build();

        List<Reloj> relojes = relojRepository.findByEmpresaId(empresaId);
        return ResponseEntity.ok(relojes);
    }

    @PostMapping
    public ResponseEntity<?> crearReloj(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) return ResponseEntity.status(401).build();

        // 3. Obtenemos la Empresa vinculada a ese usuario
        com.atomg.accessmanager.model.Empresa empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada"));

        Reloj reloj = new Reloj();
        // 4. Seteamos la empresa al objeto Reloj ANTES de guardarlo
        reloj.setEmpresa(empresa);
        reloj.setNombre(payload.get("nombre"));
        reloj.setMarca(payload.get("marca"));
        reloj.setNumeroSerie(payload.get("numeroSerie"));
        reloj.setDescripcion(payload.get("descripcion") != null ? payload.get("descripcion") : payload.get("nombre"));

        if (payload.containsKey("ip")) reloj.setIp(payload.get("ip"));
        if (payload.containsKey("usuario")) reloj.setUsuario(payload.get("usuario"));
        if (payload.containsKey("password")) reloj.setPassword(payload.get("password"));

        if (payload.containsKey("sucursalId") && payload.get("sucursalId") != null && !payload.get("sucursalId").trim().isEmpty()) {
            reloj.setSucursal(sucursalRepository.findById(Long.parseLong(payload.get("sucursalId"))).orElse(null));
        }
        if (payload.containsKey("sectorId") && payload.get("sectorId") != null && !payload.get("sectorId").trim().isEmpty()) {
            reloj.setSector(sectorRepository.findById(Long.parseLong(payload.get("sectorId"))).orElse(null));
        }

        relojRepository.save(reloj);
        return ResponseEntity.ok(Map.of("message", "Reloj guardado exitosamente.", "reloj", reloj));
    }

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

    @PutMapping("/{id}")
    public ResponseEntity<?> editarReloj(@PathVariable Long id, @RequestBody Map<String, String> payload, HttpServletRequest request) {
        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) return ResponseEntity.status(401).build();

        Optional<Reloj> relojOpt = relojRepository.findById(id);
        if (relojOpt.isEmpty() || !relojOpt.get().getEmpresa().getId().equals(empresaId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Reloj no encontrado o no pertenece a su empresa."));
        }

        Reloj reloj = relojOpt.get();
        if (payload.containsKey("nombre")) reloj.setNombre(payload.get("nombre"));
        if (payload.containsKey("marca")) reloj.setMarca(payload.get("marca"));
        if (payload.containsKey("numeroSerie")) reloj.setNumeroSerie(payload.get("numeroSerie"));
        if (payload.containsKey("descripcion")) reloj.setDescripcion(payload.get("descripcion"));

        if (payload.containsKey("ip")) reloj.setIp(payload.get("ip"));
        if (payload.containsKey("usuario")) reloj.setUsuario(payload.get("usuario"));
        if (payload.containsKey("password")) reloj.setPassword(payload.get("password"));

        if (payload.containsKey("sucursalId")) {
            String sucId = payload.get("sucursalId");
            reloj.setSucursal((sucId != null && !sucId.trim().isEmpty()) ? sucursalRepository.findById(Long.parseLong(sucId)).orElse(null) : null);
        }
        if (payload.containsKey("sectorId")) {
            String secId = payload.get("sectorId");
            reloj.setSector((secId != null && !secId.trim().isEmpty()) ? sectorRepository.findById(Long.parseLong(secId)).orElse(null) : null);
        }

        relojRepository.save(reloj);
        return ResponseEntity.ok(Map.of("message", "Reloj actualizado exitosamente.", "reloj", reloj));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarReloj(@PathVariable Long id, HttpServletRequest request) {
        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) return ResponseEntity.status(401).build();

        Optional<Reloj> relojOpt = relojRepository.findById(id);
        if (relojOpt.isEmpty() || !relojOpt.get().getEmpresa().getId().equals(empresaId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Reloj no encontrado o no pertenece a su empresa."));
        }

        // Antes de eliminar el reloj, limpiamos sus comandos pendientes si existen (depende de cascada, pero por seguridad)
        comandoRelojRepository.deleteAll(comandoRelojRepository.findByRelojId(id));
        relojRepository.delete(relojOpt.get());

        return ResponseEntity.ok(Map.of("message", "Reloj eliminado exitosamente."));
    }

    @PostMapping("/{id}/formatear-hardware")
    public ResponseEntity<?> formatearHardware(@PathVariable Long id, HttpServletRequest request) {
        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) return ResponseEntity.status(401).build();

        try {
            relojService.limpiarMemoriaFisica(id, empresaId);
            return ResponseEntity.ok(Map.of("message", "Hardware físico formateado con éxito vía ISAPI."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
