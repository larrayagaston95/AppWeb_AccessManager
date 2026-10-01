package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.service.HardwareSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador adaptador para dispositivos ZKTeco (Protocolo ADMS/HTTP-Push).
 * Normaliza los campos propietarios del firmware ZKTeco y los delega al motor
 * de asistencia compartido (HardwareSyncService), garantizando que la tabla
 * 'fichadas' y los reportes mensuales se generen de forma idéntica a Hikvision.
 *
 * Proyecto: FluxTech
 * Autor: LARRAYA GASTÓN
 */
@RestController
@RequestMapping("/api/hardware/zkteco")
@CrossOrigin(origins = "*")
public class ZktecoSyncController {

    @Autowired
    private HardwareSyncService hardwareSyncService;

    /**
     * Endpoint receptor de pings del protocolo ADMS de ZKTeco.
     * El firmware ZKTeco envía el body como texto plano tabulado,
     * con el número de serie en la query string o en un header.
     *
     * Patrón: POST /api/hardware/zkteco/sync?SN=<serial>
     * Body (text/plain): PIN\tFechaHora\tEstado\tTipoVerificacion\n...
     *
     * CRÍTICO: Siempre responde 200 OK para evitar que el firmware
     *          ZKTeco entre en bucle de reintentos infinitos.
     */
    @PostMapping(value = "/sync", consumes = {"text/plain", "application/x-www-form-urlencoded", "*/*"})
    public ResponseEntity<String> syncZKTeco(
            @RequestParam(value = "SN", required = false) String snParam,
            @RequestHeader(value = "SN", required = false) String snHeader,
            @RequestBody(required = false) String body) {

        // Normalizador del número de serie:
        // ZKTeco puede enviarlo como query param (?SN=...), header o incluirlo en la primera línea del body.
        String serialNumber = resolverNumeroSerie(snParam, snHeader, body);

        if (serialNumber == null || serialNumber.isBlank()) {
            System.out.println("[ZKTECO] Ping recibido sin número de serie. Ignorado pero respondido OK.");
            return ResponseEntity.ok("OK");
        }

        try {
            // Delegar al mismo motor de negocio que usa HikvisionSyncController
            hardwareSyncService.procesarFichajesZKTeco(serialNumber, body);
        } catch (Exception e) {
            // Tolerancia a fallos: el firmware NUNCA debe recibir un error HTTP
            System.out.println("[ZKTECO][ERROR] Fallo interno procesando fichajes de SN=" + serialNumber + ": " + e.getMessage());
        }

        // RESPUESTA OBLIGATORIA: el protocolo ADMS de ZKTeco espera un "OK" textual
        return ResponseEntity.ok("OK");
    }

    /**
     * Endpoint alternativo para integraciones via JSON (modo Push HTTP configurable
     * en ZKTeco SDK / software de terceros).
     * Acepta los campos estándar: SN, PIN / badgenumber / employeeNo, time / timestamp.
     */
    @PostMapping(value = "/sync/json", consumes = {"application/json"})
    public ResponseEntity<String> syncZKTecoJson(@RequestBody Map<String, String> payload) {
        try {
            // Resolución del número de serie del dispositivo
            String serialNumber = coalesce(payload.get("SN"), payload.get("deviceSerialNumber"), payload.get("sn"));

            // Resolución del legajo del empleado
            String pin = coalesce(payload.get("PIN"), payload.get("badgenumber"), payload.get("employeeNo"), payload.get("pin"));

            // Resolución de la fecha/hora de la fichada
            String fechaHora = coalesce(payload.get("time"), payload.get("timestamp"), payload.get("Time"));

            if (serialNumber == null || pin == null || fechaHora == null) {
                System.out.println("[ZKTECO-JSON] Payload incompleto (faltan SN, PIN o time). Ignorado.");
                return ResponseEntity.ok("OK");
            }

            // Construir el formato ADMS esperado por procesarFichajesZKTeco:
            // PIN\tFechaHora\tEstado\tModo
            String modo = coalesce(payload.get("verifyMode"), payload.get("tipo"), "");
            String lineaAdms = pin + "\t" + fechaHora + "\t0\t" + modo;

            hardwareSyncService.procesarFichajesZKTeco(serialNumber, lineaAdms);
        } catch (Exception e) {
            System.out.println("[ZKTECO-JSON][ERROR] " + e.getMessage());
        }

        return ResponseEntity.ok("OK");
    }

    /**
     * Heartbeat del firmware ZKTeco (checkin periódico de conectividad).
     * Algunos modelos hacen GET antes del POST de fichajes. Responder siempre OK.
     */
    @GetMapping("/sync")
    public ResponseEntity<String> heartbeat(
            @RequestParam(value = "SN", required = false) String sn) {
        System.out.println("[ZKTECO] Heartbeat recibido. SN=" + sn);
        return ResponseEntity.ok("OK");
    }

    // ─── Helpers privados ────────────────────────────────────────────────────

    /**
     * Resuelve el número de serie ZKTeco probando múltiples fuentes en orden de prioridad.
     */
    private String resolverNumeroSerie(String snParam, String snHeader, String body) {
        if (snParam != null && !snParam.isBlank()) return snParam;
        if (snHeader != null && !snHeader.isBlank()) return snHeader;

        // Último recurso: algunos firmwares embeben el SN en la primera línea del body
        if (body != null && !body.isBlank()) {
            String primeraLinea = body.split("\\n")[0].trim();
            if (primeraLinea.startsWith("SN=")) {
                return primeraLinea.substring(3).trim();
            }
        }

        return null;
    }

    /**
     * Retorna el primer valor no nulo y no vacío de la lista.
     */
    private String coalesce(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }
}
