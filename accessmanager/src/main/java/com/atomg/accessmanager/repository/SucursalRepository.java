package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.model.Sucursal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SucursalRepository extends JpaRepository<Sucursal, Long> {
    // Spring hace el JOIN automático: busca por el ID de la entidad Empresa
    List<Sucursal> findByEmpresaId(Long empresaId);
}