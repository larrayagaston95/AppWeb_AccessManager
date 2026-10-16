package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.Agente;
import com.atomg.accessmanager.model.Reloj;
import com.atomg.accessmanager.security.AgenteAuthFilter;
import com.atomg.accessmanager.service.AgenteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API que consumen los agentes locales. Autenticación: header X-Agent-Key (AgenteAuthFilter).
 *
 *  GET  /api/agente/config     → relojes a leer y desde cuándo
 *  POST /api/agente/fichadas   → lote de fichadas (idempotente: reenviar no duplica)
 *  POST /api/agente/heartbeat  → "estoy vivo" + estado de cada reloj
 */
@RestController
@RequestMapping("/api/agente")
public class AgenteController {

    private static final int MAX_LOTE = 500;

    @Autowired
    private AgenteService agenteService;

    @Value("${agente.intervalo-segundos:60}")
    private int intervaloSegundos;

    private Agente agente(HttpServletRequest request) {
        return (Agente) request.getAttribute(AgenteAuthFilter.ATRIBUTO_AGENTE);
    }

    @GetMapping("/config")
    public ResponseEntity<?> config(HttpServletRequest request) {
        Agente agente = agente(request);
        if (agente == null) return ResponseEntity.status(401).build();

        List<Map<String, Object>> relojes = new ArrayList<>();
        for (Reloj r : agenteService.relojesDelAgente(agente)) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("nombre", r.getNombre() != null ? r.getNombre() : r.getNumeroSerie());
            m.put("numeroSerie", r.getNumeroSerie());
            m.put("marca", r.getMarca() != null ? r.getMarca() : "");
            m.put("ip", r.getIp() != null ? r.getIp() : "");
            m.put("usuario", r.getUsuario() != null ? r.getUsuario() : "");
            // Credencial del reloj: viaja solo por HTTPS y solo al agente autenticado de esa empresa
            m.put("password", r.getPassword() != null ? r.getPassword() : "");
            LocalDateTime ultima = agenteService.ultimaFichadaDeReloj(r.getId());
            m.put("ultimaFichada", ultima != null ? ultima.toString() : "");
            relojes.add(m);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("agenteId", agente.getId());
        resp.put("nombre", agente.getNombre());
        resp.put("intervaloSegundos", intervaloSegundos);
        resp.put("relojes", relojes);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/fichadas")
    public ResponseEntity<?> fichadas(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Agente agente = agente(request);
        if (agente == null) return ResponseEntity.status(401).build();

        Object lista = body.get("fichadas");
        if (!(lista instanceof List<?> items)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Falta la lista 'fichadas'"));
        }
        if (items.size() > MAX_LOTE) {
            return ResponseEntity.badRequest().body(Map.of("error", "Máximo " + MAX_LOTE + " fichadas por lote"));
        }

        List<AgenteService.ItemFichada> parsed = new ArrayList<>();
        for (Object o : items) {
            if (!(o instanceof Map<?, ?> m)) continue;
            parsed.add(new AgenteService.ItemFichada(
                    aLong(m.get("relojId")),
                    m.get("legajo") != null ? m.get("legajo").toString() : null,
                    m.get("fechaHora") != null ? m.get("fechaHora").toString() : null,
                    m.get("modo") != null ? m.get("modo").toString() : null));
        }

        AgenteService.ResultadoLote r = agenteService.procesarLote(agente, parsed);
        return ResponseEntity.ok(Map.of(
                "recibidas", r.recibidas(),
                "guardadas", r.guardadas(),
                "duplicadas", r.duplicadas(),
                "descartadas", r.descartadas(),
                "avisos", r.avisos()));
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<?> heartbeat(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Agente agente = agente(request);
        if (agente == null) return ResponseEntity.status(401).build();

        List<AgenteService.EstadoReloj> estados = new ArrayList<>();
        if (body.get("relojes") instanceof List<?> lista) {
            for (Object o : lista) {
                if (!(o instanceof Map<?, ?> m)) continue;
                estados.add(new AgenteService.EstadoReloj(
                        aLong(m.get("id")),
                        Boolean.TRUE.equals(m.get("ok")),
                        m.get("error") != null ? m.get("error").toString() : null,
                        m.get("serialDetectado") != null ? m.get("serialDetectado").toString() : null));
            }
        }
        Long pendientes = aLong(body.get("pendientes"));
        String version = body.get("version") != null ? body.get("version").toString() : null;

        agenteService.registrarHeartbeat(agente, version, pendientes != null ? pendientes.intValue() : 0, estados);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private static Long aLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o != null) {
            try {
                return Long.parseLong(o.toString().trim());
            } catch (NumberFormatException ignored) {
                // valor inválido
            }
        }
        return null;
    }
}
