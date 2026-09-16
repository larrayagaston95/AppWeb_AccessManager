package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.Empresa;
import com.atomg.accessmanager.model.Sector;
import com.atomg.accessmanager.model.Sucursal;
import com.atomg.accessmanager.model.Usuario;
import com.atomg.accessmanager.repository.EmpresaRepository;
import com.atomg.accessmanager.repository.SectorRepository;
import com.atomg.accessmanager.repository.SucursalRepository;
import com.atomg.accessmanager.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/superadmin")
@CrossOrigin(origins = "*")
public class SuperAdminController {

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SucursalRepository sucursalRepository;

    @Autowired
    private SectorRepository sectorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // =========================================================================
    // POST /api/superadmin/clientes  — Crear empresa + usuario admin
    // =========================================================================
    @PostMapping("/clientes")
    public ResponseEntity<?> crearCliente(@RequestBody Map<String, String> payload) {
        String nombreEmpresa = payload.get("nombre_empresa");
        String usernameAdmin = payload.get("username_admin");
        String passwordAdmin = payload.get("password_admin");

        if (nombreEmpresa == null || usernameAdmin == null || passwordAdmin == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Faltan parametros (nombre_empresa, username_admin, password_admin)"));
        }

        try {
            Empresa nuevaEmpresa = new Empresa();
            nuevaEmpresa.setNombre(nombreEmpresa);
            nuevaEmpresa = empresaRepository.save(nuevaEmpresa);

            Usuario nuevoUsuario = new Usuario();
            nuevoUsuario.setUsername(usernameAdmin);
            nuevoUsuario.setPassword(passwordEncoder.encode(passwordAdmin));
            nuevoUsuario.setRol("ROLE_ADMIN");
            nuevoUsuario.setEmpresa(nuevaEmpresa);
            usuarioRepository.save(nuevoUsuario);

            return ResponseEntity.ok(Map.of(
                    "message", "Cliente creado con exito",
                    "empresa_id", nuevaEmpresa.getId(),
                    "username", usernameAdmin
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al crear cliente: " + e.getMessage()));
        }
    }

    // =========================================================================
    // GET /api/superadmin/empresas  — Lista todas las empresas (sin filtro tenant)
    // =========================================================================
    @GetMapping("/empresas")
    public ResponseEntity<?> listarEmpresas() {
        List<Map<String, Object>> resultado = empresaRepository.findAll().stream()
                .map(emp -> Map.<String, Object>of(
                        "id", emp.getId(),
                        "nombre", emp.getNombre()
                ))
                .toList();
        return ResponseEntity.ok(resultado);
    }

    // =========================================================================
    // POST /api/superadmin/sucursales  — Crear sucursal para una empresa
    // =========================================================================
    @PostMapping("/sucursales")
    public ResponseEntity<?> crearSucursal(@RequestBody Map<String, Object> payload) {
        String nombreSucursal = (String) payload.get("nombre_sucursal");
        Object empIdObj = payload.get("empresa_id");

        if (nombreSucursal == null || empIdObj == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Faltan parametros (nombre_sucursal, empresa_id)"));
        }

        Long empresaId = empIdObj instanceof Number
                ? ((Number) empIdObj).longValue()
                : Long.parseLong(empIdObj.toString());

        try {
            Empresa empresa = empresaRepository.findById(empresaId)
                    .orElseThrow(() -> new RuntimeException("Empresa no encontrada"));

            Sucursal sucursal = new Sucursal();
            sucursal.setNombre(nombreSucursal);
            sucursal.setEmpresa(empresa);
            sucursalRepository.save(sucursal);

            return ResponseEntity.ok(Map.of(
                    "message", "Sucursal creada con exito",
                    "sucursal_id", sucursal.getIdsucursal(),
                    "empresa_id", empresa.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================================================
    // GET /api/superadmin/sucursales  — Lista todas las sucursales (sin filtro tenant)
    // =========================================================================
    @GetMapping("/sucursales")
    public ResponseEntity<?> listarSucursales() {
        List<Map<String, Object>> resultado = sucursalRepository.findAll().stream()
                .map(suc -> Map.<String, Object>of(
                        "id", suc.getIdsucursal(),
                        "nombre", suc.getNombre(),
                        "empresa", suc.getEmpresa() != null ? suc.getEmpresa().getNombre() : ""
                ))
                .toList();
        return ResponseEntity.ok(resultado);
    }

    // =========================================================================
    // POST /api/superadmin/sectores  — Crear sector para una sucursal
    // =========================================================================
    @PostMapping("/sectores")
    public ResponseEntity<?> crearSector(@RequestBody Map<String, Object> payload) {
        String nombreSector = (String) payload.get("nombre_sector");
        Object sucIdObj = payload.get("sucursal_id");

        if (nombreSector == null || sucIdObj == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Faltan parametros (nombre_sector, sucursal_id)"));
        }

        Long sucursalId = sucIdObj instanceof Number
                ? ((Number) sucIdObj).longValue()
                : Long.parseLong(sucIdObj.toString());

        try {
            Sucursal sucursal = sucursalRepository.findById(sucursalId)
                    .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

            Sector sector = new Sector();
            sector.setNombre(nombreSector);
            sector.setSucursal(sucursal);
            sectorRepository.save(sector);

            return ResponseEntity.ok(Map.of(
                    "message", "Sector creado con exito",
                    "sector_id", sector.getId(),
                    "sucursal_id", sucursal.getIdsucursal()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}