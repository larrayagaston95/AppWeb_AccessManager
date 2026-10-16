package com.atomg.accessmanager.security;

import com.atomg.accessmanager.model.Agente;
import com.atomg.accessmanager.repository.AgenteRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Autentica a los agentes locales en /api/agente/** con el header X-Agent-Key.
 * Si la clave es válida y el agente está activo, deja el agente en el atributo
 * ATRIBUTO_AGENTE y concede ROLE_AGENTE. Si no, la petición sigue sin autenticar y
 * Spring Security la rechaza.
 */
@Component
public class AgenteAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Agent-Key";
    public static final String ATRIBUTO_AGENTE = "agente";

    private static final Logger log = LoggerFactory.getLogger(AgenteAuthFilter.class);

    @Autowired
    private AgenteRepository agenteRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/agente/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String clave = request.getHeader(HEADER);
        String prefijo = AgenteClaves.extraerPrefijo(clave);

        if (prefijo != null) {
            Optional<Agente> agenteOpt = agenteRepository.findByClavePrefijo(prefijo);
            if (agenteOpt.isPresent()
                    && agenteOpt.get().isActivo()
                    && AgenteClaves.coincide(clave, agenteOpt.get().getClaveHash())) {

                Agente agente = agenteOpt.get();
                request.setAttribute(ATRIBUTO_AGENTE, agente);
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        "agente:" + agente.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_AGENTE")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                log.warn("[SEGURIDAD] Clave de agente inválida o revocada (prefijo {}, IP {})",
                        prefijo, request.getRemoteAddr());
            }
        }

        chain.doFilter(request, response);
    }
}
