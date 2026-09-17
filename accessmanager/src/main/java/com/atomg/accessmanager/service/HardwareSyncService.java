package com.atomg.accessmanager.service;

import com.atomg.accessmanager.model.Empleado;
import com.atomg.accessmanager.model.Fichada;
import com.atomg.accessmanager.repository.EmpleadoRepository;
import com.atomg.accessmanager.repository.FichadaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class HardwareSyncService {

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Autowired
    private FichadaRepository fichadaRepository;

    public void procesarFichajesZKTeco(String serialNumber, String datosCrudos) {
        if (datosCrudos == null || datosCrudos.isBlank()) {
            return;
        }
        
        System.out.println("=== NUEVOS FICHAJES ZKTECO [SN: " + serialNumber + "] ===");
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String[] lineas = datosCrudos.split("\n");
        
        for (String linea : lineas) {
            String l = linea.trim();
            if (l.isEmpty()) continue;
            
            // Formato ADMS: PIN\tFechaHora\tEstado\tTipoVerificacion\t...
            String[] campos = l.split("\t");
            if (campos.length >= 2) {
                String pin = campos[0];
                String fechaHoraStr = campos[1];
                
                System.out.println("-> Procesando fichaje: Empleado Legajo=" + pin + ", FechaHora=" + fechaHoraStr);
                
                Optional<Empleado> empleadoOpt = empleadoRepository.findByLegajoReloj(pin);
                
                if (empleadoOpt.isPresent()) {
                    try {
                        LocalDateTime fechaHora = LocalDateTime.parse(fechaHoraStr, formatter);
                        
                        Fichada fichada = new Fichada();
                        fichada.setLegajoReloj(pin);
                        fichada.setFechaHora(fechaHora);
                        fichada.setIdEmpresa(empleadoOpt.get().getEmpresaId());
                        
                        if (campos.length >= 4) {
                            fichada.setModoVerificacion(campos[3]);
                        }
                        
                        fichadaRepository.save(fichada);
                        System.out.println("   [EXITO] Fichaje guardado en base de datos.");
                        
                    } catch (Exception e) {
                        System.out.println("   [ERROR] No se pudo parsear o guardar la fecha/hora: " + e.getMessage());
                    }
                } else {
                    System.out.println("   [IGNORADO] El empleado con legajo " + pin + " no existe en el sistema.");
                }
            }
        }
        System.out.println("====================================================");
    }

    public void procesarFichajeHikvision(JsonNode payload) {
        System.out.println("=== NUEVO FICHAJE HIKVISION ===");
        try {
            JsonNode eventNode = payload.has("AccessControllerEvent") ? payload.get("AccessControllerEvent") : payload;
            
            String employeeNo = eventNode.has("employeeNoString") ? eventNode.get("employeeNoString").asText() : null;
            String timeStr = eventNode.has("time") ? eventNode.get("time").asText() : null;

            if (employeeNo == null || timeStr == null) {
                if (payload.has("events") && payload.get("events").isArray() && payload.get("events").size() > 0) {
                    JsonNode firstEvent = payload.get("events").get(0);
                    employeeNo = firstEvent.has("employeeNoString") ? firstEvent.get("employeeNoString").asText() : null;
                    timeStr = firstEvent.has("time") ? firstEvent.get("time").asText() : null;
                }
            }

            if (employeeNo == null || timeStr == null) {
                System.out.println("   [IGNORADO] Faltan campos clave (employeeNoString o time) en el JSON de Hikvision.");
                return;
            }

            System.out.println("-> Procesando fichaje: Empleado Legajo=" + employeeNo + ", FechaHora=" + timeStr);
            
            Optional<Empleado> empleadoOpt = empleadoRepository.findByLegajoReloj(employeeNo);
            if (empleadoOpt.isPresent()) {
                DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
                LocalDateTime fechaHora;
                try {
                    fechaHora = LocalDateTime.parse(timeStr, isoFormatter);
                } catch (Exception e) {
                    fechaHora = LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }

                Fichada fichada = new Fichada();
                fichada.setLegajoReloj(employeeNo);
                fichada.setFechaHora(fechaHora);
                fichada.setIdEmpresa(empleadoOpt.get().getEmpresaId());
                fichada.setModoVerificacion("HIKVISION_ISAPI");
                
                fichadaRepository.save(fichada);
                System.out.println("   [EXITO] Fichaje Hikvision guardado en BD.");
            } else {
                System.out.println("   [IGNORADO] El empleado con legajo " + employeeNo + " no existe en el sistema.");
            }
        } catch (Exception e) {
            System.out.println("   [ERROR] Procesando payload Hikvision: " + e.getMessage());
        }
        System.out.println("===============================");
    }
}