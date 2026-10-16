package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.dto.EstadoDeRelojInformadoDTO;
import com.atomg.accessmanager.dto.FichadaRecibidaDeAgenteDTO;
import com.atomg.accessmanager.dto.ResultadoDelLoteDeFichadasDTO;
import com.atomg.accessmanager.model.Agente;
import com.atomg.accessmanager.model.Reloj;
import com.atomg.accessmanager.security.AgenteAuthFilter;
import com.atomg.accessmanager.service.EstadoDeAgentesService;
import com.atomg.accessmanager.service.ProcesadorDeLotesDeFichadasService;
import com.atomg.accessmanager.service.RelojesDelAgenteService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * API que consumen los agentes locales (header X-Agent-Key, validado por AgenteAuthFilter).
 *
 * Recorrido del dato:
 *   GET  /api/agente/config    → RelojesDelAgenteService → relojes a leer y desde cuándo.
 *   POST /api/agente/fichadas  → ProcesadorDeLotesDeFichadasService → resumen (guardadas, duplicadas...).
 *   POST /api/agente/heartbeat → EstadoDeAgentesService → actualiza "En línea" en el panel.
 *   El controlador solo traduce JSON ↔ DTO; las reglas de negocio están en los servicios.
 */
@RestController
@RequestMapping("/api/agente")
public class AgenteController {

    private static final Logger registro = LoggerFactory.getLogger(AgenteController.class);
    private static final int CANTIDAD_MAXIMA_DE_FICHADAS_POR_LOTE = 500;

    @Autowired
    private RelojesDelAgenteService relojesDelAgenteService;

    @Autowired
    private ProcesadorDeLotesDeFichadasService procesadorDeLotes;

    @Autowired
    private EstadoDeAgentesService estadoDeAgentesService;

    @Value("${agente.intervalo-segundos:60}")
    private int segundosEntreCiclosDelAgente;

