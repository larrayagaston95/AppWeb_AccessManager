package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.Fichada;
import com.atomg.accessmanager.model.Reloj;
import com.atomg.accessmanager.repository.RelojRepository;
import com.atomg.accessmanager.service.AsistenciaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@RestController
@RequestMapping("/api/hardware/hikvision")
public class HikvisionSyncController {

    @Autowired
    private RelojRepository relojRepository;

    @Autowired
    private AsistenciaService asistenciaService;

    @PostMapping("/sync")
    public ResponseEntity<String> syncAttendance(@RequestBody String payload) {
        // 1. Inspeccionar el payload (como String genérico para detectar JSON/XML)
        System.out.println("=== [DEBUG] Payload recibido de Hikvision ===");
        System.out.println(payload);
        System.out.println("=============================================");

        // 2. Extraer el Número de Serie del equipo y el Legajo del empleado
        String numeroSerie = extraerValor(payload, "serialNo", "serialNumber", "deviceSerialNumber", "macAddress", "MACAddr");
        String legajo = extraerValor(payload, "employeeNoString", "employeeNo", "employeeId", "user");

        System.out.println("[DEBUG] numeroSerie extraído: " + numeroSerie);
        System.out.println("[DEBUG] legajo extraído: " + legajo);

        if (numeroSerie == null || legajo == null) {
             System.out.println("[DEBUG] No se pudo extraer el numero de serie o legajo del payload. Abortando.");
             return ResponseEntity.ok("OK");
        }

        // 3. Emparejamiento: Buscar el Reloj por número de serie
        Optional<Reloj> relojOpt = relojRepository.findByNumeroSerie(numeroSerie);
        
        if (relojOpt.isPresent()) {
            Reloj reloj = relojOpt.get();
            System.out.println("[DEBUG] Reloj encontrado en BD: ID=" + reloj.getId() + " | NumeroSerie=" + reloj.getNumeroSerie());
            
            // Actualizar la fecha y hora de la última conexión del equipo físico
            LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Argentina/Cordoba"));
            System.out.println("[DEBUG] LocalDateTime.now(Cordoba) para ultimaConexion = " + ahora);
            reloj.setUltimaConexion(ahora);
            relojRepository.save(reloj);

            // 4. Registro: Guardar la Fichada
            Fichada fichada = new Fichada();
            fichada.setLegajoReloj(legajo);
            
            // Extraer y parsear la hora real de la fichada desde el payload
            String timeStr = extraerValor(payload, "time", "authDateTime", "datetime");
            System.out.println("[DEBUG] String de hora RAW extraído del payload (timeStr): '" + timeStr + "'");

            if (timeStr != null && !timeStr.isEmpty()) {
                try {
                    String cleanTime = timeStr.replace("T", " ");
                    System.out.println("[DEBUG] String de hora limpio (cleanTime): '" + cleanTime + "'");
                    // 'yyyy-M-d H:m:s' es flexible: soporta tanto "2026-09-08 08:30:05" como "2026-9-8 8:30:5"
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-M-d H:m:s");
                    LocalDateTime fechaHoraParsed = LocalDateTime.parse(cleanTime, formatter);
                    System.out.println("[DEBUG] LocalDateTime tras parseo: " + fechaHoraParsed);
                    fichada.setFechaHora(fechaHoraParsed);
                } catch (Exception e) {
                    System.out.println("[DEBUG] ERROR parseando timeStr: '" + timeStr + "'. Causa: " + e.getMessage() + ". Usando hora actual.");
                    fichada.setFechaHora(LocalDateTime.now());
                }
            } else {
                System.out.println("[DEBUG] timeStr es nulo/vacío. Usando LocalDateTime.now().");
                fichada.setFechaHora(LocalDateTime.now());
            }

            fichada.setReloj(reloj);
            fichada.setModoVerificacion("HIKVISION_API");
            
            // Asignar datos de la sucursal/sector del reloj a la fichada
            if (reloj.getEmpresa() != null) {
                fichada.setIdEmpresa(reloj.getEmpresa().getId());
            } else {
                fichada.setIdEmpresa(1L); // Fallback en caso de que no tenga empresa asignada
            }
            if (reloj.getSucursal() != null) {
                fichada.setSucursal(reloj.getSucursal());
            }
            if (reloj.getSector() != null) {
                fichada.setSector(reloj.getSector());
            }

            System.out.println("[DEBUG] >>> Fichada a GUARDAR: legajo=" + fichada.getLegajoReloj()
                    + " | fechaHora=" + fichada.getFechaHora()
                    + " | idEmpresa=" + fichada.getIdEmpresa()
                    + " | modoVerificacion=" + fichada.getModoVerificacion());

            // 5. Guardar la Fichada llamando a AsistenciaService
            asistenciaService.guardarFichada(fichada);
            System.out.println("[DEBUG] Fichada guardada correctamente para legajo: " + legajo + " (Reloj: " + numeroSerie + ")");
        } else {
            System.out.println("[DEBUG] Reloj NO encontrado en la base de datos con numero de serie: " + numeroSerie);
        }

        return ResponseEntity.ok("OK");
    }

    /**
     * Extrae valores de un payload JSON o XML usando expresiones regulares.
     */
    private String extraerValor(String payload, String... claves) {
        for (String clave : claves) {
            // Regex para JSON String: "clave": "valor"
            java.util.regex.Pattern pJsonStr = java.util.regex.Pattern.compile("\"" + clave + "\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher mJsonStr = pJsonStr.matcher(payload);
            if (mJsonStr.find()) return mJsonStr.group(1);

            // Regex para JSON Number: "clave": 123
            java.util.regex.Pattern pJsonNum = java.util.regex.Pattern.compile("\"" + clave + "\"\\s*:\\s*([^,\\}\\s]+)");
            java.util.regex.Matcher mJsonNum = pJsonNum.matcher(payload);
            if (mJsonNum.find()) return mJsonNum.group(1);

            // Regex para XML: <clave>valor</clave>
            java.util.regex.Pattern pXml = java.util.regex.Pattern.compile("<" + clave + ">([^<]+)</" + clave + ">");
            java.util.regex.Matcher mXml = pXml.matcher(payload);
            if (mXml.find()) return mXml.group(1);
        }
        return null;
    }
}
