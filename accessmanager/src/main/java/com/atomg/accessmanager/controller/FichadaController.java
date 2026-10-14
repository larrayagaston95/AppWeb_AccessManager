package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.Empleado;
import com.atomg.accessmanager.model.Fichada;
import com.atomg.accessmanager.repository.EmpleadoRepository;
import com.atomg.accessmanager.repository.FichadaRepository;
import com.atomg.accessmanager.repository.RelojRepository;
import com.atomg.accessmanager.service.EmpleadoService;
import com.atomg.accessmanager.service.HardwareSyncService;
import com.atomg.accessmanager.service.AsistenciaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fichadas")
public class FichadaController {

    private static final Logger log = LoggerFactory.getLogger(FichadaController.class);

    @Autowired
    private HardwareSyncService hardwareSyncService;

    @Autowired
    private FichadaRepository fichadaRepository;

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Autowired
    private RelojRepository relojRepository;

    @Autowired
    private EmpleadoService empleadoService;

    @Autowired
    private AsistenciaService asistenciaService;

    @Autowired
    private com.atomg.accessmanager.repository.EmpresaRepository empresaRepository;

    @Autowired
    private com.atomg.accessmanager.security.TenantGuard tenantGuard;

    /**
     * Registro de fichada corporativa. Público a nivel Spring Security, pero exige que el
     * token (header X-Api-Token, Bearer o parámetro/campo "token") coincida con empresas.api_token.
     * Body: { "legajo": "102", "fechaHora": "2026-10-05T08:00:00" (opcional), "modo": "..." (opcional), "token": "..." }
     */
    @PostMapping
    public ResponseEntity<?> registrarFichada(
            @RequestBody Map<String, Object> body,
            @RequestParam(value = "token", required = false) String tokenParam,
            @RequestHeader(value = "X-Api-Token", required = false) String tokenHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        try {
            String token = tokenHeader;
            if (token == null && authHeader != null && authHeader.startsWith("Bearer ")) token = authHeader.substring(7);
            if (token == null) token = tokenParam;
            if (token == null && body.get("token") != null) token = body.get("token").toString();

            if (token == null || token.isBlank()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token ausente"));
            }

            Optional<com.atomg.accessmanager.model.Empresa> empresaOpt = empresaRepository.findByApiToken(token.trim());
            if (empresaOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token inválido"));
            }

            Object legajo = body.get("legajo") != null ? body.get("legajo") : body.get("legajoReloj");
            if (legajo == null || legajo.toString().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Falta el legajo"));
            }

            LocalDateTime fechaHora = LocalDateTime.now();
            if (body.get("fechaHora") != null) {
                try {
                    fechaHora = LocalDateTime.parse(body.get("fechaHora").toString());
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body(Map.of("error", "fechaHora inválida (use ISO-8601)"));
                }
            }

            Fichada f = new Fichada();
            f.setIdEmpresa(empresaOpt.get().getId());
            f.setLegajoReloj(legajo.toString().trim());
            f.setFechaHora(fechaHora);
            f.setModoVerificacion(body.get("modo") != null ? body.get("modo").toString() : "API");

            Optional<Empleado> empOpt = empleadoRepository.findByLegajoRelojAndEmpresaId(f.getLegajoReloj(), f.getIdEmpresa());
            if (empOpt.isEmpty()) {
                log.warn("Fichada ignorada: Empleado con legajo {} no encontrado para la empresa {}",
                        f.getLegajoReloj(), f.getIdEmpresa());
                // 200 OK a propósito: un 4xx/5xx haría que el reloj reintente en bucle infinito
                return ResponseEntity.ok(Map.of(
                        "status", "ignorada",
                        "mensaje", "Fichada ignorada: empleado no encontrado",
                        "legajo", f.getLegajoReloj(),
                        "empresaId", f.getIdEmpresa()));
            }

            Empleado empleado = empOpt.get();
            f.setSector(empleado.getSector());
            if (empleado.getSector() != null) {
                f.setSucursal(empleado.getSector().getSucursal());
            }

            if (body.get("relojId") != null) {
                try {
                    Long relojId = Long.valueOf(body.get("relojId").toString());
                    relojRepository.findById(relojId)
                            .filter(r -> r.getEmpresa() != null && f.getIdEmpresa().equals(r.getEmpresa().getId()))
                            .ifPresent(f::setReloj);
                } catch (NumberFormatException e) {
                    f.setReloj(null);
                }
            } else {
                f.setReloj(null);
            }

            Fichada guardada = asistenciaService.guardarFichada(f);

            if (guardada == null) {
                return ResponseEntity.ok(Map.of(
                        "status", "descartada",
                        "mensaje", "Fichada descartada por cooldown de 5 minutos",
                        "legajo", f.getLegajoReloj(),
                        "empresaId", f.getIdEmpresa()));
            }

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("id", guardada.getId(), "legajo", guardada.getLegajoReloj(), "empresaId", guardada.getIdEmpresa()));
        } catch (Exception e) {
            log.error("Error inesperado registrando fichada", e);
            // 200 OK a propósito para evitar reintentos infinitos del hardware
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "mensaje", "Error interno procesando la fichada; fue registrado en el log"));
        }
    }

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
            @RequestParam("marca") String marca,
            HttpServletRequest request) {

        Long empresaId = (Long) request.getAttribute("empresaId");
        if (empresaId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No autorizado.");
        }

        // La sucursal y el sector tienen que ser de la empresa del usuario, y el sector de esa sucursal
        var sectorOpt = tenantGuard.sectorDeEmpresa(sectorId, empresaId);
        if (tenantGuard.sucursalDeEmpresa(sucursalId, empresaId).isEmpty()
                || sectorOpt.isEmpty()
                || !sucursalId.equals(sectorOpt.get().getSucursal().getIdsucursal())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Sucursal o sector no encontrados.");
        }
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("El archivo está vacío.");
        }

        try {
            int procesadas = hardwareSyncService.procesarArchivoFichadasOffline(file, sucursalId, sectorId, marca);
            return ResponseEntity.ok("Se procesaron " + procesadas + " fichadas exitosamente.");
        } catch (Exception e) {
            log.error("Error procesando archivo de fichadas", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error procesando el archivo. Verifique el formato e intente nuevamente.");
        }
    }
}