    @GetMapping("/config")
    public ResponseEntity<?> entregarConfiguracion(HttpServletRequest pedido) {
        Agente agente = obtenerAgenteAutenticado(pedido);
        if (agente == null) return ResponseEntity.status(401).build();
        try {
            List<Map<String, Object>> relojes = new ArrayList<>();
            for (Reloj reloj : relojesDelAgenteService.obtenerRelojesDelAgente(agente)) {
                relojes.add(convertirRelojParaElAgente(reloj));
            }
            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("agenteId", agente.getId());
            respuesta.put("nombre", agente.getNombre());
            respuesta.put("intervaloSegundos", segundosEntreCiclosDelAgente);
            respuesta.put("relojes", relojes);
            return ResponseEntity.ok(respuesta);
        } catch (RuntimeException excepcion) {
            registro.error("Error en AgenteController.java -> entregarConfiguracion: agente {}: {}", agente.getId(), excepcion.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "No se pudo armar la configuración"));
        }
    }

    @PostMapping("/fichadas")
    public ResponseEntity<?> recibirLoteDeFichadas(@RequestBody Map<String, Object> cuerpo, HttpServletRequest pedido) {
        Agente agente = obtenerAgenteAutenticado(pedido);
        if (agente == null) return ResponseEntity.status(401).build();
        if (!(cuerpo.get("fichadas") instanceof List<?> fichadasRecibidas)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Falta la lista 'fichadas'"));
        }
        if (fichadasRecibidas.size() > CANTIDAD_MAXIMA_DE_FICHADAS_POR_LOTE) {
            return ResponseEntity.badRequest().body(Map.of("error", "Máximo " + CANTIDAD_MAXIMA_DE_FICHADAS_POR_LOTE + " fichadas por lote"));
        }
        try {
            ResultadoDelLoteDeFichadasDTO resultado = procesadorDeLotes.procesarLote(agente, convertirFichadas(fichadasRecibidas));
            return ResponseEntity.ok(Map.of(
                    "recibidas", resultado.cantidadRecibida(),
                    "guardadas", resultado.cantidadGuardada(),
                    "duplicadas", resultado.cantidadDuplicada(),
                    "descartadas", resultado.cantidadDescartada(),
                    "avisos", resultado.avisos()));
        } catch (RuntimeException excepcion) {
            // 500 → el agente conserva el lote en su cola y reintenta en el próximo ciclo
            registro.error("Error en AgenteController.java -> recibirLoteDeFichadas: agente {}: {}", agente.getId(), excepcion.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "No se pudo procesar el lote"));
        }
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<?> recibirHeartbeat(@RequestBody Map<String, Object> cuerpo, HttpServletRequest pedido) {
        Agente agente = obtenerAgenteAutenticado(pedido);
        if (agente == null) return ResponseEntity.status(401).build();
        try {
            Long fichadasPendientes = convertirANumero(cuerpo.get("pendientes"));
            String versionDelAgente = cuerpo.get("version") != null ? cuerpo.get("version").toString() : null;
            estadoDeAgentesService.registrarHeartbeat(agente, versionDelAgente,
                    fichadasPendientes != null ? fichadasPendientes.intValue() : 0, convertirEstados(cuerpo.get("relojes")));
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (RuntimeException excepcion) {
            registro.error("Error en AgenteController.java -> recibirHeartbeat: agente {}: {}", agente.getId(), excepcion.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "No se pudo registrar el estado"));
        }
    }

    /** El filtro de seguridad deja el agente autenticado en el pedido. */
    private static Agente obtenerAgenteAutenticado(HttpServletRequest pedido) {
        return (Agente) pedido.getAttribute(AgenteAuthFilter.ATRIBUTO_AGENTE);
    }

    /** Datos que necesita el agente para conectarse al reloj. La contraseña viaja solo por HTTPS y solo a su agente. */
    private Map<String, Object> convertirRelojParaElAgente(Reloj reloj) {
        Map<String, Object> datosDelReloj = new HashMap<>();
        datosDelReloj.put("id", reloj.getId());
        datosDelReloj.put("nombre", reloj.getNombre() != null ? reloj.getNombre() : reloj.getNumeroSerie());
        datosDelReloj.put("numeroSerie", reloj.getNumeroSerie());
        datosDelReloj.put("marca", reloj.getMarca() != null ? reloj.getMarca() : "");
        datosDelReloj.put("ip", reloj.getIp() != null ? reloj.getIp() : "");
        datosDelReloj.put("usuario", reloj.getUsuario() != null ? reloj.getUsuario() : "");
        datosDelReloj.put("password", reloj.getPassword() != null ? reloj.getPassword() : "");
        LocalDateTime ultimaFichada = relojesDelAgenteService.obtenerUltimaFichadaDelReloj(reloj.getId());
        datosDelReloj.put("ultimaFichada", ultimaFichada != null ? ultimaFichada.toString() : "");
        return datosDelReloj;
    }

    private static List<FichadaRecibidaDeAgenteDTO> convertirFichadas(List<?> fichadasRecibidas) {
        List<FichadaRecibidaDeAgenteDTO> fichadas = new ArrayList<>();
        for (Object elemento : fichadasRecibidas) {
            if (elemento instanceof Map<?, ?> fichada) {
                fichadas.add(new FichadaRecibidaDeAgenteDTO(convertirANumero(fichada.get("relojId")), convertirATexto(fichada.get("legajo")),
                        convertirATexto(fichada.get("fechaHora")), convertirATexto(fichada.get("modo"))));
            }
        }
        return fichadas;
    }

    private static List<EstadoDeRelojInformadoDTO> convertirEstados(Object listaDeEstados) {
        List<EstadoDeRelojInformadoDTO> estados = new ArrayList<>();
        if (listaDeEstados instanceof List<?> lista) {
            for (Object elemento : lista) {
                if (elemento instanceof Map<?, ?> estado) {
                    estados.add(new EstadoDeRelojInformadoDTO(convertirANumero(estado.get("id")), Boolean.TRUE.equals(estado.get("ok")),
                            convertirATexto(estado.get("error")), convertirATexto(estado.get("serialDetectado")), convertirATexto(estado.get("metodo"))));
                }
            }
        }
        return estados;
    }

    private static String convertirATexto(Object valor) {
        return valor != null ? valor.toString() : null;
    }

    private static Long convertirANumero(Object valor) {
        if (valor instanceof Number numero) return numero.longValue();
        if (valor == null) return null;
        try {
            return Long.parseLong(valor.toString().trim());
        } catch (NumberFormatException valorInvalido) {
            return null;
        }
    }
}
