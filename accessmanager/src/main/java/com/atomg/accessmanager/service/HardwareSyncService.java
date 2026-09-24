package com.atomg.accessmanager.service;

import com.atomg.accessmanager.model.ComandoReloj;
import com.atomg.accessmanager.model.Empleado;
import com.atomg.accessmanager.model.Fichada;
import com.atomg.accessmanager.model.Reloj;
import com.atomg.accessmanager.model.Sucursal;
import com.atomg.accessmanager.repository.ComandoRelojRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

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

    @Autowired
    private ComandoRelojRepository comandoRelojRepository;

    @Autowired
    private AsistenciaService asistenciaService;

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
        reloj.setUltimaConexion(LocalDateTime.now());
        relojRepository.save(reloj);

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
                    Optional<Empleado> empOpt = empleadoRepository.findByLegajoRelojAndEmpresaId(pin, empresaId);
                    if (empOpt.isEmpty()) {
                        System.out.println("   [IGNORADO] Empleado con legajo " + pin + " no pertenece a la empresa " + empresaId);
                        continue;
                    }

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

            Optional<Empleado> empOpt = empleadoRepository.findByLegajoRelojAndEmpresaId(employeeNo, empresaId);
            if (empOpt.isEmpty()) {
                System.out.println("   [IGNORADO] Empleado con legajo " + employeeNo + " no pertenece a la empresa " + empresaId);
                return;
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
                        Optional<Empleado> empOpt = empleadoRepository.findByLegajoRelojAndEmpresaId(legajo, empresaId);
                        if (empOpt.isEmpty()) {
                            System.out.println("   [IGNORADO] Empleado con legajo " + legajo + " no pertenece a la empresa " + empresaId);
                            continue;
                        }

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

    public String obtenerComandoPendiente(String serialNumber) {
        if (serialNumber == null) return "OK";
        Optional<Reloj> relojOpt = relojRepository.findByNumeroSerie(serialNumber);
        if (relojOpt.isEmpty()) return "OK";
        
        Reloj reloj = relojOpt.get();
        LocalDateTime ultimaConex = reloj.getUltimaConexion();
        
        // Actualizamos la conexion para futuros chequeos
        reloj.setUltimaConexion(LocalDateTime.now());
        relojRepository.save(reloj);

        List<ComandoReloj> pendientes = comandoRelojRepository.findByRelojIdAndEjecutadoFalseOrderByFechaCreacionAsc(reloj.getId());
        if (pendientes.isEmpty()) {
            return "OK";
        }

        // Regla de seguridad: Si tiene un comando CLEAR ATTLOG, solo lo entregamos si el reloj ya estaba "al día".
        // "al dia" significa que su ultima conexion (antes de este request) fue hace menos de 5 minutos,
        // lo que asume que tuvo tiempo suficiente para enviar cualquier CDATA pendiente.
        for (ComandoReloj cmd : pendientes) {
            if ("CLEAR ATTLOG".equals(cmd.getComando())) {
                if (ultimaConex == null || ChronoUnit.MINUTES.between(ultimaConex, LocalDateTime.now()) >= 5) {
                    System.out.println("   [COMANDO PUESTO EN ESPERA] El reloj no esta al dia (ultima conexion hace >= 5 mins o nula). No se entrega CLEAR ATTLOG.");
                    return "OK"; // Se queda esperando a que este al dia
                }
            }
            
            // Si pasamos la validacion, lo marcamos como ejecutado y lo entregamos
            cmd.setEjecutado(true);
            comandoRelojRepository.save(cmd);
            System.out.println("   [COMANDO ENVIADO] " + cmd.getComando() + " a " + serialNumber);
            return "C:" + cmd.getId() + ":" + cmd.getComando();
        }

        return "OK";
    }
}
