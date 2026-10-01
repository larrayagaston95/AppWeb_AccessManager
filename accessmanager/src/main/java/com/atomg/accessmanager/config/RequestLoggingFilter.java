package com.atomg.accessmanager.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Filtro global de logging para auditoría HTTP.
 * Diseñado para depurar el tráfico entrante/saliente y detectar 
 * bloqueos ocultos de Spring Security (ej. 401/403 fantasmas).
 * 
 * Proyecto: FluxTech
 * Autor: LARRAYA GASTÓN
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // 1. Proceder con el resto de la cadena de filtros de Spring
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 2. Capturar el estado HTTP de respuesta después de que la cadena termina (incluso si hay error)
            String method = request.getMethod();
            String uri = request.getRequestURI();
            int status = response.getStatus();
            
            // 3. Imprimir log con el formato exacto requerido
            String logMessage = String.format("[FLUXTECH AUDIT] %s %s -> STATUS: %d", method, uri, status);
            
            // Logear a consola (también se usa logger de SLF4J para integrarlo mejor con Spring Boot)
            System.out.println(logMessage);
            logger.info(logMessage);
        }
    }
}
