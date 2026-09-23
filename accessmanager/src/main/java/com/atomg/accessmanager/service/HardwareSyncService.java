package com.atomg.accessmanager.service;

import com.atomg.accessmanager.model.Empleado;
import com.atomg.accessmanager.model.Fichada;
import com.atomg.accessmanager.model.Reloj;
import com.atomg.accessmanager.model.Sucursal;
import com.atomg.accessmanager.repository.EmpleadoRepository;
import com.atomg.accessmanager.repository.FichadaRepository;
import com.atomg.accessmanager.repository.RelojRepository;
import com.atomg.accessmanager.repository.SucursalRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class HardwareSyncService {

    @Autowired
    private EmpleadoRepository empleadoRepository;

    @Autowired
    private FichadaRepository fichadaRepository;

    @Autowired
    private RelojRepository relojRepository;

    @Autowired
    private SucursalRepository sucursalRepository;

    public void procesarFichajesZKTeco(String serialNumber, String datosCrudos) {
        if (datosCrudos == null || datosCrudos.isBlank() || serialNumber == null) {
            return;
        }
        
        System.out.println("=== NUEVOS FICHAJES ZKTECO [SN: " + serialNumber + "] ===");
        
        Optional<Reloj> relojOpt = relojRepository.findByNumeroSerie(serialNumber);
        if (relojOpt.isEmpty()) {
            System.out.println("   [IGNORADO] El reloj con SN " + serialNumber + " no esta registrado en el sistema.");
            return;
        }
        Reloj reloj = relojOpt.get();
        Long empresaId = reloj.getEmpresa().getId();
        
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
                
                try {
                    LocalDateTime fechaHora = LocalDateTime.parse(fechaHoraStr, formatter);
                    
                    Fichada fichada = new Fichada();
                    fichada.setLegajoReloj(pin);
                    fichada.setFechaHora(fechaHora);
                    fichada.setIdEmpresa(empresaId);
                    fichada.setReloj(reloj);
                    
                    if (campos.length >= 4) {
                        fichada.setModoVerificacion(campos[3]);
                    }
                    
                    fichadaRepository.save(fichada);
                    System.out.println("   [EXITO] Fichaje guardado en base de datos.");
                    
                } catch (Exception e) {
                    System.out.println("   [ERROR] No se pudo parsear o guardar la fecha/hora: " + e.getMessage());
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
            
            // Buscar serial en el payload, usualmente en AccessControllerEvent.deviceSerialNo o en la raiz
            String deviceSerial = null;
            if (eventNode.has("deviceSerialNo")) {
                deviceSerial = eventNode.get("deviceSerialNo").asText();
            } else if (payload.has("deviceSerialNo")) {
                deviceSerial = payload.get("deviceSerialNo").asText();
            }

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
            
            if (deviceSerial == null) {
                System.out.println("   [IGNORADO] No se encontro el numero de serie (deviceSerialNo) en el JSON de Hikvision.");
                return;
            }

            System.out.println("-> Procesando fichaje: Empleado Legajo=" + employeeNo + ", FechaHora=" + timeStr + ", SN=" + deviceSerial);
            
            Optional<Reloj> relojOpt = relojRepository.findByNumeroSerie(deviceSerial);
            if (relojOpt.isEmpty()) {
                System.out.println("   [IGNORADO] El reloj con SN " + deviceSerial + " no esta registrado en el sistema.");
                return;
            }
            Reloj reloj = relojOpt.get();
            Long empresaId = reloj.getEmpresa().getId();

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
            fichada.setIdEmpresa(empresaId);
            fichada.setReloj(reloj);
            fichada.setModoVerificacion("HIKVISION_ISAPI");
            
            fichadaRepository.save(fichada);
            System.out.println("   [EXITO] Fichaje Hikvision guardado en BD.");
        } catch (Exception e) {
            System.out.println("   [ERROR] Procesando payload Hikvision: " + e.getMessage());
        }
        System.out.println("===============================");
    }

    public int procesarArchivoFichadasOffline(MultipartFile file, Long sucursalId) throws Exception {
        Optional<Sucursal> sucursalOpt = sucursalRepository.findById(sucursalId);
        if (sucursalOpt.isEmpty()) {
            throw new Exception("La sucursal con ID " + sucursalId + " no existe.");
        }
        
        Sucursal sucursal = sucursalOpt.get();
        Long empresaId = sucursal.getEmpresa().getId();
        
        int procesadas = 0;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Esperamos algo como: "123 2026-09-23 09:00:00"
                // Puede separarse por espacios o tabulaciones
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    String legajo = parts[0];
                    String fecha = parts[1];
                    String hora = parts[2];
                    String fechaHoraStr = fecha + " " + hora;

                    try {
                        LocalDateTime fechaHora = LocalDateTime.parse(fechaHoraStr, formatter);
                        
                        Fichada fichada = new Fichada();
                        fichada.setLegajoReloj(legajo);
                        fichada.setFechaHora(fechaHora);
                        fichada.setIdEmpresa(empresaId);
                        fichada.setSucursal(sucursal);
                        fichada.setModoVerificacion("OFFLINE_MANUAL");
                        
                        fichadaRepository.save(fichada);
                        procesadas++;
                    } catch (Exception e) {
                        System.out.println("   [ERROR] Línea mal formateada: " + line + " - Error: " + e.getMessage());
                    }
                } else {
                    System.out.println("   [WARNING] Línea con formato incorrecto ignorada: " + line);
                }
            }
        }
        
        return procesadas;
    }
}