package com.atomg.accessmanager.config;

import com.atomg.accessmanager.model.Empresa;
import com.atomg.accessmanager.repository.EmpresaRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

@Component
public class HardwareAuthInterceptor implements HandlerInterceptor {

    @Autowired
    private EmpresaRepository empresaRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getParameter("token");
        
        if (token == null) {
            System.out.println("[ALERTA SEGURIDAD] Intento de acceso bloqueado a hardware endpoint. Token ausente. (IP: " + request.getRemoteAddr() + ")");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        Optional<Empresa> empresaOpt = empresaRepository.findByApiToken(token);
        if (empresaOpt.isEmpty()) {
            System.out.println("[ALERTA SEGURIDAD] Token no reconocido o invalido. (IP: " + request.getRemoteAddr() + ")");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        
        System.out.println("Acceso concedido a Empresa ID: " + empresaOpt.get().getId());
        request.setAttribute("empresaId", empresaOpt.get().getId());
        
        return true;
    }
}