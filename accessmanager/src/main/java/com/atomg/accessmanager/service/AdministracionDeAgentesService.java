package com.atomg.accessmanager.service;

import com.atomg.accessmanager.model.Agente;
import com.atomg.accessmanager.model.Empresa;
import com.atomg.accessmanager.model.Sucursal;
import com.atomg.accessmanager.repository.AgenteRepository;
import com.atomg.accessmanager.repository.EmpresaRepository;
import com.atomg.accessmanager.repository.SucursalRepository;
import com.atomg.accessmanager.security.AgenteClaves;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Alta, listado, revocación y cambio de clave de agentes (panel SuperAdmin).
 *
 * Recorrido del dato:
 *   Panel (agentesUI.js) → servicio del panel (agenteAdminService.js) → AgenteAdminController
 *   → este servicio → AgenteRepository (tabla agentes).
 *   Regla de seguridad: la clave completa se devuelve UNA sola vez (al crear o regenerar);
 *   en la base solo queda un prefijo para buscarla y su hash.
 */
@Service
public class AdministracionDeAgentesService {

    private static final Logger registro = LoggerFactory.getLogger(AdministracionDeAgentesService.class);
    private static final ZoneId ZONA_HORARIA_DEL_NEGOCIO = ZoneId.of("America/Argentina/Cordoba");

    @Autowired
    private AgenteRepository agenteRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private SucursalRepository sucursalRepository;

    @Value("${agente.intervalo-segundos:60}")
    private int segundosEntreCiclosDelAgente;

    /** Agentes con su estado. "En línea" = reportó en los últimos 3 ciclos (mínimo 3 minutos). */
    public List<Map<String, Object>> listarAgentesConEstado() {
        LocalDateTime ahora = LocalDateTime.now(ZONA_HORARIA_DEL_NEGOCIO);
        long segundosDeTolerancia = Math.max(180, segundosEntreCiclosDelAgente * 3L);
        try {
            return agenteRepository.findAllByOrderByEmpresaIdAscNombreAsc().stream()
                    .map(agente -> convertirParaElPanel(agente, ahora, segundosDeTolerancia))
                    .toList();
        } catch (RuntimeException excepcion) {
            registro.error("Error en AdministracionDeAgentesService.java -> listarAgentesConEstado: {}", excepcion.getMessage());
            throw excepcion;
        }
    }

    /** Crea el agente y devuelve su clave completa (única vez que se puede ver). */
    public Map<String, Object> crearAgente(String nombre, Long idDeLaEmpresa, Long idDeLaSucursal) {
        if (nombre == null || nombre.isBlank() || idDeLaEmpresa == null) {
            throw new IllegalArgumentException("Faltan parámetros (nombre, empresa_id)");
        }
        try {
            Empresa empresa = empresaRepository.findById(idDeLaEmpresa)
                    .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
            Sucursal sucursal = null;
            if (idDeLaSucursal != null) {
                // La sucursal tiene que ser de la misma empresa (aislamiento entre clientes)
                sucursal = sucursalRepository.findById(idDeLaSucursal)
                        .filter(encontrada -> encontrada.getEmpresa() != null && idDeLaEmpresa.equals(encontrada.getEmpresa().getId()))
                        .orElseThrow(() -> new IllegalArgumentException("La sucursal no pertenece a esa empresa"));
            }
            AgenteClaves.ClaveNueva claveNueva = AgenteClaves.generar();
            Agente agente = new Agente();
            agente.setNombre(nombre.trim());
            agente.setEmpresa(empresa);
            agente.setSucursal(sucursal);
            agente.setClavePrefijo(claveNueva.prefijo());
            agente.setClaveHash(claveNueva.hash());
            agente.setActivo(true);
            agente.setFechaCreacion(LocalDateTime.now(ZONA_HORARIA_DEL_NEGOCIO));
            agente = agenteRepository.save(agente);
            return Map.of("id", agente.getId(), "clave", claveNueva.clave(), "aviso", "Copiá la clave ahora: no se vuelve a mostrar.");
        } catch (IllegalArgumentException errorDeValidacion) {
            throw errorDeValidacion;
        } catch (RuntimeException excepcion) {
            registro.error("Error en AdministracionDeAgentesService.java -> crearAgente: {}", excepcion.getMessage());
            throw excepcion;
        }
    }

    /** La clave deja de funcionar de inmediato (el agente recibe 403 en su próximo ciclo). */
    public void revocarAgente(Long idDelAgente) {
        Agente agente = buscarAgente(idDelAgente, "revocarAgente");
        agente.setActivo(false);
        guardar(agente, "revocarAgente");
    }

    /** Genera una clave nueva (la anterior deja de funcionar) y reactiva el agente. */
    public Map<String, Object> regenerarClave(Long idDelAgente) {
        Agente agente = buscarAgente(idDelAgente, "regenerarClave");
        AgenteClaves.ClaveNueva claveNueva = AgenteClaves.generar();
        agente.setClavePrefijo(claveNueva.prefijo());
        agente.setClaveHash(claveNueva.hash());
        agente.setActivo(true);
        guardar(agente, "regenerarClave");
        return Map.of("id", agente.getId(), "clave", claveNueva.clave(), "aviso", "La clave anterior dejó de funcionar. Cargá esta en el agente.");
    }

    private Map<String, Object> convertirParaElPanel(Agente agente, LocalDateTime ahora, long segundosDeTolerancia) {
        Map<String, Object> fila = new HashMap<>();
        fila.put("id", agente.getId());
        fila.put("nombre", agente.getNombre());
        fila.put("empresa", agente.getEmpresa().getNombre());
        fila.put("sucursal", agente.getSucursal() != null ? agente.getSucursal().getNombre() : "Todas");
        fila.put("activo", agente.isActivo());
        fila.put("ultimaConexion", agente.getUltimaConexion() != null ? agente.getUltimaConexion().toString() : "");
        fila.put("enLinea", agente.isActivo() && agente.getUltimaConexion() != null
                && Duration.between(agente.getUltimaConexion(), ahora).getSeconds() <= segundosDeTolerancia);
        fila.put("version", agente.getVersionAgente() != null ? agente.getVersionAgente() : "");
        fila.put("estado", agente.getUltimoEstado() != null ? agente.getUltimoEstado() : "");
        return fila;
    }

    private Agente buscarAgente(Long idDelAgente, String nombreDeLaFuncion) {
        try {
            return agenteRepository.findById(idDelAgente).orElseThrow(() -> new NoSuchElementException("Agente no encontrado"));
        } catch (NoSuchElementException noEncontrado) {
            throw noEncontrado;
        } catch (RuntimeException excepcion) {
            registro.error("Error en AdministracionDeAgentesService.java -> {}: {}", nombreDeLaFuncion, excepcion.getMessage());
            throw excepcion;
        }
    }

    private void guardar(Agente agente, String nombreDeLaFuncion) {
        try {
            agenteRepository.save(agente);
        } catch (RuntimeException excepcion) {
            registro.error("Error en AdministracionDeAgentesService.java -> {}: {}", nombreDeLaFuncion, excepcion.getMessage());
            throw excepcion;
        }
    }
}
