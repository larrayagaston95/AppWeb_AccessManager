package com.atomg.accessmanager.config;

import com.atomg.accessmanager.model.Empresa;
import com.atomg.accessmanager.model.Reloj;
import com.atomg.accessmanager.repository.EmpresaRepository;
import com.atomg.accessmanager.repository.RelojRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Autenticación de los webhooks de relojes (/iclock/** y /api/hardware/**).
 *
 * - Si el reloj envía ?token=..., debe coincidir con empresas.api_token, y si además envía su
 *   número de serie (parámetro o header "SN"), ese reloj tiene que pertenecer a esa empresa.
 *   La empresa validada queda en el atributo HARDWARE_EMPRESA_ID para que los controladores
 *   verifiquen el número de serie que llega dentro del body (Hikvision).
 * - Si hardware.require-token=true, el token es obligatorio.
 * - Con require-token=false (modo compatibilidad) se aceptan pedidos sin token, como antes.
 *   Los servicios igualmente descartan números de serie no registrados.
 */
@Component
public class HardwareAuthInterceptor implements HandlerInterceptor {

    public static final String HARDWARE_EMPRESA_ID = "hardwareEmpresaId";

    private static final Logger log = LoggerFactory.getLogger(HardwareAuthInterceptor.class);

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private RelojRepository relojRepository;

    @Value("${hardware.require-token:false}")
    private boolean requireToken;

    /** false = solo se aceptan fichadas por agentes locales; los relojes no pueden enviar directo. */
    @Value("${hardware.directo.habilitado:true}")
    private boolean directoHabilitado;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!directoHabilitado) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return false;
        }

        String token = request.getParameter("token");

        if (token == null || token.isBlank()) {
            if (requireToken) {
                log.warn("[SEGURIDAD] Webhook de hardware sin token rechazado: {} (IP {})",
                        request.getServletPath(), request.getRemoteAddr());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
            return true;
        }

        Optional<Empresa> empresaOpt = empresaRepository.findByApiToken(token.trim());
        if (empresaOpt.isEmpty()) {
            log.warn("[SEGURIDAD] Token de hardware inválido: {} (IP {})",
                    request.getServletPath(), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        Long empresaId = empresaOpt.get().getId();

        String sn = request.getParameter("SN");
        if (sn == null || sn.isBlank()) {
            sn = request.getHeader("SN");
        }
        if (sn != null && !sn.isBlank()) {
            Optional<Reloj> relojOpt = relojRepository.findByNumeroSerie(sn.trim());
            if (relojOpt.isPresent() && !empresaId.equals(relojOpt.get().getEmpresa().getId())) {
                log.warn("[SEGURIDAD] El reloj SN={} no pertenece a la empresa del token (IP {})",
                        sn, request.getRemoteAddr());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
        }

        request.setAttribute(HARDWARE_EMPRESA_ID, empresaId);
        return true;
    }

    /**
     * Para los controladores que leen el número de serie del body: true si el reloj puede
     * operar en este pedido (sin token validado, o con token de la misma empresa).
     */
    public static boolean relojAutorizado(HttpServletRequest request, Reloj reloj) {
        Object esperado = request.getAttribute(HARDWARE_EMPRESA_ID);
        if (esperado == null) {
            return true;
        }
        return reloj != null && reloj.getEmpresa() != null && esperado.equals(reloj.getEmpresa().getId());
    }
}
