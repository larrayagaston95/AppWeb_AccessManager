package com.atomg.accessmanager.service;

import com.atomg.accessmanager.model.Empleado;
import com.atomg.accessmanager.model.Fichada;
import com.atomg.accessmanager.repository.EmpleadoRepository;
import com.atomg.accessmanager.repository.FichadaRepository;
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
}