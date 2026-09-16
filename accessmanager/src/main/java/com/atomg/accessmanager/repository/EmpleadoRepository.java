package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.model.Empleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {

    List<Empleado> findBySectorId(Long sectorId);

    Optional<Empleado> findByLegajoReloj(String legajoReloj);

    // =========================================================================
    // DASHBOARD - Conteo de empleados
    // =========================================================================

    long countByEmpresaId(Long empresaId);

    long countByEmpresaIdAndSectorSucursalIdsucursal(Long empresaId, Long sucursalId);

    @Query("SELECT e.legajoReloj FROM Empleado e WHERE e.empresaId = :empresaId")
    List<String> findLegajosByEmpresaId(@Param("empresaId") Long empresaId);

    @Query("SELECT e.legajoReloj FROM Empleado e WHERE e.empresaId = :empresaId AND e.sector.sucursal.idsucursal = :sucursalId")
    List<String> findLegajosByEmpresaIdAndSucursalId(@Param("empresaId") Long empresaId, @Param("sucursalId") Long sucursalId);
}