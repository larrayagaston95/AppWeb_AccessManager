package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.Empleado;
import com.atomg.accessmanager.model.Fichada;
import com.atomg.accessmanager.repository.FichadaRepository;
import com.atomg.accessmanager.service.EmpleadoService;
import com.atomg.accessmanager.service.HardwareSyncService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fichadas")
public class FichadaController {

    @Autowired
    private HardwareSyncService hardwareSyncService;

    @Autowired
    private FichadaRepository fichadaRepository;

    @Autowired
    private EmpleadoService empleadoService;

    @GetMapping("/historial")
    public ResponseEntity<?> obtenerHistorial(HttpServletRequest request) {
        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) return ResponseEntity.status(401).build();

        List<Fichada> fichadas = fichadaRepository.findTop100ByIdEmpresaOrderByFechaHoraDesc(empresaId);

        List<Empleado> empleados = empleadoService.obtenerTodos(empresaId);
        Map<String, String> legajoNombreMap = empleados.stream()
                .collect(Collectors.toMap(Empleado::getLegajoReloj, 
                        e -> e.getNombre() + " " + (e.getApellido() != null ? e.getApellido() : ""),
                        (existing, replacement) -> existing));

        List<Map<String, Object>> resultado = fichadas.stream().map(f -> {
            String nombre = legajoNombreMap.getOrDefault(f.getLegajoReloj(), "Desconocido");
            String relojInfo = (f.getReloj() != null) ? 
                    (f.getReloj().getNombre() != null ? f.getReloj().getNombre() : f.getReloj().getNumeroSerie()) 
                    : "N/A";

            return Map.<String, Object>of(
                "id", f.getId() != null ? f.getId() : 0,
                "fechaHora", f.getFechaHora(),
                "legajo", f.getLegajoReloj(),
                "empleadoNombre", nombre,
                "reloj", relojInfo,
                "modo", f.getModoVerificacion() != null ? f.getModoVerificacion() : "Desconocido"
            );
        }).toList();

        return ResponseEntity.ok(resultado);
    }

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
