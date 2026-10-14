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

    @Autowired
    private com.atomg.accessmanager.repository.UsuarioRepository usuarioRepository;

    /**
     * Exclusión del filtro JWT para rutas que no requieren autenticación.
     * Proyecto: FluxTech | Autor: LARRAYA GASTÓN
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/hardware/") || path.startsWith("/iclock/") || path.equals("/api/auth/login");
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

            java.util.Optional<com.atomg.accessmanager.model.Usuario> usuarioOpt = java.util.Optional.empty();
            try {
                if (jwtUtil.validateToken(jwt, username)) {
                    usuarioOpt = usuarioRepository.findByUsername(username);
                }
            } catch (Exception e) {
                // Token inválido: se continúa sin autenticar
            }

            // El rol y la empresa se toman de la base, no del token: si el usuario fue
            // borrado, cambiado de empresa o degradado, el token viejo deja de dar esos permisos.
            if (usuarioOpt.isPresent() && usuarioOpt.get().getEmpresa() != null) {

                com.atomg.accessmanager.model.Usuario usuario = usuarioOpt.get();
                Long   empresaId = usuario.getEmpresa().getId();
                String rol       = usuario.getRol();

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