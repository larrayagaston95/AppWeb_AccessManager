package com.atomg.accessmanager.controller;

import com.atomg.accessmanager.model.Fichada;
import com.atomg.accessmanager.model.Reloj;
import com.atomg.accessmanager.repository.RelojRepository;
import com.atomg.accessmanager.service.AsistenciaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
        System.out.println("=== Payload recibido de Hikvision ===");
        System.out.println(payload);
        System.out.println("=====================================");

        // 2. Extraer el Número de Serie del equipo y el Legajo del empleado
        String numeroSerie = extraerValor(payload, "serialNo", "serialNumber", "deviceSerialNumber", "macAddress", "MACAddr");
        String legajo = extraerValor(payload, "employeeNoString", "employeeNo", "employeeId", "user");

        if (numeroSerie == null || legajo == null) {
             System.out.println("No se pudo extraer el numero de serie o legajo del payload.");
             // Respondemos 200 OK de todos modos para que el reloj no se trabe reintentando infinitamente
             return ResponseEntity.ok("OK");
        }

        // 3. Emparejamiento: Buscar el Reloj por número de serie
        Optional<Reloj> relojOpt = relojRepository.findByNumeroSerie(numeroSerie);
        
        if (relojOpt.isPresent()) {
            Reloj reloj = relojOpt.get();

            // 4. Registro: Guardar la Fichada
            Fichada fichada = new Fichada();
            fichada.setLegajoReloj(legajo);
            fichada.setFechaHora(LocalDateTime.now()); // Se puede extraer del payload mas adelante
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

            // 5. Guardar la Fichada llamando a AsistenciaService
            asistenciaService.guardarFichada(fichada);
            System.out.println("Fichada guardada correctamente para legajo: " + legajo + " (Reloj: " + numeroSerie + ")");
        } else {
            System.out.println("Reloj no encontrado en la base de datos con numero de serie: " + numeroSerie);
        }

        // Responderle al reloj un HTTP 200 OK para que sepa que el dato llegó
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
