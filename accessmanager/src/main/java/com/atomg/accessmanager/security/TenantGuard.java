package com.atomg.accessmanager.security;

import com.atomg.accessmanager.model.Sector;
import com.atomg.accessmanager.model.Sucursal;
import com.atomg.accessmanager.repository.SectorRepository;
import com.atomg.accessmanager.repository.SucursalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Verificaciones multi-tenant: un recurso solo se devuelve si pertenece a la empresa del JWT.
 * Si no existe o es de otra empresa se devuelve vacío (el controlador responde 404, sin
 * revelar si el ID existe en otra empresa).
 */
@Component
public class TenantGuard {

    @Autowired
    private SucursalRepository sucursalRepository;

    @Autowired
    private SectorRepository sectorRepository;

    public Optional<Sucursal> sucursalDeEmpresa(Long sucursalId, Long empresaId) {
        if (sucursalId == null || empresaId == null) {
            return Optional.empty();
        }
        return sucursalRepository.findById(sucursalId)
                .filter(s -> s.getEmpresa() != null && empresaId.equals(s.getEmpresa().getId()));
    }

    public Optional<Sector> sectorDeEmpresa(Long sectorId, Long empresaId) {
        if (sectorId == null || empresaId == null) {
            return Optional.empty();
        }
        return sectorRepository.findById(sectorId)
                .filter(s -> s.getSucursal() != null
                        && s.getSucursal().getEmpresa() != null
                        && empresaId.equals(s.getSucursal().getEmpresa().getId()));
    }
}
