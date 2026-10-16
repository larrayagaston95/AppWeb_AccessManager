package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.Agente;
import com.atomg.accessmanager.model.Empresa;
import com.atomg.accessmanager.model.Sucursal;
import com.atomg.accessmanager.repository.AgenteRepository;
import com.atomg.accessmanager.repository.EmpresaRepository;
import com.atomg.accessmanager.repository.SucursalRepository;
import com.atomg.accessmanager.security.AgenteClaves;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestión de agentes locales desde el panel SuperAdmin (/api/superadmin/** → ROLE_SUPERADMIN).
 * La clave del agente se devuelve una sola vez, al crearlo o regenerarla.
 */
@RestController
@RequestMapping("/api/superadmin/agentes")
public class AgenteAdminController {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Cordoba");

    @Autowired
    private AgenteRepository agenteRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private SucursalRepository sucursalRepository;

    @Value("${agente.intervalo-segundos:60}")
    private int intervaloSegundos;

    @GetMapping
    public ResponseEntity<?> listar() {
        LocalDateTime ahora = LocalDateTime.now(ZONA);
        // "En línea" si reportó en los últimos 3 intervalos (mínimo 3 minutos)
        long toleranciaSeg = Math.max(180, intervaloSegundos * 3L);

        List<Map<String, Object>> resultado = agenteRepository.findAllByOrderByEmpresaIdAscNombreAsc().stream()
                .map(a -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", a.getId());
                    m.put("nombre", a.getNombre());
                    m.put("empresa", a.getEmpresa().getNombre());
                    m.put("sucursal", a.getSucursal() != null ? a.getSucursal().getNombre() : "Todas");
                    m.put("activo", a.isActivo());
                    m.put("ultimaConexion", a.getUltimaConexion() != null ? a.getUltimaConexion().toString() : "");
                    m.put("enLinea", a.isActivo() && a.getUltimaConexion() != null
                            && Duration.between(a.getUltimaConexion(), ahora).getSeconds() <= toleranciaSeg);
                    m.put("version", a.getVersionAgente() != null ? a.getVersionAgente() : "");
                    m.put("estado", a.getUltimoEstado() != null ? a.getUltimoEstado() : "");
                    return m;
                })
                .toList();
        return ResponseEntity.ok(resultado);
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Map<String, Object> payload) {
        String nombre = payload.get("nombre") != null ? payload.get("nombre").toString().trim() : "";
        Long empresaId = aLong(payload.get("empresa_id"));
        Long sucursalId = aLong(payload.get("sucursal_id"));

        if (nombre.isEmpty() || empresaId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Faltan parámetros (nombre, empresa_id)"));
        }
        Empresa empresa = empresaRepository.findById(empresaId).orElse(null);
        if (empresa == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Empresa no encontrada"));
        }
        Sucursal sucursal = null;
        if (sucursalId != null) {
            sucursal = sucursalRepository.findById(sucursalId)
                    .filter(s -> s.getEmpresa() != null && empresaId.equals(s.getEmpresa().getId()))
                    .orElse(null);
            if (sucursal == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "La sucursal no pertenece a esa empresa"));
            }
        }

        AgenteClaves.ClaveNueva clave = AgenteClaves.generar();
        Agente agente = new Agente();
        agente.setNombre(nombre);
        agente.setEmpresa(empresa);
        agente.setSucursal(sucursal);
        agente.setClavePrefijo(clave.prefijo());
        agente.setClaveHash(clave.hash());
        agente.setActivo(true);
        agente.setFechaCreacion(LocalDateTime.now(ZONA));
        agente = agenteRepository.save(agente);

        return ResponseEntity.ok(Map.of(
                "id", agente.getId(),
                "clave", clave.clave(),
                "aviso", "Copiá la clave ahora: no se vuelve a mostrar."));
    }

    @PostMapping("/{id}/revocar")
    public ResponseEntity<?> revocar(@PathVariable Long id) {
        Agente agente = agenteRepository.findById(id).orElse(null);
        if (agente == null) return ResponseEntity.status(404).body(Map.of("error", "Agente no encontrado"));
        agente.setActivo(false);
        agenteRepository.save(agente);
        return ResponseEntity.ok(Map.of("message", "Agente revocado: su clave deja de funcionar de inmediato."));
    }

    @PostMapping("/{id}/regenerar-clave")
    public ResponseEntity<?> regenerarClave(@PathVariable Long id) {
        Agente agente = agenteRepository.findById(id).orElse(null);
        if (agente == null) return ResponseEntity.status(404).body(Map.of("error", "Agente no encontrado"));
        AgenteClaves.ClaveNueva clave = AgenteClaves.generar();
        agente.setClavePrefijo(clave.prefijo());
        agente.setClaveHash(clave.hash());
        agente.setActivo(true);
        agenteRepository.save(agente);
        return ResponseEntity.ok(Map.of(
                "id", agente.getId(),
                "clave", clave.clave(),
                "aviso", "La clave anterior dejó de funcionar. Cargá esta en el agente."));
    }

    private static Long aLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o != null && !o.toString().isBlank()) {
            try {
                return Long.parseLong(o.toString().trim());
            } catch (NumberFormatException ignored) {
                // inválido
            }
        }
        return null;
    }
}
