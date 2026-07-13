package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.model.Sector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SectorRepository extends JpaRepository<Sector, Long> {
    // Spring navega por la relación: sector -> sucursal -> nombre
    List<Sector> findBySucursalNombre(String sucursalNombre);
}