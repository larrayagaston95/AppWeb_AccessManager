package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.service.AdministracionDeAgentesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Gestión de agentes locales desde el panel SuperAdmin (/api/superadmin/** exige ROLE_SUPERADMIN).
 *
 * Recorrido del dato:
 *   Botón en el panel (agentesUI.js) → agenteAdminService.js hace el fetch → este controlador
 *   → AdministracionDeAgentesService (reglas y base de datos) → respuesta JSON al panel.
 *   Errores: 400 si faltan datos, 404 si el agente no existe, 500 si falla la base.
 */
@RestController
@RequestMapping("/api/superadmin/agentes")
public class AgenteAdminController {

    private static final Logger registro = LoggerFactory.getLogger(AgenteAdminController.class);

    @Autowired
    private AdministracionDeAgentesService administracionDeAgentes;

    /** GET → lista de agentes con estado (en línea, última conexión, detalle). */
    @GetMapping
    public ResponseEntity<?> listarAgentes() {
        try {
            return ResponseEntity.ok(administracionDeAgentes.listarAgentesConEstado());
        } catch (RuntimeException excepcion) {
            registro.error("Error en AgenteAdminController.java -> listarAgentes: {}", excepcion.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "No se pudo cargar la lista de agentes"));
        }
    }

    /** POST {nombre, empresa_id, sucursal_id?} → {id, clave, aviso}. La clave se muestra una sola vez. */
    @PostMapping
    public ResponseEntity<?> crearAgente(@RequestBody Map<String, Object> datosDelAgente) {
        try {
            String nombre = datosDelAgente.get("nombre") != null ? datosDelAgente.get("nombre").toString() : null;
            return ResponseEntity.ok(administracionDeAgentes.crearAgente(nombre,
                    convertirANumero(datosDelAgente.get("empresa_id")), convertirANumero(datosDelAgente.get("sucursal_id"))));
        } catch (IllegalArgumentException datosInvalidos) {
            return ResponseEntity.badRequest().body(Map.of("error", datosInvalidos.getMessage()));
        } catch (RuntimeException excepcion) {
            registro.error("Error en AgenteAdminController.java -> crearAgente: {}", excepcion.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "No se pudo crear el agente"));
        }
    }

    /** POST /{id}/revocar → la clave del agente deja de funcionar de inmediato. */
    @PostMapping("/{idDelAgente}/revocar")
    public ResponseEntity<?> revocarAgente(@PathVariable Long idDelAgente) {
        try {
            administracionDeAgentes.revocarAgente(idDelAgente);
            return ResponseEntity.ok(Map.of("message", "Agente revocado: su clave deja de funcionar de inmediato."));
        } catch (NoSuchElementException noEncontrado) {
            return ResponseEntity.status(404).body(Map.of("error", noEncontrado.getMessage()));
        } catch (RuntimeException excepcion) {
            registro.error("Error en AgenteAdminController.java -> revocarAgente: {}", excepcion.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "No se pudo revocar el agente"));
        }
    }

    /** POST /{id}/regenerar-clave → {id, clave, aviso}. La clave anterior deja de funcionar. */
    @PostMapping("/{idDelAgente}/regenerar-clave")
    public ResponseEntity<?> regenerarClave(@PathVariable Long idDelAgente) {
        try {
            return ResponseEntity.ok(administracionDeAgentes.regenerarClave(idDelAgente));
        } catch (NoSuchElementException noEncontrado) {
            return ResponseEntity.status(404).body(Map.of("error", noEncontrado.getMessage()));
        } catch (RuntimeException excepcion) {
            registro.error("Error en AgenteAdminController.java -> regenerarClave: {}", excepcion.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "No se pudo generar la clave"));
        }
    }

    private static Long convertirANumero(Object valor) {
        if (valor instanceof Number numero) return numero.longValue();
        if (valor == null || valor.toString().isBlank()) return null;
        try {
            return Long.parseLong(valor.toString().trim());
        } catch (NumberFormatException valorInvalido) {
            return null;
        }
    }
}
