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
 *
 * Recorrido del dato:
 *   1. Llega un pedido del agente con "X-Agent-Key: fxa_<prefijo>_<secreto>".
 *   2. Se busca el agente por el prefijo (AgenteRepository) y se compara el hash de la clave.
 *   3. Si coincide y el agente está activo: se deja el Agente en el atributo ATRIBUTO_AGENTE del
 *      pedido y se concede ROLE_AGENTE. El controlador lo toma de ahí.
 *   4. Si no: el pedido sigue sin autenticar y Spring Security responde 403.
 */
@Component
public class AgenteAuthFilter extends OncePerRequestFilter {

    public static final String CABECERA_DE_LA_CLAVE = "X-Agent-Key";
    public static final String ATRIBUTO_AGENTE = "agente";

    private static final Logger registro = LoggerFactory.getLogger(AgenteAuthFilter.class);

    @Autowired
    private AgenteRepository agenteRepository;

    /** Solo actúa sobre la API de agentes; el resto de los pedidos ni se mira. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest pedido) {
        return !pedido.getServletPath().startsWith("/api/agente/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse respuesta, FilterChain cadenaDeFiltros)
            throws ServletException, IOException {

        String claveRecibida = pedido.getHeader(CABECERA_DE_LA_CLAVE);
        String prefijo = AgenteClaves.extraerPrefijo(claveRecibida);

        if (prefijo != null) {
            Optional<Agente> agenteEncontrado = buscarAgentePorPrefijo(prefijo);
            boolean claveValida = agenteEncontrado.isPresent()
                    && agenteEncontrado.get().isActivo()
                    && AgenteClaves.coincide(claveRecibida, agenteEncontrado.get().getClaveHash());
            if (claveValida) {
                Agente agente = agenteEncontrado.get();
                pedido.setAttribute(ATRIBUTO_AGENTE, agente);
                UsernamePasswordAuthenticationToken autenticacion = new UsernamePasswordAuthenticationToken(
                        "agente:" + agente.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_AGENTE")));
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
            } else {
                registro.warn("[SEGURIDAD] Clave de agente inválida o revocada (prefijo {}, IP {})", prefijo, pedido.getRemoteAddr());
            }
        }

        cadenaDeFiltros.doFilter(pedido, respuesta);
    }

    /** Consulta a la base; si falla, el pedido queda sin autenticar (nunca se concede acceso por un error). */
    private Optional<Agente> buscarAgentePorPrefijo(String prefijo) {
        try {
            return agenteRepository.findByClavePrefijo(prefijo);
        } catch (RuntimeException excepcion) {
            registro.error("Error en AgenteAuthFilter.java -> buscarAgentePorPrefijo: {}", excepcion.getMessage());
            return Optional.empty();
        }
    }
}
