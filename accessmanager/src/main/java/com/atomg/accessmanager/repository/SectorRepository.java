package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.model.Sector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SectorRepository extends JpaRepository<Sector, Long> {
    // Busca sectores por el ID numérico de la sucursal (FK: sucursal_id -> sucursal.idsucursal)
    List<Sector> findBySucursalIdsucursal(Long sucursalId);
}