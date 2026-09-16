package com.atomg.accessmanager.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * Salta el filtro JWT para requests OPTIONS (preflight CORS).
     * Sin esto el browser recibe 403 antes de poder enviar el GET/POST real con el token.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authorizationHeader = request.getHeader("Authorization");
        final String accessTokenHeader   = request.getHeader("X-Access-Token"); // Compatibilidad legacy

        String jwt      = null;
        String username = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
        } else if (accessTokenHeader != null && !accessTokenHeader.isEmpty()) {
            jwt = accessTokenHeader;
        }

        if (jwt != null) {
            try {
                username = jwtUtil.extractUsername(jwt);
            } catch (Exception e) {
                // Token invalido o expirado — continua sin autenticar
            }
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            if (jwtUtil.validateToken(jwt, username)) {

                Long   empresaId = jwtUtil.extractEmpresaId(jwt);
                String rol       = jwtUtil.extractRol(jwt);

                // Guardar en atributos del request para acceso facil en controladores Multi-Tenant
                request.setAttribute("empresaId", empresaId);
                request.setAttribute("rol", rol);

                // El valor del rol en BD es "ROLE_SUPERADMIN" / "ROLE_ADMIN"
                // SimpleGrantedAuthority lo toma literal → hasAuthority("ROLE_SUPERADMIN") coincide
                List<SimpleGrantedAuthority> authorities = rol != null
                        ? Collections.singletonList(new SimpleGrantedAuthority(rol))
                        : Collections.emptyList();

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        username, null, authorities
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}