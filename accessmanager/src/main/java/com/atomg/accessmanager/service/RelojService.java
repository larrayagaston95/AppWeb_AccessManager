package com.atomg.accessmanager.service;

import com.atomg.accessmanager.model.Reloj;
import com.atomg.accessmanager.repository.RelojRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Optional;

/**
 * Servicio para gestión de hardware de relojes (Protocolo ISAPI).
 * Autor: LARRAYA GASTÓN
 * Proyecto: FluxTech
 */
@Service
public class RelojService {

    @Autowired
    private RelojRepository relojRepository;

    public void limpiarMemoriaFisica(Long id, Long empresaId) {
        Optional<Reloj> relojOpt = relojRepository.findById(id);
        if (relojOpt.isEmpty() || !relojOpt.get().getEmpresa().getId().equals(empresaId)) {
            throw new RuntimeException("Reloj no encontrado o no autorizado.");
        }

        Reloj reloj = relojOpt.get();

        if (reloj.getIp() == null || reloj.getIp().isBlank() ||
            reloj.getUsuario() == null || reloj.getUsuario().isBlank() ||
            reloj.getPassword() == null || reloj.getPassword().isBlank()) {
            throw new RuntimeException("Esta función requiere configurar la IP y credenciales del reloj para acceso en red local");
        }

        String url = "http://" + reloj.getIp() + "/ISAPI/AccessControl/AcsEvent/Clear";
        String payloadXml = "<AcsEventClear><ClearAll>true</ClearAll></AcsEventClear>";

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            
            // Basic Auth header. (Para Digest nativo usaríamos un RequestFactory HTTP, 
            // pero para los equipos FluxTech en LAN Basic suele estar habilitado).
            String auth = reloj.getUsuario() + ":" + reloj.getPassword();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);

            HttpEntity<String> request = new HttpEntity<>(payloadXml, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("El reloj físico devolvió un código de error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            throw new RuntimeException("Falla de conexión ISAPI con el dispositivo: " + e.getMessage());
        }
    }
}
